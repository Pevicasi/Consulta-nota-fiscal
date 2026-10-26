package br.com.pevicasi.nfefacil;

import android.content.Context;
import android.graphics.*;
import android.view.*;

/** Touch crop editor; coordinates are mapped back into the original image without stretching. */
public final class CropImageView extends View {
    private Bitmap bitmap;
    private final RectF image = new RectF(), selection = new RectF();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float lastX, lastY;
    private boolean resize;
    public CropImageView(Context context) { super(context); setContentDescription("Recorte da imagem: arraste para mover; canto inferior direito para redimensionar"); }
    public void setBitmap(Bitmap value) { bitmap = value; selection.setEmpty(); invalidate(); }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas); if (bitmap == null) return;
        float scale = Math.min((float)getWidth()/bitmap.getWidth(), (float)getHeight()/bitmap.getHeight());
        float w = bitmap.getWidth()*scale, h = bitmap.getHeight()*scale;
        image.set((getWidth()-w)/2, (getHeight()-h)/2, (getWidth()+w)/2, (getHeight()+h)/2);
        paint.setStyle(Paint.Style.FILL); paint.setColor(Color.BLACK); canvas.drawColor(Color.BLACK);
        canvas.drawBitmap(bitmap, null, image, paint);
        if (selection.isEmpty()) selection.set(image.left+image.width()*.03f, image.top+image.height()*.03f, image.right-image.width()*.03f, image.bottom-image.height()*.03f);
        paint.setColor(Color.rgb(37,99,235)); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(4); canvas.drawRect(selection, paint);
        paint.setStyle(Paint.Style.FILL); canvas.drawCircle(selection.right, selection.bottom, 16, paint);
    }
    @Override public boolean onTouchEvent(MotionEvent event) {
        float x=event.getX(), y=event.getY();
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                lastX=x; lastY=y; resize=Math.hypot(x-selection.right,y-selection.bottom)<64; return true;
            case MotionEvent.ACTION_MOVE:
                float dx=x-lastX, dy=y-lastY;
                if (resize) { selection.right=Math.max(selection.left+32, Math.min(image.right,selection.right+dx)); selection.bottom=Math.max(selection.top+32,Math.min(image.bottom,selection.bottom+dy)); }
                else selection.offset(Math.max(image.left-selection.left, Math.min(image.right-selection.right,dx)), Math.max(image.top-selection.top,Math.min(image.bottom-selection.bottom,dy)));
                lastX=x;lastY=y;invalidate();return true;
            case MotionEvent.ACTION_UP: performClick();return true;
            default:return true;
        }
    }
    @Override public boolean performClick() { super.performClick(); return true; }
    public Bitmap crop() {
        if (bitmap==null || image.isEmpty() || selection.isEmpty()) return null;
        float scale=bitmap.getWidth()/image.width();
        int x=Math.max(0,Math.min(bitmap.getWidth()-1,Math.round((selection.left-image.left)*scale)));
        int y=Math.max(0,Math.min(bitmap.getHeight()-1,Math.round((selection.top-image.top)*scale)));
        int w=Math.max(1,Math.min(bitmap.getWidth()-x,Math.round(selection.width()*scale)));
        int h=Math.max(1,Math.min(bitmap.getHeight()-y,Math.round(selection.height()*scale)));
        return Bitmap.createBitmap(bitmap,x,y,w,h);
    }
}
