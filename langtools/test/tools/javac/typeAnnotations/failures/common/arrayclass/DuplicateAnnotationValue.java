/*
 * @test /nodynamiccopyright/
 * @bug 6843077
 * @summary check for duplicate annotation values
 * @author Mahmood Ali
 * @compile/fail/ref=DuplicateAnnotationValue.out -XDrawDiagnostics -source 1.7 DuplicateAnnotationValue.java
 */
class DuplicateAnnotationValue {
  void test() {
    Object a = String @A(value = 2, value = 1) [].class;
  }
}

@interface A { int value(); }
