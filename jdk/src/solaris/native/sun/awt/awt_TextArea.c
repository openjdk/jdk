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

#ifdef HEADLESS
    #error This file should not be included in headless library
#endif

#include "awt_p.h"
#include "canvas.h"
#include "java_awt_TextArea.h"
#include "java_awt_Cursor.h"
#include "java_awt_Component.h"
#include "java_awt_Color.h"
#include "java_awt_AWTEvent.h"
#include "java_awt_Font.h"
#include "java_awt_event_MouseWheelEvent.h"
#include "sun_awt_motif_MTextAreaPeer.h"
#include "sun_awt_motif_MComponentPeer.h"

#include "awt_Component.h"
#include "awt_Cursor.h"
#include "awt_TextArea.h"

#include <jni.h>
#include <jni_util.h>
#include "multi_font.h"

extern struct MComponentPeerIDs mComponentPeerIDs;
extern struct CursorIDs cursorIDs;
extern AwtGraphicsConfigDataPtr
    copyGraphicsConfigToPeer(JNIEnv *env, jobject this);
struct TextAreaIDs textAreaIDs;
struct MTextAreaPeerIDs mTextAreaPeerIDs;

/*
 * Class:     java_awt_TextArea
 * Method:    initIDs
 * Signature: ()V
 */

/* This function gets called from the static initializer for TextArea.java
   to initialize the fieldIDs for fields that may be accessed from C */

JNIEXPORT void JNICALL
Java_java_awt_TextArea_initIDs
  (JNIEnv *env, jclass cls)
{
    textAreaIDs.scrollbarVisibility =
      (*env)->GetFieldID(env, cls, "scrollbarVisibility", "I");
}

/*
 * Class:     sun_awt_motif_MTextAreaPeer
 * Method:    initIDs
 * Signature: ()V
 */

/* This function gets called from the static initializer for
   MTextAreaPeer.java to initialize the fieldIDs for fields that may
   be accessed from C */
JNIEXPORT void JNICALL
Java_sun_awt_motif_MTextAreaPeer_initIDs
  (JNIEnv *env, jclass cls)
{
    mTextAreaPeerIDs.firstChangeSkipped =
      (*env)->GetFieldID(env, cls, "firstChangeSkipped", "Z");
}

/*
 * client_data is MTextAreaPeer instance
 */
void
TextArea_valueChanged(Widget w, XtPointer client_data, XtPointer call_data)
{
    JNIEnv *env = (JNIEnv *)JNU_GetEnv(jvm, JNI_VERSION_1_2);
    jboolean skipped;

    skipped = (*env)->GetBooleanField(env, (jobject) client_data,
                                      mTextAreaPeerIDs.firstChangeSkipped);
    if (!(*env)->ExceptionOccurred(env)) {
        if (skipped == JNI_FALSE) {
            (*env)->SetBooleanField(env, (jobject) client_data,
                                    mTextAreaPeerIDs.firstChangeSkipped,
                                    JNI_TRUE);
        } else {
            JNU_CallMethodByName(env, NULL, (jobject) client_data,
                                 "valueChanged", "()V");
        }
    }

    if ((*env)->ExceptionOccurred(env)) {
        (*env)->ExceptionDescribe(env);
        (*env)->ExceptionClear(env);
    }
}

extern void Text_handlePaste(Widget w, XtPointer client_data, XEvent * event,
                             Boolean * cont);

/*
 * Class:     sun_awt_motif_MTextAreaPeer
 * Method:    pCreate
 * Signature: (Lsun/awt/motif/MComponentPeer;)V
 */
