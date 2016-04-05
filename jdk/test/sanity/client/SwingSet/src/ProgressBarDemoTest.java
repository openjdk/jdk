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

import com.sun.swingset3.demos.progressbar.ProgressBarDemo;
import static com.sun.swingset3.demos.progressbar.ProgressBarDemo.*;
import java.awt.Component;
import static org.testng.AssertJUnit.*;
import org.testng.annotations.Test;
import org.netbeans.jemmy.ClassReference;
import org.netbeans.jemmy.ComponentChooser;
import org.netbeans.jemmy.operators.JButtonOperator;
import org.netbeans.jemmy.operators.JFrameOperator;
import org.netbeans.jemmy.operators.JProgressBarOperator;
import static org.jemmy2ext.JemmyExt.captureDebugInfoOnFail;

/*
 * @test
 * @key headful
 * @summary Verifies SwingSet3 ProgressBarDemo page by pressing start and stop
 *          buttons and checking the progress bar and the buttons state.
 *
 * @library /sanity/client/lib/jemmy/src
 * @library /sanity/client/lib/Jemmy2Ext/src
 * @library /sanity/client/lib/SwingSet3/src
 * @build org.jemmy2ext.JemmyExt
 * @build com.sun.swingset3.demos.progressbar.ProgressBarDemo
 * @run testng ProgressBarDemoTest
 */
public class ProgressBarDemoTest {

    @Test
    public void test() throws Exception {
        captureDebugInfoOnFail(() -> {
            new ClassReference(ProgressBarDemo.class.getCanonicalName()).startApplication();

            JFrameOperator frame = new JFrameOperator(DEMO_TITLE);

            JButtonOperator startButton = new JButtonOperator(frame, START_BUTTON);
            JButtonOperator stopButton = new JButtonOperator(frame, STOP_BUTTON);
            JProgressBarOperator jpbo = new JProgressBarOperator(frame);

            // Check that progress completes and corect enable/disable of start/stop buttons
            checkCompleteProgress(frame, startButton, stopButton, jpbo);

            // Check progess bar progression and start/stop button disabled/enabled states
            checkStartStop(frame, startButton, stopButton, jpbo);
        });
    }

    // Check that progress completes and corect enable/disable of start/stop buttons
    public void checkStartStop(JFrameOperator frame, JButtonOperator startButton, JButtonOperator stopButton, JProgressBarOperator progressBar) throws Exception {
        int initialProgress = progressBar.getValue();
        System.out.println("initialProgress = " + initialProgress);
        int maximum = progressBar.getMaximum();

        startButton.pushNoBlock();

        progressBar.waitState(new ComponentChooser() {

            @Override
            public boolean checkComponent(Component comp) {
                int value = progressBar.getValue();
                System.out.println("checkComponent1 value = " + value);
                return value < maximum;
            }

            @Override
            public String getDescription() {
                return "Progress < maximum (" + maximum + ")";
            }
        });

        stopButton.waitComponentEnabled();

        progressBar.waitState(new ComponentChooser() {

            @Override
            public boolean checkComponent(Component comp) {
                int value = progressBar.getValue();
                System.out.println("checkComponent2 value = " + value);
                return value > 0;
            }

            @Override
            public String getDescription() {
                return "Progress > 0";
            }
        });

        int progress = progressBar.getValue();
        System.out.println("progress = " + progress);

        //Check that progress par has progressed and Start Button Disabled
        assertTrue("Progress Bar Progressing (progress > 0, actual value: " + progress + ")", progress > 0);
        assertFalse("Start Button Disabled", startButton.isEnabled());
        assertTrue("Stop Button Enabled", stopButton.isEnabled());

        //Wait a liitle bit longer then Press stop get progress
        progressBar.waitState(new ComponentChooser() {

            @Override
            public boolean checkComponent(Component comp) {
                return progressBar.getValue() > progress;
            }

            @Override
            public String getDescription() {
                return "Progress > " + progress;
            }
        });

        stopButton.pushNoBlock();

        startButton.waitComponentEnabled();

        int interimProgress = progressBar.getValue();

        // Check that progress par has Stopped and Start Button Disabled
        assertTrue("Progress Bar Stopped "
                + "(interimProgress, actual value: " + interimProgress + " "
                + "> progress, actual value: " + progress + ")",
                interimProgress > progress);
        assertTrue("Start Button Enabled", startButton.isEnabled());
        assertFalse("Stop Button Disabled", stopButton.isEnabled());
    }

    // Check progess bar progression and start/stop button disabled/enabled states
    public void checkCompleteProgress(JFrameOperator frame, JButtonOperator startButton, JButtonOperator stopButton, JProgressBarOperator progressBar) throws Exception {
        startButton.pushNoBlock();

        progressBar.waitValue(progressBar.getMaximum());

        startButton.waitComponentEnabled();

        assertEquals("Complete Progress", progressBar.getMaximum(), progressBar.getValue());
        assertTrue("Start Button Enabled", startButton.isEnabled());
        assertFalse("Stop Button Disabled", stopButton.isEnabled());
    }

}
