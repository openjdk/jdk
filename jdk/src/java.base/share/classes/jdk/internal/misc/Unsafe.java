/*
 * Copyright (c) 2000, 2015, Oracle and/or its affiliates. All rights reserved.
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

package jdk.internal.misc;

import java.lang.reflect.Field;
import java.security.ProtectionDomain;

import sun.reflect.CallerSensitive;
import sun.reflect.Reflection;
import jdk.internal.misc.VM;

import jdk.internal.HotSpotIntrinsicCandidate;


/**
 * A collection of methods for performing low-level, unsafe operations.
 * Although the class and all methods are public, use of this class is
 * limited because only trusted code can obtain instances of it.
 *
 * @author John R. Rose
 * @see #getUnsafe
 */

public final class Unsafe {

    private static native void registerNatives();
    static {
        registerNatives();
        sun.reflect.Reflection.registerMethodsToFilter(Unsafe.class, "getUnsafe");
    }

    private Unsafe() {}

    private static final Unsafe theUnsafe = new Unsafe();

    /**
     * Provides the caller with the capability of performing unsafe
     * operations.
     *
     * <p>The returned {@code Unsafe} object should be carefully guarded
     * by the caller, since it can be used to read and write data at arbitrary
     * memory addresses.  It must never be passed to untrusted code.
     *
     * <p>Most methods in this class are very low-level, and correspond to a
     * small number of hardware instructions (on typical machines).  Compilers
     * are encouraged to optimize these methods accordingly.
     *
     * <p>Here is a suggested idiom for using unsafe operations:
     *
     * <pre> {@code
     * class MyTrustedClass {
     *   private static final Unsafe unsafe = Unsafe.getUnsafe();
     *   ...
     *   private long myCountAddress = ...;
     *   public int getCount() { return unsafe.getByte(myCountAddress); }
     * }}</pre>
     *
     * (It may assist compilers to make the local variable {@code final}.)
     *
     * @throws  SecurityException  if a security manager exists and its
     *          {@code checkPropertiesAccess} method doesn't allow
     *          access to the system properties.
     */
    @CallerSensitive
    public static Unsafe getUnsafe() {
        Class<?> caller = Reflection.getCallerClass();
        if (!VM.isSystemDomainLoader(caller.getClassLoader()))
            throw new SecurityException("Unsafe");
        return theUnsafe;
    }

    /// peek and poke operations
    /// (compilers should optimize these to memory ops)

    // These work on object fields in the Java heap.
    // They will not work on elements of packed arrays.

    /**
     * Fetches a value from a given Java variable.
     * More specifically, fetches a field or array element within the given
     * object {@code o} at the given offset, or (if {@code o} is null)
     * from the memory address whose numerical value is the given offset.
     * <p>
     * The results are undefined unless one of the following cases is true:
     * <ul>
     * <li>The offset was obtained from {@link #objectFieldOffset} on
     * the {@link java.lang.reflect.Field} of some Java field and the object
     * referred to by {@code o} is of a class compatible with that
     * field's class.
     *
     * <li>The offset and object reference {@code o} (either null or
     * non-null) were both obtained via {@link #staticFieldOffset}
     * and {@link #staticFieldBase} (respectively) from the
     * reflective {@link Field} representation of some Java field.
     *
     * <li>The object referred to by {@code o} is an array, and the offset
     * is an integer of the form {@code B+N*S}, where {@code N} is
     * a valid index into the array, and {@code B} and {@code S} are
     * the values obtained by {@link #arrayBaseOffset} and {@link
     * #arrayIndexScale} (respectively) from the array's class.  The value
     * referred to is the {@code N}<em>th</em> element of the array.
     *
     * </ul>
     * <p>
     * If one of the above cases is true, the call references a specific Java
     * variable (field or array element).  However, the results are undefined
     * if that variable is not in fact of the type returned by this method.
     * <p>
     * This method refers to a variable by means of two parameters, and so
     * it provides (in effect) a <em>double-register</em> addressing mode
     * for Java variables.  When the object reference is null, this method
     * uses its offset as an absolute address.  This is similar in operation
     * to methods such as {@link #getInt(long)}, which provide (in effect) a
     * <em>single-register</em> addressing mode for non-Java variables.
     * However, because Java variables may have a different layout in memory
     * from non-Java variables, programmers should not assume that these
     * two addressing modes are ever equivalent.  Also, programmers should
     * remember that offsets from the double-register addressing mode cannot
     * be portably confused with longs used in the single-register addressing
     * mode.
     *
     * @param o Java heap object in which the variable resides, if any, else
     *        null
     * @param offset indication of where the variable resides in a Java heap
     *        object, if any, else a memory address locating the variable
     *        statically
     * @return the value fetched from the indicated Java variable
     * @throws RuntimeException No defined exceptions are thrown, not even
     *         {@link NullPointerException}
     */
    @HotSpotIntrinsicCandidate
    public native int getInt(Object o, long offset);

    /**
     * Stores a value into a given Java variable.
     * <p>
     * The first two parameters are interpreted exactly as with
     * {@link #getInt(Object, long)} to refer to a specific
     * Java variable (field or array element).  The given value
     * is stored into that variable.
     * <p>
     * The variable must be of the same type as the method
     * parameter {@code x}.
     *
     * @param o Java heap object in which the variable resides, if any, else
     *        null
     * @param offset indication of where the variable resides in a Java heap
     *        object, if any, else a memory address locating the variable
     *        statically
     * @param x the value to store into the indicated Java variable
     * @throws RuntimeException No defined exceptions are thrown, not even
     *         {@link NullPointerException}
     */
    @HotSpotIntrinsicCandidate
    public native void putInt(Object o, long offset, int x);

    /**
     * Fetches a reference value from a given Java variable.
     * @see #getInt(Object, long)
     */
    @HotSpotIntrinsicCandidate
    public native Object getObject(Object o, long offset);

    /**
     * Stores a reference value into a given Java variable.
     * <p>
     * Unless the reference {@code x} being stored is either null
     * or matches the field type, the results are undefined.
     * If the reference {@code o} is non-null, card marks or
     * other store barriers for that object (if the VM requires them)
     * are updated.
     * @see #putInt(Object, long, int)
     */
    @HotSpotIntrinsicCandidate
    public native void putObject(Object o, long offset, Object x);

