package br.com.pevicasi.nfefacil;

import android.webkit.WebView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;
import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.*;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class FinancialTest {
    private final UiDevice device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
    @org.junit.After public void cleanup()throws Exception{
        device.setOrientationNatural();device.unfreezeRotation();
        InstrumentationRegistry.getInstrumentation().getTargetContext().getSharedPreferences("appearance",0).edit().clear().commit();
    }
    private static String fixture()throws Exception{
        try(InputStream in=InstrumentationRegistry.getInstrumentation().getContext().getAssets().open("acquisition.xml");ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] bytes=new byte[8192];int n;while((n=in.read(bytes))!=-1)out.write(bytes,0,n);return out.toString(StandardCharsets.UTF_8.name());
        }
    }
    private static String js(ActivityScenario<MainActivity> s,String code)throws Exception{return AppIntegrationTest.js(s,code);}
    private static void waitJs(ActivityScenario<MainActivity> s,String code)throws Exception{AppIntegrationTest.waitForJs(s,code);}
    private static void load(ActivityScenario<MainActivity> s)throws Exception{
        waitJs(s,"window.__compactInvoice && typeof window.acceptNativeXmlDocuments === 'function' && typeof NfeCost === 'object'");
        js(s,"localStorage.clear();costFactors={};startNewConsultation();loadedXmls=[];markupPercentInput.value='20';document.querySelectorAll('.column-checkbox').forEach(e=>{if(!e.checked)e.click()});acceptNativeXmlDocuments([{name:'acquisition.xml',text:"+JSONObject.quote(fixture())+"}]);true");
        waitJs(s,"currentFinance && loadedNotes.length === 1 && currentItems.length === 5");
    }
    private static void number(ActivityScenario<MainActivity> s,String code,double expected)throws Exception{assertEquals(code,expected,Double.parseDouble(js(s,code)),.001);}
    @Test public void completeCostsAndSellableUnitsAreCalculatedInRealWebViewAndPersist()throws Exception{
        try(ActivityScenario<MainActivity> s=ActivityScenario.launch(MainActivity.class)){
            load(s);number(s,"currentFinance.classifiedCents",42700);number(s,"currentFinance.totals.ipi",1700);number(s,"currentFinance.items[0].costCents",5750);number(s,"NfeCost.price(currentFinance.items[0],20)",6900);
            assertEquals("true",js(s,"currentFinance.items[1].ready"));
            number(s,"currentFinance.items[1].conversion.units",24);number(s,"currentFinance.items[1].costCents",550);number(s,"NfeCost.price(currentFinance.items[1],20)",660);
            number(s,"NfeCost.price(currentFinance.items[2],20)",7200);number(s,"NfeCost.price(currentFinance.items[3],20)",3600);assertEquals("\"KG\"",js(s,"currentFinance.items[4].conversion.base"));number(s,"NfeCost.price(currentFinance.items[4],20)",2400);
            js(s,"markupPercentInput.value='0';markupPercentInput.dispatchEvent(new Event('input',{bubbles:true}));true");number(s,"NfeCost.price(currentFinance.items[1],getMarkupPercent())",550);
            js(s,"markupPercentInput.value='12.5';markupPercentInput.dispatchEvent(new Event('input',{bubbles:true}));true");number(s,"NfeCost.price(currentFinance.items[1],getMarkupPercent())",619);
            s.recreate();waitJs(s,"window.__compactInvoice && typeof acceptNativeXmlDocuments === 'function' && currentFinance?.items.length === 5");number(s,"currentFinance.items[1].conversion.factor",12);number(s,"NfeCost.price(currentFinance.items[1],getMarkupPercent())",619);
            assertEquals("true",js(s,"document.querySelectorAll('#itemsBody input,#itemsBody button').length===0"));
        }
    }
    @Test public void invoiceDivergenceAndExportsUseTheSameFinancialMethod()throws Exception{
        try(ActivityScenario<MainActivity> s=ActivityScenario.launch(MainActivity.class)){
            load(s);
            assertEquals("\"6.60\"",js(s,"buildFiscalCSV().rows[1][5]"));assertEquals("\"12.00\"",js(s,"buildFiscalCSV().rows[1][22]"));
            assertEquals("true",js(s,"buildFiscalCSV().headers.includes('IPI item') && document.querySelector('[data-unit-price=\"1\"]').textContent.includes('6,60')"));
            String other=fixture().replace("<nNF>900</nNF>","<nNF>901</nNF>").replace("<vNF>427</vNF>","<vNF>429.34</vNF>");
            js(s,"acceptNativeXmlDocuments([{name:'divergente.xml',text:"+JSONObject.quote(other)+"}]);showNote(1);true");
            number(s,"currentFinance.differenceCents",234);number(s,"currentFinance.totals.ipi",1700);assertEquals("false",js(s,"currentFinance.items[0].ready"));assertEquals("\"\"",js(s,"buildFiscalCSV().rows[0][5]"));
            js(s,"showNote(0);true");number(s,"currentFinance.differenceCents",0);number(s,"NfeCost.price(currentFinance.items[1],20)",660);
        }
    }
    @Test public void compactColumnsAreMeasuredAndCapturedAtAllScalesAndThemes()throws Exception{
        org.json.JSONArray metrics=new org.json.JSONArray();
        try(ActivityScenario<MainActivity> s=ActivityScenario.launch(MainActivity.class)){
            load(s);
            for(boolean landscape:new boolean[]{false,true}){
                if(landscape)device.setOrientationLeft();else device.setOrientationNatural();Thread.sleep(400);
                for(int percent:new int[]{90,100,140,160})for(boolean dark:new boolean[]{false,true}){
                    s.onActivity(a->{a.setFontPercent(percent);a.setThemeMode(dark?"dark":"light");});
                    waitJs(s,"document.documentElement.dataset.nativeFontPercent === '"+percent+"' && document.documentElement.dataset.nativeTheme === '"+(dark?"dark":"light")+"'");
                    for(boolean fourteen:new boolean[]{false,true}){
                        js(s,"['14','15'].forEach(k=>{const e=document.querySelector('.column-checkbox[data-column-key=\"'+k+'\"]');if(e.checked==="+fourteen+")e.click()});fitNativeInvoiceColumns();true");
                        assertEquals("Product descriptions must fit their cells", "true",js(s,"Array.from(document.querySelectorAll('.product-name')).every(e=>e.scrollWidth<=e.clientWidth+1 && e.getBoundingClientRect().right<=e.closest('td').getBoundingClientRect().right+1)"));
                        String raw=js(s,"JSON.stringify((()=>{fitNativeInvoiceColumns();const q=k=>document.querySelector('th[data-column-key=\"'+k+'\"]'),r=k=>q(k).getBoundingClientRect().width;return {item:r('0'),quantity:r('7'),product:r('1'),viewport:document.querySelector('.table-wrap').clientWidth,count:Array.from(document.querySelectorAll('thead th')).filter(e=>e.getClientRects().length).length,labelHeight:q('7').querySelector('.header-label').getBoundingClientRect().height,line:parseFloat(getComputedStyle(q('7')).lineHeight)};})())");
                        JSONObject m=new JSONObject((String)new JSONTokener(raw).nextValue());m.put("font",percent);m.put("dark",dark);m.put("landscape",landscape);metrics.put(m);
                        if(percent<=100)assertTrue(m.toString(),m.getDouble("item")+m.getDouble("quantity")+m.getDouble("product")<=m.getDouble("viewport")+2);
                        assertTrue(m.toString(),m.getDouble("product")>m.getDouble("quantity")*1.2);assertTrue(m.toString(),m.getDouble("product")>m.getDouble("item")*2.5);assertEquals(fourteen?15:17,m.getInt("count"));assertTrue(m.toString(),m.getDouble("labelHeight")<=m.getDouble("line")+2);
                    }
                    if(percent==100||percent==160){
                        js(s,"document.activeElement?.blur();document.querySelector('th[data-column-key=\"1\"]').scrollIntoView({behavior:'instant',block:'center'});true");Thread.sleep(500);
                        AppearanceTest.screenshot("financial-columns-"+percent+"-"+(dark?"dark":"light")+"-"+(landscape?"landscape":"portrait"),s);
                    }
                }
            }
            js(s,"moveColumn('1','0');fitNativeInvoiceColumns();true");assertEquals("\"1\"",js(s,"getHeaderCells()[0].dataset.columnKey"));
        }finally{
            File directory=new File(InstrumentationRegistry.getInstrumentation().getTargetContext().getExternalFilesDir(null),"appearance-evidence");assertTrue(directory.isDirectory()||directory.mkdirs());
            try(Writer out=new OutputStreamWriter(new FileOutputStream(new File(directory,"financial-layout-metrics.json")),StandardCharsets.UTF_8)){out.write(metrics.toString(2));}
        }
    }
}
