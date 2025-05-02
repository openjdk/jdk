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
  @key headful
*/

import java.awt.Button;
import java.awt.Component;
import java.awt.Container;
import java.awt.ContainerOrderFocusTraversalPolicy;
import java.awt.DefaultFocusTraversalPolicy;
import java.awt.EventQueue;
import java.awt.FocusTraversalPolicy;
import java.awt.Frame;
import java.awt.GridLayout;
import java.awt.Panel;
import java.awt.Robot;
import java.awt.Window;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.LayoutFocusTraversalPolicy;

public class FocusTraversalPolicyProviderTest {
    final String errorOrderMessage = "Test Failed. Traversal Order not correct.";
    final String successStage = "Test stage completed.Passed.";

    final int n_buttons = 4;
    final int jumps = 3 * n_buttons;
    Container[] cycle_roots = new Container[3];
    Panel[] a_conts = new Panel[cycle_roots.length];
    Panel[] b_conts = new Panel[cycle_roots.length];
    Component[][] a_buttons = new Component[cycle_roots.length][n_buttons];
    Component[][] b_buttons = new Component[cycle_roots.length][n_buttons];

    static volatile Frame mainFrame = null;
    static volatile Frame frame = null;
    static volatile JFrame jframe = null;

    static Robot robot;

    public static void main(String[] args) throws Exception {
        FocusTraversalPolicyProviderTest test
                = new FocusTraversalPolicyProviderTest();
        try {
            robot = new Robot();
            EventQueue.invokeAndWait(test::init);
            robot.delay(1000);
            EventQueue.invokeAndWait(test::testStages);

            EventQueue.invokeAndWait(test::initSwingContInFrame);
            robot.delay(1000);
            EventQueue.invokeAndWait(test::testSwingContInFrame);
            // test for Swing container in java.awt.Frame
            System.out.println("Test passed.");
        } finally {
            EventQueue.invokeAndWait(() -> {
                if (mainFrame != null) mainFrame.dispose();
                if (frame != null) frame.dispose();
                if (jframe != null) jframe.dispose();
            });
        }
    }

    public void init() {
        mainFrame = new Frame("FocusTraversalPolicyProviderTest - main");
        mainFrame.setSize(400, 400);
        mainFrame.setLocationRelativeTo(null);
        mainFrame.setVisible(true);

        for (int i = 0; i < cycle_roots.length; i++) {
            cycle_roots[i] = new Panel();
            cycle_roots[i].setFocusable(false);
            cycle_roots[i].setName("root" + i);
            cycle_roots[i].setFocusCycleRoot(true);
            cycle_roots[i].setLayout (new GridLayout(1, 2));
            mainFrame.add(cycle_roots[i]);

            a_conts[i] = new Panel();
            a_conts[i].setName("ac" + i);
            a_conts[i].setFocusable(false);
            cycle_roots[i].add(a_conts[i]);

            b_conts[i] = new Panel();
            b_conts[i].setName("bc" + i);
            b_conts[i].setFocusable(false);
            cycle_roots[i].add(b_conts[i]);

            for (int j = 0; j < n_buttons; j++){
                String name = "a" + i + "x" + j;
                a_buttons[i][j] = new Button(name);
                a_buttons[i][j].setName(name);
                a_conts[i].add(a_buttons[i][j]);
            }

            for (int j = 0; j < n_buttons; j++){
                String name = "b" + i + "x" + j;
                b_buttons[i][j] = new Button(name);
                b_buttons[i][j].setName(name);
                b_conts[i].add(b_buttons[i][j]);
            }
        }

        cycle_roots[0].setFocusTraversalPolicy(new DefaultFocusTraversalPolicy());
        cycle_roots[1].setFocusTraversalPolicy(new ContainerOrderFocusTraversalPolicy());
        cycle_roots[2].setFocusTraversalPolicy(new LayoutFocusTraversalPolicy());
    }

    public void testStages() {
        for (int i = 0; i < cycle_roots.length; i++) {
            testStage(cycle_roots[i], a_conts[i], b_conts[i],
                    a_buttons[i], b_buttons[i]);
        }
    }

