package br.com.principaiscapas;

import br.com.centralmidia.android.R;

import android.content.*;
import android.database.Cursor;
import android.graphics.*;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import androidx.core.content.FileProvider;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Exportador de PDF otimizado.
 *
 * Diferente do android.graphics.pdf.PdfDocument, esta classe grava cada capa
 * como JPEG de alta qualidade diretamente dentro do PDF (DCTDecode). Isso
 * evita incorporar bitmaps ARGB muito grandes e reduz bastante o tamanho do
 * arquivo sem alterar a proporção nem cortar a capa.
 */
public class PdfExporter {
    // Faixa escolhida para manter excelente leitura/zoom sem carregar pixels
    // desnecessários para um PDF de capas.
    private static final int HIGH_QUALITY_MAX_WIDTH = 2000;
    private static final int HIGH_QUALITY_JPEG_QUALITY = 93;
    private static final int PDF_PAGE_WIDTH = 2000;

    public static String buildFileName(Date date) {
        String[] months={"JAN","FEV","MAR","ABR","MAI","JUN","JUL","AGO","SET","OUT","NOV","DEZ"};
        Calendar cal=Calendar.getInstance(); cal.setTime(date);
        return String.format(Locale.ROOT,"%02d%s - PRINCIPAIS CAPAS.pdf",cal.get(Calendar.DAY_OF_MONTH),months[cal.get(Calendar.MONTH)]);
    }

