/*
 * Copyright (c) 2004, 2016, Oracle and/or its affiliates. All rights reserved.
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

package sun.corba ;

import java.io.OptionalDataException;
import java.lang.invoke.MethodHandle;
import java.lang.reflect.Field;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.StackWalker;
import java.lang.StackWalker.StackFrame;
import java.util.Optional;
import java.util.stream.Stream;

import java.security.AccessController;
import java.security.Permission;
import java.security.PrivilegedAction;
import java.security.ProtectionDomain;

import sun.misc.Unsafe;
import sun.reflect.ReflectionFactory;

/** This class provides the methods for fundamental JVM operations
 * needed in the ORB that are not part of the public Java API.  This includes:
 * <ul>
 * <li>throwException, which can throw undeclared checked exceptions.
 * This is needed to handle throwing arbitrary exceptions across a standardized
 * OMG interface that (incorrectly) does not specify appropriate exceptions.</li>
 * <li>putXXX/getXXX methods that allow unchecked access to fields of objects.
 * This is used for setting uninitialzed non-static final fields (which is
 * impossible with reflection) and for speed.</li>
 * <li>objectFieldOffset to obtain the field offsets for use in the putXXX/getXXX methods</li>
 * <li>newConstructorForSerialization to get the special constructor required for a
 * Serializable class</li>
 * <li>latestUserDefinedLoader to get the latest user defined class loader from
 * the call stack as required by the RMI-IIOP specification (really from the
 * JDK 1.1 days)</li>
 * </ul>
 * The code that calls Bridge.get() must have the following Permissions:
 * <ul>
 * <li>BridgePermission "getBridge"</li>
 * <li>ReflectPermission "suppressAccessChecks"</li>
 * <li>RuntimePermission "getStackWalkerWithClassReference"</li>
 * <li>RuntimePermission "reflectionFactoryAccess"</li>
 * </ul>
 * <p>
 * All of these permissions are required to obtain and correctly initialize
 * the instance of Bridge.  No security checks are performed on calls
 * made to Bridge instance methods, so access to the Bridge instance
 * must be protected.
 * <p>
 * This class is a singleton (per ClassLoader of course).  Access to the
 * instance is obtained through the Bridge.get() method.
 */
public final class Bridge
{
    private static final Permission getBridgePermission =
            new BridgePermission("getBridge");
    private static Bridge bridge = null ;

    /** Access to Unsafe to read/write fields. */
    private static final Unsafe unsafe = AccessController.doPrivileged(
            (PrivilegedAction<Unsafe>)() -> {
                try {
                    Field field = Unsafe.class.getDeclaredField("theUnsafe");
                    field.setAccessible(true);
                    return (Unsafe)field.get(null);

                } catch (NoSuchFieldException |IllegalAccessException ex) {
                    throw new InternalError("Unsafe.theUnsafe field not available", ex);
                }
            }
    ) ;

    private final ReflectionFactory reflectionFactory ;
    private final StackWalker stackWalker;

    private Bridge() {
        reflectionFactory = ReflectionFactory.getReflectionFactory();
        stackWalker  = StackWalker.getInstance(
                            StackWalker.Option.RETAIN_CLASS_REFERENCE);
    }

    /** Fetch the Bridge singleton.  This requires the following
     * permissions:
     * <ul>
     * <li>BridgePermission "getBridge"</li>
     * <li>ReflectPermission "suppressAccessChecks"</li>
     * <li>RuntimePermission "getStackWalkerWithClassReference"</li>
     * <li>RuntimePermission "reflectionFactoryAccess"</li>
     * </ul>
     * @return The singleton instance of the Bridge class
     * @throws SecurityException if the caller does not have the
     * required permissions and the caller has a non-null security manager.
     */
    public static final synchronized Bridge get()
    {
        SecurityManager sman = System.getSecurityManager() ;
        if (sman != null)
            sman.checkPermission( getBridgePermission ) ;

        if (bridge == null) {
            bridge = new Bridge() ;
        }

        return bridge ;
    }

    /** Returns true if the loader that loaded the frame's declaring class
     * is a user loader (if it is not the platform class loader or one of
     * its ancestor).
     */
    private boolean isUserLoader(StackFrame sf) {
        ClassLoader cl = sf.getDeclaringClass().getClassLoader();
        if (cl == null) return false;
        ClassLoader p = ClassLoader.getPlatformClassLoader();
        while (cl != p && p != null) p = p.getParent();
        return cl != p;
    }

