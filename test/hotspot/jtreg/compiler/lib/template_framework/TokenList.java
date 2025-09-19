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
 * The {@link TokenList} represents the tokens genrated by a Template call, TODO: else?
 * Note, that such a {@link TokenList} has to be used exactly once in a {@link Template},
 * otherwise a {@link RendererException} is thrown. Using it exactly once means the
 * generated string corresponding to the {@link Token}s of the {@link TokenList} occurs
 * exactly once in the rendered string.
 */
public final class TokenList implements Token {
    private final List<Token> tokens;
    private boolean hasBeenUsed = false;

    TokenList(List<Token> tokens) {
        this.tokens = List.copyOf(tokens); // defensive immutable copy
        // TODO: register with Renderer, so we can throw an exception if it is never used!
    }

    List<Token> getTokens() {
        if (this.hasBeenUsed) {
            throw new RendererException("Duplicate use of TokenList not permitted. " +
                                        "Did you duplicate the output from a Template.call" +
                                        "or ... TODO?");
        }
        this.hasBeenUsed = true;
        return tokens;
    }
}
