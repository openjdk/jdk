/*
 * Copyright 2005-2006 Sun Microsystems, Inc.  All Rights Reserved.
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

package com.sun.xml.internal.bind.v2.model.nav;

import java.util.Collection;

import com.sun.xml.internal.bind.v2.runtime.Location;

/**
 * Provides unified view of the underlying reflection library,
 * such as {@code java.lang.reflect} and/or APT.
 *
 * <p>
 * This interface provides navigation over the reflection model
 * to decouple the caller from any particular implementation.
 * This allows the JAXB RI to reuse much of the code between
 * the compile time (which works on top of APT) and the run-time
 * (which works on top of {@code java.lang.reflect})
 *
 * <p>
 * {@link Navigator} instances are stateless and immutable.
 *
 *
 * <h2>Parameterization</h2>
 * <h3>C</h3>
 * <p>
 * A Java class declaration (not an interface, a class and an enum.)
 *
 * <h3>T</h3>
 * <p>
 * A Java type. This includs declaration, but also includes such
 * things like arrays, primitive types, parameterized types, and etc.
 *
 * @author Kohsuke Kawaguchi (kk@kohsuke.org)
 */
public interface Navigator<T,C,F,M> {
    /**
     * Gets the base class of the specified class.
     *
     * @return
     *      null if the parameter represents {@link Object}.
     */
    C getSuperClass(C clazz);

    /**
     * Gets the parameterization of the given base type.
     *
     * <p>
     * For example, given the following
     * <pre><xmp>
     * interface Foo<T> extends List<List<T>> {}
     * interface Bar extends Foo<String> {}
     * </xmp></pre>
     * This method works like this:
     * <pre><xmp>
     * getBaseClass( Bar, List ) = List<List<String>
     * getBaseClass( Bar, Foo  ) = Foo<String>
     * getBaseClass( Foo<? extends Number>, Collection ) = Collection<List<? extends Number>>
     * getBaseClass( ArrayList<? extends BigInteger>, List ) = List<? extends BigInteger>
     * </xmp></pre>
     *
     * @param type
     *      The type that derives from {@code baseType}
     * @param baseType
     *      The class whose parameterization we are interested in.
     * @return
     *      The use of {@code baseType} in {@code type}.
     *      or null if the type is not assignable to the base type.
     */
    T getBaseClass(T type, C baseType);

    /**
     * Gets the fully-qualified name of the class.
     * ("java.lang.Object" for {@link Object})
     */
    String getClassName(C clazz);

    /**
     * Gets the display name of the type object
     *
     * @return
     *      a human-readable name that the type represents.
     */
    String getTypeName(T rawType);

    /**
     * Gets the short name of the class ("Object" for {@link Object}.)
     *
     * For nested classes, this method should just return the inner name.
     * (for example "Inner" for "com.acme.Outer$Inner".
     */
    String getClassShortName(C clazz);

    /**
     * Gets all the declared fields of the given class.
     */
    Collection<? extends F> getDeclaredFields(C clazz);

    /**
     * Gets the named field declared on the given class.
     *
     * This method doesn't visit ancestors, but does recognize
     * non-public fields.
     *
     * @return
     *      null if not found
     */
    F getDeclaredField(C clazz, String fieldName);

    /**
     * Gets all the declared methods of the given class
     * (regardless of their access modifiers, regardless
     * of whether they override methods of the base classes.)
     *
     * <p>
     * Note that this method does not list methods declared on base classes.
     *
     * @return
     *      can be empty but always non-null.
     */
    Collection<? extends M> getDeclaredMethods(C clazz);

    /**
     * Gets the class that declares the given field.
     */
    C getDeclaringClassForField(F field);

    /**
     * Gets the class that declares the given method.
     */
    C getDeclaringClassForMethod(M method);

    /**
     * Gets the type of the field.
     */
    T getFieldType(F f);

    /**
     * Gets the name of the field.
     */
    String getFieldName(F field);

    /**
     * Gets the name of the method, such as "toString" or "equals".
     */
    String getMethodName(M m);

    /**
     * Gets the return type of a method.
     */
    T getReturnType(M m);

    /**
     * Returns the list of parameters to the method.
     */
    T[] getMethodParameters(M method);

    /**
     * Returns true if the method is static.
     */
    boolean isStaticMethod(M method);

    /**
     * Checks if {@code sub} is a sub-type of {@code sup}.
     *
     * TODO: should this method take T or C?
     */
    boolean isSubClassOf(T sub, T sup);

