/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.serviceprovider;

import java.util.Arrays;

import jdk.vm.ci.meta.SpeculationLog.SpeculationReason;

/**
 * An implementation of {@link SpeculationReason} based on direct, unencoded values.
 */
public final class UnencodedSpeculationReason implements SpeculationReason {
    final int groupId;
    final String groupName;
    final Object[] context;

    UnencodedSpeculationReason(int groupId, String groupName, Object[] context) {
        this.groupId = groupId;
        this.groupName = groupName;
        this.context = context;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof UnencodedSpeculationReason) {
            UnencodedSpeculationReason that = (UnencodedSpeculationReason) obj;
            return this.groupId == that.groupId && Arrays.equals(this.context, that.context);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return groupId + Arrays.hashCode(this.context);
    }

    @Override
    public String toString() {
        return String.format("%s@%d%s", groupName, groupId, Arrays.toString(context));
    }
}
