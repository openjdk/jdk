/*
 * @test /nodynamiccopyright/
 * @bug 6843077 8006775
 * @summary test that only Java 8 allows type annotations
 * @author Mahmood Ali
 * @compile/fail/ref=AnnotationVersion.out -XDrawDiagnostics -Xlint:-options -source 1.6 AnnotationVersion.java
 * @compile/fail/ref=AnnotationVersion7.out -XDrawDiagnostics -Xlint:-options -source 1.7 AnnotationVersion.java
 */
class AnnotationVersion {
  public void method(@A AnnotationVersion this) { }
}

@interface A { }
