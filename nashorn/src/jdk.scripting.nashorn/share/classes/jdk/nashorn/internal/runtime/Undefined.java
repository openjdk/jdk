/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.nashorn.internal.lookup.Lookup.MH;
import static jdk.nashorn.internal.runtime.ECMAErrors.typeError;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import jdk.internal.dynalink.CallSiteDescriptor;
import jdk.internal.dynalink.linker.GuardedInvocation;
import jdk.internal.dynalink.support.CallSiteDescriptorFactory;
import jdk.internal.dynalink.support.Guards;
import jdk.nashorn.internal.runtime.linker.NashornCallSiteDescriptor;

/**
 * Unique instance of this class is used to represent JavaScript undefined.
 */
public final class Undefined extends DefaultPropertyAccess {

    private Undefined() {
    }

    private static final Undefined UNDEFINED = new Undefined();
    private static final Undefined EMPTY     = new Undefined();

    // Guard used for indexed property access/set on the Undefined instance
    private static final MethodHandle UNDEFINED_GUARD = Guards.getIdentityGuard(UNDEFINED);

    /**
     * Get the value of {@code undefined}, this is represented as a global singleton
     * instance of this class. It can always be reference compared
     *
     * @return the undefined object
     */
    public static Undefined getUndefined() {
        return UNDEFINED;
    }

    /**
     * Get the value of {@code empty}. This is represented as a global singleton
     * instanceof this class. It can always be reference compared.
     * <p>
     * We need empty to differentiate behavior in things like array iterators
     * <p>
     * @return the empty object
     */
    public static Undefined getEmpty() {
        return EMPTY;
    }

    /**
     * Get the class name of Undefined
     * @return "Undefined"
     */
    @SuppressWarnings("static-method")
    public String getClassName() {
        return "Undefined";
    }

    @Override
    public String toString() {
        return "undefined";
    }

    /**
     * Lookup the appropriate method for an invoke dynamic call.
     * @param desc The invoke dynamic callsite descriptor.
     * @return GuardedInvocation to be invoked at call site.
     */
    public static GuardedInvocation lookup(final CallSiteDescriptor desc) {
        final String operator = CallSiteDescriptorFactory.tokenizeOperators(desc).get(0);

        switch (operator) {
        case "new":
        case "call":
            throw lookupTypeError("cant.call.undefined", desc);
        case "callMethod":
            throw lookupTypeError("cant.read.property.of.undefined", desc);
        // NOTE: we support getElem and setItem as JavaScript doesn't distinguish items from properties. Nashorn itself
        // emits "dyn:getProp:identifier" for "<expr>.<identifier>" and "dyn:getElem" for "<expr>[<expr>]", but we are
        // more flexible here and dispatch not on operation name (getProp vs. getElem), but rather on whether the
        // operation has an associated name or not.
        case "getProp":
        case "getElem":
        case "getMethod":
            if (desc.getNameTokenCount() < 3) {
                return findGetIndexMethod(desc);
            }
            return findGetMethod(desc);
        case "setProp":
        case "setElem":
            if (desc.getNameTokenCount() < 3) {
                return findSetIndexMethod(desc);
            }
            return findSetMethod(desc);
        default:
            break;
        }

        return null;
    }

    private static ECMAException lookupTypeError(final String msg, final CallSiteDescriptor desc) {
        return typeError(msg, desc.getNameTokenCount() > 2 ? desc.getNameToken(2) : null);
    }

    private static final MethodHandle GET_METHOD = findOwnMH("get", Object.class, Object.class);
    private static final MethodHandle SET_METHOD = MH.insertArguments(findOwnMH("set", void.class, Object.class, Object.class, int.class), 3, NashornCallSiteDescriptor.CALLSITE_STRICT);

    private static GuardedInvocation findGetMethod(final CallSiteDescriptor desc) {
        return new GuardedInvocation(MH.insertArguments(GET_METHOD, 1, desc.getNameToken(2)), UNDEFINED_GUARD).asType(desc);
    }

    private static GuardedInvocation findGetIndexMethod(final CallSiteDescriptor desc) {
        return new GuardedInvocation(GET_METHOD, UNDEFINED_GUARD).asType(desc);
    }

    private static GuardedInvocation findSetMethod(final CallSiteDescriptor desc) {
        return new GuardedInvocation(MH.insertArguments(SET_METHOD, 1, desc.getNameToken(2)), UNDEFINED_GUARD).asType(desc);
    }

    private static GuardedInvocation findSetIndexMethod(final CallSiteDescriptor desc) {
        return new GuardedInvocation(SET_METHOD, UNDEFINED_GUARD).asType(desc);
    }

    @Override
    public Object get(final Object key) {
        throw typeError("cant.read.property.of.undefined", ScriptRuntime.safeToString(key));
    }

    @Override
    public void set(final Object key, final Object value, final int flags) {
        throw typeError("cant.set.property.of.undefined", ScriptRuntime.safeToString(key));
    }

    @Override
    public boolean delete(final Object key, final boolean strict) {
        throw typeError("cant.delete.property.of.undefined", ScriptRuntime.safeToString(key));
    }

    @Override
    public boolean has(final Object key) {
        return false;
    }

    @Override
    public boolean hasOwnProperty(final Object key) {
        return false;
    }

    private static MethodHandle findOwnMH(final String name, final Class<?> rtype, final Class<?>... types) {
        return MH.findVirtual(MethodHandles.lookup(), Undefined.class, name, MH.type(rtype, types));
    }
}
