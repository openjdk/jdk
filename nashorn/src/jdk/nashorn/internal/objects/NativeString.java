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

import static jdk.nashorn.internal.lookup.Lookup.MH;
import static jdk.nashorn.internal.runtime.ECMAErrors.typeError;
import static jdk.nashorn.internal.runtime.JSType.isRepresentableAsInt;
import static jdk.nashorn.internal.runtime.ScriptRuntime.UNDEFINED;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import jdk.internal.dynalink.CallSiteDescriptor;
import jdk.internal.dynalink.linker.GuardedInvocation;
import jdk.internal.dynalink.linker.LinkRequest;
import jdk.nashorn.internal.lookup.MethodHandleFactory.LookupException;
import jdk.nashorn.internal.objects.annotations.Attribute;
import jdk.nashorn.internal.objects.annotations.Constructor;
import jdk.nashorn.internal.objects.annotations.Function;
import jdk.nashorn.internal.objects.annotations.Getter;
import jdk.nashorn.internal.objects.annotations.ScriptClass;
import jdk.nashorn.internal.objects.annotations.SpecializedConstructor;
import jdk.nashorn.internal.objects.annotations.SpecializedFunction;
import jdk.nashorn.internal.objects.annotations.Where;
import jdk.nashorn.internal.runtime.ConsString;
import jdk.nashorn.internal.runtime.JSType;
import jdk.nashorn.internal.runtime.PropertyMap;
import jdk.nashorn.internal.runtime.ScriptFunction;
import jdk.nashorn.internal.runtime.ScriptObject;
import jdk.nashorn.internal.runtime.ScriptRuntime;
import jdk.nashorn.internal.runtime.arrays.ArrayIndex;
import jdk.nashorn.internal.runtime.linker.NashornGuards;
import jdk.nashorn.internal.runtime.linker.PrimitiveLookup;


/**
 * ECMA 15.5 String Objects.
 */
@ScriptClass("String")
public final class NativeString extends ScriptObject {

    private final CharSequence value;

    // Method handle to create an object wrapper for a primitive string
    private static final MethodHandle WRAPFILTER = findOwnMH("wrapFilter", MH.type(NativeString.class, Object.class));
    // Method handle to retrieve the String prototype object
    private static final MethodHandle PROTOFILTER = findOwnMH("protoFilter", MH.type(Object.class, Object.class));

    // initialized by nasgen
    private static PropertyMap $nasgenmap$;

    private NativeString(final CharSequence value) {
        this(value, Global.instance());
    }

    NativeString(final CharSequence value, final Global global) {
        this(value, global.getStringPrototype(), $nasgenmap$);
    }

