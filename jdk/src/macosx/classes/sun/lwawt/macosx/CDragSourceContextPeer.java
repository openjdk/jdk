/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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


package sun.lwawt.macosx;

import java.awt.*;
import java.awt.datatransfer.*;
import java.awt.dnd.*;
import java.awt.event.*;
import java.awt.image.*;
import java.awt.peer.*;

import javax.swing.*;
import javax.swing.text.*;
import javax.accessibility.*;

import java.util.Map;
import sun.awt.dnd.*;
import sun.lwawt.LWComponentPeer;


public final class CDragSourceContextPeer extends SunDragSourceContextPeer {

    private static final CDragSourceContextPeer fInstance = new CDragSourceContextPeer(null);

    private Image  fDragImage;
    private CImage fDragCImage;
    private Point  fDragImageOffset;

    private static Component hoveringComponent = null;

    private static double fMaxImageSize = 128.0;

    static {
        String propValue = java.security.AccessController.doPrivileged(new sun.security.action.GetPropertyAction("apple.awt.dnd.defaultDragImageSize"));
        if (propValue != null) {
            try {
                double value = Double.parseDouble(propValue);
                if (value > 0) {
                    fMaxImageSize = value;
                }
            } catch(NumberFormatException e) {}
        }
    }

    private CDragSourceContextPeer(DragGestureEvent dge) {
        super(dge);
    }

    public static CDragSourceContextPeer createDragSourceContextPeer(DragGestureEvent dge) throws InvalidDnDOperationException {
        fInstance.setTrigger(dge);

        return fInstance;
    }

    // We have to overload this method just to be able to grab the drag image and its offset as shared code doesn't store it:
    public void startDrag(DragSourceContext dsc, Cursor cursor, Image dragImage, Point dragImageOffset) throws InvalidDnDOperationException {
        fDragImage = dragImage;
        fDragImageOffset = dragImageOffset;

        super.startDrag(dsc, cursor, dragImage, dragImageOffset);
    }

    protected void startDrag(Transferable transferable, long[] formats, Map formatMap) {
        DragGestureEvent trigger = getTrigger();
        InputEvent         triggerEvent = trigger.getTriggerEvent();

        Point dragOrigin = trigger.getDragOrigin();
        int extModifiers = (triggerEvent.getModifiers() | triggerEvent.getModifiersEx());
        long timestamp   = triggerEvent.getWhen();
        int clickCount   = ((triggerEvent instanceof MouseEvent) ? (((MouseEvent) triggerEvent).getClickCount()) : 1);

        // Get drag source component and its peer:
        Component component = trigger.getComponent();
        Point componentOffset = new Point();
        ComponentPeer peer = component.getPeer();

        // For a lightweight component traverse up the hierarchy to the first heavyweight
        // which will be used as the ComponentModel for the native drag source.
        if (component.isLightweight()) {
            Point loc = component.getLocation();
            componentOffset.translate(loc.x, loc.y);

            for (Component parent = component.getParent(); parent != null; parent = parent.getParent()) {
                if (parent.isLightweight() == false) {
                    peer = parent.getPeer();
                    break;
                }

                loc = parent.getLocation();
                componentOffset.translate(loc.x, loc.y);
            }
        }

        // Make sure the drop target is a ComponentModel:
        if (!(peer instanceof LWComponentPeer))
            throw new IllegalArgumentException("DragSource's peer must be a ComponentModel.");

        // Get model pointer (CButton.m and such) and its native peer:
        LWComponentPeer model = (LWComponentPeer) peer;
        CPlatformWindow platformWindow = (CPlatformWindow) model.getPlatformWindow();
        long nativeWindowPtr = platformWindow.getNSWindowPtr();

        // Get drag cursor:
        Cursor cursor = this.getCursor();

        // If there isn't any drag image make one of default appearance:
        if (fDragImage == null)
            this.setDefaultDragImage(component);

        // Get drag image (if any) as BufferedImage and convert that to CImage:
        long  dragImage;
        Point dragImageOffset;

        if (fDragImage != null) {
            BufferedImage bi = (fDragImage instanceof BufferedImage ? (BufferedImage) fDragImage : null);

            if (bi == null) {
                // Create a new buffered image:
                int width  = fDragImage.getWidth(null);
                int height = fDragImage.getHeight(null);
                bi = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB_PRE);

                // Draw drag image into the buffered image:
                Graphics g = bi.getGraphics();
                g.drawImage(fDragImage, 0, 0, null);
                g.dispose();
            }
            /*   TODO:BG
            fDragCImage = CImage.getCreator().createImage(bi);
            dragImage = fDragCImage.getNSImage(); */
            fDragCImage = null;
            dragImage = 0L;
            dragImageOffset = fDragImageOffset;
        } else {

            fDragCImage = null;
            dragImage = 0L;
            dragImageOffset = new Point(0, 0);
        }

