/*
 * Copyright (c) 2010, 2012, Oracle and/or its affiliates. All rights reserved.
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
import static jdk.nashorn.internal.runtime.JSType.isRepresentableAsInt;
import static jdk.nashorn.internal.runtime.ScriptRuntime.UNDEFINED;
import static jdk.nashorn.internal.runtime.arrays.ArrayIndex.getArrayIndexNoThrow;
import static jdk.nashorn.internal.runtime.linker.Lookup.MH;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import jdk.nashorn.internal.objects.annotations.Attribute;
import jdk.nashorn.internal.objects.annotations.Constructor;
import jdk.nashorn.internal.objects.annotations.Function;
import jdk.nashorn.internal.objects.annotations.Getter;
import jdk.nashorn.internal.objects.annotations.ScriptClass;
import jdk.nashorn.internal.objects.annotations.SpecializedConstructor;
import jdk.nashorn.internal.objects.annotations.SpecializedFunction;
import jdk.nashorn.internal.objects.annotations.Where;
import jdk.nashorn.internal.parser.Lexer;
import jdk.nashorn.internal.runtime.ConsString;
import jdk.nashorn.internal.runtime.JSType;
import jdk.nashorn.internal.runtime.ScriptFunction;
import jdk.nashorn.internal.runtime.ScriptObject;
import jdk.nashorn.internal.runtime.ScriptRuntime;
import jdk.nashorn.internal.runtime.arrays.ArrayIndex;
import jdk.nashorn.internal.runtime.linker.NashornCallSiteDescriptor;
import jdk.nashorn.internal.runtime.linker.NashornGuards;
import jdk.nashorn.internal.runtime.linker.PrimitiveLookup;
import org.dynalang.dynalink.linker.GuardedInvocation;


/**
 * ECMA 15.5 String Objects.
 */
@ScriptClass("String")
public final class NativeString extends ScriptObject {

    private final CharSequence value;

    private static final MethodHandle WRAPFILTER = findWrapFilter();

    NativeString(final CharSequence value) {
        this(value, Global.instance().getStringPrototype());
    }

    private NativeString(final CharSequence value, final ScriptObject proto) {
        assert value instanceof String || value instanceof ConsString;
        this.value = value;
        this.setProto(proto);
    }

    @Override
    public String safeToString() {
        return "[String " + toString() + "]";
    }

    @Override
    public String toString() {
        return getStringValue();
    }

    @Override
    public boolean equals(final Object other) {
        if (other instanceof NativeString) {
            return getStringValue().equals(((NativeString) other).getStringValue());
        }

        return false;
    }

    @Override
    public int hashCode() {
        return getStringValue().hashCode();
    }

    private String getStringValue() {
        return value instanceof String ? (String) value : value.toString();
    }

    private CharSequence getValue() {
        return value;
    }

    @Override
    public String getClassName() {
        return "String";
    }

    @Override
    public Object getLength() {
        return value.length();
    }

    // String characters can be accessed with array-like indexing..
    @Override
    public Object get(final Object key) {
        final int index = getArrayIndexNoThrow(key);
        if (index >= 0 && index < value.length()) {
            return String.valueOf(value.charAt(index));
        }
        return super.get(key);
    }

    @Override
    public Object get(final double key) {
        if (isRepresentableAsInt(key)) {
            return get((int)key);
        }
        return super.get(key);
    }

    @Override
    public Object get(final long key) {
        if (key >= 0 && key < value.length()) {
            return String.valueOf(value.charAt((int)key));
        }
        return super.get(key);
    }

    @Override
    public Object get(final int key) {
        if (key >= 0 && key < value.length()) {
            return String.valueOf(value.charAt(key));
        }
        return super.get(key);
    }

    @Override
    public int getInt(final Object key) {
        return JSType.toInt32(get(key));
    }

    @Override
    public int getInt(final double key) {
        return JSType.toInt32(get(key));
    }

    @Override
    public int getInt(final long key) {
        return JSType.toInt32(get(key));
    }

    @Override
    public int getInt(final int key) {
        return JSType.toInt32(get(key));
    }

    @Override
    public long getLong(final Object key) {
        return JSType.toUint32(get(key));
    }

    @Override
    public long getLong(final double key) {
        return JSType.toUint32(get(key));
    }

    @Override
    public long getLong(final long key) {
        return JSType.toUint32(get(key));
    }

