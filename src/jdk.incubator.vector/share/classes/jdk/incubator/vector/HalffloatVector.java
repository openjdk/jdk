/*
 * Copyright (c) 2017, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.incubator.vector;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Objects;
import java.util.function.Function;

import jdk.internal.foreign.AbstractMemorySegmentImpl;
import jdk.internal.misc.ScopedMemoryAccess;
import jdk.internal.misc.Unsafe;
import jdk.internal.vm.annotation.ForceInline;
import jdk.internal.vm.vector.VectorSupport;

import static jdk.internal.vm.vector.VectorSupport.*;
import static jdk.incubator.vector.VectorIntrinsics.*;

import static jdk.incubator.vector.VectorOperators.*;
import jdk.incubator.vector.Float16;

// -- This file was mechanically generated: Do not edit! -- //

/**
 * A specialized {@link Vector} representing an ordered immutable sequence of
 * {@code short} values.
 */
@SuppressWarnings("cast")  // warning: redundant cast
public abstract class HalffloatVector extends AbstractVector<Float16> {

    HalffloatVector(short[] vec) {
        super(vec);
    }

    static final int FORBID_OPCODE_KIND = VO_NOFP;

    static final int VECTOR_OPER_TYPE = VECTOR_TYPE_FP16;

    static final ValueLayout.OfShort ELEMENT_LAYOUT = ValueLayout.JAVA_SHORT.withByteAlignment(1);

    @ForceInline
    static int opCode(Operator op) {
        return VectorOperators.opCode(op, VO_OPCODE_VALID, FORBID_OPCODE_KIND);
    }
    @ForceInline
    static int opCode(Operator op, int requireKind) {
        requireKind |= VO_OPCODE_VALID;
        return VectorOperators.opCode(op, requireKind, FORBID_OPCODE_KIND);
    }
    @ForceInline
    static boolean opKind(Operator op, int bit) {
        return VectorOperators.opKind(op, bit);
    }

    // Virtualized factories and operators,
    // coded with portable definitions.
    // These are all @ForceInline in case
    // they need to be used performantly.
    // The various shape-specific subclasses
    // also specialize them by wrapping
    // them in a call like this:
    //    return (Byte128Vector)
    //       super.bOp((Byte128Vector) o);
    // The purpose of that is to forcibly inline
    // the generic definition from this file
    // into a sharply-typed and size-specific
    // wrapper in the subclass file, so that
    // the JIT can specialize the code.
    // The code is only inlined and expanded
    // if it gets hot.  Think of it as a cheap
    // and lazy version of C++ templates.

    // Virtualized getter

    /*package-private*/
    abstract short[] vec();

    // Virtualized constructors

    /**
     * Build a vector directly using my own constructor.
     * It is an error if the array is aliased elsewhere.
     */
    /*package-private*/
    abstract HalffloatVector vectorFactory(short[] vec);

    /**
     * Build a mask directly using my species.
     * It is an error if the array is aliased elsewhere.
     */
    /*package-private*/
    @ForceInline
    final
    AbstractMask<Float16> maskFactory(boolean[] bits) {
        return vspecies().maskFactory(bits);
    }

    // Constant loader (takes dummy as vector arg)
    interface FVOp {
        short apply(int i);
    }

    /*package-private*/
    @ForceInline
    final
    HalffloatVector vOp(FVOp f) {
        short[] res = new short[length()];
        for (int i = 0; i < res.length; i++) {
            res[i] = f.apply(i);
        }
        return vectorFactory(res);
    }

    @ForceInline
    final
    HalffloatVector vOp(VectorMask<Float16> m, FVOp f) {
        short[] res = new short[length()];
        boolean[] mbits = ((AbstractMask<Float16>)m).getBits();
        for (int i = 0; i < res.length; i++) {
            if (mbits[i]) {
                res[i] = f.apply(i);
            }
        }
        return vectorFactory(res);
    }

    // Unary operator

    /*package-private*/
    interface FUnOp {
        short apply(int i, short a);
    }

    /*package-private*/
    abstract
    HalffloatVector uOp(FUnOp f);
    @ForceInline
    final
    HalffloatVector uOpTemplate(FUnOp f) {
        short[] vec = vec();
        short[] res = new short[length()];
        for (int i = 0; i < res.length; i++) {
            res[i] = f.apply(i, vec[i]);
        }
        return vectorFactory(res);
    }

    /*package-private*/
    abstract
    HalffloatVector uOp(VectorMask<Float16> m,
                             FUnOp f);
    @ForceInline
    final
    HalffloatVector uOpTemplate(VectorMask<Float16> m,
                                     FUnOp f) {
        if (m == null) {
            return uOpTemplate(f);
        }
        short[] vec = vec();
        short[] res = new short[length()];
        boolean[] mbits = ((AbstractMask<Float16>)m).getBits();
        for (int i = 0; i < res.length; i++) {
            res[i] = mbits[i] ? f.apply(i, vec[i]) : vec[i];
        }
        return vectorFactory(res);
    }

    // Binary operator

    /*package-private*/
    interface FBinOp {
        short apply(int i, short a, short b);
    }

    /*package-private*/
    abstract
    HalffloatVector bOp(Vector<Float16> o,
                             FBinOp f);
    @ForceInline
    final
    HalffloatVector bOpTemplate(Vector<Float16> o,
                                     FBinOp f) {
        short[] res = new short[length()];
        short[] vec1 = this.vec();
        short[] vec2 = ((HalffloatVector)o).vec();
        for (int i = 0; i < res.length; i++) {
            res[i] = f.apply(i, vec1[i], vec2[i]);
        }
        return vectorFactory(res);
    }

    /*package-private*/
    abstract
    HalffloatVector bOp(Vector<Float16> o,
                             VectorMask<Float16> m,
                             FBinOp f);
    @ForceInline
    final
    HalffloatVector bOpTemplate(Vector<Float16> o,
                                     VectorMask<Float16> m,
                                     FBinOp f) {
        if (m == null) {
            return bOpTemplate(o, f);
        }
        short[] res = new short[length()];
        short[] vec1 = this.vec();
        short[] vec2 = ((HalffloatVector)o).vec();
        boolean[] mbits = ((AbstractMask<Float16>)m).getBits();
        for (int i = 0; i < res.length; i++) {
            res[i] = mbits[i] ? f.apply(i, vec1[i], vec2[i]) : vec1[i];
        }
        return vectorFactory(res);
    }

    // Ternary operator

    /*package-private*/
    interface FTriOp {
        short apply(int i, short a, short b, short c);
    }

    /*package-private*/
    abstract
    HalffloatVector tOp(Vector<Float16> o1,
                             Vector<Float16> o2,
                             FTriOp f);
    @ForceInline
    final
    HalffloatVector tOpTemplate(Vector<Float16> o1,
                                     Vector<Float16> o2,
                                     FTriOp f) {
        short[] res = new short[length()];
        short[] vec1 = this.vec();
        short[] vec2 = ((HalffloatVector)o1).vec();
        short[] vec3 = ((HalffloatVector)o2).vec();
        for (int i = 0; i < res.length; i++) {
            res[i] = f.apply(i, vec1[i], vec2[i], vec3[i]);
        }
        return vectorFactory(res);
    }

    /*package-private*/
    abstract
    HalffloatVector tOp(Vector<Float16> o1,
                             Vector<Float16> o2,
                             VectorMask<Float16> m,
                             FTriOp f);
    @ForceInline
    final
    HalffloatVector tOpTemplate(Vector<Float16> o1,
                                     Vector<Float16> o2,
                                     VectorMask<Float16> m,
                                     FTriOp f) {
        if (m == null) {
            return tOpTemplate(o1, o2, f);
        }
        short[] res = new short[length()];
        short[] vec1 = this.vec();
        short[] vec2 = ((HalffloatVector)o1).vec();
        short[] vec3 = ((HalffloatVector)o2).vec();
        boolean[] mbits = ((AbstractMask<Float16>)m).getBits();
        for (int i = 0; i < res.length; i++) {
            res[i] = mbits[i] ? f.apply(i, vec1[i], vec2[i], vec3[i]) : vec1[i];
        }
        return vectorFactory(res);
    }

    // Reduction operator

    /*package-private*/
    abstract
    short rOp(short v, VectorMask<Float16> m, FBinOp f);

    @ForceInline
    final
    short rOpTemplate(short v, VectorMask<Float16> m, FBinOp f) {
        if (m == null) {
            return rOpTemplate(v, f);
        }
        short[] vec = vec();
        boolean[] mbits = ((AbstractMask<Float16>)m).getBits();
        for (int i = 0; i < vec.length; i++) {
            v = mbits[i] ? f.apply(i, v, vec[i]) : v;
        }
        return v;
    }

    @ForceInline
    final
    short rOpTemplate(short v, FBinOp f) {
        short[] vec = vec();
        for (int i = 0; i < vec.length; i++) {
            v = f.apply(i, v, vec[i]);
        }
        return v;
    }

    // Memory reference

    /*package-private*/
    interface FLdOp<M> {
        short apply(M memory, int offset, int i);
    }

    /*package-private*/
    @ForceInline
    final
    <M> HalffloatVector ldOp(M memory, int offset,
                                  FLdOp<M> f) {
        //dummy; no vec = vec();
        short[] res = new short[length()];
        for (int i = 0; i < res.length; i++) {
            res[i] = f.apply(memory, offset, i);
        }
        return vectorFactory(res);
    }

    /*package-private*/
    @ForceInline
    final
    <M> HalffloatVector ldOp(M memory, int offset,
                                  VectorMask<Float16> m,
                                  FLdOp<M> f) {
        //short[] vec = vec();
        short[] res = new short[length()];
        boolean[] mbits = ((AbstractMask<Float16>)m).getBits();
        for (int i = 0; i < res.length; i++) {
            if (mbits[i]) {
                res[i] = f.apply(memory, offset, i);
            }
        }
        return vectorFactory(res);
    }

    /*package-private*/
    interface FLdLongOp {
        short apply(MemorySegment memory, long offset, int i);
    }

    /*package-private*/
    @ForceInline
    final
    HalffloatVector ldLongOp(MemorySegment memory, long offset,
                                  FLdLongOp f) {
        //dummy; no vec = vec();
        short[] res = new short[length()];
        for (int i = 0; i < res.length; i++) {
            res[i] = f.apply(memory, offset, i);
        }
        return vectorFactory(res);
    }

    /*package-private*/
    @ForceInline
    final
    HalffloatVector ldLongOp(MemorySegment memory, long offset,
                                  VectorMask<Float16> m,
                                  FLdLongOp f) {
        //short[] vec = vec();
        short[] res = new short[length()];
        boolean[] mbits = ((AbstractMask<Float16>)m).getBits();
        for (int i = 0; i < res.length; i++) {
            if (mbits[i]) {
                res[i] = f.apply(memory, offset, i);
            }
        }
        return vectorFactory(res);
    }

    static short memorySegmentGet(MemorySegment ms, long o, int i) {
        return ms.get(ELEMENT_LAYOUT, o + i * 2L);
    }

    interface FStOp<M> {
        void apply(M memory, int offset, int i, short a);
    }

    /*package-private*/
    @ForceInline
    final
    <M> void stOp(M memory, int offset,
                  FStOp<M> f) {
        short[] vec = vec();
        for (int i = 0; i < vec.length; i++) {
            f.apply(memory, offset, i, vec[i]);
        }
    }

    /*package-private*/
    @ForceInline
    final
    <M> void stOp(M memory, int offset,
                  VectorMask<Float16> m,
                  FStOp<M> f) {
        short[] vec = vec();
        boolean[] mbits = ((AbstractMask<Float16>)m).getBits();
        for (int i = 0; i < vec.length; i++) {
            if (mbits[i]) {
                f.apply(memory, offset, i, vec[i]);
            }
        }
    }

    interface FStLongOp {
        void apply(MemorySegment memory, long offset, int i, short a);
    }

    /*package-private*/
    @ForceInline
    final
    void stLongOp(MemorySegment memory, long offset,
                  FStLongOp f) {
        short[] vec = vec();
        for (int i = 0; i < vec.length; i++) {
            f.apply(memory, offset, i, vec[i]);
        }
    }

    /*package-private*/
    @ForceInline
    final
    void stLongOp(MemorySegment memory, long offset,
                  VectorMask<Float16> m,
                  FStLongOp f) {
        short[] vec = vec();
        boolean[] mbits = ((AbstractMask<Float16>)m).getBits();
        for (int i = 0; i < vec.length; i++) {
            if (mbits[i]) {
                f.apply(memory, offset, i, vec[i]);
            }
        }
    }

    static void memorySegmentSet(MemorySegment ms, long o, int i, short e) {
        ms.set(ELEMENT_LAYOUT, o + i * 2L, e);
    }

    // Binary test

    /*package-private*/
    interface FBinTest {
        boolean apply(int cond, int i, short a, short b);
    }

    /*package-private*/
    @ForceInline
    final
    AbstractMask<Float16> bTest(int cond,
                                  Vector<Float16> o,
                                  FBinTest f) {
        short[] vec1 = vec();
        short[] vec2 = ((HalffloatVector)o).vec();
        boolean[] bits = new boolean[length()];
        for (int i = 0; i < length(); i++){
            bits[i] = f.apply(cond, i, vec1[i], vec2[i]);
        }
        return maskFactory(bits);
    }


    /*package-private*/
    @Override
    abstract HalffloatSpecies vspecies();

    /*package-private*/
    @ForceInline
    static long toBits(short e) {
        return e;
    }

    /*package-private*/
    @ForceInline
    static short fromBits(long bits) {
        return Float16.float16ToRawShortBits(Float16.shortBitsToFloat16((short)bits));
    }

    static HalffloatVector expandHelper(Vector<Float16> v, VectorMask<Float16> m) {
        VectorSpecies<Float16> vsp = m.vectorSpecies();
        HalffloatVector r  = (HalffloatVector) vsp.zero();
        HalffloatVector vi = (HalffloatVector) v;
        if (m.allTrue()) {
            return vi;
        }
        for (int i = 0, j = 0; i < vsp.length(); i++) {
            if (m.laneIsSet(i)) {
                r = r.withLane(i, vi.lane(j++));
            }
        }
        return r;
    }

    static HalffloatVector compressHelper(Vector<Float16> v, VectorMask<Float16> m) {
        VectorSpecies<Float16> vsp = m.vectorSpecies();
        HalffloatVector r  = (HalffloatVector) vsp.zero();
        HalffloatVector vi = (HalffloatVector) v;
        if (m.allTrue()) {
            return vi;
        }
        for (int i = 0, j = 0; i < vsp.length(); i++) {
            if (m.laneIsSet(i)) {
                r = r.withLane(j++, vi.lane(i));
            }
        }
        return r;
    }

    static HalffloatVector selectFromTwoVectorHelper(Vector<Float16> indexes, Vector<Float16> src1, Vector<Float16> src2) {
        int vlen = indexes.length();
        short[] res = new short[vlen];
        short[] vecPayload1 = ((HalffloatVector)indexes).vec();
        short[] vecPayload2 = ((HalffloatVector)src1).vec();
        short[] vecPayload3 = ((HalffloatVector)src2).vec();
        for (int i = 0; i < vlen; i++) {
            int index = Float16.shortBitsToFloat16(vecPayload1[i]).intValue();
            int wrapped_index = VectorIntrinsics.wrapToRange(index, 2 * vlen);
            res[i] = wrapped_index >= vlen ? vecPayload3[wrapped_index - vlen] : vecPayload2[wrapped_index];
        }
        return ((HalffloatVector)src1).vectorFactory(res);
    }

    // Static factories (other than memory operations)

    // Note: A surprising behavior in javadoc
    // sometimes makes a lone /** {@inheritDoc} */
    // comment drop the method altogether,
    // apparently if the method mentions a
    // parameter or return type of Vector<Float16>
    // instead of Vector<E> as originally specified.
    // Adding an empty HTML fragment appears to
    // nudge javadoc into providing the desired
    // inherited documentation.  We use the HTML
    // comment <!--workaround--> for this.

    /**
     * Returns a vector of the given species
     * where all lane elements are set to
     * zero, the default primitive value.
     *
     * @param species species of the desired zero vector
     * @return a zero vector
     */
    @ForceInline
    public static HalffloatVector zero(VectorSpecies<Float16> species) {
        HalffloatSpecies vsp = (HalffloatSpecies) species;
        return VectorSupport.fromBitsCoerced(vsp.vectorType(), short.class, VECTOR_OPER_TYPE, species.length(),
                        toBits((short) 0), MODE_BROADCAST, vsp,
                        ((bits_, s_) -> s_.rvOp(i -> bits_)));
    }

    /**
     * Returns a vector of the same species as this one
     * where all lane elements are set to
     * the primitive value {@code e}.
     *
     * The contents of the current vector are discarded;
     * only the species is relevant to this operation.
     *
     * <p> This method returns the value of this expression:
     * {@code HalffloatVector.broadcast(this.species(), e)}.
     *
     * @apiNote
     * Unlike the similar method named {@code broadcast()}
     * in the supertype {@code Vector}, this method does not
     * need to validate its argument, and cannot throw
     * {@code IllegalArgumentException}.  This method is
     * therefore preferable to the supertype method.
     *
     * @param e the value to broadcast
     * @return a vector where all lane elements are set to
     *         the primitive value {@code e}
     * @see #broadcast(VectorSpecies,long)
     * @see Vector#broadcast(long)
     * @see VectorSpecies#broadcast(long)
     */
    public abstract HalffloatVector broadcast(short e);

    /**
     * Returns a vector of the given species
     * where all lane elements are set to
     * the primitive value {@code e}.
     *
     * @param species species of the desired vector
     * @param e the value to broadcast
     * @return a vector where all lane elements are set to
     *         the primitive value {@code e}
     * @see #broadcast(long)
     * @see Vector#broadcast(long)
     * @see VectorSpecies#broadcast(long)
     */
    @ForceInline
    public static HalffloatVector broadcast(VectorSpecies<Float16> species, short e) {
        HalffloatSpecies vsp = (HalffloatSpecies) species;
        return vsp.broadcast(e);
    }

    /*package-private*/
    @ForceInline
    final HalffloatVector broadcastTemplate(short e) {
        HalffloatSpecies vsp = vspecies();
        return vsp.broadcast(e);
    }

    /**
     * {@inheritDoc} <!--workaround-->
     * @apiNote
     * When working with vector subtypes like {@code HalffloatVector},
     * {@linkplain #broadcast(short) the more strongly typed method}
     * is typically selected.  It can be explicitly selected
     * using a cast: {@code v.broadcast((short)e)}.
     * The two expressions will produce numerically identical results.
     */
    @Override
    public abstract HalffloatVector broadcast(long e);

    /**
     * Returns a vector of the given species
     * where all lane elements are set to
     * the primitive value {@code e}.
     *
     * The {@code long} value must be accurately representable
     * by the {@code ETYPE} of the vector species, so that
     * {@code e==(long)(ETYPE)e}.
     *
     * @param species species of the desired vector
     * @param e the value to broadcast
     * @return a vector where all lane elements are set to
     *         the primitive value {@code e}
     * @throws IllegalArgumentException
     *         if the given {@code long} value cannot
     *         be represented by the vector's {@code ETYPE}
     * @see #broadcast(VectorSpecies,short)
     * @see VectorSpecies#checkValue(long)
     */
    @ForceInline
    public static HalffloatVector broadcast(VectorSpecies<Float16> species, long e) {
        HalffloatSpecies vsp = (HalffloatSpecies) species;
        return vsp.broadcast(e);
    }

