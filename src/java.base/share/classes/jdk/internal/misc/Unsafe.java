/*
 * Copyright (c) 2000, 2025, Oracle and/or its affiliates. All rights reserved.
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

import jdk.internal.vm.annotation.AOTRuntimeSetup;
import jdk.internal.vm.annotation.AOTSafeClassInitializer;
import jdk.internal.vm.annotation.ForceInline;
import jdk.internal.vm.annotation.IntrinsicCandidate;
import sun.nio.Cleaner;
import sun.nio.ch.DirectBuffer;

import java.lang.reflect.Field;
import java.security.ProtectionDomain;

import static jdk.internal.misc.UnsafeConstants.*;

/**
 * A collection of methods for performing low-level, unsafe operations.
 * Although the class and all methods are public, use of this class is
 * limited because only trusted code can obtain instances of it.
 *
 * <em>Note:</em> It is the responsibility of the caller to make sure
 * arguments are checked before methods of this class are
 * called. While some rudimentary checks are performed on the input,
 * the checks are best effort and when performance is an overriding
 * priority, as when methods of this class are optimized by the
 * runtime compiler, some or all checks (if any) may be elided. Hence,
 * the caller must not rely on the checks and corresponding
 * exceptions!
 *
 * @author John R. Rose
 * @see #getUnsafe
 */
@AOTSafeClassInitializer
public final class Unsafe {

    private static native void registerNatives();
    static {
        runtimeSetup();
    }

