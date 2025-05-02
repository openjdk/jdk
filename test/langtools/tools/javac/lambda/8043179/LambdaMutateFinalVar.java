/*
 * @test /nodynamiccopyright/
 * @summary Verify lambda expression can't mutate a final variable
 * @bug 8043179
 * @compile/fail/ref=LambdaMutateFinalVar.out -XDrawDiagnostics LambdaMutateFinalVar.java
 */
class LambdaMutateFinalVar {
    LambdaMutateFinalVar() {
        final String x;
        Runnable r1 = () -> x = "not ok";
        x = "ok";
    }
}
