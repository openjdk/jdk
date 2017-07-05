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

import com.sun.swingset3.demos.tabbedpane.TabbedPaneDemo;
import static com.sun.swingset3.demos.tabbedpane.TabbedPaneDemo.*;
import static org.jemmy2ext.JemmyExt.getLabeledContainerOperator;
import static org.testng.AssertJUnit.*;
import org.testng.annotations.Test;
import org.netbeans.jemmy.ClassReference;
import org.netbeans.jemmy.operators.ContainerOperator;
import org.netbeans.jemmy.operators.JFrameOperator;
import org.netbeans.jemmy.operators.JRadioButtonOperator;
import org.netbeans.jemmy.operators.JTabbedPaneOperator;
import static org.jemmy2ext.JemmyExt.captureDebugInfoOnFail;

/*
 * @test
 * @key headful
 * @summary Verifies SwingSet3 TabbedPaneDemo by iterating through tab placement
 *          positions, opening each tab and verifying the the tab gets selected.
 *
 * @library /sanity/client/lib/jemmy/src
 * @library /sanity/client/lib/Jemmy2Ext/src
 * @library /sanity/client/lib/SwingSet3/src
 * @build org.jemmy2ext.JemmyExt
 * @build com.sun.swingset3.demos.tabbedpane.TabbedPaneDemo
 * @run testng TabbedPaneDemoTest
 */
public class TabbedPaneDemoTest {

    @Test
    public void test() throws Exception {
        captureDebugInfoOnFail(() -> {
            new ClassReference(TabbedPaneDemo.class.getCanonicalName()).startApplication();

            JFrameOperator mainFrame = new JFrameOperator(DEMO_TITLE);

            for (String tp : new String[]{TOP, LEFT, BOTTOM, RIGHT}) {
                testTabs(mainFrame, tp);
            }
        });
    }

    public void testTabs(JFrameOperator mainFrame, String tabPlacement) throws Exception {
        ContainerOperator<?> rbCont = getLabeledContainerOperator(mainFrame, TAB_PLACEMENT);
        new JRadioButtonOperator(rbCont, tabPlacement).doClick();

        final String[] tabTitles = new String[]{CAMILLE, MIRANDA, EWAN, BOUNCE};
        for (int i = 0; i < tabTitles.length; i++) {
            String pageTitle = tabTitles[i];
            JTabbedPaneOperator tabOperator = new JTabbedPaneOperator(mainFrame);
            tabOperator.selectPage(pageTitle);

            assertEquals("Selected tab is selected", i, tabOperator.getSelectedIndex());
        }
    }

}
