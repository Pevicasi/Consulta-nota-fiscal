package br.com.pevicasi.nfefacil;
import android.os.SystemClock;
import android.util.Log;
/** Timing only: never log text, keys, pictures, URIs or coordinates. */
final class ScanMetrics {
    static void record(String stage,long start,int probes){Log.d("NfeScanTiming",stage+" ms="+(SystemClock.elapsedRealtime()-start)+" probes="+probes);}
}
