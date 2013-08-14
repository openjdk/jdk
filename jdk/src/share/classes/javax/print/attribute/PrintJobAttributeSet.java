/*
 * Copyright (c) 2000, 2001, Oracle and/or its affiliates. All rights reserved.
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

/**
 * Interface PrintJobAttributeSet specifies the interface for a set of print
 * job attributes, i.e. printing attributes that implement interface {@link
 * PrintJobAttribute PrintJobAttribute}. In the Print Service API, a
 * service uses a PrintJobAttributeSet to report the status of a print job.
 * <P>
 * A PrintJobAttributeSet is just an {@link AttributeSet AttributeSet} whose
 * constructors and mutating operations guarantee an additional invariant,
 * namely that all attribute values in the PrintJobAttributeSet must be
 * instances of interface {@link PrintJobAttribute PrintJobAttribute}.
 * The {@link #add(Attribute) add(Attribute)}, and
 * {@link #addAll(AttributeSet) >addAll(AttributeSet)} operations
 * are respecified below to guarantee this additional invariant.
 * <P>
 *
 * @author  Alan Kaminsky
 */
public interface PrintJobAttributeSet extends AttributeSet {

    /**
     * Adds the specified attribute value to this attribute set if it is not
     * already present, first removing any existing value in the same
     * attribute category as the specified attribute value (optional
     * operation).
     *
     * @param  attribute  Attribute value to be added to this attribute set.
     *
     * @return  <tt>true</tt> if this attribute set changed as a result of
     *          the call, i.e., the given attribute value was not already a
     *          member of this attribute set.
     *
     * @throws  UnmodifiableSetException
     *     (unchecked exception) Thrown if this attribute set does not
     *     support the <CODE>add()</CODE> operation.
     * @throws  ClassCastException
     *     (unchecked exception) Thrown if the <CODE>attribute</CODE> is
     *     not an instance of interface
     *     {@link PrintJobAttribute PrintJobAttribute}.
     * @throws  NullPointerException
     *    (unchecked exception) Thrown if the <CODE>attribute</CODE> is null.
     */
    public boolean add(Attribute attribute);

    /**
     * Adds all of the elements in the specified set to this attribute.
     * The outcome is  the same as if the
     * {@link #add(Attribute) add(Attribute)}
     * operation had been applied to this attribute set successively with
     * each element from the specified set. If none of the categories in the
     * specified set  are the same as any categories in this attribute set,
     * the <tt>addAll()</tt> operation effectively modifies this attribute
     * set so that its value is the <i>union</i> of the two sets.
     * <P>
     * The behavior of the <CODE>addAll()</CODE> operation is unspecified if
     * the specified set is modified while the operation is in progress.
     * <P>
     * If the <CODE>addAll()</CODE> operation throws an exception, the effect
     * on this attribute set's state is implementation dependent; elements
     * from the specified set before the point of the exception may or
     * may not have been added to this attribute set.
     *
     * @param  attributes  whose elements are to be added to this attribute
     *            set.
     *
     * @return  <tt>true</tt> if this attribute set changed as a result of
     *          the call.
     *
     * @throws  UnmodifiableSetException
     *     (Unchecked exception) Thrown if this attribute set does not
     *     support the <tt>addAll()</tt> method.
     * @throws  ClassCastException
     *     (Unchecked exception) Thrown if some element in the specified
     *     set is not an instance of interface {@link PrintJobAttribute
     *     PrintJobAttribute}.
     * @throws  NullPointerException
     *     (Unchecked exception) Thrown if the specified  set is null.
     *
     * @see #add(Attribute)
     */
    public boolean addAll(AttributeSet attributes);
}
