/*
 * Copyright 2001-2003 Sun Microsystems, Inc.  All Rights Reserved.
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

#if MOTIF_VERSION!=2
    #error This file should only be compiled with motif 2.1
#endif

#include "awt_p.h"
#include "java_awt_Component.h"
#include "java_awt_AWTEvent.h"
#include "sun_awt_motif_MComponentPeer.h"
#include "sun_awt_motif_MChoicePeer.h"

#include "awt_Component.h"
#include "canvas.h"

#include "multi_font.h"

#include <jni.h>
#include <jni_util.h>
#include <Xm/ComboBox.h>

#define MAX_VISIBLE 10

extern struct ComponentIDs componentIDs;
extern struct ContainerIDs containerIDs;
extern struct MComponentPeerIDs mComponentPeerIDs;

extern AwtGraphicsConfigDataPtr
    copyGraphicsConfigToPeer(JNIEnv *env, jobject this);

/*
   setSelection
   Set the selected text on the XmTextField of the XmComboBox.
*/
static void
setSelection(JNIEnv *env,
              jobject this,
              Widget comboBox,
              jint index)
{
    jstring item = NULL;
    jobject target;
    Widget text=NULL;

    AWT_LOCK();
    /* Get the java Choice component. */
    target = (*env)->GetObjectField(env, this, mComponentPeerIDs.target);
    if (target == NULL) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        AWT_UNLOCK();
        return;
    }
    /* Get the XmTextField widget in the XmComboBox. */
    text = XtNameToWidget(comboBox, "*Text");
    /* Get the selected Unicode string from the java Choice component. */
    item = (jstring) JNU_CallMethodByName(env, NULL,
        target, "getItem", "(I)Ljava/lang/String;", index).l;
    if ((*env)->ExceptionOccurred(env)) {
        (*env)->ExceptionDescribe(env);
        (*env)->ExceptionClear(env);
    }
    if (!JNU_IsNull(env, item)) {
        /* Convert the Unicode string to a multibyte string. */
        char *temp = (char *)JNU_GetStringPlatformChars(env, item, NULL);
        /* Assign the multibyte string to the XmTextField of the XmComboBox. */
        XmTextSetString(text, temp);
        JNU_ReleaseStringPlatformChars(env, item, (const char *)temp);
    }
    AWT_UNLOCK();
}

extern Boolean skipNextNotifyWhileGrabbed;
extern Boolean skipNextFocusIn;

static void
GrabShellPopup(Widget grab_shell,
                jobject this,
                XmAnyCallbackStruct * call_data)
{
    skipNextNotifyWhileGrabbed = True;
}
static void
GrabShellPopdown(Widget grab_shell,
                jobject this,
                XmAnyCallbackStruct * call_data)
{
    skipNextNotifyWhileGrabbed = True;
    skipNextFocusIn = True;
}

static void
Choice_callback(Widget list,
                jobject this,
                XmAnyCallbackStruct * call_data)
{
    JNIEnv *env = (JNIEnv *)JNU_GetEnv(jvm, JNI_VERSION_1_2);
    XmListCallbackStruct *cbs = (XmListCallbackStruct *)call_data;
    struct ChoiceData *cdata;


    AWT_LOCK();
    /* Get the Choice data. */
    cdata = (struct ChoiceData *)
      JNU_GetLongFieldAsPtr(env,this,mComponentPeerIDs.pData);
    if (cdata == NULL) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        AWT_UNLOCK();
        return;
    }
    setSelection(env, this, cdata->comp.widget, cbs->item_position - 1);
    /* Get the Choice data. */
    JNU_CallMethodByName(env, NULL,
        this, "action", "(I)V", cbs->item_position - 1);
    if ((*env)->ExceptionOccurred(env)) {
        (*env)->ExceptionDescribe(env);
        (*env)->ExceptionClear(env);
    }
    AWT_UNLOCK();
}

