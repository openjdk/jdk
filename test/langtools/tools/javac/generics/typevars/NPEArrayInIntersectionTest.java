/*
 * @test /nodynamiccopyright/
 * @bug 8388979
 * @summary javac crashes with NullPointerException in Types.erasure when evaluating bounds
 *          of mutually dependent array type variables inside an intersection type definition
 * @compile/fail/ref=NPEArrayInIntersectionTest.out -XDrawDiagnostics NPEArrayInIntersectionTest.java
 */

class NPEArrayInIntersectionTest<A extends B[] & Runnable, B> {}
