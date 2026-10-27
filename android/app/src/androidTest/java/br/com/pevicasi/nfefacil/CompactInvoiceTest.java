package br.com.pevicasi.nfefacil;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;
import org.json.JSONObject;
import org.junit.*;
import org.junit.runner.RunWith;
import java.io.*;
import static org.junit.Assert.*;
@RunWith(AndroidJUnit4.class)
public class CompactInvoiceTest {
 private static String js(ActivityScenario<MainActivity>s,String c)throws Exception{return AppIntegrationTest.js(s,c);}
 private static void load(ActivityScenario<MainActivity>s)throws Exception{
  AppIntegrationTest.waitForJs(s,"window.__compactInvoice===true && typeof acceptNativeXmlDocuments==='function'");
  String xml;try(InputStream in=InstrumentationRegistry.getInstrumentation().getContext().getAssets().open("automatic-units.xml")){xml=new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);}
  js(s,"localStorage.clear();costFactors={};startNewConsultation();loadedXmls=[];markupPercentInput.value='20';document.querySelectorAll('.column-checkbox').forEach(c=>{const on=['0','7','1','2','16','3','4','5'].includes(c.dataset.columnKey);if(c.checked!==on)c.click()});acceptNativeXmlDocuments([{name:'sintetica.xml',text:"+JSONObject.quote(xml)+"}]);true");
  AppIntegrationTest.waitForJs(s,"currentItems.length===20");
 }
 @After public void cleanup()throws Exception{UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).setOrientationNatural();UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).unfreezeRotation();InstrumentationRegistry.getInstrumentation().getTargetContext().getSharedPreferences("appearance",0).edit().clear().commit();}
 @Test public void productsStayCompactWithVisibleIpiInEveryThemeScaleAndOrientation()throws Exception{
  UiDevice device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
  try(ActivityScenario<MainActivity>s=ActivityScenario.launch(MainActivity.class)){
   load(s);assertEquals("true",js(s,"document.querySelector('td[data-column-key=\"16\"]').textContent.includes('0,00') && document.querySelectorAll('#itemsBody input,#itemsBody details').length===0"));
   for(boolean wide:new boolean[]{false,true}){
    if(wide)device.setOrientationLeft();else device.setOrientationNatural();Thread.sleep(400);
    for(int percent:new int[]{90,100,140,160})for(String mode:new String[]{"light","dark","auto"}){
     s.onActivity(a->{a.setFontPercent(percent);a.setThemeMode(mode);});
     AppIntegrationTest.waitForJs(s,"document.documentElement.dataset.nativeFontPercent==='"+percent+"'");
     assertEquals("true",js(s,"(()=>{fitNativeInvoiceColumns();const em=parseFloat(getComputedStyle(document.body).fontSize);return Array.from(document.querySelectorAll('#itemsBody tr')).every(r=>r.getBoundingClientRect().height<14*em && r.querySelector('td[data-column-key=\"1\"]').textContent===currentItems[Array.from(r.parentElement.children).indexOf(r)].name)&&document.querySelectorAll('thead th').length===17&&document.querySelector('th[data-column-key=\"16\"]').getClientRects().length>0&&document.documentElement.scrollWidth<=innerWidth+1;})()"));
     if((percent==100||percent==160)&&!mode.equals("auto")){
      js(s,"document.querySelector('#itemsBody tr').scrollIntoView({block:'start',inline:'start',behavior:'instant'});document.querySelector('.table-wrap').scrollLeft=0;true");Thread.sleep(250);
      if(percent==100&&!wide)assertEquals("At least eight compact product rows visible", "true",js(s,"Array.from(document.querySelectorAll('#itemsBody tr')).filter(r=>{const b=r.getBoundingClientRect();return b.top>=0&&b.bottom<=innerHeight}).length>=8"));
      AppearanceTest.screenshot("compact-ipi-"+percent+"-"+mode+"-"+(wide?"landscape":"portrait"),s);
     }
    }
   }
  }
 }
 @Test public void automaticUnitMatrixAndOriginalNamesSurviveRecreationWithoutAnyEditor()throws Exception{
  try(ActivityScenario<MainActivity>s=ActivityScenario.launch(MainActivity.class)){
   load(s);
   String xml;try(InputStream in=InstrumentationRegistry.getInstrumentation().getContext().getAssets().open("automatic-units.xml")){xml=new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);}
   String expected;try(InputStream in=InstrumentationRegistry.getInstrumentation().getContext().getAssets().open("automatic-units.json")){expected=new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);}
   js(s,"startNewConsultation();loadedXmls=[];acceptNativeXmlDocuments([{name:'automatic.xml',text:"+JSONObject.quote(xml)+"}]);true");
   AppIntegrationTest.waitForJs(s,"currentItems.length===20");
   assertEquals("true",js(s,"JSON.stringify(currentFinance.items.map(r=>({factor:r.conversion.factor,units:r.conversion.units,cost:r.costCents,price:NfeCost.price(r,20),base:r.conversion.base,ipi:r.ipiCents,st:r.stCents,equivalent:r.conversion.equivalent})))===JSON.stringify("+expected+")"));
   assertEquals("true",js(s,"currentFinance.warnings.length===0 && Array.from(document.querySelectorAll('td[data-column-key=\"1\"]')).every((e,i)=>e.textContent===currentItems[i].name) && document.querySelectorAll('#itemsBody input,#itemsBody button,#productCostDialog,.cost-audit-print').length===0"));
   js(s,"markupPercentInput.value='12.5';markupPercentInput.dispatchEvent(new Event('input',{bubbles:true}));true");
   assertEquals("238",js(s,"NfeCost.price(currentFinance.items[0],getMarkupPercent())"));
   s.recreate();AppIntegrationTest.waitForJs(s,"window.__compactInvoice && currentFinance?.items.length===20");assertEquals("120",js(s,"currentFinance.items[0].conversion.units"));assertEquals("238",js(s,"NfeCost.price(currentFinance.items[0],getMarkupPercent())"));
  }
 }
}
