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



import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;


/**
 * A simple applet class to demonstrate a sort algorithm.
 * You can specify a sorting algorithm using the "alg"
 * attribute. When you click on the applet, a thread is
 * forked which animates the sorting algorithm.
 *
 * @author James Gosling
 */
@SuppressWarnings("serial")
public class SortItem extends java.applet.Applet implements Runnable,
        MouseListener {

    /**
     * The thread that is sorting (or null).
     */
    private Thread kicker;
    /**
     * The array that is being sorted.
     */
    int arr[];
    /**
     * The high water mark.
     */
    int h1 = -1;
    /**
     * The low water mark.
     */
    int h2 = -1;
    /**
     * The name of the algorithm.
     */
    String algName;
    /**
     * The sorting algorithm (or null).
     */
    SortAlgorithm algorithm;
    Dimension initialSize = null;

    /**
     * Fill the array with random numbers from 0..n-1.
     */
    void scramble() {
        initialSize = getSize();
        int a[] = new int[initialSize.height / 2];
        double f = initialSize.width / (double) a.length;

        for (int i = a.length; --i >= 0;) {
            a[i] = (int) (i * f);
        }
        for (int i = a.length; --i >= 0;) {
            int j = (int) (i * Math.random());
            int t = a[i];
            a[i] = a[j];
            a[j] = t;
        }
        arr = a;
    }

    /**
     * Pause a while.
     * @see SortAlgorithm
     */
    void pause() {
        pause(-1, -1);
    }

    /**
     * Pause a while, and draw the high water mark.
     * @see SortAlgorithm
     */
    void pause(int H1) {
        pause(H1, -1);
    }

    /**
     * Pause a while, and draw the low&high water marks.
     * @see SortAlgorithm
     */
    void pause(int H1, int H2) {
        h1 = H1;
        h2 = H2;
        if (kicker != null) {
            repaint();
        }
        try {
            Thread.sleep(20);
        } catch (InterruptedException e) {
        }
    }

    /**
     * Initialize the applet.
     */
    @Override
    public void init() {
        String at = getParameter("alg");
        if (at == null) {
            at = "BubbleSort";
        }

        algName = at + "Algorithm";
        scramble();

        resize(100, 100);
        addMouseListener(this);
    }

    @Override
    public void start() {
        h1 = h2 = -1;
        scramble();
        repaint();
        showStatus(getParameter("alg"));
    }

    /**
     * Deallocate resources of applet.
     */
    @Override
    public void destroy() {
        removeMouseListener(this);
    }

    /**
     * Paint the array of numbers as a list
     * of horizontal lines of varying lengths.
     */
    @Override
    public void paint(Graphics g) {
        int a[] = arr;
        int y = 0;
        int deltaY = 0, deltaX = 0, evenY = 0;

        Dimension currentSize = getSize();
        int currentHeight = currentSize.height;
        int currentWidth = currentSize.width;

        // Check to see if the applet has been resized since it
        // started running.  If so, need the deltas to make sure
        // the applet is centered in its containing panel.
        // The evenX and evenY are because the high and low
        // watermarks are calculated from the top, but the rest
        // of the lines are calculated from the bottom, which
        // can lead to a discrepancy if the window is not an
        // even size.
        if (!currentSize.equals(initialSize)) {
            evenY = (currentHeight - initialSize.height) % 2;
            deltaY = (currentHeight - initialSize.height) / 2;
            deltaX = (currentWidth - initialSize.width) / 2;

            if (deltaY < 0) {
                deltaY = 0;
                evenY = 0;
            }
            if (deltaX < 0) {
                deltaX = 0;
            }
        }

        // Erase old lines
        g.setColor(getBackground());
        y = currentHeight - deltaY - 1;
        for (int i = a.length; --i >= 0; y -= 2) {
            g.drawLine(deltaX + arr[i], y, currentWidth, y);
        }

        // Draw new lines
        g.setColor(Color.black);
        y = currentHeight - deltaY - 1;
        for (int i = a.length; --i >= 0; y -= 2) {
            g.drawLine(deltaX, y, deltaX + arr[i], y);
        }

        if (h1 >= 0) {
            g.setColor(Color.red);
            y = deltaY + evenY + h1 * 2 + 1;
            g.drawLine(deltaX, y, deltaX + initialSize.width, y);
        }
        if (h2 >= 0) {
            g.setColor(Color.blue);
            y = deltaY + evenY + h2 * 2 + 1;
            g.drawLine(deltaX, y, deltaX + initialSize.width, y);
        }
    }

    /**
     * Update without erasing the background.
     */
    @Override
    public void update(Graphics g) {
        paint(g);
    }

    /**
     * Run the sorting algorithm. This method is
     * called by class Thread once the sorting algorithm
     * is started.
     * @see java.lang.Thread#run
     * @see SortItem#mouseUp
     */
    @Override
    public void run() {
        try {
            if (algorithm == null) {
                algorithm = (SortAlgorithm) Class.forName(algName).newInstance();
                algorithm.setParent(this);
            }
            algorithm.init();
            algorithm.sort(arr);
        } catch (Exception e) {
        }
    }

    /**
     * Stop the applet. Kill any sorting algorithm that
     * is still sorting.
     */
    @Override
    public synchronized void stop() {
        if (algorithm != null) {
            try {
                algorithm.stop();
            } catch (IllegalThreadStateException e) {
                // ignore this exception
            }
            kicker = null;
        }
    }

    /**
     * For a Thread to actually do the sorting. This routine makes
     * sure we do not simultaneously start several sorts if the user
     * repeatedly clicks on the sort item.  It needs to be
     * synchronized with the stop() method because they both
     * manipulate the common kicker variable.
     */
    private synchronized void startSort() {
        if (kicker == null || !kicker.isAlive()) {
            kicker = new Thread(this);
            kicker.start();
        }
    }

    @Override
    public void mouseClicked(MouseEvent e) {
        showStatus(getParameter("alg"));
    }

    @Override
    public void mousePressed(MouseEvent e) {
    }

    /**
     * The user clicked in the applet. Start the clock!
     */
    @Override
    public void mouseReleased(MouseEvent e) {
        startSort();
        e.consume();
    }

    @Override
    public void mouseEntered(MouseEvent e) {
    }

    @Override
    public void mouseExited(MouseEvent e) {
    }

    @Override
    public String getAppletInfo() {
        return "Title: SortDemo \nAuthor: James Gosling 1.17f, 10 Apr 1995 \nA simple applet class to demonstrate a sort algorithm.  \nYou can specify a sorting algorithm using the 'alg' attribute.  \nWhen you click on the applet, a thread is forked which animates \nthe sorting algorithm.";
    }

    @Override
    public String[][] getParameterInfo() {
        String[][] info = {
            { "alg", "string",
                "The name of the algorithm to run.  You can choose from the provided algorithms or suppply your own, as long as the classes are runnable as threads and their names end in 'Algorithm.'  BubbleSort is the default.  Example:  Use 'QSort' to run the QSortAlgorithm class." }
        };
        return info;
    }
}
