/*
 * Copyright (c) 2012, 2025, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.source.tree.AnnotatedTypeTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreeScanner;
import com.sun.tools.javac.parser.Tokens.TokenKind;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.JCDiagnostic;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.ListBuffer;
import com.sun.tools.javac.util.Log;
import com.sun.tools.javac.util.Name;

import javax.tools.JavaFileObject;

/**
 *  A utility class to parse a string in a doc comment containing a
 *  reference to an API element, such as a type, field or method.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public class ReferenceParser {

    /**
     * Context dependent parsing mode which either disallows, allows or requires
     * a member reference. The <code>MEMBER_OPTIONAL</code> value also allows
     * arbitrary URI fragments using a double hash mark.
     */
    public enum Mode {
        MEMBER_DISALLOWED,
        MEMBER_OPTIONAL,
        MEMBER_REQUIRED
    }

    /**
     * An object to contain the result of parsing a reference to an API element.
     * Any, but not all, of the member fields may be null.
     */
    public static class Reference {
        public final JCTree.JCExpression moduleName;
        /** The type, if any, in the signature. */
        public final JCTree qualExpr;
        /** The member name, if any, in the signature. */
        public final Name member;
        /** The parameter types, if any, in the signature. */
        public final List<JCTree> paramTypes;

        Reference(JCTree.JCExpression moduleName, JCTree qualExpr, Name member, List<JCTree> paramTypes) {
            this.moduleName = moduleName;
            this.qualExpr = qualExpr;
            this.member = member;
            this.paramTypes = paramTypes;
        }
    }

    /**
     * An exception that indicates an error occurred while parsing a signature.
     */
    public static class ParseException extends Exception {
        private static final long serialVersionUID = 0;
        final int pos;

        ParseException(int pos, String message) {
            super(message);
            this.pos = pos;
        }
    }

    private final ParserFactory fac;

    /**
     * Create a parser object to parse reference signatures.
     * @param fac a factory for parsing Java source code.
     */
    public ReferenceParser(ParserFactory fac) {
        this.fac = fac;
    }

    /**
     * Parse a reference to an API element as may be found in doc comment.
     * @param sig the signature to be parsed
     * @param mode the parsing mode
     * @return a {@code Reference} object containing the result of parsing the signature
     * @throws ParseException if there is an error while parsing the signature
     */
    public Reference parse(String sig, Mode mode) throws ParseException {

        // Break sig apart into moduleName qualifiedExpr member paramTypes.
        JCTree.JCExpression moduleName;
        JCTree qualExpr;
        Name member;
        List<JCTree> paramTypes;

        Log.DeferredDiagnosticHandler dh = fac.log.new DeferredDiagnosticHandler();

        try {
            int slash = sig.indexOf("/");
            int afterSlash = slash + 1;
            int hash = sig.indexOf("#", afterSlash);
            int afterHash = hash + 1;
            int lparen = sig.indexOf("(", Math.max(slash, hash) + 1);
            int afterLparen = lparen + 1;

            moduleName = switch (slash) {
                case -1 -> null;
                case 0 -> throw new ParseException(0, "dc.ref.syntax.error");
                default -> parseModule(sig, 0, slash, dh);
            };

            if (slash > 0 && sig.length() == afterSlash) {
                qualExpr = null;
                member = null;
            } else if (hash == -1) {
                if (lparen == -1 && mode != Mode.MEMBER_REQUIRED) {
                    qualExpr = parseType(sig, afterSlash, sig.length(), dh);
                    member = null;
                } else {
                    if (mode == Mode.MEMBER_DISALLOWED) {
                        throw new ParseException(hash, "dc.ref.unexpected.input");
                    }
                    qualExpr = null;
                    member = parseMember(sig, afterSlash, lparen > -1 ? lparen : sig.length(), dh);
                }
            } else {
                if (mode == Mode.MEMBER_DISALLOWED) {
                    throw new ParseException(hash, "dc.ref.unexpected.input");
                }
                qualExpr = (hash == afterSlash) ? null : parseType(sig, afterSlash, hash, dh);
                if (sig.indexOf("#", afterHash) == afterHash) {
                    // A hash symbol followed by another hash indicates a literal URL fragment.
                    if (mode != Mode.MEMBER_OPTIONAL) {
                        throw new ParseException(afterHash, "dc.ref.unexpected.input");
                    }
                    member = null;
                } else if (lparen == -1) {
                    member = parseMember(sig, afterHash, sig.length(), dh);
                } else {
                    member = parseMember(sig, afterHash, lparen, dh);
                }
            }

            if (lparen == -1) {
                paramTypes = null;
            } else {
                int rparen = sig.indexOf(")", lparen);
                if (rparen != sig.length() - 1) {
                    throw new ParseException(rparen, "dc.ref.bad.parens");
                }
                paramTypes = parseParams(sig, afterLparen, rparen, dh);
            }

            assert dh.getDiagnostics().isEmpty();

        } finally {
            fac.log.popDiagnosticHandler(dh);
        }

        return new Reference(moduleName, qualExpr, member, paramTypes);
    }

    private JCTree.JCExpression parseModule(String sig, int beginIndex, int endIndex, Log.DeferredDiagnosticHandler dh) throws ParseException {
        String s = sig.substring(beginIndex, endIndex);
        JavaFileObject prev = fac.log.useSource(null);
        try {
            JavacParser p = fac.newParser(s, false, false, false);
            JCTree.JCExpression expr = p.qualident(false);
            if (p.token().kind != TokenKind.EOF) {
                throw new ParseException(beginIndex + p.token().pos, "dc.ref.unexpected.input");
            }
            checkDiags(dh, beginIndex);
            return expr;
        } finally {
            fac.log.useSource(prev);
        }
    }

    private JCTree parseType(String sig, int beginIndex, int endIndex, Log.DeferredDiagnosticHandler dh) throws ParseException {
        String s = sig.substring(beginIndex, endIndex);
        JavaFileObject prev = fac.log.useSource(null);
        try {
            JavacParser p = fac.newParser(s, false, false, false);
            JCTree tree = p.parseType();
            if (p.token().kind != TokenKind.EOF) {
                throw new ParseException(beginIndex + p.token().pos, "dc.ref.unexpected.input");
            }
            checkDiags(dh, beginIndex);
            return tree;
        } finally {
            fac.log.useSource(prev);
        }
    }

    private Name parseMember(String sig, int beginIndex, int endIndex, Log.DeferredDiagnosticHandler dh) throws ParseException {
        String s = sig.substring(beginIndex, endIndex);
        JavaFileObject prev = fac.log.useSource(null);
        try {
            JavacParser p = fac.newParser(s, false, false, false);
            Name name = p.ident();
            if (p.token().kind != TokenKind.EOF) {
                throw new ParseException(beginIndex + p.token().pos, "dc.ref.unexpected.input");
            }
            checkDiags(dh, beginIndex);
            return name;
        } finally {
            fac.log.useSource(prev);
        }
    }

    private List<JCTree> parseParams(String sig, int beginIndex, int endIndex, Log.DeferredDiagnosticHandler dh) throws ParseException {
        String s = sig.substring(beginIndex, endIndex);
        if (s.isBlank()) {
            return List.nil();
        }

        JavaFileObject prev = fac.log.useSource(null);
        try {
            JavacParser p = fac.newParser(s.replace("...", "[]"), false, false, false);
            ListBuffer<JCTree> paramTypes = new ListBuffer<>();
            paramTypes.add(p.parseType());

            if (p.token().kind == TokenKind.IDENTIFIER) {
                p.nextToken();
            }

            while (p.token().kind == TokenKind.COMMA) {
                p.nextToken();
                paramTypes.add(p.parseType());

                if (p.token().kind == TokenKind.IDENTIFIER) {
                    p.nextToken();
                }
            }

            if (p.token().kind != TokenKind.EOF) {
                throw new ParseException(p.token().pos, "dc.ref.unexpected.input");
            }

            Tree typeAnno = new TypeAnnotationFinder().scan(paramTypes, null);
            if (typeAnno != null) {
                int annoPos = ((JCTree) typeAnno).getStartPosition();
                throw new ParseException(beginIndex + annoPos, "dc.ref.annotations.not.allowed");
            }

            checkDiags(dh, beginIndex);

            return paramTypes.toList();
        } finally {
            fac.log.useSource(prev);
        }
    }

    private void checkDiags(Log.DeferredDiagnosticHandler h, int offset) throws ParseException {
        java.util.List<JCDiagnostic> diagnostics = h.getDiagnostics();
        if (!diagnostics.isEmpty()) {
            int pos = offset + (int)diagnostics.get(0).getPosition();
            throw new ParseException(pos, "dc.ref.syntax.error");
        }
    }

    static class TypeAnnotationFinder extends TreeScanner<Tree, Void> {
        @Override
        public Tree visitAnnotatedType(AnnotatedTypeTree t, Void ignore) {
            return t;
        }

        @Override
        public Tree reduce(Tree t1, Tree t2) {
            return t1 != null ? t1 : t2;
        }
    }
}
