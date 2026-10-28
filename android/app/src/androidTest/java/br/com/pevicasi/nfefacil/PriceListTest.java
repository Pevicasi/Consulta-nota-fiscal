package br.com.pevicasi.nfefacil;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.graphics.RectF;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.pdf.PdfRenderer;
import android.graphics.pdf.content.PdfPageTextContent;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.print.PageRange;
import android.print.PrintAttributes;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.Until;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@android.annotation.TargetApi(35)
@RunWith(AndroidJUnit4.class)
public class PriceListTest {
 private static String js(ActivityScenario<MainActivity> s,String code)throws Exception{return AppIntegrationTest.js(s,code);}
 private static String fixture(String name)throws Exception{try(InputStream in=InstrumentationRegistry.getInstrumentation().getContext().getAssets().open(name)){return new String(in.readAllBytes(),StandardCharsets.UTF_8);}}
 @Test public void priceOnlyReceiptUsesExistingSelectedPricesAndPrintsAllRowsAcross80mmPages()throws Exception{
  Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
  PackageInfo info=context.getPackageManager().getPackageInfo(context.getPackageName(),0);
  assertEquals("br.com.pevicasi.nfefacil",context.getPackageName());
  assertEquals("Consulta Nota Fiscal",context.getPackageManager().getApplicationLabel(info.applicationInfo).toString());
  assertEquals("1.4.3",info.versionName);assertEquals(11,info.getLongVersionCode());

  JSONArray rows=new JSONArray();
  for(int i=0;i<140;i++)rows.put(new JSONObject().put("name",String.format(Locale.ROOT,"ITEM-%03d Produto com nome longo para quebrar naturalmente sem sobrepor o preço na bobina térmica",i)).put("price",i%3==0?"R$ 20,40 / kg":"R$ 7,83 / UN").put("cost","CUSTO_PROIBIDO"));
  PriceListPrinter receipt=new PriceListPrinter(rows);
  PrintAttributes attributes=PriceListPrinter.attributes();
  assertNotNull(attributes.getMediaSize());
  assertEquals(3150,attributes.getMediaSize().getWidthMils());
  assertEquals(11693,attributes.getMediaSize().getHeightMils());
  File directory=new File(context.getExternalFilesDir(null),"appearance-evidence");assertTrue(directory.isDirectory()||directory.mkdirs());
  File pdf=new File(directory,"price-list-80mm.pdf");
  try(FileOutputStream out=new FileOutputStream(pdf)){receipt.writePdf(out,attributes,new PageRange[]{PageRange.ALL_PAGES},new CancellationSignal());}
  StringBuilder printed=new StringBuilder();
  try(ParcelFileDescriptor descriptor=ParcelFileDescriptor.open(pdf,ParcelFileDescriptor.MODE_READ_ONLY);PdfRenderer renderer=new PdfRenderer(descriptor)){
   assertTrue("All 140 products require multiple content pages",renderer.getPageCount()>1);
   assertEquals(receipt.pageCount(attributes),renderer.getPageCount());
   for(int i=0;i<renderer.getPageCount();i++)try(PdfRenderer.Page page=renderer.openPage(i)){
    assertTrue("80 mm width must remain approximately 227 points",Math.abs(page.getWidth()-227)<=1);
    assertTrue("Each receipt page must fit the configured 297 mm maximum",page.getHeight()>0&&page.getHeight()<=843);
    if(i==0||i==renderer.getPageCount()-1){
     Bitmap preview=Bitmap.createBitmap(page.getWidth()*3,page.getHeight()*3,Bitmap.Config.ARGB_8888);preview.eraseColor(Color.WHITE);
     Matrix scale=new Matrix();scale.setScale(3,3);page.render(preview,null,scale,PdfRenderer.Page.RENDER_MODE_FOR_PRINT);
     try(FileOutputStream out=new FileOutputStream(new File(directory,i==0?"price-list-80mm-first.png":"price-list-80mm-last.png"))){assertTrue(preview.compress(Bitmap.CompressFormat.PNG,100,out));}finally{preview.recycle();}
    }
    for(PdfPageTextContent text:page.getTextContents()){
     printed.append(text.getText()).append('\n');
     for(RectF bound:text.getBounds())assertTrue("Printed text must fit inside paper: "+bound,bound.left>=-.5f&&bound.top>=-.5f&&bound.right<=page.getWidth()+.5f&&bound.bottom<=page.getHeight()+.5f);
    }
   }
  }
  String text=printed.toString();
  for(int i=0;i<140;i++)assertTrue("Product omitted from multipage receipt: "+i,text.contains(String.format(Locale.ROOT,"ITEM-%03d",i)));
  assertTrue(text.contains("20,40"));assertTrue(text.contains("kg"));assertTrue(text.contains("7,83"));assertFalse(text.contains("CUSTO_PROIBIDO"));

  File shortPdf=new File(directory,"price-list-short-80mm.pdf");
  PriceListPrinter shortReceipt=new PriceListPrinter(new JSONArray().put(new JSONObject().put("name","Produto curto").put("price","R$ 20,40 / kg")));
  try(FileOutputStream out=new FileOutputStream(shortPdf)){shortReceipt.writePdf(out,attributes,new PageRange[]{PageRange.ALL_PAGES},new CancellationSignal());}
  try(ParcelFileDescriptor descriptor=ParcelFileDescriptor.open(shortPdf,ParcelFileDescriptor.MODE_READ_ONLY);PdfRenderer renderer=new PdfRenderer(descriptor)){
   assertEquals("A short receipt stays on one content-sized page",1,renderer.getPageCount());
   try(PdfRenderer.Page page=renderer.openPage(0)){
    assertTrue(Math.abs(page.getWidth()-227)<=1);
    assertTrue("Short receipt height follows content rather than leaving 297 mm of paper",page.getHeight()>0&&page.getHeight()<842);
    Bitmap preview=Bitmap.createBitmap(page.getWidth()*3,page.getHeight()*3,Bitmap.Config.ARGB_8888);preview.eraseColor(Color.WHITE);Matrix scale=new Matrix();scale.setScale(3,3);page.render(preview,null,scale,PdfRenderer.Page.RENDER_MODE_FOR_PRINT);
    try(FileOutputStream out=new FileOutputStream(new File(directory,"price-list-short-80mm.png"))){assertTrue(preview.compress(Bitmap.CompressFormat.PNG,100,out));}finally{preview.recycle();}
   }
  }

  UiDevice device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
  try(ActivityScenario<MainActivity> s=ActivityScenario.launch(MainActivity.class)){
   AppIntegrationTest.waitForJs(s,"window.__compactInvoice && typeof acceptNativeXmlDocuments==='function' && !!document.getElementById('printPriceListBtn')");
   js(s,"localStorage.clear();costFactors={};startNewConsultation();loadedXmls=[];markupPercentInput.value='20';acceptNativeXmlDocuments([{name:'one.xml',text:"+JSONObject.quote(fixture("dual-quantity.xml"))+"},{name:'two.xml',text:"+JSONObject.quote(fixture("acquisition.xml"))+"}]);showNote(1);true");
   AppIntegrationTest.waitForJs(s,"currentItems.length===5 && activeNoteIndex===1");
   assertEquals("true",js(s,"document.querySelectorAll('#printPriceListBtn').length===1 && document.getElementById('printPriceListBtn').textContent==='Imprimir lista de preços' && document.querySelectorAll('thead th').length===17"));
   assertEquals("true",js(s,"(()=>{document.querySelector('td[data-unit-price=\"4\"] strong').textContent='R$ 123,45 / kg';NfeCost.price=()=>{throw new Error('Do not recalculate receipt prices')};NfeCost.calculate=()=>{throw new Error('Do not recalculate receipt costs')};return currentItems.length===5&&activeNoteIndex===1;})()"));
   js(s,"document.getElementById('printPriceListBtn').scrollIntoView({block:'center',behavior:'instant'});true");
   AppearanceTest.screenshot("price-list-button-selected-note",s);
   js(s,"document.getElementById('printPriceListBtn').click();true");
   assertTrue("Android's existing print flow must open without adding a printer permission",device.wait(Until.hasObject(By.pkg("com.android.printspooler")),15000));
   assertTrue(device.takeScreenshot(new File(directory,"price-list-android-print-dialog.png")));
   device.pressBack();
  }
 }
}
