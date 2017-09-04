/*
 * Copyright (c) 2016, 2017, Oracle and/or its affiliates. All rights reserved.
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
import jdk.tools.jaotc.binformat.ByteContainer;
import jdk.tools.jaotc.binformat.CodeContainer;
import jdk.tools.jaotc.binformat.Relocation;
import jdk.tools.jaotc.binformat.Relocation.RelocType;
import jdk.tools.jaotc.binformat.Symbol;
import jdk.tools.jaotc.binformat.Symbol.Binding;
import jdk.tools.jaotc.binformat.Symbol.Kind;

/**
 * Describes a relocation symbol of a call site. That is, WHERE to do a relocation.
 */
abstract class CallSiteRelocationSymbol {

    final Symbol symbol;

    CallSiteRelocationSymbol(Symbol symbol) {
        assert symbol != null;
        this.symbol = symbol;
    }

    protected static Symbol createCodeContainerSymbol(BinaryContainer binaryContainer, String symbolName, int symbolOffset) {
        return binaryContainer.getCodeContainer().createSymbol(symbolOffset, Kind.OBJECT, Binding.LOCAL, 0, symbolName);
    }

    protected static void addCodeContainerRelocation(BinaryContainer binaryContainer, String symbolName, int symbolOffset, int relocationOffset) {
        Symbol symbol = createCodeContainerSymbol(binaryContainer, symbolName, symbolOffset);
        addExternalGotToPltRelocation(binaryContainer, symbol, relocationOffset);
    }

    protected static void addExtLinkageGotContainerRelocation(BinaryContainer binaryContainer, String symbolName, int symbolOffset, int relocationOffset) {
        ByteContainer container = binaryContainer.getExtLinkageGOTContainer();
        Symbol symbol = container.createGotSymbol(symbolOffset, symbolName);
        addExternalPltToGotRelocation(binaryContainer, symbol, relocationOffset);
    }

    /**
     * Add an {@link RelocType#EXTERNAL_GOT_TO_PLT} relocation to the
     * {@link BinaryContainer#getExtLinkageGOTContainer()}.
     */
    private static void addExternalGotToPltRelocation(BinaryContainer binaryContainer, Symbol symbol, int relocationOffset) {
        ByteContainer container = binaryContainer.getExtLinkageGOTContainer();
        Relocation relocation = new Relocation(relocationOffset, RelocType.EXTERNAL_GOT_TO_PLT, 8, container, symbol);
        binaryContainer.addRelocation(relocation);
    }

    /**
     * Add an {@link RelocType#EXTERNAL_PLT_TO_GOT} relocation to the
     * {@link BinaryContainer#getCodeContainer()}.
     */
    protected static void addExternalPltToGotRelocation(BinaryContainer binaryContainer, Symbol symbol, int relocationOffset) {
        CodeContainer container = binaryContainer.getCodeContainer();
        Relocation relocation = new Relocation(relocationOffset, RelocType.EXTERNAL_PLT_TO_GOT, 8, container, symbol);
        binaryContainer.addRelocation(relocation);
    }

}
