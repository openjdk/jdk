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



import java.applet.Applet;
import java.awt.AWTEvent;
import java.awt.BorderLayout;
import java.awt.Button;
import java.awt.Canvas;
import java.awt.Choice;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.FontMetrics;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.Label;
import java.awt.LayoutManager;
import java.awt.Panel;
import java.awt.TextField;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.TextEvent;
import java.awt.image.ColorModel;
import java.awt.image.MemoryImageSource;


enum DitherMethod {

    NOOP, RED, GREEN, BLUE, ALPHA, SATURATION
};


@SuppressWarnings("serial")
public class DitherTest extends Applet implements Runnable {

    private Thread runner;
    private DitherControls XControls;
    private DitherControls YControls;
    private DitherCanvas canvas;

    public static void main(String args[]) {
        Frame f = new Frame("DitherTest");
        DitherTest ditherTest = new DitherTest();
        ditherTest.init();
        f.add("Center", ditherTest);
        f.pack();
        f.setVisible(true);
        ditherTest.start();
    }

    @Override
    public void init() {
        String xspec = null, yspec = null;
        int xvals[] = new int[2];
        int yvals[] = new int[2];

        try {
            xspec = getParameter("xaxis");
            yspec = getParameter("yaxis");
        } catch (NullPointerException ignored) {
            //only occurs if run as application
        }

        if (xspec == null) {
            xspec = "red";
        }
        if (yspec == null) {
            yspec = "blue";
        }
        DitherMethod xmethod = colorMethod(xspec, xvals);
        DitherMethod ymethod = colorMethod(yspec, yvals);

        setLayout(new BorderLayout());
        XControls = new DitherControls(this, xvals[0], xvals[1],
                xmethod, false);
        YControls = new DitherControls(this, yvals[0], yvals[1],
                ymethod, true);
        YControls.addRenderButton();
        add("North", XControls);
        add("South", YControls);
        add("Center", canvas = new DitherCanvas());
    }

    private DitherMethod colorMethod(String s, int vals[]) {
        DitherMethod method = DitherMethod.NOOP;
        if (s == null) {
            s = "";
        }
        String lower = s.toLowerCase();

        for (DitherMethod m : DitherMethod.values()) {
            if (lower.startsWith(m.toString().toLowerCase())) {
                method = m;
                lower = lower.substring(m.toString().length());
            }
        }
        if (method == DitherMethod.NOOP) {
            vals[0] = 0;
            vals[1] = 0;
            return method;
        }
        int begval = 0;
        int endval = 255;
        try {
            int dash = lower.indexOf('-');
            if (dash < 0) {
                endval = Integer.parseInt(lower);
            } else {
                begval = Integer.parseInt(lower.substring(0, dash));
                endval = Integer.parseInt(lower.substring(dash + 1));
            }
        } catch (NumberFormatException ignored) {
        }

        if (begval < 0) {
            begval = 0;
        } else if (begval > 255) {
            begval = 255;
        }

        if (endval < 0) {
            endval = 0;
        } else if (endval > 255) {
            endval = 255;
        }

        vals[0] = begval;
        vals[1] = endval;
        return method;
    }

    /**
     * Calculates and returns the image.  Halts the calculation and returns
     * null if the Applet is stopped during the calculation.
     */
    private Image calculateImage() {
        Thread me = Thread.currentThread();

        int width = canvas.getSize().width;
        int height = canvas.getSize().height;
        int xvals[] = new int[2];
        int yvals[] = new int[2];
        int xmethod = XControls.getParams(xvals);
        int ymethod = YControls.getParams(yvals);
        int pixels[] = new int[width * height];
        int c[] = new int[4];   //temporarily holds R,G,B,A information
        int index = 0;
        for (int j = 0; j < height; j++) {
            for (int i = 0; i < width; i++) {
                c[0] = c[1] = c[2] = 0;
                c[3] = 255;
                if (xmethod < ymethod) {
                    applyMethod(c, xmethod, i, width, xvals);
                    applyMethod(c, ymethod, j, height, yvals);
                } else {
                    applyMethod(c, ymethod, j, height, yvals);
                    applyMethod(c, xmethod, i, width, xvals);
                }
                pixels[index++] = ((c[3] << 24) | (c[0] << 16) | (c[1] << 8)
                        | c[2]);
            }

            // Poll once per row to see if we've been told to stop.
            if (runner != me) {
                return null;
            }
        }
        return createImage(new MemoryImageSource(width, height,
                ColorModel.getRGBdefault(), pixels, 0, width));
    }

    private void applyMethod(int c[], int methodIndex, int step,
            int total, int vals[]) {
        DitherMethod method = DitherMethod.values()[methodIndex];
        if (method == DitherMethod.NOOP) {
            return;
        }
        int val = ((total < 2)
                ? vals[0]
                : vals[0] + ((vals[1] - vals[0]) * step / (total - 1)));
        switch (method) {
            case RED:
                c[0] = val;
                break;
            case GREEN:
                c[1] = val;
                break;
            case BLUE:
                c[2] = val;
                break;
            case ALPHA:
                c[3] = val;
                break;
            case SATURATION:
                int max = Math.max(Math.max(c[0], c[1]), c[2]);
                int min = max * (255 - val) / 255;
                if (c[0] == 0) {
                    c[0] = min;
                }
                if (c[1] == 0) {
                    c[1] = min;
                }
                if (c[2] == 0) {
                    c[2] = min;
                }
                break;
        }
    }

    @Override
    public void start() {
        runner = new Thread(this);
        runner.start();
    }

