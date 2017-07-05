/*
 * Copyright (c) 2000, 2004, Oracle and/or its affiliates. All rights reserved.
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

package javax.print.attribute;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.HashMap;

/**
 * Class HashAttributeSet provides an <code>AttributeSet</code>
 * implementation with characteristics of a hash map.
 * <P>
 *
 * @author  Alan Kaminsky
 */
public class HashAttributeSet implements AttributeSet, Serializable {

    private static final long serialVersionUID = 5311560590283707917L;

    /**
     * The interface of which all members of this attribute set must be an
     * instance. It is assumed to be interface {@link Attribute Attribute}
     * or a subinterface thereof.
     * @serial
     */
    private Class myInterface;

    /*
     * A HashMap used by the implementation.
     * The serialised form doesn't include this instance variable.
     */
    private transient HashMap attrMap = new HashMap();

    /**
     * Write the instance to a stream (ie serialize the object)
     *
     * @serialData
     * The serialized form of an attribute set explicitly writes the
     * number of attributes in the set, and each of the attributes.
     * This does not guarantee equality of serialized forms since
     * the order in which the attributes are written is not defined.
     */
    private void writeObject(ObjectOutputStream s) throws IOException {

        s.defaultWriteObject();
        Attribute [] attrs = toArray();
        s.writeInt(attrs.length);
        for (int i = 0; i < attrs.length; i++) {
            s.writeObject(attrs[i]);
        }
    }

    /**
     * Reconstitute an instance from a stream that is, deserialize it).
     */
    private void readObject(ObjectInputStream s)
        throws ClassNotFoundException, IOException {

        s.defaultReadObject();
        attrMap = new HashMap();
        int count = s.readInt();
        Attribute attr;
        for (int i = 0; i < count; i++) {
            attr = (Attribute)s.readObject();
            add(attr);
        }
    }

    /**
     * Construct a new, empty attribute set.
     */
    public HashAttributeSet() {
        this(Attribute.class);
    }

    /**
     * Construct a new attribute set,
     * initially populated with the given attribute.
     *
     * @param  attribute  Attribute value to add to the set.
     *
     * @exception  NullPointerException
     *     (unchecked exception) Thrown if <CODE>attribute</CODE> is null.
     */
    public HashAttributeSet(Attribute attribute) {
        this (attribute, Attribute.class);
    }

    /**
     * Construct a new attribute set,
     * initially populated with the values from the
     * given array. The new attribute set is populated by
     * adding the elements of <CODE>attributes</CODE> array to the set in
     * sequence, starting at index 0. Thus, later array elements may replace
     * earlier array elements if the array contains duplicate attribute
     * values or attribute categories.
     *
     * @param  attributes  Array of attribute values to add to the set.
     *                    If null, an empty attribute set is constructed.
     *
     * @exception  NullPointerException
     *     (unchecked exception) Thrown if any element of
     *     <CODE>attributes</CODE> is null.
     */
    public HashAttributeSet(Attribute[] attributes) {
        this (attributes, Attribute.class);
    }

    /**
     * Construct a new attribute set,
     * initially populated with the values from the  given set.
     *
     * @param  attributes Set of attributes from which to initialise this set.
     *                 If null, an empty attribute set is constructed.
     *
     */
    public HashAttributeSet(AttributeSet attributes) {
        this (attributes, Attribute.class);
    }

    /**
     * Construct a new, empty attribute set, where the members of
     * the attribute set are restricted to the given interface.
     *
     * @param  interfaceName  The interface of which all members of this
     *                     attribute set must be an instance. It is assumed to
     *                     be interface {@link Attribute Attribute} or a
     *                     subinterface thereof.
     * @exception NullPointerException if interfaceName is null.
     */
    protected HashAttributeSet(Class<?> interfaceName) {
        if (interfaceName == null) {
            throw new NullPointerException("null interface");
        }
        myInterface = interfaceName;
    }

