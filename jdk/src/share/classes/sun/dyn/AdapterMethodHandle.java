/*
 * Copyright 2008-2009 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package sun.dyn;

import sun.dyn.util.VerifyType;
import sun.dyn.util.Wrapper;
import java.dyn.*;
import java.util.Arrays;
import static sun.dyn.MethodHandleNatives.Constants.*;
import static sun.dyn.MethodHandleImpl.newIllegalArgumentException;

/**
 * This method handle performs simple conversion or checking of a single argument.
 * @author jrose
 */
public class AdapterMethodHandle extends BoundMethodHandle {

    //MethodHandle vmtarget;   // next AMH or BMH in chain or final DMH
    //Object       argument;   // parameter to the conversion if needed
    //int          vmargslot;  // which argument slot is affected
    private final int conversion;  // the type of conversion: RETYPE_ONLY, etc.

    // Constructors in this class *must* be package scoped or private.
    private AdapterMethodHandle(MethodHandle target, MethodType newType,
                long conv, Object convArg) {
        super(newType, convArg, newType.parameterSlotDepth(1+convArgPos(conv)));
        this.conversion = convCode(conv);
        if (MethodHandleNatives.JVM_SUPPORT) {
            // JVM might update VM-specific bits of conversion (ignore)
            MethodHandleNatives.init(this, target, convArgPos(conv));
        }
    }
    private AdapterMethodHandle(MethodHandle target, MethodType newType,
                long conv) {
        this(target, newType, conv, null);
    }

    private static final Access IMPL_TOKEN = Access.getToken();

    // TO DO:  When adapting another MH with a null conversion, clone
    // the target and change its type, instead of adding another layer.

    /** Can a JVM-level adapter directly implement the proposed
     *  argument conversions, as if by MethodHandles.convertArguments?
     */
    public static boolean canPairwiseConvert(MethodType newType, MethodType oldType) {
        // same number of args, of course
        int len = newType.parameterCount();
        if (len != oldType.parameterCount())
            return false;

        // Check return type.  (Not much can be done with it.)
        Class<?> exp = newType.returnType();
        Class<?> ret = oldType.returnType();
        if (!VerifyType.isNullConversion(ret, exp))
            return false;

        // Check args pairwise.
        for (int i = 0; i < len; i++) {
            Class<?> src = newType.parameterType(i); // source type
            Class<?> dst = oldType.parameterType(i); // destination type
            if (!canConvertArgument(src, dst))
                return false;
        }

        return true;
    }

    /** Can a JVM-level adapter directly implement the proposed
     *  argument conversion, as if by MethodHandles.convertArguments?
     */
    public static boolean canConvertArgument(Class<?> src, Class<?> dst) {
        // ? Retool this logic to use RETYPE_ONLY, CHECK_CAST, etc., as opcodes,
        // so we don't need to repeat so much decision making.
        if (VerifyType.isNullConversion(src, dst)) {
            return true;
        } else if (src.isPrimitive()) {
            if (dst.isPrimitive())
                return canPrimCast(src, dst);
            else
                return canBoxArgument(src, dst);
        } else {
            if (dst.isPrimitive())
                return canUnboxArgument(src, dst);
            else
                return true;  // any two refs can be interconverted
        }
    }

