const {test}=require('node:test');const assert=require('node:assert/strict');const F=require('../fiscal-cost.js');
function model(rows,totals={},extra={}){const items=rows.map((r,i)=>({unit:r.unit||'UN',name:'Produto '+i,productCode:'P'+i,quantity:Number(r.quantity||1)}));return {issuerId:'1',items,fiscalRaw:{finNFe:'1',model:'55',items:rows.map(r=>({quantity:'1',unit:'UN',indTot:'1',...r})),totals,...extra}};}
test('exact fiscal acquisition includes charged components once, excludes normal informational taxes',()=>{
 const data=model([{quantity:'2',gross:'100',discount:'10',st:'10',fcpst:'2',ipi:'5',ipiDevol:'1',freight:'3',insurance:'1',other:'2',ii:'1',vICMS:'18',vPIS:'1',vCOFINS:'2'}],{gross:'100',discount:'10',st:'10',fcpst:'2',ipi:'5',ipiDevol:'1',freight:'3',insurance:'1',other:'2',ii:'1',invoice:'115'});
 const f=F.calculate(data);assert.deepEqual(f.warnings,[]);assert.equal(f.classifiedCents,11500);assert.equal(f.items[0].costCents,5750);assert.equal(f.items[0].ipiCents,250);assert.equal(F.price(f.items[0],20),6900);assert.equal(F.price(f.items[0],0),5750);assert.equal(F.price(f.items[0],50),8625);
 const charged=F.calculate(model([{gross:'10',mono:'1',pisst:'0.50',cofinsst:'0.25'}],{gross:'10',mono:'1',invoice:'11.75'}));assert.equal(charged.classifiedCents,1175);assert.equal(charged.ready,true);
});
test('total-only expenses and discounts allocate exact cents by value with deterministic largest remainder',()=>{
 const d=model([{gross:'1'},{gross:'1'},{gross:'1'}],{gross:'3',freight:'.01',discount:'.02',invoice:'2.99'});
 // XML decimal syntax requires a leading zero.
 d.fiscalRaw.totals.freight='0.01';d.fiscalRaw.totals.discount='0.02';
 const f=F.calculate(d);assert.deepEqual(f.warnings,[]);assert.deepEqual(f.items.map(r=>r.parts.freight),[1,0,0]);assert.deepEqual(f.items.map(r=>r.parts.discount),[1,1,0]);assert.equal(f.items.reduce((s,r)=>s+r.totalCents,0),299);
 for(let n=1;n<25;n++)for(let amount=0;amount<60;amount++){const a=F.allocate(amount,Array.from({length:n},(_,i)=>i+1));assert.equal(a.reduce((s,v)=>s+v,0),amount);}
});
test('explicit item fields including zero are not overwritten and only missing items receive residual',()=>{
 let f=F.calculate(model([{gross:'10',freight:'1'},{gross:'20'},{gross:'30',freight:'0'}],{gross:'60',freight:'1.01',invoice:'61.01'}));assert.deepEqual(f.items.map(r=>r.parts.freight),[100,1,0]);assert.equal(f.ready,true);
 f=F.calculate(model([{gross:'10',ipi:'1'}],{gross:'10',ipi:'0',invoice:'10'}));assert.equal(f.ready,false);assert.equal(f.items[0].parts.ipi,100);
 f=F.calculate(model([{gross:'10',freight:'0'}],{gross:'10',freight:'1',invoice:'11'}));assert.equal(f.ready,false);assert.equal(f.items[0].parts.freight,0);
});
test('difference in official invoice is never inferred as IPI and suppresses misleading unit price',()=>{
 const f=F.calculate(model([{gross:'1182.19'}],{gross:'1182.19',invoice:'1184.53'}));assert.equal(f.differenceCents,234);assert.equal(f.totals.ipi,0);assert.equal(f.items[0].ready,false);assert.equal(F.price(f.items[0],20),null);
});
test('indTot zero is excluded from allocations and cannot receive a final price',()=>{
 const f=F.calculate(model([{gross:'10'},{gross:'50',indTot:'0'}],{gross:'10',freight:'1',invoice:'11'}));assert.equal(f.classifiedCents,1100);assert.equal(f.items[0].parts.freight,100);assert.equal(f.items[1].parts.freight,0);assert.equal(f.items[1].ready,false);
});
test('package/tributary ratio is only a candidate until a sellable conversion is explicitly confirmed',()=>{
 const d=model([{gross:'120',quantity:'2',unit:'CX',commercialUnitValue:'60',taxUnit:'UN',taxQuantity:'24',taxUnitValue:'5'}],{gross:'120',invoice:'120'});
 let f=F.calculate(d);assert.equal(f.items[0].conversion.factor,12);assert.equal(f.items[0].ready,false);assert.equal(F.price(f.items[0],20),null);
 const key=F.productKey(d,d.items[0]);f=F.calculate(d,{[key]:{factor:'12',base:'UN',confirmed:true}});assert.equal(f.items[0].conversion.units,24);assert.equal(f.items[0].costCents,500);assert.equal(F.price(f.items[0],20),600);
 const ambiguous=model([{gross:'120',quantity:'2',unit:'UN',taxUnit:'UN',taxQuantity:'24'}],{gross:'120',invoice:'120'});assert.equal(F.calculate(ambiguous).items[0].ready,false);
 for(const factor of ['0','-2','NaN','']){const bad=F.calculate(d,{[key]:{factor,base:'UN',confirmed:true}});assert.equal(bad.items[0].ready,false);}
});
test('bulk kilograms remain kilograms; title does not guess units in an unknown package',()=>{
 let d=model([{gross:'30',quantity:'1.5',unit:'KG',taxUnit:'G',taxQuantity:'1500',taxUnitValue:'0.02'}],{gross:'30',invoice:'30'}),f=F.calculate(d);assert.equal(f.items[0].conversion.base,'KG');assert.equal(f.items[0].costCents,2000);assert.equal(F.price(f.items[0],20),2400);
 d=model([{gross:'30',unit:'FD'}],{gross:'30',invoice:'30'});d.items[0].name='FARDO 12 UN';f=F.calculate(d);assert.equal(f.items[0].conversion.factor,null);assert.equal(f.items[0].ready,false);
});
test('fractional factors and markup use exact unrounded acquisition basis',()=>{
 const d=model([{gross:'0.01',quantity:'3'}],{gross:'0.01',invoice:'0.01'}),f=F.calculate(d);assert.equal(f.items[0].costCents,0);assert.equal(F.price(f.items[0],200),1);
 assert.equal(F.unitCents(10000,'3','0.5',20),8000);assert.equal(F.unitCents(10000,'3','0.5',0),6667);assert.equal(F.unitCents(10000,'3','0.5','12.5'),7500);assert.equal(F.unitCents(10000,'3','0.5',1e-7),6667);assert.equal(F.price(f.items[0],1e100),null);
});
test('extra purchase expense and recoverable credit are explicit, separate, and never mutate XML-derived inputs',()=>{
 const d=model([{gross:'10',quantity:'2'}],{gross:'10',invoice:'10'}),snapshot=JSON.stringify(d),f=F.calculate(d,{}, {extra:'1',credit:'0.50'});assert.equal(f.classifiedCents,1000);assert.equal(f.officialCents,1000);assert.equal(f.costCents,1050);assert.equal(f.items[0].costCents,525);assert.equal(F.price(f.items[0],20),630);assert.equal(JSON.stringify(d),snapshot);
 assert.equal(F.calculate(d,{}, {credit:'11'}).ready,false);
});
test('returns and exceptional fiscal components stay pending until applicable classification is known',()=>{
 const d=model([{gross:'10',ipiDevol:'1'}],{gross:'10',ipiDevol:'1',invoice:'11'},{finNFe:'4'});assert.equal(F.calculate(d).ready,false);assert.equal(F.calculate(d,{}, {acquisitionConfirmed:true}).items[0].costCents,1100);
 d.fiscalRaw.special=['IBS/CBS não classificados'];assert.equal(F.calculate(d,{}, {acquisitionConfirmed:true}).ready,false);
 assert.equal(F.calculate({items:[{unit:'UN'}]}).ready,false);
 const rtc=model([{gross:'10',newItemTotal:'10'}],{gross:'10',invoice:'10'},{rtc:{present:true,year:'2026',ibs:'1',cbs:'2',is:'3'},newTotal:'10'});assert.equal(F.calculate(rtc).classifiedCents,1000);assert.equal(F.calculate(rtc).ready,true);rtc.fiscalRaw.rtc.year='2027';assert.equal(F.calculate(rtc).ready,false);
});
