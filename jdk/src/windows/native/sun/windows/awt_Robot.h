/*
 * Copyright (c) 1998, 2013, Oracle and/or its affiliates. All rights reserved.
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

#ifndef AWT_ROBOT_H
#define AWT_ROBOT_H

#include "awt_Toolkit.h"
#include "awt_Object.h"
#include "sun_awt_windows_WRobotPeer.h"
#include "jlong.h"

class AwtRobot : public AwtObject
{
    public:
        AwtRobot( jobject peer );
        virtual ~AwtRobot();

        void MouseMove( jint x, jint y);
        void MousePress( jint buttonMask );
        void MouseRelease( jint buttonMask );

        void MouseWheel(jint wheelAmt);
        jint getNumberOfButtons();

        void GetRGBPixels(jint x, jint y, jint width, jint height, jintArray pixelArray);

        void KeyPress( jint key );
        void KeyRelease( jint key );
        static AwtRobot * GetRobot( jobject self );

    private:
        void DoKeyEvent( jint jkey, DWORD dwFlags );
        static jint WinToJavaPixel(USHORT r, USHORT g, USHORT b);
};

#endif // AWT_ROBOT_H
