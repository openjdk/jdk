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

package jdk.nashorn.internal.objects;

import static jdk.nashorn.internal.runtime.ECMAErrors.typeError;
import static jdk.nashorn.internal.runtime.ScriptRuntime.UNDEFINED;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import jdk.nashorn.internal.objects.annotations.Attribute;
import jdk.nashorn.internal.objects.annotations.Constructor;
import jdk.nashorn.internal.objects.annotations.Function;
import jdk.nashorn.internal.objects.annotations.Getter;
import jdk.nashorn.internal.objects.annotations.Property;
import jdk.nashorn.internal.objects.annotations.ScriptClass;
import jdk.nashorn.internal.objects.annotations.SpecializedConstructor;
import jdk.nashorn.internal.objects.annotations.Where;
import jdk.nashorn.internal.runtime.BitVector;
import jdk.nashorn.internal.runtime.JSType;
import jdk.nashorn.internal.runtime.ParserException;
import jdk.nashorn.internal.runtime.regexp.RegExp;
import jdk.nashorn.internal.runtime.regexp.RegExpFactory;
import jdk.nashorn.internal.runtime.regexp.RegExpResult;
import jdk.nashorn.internal.runtime.regexp.RegExpMatcher;
import jdk.nashorn.internal.runtime.ScriptFunction;
import jdk.nashorn.internal.runtime.ScriptObject;
import jdk.nashorn.internal.runtime.ScriptRuntime;

/**
 * ECMA 15.10 RegExp Objects.
 */
@ScriptClass("RegExp")
public final class NativeRegExp extends ScriptObject {
    /** ECMA 15.10.7.5 lastIndex property */
    @Property(attributes = Attribute.NOT_ENUMERABLE | Attribute.NOT_CONFIGURABLE)
    public Object lastIndex;

    /** Compiled regexp */
    private RegExp regexp;

    // Reference to global object needed to support static RegExp properties
    private Global globalObject;

    NativeRegExp(final String input, final String flagString) {
        try {
            this.regexp = RegExpFactory.create(input, flagString);
        } catch (final ParserException e) {
            // translate it as SyntaxError object and throw it
            e.throwAsEcmaException();
            throw new AssertionError(); //guard against null warnings below
        }

        this.setLastIndex(0);
        init();
    }

    NativeRegExp(final String string) {
        this(string, "");
    }

    NativeRegExp(final NativeRegExp regExp) {
        this.lastIndex  = regExp.getLastIndexObject();
        this.regexp      = regExp.getRegExp();
        init();
    }

    @Override
    public String getClassName() {
        return "RegExp";
    }

    /**
     * ECMA 15.10.4
     *
     * Constructor
     *
     * @param isNew is the new operator used for instantiating this regexp
     * @param self  self reference
     * @param args  arguments (optional: pattern and flags)
     * @return new NativeRegExp
     */
    @Constructor(arity = 2)
    public static Object constructor(final boolean isNew, final Object self, final Object... args) {
        if (args.length > 1) {
            return newRegExp(args[0], args[1]);
        } else if (args.length > 0) {
            return newRegExp(args[0], UNDEFINED);
        }

        return newRegExp(UNDEFINED, UNDEFINED);
    }

    /**
     * ECMA 15.10.4
     *
     * Constructor - specialized version, no args, empty regexp
     *
     * @param isNew is the new operator used for instantiating this regexp
     * @param self  self reference
     * @return new NativeRegExp
     */
    @SpecializedConstructor
    public static Object constructor(final boolean isNew, final Object self) {
        return new NativeRegExp("", "");
    }

    /**
     * ECMA 15.10.4
     *
     * Constructor - specialized version, pattern, no flags
     *
     * @param isNew is the new operator used for instantiating this regexp
     * @param self  self reference
     * @param pattern pattern
     * @return new NativeRegExp
     */
    @SpecializedConstructor
    public static Object constructor(final boolean isNew, final Object self, final Object pattern) {
        return newRegExp(pattern, UNDEFINED);
    }

    /**
     * ECMA 15.10.4
     *
     * Constructor - specialized version, pattern and flags
     *
     * @param isNew is the new operator used for instantiating this regexp
     * @param self  self reference
     * @param pattern pattern
     * @param flags  flags
     * @return new NativeRegExp
     */
    @SpecializedConstructor
    public static Object constructor(final boolean isNew, final Object self, final Object pattern, final Object flags) {
        return newRegExp(pattern, flags);
    }

