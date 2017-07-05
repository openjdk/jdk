/*
 * Copyright (c) 2002, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

package sun.jvm.hotspot.asm.sparc;

// format 2 - condition code names.
// Appendix F - Opcodes and Condition Codes - Page 231 - Table F-7.

class CoprocessorBranchDecoder extends BranchDecoder {
    private static final String coprocessorConditionNames[] = {
        "cbn", "cb123", "cb12", "cb13", "cb1", "cb23", "cb2", "cb3",
        "cba", "cb0",  "cb03", "cb02", "cb023", "cb01", "cb013", "cb012"
    };

    private static final String coprocessorAnnuledConditionNames[] = {
        "cbn,a", "cb123,a", "cb12,a", "cb13,a", "cb1,a", "cb23,a", "cb2,a", "cb3,a",
        "cba,a", "cb0,a",  "cb03,a", "cb02,a", "cb023,a", "cb01,a", "cb013,a", "cb012,a"
    };

    String getConditionName(int conditionCode, boolean isAnnuled) {
        return isAnnuled ? coprocessorAnnuledConditionNames[conditionCode]
                         : coprocessorConditionNames[conditionCode];
    }
}
