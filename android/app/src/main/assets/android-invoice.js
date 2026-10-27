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
 function documentedConversion(item,raw){
  const unit=normalize(item.unit),quantity=Number(raw.quantity),description=normalize(item.name);
  const fallback=reason=>({factor:1,base:unit||'unidade comercial',equivalent:false,source:reason});
  if(!(quantity>0))return fallback('Quantidade inválida');
  // Weight/volume is never a count; QTD identifies packages, not contents.
  const counts=[];
  const pattern=/(?:\b(?:CX|CAIXAS?|FD|FARDOS?|PCT|PACOTES?|EMB|EMBALAGE(?:M|NS))\s*(?:C\s*[/.:]\s*)?|\bC\s*[/]\s*)(\d{1,6})\s*(?:X\s*\d+(?:[.,]\d+)?\s*(?:KG|GR|G|MG|ML|LT|L)\b|(?:UNIDADES?|UNID|UND|UN|PCS?|PECAS?)\b|(?=$|[;,]|\s+(?!(?:KG|GR|G|MG|ML|LT|L)\b)[A-Z]))/g;
  for(const match of description.matchAll(pattern))counts.push(Number(match[1]));
  const distinct=[...new Set(counts)];
  const taxUnit=normalize(raw.taxUnit),tq=Number(raw.taxQuantity);
  let taxFactor=null;
  if(singles.has(taxUnit)&&tq>0){
   try{const gross=NfeCost.cents(raw.gross),commercial=NfeCost.cents(String(Number(raw.commercialUnitValue)*quantity)),tax=NfeCost.cents(String(Number(raw.taxUnitValue)*tq));
    if(gross!=null&&Math.abs(commercial-gross)<=1&&Math.abs(tax-gross)<=1)taxFactor=tq/quantity;
   }catch(_){}
  }
  if(singles.has(unit)){
   if(singles.has(taxUnit)&&tq>0&&Math.abs(tq-quantity)>0.000001)return fallback('Equivalência divergente; unidade comercial');
   return {factor:1,base:'UN',equivalent:true,source:'XML já em unidades'};
  }
  if(!packages.test(unit))return fallback('Unidade comercial documentada');
  if(distinct.length>1||distinct.some(n=>n<1||n>1000000))return fallback('Equivalência divergente; unidade comercial');
  const count=distinct[0];
  if(count&&singles.has(taxUnit)&&tq>0&&(taxFactor==null||Math.abs(count-taxFactor)>0.000001))return fallback('Equivalência divergente; unidade comercial');
  const factor=count??taxFactor;
  if(!(factor>0&&factor<=1000000))return fallback('Sem equivalência unitária');
  return {factor,base:'UN',equivalent:true,source:count?'Quantidade explícita na embalagem':'Equivalência documentada no XML'};
 }
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
  result.items.forEach((row,i)=>Object.assign(row.conversion,{automatic:true,equivalent:conversions[i].equivalent,source:conversions[i].source}));
  return result;
 };
 const style=document.createElement('style');style.textContent=`
 html[data-native-theme] #itemsBody .product-name{min-width:0;max-width:100%;font-weight:600;overflow-wrap:normal!important;word-break:normal;white-space:normal;}
 html[data-native-theme] #itemsBody td[data-column-key="1"]{overflow-wrap:normal;word-break:normal;}
 html[data-native-theme] #itemsBody :is(th,td){padding-top:3px;padding-bottom:3px;}
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
   const quantity=tr.querySelector('td[data-column-key="7"]');quantity.textContent=(row.conversion.unitsExact??numberBR.format(currentItems[index].quantity))+' '+base;
   if(commercial&&row.conversion.source&&row.conversion.source!=='Unidade comercial documentada'){const notice=document.createElement('small');notice.className='basis-notice';notice.textContent=row.conversion.source;tr.querySelector('td[data-column-key="4"]').append(notice);}
   const ipi=document.createElement('td');ipi.className='number';ipi.dataset.columnKey='16';
   const raw=loadedNotes[activeNoteIndex]?.data.fiscalRaw;const known=raw&&(raw.items[index].ipi!=null||raw.totals.ipi!=null);
   let value=null;if(known)try{value=NfeCost.unitCents(row.parts.ipi,row.quantity,row.conversion.exactFactor??row.conversion.factor);}catch(_){}
   ipi.textContent=value==null?'Não informado':fiscalMoney(value)+' / '+base+(commercial?' (comercial)':'');
   const order=getColumnOrder(),after=Array.from(tr.children)[order.indexOf('16')];tr.insertBefore(ipi,after||null);
  });
  const warnings=document.querySelector('#fiscalWarnings');if(warnings){const list=warnings.querySelector('ul');if(list)document.querySelector('#fiscalBreakdown')?.append(list);warnings.textContent=currentFinance?.warnings.length?'Confira os detalhes gerais do custo.':'Custos conferidos com a nota.';}
  const summary=document.querySelector('#fiscalBreakdown>summary');if(summary)summary.textContent='Detalhes do custo';
  applyAllColumnPreferences();window.fitNativeInvoiceColumns?.();
 };
 buildFiscalCSV=()=>{const csv=originalCSV();csv.headers[20]='Equivalência automática';csv.headers.push('IPI por unidade','Base do IPI por unidade');csv.rows.forEach((r,i)=>{const row=currentFinance.items[i];r[20]=row.conversion.source;const raw=loadedNotes[activeNoteIndex]?.data.fiscalRaw;const known=raw&&(raw.items[i].ipi!=null||raw.totals.ipi!=null);let amount=null;if(known)try{amount=NfeCost.unitCents(row.parts.ipi,row.quantity,row.conversion.confirmed?(row.conversion.exactFactor??row.conversion.factor):1);}catch(_){}r.push(amount==null?'':(amount/100).toFixed(2),row.conversion.confirmed?row.conversion.base:row.commercialUnit+' comercial');});return csv;};
 if(Array.isArray(savedOrder)&&savedOrder.length===17)reorderTableTo(savedOrder,false);else reorderTableTo(primary.concat(getColumnOrder().filter(k=>!primary.includes(k))),false);
 applyAllColumnPreferences();if(currentItems.length)renderItems();
})();
