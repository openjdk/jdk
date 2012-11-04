/*
 * @test /nodynamiccopyright/
 * @summary flow analysis is not run on inlined default bodies
 * @compile/fail/ref=Neg06.out -XDallowDefaultMethods -XDrawDiagnostics Neg06.java
 */

class Neg06 {

    interface A {
        default String m() { C.m(); }
    }

    static class C {
        static String m() { return ""; }
    }
}
