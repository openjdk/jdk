/*
 * Copyright 2005-2006 Sun Microsystems, Inc.  All Rights Reserved.
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

package com.sun.xml.internal.bind;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import com.sun.xml.internal.bind.v2.runtime.reflect.Accessor;
import javax.xml.bind.JAXBException;

/**
 * A means to allow the user to provide customized Accessor
 * to be used by JAXB.
 */
public interface AccessorFactory {
    /**
     * Access a field of the class.
     *
     * @param bean the class to be processed.
     * @param f the field within the class to be accessed.
     * @param readOnly  the isStatic value of the field's modifier.
     * @return Accessor the accessor for this field
     *
     * @throws JAXBException reports failures of the method.
     */
    Accessor createFieldAccessor(Class bean, Field f, boolean readOnly) throws JAXBException;

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
    Accessor createPropertyAccessor(Class bean, Method getter, Method setter) throws JAXBException;
}