    /**
     * External constructor used in generated code, which explains the public access
     *
     * @param regexp regexp
     * @param flags  flags
     * @return new NativeRegExp
     */
    public static NativeRegExp newRegExp(final Object regexp, final Object flags) {
        String  patternString = "";
        String  flagString    = "";
        boolean flagsDefined  = false;

        if (flags != UNDEFINED) {
            flagsDefined = true;
            flagString = JSType.toString(flags);
        }

        if (regexp != UNDEFINED) {
            if (regexp instanceof NativeRegExp) {
                if (!flagsDefined) {
                    return (NativeRegExp)regexp; // 15.10.3.1 - undefined flags and regexp as
                }
                throw typeError("regex.cant.supply.flags");
            }
            patternString = JSType.toString(regexp);
        }

        return new NativeRegExp(patternString, flagString);
    }

    /**
     * Build a regexp that matches {@code string} as-is. All meta-characters will be escaped.
     *
     * @param string pattern string
     * @return flat regexp
     */
    static NativeRegExp flatRegExp(String string) {
        // escape special characters
        StringBuilder sb = null;
        final int length = string.length();

        for (int i = 0; i < length; i++) {
            final char c = string.charAt(i);
            switch (c) {
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
                case '{':
                case '|':
                    if (sb == null) {
                        sb = new StringBuilder(length * 2);
                        sb.append(string, 0, i);
                    }
                    sb.append('\\');
                    sb.append(c);
                    break;
                default:
                    if (sb != null) {
                        sb.append(c);
                    }
                    break;
            }
        }
        return new NativeRegExp(sb == null ? string : sb.toString(), "");
    }

    private String getFlagString() {
        final StringBuilder sb = new StringBuilder(3);

        if (regexp.isGlobal()) {
            sb.append('g');
        }
        if (regexp.isIgnoreCase()) {
            sb.append('i');
        }
        if (regexp.isMultiline()) {
            sb.append('m');
        }

        return sb.toString();
    }

    @Override
    public String safeToString() {
        return "[RegExp " + toString() + "]";
    }

    @Override
    public String toString() {
        return "/" + regexp.getSource() + "/" + getFlagString();
    }

    /**
     * Nashorn extension: RegExp.prototype.compile - everybody implements this!
     *
     * @param self    self reference
     * @param pattern pattern
     * @param flags   flags
     * @return new NativeRegExp
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static Object compile(final Object self, final Object pattern, final Object flags) {
        final NativeRegExp regExp   = checkRegExp(self);
        final NativeRegExp compiled = newRegExp(pattern, flags);
        // copy over regexp to 'self'
        regExp.setRegExp(compiled.getRegExp());

        // Some implementations return undefined. Some return 'self'. Since return
        // value is most likely be ignored, we can play safe and return 'self'.
        return regExp;
    }

    /**
     * ECMA 15.10.6.2 RegExp.prototype.exec(string)
     *
     * @param self   self reference
     * @param string string to match against regexp
     * @return array containing the matches or {@code null} if no match
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static Object exec(final Object self, final Object string) {
        return checkRegExp(self).exec(JSType.toString(string));
    }

    /**
     * ECMA 15.10.6.3 RegExp.prototype.test(string)
     *
     * @param self   self reference
     * @param string string to test for matches against regexp
     * @return true if matches found, false otherwise
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static Object test(final Object self, final Object string) {
        return checkRegExp(self).test(JSType.toString(string));
    }

    /**
     * ECMA 15.10.6.4 RegExp.prototype.toString()
     *
     * @param self self reference
     * @return string version of regexp
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static Object toString(final Object self) {
        return checkRegExp(self).toString();
    }

    /**
     * ECMA 15.10.7.1 source
     *
     * @param self self reference
     * @return the input string for the regexp
     */
    @Getter(attributes = Attribute.NON_ENUMERABLE_CONSTANT)
    public static Object source(final Object self) {
        return checkRegExp(self).getRegExp().getSource();
    }

    /**
     * ECMA 15.10.7.2 global
     *
     * @param self self reference
     * @return true if this regexp is flagged global, false otherwise
     */
    @Getter(attributes = Attribute.NON_ENUMERABLE_CONSTANT)
    public static Object global(final Object self) {
        return checkRegExp(self).getRegExp().isGlobal();
    }