    @Override
    public void run() {
        canvas.setImage(null);  // Wipe previous image
        Image img = calculateImage();
        if (img != null && runner == Thread.currentThread()) {
            canvas.setImage(img);
        }
    }

    @Override
    public void stop() {
        runner = null;
    }

    @Override
    public void destroy() {
        remove(XControls);
        remove(YControls);
        remove(canvas);
    }

    @Override
    public String getAppletInfo() {
        return "An interactive demonstration of dithering.";
    }

    @Override
    public String[][] getParameterInfo() {
        String[][] info = {
            { "xaxis", "{RED, GREEN, BLUE, ALPHA, SATURATION}",
                "The color of the Y axis.  Default is RED." },
            { "yaxis", "{RED, GREEN, BLUE, ALPHA, SATURATION}",
                "The color of the X axis.  Default is BLUE." }
        };
        return info;
    }
}


@SuppressWarnings("serial")
class DitherCanvas extends Canvas {

    private Image img;
    private static String calcString = "Calculating...";

    @Override
    public void paint(Graphics g) {
        int w = getSize().width;
        int h = getSize().height;
        if (img == null) {
            super.paint(g);
            g.setColor(Color.black);
            FontMetrics fm = g.getFontMetrics();
            int x = (w - fm.stringWidth(calcString)) / 2;
            int y = h / 2;
            g.drawString(calcString, x, y);
        } else {
            g.drawImage(img, 0, 0, w, h, this);
        }
    }

    @Override
    public void update(Graphics g) {
        paint(g);
    }

    @Override
    public Dimension getMinimumSize() {
        return new Dimension(20, 20);
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(200, 200);
    }

    public Image getImage() {
        return img;
    }

    public void setImage(Image img) {
        this.img = img;
        repaint();
    }
}


@SuppressWarnings("serial")
class DitherControls extends Panel implements ActionListener {

    private CardinalTextField start;
    private CardinalTextField end;
    private Button button;
    private Choice choice;
    private DitherTest applet;
    private static LayoutManager dcLayout = new FlowLayout(FlowLayout.CENTER,
            10, 5);

    public DitherControls(DitherTest app, int s, int e, DitherMethod type,
            boolean vertical) {
        applet = app;
        setLayout(dcLayout);
        add(new Label(vertical ? "Vertical" : "Horizontal"));
        add(choice = new Choice());
        for (DitherMethod m : DitherMethod.values()) {
            choice.addItem(m.toString().substring(0, 1)
                    + m.toString().substring(1).toLowerCase());
        }
        choice.select(type.ordinal());
        add(start = new CardinalTextField(Integer.toString(s), 4));
        add(end = new CardinalTextField(Integer.toString(e), 4));
    }

    /* puts on the button */
    public void addRenderButton() {
        add(button = new Button("New Image"));
        button.addActionListener(this);
    }

    /* retrieves data from the user input fields */
    public int getParams(int vals[]) {
        try {
            vals[0] = scale(Integer.parseInt(start.getText()));
        } catch (NumberFormatException nfe) {
            vals[0] = 0;
        }
        try {
            vals[1] = scale(Integer.parseInt(end.getText()));
        } catch (NumberFormatException nfe) {
            vals[1] = 255;
        }
        return choice.getSelectedIndex();
    }

    /* fits the number between 0 and 255 inclusive */
    private int scale(int number) {
        if (number < 0) {
            number = 0;
        } else if (number > 255) {
            number = 255;
        }
        return number;
    }

    /* called when user clicks the button */
    @Override
    public void actionPerformed(ActionEvent e) {
        if (e.getSource() == button) {
            applet.start();
        }
    }
}


@SuppressWarnings("serial")
class CardinalTextField extends TextField {

    String oldText = null;

    public CardinalTextField(String text, int columns) {
        super(text, columns);
        enableEvents(AWTEvent.KEY_EVENT_MASK | AWTEvent.TEXT_EVENT_MASK);
        oldText = getText();
    }

    // Consume non-digit KeyTyped events
    // Note that processTextEvent kind of eliminates the need for this
    // function, but this is neater, since ideally, it would prevent
    // the text from appearing at all.  Sigh.  See bugid 4100317/4114565.
    //
    @Override
    protected void processEvent(AWTEvent evt) {
        int id = evt.getID();
        if (id != KeyEvent.KEY_TYPED) {
            super.processEvent(evt);
            return;
        }

        KeyEvent kevt = (KeyEvent) evt;
        char c = kevt.getKeyChar();

        // Digits, backspace, and delete are okay
        // Note that the minus sign is not allowed (neither is decimal)
        if (Character.isDigit(c) || (c == '\b') || (c == '\u007f')) {
            super.processEvent(evt);
            return;
        }

        Toolkit.getDefaultToolkit().beep();
        kevt.consume();
    }

    // Should consume TextEvents for non-integer Strings
    // Store away the text in the tf for every TextEvent
    // so we can revert to it on a TextEvent (paste, or
    // legal key in the wrong location) with bad text
    //
    // Note: it would be easy to extend this to an eight-bit
    // TextField (range 0-255), but I'll leave it as-is.
    //
    @Override
    protected void processTextEvent(TextEvent te) {
        // The empty string is okay, too
        String newText = getText();
        if (newText.equals("") || textIsCardinal(newText)) {
            oldText = newText;
            super.processTextEvent(te);
            return;
        }

        Toolkit.getDefaultToolkit().beep();
        setText(oldText);
    }

    // Returns true for Cardinal (non-negative) numbers
    // Note that the empty string is not allowed
    private boolean textIsCardinal(String textToCheck) {
        try {
            return Integer.parseInt(textToCheck, 10) >= 0;
        } catch (NumberFormatException nfe) {
            return false;
        }
    }
}