    /** @see #getInt(Object, long) */
    @HotSpotIntrinsicCandidate
    public native boolean getBoolean(Object o, long offset);
    /** @see #putInt(Object, long, int) */
    @HotSpotIntrinsicCandidate
    public native void    putBoolean(Object o, long offset, boolean x);
    /** @see #getInt(Object, long) */
    @HotSpotIntrinsicCandidate
    public native byte    getByte(Object o, long offset);
    /** @see #putInt(Object, long, int) */
    @HotSpotIntrinsicCandidate
    public native void    putByte(Object o, long offset, byte x);
    /** @see #getInt(Object, long) */
    @HotSpotIntrinsicCandidate
    public native short   getShort(Object o, long offset);
    /** @see #putInt(Object, long, int) */
    @HotSpotIntrinsicCandidate
    public native void    putShort(Object o, long offset, short x);
    /** @see #getInt(Object, long) */
    @HotSpotIntrinsicCandidate
    public native char    getChar(Object o, long offset);
    /** @see #putInt(Object, long, int) */
    @HotSpotIntrinsicCandidate
    public native void    putChar(Object o, long offset, char x);
    /** @see #getInt(Object, long) */
    @HotSpotIntrinsicCandidate
    public native long    getLong(Object o, long offset);
    /** @see #putInt(Object, long, int) */
    @HotSpotIntrinsicCandidate
    public native void    putLong(Object o, long offset, long x);
    /** @see #getInt(Object, long) */
    @HotSpotIntrinsicCandidate
    public native float   getFloat(Object o, long offset);
    /** @see #putInt(Object, long, int) */
    @HotSpotIntrinsicCandidate
    public native void    putFloat(Object o, long offset, float x);
    /** @see #getInt(Object, long) */
    @HotSpotIntrinsicCandidate
    public native double  getDouble(Object o, long offset);
    /** @see #putInt(Object, long, int) */
    @HotSpotIntrinsicCandidate
    public native void    putDouble(Object o, long offset, double x);

    // These read VM internal data.

    /**
     * Fetches an uncompressed reference value from a given native variable
     * ignoring the VM's compressed references mode.
     *
     * @param address a memory address locating the variable
     * @return the value fetched from the indicated native variable
     */
    public native Object getUncompressedObject(long address);

    /**
     * Fetches the {@link java.lang.Class} Java mirror for the given native
     * metaspace {@code Klass} pointer.
     *
     * @param metaspaceKlass a native metaspace {@code Klass} pointer
     * @return the {@link java.lang.Class} Java mirror
     */
    public native Class<?> getJavaMirror(long metaspaceKlass);

    /**
     * Fetches a native metaspace {@code Klass} pointer for the given Java
     * object.
     *
     * @param o Java heap object for which to fetch the class pointer
     * @return a native metaspace {@code Klass} pointer
     */
    public native long getKlassPointer(Object o);

    // These work on values in the C heap.

    /**
     * Fetches a value from a given memory address.  If the address is zero, or
     * does not point into a block obtained from {@link #allocateMemory}, the
     * results are undefined.
     *
     * @see #allocateMemory
     */
    @HotSpotIntrinsicCandidate
    public native byte    getByte(long address);

    /**
     * Stores a value into a given memory address.  If the address is zero, or
     * does not point into a block obtained from {@link #allocateMemory}, the
     * results are undefined.
     *
     * @see #getByte(long)
     */
    @HotSpotIntrinsicCandidate
    public native void    putByte(long address, byte x);

    /** @see #getByte(long) */
    @HotSpotIntrinsicCandidate
    public native short   getShort(long address);
    /** @see #putByte(long, byte) */
    @HotSpotIntrinsicCandidate
    public native void    putShort(long address, short x);
    /** @see #getByte(long) */
    @HotSpotIntrinsicCandidate
    public native char    getChar(long address);
    /** @see #putByte(long, byte) */
    @HotSpotIntrinsicCandidate
    public native void    putChar(long address, char x);
    /** @see #getByte(long) */
    @HotSpotIntrinsicCandidate
    public native int     getInt(long address);
    /** @see #putByte(long, byte) */
    @HotSpotIntrinsicCandidate
    public native void    putInt(long address, int x);
    /** @see #getByte(long) */
    @HotSpotIntrinsicCandidate
    public native long    getLong(long address);
    /** @see #putByte(long, byte) */
    @HotSpotIntrinsicCandidate
    public native void    putLong(long address, long x);
    /** @see #getByte(long) */
    @HotSpotIntrinsicCandidate
    public native float   getFloat(long address);
    /** @see #putByte(long, byte) */
    @HotSpotIntrinsicCandidate
    public native void    putFloat(long address, float x);
    /** @see #getByte(long) */
    @HotSpotIntrinsicCandidate
    public native double  getDouble(long address);
    /** @see #putByte(long, byte) */
    @HotSpotIntrinsicCandidate
    public native void    putDouble(long address, double x);

    /**
     * Fetches a native pointer from a given memory address.  If the address is
     * zero, or does not point into a block obtained from {@link
     * #allocateMemory}, the results are undefined.
     *
     * <p>If the native pointer is less than 64 bits wide, it is extended as
     * an unsigned number to a Java long.  The pointer may be indexed by any
     * given byte offset, simply by adding that offset (as a simple integer) to
     * the long representing the pointer.  The number of bytes actually read
     * from the target address may be determined by consulting {@link
     * #addressSize}.
     *
     * @see #allocateMemory
     */
    @HotSpotIntrinsicCandidate
    public native long getAddress(long address);

    /**
     * Stores a native pointer into a given memory address.  If the address is
     * zero, or does not point into a block obtained from {@link
     * #allocateMemory}, the results are undefined.
     *
     * <p>The number of bytes actually written at the target address may be
     * determined by consulting {@link #addressSize}.
     *
     * @see #getAddress(long)
     */
    @HotSpotIntrinsicCandidate
    public native void putAddress(long address, long x);

    /// wrappers for malloc, realloc, free:

    /**
     * Allocates a new block of native memory, of the given size in bytes.  The
     * contents of the memory are uninitialized; they will generally be
     * garbage.  The resulting native pointer will never be zero, and will be
     * aligned for all value types.  Dispose of this memory by calling {@link
     * #freeMemory}, or resize it with {@link #reallocateMemory}.
     *
     * @throws IllegalArgumentException if the size is negative or too large
     *         for the native size_t type
     *
     * @throws OutOfMemoryError if the allocation is refused by the system
     *
     * @see #getByte(long)
     * @see #putByte(long, byte)
     */
    public native long allocateMemory(long bytes);

