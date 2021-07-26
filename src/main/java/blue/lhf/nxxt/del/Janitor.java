package blue.lhf.nxxt.del;

import blue.lhf.nxxt.Nxxt;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;

@SuppressWarnings("UnusedReturnValue")
public class Janitor {
    public static final String PREFETCH_CLEAR = "powershell -Command \"Start-Process powershell -Verb RunAs @('-WindowStyle', 'Hidden', '-Command', 'Remove-Item -Path C:\\Windows\\Prefetch\\JAVA.EXE-*.PF')\"";

    public static boolean clearPrefetch() {
        if (!Nxxt.WINDOWS) return false;
        try {
            return Runtime.getRuntime().exec(PREFETCH_CLEAR).waitFor() == 0;
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }

    public static boolean markJarDeletion() {
        try {
            File f = new File(Nxxt.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            if (f.delete()) return true;

            /* Windows locks the jarfile (so we can't delete it directly), and the CMD DEL command
             * returns a success status code (0) even if deletion fails...
             *
             * We have to use powershell to remove the file ASAP.
             */
            if (Nxxt.WINDOWS) {
                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    try {
                        ProcessBuilder builder = new ProcessBuilder(List.of(
                                "powershell", "-Command", "\"do { Remove-Item \"" + f.getAbsolutePath() + "\" } while (-not $?)\""
                        ));
                        builder.start();
                    } catch (IOException e) {
                        e.printStackTrace();
                        throw new RuntimeException(e);
                    }
                }));
            }
            return true;
        } catch (URISyntaxException e) {
            return false;
        }
    }
}
