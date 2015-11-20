/*
 * Copyright (c) 2009, 2015, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Arrays;

import jdk.vm.ci.code.Location;
import jdk.vm.ci.code.ReferenceMap;

public final class HotSpotReferenceMap extends ReferenceMap {

    final Location[] objects;
    final Location[] derivedBase;
    final int[] sizeInBytes;
    final int maxRegisterSize;

    public HotSpotReferenceMap(Location[] objects, Location[] derivedBase, int[] sizeInBytes, int maxRegisterSize) {
        this.objects = objects;
        this.derivedBase = derivedBase;
        this.sizeInBytes = sizeInBytes;
        this.maxRegisterSize = maxRegisterSize;
    }

    @Override
    public int hashCode() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof HotSpotReferenceMap) {
            HotSpotReferenceMap that = (HotSpotReferenceMap) obj;
            if (Arrays.equals(objects, that.objects)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String toString() {
        return Arrays.toString(objects);
    }
}
