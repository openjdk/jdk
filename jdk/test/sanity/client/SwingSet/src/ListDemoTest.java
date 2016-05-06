/*
 * Copyright (c) 2010, 2016, Oracle and/or its affiliates. All rights reserved.
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

import org.jtregext.GuiTestListener;
import com.sun.swingset3.demos.list.ListDemo;
import static com.sun.swingset3.demos.list.ListDemo.DEMO_TITLE;
import static org.testng.AssertJUnit.*;
import org.testng.annotations.Test;
import static org.jemmy2ext.JemmyExt.getLabeledContainerOperator;
import org.netbeans.jemmy.ClassReference;
import org.netbeans.jemmy.operators.JCheckBoxOperator;
import org.netbeans.jemmy.operators.JFrameOperator;
import org.netbeans.jemmy.operators.JListOperator;
import org.testng.annotations.Listeners;

/*
 * @test
 * @key headful
 * @summary Verifies SwingSet3 ListDemo page by checking and unchecking all
 *          the checkboxes on the page and verifying the number of items in the
 *          list.
 *
 * @library /sanity/client/lib/jemmy/src
 * @library /sanity/client/lib/Extensions/src
 * @library /sanity/client/lib/SwingSet3/src
 * @build org.jemmy2ext.JemmyExt
 * @build com.sun.swingset3.demos.list.ListDemo
 * @run testng ListDemoTest
 */
@Listeners(GuiTestListener.class)
public class ListDemoTest {

    private static final int CHECKBOX_COUNT = 50;

    @Test
    public void test() throws Exception {

        new ClassReference(ListDemo.class.getCanonicalName()).startApplication();

        JFrameOperator frame = new JFrameOperator(DEMO_TITLE);
        JListOperator listOp = new JListOperator(frame);

        // Check *NO* Prefix and Suffixes Marked
        for (int i = 0; i < CHECKBOX_COUNT; i++) {
            JCheckBoxOperator checkBox = getJCheckBoxOperator(frame, i);
            checkBox.changeSelection(false);
        }
        System.out.println("######## Number of Items = " + listOp.getModel().getSize());
        assertEquals("Select None number of items is correct", 0, listOp.getModel().getSize());

        // Check *ALL* Prefix and Suffixes Marked
        for (int i = 0; i < CHECKBOX_COUNT; i++) {
            JCheckBoxOperator checkBox = getJCheckBoxOperator(frame, i);
            checkBox.changeSelection(true);
        }
        System.out.println("######## Number of Items = " + listOp.getModel().getSize());
        assertEquals("Select All number of items is correct", CHECKBOX_COUNT / 2 * CHECKBOX_COUNT / 2, listOp.getModel().getSize());

        // Check *ALL* Prefix and *NO* Suffixes Marked
        for (int i = 0; i < CHECKBOX_COUNT; i++) {
            JCheckBoxOperator checkBox = getJCheckBoxOperator(frame, i);
            if (i < CHECKBOX_COUNT / 2) {
                checkBox.changeSelection(true);
            } else {
                checkBox.changeSelection(false);
            }
        }
        System.out.println("######## Number of Items = " + listOp.getModel().getSize());
        assertEquals("Select All Prefixes and NO Suffixes number of items is correct", 0, listOp.getModel().getSize());

        // Check *NO* Prefix and *ALL* Suffixes Marked
        for (int i = 0; i < CHECKBOX_COUNT; i++) {
            JCheckBoxOperator checkBox = getJCheckBoxOperator(frame, i);
            if (i < CHECKBOX_COUNT / 2) {
                checkBox.changeSelection(false);
            } else {
                checkBox.changeSelection(true);
            }
        }
        System.out.println("######## Number of Items = " + listOp.getModel().getSize());
        assertEquals("Select NO Prefixes and All Suffixes number of items is correct", 0, listOp.getModel().getSize());
    }

    private JCheckBoxOperator getJCheckBoxOperator(JFrameOperator frame, int index) {

        // We map first half of indexes to the Prefixes panel and the second half
        // to the Suffixes panel
        String labelText;
        int subindex;
        if (index < CHECKBOX_COUNT / 2) {
            labelText = "Prefixes";
            subindex = index;
        } else {
            labelText = "Suffixes";
            subindex = index - CHECKBOX_COUNT / 2;
        }

        return new JCheckBoxOperator(getLabeledContainerOperator(frame, labelText), subindex);
    }

}
