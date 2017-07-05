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
 * JDK-8010946: AccessController.doPrivileged() doesn't work as expected.
 * This is actually a broader issue of having Dynalink correctly handle
 * caller-sensitive methods.
 *
 * @test
 * @run
 */

// This is unprivileged code that loads privileged code.
load(__DIR__ + "JDK-8010946-privileged.js")

try {
    // This should fail, even though the code itself resides in the
    // privileged script, as we're invoking it without going through
    // doPrivileged()
    print("Attempting unprivileged execution...")
    executeUnprivileged()
    print("FAIL: Unprivileged execution succeeded!")
} catch(e) {
    print("Unprivileged execution failed with " + e)
}

print()

// This should succeed, as it's going through doPrivileged().
print("Attempting privileged execution...")
executePrivileged()
