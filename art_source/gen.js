const {chromium}=require('playwright');const fs=require('fs');
(async()=>{const b=await chromium.launch();const pg=await b.newPage();pg.on('pageerror',e=>console.log('ERR',e.message));
await pg.setContent('<html><body></body></html>');await pg.addScriptTag({path:__dirname+'/art.js'});
const out=await pg.evaluate(()=>window.makeAll());fs.mkdirSync(__dirname+'/out',{recursive:true});
for(const [k,v] of Object.entries(out)){fs.writeFileSync(__dirname+'/out/'+k+'.png',Buffer.from(v.split(',')[1],'base64'))}
console.log(Object.keys(out).join(' '));await b.close()})();
