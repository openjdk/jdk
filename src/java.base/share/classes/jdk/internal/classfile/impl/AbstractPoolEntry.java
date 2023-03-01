/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.constant.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import jdk.internal.classfile.Classfile;
import jdk.internal.classfile.constantpool.ClassEntry;
import jdk.internal.classfile.constantpool.ConstantDynamicEntry;
import jdk.internal.classfile.constantpool.ConstantPool;
import jdk.internal.classfile.constantpool.ConstantPoolBuilder;
import jdk.internal.classfile.BufWriter;
import jdk.internal.classfile.constantpool.DoubleEntry;
import jdk.internal.classfile.constantpool.FieldRefEntry;
import jdk.internal.classfile.constantpool.FloatEntry;
import jdk.internal.classfile.constantpool.IntegerEntry;
import jdk.internal.classfile.constantpool.InterfaceMethodRefEntry;
import jdk.internal.classfile.constantpool.InvokeDynamicEntry;
import jdk.internal.classfile.constantpool.LoadableConstantEntry;
import jdk.internal.classfile.constantpool.LongEntry;
import jdk.internal.classfile.constantpool.MemberRefEntry;
import jdk.internal.classfile.constantpool.MethodHandleEntry;
import jdk.internal.classfile.constantpool.MethodRefEntry;
import jdk.internal.classfile.constantpool.MethodTypeEntry;
import jdk.internal.classfile.constantpool.ModuleEntry;
import jdk.internal.classfile.constantpool.NameAndTypeEntry;
import jdk.internal.classfile.constantpool.PackageEntry;
import jdk.internal.classfile.constantpool.PoolEntry;
import jdk.internal.classfile.constantpool.StringEntry;
import jdk.internal.classfile.constantpool.Utf8Entry;
import jdk.internal.classfile.jdktypes.ModuleDesc;
import jdk.internal.classfile.jdktypes.PackageDesc;

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
    private static final int INT_PHI = 0x9E3779B9;

    public static int hash1(int tag, int x1) {
        return phiMix(tag * TAG_SMEAR + x1);
    }

    public static int hash2(int tag, int x1, int x2) {
        return phiMix(tag * TAG_SMEAR + x1 + 31*x2);
    }

    // Ensure that hash is never zero
    public static int hashString(int stringHash) {
        return phiMix(stringHash | (1 << 30));
    }

    public static int phiMix(int x) {
        int h = x * INT_PHI;
        return h ^ (h >> 16);
    }

    public static Utf8Entry rawUtf8EntryFromStandardAttributeName(String name) {
        //assuming standard attribute names are all US_ASCII
        var raw = name.getBytes(StandardCharsets.US_ASCII);
        return new ConcreteUtf8Entry(null, 0, raw, 0, raw.length);
    }

    @SuppressWarnings("unchecked")
    public static <T extends PoolEntry> T maybeClone(ConstantPoolBuilder cp, T entry) {
        return (T)((AbstractPoolEntry)entry).clone(cp);
    }

    final ConstantPool constantPool;
    public final byte tag;
    private final int index;
    private final int hash;

    private AbstractPoolEntry(ConstantPool constantPool, int tag, int index, int hash) {
        this.tag = (byte) tag;
        this.index = index;
        this.hash = hash;
        this.constantPool = constantPool;
    }

    public ConstantPool constantPool() { return constantPool; }

    public int index() { return index; }

    public int hashCode() {
        return hash;
    }

    public byte tag() {
        return tag;
    }

    public int width() {
        return (tag == Classfile.TAG_LONG || tag == Classfile.TAG_DOUBLE) ? 2 : 1;
    }

    abstract PoolEntry clone(ConstantPoolBuilder cp);

    public static final class ConcreteUtf8Entry extends AbstractPoolEntry implements Utf8Entry {
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

        private State state;
        private final byte[] rawBytes; // null if initialized directly from a string
        private final int offset;
        private final int rawLen;
        // Set in any state other than RAW
        private int hash;
        private int charLen;
        // Set in CHAR state
        private char[] chars;
        // Only set in STRING state
        private String stringValue;

        ConcreteUtf8Entry(ConstantPool cpm, int index,
                          byte[] rawBytes, int offset, int rawLen) {
            super(cpm, Classfile.TAG_UTF8, index, 0);
            this.rawBytes = rawBytes;
            this.offset = offset;
            this.rawLen = rawLen;
            this.state = State.RAW;
        }

        ConcreteUtf8Entry(ConstantPool cpm, int index, String s) {
            super(cpm, Classfile.TAG_UTF8, index, 0);
            this.rawBytes = null;
            this.offset = 0;
            this.rawLen = 0;
            this.state = State.STRING;
            this.stringValue = s;
            this.charLen = s.length();
            this.hash = hashString(s.hashCode());
        }

        ConcreteUtf8Entry(ConstantPool cpm, int index, ConcreteUtf8Entry u) {
            super(cpm, Classfile.TAG_UTF8, index, 0);
            this.rawBytes = u.rawBytes;
            this.offset = u.offset;
            this.rawLen = u.rawLen;
            this.state = u.state;
            this.hash = u.hash;
            this.charLen = u.charLen;
            this.chars = u.chars;
            this.stringValue = u.stringValue;
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
            int hash = 0;
            boolean foundHigh = false;

            int px = offset;
            int utfend = px + rawLen;
            while (px < utfend) {
                int c = (int) rawBytes[px] & 0xff;
                if (c > 127) {
                    foundHigh = true;
                    break;
                }
                hash = 31 * hash + c;
                px++;
            }

            if (!foundHigh) {
                this.hash = hashString(hash);
                charLen = rawLen;
                state = State.BYTE;
            }
            else {
                char[] chararr = new char[rawLen];
                int chararr_count = 0;
                // Inflate prefix of bytes to characters
                for (int i = offset; i < px; i++) {
                    int c = (int) rawBytes[i] & 0xff;
                    chararr[chararr_count++] = (char) c;
                }
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
                                throw new CpException("malformed input: partial character at end");
                            }
                            int char2 = rawBytes[px - 1];
                            if ((char2 & 0xC0) != 0x80) {
                                throw new CpException("malformed input around byte " + px);
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
                                throw new CpException("malformed input: partial character at end");
                            }
                            int char2 = rawBytes[px - 2];
                            int char3 = rawBytes[px - 1];
                            if (((char2 & 0xC0) != 0x80) || ((char3 & 0xC0) != 0x80)) {
                                throw new CpException("malformed input around byte " + (px - 1));
                            }
                            char v = (char) (((c & 0x0F) << 12) | ((char2 & 0x3F) << 6) | ((char3 & 0x3F) << 0));
                            chararr[chararr_count++] = v;
                            hash = 31 * hash + v;
                            break;
                        }
                        default:
                            // 10xx xxxx,  1111 xxxx
                            throw new CpException("malformed input around byte " + px);
                    }
                }
                this.hash = hashString(hash);
                charLen = chararr_count;
                this.chars = chararr;
                state = State.CHAR;
            }

        }

        @Override
        public ConcreteUtf8Entry clone(ConstantPoolBuilder cp) {
            if (cp.canWriteDirect(constantPool))
                return this;
            return (state == State.STRING && rawBytes == null)
                   ? (ConcreteUtf8Entry) cp.utf8Entry(stringValue)
                   : ((SplitConstantPool) cp).maybeCloneUtf8Entry(this);
        }

        @Override
        public int hashCode() {
            if (state == State.RAW)
                inflate();
            return hash;
        }

        @Override
        public String toString() {
            if (state == State.RAW)
                inflate();
            if (state != State.STRING) {
                stringValue = (chars != null)
                              ? new String(chars, 0, charLen)
                              : new String(rawBytes, offset, charLen, StandardCharsets.UTF_8);
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
            if (o instanceof ConcreteUtf8Entry u) {
                return equalsUtf8(u);
            } else if (o instanceof Utf8Entry u) {
                return equalsString(u.stringValue());
            }
            return false;
        }

        public boolean equalsUtf8(ConcreteUtf8Entry u) {
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
                    return stringValue.equals(s);
                case CHAR:
                    if (charLen != s.length() || hash != hashString(s.hashCode()))
                        return false;
                    for (int i=0; i<charLen; i++)
                        if (chars[i] != s.charAt(i))
                            return false;
                    stringValue = s;
                    state = State.STRING;
                    return true;
                case BYTE:
                    if (rawLen != s.length() || hash != hashString(s.hashCode()))
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
        public void writeTo(BufWriter pool) {
            if (rawBytes != null) {
                pool.writeU1(tag);
                pool.writeU2(rawLen);
                pool.writeBytes(rawBytes, offset, rawLen);
            }
            else {
                // state == STRING and no raw bytes
                if (stringValue.length() > 65535) {
                    throw new IllegalArgumentException("string too long");
                }
                pool.writeU1(tag);
                pool.writeU2(charLen);
                for (int i = 0; i < charLen; ++i) {
                    char c = stringValue.charAt(i);
                    if (c >= '\001' && c <= '\177') {
                        // Optimistic writing -- hope everything is bytes
                        // If not, we bail out, and alternate path patches the length
                        pool.writeU1((byte) c);
                    }
                    else {
                        int charLength = stringValue.length();
                        int byteLength = i;
                        char c1;
                        for (int j = i; j < charLength; ++j) {
                            c1 = (stringValue).charAt(j);
                            if (c1 >= '\001' && c1 <= '\177') {
                                byteLength++;
                            } else if (c1 > '\u07FF') {
                                byteLength += 3;
                            } else {
                                byteLength += 2;
                            }
                        }
                        if (byteLength > 65535) {
                            throw new IllegalArgumentException();
                        }
                        int byteLengthFinal = byteLength;
                        pool.patchInt(pool.size() - i - 2, 2, byteLengthFinal);
                        for (int j = i; j < charLength; ++j) {
                            c1 = (stringValue).charAt(j);
                            if (c1 >= '\001' && c1 <= '\177') {
                                pool.writeU1((byte) c1);
                            } else if (c1 > '\u07FF') {
                                pool.writeU1((byte) (0xE0 | c1 >> 12 & 0xF));
                                pool.writeU1((byte) (0x80 | c1 >> 6 & 0x3F));
                                pool.writeU1((byte) (0x80 | c1 & 0x3F));
                            } else {
                                pool.writeU1((byte) (0xC0 | c1 >> 6 & 0x1F));
                                pool.writeU1((byte) (0x80 | c1 & 0x3F));
                            }
                        }
                        break;
                    }
                }
            }
        }
    }

    static abstract sealed class AbstractRefEntry<T extends PoolEntry> extends AbstractPoolEntry {
        protected final T ref1;

        public AbstractRefEntry(ConstantPool constantPool, int tag, int index, T ref1) {
            super(constantPool, tag, index, hash1(tag, ref1.index()));
            this.ref1 = ref1;
        }

        public T ref1() {
            return ref1;
        }

        public void writeTo(BufWriter pool) {
            pool.writeU1(tag);
            pool.writeU2(ref1.index());
        }

        public String toString() {
            return tag() + " " + ref1();
        }
    }

    static abstract sealed class AbstractRefsEntry<T extends PoolEntry, U extends PoolEntry>
            extends AbstractPoolEntry {
        protected final T ref1;
        protected final U ref2;

        public AbstractRefsEntry(ConstantPool constantPool, int tag, int index, T ref1, U ref2) {
            super(constantPool, tag, index, hash2(tag, ref1.index(), ref2.index()));
            this.ref1 = ref1;
            this.ref2 = ref2;
        }

        public T ref1() {
            return ref1;
        }

        public U ref2() {
            return ref2;
        }

        public void writeTo(BufWriter pool) {
            pool.writeU1(tag);
            pool.writeU2(ref1.index());
            pool.writeU2(ref2.index());
        }

        @Override
        public String toString() {
            return tag() + " " + ref1 + "-" + ref2;
        }
    }

    static abstract sealed class AbstractNamedEntry extends AbstractRefEntry<ConcreteUtf8Entry> {

        public AbstractNamedEntry(ConstantPool constantPool, int tag, int index, ConcreteUtf8Entry ref1) {
            super(constantPool, tag, index, ref1);
        }

        public Utf8Entry name() {
            return ref1;
        }

        public String asInternalName() {
            return ref1.stringValue();
        }

        public boolean equals(Object o) {
            if (o == this) { return true; }
            if (o instanceof AbstractNamedEntry ne) {
                return tag == ne.tag() && name().equals(ref1());
            }
            return false;
        }
    }

    public static final class ConcreteClassEntry extends AbstractNamedEntry implements ClassEntry {

        ConcreteClassEntry(ConstantPool cpm, int index, ConcreteUtf8Entry name) {
            super(cpm, Classfile.TAG_CLASS, index, name);
        }

        @Override
        public ClassEntry clone(ConstantPoolBuilder cp) {
            return cp.canWriteDirect(constantPool) ? this : cp.classEntry(ref1);
        }

        @Override
        public ClassDesc asSymbol() {
            return Util.toClassDesc(asInternalName());
        }

        @Override
        public boolean equals(Object o) {
            if (o == this) return true;
            if (o instanceof ConcreteClassEntry cce) {
                return cce.name().equals(this.name());
            } else if (o instanceof ClassEntry c) {
                return c.asSymbol().equals(this.asSymbol());
            }
            return false;
        }
    }

    public static final class ConcretePackageEntry extends AbstractNamedEntry implements PackageEntry {

        ConcretePackageEntry(ConstantPool cpm, int index, ConcreteUtf8Entry name) {
            super(cpm, Classfile.TAG_PACKAGE, index, name);
        }

        @Override
        public PackageEntry clone(ConstantPoolBuilder cp) {
            return cp.canWriteDirect(constantPool) ? this : cp.packageEntry(ref1);
        }

        @Override
        public PackageDesc asSymbol() {
            return PackageDesc.ofInternalName(asInternalName());
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

    public static final class ConcreteModuleEntry extends AbstractNamedEntry implements ModuleEntry {

        ConcreteModuleEntry(ConstantPool cpm, int index, ConcreteUtf8Entry name) {
            super(cpm, Classfile.TAG_MODULE, index, name);
        }

        @Override
        public ModuleEntry clone(ConstantPoolBuilder cp) {
            return cp.canWriteDirect(constantPool) ? this : cp.moduleEntry(ref1);
        }

        @Override
        public ModuleDesc asSymbol() {
            return ModuleDesc.of(asInternalName());
        }

        @Override
        public boolean equals(Object o) {
            if (o == this) return true;
            if (o instanceof ConcreteModuleEntry m) {
                return name().equals(m.name());
            }
            return false;
        }
    }

    public static final class ConcreteNameAndTypeEntry extends AbstractRefsEntry<ConcreteUtf8Entry, ConcreteUtf8Entry>
            implements NameAndTypeEntry {

        ConcreteNameAndTypeEntry(ConstantPool cpm, int index, ConcreteUtf8Entry name, ConcreteUtf8Entry type) {
            super(cpm, Classfile.TAG_NAMEANDTYPE, index, name, type);
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
            return cp.canWriteDirect(constantPool) ? this : cp.natEntry(ref1, ref2);
        }

        @Override
        public boolean equals(Object o) {
            if (o == this) return true;
            if (o instanceof ConcreteNameAndTypeEntry nat) {
                return name().equals(nat.name()) && type().equals(nat.type());
            }
            return false;
        }
    }

    public static abstract sealed class AbstractMemberRefEntry
            extends AbstractRefsEntry<ConcreteClassEntry, ConcreteNameAndTypeEntry>
            implements MemberRefEntry {

        AbstractMemberRefEntry(ConstantPool cpm, int tag, int index, ConcreteClassEntry owner,
                       ConcreteNameAndTypeEntry nameAndType) {
            super(cpm, tag, index, owner, nameAndType);
        }

        @Override
        public ConcreteClassEntry owner() {
            return ref1;
        }

        @Override
        public ConcreteNameAndTypeEntry nameAndType() {
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
                return tag == m.tag()
                && owner().equals(m.owner())
                && nameAndType().equals(m.nameAndType());
            }
            return false;
        }
    }

    public static final class ConcreteFieldRefEntry extends AbstractMemberRefEntry implements FieldRefEntry {

        ConcreteFieldRefEntry(ConstantPool cpm, int index,
                              ConcreteClassEntry owner, ConcreteNameAndTypeEntry nameAndType) {
            super(cpm, Classfile.TAG_FIELDREF, index, owner, nameAndType);
        }

        @Override
        public FieldRefEntry clone(ConstantPoolBuilder cp) {
            return cp.canWriteDirect(constantPool) ? this : cp.fieldRefEntry(ref1, ref2);
        }
    }

    public static final class ConcreteMethodRefEntry extends AbstractMemberRefEntry implements MethodRefEntry {

        ConcreteMethodRefEntry(ConstantPool cpm, int index,
                               ConcreteClassEntry owner, ConcreteNameAndTypeEntry nameAndType) {
            super(cpm, Classfile.TAG_METHODREF, index, owner, nameAndType);
        }

        @Override
        public MethodRefEntry clone(ConstantPoolBuilder cp) {
            return cp.canWriteDirect(constantPool) ? this : cp.methodRefEntry(ref1, ref2);
        }
    }

    public static final class ConcreteInterfaceMethodRefEntry extends AbstractMemberRefEntry implements InterfaceMethodRefEntry {

        ConcreteInterfaceMethodRefEntry(ConstantPool cpm, int index, ConcreteClassEntry owner,
                                        ConcreteNameAndTypeEntry nameAndType) {
            super(cpm, Classfile.TAG_INTERFACEMETHODREF, index, owner, nameAndType);
        }

        @Override
        public InterfaceMethodRefEntry clone(ConstantPoolBuilder cp) {
            return cp.canWriteDirect(constantPool) ? this : cp.interfaceMethodRefEntry(ref1, ref2);
        }
    }

    public static abstract sealed class AbstractDynamicConstantPoolEntry extends AbstractPoolEntry {

        private final int bsmIndex;
        private BootstrapMethodEntryImpl bootstrapMethod;
        private final ConcreteNameAndTypeEntry nameAndType;

        AbstractDynamicConstantPoolEntry(ConstantPool cpm, int tag, int index, int hash, BootstrapMethodEntryImpl bootstrapMethod,
                                         ConcreteNameAndTypeEntry nameAndType) {
            super(cpm, tag, index, hash);
            this.bsmIndex = bootstrapMethod.bsmIndex();
            this.bootstrapMethod = bootstrapMethod;
            this.nameAndType = nameAndType;
        }

        AbstractDynamicConstantPoolEntry(ConstantPool cpm, int tag, int index, int hash, int bsmIndex,
                                         ConcreteNameAndTypeEntry nameAndType) {
            super(cpm, tag, index, hash);
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
         * @return the nameAndType
         */
        public ConcreteNameAndTypeEntry nameAndType() {
            return nameAndType;
        }

        public void writeTo(BufWriter pool) {
            pool.writeU1(tag);
            pool.writeU2(bsmIndex);
            pool.writeU2(nameAndType.index());
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

    public static final class ConcreteInvokeDynamicEntry
            extends AbstractDynamicConstantPoolEntry
            implements InvokeDynamicEntry {

        ConcreteInvokeDynamicEntry(ConstantPool cpm, int index, int hash, BootstrapMethodEntryImpl bootstrapMethod,
                                   ConcreteNameAndTypeEntry nameAndType) {
            super(cpm, Classfile.TAG_INVOKEDYNAMIC, index, hash, bootstrapMethod, nameAndType);
        }

        ConcreteInvokeDynamicEntry(ConstantPool cpm, int index, int bsmIndex,
                                   ConcreteNameAndTypeEntry nameAndType) {
            super(cpm, Classfile.TAG_INVOKEDYNAMIC, index, hash2(Classfile.TAG_INVOKEDYNAMIC, bsmIndex, nameAndType.index()),
                  bsmIndex, nameAndType);
        }

        @Override
        public InvokeDynamicEntry clone(ConstantPoolBuilder cp) {
            return cp.canWriteDirect(constantPool) ? this : cp.invokeDynamicEntry(bootstrap(), nameAndType());
        }
    }

    public static final class ConcreteConstantDynamicEntry extends AbstractDynamicConstantPoolEntry
            implements ConstantDynamicEntry {

        ConcreteConstantDynamicEntry(ConstantPool cpm, int index, int hash, BootstrapMethodEntryImpl bootstrapMethod,
                                     ConcreteNameAndTypeEntry nameAndType) {
            super(cpm, Classfile.TAG_CONSTANTDYNAMIC, index, hash, bootstrapMethod, nameAndType);
        }

        ConcreteConstantDynamicEntry(ConstantPool cpm, int index, int bsmIndex,
                                     ConcreteNameAndTypeEntry nameAndType) {
            super(cpm, Classfile.TAG_CONSTANTDYNAMIC, index, hash2(Classfile.TAG_CONSTANTDYNAMIC, bsmIndex, nameAndType.index()),
                  bsmIndex, nameAndType);
        }

        @Override
        public ConstantDynamicEntry clone(ConstantPoolBuilder cp) {
            return cp.canWriteDirect(constantPool) ? this : cp.constantDynamicEntry(bootstrap(), nameAndType());
        }

        @Override
        public ConstantDesc constantValue() {
            List<LoadableConstantEntry> args = bootstrap().arguments();
            int argsSize =  args.size();
            ConstantDesc[] staticArgs = new ConstantDesc[argsSize];
            for (int i = 0; i < argsSize; i++)
                staticArgs[i] = args.get(i).constantValue();

            return DynamicConstantDesc.ofCanonical(bootstrap().bootstrapMethod().asSymbol(),
                                                   nameAndType().name().stringValue(), ClassDesc.ofDescriptor(nameAndType().type().stringValue()), staticArgs);
        }
    }

    public static final class ConcreteMethodHandleEntry extends AbstractPoolEntry
            implements MethodHandleEntry {

        private final int refKind;
        private final AbstractPoolEntry.AbstractMemberRefEntry reference;

        ConcreteMethodHandleEntry(ConstantPool cpm, int index, int hash, int refKind, AbstractPoolEntry.AbstractMemberRefEntry
                reference) {
            super(cpm, Classfile.TAG_METHODHANDLE, index, hash);
            this.refKind = refKind;
            this.reference = reference;
        }

        ConcreteMethodHandleEntry(ConstantPool cpm, int index, int refKind, AbstractPoolEntry.AbstractMemberRefEntry
                reference) {
            super(cpm, Classfile.TAG_METHODHANDLE, index, hash2(Classfile.TAG_METHODHANDLE, refKind, reference.index()));
            this.refKind = refKind;
            this.reference = reference;
        }

        @Override
        public int kind() {
            return refKind;
        }

        @Override
        public ConstantDesc constantValue() {
            return asSymbol();
        }

        @Override
        public AbstractPoolEntry.AbstractMemberRefEntry reference() {
            return reference;
        }

        @Override
        public DirectMethodHandleDesc asSymbol() {
            return MethodHandleDesc.of(
                    DirectMethodHandleDesc.Kind.valueOf(kind(), reference() instanceof InterfaceMethodRefEntry),
                    ((MemberRefEntry) reference()).owner().asSymbol(),
                    ((MemberRefEntry) reference()).nameAndType().name().stringValue(),
                    ((MemberRefEntry) reference()).nameAndType().type().stringValue());
        }

        @Override
        public void writeTo(BufWriter pool) {
            pool.writeU1(tag);
            pool.writeU1(refKind);
            pool.writeU2(reference.index());
        }

        @Override
        public MethodHandleEntry clone(ConstantPoolBuilder cp) {
            return cp.canWriteDirect(constantPool) ? this : cp.methodHandleEntry(refKind, reference);
        }

        @Override
        public String toString() {
            return tag() + " " + kind() + ":" + ((MemberRefEntry) reference()).owner().asInternalName() + "." + ((MemberRefEntry) reference()).nameAndType().name().stringValue()
                   + "-" + ((MemberRefEntry) reference()).nameAndType().type().stringValue();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o instanceof ConcreteMethodHandleEntry m) {
                return kind() == m.kind()
                && reference.equals(m.reference());
            }
            return false;
        }
    }

    public static final class ConcreteMethodTypeEntry
            extends AbstractRefEntry<ConcreteUtf8Entry>
            implements MethodTypeEntry {

        ConcreteMethodTypeEntry(ConstantPool cpm, int index, ConcreteUtf8Entry descriptor) {
            super(cpm, Classfile.TAG_METHODTYPE, index, descriptor);
        }

        public Utf8Entry descriptor() {
            return ref1;
        }

        @Override
        public ConstantDesc constantValue() {
            return MethodTypeDesc.ofDescriptor(descriptor().stringValue());
        }

        @Override
        public MethodTypeEntry clone(ConstantPoolBuilder cp) {
            return cp.canWriteDirect(constantPool) ? this : cp.methodTypeEntry(ref1);
        }

        @Override
        public boolean equals(Object o) {
            if (o == this) return true;
            if (o instanceof ConcreteMethodTypeEntry m) {
                return descriptor().equals(m.descriptor());
            }
            return false;
        }
    }

    public static final class ConcreteStringEntry
            extends AbstractRefEntry<ConcreteUtf8Entry>
            implements StringEntry {

        ConcreteStringEntry(ConstantPool cpm, int index, ConcreteUtf8Entry utf8) {
            super(cpm, Classfile.TAG_STRING, index, utf8);
        }

        @Override
        public ConcreteUtf8Entry utf8() {
            return ref1;
        }

        @Override
        public String stringValue() {
            return ref1.toString();
        }

        @Override
        public ConstantDesc constantValue() {
            return stringValue();
        }

        @Override
        public StringEntry clone(ConstantPoolBuilder cp) {
            return cp.canWriteDirect(constantPool) ? this : cp.stringEntry(ref1);
        }

        @Override
        public String toString() {
            return tag() + " \"" + stringValue() + "\"";
        }

        @Override
        public boolean equals(Object o) {
            if (o == this) return true;
            if (o instanceof ConcreteStringEntry s) {
                // check utf8 rather allocating a string
                return utf8().equals(s.utf8());
            }
            return false;
        }


    }

    static abstract sealed class PrimitiveEntry<T extends ConstantDesc>
            extends AbstractPoolEntry {
        protected final T val;

        public PrimitiveEntry(ConstantPool constantPool, int tag, int index, T val) {
            super(constantPool, tag, index, hash1(tag, val.hashCode()));
            this.val = val;
        }

        public T value() {
            return val;
        }

        public ConstantDesc constantValue() {
            return value();
        }

        @Override
        public String toString() {
            return "" + tag() + value();
        }
    }

    public static final class ConcreteIntegerEntry extends PrimitiveEntry<Integer>
            implements IntegerEntry {

        ConcreteIntegerEntry(ConstantPool cpm, int index, int i) {
            super(cpm, Classfile.TAG_INTEGER, index, i);
        }

        @Override
        public void writeTo(BufWriter pool) {
            pool.writeU1(tag);
            pool.writeInt(val);
        }

        @Override
        public IntegerEntry clone(ConstantPoolBuilder cp) {
            return cp.canWriteDirect(constantPool) ? this : cp.intEntry(val);
        }

        @Override
        public int intValue() {
            return value();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o instanceof ConcreteIntegerEntry e) {
                return intValue() == e.intValue();
            }
            return false;
        }
    }

    public static final class ConcreteFloatEntry extends PrimitiveEntry<Float>
            implements FloatEntry {

        ConcreteFloatEntry(ConstantPool cpm, int index, float f) {
            super(cpm, Classfile.TAG_FLOAT, index, f);
        }

        @Override
        public void writeTo(BufWriter pool) {
            pool.writeU1(tag);
            pool.writeFloat(val);
        }

        @Override
        public FloatEntry clone(ConstantPoolBuilder cp) {
            return cp.canWriteDirect(constantPool) ? this : cp.floatEntry(val);
        }

        @Override
        public float floatValue() {
            return value();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o instanceof ConcreteFloatEntry e) {
                return floatValue() == e.floatValue();
            }
            return false;
        }
    }

    public static final class ConcreteLongEntry extends PrimitiveEntry<Long> implements LongEntry {

        ConcreteLongEntry(ConstantPool cpm, int index, long l) {
            super(cpm, Classfile.TAG_LONG, index, l);
        }

        @Override
        public void writeTo(BufWriter pool) {
            pool.writeU1(tag);
            pool.writeLong(val);
        }

        @Override
        public LongEntry clone(ConstantPoolBuilder cp) {
            return cp.canWriteDirect(constantPool) ? this : cp.longEntry(val);
        }

        @Override
        public long longValue() {
            return value();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o instanceof ConcreteLongEntry e) {
                return longValue() == e.longValue();
            }
            return false;
        }
    }

    public static final class ConcreteDoubleEntry extends PrimitiveEntry<Double> implements DoubleEntry {

        ConcreteDoubleEntry(ConstantPool cpm, int index, double d) {
            super(cpm, Classfile.TAG_DOUBLE, index, d);
        }

        @Override
        public void writeTo(BufWriter pool) {
            pool.writeU1(tag);
            pool.writeDouble(val);
        }

        @Override
        public DoubleEntry clone(ConstantPoolBuilder cp) {
            return cp.canWriteDirect(constantPool) ? this : cp.doubleEntry(val);
        }

        @Override
        public double doubleValue() {
            return value();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o instanceof ConcreteDoubleEntry e) {
                return doubleValue() == e.doubleValue();
            }
            return false;
        }
    }

    static class CpException extends RuntimeException {
        static final long serialVersionUID = 32L;

        CpException(String s) {
            super(s);
        }
    }
}
