/*
 * @test /nodynamiccopyright/
 * @bug 8231827
 * @summary Match which involves a cast conversion
 * @compile/fail/ref=CastConversionMatch.out -XDrawDiagnostics CastConversionMatch.java
 * @compile --enable-preview --source ${jdk.version} CastConversionMatch.java */

public class CastConversionMatch {
    public static void meth() {
        Object o = 42;
        if (o instanceof int s) {
            System.out.println("Okay");
        } else {
            throw new AssertionError("broken");
        }
        System.out.println(">Test complete");
    }
}