JNIEXPORT void JNICALL Java_sun_awt_motif_MTextAreaPeer_pCreate
  (JNIEnv *env, jobject this, jobject parent)
{
    struct TextAreaData *tdata;
#define MAX_ARGC 30
    Arg args[MAX_ARGC];
    int32_t argc;
    struct ComponentData *wdata;
    jobject target;
    Pixel bg;
    int32_t sbVisibility;
    Boolean wordWrap = False, hsb = False, vsb = False;
    jobject globalRef = awtJNI_CreateAndSetGlobalRef(env, this);
    AwtGraphicsConfigDataPtr adata;
    char *nonEmptyText = "* will never be shown *";

    AWT_LOCK();

    adata = copyGraphicsConfigToPeer(env, this);

    if (JNU_IsNull(env, parent)) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        AWT_UNLOCK();
        return;
    }
    wdata = (struct ComponentData *)
        JNU_GetLongFieldAsPtr(env,parent,mComponentPeerIDs.pData);
    if (wdata == NULL) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        AWT_UNLOCK();
        return;
    }
    target = (*env)->GetObjectField(env, this, mComponentPeerIDs.target);

    tdata = ZALLOC(TextAreaData);
    JNU_SetLongFieldFromPtr(env,this,mComponentPeerIDs.pData,tdata);

    if (tdata == NULL) {
        JNU_ThrowOutOfMemoryError(env, "OutOfMemoryError");
        AWT_UNLOCK();
        return;
    }
    XtVaGetValues(wdata->widget, XmNbackground, &bg, NULL);

    sbVisibility = (*env)->GetIntField(env, target,
                                       textAreaIDs.scrollbarVisibility);
    switch (sbVisibility) {
        case java_awt_TextArea_SCROLLBARS_NONE:
            wordWrap = True;
            hsb = False;
            vsb = False;
            break;

        case java_awt_TextArea_SCROLLBARS_VERTICAL_ONLY:
            wordWrap = True;
            hsb = False;
            vsb = True;
            break;

        case java_awt_TextArea_SCROLLBARS_HORIZONTAL_ONLY:
            wordWrap = False;
            hsb = True;
            vsb = False;
            break;

        default:
        case java_awt_TextArea_SCROLLBARS_BOTH:
            wordWrap = False;
            hsb = True;
            vsb = True;
            break;
    }

    argc = 0;
    XtSetArg(args[argc], XmNrecomputeSize, False);
    argc++;
    XtSetArg(args[argc], XmNx, 0);
    argc++;
    XtSetArg(args[argc], XmNy, 0);
    argc++;
    XtSetArg(args[argc], XmNbackground, bg);
    argc++;
    XtSetArg(args[argc], XmNeditMode, XmMULTI_LINE_EDIT);
    argc++;
    XtSetArg(args[argc], XmNwordWrap, wordWrap);
    argc++;
    XtSetArg(args[argc], XmNscrollHorizontal, hsb);
    argc++;
    XtSetArg(args[argc], XmNscrollVertical, vsb);
    argc++;
    XtSetArg(args[argc], XmNmarginHeight, 2);
    argc++;
    XtSetArg(args[argc], XmNmarginWidth, 2);
    argc++;
    XtSetArg(args[argc], XmNuserData, (XtPointer) globalRef);
    argc++;
    XtSetArg (args[argc], XmNscreen,
              ScreenOfDisplay(awt_display,
                              adata->awt_visInfo.screen));
    argc++;
    XtSetArg(args[argc], XmNfontList, getMotifFontList());
    argc++;

    /* Initialize with a non-empty text, so the
     * TextArea_valueChanged callback will be called
     * even if the following conditions are true:
     * 1. TextArea constructed with an empty initial text.
     * 2. setText() with an empty argument is called
     *    immediately after the TextArea component is created.
     * For more details please see #4028580.
     */
    XtSetArg(args[argc], XmNvalue, nonEmptyText);
    argc++;

    DASSERT(!(argc > MAX_ARGC));
    tdata->txt = XmCreateScrolledText(wdata->widget, "textA",
                                      args, argc);
    tdata->comp.widget = XtParent(tdata->txt);

    /* Bug 4208972. Give the ScrolledWindow a minimum size. */
    XtVaSetValues(tdata->comp.widget,
        XmNwidth,  1,
        XmNheight, 1, NULL);

    XtSetMappedWhenManaged(tdata->comp.widget, False);
    XtManageChild(tdata->txt);
    XtManageChild(tdata->comp.widget);

    XtAddCallback(tdata->txt,
                  XmNvalueChangedCallback,
                  TextArea_valueChanged,
                  (XtPointer) globalRef);

    XtAddEventHandler(tdata->txt, FocusChangeMask,
                      True, awt_canvas_event_handler, globalRef);

    XtInsertEventHandler(tdata->txt,
                         KeyPressMask,
                         False, Text_handlePaste, (XtPointer) globalRef,
                         XtListHead);

    awt_addWidget(tdata->txt, tdata->comp.widget, globalRef,
                  java_awt_AWTEvent_KEY_EVENT_MASK |
                  java_awt_AWTEvent_MOUSE_EVENT_MASK |
                  java_awt_AWTEvent_MOUSE_MOTION_EVENT_MASK);
    /*
     * Fix for BugTraq ID 4349615.
     * Unregister Motif drop site to prevent it from crash
     * when dropping java objects.
     */
    XmDropSiteUnregister(tdata->txt);

    AWT_UNLOCK();
}

