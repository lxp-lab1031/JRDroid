package jp.jrdroid;

import java.io.*;
import java.nio.file.Files;
import java.util.*;

/** Immutable per-share copies prevent old URI grants from exposing later captures. */
final class IqCaptureFiles {
    static File latest(File cache) { return new File(cache,"iq-capture/latest.jrd-iq"); }
    static File resolveShare(File cache,String name) throws FileNotFoundException {
        if(name==null || !name.matches("iq-[0-9a-f-]{36}\\.jrd-iq")) throw new FileNotFoundException("Unknown capture");
        return new File(new File(cache,"iq-shares"),name);
    }
    static File prepareShare(File cache) throws IOException {
        File folder=new File(cache,"iq-shares");
        if(!folder.isDirectory() && !folder.mkdirs()) throw new IOException("共有用の保存先を作成できません");
        File copy=resolveShare(cache,"iq-"+UUID.randomUUID()+".jrd-iq");
        Files.copy(latest(cache).toPath(),copy.toPath());
        File[] files=folder.listFiles(f -> f.isFile() && f.getName().matches("iq-[0-9a-f-]{36}\\.jrd-iq"));
        if(files!=null) {
            Arrays.sort(files,Comparator.comparingLong(File::lastModified));
            int remove=Math.max(0,files.length-4);
            for(File old:files) if(remove>0 && !old.equals(copy) && old.delete()) remove--;
        }
        return copy;
    }
    private IqCaptureFiles() {}
}