    /**
     * ECMA 15.10.7.3 ignoreCase
     *
     * @param self self reference
     * @return true if this regexp if flagged to ignore case, false otherwise
     */
    @Getter(attributes = Attribute.NON_ENUMERABLE_CONSTANT)
    public static Object ignoreCase(final Object self) {
        return checkRegExp(self).getRegExp().isIgnoreCase();
    }

    /**
     * ECMA 15.10.7.4 multiline
     *
     * @param self self reference
     * @return true if this regexp is flagged to be multiline, false otherwise
     */
    @Getter(attributes = Attribute.NON_ENUMERABLE_CONSTANT)
    public static Object multiline(final Object self) {
        return checkRegExp(self).getRegExp().isMultiline();
    }

    /**
     * Getter for non-standard RegExp.input property.
     * @param self self object
     * @return last regexp input
     */
    @Getter(where = Where.CONSTRUCTOR, attributes = Attribute.CONSTANT, name = "input")
    public static Object getLastInput(Object self) {
        final RegExpResult match = Global.instance().getLastRegExpResult();
        return match == null ? "" : match.getInput();
    }

    /**
     * Getter for non-standard RegExp.multiline property.
     * @param self self object
     * @return last regexp input
     */
    @Getter(where = Where.CONSTRUCTOR, attributes = Attribute.CONSTANT, name = "multiline")
    public static Object getLastMultiline(Object self) {
        return false; // doesn't ever seem to become true and isn't documented anyhwere
    }

    /**
     * Getter for non-standard RegExp.lastMatch property.
     * @param self self object
     * @return last regexp input
     */
    @Getter(where = Where.CONSTRUCTOR, attributes = Attribute.CONSTANT, name = "lastMatch")
    public static Object getLastMatch(Object self) {
        final RegExpResult match = Global.instance().getLastRegExpResult();
        return match == null ? "" : match.getGroup(0);
    }

    /**
     * Getter for non-standard RegExp.lastParen property.
     * @param self self object
     * @return last regexp input
     */
    @Getter(where = Where.CONSTRUCTOR, attributes = Attribute.CONSTANT, name = "lastParen")
    public static Object getLastParen(Object self) {
        final RegExpResult match = Global.instance().getLastRegExpResult();
        return match == null ? "" : match.getLastParen();
    }

    /**
     * Getter for non-standard RegExp.leftContext property.
     * @param self self object
     * @return last regexp input
     */
    @Getter(where = Where.CONSTRUCTOR, attributes = Attribute.CONSTANT, name = "leftContext")
    public static Object getLeftContext(Object self) {
        final RegExpResult match = Global.instance().getLastRegExpResult();
        return match == null ? "" : match.getInput().substring(0, match.getIndex());
    }

    /**
     * Getter for non-standard RegExp.rightContext property.
     * @param self self object
     * @return last regexp input
     */
    @Getter(where = Where.CONSTRUCTOR, attributes = Attribute.CONSTANT, name = "rightContext")
    public static Object getRightContext(Object self) {
        final RegExpResult match = Global.instance().getLastRegExpResult();
        return match == null ? "" : match.getInput().substring(match.getIndex() + match.length());
    }

    /**
     * Getter for non-standard RegExp.$1 property.
     * @param self self object
     * @return last regexp input
     */
    @Getter(where = Where.CONSTRUCTOR, attributes = Attribute.CONSTANT, name = "$1")
    public static Object getGroup1(Object self) {
        final RegExpResult match = Global.instance().getLastRegExpResult();
        return match == null ? "" : match.getGroup(1);
    }

    /**
     * Getter for non-standard RegExp.$2 property.
     * @param self self object
     * @return last regexp input
     */
    @Getter(where = Where.CONSTRUCTOR, attributes = Attribute.CONSTANT, name = "$2")
    public static Object getGroup2(Object self) {
        final RegExpResult match = Global.instance().getLastRegExpResult();
        return match == null ? "" : match.getGroup(2);
    }

    /**
     * Getter for non-standard RegExp.$3 property.
     * @param self self object
     * @return last regexp input
     */
    @Getter(where = Where.CONSTRUCTOR, attributes = Attribute.CONSTANT, name = "$3")
    public static Object getGroup3(Object self) {
        final RegExpResult match = Global.instance().getLastRegExpResult();
        return match == null ? "" : match.getGroup(3);
    }

    /**
     * Getter for non-standard RegExp.$4 property.
     * @param self self object
     * @return last regexp input
     */
    @Getter(where = Where.CONSTRUCTOR, attributes = Attribute.CONSTANT, name = "$4")
    public static Object getGroup4(Object self) {
        final RegExpResult match = Global.instance().getLastRegExpResult();
        return match == null ? "" : match.getGroup(4);
    }

