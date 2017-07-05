/*
 * Copyright (c) 2009, Oracle and/or its affiliates. All rights reserved.
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

package java.util.logging;

import java.lang.management.PlatformManagedObject;

/**
 * The {@linkplain PlatformManagedObject platform managed object} for the
 * logging facility.  This interface simply unifies {@link LoggingMXBean}
 * {@link PlatformManagedObject};
 * and it does not specify any new operations.
 *
 * <p>The {@link java.lang.management.ManagementFactory#getPlatformMXBeans(Class)
 * ManagementFactory.getPlatformMXBeans} method can be used to obtain
 * the {@code PlatformLoggingMXBean} object as follows:
 * <pre>
 *     ManagementFactory.getPlatformMXBeans(PlatformLoggingMXBean.class);
 * </pre>
 * or from the {@linkplain java.lang.management.ManagementFactory#getPlatformMBeanServer
 * platform <tt>MBeanServer</tt>}.
 *
 * The {@link javax.management.ObjectName ObjectName} for uniquely
 * identifying the <tt>LoggingMXBean</tt> within an MBeanServer is:
 * <blockquote>
 *           <tt>java.util.logging:type=Logging</tt>
 * </blockquote>
 *
 * The {@link PlatformManagedObject#getObjectName} method
 * can be used to obtain its {@code ObjectName}.
 *
 * @see java.lang.management.PlatformManagedObject
 *
 * @author  Mandy Chung
 * @since   1.7
 */
public interface PlatformLoggingMXBean extends LoggingMXBean, PlatformManagedObject {
}
