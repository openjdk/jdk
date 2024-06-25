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

import java.lang.classfile.*;
import java.lang.classfile.constantpool.ClassEntry;
import java.lang.classfile.constantpool.ConstantDynamicEntry;
import java.lang.classfile.constantpool.ConstantPool;
import java.lang.classfile.constantpool.ConstantPoolBuilder;
import java.lang.classfile.constantpool.DoubleEntry;
import java.lang.classfile.constantpool.FieldRefEntry;
import java.lang.classfile.constantpool.FloatEntry;
import java.lang.classfile.constantpool.IntegerEntry;
import java.lang.classfile.constantpool.InterfaceMethodRefEntry;
import java.lang.classfile.constantpool.InvokeDynamicEntry;
import java.lang.classfile.constantpool.LoadableConstantEntry;
import java.lang.classfile.constantpool.LongEntry;
import java.lang.classfile.constantpool.MemberRefEntry;
import java.lang.classfile.constantpool.MethodHandleEntry;
import java.lang.classfile.constantpool.MethodRefEntry;
import java.lang.classfile.constantpool.MethodTypeEntry;
import java.lang.classfile.constantpool.ModuleEntry;
import java.lang.classfile.constantpool.NameAndTypeEntry;
import java.lang.classfile.constantpool.PackageEntry;
import java.lang.classfile.constantpool.PoolEntry;
import java.lang.classfile.constantpool.StringEntry;
import java.lang.classfile.constantpool.Utf8Entry;

import java.lang.constant.MethodTypeDesc;
import java.util.List;

public final class TemporaryConstantPool implements ConstantPoolBuilder {

    private TemporaryConstantPool() {}

    public static final TemporaryConstantPool INSTANCE = new TemporaryConstantPool();

    @Override
    public Utf8Entry utf8Entry(String s) {
        return new AbstractPoolEntry.Utf8EntryImpl(this, -1, s);
    }

    @Override
    public IntegerEntry intEntry(int value) {
        return new AbstractPoolEntry.IntegerEntryImpl(this, -1, value);
    }

    @Override
    public FloatEntry floatEntry(float value) {
        return new AbstractPoolEntry.FloatEntryImpl(this, -1, value);
    }

    @Override
    public LongEntry longEntry(long value) {
        return new AbstractPoolEntry.LongEntryImpl(this, -1, value);
    }

    @Override
    public DoubleEntry doubleEntry(double value) {
        return new AbstractPoolEntry.DoubleEntryImpl(this, -1, value);
    }

    @Override
    public ClassEntry classEntry(Utf8Entry name) {
        return new AbstractPoolEntry.ClassEntryImpl(this, -2, (AbstractPoolEntry.Utf8EntryImpl) name);
    }

    @Override
    public PackageEntry packageEntry(Utf8Entry name) {
        return new AbstractPoolEntry.PackageEntryImpl(this, -2, (AbstractPoolEntry.Utf8EntryImpl) name);
    }

    @Override
    public ModuleEntry moduleEntry(Utf8Entry name) {
        return new AbstractPoolEntry.ModuleEntryImpl(this, -2, (AbstractPoolEntry.Utf8EntryImpl) name);
    }

    @Override
    public NameAndTypeEntry nameAndTypeEntry(Utf8Entry nameEntry, Utf8Entry typeEntry) {
        return new AbstractPoolEntry.NameAndTypeEntryImpl(this, -3,
                                                          (AbstractPoolEntry.Utf8EntryImpl) nameEntry,
                                                          (AbstractPoolEntry.Utf8EntryImpl) typeEntry);
    }

    @Override
    public FieldRefEntry fieldRefEntry(ClassEntry owner, NameAndTypeEntry nameAndType) {
        return new AbstractPoolEntry.FieldRefEntryImpl(this, -3,
                                                       (AbstractPoolEntry.ClassEntryImpl) owner,
                                                       (AbstractPoolEntry.NameAndTypeEntryImpl) nameAndType);
    }

    @Override
    public MethodRefEntry methodRefEntry(ClassEntry owner, NameAndTypeEntry nameAndType) {
        return new AbstractPoolEntry.MethodRefEntryImpl(this, -3,
                                                        (AbstractPoolEntry.ClassEntryImpl) owner,
                                                        (AbstractPoolEntry.NameAndTypeEntryImpl) nameAndType);
    }

    @Override
    public InterfaceMethodRefEntry interfaceMethodRefEntry(ClassEntry owner, NameAndTypeEntry nameAndType) {
        return new AbstractPoolEntry.InterfaceMethodRefEntryImpl(this, -3,
                                                                 (AbstractPoolEntry.ClassEntryImpl) owner,
                                                                 (AbstractPoolEntry.NameAndTypeEntryImpl) nameAndType);
    }

    @Override
    public MethodTypeEntry methodTypeEntry(MethodTypeDesc descriptor) {
        throw new UnsupportedOperationException();
    }

    @Override
    public MethodTypeEntry methodTypeEntry(Utf8Entry descriptor) {
        throw new UnsupportedOperationException();
    }

    @Override
    public MethodHandleEntry methodHandleEntry(int refKind, MemberRefEntry reference) {
        throw new UnsupportedOperationException();
    }

    @Override
    public InvokeDynamicEntry invokeDynamicEntry(BootstrapMethodEntry bootstrapMethodEntry, NameAndTypeEntry nameAndType) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ConstantDynamicEntry constantDynamicEntry(BootstrapMethodEntry bootstrapMethodEntry, NameAndTypeEntry nameAndType) {
        throw new UnsupportedOperationException();
    }

    @Override
    public StringEntry stringEntry(Utf8Entry utf8) {
        return new AbstractPoolEntry.StringEntryImpl(this, -2, (AbstractPoolEntry.Utf8EntryImpl) utf8);
    }

    @Override
    public BootstrapMethodEntry bsmEntry(MethodHandleEntry methodReference, List<LoadableConstantEntry> arguments) {
        throw new UnsupportedOperationException();
    }

    @Override
    public PoolEntry entryByIndex(int index) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int size() {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T extends PoolEntry> T entryByIndex(int index, Class<T> cls) {
        throw new UnsupportedOperationException();
    }

    @Override
    public BootstrapMethodEntry bootstrapMethodEntry(int index) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int bootstrapMethodCount() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean canWriteDirect(ConstantPool constantPool) {
        return false;
    }

    @Override
    public boolean writeBootstrapMethods(BufWriter buf) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void writeTo(BufWriter buf) {
        throw new UnsupportedOperationException();
    }
}
