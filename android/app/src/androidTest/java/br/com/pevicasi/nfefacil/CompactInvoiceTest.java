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
  String xml;try(InputStream in=InstrumentationRegistry.getInstrumentation().getContext().getAssets().open("compact.xml")){xml=new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);}
  js(s,"localStorage.clear();costFactors={};startNewConsultation();loadedXmls=[];document.querySelectorAll('.column-checkbox').forEach(c=>{const on=['0','7','1','2','16','3','4','5'].includes(c.dataset.columnKey);if(c.checked!==on)c.click()});acceptNativeXmlDocuments([{name:'sintetica.xml',text:"+JSONObject.quote(xml)+"}]);true");
  AppIntegrationTest.waitForJs(s,"currentItems.length===15");
 }
 @After public void cleanup()throws Exception{UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).setOrientationNatural();UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).unfreezeRotation();InstrumentationRegistry.getInstrumentation().getTargetContext().getSharedPreferences("appearance",0).edit().clear().commit();}
 @Test public void fifteenProductsStayCompactWithVisibleIpiInEveryThemeScaleAndOrientation()throws Exception{
  UiDevice device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
  try(ActivityScenario<MainActivity>s=ActivityScenario.launch(MainActivity.class)){
   load(s);assertEquals("true",js(s,"document.querySelector('td[data-column-key=\"16\"]').textContent.includes('2,50') && document.querySelectorAll('#itemsBody input,#itemsBody details').length===0"));
   for(boolean wide:new boolean[]{false,true}){
    if(wide)device.setOrientationLeft();else device.setOrientationNatural();Thread.sleep(400);
    for(int percent:new int[]{90,100,140,160})for(String mode:new String[]{"light","dark","auto"}){
     s.onActivity(a->{a.setFontPercent(percent);a.setThemeMode(mode);});
     AppIntegrationTest.waitForJs(s,"document.documentElement.dataset.nativeFontPercent==='"+percent+"'");
     assertEquals("true",js(s,"(()=>{fitNativeInvoiceColumns();const em=parseFloat(getComputedStyle(document.body).fontSize);return Array.from(document.querySelectorAll('#itemsBody tr')).every(r=>r.getBoundingClientRect().height<14*em)&&document.querySelectorAll('thead th').length===17&&document.querySelector('th[data-column-key=\"16\"]').getClientRects().length>0&&document.documentElement.scrollWidth<=innerWidth+1;})()"));
     if((percent==100||percent==160)&&!mode.equals("auto")){
      js(s,"document.querySelector('th[data-column-key=\"16\"]').scrollIntoView({block:'center',inline:'center',behavior:'instant'});true");Thread.sleep(250);
      AppearanceTest.screenshot("compact-ipi-"+percent+"-"+mode+"-"+(wide?"landscape":"portrait"),s);
     }
    }
   }
  }
 }
 @Test public void separateEditorConfirmsBoxIpiAndPriceAndRestoresScroll()throws Exception{
  try(ActivityScenario<MainActivity>s=ActivityScenario.launch(MainActivity.class)){
   load(s);assertEquals("true",js(s,"document.querySelectorAll('td[data-column-key=\"16\"]')[1].textContent.includes('6,00 / CX (comercial)')"));
   js(s,"document.querySelector('.table-wrap').scrollLeft=120;openProductCost(1);true");
   js(s,"let e=document.querySelector('[data-cost-field=\"factor\"]');e.value='12';e.dispatchEvent(new Event('input',{bubbles:true}));document.querySelector('[data-cost-field=\"confirmed\"]').click();true");
   assertEquals("true",js(s,"document.querySelectorAll('td[data-column-key=\"16\"]')[1].textContent.includes('0,50 / UN') && document.querySelector('[data-unit-price=\"1\"]').textContent.includes('6,60') && buildFiscalCSV().rows[1].at(-2)==='0.50'"));
   js(s,"document.querySelector('[data-close-cost]').click();true");assertEquals("true",js(s,"!document.querySelector('dialog').open && document.querySelector('.table-wrap').scrollLeft===120"));
   s.recreate();AppIntegrationTest.waitForJs(s,"window.__compactInvoice && currentFinance?.items.length===15");assertEquals("12",js(s,"currentFinance.items[1].conversion.factor"));
  }
 }
}
