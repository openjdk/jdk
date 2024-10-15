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

import java.util.List;

import java.lang.classfile.constantpool.ConstantPool;
import java.lang.classfile.BootstrapMethodEntry;
import java.lang.classfile.constantpool.LoadableConstantEntry;
import java.lang.classfile.constantpool.MethodHandleEntry;

import static jdk.internal.classfile.impl.AbstractPoolEntry.MethodHandleEntryImpl;

public final class BootstrapMethodEntryImpl implements BootstrapMethodEntry {

    final int index;
    final int hash;
    private final ConstantPool constantPool;
    private final MethodHandleEntryImpl handle;
    private final List<LoadableConstantEntry> arguments;

    BootstrapMethodEntryImpl(ConstantPool constantPool, int bsmIndex, int hash,
                                 MethodHandleEntryImpl handle,
                                 List<LoadableConstantEntry> arguments) {
        this.index = bsmIndex;
        this.hash = hash;
        this.constantPool = constantPool;
        this.handle = handle;
        this.arguments = List.copyOf(arguments);
    }

    @Override
    public ConstantPool constantPool() {
        return constantPool;
    }

    @Override
    public MethodHandleEntry bootstrapMethod() {
        return handle;
    }

    @Override
    public List<LoadableConstantEntry> arguments() {
        return arguments;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof BootstrapMethodEntry e
                && e.bootstrapMethod().equals(handle)
                && e.arguments().equals(arguments);
    }

    static int computeHashCode(MethodHandleEntryImpl handle,
                               List<? extends LoadableConstantEntry> arguments) {
        return (31 * handle.hashCode() + arguments.hashCode()) | AbstractPoolEntry.NON_ZERO;
    }

    @Override
    public int bsmIndex() { return index; }

    @Override
    public int hashCode() {
        return hash;
    }

    void writeTo(BufWriterImpl writer) {
        writer.writeIndex(bootstrapMethod());
        Util.writeListIndices(writer, arguments());
    }
}
