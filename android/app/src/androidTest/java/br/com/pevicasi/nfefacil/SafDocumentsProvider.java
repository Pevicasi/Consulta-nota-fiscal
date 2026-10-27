package br.com.pevicasi.nfefacil;

import android.database.Cursor;
import android.database.MatrixCursor;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.provider.DocumentsProvider;
import java.io.*;
import java.nio.charset.StandardCharsets;

/** Real SAF document provider for the emulator, packaged ONLY in androidTest. */
public class SafDocumentsProvider extends DocumentsProvider {
    static final String AUTHORITY="br.com.pevicasi.nfefacil.test.documents";
    private static final String[] DOCS={DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_DISPLAY_NAME,DocumentsContract.Document.COLUMN_MIME_TYPE,DocumentsContract.Document.COLUMN_FLAGS,DocumentsContract.Document.COLUMN_SIZE,DocumentsContract.Document.COLUMN_LAST_MODIFIED};
    public static String xml(int number) {
        return "<NFe><infNFe><ide><nNF>"+(1000+number)+"</nNF></ide><emit><xNome>Fornecedor "+number+"</xNome></emit><det nItem='1'><prod><xProd>Produto Android "+number+"</xProd><qCom>2</qCom><vUnCom>"+(10*number)+"</vUnCom><vProd>"+(20*number)+"</vProd><vDesc>"+(5.5*number)+"</vDesc></prod><imposto><ICMS><ICMS10><vICMSST>"+(4*number)+"</vICMSST></ICMS10></ICMS></imposto></det><total><ICMSTot><vProd>"+(20*number)+"</vProd><vDesc>"+(5.5*number)+"</vDesc><vNF>"+(18.5*number)+"</vNF></ICMSTot></total></infNFe></NFe>";
    }
    private static String content(String id) {
        if(id.equals("empty.xml"))return "";
        if(id.equals("invalid.xml"))return "<broken>";
        if(id.matches("nota[1-5]\\.xml"))return xml(id.charAt(4)-'0');
        throw new IllegalArgumentException("Unknown fixture");
    }
    @Override public boolean onCreate(){return true;}
    @Override public Cursor queryRoots(String[] projection) {
        String[] columns=projection!=null?projection:new String[]{"root_id","flags","title","document_id","mime_types","available_bytes"};
        MatrixCursor cursor=new MatrixCursor(columns);MatrixCursor.RowBuilder row=cursor.newRow();
        for(String column:columns)switch(column){
            case "root_id":case "document_id":row.add(column,"root");break;
            case "title":row.add(column,"NF-e XML fixture");break;
            case "flags":row.add(column,DocumentsContract.Root.FLAG_LOCAL_ONLY);break;
            case "mime_types":row.add(column,"application/xml");break;
            case "available_bytes":row.add(column,1000000L);break;
            default:row.add(column,null);
        }
        return cursor;
    }
    private void add(MatrixCursor cursor,String id) {
        MatrixCursor.RowBuilder row=cursor.newRow();
        for(String column:cursor.getColumnNames())switch(column){
            case DocumentsContract.Document.COLUMN_DOCUMENT_ID:case DocumentsContract.Document.COLUMN_DISPLAY_NAME:row.add(column,id);break;
            case "mime_type":row.add(column,id.equals("root")?DocumentsContract.Document.MIME_TYPE_DIR:"application/xml");break;
            case "flags":row.add(column,0);break;
            case DocumentsContract.Document.COLUMN_SIZE:row.add(column,id.equals("root")?0:content(id).getBytes(StandardCharsets.UTF_8).length);break;
            case "last_modified":row.add(column,0L);break;
            default:row.add(column,null);
        }
    }
    @Override public Cursor queryDocument(String id,String[] projection){MatrixCursor c=new MatrixCursor(projection!=null?projection:DOCS);add(c,id);return c;}
    @Override public Cursor queryChildDocuments(String parent,String[] projection,String sort){
        MatrixCursor c=new MatrixCursor(projection!=null?projection:DOCS);
        for(int i=1;i<=5;i++)add(c,"nota"+i+".xml");add(c,"empty.xml");add(c,"invalid.xml");return c;
    }
    @Override public ParcelFileDescriptor openDocument(String id,String mode,CancellationSignal signal)throws FileNotFoundException {
        if(!"r".equals(mode))throw new FileNotFoundException("Read only");
        try {
            ParcelFileDescriptor[] pipe=ParcelFileDescriptor.createPipe();
            new Thread(()->{try(OutputStream out=new ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])){out.write(content(id).getBytes(StandardCharsets.UTF_8));}catch(IOException ignored){}}).start();
            return pipe[0];
        }catch(IOException error){throw new FileNotFoundException(error.getMessage());}
    }
}
