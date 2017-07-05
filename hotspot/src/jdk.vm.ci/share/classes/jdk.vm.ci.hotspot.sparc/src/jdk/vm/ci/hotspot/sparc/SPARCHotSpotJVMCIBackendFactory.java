/*
 * Copyright (c) 2012, 2015, Oracle and/or its affiliates. All rights reserved.
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
package jdk.vm.ci.hotspot.sparc;

import static jdk.vm.ci.inittimer.InitTimer.timer;

import java.util.EnumSet;

import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.code.RegisterConfig;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.code.stack.StackIntrospection;
import jdk.vm.ci.hotspot.HotSpotCodeCacheProvider;
import jdk.vm.ci.hotspot.HotSpotConstantReflectionProvider;
import jdk.vm.ci.hotspot.HotSpotJVMCIBackendFactory;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntimeProvider;
import jdk.vm.ci.hotspot.HotSpotMetaAccessProvider;
import jdk.vm.ci.hotspot.HotSpotStackIntrospection;
import jdk.vm.ci.hotspot.HotSpotVMConfig;
import jdk.vm.ci.inittimer.InitTimer;
import jdk.vm.ci.runtime.JVMCIBackend;
import jdk.vm.ci.service.ServiceProvider;
import jdk.vm.ci.sparc.SPARC;
import jdk.vm.ci.sparc.SPARC.CPUFeature;

@ServiceProvider(HotSpotJVMCIBackendFactory.class)
public class SPARCHotSpotJVMCIBackendFactory implements HotSpotJVMCIBackendFactory {

    protected TargetDescription createTarget(HotSpotVMConfig config) {
        final int stackFrameAlignment = 16;
        final int implicitNullCheckLimit = 4096;
        final boolean inlineObjects = false;
        Architecture arch = new SPARC(computeFeatures(config));
        return new TargetDescription(arch, true, stackFrameAlignment, implicitNullCheckLimit, inlineObjects);
    }

    protected HotSpotCodeCacheProvider createCodeCache(HotSpotJVMCIRuntimeProvider runtime, TargetDescription target, RegisterConfig regConfig) {
        return new HotSpotCodeCacheProvider(runtime, runtime.getConfig(), target, regConfig);
    }

    protected EnumSet<CPUFeature> computeFeatures(HotSpotVMConfig config) {
        EnumSet<CPUFeature> features = EnumSet.noneOf(CPUFeature.class);
        if ((config.sparcFeatures & config.vis1Instructions) != 0) {
            features.add(CPUFeature.VIS1);
        }
        if ((config.sparcFeatures & config.vis2Instructions) != 0) {
            features.add(CPUFeature.VIS2);
        }
        if ((config.sparcFeatures & config.vis3Instructions) != 0) {
            features.add(CPUFeature.VIS3);
        }
        if ((config.sparcFeatures & config.cbcondInstructions) != 0) {
            features.add(CPUFeature.CBCOND);
        }
        if ((config.sparcFeatures & config.v8Instructions) != 0) {
            features.add(CPUFeature.V8);
        }
        if ((config.sparcFeatures & config.hardwareMul32) != 0) {
            features.add(CPUFeature.HARDWARE_MUL32);
        }
        if ((config.sparcFeatures & config.hardwareDiv32) != 0) {
            features.add(CPUFeature.HARDWARE_DIV32);
        }
        if ((config.sparcFeatures & config.hardwareFsmuld) != 0) {
            features.add(CPUFeature.HARDWARE_FSMULD);
        }
        if ((config.sparcFeatures & config.hardwarePopc) != 0) {
            features.add(CPUFeature.HARDWARE_POPC);
        }
        if ((config.sparcFeatures & config.v9Instructions) != 0) {
            features.add(CPUFeature.V9);
        }
        if ((config.sparcFeatures & config.sun4v) != 0) {
            features.add(CPUFeature.SUN4V);
        }
        if ((config.sparcFeatures & config.blkInitInstructions) != 0) {
            features.add(CPUFeature.BLK_INIT_INSTRUCTIONS);
        }
        if ((config.sparcFeatures & config.fmafInstructions) != 0) {
            features.add(CPUFeature.FMAF);
        }
        if ((config.sparcFeatures & config.fmauInstructions) != 0) {
            features.add(CPUFeature.FMAU);
        }
        if ((config.sparcFeatures & config.sparc64Family) != 0) {
            features.add(CPUFeature.SPARC64_FAMILY);
        }
        if ((config.sparcFeatures & config.mFamily) != 0) {
            features.add(CPUFeature.M_FAMILY);
        }
        if ((config.sparcFeatures & config.tFamily) != 0) {
            features.add(CPUFeature.T_FAMILY);
        }
        if ((config.sparcFeatures & config.t1Model) != 0) {
            features.add(CPUFeature.T1_MODEL);
        }
        if ((config.sparcFeatures & config.sparc5Instructions) != 0) {
            features.add(CPUFeature.SPARC5);
        }
        if ((config.sparcFeatures & config.aesInstructions) != 0) {
            features.add(CPUFeature.SPARC64_FAMILY);
        }
        if ((config.sparcFeatures & config.sha1Instruction) != 0) {
            features.add(CPUFeature.SHA1);
        }
        if ((config.sparcFeatures & config.sha256Instruction) != 0) {
            features.add(CPUFeature.SHA256);
        }
        if ((config.sparcFeatures & config.sha512Instruction) != 0) {
            features.add(CPUFeature.SHA512);
        }
        return features;
    }

    @Override
    public String getArchitecture() {
        return "SPARC";
    }

    @Override
    public String toString() {
        return "JVMCIBackend:" + getArchitecture();
    }

    @SuppressWarnings("try")
    public JVMCIBackend createJVMCIBackend(HotSpotJVMCIRuntimeProvider runtime, JVMCIBackend host) {
        assert host == null;
        TargetDescription target = createTarget(runtime.getConfig());

        HotSpotMetaAccessProvider metaAccess = new HotSpotMetaAccessProvider(runtime);
        RegisterConfig regConfig = new SPARCHotSpotRegisterConfig(target.arch, runtime.getConfig());
        HotSpotCodeCacheProvider codeCache = createCodeCache(runtime, target, regConfig);
        HotSpotConstantReflectionProvider constantReflection = new HotSpotConstantReflectionProvider(runtime);
        StackIntrospection stackIntrospection = new HotSpotStackIntrospection(runtime);
        try (InitTimer rt = timer("instantiate backend")) {
            return createBackend(metaAccess, codeCache, constantReflection, stackIntrospection);
        }
    }

    protected JVMCIBackend createBackend(HotSpotMetaAccessProvider metaAccess, HotSpotCodeCacheProvider codeCache, HotSpotConstantReflectionProvider constantReflection,
                    StackIntrospection stackIntrospection) {
        return new JVMCIBackend(metaAccess, codeCache, constantReflection, stackIntrospection);
    }
}
