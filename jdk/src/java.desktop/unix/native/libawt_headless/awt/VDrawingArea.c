/*
 * Copyright (c) 1997, 2012, Oracle and/or its affiliates. All rights reserved.
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

#ifndef HEADLESS

#include <X11/IntrinsicP.h>
#include "VDrawingAreaP.h"

#endif /* !HEADLESS */

#include <stdio.h>
#include <stdlib.h>

#ifdef __linux__
/* XXX: Shouldn't be necessary. */
#include "awt_p.h"
#endif /* __linux__ */


/******************************************************************
 *
 * Provides Canvas widget which allows the X11 visual to be
 * changed (the Motif DrawingArea restricts the visual to that
 * of the parent widget).
 *
 ******************************************************************/


/******************************************************************
 *
 * VDrawingArea Widget Resources
 *
 ******************************************************************/

#ifndef HEADLESS
#define Offset(x)       (XtOffsetOf(VDrawingAreaRec, x))
static XtResource resources[]=
{
        { XtNvisual, XtCVisual, XtRVisual, sizeof(Visual*),
          Offset(vdrawing_area.visual), XtRImmediate, CopyFromParent}
};


static void Realize();
static Boolean SetValues();
static void Destroy ();

static XmBaseClassExtRec baseClassExtRec = {
    NULL,
    NULLQUARK,
    XmBaseClassExtVersion,
    sizeof(XmBaseClassExtRec),
    NULL,                               /* InitializePrehook    */
    NULL,                               /* SetValuesPrehook     */
    NULL,                               /* InitializePosthook   */
    NULL,                               /* SetValuesPosthook    */
    NULL,                               /* secondaryObjectClass */
    NULL,                               /* secondaryCreate      */
    NULL,                               /* getSecRes data       */
    { 0 },                              /* fastSubclass flags   */
    NULL,                               /* getValuesPrehook     */
    NULL,                               /* getValuesPosthook    */
    NULL,                               /* classPartInitPrehook */
    NULL,                               /* classPartInitPosthook*/
    NULL,                               /* ext_resources        */
    NULL,                               /* compiled_ext_resources*/
    0,                                  /* num_ext_resources    */
    FALSE,                              /* use_sub_resources    */
    NULL,                               /* widgetNavigable      */
    NULL,                               /* focusChange          */
    NULL                                /* wrapper_data         */
};

VDrawingAreaClassRec vDrawingAreaClassRec = {
{
    /* Core class part */

    /* superclass         */    (WidgetClass)&xmDrawingAreaClassRec,
    /* class_name         */    "VDrawingArea",
    /* widget_size        */    sizeof(VDrawingAreaRec),
    /* class_initialize   */    NULL,
    /* class_part_initialize*/  NULL,
    /* class_inited       */    FALSE,
    /* initialize         */    NULL,
    /* initialize_hook    */    NULL,
    /* realize            */    Realize,
    /* actions            */    NULL,
    /* num_actions        */    0,
    /* resources          */    resources,
    /* num_resources      */    XtNumber(resources),
    /* xrm_class          */    NULLQUARK,
    /* compress_motion    */    FALSE,
    /* compress_exposure  */    FALSE,
    /* compress_enterleave*/    FALSE,
    /* visible_interest   */    FALSE,
    /* destroy            */    Destroy,
    /* resize             */    XtInheritResize,
    /* expose             */    XtInheritExpose,
    /* set_values         */    SetValues,
    /* set_values_hook    */    NULL,
    /* set_values_almost  */    XtInheritSetValuesAlmost,
    /* get_values_hook    */    NULL,
    /* accept_focus       */    NULL,
    /* version            */    XtVersion,
    /* callback_offsets   */    NULL,
    /* tm_table           */    NULL,
    /* query_geometry       */  NULL,
    /* display_accelerator  */  NULL,
    /* extension            */  NULL
  },

   {            /* composite_class fields */
      XtInheritGeometryManager,                 /* geometry_manager   */
      XtInheritChangeManaged,                   /* change_managed     */
      XtInheritInsertChild,                     /* insert_child       */
      XtInheritDeleteChild,                     /* delete_child       */
      NULL,                                     /* extension          */
   },

   {            /* constraint_class fields */
      NULL,                                     /* resource list        */
      0,                                        /* num resources        */
      0,                                        /* constraint size      */
      NULL,                                     /* init proc            */
      NULL,                                     /* destroy proc         */
      NULL,                                     /* set values proc      */
      NULL,                                     /* extension            */
   },

   {            /* manager_class fields */
      XtInheritTranslations,                    /* translations           */
      NULL,                                     /* syn_resources          */
      0,                                        /* num_get_resources      */
      NULL,                                     /* syn_cont_resources     */
      0,                                        /* num_get_cont_resources */
      XmInheritParentProcess,                   /* parent_process         */
      NULL,                                     /* extension           */
   },

   {            /* drawingArea class */
           /* extension */      NULL
   },

   /* VDrawingArea class part */
   {
        /* extension    */      NULL
   }
};

WidgetClass vDrawingAreaClass = (WidgetClass)&vDrawingAreaClassRec;

