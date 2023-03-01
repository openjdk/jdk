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

import java.util.ArrayList;
import java.util.List;

import jdk.internal.classfile.constantpool.ConstantPool;
import jdk.internal.classfile.constantpool.ConstantPoolBuilder;
import jdk.internal.classfile.BootstrapMethodEntry;
import jdk.internal.classfile.BufWriter;
import jdk.internal.classfile.constantpool.LoadableConstantEntry;
import jdk.internal.classfile.constantpool.MethodHandleEntry;

import static jdk.internal.classfile.impl.AbstractPoolEntry.ConcreteMethodHandleEntry;

public final class BootstrapMethodEntryImpl implements BootstrapMethodEntry {

    final int index;
    final int hash;
    private final ConstantPool constantPool;
    private final ConcreteMethodHandleEntry handle;
    private final List<LoadableConstantEntry> arguments;

    BootstrapMethodEntryImpl(ConstantPool constantPool, int bsmIndex, int hash,
                                 ConcreteMethodHandleEntry handle,
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
        if (obj instanceof BootstrapMethodEntry e
            && e.bootstrapMethod() == handle
            && e.arguments().size() == arguments.size()) {
                for (int i = 0; i < arguments.size(); ++i) {
                    if (e.arguments().get(i) != arguments.get(i)) {
                        return false;
                    }
                }
                return true;
            }
        else
            return false;
    }

    static int computeHashCode(ConcreteMethodHandleEntry handle,
                               List<? extends LoadableConstantEntry> arguments) {
        int hash = handle.hashCode();
        for (LoadableConstantEntry a : arguments) {
            hash = 31 * hash + a.hashCode();
        }
        return AbstractPoolEntry.phiMix(hash);
    }

    @Override
    public int bsmIndex() { return index; }

    @Override
    public int hashCode() {
        return hash;
    }

    @Override
    public void writeTo(BufWriter writer) {
        writer.writeIndex(bootstrapMethod());
        writer.writeListIndices(arguments());
    }
}
