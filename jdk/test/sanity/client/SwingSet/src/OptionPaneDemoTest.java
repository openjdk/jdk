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

import com.sun.swingset3.demos.optionpane.OptionPaneDemo;
import static com.sun.swingset3.demos.optionpane.OptionPaneDemo.*;
import javax.swing.UIManager;
import static org.jemmy2ext.JemmyExt.*;
import static org.testng.AssertJUnit.*;
import org.testng.annotations.Test;
import org.netbeans.jemmy.ClassReference;
import org.netbeans.jemmy.operators.JButtonOperator;
import org.netbeans.jemmy.operators.JComboBoxOperator;
import org.netbeans.jemmy.operators.JDialogOperator;
import org.netbeans.jemmy.operators.JFrameOperator;
import org.netbeans.jemmy.operators.JLabelOperator;
import org.netbeans.jemmy.operators.JTextFieldOperator;


/*
 * @test
 * @key headful
 * @summary Verifies SwingSet3 OptionPaneDemo page by opening all the dialogs
 *          and choosing different options in them.
 *
 * @library /sanity/client/lib/jemmy/src
 * @library /sanity/client/lib/Jemmy2Ext/src
 * @library /sanity/client/lib/SwingSet3/src
 * @build org.jemmy2ext.JemmyExt
 * @build com.sun.swingset3.demos.optionpane.OptionPaneDemo
 * @run testng OptionPaneDemoTest
 */
public class OptionPaneDemoTest {

    public static final String SOME_TEXT_TO_TYPE = "I am some text";
    public static final String MESSAGE = UIManager.getString("OptionPane.messageDialogTitle");
    public static final String OK = "OK";
    public static final String CANCEL = "Cancel";
    public static final String INPUT = UIManager.getString("OptionPane.inputDialogTitle");
    public static final String TEXT_TO_TYPE = "Hooray! I'm a textField";
    public static final String NO = "No";
    public static final String YES = "Yes";
    public static final String SELECT_AN__OPTION = UIManager.getString("OptionPane.titleText");

    @Test
    public void test() throws Exception {
        captureDebugInfoOnFail(() -> {
            new ClassReference(OptionPaneDemo.class.getCanonicalName()).startApplication();

            JFrameOperator frame = new JFrameOperator(DEMO_TITLE);

            showInputDialog(frame);
            showWarningDialog(frame);
            showMessageDialog(frame);
            showComponentDialog(frame);
            showConfirmationDialog(frame);
        });
    }

    public void showInputDialog(JFrameOperator jfo) throws Exception {
        // Cancel with text case
        {
            new JButtonOperator(jfo, INPUT_BUTTON).pushNoBlock();

            JDialogOperator jdo = new JDialogOperator(INPUT);
            JTextFieldOperator jto = new JTextFieldOperator(jdo);
            jto.setText(SOME_TEXT_TO_TYPE);

            assertTrue("Show Input Dialog cancel w/ Text", jdo.isShowing());

            new JButtonOperator(jdo, CANCEL).push();

            assertFalse("Show Input Dialog cancel w/ Text", jdo.isShowing());
        }

        // Cancel with *NO* text case
        {
            new JButtonOperator(jfo, INPUT_BUTTON).pushNoBlock();

            JDialogOperator jdo = new JDialogOperator(INPUT);

            assertTrue("Show Input Dialog cancel w/o Text", jdo.isShowing());

            new JButtonOperator(jdo, CANCEL).push();

            assertFalse("Show Input Dialog cancel w/o Text", jdo.isShowing());
        }

        // Text field has *NO* input
        {
            new JButtonOperator(jfo, INPUT_BUTTON).pushNoBlock();

            JDialogOperator jdo = new JDialogOperator(INPUT);

            assertTrue("Show Input Dialog w/o Input", jdo.isShowing());

            new JButtonOperator(jdo, OK).push();

            assertFalse("Show Input Dialog w/o Input", jdo.isShowing());
        }

        // Text field has input
        {
            final String enteredText = "Rambo";

            new JButtonOperator(jfo, INPUT_BUTTON).pushNoBlock();

            JDialogOperator jdo = new JDialogOperator(INPUT);
            JTextFieldOperator jto = new JTextFieldOperator(jdo);
            jto.setText(enteredText);
            new JButtonOperator(jdo, OK).pushNoBlock();

            JDialogOperator jdo1 = new JDialogOperator(MESSAGE);

            assertTrue("Show Input Dialog w/ Input", jdo1.isShowing());

            final String labelText = enteredText + INPUT_RESPONSE;
            JLabelOperator jLabelOperator = new JLabelOperator(jdo1, labelText);
            assertEquals("Text from the field made it into the dialog", labelText, jLabelOperator.getText());

            new JButtonOperator(jdo1, OK).push();

            assertFalse("Show Input Dialog w/ Input", jdo1.isShowing());
        }
    }

