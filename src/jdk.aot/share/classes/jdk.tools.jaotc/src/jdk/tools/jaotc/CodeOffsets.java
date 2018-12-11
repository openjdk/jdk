/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
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

import java.util.List;

import jdk.vm.ci.code.site.Mark;

final class CodeOffsets {
    private final int entry;
    private final int verifiedEntry;
    private final int exceptionHandler;
    private final int deoptHandler;

    private CodeOffsets(int entry, int verifiedEntry, int exceptionHandler, int deoptHandler) {
        this.entry = entry;
        this.verifiedEntry = verifiedEntry;
        this.exceptionHandler = exceptionHandler;
        this.deoptHandler = deoptHandler;
    }

    static CodeOffsets buildFrom(List<Mark> marks) {
        int entry = 0;
        int verifiedEntry = 0;
        int exceptionHandler = -1;
        int deoptHandler = -1;

        for (Mark mark : marks) {
            if (mark.id instanceof Integer) {
                MarkId markId = MarkId.getEnum((int) mark.id);
                switch (markId) {
                    case UNVERIFIED_ENTRY:
                        entry = mark.pcOffset;
                        break;
                    case VERIFIED_ENTRY:
                        verifiedEntry = mark.pcOffset;
                        break;
                    case OSR_ENTRY:
                        // Unhandled
                        break;
                    case EXCEPTION_HANDLER_ENTRY:
                        exceptionHandler = mark.pcOffset;
                        break;
                    case DEOPT_HANDLER_ENTRY:
                        deoptHandler = mark.pcOffset;
                        break;
                    default:
                        break; // Ignore others
                }
            }
        }
        return new CodeOffsets(entry, verifiedEntry, exceptionHandler, deoptHandler);
    }

    int entry() {
        return entry;
    }

    int verifiedEntry() {
        return verifiedEntry;
    }

    int exceptionHandler() {
        return exceptionHandler;
    }

    int deoptHandler() {
        return deoptHandler;
    }
}
