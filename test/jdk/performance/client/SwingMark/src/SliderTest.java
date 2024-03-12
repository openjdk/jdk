/*
 * Copyright (c) 1998, 2023, Oracle and/or its affiliates. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *   - Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *
 *   - Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer in the
 *     documentation and/or other materials provided with the distribution.
 *
 *   - Neither the name of Oracle nor the names of its
 *     contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

import java.awt.Graphics;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.SwingUtilities;

/**
  * This test is mean to isolate the speed of JSlider painting
  * It creates a JList and then changes its value while repainting.
  *
  */

public class SliderTest extends AbstractSwingTest {

    JSlider slider1;
    int values = 500;

    public SliderTest() {
    }

    public JComponent getTestComponent() {
        JPanel panel = new JPanel();
        slider1 = new CountSlider(JSlider.HORIZONTAL, 0, values, 0);
        slider1.setMajorTickSpacing(values / 5);
        slider1.setMinorTickSpacing(values / 10);
        slider1.setPaintTicks(true);
        slider1.setPaintLabels(true);
        panel.add(slider1);
        return panel;
    }

    public String getTestName() {
        return "Sliders";
    }

    public void runTest() {
        testSlider(slider1, 1);  // increment this slider by ones
    }

    public void testSlider(JSlider currentSlider, int incrementBy) {
            SliderInc inc = new SliderInc(currentSlider, incrementBy);
            for (int i = currentSlider.getValue() ; i < currentSlider.getMaximum(); i++) {
            try {
                SwingUtilities.invokeLater(inc);
                rest();
            } catch (Exception e) {System.out.println(e);}
        }
    }

    public static void main(String[] args) {
        runStandAloneTest(new SliderTest());
    }

    class CountSlider extends JSlider {

        public CountSlider(int ori, int min, int max, int curr) {
           super(ori, min, max, curr);
        }

        public void paint(Graphics g) {
            super.paint(g);
            paintCount++;
            }
        }
    }

class SliderInc implements Runnable {
    JSlider slider;
    int incAmount = 1;

    public SliderInc(JSlider sliderToIncrement, int incrementBy) {
        slider = sliderToIncrement;
        incAmount = incrementBy;
    }

    public void run() {
        int currentVal = slider.getValue();
            slider.setValue(currentVal+incAmount);
    }
}