    /**
     * Resizes a new block of native memory, to the given size in bytes.  The
     * contents of the new block past the size of the old block are
     * uninitialized; they will generally be garbage.  The resulting native
     * pointer will be zero if and only if the requested size is zero.  The
     * resulting native pointer will be aligned for all value types.  Dispose
     * of this memory by calling {@link #freeMemory}, or resize it with {@link
     * #reallocateMemory}.  The address passed to this method may be null, in
     * which case an allocation will be performed.
     *
     * @throws IllegalArgumentException if the size is negative or too large
     *         for the native size_t type
     *
     * @throws OutOfMemoryError if the allocation is refused by the system
     *
     * @see #allocateMemory
     */
    public native long reallocateMemory(long address, long bytes);

    /**
     * Sets all bytes in a given block of memory to a fixed value
     * (usually zero).
     *
     * <p>This method determines a block's base address by means of two parameters,
     * and so it provides (in effect) a <em>double-register</em> addressing mode,
     * as discussed in {@link #getInt(Object,long)}.  When the object reference is null,
     * the offset supplies an absolute base address.
     *
     * <p>The stores are in coherent (atomic) units of a size determined
     * by the address and length parameters.  If the effective address and
     * length are all even modulo 8, the stores take place in 'long' units.
     * If the effective address and length are (resp.) even modulo 4 or 2,
     * the stores take place in units of 'int' or 'short'.
     *
     * @since 1.7
     */
    public native void setMemory(Object o, long offset, long bytes, byte value);

    /**
     * Sets all bytes in a given block of memory to a fixed value
     * (usually zero).  This provides a <em>single-register</em> addressing mode,
     * as discussed in {@link #getInt(Object,long)}.
     *
     * <p>Equivalent to {@code setMemory(null, address, bytes, value)}.
     */
    public void setMemory(long address, long bytes, byte value) {
        setMemory(null, address, bytes, value);
    }

    /**
     * Sets all bytes in a given block of memory to a copy of another
     * block.
     *
     * <p>This method determines each block's base address by means of two parameters,
     * and so it provides (in effect) a <em>double-register</em> addressing mode,
     * as discussed in {@link #getInt(Object,long)}.  When the object reference is null,
     * the offset supplies an absolute base address.
     *
     * <p>The transfers are in coherent (atomic) units of a size determined
     * by the address and length parameters.  If the effective addresses and
     * length are all even modulo 8, the transfer takes place in 'long' units.
     * If the effective addresses and length are (resp.) even modulo 4 or 2,
     * the transfer takes place in units of 'int' or 'short'.
     *
     * @since 1.7
     */
    @HotSpotIntrinsicCandidate
    public native void copyMemory(Object srcBase, long srcOffset,
                                  Object destBase, long destOffset,
                                  long bytes);
    /**
     * Sets all bytes in a given block of memory to a copy of another
     * block.  This provides a <em>single-register</em> addressing mode,
     * as discussed in {@link #getInt(Object,long)}.
     *
     * Equivalent to {@code copyMemory(null, srcAddress, null, destAddress, bytes)}.
     */
    public void copyMemory(long srcAddress, long destAddress, long bytes) {
        copyMemory(null, srcAddress, null, destAddress, bytes);
    }

    /**
     * Disposes of a block of native memory, as obtained from {@link
     * #allocateMemory} or {@link #reallocateMemory}.  The address passed to
     * this method may be null, in which case no action is taken.
     *
     * @see #allocateMemory
     */
    public native void freeMemory(long address);

    /// random queries

    /**
     * This constant differs from all results that will ever be returned from
     * {@link #staticFieldOffset}, {@link #objectFieldOffset},
     * or {@link #arrayBaseOffset}.
     */
    public static final int INVALID_FIELD_OFFSET   = -1;

    /**
     * Reports the location of a given field in the storage allocation of its
     * class.  Do not expect to perform any sort of arithmetic on this offset;
     * it is just a cookie which is passed to the unsafe heap memory accessors.
     *
     * <p>Any given field will always have the same offset and base, and no
     * two distinct fields of the same class will ever have the same offset
     * and base.
     *
     * <p>As of 1.4.1, offsets for fields are represented as long values,
     * although the Sun JVM does not use the most significant 32 bits.
     * However, JVM implementations which store static fields at absolute
     * addresses can use long offsets and null base pointers to express
     * the field locations in a form usable by {@link #getInt(Object,long)}.
     * Therefore, code which will be ported to such JVMs on 64-bit platforms
     * must preserve all bits of static field offsets.
     * @see #getInt(Object, long)
     */
    public native long objectFieldOffset(Field f);

    /**
     * Reports the location of a given static field, in conjunction with {@link
     * #staticFieldBase}.
     * <p>Do not expect to perform any sort of arithmetic on this offset;
     * it is just a cookie which is passed to the unsafe heap memory accessors.
     *
     * <p>Any given field will always have the same offset, and no two distinct
     * fields of the same class will ever have the same offset.
     *
     * <p>As of 1.4.1, offsets for fields are represented as long values,
     * although the Sun JVM does not use the most significant 32 bits.
     * It is hard to imagine a JVM technology which needs more than
     * a few bits to encode an offset within a non-array object,
     * However, for consistency with other methods in this class,
     * this method reports its result as a long value.
     * @see #getInt(Object, long)
     */
    public native long staticFieldOffset(Field f);

    /**
     * Reports the location of a given static field, in conjunction with {@link
     * #staticFieldOffset}.
     * <p>Fetch the base "Object", if any, with which static fields of the
     * given class can be accessed via methods like {@link #getInt(Object,
     * long)}.  This value may be null.  This value may refer to an object
     * which is a "cookie", not guaranteed to be a real Object, and it should
     * not be used in any way except as argument to the get and put routines in
     * this class.
     */
    public native Object staticFieldBase(Field f);

    /**
     * Detects if the given class may need to be initialized. This is often
     * needed in conjunction with obtaining the static field base of a
     * class.
     * @return false only if a call to {@code ensureClassInitialized} would have no effect
     */
    public native boolean shouldBeInitialized(Class<?> c);

    /**
     * Ensures the given class has been initialized. This is often
     * needed in conjunction with obtaining the static field base of a
     * class.
     */
    public native void ensureClassInitialized(Class<?> c);

    /**
     * Reports the offset of the first element in the storage allocation of a
     * given array class.  If {@link #arrayIndexScale} returns a non-zero value
     * for the same class, you may use that scale factor, together with this
     * base offset, to form new offsets to access elements of arrays of the
     * given class.
     *
     * @see #getInt(Object, long)
     * @see #putInt(Object, long, int)
     */
    public native int arrayBaseOffset(Class<?> arrayClass);

    /** The value of {@code arrayBaseOffset(boolean[].class)} */
    public static final int ARRAY_BOOLEAN_BASE_OFFSET
            = theUnsafe.arrayBaseOffset(boolean[].class);

