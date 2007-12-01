/*
 * Copyright 2000-2002 Sun Microsystems, Inc.  All Rights Reserved.
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

#ifdef HEADLESS
    #error This file should not be included in headless library
#endif

#include "awt_motif.h"

#include <jvm.h>

/* Common routines required for both Motif 2.1 and Motif 1.2 */
#include <Xm/ScrollBarP.h>

/* Remove the ScrollBar widget's continuous scrolling timeout handler
   on a ButtonRelease to prevent the continuous scrolling that would
   occur if a timeout expired after the ButtonRelease.
*/
/*
 * Note: RFE:4263104 is filed when the API is available these needs to removed
 */
void
awt_motif_Scrollbar_ButtonReleaseHandler(Widget w,
                                         XtPointer data,
                                         XEvent *event,
                                         Boolean *cont)
{
  /* Remove the timeout handler. */
#define END_TIMER         (1<<2)
  XmScrollBarWidget sbw = (XmScrollBarWidget) w;
  if (sbw->scrollBar.timer != NULL) {
    XtRemoveTimeOut( sbw->scrollBar.timer );
    sbw->scrollBar.timer = (XtIntervalId)NULL;
    sbw->scrollBar.flags |= END_TIMER;
  }
}
