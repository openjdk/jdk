/*
 * @test /nodynamiccopyright/
 * @bug 6843077
 * @summary check for duplicate annotations
 * @author Mahmood Ali
 * @compile/fail/ref=DuplicateTypeAnnotation.out -XDrawDiagnostics -source 1.7 DuplicateTypeAnnotation.java
 */
class DuplicateTypeAnno {
  void innermethod() {
    class Inner<@A @A K> { }
  }
}

@interface A { }