    /** The value of {@code arrayBaseOffset(byte[].class)} */
    public static final int ARRAY_BYTE_BASE_OFFSET
            = theUnsafe.arrayBaseOffset(byte[].class);

    /** The value of {@code arrayBaseOffset(short[].class)} */
    public static final int ARRAY_SHORT_BASE_OFFSET
            = theUnsafe.arrayBaseOffset(short[].class);

    /** The value of {@code arrayBaseOffset(char[].class)} */
    public static final int ARRAY_CHAR_BASE_OFFSET
            = theUnsafe.arrayBaseOffset(char[].class);

    /** The value of {@code arrayBaseOffset(int[].class)} */
    public static final int ARRAY_INT_BASE_OFFSET
            = theUnsafe.arrayBaseOffset(int[].class);

    /** The value of {@code arrayBaseOffset(long[].class)} */
    public static final int ARRAY_LONG_BASE_OFFSET
            = theUnsafe.arrayBaseOffset(long[].class);

    /** The value of {@code arrayBaseOffset(float[].class)} */
    public static final int ARRAY_FLOAT_BASE_OFFSET
            = theUnsafe.arrayBaseOffset(float[].class);

    /** The value of {@code arrayBaseOffset(double[].class)} */
    public static final int ARRAY_DOUBLE_BASE_OFFSET
            = theUnsafe.arrayBaseOffset(double[].class);

    /** The value of {@code arrayBaseOffset(Object[].class)} */
    public static final int ARRAY_OBJECT_BASE_OFFSET
            = theUnsafe.arrayBaseOffset(Object[].class);

    /**
     * Reports the scale factor for addressing elements in the storage
     * allocation of a given array class.  However, arrays of "narrow" types
     * will generally not work properly with accessors like {@link
     * #getByte(Object, long)}, so the scale factor for such classes is reported
     * as zero.
     *
     * @see #arrayBaseOffset
     * @see #getInt(Object, long)
     * @see #putInt(Object, long, int)
     */
    public native int arrayIndexScale(Class<?> arrayClass);

    /** The value of {@code arrayIndexScale(boolean[].class)} */
    public static final int ARRAY_BOOLEAN_INDEX_SCALE
            = theUnsafe.arrayIndexScale(boolean[].class);

    /** The value of {@code arrayIndexScale(byte[].class)} */
    public static final int ARRAY_BYTE_INDEX_SCALE
            = theUnsafe.arrayIndexScale(byte[].class);

    /** The value of {@code arrayIndexScale(short[].class)} */
    public static final int ARRAY_SHORT_INDEX_SCALE
            = theUnsafe.arrayIndexScale(short[].class);

    /** The value of {@code arrayIndexScale(char[].class)} */
    public static final int ARRAY_CHAR_INDEX_SCALE
            = theUnsafe.arrayIndexScale(char[].class);

    /** The value of {@code arrayIndexScale(int[].class)} */
    public static final int ARRAY_INT_INDEX_SCALE
            = theUnsafe.arrayIndexScale(int[].class);

    /** The value of {@code arrayIndexScale(long[].class)} */
    public static final int ARRAY_LONG_INDEX_SCALE
            = theUnsafe.arrayIndexScale(long[].class);

    /** The value of {@code arrayIndexScale(float[].class)} */
    public static final int ARRAY_FLOAT_INDEX_SCALE
            = theUnsafe.arrayIndexScale(float[].class);

    /** The value of {@code arrayIndexScale(double[].class)} */
    public static final int ARRAY_DOUBLE_INDEX_SCALE
            = theUnsafe.arrayIndexScale(double[].class);

    /** The value of {@code arrayIndexScale(Object[].class)} */
    public static final int ARRAY_OBJECT_INDEX_SCALE
            = theUnsafe.arrayIndexScale(Object[].class);

    /**
     * Reports the size in bytes of a native pointer, as stored via {@link
     * #putAddress}.  This value will be either 4 or 8.  Note that the sizes of
     * other primitive types (as stored in native memory blocks) is determined
     * fully by their information content.
     */
    public native int addressSize();

    /** The value of {@code addressSize()} */
    public static final int ADDRESS_SIZE = theUnsafe.addressSize();

    /**
     * Reports the size in bytes of a native memory page (whatever that is).
     * This value will always be a power of two.
     */
    public native int pageSize();


    /// random trusted operations from JNI:

    /**
     * Tells the VM to define a class, without security checks.  By default, the
     * class loader and protection domain come from the caller's class.
     */
    public native Class<?> defineClass(String name, byte[] b, int off, int len,
                                       ClassLoader loader,
                                       ProtectionDomain protectionDomain);

    /**
     * Defines a class but does not make it known to the class loader or system dictionary.
     * <p>
     * For each CP entry, the corresponding CP patch must either be null or have
     * the a format that matches its tag:
     * <ul>
     * <li>Integer, Long, Float, Double: the corresponding wrapper object type from java.lang
     * <li>Utf8: a string (must have suitable syntax if used as signature or name)
     * <li>Class: any java.lang.Class object
     * <li>String: any object (not just a java.lang.String)
     * <li>InterfaceMethodRef: (NYI) a method handle to invoke on that call site's arguments
     * </ul>
     * @param hostClass context for linkage, access control, protection domain, and class loader
     * @param data      bytes of a class file
     * @param cpPatches where non-null entries exist, they replace corresponding CP entries in data
     */
    public native Class<?> defineAnonymousClass(Class<?> hostClass, byte[] data, Object[] cpPatches);

    /**
     * Allocates an instance but does not run any constructor.
     * Initializes the class if it has not yet been.
     */
    @HotSpotIntrinsicCandidate
    public native Object allocateInstance(Class<?> cls)
        throws InstantiationException;

    /** Throws the exception without telling the verifier. */
    public native void throwException(Throwable ee);

    /**
     * Atomically updates Java variable to {@code x} if it is currently
     * holding {@code expected}.
     *
     * <p>This operation has memory semantics of a {@code volatile} read
     * and write.  Corresponds to C11 atomic_compare_exchange_strong.
     *
     * @return {@code true} if successful
     */
    @HotSpotIntrinsicCandidate
    public final native boolean compareAndSwapObject(Object o, long offset,
                                                     Object expected,
                                                     Object x);

    /**
     * Atomically updates Java variable to {@code x} if it is currently
     * holding {@code expected}.
     *
     * <p>This operation has memory semantics of a {@code volatile} read
     * and write.  Corresponds to C11 atomic_compare_exchange_strong.
     *
     * @return {@code true} if successful
     */
    @HotSpotIntrinsicCandidate
    public final native boolean compareAndSwapInt(Object o, long offset,
                                                  int expected,
                                                  int x);