    private Optional<StackFrame> getLatestUserDefinedLoaderFrame(Stream<StackFrame> stream) {
        return stream.filter(this::isUserLoader).findFirst();
    }


    /** Obtain the latest user defined ClassLoader from the call stack.
     * This is required by the RMI-IIOP specification.
     */
    public final ClassLoader getLatestUserDefinedLoader() {
        // requires getClassLoader permission => needs doPrivileged.
        PrivilegedAction<ClassLoader> pa = () ->
            stackWalker.walk(this::getLatestUserDefinedLoaderFrame)
                .map(sf -> sf.getDeclaringClass().getClassLoader())
                .orElseGet(() -> ClassLoader.getPlatformClassLoader());
        return AccessController.doPrivileged(pa);
    }

    /**
     * Fetches a field element within the given
     * object <code>o</code> at the given offset.
     * The result is undefined unless the offset was obtained from
     * {@link #objectFieldOffset} on the {@link java.lang.reflect.Field}
     * of some Java field and the object referred to by <code>o</code>
     * is of a class compatible with that field's class.
     * @param o Java heap object in which the field from which the offset
     * was obtained resides
     * @param offset indication of where the field resides in a Java heap
     *        object
     * @return the value fetched from the indicated Java field
     * @throws RuntimeException No defined exceptions are thrown, not even
     *         {@link NullPointerException}
     */
    public final int getInt(Object o, long offset)
    {
        return unsafe.getInt( o, offset ) ;
    }

    /**
     * Stores a value into a given Java field.
     * <p>
     * The first two parameters are interpreted exactly as with
     * {@link #getInt(Object, long)} to refer to a specific
     * Java field.  The given value is stored into that field.
     * <p>
     * The field must be of the same type as the method
     * parameter <code>x</code>.
     *
     * @param o Java heap object in which the field resides, if any, else
     *        null
     * @param offset indication of where the field resides in a Java heap
     *        object.
     * @param x the value to store into the indicated Java field
     * @throws RuntimeException No defined exceptions are thrown, not even
     *         {@link NullPointerException}
     */
    public final void putInt(Object o, long offset, int x)
    {
        unsafe.putInt( o, offset, x ) ;
    }

    /**
     * @see #getInt(Object, long)
     */
    public final Object getObject(Object o, long offset)
    {
        return unsafe.getObject( o, offset ) ;
    }

    /**
     * @see #putInt(Object, long, int)
     */
    public final void putObject(Object o, long offset, Object x)
    {
        unsafe.putObject( o, offset, x ) ;
    }

    /** @see #getInt(Object, long) */
    public final boolean getBoolean(Object o, long offset)
    {
        return unsafe.getBoolean( o, offset ) ;
    }
    /** @see #putInt(Object, long, int) */
    public final void    putBoolean(Object o, long offset, boolean x)
    {
        unsafe.putBoolean( o, offset, x ) ;
    }
    /** @see #getInt(Object, long) */
    public final byte    getByte(Object o, long offset)
    {
        return unsafe.getByte( o, offset ) ;
    }
    /** @see #putInt(Object, long, int) */
    public final void    putByte(Object o, long offset, byte x)
    {
        unsafe.putByte( o, offset, x ) ;
    }
    /** @see #getInt(Object, long) */
    public final short   getShort(Object o, long offset)
    {
        return unsafe.getShort( o, offset ) ;
    }
    /** @see #putInt(Object, long, int) */
    public final void    putShort(Object o, long offset, short x)
    {
        unsafe.putShort( o, offset, x ) ;
    }
    /** @see #getInt(Object, long) */
    public final char    getChar(Object o, long offset)
    {
        return unsafe.getChar( o, offset ) ;
    }
    /** @see #putInt(Object, long, int) */
    public final void    putChar(Object o, long offset, char x)
    {
        unsafe.putChar( o, offset, x ) ;
    }
    /** @see #getInt(Object, long) */
    public final long    getLong(Object o, long offset)
    {
        return unsafe.getLong( o, offset ) ;
    }
    /** @see #putInt(Object, long, int) */
    public final void    putLong(Object o, long offset, long x)
    {
        unsafe.putLong( o, offset, x ) ;
    }
    /** @see #getInt(Object, long) */
    public final float   getFloat(Object o, long offset)
    {
        return unsafe.getFloat( o, offset ) ;
    }
    /** @see #putInt(Object, long, int) */
    public final void    putFloat(Object o, long offset, float x)
    {
        unsafe.putFloat( o, offset, x ) ;
    }
    /** @see #getInt(Object, long) */
    public final double  getDouble(Object o, long offset)
    {
        return unsafe.getDouble( o, offset ) ;
    }
    /** @see #putInt(Object, long, int) */
    public final void    putDouble(Object o, long offset, double x)
    {
        unsafe.putDouble( o, offset, x ) ;
    }

