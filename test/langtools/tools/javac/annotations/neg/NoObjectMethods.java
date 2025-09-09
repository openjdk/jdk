/*
 * @test    /nodynamiccopyright/
 * @bug     6393539
 * @summary no compile-time error for clone, etc. in annotation type
 * @author  Peter von der Ahé
 * @compile/fail/ref=NoObjectMethods.out -XDrawDiagnostics  NoObjectMethods.java
 */

public @interface NoObjectMethods {
    int clone();
}
