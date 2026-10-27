const {test,before,after}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs/promises');
const path=require('node:path');
const {chromium}=require('playwright-core');
let browser,appearance;
before(async()=>{
 browser=await chromium.launch({executablePath:process.env.CHROME_BIN||'/usr/bin/chromium',args:['--no-sandbox']});
 appearance=await fs.readFile('android/app/src/main/assets/android-appearance.js','utf8');
 await fs.mkdir('test-results',{recursive:true});
});
after(async()=>browser?.close());
async function page(file='index.html',viewport={width:360,height:800},origin='https://appassets.androidplatform.net'){
 const p=await browser.newPage({viewport});
 await p.route('**/*',async r=>{
  if(r.request().isNavigationRequest()) await r.fulfill({contentType:'text/html; charset=utf-8',body:await fs.readFile(path.join(process.cwd(),file))});
  else await r.abort();
 });
 await p.addInitScript(()=>{window.NativeApp={postMessage:()=>{}}});
 await p.goto(origin+'/assets/site/'+file);await p.evaluate(appearance);return p;
}
const invoice=`<NFe><infNFe><ide><nNF>901</nNF></ide><det nItem="1"><prod><xProd>Produto de nome extenso para conferir legibilidade fiscal sem cortes</xProd><qCom>2</qCom><vUnCom>10</vUnCom><vProd>20</vProd><vDesc>5.50</vDesc></prod><imposto><ICMS><ICMS10><vICMSST>4</vICMSST></ICMS10></ICMS></imposto></det><total><ICMSTot><vProd>20</vProd><vDesc>5.50</vDesc><vNF>18.50</vNF></ICMSTot></total></infNFe></NFe>`;
test('app appearance scales every local page without clipped controls at mobile and landscape sizes',{timeout:120000},async()=>{
 for(const viewport of [{width:360,height:800},{width:640,height:360}])for(const file of ['index.html','consulta_precos_v15.html','consulta_precos_v16.html','consulta_precos_v17.html']){
  const p=await page(file,viewport);
  if(file==='index.html') await p.evaluate(async xml=>loadMultipleXmls([new File([xml],'nota.xml',{type:'text/xml'})]),invoice);
  for(const fontPercent of [90,120,160])for(const dark of [false,true]){
   await p.evaluate(o=>applyNativeAppearance(o),{dark,fontPercent});
   assert.equal(await p.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1),true,`${file} ${fontPercent} dark=${dark}: page overflow`);
   assert.equal(await p.evaluate(()=>getComputedStyle(document.body).fontSize),`${16*fontPercent/100}px`);
   const contrasts=await p.evaluate(()=>{
    const lum=color=>{const c=color.match(/[\d.]+/g).slice(0,3).map(Number).map(v=>{v/=255;return v<=.04045?v/12.92:((v+.055)/1.055)**2.4});return .2126*c[0]+.7152*c[1]+.0722*c[2]};
    const ratio=(a,b)=>{a=lum(a);b=lum(b);return (Math.max(a,b)+.05)/(Math.min(a,b)+.05)};
    const body=getComputedStyle(document.body),values=[ratio(body.color,body.backgroundColor)];
    const discount=document.querySelector('#totalDiscount');
    if(discount)values.push(ratio(getComputedStyle(discount).color,getComputedStyle(document.querySelector('#discountTotalBox')).backgroundColor));
    return values;
   });
   for(const ratio of contrasts)assert.ok(ratio>=4.5,`${file}: contrast ratio ${ratio}`);
   const clipped=await p.evaluate(()=>Array.from(document.querySelectorAll('button')).filter(el=>el.getClientRects().length && (el.scrollWidth>el.clientWidth+2 || el.scrollHeight>el.clientHeight+2)).map(el=>el.id||el.textContent));
   assert.deepEqual(clipped,[],`${file} ${fontPercent} dark=${dark}`);
   if(file==='index.html'){
    assert.equal(await p.locator('#discountTotalBox').isVisible(),true);
    assert.equal(await p.locator('.table-wrap').evaluate(el=>el.scrollWidth>el.clientWidth),true);
    assert.equal(await p.locator('#itemsBody td:nth-child(6)').innerText().then(s=>/12,00/.test(s)),true);
    assert.equal(await p.locator('#totalDiscount').evaluate(el=>getComputedStyle(el).color),dark?'rgb(141, 240, 187)':'rgb(2, 122, 72)');
   }
   if(viewport.width===360 && fontPercent===160) await p.screenshot({path:`test-results/appearance-${file}-${dark?'dark':'light'}-160.png`,fullPage:true});
  }
  await p.close();
 }
});
test('theme and font changes preserve fiscal data, query input, table scroll and print colors',async()=>{
 const p=await page();await p.evaluate(async xml=>loadMultipleXmls([new File([xml],'nota.xml',{type:'text/xml'})]),invoice);
 await p.locator('.table-wrap').evaluate(el=>el.scrollLeft=200);
 for(const dark of [true,false,true])await p.evaluate(o=>applyNativeAppearance(o),{dark,fontPercent:160});
 assert.equal(await p.locator('#nfeNumber').innerText(),'901');assert.equal(await p.locator('#markupPercent').inputValue(),'20');
 assert.ok(await p.locator('.table-wrap').evaluate(el=>el.scrollLeft>0));
 await p.emulateMedia({media:'print'});
 assert.equal(await p.locator('body').evaluate(el=>getComputedStyle(el).backgroundColor),'rgb(255, 255, 255)');
 assert.equal(await p.locator('#totalDiscount').evaluate(el=>getComputedStyle(el).color),'rgb(2, 122, 72)');await p.close();
 const prices=await page('consulta_precos_v17.html');await prices.fill('#q','arroz');
 await prices.evaluate(()=>applyNativeAppearance({dark:true,fontPercent:160}));assert.equal(await prices.locator('#q').inputValue(),'arroz');await prices.close();
});
test('appearance asset refuses public and third-party origins, and clamps supplied font limits',async()=>{
 for(const origin of ['https://pevicasi.github.io','https://www.nfe.fazenda.gov.br']){
  const p=await page('index.html',undefined,origin);
  assert.equal(await p.evaluate(()=>typeof applyNativeAppearance),'undefined');assert.equal(await p.locator('#nfe-native-appearance').count(),0);await p.close();
 }
 const p=await page();
 for(const [value,expected] of [[10,'90'],[999,'160'],[100,'100']]){
  await p.evaluate(fontPercent=>applyNativeAppearance({dark:false,fontPercent}),value);
  assert.equal(await p.evaluate(()=>document.documentElement.dataset.nativeFontPercent),expected);
 }
 await p.close();
});
