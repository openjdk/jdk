/*
 * @test /nodynamiccopyright/
 * @bug 6843077 6919944
 * @summary check for duplicate annotation values
 * @author Mahmood Ali
 * @compile/fail/ref=DuplicateAnnotationValue.out -XDrawDiagnostics -source 1.7 DuplicateAnnotationValue.java
 */
class DuplicateAnnotationValue {
  void test() {
    String[] a = new String @A(value = 2, value = 1) [5] ;
  }
}

@interface A { int value(); }
