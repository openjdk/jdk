/*
 * Copyright 1995-2001 Sun Microsystems, Inc.  All Rights Reserved.
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

#include "awt_p.h"
#include "java_awt_Component.h"
#include "sun_awt_motif_MComponentPeer.h"
#include "sun_awt_motif_MChoicePeer.h"

#include "awt_Component.h"
#include "awt_MToolkit.h"

#include "multi_font.h"
#include <jni.h>
#include <jni_util.h>
#include <Xm/CascadeBG.h>

extern struct ComponentIDs componentIDs;
extern struct ContainerIDs containerIDs;
extern struct MComponentPeerIDs mComponentPeerIDs;
extern AwtGraphicsConfigDataPtr
    copyGraphicsConfigToPeer(JNIEnv *env, jobject this);

static void geometry_hook(Widget wid, Widget hooked_widget, XtGeometryHookData call_data) {
    XtWidgetGeometry *request;
    JNIEnv *env;
    struct ChoiceData *cdata;
    struct WidgetInfo *winfo = NULL;

    jobject target;
    jobject parent;
    jint y, height;

    if ((call_data->widget == hooked_widget) &&
        (call_data->type == XtHpostGeometry) &&
        (call_data->result == XtGeometryYes)) {

        request = call_data->request;

        env = (JNIEnv *)JNU_GetEnv(jvm, JNI_VERSION_1_2);
        DASSERT(env != NULL);

        winfo=findWidgetInfo(hooked_widget);

        if (winfo != NULL && XmIsRowColumn(hooked_widget)) {
            target = (*env)->GetObjectField(env, (jobject)winfo->peer, mComponentPeerIDs.target);
            cdata = (struct ChoiceData *) JNU_GetLongFieldAsPtr(env, (jobject)winfo->peer, mComponentPeerIDs.pData);
            DASSERT(target != NULL);
            DASSERT(cdata != NULL && cdata->comp.widget != NULL)
            if (request->request_mode & CWHeight) {
                height = (*env)->GetIntField(env, target, componentIDs.height);
                if (request->height > 0 && request->height != height) {
                  parent = (*env)->CallObjectMethod(env, target, componentIDs.getParent);
                  if ((parent != NULL) && ((*env)->GetObjectField(env, parent, containerIDs.layoutMgr) != NULL)) {
                      y = cdata->bounds_y;
                      if (request->height < cdata->bounds_height) {
                          y += (cdata->bounds_height - request->height) / 2;
                      }
                      XtVaSetValues(hooked_widget, XmNy, y, NULL);
                      (*env)->SetIntField(env, target, componentIDs.y, y);
                  }
                  if (parent != NULL) {
                      (*env)->DeleteLocalRef(env, parent);
                  }
                }
                (*env)->SetIntField(env, target, componentIDs.height, request->height);
            }
            if (request->request_mode & CWWidth) {
                (*env)->SetIntField(env, target, componentIDs.width, request->width);
            }
            (*env)->DeleteLocalRef(env, target);
        }
    }
}

static void
Choice_callback(Widget menu_item,
                jobject this,
                XmAnyCallbackStruct * cbs)
{
    intptr_t index;
    JNIEnv *env = (JNIEnv *)JNU_GetEnv(jvm, JNI_VERSION_1_2);

    XtVaGetValues(menu_item, XmNuserData, &index, NULL);
    /* index stored in user-data is 1-based instead of 0-based because */
    /* of a bug in XmNuserData */
    index--;

    JNU_CallMethodByName(env, NULL, this, "action", "(I)V", (jint)index);
    if ((*env)->ExceptionOccurred(env)) {
        (*env)->ExceptionDescribe(env);
        (*env)->ExceptionClear(env);
    }
}

