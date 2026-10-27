package br.com.pevicasi.nfefacil;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.*;
import android.util.Size;
import android.widget.TextView;
import androidx.camera.core.ImageInfo;
import androidx.camera.core.ImageProxy;
import androidx.camera.view.transform.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.lang.reflect.Proxy;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class ScannerTest {
    @org.junit.Rule public androidx.test.rule.GrantPermissionRule cameraPermission = androidx.test.rule.GrantPermissionRule.grant(android.Manifest.permission.CAMERA);
    private static final String KEY = "35261012345678000195550010000001231123456788";
    private static String key() {
        String base=KEY.substring(0,43); int sum=0,w=2;
        for(int i=42;i>=0;i--){sum+=(base.charAt(i)-'0')*w;w=w==9?2:w+1;}
        return base+(sum%11<2?0:11-sum%11);
    }
    private static AlertDialog dialog(ScannerActivity activity) {
        try { java.lang.reflect.Field f=ScannerActivity.class.getDeclaredField("confirmation"); f.setAccessible(true); return (AlertDialog)f.get(activity); }
        catch(Exception e){throw new AssertionError(e);}
    }
    @Test public void confirmationRequiresExplicitButtonAndDeliversExactlyOneResult() {
        Intent intent=new Intent(InstrumentationRegistry.getInstrumentation().getTargetContext(),ScannerActivity.class);
        try(ActivityScenario<ScannerActivity> scenario=ActivityScenario.launchActivityForResult(intent)) {
            scenario.onActivity(activity->{
                activity.offerKeys(new LinkedHashSet<>(Arrays.asList("invalid",key())));
                AlertDialog first=dialog(activity); assertNotNull(first); assertTrue(first.isShowing());
                assertEquals(key(),findSelectable(first.getWindow().getDecorView()).getText().toString());
                activity.offerKeys(Collections.singleton(key())); assertSame(first,dialog(activity));
                assertFalse(activity.isFinishing());
                first.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            });
            assertEquals(Activity.RESULT_OK,scenario.getResult().getResultCode());
            assertEquals(key(),scenario.getResult().getResultData().getStringExtra("key"));
        }
    }
    @Test public void invalidKeysNeverOpenConfirmationAndRetryAllowsNewDetection() {
        try(ActivityScenario<ScannerActivity> scenario=ActivityScenario.launch(ScannerActivity.class)) {
            scenario.onActivity(activity->{
                activity.offerKeys(Collections.singleton("123")); assertNull(dialog(activity));
                activity.offerKeys(Collections.singleton(key()));
                try {
                    java.lang.reflect.Field field=ScannerActivity.class.getDeclaredField("overlay");field.setAccessible(true);ScannerOverlay overlay=(ScannerOverlay)field.get(activity);overlay.track(new RectF(100,100,600,180),true,1000);
                    java.lang.reflect.Method update=ScannerActivity.class.getDeclaredMethod("showDetections",DetectedKeys.class,OutputTransform.class,long.class);update.setAccessible(true);update.invoke(activity,DetectedKeys.barcodes(Collections.emptyList()),null,900L);assertFalse("Lost selected code must clear stale geometry",overlay.isTracking());
                }catch(Exception e){throw new AssertionError(e);}
                dialog(activity).getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
            });
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(activity->{
                assertFalse(activity.isFinishing()); assertFalse(dialog(activity).isShowing());
                activity.offerKeys(Collections.singleton(key())); assertTrue(dialog(activity).isShowing());
            });
        }
    }
    @Test public void confirmationSurvivesActivityRecreation() {
        try(ActivityScenario<ScannerActivity> scenario=ActivityScenario.launch(ScannerActivity.class)) {
            scenario.onActivity(activity->activity.offerKeys(Collections.singleton(key())));
            scenario.recreate();
            scenario.onActivity(activity->{ assertNotNull(dialog(activity)); assertTrue(dialog(activity).isShowing()); assertEquals(key(),findSelectable(dialog(activity).getWindow().getDecorView()).getText().toString()); });
        }
    }
    @Test public void overlayUsesDetectedBoundsAndClearsStaleSuccess() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            ScannerOverlay view=new ScannerOverlay(InstrumentationRegistry.getInstrumentation().getTargetContext());
            view.layout(0,0,1000,600);
            Bitmap bitmap=Bitmap.createBitmap(1000,600,Bitmap.Config.ARGB_8888);
            view.track(new RectF(100,100,300,200),true,1000);
            for(int i=0;i<50;i++) { bitmap.eraseColor(Color.TRANSPARENT); view.draw(new Canvas(bitmap)); }
            assertEquals(Color.rgb(52,168,83),bitmap.getPixel(100,120));
            view.clear();bitmap.eraseColor(Color.TRANSPARENT);view.draw(new Canvas(bitmap));
            assertEquals(Color.TRANSPARENT,bitmap.getPixel(100,120));
            bitmap.recycle();
        });
    }
    private static TextView findSelectable(android.view.View view) {
        if(view instanceof TextView && ((TextView)view).isTextSelectable()) return (TextView)view;
        if(view instanceof android.view.ViewGroup) for(int i=0;i<((android.view.ViewGroup)view).getChildCount();i++) {
            TextView found=findSelectable(((android.view.ViewGroup)view).getChildAt(i)); if(found!=null)return found;
        }
        return null;
    }
    @Test @androidx.annotation.OptIn(markerClass=androidx.camera.view.TransformExperimental.class)
    public void detectorCoordinatesMapThroughCropRotationAndFrontMirror() {
        for(int rotation:new int[]{0,90,180,270}) for(boolean mirror:new boolean[]{false,true}) {
            ImageInfo info=(ImageInfo)Proxy.newProxyInstance(ImageInfo.class.getClassLoader(),new Class[]{ImageInfo.class},(p,m,a)->m.getName().equals("getRotationDegrees")?rotation:null);
            ImageProxy image=(ImageProxy)Proxy.newProxyInstance(ImageProxy.class.getClassLoader(),new Class[]{ImageProxy.class},(p,m,a)->{
                switch(m.getName()) {case "getWidth":return 1280;case "getHeight":return 720;case "getCropRect":return new Rect(100,50,1100,650);case "getImageInfo":return info;default:return null;}
            });
            // Preview normalized viewport is 1000x600; mirror is supplied by PreviewView's transform.
            Matrix target=new Matrix();target.setScale(mirror?-500:500,300);target.postTranslate(500,300);
            OutputTransform preview=new OutputTransform(target,new Size(1000,600));
            float[] points={300,200,500,300};
            for(int i=0;i<points.length;i+=2) {
                float x=points[i], y=points[i+1];
                if(rotation==90){points[i]=720-y;points[i+1]=x;}
                if(rotation==180){points[i]=1280-x;points[i+1]=720-y;}
                if(rotation==270){points[i]=y;points[i+1]=1280-x;}
            }
            new CoordinateTransform(ScannerActivity.sourceTransform(image),preview).mapPoints(points);
            assertEquals(mirror?800:200,points[0],.01f);assertEquals(150,points[1],.01f);
            assertEquals(mirror?600:400,points[2],.01f);assertEquals(250,points[3],.01f);
        }
    }
}
