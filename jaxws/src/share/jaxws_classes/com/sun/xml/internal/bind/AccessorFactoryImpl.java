/*
 * Copyright (c) 1997, 2011, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.bind;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import javax.xml.bind.JAXBException;

import com.sun.xml.internal.bind.v2.runtime.reflect.Accessor;

public class AccessorFactoryImpl implements InternalAccessorFactory {

    private static AccessorFactoryImpl instance = new AccessorFactoryImpl();
    private AccessorFactoryImpl(){}

    public static AccessorFactoryImpl getInstance(){
        return instance;
    }

    /**
     * Access a field of the class.
     *
     * @param bean the class to be processed.
     * @param field the field within the class to be accessed.
     * @param readOnly  the isStatic value of the field's modifier.
     * @return Accessor the accessor for this field
     *
     * @throws JAXBException reports failures of the method.
     */
    public Accessor createFieldAccessor(Class bean, Field field, boolean readOnly) {
        return readOnly
                ? new Accessor.ReadOnlyFieldReflection(field)
                : new Accessor.FieldReflection(field);
    }

    /**
     * Access a field of the class.
     *
     * @param bean the class to be processed.
     * @param field the field within the class to be accessed.
     * @param readOnly  the isStatic value of the field's modifier.
     * @param supressWarning supress security warning about accessing fields through reflection
     * @return Accessor the accessor for this field
     *
     * @throws JAXBException reports failures of the method.
     */
    public Accessor createFieldAccessor(Class bean, Field field, boolean readOnly, boolean supressWarning) {
        return readOnly
                ? new Accessor.ReadOnlyFieldReflection(field, supressWarning)
                : new Accessor.FieldReflection(field, supressWarning);
    }

    /**
     * Access a property of the class.
     *
     * @param bean the class to be processed
     * @param getter the getter method to be accessed. The value can be null.
     * @param setter the setter method to be accessed. The value can be null.
     * @return Accessor the accessor for these methods
     *
     * @throws JAXBException reports failures of the method.
     */
    public Accessor createPropertyAccessor(Class bean, Method getter, Method setter) {
        if (getter == null) {
            return new Accessor.SetterOnlyReflection(setter);
        }
        if (setter == null) {
            return new Accessor.GetterOnlyReflection(getter);
        }
        return new Accessor.GetterSetterReflection(getter, setter);
    }
}
