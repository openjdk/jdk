/*
 * Copyright (c) 1997, 2014, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.ws.spi.db;

import javax.xml.ws.WebServiceException;

/**
 * This is the Gtter of a bean property.
 *
 * @author shih-chang.chen@oracle.com
 * @exclude
 */
public abstract class PropertyGetterBase implements PropertyGetter {
    protected Class type;

    public Class getType() {
        return type;
    }

    static public boolean getterPattern(java.lang.reflect.Method method) {
        if (!method.getReturnType().equals(void.class) &&
            (method.getParameterTypes() == null ||
             method.getParameterTypes().length == 0)) {
            if (method.getName().startsWith("get") &&
                method.getName().length() > 3) {
                return true;
            } else {
                if ((method.getReturnType().equals(boolean.class) || method.getReturnType().equals(Boolean.class)) &&
                    method.getName().startsWith("is") &&
                    method.getName().length() > 2) {
                    return true;
                }
            }
        }
        return false;
    }

    static void verifyWrapperType(Class wrapperType) {
        String className = wrapperType.getName();
        if (className.startsWith("java.") || className.startsWith("javax.")) {
            throw new WebServiceException("Invalid wrapper type " + className);
        }
    }
}
