package br.com.pevicasi.nfefacil;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.os.SystemClock;
import android.view.View;

/** Original corner artwork. Targets are supplied only by detector geometry. */
public final class ScannerOverlay extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF current = new RectF(), target = new RectF();
    private long expires;
    private boolean found, success;
    public ScannerOverlay(Context context) {
        super(context);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setClickable(false);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setStrokeWidth(3 * getResources().getDisplayMetrics().density);
    }
    public void track(RectF bounds, boolean valid, long duration) {
        if (bounds == null || bounds.isEmpty()) return;
        if (!found) current.set(getWidth() * .1f, getHeight() * .4f, getWidth() * .9f, getHeight() * .6f);
        target.set(bounds); found = true; success = valid;
        expires = SystemClock.uptimeMillis() + duration;
        invalidate();
    }
    public void clear() { found = false; success = false; invalidate(); }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (found && SystemClock.uptimeMillis() >= expires) found = false;
        boolean animate = ValueAnimator.areAnimatorsEnabled();
        if (!found) {
            // A static guide is explicitly not a detected position.
            current.set(getWidth() * .1f, getHeight() * .4f, getWidth() * .9f, getHeight() * .6f);
        } else {
            float t = animate ? .28f : 1;
            current.set(current.left + (target.left-current.left)*t, current.top + (target.top-current.top)*t,
                current.right + (target.right-current.right)*t, current.bottom + (target.bottom-current.bottom)*t);
        }
        paint.setColor(found ? (success ? Color.rgb(46, 190, 110) : Color.rgb(72, 160, 255)) : Color.LTGRAY);
        float length = Math.min(current.width(), current.height()) * .3f;
        float radius = Math.min(10 * getResources().getDisplayMetrics().density, length / 2);
        for (int i=0; i<4; i++) {
            canvas.save();
            float x=(i==0||i==3)?current.left:current.right, y=i<2?current.top:current.bottom;
            canvas.translate(x,y); canvas.scale((i==0||i==3)?1:-1,i<2?1:-1);
            Path corner=new Path(); corner.moveTo(0,length); corner.lineTo(0,radius);
            corner.quadTo(0,0,radius,0); corner.lineTo(length,0); canvas.drawPath(corner,paint); canvas.restore();
        }
        if (found) {
            float delta = Math.abs(current.left-target.left) + Math.abs(current.top-target.top)
                + Math.abs(current.right-target.right) + Math.abs(current.bottom-target.bottom);
            if (animate && delta > .5f) postInvalidateOnAnimation();
            else postInvalidateDelayed(Math.max(1, expires-SystemClock.uptimeMillis()));
        }
    }
    @Override protected void onDetachedFromWindow() { clear(); super.onDetachedFromWindow(); }
}
