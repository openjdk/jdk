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
package com.sun.xml.internal.bind.v2.model.runtime;

import java.lang.reflect.Type;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import javax.xml.namespace.QName;

import com.sun.xml.internal.bind.v2.model.core.ClassInfo;
import com.sun.xml.internal.bind.v2.runtime.reflect.Accessor;
import com.sun.xml.internal.bind.annotation.XmlLocation;

import org.xml.sax.Locator;

/**
 * @author Kohsuke Kawaguchi (kk@kohsuke.org)
 */
public interface RuntimeClassInfo extends ClassInfo<Type,Class>, RuntimeNonElement {
    RuntimeClassInfo getBaseClass();

    // refined to return RuntimePropertyInfo
    List<? extends RuntimePropertyInfo> getProperties();
    RuntimePropertyInfo getProperty(String name);

    Method getFactoryMethod();

    /**
     * If {@link #hasAttributeWildcard()} is true,
     * returns the accessor to access the property.
     *
     * @return
     *      unoptimized accessor.
     *      non-null iff {@link #hasAttributeWildcard()}==true.
     *
     * @see Accessor#optimize()
     */
    <BeanT> Accessor<BeanT,Map<QName,String>> getAttributeWildcard();

    /**
     * If this JAXB bean has a property annotated with {@link XmlLocation},
     * this method returns it.
     *
     * @return may be null.
     */
    <BeanT> Accessor<BeanT,Locator> getLocatorField();
}