static void
addItems(JNIEnv *env, jobject this,
    jstring *items, int32_t nItems, jint index)
{
    struct ChoiceData *cdata;
    int32_t i;
    Widget list;
    XmString mfstr = NULL;
    XmFontList fontlist = NULL;
    jobject font = awtJNI_GetFont(env, this);
    Boolean IsMultiFont = awtJNI_IsMultiFont(env, font);

    if ((items == NULL) || (nItems == 0)) {
        return;
    }

    AWT_LOCK();

    cdata = (struct ChoiceData *)
      JNU_GetLongFieldAsPtr(env,this,mComponentPeerIDs.pData);

    if (cdata == NULL) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        AWT_UNLOCK();
        return;
    }

    for (i = 0; i < nItems; ++i) {
        char *temp = (char *)JNU_GetStringPlatformChars(env, items[i], NULL);
        mfstr = XmStringCreateLocalized(temp);
        JNU_ReleaseStringPlatformChars(env, items[i], (const char *)temp);
        XmComboBoxAddItem(cdata->comp.widget, mfstr, index + i + 1, FALSE);

        if (mfstr != NULL) {
            XmStringFree(mfstr);
            mfstr = NULL;
        }
    }

    cdata->n_items += nItems;

    list = XtNameToWidget(cdata->comp.widget, "*List");
    XtVaSetValues(list,
                  XmNvisibleItemCount, min(MAX_VISIBLE, cdata->n_items),
                  NULL);
    AWT_UNLOCK();
}

/*
 * Class:     sun_awt_motif_MChoicePeer
 * Method:    create
 * Signature: (Lsun/awt/motif/MComponentPeer;)V
 */
