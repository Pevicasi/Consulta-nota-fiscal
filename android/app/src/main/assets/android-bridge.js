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
  // Only SAF-selected documents supplied by MainActivity enter this importer.
  // Keep the public site's upload behavior unchanged; retain existing app notes.
  if (location.pathname.endsWith('/site/index.html')) {
    const more = document.createElement('button');
    more.id = 'nativeImportMore'; more.type = 'button'; more.className = 'btn-secondary';
    more.textContent = 'Adicionar XMLs';
    more.addEventListener('click', () => document.getElementById('multipleXmlFiles').click());
    const controls = document.createElement('div');
    controls.className = 'actions'; controls.style.flexDirection = 'column';
    controls.append(more); document.getElementById('notesTabs').before(controls);
    const originalShowStatus = showStatus;
    window.showStatus = (message, type) => {
      // The original status lives inside the upload card, hidden after opening
      // notes. Keep the SAME live content visible in whichever view is active.
      if (result.classList.contains('hidden')) uploadCard.append(statusBox);
      else more.before(statusBox);
      originalShowStatus(message, type);
    };
    statusBox.setAttribute('role','status'); statusBox.setAttribute('aria-live','polite');
    window.acceptNativeXmlDocuments = documents => {
      const before = loadedNotes.slice(), selected = activeNoteIndex;
      const notes = before.slice(), registry = loadedXmls.slice();
      const identity = entry => JSON.stringify(entry.data);
      const known = new Set(notes.map(identity));
      let added = 0, duplicates = 0;
      const errors = [];
      for (const document of documents || []) {
        try {
          if (document.error) throw new Error(document.error);
          if (!document.text?.trim()) throw new Error('Arquivo vazio.');
          const data = parseXML(document.text), id = JSON.stringify(data);
          if (known.has(id)) { duplicates++; continue; }
          const entry = {file:{name:document.name},content:document.text,data,normalizedContent:normalizeSearchValue(document.text)};
          known.add(id); notes.push(entry);
          if (!registry.some(previous => identity(previous) === id)) registry.push(entry);
          added++;
        } catch (error) { errors.push(`${document.name || 'Documento'}: ${error.message || 'XML inválido.'}`); }
      }
      if (added) {
        loadedXmls = registry;
        openLoadedNotes(notes);
        if (before.length) showNote(selected);
      }
      loadedCount.textContent = `${notes.length} XML(s) carregado(s)`;
      const summary = `${added} nota(s) adicionada(s); ${notes.length} nota(s) aberta(s).` +
        (duplicates ? ` ${duplicates} repetida(s), sem duplicar abas.` : '') +
        (errors.length ? ` ${errors.length} arquivo(s) não importado(s): ${errors.join(' ')}` : '');
      showStatus(summary, errors.length ? 'error' : 'success');
      for (const id of ['xmlFile','multipleXmlFiles']) { const input=document.getElementById(id); if(input)input.value=''; }
      saveAppSession();
    };
  }
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
