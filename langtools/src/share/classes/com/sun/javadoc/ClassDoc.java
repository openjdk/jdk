/*
 * Copyright (c) 1998, 2014, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.javadoc;


/**
 * Represents a java class or interface and provides access to
 * information about the class, the class's comment and tags, and the
 * members of the class.  A ClassDoc only exists if it was
 * processed in this run of javadoc.  References to classes
 * which may or may not have been processed in this run are
 * referred to using Type (which can be converted to ClassDoc,
 * if possible).
 *
 * @see Type
 *
 * @since 1.2
 * @author Kaiyang Liu (original)
 * @author Robert Field (rewrite)
 */
public interface ClassDoc extends ProgramElementDoc, Type {

    /**
     * Return true if this class is abstract.  Return true
     * for all interfaces.
     *
     * @return true if this class is abstract.  Return true
     *         for all interfaces.
     */
    boolean isAbstract();

    /**
     * Return true if this class implements or interface extends
     * {@code java.io.Serializable}.
     *
     * Since {@code java.io.Externalizable} extends
     * {@code java.io.Serializable},
     * Externalizable objects are also Serializable.
     *
     * @return true if this class implements or interface extends
     *         {@code java.io.Serializable}.
     */
    boolean isSerializable();

    /**
     * Return true if this class implements or interface extends
     * {@code java.io.Externalizable}.
     *
     * @return true if this class implements or interface extends
     *         {@code java.io.Externalizable}.
     */
    boolean isExternalizable();

    /**
     * Return true if this class can be used as a target type of a lambda expression
     * or method reference.
     *
     * @return true if this class can be used as a target type of a lambda expression
     *         or method reference.
     */
    boolean isFunctionalInterface();

    /**
     * Return the serialization methods for this class or
     * interface.
     *
     * @return an array of MethodDoc objects that represents
     *         the serialization methods for this class or interface.
     */
    MethodDoc[] serializationMethods();

    /**
     * Return the Serializable fields of this class or interface.
     * <p>
     * Return either a list of default fields documented by
     * {@code serial} tag<br>
     * or return a single {@code FieldDoc} for
     * {@code serialPersistentField} member.
     * There should be a {@code serialField} tag for
     * each Serializable field defined by an {@code ObjectStreamField}
     * array component of {@code serialPersistentField}.
     *
     * @return an array of {@code FieldDoc} objects for the Serializable
     *         fields of this class or interface.
     *
     * @see #definesSerializableFields()
     * @see SerialFieldTag
     */
    FieldDoc[] serializableFields();

    /**
     *  Return true if Serializable fields are explicitly defined with
     *  the special class member {@code serialPersistentFields}.
     *
     * @return true if Serializable fields are explicitly defined with
     *         the special class member {@code serialPersistentFields}.
     *
     * @see #serializableFields()
     * @see SerialFieldTag
     */
    boolean definesSerializableFields();

    /**
     * Return the superclass of this class.  Return null if this is an
     * interface.
     *
     * <p> <i>This method cannot accommodate certain generic type constructs.
     * The {@code superclassType} method should be used instead.</i>
     *
     * @return the ClassDoc for the superclass of this class, null if
     *         there is no superclass.
     * @see #superclassType
     */
    ClassDoc superclass();

    /**
     * Return the superclass of this class.  Return null if this is an
     * interface.  A superclass is represented by either a
     * {@code ClassDoc} or a {@code ParametrizedType}.
     *
     * @return the superclass of this class, or null if there is no superclass.
     * @since 1.5
     */
    Type superclassType();

    /**
     * Test whether this class is a subclass of the specified class.
     * If this is an interface, return false for all classes except
     * {@code java.lang.Object} (we must keep this unexpected
     * behavior for compatibility reasons).
     *
     * @param cd the candidate superclass.
     * @return true if cd is a superclass of this class.
     */
    boolean subclassOf(ClassDoc cd);

    /**
     * Return interfaces implemented by this class or interfaces extended
     * by this interface. Includes only directly-declared interfaces, not
     * inherited interfaces.
     * Return an empty array if there are no interfaces.
     *
     * <p> <i>This method cannot accommodate certain generic type constructs.
     * The {@code interfaceTypes} method should be used instead.</i>
     *
     * @return an array of ClassDoc objects representing the interfaces.
     * @see #interfaceTypes
     */
    ClassDoc[] interfaces();

    /**
     * Return interfaces implemented by this class or interfaces extended
     * by this interface. Includes only directly-declared interfaces, not
     * inherited interfaces.
     * Return an empty array if there are no interfaces.
     *
     * @return an array of interfaces, each represented by a
     *         {@code ClassDoc} or a {@code ParametrizedType}.
     * @since 1.5
     */
    Type[] interfaceTypes();

    /**
     * Return the formal type parameters of this class or interface.
     * Return an empty array if there are none.
     *
     * @return the formal type parameters of this class or interface.
     * @since 1.5
     */
    TypeVariable[] typeParameters();

    /**
     * Return the type parameter tags of this class or interface.
     * Return an empty array if there are none.
     *
     * @return the type parameter tags of this class or interface.
     * @since 1.5
     */
    ParamTag[] typeParamTags();