static void  addItems
  (JNIEnv *env, jobject this, jstring *items, jsize nItems, jint index)
{
    char *citem = NULL;
    struct ChoiceData *odata;
    Widget bw;
#define MAX_ARGC 10
    Arg args[MAX_ARGC];
    Cardinal argc, argc1;
    jsize i;
    Pixel bg;
    Pixel fg;
    short cols;
    int32_t sheight;
    Dimension height;
    Widget *firstNewItem = NULL;

    XmString mfstr = NULL;
    XmFontList fontlist = NULL;
    jobject font = awtJNI_GetFont(env, this);
    Boolean IsMultiFont = awtJNI_IsMultiFont(env, font);

    if ((items == NULL) || (nItems == 0)) {
        return;
    }

    AWT_LOCK();

    odata = (struct ChoiceData *)
      JNU_GetLongFieldAsPtr(env,this,mComponentPeerIDs.pData);

    if (odata == NULL) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        AWT_UNLOCK();

        return;
    }
    if (odata->maxitems == 0 || (index + nItems) > odata->maxitems) {
        odata->maxitems = index + nItems + 20;
        if (odata->n_items > 0) {
            /* grow the list of items */
            odata->items = (Widget *)
                realloc((void *) (odata->items)
                        ,sizeof(Widget) * odata->maxitems);
        } else {
            odata->items = (Widget *) malloc(sizeof(Widget) * odata->maxitems);
        }
        if (odata->items == NULL) {
            JNU_ThrowOutOfMemoryError(env, "OutOfMemoryError");
            AWT_UNLOCK();
            return;
        }
    }
    XtVaGetValues(odata->comp.widget, XmNbackground, &bg, NULL);
    XtVaGetValues(odata->comp.widget, XmNforeground, &fg, NULL);

    argc = 0;
    XtSetArg(args[argc], XmNbackground, bg);
    argc++;
    XtSetArg(args[argc], XmNforeground, fg);
    argc++;

    firstNewItem = &(odata->items[index]);
    for (i = 0; i < nItems; i++) {
        argc1 = argc;
        if (IsMultiFont) {
            mfstr = awtJNI_MakeMultiFontString(env, items[i], font);
            fontlist = awtJNI_GetFontList(env, font);
            /* XXX: XmNuserData doesn't seem to work when passing in zero */
            /* so we increment the index before passing it in. */
            XtSetArg(args[argc1], XmNuserData, (XtPointer)((intptr_t)(index + i + 1)));
            argc1++;
            XtSetArg(args[argc1], XmNfontList, fontlist);
            argc1++;
            XtSetArg(args[argc1], XmNlabelString, mfstr);
            argc1++;

            DASSERT(!(argc1 > MAX_ARGC));

            bw = XmCreatePushButton(odata->menu, "", args, argc1);

            /* Free resurces */
            if ( fontlist != NULL )
            {
                XmFontListFree(fontlist);
                fontlist = NULL;
            }
            if (mfstr != NULL) {
                XmStringFree(mfstr);
                mfstr = NULL;
            }
        } else {
            citem = (char *) JNU_GetStringPlatformChars(env, items[i], NULL);
            /* XXX: XmNuserData doesn't seem to work when passing in zero */
            /* so we increment the index before passing it in. */
            XtSetArg(args[argc1], XmNuserData, (XtPointer)((intptr_t)(index + i + 1)));
            argc1++;
            DASSERT(!(argc1> MAX_ARGC));
            bw = XmCreatePushButton(odata->menu, citem, args, argc1);
            JNU_ReleaseStringPlatformChars(env, items[i], (const char *) citem);
            citem = NULL;
        }

         XtAddCallback(bw,
                       XmNactivateCallback,
                       (XtCallbackProc) Choice_callback,
                       (XtPointer) JNU_GetLongFieldAsPtr(env, this, mComponentPeerIDs.jniGlobalRef));
        odata->items[index + i] = bw;
        odata->n_items++;
    }

    XtManageChildren(firstNewItem, nItems);

    sheight = DisplayHeight(awt_display, DefaultScreen(awt_display));

    XtVaGetValues(odata->menu, XmNheight, &height, NULL);

    while ( height > sheight ) {
        cols = ++odata->n_columns;
        XtVaSetValues(odata->menu, XmNnumColumns, cols, NULL);
        XtVaGetValues(odata->menu, XmNheight, &height, NULL);
    }

    AWT_UNLOCK();
}

