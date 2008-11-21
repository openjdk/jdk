/*
 * Copyright 1999-2005 Sun Microsystems, Inc.  All Rights Reserved.
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

package javax.management;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Represents a list of values for attributes of an MBean. The methods
 * used for the insertion of {@link javax.management.Attribute
 * Attribute} objects in the <CODE>AttributeList</CODE> overrides the
 * corresponding methods in the superclass
 * <CODE>ArrayList</CODE>. This is needed in order to insure that the
 * objects contained in the <CODE>AttributeList</CODE> are only
 * <CODE>Attribute</CODE> objects. This avoids getting an exception
 * when retrieving elements from the <CODE>AttributeList</CODE>.
 *
 * @since 1.5
 */
/* We cannot extend ArrayList<Attribute> because our legacy
   add(Attribute) method would then override add(E) in ArrayList<E>,
   and our return value is void whereas ArrayList.add(E)'s is boolean.
   Likewise for set(int,Attribute).  Grrr.  We cannot use covariance
   to override the most important methods and have them return
   Attribute, either, because that would break subclasses that
   override those methods in turn (using the original return type
   of Object).  Finally, we cannot implement Iterable<Attribute>
   so you could write
       for (Attribute a : attributeList)
   because ArrayList<> implements Iterable<> and the same class cannot
   implement two versions of a generic interface.  Instead we provide
   the asList() method so you can write
       for (Attribute a : attributeList.asList())
*/
public class AttributeList extends ArrayList<Object> {

    private transient boolean typeSafe;
    private transient boolean tainted;

    /* Serial version */
    private static final long serialVersionUID = -4077085769279709076L;

    /**
     * Constructs an empty <CODE>AttributeList</CODE>.
     */
    public AttributeList() {
        super();
    }

    /**
     * Constructs an empty <CODE>AttributeList</CODE> with
     * the initial capacity specified.
     *
     * @param initialCapacity the initial capacity of the
     * <code>AttributeList</code>, as specified by {@link
     * ArrayList#ArrayList(int)}.
     */
    public AttributeList(int initialCapacity) {
        super(initialCapacity);
    }

    /**
     * Constructs an <CODE>AttributeList</CODE> containing the
     * elements of the <CODE>AttributeList</CODE> specified, in the
     * order in which they are returned by the
     * <CODE>AttributeList</CODE>'s iterator.  The
     * <CODE>AttributeList</CODE> instance has an initial capacity of
     * 110% of the size of the <CODE>AttributeList</CODE> specified.
     *
     * @param list the <code>AttributeList</code> that defines the initial
     * contents of the new <code>AttributeList</code>.
     *
     * @see ArrayList#ArrayList(java.util.Collection)
     */
    public AttributeList(AttributeList list) {
        super(list);
    }

    /**
     * Constructs an {@code AttributeList} containing the elements of the
     * {@code List} specified, in the order in which they are returned by
     * the {@code List}'s iterator.
     *
     * @param list the {@code List} that defines the initial contents of
     * the new {@code AttributeList}.
     *
     * @exception IllegalArgumentException if the {@code list} parameter
     * is {@code null} or if the {@code list} parameter contains any
     * non-Attribute objects.
     *
     * @see ArrayList#ArrayList(java.util.Collection)
     *
     * @since 1.6
     */
    public AttributeList(List<Attribute> list) {
        // Check for null parameter
        //
        if (list == null)
            throw new IllegalArgumentException("Null parameter");

        // Check for non-Attribute objects
        //
        checkTypeSafe(list);

        // Build the List<Attribute>
        //
        super.addAll(list);
    }

    /**
     * Return a view of this list as a {@code List<Attribute>}.
     * Changes to the returned value are reflected by changes
     * to the original {@code AttributeList} and vice versa.
     *
     * @return a {@code List<Attribute>} whose contents
     * reflect the contents of this {@code AttributeList}.
     *
     * <p>If this method has ever been called on a given
     * {@code AttributeList} instance, a subsequent attempt to add
     * an object to that instance which is not an {@code Attribute}
     * will fail with a {@code IllegalArgumentException}. For compatibility
     * reasons, an {@code AttributeList} on which this method has never
     * been called does allow objects other than {@code Attribute}s to
     * be added.</p>
     *
     * @throws IllegalArgumentException if this {@code AttributeList} contains
     * an element that is not an {@code Attribute}.
     *
     * @since 1.6
     */
    @SuppressWarnings("unchecked")
    public List<Attribute> asList() {
        if (!typeSafe) {
            if (tainted)
                checkTypeSafe(this);
            typeSafe = true;
        }
        return (List<Attribute>) (List<?>) this;
    }

    /**
     * Adds the {@code Attribute} specified as the last element of the list.
     *
     * @param object  The attribute to be added.
     */
    public void add(Attribute object)  {
        super.add(object);
    }

