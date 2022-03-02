/*
 * Copyright (c) 2022 Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8262981
 * @summary Test JSlider Accessibility
 * @run main AccessibleJSliderTest
 */

import java.awt.BorderLayout;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.accessibility.AccessibleContext;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.SwingUtilities;

public class AccessibleJSliderTest extends AccessibleComponentTest {

    private void createSlider() {
        AccessibleComponentTest.INSTRUCTIONS = """
                INSTRUCTIONS:
                "Turn on screen reader. Press Tab key to move the focus to the JSlider 
                or click on the JSlider.
                
                Note: Pressing the following keys, check that screen reader reads the JSlider value
                correctly & it should match with the JLabel value above the JSlider then the
                testcase pass else testcase is failed.
                             
                1) Use arrow keys to increase and decrease the value of JSlider.
                2) Use Page Up to increase the value of JSlider.
                3) Use Page Down to decrease the value of JSlider.
                4) Use Home key to set the JSlider value to 0%.
                5) Use End key to set the JSlider value to 100%
                """;
        String accName = "JSlider Test";
        String accDesc = "Regression Test: AccessibleJSliderTest";
        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());
        JSlider jSlider = new JSlider();
        final JLabel sliderValueLbl = new JLabel("JSlider value : " + jSlider.getValue() + "%",
                JLabel.CENTER);
        AccessibleContext accessibleContext = jSlider.getAccessibleContext();
        accessibleContext.setAccessibleName(accName);
        accessibleContext.setAccessibleDescription(accDesc);
        jSlider.setMajorTickSpacing(10);
        jSlider.setPaintTicks(true);
        jSlider.setPaintLabels(true);

        jSlider.addChangeListener((changeEvent) -> {
            sliderValueLbl.setText("Slider value : " + jSlider.getValue() + "%");
        });
        panel.add(sliderValueLbl, BorderLayout.CENTER);
        panel.add(jSlider, BorderLayout.SOUTH);
        exceptionString = "Simple JSlider test failed!";
        super.createUI(panel, "AccessibleJSliderTest");
    }

    public static void main(String[] args) throws InterruptedException, InvocationTargetException {
        AccessibleJSliderTest test = new AccessibleJSliderTest();
        countDownLatch = test.createCountDownLatch();
        SwingUtilities.invokeAndWait(test::createSlider);
        countDownLatch.await(15, TimeUnit.MINUTES);
        if (!testResult) {
            throw new RuntimeException(exceptionString);
        }
    }

    @Override
    public CountDownLatch createCountDownLatch() {
        return new CountDownLatch(1);
    }
}
