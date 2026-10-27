/* Shared fiscal acquisition model. Values remain XML-derived; edits are separate. */
(function(root,factory){const api=factory();if(typeof module==='object'&&module.exports)module.exports=api;else root.NfeCost=api;})(typeof globalThis!=='undefined'?globalThis:this,()=>{
 'use strict';
 const FIELDS={discount:'Desconto',st:'ICMS-ST',fcpst:'FCP-ST',ipi:'IPI',ipiDevol:'IPI devolvido',freight:'Frete',insurance:'Seguro',other:'Outras despesas',ii:'II',pisst:'PIS-ST cobrado',cofinsst:'COFINS-ST cobrado',deson:'ICMS desonerado deduzido',mono:'ICMS monofásico a reter'};
 function decimal(value){
  const text=String(value??'0').trim().replace(',','.');
  if(text.length>40||!/^[-+]?(?:\d+(?:\.\d*)?|\.\d+)(?:[eE][-+]?\d+)?$/.test(text))throw new Error('Número inválido');
  const [mantissa,power='0']=text.toLowerCase().split('e'),exponent=Number(power);
  if(!Number.isInteger(exponent)||Math.abs(exponent)>30)throw new Error('Precisão excedida');
  const negative=mantissa[0]==='-',clean=mantissa.replace(/^[+-]/,''),[whole,fraction='']=clean.split('.');
  let n=BigInt((whole+fraction)||'0')*(negative?-1n:1n),d=10n**BigInt(fraction.length);
  if(exponent>=0)n*=10n**BigInt(exponent);else d*=10n**BigInt(-exponent);
  return [n,d];
 }
 function round(n,d){if(d<=0n)throw new Error('Divisor inválido');return n<0n?-round(-n,d):(n+d/2n)/d;}
 function productDecimal(a,b){
  const[n,d]=decimal(a),[m,e]=decimal(b),digits=(d*e).toString().length-1;
  let text=(n*m).toString(),sign='';if(text[0]==='-'){sign='-';text=text.slice(1);}
  text=text.padStart(digits+1,'0');
  return sign+(digits?text.slice(0,-digits)+'.'+text.slice(-digits):text).replace(/(\.\d*?)0+$/,'$1').replace(/\.$/,'');
 }
 function safe(n){const value=Number(n);if(!Number.isSafeInteger(value))throw new Error('Valor excede precisão segura');return value;}
 function cents(value){if(value==null||value==='')return null;const[n,d]=decimal(value);return safe(round(n*100n,d));}
 function positive(value){try{const[n,d]=decimal(value);const v=Number(n)/Number(d);return n>0n&&Number.isFinite(v)?v:null;}catch{return null;}}
 function allocate(amount,weights){
  if(!Number.isSafeInteger(amount)||amount<0||weights.some(w=>!Number.isSafeInteger(w)||w<0))throw new Error('Rateio inválido');
  const total=weights.reduce((s,w)=>s+BigInt(w),0n);if(!total){if(amount)throw new Error('Rateio sem valor de produtos');return weights.map(()=>0);}
  const parts=weights.map((w,index)=>{const n=BigInt(amount)*BigInt(w);return {index,value:safe(n/total),rest:n%total};});
  let remaining=amount-parts.reduce((s,p)=>s+p.value,0);const ranked=[...parts].sort((a,b)=>a.rest===b.rest?a.index-b.index:a.rest>b.rest?-1:1);
  for(let i=0;i<remaining;i++)ranked[i].value++;return parts.map(p=>p.value);
 }
 function unitCents(total,quantity,factor=1,markup=0){
  const[q,qd]=decimal(quantity),[f,fd]=decimal(factor),[m,md]=decimal(markup);
  if(q<=0n||f<=0n||m<0n)throw new Error('Quantidade, fator ou acréscimo inválido');
  return safe(round(BigInt(total)*qd*fd*(100n*md+m),q*f*100n*md));
 }
 const unitName=u=>String(u||'').trim().toUpperCase().replace(/\./g,'');
 const singles=new Set(['UN','UND','UNID','UNIDADE','UNIDADES','PÇ','PECA','PEÇA']);
 const bulk=new Set(['KG','G','MG','T','L','LT','ML','M','M2','M²','M3','M³']);
 function conversion(item,override={}){
  const unit=unitName(item.unit), qty=positive(item.quantity),factor=positive(override.factor);
  if(Object.keys(override).length && (!factor || factor>1000000 || !override.confirmed || !String(override.base||'').trim()))return {factor,base:String(override.base||'UN').slice(0,30),confirmed:false,source:'Conversão editada: confirme um fator positivo e a unidade final',units:qty&&factor?qty*factor:null};
  if(override.confirmed && factor && factor<=1000000 && String(override.base||'').trim())return {factor,base:String(override.base).trim().slice(0,30),confirmed:true,source:'Confirmado pelo usuário',units:qty?qty*factor:null};
  if(singles.has(unit)&&singles.has(unitName(item.taxUnit))&&item.taxQuantity!=null&&qty&&Number(item.taxQuantity)!==qty){
   const count=positive(item.taxQuantity);return {factor:count?count/qty:null,base:'UN',confirmed:false,source:'UN comercial e UN tributável divergem: confirme a unidade física vendável',units:count};
  }
  if(singles.has(unit))return {factor:1,base:'UN',confirmed:true,source:'Unidade comercial UN',units:qty};
  if(bulk.has(unit))return {factor:1,base:unit,confirmed:true,source:'A granel: preço por '+unit+', não por peça',units:qty};
  const tq=positive(item.taxQuantity),tu=unitName(item.taxUnit);
  let candidate=null;
  if(qty&&tq&&singles.has(tu)){
   try{const gross=cents(item.gross), a=cents(String(Number(item.taxUnitValue)*tq)), b=cents(String(Number(item.commercialUnitValue)*qty));if(gross!=null&&a!=null&&b!=null&&Math.abs(a-gross)<=1&&Math.abs(b-gross)<=1)candidate=tq/qty;}catch{/* Cannot prove the suggested conversion. */}
  }
  return {factor:factor||candidate,base:String(override.base||'UN').slice(0,30),confirmed:false,source:candidate?'XML: '+candidate+' UN tributáveis por '+(unit||'embalagem')+'; confirme a unidade vendável':'Unidades por embalagem a confirmar; descrição não determina fator',units:qty&&(factor||candidate)?qty*(factor||candidate):null};
 }
 function productKey(data,item){const id=item.productCode&&item.productCode!=='—'?'SKU:'+item.productCode:/^\d{8,14}$/.test(item.barcode||'')?'GTIN:'+item.barcode:'NOTA:'+data.accessKey+':'+data.nfeNumber+':'+item.item+':'+item.name;return JSON.stringify([data.issuerId||data.issuer||'',id,unitName(item.unit)]);}
 function calculate(data,overrides={},options={}){
  const raw=data.fiscalRaw, warnings=[];const warn=m=>{if(!warnings.includes(m))warnings.push(m);};
  if(!raw)return {warnings:['Sessão antiga: reimporte o XML para calcular IPI, despesas e unidades.'],ready:false,items:data.items.map(()=>({ready:false,reason:'Reimporte o XML',conversion:{confirmed:false,base:'UN'}})),classifiedCents:null,officialCents:null,differenceCents:null,totals:{}};
  const money=(value,label)=>{try{const v=cents(value);if(v!=null&&v<0){warn(label+': valor negativo não classificado');return 0;}return v;}catch{warn(label+': valor inválido ou fora de precisão');return 0;}};
  const rows=raw.items.map((r,index)=>({index,raw:r,included:r.indTot!=='0',gross:money(r.gross,'Produtos')??0,parts:{},allocations:[]}));
  const eligible=rows.filter(r=>r.included), totals={};
  const officialGross=money(raw.totals.gross,'vProd'),gross=eligible.reduce((s,r)=>s+r.gross,0);
  if(officialGross!=null&&officialGross!==gross)warn('vProd oficial difere dos itens incluídos (indTot=1): '+(officialGross-gross)+' centavo(s).');
  for(const [key,label] of Object.entries(FIELDS)){
   const official=money(raw.totals[key],label), known=eligible.reduce((s,r)=>s+(money(r.raw[key],label)??0),0);
   rows.forEach(r=>{r.parts[key]=money(r.raw[key],label)??0;});
   if(official!=null&&official!==known){
    if(official<known)warn(label+': total oficial menor que a soma explícita dos itens.');
    else{
     const missing=eligible.filter(r=>r.raw[key]==null),residual=official-known;
     if(!missing.length)warn(label+': divergência entre total e itens explícitos (inclusive zeros).');
     else try{const amounts=allocate(residual,missing.map(r=>r.gross));missing.forEach((r,i)=>{r.parts[key]+=amounts[i];if(amounts[i])r.allocations.push(label+': rateio por vProd, maior resto em centavos');});}
     catch{warn(label+': '+residual+' centavo(s) sem base para rateio.');}
    }
   }
   totals[key]=eligible.reduce((s,r)=>s+r.parts[key],0);
  }
  const extras=raw.special||[];extras.forEach(warn);
  let rtcNote='';
  if(raw.rtc?.present){
   if(!['2025','2026'].includes(raw.rtc.year))warn('RTC: ano/regra de incidência não suportado para IBS/CBS/IS; classificação pendente.');
   else rtcNote='RTC '+raw.rtc.year+': IBS/CBS/IS não são acrescentados por cima de vProd (exceção 2025/2026 da NT 2025.002). Não há apropriação automática de créditos. Valores informados: IBS '+(raw.rtc.ibs||'não informado')+', CBS '+(raw.rtc.cbs||'não informado')+', IS '+(raw.rtc.is||'não informado')+'.';
  }
  rows.forEach(r=>{if(!['0','1'].includes(r.raw.indTot??'1'))warn('indTot inválido: classificação pendente.');});
  if(!options.acquisitionConfirmed)(raw.context||[]).forEach(warn);
  if(rows.some(r=>!r.included&&Object.values(r.parts).some(v=>v>0)))warn('Item indTot=0 com componentes adicionais: classificação específica pendente.');
  if(raw.finNFe&&raw.finNFe!=='1'&&!options.acquisitionConfirmed)warn('Finalidade '+raw.finNFe+': confirme a classificação da operação antes de formar custo de aquisição.');
  if(raw.model&&!['55','65'].includes(raw.model))warn('Modelo fiscal não classificado como NF-e/NFC-e.');
  const charged=r=>r.gross-r.parts.discount-r.parts.deson+r.parts.st+r.parts.fcpst+r.parts.ipi+r.parts.ipiDevol+r.parts.freight+r.parts.insurance+r.parts.other+r.parts.ii+r.parts.pisst+r.parts.cofinsst+r.parts.mono;
  const classified=eligible.reduce((s,r)=>s+charged(r),0),official=money(raw.totals.invoice,'vNF'),difference=official==null?null:official-classified;
  if(raw.newTotal!=null && money(raw.newTotal,'vNFTot')!==classified)warn('Novo total vNFTot difere dos componentes classificados; não presumir tributos ou diferenças.');
  rows.forEach(r=>{if(r.raw.newItemTotal!=null&&money(r.raw.newItemTotal,'vItem')!==charged(r))warn('vItem difere dos componentes do item '+(r.index+1)+'.');});
  if(official==null)warn('vNF ausente: não foi possível conciliar com o total oficial.');
  else if(difference)warn('Diferença não classificada de '+difference+' centavo(s) entre vNF e componentes. Não foi presumida como IPI.');
  let extra=money(options.extra||'0','Custo adicional manual')??0,credit=money(options.credit||'0','Crédito recuperável confirmado')??0;
  let extraParts=rows.map(()=>0),creditParts=rows.map(()=>0);
  try{extraParts=allocate(extra,rows.map(r=>r.included?r.gross:0));creditParts=allocate(credit,rows.map(r=>r.included?r.gross:0));}catch{warn('Ajuste manual sem base de produtos para rateio.');}
  if(credit>classified+extra)warn('Crédito confirmado excede o custo classificado.');
  if(![gross,classified,extra,credit,official??0].every(Number.isSafeInteger))warn('Soma excede precisão monetária segura.');
  const ready=warnings.length===0;
  const items=rows.map(r=>{
   const item=data.items[r.index],override=overrides[productKey(data,item)],conv=conversion({...r.raw,unit:item.unit,quantity:r.raw.quantity},override);
   if(conv.confirmed&&positive(r.raw.quantity)){conv.exactFactor=override?.confirmed?String(override.factor):'1';conv.factorText=productDecimal('1',conv.exactFactor);conv.unitsExact=productDecimal(r.raw.quantity,conv.exactFactor);}
   const total=charged(r)+extraParts[r.index]-creditParts[r.index];
   let reason=!r.included?'Fora do total: indTot=0':!ready?'Conciliação/classificação pendente':!positive(r.raw.quantity)?'Quantidade comercial inválida':!conv.confirmed?'Conversão a confirmar':total<0?'Crédito/desconto excede custo':null;
   const result={parts:r.parts,grossCents:r.gross,totalCents:total,extraCents:extraParts[r.index],creditCents:creditParts[r.index],allocations:r.allocations,conversion:conv,ready:!reason,reason,commercialUnit:item.unit||'não informada',quantity:r.raw.quantity};
   if(!reason)try{result.costCents=unitCents(total,r.raw.quantity,conv.exactFactor??conv.factor);result.basicCents=unitCents(r.gross,r.raw.quantity,conv.exactFactor??conv.factor);result.ipiCents=unitCents(r.parts.ipi,r.raw.quantity,conv.exactFactor??conv.factor);result.stCents=unitCents(r.parts.st,r.raw.quantity,conv.exactFactor??conv.factor);}catch{result.ready=false;result.reason='Quantidade/fator fora da precisão segura';}
   return result;
  });
  return {warnings,ready,items,totals,rtcNote,grossCents:gross,classifiedCents:classified,officialCents:official,differenceCents:difference,extraCents:extra,creditCents:credit,costCents:classified+extra-credit,method:'Rateios somente do residual de totais presentes, por vProd dos itens sem campo, maior resto em centavos. ICMS/PIS/COFINS normais não adicionados. Créditos somente por confirmação manual.'};
 }
 function price(row,markup){if(!row.ready)return null;try{return unitCents(row.totalCents,row.quantity,row.conversion.exactFactor??row.conversion.factor,markup);}catch{return null;}}
 return {FIELDS,cents,allocate,unitCents,conversion,productKey,calculate,price};
});
