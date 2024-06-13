/*
 * Copyright (c) 2001, 2024, Oracle and/or its affiliates. All rights reserved.
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

package jdk.internal.reflect;

import java.io.Externalizable;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.io.OptionalDataException;
import java.io.Serializable;
import java.lang.classfile.ClassFile;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.DirectMethodHandleDesc;
import java.lang.constant.DynamicConstantDesc;
import java.lang.constant.MethodHandleDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.security.PrivilegedAction;
import java.util.Properties;
import jdk.internal.access.JavaLangReflectAccess;
import jdk.internal.access.SharedSecrets;
import jdk.internal.misc.VM;
import jdk.internal.vm.annotation.Stable;
import sun.security.action.GetPropertyAction;
import sun.security.util.SecurityConstants;

/** <P> The master factory for all reflective objects, both those in
    java.lang.reflect (Fields, Methods, Constructors) as well as their
    delegates (FieldAccessors, MethodAccessors, ConstructorAccessors).
    </P>

    <P> The methods in this class are extremely unsafe and can cause
    subversion of both the language and the verifier. For this reason,
    they are all instance methods, and access to the constructor of
    this factory is guarded by a security check, in similar style to
    {@link jdk.internal.misc.Unsafe}. </P>
*/

public class ReflectionFactory {

    private static final ReflectionFactory soleInstance = new ReflectionFactory();


    /* Method for static class initializer <clinit>, or null */
    private static volatile Method hasStaticInitializerMethod;

    private final JavaLangReflectAccess langReflectAccess;
    private ReflectionFactory() {
        this.langReflectAccess = SharedSecrets.getJavaLangReflectAccess();
    }

    /**
     * A convenience class for acquiring the capability to instantiate
     * reflective objects.  Use this instead of a raw call to {@link
     * #getReflectionFactory} in order to avoid being limited by the
     * permissions of your callers.
     *
     * <p>An instance of this class can be used as the argument of
     * <code>AccessController.doPrivileged</code>.
     */
    public static final class GetReflectionFactoryAction
        implements PrivilegedAction<ReflectionFactory> {
        public ReflectionFactory run() {
            return getReflectionFactory();
        }
    }

    /**
     * Provides the caller with the capability to instantiate reflective
     * objects.
     *
     * <p> First, if there is a security manager, its
     * <code>checkPermission</code> method is called with a {@link
     * java.lang.RuntimePermission} with target
     * <code>"reflectionFactoryAccess"</code>.  This may result in a
     * security exception.
     *
     * <p> The returned <code>ReflectionFactory</code> object should be
     * carefully guarded by the caller, since it can be used to read and
     * write private data and invoke private methods, as well as to load
     * unverified bytecodes.  It must never be passed to untrusted code.
     *
     * @exception SecurityException if a security manager exists and its
     *             <code>checkPermission</code> method doesn't allow
     *             access to the RuntimePermission "reflectionFactoryAccess".  */
    public static ReflectionFactory getReflectionFactory() {
        @SuppressWarnings("removal")
        SecurityManager security = System.getSecurityManager();
        if (security != null) {
            security.checkPermission(
                SecurityConstants.REFLECTION_FACTORY_ACCESS_PERMISSION);
        }
        return soleInstance;
    }

    //--------------------------------------------------------------------------
    //
    // Routines used by java.lang.reflect
    //
    //

    /*
     * Note: this routine can cause the declaring class for the field
     * be initialized and therefore must not be called until the
     * first get/set of this field.
     * @param field the field
     * @param override true if caller has overridden accessibility
     */
    public FieldAccessor newFieldAccessor(Field field, boolean override) {
        Field root = langReflectAccess.getRoot(field);
        if (root != null) {
            // FieldAccessor will use the root unless the modifiers have
            // been overridden
            if (root.getModifiers() == field.getModifiers() || !override) {
                field = root;
            }
        }
        boolean isFinal = Modifier.isFinal(field.getModifiers());
        boolean isReadOnly = isFinal && (!override || langReflectAccess.isTrustedFinalField(field));
        return MethodHandleAccessorFactory.newFieldAccessor(field, isReadOnly);
    }

    public MethodAccessor newMethodAccessor(Method method, boolean callerSensitive) {
        // use the root Method that will not cache caller class
        Method root = langReflectAccess.getRoot(method);
        if (root != null) {
            method = root;
        }

        return MethodHandleAccessorFactory.newMethodAccessor(method, callerSensitive);
    }

