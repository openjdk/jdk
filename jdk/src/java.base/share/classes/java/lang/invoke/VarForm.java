/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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
package java.lang.invoke;

import java.lang.invoke.VarHandle.AccessMode;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static java.lang.invoke.MethodHandleNatives.Constants.REF_invokeStatic;

/**
 * A var handle form containing a set of member name, one for each operation.
 * Each member characterizes a static method.
 */
class VarForm {

    // Holds VarForm for VarHandle implementation classes
    private static final ClassValue<VarForm> VFORMS
            = new ClassValue<VarForm>() {
        @Override
        protected VarForm computeValue(Class<?> impl) {
            return new VarForm(link(staticMethodLinker(impl)));
        }
    };

    final MemberName mbGet;
    final MemberName mbSet;
    final MemberName mbGetVolatile;
    final MemberName mbSetVolatile;
    final MemberName mbGetAcquire;
    final MemberName mbSetRelease;
    final MemberName mbCompareAndSet;
    final MemberName mbCompareAndExchangeVolatile;
    final MemberName mbCompareAndExchangeAcquire;
    final MemberName mbCompareAndExchangeRelease;
    final MemberName mbWeakCompareAndSet;
    final MemberName mbWeakCompareAndSetAcquire;
    final MemberName mbWeakCompareAndSetRelease;
    final MemberName mbGetAndSet;
    final MemberName mbGetAndAdd;
    final MemberName mbAddAndGet;
    final MemberName mbGetOpaque;
    final MemberName mbSetOpaque;

    VarForm(Map<AccessMode, MemberName> linkMap) {
        mbGet = linkMap.get(AccessMode.get);
        mbSet = linkMap.get(AccessMode.set);
        mbGetVolatile = linkMap.get(AccessMode.getVolatile);
        mbSetVolatile = linkMap.get(AccessMode.setVolatile);
        mbGetOpaque = linkMap.get(AccessMode.getOpaque);
        mbSetOpaque = linkMap.get(AccessMode.setOpaque);
        mbGetAcquire = linkMap.get(AccessMode.getAcquire);
        mbSetRelease = linkMap.get(AccessMode.setRelease);
        mbCompareAndSet = linkMap.get(AccessMode.compareAndSet);
        mbCompareAndExchangeVolatile = linkMap.get(AccessMode.compareAndExchangeVolatile);
        mbCompareAndExchangeAcquire = linkMap.get(AccessMode.compareAndExchangeAcquire);
        mbCompareAndExchangeRelease = linkMap.get(AccessMode.compareAndExchangeRelease);
        mbWeakCompareAndSet = linkMap.get(AccessMode.weakCompareAndSet);
        mbWeakCompareAndSetAcquire = linkMap.get(AccessMode.weakCompareAndSetAcquire);
        mbWeakCompareAndSetRelease = linkMap.get(AccessMode.weakCompareAndSetRelease);
        mbGetAndSet = linkMap.get(AccessMode.getAndSet);
        mbGetAndAdd = linkMap.get(AccessMode.getAndAdd);
        mbAddAndGet = linkMap.get(AccessMode.addAndGet);
    }

    /**
     * Creates a var form given an VarHandle implementation class.
     * Each signature polymorphic method is linked to a static method of the
     * same name on the implementation class or a super class.
     */
    static VarForm createFromStatic(Class<? extends VarHandle> impl) {
        return VFORMS.get(impl);
    }

    /**
     * Link all signature polymorphic methods.
     */
    private static Map<AccessMode, MemberName> link(Function<AccessMode, MemberName> linker) {
        Map<AccessMode, MemberName> links = new HashMap<>();
        for (AccessMode ak : AccessMode.values()) {
            links.put(ak, linker.apply(ak));
        }
        return links;
    }


    /**
     * Returns a function that associates an AccessMode with a MemberName that
     * is a static concrete method implementation for the access operation of
     * the implementing class.
     */
    private static Function<AccessMode, MemberName> staticMethodLinker(Class<?> implClass) {
        // Find all declared static methods on the implementation class and
        // all super classes up to but not including VarHandle
        List<Method> staticMethods = new ArrayList<>(AccessMode.values().length);
        for (Class<?> c = implClass; c != VarHandle.class; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                if (Modifier.isStatic(m.getModifiers())) {
                    staticMethods.add(m);
                }
            }
        }

        // This needs to be an anonymous inner class and not a lambda expression
        // The latter will cause the intialization of classes in java.lang.invoke
        // resulting in circular dependencies if VarHandles are utilized early
        // in the start up process.  For example, if ConcurrentHashMap
        // is modified to use VarHandles.
        return new Function<>() {
            @Override
            public MemberName apply(AccessMode ak) {
                Method m = null;
                for (Method to_m : staticMethods) {
                    if (to_m.getName().equals(ak.name()) &&
                        Modifier.isStatic(to_m.getModifiers())) {
                        assert m == null : String.format(
                                "Two or more static methods named %s are present on " +
                                "class %s or a super class", ak.name(), implClass.getName());
                        m = to_m;
                    }
                }

                if (m == null)
                    return null;

                MemberName linkedMethod = new MemberName(m);
                try {
                    return MemberName.getFactory().resolveOrFail(
                            REF_invokeStatic, linkedMethod, m.getDeclaringClass(), NoSuchMethodException.class);
                }
                catch (ReflectiveOperationException e) {
                    throw new InternalError(e);
                }
            }
        };
    }
}