/*
 * Class:     sun_awt_motif_MChoicePeer
 * Method:    create
 * Signature: (Lsun/awt/motif/MComponentPeer;)V
 */
JNIEXPORT void JNICALL Java_sun_awt_motif_MChoicePeer_create
  (JNIEnv * env, jobject this, jobject parent)
{
    struct ChoiceData *odata;
    struct ComponentData *wdata;
#undef MAX_ARGC
#define MAX_ARGC 30
    Arg args[MAX_ARGC];
    Cardinal argc;
    Pixel bg;
    Pixel fg;
    Widget label;
    Widget button;
    Widget hookobj;
    jobject globalRef = awtJNI_CreateAndSetGlobalRef(env, this);
    AwtGraphicsConfigDataPtr adata;
    jobject target;
    Dimension width = 0, height = 0;
    jclass clsDimension;
    jobject dimension;
    jobject peer;

    AWT_LOCK();

    if (JNU_IsNull(env, parent)) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        return;
    }

    adata = copyGraphicsConfigToPeer(env, this);

    wdata = (struct ComponentData *)
        JNU_GetLongFieldAsPtr(env,parent,mComponentPeerIDs.pData);

    if (wdata == NULL) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        AWT_UNLOCK();

        return;
    }

    odata = ZALLOC(ChoiceData);
    if (odata == NULL) {
        JNU_ThrowOutOfMemoryError(env, "OutOfMemoryError");
        AWT_UNLOCK();
        return;
    }
    JNU_SetLongFieldFromPtr(env,this,mComponentPeerIDs.pData,odata);

    odata->items = NULL;
    odata->maxitems = 0;
    odata->n_items = 0;
    odata->n_columns = 1;

    XtVaGetValues(wdata->widget, XmNbackground, &bg, NULL);
    XtVaGetValues(wdata->widget, XmNforeground, &fg, NULL);

    argc = 0;
    XtSetArg(args[argc], XmNx, 0);
    argc++;
    XtSetArg(args[argc], XmNy, 0);
    argc++;
    XtSetArg(args[argc], XmNvisual, adata->awt_visInfo.visual);
    argc++;
    XtSetArg(args[argc], XmNbackground, bg);
    argc++;
    XtSetArg(args[argc], XmNforeground, fg);
    argc++;

    XtSetArg(args[argc], XmNorientation, XmVERTICAL);
    argc++;
    XtSetArg(args[argc], XmNpacking, XmPACK_COLUMN);
    argc++;
    XtSetArg(args[argc], XmNnumColumns, (short)1);
    argc++;
    /* Fix for 4303064 by ibd@sparc.spb.su: pop-up shells will have
     * ancestor_sensitive False if the parent was insensitive when the shell
     * was created.  Since XtSetSensitive on the parent will not modify the
     * resource of the pop-up child, clients are advised to include a resource
     * specification of the form '*TransientShell.ancestorSensitive: True' in
     * the application defaults resource file or to otherwise ensure that the
     * parent is sensitive when creating pop-up shells.
     */
    XtSetArg(args[argc], XmNancestorSensitive, True);
    argc++;

    DASSERT(!(argc > MAX_ARGC));
    odata->menu = XmCreatePulldownMenu(wdata->widget, "pulldown", args, argc);


    target = (*env)->GetObjectField(env, this, mComponentPeerIDs.target);
    clsDimension = (*env)->FindClass(env, "java/awt/Dimension");
    dimension = JNU_CallMethodByName(env,
                                     NULL,
                                     this,
                                     "getPreferredSize",
                                     "()Ljava/awt/Dimension;").l;
    DASSERT(clsDimension != NULL);
    width  = (Dimension)((*env)->GetIntField(env, dimension, (*env)->GetFieldID(env, clsDimension, "width" , "I")));
    height = (Dimension)((*env)->GetIntField(env, dimension, (*env)->GetFieldID(env, clsDimension, "height", "I")));

    argc = 0;
    XtSetArg(args[argc], XmNx, 0);
    argc++;
    XtSetArg(args[argc], XmNy, 0);
    argc++;
    XtSetArg(args[argc], XmNwidth, width);
    argc++;
    XtSetArg(args[argc], XmNheight, height);
    argc++;
    XtSetArg(args[argc], XmNmarginHeight, 0);
    argc++;
    XtSetArg(args[argc], XmNmarginWidth, 0);
    argc++;
    XtSetArg(args[argc], XmNrecomputeSize, False);
    argc++;
    XtSetArg(args[argc], XmNresizeHeight, False);
    argc++;
    XtSetArg(args[argc], XmNresizeWidth, False);
    argc++;
    XtSetArg(args[argc], XmNspacing, False);
    argc++;
    XtSetArg(args[argc], XmNborderWidth, 0);
    argc++;
    XtSetArg(args[argc], XmNnavigationType, XmTAB_GROUP);
    argc++;
    XtSetArg(args[argc], XmNtraversalOn, True);
    argc++;
    XtSetArg(args[argc], XmNorientation, XmVERTICAL);
    argc++;
    XtSetArg(args[argc], XmNadjustMargin, False);
    argc++;
    XtSetArg(args[argc], XmNbackground, bg);
    argc++;
    XtSetArg(args[argc], XmNforeground, fg);
    argc++;
    XtSetArg(args[argc], XmNsubMenuId, odata->menu);
    argc++;
    XtSetArg (args[argc], XmNscreen,
              ScreenOfDisplay(awt_display, adata->awt_visInfo.screen));
    argc++;

    DASSERT(!(argc > MAX_ARGC));
    odata->comp.widget = XmCreateOptionMenu(wdata->widget, "", args, argc);

    hookobj = XtHooksOfDisplay(XtDisplayOfObject(odata->comp.widget));
    XtAddCallback(hookobj,
                  XtNgeometryHook,
                  (XtCallbackProc) geometry_hook,
                  (XtPointer) odata->comp.widget);

    label = XmOptionLabelGadget(odata->comp.widget);
    if (label != NULL) {
        XtUnmanageChild(label);
    }
    XtSetMappedWhenManaged(odata->comp.widget, False);
    XtManageChild(odata->comp.widget);

    AWT_UNLOCK();
}

