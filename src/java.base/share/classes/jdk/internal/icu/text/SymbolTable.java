// Copyright 2016 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html
/*
 *******************************************************************************
 * Copyright (C) 1996-2005, International Business Machines Corporation and    *
 * others. All Rights Reserved.                                                *
 *******************************************************************************
 */
package jdk.internal.icu.text;
import java.text.ParsePosition;

/**
 * An interface that defines both lookup protocol and parsing of
 * symbolic names.
 * 
 * <p>This interface is used by UnicodeSet to resolve $Variable style
 * references that appear in set patterns.  RBBI and Transliteration
 * both independently implement this interface.
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
 * @stable ICU 2.8
 */
public interface SymbolTable {

    /**
     * The character preceding a symbol reference name.
     * @stable ICU 2.8
     */
    static final char SYMBOL_REF = '$';

    /**
     * Lookup the characters associated with this string and return it.
     * Return <tt>null</tt> if no such name exists.  The resultant
     * array may have length zero.
     * @param s the symbolic name to lookup
     * @return a char array containing the name's value, or null if
     * there is no mapping for s.
     * @stable ICU 2.8
     */
    char[] lookup(String s);

    /**
     * Lookup the UnicodeMatcher associated with the given character, and
     * return it.  Return <tt>null</tt> if not found.
     * @param ch a 32-bit code point from 0 to 0x10FFFF inclusive.
     * @return the UnicodeMatcher object represented by the given
     * character, or null if there is no mapping for ch.
     * @stable ICU 2.8
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
     * @stable ICU 2.8
     */
    String parseReference(String text, ParsePosition pos, int limit);
}
