package br.com.pevicasi.nfefacil;
final class ScanPolicy {
 static final int MAX_ROIS=8;
 static final long ROI_BUDGET_MS=2000;
 static boolean refine(int valid,boolean uncertain,boolean geometry){return geometry&&(valid==0||uncertain);}
 static boolean probe(int index,long elapsed){return index<MAX_ROIS&&elapsed<=ROI_BUDGET_MS;}
}