    /**
     * Atomically updates Java variable to {@code x} if it is currently
     * holding {@code expected}.
     *
     * <p>This operation has memory semantics of a {@code volatile} read
     * and write.  Corresponds to C11 atomic_compare_exchange_strong.
     *
     * @return {@code true} if successful
     */
    @HotSpotIntrinsicCandidate
    public final native boolean compareAndSwapLong(Object o, long offset,
                                                   long expected,
                                                   long x);

    /**
     * Fetches a reference value from a given Java variable, with volatile
     * load semantics. Otherwise identical to {@link #getObject(Object, long)}
     */
    @HotSpotIntrinsicCandidate
    public native Object getObjectVolatile(Object o, long offset);

    /**
     * Stores a reference value into a given Java variable, with
     * volatile store semantics. Otherwise identical to {@link #putObject(Object, long, Object)}
     */
    @HotSpotIntrinsicCandidate
    public native void    putObjectVolatile(Object o, long offset, Object x);

    /** Volatile version of {@link #getInt(Object, long)}  */
    @HotSpotIntrinsicCandidate
    public native int     getIntVolatile(Object o, long offset);

    /** Volatile version of {@link #putInt(Object, long, int)}  */
    @HotSpotIntrinsicCandidate
    public native void    putIntVolatile(Object o, long offset, int x);

    /** Volatile version of {@link #getBoolean(Object, long)}  */
    @HotSpotIntrinsicCandidate
    public native boolean getBooleanVolatile(Object o, long offset);

    /** Volatile version of {@link #putBoolean(Object, long, boolean)}  */
    @HotSpotIntrinsicCandidate
    public native void    putBooleanVolatile(Object o, long offset, boolean x);

    /** Volatile version of {@link #getByte(Object, long)}  */
    @HotSpotIntrinsicCandidate
    public native byte    getByteVolatile(Object o, long offset);

    /** Volatile version of {@link #putByte(Object, long, byte)}  */
    @HotSpotIntrinsicCandidate
    public native void    putByteVolatile(Object o, long offset, byte x);

    /** Volatile version of {@link #getShort(Object, long)}  */
    @HotSpotIntrinsicCandidate
    public native short   getShortVolatile(Object o, long offset);

    /** Volatile version of {@link #putShort(Object, long, short)}  */
    @HotSpotIntrinsicCandidate
    public native void    putShortVolatile(Object o, long offset, short x);

    /** Volatile version of {@link #getChar(Object, long)}  */
    @HotSpotIntrinsicCandidate
    public native char    getCharVolatile(Object o, long offset);

    /** Volatile version of {@link #putChar(Object, long, char)}  */
    @HotSpotIntrinsicCandidate
    public native void    putCharVolatile(Object o, long offset, char x);

    /** Volatile version of {@link #getLong(Object, long)}  */
    @HotSpotIntrinsicCandidate
    public native long    getLongVolatile(Object o, long offset);

    /** Volatile version of {@link #putLong(Object, long, long)}  */
    @HotSpotIntrinsicCandidate
    public native void    putLongVolatile(Object o, long offset, long x);

    /** Volatile version of {@link #getFloat(Object, long)}  */
    @HotSpotIntrinsicCandidate
    public native float   getFloatVolatile(Object o, long offset);

    /** Volatile version of {@link #putFloat(Object, long, float)}  */
    @HotSpotIntrinsicCandidate
    public native void    putFloatVolatile(Object o, long offset, float x);

    /** Volatile version of {@link #getDouble(Object, long)}  */
    @HotSpotIntrinsicCandidate
    public native double  getDoubleVolatile(Object o, long offset);

    /** Volatile version of {@link #putDouble(Object, long, double)}  */
    @HotSpotIntrinsicCandidate
    public native void    putDoubleVolatile(Object o, long offset, double x);

    /**
     * Version of {@link #putObjectVolatile(Object, long, Object)}
     * that does not guarantee immediate visibility of the store to
     * other threads. This method is generally only useful if the
     * underlying field is a Java volatile (or if an array cell, one
     * that is otherwise only accessed using volatile accesses).
     *
     * Corresponds to C11 atomic_store_explicit(..., memory_order_release).
     */
    @HotSpotIntrinsicCandidate
    public native void    putOrderedObject(Object o, long offset, Object x);

    /** Ordered/Lazy version of {@link #putIntVolatile(Object, long, int)}  */
    @HotSpotIntrinsicCandidate
    public native void    putOrderedInt(Object o, long offset, int x);

    /** Ordered/Lazy version of {@link #putLongVolatile(Object, long, long)} */
    @HotSpotIntrinsicCandidate
    public native void    putOrderedLong(Object o, long offset, long x);

    /**
     * Unblocks the given thread blocked on {@code park}, or, if it is
     * not blocked, causes the subsequent call to {@code park} not to
     * block.  Note: this operation is "unsafe" solely because the
     * caller must somehow ensure that the thread has not been
     * destroyed. Nothing special is usually required to ensure this
     * when called from Java (in which there will ordinarily be a live
     * reference to the thread) but this is not nearly-automatically
     * so when calling from native code.
     *
     * @param thread the thread to unpark.
     */
    @HotSpotIntrinsicCandidate
    public native void unpark(Object thread);

    /**
     * Blocks current thread, returning when a balancing
     * {@code unpark} occurs, or a balancing {@code unpark} has
     * already occurred, or the thread is interrupted, or, if not
     * absolute and time is not zero, the given time nanoseconds have
     * elapsed, or if absolute, the given deadline in milliseconds
     * since Epoch has passed, or spuriously (i.e., returning for no
     * "reason"). Note: This operation is in the Unsafe class only
     * because {@code unpark} is, so it would be strange to place it
     * elsewhere.
     */
    @HotSpotIntrinsicCandidate
    public native void park(boolean isAbsolute, long time);

    /**
     * Gets the load average in the system run queue assigned
     * to the available processors averaged over various periods of time.
     * This method retrieves the given {@code nelem} samples and
     * assigns to the elements of the given {@code loadavg} array.
     * The system imposes a maximum of 3 samples, representing
     * averages over the last 1,  5,  and  15 minutes, respectively.
     *
     * @param loadavg an array of double of size nelems
     * @param nelems the number of samples to be retrieved and
     *        must be 1 to 3.
     *
     * @return the number of samples actually retrieved; or -1
     *         if the load average is unobtainable.
     */
    public native int getLoadAverage(double[] loadavg, int nelems);

