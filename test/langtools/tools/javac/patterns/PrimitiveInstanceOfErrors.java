/*
 * @test /nodynamiccopyright/
 * @bug 8304487
 * @summary Compiler Implementation for Primitive types in patterns, instanceof, and switch (Preview)
 * @enablePreview
 * @compile/fail/ref=PrimitiveInstanceOfErrors.out -XDrawDiagnostics -XDshould-stop.at=FLOW PrimitiveInstanceOfErrors.java
 */
public class PrimitiveInstanceOfErrors {
    public static boolean unboxingAndNarrowingPrimitiveNotAllowedPerCastingConversion() {
        Long l_within_int_range = 42L;
        Long l_outside_int_range = 999999999999999999L;

        return l_within_int_range instanceof int && !(l_outside_int_range instanceof int);
    }

    public static <T extends Integer> boolean wideningReferenceConversionUnboxingAndNarrowingPrimitive(T i) {
        return i instanceof byte;
    }

    public static void boxingConversionsBetweenIncompatibleTypes() {
        int i = 42;

        boolean ret1 = i instanceof Integer; // (Integer) i // OK and true
        boolean ret2 = i instanceof Double;  // error: incompatible types
        boolean ret3 = i instanceof Short;   // error: incompatible types
    }
}
