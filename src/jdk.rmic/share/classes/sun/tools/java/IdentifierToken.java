/*
 * Copyright (c) 1996, 2003, Oracle and/or its affiliates. All rights reserved.
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

package sun.tools.java;

/**
 * Information about the occurrence of an identifier.
 * The parser produces these to represent name which cannot yet be
 * bound to field definitions.
 *
 * WARNING: The contents of this source file are not part of any
 * supported API.  Code that depends on them does so at its own risk:
 * they are subject to change or removal without notice.
 *
 * @see
 */

public
class IdentifierToken {
    long where;
    int modifiers;
    Identifier id;

    public IdentifierToken(long where, Identifier id) {
        this.where = where;
        this.id = id;
    }

    /** Use this constructor when the identifier is synthesized.
     * The location will be 0.
     */
    public IdentifierToken(Identifier id) {
        this.where = 0;
        this.id = id;
    }

    public IdentifierToken(long where, Identifier id, int modifiers) {
        this.where = where;
        this.id = id;
        this.modifiers = modifiers;
    }

    /** The source location of this identifier occurrence. */
    public long getWhere() {
        return where;
    }

    /** The identifier itself (possibly qualified). */
    public Identifier getName() {
        return id;
    }

    /** The modifiers associated with the occurrence, if any. */
    public int getModifiers() {
        return modifiers;
    }

    public String toString() {
        return id.toString();
    }

    /**
     * Return defaultWhere if id is null or id.where is missing (0).
     * Otherwise, return id.where.
     */
    public static long getWhere(IdentifierToken id, long defaultWhere) {
        return (id != null && id.where != 0) ? id.where : defaultWhere;
    }
}