    /**
     * This constant differs from all results that will ever be returned from
     * {@link #objectFieldOffset}.
     */
    public static final long INVALID_FIELD_OFFSET   = -1;

    /**
     * Returns the offset of a non-static field.
     */
    public final long objectFieldOffset(Field f)
    {
        return unsafe.objectFieldOffset( f ) ;
    }

    /**
     * Returns the offset of a static field.
     */
    public final long staticFieldOffset(Field f)
    {
        return unsafe.staticFieldOffset( f ) ;
    }

    /**
     * Ensure that the class has been initalized.
     * @param cl the class to ensure is initialized
     */
    public final void ensureClassInitialized(Class<?> cl) {
        unsafe.ensureClassInitialized(cl);
    }


    /** Throw the exception.
     * The exception may be an undeclared checked exception.
     */
    public final void throwException(Throwable ee)
    {
        unsafe.throwException( ee ) ;
    }

    /**
     * Obtain a constructor for Class cl.
     * This is used to create a constructor for Serializable classes that
     * construct an instance of the Serializable class using the
     * no args constructor of the first non-Serializable superclass
     * of the Serializable class.
     */
    public final Constructor<?> newConstructorForSerialization( Class<?> cl ) {
        return reflectionFactory.newConstructorForSerialization( cl ) ;
    }

    public final Constructor<?> newConstructorForExternalization(Class<?> cl) {
        return reflectionFactory.newConstructorForExternalization( cl ) ;
    }

    /**
     * Invokes the supplied constructor, adding the provided protection domains
     * to the invocation stack before invoking {@code Constructor::newInstance}.
     *
     * This is equivalent to calling
     * {@code ReflectionFactory.newInstanceForSerialization(cons,domains)}.
     *
     * @param cons A constructor obtained from {@code
     *        newConstructorForSerialization} or {@code
     *        newConstructorForExternalization}.
     *
     * @param domains An array of protection domains that limit the privileges
     *        with which the constructor is invoked. Can be {@code null}
     *        or empty, in which case privileges are only limited by the
     *        {@linkplain AccessController#getContext() current context}.
     *
     * @return A new object built from the provided constructor.
     *
     * @throws NullPointerException if {@code cons} is {@code null}.
     * @throws InstantiationException if thrown by {@code cons.newInstance()}.
     * @throws InvocationTargetException if thrown by {@code cons.newInstance()}.
     * @throws IllegalAccessException if thrown by {@code cons.newInstance()}.
     */
    public final Object newInstanceForSerialization(Constructor<?> cons,
                                                    ProtectionDomain[] domains)
        throws InstantiationException, InvocationTargetException, IllegalAccessException
    {
        return reflectionFactory.newInstanceForSerialization(cons, domains);
    }

    /**
     * Returns true if the given class defines a static initializer method,
     * false otherwise.
     */
    public final boolean hasStaticInitializerForSerialization(Class<?> cl) {
        return reflectionFactory.hasStaticInitializerForSerialization(cl);
    }

    public final MethodHandle writeObjectForSerialization(Class<?> cl) {
        return reflectionFactory.writeObjectForSerialization(cl);
    }

    public final MethodHandle readObjectForSerialization(Class<?> cl) {
        return reflectionFactory.readObjectForSerialization(cl);
    }

    public final MethodHandle readObjectNoDataForSerialization(Class<?> cl) {
        return reflectionFactory.readObjectNoDataForSerialization(cl);
    }

    public final MethodHandle readResolveForSerialization(Class<?> cl) {
        return reflectionFactory.readResolveForSerialization(cl);
    }

    public final MethodHandle writeReplaceForSerialization(Class<?> cl) {
        return reflectionFactory.writeReplaceForSerialization(cl);
    }

    /**
     * Return a new OptionalDataException instance.
     * @return a new OptionalDataException instance
     */
    public final OptionalDataException newOptionalDataExceptionForSerialization(boolean bool) {
        return reflectionFactory.newOptionalDataExceptionForSerialization(bool);
    }

}
