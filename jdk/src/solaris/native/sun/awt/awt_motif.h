/*
 * Copyright (c) 2000, 2001, Oracle and/or its affiliates. All rights reserved.
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

#ifndef _SWITCHXM_P_H_
#define _SWITCHXM_P_H_

#include <sun_awt_motif_MComponentPeer.h>

#include "gdefs.h"
#include <X11/Xlib.h>
#include <X11/Intrinsic.h>

#define MOTIF_NA  sun_awt_motif_MComponentPeer_MOTIF_NA
#define MOTIF_V1  sun_awt_motif_MComponentPeer_MOTIF_V1
#define MOTIF_V2  sun_awt_motif_MComponentPeer_MOTIF_V2


extern int32_t awt_motif_getIMStatusHeight(Widget w, jobject tc);
extern XVaNestedList awt_motif_getXICStatusAreaList(Widget w, jobject tc);
extern void awt_motif_Scrollbar_ButtonReleaseHandler (Widget,
                                                      XtPointer,
                                                      XEvent *,
                                                      Boolean *) ;

    /* This function causes an UnsatisfiedLinkError on Linux.
     * It's a no-op for Motif 2.1.
     * Since Linux only links against Motif 2.1, we can safely remove
     * this function altogether from the Linux build.
     * bchristi 1/22/2001
     */
#ifdef __solaris__
extern void awt_motif_adjustDragTriggerEvent(XEvent* xevent);
#endif

void awt_motif_enableSingleDragInitiator(Widget w);

#endif /* _SWITCHXM_P_H_ */
