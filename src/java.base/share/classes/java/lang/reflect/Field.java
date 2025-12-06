/*
 * Copyright (c) 1996, 2025, Oracle and/or its affiliates. All rights reserved.
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

package java.lang.reflect;

import java.lang.annotation.Annotation;
import java.net.URL;
import java.security.CodeSource;
import java.util.Map;
import java.util.Set;
import java.util.Objects;
import jdk.internal.access.SharedSecrets;
import jdk.internal.event.FinalFieldMutationEvent;
import jdk.internal.loader.ClassLoaders;
import jdk.internal.misc.VM;
import jdk.internal.module.ModuleBootstrap;
import jdk.internal.module.Modules;
import jdk.internal.reflect.CallerSensitive;
import jdk.internal.reflect.FieldAccessor;
import jdk.internal.reflect.Reflection;
import jdk.internal.vm.annotation.ForceInline;
import jdk.internal.vm.annotation.Stable;
import sun.reflect.generics.repository.FieldRepository;
import sun.reflect.generics.factory.CoreReflectionFactory;
import sun.reflect.generics.factory.GenericsFactory;
import sun.reflect.generics.scope.ClassScope;
import sun.reflect.annotation.AnnotationParser;
import sun.reflect.annotation.AnnotationSupport;
import sun.reflect.annotation.TypeAnnotation;
import sun.reflect.annotation.TypeAnnotationParser;

/**
 * A {@code Field} provides information about, and dynamic access to, a
 * single field of a class or an interface.  The reflected field may
 * be a class (static) field or an instance field.
 *
 * <p>A {@code Field} permits widening conversions to occur during a get or
 * set access operation, but throws an {@code IllegalArgumentException} if a
 * narrowing conversion would occur.
 *
 * @see Member
 * @see java.lang.Class
 * @see java.lang.Class#getFields()
 * @see java.lang.Class#getField(String)
 * @see java.lang.Class#getDeclaredFields()
 * @see java.lang.Class#getDeclaredField(String)
 *
 * @author Kenneth Russell
 * @author Nakul Saraiya
 * @since 1.1
 */
public final
class Field extends AccessibleObject implements Member {
    private final Class<?>            clazz;
    private final int                 slot;
    // This is guaranteed to be interned by the VM in the 1.4
    // reflection implementation
    private final String              name;
    private final Class<?>            type;
    private final int                 modifiers;
    private final boolean             trustedFinal;
    // Generics and annotations support
    private final transient String    signature;
    private final byte[]              annotations;

    /**
     * Fields are mutable due to {@link AccessibleObject#setAccessible(boolean)}.
     * Thus, we return a new copy of a root each time a field is returned.
     * Some lazily initialized immutable states can be stored on root and shared to the copies.
     */
    private Field root;
    private transient volatile FieldRepository genericInfo;
    private @Stable FieldAccessor fieldAccessor; // access control enabled
    private @Stable FieldAccessor overrideFieldAccessor; // access control suppressed
    // End shared states

    // Generics infrastructure

    private String getGenericSignature() {return signature;}

    // Accessor for factory
    private GenericsFactory getFactory() {
        Class<?> c = getDeclaringClass();
        // create scope and factory
        return CoreReflectionFactory.make(c, ClassScope.make(c));
    }

    // Accessor for generic info repository
    private FieldRepository getGenericInfo() {
        var genericInfo = this.genericInfo;
        if (genericInfo == null) {
            var root = this.root;
            if (root != null) {
                genericInfo = root.getGenericInfo();
            } else {
                genericInfo = FieldRepository.make(getGenericSignature(), getFactory());
            }
            this.genericInfo = genericInfo;
        }
        return genericInfo;
    }

    /**
     * Package-private constructor
     */
    @SuppressWarnings("deprecation")
    Field(Class<?> declaringClass,
          String name,
          Class<?> type,
          int modifiers,
          boolean trustedFinal,
          int slot,
          String signature,
          byte[] annotations)
    {
        this.clazz = declaringClass;
        this.name = name;
        this.type = type;
        this.modifiers = modifiers;
        this.trustedFinal = trustedFinal;
        this.slot = slot;
        this.signature = signature;
        this.annotations = annotations;
    }

    /**
     * Package-private routine (exposed to java.lang.Class via
     * ReflectAccess) which returns a copy of this Field. The copy's
     * "root" field points to this Field.
     */
    Field copy() {
        // This routine enables sharing of FieldAccessor objects
        // among Field objects which refer to the same underlying
        // method in the VM. (All of this contortion is only necessary
        // because of the "accessibility" bit in AccessibleObject,
        // which implicitly requires that new java.lang.reflect
        // objects be fabricated for each reflective call on Class
        // objects.)
        if (this.root != null)
            throw new IllegalArgumentException("Can not copy a non-root Field");

        Field res = new Field(clazz, name, type, modifiers, trustedFinal, slot, signature, annotations);
        res.root = this;
        // Might as well eagerly propagate this if already present
        res.fieldAccessor = fieldAccessor;
        res.overrideFieldAccessor = overrideFieldAccessor;
        res.genericInfo = genericInfo;

        return res;
    }

    /**
     * {@inheritDoc}
     *
     * <p>If this reflected object represents a non-final field, and this method is
     * used to enable access, then both <em>{@linkplain #get(Object) read}</em>
     * and <em>{@linkplain #set(Object, Object) write}</em> access to the field
     * are enabled.
     *
     * <p>If this reflected object represents a <em>non-modifiable</em> final field
     * then enabling access only enables read access. Any attempt to {@linkplain
     * #set(Object, Object) set} the field value throws an {@code
     * IllegalAccessException}. The following fields are non-modifiable:
     * <ul>
     * <li>static final fields declared in any class or interface</li>
     * <li>final fields declared in a {@linkplain Class#isRecord() record}</li>
     * <li>final fields declared in a {@linkplain Class#isHidden() hidden class}</li>
     * </ul>
     * <p>If this reflected object represents a non-static final field in a class that
     * is not a record class or hidden class, then enabling access will enable read
     * access. Whether write access is allowed or not is checked when attempting to
     * {@linkplain #set(Object, Object) set} the field value.
     *
     * @throws InaccessibleObjectException {@inheritDoc}
     */
    @Override
    @CallerSensitive
    public void setAccessible(boolean flag) {
        if (flag) checkCanSetAccessible(Reflection.getCallerClass());
        setAccessible0(flag);
    }

    @Override
    void checkCanSetAccessible(Class<?> caller) {
        checkCanSetAccessible(caller, clazz);
    }

    /**
     * Returns the {@code Class} object representing the class or interface
     * that declares the field represented by this {@code Field} object.
     */
    @Override
    public Class<?> getDeclaringClass() {
        return clazz;
    }

    /**
     * Returns the name of the field represented by this {@code Field} object.
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the Java language modifiers for the field represented
     * by this {@code Field} object, as an integer. The {@code Modifier} class should
     * be used to decode the modifiers.
     *
     * @see Modifier
     * @see #accessFlags()
     * @jls 8.3 Field Declarations
     * @jls 9.3 Field (Constant) Declarations
     */
    public int getModifiers() {
        return modifiers;
    }

