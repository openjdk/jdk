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
import java.util.Collection;

// jmx import
//


/**
 * The <tt>CompositeData</tt> interface specifies the behavior of a specific type of complex <i>open data</i> objects
 * which represent <i>composite data</i> structures.
 *
 *
 * @since 1.5
 */
public interface CompositeData {


    /**
     * Returns the <i>composite type </i> of this <i>composite data</i> instance.
     *
     * @return the type of this CompositeData.
     */
    public CompositeType getCompositeType();

    /**
     * Returns the value of the item whose name is <tt>key</tt>.
     *
     * @param key the name of the item.
     *
     * @return the value associated with this key.
     *
     * @throws IllegalArgumentException  if <tt>key</tt> is a null or empty String.
     *
     * @throws InvalidKeyException  if <tt>key</tt> is not an existing item name for this <tt>CompositeData</tt> instance.
     */
    public Object get(String key) ;

    /**
     * Returns an array of the values of the items whose names are specified by <tt>keys</tt>, in the same order as <tt>keys</tt>.
     *
     * @param keys the names of the items.
     *
     * @return the values corresponding to the keys.
     *
     * @throws IllegalArgumentException  if an element in <tt>keys</tt> is a null or empty String.
     *
     * @throws InvalidKeyException  if an element in <tt>keys</tt> is not an existing item name for this <tt>CompositeData</tt> instance.
     */
    public Object[] getAll(String[] keys) ;

    /**
     * Returns <tt>true</tt> if and only if this <tt>CompositeData</tt> instance contains
     * an item whose name is <tt>key</tt>.
     * If <tt>key</tt> is a null or empty String, this method simply returns false.
     *
     * @param key the key to be tested.
     *
     * @return true if this <tt>CompositeData</tt> contains the key.
     */
    public boolean containsKey(String key) ;

    /**
     * Returns <tt>true</tt> if and only if this <tt>CompositeData</tt> instance contains an item
     * whose value is <tt>value</tt>.
     *
     * @param value the value to be tested.
     *
     * @return true if this <tt>CompositeData</tt> contains the value.
     */
    public boolean containsValue(Object value) ;

    /**
     * Returns an unmodifiable Collection view of the item values contained in this <tt>CompositeData</tt> instance.
     * The returned collection's iterator will return the values in the ascending lexicographic order of the corresponding
     * item names.
     *
     * @return the values.
     */
    public Collection<?> values() ;

    /**
     * Compares the specified <var>obj</var> parameter with this
     * <code>CompositeData</code> instance for equality.
     * <p>
     * Returns <tt>true</tt> if and only if all of the following statements are true:
     * <ul>
     * <li><var>obj</var> is non null,</li>
     * <li><var>obj</var> also implements the <code>CompositeData</code> interface,</li>
     * <li>their composite types are equal</li>
     * <li>their contents, i.e. (name, value) pairs are equal. If a value contained in
     * the content is an array, the value comparison is done as if by calling
     * the {@link java.util.Arrays#deepEquals(Object[], Object[]) deepEquals} method
     * for arrays of object reference types or the appropriate overloading of
     * {@code Arrays.equals(e1,e2)} for arrays of primitive types</li>
     * </ul>
     * <p>
     * This ensures that this <tt>equals</tt> method works properly for
     * <var>obj</var> parameters which are different implementations of the
     * <code>CompositeData</code> interface, with the restrictions mentioned in the
     * {@link java.util.Collection#equals(Object) equals}
     * method of the <tt>java.util.Collection</tt> interface.
     *
     * @param  obj  the object to be compared for equality with this
     * <code>CompositeData</code> instance.
     * @return  <code>true</code> if the specified object is equal to this
     * <code>CompositeData</code> instance.
     */
    public boolean equals(Object obj) ;

    /**
     * Returns the hash code value for this <code>CompositeData</code> instance.
     * <p>
     * The hash code of a <code>CompositeData</code> instance is the sum of the hash codes
     * of all elements of information used in <code>equals</code> comparisons
     * (ie: its <i>composite type</i> and all the item values).
     * <p>
     * This ensures that <code> t1.equals(t2) </code> implies that <code> t1.hashCode()==t2.hashCode() </code>
     * for any two <code>CompositeData</code> instances <code>t1</code> and <code>t2</code>,
     * as required by the general contract of the method
     * {@link Object#hashCode() Object.hashCode()}.
     * <p>
     * Each item value's hash code is added to the returned hash code.
     * If an item value is an array,
     * its hash code is obtained as if by calling the
     * {@link java.util.Arrays#deepHashCode(Object[]) deepHashCode} method
     * for arrays of object reference types or the appropriate overloading
     * of {@code Arrays.hashCode(e)} for arrays of primitive types.
     *
     * @return the hash code value for this <code>CompositeData</code> instance
     */
    public int hashCode() ;

    /**
     * Returns a string representation of this <code>CompositeData</code> instance.
     * <p>
     * The string representation consists of the name of the implementing class,
     * the string representation of the composite type of this instance, and the string representation of the contents
     * (ie list the itemName=itemValue mappings).
     *
     * @return  a string representation of this <code>CompositeData</code> instance
     */
    public String toString() ;

}
