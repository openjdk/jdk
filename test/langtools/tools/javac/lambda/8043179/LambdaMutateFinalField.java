/*
 * @test /nodynamiccopyright/
 * @summary Verify lambda expression can't mutate a final field
 * @bug 8043179
 * @compile/fail/ref=LambdaMutateFinalField.out -XDrawDiagnostics LambdaMutateFinalField.java
 */
class LambdaMutateFinalField {
    final String x;
    LambdaMutateFinalField() {
        Runnable r1 = () -> x = "not ok";
        this.x = "ok";
    }
}
