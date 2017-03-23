/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * Launched by PermitIllegalAccess to attempt illegal access.
 */

public class AttemptAccess {

    public static void main(String[] args) throws Exception {
        String action = args[0];
        int count = Integer.parseInt(args[1]);

        for (int i=0; i<count; i++) {
            switch (action) {
                case "access":
                    tryAccess();
                    break;
                case "setAccessible":
                    trySetAccessible();
                    break;
                case "trySetAccessible":
                    tryTrySetAccessible();
                    break;
            }
        }
    }

    static void tryAccess() throws Exception {
        Class<?> clazz = Class.forName("sun.security.x509.X500Name");
        Constructor<?> ctor = clazz.getConstructor(String.class);
        Object name = ctor.newInstance("CN=user");
    }

    static void trySetAccessible() throws Exception {
        Method find = ClassLoader.class.getDeclaredMethod("findClass", String.class);
        find.setAccessible(true);
    }

    static void tryTrySetAccessible() throws Exception {
        Method find = ClassLoader.class.getDeclaredMethod("findClass", String.class);
        find.trySetAccessible();
    }

}
