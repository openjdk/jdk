/*
 * @test  /nodynamiccopyright/
 * @bug 6911256 6964740
 * @summary Test error messages for an unadorned try
 * @compile/fail/ref=PlainTry.out -XDrawDiagnostics PlainTry.java
 */
public class PlainTry {
    public static void meth() {
        try {
            ;
        }
    }
}
