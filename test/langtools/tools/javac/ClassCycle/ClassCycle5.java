/*
 * @test /nodynamiccopyright/
 * @bug 8389058
 * @summary Verify that resolving a qualified type through a self-referential
 *          (cyclic) supertype doesn't crash the compiler with StackOverflowError
 * @compile/fail/ref=ClassCycle5.out -XDrawDiagnostics ClassCycle5.java
 */

class ClassCycle5 extends ClassCycle5 implements ClassCycle5.NoSuchType {}
