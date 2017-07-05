/*
 * Copyright 2000-2006 Sun Microsystems, Inc.  All Rights Reserved.
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

#if MOTIF_VERSION!=2
    #error This file should only be compiled with motif 2.1
#endif

#include "awt_motif.h"
#include <Xm/Xm.h>
#include "awt.h"
#include "awt_p.h"
#include "awt_Component.h"

#define XmPER_SHELL 0
extern int32_t _XmImGetGeo(
                        Widget vw) ;

#define MAXARGS 10
static Arg xic_vlist[MAXARGS];

#define SEPARATOR_HEIGHT        2
#define MTEXTAREAPEER_CLASS_NAME        "sun/awt/motif/MTextAreaPeer"
extern struct MComponentPeerIDs mComponentPeerIDs;
static jobject  mTextAreaClass = NULL;

/*
 * Get the Motif text widget from the text component peer.  XmImGetXIC()
 * function should be issued on Motif text widgets.
 */
static Widget getTextWidget(jobject tc) {
    JNIEnv *env = (JNIEnv *)JNU_GetEnv(jvm, JNI_VERSION_1_2);

    if (mTextAreaClass == NULL) {
        jclass localClass = (*env)->FindClass(env, MTEXTAREAPEER_CLASS_NAME);
        mTextAreaClass = (jclass)(*env)->NewGlobalRef(env, localClass);
        (*env)->DeleteLocalRef(env, localClass);
    }

    if ((*env)->IsInstanceOf(env, tc, mTextAreaClass)) {
        struct TextAreaData * tdata = (struct TextAreaData *)
        JNU_GetLongFieldAsPtr(env, tc, mComponentPeerIDs.pData);
        return tdata->txt;
    } else {
        struct ComponentData * tdata = (struct ComponentData *)
        JNU_GetLongFieldAsPtr(env, tc, mComponentPeerIDs.pData);
        return tdata->widget;
    }
}

/* get_im_height: returns height of the input method status area in pixels.
 *
 * This function assumes that if any XIM related information cannot be
 * queried then the app must not have an input method status area in the
 * current locale and returns zero as the status area height
 */
int32_t
awt_motif_getIMStatusHeight(Widget w, jobject tc)
{
    XIC xic = NULL;
    XRectangle *im_rect=NULL;
    int32_t im_height = 0;
    char *ret;

    xic = XmImGetXIC(getTextWidget(tc), XmPER_SHELL, NULL, 0);

    if(xic != NULL) {
        /* finally query the server for the status area geometry */
        xic_vlist[0].name = XNArea;
        xic_vlist[0].value = (XtArgVal)&im_rect;
        xic_vlist[1].name = NULL;
        ret=XGetICValues(xic, XNStatusAttributes, &xic_vlist[0], NULL);
        if (ret == NULL && im_rect != NULL) {
            im_height = im_rect->height;
            if (im_height > 0) {
                im_height += SEPARATOR_HEIGHT;
            }
            XFree(im_rect);
        } else {
            im_height = 0;
        }
    }

    if (im_height == 0) {
        im_height = _XmImGetGeo(w);
    }

#if defined(DEBUG)
    jio_fprintf(stderr,"awt_motif_getIMStatusHeight: Height = %d",im_height);
#endif
    return im_height;
}


static XRectangle geomRect;
static Pixmap bpm;
XVaNestedList awt_motif_getXICStatusAreaList(Widget w, jobject tc)
{
    XIC xic;

    XRectangle *im_rect;
    XFontSet   *im_font;

    Pixel bg ;
    Pixel fg ;
    Dimension height, width ;
    Position x,y ;

    XVaNestedList list = NULL;

    char *ret;
    Widget p=w;

    while (!XtIsShell(p)) {
        p = XtParent(p);
    }

    XtVaGetValues(p,
        XmNx, &x,
        XmNy, &y,
        XmNwidth, &width,
        XmNheight, &height,
        XmNbackgroundPixmap, &bpm,
        NULL);



    xic = XmImGetXIC(getTextWidget(tc), XmPER_SHELL, NULL, 0);
    if(xic == NULL)
    {
#if defined DEBUG
        jio_fprintf(stderr,"Could not get XIC");
#endif
        return list ;
    }

   /* finally query the server for the required attributes area geometry */
    xic_vlist[0].name = XNFontSet ;
    xic_vlist[0].value =  (XtArgVal) &im_font ;
    xic_vlist[1].name = XNArea;
    xic_vlist[1].value = (XtArgVal) &im_rect;
    xic_vlist[2].name = XNBackground ;
    xic_vlist[2].value = (XtArgVal) &bg ;
    xic_vlist[3].name = XNForeground ;
    xic_vlist[3].value = (XtArgVal) &fg ;
    xic_vlist[4].name = NULL;


    if(ret=XGetICValues(xic, XNStatusAttributes, &xic_vlist[0], NULL))
    {
        return list ;
    } else {
        geomRect.x = 0 ;
        geomRect.y = height - im_rect->height ;
        geomRect.width = im_rect->width ;
        geomRect.height = im_rect->height ;
        XFree(im_rect) ;

        list = XVaCreateNestedList(0 ,
                        XNFontSet, im_font ,
                        XNArea, &geomRect ,
                        XNBackground, bg ,
                        XNForeground, fg ,
                        XNBackgroundPixmap, &bpm ,
                        NULL );
    }
#if defined(DEBUG)
    jio_fprintf(stderr,"awt_motif_getXICStatusAreaList:\n");
    jio_fprintf(stderr,"XNArea:x=%d,y=%d,width=%d,height=%d\n", \
         geomRect.x,geomRect.y,geomRect.width,geomRect.height);
    jio_fprintf(stderr,"XNBackground=0x%x\n",bg);
    jio_fprintf(stderr,"XNForeground=0x%x\n",fg);
    jio_fprintf(stderr,"XNBackgroundPixmap=0x%x\n",bpm);
#endif
    return list ;

}

    /* This function causes an UnsatisfiedLinkError on Linux.
     * Since Linux only links against Motif 2.1 and under 2.1 this function
     * is a no-op, we can safely remove
     * this function altogether from the Linux build.
     * bchristi 1/22/2001
     */

#ifdef __solaris__
void
awt_motif_adjustDragTriggerEvent(XEvent* xevent) {
    /* Do nothing. In Motif 2.1 the sanity check is corrected
       to allow any imput event as a drag trigger event. */
}
#endif /* __solaris__ */

static void
CheckDragInitiator(Widget w, XtPointer client_data,
                   XmDragStartCallbackStruct* cbstruct) {
    Widget drag_initiator = (Widget)client_data;
    /*
     * Fix for BugTraq ID 4407057.
     * Enable the drag operation only if it is registered on the specific
     * widget. We use this check to disable Motif default drag support.
     */
    if (drag_initiator != cbstruct->widget) {
        cbstruct->doit = False;
    }
}

void
awt_motif_enableSingleDragInitiator(Widget w) {
    XtAddCallback(XmGetXmDisplay(XtDisplay(w)),
                  XmNdragStartCallback, (XtCallbackProc)CheckDragInitiator,
                  (XtPointer)w);
}
