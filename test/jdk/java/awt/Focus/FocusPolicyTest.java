/*
 * Copyright (c) 2003, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4897459
 * @key headful
 * @summary The <Tab> key does not switches focus in the internal frames in Swing apps.
 * @run main FocusPolicyTest
 */

import java.awt.Container;
import java.awt.Component;
import java.awt.DefaultFocusTraversalPolicy;
import java.awt.Dialog;
import java.awt.FocusTraversalPolicy;
import java.awt.Frame;
import java.awt.KeyboardFocusManager;
import java.awt.Toolkit;
import java.awt.Window;
import javax.swing.JDialog;
import javax.swing.JInternalFrame;
import javax.swing.JFrame;
import javax.swing.JWindow;
import javax.swing.LayoutFocusTraversalPolicy;

public class FocusPolicyTest {
    static int stageNum;
    static FocusTraversalPolicy customPolicy = new CustomPolicy();
    final static Class awtDefaultPolicy = DefaultFocusTraversalPolicy.class;
    final static Class swingDefaultPolicy = LayoutFocusTraversalPolicy.class;

    public static void main(String[] args) {
        final boolean isXawt = "sun.awt.X11.XToolkit".equals(Toolkit.getDefaultToolkit().getClass().getName());

        System.err.println("isXawt = " + isXawt);

        // 1. Check default policy
        if (KeyboardFocusManager.getCurrentKeyboardFocusManager().
                getDefaultFocusTraversalPolicy().getClass() != awtDefaultPolicy) {
            throw new RuntimeException("Error: stage 1: default policy is not DefaultFocusTraversalPolicy");
        }

        // 2. Check AWT top-levels policies
        stageNum = 2;
        checkAWTPoliciesFor(awtDefaultPolicy);

        // 3. Check Swing top-levels policies
        stageNum = 3;
        checkSwingPoliciesFor(swingDefaultPolicy);

        // 4. Check default policy if not XToolkit
        if (!isXawt) {
            if (KeyboardFocusManager.getCurrentKeyboardFocusManager().
                    getDefaultFocusTraversalPolicy().getClass() != swingDefaultPolicy) {
                throw new RuntimeException("Error: stage 4: default policy is not LayoutFocusTraversalPolicy");
            }
        }

        // 5. Check AWT top-levels policies
        // this is a bug in XAWT we should change the test as soon as
        // we will be able to fix this bug.
        stageNum = 5;
        Class defaultPolicy = swingDefaultPolicy;
        if (isXawt) {
            defaultPolicy = awtDefaultPolicy;
        }
        checkAWTPoliciesFor(defaultPolicy);

        // Set custom policy as default
        KeyboardFocusManager.getCurrentKeyboardFocusManager().setDefaultFocusTraversalPolicy(customPolicy);

        // 6. Check AWT top-levels policies for custom
        stageNum = 6;
        checkAWTPoliciesFor(customPolicy.getClass());

        // 7. Check Swing top-levels policies for custom
        stageNum = 7;
        checkSwingPoliciesFor(customPolicy.getClass());

        return;
    }

    public static void checkAWTPoliciesFor(Class expectedPolicyClass) {
        Window[] tlvs = new Window[7];

        tlvs[0] = new Frame("");
        tlvs[1] = new Frame("", tlvs[0].getGraphicsConfiguration());
        tlvs[2] = new Window((Frame)tlvs[0]);
        tlvs[3] = new Dialog((Frame)tlvs[0], "", false);
        tlvs[4] = new Dialog((Frame)tlvs[0], "", false, tlvs[0].getGraphicsConfiguration());
        tlvs[5] = new Dialog((Dialog)tlvs[3], "", false);
        tlvs[6] = new Dialog((Dialog)tlvs[3], "", false, tlvs[0].getGraphicsConfiguration());

        for (int i = 0; i < 7; i++) {
            Class policyClass = tlvs[i].getFocusTraversalPolicy().getClass();
            if (policyClass != expectedPolicyClass) {
                throw new RuntimeException("Error: stage " + stageNum + ": "
                                           + tlvs[i].getClass().getName()
                                           + "'s policy is " + policyClass.getName()
                                           + " but not " + expectedPolicyClass.getName());
            }
        }
    }

    public static void checkSwingPoliciesFor(Class expectedPolicyClass) {
        Container[] tlvs = new Container[12];

        tlvs[0] = new JFrame();
        tlvs[1] = new JFrame(tlvs[0].getGraphicsConfiguration());
        tlvs[2] = new JFrame("");
        tlvs[3] = new JFrame("", tlvs[0].getGraphicsConfiguration());
        tlvs[4] = new JWindow((Frame)tlvs[0]);
        tlvs[5] = new JWindow((Window)tlvs[4]);
        tlvs[6] = new JWindow((Window)tlvs[4], tlvs[0].getGraphicsConfiguration());
        tlvs[7] = new JDialog((Frame)tlvs[0], "", false);
        tlvs[8] = new JDialog((Frame)tlvs[0], "", false, tlvs[0].getGraphicsConfiguration());
        tlvs[9] = new JDialog((Dialog)tlvs[7], "", false);
        tlvs[10] = new JDialog((Dialog)tlvs[7], "", false, tlvs[0].getGraphicsConfiguration());
        tlvs[11] = new JInternalFrame("", false, false, false, false);

        for (int i = 0; i < tlvs.length; i++) {
            Class policyClass = tlvs[i].getFocusTraversalPolicy().getClass();
            if (policyClass != expectedPolicyClass) {
                throw new RuntimeException("Error: stage " + stageNum
                                           + ": " + tlvs[i].getClass().getName()
                                           + "'s policy is " + policyClass.getName() + " but not "
                                           + expectedPolicyClass.getName());
            }
        }
    }

    // Dummy policy.
    static class CustomPolicy extends FocusTraversalPolicy {
        public Component getComponentAfter(Container focusCycleRoot,
                                           Component aComponent) {
            return null;
        }

        public Component getComponentBefore(Container focusCycleRoot,
                                            Component aComponent) {
            return null;
        }

        public Component getFirstComponent(Container focusCycleRoot) {
            return null;
        }

        public Component getLastComponent(Container focusCycleRoot) {
            return null;
        }

        public Component getDefaultComponent(Container focusCycleRoot) {
            return null;
        }
    }
}
