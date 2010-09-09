/*
 * Copyright (c) 2008, 2010, Oracle and/or its affiliates. All rights reserved.
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

package java.dyn;

import sun.dyn.*;
import java.util.Collection;

/**
 * A {@code CallSite} is a holder for a variable {@link MethodHandle},
 * which is called its {@code target}.
 * Every call to a {@code CallSite} is delegated to the site's current target.
 * <p>
 * A call site is initially created in an <em>unlinked</em> state,
 * which is distinguished by a null target variable.
 * Before the call site may be invoked (and before certain other
 * operations are attempted), the call site must be linked to
 * a non-null target.
 * <p>
 * A call site may be <em>relinked</em> by changing its target.
 * The new target must be non-null and must have the same
 * {@linkplain MethodHandle#type() type}
 * as the previous target.
 * Thus, though a call site can be relinked to a series of
 * successive targets, it cannot change its type.
 * <p>
 * Linkage happens once in the lifetime of any given {@code CallSite} object.
 * Because of call site invalidation, this linkage can be repeated for
 * a single {@code invokedynamic} instruction, with multiple {@code CallSite} objects.
 * When a {@code CallSite} is unlinked from an {@code invokedynamic} instruction,
 * the instruction is reset so that it is no longer associated with
 * the {@code CallSite} object, but the {@code CallSite} does not change
 * state.
 * <p>
 * Here is a sample use of call sites and bootstrap methods which links every
 * dynamic call site to print its arguments:
<blockquote><pre><!-- see indy-demo/src/PrintArgsDemo.java -->
&#064;BootstrapMethod(value=PrintArgsDemo.class, name="bootstrapDynamic")
static void test() throws Throwable {
    InvokeDynamic.baz("baz arg", 2, 3.14);
}
private static void printArgs(Object... args) {
  System.out.println(java.util.Arrays.deepToString(args));
}
private static final MethodHandle printArgs;
static {
  MethodHandles.Lookup lookup = MethodHandles.lookup();
  Class thisClass = lookup.lookupClass();  // (who am I?)
  printArgs = lookup.findStatic(thisClass,
      "printArgs", MethodType.methodType(void.class, Object[].class));
}
private static CallSite bootstrapDynamic(Class caller, String name, MethodType type) {
  // ignore caller and name, but match the type:
  return new CallSite(MethodHandles.collectArguments(printArgs, type));
}
</pre></blockquote>
 * @author John Rose, JSR 292 EG
 */