    /*package-private*/
    @ForceInline
    final HalffloatVector broadcastTemplate(long e) {
        return vspecies().broadcast(e);
    }

    // Unary lanewise support

    /**
     * {@inheritDoc} <!--workaround-->
     */
    public abstract
    HalffloatVector lanewise(VectorOperators.Unary op);

    @ForceInline
    final
    HalffloatVector lanewiseTemplate(VectorOperators.Unary op) {
        if (opKind(op, VO_SPECIAL)) {
            if (op == ZOMO) {
                return blend(broadcast(-1), compare(NE, 0));
            }
            else if (opKind(op, VO_MATHLIB)) {
                return unaryMathOp(op);
            }
        }
        int opc = opCode(op);
        return VectorSupport.unaryOp(
            opc, getClass(), null, short.class, VECTOR_OPER_TYPE, length(),
            this, null,
            UN_IMPL.find(op, opc, HalffloatVector::unaryOperations));
    }

    /**
     * {@inheritDoc} <!--workaround-->
     */
    @Override
    public abstract
    HalffloatVector lanewise(VectorOperators.Unary op,
                                  VectorMask<Float16> m);
    @ForceInline
    final
    HalffloatVector lanewiseTemplate(VectorOperators.Unary op,
                                          Class<? extends VectorMask<Float16>> maskClass,
                                          VectorMask<Float16> m) {
        m.check(maskClass, this);
        if (opKind(op, VO_SPECIAL)) {
            if (op == ZOMO) {
                return blend(broadcast(-1), compare(NE, 0, m));
            }
            else if (opKind(op, VO_MATHLIB)) {
                return blend(unaryMathOp(op), m);
            }
        }
        int opc = opCode(op);
        return VectorSupport.unaryOp(
            opc, getClass(), maskClass, short.class, VECTOR_OPER_TYPE, length(),
            this, m,
            UN_IMPL.find(op, opc, HalffloatVector::unaryOperations));
    }

    @ForceInline
    final
    HalffloatVector unaryMathOp(VectorOperators.Unary op) {
        return VectorMathLibrary.unaryMathOp(op, opCode(op), species(), HalffloatVector::unaryOperations,
                                             this);
    }

    private static final
    ImplCache<Unary, UnaryOperation<HalffloatVector, VectorMask<Float16>>>
        UN_IMPL = new ImplCache<>(Unary.class, HalffloatVector.class);

    private static UnaryOperation<HalffloatVector, VectorMask<Float16>> unaryOperations(int opc_) {
        switch (opc_) {
            case VECTOR_OP_NEG: return (v0, m) ->
                    v0.uOp(m, (i, a) -> (short) -a);
            case VECTOR_OP_ABS: return (v0, m) ->
                    v0.uOp(m, (i, a) -> (short) Math.abs(a));
            case VECTOR_OP_SIN: return (v0, m) ->
                    v0.uOp(m, (i, a) -> Float16.float16ToRawShortBits(Float16.valueOf(Math.sin(Float16.shortBitsToFloat16(a).doubleValue()))));
            case VECTOR_OP_COS: return (v0, m) ->
                    v0.uOp(m, (i, a) -> Float16.float16ToRawShortBits(Float16.valueOf(Math.cos(Float16.shortBitsToFloat16(a).doubleValue()))));
            case VECTOR_OP_TAN: return (v0, m) ->
                    v0.uOp(m, (i, a) -> Float16.float16ToRawShortBits(Float16.valueOf(Math.tan(Float16.shortBitsToFloat16(a).doubleValue()))));
            case VECTOR_OP_ASIN: return (v0, m) ->
                    v0.uOp(m, (i, a) -> Float16.float16ToRawShortBits(Float16.valueOf(Math.asin(Float16.shortBitsToFloat16(a).doubleValue()))));
            case VECTOR_OP_ACOS: return (v0, m) ->
                    v0.uOp(m, (i, a) -> Float16.float16ToRawShortBits(Float16.valueOf(Math.acos(Float16.shortBitsToFloat16(a).doubleValue()))));
            case VECTOR_OP_ATAN: return (v0, m) ->
                    v0.uOp(m, (i, a) -> Float16.float16ToRawShortBits(Float16.valueOf(Math.atan(Float16.shortBitsToFloat16(a).doubleValue()))));
            case VECTOR_OP_EXP: return (v0, m) ->
                    v0.uOp(m, (i, a) -> Float16.float16ToRawShortBits(Float16.valueOf(Math.exp(Float16.shortBitsToFloat16(a).doubleValue()))));
            case VECTOR_OP_LOG: return (v0, m) ->
                    v0.uOp(m, (i, a) -> Float16.float16ToRawShortBits(Float16.valueOf(Math.log(Float16.shortBitsToFloat16(a).doubleValue()))));
            case VECTOR_OP_LOG10: return (v0, m) ->
                    v0.uOp(m, (i, a) -> Float16.float16ToRawShortBits(Float16.valueOf(Math.log10(Float16.shortBitsToFloat16(a).doubleValue()))));
            case VECTOR_OP_SQRT: return (v0, m) ->
                    v0.uOp(m, (i, a) -> Float16.float16ToRawShortBits(Float16.valueOf(Math.sqrt(Float16.shortBitsToFloat16(a).doubleValue()))));
            case VECTOR_OP_CBRT: return (v0, m) ->
                    v0.uOp(m, (i, a) -> Float16.float16ToRawShortBits(Float16.valueOf(Math.cbrt(Float16.shortBitsToFloat16(a).doubleValue()))));
            case VECTOR_OP_SINH: return (v0, m) ->
                    v0.uOp(m, (i, a) -> Float16.float16ToRawShortBits(Float16.valueOf(Math.sinh(Float16.shortBitsToFloat16(a).doubleValue()))));
            case VECTOR_OP_COSH: return (v0, m) ->
                    v0.uOp(m, (i, a) -> Float16.float16ToRawShortBits(Float16.valueOf(Math.cosh(Float16.shortBitsToFloat16(a).doubleValue()))));
            case VECTOR_OP_TANH: return (v0, m) ->
                    v0.uOp(m, (i, a) -> Float16.float16ToRawShortBits(Float16.valueOf(Math.tanh(Float16.shortBitsToFloat16(a).doubleValue()))));
            case VECTOR_OP_EXPM1: return (v0, m) ->
                    v0.uOp(m, (i, a) -> Float16.float16ToRawShortBits(Float16.valueOf(Math.expm1(Float16.shortBitsToFloat16(a).doubleValue()))));
            case VECTOR_OP_LOG1P: return (v0, m) ->
                    v0.uOp(m, (i, a) -> Float16.float16ToRawShortBits(Float16.valueOf(Math.log1p(Float16.shortBitsToFloat16(a).doubleValue()))));
            default: return null;
        }
    }

    // Binary lanewise support

    /**
     * {@inheritDoc} <!--workaround-->
     * @see #lanewise(VectorOperators.Binary,short)
     * @see #lanewise(VectorOperators.Binary,short,VectorMask)
     */
    @Override
    public abstract
    HalffloatVector lanewise(VectorOperators.Binary op,
                                  Vector<Float16> v);
    @ForceInline
    final
    HalffloatVector lanewiseTemplate(VectorOperators.Binary op,
                                          Vector<Float16> v) {
        HalffloatVector that = (HalffloatVector) v;
        that.check(this);

        if (opKind(op, VO_SPECIAL )) {
            if (op == FIRST_NONZERO) {
                VectorMask<Short> mask
                    = this.viewAsIntegralLanes().compare(EQ, (short) 0);
                return this.blend(that, mask.cast(vspecies()));
            }
            else if (opKind(op, VO_MATHLIB)) {
                return binaryMathOp(op, that);
            }
        }

        int opc = opCode(op);
        return VectorSupport.binaryOp(
            opc, getClass(), null, short.class, VECTOR_OPER_TYPE, length(),
            this, that, null,
            BIN_IMPL.find(op, opc, HalffloatVector::binaryOperations));
    }

    /**
     * {@inheritDoc} <!--workaround-->
     * @see #lanewise(VectorOperators.Binary,short,VectorMask)
     */
    @Override
    public abstract
    HalffloatVector lanewise(VectorOperators.Binary op,
                                  Vector<Float16> v,
                                  VectorMask<Float16> m);
    @ForceInline
    final
    HalffloatVector lanewiseTemplate(VectorOperators.Binary op,
                                          Class<? extends VectorMask<Float16>> maskClass,
                                          Vector<Float16> v, VectorMask<Float16> m) {
        HalffloatVector that = (HalffloatVector) v;
        that.check(this);
        m.check(maskClass, this);

        if (opKind(op, VO_SPECIAL )) {
            if (op == FIRST_NONZERO) {
                ShortVector bits = this.viewAsIntegralLanes();
                VectorMask<Short> mask
                    = bits.compare(EQ, (short) 0, m.cast(bits.vspecies()));
                return this.blend(that, mask.cast(vspecies()));
            }
            else if (opKind(op, VO_MATHLIB)) {
                return this.blend(binaryMathOp(op, that), m);
            }

        }

        int opc = opCode(op);
        return VectorSupport.binaryOp(
            opc, getClass(), maskClass, short.class, VECTOR_OPER_TYPE, length(),
            this, that, m,
            BIN_IMPL.find(op, opc, HalffloatVector::binaryOperations));
    }

    @ForceInline
    final
    HalffloatVector binaryMathOp(VectorOperators.Binary op, HalffloatVector that) {
        return VectorMathLibrary.binaryMathOp(op, opCode(op), species(), HalffloatVector::binaryOperations,
                                              this, that);
    }

    private static final
    ImplCache<Binary, BinaryOperation<HalffloatVector, VectorMask<Float16>>>
        BIN_IMPL = new ImplCache<>(Binary.class, HalffloatVector.class);

    private static BinaryOperation<HalffloatVector, VectorMask<Float16>> binaryOperations(int opc_) {
        switch (opc_) {
            case VECTOR_OP_ADD: return (v0, v1, vm) ->
                    v0.bOp(v1, vm, (i, a, b) -> Float16.float16ToRawShortBits(Float16.add(Float16.shortBitsToFloat16(a), Float16.shortBitsToFloat16(b))));
            case VECTOR_OP_SUB: return (v0, v1, vm) ->
                    v0.bOp(v1, vm, (i, a, b) -> Float16.float16ToRawShortBits(Float16.subtract(Float16.shortBitsToFloat16(a), Float16.shortBitsToFloat16(b))));
            case VECTOR_OP_MUL: return (v0, v1, vm) ->
                    v0.bOp(v1, vm, (i, a, b) -> Float16.float16ToRawShortBits(Float16.multiply(Float16.shortBitsToFloat16(a), Float16.shortBitsToFloat16(b))));
            case VECTOR_OP_DIV: return (v0, v1, vm) ->
                    v0.bOp(v1, vm, (i, a, b) -> Float16.float16ToRawShortBits(Float16.divide(Float16.shortBitsToFloat16(a), Float16.shortBitsToFloat16(b))));
            case VECTOR_OP_MAX: return (v0, v1, vm) ->
                    v0.bOp(v1, vm, (i, a, b) -> Float16.float16ToRawShortBits(Float16.max(Float16.shortBitsToFloat16(a), Float16.shortBitsToFloat16(b))));
            case VECTOR_OP_MIN: return (v0, v1, vm) ->
                    v0.bOp(v1, vm, (i, a, b) -> Float16.float16ToRawShortBits(Float16.min(Float16.shortBitsToFloat16(a), Float16.shortBitsToFloat16(b))));
            case VECTOR_OP_ATAN2: return (v0, v1, vm) ->
                    v0.bOp(v1, vm, (i, a, b) -> Float16.float16ToRawShortBits(Float16.valueOf(Math.atan2(Float16.shortBitsToFloat16(a).floatValue(), Float16.shortBitsToFloat16(b).floatValue()))));
            case VECTOR_OP_POW: return (v0, v1, vm) ->
                    v0.bOp(v1, vm, (i, a, b) -> Float16.float16ToRawShortBits(Float16.valueOf(Math.pow(Float16.shortBitsToFloat16(a).floatValue(), Float16.shortBitsToFloat16(b).floatValue()))));
            case VECTOR_OP_HYPOT: return (v0, v1, vm) ->
                    v0.bOp(v1, vm, (i, a, b) -> Float16.float16ToRawShortBits(Float16.valueOf(Math.hypot(Float16.shortBitsToFloat16(a).floatValue(), Float16.shortBitsToFloat16(b).floatValue()))));
            default: return null;
        }
    }

    // FIXME: Maybe all of the public final methods in this file (the
    // simple ones that just call lanewise) should be pushed down to
    // the X-VectorBits template.  They can't optimize properly at
    // this level, and must rely on inlining.  Does it work?
    // (If it works, of course keep the code here.)

    /**
     * Combines the lane values of this vector
     * with the value of a broadcast scalar.
     *
     * This is a lane-wise binary operation which applies
     * the selected operation to each lane.
     * The return value will be equal to this expression:
     * {@code this.lanewise(op, this.broadcast(e))}.
     *
     * @param op the operation used to process lane values
     * @param e the input scalar
     * @return the result of applying the operation lane-wise
     *         to the two input vectors
     * @throws UnsupportedOperationException if this vector does
     *         not support the requested operation
     * @see #lanewise(VectorOperators.Binary,Vector)
     * @see #lanewise(VectorOperators.Binary,short,VectorMask)
     */
    @ForceInline
    public final
    HalffloatVector lanewise(VectorOperators.Binary op,
                                  short e) {
        return lanewise(op, broadcast(e));
    }

    /**
     * Combines the lane values of this vector
     * with the value of a broadcast scalar,
     * with selection of lane elements controlled by a mask.
     *
     * This is a masked lane-wise binary operation which applies
     * the selected operation to each lane.
     * The return value will be equal to this expression:
     * {@code this.lanewise(op, this.broadcast(e), m)}.
     *
     * @param op the operation used to process lane values
     * @param e the input scalar
     * @param m the mask controlling lane selection
     * @return the result of applying the operation lane-wise
     *         to the input vector and the scalar
     * @throws UnsupportedOperationException if this vector does
     *         not support the requested operation
     * @see #lanewise(VectorOperators.Binary,Vector,VectorMask)
     * @see #lanewise(VectorOperators.Binary,short)
     */
    @ForceInline
    public final
    HalffloatVector lanewise(VectorOperators.Binary op,
                                  short e,
                                  VectorMask<Float16> m) {
        return lanewise(op, broadcast(e), m);
    }

    /**
     * {@inheritDoc} <!--workaround-->
     * @apiNote
     * When working with vector subtypes like {@code HalffloatVector},
     * {@linkplain #lanewise(VectorOperators.Binary,short)
     * the more strongly typed method}
     * is typically selected.  It can be explicitly selected
     * using a cast: {@code v.lanewise(op,(short)e)}.
     * The two expressions will produce numerically identical results.
     */
    @ForceInline
    public final
    HalffloatVector lanewise(VectorOperators.Binary op,
                                  long e) {
        short e1 = (short) e;
        if ((long)e1 != e) {
            vspecies().checkValue(e);  // for exception
        }
        return lanewise(op, e1);
    }

    /**
     * {@inheritDoc} <!--workaround-->
     * @apiNote
     * When working with vector subtypes like {@code HalffloatVector},
     * {@linkplain #lanewise(VectorOperators.Binary,short,VectorMask)
     * the more strongly typed method}
     * is typically selected.  It can be explicitly selected
     * using a cast: {@code v.lanewise(op,(short)e,m)}.
     * The two expressions will produce numerically identical results.
     */
    @ForceInline
    public final
    HalffloatVector lanewise(VectorOperators.Binary op,
                                  long e, VectorMask<Float16> m) {
        short e1 = (short) e;
        if ((long)e1 != e) {
            vspecies().checkValue(e);  // for exception
        }
        return lanewise(op, e1, m);
    }


    // Ternary lanewise support

    // Ternary operators come in eight variations:
    //   lanewise(op, [broadcast(e1)|v1], [broadcast(e2)|v2])
    //   lanewise(op, [broadcast(e1)|v1], [broadcast(e2)|v2], mask)

    // It is annoying to support all of these variations of masking
    // and broadcast, but it would be more surprising not to continue
    // the obvious pattern started by unary and binary.

    /**
     * {@inheritDoc} <!--workaround-->
     * @see #lanewise(VectorOperators.Ternary,short,short,VectorMask)
     * @see #lanewise(VectorOperators.Ternary,Vector,short,VectorMask)
     * @see #lanewise(VectorOperators.Ternary,short,Vector,VectorMask)
     * @see #lanewise(VectorOperators.Ternary,short,short)
     * @see #lanewise(VectorOperators.Ternary,Vector,short)
     * @see #lanewise(VectorOperators.Ternary,short,Vector)
     */
    @Override
    public abstract
    HalffloatVector lanewise(VectorOperators.Ternary op,
                                                  Vector<Float16> v1,
                                                  Vector<Float16> v2);
    @ForceInline
    final
    HalffloatVector lanewiseTemplate(VectorOperators.Ternary op,
                                          Vector<Float16> v1,
                                          Vector<Float16> v2) {
        HalffloatVector that = (HalffloatVector) v1;
        HalffloatVector tother = (HalffloatVector) v2;
        // It's a word: https://www.dictionary.com/browse/tother
        // See also Chapter 11 of Dickens, Our Mutual Friend:
        // "Totherest Governor," replied Mr Riderhood...
        that.check(this);
        tother.check(this);
        int opc = opCode(op);
        return VectorSupport.ternaryOp(
            opc, getClass(), null, short.class, VECTOR_OPER_TYPE, length(),
            this, that, tother, null,
            TERN_IMPL.find(op, opc, HalffloatVector::ternaryOperations));
    }

    /**
     * {@inheritDoc} <!--workaround-->
     * @see #lanewise(VectorOperators.Ternary,short,short,VectorMask)
     * @see #lanewise(VectorOperators.Ternary,Vector,short,VectorMask)
     * @see #lanewise(VectorOperators.Ternary,short,Vector,VectorMask)
     */
    @Override
    public abstract
    HalffloatVector lanewise(VectorOperators.Ternary op,
                                  Vector<Float16> v1,
                                  Vector<Float16> v2,
                                  VectorMask<Float16> m);
    @ForceInline
    final
    HalffloatVector lanewiseTemplate(VectorOperators.Ternary op,
                                          Class<? extends VectorMask<Float16>> maskClass,
                                          Vector<Float16> v1,
                                          Vector<Float16> v2,
                                          VectorMask<Float16> m) {
        HalffloatVector that = (HalffloatVector) v1;
        HalffloatVector tother = (HalffloatVector) v2;
        // It's a word: https://www.dictionary.com/browse/tother
        // See also Chapter 11 of Dickens, Our Mutual Friend:
        // "Totherest Governor," replied Mr Riderhood...
        that.check(this);
        tother.check(this);
        m.check(maskClass, this);

        int opc = opCode(op);
        return VectorSupport.ternaryOp(
            opc, getClass(), maskClass, short.class, VECTOR_OPER_TYPE, length(),
            this, that, tother, m,
            TERN_IMPL.find(op, opc, HalffloatVector::ternaryOperations));
    }

