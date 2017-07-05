/*
 * Portions Copyright 2000-2008 Sun Microsystems, Inc.  All Rights Reserved.
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
/*
 * @author    IBM Corp.
 *
 * Copyright IBM Corp. 1999-2000.  All rights reserved.
 */

package javax.management;

import java.io.Serializable;

// Javadoc imports:
import java.lang.management.MemoryUsage;
import java.util.Arrays;
import java.util.ResourceBundle;

import javax.management.openmbean.CompositeData;
import javax.management.openmbean.MXBeanMappingFactory;
import javax.management.openmbean.OpenMBeanAttributeInfoSupport;
import javax.management.openmbean.OpenMBeanOperationInfoSupport;
import javax.management.openmbean.OpenMBeanParameterInfoSupport;
import javax.management.openmbean.OpenType;

/**
 * <p>Additional metadata for a JMX element.  A {@code Descriptor}
 * is associated with a {@link MBeanInfo}, {@link MBeanAttributeInfo}, etc.
 * It consists of a collection of fields.  A field is a name and an
 * associated value.</p>
 *
 * <p>Field names are not case-sensitive.  The names {@code descriptorType},
 * {@code descriptortype}, and {@code DESCRIPTORTYPE} are all equivalent.
 * However, the case that was used when the field was first set is preserved
 * in the result of the {@link #getFields} and {@link #getFieldNames}
 * methods.</p>
 *
 * <p>Not all field names and values are predefined.
 * New fields can be defined and added by any program.</p>
 *
 * <p>A descriptor can be mutable or immutable.
 * An immutable descriptor, once created, never changes.
 * The <code>Descriptor</code> methods that could modify the contents
 * of the descriptor will throw an exception
 * for an immutable descriptor.  Immutable descriptors are usually
 * instances of {@link ImmutableDescriptor} or a subclass.  Mutable
 * descriptors are usually instances of
 * {@link javax.management.modelmbean.DescriptorSupport} or a subclass.
 *
 * <p>Certain fields are used by the JMX implementation.  This means
 * either that the presence of the field may change the behavior of
 * the JMX API or that the field may be set in descriptors returned by
 * the JMX API.  These fields appear in <i>italics</i> in the table
 * below, and each one has a corresponding constant in the {@link JMX}
 * class.  For example, the field {@code defaultValue} is represented
 * by the constant {@link JMX#DEFAULT_VALUE_FIELD}.</p>
 *
 * <p>Certain other fields have conventional meanings described in the
 * table below but they are not required to be understood or set by
 * the JMX implementation.</p>
 *
 * <p>Field names defined by the JMX specification in this and all
 * future versions will never contain a period (.).  Users can safely
 * create their own fields by including a period in the name and be
 * sure that these names will not collide with any future version of
 * the JMX API.  It is recommended to follow the Java package naming
 * convention to avoid collisions between field names from different
 * origins.  For example, a field created by {@code example.com} might
 * have the name {@code com.example.interestLevel}.</p>
 *
 * <p>Note that the values in the {@code defaultValue}, {@code
 * legalValues}, {@code maxValue}, and {@code minValue} fields should
 * be consistent with the type returned by the {@code getType()}
 * method for the associated {@code MBeanAttributeInfo} or {@code
 * MBeanParameterInfo}.  For MXBeans, this means that they should be
 * of the mapped Java type, called <em>opendata</em>(J) in the <a
 * href="MXBean.html#mapping-rules">MXBean type mapping rules</a>.</p>
 *
 * <table border="1" cellpadding="5">
 *
 * <tr><th>Name</th><th>Type</th><th>Used in</th><th>Meaning</th></tr>
 *
 * <tr><td><a name="defaultValue"><i>defaultValue</i></a><td>Object</td>
 * <td>MBeanAttributeInfo<br>MBeanParameterInfo</td>
 *
 * <td>Default value for an attribute or parameter.  See
 * {@link javax.management.openmbean}.</td>
 *
 * <tr><td>deprecated</td><td>String</td><td>Any</td>
 *
 * <td>An indication that this element of the information model is no
 * longer recommended for use.  A set of MBeans defined by an
 * application is collectively called an <em>information model</em>.
 * The convention is for the value of this field to contain a string
 * that is the version of the model in which the element was first
 * deprecated, followed by a space, followed by an explanation of the
 * deprecation, for example {@code "1.3 Replaced by the Capacity
 * attribute"}.</td>
 *
 * <tr id="descriptionResourceBundleBaseName">
 * <td>descriptionResource<br>BundleBaseName</td><td>String</td><td>Any</td>
 *
 * <td>The base name for the {@link ResourceBundle} in which the key given in
 * the {@code descriptionResourceKey} field can be found, for example
 * {@code "com.example.myapp.MBeanResources"}.</td>
 *
 * <tr id="descriptionResourceKey">
 * <td>descriptionResourceKey</td><td>String</td><td>Any</td>
 *
 * <td>A resource key for the description of this element.  In
 * conjunction with the {@code descriptionResourceBundleBaseName},
 * this can be used to find a localized version of the description.</td>
 *
 * <tr><td>enabled</td><td>String</td>
 * <td>MBeanAttributeInfo<br>MBeanNotificationInfo<br>MBeanOperationInfo</td>
 *
 * <td>The string {@code "true"} or {@code "false"} according as this
 * item is enabled.  When an attribute or operation is not enabled, it
 * exists but cannot currently be accessed.  A user interface might
 * present it as a greyed-out item.  For example, an attribute might
 * only be meaningful after the {@code start()} method of an MBean has
 * been called, and is otherwise disabled.  Likewise, a notification
 * might be disabled if it cannot currently be emitted but could be in
 * other circumstances.</td>
 *
 * <tr><td><a name="immutableInfo"><i>immutableInfo</i></a><td>String</td>
 * <td>MBeanInfo</td>
 *
 * <td>The string {@code "true"} or {@code "false"} according as this
 * MBean's MBeanInfo is <em>immutable</em>.  When this field is true,
 * the MBeanInfo for the given MBean is guaranteed not to change over
 * the lifetime of the MBean.  Hence, a client can read it once and
 * cache the read value.  When this field is false or absent, there is
 * no such guarantee, although that does not mean that the MBeanInfo
 * will necessarily change.</td>
 *
 * <tr><td>infoTimeout</td><td>String<br>Long</td><td>MBeanInfo</td>
 *
 * <td>The time in milli-seconds that the MBeanInfo can reasonably be
 * expected to be unchanged.  The value can be a {@code Long} or a
 * decimal string.  This provides a hint from a DynamicMBean or any
 * MBean that does not define {@code immutableInfo} as {@code true}
 * that the MBeanInfo is not likely to change within this period and
 * therefore can be cached.  When this field is missing or has the
 * value zero, it is not recommended to cache the MBeanInfo unless it
 * has the {@code immutableInfo} set to {@code true}.</td></tr>
 *
 * <tr><td><a name="interfaceClassName"><i>interfaceClassName</i></a></td>
 * <td>String</td><td>MBeanInfo</td>
 *
 * <td>The Java interface name for a Standard MBean or MXBean, as
 * returned by {@link Class#getName()}.  A Standard MBean or MXBean
 * registered directly in the MBean Server or created using the {@link
 * StandardMBean} class will have this field in its MBeanInfo
 * Descriptor.</td>
 *
 * <tr><td><a name="legalValues"><i>legalValues</i></a></td>
 * <td>{@literal Set<?>}</td><td>MBeanAttributeInfo<br>MBeanParameterInfo</td>
 *
 * <td>Legal values for an attribute or parameter.  See
 * {@link javax.management.openmbean}.</td>
 *
 * <tr><td><a name="maxValue"><i>maxValue</i></a><td>Object</td>
 * <td>MBeanAttributeInfo<br>MBeanParameterInfo</td>
 *
 * <td>Maximum legal value for an attribute or parameter.  See
 * {@link javax.management.openmbean}.</td>
 *
 * <tr><td><a name="metricType">metricType</a><td>String</td>
 * <td>MBeanAttributeInfo<br>MBeanOperationInfo</td>
 *
 * <td>The type of a metric, one of the strings "counter" or "gauge".
 * A metric is a measurement exported by an MBean, usually an
 * attribute but sometimes the result of an operation.  A metric that
 * is a <em>counter</em> has a value that never decreases except by
 * being reset to a starting value.  Counter metrics are almost always
 * non-negative integers.  An example might be the number of requests
 * received.  A metric that is a <em>gauge</em> has a numeric value
 * that can increase or decrease.  Examples might be the number of
 * open connections or a cache hit rate or a temperature reading.
 *
 * <tr><td><a name="minValue"><i>minValue</i></a><td>Object</td>
 * <td>MBeanAttributeInfo<br>MBeanParameterInfo</td>
 *
 * <td>Minimum legal value for an attribute or parameter.  See
 * {@link javax.management.openmbean}.</td>
 *
 * <tr><td><a name="mxbean"><i>mxbean</i></a><td>String</td>
 * <td>MBeanInfo</td>
 *
 * <td>The string {@code "true"} or {@code "false"} according as this
 * MBean is an {@link MXBean}.  A Standard MBean or MXBean registered
 * directly with the MBean Server or created using the {@link
 * StandardMBean} class will have this field in its MBeanInfo
 * Descriptor.</td>
 *
 * <tr><td id="mxbeanMappingFactoryClass"><i>mxbeanMappingFactoryClass</i>
 * </td><td>String</td>
 * <td>MBeanInfo</td>
 *
 * <td>The name of the {@link MXBeanMappingFactory} class that was used for this
 * MXBean, if it was not the {@linkplain MXBeanMappingFactory#DEFAULT default}
 * one.</td>
 *
 * <tr><td><a name="openType"><i>openType</i></a><td>{@link OpenType}</td>
 * <td>MBeanAttributeInfo<br>MBeanOperationInfo<br>MBeanParameterInfo</td>
 *
 * <td><p>The Open Type of this element.  In the case of {@code
 * MBeanAttributeInfo} and {@code MBeanParameterInfo}, this is the
 * Open Type of the attribute or parameter.  In the case of {@code
 * MBeanOperationInfo}, it is the Open Type of the return value.  This
 * field is set in the Descriptor for all instances of {@link
 * OpenMBeanAttributeInfoSupport}, {@link
 * OpenMBeanOperationInfoSupport}, and {@link
 * OpenMBeanParameterInfoSupport}.  It is also set for attributes,
 * operations, and parameters of MXBeans.</p>
 *
 * <p>This field can be set for an {@code MBeanNotificationInfo}, in
 * which case it indicates the Open Type that the {@link
 * Notification#getUserData() user data} will have.</td>
 *
 * <tr><td><a name="originalType"><i>originalType</i></a><td>String</td>
 * <td>MBeanAttributeInfo<br>MBeanOperationInfo<br>MBeanParameterInfo</td>
 *
 * <td><p>The original Java type of this element as it appeared in the
 * {@link MXBean} interface method that produced this {@code
 * MBeanAttributeInfo} (etc).  For example, a method<br> <code>public
 * </code> {@link MemoryUsage}<code> getHeapMemoryUsage();</code><br>
 * in an MXBean interface defines an attribute called {@code
 * HeapMemoryUsage} of type {@link CompositeData}.  The {@code
 * originalType} field in the Descriptor for this attribute will have
 * the value {@code "java.lang.management.MemoryUsage"}.
 *
 * <p>The format of this string is described in the section <a
 * href="MXBean.html#type-names">Type Names</a> of the MXBean
 * specification.</p>
 *
 * <tr><td>severity</td><td>String<br>Integer</td>
 * <td>MBeanNotificationInfo</td>
 *
 * <td>The severity of this notification.  It can be 0 to mean
 * unknown severity or a value from 1 to 6 representing decreasing
 * levels of severity.  It can be represented as a decimal string or
 * an {@code Integer}.</td>
 *
 * <tr><td>since</td><td>String</td><td>Any</td>
 *
 * <td>The version of the information model in which this element
 * was introduced.  A set of MBeans defined by an application is
 * collectively called an <em>information model</em>.  The
 * application may also define versions of this model, and use the
 * {@code "since"} field to record the version in which an element
 * first appeared.</td>
 *
 * <tr><td>units</td><td>String</td>
 * <td>MBeanAttributeInfo<br>MBeanParameterInfo<br>MBeanOperationInfo</td>
 *
 * <td>The units in which an attribute, parameter, or operation return
 * value is measured, for example {@code "bytes"} or {@code
 * "seconds"}.</td>
 *
 * </table>
 *
 * <p>Some additional fields are defined by Model MBeans.  See
 * {@link javax.management.modelmbean.ModelMBeanInfo ModelMBeanInfo}
 * and related classes and the chapter "Model MBeans" of the
 * <a href="http://java.sun.com/products/JavaManagement/download.html">
 * JMX Specification</a>.</p>
 *
 * @since 1.5
 */
