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

package sun.reflect;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamField;
import java.io.OptionalDataException;
import java.lang.invoke.MethodHandle;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

/**
 * ReflectionFactory supports custom serialization.
 * Its methods support the creation of uninitialized objects, invoking serialization
 * private methods for readObject, writeObject, readResolve, and writeReplace.
 */
public class ReflectionFactory {

    private static final ReflectionFactory soleInstance = new ReflectionFactory();
    private static final jdk.internal.reflect.ReflectionFactory delegate =
            jdk.internal.reflect.ReflectionFactory.getReflectionFactory();

    private ReflectionFactory() {}

    /**
     * Provides the caller with the capability to instantiate reflective
     * objects.
     *
     * <p> The returned {@code ReflectionFactory} object should be carefully
     * guarded by the caller, since it can be used to read and write private
     * data and invoke private methods, as well as to load unverified bytecodes.
     * It must never be passed to untrusted code.
     *
     * @return the ReflectionFactory
     */
    public static ReflectionFactory getReflectionFactory() {
        return soleInstance;
    }

    /**
     * Returns an accessible constructor capable of creating instances
     * of the given class, initialized by the given constructor.
     *
     * @param cl the class to instantiate
     * @param constructorToCall the constructor to call
     * @return an accessible constructor
     */
    public Constructor<?> newConstructorForSerialization(Class<?> cl,
                                                         Constructor<?> constructorToCall)
    {
        return delegate.newConstructorForSerialization(cl,
                                                       constructorToCall);
    }

    /**
     * Returns an accessible no-arg constructor for a class.
     * The no-arg constructor is found searching the class and its supertypes.
     *
     * @param cl the class to instantiate
     * @return a no-arg constructor for the class or {@code null} if
     *     the class or supertypes do not have a suitable no-arg constructor
     */
    public final Constructor<?> newConstructorForSerialization(Class<?> cl)
    {
        return delegate.newConstructorForSerialization(cl);
    }

    /**
     * Returns an accessible no-arg constructor for an externalizable class to be
     * initialized using a public no-argument constructor.
     *
     * @param cl the class to instantiate
     * @return A no-arg constructor for the class; returns {@code null} if
     *     the class does not implement {@link java.io.Externalizable}
     */
    public final Constructor<?> newConstructorForExternalization(Class<?> cl) {
        return delegate.newConstructorForExternalization(cl);
    }

    /**
     * Returns a direct MethodHandle for the {@code readObject} method on
     * a Serializable class.
     * The first argument of {@link MethodHandle#invoke} is the serializable
     * object and the second argument is the {@code ObjectInputStream} passed to
     * {@code readObject}.
     *
     * @param cl a Serializable class
     * @return  a direct MethodHandle for the {@code readObject} method of the class or
     *          {@code null} if the class does not have a {@code readObject} method
     */
    public final MethodHandle readObjectForSerialization(Class<?> cl) {
        return delegate.readObjectForSerialization(cl);
    }

    /**
     * Returns a direct MethodHandle for the {@code readObjectNoData} method on
     * a Serializable class.
     * The only argument of {@link MethodHandle#invoke} is the serializable
     * object, which {@code readObjectNoData} is called on. No arguments are
     * passed to the {@code readObjectNoData} method.
     *
     * @param cl a Serializable class
     * @return  a direct MethodHandle for the {@code readObjectNoData} method
     *          of the class or {@code null} if the class does not have a
     *          {@code readObjectNoData} method
     */
    public final MethodHandle readObjectNoDataForSerialization(Class<?> cl) {
        return delegate.readObjectNoDataForSerialization(cl);
    }

    /**
     * Generate and return a direct MethodHandle which implements
     * the general default behavior for serializable class's {@code readObject}.
     * The generated method behaves in accordance with the
     * Java Serialization specification's rules for that method.
     * <p>
     * The generated method will invoke {@link ObjectInputStream#readFields()}
     * to acquire the stream field values. The serialization fields of the class will
     * then be populated from the stream values.
     * <p>
     * Only fields which are eligible for default serialization will be populated.
     * This includes only fields which are not {@code transient} and not {@code static}
     * (even if the field is {@code final} or {@code private}).
     * <p>
     * Requesting a default serialization method for a class in a disallowed
     * category is not supported; {@code null} will be returned for such classes.
     * The disallowed categories include (but are not limited to):
     * <ul>
     *     <li>Classes which do not implement {@code Serializable}</li>
     *     <li>Classes which implement {@code Externalizable}</li>
     *     <li>Classes which are specially handled by the Java Serialization specification,
     *     including record classes, enum constant classes, {@code Class}, {@code String},
     *     array classes, reflection proxy classes, and hidden classes</li>
     *     <li>Classes which declare a valid {@code serialPersistentFields} value</li>
     *     <li>Any special types which may possibly be added to the JDK or the Java language
     *     in the future which in turn might require special handling by the
     *     provisions of the corresponding future version of the Java Serialization
     *     specification</li>
     * </ul>
     * <p>
     * The generated method will accept the instance as its first argument
     * and the {@code ObjectInputStream} as its second argument.
     * The return type of the method is {@code void}.
     *
     * @param cl a Serializable class
     * @return  a direct MethodHandle for the synthetic {@code readObject} method
     *          or {@code null} if the class falls in a disallowed category
     *
     * @since 24
     */
    public final MethodHandle defaultReadObjectForSerialization(Class<?> cl) {
        return delegate.defaultReadObjectForSerialization(cl);
    }

