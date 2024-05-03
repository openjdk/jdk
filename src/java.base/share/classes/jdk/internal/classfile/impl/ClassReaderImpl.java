/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import java.lang.classfile.*;
import java.lang.classfile.attribute.BootstrapMethodsAttribute;
import java.lang.classfile.constantpool.*;

import static java.lang.classfile.ClassFile.TAG_CLASS;
import static java.lang.classfile.ClassFile.TAG_CONSTANTDYNAMIC;
import static java.lang.classfile.ClassFile.TAG_DOUBLE;
import static java.lang.classfile.ClassFile.TAG_FIELDREF;
import static java.lang.classfile.ClassFile.TAG_FLOAT;
import static java.lang.classfile.ClassFile.TAG_INTEGER;
import static java.lang.classfile.ClassFile.TAG_INTERFACEMETHODREF;
import static java.lang.classfile.ClassFile.TAG_INVOKEDYNAMIC;
import static java.lang.classfile.ClassFile.TAG_LONG;
import static java.lang.classfile.ClassFile.TAG_METHODHANDLE;
import static java.lang.classfile.ClassFile.TAG_METHODREF;
import static java.lang.classfile.ClassFile.TAG_METHODTYPE;
import static java.lang.classfile.ClassFile.TAG_MODULE;
import static java.lang.classfile.ClassFile.TAG_NAMEANDTYPE;
import static java.lang.classfile.ClassFile.TAG_PACKAGE;
import static java.lang.classfile.ClassFile.TAG_STRING;
import static java.lang.classfile.ClassFile.TAG_UTF8;

