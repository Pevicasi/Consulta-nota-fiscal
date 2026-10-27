package br.com.pevicasi.nfefacil;

import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.os.ParcelFileDescriptor;
import android.view.*;
import android.webkit.WebView;
import android.widget.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.*;
import org.junit.runner.RunWith;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class AppearanceTest {
    @Rule public androidx.test.rule.GrantPermissionRule cameraPermission = androidx.test.rule.GrantPermissionRule.grant(android.Manifest.permission.CAMERA);
    @Before public void reset() { InstrumentationRegistry.getInstrumentation().getTargetContext().getSharedPreferences("appearance",0).edit().clear().commit(); }
    @After public void cleanup() { reset(); }
    private static final String XML="<NFe><infNFe><ide><nNF>902</nNF></ide><det nItem='1'><prod><xProd>Produto para acessibilidade e leitura fiscal</xProd><qCom>2</qCom><vUnCom>10</vUnCom><vProd>20</vProd></prod><imposto><ICMS><ICMS10><vICMSST>4</vICMSST></ICMS10></ICMS></imposto></det><total><ICMSTot><vProd>20</vProd><vDesc>5.50</vDesc><vNF>18.50</vNF></ICMSTot></total></infNFe></NFe>";

    @Test public void fontControlsClampResetAndPersistAcrossActivityAndNewLaunch() throws Exception {
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)) {
            ready(scenario);
            scenario.onActivity(a->{
                Button plus=(Button)findDescription(a.getWindow().getDecorView(),"Aumentar fontes");
                for(int i=0;i<10;i++)plus.performClick();
                assertEquals(160,a.fontPercent());assertFalse(plus.isEnabled());
            });
            waitJs(scenario,"document.documentElement.dataset.nativeFontPercent === '160'");
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(a->assertLabelsFit(a.findViewById(android.R.id.content)));
            screenshot("controls-160");
            scenario.recreate();ready(scenario);scenario.onActivity(a->assertEquals(160,a.fontPercent()));
            scenario.onActivity(a->{
                Button minus=(Button)findDescription(a.getWindow().getDecorView(),"Diminuir fontes");
                for(int i=0;i<10;i++)minus.performClick();assertEquals(90,a.fontPercent());assertFalse(minus.isEnabled());
                findDescription(a.getWindow().getDecorView(),"Restaurar fontes para 100%").performClick();assertEquals(100,a.fontPercent());
                a.setFontPercent(120);
                a.getWindow().getDecorView().findViewWithTag("appearance-theme").performClick();
            });
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            android.view.accessibility.AccessibilityNodeInfo root=InstrumentationRegistry.getInstrumentation().getUiAutomation().getRootInActiveWindow();
            assertNotNull(root);boolean clicked=false;
            for(android.view.accessibility.AccessibilityNodeInfo node:root.findAccessibilityNodeInfosByText("Escuro"))
                if("Escuro".contentEquals(node.getText()))clicked|=node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK);
            assertTrue("Theme choice must be accessible and clickable",clicked);
            waitJs(scenario,"document.documentElement.dataset.nativeTheme === 'dark'");
        }
        try(ActivityScenario<MainActivity> next=ActivityScenario.launch(MainActivity.class)) {
            ready(next);waitJs(next,"document.documentElement.dataset.nativeFontPercent === '120' && document.documentElement.dataset.nativeTheme === 'dark'");
            next.onActivity(a->{assertEquals(120,a.fontPercent());assertEquals("dark",a.themeMode());});
        }
    }
    @Test public void fontAndThemeTogetherKeepInvoiceDiscountPricesAndScrollableTable() throws Exception {
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)) {
            ready(scenario);js(scenario,"loadMultipleXmls([new File(["+org.json.JSONObject.quote(XML)+"],'appearance.xml',{type:'text/xml'})]);true");
            waitJs(scenario,"document.getElementById('nfeNumber').textContent === '902'");
            for(int percent:new int[]{90,120,160})for(String mode:new String[]{"light","dark"}) {
                scenario.onActivity(a->{a.setFontPercent(percent);a.setThemeMode(mode);});
                waitJs(scenario,"document.documentElement.dataset.nativeFontPercent === '"+percent+"' && document.documentElement.dataset.nativeTheme === '"+mode+"'");
                assertEquals("true",js(scenario,"document.documentElement.scrollWidth <= innerWidth+1"));
                assertEquals("true",js(scenario,"document.querySelector('.table-wrap').scrollWidth > document.querySelector('.table-wrap').clientWidth"));
                assertEquals("true",js(scenario,"document.getElementById('discountTotalBox').getBoundingClientRect().height > 0 && document.getElementById('totalDiscount').textContent.includes('5,50')"));
                assertEquals("true",js(scenario,"document.getElementById('nfeNumber').textContent === '902' && document.getElementById('markupPercent').value === '20'"));
                assertEquals("true",js(scenario,"getComputedStyle(document.getElementById('totalDiscount')).color === '"+(mode.equals("dark")?"rgb(141, 240, 187)":"rgb(2, 122, 72)")+"'"));
                screenshot("invoice-"+mode+"-"+percent);
            }
            for(int version:new int[]{15,16,17}) {
                scenario.onActivity(a->web(a.getWindow().getDecorView()).loadUrl(MainActivity.ORIGIN+"/assets/site/consulta_precos_v"+version+".html"));
                waitJs(scenario,"location.pathname.endsWith('/consulta_precos_v"+version+".html') && document.readyState === 'complete' && typeof consultar === 'function' && document.documentElement.dataset.nativeTheme === 'dark'");
                js(scenario,"document.getElementById('q').value='arroz';true");
                scenario.onActivity(a->{a.setFontPercent(90);a.setThemeMode("light");});
                waitJs(scenario,"document.documentElement.dataset.nativeFontPercent === '90' && document.documentElement.dataset.nativeTheme === 'light'");
                assertEquals("true",js(scenario,"document.getElementById('q').value === 'arroz'"));
                scenario.onActivity(a->{a.setFontPercent(160);a.setThemeMode("dark");});
                waitJs(scenario,"document.documentElement.dataset.nativeFontPercent === '160' && document.documentElement.dataset.nativeTheme === 'dark'");
                assertEquals("true",js(scenario,"document.documentElement.scrollWidth <= innerWidth+1"));
                screenshot("prices-"+version+"-dark-160");
            }
        }
    }
    @Test public void scannerAndFiscalConfirmationUsePersistedAppearanceWithoutLosingKey() throws Exception {
        InstrumentationRegistry.getInstrumentation().getTargetContext().getSharedPreferences("appearance",0).edit().putInt("fontPercent",160).putString("themeMode","dark").commit();
        try(ActivityScenario<ScannerActivity> scenario=ActivityScenario.launch(ScannerActivity.class)) {
            String key="35261012345678000195550010000001231123456784";
            assertTrue("Fiscal fixture must have a valid structure and DV",NfeKey.isValid(key));
            scenario.onActivity(a->{assertEquals(160,a.fontPercent());assertTrue(a.isDark());a.offerKeys(Collections.singleton(key));});
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(a->{
                AlertDialog dialog=confirmation(a);assertTrue(dialog.isShowing());
                TextView digits=selectable(dialog.getWindow().getDecorView());assertEquals(key,digits.getText().toString());
                assertTrue(digits.getTextSize()>=30* a.getResources().getDisplayMetrics().scaledDensity);
                a.setThemeMode("light");assertSame(dialog,confirmation(a));assertEquals(key,digits.getText().toString());
                assertEquals(android.graphics.Color.parseColor("#172033"),digits.getCurrentTextColor());
                a.setFontPercent(90);
            });
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();screenshot("scanner-light-90");
            scenario.onActivity(a->{a.setFontPercent(160);a.setThemeMode("dark");});
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();screenshot("scanner-dark-160");
            scenario.onActivity(a->{
                TextView digits=selectable(confirmation(a).getWindow().getDecorView());assertNotNull(digits.getLayout());
                for(int line=0;line<digits.getLineCount();line++)assertEquals(0,digits.getLayout().getEllipsisCount(line));
                confirmation(a).getButton(AlertDialog.BUTTON_NEGATIVE).performClick();assertFalse(a.isFinishing());
                a.offerKeys(new LinkedHashSet<>(Arrays.asList(key,MultiBarcodeTest.key("65",902))));
                Spinner choices=spinner(confirmation(a).getWindow().getDecorView());assertNotNull(choices);
                TextView option=(TextView)choices.getAdapter().getDropDownView(1,null,choices);
                float largeSize=option.getTextSize();
                a.setFontPercent(90);a.setThemeMode("light");
                assertEquals(largeSize*90f/160f,option.getTextSize(),.5f);
                assertEquals(android.graphics.Color.parseColor("#172033"),option.getCurrentTextColor());
                a.setFontPercent(160);a.setThemeMode("dark");choices.performClick();
            });
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();screenshot("ambiguous-menu-dark-160");
        }
    }
    @Test public void automaticThemeFollowsAndroidLiveAndExplicitLightOverridesIt() throws Exception {
        android.app.UiModeManager manager=(android.app.UiModeManager)InstrumentationRegistry.getInstrumentation().getTargetContext().getSystemService(android.content.Context.UI_MODE_SERVICE);
        int original=manager.getNightMode();
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)) {
            ready(scenario);scenario.onActivity(a->{a.setThemeMode("auto");a.setFontPercent(160);});
            shell("cmd uimode night yes");waitJs(scenario,"document.documentElement.dataset.nativeTheme === 'dark'");
            scenario.onActivity(a->assertTrue(a.isDark()));
            shell("cmd uimode night no");waitJs(scenario,"document.documentElement.dataset.nativeTheme === 'light'");
            shell("cmd uimode night yes");waitJs(scenario,"document.documentElement.dataset.nativeTheme === 'dark'");
            scenario.onActivity(a->a.setThemeMode("light"));waitJs(scenario,"document.documentElement.dataset.nativeTheme === 'light'");
            scenario.recreate();ready(scenario);scenario.onActivity(a->{assertFalse(a.isDark());assertEquals(160,a.fontPercent());});
        } finally {shell("cmd uimode night "+(original==android.app.UiModeManager.MODE_NIGHT_YES?"yes":original==android.app.UiModeManager.MODE_NIGHT_NO?"no":"auto"));}
    }
    private static void shell(String command)throws Exception {
        try(ParcelFileDescriptor descriptor=InstrumentationRegistry.getInstrumentation().getUiAutomation().executeShellCommand(command);
            InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(descriptor)) {while(in.read()!=-1){} }
    }
    private static void screenshot(String name)throws Exception {
        Bitmap bitmap=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();assertNotNull(bitmap);
        File dir=InstrumentationRegistry.getInstrumentation().getTargetContext().getExternalFilesDir("appearance-evidence");assertNotNull(dir);assertTrue(dir.isDirectory()||dir.mkdirs());
        try(OutputStream out=new FileOutputStream(new File(dir,name+".png"))){assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,out));}finally{bitmap.recycle();}
    }
    private static AlertDialog confirmation(ScannerActivity a) {
        try{java.lang.reflect.Field field=ScannerActivity.class.getDeclaredField("confirmation");field.setAccessible(true);return (AlertDialog)field.get(a);}catch(Exception e){throw new AssertionError(e);}
    }
    private static TextView selectable(View view){
        if(view instanceof TextView && ((TextView)view).isTextSelectable())return (TextView)view;
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){TextView found=selectable(((ViewGroup)view).getChildAt(i));if(found!=null)return found;}return null;
    }
    private static Spinner spinner(View view){
        if(view instanceof Spinner)return (Spinner)view;
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){Spinner found=spinner(((ViewGroup)view).getChildAt(i));if(found!=null)return found;}return null;
    }
    private static View findDescription(View view,String description){
        if(description.contentEquals(view.getContentDescription()==null?"":view.getContentDescription()))return view;
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){View found=findDescription(((ViewGroup)view).getChildAt(i),description);if(found!=null)return found;}return null;
    }
    private static void assertLabelsFit(View view){
        if(view instanceof WebView || view.getVisibility()!=View.VISIBLE)return;
        if(view instanceof TextView && view.getWidth()>0 && view.getHeight()>0){
            TextView label=(TextView)view;
            if(label.getLayout()!=null){
                assertTrue("Native text clipped vertically: "+label.getText(),label.getLayout().getHeight()+label.getCompoundPaddingTop()+label.getCompoundPaddingBottom()<=label.getHeight()+2);
                for(int line=0;line<label.getLineCount();line++)assertEquals("Ellipsized native label: "+label.getText(),0,label.getLayout().getEllipsisCount(line));
            }
        }
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)assertLabelsFit(((ViewGroup)view).getChildAt(i));
    }
    private static WebView web(View view){
        if(view instanceof WebView)return (WebView)view;
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){WebView found=web(((ViewGroup)view).getChildAt(i));if(found!=null)return found;}return null;
    }
    private static String js(ActivityScenario<MainActivity> scenario,String expression)throws Exception{
        CountDownLatch latch=new CountDownLatch(1);AtomicReference<String> value=new AtomicReference<>();
        scenario.onActivity(a->web(a.getWindow().getDecorView()).evaluateJavascript(expression,result->{value.set(result);latch.countDown();}));
        assertTrue(latch.await(10,TimeUnit.SECONDS));return value.get();
    }
    private static void ready(ActivityScenario<MainActivity> scenario)throws Exception{waitJs(scenario,"typeof window.acceptNativeKey === 'function' && !!document.documentElement.dataset.nativeTheme");}
    private static void waitJs(ActivityScenario<MainActivity> scenario,String expression)throws Exception{
        long end=System.currentTimeMillis()+20000;while(System.currentTimeMillis()<end){if("true".equals(js(scenario,expression)))return;Thread.sleep(150);}fail("WebView not ready: "+expression);
    }
}