    /**
     * Return
     * <a href="{@docRoot}/com/sun/javadoc/package-summary.html#included">included</a>
     * fields in this class or interface.
     * Excludes enum constants if this is an enum type.
     *
     * @return an array of FieldDoc objects representing the included
     *         fields in this class or interface.
     */
    FieldDoc[] fields();

    /**
     * Return fields in this class or interface, filtered to the specified
     * <a href="{@docRoot}/com/sun/javadoc/package-summary.html#included">access
     * modifier option</a>.
     * Excludes enum constants if this is an enum type.
     *
     * @param filter Specify true to filter according to the specified access
     *               modifier option.
     *               Specify false to include all fields regardless of
     *               access modifier option.
     * @return       an array of FieldDoc objects representing the included
     *               fields in this class or interface.
     */
    FieldDoc[] fields(boolean filter);

    /**
     * Return the enum constants if this is an enum type.
     * Return an empty array if there are no enum constants, or if
     * this is not an enum type.
     *
     * @return the enum constants if this is an enum type.
     */
    FieldDoc[] enumConstants();

    /**
     * Return
     * <a href="{@docRoot}/com/sun/javadoc/package-summary.html#included">included</a>
     * methods in this class or interface.
     * Same as {@code methods(true)}.
     *
     * @return an array of MethodDoc objects representing the included
     *         methods in this class or interface.  Does not include
     *         constructors or annotation type elements.
     */
    MethodDoc[] methods();

    /**
     * Return methods in this class or interface, filtered to the specified
     * <a href="{@docRoot}/com/sun/javadoc/package-summary.html#included">access
     * modifier option</a>.  Does not include constructors or annotation
     *          type elements.
     *
     * @param filter Specify true to filter according to the specified access
     *               modifier option.
     *               Specify false to include all methods regardless of
     *               access modifier option.
     *
     * @return       an array of MethodDoc objects representing the included
     *               methods in this class or interface.
     */
    MethodDoc[] methods(boolean filter);

    /**
     * Return
     * <a href="{@docRoot}/com/sun/javadoc/package-summary.html#included">included</a>
     * constructors in this class.  An array containing the default
     * no-arg constructor is returned if no other constructors exist.
     * Return empty array if this is an interface.
     *
     * @return an array of ConstructorDoc objects representing the included
     *         constructors in this class.
     */
    ConstructorDoc[] constructors();

    /**
     * Return constructors in this class, filtered to the specified
     * <a href="{@docRoot}/com/sun/javadoc/package-summary.html#included">access
     * modifier option</a>.  Return an array containing the default
     * no-arg constructor if no other constructors exist.
     *
     * @param filter Specify true to filter according to the specified access
     *               modifier option.
     *               Specify false to include all constructors regardless of
     *               access modifier option.
     * @return       an array of ConstructorDoc objects representing the included
     *               constructors in this class.
     */
    ConstructorDoc[] constructors(boolean filter);


    /**
     * Return
     * <a href="{@docRoot}/com/sun/javadoc/package-summary.html#included">included</a>
     * nested classes and interfaces within this class or interface.
     * This includes both static and non-static nested classes.
     * (This method should have been named {@code nestedClasses()},
     * as inner classes are technically non-static.)  Anonymous and local classes
     * or interfaces are not included.
     *
     * @return an array of ClassDoc objects representing the included classes
     *         and interfaces defined in this class or interface.
     */
    ClassDoc[] innerClasses();

    /**
     * Return nested classes and interfaces within this class or interface
     * filtered to the specified
     * <a href="{@docRoot}/com/sun/javadoc/package-summary.html#included">access
     * modifier option</a>.
     * This includes both static and non-static nested classes.
     * Anonymous and local classes are not included.
     *
     * @param filter Specify true to filter according to the specified access
     *               modifier option.
     *               Specify false to include all nested classes regardless of
     *               access modifier option.
     * @return       a filtered array of ClassDoc objects representing the included
     *               classes and interfaces defined in this class or interface.
     */
    ClassDoc[] innerClasses(boolean filter);

    /**
     * Find the specified class or interface within the context of this class doc.
     * Search order: 1) qualified name, 2) nested in this class or interface,
     * 3) in this package, 4) in the class imports, 5) in the package imports.
     * Return the ClassDoc if found, null if not found.
     * @param className Specify the class name to find as a String.
     * @return the ClassDoc if found, null if not found.
     */
    ClassDoc findClass(String className);

    /**
     * Get the list of classes and interfaces declared as imported.
     * These are called "single-type-import declarations" in
     * <cite>The Java&trade; Language Specification</cite>.
     *
     * @return an array of ClassDoc representing the imported classes.
     *
     * @deprecated  Import declarations are implementation details that
     *          should not be exposed here.  In addition, not all imported
     *          classes are imported through single-type-import declarations.
     */
    @Deprecated
    ClassDoc[] importedClasses();

    /**
     * Get the list of packages declared as imported.
     * These are called "type-import-on-demand declarations" in
     * <cite>The Java&trade; Language Specification</cite>.
     *
     * @return an array of PackageDoc representing the imported packages.
     *
     * @deprecated  Import declarations are implementation details that
     *          should not be exposed here.  In addition, this method's
     *          return type does not allow for all type-import-on-demand
     *          declarations to be returned.
     */
    @Deprecated
    PackageDoc[] importedPackages();
}