    /**
     * Create a JVM-level adapter method handle to conform the given method
     * handle to the similar newType, using only pairwise argument conversions.
     * For each argument, convert incoming argument to the exact type needed.
     * Only null conversions are allowed on the return value (until
     * the JVM supports ricochet adapters).
     * The argument conversions allowed are casting, unboxing,
     * integral widening or narrowing, and floating point widening or narrowing.
     * @param token access check
     * @param newType required call type
     * @param target original method handle
     * @return an adapter to the original handle with the desired new type,
     *          or the original target if the types are already identical
     *          or null if the adaptation cannot be made
     */
    public static MethodHandle makePairwiseConvert(Access token,
                MethodType newType, MethodHandle target) {
        Access.check(token);
        MethodType oldType = target.type();
        if (newType == oldType)  return target;

        if (!canPairwiseConvert(newType, oldType))
            return null;
        // (after this point, it is an assertion error to fail to convert)

        // Find last non-trivial conversion (if any).
        int lastConv = newType.parameterCount()-1;
        while (lastConv >= 0) {
            Class<?> src = newType.parameterType(lastConv); // source type
            Class<?> dst = oldType.parameterType(lastConv); // destination type
            if (VerifyType.isNullConversion(src, dst)) {
                --lastConv;
            } else {
                break;
            }
        }
        // Now build a chain of one or more adapters.
        MethodHandle adapter = target;
        MethodType midType = oldType.changeReturnType(newType.returnType());
        for (int i = 0; i <= lastConv; i++) {
            Class<?> src = newType.parameterType(i); // source type
            Class<?> dst = midType.parameterType(i); // destination type
            if (VerifyType.isNullConversion(src, dst)) {
                // do nothing: difference is trivial
                continue;
            }
            // Work the current type backward toward the desired caller type:
            if (i != lastConv) {
                midType = midType.changeParameterType(i, src);
            } else {
                // When doing the last (or only) real conversion,
                // force all remaining null conversions to happen also.
                assert(VerifyType.isNullConversion(newType, midType.changeParameterType(i, src)));
                midType = newType;
            }

            // Tricky case analysis follows.
            // It parallels canConvertArgument() above.
            if (src.isPrimitive()) {
                if (dst.isPrimitive()) {
                    adapter = makePrimCast(token, midType, adapter, i, dst);
                } else {
                    adapter = makeBoxArgument(token, midType, adapter, i, dst);
                }
            } else {
                if (dst.isPrimitive()) {
                    // Caller has boxed a primitive.  Unbox it for the target.
                    // The box type must correspond exactly to the primitive type.
                    // This is simpler than the powerful set of widening
                    // conversions supported by reflect.Method.invoke.
                    // Those conversions require a big nest of if/then/else logic,
                    // which we prefer to make a user responsibility.
                    adapter = makeUnboxArgument(token, midType, adapter, i, dst);
                } else {
                    // Simple reference conversion.
                    // Note:  Do not check for a class hierarchy relation
                    // between src and dst.  In all cases a 'null' argument
                    // will pass the cast conversion.
                    adapter = makeCheckCast(token, midType, adapter, i, dst);
                }
            }
            assert(adapter != null);
            assert(adapter.type() == midType);
        }
        if (adapter.type() != newType) {
            // Only trivial conversions remain.
            adapter = makeRetypeOnly(IMPL_TOKEN, newType, adapter);
            assert(adapter != null);
            // Actually, that's because there were no non-trivial ones:
            assert(lastConv == -1);
        }
        assert(adapter.type() == newType);
        return adapter;
    }

    /**
     * Create a JVM-level adapter method handle to permute the arguments
     * of the given method.
     * @param token access check
     * @param newType required call type
     * @param target original method handle
     * @param argumentMap for each target argument, position of its source in newType
     * @return an adapter to the original handle with the desired new type,
     *          or the original target if the types are already identical
     *          and the permutation is null
     * @throws IllegalArgumentException if the adaptation cannot be made
     *          directly by a JVM-level adapter, without help from Java code
     */
    public static MethodHandle makePermutation(Access token,
                MethodType newType, MethodHandle target,
                int[] argumentMap) {
        MethodType oldType = target.type();
        boolean nullPermutation = true;
        for (int i = 0; i < argumentMap.length; i++) {
            int pos = argumentMap[i];
            if (pos != i)
                nullPermutation = false;
            if (pos < 0 || pos >= newType.parameterCount()) {
                argumentMap = new int[0]; break;
            }
        }
        if (argumentMap.length != oldType.parameterCount())
            throw newIllegalArgumentException("bad permutation: "+Arrays.toString(argumentMap));
        if (nullPermutation) {
            MethodHandle res = makePairwiseConvert(token, newType, target);
            // well, that was easy
            if (res == null)
                throw newIllegalArgumentException("cannot convert pairwise: "+newType);
            return res;
        }

        // Check return type.  (Not much can be done with it.)
        Class<?> exp = newType.returnType();
        Class<?> ret = oldType.returnType();
        if (!VerifyType.isNullConversion(ret, exp))
            throw newIllegalArgumentException("bad return conversion for "+newType);

        // See if the argument types match up.
        for (int i = 0; i < argumentMap.length; i++) {
            int j = argumentMap[i];
            Class<?> src = newType.parameterType(j);
            Class<?> dst = oldType.parameterType(i);
            if (!VerifyType.isNullConversion(src, dst))
                throw newIllegalArgumentException("bad argument #"+j+" conversion for "+newType);
        }

        // Now figure out a nice mix of SWAP, ROT, DUP, and DROP adapters.
        // A workable greedy algorithm is as follows:
        // Drop unused outgoing arguments (right to left: shallowest first).
        // Duplicate doubly-used outgoing arguments (left to right: deepest first).
        // Then the remaining problem is a true argument permutation.
        // Marshal the outgoing arguments as required from left to right.
        // That is, find the deepest outgoing stack position that does not yet
        // have the correct argument value, and correct at least that position
        // by swapping or rotating in the misplaced value (from a shallower place).
        // If the misplaced value is followed by one or more consecutive values
        // (also misplaced)  issue a rotation which brings as many as possible
        // into position.  Otherwise make progress with either a swap or a
        // rotation.  Prefer the swap as cheaper, but do not use it if it
        // breaks a slot pair.  Prefer the rotation over the swap if it would
        // preserve more consecutive values shallower than the target position.
        // When more than one rotation will work (because the required value
        // is already adjacent to the target position), then use a rotation
        // which moves the old value in the target position adjacent to
        // one of its consecutive values.  Also, prefer shorter rotation
        // spans, since they use fewer memory cycles for shuffling.

        throw new UnsupportedOperationException("NYI");
    }

