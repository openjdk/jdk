/**
 * @test  /nodynamiccopyright/
 * @bug 5012028 6384539
 * @summary javac crash when declare an annotation type illegally
 *
 * @compile/fail IllegalAnnotation.java
 * @compile/fail/ref=IllegalAnnotation.out -XDdev -XDrawDiagnostics IllegalAnnotation.java
 */
class IllegalAnnotation {
    {
        @interface SomeAnnotation { }
    }
}
