package jdk.internal.natives;

import java.lang.foreign.AddressLayout;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.ValueLayout;

import static java.lang.foreign.ValueLayout.*;

public final class CLayouts {

    private CLayouts() {
    }

    // Todo: Lookup these values via the Linker::canonicalLayouts

    public static final ValueLayout.OfBoolean C_BOOL = JAVA_BOOLEAN;
    public static final ValueLayout.OfByte C_CHAR = JAVA_BYTE;
    public static final ValueLayout.OfShort C_SHORT = JAVA_SHORT;
    public static final OfInt C_INT = JAVA_INT;
    public static final OfLong C_LONG = JAVA_LONG;
    public static final OfLong C_LONG_LONG = JAVA_LONG;
    public static final OfFloat C_FLOAT = JAVA_FLOAT;
    public static final OfDouble C_DOUBLE = JAVA_DOUBLE;
    public static final AddressLayout C_POINTER = ADDRESS.withByteAlignment(8).withTargetLayout(MemoryLayout.sequenceLayout(C_CHAR));
}
