/*
 * Copyright (c) 2001, 2010, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.java.util.jar.pack;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import static com.sun.java.util.jar.pack.Constants.*;

/**
 * Representation of constant pool entries and indexes.
 * @author John Rose
 */
abstract
class ConstantPool {
    private ConstantPool() {}  // do not instantiate

    static int verbose() {
        return Utils.currentPropMap().getInteger(Utils.DEBUG_VERBOSE);
    }

    /** Factory for Utf8 string constants.
     *  Used for well-known strings like "SourceFile", "<init>", etc.
     *  Also used to back up more complex constant pool entries, like Class.
     */
    public static synchronized Utf8Entry getUtf8Entry(String value) {
        Map<String, Utf8Entry> utf8Entries  = Utils.getUtf8Entries();
        Utf8Entry e = utf8Entries.get(value);
        if (e == null) {
            e = new Utf8Entry(value);
            utf8Entries.put(e.stringValue(), e);
        }
        return e;
    }
    /** Factory for Class constants. */
    public static synchronized ClassEntry getClassEntry(String name) {
        Map<String, ClassEntry> classEntries = Utils.getClassEntries();
        ClassEntry e = classEntries.get(name);
        if (e == null) {
            e = new ClassEntry(getUtf8Entry(name));
            assert(name.equals(e.stringValue()));
            classEntries.put(e.stringValue(), e);
        }
        return e;
    }
    /** Factory for literal constants (String, Integer, etc.). */
    public static synchronized LiteralEntry getLiteralEntry(Comparable value) {
        Map<Object, LiteralEntry> literalEntries = Utils.getLiteralEntries();
        LiteralEntry e = literalEntries.get(value);
        if (e == null) {
            if (value instanceof String)
                e = new StringEntry(getUtf8Entry((String)value));
            else
                e = new NumberEntry((Number)value);
            literalEntries.put(value, e);
        }
        return e;
    }
    /** Factory for literal constants (String, Integer, etc.). */
    public static synchronized StringEntry getStringEntry(String value) {
        return (StringEntry) getLiteralEntry(value);
    }

    /** Factory for signature (type) constants. */
    public static synchronized SignatureEntry getSignatureEntry(String type) {
        Map<String, SignatureEntry> signatureEntries = Utils.getSignatureEntries();
        SignatureEntry e = signatureEntries.get(type);
        if (e == null) {
            e = new SignatureEntry(type);
            assert(e.stringValue().equals(type));
            signatureEntries.put(type, e);
        }
        return e;
    }
    // Convenience overloading.
    public static SignatureEntry getSignatureEntry(Utf8Entry formRef, ClassEntry[] classRefs) {
        return getSignatureEntry(SignatureEntry.stringValueOf(formRef, classRefs));
    }

    /** Factory for descriptor (name-and-type) constants. */
    public static synchronized DescriptorEntry getDescriptorEntry(Utf8Entry nameRef, SignatureEntry typeRef) {
        Map<String, DescriptorEntry> descriptorEntries = Utils.getDescriptorEntries();
        String key = DescriptorEntry.stringValueOf(nameRef, typeRef);
        DescriptorEntry e = descriptorEntries.get(key);
        if (e == null) {
            e = new DescriptorEntry(nameRef, typeRef);
            assert(e.stringValue().equals(key))
                : (e.stringValue()+" != "+(key));
            descriptorEntries.put(key, e);
        }
        return e;
    }
    // Convenience overloading.
    public static DescriptorEntry getDescriptorEntry(Utf8Entry nameRef, Utf8Entry typeRef) {
        return getDescriptorEntry(nameRef, getSignatureEntry(typeRef.stringValue()));
    }

    /** Factory for member reference constants. */
    public static synchronized MemberEntry getMemberEntry(byte tag, ClassEntry classRef, DescriptorEntry descRef) {
        Map<String, MemberEntry> memberEntries = Utils.getMemberEntries();
        String key = MemberEntry.stringValueOf(tag, classRef, descRef);
        MemberEntry e = memberEntries.get(key);
        if (e == null) {
            e = new MemberEntry(tag, classRef, descRef);
            assert(e.stringValue().equals(key))
                : (e.stringValue()+" != "+(key));
            memberEntries.put(key, e);
        }
        return e;
    }


    /** Entries in the constant pool. */
    public static abstract
    class Entry implements Comparable {
        protected final byte tag;       // a CONSTANT_foo code
        protected int valueHash;        // cached hashCode

        protected Entry(byte tag) {
            this.tag = tag;
        }

        public final byte getTag() {
            return tag;
        }

        public Entry getRef(int i) {
            return null;
        }

        public boolean eq(Entry that) {  // same reference
            assert(that != null);
            return this == that || this.equals(that);
        }