    /**
     * Inserts the attribute specified as an element at the position specified.
     * Elements with an index greater than or equal to the current position are
     * shifted up. If the index is out of range (index < 0 || index >
     * size() a RuntimeOperationsException should be raised, wrapping the
     * java.lang.IndexOutOfBoundsException thrown.
     *
     * @param object  The <CODE>Attribute</CODE> object to be inserted.
     * @param index The position in the list where the new {@code Attribute}
     * object is to be inserted.
     */
    public void add(int index, Attribute object)  {
        try {
            super.add(index, object);
        }
        catch (IndexOutOfBoundsException e) {
            throw new RuntimeOperationsException(e,
                "The specified index is out of range");
        }
    }

    /**
     * Sets the element at the position specified to be the attribute specified.
     * The previous element at that position is discarded. If the index is
     * out of range (index < 0 || index > size() a RuntimeOperationsException
     * should be raised, wrapping the java.lang.IndexOutOfBoundsException thrown.
     *
     * @param object  The value to which the attribute element should be set.
     * @param index  The position specified.
     */
    public void set(int index, Attribute object)  {
        try {
            super.set(index, object);
        }
        catch (IndexOutOfBoundsException e) {
            throw new RuntimeOperationsException(e,
                "The specified index is out of range");
        }
    }

    /**
     * Appends all the elements in the <CODE>AttributeList</CODE> specified to
     * the end of the list, in the order in which they are returned by the
     * Iterator of the <CODE>AttributeList</CODE> specified.
     *
     * @param list  Elements to be inserted into the list.
     *
     * @return true if this list changed as a result of the call.
     *
     * @see ArrayList#addAll(java.util.Collection)
     */
    public boolean addAll(AttributeList list)  {
        return (super.addAll(list));
    }

    /**
     * Inserts all of the elements in the <CODE>AttributeList</CODE> specified
     * into this list, starting at the specified position, in the order in which
     * they are returned by the Iterator of the {@code AttributeList} specified.
     * If the index is out of range (index < 0 || index > size() a
     * RuntimeOperationsException should be raised, wrapping the
     * java.lang.IndexOutOfBoundsException thrown.
     *
     * @param list  Elements to be inserted into the list.
     * @param index  Position at which to insert the first element from the
     * <CODE>AttributeList</CODE> specified.
     *
     * @return true if this list changed as a result of the call.
     *
     * @see ArrayList#addAll(int, java.util.Collection)
     */
    public boolean addAll(int index, AttributeList list)  {
        try {
            return super.addAll(index, list);
        }
        catch (IndexOutOfBoundsException e) {
            throw new RuntimeOperationsException(e,
                "The specified index is out of range");
        }
    }

    /*
     * Override all of the methods from ArrayList<Object> that might add
     * a non-Attribute to the List, and disallow that if asList has ever
     * been called on this instance.
     */

    @Override
    public boolean add(Object o) {
        if (!tainted)
            tainted = isTainted(o);
        if (typeSafe)
            checkTypeSafe(o);
        return super.add(o);
    }

    @Override
    public void add(int index, Object element) {
        if (!tainted)
            tainted = isTainted(element);
        if (typeSafe)
            checkTypeSafe(element);
        super.add(index, element);
    }

    @Override
    public boolean addAll(Collection<?> c) {
        if (!tainted)
            tainted = isTainted(c);
        if (typeSafe)
            checkTypeSafe(c);
        return super.addAll(c);
    }

    @Override
    public boolean addAll(int index, Collection<?> c) {
        if (!tainted)
            tainted = isTainted(c);
        if (typeSafe)
            checkTypeSafe(c);
        return super.addAll(index, c);
    }

    @Override
    public Object set(int index, Object element) {
        if (!tainted)
            tainted = isTainted(element);
        if (typeSafe)
            checkTypeSafe(element);
        return super.set(index, element);
    }

    /**
     * IllegalArgumentException if o is a non-Attribute object.
     */
    private static void checkTypeSafe(Object o) {
        try {
            o = (Attribute) o;
        } catch (ClassCastException e) {
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * IllegalArgumentException if c contains any non-Attribute objects.
     */
    private static void checkTypeSafe(Collection<?> c) {
        try {
            Attribute a;
            for (Object o : c)
                a = (Attribute) o;
        } catch (ClassCastException e) {
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * Returns true if o is a non-Attribute object.
     */
    private static boolean isTainted(Object o) {
        try {
            checkTypeSafe(o);
        } catch (IllegalArgumentException e) {
            return true;
        }
        return false;
    }

    /**
     * Returns true if c contains any non-Attribute objects.
     */
    private static boolean isTainted(Collection<?> c) {
        try {
            checkTypeSafe(c);
        } catch (IllegalArgumentException e) {
            return true;
        }
        return false;
    }
}
