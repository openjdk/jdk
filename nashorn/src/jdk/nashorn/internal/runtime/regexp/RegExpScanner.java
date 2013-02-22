/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
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

package jdk.nashorn.internal.runtime.regexp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.PatternSyntaxException;

import jdk.nashorn.internal.parser.Scanner;
import jdk.nashorn.internal.runtime.BitVector;

/**
 * Scan a JavaScript regexp, converting to Java regex if necessary.
 *
 */
final class RegExpScanner extends Scanner {

    /**
     * String builder to accumulate the result - this contains verbatim parsed JavaScript.
     * to get the java equivalent we need to create a Pattern token and return its toString()
     */
    private final StringBuilder sb;

    /** Is this the special case of a regexp that never matches anything */
    private boolean neverMatches;

    /** The resulting java.util.regex pattern string. */
    private String javaPattern;

    /** Expected token table */
    private final Map<Character, Integer> expected = new HashMap<>();

    /** Capturing parenthesis that have been found so far. */
    private final List<Capture> caps = new LinkedList<>();

    /** Forward references to capturing parenthesis to be resolved later.*/
    private final Map<Integer, Token> forwardReferences = new LinkedHashMap<>();

    /** Current level of zero-width negative lookahead assertions. */
    private int negativeLookaheadLevel;

    private static final String NON_IDENT_ESCAPES = "$^*+(){}[]|\\.?";

    private static class Capture {
        /**
         * Zero-width negative lookaheads enclosing the capture.
         */
        private final int negativeLookaheadLevel;
        /**
         * Captures that live inside a negative lookahead are dead after the
         * lookahead and will be undefined if referenced from outside.
         */
        private boolean isDead;

        Capture(final int negativeLookaheadLevel) {
            this.negativeLookaheadLevel = negativeLookaheadLevel;
        }

        public int getNegativeLookaheadLevel() {
            return negativeLookaheadLevel;
        }

        public boolean isDead() {
            return isDead;
        }

        public void setDead() {
            this.isDead = true;
        }
    }

    /**
     * This is a token - the JavaScript regexp is scanned into a token tree
     * A token has other tokens as children as well as "atoms", i.e. Strings.
     */
    private static class Token {

        private enum Type {
            PATTERN,
            DISJUNCTION,
            ALTERNATIVE,
            TERM,
            ASSERTION,
            QUANTIFIER,
            QUANTIFIER_PREFIX,
            ATOM,
            PATTERN_CHARACTER,
            ATOM_ESCAPE,
            CHARACTER_ESCAPE,
            CONTROL_ESCAPE,
            CONTROL_LETTER,
            IDENTITY_ESCAPE,
            DECIMAL_ESCAPE,
            CHARACTERCLASS_ESCAPE,
            CHARACTERCLASS,
            CLASSRANGES,
            NON_EMPTY_CLASSRANGES,
            NON_EMPTY_CLASSRANGES_NODASH,
            CLASSATOM,
            CLASSATOM_NODASH,
            CLASS_ESCAPE,
            DECIMALDIGITS,
            HEX_ESCAPESEQUENCE,
            UNICODE_ESCAPESEQUENCE,
        }

        /**
         * Token tyoe
         */
        private final Token.Type type;

        /**
         * Child nodes
         */
        private final List<Object> children;

        /**
         * Parent node
         */
        private Token parent;

        /**
         * Dead code flag
         */
        private boolean isDead;

        private static final Map<Type, ToString> toStringMap = new HashMap<>();
        private static final ToString DEFAULT_TOSTRING = new ToString();

        private static String unicode(final int value) {
            final StringBuilder sb = new StringBuilder();
            final String hex = Integer.toHexString(value);
            sb.append('u');
            for (int i = 0; i < 4 - hex.length(); i++) {
                sb.append('0');
            }
            sb.append(hex);

            return sb.toString();
        }