    public ConstructorAccessor newConstructorAccessor(Constructor<?> c) {
        Class<?> declaringClass = c.getDeclaringClass();
        if (Modifier.isAbstract(declaringClass.getModifiers())) {
            return new InstantiationExceptionConstructorAccessorImpl(null);
        }
        if (declaringClass == Class.class) {
            return new InstantiationExceptionConstructorAccessorImpl
                ("Can not instantiate java.lang.Class");
        }

        // use the root Constructor that will not cache caller class
        Constructor<?> root = langReflectAccess.getRoot(c);
        if (root != null) {
            c = root;
        }

        return MethodHandleAccessorFactory.newConstructorAccessor(c);
    }

    //--------------------------------------------------------------------------
    //
    // Routines used by java.lang
    //
    //

    /** Creates a new java.lang.reflect.Constructor. Access checks as
        per java.lang.reflect.AccessibleObject are not overridden. */
    public Constructor<?> newConstructor(Class<?> declaringClass,
                                         Class<?>[] parameterTypes,
                                         Class<?>[] checkedExceptions,
                                         int modifiers,
                                         int slot,
                                         String signature,
                                         byte[] annotations,
                                         byte[] parameterAnnotations)
    {
        return langReflectAccess.newConstructor(declaringClass,
                                                parameterTypes,
                                                checkedExceptions,
                                                modifiers,
                                                slot,
                                                signature,
                                                annotations,
                                                parameterAnnotations);
    }

    /** Gets the ConstructorAccessor object for a
        java.lang.reflect.Constructor */
    public ConstructorAccessor getConstructorAccessor(Constructor<?> c) {
        return langReflectAccess.getConstructorAccessor(c);
    }

    /** Sets the ConstructorAccessor object for a
        java.lang.reflect.Constructor */
    public void setConstructorAccessor(Constructor<?> c,
                                       ConstructorAccessor accessor)
    {
        langReflectAccess.setConstructorAccessor(c, accessor);
    }

    /** Makes a copy of the passed method. The returned method is a
        "child" of the passed one; see the comments in Method.java for
        details. */
    public Method copyMethod(Method arg) {
        return langReflectAccess.copyMethod(arg);
    }

    /** Makes a copy of the passed method. The returned method is NOT
     * a "child" but a "sibling" of the Method in arg. Should only be
     * used on non-root methods. */
    public Method leafCopyMethod(Method arg) {
        return langReflectAccess.leafCopyMethod(arg);
    }


    /** Makes a copy of the passed field. The returned field is a
        "child" of the passed one; see the comments in Field.java for
        details. */
    public Field copyField(Field arg) {
        return langReflectAccess.copyField(arg);
    }

    /** Makes a copy of the passed constructor. The returned
        constructor is a "child" of the passed one; see the comments
        in Constructor.java for details. */
    public <T> Constructor<T> copyConstructor(Constructor<T> arg) {
        return langReflectAccess.copyConstructor(arg);
    }

    /** Gets the byte[] that encodes TypeAnnotations on an executable.
     */
    public byte[] getExecutableTypeAnnotationBytes(Executable ex) {
        return langReflectAccess.getExecutableTypeAnnotationBytes(ex);
    }

    public Class<?>[] getExecutableSharedParameterTypes(Executable ex) {
        return langReflectAccess.getExecutableSharedParameterTypes(ex);
    }

    public <T> T newInstance(Constructor<T> ctor, Object[] args, Class<?> caller)
        throws IllegalAccessException, InstantiationException, InvocationTargetException
    {
        return langReflectAccess.newInstance(ctor, args, caller);
    }

    //--------------------------------------------------------------------------
    //
    // Routines used by serialization
    //
    //

    public final Constructor<?> newConstructorForExternalization(Class<?> cl) {
        if (!Externalizable.class.isAssignableFrom(cl)) {
            return null;
        }
        try {
            Constructor<?> cons = cl.getConstructor();
            cons.setAccessible(true);
            return cons;
        } catch (NoSuchMethodException ex) {
            return null;
        }
    }

    public final Constructor<?> newConstructorForSerialization(Class<?> cl,
                                                               Constructor<?> constructorToCall)
    {
        if (constructorToCall.getDeclaringClass() == cl) {
            constructorToCall.setAccessible(true);
            return constructorToCall;
        }
        return generateConstructor(cl, constructorToCall);
    }

