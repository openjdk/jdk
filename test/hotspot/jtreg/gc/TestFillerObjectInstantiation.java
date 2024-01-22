/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

package gc;

/*
 * @test TestFillerObjectInstantiation
 * @summary Test that GC filler objects can not be instantiated by Java programs.
 * @library /test/lib
 * @run driver gc.TestFillerObjectInstantiation
 */

public class TestFillerObjectInstantiation {

    private static void testInstantiationFails(String classname) throws Exception {
        System.out.println("trying to instantiate " + classname);
        try {
            Object o = ClassLoader.getSystemClassLoader().loadClass(classname).newInstance();
            throw new Error("Have been able to instantiate " + classname);
        } catch (IllegalAccessException | ClassNotFoundException e) {
            System.out.println("Could not instantiate " + classname + " as expected");
            System.out.println("Message: " + e.toString());
        }
    }

    public static void main(String[] args) throws Exception {
        testInstantiationFails("jdk.internal.vm.FillerObject");
        testInstantiationFails("jdk.internal.vm.FillerElement");
    }
}
