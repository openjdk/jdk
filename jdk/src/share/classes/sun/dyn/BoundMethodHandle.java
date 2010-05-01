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
import java.util.List;
import sun.dyn.MethodHandleNatives.Constants;
import static sun.dyn.MethodHandleImpl.IMPL_LOOKUP;
import static sun.dyn.MemberName.newIllegalArgumentException;

/**
 * The flavor of method handle which emulates an invoke instruction
 * on a predetermined argument.  The JVM dispatches to the correct method
 * when the handle is created, not when it is invoked.
 * @author jrose
 */
public class BoundMethodHandle extends MethodHandle {
    //MethodHandle vmtarget;           // next BMH or final DMH or methodOop
    private final Object argument;     // argument to insert
    private final int    vmargslot;    // position at which it is inserted

    private static final Access IMPL_TOKEN = Access.getToken();
    private static final MemberName.Factory IMPL_NAMES = MemberName.getFactory(IMPL_TOKEN);

    // Constructors in this class *must* be package scoped or private.
    // Exception:  JavaMethodHandle constructors are protected.
    // (The link between JMH and BMH is temporary.)

    /** Bind a direct MH to its receiver (or first ref. argument).
     *  The JVM will pre-dispatch the MH if it is not already static.
     */
    BoundMethodHandle(DirectMethodHandle mh, Object argument) {
        super(Access.TOKEN, mh.type().dropParameterTypes(0, 1));
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

    /** Insert an argument into an arbitrary method handle.
     *  If argnum is zero, inserts the first argument, etc.
     *  The argument type must be a reference.
     */
    BoundMethodHandle(MethodHandle mh, Object argument, int argnum) {
        this(mh.type().dropParameterTypes(argnum, argnum+1),
             mh, argument, argnum);
    }

    /** Insert an argument into an arbitrary method handle.
     *  If argnum is zero, inserts the first argument, etc.
     */
    BoundMethodHandle(MethodType type, MethodHandle mh, Object argument, int argnum) {
        super(Access.TOKEN, type);
        if (mh.type().parameterType(argnum).isPrimitive())
            this.argument = bindPrimitiveArgument(argument, mh, argnum);
        else {
            this.argument = checkReferenceArgument(argument, mh, argnum);
        }
        this.vmargslot = type.parameterSlotDepth(argnum);
        initTarget(mh, argnum);
    }

    private void initTarget(MethodHandle mh, int argnum) {
        if (MethodHandleNatives.JVM_SUPPORT) {
            this.vmtarget = null; // maybe updated by JVM
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

    /** Initialize the current object as a Java method handle, binding it
     *  as the first argument of the method handle {@code entryPoint}.
     *  The invocation type of the resulting method handle will be the
     *  same as {@code entryPoint},  except that the first argument
     *  type will be dropped.
     */
    protected BoundMethodHandle(MethodHandle entryPoint) {
        super(Access.TOKEN, entryPoint.type().dropParameterTypes(0, 1));
        this.argument = this; // kludge; get rid of
        this.vmargslot = this.type().parameterSlotDepth(0);
        initTarget(entryPoint, 0);
        assert(this instanceof JavaMethodHandle);
    }

    /** Initialize the current object as a Java method handle.
     */
    protected BoundMethodHandle(String entryPointName, MethodType type, boolean matchArity) {
        super(Access.TOKEN, null);
        MethodHandle entryPoint
                = findJavaMethodHandleEntryPoint(this.getClass(),
                                        entryPointName, type, matchArity);
        MethodHandleImpl.initType(this, entryPoint.type().dropParameterTypes(0, 1));
        this.argument = this; // kludge; get rid of
        this.vmargslot = this.type().parameterSlotDepth(0);
        initTarget(entryPoint, 0);
        assert(this instanceof JavaMethodHandle);
    }

    private static
    MethodHandle findJavaMethodHandleEntryPoint(Class<?> caller,
                                                String name,
                                                MethodType type,
                                                boolean matchArity) {
        if (matchArity)  type.getClass();  // elicit NPE
        List<MemberName> methods = IMPL_NAMES.getMethods(caller, true, name, null, caller);
        MethodType foundType = null;
        MemberName foundMethod = null;
        for (MemberName method : methods) {
            if (method.getDeclaringClass() == MethodHandle.class)
                continue;  // ignore methods inherited from MH class itself
            MethodType mtype = method.getMethodType();
            if (type != null && type.parameterCount() != mtype.parameterCount())
                continue;
            else if (foundType == null)
                foundType = mtype;
            else if (foundType != mtype)
                throw newIllegalArgumentException("more than one method named "+name+" in "+caller.getName());
            // discard overrides
            if (foundMethod == null)
                foundMethod = method;
            else if (foundMethod.getDeclaringClass().isAssignableFrom(method.getDeclaringClass()))
                foundMethod = method;
        }
        if (foundMethod == null)
            throw newIllegalArgumentException("no method named "+name+" in "+caller.getName());
        MethodHandle entryPoint = MethodHandleImpl.findMethod(IMPL_TOKEN, foundMethod, true, caller);
        if (type != null) {
            MethodType epType = type.insertParameterTypes(0, entryPoint.type().parameterType(0));
            entryPoint = MethodHandles.convertArguments(entryPoint, epType);
        }
        return entryPoint;
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
        MethodHandle mh = this;
        while (mh instanceof BoundMethodHandle) {
            Object info = MethodHandleNatives.getTargetInfo(mh);
            if (info instanceof MethodHandle) {
                mh = (MethodHandle) info;
            } else {
                String name = null;
                if (info instanceof MemberName)
                    name = ((MemberName)info).getName();
                if (name != null)
                    return name;
                else
                    return super.toString(); // <unknown>, probably
            }
            assert(mh != this);
            if (mh instanceof JavaMethodHandle)
                break;  // access JMH.toString(), not BMH.toString()
        }
        return mh.toString();
    }
}
