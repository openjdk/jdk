package sun.management.cmd.internal;

import sun.management.cmd.Executable;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class Executor {

    // TODO: the value should be configurable
    private static final long TIMEOUT = 60;

    /**
     * invoked by native
     */
    static String executeCommand(Executable cmd) {
        ExecutorService es = Executors.newFixedThreadPool(1, r -> new Thread(r, "Java DCmd Runner"));
        try {
            Future<String> f = es.submit(
                () -> {
                    StringWriter sw = new StringWriter();
                    try (PrintWriter pw = new PrintWriter(sw)) {
                        cmd.execute(pw);
                    }
                    return sw.toString();
                }
            );

            String res;
            try {
                res = f.get(TIMEOUT, TimeUnit.SECONDS);
            } catch (Exception e) {
                f.cancel(true);
                if (e instanceof TimeoutException) {
                    res = "Timed out";
                } else {
                    res = e.getMessage();
                }
            }
            return res;
        } finally {
            es.shutdown();
        }
    }
}