    @Override
    public long getLong(final int key) {
        return JSType.toUint32(get(key));
    }

    @Override
    public double getDouble(final Object key) {
        return JSType.toNumber(get(key));
    }

    @Override
    public double getDouble(final double key) {
        return JSType.toNumber(get(key));
    }

    @Override
    public double getDouble(final long key) {
        return JSType.toNumber(get(key));
    }

    @Override
    public double getDouble(final int key) {
        return JSType.toNumber(get(key));
    }

    @Override
    public boolean has(final Object key) {
        final int index = getArrayIndexNoThrow(key);
        return isValid(index) || super.has(key);
    }

    @Override
    public boolean has(final int key) {
        return isValid(key) || super.has(key);
    }

    @Override
    public boolean has(final long key) {
        final int index = getArrayIndexNoThrow(key);
        return isValid(index) || super.has(key);
    }

    @Override
    public boolean has(final double key) {
        final int index = getArrayIndexNoThrow(key);
        return isValid(index) || super.has(key);
    }

    @Override
    public boolean hasOwnProperty(final Object key) {
        final int index = getArrayIndexNoThrow(key);
        return isValid(index) || super.hasOwnProperty(key);
    }

    @Override
    public boolean hasOwnProperty(final int key) {
        return isValid(key) || super.hasOwnProperty(key);
    }

    @Override
    public boolean hasOwnProperty(final long key) {
        final int index = getArrayIndexNoThrow(key);
        return isValid(index) || super.hasOwnProperty(key);
    }

    @Override
    public boolean hasOwnProperty(final double key) {
        final int index = getArrayIndexNoThrow(key);
        return isValid(index) || super.hasOwnProperty(key);
    }

    @Override
    public boolean delete(final int key, final boolean strict) {
        return checkDeleteIndex(key, strict)? false : super.delete(key, strict);
    }

    @Override
    public boolean delete(final long key, final boolean strict) {
        final int index = getArrayIndexNoThrow(key);
        return checkDeleteIndex(index, strict)? false : super.delete(key, strict);
    }

    @Override
    public boolean delete(final double key, final boolean strict) {
        final int index = getArrayIndexNoThrow(key);
        return checkDeleteIndex(index, strict)? false : super.delete(key, strict);
    }

    @Override
    public boolean delete(final Object key, final boolean strict) {
        final int index = getArrayIndexNoThrow(key);
        return checkDeleteIndex(index, strict)? false : super.delete(key, strict);
    }

    private boolean checkDeleteIndex(final int index, final boolean strict) {
        if (isValid(index)) {
            if (strict) {
                typeError(Global.instance(), "cant.delete.property", Integer.toString(index), ScriptRuntime.safeToString(this));
            }
            return true;
        }

        return false;
    }

    @Override
    public Object getOwnPropertyDescriptor(final String key) {
        final int index = ArrayIndex.getArrayIndexNoThrow(key);
        if (index >= 0 && index < value.length()) {
            final Global global = Global.instance();
            return global.newDataDescriptor(String.valueOf(value.charAt(index)), false, true, false);
        }

        return super.getOwnPropertyDescriptor(key);
    }

    /**
     * return a List of own keys associated with the object.
     * @param all True if to include non-enumerable keys.
     * @return Array of keys.
     */
    @Override
    public String[] getOwnKeys(final boolean all) {
        final List<Object> keys = new ArrayList<>();

        // add string index keys
        for (int i = 0; i < value.length(); i++) {
            keys.add(JSType.toString(i));
        }

        // add super class properties
        keys.addAll(Arrays.asList(super.getOwnKeys(all)));
        return keys.toArray(new String[keys.size()]);
    }

    /**
     * ECMA 15.5.3 String.length
     * @param self self reference
     * @return     value of length property for string
     */
    @Getter(attributes = Attribute.NOT_ENUMERABLE | Attribute.NOT_WRITABLE | Attribute.NOT_CONFIGURABLE)
    public static Object length(final Object self) {
        return getCharSequence(self).length();
    }

