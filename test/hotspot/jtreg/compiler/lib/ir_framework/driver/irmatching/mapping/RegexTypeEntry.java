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

package compiler.lib.ir_framework.driver.irmatching.mapping;

import compiler.lib.ir_framework.CompilePhase;
import compiler.lib.ir_framework.RegexType;

/**
 * This class represents a mapping entry for an IR node that maps to a single regex for all compile phases whose
 * {@link CompilePhase#regexType()} match the specified {@link RegexType} for this entry.
 */
public class RegexTypeEntry implements IRNodeMapEntry {
    private final SingleRegexEntry singleRegexEntry;
    private final RegexType regexType;

    public RegexTypeEntry(RegexType regexType, String regex) {
        this.regexType = regexType;
        CompilePhase defaultCompilePhase = switch (regexType) {
            case IDEAL_INDEPENDENT -> CompilePhase.PRINT_IDEAL;
            case MACH -> CompilePhase.FINAL_CODE;
            case OPTO_ASSEMBLY -> CompilePhase.PRINT_OPTO_ASSEMBLY;
        };
        this.singleRegexEntry = new SingleRegexEntry(defaultCompilePhase, regex);
    }

    @Override
    public String regexForCompilePhase(CompilePhase compilePhase) {
        if (compilePhase.regexType() == regexType) {
            return singleRegexEntry.regex();
        } else {
            return null;
        }
    }

    @Override
    public CompilePhase defaultCompilePhase() {
        return singleRegexEntry.defaultCompilePhase();
    }
}
