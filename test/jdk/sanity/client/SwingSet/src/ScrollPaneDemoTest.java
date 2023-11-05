/*
 * Copyright (c) 2010, 2022, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.swingset3.demos.ResourceManager;
import org.jemmy2ext.JemmyExt;
import org.jtregext.GuiTestListener;
import com.sun.swingset3.demos.scrollpane.ScrollPaneDemo;
import static com.sun.swingset3.demos.scrollpane.ScrollPaneDemo.DEMO_TITLE;
import static org.testng.AssertJUnit.*;

import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.UIManager;

import org.netbeans.jemmy.operators.ContainerOperator;
import org.netbeans.jemmy.operators.JLabelOperator;
import org.netbeans.jemmy.util.Dumper;
import org.testng.annotations.Test;
import org.netbeans.jemmy.ClassReference;
import org.netbeans.jemmy.operators.JFrameOperator;
import org.netbeans.jemmy.operators.JScrollPaneOperator;
import org.testng.annotations.Listeners;

import java.awt.MediaTracker;

/*
 * @test
 * @key headful
 * @summary Verifies SwingSet3 ScrollPaneDemo by scrolling to bottom, to top,
 *          to left and to right and checking scroll bar values.
 *
 * @library /sanity/client/lib/jemmy/src
 * @library /sanity/client/lib/Extensions/src
 * @library /sanity/client/lib/SwingSet3/src
 * @modules java.desktop
 *          java.logging
 * @build org.jemmy2ext.JemmyExt
 * @build com.sun.swingset3.demos.scrollpane.ScrollPaneDemo
 * @run testng/timeout=600 ScrollPaneDemoTest

 */
@Listeners(GuiTestListener.class)
public class ScrollPaneDemoTest {

    public static final String IMAGE_DESCRIPTION =
            new ResourceManager(ScrollPaneDemo.class).getString("ScrollPaneDemo.crayons");

    @Test(dataProvider = "availableLookAndFeels", dataProviderClass = TestHelpers.class)
    public void test(String lookAndFeel) throws Exception {
        UIManager.setLookAndFeel(lookAndFeel);

        new ClassReference(ScrollPaneDemo.class.getName()).startApplication();

        JFrameOperator frame = new JFrameOperator(DEMO_TITLE);
        ContainerOperator<ScrollPaneDemo> scrollPaneDemo =
                new ContainerOperator<>(frame, c -> c instanceof ScrollPaneDemo);
        JScrollPaneOperator jspo = new JScrollPaneOperator(scrollPaneDemo);

        // Set initial scrollbar positions
        int initialVerticalValue = jspo.getVerticalScrollBar().getValue();
        int initialHorizontalValue = jspo.getHorizontalScrollBar().getValue();

        System.out.println("Initial Vertical Value = " + jspo.getVerticalScrollBar().getValue());
        System.out.println("Initial HoriZontal Value = " + jspo.getHorizontalScrollBar().getValue());

        JLabelOperator imageLabel = new JLabelOperator(scrollPaneDemo, c ->
            c instanceof JLabel label && label.getIcon() instanceof ImageIcon imageIcon &&
                    imageIcon.getDescription().equals(IMAGE_DESCRIPTION));
        imageLabel.waitState(c -> ((ImageIcon)((JLabel)c).getIcon()).getImageLoadStatus() == MediaTracker.COMPLETE);

        //this additional instrumentation is related to JDK-8225013
        //after the image has been completely loaded, the UI is supposed to be functional
        //screenshot and dump are created to capture the state of the UI, should the test fail again
        JemmyExt.save(JemmyExt.capture(), "after-image-load");
        Dumper.dumpAll("after-image-load-" + JemmyExt.lafShortName() + "xml");

        // Check scroll to Bottom
        {
            jspo.scrollToBottom();
            int currentValue = jspo.getVerticalScrollBar().getValue();
            System.out.println("Final Value = " + currentValue);
            assertTrue("Scroll to Bottom of Pane "
                    + "(initialVerticalValue, actual value: " + initialVerticalValue + " "
                    + "< currentValue, actual value = " + currentValue + ")",
                    initialVerticalValue < currentValue);
        }

        // Check scroll to Top
        {
            jspo.scrollToTop();
            int currentValue = jspo.getVerticalScrollBar().getValue();
            System.out.println("Top Scroll Final Value = " + currentValue);
            assertTrue("Scroll to Top of Pane "
                    + "(initialVerticalValue, actual value: " + initialVerticalValue + " "
                    + "> currentValue, actual value = " + currentValue + ")",
                    initialVerticalValue > currentValue);
        }

        // Check scroll to Left
        {
            jspo.scrollToLeft();
            int currentValue = jspo.getHorizontalScrollBar().getValue();
            System.out.println("Scroll to Left Final Value = " + currentValue);
            assertTrue("Scroll to Left of Pane "
                    + "(initialHorizontalValue, actual value: " + initialHorizontalValue + " "
                    + "> currentValue, actual value = " + currentValue + ")",
                    initialHorizontalValue > currentValue);
        }

        // Check scroll to Right
        {
            jspo.scrollToRight();
            int currentValue = jspo.getHorizontalScrollBar().getValue();
            System.out.println("Scroll to Right Final Value = " + currentValue);
            assertTrue("Scroll to Right of Pane "
                    + "(initialHorizontalValue, actual value: " + initialHorizontalValue + " "
                    + "< currentValue, actual value = " + currentValue + ")",
                    initialHorizontalValue < currentValue);
        }
    }

}