    /**
     * ECMA 15.5.3.2 String.fromCharCode ( [ char0 [ , char1 [ , ... ] ] ] )
     * @param self  self reference
     * @param args  array of arguments to be interpreted as char
     * @return string with arguments translated to charcodes
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 1, where = Where.CONSTRUCTOR)
    public static Object fromCharCode(final Object self, final Object... args) {
        final char[] buf = new char[args.length];
        int index = 0;
        for (final Object arg : args) {
            buf[index++] = (char)JSType.toUint16(arg);
        }
        return new String(buf);
    }

    /**
     * ECMA 15.5.3.2 - specialization for one char
     * @param self  self reference
     * @param value one argument to be interpreted as char
     * @return string with one charcode
     */
    @SpecializedFunction
    public static Object fromCharCode(final Object self, final Object value) {
        try {
            return "" + (char)JSType.toUint16(((Number)value).doubleValue());
        } catch (final ClassCastException e) {
            return fromCharCode(self, new Object[] { value });
        }
    }

    /**
     * ECMA 15.5.3.2 - specialization for one char of int type
     * @param self  self reference
     * @param value one argument to be interpreted as char
     * @return string with one charcode
     */
    @SpecializedFunction
    public static Object fromCharCode(final Object self, final int value) {
        return "" + (char)(value & 0xffff);
    }

    /**
     * ECMA 15.5.3.2 - specialization for one char of long type
     * @param self  self reference
     * @param value one argument to be interpreted as char
     * @return string with one charcode
     */
    @SpecializedFunction
    public static Object fromCharCode(final Object self, final long value) {
        return "" + (char)((int)value & 0xffff);
    }

    /**
     * ECMA 15.5.3.2 - specialization for one char of double type
     * @param self  self reference
     * @param value one argument to be interpreted as char
     * @return string with one charcode
     */
    @SpecializedFunction
    public static Object fromCharCode(final Object self, final double value) {
        return "" + (char)JSType.toUint16(value);
    }