    /**
     * Returns a direct MethodHandle for the {@code writeObject} method on
     * a Serializable class.
     * The first argument of {@link MethodHandle#invoke} is the serializable
     * object and the second argument is the {@code ObjectOutputStream} passed to
     * {@code writeObject}.
     *
     * @param cl a Serializable class
     * @return  a direct MethodHandle for the {@code writeObject} method of the class or
     *          {@code null} if the class does not have a {@code writeObject} method
     */
    public final MethodHandle writeObjectForSerialization(Class<?> cl) {
        return delegate.writeObjectForSerialization(cl);
    }

    /**
     * Generate and return a direct MethodHandle which implements
     * the general default behavior for serializable class's {@code writeObject}.
     * The generated method behaves in accordance with the
     * Java Serialization specification's rules for that method.
     * <p>
     * The generated method will invoke {@link ObjectOutputStream#putFields}
     * to acquire the buffer for the stream field values. The buffer will
     * be populated from the serialization fields of the class. The buffer
     * will then be flushed to the stream using the
     * {@link ObjectOutputStream#writeFields()} method.
     * <p>
     * Only fields which are eligible for default serialization will be written
     * to the buffer.
     * This includes only fields which are not {@code transient} and not {@code static}
     * (even if the field is {@code final} or {@code private}).
     * <p>
     * Requesting a default serialization method for a class in a disallowed
     * category is not supported; {@code null} will be returned for such classes.
     * The disallowed categories include (but are not limited to):
     * <ul>
     *     <li>Classes which do not implement {@code Serializable}</li>
     *     <li>Classes which implement {@code Externalizable}</li>
     *     <li>Classes which are specially handled by the Java Serialization specification,
     *     including record classes, enum constant classes, {@code Class}, {@code String},
     *     array classes, reflection proxy classes, and hidden classes</li>
     *     <li>Classes which declare a valid {@code serialPersistentFields} value</li>
     *     <li>Any special types which may possibly be added to the JDK or the Java language
     *     in the future which in turn might require special handling by the
     *     provisions of the corresponding future version of the Java Serialization
     *     specification</li>
     * </ul>
     * <p>
     * The generated method will accept the instance as its first argument
     * and the {@code ObjectOutputStream} as its second argument.
     * The return type of the method is {@code void}.
     *
     * @param cl a Serializable class
     * @return  a direct MethodHandle for the synthetic {@code writeObject} method
     *          or {@code null} if the class falls in a disallowed category
     *
     * @since 24
     */
    public final MethodHandle defaultWriteObjectForSerialization(Class<?> cl) {
        return delegate.defaultWriteObjectForSerialization(cl);
    }

    /**
     * Returns a direct MethodHandle for the {@code readResolve} method on
     * a serializable class.
     * The single argument of {@link MethodHandle#invoke} is the serializable
     * object.
     *
     * @param cl the Serializable class
     * @return  a direct MethodHandle for the {@code readResolve} method of the class or
     *          {@code null} if the class does not have a {@code readResolve} method
     */
    public final MethodHandle readResolveForSerialization(Class<?> cl) {
        return delegate.readResolveForSerialization(cl);
    }

    /**
     * Returns a direct MethodHandle for the {@code writeReplace} method on
     * a serializable class.
     * The single argument of {@link MethodHandle#invoke} is the serializable
     * object.
     *
     * @param cl the Serializable class
     * @return  a direct MethodHandle for the {@code writeReplace} method of the class or
     *          {@code null} if the class does not have a {@code writeReplace} method
     */
    public final MethodHandle writeReplaceForSerialization(Class<?> cl) {
        return delegate.writeReplaceForSerialization(cl);
    }

    /**
     * Returns true if the class has a static initializer.
     * The presence of a static initializer is used to compute the serialVersionUID.
     * @param cl a serializable class
     * @return {@code true} if the class has a static initializer,
     *          otherwise {@code false}
     */
    public final boolean hasStaticInitializerForSerialization(Class<?> cl) {
        return delegate.hasStaticInitializerForSerialization(cl);
    }

    /**
     * Returns a new OptionalDataException with {@code eof} set to {@code true}
     * or {@code false}.
     * @param bool the value of {@code eof} in the created OptionalDataException
     * @return  a new OptionalDataException
     */
    public final OptionalDataException newOptionalDataExceptionForSerialization(boolean bool) {
        Constructor<OptionalDataException> cons = delegate.newOptionalDataExceptionForSerialization();
        try {
            return cons.newInstance(bool);
        } catch (InstantiationException|IllegalAccessException|InvocationTargetException ex) {
            throw new InternalError("unable to create OptionalDataException", ex);
        }
    }

    /**
     * {@return the declared {@code serialPersistentFields} from a
     * serializable class, or {@code null} if none is declared, the field
     * is declared but not valid, or the class is not a valid serializable class}
     * A class is a valid serializable class if it implements {@code Serializable}
     * but not {@code Externalizable}. The {@code serialPersistentFields} field
     * is valid if it meets the type and accessibility restrictions defined
     * by the Java Serialization specification.
     *
     * @param cl a Serializable class
     *
     * @since 24
     */
    public final ObjectStreamField[] serialPersistentFields(Class<?> cl) {
        return delegate.serialPersistentFields(cl);
    }
}
