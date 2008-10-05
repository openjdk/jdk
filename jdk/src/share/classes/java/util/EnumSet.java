/*
 * Copyright 2003-2008 Sun Microsystems, Inc.  All Rights Reserved.
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

package java.util;

import sun.misc.SharedSecrets;

/**
 * A specialized {@link Set} implementation for use with enum types.  All of
 * the elements in an enum set must come from a single enum type that is
 * specified, explicitly or implicitly, when the set is created.  Enum sets
 * are represented internally as bit vectors.  This representation is
 * extremely compact and efficient. The space and time performance of this
 * class should be good enough to allow its use as a high-quality, typesafe
 * alternative to traditional <tt>int</tt>-based "bit flags."  Even bulk
 * operations (such as <tt>containsAll</tt> and <tt>retainAll</tt>) should
 * run very quickly if their argument is also an enum set.
 *
 * <p>The iterator returned by the <tt>iterator</tt> method traverses the
 * elements in their <i>natural order</i> (the order in which the enum
 * constants are declared).  The returned iterator is <i>weakly
 * consistent</i>: it will never throw {@link ConcurrentModificationException}
 * and it may or may not show the effects of any modifications to the set that
 * occur while the iteration is in progress.
 *
 * <p>Null elements are not permitted.  Attempts to insert a null element
 * will throw {@link NullPointerException}.  Attempts to test for the
 * presence of a null element or to remove one will, however, function
 * properly.
 *
 * <P>Like most collection implementations, <tt>EnumSet</tt> is not
 * synchronized.  If multiple threads access an enum set concurrently, and at
 * least one of the threads modifies the set, it should be synchronized
 * externally.  This is typically accomplished by synchronizing on some
 * object that naturally encapsulates the enum set.  If no such object exists,
 * the set should be "wrapped" using the {@link Collections#synchronizedSet}
 * method.  This is best done at creation time, to prevent accidental
 * unsynchronized access:
 *
 * <pre>
 * Set&lt;MyEnum&gt; s = Collections.synchronizedSet(EnumSet.noneOf(MyEnum.class));
 * </pre>
 *
 * <p>Implementation note: All basic operations execute in constant time.
 * They are likely (though not guaranteed) to be much faster than their
 * {@link HashSet} counterparts.  Even bulk operations execute in
 * constant time if their argument is also an enum set.
 *
 * <p>This class is a member of the
 * <a href="{@docRoot}/../technotes/guides/collections/index.html">
 * Java Collections Framework</a>.
 *
 * @author Josh Bloch
 * @since 1.5
 * @see EnumMap
 * @serial exclude
 */
