/*
 * Copyright (c) 2011, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.vm.ci.hotspot;

import static jdk.vm.ci.hotspot.CompilerToVM.listFromTrustedArray;

import jdk.vm.ci.code.BytecodeFrame;
import jdk.vm.ci.code.CompiledCode;
import jdk.vm.ci.code.StackSlot;
import jdk.vm.ci.code.VirtualObject;
import jdk.vm.ci.code.site.DataPatch;
import jdk.vm.ci.code.site.Infopoint;
import jdk.vm.ci.code.site.Site;
import jdk.vm.ci.meta.Assumptions.Assumption;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;

import java.util.Arrays;
import java.util.List;

/**
 * A {@link CompiledCode} with additional HotSpot-specific information required for installing the
 * code in HotSpot's code cache.
 */
public class HotSpotCompiledCode implements CompiledCode {

    /**
     * The name of this compilation unit.
     */
    protected final String name;

    /**
     * The buffer containing the emitted machine code.
     */
    protected final byte[] targetCode;

    /**
     * The leading number of bytes in {@link #targetCode} containing the emitted machine code.
     */
    protected final int targetCodeSize;

    /**
     * A list of code annotations describing special sites in {@link #targetCode}.
     */
    protected final List<Site> sites;

    /**
     * A list of {@link Assumption} this code relies on.
     */
    protected final List<Assumption> assumptions;

    /**
     * The list of the methods whose bytecodes were used as input to the compilation. If
     * empty, then the compilation did not record method dependencies. Otherwise, the first
     * element of this array is the root method of the compilation.
     */
    protected final List<ResolvedJavaMethod> methods;

    /**
     * A list of comments that will be included in code dumps.
     */
    protected final List<Comment> comments;

    /**
     * The data section containing serialized constants for the emitted machine code.
     */
    protected final byte[] dataSection;

    /**
     * The minimum alignment of the data section.
     */
    protected final int dataSectionAlignment;

    /**
     * A list of relocations in the {@link #dataSection}.
     */
    protected final List<DataPatch> dataSectionPatches;

    /**
     * A flag determining whether this code is immutable and position independent.
     */
    protected final boolean isImmutablePIC;

    /**
     * The total size of the stack frame of this compiled method.
     */
    protected final int totalFrameSize;

    /**
     * The deopt rescue slot. Must be non-null if there is a safepoint in the method.
     */
    protected final StackSlot deoptRescueSlot;

    public static class Comment {

        public final String text;
        public final int pcOffset;

        public Comment(int pcOffset, String text) {
            this.text = text;
            this.pcOffset = pcOffset;
        }
    }

    /**
     * @param name                 the name of this compilation unit.
     * @param targetCode           the buffer containing the emitted machine code. This array is now owned by this object and should not be mutated by the caller.
     * @param targetCodeSize       the leading number of bytes in {@link #targetCode} containing the emitted machine code.
     * @param sites                an array of code annotations describing special sites in {@link #targetCode}. This array is now owned by this object and should not be mutated by the caller.
     * @param assumptions          an array of {@link Assumption} this code relies on. This array is now owned by this object and should not be mutated by the caller.
     * @param methods              an array of the methods whose bytecodes were used as input to the compilation. This array is now owned by this object and should not be mutated by the caller.
     * @param comments             an array of comments that will be included in code dumps. This array is now owned by this object and should not be mutated by the caller.
     * @param dataSection          the data section containing serialized constants for the emitted machine code. This array is now owned by this object and should not be mutated by the caller.
     * @param dataSectionAlignment the minimum alignment of the data section.
     * @param dataSectionPatches   an array of relocations in the {@link #dataSection}. This array is now owned by this object and should not be mutated by the caller.
     * @param isImmutablePIC       the flag determining whether this code is immutable and position independent.
     * @param totalFrameSize       the total size of the stack frame of this compiled method.
     * @param deoptRescueSlot      the deopt rescue slot. Must be non-null if there is a safepoint in the method.
     */
    public HotSpotCompiledCode(String name,
                               byte[] targetCode,
                               int targetCodeSize,
                               Site[] sites,
                               Assumption[] assumptions,
                               ResolvedJavaMethod[] methods,
                               Comment[] comments,
                               byte[] dataSection,
                               int dataSectionAlignment,
                               DataPatch[] dataSectionPatches,
                               boolean isImmutablePIC,
                               int totalFrameSize,
                               StackSlot deoptRescueSlot) {
        this.name = name;
        this.targetCode = targetCode;
        this.targetCodeSize = targetCodeSize;
        this.sites = listFromTrustedArray(sites);
        this.assumptions = listFromTrustedArray(assumptions);
        this.methods = listFromTrustedArray(methods);

        this.comments = listFromTrustedArray(comments);
        this.dataSection = dataSection;
        this.dataSectionAlignment = dataSectionAlignment;
        this.dataSectionPatches = listFromTrustedArray(dataSectionPatches);
        this.isImmutablePIC = isImmutablePIC;
        this.totalFrameSize = totalFrameSize;
        this.deoptRescueSlot = deoptRescueSlot;

        assert targetCode != null && dataSection != null;
        assert validateFrames();
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return name;
    }