        static {
            toStringMap.put(Type.CHARACTERCLASS, new ToString() {
                @Override
                public String toString(final Token token) {
                    return super.toString(token).replace("\\b", "\b");
                }
            });

            // for some reason java regexps don't like control characters on the
            // form "\\ca".match([string with ascii 1 at char0]). Translating
            // them to unicode does it though.
            toStringMap.put(Type.CHARACTER_ESCAPE, new ToString() {
                @Override
                public String toString(final Token token) {
                    final String str = super.toString(token);
                    if (str.length() == 2) {
                        return Token.unicode(Character.toLowerCase(str.charAt(1)) - 'a' + 1);
                    }
                    return str;
                }
            });

            toStringMap.put(Type.DECIMAL_ESCAPE, new ToString() {
                @Override
                public String toString(final Token token) {
                    final String str = super.toString(token);

                    if ("\0".equals(str)) {
                        return str;
                    }

                    int value;

                    if (!token.hasParentOfType(Type.CLASSRANGES)) {
                        return str;
                    }

                    value = Integer.parseInt(str, 8); //throws exception that leads to SyntaxError if not octal
                    if (value > 0xff) {
                        throw new NumberFormatException(str);
                    }

                    return Token.unicode(value);
                }
            });

        }

        /**
         * JavaScript Token to Java regex substring framework.
         */
        private static class ToString {
            String toString(final Token token) {
                final Object[] children = token.getChildren();

                // Allow the installed regexp factory to perform global substitutions.
                switch (children.length) {
                    case 0:
                        return "";
                    case 1:
                        return RegExpFactory.replace(children[0].toString());
                    default:
                        final StringBuilder sb = new StringBuilder();
                        for (final Object child : children) {
                            sb.append(child);
                        }
                        return RegExpFactory.replace(sb.toString());
                }
            }
        }

        /**
         * Token iterator. Doesn't return "atom" children. i.e. string representations,
         * just tokens
         *
         */
        private static class TokenIterator implements Iterator<Token> {
            private final List<Token> preorder;

            private void init(final Token root) {
                preorder.add(root);
                for (final Object child : root.getChildren()) {
                    if (child instanceof Token) {
                        init((Token)child);
                    }
                }
            }

            TokenIterator(final Token root) {
                preorder = new ArrayList<>();
                init(root);
            }

            @Override
            public boolean hasNext() {
                return !preorder.isEmpty();
            }

            @Override
            public Token next() {
                return preorder.remove(0);
            }

            @Override
            public void remove() {
                next();
            }
        }

        /**
         * Constructor
         * @param type the token type
         */
        Token(final Token.Type type) {
            this.type = type;
            children = new ArrayList<>();
        }

        /**
         * Add a an "atom" child to a token
         * @param child the child to add
         * @return the token (for chaining)
         */
        public Token add(final String child) {
            children.add(child);
            return this;
        }

        /**
         * Add a child to a token
         * @param child the child
         * @return the token (for chaining)
         */
        public Token add(final Token child) {
            if (child != null) {
                children.add(child);
                child.setParent(this);
            }
            return this;
        }

        /**
         * Remove a child from a token
         * @param child the child to remove
         * @return true if successful
         */
        public boolean remove(final Token child) {
            return children.remove(child);
        }

        /**
         * Remove the last child from a token
         * @return the removed child
         */
        public Object removeLast() {
            return children.remove(children.size() - 1);
        }

        /**
         * Flag this token as dead code
         * @param isDead is it dead or not
         */
        private void setIsDead(final boolean isDead) {
            this.isDead = isDead;
        }

        /**
         * Is this token dead code
         * @return boolean
         */
        private boolean getIsDead() {
            return isDead;
        }

        /**
         * Get the parent of this token
         * @return parent token
         */
        public Token getParent() {
            return parent;
        }

        public boolean hasParentOfType(final Token.Type parentType) {
            for (Token p = getParent(); p != null; p = p.getParent()) {
                if (p.getType() == parentType) {
                    return true;
                }
            }
            return false;
        }

        public boolean hasChildOfType(final Token.Type childType) {
            for (final Iterator<Token> iter = iterator() ; iter.hasNext() ; ) {
                if (iter.next().getType() == childType) {
                    return true;
                }
            }
            return false;
        }

        /**
         * Set the parent of this token
         * @param parent
         */
        private void setParent(final Token parent) {
            this.parent = parent;
        }

        /**
         * Get the children of this token
         * @return an array of children, never null
         */
        public Object[] getChildren() {
            return children.toArray();
        }

        /**
         * Reset this token, remove all children
         */
        public void reset() {
            children.clear();
        }

        /**
         * Get a preorder token iterator with this token as root
         * @return an iterator
         */
        public Iterator<Token> iterator() {
            return new TokenIterator(this);
        }

        /**
         * Get the type of this token
         * @return type
         */
        public Type getType() {
            return type;
        }

