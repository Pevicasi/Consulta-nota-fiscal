package br.com.pevicasi.nfefacil;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import android.os.CancellationSignal;
import android.os.OperationCanceledException;
import android.os.ParcelFileDescriptor;
import android.print.PageRange;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintDocumentInfo;
import android.os.Bundle;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/** A frozen name/visible-price snapshot, independent of the fiscal calculation. */
final class PriceListPrinter extends PrintDocumentAdapter {
    private final List<String[]> rows = new ArrayList<>();
    private PrintAttributes current = attributes();
    PriceListPrinter(JSONArray snapshot) throws JSONException {
        for (int i=0;i<snapshot.length();i++) {
            JSONObject row=snapshot.getJSONObject(i);
            rows.add(new String[]{row.getString("name"),row.getString("price")});
        }
    }
    static PrintAttributes attributes() {
        return new PrintAttributes.Builder()
            .setMediaSize(new PrintAttributes.MediaSize("RECEIPT_80MM","Bobina 80 × 297 mm",3150,11693))
            .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
            .setColorMode(PrintAttributes.COLOR_MODE_MONOCHROME).build();
    }
    private static TextPaint textPaint() {
        TextPaint p=new TextPaint(Paint.ANTI_ALIAS_FLAG);p.setColor(Color.BLACK);p.setTextSize(10.5f);return p;
    }
    private static List<String> wrap(String text,int width,TextPaint paint) {
        StaticLayout layout=StaticLayout.Builder.obtain(text,0,text.length(),paint,Math.max(1,width))
            .setAlignment(Layout.Alignment.ALIGN_NORMAL).setIncludePad(false).build();
        List<String> lines=new ArrayList<>();
        for(int i=0;i<layout.getLineCount();i++)lines.add(text.substring(layout.getLineStart(i),layout.getLineEnd(i)).replaceAll("[\\r\\n]+$",""));
        if(lines.isEmpty())lines.add("");return lines;
    }
    private static final class Line {
        final String name,price;final float y;
        Line(String n,String p,float baseline){name=n;price=p;y=baseline;}
    }
    private static final class Page {final List<Line> lines=new ArrayList<>();}
    private static final class Receipt {
        int width,height;float left,right,bottomMargin;final List<Page> pages=new ArrayList<>();
    }
    private Receipt layout(PrintAttributes attrs) {
        PrintAttributes.MediaSize media=attrs.getMediaSize();if(media==null)media=attributes().getMediaSize();
        PrintAttributes.Margins margins=attrs.getMinMargins();if(margins==null)margins=PrintAttributes.Margins.NO_MARGINS;
        Receipt r=new Receipt();r.width=Math.round(media.getWidthMils()*72f/1000);r.height=Math.round(media.getHeightMils()*72f/1000);
        // ~4 mm on either side keeps content inside the TM-T20's ~72 mm printable area.
        r.left=margins.getLeftMils()*72f/1000+12;r.right=r.width-margins.getRightMils()*72f/1000-12;
        float top=margins.getTopMils()*72f/1000+8,bottom=r.height-margins.getBottomMils()*72f/1000-8;
        r.bottomMargin=margins.getBottomMils()*72f/1000+8;
        TextPaint paint=textPaint();Paint.FontMetrics fm=paint.getFontMetrics();float step=(float)Math.ceil(fm.bottom-fm.top)+2;
        if(r.right-r.left<40||bottom-top<step)throw new IllegalArgumentException("Área de impressão insuficiente.");
        int priceWidth=1;for(String[] row:rows)priceWidth=Math.max(priceWidth,(int)Math.ceil(paint.measureText(row[1])));
        priceWidth=Math.min(priceWidth+2,(int)((r.right-r.left)*.45f));int nameWidth=(int)(r.right-r.left)-priceWidth-8;
        Page page=new Page();r.pages.add(page);float y=top;
        for(String[] row:rows) {
            List<String> names=wrap(row[0],nameWidth,paint),prices=wrap(row[1],priceWidth,paint);int count=Math.max(names.size(),prices.size());float height=count*step+4;
            if(!page.lines.isEmpty()&&height<=bottom-top&&y+height>bottom){page=new Page();r.pages.add(page);y=top;}
            for(int i=0;i<count;i++) {
                // Exceptionally long names continue only at whole line boundaries.
                if(y+step>bottom){page=new Page();r.pages.add(page);y=top;}
                page.lines.add(new Line(i<names.size()?names.get(i):"",i<prices.size()?prices.get(i):"",y-fm.top));y+=step;
            }
            y+=4;
        }
        return r;
    }
    int pageCount(PrintAttributes attrs) {return layout(attrs).pages.size();}
    void writePdf(OutputStream output,PrintAttributes attrs,PageRange[] requested,CancellationSignal cancel) throws IOException {
        Receipt receipt=layout(attrs);TextPaint namePaint=textPaint(),pricePaint=textPaint();pricePaint.setTextAlign(Paint.Align.RIGHT);
        PdfDocument pdf=new PdfDocument();
        try {
            for(int i=0;i<receipt.pages.size();i++) {
                cancel.throwIfCanceled();boolean selected=false;for(PageRange range:requested)if(i>=range.getStart()&&i<=range.getEnd()){selected=true;break;}if(!selected)continue;
                Page content=receipt.pages.get(i);int height=receipt.height;
                if(i==receipt.pages.size()-1&&!content.lines.isEmpty()) {
                    float bottom=content.lines.get(content.lines.size()-1).y+namePaint.getFontMetrics().bottom+receipt.bottomMargin;
                    height=Math.min(height,Math.max(40,(int)Math.ceil(bottom)));
                }
                PdfDocument.Page page=pdf.startPage(new PdfDocument.PageInfo.Builder(receipt.width,height,i).create());Canvas canvas=page.getCanvas();
                for(Line line:receipt.pages.get(i).lines){canvas.drawText(line.name,receipt.left,line.y,namePaint);canvas.drawText(line.price,receipt.right,line.y,pricePaint);}
                pdf.finishPage(page);
            }
            cancel.throwIfCanceled();pdf.writeTo(output);
        } finally {pdf.close();}
    }
    @Override public void onLayout(PrintAttributes oldAttributes,PrintAttributes newAttributes,CancellationSignal cancel,LayoutResultCallback result,Bundle extras) {
        if(cancel.isCanceled()){result.onLayoutCancelled();return;}
        try{current=newAttributes;result.onLayoutFinished(new PrintDocumentInfo.Builder("Lista de preços.pdf").setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).setPageCount(pageCount(current)).build(),!newAttributes.equals(oldAttributes));}
        catch(IllegalArgumentException e){result.onLayoutFailed(e.getMessage());}
    }
    @Override public void onWrite(PageRange[] pages,ParcelFileDescriptor destination,CancellationSignal cancel,WriteResultCallback result) {
        try(FileOutputStream out=new FileOutputStream(destination.getFileDescriptor())){writePdf(out,current,pages,cancel);result.onWriteFinished(pages);}
        catch(OperationCanceledException e){result.onWriteCancelled();}
        catch(IOException|IllegalArgumentException e){result.onWriteFailed("Não foi possível gerar a lista de preços.");}
    }
}