/*
 * Class:     sun_awt_motif_MTextAreaPeer
 * Method:    getExtraWidth
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_sun_awt_motif_MTextAreaPeer_getExtraWidth
  (JNIEnv *env, jobject this)
{
    struct TextAreaData *tdata;
    Dimension spacing, shadowThickness, textMarginWidth, sbWidth;
    Widget verticalScrollBar;

    AWT_LOCK();

    tdata = (struct TextAreaData *) JNU_GetLongFieldAsPtr(env,this,mComponentPeerIDs.pData);

    if (tdata == NULL || tdata->txt == NULL) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        AWT_UNLOCK();
        return 0;
    }
    XtVaGetValues(tdata->txt, XmNmarginWidth, &textMarginWidth, NULL);
    XtVaGetValues(tdata->comp.widget,
                  XmNspacing, &spacing,
                  XmNverticalScrollBar, &verticalScrollBar,
                  NULL);
    if (verticalScrollBar != NULL) {
        /* Assumption:  shadowThickness same for scrollbars and text area */
        XtVaGetValues(verticalScrollBar,
                      XmNwidth, &sbWidth,
                      XmNshadowThickness, &shadowThickness,
                      NULL);
    } else {
        sbWidth = 0;
        shadowThickness = 0;
    }

    AWT_UNLOCK();

    return (jint) (sbWidth + spacing + 2 * textMarginWidth + 4 * shadowThickness);
}

/*
 * Class:     sun_awt_motif_MTextAreaPeer
 * Method:    getExtraHeight
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_sun_awt_motif_MTextAreaPeer_getExtraHeight
  (JNIEnv *env, jobject this)
{
    struct TextAreaData *tdata;
    Dimension spacing, shadowThickness, textMarginHeight, sbHeight;
    Dimension sbShadowThickness, highlightThickness, sbHighlightThickness;
    int32_t height;
    Widget horizontalScrollBar;

    AWT_LOCK();

    tdata = (struct TextAreaData *) JNU_GetLongFieldAsPtr(env,this,mComponentPeerIDs.pData);

    if (tdata == NULL || tdata->txt == NULL) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        AWT_UNLOCK();
        return 0;
    }

    XtVaGetValues(tdata->txt, XmNmarginHeight, &textMarginHeight,
                              XmNshadowThickness, &shadowThickness,
                              XmNhighlightThickness, &highlightThickness, NULL);
    height = 2 * (textMarginHeight + shadowThickness + highlightThickness);

    XtVaGetValues(tdata->comp.widget,
                  XmNspacing, &spacing,
                  XmNhorizontalScrollBar, &horizontalScrollBar,
                  NULL);

    if (horizontalScrollBar != NULL) {
        XtVaGetValues(horizontalScrollBar,
                      XmNshadowThickness, &sbShadowThickness,
                      XmNhighlightThickness, &sbHighlightThickness,
                      XmNheight, &sbHeight,
                      NULL);
        height += sbHeight + spacing
                + 2 * (sbShadowThickness + sbHighlightThickness);
    }

    AWT_UNLOCK();

    return (jint)height;
}

/*
 * Class:     sun_awt_motif_MTextAreaPeer
 * Method:    setTextBackground
 * Signature: (Ljava/awt/Color;)V
 */
JNIEXPORT void JNICALL Java_sun_awt_motif_MTextAreaPeer_setTextBackground
  (JNIEnv *env, jobject this, jobject c)
{
    struct TextAreaData *tdata;
    Pixel color;

    AWT_LOCK();

    tdata = (struct TextAreaData *) JNU_GetLongFieldAsPtr(env,this,mComponentPeerIDs.pData);

    if (tdata == NULL || tdata->txt == NULL || JNU_IsNull(env, c)) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        AWT_UNLOCK();
        return;
    }
    color = awtJNI_GetColor(env, c);
    XtVaSetValues(tdata->txt,
                  XmNbackground, color,
                  NULL);

    AWT_FLUSH_UNLOCK();
}

