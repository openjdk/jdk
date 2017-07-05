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
import java.awt.event.ActionListener;
import java.beans.BeanDescriptor;
import java.beans.BeanInfo;
import java.beans.Introspector;
import java.beans.JavaBean;
/*
 * @test
 * @bug 4058433
 * @summary Tests the JavaBean annotation
 * @author Sergey Malenkov
 */
public class TestJavaBean {
    public static void main(String[] args) throws Exception {
        test(X.class);
        test(D.class);
        test(DP.class);
        test(DES.class);
    }

    private static void test(Class<?> type) throws Exception {
        System.out.println(type);
        BeanInfo info = Introspector.getBeanInfo(type);
        BeanDescriptor bd = info.getBeanDescriptor();

        String description = bd.getShortDescription();
        System.out.println("description = " + description);

        int dp = info.getDefaultPropertyIndex();
        System.out.println("property index = " + dp);
        if (0 <= dp) {
            String name = info.getPropertyDescriptors()[dp].getName();
            System.out.println("property name = " + name);
        }
        int des = info.getDefaultEventIndex();
        System.out.println("event set index = " + des);
        if (0 <= des) {
            String name = info.getPropertyDescriptors()[des].getName();
            System.out.println("event set name = " + name);
        }

        if ((D.class == type) == bd.getName().equals(description)) {
            throw new Error("unexpected description of the bean");
        }
        if ((DP.class == type) == (dp < 0)) {
            throw new Error("unexpected index of the default property");
        }
        if ((DES.class == type) == (des < 0)) {
            throw new Error("unexpected index of the default event set");
        }
    }

    public static class X {
    }

    @JavaBean(description = "description")
    public static class D {
    }

    @JavaBean(defaultProperty = "value")
    public static class DP {
        private int value;

        public int getValue() {
            return this.value;
        }

        public void setValue(int value) {
            this.value = value;
        }
    }

    @JavaBean(defaultEventSet = "action")
    public static class DES {
        public void addActionListener(ActionListener listener) {
        }

        public void removeActionListener(ActionListener listener) {
        }
    }
}
