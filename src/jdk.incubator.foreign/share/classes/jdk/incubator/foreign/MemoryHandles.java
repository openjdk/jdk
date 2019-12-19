/*
 *  Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
 *  DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 *  This code is free software; you can redistribute it and/or modify it
 *  under the terms of the GNU General Public License version 2 only, as
 *  published by the Free Software Foundation.  Oracle designates this
 *  particular file as subject to the "Classpath" exception as provided
 *  by Oracle in the LICENSE file that accompanied this code.
 *
 *  This code is distributed in the hope that it will be useful, but WITHOUT
 *  ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 *  FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 *  version 2 for more details (a copy is included in the LICENSE file that
 *  accompanied this code).
 *
 *  You should have received a copy of the GNU General Public License version
 *  2 along with this work; if not, write to the Free Software Foundation,
 *  Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 *   Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 *  or visit www.oracle.com if you need additional information or have any
 *  questions.
 *
 */
package jdk.incubator.foreign;

import jdk.internal.access.JavaLangInvokeAccess;
import jdk.internal.access.SharedSecrets;
import jdk.internal.foreign.Utils;
import sun.invoke.util.Wrapper;

import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;

/**
 * This class defines several factory methods for constructing and combining memory access var handles.
 * To obtain a memory access var handle, clients must start from one of the <em>leaf</em> methods
 * (see {@link MemoryHandles#varHandle(Class, ByteOrder)},
 * {@link MemoryHandles#varHandle(Class, long, ByteOrder)}). This determines the variable type
 * (all primitive types but {@code void} and {@code boolean} are supported), as well as the alignment constraint and the
 * byte order associated to a memory access var handle. The resulting memory access var handle can then be combined in various ways
 * to emulate different addressing modes. The var handles created by this class feature a <em>mandatory</em> coordinate type
 * (of type {@link MemoryAddress}), and zero or more {@code long} coordinate types, which can be used to emulate
 * multi-dimensional array indexing.
 * <p>
 * As an example, consider the memory layout expressed by a {@link SequenceLayout} instance constructed as follows:
 * <blockquote><pre>{@code
SequenceLayout seq = MemoryLayout.ofSequence(5,
    MemoryLayout.ofStruct(
        MemoryLayout.ofPaddingBits(32),
        MemoryLayout.ofValueBits(32, ByteOrder.BIG_ENDIAN).withName("value")
    ));
 * }</pre></blockquote>
 * To access the member layout named {@code value}, we can construct a memory access var handle as follows:
 * <blockquote><pre>{@code
VarHandle handle = MemoryHandles.varHandle(int.class, ByteOrder.BIG_ENDIAN); //(MemoryAddress) -> int
handle = MemoryHandles.withOffset(handle, 4); //(MemoryAddress) -> int
handle = MemoryHandles.withStride(handle, 8); //(MemoryAddress, long) -> int
 * }</pre></blockquote>
 *
 * <h2>Addressing mode</h2>
 *
 * The final memory location accessed by a memory access var handle can be computed as follows:
 *
 * <blockquote><pre>{@code
address = base + offset
 * }</pre></blockquote>
 *
 * where {@code base} denotes the address expressed by the {@link MemoryAddress} access coordinate, and {@code offset}
 * can be expressed in the following form:
 *
 * <blockquote><pre>{@code
offset = c_1 + c_2 + ... + c_m + (x_1 * s_1) + (x_2 * s_2) + ... + (x_n * s_n)
 * }</pre></blockquote>
 *
 * where {@code x_1}, {@code x_2}, ... {@code x_n} are <em>dynamic</em> values provided as optional {@code long}
 * access coordinates, whereas {@code c_1}, {@code c_2}, ... {@code c_m} and {@code s_0}, {@code s_1}, ... {@code s_n} are
 * <em>static</em> constants which are can be acquired through the {@link MemoryHandles#withOffset(VarHandle, long)}
 * and the {@link MemoryHandles#withStride(VarHandle, long)} combinators, respectively.
 *
 * <h2><a id="memaccess-mode"></a>Alignment and access modes</h2>
 *
 * A memory access var handle is associated with an access size {@code S} and an alignment constraint {@code B}
 * (both expressed in bytes). We say that a memory access operation is <em>fully aligned</em> if it occurs
 * at a memory address {@code A} which is compatible with both alignment constraints {@code S} and {@code B}.
 * If access is fully aligned then following access modes are supported and are
 * guaranteed to support atomic access:
 * <ul>
 * <li>read write access modes for all {@code T}, with the exception of
 *     access modes {@code get} and {@code set} for {@code long} and
 *     {@code double} on 32-bit platforms.
 * <li>atomic update access modes for {@code int}, {@code long},
 *     {@code float} or {@code double}.
 *     (Future major platform releases of the JDK may support additional
 *     types for certain currently unsupported access modes.)
 * <li>numeric atomic update access modes for {@code int} and {@code long}.
 *     (Future major platform releases of the JDK may support additional
 *     numeric types for certain currently unsupported access modes.)
 * <li>bitwise atomic update access modes for {@code int} and {@code long}.
 *     (Future major platform releases of the JDK may support additional
 *     numeric types for certain currently unsupported access modes.)
 * </ul>
 *
 * If {@code T} is {@code float} or {@code double} then atomic
 * update access modes compare values using their bitwise representation
 * (see {@link Float#floatToRawIntBits} and
 * {@link Double#doubleToRawLongBits}, respectively).
 * <p>
 * Alternatively, a memory access operation is <em>partially aligned</em> if it occurs at a memory address {@code A}
 * which is only compatible with the alignment constraint {@code B}; in such cases, access for anything other than the
 * {@code get} and {@code set} access modes will result in an {@code IllegalStateException}. If access is partially aligned,
 * atomic access is only guaranteed with respect to the largest power of two that divides the GCD of {@code A} and {@code S}.
 * <p>
 * Finally, in all other cases, we say that a memory access operation is <em>misaligned</em>; in such cases an
 * {@code IllegalStateException} is thrown, irrespective of the access mode being used.
 */
