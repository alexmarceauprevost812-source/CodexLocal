package com.codexlocal.chat

import android.annotation.SuppressLint
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import com.codexlocal.BuildConfig
import com.codexlocal.Message
import org.json.JSONArray
import org.json.JSONObject

/**
 * Schémas animés « style vidéo » (APK perso).
 *
 * L'IA écrit un bloc :
 * ```schema
 * Titre: Cycle de l'eau
 * Évaporation -> Nuages
 * Nuages -> Pluie
 * Pluie -> Rivières -> Océan -> Évaporation
 * ```
 * (ou simplement une étape par ligne). À la fin de la réponse, le bloc devient une carte avec
 * un aperçu et un lien ▶️ qui ouvre [SchemaActivity] : les étapes apparaissent une à une, les
 * flèches se dessinent et des points lumineux circulent, comme une petite vidéo.
 */
object SchemaAnime {

    data class Schema(val titre: String, val noeuds: List<String>, val liens: List<Pair<Int, Int>>)

    private val bloc = Regex("```(?:schema|schéma|diagramme|mermaid)[^\\n]*\\n(.*?)```", RegexOption.DOT_MATCHES_ALL)
    private val fleche = Regex("\\s*(?:-->|->|→|=>|➜|⟶)\\s*")

    val SCHEME: String get() = BuildConfig.APPLICATION_ID + ".schema"

    const val CONSIGNE = "Pour expliquer un processus, des étapes, un cycle ou une architecture, ajoute un schéma animé : " +
        "un bloc ```schema avec une première ligne « Titre: … » puis une ligne par lien « A -> B » (ou une étape par ligne). " +
        "Garde des noms courts (1 à 4 mots), 3 à 10 étapes."

    fun lire(texte: String): Schema? {
        var titre = "Schéma"
        val noms = mutableListOf<String>()
        val liens = LinkedHashSet<Pair<Int, Int>>()
        fun index(nom: String): Int {
            val propre = nom.trim().replace(Regex("^[A-Za-z0-9_]+[\\[(]{1,2}(.*?)[\\])]{1,2}$"), "$1")
                .trim('"', ' ', '-', '*', '•').take(40)
            val i = noms.indexOfFirst { it.equals(propre, ignoreCase = true) }
            if (i >= 0) return i
            noms += propre
            return noms.size - 1
        }
        var precedent: Int? = null
        for (brute in texte.lines()) {
            val ligne = brute.trim()
            if (ligne.isEmpty() || ligne.startsWith("%%") || Regex("^(graph|flowchart)\\b", RegexOption.IGNORE_CASE).containsMatchIn(ligne)) continue
            val t = Regex("^(titre|title)\\s*:\\s*(.+)$", RegexOption.IGNORE_CASE).find(ligne)
            if (t != null) { titre = t.groupValues[2].trim().take(60); continue }
            val morceaux = ligne.split(fleche).map { it.replace(Regex("\\|.*?\\|"), "").trim() }.filter { it.isNotEmpty() }
            if (morceaux.size >= 2) {
                morceaux.zipWithNext().forEach { (a, b) -> liens += index(a) to index(b) }
                precedent = null
            } else if (morceaux.size == 1) {
                // Une étape par ligne : on les enchaîne dans l'ordre.
                val i = index(morceaux[0].removePrefix("1.").replace(Regex("^\\d+[.)]\\s*"), ""))
                precedent?.let { if (it != i) liens += it to i }
                precedent = i
            }
            if (noms.size >= 16) break
        }
        if (noms.size < 2) return null
        return Schema(titre, noms, liens.toList())
    }

