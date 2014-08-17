/*
 * Copyright (c) 2007, 2012, Oracle and/or its affiliates. All rights reserved.
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


package com.sun.org.glassfish.gmbal ;

import java.lang.annotation.Documented ;
import java.lang.annotation.Target ;
import java.lang.annotation.ElementType ;
import java.lang.annotation.Retention ;
import java.lang.annotation.RetentionPolicy ;

/** This annotation defines an attribute in open MBean (ManagedObject) or
 * CompositeData (ManagedData).  It is useful in cases where the parent class
 * cannot be annotated (for example, Object.toString(), or a framework class
 * that must be extended
 * but cannot be modified).  The attribute id is defined in the annotation, and
 * it is implemented by the methods inherited by the Managed entity.
 * <p>
 * An example of a use of this is to handle @ManagedData that inherits from
 * Collection<X>, and it is desired to display a read-only attribute containing
 * the elements of the Collection.  Simple add the annotation
 * <p>
 * @InheritedAttribute( methodName="iterator" )
 * <p>
 * to handle this case.  Note that this only supports read-only attributes.
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface InheritedAttribute {
    /** The description of the attribute.  Can be a key to a resource
     * bundle for I18N support. Note that this needs a description, otherwise
     * the InheritedAttributes annotation won't work.  The Description
     * annotation is used in all other cases.  The description cannot be
     * empty.
     * @return The description.
     */
    String description() ;

    /** The name of the attribute,  This class must inherit a method whose name
     * corresponds to this id in one of the standard ways.
     * @return The ID.
     */
    String id() default "" ;

    /** The name of the method implementing this attribute.  At least one of
     * id and methodName must not be empty.  If only one is given, the other
     * is derived according to the extended attribute name rules.
     */
    String methodName() default "" ;
}
