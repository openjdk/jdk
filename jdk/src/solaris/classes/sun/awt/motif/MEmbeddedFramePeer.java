/*
 * Copyright 1996-2008 Sun Microsystems, Inc.  All Rights Reserved.
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

package sun.awt.motif;

import sun.awt.EmbeddedFrame;
import java.util.logging.*;
import java.awt.Component;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.AWTKeyStroke;
import java.awt.Component;
import java.awt.Container;
import sun.awt.SunToolkit;
import java.util.LinkedList;
import java.util.Iterator;

import sun.java2d.SurfaceData;

public class MEmbeddedFramePeer extends MFramePeer {
    private static final Logger xembedLog = Logger.getLogger("sun.awt.motif.xembed.MEmbeddedFramePeer");

//     A detail code is required for XEMBED_FOCUS_IN. The following values are valid:
/* Details for  XEMBED_FOCUS_IN: */
    final static int XEMBED_FOCUS_CURRENT       =       0;
    final static int XEMBED_FOCUS_FIRST         =       1;
    final static int XEMBED_FOCUS_LAST  =       2;

    LinkedList<AWTKeyStroke> strokes = new LinkedList<AWTKeyStroke>();

    public MEmbeddedFramePeer(EmbeddedFrame target) {
        super(target);
        xembedLog.fine("Creating XEmbed-enabled motif embedded frame, frame supports XEmbed:" + supportsXEmbed());
    }

    void create(MComponentPeer parent) {
        NEFcreate(parent, ((MEmbeddedFrame)target).handle);
    }
    native void NEFcreate(MComponentPeer parent, long handle);
    native void pShowImpl();
    void pShow() {
        pShowImpl();
    }

    boolean supportsXEmbed() {
        EmbeddedFrame frame = (EmbeddedFrame)target;
        if (frame != null) {
            return frame.supportsXEmbed();
        } else {
            return false;
        }
    }

    public void setVisible(boolean vis) {
        super.setVisible(vis);
        xembedLog.fine("Peer made visible");
        if (vis && !supportsXEmbed()) {
            xembedLog.fine("Synthesizing FocusIn");
            // Fix for 4878303 - generate WINDOW_GAINED_FOCUS and update if we were focused
            // since noone will do it for us(WM does it for regular top-levels)
            synthesizeFocusInOut(true);
        }
    }
    public native void synthesizeFocusInOut(boolean b);

    native boolean isXEmbedActive();
    native boolean isXEmbedApplicationActive();
    native void requestXEmbedFocus();

    public boolean requestWindowFocus() {
        xembedLog.fine("In requestWindowFocus");
        // Should check for active state of host application
        if (isXEmbedActive()) {
            if (isXEmbedApplicationActive()) {
                xembedLog.fine("Requesting focus from embedding host");
                requestXEmbedFocus();
                return true;
            } else {
                xembedLog.fine("Host application is not active");
                return false;
            }
        } else {
            xembedLog.fine("Requesting focus from X");
            return super.requestWindowFocus();
        }
    }

    void registerAccelerator(AWTKeyStroke stroke) {
//         if (stroke == null) return;
//         strokes.add(stroke);
//         if (isXEmbedActive()) {
//             nativeRegisterAccelerator(stroke, strokes.size()-1);
//         }
    }

    void unregisterAccelerator(AWTKeyStroke stroke) {
//         if (stroke == null) return;
//         if (isXEmbedActive()) {
//             int index = strokes.indexOf(stroke);
//             nativeUnregisterAccelerator(index);
//         }
    }

    void notifyStarted() {
        // Register accelerators
//         int i = 0;
//         Iterator<AWTKeyStroke> iter = strokes.iterator();
//         while (iter.hasNext()) {
//             nativeRegisterAccelerator(iter.next(), i++);
//         }

        updateDropTarget();
    }

    native void traverseOut(boolean direction);

    void handleFocusIn(int detail) {
        xembedLog.log(Level.FINE, "handleFocusIn {0}", new Object[]{Integer.valueOf(detail)});
        switch(detail) {
          case XEMBED_FOCUS_CURRENT:
              // Do nothing - just restore to the current value
              break;
          case XEMBED_FOCUS_FIRST:
              SunToolkit.executeOnEventHandlerThread(target, new Runnable() {
                      public void run() {
                          Component comp = ((Container)target).getFocusTraversalPolicy().getFirstComponent((Container)target);
                          if (comp != null) {
                              comp.requestFocusInWindow();
                          }
                      }});
              break;
          case XEMBED_FOCUS_LAST:
              SunToolkit.executeOnEventHandlerThread(target, new Runnable() {
                      public void run() {
                          Component comp = ((Container)target).getFocusTraversalPolicy().getLastComponent((Container)target);
                          if (comp != null) {
                              comp.requestFocusInWindow();
                          }
                      }});
              break;
        }
    }
    public void handleWindowFocusIn() {
        super.handleWindowFocusIn();
        xembedLog.fine("windowFocusIn");
    }
    public void handleWindowFocusOut(Window oppositeWindow) {
        super.handleWindowFocusOut(oppositeWindow);
        xembedLog.fine("windowFocusOut, opposite is null?:" + (oppositeWindow==null));
    }

    native void pReshapePrivate(int x, int y, int w, int h);

    public void setBoundsPrivate(int x, int y, int width, int height)
    {
        if (disposed)
        {
            return;
        }

        // Should set paintPending before reshape to prevent
        // thread race between PaintEvent and setBounds
        // This part of the 4267393 fix proved to be unstable under solaris,
        // dissabled due to regressions 4418155, 4486762, 4490079
        paintPending = false; //checkNativePaintOnSetBounds(width, height);

        pReshapePrivate(x, y, width, height);

        if ((width != oldWidth) || (height != oldHeight))
        {
            SurfaceData oldData = surfaceData;
            if (oldData != null) {
                surfaceData = graphicsConfig.createSurfaceData(this);
                oldData.invalidate();
            }
            oldWidth = width;
            oldHeight = height;
        }
        validateSurface(width, height);
        serialNum++;
    }

    public native Rectangle getBoundsPrivate();

    @Override
    Rectangle constrainBounds(int x, int y, int width, int height) {
        // We don't constrain the bounds of the EmbeddedFrames
        return new Rectangle(x, y, width, height);
    }
}