    private fun versUri(s: Schema): String {
        val json = JSONObject().put("t", s.titre).put("n", JSONArray(s.noeuds))
            .put("l", JSONArray(s.liens.map { JSONArray().put(it.first).put(it.second) }))
        val code = Base64.encodeToString(json.toString().toByteArray(), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        return "$SCHEME://voir?d=$code"
    }

    /** Remplace chaque bloc ```schema par une carte avec un lien vers l'animation. */
    fun convertir(contenu: String): String = bloc.replace(contenu) { m ->
        val s = lire(m.groupValues[1]) ?: return@replace m.value
        val apercu = if (s.liens.isEmpty()) s.noeuds.joinToString(" ➜ ")
        else s.liens.take(8).joinToString("\n") { (a, b) -> "• ${s.noeuds[a]} ➜ ${s.noeuds[b]}" }
        "🎬 **Schéma animé · ${s.titre.replace("*", "")}**\n$apercu\n▶️ [Lancer l'animation](${versUri(s)})\n"
    }

    fun annoter(message: Message) {
        if (message.isUser || !bloc.containsMatchIn(message.content)) return
        if (message.envoi == null) message.envoi = message.content
        message.content = convertir(message.content)
    }

    fun decoder(uri: Uri?): Schema? = runCatching {
        val code = uri?.getQueryParameter("d") ?: return null
        val json = JSONObject(String(Base64.decode(code, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)))
        val n = json.getJSONArray("n"); val l = json.getJSONArray("l")
        Schema(
            json.optString("t", "Schéma").take(60),
            (0 until minOf(n.length(), 16)).map { n.getString(it).take(40) },
            (0 until minOf(l.length(), 40)).map { l.getJSONArray(it).let { p -> p.getInt(0) to p.getInt(1) } }
                .filter { (a, b) -> a in 0 until minOf(n.length(), 16) && b in 0 until minOf(n.length(), 16) }
        )
    }.getOrNull()

    /** Page autonome (aucun réseau) : SVG + JavaScript pour l'animation. */
    fun html(s: Schema): String {
        val donnees = JSONObject().put("t", s.titre).put("n", JSONArray(s.noeuds))
            .put("l", JSONArray(s.liens.map { JSONArray().put(it.first).put(it.second) }))
            .toString().replace("<", "\\u003c")
        return """<!doctype html><html><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<style>
html,body{margin:0;height:100%;background:#000;color:#fff;font-family:sans-serif;overflow:hidden}
#titre{position:absolute;top:14px;left:0;right:0;text-align:center;font-size:20px;font-weight:bold;
 background:linear-gradient(90deg,#FFB300,#FF6D00,#29B6F6,#B388FF,#FFB300);background-size:300% 100%;
 -webkit-background-clip:text;color:transparent;animation:arc 6s linear infinite;opacity:0;transition:opacity .8s}
@keyframes arc{to{background-position:300% 0}}
svg{position:absolute;top:56px;left:0;width:100%;height:calc(100% - 120px)}
.n{opacity:0;transform-box:fill-box;transform-origin:center;transform:scale(.2)}
.n.on{opacity:1;transform:scale(1);transition:transform .55s cubic-bezier(.3,1.6,.5,1),opacity .4s}
.e{fill:none;stroke-width:3;stroke-linecap:round}
.lab{fill:#fff;font-size:14px;font-weight:bold;text-anchor:middle;dominant-baseline:middle}
#barre{position:absolute;bottom:14px;left:0;right:0;display:flex;gap:10px;justify-content:center}
button{background:#111;color:#FFB300;border:2px solid #FF6D00;border-radius:22px;padding:9px 18px;font-size:15px}
#prog{position:absolute;bottom:64px;left:10%;width:80%;height:4px;background:#222;border-radius:2px}
#prog div{height:100%;width:0;background:linear-gradient(90deg,#FF6D00,#29B6F6);border-radius:2px}
</style></head><body>
<div id="titre"></div><svg id="s"></svg><div id="prog"><div id="pb"></div></div>
<div id="barre"><button onclick="jouer()">↻ Rejouer</button><button onclick="vitesse()" id="v">Vitesse ×1</button></div>
<script>
const D=$donnees;const COUL=['#FF6D00','#FFB300','#29B6F6','#B388FF','#00E676','#FF4081'];
let fac=1,timers=[];
function vitesse(){fac=fac==1?2:fac==2?0.5:1;document.getElementById('v').textContent='Vitesse ×'+fac;jouer()}
function niveaux(){const n=D.n.length,niv=new Array(n).fill(0);
 for(let k=0;k<n;k++)for(const[a,b]of D.l){if(a!=b&&niv[b]<niv[a]+1&&niv[a]+1<n)niv[b]=niv[a]+1}return niv}
function jouer(){timers.forEach(clearTimeout);timers=[];const svg=document.getElementById('s');svg.innerHTML='';
 const t=document.getElementById('titre');t.textContent=D.t;t.style.opacity=0;setTimeout(()=>t.style.opacity=1,50);
 const W=svg.clientWidth,H=svg.clientHeight,niv=niveaux(),max=Math.max(...niv)+1,grp={};
 niv.forEach((v,i)=>(grp[v]=grp[v]||[]).push(i));
 const vertical=H>=W*0.8,pos=[];
 for(const v in grp){grp[v].forEach((i,k)=>{const c=grp[v].length;
  if(vertical)pos[i]=[W*(k+1)/(c+1),H*(+v+0.5)/max];else pos[i]=[W*(+v+0.5)/max,H*(k+1)/(c+1)]})}
 const ns='http://www.w3.org/2000/svg',defs=document.createElementNS(ns,'defs');
 defs.innerHTML='<filter id="g"><feGaussianBlur stdDeviation="4" result="b"/><feMerge><feMergeNode in="b"/><feMergeNode in="SourceGraphic"/></feMerge></filter>'+
 '<marker id="m" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto"><path d="M0,0L10,5L0,10z" fill="#FFB300"/></marker>';
 svg.appendChild(defs);
 const bw=Math.min(150,(vertical?W/ Math.max(...Object.values(grp).map(g=>g.length)):W/max)-16),bh=40;
 const ordre=D.n.map((_,i)=>i).sort((a,b)=>niv[a]-niv[b]||a-b);const pas=900/fac;let total=0;
 ordre.forEach((i,k)=>{const g=document.createElementNS(ns,'g');g.setAttribute('class','n');
  const[x,y]=pos[i],c=COUL[niv[i]%COUL.length];
  g.innerHTML='<rect x="'+(x-bw/2)+'" y="'+(y-bh/2)+'" width="'+bw+'" height="'+bh+'" rx="14" fill="#0d0d0d" stroke="'+c+'" stroke-width="2.5" filter="url(#g)"/>'+
   '<text class="lab" x="'+x+'" y="'+y+'">'+esc(D.n[i])+'</text>';
  g.dataset.i=i;svg.appendChild(g);fit(g.querySelector('text'),bw-10);
  timers.push(setTimeout(()=>g.classList.add('on'),k*pas));total=k*pas});
 D.l.forEach(([a,b],k)=>{if(a==b)return;const[x1,y1]=pos[a],[x2,y2]=pos[b];
  const dx=x2-x1,dy=y2-y1,L=Math.hypot(dx,dy)||1,ox=dx/L*(vertical?bh/2+4:bw/2+4),oy=dy/L*(vertical?bh/2+4:bw/2+4);
  const sx=x1+ox,sy=y1+oy,ex=x2-ox,ey=y2-oy,cx=(sx+ex)/2+(niv[b]<=niv[a]?60:0),cy=(sy+ey)/2+(niv[b]<=niv[a]?-40:0);
  const p=document.createElementNS(ns,'path');const d='M'+sx+','+sy+' Q'+cx+','+cy+' '+ex+','+ey;
  p.setAttribute('d',d);p.setAttribute('class','e');p.setAttribute('stroke',COUL[(k+1)%COUL.length]);p.setAttribute('marker-end','url(#m)');
  svg.insertBefore(p,defs.nextSibling);const len=p.getTotalLength();p.style.strokeDasharray=len;p.style.strokeDashoffset=len;
  const quand=Math.max(ordre.indexOf(a),ordre.indexOf(b))*pas+pas*0.4;
  timers.push(setTimeout(()=>{p.style.transition='stroke-dashoffset '+(700/fac)+'ms ease';p.style.strokeDashoffset=0;
   timers.push(setTimeout(()=>{p.style.strokeDasharray='8 10';p.style.strokeDashoffset=0;
    p.animate([{strokeDashoffset:36},{strokeDashoffset:0}],{duration:900/fac,iterations:Infinity});
    const dot=document.createElementNS(ns,'circle');dot.setAttribute('r','5');dot.setAttribute('fill','#FFD54F');dot.setAttribute('filter','url(#g)');
    dot.innerHTML='<animateMotion dur="'+(2.2/fac)+'s" repeatCount="indefinite" path="'+d+'"/>';svg.appendChild(dot)},720/fac))},quand))});
 const fin=total+pas*1.5,pb=document.getElementById('pb');pb.style.transition='none';pb.style.width='0';
 setTimeout(()=>{pb.style.transition='width '+fin+'ms linear';pb.style.width='100%'},30)}
function esc(s){return s.replace(/[&<>"]/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;'}[c]))}
function fit(t,w){let f=14;while(f>9&&t.getComputedTextLength&&t.getComputedTextLength()>w){f--;t.style.fontSize=f+'px'}}
window.onload=()=>setTimeout(jouer,150);window.onresize=jouer;
</script></body></html>"""
    }
}

/** Plein écran noir qui joue l'animation d'un schéma. */
class SchemaActivity : AppCompatActivity() {
    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val schema = SchemaAnime.decoder(intent?.data)
        if (schema == null) { finish(); return }
        title = schema.titre
        val vue = WebView(this).apply {
            setBackgroundColor(Color.BLACK)
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            settings.javaScriptEnabled = true
            // Page locale uniquement : aucun accès réseau ni fichier.
            settings.blockNetworkLoads = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.cacheMode = WebSettings.LOAD_NO_CACHE
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: android.webkit.WebResourceRequest?) = true
            }
        }
        setContentView(vue)
        vue.loadDataWithBaseURL(null, SchemaAnime.html(schema), "text/html", "UTF-8", null)
    }
}