/*
 * Class:     sun_awt_motif_MChoicePeer
 * Method:    addItem
 * Signature: (Ljava/lang/String;I)V
 */
JNIEXPORT void JNICALL Java_sun_awt_motif_MChoicePeer_addItem
  (JNIEnv *env, jobject this, jstring item, jint index)
{
    if (JNU_IsNull(env, item)) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        return;
    }
    addItems(env, this, &item, 1, index);
}

/*
 * Class:     sun_awt_motif_MChoicePeer
 * Method:    pSelect
 * Signature: (I)V
 */
JNIEXPORT void JNICALL Java_sun_awt_motif_MChoicePeer_pSelect
  (JNIEnv *env, jobject this, jint index, jboolean init)
{
    struct ChoiceData *odata;

    AWT_LOCK();

    odata = (struct ChoiceData *)
      JNU_GetLongFieldAsPtr(env,this,mComponentPeerIDs.pData);

    if (odata == NULL) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        AWT_UNLOCK();
        return;
    }
    if (index > odata->n_items || index < 0) {
        JNU_ThrowIllegalArgumentException(env, "IllegalArgumentException");
        AWT_UNLOCK();
        return;
    }
    XtVaSetValues(odata->comp.widget,
                  XmNmenuHistory, odata->items[index],
                  NULL);
    AWT_UNLOCK();
}

/*
 * Class:     sun_awt_motif_MChoicePeer
 * Method:    setFont
 * Signature: (Ljava/awt/Font;)V
 */