/*
 * Class:     sun_awt_motif_MTextAreaPeer
 * Method:    pSetEditable
 * Signature: (Z)V
 */
JNIEXPORT void JNICALL Java_sun_awt_motif_MTextAreaPeer_pSetEditable
  (JNIEnv *env, jobject this, jboolean editable)
{
    struct TextAreaData *tdata;

    AWT_LOCK();

    tdata = (struct TextAreaData *) JNU_GetLongFieldAsPtr(env,this,mComponentPeerIDs.pData);

    if (tdata == NULL || tdata->txt == NULL) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        AWT_UNLOCK();
        return;
    }
    XtVaSetValues(tdata->txt,
                  XmNeditable, (editable ? True : False),
                  XmNcursorPositionVisible, (editable ? True : False),
                  NULL);

    AWT_FLUSH_UNLOCK();
}

/*
 * Class:     sun_awt_motif_MTextAreaPeer
 * Method:    select
 * Signature: (II)V
 */
JNIEXPORT void JNICALL Java_sun_awt_motif_MTextAreaPeer_select
  (JNIEnv *env, jobject this, jint start, jint end)
{
    struct TextAreaData *tdata;

    AWT_LOCK();

    tdata = (struct TextAreaData *) JNU_GetLongFieldAsPtr(env,this,mComponentPeerIDs.pData);

    if (tdata == NULL || tdata->txt == NULL) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        AWT_UNLOCK();
        return;
    }
    XmTextSetSelection(tdata->txt, (XmTextPosition) start, (XmTextPosition) end, 0);
    AWT_FLUSH_UNLOCK();
}

/*
 * Class:     sun_awt_motif_MTextAreaPeer
 * Method:    getSelectionStart
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_sun_awt_motif_MTextAreaPeer_getSelectionStart
  (JNIEnv *env, jobject this)
{
    struct TextAreaData *tdata;
    XmTextPosition start, end, pos;

    AWT_LOCK();

    tdata = (struct TextAreaData *) JNU_GetLongFieldAsPtr(env,this,mComponentPeerIDs.pData);

    if (tdata == NULL || tdata->txt == NULL) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        AWT_UNLOCK();
        return 0;
    }
    if (XmTextGetSelectionPosition(tdata->txt, &start, &end) &&
                                             (start != end)) {
        pos = start;
    } else {
        pos = XmTextGetInsertionPosition(tdata->txt);
    }
    AWT_UNLOCK();

    return (jint) pos;
}

/*
 * Class:     sun_awt_motif_MTextAreaPeer
 * Method:    getSelectionEnd
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_sun_awt_motif_MTextAreaPeer_getSelectionEnd
  (JNIEnv *env, jobject this)
{
    struct TextAreaData *tdata;
    XmTextPosition start, end, pos;

    AWT_LOCK();

    tdata = (struct TextAreaData *) JNU_GetLongFieldAsPtr(env,this,mComponentPeerIDs.pData);
    if (tdata == NULL || tdata->txt == NULL) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        AWT_UNLOCK();
        return 0;
    }
    if (XmTextGetSelectionPosition(tdata->txt, &start, &end) &&
                                             (start != end)) {
        pos = end;
    } else {
        pos = XmTextGetInsertionPosition(tdata->txt);
    }
    AWT_UNLOCK();

    return (jint) pos;
}

/*
 * Class:     sun_awt_motif_MTextAreaPeer
 * Method:    setText
 * Signature: (Ljava/lang/String;)V
 */
JNIEXPORT void JNICALL Java_sun_awt_motif_MTextAreaPeer_setText
  (JNIEnv *env, jobject this, jstring txt)
{
    struct TextAreaData *tdata;
    char *cTxt;
    jobject font = awtJNI_GetFont(env, this);

    if (JNU_IsNull(env, txt)) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        return;
    }
    AWT_LOCK();

    tdata = (struct TextAreaData *)
      JNU_GetLongFieldAsPtr(env,this,mComponentPeerIDs.pData);
    if (tdata == NULL || tdata->txt == NULL) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        AWT_UNLOCK();
        return;
    }
    cTxt = (char *) JNU_GetStringPlatformChars(env, txt, NULL);

    if (cTxt == NULL) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        AWT_UNLOCK();
        return;
    }
    XtVaSetValues(tdata->txt, XmNvalue, cTxt, NULL);

    if (cTxt != NULL) {
        JNU_ReleaseStringPlatformChars(env, txt, cTxt);
    }
    AWT_FLUSH_UNLOCK();
}