    /**
     * Construct a new attribute set, initially populated with the given
     * attribute, where the members of the attribute set are restricted to the
     * given interface.
     *
     * @param  attribute      Attribute value to add to the set.
     * @param  interfaceName  The interface of which all members of this
     *                    attribute set must be an instance. It is assumed to
     *                    be interface {@link Attribute Attribute} or a
     *                    subinterface thereof.
     *
     * @exception  NullPointerException
     *     (unchecked exception) Thrown if <CODE>attribute</CODE> is null.
     * @exception NullPointerException if interfaceName is null.
     * @exception  ClassCastException
     *     (unchecked exception) Thrown if <CODE>attribute</CODE> is not an
     *     instance of <CODE>interfaceName</CODE>.
     */
    protected HashAttributeSet(Attribute attribute, Class<?> interfaceName) {
        if (interfaceName == null) {
            throw new NullPointerException("null interface");
        }
        myInterface = interfaceName;
        add (attribute);
    }

    /**
     * Construct a new attribute set, where the members of the attribute
     * set are restricted to the given interface.
     * The new attribute set is populated
     * by adding the elements of <CODE>attributes</CODE> array to the set in
     * sequence, starting at index 0. Thus, later array elements may replace
     * earlier array elements if the array contains duplicate attribute
     * values or attribute categories.
     *
     * @param  attributes Array of attribute values to add to the set. If
     *                    null, an empty attribute set is constructed.
     * @param  interfaceName  The interface of which all members of this
     *                    attribute set must be an instance. It is assumed to
     *                    be interface {@link Attribute Attribute} or a
     *                    subinterface thereof.
     *
     * @exception  NullPointerException
     *     (unchecked exception) Thrown if any element of
     * <CODE>attributes</CODE> is null.
     * @exception NullPointerException if interfaceName is null.
     * @exception  ClassCastException
     *     (unchecked exception) Thrown if any element of
     * <CODE>attributes</CODE> is not an instance of
     * <CODE>interfaceName</CODE>.
     */
    protected HashAttributeSet(Attribute[] attributes, Class<?> interfaceName) {
        if (interfaceName == null) {
            throw new NullPointerException("null interface");
        }
        myInterface = interfaceName;
        int n = attributes == null ? 0 : attributes.length;
        for (int i = 0; i < n; ++ i) {
            add (attributes[i]);
        }
    }

    /**
     * Construct a new attribute set, initially populated with the
     * values from the  given set where the members of the attribute
     * set are restricted to the given interface.
     *
     * @param  attributes set of attribute values to initialise the set. If
     *                    null, an empty attribute set is constructed.
     * @param  interfaceName  The interface of which all members of this
     *                    attribute set must be an instance. It is assumed to
     *                    be interface {@link Attribute Attribute} or a
     *                    subinterface thereof.
     *
     * @exception  ClassCastException
     *     (unchecked exception) Thrown if any element of
     * <CODE>attributes</CODE> is not an instance of
     * <CODE>interfaceName</CODE>.
     */
    protected HashAttributeSet(AttributeSet attributes, Class<?> interfaceName) {
      myInterface = interfaceName;
      if (attributes != null) {
        Attribute[] attribArray = attributes.toArray();
        int n = attribArray == null ? 0 : attribArray.length;
        for (int i = 0; i < n; ++ i) {
          add (attribArray[i]);
        }
      }
    }

    /**
     * Returns the attribute value which this attribute set contains in the
     * given attribute category. Returns <tt>null</tt> if this attribute set
     * does not contain any attribute value in the given attribute category.
     *
     * @param  category  Attribute category whose associated attribute value
     *                   is to be returned. It must be a
     *                   {@link java.lang.Class Class}
     *                   that implements interface {@link Attribute
     *                   Attribute}.
     *
     * @return  The attribute value in the given attribute category contained
     *          in this attribute set, or <tt>null</tt> if this attribute set
     *          does not contain any attribute value in the given attribute
     *          category.
     *
     * @throws  NullPointerException
     *     (unchecked exception) Thrown if the <CODE>category</CODE> is null.
     * @throws  ClassCastException
     *     (unchecked exception) Thrown if the <CODE>category</CODE> is not a
     *     {@link java.lang.Class Class} that implements interface {@link
     *     Attribute Attribute}.
     */
    public Attribute get(Class<?> category) {
        return (Attribute)
            attrMap.get(AttributeSetUtilities.
                        verifyAttributeCategory(category,
                                                Attribute.class));
    }