public interface Descriptor extends Serializable, Cloneable
{

    /**
     * Returns the value for a specific field name, or null if no value
     * is present for that name.
     *
     * @param fieldName the field name.
     *
     * @return the corresponding value, or null if the field is not present.
     *
     * @exception RuntimeOperationsException if the field name is illegal.
     */
    public Object getFieldValue(String fieldName)
            throws RuntimeOperationsException;

    /**
     * <p>Sets the value for a specific field name. This will
     * modify an existing field or add a new field.</p>
     *
     * <p>The field value will be validated before it is set.
     * If it is not valid, then an exception will be thrown.
     * The meaning of validity is dependent on the descriptor
     * implementation.</p>
     *
     * @param fieldName The field name to be set. Cannot be null or empty.
     * @param fieldValue The field value to be set for the field
     * name. Can be null if that is a valid value for the field.
     *
     * @exception RuntimeOperationsException if the field name or field value
     * is illegal (wrapped exception is {@link IllegalArgumentException}); or
     * if the descriptor is immutable (wrapped exception is
     * {@link UnsupportedOperationException}).
     */
    public void setField(String fieldName, Object fieldValue)
        throws RuntimeOperationsException;


    /**
     * Returns all of the fields contained in this descriptor as a string array.
     *
     * @return String array of fields in the format <i>fieldName=fieldValue</i>
     * <br>If the value of a field is not a String, then the toString() method
     * will be called on it and the returned value, enclosed in parentheses,
     * used as the value for the field in the returned array. If the value
     * of a field is null, then the value of the field in the returned array
     * will be empty.  If the descriptor is empty, you will get
     * an empty array.
     *
     * @see #setFields
     */
    public String[] getFields();


