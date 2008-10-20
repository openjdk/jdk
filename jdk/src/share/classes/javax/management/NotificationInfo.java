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

/**
 * <p>Specifies the kinds of notification an MBean can emit.  In both the
 * following examples, the MBean emits notifications of type
 * {@code "com.example.notifs.create"} and of type
 * {@code "com.example.notifs.destroy"}:</p>
 *
 * <pre>
 * // Example one: a Standard MBean
 * {@code @NotificationInfo}(types={"com.example.notifs.create",
 *                          "com.example.notifs.destroy"})
 * public interface CacheMBean {...}
 *
 * public class Cache implements CacheMBean {...}
 * </pre>
 *
 * <pre>
 * // Example two: an annotated MBean
 * {@link MBean @MBean}
 * {@code @NotificationInfo}(types={"com.example.notifs.create",
 *                          "com.example.notifs.destroy"})
 * public class Cache {...}
 * </pre>
 *
 * <p>Each {@code @NotificationInfo} produces an {@link
 * MBeanNotificationInfo} inside the {@link MBeanInfo} of each MBean
 * to which the annotation applies.</p>
 *
 * <p>If you need to specify different notification classes, or different
 * descriptions for different notification types, then you can group
 * several {@code @NotificationInfo} annotations into a containing
 * {@link NotificationInfos @NotificationInfos} annotation.
 *
 * <p>The {@code NotificationInfo} and {@code NotificationInfos}
 * annotations can be applied to the MBean implementation class, or to
 * any parent class or interface.  These annotations on a class take
 * precedence over annotations on any superclass or superinterface.
 * If an MBean does not have these annotations on its class or any
 * superclass, then superinterfaces are examined.  It is an error for
 * more than one superinterface to have these annotations, unless one
 * of them is a child of all the others.</p>
 */
@Documented
@Inherited
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface NotificationInfo {
    /**
     * <p>The {@linkplain Notification#getType() notification types}
     * that this MBean can emit.</p>
     */
    String[] types();

    /**
     * <p>The class that emitted notifications will have.  It is recommended
     * that this be {@link Notification}, or one of its standard subclasses
     * in the JMX API.</p>
     */
    Class<? extends Notification> notificationClass() default Notification.class;

    /**
     * <p>The description of this notification.  For example:
     *
     * <pre>
     * {@code @NotificationInfo}(
     *         types={"com.example.notifs.create"},
     *         description={@code @Description}("object created"))
     * </pre>
     */
    Description description() default @Description("");

    /**
     * <p>Additional descriptor fields for the derived {@code
     * MBeanNotificationInfo}.  They are specified in the same way as
     * for the {@link DescriptorFields @DescriptorFields} annotation,
     * for example:</p>
     * <pre>
     * {@code @NotificationInfo}(
     *         types={"com.example.notifs.create"},
     *         descriptorFields={"severity=6"})
     * </pre>
     */
    String[] descriptorFields() default {};
}
