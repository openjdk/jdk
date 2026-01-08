/*
 * @test    /nodynamiccopyright/
 * @bug     6393539
 * @summary no compile-time error for clone, etc. in annotation type
 * @author  Peter von der Ahé
 * @compile/fail/ref=NoAnnotationMethods.out -XDrawDiagnostics  NoAnnotationMethods.java
 */

public @interface NoAnnotationMethods {
    int annotationType();
}
