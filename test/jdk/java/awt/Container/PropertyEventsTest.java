/*
 * Copyright (c) 2001, 2023, Oracle and/or its affiliates. All rights reserved.
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
  @test
  @summary unit test for ability of FocusTraversalPolicyProvider
*/

import java.awt.Container;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

public class PropertyEventsTest implements PropertyChangeListener {
    final String PROPERTY = "focusTraversalPolicyProvider";


    public static void main(String[] args) throws Exception {
        new PropertyEventsTest().start();
    }

    public void start () {
        Container c1 = new Container();
        c1.addPropertyChangeListener(PROPERTY, this);

        assertEquals("Container shouldn't be a provider by default",
                     false, c1.isFocusTraversalPolicyProvider());

        prepareForEvent(false, true);
        c1.setFocusTraversalPolicyProvider(true);
        assertEventOccured();
        assertEquals("Policy provider property was not set.",
                     true, c1.isFocusTraversalPolicyProvider());

        prepareForEvent(true, false);
        c1.setFocusTraversalPolicyProvider(false);
        assertEventOccured();
        assertEquals("Policy provider property was not reset.",
                     false, c1.isFocusTraversalPolicyProvider());

        prepareForEvent(false, true);
        c1.setFocusCycleRoot(true);
        assertEventMissed();
        assertEquals("Cycle root shouldn't be a policy provider.",
                     false, c1.isFocusTraversalPolicyProvider());

        prepareForEvent(true, false);
        c1.setFocusCycleRoot(false);
        assertEventMissed();
        assertEquals("setFocusCycleRoot(false) should reset "
                        + "policy provider property.",
                     false, c1.isFocusTraversalPolicyProvider());

        System.out.println("Test passed.");
    }// start()

    void assertEquals(String msg, boolean expected, boolean actual) {
        if (expected != actual) {
            Assert(msg + "(expected=" + expected + ", actual=" + actual + ")");
        }
    }

    void assertEquals(String msg, Object expected, Object actual) {
        if ((expected != null && !expected.equals(actual))
            || (actual != null && !actual.equals(expected)))
        {
            Assert(msg + "(expected=" + expected + ", actual=" + actual + ")");
        }
    }

    void Assert(String msg) {
        throw new RuntimeException(msg);
    }

    void prepareForEvent(boolean old_val, boolean new_val) {
        property_change_fired = false;
        expected_new_value = Boolean.valueOf(new_val);
        expected_old_value = Boolean.valueOf(old_val);
    }

    void assertEventOccured() {
        if (!property_change_fired) {
            Assert("Property Change Event missed.");
        }
    }

    void assertEventMissed() {
        if (property_change_fired) {
            Assert("Unexpected property change event.");
        }
    }

    boolean property_change_fired;
    Boolean expected_new_value;
    Boolean expected_old_value;

    public void propertyChange(PropertyChangeEvent e) {
        System.out.println("PropertyChangeEvent[property=" + e.getPropertyName()
                       + ", new=" + e.getNewValue()
                       + ", old=" + e.getOldValue() + "]");

        assertEquals("Wrong proeprty name.",
                PROPERTY, e.getPropertyName());
        assertEquals("Wrong new value.",
                expected_new_value, e.getNewValue());
        assertEquals("Wrong old value.",
                expected_old_value, e.getOldValue());
        property_change_fired = true;
    }
}
