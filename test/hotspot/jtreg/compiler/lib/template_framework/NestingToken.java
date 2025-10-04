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

import java.util.List;

/**
 * TODO: fix comment
t* A Template generates a {@link NestingToken}, which is a list of {@link Token}s,
 * which are then later rendered to {@link String}s.
 *
 * @param tokens The list of {@link Token}s that are later rendered to {@link String}s.
 */
public sealed abstract class NestingToken implements Token permits NestingToken.Scope,
                                                                   NestingToken.Flat,
                                                                   NestingToken.NameScope,
                                                                   NestingToken.HashtagScope,
                                                                   NestingToken.SetFuelCostScope {

    List<Token> tokens;
    abstract boolean nestedNamesAreLocal();
    abstract boolean nestedHashtagsAreLocal();
    abstract boolean nestedSetFuelCostAreLocal();
    // TODO: just make it one inner record!

    private NestingToken(List<Token> tokens) {
        this.tokens = tokens;
    }

    static final class Scope extends NestingToken implements Token {
        Scope(List<Token> tokens) { super(tokens); }
        boolean nestedNamesAreLocal() { return true; }
        boolean nestedHashtagsAreLocal() { return true; }
        boolean nestedSetFuelCostAreLocal() { return true; }
    }

    static final class Flat extends NestingToken implements Token {
        Flat(List<Token> tokens) { super(tokens); }
        boolean nestedNamesAreLocal() { return false; }
        boolean nestedHashtagsAreLocal() { return false; }
        boolean nestedSetFuelCostAreLocal() { return false; }
    }

    static final class NameScope extends NestingToken implements Token {
        NameScope(List<Token> tokens) { super(tokens); }
        boolean nestedNamesAreLocal() { return true; }
        boolean nestedHashtagsAreLocal() { return false; }
        boolean nestedSetFuelCostAreLocal() { return false; }
    }

    static final class HashtagScope extends NestingToken implements Token {
        HashtagScope(List<Token> tokens) { super(tokens); }
        boolean nestedNamesAreLocal() { return false; }
        boolean nestedHashtagsAreLocal() { return true; }
        boolean nestedSetFuelCostAreLocal() { return false; }
    }

    static final class SetFuelCostScope extends NestingToken implements Token {
        SetFuelCostScope(List<Token> tokens) { super(tokens); }
        boolean nestedNamesAreLocal() { return false; }
        boolean nestedHashtagsAreLocal() { return false; }
        boolean nestedSetFuelCostAreLocal() { return true; }
    }
}
