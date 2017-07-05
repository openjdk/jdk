/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
package jdk.vm.ci.meta;

public class LineNumberTableImpl implements LineNumberTable {

    private final int[] lineNumbers;
    private final int[] bci;

    public LineNumberTableImpl(int[] lineNumbers, int[] bci) {
        this.lineNumbers = lineNumbers;
        this.bci = bci;
    }

    @Override
    public int[] getLineNumberEntries() {
        return lineNumbers;
    }

    @Override
    public int[] getBciEntries() {
        return bci;
    }

    @Override
    public int getLineNumber(@SuppressWarnings("hiding") int bci) {
        for (int i = 0; i < this.bci.length - 1; i++) {
            if (this.bci[i] <= bci && bci < this.bci[i + 1]) {
                return lineNumbers[i];
            }
        }
        return lineNumbers[lineNumbers.length - 1];
    }
}
