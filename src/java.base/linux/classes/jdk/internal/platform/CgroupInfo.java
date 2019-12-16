/*
 * Copyright (c) 2020, Red Hat Inc.
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

package jdk.internal.platform;

/**
 * Data structure to hold info from /proc/self/cgroup
 *
 * man 7 cgroups
 *
 * @see CgroupSubsystemFactory
 */
class CgroupInfo {

    private final String name;
    private final int hierarchyId;
    private final boolean enabled;

    private CgroupInfo(String name, int hierarchyId, boolean enabled) {
        this.name = name;
        this.hierarchyId = hierarchyId;
        this.enabled = enabled;
    }

    String getName() {
        return name;
    }

    int getHierarchyId() {
        return hierarchyId;
    }

    boolean isEnabled() {
        return enabled;
    }

    static CgroupInfo fromCgroupsLine(String line) {
        String[] tokens = line.split("\\s+");
        if (tokens.length != 4) {
            return null;
        }
        // discard 3'rd field, num_cgroups
        return new CgroupInfo(tokens[0] /* name */,
                              Integer.parseInt(tokens[1]) /* hierarchyId */,
                              (Integer.parseInt(tokens[3]) == 1) /* enabled */);
    }

}
