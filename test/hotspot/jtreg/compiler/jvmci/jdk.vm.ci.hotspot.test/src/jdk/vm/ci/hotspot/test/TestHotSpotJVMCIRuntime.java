/*
 * Copyright (c) 2018, 2023, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @requires vm.jvmci
 * @library /test/lib /
 * @modules jdk.internal.vm.ci/jdk.vm.ci.hotspot
 *          jdk.internal.vm.ci/jdk.vm.ci.runtime
 *          jdk.internal.vm.ci/jdk.vm.ci.meta
 *          jdk.internal.vm.ci/jdk.vm.ci.code
 *          jdk.internal.vm.ci/jdk.vm.ci.common
 * @library /compiler/jvmci/jdk.vm.ci.hotspot.test/src
 *          /compiler/jvmci/jdk.vm.ci.code.test/src
 * @library /test/lib
 * @run testng/othervm
 *      -XX:+UnlockExperimentalVMOptions -XX:+EnableJVMCI -XX:-UseJVMCICompiler
 *      jdk.vm.ci.hotspot.test.TestHotSpotJVMCIRuntime
 */

package jdk.vm.ci.hotspot.test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

import org.testng.Assert;
import org.testng.annotations.Test;

import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaType;

import jdk.test.lib.Platform;

public class TestHotSpotJVMCIRuntime {

    @Test
    public void writeDebugOutputTest() {
        HotSpotJVMCIRuntime runtime = HotSpotJVMCIRuntime.runtime();

        expectWriteDebugOutputFailure(runtime, null, 0, 0, true, true, NullPointerException.class);
        expectWriteDebugOutputFailure(runtime, null, 0, 0, true, false, -1);

        byte[] emptyOutput = {};
        byte[] nonEmptyOutput = String.format("non-empty output%n").getBytes();

        for (boolean canThrow : new boolean[]{true, false}) {
            for (byte[] output : new byte[][]{emptyOutput, nonEmptyOutput}) {
                for (int offset = 0; offset < output.length; offset++) {
                    int length = output.length - offset;
                    runtime.writeDebugOutput(output, offset, length, true, canThrow);
                }

                Object expect = canThrow ? IndexOutOfBoundsException.class : -2;
                expectWriteDebugOutputFailure(runtime, output, output.length + 1, 0, true, canThrow, expect);
                expectWriteDebugOutputFailure(runtime, output, 0, output.length + 1, true, canThrow, expect);
                expectWriteDebugOutputFailure(runtime, output, -1, 0, true, canThrow, expect);
                expectWriteDebugOutputFailure(runtime, output, 0, -1, true, canThrow, expect);
            }
        }
    }

    private static void expectWriteDebugOutputFailure(HotSpotJVMCIRuntime runtime, byte[] bytes, int offset, int length, boolean flush, boolean canThrow, Object expect) {
        try {
            int result = runtime.writeDebugOutput(bytes, offset, length, flush, canThrow);
            if (expect instanceof Integer) {
                Assert.assertEquals((int) expect, result);
            } else {
                Assert.fail("expected " + expect + ", got " + result + " for bytes == " + Arrays.toString(bytes));
            }
        } catch (Exception e) {
            if (expect instanceof Integer) {
                Assert.fail("expected " + expect + ", got " + e + " for bytes == " + Arrays.toString(bytes));
            } else {
                Assert.assertTrue(((Class<?>) expect).isInstance(e), e.toString());
            }
        }
    }