public abstract class EnumSet<E extends Enum<E>> extends AbstractSet<E>
    implements Cloneable, java.io.Serializable
{
    /**
     * The class of all the elements of this set.
     */
    final Class<E> elementType;

    /**
     * All of the values comprising T.  (Cached for performance.)
     */
    final Enum[] universe;

    private static Enum[] ZERO_LENGTH_ENUM_ARRAY = new Enum[0];

    EnumSet(Class<E>elementType, Enum[] universe) {
        this.elementType = elementType;
        this.universe    = universe;
    }

    /**
     * Creates an empty enum set with the specified element type.
     *
     * @param elementType the class object of the element type for this enum
     *     set
     * @throws NullPointerException if <tt>elementType</tt> is null
     */
    public static <E extends Enum<E>> EnumSet<E> noneOf(Class<E> elementType) {
        Enum[] universe = getUniverse(elementType);
        if (universe == null)
            throw new ClassCastException(elementType + " not an enum");

        if (universe.length <= 64)
            return new RegularEnumSet<E>(elementType, universe);
        else
            return new JumboEnumSet<E>(elementType, universe);
    }

    /**
     * Creates an enum set containing all of the elements in the specified
     * element type.
     *
     * @param elementType the class object of the element type for this enum
     *     set
     * @throws NullPointerException if <tt>elementType</tt> is null
     */
    public static <E extends Enum<E>> EnumSet<E> allOf(Class<E> elementType) {
        EnumSet<E> result = noneOf(elementType);
        result.addAll();
        return result;
    }

    /**
     * Adds all of the elements from the appropriate enum type to this enum
     * set, which is empty prior to the call.
     */
    abstract void addAll();

    /**
     * Creates an enum set with the same element type as the specified enum
     * set, initially containing the same elements (if any).
     *
     * @param s the enum set from which to initialize this enum set
     * @throws NullPointerException if <tt>s</tt> is null
     */
    public static <E extends Enum<E>> EnumSet<E> copyOf(EnumSet<E> s) {
        return s.clone();
    }

    /**
     * Creates an enum set initialized from the specified collection.  If
     * the specified collection is an <tt>EnumSet</tt> instance, this static
     * factory method behaves identically to {@link #copyOf(EnumSet)}.
     * Otherwise, the specified collection must contain at least one element
     * (in order to determine the new enum set's element type).
     *
     * @param c the collection from which to initialize this enum set
     * @throws IllegalArgumentException if <tt>c</tt> is not an
     *     <tt>EnumSet</tt> instance and contains no elements
     * @throws NullPointerException if <tt>c</tt> is null
     */
    public static <E extends Enum<E>> EnumSet<E> copyOf(Collection<E> c) {
        if (c instanceof EnumSet) {
            return ((EnumSet<E>)c).clone();
        } else {
            if (c.isEmpty())
                throw new IllegalArgumentException("Collection is empty");
            Iterator<E> i = c.iterator();
            E first = i.next();
            EnumSet<E> result = EnumSet.of(first);
            while (i.hasNext())
                result.add(i.next());
            return result;
        }
    }

    /**
     * Creates an enum set with the same element type as the specified enum
     * set, initially containing all the elements of this type that are
     * <i>not</i> contained in the specified set.
     *
     * @param s the enum set from whose complement to initialize this enum set
     * @throws NullPointerException if <tt>s</tt> is null
     */
    public static <E extends Enum<E>> EnumSet<E> complementOf(EnumSet<E> s) {
        EnumSet<E> result = copyOf(s);
        result.complement();
        return result;
    }

    /**
     * Creates an enum set initially containing the specified element.
     *
     * Overloadings of this method exist to initialize an enum set with
     * one through five elements.  A sixth overloading is provided that
     * uses the varargs feature.  This overloading may be used to create
     * an enum set initially containing an arbitrary number of elements, but
     * is likely to run slower than the overloadings that do not use varargs.
     *
     * @param e the element that this set is to contain initially
     * @throws NullPointerException if <tt>e</tt> is null
     * @return an enum set initially containing the specified element
     */
    public static <E extends Enum<E>> EnumSet<E> of(E e) {
        EnumSet<E> result = noneOf(e.getDeclaringClass());
        result.add(e);
        return result;
    }

    /**
     * Creates an enum set initially containing the specified elements.
     *
     * Overloadings of this method exist to initialize an enum set with
     * one through five elements.  A sixth overloading is provided that
     * uses the varargs feature.  This overloading may be used to create
     * an enum set initially containing an arbitrary number of elements, but
     * is likely to run slower than the overloadings that do not use varargs.
     *
     * @param e1 an element that this set is to contain initially
     * @param e2 another element that this set is to contain initially
     * @throws NullPointerException if any parameters are null
     * @return an enum set initially containing the specified elements
     */
    public static <E extends Enum<E>> EnumSet<E> of(E e1, E e2) {
        EnumSet<E> result = noneOf(e1.getDeclaringClass());
        result.add(e1);
        result.add(e2);
        return result;
    }

    /**
     * Creates an enum set initially containing the specified elements.
     *
     * Overloadings of this method exist to initialize an enum set with
     * one through five elements.  A sixth overloading is provided that
     * uses the varargs feature.  This overloading may be used to create
     * an enum set initially containing an arbitrary number of elements, but
     * is likely to run slower than the overloadings that do not use varargs.
     *
     * @param e1 an element that this set is to contain initially
     * @param e2 another element that this set is to contain initially
     * @param e3 another element that this set is to contain initially
     * @throws NullPointerException if any parameters are null
     * @return an enum set initially containing the specified elements
     */
    public static <E extends Enum<E>> EnumSet<E> of(E e1, E e2, E e3) {
        EnumSet<E> result = noneOf(e1.getDeclaringClass());
        result.add(e1);
        result.add(e2);
        result.add(e3);
        return result;
    }

    /**
     * Creates an enum set initially containing the specified elements.
     *
     * Overloadings of this method exist to initialize an enum set with
     * one through five elements.  A sixth overloading is provided that
     * uses the varargs feature.  This overloading may be used to create
     * an enum set initially containing an arbitrary number of elements, but
     * is likely to run slower than the overloadings that do not use varargs.
     *
     * @param e1 an element that this set is to contain initially
     * @param e2 another element that this set is to contain initially
     * @param e3 another element that this set is to contain initially
     * @param e4 another element that this set is to contain initially
     * @throws NullPointerException if any parameters are null
     * @return an enum set initially containing the specified elements
     */
    public static <E extends Enum<E>> EnumSet<E> of(E e1, E e2, E e3, E e4) {
        EnumSet<E> result = noneOf(e1.getDeclaringClass());
        result.add(e1);
        result.add(e2);
        result.add(e3);
        result.add(e4);
        return result;
    }

    /**
     * Creates an enum set initially containing the specified elements.
     *
     * Overloadings of this method exist to initialize an enum set with
     * one through five elements.  A sixth overloading is provided that
     * uses the varargs feature.  This overloading may be used to create
     * an enum set initially containing an arbitrary number of elements, but
     * is likely to run slower than the overloadings that do not use varargs.
     *
     * @param e1 an element that this set is to contain initially
     * @param e2 another element that this set is to contain initially
     * @param e3 another element that this set is to contain initially
     * @param e4 another element that this set is to contain initially
     * @param e5 another element that this set is to contain initially
     * @throws NullPointerException if any parameters are null
     * @return an enum set initially containing the specified elements
     */
    public static <E extends Enum<E>> EnumSet<E> of(E e1, E e2, E e3, E e4,
                                                    E e5)
    {
        EnumSet<E> result = noneOf(e1.getDeclaringClass());
        result.add(e1);
        result.add(e2);
        result.add(e3);
        result.add(e4);
        result.add(e5);
        return result;
    }

    /**
     * Creates an enum set initially containing the specified elements.
     * This factory, whose parameter list uses the varargs feature, may
     * be used to create an enum set initially containing an arbitrary
     * number of elements, but it is likely to run slower than the overloadings
     * that do not use varargs.
     *
     * @param first an element that the set is to contain initially
     * @param rest the remaining elements the set is to contain initially
     * @throws NullPointerException if any of the specified elements are null,
     *     or if <tt>rest</tt> is null
     * @return an enum set initially containing the specified elements
     */
    public static <E extends Enum<E>> EnumSet<E> of(E first, E... rest) {
        EnumSet<E> result = noneOf(first.getDeclaringClass());
        result.add(first);
        for (E e : rest)
            result.add(e);
        return result;
    }

    /**
     * Creates an enum set initially containing all of the elements in the
     * range defined by the two specified endpoints.  The returned set will
     * contain the endpoints themselves, which may be identical but must not
     * be out of order.
     *
     * @param from the first element in the range
     * @param to the last element in the range
     * @throws NullPointerException if {@code from} or {@code to} are null
     * @throws IllegalArgumentException if {@code from.compareTo(to) > 0}
     * @return an enum set initially containing all of the elements in the
     *         range defined by the two specified endpoints
     */
    public static <E extends Enum<E>> EnumSet<E> range(E from, E to) {
        if (from.compareTo(to) > 0)
            throw new IllegalArgumentException(from + " > " + to);
        EnumSet<E> result = noneOf(from.getDeclaringClass());
        result.addRange(from, to);
        return result;
    }

    /**
     * Adds the specified range to this enum set, which is empty prior
     * to the call.
     */
    abstract void addRange(E from, E to);

    /**
     * Returns a copy of this set.
     *
     * @return a copy of this set
     */
    public EnumSet<E> clone() {
        try {
            return (EnumSet<E>) super.clone();
        } catch(CloneNotSupportedException e) {
            throw new AssertionError(e);
        }
    }

    /**
     * Complements the contents of this enum set.
     */
    abstract void complement();

    /**
     * Throws an exception if e is not of the correct type for this enum set.
     */
    final void typeCheck(E e) {
        Class eClass = e.getClass();
        if (eClass != elementType && eClass.getSuperclass() != elementType)
            throw new ClassCastException(eClass + " != " + elementType);
    }

    /**
     * Returns all of the values comprising E.
     * The result is uncloned, cached, and shared by all callers.
     */
    private static <E extends Enum<E>> E[] getUniverse(Class<E> elementType) {
        return SharedSecrets.getJavaLangAccess()
                                        .getEnumConstantsShared(elementType);
    }

    /**
     * This class is used to serialize all EnumSet instances, regardless of
     * implementation type.  It captures their "logical contents" and they
     * are reconstructed using public static factories.  This is necessary
     * to ensure that the existence of a particular implementation type is
     * an implementation detail.
     *
     * @serial include
     */
    private static class SerializationProxy <E extends Enum<E>>
        implements java.io.Serializable
    {
        /**
         * The element type of this enum set.
         *
         * @serial
         */
        private final Class<E> elementType;

        /**
         * The elements contained in this enum set.
         *
         * @serial
         */
        private final Enum[] elements;

        SerializationProxy(EnumSet<E> set) {
            elementType = set.elementType;
            elements = set.toArray(ZERO_LENGTH_ENUM_ARRAY);
        }

        private Object readResolve() {
            EnumSet<E> result = EnumSet.noneOf(elementType);
            for (Enum e : elements)
                result.add((E)e);
            return result;
        }

        private static final long serialVersionUID = 362491234563181265L;
    }

    Object writeReplace() {
        return new SerializationProxy<E>(this);
    }

    // readObject method for the serialization proxy pattern
    // See Effective Java, Second Ed., Item 78.
    private void readObject(java.io.ObjectInputStream stream)
        throws java.io.InvalidObjectException {
        throw new java.io.InvalidObjectException("Proxy required");
    }
}
