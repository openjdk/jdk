/*
 * Copyright (c) 2004, 2015, Oracle and/or its affiliates. All rights reserved.
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

package javax.xml.bind.annotation;

import java.lang.annotation.Target;
import java.lang.annotation.Retention;
import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.*;

/**
 * <p>
 * Prevents the mapping of a JavaBean property/type to XML representation.
 * <p>
 * The {@code @XmlTransient} annotation is useful for resolving name
 * collisions between a JavaBean property name and a field name or
 * preventing the mapping of a field/property. A name collision can
 * occur when the decapitalized JavaBean property name and a field
 * name are the same. If the JavaBean property refers to the field,
 * then the name collision can be resolved by preventing the
 * mapping of either the field or the JavaBean property using the
 * {@code @XmlTransient} annotation.
 *
 * <p>
 * When placed on a class, it indicates that the class shouldn't be mapped
 * to XML by itself. Properties on such class will be mapped to XML along
 * with its derived classes, as if the class is inlined.
 *
 * <p><b>Usage</b></p>
 * <p> The {@code @XmlTransient} annotation can be used with the following
 *     program elements:
 * <ul>
 *   <li> a JavaBean property </li>
 *   <li> field </li>
 *   <li> class </li>
 * </ul>
 *
 * <p>{@code @XmlTransient} is mutually exclusive with all other
 * JAXB defined annotations. </p>
 *
 * <p>See "Package Specification" in javax.xml.bind.package javadoc for
 * additional common information.</p>
 *
 * <p><b>Example:</b> Resolve name collision between JavaBean property and
 *     field name </p>
 *
 * <pre>
 *   // Example: Code fragment
 *   public class USAddress {
 *
 *       // The field name "name" collides with the property name
 *       // obtained by bean decapitalization of getName() below
 *       &#64;XmlTransient public String name;
 *
 *       String getName() {..};
 *       String setName() {..};
 *   }
 *
 * {@code
 *
 *   <!-- Example: XML Schema fragment -->
 *   <xs:complexType name="USAddress">
 *     <xs:sequence>
 *       <xs:element name="name" type="xs:string"/>
 *     </xs:sequence>
 *   </xs:complexType>
 * }</pre>
 *
 * @author Sekhar Vajjhala, Sun Microsystems, Inc.
 * @since 1.6, JAXB 2.0
 */

@Retention(RUNTIME) @Target({FIELD, METHOD, TYPE})
public @interface XmlTransient {}