    private static byte basicType(Class<?> type) {
        if (type == null)  return T_VOID;
        switch (Wrapper.forBasicType(type)) {
            case BOOLEAN:  return T_BOOLEAN;
            case CHAR:     return T_CHAR;
            case FLOAT:    return T_FLOAT;
            case DOUBLE:   return T_DOUBLE;
            case BYTE:     return T_BYTE;
            case SHORT:    return T_SHORT;
            case INT:      return T_INT;
            case LONG:     return T_LONG;
            case OBJECT:   return T_OBJECT;
            case VOID:     return T_VOID;
        }
        return 99; // T_ILLEGAL or some such
    }

    /** Number of stack slots for the given type.
     *  Two for T_DOUBLE and T_FLOAT, one for the rest.
     */
    private static int type2size(int type) {
        assert(type >= T_BOOLEAN && type <= T_OBJECT);
        return (type == T_FLOAT || type == T_DOUBLE) ? 2 : 1;
    }

    /** Construct an adapter conversion descriptor for a single-argument conversion. */
    private static long makeConv(int convOp, int argnum, int src, int dest) {
        assert(src  == (src  & 0xF));
        assert(dest == (dest & 0xF));
        assert(convOp >= OP_CHECK_CAST && convOp <= OP_PRIM_TO_REF);
        long stackMove = type2size(dest) - type2size(src);
        return ((long) argnum << 32 |
                (long) convOp << CONV_OP_SHIFT |
                (int)  src    << CONV_SRC_TYPE_SHIFT |
                (int)  dest   << CONV_DEST_TYPE_SHIFT |
                stackMove     << CONV_STACK_MOVE_SHIFT
                );
    }
    private static long makeConv(int convOp, int argnum, int stackMove) {
        assert(convOp >= OP_SWAP_ARGS && convOp <= OP_SPREAD_ARGS);
        byte src = 0, dest = 0;
        if (convOp >= OP_COLLECT_ARGS && convOp <= OP_SPREAD_ARGS)
            src = dest = T_OBJECT;
        return ((long) argnum << 32 |
                (long) convOp << CONV_OP_SHIFT |
                (int)  src    << CONV_SRC_TYPE_SHIFT |
                (int)  dest   << CONV_DEST_TYPE_SHIFT |
                stackMove     << CONV_STACK_MOVE_SHIFT
                );
    }
    private static long makeConv(int convOp) {
        assert(convOp == OP_RETYPE_ONLY);
        return (long) convOp << CONV_OP_SHIFT;   // stackMove, src, dst, argnum all zero
    }
    private static int convCode(long conv) {
        return (int)conv;
    }
    private static int convArgPos(long conv) {
        return (int)(conv >>> 32);
    }
    private static boolean convOpSupported(int convOp) {
        assert(convOp >= 0 && convOp <= CONV_OP_LIMIT);
        return ((1<<convOp) & CONV_OP_IMPLEMENTED_MASK) != 0;
    }