        /**
         * Turn this token into Java regexp compatible text
         * @return part of a java regexp
         */
        @Override
        public String toString() {
            ToString t = toStringMap.get(getType());
            if (t == null) {
                t = DEFAULT_TOSTRING;
            }
            return t.toString(this);
        }
    }

    /**
     * Constructor
     * @param string the JavaScript regexp to parse
     */
    private RegExpScanner(final String string) {
        super(string);
        sb = new StringBuilder(limit);
        reset(0);
        expected.put(']', 0);
        expected.put('}', 0);
    }

    private void processForwardReferences() {
        if (neverMatches()) {
            return;
        }

        for (final Map.Entry<Integer, Token> fwdRef : forwardReferences.entrySet()) {
            if (fwdRef.getKey().intValue() > caps.size()) {
                neverMatches = true;
                break;
            }

            fwdRef.getValue().setIsDead(true);
        }

        forwardReferences.clear();
    }

    /**
     * Scan a JavaScript regexp string returning a Java safe regex string.
     *
     * @param string
     *            JavaScript regexp string.
     * @return Java safe regex string.
     */
    public static RegExpScanner scan(final String string) {
        final RegExpScanner scanner = new RegExpScanner(string);

        Token pattern;

        try {
            pattern = scanner.pattern();
        } catch (final Exception e) {
            throw new PatternSyntaxException(e.getMessage(), string, scanner.sb.length());
        }

        scanner.processForwardReferences();
        if (scanner.neverMatches()) {
            return null; // never matches
        }

        // go over the code and remove dead code
        final Iterator<Token> iter = pattern.iterator();
        while (iter.hasNext()) {
            final Token next = iter.next();
            if (next.getIsDead()) {
                next.getParent().remove(next);
            }
        }

        // turn the pattern into a string, p, the java equivalent string for our js regexp
        final String p = pattern.toString();
        // if builder contains all tokens that were sent in, we know
        // we correctly parsed the entire JavaScript regexp without syntax errors
        if (!string.equals(scanner.getStringBuilder().toString())) {
            throw new PatternSyntaxException(string, p, p.length() + 1);
        }

        scanner.javaPattern = p;
        return scanner;
     }

    /**
     * Does this regexp ever match anything? Use of e.g. [], which is legal in JavaScript,
     * is an example where we never match
     *
     * @return boolean
     */
    private boolean neverMatches() {
        return neverMatches;
    }

    final StringBuilder getStringBuilder() {
        return sb;
    }

    String getJavaPattern() {
        return javaPattern;
    }

    BitVector getGroupsInNegativeLookahead() {
        BitVector vec = null;
        for (int i = 0; i < caps.size(); i++) {
            final Capture cap = caps.get(i);
            if (cap.getNegativeLookaheadLevel() > 0) {
                if (vec == null) {
                    vec = new BitVector(caps.size() + 1);
                }
                vec.set(i + 1);
            }
        }
        return vec;
    }

    /**
     * Commit n characters to the builder and to a given token
     * @param token Uncommitted token.
     * @param n     Number of characters.
     * @return Committed token
     */
    private Token commit(final Token token, final int n) {
        final int startIn = position;

        switch (n) {
        case 1:
            sb.append(ch0);
            skip(1);
            break;
        case 2:
            sb.append(ch0);
            sb.append(ch1);
            skip(2);
            break;
        case 3:
            sb.append(ch0);
            sb.append(ch1);
            sb.append(ch2);
            skip(3);
            break;
        default:
            assert false : "Should not reach here";
        }

        if (token == null) {
            return null;
        }

        return token.add(sb.substring(startIn, sb.length()));
    }

    /**
     * Restart the buffers back at an earlier position.
     *
     * @param startIn
     *            Position in the input stream.
     * @param startOut
     *            Position in the output stream.
     */
    private void restart(final int startIn, final int startOut) {
        reset(startIn);
        sb.setLength(startOut);
    }

    private void push(final char ch) {
        expected.put(ch, expected.get(ch) + 1);
    }

    private void pop(final char ch) {
        expected.put(ch, Math.min(0, expected.get(ch) - 1));
    }

    /*
     * Recursive descent tokenizer starts below.
     */

    /*
     * Pattern ::
     *      Disjunction
     */
    private Token pattern() {
        final Token token = new Token(Token.Type.PATTERN);

        final Token child = disjunction();
        return token.add(child);
    }