    /**
     * {@return an unmodifiable set of the {@linkplain AccessFlag
     * access flags} for this field, possibly empty}
     * @see #getModifiers()
     * @jvms 4.5 Fields
     * @since 20
     */
    @Override
    public Set<AccessFlag> accessFlags() {
        return reflectionFactory.parseAccessFlags(getModifiers(), AccessFlag.Location.FIELD, getDeclaringClass());
    }

    /**
     * Returns {@code true} if this field represents an element of
     * an enumerated class; returns {@code false} otherwise.
     *
     * @return {@code true} if and only if this field represents an element of
     * an enumerated class.
     * @since 1.5
     * @jls 8.9.1 Enum Constants
     */
    public boolean isEnumConstant() {
        return (getModifiers() & Modifier.ENUM) != 0;
    }

    /**
     * Returns {@code true} if this field is a synthetic
     * field; returns {@code false} otherwise.
     *
     * @return true if and only if this field is a synthetic
     * field as defined by the Java Language Specification.
     * @since 1.5
     * @see <a
     * href="{@docRoot}/java.base/java/lang/reflect/package-summary.html#LanguageJvmModel">Java
     * programming language and JVM modeling in core reflection</a>
     */
    public boolean isSynthetic() {
        return Modifier.isSynthetic(getModifiers());
    }

    /**
     * Returns a {@code Class} object that identifies the
     * declared type for the field represented by this
     * {@code Field} object.
     *
     * @return a {@code Class} object identifying the declared
     * type of the field represented by this object
     */
    public Class<?> getType() {
        return type;
    }

    /**
     * Returns a {@code Type} object that represents the declared type for
     * the field represented by this {@code Field} object.
     *
     * <p>If the declared type of the field is a parameterized type,
     * the {@code Type} object returned must accurately reflect the
     * actual type arguments used in the source code.
     *
     * <p>If the type of the underlying field is a type variable or a
     * parameterized type, it is created. Otherwise, it is resolved.
     *
     * @return a {@code Type} object that represents the declared type for
     *     the field represented by this {@code Field} object
     * @throws GenericSignatureFormatError if the generic field
     *     signature does not conform to the format specified in
     *     <cite>The Java Virtual Machine Specification</cite>
     * @throws TypeNotPresentException if the generic type
     *     signature of the underlying field refers to a non-existent
     *     class or interface declaration
     * @throws MalformedParameterizedTypeException if the generic
     *     signature of the underlying field refers to a parameterized type
     *     that cannot be instantiated for any reason
     * @since 1.5
     */
    public Type getGenericType() {
        if (getGenericSignature() != null)
            return getGenericInfo().getGenericType();
        else
            return getType();
    }


    /**
     * Compares this {@code Field} against the specified object.  Returns
     * true if the objects are the same.  Two {@code Field} objects are the same if
     * they were declared by the same class and have the same name
     * and type.
     */
    public boolean equals(Object obj) {
        if (obj instanceof Field other) {
            return (getDeclaringClass() == other.getDeclaringClass())
                && (getName() == other.getName())
                && (getType() == other.getType());
        }
        return false;
    }

    /**
     * Returns a hashcode for this {@code Field}.  This is computed as the
     * exclusive-or of the hashcodes for the underlying field's
     * declaring class name and its name.
     */
    public int hashCode() {
        return getDeclaringClass().getName().hashCode() ^ getName().hashCode();
    }

    /**
     * Returns a string describing this {@code Field}.  The format is
     * the access modifiers for the field, if any, followed
     * by the field type, followed by a space, followed by
     * the fully-qualified name of the class declaring the field,
     * followed by a period, followed by the name of the field.
     * For example:
     * <pre>
     *    public static final int java.lang.Thread.MIN_PRIORITY
     *    private int java.io.FileDescriptor.fd
     * </pre>
     *
     * <p>The modifiers are placed in canonical order as specified by
     * "The Java Language Specification".  This is {@code public},
     * {@code protected} or {@code private} first, and then other
     * modifiers in the following order: {@code static}, {@code final},
     * {@code transient}, {@code volatile}.
     *
     * @return a string describing this {@code Field}
     * @jls 8.3.1 Field Modifiers
     */
    public String toString() {
        int mod = getModifiers() & Modifier.fieldModifiers();
        return (((mod == 0) ? "" : (Modifier.toString(mod) + " "))
            + getType().getTypeName() + " "
            + getDeclaringClass().getTypeName() + "."
            + getName());
    }

    @Override
    String toShortString() {
        return "field " + getDeclaringClass().getTypeName() + "." + getName();
    }

    /**
     * Returns a string describing this {@code Field}, including
     * its generic type.  The format is the access modifiers for the
     * field, if any, followed by the generic field type, followed by
     * a space, followed by the fully-qualified name of the class
     * declaring the field, followed by a period, followed by the name
     * of the field.
     *
     * <p>The modifiers are placed in canonical order as specified by
     * "The Java Language Specification".  This is {@code public},
     * {@code protected} or {@code private} first, and then other
     * modifiers in the following order: {@code static}, {@code final},
     * {@code transient}, {@code volatile}.
     *
     * @return a string describing this {@code Field}, including
     * its generic type
     *
     * @since 1.5
     * @jls 8.3.1 Field Modifiers
     */
    public String toGenericString() {
        int mod = getModifiers() & Modifier.fieldModifiers();
        Type fieldType = getGenericType();
        return (((mod == 0) ? "" : (Modifier.toString(mod) + " "))
            + fieldType.getTypeName() + " "
            + getDeclaringClass().getTypeName() + "."
            + getName());
    }

    /**
     * Returns the value of the field represented by this {@code Field}, on
     * the specified object. The value is automatically wrapped in an
     * object if it has a primitive type.
     *
     * <p>The underlying field's value is obtained as follows:
     *
     * <p>If the underlying field is a static field, the {@code obj} argument
     * is ignored; it may be null.
     *
     * <p>Otherwise, the underlying field is an instance field.  If the
     * specified {@code obj} argument is null, the method throws a
     * {@code NullPointerException}. If the specified object is not an
     * instance of the class or interface declaring the underlying
     * field, the method throws an {@code IllegalArgumentException}.
     *
     * <p>If this {@code Field} object is enforcing Java language access control, and
     * the underlying field is inaccessible, the method throws an
     * {@code IllegalAccessException}.
     * If the underlying field is static, the class that declared the
     * field is initialized if it has not already been initialized.
     *
     * <p>Otherwise, the value is retrieved from the underlying instance
     * or static field.  If the field has a primitive type, the value
     * is wrapped in an object before being returned, otherwise it is
     * returned as is.
     *
     * <p>If the field is hidden in the type of {@code obj},
     * the field's value is obtained according to the preceding rules.
     *
     * @param obj object from which the represented field's value is
     * to be extracted
     * @return the value of the represented field in object
     * {@code obj}; primitive values are wrapped in an appropriate
     * object before being returned
     *
     * @throws    IllegalAccessException    if this {@code Field} object
     *              is enforcing Java language access control and the underlying
     *              field is inaccessible.
     * @throws    IllegalArgumentException  if the specified object is not an
     *              instance of the class or interface declaring the underlying
     *              field (or a subclass or implementor thereof).
     * @throws    NullPointerException      if the specified object is null
     *              and the field is an instance field.
     * @throws    ExceptionInInitializerError if the initialization provoked
     *              by this method fails.
     */
    @CallerSensitive
    @ForceInline // to ensure Reflection.getCallerClass optimization
    public Object get(Object obj)
        throws IllegalArgumentException, IllegalAccessException
    {
        if (!override) {
            Class<?> caller = Reflection.getCallerClass();
            checkAccess(caller, obj);
            return getFieldAccessor().get(obj);
        } else {
            return getOverrideFieldAccessor().get(obj);
        }
    }

