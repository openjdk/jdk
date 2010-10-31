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
import sun.dyn.empty.Empty;
import sun.misc.Unsafe;
import java.util.Collection;

/**
 * A {@code CallSite} is a holder for a variable {@link MethodHandle},
 * which is called its {@code target}.
 * An {@code invokedynamic} instruction linked to a {@code CallSite} delegates
 * all calls to the site's current target.
 * <p>
 * If a mutable target is not required, the {@code invokedynamic} instruction
 * should be linked to a {@linkplain ConstantCallSite constant call site}.
 * If a volatile target is required, because updates to the target must be
 * reliably witnessed by other threads, the {@code invokedynamic} instruction
 * should be linked to a {@linkplain VolatileCallSite volatile call site}.
 * <p>
 * A call site may be <em>relinked</em> by changing its target.
 * The new target must have the same {@linkplain MethodHandle#type() type}
 * as the previous target.
 * Thus, though a call site can be relinked to a series of
 * successive targets, it cannot change its type.
 * <p>
 * Here is a sample use of call sites and bootstrap methods which links every
 * dynamic call site to print its arguments:
<blockquote><pre><!-- see indy-demo/src/PrintArgsDemo.java -->
static void test() throws Throwable {
    // THE FOLLOWING LINE IS PSEUDOCODE FOR A JVM INSTRUCTION
    InvokeDynamic[#bootstrapDynamic].baz("baz arg", 2, 3.14);
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
private static CallSite bootstrapDynamic(MethodHandles.Lookup caller, String name, MethodType type) {
  // ignore caller and name, but match the type:
  return new ConstantCallSite(MethodHandles.collectArguments(printArgs, type));
}
</pre></blockquote>
 * @author John Rose, JSR 292 EG
 */
public class CallSite {
    private static final Access IMPL_TOKEN = Access.getToken();

    // Fields used only by the JVM.  Do not use or change.
    private MemberName vmmethod; // supplied by the JVM (ref. to calling method)
    private int        vmindex;  // supplied by the JVM (BCI within calling method)

    // The actual payload of this call site:
    /*package-private*/
    MethodHandle target;

    // Remove this field for PFD and delete deprecated methods:
    private MemberName calleeNameRemoveForPFD;

    /**
     * <em>PROVISIONAL API, WORK IN PROGRESS:</em>
     * Make a blank call site object with the given method type.
     * An initial target method is supplied which will throw
     * an {@link IllegalStateException} if called.
     * <p>
     * Before this {@code CallSite} object is returned from a bootstrap method,
     * it is usually provided with a more useful target method,
     * via a call to {@link CallSite#setTarget(MethodHandle) setTarget}.
     */
    public CallSite(MethodType type) {
        target = MethodHandles.invokers(type).uninitializedCallSite();
    }

    /**
     * Make a blank call site object, possibly equipped with an initial target method handle.
     * @param target the method handle which will be the initial target of the call site
     */
    public CallSite(MethodHandle target) {
        target.type();  // null check
        this.target = target;
    }

    /**
     * Report the type of this call site's target.
     * Although targets may change, the call site's type can never change.
     * The {@code setTarget} method enforces this invariant by refusing any new target that does
     * not have the previous target's type.
     * @return the type of the current target, which is also the type of any future target
     */
    public MethodType type() {
        return target.type();
    }

    /** Called from JVM (or low-level Java code) after the BSM returns the newly created CallSite.
     *  The parameters are JVM-specific.
     */
    void initializeFromJVM(String name,
                           MethodType type,
                           MemberName callerMethod,
                           int        callerBCI) {
        if (this.vmmethod != null) {
            // FIXME
            throw new InvokeDynamicBootstrapError("call site has already been linked to an invokedynamic instruction");
        }
        if (!this.type().equals(type)) {
            throw wrongTargetType(target, type);
        }
        this.vmindex  = callerBCI;
        this.vmmethod = callerMethod;
    }

