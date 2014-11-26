/*
 * Copyright (c) 2010, 2014, Oracle and/or its affiliates. All rights reserved.
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

package jdk.nashorn.internal.runtime;

import static jdk.nashorn.internal.codegen.CompilerConstants.staticCall;
import static jdk.nashorn.internal.codegen.CompilerConstants.virtualCall;
import static jdk.nashorn.internal.lookup.Lookup.MH;
import static jdk.nashorn.internal.runtime.UnwarrantedOptimismException.INVALID_PROGRAM_POINT;
import static jdk.nashorn.internal.runtime.linker.NashornCallSiteDescriptor.getProgramPoint;
import static jdk.nashorn.internal.runtime.logging.DebugLogger.quote;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.SwitchPoint;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import jdk.internal.dynalink.CallSiteDescriptor;
import jdk.internal.dynalink.DynamicLinker;
import jdk.internal.dynalink.linker.GuardedInvocation;
import jdk.internal.dynalink.linker.LinkRequest;
import jdk.nashorn.internal.lookup.Lookup;
import jdk.nashorn.internal.lookup.MethodHandleFactory;
import jdk.nashorn.internal.runtime.linker.NashornCallSiteDescriptor;
import jdk.nashorn.internal.runtime.logging.DebugLogger;
import jdk.nashorn.internal.runtime.logging.Loggable;
import jdk.nashorn.internal.runtime.logging.Logger;

/**
 * Each context owns one of these. This is basically table of accessors
 * for global properties. A global constant is evaluated to a MethodHandle.constant
 * for faster access and to avoid walking to proto chain looking for it.
 *
 * We put a switchpoint on the global setter, which invalidates the
 * method handle constant getters, and reverts to the standard access strategy
 *
 * However, there is a twist - while certain globals like "undefined" and "Math"
 * are usually never reassigned, a global value can be reset once, and never again.
 * This is a rather common pattern, like:
 *
 * x = function(something) { ...
 *
 * Thus everything registered as a global constant gets an extra chance. Set once,
 * reregister the switchpoint. Set twice or more - don't try again forever, or we'd
 * just end up relinking our way into megamorphisism.
 *
 * Also it has to be noted that this kind of linking creates a coupling between a Global
 * and the call sites in compiled code belonging to the Context. For this reason, the
 * linkage becomes incorrect as soon as the Context has more than one Global. The
 * {@link #invalidateForever()} is invoked by the Context to invalidate all linkages and
 * turn off the functionality of this object as soon as the Context's {@link Context#newGlobal()} is invoked
 * for second time.
 *
 * We can extend this to ScriptObjects in general (GLOBAL_ONLY=false), which requires
 * a receiver guard on the constant getter, but it currently leaks memory and its benefits
 * have not yet been investigated property.
 *
 * As long as all Globals in a Context share the same GlobalConstants instance, we need synchronization
 * whenever we access it.
 */
@Logger(name="const")
public final class GlobalConstants implements Loggable {

    /**
     * Should we only try to link globals as constants, and not generic script objects.
     * Script objects require a receiver guard, which is memory intensive, so this is currently
     * disabled. We might implement a weak reference based approach to this later.
     */
    public static final boolean GLOBAL_ONLY = true;

    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();

    private static final MethodHandle INVALIDATE_SP  = virtualCall(LOOKUP, GlobalConstants.class, "invalidateSwitchPoint", Object.class, Object.class, Access.class).methodHandle();
    private static final MethodHandle RECEIVER_GUARD = staticCall(LOOKUP, GlobalConstants.class, "receiverGuard", boolean.class, Access.class, Object.class, Object.class).methodHandle();

    /** Logger for constant getters */
    private final DebugLogger log;

    /**
     * Access map for this global - associates a symbol name with an Access object, with getter
     * and invalidation information
     */
    private final Map<String, Access> map = new HashMap<>();

    private final AtomicBoolean invalidatedForever = new AtomicBoolean(false);

    /**
     * Constructor - used only by global
     * @param log logger, or null if none
     */
    public GlobalConstants(final DebugLogger log) {
        this.log = log == null ? DebugLogger.DISABLED_LOGGER : log;
    }

