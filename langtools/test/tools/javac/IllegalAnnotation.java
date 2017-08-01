/**
 * @test  /nodynamiccopyright/
 * @bug 5012028 6384539 8074364
 * @summary javac crash when declare an annotation type illegally
 *
 * @compile/fail/ref=IllegalAnnotation.out -XDrawDiagnostics IllegalAnnotation.java
 */
class IllegalAnnotation {
    {
        @interface SomeAnnotation { }
    }
}
