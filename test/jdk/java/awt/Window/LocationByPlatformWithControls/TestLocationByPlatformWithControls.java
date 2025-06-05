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
 * @bug 4102292
 * @summary Tests that location by platform works with other APIs
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual TestLocationByPlatformWithControls
 */

import java.awt.BorderLayout;
import java.awt.Button;
import java.awt.Checkbox;
import java.awt.Frame;
import java.awt.Label;
import java.awt.Panel;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.Vector;

public class TestLocationByPlatformWithControls extends Frame
    implements ActionListener, ItemListener {
    Panel northP;
    Panel centerP;
    Checkbox undecoratedCB;
    Checkbox defaultLocationCB;
    Checkbox visibleCB;
    Checkbox iconifiedCB;
    Checkbox maximizedCB;
    Button createB;
    Button packB;
    Button moveB;
    Button resizeB;
    Button reshapeB;
    Button disposeB;
    Vector frames;
    public static void main(String[] args) throws Exception {
        String INSTRUCTIONS = """
            This test is to check that LocationByPlatform works with other
            controls API.
            1) Create New Frame by clicking on "Create" Button in
            "TestLocationByPlatformWithControls" window.
            2) Initially this Frame will not be visible, Click on checkbox
            "LocationByPlatform" to set default platform location for the frame
            and then click on checkbox "Visible" to see that Frame is displayed
            at default offsets.
            3) Now you can play with different controls like Iconified,
            Maximized, Pack, Move, Resize and Reshape to verify that these
            controls work properly with the Frame.
            4) At the end dispose the Frame by clicking on "Dispose" button.
            5) Also we can do verify this for Undecorated Frame but for that we
            need to follow same steps but in step 2 before we click on checkbox
            "Visible", select "Undecorated" checkbox along with
            "LocationByPlatform".
            6) If everything works properly test is passed, otherwise failed.
            """;

        PassFailJFrame.builder()
            .title("Test Instructions")
            .instructions(INSTRUCTIONS)
            .columns(40)
            .testUI(TestLocationByPlatformWithControls::new)
            .logArea(4)
            .build()
            .awaitAndCheck();
    }

    public TestLocationByPlatformWithControls() {
        northP = new Panel();
        centerP = new Panel();

        undecoratedCB = new Checkbox("Undecorated");
        defaultLocationCB = new Checkbox("LocationByPlatform");
        visibleCB = new Checkbox("Visible");
        iconifiedCB = new Checkbox("Iconified");
        maximizedCB = new Checkbox("Maximized");

        createB = new Button("Create");
        packB = new Button("Pack");
        moveB = new Button("Move");
        resizeB = new Button("Resize");
        reshapeB = new Button("Reshape");
        disposeB = new Button("Dispose");

        frames = new Vector(10);
        this.setTitle("TestLocationByPlatformWithControls");
        this.setLayout(new BorderLayout());
        this.add(northP, BorderLayout.NORTH);

        northP.add(new Label("New Frame"));

        createB.addActionListener(this);
        northP.add(createB);

        centerP.setEnabled(false);
        this.add(centerP, BorderLayout.CENTER);

        centerP.add(new Label("Last Frame"));

        centerP.add(defaultLocationCB);
        defaultLocationCB.addItemListener(this);

        centerP.add(undecoratedCB);
        undecoratedCB.addItemListener(this);

        centerP.add(iconifiedCB);
        iconifiedCB.addItemListener(this);

        centerP.add(maximizedCB);
        maximizedCB.addItemListener(this);

        centerP.add(visibleCB);
        visibleCB.addItemListener(this);

        packB.addActionListener(this);
        centerP.add(packB);

        moveB.addActionListener(this);
        centerP.add(moveB);

        resizeB.addActionListener(this);
        centerP.add(resizeB);

        reshapeB.addActionListener(this);
        centerP.add(reshapeB);

        disposeB.addActionListener(this);
        centerP.add(disposeB);
        this.pack();
    }

    public void actionPerformed(ActionEvent e) {
        if (e.getSource() ==  createB) {
            Frame frame = new Frame();
            frame.setSize(200, 200);
            frames.add(frame);
            updateControls(frame);
            Panel panel = new Panel();
            frame.add(panel);
            panel.add(new Button ("Test Button"));
            panel.add(new Button ("Test Button 1"));
            panel.add(new Button ("Test Button  2"));
            panel.add(new Button ("Test Button   3"));
            centerP.setEnabled(true);
            return;
        }

        if (frames.isEmpty()) {
            return;
        }

        Frame last = (Frame)frames.lastElement();

        if (e.getSource() == packB) {
            last.pack();
        } else
        if (e.getSource() == moveB) {
            int x = (int)(Math.random() * 200);
            int y = (int)(Math.random() * 200);
            last.setLocation(x, y);
        } else
        if (e.getSource() == resizeB) {
            int w = (int)(Math.random() * 200);
            int h = (int)(Math.random() * 200);
            last.setSize(w, h);
        } else
        if (e.getSource() == reshapeB) {
            int x = (int)(Math.random() * 200);
            int y = (int)(Math.random() * 200);
            int w = (int)(Math.random() * 200);
            int h = (int)(Math.random() * 200);
            last.setBounds(x, y, w, h);
        } else
        if (e.getSource() == disposeB) {
            last.dispose();
            frames.remove(frames.size() - 1);
            if (frames.isEmpty()) {
                updateControls(null);
                centerP.setEnabled(false);
                return;
            }
            last = (Frame)frames.lastElement();
        }
        updateControls(last);
    }

    public void updateControls(Frame f) {
        undecoratedCB.setState(f != null ?
            f.isUndecorated() : false);
        defaultLocationCB.setState(f != null ?
            f.isLocationByPlatform() : false);
        visibleCB.setState(f != null ?
            f.isVisible() : false);
        iconifiedCB.setState(f != null ?
            (f.getExtendedState() & Frame.ICONIFIED) != 0 : false);
        maximizedCB.setState(f != null ?
            (f.getExtendedState() & Frame.MAXIMIZED_BOTH) != 0 : false);
    }

    public void itemStateChanged(ItemEvent e) {
        Frame last = (Frame)frames.lastElement();
        try {
            boolean state = e.getStateChange() == ItemEvent.SELECTED;
            if (e.getSource() == visibleCB) {
                last.setVisible(state);
            } else
            if (e.getSource() == defaultLocationCB) {
                last.setLocationByPlatform(state);
            } else
            if (e.getSource() == undecoratedCB) {
                last.setUndecorated(state);
            } else
            if (e.getSource() == iconifiedCB) {
                if (state) {
                    last.setExtendedState(last.getExtendedState() |
                        Frame.ICONIFIED);
                } else {
                    last.setExtendedState(last.getExtendedState() &
                        ~Frame.ICONIFIED);
                }
            } else
            if (e.getSource() == maximizedCB) {
                if (state) {
                    last.setExtendedState(last.getExtendedState() |
                        Frame.MAXIMIZED_BOTH);
                } else {
                    last.setExtendedState(last.getExtendedState() &
                        ~Frame.MAXIMIZED_BOTH);
                }
            }
        } catch (Throwable ex) {
            PassFailJFrame.log(ex.getMessage());
        } finally {
            updateControls(last);
        }
    }

    @Override
    public void dispose() {
        while (!frames.isEmpty()) {
            Frame last = (Frame)frames.lastElement();
            last.dispose();
            frames.remove(frames.size() - 1);
        }
    }
}
