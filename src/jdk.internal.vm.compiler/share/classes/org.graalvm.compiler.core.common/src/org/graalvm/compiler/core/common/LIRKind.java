/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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


package org.graalvm.compiler.core.common;

import org.graalvm.compiler.core.common.alloc.RegisterAllocationConfig;

import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.PlatformKind;
import jdk.vm.ci.meta.Value;
import jdk.vm.ci.meta.ValueKind;

/**
 * Represents the type of values in the LIR. It is composed of a {@link PlatformKind} that gives the
 * low level representation of the value, a {@link #referenceMask} that describes the location of
 * object references in the value, a {@link #referenceCompressionMask} that indicates which of these
 * references are compressed references, and for derived references a {@link #derivedReferenceBase}.
 *
 * <h2>Constructing {@link LIRKind} instances</h2>
 *
 * During LIR generation, every new {@link Value} should get a {@link LIRKind} of the correct
 * {@link PlatformKind} that also contains the correct reference information. {@linkplain LIRKind
 * LIRKinds} should be created as follows:
 *
 * <p>
 * If the result value is created from one or more input values, the {@link LIRKind} should be
 * created with {@link LIRKind#combine}(inputs). If the result has a different {@link PlatformKind}
 * than the inputs, {@link LIRKind#combine}(inputs).{@link #changeType}(resultKind) should be used.
 * <p>
 * If the result is an exact copy of one of the inputs, {@link Value#getValueKind()} can be used.
 * Note that this is only correct for move-like operations, like conditional move or
 * compare-and-swap. For convert operations, {@link LIRKind#combine} should be used.
 * <p>
 * If it is known that the result will be a reference (e.g. pointer arithmetic where the end result
 * is a valid oop), {@link #reference} or {@link LIRKind#compressedReference} should be used.
 * <p>
 * If it is known that the result will neither be a reference nor be derived from a reference,
 * {@link LIRKind#value} can be used. If the operation producing this value has inputs, this is very
 * likely wrong, and {@link LIRKind#combine} should be used instead.
 * <p>
 * If it is known that the result is derived from a reference in a way that the garbage collector
 * can not track, {@link LIRKind#unknownReference} can be used. In most cases,
 * {@link LIRKind#combine} should be used instead, since it is able to detect this automatically.
 */
public final class LIRKind extends ValueKind<LIRKind> {

    /**
     * The location of object references in the value. If the value is a vector type, each bit
     * represents one component of the vector.
     */
    private final int referenceMask;

    /** Mask with 1-bits indicating which references in {@link #referenceMask} are compressed. */
    private final int referenceCompressionMask;

    private AllocatableValue derivedReferenceBase;

    private static final int UNKNOWN_REFERENCE = -1;

    public static final LIRKind Illegal = unknownReference(ValueKind.Illegal.getPlatformKind());

    private LIRKind(PlatformKind platformKind, int referenceMask, int referenceCompressionMask, AllocatableValue derivedReferenceBase) {
        super(platformKind);
        this.referenceMask = referenceMask;
        this.referenceCompressionMask = referenceCompressionMask;
        this.derivedReferenceBase = derivedReferenceBase;

        assert this.referenceCompressionMask == 0 || this.referenceMask == this.referenceCompressionMask : "mixing compressed and uncompressed references is untested";
        assert derivedReferenceBase == null || !derivedReferenceBase.getValueKind(LIRKind.class).isDerivedReference() : "derived reference can't have another derived reference as base";
    }

    /**
     * Create a {@link LIRKind} of type {@code platformKind} that contains a primitive value. Should
     * be only used when it's guaranteed that the value is not even indirectly derived from a
     * reference. Otherwise, {@link #combine(Value...)} should be used instead.
     */
    public static LIRKind value(PlatformKind platformKind) {
        return new LIRKind(platformKind, 0, 0, null);
    }

    /**
     * Create a {@link LIRKind} of type {@code platformKind} that contains a single, tracked,
     * uncompressed oop reference.
     */
    public static LIRKind reference(PlatformKind platformKind) {
        return derivedReference(platformKind, null, false);
    }

    /**
     * Create a {@link LIRKind} of type {@code platformKind} that contains a single, tracked,
     * compressed oop reference.
     */
    public static LIRKind compressedReference(PlatformKind platformKind) {
        return derivedReference(platformKind, null, true);
    }