    /**
     * Returns all the field names in the descriptor.
     *
     * @return String array of field names. If the descriptor is empty,
     * you will get an empty array.
     */
    public String[] getFieldNames();

    /**
     * Returns all the field values in the descriptor as an array of Objects. The
     * returned values are in the same order as the {@code fieldNames} String array parameter.
     *
     * @param fieldNames String array of the names of the fields that
     * the values should be returned for.  If the array is empty then
     * an empty array will be returned.  If the array is null then all
     * values will be returned, as if the parameter were the array
     * returned by {@link #getFieldNames()}.  If a field name in the
     * array does not exist, including the case where it is null or
     * the empty string, then null is returned for the matching array
     * element being returned.
     *
     * @return Object array of field values. If the list of {@code fieldNames}
     * is empty, you will get an empty array.
     */
    public Object[] getFieldValues(String... fieldNames);

    /**
     * Removes a field from the descriptor.
     *
     * @param fieldName String name of the field to be removed.
     * If the field name is illegal or the field is not found,
     * no exception is thrown.
     *
     * @exception RuntimeOperationsException if a field of the given name
     * exists and the descriptor is immutable.  The wrapped exception will
     * be an {@link UnsupportedOperationException}.
     */
    public void removeField(String fieldName);

    /**
     * <p>Sets all fields in the field names array to the new value with
     * the same index in the field values array. Array sizes must match.</p>
     *
     * <p>The field value will be validated before it is set.
     * If it is not valid, then an exception will be thrown.
     * If the arrays are empty, then no change will take effect.</p>
     *
     * @param fieldNames String array of field names. The array and array
     * elements cannot be null.
     * @param fieldValues Object array of the corresponding field values.
     * The array cannot be null. Elements of the array can be null.
     *
     * @throws RuntimeOperationsException if the change fails for any reason.
     * Wrapped exception is {@link IllegalArgumentException} if
     * {@code fieldNames} or {@code fieldValues} is null, or if
     * the arrays are of different lengths, or if there is an
     * illegal value in one of them.
     * Wrapped exception is {@link UnsupportedOperationException}
     * if the descriptor is immutable, and the call would change
     * its contents.
     *
     * @see #getFields
     */
    public void setFields(String[] fieldNames, Object[] fieldValues)
        throws RuntimeOperationsException;


