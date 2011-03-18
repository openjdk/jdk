/*
 * @test /nodynamiccopyright/
 * @bug 6939620 7020044
 *
 * @summary  Check that diamond works where LHS is supertype of RHS (nilary constructor)
 * @author mcimadamore
 * @compile/fail/ref=Neg06.out Neg06.java -XDrawDiagnostics
 *
 */

class Neg06 {

   static class CSuperFoo<X> {}
   static class CFoo<X extends Number> extends CSuperFoo<X> {}

   CSuperFoo<String> csf1 = new CFoo<>();
}