    void testStage(Container aFCR, Container aCont, Container bCont,
                   Component[] a_comps, Component[] b_comps) {
            System.out.println("focus cycle root = " + aFCR.getName());
            System.out.println("policy = " + aFCR.getFocusTraversalPolicy());
            System.out.println("aContainer = " + aCont.getName());
            System.out.println("bContainer = " + bCont.getName());
            System.out.println("Both containers are not Providers.");

            Component[] a_comps_backward = revertArray(a_comps);
            Component[] b_comps_backward = revertArray(b_comps);

            testForwardStage(aFCR, aCont, bCont,
                    false, a_comps, false, b_comps);
            testBackwardStage(aFCR, aCont, bCont,
                    false, a_comps_backward,
                    false, b_comps_backward);

            System.out.println("Both containers are Providers.");
            testForwardStage(aFCR, aCont, bCont,
                    true, a_comps, true, b_comps);
            testForwardStage(aFCR, aCont, bCont,
                             true, shakeArray(a_comps),
                             true, shakeArray(b_comps));

            testBackwardStage(aFCR, aCont, bCont,
                              true, a_comps_backward,
                    true, b_comps_backward);
            testBackwardStage(aFCR, aCont, bCont,
                              true, shakeArray(a_comps_backward),
                              true, shakeArray(b_comps_backward));

            System.out.println("aContainer.isProvider = true. "
                    + "bContainer.isProvider = false.");
            testForwardStage(aFCR, aCont, bCont,
                             true, a_comps, false, b_comps);
            testForwardStage(aFCR, aCont, bCont,
                             true, shakeArray(a_comps),
                    false, b_comps);
            testBackwardStage(aFCR, aCont, bCont,
                              true, a_comps_backward,
                    false, b_comps_backward);
            testBackwardStage(aFCR, aCont, bCont,
                              true, shakeArray(a_comps_backward),
                              false, b_comps_backward);

            System.out.println("aContainer.isProvider = false. "
                    + "bContainer.isProvider = true.");
            testForwardStage(aFCR, aCont, bCont,
                             false, a_comps,
                    true, b_comps);
            testForwardStage(aFCR, aCont, bCont,
                             false, a_comps,
                    true, shakeArray(b_comps));
            testBackwardStage(aFCR, aCont, bCont,
                              false, a_comps_backward,
                    true, b_comps_backward);
            testBackwardStage(aFCR, aCont, bCont,
                              false, a_comps_backward,
                              true, shakeArray(b_comps_backward));

            System.out.println("Stage completed.");
    }

    public void printGoldOrder(Component[] comps) {
        String goldOrderStr = "";
        for (int i =0;i < jumps; i++){
            goldOrderStr += " " + comps[i].getName();
        }
        System.out.println("GoldOrder: " + goldOrderStr);
    }

    public void testForwardStage(Container focusCycleRoot,
                                 Container aContainer,
                                 Container bContainer,
                                 boolean aProvider, Component[] aComps,
                                 boolean bProvider, Component[] bComps) {
        System.out.println("test forward traversal");
        System.out.println("\taProvider = " + aProvider);
        System.out.println("\tbProvider = " + bProvider);
        Component[] goldOrder = new Component[2*aComps.length + bComps.length];
        System.arraycopy(aComps, 0, goldOrder, 0, aComps.length);
        System.arraycopy(bComps, 0, goldOrder,
                aComps.length, bComps.length);
        System.arraycopy(aComps, 0, goldOrder,
                aComps.length + bComps.length,
                         aComps.length);
        printGoldOrder(goldOrder);

        String jumpStr = "";
        aContainer.setFocusTraversalPolicyProvider(aProvider);
        aContainer.setFocusTraversalPolicy(
            new ArrayOrderFocusTraversalPolicy(aContainer, aComps));
        bContainer.setFocusTraversalPolicyProvider(bProvider);
        bContainer.setFocusTraversalPolicy(
            new ArrayOrderFocusTraversalPolicy(bContainer, bComps));
        FocusTraversalPolicy policy = focusCycleRoot.getFocusTraversalPolicy();
        System.out.println("policy=" + policy);
        Component current = policy.getFirstComponent(focusCycleRoot);

        for (int i = 0;i<jumps;i++){
            jumpStr += " " + current.getName();
            if (current != goldOrder[i]) {
                System.out.println("i=" + i + " label = "+ current.getName()
                               + " i%8= " + i%goldOrder.length );
                throw new RuntimeException(errorOrderMessage);
            }
            System.out.println("getComponentAfter() on " + focusCycleRoot + ", " + current);
            current = policy.getComponentAfter(focusCycleRoot, current);
            System.out.println("RealOrder :" + jumpStr);
        }
        System.out.println(successStage);
    }

