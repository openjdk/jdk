/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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

/**
 * JDK-8191468: jdk.scripting.nashorn.shell (jjs) module should use optional dependency for java.compiler module
 *
 * @test
 * @run
 */

var optJjsMod = java.lang.ModuleLayer.boot().findModule("jdk.scripting.nashorn.shell");

// make sure that the module exists!
Assert.assertTrue(optJjsMod.isPresent());

// jdk.scripting.nashorn.shell should use optional dependency for java.compiler
var javaCompilerDependency = optJjsMod.get().
        descriptor.requires().
        stream().
        filter(function(mod) { return mod.name() == "java.compiler" }).
        findFirst();

// java.compiler dependency should be present
Assert.assertTrue(javaCompilerDependency.isPresent());

var Modifier = java.lang.module.ModuleDescriptor.Requires.Modifier;
// java.compiler requires should be "requires static"
Assert.assertTrue(javaCompilerDependency.get().modifiers().contains(Modifier.STATIC));