    public void showWarningDialog(JFrameOperator jfo) throws Exception {
        new JButtonOperator(jfo, WARNING_BUTTON).pushNoBlock();

        JDialogOperator jdo = new JDialogOperator(WARNING_TITLE);

        assertTrue("Show Warning Dialog", jdo.isShowing());

        new JButtonOperator(jdo, OK).push();

        assertFalse("Show Warning Dialog", jdo.isShowing());
    }

    public void showMessageDialog(JFrameOperator jfo) throws Exception {
        new JButtonOperator(jfo, MESSAGE_BUTTON).pushNoBlock();

        JDialogOperator jdo = new JDialogOperator(MESSAGE);

        assertTrue("Show Message Dialog", jdo.isShowing());

        new JButtonOperator(jdo, OK).push();

        assertFalse("Show Message Dialog", jdo.isShowing());
    }

    public void showComponentDialog(JFrameOperator jfo) throws Exception {
        // Case: Cancel
        {
            new JButtonOperator(jfo, COMPONENT_BUTTON).pushNoBlock();

            JDialogOperator jdo = new JDialogOperator(COMPONENT_TITLE);

            assertTrue("Show Component Dialog Cancel Option", jdo.isShowing());

            new JButtonOperator(jdo, COMPONENT_OP5).push();

            assertFalse("Show Component Dialog Cancel Option", jdo.isShowing());
        }

        // Case: Yes option selected
        {
            new JButtonOperator(jfo, COMPONENT_BUTTON).pushNoBlock();

            JDialogOperator jdo = new JDialogOperator(COMPONENT_TITLE);
            new JButtonOperator(jdo, COMPONENT_OP1).pushNoBlock();

            JDialogOperator jdo1 = new JDialogOperator(MESSAGE);

            assertTrue("Component Dialog Example Yes Option", jdo1.isShowing());

            final String labelText = COMPONENT_R1;
            JLabelOperator jLabelOperator = new JLabelOperator(jdo1, labelText);
            assertEquals("Dialog contains appropriate text", labelText, jLabelOperator.getText());

            new JButtonOperator(jdo1, OK).push();

            assertFalse("Component Dialog Example Yes Option", jdo1.isShowing());
        }

        // Case: No option selected
        {
            new JButtonOperator(jfo, COMPONENT_BUTTON).pushNoBlock();

            JDialogOperator jdo = new JDialogOperator(COMPONENT_TITLE);
            new JButtonOperator(jdo, COMPONENT_OP2).pushNoBlock();

            JDialogOperator jdo1 = new JDialogOperator(MESSAGE);

            assertTrue("Component Dialog Example No Option", jdo1.isShowing());

            final String labelText = COMPONENT_R2;
            JLabelOperator jLabelOperator = new JLabelOperator(jdo1, labelText);
            assertEquals("Dialog contains appropriate text", labelText, jLabelOperator.getText());

            new JButtonOperator(jdo1, OK).push();

            assertFalse("Component Dialog Example No Option", jdo1.isShowing());
        }

        // Case: Maybe option selected
        {
            new JButtonOperator(jfo, COMPONENT_BUTTON).pushNoBlock();

            JDialogOperator jdo = new JDialogOperator(COMPONENT_TITLE);
            new JButtonOperator(jdo, COMPONENT_OP3).pushNoBlock();

            JDialogOperator jdo1 = new JDialogOperator(MESSAGE);

            assertTrue("Component Dialog Maybe Yes Option", jdo1.isShowing());

            final String labelText = COMPONENT_R3;
            JLabelOperator jLabelOperator = new JLabelOperator(jdo1, labelText);
            assertEquals("Dialog contains appropriate text", labelText, jLabelOperator.getText());

            new JButtonOperator(jdo1, OK).push();

            assertFalse("Component Dialog Maybe Yes Option", jdo1.isShowing());
        }

        // Case: Probably option selected
        {
            new JButtonOperator(jfo, COMPONENT_BUTTON).pushNoBlock();

            JDialogOperator jdo = new JDialogOperator(COMPONENT_TITLE);
            new JButtonOperator(jdo, COMPONENT_OP4).pushNoBlock();

            JDialogOperator jdo1 = new JDialogOperator(MESSAGE);

            assertTrue("Component Dialog Example Probably Option", jdo1.isShowing());

            final String labelText = COMPONENT_R4;
            JLabelOperator jLabelOperator = new JLabelOperator(jdo1, labelText);
            assertEquals("Dialog contains appropriate text", labelText, jLabelOperator.getText());

            new JButtonOperator(jdo1, OK).push();

            assertFalse("Component Dialog Example Probably Option", jdo1.isShowing());
        }

        // Case TextField and ComboBox functional
        {
            new JButtonOperator(jfo, COMPONENT_BUTTON).push();

            JDialogOperator jdo = new JDialogOperator(COMPONENT_TITLE);

            JTextFieldOperator jto = new JTextFieldOperator(jdo);
            jto.clearText();
            jto.typeText(TEXT_TO_TYPE);

            JComboBoxOperator jcbo = new JComboBoxOperator(jdo);
            jcbo.selectItem(2);

            assertEquals("Show Component Dialog TextField", TEXT_TO_TYPE, jto.getText());
            assertEquals("Show Component Dialog ComboBox", 2, jcbo.getSelectedIndex());

            new JButtonOperator(jdo, "cancel").push();
        }
    }