        // Equality of Entries is value-based.
        public abstract boolean equals(Object o);
        public final int hashCode() {
            if (valueHash == 0) {
                valueHash = computeValueHash();
                if (valueHash == 0)  valueHash = 1;
            }
            return valueHash;
        }
        protected abstract int computeValueHash();

        public abstract int compareTo(Object o);

        protected int superCompareTo(Object o) {
            Entry that = (Entry) o;

            if (this.tag != that.tag) {
                return TAG_ORDER[this.tag] - TAG_ORDER[that.tag];
            }

            return 0;  // subclasses must refine this
        }

        public final boolean isDoubleWord() {
            return tag == CONSTANT_Double || tag == CONSTANT_Long;
        }

        public final boolean tagMatches(int tag) {
            return (this.tag == tag);
        }

        public String toString() {
            String valuePrint = stringValue();
            if (verbose() > 4) {
                if (valueHash != 0)
                    valuePrint += " hash="+valueHash;
                valuePrint += " id="+System.identityHashCode(this);
            }
            return tagName(tag)+"="+valuePrint;
        }
        public abstract String stringValue();
    }

    public static
    class Utf8Entry extends Entry {
        final String value;

        Utf8Entry(String value) {
            super(CONSTANT_Utf8);
            this.value = value.intern();
            hashCode();  // force computation of valueHash
        }
        protected int computeValueHash() {
            return value.hashCode();
        }
        public boolean equals(Object o) {
            // Use reference equality of interned strings:
            return (o != null && o.getClass() == Utf8Entry.class
                    && ((Utf8Entry) o).value.equals(value));
        }
        public int compareTo(Object o) {
            int x = superCompareTo(o);
            if (x == 0) {
                x = value.compareTo(((Utf8Entry)o).value);
            }
            return x;
        }
        public String stringValue() {
            return value;
        }
    }

    static boolean isMemberTag(byte tag) {
        switch (tag) {
        case CONSTANT_Fieldref:
        case CONSTANT_Methodref:
        case CONSTANT_InterfaceMethodref:
            return true;
        }
        return false;
    }

    static byte numberTagOf(Number value) {
        if (value instanceof Integer)  return CONSTANT_Integer;
        if (value instanceof Float)    return CONSTANT_Float;
        if (value instanceof Long)     return CONSTANT_Long;
        if (value instanceof Double)   return CONSTANT_Double;
        throw new RuntimeException("bad literal value "+value);
    }

    public static abstract
    class LiteralEntry extends Entry {
        protected LiteralEntry(byte tag) {
            super(tag);
        }

        public abstract Comparable literalValue();
    }

    public static
    class NumberEntry extends LiteralEntry {
        final Number value;
        NumberEntry(Number value) {
            super(numberTagOf(value));
            this.value = value;
            hashCode();  // force computation of valueHash
        }
        protected int computeValueHash() {
            return value.hashCode();
        }

        public boolean equals(Object o) {
            return (o != null && o.getClass() == NumberEntry.class
                    && ((NumberEntry) o).value.equals(value));

        }
        public int compareTo(Object o) {
            int x = superCompareTo(o);
            if (x == 0) {
                x = ((Comparable)value).compareTo(((NumberEntry)o).value);
            }
            return x;
        }
        public Number numberValue() {
            return value;
        }
        public Comparable literalValue() {
            return (Comparable) value;
        }
        public String stringValue() {
            return value.toString();
        }
    }

    public static
    class StringEntry extends LiteralEntry {
        final Utf8Entry ref;
        public Entry getRef(int i) { return i == 0 ? ref : null; }

        StringEntry(Entry ref) {
            super(CONSTANT_String);
            this.ref = (Utf8Entry) ref;
            hashCode();  // force computation of valueHash
        }
        protected int computeValueHash() {
            return ref.hashCode() + tag;
        }
        public boolean equals(Object o) {
            return (o != null && o.getClass() == StringEntry.class &&
                    ((StringEntry)o).ref.eq(ref));
        }
        public int compareTo(Object o) {
            int x = superCompareTo(o);
            if (x == 0) {
                x = ref.compareTo(((StringEntry)o).ref);
            }
            return x;
        }
        public Comparable literalValue() {
            return ref.stringValue();
        }
        public String stringValue() {
            return ref.stringValue();
        }
    }

    public static
    class ClassEntry extends Entry {
        final Utf8Entry ref;
        public Entry getRef(int i) { return i == 0 ? ref : null; }

        protected int computeValueHash() {
            return ref.hashCode() + tag;
        }
        ClassEntry(Entry ref) {
            super(CONSTANT_Class);
            this.ref = (Utf8Entry) ref;
            hashCode();  // force computation of valueHash
        }
        public boolean equals(Object o) {
            return (o != null && o.getClass() == ClassEntry.class
                    && ((ClassEntry) o).ref.eq(ref));
        }
        public int compareTo(Object o) {
            int x = superCompareTo(o);
            if (x == 0) {
                x = ref.compareTo(((ClassEntry)o).ref);
            }
            return x;
        }
        public String stringValue() {
            return ref.stringValue();
        }
    }