    /**
     * Gets the value of a static or instance {@code boolean} field.
     *
     * @param obj the object to extract the {@code boolean} value
     * from
     * @return the value of the {@code boolean} field
     *
     * @throws    IllegalAccessException    if this {@code Field} object
     *              is enforcing Java language access control and the underlying
     *              field is inaccessible.
     * @throws    IllegalArgumentException  if the specified object is not
     *              an instance of the class or interface declaring the
     *              underlying field (or a subclass or implementor
     *              thereof), or if the field value cannot be
     *              converted to the type {@code boolean} by a
     *              widening conversion.
     * @throws    NullPointerException      if the specified object is null
     *              and the field is an instance field.
     * @throws    ExceptionInInitializerError if the initialization provoked
     *              by this method fails.
     * @see       Field#get
     */
    @CallerSensitive
    @ForceInline // to ensure Reflection.getCallerClass optimization
    public boolean getBoolean(Object obj)
        throws IllegalArgumentException, IllegalAccessException
    {
        if (!override) {
            Class<?> caller = Reflection.getCallerClass();
            checkAccess(caller, obj);
            return getFieldAccessor().getBoolean(obj);
        } else {
            return getOverrideFieldAccessor().getBoolean(obj);
        }
    }

    /**
     * Gets the value of a static or instance {@code byte} field.
     *
     * @param obj the object to extract the {@code byte} value
     * from
     * @return the value of the {@code byte} field
     *
     * @throws    IllegalAccessException    if this {@code Field} object
     *              is enforcing Java language access control and the underlying
     *              field is inaccessible.
     * @throws    IllegalArgumentException  if the specified object is not
     *              an instance of the class or interface declaring the
     *              underlying field (or a subclass or implementor
     *              thereof), or if the field value cannot be
     *              converted to the type {@code byte} by a
     *              widening conversion.
     * @throws    NullPointerException      if the specified object is null
     *              and the field is an instance field.
     * @throws    ExceptionInInitializerError if the initialization provoked
     *              by this method fails.
     * @see       Field#get
     */
    @CallerSensitive
    @ForceInline // to ensure Reflection.getCallerClass optimization
    public byte getByte(Object obj)
        throws IllegalArgumentException, IllegalAccessException
    {
        if (!override) {
            Class<?> caller = Reflection.getCallerClass();
            checkAccess(caller, obj);
            return getFieldAccessor().getByte(obj);
        } else {
            return getOverrideFieldAccessor().getByte(obj);
        }
    }

    /**
     * Gets the value of a static or instance field of type
     * {@code char} or of another primitive type convertible to
     * type {@code char} via a widening conversion.
     *
     * @param obj the object to extract the {@code char} value
     * from
     * @return the value of the field converted to type {@code char}
     *
     * @throws    IllegalAccessException    if this {@code Field} object
     *              is enforcing Java language access control and the underlying
     *              field is inaccessible.
     * @throws    IllegalArgumentException  if the specified object is not
     *              an instance of the class or interface declaring the
     *              underlying field (or a subclass or implementor
     *              thereof), or if the field value cannot be
     *              converted to the type {@code char} by a
     *              widening conversion.
     * @throws    NullPointerException      if the specified object is null
     *              and the field is an instance field.
     * @throws    ExceptionInInitializerError if the initialization provoked
     *              by this method fails.
     * @see Field#get
     */
    @CallerSensitive
    @ForceInline // to ensure Reflection.getCallerClass optimization
    public char getChar(Object obj)
        throws IllegalArgumentException, IllegalAccessException
    {
        if (!override) {
            Class<?> caller = Reflection.getCallerClass();
            checkAccess(caller, obj);
            return getFieldAccessor().getChar(obj);
        } else {
            return getOverrideFieldAccessor().getChar(obj);
        }
    }

    /**
     * Gets the value of a static or instance field of type
     * {@code short} or of another primitive type convertible to
     * type {@code short} via a widening conversion.
     *
     * @param obj the object to extract the {@code short} value
     * from
     * @return the value of the field converted to type {@code short}
     *
     * @throws    IllegalAccessException    if this {@code Field} object
     *              is enforcing Java language access control and the underlying
     *              field is inaccessible.
     * @throws    IllegalArgumentException  if the specified object is not
     *              an instance of the class or interface declaring the
     *              underlying field (or a subclass or implementor
     *              thereof), or if the field value cannot be
     *              converted to the type {@code short} by a
     *              widening conversion.
     * @throws    NullPointerException      if the specified object is null
     *              and the field is an instance field.
     * @throws    ExceptionInInitializerError if the initialization provoked
     *              by this method fails.
     * @see       Field#get
     */
    @CallerSensitive
    @ForceInline // to ensure Reflection.getCallerClass optimization
    public short getShort(Object obj)
        throws IllegalArgumentException, IllegalAccessException
    {
        if (!override) {
            Class<?> caller = Reflection.getCallerClass();
            checkAccess(caller, obj);
            return getFieldAccessor().getShort(obj);
        } else {
            return getOverrideFieldAccessor().getShort(obj);
        }
    }

    /**
     * Gets the value of a static or instance field of type
     * {@code int} or of another primitive type convertible to
     * type {@code int} via a widening conversion.
     *
     * @param obj the object to extract the {@code int} value
     * from
     * @return the value of the field converted to type {@code int}
     *
     * @throws    IllegalAccessException    if this {@code Field} object
     *              is enforcing Java language access control and the underlying
     *              field is inaccessible.
     * @throws    IllegalArgumentException  if the specified object is not
     *              an instance of the class or interface declaring the
     *              underlying field (or a subclass or implementor
     *              thereof), or if the field value cannot be
     *              converted to the type {@code int} by a
     *              widening conversion.
     * @throws    NullPointerException      if the specified object is null
     *              and the field is an instance field.
     * @throws    ExceptionInInitializerError if the initialization provoked
     *              by this method fails.
     * @see       Field#get
     */
    @CallerSensitive
    @ForceInline // to ensure Reflection.getCallerClass optimization
    public int getInt(Object obj)
        throws IllegalArgumentException, IllegalAccessException
    {
        if (!override) {
            Class<?> caller = Reflection.getCallerClass();
            checkAccess(caller, obj);
            return getFieldAccessor().getInt(obj);
        } else {
            return getOverrideFieldAccessor().getInt(obj);
        }
    }

