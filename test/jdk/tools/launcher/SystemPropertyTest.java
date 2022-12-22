/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
 * Child launched by MacOSAppNamePropertyTest.java
 * If the system property apple.awt.application.name is unset, it should default
 * to the name of this main program class, less any package name.
 * If it is set, then it should be used instead of the class name.
 * The arg. to the test indicates the *expected* name.
 * The test will fail if the property is not set or does not match
 */
public class SystemPropertyTest {

    public static void main(String[]args) {
        String prop = System.getProperty("apple.awt.application.name");
        if (prop == null) {
            throw new RuntimeException("Property not set");
        }
        if (!prop.equals(args[0])) {
            throw new RuntimeException("Got " + prop + " expected " + args[0]);
        }
    }
}