    /// BASE_OFFSET, INDEX_SCALE, and ADDRESS_SIZE fields are equivalent if the
    /// AOT initialized heap is reused, so just register natives
    @AOTRuntimeSetup
    private static void runtimeSetup() {
        registerNatives();
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
     */
    public static Unsafe getUnsafe() {
        return theUnsafe;
    }

    //--- peek and poke operations
    // (compilers should optimize these to memory ops)

    // These work on object fields (of all types) in the Java heap.
    // They also work on array elements (of all types) in the Java heap.
    // THey also work on off-heap values (of primitive types).

    // These basic type codes are shared with the classfile format.  Do not change.
    private static final byte BT_BOOLEAN = 4;
    private static final byte BT_CHAR    = 5;
    private static final byte BT_FLOAT   = 6;
    private static final byte BT_DOUBLE  = 7;
    private static final byte BT_BYTE    = 8;
    private static final byte BT_SHORT   = 9;
    private static final byte BT_INT     = 10;
    private static final byte BT_LONG    = 11;

    // The low three bits of each BT value encodes size of BT.
    private static final byte PRIMITIVE_SIZE_MASK = 3;

    // Operators for getAndOperatePrimitiveBitsMO
    private static final byte OP_ADD     = '+';
    private static final byte OP_BITAND  = '&';
    private static final byte OP_BITOR   = '|';
    private static final byte OP_BITXOR  = '^';
    private static final byte OP_SWAP    = '=';
    // Can add these if they become popular:
    //private static final byte OP_MAX     = '>';  // Cf. C++26, RISCV amomaxu
    //private static final byte OP_MIN     = '<';
    //private static final byte OP_MAXU    = ']';
    //private static final byte OP_MINU    = '[';
    // Yes, ASCII symbols are hokey, but we don't have a handy enum for this.
    // (Also, Unsafe is involved in the bootstrapping of the first enum.)

    // These memory order codes are private to this API and its native methods.
    /** Normal relaxed memory order, which is the default for Java. */
    public static final byte MO_PLAIN    = 1;
    /** Selects volatile memory order, as found in Java.  The default for CAS. */
    public static final byte MO_VOLATILE = 2;
    /** Selects an acquiring load. */
    public static final byte MO_ACQUIRE  = 4;
    /** Selects a releasing store. */
    public static final byte MO_RELEASE  = 8;
    private static final byte MO_MODE_MASK = MO_PLAIN|MO_VOLATILE|MO_ACQUIRE|MO_RELEASE;

    // Except for unaligned loads and stores, primitive and single-reference
    // accesses provided here are always atomic and always naturally aligned,
    // as machine word loads and stores.  There is no direct way to select a
    // truly unordered or nonatomic load or store, at the hardware level.
    // (Such a thing would be quite ... unsafe.)
    //
    // There is a related (but distinct) set of MO codes in accessDecorators.hpp
    // Other APIs may make further distinctions:
    //
    //  - MO_RELAXED (from C++) is effectively (*) the same as MO_PLAIN in Java
    //  - MO_SEQ_CST (from C++) could be treated for Java as an alias for MO_VOLATILE
    //  - MO_OPAQUE is an alias for MO_RELAXED, but guarantees atomicity (**)
    //  - MO_UNORDERED is a looser order than MO_RELAXED, not provided here
    //  - C++ acq_rel, as the union of acquire and release, is pretty much MO_VOLATILE
    //  - C++ consume is not used in HotSpot or the JDK

    // (*) When a Java constructor writes to a final field, there is a memory
    // effect beyond C++ relaxed semantics; the write is akin to a releasing
    // store.  This effect is not implemented in this API, so MO_PLAIN here
    // always implies MO_RELAXED (and also MO_OPAQUE).
    //
    // (**) A 64-bit access split into two 32-bit accesses might be called MO_RELAXED
    // from a C++ perspective, but would not be MO_OPAQUE.  HotSpot never splits
    // such accesses, except perhaps when an unaligned address is used explicitly.

    // For these reasons, MO_RELAXED and MO_OPAQUE and MO_PLAIN are almost
    // always aligned.  In theory, C++ "plain" struct access (MO_RELAXED)
    // could fail to be non-atomic, so it could not be called MO_OPAQUE.

    /** Alias for {@code MO_PLAIN}; see comments in code. */
    public static final byte MO_OPAQUE = MO_PLAIN;

    /** MO_WEAK_CAS is only for CAS ops, combining bitwise with lower MO values */
    public static final byte MO_WEAK_CAS = 16;

    /** MO_UNALIGNED is only for primitive load/store; atomicity can be broken */
    private static final byte MO_UNALIGNED       = 32;
    private static final byte MO_UNALIGNED_PLAIN = MO_UNALIGNED|MO_PLAIN;

    // More useful bit combinations
    public static final byte MO_WEAK_CAS_PLAIN    = MO_WEAK_CAS|MO_PLAIN;
    public static final byte MO_WEAK_CAS_VOLATILE = MO_WEAK_CAS|MO_VOLATILE;
    public static final byte MO_WEAK_CAS_ACQUIRE  = MO_WEAK_CAS|MO_ACQUIRE;
    public static final byte MO_WEAK_CAS_RELEASE  = MO_WEAK_CAS|MO_RELEASE;

    // Note that acquire and release modes are for loads and stores, respectively.
    // If a load is requested with MO_RELEASE, it may error, or rise to MO_VOLATILE
    // If a store is requested with MO_ACQUIRE, it may error, or rise to MO_VOLATILE
    // Debug versions of the VM may throw assertion errors more vigorously.

    /**
     * Loads from a variable of any primitive type, using any of a
     * variety of access methods.  See {@link #getInt(Object, long)}
     * for the connection to Java variables and off-heap variables.
     *
     * @param memoryOrder an encoding of the memory access order, one of
     *        {@link #MO_PLAIN} or another of those constants
     * @param o Java heap object in which the variable resides, if any, else
     *        null
     * @param offset indication of where the variable resides in a Java heap
     *        object, if any, else a memory address locating the variable
     *        statically
     * @param basicType an encoding of the primitive type, one of
     *        {@link #BT_BOOLEAN} or another of those constants
     * @return the value fetched from the indicated Java variable,
     *         padded by garbage high-order bits (if smaller than 64 bits)
     * @throws RuntimeException No defined exceptions are thrown, not even
     *         {@link NullPointerException}
     * @see #getInt(Object, long)
     */
    @ForceInline
    @IntrinsicCandidate
    private long getPrimitiveBitsMO(byte memoryOrder, byte basicType,
                                    Object o, long offset) {
        // This intrinsic has a body so that the JIT can refuse to
        // expand the intrinsic, when it sees a request it does not
        // understand.

        // The purpose of the decision tree is to present the JIT with
        // optimizable statements, even if memoryOrder or basicType
        // fail to be constant.  This allows the JIT to compile the
        // body to serve calls from the interpreter and lukewarm code.
        checkBasicType(basicType);
        checkMemoryOrder(memoryOrder & ~MO_UNALIGNED);
        long bits;
        switch (basicType & PRIMITIVE_SIZE_MASK) {  // encodes size of BT
            case 3 -> {
                if (memoryOrder == MO_PLAIN && basicType == BT_LONG && basicType == BT_LONG) {
                    return getPrimitiveBitsMONative(MO_PLAIN, BT_LONG, o, offset);
                }
            }
            case 2 -> {
                if (memoryOrder == MO_PLAIN && basicType == BT_INT) {
                    return getPrimitiveBitsMONative(MO_PLAIN, BT_INT, o, offset);
                }
            }
            case 1 -> {
                if (memoryOrder == MO_PLAIN && basicType == BT_SHORT) {
                    return getPrimitiveBitsMONative(MO_PLAIN, BT_SHORT, o, offset);
                }
            }
            default -> {
                if (memoryOrder == MO_PLAIN && basicType == BT_BYTE) {
                    return getPrimitiveBitsMONative(MO_PLAIN, BT_BYTE, o, offset);
                }
            }
        }

        // this might end up in the native method
        return getPrimitiveBitsMONative(memoryOrder, basicType, o, offset);
    }

    // Second try for the JIT, if it dislikes a memory order or alignment request.
    // Both this method and the previous are handled by the same native code
    // and the same compiler intrinsic logic.
    // The native method can treat every non-plain memory access as MO_VOLATILE.
    // The native method can assert on unaligned requests if !UNALIGNED_ACCESS.
    // The native method can assert on combinations which do not make sense.
    // Prefer this method when you don't need the case analysis of the previous one.
    @IntrinsicCandidate
    private native long getPrimitiveBitsMONative(byte memoryOrder, byte basicType,
                                                 Object o, long offset);


    /**
     * Stores to a variable of any primitive type, using any of a
     * variety of access methods.  See {@link #putInt(Object, long, int)}
     * for the connection to Java variables and off-heap variables.
     *
     * @param memoryOrder an encoding of the memory access order, one of
     *        {@link #MO_PLAIN} or another of those constants
     * @param o Java heap object in which the variable resides, if any, else
     *        null
     * @param offset indication of where the variable resides in a Java heap
     *        object, if any, else a memory address locating the variable
     *        statically
     * @return the value to be stored to the indicated Java variable,
     *         padded by ignored high-order bits (if smaller than 64 bits)
     * @param basicType an encoding of the primitive type, one of
     *        {@link #BT_BOOLEAN} or another of those constants
     * @throws RuntimeException No defined exceptions are thrown, not even
     *         {@link NullPointerException}
     * @see #getInt(Object, long)
     */
    @ForceInline
    @IntrinsicCandidate
    private void putPrimitiveBitsMO(byte memoryOrder, byte basicType,
                                    Object o, long offset, long bits) {
        // This intrinsic has a body so that the JIT can refuse to
        // expand the intrinsic, when it sees a request it does not
        // understand.

        // The purpose of the decision tree is to present the JIT with
        // optimizable statements, even if memoryOrder or basicType
        // fail to be constant.  This allows the JIT to compile the
        // body to serve calls from the interpreter and lukewarm code.
        checkBasicType(basicType);
        checkMemoryOrder(memoryOrder & ~MO_UNALIGNED);
        switch (basicType & PRIMITIVE_SIZE_MASK) {  // encodes size of BT
            case 3 -> {
                if (memoryOrder == MO_PLAIN && basicType == BT_LONG && basicType == BT_LONG) {
                    putPrimitiveBitsMONative(MO_PLAIN, BT_LONG, o, offset, bits);
                    return;
                }
            }
            case 2 -> {
                if (memoryOrder == MO_PLAIN && basicType == BT_INT) {
                    putPrimitiveBitsMONative(MO_PLAIN, BT_INT, o, offset, bits);
                    return;
                }
            }
            case 1 -> {
                if (memoryOrder == MO_PLAIN && basicType == BT_SHORT) {
                    putPrimitiveBitsMONative(MO_PLAIN, BT_SHORT, o, offset, bits);
                    return;
                }
            }
            default -> {
                if (memoryOrder == MO_PLAIN && basicType == BT_BYTE) {
                    putPrimitiveBitsMONative(MO_PLAIN, BT_BYTE, o, offset, bits);
                    return;
                } else if (basicType == BT_BOOLEAN) {
                    bits = bool2byte(byte2bool((byte)bits)); // do not store garbage booleans
                    break;
                }
            }
        }

        // this might end up in the native method
        putPrimitiveBitsMONative(memoryOrder, basicType, o, offset, bits);
    }

    // Second try for the JIT, if it dislikes a memory order or alignment request.
    // Both this method and the previous are handled by the same native code
    // and the same compiler intrinsic logic.
    // The native method can treat every non-plain memory access as MO_VOLATILE.
    // The native method can assert on unaligned requests if !UNALIGNED_ACCESS.
    // The native method can assert on combinations which do not make sense.
    // Prefer this method when you don't need the case analysis of the previous one.
    @IntrinsicCandidate
    private native void putPrimitiveBitsMONative(byte memoryOrder, byte basicType,
                                                 Object o, long offset, long bits);

    @ForceInline
    private static void checkMemoryOrder(int memoryOrder) {
        if ((memoryOrder & (~MO_MODE_MASK | (memoryOrder-1))) == 0) {
            // This tricky expression computes the answer to two questions:
            //  1. is memoryOrder contained in MO_MODE_MASK?  (mo & ~MMM) == 0
            //  2. is memoryOrder a power of two?             (mo & mo-1) == 0
            // First we can rewrite the tests,  A==0 & B==0  ==> (A|B) == 0
            // Then,  (mo & ~MMM) | (mo & mo-1)  ==>  (mo & (~MMM | mo-1)
            return;
        }
        throw new IllegalArgumentException("bad memory order requested");
    }

    @ForceInline
    private static void checkBasicType(byte basicType) {
        if (basicType >= BT_BOOLEAN && basicType <= BT_LONG)  return;
        throw new IllegalArgumentException("bad type");
    }

    // When converting from primitive bits, normal casts work fine for most types.
    // But not boolean, float, or double.  They need these unsurprising operators.
    // The methods are placed here because the native methods which work on the
    // 64-bit primitive bit representations must make compatible conversions.
    // It is ugly that byte, short, int, and float pad with up to 32 sign bits,
    // but that is a harmless detail internal to this API.
    // The native primitive getter method is allowed to pad with zeroes or any
    // other kind of garbage.

    /** Convert primitive representation bits to a float.  Ignore high half. */
    @ForceInline
    public static float bitsToFloat(long bits) {
        return Float.intBitsToFloat((int)bits);
    }

    /** Convert a float to primitive representation bits.  Pad with 32 bits. */
    @ForceInline
    public static long floatToBits(float x) {
        return Float.floatToRawIntBits(x);
    }

    /** Convert primitive representation bits to a double. */
    @ForceInline
    public static double bitsToDouble(long bits) {
        return Double.longBitsToDouble(bits);
    }

    /** Convert a double to primitive representation bits. */
    @ForceInline
    public static long doubleToBits(double x) {
        return Double.doubleToRawLongBits(x);
    }

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
    @ForceInline
    public int getInt(Object o, long offset) {
        return (int) getPrimitiveBitsMONative(MO_PLAIN, BT_INT, o, offset);
    }

    /** Special-access version of {@link #getInt(Object, long)}  */
    @ForceInline
    public int getIntMO(byte memoryOrder, Object o, long offset) {
        return (int) getPrimitiveBitsMO(memoryOrder, BT_INT, o, offset);
    }

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
    @ForceInline
    public void putInt(Object o, long offset, int x) {
        putIntMO(MO_PLAIN, o, offset, x);
    }

    /** Special-access version of {@link #putInt(Object, long, int)}  */
    @ForceInline
    public void putIntMO(byte memoryOrder, Object o, long offset, int x) {
        putPrimitiveBitsMO(memoryOrder, BT_INT, o, offset, (long)x);
    }

    /**
     * Fetches a reference value from a given Java variable.
     * @see #getInt(Object, long)
     */
    @ForceInline
    public Object getReference(Object o, long offset) {
        return getReferenceMO(MO_PLAIN, o, offset);
    }

    /** Special-access version of {@link #getReference(Object, long)}  */
    @IntrinsicCandidate
    public native Object getReferenceMO(byte memoryOrder, Object o, long offset);

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
    @ForceInline
    public void putReference(Object o, long offset, Object x) {
        putReferenceMO(MO_PLAIN, o, offset, x);
    }

    /** Special-access version of {@link #putReference(Object, long, Object)}  */
    @IntrinsicCandidate
    public native void putReferenceMO(byte memoryOrder, Object o, long offset, Object x);

    /** @see #getInt(Object, long) */
    @ForceInline
    public boolean getBoolean(Object o, long offset) {
        return byte2bool((byte)getPrimitiveBitsMO(MO_PLAIN, BT_BOOLEAN, o, offset));
    }

    /** Special-access version. */
    @ForceInline
    public boolean getBooleanMO(byte memoryOrder, Object o, long offset) {
        return byte2bool((byte)getPrimitiveBitsMO(memoryOrder, BT_BOOLEAN, o, offset));
    }

    /** @see #putInt(Object, long, int) */
    @ForceInline
    public void    putBoolean(Object o, long offset, boolean x) {
        putPrimitiveBitsMO(MO_PLAIN, BT_BOOLEAN, o, offset, bool2byte(x));
    }

    /** Special-access version. */
    @ForceInline
    public void    putBooleanMO(byte memoryOrder, Object o, long offset, boolean x) {
        putPrimitiveBitsMO(memoryOrder, BT_BOOLEAN, o, offset, bool2byte(x));
    }

    /** @see #getInt(Object, long) */
    @ForceInline
    public byte    getByte(Object o, long offset) {
        return (byte) getPrimitiveBitsMONative(MO_PLAIN, BT_BYTE, o, offset);
    }

    /** Special-access version. */
    @ForceInline
    public byte    getByteMO(byte memoryOrder, Object o, long offset) {
        return (byte) getPrimitiveBitsMO(memoryOrder, BT_BYTE, o, offset);
    }

    /** @see #putInt(Object, long, int) */
    @ForceInline
    public void    putByte(Object o, long offset, byte x) {
        putPrimitiveBitsMONative(MO_PLAIN, BT_BYTE, o, offset, (long)x);
    }

    /** Special-access version. */
    @ForceInline
    public void    putByteMO(byte memoryOrder, Object o, long offset, byte x) {
        putPrimitiveBitsMO(memoryOrder, BT_BYTE, o, offset, (long)x);
    }

    /** @see #getInt(Object, long) */
    @ForceInline
    public short   getShort(Object o, long offset) {
        return (short) getPrimitiveBitsMONative(MO_PLAIN, BT_SHORT, o, offset);
    }

    /** Special-access version. */
    @ForceInline
    public short   getShortMO(byte memoryOrder, Object o, long offset) {
        return (short) getPrimitiveBitsMO(memoryOrder, BT_SHORT, o, offset);
    }

    /** @see #putInt(Object, long, int) */
    @ForceInline
    public void    putShort(Object o, long offset, short x) {
        putPrimitiveBitsMONative(MO_PLAIN, BT_SHORT, o, offset, (long)x);
    }

    /** Special-access version. */
    @ForceInline
    public void    putShortMO(byte memoryOrder, Object o, long offset, short x) {
        putPrimitiveBitsMO(memoryOrder, BT_SHORT, o, offset, (long)x);
    }

    /** @see #getInt(Object, long) */
    @ForceInline
    public char    getChar(Object o, long offset) {
        return (char) getPrimitiveBitsMONative(MO_PLAIN, BT_CHAR, o, offset);
    }

    /** Special-access version. */
    @ForceInline
    public char    getCharMO(byte memoryOrder, Object o, long offset) {
        return (char) getPrimitiveBitsMO(memoryOrder, BT_CHAR, o, offset);
    }

    /** @see #putInt(Object, long, int) */
    @ForceInline
    public void    putChar(Object o, long offset, char x) {
        putPrimitiveBitsMONative(MO_PLAIN, BT_CHAR, o, offset, (long)x);
    }

    /** Special-access version. */
    @ForceInline
    public void    putCharMO(byte memoryOrder, Object o, long offset, char x) {
        putPrimitiveBitsMO(memoryOrder, BT_CHAR, o, offset, (long)x);
    }

    /** @see #getInt(Object, long) */
    @ForceInline
    public long    getLong(Object o, long offset) {
        return getPrimitiveBitsMONative(MO_PLAIN, BT_LONG, o, offset);
    }

    /** Special-access version. */
    @ForceInline
    public long    getLongMO(byte memoryOrder, Object o, long offset) {
        return getPrimitiveBitsMO(memoryOrder, BT_LONG, o, offset);
    }

    /** @see #putInt(Object, long, int) */
    @ForceInline
    public void    putLong(Object o, long offset, long x) {
        putPrimitiveBitsMONative(MO_PLAIN, BT_LONG, o, offset, x);
    }

    /** Special-access version. */
    @ForceInline
    public void    putLongMO(byte memoryOrder, Object o, long offset, long x) {
        putPrimitiveBitsMO(memoryOrder, BT_LONG, o, offset, x);
    }

    /** @see #getInt(Object, long) */
    @ForceInline
    public float   getFloat(Object o, long offset) {
        return bitsToFloat(getPrimitiveBitsMO(MO_PLAIN, BT_FLOAT, o, offset));
    }

    /** Special-access version. */
    @ForceInline
    public float   getFloatMO(byte memoryOrder, Object o, long offset) {
        return bitsToFloat(getPrimitiveBitsMO(memoryOrder, BT_FLOAT, o, offset));
    }

    /** @see #putInt(Object, long, int) */
    @ForceInline
    public void    putFloat(Object o, long offset, float x) {
        putPrimitiveBitsMO(MO_PLAIN, BT_FLOAT, o, offset, floatToBits(x));
    }

    /** Special-access version. */
    @ForceInline
    public void    putFloatMO(byte memoryOrder, Object o, long offset, float x) {
        putPrimitiveBitsMO(memoryOrder, BT_FLOAT, o, offset, floatToBits(x));
    }

    /** @see #getInt(Object, long) */
    @ForceInline
    public double  getDouble(Object o, long offset) {
        return bitsToDouble(getPrimitiveBitsMO(MO_PLAIN, BT_DOUBLE, o, offset));
    }

    /** Special-access version. */
    @ForceInline
    public double  getDoubleMO(byte memoryOrder, Object o, long offset) {
        return bitsToDouble(getPrimitiveBitsMO(memoryOrder, BT_DOUBLE, o, offset));
    }

    /** @see #putInt(Object, long, int) */
    @ForceInline
    public void    putDouble(Object o, long offset, double x) {
        putPrimitiveBitsMO(MO_PLAIN, BT_DOUBLE, o, offset, doubleToBits(x));
    }

    /** Special-access version. */
    @ForceInline
    public void    putDoubleMO(byte memoryOrder, Object o, long offset, double x) {
        putPrimitiveBitsMO(memoryOrder, BT_DOUBLE, o, offset, doubleToBits(x));
    }

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
     * @see #getInt(Object, long)
     */
    @ForceInline
    public long getAddress(Object o, long offset) {
        if (ADDRESS_SIZE == 4) {
            return Integer.toUnsignedLong(getInt(o, offset));
        } else {
            return getLong(o, offset);
        }
    }

    /**
     * Stores a native pointer into a given memory address.  If the address is
     * zero, or does not point into a block obtained from {@link
     * #allocateMemory}, the results are undefined.
     *
     * <p>The number of bytes actually written at the target address may be
     * determined by consulting {@link #addressSize}.
     *
     * @see #allocateMemory
     * @see #putInt(Object, long, int)
     */
    @ForceInline
    public void putAddress(Object o, long offset, long x) {
        if (ADDRESS_SIZE == 4) {
            putInt(o, offset, (int)x);
        } else {
            putLong(o, offset, x);
        }
    }

    // Methods that take no Object base address work on values in the
    // C heap, or VM internal data.
    //
    // If another method (of the same name) accepts an Object base
    // address, then omitting the Object base address is exactly
    // equivalent to passing null for a base address.
    //
    // If you need a stronger memory order, you need to supply the
    // null base explicitly as well as the memory order.  So
    // getInt(addr) works but then getIntMO(MO_VOLATILE, null, addr).

    /**
     * Fetches an uncompressed reference value from a given native variable
     * ignoring the VM's compressed references mode.
     * The address must be known to the VM as an oop handle (or an equivalent).
     *
     * @param address a memory address locating the variable
     * @return the value fetched from the indicated native variable
     */
    public native Object getUncompressedObject(long address);

    /**
     * Fetches a value from a given memory address.  If the address is zero, or
     * does not point into a block obtained from {@link #allocateMemory}, the
     * results are undefined.
     *
     * @see #allocateMemory
     */
    @ForceInline
    public byte getByte(long address) {
        return getByte(null, address);
    }

    /**
     * Stores a value into a given memory address.  If the address is zero, or
     * does not point into a block obtained from {@link #allocateMemory}, the
     * results are undefined.
     *
     * @see #getByte(long)
     */
    @ForceInline
    public void putByte(long address, byte x) {
        putByte(null, address, x);
    }

    /** @see #getByte(long) */
    @ForceInline
    public short getShort(long address) {
        return getShort(null, address);
    }

    /** @see #putByte(long, byte) */
    @ForceInline
    public void putShort(long address, short x) {
        putShort(null, address, x);
    }

    /** @see #getByte(long) */
    @ForceInline
    public char getChar(long address) {
        return getChar(null, address);
    }

    /** @see #putByte(long, byte) */
    @ForceInline
    public void putChar(long address, char x) {
        putChar(null, address, x);
    }

    /** @see #getByte(long) */
    @ForceInline
    public int getInt(long address) {
        return getInt(null, address);
    }

    /** @see #putByte(long, byte) */
    @ForceInline
    public void putInt(long address, int x) {
        putInt(null, address, x);
    }

    /** @see #getByte(long) */
    @ForceInline
    public long getLong(long address) {
        return getLong(null, address);
    }

    /** @see #putByte(long, byte) */
    @ForceInline
    public void putLong(long address, long x) {
        putLong(null, address, x);
    }

    /** @see #getByte(long) */
    @ForceInline
    public float getFloat(long address) {
        return getFloat(null, address);
    }

    /** @see #putByte(long, byte) */
    @ForceInline
    public void putFloat(long address, float x) {
        putFloat(null, address, x);
    }

    /** @see #getByte(long) */
    @ForceInline
    public double getDouble(long address) {
        return getDouble(null, address);
    }

    /** @see #putByte(long, byte) */
    @ForceInline
    public void putDouble(long address, double x) {
        putDouble(null, address, x);
    }

    /** @see #getAddress(Object, long) */
    @ForceInline
    public long getAddress(long address) {
        return getAddress(null, address);
    }

    /** @see #putAddress(Object, long, long) */
    @ForceInline
    public void putAddress(long address, long x) {
        putAddress(null, address, x);
    }



    //--- helper methods for validating various types of objects/values

    /**
     * Create an exception reflecting that some of the input was invalid
     *
     * <em>Note:</em> It is the responsibility of the caller to make
     * sure arguments are checked before the methods are called. While
     * some rudimentary checks are performed on the input, the checks
     * are best effort and when performance is an overriding priority,
     * as when methods of this class are optimized by the runtime
     * compiler, some or all checks (if any) may be elided. Hence, the
     * caller must not rely on the checks and corresponding
     * exceptions!
     *
     * @return an exception object
     */
    private RuntimeException invalidInput() {
        return new IllegalArgumentException();
    }

    /**
     * Check if a value is 32-bit clean (32 MSB are all zero)
     *
     * @param value the 64-bit value to check
     *
     * @return true if the value is 32-bit clean
     */
    private boolean is32BitClean(long value) {
        return value >>> 32 == 0;
    }

    /**
     * Check the validity of a size (the equivalent of a size_t)
     *
     * @throws RuntimeException if the size is invalid
     *         (<em>Note:</em> after optimization, invalid inputs may
     *         go undetected, which will lead to unpredictable
     *         behavior)
     */
    private void checkSize(long size) {
        if (ADDRESS_SIZE == 4) {
            // Note: this will also check for negative sizes
            if (!is32BitClean(size)) {
                throw invalidInput();
            }
        } else if (size < 0) {
            throw invalidInput();
        }
    }

    /**
     * Check the validity of a native address (the equivalent of void*)
     *
     * @throws RuntimeException if the address is invalid
     *         (<em>Note:</em> after optimization, invalid inputs may
     *         go undetected, which will lead to unpredictable
     *         behavior)
     */
    private void checkNativeAddress(long address) {
        if (ADDRESS_SIZE == 4) {
            // Accept both zero and sign extended pointers. A valid
            // pointer will, after the +1 below, either have produced
            // the value 0x0 or 0x1. Masking off the low bit allows
            // for testing against 0.
            if ((((address >> 32) + 1) & ~1) != 0) {
                throw invalidInput();
            }
        }
    }

    /**
     * Check the validity of an offset, relative to a base object
     *
     * @param o the base object
     * @param offset the offset to check
     *
     * @throws RuntimeException if the size is invalid
     *         (<em>Note:</em> after optimization, invalid inputs may
     *         go undetected, which will lead to unpredictable
     *         behavior)
     */
    private void checkOffset(Object o, long offset) {
        if (ADDRESS_SIZE == 4) {
            // Note: this will also check for negative offsets
            if (!is32BitClean(offset)) {
                throw invalidInput();
            }
        } else if (offset < 0) {
            throw invalidInput();
        }
    }

    /**
     * Check the validity of a double-register pointer
     *
     * Note: This code deliberately does *not* check for NPE for (at
     * least) three reasons:
     *
     * 1) NPE is not just NULL/0 - there is a range of values all
     * resulting in an NPE, which is not trivial to check for
     *
     * 2) It is the responsibility of the callers of Unsafe methods
     * to verify the input, so throwing an exception here is not really
     * useful - passing in a NULL pointer is a critical error and the
     * must not expect an exception to be thrown anyway.
     *
     * 3) the actual operations will detect NULL pointers anyway by
     * means of traps and signals (like SIGSEGV).
     *
     * @param o Java heap object, or null
     * @param offset indication of where the variable resides in a Java heap
     *        object, if any, else a memory address locating the variable
     *        statically
     *
     * @throws RuntimeException if the pointer is invalid
     *         (<em>Note:</em> after optimization, invalid inputs may
     *         go undetected, which will lead to unpredictable
     *         behavior)
     */
    private void checkPointer(Object o, long offset) {
        if (o == null) {
            checkNativeAddress(offset);
        } else {
            checkOffset(o, offset);
        }
    }

    /**
     * Check if a type is a primitive array type
     *
     * @param c the type to check
     *
     * @return true if the type is a primitive array type
     */
    private void checkPrimitiveArray(Class<?> c) {
        Class<?> componentType = c.getComponentType();
        if (componentType == null || !componentType.isPrimitive()) {
            throw invalidInput();
        }
    }

    /**
     * Check that a pointer is a valid primitive array type pointer
     *
     * Note: pointers off-heap are considered to be primitive arrays
     *
     * @throws RuntimeException if the pointer is invalid
     *         (<em>Note:</em> after optimization, invalid inputs may
     *         go undetected, which will lead to unpredictable
     *         behavior)
     */
    private void checkPrimitivePointer(Object o, long offset) {
        checkPointer(o, offset);

        if (o != null) {
            // If on heap, it must be a primitive array
            checkPrimitiveArray(o.getClass());
        }
    }


    //--- wrappers for malloc, realloc, free:

    /**
     * Round up allocation size to a multiple of HeapWordSize.
     */
    private long alignToHeapWordSize(long bytes) {
        if (bytes >= 0) {
            return (bytes + ADDRESS_SIZE - 1) & ~(ADDRESS_SIZE - 1);
        } else {
            throw invalidInput();
        }
    }

    /**
     * Allocates a new block of native memory, of the given size in bytes.  The
     * contents of the memory are uninitialized; they will generally be
     * garbage.  The resulting native pointer will be zero if and only if the
     * requested size is zero.  The resulting native pointer will be aligned for
     * all value types.   Dispose of this memory by calling {@link #freeMemory}
     * or resize it with {@link #reallocateMemory}.
     *
     * <em>Note:</em> It is the responsibility of the caller to make
     * sure arguments are checked before the methods are called. While
     * some rudimentary checks are performed on the input, the checks
     * are best effort and when performance is an overriding priority,
     * as when methods of this class are optimized by the runtime
     * compiler, some or all checks (if any) may be elided. Hence, the
     * caller must not rely on the checks and corresponding
     * exceptions!
     *
     * @throws RuntimeException if the size is negative or too large
     *         for the native size_t type
     *
     * @throws OutOfMemoryError if the allocation is refused by the system
     *
     * @see #getByte(long)
     * @see #putByte(long, byte)
     */
    public long allocateMemory(long bytes) {
        bytes = alignToHeapWordSize(bytes);

        allocateMemoryChecks(bytes);

        if (bytes == 0) {
            return 0;
        }

        long p = allocateMemory0(bytes);
        if (p == 0) {
            throw new OutOfMemoryError("Unable to allocate " + bytes + " bytes");
        }

        return p;
    }

    /**
     * Validate the arguments to allocateMemory
     *
     * @throws RuntimeException if the arguments are invalid
     *         (<em>Note:</em> after optimization, invalid inputs may
     *         go undetected, which will lead to unpredictable
     *         behavior)
     */
    private void allocateMemoryChecks(long bytes) {
        checkSize(bytes);
    }

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
     * <em>Note:</em> It is the responsibility of the caller to make
     * sure arguments are checked before the methods are called. While
     * some rudimentary checks are performed on the input, the checks
     * are best effort and when performance is an overriding priority,
     * as when methods of this class are optimized by the runtime
     * compiler, some or all checks (if any) may be elided. Hence, the
     * caller must not rely on the checks and corresponding
     * exceptions!
     *
     * @throws RuntimeException if the size is negative or too large
     *         for the native size_t type
     *
     * @throws OutOfMemoryError if the allocation is refused by the system
     *
     * @see #allocateMemory
     */
    public long reallocateMemory(long address, long bytes) {
        bytes = alignToHeapWordSize(bytes);

        reallocateMemoryChecks(address, bytes);

        if (bytes == 0) {
            freeMemory(address);
            return 0;
        }

        long p = (address == 0) ? allocateMemory0(bytes) : reallocateMemory0(address, bytes);
        if (p == 0) {
            throw new OutOfMemoryError("Unable to allocate " + bytes + " bytes");
        }

        return p;
    }

    /**
     * Validate the arguments to reallocateMemory
     *
     * @throws RuntimeException if the arguments are invalid
     *         (<em>Note:</em> after optimization, invalid inputs may
     *         go undetected, which will lead to unpredictable
     *         behavior)
     */
    private void reallocateMemoryChecks(long address, long bytes) {
        checkPointer(null, address);
        checkSize(bytes);
    }

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
     * <em>Note:</em> It is the responsibility of the caller to make
     * sure arguments are checked before the methods are called. While
     * some rudimentary checks are performed on the input, the checks
     * are best effort and when performance is an overriding priority,
     * as when methods of this class are optimized by the runtime
     * compiler, some or all checks (if any) may be elided. Hence, the
     * caller must not rely on the checks and corresponding
     * exceptions!
     *
     * @throws RuntimeException if any of the arguments is invalid
     *
     * @since 1.7
     */
    public void setMemory(Object o, long offset, long bytes, byte value) {
        setMemoryChecks(o, offset, bytes, value);

        if (bytes == 0) {
            return;
        }

        setMemory0(o, offset, bytes, value);
    }

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
     * Validate the arguments to setMemory
     *
     * @throws RuntimeException if the arguments are invalid
     *         (<em>Note:</em> after optimization, invalid inputs may
     *         go undetected, which will lead to unpredictable
     *         behavior)
     */
    private void setMemoryChecks(Object o, long offset, long bytes, byte value) {
        checkPrimitivePointer(o, offset);
        checkSize(bytes);
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
     * <em>Note:</em> It is the responsibility of the caller to make
     * sure arguments are checked before the methods are called. While
     * some rudimentary checks are performed on the input, the checks
     * are best effort and when performance is an overriding priority,
     * as when methods of this class are optimized by the runtime
     * compiler, some or all checks (if any) may be elided. Hence, the
     * caller must not rely on the checks and corresponding
     * exceptions!
     *
     * @throws RuntimeException if any of the arguments is invalid
     *
     * @since 1.7
     */
    public void copyMemory(Object srcBase, long srcOffset,
                           Object destBase, long destOffset,
                           long bytes) {
        copyMemoryChecks(srcBase, srcOffset, destBase, destOffset, bytes);

        if (bytes == 0) {
            return;
        }

        copyMemory0(srcBase, srcOffset, destBase, destOffset, bytes);
    }

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
     * Validate the arguments to copyMemory
     *
     * @throws RuntimeException if any of the arguments is invalid
     *         (<em>Note:</em> after optimization, invalid inputs may
     *         go undetected, which will lead to unpredictable
     *         behavior)
     */
    private void copyMemoryChecks(Object srcBase, long srcOffset,
                                  Object destBase, long destOffset,
                                  long bytes) {
        checkSize(bytes);
        checkPrimitivePointer(srcBase, srcOffset);
        checkPrimitivePointer(destBase, destOffset);
    }

    /**
     * Copies all elements from one block of memory to another block,
     * *unconditionally* byte swapping the elements on the fly.
     *
     * <p>This method determines each block's base address by means of two parameters,
     * and so it provides (in effect) a <em>double-register</em> addressing mode,
     * as discussed in {@link #getInt(Object,long)}.  When the object reference is null,
     * the offset supplies an absolute base address.
     *
     * <em>Note:</em> It is the responsibility of the caller to make
     * sure arguments are checked before the methods are called. While
     * some rudimentary checks are performed on the input, the checks
     * are best effort and when performance is an overriding priority,
     * as when methods of this class are optimized by the runtime
     * compiler, some or all checks (if any) may be elided. Hence, the
     * caller must not rely on the checks and corresponding
     * exceptions!
     *
     * @throws RuntimeException if any of the arguments is invalid
     *
     * @since 9
     */
    public void copySwapMemory(Object srcBase, long srcOffset,
                               Object destBase, long destOffset,
                               long bytes, long elemSize) {
        copySwapMemoryChecks(srcBase, srcOffset, destBase, destOffset, bytes, elemSize);

        if (bytes == 0) {
            return;
        }

        copySwapMemory0(srcBase, srcOffset, destBase, destOffset, bytes, elemSize);
    }

    private void copySwapMemoryChecks(Object srcBase, long srcOffset,
                                      Object destBase, long destOffset,
                                      long bytes, long elemSize) {
        checkSize(bytes);

        if (elemSize != 2 && elemSize != 4 && elemSize != 8) {
            throw invalidInput();
        }
        if (bytes % elemSize != 0) {
            throw invalidInput();
        }

        checkPrimitivePointer(srcBase, srcOffset);
        checkPrimitivePointer(destBase, destOffset);
    }

    /**
     * Copies all elements from one block of memory to another block, byte swapping the
     * elements on the fly.
     *
     * This provides a <em>single-register</em> addressing mode, as
     * discussed in {@link #getInt(Object,long)}.
     *
     * Equivalent to {@code copySwapMemory(null, srcAddress, null, destAddress, bytes, elemSize)}.
     */
    public void copySwapMemory(long srcAddress, long destAddress, long bytes, long elemSize) {
        copySwapMemory(null, srcAddress, null, destAddress, bytes, elemSize);
    }

    /**
     * Disposes of a block of native memory, as obtained from {@link
     * #allocateMemory} or {@link #reallocateMemory}.  The address passed to
     * this method may be null, in which case no action is taken.
     *
     * <em>Note:</em> It is the responsibility of the caller to make
     * sure arguments are checked before the methods are called. While
     * some rudimentary checks are performed on the input, the checks
     * are best effort and when performance is an overriding priority,
     * as when methods of this class are optimized by the runtime
     * compiler, some or all checks (if any) may be elided. Hence, the
     * caller must not rely on the checks and corresponding
     * exceptions!
     *
     * @throws RuntimeException if any of the arguments is invalid
     *
     * @see #allocateMemory
     */
    public void freeMemory(long address) {
        freeMemoryChecks(address);

        if (address == 0) {
            return;
        }

        freeMemory0(address);
    }

    /**
     * Validate the arguments to freeMemory
     *
     * @throws RuntimeException if the arguments are invalid
     *         (<em>Note:</em> after optimization, invalid inputs may
     *         go undetected, which will lead to unpredictable
     *         behavior)
     */
    private void freeMemoryChecks(long address) {
        checkPointer(null, address);
    }

    /**
     * Ensure writeback of a specified virtual memory address range
     * from cache to physical memory. All bytes in the address range
     * are guaranteed to have been written back to physical memory on
     * return from this call i.e. subsequently executed store
     * instructions are guaranteed not to be visible before the
     * writeback is completed.
     *
     * @param address
     *        the lowest byte address that must be guaranteed written
     *        back to memory. bytes at lower addresses may also be
     *        written back.
     *
     * @param length
     *        the length in bytes of the region starting at address
     *        that must be guaranteed written back to memory.
     *
     * @throws RuntimeException if memory writeback is not supported
     *         on the current hardware of if the arguments are invalid.
     *         (<em>Note:</em> after optimization, invalid inputs may
     *         go undetected, which will lead to unpredictable
     *         behavior)
     *
     * @since 14
     */

    public void writebackMemory(long address, long length) {
        checkWritebackEnabled();
        checkWritebackMemory(address, length);

        // perform any required pre-writeback barrier
        writebackPreSync0();

        // write back one cache line at a time
        long line = dataCacheLineAlignDown(address);
        long end = address + length;
        while (line < end) {
            writeback0(line);
            line += dataCacheLineFlushSize();
        }

        // perform any required post-writeback barrier
        writebackPostSync0();
    }

    /**
     * Validate the arguments to writebackMemory
     *
     * @throws RuntimeException if the arguments are invalid
     *         (<em>Note:</em> after optimization, invalid inputs may
     *         go undetected, which will lead to unpredictable
     *         behavior)
     */
    private void checkWritebackMemory(long address, long length) {
        checkNativeAddress(address);
        checkSize(length);
    }

    /**
     * Validate that the current hardware supports memory writeback.
     * (<em>Note:</em> this is a belt and braces check.  Clients are
     * expected to test whether writeback is enabled by calling
     * ({@link isWritebackEnabled #isWritebackEnabled} and avoid
     * calling method {@link writeback #writeback} if it is disabled).
     *
     *
     * @throws RuntimeException if memory writeback is not supported
     */
    private void checkWritebackEnabled() {
        if (!isWritebackEnabled()) {
            throw new RuntimeException("writebackMemory not enabled!");
        }
    }

    /**
     * force writeback of an individual cache line.
     *
     * @param address
     *        the start address of the cache line to be written back
     */
    @IntrinsicCandidate
    private native void writeback0(long address);

     /**
      * Serialize writeback operations relative to preceding memory writes.
      */
    @IntrinsicCandidate
    private native void writebackPreSync0();

     /**
      * Serialize writeback operations relative to following memory writes.
      */
    @IntrinsicCandidate
    private native void writebackPostSync0();

    //--- random queries

    /**
     * This constant differs from all results that will ever be returned from
     * {@link #staticFieldOffset}, {@link #objectFieldOffset},
     * or {@link #arrayBaseOffset}.
     * <p>
     * The static type is @code long} to emphasize that long arithmetic should
     * always be used for offset calculations to avoid overflows.
     */
    public static final long INVALID_FIELD_OFFSET = -1;

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
     *
     * @throws NullPointerException if the field is {@code null}
     * @throws IllegalArgumentException if the field is static
     * @see #getInt(Object, long)
     */
    public long objectFieldOffset(Field f) {
        if (f == null) {
            throw new NullPointerException();
        }

        return objectFieldOffset0(f);
    }

    /**
     * (For compile-time known instance fields in JDK code only) Reports the
     * location of the field with a given name in the storage allocation of its
     * class.
     * <p>
     * This API is used to avoid creating reflective Objects in Java code at
     * startup.  This should not be used to find fields in non-trusted code.
     * Use the {@link #objectFieldOffset(Field) Field}-accepting version for
     * arbitrary fields instead.
     *
     * @throws NullPointerException if any parameter is {@code null}.
     * @throws InternalError if the presumably known field couldn't be found
     *
     * @see #objectFieldOffset(Field)
     */
    public long objectFieldOffset(Class<?> c, String name) {
        if (c == null || name == null) {
            throw new NullPointerException();
        }

        long result = knownObjectFieldOffset0(c, name);
        if (result < 0) {
            String type = switch ((int) result) {
                case -2 -> "a static field";
                case -1 -> "not found";
                default -> "unknown";
            };
            throw new InternalError("Field %s.%s %s".formatted(c.getTypeName(), name, type));
        }
        return result;
    }

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
     *
     * @throws NullPointerException if the field is {@code null}
     * @throws IllegalArgumentException if the field is not static
     * @see #getInt(Object, long)
     */
    public long staticFieldOffset(Field f) {
        if (f == null) {
            throw new NullPointerException();
        }

        return staticFieldOffset0(f);
    }

    /**
     * Reports the location of a given static field, in conjunction with {@link
     * #staticFieldOffset}.
     * <p>Fetch the base "Object", if any, with which static fields of the
     * given class can be accessed via methods like {@link #getInt(Object,
     * long)}.  This value may be null.  This value may refer to an object
     * which is a "cookie", not guaranteed to be a real Object, and it should
     * not be used in any way except as argument to the get and put routines in
     * this class.
     *
     * @throws NullPointerException if the field is {@code null}
     * @throws IllegalArgumentException if the field is not static
     */
    public Object staticFieldBase(Field f) {
        if (f == null) {
            throw new NullPointerException();
        }

        return staticFieldBase0(f);
    }

    /**
     * Detects if the given class may need to be initialized. This is often
     * needed in conjunction with obtaining the static field base of a
     * class.
     * @return false only if a call to {@code ensureClassInitialized} would have no effect
     */
    public boolean shouldBeInitialized(Class<?> c) {
        if (c == null) {
            throw new NullPointerException();
        }

        return shouldBeInitialized0(c);
    }

    /**
     * Ensures the given class has been initialized (see JVMS-5.5 for details).
     * This is often needed in conjunction with obtaining the static field base
     * of a class.
     *
     * The call returns when either class {@code c} is fully initialized or
     * class {@code c} is being initialized and the call is performed from
     * the initializing thread. In the latter case a subsequent call to
     * {@link #shouldBeInitialized} will return {@code true}.
     */
    public void ensureClassInitialized(Class<?> c) {
        if (c == null) {
            throw new NullPointerException();
        }

        ensureClassInitialized0(c);
    }

    /**
     * Reports the offset of the first element in the storage allocation of a
     * given array class.  If {@link #arrayIndexScale} returns a non-zero value
     * for the same class, you may use that scale factor, together with this
     * base offset, to form new offsets to access elements of arrays of the
     * given class.
     * <p>
     * The return value is in the range of a {@code int}.  The return type is
     * {@code long} to emphasize that long arithmetic should always be used
     * for offset calculations to avoid overflows.
     *
     * @see #getInt(Object, long)
     * @see #putInt(Object, long, int)
     */
    public long arrayBaseOffset(Class<?> arrayClass) {
        if (arrayClass == null) {
            throw new NullPointerException();
        }

        return arrayBaseOffset0(arrayClass);
    }


    /** The value of {@code arrayBaseOffset(boolean[].class)} */
    public static final long ARRAY_BOOLEAN_BASE_OFFSET
            = theUnsafe.arrayBaseOffset(boolean[].class);

    /** The value of {@code arrayBaseOffset(byte[].class)} */
    public static final long ARRAY_BYTE_BASE_OFFSET
            = theUnsafe.arrayBaseOffset(byte[].class);

    /** The value of {@code arrayBaseOffset(short[].class)} */
    public static final long ARRAY_SHORT_BASE_OFFSET
            = theUnsafe.arrayBaseOffset(short[].class);

    /** The value of {@code arrayBaseOffset(char[].class)} */
    public static final long ARRAY_CHAR_BASE_OFFSET
            = theUnsafe.arrayBaseOffset(char[].class);

    /** The value of {@code arrayBaseOffset(int[].class)} */
    public static final long ARRAY_INT_BASE_OFFSET
            = theUnsafe.arrayBaseOffset(int[].class);

    /** The value of {@code arrayBaseOffset(long[].class)} */
    public static final long ARRAY_LONG_BASE_OFFSET
            = theUnsafe.arrayBaseOffset(long[].class);

    /** The value of {@code arrayBaseOffset(float[].class)} */
    public static final long ARRAY_FLOAT_BASE_OFFSET
            = theUnsafe.arrayBaseOffset(float[].class);

    /** The value of {@code arrayBaseOffset(double[].class)} */
    public static final long ARRAY_DOUBLE_BASE_OFFSET
            = theUnsafe.arrayBaseOffset(double[].class);

    /** The value of {@code arrayBaseOffset(Object[].class)} */
    public static final long ARRAY_OBJECT_BASE_OFFSET
            = theUnsafe.arrayBaseOffset(Object[].class);

    /**
     * Reports the scale factor for addressing elements in the storage
     * allocation of a given array class.  However, arrays of "narrow" types
     * will generally not work properly with accessors like {@link
     * #getByte(Object, long)}, so the scale factor for such classes is reported
     * as zero.
     * <p>
     * The computation of the actual memory offset should always use {@code
     * long} arithmetic to avoid overflows.
     *
     * @see #arrayBaseOffset
     * @see #getInt(Object, long)
     * @see #putInt(Object, long, int)
     */
    public int arrayIndexScale(Class<?> arrayClass) {
        if (arrayClass == null) {
            throw new NullPointerException();
        }

        return arrayIndexScale0(arrayClass);
    }


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
    public int addressSize() {
        return ADDRESS_SIZE;
    }

    /** The value of {@code addressSize()} */
    public static final int ADDRESS_SIZE = ADDRESS_SIZE0;

    /**
     * Reports the size in bytes of a native memory page (whatever that is).
     * This value will always be a power of two.
     */
    public int pageSize() { return PAGE_SIZE; }

    /**
     * Reports the size in bytes of a data cache line written back by
     * the hardware cache line flush operation available to the JVM or
     * 0 if data cache line flushing is not enabled.
     */
    public int dataCacheLineFlushSize() { return DATA_CACHE_LINE_FLUSH_SIZE; }

    /**
     * Rounds down address to a data cache line boundary as
     * determined by {@link #dataCacheLineFlushSize}
     * @return the rounded down address
     */
    public long dataCacheLineAlignDown(long address) {
        return (address & ~(DATA_CACHE_LINE_FLUSH_SIZE - 1));
    }

    /**
     * Returns true if data cache line writeback
     */
    public static boolean isWritebackEnabled() { return DATA_CACHE_LINE_FLUSH_SIZE != 0; }

    //--- random trusted operations from JNI:

    /**
     * Tells the VM to define a class, without security checks.  By default, the
     * class loader and protection domain come from the caller's class.
     */
    public Class<?> defineClass(String name, byte[] b, int off, int len,
                                ClassLoader loader,
                                ProtectionDomain protectionDomain) {
        if (b == null) {
            throw new NullPointerException();
        }
        if (len < 0) {
            throw new ArrayIndexOutOfBoundsException();
        }

        return defineClass0(name, b, off, len, loader, protectionDomain);
    }

    public native Class<?> defineClass0(String name, byte[] b, int off, int len,
                                        ClassLoader loader,
                                        ProtectionDomain protectionDomain);

    /**
     * Allocates an instance but does not run any constructor.
     * Initializes the class if it has not yet been.
     */
    @IntrinsicCandidate
    public native Object allocateInstance(Class<?> cls)
        throws InstantiationException;

    /**
     * Allocates an array of a given type, but does not do zeroing.
     * <p>
     * This method should only be used in the very rare cases where a high-performance code
     * overwrites the destination array completely, and compilers cannot assist in zeroing elimination.
     * In an overwhelming majority of cases, a normal Java allocation should be used instead.
     * <p>
     * Users of this method are <b>required</b> to overwrite the initial (garbage) array contents
     * before allowing untrusted code, or code in other threads, to observe the reference
     * to the newly allocated array. In addition, the publication of the array reference must be
     * safe according to the Java Memory Model requirements.
     * <p>
     * The safest approach to deal with an uninitialized array is to keep the reference to it in local
     * variable at least until the initialization is complete, and then publish it <b>once</b>, either
     * by writing it to a <em>volatile</em> field, or storing it into a <em>final</em> field in constructor,
     * or issuing a {@link #storeFence} before publishing the reference.
     * <p>
     * @implnote This method can only allocate primitive arrays, to avoid garbage reference
     * elements that could break heap integrity.
     *
     * @param componentType array component type to allocate
     * @param length array size to allocate
     * @throws IllegalArgumentException if component type is null, or not a primitive class;
     *                                  or the length is negative
     */
    public Object allocateUninitializedArray(Class<?> componentType, int length) {
       if (componentType == null) {
           throw new IllegalArgumentException("Component type is null");
       }
       if (!componentType.isPrimitive()) {
           throw new IllegalArgumentException("Component type is not primitive");
       }
       if (length < 0) {
           throw new IllegalArgumentException("Negative length");
       }
       return allocateUninitializedArray0(componentType, length);
    }

    @IntrinsicCandidate
    private Object allocateUninitializedArray0(Class<?> componentType, int length) {
       // These fallbacks provide zeroed arrays, but intrinsic is not required to
       // return the zeroed arrays.
       if (componentType == byte.class)    return new byte[length];
       if (componentType == boolean.class) return new boolean[length];
       if (componentType == short.class)   return new short[length];
       if (componentType == char.class)    return new char[length];
       if (componentType == int.class)     return new int[length];
       if (componentType == float.class)   return new float[length];
       if (componentType == long.class)    return new long[length];
       if (componentType == double.class)  return new double[length];
       return null;
    }

    /** Throws the exception without telling the verifier. */
    public native void throwException(Throwable ee);

    // Here is the zoo of CAS operations in use (beyond the VH later):
    // compareAndSet{Reference,Long,Int} (Volatile only)
    // compareAndExchange{Reference,Long,Int}{Plain,Acquire,Release,''}
    // weakCompareAndSet{Reference,Long,Int}{Plain,Acquire,Release,''}
    // The default is MO_VOLATILE, so MO_PLAIN must be requested specifically.
    // When a specific "flavor" is not available, MO_VOLATILE is a good fallback.

    // The actual API points come in a more regular zoo:
    // compareAndSet{Reference,Long,Int,{otherprims}}{'',MO}
    // compareAndExchange{Reference,Long,Int,{otherprims}}{'',MO}
    // weakCompareAndSet{Reference,Long,Int}  (only 2 primitives)
    // The explicit MO argument is optional; MO_VOLATILE is the default.
    // The weak CAS can be obtained from the regular one with MO_WEAK_CAS.

    /**
     * Atomically updates Java variable to {@code x} if it is currently
     * holding {@code expected}.
     *
     * Volatile memory order is used, and spurious failures (weak CAS)
     * are excluded, but see also {@link #compareAndSetReferenceMO}.
     *
     * @return {@code true} if successful
     */
    @ForceInline
    public boolean compareAndSetReference(Object o, long offset,
                                                Object expected,
                                                Object x) {
        return compareAndSetReferenceMO(MO_VOLATILE, o, offset, expected, x);
    }

    /**
     * Atomically updates Java variable to {@code x} if it is currently
     * holding {@code expected}.
     *
     * <p>When {@code memoryOrder} is {@code MO_VOLATILE}, this
     * operation has memory semantics of a {@code volatile} read
     * and write.  Corresponds to C11 atomic_compare_exchange_strong.
     *
     * <p>When the bit {@link MO_WEAK_CAS} is set within the bits of
     * {@code memoryOrder}, weak compare and set is selected.
     * Otherwise, it is a strong CAS, and {@code MO_VOLATILE}
     * should be selected.
     *
     * @return {@code true} if successful
     */
    @IntrinsicCandidate
    public native boolean compareAndSetReferenceMO(byte memoryOrder,
                                                         Object o, long offset,
                                                         Object expected,
                                                         Object x);

    /**
     * Atomically updates Java variable to {@code x} if it is currently
     * holding {@code expected}.
     *
     * Volatile memory order is used, but see also {@link #compareAndExchangeReferenceMO}.
     *
     * @return the previous value of the Java variable
     */
    @ForceInline
    public Object compareAndExchangeReference(Object o, long offset,
                                                    Object expected,
                                                    Object x) {
        return compareAndExchangeReferenceMO(MO_VOLATILE, o, offset, expected, x);
    }

    /** Convenience for {@code compareAndSetReferenceMO(MO_WEAK_CAS_VOLATILE ...)}. */
    @ForceInline
    public boolean weakCompareAndSetReference(Object o, long offset,
                                                    Object expected,
                                                    Object x) {
        return compareAndSetReferenceMO(MO_WEAK_CAS_VOLATILE, o, offset, expected, x);
    }

    // The native method can treat every memory access as MO_VOLATILE.
    @IntrinsicCandidate
    public native Object compareAndExchangeReferenceMO(byte memoryOrder,
                                                             Object o, long offset,
                                                             Object expected,
                                                             Object x);

    /**
     * Intrinsic for performing compare-and-set on a primitive variable
     * of any primitive type.  See {@link #putPrimitiveBitsMO} for the
     * relevant conventions.
     *
     * <p>As with {@link #compareAndSetReferenceMO}, the bit
     * {@link MO_WEAK_CAS} may be set within {@code memoryOrder}.
     */
    @IntrinsicCandidate
    private final boolean compareAndSetPrimitiveBitsMO(byte memoryOrder,
                                                       byte basicType,
                                                       Object o, long offset,
                                                       long expected,
                                                       long x) {
        // This intrinsic has a body so that the JIT can refuse to
        // expand the intrinsic, when it sees a request it does not
        // understand.

        // The purpose of the decision tree is to present the JIT with
        // optimizable statements, even if memoryOrder or basicType
        // fail to be constant.

        // This also allows the JIT to compile the body to serve calls
        // from the interpreter and lukewarm code.
        checkBasicType(basicType);
        memoryOrder &= ~MO_WEAK_CAS;  // intrinsic might use this bit but not here
        checkMemoryOrder(memoryOrder);
        switch (basicType & PRIMITIVE_SIZE_MASK) {  // encodes size of BT
            case 3 -> {
                // hardware must support 8-byte CAS
                return compareAndSetPrimitiveBitsMONative(MO_VOLATILE, BT_LONG, o, offset,
                                                          expected, x);
            }
            case 2 -> {
                // hardware must support 4-byte CAS
                return compareAndSetPrimitiveBitsMONative(MO_VOLATILE, BT_INT, o, offset,
                                                          expected, x);
            }
            case 1 -> {
                // The hardware might not have 2-byte CAS; simulate with volatile 2-byte cmpxchg.
                return ((short) compareAndExchangePrimitiveBitsMO(MO_VOLATILE, BT_SHORT, o, offset,
                                                                  (short)expected, (short)x)
                        == (short)expected);
            }
            default -> {
                // The hardware might not have 1-byte CAS; simulate with volatile 1-byte cmpxchg.
                return ((byte) compareAndExchangePrimitiveBitsMO(MO_VOLATILE, BT_BYTE, o, offset,
                                                                 (byte)expected, (byte)x)
                        == (byte)expected);
            }
        }
    }

    // Second try for the JIT, if it dislikes a memory order or data type.
    // Both this method and the previous are handled by the same native code
    // and the same compiler intrinsic logic.
    // The native method can treat every memory access as MO_VOLATILE.
    // The native method can assert on data types which have no atomic support.
    // The native method can assert on combinations which do not make sense.
    @IntrinsicCandidate
    private native boolean compareAndSetPrimitiveBitsMONative(byte memoryOrder,
                                                              byte basicType,
                                                              Object o, long offset,
                                                              long expected,
                                                              long x);

    /**
     * Follows the conventions of {@link #putPrimitiveBitsMO}.
     * @return the value fetched from the indicated Java variable,
     *         padded by garbage high-order bits (if smaller than 64 bits)
     */
    @IntrinsicCandidate
    private final long compareAndExchangePrimitiveBitsMO(byte memoryOrder,
                                                         byte basicType,
                                                         Object o, long offset,
                                                         long expected,
                                                         long x) {
        // This intrinsic has a body so that the JIT can refuse to
        // expand the intrinsic, when it sees a request it does not
        // understand.

        // The purpose of the decision tree is to present the JIT with
        // optimizable statements, even if memoryOrder or basicType
        // fail to be constant.

        // This also allows the JIT to compile the body to serve calls
        // from the interpreter and lukewarm code.
        checkBasicType(basicType);
        checkMemoryOrder(memoryOrder);
        switch (basicType & PRIMITIVE_SIZE_MASK) {  // encodes size of BT
            case 3 -> {
                // hardware must support 8-byte cmpxchg
                return compareAndExchangePrimitiveBitsMONative(MO_VOLATILE, BT_LONG, o, offset,
                                                               expected, x);
            }
            case 2 -> {
                // hardware must support 4-byte cmpxchg
                return compareAndExchangePrimitiveBitsMONative(MO_VOLATILE, BT_INT, o, offset,
                                                               expected, x);
            }
            case 1 -> {
                // The default implementation updates a byte or short inside an atomic
                // int container, if the JIT refuses to expand this intrinsic.
                return (short) compareAndExchangeUsingIntSlow(o, offset, (short)expected, (short)x, Short.SIZE / Byte.SIZE);
            }

            default -> {
                return (byte) compareAndExchangeUsingIntSlow(o, offset, (byte)expected, (byte)x, 1);
            }
        }
    }

    // Second try for the JIT, if it dislikes a memory order or data type.
    // Both this method and the previous are handled by the same native code
    // and the same compiler intrinsic logic.
    // The native method can treat every memory access as MO_VOLATILE.
    // The native method can assert on data types which have no atomic support.
    // The native method can assert on combinations which do not make sense.
    // Prefer this method when you don't need the case analysis of the previous one.
    @IntrinsicCandidate
    private native long compareAndExchangePrimitiveBitsMONative(byte memoryOrder,
                                                                byte basicType,
                                                                Object o, long offset,
                                                                long expected,
                                                                long x);

    // Fallback implementation of cmpxchg for subword types.
    private int compareAndExchangeUsingIntSlow(Object o, long offset,
                                               int expected,
                                               int x, int byteSize) {
        if (!(byteSize == 1 || byteSize == 2)) {
            throw new IllegalArgumentException("bad subword size");
        }
        int bitSize = byteSize * Byte.SIZE;
        int allbits = (1 << bitSize) - 1;  // 0xFFFF or 0xFF
        expected &= allbits;
        x &= allbits;
        int byteOffset = (int)(offset & 3);
        int bitOffset = byteOffset * Byte.SIZE;
        if (bitOffset + bitSize > Integer.SIZE) {
            throw new IllegalArgumentException("Update spans the word, not supported");
        }
        long wordOffset = offset - byteOffset;
        int shift = bitOffset;
        if (BIG_ENDIAN) {       // start counting from the top of the word
            shift = Integer.SIZE - bitSize - shift;
        }
        int mask           = allbits  << shift;
        int maskedExpected = expected << shift;
        int maskedX        = x        << shift;
        int fullWord;
        do {
            fullWord = getInt(o, wordOffset);
            if ((fullWord & mask) != maskedExpected) {
                return (fullWord >>> shift) & allbits;
            }
        } while (!weakCompareAndSetInt(o, wordOffset, fullWord, (fullWord & ~mask) | maskedX));
        return expected;
    }

    /**
     * Atomically updates Java variable to {@code x} if it is currently
     * holding {@code expected}.
     *
     * <p>This operation has memory semantics of a {@code volatile} read
     * and write.  Corresponds to C11 atomic_compare_exchange_strong.
     *
     * @return {@code true} if successful
     */
    @ForceInline
    public boolean compareAndSetInt(Object o, long offset,
                                          int expected,
                                          int x) {
        return compareAndSetPrimitiveBitsMONative(MO_VOLATILE, BT_INT, o, offset, expected, x);
    }

    /** Special-access version of {@code compareAndSetInt}. */
    @ForceInline
    public boolean compareAndSetIntMO(byte memoryOrder,
                                            Object o, long offset,
                                            int expected,
                                            int x) {
        return compareAndSetPrimitiveBitsMONative(memoryOrder, BT_INT, o, offset, expected, x);
    }

    @ForceInline
    public int compareAndExchangeInt(Object o, long offset,
                                           int expected,
                                           int x) {
        return (int) compareAndExchangePrimitiveBitsMONative(MO_VOLATILE, BT_INT,
                                                             o, offset, expected, x);
    }

    /** Special-access version of {@code compareAndExchangeInt}. */
    @ForceInline
    public int compareAndExchangeIntMO(byte memoryOrder,
                                             Object o, long offset,
                                             int expected,
                                             int x) {
        return (int) compareAndExchangePrimitiveBitsMONative(memoryOrder, BT_INT,
                                                             o, offset, expected, x);
    }

    /** Convenience for {@code compareAndSetIntMO(MO_WEAK_CAS_VOLATILE ...)}. */
    @ForceInline
    public boolean weakCompareAndSetInt(Object o, long offset,
                                              int expected,
                                              int x) {
        return compareAndSetIntMO(MO_WEAK_CAS_VOLATILE, o, offset, expected, x);
    }

    @ForceInline
    public byte compareAndExchangeByte(Object o, long offset,
                                             byte expected,
                                             byte x) {
        return (byte) compareAndExchangePrimitiveBitsMO(MO_VOLATILE, BT_BYTE,
                                                        o, offset, expected, x);
    }

    @ForceInline
    public byte compareAndExchangeByteMO(byte memoryOrder,
                                               Object o, long offset,
                                               byte expected,
                                               byte x) {
        return (byte) compareAndExchangePrimitiveBitsMO(memoryOrder, BT_BYTE,
                                                        o, offset, expected,
                                                        x);
    }

    // The compiler may replace this intrinsic if the backend supports
    // a boolean-returning CAS, for the particular requested memory order.
    @ForceInline
    public boolean compareAndSetByteMO(byte memoryOrder,
                                             Object o, long offset,
                                             byte expected,
                                             byte x) {
        return compareAndSetPrimitiveBitsMO(memoryOrder, BT_BYTE, o, offset, expected, x);
    }

    @ForceInline
    public boolean compareAndSetByte(Object o, long offset,
                                           byte expected,
                                           byte x) {
        return compareAndSetPrimitiveBitsMO(MO_VOLATILE, BT_BYTE, o, offset, expected, x);
    }

    @ForceInline
    public short compareAndExchangeShort(Object o, long offset,
                                               short expected,
                                               short x) {
        return compareAndExchangeShortMO(MO_VOLATILE, o, offset, expected, x);
    }

    @ForceInline
    public short compareAndExchangeShortMO(byte memoryOrder,
                                                 Object o, long offset,
                                                 short expected,
                                                 short x) {
        return (short) compareAndExchangePrimitiveBitsMO(memoryOrder, BT_SHORT,
                                                         o, offset, expected, x);
    }

    @ForceInline
    public boolean compareAndSetShortMO(byte memoryOrder,
                                              Object o, long offset,
                                              short expected,
                                              short x) {
        return compareAndSetPrimitiveBitsMO(memoryOrder, BT_SHORT, o, offset, expected, x);
    }

    @ForceInline
    public boolean compareAndSetShort(Object o, long offset,
                                            short expected,
                                            short x) {
        return compareAndSetPrimitiveBitsMO(MO_VOLATILE, BT_SHORT, o, offset, expected, x);
    }

    @ForceInline
    private char s2c(short s) {
        return (char) s;
    }

    @ForceInline
    private short c2s(char s) {
        return (short) s;
    }

    @ForceInline
    public boolean compareAndSetChar(Object o, long offset,
                                           char expected,
                                           char x) {
        return compareAndSetShortMO(MO_VOLATILE, o, offset, c2s(expected), c2s(x));
    }

    @ForceInline
    public boolean compareAndSetCharMO(byte memoryOrder,
                                             Object o, long offset,
                                             char expected,
                                             char x) {
        return compareAndSetShortMO(memoryOrder, o, offset, c2s(expected), c2s(x));
    }

    @ForceInline
    public char compareAndExchangeChar(Object o, long offset,
                                             char expected,
                                             char x) {
        return s2c(compareAndExchangeShort(o, offset, c2s(expected), c2s(x)));
    }

    @ForceInline
    public char compareAndExchangeCharMO(byte memoryOrder,
                                               Object o, long offset,
                                               char expected,
                                               char x) {
        return s2c(compareAndExchangeShortMO(memoryOrder, o, offset, c2s(expected), c2s(x)));
    }

    /**
     * The JVM converts integral values to boolean values using two
     * different conventions, byte testing against zero and truncation
     * to least-significant bit.
     *
     * <p>The JNI documents specify that, at least for returning
     * values from native methods, a Java boolean value is converted
     * to the value-set 0..1 by first truncating to a byte (0..255 or
     * maybe -128..127) and then testing against zero. Thus, Java
     * booleans in non-Java data structures are by convention
     * represented as 8-bit containers containing either zero (for
     * false) or any non-zero value (for true).
     *
     * <p>Java booleans in the heap are also stored in bytes, but are
     * strongly normalized to the value-set 0..1 (i.e., they are
     * truncated to the least-significant bit).
     *
     * <p>The main reason for having different conventions for
     * conversion is performance: Truncation to the least-significant
     * bit can be usually implemented with fewer (machine)
     * instructions than byte testing against zero.
     *
     * <p>A number of Unsafe methods load boolean values from the heap
     * as bytes. Unsafe converts those values according to the JNI
     * rules (i.e, using the "testing against zero" convention). The
     * method {@code byte2bool} implements that conversion.
     *
     * @param b the byte to be converted to boolean
     * @return the result of the conversion
     */
    @ForceInline
    private boolean byte2bool(byte b) {
        return b != 0;
    }

    /**
     * Convert a boolean value to a byte. The return value is strongly
     * normalized to the value-set 0..1 (i.e., the value is truncated
     * to the least-significant bit). See {@link #byte2bool(byte)} for
     * more details on conversion conventions.
     *
     * @param b the boolean to be converted to byte (and then normalized)
     * @return the result of the conversion
     */
    @ForceInline
    private byte bool2byte(boolean b) {
        return b ? (byte)1 : (byte)0;
    }

    @ForceInline
    public boolean compareAndSetBoolean(Object o, long offset,
                                              boolean expected,
                                              boolean x) {
        return compareAndSetByte(o, offset, bool2byte(expected), bool2byte(x));
    }

    @ForceInline
    public boolean compareAndSetBooleanMO(byte memoryOrder,
                                                Object o, long offset,
                                                boolean expected,
                                                boolean x) {
        return compareAndSetByteMO(memoryOrder, o, offset, bool2byte(expected), bool2byte(x));
    }

    @ForceInline
    public boolean compareAndExchangeBoolean(Object o, long offset,
                                                   boolean expected,
                                                   boolean x) {
        return byte2bool(compareAndExchangeByte(o, offset, bool2byte(expected), bool2byte(x)));
    }

    @ForceInline
    public boolean compareAndExchangeBooleanMO(byte memoryOrder,
                                                     Object o, long offset,
                                                     boolean expected,
                                                     boolean x) {
        return byte2bool(compareAndExchangeByteMO(memoryOrder, o, offset, bool2byte(expected), bool2byte(x)));
    }

    /**
     * Atomically updates Java variable to {@code x} if it is currently
     * holding {@code expected}.
     *
     * <p>This operation has memory semantics of a {@code volatile} read
     * and write.  Corresponds to C11 atomic_compare_exchange_strong.
     *
     * @return {@code true} if successful
     */
    @ForceInline
    public boolean compareAndSetFloat(Object o, long offset,
                                            float expected,
                                            float x) {
        return compareAndSetInt(o, offset,
                                Float.floatToRawIntBits(expected),
                                Float.floatToRawIntBits(x));
    }

    @ForceInline
    public boolean compareAndSetFloatMO(byte memoryOrder,
                                              Object o, long offset,
                                              float expected,
                                              float x) {
        return compareAndSetIntMO(memoryOrder, o, offset,
                                  Float.floatToRawIntBits(expected),
                                  Float.floatToRawIntBits(x));
    }

    @ForceInline
    public float compareAndExchangeFloat(Object o, long offset,
                                               float expected,
                                               float x) {
        int w = compareAndExchangeInt(o, offset,
                                      Float.floatToRawIntBits(expected),
                                      Float.floatToRawIntBits(x));
        return Float.intBitsToFloat(w);
    }

    @ForceInline
    public float compareAndExchangeFloatMO(byte memoryOrder,
                                                 Object o, long offset,
                                                 float expected,
                                                 float x) {
        int w = compareAndExchangeIntMO(memoryOrder, o, offset,
                                        Float.floatToRawIntBits(expected),
                                        Float.floatToRawIntBits(x));
        return Float.intBitsToFloat(w);
    }

    /**
     * Atomically updates Java variable to {@code x} if it is currently
     * holding {@code expected}.
     *
     * <p>This operation has memory semantics of a {@code volatile} read
     * and write.  Corresponds to C11 atomic_compare_exchange_strong.
     *
     * @return {@code true} if successful
     */
    @ForceInline
    public boolean compareAndSetDouble(Object o, long offset,
                                             double expected,
                                             double x) {
        return compareAndSetLong(o, offset,
                                 Double.doubleToRawLongBits(expected),
                                 Double.doubleToRawLongBits(x));
    }

    @ForceInline
    public boolean compareAndSetDoubleMO(byte memoryOrder,
                                               Object o, long offset,
                                               double expected,
                                               double x) {
        return compareAndSetLongMO(memoryOrder, o, offset,
                                   Double.doubleToRawLongBits(expected),
                                   Double.doubleToRawLongBits(x));
    }

    @ForceInline
    public double compareAndExchangeDouble(Object o, long offset,
                                                 double expected,
                                                 double x) {
        long w = compareAndExchangeLong(o, offset,
                                        Double.doubleToRawLongBits(expected),
                                        Double.doubleToRawLongBits(x));
        return Double.longBitsToDouble(w);
    }

    @ForceInline
    public double compareAndExchangeDoubleMO(byte memoryOrder,
                                                   Object o, long offset,
                                                   double expected,
                                                   double x) {
        long w = compareAndExchangeLongMO(memoryOrder, o, offset,
                                          Double.doubleToRawLongBits(expected),
                                          Double.doubleToRawLongBits(x));
        return Double.longBitsToDouble(w);
    }

    /**
     * Atomically updates Java variable to {@code x} if it is currently
     * holding {@code expected}.
     *
     * <p>This operation has memory semantics of a {@code volatile} read
     * and write.  Corresponds to C11 atomic_compare_exchange_strong.
     *
     * @return {@code true} if successful
     */
    @ForceInline
    public boolean compareAndSetLong(Object o, long offset,
                                           long expected,
                                           long x) {
        return compareAndSetPrimitiveBitsMONative(MO_VOLATILE, BT_LONG, o, offset, expected, x);
    }

    /** Special-access version. */
    @ForceInline
    public boolean compareAndSetLongMO(byte memoryOrder,
                                             Object o, long offset,
                                             long expected,
                                             long x) {
        return compareAndSetPrimitiveBitsMONative(memoryOrder, BT_LONG, o, offset, expected, x);
    }

    /** Regular volatile version. */
    @ForceInline
    public long compareAndExchangeLong(Object o, long offset,
                                             long expected,
                                             long x) {
        return compareAndExchangePrimitiveBitsMONative(MO_VOLATILE, BT_LONG,
                                                       o, offset, expected, x);
    }

    /** Special-access version. */
    @ForceInline
    public long compareAndExchangeLongMO(byte memoryOrder,
                                               Object o, long offset,
                                               long expected,
                                               long x) {
        return compareAndExchangePrimitiveBitsMONative(memoryOrder, BT_LONG,
                                                       o, offset, expected, x);
    }

    /** Convenience for {@code compareAndSetLongMO(MO_WEAK_CAS_VOLATILE ...)}. */
    @ForceInline
    public boolean weakCompareAndSetLong(Object o, long offset,
                                               long expected,
                                               long x) {
        return compareAndSetPrimitiveBitsMONative(MO_WEAK_CAS_VOLATILE, BT_LONG,
                                                  o, offset, expected, x);
    }

    // Note: The best way to get fine control over memory order is to
    // use an explicit memory order prefix argument like MO_RELEASE.
    // There used to be a large number of API points like
    // putBooleanRelease, getIntAcquire, getReferenceOpaque, etc.
    // Such operations are more regularly available via the MO arguments.

    // Because "volatile" is a Java modifier (unlike acquire or release),
    // old code (notably, old tests) use the MO_VOLATILE versions often.
    // So we keep those API points, as a compromise.  New code should use
    // the prefix MO_VOLATILE.

    /**
     * Fetches a reference value from a given Java variable, with volatile
     * load semantics.
     * Identical to {@link #getReferenceMO(byte, Object, long)}
     * with the prefix argument MO_VOLATILE.
     */
    @ForceInline
    public Object getReferenceVolatile(Object o, long offset) {
        return getReferenceMO(MO_VOLATILE, o, offset);
    }

    /**
     * Stores a reference value into a given Java variable, with
     * volatile store semantics.
     * Identical to {@link #putReferenceMO(byte, Object, long, Object)}
     * with the prefix argument MO_VOLATILE.
     */
    @ForceInline
    public void putReferenceVolatile(Object o, long offset, Object x) {
        putReferenceMO(MO_VOLATILE, o, offset, x);
    }

    /** Specialization to MO_VOLATILE of {@link #getIntMO(byte, Object, long)}  */
    @ForceInline
    public int     getIntVolatile(Object o, long offset) {
        return getIntMO(MO_VOLATILE, o, offset);
    }

    /** Specialization to MO_VOLATILE of {@link #putIntMO(byte, Object, long, int)}  */
    @ForceInline
    public void    putIntVolatile(Object o, long offset, int x) {
        putIntMO(MO_VOLATILE, o, offset, x);
    }

    /** Specialization to MO_VOLATILE of {@link #getBooleanMO(byte, Object, long)}  */
    @ForceInline
    public boolean getBooleanVolatile(Object o, long offset) {
        return getBooleanMO(MO_VOLATILE, o, offset);
    }

    /** Specialization to MO_VOLATILE of {@link #putBooleanMO(byte, Object, long, boolean)}  */
    @ForceInline
    public void    putBooleanVolatile(Object o, long offset, boolean x) {
        putBooleanMO(MO_VOLATILE, o, offset, x);
    }

    /** Specialization to MO_VOLATILE of {@link #getByteMO(byte, Object, long)}  */
    @ForceInline
    public byte    getByteVolatile(Object o, long offset) {
        return getByteMO(MO_VOLATILE, o, offset);
    }

    /** Specialization to MO_VOLATILE of {@link #putByteMO(byte, Object, long, byte)}  */
    @ForceInline
    public void    putByteVolatile(Object o, long offset, byte x) {
        putByteMO(MO_VOLATILE, o, offset, x);
    }

    /** Specialization to MO_VOLATILE of {@link #getShortMO(byte, Object, long)}  */
    @ForceInline
    public short   getShortVolatile(Object o, long offset) {
        return getShortMO(MO_VOLATILE, o, offset);
    }

    /** Specialization to MO_VOLATILE of {@link #putShortMO(byte, Object, long, short)}  */
    @ForceInline
    public void    putShortVolatile(Object o, long offset, short x) {
        putShortMO(MO_VOLATILE, o, offset, x);
    }

    /** Specialization to MO_VOLATILE of {@link #getCharMO(byte, Object, long)}  */
    @ForceInline
    public char    getCharVolatile(Object o, long offset) {
        return getCharMO(MO_VOLATILE, o, offset);
    }

    /** Specialization to MO_VOLATILE of {@link #putCharMO(byte, Object, long, char)}  */
    @ForceInline
    public void    putCharVolatile(Object o, long offset, char x) {
        putCharMO(MO_VOLATILE, o, offset, x);
    }

    /** Specialization to MO_VOLATILE of {@link #getLongMO(byte, Object, long)}  */
    @ForceInline
    public long    getLongVolatile(Object o, long offset) {
        return getLongMO(MO_VOLATILE, o, offset);
    }

    /** Specialization to MO_VOLATILE of {@link #putLongMO(byte, Object, long, long)}  */
    @ForceInline
    public void    putLongVolatile(Object o, long offset, long x) {
        putLongMO(MO_VOLATILE, o, offset, x);
    }

    /** Specialization to MO_VOLATILE of {@link #getFloatMO(byte, Object, long)}  */
    @ForceInline
    public float   getFloatVolatile(Object o, long offset) {
        return getFloatMO(MO_VOLATILE, o, offset);
    }

    /** Specialization to MO_VOLATILE of {@link #putFloatMO(byte, Object, long, float)}  */
    @ForceInline
    public void    putFloatVolatile(Object o, long offset, float x) {
        putFloatMO(MO_VOLATILE, o, offset, x);
    }

    /** Specialization to MO_VOLATILE of {@link #getDoubleMO(byte, Object, long)}  */
    @ForceInline
    public double  getDoubleVolatile(Object o, long offset) {
        return getDoubleMO(MO_VOLATILE, o, offset);
    }

    /** Specialization to MO_VOLATILE of {@link #putDoubleMO(byte, Object, long, double)}  */
    @ForceInline
    public void    putDoubleVolatile(Object o, long offset, double x) {
        putDoubleMO(MO_VOLATILE, o, offset, x);
    }

    /*
     * Note on stores:
     * Variations of {@link #putReferenceMO(MO_RELEASE, Object, long, Object)}
     * do not guarantee immediate visibility of the store to
     * other threads. This method is generally only useful if the
     * underlying field is a Java volatile (or if an array cell, one
     * that is otherwise only accessed using volatile accesses).
     *
     * Corresponds to C11 atomic_store_explicit(..., memory_order_release).
     */

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
    @IntrinsicCandidate
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
    @IntrinsicCandidate
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
    public int getLoadAverage(double[] loadavg, int nelems) {
        if (nelems < 0 || nelems > 3 || nelems > loadavg.length) {
            throw new ArrayIndexOutOfBoundsException();
        }

        return getLoadAverage0(loadavg, nelems);
    }

    /**
     * Intrinsic for performing get-and-operate on a primitive variable
     * of any primitive type.  See {@link #putPrimitiveBitsMO} for the
     * relevant conventions.  Atomically update the variable using the
     * given operation and operand, as if by {@code v = v op operand}.
     * Return the previous value of the variable.
     * <p>
     * The operator must be a character in {@code "+&|^="}, selecting
     * get and one of add, bitwise and, bitwise or, bitwise xor, or
     * (simply) set.
     * <p>
     * This intrinsic has a default implementation, using a weak CAS
     * loop, for platforms which do not support the relevant native
     * instructions.  The VM is free to use the default
     * implementation, even if the instructions exist.
     * @return the value previously stored in the indicated Java variable,
     *         padded by garbage high-order bits (if smaller than 64 bits)
     */
    @ForceInline
    @IntrinsicCandidate
    private long getAndOperatePrimitiveBitsMO(byte memoryOrder,
                                              byte basicType,
                                              byte op,
                                              Object o, long offset,
                                              long operand) {
        checkMemoryOrder(memoryOrder);
        checkOperatorForCAS(op, basicType);
        byte memoryOrderForLoad = memoryOrder;
        memoryOrderForLoad &= ~MO_RELEASE;  // do not release when loading
        if (memoryOrderForLoad == 0)  memoryOrderForLoad = MO_PLAIN;
        byte memoryOrderForCAS = memoryOrder;
        memoryOrderForCAS |= MO_WEAK_CAS;  // use a weak-CAS loop, if it helps
        long v;
        long nextv;
        // This loop used to be hand-copied 5x (once per OP) and also 4x (once per MO).
        // This single-instance form is equally effective, because it folds up in the JIT.
        do {
            v = getPrimitiveBitsMO(memoryOrderForLoad, basicType, o, offset);
            nextv = switch (op) {
                // All of these are "T-functions", compatible with long evaluation.
                // Float add, or signed min/max, would not be T-functions.
                case OP_ADD    -> v + operand;
                case OP_BITOR  -> v | operand;
                case OP_BITAND -> v & operand;
                case OP_BITXOR -> v ^ operand;
                default        ->     operand;  // reached as case OP_SWAP
            };
        } while (!compareAndSetPrimitiveBitsMO(memoryOrderForCAS, basicType, o, offset, v, nextv));
        return v;
    }

    @ForceInline
    private static void checkOperatorForCAS(byte op, byte basicType) {
        if ((op == OP_ADD | op == OP_BITOR | op == OP_BITAND | op == OP_BITXOR | op == OP_SWAP) &
            (basicType >= BT_BYTE & basicType <= BT_LONG))
            return;  // OK arguments
        // No direct add or other bitwise ops for boolean, char, float, double.
        // Those cases are handled carefully by other means.
        throw new IllegalArgumentException("bad op or type");
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
    @ForceInline
    public int getAndAddInt(Object o, long offset, int delta) {
        return (int) getAndOperatePrimitiveBitsMO(MO_VOLATILE, BT_INT, OP_ADD, o, offset, delta);
    }

    @ForceInline
    public int getAndAddIntMO(byte memoryOrder, Object o, long offset, int delta) {
        return (int) getAndOperatePrimitiveBitsMO(memoryOrder, BT_INT, OP_ADD, o, offset, delta);
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
    @ForceInline
    public long getAndAddLong(Object o, long offset, long delta) {
        return getAndOperatePrimitiveBitsMO(MO_VOLATILE, BT_LONG, OP_ADD, o, offset, delta);
    }

    @ForceInline
    public long getAndAddLongMO(byte memoryOrder, Object o, long offset, long delta) {
        return getAndOperatePrimitiveBitsMO(memoryOrder, BT_LONG, OP_ADD, o, offset, delta);
    }

    @ForceInline
    public byte getAndAddByte(Object o, long offset, byte delta) {
        return (byte) getAndOperatePrimitiveBitsMO(MO_VOLATILE, BT_BYTE, OP_ADD, o, offset, delta);
    }

    @ForceInline
    public byte getAndAddByteMO(byte memoryOrder, Object o, long offset, byte delta) {
        return (byte) getAndOperatePrimitiveBitsMO(memoryOrder, BT_BYTE, OP_ADD, o, offset, delta);
    }

    @ForceInline
    public short getAndAddShort(Object o, long offset, short delta) {
        return (short) getAndOperatePrimitiveBitsMO(MO_VOLATILE, BT_SHORT, OP_ADD, o, offset, delta);
    }

    @ForceInline
    public short getAndAddShortMO(byte memoryOrder, Object o, long offset, short delta) {
        return (short) getAndOperatePrimitiveBitsMO(memoryOrder, BT_SHORT, OP_ADD, o, offset, delta);
    }

    @ForceInline
    public char getAndAddChar(Object o, long offset, char delta) {
        return (char) getAndAddShort(o, offset, (short) delta);
    }

    @ForceInline
    public char getAndAddCharMO(byte memoryOrder, Object o, long offset, char delta) {
        return (char) getAndAddShortMO(memoryOrder, o, offset, (short) delta);
    }

    @ForceInline
    public float getAndAddFloat(Object o, long offset, float delta) {
        return getAndAddFloatMO(MO_VOLATILE, o, offset, delta);
    }

    /** For completeness.  It is pretty ugly. */
    @ForceInline
    public float getAndAddFloatMO(byte memoryOrder, Object o, long offset, float delta) {
        checkMemoryOrder(memoryOrder);
        byte memoryOrderForLoad = memoryOrder;
        memoryOrderForLoad &= ~MO_RELEASE;  // do not release when loading
        if (memoryOrderForLoad == 0)  memoryOrderForLoad = MO_PLAIN;
        byte memoryOrderForCAS = memoryOrder;
        memoryOrderForCAS |= MO_WEAK_CAS;
        int expectedBits;
        float v;
        do {
            // Load and CAS with the raw bits to avoid issues with NaNs and
            // possible bit conversion from signaling NaNs to quiet NaNs that
            // may result in the loop not terminating.
            expectedBits = getIntMO(memoryOrderForLoad, o, offset);
            v = Float.intBitsToFloat(expectedBits);
        } while (!compareAndSetIntMO(memoryOrderForCAS, o, offset,
                                     expectedBits, Float.floatToRawIntBits(v + delta)));
        return v;
    }

    @ForceInline
    public double getAndAddDouble(Object o, long offset, double delta) {
        return getAndAddDoubleMO(MO_VOLATILE, o, offset, delta);
    }

    /** For completeness.  It is pretty ugly. */
    @ForceInline
    public double getAndAddDoubleMO(byte memoryOrder, Object o, long offset, double delta) {
        checkMemoryOrder(memoryOrder);
        byte memoryOrderForLoad = memoryOrder;
        memoryOrderForLoad &= ~MO_RELEASE;  // do not release when loading
        if (memoryOrderForLoad == 0)  memoryOrderForLoad = MO_PLAIN;
        byte memoryOrderForCAS = memoryOrder;
        memoryOrderForCAS |= MO_WEAK_CAS;
        long expectedBits;
        double v;
        do {
            // Load and CAS with the raw bits to avoid issues with NaNs and
            // possible bit conversion from signaling NaNs to quiet NaNs that
            // may result in the loop not terminating.
            expectedBits = getLongMO(memoryOrderForLoad, o, offset);
            v = Double.longBitsToDouble(expectedBits);
        } while (!compareAndSetLongMO(memoryOrderForCAS, o, offset,
                                      expectedBits, Double.doubleToRawLongBits(v + delta)));
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
    @ForceInline
    public int getAndSetInt(Object o, long offset, int newValue) {
        return (int) getAndOperatePrimitiveBitsMO(MO_VOLATILE, BT_INT, OP_SWAP, o, offset, newValue);
    }

    @ForceInline
    public int getAndSetIntMO(byte memoryOrder, Object o, long offset, int newValue) {
        return (int) getAndOperatePrimitiveBitsMO(memoryOrder, BT_INT, OP_SWAP, o, offset, newValue);
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
    @ForceInline
    public long getAndSetLong(Object o, long offset, long newValue) {
        return getAndOperatePrimitiveBitsMO(MO_VOLATILE, BT_LONG, OP_SWAP, o, offset, newValue);
    }

    @ForceInline
    public long getAndSetLongMO(byte memoryOrder, Object o, long offset, long newValue) {
        return getAndOperatePrimitiveBitsMO(memoryOrder, BT_LONG, OP_SWAP, o, offset, newValue);
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
    @ForceInline
    public Object getAndSetReference(Object o, long offset, Object newValue) {
        return getAndSetReferenceMO(MO_VOLATILE, o, offset, newValue);
    }

    @ForceInline
    @IntrinsicCandidate
    public Object getAndSetReferenceMO(byte memoryOrder, Object o, long offset, Object newValue) {
        checkMemoryOrder(memoryOrder);
        byte memoryOrderForLoad = memoryOrder;
        memoryOrderForLoad &= ~MO_RELEASE;  // do not release when loading
        if (memoryOrderForLoad == 0)  memoryOrderForLoad = MO_PLAIN;
        byte memoryOrderForCAS = memoryOrder;
        memoryOrderForCAS |= MO_WEAK_CAS;
        Object v;
        do {
            v = getReferenceMO(memoryOrderForLoad, o, offset);
        } while (!compareAndSetReferenceMO(memoryOrderForCAS, o, offset, v, newValue));
        return v;
    }

    @ForceInline
    public byte getAndSetByte(Object o, long offset, byte newValue) {
        return (byte) getAndOperatePrimitiveBitsMO(MO_VOLATILE, BT_BYTE, OP_SWAP, o, offset, newValue);
    }

    @ForceInline
    public byte getAndSetByteMO(byte memoryOrder, Object o, long offset, byte newValue) {
        return (byte) getAndOperatePrimitiveBitsMO(memoryOrder, BT_BYTE, OP_SWAP, o, offset, newValue);
    }

    @ForceInline
    public boolean getAndSetBoolean(Object o, long offset, boolean newValue) {
        return byte2bool(getAndSetByte(o, offset, bool2byte(newValue)));
    }

    @ForceInline
    public boolean getAndSetBooleanMO(byte memoryOrder, Object o, long offset, boolean newValue) {
        return byte2bool(getAndSetByteMO(memoryOrder, o, offset, bool2byte(newValue)));
    }

    @ForceInline
    public short getAndSetShort(Object o, long offset, short newValue) {
        return (short) getAndOperatePrimitiveBitsMO(MO_VOLATILE, BT_SHORT, OP_SWAP, o, offset, newValue);
    }

    @ForceInline
    public short getAndSetShortMO(byte memoryOrder, Object o, long offset, short newValue) {
        return (short) getAndOperatePrimitiveBitsMO(memoryOrder, BT_SHORT, OP_SWAP, o, offset, newValue);
    }

    @ForceInline
    public char getAndSetChar(Object o, long offset, char newValue) {
        return s2c(getAndSetShort(o, offset, c2s(newValue)));
    }

    @ForceInline
    public char getAndSetCharMO(byte memoryOrder, Object o, long offset, char newValue) {
        return s2c(getAndSetShortMO(memoryOrder, o, offset, c2s(newValue)));
    }

    @ForceInline
    public float getAndSetFloat(Object o, long offset, float newValue) {
        int v = getAndSetInt(o, offset, Float.floatToRawIntBits(newValue));
        return Float.intBitsToFloat(v);
    }

    @ForceInline
    public float getAndSetFloatMO(byte memoryOrder, Object o, long offset, float newValue) {
        int v = getAndSetIntMO(memoryOrder, o, offset, Float.floatToRawIntBits(newValue));
        return Float.intBitsToFloat(v);
    }

    @ForceInline
    public double getAndSetDouble(Object o, long offset, double newValue) {
        long v = getAndSetLong(o, offset, Double.doubleToRawLongBits(newValue));
        return Double.longBitsToDouble(v);
   }

    @ForceInline
    public double getAndSetDoubleMO(byte memoryOrder, Object o, long offset, double newValue) {
        long v = getAndSetLongMO(memoryOrder, o, offset, Double.doubleToRawLongBits(newValue));
        return Double.longBitsToDouble(v);
    }

    // The following contain CAS-based Java implementations used on
    // platforms not supporting native instructions

    @ForceInline
    public boolean getAndBitwiseOrBoolean(Object o, long offset, boolean mask) {
        return byte2bool(getAndBitwiseOrByte(o, offset, bool2byte(mask)));
    }

    @ForceInline
    public boolean getAndBitwiseOrBooleanMO(byte memoryOrder, Object o, long offset, boolean mask) {
        return byte2bool(getAndBitwiseOrByteMO(memoryOrder, o, offset, bool2byte(mask)));
    }

    @ForceInline
    public boolean getAndBitwiseAndBoolean(Object o, long offset, boolean mask) {
        return byte2bool(getAndBitwiseAndByte(o, offset, bool2byte(mask)));
    }

    @ForceInline
    public boolean getAndBitwiseAndBooleanMO(byte memoryOrder, Object o, long offset, boolean mask) {
        return byte2bool(getAndBitwiseAndByteMO(memoryOrder, o, offset, bool2byte(mask)));
    }

    @ForceInline
    public boolean getAndBitwiseXorBoolean(Object o, long offset, boolean mask) {
        return byte2bool(getAndBitwiseXorByte(o, offset, bool2byte(mask)));
    }

    @ForceInline
    public boolean getAndBitwiseXorBooleanMO(byte memoryOrder, Object o, long offset, boolean mask) {
        return byte2bool(getAndBitwiseXorByteMO(memoryOrder, o, offset, bool2byte(mask)));
    }

    @ForceInline
    public byte getAndBitwiseOrByte(Object o, long offset, byte mask) {
        return getAndBitwiseOrByteMO(MO_VOLATILE, o, offset, mask);
    }

    @ForceInline
    public byte getAndBitwiseOrByteMO(byte memoryOrder, Object o, long offset, byte mask) {
        return (byte) getAndOperatePrimitiveBitsMO(memoryOrder, BT_BYTE, OP_BITOR, o, offset, mask);
    }

    @ForceInline
    public byte getAndBitwiseAndByte(Object o, long offset, byte mask) {
        return getAndBitwiseAndByteMO(MO_VOLATILE, o, offset, mask);
    }

    @ForceInline
    public byte getAndBitwiseAndByteMO(byte memoryOrder, Object o, long offset, byte mask) {
        return (byte) getAndOperatePrimitiveBitsMO(memoryOrder, BT_BYTE, OP_BITAND, o, offset, mask);
    }

    @ForceInline
    public byte getAndBitwiseXorByte(Object o, long offset, byte mask) {
        return getAndBitwiseXorByteMO(MO_VOLATILE, o, offset, mask);
    }

    @ForceInline
    public byte getAndBitwiseXorByteMO(byte memoryOrder, Object o, long offset, byte mask) {
        return (byte) getAndOperatePrimitiveBitsMO(memoryOrder, BT_BYTE, OP_BITXOR, o, offset, mask);
    }

    @ForceInline
    public char getAndBitwiseOrChar(Object o, long offset, char mask) {
        return s2c(getAndBitwiseOrShort(o, offset, c2s(mask)));
    }

    @ForceInline
    public char getAndBitwiseOrCharMO(byte memoryOrder, Object o, long offset, char mask) {
        return s2c(getAndBitwiseOrShortMO(memoryOrder, o, offset, c2s(mask)));
    }

    @ForceInline
    public char getAndBitwiseAndChar(Object o, long offset, char mask) {
        return s2c(getAndBitwiseAndShort(o, offset, c2s(mask)));
    }

    @ForceInline
    public char getAndBitwiseAndCharMO(byte memoryOrder, Object o, long offset, char mask) {
        return s2c(getAndBitwiseAndShortMO(memoryOrder, o, offset, c2s(mask)));
    }

    @ForceInline
    public char getAndBitwiseXorChar(Object o, long offset, char mask) {
        return s2c(getAndBitwiseXorShort(o, offset, c2s(mask)));
    }

    @ForceInline
    public char getAndBitwiseXorCharMO(byte memoryOrder, Object o, long offset, char mask) {
        return s2c(getAndBitwiseXorShortMO(memoryOrder, o, offset, c2s(mask)));
    }

    @ForceInline
    public short getAndBitwiseOrShort(Object o, long offset, short mask) {
        return getAndBitwiseOrShortMO(MO_VOLATILE, o, offset, mask);
    }

    @ForceInline
    public short getAndBitwiseOrShortMO(byte memoryOrder, Object o, long offset, short mask) {
        return (short) getAndOperatePrimitiveBitsMO(memoryOrder, BT_SHORT, OP_BITOR, o, offset, mask);
    }

    @ForceInline
    public short getAndBitwiseAndShort(Object o, long offset, short mask) {
        return getAndBitwiseAndShortMO(MO_VOLATILE, o, offset, mask);
    }

    @ForceInline
    public short getAndBitwiseAndShortMO(byte memoryOrder, Object o, long offset, short mask) {
        return (short) getAndOperatePrimitiveBitsMO(memoryOrder, BT_SHORT, OP_BITAND, o, offset, mask);
    }

    @ForceInline
    public short getAndBitwiseXorShort(Object o, long offset, short mask) {
        return getAndBitwiseXorShortMO(MO_VOLATILE, o, offset, mask);
    }

    @ForceInline
    public short getAndBitwiseXorShortMO(byte memoryOrder, Object o, long offset, short mask) {
        return (short) getAndOperatePrimitiveBitsMO(memoryOrder, BT_SHORT, OP_BITXOR, o, offset, mask);
    }

    @ForceInline
    public int getAndBitwiseOrInt(Object o, long offset, int mask) {
        return getAndBitwiseOrIntMO(MO_VOLATILE, o, offset, mask);
    }

    @ForceInline
    public int getAndBitwiseOrIntMO(byte memoryOrder, Object o, long offset, int mask) {
        return (int) getAndOperatePrimitiveBitsMO(memoryOrder, BT_INT, OP_BITOR, o, offset, mask);
    }

    /**
     * Atomically replaces the current value of a field or array element within
     * the given object with the result of bitwise AND between the current value
     * and mask.
     *
     * @param o object/array to update the field/element in
     * @param offset field/element offset
     * @param mask the mask value
     * @return the previous value
     * @since 9
     */
    @ForceInline
    public int getAndBitwiseAndInt(Object o, long offset, int mask) {
        return getAndBitwiseAndIntMO(MO_VOLATILE, o, offset, mask);
    }

    @ForceInline
    public int getAndBitwiseAndIntMO(byte memoryOrder, Object o, long offset, int mask) {
        return (int) getAndOperatePrimitiveBitsMO(memoryOrder, BT_INT, OP_BITAND, o, offset, mask);
    }

    @ForceInline
    public int getAndBitwiseXorInt(Object o, long offset, int mask) {
        return getAndBitwiseXorIntMO(MO_VOLATILE, o, offset, mask);
    }

    @ForceInline
    public int getAndBitwiseXorIntMO(byte memoryOrder, Object o, long offset, int mask) {
        return (int) getAndOperatePrimitiveBitsMO(memoryOrder, BT_INT, OP_BITXOR, o, offset, mask);
    }

    @ForceInline
    public long getAndBitwiseOrLong(Object o, long offset, long mask) {
        return getAndBitwiseOrLongMO(MO_VOLATILE, o, offset, mask);
    }

    @ForceInline
    public long getAndBitwiseOrLongMO(byte memoryOrder, Object o, long offset, long mask) {
        return getAndOperatePrimitiveBitsMO(memoryOrder, BT_LONG, OP_BITOR, o, offset, mask);
    }

    @ForceInline
    public long getAndBitwiseAndLong(Object o, long offset, long mask) {
        return getAndBitwiseAndLongMO(MO_VOLATILE, o, offset, mask);
    }

    @ForceInline
    public long getAndBitwiseAndLongMO(byte memoryOrder, Object o, long offset, long mask) {
        return getAndOperatePrimitiveBitsMO(memoryOrder, BT_LONG, OP_BITAND, o, offset, mask);
    }

    @ForceInline
    public long getAndBitwiseXorLong(Object o, long offset, long mask) {
        return getAndBitwiseXorLongMO(MO_VOLATILE, o, offset, mask);
    }

    @ForceInline
    public long getAndBitwiseXorLongMO(byte memoryOrder, Object o, long offset, long mask) {
        return getAndOperatePrimitiveBitsMO(memoryOrder, BT_LONG, OP_BITXOR, o, offset, mask);
    }

    /**
     * Ensures that loads before the fence will not be reordered with loads and
     * stores after the fence; a "LoadLoad plus LoadStore barrier".
     *
     * Corresponds to C11 atomic_thread_fence(memory_order_acquire)
     * (an "acquire fence").
     *
     * Provides a LoadLoad barrier followed by a LoadStore barrier.
     *
     * @since 1.8
     */
    @IntrinsicCandidate
    public void loadFence() {
        // If loadFence intrinsic is not available, fall back to full fence.
        fullFence();
    }

    /**
     * Ensures that loads and stores before the fence will not be reordered with
     * stores after the fence; a "StoreStore plus LoadStore barrier".
     *
     * Corresponds to C11 atomic_thread_fence(memory_order_release)
     * (a "release fence").
     *
     * Provides a StoreStore barrier followed by a LoadStore barrier.
     *
     * @since 1.8
     */
    @IntrinsicCandidate
    public void storeFence() {
        // If storeFence intrinsic is not available, fall back to full fence.
        fullFence();
    }

    /**
     * Ensures that loads and stores before the fence will not be reordered
     * with loads and stores after the fence.  Implies the effects of both
     * loadFence() and storeFence(), and in addition, the effect of a StoreLoad
     * barrier.
     *
     * Corresponds to C11 atomic_thread_fence(memory_order_seq_cst).
     * @since 1.8
     */
    @IntrinsicCandidate
    public native void fullFence();

    /**
     * Ensures that loads before the fence will not be reordered with
     * loads after the fence.
     *
     * @implNote
     * This method is operationally equivalent to {@link #loadFence()}.
     *
     * @since 9
     */
    public void loadLoadFence() {
        loadFence();
    }

    /**
     * Ensures that stores before the fence will not be reordered with
     * stores after the fence.
     *
     * @since 9
     */
    @IntrinsicCandidate
    public void storeStoreFence() {
        // If storeStoreFence intrinsic is not available, fall back to storeFence.
        storeFence();
    }

    /**
     * Throws IllegalAccessError; for use by the VM for access control
     * error support.
     * @since 1.8
     */
    private static void throwIllegalAccessError() {
        throw new IllegalAccessError();
    }

    /**
     * Throws NoSuchMethodError; for use by the VM for redefinition support.
     * @since 13
     */
    private static void throwNoSuchMethodError() {
        throw new NoSuchMethodError();
    }

    /**
     * @return Returns true if the native byte ordering of this
     * platform is big-endian, false if it is little-endian.
     */
    public boolean isBigEndian() { return BIG_ENDIAN; }

    /**
     * @return Returns true if this platform is capable of performing
     * accesses at addresses which are not aligned for the type of the
     * primitive type being accessed, false otherwise.
     */
    public boolean unalignedAccess() { return UNALIGNED_ACCESS; }

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
    @ForceInline
    public long getLongUnaligned(Object o, long offset) {
        if (UNALIGNED_ACCESS) {
            return getPrimitiveBitsMONative(MO_UNALIGNED_PLAIN, BT_LONG, o, offset);
        } else {
            return getLongUnalignedSlow(o, offset);
        }
    }
    private long getLongUnalignedSlow(Object o, long offset) {
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
    @ForceInline
    public long getLongUnaligned(Object o, long offset, boolean bigEndian) {
        return convEndian(bigEndian, getLongUnaligned(o, offset));
    }

    /** @see #getLongUnaligned(Object, long) */
    @ForceInline
    public int getIntUnaligned(Object o, long offset) {
        if (UNALIGNED_ACCESS) {
            return (int) getPrimitiveBitsMONative(MO_UNALIGNED_PLAIN, BT_INT, o, offset);
        } else {
            return getIntUnalignedSlow(o, offset);
        }
    }
    private int getIntUnalignedSlow(Object o, long offset) {
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
    @ForceInline
    public int getIntUnaligned(Object o, long offset, boolean bigEndian) {
        return convEndian(bigEndian, getIntUnaligned(o, offset));
    }

    /** @see #getLongUnaligned(Object, long) */
    @ForceInline
    public short getShortUnaligned(Object o, long offset) {
        if (UNALIGNED_ACCESS) {
            return (short) getPrimitiveBitsMONative(MO_UNALIGNED_PLAIN, BT_SHORT, o, offset);
        } else {
            return getShortUnalignedSlow(o, offset);
        }
    }
    private short getShortUnalignedSlow(Object o, long offset) {
        if ((offset & 1) == 0) {
            return getShort(o, offset);
        } else {
            return makeShort(getByte(o, offset),
                             getByte(o, offset + 1));
        }
    }
    /** @see #getLongUnaligned(Object, long, boolean) */
    @ForceInline
    public short getShortUnaligned(Object o, long offset, boolean bigEndian) {
        return convEndian(bigEndian, getShortUnaligned(o, offset));
    }

    /** @see #getLongUnaligned(Object, long) */
    @ForceInline
    public char getCharUnaligned(Object o, long offset) {
        return (char) getShortUnaligned(o, offset);
    }

    /** @see #getLongUnaligned(Object, long, boolean) */
    @ForceInline
    public char getCharUnaligned(Object o, long offset, boolean bigEndian) {
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
    @ForceInline
    public void putLongUnaligned(Object o, long offset, long x) {
        if (UNALIGNED_ACCESS) {
            putPrimitiveBitsMONative(MO_UNALIGNED_PLAIN, BT_LONG, o, offset, x);
        } else {
            putLongUnalignedSlow(o, offset, x);
        }
    }
    private void putLongUnalignedSlow(Object o, long offset, long x) {
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
    @ForceInline
    public void putLongUnaligned(Object o, long offset, long x, boolean bigEndian) {
        putLongUnaligned(o, offset, convEndian(bigEndian, x));
    }

    /** @see #putLongUnaligned(Object, long, long) */
    @ForceInline
    public void putIntUnaligned(Object o, long offset, int x) {
        if (UNALIGNED_ACCESS) {
            putPrimitiveBitsMONative(MO_UNALIGNED_PLAIN, BT_INT, o, offset, x);
        } else {
            putIntUnalignedSlow(o, offset, x);
        }
    }
    private void putIntUnalignedSlow(Object o, long offset, int x) {
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
    @ForceInline
    public void putIntUnaligned(Object o, long offset, int x, boolean bigEndian) {
        putIntUnaligned(o, offset, convEndian(bigEndian, x));
    }

    /** @see #putLongUnaligned(Object, long, long) */
    @ForceInline
    public void putShortUnaligned(Object o, long offset, short x) {
        if (UNALIGNED_ACCESS) {
            putPrimitiveBitsMONative(MO_UNALIGNED_PLAIN, BT_SHORT, o, offset, x);
        } else {
            putShortUnalignedSlow(o, offset, x);
        }
    }
    private void putShortUnalignedSlow(Object o, long offset, short x) {
        if ((offset & 1) == 0) {
            putShort(o, offset, x);
        } else {
            putShortParts(o, offset,
                          (byte)(x >>> 0),
                          (byte)(x >>> 8));
        }
    }
    /** @see #putLongUnaligned(Object, long, long, boolean) */
    @ForceInline
    public void putShortUnaligned(Object o, long offset, short x, boolean bigEndian) {
        putShortUnaligned(o, offset, convEndian(bigEndian, x));
    }

    /** @see #putLongUnaligned(Object, long, long) */
    @ForceInline
    public void putCharUnaligned(Object o, long offset, char x) {
        putShortUnaligned(o, offset, (short)x);
    }
    /** @see #putLongUnaligned(Object, long, long, boolean) */
    @ForceInline
    public void putCharUnaligned(Object o, long offset, char x, boolean bigEndian) {
        putCharUnaligned(o, offset, convEndian(bigEndian, x));
    }

    private static int pickPos(int top, int pos) { return BIG_ENDIAN ? top - pos : pos; }

    // These methods construct integers from bytes.  The byte ordering
    // is the native endianness of this platform.
    @ForceInline
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
    @ForceInline
    private static long makeLong(short i0, short i1, short i2, short i3) {
        return ((toUnsignedLong(i0) << pickPos(48, 0))
              | (toUnsignedLong(i1) << pickPos(48, 16))
              | (toUnsignedLong(i2) << pickPos(48, 32))
              | (toUnsignedLong(i3) << pickPos(48, 48)));
    }
    @ForceInline
    private static long makeLong(int i0, int i1) {
        return (toUnsignedLong(i0) << pickPos(32, 0))
             | (toUnsignedLong(i1) << pickPos(32, 32));
    }
    @ForceInline
    private static int makeInt(short i0, short i1) {
        return (toUnsignedInt(i0) << pickPos(16, 0))
             | (toUnsignedInt(i1) << pickPos(16, 16));
    }
    @ForceInline
    private static int makeInt(byte i0, byte i1, byte i2, byte i3) {
        return ((toUnsignedInt(i0) << pickPos(24, 0))
              | (toUnsignedInt(i1) << pickPos(24, 8))
              | (toUnsignedInt(i2) << pickPos(24, 16))
              | (toUnsignedInt(i3) << pickPos(24, 24)));
    }
    @ForceInline
    private static short makeShort(byte i0, byte i1) {
        return (short)((toUnsignedInt(i0) << pickPos(8, 0))
                     | (toUnsignedInt(i1) << pickPos(8, 8)));
    }

    private static byte  pick(byte  le, byte  be) { return BIG_ENDIAN ? be : le; }
    private static short pick(short le, short be) { return BIG_ENDIAN ? be : le; }
    private static int   pick(int   le, int   be) { return BIG_ENDIAN ? be : le; }

    // These methods write integers to memory from smaller parts
    // provided by their caller.  The ordering in which these parts
    // are written is the native endianness of this platform.
    @ForceInline
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
    @ForceInline
    private void putLongParts(Object o, long offset, short i0, short i1, short i2, short i3) {
        putShort(o, offset + 0, pick(i0, i3));
        putShort(o, offset + 2, pick(i1, i2));
        putShort(o, offset + 4, pick(i2, i1));
        putShort(o, offset + 6, pick(i3, i0));
    }
    @ForceInline
    private void putLongParts(Object o, long offset, int i0, int i1) {
        putInt(o, offset + 0, pick(i0, i1));
        putInt(o, offset + 4, pick(i1, i0));
    }
    @ForceInline
    private void putIntParts(Object o, long offset, short i0, short i1) {
        putShort(o, offset + 0, pick(i0, i1));
        putShort(o, offset + 2, pick(i1, i0));
    }
    @ForceInline
    private void putIntParts(Object o, long offset, byte i0, byte i1, byte i2, byte i3) {
        putByte(o, offset + 0, pick(i0, i3));
        putByte(o, offset + 1, pick(i1, i2));
        putByte(o, offset + 2, pick(i2, i1));
        putByte(o, offset + 3, pick(i3, i0));
    }
    @ForceInline
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
    private static char convEndian(boolean big, char n)   { return big == BIG_ENDIAN ? n : Character.reverseBytes(n); }
    private static short convEndian(boolean big, short n) { return big == BIG_ENDIAN ? n : Short.reverseBytes(n)    ; }
    private static int convEndian(boolean big, int n)     { return big == BIG_ENDIAN ? n : Integer.reverseBytes(n)  ; }
    private static long convEndian(boolean big, long n)   { return big == BIG_ENDIAN ? n : Long.reverseBytes(n)     ; }



    private native long allocateMemory0(long bytes);
    private native long reallocateMemory0(long address, long bytes);
    private native void freeMemory0(long address);
    @IntrinsicCandidate
    private native void setMemory0(Object o, long offset, long bytes, byte value);
    @IntrinsicCandidate
    private native void copyMemory0(Object srcBase, long srcOffset, Object destBase, long destOffset, long bytes);
    private native void copySwapMemory0(Object srcBase, long srcOffset, Object destBase, long destOffset, long bytes, long elemSize);
    private native long objectFieldOffset0(Field f); // throws IAE
    private native long knownObjectFieldOffset0(Class<?> c, String name); // error code: -1 not found, -2 static
    private native long staticFieldOffset0(Field f); // throws IAE
    private native Object staticFieldBase0(Field f); // throws IAE
    private native boolean shouldBeInitialized0(Class<?> c);
    private native void ensureClassInitialized0(Class<?> c);
    private native int arrayBaseOffset0(Class<?> arrayClass); // public version returns long to promote correct arithmetic
    private native int arrayIndexScale0(Class<?> arrayClass);
    private native int getLoadAverage0(double[] loadavg, int nelems);


    /**
     * Invokes the given direct byte buffer's cleaner, if any.
     *
     * @param directBuffer a direct byte buffer
     * @throws NullPointerException     if {@code directBuffer} is null
     * @throws IllegalArgumentException if {@code directBuffer} is non-direct,
     *                                  or is a {@link java.nio.Buffer#slice slice}, or is a
     *                                  {@link java.nio.Buffer#duplicate duplicate}
     */
    public void invokeCleaner(java.nio.ByteBuffer directBuffer) {
        if (!directBuffer.isDirect())
            throw new IllegalArgumentException("buffer is non-direct");

        DirectBuffer db = (DirectBuffer) directBuffer;
        if (db.attachment() != null)
            throw new IllegalArgumentException("duplicate or slice");

        Cleaner cleaner = db.cleaner();
        if (cleaner != null) {
            cleaner.clean();
        }
    }
}
