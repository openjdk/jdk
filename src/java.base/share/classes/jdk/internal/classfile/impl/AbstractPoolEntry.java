/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.internal.classfile.impl;

import java.lang.classfile.constantpool.*;
import java.lang.constant.*;
import java.lang.invoke.TypeDescriptor;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import jdk.internal.access.JavaLangAccess;
import jdk.internal.access.SharedSecrets;
import jdk.internal.constant.ClassOrInterfaceDescImpl;
import jdk.internal.constant.PrimitiveClassDescImpl;
import jdk.internal.util.ArraysSupport;
import jdk.internal.vm.annotation.Stable;

import static java.util.Objects.requireNonNull;

public abstract sealed class AbstractPoolEntry {
    /*
    Invariant: a {CP,BSM} entry for pool P refer only to {CP,BSM} entries
    from P or P's parent.  This is enforced by the various xxxEntry methods
    in SplitConstantPool.  As a result, code in this file can use writeU2
    instead of writeIndex.

    Cloning of entries may be a no-op if the entry is already on the right pool
    (which implies that the referenced entries will also be on the right pool.)
     */

    private static final int TAG_SMEAR = 0x13C4B2D1;
    static final int NON_ZERO = 0x40000000;

    public static int hash1(int tag, int x1) {
        return (tag * TAG_SMEAR + x1) | NON_ZERO;
    }

    public static int hash2(int tag, int x1, int x2) {
        return (tag * TAG_SMEAR + x1 + 31 * x2) | NON_ZERO;
    }

    // Ensure that hash is never zero
    public static int hashString(int stringHash) {
        return stringHash | NON_ZERO;
    }

    static int hashClassFromUtf8(boolean isArray, Utf8EntryImpl content) {
        int hash = content.contentHash();
        return hashClassFromDescriptor(isArray ? hash : Util.descriptorStringHash(content.length(), hash));
    }

    static int hashClassFromDescriptor(int descriptorHash) {
        return hash1(PoolEntry.TAG_CLASS, descriptorHash);
    }

    @SuppressWarnings("unchecked")
    public static <T extends PoolEntry> T maybeClone(ConstantPoolBuilder cp, T entry) {
        if (cp.canWriteDirect(entry.constantPool()))
            return entry;
        return (T)((AbstractPoolEntry)entry).clone(cp);
    }

    final ConstantPool constantPool;
    private final int index;
    private final int hash;

    private AbstractPoolEntry(ConstantPool constantPool, int index, int hash) {
        this.index = index;
        this.hash = hash;
        this.constantPool = constantPool;
    }

    public ConstantPool constantPool() { return constantPool; }

    public int index() { return index; }

    @Override
    public int hashCode() {
        return hash;
    }

    public abstract int tag();

    public int width() {
        return 1;
    }

    abstract void writeTo(BufWriterImpl buf);

    abstract PoolEntry clone(ConstantPoolBuilder cp);

    public static final class Utf8EntryImpl extends AbstractPoolEntry implements Utf8Entry {
        // Processing UTF8 from the constant pool is one of the more expensive
        // operations, and often, we don't actually need access to the constant
        // as a string.  So there are multiple layers of laziness in UTF8
        // constants.  In the first stage, all we do is record the range of
        // bytes in the classfile.  If the size or hashCode is needed, then we
        // process the raw bytes into a byte[] or char[], but do not inflate
        // a String.  If a string is needed, it too is inflated lazily.
        // If we construct a Utf8Entry from a string, we generate the encoding
        // at write time.

        enum State { RAW, BYTE, CHAR, STRING }

        private static final JavaLangAccess JLA = SharedSecrets.getJavaLangAccess();

        private State state;
        private final byte[] rawBytes; // null if initialized directly from a string
        private final int offset;
        private final int rawLen;
        // Set in any state other than RAW
        private @Stable int contentHash;
        private @Stable int charLen;
        // Set in CHAR state
        private @Stable char[] chars;
        // Only set in STRING state
        private @Stable String stringValue;
        // The descriptor symbol, if this is a descriptor
        @Stable TypeDescriptor typeSym;

        Utf8EntryImpl(ConstantPool cpm, int index,
                          byte[] rawBytes, int offset, int rawLen) {
            super(cpm, index, 0);
            this.rawBytes = rawBytes;
            this.offset = offset;
            this.rawLen = rawLen;
            this.state = State.RAW;
        }

        Utf8EntryImpl(ConstantPool cpm, int index, String s) {
            this(cpm, index, s, s.hashCode());
        }

        Utf8EntryImpl(ConstantPool cpm, int index, String s, int contentHash) {
            super(cpm, index, 0);
            this.rawBytes = null;
            this.offset = 0;
            this.rawLen = 0;
            this.state = State.STRING;
            this.stringValue = s;
            this.charLen = s.length();
            this.contentHash = contentHash;
        }

