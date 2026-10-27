package br.com.pevicasi.nfefacil;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.os.SystemClock;
import android.view.View;

/** Moving search artwork is never detector geometry; tracked rectangles always are. */
public final class ScannerOverlay extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF current=new RectF(),target=new RectF();
    private final int[] colors={Color.rgb(66,133,244),Color.rgb(234,67,53),Color.rgb(251,188,5),Color.rgb(52,168,83)};
    private long expires;
    private boolean found,success,active=true,multiple;
    public ScannerOverlay(Context context){super(context);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);setClickable(false);paint.setStyle(Paint.Style.STROKE);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);paint.setStrokeWidth(3*getResources().getDisplayMetrics().density);}
    public void setActive(boolean value){active=value;invalidate();}
    public void setMultiple(boolean value){multiple=value;invalidate();}
    public void track(RectF bounds,boolean valid,long duration){if(bounds==null||bounds.isEmpty())return;target.set(bounds);if(!found)current.set(bounds);found=true;success=valid;expires=SystemClock.uptimeMillis()+duration;invalidate();}
    public void clear(){found=false;success=false;multiple=false;invalidate();}
    boolean isTracking(){return found;}
    RectF drawingBounds(){return new RectF(current);}
    private boolean moving(){return active&&isShown()&&getWindowVisibility()==VISIBLE&&ValueAnimator.areAnimatorsEnabled();}
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);long now=SystemClock.uptimeMillis();if(found&&now>=expires)found=false;
        boolean animate=moving();
        if(!found){
            // Clearly a search effect, with no claim that these coordinates contain a code.
            double phase=animate?now/1250.0:0;
            float w=getWidth()*.62f,h=getHeight()*.24f;
            float x=(getWidth()-w)*(.5f+.42f*(float)Math.sin(phase));
            float y=(getHeight()-h)*(.5f+.42f*(float)Math.sin(phase*.71));
            current.set(x,y,x+w,y+h);
        }else{float t=animate?.35f:1;current.set(current.left+(target.left-current.left)*t,current.top+(target.top-current.top)*t,current.right+(target.right-current.right)*t,current.bottom+(target.bottom-current.bottom)*t);}
        float length=Math.min(current.width(),current.height())*.3f,radius=Math.min(10*getResources().getDisplayMetrics().density,length/2);
        for(int i=0;i<4;i++){
            paint.setColor(found?(success?colors[3]:colors[0]):multiple?colors[2]:colors[i]);paint.setAlpha(255);
            canvas.save();canvas.translate((i==0||i==3)?current.left:current.right,i<2?current.top:current.bottom);canvas.scale((i==0||i==3)?1:-1,i<2?1:-1);
            Path corner=new Path();corner.moveTo(0,length);corner.lineTo(0,radius);corner.quadTo(0,0,radius,0);corner.lineTo(length,0);canvas.drawPath(corner,paint);canvas.restore();
        }
        if(!found&&!multiple){
            float fraction=animate?(float)((now%1600)/1600.0):.5f,y=current.top+current.height()*fraction;
            paint.setShader(new LinearGradient(current.left,y,current.right,y,colors,null,Shader.TileMode.CLAMP));paint.setAlpha(150);canvas.drawLine(current.left+8,y,current.right-8,y,paint);paint.setShader(null);paint.setAlpha(255);
        }
        if(animate)postInvalidateDelayed(33); // At most 30 fps; no loop when paused/hidden/reduced motion.
    }
    @Override protected void onWindowVisibilityChanged(int visibility){super.onWindowVisibilityChanged(visibility);if(visibility==VISIBLE)invalidate();}
    @Override protected void onDetachedFromWindow(){active=false;clear();super.onDetachedFromWindow();}
}
