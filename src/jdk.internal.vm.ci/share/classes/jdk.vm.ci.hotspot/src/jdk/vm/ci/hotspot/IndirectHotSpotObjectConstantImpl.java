/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package jdk.vm.ci.hotspot;

import static jdk.vm.ci.hotspot.HotSpotJVMCIRuntime.runtime;

import jdk.vm.ci.meta.JavaConstant;

final class IndirectHotSpotObjectConstantImpl extends HotSpotObjectConstantImpl {
    /**
     * An object handle in {@code JVMCI::_jvmci_handles}.
     */
    final long objectHandle;
    private int hashCode;

    final IndirectHotSpotObjectConstantImpl base;

    @VMEntryPoint
    private IndirectHotSpotObjectConstantImpl(long objectHandle, boolean compressed, boolean skipRegister) {
        super(compressed);
        assert objectHandle != 0 && UnsafeAccess.UNSAFE.getLong(objectHandle) != 0;
        this.objectHandle = objectHandle;
        this.base = null;
        if (!skipRegister) {
            HandleCleaner.create(this, objectHandle);
        }
    }

    private IndirectHotSpotObjectConstantImpl(IndirectHotSpotObjectConstantImpl base, boolean compressed) {
        super(compressed);
        // This is a variant of an original object that only varies in compress vs uncompressed.
        // Instead of creating a new handle, reference that object and objectHandle.
        this.objectHandle = base.objectHandle;
        // There should only be on level of indirection to the base object.
        assert base.base == null || base.base.base == null;
        this.base = base.base != null ? base.base : base;
    }

    @Override
    public JavaConstant compress() {
        assert !compressed;
        return new IndirectHotSpotObjectConstantImpl(this, true);
    }

    @Override
    public JavaConstant uncompress() {
        assert compressed;
        return new IndirectHotSpotObjectConstantImpl(this, false);
    }

    @Override
    public int getIdentityHashCode() {
        int hash = hashCode;
        if (hash == 0) {
            hash = runtime().compilerToVm.getIdentityHashCode(this);
            if (hash == 0) {
                hash = 31;
            }
            hashCode = hash;
        }
        return hash;
    }
}