    public void showConfirmationDialog(JFrameOperator jfo) throws Exception {
        // Case: Yes option selected
        {
            new JButtonOperator(jfo, CONFIRM_BUTTON).pushNoBlock();

            JDialogOperator jdo = new JDialogOperator(SELECT_AN__OPTION);
            new JButtonOperator(jdo, YES).pushNoBlock();

            JDialogOperator jdo1 = new JDialogOperator(MESSAGE);

            assertTrue("Show Confirmation Dialog Yes Option", jdo1.isShowing());

            final String labelText = CONFIRM_YES;
            JLabelOperator jLabelOperator = new JLabelOperator(jdo1, labelText);
            assertEquals("Dialog contains appropriate text", labelText, jLabelOperator.getText());

            new JButtonOperator(jdo1, OK).push();

            assertFalse("Show Confirmation Dialog Yes Option", jdo1.isShowing());
        }

        // Case: No option selected
        {
            new JButtonOperator(jfo, CONFIRM_BUTTON).pushNoBlock();

            JDialogOperator jdo = new JDialogOperator(SELECT_AN__OPTION);
            new JButtonOperator(jdo, NO).pushNoBlock();

            JDialogOperator jdo1 = new JDialogOperator(MESSAGE);

            assertTrue("Show Confirmation Dialog No Option", jdo1.isShowing());

            final String labelText = CONFIRM_NO;
            JLabelOperator jLabelOperator = new JLabelOperator(jdo1, labelText);
            assertEquals("Dialog contains appropriate text", labelText, jLabelOperator.getText());

            new JButtonOperator(jdo1, OK).push();

            assertFalse("Show Confirmation Dialog No Option", jdo1.isShowing());
        }

        // Case: Cancel option selected
        {
            new JButtonOperator(jfo, CONFIRM_BUTTON).pushNoBlock();

            JDialogOperator jdo = new JDialogOperator(SELECT_AN__OPTION);

            assertTrue("Show Confirmation Dialog Cancel Option", jdo.isShowing());

            new JButtonOperator(jdo, CANCEL).push();

            assertFalse("Show Confirmation Dialog Cancel Option", jdo.isShowing());
        }
    }

}
