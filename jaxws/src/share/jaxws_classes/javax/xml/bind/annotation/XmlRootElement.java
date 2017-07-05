/*
 * Copyright (c) 2004, 2013, Oracle and/or its affiliates. All rights reserved.
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
import static java.lang.annotation.ElementType.TYPE;

/**
 * Maps a class or an enum type to an XML element.
 *
 * <p> <b>Usage</b> </p>
 * <p>
 * The &#64;XmlRootElement annotation can be used with the following program
 * elements:
 * <ul>
 *   <li> a top level class </li>
 *   <li> an enum type </li>
 * </ul>
 *
 * <p>See "Package Specification" in javax.xml.bind.package javadoc for
 * additional common information.</p>
 *
 * <p>
 * When a top level class or an enum type is annotated with the
 * &#64;XmlRootElement annotation, then its value is represented
 * as XML element in an XML document.
 *
 * <p> This annotation can be used with the following annotations:
 * {@link XmlType}, {@link XmlEnum}, {@link XmlAccessorType},
 * {@link XmlAccessorOrder}.
 * <p>

 * <p>
 * <b>Example 1: </b> Associate an element with XML Schema type
 * <pre>
 *     // Example: Code fragment
 *     &#64;XmlRootElement
 *     class Point {
 *        int x;
 *        int y;
 *        Point(int _x,int _y) {x=_x;y=_y;}
 *     }
 * </pre>
 *
 * <pre>
 *     //Example: Code fragment corresponding to XML output
 *     marshal( new Point(3,5), System.out);
 * </pre>
 *
 * <pre>
 *     &lt;!-- Example: XML output -->
 *     &lt;point>
 *       &lt;x> 3 </x>
 *       &lt;y> 5 </y>
 *     &lt;/point>
 * </pre>
 *
 * The annotation causes an global element declaration to be produced
 * in the schema. The global element declaration is associated with
 * the XML schema type to which the class is mapped.
 *
 * <pre>
 *     &lt;!-- Example: XML schema definition -->
 *     &lt;xs:element name="point" type="point"/>
 *     &lt;xs:complexType name="point">
 *       &lt;xs:sequence>
 *         &lt;xs:element name="x" type="xs:int"/>
 *         &lt;xs:element name="y" type="xs:int"/>
 *       &lt;/xs:sequence>
 *     &lt;/xs:complexType>
 * </pre>
 *
 * <p>
 *
 * <b>Example 2: Orthogonality to type inheritance </b>
 *
 * <p>
 * An element declaration annotated on a type is not inherited by its
 * derived types. The following example shows this.
 * <pre>
 *     // Example: Code fragment
 *     &#64;XmlRootElement
 *     class Point3D extends Point {
 *         int z;
 *         Point3D(int _x,int _y,int _z) {super(_x,_y);z=_z;}
 *     }
 *
 *     //Example: Code fragment corresponding to XML output *
 *     marshal( new Point3D(3,5,0), System.out );
 *
 *     &lt;!-- Example: XML output -->
 *     &lt;!-- The element name is point3D not point -->
 *     &lt;point3D>
 *       &lt;x>3&lt;/x>
 *       &lt;y>5&lt;/y>
 *       &lt;z>0&lt;/z>
 *     &lt;/point3D>
 *
 *     &lt;!-- Example: XML schema definition -->
 *     &lt;xs:element name="point3D" type="point3D"/>
 *     &lt;xs:complexType name="point3D">
 *       &lt;xs:complexContent>
 *         &lt;xs:extension base="point">
 *           &lt;xs:sequence>
 *             &lt;xs:element name="z" type="xs:int"/>
 *           &lt;/xs:sequence>
 *         &lt;/xs:extension>
 *       &lt;/xs:complexContent>
 *     &lt;/xs:complexType>
 * </pre>
 *
 * <b>Example 3: </b> Associate a global element with XML Schema type
 * to which the class is mapped.
 * <pre>
 *     //Example: Code fragment
 *     &#64;XmlRootElement(name="PriceElement")
 *     public class USPrice {
 *         &#64;XmlElement
 *         public java.math.BigDecimal price;
 *     }
 *
 *     &lt;!-- Example: XML schema definition -->
 *     &lt;xs:element name="PriceElement" type="USPrice"/>
 *     &lt;xs:complexType name="USPrice">
 *       &lt;xs:sequence>
 *         &lt;xs:element name="price" type="xs:decimal"/>
 *       &lt;/sequence>
 *     &lt;/xs:complexType>
 * </pre>
 *
 * @author Sekhar Vajjhala, Sun Microsystems, Inc.
 * @since JAXB2.0
 */
@Retention(RUNTIME)
@Target({TYPE})
public @interface XmlRootElement {
    /**
     * namespace name of the XML element.
     * <p>
     * If the value is "##default", then the XML namespace name is derived
     * from the package of the class ( {@link XmlSchema} ). If the
     * package is unnamed, then the XML namespace is the default empty
     * namespace.
     */
    String namespace() default "##default";

    /**
     * local name of the XML element.
     * <p>
     * If the value is "##default", then the name is derived from the
     * class name.
     *
     */
    String name() default "##default";

}
