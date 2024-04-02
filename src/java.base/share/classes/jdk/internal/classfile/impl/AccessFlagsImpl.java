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

import java.util.Set;
import java.lang.classfile.AccessFlags;
import java.lang.reflect.AccessFlag;

public final class AccessFlagsImpl extends AbstractElement
        implements AccessFlags {

    private final AccessFlag.Location location;
    private final int flagsMask;
    private Set<AccessFlag> flags;

    public  AccessFlagsImpl(AccessFlag.Location location, AccessFlag... flags) {
        this.location = location;
        this.flagsMask = Util.flagsToBits(location, flags);
        this.flags = Set.of(flags);
    }

    public AccessFlagsImpl(AccessFlag.Location location, int mask) {
        this.location = location;
        this.flagsMask = mask;
    }

    @Override
    public int flagsMask() {
        return flagsMask;
    }

    @Override
    public Set<AccessFlag> flags() {
        if (flags == null)
            flags = AccessFlag.maskToAccessFlags(flagsMask, location);
        return flags;
    }

    @Override
    public void writeTo(DirectClassBuilder builder) {
        builder.setFlags(flagsMask);
    }

    @Override
    public void writeTo(DirectMethodBuilder builder) {
        builder.setFlags(flagsMask);
    }

    @Override
    public void writeTo(DirectFieldBuilder builder) {
        builder.setFlags(flagsMask);
    }

    @Override
    public AccessFlag.Location location() {
        return location;
    }

    @Override
    public boolean has(AccessFlag flag) {
        return Util.has(location, flagsMask, flag);
    }

    @Override
    public String toString() {
        return String.format("AccessFlags[flags=%d]", flagsMask);
    }
}