    /**
     * ECMA 15.5.4.2 String.prototype.toString ( )
     * @param self self reference
     * @return self as string
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static Object toString(final Object self) {
        return getString(self);
    }

    /**
     * ECMA 15.5.4.3 String.prototype.valueOf ( )
     * @param self self reference
     * @return self as string
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static Object valueOf(final Object self) {
        return getString(self);
    }

    /**
     * ECMA 15.5.4.4 String.prototype.charAt (pos)
     * @param self self reference
     * @param pos  position in string
     * @return string representing the char at the given position
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static Object charAt(final Object self, final Object pos) {
        try {
            return String.valueOf(((String)self).charAt(((Number)pos).intValue()));
        } catch (final ClassCastException | IndexOutOfBoundsException | NullPointerException e) {
            Global.checkObjectCoercible(self);
            final String str = JSType.toString(self);
            final int    at  = JSType.toInteger(pos);
            if (at < 0 || at >= str.length()) {
                return "";
            }
            return String.valueOf(str.charAt(at));
        }
    }

    /**
     * ECMA 15.5.4.5 String.prototype.charCodeAt (pos)
     * @param self self reference
     * @param pos  position in string
     * @return number representing charcode at position
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static Object charCodeAt(final Object self, final Object pos) {
        try {
            return (int)((String)self).charAt(((Number)pos).intValue());
        } catch (final ClassCastException | IndexOutOfBoundsException | NullPointerException e) {
            Global.checkObjectCoercible(self);
            final String str = JSType.toString(self);
            final int at     = JSType.toInteger(pos);
            if (at < 0 || at >= str.length()) {
                return Double.NaN;
            }

            return JSType.toObject(str.charAt(at));
        }
    }

    /**
     * ECMA 15.5.4.6 String.prototype.concat ( [ string1 [ , string2 [ , ... ] ] ] )
     * @param self self reference
     * @param args list of string to concatenate
     * @return concatenated string
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 1)
    public static Object concat(final Object self, final Object... args) {
        Global.checkObjectCoercible(self);
        final StringBuilder sb = new StringBuilder(JSType.toString(self));
        if (args != null) {
            for (final Object obj : args) {
                sb.append(JSType.toString(obj));
            }
        }
        return sb.toString();
    }

    /**
     * ECMA 15.5.4.7 String.prototype.indexOf (searchString, position)
     * @param self   self reference
     * @param search string to search for
     * @param pos    position to start search
     * @return position of first match or -1
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 1)
    public static Object indexOf(final Object self, final Object search, final Object pos) {
        try {
            return ((String)self).indexOf((String)search, ((Number)pos).intValue()); //assuming that the conversions really mean "toInteger" and not "toInt32" this is ok.
        } catch (final ClassCastException | IndexOutOfBoundsException | NullPointerException e) {
            Global.checkObjectCoercible(self);
            return JSType.toString(self).indexOf(JSType.toString(search), JSType.toInteger(pos));
        }
    }

    /**
     * ECMA 15.5.4.8 String.prototype.lastIndexOf (searchString, position)
     * @param self   self reference
     * @param search string to search for
     * @param pos    position to start search
     * @return last position of match or -1
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 1)
    public static Object lastIndexOf(final Object self, final Object search, final Object pos) {
        Global.checkObjectCoercible(self);

        final String str       = JSType.toString(self);
        final String searchStr = JSType.toString(search);

        int from;

        if (pos == UNDEFINED) {
            from = str.length();
        } else {
            final double numPos = JSType.toNumber(pos);
            from = !Double.isNaN(numPos) ? (int)numPos : (int)Double.POSITIVE_INFINITY;
        }

        return str.lastIndexOf(searchStr, from);
    }

    /**
     * ECMA 15.5.4.9 String.prototype.localeCompare (that)
     * @param self self reference
     * @param that comparison object
     * @return result of locale sensitive comparison operation between {@code self} and {@code that}
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static Object localeCompare(final Object self, final Object that) {
        Global.checkObjectCoercible(self);

        final String   str      = JSType.toString(self);
        final Collator collator = Collator.getInstance(Global.getThisContext().getLocale());

        collator.setStrength(Collator.IDENTICAL);
        collator.setDecomposition(Collator.CANONICAL_DECOMPOSITION);

        return (double)collator.compare(str, JSType.toString(that));
    }

    /**
     * ECMA 15.5.4.10 String.prototype.match (regexp)
     * @param self   self reference
     * @param regexp regexp expression
     * @return array of regexp matches
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static Object match(final Object self, final Object regexp) {
        Global.checkObjectCoercible(self);

        final String str = JSType.toString(self);

        NativeRegExp nativeRegExp;
        if (regexp == UNDEFINED) {
            nativeRegExp = new NativeRegExp("");
        } else {
            nativeRegExp = Global.toRegExp(regexp);
        }

        if (!nativeRegExp.getGlobal()) {
            return nativeRegExp.exec(str);
        }

        nativeRegExp.setLastIndex(0);

        int previousLastIndex = 0;
        final List<Object> matches = new ArrayList<>();

        Object result;
        while ((result = nativeRegExp.exec(str)) != null) {
            final int thisIndex = nativeRegExp.getLastIndex();
            if (thisIndex == previousLastIndex) {
                nativeRegExp.setLastIndex(thisIndex + 1);
                previousLastIndex = thisIndex + 1;
            } else {
                previousLastIndex = thisIndex;
            }
            matches.add(((ScriptObject)result).get(0));
        }

        if (matches.isEmpty()) {
            return null;
        }

        return new NativeArray(matches.toArray());
    }

    /**
     * ECMA 15.5.4.11 String.prototype.replace (searchValue, replaceValue)
     * @param self        self reference
     * @param string      item to replace
     * @param replacement item to replace it with
     * @return string after replacement
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static Object replace(final Object self, final Object string, final Object replacement) {
        Global.checkObjectCoercible(self);

        final String str = JSType.toString(self);

        final NativeRegExp nativeRegExp;
        if (string instanceof NativeRegExp) {
            nativeRegExp = (NativeRegExp) string;
        } else {
            nativeRegExp = new NativeRegExp(Pattern.compile(JSType.toString(string), Pattern.LITERAL));
        }

        if (replacement instanceof ScriptFunction) {
            return nativeRegExp.replace(str, "", (ScriptFunction)replacement);
        }

        return nativeRegExp.replace(str, JSType.toString(replacement), null);
    }

    /**
     * ECMA 15.5.4.12 String.prototype.search (regexp)
     *
     * @param self    self reference
     * @param string  string to search for
     * @return offset where match occurred
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static Object search(final Object self, final Object string) {
        Global.checkObjectCoercible(self);

        final String       str          = JSType.toString(self);
        final NativeRegExp nativeRegExp = Global.toRegExp(string == UNDEFINED ? "" : string);

        return nativeRegExp.search(str);
    }

    /**
     * ECMA 15.5.4.13 String.prototype.slice (start, end)
     *
     * @param self  self reference
     * @param start start position for slice
     * @param end   end position for slice
     * @return sliced out substring
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static Object slice(final Object self, final Object start, final Object end) {
        Global.checkObjectCoercible(self);

        final String str      = JSType.toString(self);
        final int    len      = str.length();
        final int    intStart = JSType.toInteger(start);
        final int    intEnd   = (end == UNDEFINED) ? len : JSType.toInteger(end);

        int from;

        if (intStart < 0) {
            from = Math.max(len + intStart, 0);
        } else {
            from = Math.min(intStart, len);
        }

        int to;

        if (intEnd < 0) {
            to = Math.max(len + intEnd,0);
        } else {
            to = Math.min(intEnd, len);
        }

        return str.substring(Math.min(from,  to), to);
    }

    /**
     * ECMA 15.5.4.14 String.prototype.split (separator, limit)
     *
     * @param self      self reference
     * @param separator separator for split
     * @param limit     limit for splits
     * @return array object in which splits have been placed
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static Object split(final Object self, final Object separator, final Object limit) {
        Global.checkObjectCoercible(self);

        final String str = JSType.toString(self);

        if (separator == UNDEFINED) {
            return new NativeArray(new Object[]{str});
        }

        final long lim = (limit == UNDEFINED) ? JSType.MAX_UINT : JSType.toUint32(limit);

        if (separator instanceof NativeRegExp) {
            return ((NativeRegExp) separator).split(str, lim);
        }

        // when separator is a string, it has to be treated as a
        // literal search string to be used for splitting.
        return new NativeRegExp(Pattern.compile(JSType.toString(separator), Pattern.LITERAL)).split(str, lim);
    }

    /**
     * ECMA B.2.3 String.prototype.substr (start, length)
     *
     * @param self   self reference
     * @param start  start position
     * @param length length of section
     * @return substring given start and length of section
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static Object substr(final Object self, final Object start, final Object length) {
        final String str       = JSType.toString(self);
        final int    strLength = str.length();

        int intStart = JSType.toInteger(start);
        if (intStart < 0) {
            intStart = Math.max(intStart + strLength, 0);
        }

        final int intLen = Math.min(Math.max((length == UNDEFINED) ? Integer.MAX_VALUE : JSType.toInteger(length), 0), strLength - intStart);

        return intLen <= 0 ? "" : str.substring(intStart, intStart + intLen);
    }

    /**
     * ECMA 15.5.4.15 String.prototype.substring (start, end)
     *
     * @param self  self reference
     * @param start start position of substring
     * @param end   end position of substring
     * @return substring given start and end indexes
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static Object substring(final Object self, final Object start, final Object end) {
        Global.checkObjectCoercible(self);

        final String str        = JSType.toString(self);
        final int    len        = str.length();
        final int    intStart   = JSType.toInteger(start);
        final int    intEnd     = (end == UNDEFINED) ? len : JSType.toInteger(end);
        final int    finalStart = Math.min((intStart < 0) ? 0 : intStart, len);
        final int    finalEnd   = Math.min((intEnd < 0) ? 0 : intEnd, len);

        int from, to;

        if (finalStart < finalEnd) {
            from = finalStart;
            to = finalEnd;
        } else {
            from = finalEnd;
            to = finalStart;
        }
        return str.substring(from, to);
    }

    /**
     * ECMA 15.5.4.16 String.prototype.toLowerCase ( )
     * @param self self reference
     * @return string to lower case
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static Object toLowerCase(final Object self) {
        Global.checkObjectCoercible(self);
        return JSType.toString(self).toLowerCase();
    }

    /**
     * ECMA 15.5.4.17 String.prototype.toLocaleLowerCase ( )
     * @param self self reference
     * @return string to locale sensitive lower case
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static Object toLocaleLowerCase(final Object self) {
        Global.checkObjectCoercible(self);
        return JSType.toString(self).toLowerCase(Global.getThisContext().getLocale());
    }

    /**
     * ECMA 15.5.4.18 String.prototype.toUpperCase ( )
     * @param self self reference
     * @return string to upper case
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static Object toUpperCase(final Object self) {
        Global.checkObjectCoercible(self);
        return JSType.toString(self).toUpperCase();
    }

    /**
     * ECMA 15.5.4.19 String.prototype.toLocaleUpperCase ( )
     * @param self self reference
     * @return string to locale sensitive upper case
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static Object toLocaleUpperCase(final Object self) {
        Global.checkObjectCoercible(self);
        return JSType.toString(self).toUpperCase(Global.getThisContext().getLocale());
    }

    /**
     * ECMA 15.5.4.20 String.prototype.trim ( )
     * @param self self reference
     * @return string trimmed from whitespace
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static Object trim(final Object self) {
        Global.checkObjectCoercible(self);

        final String str = JSType.toString(self);

        int start = 0;
        int end   = str.length() - 1;

        while (start <= end && Lexer.isJSWhitespace(str.charAt(start))) {
            start++;
        }
        while (end > start && Lexer.isJSWhitespace(str.charAt(end))) {
            end--;
        }

        return str.substring(start, end + 1);
    }

    private static Object newObj(final Object self, final CharSequence str) {
        if (self instanceof ScriptObject) {
            return new NativeString(str, ((ScriptObject)self).getProto());
        }
        return new NativeString(str, Global.instance().getStringPrototype());
    }

    /**
     * ECMA 15.5.2.1 new String ( [ value ] )
     *
     * Constructor
     *
     * @param newObj is this constructor invoked with the new operator
     * @param self   self reference
     * @param args   arguments (a value)
     *
     * @return new NativeString, empty string if no args, extraneous args ignored
     */
    @Constructor(arity = 1)
    public static Object constructor(final boolean newObj, final Object self, final Object... args) {
        final CharSequence str = (args.length > 0) ? JSType.toCharSequence(args[0]) : "";
        return newObj ? newObj(self, str) : str.toString();
    }

