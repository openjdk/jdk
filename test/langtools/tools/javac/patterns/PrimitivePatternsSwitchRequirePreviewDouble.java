/*
 * @test /nodynamiccopyright/
 * @bug 8348410
 * @summary Ensure --enable-preview is required for primitiver switch on a boxed expression
 * @compile/fail/ref=PrimitivePatternsSwitchRequirePreviewDouble.out -XDrawDiagnostics -XDshould-stop.at=FLOW PrimitivePatternsSwitchRequirePreviewDouble.java
 */
public class PrimitivePatternsSwitchRequirePreviewDouble {

    public static void testDouble(Long value) {
        switch (value) {
            case 0L      -> System.out.println("zero");
            default      -> System.out.println("non-zero");
        }
    }
}