static Boolean
SetValues(cw, rw, nw, args, num_args)
    Widget cw;
    Widget rw;
    Widget nw;
    ArgList args;
    Cardinal *num_args;
{
    VDrawingAreaWidget current = (VDrawingAreaWidget)cw;
    VDrawingAreaWidget new_w = (VDrawingAreaWidget)nw;

    if (new_w->vdrawing_area.visual != current->vdrawing_area.visual) {
        new_w->vdrawing_area.visual = current->vdrawing_area.visual;
#ifdef DEBUG
        fprintf(stdout, "VDrawingArea.SetValues: can't change visual from: visualID=%ld to visualID=%ld\n",
                     current->vdrawing_area.visual->visualid,
                     new_w->vdrawing_area.visual->visualid);
#endif

    }

    return (False);
}

int
FindWindowInList (Window parentWindow, Window *colormap_windows, int count)
{
    int i;

    for (i = 0; i < count; i++)
        if (colormap_windows [i] == parentWindow)
           return i;
    return -1;
}

static void
Realize(w, value_mask, attributes)
    Widget               w;
    XtValueMask          *value_mask;
    XSetWindowAttributes *attributes;
{
    Widget parent;
    Status status;
    Window *colormap_windows;
    Window *new_colormap_windows;
    int count;
    int i;
    VDrawingAreaWidget vd = (VDrawingAreaWidget)w;

#ifdef DEBUG
    fprintf(stdout, "VDrawingArea.Realize: visualID=%ld, depth=%d\n",
                        vd->vdrawing_area.visual->visualid, w->core.depth);
#endif

    /* 4328588:
     * Since we have our own Realize() function, we don't execute the one for
     * our super-super class, XmManager, and miss the code which checks that
     * height and width != 0.  I've added that here.  -bchristi
     */
    if (!XtWidth(w)) XtWidth(w) = 1 ;
    if (!XtHeight(w)) XtHeight(w) = 1 ;

    w->core.window = XCreateWindow (XtDisplay (w), XtWindow (w->core.parent),
                        w->core.x, w->core.y, w->core.width, w->core.height,
                        0, w->core.depth, InputOutput,
                        vd->vdrawing_area.visual,
                        *value_mask, attributes );

    /* Need to add this window to the list of Colormap windows */
    parent = XtParent (w);
    while ((parent != NULL) && (!(XtIsShell (parent))))
        parent = XtParent (parent);
    if (parent == NULL) {
        fprintf (stderr, "NO TopLevel widget?!\n");
        return;
    }

    status = XGetWMColormapWindows (XtDisplay (w), XtWindow (parent),
                                    &colormap_windows, &count);

    /* If status is zero, add this window and shell to the list
       of colormap Windows */
    if (status == 0) {
        new_colormap_windows = (Window *) calloc (2, sizeof (Window));
        new_colormap_windows [0] = XtWindow (w);
        new_colormap_windows [1] = XtWindow (parent);
        XSetWMColormapWindows (XtDisplay (w), XtWindow (parent),
                               new_colormap_windows, 2);
        free (new_colormap_windows);
    } else {
        /* Check if parent is already in the list */
        int parent_entry = -1;

        if (count > 0)
            parent_entry = FindWindowInList (XtWindow (parent),
                                        colormap_windows, count);
        if (parent_entry == -1) {  /*  Parent not in list  */
            new_colormap_windows = (Window *) calloc (count + 2,
                                                sizeof (Window));
            new_colormap_windows [0] = XtWindow (w);
            new_colormap_windows [1] = XtWindow (parent);
            for (i = 0; i < count; i++)
                new_colormap_windows [i + 2] = colormap_windows [i];
            XSetWMColormapWindows (XtDisplay (w), XtWindow (parent),
                                   new_colormap_windows, count + 2);

        } else {        /* parent already in list, just add new window */
            new_colormap_windows = (Window *) calloc (count + 1,
                                                sizeof (Window));
            new_colormap_windows [0] = XtWindow (w);
            for (i = 0; i < count; i++)
                new_colormap_windows [i + 1] = colormap_windows [i];
            XSetWMColormapWindows (XtDisplay (w), XtWindow (parent),
                                   new_colormap_windows, count + 1);
        }
        free (new_colormap_windows);
        XFree (colormap_windows);
    }


}

static void
Destroy(Widget widget)
{
    Status status;
    Widget parent;
    Window *colormap_windows;
    Window *new_colormap_windows;
    int count;
    int listEntry;
    int i;
    int j;

    /* Need to get this window's parent shell first */
    parent = XtParent (widget);
    while ((parent != NULL) && (!(XtIsShell (parent))))
        parent = XtParent (parent);
    if (parent == NULL) {
        fprintf (stderr, "NO TopLevel widget?!\n");
        return;
    }

    status = XGetWMColormapWindows (XtDisplay (widget), XtWindow (parent),
                                    &colormap_windows, &count);

    /* If status is zero, then there were no colormap windows for
       the parent ?? */

    if (status == 0)
        return;

    /* Remove this window from the list of colormap windows */
    listEntry = FindWindowInList (XtWindow (widget), colormap_windows,
                                  count);

    new_colormap_windows = (Window *) calloc (count - 1, sizeof (Window));
    j = 0;
    for (i = 0; i < count; i++) {
        if (i == listEntry)
           continue;
        new_colormap_windows [j] = colormap_windows [i];
        j++;
    }
    XSetWMColormapWindows (XtDisplay (widget), XtWindow (parent),
                           new_colormap_windows, count - 1);
    free (new_colormap_windows);
    XFree (colormap_windows);

}
#endif /* !HEADLESS */