        // Get NS drag image instance if we have a drag image:
        long nsDragImage = 0L; //TODO:BG (fDragCImage != null ? fDragCImage.getNSImage() : 0L);

        try {
            // Create native dragging source:
            final long nativeDragSource = createNativeDragSource(component, peer, nativeWindowPtr, transferable, triggerEvent,
                (int) (dragOrigin.getX() + componentOffset.x), (int) (dragOrigin.getY() + componentOffset.y), extModifiers,
                clickCount, timestamp, cursor, dragImage, dragImageOffset.x, dragImageOffset.y,
                getDragSourceContext().getSourceActions(), formats, formatMap);

            if (nativeDragSource == 0)
                throw new InvalidDnDOperationException("");

            setNativeContext(nativeDragSource);

            CCursorManager.getInstance().startDrag(
                    (int) (dragOrigin.getX() + componentOffset.x),
                    (int) (dragOrigin.getY() + componentOffset.y));
        }

        catch (Exception e) {
            throw new InvalidDnDOperationException("failed to create native peer: " + e);
        }

        SunDropTargetContextPeer.setCurrentJVMLocalSourceTransferable(transferable);

        // Create a new thread to run the dragging operation since it's synchronous, only coming back
        // after dragging is finished. This leaves the AWT event thread free to handle AWT events which
        // are posted during dragging by native event handlers.

        try {
            Thread dragThread = new Thread() {
                public void run() {
                    final long nativeDragSource = getNativeContext();
                    try {
                        doDragging(nativeDragSource);
                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        CCursorManager.getInstance().stopDrag();

                        releaseNativeDragSource(nativeDragSource);
                        fDragImage = null;
                        if (fDragCImage != null) {
                            fDragCImage.dispose();
                            fDragCImage = null;
                        }
                    }
                }
            };

            dragThread.start();
        }

