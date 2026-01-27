/*
 * @test /nodynamiccopyright/
 * @summary Retain exhaustiveness properties of switches with a constant selector
 * @enablePreview
 * @compile/fail/ref=PrimitivePatternsSwitchConstants.out -XDrawDiagnostics -XDshould-stop.at=FLOW -XDexhaustivityMaxBaseChecks=0 PrimitivePatternsSwitchConstants.java
 */
public class PrimitivePatternsSwitchConstants {
    void testConstExpressions() {
        switch (42) {         // error: not exhaustive
            case byte _ :
        }

        switch (42l) {        // error: not exhaustive
            case byte _ :
        }

        switch (123456) {     // error: not exhaustive
            case byte _ :
        }

        switch (16_777_216) { // error: not exhaustive
            case float _ :
        }

        switch (16_777_217) { // error: not exhaustive
            case float _ :
        }

        switch (42d) {        // error: not exhaustive
            case float _ :
        }

        switch (1) {          // OK
            case long _ :
        }

        final int i = 42;
        switch (i) {          // OK
            case long _ :
        }

        switch (1) {          // error: non-exhaustive
            case Long _ :         // error: widening primitive conversion and boxing is not supported
        }

        switch (42) {
            case byte bb  -> {}
            case int ii   -> {}   // OK
        };

        switch (42) {
            case 42 -> {}
            case int ii -> {}     // OK
        };

        switch (42) {
            case (byte) 42 -> {}
            case int ii -> {}     // OK
        };

        switch (42) {
            case 42 -> {}
            default -> {}         // OK
        };

        switch (42) {
            default -> {}         // OK
            case 42 -> {}
        };
    }
}
