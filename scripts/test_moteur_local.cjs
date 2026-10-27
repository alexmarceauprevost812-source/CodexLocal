/* Run with Playwright installed: node scripts/test_moteur_local.cjs */
const assert = require('node:assert/strict');
const http = require('node:http');
const fs = require('node:fs');
const path = require('node:path');
const {chromium} = require('playwright');
const base = path.resolve(__dirname, '../app/src/main/assets/moteurs');
const server = http.createServer((req,res) => {
  const file = req.url === '/' ? 'index.html' : req.url === '/moteurs/lexapp-v1.js' ? 'lexapp-v1.js' : null;
  if (!file) {res.writeHead(404).end();return;}
  res.setHeader('Content-Type', file.endsWith('.js') ? 'text/javascript' : 'text/html');
  res.end(fs.readFileSync(path.join(base,file)));
});
(async()=>{
  await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
  const browser=await chromium.launch({headless:true});
  try {
    const page=await browser.newPage({viewport:{width:360,height:740}});
    const errors=[];page.on('pageerror',e=>errors.push(e.message));
    const external=[];page.on('request',r=>{if(!r.url().startsWith(`http://127.0.0.1:${server.address().port}/`))external.push(r.url());});
    await page.goto(`http://127.0.0.1:${server.address().port}/`);
    await page.getByLabel('Ma note').fill('Mon projet LexOS');
    await page.getByRole('button',{name:'Enregistrer',exact:true}).click();
    assert.match(await page.getByRole('status').textContent(),/enregistrée/);
    await page.reload();assert.equal(await page.getByLabel('Ma note').inputValue(),'Mon projet LexOS');
    await page.getByRole('button',{name:'Animations 2D',exact:true}).click();
    await page.getByRole('button',{name:'Animer',exact:true}).click();
    assert.equal(await page.locator('canvas').count(),1);
    assert.ok(await page.locator('canvas').evaluate(c=>c.width>0 && c.height>0));
    await page.getByRole('button',{name:'Schéma',exact:true}).click();
    assert.equal(await page.locator('svg rect').count(),4);
    assert.equal(await page.locator('svg path[marker-end]').count(),3);
    assert.equal(await page.locator('canvas').count(),0);
    assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=360));
    // Labels are text, and diagram ids cannot silently collide.
    assert.equal(await page.evaluate(()=>{const z=document.createElement('div');LexApp.diagram(z,{nodes:[{id:'x',label:'<img src=x onerror=alert(1)>'}]});return z.querySelectorAll('img').length;}),0);
    assert.equal(await page.evaluate(()=>{try{LexApp.diagram(document.createElement('div'),{nodes:[{id:'x'},{id:'x'}]});return false;}catch(_){return true;}}),true);
    await page.emulateMedia({reducedMotion:'reduce'});
    await page.getByRole('button',{name:'Animations 2D',exact:true}).click();
    await page.getByRole('button',{name:'Animer',exact:true}).click();
    assert.equal(await page.evaluate(()=>document.getAnimations().length),0);
    await page.getByRole('button',{name:'Mes notes',exact:true}).click();
    assert.equal(await page.getByLabel('Ma note').inputValue(),'Mon projet LexOS');
    assert.deepEqual(errors,[]);assert.deepEqual(external,[]);
    console.log('OK: navigation, stockage persistant, animations, schémas, mobile, mode sans animation, aucune requête externe.');
  } finally {await browser.close();server.close();}
})().catch(e=>{console.error(e);server.close();process.exitCode=1;});