JNIEXPORT void JNICALL
Java_sun_awt_motif_MChoicePeer_create(JNIEnv * env, jobject this,
    jobject parent)
{
    jobject globalRef = awtJNI_CreateAndSetGlobalRef(env, this);

    struct ComponentData *wdata; /* parent's peer data */
    struct ChoiceData *cdata;    /* our own peer data */
    Widget list, text, list_shell;               /* components of drop dowwn list widget */

    AwtGraphicsConfigDataPtr adata;
    Pixel fg, bg;                /* colors inherited from parent */
    Dimension width = 0, height = 0;
    jclass clsDimension;
    jobject dimension;

#undef MAX_ARGC
#define MAX_ARGC 30
    Arg args[MAX_ARGC];
    int32_t argc;

    AWT_LOCK();

    if (JNU_IsNull(env, parent)) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        return;
    }

    /* get parent's peer data */
    wdata = (struct ComponentData *)JNU_GetLongFieldAsPtr(env,
                parent, mComponentPeerIDs.pData);
    if (wdata == NULL) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        AWT_UNLOCK();
        return;
    }

    /* create our own peer data */
    cdata = ZALLOC(ChoiceData);
    if (cdata == NULL) {
        JNU_ThrowOutOfMemoryError(env, "OutOfMemoryError");
        AWT_UNLOCK();
        return;
    }
    JNU_SetLongFieldFromPtr(env, this, mComponentPeerIDs.pData, cdata);

    /* get desired size */
    clsDimension = (*env)->FindClass(env, "java/awt/Dimension");
    DASSERT(clsDimension != NULL);

    dimension = JNU_CallMethodByName(env, NULL,
                    this, "getPreferredSize", "()Ljava/awt/Dimension;").l;
    width  = (Dimension)((*env)->GetIntField(env, dimension,
                             (*env)->GetFieldID(env, clsDimension,
                                 "width" , "I")));
    height = (Dimension)((*env)->GetIntField(env, dimension,
                             (*env)->GetFieldID(env, clsDimension,
                                 "height", "I")));

    /* Inherit visual/colors from parent component */
    XtVaGetValues(wdata->widget, XmNbackground, &bg, NULL);
    XtVaGetValues(wdata->widget, XmNforeground, &fg, NULL);
    adata = copyGraphicsConfigToPeer(env, this);

    argc = 0;
    XtSetArg(args[argc], XmNuserData, (XtPointer)globalRef);            ++argc;
    XtSetArg(args[argc], XmNx, 0);                                      ++argc;
    XtSetArg(args[argc], XmNy, 0);                                      ++argc;
    XtSetArg(args[argc], XmNmarginHeight, 2);                           ++argc;
    XtSetArg(args[argc], XmNmarginWidth, 1);                            ++argc;
    XtSetArg(args[argc], XmNvisibleItemCount, 0);                       ++argc;
    XtSetArg(args[argc], XmNancestorSensitive, True);                   ++argc;
    /* Don't ding on key press */
    XtSetArg(args[argc], XmNverifyBell, False);                         ++argc;
    XtSetArg(args[argc], XmNvisual, adata->awt_visInfo.visual);         ++argc;
    XtSetArg(args[argc], XmNscreen,
             ScreenOfDisplay(awt_display, adata->awt_visInfo.screen));  ++argc;
    XtSetArg(args[argc], XmNbackground, bg);                            ++argc;
    XtSetArg(args[argc], XmNforeground, fg);                            ++argc;

    DASSERT(!(argc > MAX_ARGC));
    cdata->comp.widget = XmCreateDropDownList(wdata->widget,
                                              "combobox", args, argc);
    cdata->n_items = 0;

    list = XtNameToWidget(cdata->comp.widget, "*List");
    text = XtNameToWidget(cdata->comp.widget, "*Text");
    list_shell = XtNameToWidget(cdata->comp.widget, "*GrabShell");
    XtAddCallback(list_shell,
                  XmNpopupCallback,
                  (XtCallbackProc)GrabShellPopup,
                  globalRef);
    XtAddCallback(list_shell,
                  XmNpopdownCallback,
                  (XtCallbackProc)GrabShellPopdown,
                  globalRef);

    /*
     * Bug 4477410:  Setting the width of the XmComboBox made the XmTextField
     * too small, cutting off the dropdown list knob on the right side. Set
     * the width of the TextField because it is the widget actually seen.
    */
    /* Set the width and height of the TextField widget. */
    XtVaSetValues(text,
                  XmNwidth, width,
                  XmNheight, height,
                  NULL);

    XtAddCallback(list,
                  XmNbrowseSelectionCallback,
                  (XtCallbackProc)Choice_callback,
                  (XtPointer)globalRef);

    XtAddEventHandler(text, FocusChangeMask, True,
                      awt_canvas_event_handler, globalRef);

    awt_addWidget(text, cdata->comp.widget, globalRef,
                  java_awt_AWTEvent_KEY_EVENT_MASK
                  | java_awt_AWTEvent_MOUSE_EVENT_MASK
                  | java_awt_AWTEvent_MOUSE_MOTION_EVENT_MASK);

    XtSetMappedWhenManaged(cdata->comp.widget, False);
    XtManageChild(cdata->comp.widget);

    AWT_UNLOCK();
}

/*
 * Class:     sun_awt_motif_MChoicePeer
 * Method:    pSelect
 * Signature: (I)V
 */
JNIEXPORT void JNICALL
Java_sun_awt_motif_MChoicePeer_pSelect(JNIEnv *env, jobject this,
    jint index, jboolean init)
{
    struct ChoiceData *cdata;
    Widget list;

    AWT_LOCK();

    cdata = (struct ChoiceData *)JNU_GetLongFieldAsPtr(env,
                this, mComponentPeerIDs.pData);
    if (cdata == NULL) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        AWT_UNLOCK();
        return;
    }

    list = XtNameToWidget(cdata->comp.widget, "*List");

    XmListDeselectAllItems(list);
    XmListSelectPos(list, index + 1, False);
    setSelection(env, this, cdata->comp.widget, index);
    XmComboBoxUpdate(cdata->comp.widget);

    AWT_UNLOCK();
}

/*
 * Class:     sun_awt_motif_MChoicePeer
 * Method:    setFont
 * Signature: (Ljava/awt/Font;)V
 */