JNIEXPORT void JNICALL Java_sun_awt_motif_MChoicePeer_setFont
  (JNIEnv *env, jobject this, jobject f)
{
    struct ChoiceData *cdata;
    struct FontData *fdata;
    XmFontList fontlist;
    char *err;

    if (JNU_IsNull(env, f)) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        return;
    }
    AWT_LOCK();

    fdata = awtJNI_GetFontData(env, f, &err);
    if (fdata == NULL) {
        JNU_ThrowInternalError(env, err);
        AWT_UNLOCK();
        return;
    }
    cdata = (struct ChoiceData *)
      JNU_GetLongFieldAsPtr(env,this,mComponentPeerIDs.pData);
    if (cdata == NULL || cdata->comp.widget == NULL) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        AWT_UNLOCK();
        return;
    }
    if (awtJNI_IsMultiFont(env, f)) {
        fontlist = awtJNI_GetFontList(env, f);
    } else {
        fontlist = XmFontListCreate(fdata->xfont, "labelFont");
    }

    if (fontlist != NULL) {
        jint i;

        XtVaSetValues(cdata->comp.widget,
                      XmNfontList, fontlist,
                      NULL);
        XtVaSetValues(cdata->menu,
                      XmNfontList, fontlist,
                      NULL);
        for (i = 0; i < cdata->n_items; i++) {
            XtVaSetValues(cdata->items[i],
                          XmNfontList, fontlist,
                          NULL);
        }

        XmFontListFree(fontlist);
    } else {
        JNU_ThrowNullPointerException(env, "NullPointerException");
    }
    AWT_UNLOCK();
}

/* Fix for bug 4326619 */
/*
 * Class:     sun_awt_motif_MChoicePeer
 * Method:    freeNativeData
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_sun_awt_motif_MChoicePeer_freeNativeData
  (JNIEnv *env, jobject this)
{
    struct ChoiceData *cdata;

    AWT_LOCK();

    cdata = (struct ChoiceData *)
      JNU_GetLongFieldAsPtr(env, this, mComponentPeerIDs.pData);

    cdata->n_items = 0;
    free((void *)cdata->items);
    cdata->items = NULL;
    AWT_UNLOCK();
}

/*
 * Class:     sun_awt_motif_MChoicePeer
 * Method:    setBackground
 * Signature: (Ljava/awt/Color;)V
 */
JNIEXPORT void JNICALL Java_sun_awt_motif_MChoicePeer_setBackground
  (JNIEnv *env, jobject this, jobject c)
{
    struct ChoiceData *bdata;
    Pixel bg;
    Pixel fg;
    WidgetList children;
    Cardinal numChildren;
    int32_t i;

    if (JNU_IsNull(env, c)) {
        JNU_ThrowNullPointerException(env, "NullPointerException: null color");
        return;
    }
    AWT_LOCK();

    bdata = (struct ChoiceData *)
        JNU_GetLongFieldAsPtr(env,this,mComponentPeerIDs.pData);
    if (bdata == NULL || bdata->comp.widget == NULL) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        AWT_UNLOCK();
        return;
    }
    /* Get background color */
    bg = awtJNI_GetColor(env, c);

    /*
       XmChangeColor(), in addtion to changing the background and
       selection colors, also changes the foreground color to be
       what it thinks should be. However, we want to use the color
       that gets set by setForeground() instead. We therefore need to
       save the current foreground color here, and then set it again
       after the XmChangeColor() occurs.
    */
    XtVaGetValues(bdata->comp.widget, XmNforeground, &fg, NULL);

    /* Set color */
    XmChangeColor(bdata->comp.widget, bg);
    XtVaSetValues(bdata->comp.widget, XmNforeground, fg, NULL);

    /*
     * The following recursion fixes a bug in Motif 2.1 that caused
     * black colored choice buttons (change has no effect on Motif 1.2).
     */
    XtVaGetValues(bdata->comp.widget,
                  XmNchildren, &children,
                  XmNnumChildren, &numChildren,
                  NULL);
    for (i = 0; i < numChildren; i++) {
        XmChangeColor(children[i], bg);
        XtVaSetValues(children[i], XmNforeground, fg, NULL);
    }


    XmChangeColor(bdata->menu, bg);
    XtVaSetValues(bdata->menu, XmNforeground, fg, NULL);

    for (i = 0; i < bdata->n_items; i++) {
        XmChangeColor(bdata->items[i], bg);
        XtVaSetValues(bdata->items[i], XmNforeground, fg, NULL);
    }
    AWT_FLUSH_UNLOCK();
}

