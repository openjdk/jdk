/*
 * Copyright 2005-2008 Sun Microsystems, Inc.  All Rights Reserved.
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

/**
 * <p>An MBean can implement this interface to affect how the MBeanServer's
 * {@link MBeanServer#getClassLoaderFor getClassLoaderFor} and
 * {@link MBeanServer#isInstanceOf isInstanceOf} methods behave.
 * If these methods should refer to a wrapped object rather than the
 * MBean object itself, then the {@link #getWrappedObject} method should
 * return that wrapped object.</p>
 *
 * @see MBeanServer#getClassLoaderFor
 * @see MBeanServer#isInstanceOf
 */
public interface DynamicWrapperMBean extends DynamicMBean {
    /**
     * <p>The resource corresponding to this MBean.  This is the object whose
     * class name should be reflected by the MBean's
     * {@link MBeanServer#getMBeanInfo getMBeanInfo()}.<!--
     * -->{@link MBeanInfo#getClassName getClassName()} for example.  For a "plain"
     * DynamicMBean it will be "this".  For an MBean that wraps another
     * object, in the manner of {@link javax.management.StandardMBean}, it will be the
     * wrapped object.</p>
     *
     * @return The resource corresponding to this MBean.
     */
    public Object getWrappedObject();

    /**
     * <p>The {@code ClassLoader} for this MBean, which can be used to
     * retrieve resources associated with the MBean for example.  Usually,
     * it will be
     * {@link #getWrappedObject()}.{@code getClass().getClassLoader()}.
     *
     * @return The {@code ClassLoader} for this MBean.
     */
    public ClassLoader getWrappedClassLoader();
}
