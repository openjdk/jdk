/*
 * Copyright (c) 1999, 2024, Oracle and/or its affiliates. All rights reserved.
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

/**
 * <p>Provides the classes which implement advanced dynamic
 * loading.  See the chapter <em>Advanced Dynamic Loading</em> in
 * the <a href="#spec">JMX Specification</a>.</p>
 *
 * <p>An MBean that is of a subclass of {@link
 * java.lang.ClassLoader} can be used as a class loader to create
 * other MBeans via the method {@link
 * javax.management.MBeanServer#createMBean(String, ObjectName,
 * ObjectName, Object[], String[])}, and to instantiate arbitrary
 * objects via the method {@link
 * javax.management.MBeanServer#instantiate(String, ObjectName,
 * Object[], String[])}.</p>
 *
 * <p>Every MBean Server has a <em>class loader repository</em>
 * containing all MBeans registered in that MBean Server that
 * are of a subclass of {@link java.lang.ClassLoader}.  The class
 * loader repository is used by the forms of the
 * <code>createMBean</code> and <code>instantiate</code> methods
 * in the {@link javax.management.MBeanServer MBeanServer}
 * interface that do not have an explicit loader parameter.</p>
 *
 * <p>If an MBean implements the interface {@link
 * javax.management.loading.PrivateClassLoader PrivateClassLoader},
 * then it is not added to the class loader repository.</p>
 *
 * @see <a id="spec" href="https://jcp.org/aboutJava/communityprocess/mrel/jsr160/index2.html">
 * JMX Specification, version 1.4</a>
 *
 * @since 1.5
 */
package javax.management.loading;
