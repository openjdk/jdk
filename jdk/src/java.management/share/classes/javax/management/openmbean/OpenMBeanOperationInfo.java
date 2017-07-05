/*
 * Copyright (c) 2000, 2014, Oracle and/or its affiliates. All rights reserved.
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
import javax.management.MBeanParameterInfo;

/**
 * <p>Describes an operation of an Open MBean.</p>
 *
 * <p>This interface declares the same methods as the class {@link
 * javax.management.MBeanOperationInfo}.  A class implementing this
 * interface (typically {@link OpenMBeanOperationInfoSupport}) should
 * extend {@link javax.management.MBeanOperationInfo}.</p>
 *
 * <p>The {@link #getSignature()} method should return at runtime an
 * array of instances of a subclass of {@link MBeanParameterInfo}
 * which implements the {@link OpenMBeanParameterInfo} interface
 * (typically {@link OpenMBeanParameterInfoSupport}).</p>
 *
 *
 * @since 1.5
 */
public interface OpenMBeanOperationInfo  {

    // Re-declares fields and methods that are in class MBeanOperationInfo of JMX 1.0
    // (fields and methods will be removed when MBeanOperationInfo is made a parent interface of this interface)

    /**
     * Returns a human readable description of the operation
     * described by this <tt>OpenMBeanOperationInfo</tt> instance.
     *
     * @return the description.
     */
    public String getDescription() ;

    /**
     * Returns the name of the operation
     * described by this <tt>OpenMBeanOperationInfo</tt> instance.
     *
     * @return the name.
     */
    public String getName() ;

    /**
     * Returns an array of <tt>OpenMBeanParameterInfo</tt> instances
     * describing each parameter in the signature of the operation
     * described by this <tt>OpenMBeanOperationInfo</tt> instance.
     * Each instance in the returned array should actually be a
     * subclass of <tt>MBeanParameterInfo</tt> which implements the
     * <tt>OpenMBeanParameterInfo</tt> interface (typically {@link
     * OpenMBeanParameterInfoSupport}).
     *
     * @return the signature.
     */
    public MBeanParameterInfo[] getSignature() ;

    /**
     * Returns an <tt>int</tt> constant qualifying the impact of the
     * operation described by this <tt>OpenMBeanOperationInfo</tt>
     * instance.
     *
     * The returned constant is one of {@link
     * javax.management.MBeanOperationInfo#INFO}, {@link
     * javax.management.MBeanOperationInfo#ACTION}, {@link
     * javax.management.MBeanOperationInfo#ACTION_INFO}, or {@link
     * javax.management.MBeanOperationInfo#UNKNOWN}.
     *
     * @return the impact code.
     */
    public int getImpact() ;

    /**
     * Returns the fully qualified Java class name of the values
     * returned by the operation described by this
     * <tt>OpenMBeanOperationInfo</tt> instance.  This method should
     * return the same value as a call to
     * <tt>getReturnOpenType().getClassName()</tt>.
     *
     * @return the return type.
     */
    public String getReturnType() ;


    // Now declares methods that are specific to open MBeans
    //

    /**
     * Returns the <i>open type</i> of the values returned by the
     * operation described by this <tt>OpenMBeanOperationInfo</tt>
     * instance.
     *
     * @return the return type.
     */
    public OpenType<?> getReturnOpenType() ; // open MBean specific method


    // commodity methods
    //

    /**
     * Compares the specified <var>obj</var> parameter with this <code>OpenMBeanOperationInfo</code> instance for equality.
     * <p>
     * Returns <tt>true</tt> if and only if all of the following statements are true:
     * <ul>
     * <li><var>obj</var> is non null,</li>
     * <li><var>obj</var> also implements the <code>OpenMBeanOperationInfo</code> interface,</li>
     * <li>their names are equal</li>
     * <li>their signatures are equal</li>
     * <li>their return open types are equal</li>
     * <li>their impacts are equal</li>
     * </ul>
     * This ensures that this <tt>equals</tt> method works properly for <var>obj</var> parameters which are
     * different implementations of the <code>OpenMBeanOperationInfo</code> interface.
     * <br>&nbsp;
     * @param  obj  the object to be compared for equality with this <code>OpenMBeanOperationInfo</code> instance;
     *
     * @return  <code>true</code> if the specified object is equal to this <code>OpenMBeanOperationInfo</code> instance.
     */
    public boolean equals(Object obj);

    /**
     * Returns the hash code value for this <code>OpenMBeanOperationInfo</code> instance.
     * <p>
     * The hash code of an <code>OpenMBeanOperationInfo</code> instance is the sum of the hash codes
     * of all elements of information used in <code>equals</code> comparisons
     * (ie: its name, return open type, impact and signature, where the signature hashCode is calculated by a call to
     *  <tt>java.util.Arrays.asList(this.getSignature).hashCode()</tt>).
     * <p>
     * This ensures that <code> t1.equals(t2) </code> implies that <code> t1.hashCode()==t2.hashCode() </code>
     * for any two <code>OpenMBeanOperationInfo</code> instances <code>t1</code> and <code>t2</code>,
     * as required by the general contract of the method
     * {@link Object#hashCode() Object.hashCode()}.
     *
     *
     * @return  the hash code value for this <code>OpenMBeanOperationInfo</code> instance
     */
    public int hashCode();

    /**
     * Returns a string representation of this <code>OpenMBeanOperationInfo</code> instance.
     * <p>
     * The string representation consists of the name of this class (ie <code>javax.management.openmbean.OpenMBeanOperationInfo</code>),
     * and the name, signature, return open type and impact of the described operation.
     *
     * @return  a string representation of this <code>OpenMBeanOperationInfo</code> instance
     */
    public String toString();

}
