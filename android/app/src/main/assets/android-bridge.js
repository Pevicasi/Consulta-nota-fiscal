(() => {
  if (window.__nfeNative || !window.NativeApp) return;
  window.__nfeNative = true;
  const send = (type, value = '') => NativeApp.postMessage(JSON.stringify({type, value}));
  const bind = (id, fn) => document.getElementById(id)?.addEventListener('click', e => {
    e.preventDefault(); e.stopImmediatePropagation(); fn();
  }, true);
  bind('scanBtn', () => send('scan'));
  bind('pasteScannedBtn', () => send('paste'));
  window.acceptNativeKey = key => {
    if (!isValidNfeKey(key)) return;
    if (document.getElementById('result') && !document.getElementById('result').classList.contains('hidden')) startNewConsultation();
    invoiceSearch.value = key;
    addScannedCode(key); updateCodeButtons();
    showStatus('Chave válida lida no aparelho. Carregue o XML para consultar os itens.', 'success');
    saveAppSession();
  };
  window.acceptNativeClipboard = value => {
    invoiceSearch.value = value.replace(/\D/g, ''); updateCodeButtons();
    if (isValidNfeKey(invoiceSearch.value)) addScannedCode(invoiceSearch.value);
    saveAppSession();
  };
  Object.defineProperty(navigator, 'clipboard', {configurable: true, value: {
    writeText: value => { send('copy', value); return Promise.resolve(); }
  }});
  window.print = () => send('print');
  const click = HTMLAnchorElement.prototype.click;
  HTMLAnchorElement.prototype.click = function () {
    if (this.href.startsWith('blob:') && this.download) {
      const name = this.download;
      fetch(this.href).then(r => r.blob()).then(blob => {
        const reader = new FileReader();
        reader.onload = () => send('export', JSON.stringify({name, data: reader.result}));
        reader.readAsDataURL(blob);
      }).catch(() => send('error', 'Não foi possível preparar o CSV.'));
      return;
    }
    return click.call(this);
  };
})();
