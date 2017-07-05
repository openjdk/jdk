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

package sun.lwawt.macosx.event;

import sun.lwawt.macosx.CocoaConstants;
import java.awt.event.*;

/**
 * A class representing Cocoa NSEvent class with the fields only necessary for
 * JDK functionality.
 */
public final class NSEvent {
    private int type;
    private int modifierFlags;

    // Mouse event information
    private int clickCount;
    private int buttonNumber;
    private int x;
    private int y;
    private double scrollDeltaY;
    private double scrollDeltaX;
    private int absX;
    private int absY;

    // Key event information
    private short keyCode;
    private String charactersIgnoringModifiers;

    public NSEvent(int type, int modifierFlags, short keyCode, String charactersIgnoringModifiers) {
        this.type = type;
        this.modifierFlags = modifierFlags;
        this.keyCode = keyCode;
        this.charactersIgnoringModifiers = charactersIgnoringModifiers;
    }

    public NSEvent(int type, int modifierFlags, int clickCount, int buttonNumber,
                   int x, int y, int absX, int absY,
                   double scrollDeltaY, double scrollDeltaX) {
        this.type = type;
        this.modifierFlags = modifierFlags;
        this.clickCount = clickCount;
        this.buttonNumber = buttonNumber;
        this.x = x;
        this.y = y;
        this.absX = absX;
        this.absY = absY;
        this.scrollDeltaY = scrollDeltaY;
        this.scrollDeltaX = scrollDeltaX;
    }

    public int getType() {
        return type;
    }

    public int getModifierFlags() {
        return modifierFlags;
    }

    public int getClickCount() {
        return clickCount;
    }

    public int getButtonNumber() {
        return buttonNumber;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public double getScrollDeltaY() {
        return scrollDeltaY;
    }

    public double getScrollDeltaX() {
        return scrollDeltaX;
    }

    public int getAbsX() {
        return absX;
    }

    public int getAbsY() {
        return absY;
    }

    public short getKeyCode() {
        return keyCode;
    }

    public String getCharactersIgnoringModifiers() {
        return charactersIgnoringModifiers;
    }

    @Override
    public String toString() {
        return "NSEvent[" + getType() + " ," + getModifierFlags() + " ,"
                + getClickCount() + " ," + getButtonNumber() + " ," + getX() + " ,"
                + getY() + " ," + getAbsX() + " ," + getAbsY()+ " ," + getKeyCode() + " ,"
                + getCharactersIgnoringModifiers() + "]";
    }

    /*
     * Converts an NSEvent button number to a MouseEvent constant.
     */
    public static int nsToJavaButton(int buttonNumber) {
        int jbuttonNumber = buttonNumber + 1;
        switch (buttonNumber) {
            case CocoaConstants.kCGMouseButtonLeft:
                jbuttonNumber = MouseEvent.BUTTON1;
                break;
            case CocoaConstants.kCGMouseButtonRight:
                jbuttonNumber = MouseEvent.BUTTON3;
                break;
            case CocoaConstants.kCGMouseButtonCenter:
                jbuttonNumber = MouseEvent.BUTTON2;
                break;
        }
        return jbuttonNumber;
    }

    /*
     * Converts NPCocoaEvent types to AWT event types.
     */
    public static int npToJavaEventType(int npEventType) {
        int jeventType = 0;
        switch (npEventType) {
            case CocoaConstants.NPCocoaEventMouseDown:
                jeventType = MouseEvent.MOUSE_PRESSED;
                break;
            case CocoaConstants.NPCocoaEventMouseUp:
                jeventType = MouseEvent.MOUSE_RELEASED;
                break;
            case CocoaConstants.NPCocoaEventMouseMoved:
                jeventType = MouseEvent.MOUSE_MOVED;
                break;
            case CocoaConstants.NPCocoaEventMouseEntered:
                jeventType = MouseEvent.MOUSE_ENTERED;
                break;
            case CocoaConstants.NPCocoaEventMouseExited:
                jeventType = MouseEvent.MOUSE_EXITED;
                break;
            case CocoaConstants.NPCocoaEventMouseDragged:
                jeventType = MouseEvent.MOUSE_DRAGGED;
                break;
            case CocoaConstants.NPCocoaEventKeyDown:
                jeventType = KeyEvent.KEY_PRESSED;
                break;
            case CocoaConstants.NPCocoaEventKeyUp:
                jeventType = KeyEvent.KEY_RELEASED;
                break;
        }
        return jeventType;
    }

    /*
     * Converts NSEvent types to AWT event types.
     */
    public static int nsToJavaEventType(int nsEventType) {
        int jeventType = 0;
        switch (nsEventType) {
            case CocoaConstants.NSLeftMouseDown:
            case CocoaConstants.NSRightMouseDown:
            case CocoaConstants.NSOtherMouseDown:
                jeventType = MouseEvent.MOUSE_PRESSED;
                break;
            case CocoaConstants.NSLeftMouseUp:
            case CocoaConstants.NSRightMouseUp:
            case CocoaConstants.NSOtherMouseUp:
                jeventType = MouseEvent.MOUSE_RELEASED;
                break;
            case CocoaConstants.NSMouseMoved:
                jeventType = MouseEvent.MOUSE_MOVED;
                break;
            case CocoaConstants.NSLeftMouseDragged:
            case CocoaConstants.NSRightMouseDragged:
            case CocoaConstants.NSOtherMouseDragged:
                jeventType = MouseEvent.MOUSE_DRAGGED;
                break;
            case CocoaConstants.NSMouseEntered:
                jeventType = MouseEvent.MOUSE_ENTERED;
                break;
            case CocoaConstants.NSMouseExited:
                jeventType = MouseEvent.MOUSE_EXITED;
                break;
            case CocoaConstants.NSScrollWheel:
                jeventType = MouseEvent.MOUSE_WHEEL;
                break;
            case CocoaConstants.NSKeyDown:
                jeventType = KeyEvent.KEY_PRESSED;
                break;
            case CocoaConstants.NSKeyUp:
                jeventType = KeyEvent.KEY_RELEASED;
                break;
        }
        return jeventType;
    }

    /*
     * Converts NSEvent mouse modifiers to AWT mouse modifiers.
     */
    public static native int nsToJavaMouseModifiers(int buttonNumber,
                                                    int modifierFlags);

    /*
     * Converts NSEvent key modifiers to AWT key modifiers.
     */
    public static native int nsToJavaKeyModifiers(int modifierFlags);

    /*
     * Converts NSEvent key info to AWT key info.
     */
    public static native boolean nsToJavaKeyInfo(int[] in, int[] out);

    /*
     * Converts NSEvent key modifiers to AWT key info.
     */
    public static native void nsKeyModifiersToJavaKeyInfo(int[] in, int[] out);

    public static boolean isPopupTrigger(int jmodifiers) {
        final boolean isRightButtonDown = ((jmodifiers & InputEvent.BUTTON3_DOWN_MASK) != 0);
        final boolean isLeftButtonDown = ((jmodifiers & InputEvent.BUTTON1_DOWN_MASK) != 0);
        final boolean isControlDown = ((jmodifiers & InputEvent.CTRL_DOWN_MASK) != 0);
        return isRightButtonDown || (isControlDown && isLeftButtonDown);
    }
}
