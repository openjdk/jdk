/*
 * @test /nodynamiccopyright/
 * @bug 6843077
 * @summary static field access isn't a valid location
 * @author Mahmood Ali
 * @compile/fail/ref=StaticFields.out -XDrawDiagnostics -source 1.7 StaticFields.java
 */
class C {
  int f;
  int a = @A C.f;
}

@interface A { }