    /**
     * Ensure that all the frames passed into the VM are properly formatted with an empty or illegal
     * slot following double word slots.
     */
    private boolean validateFrames() {
        for (Site site : sites) {
            if (site instanceof Infopoint info) {
                if (info.debugInfo != null) {
                    BytecodeFrame frame = info.debugInfo.frame();
                    assert frame == null || frame.validateFormat();
                    if (info.debugInfo.getVirtualObjectMapping() != null) {
                        for (VirtualObject v : info.debugInfo.getVirtualObjectMapping()) {
                            verifyVirtualObject(v);
                        }
                    }
                }
            }
        }
        return true;
    }

    public static void verifyVirtualObject(VirtualObject v) {
        v.verifyLayout(new VirtualObject.LayoutVerifier() {
            @Override
            public int getOffset(ResolvedJavaField field) {
                return field.getOffset();
            }
        });
    }

    /**
     * Returns a copy of the compiled machine code.
     */
    public byte[] getTargetCode() {
        return targetCode.clone();
    }

    /**
     * Gets the size of the compiled machine code in bytes.
     */
    public int getTargetCodeSize() {
        return targetCodeSize;
    }

    /**
     * Returns the list of code annotations describing special sites in {@link #targetCode}.
     */
    public List<Site> getSites() {
        return sites;
    }

    /**
     * Returns list of {@link Assumption} this code relies on.
     */
    public List<Assumption> getAssumptions() {
        return assumptions;
    }

    /**
     * Returns the list of the methods whose bytecodes were used as input to the compilation
     */
    public List<ResolvedJavaMethod> getMethods() {
        return methods;
    }

    /**
     * Returns the list of comments that will be included in code dumps.
     */
    public List<Comment> getComments() {
        return comments;
    }

    /**
     * Returns a copy of the data section containing serialized constants for the emitted machine code.
     */
    public byte[] getDataSection() {
        return dataSection.clone();
    }

    /**
     * Gets the minimum alignment of the data section.
     */
    public int getDataSectionAlignment() {
        return dataSectionAlignment;
    }

    /**
     * Gets the list of relocations in the {@link #dataSection}.
     */
    public List<DataPatch> getDataSectionPatches() {
        return dataSectionPatches;
    }

    /**
     * Checks if this compiled code is immutable and position independent.
     */
    public boolean isImmutablePIC() {
        return isImmutablePIC;
    }

    /**
     * Gets the total size of the stack frame of this compiled method.
     */
    public int getTotalFrameSize() {
        return totalFrameSize;
    }

    /**
     * Gets the deoptimization rescue slot associated with this compiled code.
     */
    public StackSlot getDeoptRescueSlot() {
        return deoptRescueSlot;
    }
}