    /**
     * ECMA 15.5.2.1 new String ( [ value ] ) - special version with no args
     *
     * Constructor
     *
     * @param newObj is this constructor invoked with the new operator
     * @param self   self reference
     *
     * @return new NativeString ("")
     */
    @SpecializedConstructor
    public static Object constructor(final boolean newObj, final Object self) {
        return newObj ? newObj(self, "") : "";
    }

    //TODO - why is there no String with one String arg constructor?

    /**
     * ECMA 15.5.2.1 new String ( [ value ] ) - special version with exactly one {@code int} arg
     *
     * Constructor
     *
     * @param newObj is this constructor invoked with the new operator
     * @param self   self reference
     * @param arg    the arg
     *
     * @return new NativeString containing the string representation of the arg
     */
    @SpecializedConstructor
    public static Object constructor(final boolean newObj, final Object self, final int arg) {
        final CharSequence str = JSType.toCharSequence(arg);
        return newObj ? newObj(self, str) : str;
    }

    /**
     * Lookup the appropriate method for an invoke dynamic call.
     *
     * @param desc the call site descriptor
     * @param receiver receiver of call
     * @return Link to be invoked at call site.
     */
    public static GuardedInvocation lookupPrimitive(final NashornCallSiteDescriptor desc, final Object receiver) {
        final MethodHandle guard = NashornGuards.getInstanceOf2Guard(String.class, ConsString.class);
        return PrimitiveLookup.lookupPrimitive(desc, guard, new NativeString((CharSequence)receiver), WRAPFILTER);
    }

