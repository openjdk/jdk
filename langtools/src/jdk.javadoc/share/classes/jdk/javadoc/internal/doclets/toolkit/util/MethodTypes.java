/*
 * Copyright (c) 2012, 2015, Oracle and/or its affiliates. All rights reserved.
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

package jdk.javadoc.internal.doclets.toolkit.util;

/**
 * Enum representing method types.
 *
 * @author Bhavesh Patel
 */
public enum MethodTypes {
    ALL(0xffff, "doclet.All_Methods", "t0", true),
    STATIC(0x1, "doclet.Static_Methods", "t1", false),
    INSTANCE(0x2, "doclet.Instance_Methods", "t2", false),
    ABSTRACT(0x4, "doclet.Abstract_Methods", "t3", false),
    CONCRETE(0x8, "doclet.Concrete_Methods", "t4", false),
    DEFAULT(0x10, "doclet.Default_Methods", "t5", false),
    DEPRECATED(0x20, "doclet.Deprecated_Methods", "t6", false);

    private final int value;
    private final String resourceKey;
    private final String tabId;
    private final boolean isDefaultTab;

    MethodTypes(int v, String k, String id, boolean dt) {
        this.value = v;
        this.resourceKey = k;
        this.tabId = id;
        this.isDefaultTab = dt;
    }

    public int value() {
        return value;
    }

    public String resourceKey() {
        return resourceKey;
    }

    public String tabId() {
        return tabId;
    }

    public boolean isDefaultTab() {
        return isDefaultTab;
    }
}