    /**
     * Gets the value of a static or instance field of type
     * {@code long} or of another primitive type convertible to
     * type {@code long} via a widening conversion.
     *
     * @param obj the object to extract the {@code long} value
     * from
     * @return the value of the field converted to type {@code long}
     *
     * @throws    IllegalAccessException    if this {@code Field} object
     *              is enforcing Java language access control and the underlying
     *              field is inaccessible.
     * @throws    IllegalArgumentException  if the specified object is not
     *              an instance of the class or interface declaring the
     *              underlying field (or a subclass or implementor
     *              thereof), or if the field value cannot be
     *              converted to the type {@code long} by a
     *              widening conversion.
     * @throws    NullPointerException      if the specified object is null
     *              and the field is an instance field.
     * @throws    ExceptionInInitializerError if the initialization provoked
     *              by this method fails.
     * @see       Field#get
     */
    @CallerSensitive
    @ForceInline // to ensure Reflection.getCallerClass optimization
    public long getLong(Object obj)
        throws IllegalArgumentException, IllegalAccessException
    {
        if (!override) {
            Class<?> caller = Reflection.getCallerClass();
            checkAccess(caller, obj);
            return getFieldAccessor().getLong(obj);
        } else {
            return getOverrideFieldAccessor().getLong(obj);
        }
    }

    /**
     * Gets the value of a static or instance field of type
     * {@code float} or of another primitive type convertible to
     * type {@code float} via a widening conversion.
     *
     * @param obj the object to extract the {@code float} value
     * from
     * @return the value of the field converted to type {@code float}
     *
     * @throws    IllegalAccessException    if this {@code Field} object
     *              is enforcing Java language access control and the underlying
     *              field is inaccessible.
     * @throws    IllegalArgumentException  if the specified object is not
     *              an instance of the class or interface declaring the
     *              underlying field (or a subclass or implementor
     *              thereof), or if the field value cannot be
     *              converted to the type {@code float} by a
     *              widening conversion.
     * @throws    NullPointerException      if the specified object is null
     *              and the field is an instance field.
     * @throws    ExceptionInInitializerError if the initialization provoked
     *              by this method fails.
     * @see Field#get
     */
    @CallerSensitive
    @ForceInline // to ensure Reflection.getCallerClass optimization
    public float getFloat(Object obj)
        throws IllegalArgumentException, IllegalAccessException
    {
        if (!override) {
            Class<?> caller = Reflection.getCallerClass();
            checkAccess(caller, obj);
            return getFieldAccessor().getFloat(obj);
        } else {
            return getOverrideFieldAccessor().getFloat(obj);
        }
    }

    /**
     * Gets the value of a static or instance field of type
     * {@code double} or of another primitive type convertible to
     * type {@code double} via a widening conversion.
     *
     * @param obj the object to extract the {@code double} value
     * from
     * @return the value of the field converted to type {@code double}
     *
     * @throws    IllegalAccessException    if this {@code Field} object
     *              is enforcing Java language access control and the underlying
     *              field is inaccessible.
     * @throws    IllegalArgumentException  if the specified object is not
     *              an instance of the class or interface declaring the
     *              underlying field (or a subclass or implementor
     *              thereof), or if the field value cannot be
     *              converted to the type {@code double} by a
     *              widening conversion.
     * @throws    NullPointerException      if the specified object is null
     *              and the field is an instance field.
     * @throws    ExceptionInInitializerError if the initialization provoked
     *              by this method fails.
     * @see       Field#get
     */
    @CallerSensitive
    @ForceInline // to ensure Reflection.getCallerClass optimization
    public double getDouble(Object obj)
        throws IllegalArgumentException, IllegalAccessException
    {
        if (!override) {
            Class<?> caller = Reflection.getCallerClass();
            checkAccess(caller, obj);
            return getFieldAccessor().getDouble(obj);
        } else {
            return getOverrideFieldAccessor().getDouble(obj);
        }
    }

    /**
     * Sets the field represented by this {@code Field} object on the
     * specified object argument to the specified new value. The new
     * value is automatically unwrapped if the underlying field has a
     * primitive type.
     *
     * <p>The operation proceeds as follows:
     *
     * <p>If the underlying field is static, the {@code obj} argument is
     * ignored; it may be null.
     *
     * <p>Otherwise the underlying field is an instance field.  If the
     * specified object argument is null, the method throws a
     * {@code NullPointerException}.  If the specified object argument is not
     * an instance of the class or interface declaring the underlying
     * field, the method throws an {@code IllegalArgumentException}.
     *
     * <p>If this {@code Field} object is enforcing Java language access control, and
     * the underlying field is inaccessible, the method throws an
     * {@code IllegalAccessException}.
     *
     * <p>If the underlying field is final, this {@code Field} object has <em>write</em>
     * access if and only if all of the following conditions are true, where {@code D} is
     * the field's {@linkplain #getDeclaringClass() declaring class}:
     *
     * <ul>
     * <li>{@link #setAccessible(boolean) setAccessible(true)} has succeeded for this
     *     {@code Field} object.</li>
     * <li><a href="doc-files/MutationMethods.html">final field mutation is enabled</a>
     *     for the caller's module.</li>
     * <li> At least one of the following conditions holds:
     *     <ol type="a">
     *     <li> {@code D} and the caller class are in the same module.</li>
     *     <li> The field is {@code public} and {@code D} is {@code public} in a package
     *     that the module containing {@code D} exports to at least the caller's module.</li>
     *     <li> {@code D} is in a package that is {@linkplain Module#isOpen(String, Module)
     *     open} to the caller's module.</li>
     *     </ol>
     * </li>
     * <li>{@code D} is not a {@linkplain Class#isRecord() record class}.</li>
     * <li>{@code D} is not a {@linkplain Class#isHidden() hidden class}.</li>
     * <li>The field is non-static.</li>
     * </ul>
     *
     * <p>If any of the above conditions is not met, this method throws an
     * {@code IllegalAccessException}.
     *
     * <p>These conditions are more restrictive than the conditions specified by {@link
     * #setAccessible(boolean)} to suppress access checks. In particular, updating a
     * module to export or open a package cannot be used to allow <em>write</em> access
     * to final fields with the {@code set} methods defined by {@code Field}.
     * Condition (b) is not met if the module containing {@code D} has been updated with
     * {@linkplain Module#addExports(String, Module) addExports} to export the package to
     * the caller's module. Condition (c) is not met if the module containing {@code D}
     * has been updated with {@linkplain Module#addOpens(String, Module) addOpens} to open
     * the package to the caller's module.
     *
     * <p>This method may be called by <a href="{@docRoot}/../specs/jni/index.html">
     * JNI code</a> with no caller class on the stack. In that case, and when the
     * underlying field is final, this {@code Field} object has <em>write</em> access
     * if and only if all of the following conditions are true, where {@code D} is the
     * field's {@linkplain #getDeclaringClass() declaring class}:
     *
     * <ul>
     * <li>{@code setAccessible(true)} has succeeded for this {@code Field} object.</li>
     * <li>final field mutation is enabled for the unnamed module.</li>
     * <li>The field is {@code public} and {@code D} is {@code public} in a package that
     *     is {@linkplain Module#isExported(String) exported} to all modules.</li>
     * <li>{@code D} is not a {@linkplain Class#isRecord() record class}.</li>
     * <li>{@code D} is not a {@linkplain Class#isHidden() hidden class}.</li>
     * <li>The field is non-static.</li>
     * </ul>
     *
     * <p>If any of the above conditions is not met, this method throws an
     * {@code IllegalAccessException}.
     *
     * <p> Setting a final field in this way
     * is meaningful only during deserialization or reconstruction of
     * instances of classes with blank final fields, before they are
     * made available for access by other parts of a program. Use in
     * any other context may have unpredictable effects, including cases
     * in which other parts of a program continue to use the original
     * value of this field.
     *
     * <p>If the underlying field is of a primitive type, an unwrapping
     * conversion is attempted to convert the new value to a value of
     * a primitive type.  If this attempt fails, the method throws an
     * {@code IllegalArgumentException}.
     *
     * <p>If, after possible unwrapping, the new value cannot be
     * converted to the type of the underlying field by an identity or
     * widening conversion, the method throws an
     * {@code IllegalArgumentException}.
     *
     * <p>If the underlying field is static, the class that declared the
     * field is initialized if it has not already been initialized.
     *
     * <p>The field is set to the possibly unwrapped and widened new value.
     *
     * <p>If the field is hidden in the type of {@code obj},
     * the field's value is set according to the preceding rules.
     *
     * @param obj the object whose field should be modified
     * @param value the new value for the field of {@code obj}
     * being modified
     *
     * @throws    IllegalAccessException    if this {@code Field} object
     *              is enforcing Java language access control and the underlying
     *              field is inaccessible or final;
     *              or if this {@code Field} object has no write access.
     * @throws    IllegalArgumentException  if the specified object is not an
     *              instance of the class or interface declaring the underlying
     *              field (or a subclass or implementor thereof),
     *              or if an unwrapping conversion fails.
     * @throws    NullPointerException      if the specified object is null
     *              and the field is an instance field.
     * @throws    ExceptionInInitializerError if the initialization provoked
     *              by this method fails.
     *
     * @see <a href="doc-files/MutationMethods.html">Mutation methods</a>
     */
    @CallerSensitive
    @ForceInline // to ensure Reflection.getCallerClass optimization
    public void set(Object obj, Object value)
        throws IllegalArgumentException, IllegalAccessException
    {
        if (!override) {
            Class<?> caller = Reflection.getCallerClass();
            checkAccess(caller, obj);
            getFieldAccessor().set(obj, value);
            return;
        }

        FieldAccessor fa = getOverrideFieldAccessor();
        if (!Modifier.isFinal(modifiers)) {
            fa.set(obj, value);
        } else {
            setFinal(Reflection.getCallerClass(), obj, () -> fa.set(obj, value));
        }
    }

