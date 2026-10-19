"""Fabrique un mini modele GGUF (poids au hasard) pour tester le moteur sans vrai modele.

Usage : LLAMA_CPP=/chemin/llama.cpp python3 creer_modele.py sortie.gguf [chatml]
"""
import sys, numpy as np
import os
sys.path.insert(0, os.path.join(os.environ.get('LLAMA_CPP', 'llama.cpp'), 'gguf-py'))
import gguf

with_template = len(sys.argv) > 2 and sys.argv[2] == 'chatml'
out = sys.argv[1]

# --- vocabulaire SPM minimal : <unk> <s> </s>, 256 octets, "▁", caracteres ASCII, quelques mots
tokens = ['<unk>', '<s>', '</s>']
types = [gguf.TokenType.UNKNOWN, gguf.TokenType.CONTROL, gguf.TokenType.CONTROL]
scores = [0.0, 0.0, 0.0]
for b in range(256):
    tokens.append('<0x%02X>' % b); types.append(gguf.TokenType.BYTE); scores.append(0.0)
tokens.append('▁'); types.append(gguf.TokenType.NORMAL); scores.append(-1.0)
for c in 'abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789.,;:!?()[]{}<>=+-*/\\"\'_#@$%&|~`^\n':
    tokens.append(c); types.append(gguf.TokenType.NORMAL); scores.append(-2.0)
for w in ['▁the', '▁a', '▁de', '▁fun', '▁val', '▁class', 'ing', 'er', 'on']:
    tokens.append(w); types.append(gguf.TokenType.NORMAL); scores.append(-0.5)
if with_template:
    for w in ['<|im_start|>', '<|im_end|>']:
        tokens.append(w); types.append(gguf.TokenType.CONTROL); scores.append(0.0)

n_vocab = len(tokens)
n_embd, n_layer, n_head, n_ff = 64, 2, 4, 128
head_dim = n_embd // n_head

w = gguf.GGUFWriter(out, 'llama')
w.add_name('tiny-test')
w.add_context_length(16384)
w.add_embedding_length(n_embd)
w.add_block_count(n_layer)
w.add_feed_forward_length(n_ff)
w.add_head_count(n_head)
w.add_head_count_kv(n_head)
w.add_layer_norm_rms_eps(1e-5)
w.add_rope_dimension_count(head_dim)
w.add_vocab_size(n_vocab)
w.add_tokenizer_model('llama')
w.add_token_list(tokens)
w.add_token_scores(scores)
w.add_token_types(types)
w.add_bos_token_id(1)
w.add_eos_token_id(2)
w.add_unk_token_id(0)
w.add_add_bos_token(True)
w.add_add_space_prefix(True)
if with_template:
    w.add_chat_template("{% for m in messages %}<|im_start|>{{ m['role'] }}\n{{ m['content'] }}<|im_end|>\n{% endfor %}{% if add_generation_prompt %}<|im_start|>assistant\n{% endif %}")

rng = np.random.default_rng(1)
def t(name, *shape, scale=0.05):
    w.add_tensor(name, (rng.standard_normal(shape) * scale).astype(np.float32))
def ones(name, n):
    w.add_tensor(name, np.ones(n, dtype=np.float32))

t('token_embd.weight', n_vocab, n_embd, scale=0.5)
ones('output_norm.weight', n_embd)
t('output.weight', n_vocab, n_embd, scale=0.5)
for i in range(n_layer):
    ones(f'blk.{i}.attn_norm.weight', n_embd)
    t(f'blk.{i}.attn_q.weight', n_embd, n_embd)
    t(f'blk.{i}.attn_k.weight', n_embd, n_embd)
    t(f'blk.{i}.attn_v.weight', n_embd, n_embd)
    t(f'blk.{i}.attn_output.weight', n_embd, n_embd)
    ones(f'blk.{i}.ffn_norm.weight', n_embd)
    t(f'blk.{i}.ffn_gate.weight', n_ff, n_embd)
    t(f'blk.{i}.ffn_up.weight', n_ff, n_embd)
    t(f'blk.{i}.ffn_down.weight', n_embd, n_ff)

w.write_header_to_file(); w.write_kv_data_to_file(); w.write_tensors_to_file(); w.close()
print('ecrit', out, 'vocab', n_vocab)
