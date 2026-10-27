(() => {
  // App-only asset: never loaded by Pages or injected into a third-party page.
  if (!window.NativeApp || location.origin !== 'https://appassets.androidplatform.net' ||
      !/^\/assets\/site\/(index|consulta_precos_v(15|16|17))\.html$/.test(location.pathname)) return;
  if (window.applyNativeAppearance) return;
  const style = document.createElement('style');
  style.id = 'nfe-native-appearance';
  style.textContent = `
  @media screen {
    html[data-native-theme] { font-size:16px; }
    html[data-native-theme] body { font-size:calc(16px * var(--nfe-scale)); line-height:1.5; overflow-wrap:anywhere; }
    html[data-native-theme] body * { box-sizing:border-box; font-size:inherit !important; }
    html[data-native-theme] h1 { font-size:calc(28px * var(--nfe-scale)) !important; }
    html[data-native-theme] :is(h2,h3) { font-size:calc(22px * var(--nfe-scale)) !important; }
    html[data-native-theme] :is(small,.small) { font-size:calc(14px * var(--nfe-scale)) !important; }
    html[data-native-theme] :is(.total-box strong,.price) { font-size:calc(20px * var(--nfe-scale)) !important; }
    html[data-native-theme] :is(button,input:not([type=checkbox]):not([type=radio]),select,textarea) {
      min-height:48px; height:auto !important; max-width:100%; line-height:1.5; white-space:normal !important;
    }
    html[data-native-theme] input::placeholder, html[data-native-theme] textarea::placeholder { color:#667085 !important; opacity:1; }
    html[data-native-theme] :is(button,label,summary,.note-tab,.header-label) { overflow-wrap:anywhere; white-space:normal !important; text-overflow:clip !important; }
    html[data-native-theme] :is(.summary-grid,.totals) { grid-template-columns:repeat(auto-fit,minmax(min(100%,calc(170px * var(--nfe-scale))),1fr)); }
    html[data-native-theme] :is(.summary-item,.total-box,.card,.wrap,.container) { min-width:0; }
    html[data-native-theme] :is(.actions,.tabs,.modebar,.fuelgrid) { flex-wrap:wrap; }
    html[data-native-theme] .table-wrap { max-width:100%; overflow-x:auto; }
    html[data-native-theme] table { table-layout:fixed; min-width:0 !important; width:var(--nfe-table-width,1600px) !important; }
    html[data-native-theme] :is(th,td) { min-width:0 !important; max-width:none; white-space:normal !important; overflow-wrap:anywhere; text-overflow:clip !important; }
    html[data-native-theme] th .header-label { overflow-wrap:normal; word-break:normal; }
    html[data-native-theme] :is(th,td)[data-column-key="0"] { text-align:center; padding-left:6px; padding-right:6px; }
    html[data-native-theme] th[data-column-key="0"] .drag-handle { display:block; margin-right:0; line-height:1; }
    html[data-native-theme] :is(th,td)[data-column-key="7"] { padding-left:6px; padding-right:6px; }
    html[data-native-theme] td[data-column-key="7"] { text-align:right; white-space:nowrap !important; }
    html[data-native-theme] td[data-column-key="1"] { overflow-wrap:break-word; word-break:normal; }
    html[data-native-theme] .cost-details :is(input,label,button) { min-width:0 !important; width:100%; max-width:100%; }
    html[data-native-theme] .cost-details input[type=checkbox] { width:auto; min-width:24px !important; }
    html[data-native-theme] :is(th[data-column-key="0"],th[data-column-key="1"],th[data-column-key="7"]) .header-label { white-space:nowrap !important; display:inline-block; }
    html[data-native-theme] td { overflow-wrap:break-word; word-break:normal; vertical-align:top; }
    html[data-native-theme] a.btn-link { display:inline-flex; align-items:center; justify-content:center; min-height:48px; padding:10px 14px; border:1px solid #24479D; border-radius:10px; background-color:#24479D !important; color:#FFFFFF !important; text-decoration:none !important; white-space:normal; overflow-wrap:normal; }
    html[data-native-theme] a.btn-link:focus-visible { outline:3px solid #1F5EFF; outline-offset:3px; }
    html[data-native-theme] :is(th,td) :is(input,button) { min-width:3em; }
    html[data-native-theme=dark] { color-scheme:dark; --bg:#111827; --card:#1E293B; --text:#E8EDF5; --muted:#C0CDDE; --border:#526179; --primary:#9BBDFF; --success-bg:#103E30; --success-text:#8DF0BB; --danger-bg:#51202A; --danger-text:#FFD2D7; }
    html[data-native-theme=dark] body { background:#111827 !important; color:#E8EDF5 !important; }
    html[data-native-theme=dark] body * { background-color:transparent !important; color:inherit !important; border-color:#526179 !important; }
    html[data-native-theme=dark] :is(.card,.item,.summary-item,.total-box,table,details,.upload-area,.modal-content,.modal-box,#sugestoes) { background-color:#1E293B !important; }
    html[data-native-theme=dark] :is(input,select,textarea,button,.btn-secondary,.tab,.mode,.fuel) { background-color:#293E5C !important; color:#F1F5FC !important; }
    html[data-native-theme=dark] input::placeholder, html[data-native-theme=dark] textarea::placeholder { color:#C0CDDE !important; opacity:1; }
    html[data-native-theme=dark] :is(.btn-primary,.go,.tab.on,.mode.active,.fuel.selected,.note-tab.active,thead th) { background-color:#24479D !important; color:#FFFFFF !important; }
    html[data-native-theme=dark] :is(#discountTotalBox,.status.success) { background-color:#103E30 !important; border-color:#8DF0BB !important; color:#8DF0BB !important; }
    html[data-native-theme=dark] :is(#discountTotalBox,.status.success) * { color:#8DF0BB !important; }
    html[data-native-theme=dark] :is(.price,.scan-code-valid) { color:#8DF0BB !important; }
    html[data-native-theme=dark] .status.error { background-color:#51202A !important; color:#FFD2D7 !important; }
    html[data-native-theme=dark] a { color:#9BBDFF !important; text-decoration:underline; }
    html[data-native-theme=dark] a.btn-link { background-color:#24479D !important; border-color:#9BBDFF !important; color:#FFFFFF !important; }
    html[data-native-theme=dark] a.btn-link:focus-visible { outline-color:#9BBDFF; }
    html[data-native-theme=dark] button:disabled { opacity:.65; }
  }`;
  document.head.appendChild(style);
  let scheduled=false, lastColumns='';
  function fitColumns(){
    scheduled=false;
    const table=document.querySelector('#draggableHeaderRow')?.closest('table');if(!table)return;
    const headers=Array.from(table.querySelectorAll('thead th')).filter(h=>getComputedStyle(h).display!=='none');
    const context=document.createElement('canvas').getContext('2d');
    const em=parseFloat(getComputedStyle(document.body).fontSize);
    const widths=headers.map(header=>{
      const key=header.dataset.columnKey,label=header.querySelector('.header-label'),style=getComputedStyle(label||header),cell=getComputedStyle(header);
      context.font=style.fontWeight+' '+style.fontSize+' '+style.fontFamily;
      const padding=parseFloat(cell.paddingLeft)+parseFloat(cell.paddingRight)+4;
      const icon=header.querySelector('.drag-handle'),iconStyle=icon?getComputedStyle(icon):null;
      const iconWidth=icon?context.measureText(icon.textContent).width+(parseFloat(iconStyle.marginRight)||0):0;
      const labelWidth=context.measureText(label?.textContent||header.textContent).width;
      const heading=(key==='0'?Math.max(labelWidth,iconWidth):labelWidth+iconWidth)+padding+4;
      if(key==='0'||key==='7'){
        const values=Array.from(table.querySelectorAll('tbody td[data-column-key="'+key+'"]')).map(td=>context.measureText(td.textContent).width+padding);
        return Math.ceil(Math.max(heading,...values));
      }
      if(key==='1')return 0;
      return Math.ceil((['11','15'].includes(key)?12:10)*em);
    });
    const product=headers.findIndex(h=>h.dataset.columnKey==='1');
    if(product>=0){const prefix=headers.slice(0,product).every(h=>['0','7'].includes(h.dataset.columnKey))?widths.slice(0,product).reduce((a,b)=>a+b,0):0;
      widths[product]=Math.ceil(Math.min(24*em,Math.max(10*em,table.closest('.table-wrap').clientWidth-prefix)));}
    const signature=headers.map((h,i)=>h.dataset.columnKey+':'+widths[i]).join('|');if(signature===lastColumns)return;lastColumns=signature;
    let group=table.querySelector('colgroup');if(!group){group=document.createElement('colgroup');table.prepend(group);}
    group.innerHTML=widths.map(width=>'<col style="width:'+width+'px">').join('');
    table.style.setProperty('--nfe-table-width',widths.reduce((sum,w)=>sum+w,0)+'px');
  }
  function scheduleColumns(){if(!scheduled){scheduled=true;requestAnimationFrame(fitColumns);}}
  window.fitNativeInvoiceColumns=fitColumns;
  const headerRow=document.getElementById('draggableHeaderRow'),body=document.getElementById('itemsBody');
  if(headerRow)new MutationObserver(scheduleColumns).observe(headerRow,{childList:true,subtree:true,attributes:true,attributeFilter:['class']});
  if(body)new MutationObserver(scheduleColumns).observe(body,{childList:true});
  addEventListener('resize',scheduleColumns);
  window.applyNativeAppearance = ({dark, fontPercent}) => {
    const percent = Math.max(90, Math.min(160, Number(fontPercent) || 100));
    document.documentElement.dataset.nativeTheme = dark ? 'dark' : 'light';
    document.documentElement.dataset.nativeFontPercent = String(percent);
    document.documentElement.style.setProperty('--nfe-scale', String(percent / 100));
    fitColumns();
  };
})();