    /** One of OP_RETYPE_ONLY, etc. */
    int conversionOp() { return (conversion & CONV_OP_MASK) >> CONV_OP_SHIFT; }

    @Override
    public String toString() {
        return addTypeString(this, "Adapted[" + basicToString(nonAdapter((MethodHandle)vmtarget)) + "]");
    }

    private static MethodHandle nonAdapter(MethodHandle mh) {
        return (MethodHandle)
            MethodHandleNatives.getTarget(mh, ETF_DIRECT_HANDLE);
    }

    /* Return one plus the position of the first non-trivial difference
     * between the given types.  This is not a symmetric operation;
     * we are considering adapting the targetType to adapterType.
     * Trivial differences are those which could be ignored by the JVM
     * without subverting the verifier.  Otherwise, adaptable differences
     * are ones for which we could create an adapter to make the type change.
     * Return zero if there are no differences (other than trivial ones).
     * Return 1+N if N is the only adaptable argument difference.
     * Return the -2-N where N is the first of several adaptable
     * argument differences.
     * Return -1 if there there are differences which are not adaptable.
     */
    private static int diffTypes(MethodType adapterType,
                                 MethodType targetType,
                                 boolean raw) {
        int diff;
        diff = diffReturnTypes(adapterType, targetType, raw);
        if (diff != 0)  return diff;
        int nargs = adapterType.parameterCount();
        if (nargs != targetType.parameterCount())
            return -1;
        diff = diffParamTypes(adapterType, 0, targetType, 0, nargs, raw);
        //System.out.println("diff "+adapterType);
        //System.out.println("  "+diff+" "+targetType);
        return diff;
    }
    private static int diffReturnTypes(MethodType adapterType,
                                       MethodType targetType,
                                       boolean raw) {
        Class<?> src = targetType.returnType();
        Class<?> dst = adapterType.returnType();
        if ((!raw
             ? VerifyType.canPassUnchecked(src, dst)
             : VerifyType.canPassRaw(src, dst)
             ) > 0)
            return 0;  // no significant difference
        if (raw && !src.isPrimitive() && !dst.isPrimitive())
            return 0;  // can force a reference return (very carefully!)
        //if (false)  return 1;  // never adaptable!
        return -1;  // some significant difference
    }
    private static int diffParamTypes(MethodType adapterType, int tstart,
                                      MethodType targetType, int astart,
                                      int nargs, boolean raw) {
        assert(nargs >= 0);
        int res = 0;
        for (int i = 0; i < nargs; i++) {
            Class<?> src  = adapterType.parameterType(tstart+i);
            Class<?> dest = targetType.parameterType(astart+i);
            if ((!raw
                 ? VerifyType.canPassUnchecked(src, dest)
                 : VerifyType.canPassRaw(src, dest)
                ) <= 0) {
                // found a difference; is it the only one so far?
                if (res != 0)
                    return -1-res; // return -2-i for prev. i
                res = 1+i;
            }
        }
        return res;
    }

    /** Can a retyping adapter (alone) validly convert the target to newType? */
    public static boolean canRetypeOnly(MethodType newType, MethodType targetType) {
        return canRetypeOnly(newType, targetType, false);
    }
    /** Can a retyping adapter (alone) convert the target to newType?
     *  It is allowed to widen subword types and void to int, to make bitwise
     *  conversions between float/int and double/long, and to perform unchecked
     *  reference conversions on return.  This last feature requires that the
     *  caller be trusted, and perform explicit cast conversions on return values.
     */
    static boolean canRawRetypeOnly(MethodType newType, MethodType targetType) {
        return canRetypeOnly(newType, targetType, true);
    }
    static boolean canRetypeOnly(MethodType newType, MethodType targetType, boolean raw) {
        if (!convOpSupported(OP_RETYPE_ONLY))  return false;
        int diff = diffTypes(newType, targetType, raw);
        // %%% This assert is too strong.  Factor diff into VerifyType and reconcile.
        assert((diff == 0) == VerifyType.isNullConversion(newType, targetType));
        return diff == 0;
    }

