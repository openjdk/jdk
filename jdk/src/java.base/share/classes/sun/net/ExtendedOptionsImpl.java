/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package sun.net;

import java.net.*;
import jdk.net.*;
import java.io.IOException;
import java.io.FileDescriptor;
import java.security.PrivilegedAction;
import java.security.AccessController;
import java.lang.reflect.Field;
import java.util.Set;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Collections;

/**
 * Contains the native implementation for extended socket options
 * together with some other static utilities
 */
public class ExtendedOptionsImpl {

    static {
        AccessController.doPrivileged((PrivilegedAction<Void>)() -> {
            System.loadLibrary("net");
            return null;
        });
        init();
    }

    private ExtendedOptionsImpl() {}

    public static void checkSetOptionPermission(SocketOption<?> option) {
        SecurityManager sm = System.getSecurityManager();
        if (sm == null) {
            return;
        }
        String check = "setOption." + option.name();
        sm.checkPermission(new NetworkPermission(check));
    }

    public static void checkGetOptionPermission(SocketOption<?> option) {
        SecurityManager sm = System.getSecurityManager();
        if (sm == null) {
            return;
        }
        String check = "getOption." + option.name();
        sm.checkPermission(new NetworkPermission(check));
    }

    public static void checkValueType(Object value, Class<?> type) {
        if (!type.isAssignableFrom(value.getClass())) {
            String s = "Found: " + value.getClass().toString() + " Expected: "
                        + type.toString();
            throw new IllegalArgumentException(s);
        }
    }

    private static native void init();

    /*
     * Extension native implementations
     *
     * SO_FLOW_SLA
     */
    public static native void setFlowOption(FileDescriptor fd, SocketFlow f);
    public static native void getFlowOption(FileDescriptor fd, SocketFlow f);
    public static native boolean flowSupported();
}