        Utf8EntryImpl(ConstantPool cpm, int index, Utf8EntryImpl u) {
            super(cpm, index, 0);
            this.rawBytes = u.rawBytes;
            this.offset = u.offset;
            this.rawLen = u.rawLen;
            this.state = u.state;
            this.contentHash = u.contentHash;
            this.charLen = u.charLen;
            this.chars = u.chars;
            this.stringValue = u.stringValue;
            this.typeSym = u.typeSym;
        }

        @Override
        public int tag() {
            return TAG_UTF8;
        }

        /**
         * {@jvms 4.4.7} String content is encoded in modified UTF-8.
         *
         * Modified UTF-8 strings are encoded so that code point sequences that
         * contain only non-null ASCII characters can be represented using only 1
         * byte per code point, but all code points in the Unicode codespace can be
         * represented.
         *
         * Modified UTF-8 strings are not null-terminated.
         *
         * Code points in the range '\u0001' to '\u007F' are represented by a single
         * byte.
         *
         * The null code point ('\u0000') and code points in the range '\u0080' to
         * '\u07FF' are represented by a pair of bytes.
         *
         * Code points in the range '\u0800' to '\uFFFF' are represented by 3 bytes.
         *
         * Characters with code points above U+FFFF (so-called supplementary
         * characters) are represented by separately encoding the two surrogate code
         * units of their UTF-16 representation. Each of the surrogate code units is
         * represented by three bytes. This means supplementary characters are
         * represented by six bytes.
         *
         * The bytes of multibyte characters are stored in the class file in
         * big-endian (high byte first) order.
         *
         * There are two differences between this format and the "standard" UTF-8
         * format. First, the null character (char)0 is encoded using the 2-byte
         * format rather than the 1-byte format, so that modified UTF-8 strings
         * never have embedded nulls. Second, only the 1-byte, 2-byte, and 3-byte
         * formats of standard UTF-8 are used. The Java Virtual Machine does not
         * recognize the four-byte format of standard UTF-8; it uses its own
         * two-times-three-byte format instead.
         */
        private void inflate() {
            int singleBytes = JLA.countPositives(rawBytes, offset, rawLen);
            int hash = ArraysSupport.hashCodeOfUnsigned(rawBytes, offset, singleBytes, 0);
            if (singleBytes == rawLen) {
                this.contentHash = hash;
                charLen = rawLen;
                state = State.BYTE;
            } else {
                inflateNonAscii(singleBytes, hash);
            }
        }

        private void inflateNonAscii(int singleBytes, int hash) {
            char[] chararr = new char[rawLen];
            int chararr_count = singleBytes;
            // Inflate prefix of bytes to characters
            JLA.inflateBytesToChars(rawBytes, offset, chararr, 0, singleBytes);

            int px = offset + singleBytes;
            int utfend = offset + rawLen;
            while (px < utfend) {
                int c = (int) rawBytes[px] & 0xff;
                switch (c >> 4) {
                    case 0, 1, 2, 3, 4, 5, 6, 7: {
                        // 0xxx xxxx
                        px++;
                        chararr[chararr_count++] = (char) c;
                        hash = 31 * hash + c;
                        break;
                    }
                    case 12, 13: {
                        // 110x xxxx  10xx xxxx
                        px += 2;
                        if (px > utfend) {
                            throw malformedInput(utfend);
                        }
                        int char2 = rawBytes[px - 1];
                        if ((char2 & 0xC0) != 0x80) {
                            throw malformedInput(px);
                        }
                        char v = (char) (((c & 0x1F) << 6) | (char2 & 0x3F));
                        chararr[chararr_count++] = v;
                        hash = 31 * hash + v;
                        break;
                    }
                    case 14: {
                        // 1110 xxxx  10xx xxxx  10xx xxxx
                        px += 3;
                        if (px > utfend) {
                            throw malformedInput(utfend);
                        }
                        int char2 = rawBytes[px - 2];
                        int char3 = rawBytes[px - 1];
                        if (((char2 & 0xC0) != 0x80) || ((char3 & 0xC0) != 0x80)) {
                            throw malformedInput(px - 1);
                        }
                        char v = (char) (((c & 0x0F) << 12) | ((char2 & 0x3F) << 6) | (char3 & 0x3F));
                        chararr[chararr_count++] = v;
                        hash = 31 * hash + v;
                        break;
                    }
                    default:
                        // 10xx xxxx,  1111 xxxx
                        throw malformedInput(px);
                }
            }
            this.contentHash = hash;
            charLen = chararr_count;
            this.chars = chararr;
            state = State.CHAR;
        }

        private ConstantPoolException malformedInput(int px) {
            return new ConstantPoolException("#%d: malformed modified UTF8 around byte %d".formatted(index(), px));
        }