    /**
     * Adds the specified attribute to this attribute set if it is not
     * already present, first removing any existing in the same
     * attribute category as the specified attribute value.
     *
     * @param  attribute  Attribute value to be added to this attribute set.
     *
     * @return  <tt>true</tt> if this attribute set changed as a result of the
     *          call, i.e., the given attribute value was not already a
     *          member of this attribute set.
     *
     * @throws  NullPointerException
     *    (unchecked exception) Thrown if the <CODE>attribute</CODE> is null.
     * @throws  UnmodifiableSetException
     *    (unchecked exception) Thrown if this attribute set does not support
     *     the <CODE>add()</CODE> operation.
     */
    public boolean add(Attribute attribute) {
        Object oldAttribute =
            attrMap.put(attribute.getCategory(),
                        AttributeSetUtilities.
                        verifyAttributeValue(attribute, myInterface));
        return (!attribute.equals(oldAttribute));
    }

    /**
     * Removes any attribute for this category from this attribute set if
     * present. If <CODE>category</CODE> is null, then
     * <CODE>remove()</CODE> does nothing and returns <tt>false</tt>.
     *
     * @param  category Attribute category to be removed from this
     *                  attribute set.
     *
     * @return  <tt>true</tt> if this attribute set changed as a result of the
     *         call, i.e., the given attribute category had been a member of
     *         this attribute set.
     *
     * @throws  UnmodifiableSetException
     *     (unchecked exception) Thrown if this attribute set does not
     *     support the <CODE>remove()</CODE> operation.
     */
    public boolean remove(Class<?> category) {
        return
            category != null &&
            AttributeSetUtilities.
            verifyAttributeCategory(category, Attribute.class) != null &&
            attrMap.remove(category) != null;
    }

    /**
     * Removes the specified attribute from this attribute set if
     * present. If <CODE>attribute</CODE> is null, then
     * <CODE>remove()</CODE> does nothing and returns <tt>false</tt>.
     *
     * @param attribute Attribute value to be removed from this attribute set.
     *
     * @return  <tt>true</tt> if this attribute set changed as a result of the
     *         call, i.e., the given attribute value had been a member of
     *         this attribute set.
     *
     * @throws  UnmodifiableSetException
     *     (unchecked exception) Thrown if this attribute set does not
     *     support the <CODE>remove()</CODE> operation.
     */
    public boolean remove(Attribute attribute) {
        return
            attribute != null &&
            attrMap.remove(attribute.getCategory()) != null;
    }

    /**
     * Returns <tt>true</tt> if this attribute set contains an
     * attribute for the specified category.
     *
     * @param  category whose presence in this attribute set is
     *            to be tested.
     *
     * @return  <tt>true</tt> if this attribute set contains an attribute
     *         value for the specified category.
     */
    public boolean containsKey(Class<?> category) {
        return
            category != null &&
            AttributeSetUtilities.
            verifyAttributeCategory(category, Attribute.class) != null &&
            attrMap.get(category) != null;
    }

    /**
     * Returns <tt>true</tt> if this attribute set contains the given
     * attribute.
     *
     * @param  attribute  value whose presence in this attribute set is
     *            to be tested.
     *
     * @return  <tt>true</tt> if this attribute set contains the given
     *      attribute    value.
     */
    public boolean containsValue(Attribute attribute) {
        return
           attribute != null &&
           attribute instanceof Attribute &&
           attribute.equals(attrMap.get(((Attribute)attribute).getCategory()));
    }

