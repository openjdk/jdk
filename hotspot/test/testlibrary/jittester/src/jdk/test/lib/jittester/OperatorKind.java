/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

package jdk.test.lib.jittester;

// all unary and binary operator kinds
public enum OperatorKind {
    COMPOUND_ADD("+=",   1),
    COMPOUND_SUB("-=",   1),
    COMPOUND_MUL("*=",   1),
    COMPOUND_DIV("-=",   1),
    COMPOUND_MOD("%=",   1),
    COMPOUND_AND("&=",   1),
    COMPOUND_OR ("|=",   1),
    COMPOUND_XOR("^=",   1),
    COMPOUND_SHR(">>=",  1),
    COMPOUND_SHL("<<=",  1),
    COMPOUND_SAR(">>>=", 1),
    ASSIGN      ("=",    1),
    OR          ("||",   3),
    BIT_OR      ("|",    5),
    BIT_XOR     ("^",    6),
    AND         ("&&",   7),
    BIT_AND     ("&",    7),
    EQ          ("==",   8),
    NE          ("!=",   8),
    GT          (">",    9),
    LT          ("<",    9),
    GE          (">=",   9),
    LE          ("<=",   9),
    SHR         (">>",  10),
    SHL         ("<<",  10),
    SAR         (">>>", 10),
    ADD         ("+",   11),
    STRADD      ("+",   11),
    SUB         ("-",   11),
    MUL         ("*",   12),
    DIV         ("/",   12),
    MOD         ("%",   12),
    NOT         ("!",   14),
    BIT_NOT     ("~",   14),
    UNARY_PLUS  ("+",   14),
    UNARY_MINUS ("-",   14),
    PRE_DEC     ("--",  15, true),
    POST_DEC    ("--",  15, false),
    PRE_INC     ("++",  16, true),
    POST_INC    ("++",  16, false),
    ;

    public final String text;
    public final int priority;
    public final boolean isPrefix; // used for unary operators

    private OperatorKind(String text, int priority) {
        this(text, priority, true);
    }

    private OperatorKind(String text, int priority, boolean isPrefix) {
        this.text = text;
        this.priority = priority;
        this.isPrefix = isPrefix;
    }
}