    /** Factory method:  Performs no conversions; simply retypes the adapter.
     *  Allows unchecked argument conversions pairwise, if they are safe.
     *  Returns null if not possible.
     */
    public static MethodHandle makeRetypeOnly(Access token,
                MethodType newType, MethodHandle target) {
        return makeRetypeOnly(token, newType, target, false);
    }
    public static MethodHandle makeRawRetypeOnly(Access token,
                MethodType newType, MethodHandle target) {
        return makeRetypeOnly(token, newType, target, true);
    }
    static MethodHandle makeRetypeOnly(Access token,
                MethodType newType, MethodHandle target, boolean raw) {
        Access.check(token);
        if (!canRetypeOnly(newType, target.type(), raw))
            return null;
        // TO DO:  clone the target guy, whatever he is, with new type.
        return new AdapterMethodHandle(target, newType, makeConv(OP_RETYPE_ONLY));
    }

    /** Can a checkcast adapter validly convert the target to newType?
     *  The JVM supports all kind of reference casts, even silly ones.
     */
    public static boolean canCheckCast(MethodType newType, MethodType targetType,
                int arg, Class<?> castType) {
        if (!convOpSupported(OP_CHECK_CAST))  return false;
        Class<?> src = newType.parameterType(arg);
        Class<?> dst = targetType.parameterType(arg);
        if (!canCheckCast(src, castType)
                || !VerifyType.isNullConversion(castType, dst))
            return false;
        int diff = diffTypes(newType, targetType, false);
        return (diff == arg+1);  // arg is sole non-trivial diff
    }
    /** Can an primitive conversion adapter validly convert src to dst? */
    public static boolean canCheckCast(Class<?> src, Class<?> dst) {
        return (!src.isPrimitive() && !dst.isPrimitive());
    }

    /** Factory method:  Forces a cast at the given argument.
     *  The castType is the target of the cast, and can be any type
     *  with a null conversion to the corresponding target parameter.
     *  Return null if this cannot be done.
     */
    public static MethodHandle makeCheckCast(Access token,
                MethodType newType, MethodHandle target,
                int arg, Class<?> castType) {
        Access.check(token);
        if (!canCheckCast(newType, target.type(), arg, castType))
            return null;
        long conv = makeConv(OP_CHECK_CAST, arg, 0);
        return new AdapterMethodHandle(target, newType, conv, castType);
    }

    /** Can an primitive conversion adapter validly convert the target to newType?
     *  The JVM currently supports all conversions except those between
     *  floating and integral types.
     */
    public static boolean canPrimCast(MethodType newType, MethodType targetType,
                int arg, Class<?> convType) {
        if (!convOpSupported(OP_PRIM_TO_PRIM))  return false;
        Class<?> src = newType.parameterType(arg);
        Class<?> dst = targetType.parameterType(arg);
        if (!canPrimCast(src, convType)
                || !VerifyType.isNullConversion(convType, dst))
            return false;
        int diff = diffTypes(newType, targetType, false);
        return (diff == arg+1);  // arg is sole non-trivial diff
    }
    /** Can an primitive conversion adapter validly convert src to dst? */
    public static boolean canPrimCast(Class<?> src, Class<?> dst) {
        if (src == dst || !src.isPrimitive() || !dst.isPrimitive()) {
            return false;
        } else if (Wrapper.forPrimitiveType(dst).isFloating()) {
            // both must be floating types
            return Wrapper.forPrimitiveType(src).isFloating();
        } else {
            // both are integral, and all combinations work fine
            assert(Wrapper.forPrimitiveType(src).isIntegral() &&
                   Wrapper.forPrimitiveType(dst).isIntegral());
            return true;
        }
    }

    /** Factory method:  Truncate the given argument with zero or sign extension,
     *  and/or convert between single and doubleword versions of integer or float.
     *  The convType is the target of the conversion, and can be any type
     *  with a null conversion to the corresponding target parameter.
     *  Return null if this cannot be done.
     */
    public static MethodHandle makePrimCast(Access token,
                MethodType newType, MethodHandle target,
                int arg, Class<?> convType) {
        Access.check(token);
        MethodType oldType = target.type();
        Class<?> src = newType.parameterType(arg);
        Class<?> dst = oldType.parameterType(arg);
        if (!canPrimCast(newType, oldType, arg, convType))
            return null;
        long conv = makeConv(OP_PRIM_TO_PRIM, arg, basicType(src), basicType(convType));
        return new AdapterMethodHandle(target, newType, conv);
    }

