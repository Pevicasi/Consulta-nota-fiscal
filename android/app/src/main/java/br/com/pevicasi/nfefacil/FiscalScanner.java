package br.com.pevicasi.nfefacil;

import android.graphics.Bitmap;
import android.graphics.Rect;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import android.os.SystemClock;

/** Bounded ML Kit rereads separate aligned codes merged into one full-frame ROI. */
final class FiscalScanner {
    /** Live frames never wait for Bitmap conversion or regional rereads; next frame supplies fresh geometry. */
    static Task<DetectedKeys> scanLive(BarcodeScanner scanner,InputImage full,Executor worker){
        long started=SystemClock.elapsedRealtime();
        try{return scanner.process(full).continueWith(worker,task->{
            ScanMetrics.record("live-full-frame",started,0);
            return DetectedKeys.barcodes(task.getResult());
        });}catch(RuntimeException error){return Tasks.forException(error);}
    }
    static Task<DetectedKeys> scan(BarcodeScanner scanner, InputImage full, Bitmap upright, Executor worker) {
        return scanLazy(scanner, full, () -> upright, worker);
    }
    static Task<DetectedKeys> scanLazy(BarcodeScanner scanner, InputImage full, Supplier<Bitmap> bitmap, Executor worker) {
        final long started=SystemClock.elapsedRealtime();
        try {
            return scanner.process(full).onSuccessTask(worker, results -> {
                DetectedKeys first=DetectedKeys.barcodes(results);
                boolean uncertain=results.stream().anyMatch(b -> b.getBoundingBox()!=null && (NfeKey.barcode(b.getRawValue()).isEmpty() || merged(b.getBoundingBox())));
                if(!ScanPolicy.refine(first.keys().size(),uncertain,results.stream().anyMatch(b->b.getBoundingBox()!=null&&!b.getBoundingBox().isEmpty()))){ ScanMetrics.record("barcode-fast",started,0);return Tasks.forResult(first); }
                if(results.isEmpty())return Tasks.forResult(first);
                Bitmap upright=bitmap.get();if(upright==null)return Tasks.forResult(first);
                final long roiStarted=SystemClock.elapsedRealtime();
                Task<DetectedKeys> accumulated=Tasks.forResult(first);
                int count=0;final java.util.concurrent.atomic.AtomicInteger executed=new java.util.concurrent.atomic.AtomicInteger();
                for(Rect region:regions(upright.getWidth(),upright.getHeight(),results)){
                    if(++count>ScanPolicy.MAX_ROIS)break;
                    accumulated=accumulated.onSuccessTask(worker,found -> {
                        if(!ScanPolicy.probe(0,SystemClock.elapsedRealtime()-roiStarted))return Tasks.forResult(found);
                        executed.incrementAndGet();return reread(scanner,upright,region,found,worker);
                    });
                }
                return accumulated.continueWith(worker,t->{ScanMetrics.record("barcode-roi",started,executed.get());return t.getResult();});
            });
        } catch(RuntimeException error){return Tasks.forException(error);}
    }
    private static boolean merged(Rect box){return Math.max(box.width(),box.height())<4.5f*Math.min(box.width(),box.height());}
    private static Task<DetectedKeys> reread(BarcodeScanner scanner, Bitmap upright, Rect region, DetectedKeys found, Executor worker) {
        final Bitmap patch;
        try { patch = Bitmap.createBitmap(upright, region.left, region.top, region.width(), region.height()); }
        catch (RuntimeException | OutOfMemoryError error) { return Tasks.forResult(found); }
        try {
            return scanner.process(InputImage.fromBitmap(patch, 0)).continueWith(worker, task -> {
                try {
                    if (task.isSuccessful()) found.merge(DetectedKeys.region(task.getResult(), region));
                    return found; // Optional reread failure never discards a successful full-frame result.
                } finally { patch.recycle(); }
            });
        } catch (RuntimeException error) { patch.recycle(); return Tasks.forResult(found); }
    }
    private static List<Rect> regions(int width, int height, List<Barcode> results) {
        boolean horizontal=false, vertical=false, narrow=false, shortCode=false;
        for (Barcode result : results) {
            Rect bounds=result.getBoundingBox();
            if (bounds==null || bounds.isEmpty()) continue;
            horizontal |= bounds.width()>=bounds.height();
            vertical |= bounds.height()>bounds.width();
            narrow |= bounds.width()<width*.65f;
            shortCode |= bounds.height()<height*.65f;
        }
        List<Rect> windows=new ArrayList<>();
        // No detected/potential code: keep the original full-frame/OCR path, without blind searches.
        // Probe only detector-supported orientations; cap eight probes and two seconds in scanLazy.
        addBands(windows,width,height,true,horizontal?4:0);
        addBands(windows,width,height,false,vertical?4:0);
        return windows;
    }
    private static void addBands(List<Rect> windows,int width,int height,boolean rows,int divisor) {
        int dimension=rows?height:width;
        if(divisor==0 || dimension<240)return;
        int size=dimension/divisor, count=2*divisor-1;
        for(int i=0;i<count;i++) {
            int start=i*(dimension-size)/(count-1);
            windows.add(rows?new Rect(0,start,width,start+size):new Rect(start,0,start+size,height));
        }
    }
}
