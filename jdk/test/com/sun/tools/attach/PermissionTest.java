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
 * Unit test for Attach API - this checks that a SecurityException is thrown as
 * expected.
 */
import com.sun.tools.attach.VirtualMachine;
import com.sun.tools.attach.AttachNotSupportedException;
import java.util.Properties;

public class PermissionTest {
    public static void main(String args[]) throws Exception {
        SecurityManager sm = System.getSecurityManager();
        if (sm == null) {
            throw new RuntimeException("Test configuration error - no security manager set");
        }

        String pid = args[0];
        boolean shouldFail = Boolean.parseBoolean(args[1]);

        try {
            VirtualMachine.attach(pid).detach();
            if (shouldFail) {
                throw new RuntimeException("SecurityException should be thrown");
            }
            System.out.println(" - attached to target VM as expected.");
        } catch (Exception x) {
            // AttachNotSupportedException thrown when no providers can be loaded
            if (shouldFail && ((x instanceof AttachNotSupportedException) ||
                (x instanceof SecurityException))) {
                System.out.println(" - exception thrown as expected.");
            } else {
                throw x;
            }
        }
    }
}