    private static final
    ImplCache<Ternary, TernaryOperation<HalffloatVector, VectorMask<Float16>>>
        TERN_IMPL = new ImplCache<>(Ternary.class, HalffloatVector.class);

    private static TernaryOperation<HalffloatVector, VectorMask<Float16>> ternaryOperations(int opc_) {
        switch (opc_) {
            case VECTOR_OP_FMA: return (v0, v1_, v2_, m) -> v0.tOp(v1_, v2_, m, (i, a, b, c) ->
                                        Float16.float16ToRawShortBits(Float16.fma(Float16.shortBitsToFloat16(a), Float16.shortBitsToFloat16(b), Float16.shortBitsToFloat16(c))));
            default: return null;
        }
    }

    /**
     * Combines the lane values of this vector
     * with the values of two broadcast scalars.
     *
     * This is a lane-wise ternary operation which applies
     * the selected operation to each lane.
     * The return value will be equal to this expression:
     * {@code this.lanewise(op, this.broadcast(e1), this.broadcast(e2))}.
     *
     * @param op the operation used to combine lane values
     * @param e1 the first input scalar
     * @param e2 the second input scalar
     * @return the result of applying the operation lane-wise
     *         to the input vector and the scalars
     * @throws UnsupportedOperationException if this vector does
     *         not support the requested operation
     * @see #lanewise(VectorOperators.Ternary,Vector,Vector)
     * @see #lanewise(VectorOperators.Ternary,short,short,VectorMask)
     */
    @ForceInline
    public final
    HalffloatVector lanewise(VectorOperators.Ternary op, //(op,e1,e2)
                                  short e1,
                                  short e2) {
        return lanewise(op, broadcast(e1), broadcast(e2));
    }

    /**
     * Combines the lane values of this vector
     * with the values of two broadcast scalars,
     * with selection of lane elements controlled by a mask.
     *
     * This is a masked lane-wise ternary operation which applies
     * the selected operation to each lane.
     * The return value will be equal to this expression:
     * {@code this.lanewise(op, this.broadcast(e1), this.broadcast(e2), m)}.
     *
     * @param op the operation used to combine lane values
     * @param e1 the first input scalar
     * @param e2 the second input scalar
     * @param m the mask controlling lane selection
     * @return the result of applying the operation lane-wise
     *         to the input vector and the scalars
     * @throws UnsupportedOperationException if this vector does
     *         not support the requested operation
     * @see #lanewise(VectorOperators.Ternary,Vector,Vector,VectorMask)
     * @see #lanewise(VectorOperators.Ternary,short,short)
     */
    @ForceInline
    public final
    HalffloatVector lanewise(VectorOperators.Ternary op, //(op,e1,e2,m)
                                  short e1,
                                  short e2,
                                  VectorMask<Float16> m) {
        return lanewise(op, broadcast(e1), broadcast(e2), m);
    }

    /**
     * Combines the lane values of this vector
     * with the values of another vector and a broadcast scalar.
     *
     * This is a lane-wise ternary operation which applies
     * the selected operation to each lane.
     * The return value will be equal to this expression:
     * {@code this.lanewise(op, v1, this.broadcast(e2))}.
     *
     * @param op the operation used to combine lane values
     * @param v1 the other input vector
     * @param e2 the input scalar
     * @return the result of applying the operation lane-wise
     *         to the input vectors and the scalar
     * @throws UnsupportedOperationException if this vector does
     *         not support the requested operation
     * @see #lanewise(VectorOperators.Ternary,short,short)
     * @see #lanewise(VectorOperators.Ternary,Vector,short,VectorMask)
     */
    @ForceInline
    public final
    HalffloatVector lanewise(VectorOperators.Ternary op, //(op,v1,e2)
                                  Vector<Float16> v1,
                                  short e2) {
        return lanewise(op, v1, broadcast(e2));
    }

    /**
     * Combines the lane values of this vector
     * with the values of another vector and a broadcast scalar,
     * with selection of lane elements controlled by a mask.
     *
     * This is a masked lane-wise ternary operation which applies
     * the selected operation to each lane.
     * The return value will be equal to this expression:
     * {@code this.lanewise(op, v1, this.broadcast(e2), m)}.
     *
     * @param op the operation used to combine lane values
     * @param v1 the other input vector
     * @param e2 the input scalar
     * @param m the mask controlling lane selection
     * @return the result of applying the operation lane-wise
     *         to the input vectors and the scalar
     * @throws UnsupportedOperationException if this vector does
     *         not support the requested operation
     * @see #lanewise(VectorOperators.Ternary,Vector,Vector)
     * @see #lanewise(VectorOperators.Ternary,short,short,VectorMask)
     * @see #lanewise(VectorOperators.Ternary,Vector,short)
     */
    @ForceInline
    public final
    HalffloatVector lanewise(VectorOperators.Ternary op, //(op,v1,e2,m)
                                  Vector<Float16> v1,
                                  short e2,
                                  VectorMask<Float16> m) {
        return lanewise(op, v1, broadcast(e2), m);
    }

    /**
     * Combines the lane values of this vector
     * with the values of another vector and a broadcast scalar.
     *
     * This is a lane-wise ternary operation which applies
     * the selected operation to each lane.
     * The return value will be equal to this expression:
     * {@code this.lanewise(op, this.broadcast(e1), v2)}.
     *
     * @param op the operation used to combine lane values
     * @param e1 the input scalar
     * @param v2 the other input vector
     * @return the result of applying the operation lane-wise
     *         to the input vectors and the scalar
     * @throws UnsupportedOperationException if this vector does
     *         not support the requested operation
     * @see #lanewise(VectorOperators.Ternary,Vector,Vector)
     * @see #lanewise(VectorOperators.Ternary,short,Vector,VectorMask)
     */
    @ForceInline
    public final
    HalffloatVector lanewise(VectorOperators.Ternary op, //(op,e1,v2)
                                  short e1,
                                  Vector<Float16> v2) {
        return lanewise(op, broadcast(e1), v2);
    }

    /**
     * Combines the lane values of this vector
     * with the values of another vector and a broadcast scalar,
     * with selection of lane elements controlled by a mask.
     *
     * This is a masked lane-wise ternary operation which applies
     * the selected operation to each lane.
     * The return value will be equal to this expression:
     * {@code this.lanewise(op, this.broadcast(e1), v2, m)}.
     *
     * @param op the operation used to combine lane values
     * @param e1 the input scalar
     * @param v2 the other input vector
     * @param m the mask controlling lane selection
     * @return the result of applying the operation lane-wise
     *         to the input vectors and the scalar
     * @throws UnsupportedOperationException if this vector does
     *         not support the requested operation
     * @see #lanewise(VectorOperators.Ternary,Vector,Vector,VectorMask)
     * @see #lanewise(VectorOperators.Ternary,short,Vector)
     */
    @ForceInline
    public final
    HalffloatVector lanewise(VectorOperators.Ternary op, //(op,e1,v2,m)
                                  short e1,
                                  Vector<Float16> v2,
                                  VectorMask<Float16> m) {
        return lanewise(op, broadcast(e1), v2, m);
    }

    // (Thus endeth the Great and Mighty Ternary Ogdoad.)
    // https://en.wikipedia.org/wiki/Ogdoad

    /// FULL-SERVICE BINARY METHODS: ADD, SUB, MUL, DIV
    //
    // These include masked and non-masked versions.
    // This subclass adds broadcast (masked or not).

    /**
     * {@inheritDoc} <!--workaround-->
     * @see #add(short)
     */
    @Override
    @ForceInline
    public final HalffloatVector add(Vector<Float16> v) {
        return lanewise(ADD, v);
    }

    /**
     * Adds this vector to the broadcast of an input scalar.
     *
     * This is a lane-wise binary operation which applies
     * the primitive addition operation ({@code +}) to each lane.
     *
     * This method is also equivalent to the expression
     * {@link #lanewise(VectorOperators.Binary,short)
     *    lanewise}{@code (}{@link VectorOperators#ADD
     *    ADD}{@code , e)}.
     *
     * @param e the input scalar
     * @return the result of adding each lane of this vector to the scalar
     * @see #add(Vector)
     * @see #broadcast(short)
     * @see #add(short,VectorMask)
     * @see VectorOperators#ADD
     * @see #lanewise(VectorOperators.Binary,Vector)
     * @see #lanewise(VectorOperators.Binary,short)
     */
    @ForceInline
    public final
    HalffloatVector add(short e) {
        return lanewise(ADD, e);
    }

    /**
     * {@inheritDoc} <!--workaround-->
     * @see #add(short,VectorMask)
     */
    @Override
    @ForceInline
    public final HalffloatVector add(Vector<Float16> v,
                                          VectorMask<Float16> m) {
        return lanewise(ADD, v, m);
    }

    /**
     * Adds this vector to the broadcast of an input scalar,
     * selecting lane elements controlled by a mask.
     *
     * This is a masked lane-wise binary operation which applies
     * the primitive addition operation ({@code +}) to each lane.
     *
     * This method is also equivalent to the expression
     * {@link #lanewise(VectorOperators.Binary,short,VectorMask)
     *    lanewise}{@code (}{@link VectorOperators#ADD
     *    ADD}{@code , s, m)}.
     *
     * @param e the input scalar
     * @param m the mask controlling lane selection
     * @return the result of adding each lane of this vector to the scalar
     * @see #add(Vector,VectorMask)
     * @see #broadcast(short)
     * @see #add(short)
     * @see VectorOperators#ADD
     * @see #lanewise(VectorOperators.Binary,Vector)
     * @see #lanewise(VectorOperators.Binary,short)
     */
    @ForceInline
    public final HalffloatVector add(short e,
                                          VectorMask<Float16> m) {
        return lanewise(ADD, e, m);
    }

    /**
     * {@inheritDoc} <!--workaround-->
     * @see #sub(short)
     */
    @Override
    @ForceInline
    public final HalffloatVector sub(Vector<Float16> v) {
        return lanewise(SUB, v);
    }

    /**
     * Subtracts an input scalar from this vector.
     *
     * This is a masked lane-wise binary operation which applies
     * the primitive subtraction operation ({@code -}) to each lane.
     *
     * This method is also equivalent to the expression
     * {@link #lanewise(VectorOperators.Binary,short)
     *    lanewise}{@code (}{@link VectorOperators#SUB
     *    SUB}{@code , e)}.
     *
     * @param e the input scalar
     * @return the result of subtracting the scalar from each lane of this vector
     * @see #sub(Vector)
     * @see #broadcast(short)
     * @see #sub(short,VectorMask)
     * @see VectorOperators#SUB
     * @see #lanewise(VectorOperators.Binary,Vector)
     * @see #lanewise(VectorOperators.Binary,short)
     */
    @ForceInline
    public final HalffloatVector sub(short e) {
        return lanewise(SUB, e);
    }

    /**
     * {@inheritDoc} <!--workaround-->
     * @see #sub(short,VectorMask)
     */
    @Override
    @ForceInline
    public final HalffloatVector sub(Vector<Float16> v,
                                          VectorMask<Float16> m) {
        return lanewise(SUB, v, m);
    }

    /**
     * Subtracts an input scalar from this vector
     * under the control of a mask.
     *
     * This is a masked lane-wise binary operation which applies
     * the primitive subtraction operation ({@code -}) to each lane.
     *
     * This method is also equivalent to the expression
     * {@link #lanewise(VectorOperators.Binary,short,VectorMask)
     *    lanewise}{@code (}{@link VectorOperators#SUB
     *    SUB}{@code , s, m)}.
     *
     * @param e the input scalar
     * @param m the mask controlling lane selection
     * @return the result of subtracting the scalar from each lane of this vector
     * @see #sub(Vector,VectorMask)
     * @see #broadcast(short)
     * @see #sub(short)
     * @see VectorOperators#SUB
     * @see #lanewise(VectorOperators.Binary,Vector)
     * @see #lanewise(VectorOperators.Binary,short)
     */
    @ForceInline
    public final HalffloatVector sub(short e,
                                          VectorMask<Float16> m) {
        return lanewise(SUB, e, m);
    }

    /**
     * {@inheritDoc} <!--workaround-->
     * @see #mul(short)
     */
    @Override
    @ForceInline
    public final HalffloatVector mul(Vector<Float16> v) {
        return lanewise(MUL, v);
    }

    /**
     * Multiplies this vector by the broadcast of an input scalar.
     *
     * This is a lane-wise binary operation which applies
     * the primitive multiplication operation ({@code *}) to each lane.
     *
     * This method is also equivalent to the expression
     * {@link #lanewise(VectorOperators.Binary,short)
     *    lanewise}{@code (}{@link VectorOperators#MUL
     *    MUL}{@code , e)}.
     *
     * @param e the input scalar
     * @return the result of multiplying this vector by the given scalar
     * @see #mul(Vector)
     * @see #broadcast(short)
     * @see #mul(short,VectorMask)
     * @see VectorOperators#MUL
     * @see #lanewise(VectorOperators.Binary,Vector)
     * @see #lanewise(VectorOperators.Binary,short)
     */
    @ForceInline
    public final HalffloatVector mul(short e) {
        return lanewise(MUL, e);
    }

    /**
     * {@inheritDoc} <!--workaround-->
     * @see #mul(short,VectorMask)
     */
    @Override
    @ForceInline
    public final HalffloatVector mul(Vector<Float16> v,
                                          VectorMask<Float16> m) {
        return lanewise(MUL, v, m);
    }

    /**
     * Multiplies this vector by the broadcast of an input scalar,
     * selecting lane elements controlled by a mask.
     *
     * This is a masked lane-wise binary operation which applies
     * the primitive multiplication operation ({@code *}) to each lane.
     *
     * This method is also equivalent to the expression
     * {@link #lanewise(VectorOperators.Binary,short,VectorMask)
     *    lanewise}{@code (}{@link VectorOperators#MUL
     *    MUL}{@code , s, m)}.
     *
     * @param e the input scalar
     * @param m the mask controlling lane selection
     * @return the result of muling each lane of this vector to the scalar
     * @see #mul(Vector,VectorMask)
     * @see #broadcast(short)
     * @see #mul(short)
     * @see VectorOperators#MUL
     * @see #lanewise(VectorOperators.Binary,Vector)
     * @see #lanewise(VectorOperators.Binary,short)
     */
    @ForceInline
    public final HalffloatVector mul(short e,
                                          VectorMask<Float16> m) {
        return lanewise(MUL, e, m);
    }

    /**
     * {@inheritDoc} <!--workaround-->
     * @apiNote Because the underlying scalar operator is an IEEE
     * floating point number, division by zero in fact will
     * not throw an exception, but will yield a signed
     * infinity or NaN.
     */
    @Override
    @ForceInline
    public final HalffloatVector div(Vector<Float16> v) {
        return lanewise(DIV, v);
    }

    /**
     * Divides this vector by the broadcast of an input scalar.
     *
     * This is a lane-wise binary operation which applies
     * the primitive division operation ({@code /}) to each lane.
     *
     * This method is also equivalent to the expression
     * {@link #lanewise(VectorOperators.Binary,short)
     *    lanewise}{@code (}{@link VectorOperators#DIV
     *    DIV}{@code , e)}.
     *
     * @apiNote Because the underlying scalar operator is an IEEE
     * floating point number, division by zero in fact will
     * not throw an exception, but will yield a signed
     * infinity or NaN.
     *
     * @param e the input scalar
     * @return the result of dividing each lane of this vector by the scalar
     * @see #div(Vector)
     * @see #broadcast(short)
     * @see #div(short,VectorMask)
     * @see VectorOperators#DIV
     * @see #lanewise(VectorOperators.Binary,Vector)
     * @see #lanewise(VectorOperators.Binary,short)
     */
    @ForceInline
    public final HalffloatVector div(short e) {
        return lanewise(DIV, e);
    }

    /**
     * {@inheritDoc} <!--workaround-->
     * @see #div(short,VectorMask)
     * @apiNote Because the underlying scalar operator is an IEEE
     * floating point number, division by zero in fact will
     * not throw an exception, but will yield a signed
     * infinity or NaN.
     */
    @Override
    @ForceInline
    public final HalffloatVector div(Vector<Float16> v,
                                          VectorMask<Float16> m) {
        return lanewise(DIV, v, m);
    }

    /**
     * Divides this vector by the broadcast of an input scalar,
     * selecting lane elements controlled by a mask.
     *
     * This is a masked lane-wise binary operation which applies
     * the primitive division operation ({@code /}) to each lane.
     *
     * This method is also equivalent to the expression
     * {@link #lanewise(VectorOperators.Binary,short,VectorMask)
     *    lanewise}{@code (}{@link VectorOperators#DIV
     *    DIV}{@code , s, m)}.
     *
     * @apiNote Because the underlying scalar operator is an IEEE
     * floating point number, division by zero in fact will
     * not throw an exception, but will yield a signed
     * infinity or NaN.
     *
     * @param e the input scalar
     * @param m the mask controlling lane selection
     * @return the result of dividing each lane of this vector by the scalar
     * @see #div(Vector,VectorMask)
     * @see #broadcast(short)
     * @see #div(short)
     * @see VectorOperators#DIV
     * @see #lanewise(VectorOperators.Binary,Vector)
     * @see #lanewise(VectorOperators.Binary,short)
     */
    @ForceInline
    public final HalffloatVector div(short e,
                                          VectorMask<Float16> m) {
        return lanewise(DIV, e, m);
    }

    /// END OF FULL-SERVICE BINARY METHODS

    /// SECOND-TIER BINARY METHODS
    //
    // There are no masked versions.

    /**
     * {@inheritDoc} <!--workaround-->
     * @apiNote
     * For this method, floating point negative
     * zero {@code -0.0} is treated as a value distinct from, and less
     * than the default value (positive zero).
     */
    @Override
    @ForceInline
    public final HalffloatVector min(Vector<Float16> v) {
        return lanewise(MIN, v);
    }

    // FIXME:  "broadcast of an input scalar" is really wordy.  Reduce?
    /**
     * Computes the smaller of this vector and the broadcast of an input scalar.
     *
     * This is a lane-wise binary operation which applies the
     * operation {@code Math.min()} to each pair of
     * corresponding lane values.
     *
     * This method is also equivalent to the expression
     * {@link #lanewise(VectorOperators.Binary,short)
     *    lanewise}{@code (}{@link VectorOperators#MIN
     *    MIN}{@code , e)}.
     *
     * @param e the input scalar
     * @return the result of multiplying this vector by the given scalar
     * @see #min(Vector)
     * @see #broadcast(short)
     * @see VectorOperators#MIN
     * @see #lanewise(VectorOperators.Binary,short,VectorMask)
     * @apiNote
     * For this method, floating point negative
     * zero {@code -0.0} is treated as a value distinct from, and less
     * than the default value (positive zero).
     */
    @ForceInline
    public final HalffloatVector min(short e) {
        return lanewise(MIN, e);
    }

    /**
     * {@inheritDoc} <!--workaround-->
     * @apiNote
     * For this method, floating point negative
     * zero {@code -0.0} is treated as a value distinct from, and less
     * than the default value (positive zero).
     */
    @Override
    @ForceInline
    public final HalffloatVector max(Vector<Float16> v) {
        return lanewise(MAX, v);
    }

