package br.com.pevicasi.nfefacil;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.*;
import android.widget.Spinner;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.barcode.*;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;
import com.google.zxing.*;
import com.google.zxing.common.BitMatrix;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class MultiBarcodeTest {
    @org.junit.Rule public androidx.test.rule.GrantPermissionRule cameraPermission = androidx.test.rule.GrantPermissionRule.grant(android.Manifest.permission.CAMERA);
    static String key(String model, int number) {
        String base="35261012345678000195"+model+"001"+String.format(java.util.Locale.ROOT,"%09d",number)+"1"+"12345678";
        int sum=0,w=2;for(int i=42;i>=0;i--){sum+=(base.charAt(i)-'0')*w;w=w==9?2:w+1;}
        return base+(sum%11<2?0:11-sum%11);
    }
    private static String syntheticBoleto() {
        String digits="0019"+"0"+"1000"+"0000001000"+"0000000000000000000000000";
        int sum=0,weight=2;
        for(int i=43;i>=0;i--) if(i!=4){sum+=(digits.charAt(i)-'0')*weight;weight=weight==9?2:weight+1;}
        int dv=11-sum%11;if(dv==0||dv==10||dv==11)dv=1;
        return digits.substring(0,4)+dv+digits.substring(5);
    }
    private static void draw(Bitmap sheet, String value, BarcodeFormat format, int y) throws Exception {
        int height = format == BarcodeFormat.QR_CODE ? 340 : 200;
        int width = format == BarcodeFormat.QR_CODE ? 340 : 2100;
        BitMatrix bits=new MultiFormatWriter().encode(value,format,width,height);
        Bitmap stamp=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);
        for(int row=0;row<height;row++)for(int col=0;col<width;col++)stamp.setPixel(col,row,bits.get(col,row)?Color.BLACK:Color.WHITE);
        new Canvas(sheet).drawBitmap(stamp,50,y,null);stamp.recycle();
    }
    private static List<Barcode> decode(Bitmap sheet) throws Exception { return decode(sheet, Barcode.FORMAT_ALL_FORMATS); }
    private static List<Barcode> decode(Bitmap sheet, int format) throws Exception {
        BarcodeScanner scanner=BarcodeScanning.getClient(new BarcodeScannerOptions.Builder().setBarcodeFormats(format).build());
        try {return new ArrayList<>(Tasks.await(scanner.process(InputImage.fromBitmap(sheet,0)),30,TimeUnit.SECONDS));}
        finally {scanner.close();}
    }
    private static DetectedKeys decodeFiscal(Bitmap sheet) throws Exception {
        BarcodeScanner scanner=BarcodeScanning.getClient(new BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_CODE_128).enableAllPotentialBarcodes().build());
        java.util.concurrent.ExecutorService worker=java.util.concurrent.Executors.newSingleThreadExecutor();
        try {return Tasks.await(FiscalScanner.scan(scanner,InputImage.fromBitmap(sheet,0),sheet,worker),60,TimeUnit.SECONDS);}
        finally {scanner.close();worker.shutdown();}
    }
    private static String describe(List<Barcode> codes) {
        StringBuilder description=new StringBuilder();
        for(Barcode code:codes) description.append("[format=").append(code.getFormat()).append(", raw=").append(code.getRawValue()).append(", fiscal=").append(NfeKey.barcode(code.getRawValue())).append(", box=").append(code.getBoundingBox()).append("] ");
        return description.toString();
    }
    @Test public void paymentAndQrBeforeTheInvoiceNeverBecomeTheCandidate() throws Exception {
        Bitmap sheet=Bitmap.createBitmap(2200,1500,Bitmap.Config.ARGB_8888);sheet.eraseColor(Color.WHITE);
        try {
            String payment=syntheticBoleto(), nonFiscal=key("47",1), invoice=key("55",123), qr=key("55",789);
            draw(sheet,payment,BarcodeFormat.ITF,40);
            draw(sheet,nonFiscal,BarcodeFormat.CODE_128,330);
            draw(sheet,qr,BarcodeFormat.QR_CODE,610);
            draw(sheet,invoice,BarcodeFormat.CODE_128,1100);
            List<Barcode> results=decode(sheet);
            assertTrue("The real mixed-format image must decode its fiscal QR",results.stream().anyMatch(b->b.getFormat()==Barcode.FORMAT_QR_CODE && qr.equals(b.getRawValue())));
            assertTrue("The real mixed-format image must decode its payment ITF",results.stream().anyMatch(b->b.getFormat()==Barcode.FORMAT_ITF && payment.equals(b.getRawValue())));
            assertTrue(results.stream().anyMatch(b->nonFiscal.equals(b.getRawValue())));
            DetectedKeys found=decodeFiscal(sheet);
            assertEquals("Regional production scan must recover the fiscal code hidden by the full-frame ROI: "+describe(results),Collections.singleton(invoice),found.keys());
            assertNotNull(found.bounds(null));assertTrue(found.bounds(null).top>1000);
            Collections.reverse(results);
            assertFalse(DetectedKeys.barcodes(results).keys().contains(qr));
            assertFalse(found.keys().contains(payment));assertFalse(found.keys().contains(nonFiscal));
        } finally {sheet.recycle();}
    }
    @Test public void multipleFiscalKeysAreDeduplicatedAndHaveNoImplicitOverlayTarget() throws Exception {
        Bitmap sheet=Bitmap.createBitmap(2200,1500,Bitmap.Config.ARGB_8888);sheet.eraseColor(Color.WHITE);
        try {
            String first=key("55",123), second=key("65",456);
            String invalid=first.substring(0,43)+(first.endsWith("9")?"0":"9");
            draw(sheet,first,BarcodeFormat.CODE_128,40);
            draw(sheet,invalid,BarcodeFormat.CODE_128,350);
            draw(sheet,first,BarcodeFormat.CODE_128,660);
            draw(sheet,second,BarcodeFormat.CODE_128,970);
            List<Barcode> results=decode(sheet);
            // ML Kit may merge repeated/aligned physical codes; validate fiscal identities, not an unsupported physical-count guarantee.
            DetectedKeys found=decodeFiscal(sheet);
            assertEquals(new HashSet<>(Arrays.asList(first,second)),found.keys());
            assertNull(found.bounds(null));assertNull(found.bounds("unselected"));
            assertNotNull(found.bounds(first));assertTrue("Must fit a single code, not the merged multi-code ROI",found.bounds(first).height()<300);
            assertTrue(found.bounds(second).top>900);
            assertFalse(found.keys().contains(invalid));
            // Also exercise app-side deduplication when the SDK supplies repeated detections.
            Barcode duplicate=results.stream().filter(b->first.equals(b.getRawValue())).findFirst().get();
            results.add(duplicate);Collections.reverse(results);
            assertEquals(found.keys(),DetectedKeys.barcodes(results).keys());
        } finally {sheet.recycle();}
    }
    @Test public void itfEncodedFiscalDigitsAreNotAcceptedAsCode128() throws Exception {
        Bitmap sheet=Bitmap.createBitmap(2200,700,Bitmap.Config.ARGB_8888);sheet.eraseColor(Color.WHITE);
        try {
            draw(sheet,key("55",123),BarcodeFormat.ITF,40);
            draw(sheet,key("55",456),BarcodeFormat.CODE_128,380);
            List<Barcode> results=decode(sheet);
            assertTrue(results.stream().anyMatch(b->b.getFormat()==Barcode.FORMAT_ITF));
            assertEquals(Collections.singleton(key("55",456)),DetectedKeys.barcodes(results).keys());
        } finally {sheet.recycle();}
    }
    @Test public void ambiguousConfirmationNeedsAnExplicitChoiceAndReturnsChosenKey() {
        Intent intent=new Intent(InstrumentationRegistry.getInstrumentation().getTargetContext(),ScannerActivity.class);
        try(ActivityScenario<ScannerActivity> scenario=ActivityScenario.launchActivityForResult(intent)) {
            scenario.onActivity(activity->{
                activity.offerKeys(new LinkedHashSet<>(Arrays.asList(key("55",123),key("65",456))));
                AlertDialog dialog=dialog(activity);
                assertFalse(dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled());
                Spinner spinner=findSpinner(dialog.getWindow().getDecorView());assertNotNull(spinner);spinner.setSelection(2);
            });
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(activity->{AlertDialog dialog=dialog(activity);assertTrue(dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled());dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();});
            assertEquals(key("65",456),scenario.getResult().getResultData().getStringExtra("key"));
        }
    }
    @Test public void ocrUsesTheSameFiscalPolicyAndKeepsAllValidChoices() throws Exception {
        Bitmap bitmap=Bitmap.createBitmap(2200,650,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(bitmap);canvas.drawColor(Color.WHITE);
        Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);paint.setColor(Color.BLACK);paint.setTypeface(Typeface.MONOSPACE);paint.setTextSize(56);
        String first=key("55",123),second=key("65",456);
        canvas.drawText(first.replaceAll("(.{4})","$1 "),30,120,paint);
        canvas.drawText(second.replaceAll("(.{4})","$1 "),30,300,paint);
        canvas.drawText(key("47",1),30,480,paint);
        com.google.mlkit.vision.text.TextRecognizer recognizer=com.google.mlkit.vision.text.TextRecognition.getClient(com.google.mlkit.vision.text.latin.TextRecognizerOptions.DEFAULT_OPTIONS);
        try {
            com.google.mlkit.vision.text.Text text=Tasks.await(recognizer.process(InputImage.fromBitmap(bitmap,0)),30,TimeUnit.SECONDS);
            DetectedKeys found=DetectedKeys.text(text);
            assertEquals(new HashSet<>(Arrays.asList(first,second)),found.keys());
            assertNull(found.bounds(null));assertNotNull(found.bounds(first));assertNotNull(found.bounds(second));
        } finally {recognizer.close();bitmap.recycle();}
    }
    @Test public void recreationDoesNotAutoSelectAnAmbiguousKey() {
        try(ActivityScenario<ScannerActivity> scenario=ActivityScenario.launch(ScannerActivity.class)) {
            scenario.onActivity(activity->activity.offerKeys(new LinkedHashSet<>(Arrays.asList(key("55",123),key("65",456)))));
            scenario.recreate();
            scenario.onActivity(activity->{assertFalse(dialog(activity).getButton(AlertDialog.BUTTON_POSITIVE).isEnabled());findSpinner(dialog(activity).getWindow().getDecorView()).setSelection(2);});
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.recreate();
            scenario.onActivity(activity->{assertTrue(dialog(activity).getButton(AlertDialog.BUTTON_POSITIVE).isEnabled());assertEquals(2,findSpinner(dialog(activity).getWindow().getDecorView()).getSelectedItemPosition());});
        }
    }
    private static AlertDialog dialog(ScannerActivity activity) {
        try {java.lang.reflect.Field f=ScannerActivity.class.getDeclaredField("confirmation");f.setAccessible(true);return (AlertDialog)f.get(activity);}
        catch(Exception e){throw new AssertionError(e);}
    }
    private static Spinner findSpinner(android.view.View view) {
        if(view instanceof Spinner)return (Spinner)view;
        if(view instanceof android.view.ViewGroup)for(int i=0;i<((android.view.ViewGroup)view).getChildCount();i++) {
            Spinner s=findSpinner(((android.view.ViewGroup)view).getChildAt(i));if(s!=null)return s;
        }
        return null;
    }
}
