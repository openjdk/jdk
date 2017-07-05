/*
 * Copyright (c) 2011, 2016, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.swingset3.demos.button.ButtonDemo;
import org.jtregext.GuiTestListener;
import java.awt.Point;
import java.awt.Robot;
import java.awt.event.InputEvent;
import java.awt.image.BufferedImage;
import org.netbeans.jemmy.ClassReference;
import org.netbeans.jemmy.image.StrictImageComparator;
import org.netbeans.jemmy.operators.JButtonOperator;
import org.netbeans.jemmy.operators.JFrameOperator;
import static org.jemmy2ext.JemmyExt.*;
import org.testng.annotations.Test;
import static com.sun.swingset3.demos.button.ButtonDemo.*;
import org.testng.annotations.Listeners;

/*
 * @test
 * @key headful screenshots
 * @summary Verifies buttons on SwingSet3 ButtonDemo page by clicking each
 *          button, taking its screenshots and checking that pressed button
 *          image is different from initial button image.
 *
 * @library /sanity/client/lib/jemmy/src
 * @library /sanity/client/lib/Extensions/src
 * @library /sanity/client/lib/SwingSet3/src
 * @build org.jemmy2ext.JemmyExt
 * @build com.sun.swingset3.demos.button.ButtonDemo
 * @run testng ButtonDemoScreenshotTest
 */
@Listeners(GuiTestListener.class)
public class ButtonDemoScreenshotTest {

    private static final int BUTTON_COUNT = 6; // TODO: Decide about "open browser" buttons (value was 8 originally)

    @Test
    public void test() throws Exception {
        Robot rob = new Robot();

        new ClassReference(ButtonDemo.class.getCanonicalName()).startApplication();

        JFrameOperator mainFrame = new JFrameOperator(DEMO_TITLE);
        waitImageIsStill(rob, mainFrame);

        // Check all the buttons
        for (int i = 0; i < BUTTON_COUNT; i++) {
            checkButton(mainFrame, i, rob);
        }
    }

    public void checkButton(JFrameOperator jfo, int i, Robot rob) {
        JButtonOperator button = new JButtonOperator(jfo, i);

        Point loc = button.getLocationOnScreen();
        rob.mouseMove(loc.x, loc.y);

        BufferedImage initialButtonImage = capture(rob, button);
        assertNotBlack(initialButtonImage);
        save(initialButtonImage, "button" + i + "_0initial.png");
        rob.mousePress(InputEvent.BUTTON1_MASK);
        try {
            waitPressed(button);
            BufferedImage pressedButtonImage = capture(rob, button);
            assertNotBlack(pressedButtonImage);
            save(pressedButtonImage, "button" + i + "_1pressed.png");

            StrictImageComparator sComparator = new StrictImageComparator();
            assertNotEquals("Button " + i + " Test", sComparator, initialButtonImage, pressedButtonImage);
        } finally {
            rob.mouseRelease(InputEvent.BUTTON1_MASK);
        }
    }

}
