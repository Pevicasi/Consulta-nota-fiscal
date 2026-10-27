/* Android presentation only. Never injected into Pages/external URLs. */
(()=>{
 if(!window.NativeApp||location.origin!=='https://appassets.androidplatform.net'||!location.pathname.endsWith('/site/index.html')||window.__compactInvoice)return;
 window.__compactInvoice=true;
 const visibilityKey='nfeAppColumnVisibilityV3',primary=['0','7','1','2','16','3','4','5'];
 const originalDetails=renderCostDetails,originalItems=renderItems,originalCSV=buildFiscalCSV;
 const style=document.createElement('style');style.textContent=`
 @media screen {
 html[data-native-theme] #itemsBody .product-name{min-width:0;max-width:100%;font-weight:600;}
 html[data-native-theme] #itemsBody .product-action{display:block;background:transparent!important;color:var(--primary)!important;border:0;padding:0;text-align:left;min-height:48px;}
 html[data-native-theme] #itemsBody :is(.unit-basis,.cost-audit-print,.cost-details){display:none!important;}
 html[data-native-theme] #fiscalSummary>p{display:none;}
 html[data-native-theme] #fiscalWarnings{margin-top:8px;}
 html[data-native-theme] #fiscalWarnings ul{display:none;}
 html[data-native-theme] dialog{width:calc(100% - 24px);max-width:680px;max-height:calc(100dvh - 24px);overflow:auto;background:var(--card,#fff);color:inherit;border:1px solid var(--border);border-radius:16px;padding:16px;}
 html[data-native-theme=light] dialog{background:#fff!important;}
 html[data-native-theme=dark] dialog{background:#1E293B!important;}
 html[data-native-theme] dialog::backdrop{background:#0008;}
 html[data-native-theme] dialog .conversion-editor{display:flex;flex-direction:column;align-items:stretch;}
 html[data-native-theme] dialog :is(input,button){max-width:100%;}
 html[data-native-theme] dialog summary{display:none;}
 html[data-native-theme] dialog .cost-details{display:block;}
 }
 @media print {dialog{display:none!important;} .product-action{display:none!important;}}
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
 const modal=document.createElement('dialog');modal.id='productCostDialog';modal.setAttribute('aria-labelledby','costDialogTitle');document.body.append(modal);
 let opened=-1,returnPosition=null;
 function content(index){const row=currentFinance?.items[index];if(!row)return '';return '<h2 id="costDialogTitle">'+escapeHTML(currentItems[index].name)+'</h2><button type="button" data-close-cost>Fechar detalhes</button>'+originalDetails(currentItems[index],index,row);}
 function refreshDialog(){if(opened<0||!modal.open)return;const active=modal.contains(document.activeElement)?document.activeElement:null,field=active?.dataset.costField,caret=active?.selectionStart;modal.innerHTML=content(opened);modal.querySelectorAll('details').forEach(d=>d.open=true);if(field){const e=modal.querySelector('[data-cost-field="'+field+'"]');e?.focus({preventScroll:true});if(caret!=null)try{e.setSelectionRange(caret,caret);}catch(_){}}}
 window.openProductCost=index=>{if(!currentItems[index])return;opened=index;returnPosition={x:scrollX,y:scrollY,h:document.querySelector('.table-wrap').scrollLeft,note:activeNoteIndex};modal.innerHTML=content(index);modal.querySelectorAll('details').forEach(d=>d.open=true);modal.showModal();};
 modal.addEventListener('close',()=>{opened=-1;modal.innerHTML='';if(returnPosition&&activeNoteIndex===returnPosition.note){scrollTo(returnPosition.x,returnPosition.y);document.querySelector('.table-wrap').scrollLeft=returnPosition.h;}});
 document.addEventListener('click',e=>{const action=e.target.closest('[data-open-cost]');if(action)openProductCost(Number(action.dataset.openCost));if(e.target.closest('[data-close-cost]'))modal.close();});
 renderCostDetails=(item,index,row)=>'<button type="button" class="product-action" data-open-cost="'+index+'">'+(row?.conversion.confirmed?'Detalhes':'Confirmar unidades')+'</button>'+(()=>{const d=document.createElement('div');d.innerHTML=originalDetails(item,index,row);return d.querySelector('.cost-audit-print')?.outerHTML||'';})();
 const perCommercial=(row,key)=>{try{return NfeCost.unitCents(key==='gross'?row.grossCents:key==='total'?row.totalCents:row.parts[key],row.quantity);}catch(_){return null;}};
 renderItems=()=>{
  // Temporarily omit the new header so the unchanged site's positional renderer remains coherent.
  const sibling=header.nextSibling;header.remove();originalItems();document.getElementById('draggableHeaderRow').insertBefore(header,sibling);
  document.querySelectorAll('#itemsBody tr').forEach((tr,index)=>{
   const row=currentFinance?.items[index];if(!row)return;
   tr.querySelectorAll('td small').forEach(e=>e.remove());
   const commercial=!row.conversion.confirmed,base=commercial?row.commercialUnit:row.conversion.base;
   for(const [key,part] of [['2','gross'],['3','st'],['4','total']]){
    const td=tr.querySelector('td[data-column-key="'+key+'"]');if(commercial&&td)td.textContent=fiscalMoney(perCommercial(row,part))+' / '+base+' (embalagem)';
   }
   const ipi=document.createElement('td');ipi.className='number';ipi.dataset.columnKey='16';
   const raw=loadedNotes[activeNoteIndex]?.data.fiscalRaw;const known=raw&&(raw.items[index].ipi!=null||raw.totals.ipi!=null);
   let value=null;if(known)try{value=NfeCost.unitCents(row.parts.ipi,row.quantity,commercial?1:(row.conversion.exactFactor??row.conversion.factor));}catch(_){}
   ipi.textContent=value==null?'Não informado':fiscalMoney(value)+' / '+base+(commercial?' (comercial)':'');
   const order=getColumnOrder(),after=Array.from(tr.children)[order.indexOf('16')];tr.insertBefore(ipi,after||null);
  });
  const warnings=document.querySelector('#fiscalWarnings');if(warnings){const list=warnings.querySelector('ul');if(list)document.querySelector('#fiscalBreakdown')?.append(list);warnings.textContent=currentFinance?.warnings.length?'Confira as pendências em Detalhes do custo.':'Custos conferidos com a nota.';}
  const summary=document.querySelector('#fiscalBreakdown>summary');if(summary)summary.textContent='Detalhes do custo';
  applyAllColumnPreferences();window.fitNativeInvoiceColumns?.();refreshDialog();
 };
 buildFiscalCSV=()=>{const csv=originalCSV();csv.headers.push('IPI por unidade','Base do IPI por unidade');csv.rows.forEach((r,i)=>{const row=currentFinance.items[i],raw=loadedNotes[activeNoteIndex]?.data.fiscalRaw;const known=raw&&(raw.items[i].ipi!=null||raw.totals.ipi!=null);let amount=null;if(known)try{amount=NfeCost.unitCents(row.parts.ipi,row.quantity,row.conversion.confirmed?(row.conversion.exactFactor??row.conversion.factor):1);}catch(_){}r.push(amount==null?'':(amount/100).toFixed(2),row.conversion.confirmed?row.conversion.base:row.commercialUnit+' comercial');});return csv;};
 if(Array.isArray(savedOrder)&&savedOrder.length===17)reorderTableTo(savedOrder,false);else reorderTableTo(primary.concat(getColumnOrder().filter(k=>!primary.includes(k))),false);
 // A note switch must close the editor rather than edit a different product accidentally.
 document.getElementById('notesTabs').addEventListener('click',()=>{if(modal.open)modal.close();});
 applyAllColumnPreferences();if(currentItems.length)renderItems();
})();
