/*
 * Copyright 2000-2001 Sun Microsystems, Inc.  All Rights Reserved.
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

#if MOTIF_VERSION!=1
    #error This file should only be compiled with motif 1.2
#endif

#include "awt_motif.h"
#include <Xm/VendorSEP.h>
#include <Xm/DragCP.h>
#include "debug_util.h"
#include "awt.h"

/*
 * awt_motif_getIMStatusHeight is a cut and paste of the ImGetGeo() function
 * found in CDE Motif's Xm/XmIm.c.  It returns the height of the Input Method
 * Status region attached to the given VendorShell.  This is needed in order
 * to calculate geometry for Frames and Dialogs that contain TextField or
 * TextArea widgets.
 *
 * BCB: Copying this function out of the Motif source is a horrible
 * hack. Unfortunately, Motif tries to hide the existence of the IM Status
 * region from us so it does not provide any public way to get this info.
 * Clearly a better long term solution needs to be found.
 */

typedef struct _XmICStruct {
    struct _XmICStruct *next;
    Widget icw;
    Window focus_window;
    XtArgVal foreground;
    XtArgVal background;
    XtArgVal background_pixmap;
    XtArgVal font_list;
    XtArgVal line_space;
    int32_t status_width;
    int32_t status_height;
    int32_t preedit_width;
    int32_t preedit_height;
    Boolean has_focus;
    Boolean need_reset;
}   XmICStruct;

typedef struct {
    Widget im_widget;
    XIMStyle input_style;
    XIC xic;
    int32_t status_width;
    int32_t status_height;
    int32_t preedit_width;
    int32_t preedit_height;
    XmICStruct *iclist;
    XmICStruct *current;
}   XmImInfo;

static XFontSet extract_fontset(XmFontList);
static XmICStruct *get_iclist(Widget);

#define MAXARGS 10
static Arg xic_vlist[MAXARGS];
static Arg status_vlist[MAXARGS];
static Arg preedit_vlist[MAXARGS];

#define NO_ARG_VAL -1
#define SEPARATOR_HEIGHT 2


#ifdef MOTIF_2_1_HACK
/* To shut up warning messages from "cc -v"
 *   Copied from Solaris 2.6 /usr/dt/include/Xm/BaseClassP.h and not
 *     there in Solaris 7.
 */
#if defined(__SunOS_5_7) || defined(__SunOS_5_8)
extern XmWidgetExtData _XmGetWidgetExtData(Widget, unsigned char);
#endif

#else

/*
   The following defines are to make the XmImGetXIC to compile on systems
   lower than SunOS 5.7, so therefore the following is a copy of the
   defines on SunOS 5.7/Motif2.1 header files.
*/
/*#if defined (__SunOS_5_5_1) || defined (__SunOS_5_6)*/
#define XmPER_SHELL 0

extern XIC XmImGetXIC(
                        Widget          w,
                        unsigned int    input_policy,
                        ArgList         args,
                        Cardinal        num_args) ;
#endif

static XmICStruct *
get_iclist(Widget w)
{
    Widget p;
    XmVendorShellExtObject ve;
    XmWidgetExtData extData;
    XmImInfo *im_info;

    p = w;
    while (!XtIsShell(p))
        p = XtParent(p);

    extData = (XmWidgetExtData)_XmGetWidgetExtData((Widget) p, XmSHELL_EXTENSION);
    if (extData == NULL)
        return NULL;

    ve = (XmVendorShellExtObject) extData->widget;
    if ((im_info = (XmImInfo *) ve->vendor.im_info) == NULL)
        return NULL;
    else
        return im_info->iclist;
}