    /**
     * Create the correct {@link LIRKind} for a given {@link Architecture} and {@link JavaKind}.
     */
    public static LIRKind fromJavaKind(Architecture arch, JavaKind javaKind) {
        PlatformKind platformKind = arch.getPlatformKind(javaKind);
        if (javaKind.isObject()) {
            return LIRKind.reference(platformKind);
        } else {
            return LIRKind.value(platformKind);
        }
    }

    /**
     * Create a {@link LIRKind} of type {@code platformKind} that contains a derived reference.
     */
    public static LIRKind derivedReference(PlatformKind platformKind, AllocatableValue base, boolean compressed) {
        int length = platformKind.getVectorLength();
        assert 0 < length && length < 32 : "vector of " + length + " references not supported";
        int referenceMask = (1 << length) - 1;
        int referenceCompressionMask = (compressed ? referenceMask : 0);
        return new LIRKind(platformKind, referenceMask, referenceCompressionMask, base);
    }

    /**
     * Create a {@link LIRKind} of type {@code platformKind} that contains a value that is derived
     * from a reference in a non-linear way. Values of this {@link LIRKind} can not be live at
     * safepoints. In most cases, this should not be called directly. {@link #combine} should be
     * used instead to automatically propagate this information.
     */
    public static LIRKind unknownReference(PlatformKind platformKind) {
        return new LIRKind(platformKind, UNKNOWN_REFERENCE, UNKNOWN_REFERENCE, null);
    }

    /**
     * Create a derived reference.
     *
     * @param base An {@link AllocatableValue} containing the base pointer of the derived reference.
     */
    public LIRKind makeDerivedReference(AllocatableValue base) {
        assert !isUnknownReference() && derivedReferenceBase == null;
        if (Value.ILLEGAL.equals(base)) {
            return makeUnknownReference();
        } else {
            if (isValue()) {
                return derivedReference(getPlatformKind(), base, false);
            } else {
                return new LIRKind(getPlatformKind(), referenceMask, referenceCompressionMask, base);
            }
        }
    }

    /**
     * Derive a new type from inputs. The result will have the {@link PlatformKind} of one of the
     * inputs. If all inputs are values, the result is a value. Otherwise, the result is an unknown
     * reference.
     *
     * This method should be used to construct the result {@link LIRKind} of any operation that
     * modifies values (e.g. arithmetics).
     */
    public static LIRKind combine(Value... inputs) {
        assert inputs.length > 0;
        for (Value input : inputs) {
            LIRKind kind = input.getValueKind(LIRKind.class);
            if (kind.isUnknownReference()) {
                return kind;
            } else if (!kind.isValue()) {
                return kind.makeUnknownReference();
            }
        }

        // all inputs are values, just return one of them
        return inputs[0].getValueKind(LIRKind.class);
    }

    /**
     * Helper method to construct derived reference kinds. Returns the base value of a reference or
     * derived reference. For values it returns {@code null}, and for unknown references it returns
     * {@link Value#ILLEGAL}.
     */
    public static AllocatableValue derivedBaseFromValue(AllocatableValue value) {
        ValueKind<?> valueKind = value.getValueKind();
        if (valueKind instanceof LIRKind) {
            LIRKind kind = value.getValueKind(LIRKind.class);
            if (kind.isValue()) {
                return null;
            } else if (kind.isDerivedReference()) {
                return kind.getDerivedReferenceBase();
            } else if (kind.isUnknownReference()) {
                return Value.ILLEGAL;
            } else {
                // kind is a reference
                return value;
            }
        } else {
            return Value.ILLEGAL;
        }
    }

    /**
     * Helper method to construct derived reference kinds. If one of {@code base1} or {@code base2}
     * are set, it creates a derived reference using it as the base. If both are set, the result is
     * an unknown reference.
     */
    public static LIRKind combineDerived(LIRKind kind, AllocatableValue base1, AllocatableValue base2) {
        if (base1 == null && base2 == null) {
            return kind;
        } else if (base1 == null) {
            return kind.makeDerivedReference(base2);
        } else if (base2 == null) {
            return kind.makeDerivedReference(base1);
        } else {
            return kind.makeUnknownReference();
        }
    }