    /**
     * Computes the larger of this vector and the broadcast of an input scalar.
     *
     * This is a lane-wise binary operation which applies the
     * operation {@code Math.max()} to each pair of
     * corresponding lane values.
     *
     * This method is also equivalent to the expression
     * {@link #lanewise(VectorOperators.Binary,short)
     *    lanewise}{@code (}{@link VectorOperators#MAX
     *    MAX}{@code , e)}.
     *
     * @param e the input scalar
     * @return the result of multiplying this vector by the given scalar
     * @see #max(Vector)
     * @see #broadcast(short)
     * @see VectorOperators#MAX
     * @see #lanewise(VectorOperators.Binary,short,VectorMask)
     * @apiNote
     * For this method, floating point negative
     * zero {@code -0.0} is treated as a value distinct from, and less
     * than the default value (positive zero).
     */
    @ForceInline
    public final HalffloatVector max(short e) {
        return lanewise(MAX, e);
    }


    // common FP operator: pow
    /**
     * Raises this vector to the power of a second input vector.
     *
     * This is a lane-wise binary operation which applies an operation
     * conforming to the specification of
     * {@link Math#pow Math.pow(a,b)}
     * to each pair of corresponding lane values.
     *
     * This method is also equivalent to the expression
     * {@link #lanewise(VectorOperators.Binary,Vector)
     *    lanewise}{@code (}{@link VectorOperators#POW
     *    POW}{@code , b)}.
     *
     * <p>
     * This is not a full-service named operation like
     * {@link #add(Vector) add}.  A masked version of
     * this operation is not directly available
     * but may be obtained via the masked version of
     * {@code lanewise}.
     *
     * @param b a vector exponent by which to raise this vector
     * @return the {@code b}-th power of this vector
     * @see #pow(short)
     * @see VectorOperators#POW
     * @see #lanewise(VectorOperators.Binary,Vector,VectorMask)
     */
    @ForceInline
    public final HalffloatVector pow(Vector<Float16> b) {
        return lanewise(POW, b);
    }

    /**
     * Raises this vector to a scalar power.
     *
     * This is a lane-wise binary operation which applies an operation
     * conforming to the specification of
     * {@link Math#pow Math.pow(a,b)}
     * to each pair of corresponding lane values.
     *
     * This method is also equivalent to the expression
     * {@link #lanewise(VectorOperators.Binary,Vector)
     *    lanewise}{@code (}{@link VectorOperators#POW
     *    POW}{@code , b)}.
     *
     * @param b a scalar exponent by which to raise this vector
     * @return the {@code b}-th power of this vector
     * @see #pow(Vector)
     * @see VectorOperators#POW
     * @see #lanewise(VectorOperators.Binary,short,VectorMask)
     */
    @ForceInline
    public final HalffloatVector pow(short b) {
        return lanewise(POW, b);
    }

    /// UNARY METHODS

    /**
     * {@inheritDoc} <!--workaround-->
     */
    @Override
    @ForceInline
    public final
    HalffloatVector neg() {
        return lanewise(NEG);
    }

    /**
     * {@inheritDoc} <!--workaround-->
     */
    @Override
    @ForceInline
    public final
    HalffloatVector abs() {
        return lanewise(ABS);
    }



    // sqrt
    /**
     * Computes the square root of this vector.
     *
     * This is a lane-wise unary operation which applies an operation
     * conforming to the specification of
     * {@link Math#sqrt Math.sqrt(a)}
     * to each lane value.
     *
     * This method is also equivalent to the expression
     * {@link #lanewise(VectorOperators.Unary)
     *    lanewise}{@code (}{@link VectorOperators#SQRT
     *    SQRT}{@code )}.
     *
     * @return the square root of this vector
     * @see VectorOperators#SQRT
     * @see #lanewise(VectorOperators.Unary,VectorMask)
     */
    @ForceInline
    public final HalffloatVector sqrt() {
        return lanewise(SQRT);
    }

    /// COMPARISONS

    /**
     * {@inheritDoc} <!--workaround-->
     */
    @Override
    @ForceInline
    public final
    VectorMask<Float16> eq(Vector<Float16> v) {
        return compare(EQ, v);
    }

    /**
     * Tests if this vector is equal to an input scalar.
     *
     * This is a lane-wise binary test operation which applies
     * the primitive equals operation ({@code ==}) to each lane.
     * The result is the same as {@code compare(VectorOperators.Comparison.EQ, e)}.
     *
     * @param e the input scalar
     * @return the result mask of testing if this vector
     *         is equal to {@code e}
     * @see #compare(VectorOperators.Comparison,short)
     */
    @ForceInline
    public final
    VectorMask<Float16> eq(short e) {
        return compare(EQ, e);
    }

    /**
     * {@inheritDoc} <!--workaround-->
     */
    @Override
    @ForceInline
    public final
    VectorMask<Float16> lt(Vector<Float16> v) {
        return compare(LT, v);
    }

    /**
     * Tests if this vector is less than an input scalar.
     *
     * This is a lane-wise binary test operation which applies
     * the primitive less than operation ({@code <}) to each lane.
     * The result is the same as {@code compare(VectorOperators.LT, e)}.
     *
     * @param e the input scalar
     * @return the mask result of testing if this vector
     *         is less than the input scalar
     * @see #compare(VectorOperators.Comparison,short)
     */
    @ForceInline
    public final
    VectorMask<Float16> lt(short e) {
        return compare(LT, e);
    }

    /**
     * {@inheritDoc} <!--workaround-->
     */
    @Override
    public abstract
    VectorMask<Float16> test(VectorOperators.Test op);

    /*package-private*/
    @ForceInline
    final
    <M extends VectorMask<Float16>>
    M testTemplate(Class<M> maskType, Test op) {
        HalffloatSpecies vsp = vspecies();
        if (opKind(op, VO_SPECIAL)) {
            ShortVector bits = this.viewAsIntegralLanes();
            VectorMask<Short> m;
            if (op == IS_DEFAULT) {
                m = bits.compare(EQ, (short) 0);
            } else if (op == IS_NEGATIVE) {
                m = bits.compare(LT, (short) 0);
            }
            else if (op == IS_FINITE ||
                     op == IS_NAN ||
                     op == IS_INFINITE) {
                // first kill the sign:
                bits = bits.and(Short.MAX_VALUE);
                // next find the bit pattern for infinity:
                short infbits = (short) toBits(Float16.float16ToRawShortBits(Float16.POSITIVE_INFINITY));
                // now compare:
                if (op == IS_FINITE) {
                    m = bits.compare(LT, infbits);
                } else if (op == IS_NAN) {
                    m = bits.compare(GT, infbits);
                } else {
                    m = bits.compare(EQ, infbits);
                }
            }
            else {
                throw new AssertionError(op);
            }
            return maskType.cast(m.cast(vsp));
        }
        int opc = opCode(op);
        throw new AssertionError(op);
    }

    /**
     * {@inheritDoc} <!--workaround-->
     */
    @Override
    public abstract
    VectorMask<Float16> test(VectorOperators.Test op,
                                  VectorMask<Float16> m);

    /*package-private*/
    @ForceInline
    final
    <M extends VectorMask<Float16>>
    M testTemplate(Class<M> maskType, Test op, M mask) {
        HalffloatSpecies vsp = vspecies();
        mask.check(maskType, this);
        if (opKind(op, VO_SPECIAL)) {
            ShortVector bits = this.viewAsIntegralLanes();
            VectorMask<Short> m = mask.cast(ShortVector.species(shape()));
            if (op == IS_DEFAULT) {
                m = bits.compare(EQ, (short) 0, m);
            } else if (op == IS_NEGATIVE) {
                m = bits.compare(LT, (short) 0, m);
            }
            else if (op == IS_FINITE ||
                     op == IS_NAN ||
                     op == IS_INFINITE) {
                // first kill the sign:
                bits = bits.and(Short.MAX_VALUE);
                // next find the bit pattern for infinity:
                short infbits = (short) toBits(Float16.float16ToRawShortBits(Float16.POSITIVE_INFINITY));
                // now compare:
                if (op == IS_FINITE) {
                    m = bits.compare(LT, infbits, m);
                } else if (op == IS_NAN) {
                    m = bits.compare(GT, infbits, m);
                } else {
                    m = bits.compare(EQ, infbits, m);
                }
            }
            else {
                throw new AssertionError(op);
            }
            return maskType.cast(m.cast(vsp));
        }
        int opc = opCode(op);
        throw new AssertionError(op);
    }

    /**
     * {@inheritDoc} <!--workaround-->
     */
    @Override
    public abstract
    VectorMask<Float16> compare(VectorOperators.Comparison op, Vector<Float16> v);

    /*package-private*/
    @ForceInline
    final
    <M extends VectorMask<Float16>>
    M compareTemplate(Class<M> maskType, Comparison op, Vector<Float16> v) {
        HalffloatVector that = (HalffloatVector) v;
        that.check(this);
        int opc = opCode(op);
        return VectorSupport.compare(
            opc, getClass(), maskType, short.class, VECTOR_OPER_TYPE, length(),
            this, that, null,
            (cond, v0, v1, m1) -> {
                AbstractMask<Float16> m
                    = v0.bTest(cond, v1, (cond_, i, a, b)
                               -> compareWithOp(cond, a, b));
                @SuppressWarnings("unchecked")
                M m2 = (M) m;
                return m2;
            });
    }

    /*package-private*/
    @ForceInline
    final
    <M extends VectorMask<Float16>>
    M compareTemplate(Class<M> maskType, Comparison op, Vector<Float16> v, M m) {
        HalffloatVector that = (HalffloatVector) v;
        that.check(this);
        m.check(maskType, this);
        int opc = opCode(op);
        return VectorSupport.compare(
            opc, getClass(), maskType, short.class, VECTOR_OPER_TYPE, length(),
            this, that, m,
            (cond, v0, v1, m1) -> {
                AbstractMask<Float16> cmpM
                    = v0.bTest(cond, v1, (cond_, i, a, b)
                               -> compareWithOp(cond, a, b));
                @SuppressWarnings("unchecked")
                M m2 = (M) cmpM.and(m1);
                return m2;
            });
    }

    @ForceInline
    private static boolean compareWithOp(int cond, short a, short b) {
        return switch (cond) {
            case BT_eq -> Float.float16ToFloat(a) == Float.float16ToFloat(b);
            case BT_ne -> Float.float16ToFloat(a) != Float.float16ToFloat(b);
            case BT_lt -> Float.float16ToFloat(a) < Float.float16ToFloat(b);
            case BT_le -> Float.float16ToFloat(a) <= Float.float16ToFloat(b);
            case BT_gt -> Float.float16ToFloat(a) > Float.float16ToFloat(b);
            case BT_ge -> Float.float16ToFloat(a) >= Float.float16ToFloat(b);
            default -> throw new AssertionError();
        };
    }

    /**
     * Tests this vector by comparing it with an input scalar,
     * according to the given comparison operation.
     *
     * This is a lane-wise binary test operation which applies
     * the comparison operation to each lane.
     * <p>
     * The result is the same as
     * {@code compare(op, broadcast(species(), e))}.
     * That is, the scalar may be regarded as broadcast to
     * a vector of the same species, and then compared
     * against the original vector, using the selected
     * comparison operation.
     *
     * @param op the operation used to compare lane values
     * @param e the input scalar
     * @return the mask result of testing lane-wise if this vector
     *         compares to the input, according to the selected
     *         comparison operator
     * @see HalffloatVector#compare(VectorOperators.Comparison,Vector)
     * @see #eq(short)
     * @see #lt(short)
     */
    public abstract
    VectorMask<Float16> compare(Comparison op, short e);

    /*package-private*/
    @ForceInline
    final
    <M extends VectorMask<Float16>>
    M compareTemplate(Class<M> maskType, Comparison op, short e) {
        return compareTemplate(maskType, op, broadcast(e));
    }

    /**
     * Tests this vector by comparing it with an input scalar,
     * according to the given comparison operation,
     * in lanes selected by a mask.
     *
     * This is a masked lane-wise binary test operation which applies
     * to each pair of corresponding lane values.
     *
     * The returned result is equal to the expression
     * {@code compare(op,s).and(m)}.
     *
     * @param op the operation used to compare lane values
     * @param e the input scalar
     * @param m the mask controlling lane selection
     * @return the mask result of testing lane-wise if this vector
     *         compares to the input, according to the selected
     *         comparison operator,
     *         and only in the lanes selected by the mask
     * @see HalffloatVector#compare(VectorOperators.Comparison,Vector,VectorMask)
     */
    @ForceInline
    public final VectorMask<Float16> compare(VectorOperators.Comparison op,
                                               short e,
                                               VectorMask<Float16> m) {
        return compare(op, broadcast(e), m);
    }

    /**
     * {@inheritDoc} <!--workaround-->
     */
    @Override
    public abstract
    VectorMask<Float16> compare(Comparison op, long e);

    /*package-private*/
    @ForceInline
    final
    <M extends VectorMask<Float16>>
    M compareTemplate(Class<M> maskType, Comparison op, long e) {
        return compareTemplate(maskType, op, broadcast(e));
    }

    /**
     * {@inheritDoc} <!--workaround-->
     */
    @Override
    @ForceInline
    public final
    VectorMask<Float16> compare(Comparison op, long e, VectorMask<Float16> m) {
        return compare(op, broadcast(e), m);
    }



    /**
     * {@inheritDoc} <!--workaround-->
     */
    @Override public abstract
    HalffloatVector blend(Vector<Float16> v, VectorMask<Float16> m);

    /*package-private*/
    @ForceInline
    final
    <M extends VectorMask<Float16>>
    HalffloatVector
    blendTemplate(Class<M> maskType, HalffloatVector v, M m) {
        v.check(this);
        return VectorSupport.blend(
            getClass(), maskType, short.class, VECTOR_OPER_TYPE, length(),
            this, v, m,
            (v0, v1, m_) -> v0.bOp(v1, m_, (i, a, b) -> b));
    }

    /**
     * {@inheritDoc} <!--workaround-->
     */
    @Override public abstract HalffloatVector addIndex(int scale);

    /*package-private*/
    @ForceInline
    final HalffloatVector addIndexTemplate(int scale) {
        HalffloatSpecies vsp = vspecies();
        // make sure VLENGTH*scale doesn't overflow:
        vsp.checkScale(scale);
        return VectorSupport.indexVector(
            getClass(), short.class, VECTOR_OPER_TYPE, length(),
            this, scale, vsp,
            (v, scale_, s)
            -> {
                // If the platform doesn't support an INDEX
                // instruction directly, load IOTA from memory
                // and multiply.
                HalffloatVector iota = s.iota();
                short sc = (short) scale_;
                return v.add(sc == 1 ? iota : iota.mul(Float16.float16ToRawShortBits(Float16.valueOf(sc))));
            });
    }

    /**
     * Replaces selected lanes of this vector with
     * a scalar value
     * under the control of a mask.
     *
     * This is a masked lane-wise binary operation which
     * selects each lane value from one or the other input.
     *
     * The returned result is equal to the expression
     * {@code blend(broadcast(e),m)}.
     *
     * @param e the input scalar, containing the replacement lane value
     * @param m the mask controlling lane selection of the scalar
     * @return the result of blending the lane elements of this vector with
     *         the scalar value
     */
    @ForceInline
    public final HalffloatVector blend(short e,
                                            VectorMask<Float16> m) {
        return blend(broadcast(e), m);
    }

    /**
     * Replaces selected lanes of this vector with
     * a scalar value
     * under the control of a mask.
     *
     * This is a masked lane-wise binary operation which
     * selects each lane value from one or the other input.
     *
     * The returned result is equal to the expression
     * {@code blend(broadcast(e),m)}.
     *
     * @param e the input scalar, containing the replacement lane value
     * @param m the mask controlling lane selection of the scalar
     * @return the result of blending the lane elements of this vector with
     *         the scalar value
     */
    @ForceInline
    public final HalffloatVector blend(long e,
                                            VectorMask<Float16> m) {
        return blend(broadcast(e), m);
    }

    /**
     * {@inheritDoc} <!--workaround-->
     */
    @Override
    public abstract
    HalffloatVector slice(int origin, Vector<Float16> v1);

    /*package-private*/
    final
    @ForceInline
    HalffloatVector sliceTemplate(int origin, Vector<Float16> v1) {
        HalffloatVector that = (HalffloatVector) v1;
        that.check(this);
        Objects.checkIndex(origin, length() + 1);
        ShortVector iotaVector = (ShortVector) iotaShuffle().toBitsVector();
        ShortVector filter = ShortVector.broadcast((ShortVector.ShortSpecies) vspecies().asIntegral(), (short)(length() - origin));
        VectorMask<Float16> blendMask = iotaVector.compare(VectorOperators.LT, filter).cast(vspecies());
        AbstractShuffle<Float16> iota = iotaShuffle(origin, 1, true);
        return that.rearrange(iota).blend(this.rearrange(iota), blendMask);
    }

    /**
     * {@inheritDoc} <!--workaround-->
     */
    @Override
    @ForceInline
    public final
    HalffloatVector slice(int origin,
                               Vector<Float16> w,
                               VectorMask<Float16> m) {
        return broadcast(0).blend(slice(origin, w), m);
    }

    /**
     * {@inheritDoc} <!--workaround-->
     */
    @Override
    public abstract
    HalffloatVector slice(int origin);

    /*package-private*/
    final
    @ForceInline
    HalffloatVector sliceTemplate(int origin) {
        Objects.checkIndex(origin, length() + 1);
        ShortVector iotaVector = (ShortVector) iotaShuffle().toBitsVector();
        ShortVector filter = ShortVector.broadcast((ShortVector.ShortSpecies) vspecies().asIntegral(), (short)(length() - origin));
        VectorMask<Float16> blendMask = iotaVector.compare(VectorOperators.LT, filter).cast(vspecies());
        AbstractShuffle<Float16> iota = iotaShuffle(origin, 1, true);
        return vspecies().zero().blend(this.rearrange(iota), blendMask);
    }

    /**
     * {@inheritDoc} <!--workaround-->
     */
    @Override
    public abstract
    HalffloatVector unslice(int origin, Vector<Float16> w, int part);

    /*package-private*/
    final
    @ForceInline
    HalffloatVector
    unsliceTemplate(int origin, Vector<Float16> w, int part) {
        HalffloatVector that = (HalffloatVector) w;
        that.check(this);
        Objects.checkIndex(origin, length() + 1);
        ShortVector iotaVector = (ShortVector) iotaShuffle().toBitsVector();
        ShortVector filter = ShortVector.broadcast((ShortVector.ShortSpecies) vspecies().asIntegral(), (short)origin);
        VectorMask<Float16> blendMask = iotaVector.compare((part == 0) ? VectorOperators.GE : VectorOperators.LT, filter).cast(vspecies());
        AbstractShuffle<Float16> iota = iotaShuffle(-origin, 1, true);
        return that.blend(this.rearrange(iota), blendMask);
    }

    /*package-private*/
    final
    @ForceInline
    <M extends VectorMask<Float16>>
    HalffloatVector
    unsliceTemplate(Class<M> maskType, int origin, Vector<Float16> w, int part, M m) {
        HalffloatVector that = (HalffloatVector) w;
        that.check(this);
        HalffloatVector slice = that.sliceTemplate(origin, that);
        slice = slice.blendTemplate(maskType, this, m);
        return slice.unsliceTemplate(origin, w, part);
    }

    /**
     * {@inheritDoc} <!--workaround-->
     */
    @Override
    public abstract
    HalffloatVector unslice(int origin, Vector<Float16> w, int part, VectorMask<Float16> m);

