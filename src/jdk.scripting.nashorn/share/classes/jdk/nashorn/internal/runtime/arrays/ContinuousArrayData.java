/*
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

package jdk.nashorn.internal.runtime.arrays;

import static jdk.nashorn.internal.codegen.CompilerConstants.staticCall;
import static jdk.nashorn.internal.lookup.Lookup.MH;
import static jdk.nashorn.internal.runtime.JSType.getAccessorTypeIndex;
import static jdk.nashorn.internal.runtime.UnwarrantedOptimismException.INVALID_PROGRAM_POINT;
import static jdk.nashorn.internal.runtime.UnwarrantedOptimismException.isValid;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.SwitchPoint;
import jdk.dynalink.CallSiteDescriptor;
import jdk.dynalink.linker.GuardedInvocation;
import jdk.dynalink.linker.LinkRequest;
import jdk.nashorn.internal.codegen.types.Type;
import jdk.nashorn.internal.lookup.Lookup;
import jdk.nashorn.internal.runtime.ScriptObject;
import jdk.nashorn.internal.runtime.linker.NashornCallSiteDescriptor;
import jdk.nashorn.internal.runtime.logging.Logger;

/**
 * Interface implemented by all arrays that are directly accessible as underlying
 * native arrays
 */
@Logger(name="arrays")
public abstract class ContinuousArrayData extends ArrayData {
    /**
     * Constructor
     * @param length length (elementLength)
     */
    protected ContinuousArrayData(final long length) {
        super(length);
    }

    /**
     * Check if we can put one more element at the end of this continuous
     * array without reallocating, or if we are overwriting an already
     * allocated element
     *
     * @param index index to check
     * @return true if we don't need to do any array reallocation to fit an element at index
     */
    public final boolean hasRoomFor(final int index) {
        return has(index) || (index == length() && ensure(index) == this);
    }

    /**
     * Check if an arraydata is empty
     * @return true if empty
     */
    public boolean isEmpty() {
        return length() == 0L;
    }

    /**
     * Return element getter for a certain type at a certain program point
     * @param returnType   return type
     * @param programPoint program point
     * @return element getter or null if not supported (used to implement slow linkage instead
     *   as fast isn't possible)
     */
    public abstract MethodHandle getElementGetter(final Class<?> returnType, final int programPoint);

    /**
     * Return element getter for a certain type at a certain program point
     * @param elementType element type
     * @return element setter or null if not supported (used to implement slow linkage instead
     *   as fast isn't possible)
     */
    public abstract MethodHandle getElementSetter(final Class<?> elementType);

    /**
     * Version of has that throws a class cast exception if element does not exist
     * used for relinking
     *
     * @param index index to check - currently only int indexes
     * @return index
     */
    protected final int throwHas(final int index) {
        if (!has(index)) {
            throw new ClassCastException();
        }
        return index;
    }

    @Override
    public abstract ContinuousArrayData copy();

    /**
     * Returns the type used to store an element in this array
     * @return element type
     */
    public abstract Class<?> getElementType();

    @Override
    public Type getOptimisticType() {
        return Type.typeFor(getElementType());
    }

    /**
     * Returns the boxed type of the type used to store an element in this array
     * @return element type
     */
    public abstract Class<?> getBoxedElementType();

    /**
     * Get the widest element type of two arrays. This can be done faster in subclasses, but
     * this works for all ContinuousArrayDatas and for where more optimal checks haven't been
     * implemented.
     *
     * @param otherData another ContinuousArrayData
     * @return the widest boxed element type
     */
    public ContinuousArrayData widest(final ContinuousArrayData otherData) {
        final Class<?> elementType = getElementType();
        return Type.widest(elementType, otherData.getElementType()) == elementType ? this : otherData;
    }

