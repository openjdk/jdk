/*
 * Copyright (c) 2026, Intel Corporation. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

import java.util.Arrays;
import java.util.Random;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HexFormat;

/*
 * @test
 * @requires os.simpleArch == "x64"
 * @library /test/lib
 * @key randomness
 * @modules java.base/com.sun.crypto.provider:+open
 * @run main/othervm -XX:UseAVX=2 ML_KEM_Intrinsic_Test
 */
/*
 * @test
 * @library /test/lib
 * @key randomness
 * @modules java.base/com.sun.crypto.provider:+open
 * @run main ML_KEM_Intrinsic_Test
 */

// To run manually:
// java --add-opens java.base/com.sun.crypto.provider=ALL-UNNAMED
//  --add-exports java.base/com.sun.crypto.provider=ALL-UNNAMED
//  -XX:+UnlockDiagnosticVMOptions -XX:+UseKyberIntrinsics
//  test/jdk/sun/security/provider/pqc/ML_KEM_Intrinsic_Test.java

public class ML_KEM_Intrinsic_Test {
    public static void main(String[] args) throws Throwable {
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        Class<?> kClazz = com.sun.crypto.provider.ML_KEM.class;

        Method m = kClazz.getDeclaredMethod("implKyberNtt",
                short[].class, short[].class);
        m.setAccessible(true);
        MethodHandle ntt = lookup.unreflect(m);

        m = kClazz.getDeclaredMethod("implKyberNttJava",
                short[].class);
        m.setAccessible(true);
        MethodHandle nttJava = lookup.unreflect(m);

        m = kClazz.getDeclaredMethod("implKyberInverseNtt",
                short[].class, short[].class);
        m.setAccessible(true);
        MethodHandle inverseNtt = lookup.unreflect(m);

        m = kClazz.getDeclaredMethod("implKyberInverseNttJava",
                short[].class);
        m.setAccessible(true);
        MethodHandle inverseNttJava = lookup.unreflect(m);

        m = kClazz.getDeclaredMethod("implKyberNttMult",
                short[].class, short[].class, short[].class, short[].class);
        m.setAccessible(true);
        MethodHandle mult = lookup.unreflect(m);

        m = kClazz.getDeclaredMethod("implKyberNttMultJava",
                short[].class, short[].class, short[].class);
        m.setAccessible(true);
        MethodHandle multJava = lookup.unreflect(m);

        m = kClazz.getDeclaredMethod("implKyberAddPoly",
                short[].class, short[].class, short[].class);
        m.setAccessible(true);
        MethodHandle addPoly2 = lookup.unreflect(m);

        m = kClazz.getDeclaredMethod("implKyberAddPolyJava",
                short[].class, short[].class, short[].class);
        m.setAccessible(true);
        MethodHandle addPoly2Java = lookup.unreflect(m);

        m = kClazz.getDeclaredMethod("implKyberAddPoly",
                short[].class, short[].class, short[].class, short[].class);
        m.setAccessible(true);
        MethodHandle addPoly3 = lookup.unreflect(m);

        m = kClazz.getDeclaredMethod("implKyberAddPolyJava",
                short[].class, short[].class, short[].class, short[].class);
        m.setAccessible(true);
        MethodHandle addPoly3Java = lookup.unreflect(m);

        m = kClazz.getDeclaredMethod("implKyber12To16",
                byte[].class, int.class, short[].class, int.class);
        m.setAccessible(true);
        MethodHandle twelve2Sixteen = lookup.unreflect(m);

        m = kClazz.getDeclaredMethod("implKyber12To16Java",
                byte[].class, int.class, short[].class, int.class);
        m.setAccessible(true);
        MethodHandle twelve2SixteenJava = lookup.unreflect(m);

        m = kClazz.getDeclaredMethod("implKyberBarrettReduce",
                short[].class);
        m.setAccessible(true);
        MethodHandle barrettReduce = lookup.unreflect(m);

        m = kClazz.getDeclaredMethod("implKyberBarrettReduceJava",
                short[].class);
        m.setAccessible(true);
        MethodHandle barrettReduceJava = lookup.unreflect(m);

        Field f = kClazz.getDeclaredField("montZetasForVectorNttArr");
        f.setAccessible(true);
        short[] montZetasForVectorNttArr = (short[]) f.get(null);

        f = kClazz.getDeclaredField("montZetasForVectorInverseNttArr");
        f.setAccessible(true);
        short[] montZetasForVectorInverseNttArr = (short[]) f.get(null);

        f = kClazz.getDeclaredField("montZetasForVectorNttMultArr");
        f.setAccessible(true);
        short[] montZetasForVectorNttMultArr = (short[]) f.get(null);

        Random rnd = new Random();
        long seed = rnd.nextLong();
        // Hint: if a test fails, it prints the seed, so you can hardcode
        // it here to reproduce the failure
        rnd.setSeed(seed);
        //Note: it might be useful to increase this number during development of new intrinsics
        final int repeat = 100_000;
        short[] out1 = new short[ML_KEM_N];
        short[] out2 = new short[ML_KEM_N];
        short[] in1 = new short[ML_KEM_N];
        short[] in2 = new short[ML_KEM_N];
        short[] in3 = new short[ML_KEM_N];
        byte[] condensed = new byte[512];
        for (int i = 0; i < repeat; i++) {
            testNtt(out1, out2, ntt, nttJava,
                    montZetasForVectorNttArr, rnd, seed, i);
            testMult(out1, out2, in1, in2, mult, multJava,
                    montZetasForVectorNttMultArr, rnd, seed, i);
            testInverseNtt(out1, out2, inverseNtt, inverseNttJava,
                    montZetasForVectorInverseNttArr, rnd, seed, i);
            testAddPoly2(out1, out2, in1, in2, addPoly2, addPoly2Java,
                    rnd, seed, i);
            testAddPoly3(out1, out2, in1, in2, in3,
                    addPoly3, addPoly3Java, rnd, seed, i);
            testBarrettReduce(out1, out2, barrettReduce,
                    barrettReduceJava, rnd, seed, i);
            test12To16(condensed, out1, out2, twelve2Sixteen,
                    twelve2SixteenJava, rnd, seed, i);
        }
        System.out.println("Fuzz Success");
    }

