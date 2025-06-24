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

import java.lang.classfile.Attributes;
import java.lang.classfile.BootstrapMethodEntry;
import java.lang.classfile.ClassReader;
import java.lang.classfile.attribute.BootstrapMethodsAttribute;
import java.lang.classfile.constantpool.*;
import java.lang.constant.ClassDesc;
import java.lang.constant.DynamicCallSiteDesc;
import java.lang.constant.MethodTypeDesc;
import java.util.Arrays;
import java.util.List;

import static java.lang.classfile.constantpool.PoolEntry.*;
import static java.util.Objects.requireNonNull;

public final class SplitConstantPool implements ConstantPoolBuilder {

    private final ClassReaderImpl parent;
    private final int parentSize, parentBsmSize;

    private int size, bsmSize;
    private PoolEntry[] myEntries;
    private BootstrapMethodEntryImpl[] myBsmEntries;
    private boolean doneFullScan;
    private EntryMap map;
    private EntryMap bsmMap;

    public SplitConstantPool() {
        this.size = 1;
        this.bsmSize = 0;
        this.myEntries = new PoolEntry[1024];
        this.myBsmEntries = new BootstrapMethodEntryImpl[8];
        this.parent = null;
        this.parentSize = 0;
        this.parentBsmSize = 0;
        this.doneFullScan = true;
    }

