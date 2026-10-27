package br.com.pevicasi.nfefacil;

import android.content.Context;
import android.graphics.*;
import android.os.SystemClock;
import android.view.View;

/** Fixed four-color idle guide; blue/green corners use detector coordinates only. */
public final class ScannerOverlay extends View {
 private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
 private final RectF current=new RectF();
 private final int[] colors={Color.rgb(66,133,244),Color.rgb(234,67,53),Color.rgb(251,188,5),Color.rgb(52,168,83)};
 private final Runnable expiration=this::invalidate;
 private long expires;private boolean found,success,active=true;
 public ScannerOverlay(Context context){super(context);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);setClickable(false);paint.setStyle(Paint.Style.STROKE);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);paint.setStrokeWidth(3*getResources().getDisplayMetrics().density);}
 public void setActive(boolean value){active=value;removeCallbacks(expiration);if(active&&found)postDelayed(expiration,Math.max(1,expires-SystemClock.uptimeMillis()));invalidate();}
 public void setMultiple(boolean value){invalidate();} // Ambiguous keys never imply a default geometric target.
 public void track(RectF bounds,boolean valid,long duration){if(bounds==null||bounds.isEmpty())return;current.set(bounds);found=true;success=valid;expires=SystemClock.uptimeMillis()+duration;removeCallbacks(expiration);if(active)postDelayed(expiration,duration);invalidate();}
 public void clear(){found=false;success=false;removeCallbacks(expiration);invalidate();}
 boolean isTracking(){return found;}
 RectF drawingBounds(){return new RectF(current);}
 @Override protected void onDraw(Canvas canvas){
  super.onDraw(canvas);if(found&&SystemClock.uptimeMillis()>=expires)found=false;
  if(!found)current.set(getWidth()*.15f,getHeight()*.4f,getWidth()*.85f,getHeight()*.6f);
  float density=getResources().getDisplayMetrics().density,length=Math.min(14*density,Math.min(current.width(),current.height())*.45f),radius=Math.min(6*density,length/2);
  for(int i=0;i<4;i++){
   paint.setColor(found?(success?colors[3]:colors[0]):colors[i]);
   canvas.save();canvas.translate((i==0||i==3)?current.left:current.right,i<2?current.top:current.bottom);canvas.scale((i==0||i==3)?1:-1,i<2?1:-1);
   Path corner=new Path();corner.moveTo(0,length);corner.lineTo(0,radius);corner.quadTo(0,0,radius,0);corner.lineTo(length,0);canvas.drawPath(corner,paint);canvas.restore();
  }
  // No time-based search/interpolation or recurring render loop. Detection events move the corners.
 }
 @Override protected void onDetachedFromWindow(){active=false;clear();super.onDetachedFromWindow();}
}
