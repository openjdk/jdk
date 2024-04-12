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

import javax.swing.JButton;
import javax.swing.JRootPane;
import java.lang.reflect.Field;

/*
 * @test
 * @bug 4207333
 * @summary Inadvertant API regression in JRootPane
 * @run main bug4207333
 */

public class bug4207333 {
    public static void main(String[] argv) {
        TestableRootPane rp = new TestableRootPane();
        rp.setDefaultButton(new JButton("Default, eh?"));

        if (!rp.test("defaultPressAction")) {
            throw new RuntimeException("Failed test for bug 4207333");
        }
        if (!rp.test("defaultReleaseAction")) {
            throw new RuntimeException("Failed test for bug 4207333");
        }
        System.out.println("Test Passed!");
    }

    private static class TestableRootPane extends JRootPane {
        public boolean test(String fieldName) {
            boolean result = false;
            try {
                Class superClass = getClass().getSuperclass();
                Field field = superClass.getDeclaredField(fieldName);
                Class fieldClass = field.getType();
                Class actionClass = Class.forName("javax.swing.Action");

                // Is the Field an Action?
                result = actionClass.isAssignableFrom(fieldClass);
            } catch (NoSuchFieldException pe) {
                // Not a bug if the fields are removed since their
                // type was a package private class!
                result = true;
            } catch (Exception iae) {
                System.out.println("Exception " + iae);
            }
            return result;
        }
    }
}
