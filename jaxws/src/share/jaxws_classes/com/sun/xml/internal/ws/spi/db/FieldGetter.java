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

import java.lang.reflect.Field;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;


/**
 * FieldGetter gets the value of a field from an instance.
 *
 * @author shih-chang.chen@oracle.com
 * @exclude
 */
public class FieldGetter extends PropertyGetterBase {

    protected Field field;

    public FieldGetter(Field f) {
        field = f;
        type = f.getType();
    }

    public Field getField() {
        return field;
    }

    static class PrivilegedGetter implements PrivilegedExceptionAction {
        private Object value;
        private Field  field;
        private Object instance;
        public PrivilegedGetter(Field field, Object instance) {
            super();
            this.field = field;
            this.instance = instance;
        }
        public Object run() throws IllegalAccessException {
            if (!field.isAccessible()) {
                field.setAccessible(true);
            }
            value = field.get(instance);
            return null;
        }
    }

    public Object get(final Object instance) {
        if (field.isAccessible()) {
            try {
                return field.get(instance);
            } catch (Exception e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        } else {
            PrivilegedGetter privilegedGetter = new PrivilegedGetter(field, instance);
            try {
                AccessController.doPrivileged(privilegedGetter);
            } catch (PrivilegedActionException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            return privilegedGetter.value;
        }
        return null;
    }

    public <A> A getAnnotation(Class<A> annotationType) {
        Class c = annotationType;
        return (A) field.getAnnotation(c);
    }
}
