/*
 * @test /nodynamiccopyright/
 * @bug 6843077
 * @summary check for Duplicate annotation value
 * @author Mahmood Ali
 * @compile/fail/ref=DuplicateAnnotationValue.out -XDrawDiagnostics -source 1.7 DuplicateAnnotationValue.java
 */
class DuplicateAnnotationValue {
  void test() {
    new @A String();
  }
}

@interface A { int field(); }
