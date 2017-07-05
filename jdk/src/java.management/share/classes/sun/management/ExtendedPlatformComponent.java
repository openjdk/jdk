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

package sun.management;

import java.util.Collections;
import java.util.List;
import java.lang.management.PlatformManagedObject;

/**
 * Class to allow for an extended set of platform MXBeans
 */
public final class ExtendedPlatformComponent {
    private ExtendedPlatformComponent() {} // Don't create any instances

    /**
     * Get the extended set of platform MXBeans that should be registered in the
     * platform MBeanServer, or an empty list if there are no such MXBeans.
     */
    public static List<? extends PlatformManagedObject> getMXBeans() {
        return Collections.emptyList();
    }

    /**
     * Returns the extended platform MXBean implementing the given
     * mxbeanInterface, or null if there is no such MXBean.
     */
    public static <T extends PlatformManagedObject>
            T getMXBean(Class<T> mxbeanInterface) {
        return null;
    }
}