    public void testBackwardStage(Container focusCycleRoot,
                                  Container aContainer,
                                  Container bContainer,
                                  boolean aProvider, Component[] aComps,
                                  boolean bProvider, Component[] bComps)
    {
        System.out.println("test backward traversal");
        System.out.println("\taProvider = " + aProvider);
        System.out.println("\tbProvider = " + bProvider);
        Component[] goldOrder = new Component[2*bComps.length + bComps.length];
        System.arraycopy(bComps, 0, goldOrder, 0, bComps.length);
        System.arraycopy(aComps, 0, goldOrder, bComps.length, aComps.length);
        System.arraycopy(bComps, 0, goldOrder,
                aComps.length + bComps.length, bComps.length);
        printGoldOrder(goldOrder);

        String jumpStr = "";
        aContainer.setFocusTraversalPolicyProvider(aProvider);
        aContainer.setFocusTraversalPolicy(
            new ArrayOrderFocusTraversalPolicy(aContainer, revertArray(aComps)));
        bContainer.setFocusTraversalPolicyProvider(bProvider);
        bContainer.setFocusTraversalPolicy(
            new ArrayOrderFocusTraversalPolicy(bContainer, revertArray(bComps)));
        FocusTraversalPolicy policy = focusCycleRoot.getFocusTraversalPolicy();
        System.out.println("policy=" + policy);
        Component current = policy.getLastComponent(focusCycleRoot);

        for (int i = 0;i<jumps;i++){
            jumpStr += " " + current.getName();
            if (current != goldOrder[i]) {
                System.out.println("i=" + i + " label = "+ current.getName());
                throw new RuntimeException(errorOrderMessage);
            }
            System.out.println("getComponentBefore() on "
                    + focusCycleRoot.getName() + ", " + current.getName());
            current = policy.getComponentBefore(focusCycleRoot, current);
            System.out.println("RealOrder :" + jumpStr);
        }
        System.out.println(successStage);
    }

    Component[] shakeArray(Component[] comps) {
        Component[] new_comps = new Component[comps.length];
        System.arraycopy(comps, 0, new_comps, 0, comps.length);
        new_comps[0] = comps[1];
        new_comps[1] = comps[0];
        return new_comps;
    }

    Component[] revertArray(Component[] comps) {
        Component[] new_comps = new Component[comps.length];
        for (int i=0; i < comps.length; i++) {
            new_comps[i] = comps[comps.length - 1 - i];
        }

        return new_comps;
    }

    public void initSwingContInFrame()  {
        System.out.println("test Swing policy provider in AWT Frame.");

        jframe = new JFrame("FocusTraversalPolicyProviderTest - JFrame");
        jframe.setName("JFrame");
        JPanel panel1 = createPanel();
        jframe.getContentPane().add(panel1);

        frame = new Frame("FocusTraversalPolicyProviderTest - Frame");
        frame.setName("Frame");
        JPanel panel2 = createPanel();
        panel2.setFocusTraversalPolicyProvider(true);
        panel2.setFocusTraversalPolicy(jframe.getFocusTraversalPolicy());
        frame.add(panel2);

        jframe.pack();
        jframe.setVisible(true);
        frame.pack();
        frame.setVisible(true);
    }

    public void testSwingContInFrame() {
        FocusTraversalPolicy policy = frame.getFocusTraversalPolicy();
        FocusTraversalPolicy jpolicy = jframe.getFocusTraversalPolicy();

        System.out.println("policy = " + policy);
        System.out.println("jpolicy = " + jpolicy);

        assertEquals("Different default components.",
                jpolicy.getDefaultComponent(jframe),
                policy.getDefaultComponent(frame));

        assertEquals("Different first components.",
                jpolicy.getFirstComponent(jframe),
                policy.getFirstComponent(frame));

        assertEquals("Different last components.",
                jpolicy.getLastComponent(jframe),
                policy.getLastComponent(frame));

        System.out.println("test forward traversal order.");

        Component jcur = jpolicy.getFirstComponent(jframe);
        Component cur = jpolicy.getFirstComponent(frame);

        for (int i = 0; i < 2 * n_buttons; i++) {
            assertEquals("Wrong sequence (step=" + i + ")",
                    jcur, cur);
            jcur = jpolicy.getComponentAfter(jframe, jcur);
            cur = policy.getComponentAfter(frame, cur);
        }

        System.out.println("test backward traversal order.");

        jcur = jpolicy.getLastComponent(jframe);
        cur = jpolicy.getLastComponent(frame);

        for (int i = 0; i < 2 * n_buttons; i++) {
            assertEquals("Wrong sequence (step=" + i + ")",
                    jcur, cur);
            jcur = jpolicy.getComponentBefore(jframe, jcur);
            cur = policy.getComponentBefore(frame, cur);
        }
    }