        @Override
        public Utf8EntryImpl clone(ConstantPoolBuilder cp) {
            var ret = (state == State.STRING && rawBytes == null)
                   ? (Utf8EntryImpl) cp.utf8Entry(stringValue)
                   : ((SplitConstantPool) cp).maybeCloneUtf8Entry(this);
            var mySym = this.typeSym;
            if (ret.typeSym == null && mySym != null)
                ret.typeSym = mySym;
            return ret;
        }

        @Override
        public int hashCode() {
            return hashString(contentHash());
        }

        int contentHash() {
            if (state == State.RAW)
                inflate();
            return contentHash;
        }

        @Override
        public String toString() {
            if (state == State.RAW)
                inflate();
            if (state != State.STRING) {
                stringValue = (chars != null)
                              ? new String(chars, 0, charLen)
                              : new String(rawBytes, offset, charLen, StandardCharsets.ISO_8859_1);
                state = State.STRING;
            }
            return stringValue;
        }

        @Override
        public String stringValue() {
            return toString();
        }

        @Override
        public ConstantDesc constantValue() {
            return stringValue();
        }

        @Override
        public int length() {
            if (state == State.RAW)
                inflate();
            return charLen;
        }

        @Override
        public char charAt(int index) {
            if (state == State.STRING)
                return stringValue.charAt(index);
            if (state == State.RAW)
                inflate();
            return (chars != null)
                   ? chars[index]
                   : (char) rawBytes[index + offset];
        }

        @Override
        public CharSequence subSequence(int start, int end) {
            return toString().subSequence(start, end);
        }

        @Override
        public boolean equals(Object o) {
            if (o == this) return true;
            if (o instanceof Utf8EntryImpl u) {
                return equalsUtf8(u);
            }
            return false;
        }

        public boolean equalsUtf8(Utf8EntryImpl u) {
            if (hashCode() != u.hashCode()
                || length() != u.length())
                return false;
            if (rawBytes != null && u.rawBytes != null)
                return Arrays.equals(rawBytes, offset, offset + rawLen,
                                     u.rawBytes, u.offset, u.offset + u.rawLen);
            else if ((state == State.STRING && u.state == State.STRING))
                return stringValue.equals(u.stringValue);
            else
                return stringValue().equals(u.stringValue());
        }

        @Override
        public boolean equalsString(String s) {
            if (state == State.RAW)
                inflate();
            switch (state) {
                case STRING:
                    return stringValue.equals(requireNonNull(s));
                case CHAR:
                    if (charLen != s.length() || contentHash != s.hashCode())
                        return false;
                    for (int i=0; i<charLen; i++)
                        if (chars[i] != s.charAt(i))
                            return false;
                    stringValue = s;
                    state = State.STRING;
                    return true;
                case BYTE:
                    if (rawLen != s.length() || contentHash != s.hashCode())
                        return false;
                    for (int i=0; i<rawLen; i++)
                        if (rawBytes[offset+i] != s.charAt(i))
                            return false;
                    stringValue = s;
                    state = State.STRING;
                    return true;
            }
            throw new IllegalStateException("cannot reach here");
        }

        @Override
        void writeTo(BufWriterImpl pool) {
            if (rawBytes != null) {
                pool.writeU1U2(TAG_UTF8, rawLen);
                pool.writeBytes(rawBytes, offset, rawLen);
            }
            else {
                // state == STRING and no raw bytes
                pool.writeUtfEntry(stringValue);
            }
        }

        public ClassDesc fieldTypeSymbol() {
            if (typeSym instanceof ClassDesc cd)
                return cd;
            var ret = ClassDesc.ofDescriptor(stringValue());
            typeSym = ret;
            return ret;
        }

        public MethodTypeDesc methodTypeSymbol() {
            if (typeSym instanceof MethodTypeDesc mtd)
                return mtd;
            var ret = MethodTypeDesc.ofDescriptor(stringValue());
            typeSym = ret;
            return ret;
        }

        @Override
        public boolean isFieldType(ClassDesc desc) {
            var sym = typeSym;
            if (sym != null) {
                return sym instanceof ClassDesc cd && cd.equals(desc);
            }

            // In parsing, Utf8Entry is not even inflated by this point
            // We can operate on the raw byte arrays, as all ascii are compatible
            var ret = state == State.RAW
                    ? rawEqualsSym(desc)
                    : equalsString(desc.descriptorString());
            if (ret)
                this.typeSym = desc;
            return ret;
        }

        private boolean rawEqualsSym(ClassDesc desc) {
            int len = rawLen;
            if (len < 1) {
                return false;
            }
            int c = rawBytes[offset];
            if (len == 1) {
                return desc instanceof PrimitiveClassDescImpl pd && pd.wrapper().basicTypeChar() == c;
            } else if (c == 'L') {
                return desc.isClassOrInterface() && equalsString(desc.descriptorString());
            } else if (c == '[') {
                return desc.isArray() && equalsString(desc.descriptorString());
            } else {
                return false;
            }
        }

        boolean mayBeArrayDescriptor() {
            if (state == State.RAW) {
                return rawLen > 0 && rawBytes[offset] == '[';
            } else {
                return charLen > 0 && charAt(0) == '[';
            }
        }

