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

package javax.xml.bind.annotation;

import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.*;

/**
 * Maps a JavaBean property to a XML element derived from property name.
 *
 * <p> <b>Usage</b> </p>
 * <p>
 * </tt>@XmlElement</tt> annotation can be used with the following program
 * elements:
 * <ul>
 *   <li> a JavaBean property </li>
 *   <li> non static, non transient field </li>
 *   <li> within {@link XmlElements}
 * <p>
 *
 * </ul>
 *
 * The usage is subject to the following constraints:
 * <ul>
 *   <li> This annotation can be used with following annotations:
 *            {@link XmlID},
 *            {@link XmlIDREF},
 *            {@link XmlList},
 *            {@link XmlSchemaType},
 *            {@link XmlValue},
 *            {@link XmlAttachmentRef},
 *            {@link XmlMimeType},
 *            {@link XmlInlineBinaryData},
 *            {@link XmlElementWrapper},
 *            {@link XmlJavaTypeAdapter}</li>
 *   <li> if the type of JavaBean property is a collection type of
 *        array, an indexed property, or a parameterized list, and
 *        this annotation is used with {@link XmlElements} then,
 *        <tt>@XmlElement.type()</tt> must be DEFAULT.class since the
 *        collection item type is already known. </li>
 * </ul>
 *
 * <p>
 * A JavaBean property, when annotated with @XmlElement annotation
 * is mapped to a local element in the XML Schema complex type to
 * which the containing class is mapped.
 *
 * <p>
 * <b>Example 1: </b> Map a public non static non final field to local
 * element
 * <pre>
 *     //Example: Code fragment
 *     public class USPrice {
 *         &#64;XmlElement(name="itemprice")
 *         public java.math.BigDecimal price;
 *     }
 *
 *     &lt;!-- Example: Local XML Schema element -->
 *     &lt;xs:complexType name="USPrice"/>
 *       &lt;xs:sequence>
 *         &lt;xs:element name="itemprice" type="xs:decimal" minOccurs="0"/>
 *       &lt;/sequence>
 *     &lt;/xs:complexType>
 *   </pre>
 * <p>
 *
 * <b> Example 2: </b> Map a field to a nillable element.
 *   <pre>
 *
 *     //Example: Code fragment
 *     public class USPrice {
 *         &#64;XmlElement(nillable=true)
 *         public java.math.BigDecimal price;
 *     }
 *
 *     &lt;!-- Example: Local XML Schema element -->
 *     &lt;xs:complexType name="USPrice">
 *       &lt;xs:sequence>
 *         &lt;xs:element name="price" type="xs:decimal" nillable="true" minOccurs="0"/>
 *       &lt;/sequence>
 *     &lt;/xs:complexType>
 *   </pre>
 * <p>
 * <b> Example 3: </b> Map a field to a nillable, required element.
 *   <pre>
 *
 *     //Example: Code fragment
 *     public class USPrice {
 *         &#64;XmlElement(nillable=true, required=true)
 *         public java.math.BigDecimal price;
 *     }
 *
 *     &lt;!-- Example: Local XML Schema element -->
 *     &lt;xs:complexType name="USPrice">
 *       &lt;xs:sequence>
 *         &lt;xs:element name="price" type="xs:decimal" nillable="true" minOccurs="1"/>
 *       &lt;/sequence>
 *     &lt;/xs:complexType>
 *   </pre>
 * <p>
 *
 * <p> <b>Example 4: </b>Map a JavaBean property to an XML element
 * with anonymous type.</p>
 * <p>
 * See Example 6 in @{@link XmlType}.
 *
 * <p>
 * @author Sekhar Vajjhala, Sun Microsystems, Inc.
 * @since JAXB2.0
 * @version $Revision: 1.19 $
 */

@Retention(RUNTIME) @Target({FIELD, METHOD})
public @interface XmlElement {
    /**
     * Name of the XML Schema element.
     * <p> If the value is "##default", then element name is derived from the
     * JavaBean property name.
     */
    String name() default "##default";

    /**
     * Customize the element declaration to be nillable.
     * <p>If nillable() is true, then the JavaBean property is
     * mapped to a XML Schema nillable element declaration.
     */
    boolean nillable() default false;

    /**
     * Customize the element declaration to be required.
     * <p>If required() is true, then Javabean property is mapped to
     * an XML schema element declaration with minOccurs="1".
     * maxOccurs is "1" for a single valued property and "unbounded"
     * for a multivalued property.
     * <p>If required() is false, then the Javabean property is mapped
     * to XML Schema element declaration with minOccurs="0".
     * maxOccurs is "1" for a single valued property and "unbounded"
     * for a multivalued property.
     */

    boolean required() default false;

    /**
     * XML target namespace of the XML Schema element.
     * <p>
     * If the value is "##default", then the namespace is determined
     * as follows:
     * <ol>
     *  <li>
     *  If the enclosing package has {@link XmlSchema} annotation,
     *  and its {@link XmlSchema#elementFormDefault() elementFormDefault}
     *  is {@link XmlNsForm#QUALIFIED QUALIFIED}, then the namespace of
     *  the enclosing class.
     *
     *  <li>
     *  Otherwise "" (which produces unqualified element in the default
     *  namespace.
     * </ol>
     */
    String namespace() default "##default";

    /**
     * Default value of this element.
     *
     * <p>
     * The '\u0000' value specified as a default of this annotation element
     * is used as a poor-man's substitute for null to allow implementations
     * to recognize the 'no default value' state.
     */
    String defaultValue() default "\u0000";

    /**
     * The Java class being referenced.
     */
    Class type() default DEFAULT.class;

    /**
     * Used in {@link XmlElement#type()} to
     * signal that the type be inferred from the signature
     * of the property.
     */
    static final class DEFAULT {}
}