    /*
     * Disjunction ::
     *      Alternative
     *      Alternative | Disjunction
     */
    private Token disjunction() {
        final Token token = new Token(Token.Type.DISJUNCTION);

        while (true) {
            token.add(alternative());

            if (ch0 == '|') {
                commit(token, 1);
            } else {
                break;
            }
        }

        return token;
    }

    /*
     * Alternative ::
     *      [empty]
     *      Alternative Term
     */
    private Token alternative() {
        final Token token = new Token(Token.Type.ALTERNATIVE);

        Token child;
        while ((child = term()) != null) {
            token.add(child);
        }

        return token;
    }

    /*
     * Term ::
     *      Assertion
     *      Atom
     *      Atom Quantifier
     */
    private Token term() {
        final int startIn  = position;
        final int startOut = sb.length();
        final Token token  = new Token(Token.Type.TERM);
        Token child;

        child = assertion();
        if (child != null) {
            return token.add(child);
        }

        child = atom();
        if (child != null) {
            boolean emptyCharacterClass = false;
            if ("[]".equals(child.toString())) {
                emptyCharacterClass = true;
            }

            token.add(child);

            final Token quantifier = quantifier();
            if (quantifier != null) {
                token.add(quantifier);
            }

            if (emptyCharacterClass) {
                if (quantifier == null) {
                    neverMatches = true; //never matches ever.
                } else {
                    //if we can get away with max zero, remove this entire token
                    final String qs = quantifier.toString();
                    if ("+".equals(qs) || "*".equals(qs) || qs.startsWith("{0,")) {
                        token.setIsDead(true);
                    }
                }
            }

            return token;
        }

        restart(startIn, startOut);
        return null;
    }

    /*
     * Assertion ::
     *      ^
     *      $
     *      \b
     *      \B
     *      ( ? = Disjunction )
     *      ( ? ! Disjunction )
     */
    private Token assertion() {
        final int startIn  = position;
        final int startOut = sb.length();
        final Token token  = new Token(Token.Type.ASSERTION);

        switch (ch0) {
        case '^':
        case '$':
            return commit(token, 1);

        case '\\':
            if (ch1 == 'b' || ch1 == 'B') {
                return commit(token, 2);
            }
            break;

        case '(':
            if (ch1 != '?') {
                break;
            }
            if (ch2 != '=' && ch2 != '!') {
                break;
            }
            final boolean isNegativeLookahead = (ch2 == '!');
            commit(token, 3);

            if (isNegativeLookahead) {
                negativeLookaheadLevel++;
            }
            final Token disjunction = disjunction();
            if (isNegativeLookahead) {
                for (final Capture cap : caps) {
                    if (cap.getNegativeLookaheadLevel() >= negativeLookaheadLevel) {
                        cap.setDead();
                    }
                }
                negativeLookaheadLevel--;
            }

            if (disjunction != null && ch0 == ')') {
                token.add(disjunction);
                return commit(token, 1);
            }
            break;

        default:
            break;
        }

        restart(startIn, startOut);

        return null;
    }

    /*
     * Quantifier ::
     *      QuantifierPrefix
     *      QuantifierPrefix ?
     */
    private Token quantifier() {
        final Token token = new Token(Token.Type.QUANTIFIER);
        final Token child = quantifierPrefix();
        if (child != null) {
            token.add(child);
            if (ch0 == '?') {
                commit(token, 1);
            }
            return token;
        }
        return null;
    }

    /*
     * QuantifierPrefix ::
     *      *
     *      +
     *      ?
     *      { DecimalDigits }
     *      { DecimalDigits , }
     *      { DecimalDigits , DecimalDigits }
     */
    private Token quantifierPrefix() {
        final int startIn  = position;
        final int startOut = sb.length();
        final Token token  = new Token(Token.Type.QUANTIFIER_PREFIX);

        switch (ch0) {
        case '*':
        case '+':
        case '?':
            return commit(token, 1);

        case '{':
            commit(token, 1);

            final Token child = decimalDigits();
            if (child == null) {
                break; // not a quantifier - back out
            }
            push('}');
            token.add(child);

            if (ch0 == ',') {
                commit(token, 1);
                token.add(decimalDigits());
            }

            if (ch0 == '}') {
                pop('}');
                commit(token, 1);
            }

            return token;

        default:
            break;
        }

        restart(startIn, startOut);
        return null;
    }

