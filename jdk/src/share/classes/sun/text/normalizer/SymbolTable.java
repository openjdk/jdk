/*
 * Copyright (c) 2005, Oracle and/or its affiliates. All rights reserved.
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

/*
 *******************************************************************************
 * (C) Copyright IBM Corp. 1996-2005 - All Rights Reserved                     *
 *                                                                             *
 * The original version of this source code and documentation is copyrighted   *
 * and owned by IBM, These materials are provided under terms of a License     *
 * Agreement between IBM and Sun. This technology is protected by multiple     *
 * US and International patents. This notice and attribution to IBM may not    *
 * to removed.                                                                 *
 *******************************************************************************
 */

package sun.text.normalizer;

import java.text.ParsePosition;

/**
 * An interface that defines both lookup protocol and parsing of
 * symbolic names.
 *
 * <p>A symbol table maintains two kinds of mappings.  The first is
 * between symbolic names and their values.  For example, if the
 * variable with the name "start" is set to the value "alpha"
 * (perhaps, though not necessarily, through an expression such as
 * "$start=alpha"), then the call lookup("start") will return the
 * char[] array ['a', 'l', 'p', 'h', 'a'].
 *
 * <p>The second kind of mapping is between character values and
 * UnicodeMatcher objects.  This is used by RuleBasedTransliterator,
 * which uses characters in the private use area to represent objects
 * such as UnicodeSets.  If U+E015 is mapped to the UnicodeSet [a-z],
 * then lookupMatcher(0xE015) will return the UnicodeSet [a-z].
 *
 * <p>Finally, a symbol table defines parsing behavior for symbolic
 * names.  All symbolic names start with the SYMBOL_REF character.
 * When a parser encounters this character, it calls parseReference()
 * with the position immediately following the SYMBOL_REF.  The symbol
 * table parses the name, if there is one, and returns it.
 *
 * @draft ICU 2.8
 * @deprecated This is a draft API and might change in a future release of ICU.
 */
public interface SymbolTable {

    /**
     * The character preceding a symbol reference name.
     * @draft ICU 2.8
     * @deprecated This is a draft API and might change in a future release of ICU.
     */
    static final char SYMBOL_REF = '$';

    /**
     * Lookup the characters associated with this string and return it.
     * Return <tt>null</tt> if no such name exists.  The resultant
     * array may have length zero.
     * @param s the symbolic name to lookup
     * @return a char array containing the name's value, or null if
     * there is no mapping for s.
     * @draft ICU 2.8
     * @deprecated This is a draft API and might change in a future release of ICU.
     */
    char[] lookup(String s);

    /**
     * Lookup the UnicodeMatcher associated with the given character, and
     * return it.  Return <tt>null</tt> if not found.
     * @param ch a 32-bit code point from 0 to 0x10FFFF inclusive.
     * @return the UnicodeMatcher object represented by the given
     * character, or null if there is no mapping for ch.
     * @draft ICU 2.8
     * @deprecated This is a draft API and might change in a future release of ICU.
     */
    UnicodeMatcher lookupMatcher(int ch);

    /**
     * Parse a symbol reference name from the given string, starting
     * at the given position.  If no valid symbol reference name is
     * found, return null and leave pos unchanged.  That is, if the
     * character at pos cannot start a name, or if pos is at or after
     * text.length(), then return null.  This indicates an isolated
     * SYMBOL_REF character.
     * @param text the text to parse for the name
     * @param pos on entry, the index of the first character to parse.
     * This is the character following the SYMBOL_REF character.  On
     * exit, the index after the last parsed character.  If the parse
     * failed, pos is unchanged on exit.
     * @param limit the index after the last character to be parsed.
     * @return the parsed name, or null if there is no valid symbolic
     * name at the given position.
     * @draft ICU 2.8
     * @deprecated This is a draft API and might change in a future release of ICU.
     */
    String parseReference(String text, ParsePosition pos, int limit);
}