    private NativeString(final CharSequence value, final ScriptObject proto, final PropertyMap map) {
        super(proto, map);
        assert value instanceof String || value instanceof ConsString;
        this.value = value;
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

    // This is to support length as method call as well.
    @Override
    protected GuardedInvocation findGetMethod(final CallSiteDescriptor desc, final LinkRequest request, final String operator) {
        final String name = desc.getNameToken(2);

        // if str.length(), then let the bean linker handle it
        if ("length".equals(name) && "getMethod".equals(operator)) {
            return null;
        }

        return super.findGetMethod(desc, request, operator);
    }

    // This is to provide array-like access to string characters without creating a NativeString wrapper.
    @Override
    protected GuardedInvocation findGetIndexMethod(final CallSiteDescriptor desc, final LinkRequest request) {
        final Object self = request.getReceiver();
        final Class<?> returnType = desc.getMethodType().returnType();

        if (returnType == Object.class && (self instanceof String || self instanceof ConsString)) {
            try {
                MethodHandle mh = MH.findStatic(MethodHandles.lookup(), NativeString.class, "get", desc.getMethodType());
                return new GuardedInvocation(mh, NashornGuards.getInstanceOf2Guard(String.class, ConsString.class));
            } catch (final LookupException e) {
                // Shouldn't happen. Fall back to super
            }
        }
        return super.findGetIndexMethod(desc, request);
    }

    @SuppressWarnings("unused")
    private static Object get(final Object self, final Object key) {
        final CharSequence cs = JSType.toCharSequence(self);
        final Object primitiveKey = JSType.toPrimitive(key, String.class);
        final int index = ArrayIndex.getArrayIndex(primitiveKey);
        if (index >= 0 && index < cs.length()) {
            return String.valueOf(cs.charAt(index));
        }
        return ((ScriptObject) Global.toObject(self)).get(primitiveKey);
    }

    @SuppressWarnings("unused")
    private static Object get(final Object self, final double key) {
        if (isRepresentableAsInt(key)) {
            return get(self, (int)key);
        }
        return ((ScriptObject) Global.toObject(self)).get(key);
    }

    @SuppressWarnings("unused")
    private static Object get(final Object self, final long key) {
        final CharSequence cs = JSType.toCharSequence(self);
        if (key >= 0 && key < cs.length()) {
            return String.valueOf(cs.charAt((int)key));
        }
        return ((ScriptObject) Global.toObject(self)).get(key);
    }

    private static Object get(final Object self, final int key) {
        final CharSequence cs = JSType.toCharSequence(self);
        if (key >= 0 && key < cs.length()) {
            return String.valueOf(cs.charAt(key));
        }
        return ((ScriptObject) Global.toObject(self)).get(key);
    }

    // String characters can be accessed with array-like indexing..
    @Override
    public Object get(final Object key) {
        final Object primitiveKey = JSType.toPrimitive(key, String.class);
        final int index = ArrayIndex.getArrayIndex(primitiveKey);
        if (index >= 0 && index < value.length()) {
            return String.valueOf(value.charAt(index));
        }
        return super.get(primitiveKey);
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
        final Object primitiveKey = JSType.toPrimitive(key, String.class);
        final int index = ArrayIndex.getArrayIndex(primitiveKey);
        return isValid(index) || super.has(primitiveKey);
    }

    @Override
    public boolean has(final int key) {
        return isValid(key) || super.has(key);
    }

    @Override
    public boolean has(final long key) {
        final int index = ArrayIndex.getArrayIndex(key);
        return isValid(index) || super.has(key);
    }

    @Override
    public boolean has(final double key) {
        final int index = ArrayIndex.getArrayIndex(key);
        return isValid(index) || super.has(key);
    }

    @Override
    public boolean hasOwnProperty(final Object key) {
        final Object primitiveKey = JSType.toPrimitive(key, String.class);
        final int index = ArrayIndex.getArrayIndex(primitiveKey);
        return isValid(index) || super.hasOwnProperty(primitiveKey);
    }

    @Override
    public boolean hasOwnProperty(final int key) {
        return isValid(key) || super.hasOwnProperty(key);
    }

    @Override
    public boolean hasOwnProperty(final long key) {
        final int index = ArrayIndex.getArrayIndex(key);
        return isValid(index) || super.hasOwnProperty(key);
    }

    @Override
    public boolean hasOwnProperty(final double key) {
        final int index = ArrayIndex.getArrayIndex(key);
        return isValid(index) || super.hasOwnProperty(key);
    }

    @Override
    public boolean delete(final int key, final boolean strict) {
        return checkDeleteIndex(key, strict)? false : super.delete(key, strict);
    }

    @Override
    public boolean delete(final long key, final boolean strict) {
        final int index = ArrayIndex.getArrayIndex(key);
        return checkDeleteIndex(index, strict)? false : super.delete(key, strict);
    }

    @Override
    public boolean delete(final double key, final boolean strict) {
        final int index = ArrayIndex.getArrayIndex(key);
        return checkDeleteIndex(index, strict)? false : super.delete(key, strict);
    }

    @Override
    public boolean delete(final Object key, final boolean strict) {
        final Object primitiveKey = JSType.toPrimitive(key, String.class);
        final int index = ArrayIndex.getArrayIndex(primitiveKey);
        return checkDeleteIndex(index, strict)? false : super.delete(primitiveKey, strict);
    }

    private boolean checkDeleteIndex(final int index, final boolean strict) {
        if (isValid(index)) {
            if (strict) {
                throw typeError("cant.delete.property", Integer.toString(index), ScriptRuntime.safeToString(this));
            }
            return true;
        }

        return false;
    }

    @Override
    public Object getOwnPropertyDescriptor(final String key) {
        final int index = ArrayIndex.getArrayIndex(key);
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
    public static String fromCharCode(final Object self, final Object... args) {
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
    public static String fromCharCode(final Object self, final Object value) {
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
    public static String fromCharCode(final Object self, final int value) {
        return "" + (char)(value & 0xffff);
    }

    /**
     * ECMA 15.5.3.2 - specialization for one char of long type
     * @param self  self reference
     * @param value one argument to be interpreted as char
     * @return string with one charcode
     */
    @SpecializedFunction
    public static String fromCharCode(final Object self, final long value) {
        return "" + (char)((int)value & 0xffff);
    }

    /**
     * ECMA 15.5.3.2 - specialization for one char of double type
     * @param self  self reference
     * @param value one argument to be interpreted as char
     * @return string with one charcode
     */
    @SpecializedFunction
    public static String fromCharCode(final Object self, final double value) {
        return "" + (char)JSType.toUint16(value);
    }

    /**
     * ECMA 15.5.4.2 String.prototype.toString ( )
     * @param self self reference
     * @return self as string
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static String toString(final Object self) {
        return getString(self);
    }

    /**
     * ECMA 15.5.4.3 String.prototype.valueOf ( )
     * @param self self reference
     * @return self as string
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static String valueOf(final Object self) {
        return getString(self);
    }

    /**
     * ECMA 15.5.4.4 String.prototype.charAt (pos)
     * @param self self reference
     * @param pos  position in string
     * @return string representing the char at the given position
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static String charAt(final Object self, final Object pos) {
        return charAtImpl(checkObjectToString(self), JSType.toInteger(pos));
    }

    /**
     * ECMA 15.5.4.4 String.prototype.charAt (pos) - specialized version for double position
     * @param self self reference
     * @param pos  position in string
     * @return string representing the char at the given position
     */
    @SpecializedFunction
    public static String charAt(final Object self, final double pos) {
        return charAt(self, (int)pos);
    }

    /**
     * ECMA 15.5.4.4 String.prototype.charAt (pos) - specialized version for int position
     * @param self self reference
     * @param pos  position in string
     * @return string representing the char at the given position
     */
    @SpecializedFunction
    public static String charAt(final Object self, final int pos) {
        return charAtImpl(checkObjectToString(self), pos);
    }

    private static String charAtImpl(final String str, final int pos) {
        return (pos < 0 || pos >= str.length()) ? "" : String.valueOf(str.charAt(pos));
    }

    /**
     * ECMA 15.5.4.5 String.prototype.charCodeAt (pos)
     * @param self self reference
     * @param pos  position in string
     * @return number representing charcode at position
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static double charCodeAt(final Object self, final Object pos) {
        return charCodeAtImpl(checkObjectToString(self), JSType.toInteger(pos));
    }

    /**
     * ECMA 15.5.4.5 String.prototype.charCodeAt (pos) - specialized version for double position
     * @param self self reference
     * @param pos  position in string
     * @return number representing charcode at position
     */
    @SpecializedFunction
    public static double charCodeAt(final Object self, final double pos) {
        return charCodeAt(self, (int) pos);
    }

    /**
     * ECMA 15.5.4.5 String.prototype.charCodeAt (pos) - specialized version for int position
     * @param self self reference
     * @param pos  position in string
     * @return number representing charcode at position
     */
    @SpecializedFunction
    public static double charCodeAt(final Object self, final int pos) {
        return charCodeAtImpl(checkObjectToString(self), pos);
    }

    private static double charCodeAtImpl(final String str, final int pos) {
        return (pos < 0 || pos >= str.length()) ? Double.NaN :  str.charAt(pos);
    }

    /**
     * ECMA 15.5.4.6 String.prototype.concat ( [ string1 [ , string2 [ , ... ] ] ] )
     * @param self self reference
     * @param args list of string to concatenate
     * @return concatenated string
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 1)
    public static Object concat(final Object self, final Object... args) {
        CharSequence cs = checkObjectToString(self);
        if (args != null) {
            for (final Object obj : args) {
                cs = new ConsString(cs, JSType.toCharSequence(obj));
            }
        }
        return cs;
    }

    /**
     * ECMA 15.5.4.7 String.prototype.indexOf (searchString, position)
     * @param self   self reference
     * @param search string to search for
     * @param pos    position to start search
     * @return position of first match or -1
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 1)
    public static int indexOf(final Object self, final Object search, final Object pos) {
        final String str = checkObjectToString(self);
        return str.indexOf(JSType.toString(search), JSType.toInteger(pos));
    }

    /**
     * ECMA 15.5.4.7 String.prototype.indexOf (searchString, position) specialized for no position parameter
     * @param self   self reference
     * @param search string to search for
     * @return position of first match or -1
     */
    @SpecializedFunction
    public static int indexOf(final Object self, final Object search) {
        return indexOf(self, search, 0);
    }

    /**
     * ECMA 15.5.4.7 String.prototype.indexOf (searchString, position) specialized for double position parameter
     * @param self   self reference
     * @param search string to search for
     * @param pos    position to start search
     * @return position of first match or -1
     */
    @SpecializedFunction
    public static int indexOf(final Object self, final Object search, final double pos) {
        return indexOf(self, search, (int) pos);
    }

    /**
     * ECMA 15.5.4.7 String.prototype.indexOf (searchString, position) specialized for int position parameter
     * @param self   self reference
     * @param search string to search for
     * @param pos    position to start search
     * @return position of first match or -1
     */
    @SpecializedFunction
    public static int indexOf(final Object self, final Object search, final int pos) {
        return checkObjectToString(self).indexOf(JSType.toString(search), pos);
    }

    /**
     * ECMA 15.5.4.8 String.prototype.lastIndexOf (searchString, position)
     * @param self   self reference
     * @param search string to search for
     * @param pos    position to start search
     * @return last position of match or -1
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 1)
    public static int lastIndexOf(final Object self, final Object search, final Object pos) {

        final String str       = checkObjectToString(self);
        final String searchStr = JSType.toString(search);
        final int length       = str.length();

        int end;

        if (pos == UNDEFINED) {
            end = length;
        } else {
            final double numPos = JSType.toNumber(pos);
            end = Double.isNaN(numPos) ? length : (int)numPos;
            if (end < 0) {
                end = 0;
            } else if (end > length) {
                end = length;
            }
        }


        return str.lastIndexOf(searchStr, end);
    }

    /**
     * ECMA 15.5.4.9 String.prototype.localeCompare (that)
     * @param self self reference
     * @param that comparison object
     * @return result of locale sensitive comparison operation between {@code self} and {@code that}
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static double localeCompare(final Object self, final Object that) {

        final String   str      = checkObjectToString(self);
        final Collator collator = Collator.getInstance(Global.getEnv()._locale);

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
    public static ScriptObject match(final Object self, final Object regexp) {

        final String str = checkObjectToString(self);

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
    public static String replace(final Object self, final Object string, final Object replacement) {

        final String str = checkObjectToString(self);

        final NativeRegExp nativeRegExp;
        if (string instanceof NativeRegExp) {
            nativeRegExp = (NativeRegExp) string;
        } else {
            nativeRegExp = NativeRegExp.flatRegExp(JSType.toString(string));
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
    public static int search(final Object self, final Object string) {

        final String       str          = checkObjectToString(self);
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
    public static String slice(final Object self, final Object start, final Object end) {

        final String str      = checkObjectToString(self);
        if (end == UNDEFINED) {
            return slice(str, JSType.toInteger(start));
        }
        return slice(str, JSType.toInteger(start), JSType.toInteger(end));
    }

    /**
     * ECMA 15.5.4.13 String.prototype.slice (start, end) specialized for single int parameter
     *
     * @param self  self reference
     * @param start start position for slice
     * @return sliced out substring
     */
    @SpecializedFunction
    public static String slice(final Object self, final int start) {
        final String str = checkObjectToString(self);
        final int from = (start < 0) ? Math.max(str.length() + start, 0) : Math.min(start, str.length());

        return str.substring(from);
    }

    /**
     * ECMA 15.5.4.13 String.prototype.slice (start, end) specialized for single double parameter
     *
     * @param self  self reference
     * @param start start position for slice
     * @return sliced out substring
     */
    @SpecializedFunction
    public static String slice(final Object self, final double start) {
        return slice(self, (int)start);
    }

    /**
     * ECMA 15.5.4.13 String.prototype.slice (start, end) specialized for two int parameters
     *
     * @param self  self reference
     * @param start start position for slice
     * @param end   end position for slice
     * @return sliced out substring
     */
    @SpecializedFunction
    public static String slice(final Object self, final int start, final int end) {

        final String str = checkObjectToString(self);
        final int len    = str.length();

        final int from = (start < 0) ? Math.max(len + start, 0) : Math.min(start, len);
        final int to   = (end < 0)   ? Math.max(len + end, 0)   : Math.min(end, len);

        return str.substring(Math.min(from, to), to);
    }

    /**
     * ECMA 15.5.4.13 String.prototype.slice (start, end) specialized for two double parameters
     *
     * @param self  self reference
     * @param start start position for slice
     * @param end   end position for slice
     * @return sliced out substring
     */
    @SpecializedFunction
    public static String slice(final Object self, final double start, final double end) {
        return slice(self, (int)start, (int)end);
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
    public static ScriptObject split(final Object self, final Object separator, final Object limit) {
        final String str = checkObjectToString(self);
        final long lim = (limit == UNDEFINED) ? JSType.MAX_UINT : JSType.toUint32(limit);

        if (separator == UNDEFINED) {
            return lim == 0 ? new NativeArray() : new NativeArray(new Object[]{str});
        }

        if (separator instanceof NativeRegExp) {
            return ((NativeRegExp) separator).split(str, lim);
        }

        // when separator is a string, it is treated as a literal search string to be used for splitting.
        return splitString(str, JSType.toString(separator), lim);
    }

    private static ScriptObject splitString(String str, String separator, long limit) {
        if (separator.isEmpty()) {
            final int length = (int) Math.min(str.length(), limit);
            final Object[] array = new Object[length];
            for (int i = 0; i < length; i++) {
                array[i] = String.valueOf(str.charAt(i));
            }
            return new NativeArray(array);
        }

        final List<String> elements = new LinkedList<>();
        final int strLength = str.length();
        final int sepLength = separator.length();
        int pos = 0;
        int n = 0;

        while (pos < strLength && n < limit) {
            int found = str.indexOf(separator, pos);
            if (found == -1) {
                break;
            }
            elements.add(str.substring(pos, found));
            n++;
            pos = found + sepLength;
        }
        if (pos <= strLength && n < limit) {
            elements.add(str.substring(pos));
        }

        return new NativeArray(elements.toArray());
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
    public static String substr(final Object self, final Object start, final Object length) {
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
    public static String substring(final Object self, final Object start, final Object end) {

        final String str = checkObjectToString(self);
        if (end == UNDEFINED) {
            return substring(str, JSType.toInteger(start));
        }
        return substring(str, JSType.toInteger(start), JSType.toInteger(end));
    }

    /**
     * ECMA 15.5.4.15 String.prototype.substring (start, end) specialized for int start parameter
     *
     * @param self  self reference
     * @param start start position of substring
     * @return substring given start and end indexes
     */
    @SpecializedFunction
    public static String substring(final Object self, final int start) {
        final String str = checkObjectToString(self);
        if (start < 0) {
            return str;
        } else if (start >= str.length()) {
            return "";
        } else {
            return str.substring(start);
        }
    }

    /**
     * ECMA 15.5.4.15 String.prototype.substring (start, end) specialized for double start parameter
     *
     * @param self  self reference
     * @param start start position of substring
     * @return substring given start and end indexes
     */
    @SpecializedFunction
    public static String substring(final Object self, final double start) {
        return substring(self, (int)start);
    }

    /**
     * ECMA 15.5.4.15 String.prototype.substring (start, end) specialized for int start and end parameters
     *
     * @param self  self reference
     * @param start start position of substring
     * @param end   end position of substring
     * @return substring given start and end indexes
     */
    @SpecializedFunction
    public static String substring(final Object self, final int start, final int end) {
        final String str = checkObjectToString(self);
        final int len = str.length();
        final int validStart = start < 0 ? 0 : (start > len ? len : start);
        final int validEnd   = end < 0 ? 0 : (end > len ? len : end);

        if (validStart < validEnd) {
            return str.substring(validStart, validEnd);
        }
        return str.substring(validEnd, validStart);
    }

    /**
     * ECMA 15.5.4.15 String.prototype.substring (start, end) specialized for double start and end parameters
     *
     * @param self  self reference
     * @param start start position of substring
     * @param end   end position of substring
     * @return substring given start and end indexes
     */
    @SpecializedFunction
    public static String substring(final Object self, final double start, final double end) {
        return substring(self, (int)start, (int)end);
    }

    /**
     * ECMA 15.5.4.16 String.prototype.toLowerCase ( )
     * @param self self reference
     * @return string to lower case
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static String toLowerCase(final Object self) {
        return checkObjectToString(self).toLowerCase(Locale.ROOT);
    }

    /**
     * ECMA 15.5.4.17 String.prototype.toLocaleLowerCase ( )
     * @param self self reference
     * @return string to locale sensitive lower case
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static String toLocaleLowerCase(final Object self) {
        return checkObjectToString(self).toLowerCase(Global.getEnv()._locale);
    }

    /**
     * ECMA 15.5.4.18 String.prototype.toUpperCase ( )
     * @param self self reference
     * @return string to upper case
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static String toUpperCase(final Object self) {
        return checkObjectToString(self).toUpperCase(Locale.ROOT);
    }

    /**
     * ECMA 15.5.4.19 String.prototype.toLocaleUpperCase ( )
     * @param self self reference
     * @return string to locale sensitive upper case
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static String toLocaleUpperCase(final Object self) {
        return checkObjectToString(self).toUpperCase(Global.getEnv()._locale);
    }

    /**
     * ECMA 15.5.4.20 String.prototype.trim ( )
     * @param self self reference
     * @return string trimmed from whitespace
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static String trim(final Object self) {

        final String str = checkObjectToString(self);
        int start = 0;
        int end   = str.length() - 1;

        while (start <= end && ScriptRuntime.isJSWhitespace(str.charAt(start))) {
            start++;
        }
        while (end > start && ScriptRuntime.isJSWhitespace(str.charAt(end))) {
            end--;
        }

        return str.substring(start, end + 1);
    }

    /**
     * Nashorn extension: String.prototype.trimLeft ( )
     * @param self self reference
     * @return string trimmed left from whitespace
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static String trimLeft(final Object self) {

        final String str = checkObjectToString(self);
        int start = 0;
        int end   = str.length() - 1;

        while (start <= end && ScriptRuntime.isJSWhitespace(str.charAt(start))) {
            start++;
        }

        return str.substring(start, end + 1);
    }

    /**
     * Nashorn extension: String.prototype.trimRight ( )
     * @param self self reference
     * @return string trimmed right from whitespace
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static String trimRight(final Object self) {

        final String str = checkObjectToString(self);
        int start = 0;
        int end   = str.length() - 1;

        while (end >= start && ScriptRuntime.isJSWhitespace(str.charAt(end))) {
            end--;
        }

        return str.substring(start, end + 1);
    }

    private static ScriptObject newObj(final Object self, final CharSequence str) {
        return new NativeString(str);
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

    /**
     * ECMA 15.5.2.1 new String ( [ value ] ) - special version with one arg
     *
     * Constructor
     *
     * @param newObj is this constructor invoked with the new operator
     * @param self   self reference
     * @param arg    argument
     *
     * @return new NativeString (arg)
     */
    @SpecializedConstructor
    public static Object constructor(final boolean newObj, final Object self, final Object arg) {
        final CharSequence str = JSType.toCharSequence(arg);
        return newObj ? newObj(self, str) : str.toString();
    }

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
        final String str = JSType.toString(arg);
        return newObj ? newObj(self, str) : str;
    }

    /**
     * Lookup the appropriate method for an invoke dynamic call.
     *
     * @param request  the link request
     * @param receiver receiver of call
     * @return Link to be invoked at call site.
     */
    public static GuardedInvocation lookupPrimitive(final LinkRequest request, final Object receiver) {
        final MethodHandle guard = NashornGuards.getInstanceOf2Guard(String.class, ConsString.class);
        return PrimitiveLookup.lookupPrimitive(request, guard, new NativeString((CharSequence)receiver), WRAPFILTER, PROTOFILTER);
    }

    @SuppressWarnings("unused")
    private static NativeString wrapFilter(final Object receiver) {
        return new NativeString((CharSequence)receiver);
    }

    @SuppressWarnings("unused")
    private static Object protoFilter(final Object object) {
        return Global.instance().getStringPrototype();
    }

    private static CharSequence getCharSequence(final Object self) {
        if (self instanceof String || self instanceof ConsString) {
            return (CharSequence)self;
        } else if (self instanceof NativeString) {
            return ((NativeString)self).getValue();
        } else if (self != null && self == Global.instance().getStringPrototype()) {
            return "";
        } else {
            throw typeError("not.a.string", ScriptRuntime.safeToString(self));
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
            throw typeError( "not.a.string", ScriptRuntime.safeToString(self));
        }
    }

    /**
     * Combines ECMA 9.10 CheckObjectCoercible and ECMA 9.8 ToString with a shortcut for strings.
     *
     * @param self the object
     * @return the object as string
     */
    private static String checkObjectToString(final Object self) {
        if (self instanceof String) {
            return (String)self;
        } else if (self instanceof ConsString) {
            return self.toString();
        } else {
            Global.checkObjectCoercible(self);
            return JSType.toString(self);
        }
    }

    private boolean isValid(final int key) {
        return key >= 0 && key < value.length();
    }

    private static MethodHandle findOwnMH(final String name, final MethodType type) {
        return MH.findStatic(MethodHandles.lookup(), NativeString.class, name, type);
    }
}