    /**
     * {@inheritDoc} <!--workaround-->
     */
    @Override
    public abstract
    HalffloatVector unslice(int origin);

    /*package-private*/
    final
    @ForceInline
    HalffloatVector
    unsliceTemplate(int origin) {
        Objects.checkIndex(origin, length() + 1);
        ShortVector iotaVector = (ShortVector) iotaShuffle().toBitsVector();
        ShortVector filter = ShortVector.broadcast((ShortVector.ShortSpecies) vspecies().asIntegral(), (short)origin);
        VectorMask<Float16> blendMask = iotaVector.compare(VectorOperators.GE, filter).cast(vspecies());
        AbstractShuffle<Float16> iota = iotaShuffle(-origin, 1, true);
        return vspecies().zero().blend(this.rearrange(iota), blendMask);
    }

    private ArrayIndexOutOfBoundsException
    wrongPartForSlice(int part) {
        String msg = String.format("bad part number %d for slice operation",
                                   part);
        return new ArrayIndexOutOfBoundsException(msg);
    }

    /**
     * {@inheritDoc} <!--workaround-->
     */
    @Override
    public abstract
    HalffloatVector rearrange(VectorShuffle<Float16> shuffle);

    /*package-private*/
    @ForceInline
    final
    <S extends VectorShuffle<Float16>>
    HalffloatVector rearrangeTemplate(Class<S> shuffletype, S shuffle) {
        Objects.requireNonNull(shuffle);
        return VectorSupport.rearrangeOp(
            getClass(), shuffletype, null, short.class, VECTOR_OPER_TYPE, length(),
            this, shuffle, null,
            (v1, s_, m_) -> v1.uOp((i, a) -> {
                int ei = Integer.remainderUnsigned(s_.laneSource(i), v1.length());
                return v1.lane(ei);
            }));
    }

    /**
     * {@inheritDoc} <!--workaround-->
     */
    @Override
    public abstract
    HalffloatVector rearrange(VectorShuffle<Float16> s,
                                   VectorMask<Float16> m);

    /*package-private*/
    @ForceInline
    final
    <S extends VectorShuffle<Float16>, M extends VectorMask<Float16>>
    HalffloatVector rearrangeTemplate(Class<S> shuffletype,
                                           Class<M> masktype,
                                           S shuffle,
                                           M m) {
        Objects.requireNonNull(shuffle);
        m.check(masktype, this);
        return VectorSupport.rearrangeOp(
                   getClass(), shuffletype, masktype, short.class, VECTOR_OPER_TYPE, length(),
                   this, shuffle, m,
                   (v1, s_, m_) -> v1.uOp((i, a) -> {
                        int ei = Integer.remainderUnsigned(s_.laneSource(i), v1.length());
                        return !m_.laneIsSet(i) ? 0 : v1.lane(ei);
                   }));
    }

    /**
     * {@inheritDoc} <!--workaround-->
     */
    @Override
    public abstract
    HalffloatVector rearrange(VectorShuffle<Float16> s,
                                   Vector<Float16> v);

    /*package-private*/
    @ForceInline
    final
    <S extends VectorShuffle<Float16>>
    HalffloatVector rearrangeTemplate(Class<S> shuffletype,
                                           S shuffle,
                                           HalffloatVector v) {
        VectorMask<Float16> valid = shuffle.laneIsValid();
        HalffloatVector r0 =
            VectorSupport.rearrangeOp(
                getClass(), shuffletype, null, short.class, VECTOR_OPER_TYPE, length(),
                this, shuffle, null,
                (v0, s_, m_) -> v0.uOp((i, a) -> {
                    int ei = Integer.remainderUnsigned(s_.laneSource(i), v0.length());
                    return v0.lane(ei);
                }));
        HalffloatVector r1 =
            VectorSupport.rearrangeOp(
                getClass(), shuffletype, null, short.class, VECTOR_OPER_TYPE, length(),
                v, shuffle, null,
                (v1, s_, m_) -> v1.uOp((i, a) -> {
                    int ei = Integer.remainderUnsigned(s_.laneSource(i), v1.length());
                    return v1.lane(ei);
                }));
        return r1.blend(r0, valid);
    }

    @Override
    @ForceInline
    final <F> VectorShuffle<F> bitsToShuffle0(AbstractSpecies<F> dsp) {
        throw new AssertionError();
    }

    @ForceInline
    final <F>
    VectorShuffle<F> toShuffle(AbstractSpecies<F> dsp, boolean wrap) {
        assert(dsp.elementSize() == vspecies().elementSize());
        ShortVector idx = convert(VectorOperators.H2S, 0).reinterpretAsShorts();
        ShortVector wrapped = idx.lanewise(VectorOperators.AND, length() - 1);
        if (!wrap) {
            ShortVector wrappedEx = wrapped.lanewise(VectorOperators.SUB, length());
            VectorMask<Short> inBound = wrapped.compare(VectorOperators.EQ, idx);
            wrapped = wrappedEx.blend(wrapped, inBound);
        }
        return wrapped.bitsToShuffle(dsp);
    }

    /**
     * {@inheritDoc} <!--workaround-->
     * @since 19
     */
    @Override
    public abstract
    HalffloatVector compress(VectorMask<Float16> m);

    /*package-private*/
    @ForceInline
    final
    <M extends AbstractMask<Float16>>
    HalffloatVector compressTemplate(Class<M> masktype, M m) {
      m.check(masktype, this);
      return (HalffloatVector) VectorSupport.compressExpandOp(VectorSupport.VECTOR_OP_COMPRESS, getClass(), masktype,
                                                        short.class, VECTOR_OPER_TYPE, length(), this, m,
                                                        (v1, m1) -> compressHelper(v1, m1));
    }

    /**
     * {@inheritDoc} <!--workaround-->
     * @since 19
     */
    @Override
    public abstract
    HalffloatVector expand(VectorMask<Float16> m);

    /*package-private*/
    @ForceInline
    final
    <M extends AbstractMask<Float16>>
    HalffloatVector expandTemplate(Class<M> masktype, M m) {
      m.check(masktype, this);
      return (HalffloatVector) VectorSupport.compressExpandOp(VectorSupport.VECTOR_OP_EXPAND, getClass(), masktype,
                                                        short.class, VECTOR_OPER_TYPE, length(), this, m,
                                                        (v1, m1) -> expandHelper(v1, m1));
    }


    /**
     * {@inheritDoc} <!--workaround-->
     */
    @Override
    public abstract
    HalffloatVector selectFrom(Vector<Float16> v);

    /*package-private*/
    @ForceInline
    final HalffloatVector selectFromTemplate(HalffloatVector v) {
        return (HalffloatVector)VectorSupport.selectFromOp(getClass(), null, short.class, VECTOR_OPER_TYPE,
                                                        length(), this, v, null,
                                                        (v1, v2, _m) ->
                                                         v2.rearrange(v1.toShuffle()));
    }

    /**
     * {@inheritDoc} <!--workaround-->
     */
    @Override
    public abstract
    HalffloatVector selectFrom(Vector<Float16> s, VectorMask<Float16> m);

    /*package-private*/
    @ForceInline
    final
    <M extends VectorMask<Float16>>
    HalffloatVector selectFromTemplate(HalffloatVector v,
                                            Class<M> masktype, M m) {
        m.check(masktype, this);
        return (HalffloatVector)VectorSupport.selectFromOp(getClass(), masktype, short.class, VECTOR_OPER_TYPE,
                                                        length(), this, v, m,
                                                        (v1, v2, _m) ->
                                                         v2.rearrange(v1.toShuffle(), _m));
    }


    /**
     * {@inheritDoc} <!--workaround-->
     */
    @Override
    public abstract
    HalffloatVector selectFrom(Vector<Float16> v1, Vector<Float16> v2);


    /*package-private*/
    @ForceInline
    final HalffloatVector selectFromTemplate(HalffloatVector v1, HalffloatVector v2) {
        return VectorSupport.selectFromTwoVectorOp(getClass(), short.class, VECTOR_OPER_TYPE, length(), this, v1, v2,
                                                   (vec1, vec2, vec3) -> selectFromTwoVectorHelper(vec1, vec2, vec3));
    }

    /// Ternary operations


    /**
     * Multiplies this vector by a second input vector, and sums
     * the result with a third.
     *
     * Extended precision is used for the intermediate result,
     * avoiding possible loss of precision from rounding once
     * for each of the two operations.
     * The result is numerically close to {@code this.mul(b).add(c)},
     * and is typically closer to the true mathematical result.
     *
     * This is a lane-wise ternary operation which applies an operation
     * conforming to the specification of
     * {@link Math#fma(short,short,short) Math.fma(a,b,c)}
     * to each lane.
     *
     * This method is also equivalent to the expression
     * {@link #lanewise(VectorOperators.Ternary,Vector,Vector)
     *    lanewise}{@code (}{@link VectorOperators#FMA
     *    FMA}{@code , b, c)}.
     *
     * @param b the second input vector, supplying multiplier values
     * @param c the third input vector, supplying addend values
     * @return the product of this vector and the second input vector
     *         summed with the third input vector, using extended precision
     *         for the intermediate result
     * @see #fma(short,short)
     * @see VectorOperators#FMA
     * @see #lanewise(VectorOperators.Ternary,Vector,Vector,VectorMask)
     */
    @ForceInline
    public final
    HalffloatVector fma(Vector<Float16> b, Vector<Float16> c) {
        return lanewise(FMA, b, c);
    }

    /**
     * Multiplies this vector by a scalar multiplier, and sums
     * the result with a scalar addend.
     *
     * Extended precision is used for the intermediate result,
     * avoiding possible loss of precision from rounding once
     * for each of the two operations.
     * The result is numerically close to {@code this.mul(b).add(c)},
     * and is typically closer to the true mathematical result.
     *
     * This is a lane-wise ternary operation which applies an operation
     * conforming to the specification of
     * {@link Math#fma(short,short,short) Math.fma(a,b,c)}
     * to each lane.
     *
     * This method is also equivalent to the expression
     * {@link #lanewise(VectorOperators.Ternary,Vector,Vector)
     *    lanewise}{@code (}{@link VectorOperators#FMA
     *    FMA}{@code , b, c)}.
     *
     * @param b the scalar multiplier
     * @param c the scalar addend
     * @return the product of this vector and the scalar multiplier
     *         summed with scalar addend, using extended precision
     *         for the intermediate result
     * @see #fma(Vector,Vector)
     * @see VectorOperators#FMA
     * @see #lanewise(VectorOperators.Ternary,short,short,VectorMask)
     */
    @ForceInline
    public final
    HalffloatVector fma(short b, short c) {
        return lanewise(FMA, b, c);
    }

    // Don't bother with (Vector,short) and (short,Vector) overloadings.

    // Type specific horizontal reductions

    /**
     * Returns a value accumulated from all the lanes of this vector.
     *
     * This is an associative cross-lane reduction operation which
     * applies the specified operation to all the lane elements.
     * <p>
     * A few reduction operations do not support arbitrary reordering
     * of their operands, yet are included here because of their
     * usefulness.
     * <ul>
     * <li>
     * In the case of {@code FIRST_NONZERO}, the reduction returns
     * the value from the lowest-numbered non-zero lane.
     * (As with {@code MAX} and {@code MIN}, floating point negative
     * zero {@code -0.0} is treated as a value distinct from
     * the default value, positive zero. So a first-nonzero lane reduction
     * might return {@code -0.0} even in the presence of non-zero
     * lane values.)
     * <li>
     * In the case of {@code ADD} and {@code MUL}, the
     * precise result will reflect the choice of an arbitrary order
     * of operations, which may even vary over time.
     * For further details see the section
     * <a href="VectorOperators.html#fp_assoc">Operations on floating point vectors</a>.
     * <li>
     * All other reduction operations are fully commutative and
     * associative.  The implementation can choose any order of
     * processing, yet it will always produce the same result.
     * </ul>
     *
     * @param op the operation used to combine lane values
     * @return the accumulated result
     * @throws UnsupportedOperationException if this vector does
     *         not support the requested operation
     * @see #reduceLanes(VectorOperators.Associative,VectorMask)
     * @see #add(Vector)
     * @see #mul(Vector)
     * @see #min(Vector)
     * @see #max(Vector)
     * @see VectorOperators#FIRST_NONZERO
     */
    public abstract short reduceLanes(VectorOperators.Associative op);

    /**
     * Returns a value accumulated from selected lanes of this vector,
     * controlled by a mask.
     *
     * This is an associative cross-lane reduction operation which
     * applies the specified operation to the selected lane elements.
     * <p>
     * If no elements are selected, an operation-specific identity
     * value is returned.
     * <ul>
     * <li>
     * If the operation is
     *  {@code ADD}
     * or {@code FIRST_NONZERO},
     * then the identity value is positive zero, the default {@code short} value.
     * <li>
     * If the operation is {@code MUL},
     * then the identity value is one.
     * <li>
     * If the operation is {@code MAX},
     * then the identity value is {@code Float16.NEGATIVE_INFINITY}.
     * <li>
     * If the operation is {@code MIN},
     * then the identity value is {@code Float16.POSITIVE_INFINITY}.
     * </ul>
     * <p>
     * A few reduction operations do not support arbitrary reordering
     * of their operands, yet are included here because of their
     * usefulness.
     * <ul>
     * <li>
     * In the case of {@code FIRST_NONZERO}, the reduction returns
     * the value from the lowest-numbered non-zero lane.
     * (As with {@code MAX} and {@code MIN}, floating point negative
     * zero {@code -0.0} is treated as a value distinct from
     * the default value, positive zero. So a first-nonzero lane reduction
     * might return {@code -0.0} even in the presence of non-zero
     * lane values.)
     * <li>
     * In the case of {@code ADD} and {@code MUL}, the
     * precise result will reflect the choice of an arbitrary order
     * of operations, which may even vary over time.
     * For further details see the section
     * <a href="VectorOperators.html#fp_assoc">Operations on floating point vectors</a>.
     * <li>
     * All other reduction operations are fully commutative and
     * associative.  The implementation can choose any order of
     * processing, yet it will always produce the same result.
     * </ul>
     *
     * @param op the operation used to combine lane values
     * @param m the mask controlling lane selection
     * @return the reduced result accumulated from the selected lane values
     * @throws UnsupportedOperationException if this vector does
     *         not support the requested operation
     * @see #reduceLanes(VectorOperators.Associative)
     */
    public abstract short reduceLanes(VectorOperators.Associative op,
                                       VectorMask<Float16> m);

    /*package-private*/
    @ForceInline
    final
    short reduceLanesTemplate(VectorOperators.Associative op,
                               Class<? extends VectorMask<Float16>> maskClass,
                               VectorMask<Float16> m) {
        m.check(maskClass, this);
        if (op == FIRST_NONZERO) {
            // FIXME:  The JIT should handle this.
            HalffloatVector v = broadcast((short) 0).blend(this, m);
            return v.reduceLanesTemplate(op);
        }
        int opc = opCode(op);
        return fromBits(VectorSupport.reductionCoerced(
            opc, getClass(), maskClass, short.class, VECTOR_OPER_TYPE, length(),
            this, m,
            REDUCE_IMPL.find(op, opc, HalffloatVector::reductionOperations)));
    }

    /*package-private*/
    @ForceInline
    final
    short reduceLanesTemplate(VectorOperators.Associative op) {
        if (op == FIRST_NONZERO) {
            // FIXME:  The JIT should handle this.
            VectorMask<Short> thisNZ
                = this.viewAsIntegralLanes().compare(NE, (short) 0);
            int ft = thisNZ.firstTrue();
            return ft < length() ? this.lane(ft) : (short) 0;
        }
        int opc = opCode(op);
        return fromBits(VectorSupport.reductionCoerced(
            opc, getClass(), null, short.class, VECTOR_OPER_TYPE, length(),
            this, null,
            REDUCE_IMPL.find(op, opc, HalffloatVector::reductionOperations)));
    }

    private static final
    ImplCache<Associative, ReductionOperation<HalffloatVector, VectorMask<Float16>>>
        REDUCE_IMPL = new ImplCache<>(Associative.class, HalffloatVector.class);

    private static ReductionOperation<HalffloatVector, VectorMask<Float16>> reductionOperations(int opc_) {
        switch (opc_) {
            case VECTOR_OP_ADD: return (v, m) ->
                    toBits(v.rOp((short)0, m, (i, a, b) -> Float16.float16ToRawShortBits(Float16.add(Float16.shortBitsToFloat16(a), Float16.shortBitsToFloat16(b)))));
            case VECTOR_OP_MUL: return (v, m) ->
                    toBits(v.rOp((short)0, m, (i, a, b) -> Float16.float16ToRawShortBits(Float16.multiply(Float16.shortBitsToFloat16(a), Float16.shortBitsToFloat16(b)))));
            case VECTOR_OP_MIN: return (v, m) ->
                    toBits(v.rOp((short)0, m, (i, a, b) -> Float16.float16ToRawShortBits(Float16.min(Float16.shortBitsToFloat16(a), Float16.shortBitsToFloat16(b)))));
            case VECTOR_OP_MAX: return (v, m) ->
                    toBits(v.rOp((short)0, m, (i, a, b) -> Float16.float16ToRawShortBits(Float16.max(Float16.shortBitsToFloat16(a), Float16.shortBitsToFloat16(b)))));
            default: return null;
        }
    }

    private static final short MIN_OR_INF = Float16.float16ToRawShortBits(Float16.NEGATIVE_INFINITY);
    private static final short MAX_OR_INF = Float16.float16ToRawShortBits(Float16.POSITIVE_INFINITY);

    public @Override abstract long reduceLanesToLong(VectorOperators.Associative op);
    public @Override abstract long reduceLanesToLong(VectorOperators.Associative op,
                                                     VectorMask<Float16> m);

    // Type specific accessors

    /**
     * Gets the lane element at lane index {@code i}
     *
     * @param i the lane index
     * @return the lane element at lane index {@code i}
     * @throws IllegalArgumentException if the index is out of range
     * ({@code < 0 || >= length()})
     */
    public abstract short lane(int i);

    /**
     * Replaces the lane element of this vector at lane index {@code i} with
     * value {@code e}.
     *
     * This is a cross-lane operation and behaves as if it returns the result
     * of blending this vector with an input vector that is the result of
     * broadcasting {@code e} and a mask that has only one lane set at lane
     * index {@code i}.
     *
     * @param i the lane index of the lane element to be replaced
     * @param e the value to be placed
     * @return the result of replacing the lane element of this vector at lane
     * index {@code i} with value {@code e}.
     * @throws IllegalArgumentException if the index is out of range
     * ({@code < 0 || >= length()})
     */
    public abstract HalffloatVector withLane(int i, short e);

    // Memory load operations

    /**
     * Returns an array of type {@code short[]}
     * containing all the lane values.
     * The array length is the same as the vector length.
     * The array elements are stored in lane order.
     * <p>
     * This method behaves as if it stores
     * this vector into an allocated array
     * (using {@link #intoArray(short[], int) intoArray})
     * and returns the array as follows:
     * <pre>{@code
     *   short[] a = new short[this.length()];
     *   this.intoArray(a, 0);
     *   return a;
     * }</pre>
     *
     * @return an array containing the lane values of this vector
     */
    @ForceInline
    @Override
    public final short[] toArray() {
        short[] a = new short[vspecies().laneCount()];
        intoArray(a, 0);
        return a;
    }

