/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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



package org.graalvm.compiler.hotspot.test;

import java.lang.reflect.InvocationTargetException;
import java.math.BigInteger;
import java.util.Random;

import org.graalvm.compiler.api.test.Graal;
import org.graalvm.compiler.core.test.GraalCompilerTest;
import org.graalvm.compiler.hotspot.GraalHotSpotVMConfig;
import org.graalvm.compiler.hotspot.HotSpotGraalRuntimeProvider;
import org.graalvm.compiler.runtime.RuntimeProvider;

import org.junit.Test;

import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.code.InvalidInstalledCodeException;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/*
 * Indirectly test intrinsic/call substitutions for (innate) methods:
 *
 *      BigInteger.implMultiplyToLen
 *      BigInteger.implMulAdd
 *      BigInteger.implMontgomeryMultiply
 *      BigInteger.implMontgomerySquare
 *      BigInteger.implSquareToLen
 *
 * via BigInteger.multiply() and .modPow(). Note that the actual substitution
 * is not tested per se (only execution based on admissible intrinsics).
 *
 */
public final class BigIntegerIntrinsicsTest extends GraalCompilerTest {

    static final int N = 100;

    @Test
    public void testMultiplyToLen() throws ClassNotFoundException {

        // Intrinsic must be available.
        org.junit.Assume.assumeTrue(config.useMultiplyToLenIntrinsic());
        // Test case is (currently) AMD64 only.
        org.junit.Assume.assumeTrue(getTarget().arch instanceof AMD64);

        Class<?> javaclass = Class.forName("java.math.BigInteger");

        TestIntrinsic tin = new TestIntrinsic("testMultiplyAux", javaclass,
                        "multiply", BigInteger.class);

        for (int i = 0; i < N; i++) {

            BigInteger big1 = randomBig(i);
            BigInteger big2 = randomBig(i);

            // Invoke BigInteger BigInteger.multiply(BigInteger)
            BigInteger res1 = (BigInteger) tin.invokeJava(big1, big2);

            // Invoke BigInteger testMultiplyAux(BigInteger)
            BigInteger res2 = (BigInteger) tin.invokeTest(big1, big2);

            assertDeepEquals(res1, res2);

            // Invoke BigInteger testMultiplyAux(BigInteger) through code handle.
            BigInteger res3 = (BigInteger) tin.invokeCode(big1, big2);

            assertDeepEquals(res1, res3);
        }
    }

    @Test
    public void testMulAdd() throws ClassNotFoundException {

        // Intrinsic must be available.
        org.junit.Assume.assumeTrue(config.useMulAddIntrinsic() ||
                        config.useSquareToLenIntrinsic());
        // Test case is (currently) AMD64 only.
        org.junit.Assume.assumeTrue(getTarget().arch instanceof AMD64);

        Class<?> javaclass = Class.forName("java.math.BigInteger");

        TestIntrinsic tin = new TestIntrinsic("testMultiplyAux", javaclass,
                        "multiply", BigInteger.class);

        for (int i = 0; i < N; i++) {

            BigInteger big1 = randomBig(i);

            // Invoke BigInteger BigInteger.multiply(BigInteger)
            BigInteger res1 = (BigInteger) tin.invokeJava(big1, big1);

            // Invoke BigInteger testMultiplyAux(BigInteger)
            BigInteger res2 = (BigInteger) tin.invokeTest(big1, big1);

            assertDeepEquals(res1, res2);

            // Invoke BigInteger testMultiplyAux(BigInteger) through code handle.
            BigInteger res3 = (BigInteger) tin.invokeCode(big1, big1);

            assertDeepEquals(res1, res3);
        }
    }

    @Test
    public void testMontgomery() throws ClassNotFoundException {

        // Intrinsic must be available.
        org.junit.Assume.assumeTrue(config.useMontgomeryMultiplyIntrinsic() ||
                        config.useMontgomerySquareIntrinsic());
        // Test case is (currently) AMD64 only.
        org.junit.Assume.assumeTrue(getTarget().arch instanceof AMD64);

        Class<?> javaclass = Class.forName("java.math.BigInteger");

        TestIntrinsic tin = new TestIntrinsic("testMontgomeryAux", javaclass,
                        "modPow", BigInteger.class, BigInteger.class);

        for (int i = 0; i < N; i++) {

            BigInteger big1 = randomBig(i);
            BigInteger big2 = randomBig(i);

            // Invoke BigInteger BigInteger.modPow(BigExp, BigInteger)
            BigInteger res1 = (BigInteger) tin.invokeJava(big1, bigTwo, big2);

            // Invoke BigInteger testMontgomeryAux(BigInteger, BigExp, BigInteger)
            BigInteger res2 = (BigInteger) tin.invokeTest(big1, bigTwo, big2);

            assertDeepEquals(res1, res2);

            // Invoke BigInteger testMontgomeryAux(BigInteger, BigExp, BigInteger)
            // through code handle.
            BigInteger res3 = (BigInteger) tin.invokeCode(big1, bigTwo, big2);

            assertDeepEquals(res1, res3);
        }
    }

    public static BigInteger testMultiplyAux(BigInteger a, BigInteger b) {
        return a.multiply(b);
    }

    public static BigInteger testMontgomeryAux(BigInteger a, BigInteger exp, BigInteger b) {
        return a.modPow(exp, b);
    }

    private class TestIntrinsic {

        TestIntrinsic(String testmname, Class<?> javaclass, String javamname, Class<?>... params) {
            javamethod = getResolvedJavaMethod(javaclass, javamname, params);
            testmethod = getResolvedJavaMethod(testmname);

            assert javamethod != null;
            assert testmethod != null;

            // Force the test method to be compiled.
            testcode = getCode(testmethod);

            assert testcode != null;
            assert testcode.isValid();
        }

        Object invokeJava(BigInteger big, Object... args) {
            return invokeSafe(javamethod, big, args);
        }

        Object invokeTest(Object... args) {
            return invokeSafe(testmethod, null, args);
        }

        Object invokeCode(Object... args) {
            try {
                return testcode.executeVarargs(args);
            } catch (InvalidInstalledCodeException e) {
                // Ensure the installed code is valid, possibly recompiled.
                testcode = getCode(testmethod);

                assert testcode != null;
                assert testcode.isValid();

                return invokeCode(args);
            }
        }

        private Object invokeSafe(ResolvedJavaMethod method, Object receiver, Object... args) {
            try {
                return invoke(method, receiver, args);
            } catch (IllegalAccessException | InvocationTargetException | IllegalArgumentException | InstantiationException e) {
                throw new RuntimeException(e);
            }
        }

        // Private data section:
        private ResolvedJavaMethod javamethod;
        private ResolvedJavaMethod testmethod;
        private InstalledCode testcode;
    }

    private static GraalHotSpotVMConfig config = ((HotSpotGraalRuntimeProvider) Graal.getRequiredCapability(RuntimeProvider.class)).getVMConfig();

    private static BigInteger bigTwo = BigInteger.valueOf(2);
    private static Random rnd = new Random(17);

    private static BigInteger randomBig(int i) {
        return new BigInteger(rnd.nextInt(4096) + i2sz(i), rnd);
    }

    private static int i2sz(int i) {
        return i * 3 + 1;
    }
}
