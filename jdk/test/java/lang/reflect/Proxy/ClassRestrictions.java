/*
 * Copyright (c) 1999, 2010, Oracle and/or its affiliates. All rights reserved.
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

/* @test
 * @bug 4227192
 * @summary This is a test of the restrictions on the parameters that may
 * be passed to the Proxy.getProxyClass method.
 * @author Peter Jones
 *
 * @build ClassRestrictions
 * @run main ClassRestrictions
 */

import java.lang.reflect.Proxy;
import java.net.URLClassLoader;

public class ClassRestrictions {

    public interface Bar {
        int foo();
    }

    public interface Baz {
        long foo();
    }

    interface Bashful {
        void foo();
    }

    public static void main(String[] args) {

        System.err.println(
            "\nTest of restrictions on parameters to Proxy.getProxyClass\n");

        try {
            ClassLoader loader = ClassRestrictions.class.getClassLoader();
            Class<?>[] interfaces;
            Class<?> proxyClass;

            /*
             * All of the Class objects in the interfaces array must represent
             * interfaces, not classes or primitive types.
             */
            try {
                interfaces = new Class<?>[] { Object.class };
                proxyClass = Proxy.getProxyClass(loader, interfaces);
                throw new RuntimeException(
                    "proxy class created with java.lang.Object as interface");
            } catch (IllegalArgumentException e) {
                e.printStackTrace();
                System.err.println();
                // assume exception is for intended failure
            }
            try {
                interfaces = new Class<?>[] { Integer.TYPE };
                proxyClass = Proxy.getProxyClass(loader, interfaces);
                throw new RuntimeException(
                    "proxy class created with int.class as interface");
            } catch (IllegalArgumentException e) {
                e.printStackTrace();
                System.err.println();
                // assume exception is for intended failure
            }

            /*
             * No two elements in the interfaces array may refer to identical
             * Class objects.
             */
            try {
                interfaces = new Class<?>[] { Bar.class, Bar.class };
                proxyClass = Proxy.getProxyClass(loader, interfaces);
                throw new RuntimeException(
                    "proxy class created with repeated interfaces");
            } catch (IllegalArgumentException e) {
                e.printStackTrace();
                System.err.println();
                // assume exception is for intended failure
            }

            /*
             * All of the interfaces types must be visible by name though the
             * specified class loader.
             */
            ClassLoader altLoader = new URLClassLoader(
                ((URLClassLoader) loader).getURLs(), null);
            Class altBarClass;
            altBarClass = Class.forName(Bar.class.getName(), false, altLoader);
            try {
                interfaces = new Class<?>[] { altBarClass };
                proxyClass = Proxy.getProxyClass(loader, interfaces);
                throw new RuntimeException(
                    "proxy class created with interface " +
                    "not visible to class loader");
            } catch (IllegalArgumentException e) {
                e.printStackTrace();
                System.err.println();
                // assume exception is for intended failure
            }

            /*
             * All non-public interfaces must be in the same package.
             */
            Class<?> nonPublic1 = Bashful.class;
            Class<?> nonPublic2 = null;
            String[] nonPublicInterfaces = new String[] {
                "java.awt.Conditional",
                "java.util.zip.ZipConstants",
                "javax.swing.GraphicsWrapper",
                "javax.swing.JPopupMenu$Popup",
                "javax.swing.JTable$Resizable2",
                "javax.swing.JTable$Resizable3",
                "javax.swing.ToolTipManager$Popup",
                "sun.audio.Format",
                "sun.audio.HaePlayable",
                "sun.tools.agent.StepConstants",
            };
            for (int i = 0; i < nonPublicInterfaces.length; i++) {
                try {
                    nonPublic2 = Class.forName(nonPublicInterfaces[i]);
                    break;
                } catch (ClassNotFoundException e) {
                }
            }
            if (nonPublic2 == null) {
                throw new RuntimeException(
                    "no second non-public interface found for test");
            }
            try {
                interfaces = new Class<?>[] { nonPublic1, nonPublic2 };
                proxyClass = Proxy.getProxyClass(loader, interfaces);
                throw new RuntimeException(
                    "proxy class created with two non-public interfaces " +
                    "in different packages");
            } catch (IllegalArgumentException e) {
                e.printStackTrace();
                System.err.println();
                // assume exception is for intended failure
            }

            /*
             * No two interfaces may each have a method with the same name and
             * parameter signature but different return type.
             */
            try {
                interfaces = new Class<?>[] { Bar.class, Baz.class };
                proxyClass = Proxy.getProxyClass(loader, interfaces);
                throw new RuntimeException(
                    "proxy class created with conflicting methods");
            } catch (IllegalArgumentException e) {
                e.printStackTrace();
                System.err.println();
                // assume exception is for intended failure
            }

            /*
             * All components of this test have passed.
             */
            System.err.println("\nTEST PASSED");

        } catch (Exception e) {
            System.err.println("\nTEST FAILED:");
            e.printStackTrace();
            throw new RuntimeException("TEST FAILED: " + e.toString());
        }
    }
}
