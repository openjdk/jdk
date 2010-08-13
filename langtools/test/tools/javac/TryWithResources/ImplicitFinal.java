/*
 * @test  /nodynamiccopyright/
 * @bug 6911256 6964740 6965277
 * @author Maurizio Cimadamore
 * @summary Test that resource variables are implicitly final
 * @compile/fail/ref=ImplicitFinal.out -XDrawDiagnostics ImplicitFinal.java
 */

import java.io.IOException;

class ImplicitFinal implements AutoCloseable {
    public static void main(String... args) {
        try(ImplicitFinal r = new ImplicitFinal()) {
            r = null; //disallowed
        } catch (IOException ioe) { // Not reachable
            throw new AssertionError("Shouldn't reach here", ioe);
        }
    }


     // A close method, but the class is <em>not</em> Closeable or
     // AutoCloseable.

    public void close() throws IOException {
        throw new IOException();
    }
}
