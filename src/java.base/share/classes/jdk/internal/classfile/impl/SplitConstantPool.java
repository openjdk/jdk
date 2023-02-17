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

import java.lang.constant.ConstantDesc;
import java.lang.constant.MethodTypeDesc;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import jdk.internal.classfile.Attribute;
import jdk.internal.classfile.Attributes;
import jdk.internal.classfile.ClassReader;
import jdk.internal.classfile.Classfile;
import jdk.internal.classfile.constantpool.ClassEntry;
import jdk.internal.classfile.constantpool.ConstantDynamicEntry;
import jdk.internal.classfile.constantpool.ConstantPoolBuilder;
import jdk.internal.classfile.constantpool.ConstantPool;
import jdk.internal.classfile.BootstrapMethodEntry;
import jdk.internal.classfile.BufWriter;
import jdk.internal.classfile.attribute.BootstrapMethodsAttribute;
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

import static jdk.internal.classfile.Classfile.TAG_CLASS;
import static jdk.internal.classfile.Classfile.TAG_CONSTANTDYNAMIC;
import static jdk.internal.classfile.Classfile.TAG_DOUBLE;
import static jdk.internal.classfile.Classfile.TAG_FIELDREF;
import static jdk.internal.classfile.Classfile.TAG_FLOAT;
import static jdk.internal.classfile.Classfile.TAG_INTEGER;
import static jdk.internal.classfile.Classfile.TAG_INTERFACEMETHODREF;
import static jdk.internal.classfile.Classfile.TAG_INVOKEDYNAMIC;
import static jdk.internal.classfile.Classfile.TAG_LONG;
import static jdk.internal.classfile.Classfile.TAG_METHODHANDLE;
import static jdk.internal.classfile.Classfile.TAG_METHODREF;
import static jdk.internal.classfile.Classfile.TAG_METHODTYPE;
import static jdk.internal.classfile.Classfile.TAG_MODULE;
import static jdk.internal.classfile.Classfile.TAG_NAMEANDTYPE;
import static jdk.internal.classfile.Classfile.TAG_PACKAGE;
import static jdk.internal.classfile.Classfile.TAG_STRING;

/**
 * ConstantPool.
 */
public final class SplitConstantPool implements ConstantPoolBuilder {

    private final ClassReaderImpl parent;
    private final int parentSize, parentBsmSize;
    final Options options;

    private int size, bsmSize;
    private PoolEntry[] myEntries;
    private ConcreteBootstrapMethodEntry[] myBsmEntries;
    private boolean doneFullScan;
    private EntryMap<PoolEntry> map;
    private EntryMap<ConcreteBootstrapMethodEntry> bsmMap;

    public SplitConstantPool() {
        this(new Options(Collections.emptyList()));
    }

    public SplitConstantPool(Options options) {
        this.size = 1;
        this.bsmSize = 0;
        this.myEntries = new PoolEntry[1024];
        this.myBsmEntries = new ConcreteBootstrapMethodEntry[8];
        this.parent = null;
        this.parentSize = 0;
        this.parentBsmSize = 0;
        this.options = options;
    }

    public SplitConstantPool(ClassReader parent) {
        this.options = ((ClassReaderImpl) parent).options;
        this.parent = (ClassReaderImpl) parent;
        this.parentSize = parent.entryCount();
        this.parentBsmSize = parent.bootstrapMethodCount();
        this.size = parentSize;
        this.bsmSize = parentBsmSize;
        this.myEntries = new PoolEntry[8];
        this.myBsmEntries = new ConcreteBootstrapMethodEntry[8];
    }

    @Override
    public int entryCount() {
        return size;
    }

    @Override
    public int bootstrapMethodCount() {
        return bsmSize;
    }

    @Override
    public PoolEntry entryByIndex(int index) {
        return (index < parentSize)
               ? parent.entryByIndex(index)
               : myEntries[index - parentSize];
    }