    /*
     * Atom ::
     *      PatternCharacter
     *      .
     *      \ AtomEscape
     *      CharacterClass
     *      ( Disjunction )
     *      ( ? : Disjunction )
     *
     */
    private Token atom() {
        final int startIn  = position;
        final int startOut = sb.length();
        final Token token  = new Token(Token.Type.ATOM);
        Token child;

        child = patternCharacter();
        if (child != null) {
            return token.add(child);
        }

        if (ch0 == '.') {
            return commit(token, 1);
        }

        if (ch0 == '\\') {
            commit(token, 1);
            child = atomEscape();

            if (child != null) {
                if (child.hasChildOfType(Token.Type.IDENTITY_ESCAPE)) {
                    final char idEscape = child.toString().charAt(0);
                    if (NON_IDENT_ESCAPES.indexOf(idEscape) == -1) {
                        token.reset();
                    }
                }

                token.add(child);

                // forward backreferences always match empty. JavaScript != Java
                if (child.hasChildOfType(Token.Type.DECIMAL_ESCAPE) && !"\u0000".equals(child.toString())) {
                    final int refNum = Integer.parseInt(child.toString());

                    if (refNum - 1 < caps.size() && caps.get(refNum - 1).isDead()) {
                        // reference to dead in-negative-lookahead capture
                        token.setIsDead(true);
                    } else if (caps.size() < refNum) {
                        // forward reference: always matches against empty string (dead token).
                        // invalid reference (non-existant capture): pattern never matches.
                        forwardReferences.put(refNum, token);
                    }
                }

                return token;
            }
        }

        child = characterClass();
        if (child != null) {
            return token.add(child);
        }

        if (ch0 == '(') {
            boolean capturingParens = true;
            commit(token, 1);
            if (ch0 == '?' && ch1 == ':') {
                capturingParens = false;
                commit(token, 2);
            }

            child = disjunction();
            if (child != null) {
                token.add(child);
                if (ch0 == ')') {
                    final Token atom = commit(token, 1);
                    if (capturingParens) {
                        caps.add(new Capture(negativeLookaheadLevel));
                    }
                    return atom;
                }
            }
        }

        restart(startIn, startOut);
        return null;
    }

    /*
     * PatternCharacter ::
     *      SourceCharacter but not any of: ^$\.*+?()[]{}|
     */
    @SuppressWarnings("fallthrough")
    private Token patternCharacter() {
        if (atEOF()) {
            return null;
        }

        switch (ch0) {
        case '^':
        case '$':
        case '\\':
        case '.':
        case '*':
        case '+':
        case '?':
        case '(':
        case ')':
        case '[':
        case '|':
            return null;

        case '}':
        case ']':
            final int n = expected.get(ch0);
            if (n != 0) {
                return null;
            }

       case '{':
           // if not a valid quantifier escape curly brace to match itself
           // this ensures compatibility with other JS implementations
           final Token quant = quantifierPrefix();
           return (quant == null) ? commit(new Token(Token.Type.PATTERN_CHARACTER).add("\\"), 1) : null;

        default:
            return commit(new Token(Token.Type.PATTERN_CHARACTER), 1); // SOURCECHARACTER
        }
    }

    /*
     * AtomEscape ::
     *      DecimalEscape
     *      CharacterEscape
     *      CharacterClassEscape
     */
    private Token atomEscape() {
        final Token token = new Token(Token.Type.ATOM_ESCAPE);
        Token child;

        child = decimalEscape();
        if (child != null) {
            return token.add(child);
        }

        child = characterClassEscape();
        if (child != null) {
            return token.add(child);
        }

        child = characterEscape();
        if (child != null) {
            return token.add(child);
        }


        return null;
    }

    /*
     * CharacterEscape ::
     *      ControlEscape
     *      c ControlLetter
     *      HexEscapeSequence
     *      UnicodeEscapeSequence
     *      IdentityEscape
     */
    private Token characterEscape() {
        final int startIn  = position;
        final int startOut = sb.length();

        final Token token = new Token(Token.Type.CHARACTER_ESCAPE);
        Token child;

        child = controlEscape();
        if (child != null) {
            return token.add(child);
        }

        if (ch0 == 'c') {
            commit(token, 1);
            child = controlLetter();
            if (child != null) {
                return token.add(child);
            }
            restart(startIn, startOut);
        }

        child = hexEscapeSequence();
        if (child != null) {
            return token.add(child);
        }

        child = unicodeEscapeSequence();
        if (child != null) {
            return token.add(child);
        }

        child = identityEscape();
        if (child != null) {
            return token.add(child);
        }

        restart(startIn, startOut);

        return null;
    }

