/*
 * @test
 * @bug 8370459
 * @summary TODO: desc
 * @run main/othervm
 *      -Xbatch
 *      -XX:CompileCommand=compileonly,*.TestCompressBitsReduced::test_compiled
 *      -XX:CompileCommand=printcompilation,*.TestCompressBitsReduced::test_compiled
 *      -XX:+PrintIdeal
 *      -XX:+TieredCompilation compiler.igvn.templated.TestCompressBitsReduced
 */

package compiler.igvn.templated;

public class TestCompressBitsReduced {
    public static void main(String[] vmFlags) {
        for (int i = 0; i < 10_000; i++) {
            test();
        }
    }

    public static void test() {
        int x = -16385;
        long v0 = test_compiled(x);
        long v1 = test_reference(x);
        if (v0 != v1) {
            StringBuilder sb = new StringBuilder();
            sb.append("wrong value: " + v0 + " vs " + v1);
            throw new RuntimeException(sb.toString());
        }
    }

    public static long test_compiled(int x) {
        // src  = -2683206580L = ffff_ffff_6011_844c
        // mask = 0..maxuint, at runtime: 4294950911 = 0xffff_bfff
        //
        // Hence we go to the B) case of CompressBits in bitshuffle_value
        //
        // mask_bit_width = 64
        // clz = 32
        // result_bit_width = 32
        //
        // So we have result_bit_width < mask_bit_width
        //
        // And we do:
        // lo = result_bit_width == mask_bit_width ? lo : 0L;
        // -> lo = 0
        //
        // And we do:
        // hi = MIN2((jlong)((1UL << result_bit_width) - 1L), hi);
        //
        // But watch out: on windows 1UL is only a 32 bit value. Intended was probably 1ULL.
        // So when we caluculate "1UL << 32", we just get 1. And so then hi would be 0 now.
        // If we instead did "1ULL << 32", we would get 0x1_0000_0000, and hi = 0xffff_ffff.
        //
        // We create type [lo, hi]:
        // windowns: [0, 0]           -> constant zero
        // correct:  [0, 0xffff_ffff] -> does not constant fold. At runtime: 0x3008_c44c
        return Long.compress(-2683206580L, Integer.toUnsignedLong(x));
    }

    public static long test_reference(int x) {
        return Long.compress(-2683206580L, Integer.toUnsignedLong(x));
    }
}
