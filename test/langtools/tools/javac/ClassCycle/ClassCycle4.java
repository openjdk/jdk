/*
 * @test /nodynamiccopyright/
 * @bug 8320220
 * @summary Fix infinite recursion in cyclic inheritance situation
 * @compile/fail/ref=ClassCycle4.out -XDrawDiagnostics ClassCycle4.java
 */

interface ClassCycle4 extends I1, I2 {}
interface I1 extends ClassCycle4 {}
interface I2 extends ClassCycle4 {}
