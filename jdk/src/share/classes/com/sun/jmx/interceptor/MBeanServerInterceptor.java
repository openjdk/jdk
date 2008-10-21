/*
 * Copyright 2002-2008 Sun Microsystems, Inc.  All Rights Reserved.
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

package com.sun.jmx.interceptor;


import java.io.ObjectInputStream;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanException;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.management.OperationsException;
import javax.management.ReflectionException;
import javax.management.loading.ClassLoaderRepository;

/**
 * <p>This interface specifies the behavior to be implemented by an
 * MBean Server Interceptor.  An MBean Server Interceptor has
 * essentially the same interface as an MBean Server.  An MBean Server
 * forwards received requests to its default interceptor, which may
 * handle them itself or forward them to other interceptors.  The
 * default interceptor may be changed via the {@link
 * com.sun.jmx.mbeanserver.SunJmxMBeanServer#setMBeanServerInterceptor}
 * method.</p>
 *
 * <p>The initial default interceptor provides the standard MBean
 * Server behavior.  It handles a collection of named MBeans, each
 * represented by a Java object.  A replacement default interceptor
 * may build on this behavior, for instance by adding logging or
 * security checks, before forwarding requests to the initial default
 * interceptor.  Or, it may route each request to one of a number of
 * sub-interceptors, for instance based on the {@link ObjectName} in
 * the request.</p>
 *
 * <p>An interceptor, default or not, need not implement MBeans as
 * Java objects, in the way that the initial default interceptor does.
 * It may instead implement <em>virtual MBeans</em>, which do not
 * exist as Java objects when they are not in use.  For example, these
 * MBeans could be implemented by forwarding requests to a database,
 * or to a remote MBean server, or by performing system calls to query
 * or modify system resources.</p>
 *
 * @since 1.5
 */
public interface MBeanServerInterceptor extends MBeanServer {
    /**
     * This method should never be called.
     * Usually hrows UnsupportedOperationException.
     */
    public Object instantiate(String className)
            throws ReflectionException, MBeanException;
    /**
     * This method should never be called.
     * Usually throws UnsupportedOperationException.
     */
    public Object instantiate(String className, ObjectName loaderName)
            throws ReflectionException, MBeanException,
            InstanceNotFoundException;
    /**
     * This method should never be called.
     * Usually throws UnsupportedOperationException.
     */
    public Object instantiate(String className, Object[] params,
            String[] signature) throws ReflectionException, MBeanException;

    /**
     * This method should never be called.
     * Usually throws UnsupportedOperationException.
     */
    public Object instantiate(String className, ObjectName loaderName,
            Object[] params, String[] signature)
            throws ReflectionException, MBeanException,
            InstanceNotFoundException;

    /**
     * This method should never be called.
     * Usually throws UnsupportedOperationException.
     */
    @Deprecated
    public ObjectInputStream deserialize(ObjectName name, byte[] data)
            throws InstanceNotFoundException, OperationsException;

    /**
     * This method should never be called.
     * Usually throws UnsupportedOperationException.
     */
    @Deprecated
    public ObjectInputStream deserialize(String className, byte[] data)
            throws OperationsException, ReflectionException;

    /**
     * This method should never be called.
     * Usually hrows UnsupportedOperationException.
     */
    @Deprecated
    public ObjectInputStream deserialize(String className,
            ObjectName loaderName, byte[] data)
            throws InstanceNotFoundException, OperationsException,
            ReflectionException;

    /**
     * This method should never be called.
     * Usually throws UnsupportedOperationException.
     */
    public ClassLoaderRepository getClassLoaderRepository();

}

