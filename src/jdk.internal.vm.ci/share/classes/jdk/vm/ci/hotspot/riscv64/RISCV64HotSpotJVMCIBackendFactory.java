/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.vm.ci.hotspot.riscv64;

import static java.util.Collections.emptyMap;
import static jdk.vm.ci.common.InitTimer.timer;

import java.util.EnumSet;
import java.util.Map;

import jdk.vm.ci.riscv64.RISCV64;
import jdk.vm.ci.riscv64.RISCV64.CPUFeature;
import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.code.RegisterConfig;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.code.stack.StackIntrospection;
import jdk.vm.ci.common.InitTimer;
import jdk.vm.ci.hotspot.HotSpotCodeCacheProvider;
import jdk.vm.ci.hotspot.HotSpotConstantReflectionProvider;
import jdk.vm.ci.hotspot.HotSpotJVMCIBackendFactory;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.hotspot.HotSpotMetaAccessProvider;
import jdk.vm.ci.hotspot.HotSpotStackIntrospection;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.runtime.JVMCIBackend;

public class RISCV64HotSpotJVMCIBackendFactory implements HotSpotJVMCIBackendFactory {

    private static EnumSet<RISCV64.CPUFeature> computeFeatures(RISCV64HotSpotVMConfig config) {
        // Configure the feature set using the HotSpot flag settings.
        Map<String, Long> constants = config.getStore().getConstants();
        return HotSpotJVMCIBackendFactory.convertFeatures(CPUFeature.class, constants, config.vmVersionFeatures, emptyMap());
    }

    private static EnumSet<RISCV64.Flag> computeFlags(RISCV64HotSpotVMConfig config) {
        EnumSet<RISCV64.Flag> flags = EnumSet.noneOf(RISCV64.Flag.class);

        if (config.useConservativeFence) {
            flags.add(RISCV64.Flag.UseConservativeFence);
        }
        if (config.avoidUnalignedAccesses) {
            flags.add(RISCV64.Flag.AvoidUnalignedAccesses);
        }
        if (config.nearCpool) {
            flags.add(RISCV64.Flag.NearCpool);
        }
        if (config.traceTraps) {
            flags.add(RISCV64.Flag.TraceTraps);
        }
        if (config.useRVV) {
            flags.add(RISCV64.Flag.UseRVV);
        }
        if (config.useRVC) {
            flags.add(RISCV64.Flag.UseRVC);
        }
        if (config.useZba) {
            flags.add(RISCV64.Flag.UseZba);
        }
        if (config.useZbb) {
            flags.add(RISCV64.Flag.UseZbb);
        }
        if (config.useRVVForBigIntegerShiftIntrinsics) {
            flags.add(RISCV64.Flag.UseRVVForBigIntegerShiftIntrinsics);
        }

        return flags;
    }

    private static TargetDescription createTarget(RISCV64HotSpotVMConfig config) {
        final int stackFrameAlignment = 16;
        final int implicitNullCheckLimit = 4096;
        final boolean inlineObjects = true;
        Architecture arch = new RISCV64(computeFeatures(config), computeFlags(config));
        return new TargetDescription(arch, true, stackFrameAlignment, implicitNullCheckLimit, inlineObjects);
    }

    protected HotSpotConstantReflectionProvider createConstantReflection(HotSpotJVMCIRuntime runtime) {
        return new HotSpotConstantReflectionProvider(runtime);
    }

    private static RegisterConfig createRegisterConfig(RISCV64HotSpotVMConfig config, TargetDescription target) {
        return new RISCV64HotSpotRegisterConfig(target, config.useCompressedOops, target.linuxOs);
    }

    protected HotSpotCodeCacheProvider createCodeCache(HotSpotJVMCIRuntime runtime, TargetDescription target, RegisterConfig regConfig) {
        return new HotSpotCodeCacheProvider(runtime, target, regConfig);
    }

    protected HotSpotMetaAccessProvider createMetaAccess(HotSpotJVMCIRuntime runtime) {
        return new HotSpotMetaAccessProvider(runtime);
    }

    @Override
    public String getArchitecture() {
        return "riscv64";
    }

    @Override
    public String toString() {
        return "JVMCIBackend:" + getArchitecture();
    }

    @Override
    @SuppressWarnings("try")
    public JVMCIBackend createJVMCIBackend(HotSpotJVMCIRuntime runtime, JVMCIBackend host) {
        assert host == null;
        RISCV64HotSpotVMConfig config = new RISCV64HotSpotVMConfig(runtime.getConfigStore());
        TargetDescription target = createTarget(config);

        RegisterConfig regConfig;
        HotSpotCodeCacheProvider codeCache;
        ConstantReflectionProvider constantReflection;
        HotSpotMetaAccessProvider metaAccess;
        StackIntrospection stackIntrospection;
        try (InitTimer t = timer("create providers")) {
            try (InitTimer rt = timer("create MetaAccess provider")) {
                metaAccess = createMetaAccess(runtime);
            }
            try (InitTimer rt = timer("create RegisterConfig")) {
                regConfig = createRegisterConfig(config, target);
            }
            try (InitTimer rt = timer("create CodeCache provider")) {
                codeCache = createCodeCache(runtime, target, regConfig);
            }
            try (InitTimer rt = timer("create ConstantReflection provider")) {
                constantReflection = createConstantReflection(runtime);
            }
            try (InitTimer rt = timer("create StackIntrospection provider")) {
                stackIntrospection = new HotSpotStackIntrospection(runtime);
            }
        }
        try (InitTimer rt = timer("instantiate backend")) {
            return createBackend(metaAccess, codeCache, constantReflection, stackIntrospection);
        }
    }

    protected JVMCIBackend createBackend(HotSpotMetaAccessProvider metaAccess, HotSpotCodeCacheProvider codeCache, ConstantReflectionProvider constantReflection,
                    StackIntrospection stackIntrospection) {
        return new JVMCIBackend(metaAccess, codeCache, constantReflection, stackIntrospection);
    }
}