    public static String export(Context context, List<CoverEntry> entries, Date date) throws Exception {
        List<PreparedPage> pages=new ArrayList<>();
        try {
            Bitmap standard=BitmapFactory.decodeResource(context.getResources(),R.drawable.principais_capas_cover);
            if (standard==null) throw new Exception("Não foi possível carregar a capa padrão");
            try {
                pages.add(preparePage(context,standard,0,HIGH_QUALITY_MAX_WIDTH,HIGH_QUALITY_JPEG_QUALITY));
            } finally {
                if(!standard.isRecycled()) standard.recycle();
            }

            Calendar cal=Calendar.getInstance(); cal.setTime(date);
            boolean weekend=cal.get(Calendar.DAY_OF_WEEK)==Calendar.SATURDAY || cal.get(Calendar.DAY_OF_WEEK)==Calendar.SUNDAY;

            for (CoverEntry e:entries) {
                if (!e.selected || e.coverBitmap==null) continue;
                if (weekend && "VALOR ECONÔMICO".equalsIgnoreCase(e.source.name)) continue;

                Bitmap full=null;
                boolean recycle=false;
                try {
                    if(e.originalImagePath!=null && !e.originalImagePath.isEmpty() && new File(e.originalImagePath).exists()){
                        full=decodeForPdf(e.originalImagePath,HIGH_QUALITY_MAX_WIDTH);
                        recycle=full!=null;
                    }
                    if(full==null) full=e.coverBitmap;
                    int margin=Math.max(0,Math.min(6,e.source.pdfMarginPercent));
                    pages.add(preparePage(context,full,margin,HIGH_QUALITY_MAX_WIDTH,HIGH_QUALITY_JPEG_QUALITY));
                } finally {
                    if(recycle && full!=null && full!=e.coverBitmap && !full.isRecycled()) full.recycle();
                }
            }

            if(pages.isEmpty()) throw new Exception("Nenhuma página disponível para o PDF");

            String fileName=buildFileName(date);
            if (Build.VERSION.SDK_INT>=29) {
                ContentResolver r=context.getContentResolver();
                String relPath=Environment.DIRECTORY_DOWNLOADS+"/Principais Capas";
                try {
                    r.delete(MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                            MediaStore.Downloads.DISPLAY_NAME+"=? AND ("+MediaStore.Downloads.RELATIVE_PATH+"=? OR "+MediaStore.Downloads.RELATIVE_PATH+"=?)",
                            new String[]{fileName,relPath,relPath+"/"});
                } catch (Exception ignored) {}

                ContentValues v=new ContentValues();
                v.put(MediaStore.Downloads.DISPLAY_NAME,fileName);
                v.put(MediaStore.Downloads.MIME_TYPE,"application/pdf");
                v.put(MediaStore.Downloads.RELATIVE_PATH,relPath);
                Uri uri=r.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,v);
                if(uri==null) throw new Exception("Não foi possível criar o PDF");
                try(OutputStream out=r.openOutputStream(uri)){
                    if(out==null) throw new Exception("Não foi possível abrir o destino");
                    writePdf(out,pages);
                }
                return fileName;
            }

            File dir=new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),"Principais Capas");
            if(!dir.exists()&&!dir.mkdirs()) throw new Exception("Não foi possível criar a pasta");
            File file=new File(dir,fileName);
            try(FileOutputStream out=new FileOutputStream(file)){
                writePdf(out,pages);
            }
            return file.getAbsolutePath();
        } finally {
            for(PreparedPage p:pages) {
                try { if(p.jpegFile!=null) p.jpegFile.delete(); } catch(Exception ignored) {}
            }
        }
    }

    public static Uri findExistingPdfUri(Context context, Date date) {
        String fileName=buildFileName(date);
        if (Build.VERSION.SDK_INT>=29) {
            ContentResolver r=context.getContentResolver();
            String[] projection={MediaStore.Downloads._ID};
            String selection=MediaStore.Downloads.DISPLAY_NAME+"=?";
            try (Cursor c=r.query(MediaStore.Downloads.EXTERNAL_CONTENT_URI,projection,selection,new String[]{fileName},MediaStore.Downloads.DATE_ADDED+" DESC")) {
                if(c!=null && c.moveToFirst()) {
                    long id=c.getLong(c.getColumnIndexOrThrow(MediaStore.Downloads._ID));
                    return ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI,id);
                }
            } catch (Exception ignored) {}
            return null;
        }

        File dir=new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),"Principais Capas");
        File file=new File(dir,fileName);
        if(!file.exists()) return null;
        try {
            return FileProvider.getUriForFile(context,context.getPackageName()+".fileprovider",file);
        } catch (Exception e) {
            return null;
        }
    }

    /** Prepara um JPEG RGB de alta qualidade para ser embutido diretamente no PDF. */
    private static PreparedPage preparePage(Context context, Bitmap source, int marginPercent, int maxWidth, int jpegQuality) throws Exception {
        if(source==null || source.isRecycled()) throw new Exception("Imagem inválida ao gerar PDF");

        Bitmap work=source;
        Bitmap scaled=null;
        Bitmap flattened=null;
        try {
            int srcW=Math.max(1,source.getWidth());
            int srcH=Math.max(1,source.getHeight());

            if(srcW>maxWidth) {
                int targetH=Math.max(1,Math.round(srcH*(maxWidth/(float)srcW)));
                scaled=Bitmap.createScaledBitmap(source,maxWidth,targetH,true);
                work=scaled;
            }

            // JPEG não tem alfa. Quando necessário, achata em branco para evitar fundo escuro.
            if(work.hasAlpha()) {
                flattened=Bitmap.createBitmap(work.getWidth(),work.getHeight(),Bitmap.Config.ARGB_8888);
                Canvas canvas=new Canvas(flattened);
                canvas.drawColor(Color.WHITE);
                Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG|Paint.DITHER_FLAG);
                canvas.drawBitmap(work,0,0,paint);
                work=flattened;
            }

            File jpeg=File.createTempFile("capapdf_",".jpg",context.getCacheDir());
            try(FileOutputStream fos=new FileOutputStream(jpeg)) {
                if(!work.compress(Bitmap.CompressFormat.JPEG,jpegQuality,fos)) {
                    throw new Exception("Falha ao comprimir uma capa para o PDF");
                }
            }

            int imageW=Math.max(1,work.getWidth());
            int imageH=Math.max(1,work.getHeight());
            float m=Math.max(0,Math.min(6,marginPercent))/100f;
            int pad=Math.round(PDF_PAGE_WIDTH*m);
            int drawW=Math.max(1,PDF_PAGE_WIDTH-pad*2);
            int drawH=Math.max(1,Math.round(imageH*(drawW/(float)imageW)));
            int pageH=Math.max(1,drawH+pad*2);
            return new PreparedPage(jpeg,imageW,imageH,PDF_PAGE_WIDTH,pageH,pad,drawW,drawH);
        } finally {
            if(flattened!=null && flattened!=source && !flattened.isRecycled()) flattened.recycle();
            if(scaled!=null && scaled!=source && scaled!=flattened && !scaled.isRecycled()) scaled.recycle();
        }
    }

    /**
     * Escreve um PDF 1.4 simples, com cada JPEG usando /DCTDecode diretamente.
     * Assim o JPEG não é transformado novamente em bitmap bruto dentro do PDF.
     */
    private static void writePdf(OutputStream destination, List<PreparedPage> pages) throws Exception {
        CountingOutputStream out=new CountingOutputStream(new BufferedOutputStream(destination,64*1024));
        int pageCount=pages.size();
        int totalObjects=2+(pageCount*3);
        long[] offsets=new long[totalObjects+1];

        writeAscii(out,"%PDF-1.4\n%\u00E2\u00E3\u00CF\u00D3\n");

        // 1: Catalog
        offsets[1]=out.getCount();
        writeAscii(out,"1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n");

        // 2: Pages
        offsets[2]=out.getCount();
        StringBuilder kids=new StringBuilder();
        for(int i=0;i<pageCount;i++) kids.append(3+i*3).append(" 0 R ");
        writeAscii(out,"2 0 obj\n<< /Type /Pages /Count "+pageCount+" /Kids [ "+kids+"] >>\nendobj\n");

        for(int i=0;i<pageCount;i++) {
            PreparedPage p=pages.get(i);
            int pageObj=3+i*3;
            int contentObj=pageObj+1;
            int imageObj=pageObj+2;

            offsets[pageObj]=out.getCount();
            writeAscii(out,pageObj+" 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 "+p.pageW+" "+p.pageH+"] "+
                    "/Resources << /XObject << /Im0 "+imageObj+" 0 R >> >> /Contents "+contentObj+" 0 R >>\nendobj\n");

            String content="q\n"+p.drawW+" 0 0 "+p.drawH+" "+p.pad+" "+p.pad+" cm\n/Im0 Do\nQ\n";
            byte[] contentBytes=content.getBytes(StandardCharsets.US_ASCII);
            offsets[contentObj]=out.getCount();
            writeAscii(out,contentObj+" 0 obj\n<< /Length "+contentBytes.length+" >>\nstream\n");
            out.write(contentBytes);
            writeAscii(out,"endstream\nendobj\n");

            long imageLen=p.jpegFile.length();
            offsets[imageObj]=out.getCount();
            writeAscii(out,imageObj+" 0 obj\n<< /Type /XObject /Subtype /Image /Width "+p.imageW+" /Height "+p.imageH+
                    " /ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode /Interpolate true /Length "+imageLen+" >>\nstream\n");
            try(InputStream in=new BufferedInputStream(new FileInputStream(p.jpegFile),64*1024)) {
                byte[] buffer=new byte[64*1024];
                int n;
                while((n=in.read(buffer))!=-1) out.write(buffer,0,n);
            }
            writeAscii(out,"\nendstream\nendobj\n");
        }

        long xref=out.getCount();
        writeAscii(out,"xref\n0 "+(totalObjects+1)+"\n");
        writeAscii(out,"0000000000 65535 f \n");
        for(int i=1;i<=totalObjects;i++) {
            writeAscii(out,String.format(Locale.ROOT,"%010d 00000 n \n",offsets[i]));
        }
        writeAscii(out,"trailer\n<< /Size "+(totalObjects+1)+" /Root 1 0 R >>\nstartxref\n"+xref+"\n%%EOF\n");
        out.flush();
    }

    private static void writeAscii(OutputStream out,String text) throws IOException {
        // ISO-8859-1 preserva a linha binária do cabeçalho PDF e continua 1 byte/caractere.
        out.write(text.getBytes(StandardCharsets.ISO_8859_1));
    }

    private static Bitmap decodeForPdf(String path,int targetWidth){
        BitmapFactory.Options b=new BitmapFactory.Options();
        b.inJustDecodeBounds=true;
        BitmapFactory.decodeFile(path,b);
        if(b.outWidth<=0||b.outHeight<=0) return null;
        int sample=1;
        while(b.outWidth/(sample*2)>=targetWidth) sample*=2;
        BitmapFactory.Options o=new BitmapFactory.Options();
        o.inSampleSize=Math.max(1,sample);
        o.inPreferredConfig=Bitmap.Config.ARGB_8888;
        return BitmapFactory.decodeFile(path,o);
    }

    private static class PreparedPage {
        final File jpegFile;
        final int imageW,imageH,pageW,pageH,pad,drawW,drawH;
        PreparedPage(File jpegFile,int imageW,int imageH,int pageW,int pageH,int pad,int drawW,int drawH){
            this.jpegFile=jpegFile;
            this.imageW=imageW;
            this.imageH=imageH;
            this.pageW=pageW;
            this.pageH=pageH;
            this.pad=pad;
            this.drawW=drawW;
            this.drawH=drawH;
        }
    }

    private static class CountingOutputStream extends OutputStream {
        private final OutputStream out;
        private long count=0;
        CountingOutputStream(OutputStream out){ this.out=out; }
        long getCount(){ return count; }
        @Override public void write(int b) throws IOException { out.write(b); count++; }
        @Override public void write(byte[] b,int off,int len) throws IOException { out.write(b,off,len); count+=len; }
        @Override public void flush() throws IOException { out.flush(); }
        @Override public void close() throws IOException { out.close(); }
    }
}
