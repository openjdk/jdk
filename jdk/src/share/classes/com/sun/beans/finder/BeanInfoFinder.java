/*
 * Copyright 2009 Sun Microsystems, Inc.  All Rights Reserved.
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
package com.sun.beans.finder;

import java.beans.BeanDescriptor;
import java.beans.BeanInfo;
import java.beans.MethodDescriptor;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Method;

/**
 * This is utility class that provides functionality
 * to find a {@link BeanInfo} for a JavaBean specified by its type.
 *
 * @since 1.7
 *
 * @author Sergey A. Malenkov
 */
public final class BeanInfoFinder
        extends InstanceFinder<BeanInfo> {

    private static final String DEFAULT = "sun.beans.infos";

    public BeanInfoFinder() {
        super(BeanInfo.class, true, "BeanInfo", DEFAULT);
    }

    private static boolean isValid(Class<?> type, Method method) {
        return (method != null) && method.getDeclaringClass().isAssignableFrom(type);
    }

    @Override
    protected BeanInfo instantiate(Class<?> type, String name) {
        BeanInfo info = super.instantiate(type, name);
        if (info != null) {
            // make sure that the returned BeanInfo matches the class
            BeanDescriptor bd = info.getBeanDescriptor();
            if (bd != null) {
                if (type.equals(bd.getBeanClass())) {
                    return info;
                }
            }
            else {
                PropertyDescriptor[] pds = info.getPropertyDescriptors();
                if (pds != null) {
                    for (PropertyDescriptor pd : pds) {
                        Method method = pd.getReadMethod();
                        if (method == null) {
                            method = pd.getWriteMethod();
                        }
                        if (isValid(type, method)) {
                            return info;
                        }
                    }
                }
                else {
                    MethodDescriptor[] mds = info.getMethodDescriptors();
                    if (mds != null) {
                        for (MethodDescriptor md : mds) {
                            if (isValid(type, md.getMethod())) {
                                return info;
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    @Override
    protected BeanInfo instantiate(Class<?> type, String prefix, String name) {
        // this optimization will only use the BeanInfo search path
        // if is has changed from the original
        // or trying to get the ComponentBeanInfo
        return !DEFAULT.equals(prefix) || "ComponentBeanInfo".equals(name)
                ? super.instantiate(type, prefix, name)
                : null;
    }
}