JNIEXPORT void JNICALL
Java_sun_awt_motif_MChoicePeer_setFont(JNIEnv *env, jobject this,
    jobject f)
{
    struct ChoiceData *cdata;
    struct FontData *fdata;
    XmFontList fontlist;
    Widget list;
    Widget text;
    char *err;
    XmFontListEntry fontentry;
    Position x=0, y=0;

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

    cdata = (struct ChoiceData *)JNU_GetLongFieldAsPtr(env,
                this, mComponentPeerIDs.pData);
    if (cdata == NULL || cdata->comp.widget == NULL) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        AWT_UNLOCK();
        return;
    }

    /* Make a fontset and set it. */
    if (awtJNI_IsMultiFont(env, f)) {
        if (fdata->xfs == NULL) {
            fdata->xfs = awtJNI_MakeFontSet(env, f);
        }
        if (fdata->xfs != NULL) {
            fontentry = XmFontListEntryCreate("labelFont",
                                              XmFONT_IS_FONTSET,
                                              (XtPointer) (fdata->xfs));
            fontlist = XmFontListAppendEntry(NULL, fontentry);
            /*
             * Some versions of motif have a bug in
             * XmFontListEntryFree() which causes it to free more than it
             * should.  Use XtFree() instead.  See O'Reilly's
             * Motif Reference Manual for more information.
             */
            XmFontListEntryFree(&fontentry);
        } else {
            fontlist = XmFontListCreate(fdata->xfont, "labelFont");
        }
    } else {
        fontlist = XmFontListCreate(fdata->xfont, "labelFont");
    }
    XtVaSetValues(cdata->comp.widget,
                  XmNfontList, fontlist,
                  NULL);
    list = XtNameToWidget(cdata->comp.widget, "*List");
    XtVaSetValues(list,
                  XmNfontList, fontlist,
                  NULL);

    text = XtNameToWidget(cdata->comp.widget, "*Text");
    XtVaSetValues(text,
                  XmNfontList, fontlist,
                  NULL);
    XmFontListFree(fontlist);
    XtVaGetValues(cdata->comp.widget,
                  XmNx, &x,
                  XmNy, &y,
                  NULL);
    Java_sun_awt_motif_MChoicePeer_pReshape(env, this, x, y, 0, 0);
    AWT_UNLOCK();
}


/*
 * Class:     sun_awt_motif_MChoicePeer
 * Method:    freeNativeData
 * Signature: ()V
 */
JNIEXPORT void JNICALL
Java_sun_awt_motif_MChoicePeer_freeNativeData(JNIEnv *env, jobject this)
{
    /*
     * Fix for bug 4326619 - not necessary for Motif 2.1
     */
}


/*
 * Class:     sun_awt_motif_MChoicePeer
 * Method:    setBackground
 * Signature: (Ljava/awt/Color;)V
 */
JNIEXPORT void JNICALL
Java_sun_awt_motif_MChoicePeer_setBackground(JNIEnv *env, jobject this,
    jobject c)
{
    struct ChoiceData *cdata;
    Pixel bg;
    Pixel fg;
    int32_t i;

    if (JNU_IsNull(env, c)) {
        JNU_ThrowNullPointerException(env, "NullPointerException: null color");
        return;
    }
    AWT_LOCK();

    cdata = (struct ChoiceData *)
        JNU_GetLongFieldAsPtr(env,this,mComponentPeerIDs.pData);
    if (cdata == NULL || cdata->comp.widget == NULL) {
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
    XtVaGetValues(cdata->comp.widget, XmNforeground, &fg, NULL);

    /* Set color */
    XmChangeColor(cdata->comp.widget, bg);
    XtVaSetValues(cdata->comp.widget, XmNforeground, fg, NULL);

    AWT_FLUSH_UNLOCK();
}

/*
 * Class:     sun_awt_motif_MChoicePeer
 * Method:    setForeground
 * Signature: (Ljava/awt/Color;)V
 */
JNIEXPORT void JNICALL
Java_sun_awt_motif_MChoicePeer_setForeground(JNIEnv *env, jobject this,
    jobject c)
{
    struct ChoiceData *cdata;
    Pixel color;
    int32_t i;

    if (JNU_IsNull(env, c)) {
        JNU_ThrowNullPointerException(env, "NullPointerException: null color");
        return;
    }
    AWT_LOCK();

    cdata = (struct ChoiceData *)
      JNU_GetLongFieldAsPtr(env,this,mComponentPeerIDs.pData);
    if (cdata == NULL || cdata->comp.widget == NULL) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        AWT_UNLOCK();
        return;
    }
    color = awtJNI_GetColor(env, c);

    XtVaSetValues(cdata->comp.widget, XmNforeground, color, NULL);

    AWT_FLUSH_UNLOCK();
}

