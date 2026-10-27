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
    html[data-native-theme] table { min-width:calc(1450px * var(--nfe-scale)); }
    html[data-native-theme] :is(th,td) { min-width:6em; max-width:24em; white-space:normal !important; overflow-wrap:anywhere; text-overflow:clip !important; }
    html[data-native-theme] th .header-label { overflow-wrap:normal; word-break:normal; }
    html[data-native-theme] th[data-column-key="7"] { min-width:12em; }
    html[data-native-theme] :is(th[data-column-key="0"],th[data-column-key="1"],th[data-column-key="7"]) .header-label { white-space:nowrap !important; display:inline-block; }
    html[data-native-theme] td { overflow-wrap:break-word; word-break:normal; }
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
  window.applyNativeAppearance = ({dark, fontPercent}) => {
    const percent = Math.max(90, Math.min(160, Number(fontPercent) || 100));
    document.documentElement.dataset.nativeTheme = dark ? 'dark' : 'light';
    document.documentElement.dataset.nativeFontPercent = String(percent);
    document.documentElement.style.setProperty('--nfe-scale', String(percent / 100));
  };
})();
