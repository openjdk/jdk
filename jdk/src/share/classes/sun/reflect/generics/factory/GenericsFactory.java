/*
 * Copyright 2003-2006 Sun Microsystems, Inc.  All Rights Reserved.
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

package sun.reflect.generics.factory;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import sun.reflect.generics.tree.FieldTypeSignature;

/**
 * A factory interface for reflective objects representing generic types.
 * Implementors (such as core reflection or JDI, or possibly javadoc
 * will manufacture instances of (potentially) different classes
 * in response to invocations of the methods described here.
 * <p> The intent is that reflective systems use these factories to
 * produce generic type information on demand.
 * Certain components of such reflective systems can be independent
 * of a specific implementation by using this interface. For example,
 * repositories of generic type information are initialized with a
 * factory conforming to this interface, and use it to generate the
 * tpe information they are required to provide. As a result, such
 * repository code can be shared across different reflective systems.
 */
public interface GenericsFactory {
    /**
     * Returns a new type variable declaration. Note that <tt>name</tt>
     * may be empty (but not <tt>null</tt>). If <tt>bounds</tt> is
     * empty, a bound of <tt>java.lang.Object</tt> is used.
     * @param name The name of the type variable
     * @param bounds An array of abstract syntax trees representing
     * the upper bound(s) on the type variable being declared
     * @return a new type variable declaration
     * @throws NullPointerException - if any of the actual parameters
     * or any of the elements of <tt>bounds</tt> are <tt>null</tt>.
     */
    TypeVariable<?> makeTypeVariable(String name,
                                     FieldTypeSignature[] bounds);
    /**
     * Return an instance of the <tt>ParameterizedType</tt> interface
     * that corresponds to a generic type instantiation of the
     * generic declaration <tt>declaration</tt> with actual type arguments
     * <tt>typeArgs</tt>.
     * If <tt>owner</tt> is <tt>null</tt>, the declaring class of
     * <tt>declaration</tt> is used as the owner of this parameterized
     * type.
     * <p> This method throws a MalformedParameterizedTypeException
     * under the following circumstances:
     * If the type declaration does not represent a generic declaration
     * (i.e., it is not an instance of <tt>GenericDeclaration</tt>).
     * If the number of actual type arguments (i.e., the size of the
     * array <tt>typeArgs</tt>) does not correspond to the number of
     * formal type arguments.
     * If any of the actual type arguments is not an instance of the
     * bounds on the corresponding formal.
     * @param declaration - the generic type declaration that is to be
     * instantiated
     * @param typeArgs - the list of actual type arguments
     * @return - a parameterized type representing the instantiation
     * of the declaration with the actual type arguments
     * @throws MalformedParameterizedTypeException - if the instantiation
     * is invalid
     * @throws NullPointerException - if any of <tt>declaration</tt>
     * , <tt>typeArgs</tt>
     * or any of the elements of <tt>typeArgs</tt> are <tt>null</tt>
     */
    ParameterizedType makeParameterizedType(Type declaration,
                                            Type[] typeArgs,
                                            Type owner);

    /**
     * Returns the type variable with name <tt>name</tt>, if such
     * a type variable is declared in the
     * scope used to create this factory.
     * Returns <tt>null</tt> otherwise.
     * @param name - the name of the type variable to search for
     * @return - the type variable with name <tt>name</tt>, or <tt>null</tt>
     * @throws  NullPointerException - if any of actual parameters are
     * <tt>null</tt>
     */
    TypeVariable<?> findTypeVariable(String name);

    /**
     * Returns a new wildcard type variable. If
     * <tt>ubs</tt> is empty, a bound of <tt>java.lang.Object</tt> is used.
     * @param ubs An array of abstract syntax trees representing
     * the upper bound(s) on the type variable being declared
     * @param lbs An array of abstract syntax trees representing
     * the lower bound(s) on the type variable being declared
     * @return a new wildcard type variable
     * @throws NullPointerException - if any of the actual parameters
     * or any of the elements of <tt>ubs</tt> or <tt>lbs</tt>are
     * <tt>null</tt>
     */
    WildcardType makeWildcard(FieldTypeSignature[] ubs,
                              FieldTypeSignature[] lbs);

    Type makeNamedType(String name);

    /**
     * Returns a (possibly generic) array type.
     * If the component type is a parameterized type, it must
     * only have unbounded wildcard arguemnts, otherwise
     * a MalformedParameterizedTypeException is thrown.
     * @param componentType - the component type of the array
     * @return a (possibly generic) array type.
     * @throws MalformedParameterizedTypeException if <tt>componentType</tt>
     * is a parameterized type with non-wildcard type arguments
     * @throws NullPointerException - if any of the actual parameters
     * are <tt>null</tt>
     */
    Type makeArrayType(Type componentType);

    /**
     * Returns the reflective representation of type <tt>byte</tt>.
     * @return the reflective representation of type <tt>byte</tt>.
     */
    Type makeByte();

    /**
     * Returns the reflective representation of type <tt>boolean</tt>.
     * @return the reflective representation of type <tt>boolean</tt>.
     */
    Type makeBool();

    /**
     * Returns the reflective representation of type <tt>short</tt>.
     * @return the reflective representation of type <tt>short</tt>.
     */
    Type makeShort();

    /**
     * Returns the reflective representation of type <tt>char</tt>.
     * @return the reflective representation of type <tt>char</tt>.
     */
    Type makeChar();

    /**
     * Returns the reflective representation of type <tt>int</tt>.
     * @return the reflective representation of type <tt>int</tt>.
     */
    Type makeInt();

    /**
     * Returns the reflective representation of type <tt>long</tt>.
     * @return the reflective representation of type <tt>long</tt>.
     */
    Type makeLong();

    /**
     * Returns the reflective representation of type <tt>float</tt>.
     * @return the reflective representation of type <tt>float</tt>.
     */
    Type makeFloat();

    /**
     * Returns the reflective representation of type <tt>double</tt>.
     * @return the reflective representation of type <tt>double</tt>.
     */
    Type makeDouble();

    /**
     * Returns the reflective representation of <tt>void</tt>.
     * @return the reflective representation of <tt>void</tt>.
     */
    Type makeVoid();
}
