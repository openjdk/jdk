/*
 * @test /nodynamiccopyright/
 * @bug 6843077
 * @summary test indexing of an array
 * @author Mahmood Ali
 * @compile/fail/ref=IndexArray.out -XDrawDiagnostics -source 1.7 IndexArray.java
 */
class IndexArray {
  int[] var;
  int a = var @A [1];
}

@interface A { }
