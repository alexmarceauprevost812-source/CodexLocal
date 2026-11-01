"use strict";
(() => {
  const KEY = "lexos.mini-studio.v1";
  const byId = id => document.getElementById(id);
  const status = message => { byId("status").textContent = message; };
  const newProject = name => ({ id: "p-" + Date.now() + "-" + Math.random().toString(36).slice(2, 9), name, title: "", body: "" });
  let projects;
  try {
    const saved = JSON.parse(localStorage.getItem(KEY) || "null");
    projects = Array.isArray(saved) ? saved.filter(p => p && typeof p.id === "string" && typeof p.name === "string" && typeof p.title === "string" && typeof p.body === "string").slice(0, 100) : [];
  } catch { projects = []; }
  if (!projects.length) projects = [newProject("Premier projet")];
  let active = projects[0].id;
  function persist() {
    try { localStorage.setItem(KEY, JSON.stringify(projects)); return true; }
    catch { status("Stockage local indisponible : copie tes notes avant de quitter."); return false; }
  }
  function render() {
    const select = byId("project");
    select.replaceChildren();
    for (const p of projects) {
      const option = document.createElement("option");
      option.value = p.id; option.textContent = p.name; select.append(option);
    }
    select.value = active;
    const p = projects.find(p => p.id === active);
    byId("title").value = p.title;
    byId("body").value = p.body;
    byId("deleteProject").disabled = projects.length === 1;
  }
  function capture() {
    const p = projects.find(p => p.id === active);
    p.title = byId("title").value;
    p.body = byId("body").value;
  }
  byId("save").addEventListener("click", () => {
    capture(); if (persist()) status("Enregistré sur cet appareil.");
  });
  byId("project").addEventListener("change", e => {
    capture(); if (!persist()) return;
    active = e.target.value; render(); status("Projet ouvert.");
  });
  byId("addProject").addEventListener("click", () => {
    if (projects.length >= 100) return status("Maximum de 100 projets.");
    const name = prompt("Nom du nouveau projet ?");
    if (name === null || !name.trim()) return;
    capture(); const p = newProject(name.trim().slice(0, 80));
    projects.push(p); active = p.id; persist(); render(); status("Projet créé.");
  });
  byId("deleteProject").addEventListener("click", () => {
    if (projects.length === 1 || !confirm("Supprimer ce projet et ses notes locales ?")) return;
    projects = projects.filter(p => p.id !== active);
    active = projects[0].id; persist(); render(); status("Projet supprimé.");
  });
  byId("export").addEventListener("click", () => {
    capture();
    const p = projects.find(p => p.id === active);
    const blob = new Blob([p.title + "\n\n" + p.body], { type: "text/plain;charset=utf-8" });
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url; link.download = "mini-studio-notes.txt";
    document.body.append(link); link.click(); link.remove();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
    status("Export demandé. Vérifie le téléchargement.");
  });
  window.addEventListener("beforeunload", () => { capture(); persist(); });
  render(); persist();
})();