    /**
     * Sets the value of a field as a {@code boolean} on the specified object.
     * This method is equivalent to
     * {@code set(obj, zObj)},
     * where {@code zObj} is a {@code Boolean} object and
     * {@code zObj.booleanValue() == z}.
     *
     * @param obj the object whose field should be modified
     * @param z   the new value for the field of {@code obj}
     * being modified
     *
     * @throws    IllegalAccessException    if this {@code Field} object
     *              is enforcing Java language access control and the underlying
     *              field is either inaccessible or final;
     *              or if this {@code Field} object has no write access.
     * @throws    IllegalArgumentException  if the specified object is not an
     *              instance of the class or interface declaring the underlying
     *              field (or a subclass or implementor thereof),
     *              or if an unwrapping conversion fails.
     * @throws    NullPointerException      if the specified object is null
     *              and the field is an instance field.
     * @throws    ExceptionInInitializerError if the initialization provoked
     *              by this method fails.
     * @see       Field#set
     */
    @CallerSensitive
    @ForceInline // to ensure Reflection.getCallerClass optimization
    public void setBoolean(Object obj, boolean z)
        throws IllegalArgumentException, IllegalAccessException
    {
        if (!override) {
            Class<?> caller = Reflection.getCallerClass();
            checkAccess(caller, obj);
            getFieldAccessor().setBoolean(obj, z);
            return;
        }

        FieldAccessor fa = getOverrideFieldAccessor();
        if (!Modifier.isFinal(modifiers)) {
            fa.setBoolean(obj, z);
        } else {
            setFinal(Reflection.getCallerClass(), obj, () -> fa.setBoolean(obj, z));
        }
    }

    /**
     * Sets the value of a field as a {@code byte} on the specified object.
     * This method is equivalent to
     * {@code set(obj, bObj)},
     * where {@code bObj} is a {@code Byte} object and
     * {@code bObj.byteValue() == b}.
     *
     * @param obj the object whose field should be modified
     * @param b   the new value for the field of {@code obj}
     * being modified
     *
     * @throws    IllegalAccessException    if this {@code Field} object
     *              is enforcing Java language access control and the underlying
     *              field is either inaccessible or final;
     *              or if this {@code Field} object has no write access.
     * @throws    IllegalArgumentException  if the specified object is not an
     *              instance of the class or interface declaring the underlying
     *              field (or a subclass or implementor thereof),
     *              or if an unwrapping conversion fails.
     * @throws    NullPointerException      if the specified object is null
     *              and the field is an instance field.
     * @throws    ExceptionInInitializerError if the initialization provoked
     *              by this method fails.
     * @see       Field#set
     */
    @CallerSensitive
    @ForceInline // to ensure Reflection.getCallerClass optimization
    public void setByte(Object obj, byte b)
        throws IllegalArgumentException, IllegalAccessException
    {
        if (!override) {
            Class<?> caller = Reflection.getCallerClass();
            checkAccess(caller, obj);
            getFieldAccessor().setByte(obj, b);
            return;
        }

        FieldAccessor fa = getOverrideFieldAccessor();
        if (!Modifier.isFinal(modifiers)) {
            fa.setByte(obj, b);
        } else {
            setFinal(Reflection.getCallerClass(), obj, () -> fa.setByte(obj, b));
        }
    }

    /**
     * Sets the value of a field as a {@code char} on the specified object.
     * This method is equivalent to
     * {@code set(obj, cObj)},
     * where {@code cObj} is a {@code Character} object and
     * {@code cObj.charValue() == c}.
     *
     * @param obj the object whose field should be modified
     * @param c   the new value for the field of {@code obj}
     * being modified
     *
     * @throws    IllegalAccessException    if this {@code Field} object
     *              is enforcing Java language access control and the underlying
     *              field is either inaccessible or final;
     *              or if this {@code Field} object has no write access.
     * @throws    IllegalArgumentException  if the specified object is not an
     *              instance of the class or interface declaring the underlying
     *              field (or a subclass or implementor thereof),
     *              or if an unwrapping conversion fails.
     * @throws    NullPointerException      if the specified object is null
     *              and the field is an instance field.
     * @throws    ExceptionInInitializerError if the initialization provoked
     *              by this method fails.
     * @see       Field#set
     */
    @CallerSensitive
    @ForceInline // to ensure Reflection.getCallerClass optimization
    public void setChar(Object obj, char c)
        throws IllegalArgumentException, IllegalAccessException
    {
        if (!override) {
            Class<?> caller = Reflection.getCallerClass();
            checkAccess(caller, obj);
            getFieldAccessor().setChar(obj, c);
            return;
        }

        FieldAccessor fa = getOverrideFieldAccessor();
        if (!Modifier.isFinal(modifiers)) {
            fa.setChar(obj, c);
        } else {
            setFinal(Reflection.getCallerClass(), obj, () -> fa.setChar(obj, c));
        }
    }

