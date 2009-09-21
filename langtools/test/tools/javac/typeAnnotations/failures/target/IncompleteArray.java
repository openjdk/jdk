/*
 * @test /nodynamiccopyright/
 * @bug 6843077
 * @summary test incomplete array declaration
 * @author Mahmood Ali
 * @compile/fail/ref=IncompleteArray.out -XDrawDiagnostics -source 1.7 IncompleteArray.java
 */
class IncompleteArray {
  int @A [] @A var;
}

@interface A { }
