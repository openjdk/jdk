/*
 * @test /nodynamiccopyright/
 * @bug 6843077 8006775
 * @summary test incomplete vararg declaration
 * @author Mahmood Ali
 * @compile/fail/ref=IncompleteVararg.out -XDrawDiagnostics IncompleteVararg.java
 */
class IncompleteArray {
  // the last variable may be vararg
  void method(int @A test) { }
}

@interface A { }
