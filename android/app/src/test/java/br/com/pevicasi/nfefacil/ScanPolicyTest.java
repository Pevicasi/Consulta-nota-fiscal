package br.com.pevicasi.nfefacil;
import org.junit.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;
public class ScanPolicyTest {
 @Test public void fastPathDoesNotRefineUnambiguousKeys(){assertFalse(ScanPolicy.refine(1,false,true));assertFalse(ScanPolicy.refine(2,false,true));assertTrue(ScanPolicy.refine(1,true,true));assertTrue(ScanPolicy.refine(0,true,true));assertFalse(ScanPolicy.refine(0,true,false));}
 @Test public void refinementHasCountAndTimeBudgets(){assertTrue(ScanPolicy.probe(7,2000));assertFalse(ScanPolicy.probe(8,0));assertFalse(ScanPolicy.probe(0,2001));}
 @Test public void frameCompletionReleasesOnceForCompetingOutcomes()throws Exception{AtomicInteger count=new AtomicInteger();CloseOnce close=new CloseOnce(count::incrementAndGet);Thread[] threads=new Thread[8];for(int i=0;i<threads.length;i++){threads[i]=new Thread(close);threads[i].start();}for(Thread t:threads)t.join();close.run();assertEquals(1,count.get());}
}
