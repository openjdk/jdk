/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
import java.beans.BeanDescriptor;
import java.beans.BeanInfo;
import java.beans.Introspector;
import java.util.Objects;
import javax.swing.SwingContainer;
/*
 * @test
 * @bug 4058433
 * @summary Tests the SwingContainer annotation
 * @author Sergey Malenkov
 */
public class TestSwingContainer {
    public static void main(String[] args) throws Exception {
        test(X.class, null, null);
        test(T.class, true, null);
        test(D.class, true, "method");
        test(F.class, false, null);
        test(A.class, false, "method");
    }

    private static void test(Class<?> type, Object iC, Object cD) throws Exception {
        System.out.println(type);
        BeanInfo info = Introspector.getBeanInfo(type);
        BeanDescriptor bd = info.getBeanDescriptor();
        test(bd, "isContainer", iC);
        test(bd, "containerDelegate", cD);
    }

    private static void test(BeanDescriptor bd, String name, Object expected) {
        Object value = bd.getValue(name);
        System.out.println(name + " = " + value);
        if (!Objects.equals(value, expected)) {
            throw new Error(name + ": expected = " + expected + "; actual = " + value);
        }
    }

    public static class X {
    }

    @SwingContainer()
    public static class T {
    }

    @SwingContainer(delegate = "method")
    public static class D {
    }

    @SwingContainer(false)
    public static class F {
    }

    @SwingContainer(value = false, delegate = "method")
    public static class A {
    }
}
