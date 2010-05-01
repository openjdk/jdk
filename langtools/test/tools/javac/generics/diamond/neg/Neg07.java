/*
 * @test /nodynamiccopyright/
 * @bug 6939620
 *
 * @summary  Switch to 'complex' diamond inference scheme
 * @author mcimadamore
 * @compile/fail/ref=Neg07.out Neg07.java -XDrawDiagnostics
 *
 */

class Neg07 {
   static class SuperFoo<X> {}
   static class Foo<X extends Number> extends SuperFoo<X> {
       Foo(X x) {}
   }

   SuperFoo<String> sf1 = new Foo<>("");
   SuperFoo<String> sf2 = new Foo<>("") {};
}
