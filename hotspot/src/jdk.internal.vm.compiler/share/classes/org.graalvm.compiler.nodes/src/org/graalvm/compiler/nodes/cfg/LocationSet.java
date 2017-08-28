/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.nodes.cfg;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.graalvm.word.LocationIdentity;

public class LocationSet {
    private LocationIdentity firstLocation;
    private List<LocationIdentity> list;

    public LocationSet() {
        list = null;
    }

    public LocationSet(LocationSet other) {
        this.firstLocation = other.firstLocation;
        if (other.list != null && other.list.size() > 0) {
            list = new ArrayList<>(other.list);
        }
    }

    private void initList() {
        if (list == null) {
            list = new ArrayList<>(4);
        }
    }

    public boolean isEmpty() {
        return firstLocation == null;
    }

    public boolean isAny() {
        return firstLocation != null && firstLocation.isAny();
    }

    public void add(LocationIdentity location) {
        if (this.isAny()) {
            return;
        } else if (location.isAny()) {
            firstLocation = location;
            list = null;
        } else if (location.isImmutable()) {
            return;
        } else {
            assert location.isMutable() && location.isSingle();
            if (firstLocation == null) {
                firstLocation = location;
            } else if (location.equals(firstLocation)) {
                return;
            } else {
                initList();
                for (int i = 0; i < list.size(); ++i) {
                    LocationIdentity value = list.get(i);
                    if (location.equals(value)) {
                        return;
                    }
                }
                list.add(location);
            }
        }
    }

    public void addAll(LocationSet other) {
        if (other.firstLocation != null) {
            add(other.firstLocation);
        }
        List<LocationIdentity> otherList = other.list;
        if (otherList != null) {
            for (LocationIdentity l : otherList) {
                add(l);
            }
        }
    }

    public boolean contains(LocationIdentity locationIdentity) {
        assert locationIdentity.isSingle();
        assert locationIdentity.isMutable();
        if (LocationIdentity.any().equals(firstLocation)) {
            return true;
        }
        if (locationIdentity.equals(firstLocation)) {
            return true;
        }
        if (list != null) {
            for (int i = 0; i < list.size(); ++i) {
                LocationIdentity value = list.get(i);
                if (locationIdentity.equals(value)) {
                    return true;
                }
            }
        }
        return false;
    }

    public List<LocationIdentity> getCopyAsList() {
        ArrayList<LocationIdentity> result = new ArrayList<>();
        if (firstLocation != null) {
            result.add(firstLocation);
        }
        if (list != null) {
            result.addAll(list);
        }
        return result;
    }

    @Override
    public String toString() {
        if (this.isAny()) {
            return "ANY";
        } else if (this.isEmpty()) {
            return "EMPTY";
        } else {
            List<LocationIdentity> copyAsList = getCopyAsList();
            return Arrays.toString(copyAsList.toArray(new LocationIdentity[0]));
        }
    }
}
