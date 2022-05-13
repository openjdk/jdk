/*
 * @test /nodynamiccopyright/
 * @bug 8286057
 * @summary Make javac error on a generic enum friendlier
 *
 * @compile/fail/ref=T8286057.out -XDrawDiagnostics T8286057.java
 */

class EnumsCantBeGeneric {
  public enum E1<> {}
  public enum E2<T> {}
  public enum E3<T, T> {}
}
