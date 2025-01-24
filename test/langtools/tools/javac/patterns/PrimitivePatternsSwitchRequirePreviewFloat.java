/*
 * @test /nodynamiccopyright/
 * @bug 8348410
 * @summary Ensure --enable-preview is required for primitive switch on a boxed expression
 * @compile/fail/ref=PrimitivePatternsSwitchRequirePreviewFloat.out -XDrawDiagnostics -XDshould-stop.at=FLOW PrimitivePatternsSwitchRequirePreviewFloat.java
 */
public class PrimitivePatternsSwitchRequirePreviewFloat {

    public static void testFloat(Float value) {
        switch (value) {
            case 0f      -> System.out.println("zero");
            default      -> System.out.println("non-zero");
        }
    }
}