    /**
     * Report the current linkage state of the call site, a value which may change over time.
     * <p>
     * If a {@code CallSite} object is returned
     * from the bootstrap method of the {@code invokedynamic} instruction,
     * the {@code CallSite} is permanently bound to that instruction.
     * When the {@code invokedynamic} instruction is executed, the target method
     * of its associated call site object is invoked directly.
     * It is as if the instruction calls {@code getTarget} and then
     * calls {@link MethodHandle#invokeExact invokeExact} on the result.
     * <p>
     * Unless specified differently by a subclass,
     * the interactions of {@code getTarget} with memory are the same
     * as of a read from an ordinary variable, such as an array element or a
     * non-volatile, non-final field.
     * <p>
     * In particular, the current thread may choose to reuse the result
     * of a previous read of the target from memory, and may fail to see
     * a recent update to the target by another thread.
     * <p>
     * In a {@linkplain ConstantCallSite constant call site}, the {@code getTarget} method behaves
     * like a read from a {@code final} field of the {@code CallSite}.
     * <p>
     * In a {@linkplain VolatileCallSite volatile call site}, the {@code getTarget} method behaves
     * like a read from a {@code volatile} field of the {@code CallSite}.
     * <p>
     * This method may not be overridden by application code.
     * @return the current linkage state of the call site, its target method handle
     * @see ConstantCallSite
     * @see VolatileCallSite
     * @see #setTarget
     */
    public final MethodHandle getTarget() {
        return getTarget0();
    }

    /**
     * Privileged implementations can override this to force final or volatile semantics on getTarget.
     */
    /*package-private*/
    MethodHandle getTarget0() {
        return target;
    }

    /**
     * Set the target method of this call site.
     * <p>
     * Unless a subclass of CallSite documents otherwise,
     * the interactions of {@code setTarget} with memory are the same
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
     * @throws WrongMethodTypeException if the proposed new target
     *         has a method type that differs from the previous target
     */
    public void setTarget(MethodHandle newTarget) {
        checkTargetChange(this.target, newTarget);
        setTargetNormal(newTarget);
    }

    void checkTargetChange(MethodHandle oldTarget, MethodHandle newTarget) {
        MethodType oldType = oldTarget.type();
        MethodType newType = newTarget.type();  // null check!
        if (!newType.equals(oldType))
            throw wrongTargetType(newTarget, oldType);
    }

    private static WrongMethodTypeException wrongTargetType(MethodHandle target, MethodType type) {
        return new WrongMethodTypeException(String.valueOf(target)+" should be of type "+type);
    }

    /** Produce a printed representation that displays information about this call site
     *  that may be useful to the human reader.
     */
    @Override
    public String toString() {
        return super.toString() + type();
    }

    /**
     * Produce a method handle equivalent to an invokedynamic instruction
     * which has been linked to this call site.
     * <p>If this call site is a {@linkplain ConstantCallSite constant call site},
     * this method simply returns the call site's target, since that will never change.
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
        if (this instanceof ConstantCallSite) {
            return target;  // will not change dynamically
        }
        MethodHandle getTarget = MethodHandleImpl.bindReceiver(IMPL_TOKEN, GET_TARGET, this);
        MethodHandle invoker = MethodHandles.exactInvoker(this.type());
        return MethodHandles.foldArguments(invoker, getTarget);
    }
    private static final MethodHandle GET_TARGET;
    static {
        try {
            GET_TARGET = MethodHandles.Lookup.IMPL_LOOKUP.
                findVirtual(CallSite.class, "getTarget", MethodType.methodType(MethodHandle.class));
        } catch (NoAccessException ignore) {
            throw new InternalError();
        }
    }

    /** This guy is rolled into the default target if a MethodType is supplied to the constructor. */
    /*package-private*/
    static Empty uninitializedCallSite() {
        throw new IllegalStateException("uninitialized call site");
    }

    // unsafe stuff:
    private static final Unsafe unsafe = Unsafe.getUnsafe();
    private static final long TARGET_OFFSET;

    static {
        try {
            TARGET_OFFSET = unsafe.objectFieldOffset(CallSite.class.getDeclaredField("target"));
        } catch (Exception ex) { throw new Error(ex); }
    }

    /*package-private*/
    void setTargetNormal(MethodHandle newTarget) {
        target = newTarget;
        //CallSiteImpl.setCallSiteTarget(IMPL_TOKEN, this, newTarget);
    }
    /*package-private*/
    MethodHandle getTargetVolatile() {
        return (MethodHandle) unsafe.getObjectVolatile(this, TARGET_OFFSET);
    }
    /*package-private*/
    void setTargetVolatile(MethodHandle newTarget) {
        unsafe.putObjectVolatile(this, TARGET_OFFSET, newTarget);
        //CallSiteImpl.setCallSiteTarget(IMPL_TOKEN, this, newTarget);
    }
}
