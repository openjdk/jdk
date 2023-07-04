/*
 * @test /nodynamiccopyright/
 * @bug 8184444
 * @summary Report unintialized static final variables
 * @compile/fail/ref=StaticFinalInit.out -XDrawDiagnostics StaticFinalInit.java
 */
class StaticFinalInit {
    static final int i;
}
