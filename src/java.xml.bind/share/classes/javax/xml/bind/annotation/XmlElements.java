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

import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * <p>
 * A container for multiple @{@link XmlElement} annotations.
 *
 * Multiple annotations of the same type are not allowed on a program
 * element. This annotation therefore serves as a container annotation
 * for multiple &#64;XmlElements as follows:
 *
 * <pre>
 * &#64;XmlElements({ @XmlElement(...),@XmlElement(...) })
 * </pre>
 *
 * <p>The {@code @XmlElements} annotation can be used with the
 * following program elements: </p>
 * <ul>
 *   <li> a JavaBean property </li>
 *   <li> non static, non transient field </li>
 * </ul>
 *
 * This annotation is intended for annotation a JavaBean collection
 * property (e.g. List).
 *
 * <p><b>Usage</b></p>
 *
 * <p>The usage is subject to the following constraints:
 * <ul>
 *   <li> This annotation can be used with the following
 *        annotations: @{@link XmlIDREF}, @{@link XmlElementWrapper}. </li>
 *   <li> If @XmlIDREF is also specified on the JavaBean property,
 *        then each &#64;XmlElement.type() must contain a JavaBean
 *        property annotated with {@code @XmlID}.</li>
 * </ul>
 *
 * <p>See "Package Specification" in javax.xml.bind.package javadoc for
 * additional common information.</p>
 *
 * <hr>
 *
 * <p><b>Example 1:</b> Map to a list of elements</p>
 * <pre>
 *
 *    // Mapped code fragment
 *    public class Foo {
 *        &#64;XmlElements(
 *            &#64;XmlElement(name="A", type=Integer.class),
 *            &#64;XmlElement(name="B", type=Float.class)
 *         )
 *         public List items;
 *    }
 * {@code
 *
 *    <!-- XML Representation for a List of {1,2.5}
 *            XML output is not wrapped using another element -->
 *    ...
 *    <A> 1 </A>
 *    <B> 2.5 </B>
 *    ...
 *
 *    <!-- XML Schema fragment -->
 *    <xs:complexType name="Foo">
 *      <xs:sequence>
 *        <xs:choice minOccurs="0" maxOccurs="unbounded">
 *          <xs:element name="A" type="xs:int"/>
 *          <xs:element name="B" type="xs:float"/>
 *        <xs:choice>
 *      </xs:sequence>
 *    </xs:complexType>
 *
 * }</pre>
 *
 * <p><b>Example 2:</b> Map to a list of elements wrapped with another element
 * </p>
 * <pre>
 *
 *    // Mapped code fragment
 *    public class Foo {
 *        &#64;XmlElementWrapper(name="bar")
 *        &#64;XmlElements(
 *            &#64;XmlElement(name="A", type=Integer.class),
 *            &#64;XmlElement(name="B", type=Float.class)
 *        }
 *        public List items;
 *    }
 * {@code
 *
 *    <!-- XML Schema fragment -->
 *    <xs:complexType name="Foo">
 *      <xs:sequence>
 *        <xs:element name="bar">
 *          <xs:complexType>
 *            <xs:choice minOccurs="0" maxOccurs="unbounded">
 *              <xs:element name="A" type="xs:int"/>
 *              <xs:element name="B" type="xs:float"/>
 *            </xs:choice>
 *          </xs:complexType>
 *        </xs:element>
 *      </xs:sequence>
 *    </xs:complexType>
 * }</pre>
 *
 * <p><b>Example 3:</b> Change element name based on type using an adapter.
 * </p>
 * <pre>
 *    class Foo {
 *       &#64;XmlJavaTypeAdapter(QtoPAdapter.class)
 *       &#64;XmlElements({
 *           &#64;XmlElement(name="A",type=PX.class),
 *           &#64;XmlElement(name="B",type=PY.class)
 *       })
 *       Q bar;
 *    }
 *
 *    &#64;XmlType abstract class P {...}
 *    &#64;XmlType(name="PX") class PX extends P {...}
 *    &#64;XmlType(name="PY") class PY extends P {...}
 * {@code
 *
 *    <!-- XML Schema fragment -->
 *    <xs:complexType name="Foo">
 *      <xs:sequence>
 *        <xs:element name="bar">
 *          <xs:complexType>
 *            <xs:choice minOccurs="0" maxOccurs="unbounded">
 *              <xs:element name="A" type="PX"/>
 *              <xs:element name="B" type="PY"/>
 *            </xs:choice>
 *          </xs:complexType>
 *        </xs:element>
 *      </xs:sequence>
 *    </xs:complexType>
 * }</pre>
 *
 * @author <ul><li>Kohsuke Kawaguchi, Sun Microsystems, Inc.</li><li>Sekhar Vajjhala, Sun Microsystems, Inc.</li></ul>
 * @see XmlElement
 * @see XmlElementRef
 * @see XmlElementRefs
 * @see XmlJavaTypeAdapter
 * @since 1.6, JAXB 2.0
 */
@Retention(RUNTIME) @Target({FIELD,METHOD})
public @interface XmlElements {
    /**
     * Collection of @{@link XmlElement} annotations
     */
    XmlElement[] value();
}
