/*
 * Copyright 1999-2006 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package com.sun.tools.javac.util;

import java.lang.ref.SoftReference;


/** An abstraction for internal compiler strings. For efficiency reasons,
 *  GJC uses hashed strings that are stored in a common large buffer.
 *
 *  <p>Names represent unique hashable strings. Two names are equal
 *  if their indices are equal. Utf8 representation is used
 *  for storing names internally.
 *
 *  <p><b>This is NOT part of any API supported by Sun Microsystems.  If
 *  you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public class Name implements javax.lang.model.element.Name {

    /** The table structure where the name is stored
     */
    public Table table;

    /** The index where the bytes of this name are stored in the global name
     *  buffer `names'.
     */
    public int index;

    /** The number of bytes in this name.
     */
    public int len;

    /** The next name occupying the same hash bucket.
     */
    Name next;

    /** The hashcode of a name.
     */
    private static int hashValue(byte cs[], int start, int len) {
        int h = 0;
        int off = start;

        for (int i = 0; i < len; i++) {
            h = (h << 5) - h + cs[off++];
        }
        return h;
    }

    /** Is (the utf8 representation of) name equal to
     *  cs[start..start+len-1]?
     */
    private static boolean equals(byte[] names, int index,
                                  byte cs[], int start, int len) {
        int i = 0;
        while (i < len && names[index + i] == cs[start + i]) i++;
        return i == len;
    }

    /** Create a name from the bytes in cs[start..start+len-1].
     *  Assume that bytes are in utf8 format.
     */
    public static Name fromUtf(Table table, byte cs[], int start, int len) {
        int h = hashValue(cs, start, len) & table.hashMask;
        Name n = table.hashes[h];
        byte[] names = table.names;
        while (n != null &&
               (n.len != len || !equals(names, n.index, cs, start, len)))
            n = n.next;
        if (n == null) {
            int nc = table.nc;
            while (nc + len > names.length) {
//              System.err.println("doubling name buffer of length + " + names.length + " to fit " + len + " bytes");//DEBUG
                byte[] newnames = new byte[names.length * 2];
                System.arraycopy(names, 0, newnames, 0, names.length);
                names = table.names = newnames;
            }
            System.arraycopy(cs, start, names, nc, len);
            n = new Name();
            n.table = table;
            n.index = nc;
            n.len = len;
            n.next = table.hashes[h];
            table.hashes[h] = n;
            table.nc = nc + len;
            if (len == 0) table.nc++;
        }
        return n;
    }

    /** Create a name from the bytes in array cs.
     *  Assume that bytes are in utf8 format.
     */
    public static Name fromUtf(Table table, byte cs[]) {
        return fromUtf(table, cs, 0, cs.length);
    }

    /** Create a name from the characters in cs[start..start+len-1].
     */
    public static Name fromChars(Table table, char[] cs, int start, int len) {
        int nc = table.nc;
        byte[] names = table.names;
        while (nc + len * 3 >= names.length) {
//          System.err.println("doubling name buffer of length " + names.length + " to fit " + len + " chars");//DEBUG
            byte[] newnames = new byte[names.length * 2];
            System.arraycopy(names, 0, newnames, 0, names.length);
            names = table.names = newnames;
        }
        int nbytes =
            Convert.chars2utf(cs, start, names, nc, len) - nc;
        int h = hashValue(names, nc, nbytes) & table.hashMask;
        Name n = table.hashes[h];
        while (n != null &&
               (n.len != nbytes ||
                !equals(names, n.index, names, nc, nbytes)))
            n = n.next;
        if (n == null) {
            n = new Name();
            n.table = table;
            n.index = nc;
            n.len = nbytes;
            n.next = table.hashes[h];
            table.hashes[h] = n;
            table.nc = nc + nbytes;
            if (nbytes == 0) table.nc++;
        }
        return n;
    }

    /** Create a name from the characters in string s.
     */
    public static Name fromString(Table table, String s) {
        char[] cs = s.toCharArray();
        return fromChars(table, cs, 0, cs.length);
    }

    /** Create a name from the characters in char sequence s.
     */
    public static Name fromString(Table table, CharSequence s) {
        return fromString(table, s.toString());
    }

    /** Return the Utf8 representation of this name.
     */
    public byte[] toUtf() {
        byte[] bs = new byte[len];
        System.arraycopy(table.names, index, bs, 0, len);
        return bs;
    }

    /** Return the string representation of this name.
     */
    public String toString() {
        return Convert.utf2string(table.names, index, len);
    }

    /** Copy all bytes of this name to buffer cs, starting at start.
     */
    public void getBytes(byte cs[], int start) {
        System.arraycopy(table.names, index, cs, start, len);
    }

    /** Return the hash value of this name.
     */
    public int hashCode() {
        return index;
    }

    /** Is this name equal to other?
     */
    public boolean equals(Object other) {
        if (other instanceof Name)
            return
                table == ((Name)other).table && index == ((Name)other).index;
        else return false;
    }

    /** Compare this name to other name, yielding -1 if smaller, 0 if equal,
     *  1 if greater.
     */
    public boolean less(Name that) {
        int i = 0;
        while (i < this.len && i < that.len) {
            byte thisb = this.table.names[this.index + i];
            byte thatb = that.table.names[that.index + i];
            if (thisb < thatb) return true;
            else if (thisb > thatb) return false;
            else i++;
        }
        return this.len < that.len;
    }

    /** Returns the length of this name.
     */
    public int length() {
        return toString().length();
    }

    /** Returns i'th byte of this name.
     */
    public byte byteAt(int i) {
        return table.names[index + i];
    }

    /** Returns first occurrence of byte b in this name, len if not found.
     */
    public int indexOf(byte b) {
        byte[] names = table.names;
        int i = 0;
        while (i < len && names[index + i] != b) i++;
        return i;
    }

    /** Returns last occurrence of byte b in this name, -1 if not found.
     */
    public int lastIndexOf(byte b) {
        byte[] names = table.names;
        int i = len - 1;
        while (i >= 0 && names[index + i] != b) i--;
        return i;
    }

    /** Does this name start with prefix?
     */
    public boolean startsWith(Name prefix) {
        int i = 0;
        while (i < prefix.len &&
               i < len &&
               table.names[index + i] == prefix.table.names[prefix.index + i])
            i++;
        return i == prefix.len;
    }

    /** Does this name end with suffix?
     */
    public boolean endsWith(Name suffix) {
        int i = len - 1;
        int j = suffix.len - 1;
        while (j >= 0 && i >= 0 &&
               table.names[index + i] == suffix.table.names[suffix.index + j]) {
            i--; j--;
        }
        return j < 0;
    }

    /** Returns the sub-name starting at position start, up to and
     *  excluding position end.
     */
    public Name subName(int start, int end) {
        if (end < start) end = start;
        return fromUtf(table, table.names, index + start, end - start);
    }

    /** Replace all `from' bytes in this name with `to' bytes.
     */
    public Name replace(byte from, byte to) {
        byte[] names = table.names;
        int i = 0;
        while (i < len) {
            if (names[index + i] == from) {
                byte[] bs = new byte[len];
                System.arraycopy(names, index, bs, 0, i);
                bs[i] = to;
                i++;
                while (i < len) {
                    byte b = names[index + i];
                    bs[i] = b == from ? to : b;
                    i++;
                }
                return fromUtf(table, bs, 0, len);
            }
            i++;
        }
        return this;
    }

    /** Return the concatenation of this name and name `n'.
     */
    public Name append(Name n) {
        byte[] bs = new byte[len + n.len];
        getBytes(bs, 0);
        n.getBytes(bs, len);
        return fromUtf(table, bs, 0, bs.length);
    }

    /** Return the concatenation of this name, the given ASCII
     *  character, and name `n'.
     */
    public Name append(char c, Name n) {
        byte[] bs = new byte[len + n.len + 1];
        getBytes(bs, 0);
        bs[len] = (byte)c;
        n.getBytes(bs, len+1);
        return fromUtf(table, bs, 0, bs.length);
    }

    /** An arbitrary but consistent complete order among all Names.
     */
    public int compareTo(Name other) {
        return other.index - this.index;
    }

    /** Return the concatenation of all names in the array `ns'.
     */
    public static Name concat(Table table, Name ns[]) {
        int len = 0;
        for (int i = 0; i < ns.length; i++)
            len = len + ns[i].len;
        byte[] bs = new byte[len];
        len = 0;
        for (int i = 0; i < ns.length; i++) {
            ns[i].getBytes(bs, len);
            len = len + ns[i].len;
        }
        return fromUtf(table, bs, 0, len);
    }

    public char charAt(int index) {
        return toString().charAt(index);
    }

    public CharSequence subSequence(int start, int end) {
        return toString().subSequence(start, end);
    }

    public boolean contentEquals(CharSequence cs) {
        return this.toString().equals(cs.toString());
    }

    public static class Table {
        // maintain a freelist of recently used name tables for reuse.
        private static List<SoftReference<Table>> freelist = List.nil();

        static private synchronized Table make() {
            while (freelist.nonEmpty()) {
                Table t = freelist.head.get();
                freelist = freelist.tail;
                if (t != null) return t;
            }
            return new Table();
        }

        static private synchronized void dispose(Table t) {
            freelist = freelist.prepend(new SoftReference<Table>(t));
        }

        public void dispose() {
            dispose(this);
        }

        public static final Context.Key<Table> namesKey =
            new Context.Key<Table>();

        public static Table instance(Context context) {
            Table instance = context.get(namesKey);
            if (instance == null) {
                instance = make();
                context.put(namesKey, instance);
            }
            return instance;
        }

        /** The hash table for names.
         */
        private Name[] hashes;

        /** The array holding all encountered names.
         */
        public byte[] names;

        /** The mask to be used for hashing
         */
        private int hashMask;

        /** The number of filled bytes in `names'.
         */
        private int nc = 0;

        /** Allocator
         *  @param hashSize the (constant) size to be used for the hash table
         *                  needs to be a power of two.
         *  @param nameSize the initial size of the name table.
         */
        public Table(int hashSize, int nameSize) {
            hashMask = hashSize - 1;
            hashes = new Name[hashSize];
            names = new byte[nameSize];

            slash = fromString("/");
            hyphen = fromString("-");
            T = fromString("T");
            slashequals = fromString("/=");
            deprecated = fromString("deprecated");

            init = fromString("<init>");
            clinit = fromString("<clinit>");
            error = fromString("<error>");
            any = fromString("<any>");
            empty = fromString("");
            one = fromString("1");
            period = fromString(".");
            comma = fromString(",");
            semicolon = fromString(";");
            asterisk = fromString("*");
            _this = fromString("this");
            _super = fromString("super");
            _default = fromString("default");

            _class = fromString("class");
            java_lang = fromString("java.lang");
            java_lang_Object = fromString("java.lang.Object");
            java_lang_Class = fromString("java.lang.Class");
            java_lang_Cloneable = fromString("java.lang.Cloneable");
            java_io_Serializable = fromString("java.io.Serializable");
            java_lang_Enum = fromString("java.lang.Enum");
            package_info = fromString("package-info");
            serialVersionUID = fromString("serialVersionUID");
            ConstantValue = fromString("ConstantValue");
            LineNumberTable = fromString("LineNumberTable");
            LocalVariableTable = fromString("LocalVariableTable");
            LocalVariableTypeTable = fromString("LocalVariableTypeTable");
            CharacterRangeTable = fromString("CharacterRangeTable");
            StackMap = fromString("StackMap");
            StackMapTable = fromString("StackMapTable");
            SourceID = fromString("SourceID");
            CompilationID = fromString("CompilationID");
            Code = fromString("Code");
            Exceptions = fromString("Exceptions");
            SourceFile = fromString("SourceFile");
            InnerClasses = fromString("InnerClasses");
            Synthetic = fromString("Synthetic");
            Bridge= fromString("Bridge");
            Deprecated = fromString("Deprecated");
            Enum = fromString("Enum");
            _name = fromString("name");
            Signature = fromString("Signature");
            Varargs = fromString("Varargs");
            Annotation = fromString("Annotation");
            RuntimeVisibleAnnotations = fromString("RuntimeVisibleAnnotations");
            RuntimeInvisibleAnnotations = fromString("RuntimeInvisibleAnnotations");
            RuntimeVisibleParameterAnnotations = fromString("RuntimeVisibleParameterAnnotations");
            RuntimeInvisibleParameterAnnotations = fromString("RuntimeInvisibleParameterAnnotations");
            Value = fromString("Value");
            EnclosingMethod = fromString("EnclosingMethod");

            desiredAssertionStatus = fromString("desiredAssertionStatus");

            append  = fromString("append");
            family  = fromString("family");
            forName = fromString("forName");
            toString = fromString("toString");
            length = fromString("length");
            valueOf = fromString("valueOf");
            value = fromString("value");
            getMessage = fromString("getMessage");
            getClass = fromString("getClass");

            TYPE = fromString("TYPE");
            FIELD = fromString("FIELD");
            METHOD = fromString("METHOD");
            PARAMETER = fromString("PARAMETER");
            CONSTRUCTOR = fromString("CONSTRUCTOR");
            LOCAL_VARIABLE = fromString("LOCAL_VARIABLE");
            ANNOTATION_TYPE = fromString("ANNOTATION_TYPE");
            PACKAGE = fromString("PACKAGE");

            SOURCE = fromString("SOURCE");
            CLASS = fromString("CLASS");
            RUNTIME = fromString("RUNTIME");

            Array = fromString("Array");
            Method = fromString("Method");
            Bound = fromString("Bound");
            clone = fromString("clone");
            getComponentType = fromString("getComponentType");
            getClassLoader = fromString("getClassLoader");
            initCause = fromString("initCause");
            values = fromString("values");
            iterator = fromString("iterator");
            hasNext = fromString("hasNext");
            next = fromString("next");
            AnnotationDefault = fromString("AnnotationDefault");
            ordinal = fromString("ordinal");
            equals = fromString("equals");
            hashCode = fromString("hashCode");
            compareTo = fromString("compareTo");
            getDeclaringClass = fromString("getDeclaringClass");
            ex = fromString("ex");
            finalize = fromString("finalize");
        }

        public Table() {
            this(0x8000, 0x20000);
        }

        /** Create a name from the bytes in cs[start..start+len-1].
         *  Assume that bytes are in utf8 format.
         */
        public Name fromUtf(byte cs[], int start, int len) {
            return Name.fromUtf(this, cs, start, len);
        }

        /** Create a name from the bytes in array cs.
         *  Assume that bytes are in utf8 format.
         */
        public Name fromUtf(byte cs[]) {
            return Name.fromUtf(this, cs, 0, cs.length);
        }

        /** Create a name from the characters in cs[start..start+len-1].
         */
        public Name fromChars(char[] cs, int start, int len) {
            return Name.fromChars(this, cs, start, len);
        }

        /** Create a name from the characters in string s.
         */
        public Name fromString(CharSequence s) {
            return Name.fromString(this, s);
        }

        public final Name slash;
        public final Name hyphen;
        public final Name T;
        public final Name slashequals;
        public final Name deprecated;

        public final Name init;
        public final Name clinit;
        public final Name error;
        public final Name any;
        public final Name empty;
        public final Name one;
        public final Name period;
        public final Name comma;
        public final Name semicolon;
        public final Name asterisk;
        public final Name _this;
        public final Name _super;
        public final Name _default;

        public final Name _class;
        public final Name java_lang;
        public final Name java_lang_Object;
        public final Name java_lang_Class;
        public final Name java_lang_Cloneable;
        public final Name java_io_Serializable;
        public final Name serialVersionUID;
        public final Name java_lang_Enum;
        public final Name package_info;
        public final Name ConstantValue;
        public final Name LineNumberTable;
        public final Name LocalVariableTable;
        public final Name LocalVariableTypeTable;
        public final Name CharacterRangeTable;
        public final Name StackMap;
        public final Name StackMapTable;
        public final Name SourceID;
        public final Name CompilationID;
        public final Name Code;
        public final Name Exceptions;
        public final Name SourceFile;
        public final Name InnerClasses;
        public final Name Synthetic;
        public final Name Bridge;
        public final Name Deprecated;
        public final Name Enum;
        public final Name _name;
        public final Name Signature;
        public final Name Varargs;
        public final Name Annotation;
        public final Name RuntimeVisibleAnnotations;
        public final Name RuntimeInvisibleAnnotations;
        public final Name RuntimeVisibleParameterAnnotations;
        public final Name RuntimeInvisibleParameterAnnotations;

        public final Name Value;
        public final Name EnclosingMethod;

        public final Name desiredAssertionStatus;

        public final Name append;
        public final Name family;
        public final Name forName;
        public final Name toString;
        public final Name length;
        public final Name valueOf;
        public final Name value;
        public final Name getMessage;
        public final Name getClass;

        public final Name TYPE;
        public final Name FIELD;
        public final Name METHOD;
        public final Name PARAMETER;
        public final Name CONSTRUCTOR;
        public final Name LOCAL_VARIABLE;
        public final Name ANNOTATION_TYPE;
        public final Name PACKAGE;

        public final Name SOURCE;
        public final Name CLASS;
        public final Name RUNTIME;

        public final Name Array;
        public final Name Method;
        public final Name Bound;
        public final Name clone;
        public final Name getComponentType;
        public final Name getClassLoader;
        public final Name initCause;
        public final Name values;
        public final Name iterator;
        public final Name hasNext;
        public final Name next;
        public final Name AnnotationDefault;
        public final Name ordinal;
        public final Name equals;
        public final Name hashCode;
        public final Name compareTo;
        public final Name getDeclaringClass;
        public final Name ex;
        public final Name finalize;
    }

    public boolean isEmpty() {
        return len == 0;
    }
}
