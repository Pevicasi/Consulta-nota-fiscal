package br.com.pevicasi.nfefacil;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class DualQuantityTest {
 private static String js(ActivityScenario<MainActivity> s,String code)throws Exception{return AppIntegrationTest.js(s,code);}
 private static String fixture(String name)throws Exception{try(InputStream in=InstrumentationRegistry.getInstrumentation().getContext().getAssets().open(name)){return new String(in.readAllBytes(),StandardCharsets.UTF_8);}}
 @After public void cleanup()throws Exception{
  UiDevice d=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());d.setOrientationNatural();d.unfreezeRotation();
  InstrumentationRegistry.getInstrumentation().getTargetContext().getSharedPreferences("appearance",0).edit().clear().commit();
 }
 @Test public void unitsPackagingWeightsAndDiscountsUseOneQuantityColumnInRealWebView()throws Exception{
  UiDevice d=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
  try(ActivityScenario<MainActivity> s=ActivityScenario.launch(MainActivity.class)){
   AppIntegrationTest.waitForJs(s,"window.__compactInvoice && typeof acceptNativeXmlDocuments==='function'");
   js(s,"localStorage.clear();costFactors={};startNewConsultation();loadedXmls=[];markupPercentInput.value='20';document.querySelectorAll('.column-checkbox').forEach(c=>{const on=['0','7','1','2','16','3','4','5'].includes(c.dataset.columnKey);if(c.checked!==on)c.click()});acceptNativeXmlDocuments([{name:'dual-quantity.xml',text:"+JSONObject.quote(fixture("dual-quantity.xml"))+"}]);true");
   AppIntegrationTest.waitForJs(s,"currentItems.length===22");
   String expected=fixture("dual-quantity.json");
   String snapshot="currentFinance.items.map((r,i)=>{const q=document.querySelectorAll('td[data-column-key=\"7\"]')[i],norm=e=>e?e.textContent.trim().replace(/\\s+/g,' ').toUpperCase():null;return {primary:norm(q.querySelector('.quantity-primary')),packaging:norm(q.querySelector('.quantity-packaging')),cost:r.costCents,price:NfeCost.price(r,getMarkupPercent()),packageCost:r.packageCostCents??null,packagePrice:r.packagePriceCents??null,ipi:r.ipiCents,st:r.stCents,total:r.totalCents,value:r.basicCents,net:r.netProductUnitCents}})";
   assertEquals("All twenty-two synthetic XML rows retain exact units, tax, discount and package prices", "true",js(s,"JSON.stringify("+snapshot+")===JSON.stringify(("+expected+").map(r=>({...r,primary:r.primary.toUpperCase()})))"));
   assertEquals("true",js(s,"document.querySelectorAll('thead th').length===17 && document.querySelector('th[data-column-key=\"2\"] .header-label').textContent==='VALOR' && document.querySelector('th[data-column-key=\"4\"] .header-label').textContent==='VALOR COM DESCONTO' && !document.querySelector('thead').textContent.includes('Compra básica') && Array.from(document.querySelectorAll('td[data-column-key=\"1\"]')).every((e,i)=>e.textContent===currentItems[i].name) && document.querySelectorAll('#itemsBody input,#itemsBody button,#itemsBody details,#productCostDialog').length===0"));
   assertEquals("true",js(s,"Array.from(document.querySelectorAll('.quantity-packaging')).every(e=>{const a=e.parentElement.querySelector('.quantity-primary');return e.getBoundingClientRect().top>=a.getBoundingClientRect().bottom-1})"));
   assertEquals("true",js(s,"currentFinance.items[7].packaging.unit==='FD' && currentFinance.items[8].conversion.base==='KG' && currentFinance.items[3].conversion.units===81 && Number(currentFinance.items[3].packaging.quantity)===3 && document.querySelectorAll('td[data-column-key=\"4\"]')[6].querySelectorAll('.package-value').length===0"));
   assertEquals("true",js(s,"document.querySelectorAll('td[data-column-key=\"4\"]')[13].firstChild.textContent.includes('4,96') && document.querySelectorAll('td[data-column-key=\"4\"]')[13].querySelector('.package-value').textContent.includes('59,50') && currentFinance.items[13].costCents===583 && NfeCost.price(currentFinance.items[13],20)===700"));
   for(boolean landscape:new boolean[]{false,true}){
    if(landscape)d.setOrientationLeft();else d.setOrientationNatural();Thread.sleep(300);
    for(int percent:new int[]{90,100,140,160})for(String mode:new String[]{"light","dark","auto"}){
     s.onActivity(a->{a.setFontPercent(percent);a.setThemeMode(mode);});
     AppIntegrationTest.waitForJs(s,"document.documentElement.dataset.nativeFontPercent==='"+percent+"'");
     assertEquals("true",js(s,"(()=>{fitNativeInvoiceColumns();return document.documentElement.scrollWidth<=innerWidth+1 && Array.from(document.querySelectorAll('td[data-column-key=\"7\"]')).every(e=>e.scrollWidth<=e.clientWidth+1)})()"));
     if((percent==100||percent==160)&&!mode.equals("auto")){
      js(s,"document.querySelector('#itemsBody tr').scrollIntoView({block:'start',inline:'start',behavior:'instant'});document.querySelector('.table-wrap').scrollLeft=0;true");Thread.sleep(200);
      AppearanceTest.screenshot("dual-quantity-"+percent+"-"+mode+"-"+(landscape?"landscape":"portrait"),s);
      js(s,"document.querySelectorAll('#itemsBody tr')[16].querySelector('td[data-column-key=\"7\"]').scrollIntoView({block:'start',inline:'start',behavior:'instant'});true");Thread.sleep(200);
      AppearanceTest.screenshot("fitilho-weight-"+percent+"-"+mode+"-"+(landscape?"landscape":"portrait"),s);
      js(s,"document.querySelectorAll('#itemsBody tr')[18].querySelector('td[data-column-key=\"2\"]').scrollIntoView({block:'start',inline:'start',behavior:'instant'});true");Thread.sleep(200);
      AppearanceTest.screenshot("ebd-values-"+percent+"-"+mode+"-"+(landscape?"landscape":"portrait"),s);
     }
    }
   }
   js(s,"markupPercentInput.value='12.5';markupPercentInput.dispatchEvent(new Event('input',{bubbles:true}));true");
   assertEquals("true",js(s,"NfeCost.price(currentFinance.items[0],getMarkupPercent())===238 && currentFinance.items[0].packagePriceCents===2851"));
   s.recreate();AppIntegrationTest.waitForJs(s,"window.__compactInvoice && currentFinance?.items.length===22");
   assertEquals("true",js(s,"currentFinance.items[3].conversion.units===81 && Number(currentFinance.items[3].packaging.quantity)===3 && currentFinance.items[0].packagePriceCents===2851 && buildFiscalCSV().headers.includes('VALOR COM DESCONTO') && buildFiscalCSV().headers.includes('Quantidade de embalagens')"));
  }
 }
}