    @Override
    public DebugLogger getLogger() {
        return log;
    }

    @Override
    public DebugLogger initLogger(final Context context) {
        return DebugLogger.DISABLED_LOGGER;
    }

    /**
     * Information about a constant access and its potential invalidations
     */
    private static class Access {
        /** name of symbol */
        private final String name;

        /** switchpoint that invalidates the getters and setters for this access */
        private SwitchPoint sp;

        /** invalidation count for this access, i.e. how many times has this property been reset */
        private int invalidations;

        /** has a guard guarding this property getter failed? */
        private boolean guardFailed;

        private static final int MAX_RETRIES = 2;

        private Access(final String name, final SwitchPoint sp) {
            this.name      = name;
            this.sp        = sp;
        }

        private boolean hasBeenInvalidated() {
            return sp.hasBeenInvalidated();
        }

        private boolean guardFailed() {
            return guardFailed;
        }

        private void failGuard() {
            invalidateOnce();
            guardFailed = true;
        }

        private void newSwitchPoint() {
            assert hasBeenInvalidated();
            sp = new SwitchPoint();
        }

        private void invalidate(final int count) {
            if (!sp.hasBeenInvalidated()) {
                SwitchPoint.invalidateAll(new SwitchPoint[] { sp });
                invalidations += count;
            }
        }

        /**
         * Invalidate the access, but do not contribute to the invalidation count
         */
        private void invalidateUncounted() {
            invalidate(0);
        }

        /**
         * Invalidate the access, and contribute 1 to the invalidation count
         */
        private void invalidateOnce() {
            invalidate(1);
        }

        /**
         * Invalidate the access and make sure that we never try to turn this into
         * a MethodHandle.constant getter again
         */
        private void invalidateForever() {
            invalidate(MAX_RETRIES);
        }

        /**
         * Are we allowed to relink this as constant getter, even though it
         * it has been reset
         * @return true if we can relink as constant, one retry is allowed
         */
        private boolean mayRetry() {
            return invalidations < MAX_RETRIES;
        }

        @Override
        public String toString() {
            return "[" + quote(name) + " <id=" + Debug.id(this) + "> inv#=" + invalidations + '/' + MAX_RETRIES + " sp_inv=" + sp.hasBeenInvalidated() + ']';
        }

        String getName() {
            return name;
        }

        SwitchPoint getSwitchPoint() {
            return sp;
        }
    }

    /**
     * To avoid an expensive global guard "is this the same global", similar to the
     * receiver guard on the ScriptObject level, we invalidate all getters once
     * when we switch globals. This is used from the class cache. We _can_ reuse
     * the same class for a new global, but the builtins and global scoped variables
     * will have changed.
     */
    public void invalidateAll() {
        if (!invalidatedForever.get()) {
            log.info("New global created - invalidating all constant callsites without increasing invocation count.");
            synchronized (this) {
                for (final Access acc : map.values()) {
                    acc.invalidateUncounted();
                }
            }
        }
    }

    /**
     * To avoid an expensive global guard "is this the same global", similar to the
     * receiver guard on the ScriptObject level, we invalidate all getters when the
     * second Global is created by the Context owning this instance. After this
     * method is invoked, this GlobalConstants instance will both invalidate all the
     * switch points it produced, and it will stop handing out new method handles
     * altogether.
     */
    public void invalidateForever() {
        if (invalidatedForever.compareAndSet(false, true)) {
            log.info("New global created - invalidating all constant callsites.");
            synchronized (this) {
                for (final Access acc : map.values()) {
                    acc.invalidateForever();
                }
                map.clear();
            }
        }
    }

