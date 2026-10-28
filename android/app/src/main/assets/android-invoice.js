/* Android presentation only. Never injected into Pages/external URLs. */
(()=>{
 if(!window.NativeApp||location.origin!=='https://appassets.androidplatform.net'||!location.pathname.endsWith('/site/index.html')||window.__compactInvoice)return;
 window.__compactInvoice=true;
 const visibilityKey='nfeAppColumnVisibilityV3',primary=['0','7','1','2','16','3','4','5'];
 const originalItems=renderItems,originalCSV=buildFiscalCSV,originalCalculate=NfeCost.calculate;
 // Conversion is app-only. The audited shared fiscal engine still owns all money,
 // allocation and final rounding; persisted manual factors are never input here.
 const normalize=u=>String(u||'').normalize('NFD').replace(/[\u0300-\u036f]/g,'').toUpperCase().replace(/\./g,'').trim();
 const singles=new Set(['UN','UND','UNID','UNIDADE','UNIDADES','PC','PCS','PÇ','PECA','PECAS']);
 const packages=/^(CX|CXS|CAIXAS?|FD|FDS|FARDOS?|PCT|PCTS|PACOTES?|EMB|EMBALAGE(?:M|NS))$/;
 const descriptionText=s=>String(s||'').normalize('NFD').replace(/[\u0300-\u036f]/g,'').toUpperCase();
 const packagePattern='CX|CXS|CAIXAS?|FD|FDS|FARDOS?|PCT|PCTS|PACOTES?|EMB|EMBALAGEM|EMBALAGENS';
 const packageUnit=s=>/^(CX|CXS|CAIXAS?)$/.test(s)?'CX':/^(FD|FDS|FARDOS?)$/.test(s)?'FD':/^(PCT|PCTS|PACOTES?)$/.test(s)?'PCT':packages.test(s)?'EMB':null;
 const numeric=s=>Number(String(s).replace(',','.'));
 const same=(a,b)=>Number.isFinite(a)&&Number.isFinite(b)&&Math.abs(a-b)<=1e-7;
 function packageFacts(item){
  const text=descriptionText(item.name),counts=[],weights=[],amounts=[],kinds=[];
  const kind=s=>{const k=packageUnit(s);if(k)kinds.push(k);return k;};
  const countPattern=new RegExp('(?:\\b('+packagePattern+')\\s*(?:C\\s*[/.:]\\s*)?|\\bC\\s*[/]\\s*)(\\d{1,6})\\s*(?:X\\s*\\d+(?:[.,]\\d+)?\\s*(?:KG|GR|G|MG|ML|LT|L)\\b|(?:UNIDADES?|UNID|UND|UN|PCS?|PECAS?)\\b|(?=$|[;,]|\\s+(?!(?:KG|GR|G|MG|ML|LT|L)\\b)[A-Z]))','g');
  for(const m of text.matchAll(countPattern)){counts.push(Number(m[2]));kind(m[1]);}
  const amountPattern=new RegExp('\\bQTD\\.?\\s*:?\\s*(\\d+(?:[.,]\\d+)?)\\s*('+packagePattern+')\\b','g');
  for(const m of text.matchAll(amountPattern)){amounts.push(numeric(m[1]));kind(m[2]);}
  const weightPatterns=[new RegExp('\\b('+packagePattern+')\\s*(?:C\\s*[/.:]\\s*)?(\\d+(?:[.,]\\d+)?)\\s*(KG|GR|G)\\b','g'),new RegExp('(?:^|[^\\dX])(\\d+(?:[.,]\\d+)?)\\s*(KG|GR|G)\\s+('+packagePattern+')\\b','g')];
  for(const [i,pattern] of weightPatterns.entries())for(const m of text.matchAll(pattern)){const n=numeric(m[i?1:2]),u=m[i?2:3];weights.push(n*(u==='KG'?1:.001));kind(m[i?3:1]);}
  const distinct=a=>[...new Set(a)];const cs=distinct(counts),ws=distinct(weights),qs=distinct(amounts),ks=distinct(kinds);
  return {count:cs[0],weight:ws[0],quantity:qs[0],unit:ks[0],conflict:cs.length>1||ws.length>1||qs.length>1||ks.length>1||[...counts,...weights,...amounts].some(n=>!(n>0&&n<=1000000))};
 }
 function documentedConversion(item,raw){
  const unit=normalize(item.unit),quantity=Number(raw.quantity),spec=packageFacts(item);
  const fallback=reason=>({factor:1,base:unit||'unidade comercial',equivalent:false,source:reason});
  if(!(quantity>0))return fallback('Quantidade inválida');
  const taxUnit=normalize(raw.taxUnit),tq=Number(raw.taxQuantity);
  if(singles.has(unit)){
   if(singles.has(taxUnit)&&tq>0&&!same(tq,quantity))return fallback('Equivalência divergente; unidade comercial');
   return {factor:1,base:'UN',equivalent:true,source:'XML já em unidades'};
  }
  if(unit==='KG'||unit==='G'||unit==='GR')return {factor:unit==='KG'?1:.001,base:'KG',equivalent:false,source:'Unidade comercial documentada'};
  if(!packages.test(unit))return fallback('Unidade comercial documentada');
  if(spec.conflict||spec.unit&&spec.unit!==packageUnit(unit)||spec.quantity!=null&&!same(spec.quantity,quantity))return fallback('Equivalência divergente; unidade comercial');
  if(spec.weight&&!spec.count){
   if(['KG','G','GR'].includes(taxUnit)&&tq>0&&!same(tq*(taxUnit==='KG'?1:.001),quantity*spec.weight))return fallback('Equivalência divergente; unidade comercial');
   return {factor:spec.weight,base:'KG',equivalent:false,source:'Peso documentado por embalagem'};
  }
  let taxFactor=null;
  if(singles.has(taxUnit)&&tq>0){try{const gross=NfeCost.cents(raw.gross),commercial=NfeCost.cents(String(Number(raw.commercialUnitValue)*quantity)),tax=NfeCost.cents(String(Number(raw.taxUnitValue)*tq));if(gross!=null&&Math.abs(commercial-gross)<=1&&Math.abs(tax-gross)<=1)taxFactor=tq/quantity;}catch(_){} }
  const count=spec.count;
  if(count&&singles.has(taxUnit)&&tq>0&&(taxFactor==null||!same(count,taxFactor)))return fallback('Equivalência divergente; unidade comercial');
  const factor=count??taxFactor;
  if(!(factor>0&&factor<=1000000))return fallback('Sem equivalência unitária');
  return {factor,base:'UN',equivalent:true,source:count?'Quantidade explícita na embalagem':'Equivalência documentada no XML'};
 }
 function packagingFor(item,raw,row){
  const unit=normalize(item.unit),spec=packageFacts(item),q=Number(raw.quantity),base=row.conversion.base,total=row.conversion.units;
  if(spec.conflict||!(q>0)||!Number.isFinite(total))return null;
  const taxUnit=normalize(raw.taxUnit),taxQuantity=Number(raw.taxQuantity);
  if(base==='KG'&&['KG','G','GR'].includes(taxUnit)&&taxQuantity>0&&!same(total,taxQuantity*(taxUnit==='KG'?1:.001)))return null;
  const commercial=packageUnit(unit);
  if(commercial){
   if(base===unit||base===commercial||!row.conversion.equivalent&&base!=='KG')return null;
   if(spec.unit&&spec.unit!==commercial||spec.quantity!=null&&!same(spec.quantity,q))return null;
   return {quantity:String(raw.quantity),unit:commercial};
  }
  const factor=base==='UN'&&row.conversion.equivalent?spec.count:base==='KG'?spec.weight:null;
  if(!(factor>0)||!spec.unit)return null;
  const derived=total/factor,rounded=Math.round(derived);
  // An incomplete package or contradicting QTD is not an actual box count.
  if(!Number.isSafeInteger(rounded)||rounded<=0||!same(derived,rounded)||spec.quantity!=null&&!same(spec.quantity,rounded))return null;
  return {quantity:String(rounded),unit:spec.unit};
 }
 const displayedUnit=base=>base==='KG'?'kg':base;
 const amountText=value=>String(value).replace(/(\.\d*?)0+$/,'$1').replace(/\.$/,'').replace('.',',');
 NfeCost.calculate=(data,ignored,options)=>{
  if(!data.fiscalRaw){
   const result=originalCalculate(data,{},options);
   result.items.forEach((row,i)=>{row.commercialUnit=normalize(data.items[i].unit)||'unidade comercial';Object.assign(row.conversion,{base:row.commercialUnit,equivalent:false,automatic:true,source:''});});
   return result;
  }
  const conversions=data.items.map((item,i)=>documentedConversion(item,data.fiscalRaw.items[i]));
  // Isolate repeated SKUs: packaging can differ between lines of the same note.
  const calculationData={...data,items:data.items.map((item,i)=>({...item,productCode:'APP-AUTO-LINE-'+i}))};
  const overrides=Object.fromEntries(calculationData.items.map((item,i)=>[NfeCost.productKey(calculationData,item),{...conversions[i],confirmed:true}]));
  const result=originalCalculate(calculationData,overrides,options);
  result.items.forEach((row,i)=>{Object.assign(row.conversion,{automatic:true,equivalent:conversions[i].equivalent,source:conversions[i].source});row.packaging=packagingFor(data.items[i],data.fiscalRaw.items[i],row);});
  return result;
 };
 const style=document.createElement('style');style.textContent=`
 html[data-native-theme] #itemsBody .product-name{min-width:0;max-width:100%;font-weight:600;overflow-wrap:normal!important;word-break:normal;white-space:normal;}
 html[data-native-theme] #itemsBody td[data-column-key="1"]{overflow-wrap:normal;word-break:normal;}
 html[data-native-theme] #itemsBody :is(th,td){padding-top:3px;padding-bottom:3px;}
 html[data-native-theme] #itemsBody :is(.quantity-primary,.quantity-packaging){display:block;white-space:nowrap;}
 html[data-native-theme] #itemsBody .package-value{display:block;white-space:normal;}
 html[data-native-theme] #itemsBody .basis-notice{display:block;white-space:normal;}
 html[data-native-theme] #fiscalSummary>p{display:none;}
 html[data-native-theme] #fiscalWarnings{margin-top:8px;}
 html[data-native-theme] #fiscalWarnings ul{display:none;}
 `;document.head.append(style);
 const header=document.createElement('th');header.dataset.columnKey='16';header.draggable=true;header.className='number draggable-column';header.innerHTML='<span class="drag-handle" aria-hidden="true">↔</span><span class="header-label">IPI por unidade</span>';
 document.querySelector('th[data-column-key="3"]').before(header);
 const label=document.createElement('label');label.dataset.columnKey='16';label.innerHTML='<input type="checkbox" class="column-checkbox" data-column-key="16" checked> IPI por unidade';document.querySelector('.column-selector-list').append(label);
 const newCheckbox=label.querySelector('input');newCheckbox.addEventListener('change',()=>{applyColumnVisibility('16',newCheckbox.checked);updateColumnCounter();});
 header.addEventListener('dragstart',e=>e.dataTransfer.setData('text/plain','16'));header.addEventListener('dragover',e=>e.preventDefault());header.addEventListener('drop',e=>{e.preventDefault();moveColumn(e.dataTransfer.getData('text/plain'),'16');});
 header.addEventListener('pointerdown',e=>{if(e.pointerType!=='mouse')header.setPointerCapture?.(e.pointerId);});
 header.addEventListener('pointerup',e=>{if(e.pointerType!=='mouse'){const t=document.elementFromPoint(e.clientX,e.clientY)?.closest('th[data-column-key]');if(t)moveColumn('16',t.dataset.columnKey);}});
 let savedOrder;try{savedOrder=JSON.parse(localStorage.getItem(COLUMN_ORDER_STORAGE_KEY)||'null');}catch(_){}
 // Existing 16-key order is retained; the new IPI column starts visible beside ST.
 if(Array.isArray(savedOrder)&&savedOrder.length===16){const at=savedOrder.indexOf('3');savedOrder.splice(at<0?savedOrder.length:at,0,'16');}
 let savedVisibility;try{savedVisibility=JSON.parse(localStorage.getItem(visibilityKey)||'null');}catch(_){}
 document.querySelectorAll('.column-checkbox').forEach(c=>{if(savedVisibility&&Object.hasOwn(savedVisibility,c.dataset.columnKey))c.checked=!!savedVisibility[c.dataset.columnKey];else if(!savedOrder)c.checked=primary.includes(c.dataset.columnKey);});
 document.querySelector('.column-selector-list').addEventListener('change',()=>{localStorage.setItem(visibilityKey,JSON.stringify(Object.fromEntries(Array.from(document.querySelectorAll('.column-checkbox')).map(c=>[c.dataset.columnKey,c.checked]))));});
 // Do not create editors, actions, hidden per-product audit text or dialogs.
 renderCostDetails=()=>'';
 renderItems=()=>{
  const data=loadedNotes[activeNoteIndex]?.data;if(data)currentFinance=NfeCost.calculate(data,{},data.purchaseOptions||{});
  // Temporarily omit the new header so the unchanged site's positional renderer remains coherent.
  const sibling=header.nextSibling;header.remove();originalItems();document.getElementById('draggableHeaderRow').insertBefore(header,sibling);
  document.querySelectorAll('#itemsBody tr').forEach((tr,index)=>{
   const row=currentFinance?.items[index];if(!row)return;
   tr.querySelector('td[data-column-key="2"] small')?.remove();
   const product=tr.querySelector('td[data-column-key="1"]'),name=document.createElement('div');name.className='product-name';name.textContent=currentItems[index].name;product.replaceChildren(name);
   const commercial=!row.conversion.equivalent,base=row.conversion.base;
   const unit=displayedUnit(base),quantity=tr.querySelector('td[data-column-key="7"]'),main=document.createElement('span');main.className='quantity-primary';main.textContent=(row.conversion.unitsExact?amountText(row.conversion.unitsExact):numberBR.format(currentItems[index].quantity))+' '+unit;quantity.replaceChildren(main);
   if(row.packaging){const sub=document.createElement('span');sub.className='quantity-packaging';sub.textContent=amountText(row.packaging.quantity)+' '+row.packaging.unit;quantity.append(sub);}
   const monetary=(cents)=>fiscalMoney(cents)+' / '+unit;
   const net=row.parts?row.grossCents-row.parts.discount:null;
   let netUnit=null;try{if(net!=null)netUnit=NfeCost.unitCents(net,row.quantity,row.conversion.exactFactor??row.conversion.factor);}catch(_){}
   let grossUnit=null;try{if(row.grossCents!=null)grossUnit=NfeCost.unitCents(row.grossCents,row.quantity,row.conversion.exactFactor??row.conversion.factor);}catch(_){}
   row.basicCents=grossUnit;tr.querySelector('td[data-column-key="2"]').textContent=monetary(grossUnit);
   tr.querySelector('td[data-column-key="4"]').textContent=monetary(netUnit);
   row.netProductUnitCents=netUnit;row.packageCostCents=null;row.packagePriceCents=null;
   if(row.packaging&&row.ready){try{row.packageCostCents=NfeCost.unitCents(row.totalCents,row.packaging.quantity);if(validMarkup())row.packagePriceCents=NfeCost.unitCents(row.totalCents,row.packaging.quantity,1,getMarkupPercent());}catch(_){} }
   let packageNet=null;if(row.packaging&&net!=null)try{packageNet=NfeCost.unitCents(net,row.packaging.quantity);}catch(_){}
   for(const [key,value] of [['4',packageNet],['5',row.packagePriceCents]]){if(row.packaging){const extra=document.createElement('small');extra.className='package-value';extra.textContent=fiscalMoney(value)+' / '+row.packaging.unit;tr.querySelector('td[data-column-key="'+key+'"]').append(extra);}}
   for(const key of ['4','5']){const td=tr.querySelector('td[data-column-key="'+key+'"]');if(base==='KG'){const walker=document.createTreeWalker(td,NodeFilter.SHOW_TEXT);let node;while(node=walker.nextNode())node.textContent=node.textContent.replace('/ KG','/ kg');}}
   if(commercial&&row.conversion.source&&row.conversion.source!=='Unidade comercial documentada'&&row.conversion.base===normalize(row.commercialUnit)){const notice=document.createElement('small');notice.className='basis-notice';notice.textContent=row.conversion.source;tr.querySelector('td[data-column-key="4"]').append(notice);}
   const ipi=document.createElement('td');ipi.className='number';ipi.dataset.columnKey='16';
   const raw=loadedNotes[activeNoteIndex]?.data.fiscalRaw;const known=raw&&(raw.items[index].ipi!=null||raw.totals.ipi!=null);
   let value=null;if(known)try{value=NfeCost.unitCents(row.parts.ipi,row.quantity,row.conversion.exactFactor??row.conversion.factor);}catch(_){}
   ipi.textContent=value==null?'Não informado':fiscalMoney(value)+' / '+unit+(commercial&&base===normalize(row.commercialUnit)&&packages.test(normalize(row.commercialUnit))?' (comercial)':'');
   const order=getColumnOrder(),after=Array.from(tr.children)[order.indexOf('16')];tr.insertBefore(ipi,after||null);
  });
  const warnings=document.querySelector('#fiscalWarnings');if(warnings){const list=warnings.querySelector('ul');if(list)document.querySelector('#fiscalBreakdown')?.append(list);warnings.textContent=currentFinance?.warnings.length?'Confira os detalhes gerais do custo.':'Custos conferidos com a nota.';}
  const summary=document.querySelector('#fiscalBreakdown>summary');if(summary)summary.textContent='Detalhes do custo';
  for(const [key,title] of [['2','VALOR'],['4','VALOR COM DESCONTO'],['5','Preço sugerido (+'+percentBR.format(getMarkupPercent())+'%)']]){document.querySelector('th[data-column-key="'+key+'"] .header-label').textContent=title;const e=document.querySelector('.column-selector-list label[data-column-key="'+key+'"]');e.querySelector('span').textContent=title;}
  document.getElementById('appSaleBasis')?.remove();
  if(currentItems.length){const info=document.createElement('p');info.id='appSaleBasis';info.textContent='Preço sugerido: custo com IPI, ST e demais encargos cobrados, menos descontos uma única vez, acrescido do percentual informado.';document.querySelector('#fiscalBreakdown')?.append(info);}
  applyAllColumnPreferences();window.fitNativeInvoiceColumns?.();
 };
 buildFiscalCSV=()=>{const csv=originalCSV();csv.headers[2]='VALOR';csv.headers[6]='Total bruto dos produtos (XML)';csv.headers[4]='VALOR COM DESCONTO';csv.headers[5]='Preço sugerido (+'+getMarkupPercent()+'%)';csv.headers[20]='Equivalência automática';csv.headers.push('IPI por unidade','Base do IPI por unidade');csv.rows.forEach((r,i)=>{const row=currentFinance.items[i];r[4]=row.netProductUnitCents==null?'':(row.netProductUnitCents/100).toFixed(2);r[20]=row.conversion.source;const raw=loadedNotes[activeNoteIndex]?.data.fiscalRaw;const known=raw&&(raw.items[i].ipi!=null||raw.totals.ipi!=null);let amount=null;if(known)try{amount=NfeCost.unitCents(row.parts.ipi,row.quantity,row.conversion.confirmed?(row.conversion.exactFactor??row.conversion.factor):1);}catch(_){}r.push(amount==null?'':(amount/100).toFixed(2),row.conversion.confirmed?row.conversion.base:row.commercialUnit+' comercial');});csv.headers.push('Custo com encargos / unidade ou kg','Quantidade de embalagens','Tipo de embalagem','Custo por embalagem','Preço sugerido por embalagem');csv.rows.forEach((r,i)=>{const row=currentFinance.items[i];r.push(row.costCents==null?'':(row.costCents/100).toFixed(2),row.packaging?.quantity||'',row.packaging?.unit||'',row.packageCostCents==null?'':(row.packageCostCents/100).toFixed(2),row.packagePriceCents==null?'':(row.packagePriceCents/100).toFixed(2));});return csv;};
 if(Array.isArray(savedOrder)&&savedOrder.length===17)reorderTableTo(savedOrder,false);else reorderTableTo(primary.concat(getColumnOrder().filter(k=>!primary.includes(k))),false);
 applyAllColumnPreferences();renderItems();
})();
