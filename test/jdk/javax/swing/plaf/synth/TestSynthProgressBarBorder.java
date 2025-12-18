/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8192888
 * @summary Verifies ProgressBar in Synth L&F renders background
 *          when border is not painted
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual TestSynthProgressBarBorder
 */

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GridBagLayout;

import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JSlider;
import javax.swing.UIManager;

public class TestSynthProgressBarBorder {

    static final String INSTRUCTIONS = """
        A frame containing progress bar will be shown with 50% value
        and "ProgressBar setBorder" checkbox at the top
        and a slider at bottom.

        Please check if 50% progress is rendered in the progress bar
        and rest 50% is rendered as blank bar
        with Nimbus default background color.

        Please verify if "ProgressBar setBorder" checkbox is unchecked,
        the 50% blank bar should not disappear.
        Also, now if you use slider to set progress value to 0%,
        a blank progress bar with 0% value should be rendered and
        the progress bar should not disappear.
        If this verification is met,  press Pass else press Fail.""";

    public static void main(String[] args) throws Exception {
        // Set Nimbus L&F
        UIManager.setLookAndFeel("javax.swing.plaf.nimbus.NimbusLookAndFeel");
        PassFailJFrame.builder()
                .instructions(INSTRUCTIONS)
                .columns(35)
                .testUI(TestSynthProgressBarBorder::createUI)
                .build()
                .awaitAndCheck();
    }

    static JFrame createUI() {
        JFrame frame = new JFrame("Synth JProgressBar Test");
        frame.setSize(400, 200);

        // ProgressBar setup
        JProgressBar progressBar = new JProgressBar(0, 100);
        progressBar.setValue(50);
        progressBar.setStringPainted(true);
        progressBar.setBorderPainted(true);

        final JCheckBox checkBox = new JCheckBox("ProgressBar setBorder", true);
        checkBox.addActionListener((e)->{
            boolean isSelected = checkBox.isSelected();
            progressBar.setBorderPainted(isSelected);
            checkBox.setText("ProgressBar setBorder: " + isSelected);
        });

        JPanel center = new JPanel(new GridBagLayout());
        center.setBackground(Color.CYAN);
        center.add(progressBar);

        // Slider to adjust progress bar
        JSlider slider = new JSlider(0, 100, 50);
        slider.addChangeListener(e -> progressBar.setValue(slider.getValue()));

        frame.add(checkBox, BorderLayout.NORTH);
        frame.add(center, BorderLayout.CENTER);
        frame.add(slider, BorderLayout.SOUTH);
        return frame;
    }

}
