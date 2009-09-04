/*
 * @test /nodynamiccopyright/
 * @bug 6843077
 * @summary test incomplete vararg declaration
 * @author Mahmood Ali
 * @compile/fail/ref=IncompleteVararg.out -XDrawDiagnostics -source 1.7 IncompleteVararg.java
 */
class IncompleteArray {
  // the last variable may be vararg
  void method(int @A test) { }
}

@interface A { }
