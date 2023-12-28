/*
 * Copyright (c) 2003, 2023, Oracle and/or its affiliates. All rights reserved.
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
  @bug 4467840
  @summary Generate a PropertyChange when KeyboardFocusManager changes
  @key headful
  @run main ChangeKFMTest
*/

import java.awt.BorderLayout;
import java.awt.DefaultKeyboardFocusManager;
import java.awt.EventQueue;
import java.awt.KeyboardFocusManager;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

public class ChangeKFMTest implements PropertyChangeListener {
    static final String CURRENT_PROP_NAME = "managingFocus";
    boolean current_fired;
    boolean not_current_fired;
    KeyboardFocusManager kfm;
    public static void main(String[] args) throws Exception {
        ChangeKFMTest test = new ChangeKFMTest();
        test.start();
    }

    public void start () throws Exception {
        EventQueue.invokeAndWait(() -> {
            kfm = new DefaultKeyboardFocusManager();
            kfm.addPropertyChangeListener(CURRENT_PROP_NAME, this);
            current_fired = false;
            not_current_fired = false;
            KeyboardFocusManager.setCurrentKeyboardFocusManager(kfm);
            if (!current_fired) {
                throw new RuntimeException("Change to current was not fired on KFM instalation");
            }
            if (not_current_fired) {
                throw new RuntimeException("Change to non-current was fired on KFM instalation");
            } else {
                System.out.println("Installation was complete correctly");
            }

            current_fired = false;
            not_current_fired = false;
            KeyboardFocusManager.setCurrentKeyboardFocusManager(null);
            if (!not_current_fired) {
                throw new RuntimeException("Change to non-current was not fired on KFM uninstalation");
            }
            if (current_fired) {
                throw new RuntimeException("Change to current was fired on KFM uninstalation");
            } else {
                System.out.println("Uninstallation was complete correctly");
            }
        });
    }

    public void propertyChange(PropertyChangeEvent e) {
        System.out.println(e.toString());
        if (!CURRENT_PROP_NAME.equals(e.getPropertyName())) {
            throw new RuntimeException("Unexpected property name - " + e.getPropertyName());
        }
        if (((Boolean)e.getNewValue()).booleanValue()) {
            current_fired = true;
        } else {
            not_current_fired = true;
        }
        System.out.println("current_fired = " + current_fired);
        System.out.println("not_current_fired = " + not_current_fired);
    }
}
