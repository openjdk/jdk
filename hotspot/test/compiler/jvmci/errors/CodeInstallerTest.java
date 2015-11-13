/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

package compiler.jvmci.errors;

import java.lang.reflect.Method;

import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.code.CodeCacheProvider;
import jdk.vm.ci.code.CompilationResult;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.PlatformKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.hotspot.HotSpotConstantReflectionProvider;
import jdk.vm.ci.runtime.JVMCI;
import jdk.vm.ci.runtime.JVMCIBackend;

import org.junit.Assert;

public class CodeInstallerTest {

    protected final Architecture arch;
    protected final CodeCacheProvider codeCache;
    protected final MetaAccessProvider metaAccess;
    protected final HotSpotConstantReflectionProvider constantReflection;

    protected final ResolvedJavaMethod dummyMethod;

    public static void dummyMethod() {
    }

    protected CodeInstallerTest() {
        JVMCIBackend backend = JVMCI.getRuntime().getHostJVMCIBackend();
        metaAccess = backend.getMetaAccess();
        codeCache = backend.getCodeCache();
        constantReflection = (HotSpotConstantReflectionProvider) backend.getConstantReflection();
        arch = codeCache.getTarget().arch;

        Method method = null;
        try {
            method = CodeInstallerTest.class.getMethod("dummyMethod");
        } catch (NoSuchMethodException e) {
            Assert.fail();
        }

        dummyMethod = metaAccess.lookupJavaMethod(method);
    }

    protected void installCode(CompilationResult result) {
        codeCache.addCode(dummyMethod, result, null, null);
    }

    protected CompilationResult createEmptyCompilationResult() {
        CompilationResult ret = new CompilationResult();
        ret.setTotalFrameSize(0);
        return ret;
    }

    protected Register getRegister(PlatformKind kind, int index) {
        Register[] allRegs = arch.getAvailableValueRegisters();
        for (int i = 0; i < allRegs.length; i++) {
            if (arch.canStoreValue(allRegs[i].getRegisterCategory(), kind)) {
                if (index-- == 0) {
                    return allRegs[i];
                }
            }
        }
        return null;
    }
}