    public static void testNtt(short[] coeffs1, short[] coeffs2,
        MethodHandle ntt, MethodHandle nttJava, short[] zetas, Random rnd,
        long seed, int i) throws Throwable {

        // implKyberNtt mutates its argument in place, so give each version
        // an independent copy of the same random input.
        for (int j = 0; j < ML_KEM_N; j++) {
            coeffs1[j] = coeffs2[j] = (short) rnd.nextInt(ML_KEM_Q);
        }

        ntt.invoke(coeffs1, zetas);
        nttJava.invoke(coeffs2);

        if (!Arrays.equals(coeffs1, coeffs2)) {
            // The Java version and the intrinsic version need not produce
            // the exact same result (although usually they do); it is enough
            // if the corresponding array elements are congruent modulo ML_KEM_Q
            boolean modQequal = true;
            for (int j = 0; j < ML_KEM_N; j++) {
                if (coeffs1[j] != coeffs2[j]) {
                    modQequal &= (((coeffs1[j] - coeffs2[j]) % ML_KEM_Q) == 0);
                }
            }
            if (!modQequal) {
                throw new RuntimeException("[Seed " + seed + "@" + i
                        + "] Result Ntt mismatch: "
                        + formatOf(coeffs1) + "\n != " + formatOf(coeffs2));
            }
        }
    }

    public static void testInverseNtt(short[] coeffs1, short[] coeffs2,
        MethodHandle inverseNtt, MethodHandle inverseNttJava, short[] zetas,
        Random rnd, long seed, int i) throws Throwable {

        for (int j = 0; j < ML_KEM_N; j++) {
            coeffs1[j] = coeffs2[j] = (short) (rnd.nextInt(2 * ML_KEM_Q) - ML_KEM_Q);
        }

        inverseNtt.invoke(coeffs1, zetas);
        inverseNttJava.invoke(coeffs2);

        if (!Arrays.equals(coeffs1, coeffs2)) {
            // The Java version and the intrinsic version need not produce
            // the exact same result (although usually they do); it is enough
            // if the corresponding array elements are congruent modulo ML_KEM_Q
            boolean modQequal = true;
            for (int j = 0; j < ML_KEM_N; j++) {
                if (coeffs1[j] != coeffs2[j]) {
                    modQequal &= (((coeffs1[j] - coeffs2[j]) % ML_KEM_Q) == 0);
                }
            }
            if (!modQequal) {
                throw new RuntimeException("[Seed " + seed + "@" + i
                        + "] Result InverseNtt mismatch: "
                        + formatOf(coeffs1) + "\n != " + formatOf(coeffs2));
            }
        }
    }

    public static void testMult(short[] prod1, short[] prod2, short[] ntta,
        short[] nttb, MethodHandle mult, MethodHandle multJava, short[] zetas,
        Random rnd, long seed, int i) throws Throwable {

        for (int j = 0; j < ML_KEM_N; j++) {
            ntta[j] = (short) rnd.nextInt(ML_KEM_Q);
            nttb[j] = (short) rnd.nextInt(ML_KEM_Q);
        }

        mult.invoke(prod1, ntta, nttb, zetas);
        multJava.invoke(prod2, ntta, nttb);

        if (!Arrays.equals(prod1, prod2)) {
            // The Java version and the intrinsic version need not produce
            // the exact same result (although usually they do); it is enough
            // if the corresponding array elements are congruent modulo ML_KEM_Q
            boolean modQequal = true;
            for (int j = 0; j < ML_KEM_N; j++) {
                if (prod1[j] != prod2[j]) {
                    modQequal &= (((prod1[j] - prod2[j]) % ML_KEM_Q) == 0);
                }
            }
            if (!modQequal) {
                throw new RuntimeException("[Seed " + seed + "@" + i
                        + "] Result mult mismatch: "
                        + formatOf(prod1) + "\n != " + formatOf(prod2)
                        + "\n ntta = " + formatOf(ntta)
                        + "\n nttb = " + formatOf(nttb));
            }
        }
    }

