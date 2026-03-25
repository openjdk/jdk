/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @bug 8360851
 * @summary Test that the PrintAssembly [Entry Point] gets annotated with "# {method}".
 * @requires vm.jvmci
 * @requires vm.simpleArch == "x64" | vm.simpleArch == "aarch64" | vm.simpleArch == "riscv64"
 * @library /test/lib /
 * @modules jdk.internal.vm.ci/jdk.vm.ci.hotspot
 *          jdk.internal.vm.ci/jdk.vm.ci.meta
 *          jdk.internal.vm.ci/jdk.vm.ci.code
 *          jdk.internal.vm.ci/jdk.vm.ci.code.site
 *          jdk.internal.vm.ci/jdk.vm.ci.runtime
 *          jdk.internal.vm.ci/jdk.vm.ci.aarch64
 *          jdk.internal.vm.ci/jdk.vm.ci.amd64
 *          jdk.internal.vm.ci/jdk.vm.ci.riscv64
 * @compile TestAssembler.java TestHotSpotVMConfig.java amd64/AMD64TestAssembler.java aarch64/AArch64TestAssembler.java riscv64/RISCV64TestAssembler.java
 * @run junit/othervm -XX:+UnlockExperimentalVMOptions -XX:+EnableJVMCI -XX:-UseJVMCICompiler jdk.vm.ci.code.test.MethodTagTest
 */

package jdk.vm.ci.code.test;

import java.lang.reflect.Method;

import org.junit.Assert;
import org.junit.Test;

import jdk.test.lib.Asserts;
import jdk.vm.ci.aarch64.AArch64;
import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.code.CodeCacheProvider;
import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.code.test.aarch64.AArch64TestAssembler;
import jdk.vm.ci.code.test.amd64.AMD64TestAssembler;
import jdk.vm.ci.code.test.riscv64.RISCV64TestAssembler;
import jdk.vm.ci.hotspot.HotSpotCodeCacheProvider;
import jdk.vm.ci.hotspot.HotSpotCompiledCode;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.hotspot.HotSpotNmethod;
import jdk.vm.ci.hotspot.HotSpotResolvedJavaMethod;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.riscv64.RISCV64;
import jdk.vm.ci.runtime.JVMCI;
import jdk.vm.ci.runtime.JVMCIBackend;


public final class MethodTagTest {
    private static final boolean DEBUG = false;

    protected final MetaAccessProvider metaAccess;
    protected final CodeCacheProvider codeCache;
    protected final TargetDescription target;
    protected final ConstantReflectionProvider constantReflection;
    protected final TestHotSpotVMConfig config;
    protected final Architecture arch;

    public MethodTagTest() {
        JVMCIBackend backend = JVMCI.getRuntime().getHostJVMCIBackend();
        metaAccess = backend.getMetaAccess();
        codeCache = backend.getCodeCache();
        target = backend.getTarget();
        constantReflection = backend.getConstantReflection();
        arch = codeCache.getTarget().arch;
        config = new TestHotSpotVMConfig(HotSpotJVMCIRuntime.runtime().getConfigStore(), arch);
    }

    protected interface TestCompiler {

        void compile(TestAssembler asm);
    }

    private TestAssembler createAssembler() {
        if (arch instanceof AMD64) {
            return new AMD64TestAssembler(codeCache, config);
        } else if (arch instanceof AArch64) {
            return new AArch64TestAssembler(codeCache, config);
        } else if (arch instanceof RISCV64) {
            return new RISCV64TestAssembler(codeCache, config);
        } else {
            Assert.fail("unsupported architecture");
            return null;
        }
    }

    protected Method getMethod(String name, Class<?>... args) {
        try {
            return getClass().getMethod(name, args);
        } catch (NoSuchMethodException e) {
            Assert.fail("method not found");
            return null;
        }
    }

    public static int add(int a, int b) {
        return a + b;
    }

    private static void compileAdd(TestAssembler asm) {
        Register arg0 = asm.emitIntArg0();
        Register arg1 = asm.emitIntArg1();
        Register ret = asm.emitIntAdd(arg0, arg1);
        asm.emitIntRet(ret);
    }

    protected HotSpotNmethod test(TestCompiler compiler, Method method, Object... args) {
        try {
            HotSpotResolvedJavaMethod resolvedMethod = (HotSpotResolvedJavaMethod) metaAccess.lookupJavaMethod(method);
            TestAssembler asm = createAssembler();

            asm.emitPrologue();
            compiler.compile(asm);
            asm.emitEpilogue();

            HotSpotCompiledCode code = asm.finish(resolvedMethod);
            InstalledCode installed = codeCache.addCode(resolvedMethod, code, null, null, true);

            String str = ((HotSpotCodeCacheProvider) codeCache).disassemble(installed);
            Asserts.assertTrue(str.contains("# {method}"), "\"# {method}\" tag not found");
            if (DEBUG) {
                System.out.println(str);
            }

            Object expected = method.invoke(null, args);
            Object actual = installed.executeVarargs(args);
            Assert.assertEquals(expected, actual);
            return (HotSpotNmethod) installed;
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    @Test
    public void test() {
        HotSpotNmethod nmethod = test(MethodTagTest::compileAdd, getMethod("add", int.class, int.class), 5, 7);

        // Test code invalidation
        Asserts.assertTrue(nmethod.isValid(), "code is not valid, i = " + nmethod);
        Asserts.assertTrue(nmethod.isAlive(), "code is not alive, i = " + nmethod);
        Asserts.assertNotEquals(nmethod.getStart(), 0L);

        // Make nmethod non-entrant but still alive
        nmethod.invalidate(false);
        Asserts.assertFalse(nmethod.isValid(), "code is valid, i = " + nmethod);
        Asserts.assertTrue(nmethod.isAlive(), "code is not alive, i = " + nmethod);
        Asserts.assertEquals(nmethod.getStart(), 0L);

        // Deoptimize the nmethod and cut the link to it from the HotSpotNmethod
        nmethod.invalidate(true);
        Asserts.assertFalse(nmethod.isValid(), "code is valid, i = " + nmethod);
        Asserts.assertFalse(nmethod.isAlive(), "code is alive, i = " + nmethod);
        Asserts.assertEquals(nmethod.getStart(), 0L);
    }
}