    /**
     * Getter for non-standard RegExp.$5 property.
     * @param self self object
     * @return last regexp input
     */
    @Getter(where = Where.CONSTRUCTOR, attributes = Attribute.CONSTANT, name = "$5")
    public static Object getGroup5(Object self) {
        final RegExpResult match = Global.instance().getLastRegExpResult();
        return match == null ? "" : match.getGroup(5);
    }

    /**
     * Getter for non-standard RegExp.$6 property.
     * @param self self object
     * @return last regexp input
     */
    @Getter(where = Where.CONSTRUCTOR, attributes = Attribute.CONSTANT, name = "$6")
    public static Object getGroup6(Object self) {
        final RegExpResult match = Global.instance().getLastRegExpResult();
        return match == null ? "" : match.getGroup(6);
    }

    /**
     * Getter for non-standard RegExp.$7 property.
     * @param self self object
     * @return last regexp input
     */
    @Getter(where = Where.CONSTRUCTOR, attributes = Attribute.CONSTANT, name = "$7")
    public static Object getGroup7(Object self) {
        final RegExpResult match = Global.instance().getLastRegExpResult();
        return match == null ? "" : match.getGroup(7);
    }

    /**
     * Getter for non-standard RegExp.$8 property.
     * @param self self object
     * @return last regexp input
     */
    @Getter(where = Where.CONSTRUCTOR, attributes = Attribute.CONSTANT, name = "$8")
    public static Object getGroup8(Object self) {
        final RegExpResult match = Global.instance().getLastRegExpResult();
        return match == null ? "" : match.getGroup(8);
    }

    /**
     * Getter for non-standard RegExp.$9 property.
     * @param self self object
     * @return last regexp input
     */
    @Getter(where = Where.CONSTRUCTOR, attributes = Attribute.CONSTANT, name = "$9")
    public static Object getGroup9(Object self) {
        final RegExpResult match = Global.instance().getLastRegExpResult();
        return match == null ? "" : match.getGroup(9);
    }

    private RegExpResult execInner(final String string) {
        final boolean isGlobal = regexp.isGlobal();
        int start = getLastIndex();
        if (!isGlobal) {
            start = 0;
        }

        if (start < 0 || start > string.length()) {
            if (isGlobal) {
                setLastIndex(0);
            }
            return null;
        }

        final RegExpMatcher matcher = regexp.match(string);
        if (matcher == null || !matcher.search(start)) {
            if (isGlobal) {
                setLastIndex(0);
            }
            return null;
        }

        if (isGlobal) {
            setLastIndex(matcher.end());
        }

        final RegExpResult match = new RegExpResult(string, matcher.start(), groups(matcher));
        globalObject.setLastRegExpResult(match);
        return match;
    }

    // String.prototype.split method ignores the global flag and should not update lastIndex property.
    private RegExpResult execSplit(final String string, int start) {
        if (start < 0 || start > string.length()) {
            return null;
        }

        final RegExpMatcher matcher = regexp.match(string);
        if (matcher == null || !matcher.search(start)) {
            return null;
        }

        final RegExpResult match = new RegExpResult(string, matcher.start(), groups(matcher));
        globalObject.setLastRegExpResult(match);
        return match;
    }

    /**
     * Convert java.util.regex.Matcher groups to JavaScript groups.
     * That is, replace null and groups that didn't match with undefined.
     */
    private Object[] groups(final RegExpMatcher matcher) {
        final int groupCount = matcher.groupCount();
        final Object[] groups = new Object[groupCount + 1];
        final BitVector groupsInNegativeLookahead  = regexp.getGroupsInNegativeLookahead();

        for (int i = 0, lastGroupStart = matcher.start(); i <= groupCount; i++) {
            final int groupStart = matcher.start(i);
            if (lastGroupStart > groupStart
                    || (groupsInNegativeLookahead != null && groupsInNegativeLookahead.isSet(i))) {
                // (1) ECMA 15.10.2.5 NOTE 3: need to clear Atom's captures each time Atom is repeated.
                // (2) ECMA 15.10.2.8 NOTE 3: Backreferences to captures in (?!Disjunction) from elsewhere
                // in the pattern always return undefined because the negative lookahead must fail.
                groups[i] = UNDEFINED;
                continue;
            }
            final String group = matcher.group(i);
            groups[i] = group == null ? UNDEFINED : group;
            lastGroupStart = groupStart;
        }
        return groups;
    }

