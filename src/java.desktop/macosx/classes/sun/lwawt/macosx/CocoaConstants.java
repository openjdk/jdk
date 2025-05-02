/*
 * Copyright (c) 2011, 2023, Oracle and/or its affiliates. All rights reserved.
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


public final class CocoaConstants {
    private CocoaConstants(){}

    //from the NSEvent class reference:
    public static final int NSEventTypeLeftMouseDown      = 1;
    public static final int NSEventTypeLeftMouseUp        = 2;
    public static final int NSEventTypeRightMouseDown     = 3;
    public static final int NSEventTypeRightMouseUp       = 4;
    public static final int NSEventTypeMouseMoved         = 5;
    public static final int NSEventTypeLeftMouseDragged   = 6;
    public static final int NSEventTypeRightMouseDragged  = 7;
    public static final int NSEventTypeMouseEntered       = 8;
    public static final int NSEventTypeMouseExited        = 9;
    public static final int NSEventTypeKeyDown            = 10;
    public static final int NSEventTypeKeyUp              = 11;
    public static final int NSEventTypeFlagsChanged       = 12;

    public static final int NSEventTypeScrollWheel        = 22;
    public static final int NSEventTypeOtherMouseDown     = 25;
    public static final int NSEventTypeOtherMouseUp       = 26;
    public static final int NSEventTypeOtherMouseDragged  = 27;

    public static final int AllLeftMouseEventsMask =
        1 << NSEventTypeLeftMouseDown |
        1 << NSEventTypeLeftMouseUp |
        1 << NSEventTypeLeftMouseDragged;

    public static final int AllRightMouseEventsMask =
        1 << NSEventTypeRightMouseDown |
        1 << NSEventTypeRightMouseUp |
        1 << NSEventTypeRightMouseDragged;

    public static final int AllOtherMouseEventsMask =
        1 << NSEventTypeOtherMouseDown |
        1 << NSEventTypeOtherMouseUp |
        1 << NSEventTypeOtherMouseDragged;

    /*
    NSEventTypeAppKitDefined      = 13,
    NSEventTypeSystemDefined      = 14,
    NSEventTypeApplicationDefined = 15,
    NSEventTypePeriodic           = 16,
    NSEventTypeCursorUpdate       = 17,
    NSEventTypeScrollWheel        = 22,
    NSEventTypeTabletPoint        = 23,
    NSEventTypeTabletProximity    = 24,
    NSEventTypeGesture            = 29,
    NSEventTypeMagnify            = 30,
    NSEventTypeSwipe              = 31,
    NSEventTypeRotate             = 18,
    NSEventTypeBeginGesture       = 19,
    NSEventTypeEndGesture         = 20
    */

    // See http://developer.apple.com/library/mac/#documentation/Carbon/Reference/QuartzEventServicesRef/Reference/reference.html

    public static final int kCGMouseButtonLeft   = 0;
    public static final int kCGMouseButtonRight  = 1;
    public static final int kCGMouseButtonCenter = 2;

    // See https://wiki.mozilla.org/NPAPI:CocoaEventModel

    public static final int NPCocoaEventDrawRect           = 1;
    public static final int NPCocoaEventMouseDown          = 2;
    public static final int NPCocoaEventMouseUp            = 3;
    public static final int NPCocoaEventMouseMoved         = 4;
    public static final int NPCocoaEventMouseEntered       = 5;
    public static final int NPCocoaEventMouseExited        = 6;
    public static final int NPCocoaEventMouseDragged       = 7;
    public static final int NPCocoaEventKeyDown            = 8;
    public static final int NPCocoaEventKeyUp              = 9;
    public static final int NPCocoaEventFlagsChanged       = 10;
    public static final int NPCocoaEventFocusChanged       = 11;
    public static final int NPCocoaEventWindowFocusChanged = 12;
    public static final int NPCocoaEventScrollWheel        = 13;
    public static final int NPCocoaEventTextInput          = 14;
}
