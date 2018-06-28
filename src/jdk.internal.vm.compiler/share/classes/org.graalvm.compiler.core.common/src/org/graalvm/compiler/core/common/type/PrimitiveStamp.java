/*
 * Copyright (c) 2014, 2014, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.core.common.type;

import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.MemoryAccessProvider;

/**
 * Type describing primitive values.
 */
public abstract class PrimitiveStamp extends ArithmeticStamp {

    private final int bits;

    protected PrimitiveStamp(int bits, ArithmeticOpTable ops) {
        super(ops);
        this.bits = bits;
    }

    /**
     * The width in bits of the value described by this stamp.
     */
    public int getBits() {
        return bits;
    }

    public static int getBits(Stamp stamp) {
        if (stamp instanceof PrimitiveStamp) {
            return ((PrimitiveStamp) stamp).getBits();
        } else {
            return 0;
        }
    }

    @Override
    public Constant readConstant(MemoryAccessProvider provider, Constant base, long displacement) {
        try {
            return provider.readPrimitiveConstant(getStackKind(), base, displacement, getBits());
        } catch (IllegalArgumentException e) {
            /*
             * It's possible that the base and displacement aren't valid together so simply return
             * null.
             */
            return null;
        }
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + bits;
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof PrimitiveStamp)) {
            return false;
        }
        PrimitiveStamp other = (PrimitiveStamp) obj;
        if (bits != other.bits) {
            return false;
        }
        return super.equals(obj);
    }
}