/*
 * Class:     sun_awt_motif_MTextAreaPeer
 * Method:    getText
 * Signature: ()Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_sun_awt_motif_MTextAreaPeer_getText
  (JNIEnv *env, jobject this)
{
    struct TextAreaData *tdata;
    char *cTxt;
    jstring rval;
    jobject font = awtJNI_GetFont(env, this);

    AWT_LOCK();

    tdata = (struct TextAreaData *)
      JNU_GetLongFieldAsPtr(env,this, mComponentPeerIDs.pData);
    if (tdata == NULL || tdata->txt == NULL) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        AWT_UNLOCK();
        return NULL;
    }
    cTxt = XmTextGetString(tdata->txt);

    rval = JNU_NewStringPlatform(env, (const char *) cTxt);

    XtFree(cTxt);

    AWT_UNLOCK();

    return rval;
}

/*
 * Class:     sun_awt_motif_MTextAreaPeer
 * Method:    insert
 * Signature: (Ljava/lang/String;I)V
 */
JNIEXPORT void JNICALL Java_sun_awt_motif_MTextAreaPeer_insert
  (JNIEnv *env, jobject this, jstring txt, jint pos)
{
    struct TextAreaData *tdata;
    char *cTxt;
    jobject font = awtJNI_GetFont(env, this);

    if (JNU_IsNull(env, txt)) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        return;
    }
    AWT_LOCK();
    tdata = (struct TextAreaData *)
      JNU_GetLongFieldAsPtr(env, this, mComponentPeerIDs.pData);
    if (tdata == NULL || tdata->txt == NULL) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        AWT_UNLOCK();
        return;
    }
    cTxt = (char *) JNU_GetStringPlatformChars(env, txt, NULL);

    if (cTxt == NULL) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        AWT_UNLOCK();
        return;
    }
    XmTextInsert(tdata->txt, (XmTextPosition) pos, cTxt);

    if (cTxt != NULL) {
        JNU_ReleaseStringPlatformChars(env, txt, cTxt);
    }
    AWT_FLUSH_UNLOCK();
}

/*
 * Class:     sun_awt_motif_MTextAreaPeer
 * Method:    replaceRange
 * Signature: (Ljava/lang/String;II)V
 */
JNIEXPORT void JNICALL Java_sun_awt_motif_MTextAreaPeer_replaceRange
  (JNIEnv *env, jobject this, jstring txt, jint start, jint end)
{
    struct TextAreaData *tdata;
    char *cTxt;
    jobject font = awtJNI_GetFont(env, this);

    if (JNU_IsNull(env, txt)) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        return;
    }
    AWT_LOCK();
    tdata = (struct TextAreaData *)
      JNU_GetLongFieldAsPtr(env, this, mComponentPeerIDs.pData);
    if (tdata == NULL || tdata->txt == NULL) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        AWT_UNLOCK();
        return;
    }
    cTxt = (char *) JNU_GetStringPlatformChars(env, txt, NULL);

    if (cTxt == NULL) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        AWT_UNLOCK();
        return;
    }
    XmTextReplace(tdata->txt,
                  (XmTextPosition) start,
                  (XmTextPosition) end,
                  cTxt);

    if (cTxt != NULL) {
        JNU_ReleaseStringPlatformChars(env, txt, cTxt);
    }
    AWT_FLUSH_UNLOCK();
}

/*
 * Class:     sun_awt_motif_MTextAreaPeer
 * Method:    setFont
 * Signature: (Ljava/awt/Font;)V
 */