    /**
     * Sets the value of a field as a {@code short} on the specified object.
     * This method is equivalent to
     * {@code set(obj, sObj)},
     * where {@code sObj} is a {@code Short} object and
     * {@code sObj.shortValue() == s}.
     *
     * @param obj the object whose field should be modified
     * @param s   the new value for the field of {@code obj}
     * being modified
     *
     * @throws    IllegalAccessException    if this {@code Field} object
     *              is enforcing Java language access control and the underlying
     *              field is either inaccessible or final;
     *              or if this {@code Field} object has no write access.
     * @throws    IllegalArgumentException  if the specified object is not an
     *              instance of the class or interface declaring the underlying
     *              field (or a subclass or implementor thereof),
     *              or if an unwrapping conversion fails.
     * @throws    NullPointerException      if the specified object is null
     *              and the field is an instance field.
     * @throws    ExceptionInInitializerError if the initialization provoked
     *              by this method fails.
     * @see       Field#set
     */
    @CallerSensitive
    @ForceInline // to ensure Reflection.getCallerClass optimization
    public void setShort(Object obj, short s)
        throws IllegalArgumentException, IllegalAccessException
    {
        if (!override) {
            Class<?> caller = Reflection.getCallerClass();
            checkAccess(caller, obj);
            getFieldAccessor().setShort(obj, s);
            return;
        }

        FieldAccessor fa = getOverrideFieldAccessor();
        if (!Modifier.isFinal(modifiers)) {
            fa.setShort(obj, s);
        } else {
            setFinal(Reflection.getCallerClass(), obj, () -> fa.setShort(obj, s));
        }
    }

    /**
     * Sets the value of a field as an {@code int} on the specified object.
     * This method is equivalent to
     * {@code set(obj, iObj)},
     * where {@code iObj} is an {@code Integer} object and
     * {@code iObj.intValue() == i}.
     *
     * @param obj the object whose field should be modified
     * @param i   the new value for the field of {@code obj}
     * being modified
     *
     * @throws    IllegalAccessException    if this {@code Field} object
     *              is enforcing Java language access control and the underlying
     *              field is either inaccessible or final;
     *              or if this {@code Field} object has no write access.
     * @throws    IllegalArgumentException  if the specified object is not an
     *              instance of the class or interface declaring the underlying
     *              field (or a subclass or implementor thereof),
     *              or if an unwrapping conversion fails.
     * @throws    NullPointerException      if the specified object is null
     *              and the field is an instance field.
     * @throws    ExceptionInInitializerError if the initialization provoked
     *              by this method fails.
     * @see       Field#set
     */
    @CallerSensitive
    @ForceInline // to ensure Reflection.getCallerClass optimization
    public void setInt(Object obj, int i)
        throws IllegalArgumentException, IllegalAccessException
    {
        if (!override) {
            Class<?> caller = Reflection.getCallerClass();
            checkAccess(caller, obj);
            getFieldAccessor().setInt(obj, i);
            return;
        }

        FieldAccessor fa = getOverrideFieldAccessor();
        if (!Modifier.isFinal(modifiers)) {
            fa.setInt(obj, i);
        } else {
            setFinal(Reflection.getCallerClass(), obj, () -> fa.setInt(obj, i));
        }
    }

    /**
     * Sets the value of a field as a {@code long} on the specified object.
     * This method is equivalent to
     * {@code set(obj, lObj)},
     * where {@code lObj} is a {@code Long} object and
     * {@code lObj.longValue() == l}.
     *
     * @param obj the object whose field should be modified
     * @param l   the new value for the field of {@code obj}
     * being modified
     *
     * @throws    IllegalAccessException    if this {@code Field} object
     *              is enforcing Java language access control and the underlying
     *              field is either inaccessible or final;
     *              or if this {@code Field} object has no write access.
     * @throws    IllegalArgumentException  if the specified object is not an
     *              instance of the class or interface declaring the underlying
     *              field (or a subclass or implementor thereof),
     *              or if an unwrapping conversion fails.
     * @throws    NullPointerException      if the specified object is null
     *              and the field is an instance field.
     * @throws    ExceptionInInitializerError if the initialization provoked
     *              by this method fails.
     * @see       Field#set
     */
    @CallerSensitive
    @ForceInline // to ensure Reflection.getCallerClass optimization
    public void setLong(Object obj, long l)
        throws IllegalArgumentException, IllegalAccessException
    {
        if (!override) {
            Class<?> caller = Reflection.getCallerClass();
            checkAccess(caller, obj);
            getFieldAccessor().setLong(obj, l);
            return;
        }

        FieldAccessor fa = getOverrideFieldAccessor();
        if (!Modifier.isFinal(modifiers)) {
            fa.setLong(obj, l);
        } else {
            setFinal(Reflection.getCallerClass(), obj, () -> fa.setLong(obj, l));
        }
    }

    /**
     * Sets the value of a field as a {@code float} on the specified object.
     * This method is equivalent to
     * {@code set(obj, fObj)},
     * where {@code fObj} is a {@code Float} object and
     * {@code fObj.floatValue() == f}.
     *
     * @param obj the object whose field should be modified
     * @param f   the new value for the field of {@code obj}
     * being modified
     *
     * @throws    IllegalAccessException    if this {@code Field} object
     *              is enforcing Java language access control and the underlying
     *              field is either inaccessible or final;
     *              or if this {@code Field} object has no write access.
     * @throws    IllegalArgumentException  if the specified object is not an
     *              instance of the class or interface declaring the underlying
     *              field (or a subclass or implementor thereof),
     *              or if an unwrapping conversion fails.
     * @throws    NullPointerException      if the specified object is null
     *              and the field is an instance field.
     * @throws    ExceptionInInitializerError if the initialization provoked
     *              by this method fails.
     * @see       Field#set
     */
    @CallerSensitive
    @ForceInline // to ensure Reflection.getCallerClass optimization
    public void setFloat(Object obj, float f)
        throws IllegalArgumentException, IllegalAccessException
    {
        if (!override) {
            Class<?> caller = Reflection.getCallerClass();
            checkAccess(caller, obj);
            getFieldAccessor().setFloat(obj, f);
            return;
        }

        FieldAccessor fa = getOverrideFieldAccessor();
        if (!Modifier.isFinal(modifiers)) {
            fa.setFloat(obj, f);
        } else {
            setFinal(Reflection.getCallerClass(), obj, () -> fa.setFloat(obj, f));
        }
    }