        @Override
        public boolean isMethodType(MethodTypeDesc desc) {
            var sym = typeSym;
            if (sym != null) {
                return sym instanceof MethodTypeDesc mtd && mtd.equals(desc);
            }

            // In parsing, Utf8Entry is not even inflated by this point
            // We can operate on the raw byte arrays, as all ascii are compatible
            var ret = state == State.RAW
                    ? rawEqualsSym(desc)
                    : equalsString(desc.descriptorString());
            if (ret)
                this.typeSym = desc;
            return ret;
        }

        private boolean rawEqualsSym(MethodTypeDesc desc) {
            if (rawLen < 3) {
                return false;
            }
            var bytes = rawBytes;
            int index = offset;
            int c = bytes[index] | (bytes[index + 1] << Byte.SIZE);
            if ((desc.parameterCount() == 0) != (c == ('(' | (')' << Byte.SIZE)))) {
                // heuristic - avoid inflation for no-arg status mismatch
                return false;
            }
            return (c & 0xFF) == '(' && equalsString(desc.descriptorString());
        }
    }

    abstract static sealed class AbstractRefEntry<T extends PoolEntry> extends AbstractPoolEntry {
        protected final T ref1;

        public AbstractRefEntry(ConstantPool constantPool, int tag, int index, T ref1) {
            super(constantPool, index, hash1(tag, ref1.index()));
            this.ref1 = ref1;
        }

        public T ref1() {
            return ref1;
        }

        void writeTo(BufWriterImpl pool) {
            pool.writeU1U2(tag(), ref1.index());
        }

        @Override
        public String toString() {
            return tag() + " " + ref1();
        }
    }

    abstract static sealed class AbstractRefsEntry<T extends PoolEntry, U extends PoolEntry>
            extends AbstractPoolEntry {
        protected final T ref1;
        protected final U ref2;

        public AbstractRefsEntry(ConstantPool constantPool, int tag, int index, T ref1, U ref2) {
            super(constantPool, index, hash2(tag, ref1.index(), ref2.index()));
            this.ref1 = ref1;
            this.ref2 = ref2;
        }

        public T ref1() {
            return ref1;
        }

        public U ref2() {
            return ref2;
        }

        void writeTo(BufWriterImpl pool) {
            pool.writeU1U2U2(tag(), ref1.index(), ref2.index());
        }

        @Override
        public String toString() {
            return tag() + " " + ref1 + "-" + ref2;
        }
    }

    abstract static sealed class AbstractNamedEntry extends AbstractRefEntry<Utf8EntryImpl> {

        public AbstractNamedEntry(ConstantPool constantPool, int tag, int index, Utf8EntryImpl ref1) {
            super(constantPool, tag, index, ref1);
        }

        public Utf8Entry name() {
            return ref1;
        }

        public String asInternalName() {
            return ref1.stringValue();
        }
    }

    public static final class ClassEntryImpl extends AbstractNamedEntry implements ClassEntry {

        public @Stable ClassDesc sym;
        private @Stable int hash;

        ClassEntryImpl(ConstantPool cpm, int index, Utf8EntryImpl name) {
            super(cpm, TAG_CLASS, index, name);
        }

        ClassEntryImpl(ConstantPool cpm, int index, Utf8EntryImpl name, int hash, ClassDesc sym) {
            super(cpm, TAG_CLASS, index, name);
            this.hash = hash;
            this.sym = sym;
        }

        @Override
        public int tag() {
            return TAG_CLASS;
        }

        @Override
        public ClassEntry clone(ConstantPoolBuilder cp) {
            return ((SplitConstantPool) cp).cloneClassEntry(this);
        }

        @Override
        public ClassDesc asSymbol() {
            var sym = this.sym;
            if (sym != null) {
                return sym;
            }

            if (ref1.mayBeArrayDescriptor()) {
                sym = ref1.fieldTypeSymbol(); // array, symbol already available
            } else {
                sym = ClassDesc.ofInternalName(asInternalName()); // class or interface
            }
            return this.sym = sym;
        }

        @Override
        public boolean matches(ClassDesc desc) {
            var sym = this.sym;
            if (sym != null) {
                return sym.equals(desc);
            }

            var ret = rawEqualsSymbol(desc);
            if (ret)
                this.sym = desc;
            return ret;
        }

        private boolean rawEqualsSymbol(ClassDesc desc) {
            if (ref1.mayBeArrayDescriptor()) {
                return desc.isArray() && ref1.isFieldType(desc);
            } else {
                return desc instanceof ClassOrInterfaceDescImpl coid
                        && ref1.equalsString(coid.internalName());
            }
        }

        @Override
        public boolean equals(Object o) {
            if (o == this) return true;
            if (o instanceof ClassEntryImpl other) {
                return equalsEntry(other);
            }
            return false;
        }

