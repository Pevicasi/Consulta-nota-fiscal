package br.com.pevicasi.nfefacil;
import android.graphics.*;
import android.os.SystemClock;
import android.view.*;
import android.widget.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.barcode.*;
import com.google.mlkit.vision.common.InputImage;
import com.google.zxing.*;
import com.google.zxing.common.BitMatrix;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.Assert.*;
@RunWith(AndroidJUnit4.class)
public class ScannerUpdateTest {
 @Rule public androidx.test.rule.GrantPermissionRule cameraPermission=androidx.test.rule.GrantPermissionRule.grant(android.Manifest.permission.CAMERA);
 static Bitmap sheet(int variant)throws Exception{
  Bitmap sheet=Bitmap.createBitmap(1800,900,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(sheet);c.drawColor(variant==3?Color.rgb(150,150,140):Color.WHITE);
  Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setColor(Color.BLACK);p.setTextSize(50);c.drawText("DANFE SINTETICO - SEM VALIDADE FISCAL",40,90,p);
  int w=variant==2?1100:1650,h=180;BitMatrix code=new MultiFormatWriter().encode(MultiBarcodeTest.key("55",123),BarcodeFormat.CODE_128,w,h);
  Bitmap stamp=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);for(int y=0;y<h;y++)for(int x=0;x<w;x++)stamp.setPixel(x,y,code.get(x,y)?Color.BLACK:Color.WHITE);
  c.save();if(variant==1)c.rotate(4,900,500);if(variant==4){p.setAlpha(160);p.setFilterBitmap(true);}c.drawBitmap(stamp,60,350,p);c.restore();if(variant==6){BitMatrix second=new MultiFormatWriter().encode(MultiBarcodeTest.key("65",456),BarcodeFormat.CODE_128,w,h);for(int y=0;y<h;y++)for(int x=0;x<w;x++)stamp.setPixel(x,y,second.get(x,y)?Color.BLACK:Color.WHITE);c.drawBitmap(stamp,60,650,null);}stamp.recycle();
  if(variant==5){p.setColor(Color.rgb(220,220,220));p.setStrokeWidth(3);c.drawLine(950,100,955,800,p);}return sheet;
 }
 private static File directory(){File d=InstrumentationRegistry.getInstrumentation().getTargetContext().getExternalFilesDir("appearance-evidence");assertTrue(d.isDirectory()||d.mkdirs());return d;}
 @Test public void benchmarkBeforeAndAfterUsesTheSameSyntheticDanfeFramesAndLazyFastPath()throws Exception{
  BarcodeScanner scanner=BarcodeScanning.getClient(new BarcodeScannerOptions.Builder().setBarcodeFormats(com.google.mlkit.vision.barcode.common.Barcode.FORMAT_CODE_128).enableAllPotentialBarcodes().build());ExecutorService worker=Executors.newSingleThreadExecutor();JSONArray samples=new JSONArray();
  try{
   Bitmap warm=sheet(0);Tasks.await(scanner.process(InputImage.fromBitmap(warm,0)),30,TimeUnit.SECONDS);warm.recycle();
   for(int variant=0;variant<7;variant++){
    Bitmap bitmap=sheet(variant);try{
     for(int repeat=0;repeat<3;repeat++){
      long start,before,after;DetectedKeys old,now;AtomicInteger conversions=new AtomicInteger();
      if(repeat%2==0){
       start=SystemClock.elapsedRealtime();old=Tasks.await(FiscalScannerBaseline.scan(scanner,InputImage.fromBitmap(bitmap,0),bitmap,worker),60,TimeUnit.SECONDS);before=SystemClock.elapsedRealtime()-start;
       start=SystemClock.elapsedRealtime();now=Tasks.await(FiscalScanner.scanLazy(scanner,InputImage.fromBitmap(bitmap,0),()->{conversions.incrementAndGet();return bitmap;},worker),60,TimeUnit.SECONDS);after=SystemClock.elapsedRealtime()-start;
      }else{
       start=SystemClock.elapsedRealtime();now=Tasks.await(FiscalScanner.scanLazy(scanner,InputImage.fromBitmap(bitmap,0),()->{conversions.incrementAndGet();return bitmap;},worker),60,TimeUnit.SECONDS);after=SystemClock.elapsedRealtime()-start;
       start=SystemClock.elapsedRealtime();old=Tasks.await(FiscalScannerBaseline.scan(scanner,InputImage.fromBitmap(bitmap,0),bitmap,worker),60,TimeUnit.SECONDS);before=SystemClock.elapsedRealtime()-start;
      }
      assertTrue("Previously recognized keys must be retained",now.keys().containsAll(old.keys()));if(variant==0)assertTrue("Clear DANFE must decode",now.keys().contains(MultiBarcodeTest.key("55",123)));if(variant==0)assertEquals("Fast path must not request bitmap conversion",0,conversions.get());
      samples.put(new JSONObject().put("variant",new String[]{"clear","tilted4deg","small","low-light-background","low-contrast","fold-line","multiple-code128"}[variant]).put("repeat",repeat).put("beforeMs",before).put("afterMs",after).put("bitmapConversions",conversions.get()).put("validCount",now.keys().size()));
     }
    }finally{bitmap.recycle();}
   }
  }finally{scanner.close();worker.shutdown();try(java.io.Writer out=new FileWriter(new File(directory(),"scanner-benchmark.json"))){out.write(samples.toString(2));}}
 }
 @Test public void movingSearchThenRealDetectorTrackingHasSequentialEvidenceAndStopsOnPause()throws Exception{
  androidx.test.uiautomator.UiDevice device=androidx.test.uiautomator.UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
  String previousScale=device.executeShellCommand("settings get global animator_duration_scale").trim();
  device.executeShellCommand("settings put global animator_duration_scale 1");
  try(ActivityScenario<ScannerActivity>s=ActivityScenario.launch(ScannerActivity.class)){
   AtomicReference<ScannerOverlay> ref=new AtomicReference<>();
   s.onActivity(a->{try{java.lang.reflect.Field f=ScannerActivity.class.getDeclaredField("overlay");f.setAccessible(true);ref.set((ScannerOverlay)f.get(a));java.lang.reflect.Field analysis=ScannerActivity.class.getDeclaredField("analysis");analysis.setAccessible(true);if(analysis.get(a)!=null)((androidx.camera.core.ImageAnalysis)analysis.get(a)).clearAnalyzer();}catch(Exception e){throw new AssertionError(e);}});
   Thread.sleep(800);s.onActivity(a->{try{java.lang.reflect.Field f=ScannerActivity.class.getDeclaredField("analysis");f.setAccessible(true);if(f.get(a)!=null)((androidx.camera.core.ImageAnalysis)f.get(a)).clearAnalyzer();}catch(Exception e){throw new AssertionError(e);}});
   ScannerOverlay overlay=ref.get();JSONArray frames=new JSONArray();
   for(String mode:new String[]{"light","dark"})for(int font:new int[]{90,160}){
    s.onActivity(a->{a.setThemeMode(mode);a.setFontPercent(font);overlay.clear();overlay.setActive(true);});
    RectF first=new RectF();
    for(int n=0;n<3;n++){Thread.sleep(320);final int step=n;s.onActivity(a->{RectF r=overlay.drawingBounds();if(step==0)first.set(r);else assertFalse("Search frame must move before detection",first.equals(r));try{frames.put(new JSONObject().put("theme",mode).put("font",font).put("step",step).put("bounds",r.toShortString()).put("tracking",overlay.isTracking()));}catch(Exception e){throw new AssertionError(e);}});AppearanceTest.screenshot("search-"+mode+"-"+font+"-"+n,s);}
    // Display a synthetic video frame, decode it with the real bundled ML Kit, then map its real bounds to that displayed frame.
    Bitmap bitmap=sheet(0);BarcodeScanner scanner=BarcodeScanning.getClient(new BarcodeScannerOptions.Builder().setBarcodeFormats(com.google.mlkit.vision.barcode.common.Barcode.FORMAT_CODE_128).build());
    try{DetectedKeys found=DetectedKeys.barcodes(Tasks.await(scanner.process(InputImage.fromBitmap(bitmap,0)),30,TimeUnit.SECONDS));assertNotNull(found.bounds(null));
     s.onActivity(a->{FrameLayout parent=(FrameLayout)overlay.getParent();ImageView image=new ImageView(a);image.setImageBitmap(bitmap);image.setScaleType(ImageView.ScaleType.FIT_XY);parent.addView(image,Math.max(0,parent.indexOfChild(overlay)));Rect real=found.bounds(null);float x=overlay.getWidth()/1800f,y=overlay.getHeight()/900f;overlay.track(new RectF(real.left*x,real.top*y,real.right*x,real.bottom*y),true,3000);});Thread.sleep(150);AppearanceTest.screenshot("tracking-real-"+mode+"-"+font,s);
     s.onActivity(a->assertTrue(overlay.isTracking()));
     long confirmationStart=SystemClock.elapsedRealtime();s.onActivity(a->a.offerKeys(found.keys()));long confirmationMs=SystemClock.elapsedRealtime()-confirmationStart;
     frames.put(new JSONObject().put("theme",mode).put("font",font).put("stage","valid-result-to-dialog").put("ms",confirmationMs));
     s.onActivity(a->{try{java.lang.reflect.Field f=ScannerActivity.class.getDeclaredField("confirmation");f.setAccessible(true);android.app.AlertDialog dialog=(android.app.AlertDialog)f.get(a);assertTrue(dialog.isShowing());dialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE).performClick();}catch(Exception e){throw new AssertionError(e);}});
     s.onActivity(a->{FrameLayout parent=(FrameLayout)overlay.getParent();for(int i=parent.getChildCount()-1;i>=0;i--)if(parent.getChildAt(i) instanceof ImageView)parent.removeViewAt(i);});
    }finally{scanner.close();bitmap.recycle();}
   }
   s.moveToState(androidx.lifecycle.Lifecycle.State.CREATED);Thread.sleep(100);RectF frozen=new RectF();InstrumentationRegistry.getInstrumentation().runOnMainSync(()->frozen.set(overlay.drawingBounds()));Thread.sleep(150);InstrumentationRegistry.getInstrumentation().runOnMainSync(()->assertEquals(frozen,overlay.drawingBounds()));
   try(java.io.Writer out=new FileWriter(new File(directory(),"scanner-animation-frames.json"))){out.write(frames.toString(2));}
  }finally{device.executeShellCommand("settings put global animator_duration_scale "+(previousScale.equals("null")?"0":previousScale));}
 }
 @Test public void reducedMotionDoesNotExploreAndRealPotentialIsNotAValidatedKey()throws Exception{
  androidx.test.uiautomator.UiDevice device=androidx.test.uiautomator.UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());String previous=device.executeShellCommand("settings get global animator_duration_scale").trim();device.executeShellCommand("settings put global animator_duration_scale 0");
  try(ActivityScenario<ScannerActivity>s=ActivityScenario.launch(ScannerActivity.class)){
   Thread.sleep(600);AtomicReference<ScannerOverlay> reference=new AtomicReference<>();RectF first=new RectF();
   s.onActivity(a->{try{java.lang.reflect.Field field=ScannerActivity.class.getDeclaredField("overlay");field.setAccessible(true);ScannerOverlay overlay=(ScannerOverlay)field.get(a);reference.set(overlay);assertFalse(android.animation.ValueAnimator.areAnimatorsEnabled());overlay.clear();overlay.setActive(true);Bitmap b=Bitmap.createBitmap(overlay.getWidth(),overlay.getHeight(),Bitmap.Config.ARGB_8888);overlay.draw(new Canvas(b));first.set(overlay.drawingBounds());b.recycle();}catch(Exception e){throw new AssertionError(e);}});
   Thread.sleep(200);s.onActivity(a->{ScannerOverlay overlay=reference.get();Bitmap b=Bitmap.createBitmap(overlay.getWidth(),overlay.getHeight(),Bitmap.Config.ARGB_8888);overlay.draw(new Canvas(b));assertEquals(first,overlay.drawingBounds());assertFalse(overlay.isTracking());overlay.track(new RectF(50,60,400,100),false,1000);overlay.draw(new Canvas(b));assertEquals(new RectF(50,60,400,100),overlay.drawingBounds());b.recycle();});
  }finally{device.executeShellCommand("settings put global animator_duration_scale "+(previous.equals("null")?"0":previous));}
 }
}