    /**
     * Look up a continuous array element getter
     * @param get          getter, sometimes combined with a has check that throws CCE on failure for relink
     * @param returnType   return type
     * @param programPoint program point
     * @return array getter
     */
    protected final MethodHandle getContinuousElementGetter(final MethodHandle get, final Class<?> returnType, final int programPoint) {
        return getContinuousElementGetter(getClass(), get, returnType, programPoint);
    }

    /**
     * Look up a continuous array element setter
     * @param set          setter, sometimes combined with a has check that throws CCE on failure for relink
     * @param returnType   return type
     * @return array setter
     */
    protected final MethodHandle getContinuousElementSetter(final MethodHandle set, final Class<?> returnType) {
        return getContinuousElementSetter(getClass(), set, returnType);
    }

    /**
     * Return element getter for a {@link ContinuousArrayData}
     * @param clazz        clazz for exact type guard
     * @param getHas       has getter
     * @param returnType   return type
     * @param programPoint program point
     * @return method handle for element setter
     */
    protected MethodHandle getContinuousElementGetter(final Class<? extends ContinuousArrayData> clazz, final MethodHandle getHas, final Class<?> returnType, final int programPoint) {
        final boolean isOptimistic = isValid(programPoint);
        final int     fti          = getAccessorTypeIndex(getHas.type().returnType());
        final int     ti           = getAccessorTypeIndex(returnType);
        MethodHandle  mh           = getHas;

        if (isOptimistic) {
            if (ti < fti) {
                mh = MH.insertArguments(ArrayData.THROW_UNWARRANTED.methodHandle(), 1, programPoint);
            }
        }
        mh = MH.asType(mh, mh.type().changeReturnType(returnType).changeParameterType(0, clazz));

        if (!isOptimistic) {
            //for example a & array[17];
            return Lookup.filterReturnType(mh, returnType);
        }
        return mh;
    }

    /**
     * Return element setter for a {@link ContinuousArrayData}
     * @param clazz        class for exact type guard
     * @param setHas       set has guard
     * @param elementType  element type
     * @return method handle for element setter
     */
    protected MethodHandle getContinuousElementSetter(final Class<? extends ContinuousArrayData> clazz, final MethodHandle setHas, final Class<?> elementType) {
        return MH.asType(setHas, setHas.type().changeParameterType(2, elementType).changeParameterType(0, clazz));
    }

    /** Fast access guard - it is impractical for JIT performance reasons to use only CCE asType as guard :-(, also we need
      the null case explicitly, which is the one that CCE doesn't handle */
    protected static final MethodHandle FAST_ACCESS_GUARD =
            MH.dropArguments(
                    staticCall(
                            MethodHandles.lookup(),
                            ContinuousArrayData.class,
                            "guard",
                            boolean.class,
                            Class.class,
                            ScriptObject.class).methodHandle(),
                    2,
                    int.class);

    @SuppressWarnings("unused")
    private static boolean guard(final Class<? extends ContinuousArrayData> clazz, final ScriptObject sobj) {
        return sobj != null && sobj.getArray().getClass() == clazz;
    }

    /**
     * Return a fast linked array getter, or null if we have to dispatch to super class
     * @param desc     descriptor
     * @param request  link request
     * @return invocation or null if needs to be sent to slow relink
     */
    @Override
    public GuardedInvocation findFastGetIndexMethod(final Class<? extends ArrayData> clazz, final CallSiteDescriptor desc, final LinkRequest request) {
        final MethodType callType   = desc.getMethodType();
        final Class<?>   indexType  = callType.parameterType(1);
        final Class<?>   returnType = callType.returnType();

        if (ContinuousArrayData.class.isAssignableFrom(clazz) && indexType == int.class) {
            final Object[] args  = request.getArguments();
            final int      index = (int)args[args.length - 1];

            if (has(index)) {
                final MethodHandle getArray     = ScriptObject.GET_ARRAY.methodHandle();
                final int          programPoint = NashornCallSiteDescriptor.isOptimistic(desc) ? NashornCallSiteDescriptor.getProgramPoint(desc) : INVALID_PROGRAM_POINT;
                MethodHandle       getElement   = getElementGetter(returnType, programPoint);
                if (getElement != null) {
                    getElement = MH.filterArguments(getElement, 0, MH.asType(getArray, getArray.type().changeReturnType(clazz)));
                    final MethodHandle guard = MH.insertArguments(FAST_ACCESS_GUARD, 0, clazz);
                    return new GuardedInvocation(getElement, guard, (SwitchPoint)null, ClassCastException.class);
                }
            }
        }

        return null;
    }

