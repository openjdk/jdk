/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package jdk.internal.classfile.impl;

import jdk.internal.classfile.BufWriter;
import jdk.internal.classfile.Label;
import jdk.internal.classfile.constantpool.Utf8Entry;

/**
 * Utilities for writing logic shared for attributes that are streamable.
 */
public final class AttributeHelpers {
    private AttributeHelpers() {
    }

    public static boolean writeLocalVariable(LabelContext lc, BufWriter b, Label start, Label end,
                                           Utf8Entry name, Utf8Entry type, int slot) {
        int startBci = lc.labelToBci(start);
        int endBci = lc.labelToBci(end);
        if (startBci == -1 || endBci == -1) {
            return false;
        }
        writeLocalVariableInfo(b, startBci, endBci - startBci, name, type, slot);
        return true;
    }

    public static void writeLocalVariableInfo(BufWriter b, int startPc, int length,
                                          Utf8Entry name, Utf8Entry type, int slot) {
        b.writeU2(startPc);
        b.writeU2(length);
        b.writeIndex(name);
        b.writeIndex(type);
        b.writeU2(slot);
    }
}
