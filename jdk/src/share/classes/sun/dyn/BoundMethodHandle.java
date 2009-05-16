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

/**
 * The flavor of method handle which emulates an invoke instruction
 * on a predetermined argument.  The JVM dispatches to the correct method
 * when the handle is created, not when it is invoked.
 * @author jrose
 */
public class BoundMethodHandle extends MethodHandle  {
    //MethodHandle vmtarget;           // next BMH or final DMH or methodOop
    private final Object argument;     // argument to insert
    private final int    vmargslot;    // position at which it is inserted

    // Constructors in this class *must* be package scoped or private.

    /** Bind a direct MH to its receiver (or first ref. argument).
     *  The JVM will pre-dispatch the MH if it is not already static.
     */
    BoundMethodHandle(DirectMethodHandle mh, Object argument) {
        super(Access.TOKEN, mh.type().dropParameterType(0));
        // check the type now, once for all:
        this.argument = checkReferenceArgument(argument, mh, 0);
        this.vmargslot = this.type().parameterSlotCount();
        if (MethodHandleNatives.JVM_SUPPORT) {
            this.vmtarget = null;  // maybe updated by JVM
            MethodHandleNatives.init(this, mh, 0);
        } else {
            this.vmtarget = mh;
        }
     }

    private static final int REF_ARG = 0, PRIM_ARG = 1, SELF_ARG = 2;

    /** Insert an argument into an arbitrary method handle.
     *  If argnum is zero, inserts the first argument, etc.
     *  The argument type must be a reference.
     */
    BoundMethodHandle(MethodHandle mh, Object argument, int argnum) {
        this(mh, argument, argnum, mh.type().parameterType(argnum).isPrimitive() ? PRIM_ARG : REF_ARG);
    }

    /** Insert an argument into an arbitrary method handle.
     *  If argnum is zero, inserts the first argument, etc.
     */
    BoundMethodHandle(MethodHandle mh, Object argument, int argnum, int whichArg) {
        super(Access.TOKEN, mh.type().dropParameterType(argnum));
        if (whichArg == PRIM_ARG)
            this.argument = bindPrimitiveArgument(argument, mh, argnum);
        else {
            if (whichArg == SELF_ARG)  argument = this;
            this.argument = checkReferenceArgument(argument, mh, argnum);
        }
        this.vmargslot = this.type().parameterSlotDepth(argnum);
        if (MethodHandleNatives.JVM_SUPPORT) {
            this.vmtarget = null;  // maybe updated by JVM
            MethodHandleNatives.init(this, mh, argnum);
        } else {
            this.vmtarget = mh;
        }
    }

    /** For the AdapterMethodHandle subclass.
     */
    BoundMethodHandle(MethodType type, Object argument, int vmargslot) {
        super(Access.TOKEN, type);
        this.argument = argument;
        this.vmargslot = vmargslot;
        assert(this.getClass() == AdapterMethodHandle.class);
    }

    /** Initialize the current object as a method handle, binding it
     *  as the {@code argnum}th argument of the method handle {@code entryPoint}.
     *  The invocation type of the resulting method handle will be the
     *  same as {@code entryPoint},  except that the {@code argnum}th argument
     *  type will be dropped.
     */
    public BoundMethodHandle(MethodHandle entryPoint, int argnum) {
        this(entryPoint, null, argnum, SELF_ARG);

        // Note:  If the conversion fails, perhaps because of a bad entryPoint,
        // the MethodHandle.type field will not be filled in, and therefore
        // no MH.invoke call will ever succeed.  The caller may retain a pointer
        // to the broken method handle, but no harm can be done with it.
    }

    /** Initialize the current object as a method handle, binding it
     *  as the first argument of the method handle {@code entryPoint}.
     *  The invocation type of the resulting method handle will be the
     *  same as {@code entryPoint},  except that the first argument
     *  type will be dropped.
     */
    public BoundMethodHandle(MethodHandle entryPoint) {
        this(entryPoint, null, 0, SELF_ARG);
    }

    /** Make sure the given {@code argument} can be used as {@code argnum}-th
     *  parameter of the given method handle {@code mh}, which must be a reference.
     *  <p>
     *  If this fails, throw a suitable {@code WrongMethodTypeException},
     *  which will prevent the creation of an illegally typed bound
     *  method handle.
     */
    final static Object checkReferenceArgument(Object argument, MethodHandle mh, int argnum) {
        Class<?> ptype = mh.type().parameterType(argnum);
        if (ptype.isPrimitive()) {
            // fail
        } else if (argument == null) {
            return null;
        } else if (VerifyType.isNullReferenceConversion(argument.getClass(), ptype)) {
            return argument;
        }
        throw badBoundArgumentException(argument, mh, argnum);
    }

    /** Make sure the given {@code argument} can be used as {@code argnum}-th
     *  parameter of the given method handle {@code mh}, which must be a primitive.
     *  <p>
     *  If this fails, throw a suitable {@code WrongMethodTypeException},
     *  which will prevent the creation of an illegally typed bound
     *  method handle.
     */
    final static Object bindPrimitiveArgument(Object argument, MethodHandle mh, int argnum) {
        Class<?> ptype = mh.type().parameterType(argnum);
        Wrapper  wrap = Wrapper.forPrimitiveType(ptype);
        Object   zero  = wrap.zero();
        if (zero == null) {
            // fail
        } else if (argument == null) {
            if (ptype != int.class && wrap.isSubwordOrInt())
                return Integer.valueOf(0);
            else
                return zero;
        } else if (VerifyType.isNullReferenceConversion(argument.getClass(), zero.getClass())) {
            if (ptype != int.class && wrap.isSubwordOrInt())
                return Wrapper.INT.wrap(argument);
            else
                return argument;
        }
        throw badBoundArgumentException(argument, mh, argnum);
    }

    final static RuntimeException badBoundArgumentException(Object argument, MethodHandle mh, int argnum) {
        String atype = (argument == null) ? "null" : argument.getClass().toString();
        return new WrongMethodTypeException("cannot bind "+atype+" argument to parameter #"+argnum+" of "+mh.type());
    }

    @Override
    public String toString() {
        return "Bound[" + super.toString() + "]";
    }
}
