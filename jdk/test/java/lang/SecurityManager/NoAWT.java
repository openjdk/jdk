/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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

/* @test
 * @bug 8004502 8008793
 * @summary Sanity check that SecurityManager methods that check AWTPermission
 *   behave as expected when AWT is not present
 */

import java.security.AllPermission;
import java.security.Permission;

public class NoAWT {

    static class MySecurityManager extends SecurityManager {
        Class<?> expectedClass;

        void setExpectedPermissionClass(Class<?> c) {
            expectedClass = c;
        }

        @Override
        public void checkPermission(Permission perm) {
            if (perm.getClass() != expectedClass)
                throw new RuntimeException("Got: " + perm.getClass() + ", expected: " + expectedClass);
            super.checkPermission(perm);
        }
    }

    public static void main(String[] args) {
        Class<?> awtPermissionClass = null;
        try {
            awtPermissionClass = Class.forName("java.awt.AWTPermission");
        } catch (ClassNotFoundException ignore) { }

        MySecurityManager sm = new MySecurityManager();
        if (awtPermissionClass != null) {
            sm.setExpectedPermissionClass(awtPermissionClass);
        } else {
            sm.setExpectedPermissionClass(AllPermission.class);
        }

        try {
            sm.checkAwtEventQueueAccess();
            throw new RuntimeException("SecurityException expected");
        } catch (SecurityException expected) { }

        try {
            sm.checkSystemClipboardAccess();
            throw new RuntimeException("SecurityException expected");
        } catch (SecurityException expected) { }

        try {
            sm.checkTopLevelWindow(null);
            throw new RuntimeException("NullPointException expected");
        } catch (NullPointerException expected) { }

        if (sm.checkTopLevelWindow(new Object())) {
            throw new RuntimeException("checkTopLevelWindow expected to return false");
        }
    }
}
