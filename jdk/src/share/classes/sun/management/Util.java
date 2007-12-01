/*
 * Copyright 2003-2004 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package sun.management;

import java.lang.management.*;
import static java.lang.management.ManagementFactory.*;
import java.util.List;

class Util {
    static String getMBeanObjectName(MemoryPoolMXBean pool) {
        return MEMORY_POOL_MXBEAN_DOMAIN_TYPE +
            ",name=" + pool.getName();
    }

    static String getMBeanObjectName(MemoryManagerMXBean mgr) {
        if (mgr instanceof GarbageCollectorMXBean) {
            return getMBeanObjectName((GarbageCollectorMXBean) mgr);
        } else {
            return MEMORY_MANAGER_MXBEAN_DOMAIN_TYPE +
                ",name=" + mgr.getName();
        }
    }

    static String getMBeanObjectName(GarbageCollectorMXBean gc) {
        return GARBAGE_COLLECTOR_MXBEAN_DOMAIN_TYPE +
            ",name=" + gc.getName();
    }

    static RuntimeException newException(Exception e) {
        RuntimeException e1 = new RuntimeException(e.getMessage());
        e1.initCause(e);
        return e1;
    }

    static InternalError newInternalError(Exception e) {
        InternalError e1 = new InternalError(e.getMessage());
        e1.initCause(e);
        return e1;
    }
    static AssertionError newAssertionError(Exception e) {
        AssertionError e1 = new AssertionError(e.getMessage());
        e1.initCause(e);
        return e1;
    }

    private static String[] EMPTY_STRING_ARRAY = new String[0];
    static String[] toStringArray(List<String> list) {
        return (String[]) list.toArray(EMPTY_STRING_ARRAY);
    }
}
