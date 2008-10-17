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

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * <p>Indicates that the annotated class is a Standard MBean.  A Standard
 * MBean class can be defined as in this example:</p>
 *
 * <pre>
 * {@code @MBean}
 * public class Configuration {
 *     {@link ManagedAttribute @ManagedAttribute}
 *     public int getCacheSize() {...}
 *     {@code @ManagedAttribute}
 *     public void setCacheSize(int size);
 *
 *     {@code @ManagedAttribute}
 *     public long getLastChangedTime();
 *
 *     {@link ManagedOperation @ManagedOperation}
 *     public void save();
 * }
 * </pre>
 *
 * <p>The class must be public.  Public methods within the class can be
 * annotated with {@code @ManagedOperation} to indicate that they are
 * MBean operations.  Public getter and setter methods within the class
 * can be annotated with {@code @ManagedAttribute} to indicate that they define
 * MBean attributes.</p>
 *
 * <p>If the MBean is to be an MXBean rather than a Standard MBean, then
 * the {@link MXBean @MXBean} annotation must be used instead of
 * {@code @MBean}.</p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Inherited
public @interface MBean {
}
