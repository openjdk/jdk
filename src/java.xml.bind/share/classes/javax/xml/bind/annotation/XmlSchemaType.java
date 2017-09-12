/*
 * Copyright (c) 2005, 2015, Oracle and/or its affiliates. All rights reserved.
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

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PACKAGE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Maps a Java type to a simple schema built-in type.
 *
 * <p> <b>Usage</b> </p>
 * <p>
 * {@code @XmlSchemaType} annotation can be used with the following program
 * elements:
 * <ul>
 *   <li> a JavaBean property </li>
 *   <li> field </li>
 *   <li> package</li>
 * </ul>
 *
 * <p> {@code @XmlSchemaType} annotation defined for Java type
 * applies to all references to the Java type from a property/field.
 * A {@code @XmlSchemaType} annotation specified on the
 * property/field overrides the {@code @XmlSchemaType} annotation
 * specified at the package level.
 *
 * <p> This annotation can be used with the following annotations:
 * {@link XmlElement},  {@link XmlAttribute}.
 * <p>
 * <b>Example 1: </b> Customize mapping of XMLGregorianCalendar on the
 *  field.
 *
 * <pre>
 *     //Example: Code fragment
 *     public class USPrice {
 *         &#64;XmlElement
 *         &#64;XmlSchemaType(name="date")
 *         public XMLGregorianCalendar date;
 *     }
 * {@code
 *
 *     <!-- Example: Local XML Schema element -->
 *     <xs:complexType name="USPrice"/>
 *       <xs:sequence>
 *         <xs:element name="date" type="xs:date"/>
 *       </sequence>
 *     </xs:complexType>
 * }</pre>
 *
 * <p> <b> Example 2: </b> Customize mapping of XMLGregorianCalendar at package
 *     level </p>
 * <pre>
 *     package foo;
 *     &#64;javax.xml.bind.annotation.XmlSchemaType(
 *          name="date", type=javax.xml.datatype.XMLGregorianCalendar.class)
 *     }
 * </pre>
 *
 * @since 1.6, JAXB 2.0
 */

@Retention(RUNTIME) @Target({FIELD,METHOD,PACKAGE})
public @interface XmlSchemaType {
    String name();
    String namespace() default "http://www.w3.org/2001/XMLSchema";
    /**
     * If this annotation is used at the package level, then value of
     * the type() must be specified.
     */

    Class type() default DEFAULT.class;

    /**
     * Used in {@link XmlSchemaType#type()} to
     * signal that the type be inferred from the signature
     * of the property.
     */

    static final class DEFAULT {}

}
