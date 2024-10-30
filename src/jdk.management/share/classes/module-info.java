/*
 * Copyright (c) 2015, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.management.ManagementFactory;

/**
 * Defines JDK-specific management interfaces for the JVM.
 *
 * <p> This module contains the JDK's extensions to the standard implementation
 * of the {@link java.lang.management} API and also defines the management
 * interfaces for some other components of the platform.
 *
 * <p> All platform MBeans are registered in the <em>platform MBeanServer</em>
 * which can be obtained with {@link ManagementFactory#getPlatformMBeanServer}.
 *
 * @moduleGraph
 * @since 9
 */
module jdk.management {
    requires transitive java.management;

    exports com.sun.management;
    exports jdk.management;

    provides sun.management.spi.PlatformMBeanProvider with
        com.sun.management.internal.PlatformMBeanProviderImpl;
}

