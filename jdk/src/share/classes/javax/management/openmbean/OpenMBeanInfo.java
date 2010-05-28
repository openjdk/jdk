/*
 * Copyright (c) 2000, 2007, Oracle and/or its affiliates. All rights reserved.
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


package javax.management.openmbean;


// java import
//


// jmx import
//
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanOperationInfo;
import javax.management.MBeanConstructorInfo;
import javax.management.MBeanNotificationInfo;



/**
 * <p>Describes an Open MBean: an Open MBean is recognized as such if
 * its {@link javax.management.DynamicMBean#getMBeanInfo()
 * getMBeanInfo()} method returns an instance of a class which
 * implements the {@link OpenMBeanInfo} interface, typically {@link
 * OpenMBeanInfoSupport}.</p>
 *
 * <p>This interface declares the same methods as the class {@link
 * javax.management.MBeanInfo}.  A class implementing this interface
 * (typically {@link OpenMBeanInfoSupport}) should extend {@link
 * javax.management.MBeanInfo}.</p>
 *
 * <p>The {@link #getAttributes()}, {@link #getOperations()} and
 * {@link #getConstructors()} methods of the implementing class should
 * return at runtime an array of instances of a subclass of {@link
 * MBeanAttributeInfo}, {@link MBeanOperationInfo} or {@link
 * MBeanConstructorInfo} respectively which implement the {@link
 * OpenMBeanAttributeInfo}, {@link OpenMBeanOperationInfo} or {@link
 * OpenMBeanConstructorInfo} interface respectively.
 *
 *
 * @since 1.5
 */
public interface OpenMBeanInfo {

    // Re-declares the methods that are in class MBeanInfo of JMX 1.0
    // (methods will be removed when MBeanInfo is made a parent interface of this interface)

    /**
     * Returns the fully qualified Java class name of the open MBean
     * instances this <tt>OpenMBeanInfo</tt> describes.
     *
     * @return the class name.
     */
    public String getClassName() ;

    /**
     * Returns a human readable description of the type of open MBean
     * instances this <tt>OpenMBeanInfo</tt> describes.
     *
     * @return the description.
     */
    public String getDescription() ;

    /**
     * Returns an array of <tt>OpenMBeanAttributeInfo</tt> instances
     * describing each attribute in the open MBean described by this
     * <tt>OpenMBeanInfo</tt> instance.  Each instance in the returned
     * array should actually be a subclass of
     * <tt>MBeanAttributeInfo</tt> which implements the
     * <tt>OpenMBeanAttributeInfo</tt> interface (typically {@link
     * OpenMBeanAttributeInfoSupport}).
     *
     * @return the attribute array.
     */
    public MBeanAttributeInfo[] getAttributes() ;

    /**
     * Returns an array of <tt>OpenMBeanOperationInfo</tt> instances
     * describing each operation in the open MBean described by this
     * <tt>OpenMBeanInfo</tt> instance.  Each instance in the returned
     * array should actually be a subclass of
     * <tt>MBeanOperationInfo</tt> which implements the
     * <tt>OpenMBeanOperationInfo</tt> interface (typically {@link
     * OpenMBeanOperationInfoSupport}).
     *
     * @return the operation array.
     */
    public MBeanOperationInfo[] getOperations() ;

    /**
     * Returns an array of <tt>OpenMBeanConstructorInfo</tt> instances
     * describing each constructor in the open MBean described by this
     * <tt>OpenMBeanInfo</tt> instance.  Each instance in the returned
     * array should actually be a subclass of
     * <tt>MBeanConstructorInfo</tt> which implements the
     * <tt>OpenMBeanConstructorInfo</tt> interface (typically {@link
     * OpenMBeanConstructorInfoSupport}).
     *
     * @return the constructor array.
     */
    public MBeanConstructorInfo[] getConstructors() ;

    /**
     * Returns an array of <tt>MBeanNotificationInfo</tt> instances
     * describing each notification emitted by the open MBean
     * described by this <tt>OpenMBeanInfo</tt> instance.
     *
     * @return the notification array.
     */
    public MBeanNotificationInfo[] getNotifications() ;


    // commodity methods
    //

    /**
     * Compares the specified <var>obj</var> parameter with this <code>OpenMBeanInfo</code> instance for equality.
     * <p>
     * Returns <tt>true</tt> if and only if all of the following statements are true:
     * <ul>
     * <li><var>obj</var> is non null,</li>
     * <li><var>obj</var> also implements the <code>OpenMBeanInfo</code> interface,</li>
     * <li>their class names are equal</li>
     * <li>their infos on attributes, constructors, operations and notifications are equal</li>
     * </ul>
     * This ensures that this <tt>equals</tt> method works properly for <var>obj</var> parameters which are
     * different implementations of the <code>OpenMBeanInfo</code> interface.
     * <br>&nbsp;
     * @param  obj  the object to be compared for equality with this <code>OpenMBeanInfo</code> instance;
     *
     * @return  <code>true</code> if the specified object is equal to this <code>OpenMBeanInfo</code> instance.
     */
    public boolean equals(Object obj);

    /**
     * Returns the hash code value for this <code>OpenMBeanInfo</code> instance.
     * <p>
     * The hash code of an <code>OpenMBeanInfo</code> instance is the sum of the hash codes
     * of all elements of information used in <code>equals</code> comparisons
     * (ie: its class name, and its infos on attributes, constructors, operations and notifications,
     * where the hashCode of each of these arrays is calculated by a call to
     *  <tt>new java.util.HashSet(java.util.Arrays.asList(this.getSignature)).hashCode()</tt>).
     * <p>
     * This ensures that <code> t1.equals(t2) </code> implies that <code> t1.hashCode()==t2.hashCode() </code>
     * for any two <code>OpenMBeanInfo</code> instances <code>t1</code> and <code>t2</code>,
     * as required by the general contract of the method
     * {@link Object#hashCode() Object.hashCode()}.
     * <p>
     *
     * @return  the hash code value for this <code>OpenMBeanInfo</code> instance
     */
    public int hashCode();

    /**
     * Returns a string representation of this <code>OpenMBeanInfo</code> instance.
     * <p>
     * The string representation consists of the name of this class (ie <code>javax.management.openmbean.OpenMBeanInfo</code>),
     * the MBean class name,
     * and the string representation of infos on attributes, constructors, operations and notifications of the described MBean.
     *
     * @return  a string representation of this <code>OpenMBeanInfo</code> instance
     */
    public String toString();

}