    /**
     * Given a class, determines whether its superclass has
     * any constructors that are accessible from the class.
     * This is a special purpose method intended to do access
     * checking for a serializable class and its superclasses
     * up to, but not including, the first non-serializable
     * superclass. This also implies that the superclass is
     * always non-null, because a serializable class must be a
     * class (not an interface) and Object is not serializable.
     *
     * @param cl the class from which access is checked
     * @return whether the superclass has a constructor accessible from cl
     */
    private boolean superHasAccessibleConstructor(Class<?> cl) {
        Class<?> superCl = cl.getSuperclass();
        assert Serializable.class.isAssignableFrom(cl);
        assert superCl != null;
        if (packageEquals(cl, superCl)) {
            // accessible if any non-private constructor is found
            for (Constructor<?> ctor : superCl.getDeclaredConstructors()) {
                if ((ctor.getModifiers() & Modifier.PRIVATE) == 0) {
                    return true;
                }
            }
            if (Reflection.areNestMates(cl, superCl)) {
                return true;
            }
            return false;
        } else {
            // sanity check to ensure the parent is protected or public
            if ((superCl.getModifiers() & (Modifier.PROTECTED | Modifier.PUBLIC)) == 0) {
                return false;
            }
            // accessible if any constructor is protected or public
            for (Constructor<?> ctor : superCl.getDeclaredConstructors()) {
                if ((ctor.getModifiers() & (Modifier.PROTECTED | Modifier.PUBLIC)) != 0) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * Returns a constructor that allocates an instance of cl and that then initializes
     * the instance by calling the no-arg constructor of its first non-serializable
     * superclass. This is specified in the Serialization Specification, section 3.1,
     * in step 11 of the deserialization process. If cl is not serializable, returns
     * cl's no-arg constructor. If no accessible constructor is found, or if the
     * class hierarchy is somehow malformed (e.g., a serializable class has no
     * superclass), null is returned.
     *
     * @param cl the class for which a constructor is to be found
     * @return the generated constructor, or null if none is available
     */
    public final Constructor<?> newConstructorForSerialization(Class<?> cl) {
        Class<?> initCl = cl;
        while (Serializable.class.isAssignableFrom(initCl)) {
            Class<?> prev = initCl;
            if ((initCl = initCl.getSuperclass()) == null ||
                (!disableSerialConstructorChecks() && !superHasAccessibleConstructor(prev))) {
                return null;
            }
        }
        Constructor<?> constructorToCall;
        try {
            constructorToCall = initCl.getDeclaredConstructor();
            int mods = constructorToCall.getModifiers();
            if ((mods & Modifier.PRIVATE) != 0 ||
                    ((mods & (Modifier.PUBLIC | Modifier.PROTECTED)) == 0 &&
                            !packageEquals(cl, initCl))) {
                return null;
            }
        } catch (NoSuchMethodException ex) {
            return null;
        }
        return generateConstructor(cl, constructorToCall);
    }

    private final Constructor<?> generateConstructor(Class<?> cl,
                                                     Constructor<?> constructorToCall) {

        Constructor<?> ctor = newConstructor(constructorToCall.getDeclaringClass(),
                                             constructorToCall.getParameterTypes(),
                                             constructorToCall.getExceptionTypes(),
                                             constructorToCall.getModifiers(),
                                             langReflectAccess.getConstructorSlot(constructorToCall),
                                             langReflectAccess.getConstructorSignature(constructorToCall),
                                             langReflectAccess.getConstructorAnnotations(constructorToCall),
                                             langReflectAccess.getConstructorParameterAnnotations(constructorToCall));
        ConstructorAccessor acc;
        if (useOldSerializableConstructor()) {
            acc = new SerializationConstructorAccessorGenerator().
                                generateSerializationConstructor(cl,
                                                                 constructorToCall.getParameterTypes(),
                                                                 constructorToCall.getModifiers(),
                                                                 constructorToCall.getDeclaringClass());
        } else {
            acc = MethodHandleAccessorFactory.newSerializableConstructorAccessor(cl, ctor);
        }
        setConstructorAccessor(ctor, acc);
        ctor.setAccessible(true);
        return ctor;
    }

    public final MethodHandle readObjectForSerialization(Class<?> cl) {
        return findReadWriteObjectForSerialization(cl, "readObject", ObjectInputStream.class);
    }

    public final MethodHandle readObjectNoDataForSerialization(Class<?> cl) {
        return findReadWriteObjectForSerialization(cl, "readObjectNoData", null);
    }

    public final MethodHandle writeObjectForSerialization(Class<?> cl) {
        return findReadWriteObjectForSerialization(cl, "writeObject", ObjectOutputStream.class);
    }

    private final MethodHandle findReadWriteObjectForSerialization(Class<?> cl,
                                                                   String methodName,
                                                                   Class<?> streamClass) {
        if (!Serializable.class.isAssignableFrom(cl)) {
            return null;
        }

        try {
            Method meth = streamClass == null ? cl.getDeclaredMethod(methodName)
                    : cl.getDeclaredMethod(methodName, streamClass);
            int mods = meth.getModifiers();
            if (meth.getReturnType() != Void.TYPE ||
                    Modifier.isStatic(mods) ||
                    !Modifier.isPrivate(mods)) {
                return null;
            }
            meth.setAccessible(true);
            return MethodHandles.lookup().unreflect(meth);
        } catch (NoSuchMethodException ex) {
            return null;
        } catch (IllegalAccessException ex1) {
            throw new InternalError("Error", ex1);
        }
    }

    public final MethodHandle defaultReadObjectForSerialization(Class<?> cl) {
        if (!Serializable.class.isAssignableFrom(cl) || Externalizable.class.isAssignableFrom(cl) || cl.isEnum() || cl.isRecord() || cl.isHidden()) {
            return null;
        }

        // build an anonymous+hidden nestmate to perform the read operation
        ClassDesc thisClass = cl.describeConstable().orElseThrow(InternalError::new);
        byte[] bytes = ClassFile.of().build(ClassDesc.of(thisClass.packageName(), thisClass.displayName() + "$$readObject"), classBuilder -> {
            classBuilder.withMethod("readObject",
                MethodTypeDesc.of(ConstantDescs.CD_void, thisClass, ObjectInputStream.class.describeConstable().orElseThrow(InternalError::new)),
                Modifier.STATIC | Modifier.PRIVATE,
                mb -> mb.withCode(cb -> {
                    // get our GetField
                    cb.aload(1);
                    cb.invokevirtual(SerializationDescs.CD_ObjectInputStream, "readFields", SerializationDescs.MTD_ObjectInputStream_readFields);
                    cb.astore(2);
                    // iterate the fields of the class
                    for (Field field : cl.getDeclaredFields()) {
                        int fieldMods = field.getModifiers();
                        if (Modifier.isStatic(fieldMods) || Modifier.isTransient(fieldMods)) {
                            continue;
                        }
                        boolean isFinal = Modifier.isFinal(fieldMods);
                        String fieldName = field.getName();
                        Class<?> fieldType = field.getType();

                        if (isFinal) {
                            // special setter
                            cb.ldc(DynamicConstantDesc.ofNamed(
                                SerializationDescs.DMHD_ConstantBootstraps_fieldSetterForSerialzation,
                                fieldName,
                                ConstantDescs.CD_MethodHandle,
                                thisClass
                            ));
                            // stack: <mh>
                        }
                        cb.aload(0); // stack: <mh>? this
                        cb.aload(2); // stack: <mh>? this GetField
                        cb.ldc(fieldName); // stack: <mh>? this GetField <name>

                        ClassDesc fieldDesc = fieldType.describeConstable().orElseThrow(InternalError::new);

                        switch (fieldDesc.descriptorString()) {
                            case "B" -> {
                                cb.iconst_0();
                                cb.invokevirtual(SerializationDescs.CD_ObjectInputStream_GetField, "get", SerializationDescs.MTD_ObjectInputStream_GetField_get_B);
                            }
                            case "C" -> {
                                cb.iconst_0();
                                cb.invokevirtual(SerializationDescs.CD_ObjectInputStream_GetField, "get", SerializationDescs.MTD_ObjectInputStream_GetField_get_C);
                            }
                            case "D" -> {
                                cb.dconst_0();
                                cb.invokevirtual(SerializationDescs.CD_ObjectInputStream_GetField, "get", SerializationDescs.MTD_ObjectInputStream_GetField_get_D);
                            }
                            case "F" -> {
                                cb.fconst_0();
                                cb.invokevirtual(SerializationDescs.CD_ObjectInputStream_GetField, "get", SerializationDescs.MTD_ObjectInputStream_GetField_get_F);
                            }
                            case "I" -> {
                                cb.iconst_0();
                                cb.invokevirtual(SerializationDescs.CD_ObjectInputStream_GetField, "get", SerializationDescs.MTD_ObjectInputStream_GetField_get_I);
                            }
                            case "J" -> {
                                cb.lconst_0();
                                cb.invokevirtual(SerializationDescs.CD_ObjectInputStream_GetField, "get", SerializationDescs.MTD_ObjectInputStream_GetField_get_J);
                            }
                            case "S" -> {
                                cb.iconst_0();
                                cb.invokevirtual(SerializationDescs.CD_ObjectInputStream_GetField, "get", SerializationDescs.MTD_ObjectInputStream_GetField_get_S);
                            }
                            case "Z" -> {
                                cb.iconst_0();
                                cb.invokevirtual(SerializationDescs.CD_ObjectInputStream_GetField, "get", SerializationDescs.MTD_ObjectInputStream_GetField_get_Z);
                            }
                            default -> {
                                cb.aconst_null();
                                cb.invokevirtual(SerializationDescs.CD_ObjectInputStream_GetField, "get", SerializationDescs.MTD_ObjectInputStream_GetField_get_L);
                                cb.checkcast(fieldDesc);
                            }
                        }
                        if (isFinal) {
                            // stack: <mh> this <val>
                            cb.invokevirtual(ConstantDescs.CD_MethodHandle, "invokeExact", MethodTypeDesc.of(ConstantDescs.CD_void, thisClass, fieldDesc));
                        } else {
                            // non-final; store it the usual way
                            // stack: this <val>
                            cb.putfield(thisClass, fieldName, fieldDesc);
                        }
                    }
                    cb.return_();
                })
            );
        });
        try {
            MethodHandles.Lookup clLookup = MethodHandles.privateLookupIn(cl, MethodHandles.lookup());
            MethodHandles.Lookup hcLookup = clLookup.defineHiddenClass(bytes, false, MethodHandles.Lookup.ClassOption.NESTMATE);
            return hcLookup.findStatic(hcLookup.lookupClass(), "readObject", MethodType.methodType(void.class, cl, ObjectInputStream.class));
        } catch (IllegalAccessException | NoSuchMethodException e) {
            throw new InternalError("Error in readObject generation", e);
        }
    }

    public final MethodHandle defaultWriteObjectForSerialization(Class<?> cl) {
        if (!Serializable.class.isAssignableFrom(cl) || Externalizable.class.isAssignableFrom(cl) || cl.isEnum() || cl.isRecord() || cl.isHidden()) {
            return null;
        }

        // build an anonymous+hidden nestmate to perform the write operation
        ClassDesc thisClass = cl.describeConstable().orElseThrow(InternalError::new);
        byte[] bytes = ClassFile.of().build(ClassDesc.of(thisClass.packageName(), thisClass.displayName() + "$$writeObject"), classBuilder -> {
            classBuilder.withMethod("writeObject",
                MethodTypeDesc.of(ConstantDescs.CD_void, thisClass, ObjectOutputStream.class.describeConstable().orElseThrow(InternalError::new)),
                Modifier.STATIC | Modifier.PRIVATE,
                mb -> mb.withCode(cb -> {
                    // get our PutField
                    cb.aload(1);
                    cb.invokevirtual(SerializationDescs.CD_ObjectOutputStream, "putFields", SerializationDescs.MTD_ObjectOutputStream_putFields);
                    cb.astore(2);
                    // iterate the fields of the class
                    for (Field field : cl.getDeclaredFields()) {
                        int fieldMods = field.getModifiers();
                        if (Modifier.isStatic(fieldMods) || Modifier.isTransient(fieldMods)) {
                            continue;
                        }
                        String fieldName = field.getName();
                        Class<?> fieldType = field.getType();

                        cb.aload(2); // stack: PutField
                        cb.ldc(fieldName); // stack: PutField fieldName
                        cb.aload(0); // stack: PutField fieldName this

                        switch (fieldType.descriptorString()) {
                            case "B" -> {
                                cb.getfield(thisClass, fieldName, ConstantDescs.CD_byte);
                                cb.invokevirtual(SerializationDescs.CD_ObjectOutputStream_PutField, "put", SerializationDescs.MTD_ObjectOutputStream_PutField_put_B);
                            }
                            case "C" -> {
                                cb.getfield(thisClass, fieldName, ConstantDescs.CD_char);
                                cb.invokevirtual(SerializationDescs.CD_ObjectOutputStream_PutField, "put", SerializationDescs.MTD_ObjectOutputStream_PutField_put_C);
                            }
                            case "D" -> {
                                cb.getfield(thisClass, fieldName, ConstantDescs.CD_double);
                                cb.invokevirtual(SerializationDescs.CD_ObjectOutputStream_PutField, "put", SerializationDescs.MTD_ObjectOutputStream_PutField_put_D);
                            }
                            case "F" -> {
                                cb.getfield(thisClass, fieldName, ConstantDescs.CD_float);
                                cb.invokevirtual(SerializationDescs.CD_ObjectOutputStream_PutField, "put", SerializationDescs.MTD_ObjectOutputStream_PutField_put_F);
                            }
                            case "I" -> {
                                cb.getfield(thisClass, fieldName, ConstantDescs.CD_int);
                                cb.invokevirtual(SerializationDescs.CD_ObjectOutputStream_PutField, "put", SerializationDescs.MTD_ObjectOutputStream_PutField_put_I);
                            }
                            case "J" -> {
                                cb.getfield(thisClass, fieldName, ConstantDescs.CD_long);
                                cb.invokevirtual(SerializationDescs.CD_ObjectOutputStream_PutField, "put", SerializationDescs.MTD_ObjectOutputStream_PutField_put_J);
                            }
                            case "S" -> {
                                cb.getfield(thisClass, fieldName, ConstantDescs.CD_short);
                                cb.invokevirtual(SerializationDescs.CD_ObjectOutputStream_PutField, "put", SerializationDescs.MTD_ObjectOutputStream_PutField_put_S);
                            }
                            case "Z" -> {
                                cb.getfield(thisClass, fieldName, ConstantDescs.CD_boolean);
                                cb.invokevirtual(SerializationDescs.CD_ObjectOutputStream_PutField, "put", SerializationDescs.MTD_ObjectOutputStream_PutField_put_Z);
                            }
                            default -> {
                                cb.getfield(thisClass, fieldName, fieldType.describeConstable().orElseThrow(InternalError::new));
                                cb.invokevirtual(SerializationDescs.CD_ObjectOutputStream_PutField, "put", SerializationDescs.MTD_ObjectOutputStream_PutField_put_L);
                            }
                        }
                    }
                    // commit fields to stream
                    cb.aload(1);
                    cb.invokevirtual(SerializationDescs.CD_ObjectOutputStream, "writeFields", ConstantDescs.MTD_void);
                    cb.return_();
                })
            );
        });
        try {
            MethodHandles.Lookup clLookup = MethodHandles.privateLookupIn(cl, MethodHandles.lookup());
            MethodHandles.Lookup hcLookup = clLookup.defineHiddenClass(bytes, false, MethodHandles.Lookup.ClassOption.NESTMATE);
            return hcLookup.findStatic(hcLookup.lookupClass(), "writeObject", MethodType.methodType(void.class, cl, ObjectOutputStream.class));
        } catch (IllegalAccessException | NoSuchMethodException e) {
            throw new InternalError("Error in writeObject generation", e);
        }
    }

    /**
     * Returns a MethodHandle for {@code writeReplace} on the serializable class
     * or null if no match found.
     * @param cl a serializable class
     * @return the {@code writeReplace} MethodHandle or {@code null} if not found
     */
    public final MethodHandle writeReplaceForSerialization(Class<?> cl) {
        return getReplaceResolveForSerialization(cl, "writeReplace");
    }

    /**
     * Returns a MethodHandle for {@code readResolve} on the serializable class
     * or null if no match found.
     * @param cl a serializable class
     * @return the {@code writeReplace} MethodHandle or {@code null} if not found
     */
    public final MethodHandle readResolveForSerialization(Class<?> cl) {
        return getReplaceResolveForSerialization(cl, "readResolve");
    }

    /**
     * Lookup readResolve or writeReplace on a class with specified
     * signature constraints.
     * @param cl a serializable class
     * @param methodName the method name to find
     * @return a MethodHandle for the method or {@code null} if not found or
     *       has the wrong signature.
     */
    private MethodHandle getReplaceResolveForSerialization(Class<?> cl,
                                                           String methodName) {
        if (!Serializable.class.isAssignableFrom(cl)) {
            return null;
        }

        Class<?> defCl = cl;
        while (defCl != null) {
            try {
                Method m = defCl.getDeclaredMethod(methodName);
                if (m.getReturnType() != Object.class) {
                    return null;
                }
                int mods = m.getModifiers();
                if (Modifier.isStatic(mods) | Modifier.isAbstract(mods)) {
                    return null;
                } else if (Modifier.isPublic(mods) | Modifier.isProtected(mods)) {
                    // fall through
                } else if (Modifier.isPrivate(mods) && (cl != defCl)) {
                    return null;
                } else if (!packageEquals(cl, defCl)) {
                    return null;
                }
                try {
                    // Normal return
                    m.setAccessible(true);
                    return MethodHandles.lookup().unreflect(m);
                } catch (IllegalAccessException ex0) {
                    // setAccessible should prevent IAE
                    throw new InternalError("Error", ex0);
                }
            } catch (NoSuchMethodException ex) {
                defCl = defCl.getSuperclass();
            }
        }
        return null;
    }

    /**
     * Returns true if the given class defines a static initializer method,
     * false otherwise.
     */
    public final boolean hasStaticInitializerForSerialization(Class<?> cl) {
        Method m = hasStaticInitializerMethod;
        if (m == null) {
            try {
                m = ObjectStreamClass.class.getDeclaredMethod("hasStaticInitializer",
                        new Class<?>[]{Class.class});
                m.setAccessible(true);
                hasStaticInitializerMethod = m;
            } catch (NoSuchMethodException ex) {
                throw new InternalError("No such method hasStaticInitializer on "
                        + ObjectStreamClass.class, ex);
            }
        }
        try {
            return (Boolean) m.invoke(null, cl);
        } catch (InvocationTargetException | IllegalAccessException ex) {
            throw new InternalError("Exception invoking hasStaticInitializer", ex);
        }
    }

    /**
     * Return the accessible constructor for OptionalDataException signaling eof.
     * @return the eof constructor for OptionalDataException
     */
    public final Constructor<OptionalDataException> newOptionalDataExceptionForSerialization() {
        try {
            Constructor<OptionalDataException> boolCtor =
                    OptionalDataException.class.getDeclaredConstructor(Boolean.TYPE);
            boolCtor.setAccessible(true);
            return boolCtor;
        } catch (NoSuchMethodException ex) {
            throw new InternalError("Constructor not found", ex);
        }
    }

    //--------------------------------------------------------------------------
    //
    // Internals only below this point
    //

    /*
     * If -Djdk.reflect.useNativeAccessorOnly is set, use the native accessor only.
     * For testing purpose only.
     */
    static boolean useNativeAccessorOnly() {
        return config().useNativeAccessorOnly;
    }

    static boolean useOldSerializableConstructor() {
        return config().useOldSerializableConstructor;
    }

    private static boolean disableSerialConstructorChecks() {
        return config().disableSerialConstructorChecks;
    }

    /**
     * The configuration is lazily initialized after the module system is initialized. The
     * default config would be used before the proper config is loaded.
     *
     * The static initializer of ReflectionFactory is run before the system properties are set up.
     * The class initialization is caused by the class initialization of java.lang.reflect.Method
     * (more properly, caused by the class initialization for java.lang.reflect.AccessibleObject)
     * that happens very early VM startup, initPhase1.
     */
    private static @Stable Config config;

    private static final Config DEFAULT_CONFIG = new Config(false, // useNativeAccessorOnly
                                                            false,  // useOldSerializeableConstructor
                                                            false); // disableSerialConstructorChecks

    /**
     * The configurations for the reflection factory. Configurable via
     * system properties but only available after ReflectionFactory is
     * loaded during early VM startup.
     *
     * Note that the default implementations of the object methods of
     * this Config record (toString, equals, hashCode) use indy,
     * which is available to use only after initPhase1. These methods
     * are currently not called, but should they be needed, a workaround
     * is to override them.
     */
    private record Config(boolean useNativeAccessorOnly,
                          boolean useOldSerializableConstructor,
                          boolean disableSerialConstructorChecks) {
    }

    private static Config config() {
        Config c = config;
        if (c != null) {
            return c;
        }

        // Always use the default configuration until the module system is initialized.
        if (!VM.isModuleSystemInited()) {
            return DEFAULT_CONFIG;
        }

        return config = loadConfig();
    }

    private static Config loadConfig() {
        assert VM.isModuleSystemInited();

        Properties props = GetPropertyAction.privilegedGetProperties();
        boolean useNativeAccessorOnly =
            "true".equals(props.getProperty("jdk.reflect.useNativeAccessorOnly"));
        boolean useOldSerializableConstructor =
            "true".equals(props.getProperty("jdk.reflect.useOldSerializableConstructor"));
        boolean disableSerialConstructorChecks =
            "true".equals(props.getProperty("jdk.disableSerialConstructorChecks"));

        return new Config(useNativeAccessorOnly, useOldSerializableConstructor, disableSerialConstructorChecks);
    }

    /**
     * Returns true if classes are defined in the classloader and same package, false
     * otherwise.
     * @param cl1 a class
     * @param cl2 another class
     * @return true if the two classes are in the same classloader and package
     */
    private static boolean packageEquals(Class<?> cl1, Class<?> cl2) {
        assert !cl1.isArray() && !cl2.isArray();

        if (cl1 == cl2) {
            return true;
        }

        return cl1.getClassLoader() == cl2.getClassLoader() &&
                cl1.getPackageName() == cl2.getPackageName();
    }

    /**
     * Holder class for lazy init of serialization constant descriptors.
     */
    private static final class SerializationDescs {
        private SerializationDescs() {
            // no instances
        }

        private static final ClassDesc CD_ObjectInputStream = ClassDesc.of("java.io.ObjectInputStream");
        private static final ClassDesc CD_ObjectInputStream_GetField = ClassDesc.of("java.io.ObjectInputStream$GetField");

        private static final ClassDesc CD_ObjectOutputStream = ClassDesc.of("java.io.ObjectOutputStream");
        private static final ClassDesc CD_ObjectOutputStream_PutField = ClassDesc.of("java.io.ObjectOutputStream$PutField");

        private static final MethodTypeDesc MTD_ObjectInputStream_readFields = MethodTypeDesc.of(CD_ObjectInputStream_GetField);

        private static final MethodTypeDesc MTD_ObjectInputStream_GetField_get_B = MethodTypeDesc.of(ConstantDescs.CD_byte, ConstantDescs.CD_String, ConstantDescs.CD_byte);
        private static final MethodTypeDesc MTD_ObjectInputStream_GetField_get_C = MethodTypeDesc.of(ConstantDescs.CD_char, ConstantDescs.CD_String, ConstantDescs.CD_char);
        private static final MethodTypeDesc MTD_ObjectInputStream_GetField_get_D = MethodTypeDesc.of(ConstantDescs.CD_double, ConstantDescs.CD_String, ConstantDescs.CD_double);
        private static final MethodTypeDesc MTD_ObjectInputStream_GetField_get_F = MethodTypeDesc.of(ConstantDescs.CD_float, ConstantDescs.CD_String, ConstantDescs.CD_float);
        private static final MethodTypeDesc MTD_ObjectInputStream_GetField_get_I = MethodTypeDesc.of(ConstantDescs.CD_int, ConstantDescs.CD_String, ConstantDescs.CD_int);
        private static final MethodTypeDesc MTD_ObjectInputStream_GetField_get_J = MethodTypeDesc.of(ConstantDescs.CD_long, ConstantDescs.CD_String, ConstantDescs.CD_long);
        private static final MethodTypeDesc MTD_ObjectInputStream_GetField_get_L = MethodTypeDesc.of(ConstantDescs.CD_Object, ConstantDescs.CD_String, ConstantDescs.CD_Object);
        private static final MethodTypeDesc MTD_ObjectInputStream_GetField_get_S = MethodTypeDesc.of(ConstantDescs.CD_short, ConstantDescs.CD_String, ConstantDescs.CD_short);
        private static final MethodTypeDesc MTD_ObjectInputStream_GetField_get_Z = MethodTypeDesc.of(ConstantDescs.CD_boolean, ConstantDescs.CD_String, ConstantDescs.CD_boolean);

        private static final MethodTypeDesc MTD_ObjectOutputStream_putFields = MethodTypeDesc.of(CD_ObjectOutputStream_PutField);

        private static final MethodTypeDesc MTD_ObjectOutputStream_PutField_put_B = MethodTypeDesc.of(ConstantDescs.CD_void, ConstantDescs.CD_String, ConstantDescs.CD_byte);
        private static final MethodTypeDesc MTD_ObjectOutputStream_PutField_put_C = MethodTypeDesc.of(ConstantDescs.CD_void, ConstantDescs.CD_String, ConstantDescs.CD_char);
        private static final MethodTypeDesc MTD_ObjectOutputStream_PutField_put_D = MethodTypeDesc.of(ConstantDescs.CD_void, ConstantDescs.CD_String, ConstantDescs.CD_double);
        private static final MethodTypeDesc MTD_ObjectOutputStream_PutField_put_F = MethodTypeDesc.of(ConstantDescs.CD_void, ConstantDescs.CD_String, ConstantDescs.CD_float);
        private static final MethodTypeDesc MTD_ObjectOutputStream_PutField_put_I = MethodTypeDesc.of(ConstantDescs.CD_void, ConstantDescs.CD_String, ConstantDescs.CD_int);
        private static final MethodTypeDesc MTD_ObjectOutputStream_PutField_put_J = MethodTypeDesc.of(ConstantDescs.CD_void, ConstantDescs.CD_String, ConstantDescs.CD_long);
        private static final MethodTypeDesc MTD_ObjectOutputStream_PutField_put_L = MethodTypeDesc.of(ConstantDescs.CD_void, ConstantDescs.CD_String, ConstantDescs.CD_Object);
        private static final MethodTypeDesc MTD_ObjectOutputStream_PutField_put_S = MethodTypeDesc.of(ConstantDescs.CD_void, ConstantDescs.CD_String, ConstantDescs.CD_short);
        private static final MethodTypeDesc MTD_ObjectOutputStream_PutField_put_Z = MethodTypeDesc.of(ConstantDescs.CD_void, ConstantDescs.CD_String, ConstantDescs.CD_boolean);

        private static final DirectMethodHandleDesc DMHD_ConstantBootstraps_fieldSetterForSerialzation = MethodHandleDesc.ofMethod(
            DirectMethodHandleDesc.Kind.STATIC,
            ConstantDescs.CD_ConstantBootstraps,
            "fieldSetterForSerialization",
            MethodTypeDesc.of(
                ConstantDescs.CD_MethodHandle,
                ConstantDescs.CD_MethodHandles_Lookup,
                ConstantDescs.CD_String,
                ConstantDescs.CD_Class,
                ConstantDescs.CD_Class
            )
        );
    }
}
