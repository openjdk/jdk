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

import com.sun.swingset3.demos.textfield.JHistoryTextField;
import com.sun.swingset3.demos.textfield.TextFieldDemo;
import static com.sun.swingset3.demos.textfield.TextFieldDemo.*;
import java.awt.Color;
import java.awt.event.KeyEvent;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import javax.swing.JFormattedTextField;
import static org.jemmy2ext.JemmyExt.*;
import static org.testng.AssertJUnit.*;
import org.testng.annotations.Test;
import org.netbeans.jemmy.ClassReference;
import org.netbeans.jemmy.QueueTool;
import org.netbeans.jemmy.operators.ContainerOperator;
import org.netbeans.jemmy.operators.JButtonOperator;
import org.netbeans.jemmy.operators.JFrameOperator;
import org.netbeans.jemmy.operators.JLabelOperator;
import org.netbeans.jemmy.operators.JPasswordFieldOperator;
import org.netbeans.jemmy.operators.JTextFieldOperator;

/*
 * @test
 * @key headful
 * @summary Verifies SwingSet3 TextFieldDemo by entering text in each field and
 *          checking that app reacts accordingly.
 *
 * @library /sanity/client/lib/jemmy/src
 * @library /sanity/client/lib/Jemmy2Ext/src
 * @library /sanity/client/lib/SwingSet3/src
 * @build org.jemmy2ext.JemmyExt
 * @build com.sun.swingset3.demos.textfield.TextFieldDemo
 * @run testng TextFieldDemoTest
 */
public class TextFieldDemoTest {

    @Test
    public void test() throws Exception {
        captureDebugInfoOnFail(() -> {
            new ClassReference(TextFieldDemo.class.getCanonicalName()).startApplication();

            JFrameOperator frame = new JFrameOperator(DEMO_TITLE);

            historyTextField(frame);
            dateTextField(frame);
            passwordField(frame);
        });
    }

    private void historyTextField(JFrameOperator jfo) throws Exception {
        JTextFieldOperator jtfo = new JTextFieldOperator(jfo, new ByClassChooser(JHistoryTextField.class));
        jtfo.typeText("cat");

        jtfo.pressKey(KeyEvent.VK_DOWN);
        jtfo.pressKey(KeyEvent.VK_DOWN);
        jtfo.pressKey(KeyEvent.VK_ENTER);

        final String expectedValue = "category";
        jtfo.waitText(expectedValue);
        assertEquals("Select History Item", expectedValue, jtfo.getText());
    }

    public void dateTextField(JFrameOperator jfo) throws Exception {
        JTextFieldOperator jtfo = new JTextFieldOperator(jfo,
                new ByClassChooser(JFormattedTextField.class));
        ContainerOperator<?> containerOperator = new ContainerOperator<>(jtfo.getParent());
        JButtonOperator jbo = new JButtonOperator(containerOperator, GO);
        JLabelOperator dowLabel = new JLabelOperator(containerOperator);
        Calendar calendar = Calendar.getInstance(Locale.ENGLISH);

        // Check default date Day of the Week
        jbo.push();
        assertEquals("Default DOW",
                calendar.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.LONG, Locale.ENGLISH),
                dowLabel.getText());

        // Check Custom Day of the Week
        calendar.set(2012, 9, 11); // Represents "Oct 11, 2012"
        Date date = calendar.getTime();
        String dateString = jtfo.getQueueTool().invokeAndWait(
                new QueueTool.QueueAction<String>("Formatting the value using JFormattedTextField formatter") {

            @Override
            public String launch() throws Exception {
                return ((JFormattedTextField) jtfo.getSource()).getFormatter().valueToString(date);
            }
        });
        System.out.println("dateString = " + dateString);
        jtfo.enterText(dateString);

        jbo.push();
        assertEquals("Custom DOW", "Thursday", dowLabel.getText());
    }

    public void passwordField(JFrameOperator jfo) throws Exception {
        JPasswordFieldOperator password1 = new JPasswordFieldOperator(jfo, 0);
        JPasswordFieldOperator password2 = new JPasswordFieldOperator(jfo, 1);

        password1.typeText("password");
        password2.typeText("password");

        // Check Matching Passwords
        assertEquals("Matching Passwords", Color.green, password1.getBackground());
        assertEquals("Matching Passwords", Color.green, password2.getBackground());

        // Check non-matching passwords
        password2.typeText("passwereertegrs");
        assertEquals("Non-Matching Passwords", Color.white, password1.getBackground());
        assertEquals("Non-Matching Passwords", Color.white, password2.getBackground());
    }

}