    // The following contain CAS-based Java implementations used on
    // platforms not supporting native instructions

    /**
     * Atomically adds the given value to the current value of a field
     * or array element within the given object {@code o}
     * at the given {@code offset}.
     *
     * @param o object/array to update the field/element in
     * @param offset field/element offset
     * @param delta the value to add
     * @return the previous value
     * @since 1.8
     */
    @HotSpotIntrinsicCandidate
    public final int getAndAddInt(Object o, long offset, int delta) {
        int v;
        do {
            v = getIntVolatile(o, offset);
        } while (!compareAndSwapInt(o, offset, v, v + delta));
        return v;
    }

    /**
     * Atomically adds the given value to the current value of a field
     * or array element within the given object {@code o}
     * at the given {@code offset}.
     *
     * @param o object/array to update the field/element in
     * @param offset field/element offset
     * @param delta the value to add
     * @return the previous value
     * @since 1.8
     */
    @HotSpotIntrinsicCandidate
    public final long getAndAddLong(Object o, long offset, long delta) {
        long v;
        do {
            v = getLongVolatile(o, offset);
        } while (!compareAndSwapLong(o, offset, v, v + delta));
        return v;
    }

    /**
     * Atomically exchanges the given value with the current value of
     * a field or array element within the given object {@code o}
     * at the given {@code offset}.
     *
     * @param o object/array to update the field/element in
     * @param offset field/element offset
     * @param newValue new value
     * @return the previous value
     * @since 1.8
     */
    @HotSpotIntrinsicCandidate
    public final int getAndSetInt(Object o, long offset, int newValue) {
        int v;
        do {
            v = getIntVolatile(o, offset);
        } while (!compareAndSwapInt(o, offset, v, newValue));
        return v;
    }

    /**
     * Atomically exchanges the given value with the current value of
     * a field or array element within the given object {@code o}
     * at the given {@code offset}.
     *
     * @param o object/array to update the field/element in
     * @param offset field/element offset
     * @param newValue new value
     * @return the previous value
     * @since 1.8
     */
    @HotSpotIntrinsicCandidate
    public final long getAndSetLong(Object o, long offset, long newValue) {
        long v;
        do {
            v = getLongVolatile(o, offset);
        } while (!compareAndSwapLong(o, offset, v, newValue));
        return v;
    }

    /**
     * Atomically exchanges the given reference value with the current
     * reference value of a field or array element within the given
     * object {@code o} at the given {@code offset}.
     *
     * @param o object/array to update the field/element in
     * @param offset field/element offset
     * @param newValue new value
     * @return the previous value
     * @since 1.8
     */
    @HotSpotIntrinsicCandidate
    public final Object getAndSetObject(Object o, long offset, Object newValue) {
        Object v;
        do {
            v = getObjectVolatile(o, offset);
        } while (!compareAndSwapObject(o, offset, v, newValue));
        return v;
    }


    /**
     * Ensures that loads before the fence will not be reordered with loads and
     * stores after the fence; a "LoadLoad plus LoadStore barrier".
     *
     * Corresponds to C11 atomic_thread_fence(memory_order_acquire)
     * (an "acquire fence").
     *
     * A pure LoadLoad fence is not provided, since the addition of LoadStore
     * is almost always desired, and most current hardware instructions that
     * provide a LoadLoad barrier also provide a LoadStore barrier for free.
     * @since 1.8
     */
    @HotSpotIntrinsicCandidate
    public native void loadFence();

    /**
     * Ensures that loads and stores before the fence will not be reordered with
     * stores after the fence; a "StoreStore plus LoadStore barrier".
     *
     * Corresponds to C11 atomic_thread_fence(memory_order_release)
     * (a "release fence").
     *
     * A pure StoreStore fence is not provided, since the addition of LoadStore
     * is almost always desired, and most current hardware instructions that
     * provide a StoreStore barrier also provide a LoadStore barrier for free.
     * @since 1.8
     */
    @HotSpotIntrinsicCandidate
    public native void storeFence();

    /**
     * Ensures that loads and stores before the fence will not be reordered
     * with loads and stores after the fence.  Implies the effects of both
     * loadFence() and storeFence(), and in addition, the effect of a StoreLoad
     * barrier.
     *
     * Corresponds to C11 atomic_thread_fence(memory_order_seq_cst).
     * @since 1.8
     */
    @HotSpotIntrinsicCandidate
    public native void fullFence();

    /**
     * Throws IllegalAccessError; for use by the VM for access control
     * error support.
     * @since 1.8
     */
    private static void throwIllegalAccessError() {
        throw new IllegalAccessError();
    }

    /**
     * @return Returns true if the native byte ordering of this
     * platform is big-endian, false if it is little-endian.
     */
    public final boolean isBigEndian() { return BE; }

    /**
     * @return Returns true if this platform is capable of performing
     * accesses at addresses which are not aligned for the type of the
     * primitive type being accessed, false otherwise.
     */
    public final boolean unalignedAccess() { return unalignedAccess; }

    /**
     * Fetches a value at some byte offset into a given Java object.
     * More specifically, fetches a value within the given object
     * <code>o</code> at the given offset, or (if <code>o</code> is
     * null) from the memory address whose numerical value is the
     * given offset.  <p>
     *
     * The specification of this method is the same as {@link
     * #getLong(Object, long)} except that the offset does not need to
     * have been obtained from {@link #objectFieldOffset} on the
     * {@link java.lang.reflect.Field} of some Java field.  The value
     * in memory is raw data, and need not correspond to any Java
     * variable.  Unless <code>o</code> is null, the value accessed
     * must be entirely within the allocated object.  The endianness
     * of the value in memory is the endianness of the native platform.
     *
     * <p> The read will be atomic with respect to the largest power
     * of two that divides the GCD of the offset and the storage size.
     * For example, getLongUnaligned will make atomic reads of 2-, 4-,
     * or 8-byte storage units if the offset is zero mod 2, 4, or 8,
     * respectively.  There are no other guarantees of atomicity.
     * <p>
     * 8-byte atomicity is only guaranteed on platforms on which
     * support atomic accesses to longs.
     *
     * @param o Java heap object in which the value resides, if any, else
     *        null
     * @param offset The offset in bytes from the start of the object
     * @return the value fetched from the indicated object
     * @throws RuntimeException No defined exceptions are thrown, not even
     *         {@link NullPointerException}
     * @since 9
     */
    @HotSpotIntrinsicCandidate
    public final long getLongUnaligned(Object o, long offset) {
        if ((offset & 7) == 0) {
            return getLong(o, offset);
        } else if ((offset & 3) == 0) {
            return makeLong(getInt(o, offset),
                            getInt(o, offset + 4));
        } else if ((offset & 1) == 0) {
            return makeLong(getShort(o, offset),
                            getShort(o, offset + 2),
                            getShort(o, offset + 4),
                            getShort(o, offset + 6));
        } else {
            return makeLong(getByte(o, offset),
                            getByte(o, offset + 1),
                            getByte(o, offset + 2),
                            getByte(o, offset + 3),
                            getByte(o, offset + 4),
                            getByte(o, offset + 5),
                            getByte(o, offset + 6),
                            getByte(o, offset + 7));
        }
    }
    /**
     * As {@link #getLongUnaligned(Object, long)} but with an
     * additional argument which specifies the endianness of the value
     * as stored in memory.
     *
     * @param o Java heap object in which the variable resides
     * @param offset The offset in bytes from the start of the object
     * @param bigEndian The endianness of the value
     * @return the value fetched from the indicated object
     * @since 9
     */
    public final long getLongUnaligned(Object o, long offset, boolean bigEndian) {
        return convEndian(bigEndian, getLongUnaligned(o, offset));
    }

