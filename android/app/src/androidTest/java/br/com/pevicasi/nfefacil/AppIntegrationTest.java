package br.com.pevicasi.nfefacil;

import android.graphics.*;
import android.webkit.WebView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.barcode.*;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.*;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class AppIntegrationTest {
    @org.junit.Rule public androidx.test.rule.GrantPermissionRule cameraPermission = androidx.test.rule.GrantPermissionRule.grant(android.Manifest.permission.CAMERA);
    private static String key() {
        String base="3526101234567800019555001000000123112345678";
        int sum=0,w=2;for(int i=42;i>=0;i--){sum+=(base.charAt(i)-'0')*w;w=w==9?2:w+1;}
        return base+(sum%11<2?0:11-sum%11);
    }
    @Test public void bundledBarcodeModelReadsCode128WithoutDownload() throws Exception {
        BitMatrix bits = new MultiFormatWriter().encode(key(), BarcodeFormat.CODE_128, 1800, 400);
        Bitmap bitmap = Bitmap.createBitmap(1800, 400, Bitmap.Config.ARGB_8888);
        for(int y=0;y<400;y++) for(int x=0;x<1800;x++) bitmap.setPixel(x,y,bits.get(x,y)?Color.BLACK:Color.WHITE);
        BarcodeScanner scanner = BarcodeScanning.getClient(new BarcodeScannerOptions.Builder()
            .setBarcodeFormats(com.google.mlkit.vision.barcode.common.Barcode.FORMAT_CODE_128).build());
        try {
            java.util.List<com.google.mlkit.vision.barcode.common.Barcode> results = Tasks.await(scanner.process(InputImage.fromBitmap(bitmap,0)),30,TimeUnit.SECONDS);
            assertFalse(results.isEmpty()); assertEquals(key(),NfeKey.barcode(results.get(0).getRawValue()));
        } finally {scanner.close();bitmap.recycle();}
    }
    @Test public void bundledOcrReadsPrinted44DigitKey() throws Exception {
        Bitmap bitmap=Bitmap.createBitmap(2200,350,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(bitmap);canvas.drawColor(Color.WHITE);
        Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);paint.setColor(Color.BLACK);paint.setTypeface(Typeface.MONOSPACE);paint.setTextSize(64);
        canvas.drawText(key().replaceAll("(.{4})", "$1 "),30,180,paint);
        TextRecognizer recognizer=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        try {
            Text text=Tasks.await(recognizer.process(InputImage.fromBitmap(bitmap,0)),30,TimeUnit.SECONDS);
            assertTrue("OCR result did not contain the valid key: "+text.getText(),NfeKey.fromOcr(text.getText()).contains(key()));
        } finally {recognizer.close();bitmap.recycle();}
    }
    @Test public void localSiteLoadsAndNativeKeyPersists() throws Exception {
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)) {
            waitForJs(scenario,"typeof window.acceptNativeKey === 'function'");
            String quoted=org.json.JSONObject.quote(key());
            assertEquals("true",js(scenario,"window.acceptNativeKey("+quoted+");document.getElementById('invoiceSearch').value === "+quoted));
            assertEquals("true",js(scenario,"!!localStorage.getItem('nfeFacilSessionV1')"));
            assertEquals("true",js(scenario,"document.querySelectorAll('.scan-copy-btn').length > 0"));
            assertEquals("false",js(scenario,"document.getElementById('cameraModal').classList.contains('hidden') === false"));
        }
    }
    @Test public void xmlCalculationAndNativeCsvBridge() throws Exception {
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)) {
            waitForJs(scenario,"typeof window.acceptNativeKey === 'function'");
            String xml="<NFe><infNFe><ide><nNF>123</nNF></ide><det nItem='1'><prod><xProd>Teste Android</xProd><qCom>2</qCom><vUnCom>10</vUnCom><vProd>20</vProd></prod><imposto><ICMS><ICMS10><vICMSST>4</vICMSST></ICMS10></ICMS></imposto></det></infNFe></NFe>";
            assertEquals("12",js(scenario,"parseXML("+org.json.JSONObject.quote(xml)+").items[0].unitCostWithST"));
            assertEquals("true",js(scenario,"typeof window.print === 'function' && window.__nfeNative === true"));
        }
    }
    @Test public void scannerOpensAndClosesWithCameraPermission() {
        try(ActivityScenario<ScannerActivity> scenario=ActivityScenario.launch(ScannerActivity.class)) {
            scenario.onActivity(activity->assertFalse(activity.isFinishing()));
        }
    }
    private static void waitForJs(ActivityScenario<MainActivity> scenario,String expression)throws Exception {
        long end=System.currentTimeMillis()+20000;
        while(System.currentTimeMillis()<end){if("true".equals(js(scenario,expression)))return;Thread.sleep(150);}
        fail("WebView did not become ready: "+expression);
    }
    private static String js(ActivityScenario<MainActivity> scenario,String expression)throws Exception {
        CountDownLatch latch=new CountDownLatch(1);AtomicReference<String> value=new AtomicReference<>();
        scenario.onActivity(activity->{WebView view=findWebView(activity.getWindow().getDecorView());assertNotNull(view);view.evaluateJavascript(expression,result->{value.set(result);latch.countDown();});});
        assertTrue(latch.await(10,TimeUnit.SECONDS));return value.get();
    }
    private static WebView findWebView(android.view.View view){
        if(view instanceof WebView)return (WebView)view;
        if(view instanceof android.view.ViewGroup){android.view.ViewGroup group=(android.view.ViewGroup)view;for(int i=0;i<group.getChildCount();i++){WebView found=findWebView(group.getChildAt(i));if(found!=null)return found;}}
        return null;
    }
}