public class CallSite
    implements MethodHandleProvider
{
    private static final Access IMPL_TOKEN = Access.getToken();

    // Fields used only by the JVM.  Do not use or change.
    private MemberName vmmethod; // supplied by the JVM (ref. to calling method)
    private int        vmindex;  // supplied by the JVM (BCI within calling method)

    // The actual payload of this call site:
    private MethodHandle target;

    // Remove this field for PFD and delete deprecated methods:
    private MemberName calleeNameRemoveForPFD;

    /**
     * Make a blank call site object.
     * Before it is returned from a bootstrap method, this {@code CallSite} object
     * must be provided with
     * a target method via a call to {@link CallSite#setTarget(MethodHandle) setTarget},
     * or by a subclass override of {@link CallSite#initialTarget(Class,String,MethodType) initialTarget}.
     */
    public CallSite() {
    }

    /**
     * Make a blank call site object, possibly equipped with an initial target method handle.
     * The initial target reference may be null, in which case the {@code CallSite} object
     * must be provided with a target method via a call to {@link CallSite#setTarget},
     * or by a subclass override of {@link CallSite#initialTarget}.
     * @param target the method handle which will be the initial target of the call site, or null if there is none yet
     */
    public CallSite(MethodHandle target) {
        this.target = target;
    }

    /** @deprecated transitional form defined in EDR but removed in PFD */
    public CallSite(Class<?> caller, String name, MethodType type) {
        this.calleeNameRemoveForPFD = new MemberName(caller, name, type);
    }
    /** @deprecated transitional form defined in EDR but removed in PFD */
    public Class<?> callerClass() {
        MemberName callee = this.calleeNameRemoveForPFD;
        return callee == null ? null : callee.getDeclaringClass();
    }
    /** @deprecated transitional form defined in EDR but removed in PFD */
    public String name() {
        MemberName callee = this.calleeNameRemoveForPFD;
        return callee == null ? null : callee.getName();
    }
    /** @deprecated transitional form defined in EDR but removed in PFD */
    public MethodType type() {
        MemberName callee = this.calleeNameRemoveForPFD;
        return callee == null ? (target == null ? null : target.type()) : callee.getMethodType();
    }
    /** @deprecated transitional form defined in EDR but removed in PFD */
    protected MethodHandle initialTarget() {
        return initialTarget(callerClass(), name(), type());
    }

    /** Report if the JVM has linked this {@code CallSite} object to a dynamic call site instruction.
     *  Once it is linked, it is never unlinked.
     */
    private boolean isLinked() {
        return vmmethod != null;
    }

    /** Called from JVM (or low-level Java code) after the BSM returns the newly created CallSite.
     *  The parameters are JVM-specific.
     */
    void initializeFromJVM(String name,
                           MethodType type,
                           MemberName callerMethod,
                           int        callerBCI) {
        if (this.isLinked()) {
            throw new InvokeDynamicBootstrapError("call site has already been linked to an invokedynamic instruction");
        }
        MethodHandle target = this.target;
        if (target == null) {
            this.target = target = this.initialTarget(callerMethod.getDeclaringClass(), name, type);
        }
        if (!target.type().equals(type)) {
            throw wrongTargetType(target, type);
        }
        this.vmindex  = callerBCI;
        this.vmmethod = callerMethod;
        assert(this.isLinked());
    }

    /**
     * Just after a call site is created by a bootstrap method handle,
     * if the target has not been initialized by the factory method itself,
     * the method {@code initialTarget} is called to produce an initial
     * non-null target.  (Live call sites must never have null targets.)
     * <p>
     * The arguments are the same as those passed to the bootstrap method.
     * Thus, a bootstrap method is free to ignore the arguments and simply
     * create a "blank" {@code CallSite} object of an appropriate subclass.
     * <p>
     * If the bootstrap method itself does not initialize the call site,
     * this method must be overridden, because it just raises an
     * {@code InvokeDynamicBootstrapError}, which in turn causes the
     * linkage of the {@code invokedynamic} instruction to terminate
     * abnormally.
     * @deprecated transitional form defined in EDR but removed in PFD
     */
    protected MethodHandle initialTarget(Class<?> callerClass, String name, MethodType type) {
        throw new InvokeDynamicBootstrapError("target must be initialized before call site is linked: "+name+type);
    }

    /**
     * Report the current linkage state of the call site.  (This is mutable.)
     * The value may not be null after the {@code CallSite} object is returned
     * from the bootstrap method of the {@code invokedynamic} instruction.
     * When an {@code invokedynamic} instruction is executed, the target method
     * of its associated {@code call site} object is invoked directly,
     * as if via {@link MethodHandle}{@code .invoke}.
     * <p>
     * The interactions of {@code getTarget} with memory are the same
     * as of a read from an ordinary variable, such as an array element or a
     * non-volatile, non-final field.
     * <p>
     * In particular, the current thread may choose to reuse the result
     * of a previous read of the target from memory, and may fail to see
     * a recent update to the target by another thread.
     * @return the current linkage state of the call site
     * @see #setTarget
     */
    public MethodHandle getTarget() {
        return target;
    }

    /**
     * Set the target method of this call site.
     * <p>
     * The interactions of {@code setTarget} with memory are the same
     * as of a write to an ordinary variable, such as an array element or a
     * non-volatile, non-final field.
     * <p>
     * In particular, unrelated threads may fail to see the updated target
     * until they perform a read from memory.
     * Stronger guarantees can be created by putting appropriate operations
     * into the bootstrap method and/or the target methods used
     * at any given call site.
     * @param newTarget the new target
     * @throws NullPointerException if the proposed new target is null
     * @throws WrongMethodTypeException if the call site is linked and the proposed new target
     *         has a method type that differs from the previous target
     */
    public void setTarget(MethodHandle newTarget) {
        MethodType newType = newTarget.type();  // null check!
        MethodHandle oldTarget = this.target;
        if (oldTarget == null) {
            // CallSite is not yet linked.
            assert(!isLinked());
            this.target = newTarget;  // might be null!
            return;
        }
        MethodType oldType = oldTarget.type();
        if (!newTarget.type().equals(oldType))
            throw wrongTargetType(newTarget, oldType);
        if (oldTarget != newTarget)
            CallSiteImpl.setCallSiteTarget(IMPL_TOKEN, this, newTarget);
    }

    private static WrongMethodTypeException wrongTargetType(MethodHandle target, MethodType type) {
        return new WrongMethodTypeException(String.valueOf(target)+target.type()+" should be of type "+type);
    }

    /** Produce a printed representation that displays information about this call site
     *  that may be useful to the human reader.
     */
    @Override
    public String toString() {
        return "CallSite"+(target == null ? "" : target.type());
    }

    /**
     * <em>PROVISIONAL API, WORK IN PROGRESS:</em>
     * Produce a method handle equivalent to an invokedynamic instruction
     * which has been linked to this call site.
     * <p>If this call site is a {@link ConstantCallSite}, this method
     * simply returns the call site's target, since that will not change.
     * <p>Otherwise, this method is equivalent to the following code:
     * <p><blockquote><pre>
     * MethodHandle getTarget, invoker, result;
     * getTarget = MethodHandles.lookup().bind(this, "getTarget", MethodType.methodType(MethodHandle.class));
     * invoker = MethodHandles.exactInvoker(this.type());
     * result = MethodHandles.foldArguments(invoker, getTarget)
     * </pre></blockquote>
     * @return a method handle which always invokes this call site's current target
     */
    public final MethodHandle dynamicInvoker() {
        if (this instanceof ConstantCallSite)
            return getTarget();  // will not change dynamically
        MethodHandle getCSTarget = GET_TARGET;
        if (getCSTarget == null)
            GET_TARGET = getCSTarget = MethodHandles.Lookup.IMPL_LOOKUP.
                findVirtual(CallSite.class, "getTarget", MethodType.methodType(MethodHandle.class));
        MethodHandle getTarget = MethodHandleImpl.bindReceiver(IMPL_TOKEN, getCSTarget, this);
        MethodHandle invoker = MethodHandles.exactInvoker(this.type());
        return MethodHandles.foldArguments(invoker, getTarget);
    }
    private static MethodHandle GET_TARGET = null;  // link this lazily, not eagerly

    /** Implementation of {@link MethodHandleProvider} which returns {@code this.dynamicInvoker()}. */
    public final MethodHandle asMethodHandle() { return dynamicInvoker(); }

    /** Implementation of {@link MethodHandleProvider}, which returns {@code this.dynamicInvoker().asType(type)}. */
    public final MethodHandle asMethodHandle(MethodType type) { return dynamicInvoker().asType(type); }
}
