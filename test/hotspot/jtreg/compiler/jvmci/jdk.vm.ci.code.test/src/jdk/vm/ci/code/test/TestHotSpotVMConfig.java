/*
 * Copyright (c) 2016, 2025, Oracle and/or its affiliates. All rights reserved.
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

import jdk.vm.ci.aarch64.AArch64;
import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.hotspot.HotSpotVMConfigAccess;
import jdk.vm.ci.hotspot.HotSpotVMConfigStore;

public class TestHotSpotVMConfig extends HotSpotVMConfigAccess {

    public TestHotSpotVMConfig(HotSpotVMConfigStore config, Architecture arch) {
        super(config);
        ropProtection = (arch instanceof AArch64) ? getFieldValue("VM_Version::_rop_protection", Boolean.class) : false;
        nmethodEntryBarrierConcurrentPatch = initNmethodEntryBarrierConcurrentPatch(arch);
    }

    public final boolean useCompressedOops = getFlag("UseCompressedOops", Boolean.class);
    public final boolean useCompressedClassPointers = getFlag("UseCompressedClassPointers", Boolean.class);

    public final long narrowOopBase = getFieldValue("CompilerToVM::Data::Universe_narrow_oop_base", Long.class, "address");
    public final int narrowOopShift = getFieldValue("CompilerToVM::Data::Universe_narrow_oop_shift", Integer.class, "int");

    public final long narrowKlassBase = getFieldValue("CompilerToVM::Data::Universe_narrow_klass_base", Long.class, "address");
    public final int narrowKlassShift = getFieldValue("CompilerToVM::Data::Universe_narrow_klass_shift", Integer.class, "int");

    public final int classMirrorHandleOffset = getFieldOffset("Klass::_java_mirror", Integer.class, "OopHandle");

    // Checkstyle: stop
    public final int MARKID_DEOPT_HANDLER_ENTRY = getConstant("CodeInstaller::DEOPT_HANDLER_ENTRY", Integer.class);
    public final int MARKID_FRAME_COMPLETE = getConstant("CodeInstaller::FRAME_COMPLETE", Integer.class);
    public final int MARKID_ENTRY_BARRIER_PATCH = getConstant("CodeInstaller::ENTRY_BARRIER_PATCH", Integer.class);
    public final long handleDeoptStub = getFieldValue("CompilerToVM::Data::SharedRuntime_deopt_blob_unpack", Long.class, "address");

    public final int maxOopMapStackOffset = getFieldValue("CompilerToVM::Data::_max_oop_map_stack_offset", Integer.class, "int");
    public final int heapWordSize = getConstant("HeapWordSize", Integer.class);

    public final int NMETHOD_INVALIDATION_REASON_JVMCI_INVALIDATE = getConstant("nmethod::InvalidationReason::JVMCI_INVALIDATE", Integer.class);

    public final boolean ropProtection;

    private Boolean initNmethodEntryBarrierConcurrentPatch(Architecture arch) {
        Boolean patchConcurrent = null;
        if (arch instanceof AArch64 && nmethodEntryBarrier != 0) {
            Integer patchingType = getFieldValue("CompilerToVM::Data::BarrierSetAssembler_nmethod_patching_type", Integer.class, "int");
            if (patchingType != null) {
                // There currently only 2 variants in use that differ only by the presence of a
                // dmb instruction
                int stw = getConstant("NMethodPatchingType::stw_instruction_and_data_patch", Integer.class);
                int conc1 = getConstant("NMethodPatchingType::conc_data_patch", Integer.class);
                int conc2 = getConstant("NMethodPatchingType::conc_instruction_and_data_patch", Integer.class);
                if (patchingType == stw) {
                    patchConcurrent = false;
                } else if (patchingType == conc1 || patchingType == conc2) {
                    patchConcurrent = true;
                } else {
                    throw new IllegalArgumentException("unsupported barrier sequence " + patchingType);
                }
            }
        }
        return patchConcurrent;
    }

    public final int threadDisarmedOffset = getFieldValue("CompilerToVM::Data::thread_disarmed_guard_value_offset", Integer.class, "int");
    public final long nmethodEntryBarrier = getFieldValue("CompilerToVM::Data::nmethod_entry_barrier", Long.class, "address");
    public final Boolean nmethodEntryBarrierConcurrentPatch;
}
