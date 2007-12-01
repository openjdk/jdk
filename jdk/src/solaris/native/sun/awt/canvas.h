/*
 * Copyright 1995-2003 Sun Microsystems, Inc.  All Rights Reserved.
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
#ifndef _CANVAS_H_
#define _CANVAS_H_
#ifndef HEADLESS

void awt_canvas_reconfigure(struct FrameData *wdata);
Widget awt_canvas_create(XtPointer this,
                         Widget parent,
                         char *base,
                         int32_t width,
                         int32_t height,
                         Boolean parentIsFrame,
                         struct FrameData *wdata,
                         AwtGraphicsConfigDataPtr awtData);
void awt_canvas_scroll(XtPointer this, struct CanvasData *wdata, long dx, long dy);
void awt_canvas_event_handler(Widget w, XtPointer client_data,
                              XEvent *event, Boolean *cont);
void awt_canvas_handleEvent(Widget w, XtPointer client_data,
                            XEvent *event, struct WidgetInfo *winfo,
                            Boolean *cont, Boolean passEvent);

void awt_copyXEventToAWTEvent(JNIEnv* env, XEvent * xevent, jobject jevent);
KeySym awt_getX11KeySym(jint awtKey);
jobject awt_canvas_getFocusOwnerPeer();
jobject awt_canvas_getFocusedWindowPeer();
void awt_canvas_setFocusOwnerPeer(jobject peer);
void awt_canvas_setFocusedWindowPeer(jobject peer);
jobject awt_canvas_wrapInSequenced(jobject awtevent);
extern void keysymToAWTKeyCode(KeySym x11Key, jint *keycode, Boolean *mapsToUnicodeChar,
                        jint *keyLocation);
#define awt_canvas_addToFocusList awt_canvas_addToFocusListDefault
void awt_canvas_addToFocusListDefault(jobject target);
void awt_canvas_addToFocusListWithDuplicates(jobject target, jboolean acceptDuplicate);
extern void callFocusCallback(jobject focusPeer, int focus_type, jobject cause);
extern void callFocusHandler(Widget w, int eventType, jobject cause);

typedef struct FocusListElt{
  jweak requestor;
  struct FocusListElt * next;
} FocusListElt;
extern FocusListElt *focusList;
extern FocusListElt *focusListEnd;
extern jweak forGained;

#endif /* !HEADLESS */
#endif           /* _CANVAS_H_ */
