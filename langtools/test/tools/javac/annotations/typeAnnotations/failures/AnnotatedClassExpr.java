/*
 * @test /nodynamiccopyright/
 * @bug 8027262
 * @summary A class expression cannot be annotated.
 * @compile/fail/ref=AnnotatedClassExpr.out -XDrawDiagnostics AnnotatedClassExpr.java
 */
import java.lang.annotation.*;
import java.util.List;

class AnnotatedClassExpr {
  static void main() {
    Object o1 = @A int.class;
  }
}

@Target(ElementType.TYPE_USE)
@interface A { }
