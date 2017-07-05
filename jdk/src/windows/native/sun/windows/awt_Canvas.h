/*
 * Copyright 1996-2007 Sun Microsystems, Inc.  All Rights Reserved.
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

#ifndef AWT_CANVAS_H
#define AWT_CANVAS_H

#include "awt_Component.h"
#include "sun_awt_windows_WCanvasPeer.h"


/************************************************************************
 * AwtCanvas class
 */

class AwtCanvas : public AwtComponent {
public:
    AwtCanvas();
    virtual ~AwtCanvas();

    virtual LPCTSTR GetClassName();
    static AwtCanvas* Create(jobject self, jobject hParent);

    virtual MsgRouting WmEraseBkgnd(HDC hDC, BOOL& didErase);
    virtual MsgRouting WmPaint(HDC hDC);

    virtual MsgRouting HandleEvent(MSG *msg, BOOL synthetic);

    static void _ResetTargetGC(void *);
    static void _SetEraseBackground(void *);

private:
    jboolean m_eraseBackground;
    jboolean m_eraseBackgroundOnResize;
 };

#endif /* AWT_CANVAS_H */
