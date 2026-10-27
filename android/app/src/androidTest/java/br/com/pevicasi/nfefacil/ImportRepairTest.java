package br.com.pevicasi.nfefacil;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.*;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.webkit.WebView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.*;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class ImportRepairTest {
    private final Instrumentation instrumentation=InstrumentationRegistry.getInstrumentation();
    private final UiDevice device=UiDevice.getInstance(instrumentation);
    @org.junit.After public void cleanup()throws Exception {
        device.setOrientationNatural();device.unfreezeRotation();
        instrumentation.getTargetContext().getSharedPreferences("appearance",0).edit().clear().commit();
    }
    private void ready(ActivityScenario<MainActivity> scenario)throws Exception {waitJs(scenario,"typeof acceptNativeXmlDocuments === 'function' && !!document.documentElement.dataset.nativeTheme");}
    private void reset(ActivityScenario<MainActivity> scenario)throws Exception {
        ready(scenario);js(scenario,"localStorage.clear();startNewConsultation();loadedXmls=[];scannedCodes=[];renderScannedCodes();markupPercentInput.value='20';document.querySelectorAll('.column-checkbox').forEach(e=>{if(!e.checked)e.click()});true");
    }
    @Test public void realSafPicksTwoThreeAndSevenDocumentsAndKeepsNotesAfterReopen()throws Exception {
        Instrumentation.ActivityMonitor observer=new Instrumentation.ActivityMonitor(){
            @Override public Instrumentation.ActivityResult onStartActivity(Intent intent){
                if(Intent.ACTION_OPEN_DOCUMENT.equals(intent.getAction())){
                    assertTrue(intent.getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE,false));
                    assertTrue(intent.hasCategory(Intent.CATEGORY_OPENABLE));
                }
                return null;
            }
        };
        instrumentation.addMonitor(observer);
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)) {
            reset(scenario);
            select(scenario,"nota1.xml","nota2.xml");waitJs(scenario,"document.querySelectorAll('#notesTabs button').length === 2");AppearanceTest.screenshot("saf-imported-two-notes",scenario);
            assertEquals("true",js(scenario,"loadedNotes.map(e=>e.data.nfeNumber).sort().join(',') === '1001,1002'"));
            js(scenario,"showNote(loadedNotes.findIndex(e=>e.data.nfeNumber==='1002'));true");assertEquals("\"1002\"",js(scenario,"document.getElementById('nfeNumber').textContent"));
            select(scenario,"nota3.xml","nota4.xml","nota5.xml");waitJs(scenario,"document.querySelectorAll('#notesTabs button').length === 5");
            assertEquals("\"1002\"",js(scenario,"document.getElementById('nfeNumber').textContent"));
            select(scenario,"empty.xml","invalid.xml","nota1.xml","nota2.xml","nota3.xml","nota4.xml","nota5.xml");
            waitJs(scenario,"document.getElementById('status').textContent.includes('2 arquivo(s) não importado(s)')");
            assertEquals("true",js(scenario,"loadedNotes.length === 5 && document.getElementById('status').textContent.includes('5 repetida(s)') && document.getElementById('status').getBoundingClientRect().height>0"));
            for(int i=0;i<5;i++)assertEquals("true",js(scenario,"showNote(loadedNotes.findIndex(e=>e.data.nfeNumber==='"+(1001+i)+"'));document.getElementById('nfeNumber').textContent === '"+(1001+i)+"' && currentItems[0].description === 'Produto Android "+(i+1)+"' && loadedNotes[activeNoteIndex].data.totalDiscount === 5.5"));
            assertEquals("\"20\"",js(scenario,"document.getElementById('markupPercent').value"));
            scenario.recreate();ready(scenario);assertEquals("true",js(scenario,"loadedNotes.length === 5 && document.querySelectorAll('#notesTabs button').length === 5"));
            select(scenario,"nota1.xml","nota2.xml");waitJs(scenario,"document.getElementById('status').textContent.includes('2 repetida(s)')");
            assertEquals("true",js(scenario,"loadedNotes.length === 5 && loadedXmls.length === 5"));
            js(scenario,"startNewConsultation();true");
            select(scenario,"nota1.xml","nota2.xml","nota3.xml","nota4.xml","nota5.xml");
            waitJs(scenario,"loadedNotes.length === 5 && document.getElementById('status').textContent.includes('5 nota(s) adicionada(s)')");
            scrollWeb(scenario,"#notesTabs");AppearanceTest.screenshot("saf-imported-five-notes",scenario);
            scenario.onActivity(a->{WebView web=web(a.getWindow().getDecorView());assertFalse(web.getSettings().getAllowContentAccess());assertFalse(web.getSettings().getAllowFileAccess());});
        }finally{instrumentation.removeMonitor(observer);}
    }
    @Test public void callbackHandlesClipDataCancellationMissingAndDeniedUrisWithoutLosingNotes()throws Exception {
        Uri first=Uri.parse("content://fixture/1"),second=Uri.parse("content://fixture/2");
        Intent combined=new Intent().setData(first);ClipData clips=ClipData.newRawUri("XML",first);clips.addItem(new ClipData.Item(second));combined.setClipData(clips);
        assertArrayEquals(new Uri[]{first,second},MainActivity.selectedDocuments(Activity.RESULT_OK,combined));
        assertEquals(0,MainActivity.selectedDocuments(Activity.RESULT_CANCELED,combined).length);
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            reset(scenario);js(scenario,"acceptNativeXmlDocuments([{name:'antes.xml',text:"+JSONObject.quote(SafDocumentsProvider.xml(1))+"}]);true");
            IntentFilter documents=new IntentFilter(Intent.ACTION_OPEN_DOCUMENT);documents.addDataType("*/*");documents.addCategory(Intent.CATEGORY_OPENABLE);
            for(Instrumentation.ActivityResult result:new Instrumentation.ActivityResult[]{
                new Instrumentation.ActivityResult(Activity.RESULT_CANCELED,null),
                new Instrumentation.ActivityResult(Activity.RESULT_OK,new Intent()),
                new Instrumentation.ActivityResult(Activity.RESULT_OK,new Intent().setData(Uri.parse("content://com.android.contacts/contacts"))),
                new Instrumentation.ActivityResult(Activity.RESULT_OK,new Intent().setData(Uri.parse("file:///etc/hosts")))}){
                js(scenario,"clearStatus();true");
                Instrumentation.ActivityMonitor monitor=instrumentation.addMonitor(documents,result,true);
                try{clickWeb(scenario,"true".equals(js(scenario,"document.getElementById('nativeImportMore').getClientRects().length>0"))?"#nativeImportMore":"#loadXmlsBtn");long until=System.currentTimeMillis()+10000;while(monitor.getHits()==0 && System.currentTimeMillis()<until)Thread.sleep(50);if(monitor.getHits()==0)saveUiEvidence("callback-chooser-not-opened");assertTrue("WebView must launch ACTION_OPEN_DOCUMENT",instrumentation.checkMonitorHit(monitor,1));instrumentation.waitForIdleSync();}
                finally{instrumentation.removeMonitor(monitor);}
                if(result.getResultCode()==Activity.RESULT_OK){
                    String predicate=result.getResultData().getData()==null?"document.getElementById('status').textContent.includes('provedor não retornou')":"document.getElementById('status').textContent.includes('1 arquivo(s) não importado(s)')";
                    waitJs(scenario,predicate);
                }
                assertEquals("true",js(scenario,"loadedNotes.length === 1 && document.getElementById('nfeNumber').textContent === '1001'"));
            }
        }
    }
    @Test public void androidWebViewLayoutsAndOfficialExternalButtonAtAllReportedScales()throws Exception {
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            reset(scenario);js(scenario,"addScannedCode('35261012345678000195550010000001231123456784');acceptNativeXmlDocuments([{name:'layout.xml',text:"+JSONObject.quote(SafDocumentsProvider.xml(1))+"}]);true");
            js(scenario,"document.querySelector('.column-checkbox[data-column-key=\"14\"]').click();document.querySelector('.column-checkbox[data-column-key=\"15\"]').click();true");
            for(boolean landscape:new boolean[]{false,true}){
                if(landscape)device.setOrientationLeft();else device.setOrientationNatural();Thread.sleep(500);
                for(int percent:new int[]{90,100,140,160})for(boolean dark:new boolean[]{false,true}){
                    scenario.onActivity(a->{a.setFontPercent(percent);a.setThemeMode(dark?"dark":"light");});
                    waitJs(scenario,"document.documentElement.dataset.nativeFontPercent === '"+percent+"' && document.documentElement.dataset.nativeTheme === '"+(dark?"dark":"light")+"'");
                    js(scenario,"acceptNativeXmlDocuments([{name:'layout.xml',text:"+JSONObject.quote(SafDocumentsProvider.xml(1))+"}]);true");
                    assertEquals("true",js(scenario,"['0','1','7'].every(k=>{const e=document.querySelector('th[data-column-key=\"'+k+'\"] .header-label'),r=e.getBoundingClientRect(),p=e.closest('th').getBoundingClientRect();return getComputedStyle(e).whiteSpace==='nowrap' && r.height<=parseFloat(getComputedStyle(e).lineHeight)+2 && r.right<=p.right+1;})"));
                    assertEquals("true",js(scenario,"Array.from(document.querySelectorAll('#draggableHeaderRow th')).filter(e=>e.getClientRects().length).length===14 && document.querySelector('.table-wrap').scrollWidth>document.querySelector('.table-wrap').clientWidth && document.getElementById('markupPercent').value==='20' && loadedNotes[0].data.totalDiscount===5.5"));
                    if(percent==160){
                        scrollWeb(scenario,"th[data-column-key=\"7\"] .header-label");
                        AppearanceTest.screenshot("repairs-table-"+(dark?"dark":"light")+"-160-"+(landscape?"landscape":"portrait"),scenario);
                    }
                    js(scenario,"startNewConsultation();true");
                    assertEquals("true",js(scenario,"(()=>{const e=document.querySelector('.scan-item-actions .btn-link'),s=getComputedStyle(e);return e.getBoundingClientRect().height>=48 && s.backgroundColor==='rgb(36, 71, 157)' && s.color==='rgb(255, 255, 255)' && s.textDecorationLine==='none' && e.scrollWidth<=e.clientWidth+1;})()"));
                    if(percent==160){
                        scrollWeb(scenario,".scan-item-actions .btn-link");
                        AppearanceTest.screenshot("repairs-link-"+(dark?"dark":"light")+"-160-"+(landscape?"landscape":"portrait"),scenario);
                    }
                }
            }
            AtomicReference<Intent> external=new AtomicReference<>();
            Instrumentation.ActivityMonitor monitor=new Instrumentation.ActivityMonitor(){
                @Override public Instrumentation.ActivityResult onStartActivity(Intent intent){if(Intent.ACTION_VIEW.equals(intent.getAction())){external.set(intent);return new Instrumentation.ActivityResult(Activity.RESULT_CANCELED,null);}return null;}
            };
            instrumentation.addMonitor(monitor);
            try{clickWeb(scenario,".scan-item-actions .btn-link");long end=System.currentTimeMillis()+10000;while(external.get()==null && System.currentTimeMillis()<end)Thread.sleep(100);assertNotNull(external.get());assertEquals("https",external.get().getData().getScheme());assertEquals("www.nfe.fazenda.gov.br",external.get().getData().getHost());}
            finally{instrumentation.removeMonitor(monitor);}
            assertEquals("true",js(scenario,"location.origin === 'https://appassets.androidplatform.net'"));
        }
    }
    private void select(ActivityScenario<MainActivity> scenario,String...names)throws Exception {
        clickWeb(scenario,"true".equals(js(scenario,"document.getElementById('nativeImportMore').getClientRects().length>0"))?"#nativeImportMore":"#loadXmlsBtn");
        long deadline=System.currentTimeMillis()+15000;
        boolean openedRoots=false;
        while(device.findObject(By.text(names[0]))==null && System.currentTimeMillis()<deadline){
            UiObject2 root=device.findObject(By.text("NF-e XML fixture"));
            try {
                if(root!=null)root.click();
                else if(!openedRoots){
                    UiObject2 roots=device.findObject(By.desc(Pattern.compile("(?i)(show roots|mostrar raízes|open navigation drawer)")));
                    if(roots!=null){roots.click();openedRoots=true;}
                }
            }catch(StaleObjectException refreshed){/* DocumentsUI refreshed roots; query fresh nodes. */}
            Thread.sleep(200);
        }
        saveUiEvidence("saf-documents-before-"+names.length);
        clickSaf(By.text(names[0]),true);
        for(int i=1;i<names.length;i++)clickSaf(By.text(names[i]),false);
        saveUiEvidence("saf-selected-"+names.length);
        BySelector confirm=By.res("com.google.android.documentsui","action_menu_select");
        if(device.wait(Until.findObject(confirm),1000)==null)confirm=By.text(Pattern.compile("(?i)(open|abrir|select|selecionar)"));
        clickSaf(confirm,false);
        assertTrue(device.wait(Until.hasObject(By.pkg("br.com.pevicasi.nfefacil")),10000));
    }
    private void clickSaf(BySelector selector,boolean hold)throws Exception {
        long end=System.currentTimeMillis()+10000;
        while(System.currentTimeMillis()<end){
            UiObject2 node=device.findObject(selector);
            if(node!=null)try{if(hold)node.longClick();else node.click();device.waitForIdle(1000);return;}
            catch(StaleObjectException refreshed){/* Retry with the current accessibility node. */}
            Thread.sleep(100);
        }
        saveUiEvidence("saf-missing-control");fail("SAF control not available: "+selector);
    }
    private void saveUiEvidence(String name)throws Exception {
        java.io.File directory=new java.io.File(instrumentation.getTargetContext().getExternalFilesDir(null),"appearance-evidence");assertTrue(directory.isDirectory()||directory.mkdirs());
        android.graphics.Bitmap shot=instrumentation.getUiAutomation().takeScreenshot();assertNotNull(shot);
        try(java.io.OutputStream out=new java.io.FileOutputStream(new java.io.File(directory,name+".png"))){assertTrue(shot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out));}finally{shot.recycle();}
        device.dumpWindowHierarchy(new java.io.File(directory,name+".xml"));
    }

    private void scrollWeb(ActivityScenario<MainActivity> scenario,String selector)throws Exception {
        js(scenario,"document.activeElement?.blur();true");
        scenario.onActivity(a->{android.view.inputmethod.InputMethodManager ime=(android.view.inputmethod.InputMethodManager)a.getSystemService(android.content.Context.INPUT_METHOD_SERVICE);ime.hideSoftInputFromWindow(web(a.getWindow().getDecorView()).getWindowToken(),0);});
        device.waitForIdle(1000);Thread.sleep(300);
        js(scenario,"window.scrollTo({top:scrollY,behavior:'instant'});document.querySelector("+JSONObject.quote(selector)+").scrollIntoView({behavior:'instant',block:'center'});true");
        waitJs(scenario,"(()=>{const r=document.querySelector("+JSONObject.quote(selector)+").getBoundingClientRect();return r.top>=0 && r.bottom<=innerHeight+1 && r.height>0;})()");
        scenario.onActivity(a->web(a.getWindow().getDecorView()).postInvalidateOnAnimation());
        device.waitForIdle(1000);Thread.sleep(500);
    }
    private void clickWeb(ActivityScenario<MainActivity> scenario,String selector)throws Exception {
        // Finish IME/window animations before mapping CSS coordinates to the screen.
        js(scenario,"document.activeElement?.blur();document.querySelector("+JSONObject.quote(selector)+").scrollIntoView({behavior:'instant',block:'center'});true");
        scenario.onActivity(a->{android.view.inputmethod.InputMethodManager ime=(android.view.inputmethod.InputMethodManager)a.getSystemService(android.content.Context.INPUT_METHOD_SERVICE);ime.hideSoftInputFromWindow(web(a.getWindow().getDecorView()).getWindowToken(),0);});
        device.waitForIdle(1000);Thread.sleep(300);
        CountDownLatch painted=new CountDownLatch(1);
        scenario.onActivity(a->web(a.getWindow().getDecorView()).postVisualStateCallback(System.nanoTime(),new WebView.VisualStateCallback(){@Override public void onComplete(long id){painted.countDown();}}));
        assertTrue(painted.await(10,TimeUnit.SECONDS));
        int[] bounds=new int[3];scenario.onActivity(a->{WebView w=web(a.getWindow().getDecorView());w.getLocationOnScreen(bounds);bounds[2]=w.getWidth();});
        double scale=bounds[2]/Double.parseDouble(js(scenario,"innerWidth"));
        String rect="document.querySelector("+JSONObject.quote(selector)+").getBoundingClientRect()";
        double x=Double.parseDouble(js(scenario,"(()=>{const r="+rect+";return r.x+r.width/2;})()"));
        double y=Double.parseDouble(js(scenario,"(()=>{const r="+rect+";return r.y+r.height/2;})()"));
        assertTrue(device.click(bounds[0]+(int)(x*scale),bounds[1]+(int)(y*scale)));
    }
    private static WebView web(android.view.View v){if(v instanceof WebView)return (WebView)v;if(v instanceof android.view.ViewGroup)for(int i=0;i<((android.view.ViewGroup)v).getChildCount();i++){WebView found=web(((android.view.ViewGroup)v).getChildAt(i));if(found!=null)return found;}return null;}
    private static String js(ActivityScenario<MainActivity> scenario,String code)throws Exception {
        CountDownLatch latch=new CountDownLatch(1);AtomicReference<String> result=new AtomicReference<>();scenario.onActivity(a->web(a.getWindow().getDecorView()).evaluateJavascript(code,value->{result.set(value);latch.countDown();}));assertTrue(latch.await(10,TimeUnit.SECONDS));return result.get();
    }
    private static void waitJs(ActivityScenario<MainActivity> scenario,String code)throws Exception {long end=System.currentTimeMillis()+20000;while(System.currentTimeMillis()<end){if("true".equals(js(scenario,code)))return;Thread.sleep(100);}fail("WebView condition: "+code);}
}