    /**
     * Gets the representation of the given Java type in {@code T}.
     *
     * @param c
     *      can be a primitive, array, class, or anything.
     *      (therefore the return type has to be T, not C)
     */
    T ref(Class c);

    /**
     * Gets the T for the given C.
     */
    T use(C c);

    /**
     * If the given type is an use of class declaration,
     * returns the type casted as {@code C}.
     * Otherwise null.
     *
     * <p>
     * TODO: define the exact semantics.
     */
    C asDecl(T type);

    /**
     * Gets the {@code C} representation for the given class.
     *
     * The behavior is undefined if the class object represents
     * primitives, arrays, and other types that are not class declaration.
     */
    C asDecl(Class c);

    /**
     * Checks if the type is an array type.
     */
    boolean isArray(T t);

    /**
     * Checks if the type is an array type but not byte[].
     */
    boolean isArrayButNotByteArray(T t);

    /**
     * Gets the component type of the array.
     *
     * @param t
     *      must be an array.
     */
    T getComponentType(T t);


    /** The singleton instance. */
    public static final ReflectionNavigator REFLECTION = new ReflectionNavigator();

    /**
     * Gets the i-th type argument from a parameterized type.
     *
     * For example, {@code getTypeArgument([Map<Integer,String>],0)=Integer}
     *
     * @throws IllegalArgumentException
     *      If t is not a parameterized type
     * @throws IndexOutOfBoundsException
     *      If i is out of range.
     *
     * @see #isParameterizedType(Object)
     */
    T getTypeArgument(T t, int i);

    /**
     * Returns true if t is a parameterized type.
     */
    boolean isParameterizedType(T t);

    /**
     * Checks if the given type is a primitive type.
     */
    boolean isPrimitive(T t);

    /**
     * Returns the representation for the given primitive type.
     *
     * @param primitiveType
     *      must be Class objects like {@link Integer#TYPE}.
     */
    T getPrimitive(Class primitiveType);

    /**
     * Returns a location of the specified class.
     */
    Location getClassLocation(C clazz);

    Location getFieldLocation(F field);

    Location getMethodLocation(M getter);

    /**
     * Returns true if the given class has a no-arg default constructor.
     * The constructor does not need to be public.
     */
    boolean hasDefaultConstructor(C clazz);

    /**
     * Returns true if the field is static.
     */
    boolean isStaticField(F field);

    /**
     * Returns true if the method is public.
     */
    boolean isPublicMethod(M method);

    /**
     * Returns true if the field is public.
     */
    boolean isPublicField(F field);

    /**
     * Returns true if this is an enum class.
     */
    boolean isEnum(C clazz);

    /**
     * Computes the erasure
     */
    <P> T erasure(T contentInMemoryType);
    // This unused P is necessary to make ReflectionNavigator.erasure work nicely

    /**
     * Returns true if this is an abstract class.
     */
    boolean isAbstract(C clazz);

    /**
     * Returns true if this is a final class.
     */
    boolean isFinal(C clazz);

    /**
     * Gets the enumeration constants from an enum class.
     *
     * @param clazz
     *      must derive from {@link Enum}.
     *
     * @return
     *      can be empty but never null.
     */
    F[] getEnumConstants(C clazz);

    /**
     * Gets the representation of the primitive "void" type.
     */
    T getVoidType();

    /**
     * Gets the package name of the given class.
     *
     * @return
     *      i.e. "", "java.lang" but not null.
     */
    String getPackageName(C clazz);

    /**
     * Finds the class/interface/enum/annotation of the given name.
     *
     * @param referencePoint
     *      The class that refers to the specified class.
     * @return
     *      null if not found.
     */
    C findClass(String className, C referencePoint);

    /**
     * Returns true if this method is a bridge method as defined in JLS.
     */
    boolean isBridgeMethod(M method);

    /**
     * Returns true if the given method is overriding another one
     * defined in the base class 'base' or its ancestors.
     */
    boolean isOverriding(M method, C base);

    /**
     * Returns true if 'clazz' is an interface.
     */
    boolean isInterface(C clazz);

    /**
     * Returns true if the field is transient.
     */
    boolean isTransient(F f);

    /**
     * Returns true if the given class is an inner class.
     *
     * This is only used to improve the error diagnostics, so
     * it's OK to fail to detect some inner classes as such.
     *
     * Note that this method should return false for nested classes
     * (static classes.)
     */
    boolean isInnerClass(C clazz);
}
