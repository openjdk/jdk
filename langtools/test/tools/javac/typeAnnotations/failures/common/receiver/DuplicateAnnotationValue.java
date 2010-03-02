/*
 * @test /nodynamiccopyright/
 * @bug 6843077 6919944
 * @summary check for duplicate annotation values in receiver
 * @author Mahmood Ali
 * @compile/fail/ref=DuplicateAnnotationValue.out -XDrawDiagnostics -source 1.7 DuplicateAnnotationValue.java
 */
class DuplicateAnnotationValue {
  void test() @A(value = 2, value = 1) { }
}

@interface A { int value(); }
