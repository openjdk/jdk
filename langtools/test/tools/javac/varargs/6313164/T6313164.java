/*
 * @test /nodynamiccopyright/
 * @bug     6313164
 * @author mcimadamore
 * @summary  javac generates code that fails byte code verification for the varargs feature
 * @compile/fail/ref=T6313164.out -XDrawDiagnostics T6313164.java
 */
import p1.*;

class T6313164 {
    { B b = new B();
      b.foo1(new B(), new B()); //error - A not accesible
      b.foo2(new B(), new B()); //ok - A not accessible, but foo2(Object...) applicable
      b.foo3(null, null); //error - A (inferred) not accesible
      b.foo4(null, null); //error - A (inferred in 15.12.2.8 - no resolution backtrack) not accesible
      b.foo4(new B(), new C()); //ok - A (inferred in 15.12.2.7) not accessible, but foo4(Object...) applicable
    }
}
