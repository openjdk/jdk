/*
 * @test /nodynamiccopyright/
 * @bug 8334757
 * @compile/fail/ref=CantAnnotateClassWithTypeVariable.out -XDrawDiagnostics CantAnnotateClassWithTypeVariable.java
 */

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

public class CantAnnotateClassWithTypeVariable {
  @Target(ElementType.TYPE_USE)
  @interface TA {}

  static class A {
    static class B<T> {}
  }

  <T> @TA A.B<T> f() {}
}