    /**
     * Invalidate the switchpoint of an access - we have written to
     * the property
     *
     * @param obj receiver
     * @param acc access
     *
     * @return receiver, so this can be used as param filter
     */
    @SuppressWarnings("unused")
    private synchronized Object invalidateSwitchPoint(final Object obj, final Access acc) {
        if (log.isEnabled()) {
            log.info("*** Invalidating switchpoint " + acc.getSwitchPoint() + " for receiver=" + obj + " access=" + acc);
        }
        acc.invalidateOnce();
        if (acc.mayRetry()) {
            if (log.isEnabled()) {
                log.info("Retry is allowed for " + acc + "... Creating a new switchpoint.");
            }
            acc.newSwitchPoint();
        } else {
            if (log.isEnabled()) {
                log.info("This was the last time I allowed " + quote(acc.getName()) + " to relink as constant.");
            }
        }
        return obj;
    }

    private Access getOrCreateSwitchPoint(final String name) {
        Access acc = map.get(name);
        if (acc != null) {
            return acc;
        }
        final SwitchPoint sp = new SwitchPoint();
        map.put(name, acc = new Access(name, sp));
        return acc;
    }

    /**
     * Called from script object on property deletion to erase a property
     * that might be linked as MethodHandle.constant and force relink
     * @param name name of property
     */
    void delete(final String name) {
        if (!invalidatedForever.get()) {
            synchronized (this) {
                final Access acc = map.get(name);
                if (acc != null) {
                    acc.invalidateForever();
                }
            }
        }
    }

    /**
     * Receiver guard is used if we extend the global constants to script objects in general.
     * As the property can have different values in different script objects, while Global is
     * by definition a singleton, we need this for ScriptObject constants (currently disabled)
     *
     * TODO: Note - this seems to cause memory leaks. Use weak references? But what is leaking seems
     * to be the Access objects, which isn't the case for Globals. Weird.
     *
     * @param acc            access
     * @param boundReceiver  the receiver bound to the callsite
     * @param receiver       the receiver to check against
     *
     * @return true if this receiver is still the one we bound to the callsite
     */
    @SuppressWarnings("unused")
    private static boolean receiverGuard(final Access acc, final Object boundReceiver, final Object receiver) {
        final boolean id = receiver == boundReceiver;
        if (!id) {
            acc.failGuard();
        }
        return id;
    }

    private static boolean isGlobalSetter(final ScriptObject receiver, final FindProperty find) {
        if (find == null) {
            return receiver.isScope();
        }
        return find.getOwner().isGlobal();
    }

    /**
     * Augment a setter with switchpoint for invalidating its getters, should the setter be called
     *
     * @param find    property lookup
     * @param inv     normal guarded invocation for this setter, as computed by the ScriptObject linker
     * @param desc    callsite descriptor
     * @param request link request
     *
     * @return null if failed to set up constant linkage
     */
    GuardedInvocation findSetMethod(final FindProperty find, final ScriptObject receiver, final GuardedInvocation inv, final CallSiteDescriptor desc, final LinkRequest request) {
        if (invalidatedForever.get() || (GLOBAL_ONLY && !isGlobalSetter(receiver, find))) {
            return null;
        }

        final String name = desc.getNameToken(CallSiteDescriptor.NAME_OPERAND);

        synchronized (this) {
            final Access acc  = getOrCreateSwitchPoint(name);

            if (log.isEnabled()) {
                log.fine("Trying to link constant SETTER ", acc);
            }

            if (!acc.mayRetry() || invalidatedForever.get()) {
                if (log.isEnabled()) {
                    log.fine("*** SET: Giving up on " + quote(name) + " - retry count has exceeded " + DynamicLinker.getLinkedCallSiteLocation());
                }
                return null;
            }

            if (acc.hasBeenInvalidated()) {
                log.info("New chance for " + acc);
                acc.newSwitchPoint();
            }

            assert !acc.hasBeenInvalidated();

            // if we haven't given up on this symbol, add a switchpoint invalidation filter to the receiver parameter
            final MethodHandle target           = inv.getInvocation();
            final Class<?>     receiverType     = target.type().parameterType(0);
            final MethodHandle boundInvalidator = MH.bindTo(INVALIDATE_SP,  this);
            final MethodHandle invalidator      = MH.asType(boundInvalidator, boundInvalidator.type().changeParameterType(0, receiverType).changeReturnType(receiverType));
            final MethodHandle mh               = MH.filterArguments(inv.getInvocation(), 0, MH.insertArguments(invalidator, 1, acc));

            assert inv.getSwitchPoints() == null : Arrays.asList(inv.getSwitchPoints());
            log.info("Linked setter " + quote(name) + " " + acc.getSwitchPoint());
            return new GuardedInvocation(mh, inv.getGuard(), acc.getSwitchPoint(), inv.getException());
        }
    }

