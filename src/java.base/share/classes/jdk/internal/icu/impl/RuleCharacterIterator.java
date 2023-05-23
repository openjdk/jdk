// Copyright 2016 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html
/*
**********************************************************************
* Copyright (c) 2003-2011, International Business Machines
* Corporation and others.  All Rights Reserved.
**********************************************************************
* Author: Alan Liu
* Created: September 23 2003
* Since: ICU 2.8
**********************************************************************
*/
package jdk.internal.icu.impl;

import java.text.ParsePosition;

import jdk.internal.icu.text.SymbolTable;
import jdk.internal.icu.text.UTF16;

/**
 * An iterator that returns 32-bit code points.  This class is deliberately
 * <em>not</em> related to any of the JDK or ICU4J character iterator classes
 * in order to minimize complexity.
 * @author Alan Liu
 * @since ICU 2.8
 */
public class RuleCharacterIterator {

    // TODO: Ideas for later.  (Do not implement if not needed, lest the
    // code coverage numbers go down due to unused methods.)
    // 1. Add a copy constructor, equals() method, clone() method.
    // 2. Rather than return DONE, throw an exception if the end
    // is reached -- this is an alternate usage model, probably not useful.
    // 3. Return isEscaped from next().  If this happens,
    // don't keep an isEscaped member variable.

    /**
     * Text being iterated.
     */
    private String text;

    /**
     * Position of iterator.
     */
    private ParsePosition pos;

    /**
     * Symbol table used to parse and dereference variables.  May be null.
     */
    private SymbolTable sym;

    /**
     * Current variable expansion, or null if none.
     */
    private String buf;

    /**
     * Position within buf[].  Meaningless if buf == null.
     */
    private int bufPos;

    /**
     * Flag indicating whether the last character was parsed from an escape.
     */
    private boolean isEscaped;

    /**
     * Value returned when there are no more characters to iterate.
     */
    public static final int DONE = -1;

    /**
     * Bitmask option to enable parsing of variable names.  If (options &
     * PARSE_VARIABLES) != 0, then an embedded variable will be expanded to
     * its value.  Variables are parsed using the SymbolTable API.
     */
    public static final int PARSE_VARIABLES = 1;

    /**
     * Bitmask option to enable parsing of escape sequences.  If (options &
     * PARSE_ESCAPES) != 0, then an embedded escape sequence will be expanded
     * to its value.  Escapes are parsed using Utility.unescapeAndLengthAt().
     */
    public static final int PARSE_ESCAPES   = 2;

    /**
     * Bitmask option to enable skipping of whitespace.  If (options &
     * SKIP_WHITESPACE) != 0, then Unicode Pattern_White_Space characters will be silently
     * skipped, as if they were not present in the input.
     */
    public static final int SKIP_WHITESPACE = 4;

    /** For use with {@link #getPos(Position)} & {@link #setPos(Position)}. */
    public static final class Position {
        private String buf;
        private int bufPos;
        private int posIndex;
    };

    /**
     * Constructs an iterator over the given text, starting at the given
     * position.
     * @param text the text to be iterated
     * @param sym the symbol table, or null if there is none.  If sym is null,
     * then variables will not be dereferenced, even if the PARSE_VARIABLES
     * option is set.
     * @param pos upon input, the index of the next character to return.  If a
     * variable has been dereferenced, then pos will <em>not</em> increment as
     * characters of the variable value are iterated.
     */
    public RuleCharacterIterator(String text, SymbolTable sym,
                                 ParsePosition pos) {
        if (text == null || pos.getIndex() > text.length()) {
            throw new IllegalArgumentException();
        }
        this.text = text;
        this.sym = sym;
        this.pos = pos;
        buf = null;
    }

    /**
     * Returns true if this iterator has no more characters to return.
     */
    public boolean atEnd() {
        return buf == null && pos.getIndex() == text.length();
    }

    /**
     * Returns the next character using the given options, or DONE if there
     * are no more characters, and advance the position to the next
     * character.
     * @param options one or more of the following options, bitwise-OR-ed
     * together: PARSE_VARIABLES, PARSE_ESCAPES, SKIP_WHITESPACE.
     * @return the current 32-bit code point, or DONE
     */
    public int next(int options) {
        int c = DONE;
        isEscaped = false;

        for (;;) {
            c = _current();
            _advance(UTF16.getCharCount(c));

            if (c == SymbolTable.SYMBOL_REF && buf == null &&
                (options & PARSE_VARIABLES) != 0 && sym != null) {
                String name = sym.parseReference(text, pos, text.length());
                // If name == null there was an isolated SYMBOL_REF;
                // return it.  Caller must be prepared for this.
                if (name == null) {
                    break;
                }
                bufPos = 0;
                char[] chars = sym.lookup(name);
                if (chars == null) {
                    buf = null;
                    throw new IllegalArgumentException(
                                "Undefined variable: " + name);
                }
                // Handle empty variable value
                if (chars.length == 0) {
                    buf = null;
                }
                buf = new String(chars);
                continue;
            }

            if ((options & SKIP_WHITESPACE) != 0 &&
                PatternProps.isWhiteSpace(c)) {
                continue;
            }

            if (c == '\\' && (options & PARSE_ESCAPES) != 0) {
                int cpAndLength = Utility.unescapeAndLengthAt(
                        getCurrentBuffer(), getCurrentBufferPos());
                if (cpAndLength < 0) {
                    throw new IllegalArgumentException("Invalid escape");
                }
                c = Utility.cpFromCodePointAndLength(cpAndLength);
                jumpahead(Utility.lengthFromCodePointAndLength(cpAndLength));
                isEscaped = true;
            }

            break;
        }

        return c;
    }