    @SuppressWarnings("unused")
    private static NativeString wrapFilter(final Object receiver) {
        return new NativeString((CharSequence)receiver);
    }

    private static CharSequence getCharSequence(final Object self) {
        if (self instanceof String || self instanceof ConsString) {
            return (CharSequence)self;
        } else if (self instanceof NativeString) {
            return ((NativeString)self).getValue();
        } else if (self != null && self == Global.instance().getStringPrototype()) {
            return "";
        } else {
            typeError(Global.instance(), "not.a.string", ScriptRuntime.safeToString(self));
            return null;
        }
    }

    private static String getString(final Object self) {
        if (self instanceof String) {
            return (String)self;
        } else if (self instanceof ConsString) {
            return self.toString();
        } else if (self instanceof NativeString) {
            return ((NativeString)self).getStringValue();
        } else if (self != null && self == Global.instance().getStringPrototype()) {
            return "";
        } else {
            typeError(Global.instance(), "not.a.string", ScriptRuntime.safeToString(self));
            return null;
        }
    }

    private boolean isValid(final int key) {
        return key >= 0 && key < value.length();
    }

    private static MethodHandle findWrapFilter() {
        try {
            return MethodHandles.lookup().findStatic(NativeString.class, "wrapFilter", MH.type(NativeString.class, Object.class));
        } catch (final NoSuchMethodException | IllegalAccessException e) {
            throw new AssertionError(e);
        }
    }
}