    /**
     * Adds all of the elements in the specified set to this attribute.
     * The outcome is the same as if the
     * {@link #add(Attribute) <CODE>add(Attribute)</CODE>}
     * operation had been applied to this attribute set successively with
     * each element from the specified set.
     * The behavior of the <CODE>addAll(AttributeSet)</CODE>
     * operation is unspecified if the specified set is modified while
     * the operation is in progress.
     * <P>
     * If the <CODE>addAll(AttributeSet)</CODE> operation throws an exception,
     * the effect on this attribute set's state is implementation dependent;
     * elements from the specified set before the point of the exception may
     * or may not have been added to this attribute set.
     *
     * @param  attributes  whose elements are to be added to this attribute
     *            set.
     *
     * @return  <tt>true</tt> if this attribute set changed as a result of the
     *          call.
     *
     * @throws  UnmodifiableSetException
     *    (Unchecked exception) Thrown if this attribute set does not
     *     support the <tt>addAll(AttributeSet)</tt> method.
     * @throws  NullPointerException
     *     (Unchecked exception) Thrown if some element in the specified
     *     set is null, or the set is null.
     *
     * @see #add(Attribute)
     */
    public boolean addAll(AttributeSet attributes) {

        Attribute []attrs = attributes.toArray();
        boolean result = false;
        for (int i=0; i<attrs.length; i++) {
            Attribute newValue =
                AttributeSetUtilities.verifyAttributeValue(attrs[i],
                                                           myInterface);
            Object oldValue = attrMap.put(newValue.getCategory(), newValue);
            result = (! newValue.equals(oldValue)) || result;
        }
        return result;
    }

    /**
     * Returns the number of attributes in this attribute set. If this
     * attribute set contains more than <tt>Integer.MAX_VALUE</tt> elements,
     * returns  <tt>Integer.MAX_VALUE</tt>.
     *
     * @return  The number of attributes in this attribute set.
     */
    public int size() {
        return attrMap.size();
    }

    /**
     *
     * @return the Attributes contained in this set as an array, zero length
     * if the AttributeSet is empty.
     */
    public Attribute[] toArray() {
        Attribute []attrs = new Attribute[size()];
        attrMap.values().toArray(attrs);
        return attrs;
    }


    /**
     * Removes all attributes from this attribute set.
     *
     * @throws  UnmodifiableSetException
     *   (unchecked exception) Thrown if this attribute set does not support
     *     the <CODE>clear()</CODE> operation.
     */
    public void clear() {
        attrMap.clear();
    }

   /**
     * Returns true if this attribute set contains no attributes.
     *
     * @return true if this attribute set contains no attributes.
     */
    public boolean isEmpty() {
        return attrMap.isEmpty();
    }

    /**
     * Compares the specified object with this attribute set for equality.
     * Returns <tt>true</tt> if the given object is also an attribute set and
     * the two attribute sets contain the same attribute category-attribute
     * value mappings. This ensures that the
     * <tt>equals()</tt> method works properly across different
     * implementations of the AttributeSet interface.
     *
     * @param  object to be compared for equality with this attribute set.
     *
     * @return  <tt>true</tt> if the specified object is equal to this
     *       attribute   set.
     */

    public boolean equals(Object object) {
        if (object == null || !(object instanceof AttributeSet)) {
            return false;
        }

        AttributeSet aset = (AttributeSet)object;
        if (aset.size() != size()) {
            return false;
        }

        Attribute[] attrs = toArray();
        for (int i=0;i<attrs.length; i++) {
            if (!aset.containsValue(attrs[i])) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns the hash code value for this attribute set.
     * The hash code of an attribute set is defined to be the sum
     * of the hash codes of each entry in the AttributeSet.
     * This ensures that <tt>t1.equals(t2)</tt> implies that
     * <tt>t1.hashCode()==t2.hashCode()</tt> for any two attribute sets
     * <tt>t1</tt> and <tt>t2</tt>, as required by the general contract of
     * {@link java.lang.Object#hashCode() <CODE>Object.hashCode()</CODE>}.
     *
     * @return  The hash code value for this attribute set.
     */
    public int hashCode() {
        int hcode = 0;
        Attribute[] attrs = toArray();
        for (int i=0;i<attrs.length; i++) {
            hcode += attrs[i].hashCode();
        }
        return hcode;
    }

}
