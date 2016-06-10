/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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
package jdk.vm.ci.code.test;

import java.lang.reflect.Method;

import org.junit.Assert;

import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.code.CodeCacheProvider;
import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.code.test.amd64.AMD64TestAssembler;
import jdk.vm.ci.code.test.sparc.SPARCTestAssembler;
import jdk.vm.ci.hotspot.HotSpotCompiledCode;
import jdk.vm.ci.hotspot.HotSpotResolvedJavaMethod;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.runtime.JVMCI;
import jdk.vm.ci.runtime.JVMCIBackend;
import jdk.vm.ci.sparc.SPARC;

/**
 * Base class for code installation tests.
 */
public class CodeInstallationTest {

    protected final MetaAccessProvider metaAccess;
    protected final CodeCacheProvider codeCache;
    protected final TargetDescription target;
    protected final ConstantReflectionProvider constantReflection;

    public CodeInstallationTest() {
        JVMCIBackend backend = JVMCI.getRuntime().getHostJVMCIBackend();
        metaAccess = backend.getMetaAccess();
        codeCache = backend.getCodeCache();
        target = backend.getTarget();
        constantReflection = backend.getConstantReflection();
    }

    protected interface TestCompiler {

        void compile(TestAssembler asm);
    }

    private TestAssembler createAssembler() {
        Architecture arch = codeCache.getTarget().arch;
        if (arch instanceof AMD64) {
            return new AMD64TestAssembler(codeCache);
        } else if (arch instanceof SPARC) {
            return new SPARCTestAssembler(codeCache);
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

    protected void test(TestCompiler compiler, Method method, Object... args) {
        HotSpotResolvedJavaMethod resolvedMethod = (HotSpotResolvedJavaMethod) metaAccess.lookupJavaMethod(method);
        TestAssembler asm = createAssembler();

        asm.emitPrologue();
        compiler.compile(asm);
        asm.emitEpilogue();

        HotSpotCompiledCode code = asm.finish(resolvedMethod);
        InstalledCode installed = codeCache.addCode(resolvedMethod, code, null, null);

        try {
            Object expected = method.invoke(null, args);
            Object actual = installed.executeVarargs(args);
            Assert.assertEquals(expected, actual);
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail(e.toString());
        }
    }
}