        catch (Exception e) {
            CCursorManager.getInstance().stopDrag();

            final long nativeDragSource = getNativeContext();
            setNativeContext(0);
            releaseNativeDragSource(nativeDragSource);
            SunDropTargetContextPeer.setCurrentJVMLocalSourceTransferable(null);
            throw new InvalidDnDOperationException("failed to start dragging thread: " + e);
        }
    }

    private void setDefaultDragImage(Component component) {
        boolean handled = false;

        // Special-case default drag image, depending on the drag source type:
        if (component.isLightweight()) {
            if (component instanceof JTextComponent) {
                this.setDefaultDragImage((JTextComponent) component);
                handled = true;
            } else if (component instanceof JTree) {
                            this.setDefaultDragImage((JTree) component);
                            handled = true;
                        } else if (component instanceof JTable) {
                            this.setDefaultDragImage((JTable) component);
                            handled = true;
                        } else if (component instanceof JList) {
                            this.setDefaultDragImage((JList) component);
                            handled = true;
                        }
        }

        if (handled == false)
            this.setDefaultDragImage();
    }

    private void setDefaultDragImage(JTextComponent component) {
        DragGestureEvent trigger = getTrigger();
        int selectionStart = component.getSelectionStart();
        int selectionEnd = component.getSelectionEnd();
        boolean handled = false;

        // Make sure we're dragging current selection:
        int index = component.viewToModel(trigger.getDragOrigin());
        if ((selectionStart < selectionEnd) && (index >= selectionStart) && (index <= selectionEnd)) {
            try {
                Rectangle selectionStartBounds = component.modelToView(selectionStart);
                Rectangle selectionEndBounds = component.modelToView(selectionEnd);

                Rectangle selectionBounds = null;

                // Single-line selection:
                if (selectionStartBounds.y == selectionEndBounds.y) {
                    selectionBounds = new Rectangle(selectionStartBounds.x, selectionStartBounds.y,
                        selectionEndBounds.x - selectionStartBounds.x + selectionEndBounds.width,
                        selectionEndBounds.y - selectionStartBounds.y + selectionEndBounds.height);
                }

                // Multi-line selection:
                else {
                    AccessibleContext ctx = component.getAccessibleContext();
                    AccessibleText at = (AccessibleText) ctx;

                    selectionBounds = component.modelToView(selectionStart);
                    for (int i = selectionStart + 1; i <= selectionEnd; i++) {
                                            Rectangle charBounds = at.getCharacterBounds(i);
                                            // Invalid index returns null Rectangle
                                            // Note that this goes against jdk doc - should be empty, but is null instead
                                            if (charBounds != null) {
                                                selectionBounds.add(charBounds);
                                            }
                    }
                }

                this.setOutlineDragImage(selectionBounds);
                handled = true;
            }

            catch (BadLocationException exc) {
                // Default the drag image to component bounds.
            }
        }

        if (handled == false)
            this.setDefaultDragImage();
    }


    private void setDefaultDragImage(JTree component) {
        Rectangle selectedOutline = null;

        int[] selectedRows = component.getSelectionRows();
        for (int i=0; i<selectedRows.length; i++) {
            Rectangle r = component.getRowBounds(selectedRows[i]);
            if (selectedOutline == null)
                selectedOutline = r;
            else
                selectedOutline.add(r);
        }

        if (selectedOutline != null) {
            this.setOutlineDragImage(selectedOutline);
        } else {
            this.setDefaultDragImage();
        }
    }

    private void setDefaultDragImage(JTable component) {
        Rectangle selectedOutline = null;

        // This code will likely break once multiple selections works (3645873)
        int[] selectedRows = component.getSelectedRows();
        int[] selectedColumns = component.getSelectedColumns();
        for (int row=0; row<selectedRows.length; row++) {
            for (int col=0; col<selectedColumns.length; col++) {
                Rectangle r = component.getCellRect(selectedRows[row], selectedColumns[col], true);
                if (selectedOutline == null)
                    selectedOutline = r;
                else
                    selectedOutline.add(r);
            }
        }

        if (selectedOutline != null) {
            this.setOutlineDragImage(selectedOutline);
        } else {
            this.setDefaultDragImage();
        }
    }

    private void setDefaultDragImage(JList component) {
        Rectangle selectedOutline = null;

        // This code actually works, even under the (non-existant) multiple-selections, because we only draw a union outline
        int[] selectedIndices = component.getSelectedIndices();
        if (selectedIndices.length > 0)
            selectedOutline = component.getCellBounds(selectedIndices[0], selectedIndices[selectedIndices.length-1]);

        if (selectedOutline != null) {
            this.setOutlineDragImage(selectedOutline);
        } else {
            this.setDefaultDragImage();
        }
    }


    private void setDefaultDragImage() {
        DragGestureEvent trigger = this.getTrigger();
        Component comp = trigger.getComponent();

        setOutlineDragImage(new Rectangle(0, 0, comp.getWidth(), comp.getHeight()), true);
    }

    private void setOutlineDragImage(Rectangle outline) {
        setOutlineDragImage(outline, false);
    }

    private void setOutlineDragImage(Rectangle outline, Boolean shouldScale) {
        int width = (int)outline.getWidth();
        int height = (int)outline.getHeight();

        double scale = 1.0;
        if (shouldScale) {
            final int area = width * height;
            final int maxArea = (int)(fMaxImageSize * fMaxImageSize);

            if (area > maxArea) {
                scale = (double)area / (double)maxArea;
                width /= scale;
                height /= scale;
            }
        }

        if (width <=0) width = 1;
        if (height <=0) height = 1;

        DragGestureEvent trigger = this.getTrigger();
        Component comp = trigger.getComponent();
        Point compOffset = comp.getLocation();

        // For lightweight components add some special treatment:
        if (comp instanceof JComponent) {
            // Intersect requested bounds with visible bounds:
            Rectangle visibleBounds = ((JComponent) comp).getVisibleRect();
            Rectangle clipedOutline = outline.intersection(visibleBounds);
            if (clipedOutline.isEmpty() == false)
                outline = clipedOutline;

            // Compensate for the component offset (e.g. when contained in a JScrollPane):
            outline.translate(compOffset.x, compOffset.y);
        }

        GraphicsConfiguration config = comp.getGraphicsConfiguration();
        BufferedImage dragImage = config.createCompatibleImage(width, height, Transparency.TRANSLUCENT);

        Color paint = Color.gray;
        BasicStroke stroke = new BasicStroke(2.0f);
        int halfLineWidth = (int) (stroke.getLineWidth() + 1) / 2; // Rounded up.

        Graphics2D g2 = (Graphics2D) dragImage.getGraphics();
        g2.setPaint(paint);
        g2.setStroke(stroke);
        g2.drawRect(halfLineWidth, halfLineWidth, width - 2 * halfLineWidth - 1, height - 2 * halfLineWidth - 1);
        g2.dispose();

        fDragImage = dragImage;


        Point dragOrigin = trigger.getDragOrigin();
        Point dragImageOffset = new Point(outline.x - dragOrigin.x, outline.y - dragOrigin.y);
        if (comp instanceof JComponent) {
            dragImageOffset.translate(-compOffset.x, -compOffset.y);
        }

        if (shouldScale) {
            dragImageOffset.x /= scale;
            dragImageOffset.y /= scale;
        }

        fDragImageOffset = dragImageOffset;
    }

    /**
     * upcall from native code
     */
    private void dragMouseMoved(final int targetActions,
                                final int modifiers,
                                final int x, final int y) {

        CCursorManager.getInstance().updateDragPosition(x, y);

        Component rootComponent = SwingUtilities.getRoot(getComponent());
        if(rootComponent != null) {
            Point componentPoint = new Point(x, y);
            SwingUtilities.convertPointFromScreen(componentPoint, rootComponent);
            Component componentAt = SwingUtilities.getDeepestComponentAt(rootComponent, componentPoint.x, componentPoint.y);
            if(componentAt != hoveringComponent) {
                if(hoveringComponent != null) {
                    dragExit(x, y);
                }
                if(componentAt != null) {
                    dragEnter(targetActions, modifiers, x, y);
                }
                hoveringComponent = componentAt;
            }
        }
        postDragSourceDragEvent(targetActions, modifiers, x, y,
                                DISPATCH_MOUSE_MOVED);
    }

    /**
     * upcall from native code
     */
    private void dragEnter(final int targetActions,
                           final int modifiers,
                           final int x, final int y) {
        CCursorManager.getInstance().updateDragPosition(x, y);

        postDragSourceDragEvent(targetActions, modifiers, x, y, DISPATCH_ENTER);
    }

    /**
     * upcall from native code - reset hovering component
     */
    private void resetHovering() {
        hoveringComponent = null;
    }

    public void setCursor(Cursor c) throws InvalidDnDOperationException {
        // TODO : BG
        //AWTLockAccess.awtLock();
        super.setCursor(c);
        //AWTLockAccess.awtUnlock();
    }

    protected native void setNativeCursor(long nativeCtxt, Cursor c, int cType);

    // Native support:
    private native long createNativeDragSource(Component component, ComponentPeer peer, long nativePeer, Transferable transferable,
        InputEvent triggerEvent, int dragPosX, int dragPosY, int extModifiers, int clickCount, long timestamp,
        Cursor cursor, long nsDragImage, int dragImageOffsetX, int dragImageOffsetY,
        int sourceActions, long[] formats, Map formatMap);

    private native void doDragging(long nativeDragSource);

    private native void releaseNativeDragSource(long nativeDragSource);
}
