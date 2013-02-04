/*
 * @test /nodynamiccopyright/
 * @bug 6843077 8006775
 * @summary test indexing of an array
 * @author Mahmood Ali
 * @compile/fail/ref=IndexArray.out -XDrawDiagnostics IndexArray.java
 */
class IndexArray {
  int[] var;
  int a = var @A [1];
}

@interface A { }