    /**
     * Sets the value of a field as a {@code double} on the specified object.
     * This method is equivalent to
     * {@code set(obj, dObj)},
     * where {@code dObj} is a {@code Double} object and
     * {@code dObj.doubleValue() == d}.
     *
     * @param obj the object whose field should be modified
     * @param d   the new value for the field of {@code obj}
     * being modified
     *
     * @throws    IllegalAccessException    if this {@code Field} object
     *              is enforcing Java language access control and the underlying
     *              field is either inaccessible or final;
     *              or if this {@code Field} object has no write access.
     * @throws    IllegalArgumentException  if the specified object is not an
     *              instance of the class or interface declaring the underlying
     *              field (or a subclass or implementor thereof),
     *              or if an unwrapping conversion fails.
     * @throws    NullPointerException      if the specified object is null
     *              and the field is an instance field.
     * @throws    ExceptionInInitializerError if the initialization provoked
     *              by this method fails.
     * @see       Field#set
     */
    @CallerSensitive
    @ForceInline // to ensure Reflection.getCallerClass optimization
    public void setDouble(Object obj, double d)
        throws IllegalArgumentException, IllegalAccessException
    {
        if (!override) {
            Class<?> caller = Reflection.getCallerClass();
            checkAccess(caller, obj);
            getFieldAccessor().setDouble(obj, d);
            return;
        }

        FieldAccessor fa = getOverrideFieldAccessor();
        if (!Modifier.isFinal(modifiers)) {
            fa.setDouble(obj, d);
        } else {
            setFinal(Reflection.getCallerClass(), obj, () -> fa.setDouble(obj, d));
        }
    }

    // check access to field
    private void checkAccess(Class<?> caller, Object obj)
        throws IllegalAccessException
    {
        checkAccess(caller, clazz,
                    Modifier.isStatic(modifiers) ? null : obj.getClass(),
                    modifiers);
    }

    private FieldAccessor getFieldAccessor() {
        FieldAccessor a = fieldAccessor;
        return (a != null) ? a : acquireFieldAccessor();
    }

    private FieldAccessor getOverrideFieldAccessor() {
        FieldAccessor a = overrideFieldAccessor;
        return (a != null) ? a : acquireOverrideFieldAccessor();
    }

    // NOTE that there is no synchronization used here. It is correct
    // (though not efficient) to generate more than one FieldAccessor
    // for a given Field. However, avoiding synchronization will
    // probably make the implementation more scalable.
    private FieldAccessor acquireFieldAccessor() {
        // First check to see if one has been created yet, and take it
        // if so
        Field root = this.root;
        FieldAccessor tmp = root == null ? null : root.fieldAccessor;
        if (tmp != null) {
            fieldAccessor = tmp;
        } else {
            // Otherwise fabricate one and propagate it up to the root
            tmp = reflectionFactory.newFieldAccessor(this, false);
            setFieldAccessor(tmp);
        }
        return tmp;
    }

    private FieldAccessor acquireOverrideFieldAccessor() {
        // First check to see if one has been created yet, and take it
        // if so
        Field root = this.root;
        FieldAccessor tmp = root == null ? null : root.overrideFieldAccessor;
        if (tmp != null) {
            overrideFieldAccessor = tmp;
        } else {
            // Otherwise fabricate one and propagate it up to the root
            tmp = reflectionFactory.newFieldAccessor(this, true);
            setOverrideFieldAccessor(tmp);
        }
        return tmp;
    }

    // Sets the fieldAccessor for this Field object and
    // (recursively) its root
    private void setFieldAccessor(FieldAccessor accessor) {
        fieldAccessor = accessor;
        // Propagate up
        Field root = this.root;
        if (root != null) {
            root.setFieldAccessor(accessor);
        }
    }

    // Sets the overrideFieldAccessor for this Field object and
    // (recursively) its root
    private void setOverrideFieldAccessor(FieldAccessor accessor) {
        overrideFieldAccessor = accessor;
        // Propagate up
        Field root = this.root;
        if (root != null) {
            root.setOverrideFieldAccessor(accessor);
        }
    }

    @Override
    /* package-private */ Field getRoot() {
        return root;
    }

    /* package-private */ boolean isTrustedFinal() {
        return trustedFinal;
    }

