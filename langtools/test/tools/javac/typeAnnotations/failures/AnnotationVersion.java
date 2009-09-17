/*
 * @test /nodynamiccopyright/
 * @bug 6843077
 * @summary test that only java 7 allows type annotations
 * @author Mahmood Ali
 * @compile/fail/ref=AnnotationVersion.out -XDrawDiagnostics -source 1.6 AnnotationVersion.java
 */
class AnnotationVersion {
  public void method() @A { }
}

@interface A { }
