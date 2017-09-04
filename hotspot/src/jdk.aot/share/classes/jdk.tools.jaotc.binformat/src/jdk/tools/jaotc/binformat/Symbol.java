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

package jdk.tools.jaotc.binformat;

import java.util.Objects;

import jdk.tools.jaotc.binformat.NativeSymbol;

public class Symbol {

    public enum Binding {
        UNDEFINED,
        LOCAL,
        GLOBAL
    }

    public enum Kind {
        UNDEFINED,
        NATIVE_FUNCTION,
        JAVA_FUNCTION,
        OBJECT,
        NOTYPE
    }

    private final String name;
    private final int size;
    private final int offset;
    private final Binding binding;
    private final Kind kind;

    private ByteContainer section;
    private NativeSymbol nativeSymbol;

    /**
     * Create symbol info.
     *
     * @param offset section offset for the defined symbol
     * @param kind kind of the symbol (UNDEFINED, FUNC, etc)
     * @param binding binding of the symbol (LOCAL, GLOBAL, ...)
     * @param section section in which this symbol is "defined"
     * @param size size of the symbol
     * @param name name of the symbol
     */

    public Symbol(int offset, Kind kind, Binding binding, ByteContainer section, int size, String name) {
        this.binding = binding;
        this.kind = kind;
        this.section = section;
        this.size = size;
        this.offset = offset;
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public NativeSymbol getNativeSymbol() {
        return nativeSymbol;
    }

    public void setNativeSymbol(NativeSymbol nativeSym) {
        this.nativeSymbol = nativeSym;
    }

    public Binding getBinding() {
        return binding;
    }

    public Kind getKind() {
        return kind;
    }

    public int getSize() {
        return size;
    }

    public ByteContainer getSection() {
        return section;
    }

    public int getOffset() {
        return offset;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof Symbol)) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }

        Symbol symbol = (Symbol) obj;

        if (size != symbol.size) {
            return false;
        }
        if (offset != symbol.offset) {
            return false;
        }
        if (!name.equals(symbol.name)) {
            return false;
        }
        if (binding != symbol.binding) {
            return false;
        }
        if (kind != symbol.kind) {
            return false;
        }
        return !(section != null ? !section.equals(symbol.section) : symbol.section != null);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(name, binding, kind, section);
        result = 31 * result + size;
        result = 31 * result + offset;
        return result;
    }

    @Override
    public String toString() {
        return "[" + name + ", " + size + ", " + offset + ", " + binding + ", " + kind + ", " + section + "]";
    }

}
