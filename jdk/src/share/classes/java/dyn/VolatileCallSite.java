/*
 * Copyright (c) 2010, Oracle and/or its affiliates. All rights reserved.
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

import java.util.List;

/**
 * <em>PROVISIONAL API, WORK IN PROGRESS:</em>
 * A {@code VolatileCallSite} is a {@link CallSite} whose target acts like a volatile variable.
 * An {@code invokedynamic} instruction linked to a {@code VolatileCallSite} sees updates
 * to its call site target immediately, even if the update occurs in another thread.
 * <p>
 * Also, a volatile call site has the ability to be <em>invalidated</em>,
 * or reset to a well-defined fallback state.
 * <p>
 * A volatile call site can be used as a switch to control the behavior
 * of another method handle.  For example:
 * <blockquote><pre>
MethodHandle strcat = MethodHandles.lookup()
  .findVirtual(String.class, "concat", MethodType.methodType(String.class, String.class));
MethodHandle trueCon  = MethodHandles.constant(boolean.class, true);
MethodHandle falseCon = MethodHandles.constant(boolean.class, false);
VolatileCallSite switcher = new VolatileCallSite(trueCon, falseCon);
// following steps may be repeated to re-use the same switcher:
MethodHandle worker1 = strcat;
MethodHandle worker2 = MethodHandles.permuteArguments(strcat, strcat.type(), 1, 0);
MethodHandle worker = MethodHandles.guardWithTest(switcher.dynamicInvoker(), worker1, worker2);
System.out.println((String) worker.invokeExact("met", "hod"));  // method
switcher.invalidate();
System.out.println((String) worker.invokeExact("met", "hod"));  // hodmet
 * </pre></blockquote>
 * In this case, the fallback path (worker2) does not cause a state change.
 * In a real application, the fallback path could cause call sites to relink
 * themselves in response to a global data structure change.
 * Thus, volatile call sites can be used to build dependency mechanisms.
 * @author John Rose, JSR 292 EG
 */
public class VolatileCallSite extends CallSite {
    volatile MethodHandle fallback;

    /** Create a call site with a volatile target.
     *  The initial target and fallback are both set to a method handle
     *  of the given type which will throw {@code IllegalStateException}.
     */
    public VolatileCallSite(MethodType type) {
        super(type);
        fallback = target;
    }

    /** Create a call site with a volatile target.
     *  The fallback and target are both set to the same initial value.
     */
    public VolatileCallSite(MethodHandle target) {
        super(target);
        fallback = target;
    }

    /** Create a call site with a volatile target.
     *  The fallback and target are set to the given initial values.
     */
    public VolatileCallSite(MethodHandle target, MethodHandle fallback) {
        this(target);
        checkTargetChange(target, fallback);  // make sure they have the same type
        this.fallback = fallback;
    }

    /** Internal override to nominally final getTarget. */
    @Override
    MethodHandle getTarget0() {
        return getTargetVolatile();
    }

    /**
     * Set the target method of this call site, as a volatile variable.
     * Has the same effect as {@link CallSite#setTarget}, with the additional
     * effects associated with volatiles, in the Java Memory Model.
     */
    @Override public void setTarget(MethodHandle newTarget) {
        checkTargetChange(getTargetVolatile(), newTarget);
        setTargetVolatile(newTarget);
    }

    /**
     * Return the fallback target for this call site.
     * It is initialized to the target the call site had when it was constructed,
     * but it may be changed by {@link setFallbackTarget}.
     * <p>
     * Like the regular target of a volatile call site,
     * the fallback target also has the behavior of a volatile variable.
     */
    public MethodHandle getFallbackTarget() {
        return fallback;
    }

    /**
     * Update the fallback target for this call site.
     * @see #getFallbackTarget
     */
    public void setFallbackTarget(MethodHandle newFallbackTarget) {
        checkTargetChange(fallback, newFallbackTarget);
        fallback = newFallbackTarget;
    }

    /**
     * Reset this call site to a known state by changing the target to the fallback target value.
     * Equivalent to {@code setTarget(getFallbackTarget())}.
     */
    public void invalidate() {
        setTargetVolatile(getFallbackTarget());
    }

    /**
     * Reset all call sites in a list by changing the target of each to its fallback value.
     */
    public static void invalidateAll(List<VolatileCallSite> sites) {
        for (VolatileCallSite site : sites) {
            site.invalidate();
        }
    }

}
