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
 * Maps a JavaBean property to XML IDREF.
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
 * The <tt>&#64;XmlIDREF</tt> annotation can be used with the following
 * program elements:
 * <ul>
 *   <li> a JavaBean property </li>
 *   <li> non static, non transient field </li>
 * </ul>
 *
 * <p>See "Package Specification" in javax.xml.bind.package javadoc for
 * additional common information.</p>
 *
 * <p> The usage is subject to the following constraints:
 * <ul>
 *
 *   <li> If the type of the field or property is a collection type,
 *        then the collection item type must contain a property or
 *        field annotated with <tt>&#64;XmlID</tt>.  </li>
 *   <li> If the field or property is single valued, then the type of
 *        the property or field must contain a property or field
 *        annotated with <tt>&#64;XmlID</tt>.
 *        <p>Note: If the collection item type or the type of the
 *        property (for non collection type) is java.lang.Object, then
 *        the instance must contain a property/field annotated with
 *        <tt>&#64;XmlID</tt> attribute.
 *        </li>
 *   <li> This annotation can be used with the following annotations:
 *        {@link XmlElement}, {@link XmlAttribute}, {@link XmlList},
 *        and {@link XmlElements}.</li>
 *
 * </ul>
 * <p><b>Example:</b> Map a JavaBean property to <tt>xs:IDREF</tt>
 *   (i.e. by reference rather than by containment)</p>
 * <pre>
 *
 *   //EXAMPLE: Code fragment
 *   public class Shipping {
 *       &#64;XmlIDREF public Customer getCustomer();
 *       public void setCustomer(Customer customer);
 *       ....
 *    }
 *
 *   &lt;!-- Example: XML Schema fragment -->
 *   &lt;xs:complexType name="Shipping">
 *     &lt;xs:complexContent>
 *       &lt;xs:sequence>
 *         &lt;xs:element name="customer" type="xs:IDREF"/>
 *         ....
 *       &lt;/xs:sequence>
 *     &lt;/xs:complexContent>
 *   &lt;/xs:complexType>
 *
 * </pre>
 *
 *
 * <p><b>Example 2: </b> The following is a complete example of
 * containment versus reference.
 *
 * <pre>
 *    // By default, Customer maps to complex type <tt>xs:Customer</tt>
 *    public class Customer {
 *
 *        // map JavaBean property type to <tt>xs:ID</tt>
 *        &#64;XmlID public String getCustomerID();
 *        public void setCustomerID(String id);
 *
 *        // .... other properties not shown
 *    }
 *
 *
 *   // By default, Invoice maps to a complex type <tt>xs:Invoice</tt>
 *   public class Invoice {
 *
 *       // map by reference
 *       &#64;XmlIDREF public Customer getCustomer();
 *       public void setCustomer(Customer customer);
 *
 *      // .... other properties not shown here
 *   }
 *
 *   // By default, Shipping maps to complex type <tt>xs:Shipping</tt>
 *   public class Shipping {
 *
 *       // map by reference
 *       &#64;XmlIDREF public Customer getCustomer();
 *       public void setCustomer(Customer customer);
 *   }
 *
 *   // at least one class must reference Customer by containment;
 *   // Customer instances won't be marshalled.
 *   &#64;XmlElement(name="CustomerData")
 *   public class CustomerData {
 *       // map reference to Customer by containment by default.
 *       public Customer getCustomer();
 *
 *       // maps reference to Shipping by containment by default.
 *       public Shipping getShipping();
 *
 *       // maps reference to Invoice by containment by default.
 *       public Invoice getInvoice();
 *   }
 *
 *   &lt;!-- XML Schema mapping for above code frament -->
 *
 *   &lt;xs:complexType name="Invoice">
 *     &lt;xs:complexContent>
 *       &lt;xs:sequence>
 *         &lt;xs:element name="customer" type="xs:IDREF"/>
 *         ....
 *       &lt;/xs:sequence>
 *     &lt;/xs:complexContent>
 *   &lt;/xs:complexType>
 *
 *   &lt;xs:complexType name="Shipping">
 *     &lt;xs:complexContent>
 *       &lt;xs:sequence>
 *         &lt;xs:element name="customer" type="xs:IDREF"/>
 *         ....
 *       &lt;/xs:sequence>
 *     &lt;/xs:complexContent>
 *   &lt;/xs:complexType>
 *
 *   &lt;xs:complexType name="Customer">
 *     &lt;xs:complexContent>
 *       &lt;xs:sequence>
 *         ....
 *       &lt;/xs:sequence>
 *       &lt;xs:attribute name="CustomerID" type="xs:ID"/>
 *     &lt;/xs:complexContent>
 *   &lt;/xs:complexType>
 *
 *   &lt;xs:complexType name="CustomerData">
 *     &lt;xs:complexContent>
 *       &lt;xs:sequence>
 *         &lt;xs:element name="customer" type="xs:Customer"/>
 *         &lt;xs:element name="shipping" type="xs:Shipping"/>
 *         &lt;xs:element name="invoice"  type="xs:Invoice"/>
 *       &lt;/xs:sequence>
 *     &lt;/xs:complexContent>
 *   &lt;/xs:complexType>
 *
 *   &lt;xs:element name"customerData" type="xs:CustomerData"/>
 *
 *   &lt;!-- Instance document conforming to the above XML Schema -->
 *    &lt;customerData>
 *       &lt;customer customerID="Alice">
 *           ....
 *       &lt;/customer>
 *
 *       &lt;shipping customer="Alice">
 *           ....
 *       &lt;/shipping>
 *
 *       &lt;invoice customer="Alice">
 *           ....
 *       &lt;/invoice>
 *   &lt;/customerData>
 *
 * </pre>
 *
 * <p><b>Example 3: </b> Mapping List to repeating element of type IDREF
 * <pre>
 *     // Code fragment
 *     public class Shipping {
 *         &#64;XmlIDREF
 *         &#64;XmlElement(name="Alice")
 *             public List customers;
 *     }
 *
 *     &lt;!-- XML schema fragment -->
 *     &lt;xs:complexType name="Shipping">
 *       &lt;xs:sequence>
 *         &lt;xs:choice minOccurs="0" maxOccurs="unbounded">
 *           &lt;xs:element name="Alice" type="xs:IDREF"/>
 *         &lt;/xs:choice>
 *       &lt;/xs:sequence>
 *     &lt;/xs:complexType>
 * </pre>
 *
 * <p><b>Example 4: </b> Mapping a List to a list of elements of type IDREF.
 * <pre>
 *     //Code fragment
 *     public class Shipping {
 *         &#64;XmlIDREF
 *         &#64;XmlElements(
 *             &#64;XmlElement(name="Alice", type="Customer.class")
 *              &#64;XmlElement(name="John", type="InternationalCustomer.class")
 *         public List customers;
 *     }
 *
 *     &lt;!-- XML Schema fragment -->
 *     &lt;xs:complexType name="Shipping">
 *       &lt;xs:sequence>
 *         &lt;xs:choice minOccurs="0" maxOccurs="unbounded">
 *           &lt;xs:element name="Alice" type="xs:IDREF"/>
 *           &lt;xs:element name="John" type="xs:IDREF"/>
 *         &lt;/xs:choice>
 *       &lt;/xs:sequence>
 *     &lt;/xs:complexType>
 * </pre>
 * @author Sekhar Vajjhala, Sun Microsystems, Inc.
 * @see XmlID
 * @since JAXB2.0
 * @version $Revision: 1.11 $
 */

@Retention(RUNTIME) @Target({FIELD, METHOD})
public @interface XmlIDREF {}
