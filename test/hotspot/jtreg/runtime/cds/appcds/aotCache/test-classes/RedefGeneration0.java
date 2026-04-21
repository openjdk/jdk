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
 *
 */

// These are "generation 0" test classes used in redefinition tests.
// These classes are loaded from the classpath, and will later be redefined
// with the versions in RedefGeneration1.java

class RedefFoo {
    static int foo0() {
        return 10;
    }
    static int foo1(Runnable r) {
        return RedefBar.bar0(r);
    }
}

class RedefBar {
    static int bar0(Runnable r) {
        r.run();
        return bar1();
    }
    static int bar1() {
        return 1;
    }
}

// The following classes will be redefined
class RedefTaz0 { static int x1, x2, x3, x4, x5; int a; Runnable x() { return () -> {a += 1;}; } }
class RedefTaz1 { static int x1, x2, x3, x4, x5; int a; Runnable x() { return () -> {a += 1;}; } }
class RedefTaz2 { static int x1, x2, x3, x4, x5; int a; Runnable x() { return () -> {a += 1;}; } }
class RedefTaz3 { static int x1, x2, x3, x4, x5; int a; Runnable x() { return () -> {a += 1;}; } }
class RedefTaz4 { static int x1, x2, x3, x4, x5; int a; Runnable x() { return () -> {a += 1;}; } }

// The following classes will be loaded after the RedefTaz* classes are redefined.
// They may reuse the constant pools that were freed during the redefinitions of RedefTaz*.
//
// These classes are NOT redefined in the training run, so they should be stored into AOT
// configuration and AOT cache.
//
// The Qux classes have a smaller constant pool size (as defined in the classfile) than the
// Taz classes, so they are likely to reuse the space of the constant pools freed from Taz.
// However, Qux uses a larger ConstantPool::resolved_reference(), as it has one extra
// String. Without the JDK-8381117 fix, the JVM would crash during the training run inside
// ConstantPool::prepare_resolved_references_for_archiving().
class Qux0 { static final String s = "x"; int a; Runnable x() { return () -> {a += 1;}; } }
class Qux1 { static final String s = "x"; int a; Runnable x() { return () -> {a += 1;}; } }
class Qux2 { static final String s = "x"; int a; Runnable x() { return () -> {a += 1;}; } }
class Qux3 { static final String s = "x"; int a; Runnable x() { return () -> {a += 1;}; } }
class Qux4 { static final String s = "x"; int a; Runnable x() { return () -> {a += 1;}; } }
class Qux5 { static final String s = "x"; int a; Runnable x() { return () -> {a += 1;}; } }
class Qux6 { static final String s = "x"; int a; Runnable x() { return () -> {a += 1;}; } }
class Qux7 { static final String s = "x"; int a; Runnable x() { return () -> {a += 1;}; } }
class Qux8 { static final String s = "x"; int a; Runnable x() { return () -> {a += 1;}; } }
class Qux9 { static final String s = "x"; int a; Runnable x() { return () -> {a += 1;}; } }
