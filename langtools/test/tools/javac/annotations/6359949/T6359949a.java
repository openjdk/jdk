/*
 * @test    /nodynamiccopyright/
 * @bug     6359949
 * @summary (at)Override of static shouldn't be accepted (compiler shouldissue an error/warning)
 * @compile/fail/ref=T6359949a.out -XDrawDiagnostics  T6359949a.java
 */

class Example {
    public static void example() {

    }
}

class Test extends Example {
    @Override
    public static void example() {

    }
}