int32_t
awt_motif_getIMStatusHeight(Widget vw, jobject tc)
{
    XmICStruct *icp;
    XmVendorShellExtObject ve;
    XmWidgetExtData extData;
    XmImInfo *im_info;
    int32_t width = 0;
    int32_t height = 0;
    XRectangle rect;
    XRectangle *rp;
    int32_t old_height;
    Arg args[1];
    int32_t base_height;
    XFontSet fs;
    XFontSet fss = NULL;
    XFontSet fsp = NULL;

    extData = (XmWidgetExtData)_XmGetWidgetExtData((Widget) vw, XmSHELL_EXTENSION);
    ve = (XmVendorShellExtObject) extData->widget;

    if ((icp = get_iclist(vw)) == NULL) {
        ve->vendor.im_height = 0;
        return 0;
    }
    im_info = (XmImInfo *) ve->vendor.im_info;
    if (im_info->xic == NULL) {
        ve->vendor.im_height = 0;
        return 0;
    }
    status_vlist[0].name = XNFontSet;
    status_vlist[1].name = NULL;
    preedit_vlist[0].name = XNFontSet;
    preedit_vlist[1].name = NULL;

    xic_vlist[0].name = XNAreaNeeded;
    xic_vlist[1].name = NULL;

    im_info->status_width = 0;
    im_info->status_height = 0;
    im_info->preedit_width = 0;
    im_info->preedit_height = 0;
    for (; icp != NULL; icp = icp->next) {
        if (im_info->input_style & XIMStatusArea) {
            if (icp->status_height == 0) {
                char *ret;

                if (icp->font_list == NO_ARG_VAL ||
                    (fss = extract_fontset((XmFontList) icp->font_list)) == NULL)
                    continue;

                status_vlist[0].value = (XtArgVal) fss;
                XSetICValues(im_info->xic,
                             XNStatusAttributes, &status_vlist[0],
                             NULL);

                xic_vlist[0].value = (XtArgVal) & rp;
                ret = XGetICValues(im_info->xic,
                                   XNStatusAttributes, &xic_vlist[0],
                                   NULL);

                if (ret) {
                    /* Cannot obtain XIC value. IM server may be gone. */
                    ve->vendor.im_height = 0;
                    return 0;
                } else {
                    icp->status_width = rp->width;
                    icp->status_height = rp->height;
                    XFree(rp);
                }
            }
            if (icp->status_width > im_info->status_width)
                im_info->status_width = icp->status_width;
            if (icp->status_height > im_info->status_height)
                im_info->status_height = icp->status_height;
        }
        if (im_info->input_style & XIMPreeditArea) {
            if (icp->preedit_height == 0) {
                if (icp->font_list == NO_ARG_VAL ||
                    (fsp = extract_fontset((XmFontList) icp->font_list)) == NULL)
                    continue;

                preedit_vlist[0].value = (XtArgVal) fsp;
                XSetICValues(im_info->xic,
                             XNPreeditAttributes, &preedit_vlist[0],
                             NULL);

                xic_vlist[0].value = (XtArgVal) & rp;
                XGetICValues(im_info->xic,
                             XNPreeditAttributes, &xic_vlist[0],
                             NULL);

                icp->preedit_width = rp->width;
                icp->preedit_height = rp->height;
                XFree(rp);
            }
            if (icp->preedit_width > im_info->preedit_width)
                im_info->preedit_width = icp->preedit_width;
            if (icp->preedit_height > im_info->preedit_height)
                im_info->preedit_height = icp->preedit_height;
        }
    }

    if (im_info->current != NULL && (fss != NULL || fsp != NULL)) {
        if (im_info->current->font_list != NO_ARG_VAL &&
            (fs = extract_fontset((XmFontList) im_info->current->font_list))
            != NULL) {
            if (fss != NULL)
                status_vlist[0].value = (XtArgVal) fs;
            else
                status_vlist[0].name = NULL;
            if (fsp != NULL)
                preedit_vlist[0].value = (XtArgVal) fs;
            else
                preedit_vlist[0].name = NULL;
            XSetICValues(im_info->xic,
                         XNStatusAttributes, &status_vlist[0],
                         XNPreeditAttributes, &preedit_vlist[0],
                         NULL);
        }
    }
    if (im_info->status_height > im_info->preedit_height)
        height = im_info->status_height;
    else
        height = im_info->preedit_height;
    old_height = ve->vendor.im_height;
    if (height)
        height += SEPARATOR_HEIGHT;

    ve->vendor.im_height = height;

    XtSetArg(args[0], XtNbaseHeight, &base_height);
    XtGetValues(vw, args, 1);
    if (base_height < 0)
        base_height = 0;
    XtSetArg(args[0], XtNbaseHeight, base_height);
    XtSetValues(vw, args, 1);
    return height;
}
static XRectangle geometryRect;
XVaNestedList awt_motif_getXICStatusAreaList(Widget w, jobject tc)
{
    Widget p;
    XmVendorShellExtObject ve;
    XmWidgetExtData extData;
    XmImInfo *im_info;
    XmICStruct *icp;

    XVaNestedList list = NULL;
    XRectangle  *ssgeometry = &geometryRect;
    Pixel  bg;
    Pixel  fg;
    Pixmap bpm;
    Dimension height,width;
    Position  x,y;

    p = w;
    while (!XtIsShell(p)){
        p = XtParent(p);
    }

    XtVaGetValues(p,
                  XmNx, &x,
                  XmNy, &y,
                  XmNwidth, &width,
                  XmNheight, &height,
                  NULL);

    extData = (XmWidgetExtData)_XmGetWidgetExtData((Widget) p, XmSHELL_EXTENSION);
    if (extData == NULL) {
        return NULL;
    }

    ve = (XmVendorShellExtObject) extData->widget;
    if ((im_info = (XmImInfo *) ve->vendor.im_info) == NULL) {
        return NULL;
    } else
        icp = im_info->iclist;


    if (icp) {
        /*
         * We hava at least a textfield/textarea in the frame, use the
         * first one.
         */
        ssgeometry->x = 0;
        ssgeometry->y = height - icp->status_height;
        ssgeometry->width = icp->status_width;
        ssgeometry->height = icp->status_height;

        /*
         * use motif TextComponent's resource
         */
        fg = icp->foreground;
        bg = icp->background;
        bpm = icp->background_pixmap;

        list = XVaCreateNestedList(0,
                        XNFontSet, extract_fontset((XmFontList)icp->font_list),
                        XNArea, ssgeometry,
                        XNBackground, bg,
                        XNForeground, fg,
                        XNBackgroundPixmap, bpm,
                        NULL);
   }
    return list ;
}

