/*
 * @test /nodynamiccopyright/
 * @summary Omit type-use annotations from diagnostics
 * @compile/fail/ref=MethodArguments.out -XDrawDiagnostics MethodArguments.java p/A.java p/B.java
 */

import java.util.List;
import p.A;
import p.B;

public final class MethodArguments {
  public static void main(String[] args) {
    // error non-static.cant.be.ref:
    //     non-static ... cannot be referenced from a static context
    B.one("bar");

    B b = new B();

    // error ref.ambiguous:
    //     reference to ... is ambiguous
    //     ...
    //     both ... and ... match
    b.one(null);

    // error report.access:
    //     ... has private access in ...
    b.two("foo");
    //     ... has protected access in ...
    b.three("foo");

    // error not.def.public.cant.access:
    //     ... is not public in ... cannot be accessed from outside package
    b.four("foo");
  }

  void five(@A String s) {
  }

  void five(@A String s) {
  }

  void six(List<@A String> s) {
  }

  void six(List<@A String> s) {
  }
}
