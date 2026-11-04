package jp.jrdroid;

import java.io.*;
import java.util.zip.*;

/** Preserves the capture bytes in a ZIP; failed writes never publish a result. */
final class IqCaptureExport {
    static final long MAX_CAPTURE_BYTES=IqCaptureBuffer.LIMIT_BYTES+4096L;
    interface Target {
        OutputStream open() throws IOException;
        void publish() throws IOException;
        void abort() throws IOException;
    }
    static void write(File capture,Target target) throws IOException {
        try {
            if(!capture.isFile() || capture.length()==0 || capture.length()>MAX_CAPTURE_BYTES)
                throw new IOException("保存できるIQファイルがありません");
            // Keep this inode open while the receiver may atomically replace latest.
            try(InputStream in=new FileInputStream(capture);
                OutputStream out=target.open()) {
                if(out==null) throw new IOException("保存先を開けません");
                try(ZipOutputStream zip=new ZipOutputStream(new BufferedOutputStream(out))) {
                    zip.setLevel(Deflater.BEST_SPEED);
                    zip.putNextEntry(new ZipEntry("capture.jrd-iq"));
                    byte[] buffer=new byte[32768];long total=0;int n;
                    while((n=in.read(buffer))!=-1) {
                        total+=n;
                        if(total>MAX_CAPTURE_BYTES) throw new IOException("IQファイルが大きすぎます");
                        zip.write(buffer,0,n);
                    }
                    if(total==0) throw new IOException("IQファイルが空です");
                    zip.closeEntry();
                }
            }
            target.publish();
        } catch(IOException | RuntimeException e) {
            try { target.abort(); } catch(IOException | RuntimeException cleanup) { e.addSuppressed(cleanup); }
            throw e;
        }
    }
    private IqCaptureExport() {}
}