    /** {@inheritDoc} <!--workaround-->
     */
    @ForceInline
    @Override
    public final int[] toIntArray() {
        short[] a = toArray();
        int[] res = new int[a.length];
        for (int i = 0; i < a.length; i++) {
            short e = a[i];
            res[i] = (int) HalffloatSpecies.toIntegralChecked(e, true);
        }
        return res;
    }

    /** {@inheritDoc} <!--workaround-->
     */
    @ForceInline
    @Override
    public final long[] toLongArray() {
        short[] a = toArray();
        long[] res = new long[a.length];
        for (int i = 0; i < a.length; i++) {
            short e = Float16.shortBitsToFloat16(a[i]).shortValue();
            res[i] = HalffloatSpecies.toIntegralChecked(e, false);
        }
        return res;
    }

    /** {@inheritDoc} <!--workaround-->
     * @implNote
     * When this method is used on vectors
     * of type {@code HalffloatVector},
     * there will be no loss of precision.
     */
    @ForceInline
    @Override
    public final double[] toDoubleArray() {
        short[] a = toArray();
        double[] res = new double[a.length];
        for (int i = 0; i < a.length; i++) {
            res[i] = (double) Float16.shortBitsToFloat16(a[i]).doubleValue();
        }
        return res;
    }

    /**
     * Loads a vector from an array of type {@code short[]}
     * starting at an offset.
     * For each vector lane, where {@code N} is the vector lane index, the
     * array element at index {@code offset + N} is placed into the
     * resulting vector at lane index {@code N}.
     *
     * @param species species of desired vector
     * @param a the array
     * @param offset the offset into the array
     * @return the vector loaded from an array
     * @throws IndexOutOfBoundsException
     *         if {@code offset+N < 0} or {@code offset+N >= a.length}
     *         for any lane {@code N} in the vector
     */
    @ForceInline
    public static
    HalffloatVector fromArray(VectorSpecies<Float16> species,
                                   short[] a, int offset) {
        offset = checkFromIndexSize(offset, species.length(), a.length);
        HalffloatSpecies vsp = (HalffloatSpecies) species;
        return vsp.dummyVector().fromArray0(a, offset);
    }

    /**
     * Loads a vector from an array of type {@code short[]}
     * starting at an offset and using a mask.
     * Lanes where the mask is unset are filled with the default
     * value of {@code short} (positive zero).
     * For each vector lane, where {@code N} is the vector lane index,
     * if the mask lane at index {@code N} is set then the array element at
     * index {@code offset + N} is placed into the resulting vector at lane index
     * {@code N}, otherwise the default element value is placed into the
     * resulting vector at lane index {@code N}.
     *
     * @param species species of desired vector
     * @param a the array
     * @param offset the offset into the array
     * @param m the mask controlling lane selection
     * @return the vector loaded from an array
     * @throws IndexOutOfBoundsException
     *         if {@code offset+N < 0} or {@code offset+N >= a.length}
     *         for any lane {@code N} in the vector
     *         where the mask is set
     */
    @ForceInline
    public static
    HalffloatVector fromArray(VectorSpecies<Float16> species,
                                   short[] a, int offset,
                                   VectorMask<Float16> m) {
        HalffloatSpecies vsp = (HalffloatSpecies) species;
        if (VectorIntrinsics.indexInRange(offset, vsp.length(), a.length)) {
            return vsp.dummyVector().fromArray0(a, offset, m, OFFSET_IN_RANGE);
        }

        ((AbstractMask<Float16>)m)
            .checkIndexByLane(offset, a.length, vsp.iota(), 1);
        return vsp.dummyVector().fromArray0(a, offset, m, OFFSET_OUT_OF_RANGE);
    }

    /**
     * Gathers a new vector composed of elements from an array of type
     * {@code short[]},
     * using indexes obtained by adding a fixed {@code offset} to a
     * series of secondary offsets from an <em>index map</em>.
     * The index map is a contiguous sequence of {@code VLENGTH}
     * elements in a second array of {@code int}s, starting at a given
     * {@code mapOffset}.
     * <p>
     * For each vector lane, where {@code N} is the vector lane index,
     * the lane is loaded from the array
     * element {@code a[f(N)]}, where {@code f(N)} is the
     * index mapping expression
     * {@code offset + indexMap[mapOffset + N]]}.
     *
     * @param species species of desired vector
     * @param a the array
     * @param offset the offset into the array, may be negative if relative
     * indexes in the index map compensate to produce a value within the
     * array bounds
     * @param indexMap the index map
     * @param mapOffset the offset into the index map
     * @return the vector loaded from the indexed elements of the array
     * @throws IndexOutOfBoundsException
     *         if {@code mapOffset+N < 0}
     *         or if {@code mapOffset+N >= indexMap.length},
     *         or if {@code f(N)=offset+indexMap[mapOffset+N]}
     *         is an invalid index into {@code a},
     *         for any lane {@code N} in the vector
     * @see HalffloatVector#toIntArray()
     */
    @ForceInline
    public static
    HalffloatVector fromArray(VectorSpecies<Float16> species,
                                   short[] a, int offset,
                                   int[] indexMap, int mapOffset) {
        HalffloatSpecies vsp = (HalffloatSpecies) species;
        IntVector.IntSpecies isp = IntVector.species(vsp.indexShape());
        Objects.requireNonNull(a);
        Objects.requireNonNull(indexMap);
        Class<? extends HalffloatVector> vectorType = vsp.vectorType();


        // Constant folding should sweep out following conditonal logic.
        VectorSpecies<Integer> lsp;
        if (isp.length() > IntVector.SPECIES_PREFERRED.length()) {
            lsp = IntVector.SPECIES_PREFERRED;
        } else {
            lsp = isp;
        }

        // Check indices are within array bounds.
        IntVector vix0 = IntVector.fromArray(lsp, indexMap, mapOffset).add(offset);
        VectorIntrinsics.checkIndex(vix0, a.length);

        int vlen = vsp.length();
        int idx_vlen = lsp.length();
        IntVector vix1 = null;
        if (vlen >= idx_vlen * 2) {
            vix1 = IntVector.fromArray(lsp, indexMap, mapOffset + idx_vlen).add(offset);
            VectorIntrinsics.checkIndex(vix1, a.length);
        }

        return VectorSupport.loadWithMap(
            vectorType, null, short.class, VECTOR_OPER_TYPE, vsp.laneCount(),
            lsp.vectorType(), lsp.length(),
            a, ARRAY_BASE, vix0, vix1, null, null, null,
            a, offset, indexMap, mapOffset, vsp,
            (c, idx, iMap, idy, s, vm) ->
            s.vOp(n -> c[idx + iMap[idy+n]]));
    }

    /**
     * Gathers a new vector composed of elements from an array of type
     * {@code short[]},
     * under the control of a mask, and
     * using indexes obtained by adding a fixed {@code offset} to a
     * series of secondary offsets from an <em>index map</em>.
     * The index map is a contiguous sequence of {@code VLENGTH}
     * elements in a second array of {@code int}s, starting at a given
     * {@code mapOffset}.
     * <p>
     * For each vector lane, where {@code N} is the vector lane index,
     * if the lane is set in the mask,
     * the lane is loaded from the array
     * element {@code a[f(N)]}, where {@code f(N)} is the
     * index mapping expression
     * {@code offset + indexMap[mapOffset + N]]}.
     * Unset lanes in the resulting vector are set to zero.
     *
     * @param species species of desired vector
     * @param a the array
     * @param offset the offset into the array, may be negative if relative
     * indexes in the index map compensate to produce a value within the
     * array bounds
     * @param indexMap the index map
     * @param mapOffset the offset into the index map
     * @param m the mask controlling lane selection
     * @return the vector loaded from the indexed elements of the array
     * @throws IndexOutOfBoundsException
     *         if {@code mapOffset+N < 0}
     *         or if {@code mapOffset+N >= indexMap.length},
     *         or if {@code f(N)=offset+indexMap[mapOffset+N]}
     *         is an invalid index into {@code a},
     *         for any lane {@code N} in the vector
     *         where the mask is set
     * @see HalffloatVector#toIntArray()
     */
    @ForceInline
    public static
    HalffloatVector fromArray(VectorSpecies<Float16> species,
                                   short[] a, int offset,
                                   int[] indexMap, int mapOffset,
                                   VectorMask<Float16> m) {
        if (m.allTrue()) {
            return fromArray(species, a, offset, indexMap, mapOffset);
        }
        else {
            HalffloatSpecies vsp = (HalffloatSpecies) species;
            return vsp.dummyVector().fromArray0(a, offset, indexMap, mapOffset, m);
        }
    }

    /**
     * Loads a vector from an array of type {@code char[]}
     * starting at an offset.
     * For each vector lane, where {@code N} is the vector lane index, the
     * array element at index {@code offset + N}
     * is first cast to a {@code short} value and then
     * placed into the resulting vector at lane index {@code N}.
     *
     * @param species species of desired vector
     * @param a the array
     * @param offset the offset into the array
     * @return the vector loaded from an array
     * @throws IndexOutOfBoundsException
     *         if {@code offset+N < 0} or {@code offset+N >= a.length}
     *         for any lane {@code N} in the vector
     */
    @ForceInline
    public static
    HalffloatVector fromCharArray(VectorSpecies<Float16> species,
                                       char[] a, int offset) {
        offset = checkFromIndexSize(offset, species.length(), a.length);
        HalffloatSpecies vsp = (HalffloatSpecies) species;
        return vsp.dummyVector().fromCharArray0(a, offset);
    }

    /**
     * Loads a vector from an array of type {@code char[]}
     * starting at an offset and using a mask.
     * Lanes where the mask is unset are filled with the default
     * value of {@code short} (positive zero).
     * For each vector lane, where {@code N} is the vector lane index,
     * if the mask lane at index {@code N} is set then the array element at
     * index {@code offset + N}
     * is first cast to a {@code short} value and then
     * placed into the resulting vector at lane index
     * {@code N}, otherwise the default element value is placed into the
     * resulting vector at lane index {@code N}.
     *
     * @param species species of desired vector
     * @param a the array
     * @param offset the offset into the array
     * @param m the mask controlling lane selection
     * @return the vector loaded from an array
     * @throws IndexOutOfBoundsException
     *         if {@code offset+N < 0} or {@code offset+N >= a.length}
     *         for any lane {@code N} in the vector
     *         where the mask is set
     */
    @ForceInline
    public static
    HalffloatVector fromCharArray(VectorSpecies<Float16> species,
                                       char[] a, int offset,
                                       VectorMask<Float16> m) {
        HalffloatSpecies vsp = (HalffloatSpecies) species;
        if (VectorIntrinsics.indexInRange(offset, vsp.length(), a.length)) {
            return vsp.dummyVector().fromCharArray0(a, offset, m, OFFSET_IN_RANGE);
        }

        ((AbstractMask<Float16>)m)
            .checkIndexByLane(offset, a.length, vsp.iota(), 1);
        return vsp.dummyVector().fromCharArray0(a, offset, m, OFFSET_OUT_OF_RANGE);
    }

    /**
     * Gathers a new vector composed of elements from an array of type
     * {@code char[]},
     * using indexes obtained by adding a fixed {@code offset} to a
     * series of secondary offsets from an <em>index map</em>.
     * The index map is a contiguous sequence of {@code VLENGTH}
     * elements in a second array of {@code int}s, starting at a given
     * {@code mapOffset}.
     * <p>
     * For each vector lane, where {@code N} is the vector lane index,
     * the lane is loaded from the expression
     * {@code (short) a[f(N)]}, where {@code f(N)} is the
     * index mapping expression
     * {@code offset + indexMap[mapOffset + N]]}.
     *
     * @param species species of desired vector
     * @param a the array
     * @param offset the offset into the array, may be negative if relative
     * indexes in the index map compensate to produce a value within the
     * array bounds
     * @param indexMap the index map
     * @param mapOffset the offset into the index map
     * @return the vector loaded from the indexed elements of the array
     * @throws IndexOutOfBoundsException
     *         if {@code mapOffset+N < 0}
     *         or if {@code mapOffset+N >= indexMap.length},
     *         or if {@code f(N)=offset+indexMap[mapOffset+N]}
     *         is an invalid index into {@code a},
     *         for any lane {@code N} in the vector
     * @see HalffloatVector#toIntArray()
     */
    @ForceInline
    public static
    HalffloatVector fromCharArray(VectorSpecies<Float16> species,
                                       char[] a, int offset,
                                       int[] indexMap, int mapOffset) {
        // FIXME: optimize
        HalffloatSpecies vsp = (HalffloatSpecies) species;
        return vsp.vOp(n -> (short) a[offset + indexMap[mapOffset + n]]);
    }

    /**
     * Gathers a new vector composed of elements from an array of type
     * {@code char[]},
     * under the control of a mask, and
     * using indexes obtained by adding a fixed {@code offset} to a
     * series of secondary offsets from an <em>index map</em>.
     * The index map is a contiguous sequence of {@code VLENGTH}
     * elements in a second array of {@code int}s, starting at a given
     * {@code mapOffset}.
     * <p>
     * For each vector lane, where {@code N} is the vector lane index,
     * if the lane is set in the mask,
     * the lane is loaded from the expression
     * {@code (short) a[f(N)]}, where {@code f(N)} is the
     * index mapping expression
     * {@code offset + indexMap[mapOffset + N]]}.
     * Unset lanes in the resulting vector are set to zero.
     *
     * @param species species of desired vector
     * @param a the array
     * @param offset the offset into the array, may be negative if relative
     * indexes in the index map compensate to produce a value within the
     * array bounds
     * @param indexMap the index map
     * @param mapOffset the offset into the index map
     * @param m the mask controlling lane selection
     * @return the vector loaded from the indexed elements of the array
     * @throws IndexOutOfBoundsException
     *         if {@code mapOffset+N < 0}
     *         or if {@code mapOffset+N >= indexMap.length},
     *         or if {@code f(N)=offset+indexMap[mapOffset+N]}
     *         is an invalid index into {@code a},
     *         for any lane {@code N} in the vector
     *         where the mask is set
     * @see HalffloatVector#toIntArray()
     */
    @ForceInline
    public static
    HalffloatVector fromCharArray(VectorSpecies<Float16> species,
                                       char[] a, int offset,
                                       int[] indexMap, int mapOffset,
                                       VectorMask<Float16> m) {
        // FIXME: optimize
        HalffloatSpecies vsp = (HalffloatSpecies) species;
        return vsp.vOp(m, n -> (short) a[offset + indexMap[mapOffset + n]]);
    }


    /**
     * Loads a vector from a {@linkplain MemorySegment memory segment}
     * starting at an offset into the memory segment.
     * Bytes are composed into primitive lane elements according
     * to the specified byte order.
     * The vector is arranged into lanes according to
     * <a href="Vector.html#lane-order">memory ordering</a>.
     * <p>
     * This method behaves as if it returns the result of calling
     * {@link #fromMemorySegment(VectorSpecies,MemorySegment,long,ByteOrder,VectorMask)
     * fromMemorySegment()} as follows:
     * <pre>{@code
     * var m = species.maskAll(true);
     * return fromMemorySegment(species, ms, offset, bo, m);
     * }</pre>
     *
     * @param species species of desired vector
     * @param ms the memory segment
     * @param offset the offset into the memory segment
     * @param bo the intended byte order
     * @return a vector loaded from the memory segment
     * @throws IndexOutOfBoundsException
     *         if {@code offset+N*2 < 0}
     *         or {@code offset+N*2 >= ms.byteSize()}
     *         for any lane {@code N} in the vector
     * @throws IllegalStateException if the memory segment's session is not alive,
     *         or if access occurs from a thread other than the thread owning the session.
     * @since 19
     */
    @ForceInline
    public static
    HalffloatVector fromMemorySegment(VectorSpecies<Float16> species,
                                           MemorySegment ms, long offset,
                                           ByteOrder bo) {
        offset = checkFromIndexSize(offset, species.vectorByteSize(), ms.byteSize());
        HalffloatSpecies vsp = (HalffloatSpecies) species;
        return vsp.dummyVector().fromMemorySegment0(ms, offset).maybeSwap(bo);
    }

    /**
     * Loads a vector from a {@linkplain MemorySegment memory segment}
     * starting at an offset into the memory segment
     * and using a mask.
     * Lanes where the mask is unset are filled with the default
     * value of {@code short} (positive zero).
     * Bytes are composed into primitive lane elements according
     * to the specified byte order.
     * The vector is arranged into lanes according to
     * <a href="Vector.html#lane-order">memory ordering</a>.
     * <p>
     * The following pseudocode illustrates the behavior:
     * <pre>{@code
     * var slice = ms.asSlice(offset);
     * short[] ar = new short[species.length()];
     * for (int n = 0; n < ar.length; n++) {
     *     if (m.laneIsSet(n)) {
     *         ar[n] = slice.getAtIndex(ValuaLayout.JAVA_SHORT.withByteAlignment(1), n);
     *     }
     * }
     * HalffloatVector r = HalffloatVector.fromArray(species, ar, 0);
     * }</pre>
     * @implNote
     * This operation is likely to be more efficient if
     * the specified byte order is the same as
     * {@linkplain ByteOrder#nativeOrder()
     * the platform native order},
     * since this method will not need to reorder
     * the bytes of lane values.
     *
     * @param species species of desired vector
     * @param ms the memory segment
     * @param offset the offset into the memory segment
     * @param bo the intended byte order
     * @param m the mask controlling lane selection
     * @return a vector loaded from the memory segment
     * @throws IndexOutOfBoundsException
     *         if {@code offset+N*2 < 0}
     *         or {@code offset+N*2 >= ms.byteSize()}
     *         for any lane {@code N} in the vector
     *         where the mask is set
     * @throws IllegalStateException if the memory segment's session is not alive,
     *         or if access occurs from a thread other than the thread owning the session.
     * @since 19
     */
    @ForceInline
    public static
    HalffloatVector fromMemorySegment(VectorSpecies<Float16> species,
                                           MemorySegment ms, long offset,
                                           ByteOrder bo,
                                           VectorMask<Float16> m) {
        HalffloatSpecies vsp = (HalffloatSpecies) species;
        if (VectorIntrinsics.indexInRange(offset, vsp.vectorByteSize(), ms.byteSize())) {
            return vsp.dummyVector().fromMemorySegment0(ms, offset, m, OFFSET_IN_RANGE).maybeSwap(bo);
        }

        ((AbstractMask<Float16>)m)
            .checkIndexByLane(offset, ms.byteSize(), vsp.iota(), 2);
        return vsp.dummyVector().fromMemorySegment0(ms, offset, m, OFFSET_OUT_OF_RANGE).maybeSwap(bo);
    }

    // Memory store operations

    /**
     * Stores this vector into an array of type {@code short[]}
     * starting at an offset.
     * <p>
     * For each vector lane, where {@code N} is the vector lane index,
     * the lane element at index {@code N} is stored into the array
     * element {@code a[offset+N]}.
     *
     * @param a the array, of type {@code short[]}
     * @param offset the offset into the array
     * @throws IndexOutOfBoundsException
     *         if {@code offset+N < 0} or {@code offset+N >= a.length}
     *         for any lane {@code N} in the vector
     */
    @ForceInline
    public final
    void intoArray(short[] a, int offset) {
        offset = checkFromIndexSize(offset, length(), a.length);
        HalffloatSpecies vsp = vspecies();
        VectorSupport.store(
            vsp.vectorType(), vsp.carrierType(), VECTOR_OPER_TYPE, vsp.laneCount(),
            a, arrayAddress(a, offset), false,
            this,
            a, offset,
            (arr, off, v)
            -> v.stOp(arr, (int) off,
                      (arr_, off_, i, e) -> arr_[off_ + i] = e));
    }

