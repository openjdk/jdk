/*
 * Copyright (c) 2015, 2025, Oracle and/or its affiliates. All rights reserved.
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

package compiler.jvmci.common;

import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.code.CodeCacheProvider;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.StackSlot;
import jdk.vm.ci.code.site.DataPatch;
import jdk.vm.ci.code.site.Site;
import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.hotspot.HotSpotCompiledCode;
import jdk.vm.ci.hotspot.HotSpotCompiledCode.Comment;
import jdk.vm.ci.hotspot.HotSpotCompiledNmethod;
import jdk.vm.ci.hotspot.HotSpotConstantReflectionProvider;
import jdk.vm.ci.hotspot.HotSpotResolvedJavaMethod;
import jdk.vm.ci.meta.Assumptions.Assumption;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.PlatformKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.SpeculationLog;
import jdk.vm.ci.runtime.JVMCI;
import jdk.vm.ci.runtime.JVMCIBackend;
import org.junit.Assert;

import java.lang.reflect.Method;

public class CodeInstallerTest {

    protected final Architecture arch;
    protected final CodeCacheProvider codeCache;
    protected final MetaAccessProvider metaAccess;
    protected final HotSpotConstantReflectionProvider constantReflection;

    protected final HotSpotResolvedJavaMethod dummyMethod;

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

        dummyMethod = (HotSpotResolvedJavaMethod) metaAccess.lookupJavaMethod(method);
    }

    protected InstalledCode installEmptyCode(Site[] sites,
                                    Assumption[] assumptions,
                                    Comment[] comments,
                                    int dataSectionAlignment,
                                    DataPatch[] dataSectionPatches,
                                    StackSlot deoptRescueSlot) {
        ResolvedJavaMethod[] methods = {dummyMethod};
        byte[] targetCode = {0};
        int targetCodeSize = targetCode.length;
        boolean isImmutablePIC = false;
        int totalFrameSize = 0;
        int entryBCI = 0;
        int id = 1;
        long compileState = 0L;
        boolean hasUnsafeAccess = false;

        HotSpotCompiledCode code =
            new HotSpotCompiledNmethod("dummyMethod",
                                    targetCode,
                                    targetCodeSize,
                                    sites,
                                    assumptions,
                                    methods,
                                    comments,
                                    new byte[8],
                                    dataSectionAlignment,
                                    dataSectionPatches,
                                    isImmutablePIC,
                                    totalFrameSize,
                                    deoptRescueSlot,
                                    dummyMethod,
                                    entryBCI,
                                    id,
                                    compileState,
                                    hasUnsafeAccess);
        SpeculationLog log = null;
        InstalledCode installedCode = null;
        return codeCache.addCode(dummyMethod, code, log, installedCode, true);
    }

    protected Register getRegister(PlatformKind kind, int index) {
        int idx = index;
        for (Register reg : arch.getAvailableValueRegisters()) {
            if (arch.canStoreValue(reg.getRegisterCategory(), kind)) {
                if (idx-- == 0) {
                    return reg;
                }
            }
        }
        return null;
    }
}
