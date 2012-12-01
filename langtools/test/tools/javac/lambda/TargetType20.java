/*
 * @test /nodynamiccopyright/
 * @bug 8003280
 * @summary Add lambda tests
 *  complex case of lambda return type that depends on generic method
 *          inference variable
 * @compile/fail/ref=TargetType20.out -XDrawDiagnostics TargetType20.java
 */
import java.util.*;

class TargetType20 {

    interface SAM2<X> {
      List<X> f();
    }

    class Test {
       <Z> void call(SAM2<Z> x, SAM2<Z> y) { }
       { call(() -> Collections.emptyList(), () -> new ArrayList<String>()); }
    }
}
