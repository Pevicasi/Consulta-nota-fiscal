const {test,before,after}=require('node:test');
const assert=require('node:assert/strict');
const http=require('node:http');
const fs=require('node:fs/promises');
const path=require('node:path');
const {chromium}=require('playwright-core');
let server,browser,base;
before(async()=>{
 server=http.createServer(async(req,res)=>{try{const file=path.basename(req.url.split('?')[0])||'index.html';if(!/^((index|consulta_precos_v(15|16|17))\.html|fiscal-cost\.js)$/.test(file)){res.writeHead(404).end();return;}res.setHeader('Content-Type',file.endsWith('.js')?'text/javascript; charset=utf-8':'text/html; charset=utf-8');res.end(await fs.readFile(path.join(__dirname,'..',file)));}catch{res.writeHead(404).end();}});
 await new Promise(r=>server.listen(0,'127.0.0.1',r));base=`http://127.0.0.1:${server.address().port}`;
 browser=await chromium.launch({executablePath:process.env.CHROME_BIN||'/usr/bin/chromium',args:['--no-sandbox']});
});
after(async()=>{await browser?.close();await new Promise(r=>server?.close(r));});
function validKey(){const base='3526101234567800019555001000000123112345678';let sum=0,w=2;for(let i=42;i>=0;i--){sum+=Number(base[i])*w;w=w===9?2:w+1;}return base+(sum%11<2?0:11-sum%11);}
function xml(n=123,name='Produto <seguro>',q=2,st=4){return `<nfeProc xmlns="http://www.portalfiscal.inf.br/nfe"><NFe><infNFe Id="NFe${validKey()}"><ide><nNF>${n}</nNF></ide><emit><xNome>Empresa de teste</xNome></emit><dest><xNome>Cliente teste</xNome></dest><det nItem="1"><prod><cProd>001</cProd><cEAN>7891234567890</cEAN><xProd>${name.replaceAll('&','&amp;').replaceAll('<','&lt;').replaceAll('>','&gt;')}</xProd><uCom>UN</uCom><qCom>${q}</qCom><vUnCom>10</vUnCom><vProd>20</vProd></prod><imposto><ICMS><ICMS10><vBCST>20</vBCST><vICMSST>${st}</vICMSST></ICMS10></ICMS></imposto></det><total><ICMSTot><vProd>20</vProd><vBCST>20</vBCST><vST>${st}</vST><vNF>24</vNF></ICMSTot></total></infNFe></NFe></nfeProc>`;}
async function page(viewport){const p=await browser.newPage({viewport:viewport||{width:1280,height:800}});await p.route('https://**/*',r=>r.abort());return p;}
async function upload(p,files){await p.setInputFiles('#multipleXmlFiles',files.map(([name,text])=>({name,mimeType:'text/xml',buffer:Buffer.from(text)})));await p.waitForFunction(()=>!document.querySelector('#loadXmlsBtn').disabled);}
test('all pages initialize without JS exceptions at desktop and mobile sizes',async()=>{
 for(const viewport of [{width:1280,height:800},{width:390,height:844}])for(const file of ['index.html','consulta_precos_v15.html','consulta_precos_v16.html','consulta_precos_v17.html']){
  const p=await page(viewport),errors=[];p.on('pageerror',e=>errors.push(e.message));assert.equal((await p.goto(base+'/'+file)).status(),200);
  assert.ok(await p.title());assert.deepEqual(errors,[],file);
  assert.equal(await p.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1),true,file+' page overflow');
  await fs.mkdir('test-results',{recursive:true});await p.screenshot({path:`test-results/${file}-${viewport.width}.png`,fullPage:true});await p.close();
 }
});
test('torchBtn regression: optional controls do not abort later event handlers or cleanup',async()=>{
 const p=await page(),errors=[];p.on('pageerror',e=>errors.push(e.message));await p.goto(base);
 assert.equal(await p.locator('#torchBtn').count(),0);
 await p.evaluate(()=>{window.testCopied='';Object.defineProperty(navigator,'clipboard',{value:{writeText:async text=>window.testCopied=text},configurable:true});});
 await p.fill('#invoiceSearch',validKey());await p.click('#copyCodeBtn');assert.equal(await p.evaluate(()=>window.testCopied),validKey());
 assert.equal(await p.locator('.scan-copy-btn').count(),1);
 await p.evaluate(()=>{cameraModal.classList.remove('hidden');cameraModal.setAttribute('aria-hidden','false');});
 await p.click('#closeCameraBtn');assert.equal(await p.locator('#cameraModal').getAttribute('aria-hidden'),'true');
 await p.evaluate(()=>window.dispatchEvent(new Event('pagehide')));assert.deepEqual(errors,[]);await p.close();
});
test('multi XML, tabs, ST calculations, margin, columns, search, CSV, session and clearing',async()=>{
 const p=await page();await p.goto(base);
 await p.click('#searchBtn');assert.match(await p.locator('#status').innerText(),/Carregar XMLs/);
 await upload(p,[['nota-123.xml',xml()],['nota-456.xml',xml(456,'Segundo produto')],['invalido.xml','<broken>']]);
 assert.equal(await p.locator('#notesTabs button').count(),2);assert.match(await p.locator('#loadedCount').innerText(),/2 XML/);
 assert.match(await p.locator('#itemsBody').innerText(),/Produto <seguro>/);
 await p.screenshot({path:'test-results/nota-importada-desktop.png',fullPage:true});
 assert.match(await p.locator('#itemsBody td:nth-child(6)').innerText(),/12,00/);
 await p.fill('#markupPercent','50');await p.locator('#markupPercent').dispatchEvent('input');
 assert.match(await p.locator('#itemsBody td:nth-child(7)').innerText(),/18,00/);
 await p.locator('#columnSelectorDetails summary').click();
 await p.locator('.column-checkbox[data-column-key="12"]').uncheck();assert.equal(await p.locator('#itemsBody td:nth-child(13)').isVisible(),false);
 await p.evaluate(()=>moveColumn('15','0'));assert.equal(await p.locator('#draggableHeaderRow th').first().getAttribute('data-column-key'),'15');
 await p.locator('#notesTabs button').nth(1).click();assert.equal(await p.locator('#nfeNumber').innerText(),'456');
 await p.reload();assert.equal(await p.locator('#notesTabs button').count(),2);assert.equal(await p.locator('#markupPercent').inputValue(),'50');
 await p.click('#newSearchBtn');await p.fill('#invoiceSearch','999999999999999999');await p.click('#searchBtn');assert.match(await p.locator('#status').innerText(),/não encontrado/);
 await p.fill('#invoiceSearch','123');await p.click('#searchBtn');assert.equal(await p.locator('#nfeNumber').innerText(),'123');
 const downloaded=p.waitForEvent('download');await p.click('#csvBtn');const file=await downloaded;
 const csv=await fs.readFile(await file.path(),'utf8');assert.match(csv,/Produto <seguro>/);assert.match(csv,/18/);assert.match(csv,/Preço sugerido/);
 await p.click('#newSearchBtn');await p.reload();assert.equal(await p.locator('#result').isVisible(),false);await p.close();
});
test('invalid XML, zero quantity, malformed input and XSS are handled',async()=>{
 const p=await page();await p.goto(base);await upload(p,[['bad.xml','<not-nfe/>']]);assert.match(await p.locator('#status').innerText(),/Nenhum XML válido/);
 await upload(p,[['zero.xml',xml(123,'<img src=x onerror=alert(1)>',0,0)]]);
 assert.equal(await p.locator('#itemsBody img').count(),0);assert.doesNotMatch(await p.locator('#itemsBody').innerText(),/NaN|Infinity/);
 assert.equal(await p.evaluate(()=>extractNfeKeyFromOcrText('123\n456')),'');assert.equal(await p.evaluate(k=>extractValidNfeKey(k),validKey()),validKey());
 assert.equal(await p.evaluate(k=>isValidNfeKey(k.slice(0,43)+(Number(k[43])+1)%10),validKey()),false);await p.close();
});
test('official discount is visible only when positive, without double counting or inferring totals',async()=>{
 const p=await page();await p.goto(base);
 const cases=[
  {total:'5.50',item:'5.50',expected:5.5},
  {total:'0',item:'5.50',expected:0},
  {total:null,item:null,expected:0},
  {total:null,item:'3.25',expected:3.25},
  {total:'5.50',item:'5.50',expected:5.5,adjustments:true},
 ];
 assert.equal(await p.locator('#markupPercent').inputValue(),'20');
 for(const [i,c] of cases.entries()){
  let source=xml(700+i);
  if(c.item!==null)source=source.replace('</prod>',`<vDesc>${c.item}</vDesc></prod>`);
  source=source.replace('<vNF>24</vNF>',`${c.total===null?'':`<vDesc>${c.total}</vDesc>`}<vFrete>7</vFrete><vOutro>2</vOutro><vNF>${c.adjustments?'27.50':'10'}</vNF>`);
  await upload(p,[[`discount-${i}.xml`,source]]);
  await p.locator('#notesTabs button').last().click();
  assert.equal(await p.evaluate(s=>parseXML(s).totalDiscount,source),c.expected);
  assert.equal(await p.locator('#discountTotalBox').isVisible(),c.expected>0);
  if(c.expected>0){
   assert.equal((await p.locator('#totalDiscount').innerText()).replace(/\s/g,''),`R$${c.expected.toFixed(2).replace('.',',')}`);
   assert.equal(await p.locator('#discountTotalBox').evaluate(el=>el.hidden),false);
  }
  assert.equal(await p.locator('#markupPercent').inputValue(),'20');
  if(c.adjustments)assert.match(await p.locator('#itemsBody td:nth-child(7)').innerText(),/16,50/);else assert.match(await p.locator('#itemsBody td:nth-child(7)').innerText(),/—/);
 }
 await p.reload();assert.equal(await p.locator('#discountTotalBox').isVisible(),true);
 await p.close();
});
test('price modes, fuels, token requirement and mocked product response',async()=>{
 for(const version of [15,16,17]){
  const p=await page();await p.goto(base+`/consulta_precos_v${version}.html`);
  await p.evaluate(()=>{lat.value='-25.4';lon.value='-49.2';setTab(2);});await p.fill('#q','arroz');await p.click('.go');assert.match(await p.locator('#status').innerText(),/token/i);
  await p.click('#modoCombustiveis');assert.equal(await p.locator('.fuel').first().isVisible(),true);
  await p.locator('.fuel').first().click();await p.click('#modoProdutos');assert.equal(await p.locator('#q').isVisible(),true);
  await p.evaluate(()=>{setTab(1);window.fetch=async()=>({ok:true,text:async()=>JSON.stringify({produtos:[]}),json:async()=>({produtos:[]})});});
  await p.fill('#q','arroz');await p.click('.go');await p.waitForFunction(()=>document.querySelector('#result').textContent.includes('Nenhum resultado'));assert.match(await p.locator('#result').innerText(),/Nenhum resultado/);await p.close();
 }
});
test('price consultation 2 reports failed requests instead of remaining stuck',async()=>{
 for(const version of [15,16,17]){
  const p=await page();await p.goto(base+`/consulta_precos_v${version}.html`);
  await p.evaluate(()=>{lat.value='-25.4';lon.value='-49.2';setTab(2);window.fetch=async()=>{throw new Error('offline fixture')};});
  assert.match(await p.locator('#status').innerText(),/autenticação/);
  await p.fill('#token','fixture-not-a-credential');await p.fill('#q','arroz');await p.click('.go');
  await p.waitForFunction(()=>document.querySelector('#status').textContent.startsWith('Falhou:'));
  assert.equal(await p.locator('#status').isVisible(),true);await p.close();
 }
});
test('Android bridge preserves XML/CSV, native scanner, clipboard and print routes',async()=>{
 const p=await page();await p.goto(base);await p.evaluate(()=>{window.nativeMessages=[];window.NativeApp={postMessage:data=>nativeMessages.push(JSON.parse(data))};});
 await p.evaluate(await fs.readFile('android/app/src/main/assets/android-bridge.js','utf8'));
 await p.click('#scanBtn');assert.equal(await p.evaluate(()=>nativeMessages.at(-1).type),'scan');
 await p.evaluate(k=>acceptNativeKey(k),validKey());assert.equal(await p.locator('#invoiceSearch').inputValue(),validKey());
 await p.click('#copyCodeBtn');assert.equal(await p.evaluate(()=>nativeMessages.at(-1).type),'copy');
 await p.click('#pasteScannedBtn');assert.equal(await p.evaluate(()=>nativeMessages.at(-1).type),'paste');
 await upload(p,[['native.xml',xml()]]);await p.click('#csvBtn');
 await p.waitForFunction(()=>nativeMessages.some(m=>m.type==='export'));
 const data=await p.evaluate(()=>JSON.parse(nativeMessages.find(m=>m.type==='export').value));
 assert.match(data.name,/\.csv$/);assert.match(Buffer.from(data.data.split(',')[1],'base64').toString('utf8'),/Produto <seguro>/);
 await p.click('#printBtn');assert.equal(await p.evaluate(()=>nativeMessages.at(-1).type),'print');await p.close();
});
test('camera permission failure and cleanup are reported without optional-control exceptions',async()=>{
 const p=await page();await p.goto(base);
 await p.evaluate(()=>{
  window.ZXingBrowser={BrowserMultiFormatReader:class{}};
  Object.defineProperty(navigator,'mediaDevices',{configurable:true,value:{getUserMedia:async()=>{throw new DOMException('denied','NotAllowedError')}}});
 });
 await p.click('#scanBtn');await p.waitForFunction(()=>document.querySelector('#status').textContent.includes('negada'));
 assert.equal(await p.locator('#cameraModal').getAttribute('aria-hidden'),'true');await p.close();
});
test('mobile invoice table, scan history removal and clear retain usable layout',async()=>{
 const p=await page({width:390,height:844});await p.goto(base);
 await p.evaluate(k=>{addScannedCode(k);addScannedCode(k);},validKey());assert.equal(await p.locator('.scan-remove-btn').count(),1);
 await p.locator('.scan-remove-btn').click();assert.equal(await p.locator('#scanList').isVisible(),false);
 await p.evaluate(k=>addScannedCode(k),validKey());await p.click('#clearScansBtn');assert.equal(await p.locator('#scanList').isVisible(),false);
 await upload(p,[['mobile.xml',xml()]]);assert.match(await p.locator('#itemsBody').innerText(),/Produto <seguro>/);
 assert.equal(await p.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1),true);
 await p.screenshot({path:'test-results/nota-importada-mobile.png',fullPage:true});await p.close();
});