    private boolean scanEscapeSequence(final char leader, final int length, final Token token) {
        final int startIn  = position;
        final int startOut = sb.length();

        if (ch0 != leader) {
            return false;
        }

        commit(token, 1);
        for (int i = 0; i < length; i++) {
            final char ch0l = Character.toLowerCase(ch0);
            if ((ch0l >= 'a' && ch0l <= 'f') || isDecimalDigit(ch0)) {
                commit(token, 1);
            } else {
                restart(startIn, startOut);
                return false;
            }
        }

        return true;
    }

    private Token hexEscapeSequence() {
        final Token token = new Token(Token.Type.HEX_ESCAPESEQUENCE);
        if (scanEscapeSequence('x', 2, token)) {
            return token;
        }
        return null;
    }

    private Token unicodeEscapeSequence() {
        final Token token = new Token(Token.Type.UNICODE_ESCAPESEQUENCE);
        if (scanEscapeSequence('u', 4, token)) {
            return token;
        }
        return null;
    }

    /*
     * ControlEscape ::
     *      one of fnrtv
     */
    private Token controlEscape() {
        switch (ch0) {
        case 'f':
        case 'n':
        case 'r':
        case 't':
        case 'v':
            return commit(new Token(Token.Type.CONTROL_ESCAPE), 1);

        default:
            return null;
        }
    }

    /*
     * ControlLetter ::
     *      one of abcdefghijklmnopqrstuvwxyz
     *      ABCDEFGHIJKLMNOPQRSTUVWXYZ
     */
    private Token controlLetter() {
        final char c = Character.toUpperCase(ch0);
        if (c >= 'A' && c <= 'Z') {
            final Token token = new Token(Token.Type.CONTROL_LETTER);
            commit(token, 1);
            return token;
        }
        return null;
        /*
        Token token = new Token(Token.Type.CONTROL_LETTER);
        commit(null, 1);//add original char to builder not to token
        this.neverMatches = c < 'A' || c > 'Z';
        return token.add(""+c);*/
    }

    /*
     * IdentityEscape ::
     *      SourceCharacter but not IdentifierPart
     *      <ZWJ>  (200c)
     *      <ZWNJ> (200d)
     */
    private Token identityEscape() {
        final Token token = new Token(Token.Type.IDENTITY_ESCAPE);
        commit(token, 1);
        return token;
    }

    /*
     * DecimalEscape ::
     *      DecimalIntegerLiteral [lookahead DecimalDigit]
     */
    private Token decimalEscape() {
        final Token token = new Token(Token.Type.DECIMAL_ESCAPE);
        final int startIn  = position;
        final int startOut = sb.length();

        if (ch0 == '0' && !isDecimalDigit(ch1)) {
            commit(token, 1);
            token.removeLast();
            //  DecimalEscape :: 0. If i is zero, return the EscapeValue consisting of a <NUL> character (Unicodevalue0000);
            return token.add("\u0000");
        }

        if (isDecimalDigit(ch0)) {
            while (isDecimalDigit(ch0)) {
                commit(token, 1);
            }
            return token;
        }

        restart(startIn, startOut);

        return null;
    }

    /*
     * CharacterClassEscape ::
     *  one of dDsSwW
     */
    private Token characterClassEscape() {
        switch (ch0) {
        case 's':
        case 'S':
        case 'd':
        case 'D':
        case 'w':
        case 'W':
            return commit(new Token(Token.Type.CHARACTERCLASS_ESCAPE), 1);

        default:
            return null;
        }
    }

    /*
     * CharacterClass ::
     *      [ [lookahead {^}] ClassRanges ]
     *      [ ^ ClassRanges ]
     */
    private Token characterClass() {
        final int startIn  = position;
        final int startOut = sb.length();
        final Token token  = new Token(Token.Type.CHARACTERCLASS);

        if (ch0 == '[') {
            push(']');
            commit(token, 1);

            if (ch0 == '^') {
                commit(token, 1);
            }

            final Token child = classRanges();
            if (child != null && ch0 == ']') {
                pop(']');
                token.add(child);
                return commit(token, 1);
            }
        }

        restart(startIn, startOut);
        return null;
    }

