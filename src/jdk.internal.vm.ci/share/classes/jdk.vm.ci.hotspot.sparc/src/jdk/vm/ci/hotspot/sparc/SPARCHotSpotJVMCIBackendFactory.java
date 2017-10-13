/*
 * Copyright (c) 2012, 2016, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.vm.ci.common.InitTimer.timer;

import java.util.EnumSet;

import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.code.RegisterConfig;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.code.stack.StackIntrospection;
import jdk.vm.ci.common.InitTimer;
import jdk.vm.ci.hotspot.HotSpotCodeCacheProvider;
import jdk.vm.ci.hotspot.HotSpotConstantReflectionProvider;
import jdk.vm.ci.hotspot.HotSpotJVMCIBackendFactory;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntimeProvider;
import jdk.vm.ci.hotspot.HotSpotMetaAccessProvider;
import jdk.vm.ci.hotspot.HotSpotStackIntrospection;
import jdk.vm.ci.runtime.JVMCIBackend;
import jdk.vm.ci.sparc.SPARC;
import jdk.vm.ci.sparc.SPARC.CPUFeature;

public class SPARCHotSpotJVMCIBackendFactory implements HotSpotJVMCIBackendFactory {

    protected TargetDescription createTarget(SPARCHotSpotVMConfig config) {
        final int stackFrameAlignment = 16;
        final int implicitNullCheckLimit = 4096;
        final boolean inlineObjects = false;
        Architecture arch = new SPARC(computeFeatures(config));
        return new TargetDescription(arch, true, stackFrameAlignment, implicitNullCheckLimit, inlineObjects);
    }

    protected HotSpotCodeCacheProvider createCodeCache(HotSpotJVMCIRuntimeProvider runtime, TargetDescription target, RegisterConfig regConfig) {
        return new HotSpotCodeCacheProvider(runtime, runtime.getConfig(), target, regConfig);
    }

    protected EnumSet<CPUFeature> computeFeatures(SPARCHotSpotVMConfig config) {
        EnumSet<CPUFeature> features = EnumSet.noneOf(CPUFeature.class);

        if ((config.vmVersionFeatures & 1L << config.sparc_ADI) != 0) {
            features.add(CPUFeature.ADI);
        }
        if ((config.vmVersionFeatures & 1L << config.sparc_AES) != 0) {
            features.add(CPUFeature.AES);
        }
        if ((config.vmVersionFeatures & 1L << config.sparc_BLK_INIT) != 0) {
            features.add(CPUFeature.BLK_INIT);
        }
        if ((config.vmVersionFeatures & 1L << config.sparc_CAMELLIA) != 0) {
            features.add(CPUFeature.CAMELLIA);
        }
        if ((config.vmVersionFeatures & 1L << config.sparc_CBCOND) != 0) {
            features.add(CPUFeature.CBCOND);
        }
        if ((config.vmVersionFeatures & 1L << config.sparc_CRC32C) != 0) {
            features.add(CPUFeature.CRC32C);
        }
        if ((config.vmVersionFeatures & 1L << config.sparc_DES) != 0) {
            features.add(CPUFeature.DES);
        }
        if ((config.vmVersionFeatures & 1L << config.sparc_DICTUNP) != 0) {
            features.add(CPUFeature.DICTUNP);
        }
        if ((config.vmVersionFeatures & 1L << config.sparc_FMAF) != 0) {
            features.add(CPUFeature.FMAF);
        }
        if ((config.vmVersionFeatures & 1L << config.sparc_FPCMPSHL) != 0) {
            features.add(CPUFeature.FPCMPSHL);
        }
        if ((config.vmVersionFeatures & 1L << config.sparc_HPC) != 0) {
            features.add(CPUFeature.HPC);
        }
        if ((config.vmVersionFeatures & 1L << config.sparc_IMA) != 0) {
            features.add(CPUFeature.IMA);
        }
        if ((config.vmVersionFeatures & 1L << config.sparc_KASUMI) != 0) {
            features.add(CPUFeature.KASUMI);
        }
        if ((config.vmVersionFeatures & 1L << config.sparc_MD5) != 0) {
            features.add(CPUFeature.MD5);
        }
        if ((config.vmVersionFeatures & 1L << config.sparc_MME) != 0) {
            features.add(CPUFeature.MME);
        }
        if ((config.vmVersionFeatures & 1L << config.sparc_MONT) != 0) {
            features.add(CPUFeature.MONT);
        }
        if ((config.vmVersionFeatures & 1L << config.sparc_MPMUL) != 0) {
            features.add(CPUFeature.MPMUL);
        }
        if ((config.vmVersionFeatures & 1L << config.sparc_MWAIT) != 0) {
            features.add(CPUFeature.MWAIT);
        }
        if ((config.vmVersionFeatures & 1L << config.sparc_PAUSE) != 0) {
            features.add(CPUFeature.PAUSE);
        }
        if ((config.vmVersionFeatures & 1L << config.sparc_PAUSE_NSEC) != 0) {
            features.add(CPUFeature.PAUSE_NSEC);
        }
        if ((config.vmVersionFeatures & 1L << config.sparc_POPC) != 0) {
            features.add(CPUFeature.POPC);
        }
        if ((config.vmVersionFeatures & 1L << config.sparc_RLE) != 0) {
            features.add(CPUFeature.RLE);
        }
        if ((config.vmVersionFeatures & 1L << config.sparc_SHA1) != 0) {
            features.add(CPUFeature.SHA1);
        }
        if ((config.vmVersionFeatures & 1L << config.sparc_SHA256) != 0) {
            features.add(CPUFeature.SHA256);
        }
        if ((config.vmVersionFeatures & 1L << config.sparc_SHA3) != 0) {
            features.add(CPUFeature.SHA3);
        }
        if ((config.vmVersionFeatures & 1L << config.sparc_SHA512) != 0) {
            features.add(CPUFeature.SHA512);
        }
        if ((config.vmVersionFeatures & 1L << config.sparc_SPARC5) != 0) {
            features.add(CPUFeature.SPARC5);
        }
        if ((config.vmVersionFeatures & 1L << config.sparc_SPARC5B) != 0) {
            features.add(CPUFeature.SPARC5B);
        }
        if ((config.vmVersionFeatures & 1L << config.sparc_SPARC6) != 0) {
            features.add(CPUFeature.SPARC6);
        }
        if ((config.vmVersionFeatures & 1L << config.sparc_V9) != 0) {
            features.add(CPUFeature.V9);
        }
        if ((config.vmVersionFeatures & 1L << config.sparc_VAMASK) != 0) {
            features.add(CPUFeature.VAMASK);
        }
        if ((config.vmVersionFeatures & 1L << config.sparc_VIS1) != 0) {
            features.add(CPUFeature.VIS1);
        }
        if ((config.vmVersionFeatures & 1L << config.sparc_VIS2) != 0) {
            features.add(CPUFeature.VIS2);
        }
        if ((config.vmVersionFeatures & 1L << config.sparc_VIS3) != 0) {
            features.add(CPUFeature.VIS3);
        }
        if ((config.vmVersionFeatures & 1L << config.sparc_VIS3B) != 0) {
            features.add(CPUFeature.VIS3B);
        }
        if ((config.vmVersionFeatures & 1L << config.sparc_VIS3C) != 0) {
            features.add(CPUFeature.VIS3C);
        }
        if ((config.vmVersionFeatures & 1L << config.sparc_XMONT) != 0) {
            features.add(CPUFeature.XMONT);
        }
        if ((config.vmVersionFeatures & 1L << config.sparc_XMPMUL) != 0) {
            features.add(CPUFeature.XMPMUL);
        }

        if ((config.vmVersionFeatures & 1L << config.sparc_BLK_ZEROING) != 0) {
            features.add(CPUFeature.BLK_ZEROING);
        }
        if ((config.vmVersionFeatures & 1L << config.sparc_FAST_BIS) != 0) {
            features.add(CPUFeature.FAST_BIS);
        }
        if ((config.vmVersionFeatures & 1L << config.sparc_FAST_CMOVE) != 0) {
            features.add(CPUFeature.FAST_CMOVE);
        }
        if ((config.vmVersionFeatures & 1L << config.sparc_FAST_IDIV) != 0) {
            features.add(CPUFeature.FAST_IDIV);
        }
        if ((config.vmVersionFeatures & 1L << config.sparc_FAST_IND_BR) != 0) {
            features.add(CPUFeature.FAST_IND_BR);
        }
        if ((config.vmVersionFeatures & 1L << config.sparc_FAST_LD) != 0) {
            features.add(CPUFeature.FAST_LD);
        }
        if ((config.vmVersionFeatures & 1L << config.sparc_FAST_RDPC) != 0) {
            features.add(CPUFeature.FAST_RDPC);
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
        SPARCHotSpotVMConfig config = new SPARCHotSpotVMConfig(runtime.getConfigStore());
        TargetDescription target = createTarget(config);

        HotSpotMetaAccessProvider metaAccess = new HotSpotMetaAccessProvider(runtime);
        RegisterConfig regConfig = new SPARCHotSpotRegisterConfig(target, config.useCompressedOops);
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
