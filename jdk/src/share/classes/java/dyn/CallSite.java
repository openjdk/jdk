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

import sun.dyn.Access;
import sun.dyn.MemberName;
import sun.dyn.CallSiteImpl;

/**
 * A {@code CallSite} reifies an {@code invokedynamic} instruction from bytecode,
 * and controls its linkage.
 * Every linked {@code CallSite} object corresponds to a distinct instance
 * of the {@code invokedynamic} instruction, and vice versa.
 * <p>
 * Every linked {@code CallSite} object has one state variable,
 * a {@link MethodHandle} reference called the {@code target}.
 * This reference is never null.  Though it can change its value
 * successive values must always have exactly the {@link MethodType method type}
 * called for by the bytecodes of the associated {@code invokedynamic} instruction
 * <p>
 * It is the responsibility of each class's
 * {@link Linkage#registerBootstrapMethod(Class, MethodHandle) bootstrap method}
 * to produce call sites which have been pre-linked to an initial target method.
 * The required {@link MethodType type} for the target method is a parameter
 * to each bootstrap method call.
 * <p>
 * The bootstrap method may elect to produce call sites of a
 * language-specific subclass of {@code CallSite}.  In such a case,
 * the subclass may claim responsibility for initializing its target to
 * a non-null value, by overriding {@link #initialTarget}.
 * <p>
 * An {@code invokedynamic} instruction which has not yet been executed
 * is said to be <em>unlinked</em>.  When an unlinked call site is executed,
 * the containing class's bootstrap method is called to manufacture a call site,
 * for the instruction.  If the bootstrap method does not assign a non-null
 * value to the new call site's target variable, the method {@link #initialTarget}
 * is called to produce the new call site's first target method.
 * <p>
 * A freshly-created {@code CallSite} object is not yet in a linked state.
 * An unlinked {@code CallSite} object reports null for its {@code callerClass}.
 * When the JVM receives a {@code CallSite} object from a bootstrap method,
 * it first ensures that its target is non-null and of the correct type.
 * The JVM then links the {@code CallSite} object to the call site instruction,
 * enabling the {@code callerClass} to return the class in which the instruction occurs.
 * <p>
 * Next, the JVM links the instruction to the {@code CallSite}, at which point
 * any further execution of the {@code invokedynamic} instruction implicitly
 * invokes the current target of the {@code CallSite} object.
 * After this two-way linkage, both the instruction and the {@code CallSite}
 * object are said to be linked.
 * <p>
 * This state of linkage continues until the method containing the
 * dynamic call site is garbage collected, or the dynamic call site
 * is invalidated by an explicit request.
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
private static void printArgs(Object... args) {
  System.out.println(java.util.Arrays.deepToString(args));
}
private static final MethodHandle printArgs;
static {
  MethodHandles.Lookup lookup = MethodHandles.lookup();
  Class thisClass = lookup.lookupClass();  // (who am I?)
  printArgs = lookup.findStatic(thisClass,
      "printArgs", MethodType.methodType(void.class, Object[].class));
  Linkage.registerBootstrapMethod("bootstrapDynamic");
}
private static CallSite bootstrapDynamic(Class caller, String name, MethodType type) {
  // ignore caller and name, but match the type:
  return new CallSite(MethodHandles.collectArguments(printArgs, type));
}
</pre></blockquote>
 * @see Linkage#registerBootstrapMethod(java.lang.Class, java.dyn.MethodHandle)
 * @author John Rose, JSR 292 EG
 */
public class CallSite
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
        StringBuilder buf = new StringBuilder("CallSite#");
        buf.append(hashCode());
        if (!isLinked())
            buf.append("[unlinked]");
        else
            buf.append("[")
                .append("from ").append(vmmethod.getDeclaringClass().getName())
                .append(" : ").append(getTarget().type())
                .append(" => ").append(getTarget())
                .append("]");
        return buf.toString();
    }
}
