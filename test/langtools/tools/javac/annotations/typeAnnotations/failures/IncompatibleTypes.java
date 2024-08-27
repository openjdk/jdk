/*
 * @test /nodynamiccopyright/
 * @bug 8291643
 * @summary Consider omitting type annotations from type error diagnostics
 * @compile/fail/ref=IncompatibleTypes.out -XDrawDiagnostics IncompatibleTypes.java
 */
import java.lang.annotation.*;
import java.util.List;

class IncompatibleTypes {
  List<@A Number> f(List<String> xs) {
    return xs;
  }
}

@Target(ElementType.TYPE_USE)
@interface A { }