public final class MemoryHandles {

    private final static JavaLangInvokeAccess JLI = SharedSecrets.getJavaLangInvokeAccess();

    private MemoryHandles() {
        //sorry, just the one!
    }

    /**
     * Creates a memory access var handle with the given carrier type and byte order.
     *
     * The resulting memory access var handle features a single {@link MemoryAddress} access coordinate,
     * and its variable type is set by the given carrier type.
     *
     * The alignment constraint for the resulting memory access var handle is the same as the in memory size of the
     * carrier type, and the accessed offset is set at zero.
     *
     * @apiNote the resulting var handle features certain <a href="#memaccess-mode">access mode restrictions</a>,
     * which are common to all memory access var handles.
     *
     * @param carrier the carrier type. Valid carriers are {@code byte}, {@code short}, {@code char}, {@code int},
     * {@code float}, {@code long}, and {@code double}.
     * @param byteOrder the required byte order.
     * @return the new memory access var handle.
     * @throws IllegalArgumentException when an illegal carrier type is used
     */
    public static VarHandle varHandle(Class<?> carrier, ByteOrder byteOrder) {
        checkCarrier(carrier);
        return varHandle(carrier,
                carrierSize(carrier),
                byteOrder);
    }

    /**
     * Creates a memory access var handle with the given carrier type, alignment constraint, and byte order.
     *
     * The resulting memory access var handle features a single {@link MemoryAddress} access coordinate,
     * and its variable type is set by the given carrier type.
     *
     * The accessed offset is zero.
     *
     * @apiNote the resulting var handle features certain <a href="#memaccess-mode">access mode restrictions</a>,
     * which are common to all memory access var handles.
     *
     * @param carrier the carrier type. Valid carriers are {@code byte}, {@code short}, {@code char}, {@code int},
     * {@code float}, {@code long}, and {@code double}.
     * @param alignmentBytes the alignment constraint (in bytes). Must be a power of two.
     * @param byteOrder the required byte order.
     * @return the new memory access var handle.
     * @throws IllegalArgumentException if an illegal carrier type is used, or if {@code alignmentBytes} is not a power of two.
     */
    public static VarHandle varHandle(Class<?> carrier, long alignmentBytes, ByteOrder byteOrder) {
        checkCarrier(carrier);

        if (alignmentBytes <= 0
                || (alignmentBytes & (alignmentBytes - 1)) != 0) { // is power of 2?
            throw new IllegalArgumentException("Bad alignment: " + alignmentBytes);
        }

        return JLI.memoryAddressViewVarHandle(carrier, alignmentBytes - 1, byteOrder, 0, new long[]{});
    }

