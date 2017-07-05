/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

import jdk.vm.ci.hotspot.HotSpotVMConfigAccess;
import jdk.vm.ci.hotspot.HotSpotVMConfigStore;

/**
 * Used to access native configuration details.
 *
 * All non-static, public fields in this class are so that they can be compiled as constants.
 */
class SPARCHotSpotVMConfig extends HotSpotVMConfigAccess {

    SPARCHotSpotVMConfig(HotSpotVMConfigStore config) {
        super(config);
    }

    final boolean useCompressedOops = getFlag("UseCompressedOops", Boolean.class);

    // CPU capabilities
    final long vmVersionFeatures = getFieldValue("Abstract_VM_Version::_features", Long.class, "uint64_t");

    // SPARC specific values
    final int sparcVis3Instructions = getConstant("VM_Version::vis3_instructions_m", Integer.class);
    final int sparcVis2Instructions = getConstant("VM_Version::vis2_instructions_m", Integer.class);
    final int sparcVis1Instructions = getConstant("VM_Version::vis1_instructions_m", Integer.class);
    final int sparcCbcondInstructions = getConstant("VM_Version::cbcond_instructions_m", Integer.class);
    final int sparcV8Instructions = getConstant("VM_Version::v8_instructions_m", Integer.class);
    final int sparcHardwareMul32 = getConstant("VM_Version::hardware_mul32_m", Integer.class);
    final int sparcHardwareDiv32 = getConstant("VM_Version::hardware_div32_m", Integer.class);
    final int sparcHardwareFsmuld = getConstant("VM_Version::hardware_fsmuld_m", Integer.class);
    final int sparcHardwarePopc = getConstant("VM_Version::hardware_popc_m", Integer.class);
    final int sparcV9Instructions = getConstant("VM_Version::v9_instructions_m", Integer.class);
    final int sparcSun4v = getConstant("VM_Version::sun4v_m", Integer.class);
    final int sparcBlkInitInstructions = getConstant("VM_Version::blk_init_instructions_m", Integer.class);
    final int sparcFmafInstructions = getConstant("VM_Version::fmaf_instructions_m", Integer.class);
    final int sparcSparc64Family = getConstant("VM_Version::sparc64_family_m", Integer.class);
    final int sparcMFamily = getConstant("VM_Version::M_family_m", Integer.class);
    final int sparcTFamily = getConstant("VM_Version::T_family_m", Integer.class);
    final int sparcT1Model = getConstant("VM_Version::T1_model_m", Integer.class);
    final int sparcSparc5Instructions = getConstant("VM_Version::sparc5_instructions_m", Integer.class);
    final int sparcAesInstructions = getConstant("VM_Version::aes_instructions_m", Integer.class);
    final int sparcSha1Instruction = getConstant("VM_Version::sha1_instruction_m", Integer.class);
    final int sparcSha256Instruction = getConstant("VM_Version::sha256_instruction_m", Integer.class);
    final int sparcSha512Instruction = getConstant("VM_Version::sha512_instruction_m", Integer.class);

    final boolean useBlockZeroing = getFlag("UseBlockZeroing", Boolean.class);
    final int blockZeroingLowLimit = getFlag("BlockZeroingLowLimit", Integer.class);
}
