/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.javac.parser;

import com.sun.tools.javac.parser.Tokens.Token;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCErroneous;
import com.sun.tools.javac.util.JCDiagnostic.DiagnosticPosition;
import com.sun.tools.javac.util.JCDiagnostic.Error;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Position.LineMap;

import java.util.Optional;
import java.util.Queue;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * The virtual parser allows for speculative parsing while not commiting to
 * consuming tokens unless the speculation is successful.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public class VirtualParser extends JavacParser {

    private boolean hasErrors;

    public VirtualParser(JavacParser parser) {
        super(parser, new VirtualScanner(parser.S));
    }

    @Override
    protected JCErroneous syntaxError(int pos, Error errorKey) {
        hasErrors = true;
        return F.Erroneous();
    }

    @Override
    protected JCErroneous syntaxError(int pos, List<JCTree> errs, Error errorKey) {
        hasErrors = true;
        return F.Erroneous();
    }

    @Override
    protected void reportSyntaxError(int pos, Error errorKey) {
        hasErrors = true;
    }

    @Override
    protected void reportSyntaxError(DiagnosticPosition diagPos, Error errorKey) {
        hasErrors = true;
    }

    public boolean hasErrors() {
        return hasErrors;
    }

    /**
     * Scanner that does token lookahead and throws AssertionErrors if an error
     * occurs.
     */
    public static class VirtualScanner implements Lexer {
        /** Parent scanner.
         */
        Lexer S;

        /** Token offset from where parent scanner branched.
         */
        int offset = 0;

        /** The token, set by nextToken().
         */
        private Token token;

        /** The previous token, set by nextToken().
         */
        private Token prevToken;

        public VirtualScanner(Lexer s) {
            while (s instanceof VirtualScanner virtualScanner) {
                s = virtualScanner.S;
                offset += virtualScanner.offset;
            }
            S = s;
            token = s.token();
            prevToken = S.prevToken();
        }

        @Override
        public void nextToken() {
            prevToken = token;
            offset++;
            token = token();
        }

        @Override
        public Token token() {
            return token(0);
        }

        @Override
        public Token token(int lookahead) {
            return S.token(offset + lookahead);
        }

        @Override
        public Token prevToken() {
            return prevToken;
        }

        @Override
        public void setPrevToken(Token prevToken) {
            this.prevToken = prevToken;
        }

        @Override
        public Token split() {
            Token[] splitTokens = token.split(((Scanner)S).tokens);
            prevToken = splitTokens[0];
            token = splitTokens[1];
            return token;
        }

        @Override
        public Queue<Tokens.Comment> getDocComments() {
            return S.getDocComments();
        }

        @Override
        public int errPos() {
            return S.errPos();
        }

        @Override
        public void errPos(int pos) {
            S.errPos(pos);
        }

        @Override
        public LineMap getLineMap() {
            return S.getLineMap();
        }

        public void commit() {
            for (int i = 0 ; i < offset ; i++) {
                S.nextToken(); // advance underlying lexer until position matches
            }
        }
    }

    /**
     * Attempts a parse action and returns true if successful or false if
     * a parse error is thrown.
     *
     * @param parser        parent parser
     * @param parserAction  function that takes a parser and invokes a method on that parser
     *
     * @return true if successful
     */
    public static boolean tryParse(JavacParser parser, Consumer<JavacParser> parserAction) {
        VirtualParser virtualParser = new VirtualParser(parser);
        try {
            parserAction.accept(virtualParser);
            return true;
        } catch (AssertionError ex) {
            return false;
        }
    }
}