    /** Can an unboxing conversion validly convert src to dst?
     *  The JVM currently supports all kinds of casting and unboxing.
     *  The convType is the unboxed type; it can be either a primitive or wrapper.
     */
    public static boolean canUnboxArgument(MethodType newType, MethodType targetType,
                int arg, Class<?> convType) {
        if (!convOpSupported(OP_REF_TO_PRIM))  return false;
        Class<?> src = newType.parameterType(arg);
        Class<?> dst = targetType.parameterType(arg);
        Class<?> boxType = Wrapper.asWrapperType(convType);
        convType = Wrapper.asPrimitiveType(convType);
        if (!canCheckCast(src, boxType)
                || boxType == convType
                || !VerifyType.isNullConversion(convType, dst))
            return false;
        int diff = diffTypes(newType, targetType, false);
        return (diff == arg+1);  // arg is sole non-trivial diff
    }
    /** Can an primitive unboxing adapter validly convert src to dst? */
    public static boolean canUnboxArgument(Class<?> src, Class<?> dst) {
        return (!src.isPrimitive() && Wrapper.asPrimitiveType(dst).isPrimitive());
    }

    /** Factory method:  Unbox the given argument.
     *  Return null if this cannot be done.
     */
    public static MethodHandle makeUnboxArgument(Access token,
                MethodType newType, MethodHandle target,
                int arg, Class<?> convType) {
        MethodType oldType = target.type();
        Class<?> src = newType.parameterType(arg);
        Class<?> dst = oldType.parameterType(arg);
        Class<?> boxType = Wrapper.asWrapperType(convType);
        Class<?> primType = Wrapper.asPrimitiveType(convType);
        if (!canUnboxArgument(newType, oldType, arg, convType))
            return null;
        MethodType castDone = newType;
        if (!VerifyType.isNullConversion(src, boxType))
            castDone = newType.changeParameterType(arg, boxType);
        long conv = makeConv(OP_REF_TO_PRIM, arg, T_OBJECT, basicType(primType));
        MethodHandle adapter = new AdapterMethodHandle(target, castDone, conv, boxType);
        if (castDone == newType)
            return adapter;
        return makeCheckCast(token, newType, adapter, arg, boxType);
    }

    /** Can an primitive boxing adapter validly convert src to dst? */
    public static boolean canBoxArgument(Class<?> src, Class<?> dst) {
        if (!convOpSupported(OP_PRIM_TO_REF))  return false;
        throw new UnsupportedOperationException("NYI");
    }

    /** Factory method:  Unbox the given argument.
     *  Return null if this cannot be done.
     */
    public static MethodHandle makeBoxArgument(Access token,
                MethodType newType, MethodHandle target,
                int arg, Class<?> convType) {
        // this is difficult to do in the JVM because it must GC
        return null;
    }

    // TO DO: makeSwapArguments, makeRotateArguments, makeDuplicateArguments

    /** Can an adapter simply drop arguments to convert the target to newType? */
    public static boolean canDropArguments(MethodType newType, MethodType targetType,
                int dropArgPos, int dropArgCount) {
        if (dropArgCount == 0)
            return canRetypeOnly(newType, targetType);
        if (!convOpSupported(OP_DROP_ARGS))  return false;
        if (diffReturnTypes(newType, targetType, false) != 0)
            return false;
        int nptypes = newType.parameterCount();
        // parameter types must be the same up to the drop point
        if (dropArgPos != 0 && diffParamTypes(newType, 0, targetType, 0, dropArgPos, false) != 0)
            return false;
        int afterPos = dropArgPos + dropArgCount;
        int afterCount = nptypes - afterPos;
        if (dropArgPos < 0 || dropArgPos >= nptypes ||
            dropArgCount < 1 || afterPos > nptypes ||
            targetType.parameterCount() != nptypes - dropArgCount)
            return false;
        // parameter types after the drop point must also be the same
        if (afterCount != 0 && diffParamTypes(newType, afterPos, targetType, dropArgPos, afterCount, false) != 0)
            return false;
        return true;
    }