/*
 * Class:     sun_awt_motif_MChoicePeer
 * Method:    pReshape
 * Signature: (IIII)V
 */
JNIEXPORT void JNICALL
Java_sun_awt_motif_MChoicePeer_pReshape(JNIEnv *env, jobject this,
    jint x, jint y, jint w, jint h)
{
    struct ChoiceData *cdata;
    Widget list;
    Dimension width = 0, height = 0;
    jclass clsDimension;
    jobject dimension;
    jobject target;
    Widget text=NULL;

    AWT_LOCK();

    cdata = (struct ChoiceData *)
      JNU_GetLongFieldAsPtr(env,this,mComponentPeerIDs.pData);
    if (cdata == NULL || cdata->comp.widget == NULL) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        AWT_UNLOCK();
        return;
    }

    if (w == 0) {
        /* Set the width and height of the TextField widget to the
         * PreferredSize, based on the font size.
        */
        clsDimension = (*env)->FindClass(env, "java/awt/Dimension");
        DASSERT(clsDimension != NULL);
        dimension = JNU_CallMethodByName(env, NULL,
                        this, "getPreferredSize", "()Ljava/awt/Dimension;").l;
        width  = (Dimension)((*env)->GetIntField(env, dimension,
                                 (*env)->GetFieldID(env, clsDimension,
                                     "width" , "I")));
        height = (Dimension)((*env)->GetIntField(env, dimension,
                                 (*env)->GetFieldID(env, clsDimension,
                                     "height", "I")));
    } else {
        /* Set the width and height of the TextField widget to the
         * given values. BorderLayout passes these values, for example.
        */
        width = w;
        height = h;
    }
    text = XtNameToWidget(cdata->comp.widget, "*Text");
    /*
     * Bug 4477410:  Setting the width of the XmComboBox made the XmTextField
     * too small, cutting off the dropdown list knob on the right side. Set
     * the width of the TextField because it is the widget actually seen.
    */
    XtVaSetValues(text,
                  XmNwidth, width,
                  XmNheight, height,
                  NULL);

    awt_util_reshape(cdata->comp.widget, x, y, width, height);

    list = XtNameToWidget(cdata->comp.widget, "*List");

    XtVaSetValues(list, XmNwidth, width, NULL);

    /* Set the width and height of the Choice component. */
    target = (*env)->GetObjectField(env, this, mComponentPeerIDs.target);
    if (target == NULL) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        AWT_UNLOCK();
        return;
    }
    (*env)->SetIntField(env, target, componentIDs.width, (jint)width);
    (*env)->SetIntField(env, target, componentIDs.height, (jint)height);

    AWT_FLUSH_UNLOCK();
}


/*
 * Class:     sun_awt_motif_MChoicePeer
 * Method:    addItem
 * Signature: (Ljava/lang/String;I)V
 */
JNIEXPORT void JNICALL
Java_sun_awt_motif_MChoicePeer_addItem(JNIEnv *env, jobject this,
    jstring item, jint index)
{
    if (JNU_IsNull(env, item)) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        return;
    }
    addItems(env, this, &item, 1, index);
}