    public void assertEquals(String msg, Component expected, Component actual) {
        if (expected == null && actual != null
            || actual == null && expected != null)
        {
            throw new RuntimeException(msg + "(expected=" + expected
                                       + ", actual=" + actual + ")");
        }

        String expected_name = expected.getName();
        String actual_name = actual.getName();

        if ((expected_name != null && !expected_name.equals(actual_name))
            || (actual_name != null && !actual_name.equals(expected_name)))
        {
            throw new RuntimeException(msg + "(expected_name=" + expected_name
                                       + ", actual_name=" + actual_name + ")");
        }
    }

    public JPanel createPanel() {
        JPanel pane = new JPanel();
        pane.setName("jpanel");
        for (int i = 0; i < n_buttons; i++) {
            JButton btn = new JButton("jbtn" + i);
            btn.setName("jbtn" + i);
            pane.add(btn);
        }
        return pane;
    }
}

class ArrayOrderFocusTraversalPolicy extends FocusTraversalPolicy {

    final Component[] comps;
    final Container cont;

    public ArrayOrderFocusTraversalPolicy(Container aCont, Component[] aComps) {
        if (aCont == null) {
            throw new NullPointerException("aCont is null.");
        }
        cont = aCont;
        comps = new Component[aComps.length];
        for (int i = 0; i < comps.length; i++) {
            comps[i] = aComps[i];
        }
    }

    private void checkContainer(Container aCont) {
        if (aCont != cont) {
            System.err.println("aCont = " + aCont);
            System.err.println("cont = " + cont);
            throw new IllegalArgumentException(
                    "Policy is not registered for this container.");
        }
    }

    private int findIndex(Component aComp) {
        for (int i = 0; i < comps.length; i++) {
            if (aComp == comps[i]) {
                return i;
            }
        }

        return -1;
    }

    public Component getComponentAfter(Container focusCycleRoot,
                                       Component aComponent) {
        checkContainer(focusCycleRoot);

        int current_index = findIndex(aComponent);
        if (current_index < 0) {
            return null;
        }

        current_index++;

        if (current_index < comps.length) {
            return comps[current_index];
        }

        if (focusCycleRoot.isFocusCycleRoot()) {
            return getFirstComponent(focusCycleRoot);
        } else {
            return null;
        }
    }

    public Component getComponentBefore(Container focusCycleRoot,
                                        Component aComponent) {
        checkContainer(focusCycleRoot);

        int current_index = findIndex(aComponent);
        if (current_index < 0) {
            return null;
        }

        current_index--;

        if (current_index >= 0) {
            return comps[current_index];
        }

        if (focusCycleRoot.isFocusCycleRoot()) {
            return getLastComponent(focusCycleRoot);
        } else {
            return null;
        }
    }

    public Component getFirstComponent(Container focusCycleRoot) {
        checkContainer(focusCycleRoot);
        return comps[0];
    }

    public Component getLastComponent(Container focusCycleRoot) {
        checkContainer(focusCycleRoot);
        return comps[comps.length - 1];
    }

    public Component getDefaultComponent(Container focusCycleRoot) {
        return getFirstComponent(focusCycleRoot);
    }

    public Component getInitialComponent(Window window) {
        throw new UnsupportedOperationException("getInitialComponent() is not supported.");
    }

    public Component[] getCycle(Container focusCycleRoot) {
        checkContainer(focusCycleRoot);
        Component[] temp = new Component[comps.length];
        System.arraycopy(comps, 0, temp, 0, comps.length);
        return temp;
    }
}