    /** @see #getLongUnaligned(Object, long) */
    @HotSpotIntrinsicCandidate
    public final int getIntUnaligned(Object o, long offset) {
        if ((offset & 3) == 0) {
            return getInt(o, offset);
        } else if ((offset & 1) == 0) {
            return makeInt(getShort(o, offset),
                           getShort(o, offset + 2));
        } else {
            return makeInt(getByte(o, offset),
                           getByte(o, offset + 1),
                           getByte(o, offset + 2),
                           getByte(o, offset + 3));
        }
    }
    /** @see #getLongUnaligned(Object, long, boolean) */
    public final int getIntUnaligned(Object o, long offset, boolean bigEndian) {
        return convEndian(bigEndian, getIntUnaligned(o, offset));
    }

    /** @see #getLongUnaligned(Object, long) */
    @HotSpotIntrinsicCandidate
    public final short getShortUnaligned(Object o, long offset) {
        if ((offset & 1) == 0) {
            return getShort(o, offset);
        } else {
            return makeShort(getByte(o, offset),
                             getByte(o, offset + 1));
        }
    }
    /** @see #getLongUnaligned(Object, long, boolean) */
    public final short getShortUnaligned(Object o, long offset, boolean bigEndian) {
        return convEndian(bigEndian, getShortUnaligned(o, offset));
    }

    /** @see #getLongUnaligned(Object, long) */
    @HotSpotIntrinsicCandidate
    public final char getCharUnaligned(Object o, long offset) {
        if ((offset & 1) == 0) {
            return getChar(o, offset);
        } else {
            return (char)makeShort(getByte(o, offset),
                                   getByte(o, offset + 1));
        }
    }

    /** @see #getLongUnaligned(Object, long, boolean) */
    public final char getCharUnaligned(Object o, long offset, boolean bigEndian) {
        return convEndian(bigEndian, getCharUnaligned(o, offset));
    }

    /**
     * Stores a value at some byte offset into a given Java object.
     * <p>
     * The specification of this method is the same as {@link
     * #getLong(Object, long)} except that the offset does not need to
     * have been obtained from {@link #objectFieldOffset} on the
     * {@link java.lang.reflect.Field} of some Java field.  The value
     * in memory is raw data, and need not correspond to any Java
     * variable.  The endianness of the value in memory is the
     * endianness of the native platform.
     * <p>
     * The write will be atomic with respect to the largest power of
     * two that divides the GCD of the offset and the storage size.
     * For example, putLongUnaligned will make atomic writes of 2-, 4-,
     * or 8-byte storage units if the offset is zero mod 2, 4, or 8,
     * respectively.  There are no other guarantees of atomicity.
     * <p>
     * 8-byte atomicity is only guaranteed on platforms on which
     * support atomic accesses to longs.
     *
     * @param o Java heap object in which the value resides, if any, else
     *        null
     * @param offset The offset in bytes from the start of the object
     * @param x the value to store
     * @throws RuntimeException No defined exceptions are thrown, not even
     *         {@link NullPointerException}
     * @since 9
     */
    @HotSpotIntrinsicCandidate
    public final void putLongUnaligned(Object o, long offset, long x) {
        if ((offset & 7) == 0) {
            putLong(o, offset, x);
        } else if ((offset & 3) == 0) {
            putLongParts(o, offset,
                         (int)(x >> 0),
                         (int)(x >>> 32));
        } else if ((offset & 1) == 0) {
            putLongParts(o, offset,
                         (short)(x >>> 0),
                         (short)(x >>> 16),
                         (short)(x >>> 32),
                         (short)(x >>> 48));
        } else {
            putLongParts(o, offset,
                         (byte)(x >>> 0),
                         (byte)(x >>> 8),
                         (byte)(x >>> 16),
                         (byte)(x >>> 24),
                         (byte)(x >>> 32),
                         (byte)(x >>> 40),
                         (byte)(x >>> 48),
                         (byte)(x >>> 56));
        }
    }

    /**
     * As {@link #putLongUnaligned(Object, long, long)} but with an additional
     * argument which specifies the endianness of the value as stored in memory.
     * @param o Java heap object in which the value resides
     * @param offset The offset in bytes from the start of the object
     * @param x the value to store
     * @param bigEndian The endianness of the value
     * @throws RuntimeException No defined exceptions are thrown, not even
     *         {@link NullPointerException}
     * @since 9
     */
    public final void putLongUnaligned(Object o, long offset, long x, boolean bigEndian) {
        putLongUnaligned(o, offset, convEndian(bigEndian, x));
    }

    /** @see #putLongUnaligned(Object, long, long) */
    @HotSpotIntrinsicCandidate
    public final void putIntUnaligned(Object o, long offset, int x) {
        if ((offset & 3) == 0) {
            putInt(o, offset, x);
        } else if ((offset & 1) == 0) {
            putIntParts(o, offset,
                        (short)(x >> 0),
                        (short)(x >>> 16));
        } else {
            putIntParts(o, offset,
                        (byte)(x >>> 0),
                        (byte)(x >>> 8),
                        (byte)(x >>> 16),
                        (byte)(x >>> 24));
        }
    }
    /** @see #putLongUnaligned(Object, long, long, boolean) */
    public final void putIntUnaligned(Object o, long offset, int x, boolean bigEndian) {
        putIntUnaligned(o, offset, convEndian(bigEndian, x));
    }

