/*
 * Copyright (c) 1997, 2011, Oracle and/or its affiliates. All rights reserved.
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

/*
 * This source code is provided to illustrate the usage of a given feature
 * or technique and has been deliberately simplified. Additional steps
 * required for a production-quality application, such as security checks,
 * input validation and proper error handling, might not be present in
 * this sample code.
 */



/**
 * I love blinking things.
 *
 * @author Arthur van Hoff
 * @author 04/24/96 Jim Hagen use getBackground
 * @author 02/05/98 Mike McCloskey removed use of deprecated methods
 * @author 04/23/99 Josh Bloch, use timer instead of explicit multithreading.
 * @author 07/10/00 Daniel Peek brought to code conventions, minor changes
 */
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.util.StringTokenizer;
import java.util.Timer;
import java.util.TimerTask;


public class Blink extends java.applet.Applet {

    private static final long serialVersionUID = -775844794477507646L;
    private Timer timer;              // Schedules the blinking
    private String labelString;       // The label for the window
    private int delay;                // the delay time between blinks

    @Override
    public void init() {
        String blinkFrequency = getParameter("speed");
        delay = (blinkFrequency == null) ? 400
                : (1000 / Integer.parseInt(blinkFrequency));
        labelString = getParameter("lbl");
        if (labelString == null) {
            labelString = "Blink";
        }
        Font font = new java.awt.Font("Serif", Font.PLAIN, 24);
        setFont(font);
    }

    @Override
    public void start() {
        timer = new Timer();     //creates a new timer to schedule the blinking
        timer.schedule(new TimerTask() {      //creates a timertask to schedule

            // overrides the run method to provide functionality
            @Override
            public void run() {
                repaint();
            }
        }, delay, delay);
    }

    @Override
    public void paint(Graphics g) {
        int fontSize = g.getFont().getSize();
        int x = 0, y = fontSize, space;
        int red = (int) (50 * Math.random());
        int green = (int) (50 * Math.random());
        int blue = (int) (256 * Math.random());
        Dimension d = getSize();
        g.setColor(Color.black);
        FontMetrics fm = g.getFontMetrics();
        space = fm.stringWidth(" ");
        for (StringTokenizer t = new StringTokenizer(labelString);
                t.hasMoreTokens();) {
            String word = t.nextToken();
            int w = fm.stringWidth(word) + space;
            if (x + w > d.width) {
                x = 0;
                y += fontSize;  //move word to next line if it doesn't fit
            }
            if (Math.random() < 0.5) {
                g.setColor(new java.awt.Color((red + y * 30) % 256,
                        (green + x / 3) % 256, blue));
            } else {
                g.setColor(getBackground());
            }
            g.drawString(word, x, y);
            x += w;  //shift to the right to draw the next word
        }
    }

    @Override
    public void stop() {
        timer.cancel();  //stops the timer
    }

    @Override
    public String getAppletInfo() {
        return "Title: Blinker\n"
                + "Author: Arthur van Hoff\n"
                + "Displays multicolored blinking text.";
    }

    @Override
    public String[][] getParameterInfo() {
        String pinfo[][] = {
            { "speed", "string", "The blink frequency" },
            { "lbl", "string", "The text to blink." }, };
        return pinfo;
    }
}
