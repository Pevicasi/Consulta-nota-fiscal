package br.com.pevicasi.nfefacil;
import java.util.concurrent.atomic.AtomicBoolean;
/** Completion ownership for success/failure/cancellation; never release an image twice. */
final class CloseOnce implements Runnable {
 private final AtomicBoolean closed=new AtomicBoolean();private final Runnable close;
 CloseOnce(Runnable close){this.close=close;}
 @Override public void run(){if(closed.compareAndSet(false,true))close.run();}
}
