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
  @bug 4457455
  @summary Component and KeyboardFocusManager use wrong names of the properties
  @run main TraversalKeysPropertyNamesTest
*/

import java.awt.AWTKeyStroke;
import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.EventQueue;
import java.awt.KeyboardFocusManager;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.HashSet;

public class TraversalKeysPropertyNamesTest implements PropertyChangeListener {
    final String[] properties = {
        "forwardDefaultFocusTraversalKeys",
        "backwardDefaultFocusTraversalKeys",
        "upCycleDefaultFocusTraversalKeys",
        "downCycleDefaultFocusTraversalKeys",
        "forwardFocusTraversalKeys",
        "backwardFocusTraversalKeys",
        "upCycleFocusTraversalKeys",
        "downCycleFocusTraversalKeys"
    };
    final int PROPERTIES_COUNT = properties.length;
    boolean[] flags = new boolean[PROPERTIES_COUNT];

    public static void main(String[] args) throws Exception {
        TraversalKeysPropertyNamesTest test = new TraversalKeysPropertyNamesTest();
        test.start();
    }

    public void start() throws Exception {
        EventQueue.invokeAndWait(() -> {
            Container cont = new Container() {};
            HashSet forwardKeys = new HashSet();
            forwardKeys.add(AWTKeyStroke.getAWTKeyStroke("ctrl A"));
            HashSet backwardKeys = new HashSet();
            backwardKeys.add(AWTKeyStroke.getAWTKeyStroke("ctrl B"));
            HashSet upKeys = new HashSet();
            upKeys.add(AWTKeyStroke.getAWTKeyStroke("ctrl C"));
            HashSet downKeys = new HashSet();
            downKeys.add(AWTKeyStroke.getAWTKeyStroke("ctrl D"));

            KeyboardFocusManager manager =
                KeyboardFocusManager.getCurrentKeyboardFocusManager();
            manager.addPropertyChangeListener(this);
            manager.setDefaultFocusTraversalKeys(
                   KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS, backwardKeys);
            manager.setDefaultFocusTraversalKeys(
                   KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS, forwardKeys);
            manager.setDefaultFocusTraversalKeys(
                   KeyboardFocusManager.DOWN_CYCLE_TRAVERSAL_KEYS, downKeys);
            manager.setDefaultFocusTraversalKeys(
                   KeyboardFocusManager.UP_CYCLE_TRAVERSAL_KEYS, upKeys);

            cont.addPropertyChangeListener(this);
            cont.setFocusTraversalKeys(KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS, backwardKeys);
            cont.setFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS, forwardKeys);
            cont.setFocusTraversalKeys(KeyboardFocusManager.DOWN_CYCLE_TRAVERSAL_KEYS, downKeys);
            cont.setFocusTraversalKeys(KeyboardFocusManager.UP_CYCLE_TRAVERSAL_KEYS, upKeys);

            for (int i = 0; i < PROPERTIES_COUNT; i++) {
                if (!flags[i]) {
                    throw new RuntimeException(
                        "Notification on "+properties[i]+" change was not received");
                }
            }
        });
    }// start()

    public void propertyChange(PropertyChangeEvent pce) {
        String property = pce.getPropertyName();
        System.err.println(property);
        int index;
        for (index = 0; index < PROPERTIES_COUNT; index++) {
            if (property.equals(properties[index])) {
                break;
            }
        }

        if (index < PROPERTIES_COUNT) {
            flags[index] = true;
        }
    }
 }// class TraversalKeysPropertyNamesTest
