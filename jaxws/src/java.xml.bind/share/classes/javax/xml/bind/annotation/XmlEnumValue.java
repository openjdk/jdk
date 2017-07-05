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

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static java.lang.annotation.ElementType.FIELD;

/**
 * Maps an enum constant in {@link Enum} type to XML representation.
 *
 * <p> <b>Usage</b> </p>
 *
 * <p> The {@code @XmlEnumValue} annotation can be used with the
 *     following program elements:
 * <ul>
 *   <li>enum constant</li>
 * </ul>
 *
 * <p>See "Package Specification" in javax.xml.bind.package javadoc for
 * additional common information.</p>
 *
 * <p>This annotation, together with {@link XmlEnum} provides a
 * mapping of enum type to XML representation.
 *
 * <p>An enum type is mapped to a schema simple type with enumeration
 * facets. The schema type is derived from the Java type specified in
 * {@code @XmlEnum.value()}. Each enum constant {@code @XmlEnumValue}
 * must have a valid lexical representation for the type
 * {@code @XmlEnum.value()}
 *
 * <p> In the absence of this annotation, {@link Enum#name()} is used
 * as the XML representation.
 *
 * <p> <b>Example 1: </b>Map enum constant name {@literal ->} enumeration facet</p>
 * <pre>
 *     //Example: Code fragment
 *     &#64;XmlEnum(String.class)
 *     public enum Card { CLUBS, DIAMONDS, HEARTS, SPADES }
 * {@code
 *
 *     <!-- Example: XML Schema fragment -->
 *     <xs:simpleType name="Card">
 *       <xs:restriction base="xs:string"/>
 *         <xs:enumeration value="CLUBS"/>
 *         <xs:enumeration value="DIAMONDS"/>
 *         <xs:enumeration value="HEARTS"/>
 *         <xs:enumeration value="SPADES"/>
 *     </xs:simpleType>
 * }</pre>
 *
 * <p><b>Example 2: </b>Map enum constant name(value) {@literal ->} enumeration facet </p>
 * <pre>
 *     //Example: code fragment
 *     &#64;XmlType
 *     &#64;XmlEnum(Integer.class)
 *     public enum Coin {
 *         &#64;XmlEnumValue("1") PENNY(1),
 *         &#64;XmlEnumValue("5") NICKEL(5),
 *         &#64;XmlEnumValue("10") DIME(10),
 *         &#64;XmlEnumValue("25") QUARTER(25) }
 * {@code
 *
 *     <!-- Example: XML Schema fragment -->
 *     <xs:simpleType name="Coin">
 *       <xs:restriction base="xs:int">
 *         <xs:enumeration value="1"/>
 *         <xs:enumeration value="5"/>
 *         <xs:enumeration value="10"/>
 *         <xs:enumeration value="25"/>
 *       </xs:restriction>
 *     </xs:simpleType>
 * }</pre>
 *
 * <p><b>Example 3: </b>Map enum constant name {@literal ->} enumeration facet </p>
 *
 * <pre>
 *     //Code fragment
 *     &#64;XmlType
 *     &#64;XmlEnum(Integer.class)
 *     public enum Code {
 *         &#64;XmlEnumValue("1") ONE,
 *         &#64;XmlEnumValue("2") TWO;
 *     }
 * {@code
 *
 *     <!-- Example: XML Schema fragment -->
 *     <xs:simpleType name="Code">
 *       <xs:restriction base="xs:int">
 *         <xs:enumeration value="1"/>
 *         <xs:enumeration value="2"/>
 *       </xs:restriction>
 *     </xs:simpleType>
 * }</pre>
 *
 * @since 1.6, JAXB 2.0
 */
@Retention(RUNTIME)
@Target({FIELD})
public @interface XmlEnumValue {
    String value();
}
