/*
 * Copyright (c) 2010, 2015, Oracle and/or its affiliates. All rights reserved.
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

package jdk.nashorn.internal.objects.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.invoke.MethodHandle;
import jdk.dynalink.CallSiteDescriptor;
import jdk.dynalink.linker.LinkRequest;
import jdk.nashorn.internal.runtime.ScriptFunction;
import jdk.nashorn.internal.runtime.UnwarrantedOptimismException;

/**
 * The SpecializedFunction annotation is used to flag more type specific
 * functions than the standard one in the native objects.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface SpecializedFunction {

    /**
     * Functionality for testing if we are allowed to link a specialized
     * function the first time we encounter it. Then the guard will handle the
     * rest of the invocations.
     *
     * This is the same for all callsites in Nashorn; the first time a callsite is
     * linked, we have to manually check that the linkage is OK. Even if we add
     * a guard and it fails upon the first try, this is not good enough.
     * (Symmetrical to how it works everywhere else in the Nashorn runtime.)
     *
     * Here we abstract out a few of the most common link guard checks.
     */
    public static abstract class LinkLogic {
        /**
         * Empty link logic instance - this is the default.
         * "no special linking or runtime guard behavior"
         */
        public static final LinkLogic EMPTY_INSTANCE = new Empty();

        /** Empty link logic class - allow all linking, no guards. */
        private static final class Empty extends LinkLogic {
            @Override
            public boolean canLink(final Object self, final CallSiteDescriptor desc, final LinkRequest request) {
                return true;
            }

            @Override
            public boolean isEmpty() {
                return true;
            }
        }

        /**
         * Get the class representing the empty link logic.
         *
         * @return class representing empty link logic
         */
        public static Class<? extends LinkLogic> getEmptyLinkLogicClass() {
            return Empty.class;
        }

        /**
         * Should this callsite relink when an exception is thrown?
         *
         * @return the relink exception, or {@code null} if none
         */
        public Class<? extends Throwable> getRelinkException() {
            return null;
        }

        /**
         * Is this link logic class empty - i.e., no special linking logic
         * supplied?
         *
         * @param clazz class to check
         *
         * @return {@code true} if this link logic is empty
         */
        public static boolean isEmpty(final Class<? extends LinkLogic> clazz) {
            return clazz == Empty.class;
        }

        /**
         * Is this link logic instance empty - i.e., no special linking logic
         * supplied?
         *
         * @return {@code true} if this link logic instance is empty
         */
        public boolean isEmpty() {
            return false;
        }

        /**
         * Given a callsite, can we link this method based on the receiver and
         * parameters?
         *
         * @param self    receiver
         * @param desc    callsite descriptor
         * @param request link request
         *
         * @return {@code true} if we can link this callsite at this time
         */
        public abstract boolean canLink(final Object self, final CallSiteDescriptor desc, final LinkRequest request);

        /**
         * Given a callsite, do we require an extra guard for specialization to
         * go through?
         *
         * @param self receiver
         *
         * @return {@code true} if a guard is to be woven into the callsite
         */
        public boolean needsGuard(final Object self) {
            return true;
        }

        /**
         * Given a callsite, and optional arguments, do we need an extra guard
         * for specialization to go through? This guard can be a function of
         * the arguments too.
         *
         * @param self receiver
         * @param args arguments
         *
         * @return {@code true} if a guard is to be woven into the callsite
         */
        public boolean needsGuard(final Object self, final Object... args) {
            return true;
        }

        /**
         * Given a callsite, and optional arguments, return any extra guard we
         * might need for specialization as a method handle.
         *
         * @return methodhandle for guard, or null if no guard is needed
         */
        public MethodHandle getGuard() {
            return null;
        }

        /**
         * Check, given a link request and a receiver, if this specialization
         * fits This is used by the linker in {@link ScriptFunction} to figure
         * out if an optimistic builtin can be linked when first discovered
         *
         * @param self receiver
         * @param desc callsite descriptor
         * @param request link request
         *
         * @return {@code true} if we can link, {@code false} otherwise - that
         *         means we have to pick a non specialized target
         */
        public boolean checkLinkable(final Object self, final CallSiteDescriptor desc, final LinkRequest request) {
            // check the link guard, if it says we can link, go ahead
            return canLink(self, desc, request);
        }
    }

    /**
     * Name override for return value polymorphism, for example we can't have
     * pop(V)I and pop(V)D in the same Java class, so they need to be named,
     * e.g., popInt(V)I and popDouble(V)D for disambiguation, however, their
     * names still need to resolve to "pop" to JavaScript so we can still
     * specialize on return values and so that the linker can find them.
     *
     * @return name, "" means no override, use the Java function name, e.g.
     *         "push"
     */
    String name() default "";

    /**
     * Return the guard for this specialized function. The default is no guard.
     *
     * @return guard
     */
    Class<?> linkLogic() default LinkLogic.Empty.class;

    /**
     * @return whether this is a specialized constructor.
     */
    boolean isConstructor() default false;

    /**
     * Can this function throw {@link UnwarrantedOptimismException}s? This works
     * just like the normal functions, but we need the function to be
     * immutable/non-state modifying, as we can't generate continuations for
     * native code. Luckily a lot of the methods we want to specialize have this
     * property.
     *
     * @return whether this function can throw {@link UnwarrantedOptimismException}.
     */
    boolean isOptimistic() default false;
}