    /**
     * Executes a search for a match within a string based on a regular
     * expression. It returns an array of information or null if no match is
     * found.
     *
     * @param string String to match.
     * @return NativeArray of matches, string or null.
     */
    public Object exec(final String string) {
        final RegExpResult match = execInner(string);

        if (match == null) {
            return null;
        }

        return new NativeRegExpExecResult(match);
    }

    /**
     * Executes a search for a match within a string based on a regular
     * expression.
     *
     * @param string String to match.
     * @return True if a match is found.
     */
    public Object test(final String string) {
        return execInner(string) != null;
    }

    /**
     * Searches and replaces the regular expression portion (match) with the
     * replaced text instead. For the "replacement text" parameter, you can use
     * the keywords $1 to $2 to replace the original text with values from
     * sub-patterns defined within the main pattern.
     *
     * @param string String to match.
     * @param replacement Replacement string.
     * @return String with substitutions.
     */
    Object replace(final String string, final String replacement, final ScriptFunction function) {
        final RegExpMatcher matcher = regexp.match(string);

        if (matcher == null) {
            return string;
        }

        if (!regexp.isGlobal()) {
            if (!matcher.search(0)) {
                return string;
            }

            final StringBuilder sb = new StringBuilder();
            sb.append(string, 0, matcher.start());

            if (function != null) {
                sb.append(callReplaceValue(function, matcher, string));
            } else {
                appendReplacement(matcher, string, replacement, sb);
            }
            sb.append(string, matcher.end(), string.length());
            return sb.toString();
        }

        setLastIndex(0);

        if (!matcher.search(0)) {
            return string;
        }

        int thisIndex = 0;
        int previousLastIndex = 0;
        final StringBuilder sb = new StringBuilder();

        do {
            sb.append(string, thisIndex, matcher.start());
            if (function != null) {
                sb.append(callReplaceValue(function, matcher, string));
            } else {
                appendReplacement(matcher, string, replacement, sb);
            }

            // ECMA 15.5.4.10 String.prototype.match(regexp)
            thisIndex = matcher.end();
            if (thisIndex == previousLastIndex) {
                setLastIndex(thisIndex + 1);
                previousLastIndex = thisIndex + 1;
            } else {
                previousLastIndex = thisIndex;
            }
        } while (previousLastIndex <= string.length() && matcher.search(previousLastIndex));

        sb.append(string, thisIndex, string.length());

        return sb.toString();
    }

    private void appendReplacement(final RegExpMatcher matcher, final String text, final String replacement, final StringBuilder sb) {
        /*
         * Process substitution patterns:
         *
         * $$ -> $
         * $& -> the matched substring
         * $` -> the portion of string that preceeds matched substring
         * $' -> the portion of string that follows the matched substring
         * $n -> the nth capture, where n is [1-9] and $n is NOT followed by a decimal digit
         * $nn -> the nnth capture, where nn is a two digit decimal number [01-99].
         */

        int cursor = 0;
        Object[] groups = null;

        while (cursor < replacement.length()) {
            char nextChar = replacement.charAt(cursor);
            if (nextChar == '$') {
                // Skip past $
                cursor++;
                nextChar = replacement.charAt(cursor);
                final int firstDigit = nextChar - '0';

                if (firstDigit >= 0 && firstDigit <= 9 && firstDigit <= matcher.groupCount()) {
                    // $0 is not supported, but $01 is. implementation-defined: if n>m, ignore second digit.
                    int refNum = firstDigit;
                    cursor++;
                    if (cursor < replacement.length() && firstDigit < matcher.groupCount()) {
                        final int secondDigit = replacement.charAt(cursor) - '0';
                        if ((secondDigit >= 0) && (secondDigit <= 9)) {
                            final int newRefNum = (firstDigit * 10) + secondDigit;
                            if (newRefNum <= matcher.groupCount() && newRefNum > 0) {
                                // $nn ($01-$99)
                                refNum = newRefNum;
                                cursor++;
                            }
                        }
                    }
                    if (refNum > 0) {
                        if (groups == null) {
                            groups = groups(matcher);
                        }
                        // Append group if matched.
                        if (groups[refNum] != UNDEFINED) {
                            sb.append((String) groups[refNum]);
                        }
                    } else { // $0. ignore.
                        assert refNum == 0;
                        sb.append("$0");
                    }
                } else if (nextChar == '$') {
                    sb.append('$');
                    cursor++;
                } else if (nextChar == '&') {
                    sb.append(matcher.group());
                    cursor++;
                } else if (nextChar == '`') {
                    sb.append(text, 0, matcher.start());
                    cursor++;
                } else if (nextChar == '\'') {
                    sb.append(text, matcher.end(), text.length());
                    cursor++;
                } else {
                    // unknown substitution or $n with n>m. skip.
                    sb.append('$');
                }
            } else {
                sb.append(nextChar);
                cursor++;
            }
        }
    }