/*
 * Class:     sun_awt_motif_MChoicePeer
 * Method:    appendItems
 * Signature: ([Ljava/lang/String;)V
 */
JNIEXPORT void JNICALL
Java_sun_awt_motif_MChoicePeer_appendItems(JNIEnv *env, jobject this,
    jarray items)
{
    struct ChoiceData *cdata = NULL;
    jstring *strItems = NULL;
    int32_t nItems, i;

    if (JNU_IsNull(env, items)) {
        return;
    }
    nItems  = (*env)->GetArrayLength(env, items);
    if (nItems == 0) {
        return;
    }

    AWT_LOCK();

    cdata = (struct ChoiceData *)JNU_GetLongFieldAsPtr(env,
                this, mComponentPeerIDs.pData);
    if (cdata == NULL) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        goto cleanup;
    }

    strItems = (jstring *)malloc(sizeof(jstring) * nItems);
    if (strItems == NULL) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        goto cleanup;
    }

    for (i = 0; i < nItems; ++i) {
        strItems[i] = (jstring)(*env)->GetObjectArrayElement(env,
                                   items, (jsize)i);
        if (JNU_IsNull(env, strItems[i])) {
            JNU_ThrowNullPointerException(env, "NullPointerException");
            goto cleanup;
        }
    }

    addItems(env, this, strItems, nItems, (jint)cdata->n_items);

  cleanup:
    if (strItems != NULL) {
        free(strItems);
    }
    AWT_UNLOCK();
}


/*
 * Class:     sun_awt_motif_MChoicePeer
 * Method:    remove
 * Signature: (I)V
 */
JNIEXPORT void JNICALL
Java_sun_awt_motif_MChoicePeer_remove(JNIEnv *env, jobject this,
    jint index)
{
    struct ChoiceData *cdata;
    Widget list;
    Widget text=NULL;

    AWT_LOCK();

    cdata = (struct ChoiceData *)
      JNU_GetLongFieldAsPtr(env,this,mComponentPeerIDs.pData);
    if (cdata == NULL || cdata->comp.widget == NULL) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        AWT_UNLOCK();
        return;
    }

    XmComboBoxDeletePos(cdata->comp.widget, index + 1);
    --(cdata->n_items);

    list = XtNameToWidget(cdata->comp.widget, "*List");
    XtVaSetValues(list, XmNvisibleItemCount, min(MAX_VISIBLE, cdata->n_items), NULL);

    if (cdata->n_items == 0) {
        /* No item is selected, so clear the TextField. */
        text = XtNameToWidget(cdata->comp.widget, "*Text");
        XtVaSetValues(text, XmNvalue, "", NULL);
    }

    AWT_UNLOCK();
}

/*
 * Class:     sun_awt_motif_MChoicePeer
 * Method:    removeAll
 * Signature: ()V
 */
JNIEXPORT void JNICALL
Java_sun_awt_motif_MChoicePeer_removeAll(JNIEnv *env, jobject this)
{
    struct ChoiceData *cdata;
    int32_t i;
    Widget text=NULL;
    Widget list;

    AWT_LOCK();

    cdata = (struct ChoiceData *)
      JNU_GetLongFieldAsPtr(env,this,mComponentPeerIDs.pData);
    if (cdata == NULL || cdata->comp.widget == NULL) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        AWT_UNLOCK();
        return;
    }

    for (i = cdata->n_items - 1; i >= 0; --i) {
        XmComboBoxDeletePos(cdata->comp.widget, i);
    }
    cdata->n_items = 0;

    /* No item is selected, so clear the TextField. */
    text = XtNameToWidget(cdata->comp.widget, "*Text");
    XtVaSetValues(text, XmNvalue, "", NULL);

    /* should set XmNvisibleItemCount to 1 as 0 is invalid value */
    list = XtNameToWidget(cdata->comp.widget, "*List");
    XtVaSetValues(list, XmNvisibleItemCount, 1, NULL);

    AWT_UNLOCK();
}
