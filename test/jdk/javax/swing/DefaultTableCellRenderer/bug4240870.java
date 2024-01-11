/*
 * Copyright (c) 1999, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4240870 4240855
 * @summary Tests that DefaultTableCellRenderer overrides following methods:
 *          validate()
 *          revalidate()
 *          repaint(long, int, int, int, int)
 *          repaint(Rectangle)
 *          firePropertyChange(String, Object, Object)
 *          firePropertyChange(String, boolean, boolean)
 */

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import javax.swing.table.DefaultTableCellRenderer;

public class bug4240870 {
    public static void main(String[] argv) {
        // Test overridden public methods using reflection
        String methodName = null;
        try {
            Class clazz = Class.forName(
                    "javax.swing.table.DefaultTableCellRenderer");
            Class[] noArgs = {};
            methodName = "validate";
            clazz.getDeclaredMethod(methodName, noArgs);
            methodName = "revalidate";
            clazz.getDeclaredMethod(methodName, noArgs);

            Class[] args1 = {long.class, int.class, int.class,
                    int.class, int.class};
            methodName = "repaint";
            clazz.getDeclaredMethod(methodName, args1);
            Class[] args2 = {Class.forName("java.awt.Rectangle")};
            methodName = "repaint";
            clazz.getDeclaredMethod(methodName, args2);

            Class objectClass = Class.forName("java.lang.Object");
            Class stringClass = Class.forName("java.lang.String");
            Class[] args3 = {stringClass, objectClass, objectClass};
            methodName = "firePropertyChange";
            clazz.getDeclaredMethod(methodName, args3);
            Class[] args4 = {stringClass, boolean.class, boolean.class};
            clazz.getDeclaredMethod(methodName, args4);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException("Failed: " + methodName + " not overridden");
        } catch (ClassNotFoundException e) {
        }

        // test protected firePropertyChange(String, Object, Object)
        Renderer r = new Renderer();
        r.addPropertyChangeListener(new Listener());
        r.test();
    }

    static class Renderer extends DefaultTableCellRenderer {
        public void test() {
            super.firePropertyChange("text", "old_text", "new_text");
            super.firePropertyChange("stuff", "old_stuff", "new_stuff");
        }
    }

    static class Listener implements PropertyChangeListener {
        public void propertyChange(PropertyChangeEvent e) {
            if (!e.getPropertyName().equals("text")) {
                throw new RuntimeException("Failed: firePropertyChange not overridden");
            }
        }
    }
}
