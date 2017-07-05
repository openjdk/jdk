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

package com.sun.xml.internal.ws.db.glassfish;

import com.sun.xml.internal.ws.spi.db.DatabindingException;
import com.sun.xml.internal.ws.spi.db.PropertyAccessor;
import com.sun.xml.internal.bind.api.AccessorException;
import com.sun.xml.internal.bind.api.RawAccessor;

@SuppressWarnings("unchecked")
public class RawAccessorWrapper implements PropertyAccessor {

    private RawAccessor accessor;

    public RawAccessorWrapper(RawAccessor a) {
        accessor = a;
    }

    @Override
    public boolean equals(Object obj) {
        return accessor.equals(obj);
    }

    @Override
    public Object get(Object bean) throws DatabindingException {
        try {
            return accessor.get(bean);
        } catch (AccessorException e) {
            throw new DatabindingException(e);
        }
    }

    @Override
    public int hashCode() {
        return accessor.hashCode();
    }

    @Override
    public void set(Object bean, Object value) throws DatabindingException {
        try {
            accessor.set(bean, value);
        } catch (AccessorException e) {
            throw new DatabindingException(e);
        }
    }

    @Override
    public String toString() {
        return accessor.toString();
    }
}
