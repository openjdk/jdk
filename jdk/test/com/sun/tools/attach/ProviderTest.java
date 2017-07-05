/*
 * Copyright (c) 2005, Oracle and/or its affiliates. All rights reserved.
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
 *
 *
 * Unit test for Attach API. Attaches to the given VM and performs a number
 * unit tests.
 */
import com.sun.tools.attach.VirtualMachine;
import com.sun.tools.attach.spi.AttachProvider;

public class ProviderTest {
    public static void main(String args[]) throws Exception {
        // deal with internal builds where classes are loaded from the
        // 'classes' directory rather than rt.jar
        ClassLoader cl = AttachProvider.class.getClassLoader();
        if (cl != ClassLoader.getSystemClassLoader()) {
            System.out.println("Attach API not loaded by system class loader - test skipped");
            return;
        }
        VirtualMachine.attach("simple:1234").detach();
    }
}
