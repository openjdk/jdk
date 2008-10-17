/*
 * Copyright 2007-2008 Sun Microsystems, Inc.  All Rights Reserved.
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

package javax.management;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import javax.management.remote.JMXConnectionNotification;

/**
 * <p>Specifies the kinds of notification an MBean can emit, when this
 * cannot be represented by a single {@link NotificationInfo
 * &#64;NotificationInfo} annotation.</p>
 *
 * <p>For example, this annotation specifies that an MBean can emit
 * {@link AttributeChangeNotification} and {@link
 * JMXConnectionNotification}:</p>
 *
 * <pre>
 * {@code @NotificationInfos}(
 *     {@code @NotificationInfo}(
 *         types = {{@link AttributeChangeNotification#ATTRIBUTE_CHANGE}},
 *         notificationClass = AttributeChangeNotification.class),
 *     {@code @NotificationInfo}(
 *         types = {{@link JMXConnectionNotification#OPENED},
 *                  {@link JMXConnectionNotification#CLOSED}},
 *         notificationClass = JMXConnectionNotification.class)
 * )
 * </pre>
 *
 * <p>If an MBean has both {@code NotificationInfo} and {@code
 * NotificationInfos} on the same class or interface, the effect is
 * the same as if the {@code NotificationInfo} were moved inside the
 * {@code NotificationInfos}.</p>
 */
@Documented
@Inherited
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface NotificationInfos {
    /**
     * <p>The {@link NotificationInfo} annotations.</p>
     */
    NotificationInfo[] value();
}
