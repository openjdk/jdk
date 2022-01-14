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

package com.sun.org.apache.xpath.internal.compiler;

import java.util.Arrays;

/**
 * XPath tokens
 */
public final class Token {
    static final char EM = '!';
    static final char EQ = '=';
    static final char LT = '<';
    static final char GT = '>';
    static final char PLUS = '+';
    static final char MINUS = '-';
    static final char STAR = '*';
    static final char VBAR = '|';
    static final char SLASH = '/';
    static final char LBRACK = '[';
    static final char RBRACK = ']';
    static final char LPAREN = '(';
    static final char RPAREN = ')';
    static final char COMMA = ',';
    static final char DOT = '.';
    static final char AT = '@';
    static final char US = '_';
    static final char COLON = ':';
    static final char SQ = '\'';
    static final char DQ = '"';
    static final char DOLLAR = '$';

    static final String OR = "or";
    static final String AND = "and";
    static final String DIV = "div";
    static final String MOD = "mod";
    static final String QUO = "quo";
    static final String DDOT = "..";
    static final String DCOLON = "::";
    static final String ATTR = "attribute";
    static final String CHILD = "child";

    static final String[] OPERATORS = {OR, AND, DIV, MOD, QUO,
        DDOT, DCOLON, ATTR, CHILD};

    public static boolean contains(String str) {
        return Arrays.stream(OPERATORS).anyMatch(str::equals);
    }

    private Token() {
        //to prevent instantiation
    }
}
