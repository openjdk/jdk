/*
 * Copyright (c) 1997, 2012, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Map;

import javax.xml.namespace.QName;

/**
 * WrapperAccessor
 *
 * @author shih-chang.chen@oracle.com
 */
public abstract class WrapperAccessor {
        protected Map<Object, PropertySetter> propertySetters;
        protected Map<Object, PropertyGetter> propertyGetters;
        protected boolean elementLocalNameCollision;

        protected PropertySetter getPropertySetter(QName name) {
        Object key = (elementLocalNameCollision) ? name : name.getLocalPart();
        return propertySetters.get(key);
    }
        protected PropertyGetter getPropertyGetter(QName name) {
        Object key = (elementLocalNameCollision) ? name : name.getLocalPart();
        return propertyGetters.get(key);
    }

        public PropertyAccessor getPropertyAccessor(String ns, String name) {
                QName n = new QName(ns, name);
                final PropertySetter setter = getPropertySetter(n);
                final PropertyGetter getter = getPropertyGetter(n);
                return new PropertyAccessor() {
                        public Object get(Object bean) throws DatabindingException {
                                return getter.get(bean);
                        }

                        public void set(Object bean, Object value) throws DatabindingException {
                                setter.set(bean, value);
                        }
                };
        }
}
