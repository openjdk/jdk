/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.classfile.instruction.LineNumber;

public final class LineNumberImpl
        extends AbstractElement
        implements LineNumber {
    private static final int INTERN_LIMIT = 1000;
    private static final LineNumber[] internCache = new LineNumber[INTERN_LIMIT];
    static {
        for (int i=0; i<INTERN_LIMIT; i++)
            internCache[i] = new LineNumberImpl(i);
    }

    private final int line;

    private LineNumberImpl(int line) {
        this.line = line;
    }

    public static LineNumber of(int line) {
        return (line < INTERN_LIMIT)
               ? internCache[line]
               : new LineNumberImpl(line);
    }

    @Override
    public int line() {
        return line;
    }

    @Override
    public void writeTo(DirectCodeBuilder writer) {
        writer.setLineNumber(line);
    }

    @Override
    public String toString() {
        return String.format("LineNumber[line=%d]", line);
    }
}

