const {test,before,after}=require('node:test');const assert=require('node:assert/strict');const fs=require('node:fs/promises');const {chromium}=require('playwright-core');
let browser,xml,appearance;
before(async()=>{browser=await chromium.launch({executablePath:process.env.CHROME_BIN||'/usr/bin/chromium',args:['--no-sandbox']});xml=await fs.readFile('tests/fixtures/acquisition.xml','utf8');appearance=await fs.readFile('android/app/src/main/assets/android-appearance.js','utf8');});
after(async()=>browser?.close());
async function page(viewport={width:390,height:844}){const p=await browser.newPage({viewport,acceptDownloads:true});await p.route('**/*',async route=>{const path=new URL(route.request().url()).pathname;if(path.endsWith('fiscal-cost.js'))await route.fulfill({contentType:'text/javascript',body:await fs.readFile('fiscal-cost.js','utf8')});else if(route.request().isNavigationRequest())await route.fulfill({contentType:'text/html',body:await fs.readFile('index.html','utf8')});else await route.abort();});await p.addInitScript(()=>window.NativeApp={postMessage:()=>{}});await p.goto('https://appassets.androidplatform.net/assets/site/index.html');await p.evaluate(appearance);await p.evaluate(async source=>loadMultipleXmls([new File([source],'custos.xml')]),xml);return p;}
async function factor(p,index,value){const detail=p.locator(`.cost-details[data-cost-item="${index}"]`);await detail.evaluate(e=>e.open=true);await p.locator(`[data-cost-field="factor"][data-cost-index="${index}"]`).fill(value);await p.locator(`[data-cost-field="confirmed"][data-cost-index="${index}"]`).check();}
test('IPI, all acquisition components and confirmed physical units drive UI, persistence, actual CSV and print',async()=>{
 const p=await page();assert.equal(await p.evaluate(()=>currentFinance.classifiedCents),42700);assert.equal(await p.evaluate(()=>currentFinance.totals.ipi),1700);
 assert.match(await p.locator('[data-unit-price="0"]').innerText(),/69,00/);assert.match(await p.locator('[data-unit-price="1"]').innerText(),/—/);
 await factor(p,1,'12');assert.match(await p.locator('[data-unit-price="1"]').innerText(),/6,60/);await factor(p,2,'6');await factor(p,3,'10');
 assert.deepEqual(await p.evaluate(()=>currentFinance.items.map(r=>NfeCost.price(r,20))),[6900,660,1200,360,2400]);
 await p.locator('#markupPercent').fill('0');await p.locator('#markupPercent').dispatchEvent('input');assert.match(await p.locator('[data-unit-price="1"]').innerText(),/5,50/);
 await p.locator('#markupPercent').fill('12.5');await p.locator('#markupPercent').dispatchEvent('input');assert.match(await p.locator('[data-unit-price="1"]').innerText(),/6,19/);
 await p.reload();assert.equal(await p.evaluate(()=>currentFinance.items[1].conversion.factor),12);assert.match(await p.locator('[data-unit-price="1"]').innerText(),/6,19/);
 const download=p.waitForEvent('download');await p.locator('#csvBtn').click();const file=await download,csv=await fs.readFile(await file.path(),'utf8');assert.match(csv,/IPI item/);assert.match(csv,/"6.19"/);assert.match(csv,/"CX"/);assert.match(csv,/"24"/);
 await p.emulateMedia({media:'print'});assert.equal(await p.locator('.conversion-editor').first().isVisible(),false);assert.equal(await p.locator('.cost-audit-print').first().isVisible(),true);assert.match(await p.locator('.cost-audit-print').first().innerText(),/IPI: R\$\s?5,00/);await p.close();
});
test('unclassified invoice differences, non-taxed IPI, ambiguous packages, manual extras and multiple notes remain explicit',async()=>{
 const p=await page();const changed=xml.replace('<nNF>900</nNF>','<nNF>901</nNF>').replace('<vNF>427</vNF>','<vNF>429.34</vNF>');await p.evaluate(async source=>loadMultipleXmls([new File([source],'outro.xml')]),changed);
 assert.equal(await p.evaluate(()=>currentFinance.differenceCents),234);assert.equal(await p.evaluate(()=>currentFinance.totals.ipi),1700);assert.match(await p.locator('[data-unit-price="0"]').innerText(),/—/);assert.match(await p.locator('#fiscalWarnings').innerText(),/Não foi presumida como IPI/);
 const untaxed=xml.replace('<IPITrib><vIPI>5</vIPI></IPITrib>','<IPINT><CST>53</CST></IPINT>').replace('<vIPI>17</vIPI>','<vIPI>12</vIPI>').replace('<vNF>427</vNF>','<vNF>422</vNF>');
 await p.evaluate(async sources=>loadMultipleXmls(sources.map((s,i)=>new File([s],i+'.xml'))),[xml,untaxed]);assert.equal(await p.locator('#notesTabs button').count(),2);await p.locator('#notesTabs button').nth(1).click();assert.equal(await p.evaluate(()=>currentFinance.items[0].parts.ipi),0);
 await p.locator('#fiscalBreakdown').evaluate(e=>e.open=true);assert.equal(await p.evaluate(source=>{const f=NfeCost.calculate(parseXML(source));return f.items[0].parts.ipi===0&&!f.ready;},untaxed.replace('<vIPI>12</vIPI>','<vIPI>17</vIPI>').replace('<vNF>422</vNF>','<vNF>427</vNF>')),true);
 await p.locator('[data-note-option="extra"]').fill('4');assert.equal(await p.evaluate(()=>currentFinance.extraCents),400);assert.equal(await p.evaluate(()=>currentFinance.officialCents),42200);assert.equal(await p.evaluate(()=>currentFinance.costCents),42600);
 await p.locator('#notesTabs button').first().click();assert.equal(await p.evaluate(()=>currentFinance.extraCents),0);assert.equal(await p.evaluate(()=>currentFinance.totals.ipi),1700);await p.evaluate(await fs.readFile('android/app/src/main/assets/android-bridge.js','utf8'));
 await p.evaluate(()=>{const old=JSON.parse(JSON.stringify(loadedNotes[0].data));delete old.fiscalRaw;delete old.issuerId;loadedNotes=[{file:{name:'old.xml'},data:old}];loadedXmls=loadedNotes;activeNoteIndex=0;renderNoteData(old)});
 assert.equal(await p.evaluate(()=>currentFinance.ready),false);await p.evaluate(source=>acceptNativeXmlDocuments([{name:'reimportado.xml',text:source}]),xml);
 assert.equal(await p.locator('#notesTabs button').count(),1);assert.equal(await p.evaluate(()=>currentFinance.totals.ipi),1700);assert.match(await p.locator('#status').innerText(),/antiga.*atualizada/);
 await p.close();
});
test('app column geometry is compact and stable with long descriptions, 14/16 columns and reordered fields',async()=>{
 for(const viewport of [{width:390,height:844},{width:844,height:390}]){
  const p=await page(viewport);
  for(const fontPercent of [90,100,140,160])for(const dark of [false,true]){
   await p.evaluate(o=>{applyNativeAppearance(o);fitNativeInvoiceColumns();}, {fontPercent,dark});
   const sizes=await p.evaluate(()=>Object.fromEntries(['0','7','1'].map(k=>[k,document.querySelector(`th[data-column-key="${k}"]`).getBoundingClientRect().width])));
   assert.ok(sizes['1']>sizes['7']*1.2,JSON.stringify(sizes));assert.ok(sizes['1']>sizes['0']*2.5,JSON.stringify(sizes));assert.ok(sizes['7']<11*16*fontPercent/100,JSON.stringify(sizes));if(fontPercent<=100)assert.ok(await p.locator('.table-wrap').evaluate((e,s)=>s['0']+s['7']+s['1']<=e.clientWidth+2,sizes),JSON.stringify(sizes));
   assert.equal(await p.locator('.table-wrap').evaluate(e=>e.scrollWidth>e.clientWidth),true);assert.equal(await p.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1),true);
   const q=await p.locator('th[data-column-key="7"] .header-label').evaluate(e=>({h:e.getBoundingClientRect().height,l:parseFloat(getComputedStyle(e).lineHeight)}));assert.ok(q.h<=q.l+2);
  }
  await p.locator('#columnSelectorDetails summary').click();await p.locator('.column-checkbox[data-column-key="14"]').uncheck();await p.locator('.column-checkbox[data-column-key="15"]').uncheck();
  await p.evaluate(()=>{moveColumn('1','0');fitNativeInvoiceColumns();});assert.equal(await p.locator('#draggableHeaderRow th').first().getAttribute('data-column-key'),'1');
  assert.equal(await p.locator('#draggableHeaderRow th:visible').count(),14);
  await p.screenshot({path:`test-results/cost-layout-${viewport.width}.png`,fullPage:true});await p.close();
 }
});