    /** Factory method:  Drop selected arguments.
     *  Allow unchecked retyping of remaining arguments, pairwise.
     *  Return null if this is not possible.
     */
    public static MethodHandle makeDropArguments(Access token,
                MethodType newType, MethodHandle target,
                int dropArgPos, int dropArgCount) {
        Access.check(token);
        if (dropArgCount == 0)
            return makeRetypeOnly(IMPL_TOKEN, newType, target);
        MethodType mt = target.type();
        int argCount  = mt.parameterCount();
        if (!canDropArguments(newType, mt, dropArgPos, dropArgCount))
            return null;
        int dropSlotCount, dropSlotPos;
        if (dropArgCount >= argCount) {
            assert(dropArgPos == argCount-1);
            dropSlotPos = 0;
            dropSlotCount = mt.parameterSlotCount();
        } else {
            // arglist: [0: keep... | dpos: drop... | dpos+dcount: keep... ]
            int lastDroppedArg = dropArgPos + dropArgCount - 1;
            int lastKeptArg    = dropArgPos - 1;  // might be -1, which is OK
            dropSlotPos      = mt.parameterSlotDepth(1+lastDroppedArg);
            int lastKeptSlot = mt.parameterSlotDepth(1+lastKeptArg);
            dropSlotCount = lastKeptSlot - dropSlotPos;
            assert(dropSlotCount >= dropArgCount);
        }
        long conv = makeConv(OP_DROP_ARGS, dropArgPos, +dropSlotCount);
        return new AdapterMethodHandle(target, newType, dropSlotCount, conv);
    }

    /** Can an adapter spread an argument to convert the target to newType? */
    public static boolean canSpreadArguments(MethodType newType, MethodType targetType,
                Class<?> spreadArgType, int spreadArgPos, int spreadArgCount) {
        if (!convOpSupported(OP_SPREAD_ARGS))  return false;
        if (diffReturnTypes(newType, targetType, false) != 0)
            return false;
        int nptypes = newType.parameterCount();
        // parameter types must be the same up to the spread point
        if (spreadArgPos != 0 && diffParamTypes(newType, 0, targetType, 0, spreadArgPos, false) != 0)
            return false;
        int afterPos = spreadArgPos + spreadArgCount;
        int afterCount = nptypes - afterPos;
        if (spreadArgPos < 0 || spreadArgPos >= nptypes ||
            spreadArgCount < 0 ||
            targetType.parameterCount() != nptypes - 1 + spreadArgCount)
            return false;
        // parameter types after the spread point must also be the same
        if (afterCount != 0 && diffParamTypes(newType, spreadArgPos+1, targetType, afterPos, afterCount, false) != 0)
            return false;
        // match the array element type to the spread arg types
        Class<?> rawSpreadArgType = newType.parameterType(spreadArgPos);
        if (rawSpreadArgType != spreadArgType && !canCheckCast(rawSpreadArgType, spreadArgType))
            return false;
        for (int i = 0; i < spreadArgCount; i++) {
            Class<?> src = VerifyType.spreadArgElementType(spreadArgType, i);
            Class<?> dst = targetType.parameterType(spreadArgPos + i);
            if (src == null || !VerifyType.isNullConversion(src, dst))
                return false;
        }
        return true;
    }

    /** Factory method:  Spread selected argument. */
    public static MethodHandle makeSpreadArguments(Access token,
                MethodType newType, MethodHandle target,
                Class<?> spreadArgType, int spreadArgPos, int spreadArgCount) {
        Access.check(token);
        MethodType mt = target.type();
        int argCount  = mt.parameterCount();
        if (!canSpreadArguments(newType, mt, spreadArgType, spreadArgPos, spreadArgCount))
            return null;
        int spreadSlotCount, spreadSlotPos;
        if (spreadArgCount >= argCount) {
            assert(spreadArgPos == argCount-1);
            spreadSlotPos = 0;
            spreadSlotCount = mt.parameterSlotCount();
        } else {
            // arglist: [0: keep... | dpos: spread... | dpos+dcount: keep... ]
            int lastSpreadArg = spreadArgPos + spreadArgCount - 1;
            int lastKeptArg   = spreadArgPos - 1;  // might be -1, which is OK
            spreadSlotPos     = mt.parameterSlotDepth(1+lastSpreadArg);
            int lastKeptSlot  = mt.parameterSlotDepth(1+lastKeptArg);
            spreadSlotCount = lastKeptSlot - spreadSlotPos;
            assert(spreadSlotCount >= spreadArgCount);
        }
        long conv = makeConv(OP_SPREAD_ARGS, spreadArgPos, spreadSlotCount);
        return new AdapterMethodHandle(target, newType, conv, spreadArgType);
    }

    // TO DO: makeCollectArguments, makeFlyby, makeRicochet
}
