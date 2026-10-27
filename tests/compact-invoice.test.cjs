const {test,before,after}=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs/promises'),{chromium}=require('playwright-core');
let browser,xml;
before(async()=>{browser=await chromium.launch({executablePath:process.env.CHROME_BIN||'/usr/bin/chromium',args:['--no-sandbox']});xml=await fs.readFile('tests/fixtures/acquisition.xml','utf8')});after(async()=>browser?.close());
async function page(saved=false){const p=await browser.newPage({viewport:{width:390,height:844}});await p.route('**/*',async r=>r.fulfill({contentType:r.request().url().endsWith('.js')?'text/javascript':'text/html',body:await fs.readFile(r.request().url().endsWith('.js')?'fiscal-cost.js':'index.html','utf8')}));await p.addInitScript(saved=>{window.NativeApp={postMessage:()=>{}};if(saved&&!localStorage.getItem('icmsStColumnOrderV2')){localStorage.setItem('icmsStColumnOrderV2',JSON.stringify(['1','0','7','2','3','4','5','6','8','9','10','11','12','13','14','15']));}},saved);await p.goto('https://appassets.androidplatform.net/assets/site/index.html');for(const f of ['android-appearance.js','android-invoice.js'])await p.evaluate(await fs.readFile('android/app/src/main/assets/'+f,'utf8'));await p.evaluate(async x=>{applyNativeAppearance({fontPercent:100,dark:false});await loadMultipleXmls([new File([x],'custos.xml')]);},xml);return p;}
test('Android table contains only original product names and calculates documented units without controls',async()=>{
 const p=await page();assert.equal(await p.locator('thead th:visible').count(),8);
 assert.equal(await p.evaluate(()=>Array.from(document.querySelectorAll('td[data-column-key="1"]')).every((e,i)=>e.textContent===currentItems[i].name&&e.children.length===1)),true);
 assert.equal(await p.locator('#itemsBody input,#itemsBody details,#itemsBody button,#productCostDialog,.cost-audit-print').count(),0);
 assert.match(await p.locator('td[data-column-key="16"]').nth(1).innerText(),/0,50.*UN/);
 assert.match(await p.locator('[data-unit-price="1"]').innerText(),/6,60/);
 assert.equal(await p.evaluate(()=>buildFiscalCSV().rows[1].at(-2)),'0.50');
 for(const percent of [90,100,140,160])for(const dark of [false,true]){await p.evaluate(o=>{applyNativeAppearance(o);fitNativeInvoiceColumns()}, {fontPercent:percent,dark});assert.equal(await p.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1),true);}
 await p.close();
});
test('16-column preferences migrate without losing order; IPI visibility and order survive reload',async()=>{
 const p=await page(true);assert.equal(await p.locator('thead th').count(),17);assert.equal(await p.locator('thead th').first().getAttribute('data-column-key'),'1');assert.equal(await p.locator('th[data-column-key="16"]').isVisible(),true);await p.locator('#columnSelectorDetails summary').click();await p.locator('.column-checkbox[data-column-key="16"]').uncheck();await p.evaluate(()=>moveColumn('16','0'));await p.reload();for(const f of ['android-appearance.js','android-invoice.js'])await p.evaluate(await fs.readFile('android/app/src/main/assets/'+f,'utf8'));assert.equal(await p.locator('th[data-column-key="16"]').isVisible(),false);assert.equal(await p.evaluate(()=>getColumnOrder().indexOf('16')+1===getColumnOrder().indexOf('0')),true);await p.close();
});
test('IPI zero and absent are distinct and the compact asset refuses the public site',async()=>{
 const p=await page();assert.match(await p.locator('td[data-column-key="16"]').nth(2).innerText(),/0,00/);const noIpi=xml.replace(/<IPI><IPITrib>.*?<\/IPITrib><\/IPI>/g,'').replace('<vIPI>17</vIPI>','');await p.evaluate(async x=>loadMultipleXmls([new File([x],'sem.xml')]),noIpi);assert.match(await p.locator('td[data-column-key="16"]').first().innerText(),/Não informado/);await p.close();const publicPage=await browser.newPage();await publicPage.route('**/*',async r=>r.fulfill({contentType:'text/html',body:await fs.readFile('index.html','utf8')}));await publicPage.goto('https://example.org/');await publicPage.evaluate(await fs.readFile('android/app/src/main/assets/android-invoice.js','utf8'));assert.equal(await publicPage.evaluate(()=>!!window.__compactInvoice),false);await publicPage.close();
});