    private String callReplaceValue(final ScriptFunction function, final RegExpMatcher matcher, final String string) {
        final Object[] groups = groups(matcher);
        final Object[] args   = Arrays.copyOf(groups, groups.length + 2);

        args[groups.length]     = matcher.start();
        args[groups.length + 1] = string;

        final Object self = function.isStrict() ? UNDEFINED : Global.instance();

        return JSType.toString(ScriptRuntime.apply(function, self, args));
    }

    /**
     * Breaks up a string into an array of substrings based on a regular
     * expression or fixed string.
     *
     * @param string String to match.
     * @param limit  Split limit.
     * @return Array of substrings.
     */
    Object split(final String string, final long limit) {
        if (limit == 0L) {
            return new NativeArray();
        }

        final List<Object> matches = new ArrayList<>();

        RegExpResult match;
        final int inputLength = string.length();
        int splitLastLength = -1;
        int splitLastIndex = 0;
        int splitLastLastIndex = 0;

        while ((match = execSplit(string, splitLastIndex)) != null) {
            splitLastIndex = match.getIndex() + match.length();

            if (splitLastIndex > splitLastLastIndex) {
                matches.add(string.substring(splitLastLastIndex, match.getIndex()));
                final Object[] groups = match.getGroups();
                if (groups.length > 1 && match.getIndex() < inputLength) {
                    for (int index = 1; index < groups.length && matches.size() < limit; index++) {
                        matches.add(groups[index]);
                    }
                }

                splitLastLength = match.length();

                if (matches.size() >= limit) {
                    break;
                }
            }

            // bump the index to avoid infinite loop
            if (splitLastIndex == splitLastLastIndex) {
                splitLastIndex++;
            } else {
                splitLastLastIndex = splitLastIndex;
            }
        }

        if (matches.size() < limit) {
            // check special case if we need to append an empty string at the
            // end of the match
            // if the lastIndex was the entire string
            if (splitLastLastIndex == string.length()) {
                if (splitLastLength > 0 || execSplit("", 0) == null) {
                    matches.add("");
                }
            } else {
                matches.add(string.substring(splitLastLastIndex, inputLength));
            }
        }

        return new NativeArray(matches.toArray());
    }

    /**
     * Tests for a match in a string. It returns the index of the match, or -1
     * if not found.
     *
     * @param string String to match.
     * @return Index of match.
     */
    Object search(final String string) {
        final RegExpResult match = execInner(string);

        if (match == null) {
            return -1;
        }

        return match.getIndex();
    }

    /**
     * Fast lastIndex getter
     * @return last index property as int
     */
    public int getLastIndex() {
        return JSType.toInt32(lastIndex);
    }

    /**
     * Fast lastIndex getter
     * @return last index property as boxed integer
     */
    public Object getLastIndexObject() {
        return lastIndex;
    }

    /**
     * Fast lastIndex setter
     * @param lastIndex lastIndex
     */
    public void setLastIndex(final int lastIndex) {
        this.lastIndex = JSType.toObject(lastIndex);
    }

    private void init() {
        // Keep reference to global object to support "static" properties of RegExp
        this.globalObject = Global.instance();
        this.setProto(globalObject.getRegExpPrototype());
    }

    private static NativeRegExp checkRegExp(final Object self) {
        Global.checkObjectCoercible(self);
        if (self instanceof NativeRegExp) {
            return (NativeRegExp)self;
        } else if (self != null && self == Global.instance().getRegExpPrototype()) {
            return Global.instance().DEFAULT_REGEXP;
        } else {
            throw typeError("not.a.regexp", ScriptRuntime.safeToString(self));
        }
    }

    boolean getGlobal() {
        return regexp.isGlobal();
    }

    private RegExp getRegExp() {
        return regexp;
    }

    private void setRegExp(final RegExp regexp) {
        this.regexp = regexp;
    }

}
