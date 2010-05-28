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
import javax.management.MBeanParameterInfo;


/**
 * <p>Describes a constructor of an Open MBean.</p>
 *
 * <p>This interface declares the same methods as the class {@link
 * javax.management.MBeanConstructorInfo}.  A class implementing this
 * interface (typically {@link OpenMBeanConstructorInfoSupport})
 * should extend {@link javax.management.MBeanConstructorInfo}.</p>
 *
 * <p>The {@link #getSignature()} method should return at runtime an
 * array of instances of a subclass of {@link MBeanParameterInfo}
 * which implements the {@link OpenMBeanParameterInfo} interface
 * (typically {@link OpenMBeanParameterInfoSupport}).</p>
 *
 *
 *
 * @since 1.5
 */
public interface OpenMBeanConstructorInfo {

    // Re-declares the methods that are in class MBeanConstructorInfo of JMX 1.0
    // (methods will be removed when MBeanConstructorInfo is made a parent interface of this interface)

    /**
     * Returns a human readable description of the constructor
     * described by this <tt>OpenMBeanConstructorInfo</tt> instance.
     *
     * @return the description.
     */
    public String getDescription() ;

    /**
     * Returns the name of the constructor
     * described by this <tt>OpenMBeanConstructorInfo</tt> instance.
     *
     * @return the name.
     */
    public String getName() ;

    /**
     * Returns an array of <tt>OpenMBeanParameterInfo</tt> instances
     * describing each parameter in the signature of the constructor
     * described by this <tt>OpenMBeanConstructorInfo</tt> instance.
     *
     * @return the signature.
     */
    public MBeanParameterInfo[] getSignature() ;


    // commodity methods
    //

    /**
     * Compares the specified <var>obj</var> parameter with this <code>OpenMBeanConstructorInfo</code> instance for equality.
     * <p>
     * Returns <tt>true</tt> if and only if all of the following statements are true:
     * <ul>
     * <li><var>obj</var> is non null,</li>
     * <li><var>obj</var> also implements the <code>OpenMBeanConstructorInfo</code> interface,</li>
     * <li>their names are equal</li>
     * <li>their signatures are equal.</li>
     * </ul>
     * This ensures that this <tt>equals</tt> method works properly for <var>obj</var> parameters which are
     * different implementations of the <code>OpenMBeanConstructorInfo</code> interface.
     * <br>&nbsp;
     * @param  obj  the object to be compared for equality with this <code>OpenMBeanConstructorInfo</code> instance;
     *
     * @return  <code>true</code> if the specified object is equal to this <code>OpenMBeanConstructorInfo</code> instance.
     */
    public boolean equals(Object obj);

    /**
     * Returns the hash code value for this <code>OpenMBeanConstructorInfo</code> instance.
     * <p>
     * The hash code of an <code>OpenMBeanConstructorInfo</code> instance is the sum of the hash codes
     * of all elements of information used in <code>equals</code> comparisons
     * (ie: its name and signature, where the signature hashCode is calculated by a call to
     *  <tt>java.util.Arrays.asList(this.getSignature).hashCode()</tt>).
     * <p>
     * This ensures that <code> t1.equals(t2) </code> implies that <code> t1.hashCode()==t2.hashCode() </code>
     * for any two <code>OpenMBeanConstructorInfo</code> instances <code>t1</code> and <code>t2</code>,
     * as required by the general contract of the method
     * {@link Object#hashCode() Object.hashCode()}.
     * <p>
     *
     * @return  the hash code value for this <code>OpenMBeanConstructorInfo</code> instance
     */
    public int hashCode();

    /**
     * Returns a string representation of this <code>OpenMBeanConstructorInfo</code> instance.
     * <p>
     * The string representation consists of the name of this class (ie <code>javax.management.openmbean.OpenMBeanConstructorInfo</code>),
     * and the name and signature of the described constructor.
     *
     * @return  a string representation of this <code>OpenMBeanConstructorInfo</code> instance
     */
    public String toString();

}