    /**
     * Return a fast linked array setter, or null if we have to dispatch to super class
     * @param desc     descriptor
     * @param request  link request
     * @return invocation or null if needs to be sent to slow relink
     */
    @Override
    public GuardedInvocation findFastSetIndexMethod(final Class<? extends ArrayData> clazz, final CallSiteDescriptor desc, final LinkRequest request) { // array, index, value
        final MethodType callType    = desc.getMethodType();
        final Class<?>   indexType   = callType.parameterType(1);
        final Class<?>   elementType = callType.parameterType(2);

        if (ContinuousArrayData.class.isAssignableFrom(clazz) && indexType == int.class) {
            final Object[]        args  = request.getArguments();
            final int             index = (int)args[args.length - 2];

            if (hasRoomFor(index)) {
                MethodHandle setElement = getElementSetter(elementType); //Z(continuousarraydata, int, int), return true if successful
                if (setElement != null) {
                    //else we are dealing with a wider type than supported by this callsite
                    MethodHandle getArray = ScriptObject.GET_ARRAY.methodHandle();
                    getArray   = MH.asType(getArray, getArray.type().changeReturnType(getClass()));
                    setElement = MH.filterArguments(setElement, 0, getArray);
                    final MethodHandle guard = MH.insertArguments(FAST_ACCESS_GUARD, 0, clazz);
                    return new GuardedInvocation(setElement, guard, (SwitchPoint)null, ClassCastException.class); //CCE if not a scriptObject anymore
                }
            }
        }

        return null;
    }

    /**
     * Specialization - fast push implementation
     * @param arg argument
     * @return new array length
     */
    public double fastPush(final int arg) {
        throw new ClassCastException(String.valueOf(getClass())); //type is wrong, relink
    }

    /**
     * Specialization - fast push implementation
     * @param arg argument
     * @return new array length
     */
    public double fastPush(final long arg) {
        throw new ClassCastException(String.valueOf(getClass())); //type is wrong, relink
    }

    /**
     * Specialization - fast push implementation
     * @param arg argument
     * @return new array length
     */
    public double fastPush(final double arg) {
        throw new ClassCastException(String.valueOf(getClass())); //type is wrong, relink
    }

    /**
     * Specialization - fast push implementation
     * @param arg argument
     * @return new array length
     */
    public double fastPush(final Object arg) {
        throw new ClassCastException(String.valueOf(getClass())); //type is wrong, relink
    }

    /**
     * Specialization - fast pop implementation
     * @return element value
     */
    public int fastPopInt() {
        throw new ClassCastException(String.valueOf(getClass())); //type is wrong, relink
    }

    /**
     * Specialization - fast pop implementation
     * @return element value
     */
    public double fastPopDouble() {
       throw new ClassCastException(String.valueOf(getClass())); //type is wrong, relink
    }

    /**
     * Specialization - fast pop implementation
     * @return element value
     */
    public Object fastPopObject() {
        throw new ClassCastException(String.valueOf(getClass())); //type is wrong, relink
    }

    /**
     * Specialization - fast concat implementation
     * @param otherData data to concat
     * @return new arraydata
     */
    public ContinuousArrayData fastConcat(final ContinuousArrayData otherData) {
        throw new ClassCastException(String.valueOf(getClass()) + " != " + String.valueOf(otherData.getClass()));
    }
}
