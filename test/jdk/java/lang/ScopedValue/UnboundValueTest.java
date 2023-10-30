import java.util.NoSuchElementException;

/*
 * @test
 * @enablePreview
 * @run main/othervm -Xmx10m UnboundValueTest
 */
public class UnboundValueTest {

    static final Thread doRun = new Thread() {
        public void run() {
            try {
                try {
                    // Provoke the VM to throw an OutOfMemoryError
                    java.util.Arrays.fill(new int[Integer.MAX_VALUE][], new int[Integer.MAX_VALUE]);
                } catch (OutOfMemoryError e) {
                    // Try to get() an unbound ScopedValue
                    ScopedValue.newInstance().get();
                }
            } catch (NoSuchElementException e) {
                System.out.println("OK");
                return;
            }
            throw new RuntimeException("Expected NoSuchElementException");
        }
    };

    public static void main(String [] args) throws Exception {
        doRun.run();   // Run on this Thread
        var job = new Thread(doRun);
        job.start();   // Run on a new Thread
        job.join();
        doRun.start(); // Run on the Thread doRun
        doRun.join();
    }
}
