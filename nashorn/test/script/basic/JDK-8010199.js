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
 * JDK-8010199: javax.script.Invocable implementation for nashorn does not return null when matching functions are missing
 *
 * @test
 * @run
 */

var m = new javax.script.ScriptEngineManager();
var e = m.getEngineByName("nashorn");

var iface = e.getInterface(java.lang.Runnable.class);

if (iface != null) {
    fail("Expected interface object to be null");
}

e.eval("var runcalled = false; function run() { runcalled = true }");

iface = e.getInterface(java.lang.Runnable.class);
if (iface == null) {
    fail("Expected interface object to be non-null");
}

iface.run();

if (e.get("runcalled") != true) {
    fail("runcalled is not true");
}
