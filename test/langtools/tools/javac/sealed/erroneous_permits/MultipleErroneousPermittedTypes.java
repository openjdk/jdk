/*
 * @test /nodynamiccopyright/
 * @bug 8347562
 * @summary Erroneous types in permit clause shouldn't cause more errors/crashes
 * @compile/fail/ref=MultipleErroneousPermittedTypes.out -XDrawDiagnostics MultipleErroneousPermittedTypes.java
 */
sealed class Permits<X, Y> permits X, Y {}