    /*
     * ClassRanges ::
     *      [empty]
     *      NonemptyClassRanges
     */
    private Token classRanges() {
        return new Token(Token.Type.CLASSRANGES).add(nonemptyClassRanges());
    }

    /*
     * NonemptyClassRanges ::
     *      ClassAtom
     *      ClassAtom NonemptyClassRangesNoDash
     *      ClassAtom - ClassAtom ClassRanges
     */
    private Token nonemptyClassRanges() {
        final int startIn  = position;
        final int startOut = sb.length();
        final Token token  = new Token(Token.Type.NON_EMPTY_CLASSRANGES);
        Token child;

        child = classAtom();
        if (child != null) {
            token.add(child);

            if (ch0 == '-') {
                commit(token, 1);

                final Token child1 = classAtom();
                final Token child2 = classRanges();
                if (child1 != null && child2 != null) {
                    token.add(child1);
                    token.add(child2);

                    return token;
                }
            }

            child = nonemptyClassRangesNoDash();
            if (child != null) {
                token.add(child);
                return token;
            }

            return token;
        }

        restart(startIn, startOut);
        return null;
    }

    /*
     * NonemptyClassRangesNoDash ::
     *      ClassAtom
     *      ClassAtomNoDash NonemptyClassRangesNoDash
     *      ClassAtomNoDash - ClassAtom ClassRanges
     */
    private Token nonemptyClassRangesNoDash() {
        final int startIn  = position;
        final int startOut = sb.length();
        final Token token  = new Token(Token.Type.NON_EMPTY_CLASSRANGES_NODASH);
        Token child;

        child = classAtomNoDash();
        if (child != null) {
            token.add(child);

            // need to check dash first, as for e.g. [a-b|c-d] will otherwise parse - as an atom
            if (ch0 == '-') {
               commit(token, 1);

               final Token child1 = classAtom();
               final Token child2 = classRanges();
               if (child1 != null && child2 != null) {
                   token.add(child1);
                   return token.add(child2);
               }
               //fallthru
           }

            child = nonemptyClassRangesNoDash();
            if (child != null) {
                token.add(child);
            }
            return token; // still a class atom
        }

        child = classAtom();
        if (child != null) {
            return token.add(child);
        }

        restart(startIn, startOut);
        return null;
    }

    /*
     * ClassAtom : - ClassAtomNoDash
     */
    private Token classAtom() {
        final Token token = new Token(Token.Type.CLASSATOM);

        if (ch0 == '-') {
            return commit(token, 1);
        }

        final Token child = classAtomNoDash();
        if (child != null) {
            return token.add(child);
        }

        return null;
    }

    /*
     * ClassAtomNoDash ::
     *      SourceCharacter but not one of \ or ] or -
     *      \ ClassEscape
     */
    private Token classAtomNoDash() {
        final int startIn  = position;
        final int startOut = sb.length();
        final Token token  = new Token(Token.Type.CLASSATOM_NODASH);

        switch (ch0) {
        case ']':
        case '-':
        case '\0':
            return null;

        case '[':
            // unescaped left square bracket - add escape
            return commit(token.add("\\"), 1);

        case '\\':
            commit(token, 1);
            final Token child = classEscape();
            if (child != null) {
                return token.add(child);
            }

            restart(startIn, startOut);
            return null;

        default:
            return commit(token, 1);
        }
    }

    /*
     * ClassEscape ::
     *      DecimalEscape
     *      b
     *      CharacterEscape
     *      CharacterClassEscape
     */
    private Token classEscape() {
        final Token token = new Token(Token.Type.CLASS_ESCAPE);
        Token child;

        child = decimalEscape();
        if (child != null) {
            return token.add(child);
        }

        if (ch0 == 'b') {
            return commit(token, 1);
        }

        child = characterEscape();
        if (child != null) {
            return token.add(child);
        }

        child = characterClassEscape();
        if (child != null) {
            return token.add(child);
        }

        return null;
    }

    /*
     * DecimalDigits
     */
    private Token decimalDigits() {
        if (!isDecimalDigit(ch0)) {
            return null;
        }

        final Token token = new Token(Token.Type.DECIMALDIGITS);
        while (isDecimalDigit(ch0)) {
            commit(token, 1);
        }

        return token;
    }

    private static boolean isDecimalDigit(final char ch) {
        return ch >= '0' && ch <= '9';
    }
}