    public static
    class DescriptorEntry extends Entry {
        final Utf8Entry      nameRef;
        final SignatureEntry typeRef;
        public Entry getRef(int i) {
            if (i == 0)  return nameRef;
            if (i == 1)  return typeRef;
            return null;
        }
        DescriptorEntry(Entry nameRef, Entry typeRef) {
            super(CONSTANT_NameandType);
            if (typeRef instanceof Utf8Entry) {
                typeRef = getSignatureEntry(typeRef.stringValue());
            }
            this.nameRef = (Utf8Entry) nameRef;
            this.typeRef = (SignatureEntry) typeRef;
            hashCode();  // force computation of valueHash
        }
        protected int computeValueHash() {
            int hc2 = typeRef.hashCode();
            return (nameRef.hashCode() + (hc2 << 8)) ^ hc2;
        }
        public boolean equals(Object o) {
            if (o == null || o.getClass() != DescriptorEntry.class) {
                return false;
            }
            DescriptorEntry that = (DescriptorEntry)o;
            return this.nameRef.eq(that.nameRef)
                && this.typeRef.eq(that.typeRef);
        }
        public int compareTo(Object o) {
            int x = superCompareTo(o);
            if (x == 0) {
                DescriptorEntry that = (DescriptorEntry)o;
                // Primary key is typeRef, not nameRef.
                x = this.typeRef.compareTo(that.typeRef);
                if (x == 0)
                    x = this.nameRef.compareTo(that.nameRef);
            }
            return x;
        }
        public String stringValue() {
            return stringValueOf(nameRef, typeRef);
        }
        static
        String stringValueOf(Entry nameRef, Entry typeRef) {
            return typeRef.stringValue()+","+nameRef.stringValue();
        }

        public String prettyString() {
            return nameRef.stringValue()+typeRef.prettyString();
        }

        public boolean isMethod() {
            return typeRef.isMethod();
        }

        public byte getLiteralTag() {
            return typeRef.getLiteralTag();
        }
    }

    public static
    class MemberEntry extends Entry {
        final ClassEntry classRef;
        final DescriptorEntry descRef;
        public Entry getRef(int i) {
            if (i == 0)  return classRef;
            if (i == 1)  return descRef;
            return null;
        }
        protected int computeValueHash() {
            int hc2 = descRef.hashCode();
            return (classRef.hashCode() + (hc2 << 8)) ^ hc2;
        }

        MemberEntry(byte tag, ClassEntry classRef, DescriptorEntry descRef) {
            super(tag);
            assert(isMemberTag(tag));
            this.classRef = classRef;
            this.descRef  = descRef;
            hashCode();  // force computation of valueHash
        }
        public boolean equals(Object o) {
            if (o == null || o.getClass() != MemberEntry.class) {
                return false;
            }
            MemberEntry that = (MemberEntry)o;
            return this.classRef.eq(that.classRef)
                && this.descRef.eq(that.descRef);
        }
        public int compareTo(Object o) {
            int x = superCompareTo(o);
            if (x == 0) {
                MemberEntry that = (MemberEntry)o;
                // Primary key is classRef.
                x = this.classRef.compareTo(that.classRef);
                if (x == 0)
                    x = this.descRef.compareTo(that.descRef);
            }
            return x;
        }
        public String stringValue() {
            return stringValueOf(tag, classRef, descRef);
        }
        static
        String stringValueOf(byte tag, ClassEntry classRef, DescriptorEntry descRef) {
            assert(isMemberTag(tag));
            String pfx;
            switch (tag) {
            case CONSTANT_Fieldref:            pfx = "Field:";   break;
            case CONSTANT_Methodref:           pfx = "Method:";  break;
            case CONSTANT_InterfaceMethodref:  pfx = "IMethod:"; break;
            default:                           pfx = tag+"???";  break;
            }
            return pfx+classRef.stringValue()+","+descRef.stringValue();
        }

        public boolean isMethod() {
            return descRef.isMethod();
        }
    }

    public static
    class SignatureEntry extends Entry {
        final Utf8Entry    formRef;
        final ClassEntry[] classRefs;
        String             value;
        Utf8Entry          asUtf8Entry;
        public Entry getRef(int i) {
            if (i == 0)  return formRef;
            return i-1 < classRefs.length ? classRefs[i-1] : null;
        }
        SignatureEntry(String value) {
            super(CONSTANT_Signature);
            value = value.intern();  // always do this
            this.value = value;
            String[] parts = structureSignature(value);
            formRef = getUtf8Entry(parts[0]);
            classRefs = new ClassEntry[parts.length-1];
            for (int i = 1; i < parts.length; i++) {
                classRefs[i - 1] = getClassEntry(parts[i]);
            }
            hashCode();  // force computation of valueHash
        }
        protected int computeValueHash() {
            stringValue();  // force computation of value
            return value.hashCode() + tag;
        }

