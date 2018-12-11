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

public final class Relocation {

    // @formatter:off (workaround for Eclipse formatting bug)
    public enum RelocType {
        UNDEFINED,
        JAVA_CALL_INDIRECT,
        JAVA_CALL_DIRECT,
        FOREIGN_CALL_INDIRECT_GOT, // Call to address in GOT cell
        STUB_CALL_DIRECT,
        METASPACE_GOT_REFERENCE,
        EXTERNAL_GOT_TO_PLT,
        EXTERNAL_PLT_TO_GOT
    }
    // @formatter:on

    private final RelocType type;

    /**
     * Byte offset from the beginning of the file affected by relocation.
     */
    private final int offset;

    /**
     * Size of relocation.
     */
    private final int size;

    /**
     * Symbol associated with this relocation.
     */
    private final Symbol symbol;

    /**
     * Section this relocation entry modifies.
     */
    private final ByteContainer section;

    public Relocation(int offset, RelocType type, int size, ByteContainer section, Symbol sym) {
        if (sym == null) {
            throw new InternalError("must have symbol");
        }
        this.offset = offset;
        this.type = type;
        this.size = size;
        this.symbol = sym;
        this.section = section;
        section.setHasRelocations();
    }

    public RelocType getType() {
        return type;
    }

    public int getOffset() {
        return offset;
    }

    public int getSize() {
        return size;
    }

    public Symbol getSymbol() {
        return symbol;
    }

    public ByteContainer getSection() {
        return section;
    }

}
