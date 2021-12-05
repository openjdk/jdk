package compiler.vectorapi.reshape;

public class TestMethods {
    public static final String AVX1_CAST_TESTS = """
            testB64toS64
            testB64toS128
            testB64toI128
            testB64toF128
            testS64toB64
            testS128toB64
            testS64toI64
            testS64toI128
            testS64toL128
            testS64toF64
            testS64toF128
            testS128toF256
            testS64toD128
            notestS64toD256
            testI128toB64
            testI64toS64
            testI128toS64
            testI64toL128
            testI64toF64
            testI128toF128
            testI64toD128
            testI128toD256
            testL128toS64
            testL128toI64
            testF64toI64
            testF128toI128
            testF64toD128
            testF128toD256
            testD128toF64
            notestD256toF128
            """;

    public static final String AVX2_CAST_TESTS = AVX1_CAST_TESTS + """
            testB128toS256
            testB64toI256
            testB64toL256
            testB64toF256
            testB64toD256
            testS256toB128
            testS128toI256
            testS64toL256
            testI256toB64
            testI256toS128
            testI128toL256
            testI256toF256
            testL256toB64
            testL256toS64
            testL256toI128
            testF256toI256
            """;
}