        public Utf8Entry asUtf8Entry() {
            if (asUtf8Entry == null) {
                asUtf8Entry = getUtf8Entry(stringValue());
            }
            return asUtf8Entry;
        }

        public boolean equals(Object o) {
            return (o != null && o.getClass() == SignatureEntry.class &&
                    ((SignatureEntry)o).value.equals(value));
        }
        public int compareTo(Object o) {
            int x = superCompareTo(o);
            if (x == 0) {
                SignatureEntry that = (SignatureEntry)o;
                x = compareSignatures(this.value, that.value);
            }
            return x;
        }
        public String stringValue() {
            if (value == null) {
                value = stringValueOf(formRef, classRefs);
            }
            return value;
        }
        static
        String stringValueOf(Utf8Entry formRef, ClassEntry[] classRefs) {
            String[] parts = new String[1+classRefs.length];
            parts[0] = formRef.stringValue();
            for (int i = 1; i < parts.length; i++) {
                parts[i] = classRefs[i - 1].stringValue();
            }
            return flattenSignature(parts).intern();
        }

        public int computeSize(boolean countDoublesTwice) {
            String form = formRef.stringValue();
            int min = 0;
            int max = 1;
            if (isMethod()) {
                min = 1;
                max = form.indexOf(')');
            }
            int size = 0;
            for (int i = min; i < max; i++) {
                switch (form.charAt(i)) {
                    case 'D':
                    case 'J':
                        if (countDoublesTwice) {
                            size++;
                        }
                        break;
                    case '[':
                        // Skip rest of array info.
                        while (form.charAt(i) == '[') {
                            ++i;
                        }
                        break;
                    case ';':
                        continue;
                    default:
                        assert (0 <= JAVA_SIGNATURE_CHARS.indexOf(form.charAt(i)));
                        break;
                }
                size++;
            }
            return size;
        }
        public boolean isMethod() {
            return formRef.stringValue().charAt(0) == '(';
        }
        public byte getLiteralTag() {
            switch (formRef.stringValue().charAt(0)) {
            case 'L': return CONSTANT_String;
            case 'I': return CONSTANT_Integer;
            case 'J': return CONSTANT_Long;
            case 'F': return CONSTANT_Float;
            case 'D': return CONSTANT_Double;
            case 'B': case 'S': case 'C': case 'Z':
                return CONSTANT_Integer;
            }
            assert(false);
            return CONSTANT_None;
        }
        public String prettyString() {
            String s;
            if (isMethod()) {
                s = formRef.stringValue();
                s = s.substring(0, 1+s.indexOf(')'));
            } else {
                s = "/" + formRef.stringValue();
            }
            int i;
            while ((i = s.indexOf(';')) >= 0) {
                s = s.substring(0, i) + s.substring(i + 1);
            }
            return s;
        }
    }

    static int compareSignatures(String s1, String s2) {
        return compareSignatures(s1, s2, null, null);
    }
    static int compareSignatures(String s1, String s2, String[] p1, String[] p2) {
        final int S1_COMES_FIRST = -1;
        final int S2_COMES_FIRST = +1;
        char c1 = s1.charAt(0);
        char c2 = s2.charAt(0);
        // fields before methods (because there are fewer of them)
        if (c1 != '(' && c2 == '(')  return S1_COMES_FIRST;
        if (c2 != '(' && c1 == '(')  return S2_COMES_FIRST;
        if (p1 == null)  p1 = structureSignature(s1);
        if (p2 == null)  p2 = structureSignature(s2);
        /*
         // non-classes before classes (because there are fewer of them)
         if (p1.length == 1 && p2.length > 1)  return S1_COMES_FIRST;
         if (p2.length == 1 && p1.length > 1)  return S2_COMES_FIRST;
         // all else being equal, use the same comparison as for Utf8 strings
         return s1.compareTo(s2);
         */
        if (p1.length != p2.length)  return p1.length - p2.length;
        int length = p1.length;
        for (int i = length; --i >= 0; ) {
            int res = p1[i].compareTo(p2[i]);
            if (res != 0)  return res;
        }
        assert(s1.equals(s2));
        return 0;
    }

