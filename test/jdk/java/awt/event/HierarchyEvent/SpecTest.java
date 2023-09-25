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
  @test
  @bug 4222172
  @summary Verifies initial implementation of HierarchyEvent (programmatic)
  @key headful
  @run main SpecTest
*/

import java.awt.AWTEvent;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Label;
import java.awt.Panel;
import java.awt.Window;
import java.awt.event.HierarchyBoundsListener;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import java.lang.reflect.InvocationTargetException;

import javax.swing.JLabel;
import javax.swing.JPanel;

public class SpecTest extends Frame implements HierarchyListener, HierarchyBoundsListener {
    static SpecTest f;

    public static void main(String args[]) throws InterruptedException,
            InvocationTargetException {
        EventQueue.invokeAndWait(() -> init());
        f.start();
    }

    class EELabel extends Label {
        private long mask = 0;
        public EELabel(String s) {
            super(s);
        }

        protected void processHierarchyEvent(HierarchyEvent e) {
            super.processHierarchyEvent(e);
            if ((mask & AWTEvent.HIERARCHY_EVENT_MASK) != 0) {
                switch (e.getID()) {
                    case HierarchyEvent.HIERARCHY_CHANGED:
                        hierarchyChanged(e);
                        break;
                    default:
                        break;
                }
            }
        }

        protected void processHierarchyBoundsEvent(HierarchyEvent e) {
            super.processHierarchyBoundsEvent(e);
            if ((mask & AWTEvent.HIERARCHY_BOUNDS_EVENT_MASK) != 0) {
                switch (e.getID()) {
                    case HierarchyEvent.ANCESTOR_MOVED:
                        ancestorMoved(e);
                        break;
                    case HierarchyEvent.ANCESTOR_RESIZED:
                        ancestorResized(e);
                        break;
                    default:
                        break;
                }
            }
        }

        public void pubEnableEvents(long eventsToEnable) {
            mask |= eventsToEnable;
            enableEvents(eventsToEnable);
        }

        public void pubDisableEvents(long eventsToDisable) {
            mask &= ~eventsToDisable;
            disableEvents(eventsToDisable);
        }
    }

    class EEJLabel extends JLabel {
        private long mask = 0;
        public EEJLabel(String s) {
            super(s);
        }
        protected void processHierarchyEvent(HierarchyEvent e) {
            super.processHierarchyEvent(e);
            if ((mask & AWTEvent.HIERARCHY_EVENT_MASK) != 0) {
                switch (e.getID()) {
                    case HierarchyEvent.HIERARCHY_CHANGED:
                        hierarchyChanged(e);
                        break;
                    default:
                        break;
                }
            }
        }

        protected void processHierarchyBoundsEvent(HierarchyEvent e) {
            super.processHierarchyBoundsEvent(e);
            if ((mask & AWTEvent.HIERARCHY_BOUNDS_EVENT_MASK) != 0) {
                switch (e.getID()) {
                    case HierarchyEvent.ANCESTOR_MOVED:
                        ancestorMoved(e);
                        break;
                    case HierarchyEvent.ANCESTOR_RESIZED:
                        ancestorResized(e);
                        break;
                    default:
                        break;
                }
            }
        }

        public void pubEnableEvents(long eventsToEnable) {
            mask |= eventsToEnable;
            enableEvents(eventsToEnable);
        }

        public void pubDisableEvents(long eventsToDisable) {
            mask &= ~eventsToDisable;
            disableEvents(eventsToDisable);
        }
    }

    class EEPanel extends Panel {
        private long mask = 0;
        protected void processHierarchyEvent(HierarchyEvent e) {
            super.processHierarchyEvent(e);
            if ((mask & AWTEvent.HIERARCHY_EVENT_MASK) != 0) {
                switch (e.getID()) {
                    case HierarchyEvent.HIERARCHY_CHANGED:
                        hierarchyChanged(e);
                        break;
                    default:
                        break;
                }
            }
        }

        protected void processHierarchyBoundsEvent(HierarchyEvent e) {
            super.processHierarchyBoundsEvent(e);
            if ((mask & AWTEvent.HIERARCHY_BOUNDS_EVENT_MASK) != 0) {
                switch (e.getID()) {
                    case HierarchyEvent.ANCESTOR_MOVED:
                        ancestorMoved(e);
                        break;
                    case HierarchyEvent.ANCESTOR_RESIZED:
                        ancestorResized(e);
                        break;
                    default:
                        break;
                }
            }
        }

        public void pubEnableEvents(long eventsToEnable) {
            mask |= eventsToEnable;
            enableEvents(eventsToEnable);
        }

        public void pubDisableEvents(long eventsToDisable) {
            mask &= ~eventsToDisable;
            disableEvents(eventsToDisable);
        }
    }

    class EEJPanel extends JPanel {
        private long mask = 0;
        protected void processHierarchyEvent(HierarchyEvent e) {
            super.processHierarchyEvent(e);
            if ((mask & AWTEvent.HIERARCHY_EVENT_MASK) != 0) {
                switch (e.getID()) {
                    case HierarchyEvent.HIERARCHY_CHANGED:
                        hierarchyChanged(e);
                        break;
                    default:
                        break;
                }
            }
        }

        protected void processHierarchyBoundsEvent(HierarchyEvent e) {
            super.processHierarchyBoundsEvent(e);
            if ((mask & AWTEvent.HIERARCHY_BOUNDS_EVENT_MASK) != 0) {
                switch (e.getID()) {
                    case HierarchyEvent.ANCESTOR_MOVED:
                        ancestorMoved(e);
                        break;
                    case HierarchyEvent.ANCESTOR_RESIZED:
                        ancestorResized(e);
                        break;
                    default:
                        break;
                }
            }
        }

        public void pubEnableEvents(long eventsToEnable) {
            mask |= eventsToEnable;
            enableEvents(eventsToEnable);
        }

        public void pubDisableEvents(long eventsToDisable) {
            mask &= ~eventsToDisable;
            disableEvents(eventsToDisable);
        }
    }

    int parentChanged, displayabilityChanged, showingChanged, ancestorMoved,
            ancestorResized;
    private static final int OBJ_ARRAY_SIZE = 2;
    Component[] source = new Component[OBJ_ARRAY_SIZE],
            changed = new Component[OBJ_ARRAY_SIZE];
    Container[] changedParent = new Container[OBJ_ARRAY_SIZE];

    boolean assertParent = false, assertDisplayability = false,
            assertShowing = false, assertMoved = false, assertResized = false;