        boolean equalsEntry(ClassEntryImpl other) {
            var tsym = this.sym;
            var osym = other.sym;
            if (tsym != null && osym != null) {
                return tsym.equals(osym);
            }

            return ref1.equalsUtf8(other.ref1);
        }

        @Override
        public int hashCode() {
            var hash = this.hash;
            if (hash != 0)
                return hash;

            return this.hash = hashClassFromUtf8(ref1.mayBeArrayDescriptor(), ref1);
        }
    }

    public static final class PackageEntryImpl extends AbstractNamedEntry implements PackageEntry {

        PackageEntryImpl(ConstantPool cpm, int index, Utf8EntryImpl name) {
            super(cpm, TAG_PACKAGE, index, name);
        }

        @Override
        public int tag() {
            return TAG_PACKAGE;
        }

        @Override
        public PackageEntry clone(ConstantPoolBuilder cp) {
            return cp.packageEntry(ref1);
        }

        @Override
        public PackageDesc asSymbol() {
            return PackageDesc.ofInternalName(asInternalName());
        }

        @Override
        public boolean matches(PackageDesc desc) {
            return ref1.equalsString(desc.internalName());
        }

        @Override
        public boolean equals(Object o) {
            if (o == this) return true;
            if (o instanceof PackageEntry p) {
                return name().equals(p.name());
            }
            return false;
        }
    }

    public static final class ModuleEntryImpl extends AbstractNamedEntry implements ModuleEntry {

        ModuleEntryImpl(ConstantPool cpm, int index, Utf8EntryImpl name) {
            super(cpm, TAG_MODULE, index, name);
        }

        @Override
        public int tag() {
            return TAG_MODULE;
        }

        @Override
        public ModuleEntry clone(ConstantPoolBuilder cp) {
            return cp.moduleEntry(ref1);
        }

        @Override
        public ModuleDesc asSymbol() {
            return ModuleDesc.of(asInternalName());
        }

        @Override
        public boolean matches(ModuleDesc desc) {
            return ref1.equalsString(desc.name());
        }

        @Override
        public boolean equals(Object o) {
            if (o == this) return true;
            if (o instanceof ModuleEntryImpl m) {
                return name().equals(m.name());
            }
            return false;
        }
    }

    public static final class NameAndTypeEntryImpl extends AbstractRefsEntry<Utf8EntryImpl, Utf8EntryImpl>
            implements NameAndTypeEntry {

        NameAndTypeEntryImpl(ConstantPool cpm, int index, Utf8EntryImpl name, Utf8EntryImpl type) {
            super(cpm, TAG_NAME_AND_TYPE, index, name, type);
        }

        @Override
        public int tag() {
            return TAG_NAME_AND_TYPE;
        }

        @Override
        public Utf8Entry name() {
            return ref1;
        }

        @Override
        public Utf8Entry type() {
            return ref2;
        }

        @Override
        public NameAndTypeEntry clone(ConstantPoolBuilder cp) {
            return cp.nameAndTypeEntry(ref1, ref2);
        }

        @Override
        public boolean equals(Object o) {
            if (o == this) return true;
            if (o instanceof NameAndTypeEntryImpl nat) {
                return name().equals(nat.name()) && type().equals(nat.type());
            }
            return false;
        }
    }