    /**
     * {@inheritDoc}
     *
     * @throws NullPointerException {@inheritDoc}
     * @since 1.5
     */
    @Override
    public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
        Objects.requireNonNull(annotationClass);
        return annotationClass.cast(declaredAnnotations().get(annotationClass));
    }

    /**
     * {@inheritDoc}
     *
     * @throws NullPointerException {@inheritDoc}
     * @since 1.8
     */
    @Override
    public <T extends Annotation> T[] getAnnotationsByType(Class<T> annotationClass) {
        Objects.requireNonNull(annotationClass);

        return AnnotationSupport.getDirectlyAndIndirectlyPresent(declaredAnnotations(), annotationClass);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Annotation[] getDeclaredAnnotations()  {
        return AnnotationParser.toArray(declaredAnnotations());
    }

    private transient volatile Map<Class<? extends Annotation>, Annotation> declaredAnnotations;

    private Map<Class<? extends Annotation>, Annotation> declaredAnnotations() {
        Map<Class<? extends Annotation>, Annotation> declAnnos;
        if ((declAnnos = declaredAnnotations) == null) {
            synchronized (this) {
                if ((declAnnos = declaredAnnotations) == null) {
                    Field root = this.root;
                    if (root != null) {
                        declAnnos = root.declaredAnnotations();
                    } else {
                        declAnnos = AnnotationParser.parseAnnotations(
                                annotations,
                                SharedSecrets.getJavaLangAccess()
                                        .getConstantPool(getDeclaringClass()),
                                getDeclaringClass());
                    }
                    declaredAnnotations = declAnnos;
                }
            }
        }
        return declAnnos;
    }

    private native byte[] getTypeAnnotationBytes0();

    /**
     * Returns an AnnotatedType object that represents the use of a type to specify
     * the declared type of the field represented by this Field.
     * @return an object representing the declared type of the field
     * represented by this Field
     *
     * @since 1.8
     */
    public AnnotatedType getAnnotatedType() {
        return TypeAnnotationParser.buildAnnotatedType(getTypeAnnotationBytes0(),
                                                       SharedSecrets.getJavaLangAccess().
                                                           getConstantPool(getDeclaringClass()),
                                                       this,
                                                       getDeclaringClass(),
                                                       getGenericType(),
                                                       TypeAnnotation.TypeAnnotationTarget.FIELD);
    }

    /**
     * A function that sets a field to a value.
     */
    @FunctionalInterface
    private interface FieldSetter {
        void setFieldValue() throws IllegalAccessException;
    }

    /**
     * Attempts to set a final field.
     */
    private void setFinal(Class<?> caller, Object obj, FieldSetter setter) throws IllegalAccessException {
        if (obj != null && isFinalInstanceInNormalClass()) {
            preSetFinal(caller, false);
            setter.setFieldValue();
            postSetFinal(caller, false);
        } else {
            // throws IllegalAccessException if static, or field in record or hidden class
            setter.setFieldValue();
        }
    }

    /**
     * Return true if this field is a final instance field in a normal class (not a
     * record class or hidden class),
     */
    private boolean isFinalInstanceInNormalClass() {
        return Modifier.isFinal(modifiers)
                && !Modifier.isStatic(modifiers)
                && !clazz.isRecord()
                && !clazz.isHidden();
    }

    /**
     * Check that the caller is allowed to unreflect for mutation a final instance field
     * in a normal class.
     * @throws IllegalAccessException if not allowed
     */
    void checkAllowedToUnreflectFinalSetter(Class<?> caller) throws IllegalAccessException {
        Objects.requireNonNull(caller);
        preSetFinal(caller, true);
        postSetFinal(caller, true);
    }

    /**
     * Invoke before attempting to mutate, or unreflect for mutation, a final instance
     * field in a normal class.
     * @throws IllegalAccessException if not allowed
     */
    private void preSetFinal(Class<?> caller, boolean unreflect) throws IllegalAccessException {
        assert isFinalInstanceInNormalClass();

        if (caller != null) {
            // check if declaring class in package that is open to caller, or public field
            // and declaring class is public in package exported to caller
            if (!isFinalDeeplyAccessible(caller)) {
                throw new IllegalAccessException(notAccessibleToCallerMessage(caller, unreflect));
            }
        } else {
            // no java caller, only allowed if field is public in exported package
            if (!Reflection.verifyPublicMemberAccess(clazz, modifiers)) {
                throw new IllegalAccessException(notAccessibleToNoCallerMessage(unreflect));
            }
        }

        // check if field mutation is enabled for caller module or illegal final field
        // mutation is allowed
        var mode = ModuleBootstrap.illegalFinalFieldMutation();
        if (mode == ModuleBootstrap.IllegalFinalFieldMutation.DENY
                && !Modules.isFinalMutationEnabled(moduleToCheck(caller))) {
            throw new IllegalAccessException(callerNotAllowedToMutateMessage(caller, unreflect));
        }
    }

    /**
     * Invoke after mutating a final instance field, or when unreflecting a final instance
     * field for mutation, to print a warning and record a JFR event.
     */
    private void postSetFinal(Class<?> caller, boolean unreflect) {
        assert isFinalInstanceInNormalClass();

        var mode = ModuleBootstrap.illegalFinalFieldMutation();
        if (mode == ModuleBootstrap.IllegalFinalFieldMutation.WARN) {
            // first mutation prints warning
            Module moduleToCheck = moduleToCheck(caller);
            if (Modules.tryEnableFinalMutation(moduleToCheck)) {
                String warningMsg = finalFieldMutationWarning(caller, unreflect);
                String targetModule = (caller != null && moduleToCheck.isNamed())
                        ? moduleToCheck.getName()
                        : "ALL-UNNAMED";
                VM.initialErr().printf("""
                        WARNING: %s
                        WARNING: Use --enable-final-field-mutation=%s to avoid a warning
                        WARNING: Mutating final fields will be blocked in a future release unless final field mutation is enabled
                        """, warningMsg, targetModule);
            }
        } else if (mode == ModuleBootstrap.IllegalFinalFieldMutation.DEBUG) {
            // print warning and stack trace
            var sb = new StringBuilder(finalFieldMutationWarning(caller, unreflect));
            StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE)
                    .forEach(sf -> {
                        sb.append(System.lineSeparator()).append("\tat " + sf);
                    });
            VM.initialErr().println(sb);
        }

        // record JFR event
        FinalFieldMutationEvent.offer(getDeclaringClass(), getName());
    }

    /**
     * Returns true if this final field is "deeply accessible" to the caller.
     * The field is deeply accessible if declaring class is in a package that is open
     * to the caller's module, or the field is public in a public class that is exported
     * to the caller's module.
     *
     * Updates to the module of the declaring class at runtime with {@code Module.addExports}
     * or {@code Module.addOpens} have no impact on the result of this method.
     */
    private boolean isFinalDeeplyAccessible(Class<?> caller) {
        assert isFinalInstanceInNormalClass();

        // all fields in unnamed modules are deeply accessible
        Module declaringModule = clazz.getModule();
        if (!declaringModule.isNamed()) return true;

        // all fields in the caller's module are deeply accessible
        Module callerModule = caller.getModule();
        if (callerModule == declaringModule) return true;

        // public field, public class, package exported to caller's module
        String pn = clazz.getPackageName();
        if (Modifier.isPublic(modifiers)
                && Modifier.isPublic(clazz.getModifiers())
                && Modules.isStaticallyExported(declaringModule, pn, callerModule)) {
            return true;
        }

        // package open to caller's module
        return Modules.isStaticallyOpened(declaringModule, pn, callerModule);
    }

    /**
     * Returns the Module to use for access checks with the given caller.
     */
    private Module moduleToCheck(Class<?> caller) {
        if (caller != null) {
            return caller.getModule();
        } else {
            // no java caller, only allowed if field is public in exported package
            return ClassLoaders.appClassLoader().getUnnamedModule();
        }
    }

    /**
     * Returns the warning message to print when this final field is mutated by
     * the given possibly-null caller.
     */
    private String finalFieldMutationWarning(Class<?> caller, boolean unreflect) {
        assert Modifier.isFinal(modifiers);
        String source;
        if (caller != null) {
            source = caller + " in " + caller.getModule();
            CodeSource cs = caller.getProtectionDomain().getCodeSource();
            if (cs != null) {
                URL url = cs.getLocation();
                if (url != null) {
                    source += " (" + url + ")";
                }
            }
        } else {
            source = "JNI attached thread with no caller frame";
        }
        return String.format("Final field %s in %s has been %s by %s",
                name,
                clazz,
                (unreflect) ? "unreflected for mutation" : "mutated reflectively",
                source);
    }

    /**
     * Returns the message for an IllegalAccessException when a final field cannot be
     * mutated because the declaring class is in a package that is not "deeply accessible"
     * to the caller.
     */
    private String notAccessibleToCallerMessage(Class<?> caller, boolean unreflect) {
        String exportsOrOpens = Modifier.isPublic(modifiers)
                && Modifier.isPublic(clazz.getModifiers()) ? "exports" : "opens";
        return String.format("%s, %s does not explicitly \"%s\" package %s to %s",
                cannotSetFieldMessage(caller, unreflect),
                clazz.getModule(),
                exportsOrOpens,
                clazz.getPackageName(),
                caller.getModule());
    }

    /**
     * Returns the exception message for the IllegalAccessException when this
     * final field cannot be mutated because the caller module is not allowed
     * to mutate final fields.
     */
    private String callerNotAllowedToMutateMessage(Class<?> caller, boolean unreflect) {
        if (caller != null) {
            return String.format("%s, %s is not allowed to mutate final fields",
                    cannotSetFieldMessage(caller, unreflect),
                    caller.getModule());
        } else {
            return notAccessibleToNoCallerMessage(unreflect);
        }
    }

    /**
     * Returns the message for an IllegalAccessException when a field is not
     * accessible to a JNI attached thread.
     */
    private String notAccessibleToNoCallerMessage(boolean unreflect) {
        return cannotSetFieldMessage("JNI attached thread with no caller frame cannot", unreflect);
    }

    /**
     * Returns a message to indicate that the caller cannot set/unreflect this final field.
     */
    private String cannotSetFieldMessage(Class<?> caller, boolean unreflect) {
        return cannotSetFieldMessage(caller + " (in " + caller.getModule() + ") cannot", unreflect);
    }

    /**
     * Returns a message to indicate that a field cannot be set/unreflected.
     */
    private String cannotSetFieldMessage(String prefix, boolean unreflect) {
        if (unreflect) {
            return prefix + " unreflect final field " + clazz.getName() + "." + name
                    + " (in " + clazz.getModule() + ") for mutation";
        } else {
            return prefix + " set final field " + clazz.getName() + "." + name
                    + " (in " + clazz.getModule() + ")";
        }
    }
}