static XFontSet
extract_fontset(XmFontList fl)
{
    XmFontContext context;
    XmFontListEntry next_entry;
    XmFontType type_return;
    XtPointer tmp_font;
    XFontSet first_fs = NULL;
    char *font_tag;

    if (!XmFontListInitFontContext(&context, fl))
        return NULL;

    do {
        next_entry = XmFontListNextEntry(context);
        if (next_entry) {
            tmp_font = XmFontListEntryGetFont(next_entry, &type_return);
            if (type_return == XmFONT_IS_FONTSET) {
                font_tag = XmFontListEntryGetTag(next_entry);
                if (!strcmp(font_tag, XmFONTLIST_DEFAULT_TAG)) {
                    XmFontListFreeFontContext(context);
                    XtFree(font_tag);
                    return (XFontSet) tmp_font;
                }
                XtFree(font_tag);
                if (first_fs == NULL)
                    first_fs = (XFontSet) tmp_font;
            }
        }
    } while (next_entry);

    XmFontListFreeFontContext(context);
    return first_fs;
}

/*
 * Motif 1.2 requires that an X event passed to XmDragStart is of
 * ButtonPress type. In Motif 2.1 the restriction is relaxed to allow
 * ButtonPress, ButtonRelease, KeyRelease, KeyPress, MotionNotify events
 * as drag initiators. Actually the code in Motif 1.2 works okay for these
 * events as well, since it uses only the fields that have the same values
 * in all five event types. To bypass the initial sanity check in
 * XmDragStart we forcibly change event type to ButtonPress.
 *
 * This function causes an UnsatisfiedLinkError on Linux.
 * Since Linux only links against Motif 2.1, we can safely remove
 * this function altogether from the Linux build.
 * bchristi 1/22/2001
 */

#ifdef __solaris__
void
awt_motif_adjustDragTriggerEvent(XEvent* xevent) {
    xevent->type = ButtonPress;
}
#endif /* __solaris__ */

static XmDragStartProc do_drag_start = NULL;
static Widget drag_initiator = NULL;

static void
CheckedDragStart(XmDragContext dc, Widget src, XEvent *event) {
    DASSERT(do_drag_start != NULL);
    DASSERT(drag_initiator != NULL);
    /*
     * Fix for BugTraq ID 4407057.
     * Enable the drag operation only if it is registered on the specific widget.
     * We use this check to disable Motif default drag support.
     */
    if (src == drag_initiator) {
        do_drag_start(dc, src, event);
    } else {
        /*
         * This is the last chance to destroy the XmDragContext widget.
         * NOTE: We rely on the fact that Motif 1.2 never uses the pointer
         * to XmDragContext object returned from XmDragStart.
         */
        XtDestroyWidget(dc);
    }
}

void
awt_motif_enableSingleDragInitiator(Widget w) {
    DASSERT(drag_initiator == NULL && do_drag_start == NULL && w != NULL);
    drag_initiator = w;
    do_drag_start = xmDragContextClassRec.drag_class.start;
    DASSERT(do_drag_start != NULL);
    xmDragContextClassRec.drag_class.start = (XmDragStartProc)CheckedDragStart;
}