    /**
     * Stores this vector into an array of type {@code short[]}
     * starting at offset and using a mask.
     * <p>
     * For each vector lane, where {@code N} is the vector lane index,
     * the lane element at index {@code N} is stored into the array
     * element {@code a[offset+N]}.
     * If the mask lane at {@code N} is unset then the corresponding
     * array element {@code a[offset+N]} is left unchanged.
     * <p>
     * Array range checking is done for lanes where the mask is set.
     * Lanes where the mask is unset are not stored and do not need
     * to correspond to legitimate elements of {@code a}.
     * That is, unset lanes may correspond to array indexes less than
     * zero or beyond the end of the array.
     *
     * @param a the array, of type {@code short[]}
     * @param offset the offset into the array
     * @param m the mask controlling lane storage
     * @throws IndexOutOfBoundsException
     *         if {@code offset+N < 0} or {@code offset+N >= a.length}
     *         for any lane {@code N} in the vector
     *         where the mask is set
     */
    @ForceInline
    public final
    void intoArray(short[] a, int offset,
                   VectorMask<Float16> m) {
        if (m.allTrue()) {
            intoArray(a, offset);
        } else {
            HalffloatSpecies vsp = vspecies();
            if (!VectorIntrinsics.indexInRange(offset, vsp.length(), a.length)) {
                ((AbstractMask<Float16>)m)
                    .checkIndexByLane(offset, a.length, vsp.iota(), 1);
            }
            intoArray0(a, offset, m);
        }
    }

    /**
     * Scatters this vector into an array of type {@code short[]}
     * using indexes obtained by adding a fixed {@code offset} to a
     * series of secondary offsets from an <em>index map</em>.
     * The index map is a contiguous sequence of {@code VLENGTH}
     * elements in a second array of {@code int}s, starting at a given
     * {@code mapOffset}.
     * <p>
     * For each vector lane, where {@code N} is the vector lane index,
     * the lane element at index {@code N} is stored into the array
     * element {@code a[f(N)]}, where {@code f(N)} is the
     * index mapping expression
     * {@code offset + indexMap[mapOffset + N]]}.
     *
     * @param a the array
     * @param offset an offset to combine with the index map offsets
     * @param indexMap the index map
     * @param mapOffset the offset into the index map
     * @throws IndexOutOfBoundsException
     *         if {@code mapOffset+N < 0}
     *         or if {@code mapOffset+N >= indexMap.length},
     *         or if {@code f(N)=offset+indexMap[mapOffset+N]}
     *         is an invalid index into {@code a},
     *         for any lane {@code N} in the vector
     * @see HalffloatVector#toIntArray()
     */
    @ForceInline
    public final
    void intoArray(short[] a, int offset,
                   int[] indexMap, int mapOffset) {
        stOp(a, offset,
             (arr, off, i, e) -> {
                 int j = indexMap[mapOffset + i];
                 arr[off + j] = e;
             });
    }

    /**
     * Scatters this vector into an array of type {@code short[]},
     * under the control of a mask, and
     * using indexes obtained by adding a fixed {@code offset} to a
     * series of secondary offsets from an <em>index map</em>.
     * The index map is a contiguous sequence of {@code VLENGTH}
     * elements in a second array of {@code int}s, starting at a given
     * {@code mapOffset}.
     * <p>
     * For each vector lane, where {@code N} is the vector lane index,
     * if the mask lane at index {@code N} is set then
     * the lane element at index {@code N} is stored into the array
     * element {@code a[f(N)]}, where {@code f(N)} is the
     * index mapping expression
     * {@code offset + indexMap[mapOffset + N]]}.
     *
     * @param a the array
     * @param offset an offset to combine with the index map offsets
     * @param indexMap the index map
     * @param mapOffset the offset into the index map
     * @param m the mask
     * @throws IndexOutOfBoundsException
     *         if {@code mapOffset+N < 0}
     *         or if {@code mapOffset+N >= indexMap.length},
     *         or if {@code f(N)=offset+indexMap[mapOffset+N]}
     *         is an invalid index into {@code a},
     *         for any lane {@code N} in the vector
     *         where the mask is set
     * @see HalffloatVector#toIntArray()
     */
    @ForceInline
    public final
    void intoArray(short[] a, int offset,
                   int[] indexMap, int mapOffset,
                   VectorMask<Float16> m) {
        stOp(a, offset, m,
             (arr, off, i, e) -> {
                 int j = indexMap[mapOffset + i];
                 arr[off + j] = e;
             });
    }

    /**
     * Stores this vector into an array of type {@code char[]}
     * starting at an offset.
     * <p>
     * For each vector lane, where {@code N} is the vector lane index,
     * the lane element at index {@code N}
     * is first cast to a {@code char} value and then
     * stored into the array element {@code a[offset+N]}.
     *
     * @param a the array, of type {@code char[]}
     * @param offset the offset into the array
     * @throws IndexOutOfBoundsException
     *         if {@code offset+N < 0} or {@code offset+N >= a.length}
     *         for any lane {@code N} in the vector
     */
    @ForceInline
    public final
    void intoCharArray(char[] a, int offset) {
        offset = checkFromIndexSize(offset, length(), a.length);
        HalffloatSpecies vsp = vspecies();
        VectorSupport.store(
            vsp.vectorType(), vsp.carrierType(), VECTOR_OPER_TYPE, vsp.laneCount(),
            a, charArrayAddress(a, offset), false,
            this,
            a, offset,
            (arr, off, v)
            -> v.stOp(arr, (int) off,
                      (arr_, off_, i, e) -> arr_[off_ + i] = (char) e));
    }

    /**
     * Stores this vector into an array of type {@code char[]}
     * starting at offset and using a mask.
     * <p>
     * For each vector lane, where {@code N} is the vector lane index,
     * the lane element at index {@code N}
     * is first cast to a {@code char} value and then
     * stored into the array element {@code a[offset+N]}.
     * If the mask lane at {@code N} is unset then the corresponding
     * array element {@code a[offset+N]} is left unchanged.
     * <p>
     * Array range checking is done for lanes where the mask is set.
     * Lanes where the mask is unset are not stored and do not need
     * to correspond to legitimate elements of {@code a}.
     * That is, unset lanes may correspond to array indexes less than
     * zero or beyond the end of the array.
     *
     * @param a the array, of type {@code char[]}
     * @param offset the offset into the array
     * @param m the mask controlling lane storage
     * @throws IndexOutOfBoundsException
     *         if {@code offset+N < 0} or {@code offset+N >= a.length}
     *         for any lane {@code N} in the vector
     *         where the mask is set
     */
    @ForceInline
    public final
    void intoCharArray(char[] a, int offset,
                       VectorMask<Float16> m) {
        if (m.allTrue()) {
            intoCharArray(a, offset);
        } else {
            HalffloatSpecies vsp = vspecies();
            if (!VectorIntrinsics.indexInRange(offset, vsp.length(), a.length)) {
                ((AbstractMask<Float16>)m)
                    .checkIndexByLane(offset, a.length, vsp.iota(), 1);
            }
            intoCharArray0(a, offset, m);
        }
    }

    /**
     * Scatters this vector into an array of type {@code char[]}
     * using indexes obtained by adding a fixed {@code offset} to a
     * series of secondary offsets from an <em>index map</em>.
     * The index map is a contiguous sequence of {@code VLENGTH}
     * elements in a second array of {@code int}s, starting at a given
     * {@code mapOffset}.
     * <p>
     * For each vector lane, where {@code N} is the vector lane index,
     * the lane element at index {@code N}
     * is first cast to a {@code char} value and then
     * stored into the array
     * element {@code a[f(N)]}, where {@code f(N)} is the
     * index mapping expression
     * {@code offset + indexMap[mapOffset + N]]}.
     *
     * @param a the array
     * @param offset an offset to combine with the index map offsets
     * @param indexMap the index map
     * @param mapOffset the offset into the index map
     * @throws IndexOutOfBoundsException
     *         if {@code mapOffset+N < 0}
     *         or if {@code mapOffset+N >= indexMap.length},
     *         or if {@code f(N)=offset+indexMap[mapOffset+N]}
     *         is an invalid index into {@code a},
     *         for any lane {@code N} in the vector
     * @see HalffloatVector#toIntArray()
     */
    @ForceInline
    public final
    void intoCharArray(char[] a, int offset,
                       int[] indexMap, int mapOffset) {
        // FIXME: optimize
        stOp(a, offset,
             (arr, off, i, e) -> {
                 int j = indexMap[mapOffset + i];
                 arr[off + j] = (char) e;
             });
    }

    /**
     * Scatters this vector into an array of type {@code char[]},
     * under the control of a mask, and
     * using indexes obtained by adding a fixed {@code offset} to a
     * series of secondary offsets from an <em>index map</em>.
     * The index map is a contiguous sequence of {@code VLENGTH}
     * elements in a second array of {@code int}s, starting at a given
     * {@code mapOffset}.
     * <p>
     * For each vector lane, where {@code N} is the vector lane index,
     * if the mask lane at index {@code N} is set then
     * the lane element at index {@code N}
     * is first cast to a {@code char} value and then
     * stored into the array
     * element {@code a[f(N)]}, where {@code f(N)} is the
     * index mapping expression
     * {@code offset + indexMap[mapOffset + N]]}.
     *
     * @param a the array
     * @param offset an offset to combine with the index map offsets
     * @param indexMap the index map
     * @param mapOffset the offset into the index map
     * @param m the mask
     * @throws IndexOutOfBoundsException
     *         if {@code mapOffset+N < 0}
     *         or if {@code mapOffset+N >= indexMap.length},
     *         or if {@code f(N)=offset+indexMap[mapOffset+N]}
     *         is an invalid index into {@code a},
     *         for any lane {@code N} in the vector
     *         where the mask is set
     * @see HalffloatVector#toIntArray()
     */
    @ForceInline
    public final
    void intoCharArray(char[] a, int offset,
                       int[] indexMap, int mapOffset,
                       VectorMask<Float16> m) {
        // FIXME: optimize
        stOp(a, offset, m,
             (arr, off, i, e) -> {
                 int j = indexMap[mapOffset + i];
                 arr[off + j] = (char) e;
             });
    }


    /**
     * {@inheritDoc} <!--workaround-->
     * @since 19
     */
    @Override
    @ForceInline
    public final
    void intoMemorySegment(MemorySegment ms, long offset,
                           ByteOrder bo) {
        if (ms.isReadOnly()) {
            throw new UnsupportedOperationException("Attempt to write a read-only segment");
        }

        offset = checkFromIndexSize(offset, byteSize(), ms.byteSize());
        maybeSwap(bo).intoMemorySegment0(ms, offset);
    }

    /**
     * {@inheritDoc} <!--workaround-->
     * @since 19
     */
    @Override
    @ForceInline
    public final
    void intoMemorySegment(MemorySegment ms, long offset,
                           ByteOrder bo,
                           VectorMask<Float16> m) {
        if (m.allTrue()) {
            intoMemorySegment(ms, offset, bo);
        } else {
            if (ms.isReadOnly()) {
                throw new UnsupportedOperationException("Attempt to write a read-only segment");
            }
            HalffloatSpecies vsp = vspecies();
            if (!VectorIntrinsics.indexInRange(offset, vsp.vectorByteSize(), ms.byteSize())) {
                ((AbstractMask<Float16>)m)
                    .checkIndexByLane(offset, ms.byteSize(), vsp.iota(), 2);
            }
            maybeSwap(bo).intoMemorySegment0(ms, offset, m);
        }
    }

    // ================================================

    // Low-level memory operations.
    //
    // Note that all of these operations *must* inline into a context
    // where the exact species of the involved vector is a
    // compile-time constant.  Otherwise, the intrinsic generation
    // will fail and performance will suffer.
    //
    // In many cases this is achieved by re-deriving a version of the
    // method in each concrete subclass (per species).  The re-derived
    // method simply calls one of these generic methods, with exact
    // parameters for the controlling metadata, which is either a
    // typed vector or constant species instance.

    // Unchecked loading operations in native byte order.
    // Caller is responsible for applying index checks, masking, and
    // byte swapping.

    /*package-private*/
    abstract
    HalffloatVector fromArray0(short[] a, int offset);
    @ForceInline
    final
    HalffloatVector fromArray0Template(short[] a, int offset) {
        HalffloatSpecies vsp = vspecies();
        return VectorSupport.load(
            vsp.vectorType(), vsp.carrierType(), VECTOR_OPER_TYPE, vsp.laneCount(),
            a, arrayAddress(a, offset), false,
            a, offset, vsp,
            (arr, off, s) -> s.ldOp(arr, (int) off,
                                    (arr_, off_, i) -> arr_[off_ + i]));
    }

    /*package-private*/
    abstract
    HalffloatVector fromArray0(short[] a, int offset, VectorMask<Float16> m, int offsetInRange);
    @ForceInline
    final
    <M extends VectorMask<Float16>>
    HalffloatVector fromArray0Template(Class<M> maskClass, short[] a, int offset, M m, int offsetInRange) {
        m.check(species());
        HalffloatSpecies vsp = vspecies();
        return VectorSupport.loadMasked(
            vsp.vectorType(), maskClass, vsp.carrierType(), VECTOR_OPER_TYPE, vsp.laneCount(),
            a, arrayAddress(a, offset), false, m, offsetInRange,
            a, offset, vsp,
            (arr, off, s, vm) -> s.ldOp(arr, (int) off, vm,
                                        (arr_, off_, i) -> arr_[off_ + i]));
    }

    /*package-private*/
    abstract
    HalffloatVector fromArray0(short[] a, int offset,
                                    int[] indexMap, int mapOffset,
                                    VectorMask<Float16> m);
    @ForceInline
    final
    <M extends VectorMask<Float16>>
    HalffloatVector fromArray0Template(Class<M> maskClass, short[] a, int offset,
                                            int[] indexMap, int mapOffset, M m) {
        HalffloatSpecies vsp = vspecies();
        IntVector.IntSpecies isp = IntVector.species(vsp.indexShape());
        Objects.requireNonNull(a);
        Objects.requireNonNull(indexMap);
        m.check(vsp);
        Class<? extends HalffloatVector> vectorType = vsp.vectorType();


        // Constant folding should sweep out following conditonal logic.
        VectorSpecies<Integer> lsp;
        if (isp.length() > IntVector.SPECIES_PREFERRED.length()) {
            lsp = IntVector.SPECIES_PREFERRED;
        } else {
            lsp = isp;
        }

        // Check indices are within array bounds.
        // FIXME: Check index under mask controlling.
        IntVector vix0 = IntVector.fromArray(lsp, indexMap, mapOffset).add(offset);
        VectorIntrinsics.checkIndex(vix0, a.length);

        int vlen = vsp.length();
        int idx_vlen = lsp.length();
        IntVector vix1 = null;
        if (vlen >= idx_vlen * 2) {
            vix1 = IntVector.fromArray(lsp, indexMap, mapOffset + idx_vlen).add(offset);
            VectorIntrinsics.checkIndex(vix1, a.length);
        }

        return VectorSupport.loadWithMap(
            vectorType, maskClass, short.class, VECTOR_OPER_TYPE, vsp.laneCount(),
            lsp.vectorType(), lsp.length(),
            a, ARRAY_BASE, vix0, vix1, null, null, m,
            a, offset, indexMap, mapOffset, vsp,
            (c, idx, iMap, idy, s, vm) ->
            s.vOp(vm, n -> c[idx + iMap[idy+n]]));
    }

    /*package-private*/
    abstract
    HalffloatVector fromCharArray0(char[] a, int offset);
    @ForceInline
    final
    HalffloatVector fromCharArray0Template(char[] a, int offset) {
        HalffloatSpecies vsp = vspecies();
        return VectorSupport.load(
            vsp.vectorType(), vsp.carrierType(), VECTOR_OPER_TYPE, vsp.laneCount(),
            a, charArrayAddress(a, offset), false,
            a, offset, vsp,
            (arr, off, s) -> s.ldOp(arr, (int) off,
                                    (arr_, off_, i) -> (short) arr_[off_ + i]));
    }

    /*package-private*/
    abstract
    HalffloatVector fromCharArray0(char[] a, int offset, VectorMask<Float16> m, int offsetInRange);
    @ForceInline
    final
    <M extends VectorMask<Float16>>
    HalffloatVector fromCharArray0Template(Class<M> maskClass, char[] a, int offset, M m, int offsetInRange) {
        m.check(species());
        HalffloatSpecies vsp = vspecies();
        return VectorSupport.loadMasked(
                vsp.vectorType(), maskClass, vsp.carrierType(), VECTOR_OPER_TYPE, vsp.laneCount(),
                a, charArrayAddress(a, offset), false, m, offsetInRange,
                a, offset, vsp,
                (arr, off, s, vm) -> s.ldOp(arr, (int) off, vm,
                                            (arr_, off_, i) -> (short) arr_[off_ + i]));
    }


    abstract
    HalffloatVector fromMemorySegment0(MemorySegment bb, long offset);
    @ForceInline
    final
    HalffloatVector fromMemorySegment0Template(MemorySegment ms, long offset) {
        HalffloatSpecies vsp = vspecies();
        return ScopedMemoryAccess.loadFromMemorySegment(
                vsp.vectorType(), vsp.carrierType(), VECTOR_OPER_TYPE, vsp.laneCount(),
                (AbstractMemorySegmentImpl) ms, offset, vsp,
                (msp, off, s) -> {
                    return s.ldLongOp((MemorySegment) msp, off, HalffloatVector::memorySegmentGet);
                });
    }

    abstract
    HalffloatVector fromMemorySegment0(MemorySegment ms, long offset, VectorMask<Float16> m, int offsetInRange);
    @ForceInline
    final
    <M extends VectorMask<Float16>>
    HalffloatVector fromMemorySegment0Template(Class<M> maskClass, MemorySegment ms, long offset, M m, int offsetInRange) {
        HalffloatSpecies vsp = vspecies();
        m.check(vsp);
        return ScopedMemoryAccess.loadFromMemorySegmentMasked(
                vsp.vectorType(), maskClass, vsp.carrierType(), VECTOR_OPER_TYPE, vsp.laneCount(),
                (AbstractMemorySegmentImpl) ms, offset, m, vsp, offsetInRange,
                (msp, off, s, vm) -> {
                    return s.ldLongOp((MemorySegment) msp, off, vm, HalffloatVector::memorySegmentGet);
                });
    }

    // Unchecked storing operations in native byte order.
    // Caller is responsible for applying index checks, masking, and
    // byte swapping.

    abstract
    void intoArray0(short[] a, int offset);
    @ForceInline
    final
    void intoArray0Template(short[] a, int offset) {
        HalffloatSpecies vsp = vspecies();
        VectorSupport.store(
            vsp.vectorType(), vsp.carrierType(), VECTOR_OPER_TYPE, vsp.laneCount(),
            a, arrayAddress(a, offset), false,
            this, a, offset,
            (arr, off, v)
            -> v.stOp(arr, (int) off,
                      (arr_, off_, i, e) -> arr_[off_+i] = e));
    }