    public SplitConstantPool(ClassReader parent) {
        this.parent = (ClassReaderImpl) parent;
        this.parentSize = parent.size();
        this.parentBsmSize = parent.bootstrapMethodCount();
        this.size = parentSize;
        this.bsmSize = parentBsmSize;
        this.myEntries = new PoolEntry[8];
        this.myBsmEntries = new BootstrapMethodEntryImpl[8];
        this.doneFullScan = true;
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public int bootstrapMethodCount() {
        return bsmSize;
    }

    @Override
    public PoolEntry entryByIndex(int index) {
        if (index <= 0 || index >= size()) {
            throw badCP(index);
        }
        PoolEntry pe = (index < parentSize)
               ? parent.entryByIndex(index)
               : myEntries[index - parentSize];
        if (pe == null) {
            throw unusableCP(index);
        }
        return pe;
    }

    private static ConstantPoolException badCP(int index) {
        return new ConstantPoolException("Bad CP index: " + index);
    }

    private static ConstantPoolException unusableCP(int index) {
        return new ConstantPoolException("Unusable CP index: " + index);
    }

    @Override
    public <T extends PoolEntry> T entryByIndex(int index, Class<T> cls) {
        return ClassReaderImpl.checkType(entryByIndex(index), index, cls);
    }

    @Override
    public BootstrapMethodEntryImpl bootstrapMethodEntry(int index) {
        if (index < 0 || index >= bootstrapMethodCount()) {
            throw new ConstantPoolException("Bad BSM index: " + index);
        }
        return (index < parentBsmSize)
               ? parent.bootstrapMethodEntry(index)
               : myBsmEntries[index - parentBsmSize];
    }

    @Override
    public boolean canWriteDirect(ConstantPool other) {
        requireNonNull(other);
        return this == other || parent == other;
    }

    public boolean writeBootstrapMethods(BufWriterImpl buf) {
        if (bsmSize == 0)
            return false;
        int pos = buf.size();
        if (parent != null && parentBsmSize != 0) {
            parent.writeBootstrapMethods(buf);
            for (int i = parentBsmSize; i < bsmSize; i++)
                bootstrapMethodEntry(i).writeTo(buf);
            int attrLen = buf.size() - pos;
            buf.patchInt(pos + 2, attrLen - 6);
            buf.patchU2(pos + 6, bsmSize);
        }
        else {
            UnboundAttribute<BootstrapMethodsAttribute> a
                    = new UnboundAttribute.AdHocAttribute<>(Attributes.bootstrapMethods()) {

                @Override
                public void writeBody(BufWriterImpl b) {
                    buf.writeU2(bsmSize);
                    for (int i = 0; i < bsmSize; i++)
                        bootstrapMethodEntry(i).writeTo(buf);
                }

                @Override
                public Utf8Entry attributeName() {
                    return utf8Entry(Attributes.NAME_BOOTSTRAP_METHODS);
                }
            };
            a.writeTo(buf);
        }
        return true;
    }

    void writeTo(BufWriterImpl buf) {
        int writeFrom = 1;
        if (size() >= 65536) {
            throw new IllegalArgumentException(String.format("Constant pool is too large %d", size()));
        }
        buf.writeU2(size());
        if (parent != null && buf.constantPool().canWriteDirect(this)) {
            parent.writeConstantPoolEntries(buf);
            writeFrom = parent.size();
        }
        for (int i = writeFrom; i < size(); ) {
            var info = (AbstractPoolEntry) entryByIndex(i);
            info.writeTo(buf);
            i += info.width();
        }
    }

    private EntryMap map() {
        int parentSize = this.parentSize;
        var map = this.map;
        if (map == null) {
            this.map = map = new EntryMap(Math.max(size, 1024), .75f);

            // Doing a full scan here yields fall-off-the-cliff performance results,
            // especially if we only need a few entries that are already
            // inflated (such as attribute names).
            // So we inflate the map with whatever we've got from the parent, and
            // later, if we miss, we do a one-time full inflation before creating
            // a new entry.
            for (int i=1; i<parentSize;) {
                PoolEntry cpi = parent.cp[i];
                if (cpi == null) {
                    doneFullScan = false;
                    i++;
                } else {
                    map.put(cpi.hashCode(), cpi.index());
                    i += cpi.width();
                }
            }
            for (int i = Math.max(parentSize, 1); i < size; ) {
                PoolEntry cpi = myEntries[i - parentSize];
                map.put(cpi.hashCode(), cpi.index());
                i += cpi.width();
            }
        }
        return map;
    }

    private void fullScan() {
        for (int i=1; i<parentSize;) {
            PoolEntry cpi = parent.entryByIndex(i);
            map.put(cpi.hashCode(), cpi.index());
            i += cpi.width();
        }
        doneFullScan = true;
    }

    private EntryMap bsmMap() {
        int bsmSize = this.bsmSize;
        var bsmMap = this.bsmMap;
        if (bsmMap == null) {
            this.bsmMap = bsmMap = new EntryMap(Math.max(bsmSize, 16), .75f);
            for (int i=0; i<parentBsmSize; i++) {
                BootstrapMethodEntryImpl bsm = parent.bootstrapMethodEntry(i);
                bsmMap.put(bsm.hash, bsm.index);
            }
            for (int i = parentBsmSize; i < bsmSize; ++i) {
                BootstrapMethodEntryImpl bsm = myBsmEntries[i - parentBsmSize];
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
        size += cpi.width();
        map().put(hash, cpi.index());
        return cpi;
    }

    private BootstrapMethodEntryImpl internalAdd(BootstrapMethodEntryImpl bsm, int hash) {
        int newIndex = bsmSize-parentBsmSize;
        if (newIndex + 2 > myBsmEntries.length) {
            myBsmEntries = Arrays.copyOf(myBsmEntries, 2 * newIndex, BootstrapMethodEntryImpl[].class);
        }
        myBsmEntries[newIndex] = bsm;
        bsmSize += 1;
        bsmMap().put(hash, bsm.index);
        return bsm;
    }

    private IntegerEntry findIntEntry(int val) {
        int hash = AbstractPoolEntry.hash1(TAG_INTEGER, Integer.hashCode(val));
        EntryMap map = map();
        for (int token = map.firstToken(hash); token != -1; token = map.nextToken(hash, token)) {
            PoolEntry e = entryByIndex(map.getIndexByToken(token));
            if (e.tag() == TAG_INTEGER
                    && e instanceof AbstractPoolEntry.IntegerEntryImpl ce
                    && ce.intValue() == val)
                return ce;
        }
        if (!doneFullScan) {
            fullScan();
            return findIntEntry(val);
        }
        return null;
    }

    private LongEntry findLongEntry(long val) {
        int hash = AbstractPoolEntry.hash1(TAG_LONG, Long.hashCode(val));
        EntryMap map = map();
        for (int token = map.firstToken(hash); token != -1; token = map.nextToken(hash, token)) {
            PoolEntry e = entryByIndex(map.getIndexByToken(token));
            if (e.tag() == TAG_LONG
                    && e instanceof AbstractPoolEntry.LongEntryImpl ce
                    && ce.longValue() == val)
                return ce;
        }
        if (!doneFullScan) {
            fullScan();
            return findLongEntry(val);
        }
        return null;
    }

    private FloatEntry findFloatEntry(float val) {
        int hash = AbstractPoolEntry.hash1(TAG_FLOAT, Float.hashCode(val));
        EntryMap map = map();
        for (int token = map.firstToken(hash); token != -1; token = map.nextToken(hash, token)) {
            PoolEntry e = entryByIndex(map.getIndexByToken(token));
            if (e.tag() == TAG_FLOAT
                    && e instanceof AbstractPoolEntry.FloatEntryImpl ce
                    && ce.floatValue() == val)
                return ce;
        }
        if (!doneFullScan) {
            fullScan();
            return findFloatEntry(val);
        }
        return null;
    }

    private DoubleEntry findDoubleEntry(double val) {
        int hash = AbstractPoolEntry.hash1(TAG_DOUBLE, Double.hashCode(val));
        EntryMap map = map();
        for (int token = map.firstToken(hash); token != -1; token = map.nextToken(hash, token)) {
            PoolEntry e = entryByIndex(map.getIndexByToken(token));
            if (e.tag() == TAG_DOUBLE
                    && e instanceof AbstractPoolEntry.DoubleEntryImpl ce
                    && ce.doubleValue() == val)
                return ce;
        }
        if (!doneFullScan) {
            fullScan();
            return findDoubleEntry(val);
        }
        return null;
    }

    private<T extends AbstractPoolEntry> AbstractPoolEntry findEntry(int tag, T ref1) {
        // invariant: canWriteDirect(ref1.constantPool())
        int hash = AbstractPoolEntry.hash1(tag, ref1.index());
        EntryMap map = map();
        for (int token = map.firstToken(hash); token != -1; token = map.nextToken(hash, token)) {
            PoolEntry e = entryByIndex(map.getIndexByToken(token));
            if (e.tag() == tag
                && e instanceof AbstractPoolEntry.AbstractRefEntry<?> re
                && re.ref1 == ref1)
                return re;
        }
        if (!doneFullScan) {
            fullScan();
            return findEntry(tag, ref1);
        }
        return null;
    }

    private <T extends AbstractPoolEntry, U extends AbstractPoolEntry>
            AbstractPoolEntry findEntry(int tag, T ref1, U ref2) {
        // invariant: canWriteDirect(ref1.constantPool()), canWriteDirect(ref2.constantPool())
        int hash = AbstractPoolEntry.hash2(tag, ref1.index(), ref2.index());
        EntryMap map = map();
        for (int token = map.firstToken(hash); token != -1; token = map.nextToken(hash, token)) {
            PoolEntry e = entryByIndex(map.getIndexByToken(token));
            if (e.tag() == tag
                    && e instanceof AbstractPoolEntry.AbstractRefsEntry<?, ?> re
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

    private AbstractPoolEntry.Utf8EntryImpl tryFindUtf8(int hash, String target) {
        EntryMap map = map();
        for (int token = map.firstToken(hash); token != -1;
             token = map.nextToken(hash, token)) {
            PoolEntry e = entryByIndex(map.getIndexByToken(token));
            if (e.tag() == TAG_UTF8
                && e instanceof AbstractPoolEntry.Utf8EntryImpl ce
                && target.equals(ce.stringValue()))
                return ce;
        }
        if (!doneFullScan) {
            fullScan();
            return tryFindUtf8(hash, target);
        }
        return null;
    }

    private AbstractPoolEntry.Utf8EntryImpl tryFindUtf8(int hash, AbstractPoolEntry.Utf8EntryImpl target) {
        EntryMap map = map();
        for (int token = map.firstToken(hash); token != -1; token = map.nextToken(hash, token)) {
            PoolEntry e = entryByIndex(map.getIndexByToken(token));
            if (e.tag() == TAG_UTF8
                && e instanceof AbstractPoolEntry.Utf8EntryImpl ce
                && target.equalsUtf8(ce))
                return ce;
        }
        if (!doneFullScan) {
            fullScan();
            return tryFindUtf8(hash, target);
        }
        return null;
    }

    private AbstractPoolEntry.ClassEntryImpl tryFindClassOrInterface(int hash, ClassDesc cd) {
        while (true) {
            EntryMap map = map();
            for (int token = map.firstToken(hash); token != -1; token = map.nextToken(hash, token)) {
                PoolEntry e = entryByIndex(map.getIndexByToken(token));
                if (e.tag() == TAG_CLASS
                        && e instanceof AbstractPoolEntry.ClassEntryImpl ce) {
                    var esym = ce.sym;

                    if (esym != null) {
                        if (cd.equals(esym)) {
                            return ce; // definite match
                        }
                        continue; // definite mismatch
                    }

                    // no symbol available
                    if (ce.ref1.equalsString(Util.toInternalName(cd))) {
                        // definite match, propagate symbol
                        ce.sym = cd;
                        return ce;
                    }
                    // definite mismatch
                }
            }
            if (!doneFullScan) {
                fullScan();
                continue;
            }
            return null;
        }
    }

    private AbstractPoolEntry.ClassEntryImpl classEntryForClassOrInterface(ClassDesc cd) {
        var desc = cd.descriptorString();

        int hash = AbstractPoolEntry.hashClassFromDescriptor(desc.hashCode());
        var ce = tryFindClassOrInterface(hash, cd);
        if (ce != null)
            return ce;

        String internalName = Util.toInternalName(cd);
        var utfHash = internalName.hashCode();
        var utf = tryFindUtf8(AbstractPoolEntry.hashString(utfHash), internalName);
        if (utf == null)
            utf = internalAdd(new AbstractPoolEntry.Utf8EntryImpl(this, size, internalName, utfHash));

        return internalAdd(new AbstractPoolEntry.ClassEntryImpl(this, size, utf, hash, cd));
    }

    private AbstractPoolEntry.ClassEntryImpl tryFindClassEntry(int hash, AbstractPoolEntry.Utf8EntryImpl utf8) {
        EntryMap map = map();
        for (int token = map.firstToken(hash); token != -1; token = map.nextToken(hash, token)) {
            PoolEntry e = entryByIndex(map.getIndexByToken(token));
            if (e.tag() == TAG_CLASS
                    && e instanceof AbstractPoolEntry.ClassEntryImpl ce
                    && ce.ref1.equalsUtf8(utf8))
                return ce;
        }
        if (!doneFullScan) {
            fullScan();
            return tryFindClassEntry(hash, utf8);
        }
        return null;
    }

    @Override
    public AbstractPoolEntry.Utf8EntryImpl utf8Entry(ClassDesc desc) {
        var utf8 = utf8Entry(desc.descriptorString());
        if (utf8.typeSym == null)
            utf8.typeSym = desc;
        return utf8;
    }

    @Override
    public Utf8Entry utf8Entry(MethodTypeDesc desc) {
        var utf8 = utf8Entry(desc.descriptorString());
        if (utf8.typeSym == null)
            utf8.typeSym = desc;
        return utf8;
    }

    @Override
    public AbstractPoolEntry.Utf8EntryImpl utf8Entry(String s) {
        int contentHash = s.hashCode();
        var ce = tryFindUtf8(AbstractPoolEntry.hashString(contentHash), s);
        return ce == null ? internalAdd(new AbstractPoolEntry.Utf8EntryImpl(this, size, s, contentHash)) : ce;
    }

    AbstractPoolEntry.Utf8EntryImpl maybeCloneUtf8Entry(Utf8Entry entry) {
        AbstractPoolEntry.Utf8EntryImpl e = (AbstractPoolEntry.Utf8EntryImpl) entry;
        if (e.constantPool == this || e.constantPool == parent)
            return e;
        AbstractPoolEntry.Utf8EntryImpl ce = tryFindUtf8(e.hashCode(), e);
        return ce == null ? internalAdd(new AbstractPoolEntry.Utf8EntryImpl(this, size, e)) : ce;
    }

    @Override
    public AbstractPoolEntry.ClassEntryImpl classEntry(Utf8Entry nameEntry) {
        var ne = maybeCloneUtf8Entry(nameEntry);
        return classEntry(ne, ne.mayBeArrayDescriptor());
    }

    AbstractPoolEntry.ClassEntryImpl classEntry(AbstractPoolEntry.Utf8EntryImpl ne, boolean isArray) {
        int hash = AbstractPoolEntry.hashClassFromUtf8(isArray, ne);
        var e = tryFindClassEntry(hash, ne);
        return e == null ? internalAdd(new AbstractPoolEntry.ClassEntryImpl(this, size, ne, hash,
                isArray && ne.typeSym instanceof ClassDesc cd ? cd : null)) : e;
    }

    @Override
    public ClassEntry classEntry(ClassDesc cd) {
        if (cd.isClassOrInterface()) { // implicit null check
            return classEntryForClassOrInterface(cd);
        }
        if (cd.isArray()) {
            return classEntry(utf8Entry(cd), true);
        }
        throw new IllegalArgumentException("Cannot be encoded as ClassEntry: " + cd.displayName());
    }

    AbstractPoolEntry.ClassEntryImpl cloneClassEntry(AbstractPoolEntry.ClassEntryImpl e) {
        var ce = tryFindClassEntry(e.hashCode(), e.ref1);
        if (ce != null) {
            var mySym = e.sym;
            if (ce.sym == null && mySym != null) {
                ce.sym = mySym;
            }
            return ce;
        }

        var utf8 = maybeCloneUtf8Entry(e.ref1); // call order matters
        return internalAdd(new AbstractPoolEntry.ClassEntryImpl(this, size,
                utf8, e.hashCode(), e.sym));
    }

    @Override
    public PackageEntry packageEntry(Utf8Entry nameEntry) {
        AbstractPoolEntry.Utf8EntryImpl ne = maybeCloneUtf8Entry(nameEntry);
        var e = (AbstractPoolEntry.PackageEntryImpl) findEntry(TAG_PACKAGE, ne);
        return e == null ? internalAdd(new AbstractPoolEntry.PackageEntryImpl(this, size, ne)) : e;
    }

    @Override
    public ModuleEntry moduleEntry(Utf8Entry nameEntry) {
        AbstractPoolEntry.Utf8EntryImpl ne = maybeCloneUtf8Entry(nameEntry);
        var e = (AbstractPoolEntry.ModuleEntryImpl) findEntry(TAG_MODULE, ne);
        return e == null ? internalAdd(new AbstractPoolEntry.ModuleEntryImpl(this, size, ne)) : e;
    }

    @Override
    public AbstractPoolEntry.NameAndTypeEntryImpl nameAndTypeEntry(Utf8Entry nameEntry, Utf8Entry typeEntry) {
        AbstractPoolEntry.Utf8EntryImpl ne = maybeCloneUtf8Entry(nameEntry);
        AbstractPoolEntry.Utf8EntryImpl te = maybeCloneUtf8Entry(typeEntry);
        var e = (AbstractPoolEntry.NameAndTypeEntryImpl) findEntry(TAG_NAME_AND_TYPE, ne, te);
        return e == null ? internalAdd(new AbstractPoolEntry.NameAndTypeEntryImpl(this, size, ne, te)) : e;
    }

    @Override
    public FieldRefEntry fieldRefEntry(ClassEntry owner, NameAndTypeEntry nameAndType) {
        var oe = AbstractPoolEntry.maybeClone(this, (AbstractPoolEntry.ClassEntryImpl) owner);
        var ne = AbstractPoolEntry.maybeClone(this, (AbstractPoolEntry.NameAndTypeEntryImpl) nameAndType);
        var e = (AbstractPoolEntry.FieldRefEntryImpl) findEntry(TAG_FIELDREF, oe, ne);
        return e == null ? internalAdd(new AbstractPoolEntry.FieldRefEntryImpl(this, size, oe, ne)) : e;
    }

    @Override
    public MethodRefEntry methodRefEntry(ClassEntry owner, NameAndTypeEntry nameAndType) {
        var oe = AbstractPoolEntry.maybeClone(this, (AbstractPoolEntry.ClassEntryImpl) owner);
        var ne = AbstractPoolEntry.maybeClone(this, (AbstractPoolEntry.NameAndTypeEntryImpl) nameAndType);
        var e = (AbstractPoolEntry.MethodRefEntryImpl) findEntry(TAG_METHODREF, oe, ne);
        return e == null ? internalAdd(new AbstractPoolEntry.MethodRefEntryImpl(this, size, oe, ne)) : e;
    }

    @Override
    public InterfaceMethodRefEntry interfaceMethodRefEntry(ClassEntry owner, NameAndTypeEntry nameAndType) {
        var oe = AbstractPoolEntry.maybeClone(this, (AbstractPoolEntry.ClassEntryImpl) owner);
        var ne = AbstractPoolEntry.maybeClone(this, (AbstractPoolEntry.NameAndTypeEntryImpl) nameAndType);
        var e = (AbstractPoolEntry.InterfaceMethodRefEntryImpl) findEntry(TAG_INTERFACE_METHODREF, oe, ne);
        return e == null ? internalAdd(new AbstractPoolEntry.InterfaceMethodRefEntryImpl(this, size, oe, ne)) : e;
    }

    @Override
    public MethodTypeEntry methodTypeEntry(MethodTypeDesc descriptor) {
        return methodTypeEntry(utf8Entry(descriptor));
    }

    @Override
    public MethodTypeEntry methodTypeEntry(Utf8Entry descriptor) {
        AbstractPoolEntry.Utf8EntryImpl de = maybeCloneUtf8Entry(descriptor);
        var e = (AbstractPoolEntry.MethodTypeEntryImpl) findEntry(TAG_METHOD_TYPE, de);
        return e == null ? internalAdd(new AbstractPoolEntry.MethodTypeEntryImpl(this, size, de)) : e;
    }

    @Override
    public MethodHandleEntry methodHandleEntry(int refKind, MemberRefEntry reference) {
        reference = AbstractPoolEntry.maybeClone(this, reference);
        int hash = AbstractPoolEntry.hash2(TAG_METHOD_HANDLE, refKind, reference.index());
        EntryMap map1 = map();
        for (int token = map1.firstToken(hash); token != -1; token = map1.nextToken(hash, token)) {
            PoolEntry e = entryByIndex(map1.getIndexByToken(token));
            if (e.tag() == TAG_METHOD_HANDLE
                && e instanceof AbstractPoolEntry.MethodHandleEntryImpl ce
                && ce.kind() == refKind && ce.reference() == reference)
                return ce;
        }
        if (!doneFullScan) {
            fullScan();
            return methodHandleEntry(refKind, reference);
        }
        return internalAdd(new AbstractPoolEntry.MethodHandleEntryImpl(this, size,
                hash, refKind, (AbstractPoolEntry.AbstractMemberRefEntry) reference), hash);
    }

    @Override
    public InvokeDynamicEntry invokeDynamicEntry(BootstrapMethodEntry bootstrapMethodEntry,
                                                 NameAndTypeEntry nameAndType) {
        if (!canWriteDirect(bootstrapMethodEntry.constantPool()))
            bootstrapMethodEntry = bsmEntry(bootstrapMethodEntry.bootstrapMethod(),
                                            bootstrapMethodEntry.arguments());
        nameAndType = AbstractPoolEntry.maybeClone(this, nameAndType);
        int hash = AbstractPoolEntry.hash2(TAG_INVOKE_DYNAMIC,
                bootstrapMethodEntry.bsmIndex(), nameAndType.index());
        EntryMap map1 = map();
        for (int token = map1.firstToken(hash); token != -1; token = map1.nextToken(hash, token)) {
            PoolEntry e = entryByIndex(map1.getIndexByToken(token));
            if (e.tag() == TAG_INVOKE_DYNAMIC
                && e instanceof AbstractPoolEntry.InvokeDynamicEntryImpl ce
                && ce.bootstrap() == bootstrapMethodEntry && ce.nameAndType() == nameAndType)
                return ce;
        }
        if (!doneFullScan) {
            fullScan();
            return invokeDynamicEntry(bootstrapMethodEntry, nameAndType);
        }

        AbstractPoolEntry.InvokeDynamicEntryImpl ce =
                new AbstractPoolEntry.InvokeDynamicEntryImpl(this, size, hash,
                        (BootstrapMethodEntryImpl) bootstrapMethodEntry,
                        (AbstractPoolEntry.NameAndTypeEntryImpl) nameAndType);
        internalAdd(ce, hash);
        return ce;
    }

    @Override
    public ConstantDynamicEntry constantDynamicEntry(BootstrapMethodEntry bootstrapMethodEntry,
                                                     NameAndTypeEntry nameAndType) {
        if (!canWriteDirect(bootstrapMethodEntry.constantPool()))
            bootstrapMethodEntry = bsmEntry(bootstrapMethodEntry.bootstrapMethod(),
                                            bootstrapMethodEntry.arguments());
        nameAndType = AbstractPoolEntry.maybeClone(this, nameAndType);
        int hash = AbstractPoolEntry.hash2(TAG_DYNAMIC,
                bootstrapMethodEntry.bsmIndex(), nameAndType.index());
        EntryMap map1 = map();
        for (int token = map1.firstToken(hash); token != -1; token = map1.nextToken(hash, token)) {
            PoolEntry e = entryByIndex(map1.getIndexByToken(token));
            if (e.tag() == TAG_DYNAMIC
                && e instanceof AbstractPoolEntry.ConstantDynamicEntryImpl ce
                && ce.bootstrap() == bootstrapMethodEntry && ce.nameAndType() == nameAndType)
                return ce;
        }
        if (!doneFullScan) {
            fullScan();
            return constantDynamicEntry(bootstrapMethodEntry, nameAndType);
        }

        AbstractPoolEntry.ConstantDynamicEntryImpl ce =
                new AbstractPoolEntry.ConstantDynamicEntryImpl(this, size, hash,
                        (BootstrapMethodEntryImpl) bootstrapMethodEntry,
                        (AbstractPoolEntry.NameAndTypeEntryImpl) nameAndType);
        internalAdd(ce, hash);
        return ce;
    }

    @Override
    public IntegerEntry intEntry(int value) {
        var e = findIntEntry(value);
        return e == null ? internalAdd(new AbstractPoolEntry.IntegerEntryImpl(this, size, value)) : e;
    }

    @Override
    public FloatEntry floatEntry(float value) {
        var e = findFloatEntry(value);
        return e == null ? internalAdd(new AbstractPoolEntry.FloatEntryImpl(this, size, value)) : e;
    }

    @Override
    public LongEntry longEntry(long value) {
        var e = findLongEntry(value);
        return e == null ? internalAdd(new AbstractPoolEntry.LongEntryImpl(this, size, value)) : e;
    }

    @Override
    public DoubleEntry doubleEntry(double value) {
        var e = findDoubleEntry(value);
        return e == null ? internalAdd(new AbstractPoolEntry.DoubleEntryImpl(this, size, value)) : e;
    }

    @Override
    public StringEntry stringEntry(Utf8Entry utf8) {
        AbstractPoolEntry.Utf8EntryImpl ue = maybeCloneUtf8Entry(utf8);
        var e = (AbstractPoolEntry.StringEntryImpl) findEntry(TAG_STRING, ue);
        return e == null ? internalAdd(new AbstractPoolEntry.StringEntryImpl(this, size, ue)) : e;
    }

    @Override
    public BootstrapMethodEntry bsmEntry(MethodHandleEntry methodReference,
                                         List<LoadableConstantEntry> arguments) {
        methodReference = AbstractPoolEntry.maybeClone(this, methodReference);
        for (LoadableConstantEntry a : arguments) {
            if (!canWriteDirect(a.constantPool())) {
                // copy args list
                LoadableConstantEntry[] arr = arguments.toArray(new LoadableConstantEntry[0]);
                for (int i = 0; i < arr.length; i++)
                    arr[i] = AbstractPoolEntry.maybeClone(this, arr[i]);
                arguments = List.of(arr);

                break;
            }
        }
        AbstractPoolEntry.MethodHandleEntryImpl mre = (AbstractPoolEntry.MethodHandleEntryImpl) methodReference;
        int hash = BootstrapMethodEntryImpl.computeHashCode(mre, arguments);
        EntryMap map = bsmMap();
        for (int token = map.firstToken(hash); token != -1; token = map.nextToken(hash, token)) {
            BootstrapMethodEntryImpl e = bootstrapMethodEntry(map.getIndexByToken(token));
            if (e.bootstrapMethod() == mre && e.arguments().equals(arguments)) {
                return e;
            }
        }
        BootstrapMethodEntryImpl ne = new BootstrapMethodEntryImpl(this, bsmSize, hash, mre, arguments);
        return internalAdd(ne, hash);
    }
}
