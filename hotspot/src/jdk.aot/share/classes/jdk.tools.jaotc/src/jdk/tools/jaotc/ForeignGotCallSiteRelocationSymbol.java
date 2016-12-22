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

package jdk.tools.jaotc;

import jdk.tools.jaotc.binformat.BinaryContainer;
import jdk.tools.jaotc.binformat.Symbol;

import jdk.vm.ci.code.site.Call;

final class ForeignGotCallSiteRelocationSymbol extends CallSiteRelocationSymbol {

    public ForeignGotCallSiteRelocationSymbol(CompiledMethodInfo mi, Call call, CallSiteRelocationInfo callSiteRelocation, DataBuilder dataBuilder) {
        super(createPltSymbol(dataBuilder, mi, call, callSiteRelocation));
    }

    private static Symbol createPltSymbol(DataBuilder dataBuilder, CompiledMethodInfo mi, Call call, CallSiteRelocationInfo callSiteRelocation) {
        BinaryContainer binaryContainer = dataBuilder.getBinaryContainer();
        String vmSymbolName = callSiteRelocation.targetSymbol;

        // Add relocation to GOT cell for call resolution jump.
        String pltSymbolName = "plt." + vmSymbolName;
        Symbol pltSymbol = binaryContainer.getSymbol(pltSymbolName);

        if (pltSymbol == null) {
            String gotSymbolName = "got." + vmSymbolName;
            Symbol gotSymbol = binaryContainer.getGotSymbol(gotSymbolName);
            assert gotSymbol != null : "undefined VM got symbol '" + gotSymbolName + "' for call at " + call.pcOffset + " in " + mi.getMethodInfo().getSymbolName();

            // Generate PLT jump (do it only once).
            final int pltStartOffset = binaryContainer.getCodeContainer().getByteStreamSize();
            final int pltEndOffset = pltStartOffset + addPltJump(dataBuilder);

            // Link GOT cell to PLT jump.
            pltSymbol = createCodeContainerSymbol(binaryContainer, pltSymbolName, pltStartOffset);
            addExternalPltToGotRelocation(binaryContainer, gotSymbol, pltEndOffset);
        }

        return pltSymbol;
    }

    private static int addPltJump(DataBuilder dataBuilder) {
        ELFMacroAssembler masm = ELFMacroAssembler.getELFMacroAssembler(dataBuilder.getBackend().getTarget());
        byte[] code = masm.getPLTJumpCode(); // It includes alignment nops.
        int size = masm.currentEndOfInstruction();
        dataBuilder.getBinaryContainer().appendCodeBytes(code, 0, code.length);
        return size;
    }

}
