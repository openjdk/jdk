/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
 * @run testng/othervm ChainedReflection
 * @summary Test Method::invoke and Constructor::newInstance chained calls that
 *          should wrap NPE in InvocationTargetException properly
 */

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;
import java.util.Optional;

import org.testng.annotations.Test;

public class ChainedReflection {
    public ChainedReflection() {}

    ChainedReflection(Void dummy) throws ReflectiveOperationException {
        Method m = ChainedReflection.class.getMethod("throwNPE");
        try {
            m.invoke(null);
        } catch (InvocationTargetException e) {
            Throwable t = e.getTargetException();
            if (t instanceof NullPointerException npe) {
                throw npe;
            } else {
                throw new RuntimeException("Test failed (InvocationTargetException didn't wrap NullPointerException)");
            }
        } catch (Throwable t) {
            throw new RuntimeException("Test failed (Unexpected exception)", t);
        }
    }

    public static void throwNPE() {
        throw new NullPointerException();
    }

    /*
     * Wrap a NullPointerException with a new NullPointerException
     */
    public static void npe() throws ReflectiveOperationException {
        NullPointerException cause;
        try {
            Optional.of(null);
            throw new RuntimeException("NPE not thrown");
        } catch (NullPointerException e) {
            cause = e;
        }
        Class<?> c = NullPointerException.class;
        Constructor<?> ctor = c.getConstructor();
        NullPointerException npe = (NullPointerException) ctor.newInstance();
        npe.initCause(cause);
        throw npe;
    }

    /**
     * Tests the target method being invoked via core reflection that
     * throws NullPointerException allocated via Constructor::newInstance.
     * The newly allocated NPE thrown by the target method should be
     * wrapped by InvocationTargetException.
     */
    @Test
    public void methodCallNewInstance() throws ReflectiveOperationException {
        Method m = ChainedReflection.class.getMethod("npe");
        try {
            m.invoke(null);
        } catch (InvocationTargetException e) {
            Throwable t = e.getTargetException();
            if (!(t instanceof NullPointerException)) {
                throw new RuntimeException("Test failed (InvocationTargetException didn't wrap NullPointerException)");
            }
        } catch (Throwable t) {
            throw new RuntimeException("Test failed (Unexpected exception)", t);
        }
    }

    /**
     * Tests a constructor C calling the method "throwNPE" that throws
     * a new instance of NullPointerException.
     * C::newInstance should wrap NullPointerException thrown by
     * Method::invoke on "throwNPE"  by InvocationTargetException.
     */
    @Test
    public void ctorCallMethodInvoke() throws ReflectiveOperationException {
        Constructor<?> ctor = ChainedReflection.class.getDeclaredConstructor(Void.class);
        try {
            ctor.newInstance((Void)null);
        } catch (InvocationTargetException e) {
            Throwable t = e.getTargetException();
            if (!(t instanceof NullPointerException)) {
                throw new RuntimeException("Test failed (InvocationTargetException didn't wrap NullPointerException)");
            }
        } catch (Throwable t) {
            throw new RuntimeException("Test failed (Unexpected exception)", t);
        }
    }
}