    /**
     * Merges the reference information of the inputs. The result will have the {@link PlatformKind}
     * of {@code mergeKind}. If all inputs are values (references), the result is a value
     * (reference). Otherwise, the result is an unknown reference.
     *
     * The correctness of the {@link PlatformKind} is not verified.
     */
    public static LIRKind mergeReferenceInformation(LIRKind mergeKind, LIRKind inputKind) {
        assert mergeKind != null;
        assert inputKind != null;

        if (mergeKind.isUnknownReference()) {
            /**
             * {@code mergeKind} is an unknown reference, therefore the result can only be also an
             * unknown reference.
             */
            return mergeKind;
        }

        if (mergeKind.isValue()) {
            /* {@code mergeKind} is a value. */
            if (!inputKind.isValue()) {
                /*
                 * Inputs consists of values and references. Make the result an unknown reference.
                 */
                return mergeKind.makeUnknownReference();
            }
            return mergeKind;
        }
        /* {@code mergeKind} is a reference. */
        if (mergeKind.referenceMask != inputKind.referenceMask || mergeKind.referenceCompressionMask != inputKind.referenceCompressionMask) {
            /*
             * Reference masks do not match so the result can only be an unknown reference.
             */
            return mergeKind.makeUnknownReference();
        }

        /* Both are references. */
        if (mergeKind.isDerivedReference()) {
            if (inputKind.isDerivedReference() && mergeKind.getDerivedReferenceBase().equals(inputKind.getDerivedReferenceBase())) {
                /* Same reference base so they must be equal. */
                return mergeKind;
            }
            /* Base pointers differ. Make the result an unknown reference. */
            return mergeKind.makeUnknownReference();
        }
        if (inputKind.isDerivedReference()) {
            /*
             * {@code mergeKind} is not derived but {@code inputKind} is. Make the result an unknown
             * reference.
             */
            return mergeKind.makeUnknownReference();
        }
        /* Both are not derived references so they must be equal. */
        return mergeKind;
    }

    /**
     * Create a new {@link LIRKind} with the same reference information and a new
     * {@linkplain #getPlatformKind platform kind}. If the new kind is a longer vector than this,
     * the new elements are marked as untracked values.
     */
    @Override
    public LIRKind changeType(PlatformKind newPlatformKind) {
        if (newPlatformKind == getPlatformKind()) {
            return this;
        } else if (isUnknownReference()) {
            return unknownReference(newPlatformKind);
        } else if (referenceMask == 0) {
            // value type
            return LIRKind.value(newPlatformKind);
        } else {
            // reference type
            int newLength = Math.min(32, newPlatformKind.getVectorLength());
            int lengthMask = 0xFFFFFFFF >>> (32 - newLength);
            int newReferenceMask = referenceMask & lengthMask;
            int newReferenceCompressionMask = referenceCompressionMask & lengthMask;
            assert newReferenceMask != UNKNOWN_REFERENCE;
            return new LIRKind(newPlatformKind, newReferenceMask, newReferenceCompressionMask, derivedReferenceBase);
        }
    }

    /**
     * Create a new {@link LIRKind} with a new {@linkplain #getPlatformKind platform kind}. If the
     * new kind is longer than this, the reference positions are repeated to fill the vector.
     */
    public LIRKind repeat(PlatformKind newPlatformKind) {
        if (isUnknownReference()) {
            return unknownReference(newPlatformKind);
        } else if (referenceMask == 0) {
            // value type
            return LIRKind.value(newPlatformKind);
        } else {
            // reference type
            int oldLength = getPlatformKind().getVectorLength();
            int newLength = newPlatformKind.getVectorLength();
            assert oldLength <= newLength && newLength < 32 && (newLength % oldLength) == 0;

            // repeat reference mask to fill new kind
            int newReferenceMask = 0;
            int newReferenceCompressionMask = 0;
            for (int i = 0; i < newLength; i += getPlatformKind().getVectorLength()) {
                newReferenceMask |= referenceMask << i;
                newReferenceCompressionMask |= referenceCompressionMask << i;
            }

            assert newReferenceMask != UNKNOWN_REFERENCE;
            return new LIRKind(newPlatformKind, newReferenceMask, newReferenceCompressionMask, derivedReferenceBase);
        }
    }

    /**
     * Create a new {@link LIRKind} with the same type, but marked as containing an
     * {@link LIRKind#unknownReference}.
     */
    public LIRKind makeUnknownReference() {
        return new LIRKind(getPlatformKind(), UNKNOWN_REFERENCE, UNKNOWN_REFERENCE, null);
    }

    /**
     * Check whether this value is a derived reference.
     */
    public boolean isDerivedReference() {
        return getDerivedReferenceBase() != null;
    }

    /**
     * Get the base value of a derived reference.
     */
    public AllocatableValue getDerivedReferenceBase() {
        return derivedReferenceBase;
    }

