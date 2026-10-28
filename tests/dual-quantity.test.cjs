const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs/promises');
const {chromium}=require('playwright-core');
// Expected values are fixture fractions rounded only at the final monetary value.
const snapshot=()=>currentFinance.items.map((r,i)=>{
 const q=document.querySelectorAll('td[data-column-key="7"]')[i];
 const normalized=e=>e?e.textContent.trim().replace(/\s+/g,' ').toUpperCase():null;
 return {primary:normalized(q.querySelector('.quantity-primary')),packaging:normalized(q.querySelector('.quantity-packaging')),cost:r.costCents,price:NfeCost.price(r,getMarkupPercent()),packageCost:r.packageCostCents??null,packagePrice:r.packagePriceCents??null,ipi:r.ipiCents,st:r.stCents,total:r.totalCents,value:r.basicCents,net:r.netProductUnitCents};
});
test('two quantities share one compact column with automatic case and weight pricing, safe conflicts and persistence',async()=>{
 const browser=await chromium.launch({executablePath:process.env.CHROME_BIN||'/usr/bin/chromium',args:['--no-sandbox']});
 try{
  const p=await browser.newPage({viewport:{width:411,height:731}});
  await p.route('**/*',async route=>route.fulfill({contentType:route.request().url().endsWith('.js')?'text/javascript':'text/html',body:await fs.readFile(route.request().url().endsWith('.js')?'fiscal-cost.js':'index.html','utf8')}));
  await p.addInitScript(()=>{window.NativeApp={postMessage:()=>{}};});
  await p.goto('https://appassets.androidplatform.net/assets/site/index.html');
  const inject=async()=>{for(const name of ['android-appearance.js','android-invoice.js'])await p.evaluate(await fs.readFile('android/app/src/main/assets/'+name,'utf8'));};
  await inject();
  await p.evaluate(async xml=>{applyNativeAppearance({fontPercent:100,dark:true});markupPercentInput.value='20';await loadMultipleXmls([new File([xml],'dual-quantity.xml')]);},await fs.readFile('tests/fixtures/dual-quantity.xml','utf8'));
  const expected=JSON.parse(await fs.readFile('tests/fixtures/dual-quantity.json','utf8')).map(r=>({...r,primary:r.primary.toUpperCase()}));
  assert.deepEqual(await p.evaluate(snapshot),expected);
  assert.equal(await p.locator('thead th').count(),17,'do not create a packaging column');
  assert.equal(await p.locator('th[data-column-key="2"] .header-label').innerText(),'VALOR');
  assert.equal(await p.locator('th[data-column-key="4"] .header-label').innerText(),'VALOR COM DESCONTO');
  assert.equal(await p.locator('thead').innerText().then(s=>/Compra básica/i.test(s)),false);
  assert.equal(await p.locator('thead th:visible').count(),8);
  assert.equal(await p.locator('td[data-column-key="4"]').nth(13).evaluate(e=>e.firstChild.textContent.replace(/\s/g,'')),'R$4,96/UN','VALOR COM DESCONTO excludes tax/freight rather than relabeling acquisition cost');
  assert.match(await p.locator('td[data-column-key="4"]').nth(13).locator('.package-value').innerText(),/59,50.*CX/);
  assert.equal(await p.evaluate(()=>currentFinance.items[13].costCents),583,'suggestion continues using true acquisition cost');
  assert.equal(await p.locator('#itemsBody input,#itemsBody button,#itemsBody details,#productCostDialog').count(),0);
  assert.equal(await p.evaluate(()=>currentFinance.warnings.length),0);
  assert.equal(await p.evaluate(()=>Array.from(document.querySelectorAll('td[data-column-key="1"]')).every((e,i)=>e.textContent===currentItems[i].name)),true);
  assert.equal(await p.evaluate(()=>Array.from(document.querySelectorAll('.quantity-packaging')).every(e=>{const first=e.parentElement.querySelector('.quantity-primary');return !!first&&e.getBoundingClientRect().top>=first.getBoundingClientRect().bottom-1;})),true,'individual measure is physically above packaging');
  assert.equal(await p.locator('td[data-column-key="4"]').nth(0).locator('.package-value').count(),1);
  assert.equal(await p.locator('td[data-column-key="5"]').nth(3).locator('.package-value').count(),1);
  assert.equal(await p.locator('td[data-column-key="4"]').nth(6).locator('.package-value').count(),0,'no artificial case price for avulso');
  assert.equal(await p.evaluate(()=>currentFinance.items[7].packaging.unit),'FD');
  assert.equal(await p.evaluate(()=>Number(currentFinance.items[3].packaging.quantity)),3);
  assert.deepEqual(await p.evaluate(()=>buildFiscalCSV().rows.map(r=>r[5])),expected.map(r=>(r.price/100).toFixed(2)));
  for(const viewport of [{width:411,height:731},{width:731,height:411}]){
   await p.setViewportSize(viewport);
   for(const fontPercent of [90,100,140,160])for(const dark of [false,true]){
    await p.evaluate(o=>{applyNativeAppearance(o);fitNativeInvoiceColumns();}, {fontPercent,dark});
    assert.equal(await p.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1),true);
    assert.equal(await p.evaluate(()=>Array.from(document.querySelectorAll('td[data-column-key="7"]')).every(e=>e.scrollWidth<=e.clientWidth+1)),true,'both quantities fit their column at every scale');
   }
  }
  await p.evaluate(()=>{markupPercentInput.value='12.5';markupPercentInput.dispatchEvent(new Event('input',{bubbles:true}));});
  assert.deepEqual(await p.evaluate(()=>[NfeCost.price(currentFinance.items[0],getMarkupPercent()),currentFinance.items[0].packagePriceCents]),[238,2851]);
  await p.reload();await inject();
  assert.deepEqual(await p.evaluate(()=>[currentFinance.items[3].conversion.units,Number(currentFinance.items[3].packaging.quantity),NfeCost.price(currentFinance.items[0],getMarkupPercent()),currentFinance.items[0].packagePriceCents]),[81,3,238,2851]);
  await p.emulateMedia({media:'print'});
  assert.equal(await p.locator('.quantity-packaging').first().isVisible(),true);
  assert.equal(await p.locator('#itemsBody button,#itemsBody input').count(),0);
 }finally{await browser.close();}
});
