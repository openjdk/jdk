/*
 * Copyright 2008 Sun Microsystems, Inc.  All Rights Reserved.
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
import javax.management.ObjectName;
import javax.management.OperationsException;
import javax.management.ReflectionException;
import javax.management.loading.ClassLoaderRepository;

/**
 * An abstract class for MBeanServerInterceptorSupport.
 * Some methods in MBeanServerInterceptor should never be called.
 * This base class provides an implementation of these methods that simply
 * throw an {@link UnsupportedOperationException}.
 * <p><b>
 * This API is a Sun internal API and is subject to changes without notice.
 * </b></p>
 * @since 1.7
 */
public abstract class MBeanServerInterceptorSupport
        implements MBeanServerInterceptor {
    /**
     * This method should never be called.
     * Throws UnsupportedOperationException.
     */
    public Object instantiate(String className)
            throws ReflectionException, MBeanException {
        throw new UnsupportedOperationException("Not applicable.");
    }

    /**
     * This method should never be called.
     * Throws UnsupportedOperationException.
     */
    public Object instantiate(String className, ObjectName loaderName)
            throws ReflectionException, MBeanException,
            InstanceNotFoundException {
        throw new UnsupportedOperationException("Not applicable.");
    }

    /**
     * This method should never be called.
     * Throws UnsupportedOperationException.
     */
    public Object instantiate(String className, Object[] params,
            String[] signature) throws ReflectionException, MBeanException {
        throw new UnsupportedOperationException("Not applicable.");
    }

    /**
     * This method should never be called.
     * Throws UnsupportedOperationException.
     */
    public Object instantiate(String className, ObjectName loaderName,
            Object[] params, String[] signature)
            throws ReflectionException, MBeanException,
            InstanceNotFoundException {
        throw new UnsupportedOperationException("Not applicable.");
    }

    /**
     * This method should never be called.
     * Throws UnsupportedOperationException.
     */
    @Deprecated
    public ObjectInputStream deserialize(ObjectName name, byte[] data)
            throws InstanceNotFoundException, OperationsException {
        throw new UnsupportedOperationException("Not applicable.");
    }

    /**
     * This method should never be called.
     * Throws UnsupportedOperationException.
     */
    @Deprecated
    public ObjectInputStream deserialize(String className, byte[] data)
            throws OperationsException, ReflectionException {
        throw new UnsupportedOperationException("Not applicable.");
    }

    /**
     * This method should never be called.
     * Throws UnsupportedOperationException.
     */
    @Deprecated
    public ObjectInputStream deserialize(String className,
            ObjectName loaderName, byte[] data)
            throws InstanceNotFoundException, OperationsException,
            ReflectionException {
        throw new UnsupportedOperationException("Not applicable.");
    }

    /**
     * This method should never be called.
     * Throws UnsupportedOperationException.
     */
    public ClassLoaderRepository getClassLoaderRepository() {
        throw new UnsupportedOperationException("Not applicable.");
    }

}