    /**
     * Change the base value of a derived reference. This must be called on derived references only.
     */
    public void setDerivedReferenceBase(AllocatableValue derivedReferenceBase) {
        assert isDerivedReference();
        this.derivedReferenceBase = derivedReferenceBase;
    }

    /**
     * Check whether this value is derived from a reference in a non-linear way. If this returns
     * {@code true}, this value must not be live at safepoints.
     */
    public boolean isUnknownReference() {
        return referenceMask == UNKNOWN_REFERENCE;
    }

    public static boolean isUnknownReference(ValueKind<?> kind) {
        if (kind instanceof LIRKind) {
            return ((LIRKind) kind).isUnknownReference();
        } else {
            return true;
        }
    }

    public static boolean isUnknownReference(Value value) {
        return isUnknownReference(value.getValueKind());
    }

    public int getReferenceCount() {
        assert !isUnknownReference();
        return Integer.bitCount(referenceMask);
    }

    /**
     * Check whether the {@code idx}th part of this value is a reference that must be tracked at
     * safepoints.
     *
     * @param idx The index into the vector if this is a vector kind. Must be 0 if this is a scalar
     *            kind.
     */
    public boolean isReference(int idx) {
        assert 0 <= idx && idx < getPlatformKind().getVectorLength() : "invalid index " + idx + " in " + this;
        return !isUnknownReference() && (referenceMask & 1 << idx) != 0;
    }

    /**
     * Check whether the {@code idx}th part of this value is a <b>compressed</b> reference.
     *
     * @param idx The index into the vector if this is a vector kind. Must be 0 if this is a scalar
     *            kind.
     */
    public boolean isCompressedReference(int idx) {
        assert 0 <= idx && idx < getPlatformKind().getVectorLength() : "invalid index " + idx + " in " + this;
        return !isUnknownReference() && (referenceCompressionMask & (1 << idx)) != 0;
    }

    /**
     * Check whether this kind is a value type that doesn't need to be tracked at safepoints.
     */
    public boolean isValue() {
        return referenceMask == 0;
    }

    public static boolean isValue(ValueKind<?> kind) {
        if (kind instanceof LIRKind) {
            return ((LIRKind) kind).isValue();
        } else {
            return false;
        }
    }

    public static boolean isValue(Value value) {
        return isValue(value.getValueKind());
    }

    @Override
    public String toString() {
        if (isValue()) {
            return getPlatformKind().name();
        } else if (isUnknownReference()) {
            return getPlatformKind().name() + "[*]";
        } else {
            StringBuilder ret = new StringBuilder();
            ret.append(getPlatformKind().name());
            ret.append('[');
            for (int i = 0; i < getPlatformKind().getVectorLength(); i++) {
                if (isReference(i)) {
                    ret.append('.');
                } else {
                    ret.append(' ');
                }
            }
            ret.append(']');
            return ret.toString();
        }
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((getPlatformKind() == null) ? 0 : getPlatformKind().hashCode());
        result = prime * result + ((getDerivedReferenceBase() == null) ? 0 : getDerivedReferenceBase().hashCode());
        result = prime * result + referenceMask;
        result = prime * result + referenceCompressionMask;
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof LIRKind)) {
            return false;
        }

        LIRKind other = (LIRKind) obj;
        if (getPlatformKind() != other.getPlatformKind() || referenceMask != other.referenceMask || referenceCompressionMask != other.referenceCompressionMask) {
            return false;
        }
        if (isDerivedReference()) {
            if (!other.isDerivedReference()) {
                return false;
            }
            return getDerivedReferenceBase().equals(other.getDerivedReferenceBase());
        }
        // `this` is not a derived reference
        if (other.isDerivedReference()) {
            return false;
        }
        return true;
    }

    public static boolean verifyMoveKinds(ValueKind<?> dst, ValueKind<?> src, RegisterAllocationConfig config) {
        if (src.equals(dst)) {
            return true;
        }
        if (isUnknownReference(dst) || isValue(dst) && isValue(src)) {
            PlatformKind srcPlatformKind = src.getPlatformKind();
            PlatformKind dstPlatformKind = dst.getPlatformKind();
            if (srcPlatformKind.equals(dstPlatformKind)) {
                return true;
            }
            // if the register category matches it should be fine, although the kind is different
            return config.getRegisterCategory(srcPlatformKind).equals(config.getRegisterCategory(dstPlatformKind));
        }
        // reference information mismatch
        return false;
    }

}