    @Override
    public ConcreteBootstrapMethodEntry bootstrapMethodEntry(int index) {
        return (index < parentBsmSize)
               ? parent.bootstrapMethodEntry(index)
               : myBsmEntries[index - parentBsmSize];
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T extends PoolEntry> T maybeClone(T entry) {
        return canWriteDirect(entry.constantPool())
               ? entry
               : (T) entry.clone(this);
    }

    public Options options() {
        return options;
    }

    @Override
    public boolean canWriteDirect(ConstantPool other) {
        return this == other || parent == other;
    }

    @Override
    public boolean writeBootstrapMethods(BufWriter buf) {
        if (bsmSize == 0)
            return false;
        int pos = buf.size();
        if (parent != null && parentBsmSize != 0) {
            parent.writeBootstrapMethods(buf);
            for (int i = parentBsmSize; i < bsmSize; i++)
                bootstrapMethodEntry(i).writeTo(buf);
            int attrLen = buf.size() - pos;
            buf.patchInt(pos + 2, 4, attrLen - 6);
            buf.patchInt(pos + 6, 2, bsmSize);
            return true;
        }
        else {
            Attribute<BootstrapMethodsAttribute> a
                    = new UnboundAttribute.AdHocAttribute<>(Attributes.BOOTSTRAP_METHODS) {

                @Override
                public void writeBody(BufWriter b) {
                    buf.writeU2(bsmSize);
                    for (int i = 0; i < bsmSize; i++)
                        bootstrapMethodEntry(i).writeTo(buf);
                }
            };
            a.writeTo(buf);
            return true;
        }
    }

    @Override
    public void writeTo(BufWriter buf) {
        int writeFrom = 1;
        buf.writeU2(entryCount());
        if (parent != null && buf.constantPool().canWriteDirect(this)) {
            parent.writeConstantPoolEntries(buf);
            writeFrom = parent.entryCount();
        }
        for (int i = writeFrom; i < entryCount(); ) {
            PoolEntry info = entryByIndex(i);
            info.writeTo(buf);
            i += info.poolEntries();
        }
    }

    private EntryMap<PoolEntry> map() {
        if (map == null) {
            map = new EntryMap<>(Math.max(size, 1024), .75f) {
                @Override
                protected PoolEntry fetchElement(int index) {
                    return entryByIndex(index);
                }
            };
            // Doing a full scan here yields fall-off-the-cliff performance results,
            // especially if we only need a few entries that are already
            // inflated (such as attribute names).
            // So we inflate the map with whatever we've got from the parent, and
            // later, if we miss, we do a one-time full inflation before creating
            // a new entry.
            for (int i=1; i<parentSize; i++) {
                PoolEntry cpi = parent.cp[i];
                if (cpi != null)
                    map.put(cpi.hashCode(), cpi.index());
            }
            for (int i = Math.max(parentSize, 1); i < size; ) {
                PoolEntry cpi = myEntries[i - parentSize];
                map.put(cpi.hashCode(), cpi.index());
                i += cpi.poolEntries();
            }
        }
        return map;
    }

    private void fullScan() {
        for (int i=1; i<parentSize;) {
            PoolEntry cpi = parent.entryByIndex(i);
            map.put(cpi.hashCode(), cpi.index());
            i += cpi.poolEntries();
        }
        doneFullScan = true;
    }

    private EntryMap<ConcreteBootstrapMethodEntry> bsmMap() {
        if (bsmMap == null) {
            bsmMap = new EntryMap<>(Math.max(bsmSize, 16), .75f) {
                @Override
                protected ConcreteBootstrapMethodEntry fetchElement(int index) {
                    return bootstrapMethodEntry(index);
                }
            };
            for (int i=0; i<parentBsmSize; i++) {
                ConcreteBootstrapMethodEntry bsm = parent.bootstrapMethodEntry(i);
                bsmMap.put(bsm.hash, bsm.index);
            }
            for (int i = parentBsmSize; i < bsmSize; ++i) {
                ConcreteBootstrapMethodEntry bsm = myBsmEntries[i - parentBsmSize];
                bsmMap.put(bsm.hash, bsm.index);
            }
        }
        return bsmMap;
    }

    private <E extends PoolEntry> E internalAdd(E cpi) {
        return internalAdd(cpi, cpi.hashCode());
    }

    private <E extends PoolEntry> E internalAdd(E cpi, int hash) {
        int newIndex = size-parentSize;
        if (newIndex + 2 > myEntries.length) {
            myEntries = Arrays.copyOf(myEntries, 2 * newIndex, PoolEntry[].class);
        }
        myEntries[newIndex] = cpi;
        size += cpi.poolEntries();
        map().put(hash, cpi.index());
        return cpi;
    }

    private ConcreteBootstrapMethodEntry internalAdd(ConcreteBootstrapMethodEntry bsm, int hash) {
        int newIndex = bsmSize-parentBsmSize;
        if (newIndex + 2 > myBsmEntries.length) {
            myBsmEntries = Arrays.copyOf(myBsmEntries, 2 * newIndex, ConcreteBootstrapMethodEntry[].class);
        }
        myBsmEntries[newIndex] = bsm;
        bsmSize += 1;
        bsmMap().put(hash, bsm.index);
        return bsm;
    }

    private <T extends ConstantDesc> PoolEntry findPrimitiveEntry(int tag, T val) {
        int hash = ConcreteEntry.hash1(tag, val.hashCode());
        EntryMap<PoolEntry> map = map();
        for (int token = map.firstToken(hash); token != -1; token = map.nextToken(hash, token)) {
            PoolEntry e = map.getElementByToken(token);
            if (e.tag() == tag
                && e instanceof ConcreteEntry.PrimitiveEntry<?> ce
                && ce.value().equals(val))
                return e;
        }
        if (!doneFullScan) {
            fullScan();
            return findPrimitiveEntry(tag, val);
        }
        return null;
    }

    private<T extends ConcreteEntry> ConcreteEntry findEntry(int tag, T ref1) {
        // invariant: canWriteDirect(ref1.constantPool())
        int hash = ConcreteEntry.hash1(tag, ref1.index());
        EntryMap<PoolEntry> map = map();
        for (int token = map.firstToken(hash); token != -1; token = map.nextToken(hash, token)) {
            PoolEntry e = map.getElementByToken(token);
            if (e.tag() == tag
                && e instanceof ConcreteEntry.RefEntry<?> re
                && re.ref1 == ref1)
                return re;
        }
        if (!doneFullScan) {
            fullScan();
            return findEntry(tag, ref1);
        }
        return null;
    }

    private <T extends ConcreteEntry, U extends ConcreteEntry>
            ConcreteEntry findEntry(int tag, T ref1, U ref2) {
        // invariant: canWriteDirect(ref1.constantPool()), canWriteDirect(ref2.constantPool())
        int hash = ConcreteEntry.hash2(tag, ref1.index(), ref2.index());
        EntryMap<PoolEntry> map = map();
        for (int token = map.firstToken(hash); token != -1; token = map.nextToken(hash, token)) {
            PoolEntry e = map.getElementByToken(token);
            if (e.tag() == tag
                    && e instanceof ConcreteEntry.RefsEntry<?, ?> re
                    && re.ref1 == ref1
                    && re.ref2 == ref2) {
                return re;
            }
        }
        if (!doneFullScan) {
            fullScan();
            return findEntry(tag, ref1, ref2);
        }
        return null;
    }

    private<T> ConcreteEntry.ConcreteUtf8Entry tryFindUtf8(int hash, String target) {
        EntryMap<PoolEntry> map = map();
        for (int token = map.firstToken(hash); token != -1;
             token = map.nextToken(hash, token)) {
            PoolEntry e = map.getElementByToken(token);
            if (e.tag() == Classfile.TAG_UTF8
                && e instanceof ConcreteEntry.ConcreteUtf8Entry ce
                && ce.hashCode() == hash
                && target.equals(ce.stringValue()))
                return ce;
        }
        if (!doneFullScan) {
            fullScan();
            return tryFindUtf8(hash, target);
        }
        return null;
    }

    private ConcreteEntry.ConcreteUtf8Entry tryFindUtf8(int hash, ConcreteEntry.ConcreteUtf8Entry target) {
        EntryMap<PoolEntry> map = map();
        for (int token = map.firstToken(hash); token != -1; token = map.nextToken(hash, token)) {
            PoolEntry e = map.getElementByToken(token);
            if (e.tag() == Classfile.TAG_UTF8
                && e instanceof ConcreteEntry.ConcreteUtf8Entry ce
                && target.equalsUtf8(ce))
                return ce;
        }
        if (!doneFullScan) {
            fullScan();
            return tryFindUtf8(hash, target);
        }
        return null;
    }

    @Override
    public ConcreteEntry.ConcreteUtf8Entry utf8Entry(String s) {
        var ce = tryFindUtf8(ConcreteEntry.hashString(s.hashCode()), s);
        return ce == null ? internalAdd(new ConcreteEntry.ConcreteUtf8Entry(this, size, s)) : ce;
    }

    ConcreteEntry.ConcreteUtf8Entry maybeCloneUtf8Entry(Utf8Entry entry) {
        ConcreteEntry.ConcreteUtf8Entry e = (ConcreteEntry.ConcreteUtf8Entry) entry;
        if (e.constantPool == this || e.constantPool == parent)
            return e;
        ConcreteEntry.ConcreteUtf8Entry ce = tryFindUtf8(e.hashCode(), e);
        return ce == null ? internalAdd(new ConcreteEntry.ConcreteUtf8Entry(this, size, e)) : ce;
    }

    @Override
    public ConcreteEntry.ConcreteClassEntry classEntry(Utf8Entry nameEntry) {
        ConcreteEntry.ConcreteUtf8Entry ne = maybeCloneUtf8Entry(nameEntry);
        var e = (ConcreteEntry.ConcreteClassEntry) findEntry(TAG_CLASS, ne);
        return e == null ? internalAdd(new ConcreteEntry.ConcreteClassEntry(this, size, ne)) : e;
    }

    @Override
    public PackageEntry packageEntry(Utf8Entry nameEntry) {
        ConcreteEntry.ConcreteUtf8Entry ne = maybeCloneUtf8Entry(nameEntry);
        var e = (ConcreteEntry.ConcretePackageEntry) findEntry(TAG_PACKAGE, ne);
        return e == null ? internalAdd(new ConcreteEntry.ConcretePackageEntry(this, size, ne)) : e;
    }

    @Override
    public ModuleEntry moduleEntry(Utf8Entry nameEntry) {
        ConcreteEntry.ConcreteUtf8Entry ne = maybeCloneUtf8Entry(nameEntry);
        var e = (ConcreteEntry.ConcreteModuleEntry) findEntry(TAG_MODULE, ne);
        return e == null ? internalAdd(new ConcreteEntry.ConcreteModuleEntry(this, size, ne)) : e;
    }

    @Override
    public ConcreteEntry.ConcreteNameAndTypeEntry natEntry(Utf8Entry nameEntry, Utf8Entry typeEntry) {
        ConcreteEntry.ConcreteUtf8Entry ne = maybeCloneUtf8Entry(nameEntry);
        ConcreteEntry.ConcreteUtf8Entry te = maybeCloneUtf8Entry(typeEntry);
        var e = (ConcreteEntry.ConcreteNameAndTypeEntry) findEntry(TAG_NAMEANDTYPE, ne, te);
        return e == null ? internalAdd(new ConcreteEntry.ConcreteNameAndTypeEntry(this, size, ne, te)) : e;
    }

    @Override
    public FieldRefEntry fieldRefEntry(ClassEntry owner, NameAndTypeEntry nameAndType) {
        ConcreteEntry.ConcreteClassEntry oe = (ConcreteEntry.ConcreteClassEntry) owner;
        ConcreteEntry.ConcreteNameAndTypeEntry ne = (ConcreteEntry.ConcreteNameAndTypeEntry) nameAndType;
        if (!canWriteDirect(oe.constantPool))
            oe = classEntry(owner.name());
        if (!canWriteDirect(ne.constantPool))
            ne = natEntry(nameAndType.name(), nameAndType.type());
        var e = (ConcreteEntry.ConcreteFieldRefEntry) findEntry(TAG_FIELDREF, oe, ne);
        return e == null ? internalAdd(new ConcreteEntry.ConcreteFieldRefEntry(this, size, oe, ne)) : e;
    }

    @Override
    public MethodRefEntry methodRefEntry(ClassEntry owner, NameAndTypeEntry nameAndType) {
        ConcreteEntry.ConcreteClassEntry oe = (ConcreteEntry.ConcreteClassEntry) owner;
        ConcreteEntry.ConcreteNameAndTypeEntry ne = (ConcreteEntry.ConcreteNameAndTypeEntry) nameAndType;
        if (!canWriteDirect(oe.constantPool))
            oe = classEntry(owner.name());
        if (!canWriteDirect(ne.constantPool))
            ne = natEntry(nameAndType.name(), nameAndType.type());
        var e = (ConcreteEntry.ConcreteMethodRefEntry) findEntry(TAG_METHODREF, oe, ne);
        return e == null ? internalAdd(new ConcreteEntry.ConcreteMethodRefEntry(this, size, oe, ne)) : e;
    }

    @Override
    public InterfaceMethodRefEntry interfaceMethodRefEntry(ClassEntry owner, NameAndTypeEntry nameAndType) {
        ConcreteEntry.ConcreteClassEntry oe = (ConcreteEntry.ConcreteClassEntry) owner;
        ConcreteEntry.ConcreteNameAndTypeEntry ne = (ConcreteEntry.ConcreteNameAndTypeEntry) nameAndType;
        if (!canWriteDirect(oe.constantPool))
            oe = classEntry(owner.name());
        if (!canWriteDirect(ne.constantPool))
            ne = natEntry(nameAndType.name(), nameAndType.type());
        var e = (ConcreteEntry.ConcreteInterfaceMethodRefEntry) findEntry(TAG_INTERFACEMETHODREF, oe, ne);
        return e == null ? internalAdd(new ConcreteEntry.ConcreteInterfaceMethodRefEntry(this, size, oe, ne)) : e;
    }

    @Override
    public MethodTypeEntry methodTypeEntry(MethodTypeDesc descriptor) {
        return methodTypeEntry(utf8Entry(descriptor.descriptorString()));
    }

    @Override
    public MethodTypeEntry methodTypeEntry(Utf8Entry descriptor) {
        ConcreteEntry.ConcreteUtf8Entry de = maybeCloneUtf8Entry(descriptor);
        var e = (ConcreteEntry.ConcreteMethodTypeEntry) findEntry(TAG_METHODTYPE, de);
        return e == null ? internalAdd(new ConcreteEntry.ConcreteMethodTypeEntry(this, size, de)) : e;
    }

    @Override
    public MethodHandleEntry methodHandleEntry(int refKind, MemberRefEntry reference) {
        if (!canWriteDirect(reference.constantPool())) {
            reference = switch (reference.tag()) {
                case TAG_FIELDREF -> fieldRefEntry(reference.owner(), reference.nameAndType());
                case TAG_METHODREF -> methodRefEntry(reference.owner(), reference.nameAndType());
                case TAG_INTERFACEMETHODREF -> interfaceMethodRefEntry(reference.owner(), reference.nameAndType());
                default -> throw new IllegalStateException(String.format("Bad tag %d", reference.tag()));
            };
        }

        int hash = ConcreteEntry.hash2(TAG_METHODHANDLE, refKind, reference.index());
        EntryMap<PoolEntry> map1 = map();
        for (int token = map1.firstToken(hash); token != -1; token = map1.nextToken(hash, token)) {
            PoolEntry e = map1.getElementByToken(token);
            if (e.tag() == TAG_METHODHANDLE
                && e instanceof ConcreteEntry.ConcreteMethodHandleEntry ce
                && ce.kind() == refKind && ce.reference() == reference)
                return ce;
        }
        if (!doneFullScan) {
            fullScan();
            return methodHandleEntry(refKind, reference);
        }
        return internalAdd(new ConcreteEntry.ConcreteMethodHandleEntry(this, size, hash, refKind, (ConcreteEntry.MemberRefEntry) reference), hash);
    }

    @Override
    public InvokeDynamicEntry invokeDynamicEntry(BootstrapMethodEntry bootstrapMethodEntry,
                                                 NameAndTypeEntry nameAndType) {
        if (!canWriteDirect(bootstrapMethodEntry.constantPool()))
            bootstrapMethodEntry = bsmEntry(bootstrapMethodEntry.bootstrapMethod(),
                                            bootstrapMethodEntry.arguments());
        if (!canWriteDirect(nameAndType.constantPool()))
            nameAndType = natEntry(nameAndType.name(), nameAndType.type());
        int hash = ConcreteEntry.hash2(TAG_INVOKEDYNAMIC, bootstrapMethodEntry.bsmIndex(), nameAndType.index());
        EntryMap<PoolEntry> map1 = map();
        for (int token = map1.firstToken(hash); token != -1; token = map1.nextToken(hash, token)) {
            PoolEntry e = map1.getElementByToken(token);
            if (e.tag() == TAG_INVOKEDYNAMIC
                && e instanceof ConcreteEntry.ConcreteInvokeDynamicEntry ce
                && ce.bootstrap() == bootstrapMethodEntry && ce.nameAndType() == nameAndType)
                return ce;
        }
        if (!doneFullScan) {
            fullScan();
            return invokeDynamicEntry(bootstrapMethodEntry, nameAndType);
        }

        ConcreteEntry.ConcreteInvokeDynamicEntry ce = new ConcreteEntry.ConcreteInvokeDynamicEntry(this, size, hash, (ConcreteBootstrapMethodEntry) bootstrapMethodEntry, (ConcreteEntry.ConcreteNameAndTypeEntry) nameAndType);
        internalAdd(ce, hash);
        return ce;
    }

    @Override
    public ConstantDynamicEntry constantDynamicEntry(BootstrapMethodEntry bootstrapMethodEntry,
                                                     NameAndTypeEntry nameAndType) {
        if (!canWriteDirect(bootstrapMethodEntry.constantPool()))
            bootstrapMethodEntry = bsmEntry(bootstrapMethodEntry.bootstrapMethod(),
                                            bootstrapMethodEntry.arguments());
        if (!canWriteDirect(nameAndType.constantPool()))
            nameAndType = natEntry(nameAndType.name(), nameAndType.type());
        int hash = ConcreteEntry.hash2(TAG_CONSTANTDYNAMIC, bootstrapMethodEntry.bsmIndex(), nameAndType.index());
        EntryMap<PoolEntry> map1 = map();
        for (int token = map1.firstToken(hash); token != -1; token = map1.nextToken(hash, token)) {
            PoolEntry e = map1.getElementByToken(token);
            if (e.tag() == TAG_CONSTANTDYNAMIC
                && e instanceof ConcreteEntry.ConcreteConstantDynamicEntry ce
                && ce.bootstrap() == bootstrapMethodEntry && ce.nameAndType() == nameAndType)
                return ce;
        }
        if (!doneFullScan) {
            fullScan();
            return constantDynamicEntry(bootstrapMethodEntry, nameAndType);
        }

        ConcreteEntry.ConcreteConstantDynamicEntry ce = new ConcreteEntry.ConcreteConstantDynamicEntry(this, size, hash, (ConcreteBootstrapMethodEntry) bootstrapMethodEntry, (ConcreteEntry.ConcreteNameAndTypeEntry) nameAndType);
        internalAdd(ce, hash);
        return ce;
    }

    @Override
    public IntegerEntry intEntry(int value) {
        var e = (IntegerEntry) findPrimitiveEntry(TAG_INTEGER, value);
        return e == null ? internalAdd(new ConcreteEntry.ConcreteIntegerEntry(this, size, value)) : e;
    }

    @Override
    public FloatEntry floatEntry(float value) {
        var e = (FloatEntry) findPrimitiveEntry(TAG_FLOAT, value);
        return e == null ? internalAdd(new ConcreteEntry.ConcreteFloatEntry(this, size, value)) : e;
    }

    @Override
    public LongEntry longEntry(long value) {
        var e = (LongEntry) findPrimitiveEntry(TAG_LONG, value);
        return e == null ? internalAdd(new ConcreteEntry.ConcreteLongEntry(this, size, value)) : e;
    }

    @Override
    public DoubleEntry doubleEntry(double value) {
        var e = (DoubleEntry) findPrimitiveEntry(TAG_DOUBLE, value);
        return e == null ? internalAdd(new ConcreteEntry.ConcreteDoubleEntry(this, size, value)) : e;
    }

    @Override
    public StringEntry stringEntry(Utf8Entry utf8) {
        ConcreteEntry.ConcreteUtf8Entry ue = maybeCloneUtf8Entry(utf8);
        var e = (ConcreteEntry.ConcreteStringEntry) findEntry(TAG_STRING, ue);
        return e == null ? internalAdd(new ConcreteEntry.ConcreteStringEntry(this, size, ue)) : e;
    }

    @Override
    public BootstrapMethodEntry bsmEntry(MethodHandleEntry methodReference,
                                         List<LoadableConstantEntry> arguments) {
        if (!canWriteDirect(methodReference.constantPool()))
            methodReference = methodHandleEntry(methodReference.kind(), methodReference.reference());
        for (LoadableConstantEntry a : arguments) {
            if (!canWriteDirect(a.constantPool())) {
                // copy args list
                LoadableConstantEntry[] arr = arguments.toArray(new LoadableConstantEntry[0]);
                for (int i = 0; i < arr.length; i++)
                    arr[i] = (LoadableConstantEntry) arr[i].clone(this);
                arguments = List.of(arr);

                break;
            }
        }
        ConcreteEntry.ConcreteMethodHandleEntry mre = (ConcreteEntry.ConcreteMethodHandleEntry) methodReference;
        int hash = ConcreteBootstrapMethodEntry.computeHashCode(mre, arguments);
        EntryMap<ConcreteBootstrapMethodEntry> map = bsmMap();
        for (int token = map.firstToken(hash); token != -1; token = map.nextToken(hash, token)) {
            ConcreteBootstrapMethodEntry e = map.getElementByToken(token);
            if (e.bootstrapMethod() == mre && e.arguments().equals(arguments)) {
                return e;
            }
        }
        ConcreteBootstrapMethodEntry ne = new ConcreteBootstrapMethodEntry(this, bsmSize, hash, mre, arguments);
        return internalAdd(ne, hash);
    }
}