    public abstract static sealed class AbstractMemberRefEntry
            extends AbstractRefsEntry<ClassEntryImpl, NameAndTypeEntryImpl>
            implements MemberRefEntry {

        AbstractMemberRefEntry(ConstantPool cpm, int tag, int index, ClassEntryImpl owner,
                       NameAndTypeEntryImpl nameAndType) {
            super(cpm, tag, index, owner, nameAndType);
        }

        @Override
        public ClassEntryImpl owner() {
            return ref1;
        }

        @Override
        public NameAndTypeEntryImpl nameAndType() {
            return ref2;
        }

        @Override
        public String toString() {
            return tag() + " " + owner().asInternalName() + "." + nameAndType().name().stringValue()
                   + "-" + nameAndType().type().stringValue();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o instanceof AbstractMemberRefEntry m) {
                return tag() == m.tag()
                && owner().equals(m.owner())
                && nameAndType().equals(m.nameAndType());
            }
            return false;
        }
    }

    public static final class FieldRefEntryImpl extends AbstractMemberRefEntry implements FieldRefEntry {

        FieldRefEntryImpl(ConstantPool cpm, int index,
                              ClassEntryImpl owner, NameAndTypeEntryImpl nameAndType) {
            super(cpm, TAG_FIELDREF, index, owner, nameAndType);
        }

        @Override
        public int tag() {
            return TAG_FIELDREF;
        }

        @Override
        public FieldRefEntry clone(ConstantPoolBuilder cp) {
            return cp.fieldRefEntry(ref1, ref2);
        }
    }

    public static final class MethodRefEntryImpl extends AbstractMemberRefEntry implements MethodRefEntry {

        MethodRefEntryImpl(ConstantPool cpm, int index,
                               ClassEntryImpl owner, NameAndTypeEntryImpl nameAndType) {
            super(cpm, TAG_METHODREF, index, owner, nameAndType);
        }

        @Override
        public int tag() {
            return TAG_METHODREF;
        }

        @Override
        public MethodRefEntry clone(ConstantPoolBuilder cp) {
            return cp.methodRefEntry(ref1, ref2);
        }
    }

    public static final class InterfaceMethodRefEntryImpl extends AbstractMemberRefEntry implements InterfaceMethodRefEntry {

        InterfaceMethodRefEntryImpl(ConstantPool cpm, int index, ClassEntryImpl owner,
                                        NameAndTypeEntryImpl nameAndType) {
            super(cpm, TAG_INTERFACE_METHODREF, index, owner, nameAndType);
        }

        @Override
        public int tag() {
            return TAG_INTERFACE_METHODREF;
        }

        @Override
        public InterfaceMethodRefEntry clone(ConstantPoolBuilder cp) {
            return cp.interfaceMethodRefEntry(ref1, ref2);
        }
    }

    public abstract static sealed class AbstractDynamicConstantPoolEntry extends AbstractPoolEntry {

        private final int bsmIndex;
        private BootstrapMethodEntryImpl bootstrapMethod;
        private final NameAndTypeEntryImpl nameAndType;

        AbstractDynamicConstantPoolEntry(ConstantPool cpm, int index, int hash, BootstrapMethodEntryImpl bootstrapMethod,
                                         NameAndTypeEntryImpl nameAndType) {
            super(cpm, index, hash);
            this.bsmIndex = bootstrapMethod.bsmIndex();
            this.bootstrapMethod = bootstrapMethod;
            this.nameAndType = nameAndType;
        }

        AbstractDynamicConstantPoolEntry(ConstantPool cpm, int index, int hash, int bsmIndex,
                                         NameAndTypeEntryImpl nameAndType) {
            super(cpm, index, hash);
            this.bsmIndex = bsmIndex;
            this.bootstrapMethod = null;
            this.nameAndType = nameAndType;
        }

        /**
         * @return the bootstrapMethod
         */
        public BootstrapMethodEntryImpl bootstrap() {
            if (bootstrapMethod == null) {
                bootstrapMethod = (BootstrapMethodEntryImpl) constantPool.bootstrapMethodEntry(bsmIndex);
            }
            return bootstrapMethod;
        }

        /**
         * @return the bsmIndex
         */
        public int bootstrapMethodIndex() {
            return bsmIndex;
        }

        /**
         * @return the nameAndType
         */
        public NameAndTypeEntryImpl nameAndType() {
            return nameAndType;
        }

        void writeTo(BufWriterImpl pool) {
            pool.writeU1U2U2(tag(), bsmIndex, nameAndType.index());
        }

        @Override
        public String toString() {
            return tag() + " " + bootstrap() + "." + nameAndType().name().stringValue()
                   + "-" + nameAndType().type().stringValue();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o instanceof AbstractDynamicConstantPoolEntry d) {
                return this.tag() == d.tag()
                && bootstrap().equals(d.bootstrap())
                && nameAndType.equals(d.nameAndType());
            }
            return false;
        }
    }

    public static final class InvokeDynamicEntryImpl
            extends AbstractDynamicConstantPoolEntry
            implements InvokeDynamicEntry {

        public @Stable DynamicCallSiteDesc sym;

        InvokeDynamicEntryImpl(ConstantPool cpm, int index, int hash, BootstrapMethodEntryImpl bootstrapMethod,
                                   NameAndTypeEntryImpl nameAndType) {
            super(cpm, index, hash, bootstrapMethod, nameAndType);
        }

        InvokeDynamicEntryImpl(ConstantPool cpm, int index, int bsmIndex,
                                   NameAndTypeEntryImpl nameAndType) {
            super(cpm, index, hash2(TAG_INVOKE_DYNAMIC, bsmIndex, nameAndType.index()),
                  bsmIndex, nameAndType);
        }

        @Override
        public int tag() {
            return TAG_INVOKE_DYNAMIC;
        }

        @Override
        public InvokeDynamicEntry clone(ConstantPoolBuilder cp) {
            var ret = (InvokeDynamicEntryImpl) cp.invokeDynamicEntry(bootstrap(), nameAndType());
            var mySym = this.sym;
            if (ret.sym == null && mySym != null)
                ret.sym = mySym;
            return ret;
        }

        @Override
        public DynamicCallSiteDesc asSymbol() {
            var cache = this.sym;
            if (cache != null)
                return cache;
            return this.sym = InvokeDynamicEntry.super.asSymbol();
        }
    }

    public static final class ConstantDynamicEntryImpl extends AbstractDynamicConstantPoolEntry
            implements ConstantDynamicEntry {

        public @Stable DynamicConstantDesc<?> sym;

        ConstantDynamicEntryImpl(ConstantPool cpm, int index, int hash, BootstrapMethodEntryImpl bootstrapMethod,
                                     NameAndTypeEntryImpl nameAndType) {
            super(cpm, index, hash, bootstrapMethod, nameAndType);
        }

        ConstantDynamicEntryImpl(ConstantPool cpm, int index, int bsmIndex,
                                     NameAndTypeEntryImpl nameAndType) {
            super(cpm, index, hash2(TAG_DYNAMIC, bsmIndex, nameAndType.index()),
                  bsmIndex, nameAndType);
        }

        @Override
        public int tag() {
            return TAG_DYNAMIC;
        }

        @Override
        public ConstantDynamicEntry clone(ConstantPoolBuilder cp) {
            var ret = (ConstantDynamicEntryImpl) cp.constantDynamicEntry(bootstrap(), nameAndType());
            var mySym = this.sym;
            if (ret.sym == null && mySym != null)
                ret.sym = mySym;
            return ret;
        }

        @Override
        public DynamicConstantDesc<?> asSymbol() {
            var cache = this.sym;
            if (cache != null)
                return cache;
            return this.sym = ConstantDynamicEntry.super.asSymbol();
        }
    }

    public static final class MethodHandleEntryImpl extends AbstractPoolEntry
            implements MethodHandleEntry {

        private final int refKind;
        private final AbstractPoolEntry.AbstractMemberRefEntry reference;
        public @Stable DirectMethodHandleDesc sym;

        MethodHandleEntryImpl(ConstantPool cpm, int index, int hash, int refKind, AbstractPoolEntry.AbstractMemberRefEntry
                reference) {
            super(cpm, index, hash);
            this.refKind = refKind;
            this.reference = reference;
        }

        MethodHandleEntryImpl(ConstantPool cpm, int index, int refKind, AbstractPoolEntry.AbstractMemberRefEntry
                reference) {
            super(cpm, index, hash2(TAG_METHOD_HANDLE, refKind, reference.index()));
            this.refKind = refKind;
            this.reference = reference;
        }

        @Override
        public int tag() {
            return TAG_METHOD_HANDLE;
        }

        @Override
        public int kind() {
            return refKind;
        }

        @Override
        public AbstractPoolEntry.AbstractMemberRefEntry reference() {
            return reference;
        }

        @Override
        public DirectMethodHandleDesc asSymbol() {
            var cache = this.sym;
            if (cache != null)
                return cache;
            return computeSymbol();
        }

        private DirectMethodHandleDesc computeSymbol() {
            return this.sym = MethodHandleDesc.of(
                    DirectMethodHandleDesc.Kind.valueOf(kind(), reference() instanceof InterfaceMethodRefEntry),
                    ((MemberRefEntry) reference()).owner().asSymbol(),
                    ((MemberRefEntry) reference()).nameAndType().name().stringValue(),
                    ((MemberRefEntry) reference()).nameAndType().type().stringValue());
        }

        @Override
        void writeTo(BufWriterImpl pool) {
            pool.writeU1U1U2(TAG_METHOD_HANDLE, refKind, reference.index());
        }

        @Override
        public MethodHandleEntry clone(ConstantPoolBuilder cp) {
            var ret = (MethodHandleEntryImpl) cp.methodHandleEntry(refKind, reference);
            var mySym = this.sym;
            if (ret.sym == null && mySym != null)
                ret.sym = mySym;
            return ret;
        }

        @Override
        public String toString() {
            return tag() + " " + kind() + ":" + ((MemberRefEntry) reference()).owner().asInternalName() + "." + ((MemberRefEntry) reference()).nameAndType().name().stringValue()
                   + "-" + ((MemberRefEntry) reference()).nameAndType().type().stringValue();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o instanceof MethodHandleEntryImpl m) {
                return kind() == m.kind()
                && reference.equals(m.reference());
            }
            return false;
        }
    }

    public static final class MethodTypeEntryImpl
            extends AbstractRefEntry<Utf8EntryImpl>
            implements MethodTypeEntry {

        MethodTypeEntryImpl(ConstantPool cpm, int index, Utf8EntryImpl descriptor) {
            super(cpm, TAG_METHOD_TYPE, index, descriptor);
        }

        @Override
        public int tag() {
            return TAG_METHOD_TYPE;
        }

        @Override
        public Utf8Entry descriptor() {
            return ref1;
        }

        @Override
        public MethodTypeEntry clone(ConstantPoolBuilder cp) {
            return cp.methodTypeEntry(ref1);
        }

        @Override
        public MethodTypeDesc asSymbol() {
            return ref1.methodTypeSymbol();
        }

        @Override
        public boolean matches(MethodTypeDesc desc) {
            return ref1.isMethodType(desc);
        }

        @Override
        public boolean equals(Object o) {
            if (o == this) return true;
            if (o instanceof MethodTypeEntryImpl m) {
                return descriptor().equals(m.descriptor());
            }
            return false;
        }
    }

    public static final class StringEntryImpl
            extends AbstractRefEntry<Utf8EntryImpl>
            implements StringEntry {

        StringEntryImpl(ConstantPool cpm, int index, Utf8EntryImpl utf8) {
            super(cpm, TAG_STRING, index, utf8);
        }

        @Override
        public int tag() {
            return TAG_STRING;
        }

        @Override
        public Utf8EntryImpl utf8() {
            return ref1;
        }

        @Override
        public String stringValue() {
            return ref1.toString();
        }

        @Override
        public boolean equalsString(String value) {
            return ref1.equalsString(value);
        }

        @Override
        public ConstantDesc constantValue() {
            return stringValue();
        }

        @Override
        public StringEntry clone(ConstantPoolBuilder cp) {
            return cp.stringEntry(ref1);
        }

        @Override
        public String toString() {
            return tag() + " \"" + stringValue() + "\"";
        }

        @Override
        public boolean equals(Object o) {
            if (o == this) return true;
            if (o instanceof StringEntryImpl s) {
                // check utf8 rather allocating a string
                return utf8().equals(s.utf8());
            }
            return false;
        }


    }

    public static final class IntegerEntryImpl extends AbstractPoolEntry implements IntegerEntry {

        private final int val;

        IntegerEntryImpl(ConstantPool cpm, int index, int i) {
            super(cpm, index, hash1(TAG_INTEGER, Integer.hashCode(i)));
            val = i;
        }

        @Override
        public int tag() {
            return TAG_INTEGER;
        }

        @Override
        void writeTo(BufWriterImpl pool) {
            pool.writeU1(TAG_INTEGER);
            pool.writeInt(val);
        }

        @Override
        public IntegerEntry clone(ConstantPoolBuilder cp) {
            return cp.intEntry(val);
        }

        @Override
        public int intValue() {
            return val;
        }

        @Override
        public ConstantDesc constantValue() {
            return val;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o instanceof IntegerEntryImpl e) {
                return intValue() == e.intValue();
            }
            return false;
        }
    }

    public static final class FloatEntryImpl extends AbstractPoolEntry
            implements FloatEntry {

        private final float val;

        FloatEntryImpl(ConstantPool cpm, int index, float f) {
            super(cpm, index, hash1(TAG_FLOAT, Float.hashCode(f)));
            val = f;
        }

        @Override
        public int tag() {
            return TAG_FLOAT;
        }

        @Override
        void writeTo(BufWriterImpl pool) {
            pool.writeU1(TAG_FLOAT);
            pool.writeFloat(val);
        }

        @Override
        public FloatEntry clone(ConstantPoolBuilder cp) {
            return cp.floatEntry(val);
        }

        @Override
        public float floatValue() {
            return val;
        }

        @Override
        public ConstantDesc constantValue() {
            return val;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o instanceof FloatEntryImpl e) {
                return floatValue() == e.floatValue();
            }
            return false;
        }
    }

    public static final class LongEntryImpl extends AbstractPoolEntry implements LongEntry {

        private final long val;

        LongEntryImpl(ConstantPool cpm, int index, long l) {
            super(cpm, index, hash1(TAG_LONG, Long.hashCode(l)));
            val = l;
        }

        @Override
        public int tag() {
            return TAG_LONG;
        }

        @Override
        public int width() {
            return 2;
        }

        @Override
        void writeTo(BufWriterImpl pool) {
            pool.writeU1(TAG_LONG);
            pool.writeLong(val);
        }

        @Override
        public LongEntry clone(ConstantPoolBuilder cp) {
            return cp.longEntry(val);
        }

        @Override
        public long longValue() {
            return val;
        }

        @Override
        public ConstantDesc constantValue() {
            return val;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o instanceof LongEntryImpl e) {
                return longValue() == e.longValue();
            }
            return false;
        }
    }

    public static final class DoubleEntryImpl extends AbstractPoolEntry implements DoubleEntry {

        private final double val;

        DoubleEntryImpl(ConstantPool cpm, int index, double d) {
            super(cpm, index, hash1(TAG_DOUBLE, Double.hashCode(d)));
            val = d;
        }

        @Override
        public int tag() {
            return TAG_DOUBLE;
        }

        @Override
        public int width() {
            return 2;
        }

        @Override
        void writeTo(BufWriterImpl pool) {
            pool.writeU1(TAG_DOUBLE);
            pool.writeDouble(val);
        }

        @Override
        public DoubleEntry clone(ConstantPoolBuilder cp) {
            return cp.doubleEntry(val);
        }

        @Override
        public double doubleValue() {
            return val;
        }

        @Override
        public ConstantDesc constantValue() {
            return val;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o instanceof DoubleEntryImpl e) {
                return doubleValue() == e.doubleValue();
            }
            return false;
        }
    }
}