    /**
     * <p>Returns a descriptor which is equal to this descriptor.
     * Changes to the returned descriptor will have no effect on this
     * descriptor, and vice versa.  If this descriptor is immutable,
     * it may fulfill this condition by returning itself.</p>
     * @exception RuntimeOperationsException for illegal value for field names
     * or field values.
     * If the descriptor construction fails for any reason, this exception will
     * be thrown.
     * @return A descriptor which is equal to this descriptor.
     */
    public Object clone() throws RuntimeOperationsException;


    /**
     * Returns true if all of the fields have legal values given their
     * names.
     *
     * @return true if the values are legal.
     *
     * @exception RuntimeOperationsException If the validity checking fails for
     * any reason, this exception will be thrown.
     * The method returns false if the descriptor is not valid, but throws
     * this exception if the attempt to determine validity fails.
     */
    public boolean isValid() throws RuntimeOperationsException;

    /**
     * Compares this descriptor to the given object.  The objects are equal if
     * the given object is also a Descriptor, and if the two Descriptors have
     * the same field names (possibly differing in case) and the same
     * associated values.  The respective values for a field in the two
     * Descriptors are equal if the following conditions hold:</p>
     *
     * <ul>
     * <li>If one value is null then the other must be too.</li>
     * <li>If one value is a primitive array then the other must be a primitive
     * array of the same type with the same elements.</li>
     * <li>If one value is an object array then the other must be too and
     * {@link Arrays#deepEquals(Object[],Object[])} must return true.</li>
     * <li>Otherwise {@link Object#equals(Object)} must return true.</li>
     * </ul>
     *
     * @param obj the object to compare with.
     *
     * @return {@code true} if the objects are the same; {@code false}
     * otherwise.
     *
     * @since 1.6
     */
    public boolean equals(Object obj);

    /**
     * <p>Returns the hash code value for this descriptor.  The hash
     * code is computed as the sum of the hash codes for each field in
     * the descriptor.  The hash code of a field with name {@code n}
     * and value {@code v} is {@code n.toLowerCase().hashCode() ^ h}.
     * Here {@code h} is the hash code of {@code v}, computed as
     * follows:</p>
     *
     * <ul>
     * <li>If {@code v} is null then {@code h} is 0.</li>
     * <li>If {@code v} is a primitive array then {@code h} is computed using
     * the appropriate overloading of {@code java.util.Arrays.hashCode}.</li>
     * <li>If {@code v} is an object array then {@code h} is computed using
     * {@link Arrays#deepHashCode(Object[])}.</li>
     * <li>Otherwise {@code h} is {@code v.hashCode()}.</li>
     * </ul>
     *
     * @return A hash code value for this object.
     *
     * @since 1.6
     */
    public int hashCode();
}
