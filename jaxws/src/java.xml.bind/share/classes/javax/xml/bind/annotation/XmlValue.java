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
 * Enables mapping a class to a  XML Schema complex type with a
 * simpleContent or a XML Schema simple type.
 * </p>
 *
 * <p>
 * <b> Usage: </b>
 * <p>
 * The {@code @XmlValue} annotation can be used with the following program
 * elements:
 * <ul>
 *   <li> a JavaBean property.</li>
 *   <li> non static, non transient field.</li>
 * </ul>
 *
 * <p>See "Package Specification" in javax.xml.bind.package javadoc for
 * additional common information.</p>
 *
 * The usage is subject to the following usage constraints:
 * <ul>
 *   <li>At most one field or property can be annotated with the
 *       {@code @XmlValue} annotation. </li>
 *
 *   <li>{@code @XmlValue} can be used with the following
 *   annotations: {@link XmlList}. However this is redundant since
 *   {@link XmlList} maps a type to a simple schema type that derives by
 *   list just as {@link XmlValue} would. </li>
 *
 *   <li>If the type of the field or property is a collection type,
 *       then the collection item type must map to a simple schema
 *       type.  </li>
 *
 *   <li>If the type of the field or property is not a collection
 *       type, then the type must map to a XML Schema simple type. </li>
 *
 * </ul>
 * <p>
 * If the annotated JavaBean property is the sole class member being
 * mapped to XML Schema construct, then the class is mapped to a
 * simple type.
 *
 * If there are additional JavaBean properties (other than the
 * JavaBean property annotated with {@code @XmlValue} annotation)
 * that are mapped to XML attributes, then the class is mapped to a
 * complex type with simpleContent.
 * </p>
 *
 * <p> <b> Example 1: </b> Map a class to XML Schema simpleType</p>
 *
 *   <pre>
 *
 *     // Example 1: Code fragment
 *     public class USPrice {
 *         &#64;XmlValue
 *         public java.math.BigDecimal price;
 *     }
 * {@code
 *
 *     <!-- Example 1: XML Schema fragment -->
 *     <xs:simpleType name="USPrice">
 *       <xs:restriction base="xs:decimal"/>
 *     </xs:simpleType>
 *
 * }</pre>
 *
 * <p><b> Example 2: </b> Map a class to XML Schema complexType with
 *        with simpleContent.</p>
 *
 *   <pre>
 *
 *   // Example 2: Code fragment
 *   public class InternationalPrice {
 *       &#64;XmlValue
 *       public java.math.BigDecimal price;
 *
 *       &#64;XmlAttribute
 *       public String currency;
 *   }
 * {@code
 *
 *   <!-- Example 2: XML Schema fragment -->
 *   <xs:complexType name="InternationalPrice">
 *     <xs:simpleContent>
 *       <xs:extension base="xs:decimal">
 *         <xs:attribute name="currency" type="xs:string"/>
 *       </xs:extension>
 *     </xs:simpleContent>
 *   </xs:complexType>
 *
 * }</pre>
 *
 * @author Sekhar Vajjhala, Sun Microsystems, Inc.
 * @see XmlType
 * @since 1.6, JAXB 2.0
 */

@Retention(RUNTIME) @Target({FIELD, METHOD})
public @interface XmlValue {}
