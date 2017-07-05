/*
 * Copyright 2007-2008 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

/*
  @test
  @bug 6255653
  @summary REGRESSION: Override isLightweight() causes access violation in awt.dll
  @author Andrei Dmitriev: area=awt-component
  @run main StubPeerCrash
*/

/*
 * The test may not crash for several times so iteratively continue up to some limit.
 */

import java.awt.*;
import java.awt.peer.*;
import java.awt.event.PaintEvent;
import java.awt.image.ImageProducer;
import java.awt.image.ImageObserver;
import java.awt.image.ColorModel;
import java.awt.image.VolatileImage;
import java.awt.GraphicsConfiguration;
import sun.awt.CausedFocusEvent;
import sun.java2d.pipe.Region;

public class StubPeerCrash {
    public static int ITERATIONS = 20;

    public static void main(String []s)
    {
        for (int i = 0; i < ITERATIONS; i++){
            showFrame(i);
        }
    }

    private static void showFrame(int i){
        System.out.println("iteration = "+i);
        Frame f = new Frame();
        f.add(new AHeavyweightComponent());
        f.setVisible(true);
        f.setVisible(false);
    }
}

class AHeavyweightComponent extends Component {
    private ComponentPeer peer = new StubComponentPeer();

    public AHeavyweightComponent(){
    }

    public boolean isLightweight() {
        return false;
    }

    public ComponentPeer getPeer(){
        return peer;
    }
}

class StubComponentPeer implements ComponentPeer {
    public boolean isObscured(){return true;};
    public boolean canDetermineObscurity(){return true;};
    public void                setVisible(boolean b){};
    public void                setEnabled(boolean b){};
    public void                paint(Graphics g){};
    public void                repaint(long tm, int x, int y, int width, int height){};
    public void                print(Graphics g){};
    public void                setBounds(int x, int y, int width, int height, int op){};
    public void                handleEvent(AWTEvent e){};
    public void                coalescePaintEvent(PaintEvent e){};
    public Point               getLocationOnScreen(){return null;};
    public Dimension           getPreferredSize(){return null;};
    public Dimension           getMinimumSize(){return null;};
    public ColorModel          getColorModel(){return null;};
    public Toolkit             getToolkit(){return null;};
    public Graphics            getGraphics(){return null;};
    public FontMetrics         getFontMetrics(Font font){return null;};
    public void                dispose(){};
    public void                setForeground(Color c){};
    public void                setBackground(Color c){};
    public void                setFont(Font f){};
    public void                updateCursorImmediately(){};
    public boolean             requestFocus(Component lightweightChild,
                                     boolean temporary,
                                     boolean focusedWindowChangeAllowed,
                                     long time, CausedFocusEvent.Cause cause){
        return true;
    };
    public boolean             isFocusable(){return true;};

    public Image               createImage(ImageProducer producer){return null;};
    public Image               createImage(int width, int height){return null;};
    public VolatileImage       createVolatileImage(int width, int height){return null;};
    public boolean             prepareImage(Image img, int w, int h, ImageObserver o){return true;};
    public int                 checkImage(Image img, int w, int h, ImageObserver o){return 0;};
    public GraphicsConfiguration getGraphicsConfiguration(){return null;};
    public boolean     handlesWheelScrolling(){return true;};
    public void createBuffers(int numBuffers, BufferCapabilities caps) throws AWTException{};
    public Image getBackBuffer(){return null;};
    public void flip(int x1, int y1, int x2, int y2, BufferCapabilities.FlipContents flipAction){};
    public void destroyBuffers(){};

    /**
     * Reparents this peer to the new parent referenced by <code>newContainer</code> peer
     * Implementation depends on toolkit and container.
     * @param newContainer peer of the new parent container
     * @since 1.5
     */
    public void reparent(ContainerPeer newContainer){};
    /**
     * Returns whether this peer supports reparenting to another parent withour destroying the peer
     * @return true if appropriate reparent is supported, false otherwise
     * @since 1.5
     */
    public boolean isReparentSupported(){return true;};

    /**
     * Used by lightweight implementations to tell a ComponentPeer to layout
     * its sub-elements.  For instance, a lightweight Checkbox needs to layout
     * the box, as well as the text label.
     */
    public void        layout(){};


     public    Rectangle getBounds(){return null;};

    /**
     * Applies the shape to the native component window.
     * @since 1.7
     */
    public void applyShape(Region shape){};

    /**
     * DEPRECATED:  Replaced by getPreferredSize().
     */
    public Dimension           preferredSize(){return null;};

    /**
     * DEPRECATED:  Replaced by getMinimumSize().
     */
    public Dimension           minimumSize(){return null;};

    /**
     * DEPRECATED:  Replaced by setVisible(boolean).
     */
    public void                show(){};

    /**
     * DEPRECATED:  Replaced by setVisible(boolean).
     */
    public void                hide(){};

    /**
     * DEPRECATED:  Replaced by setEnabled(boolean).
     */
    public void                enable(){};

    /**
     * DEPRECATED:  Replaced by setEnabled(boolean).
     */
    public void                disable(){};

    /**
     * DEPRECATED:  Replaced by setBounds(int, int, int, int).
     */
    public void                reshape(int x, int y, int width, int height){};
}
