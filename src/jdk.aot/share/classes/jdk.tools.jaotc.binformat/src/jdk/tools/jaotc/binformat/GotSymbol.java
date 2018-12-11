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



package jdk.tools.jaotc.binformat;

public final class GotSymbol extends Symbol {

    private static final int GOT_SIZE = 8;

    public int getIndex() {
        int offset = getOffset();
        assert (offset % GOT_SIZE) == 0 : "got cells should be aligned: " + offset;
        return offset / GOT_SIZE;
    }

    /**
     * Create GOT symbol info.
     *
     * @param type type of the symbol (UNDEFINED, FUNC, etc)
     * @param binding binding of the symbol (LOCAL, GLOBAL, ...)
     * @param container section in which this symbol is "defined"
     * @param name name of the symbol
     */
    public GotSymbol(Kind type, Binding binding, ByteContainer container, String name) {
        this(container.getByteStreamSize(), type, binding, container, name);
        container.appendBytes(new byte[GOT_SIZE], 0, GOT_SIZE);
    }

    /**
     * Create GOT symbol info.
     *
     * @param offset section offset for the defined symbol
     * @param type type of the symbol (UNDEFINED, FUNC, etc)
     * @param binding binding of the symbol (LOCAL, GLOBAL, ...)
     * @param sec section in which this symbol is "defined"
     * @param name name of the symbol
     */
    public GotSymbol(int offset, Kind type, Binding binding, ByteContainer sec, String name) {
        super(offset, type, binding, sec, GOT_SIZE, name);
    }

}