    @Test
    public void getIntrinsificationTrustPredicateTest() throws Exception {
        HotSpotJVMCIRuntime runtime = HotSpotJVMCIRuntime.runtime();
        MetaAccessProvider metaAccess = runtime.getHostJVMCIBackend().getMetaAccess();
        Predicate<ResolvedJavaType> predicate = runtime.getIntrinsificationTrustPredicate(HotSpotJVMCIRuntime.class);
        List<Class<?>> classes = new ArrayList<>(Arrays.asList(
                        Object.class,
                        String.class,
                        Class.class,
                        HotSpotJVMCIRuntime.class,
                        VirtualObjectLayoutTest.class,
                        TestHotSpotJVMCIRuntime.class));
        try {
            classes.add(Class.forName("com.sun.crypto.provider.AESCrypt"));
            classes.add(Class.forName("com.sun.crypto.provider.CipherBlockChaining"));
        } catch (ClassNotFoundException e) {
            // Extension classes not available
        }
        ClassLoader jvmciLoader = HotSpotJVMCIRuntime.class.getClassLoader();
        ClassLoader platformLoader = ClassLoader.getPlatformClassLoader();
        for (Class<?> c : classes) {
            ClassLoader cl = c.getClassLoader();
            boolean expected = cl == null || cl == jvmciLoader || cl == platformLoader;
            boolean actual = predicate.test(metaAccess.lookupJavaType(c));
            Assert.assertEquals(expected, actual, c + ": cl=" + cl);
        }
    }

    /**
     * Test program that calls into the VM and expects an {@code OutOfMemoryError} to be
     * raised when {@code test.jvmci.forceEnomemOnLibjvmciInit == true}.
     *
     * For example:
     * <pre>
     * Exception in thread "main" java.lang.OutOfMemoryError: JNI_ENOMEM creating or attaching to libjvmci
     *    at jdk.internal.vm.ci/jdk.vm.ci.hotspot.CompilerToVM.attachCurrentThread(Native Method)
     *    at jdk.internal.vm.ci/jdk.vm.ci.hotspot.HotSpotJVMCIRuntime.attachCurrentThread(HotSpotJVMCIRuntime.java:1385)
     *    at jdk.vm.ci.hotspot.test.TestHotSpotJVMCIRuntime$JNIEnomemVMCall.main(TestHotSpotJVMCIRuntime.java:133)
     * </pre>
     */
    public static class JNIEnomemVMCall {
        public static void main(String[] args) {
            String name = args[0];
            HotSpotJVMCIRuntime runtime = HotSpotJVMCIRuntime.runtime();
            MetaAccessProvider metaAccess = runtime.getHostJVMCIBackend().getMetaAccess();
            if (name.equals("translate")) {
                runtime.translate("object");
            } else if (name.equals("attachCurrentThread")) {
                runtime.attachCurrentThread(false, null);
            } else if (name.equals("registerNativeMethods")) {
                runtime.registerNativeMethods(JNIEnomemVMCall.class);
            } else {
                throw new InternalError("Unknown method: " + name);
            }
        }
    }

    @Test
    public void jniEnomemTest() throws Exception {
        if (!Platform.isDebugBuild()) {
            // The test.jvmci.forceEnomemOnLibjvmciInit property is only
            // read in a debug VM.
            return;
        }
        String[] names = {"translate", "attachCurrentThread", "registerNativeMethods"};
        for (String name : names) {
            ProcessBuilder pb = ProcessTools.createLimitedTestJavaProcessBuilder(
                "-XX:+UnlockExperimentalVMOptions",
                "-XX:+EnableJVMCI",
                "-XX:-UseJVMCICompiler",
                "-XX:+UseJVMCINativeLibrary",
                "-Dtest.jvmci.forceEnomemOnLibjvmciInit=true",
                "--add-exports=jdk.internal.vm.ci/jdk.vm.ci.services=ALL-UNNAMED",
                "--add-exports=jdk.internal.vm.ci/jdk.vm.ci.runtime=ALL-UNNAMED",
                "--add-exports=jdk.internal.vm.ci/jdk.vm.ci.hotspot=ALL-UNNAMED",
                "-Xbootclasspath/a:.",
                JNIEnomemVMCall.class.getName(), name);
            OutputAnalyzer output = new OutputAnalyzer(pb.start());
            output.shouldContain("java.lang.OutOfMemoryError: JNI_ENOMEM creating or attaching to libjvmci");
            output.shouldNotHaveExitValue(0);
        }
    }

    @Test
    public void lookupTypeTest() throws Exception {
        // This is tested by compiler/jvmci/compilerToVM/LookupTypeTest.java
    }
}