    /**
     * Try to reuse constant method handles for getters
     * @param c constant value
     * @return method handle (with dummy receiver) that returns this constant
     */
    public static MethodHandle staticConstantGetter(final Object c) {
        return MH.dropArguments(JSType.unboxConstant(c), 0, Object.class);
    }

    private MethodHandle constantGetter(final Object c) {
        final MethodHandle mh = staticConstantGetter(c);
        if (log.isEnabled()) {
            return MethodHandleFactory.addDebugPrintout(log, Level.FINEST, mh, "getting as constant");
        }
        return mh;
    }

    /**
     * Try to turn a getter into a MethodHandle.constant, if possible
     *
     * @param find      property lookup
     * @param receiver  receiver
     * @param desc      callsite descriptor
     *
     * @return resulting getter, or null if failed to create constant
     */
    GuardedInvocation findGetMethod(final FindProperty find, final ScriptObject receiver, final CallSiteDescriptor desc) {
        // Only use constant getter for fast scope access, because the receiver may change between invocations
        // for slow-scope and non-scope callsites.
        // Also return null for user accessor properties as they may have side effects.
        if (invalidatedForever.get() || !NashornCallSiteDescriptor.isFastScope(desc)
                || (GLOBAL_ONLY && !find.getOwner().isGlobal())
                || find.getProperty() instanceof UserAccessorProperty) {
            return null;
        }

        final boolean  isOptimistic = NashornCallSiteDescriptor.isOptimistic(desc);
        final int      programPoint = isOptimistic ? getProgramPoint(desc) : INVALID_PROGRAM_POINT;
        final Class<?> retType      = desc.getMethodType().returnType();
        final String   name         = desc.getNameToken(CallSiteDescriptor.NAME_OPERAND);

        synchronized (this) {
            final Access acc = getOrCreateSwitchPoint(name);

            log.fine("Starting to look up object value " + name);
            final Object c = find.getObjectValue();

            if (log.isEnabled()) {
                log.fine("Trying to link constant GETTER " + acc + " value = " + c);
            }

            if (acc.hasBeenInvalidated() || acc.guardFailed() || invalidatedForever.get()) {
                if (log.isEnabled()) {
                    log.info("*** GET: Giving up on " + quote(name) + " - retry count has exceeded " + DynamicLinker.getLinkedCallSiteLocation());
                }
                return null;
            }

            final MethodHandle cmh = constantGetter(c);

            MethodHandle mh;
            MethodHandle guard;

            if (isOptimistic) {
                if (JSType.getAccessorTypeIndex(cmh.type().returnType()) <= JSType.getAccessorTypeIndex(retType)) {
                    //widen return type - this is pessimistic, so it will always work
                    mh = MH.asType(cmh, cmh.type().changeReturnType(retType));
                } else {
                    //immediately invalidate - we asked for a too wide constant as a narrower one
                    mh = MH.dropArguments(MH.insertArguments(JSType.THROW_UNWARRANTED.methodHandle(), 0, c, programPoint), 0, Object.class);
                }
            } else {
                //pessimistic return type filter
                mh = Lookup.filterReturnType(cmh, retType);
            }

            if (find.getOwner().isGlobal()) {
                guard = null;
            } else {
                guard = MH.insertArguments(RECEIVER_GUARD, 0, acc, receiver);
            }

            if (log.isEnabled()) {
                log.info("Linked getter " + quote(name) + " as MethodHandle.constant() -> " + c + " " + acc.getSwitchPoint());
                mh = MethodHandleFactory.addDebugPrintout(log, Level.FINE, mh, "get const " + acc);
            }

            return new GuardedInvocation(mh, guard, acc.getSwitchPoint(), null);
        }
    }
}
