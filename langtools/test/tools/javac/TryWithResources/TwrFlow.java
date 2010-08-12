/*
 * @test  /nodynamiccopyright/
 * @bug 6911256 6964740
 * @author Joseph D. Darcy
 * @summary Test exception analysis of ARM blocks
 * @compile/fail/ref=TwrFlow.out -XDrawDiagnostics TwrFlow.java
 */

import java.io.IOException;
public class TwrFlow implements AutoCloseable {
    public static void main(String... args) {
        try(TwrFlow armflow = new TwrFlow()) {
            System.out.println(armflow.toString());
        } catch (IOException ioe) { // Not reachable
            throw new AssertionError("Shouldn't reach here", ioe);
        }
        // CustomCloseException should be caught or added to throws clause

        // Also check behavior on a resource expression rather than a
        // declaration.
        TwrFlow armflowexpr = new TwrFlow();
        try(armflowexpr) {
            System.out.println(armflowexpr.toString());
        } catch (IOException ioe) { // Not reachable
            throw new AssertionError("Shouldn't reach here", ioe);
        }
        // CustomCloseException should be caught or added to throws clause
    }

    /*
     * A close method, but the class is <em>not</em> Closeable or
     * AutoCloseable.
     */
    public void close() throws CustomCloseException {
        throw new CustomCloseException();
    }
}

class CustomCloseException extends Exception {}
