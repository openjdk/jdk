/*
 * Copyright (c) 1998, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Label;
import java.awt.Panel;

/*
 * @test
 * @key headful
 * @summary automated test for "displayable" property on Component
 */

public class Displayable extends Panel {
    Label status = new Label("Displayable Test started...");

    public void init() {
        setLayout(new BorderLayout());
        add("South", status);

        LightDisplayable light = new LightDisplayable();
        shouldNotBeDisplayable(light, "before added to container ");

        HeavyDisplayable heavy = new HeavyDisplayable();
        shouldNotBeDisplayable(heavy, "before added to container ");

        add("West", light);
        add("East", heavy);

        statusMessage("Displayable test completed successfully.");
    }

    protected void addImpl(Component child, Object constraints, int index) {
        super.addImpl(child, constraints, index);
        if (isDisplayable()) {
            shouldBeDisplayable(child, "after added to displayable container ");
        } else {
            shouldNotBeDisplayable(child, "after added to undisplayable container ");
        }
    }

    public void remove(Component child) {
        super.remove(child);
        shouldNotBeDisplayable(child, "after removed from displayable container ");
    }

    public void statusMessage(String msg) {
        status.setText(msg);
        status.invalidate();
        validate();
    }

    public static void shouldNotBeDisplayable(Component c, String why) {
        if (c.isDisplayable()) {
            throw new RuntimeException("Component is displayable "+why+c.getName());
        }
    }

    public static void shouldBeDisplayable(Component c, String why) {
        if (!c.isDisplayable()) {
            throw new RuntimeException("Component is NOT displayable "+why+c.getName());
        }
    }

    public static void main(String[] args) throws Exception {
        EventQueue.invokeAndWait(() -> {
            Frame f = new Frame();
            try {
                Displayable test = new Displayable();
                test.init();
                f.add("North", test);
                f.pack();
            } finally {
                f.dispose();
            }
        });
    }
}

class LightDisplayable extends Component {

    public Dimension getPreferredSize() {
        return new Dimension(50,50);
    }

    public void paint(Graphics g) {
        Dimension size = getSize();
        g.setColor(Color.blue);
        g.fillRect(0, 0, size.width, size.height);
        super.paint(g);
    }

    public void addNotify() {
        Displayable.shouldNotBeDisplayable(this, "before addNotify ");
        super.addNotify();
        Displayable.shouldBeDisplayable(this, "after addNotify ");
    }

    public void removeNotify() {
        Displayable.shouldBeDisplayable(this, "before removeNotify ");
        super.removeNotify();
        Displayable.shouldNotBeDisplayable(this, "after removeNotify ");
    }
}

class HeavyDisplayable extends Panel {

    public Dimension getPreferredSize() {
        return new Dimension(50, 50);
    }

    public void paint(Graphics g) {
        Dimension size = getSize();
        g.setColor(Color.black);
        g.fillRect(0, 0, size.width, size.height);
        super.paint(g);
    }

    public void addNotify() {
        Displayable.shouldNotBeDisplayable(this, "before addNotify ");
        super.addNotify();
        Displayable.shouldBeDisplayable(this, "after addNotify ");
    }

    public void removeNotify() {
        Displayable.shouldBeDisplayable(this, "before removeNotify ");
        super.removeNotify();
        Displayable.shouldNotBeDisplayable(this, "after removeNotify ");
    }
}