    public static void testAddPoly2(short[] sum1, short[] sum2, short[] a,
        short[] b, MethodHandle addPoly, MethodHandle addPolyJava, Random rnd,
        long seed, int i) throws Throwable {

        for (int j = 0; j < ML_KEM_N; j++) {
            sum1[j] = sum2[j] = (short) (rnd.nextInt(2 * ML_KEM_Q) - ML_KEM_Q);
            a[j] = (short) (rnd.nextInt(2 * ML_KEM_Q) - ML_KEM_Q);
            b[j] = (short) (rnd.nextInt(2 * ML_KEM_Q) - ML_KEM_Q);
        }

        addPoly.invoke(sum1, a, b);
        addPolyJava.invoke(sum2, a, b);

        if (!Arrays.equals(sum1, sum2)) {
            throw new RuntimeException("[Seed " + seed + "@" + i
                    + "] Result AddPoly2 mismatch: "
                    + formatOf(sum1) + "\n != " + formatOf(sum2)
                    + "\n b = " + formatOf(b));
        }
    }

    public static void testAddPoly3(short[] sum1, short[] sum2, short[] a,
        short[] b, short[] c, MethodHandle addPoly, MethodHandle addPolyJava,
        Random rnd, long seed, int i) throws Throwable {

        for (int j = 0; j < ML_KEM_N; j++) {
            a[j] = (short) (rnd.nextInt(2 * ML_KEM_Q) - ML_KEM_Q);
            b[j] = (short) (rnd.nextInt(2 * ML_KEM_Q) - ML_KEM_Q);
            c[j] = (short) (rnd.nextInt(2 * ML_KEM_Q) - ML_KEM_Q);
        }

        addPoly.invoke(sum1, a, b, c);
        addPolyJava.invoke(sum2, a, b, c);

        if (!Arrays.equals(sum1, sum2)) {
            throw new RuntimeException("[Seed " + seed + "@" + i
                    + "] Result AddPoly3 mismatch: "
                    + formatOf(sum1) + "\n != " + formatOf(sum2)
                    + "\n a = " + formatOf(a)
                    + "\n b = " + formatOf(b)
                    + "\n c = " + formatOf(c));
        }
    }

    public static void testBarrettReduce(short[] coeffs1, short[] coeffs2,
        MethodHandle barrettReduce, MethodHandle barrettReduceJava, Random rnd,
        long seed, int i) throws Throwable {

        // implKyberBarrettReduce mutates its argument in place, so give each
        // version an independent copy of the same random input. The intrinsic
        // matches the Java version over the whole short range, so this fuzzes
        // wider than the > -ML_KEM_Q inputs the real callers produce.
        for (int j = 0; j < ML_KEM_N; j++) {
            coeffs1[j] = coeffs2[j] = (short) rnd.nextInt();
        }

        barrettReduce.invoke(coeffs1);
        barrettReduceJava.invoke(coeffs2);

        if (!Arrays.equals(coeffs1, coeffs2)) {
            throw new RuntimeException("[Seed " + seed + "@" + i
                    + "] Result BarrettReduce mismatch: "
                    + formatOf(coeffs1) + "\n != " + formatOf(coeffs2));
        }
    }

    public static void test12To16(byte[] condensed, short[] parsed1,
        short[] parsed2, MethodHandle twelve2Sixteen,
        MethodHandle twelve2SixteenJava, Random rnd, long seed, int i)
        throws Throwable {

        int index = rnd.nextInt(condensed.length - 3 * ML_KEM_N / 2 + 1);
        for (int j = 0; j < condensed.length; j++) {
            condensed[j] = (byte) rnd.nextInt();
        }

        twelve2Sixteen.invoke(condensed, index, parsed1, ML_KEM_N);
        twelve2SixteenJava.invoke(condensed, index, parsed2, ML_KEM_N);

        // Both only repack bits, so the results have to match exactly
        if (!Arrays.equals(parsed1, parsed2)) {
            throw new RuntimeException("[Seed " + seed + "@" + i
                    + "] Result 12To16 mismatch: "
                    + formatOf(parsed1) + "\n != " + formatOf(parsed2)
                    + "\n index = " + index
                    + "\n condensed = " + HexFormat.of().formatHex(condensed));
        }
    }

    private static CharSequence formatOf(short[] arr) {
        StringBuilder b = new StringBuilder(arr.length*4);
        HexFormat hex = HexFormat.of();
        for (int j = 0; j<arr.length; j++) {
            b.append(hex.toHexDigits(arr[j]));
        }
        return b.toString();
    }

    // Copied constants from com.sun.crypto.provider.ML_KEM
    private static final int ML_KEM_N = 256;
    private static final int ML_KEM_Q = 3329;
}