    abstract
    void intoArray0(short[] a, int offset, VectorMask<Float16> m);
    @ForceInline
    final
    <M extends VectorMask<Float16>>
    void intoArray0Template(Class<M> maskClass, short[] a, int offset, M m) {
        m.check(species());
        HalffloatSpecies vsp = vspecies();
        VectorSupport.storeMasked(
            vsp.vectorType(), maskClass, vsp.carrierType(), VECTOR_OPER_TYPE, vsp.laneCount(),
            a, arrayAddress(a, offset), false,
            this, m, a, offset,
            (arr, off, v, vm)
            -> v.stOp(arr, (int) off, vm,
                      (arr_, off_, i, e) -> arr_[off_ + i] = e));
    }



    @ForceInline
    final
    void intoMemorySegment0(MemorySegment ms, long offset) {
        HalffloatSpecies vsp = vspecies();
        ScopedMemoryAccess.storeIntoMemorySegment(
                vsp.vectorType(), vsp.carrierType(), VECTOR_OPER_TYPE, vsp.laneCount(),
                this,
                (AbstractMemorySegmentImpl) ms, offset,
                (msp, off, v) -> {
                    v.stLongOp((MemorySegment) msp, off, HalffloatVector::memorySegmentSet);
                });
    }

    abstract
    void intoMemorySegment0(MemorySegment bb, long offset, VectorMask<Float16> m);
    @ForceInline
    final
    <M extends VectorMask<Float16>>
    void intoMemorySegment0Template(Class<M> maskClass, MemorySegment ms, long offset, M m) {
        HalffloatSpecies vsp = vspecies();
        m.check(vsp);
        ScopedMemoryAccess.storeIntoMemorySegmentMasked(
                vsp.vectorType(), maskClass, vsp.carrierType(), VECTOR_OPER_TYPE, vsp.laneCount(),
                this, m,
                (AbstractMemorySegmentImpl) ms, offset,
                (msp, off, v, vm) -> {
                    v.stLongOp((MemorySegment) msp, off, vm, HalffloatVector::memorySegmentSet);
                });
    }

    /*package-private*/
    abstract
    void intoCharArray0(char[] a, int offset, VectorMask<Float16> m);
    @ForceInline
    final
    <M extends VectorMask<Float16>>
    void intoCharArray0Template(Class<M> maskClass, char[] a, int offset, M m) {
        m.check(species());
        HalffloatSpecies vsp = vspecies();
        VectorSupport.storeMasked(
            vsp.vectorType(), maskClass, vsp.carrierType(), VECTOR_OPER_TYPE, vsp.laneCount(),
            a, charArrayAddress(a, offset), false,
            this, m, a, offset,
            (arr, off, v, vm)
            -> v.stOp(arr, (int) off, vm,
                      (arr_, off_, i, e) -> arr_[off_ + i] = (char) e));
    }

    // End of low-level memory operations.

    @ForceInline
    private void conditionalStoreNYI(int offset,
                                     HalffloatSpecies vsp,
                                     VectorMask<Float16> m,
                                     int scale,
                                     int limit) {
        if (offset < 0 || offset + vsp.laneCount() * scale > limit) {
            String msg =
                String.format("unimplemented: store @%d in [0..%d), %s in %s",
                              offset, limit, m, vsp);
            throw new AssertionError(msg);
        }
    }

    /*package-private*/
    @Override
    @ForceInline
    final
    HalffloatVector maybeSwap(ByteOrder bo) {
        if (bo != NATIVE_ENDIAN) {
            return this.reinterpretAsBytes()
                .rearrange(swapBytesShuffle())
                .reinterpretAsHalffloats();
        }
        return this;
    }

    static final int ARRAY_SHIFT =
        31 - Integer.numberOfLeadingZeros(Unsafe.ARRAY_SHORT_INDEX_SCALE);
    static final long ARRAY_BASE =
        Unsafe.ARRAY_SHORT_BASE_OFFSET;

    @ForceInline
    static long arrayAddress(short[] a, int index) {
        return ARRAY_BASE + (((long)index) << ARRAY_SHIFT);
    }

    static final int ARRAY_CHAR_SHIFT =
            31 - Integer.numberOfLeadingZeros(Unsafe.ARRAY_CHAR_INDEX_SCALE);
    static final long ARRAY_CHAR_BASE =
            Unsafe.ARRAY_CHAR_BASE_OFFSET;

    @ForceInline
    static long charArrayAddress(char[] a, int index) {
        return ARRAY_CHAR_BASE + (((long)index) << ARRAY_CHAR_SHIFT);
    }


    @ForceInline
    static long byteArrayAddress(byte[] a, int index) {
        return Unsafe.ARRAY_BYTE_BASE_OFFSET + index;
    }

    // ================================================

    /// Reinterpreting view methods:
    //   lanewise reinterpret: viewAsXVector()
    //   keep shape, redraw lanes: reinterpretAsEs()

    /**
     * {@inheritDoc} <!--workaround-->
     */
    @ForceInline
    @Override
    public final ByteVector reinterpretAsBytes() {
         // Going to ByteVector, pay close attention to byte order.
         assert(REGISTER_ENDIAN == ByteOrder.LITTLE_ENDIAN);
         return asByteVectorRaw();
         //return asByteVectorRaw().rearrange(swapBytesShuffle());
    }

    /**
     * {@inheritDoc} <!--workaround-->
     */
    @ForceInline
    @Override
    public final ShortVector viewAsIntegralLanes() {
        LaneType ilt = LaneType.SHORT.asIntegral();
        return (ShortVector) asVectorRaw(ilt);
    }

    /**
     * {@inheritDoc} <!--workaround-->
     *
     * @implNote This method always throws
     * {@code UnsupportedOperationException}, because there is no floating
     * point type of the same size as {@code short}.  The return type
     * of this method is arbitrarily designated as
     * {@code Vector<?>}.  Future versions of this API may change the return
     * type if additional floating point types become available.
     */
    @ForceInline
    @Override
    public final
    HalffloatVector
    viewAsFloatingLanes() {
        return this;
    }

    // ================================================

    /// Object methods: toString, equals, hashCode
    //
    // Object methods are defined as if via Arrays.toString, etc.,
    // is applied to the array of elements.  Two equal vectors
    // are required to have equal species and equal lane values.

    /**
     * Returns a string representation of this vector, of the form
     * {@code "[0,1,2...]"}, reporting the lane values of this vector,
     * in lane order.
     *
     * The string is produced as if by a call to {@link
     * java.util.Arrays#toString(short[]) Arrays.toString()},
     * as appropriate to the {@code short} array returned by
     * {@link #toArray this.toArray()}.
     *
     * @return a string of the form {@code "[0,1,2...]"}
     * reporting the lane values of this vector
     */
    @Override
    @ForceInline
    public final
    String toString() {
        // now that toArray is strongly typed, we can define this
        return Arrays.toString(toArray());
    }

    /**
     * {@inheritDoc} <!--workaround-->
     */
    @Override
    @ForceInline
    public final
    boolean equals(Object obj) {
        if (obj instanceof Vector) {
            Vector<?> that = (Vector<?>) obj;
            if (this.species().equals(that.species())) {
                return this.eq(that.check(this.species())).allTrue();
            }
        }
        return false;
    }

    /**
     * {@inheritDoc} <!--workaround-->
     */
    @Override
    @ForceInline
    public final
    int hashCode() {
        // now that toArray is strongly typed, we can define this
        return Objects.hash(species(), Arrays.hashCode(toArray()));
    }

    // ================================================

    // Species

    /**
     * Class representing {@link HalffloatVector}'s of the same {@link VectorShape VectorShape}.
     */
    /*package-private*/
    static final class HalffloatSpecies extends AbstractSpecies<Float16> {
        private HalffloatSpecies(VectorShape shape,
                Class<? extends HalffloatVector> vectorType,
                Class<? extends AbstractMask<Float16>> maskType,
                Class<? extends AbstractShuffle<Float16>> shuffleType,
                Function<Object, HalffloatVector> vectorFactory) {
            super(shape, LaneType.of(Float16.class),
                  vectorType, maskType, shuffleType,
                  vectorFactory);
            assert(this.elementSize() == Float16.SIZE);
        }

        // Specializing overrides:

        @ForceInline
        final Class<?> carrierType() {
            return short.class;
        }

        @ForceInline
        final int operType() {
            if (Float16.class.equals(Float16.class)) {
                return VECTOR_TYPE_FP16;
            }
            return VECTOR_TYPE_PRIM;
        }

        @Override
        @ForceInline
        public final Class<Float16> elementType() {
            return Float16.class;
        }

        @Override
        @ForceInline
        final Class<Float16> genericElementType() {
            return Float16.class;
        }

        @SuppressWarnings("unchecked")
        @Override
        @ForceInline
        public final Class<? extends HalffloatVector> vectorType() {
            return (Class<? extends HalffloatVector>) vectorType;
        }

        @Override
        @ForceInline
        public final long checkValue(long e) {
            longToElementBits(e);  // only for exception
            return e;
        }

        /*package-private*/
        @Override
        @ForceInline
        final HalffloatVector broadcastBits(long bits) {
            return (HalffloatVector)
                VectorSupport.fromBitsCoerced(
                    vectorType, short.class, VECTOR_OPER_TYPE, laneCount,
                    bits, MODE_BROADCAST, this,
                    (bits_, s_) -> s_.rvOp(i -> bits_));
        }

        /*package-private*/
        @ForceInline
        final HalffloatVector broadcast(short e) {
            return broadcastBits(toBits(e));
        }

        @Override
        @ForceInline
        public final HalffloatVector broadcast(long e) {
            return broadcastBits(longToElementBits(e));
        }

        /*package-private*/
        final @Override
        @ForceInline
        long longToElementBits(long value) {
            // Do the conversion, and then test it for failure.
            short e = (short) value;
            if ((long) e != value) {
                throw badElementBits(value, e);
            }
            return toBits(e);
        }

        /*package-private*/
        @ForceInline
        static long toIntegralChecked(short e, boolean convertToInt) {
            long value = convertToInt ? (int) e : (long) e;
            if ((short) value != e) {
                throw badArrayBits(e, convertToInt, value);
            }
            return value;
        }

        /* this non-public one is for internal conversions */
        @Override
        @ForceInline
        final HalffloatVector fromIntValues(int[] values) {
            VectorIntrinsics.requireLength(values.length, laneCount);
            short[] va = new short[laneCount()];
            for (int i = 0; i < va.length; i++) {
                int lv = values[i];
                short v = Float16.float16ToRawShortBits(Float16.valueOf(lv));
                va[i] = v;
                if (Float16.valueOf(lv).intValue() != lv) {
                    throw badElementBits(lv, v);
                }
            }
            return dummyVector().fromArray0(va, 0);
        }

        // Virtual constructors

        @ForceInline
        @Override final
        public HalffloatVector fromArray(Object a, int offset) {
            // User entry point
            // Defer only to the equivalent method on the vector class, using the same inputs
            return HalffloatVector
                .fromArray(this, (short[]) a, offset);
        }

        @ForceInline
        @Override final
        public HalffloatVector fromMemorySegment(MemorySegment ms, long offset, ByteOrder bo) {
            // User entry point
            // Defer only to the equivalent method on the vector class, using the same inputs
            return HalffloatVector
                .fromMemorySegment(this, ms, offset, bo);
        }

        @ForceInline
        @Override final
        HalffloatVector dummyVector() {
            return (HalffloatVector) super.dummyVector();
        }

        /*package-private*/
        final @Override
        @ForceInline
        HalffloatVector rvOp(RVOp f) {
            short[] res = new short[laneCount()];
            for (int i = 0; i < res.length; i++) {
                short bits = (short) f.apply(i);
                res[i] = fromBits(bits);
            }
            return dummyVector().vectorFactory(res);
        }

        HalffloatVector vOp(FVOp f) {
            short[] res = new short[laneCount()];
            for (int i = 0; i < res.length; i++) {
                res[i] = f.apply(i);
            }
            return dummyVector().vectorFactory(res);
        }

        HalffloatVector vOp(VectorMask<Float16> m, FVOp f) {
            short[] res = new short[laneCount()];
            boolean[] mbits = ((AbstractMask<Float16>)m).getBits();
            for (int i = 0; i < res.length; i++) {
                if (mbits[i]) {
                    res[i] = f.apply(i);
                }
            }
            return dummyVector().vectorFactory(res);
        }

        /*package-private*/
        @ForceInline
        <M> HalffloatVector ldOp(M memory, int offset,
                                      FLdOp<M> f) {
            return dummyVector().ldOp(memory, offset, f);
        }

        /*package-private*/
        @ForceInline
        <M> HalffloatVector ldOp(M memory, int offset,
                                      VectorMask<Float16> m,
                                      FLdOp<M> f) {
            return dummyVector().ldOp(memory, offset, m, f);
        }

        /*package-private*/
        @ForceInline
        HalffloatVector ldLongOp(MemorySegment memory, long offset,
                                      FLdLongOp f) {
            return dummyVector().ldLongOp(memory, offset, f);
        }

        /*package-private*/
        @ForceInline
        HalffloatVector ldLongOp(MemorySegment memory, long offset,
                                      VectorMask<Float16> m,
                                      FLdLongOp f) {
            return dummyVector().ldLongOp(memory, offset, m, f);
        }

        /*package-private*/
        @ForceInline
        <M> void stOp(M memory, int offset, FStOp<M> f) {
            dummyVector().stOp(memory, offset, f);
        }

        /*package-private*/
        @ForceInline
        <M> void stOp(M memory, int offset,
                      AbstractMask<Float16> m,
                      FStOp<M> f) {
            dummyVector().stOp(memory, offset, m, f);
        }

        /*package-private*/
        @ForceInline
        void stLongOp(MemorySegment memory, long offset, FStLongOp f) {
            dummyVector().stLongOp(memory, offset, f);
        }

        /*package-private*/
        @ForceInline
        void stLongOp(MemorySegment memory, long offset,
                      AbstractMask<Float16> m,
                      FStLongOp f) {
            dummyVector().stLongOp(memory, offset, m, f);
        }

        // N.B. Make sure these constant vectors and
        // masks load up correctly into registers.
        //
        // Also, see if we can avoid all that switching.
        // Could we cache both vectors and both masks in
        // this species object?

        // Zero and iota vector access
        @Override
        @ForceInline
        public final HalffloatVector zero() {
            if ((Class<?>) vectorType() == HalffloatMaxVector.class)
                return HalffloatMaxVector.ZERO;
            switch (vectorBitSize()) {
                case 64: return Halffloat64Vector.ZERO;
                case 128: return Halffloat128Vector.ZERO;
                case 256: return Halffloat256Vector.ZERO;
                case 512: return Halffloat512Vector.ZERO;
            }
            throw new AssertionError();
        }

        @Override
        @ForceInline
        public final HalffloatVector iota() {
            if ((Class<?>) vectorType() == HalffloatMaxVector.class)
                return HalffloatMaxVector.IOTA;
            switch (vectorBitSize()) {
                case 64: return Halffloat64Vector.IOTA;
                case 128: return Halffloat128Vector.IOTA;
                case 256: return Halffloat256Vector.IOTA;
                case 512: return Halffloat512Vector.IOTA;
            }
            throw new AssertionError();
        }

        // Mask access
        @Override
        @ForceInline
        public final VectorMask<Float16> maskAll(boolean bit) {
            if ((Class<?>) vectorType() == HalffloatMaxVector.class)
                return HalffloatMaxVector.HalffloatMaxMask.maskAll(bit);
            switch (vectorBitSize()) {
                case 64: return Halffloat64Vector.Halffloat64Mask.maskAll(bit);
                case 128: return Halffloat128Vector.Halffloat128Mask.maskAll(bit);
                case 256: return Halffloat256Vector.Halffloat256Mask.maskAll(bit);
                case 512: return Halffloat512Vector.Halffloat512Mask.maskAll(bit);
            }
            throw new AssertionError();
        }
    }

    /**
     * Finds a species for an element type of {@code short} and shape.
     *
     * @param s the shape
     * @return a species for an element type of {@code short} and shape
     * @throws IllegalArgumentException if no such species exists for the shape
     */
    static HalffloatSpecies species(VectorShape s) {
        Objects.requireNonNull(s);
        switch (s.switchKey) {
            case VectorShape.SK_64_BIT: return (HalffloatSpecies) SPECIES_64;
            case VectorShape.SK_128_BIT: return (HalffloatSpecies) SPECIES_128;
            case VectorShape.SK_256_BIT: return (HalffloatSpecies) SPECIES_256;
            case VectorShape.SK_512_BIT: return (HalffloatSpecies) SPECIES_512;
            case VectorShape.SK_Max_BIT: return (HalffloatSpecies) SPECIES_MAX;
            default: throw new IllegalArgumentException("Bad shape: " + s);
        }
    }

    /** Species representing {@link HalffloatVector}s of {@link VectorShape#S_64_BIT VectorShape.S_64_BIT}. */
    public static final VectorSpecies<Float16> SPECIES_64
        = new HalffloatSpecies(VectorShape.S_64_BIT,
                            Halffloat64Vector.class,
                            Halffloat64Vector.Halffloat64Mask.class,
                            Halffloat64Vector.Halffloat64Shuffle.class,
                            Halffloat64Vector::new);

    /** Species representing {@link HalffloatVector}s of {@link VectorShape#S_128_BIT VectorShape.S_128_BIT}. */
    public static final VectorSpecies<Float16> SPECIES_128
        = new HalffloatSpecies(VectorShape.S_128_BIT,
                            Halffloat128Vector.class,
                            Halffloat128Vector.Halffloat128Mask.class,
                            Halffloat128Vector.Halffloat128Shuffle.class,
                            Halffloat128Vector::new);

    /** Species representing {@link HalffloatVector}s of {@link VectorShape#S_256_BIT VectorShape.S_256_BIT}. */
    public static final VectorSpecies<Float16> SPECIES_256
        = new HalffloatSpecies(VectorShape.S_256_BIT,
                            Halffloat256Vector.class,
                            Halffloat256Vector.Halffloat256Mask.class,
                            Halffloat256Vector.Halffloat256Shuffle.class,
                            Halffloat256Vector::new);

    /** Species representing {@link HalffloatVector}s of {@link VectorShape#S_512_BIT VectorShape.S_512_BIT}. */
    public static final VectorSpecies<Float16> SPECIES_512
        = new HalffloatSpecies(VectorShape.S_512_BIT,
                            Halffloat512Vector.class,
                            Halffloat512Vector.Halffloat512Mask.class,
                            Halffloat512Vector.Halffloat512Shuffle.class,
                            Halffloat512Vector::new);

    /** Species representing {@link HalffloatVector}s of {@link VectorShape#S_Max_BIT VectorShape.S_Max_BIT}. */
    public static final VectorSpecies<Float16> SPECIES_MAX
        = new HalffloatSpecies(VectorShape.S_Max_BIT,
                            HalffloatMaxVector.class,
                            HalffloatMaxVector.HalffloatMaxMask.class,
                            HalffloatMaxVector.HalffloatMaxShuffle.class,
                            HalffloatMaxVector::new);

    /**
     * Preferred species for {@link HalffloatVector}s.
     * A preferred species is a species of maximal bit-size for the platform.
     */
    public static final VectorSpecies<Float16> SPECIES_PREFERRED
        = (HalffloatSpecies) VectorSpecies.ofPreferred(Float16.class);
}

