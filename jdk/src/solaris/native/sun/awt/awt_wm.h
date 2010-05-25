/*
 * Copyright (c) 2001, 2004, Oracle and/or its affiliates. All rights reserved.
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

#ifndef _AWT_WM_H_
#define _AWT_WM_H_

#ifndef HEADLESS

#include "awt_p.h"

/*
 * Window Managers we care to distinguish.
 * See awt_wm_getRunningWM()
 */
enum wmgr_t {
    UNDETERMINED_WM,
    NO_WM,
    OTHER_WM,
    OPENLOOK_WM,
    MOTIF_WM,
    CDE_WM,
    ENLIGHTEN_WM,
    KDE2_WM,
    SAWFISH_WM,
    ICE_WM,
    METACITY_WM
};

extern void awt_wm_init(void);

extern enum wmgr_t awt_wm_getRunningWM(void);
extern Boolean awt_wm_configureGravityBuggy(void);
extern Boolean awt_wm_supportsExtendedState(jint state);

/* XWMHints.flags is declared long, so 'mask' argument is declared long too */
extern void awt_wm_removeSizeHints(Widget shell, long mask);

extern void awt_wm_setShellDecor(struct FrameData *wdata, Boolean resizable);
extern void awt_wm_setShellResizable(struct FrameData *wdata);
extern void awt_wm_setShellNotResizable(struct FrameData *wdata,
                                        int32_t width, int32_t height,
                                        Boolean justChangeSize);

extern Boolean awt_wm_getInsetsFromProp(Window w,
                 int32_t *top, int32_t *left, int32_t *bottom, int32_t *right);

/*
 * WM_STATE: WithdrawnState, NormalState, IconicState.
 * Absence of WM_STATE is treated as WithdrawnState.
 */
extern int awt_wm_getWMState(Window w);

extern void awt_wm_setExtendedState(struct FrameData *wdata, jint state);
extern Boolean awt_wm_isStateChange(struct FrameData *wdata, XPropertyEvent *e,
                                    jint *pstate);

extern void awt_wm_unshadeKludge(struct FrameData *wdata);
extern void awt_wm_updateAlwaysOnTop(struct FrameData *wdata, jboolean bLayerState);
extern Boolean awt_wm_supportsAlwaysOnTop();

#endif /* !HEADLESS */
#endif /* _AWT_WM_H_ */
