/*
 * @test /nodynamiccopyright/
 * @bug 6843077
 * @summary check that A is accessible in the class type parameters
 * @author Mahmood Ali
 * @compile/fail/ref=Scopes.out -XDrawDiagnostics -source 1.7 Scopes.java
 */
class Scopes<T extends @UniqueInner Object> {
  @interface UniqueInner { };
}