JNIEXPORT void JNICALL Java_sun_awt_motif_MTextAreaPeer_setFont
  (JNIEnv *env, jobject this, jobject f)
{
    struct TextAreaData *tdata;
    struct FontData *fdata;
    XmFontList fontlist;
    char *err;
    XmFontListEntry fontentry;

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
    tdata = (struct TextAreaData *)
      JNU_GetLongFieldAsPtr(env, this, mComponentPeerIDs.pData);
    if (tdata == NULL || tdata->comp.widget == NULL) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        AWT_UNLOCK();
        return;
    }
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

    if (fontlist != NULL) {
        Dimension textw, texth;
        Dimension w, h;

        XtVaGetValues(tdata->txt,
                      XmNwidth, &textw,
                      XmNheight, &texth,
                      NULL);
        XtVaGetValues(tdata->comp.widget,
                      XmNwidth, &w,
                      XmNheight, &h,
                      NULL);

        /* Must set width/height when we set the font, else
         * Motif resets the text to a single row.
         */
        XtVaSetValues(tdata->txt,
                      XmNfontList, fontlist,
                      XmNwidth, textw,
                      XmNheight, texth,
                      NULL);
        XtVaSetValues(tdata->comp.widget,
                      XmNwidth, w,
                      XmNheight, h,
                      NULL);

        XmFontListFree(fontlist);
    } else {
        JNU_ThrowNullPointerException(env, "NullPointerException");
    }

    AWT_UNLOCK();
}

/*
 * Class:     sun_awt_motif_MTextAreaPeer
 * Method:    setCaretPosition
 * Signature: (I)V
 */
JNIEXPORT void JNICALL Java_sun_awt_motif_MTextAreaPeer_setCaretPosition
  (JNIEnv *env, jobject this, jint pos)
{
    struct TextAreaData *tdata;

    AWT_LOCK();

    tdata = (struct TextAreaData *)
      JNU_GetLongFieldAsPtr(env, this, mComponentPeerIDs.pData);

    if (tdata == NULL || tdata->txt == NULL) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        AWT_UNLOCK();
        return;
    }
    XmTextSetInsertionPosition(tdata->txt, (XmTextPosition) pos);

    AWT_FLUSH_UNLOCK();
}

/*
 * Class:     sun_awt_motif_MTextAreaPeer
 * Method:    getCaretPosition
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_sun_awt_motif_MTextAreaPeer_getCaretPosition
  (JNIEnv *env, jobject this)
{
    struct TextAreaData *tdata;
    XmTextPosition pos;

    AWT_LOCK();

    tdata = (struct TextAreaData *)
      JNU_GetLongFieldAsPtr(env, this, mComponentPeerIDs.pData);

    if (tdata == NULL || tdata->txt == NULL) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        AWT_UNLOCK();
        return 0;
    }
    pos = XmTextGetInsertionPosition(tdata->txt);

    AWT_UNLOCK();

    return (jint) pos;
}

/*
 * Class:     sun_awt_motif_MTextAreaPeer
 * Method:    pShow
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_sun_awt_motif_MTextAreaPeer_pShow2
  (JNIEnv *env, jobject this)
{
    struct TextAreaData *tdata;

    AWT_LOCK();
    tdata = (struct TextAreaData *) JNU_GetLongFieldAsPtr(env,this,mComponentPeerIDs.pData);
    if (tdata == NULL || tdata->comp.widget == NULL) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        AWT_UNLOCK();
        return;
    }

    awt_util_show(tdata->comp.widget);
    AWT_FLUSH_UNLOCK();
}


/*
 * Class:     sun_awt_motif_MTextAreaPeer
 * Method:    pMakeCursorVisible
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_sun_awt_motif_MTextAreaPeer_pMakeCursorVisible
  (JNIEnv *env, jobject this)
{
    struct TextAreaData *tdata;

    AWT_LOCK();
    tdata = (struct TextAreaData *) JNU_GetLongFieldAsPtr(env,this,mComponentPeerIDs.pData);
    if (tdata == NULL || tdata->comp.widget == NULL) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        AWT_UNLOCK();
        return;
    }

    AWT_FLUSH_UNLOCK();
}

/*
 * Class:     sun_awt_motif_MTextAreaPeer
 * Method:    pSetCursor
 * Signature: (L/java/awt/Cursor;)V
 */
JNIEXPORT void JNICALL Java_sun_awt_motif_MTextAreaPeer_pSetCursor
  (JNIEnv *env, jobject this, jobject cursor)
{
    Cursor xcursor;
    struct TextAreaData         *tdata;

    AWT_LOCK();
    tdata = (struct TextAreaData *)
        JNU_GetLongFieldAsPtr(env, this, mComponentPeerIDs.pData);
    if (tdata == NULL || tdata->comp.widget == NULL || JNU_IsNull(env, cursor)) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        AWT_UNLOCK();
        return;
    }

    awt_util_setCursor(tdata->txt, getCursor(env, cursor));

    AWT_FLUSH_UNLOCK();
}

