/*
 * @test /nodynamiccopyright/
 * @bug 8348410
 * @summary Ensure --enable-preview is required for primitiver switch on a boxed expression
 * @compile/fail/ref=PrimitivePatternsSwitchRequirePreviewBoolean.out -XDrawDiagnostics -XDshould-stop.at=FLOW PrimitivePatternsSwitchRequirePreviewBoolean.java
 */
public class PrimitivePatternsSwitchRequirePreviewBoolean {

    public static void testBoolean(Boolean value) {
        switch (value) {
            case true   -> System.out.println("true");
            default     -> System.out.println("false");
        }
    }
}
