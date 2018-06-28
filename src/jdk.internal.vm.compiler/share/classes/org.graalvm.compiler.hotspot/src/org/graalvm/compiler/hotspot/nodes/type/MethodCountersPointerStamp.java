/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.hotspot.nodes.type;

import jdk.vm.ci.hotspot.HotSpotMetaspaceConstant;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.MemoryAccessProvider;
import jdk.vm.ci.meta.MetaAccessProvider;

import org.graalvm.compiler.core.common.type.AbstractPointerStamp;
import org.graalvm.compiler.core.common.type.Stamp;

public final class MethodCountersPointerStamp extends MetaspacePointerStamp {

    private static final MethodCountersPointerStamp METHOD_COUNTERS = new MethodCountersPointerStamp(false, false);

    private static final MethodCountersPointerStamp METHOD_COUNTERS_NON_NULL = new MethodCountersPointerStamp(true, false);

    private static final MethodCountersPointerStamp METHOD_COUNTERS_ALWAYS_NULL = new MethodCountersPointerStamp(false, true);

    public static MethodCountersPointerStamp methodCounters() {
        return METHOD_COUNTERS;
    }

    public static MethodCountersPointerStamp methodCountersNonNull() {
        return METHOD_COUNTERS_NON_NULL;
    }

    private MethodCountersPointerStamp(boolean nonNull, boolean alwaysNull) {
        super(nonNull, alwaysNull);
    }

    @Override
    protected AbstractPointerStamp copyWith(boolean newNonNull, boolean newAlwaysNull) {
        if (newNonNull) {
            assert !newAlwaysNull;
            return METHOD_COUNTERS_NON_NULL;
        } else if (newAlwaysNull) {
            return METHOD_COUNTERS_ALWAYS_NULL;
        } else {
            return METHOD_COUNTERS;
        }
    }

    @Override
    public boolean isCompatible(Stamp otherStamp) {
        if (this == otherStamp) {
            return true;
        }
        return otherStamp instanceof MethodCountersPointerStamp;
    }

    @Override
    public Stamp constant(Constant c, MetaAccessProvider meta) {
        if (JavaConstant.NULL_POINTER.equals(c)) {
            return METHOD_COUNTERS_ALWAYS_NULL;
        } else {
            assert c instanceof HotSpotMetaspaceConstant;
            return METHOD_COUNTERS_NON_NULL;
        }
    }

    @Override
    public Constant readConstant(MemoryAccessProvider provider, Constant base, long displacement) {
        return null;
    }

    @Override
    public String toString() {
        StringBuilder ret = new StringBuilder("MethodCounters*");
        appendString(ret);
        return ret.toString();
    }
}
