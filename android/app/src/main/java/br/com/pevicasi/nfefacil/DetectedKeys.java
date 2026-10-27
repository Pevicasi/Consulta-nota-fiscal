package br.com.pevicasi.nfefacil;

import android.graphics.Rect;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.text.Text;
import java.util.*;

/** One policy for live frames, photos and OCR; ordering never chooses an invoice. */
final class DetectedKeys {
    private Rect potential;
    Rect potentialBounds(){return potential==null?null:new Rect(potential);}
    private final SortedMap<String, Rect> candidates = new TreeMap<>();
    static DetectedKeys barcodes(List<Barcode> results) {
        DetectedKeys found = new DetectedKeys();
        for (Barcode result : results) {
            if (result.getFormat() != Barcode.FORMAT_CODE_128) continue;
            String key=NfeKey.barcode(result.getRawValue());
            if(key.isEmpty()&&result.getBoundingBox()!=null)found.potential=new Rect(result.getBoundingBox());
            found.add(key, result.getBoundingBox());
        }
        return found;
    }
    static DetectedKeys region(List<Barcode> results, Rect region) {
        DetectedKeys found = new DetectedKeys();
        for (Barcode result : results) {
            if (result.getFormat() != Barcode.FORMAT_CODE_128) continue;
            Rect bounds = result.getBoundingBox();
            // A clipped strip may decode correctly but cannot define the full code's geometry.
            if (bounds != null) {
                bounds = new Rect(bounds);
                if (bounds.left <= 1 || bounds.top <= 1 || bounds.right >= region.width()-1 || bounds.bottom >= region.height()-1) bounds = null;
                else bounds.offset(region.left, region.top);
            }
            found.add(NfeKey.barcode(result.getRawValue()), bounds);
        }
        return found;
    }
    void merge(DetectedKeys other) {
        for (String key : other.keys()) add(key, other.candidates.get(key));
    }
    static DetectedKeys text(Text text) {
        DetectedKeys found = new DetectedKeys();
        for (String key : NfeKey.fromOcr(text.getText())) found.add(key, null);
        for (Text.TextBlock block : text.getTextBlocks()) for (Text.Line line : block.getLines())
            for (String key : NfeKey.fromOcr(line.getText())) found.add(key, line.getBoundingBox());
        return found;
    }
    private void add(String key, Rect bounds) {
        if (key.isEmpty()) return;
        Rect previous = candidates.get(key);
        if (!candidates.containsKey(key) || previous == null || (bounds != null && bounds.width() * (long) bounds.height() < previous.width() * (long) previous.height()))
            candidates.put(key, bounds == null ? null : new Rect(bounds));
    }
    Set<String> keys() { return new LinkedHashSet<>(candidates.keySet()); }
    Rect bounds(String explicitlySelected) {
        // Multiple different keys have no default target, even when one is larger or first.
        String key = explicitlySelected;
        if (key == null && candidates.size() == 1) key = candidates.firstKey();
        Rect bounds = key == null ? null : candidates.get(key);
        return bounds == null ? null : new Rect(bounds);
    }
}
