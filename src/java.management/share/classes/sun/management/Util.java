/*
 * Copyright (c) 2003, 2024, Oracle and/or its affiliates. All rights reserved.
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

package sun.management;

import java.lang.management.ManagementPermission;
import java.util.List;
import javax.management.ObjectName;
import javax.management.MalformedObjectNameException;

public class Util {
    private Util() {}  // there are no instances of this class

    private static final String[] EMPTY_STRING_ARRAY = new String[0];
    static String[] toStringArray(List<String> list) {
        return list.toArray(EMPTY_STRING_ARRAY);
    }

    public static ObjectName newObjectName(String domainAndType, String name) {
        return newObjectName(domainAndType + ",name=" + name);
    }

    public static ObjectName newObjectName(String name) {
        try {
            return ObjectName.getInstance(name);
        } catch (MalformedObjectNameException e) {
            throw new IllegalArgumentException(e);
        }
    }

    // Methods retained temporarily due to usage by jdk.management.
    static void checkAccess(ManagementPermission p) {
        // no-op
    }

    static void checkMonitorAccess() throws SecurityException {
        // no-op
    }
    public static void checkControlAccess() throws SecurityException {
        // no-op
    }
}
