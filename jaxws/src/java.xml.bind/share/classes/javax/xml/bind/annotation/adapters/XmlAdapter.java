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

package javax.xml.bind.annotation.adapters;

/**
 * Adapts a Java type for custom marshaling.
 *
 * <p> <b> Usage: </b> </p>
 *
 * <p>
 * Some Java types do not map naturally to an XML representation, for
 * example {@code HashMap} or other non JavaBean classes. Conversely,
 * an XML representation may map to a Java type but an application may
 * choose to access the XML representation using another Java
 * type. For example, the schema to Java binding rules bind
 * xs:DateTime by default to XmlGregorianCalendar. But an application
 * may desire to bind xs:DateTime to a custom type,
 * MyXmlGregorianCalendar, for example. In both cases, there is a
 * mismatch between <i> bound type </i>, used by an application to
 * access XML content and the <i> value type</i>, that is mapped to an
 * XML representation.
 *
 * <p>
 * This abstract class defines methods for adapting a bound type to a value
 * type or vice versa. The methods are invoked by the JAXB binding
 * framework during marshaling and unmarshalling:
 *
 * <ul>
 *   <li> <b> XmlAdapter.marshal(...): </b> During marshalling, JAXB
 *        binding framework invokes XmlAdapter.marshal(..) to adapt a
 *        bound type to value type, which is then marshaled to XML
 *        representation. </li>
 *
 *   <li> <b> XmlAdapter.unmarshal(...): </b> During unmarshalling,
 *        JAXB binding framework first unmarshals XML representation
 *        to a value type and then invokes XmlAdapter.unmarshal(..) to
 *        adapt the value type to a bound type. </li>
 * </ul>
 *
 * Writing an adapter therefore involves the following steps:
 *
 * <ul>
 *   <li> Write an adapter that implements this abstract class. </li>
 *   <li> Install the adapter using the annotation {@link
 *        XmlJavaTypeAdapter} </li>
 * </ul>
 *
 * <p><b>Example:</b> Customized mapping of {@code HashMap}</p>
 * <p> The following example illustrates the use of
 * {@code @XmlAdapter} and {@code @XmlJavaTypeAdapter} to
 * customize the mapping of a {@code HashMap}.
 *
 * <p> <b> Step 1: </b> Determine the desired XML representation for HashMap.
 *
 * <pre>{@code
 *     <hashmap>
 *         <entry key="id123">this is a value</entry>
 *         <entry key="id312">this is another value</entry>
 *         ...
 *     </hashmap>
 * }</pre>
 *
 * <p> <b> Step 2: </b> Determine the schema definition that the
 * desired XML representation shown above should follow.
 *
 * <pre>{@code
 *
 *     <xs:complexType name="myHashMapType">
 *       <xs:sequence>
 *         <xs:element name="entry" type="myHashMapEntryType"
 *                        minOccurs = "0" maxOccurs="unbounded"/>
 *       </xs:sequence>
 *     </xs:complexType>
 *
 *     <xs:complexType name="myHashMapEntryType">
 *       <xs:simpleContent>
 *         <xs:extension base="xs:string">
 *           <xs:attribute name="key" type="xs:int"/>
 *         </xs:extension>
 *       </xs:simpleContent>
 *     </xs:complexType>
 *
 * }</pre>
 *
 * <p> <b> Step 3: </b> Write value types that can generate the above
 * schema definition.
 *
 * <pre>
 *     public class MyHashMapType {
 *         List&lt;MyHashMapEntryType&gt; entry;
 *     }
 *
 *     public class MyHashMapEntryType {
 *         &#64;XmlAttribute
 *         public Integer key;
 *
 *         &#64;XmlValue
 *         public String value;
 *     }
 * </pre>
 *
 * <p> <b> Step 4: </b> Write the adapter that adapts the value type,
 * MyHashMapType to a bound type, HashMap, used by the application.
 *
 * <pre>{@code
 *     public final class MyHashMapAdapter extends
 *                        XmlAdapter<MyHashMapType,HashMap> { ... }
 *
 * }</pre>
 *
 * <p> <b> Step 5: </b> Use the adapter.
 *
 * <pre>
 *     public class Foo {
 *         &#64;XmlJavaTypeAdapter(MyHashMapAdapter.class)
 *         HashMap hashmap;
 *         ...
 *     }
 * </pre>
 *
 * The above code fragment will map to the following schema:
 *
 * <pre>{@code
 *     <xs:complexType name="Foo">
 *       <xs:sequence>
 *         <xs:element name="hashmap" type="myHashMapType">
 *       </xs:sequence>
 *     </xs:complexType>
 * }</pre>
 *
 * @param <BoundType>
 *      The type that JAXB doesn't know how to handle. An adapter is written
 *      to allow this type to be used as an in-memory representation through
 *      the {@code ValueType}.
 * @param <ValueType>
 *      The type that JAXB knows how to handle out of the box.
 *
 * @author <ul><li>Sekhar Vajjhala, Sun Microsystems Inc.</li> <li> Kohsuke Kawaguchi, Sun Microsystems Inc.</li></ul>
 * @see XmlJavaTypeAdapter
 * @since 1.6, JAXB 2.0
 */
public abstract class XmlAdapter<ValueType,BoundType> {

    /**
     * Do-nothing constructor for the derived classes.
     */
    protected XmlAdapter() {}

    /**
     * Convert a value type to a bound type.
     *
     * @param v
     *      The value to be converted. Can be null.
     * @throws Exception
     *      if there's an error during the conversion. The caller is responsible for
     *      reporting the error to the user through {@link javax.xml.bind.ValidationEventHandler}.
     */
    public abstract BoundType unmarshal(ValueType v) throws Exception;

    /**
     * Convert a bound type to a value type.
     *
     * @param v
     *      The value to be convereted. Can be null.
     * @throws Exception
     *      if there's an error during the conversion. The caller is responsible for
     *      reporting the error to the user through {@link javax.xml.bind.ValidationEventHandler}.
     */
    public abstract ValueType marshal(BoundType v) throws Exception;
}
