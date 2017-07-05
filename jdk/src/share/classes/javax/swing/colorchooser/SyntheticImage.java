/*
 * Copyright 1997-2003 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package javax.swing.colorchooser;

import java.awt.*;
import java.awt.image.*;

/** A helper class to make computing synthetic images a little easier.
 *  All you need to do is define a subclass that overrides computeRow
 *  to compute a row of the image.  It is passed the y coordinate of the
 *  row and an array into which to put the pixels in
 *  <a href="http://java.sun.com/products/jdk/1.1/docs/api/java.awt.image.ColorModel.html#getRGBdefault()">
 *  standard ARGB format</a>.
 *  <p>Normal usage looks something like this:
 *  <pre>&nbsp;Image i = createImage(new SyntheticImage(200, 100) {
 *  &nbsp;    protected void computeRow(int y, int[] row) {
 *  &nbsp;      for(int i = width; --i>=0; ) {
 *  &nbsp;          int grey = i*255/(width-1);
 *  &nbsp;          row[i] = (255<<24)|(grey<<16)|(grey<<8)|grey;
 *  &nbsp;      }
 *  &nbsp;    }
 *  &nbsp;}
 *  </pre>This creates a image 200 pixels wide and 100 pixels high
 *  that is a horizontal grey ramp, going from black on the left to
 *  white on the right.
 *  <p>
 *  If the image is to be a movie, override isStatic to return false,
 *  <i>y</i> cycling back to 0 is computeRow's signal that the next
 *  frame has started.  It is acceptable (expected?) for computeRow(0,r)
 *  to pause until the appropriate time to start the next frame.
 *
 *  @author James Gosling
 */
abstract class SyntheticImage implements ImageProducer {
    private SyntheticImageGenerator root;
    protected int width=10, height=100;
    static final ColorModel cm = ColorModel.getRGBdefault();
    public static final int pixMask = 0xFF;
    private Thread runner;
    protected SyntheticImage() {    }
    protected SyntheticImage(int w, int h) { width = w; height = h; }
    protected void computeRow(int y, int[] row) {
        int p = 255-255*y/(height-1);
        p = (pixMask<<24)|(p<<16)|(p<<8)|p;
        for (int i = row.length; --i>=0; ) row[i] = p;
    }
    public synchronized void addConsumer(ImageConsumer ic){
        for (SyntheticImageGenerator ics = root; ics != null; ics = ics.next)
            if (ics.ic == ic) return;
        root = new SyntheticImageGenerator(ic, root, this);
    }
    public synchronized boolean isConsumer(ImageConsumer ic){
        for (SyntheticImageGenerator ics = root; ics != null; ics = ics.next)
            if (ics.ic == ic) return true;
        return false;
    }
    public synchronized void removeConsumer(ImageConsumer ic) {
        SyntheticImageGenerator prev = null;
        for (SyntheticImageGenerator ics = root; ics != null; ics = ics.next) {
            if (ics.ic == ic) {
                ics.useful = false;
                if (prev!=null) prev.next = ics.next;
                else root = ics.next;
                return;
            }
            prev = ics;
        }
    }
    public synchronized void startProduction(ImageConsumer ic) {
        addConsumer(ic);
        for (SyntheticImageGenerator ics = root; ics != null; ics = ics.next)
            if (ics.useful && !ics.isAlive())
                ics.start();
    }
    protected boolean isStatic() { return true; }
    public void nextFrame(int param) {}//Override if !isStatic
    public void requestTopDownLeftRightResend(ImageConsumer ic){}

    protected volatile boolean aborted = false;
}

class SyntheticImageGenerator extends Thread {
    ImageConsumer ic;
    boolean useful;
    SyntheticImageGenerator next;
    SyntheticImage parent;
    SyntheticImageGenerator(ImageConsumer ic, SyntheticImageGenerator next,
        SyntheticImage parent) {
        super("SyntheticImageGenerator");
        this.ic = ic;
        this.next = next;
        this.parent = parent;
        useful = true;
        setDaemon(true);
    }
    public void run() {
        ImageConsumer ic = this.ic;
        int w = parent.width;
        int h = parent.height;
        int hints = ic.SINGLEPASS|ic.COMPLETESCANLINES|ic.TOPDOWNLEFTRIGHT;
        if (parent.isStatic())
            hints |= ic.SINGLEFRAME;
        ic.setHints(hints);
        ic.setDimensions(w, h);
        ic.setProperties(null);
        ic.setColorModel(parent.cm);

        if (useful) {
            int[] row=new int[w];
            doPrivileged( new Runnable() {
                public void run() {
                    Thread.currentThread().setPriority(Thread.MIN_PRIORITY);
                }
            });

            do {
                for (int y = 0; y<h && useful; y++) {
                    parent.computeRow(y,row);

                    if (parent.aborted) {
                        ic.imageComplete(ic.IMAGEABORTED);
                        return;
                    }

                    ic.setPixels(0, y, w, 1, parent.cm, row, 0, w);
                }
                ic.imageComplete(parent.isStatic() ? ic.STATICIMAGEDONE
                                            : ic.SINGLEFRAMEDONE );
            } while(!parent.isStatic() && useful);
        }
    }

    private final static void doPrivileged(final Runnable doRun) {
        java.security.AccessController.doPrivileged(
            new java.security.PrivilegedAction() {
                public Object run() {
                  doRun.run();
                  return null;
                }
            }
        );
    }
}