/*
 * Class:     sun_awt_motif_MChoicePeer
 * Method:    setForeground
 * Signature: (Ljava/awt/Color;)V
 */
JNIEXPORT void JNICALL Java_sun_awt_motif_MChoicePeer_setForeground
  (JNIEnv *env, jobject this, jobject c)
{
    struct ChoiceData *bdata;
    Pixel color;
    int32_t i;

    if (JNU_IsNull(env, c)) {
        JNU_ThrowNullPointerException(env, "NullPointerException: null color");
        return;
    }
    AWT_LOCK();

    bdata = (struct ChoiceData *)
      JNU_GetLongFieldAsPtr(env,this,mComponentPeerIDs.pData);
    if (bdata == NULL || bdata->comp.widget == NULL) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        AWT_UNLOCK();
        return;
    }
    color = awtJNI_GetColor(env, c);

    XtVaSetValues(bdata->comp.widget, XmNforeground, color, NULL);

    XtVaSetValues(bdata->menu, XmNforeground, color, NULL);
    for (i = 0; i < bdata->n_items; i++) {
        XtVaSetValues(bdata->items[i], XmNforeground, color, NULL);
    }

    AWT_FLUSH_UNLOCK();
}

/*
 * Class:     sun_awt_motif_MChoicePeer
 * Method:    pReshape
 * Signature: (IIII)V
 */
JNIEXPORT void JNICALL Java_sun_awt_motif_MChoicePeer_pReshape
  (JNIEnv *env, jobject this, jint x, jint y, jint w, jint h)
{
    struct ChoiceData *cdata;
    Widget button;
    jobject target;
    Dimension width=0, height=0;
    Position new_y = 0;

    AWT_LOCK();

    cdata = (struct ChoiceData *)
      JNU_GetLongFieldAsPtr(env,this,mComponentPeerIDs.pData);
    if (cdata == NULL || cdata->comp.widget == NULL) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        AWT_UNLOCK();
        return;
    }

    button = XmOptionButtonGadget(cdata->comp.widget);
    cdata->bounds_y = y;
    cdata->bounds_height = h;
    awt_util_reshape(cdata->comp.widget, x, y, w, h);
    awt_util_reshape(button, x, y, w, h);

    /* Bug 4255631 Solaris: Size returned by Choice.getSize() does not match
     * actual size
     */
    XtVaGetValues(cdata->comp.widget, XmNy, &new_y, NULL);
    XtVaGetValues(button, XmNwidth, &width, XmNheight, &height , NULL);
    awt_util_reshape(cdata->comp.widget, x, new_y, width, height);

    AWT_FLUSH_UNLOCK();
}

/*
 * Class:     sun_awt_motif_MChoicePeer
 * Method:    remove
 * Signature: (I)V
 */
