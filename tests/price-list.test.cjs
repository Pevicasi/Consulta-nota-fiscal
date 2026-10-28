const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs/promises');
const {chromium}=require('playwright-core');

test('one app-only price-list button prints the selected note using every displayed sale price without recalculation',async()=>{
 const browser=await chromium.launch({executablePath:process.env.CHROME_BIN||'/usr/bin/chromium',args:['--no-sandbox']});
 try{
  const p=await browser.newPage({viewport:{width:411,height:731}});
  await p.route('**/*',async route=>route.fulfill({contentType:route.request().url().endsWith('.js')?'text/javascript':'text/html',body:await fs.readFile(route.request().url().endsWith('.js')?'fiscal-cost.js':'index.html','utf8')}));
  await p.addInitScript(()=>{window.__priceMessages=[];window.NativeApp={postMessage:s=>window.__priceMessages.push(JSON.parse(s))};});
  await p.goto('https://appassets.androidplatform.net/assets/site/index.html');
  for(const f of ['android-appearance.js','android-invoice.js','android-price-print.js'])await p.evaluate(await fs.readFile('android/app/src/main/assets/'+f,'utf8'));
  const before=await p.locator('button').count();
  await p.evaluate(await fs.readFile('android/app/src/main/assets/android-price-print.js','utf8'));
  assert.equal(await p.locator('button').count(),before,'reinjection does not add a second control');
  assert.equal(await p.locator('#printPriceListBtn').count(),1);
  assert.equal(await p.locator('#printPriceListBtn').innerText(),'Imprimir lista de preços');
  const xmls=await Promise.all(['dual-quantity.xml','acquisition.xml'].map(f=>fs.readFile('tests/fixtures/'+f,'utf8')));
  await p.evaluate(async xmls=>{applyNativeAppearance({fontPercent:100,dark:true});await loadMultipleXmls(xmls.map((x,i)=>new File([x],'prices-'+i+'.xml')));showNote(1);markupPercentInput.value='12.5';markupPercentInput.dispatchEvent(new Event('input',{bubbles:true}));},xmls);
  assert.equal(await p.evaluate(()=>currentItems.length),5);
  const current=await p.evaluate(()=>currentItems.map((item,i)=>({name:item.name,price:document.querySelector('td[data-unit-price="'+i+'"] strong').textContent.trim()})));
  assert.match(current[4].price,/kg/i,'existing kg price is retained');
  const notes=await p.evaluate(()=>JSON.stringify({loadedNotes,currentItems,currentFinance,activeNoteIndex}));
  await p.locator('#printPriceListBtn').click();
  const first=await p.evaluate(()=>window.__priceMessages.filter(m=>m.type==='printPriceList').at(-1));
  assert.ok(first,'native print request is sent');
  assert.deepEqual(JSON.parse(first.value),current);
  assert.equal(await p.evaluate(()=>JSON.stringify({loadedNotes,currentItems,currentFinance,activeNoteIndex})),notes,'printing leaves fiscal data and note selection intact');
  await p.evaluate(()=>{
   document.querySelectorAll('#itemsBody tr')[0].style.display='none';
   document.querySelector('td[data-unit-price="4"] strong').textContent='R$ 123,45 / kg';
   NfeCost.price=()=>{throw new Error('Printing must not recalculate sale prices');};
   NfeCost.calculate=()=>{throw new Error('Printing must not recalculate acquisition');};
  });
  await p.locator('#printPriceListBtn').click();
  const second=await p.evaluate(()=>JSON.parse(window.__priceMessages.filter(m=>m.type==='printPriceList').at(-1).value));
  assert.equal(second.length,5,'hidden rows are still included');
  assert.deepEqual(second[0],current[0]);
  assert.deepEqual(second[4],{name:current[4].name,price:'R$ 123,45 / kg'});
  assert.equal(second.every(r=>Object.keys(r).sort().join(',')==='name,price'),true,'no costs or fiscal fields enter the receipt');
  assert.equal(await p.locator('thead th').count(),17);
  assert.equal(await p.evaluate(()=>Array.from(document.querySelectorAll('td[data-column-key="1"]')).every((e,i)=>e.textContent===currentItems[i].name)),true);
  for(const target of ['https://example.org/','https://appassets.androidplatform.net/assets/site/consulta_precos_v17.html']){
   const q=await browser.newPage();await q.route('**/*',r=>r.fulfill({contentType:'text/html',body:'<!doctype html><div id="result"><button id="printBtn">Imprimir</button></div>'}));await q.addInitScript(()=>{window.NativeApp={postMessage:()=>{}};});await q.goto(target);await q.evaluate(await fs.readFile('android/app/src/main/assets/android-price-print.js','utf8'));assert.equal(await q.locator('#printPriceListBtn').count(),0,'public/external and other local pages remain unchanged');await q.close();
  }
 }finally{await browser.close();}
});
