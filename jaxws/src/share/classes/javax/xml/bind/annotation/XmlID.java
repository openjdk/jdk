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

import java.lang.annotation.Target;
import java.lang.annotation.Retention;
import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.*;

/**
 * <p>
 * Maps a JavaBean property to XML ID.
 *
 * <p>
 * To preserve referential integrity of an object graph across XML
 * serialization followed by a XML deserialization, requires an object
 * reference to be marshalled by reference or containment
 * appropriately. Annotations <tt>&#64;XmlID</tt> and <tt>&#64;XmlIDREF</tt>
 * together allow a customized mapping of a JavaBean property's
 * type by containment or reference.
 *
 * <p><b>Usage</b> </p>
 * The <tt>&#64;XmlID</tt> annotation can be used with the following
 * program elements:
 * <ul>
 *   <li> a JavaBean property </li>
 *   <li> non static, non transient field </li>
 * </ul>
 *
 * <p>See "Package Specification" in javax.xml.bind.package javadoc for
 * additional common information.</p>
 *
 * The usage is subject to the following constraints:
 * <ul>
 *   <li> At most one field or property in a class can be annotated
 *        with <tt>&#64;XmlID</tt>.  </li>
 *   <li> The JavaBean property's type must be <tt>java.lang.String</tt>.</li>
 *   <li> The only other mapping annotations that can be used
 *        with <tt>&#64;XmlID</tt>
 *        are:<tt>&#64;XmlElement</tt> and <tt>&#64;XmlAttribute</tt>.</li>
 * </ul>
 *
 * <p><b>Example</b>: Map a JavaBean property's type to <tt>xs:ID</tt></p>
 * <pre>
 *    // Example: code fragment
 *    public class Customer {
 *        &#64;XmlAttribute
 *        &#64;XmlID
 *        public String getCustomerID();
 *        public void setCustomerID(String id);
 *        .... other properties not shown
 *    }
 *
 *    &lt;!-- Example: XML Schema fragment -->
 *    &lt;xs:complexType name="Customer">
 *      &lt;xs:complexContent>
 *        &lt;xs:sequence>
 *          ....
 *        &lt;/xs:sequence>
 *        &lt;xs:attribute name="customerID" type="xs:ID"/>
 *      &lt;/xs:complexContent>
 *    &lt;/xs:complexType>
 * </pre>
 *
 * @author Sekhar Vajjhala, Sun Microsystems, Inc.
 * @see XmlIDREF
 * @since JAXB2.0
 * @version $Revision: 1.5 $
 */

@Retention(RUNTIME) @Target({FIELD, METHOD})
public @interface XmlID { }
