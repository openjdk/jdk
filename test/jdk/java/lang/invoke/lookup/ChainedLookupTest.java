/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug 8013527
 * @run testng/othervm ChainedLookupTest
 * @summary Test MethodHandles.lookup method to produce the Lookup object with
 *          proper lookup class if invoked through reflection and method handle.
 */

import java.lang.invoke.*;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.reflect.Method;

import org.testng.annotations.Test;

import static java.lang.invoke.MethodHandles.*;
import static java.lang.invoke.MethodType.*;
import static org.testng.Assert.*;

public class ChainedLookupTest {
    /**
     * Direct call to MethodHandles::lookup
     */
    @Test
    public void staticLookup() throws Throwable {
        Lookup lookup =  lookup();
        test(lookup, "Lookup produced via direct call to MethodHandles.lookup()");
    }

    /**
     * Reflective Method::invoke on MethodHandles::lookup
     */
    @Test
    public void methodInvoke() throws Throwable {
        Method m =  MethodHandles.class.getMethod("lookup");
        Lookup lookup = (Lookup) m.invoke(null);
        test(lookup, "Lookup produced via reflective call");
    }

    /**
     * Invoke Method::invoke on MethodHandles::lookup via MethodHandle
     */
    @Test
    public void methodInvokeViaMethodHandle() throws Throwable {
        Method m =  MethodHandles.class.getMethod("lookup");

        MethodHandle mh = lookup().findVirtual(Method.class, "invoke",
                                               methodType(Object.class, Object.class, Object[].class));
        Lookup lookup = (Lookup)mh.invoke(m, (Object)null, (Object[])null);
        test(lookup, "Lookup produced via Method::invoke via MethodHandle");
    }

    /**
     * Invoke MethodHandle on MethodHandles::lookup via Method::invoke
     */
    // @Test
    public void methodHandleViaReflection() throws Throwable {
        MethodHandle MH_lookup = lookup().findStatic(MethodHandles.class, "lookup", methodType(Lookup.class));
        Lookup lookup = (Lookup) MH_lookup.invokeExact();

        Method m =  MethodHandle.class.getMethod("invoke", Object[].class);

        // should this throw UnsupportedOperationException per MethodHandle::invoke spec?
        lookup = (Lookup)m.invoke(MH_lookup);
        test(lookup, "Lookup produced via MethodHandle::invoke via Method::invoke");
    }

    /**
     * MethodHandle::invokeExact on MethodHandles::lookup
     */
    @Test
    public void methodHandle() throws Throwable {
        MethodHandle MH_lookup = lookup().findStatic(MethodHandles.class, "lookup", methodType(Lookup.class));
        Lookup lookup = (Lookup) MH_lookup.invokeExact();
        test(lookup, "Lookup produced via MethodHandle");
    }

    /**
     * MethodHandles::unreflect the reflective Method representing MethodHandles::lookup
     */
    @Test
    public void unreflect() throws Throwable {
        MethodHandle MH_lookup = lookup().unreflect(MethodHandles.class.getMethod("lookup"));
        Lookup lookup = (Lookup) MH_lookup.invokeExact();
        test(lookup, "Lookup produced via unreflect MethodHandle");
    }

    /**
     * Use the Lookup object returned from MethodHandle::invokeExact on MethodHandles::lookup
     * to look up MethodHandle representing MethodHandles::lookup and then invoking it.
     */
    @Test
    public void chainedMethodHandle() throws Throwable {
        MethodHandle MH_lookup = lookup().findStatic(MethodHandles.class, "lookup", methodType(Lookup.class));
        Lookup lookup = (Lookup) MH_lookup.invokeExact();
        MethodHandle MH_lookup2 = lookup.unreflect(MethodHandles.class.getMethod("lookup"));
        Lookup lookup2 = (Lookup) MH_lookup2.invokeExact();
        test(lookup2, "Lookup produced via a chained call to MethodHandle");
    }

    void test(Lookup lookup, String msg) throws Throwable {
        assertTrue(lookup.lookupClass() == ChainedLookupTest.class);
        assertTrue(lookup.hasFullPrivilegeAccess());

        MethodHandle mh = lookup.findStatic(lookup.lookupClass(), "say", methodType(void.class, String.class));
        mh.invokeExact(msg);
    }

    private static void say(String msg) {
        System.out.println(msg);
    }
}