/*
 * Class:     sun_awt_motif_MTextAreaPeer
 * Method:    nativeHandleMouseWheel
 * Signature: (III)V
 */
JNIEXPORT void JNICALL Java_sun_awt_motif_MTextAreaPeer_nativeHandleMouseWheel
  (JNIEnv *env, jobject this, jint scrollType, jint scrollAmt, jint wheelAmt)
{
    struct TextAreaData         *tdata;
    Widget text = NULL;
    Widget scroll = NULL;

    AWT_LOCK();
    tdata = (struct TextAreaData *)
        JNU_GetLongFieldAsPtr(env, this, mComponentPeerIDs.pData);
    if (tdata == NULL || tdata->comp.widget == NULL) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        AWT_UNLOCK();
        return;
    }
    // get the Text widget
    text = tdata->txt;
    if (text == NULL) {
        AWT_UNLOCK();
        return;
    }

    // get the ScrolledWindow
    scroll = XtParent(text);
    if (scroll == NULL) {
        AWT_UNLOCK();
        return;
    }

    awt_util_do_wheel_scroll(scroll, scrollType, scrollAmt, wheelAmt);
    AWT_UNLOCK();
}



/*  To be fully implemented in a future release
 *
 * Class:     sun_awt_windows_MTextAreaPeer
 * Method:    getIndexAtPoint
 * Signature: (II)I
 *
JNIEXPORT jint JNICALL
Java_sun_awt_motif_MTextAreaPeer_getIndexAtPoint(JNIEnv *env, jobject self,
 jint x, jint y)
{
    struct TextAreaData *tdata;
    XmTextPosition pos;

    AWT_LOCK();

    tdata = (struct TextAreaData *)
        JNU_GetLongFieldAsPtr(env,self,mComponentPeerIDs.pData);

    if (tdata == NULL || tdata->txt == NULL) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        AWT_UNLOCK();
        return -1;
    }
    pos = XmTextXYToPos(tdata->txt, x, y);
    AWT_UNLOCK();

    return (jint) pos;
}
*/

/*  To be fully implemented in a future release
 *
 * Class:     sun_awt_windows_MTextAreaPeer
 * Method:    getCharacterBounds
 * Signature: (I)Ljava/awt/Rectangle;
 *
JNIEXPORT jobject JNICALL
Java_sun_awt_motif_MTextAreaPeer_getCharacterBounds(JNIEnv *env, jobject self, jint i)
{
#define Text_FontAscent(tfg)                   (((XmTextWidget)(tfg)) -> \
                                           text.output->data->font_ascent)
#define Text_FontDescent(tfg)                  (((XmTextWidget)(tfg)) -> \
                                           text.output->data->font_descent)

    struct TextAreaData *tdata;
    jobject rect=NULL;
    Position x=0, y=0;
    Position next_x=0, next_y=0;
    int32_t w=0, h=0;

    AWT_LOCK();

    tdata = (struct TextAreaData *)
        JNU_GetLongFieldAsPtr(env,self,mComponentPeerIDs.pData);

    if (tdata == NULL || tdata->txt == NULL) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        AWT_UNLOCK();
        return (jobject) NULL;
    }

    XmTextPosToXY(tdata->txt, i, &x, &y);
    y -= Text_FontAscent(tdata->txt);
    XmTextPosToXY(tdata->txt, i+1, &next_x, &next_y);
    w = next_x - x;
    h = Text_FontAscent(tdata->txt) + Text_FontDescent(tdata->txt);

    AWT_UNLOCK();

    if (w>0) {
        jclass clazz;
        jmethodID mid;

        clazz = (*env)->FindClass(env, "java/awt/Rectangle");
        mid = (*env)->GetMethodID(env, clazz, "<init>", "(IIII)V");
        if (mid != NULL) {
            rect = (*env)->NewObject(env, clazz, mid, x, y, w, h);
            if ((*env)->ExceptionOccurred(env)) {
                return (jobject) NULL;
            }
        }
    }
    return rect;
}
*/
