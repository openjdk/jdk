/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package compiler.cha.packagePrivate;

/**
 * This class provides the unique implementation for {@link OverridenParent#call}, it is abstract
 * so that {@code ciInstanceKlass::unique_concrete_subklass} does not find it.
 */
public abstract class OverridingChild extends OverridenParent {
    // 3 concrete implementations to defeat the bimorphic inlining heuristic
    public static class GrandChild1 extends OverridingChild {}
    public static class GrandChild2 extends OverridingChild {}
    public static class GrandChild3 extends OverridingChild {}

    @Override
    int call() {
        return 1;
    }
}