public final class ClassReaderImpl
        implements ClassReader {
    static final int CP_ITEM_START = 10;

    private final byte[] buffer;
    private final int metadataStart;
    private final int classfileLength;
    private final Function<Utf8Entry, AttributeMapper<?>> attributeMapper;
    private final int flags;
    private final int thisClassPos;
    private ClassEntry thisClass;
    private Optional<ClassEntry> superclass;
    private final int constantPoolCount;
    private final int[] cpOffset;

    final ClassFileImpl context;
    final int interfacesPos;
    final PoolEntry[] cp;

    private ClassModel containedClass;
    private List<BootstrapMethodEntryImpl> bsmEntries;
    private BootstrapMethodsAttribute bootstrapMethodsAttribute;

    ClassReaderImpl(byte[] classfileBytes,
                    ClassFileImpl context) {
        this.buffer = classfileBytes;
        this.classfileLength = classfileBytes.length;
        this.context = context;
        this.attributeMapper = this.context.attributeMapperOption().attributeMapper();
        if (classfileLength < 4 || readInt(0) != 0xCAFEBABE) {
            throw new IllegalArgumentException("Bad magic number");
        }
        if (readU2(6) > ClassFile.latestMajorVersion()) {
            throw new IllegalArgumentException("Unsupported class file version: " + readU2(6));
        }
        int constantPoolCount = readU2(8);
        int[] cpOffset = new int[constantPoolCount];
        int p = CP_ITEM_START;
        for (int i = 1; i < cpOffset.length; ++i) {
            cpOffset[i] = p;
            int tag = readU1(p);
            ++p;
            switch (tag) {
                // 2
                case TAG_CLASS, TAG_METHODTYPE, TAG_MODULE, TAG_STRING, TAG_PACKAGE -> p += 2;

                // 3
                case TAG_METHODHANDLE -> p += 3;

                // 4
                case TAG_CONSTANTDYNAMIC, TAG_FIELDREF, TAG_FLOAT, TAG_INTEGER,
                     TAG_INTERFACEMETHODREF, TAG_INVOKEDYNAMIC, TAG_METHODREF,
                     TAG_NAMEANDTYPE -> p += 4;

                // 8
                case TAG_DOUBLE, TAG_LONG -> {
                    p += 8;
                    ++i;
                }
                case TAG_UTF8 -> p += 2 + readU2(p);
                default -> throw new ConstantPoolException(
                        "Bad tag (" + tag + ") at index (" + i + ") position (" + p + ")");
            }
        }
        this.metadataStart = p;
        this.cpOffset = cpOffset;
        this.constantPoolCount = constantPoolCount;
        this.cp = new PoolEntry[constantPoolCount];

        this.flags = readU2(p);
        this.thisClassPos = p + 2;
        p += 6;
        this.interfacesPos = p;
    }

    public ClassFileImpl context() {
        return context;
    }

    @Override
    public Function<Utf8Entry, AttributeMapper<?>> customAttributes() {
        return attributeMapper;
    }

    @Override
    public int size() {
        return constantPoolCount;
    }

    @Override
    public int flags() {
        return flags;
    }

    @Override
    public ClassEntry thisClassEntry() {
        if (thisClass == null) {
            thisClass = readClassEntry(thisClassPos);
        }
        return thisClass;
    }

    @Override
    public Optional<ClassEntry> superclassEntry() {
        if (superclass == null) {
            int scIndex = readU2(thisClassPos + 2);
            superclass = Optional.ofNullable(scIndex == 0 ? null : (ClassEntry) entryByIndex(scIndex));
        }
        return superclass;
    }

    @Override
    public int thisClassPos() {
        return thisClassPos;
    }

    @Override
    public int classfileLength() {
        return classfileLength;
    }

    //------ Bootstrap Method Table handling

    @Override
    public int bootstrapMethodCount() {
        return bootstrapMethodsAttribute().bootstrapMethodsSize();
    }

    @Override
    public BootstrapMethodEntryImpl bootstrapMethodEntry(int index) {
        if (index < 0 || index >= bootstrapMethodCount()) {
            throw new ConstantPoolException("Bad BSM index: " + index);
        }
        return bsmEntries().get(index);
    }

    private static IllegalArgumentException outOfBoundsError(IndexOutOfBoundsException cause) {
        return new IllegalArgumentException("Reading beyond classfile bounds", cause);
    }

    @Override
    public int readU1(int p) {
        try {
            return buffer[p] & 0xFF;
        } catch (IndexOutOfBoundsException e) {
            throw outOfBoundsError(e);
        }
    }

    @Override
    public int readU2(int p) {
        try {
            int b1 = buffer[p] & 0xFF;
            int b2 = buffer[p + 1] & 0xFF;
            return (b1 << 8) + b2;
        } catch (IndexOutOfBoundsException e) {
            throw outOfBoundsError(e);
        }
    }

    @Override
    public int readS1(int p) {
        try {
            return buffer[p];
        } catch (IndexOutOfBoundsException e) {
            throw outOfBoundsError(e);
        }
    }

    @Override
    public int readS2(int p) {
        try {
            int b1 = buffer[p];
            int b2 = buffer[p + 1] & 0xFF;
            return (b1 << 8) + b2;
        } catch (IndexOutOfBoundsException e) {
            throw outOfBoundsError(e);
        }
    }

    @Override
    public int readInt(int p) {
        try {
            int ch1 = buffer[p] & 0xFF;
            int ch2 = buffer[p + 1] & 0xFF;
            int ch3 = buffer[p + 2] & 0xFF;
            int ch4 = buffer[p + 3] & 0xFF;
            return (ch1 << 24) + (ch2 << 16) + (ch3 << 8) + ch4;
        } catch (IndexOutOfBoundsException e) {
            throw outOfBoundsError(e);
        }
    }

    @Override
    public long readLong(int p) {
        try {
            return ((long) buffer[p + 0] << 56) + ((long) (buffer[p + 1] & 255) << 48) +
                   ((long) (buffer[p + 2] & 255) << 40) + ((long) (buffer[p + 3] & 255) << 32) +
                   ((long) (buffer[p + 4] & 255) << 24) + ((buffer[p + 5] & 255) << 16) + ((buffer[p + 6] & 255) << 8) +
                   (buffer[p + 7] & 255);
        } catch (IndexOutOfBoundsException e) {
            throw outOfBoundsError(e);
        }
    }

    @Override
    public float readFloat(int p) {
        return Float.intBitsToFloat(readInt(p));
    }

    @Override
    public double readDouble(int p) {
        return Double.longBitsToDouble(readLong(p));
    }

    @Override
    public byte[] readBytes(int p, int len) {
        try {
            return Arrays.copyOfRange(buffer, p, p + len);
        } catch (IndexOutOfBoundsException e) {
            throw outOfBoundsError(e);
        }
    }

    @Override
    public void copyBytesTo(BufWriter buf, int p, int len) {
        try {
            buf.writeBytes(buffer, p, len);
        } catch (IndexOutOfBoundsException e) {
            throw outOfBoundsError(e);
        }
    }

    BootstrapMethodsAttribute bootstrapMethodsAttribute() {

        if (bootstrapMethodsAttribute == null) {
            bootstrapMethodsAttribute
                    = containedClass.findAttribute(Attributes.BOOTSTRAP_METHODS)
                                    .orElse(new UnboundAttribute.EmptyBootstrapAttribute());
        }

        return bootstrapMethodsAttribute;
    }

    List<BootstrapMethodEntryImpl> bsmEntries() {
        if (bsmEntries == null) {
            bsmEntries = new ArrayList<>();
            BootstrapMethodsAttribute attr = bootstrapMethodsAttribute();
            List<BootstrapMethodEntry> list = attr.bootstrapMethods();
            if (!list.isEmpty()) {
                for (BootstrapMethodEntry bm : list) {
                    AbstractPoolEntry.MethodHandleEntryImpl handle = (AbstractPoolEntry.MethodHandleEntryImpl) bm.bootstrapMethod();
                    List<LoadableConstantEntry> args = bm.arguments();
                    int hash = BootstrapMethodEntryImpl.computeHashCode(handle, args);
                    bsmEntries.add(new BootstrapMethodEntryImpl(this, bsmEntries.size(), hash, handle, args));
                }
            }
        }
        return bsmEntries;
    }

    void setContainedClass(ClassModel containedClass) {
        this.containedClass = containedClass;
    }

    ClassModel getContainedClass() {
        return containedClass;
    }

    boolean writeBootstrapMethods(BufWriter buf) {
        Optional<BootstrapMethodsAttribute> a
                = containedClass.findAttribute(Attributes.BOOTSTRAP_METHODS);
        if (a.isEmpty())
            return false;
        a.get().writeTo(buf);
        return true;
    }

    void writeConstantPoolEntries(BufWriter buf) {
        copyBytesTo(buf, ClassReaderImpl.CP_ITEM_START,
                    metadataStart - ClassReaderImpl.CP_ITEM_START);
    }

    // Constantpool
    @Override
    public PoolEntry entryByIndex(int index) {
        return entryByIndex(index, 0, 0xff);
    }

    private PoolEntry entryByIndex(int index, int lowerBoundTag, int upperBoundTag) {
        if (index <= 0 || index >= constantPoolCount) {
            throw new ConstantPoolException("Bad CP index: " + index);
        }
        PoolEntry info = cp[index];
        if (info == null) {
            int offset = cpOffset[index];
            if (offset == 0) {
                throw new ConstantPoolException("Unusable CP index: " + index);
            }
            int tag = readU1(offset);
            if (tag < lowerBoundTag || tag > upperBoundTag) {
                throw new ConstantPoolException(
                        "Bad tag (" + tag + ") at index (" + index + ") position (" + offset + ")");
            }
            final int q = offset + 1;
            info = switch (tag) {
                case TAG_UTF8 -> new AbstractPoolEntry.Utf8EntryImpl(this, index, buffer, q + 2, readU2(q));
                case TAG_INTEGER -> new AbstractPoolEntry.IntegerEntryImpl(this, index, readInt(q));
                case TAG_FLOAT -> new AbstractPoolEntry.FloatEntryImpl(this, index, readFloat(q));
                case TAG_LONG -> new AbstractPoolEntry.LongEntryImpl(this, index, readLong(q));
                case TAG_DOUBLE -> new AbstractPoolEntry.DoubleEntryImpl(this, index, readDouble(q));
                case TAG_CLASS -> new AbstractPoolEntry.ClassEntryImpl(this, index, (AbstractPoolEntry.Utf8EntryImpl) readUtf8Entry(q));
                case TAG_STRING -> new AbstractPoolEntry.StringEntryImpl(this, index, (AbstractPoolEntry.Utf8EntryImpl) readUtf8Entry(q));
                case TAG_FIELDREF -> new AbstractPoolEntry.FieldRefEntryImpl(this, index, (AbstractPoolEntry.ClassEntryImpl) readClassEntry(q),
                                                                             (AbstractPoolEntry.NameAndTypeEntryImpl) readNameAndTypeEntry(q + 2));
                case TAG_METHODREF -> new AbstractPoolEntry.MethodRefEntryImpl(this, index, (AbstractPoolEntry.ClassEntryImpl) readClassEntry(q),
                                                                               (AbstractPoolEntry.NameAndTypeEntryImpl) readNameAndTypeEntry(q + 2));
                case TAG_INTERFACEMETHODREF -> new AbstractPoolEntry.InterfaceMethodRefEntryImpl(this, index, (AbstractPoolEntry.ClassEntryImpl) readClassEntry(q),
                                                                                                 (AbstractPoolEntry.NameAndTypeEntryImpl) readNameAndTypeEntry(q + 2));
                case TAG_NAMEANDTYPE -> new AbstractPoolEntry.NameAndTypeEntryImpl(this, index, (AbstractPoolEntry.Utf8EntryImpl) readUtf8Entry(q),
                                                                                   (AbstractPoolEntry.Utf8EntryImpl) readUtf8Entry(q + 2));
                case TAG_METHODHANDLE -> new AbstractPoolEntry.MethodHandleEntryImpl(this, index, readU1(q),
                                                                                     readEntry(q + 1, AbstractPoolEntry.AbstractMemberRefEntry.class, TAG_FIELDREF, TAG_INTERFACEMETHODREF));
                case TAG_METHODTYPE -> new AbstractPoolEntry.MethodTypeEntryImpl(this, index, (AbstractPoolEntry.Utf8EntryImpl) readUtf8Entry(q));
                case TAG_CONSTANTDYNAMIC -> new AbstractPoolEntry.ConstantDynamicEntryImpl(this, index, readU2(q), (AbstractPoolEntry.NameAndTypeEntryImpl) readNameAndTypeEntry(q + 2));
                case TAG_INVOKEDYNAMIC -> new AbstractPoolEntry.InvokeDynamicEntryImpl(this, index, readU2(q), (AbstractPoolEntry.NameAndTypeEntryImpl) readNameAndTypeEntry(q + 2));
                case TAG_MODULE -> new AbstractPoolEntry.ModuleEntryImpl(this, index, (AbstractPoolEntry.Utf8EntryImpl) readUtf8Entry(q));
                case TAG_PACKAGE -> new AbstractPoolEntry.PackageEntryImpl(this, index, (AbstractPoolEntry.Utf8EntryImpl) readUtf8Entry(q));
                default -> throw new ConstantPoolException(
                        "Bad tag (" + tag + ") at index (" + index + ") position (" + offset + ")");
            };
            cp[index] = info;
        }
        return info;
    }

    @Override
    public AbstractPoolEntry.Utf8EntryImpl utf8EntryByIndex(int index) {
        if (index <= 0 || index >= constantPoolCount) {
            throw new ConstantPoolException("Bad CP UTF8 index: " + index);
        }
        PoolEntry info = cp[index];
        if (info == null) {
            int offset = cpOffset[index];
            int tag = readU1(offset);
            final int q = offset + 1;
            if (tag != TAG_UTF8) throw new ConstantPoolException("Not a UTF8 - index: " + index);
            AbstractPoolEntry.Utf8EntryImpl uinfo
                    = new AbstractPoolEntry.Utf8EntryImpl(this, index, buffer, q + 2, readU2(q));
            cp[index] = uinfo;
            return uinfo;
        }
        return (AbstractPoolEntry.Utf8EntryImpl) info;
    }

    @Override
    public int skipAttributeHolder(int offset) {
        int p = offset;
        int cnt = readU2(p);
        p += 2;
        for (int i = 0; i < cnt; ++i) {
            int len = readInt(p + 2);
            p += 6;
            if (len < 0 || len > classfileLength - p) {
                throw new IllegalArgumentException("attribute " + readUtf8Entry(p - 6).stringValue() + " too big to handle");
            }
            p += len;
        }
        return p;
    }

    @Override
    public PoolEntry readEntry(int pos) {
        return entryByIndex(readU2(pos));
    }

    @Override
    public <T extends PoolEntry> T readEntry(int pos, Class<T> cls) {
        return readEntry(pos, cls, 0, 0xff);
    }

    private <T extends PoolEntry> T readEntry(int pos, Class<T> cls, int expectedTag) {
        return readEntry(pos, cls, expectedTag, expectedTag);
    }

    private <T extends PoolEntry> T readEntry(int pos, Class<T> cls, int lowerBoundTag, int upperBoundTag) {
        var e = entryByIndex(readU2(pos), lowerBoundTag, upperBoundTag);
        if (cls.isInstance(e)) return cls.cast(e);
        throw new ConstantPoolException("Not a " + cls.getSimpleName() + " at index: " + readU2(pos));
    }

    @Override
    public PoolEntry readEntryOrNull(int pos) {
        int index = readU2(pos);
        if (index == 0) {
            return null;
        }
        return entryByIndex(index);
    }

    @Override
    public Utf8Entry readUtf8Entry(int pos) {
        int index = readU2(pos);
        return utf8EntryByIndex(index);
    }

    @Override
    public Utf8Entry readUtf8EntryOrNull(int pos) {
        int index = readU2(pos);
        if (index == 0) {
            return null;
        }
        return utf8EntryByIndex(index);
    }

    @Override
    public ModuleEntry readModuleEntry(int pos) {
        return readEntry(pos, ModuleEntry.class, TAG_MODULE);
    }

    @Override
    public PackageEntry readPackageEntry(int pos) {
        return readEntry(pos, PackageEntry.class, TAG_PACKAGE);
    }

    @Override
    public ClassEntry readClassEntry(int pos) {
        return readEntry(pos, ClassEntry.class, TAG_CLASS);
    }

    @Override
    public NameAndTypeEntry readNameAndTypeEntry(int pos) {
        return readEntry(pos, NameAndTypeEntry.class, TAG_NAMEANDTYPE);
    }

    @Override
    public MethodHandleEntry readMethodHandleEntry(int pos) {
        return readEntry(pos, MethodHandleEntry.class, TAG_METHODHANDLE);
    }

    @Override
    public boolean compare(BufWriter bufWriter,
                           int bufWriterOffset,
                           int classReaderOffset,
                           int length) {
        try {
            return Arrays.equals(((BufWriterImpl) bufWriter).elems,
                                 bufWriterOffset, bufWriterOffset + length,
                                 buffer, classReaderOffset, classReaderOffset + length);
        } catch (IndexOutOfBoundsException e) {
            throw outOfBoundsError(e);
        }
    }
}