    /** @see #putLongUnaligned(Object, long, long) */
    @HotSpotIntrinsicCandidate
    public final void putShortUnaligned(Object o, long offset, short x) {
        if ((offset & 1) == 0) {
            putShort(o, offset, x);
        } else {
            putShortParts(o, offset,
                          (byte)(x >>> 0),
                          (byte)(x >>> 8));
        }
    }
    /** @see #putLongUnaligned(Object, long, long, boolean) */
    public final void putShortUnaligned(Object o, long offset, short x, boolean bigEndian) {
        putShortUnaligned(o, offset, convEndian(bigEndian, x));
    }

    /** @see #putLongUnaligned(Object, long, long) */
    @HotSpotIntrinsicCandidate
    public final void putCharUnaligned(Object o, long offset, char x) {
        putShortUnaligned(o, offset, (short)x);
    }
    /** @see #putLongUnaligned(Object, long, long, boolean) */
    public final void putCharUnaligned(Object o, long offset, char x, boolean bigEndian) {
        putCharUnaligned(o, offset, convEndian(bigEndian, x));
    }

    // JVM interface methods
    private native boolean unalignedAccess0();
    private native boolean isBigEndian0();

    // BE is true iff the native endianness of this platform is big.
    private static final boolean BE = theUnsafe.isBigEndian0();

    // unalignedAccess is true iff this platform can perform unaligned accesses.
    private static final boolean unalignedAccess = theUnsafe.unalignedAccess0();

    private static int pickPos(int top, int pos) { return BE ? top - pos : pos; }

    // These methods construct integers from bytes.  The byte ordering
    // is the native endianness of this platform.
    private static long makeLong(byte i0, byte i1, byte i2, byte i3, byte i4, byte i5, byte i6, byte i7) {
        return ((toUnsignedLong(i0) << pickPos(56, 0))
              | (toUnsignedLong(i1) << pickPos(56, 8))
              | (toUnsignedLong(i2) << pickPos(56, 16))
              | (toUnsignedLong(i3) << pickPos(56, 24))
              | (toUnsignedLong(i4) << pickPos(56, 32))
              | (toUnsignedLong(i5) << pickPos(56, 40))
              | (toUnsignedLong(i6) << pickPos(56, 48))
              | (toUnsignedLong(i7) << pickPos(56, 56)));
    }
    private static long makeLong(short i0, short i1, short i2, short i3) {
        return ((toUnsignedLong(i0) << pickPos(48, 0))
              | (toUnsignedLong(i1) << pickPos(48, 16))
              | (toUnsignedLong(i2) << pickPos(48, 32))
              | (toUnsignedLong(i3) << pickPos(48, 48)));
    }
    private static long makeLong(int i0, int i1) {
        return (toUnsignedLong(i0) << pickPos(32, 0))
             | (toUnsignedLong(i1) << pickPos(32, 32));
    }
    private static int makeInt(short i0, short i1) {
        return (toUnsignedInt(i0) << pickPos(16, 0))
             | (toUnsignedInt(i1) << pickPos(16, 16));
    }
    private static int makeInt(byte i0, byte i1, byte i2, byte i3) {
        return ((toUnsignedInt(i0) << pickPos(24, 0))
              | (toUnsignedInt(i1) << pickPos(24, 8))
              | (toUnsignedInt(i2) << pickPos(24, 16))
              | (toUnsignedInt(i3) << pickPos(24, 24)));
    }
    private static short makeShort(byte i0, byte i1) {
        return (short)((toUnsignedInt(i0) << pickPos(8, 0))
                     | (toUnsignedInt(i1) << pickPos(8, 8)));
    }

    private static byte  pick(byte  le, byte  be) { return BE ? be : le; }
    private static short pick(short le, short be) { return BE ? be : le; }
    private static int   pick(int   le, int   be) { return BE ? be : le; }

    // These methods write integers to memory from smaller parts
    // provided by their caller.  The ordering in which these parts
    // are written is the native endianness of this platform.
    private void putLongParts(Object o, long offset, byte i0, byte i1, byte i2, byte i3, byte i4, byte i5, byte i6, byte i7) {
        putByte(o, offset + 0, pick(i0, i7));
        putByte(o, offset + 1, pick(i1, i6));
        putByte(o, offset + 2, pick(i2, i5));
        putByte(o, offset + 3, pick(i3, i4));
        putByte(o, offset + 4, pick(i4, i3));
        putByte(o, offset + 5, pick(i5, i2));
        putByte(o, offset + 6, pick(i6, i1));
        putByte(o, offset + 7, pick(i7, i0));
    }
    private void putLongParts(Object o, long offset, short i0, short i1, short i2, short i3) {
        putShort(o, offset + 0, pick(i0, i3));
        putShort(o, offset + 2, pick(i1, i2));
        putShort(o, offset + 4, pick(i2, i1));
        putShort(o, offset + 6, pick(i3, i0));
    }
    private void putLongParts(Object o, long offset, int i0, int i1) {
        putInt(o, offset + 0, pick(i0, i1));
        putInt(o, offset + 4, pick(i1, i0));
    }
    private void putIntParts(Object o, long offset, short i0, short i1) {
        putShort(o, offset + 0, pick(i0, i1));
        putShort(o, offset + 2, pick(i1, i0));
    }
    private void putIntParts(Object o, long offset, byte i0, byte i1, byte i2, byte i3) {
        putByte(o, offset + 0, pick(i0, i3));
        putByte(o, offset + 1, pick(i1, i2));
        putByte(o, offset + 2, pick(i2, i1));
        putByte(o, offset + 3, pick(i3, i0));
    }
    private void putShortParts(Object o, long offset, byte i0, byte i1) {
        putByte(o, offset + 0, pick(i0, i1));
        putByte(o, offset + 1, pick(i1, i0));
    }

    // Zero-extend an integer
    private static int toUnsignedInt(byte n)    { return n & 0xff; }
    private static int toUnsignedInt(short n)   { return n & 0xffff; }
    private static long toUnsignedLong(byte n)  { return n & 0xffl; }
    private static long toUnsignedLong(short n) { return n & 0xffffl; }
    private static long toUnsignedLong(int n)   { return n & 0xffffffffl; }

    // Maybe byte-reverse an integer
    private static char convEndian(boolean big, char n)   { return big == BE ? n : Character.reverseBytes(n); }
    private static short convEndian(boolean big, short n) { return big == BE ? n : Short.reverseBytes(n)    ; }
    private static int convEndian(boolean big, int n)     { return big == BE ? n : Integer.reverseBytes(n)  ; }
    private static long convEndian(boolean big, long n)   { return big == BE ? n : Long.reverseBytes(n)     ; }
}
