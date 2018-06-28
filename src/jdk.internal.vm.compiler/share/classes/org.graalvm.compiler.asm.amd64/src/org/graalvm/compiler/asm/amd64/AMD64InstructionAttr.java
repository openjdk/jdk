/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.asm.amd64;

import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.amd64.AMD64.CPUFeature;
import jdk.vm.ci.code.TargetDescription;

/**
 * Attributes for instructions for SSE through EVEX, also including address components.
 */
public class AMD64InstructionAttr {
    AMD64InstructionAttr(
                    int inVectorLen,
                    boolean inRexVexW,
                    boolean inLegacyMode,
                    boolean inNoRegMask,
                    boolean inUsesVl,
                    TargetDescription target) {
        avxVectorLen = inVectorLen;
        rexVexW = inRexVexW;
        this.target = target;
        legacyMode = (!supports(CPUFeature.AVX512F)) ? true : inLegacyMode;
        noRegMask = inNoRegMask;
        usesVl = inUsesVl;
        rexVexWReverted = false;
        tupleType = 0;
        inputSizeInBits = 0;
        isEvexInstruction = false;
        evexEncoding = 0;
        isClearContext = false;
        isExtendedContext = false;
    }

    private TargetDescription target;
    private int avxVectorLen;
    private boolean rexVexW;
    private boolean rexVexWReverted;
    private boolean legacyMode;
    private boolean noRegMask;
    private boolean usesVl;
    private int tupleType;
    private int inputSizeInBits;
    private boolean isEvexInstruction;
    private int evexEncoding;
    private boolean isClearContext;
    private boolean isExtendedContext;

    public int getVectorLen() {
        return avxVectorLen;
    }

    public boolean isRexVexW() {
        return rexVexW;
    }

    public boolean isRexVexWReverted() {
        return rexVexWReverted;
    }

    public boolean isLegacyMode() {
        return legacyMode;
    }

    public boolean isNoRegMask() {
        return noRegMask;
    }

    public boolean usesVl() {
        return usesVl;
    }

    public int getTupleType() {
        return tupleType;
    }

    public int getInputSize() {
        return inputSizeInBits;
    }

    public boolean isEvexInstruction() {
        return isEvexInstruction;
    }

    public int getEvexEncoding() {
        return evexEncoding;
    }

    public boolean isClearContext() {
        return isClearContext;
    }

    public boolean isExtendedContext() {
        return isExtendedContext;
    }

    /**
     * Set the vector length of a given instruction.
     *
     * @param vectorLen
     */
    public void setVectorLen(int vectorLen) {
        avxVectorLen = vectorLen;
    }

    /**
     * In EVEX it is possible in blended code generation to revert the encoding width for AVX.
     */
    public void setRexVexWReverted() {
        rexVexWReverted = true;
    }

    /**
     * Alter the current encoding width.
     *
     * @param state
     */
    public void setRexVexW(boolean state) {
        rexVexW = state;
    }

    /**
     * Alter the current instructions legacy mode. Blended code generation will use this.
     */
    public void setLegacyMode() {
        legacyMode = true;
    }

    /**
     * During emit or during definition of an instruction, mark if it is EVEX.
     */
    public void setIsEvexInstruction() {
        isEvexInstruction = true;
    }

    /**
     * Set the current encoding attributes to be used in address calculations for EVEX.
     *
     * @param value
     */
    public void setEvexEncoding(int value) {
        evexEncoding = value;
    }

    /**
     * Use clear context for this instruction in EVEX, defaults is merge(false).
     */
    public void setIsClearContext() {
        isClearContext = true;
    }

    /**
     * Set the address attributes for configuring displacement calculations in EVEX.
     */
    public void setAddressAttributes(int inTupleType, int inInputSizeInBits) {
        if (supports(CPUFeature.AVX512F)) {
            tupleType = inTupleType;
            inputSizeInBits = inInputSizeInBits;
        }
    }

    private boolean supports(CPUFeature feature) {
        return ((AMD64) target.arch).getFeatures().contains(feature);
    }
}
