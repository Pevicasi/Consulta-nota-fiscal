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

/** Bounded ML Kit rereads separate aligned codes merged into one full-frame ROI. */
final class FiscalScanner {
    static Task<DetectedKeys> scan(BarcodeScanner scanner, InputImage full, Bitmap upright, Executor worker) {
        try {
            return scanner.process(full).onSuccessTask(worker, results -> {
                Task<DetectedKeys> accumulated = Tasks.forResult(DetectedKeys.barcodes(results));
                if (upright == null) return accumulated;
                for (Rect region : regions(upright.getWidth(), upright.getHeight(), results)) {
                    accumulated = accumulated.onSuccessTask(worker, found -> reread(scanner, upright, region, found, worker));
                }
                return accumulated;
            });
        } catch (RuntimeException error) { return Tasks.forException(error); }
    }
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
        // At most seven overlapping bands per axis (14 probes), processed sequentially off the UI thread.
        addBands(windows,width,height,true,horizontal?4:(shortCode?2:0));
        addBands(windows,width,height,false,vertical?4:(narrow?2:0));
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
