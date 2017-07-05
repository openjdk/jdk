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
 * NASHORN-525 : nashorn misses security access checks 
 *
 * @test
 * @run
 */

function check(code) {
    try {
        eval(code);
        fail("SecurityException expected for : " + code);
    } catch (e) {
        if (! (e instanceof java.lang.SecurityException)) {
            fail("SecurityException expected, but got " + e);
        }
    }
}

// if security manager is absent, pass the test vacuously.
if (java.lang.System.getSecurityManager() != null) {
    // try accessing class from 'sun.*' packages
    check("Packages.sun.misc.Unsafe");
    check("Java.type('sun.misc.Unsafe')");

    // TODO this works in Java8 but not in Java8, disabling for now
    check("java.lang.Class.forName('sun.misc.Unsafe')");

    // try System.exit and System.loadLibrary
    check("java.lang.System.exit(0)");
    check("java.lang.System.loadLibrary('foo')");
}
