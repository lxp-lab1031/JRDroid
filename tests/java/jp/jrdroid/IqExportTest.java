package jp.jrdroid;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

public final class IqExportTest {
    private static void check(boolean ok) { if(!ok) throw new AssertionError(); }
    private static final class Target implements IqCaptureExport.Target {
        final String failure;
        final ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        boolean opened,closed,published,aborted;
        Target(String failure) { this.failure=failure; }
        public OutputStream open() throws IOException {
            opened=true;
            if(failure.equals("open")) throw new IOException("open failed");
            if(failure.equals("null")) return null;
            return new OutputStream() {
                public void write(int b) throws IOException {
                    if(failure.equals("write")) throw new IOException("write failed");
                    bytes.write(b);
                }
                public void write(byte[] b,int off,int len) throws IOException {
                    if(failure.equals("write")) throw new IOException("write failed");
                    bytes.write(b,off,len);
                }
                public void close() throws IOException {
                    closed=true;
                    if(failure.equals("close")) throw new IOException("close failed");
                }
            };
        }
        public void publish() throws IOException {
            check(closed);
            if(failure.equals("publish") || failure.equals("rollback")) throw new IOException("publish failed");
            published=true;
        }
        public void abort() throws IOException {
            aborted=true;bytes.reset();
            if(failure.equals("rollback")) throw new IOException("cleanup failed");
        }
    }
    private static byte[] captureFromZip(byte[] zipped) throws IOException {
        try(ZipInputStream zip=new ZipInputStream(new ByteArrayInputStream(zipped))) {
            ZipEntry entry=zip.getNextEntry();check(entry!=null && entry.getName().equals("capture.jrd-iq"));
            byte[] captured=zip.readAllBytes();check(zip.getNextEntry()==null);return captured;
        }
    }
    public static void main(String[] args) throws Exception {
        Path folder=Files.createTempDirectory("jrd-iq-export-test");
        try {
            File source=folder.resolve("latest.jrd-iq").toFile();
            for(boolean full:new boolean[]{false,true}) {
                IqCaptureBuffer capture=new IqCaptureBuffer(146481250);
                byte[] raw=new byte[full?IqCaptureBuffer.LIMIT_BYTES:137];
                for(int i=0;i<raw.length;i++) raw[i]=(byte)(i*31);
                capture.append(raw,raw.length);
                try(OutputStream out=new FileOutputStream(source)) {capture.writeTo(out);}
                byte[] expected=Files.readAllBytes(source.toPath());
                Target target=new Target("");IqCaptureExport.write(source,target);
                check(target.published && target.closed && !target.aborted);
                check(Arrays.equals(expected,captureFromZip(target.bytes.toByteArray())));
                check(Arrays.equals(expected,Files.readAllBytes(source.toPath())));
            }
            System.out.println("PASS: exported ZIP preserves complete/partial IQ metadata and every payload byte");
            for(String failure:new String[]{"open","null","write","close","publish","rollback"}) {
                Target target=new Target(failure);
                try {IqCaptureExport.write(source,target);throw new AssertionError();}
                catch(IOException e) {
                    check(target.aborted && !target.published && target.bytes.size()==0);
                    if(failure.equals("rollback")) check(e.getSuppressed().length==1);
                }
            }
            System.out.println("PASS: export open/write/close/publish failures roll back without false success");
            for(int kind=0;kind<3;kind++) {
                File invalid=folder.resolve("invalid-"+kind).toFile();
                if(kind==1) Files.write(invalid.toPath(),new byte[0]);
                if(kind==2) try(RandomAccessFile out=new RandomAccessFile(invalid,"rw")) {out.setLength(IqCaptureExport.MAX_CAPTURE_BYTES+1);}
                Target target=new Target("");
                try {IqCaptureExport.write(invalid,target);throw new AssertionError();}
                catch(IOException e) {check(!target.opened && target.aborted && !target.published);}
            }
            System.out.println("PASS: missing, empty and oversized captures do not create export output");
            if(args.length>1) {
                Path destination=Path.of(args[1]);File input=new File(args[0]);
                IqCaptureExport.write(input,new IqCaptureExport.Target() {
                    public OutputStream open() throws IOException {return Files.newOutputStream(destination);}
                    public void publish() {}
                    public void abort() throws IOException {Files.deleteIfExists(destination);}
                });
                byte[] original=Files.readAllBytes(input.toPath());
                Path persisted=folder.resolve("saved.zip");Files.copy(destination,persisted);
                File temporary=folder.resolve("temporary-capture.jrd-iq").toFile();Files.write(temporary.toPath(),original);
                Target frozen=new Target("");IqCaptureExport.write(temporary,frozen);Files.delete(temporary.toPath());
                check(Arrays.equals(original,captureFromZip(Files.readAllBytes(persisted))));
                check(Arrays.equals(original,captureFromZip(frozen.bytes.toByteArray())));
            }
            System.out.println("PASS: saved ZIP is independent of the receiver's temporary capture file");
        } finally {
            try(java.util.stream.Stream<Path> paths=Files.walk(folder)) {
                paths.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
            }
        }
    }
}
