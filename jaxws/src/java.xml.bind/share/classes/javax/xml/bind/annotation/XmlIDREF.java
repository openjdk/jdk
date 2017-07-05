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
 * Maps a JavaBean property to XML IDREF.
 *
 * <p>
 * To preserve referential integrity of an object graph across XML
 * serialization followed by a XML deserialization, requires an object
 * reference to be marshaled by reference or containment
 * appropriately. Annotations {@code @XmlID} and {@code @XmlIDREF}
 * together allow a customized mapping of a JavaBean property's
 * type by containment or reference.
 *
 * <p><b>Usage</b> </p>
 * The {@code @XmlIDREF} annotation can be used with the following
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
 *        field annotated with {@code @XmlID}.  </li>
 *   <li> If the field or property is single valued, then the type of
 *        the property or field must contain a property or field
 *        annotated with {@code @XmlID}.
 *        <p>Note: If the collection item type or the type of the
 *        property (for non collection type) is java.lang.Object, then
 *        the instance must contain a property/field annotated with
 *        {@code @XmlID} attribute.
 *        </li>
 *   <li> This annotation can be used with the following annotations:
 *        {@link XmlElement}, {@link XmlAttribute}, {@link XmlList},
 *        and {@link XmlElements}.</li>
 *
 * </ul>
 * <p><b>Example:</b> Map a JavaBean property to {@code xs:IDREF}
 *   (i.e. by reference rather than by containment)</p>
 * <pre>
 *
 *   //EXAMPLE: Code fragment
 *   public class Shipping {
 *       &#64;XmlIDREF public Customer getCustomer();
 *       public void setCustomer(Customer customer);
 *       ....
 *    }
 * {@code
 *
 *   <!-- Example: XML Schema fragment -->
 *   <xs:complexType name="Shipping">
 *     <xs:complexContent>
 *       <xs:sequence>
 *         <xs:element name="customer" type="xs:IDREF"/>
 *         ....
 *       </xs:sequence>
 *     </xs:complexContent>
 *   </xs:complexType>
 *
 * }</pre>
 *
 *
 * <p><b>Example 2: </b> The following is a complete example of
 * containment versus reference.
 *
 * <pre>
 *    // By default, Customer maps to complex type {@code xs:Customer}
 *    public class Customer {
 *
 *        // map JavaBean property type to {@code xs:ID}
 *        &#64;XmlID public String getCustomerID();
 *        public void setCustomerID(String id);
 *
 *        // .... other properties not shown
 *    }
 *
 *
 *   // By default, Invoice maps to a complex type {@code xs:Invoice}
 *   public class Invoice {
 *
 *       // map by reference
 *       &#64;XmlIDREF public Customer getCustomer();
 *       public void setCustomer(Customer customer);
 *
 *      // .... other properties not shown here
 *   }
 *
 *   // By default, Shipping maps to complex type {@code xs:Shipping}
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
 * {@code
 *
 *   <!-- XML Schema mapping for above code frament -->
 *
 *   <xs:complexType name="Invoice">
 *     <xs:complexContent>
 *       <xs:sequence>
 *         <xs:element name="customer" type="xs:IDREF"/>
 *         ....
 *       </xs:sequence>
 *     </xs:complexContent>
 *   </xs:complexType>
 *
 *   <xs:complexType name="Shipping">
 *     <xs:complexContent>
 *       <xs:sequence>
 *         <xs:element name="customer" type="xs:IDREF"/>
 *         ....
 *       </xs:sequence>
 *     </xs:complexContent>
 *   </xs:complexType>
 *
 *   <xs:complexType name="Customer">
 *     <xs:complexContent>
 *       <xs:sequence>
 *         ....
 *       </xs:sequence>
 *       <xs:attribute name="CustomerID" type="xs:ID"/>
 *     </xs:complexContent>
 *   </xs:complexType>
 *
 *   <xs:complexType name="CustomerData">
 *     <xs:complexContent>
 *       <xs:sequence>
 *         <xs:element name="customer" type="xs:Customer"/>
 *         <xs:element name="shipping" type="xs:Shipping"/>
 *         <xs:element name="invoice"  type="xs:Invoice"/>
 *       </xs:sequence>
 *     </xs:complexContent>
 *   </xs:complexType>
 *
 *   <xs:element name"customerData" type="xs:CustomerData"/>
 *
 *   <!-- Instance document conforming to the above XML Schema -->
 *    <customerData>
 *       <customer customerID="Alice">
 *           ....
 *       </customer>
 *
 *       <shipping customer="Alice">
 *           ....
 *       </shipping>
 *
 *       <invoice customer="Alice">
 *           ....
 *       </invoice>
 *   </customerData>
 *
 * }</pre>
 *
 * <p><b>Example 3: </b> Mapping List to repeating element of type IDREF
 * <pre>
 *     // Code fragment
 *     public class Shipping {
 *         &#64;XmlIDREF
 *         &#64;XmlElement(name="Alice")
 *             public List customers;
 *     }
 * {@code
 *
 *     <!-- XML schema fragment -->
 *     <xs:complexType name="Shipping">
 *       <xs:sequence>
 *         <xs:choice minOccurs="0" maxOccurs="unbounded">
 *           <xs:element name="Alice" type="xs:IDREF"/>
 *         </xs:choice>
 *       </xs:sequence>
 *     </xs:complexType>
 * }</pre>
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
 * {@code
 *
 *     <!-- XML Schema fragment -->
 *     <xs:complexType name="Shipping">
 *       <xs:sequence>
 *         <xs:choice minOccurs="0" maxOccurs="unbounded">
 *           <xs:element name="Alice" type="xs:IDREF"/>
 *           <xs:element name="John" type="xs:IDREF"/>
 *         </xs:choice>
 *       </xs:sequence>
 *     </xs:complexType>
 * }</pre>
 * @author Sekhar Vajjhala, Sun Microsystems, Inc.
 * @see XmlID
 * @since 1.6, JAXB 2.0
 */

@Retention(RUNTIME) @Target({FIELD, METHOD})
public @interface XmlIDREF {}
