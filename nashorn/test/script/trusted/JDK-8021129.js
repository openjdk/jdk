/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
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
 * JDK-8021129: Test prevention of access to members of restricted classes.
 * Note that even though the script runs as trusted, we still don't allow
 * access to non-public portions of restricted classes.
 *
 * @test
 * @run
 */

var InternalRunnableSuperclass = Java.type("jdk.nashorn.test.models.InternalRunnableSuperclass");
var r1 = InternalRunnableSuperclass.makeInternalRunnable();
r1.run() // Can execute method from an implemented non-restricted interface
print(r1.toString()) // Can execute public method from a superclass

print(r1.restrictedRun === undefined) // Can't see method from a restricted interface
print(r1.canNotInvokeThis === undefined) // Can't see any other public methods
print(r1.invisibleProperty === undefined) // Can't see any other properties
print(r1.canSeeThisField === undefined) // Can't see fields from superclasses
print(r1.canNotSeeThisField === undefined) // Can't see its own fields

var r2 = new InternalRunnableSuperclass();
print(r2.canSeeThisField) // Superclass field works fine on its own