    private void flushEventQueue() throws InterruptedException, InvocationTargetException {
        EventQueue.invokeAndWait(() -> {});
    }

    private void testAdd(Component child, Container parent, int count)
            throws InterruptedException, InvocationTargetException {
        assertParent = true;
        parentChanged = count;
        parent.add(child);
        flushEventQueue();
        if (parentChanged != 0) {
            throw new RuntimeException("hierarchyChanged(PARENT_CHANGED) invoked "+
                    parentChanged+" too few times");
        }
        assertParent = false;
    }

    private void testRemove(Component child, Container parent, int count)
            throws InterruptedException, InvocationTargetException {
        assertParent = true;
        parentChanged = count;
        parent.remove(child);
        flushEventQueue();
        if (parentChanged != 0) {
            throw new RuntimeException("hierarchyChanged(PARENT_CHANGED) invoked "+
                    parentChanged+" too few times");
        }
        assertParent = false;
    }

    private void testSetLocation(Component changed, int x, int y, int count)
            throws InterruptedException, InvocationTargetException {
        assertMoved = true;
        ancestorMoved = count;
        changed.setLocation(x, y);
        flushEventQueue();
        if (ancestorMoved != 0) {
            throw new RuntimeException("ancestorMoved invoked "+ancestorMoved+
                    " too few times");
        }
        assertMoved = false;
    }

    private void testSetSize(Component changed, int w, int h, int count)
            throws InterruptedException, InvocationTargetException {
        assertResized = true;
        ancestorResized = count;
        changed.setSize(w, h);
        flushEventQueue();
        if (ancestorResized != 0) {
            throw new RuntimeException("ancestorResized invoked "+ancestorResized+
                    " too few times");
        }
        assertResized = false;
    }

    private void testPack(Window topLevel, int displayabilityCount)
            throws InterruptedException, InvocationTargetException {
        assertDisplayability = true;
        assertShowing = true;
        displayabilityChanged = displayabilityCount;
        showingChanged = 0;
        topLevel.pack();
        flushEventQueue();
        if (displayabilityChanged != 0) {
            throw new RuntimeException("hierarchyChanged(DISPLAYABILITY_CHANGED) "+
                    "invoked "+displayabilityChanged+
                    " too few times");
        }
        if (showingChanged != 0) {
            throw new RuntimeException("hierarchyChanged(SHOWING_CHANGED) "+
                    "invoked, but should not have been");
        }
        assertDisplayability = false;
        assertShowing = false;
    }

    private void testShow(Window topLevel, int displayabilityCount,
                          int showingCount)
            throws InterruptedException, InvocationTargetException {
        assertDisplayability = true;
        assertShowing = true;
        displayabilityChanged = displayabilityCount;
        showingChanged = showingCount;
        topLevel.show();
        flushEventQueue();
        if (displayabilityChanged != 0) {
            throw new RuntimeException("hierarchyChanged(DISPLAYABILITY_CHANGED) "+
                    "invoked "+displayabilityChanged+
                    " too few times");
        }
        if (showingChanged != 0) {
            throw new RuntimeException("hierarchyChanged(SHOWING_CHANGED) "+
                    "invoked "+showingChanged+" too few times");
        }
        assertDisplayability = false;
        assertShowing = false;
    }

    private void testHide(Window topLevel, int showingCount)
            throws InterruptedException, InvocationTargetException {
        assertDisplayability = true;
        assertShowing = true;
        displayabilityChanged = 0;
        showingChanged = showingCount;
        topLevel.hide();
        flushEventQueue();
        if (displayabilityChanged != 0) {
            throw new RuntimeException("hierarchyChanged(DISPLAYABILITY_CHANGED) "+
                    "invoked, but should not have been");
        }
        if (showingChanged != 0) {
            throw new RuntimeException("hierarchyChanged(SHOWING_CHANGED) "+
                    "invoked "+showingChanged+" too few times");
        }
        assertDisplayability = false;
        assertShowing = false;
    }

    private void testDispose(Window topLevel, int displayabilityCount,
                             int showingCount)
            throws InterruptedException, InvocationTargetException {
        assertDisplayability = true;
        assertShowing = true;
        displayabilityChanged = displayabilityCount;
        showingChanged = showingCount;
        topLevel.dispose();
        flushEventQueue();
        if (displayabilityChanged != 0) {
            throw new RuntimeException("hierarchyChanged(DISPLAYABILITY_CHANGED) "+
                    "invoked "+displayabilityChanged+
                    " too few times");
        }
        if (showingChanged != 0) {
            throw new RuntimeException("hierarchyChanged(SHOWING_CHANGED) "+
                    "invoked "+showingChanged+" too few times");
        }
        assertDisplayability = false;
        assertShowing = false;
    }

    private void assertObjectsImpl(HierarchyEvent e) {
        int match = -1;

        for (int i = 0; i < OBJ_ARRAY_SIZE; i++) {
            if (e.getComponent() == source[i]) {
                match = i;
                break;
            }
        }

        if (match == -1) {
            String str = "\n\nsource incorrect, was "+e.getComponent()+"\n\n";
            for (int i = 0; i < OBJ_ARRAY_SIZE; i++) {
                str += "available source: "+source[i]+"\n\n";
            }
            str += "event: "+e+"\n";
            throw new RuntimeException(str);
        }

        if (e.getChanged() != changed[match]) {
            if (e.getID() == HierarchyEvent.HIERARCHY_CHANGED &&
                    (e.getChangeFlags() & HierarchyEvent.DISPLAYABILITY_CHANGED) != 0) {
                System.err.println("warning (known bug): changed for "+
                        "DISPLAYABILITY_CHANGED event incorrect");
            } else {
                throw new RuntimeException("\n\nchanged incorrect, was "+
                        e.getChanged()+
                        ", should be "+changed[match]+"\n\n"+
                        "event: "+e+"\n");
            }
        }

        if (e.getChangedParent() != changedParent[match]) {
            if (e.getID() == HierarchyEvent.HIERARCHY_CHANGED &&
                    (e.getChangeFlags() & HierarchyEvent.DISPLAYABILITY_CHANGED) != 0) {
                System.err.println("warning (known bug): changedParent for "+
                        "DISPLAYABILITY_CHANGED event incorrect");
            } else {
                throw new RuntimeException("changedParent incorrect, was "+
                        e.getChangedParent()+
                        ", should be "+changedParent[match]+"\n\n"+
                        "event: "+e+"\n");
            }
        }
    }

