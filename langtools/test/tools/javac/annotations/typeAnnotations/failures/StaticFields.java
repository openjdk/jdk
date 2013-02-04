/*
 * @test /nodynamiccopyright/
 * @bug 6843077 8006775
 * @summary static field access isn't a valid location
 * @author Mahmood Ali
 * @compile/fail/ref=StaticFields.out -XDrawDiagnostics StaticFields.java
 */
class C {
  int f;
  int a = @A C.f;
}

@interface A { }
