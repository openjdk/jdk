/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

package compiler.lib.template_framework;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;

/**
 * Helper class for {@link Token}, to keep the parsing methods package private.
 *
 * <p>
 * The {@link Template#body} and {@link Hook#anchor} are given a list of tokens, which are either
 * {@link Token}s or {@link String}s or some permitted boxed primitives. These are then parsed
 * and all non-{@link Token}s are converted to {@link StringToken}s. The parsing also flattens
 * {@link List}s.
 */
final class TokenParser {
    static List<Token> parse(Object[] objects) {
        if (objects == null) {
            throw new IllegalArgumentException("Unexpected tokens: null");
        }
        List<Token> outputList = new ArrayList<>();
        parseToken(Arrays.asList(objects), outputList);
        return outputList;
    }

    private static void parseList(List<?> inputList, List<Token> outputList) {
        for (Object o : inputList) {
            parseToken(o, outputList);
        }
    }

    private static void parseToken(Object o, List<Token> outputList) {
        if (o == null) {
            throw new IllegalArgumentException("Unexpected token: null");
        }
        switch (o) {
            case Token t   -> outputList.add(t);
            case String s  -> outputList.add(new StringToken(Renderer.format(s)));
            case Integer s -> outputList.add(new StringToken(Renderer.format(s)));
            case Long s    -> outputList.add(new StringToken(Renderer.format(s)));
            case Double s  -> outputList.add(new StringToken(Renderer.format(s)));
            case Float s   -> outputList.add(new StringToken(Renderer.format(s)));
            case Boolean s -> outputList.add(new StringToken(Renderer.format(s)));
            case List<?> l -> parseList(l, outputList);
            default -> throw new IllegalArgumentException("Unexpected token: " + o);
        }
    }
}