test('automatic packaging XML matrix reconciles money, counts, taxes, ambiguity, markup, reload and print',async()=>{
 const p=await page();await p.evaluate(async x=>loadMultipleXmls([new File([x],'automatic.xml')]),await fs.readFile('tests/fixtures/automatic-units.xml','utf8'));
 const expected=JSON.parse(await fs.readFile('tests/fixtures/automatic-units.json','utf8'));
 const result=await p.evaluate(()=>currentFinance.items.map(r=>({factor:r.conversion.factor,units:r.conversion.units,cost:r.costCents,price:NfeCost.price(r,20),base:r.conversion.base,ipi:r.ipiCents,st:r.stCents,equivalent:r.conversion.equivalent})));
 assert.deepEqual(result,expected);
 assert.deepEqual(await p.evaluate(()=>buildFiscalCSV().rows.map(r=>r[5])),expected.map(r=>(r.price/100).toFixed(2)));
 assert.equal(await p.evaluate(()=>currentFinance.warnings.length),0);
 assert.equal(await p.evaluate(()=>{const d=structuredClone(loadedNotes[activeNoteIndex].data);d.items[1].productCode=d.items[0].productCode;const r=NfeCost.calculate(d,{bad:{factor:99,confirmed:true}},{});return r.items[0].conversion.factor===12&&r.items[1].conversion.factor===2}),true);
 assert.equal(await p.evaluate(()=>Array.from(document.querySelectorAll('td[data-column-key="1"]')).every((e,i)=>e.textContent===currentItems[i].name)),true);
 await p.evaluate(()=>{markupPercentInput.value='12.5';markupPercentInput.dispatchEvent(new Event('input',{bubbles:true}));});
 assert.equal(await p.evaluate(()=>NfeCost.price(currentFinance.items[0],getMarkupPercent())),238);
 await p.reload();for(const f of ['android-appearance.js','android-invoice.js'])await p.evaluate(await fs.readFile('android/app/src/main/assets/'+f,'utf8'));
 assert.equal(await p.evaluate(()=>currentFinance.items[0].conversion.units),120);
 await p.setViewportSize({width:411,height:535});await p.evaluate(()=>{applyNativeAppearance({fontPercent:100,dark:true});fitNativeInvoiceColumns();document.querySelector('#itemsBody tr').scrollIntoView({block:'start',behavior:'instant'});});
 assert.equal(await p.evaluate(()=>Array.from(document.querySelectorAll('#itemsBody tr')).filter(r=>{const b=r.getBoundingClientRect();return b.top>=0&&b.bottom<=innerHeight}).length>=8),true);
 await p.evaluate(()=>{delete loadedNotes[activeNoteIndex].data.fiscalRaw;renderNoteData(loadedNotes[activeNoteIndex].data);});
 assert.equal(await p.evaluate(()=>currentFinance.items[0].conversion.base),'CX');
 assert.equal(await p.evaluate(()=>currentFinance.items[0].ready),false);
 assert.match(await p.locator('td[data-column-key="7"]').first().innerText(),/CX/);
 await p.emulateMedia({media:'print'});assert.equal(await p.locator('#itemsBody button,#itemsBody input,#itemsBody .cost-audit-print').count(),0);await p.close();
});