JNIEXPORT void JNICALL Java_sun_awt_motif_MChoicePeer_remove
  (JNIEnv *env, jobject this, jint index)
{
    struct ChoiceData *cdata;
    Widget selected;
    jint i;
    short cols;
    int32_t sheight;
    Dimension height;

    AWT_LOCK();

    cdata = (struct ChoiceData *)
      JNU_GetLongFieldAsPtr(env,this,mComponentPeerIDs.pData);
    if (cdata == NULL || cdata->comp.widget == NULL) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        AWT_UNLOCK();
        return;
    }
    if (index < 0 || index > cdata->n_items) {
        JNU_ThrowIllegalArgumentException(env, "IllegalArgumentException");
        AWT_UNLOCK();
        return;
    }
    XtUnmanageChild(cdata->items[index]);
    awt_util_consumeAllXEvents(cdata->items[index]);
    awt_util_cleanupBeforeDestroyWidget(cdata->items[index]);
    XtDestroyWidget(cdata->items[index]);
    for (i = index; i < cdata->n_items-1; i++) {
        cdata->items[i] = cdata->items[i + 1];
        /* need to reset stored index value, (adding 1 to disambiguate it */
        /* from an arg list terminator)                                   */
        /* bug fix 4079027 robi.khan@eng                                  */
        XtVaSetValues(cdata->items[i],  XmNuserData, (XtPointer)((intptr_t)(i+1)), NULL);
    }
    cdata->items[cdata->n_items-1] = NULL;
    cdata->n_items--;

    XtVaGetValues(cdata->menu, XmNheight, &height, NULL);

    sheight = DisplayHeight(awt_display, DefaultScreen(awt_display));
    cols = cdata->n_columns;

    if (cols >1) {
        /* first try to remove a column */
        cols = --cdata->n_columns;
        XtVaSetValues(cdata->menu, XmNnumColumns, cols, NULL);

        /* then see if it fits, if not add it back */
        XtVaGetValues(cdata->menu, XmNheight, &height, NULL);
        if ( height > sheight ) {
            cols = ++cdata->n_columns;
            XtVaSetValues(cdata->menu, XmNnumColumns, cols, NULL);
        }
    }

    AWT_UNLOCK();
}

/*
 * Class:     sun_awt_motif_MChoicePeer
 * Method:    removeAll
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_sun_awt_motif_MChoicePeer_removeAll
  (JNIEnv *env, jobject this)
{
    struct ChoiceData *cdata;
    Widget selected;
    jint i;

    AWT_LOCK();

    cdata = (struct ChoiceData *)
      JNU_GetLongFieldAsPtr(env,this,mComponentPeerIDs.pData);
    if (cdata == NULL || cdata->comp.widget == NULL) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        AWT_UNLOCK();
        return;
    }

    XtUnmanageChildren(cdata->items, cdata->n_items);

    for (i = cdata->n_items-1; i >= 0; i--) {
        awt_util_consumeAllXEvents(cdata->items[i]);
        awt_util_cleanupBeforeDestroyWidget(cdata->items[i]);
        XtDestroyWidget(cdata->items[i]);
        cdata->items[i] = NULL;
    }

    cdata->n_items = 0;

    if (cdata->n_columns > 1) {
        cdata->n_columns = 1;
        XtVaSetValues(cdata->menu, XmNnumColumns, cdata->n_columns, NULL);
    }

    AWT_UNLOCK();
}

/*
 * Class:     sun_awt_motif_MChoicePeer
 * Method:    appendItems
 * Signature: ([Ljava/lang/String;)V
 */
JNIEXPORT void JNICALL Java_sun_awt_motif_MChoicePeer_appendItems
  (JNIEnv *env, jobject this, jarray items)
{
    struct ChoiceData *odata = NULL;
    jstring *strItems = NULL;
    jsize nItems, i; // MP

    if (JNU_IsNull(env, items)) {
        return;
    }
    nItems  = (*env)->GetArrayLength(env, items);
    if (nItems == 0) {
        return;
    }

    AWT_LOCK();

    odata = (struct ChoiceData *)
      JNU_GetLongFieldAsPtr(env,this,mComponentPeerIDs.pData);

    if (odata == NULL) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        goto cleanup;
    }

    strItems = (jstring *) malloc(sizeof(jstring) * nItems);
    if (strItems == NULL) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        goto cleanup;
    }

    for (i = 0; i < nItems; i++) {
        strItems[i] = (jstring)(*env)->GetObjectArrayElement(env, items, i);
        if (JNU_IsNull(env, strItems[i])) {
            JNU_ThrowNullPointerException(env, "NullPointerException");
            goto cleanup;
        }
    }

    addItems(env, this, strItems, nItems, odata->n_items);

cleanup:
    if (strItems != NULL) {
        free(strItems);
    }
    AWT_UNLOCK();
}
