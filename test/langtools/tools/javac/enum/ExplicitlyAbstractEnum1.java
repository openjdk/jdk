/*
 * @test /nodynamiccopyright/
 * @bug 5009601
 * @summary enum's cannot be explicitly declared abstract
 * @compile/fail/ref=ExplicitlyAbstractEnum1.out -XDrawDiagnostics ExplicitlyAbstractEnum1.java
 */

abstract enum ExplicitlyAbstractEnum1 {
    FE,
    FI,
    FO,
    FUM;
}
