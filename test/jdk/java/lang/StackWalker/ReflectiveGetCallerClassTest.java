/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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
 *
 */

/*
 * @test
 * @bug 8311500
 * @summary StackWalker.getCallerClass() can throw if invoked reflectively
 * @run main/othervm ReflectiveGetCallerClassTest
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+ShowHiddenFrames ReflectiveGetCallerClassTest
 */

import java.lang.reflect.Method;
import java.util.List;
import java.util.ArrayList;

public class ReflectiveGetCallerClassTest {
    private static StackWalker WALKER = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
    private static Method gcc, inv;
    static {
        try {
            inv = Method.class.getDeclaredMethod("invoke", Object.class, Object[].class);
            gcc = ReflectiveGetCallerClassTest.class.getDeclaredMethod("getCallerClass");
        } catch (SecurityException se) {
            // This test can't run if a security manager prohibits "getStackWalkerWithClassReference"
            System.err.println(se);
            System.exit(0);
        } catch (Exception e) {
            System.err.println(e);
            System.exit(1);
        }
    }

    public static void getCallerClass() {
        System.out.println(WALKER.getCallerClass());
    }

    // Create a list of Object[] of the form:
    //   { m, first }
    //   { m, { m, first } }
    //   { m, { m, { m, first } } }
    static List<Object[]> prepareArgs(Object[] first, Method m, int depth) {
        List<Object[]> l = new ArrayList<Object[]>(depth + 1);
        l.add(first);
        while (depth-- > 0) {
            l.add(new Object[] { m, l.get(l.size() - 1) });
        }
        return l;
    }

    public static void main(String[] args) throws Throwable {
        // gcc is ReflectiveGetCallerClassTest::getCallerClass()
        // inv is Method::invoke()
        for (Object[] params : prepareArgs(new Object[] { gcc, new Object[] { null, null } }, inv, 10)) {
            inv.invoke(inv, inv, params);
        }
    }
}
