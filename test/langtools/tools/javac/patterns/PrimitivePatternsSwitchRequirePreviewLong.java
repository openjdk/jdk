/*
 * @test /nodynamiccopyright/
 * @bug 8348410
 * @summary Ensure --enable-preview is required for primitive switch on a boxed expression
 * @compile/fail/ref=PrimitivePatternsSwitchRequirePreviewLong.out -XDrawDiagnostics -XDshould-stop.at=FLOW PrimitivePatternsSwitchRequirePreviewLong.java
 */
public class PrimitivePatternsSwitchRequirePreviewLong {

    public static void testLong(Long value) {
        switch (value) {
            case 0L      -> System.out.println("zero");
            default      -> System.out.println("non-zero");
        }
    }
}
