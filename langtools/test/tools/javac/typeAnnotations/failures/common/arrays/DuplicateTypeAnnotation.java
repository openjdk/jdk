/*
 * @test /nodynamiccopyright/
 * @bug 6843077
 * @summary check for duplicate annotations
 * @author Mahmood Ali
 * @compile/fail/ref=DuplicateTypeAnnotation.out -XDrawDiagnostics -source 1.7 DuplicateTypeAnnotation.java
 */

class DuplicateTypeAnnotation {
  void test() {
    String @A @A [] s;
  }
}

@interface A { }
