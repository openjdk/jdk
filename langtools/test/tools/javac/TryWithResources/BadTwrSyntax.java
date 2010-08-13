/*
 * @test  /nodynamiccopyright/
 * @bug 6911256 6964740
 * @author Joseph D. Darcy
 * @summary Verify bad TWRs don't compile
 * @compile/fail -source 6 BadTwrSyntax.java
 * @compile/fail/ref=BadTwrSyntax.out  -XDrawDiagnostics BadTwrSyntax.java
 */

import java.io.IOException;
public class BadTwrSyntax implements AutoCloseable {
    public static void main(String... args) throws Exception {
        // illegal semicolon ending resources
        try(BadTwr twrflow = new BadTwr();) {
            System.out.println(twrflow.toString());
        }
    }

    public void close() {
        ;
    }
}