    /**
     * Creates a memory access var handle with a fixed offset added to the accessed offset. That is,
     * if the target memory access var handle accesses a memory location at offset <em>O</em>, the new memory access var
     * handle will access a memory location at offset <em>O' + O</em>.
     *
     * The resulting memory access var handle will feature the same access coordinates as the ones in the target memory access var handle.
     *
     * @apiNote the resulting var handle features certain <a href="#memaccess-mode">access mode restrictions</a>,
     * which are common to all memory access var handles.
     *
     * @param target the target memory access handle to access after the offset adjustment.
     * @param bytesOffset the offset, in bytes. Must be positive or zero.
     * @return the new memory access var handle.
     * @throws IllegalArgumentException when the target var handle is not a memory access var handle,
     * or when {@code bytesOffset < 0}, or otherwise incompatible with the alignment constraint.
     */
    public static VarHandle withOffset(VarHandle target, long bytesOffset) {
        if (bytesOffset < 0) {
            throw new IllegalArgumentException("Illegal offset: " + bytesOffset);
        }

        long alignMask = JLI.memoryAddressAlignmentMask(target);

        if ((bytesOffset & alignMask) != 0) {
            throw new IllegalArgumentException("Offset " + bytesOffset + " does not conform to alignment " + (alignMask + 1));
        }

        return JLI.memoryAddressViewVarHandle(
                JLI.memoryAddressCarrier(target),
                alignMask,
                JLI.memoryAddressByteOrder(target),
                JLI.memoryAddressOffset(target) + bytesOffset,
                JLI.memoryAddressStrides(target));
    }

    /**
     * Creates a memory access var handle with a <em>variable</em> offset added to the accessed offset.
     * That is, if the target memory access var handle accesses a memory location at offset <em>O</em>,
     * the new memory access var handle will access a memory location at offset <em>(S * X) + O</em>, where <em>S</em>
     * is a constant <em>stride</em>, whereas <em>X</em> is a dynamic value that will be provided as an additional access
     * coordinate (of type {@code long}). The new access coordinate will be <em>prepended</em> to the ones available
     * in the target memory access var handles (if any).
     *
     * @apiNote the resulting var handle features certain <a href="#memaccess-mode">access mode restrictions</a>,
     * which are common to all memory access var handles.
     *
     * @param target the target memory access handle to access after the scale adjustment.
     * @param bytesStride the stride, in bytes, by which to multiply the coordinate value. Must be greater than zero.
     * @return the new memory access var handle.
     * @throws IllegalArgumentException when the target var handle is not a memory access var handle,
     * or if {@code bytesStride <= 0}, or otherwise incompatible with the alignment constraint.
     */
    public static VarHandle withStride(VarHandle target, long bytesStride) {
        if (bytesStride == 0) {
            throw new IllegalArgumentException("Stride must be positive: " + bytesStride);
        }

        long alignMask = JLI.memoryAddressAlignmentMask(target);

        if ((bytesStride & alignMask) != 0) {
            throw new IllegalArgumentException("Stride " + bytesStride + " does not conform to alignment " + (alignMask + 1));
        }

        long offset = JLI.memoryAddressOffset(target);

        long[] strides = JLI.memoryAddressStrides(target);
        long[] newStrides = new long[strides.length + 1];
        System.arraycopy(strides, 0, newStrides, 1, strides.length);
        newStrides[0] = bytesStride;

        return JLI.memoryAddressViewVarHandle(
                JLI.memoryAddressCarrier(target),
                alignMask,
                JLI.memoryAddressByteOrder(target),
                offset,
                newStrides);
    }

    private static void checkCarrier(Class<?> carrier) {
        if (!carrier.isPrimitive() || carrier == void.class || carrier == boolean.class) {
            throw new IllegalArgumentException("Illegal carrier: " + carrier.getSimpleName());
        }
    }

    private static long carrierSize(Class<?> carrier) {
        long bitsAlignment = Math.max(8, Wrapper.forPrimitiveType(carrier).bitWidth());
        return Utils.bitsToBytesOrThrow(bitsAlignment, IllegalStateException::new);
    }
}
