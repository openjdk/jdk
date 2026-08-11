/*
 * @test /nodynamiccopyright/
 * @bug 8388979
 * @summary javac crashes with NullPointerException in Types.erasure when evaluating bounds
 *          of mutually dependent array type variables inside an intersection type definition
 * @compile/fail/ref=NPECyclicTypeVarsTest.out -XDrawDiagnostics NPECyclicTypeVarsTest.java
 */

/* `B` is declared after `A`, and only referenced through an array type used as
 * the first (explicit) bound of an intersection bound. Erasing that bound
 * while attributing `A` recurses into erasing `B`'s (not yet attributed) bound.
 */
class NPECyclicTypeVarsTest<A extends B[] & Runnable, B> {}
