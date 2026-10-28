/* Isolated Android price-list action. Never changes the invoice or price engine. */
(()=>{
 if(!window.NativeApp||location.origin!=='https://appassets.androidplatform.net'||!location.pathname.endsWith('/site/index.html')||document.getElementById('printPriceListBtn'))return;
 const original=document.getElementById('printBtn');if(!original)return;
 const button=document.createElement('button');button.id='printPriceListBtn';button.type='button';button.className=original.className;button.textContent='Imprimir lista de preços';
 button.addEventListener('click',()=>{
  const rows=currentItems.map((item,index)=>({name:String(item.name),price:document.querySelector('[data-unit-price="'+index+'"] strong')?.textContent.trim()||'—'}));
  if(rows.length)NativeApp.postMessage(JSON.stringify({type:'printPriceList',value:JSON.stringify(rows)}));
 });
 original.after(button);
})();
