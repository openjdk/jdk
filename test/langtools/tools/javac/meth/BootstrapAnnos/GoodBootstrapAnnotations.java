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

/*
 * @test
 * @summary Verify okay BSMs are permitted by annotations
 * @compile GoodBootstrapAnnotations.java
 */

import java.lang.constant.ConstantDesc;
import java.lang.invoke.CallSite;
import java.lang.invoke.CallSiteBootstrap;
import java.lang.invoke.ConstantBootstrap;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.TypeDescriptor;

public class GoodBootstrapAnnotations {
    @CallSiteBootstrap static CallSite bootstrap(MethodHandles.Lookup caller, String name, MethodType type, Object... args) { return null; }
    @CallSiteBootstrap static CallSite bootstrap(Object... args) { return null; }
    @CallSiteBootstrap static Object bootstrap0(Object... args) { return null; }
    @CallSiteBootstrap static CallSite bootstrap(Object caller, Object... nameAndTypeWithArgs) { return null; }
    @CallSiteBootstrap static CallSite bootstrap(MethodHandles.Lookup caller, String name, MethodType type) { return null; }
    @CallSiteBootstrap static CallSite bootstrap(Object caller, Object name, Object type) { return null; }
    @CallSiteBootstrap static CallSite bootstrap(Object caller, ConstantDesc name, TypeDescriptor type) { return null; }
    @CallSiteBootstrap static Object bootstrap0(Object caller, Object name, Object type) { return null; }
    @CallSiteBootstrap static CallSite bootstrap(MethodHandles.Lookup caller, String name, MethodType type, Object arg) { return null; }
    @CallSiteBootstrap static CallSite bootstrap(MethodHandles.Lookup caller, String name, MethodType type, String... args) { return null; }
    @CallSiteBootstrap static CallSite bootstrap(MethodHandles.Lookup caller, String name, MethodType type, String x, int y) { return null; }

    class MySite extends ConstantCallSite {
        @CallSiteBootstrap
        MySite(MethodHandles.Lookup caller, String name, MethodType type) {
            super(MethodHandles.empty(type));
        }

        @ConstantBootstrap
        @CallSiteBootstrap
        MySite(MethodHandles.Lookup caller, String name, TypeDescriptor type) {
            super(MethodHandles.empty((MethodType) type));
        }
    }

    // weird condys
    @ConstantBootstrap static void constNull(MethodHandles.Lookup l, Object... o) {}
}