    private void setObjects(int index, Component source, Component changed,
                            Container changedParent) {
        this.source[index] = source;
        this.changed[index] = changed;
        this.changedParent[index] = changedParent;
    }

    private void resetObjects() {
        for (int i = 0; i < OBJ_ARRAY_SIZE; i++) {
            setObjects(i, null, null, null);
        }
    }

    public void hierarchyChanged(HierarchyEvent e) {
        if (assertParent &&
                (e.getChangeFlags() & HierarchyEvent.PARENT_CHANGED) != 0) {
            assertObjectsImpl(e);
            parentChanged--;
        }
        if (assertDisplayability &&
                (e.getChangeFlags() & HierarchyEvent.DISPLAYABILITY_CHANGED) != 0){
            assertObjectsImpl(e);
            displayabilityChanged--;
        }
        if (assertShowing &&
                (e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0) {
            assertObjectsImpl(e);
            showingChanged--;
        }
    }

    public void ancestorMoved(HierarchyEvent e) {
        if (!assertMoved)
            return;

        assertObjectsImpl(e);
        ancestorMoved--;
    }

    public void ancestorResized(HierarchyEvent e) {
        if (!assertResized)
            return;

        assertObjectsImpl(e);
        ancestorResized--;
    }

    public static void init() {
        f = new SpecTest();
        f.setTitle("SpecTest");
        f.setLayout(new BorderLayout());
    }

    private void test1(Component source, Component changed,
                       Container changedParent, Window topLevel,
                       int hierarchyCount, int hierarchyBoundsCount)
            throws InterruptedException, InvocationTargetException {
        changed.setBounds(0, 0, 100, 100);
        topLevel.dispose();
        resetObjects();

        setObjects(0, source, changed, changedParent);

        testRemove(changed, changedParent, hierarchyCount);
        testAdd(changed, changedParent, hierarchyCount);
        testSetLocation(changed, 200, 250, hierarchyBoundsCount);
        testSetSize(changed, 50, 50, hierarchyBoundsCount);

        setObjects(0, source, topLevel, null);

        testPack(topLevel, hierarchyCount);
        testDispose(topLevel, hierarchyCount, 0);
        testPack(topLevel, hierarchyCount);
        testShow(topLevel, 0, hierarchyCount);
        testDispose(topLevel, hierarchyCount, hierarchyCount);
        testShow(topLevel, hierarchyCount, hierarchyCount);
        testHide(topLevel, hierarchyCount);
        testDispose(topLevel, hierarchyCount, 0);

        resetObjects();
    }

    private void test2(Component source1, Container parent1,
                       Component source2, Container parent2,
                       Window topLevel)
            throws InterruptedException, InvocationTargetException {
        topLevel.setBounds(0, 0, 100, 100);
        topLevel.dispose();
        resetObjects();

        setObjects(0, source1, source1, parent1);
        testRemove(source1, parent1, 1);
        testAdd(source1, parent1, 1);

        setObjects(0, source2, source2, parent2);
        testRemove(source2, parent2, 1);
        testAdd(source2, parent2, 1);

        setObjects(0, source1, topLevel, null);
        setObjects(1, source2, topLevel, null);

        testSetLocation(topLevel, 200, 250, 2);
        testSetSize(topLevel, 50, 50, 2);

        testPack(topLevel, 2);
        testDispose(topLevel, 2, 0);
        testPack(topLevel, 2);
        testShow(topLevel, 0, 2);
        testDispose(topLevel, 2, 2);
        testShow(topLevel, 2, 2);
        testHide(topLevel, 2);
        testDispose(topLevel, 2, 0);

        resetObjects();
    }

    public void start() throws InterruptedException, InvocationTargetException {
    /*
                                    f
                                    |
                -----------------------------------------
                |       |       |       |       |       |
                l1      l2      p1      p2      p5      p6
                                |       |       |       |
                              -----   -----     |       |
                              |   |   |   |     |       |
                              l3  l4  l5  l6    p3      p4
                                                |       |
                                              -----   -----
                                              |   |   |   |
                                              l7  l8  l9  l10
    */

        // listener-only tests

        {
            Label l1 = new Label("Label 1");
            JLabel l2 = new JLabel("Label 2");
            Panel p1 = new Panel();
            JPanel p2 = new JPanel();
            Panel p5 = new Panel();
            JPanel p6 = new JPanel();

            Label l3 = new Label("Label 3");
            JLabel l4 = new JLabel("Label 4");
            Label l5 = new Label("Label 5");
            JLabel l6 = new JLabel("Label 6");
            JPanel p3 = new JPanel();
            Panel p4 = new Panel();

            Label l7 = new Label("Label 7");
            JLabel l8 = new JLabel("Label 8");
            JLabel l9 = new JLabel("Label 9");
            Label l10 = new Label("Label 10");

            f.add(l1);
            f.add(l2);
            f.add(p1);
            f.add(p2);
            f.add(p5);
            f.add(p6);

            p1.add(l3);
            p1.add(l4);

            p2.add(l5);
            p2.add(l6);

            p5.add(p3);

            p6.add(p4);

            p3.add(l7);
            p3.add(l8);

            p4.add(l9);
            p4.add(l10);



            // test1

            l1.addHierarchyListener(this);
            l1.addHierarchyBoundsListener(this);
            test1(l1, l1, f, f, 1, 0);
            l1.addHierarchyListener(this);
            l1.addHierarchyBoundsListener(this);
            test1(l1, l1, f, f, 2, 0);
            l1.removeHierarchyListener(this);
            l1.removeHierarchyBoundsListener(this);
            l1.removeHierarchyListener(this);
            l1.removeHierarchyBoundsListener(this);

            l2.addHierarchyListener(this);
            l2.addHierarchyBoundsListener(this);
            test1(l2, l2, f, f, 1, 0);
            l2.addHierarchyListener(this);
            l2.addHierarchyBoundsListener(this);
            test1(l2, l2, f, f, 2, 0);
            l2.removeHierarchyListener(this);
            l2.removeHierarchyBoundsListener(this);
            l2.removeHierarchyListener(this);
            l2.removeHierarchyBoundsListener(this);

            p1.addHierarchyListener(this);
            p1.addHierarchyBoundsListener(this);
            test1(p1, p1, f, f, 1, 0);
            p1.addHierarchyListener(this);
            p1.addHierarchyBoundsListener(this);
            test1(p1, p1, f, f, 2, 0);
            p1.removeHierarchyListener(this);
            p1.removeHierarchyBoundsListener(this);
            p1.removeHierarchyListener(this);
            p1.removeHierarchyBoundsListener(this);

            p2.addHierarchyListener(this);
            p2.addHierarchyBoundsListener(this);
            test1(p2, p2, f, f, 1, 0);
            p2.addHierarchyListener(this);
            p2.addHierarchyBoundsListener(this);
            test1(p2, p2, f, f, 2, 0);
            p2.removeHierarchyListener(this);
            p2.removeHierarchyBoundsListener(this);
            p2.removeHierarchyListener(this);
            p2.removeHierarchyBoundsListener(this);

            p5.addHierarchyListener(this);
            p5.addHierarchyBoundsListener(this);
            test1(p5, p5, f, f, 1, 0);
            p5.addHierarchyListener(this);
            p5.addHierarchyBoundsListener(this);
            test1(p5, p5, f, f, 2, 0);
            p5.removeHierarchyListener(this);
            p5.removeHierarchyBoundsListener(this);
            p5.removeHierarchyListener(this);
            p5.removeHierarchyBoundsListener(this);

            p6.addHierarchyListener(this);
            p6.addHierarchyBoundsListener(this);
            test1(p6, p6, f, f, 1, 0);
            p6.addHierarchyListener(this);
            p6.addHierarchyBoundsListener(this);
            test1(p6, p6, f, f, 2, 0);
            p6.removeHierarchyListener(this);
            p6.removeHierarchyBoundsListener(this);
            p6.removeHierarchyListener(this);
            p6.removeHierarchyBoundsListener(this);

            l3.addHierarchyListener(this);
            l3.addHierarchyBoundsListener(this);
            test1(l3, l3, p1, f, 1, 0);
            test1(l3, p1, f, f, 1, 1);
            l3.addHierarchyListener(this);
            l3.addHierarchyBoundsListener(this);
            test1(l3, l3, p1, f, 2, 0);
            test1(l3, p1, f, f, 2, 2);
            l3.removeHierarchyListener(this);
            l3.removeHierarchyBoundsListener(this);
            l3.removeHierarchyListener(this);
            l3.removeHierarchyBoundsListener(this);

            l4.addHierarchyListener(this);
            l4.addHierarchyBoundsListener(this);
            test1(l4, l4, p1, f, 1, 0);
            test1(l4, p1, f, f, 1, 1);
            l4.addHierarchyListener(this);
            l4.addHierarchyBoundsListener(this);
            test1(l4, l4, p1, f, 2, 0);
            test1(l4, p1, f, f, 2, 2);
            l4.removeHierarchyListener(this);
            l4.removeHierarchyBoundsListener(this);
            l4.removeHierarchyListener(this);
            l4.removeHierarchyBoundsListener(this);

            l5.addHierarchyListener(this);
            l5.addHierarchyBoundsListener(this);
            test1(l5, l5, p2, f, 1, 0);
            test1(l5, p2, f, f, 1, 1);
            l5.addHierarchyListener(this);
            l5.addHierarchyBoundsListener(this);
            test1(l5, l5, p2, f, 2, 0);
            test1(l5, p2, f, f, 2, 2);
            l5.removeHierarchyListener(this);
            l5.removeHierarchyBoundsListener(this);
            l5.removeHierarchyListener(this);
            l5.removeHierarchyBoundsListener(this);

            l6.addHierarchyListener(this);
            l6.addHierarchyBoundsListener(this);
            test1(l6, l6, p2, f, 1, 0);
            test1(l6, p2, f, f, 1, 1);
            l6.addHierarchyListener(this);
            l6.addHierarchyBoundsListener(this);
            test1(l6, l6, p2, f, 2, 0);
            test1(l6, p2, f, f, 2, 2);
            l6.removeHierarchyListener(this);
            l6.removeHierarchyBoundsListener(this);
            l6.removeHierarchyListener(this);
            l6.removeHierarchyBoundsListener(this);

            p3.addHierarchyListener(this);
            p3.addHierarchyBoundsListener(this);
            test1(p3, p3, p5, f, 1, 0);
            test1(p3, p5, f, f, 1, 1);
            p3.addHierarchyListener(this);
            p3.addHierarchyBoundsListener(this);
            test1(p3, p3, p5, f, 2, 0);
            test1(p3, p5, f, f, 2, 2);
            p3.removeHierarchyListener(this);
            p3.removeHierarchyBoundsListener(this);
            p3.removeHierarchyListener(this);
            p3.removeHierarchyBoundsListener(this);

            p4.addHierarchyListener(this);
            p4.addHierarchyBoundsListener(this);
            test1(p4, p4, p6, f, 1, 0);
            test1(p4, p6, f, f, 1, 1);
            p4.addHierarchyListener(this);
            p4.addHierarchyBoundsListener(this);
            test1(p4, p4, p6, f, 2, 0);
            test1(p4, p6, f, f, 2, 2);
            p4.removeHierarchyListener(this);
            p4.removeHierarchyBoundsListener(this);
            p4.removeHierarchyListener(this);
            p4.removeHierarchyBoundsListener(this);

            l7.addHierarchyListener(this);
            l7.addHierarchyBoundsListener(this);
            test1(l7, l7, p3, f, 1, 0);
            test1(l7, p3, p5, f, 1, 1);
            test1(l7, p5, f, f, 1, 1);
            l7.addHierarchyListener(this);
            l7.addHierarchyBoundsListener(this);
            test1(l7, l7, p3, f, 2, 0);
            test1(l7, p3, p5, f, 2, 2);
            test1(l7, p5, f, f, 2, 2);
            l7.removeHierarchyListener(this);
            l7.removeHierarchyBoundsListener(this);
            l7.removeHierarchyListener(this);
            l7.removeHierarchyBoundsListener(this);

            l8.addHierarchyListener(this);
            l8.addHierarchyBoundsListener(this);
            test1(l8, l8, p3, f, 1, 0);
            test1(l8, p3, p5, f, 1, 1);
            test1(l8, p5, f, f, 1, 1);
            l8.addHierarchyListener(this);
            l8.addHierarchyBoundsListener(this);
            test1(l8, l8, p3, f, 2, 0);
            test1(l8, p3, p5, f, 2, 2);
            test1(l8, p5, f, f, 2, 2);
            l8.removeHierarchyListener(this);
            l8.removeHierarchyBoundsListener(this);
            l8.removeHierarchyListener(this);
            l8.removeHierarchyBoundsListener(this);

            l9.addHierarchyListener(this);
            l9.addHierarchyBoundsListener(this);
            test1(l9, l9, p4, f, 1, 0);
            test1(l9, p4, p6, f, 1, 1);
            test1(l9, p6, f, f, 1, 1);
            l9.addHierarchyListener(this);
            l9.addHierarchyBoundsListener(this);
            test1(l9, l9, p4, f, 2, 0);
            test1(l9, p4, p6, f, 2, 2);
            test1(l9, p6, f, f, 2, 2);
            l9.removeHierarchyListener(this);
            l9.removeHierarchyBoundsListener(this);
            l9.removeHierarchyListener(this);
            l9.removeHierarchyBoundsListener(this);

            l10.addHierarchyListener(this);
            l10.addHierarchyBoundsListener(this);
            test1(l10, l10, p4, f, 1, 0);
            test1(l10, p4, p6, f, 1, 1);
            test1(l10, p6, f, f, 1, 1);
            l10.addHierarchyListener(this);
            l10.addHierarchyBoundsListener(this);
            test1(l10, l10, p4, f, 2, 0);
            test1(l10, p4, p6, f, 2, 2);
            test1(l10, p6, f, f, 2, 2);
            l10.removeHierarchyListener(this);
            l10.removeHierarchyBoundsListener(this);
            l10.removeHierarchyListener(this);
            l10.removeHierarchyBoundsListener(this);



            // test2

            l1.addHierarchyListener(this);
            l10.addHierarchyListener(this);
            l1.addHierarchyBoundsListener(this);
            l10.addHierarchyBoundsListener(this);
            test2(l1, f, l10, p4, f);
            l1.removeHierarchyListener(this);
            l10.removeHierarchyListener(this);
            l1.removeHierarchyBoundsListener(this);
            l10.removeHierarchyBoundsListener(this);

            l2.addHierarchyListener(this);
            l9.addHierarchyListener(this);
            l2.addHierarchyBoundsListener(this);
            l9.addHierarchyBoundsListener(this);
            test2(l2, f, l9, p4, f);
            l2.removeHierarchyListener(this);
            l9.removeHierarchyListener(this);
            l2.removeHierarchyBoundsListener(this);
            l9.removeHierarchyBoundsListener(this);

            l3.addHierarchyListener(this);
            l8.addHierarchyListener(this);
            l3.addHierarchyBoundsListener(this);
            l8.addHierarchyBoundsListener(this);
            test2(l3, p1, l8, p3, f);
            l3.removeHierarchyListener(this);
            l8.removeHierarchyListener(this);
            l3.removeHierarchyBoundsListener(this);
            l8.removeHierarchyBoundsListener(this);

            l4.addHierarchyListener(this);
            l7.addHierarchyListener(this);
            l4.addHierarchyBoundsListener(this);
            l7.addHierarchyBoundsListener(this);
            test2(l4, p1, l7, p3, f);
            l4.removeHierarchyListener(this);
            l7.removeHierarchyListener(this);
            l4.removeHierarchyBoundsListener(this);
            l7.removeHierarchyBoundsListener(this);

            l5.addHierarchyListener(this);
            l6.addHierarchyListener(this);
            l5.addHierarchyBoundsListener(this);
            l6.addHierarchyBoundsListener(this);
            test2(l5, p2, l6, p2, f);
            l5.removeHierarchyListener(this);
            l6.removeHierarchyListener(this);
            l5.removeHierarchyBoundsListener(this);
            l6.removeHierarchyBoundsListener(this);

            p1.addHierarchyListener(this);
            p4.addHierarchyListener(this);
            p1.addHierarchyBoundsListener(this);
            p4.addHierarchyBoundsListener(this);
            test2(p1, f, p4, p6, f);
            p1.removeHierarchyListener(this);
            p4.removeHierarchyListener(this);
            p1.removeHierarchyBoundsListener(this);
            p4.removeHierarchyBoundsListener(this);

            p2.addHierarchyListener(this);
            p3.addHierarchyListener(this);
            p2.addHierarchyBoundsListener(this);
            p3.addHierarchyBoundsListener(this);
            test2(p2, f, p3, p5, f);
            p2.removeHierarchyListener(this);
            p3.removeHierarchyListener(this);
            p2.removeHierarchyBoundsListener(this);
            p3.removeHierarchyBoundsListener(this);

            p5.addHierarchyListener(this);
            p6.addHierarchyListener(this);
            p5.addHierarchyBoundsListener(this);
            p6.addHierarchyBoundsListener(this);
            test2(p5, f, p6, f, f);
            p5.removeHierarchyListener(this);
            p6.removeHierarchyListener(this);
            p5.removeHierarchyBoundsListener(this);
            p6.removeHierarchyBoundsListener(this);
        }

        EventQueue.invokeAndWait(() -> {
            if (f != null) {
                f.dispose();
                f.removeAll();
            }
        });

        // mixed listener/eventEnabled and eventEnabled-only tests

        {
            EELabel l1 = new EELabel("Label 1");
            EEJLabel l2 = new EEJLabel("Label 2");
            EEPanel p1 = new EEPanel();
            EEJPanel p2 = new EEJPanel();
            EEPanel p5 = new EEPanel();
            EEJPanel p6 = new EEJPanel();

            EELabel l3 = new EELabel("Label 3");
            EEJLabel l4 = new EEJLabel("Label 4");
            EELabel l5 = new EELabel("Label 5");
            EEJLabel l6 = new EEJLabel("Label 6");
            EEJPanel p3 = new EEJPanel();
            EEPanel p4 = new EEPanel();

            EELabel l7 = new EELabel("Label 7");
            EEJLabel l8 = new EEJLabel("Label 8");
            EEJLabel l9 = new EEJLabel("Label 9");
            EELabel l10 = new EELabel("Label 10");

            f.add(l1);
            f.add(l2);
            f.add(p1);
            f.add(p2);
            f.add(p5);
            f.add(p6);

            p1.add(l3);
            p1.add(l4);

            p2.add(l5);
            p2.add(l6);

            p5.add(p3);

            p6.add(p4);

            p3.add(l7);
            p3.add(l8);

            p4.add(l9);
            p4.add(l10);



            // test3

            l1.pubEnableEvents(AWTEvent.HIERARCHY_EVENT_MASK);
            l1.pubEnableEvents(AWTEvent.HIERARCHY_BOUNDS_EVENT_MASK);
            test1(l1, l1, f, f, 1, 0);
            l1.addHierarchyListener(this);
            l1.addHierarchyBoundsListener(this);
            test1(l1, l1, f, f, 2, 0);
            l1.pubDisableEvents(AWTEvent.HIERARCHY_EVENT_MASK);
            l1.pubDisableEvents(AWTEvent.HIERARCHY_BOUNDS_EVENT_MASK);
            l1.removeHierarchyListener(this);
            l1.removeHierarchyBoundsListener(this);

            l2.pubEnableEvents(AWTEvent.HIERARCHY_EVENT_MASK);
            l2.pubEnableEvents(AWTEvent.HIERARCHY_BOUNDS_EVENT_MASK);
            test1(l2, l2, f, f, 1, 0);
            l2.addHierarchyListener(this);
            l2.addHierarchyBoundsListener(this);
            test1(l2, l2, f, f, 2, 0);
            l2.removeHierarchyListener(this);
            l2.removeHierarchyBoundsListener(this);
            l2.pubDisableEvents(AWTEvent.HIERARCHY_EVENT_MASK);
            l2.pubDisableEvents(AWTEvent.HIERARCHY_BOUNDS_EVENT_MASK);

            p1.addHierarchyListener(this);
            p1.addHierarchyBoundsListener(this);
            test1(p1, p1, f, f, 1, 0);
            p1.pubEnableEvents(AWTEvent.HIERARCHY_EVENT_MASK);
            p1.pubEnableEvents(AWTEvent.HIERARCHY_BOUNDS_EVENT_MASK);
            test1(p1, p1, f, f, 2, 0);
            p1.pubDisableEvents(AWTEvent.HIERARCHY_EVENT_MASK);
            p1.pubDisableEvents(AWTEvent.HIERARCHY_BOUNDS_EVENT_MASK);
            p1.removeHierarchyListener(this);
            p1.removeHierarchyBoundsListener(this);

            p2.addHierarchyListener(this);
            p2.addHierarchyBoundsListener(this);
            test1(p2, p2, f, f, 1, 0);
            p2.pubEnableEvents(AWTEvent.HIERARCHY_EVENT_MASK);
            p2.pubEnableEvents(AWTEvent.HIERARCHY_BOUNDS_EVENT_MASK);
            test1(p2, p2, f, f, 2, 0);
            p2.removeHierarchyListener(this);
            p2.removeHierarchyBoundsListener(this);
            p2.pubDisableEvents(AWTEvent.HIERARCHY_EVENT_MASK);
            p2.pubDisableEvents(AWTEvent.HIERARCHY_BOUNDS_EVENT_MASK);

            p5.pubEnableEvents(AWTEvent.HIERARCHY_EVENT_MASK);
            p5.pubEnableEvents(AWTEvent.HIERARCHY_BOUNDS_EVENT_MASK);
            test1(p5, p5, f, f, 1, 0);
            p5.addHierarchyListener(this);
            p5.addHierarchyBoundsListener(this);
            test1(p5, p5, f, f, 2, 0);
            p5.pubDisableEvents(AWTEvent.HIERARCHY_EVENT_MASK);
            p5.pubDisableEvents(AWTEvent.HIERARCHY_BOUNDS_EVENT_MASK);
            p5.removeHierarchyListener(this);
            p5.removeHierarchyBoundsListener(this);

            p6.pubEnableEvents(AWTEvent.HIERARCHY_EVENT_MASK);
            p6.pubEnableEvents(AWTEvent.HIERARCHY_BOUNDS_EVENT_MASK);
            test1(p6, p6, f, f, 1, 0);
            p6.addHierarchyListener(this);
            p6.addHierarchyBoundsListener(this);
            test1(p6, p6, f, f, 2, 0);
            p6.removeHierarchyListener(this);
            p6.removeHierarchyBoundsListener(this);
            p6.pubDisableEvents(AWTEvent.HIERARCHY_EVENT_MASK);
            p6.pubDisableEvents(AWTEvent.HIERARCHY_BOUNDS_EVENT_MASK);

            l3.addHierarchyListener(this);
            l3.addHierarchyBoundsListener(this);
            test1(l3, l3, p1, f, 1, 0);
            test1(l3, p1, f, f, 1, 1);
            l3.pubEnableEvents(AWTEvent.HIERARCHY_EVENT_MASK);
            l3.pubEnableEvents(AWTEvent.HIERARCHY_BOUNDS_EVENT_MASK);
            test1(l3, l3, p1, f, 2, 0);
            test1(l3, p1, f, f, 2, 2);
            l3.pubDisableEvents(AWTEvent.HIERARCHY_EVENT_MASK);
            l3.pubDisableEvents(AWTEvent.HIERARCHY_BOUNDS_EVENT_MASK);
            l3.removeHierarchyListener(this);
            l3.removeHierarchyBoundsListener(this);

            l4.addHierarchyListener(this);
            l4.addHierarchyBoundsListener(this);
            test1(l4, l4, p1, f, 1, 0);
            test1(l4, p1, f, f, 1, 1);
            l4.pubEnableEvents(AWTEvent.HIERARCHY_EVENT_MASK);
            l4.pubEnableEvents(AWTEvent.HIERARCHY_BOUNDS_EVENT_MASK);
            test1(l4, l4, p1, f, 2, 0);
            test1(l4, p1, f, f, 2, 2);
            l4.removeHierarchyListener(this);
            l4.removeHierarchyBoundsListener(this);
            l4.pubDisableEvents(AWTEvent.HIERARCHY_EVENT_MASK);
            l4.pubDisableEvents(AWTEvent.HIERARCHY_BOUNDS_EVENT_MASK);

            l5.pubEnableEvents(AWTEvent.HIERARCHY_EVENT_MASK);
            l5.pubEnableEvents(AWTEvent.HIERARCHY_BOUNDS_EVENT_MASK);
            test1(l5, l5, p2, f, 1, 0);
            test1(l5, p2, f, f, 1, 1);
            l5.addHierarchyListener(this);
            l5.addHierarchyBoundsListener(this);
            test1(l5, l5, p2, f, 2, 0);
            test1(l5, p2, f, f, 2, 2);
            l5.pubDisableEvents(AWTEvent.HIERARCHY_EVENT_MASK);
            l5.pubDisableEvents(AWTEvent.HIERARCHY_BOUNDS_EVENT_MASK);
            l5.removeHierarchyListener(this);
            l5.removeHierarchyBoundsListener(this);

            l6.pubEnableEvents(AWTEvent.HIERARCHY_EVENT_MASK);
            l6.pubEnableEvents(AWTEvent.HIERARCHY_BOUNDS_EVENT_MASK);
            test1(l6, l6, p2, f, 1, 0);
            test1(l6, p2, f, f, 1, 1);
            l6.addHierarchyListener(this);
            l6.addHierarchyBoundsListener(this);
            test1(l6, l6, p2, f, 2, 0);
            test1(l6, p2, f, f, 2, 2);
            l6.removeHierarchyListener(this);
            l6.removeHierarchyBoundsListener(this);
            l6.pubDisableEvents(AWTEvent.HIERARCHY_EVENT_MASK);
            l6.pubDisableEvents(AWTEvent.HIERARCHY_BOUNDS_EVENT_MASK);

            p3.addHierarchyListener(this);
            p3.addHierarchyBoundsListener(this);
            test1(p3, p3, p5, f, 1, 0);
            test1(p3, p5, f, f, 1, 1);
            p3.pubEnableEvents(AWTEvent.HIERARCHY_EVENT_MASK);
            p3.pubEnableEvents(AWTEvent.HIERARCHY_BOUNDS_EVENT_MASK);
            test1(p3, p3, p5, f, 2, 0);
            test1(p3, p5, f, f, 2, 2);
            p3.pubDisableEvents(AWTEvent.HIERARCHY_EVENT_MASK);
            p3.pubDisableEvents(AWTEvent.HIERARCHY_BOUNDS_EVENT_MASK);
            p3.removeHierarchyListener(this);
            p3.removeHierarchyBoundsListener(this);

            p4.addHierarchyListener(this);
            p4.addHierarchyBoundsListener(this);
            test1(p4, p4, p6, f, 1, 0);
            test1(p4, p6, f, f, 1, 1);
            p4.pubEnableEvents(AWTEvent.HIERARCHY_EVENT_MASK);
            p4.pubEnableEvents(AWTEvent.HIERARCHY_BOUNDS_EVENT_MASK);
            test1(p4, p4, p6, f, 2, 0);
            test1(p4, p6, f, f, 2, 2);
            p4.removeHierarchyListener(this);
            p4.removeHierarchyBoundsListener(this);
            p4.pubDisableEvents(AWTEvent.HIERARCHY_EVENT_MASK);
            p4.pubDisableEvents(AWTEvent.HIERARCHY_BOUNDS_EVENT_MASK);

            l7.pubEnableEvents(AWTEvent.HIERARCHY_EVENT_MASK);
            l7.pubEnableEvents(AWTEvent.HIERARCHY_BOUNDS_EVENT_MASK);
            test1(l7, l7, p3, f, 1, 0);
            test1(l7, p3, p5, f, 1, 1);
            test1(l7, p5, f, f, 1, 1);
            l7.addHierarchyListener(this);
            l7.addHierarchyBoundsListener(this);
            test1(l7, l7, p3, f, 2, 0);
            test1(l7, p3, p5, f, 2, 2);
            test1(l7, p5, f, f, 2, 2);
            l7.pubDisableEvents(AWTEvent.HIERARCHY_EVENT_MASK);
            l7.pubDisableEvents(AWTEvent.HIERARCHY_BOUNDS_EVENT_MASK);
            l7.removeHierarchyListener(this);
            l7.removeHierarchyBoundsListener(this);

            l8.pubEnableEvents(AWTEvent.HIERARCHY_EVENT_MASK);
            l8.pubEnableEvents(AWTEvent.HIERARCHY_BOUNDS_EVENT_MASK);
            test1(l8, l8, p3, f, 1, 0);
            test1(l8, p3, p5, f, 1, 1);
            test1(l8, p5, f, f, 1, 1);
            l8.addHierarchyListener(this);
            l8.addHierarchyBoundsListener(this);
            test1(l8, l8, p3, f, 2, 0);
            test1(l8, p3, p5, f, 2, 2);
            test1(l8, p5, f, f, 2, 2);
            l8.removeHierarchyListener(this);
            l8.removeHierarchyBoundsListener(this);
            l8.pubDisableEvents(AWTEvent.HIERARCHY_EVENT_MASK);
            l8.pubDisableEvents(AWTEvent.HIERARCHY_BOUNDS_EVENT_MASK);

            l9.addHierarchyListener(this);
            l9.addHierarchyBoundsListener(this);
            test1(l9, l9, p4, f, 1, 0);
            test1(l9, p4, p6, f, 1, 1);
            test1(l9, p6, f, f, 1, 1);
            l9.pubEnableEvents(AWTEvent.HIERARCHY_EVENT_MASK);
            l9.pubEnableEvents(AWTEvent.HIERARCHY_BOUNDS_EVENT_MASK);
            test1(l9, l9, p4, f, 2, 0);
            test1(l9, p4, p6, f, 2, 2);
            test1(l9, p6, f, f, 2, 2);
            l9.pubDisableEvents(AWTEvent.HIERARCHY_EVENT_MASK);
            l9.pubDisableEvents(AWTEvent.HIERARCHY_BOUNDS_EVENT_MASK);
            l9.removeHierarchyListener(this);
            l9.removeHierarchyBoundsListener(this);

            l10.addHierarchyListener(this);
            l10.addHierarchyBoundsListener(this);
            test1(l10, l10, p4, f, 1, 0);
            test1(l10, p4, p6, f, 1, 1);
            test1(l10, p6, f, f, 1, 1);
            l10.pubEnableEvents(AWTEvent.HIERARCHY_EVENT_MASK);
            l10.pubEnableEvents(AWTEvent.HIERARCHY_BOUNDS_EVENT_MASK);
            test1(l10, l10, p4, f, 2, 0);
            test1(l10, p4, p6, f, 2, 2);
            test1(l10, p6, f, f, 2, 2);
            l10.removeHierarchyListener(this);
            l10.removeHierarchyBoundsListener(this);
            l10.pubDisableEvents(AWTEvent.HIERARCHY_EVENT_MASK);
            l10.pubDisableEvents(AWTEvent.HIERARCHY_BOUNDS_EVENT_MASK);



            // test4

            l1.pubEnableEvents(AWTEvent.HIERARCHY_EVENT_MASK);
            l10.pubEnableEvents(AWTEvent.HIERARCHY_EVENT_MASK);
            l1.pubEnableEvents(AWTEvent.HIERARCHY_BOUNDS_EVENT_MASK);
            l10.pubEnableEvents(AWTEvent.HIERARCHY_BOUNDS_EVENT_MASK);
            test2(l1, f, l10, p4, f);
            l1.pubDisableEvents(AWTEvent.HIERARCHY_EVENT_MASK);
            l10.pubDisableEvents(AWTEvent.HIERARCHY_EVENT_MASK);
            l1.pubDisableEvents(AWTEvent.HIERARCHY_BOUNDS_EVENT_MASK);
            l10.pubDisableEvents(AWTEvent.HIERARCHY_BOUNDS_EVENT_MASK);

            l2.pubEnableEvents(AWTEvent.HIERARCHY_EVENT_MASK);
            l9.pubEnableEvents(AWTEvent.HIERARCHY_EVENT_MASK);
            l2.pubEnableEvents(AWTEvent.HIERARCHY_BOUNDS_EVENT_MASK);
            l9.pubEnableEvents(AWTEvent.HIERARCHY_BOUNDS_EVENT_MASK);
            test2(l2, f, l9, p4, f);
            l2.pubDisableEvents(AWTEvent.HIERARCHY_EVENT_MASK);
            l9.pubDisableEvents(AWTEvent.HIERARCHY_EVENT_MASK);
            l2.pubDisableEvents(AWTEvent.HIERARCHY_BOUNDS_EVENT_MASK);
            l9.pubDisableEvents(AWTEvent.HIERARCHY_BOUNDS_EVENT_MASK);

            l3.pubEnableEvents(AWTEvent.HIERARCHY_EVENT_MASK);
            l8.pubEnableEvents(AWTEvent.HIERARCHY_EVENT_MASK);
            l3.pubEnableEvents(AWTEvent.HIERARCHY_BOUNDS_EVENT_MASK);
            l8.pubEnableEvents(AWTEvent.HIERARCHY_BOUNDS_EVENT_MASK);
            test2(l3, p1, l8, p3, f);
            l3.pubDisableEvents(AWTEvent.HIERARCHY_EVENT_MASK);
            l8.pubDisableEvents(AWTEvent.HIERARCHY_EVENT_MASK);
            l3.pubDisableEvents(AWTEvent.HIERARCHY_BOUNDS_EVENT_MASK);
            l8.pubDisableEvents(AWTEvent.HIERARCHY_BOUNDS_EVENT_MASK);

            l4.pubEnableEvents(AWTEvent.HIERARCHY_EVENT_MASK);
            l7.pubEnableEvents(AWTEvent.HIERARCHY_EVENT_MASK);
            l4.pubEnableEvents(AWTEvent.HIERARCHY_BOUNDS_EVENT_MASK);
            l7.pubEnableEvents(AWTEvent.HIERARCHY_BOUNDS_EVENT_MASK);
            test2(l4, p1, l7, p3, f);
            l4.pubDisableEvents(AWTEvent.HIERARCHY_EVENT_MASK);
            l7.pubDisableEvents(AWTEvent.HIERARCHY_EVENT_MASK);
            l4.pubDisableEvents(AWTEvent.HIERARCHY_BOUNDS_EVENT_MASK);
            l7.pubDisableEvents(AWTEvent.HIERARCHY_BOUNDS_EVENT_MASK);

            l5.pubEnableEvents(AWTEvent.HIERARCHY_EVENT_MASK);
            l6.pubEnableEvents(AWTEvent.HIERARCHY_EVENT_MASK);
            l5.pubEnableEvents(AWTEvent.HIERARCHY_BOUNDS_EVENT_MASK);
            l6.pubEnableEvents(AWTEvent.HIERARCHY_BOUNDS_EVENT_MASK);
            test2(l5, p2, l6, p2, f);
            l5.pubDisableEvents(AWTEvent.HIERARCHY_EVENT_MASK);
            l6.pubDisableEvents(AWTEvent.HIERARCHY_EVENT_MASK);
            l5.pubDisableEvents(AWTEvent.HIERARCHY_BOUNDS_EVENT_MASK);
            l6.pubDisableEvents(AWTEvent.HIERARCHY_BOUNDS_EVENT_MASK);

            p1.pubEnableEvents(AWTEvent.HIERARCHY_EVENT_MASK);
            p4.pubEnableEvents(AWTEvent.HIERARCHY_EVENT_MASK);
            p1.pubEnableEvents(AWTEvent.HIERARCHY_BOUNDS_EVENT_MASK);
            p4.pubEnableEvents(AWTEvent.HIERARCHY_BOUNDS_EVENT_MASK);
            test2(p1, f, p4, p6, f);
            p1.pubDisableEvents(AWTEvent.HIERARCHY_EVENT_MASK);
            p4.pubDisableEvents(AWTEvent.HIERARCHY_EVENT_MASK);
            p1.pubDisableEvents(AWTEvent.HIERARCHY_BOUNDS_EVENT_MASK);
            p4.pubDisableEvents(AWTEvent.HIERARCHY_BOUNDS_EVENT_MASK);

            p2.pubEnableEvents(AWTEvent.HIERARCHY_EVENT_MASK);
            p3.pubEnableEvents(AWTEvent.HIERARCHY_EVENT_MASK);
            p2.pubEnableEvents(AWTEvent.HIERARCHY_BOUNDS_EVENT_MASK);
            p3.pubEnableEvents(AWTEvent.HIERARCHY_BOUNDS_EVENT_MASK);
            test2(p2, f, p3, p5, f);
            p2.pubDisableEvents(AWTEvent.HIERARCHY_EVENT_MASK);
            p3.pubDisableEvents(AWTEvent.HIERARCHY_EVENT_MASK);
            p2.pubDisableEvents(AWTEvent.HIERARCHY_BOUNDS_EVENT_MASK);
            p3.pubDisableEvents(AWTEvent.HIERARCHY_BOUNDS_EVENT_MASK);

            p5.pubEnableEvents(AWTEvent.HIERARCHY_EVENT_MASK);
            p6.pubEnableEvents(AWTEvent.HIERARCHY_EVENT_MASK);
            p5.pubEnableEvents(AWTEvent.HIERARCHY_BOUNDS_EVENT_MASK);
            p6.pubEnableEvents(AWTEvent.HIERARCHY_BOUNDS_EVENT_MASK);
            test2(p5, f, p6, f, f);
            p5.pubDisableEvents(AWTEvent.HIERARCHY_EVENT_MASK);
            p6.pubDisableEvents(AWTEvent.HIERARCHY_EVENT_MASK);
            p5.pubDisableEvents(AWTEvent.HIERARCHY_BOUNDS_EVENT_MASK);
            p6.pubDisableEvents(AWTEvent.HIERARCHY_BOUNDS_EVENT_MASK);
        }

        System.out.println("passed");
    }
}
