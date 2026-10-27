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
    const field=document.querySelector('input[placeholder]');
    if(field)values.push(ratio(getComputedStyle(field,'::placeholder').color,getComputedStyle(field).backgroundColor));
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
test('Android SAF importer appends batches, reports unreadable/invalid/empty files and survives restored-session duplicates',async()=>{
 const p=await page();const bridge=await fs.readFile('android/app/src/main/assets/android-bridge.js','utf8');await p.evaluate(bridge);
 const docs=Array.from({length:5},(_,i)=>({name:`nota${i+1}.xml`,text:invoice.replace('<nNF>901</nNF>',`<nNF>${1001+i}</nNF>`)}));
 await p.evaluate(d=>acceptNativeXmlDocuments(d),docs.slice(0,2));assert.equal(await p.locator('#notesTabs button').count(),2);
 await p.locator('#notesTabs button').nth(1).click();assert.equal(await p.locator('#nfeNumber').innerText(),'1002');
 await p.evaluate(d=>acceptNativeXmlDocuments(d),docs.slice(2));assert.equal(await p.locator('#notesTabs button').count(),5);assert.equal(await p.locator('#nfeNumber').innerText(),'1002');
 await p.evaluate(d=>acceptNativeXmlDocuments([...d,{name:'vazio.xml',text:''},{name:'invalido.xml',text:'<broken>'},{name:'negado.xml',error:'Permissão negada'}]),docs);
 assert.equal(await p.locator('#notesTabs button').count(),5);assert.match(await p.locator('#status').innerText(),/3 arquivo\(s\) não importado/);assert.equal(await p.locator('#status').isVisible(),true);
 assert.match(await p.locator('#status').innerText(),/5 repetida/);assert.equal(await p.locator('#markupPercent').inputValue(),'20');
 await p.reload();await p.evaluate(bridge);await p.evaluate(d=>acceptNativeXmlDocuments(d),docs);
 assert.equal(await p.locator('#notesTabs button').count(),5);assert.equal(await p.locator('#nfeNumber').innerText(),'1002');
 assert.equal(await p.locator('#discountTotalBox').isVisible(),true);
 await p.evaluate(()=>acceptNativeXmlDocuments([{name:'outro.xml',text:'sem XML'}]));assert.equal(await p.locator('#notesTabs button').count(),5);
 await p.emulateMedia({media:'print'});assert.equal(await p.locator('#nativeImportMore').isVisible(),false);assert.equal(await p.locator('#status').isVisible(),false);
 assert.equal(await p.locator('#discountTotalBox').isVisible(),true);
 await p.close();
});
test('scan link remains a real accessible button and short fiscal headings stay whole at reported scales',async()=>{
 for(const viewport of [{width:360,height:800},{width:640,height:360}]){
  const p=await page('index.html',viewport),q=await page('index.html',viewport);await p.evaluate(()=>addScannedCode('35261012345678000195550010000001231123456784'));await q.evaluate(async xml=>loadMultipleXmls([new File([xml],'nota.xml')]),invoice);
  for(const fontPercent of [90,100,140,160])for(const dark of [false,true]){
   await p.evaluate(o=>applyNativeAppearance(o),{dark,fontPercent});await q.evaluate(o=>applyNativeAppearance(o),{dark,fontPercent});
   const link=p.locator('.scan-item-actions a.btn-link');assert.equal(await link.getAttribute('href'),'https://www.nfe.fazenda.gov.br/portal/consultaRecaptcha.aspx?tipoConsulta=resumo&tipoConteudo=7PhJ+gAVw2g=');
   const styles=await link.evaluate(el=>{const s=getComputedStyle(el);return {background:s.backgroundColor,color:s.color,decoration:s.textDecorationLine,height:el.getBoundingClientRect().height,width:el.getBoundingClientRect().width,scroll:el.scrollWidth,client:el.clientWidth}});
   assert.equal(styles.background,'rgb(36, 71, 157)');assert.equal(styles.color,'rgb(255, 255, 255)');assert.equal(styles.decoration,'none');assert.ok(styles.height>=48);assert.ok(styles.scroll<=styles.client+1);
   await link.focus();assert.equal(await link.evaluate(el=>getComputedStyle(el).outlineStyle),'solid');
   for(const key of ['0','1','7']){
    const box=await q.locator(`th[data-column-key="${key}"] .header-label`).evaluate(el=>{const r=el.getBoundingClientRect(),th=el.closest('th').getBoundingClientRect();return {height:r.height,line:parseFloat(getComputedStyle(el).lineHeight),right:r.right,parentRight:th.right,wrap:getComputedStyle(el).whiteSpace}});
    assert.equal(box.wrap,'nowrap');assert.ok(box.height<=box.line+2);assert.ok(box.right<=box.parentRight+1);
   }
   assert.equal(await q.locator('.table-wrap').evaluate(el=>el.scrollWidth>el.clientWidth),true);
   assert.equal(await q.locator('th[data-column-key="7"] .drag-handle').innerText(),'↔');
  }
  await q.evaluate(()=>{const order=getHeaderCells().map(h=>h.dataset.columnKey);reorderTableTo(['7',...order.filter(k=>k!=='7')]);});
  assert.equal(await q.locator('#draggableHeaderRow th').first().getAttribute('data-column-key'),'7');
  await q.locator('#columnSelectorDetails summary').click();const checkbox=q.locator('.column-checkbox[data-column-key="7"]');await checkbox.uncheck();assert.equal(await q.locator('th[data-column-key="7"]').isVisible(),false);await checkbox.check();assert.equal(await q.locator('th[data-column-key="7"]').isVisible(),true);
  await p.screenshot({path:`test-results/repairs-${viewport.width}-dark-160.png`,fullPage:true});await p.close();await q.close();
 }
});