    static int countClassParts(Utf8Entry formRef) {
        int num = 0;
        String s = formRef.stringValue();
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == 'L')  ++num;
        }
        return num;
    }

    static String flattenSignature(String[] parts) {
        String form = parts[0];
        if (parts.length == 1)  return form;
        int len = form.length();
        for (int i = 1; i < parts.length; i++) {
            len += parts[i].length();
        }
        char[] sig = new char[len];
        int j = 0;
        int k = 1;
        for (int i = 0; i < form.length(); i++) {
            char ch = form.charAt(i);
            sig[j++] = ch;
            if (ch == 'L') {
                String cls = parts[k++];
                cls.getChars(0, cls.length(), sig, j);
                j += cls.length();
                //sig[j++] = ';';
            }
        }
        assert(j == len);
        assert(k == parts.length);
        return new String(sig);
    }

    static private int skipClassNameChars(String sig, int i) {
        int len = sig.length();
        for (; i < len; i++) {
            char ch = sig.charAt(i);
            if (ch <= ' ')  break;
            if (ch >= ';' && ch <= '@')  break;
        }
        return i;
    }

    static String[] structureSignature(String sig) {
        sig = sig.intern();

        int formLen = 0;
        int nparts = 1;
        for (int i = 0; i < sig.length(); i++) {
            char ch = sig.charAt(i);
            formLen++;
            if (ch == 'L') {
                nparts++;
                int i2 = skipClassNameChars(sig, i+1);
                i = i2-1;  // keep the semicolon in the form
                int i3 = sig.indexOf('<', i+1);
                if (i3 > 0 && i3 < i2)
                    i = i3-1;
            }
        }
        char[] form = new char[formLen];
        if (nparts == 1) {
            String[] parts = { sig };
            return parts;
        }
        String[] parts = new String[nparts];
        int j = 0;
        int k = 1;
        for (int i = 0; i < sig.length(); i++) {
            char ch = sig.charAt(i);
            form[j++] = ch;
            if (ch == 'L') {
                int i2 = skipClassNameChars(sig, i+1);
                parts[k++] = sig.substring(i+1, i2);
                i = i2;
                --i;  // keep the semicolon in the form
            }
        }
        assert(j == formLen);
        assert(k == parts.length);
        parts[0] = new String(form);
        //assert(flattenSignature(parts).equals(sig));
        return parts;
    }

    // Handy constants:
    protected static final Entry[] noRefs = {};
    protected static final ClassEntry[] noClassRefs = {};

    /** An Index is a mapping between CP entries and small integers. */
    public static final
    class Index extends AbstractList {
        protected String debugName;
        protected Entry[] cpMap;
        protected boolean flattenSigs;
        protected Entry[] getMap() {
            return cpMap;
        }
        protected Index(String debugName) {
            this.debugName = debugName;
        }
        protected Index(String debugName, Entry[] cpMap) {
            this(debugName);
            setMap(cpMap);
        }
        protected void setMap(Entry[] cpMap) {
            clearIndex();
            this.cpMap = cpMap;
        }
        protected Index(String debugName, Collection<Entry> cpMapList) {
            this(debugName);
            setMap(cpMapList);
        }
        protected void setMap(Collection<Entry> cpMapList) {
            cpMap = new Entry[cpMapList.size()];
            cpMapList.toArray(cpMap);
            setMap(cpMap);
        }
        public int size() {
            return cpMap.length;
        }
        public Object get(int i) {
            return cpMap[i];
        }
        public Entry getEntry(int i) {
            // same as get(), with covariant return type
            return cpMap[i];
        }

        // Find index of e in cpMap, or return -1 if none.
        //
        // As a special hack, if flattenSigs, signatures are
        // treated as equivalent entries of cpMap.  This is wrong
        // from a Collection point of view, because contains()
        // reports true for signatures, but the iterator()
        // never produces them!
        private int findIndexOf(Entry e) {
            if (indexKey == null) {
                initializeIndex();
            }
            int probe = findIndexLocation(e);
            if (indexKey[probe] != e) {
                if (flattenSigs && e.tag == CONSTANT_Signature) {
                    SignatureEntry se = (SignatureEntry) e;
                    return findIndexOf(se.asUtf8Entry());
                }
                return -1;
            }
            int index = indexValue[probe];
            assert(e.equals(cpMap[index]));
            return index;
        }
        public boolean contains(Entry e) {
            return findIndexOf(e) >= 0;
        }
        // Find index of e in cpMap.  Should not return -1.
        public int indexOf(Entry e) {
            int index = findIndexOf(e);
            if (index < 0 && verbose() > 0) {
                System.out.println("not found: "+e);
                System.out.println("       in: "+this.dumpString());
                Thread.dumpStack();
            }
            assert(index >= 0);
            return index;
        }
        public boolean contains(Object e) {
            return findIndexOf((Entry)e) >= 0;
        }
        public int indexOf(Object e) {
            return findIndexOf((Entry)e);
        }
        public int lastIndexOf(Object e) {
            return indexOf(e);
        }

        public boolean assertIsSorted() {
            for (int i = 1; i < cpMap.length; i++) {
                if (cpMap[i-1].compareTo(cpMap[i]) > 0) {
                    System.out.println("Not sorted at "+(i-1)+"/"+i+": "+this.dumpString());
                    return false;
                }
            }
            return true;
        }

        // internal hash table
        protected Entry[] indexKey;
        protected int[]   indexValue;
        protected void clearIndex() {
            indexKey   = null;
            indexValue = null;
        }
        private int findIndexLocation(Entry e) {
            int size   = indexKey.length;
            int hash   = e.hashCode();
            int probe  = hash & (size - 1);
            int stride = ((hash >>> 8) | 1) & (size - 1);
            for (;;) {
                Entry e1 = indexKey[probe];
                if (e1 == e || e1 == null)
                    return probe;
                probe += stride;
                if (probe >= size)  probe -= size;
            }
        }
        private void initializeIndex() {
            if (verbose() > 2)
                System.out.println("initialize Index "+debugName+" ["+size()+"]");
            int hsize0 = (int)((cpMap.length + 10) * 1.5);
            int hsize = 1;
            while (hsize < hsize0) {
                hsize <<= 1;
            }
            indexKey   = new Entry[hsize];
            indexValue = new int[hsize];
            for (int i = 0; i < cpMap.length; i++) {
                Entry e = cpMap[i];
                if (e == null)  continue;
                int probe = findIndexLocation(e);
                assert(indexKey[probe] == null);  // e has unique index
                indexKey[probe] = e;
                indexValue[probe] = i;
            }
        }
        public Object[] toArray(Object[] a) {
            int sz = size();
            if (a.length < sz)  return super.toArray(a);
            System.arraycopy(cpMap, 0, a, 0, sz);
            if (a.length > sz)  a[sz] = null;
            return a;
        }
        public Object[] toArray() {
            return toArray(new Entry[size()]);
        }
        public Object clone() {
            return new Index(debugName, cpMap.clone());
        }
        public String toString() {
            return "Index "+debugName+" ["+size()+"]";
        }
        public String dumpString() {
            String s = toString();
            s += " {\n";
            for (int i = 0; i < cpMap.length; i++) {
                s += "    "+i+": "+cpMap[i]+"\n";
            }
            s += "}";
            return s;
        }
    }

    // Index methods.

    public static
    Index makeIndex(String debugName, Entry[] cpMap) {
        return new Index(debugName, cpMap);
    }

    public static
    Index makeIndex(String debugName, Collection<Entry> cpMapList) {
        return new Index(debugName, cpMapList);
    }

    /** Sort this index (destructively) into canonical order. */
    public static
    void sort(Index ix) {
        // %%% Should move this into class Index.
        ix.clearIndex();
        Arrays.sort(ix.cpMap);
        if (verbose() > 2)
            System.out.println("sorted "+ix.dumpString());
    }

    /** Return a set of indexes partitioning these entries.
     *  The keys array must of length this.size(), and marks entries.
     *  The result array is as long as one plus the largest key value.
     *  Entries with a negative key are dropped from the partition.
     */
    public static
    Index[] partition(Index ix, int[] keys) {
        // %%% Should move this into class Index.
        List<List<Entry>> parts = new ArrayList<>();
        Entry[] cpMap = ix.cpMap;
        assert(keys.length == cpMap.length);
        for (int i = 0; i < keys.length; i++) {
            int key = keys[i];
            if (key < 0)  continue;
            while (key >= parts.size()) {
                parts.add(null);
            }
            List<Entry> part = parts.get(key);
            if (part == null) {
                parts.set(key, part = new ArrayList<>());
            }
            part.add(cpMap[i]);
        }
        Index[] indexes = new Index[parts.size()];
        for (int key = 0; key < indexes.length; key++) {
            List<Entry> part = parts.get(key);
            if (part == null)  continue;
            indexes[key] = new Index(ix.debugName+"/part#"+key, part);
            assert(indexes[key].indexOf(part.get(0)) == 0);
        }
        return indexes;
    }
    public static
    Index[] partitionByTag(Index ix) {
        // Partition by tag.
        Entry[] cpMap = ix.cpMap;
        int[] keys = new int[cpMap.length];
        for (int i = 0; i < keys.length; i++) {
            Entry e = cpMap[i];
            keys[i] = (e == null)? -1: e.tag;
        }
        Index[] byTag = partition(ix, keys);
        for (int tag = 0; tag < byTag.length; tag++) {
            if (byTag[tag] == null)  continue;
            byTag[tag].debugName = tagName(tag);
        }
        if (byTag.length < CONSTANT_Limit) {
            Index[] longer = new Index[CONSTANT_Limit];
            System.arraycopy(byTag, 0, longer, 0, byTag.length);
            byTag = longer;
        }
        return byTag;
    }

    /** Coherent group of constant pool indexes. */
    public static
    class IndexGroup {
        private Index indexUntyped;
        private Index[] indexByTag = new Index[CONSTANT_Limit];
        private int[]   untypedFirstIndexByTag;
        private int     totalSize;
        private Index[][] indexByTagAndClass;

        /** Index of all CP entries of all types, in definition order. */
        public Index getUntypedIndex() {
            if (indexUntyped == null) {
                untypedIndexOf(null);  // warm up untypedFirstIndexByTag
                Entry[] cpMap = new Entry[totalSize];
                for (int tag = 0; tag < indexByTag.length; tag++) {
                    Index ix = indexByTag[tag];
                    if (ix == null)  continue;
                    int ixLen = ix.cpMap.length;
                    if (ixLen == 0)  continue;
                    int fillp = untypedFirstIndexByTag[tag];
                    assert(cpMap[fillp] == null);
                    assert(cpMap[fillp+ixLen-1] == null);
                    System.arraycopy(ix.cpMap, 0, cpMap, fillp, ixLen);
                }
                indexUntyped = new Index("untyped", cpMap);
            }
            return indexUntyped;
        }

        public int untypedIndexOf(Entry e) {
            if (untypedFirstIndexByTag == null) {
                untypedFirstIndexByTag = new int[CONSTANT_Limit];
                int fillp = 0;
                for (int i = 0; i < TAGS_IN_ORDER.length; i++) {
                    byte tag = TAGS_IN_ORDER[i];
                    Index ix = indexByTag[tag];
                    if (ix == null)  continue;
                    int ixLen = ix.cpMap.length;
                    untypedFirstIndexByTag[tag] = fillp;
                    fillp += ixLen;
                }
                totalSize = fillp;
            }
            if (e == null)  return -1;
            int tag = e.tag;
            Index ix = indexByTag[tag];
            if (ix == null)  return -1;
            int idx = ix.findIndexOf(e);
            if (idx >= 0)
                idx += untypedFirstIndexByTag[tag];
            return idx;
        }

        public void initIndexByTag(byte tag, Index ix) {
            assert(indexByTag[tag] == null);  // do not init twice
            Entry[] cpMap = ix.cpMap;
            for (int i = 0; i < cpMap.length; i++) {
                // It must be a homogeneous Entry set.
                assert(cpMap[i].tag == tag);
            }
            if (tag == CONSTANT_Utf8) {
                // Special case:  First Utf8 must always be empty string.
                assert(cpMap.length == 0 || cpMap[0].stringValue().equals(""));
            }
            indexByTag[tag] = ix;
            // decache indexes derived from this one:
            untypedFirstIndexByTag = null;
            indexUntyped = null;
            if (indexByTagAndClass != null)
                indexByTagAndClass[tag] = null;
        }

        /** Index of all CP entries of a given tag. */
        public Index getIndexByTag(byte tag) {
            if (tag == CONSTANT_All) {
                return getUntypedIndex();
            }
            Index ix = indexByTag[tag];
            if (ix == null) {
                // Make an empty one by default.
                ix = new Index(tagName(tag), new Entry[0]);
                indexByTag[tag] = ix;
            }
            return ix;
        }

        /** Index of all CP entries of a given tag and class. */
        public Index getMemberIndex(byte tag, ClassEntry classRef) {
            if (indexByTagAndClass == null)
                indexByTagAndClass = new Index[CONSTANT_Limit][];
            Index allClasses =  getIndexByTag(CONSTANT_Class);
            Index[] perClassIndexes = indexByTagAndClass[tag];
            if (perClassIndexes == null) {
                // Create the partition now.
                // Divide up all entries of the given tag according to their class.
                Index allMembers = getIndexByTag(tag);
                int[] whichClasses = new int[allMembers.size()];
                for (int i = 0; i < whichClasses.length; i++) {
                    MemberEntry e = (MemberEntry) allMembers.get(i);
                    int whichClass = allClasses.indexOf(e.classRef);
                    whichClasses[i] = whichClass;
                }
                perClassIndexes = partition(allMembers, whichClasses);
                for (int i = 0; i < perClassIndexes.length; i++) {
                    assert (perClassIndexes[i] == null ||
                            perClassIndexes[i].assertIsSorted());
                }
                indexByTagAndClass[tag] = perClassIndexes;
            }
            int whichClass = allClasses.indexOf(classRef);
            return perClassIndexes[whichClass];
        }

        // Given the sequence of all methods of the given name and class,
        // produce the ordinal of this particular given overloading.
        public int getOverloadingIndex(MemberEntry methodRef) {
            Index ix = getMemberIndex(methodRef.tag, methodRef.classRef);
            Utf8Entry nameRef = methodRef.descRef.nameRef;
            int ord = 0;
            for (int i = 0; i < ix.cpMap.length; i++) {
                MemberEntry e = (MemberEntry) ix.cpMap[i];
                if (e.equals(methodRef))
                    return ord;
                if (e.descRef.nameRef.equals(nameRef))
                    // Found a different overloading.  Increment the ordinal.
                    ord++;
            }
            throw new RuntimeException("should not reach here");
        }

        // Inverse of getOverloadingIndex
        public MemberEntry getOverloadingForIndex(byte tag, ClassEntry classRef, String name, int which) {
            assert(name.equals(name.intern()));
            Index ix = getMemberIndex(tag, classRef);
            int ord = 0;
            for (int i = 0; i < ix.cpMap.length; i++) {
                MemberEntry e = (MemberEntry) ix.cpMap[i];
                if (e.descRef.nameRef.stringValue().equals(name)) {
                    if (ord == which)  return e;
                    ord++;
                }
            }
            throw new RuntimeException("should not reach here");
        }

        public boolean haveNumbers() {
            for (byte tag = CONSTANT_Integer; tag <= CONSTANT_Double; tag++) {
                switch (tag) {
                case CONSTANT_Integer:
                case CONSTANT_Float:
                case CONSTANT_Long:
                case CONSTANT_Double:
                    break;
                default:
                    assert(false);
                }
                if (getIndexByTag(tag).size() > 0)  return true;
            }
            return false;
        }

    }

    /** Close the set cpRefs under the getRef(*) relation.
     *  Also, if flattenSigs, replace all signatures in cpRefs
     *  by their equivalent Utf8s.
     *  Also, discard null from cpRefs.
     */
    public static
    void completeReferencesIn(Set<Entry> cpRefs, boolean flattenSigs) {
        cpRefs.remove(null);
        for (ListIterator<Entry> work =
                 new ArrayList<>(cpRefs).listIterator(cpRefs.size());
             work.hasPrevious(); ) {
            Entry e = work.previous();
            work.remove();          // pop stack
            assert(e != null);
            if (flattenSigs && e.tag == CONSTANT_Signature) {
                SignatureEntry se = (SignatureEntry) e;
                Utf8Entry      ue = se.asUtf8Entry();
                // Totally replace e by se.
                cpRefs.remove(se);
                cpRefs.add(ue);
                e = ue;   // do not descend into the sig
            }
            // Recursively add the refs of e to cpRefs:
            for (int i = 0; ; i++) {
                Entry re = e.getRef(i);
                if (re == null)
                    break;          // no more refs in e
                if (cpRefs.add(re)) // output the ref
                    work.add(re);   // push stack, if a new ref
            }
        }
    }

    static double percent(int num, int den) {
        return (int)((10000.0*num)/den + 0.5) / 100.0;
    }

    public static String tagName(int tag) {
        switch (tag) {
            case CONSTANT_Utf8:                 return "Utf8";
            case CONSTANT_Integer:              return "Integer";
            case CONSTANT_Float:                return "Float";
            case CONSTANT_Long:                 return "Long";
            case CONSTANT_Double:               return "Double";
            case CONSTANT_Class:                return "Class";
            case CONSTANT_String:               return "String";
            case CONSTANT_Fieldref:             return "Fieldref";
            case CONSTANT_Methodref:            return "Methodref";
            case CONSTANT_InterfaceMethodref:   return "InterfaceMethodref";
            case CONSTANT_NameandType:          return "NameandType";

                // pseudo-tags:
            case CONSTANT_All:                  return "*All";
            case CONSTANT_None:                 return "*None";
            case CONSTANT_Signature:            return "*Signature";
        }
        return "tag#"+tag;
    }

    // archive constant pool definition order
    static final byte TAGS_IN_ORDER[] = {
        CONSTANT_Utf8,
        CONSTANT_Integer,           // cp_Int
        CONSTANT_Float,
        CONSTANT_Long,
        CONSTANT_Double,
        CONSTANT_String,
        CONSTANT_Class,
        CONSTANT_Signature,
        CONSTANT_NameandType,       // cp_Descr
        CONSTANT_Fieldref,          // cp_Field
        CONSTANT_Methodref,         // cp_Method
        CONSTANT_InterfaceMethodref // cp_Imethod
    };
    static final byte TAG_ORDER[];
    static {
        TAG_ORDER = new byte[CONSTANT_Limit];
        for (int i = 0; i < TAGS_IN_ORDER.length; i++) {
            TAG_ORDER[TAGS_IN_ORDER[i]] = (byte)(i+1);
        }
        /*
        System.out.println("TAG_ORDER[] = {");
        for (int i = 0; i < TAG_ORDER.length; i++)
            System.out.println("  "+TAG_ORDER[i]+",");
        System.out.println("};");
        */
    }
}