    /**
     * Returns true if the last character returned by next() was
     * escaped.  This will only be the case if the option passed in to
     * next() included PARSE_ESCAPED and the next character was an
     * escape sequence.
     */
    public boolean isEscaped() {
        return isEscaped;
    }

    /**
     * Returns true if this iterator is currently within a variable expansion.
     */
    public boolean inVariable() {
        return buf != null;
    }

    /**
     * Returns an object which, when later passed to setPos(), will
     * restore this iterator's position.  Usage idiom:
     *
     * RuleCharacterIterator iterator = ...;
     * Position pos = iterator.getPos(null); // allocate position object
     * for (;;) {
     *   pos = iterator.getPos(pos); // reuse position object
     *   int c = iterator.next(...);
     *   ...
     * }
     * iterator.setPos(pos);
     *
     * @param p a position object previously returned by {@code getPos()},
     * or null.  If not null, it will be updated and returned.  If
     * null, a new position object will be allocated and returned.
     * @return a position object which may be passed to setPos(), either
     * {@code p}, or if {@code p} == null, a newly-allocated object
     */
    public Position getPos(Position p) {
        if (p == null) {
            p = new Position();
        }
        p.buf = buf;
        p.bufPos = bufPos;
        p.posIndex = pos.getIndex();
        return p;
    }

    /**
     * Restores this iterator to the position it had when getPos()
     * returned the given object.
     * @param p a position object previously returned by getPos()
     */
    public void setPos(Position p) {
        buf = p.buf;
        pos.setIndex(p.posIndex);
        bufPos = p.bufPos;
    }

    /**
     * Skips ahead past any ignored characters, as indicated by the given
     * options.  This is useful in conjunction with the lookahead() method.
     *
     * Currently, this only has an effect for SKIP_WHITESPACE.
     * @param options one or more of the following options, bitwise-OR-ed
     * together: PARSE_VARIABLES, PARSE_ESCAPES, SKIP_WHITESPACE.
     */
    public void skipIgnored(int options) {
        if ((options & SKIP_WHITESPACE) != 0) {
            for (;;) {
                int a = _current();
                if (!PatternProps.isWhiteSpace(a)) break;
                _advance(UTF16.getCharCount(a));
            }
        }
    }

    /**
     * Returns a string containing the remainder of the characters to be
     * returned by this iterator, without any option processing.  If the
     * iterator is currently within a variable expansion, this will only
     * extend to the end of the variable expansion.
     * This method, together with getCurrentBufferPos() (which replace the former lookahead()),
     * is provided so that iterators may interoperate with string-based APIs. The typical
     * sequence of calls is to call skipIgnored(), then call these methods, then
     * parse that substring, then call jumpahead() to
     * resynchronize the iterator.
     * @return a string containing the characters to be returned by future
     * calls to next()
     */
    public String getCurrentBuffer() {
        if (buf != null) {
            return buf;
        } else {
            return text;
        }
    }

    public int getCurrentBufferPos() {
        if (buf != null) {
            return bufPos;
        } else {
            return pos.getIndex();
        }
    }

    /**
     * Advances the position by the given number of 16-bit code units.
     * This is useful in conjunction with getCurrentBuffer()+getCurrentBufferPos()
     * (formerly lookahead()).
     * @param count the number of 16-bit code units to jump over
     */
    public void jumpahead(int count) {
        if (count < 0) {
            throw new IllegalArgumentException();
        }
        if (buf != null) {
            bufPos += count;
            if (bufPos > buf.length()) {
                throw new IllegalArgumentException();
            }
            if (bufPos == buf.length()) {
                buf = null;
            }
        } else {
            int i = pos.getIndex() + count;
            pos.setIndex(i);
            if (i > text.length()) {
                throw new IllegalArgumentException();
            }
        }
    }

    /**
     * Returns a string representation of this object, consisting of the
     * characters being iterated, with a '|' marking the current position.
     * Position within an expanded variable is <em>not</em> indicated.
     * @return a string representation of this object
     */
    @Override
    public String toString() {
        int b = pos.getIndex();
        return text.substring(0, b) + '|' + text.substring(b);
    }

    /**
     * Returns the current 32-bit code point without parsing escapes, parsing
     * variables, or skipping whitespace.
     * @return the current 32-bit code point
     */
    private int _current() {
        if (buf != null) {
            return UTF16.charAt(buf, bufPos);
        } else {
            int i = pos.getIndex();
            return (i < text.length()) ? UTF16.charAt(text, i) : DONE;
        }
    }

    /**
     * Advances the position by the given amount.
     * @param count the number of 16-bit code units to advance past
     */
    private void _advance(int count) {
        if (buf != null) {
            bufPos += count;
            if (bufPos == buf.length()) {
                buf = null;
            }
        } else {
            pos.setIndex(pos.getIndex() + count);
            if (pos.getIndex() > text.length()) {
                pos.setIndex(text.length());
            }
        }
    }
}
