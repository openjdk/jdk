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

#include <Xm/VirtKeys.h>

#include "awt_p.h"
#include "java_awt_TextField.h"
#include "java_awt_Color.h"
#include "java_awt_AWTEvent.h"
#include "java_awt_Font.h"
#include "java_awt_Canvas.h"
#include "sun_awt_motif_MComponentPeer.h"
#include "sun_awt_motif_MCanvasPeer.h"
#include "sun_awt_motif_MTextFieldPeer.h"

#include "awt_Component.h"
#include "awt_TextField.h"

#include "multi_font.h"
#include <jni.h>
#include <jni_util.h>
#include <Xm/DropSMgr.h>
#include <Xm/TextFP.h>  /* Motif TextField private header. */


#define ECHO_BUFFER_LEN 1024

extern struct MComponentPeerIDs mComponentPeerIDs;
extern AwtGraphicsConfigDataPtr
    copyGraphicsConfigToPeer(JNIEnv *env, jobject this);
struct TextFieldIDs textFieldIDs;
struct MTextFieldPeerIDs mTextFieldPeerIDs;

/*
 * Class:     java_awt_TextField
 * Method:    initIDs
 * Signature: ()V
 */

/* This function gets called from the static initializer for TextField.java
   to initialize the fieldIDs for fields that may be accessed from C */

JNIEXPORT void JNICALL
Java_java_awt_TextField_initIDs
  (JNIEnv *env, jclass cls)
{
    textFieldIDs.echoChar =
      (*env)->GetFieldID(env, cls, "echoChar", "C");
}

/*
 * Class:     sun_awt_motif_MTextFieldPeer
 * Method:    initIDs
 * Signature: ()V
 */

/* This function gets called from the static initializer for
   MTextFieldPeer.java to initialize the fieldIDs for fields that may
   be accessed from C */

JNIEXPORT void JNICALL
Java_sun_awt_motif_MTextFieldPeer_initIDs
  (JNIEnv *env, jclass cls)
{
    mTextFieldPeerIDs.firstChangeSkipped =
      (*env)->GetFieldID(env, cls, "firstChangeSkipped", "Z");
}

static void
echoChar(Widget text_w, XtPointer unused, XmTextVerifyCallbackStruct * cbs)
{
    size_t len;
    int32_t c;
    char *val;
    struct DPos *dp;
    int32_t ret;
    jobject globalRef;
    int32_t i, numbytes;

    struct TextFieldData *tdata;

    JNIEnv *env = (JNIEnv *)JNU_GetEnv(jvm, JNI_VERSION_1_2);

    /*
     * Get the echoContextID from the globalRef which is stored in
     * the XmNuserData resource for the widget.
     */
    XtVaGetValues(text_w,XmNuserData,&globalRef,NULL);

    tdata = (struct TextFieldData *)
      (*env)->GetLongField(env,globalRef,mComponentPeerIDs.pData);

    ret = XFindContext(XtDisplay(text_w), (XID)text_w, tdata->echoContextID,
                       (XPointer *)&dp);
    if ((ret != 0) || (dp == NULL)) {
        /* no context found or DPos is NULL - shouldn't happen */
        return;
    }

    c = dp->echoC;
    val = (char *) (dp->data);

    len = strlen(val);
    if (cbs->text->ptr == NULL) {
        if (cbs->text->length == 0 && cbs->startPos == 0) {
            val[0] = '\0';
            return;
        } else if (cbs->startPos == (len - 1)) {
            /* handle deletion */
            cbs->endPos = strlen(val);
            val[cbs->startPos] = '\0';
            return;
        } else {
            /* disable deletes anywhere but at the end */
            cbs->doit = False;
            return;
        }
    }
    if (cbs->startPos != len) {
        /* disable "paste" or inserts into the middle */
        cbs->doit = False;
        return;
    }
    /* append the value typed in */
    if ((cbs->endPos + cbs->text->length) > ECHO_BUFFER_LEN) {
        val = realloc(val, cbs->endPos + cbs->text->length + 10);
    }
    strncat(val, cbs->text->ptr, cbs->text->length);
    val[cbs->endPos + cbs->text->length] = '\0';

    /* modify the output to be the echo character */
    for (len = 0, i = 0; len < cbs->text->length; i++) {
        /* Write one echo character for each multibyte character. */
        numbytes = mblen(cbs->text->ptr + len, cbs->text->length - len);
        cbs->text->ptr[i] = (char) c;
        len += numbytes;
    }
    cbs->text->length = i;
}

/*
 * Event handler used by both TextField/TextArea to correctly process
 * cut/copy/paste keys such that interaction with our own
 * clipboard mechanism will work properly.
 *
 * client_data is MTextFieldPeer instance
 */
void
Text_handlePaste(Widget w, XtPointer client_data, XEvent * event, Boolean * cont)
{
    JNIEnv *env = (JNIEnv *)JNU_GetEnv(jvm, JNI_VERSION_1_2);
    KeySym keysym;
    Modifiers mods;

    /* Any event handlers which take peer instance pointers as
     * client_data should check to ensure the widget has not been
     * marked as destroyed as a result of a dispose() call on the peer
     * (which can result in the peer instance pointer already haven
     * been gc'd by the time this event is processed)
     */
    if (event->type != KeyPress || w->core.being_destroyed) {
        return;
    }

    XtTranslateKeycode(event->xkey.display, (KeyCode) event->xkey.keycode,
                       event->xkey.state, &mods, &keysym);

    /* Should be a temporary fix for 4052132 if a cleaner fix is found later */
    if ((event->xkey.state & ControlMask) && (keysym == 'v' || keysym == 'V'))
        keysym = osfXK_Paste;
    if ((event->xkey.state & ShiftMask) && (keysym == osfXK_Insert))
        keysym = osfXK_Paste;

    switch (keysym) {
        case osfXK_Paste:
            /* If we own the selection, then paste the data directly */
            if (awtJNI_isSelectionOwner(env, "CLIPBOARD")) {
                JNU_CallMethodByName(env, NULL, (jobject) client_data,
                                     "pasteFromClipboard", "()V");
                if ((*env)->ExceptionOccurred(env)) {
                    (*env)->ExceptionDescribe(env);
                    (*env)->ExceptionClear(env);
                }
                *cont = FALSE;
            }
            break;

        case osfXK_Cut:
        case osfXK_Copy:
            /* For some reason if we own the selection, our loseSelection
             * callback is not automatically called on cut/paste from
             * text widgets.
             */
            if (awtJNI_isSelectionOwner(env, "CLIPBOARD")) {
                awtJNI_notifySelectionLost(env, "CLIPBOARD");
            }
            break;
        default:
            break;
    }
}

/*
 * client_data is MTextFieldPeer instance
 */
void
TextField_valueChanged(Widget w, XtPointer client_data, XtPointer call_data)
{
    JNIEnv *env = (JNIEnv *)JNU_GetEnv(jvm, JNI_VERSION_1_2);
    jboolean skipped;

    skipped = (*env)->GetBooleanField(env, (jobject) client_data,
                                      mTextFieldPeerIDs.firstChangeSkipped);
    if (!(*env)->ExceptionOccurred(env)) {
        if (skipped == JNI_FALSE) {
            (*env)->SetBooleanField(env, (jobject) client_data,
                                    mTextFieldPeerIDs.firstChangeSkipped,
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

/*
 * client_data is MTextFieldPeer instance
 */
static void
TextField_action(Widget w, XtPointer client_data, XmAnyCallbackStruct * s)
{
    JNIEnv *env = (JNIEnv *)JNU_GetEnv(jvm, JNI_VERSION_1_2);
    ConvertEventTimeAndModifiers converted;

    awt_util_convertEventTimeAndModifiers(s->event, &converted);

    JNU_CallMethodByName(env, NULL, (jobject) client_data, "action", "(JI)V",
                         converted.when, converted.modifiers);
    if ((*env)->ExceptionOccurred(env)) {
        (*env)->ExceptionDescribe(env);
        (*env)->ExceptionClear(env);
    }
}

/*
 * Class:     sun_awt_motif_MTextFieldPeer
 * Method:    pCreate
 * Signature: (Lsun/awt/motif/MComponentPeer;)V
 */
JNIEXPORT void JNICALL Java_sun_awt_motif_MTextFieldPeer_pCreate
  (JNIEnv *env, jobject this, jobject parent)
{
    struct ComponentData *wdata;
    struct TextFieldData *tdata;

    jobject globalRef = awtJNI_CreateAndSetGlobalRef(env, this);
    AwtGraphicsConfigDataPtr adata;

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
    tdata = ZALLOC(TextFieldData);
    if (tdata == NULL) {
        JNU_ThrowOutOfMemoryError(env, "OutOfMemoryError");
        AWT_UNLOCK();
        return;
    }
    JNU_SetLongFieldFromPtr(env,this,mComponentPeerIDs.pData,tdata);

    tdata->comp.widget = XtVaCreateManagedWidget("textfield",
                                                 xmTextFieldWidgetClass,
                                                 wdata->widget,
                                                 XmNrecomputeSize, False,
                                                 XmNhighlightThickness, 1,
                                                 XmNshadowThickness, 2,
                                                 XmNuserData, (XtPointer) globalRef,
                                                 XmNscreen,
                                                 ScreenOfDisplay(awt_display,
                                                   adata->awt_visInfo.screen),
                                                 XmNfontList, getMotifFontList(),
                                                 NULL);
    tdata->echoContextIDInit = FALSE;

    XtSetMappedWhenManaged(tdata->comp.widget, False);
    XtAddCallback(tdata->comp.widget,
                  XmNactivateCallback,
                  (XtCallbackProc) TextField_action,
                  (XtPointer) globalRef);
    XtAddCallback(tdata->comp.widget,
                  XmNvalueChangedCallback,
                  (XtCallbackProc) TextField_valueChanged,
                  (XtPointer) globalRef);
    XtInsertEventHandler(tdata->comp.widget,
                         KeyPressMask,
                         False, Text_handlePaste, (XtPointer) globalRef,
                         XtListHead);
    /*
     * Fix for BugTraq ID 4349615.
     * Unregister Motif drop site to prevent it from crash
     * when dropping java objects.
     */
    XmDropSiteUnregister(tdata->comp.widget);

    AWT_UNLOCK();
}

/*
 * Class     sun_awt_motif_MTextFieldPeer
 * Method:    pSetEditable
 * Signature: (Z)V
 */
JNIEXPORT void JNICALL Java_sun_awt_motif_MTextFieldPeer_pSetEditable
  (JNIEnv *env, jobject this, jboolean editable)
{
    struct TextFieldData *tdata;

    AWT_LOCK();
    tdata = (struct TextFieldData *)
        JNU_GetLongFieldAsPtr(env,this,mComponentPeerIDs.pData);

    if (tdata == NULL || tdata->comp.widget == NULL) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        AWT_UNLOCK();
        return;
    }
    XtVaSetValues(tdata->comp.widget,
                  XmNeditable, (editable ? True : False),
                  XmNcursorPositionVisible, (editable ? True : False),
                  NULL);
    AWT_FLUSH_UNLOCK();
}

/*
 * Class:     sun_awt_motif_MTextFieldPeer
 * Method:    select
 * Signature: (II)V
 */
JNIEXPORT void JNICALL Java_sun_awt_motif_MTextFieldPeer_select
  (JNIEnv *env, jobject this, jint start, jint end)
{
    struct TextFieldData *tdata;

    AWT_LOCK();

    tdata = (struct TextFieldData *)
        JNU_GetLongFieldAsPtr(env,this,mComponentPeerIDs.pData);

    if (tdata == NULL || tdata->comp.widget == NULL) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        AWT_UNLOCK();
        return;
    }
    XmTextSetSelection(tdata->comp.widget, (XmTextPosition) start, (XmTextPosition) end, 0);
    AWT_FLUSH_UNLOCK();
}

/*
 * Class:     sun_awt_motif_MTextFieldPeer
 * Method:    getSelectionStart
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_sun_awt_motif_MTextFieldPeer_getSelectionStart
  (JNIEnv *env, jobject this)
{
    struct TextFieldData *tdata;
    XmTextPosition start, end, pos;

    AWT_LOCK();

    tdata = (struct TextFieldData *)
        JNU_GetLongFieldAsPtr(env,this,mComponentPeerIDs.pData);

    if (tdata == NULL || tdata->comp.widget == NULL) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        AWT_UNLOCK();
        return 0;
    }
    if (XmTextGetSelectionPosition(tdata->comp.widget, &start, &end) &&
                                                (start != end)) {
        pos = start;
    } else {
        pos = XmTextGetInsertionPosition(tdata->comp.widget);
    }
    AWT_UNLOCK();

    return (jint) pos;
}

/*
 * Class:     sun_awt_motif_MTextFieldPeer
 * Method:    getSelectionEnd
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_sun_awt_motif_MTextFieldPeer_getSelectionEnd
  (JNIEnv *env, jobject this)
{
    struct TextFieldData *tdata;
    XmTextPosition start, end, pos;

    AWT_LOCK();

    tdata = (struct TextFieldData *)
        JNU_GetLongFieldAsPtr(env,this,mComponentPeerIDs.pData);

    if (tdata == NULL || tdata->comp.widget == NULL) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        AWT_UNLOCK();
        return 0;
    }
    if (XmTextGetSelectionPosition(tdata->comp.widget, &start, &end) &&
                                                 (start != end)) {
        pos = end;
    } else {
        pos = XmTextGetInsertionPosition(tdata->comp.widget);
    }
    AWT_UNLOCK();

    return (jint) pos;
}

/*
 * Class:     sun_awt_motif_MTextFieldPeer
 * Method:    setText
 * Signature: (Ljava/lang/String;)V
 */
JNIEXPORT void JNICALL Java_sun_awt_motif_MTextFieldPeer_setText
  (JNIEnv *env, jobject this, jstring l)
{
    struct TextFieldData *tdata;
    char *cl;
    jobject target;

    AWT_LOCK();

    tdata = (struct TextFieldData *)
        JNU_GetLongFieldAsPtr(env,this,mComponentPeerIDs.pData);

    if (tdata == NULL || tdata->comp.widget == NULL) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        AWT_UNLOCK();
        return;
    }
    if (JNU_IsNull(env, l)) {
        cl = "";
    } else {
        /*
         * Note: Motif TextField widgets do not support multi-font
         * compound strings.
         */
        cl = (char *) JNU_GetStringPlatformChars(env, l, NULL);
    }

    /* Fix for bug 4084454 : setText appears in clear */
    target = (*env)->GetObjectField(env, this, mComponentPeerIDs.target);
    if ((*env)->GetCharField(env, target, textFieldIDs.echoChar) != 0) {
        XtVaSetValues(tdata->comp.widget,
                      XmNvalue, "", NULL);
        XmTextFieldInsert(tdata->comp.widget,0,cl);
        XmTextSetInsertionPosition(tdata->comp.widget,
                                   (XmTextPosition) strlen(cl));
    }
    else {
        XtVaSetValues(tdata->comp.widget,
                      XmNvalue, cl,
                      NULL);
    }
    /*
     * Fix for BugTraq Id 4185654 - TextField.setText(<String>) incorrect justification
     * Comment out the next line.
     */
    /* XmTextSetInsertionPosition(tdata->comp.widget,
     *                            (XmTextPosition) strlen(cl));
     */

    if (cl != NULL && cl != "") {
        JNU_ReleaseStringPlatformChars(env, l, cl);
    }
    AWT_FLUSH_UNLOCK();
}

/*
 * Class:     sun_awt_motif_MTextFieldPeer
 * Method:    insertReplaceText
 * Signature: (Ljava/lang/String;)V
 */
JNIEXPORT void JNICALL Java_sun_awt_motif_MTextFieldPeer_insertReplaceText
  (JNIEnv *env, jobject this, jstring l)
{
    struct TextFieldData *tdata;
    char *cl;
    XmTextPosition start, end;

    AWT_LOCK();

    tdata = (struct TextFieldData *)
        JNU_GetLongFieldAsPtr(env,this,mComponentPeerIDs.pData);
    if (tdata == NULL || tdata->comp.widget == NULL) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        AWT_UNLOCK();
        return;
    }

    if (JNU_IsNull(env, l)) {
        cl = "";
    } else {
        /*
         * Note: Motif TextField widgets do not support multi-font
         * compound strings.
         */
        cl = (char *) JNU_GetStringPlatformChars(env, l, NULL);
    }

    if (!XmTextGetSelectionPosition(tdata->comp.widget, &start, &end)) {
        start = end = XmTextGetInsertionPosition(tdata->comp.widget);
    }
    XmTextReplace(tdata->comp.widget, start, end, cl);

    if (cl != NULL && cl != "") {
        JNU_ReleaseStringPlatformChars(env, l, cl);
    }
    AWT_FLUSH_UNLOCK();
}

/*
 * Class:     sun_awt_motif_MTextFieldPeer
 * Method:    preDispose
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_sun_awt_motif_MTextFieldPeer_preDispose
  (JNIEnv *env, jobject this)
{
    struct TextFieldData *tdata;
    struct DPos *dp;
    jobject target;
    int32_t ret;

    AWT_LOCK();

    tdata = (struct TextFieldData *)
        JNU_GetLongFieldAsPtr(env,this,mComponentPeerIDs.pData);

    if (tdata == NULL || tdata->comp.widget == NULL) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        AWT_UNLOCK();
        return;
    }
    target = (*env)->GetObjectField(env, this, mComponentPeerIDs.target);

    if ((*env)->GetCharField(env, target, textFieldIDs.echoChar) != 0) {
        ret = XFindContext(XtDisplay(tdata->comp.widget), (XID)(tdata->comp.widget),
                           tdata->echoContextID, (XPointer *)&dp);
        if ((ret == 0) && dp != NULL) {

            /* Remove the X context associated with this textfield's
             * echo character. BugId #4225734
             */
            XDeleteContext(XtDisplay(tdata->comp.widget),
                           (XID)(tdata->comp.widget),
                           tdata->echoContextID);

            tdata->echoContextIDInit = FALSE;

            /* Free up the space allocated for the echo character data. */
            if (dp->data) {
                free(dp->data);
            }
            free(dp);
        }
    }

    AWT_UNLOCK();
}

/*
 * Class:     sun_awt_motif_MTextFieldPeer
 * Method:    getText
 * Signature: ()Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_sun_awt_motif_MTextFieldPeer_getText
  (JNIEnv *env, jobject this)
{
    struct TextFieldData *tdata;
    char *val;
    struct DPos *dp;
    jobject target;
    int32_t ret;
    jstring returnVal;

    AWT_LOCK();
    tdata = (struct TextFieldData *)
        JNU_GetLongFieldAsPtr(env,this,mComponentPeerIDs.pData);

    if (tdata == NULL || tdata->comp.widget == NULL) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        AWT_UNLOCK();
        return NULL;
    }

    target = (*env)->GetObjectField(env, this, mComponentPeerIDs.target);

    if ((*env)->GetCharField(env, target, textFieldIDs.echoChar) != 0) {
        ret = XFindContext(XtDisplay(tdata->comp.widget), (XID)tdata->comp.widget,
                           tdata->echoContextID, (XPointer *)&dp);
        if ((ret == 0) && (dp != NULL)) {
            val = (char *)(dp->data);
        } else {
            val = "";
        }
    } else {
        XtVaGetValues(tdata->comp.widget, XmNvalue, &val, NULL);
    }
    AWT_UNLOCK();

    returnVal = JNU_NewStringPlatform(env, (const char *) val);
    if ((*env)->GetCharField(env, target, textFieldIDs.echoChar) == 0) {
        free(val);
    }
    return returnVal;
}

/*
 * Class:     sun_awt_motif_MTextFieldPeer
 * Method:    setEchoChar
 * Signature: (C)V
 */
JNIEXPORT void JNICALL Java_sun_awt_motif_MTextFieldPeer_setEchoChar
  (JNIEnv *env, jobject this, jchar c)
{
    char *val;
    char *cval;
    struct TextFieldData *tdata;
    struct DPos *dp;
    int32_t i;
    size_t len;
    int32_t ret;

    AWT_LOCK();
    tdata = (struct TextFieldData *)
        JNU_GetLongFieldAsPtr(env,this,mComponentPeerIDs.pData);
    if (tdata == NULL || tdata->comp.widget == NULL) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        AWT_UNLOCK();
        return;
    }

    XtVaGetValues(tdata->comp.widget,
                  XmNvalue, &cval,
                  NULL);

    DASSERT(c != 0 || tdata->echoContextIDInit);

    if (!tdata->echoContextIDInit) {
        tdata->echoContextID = XUniqueContext();
        tdata->echoContextIDInit = TRUE;
    }
    ret = XFindContext(XtDisplay(tdata->comp.widget), (XID)(tdata->comp.widget),
                       tdata->echoContextID, (XPointer *)&dp);
    /*
     * Fix for BugTraq ID 4307281.
     * Special case for setting echo char to 0:
     *  - remove the callback and X context associated with echo character;
     *  - restore the original text.
     */
    if (c == 0) {
        XtRemoveCallback(tdata->comp.widget, XmNmodifyVerifyCallback,
                         (XtCallbackProc) echoChar, NULL);
        if (ret == 0 && dp != NULL) {

            /* Remove the X context associated with echo character. */
            XDeleteContext(XtDisplay(tdata->comp.widget),
                           (XID)(tdata->comp.widget),
                           tdata->echoContextID);

            tdata->echoContextIDInit = FALSE;

            /* Restore the original text. */
            if (dp->data != NULL) {
                val = (char *)(dp->data);
            } else {
                val = "";
            }
            XtVaSetValues(tdata->comp.widget,
                          XmNvalue, val,
                          NULL);

            /* Free up the space allocated for the echo character data. */
            if (dp->data) {
                free(dp->data);
            }
            free(dp);
        }
        AWT_UNLOCK();
        return;
    }
    if (ret != 0) {
        dp = NULL;
    }

    if (dp != NULL) {
        /* Fix bug 4124697: cannot change setEchoChar twice on Motif */
        XtRemoveCallback(tdata->comp.widget, XmNmodifyVerifyCallback,
                        (XtCallbackProc) echoChar, NULL);
    } else {
        if ((int32_t) strlen(cval) > ECHO_BUFFER_LEN) {
            val = (char *) malloc(strlen(cval) + 1);
        } else {
            val = (char *) malloc(ECHO_BUFFER_LEN + 1);
        }
        if (val == NULL) {
            JNU_ThrowOutOfMemoryError(env, "OutOfMemoryError");
            AWT_UNLOCK();
            return;
        }
        if (cval != NULL) {
            strcpy(val, cval);
        } else {
            *val = '\0';
        }
        dp = (struct DPos *) malloc(sizeof(struct DPos));

        dp->x = -1;
        dp->data = (void *) val;
    }

    dp->echoC = c;
    len = strlen(cval);
    for (i = 0; i < len; i++) {
        cval[i] = (char) (c);
    }
    XtVaSetValues(tdata->comp.widget,
                  XmNvalue, cval,
                  NULL);

    ret = XSaveContext(XtDisplay(tdata->comp.widget), (XID)tdata->comp.widget,
                       tdata->echoContextID, (XPointer)dp);
    if (ret == 0) {
        XtAddCallback(tdata->comp.widget, XmNmodifyVerifyCallback,
                      (XtCallbackProc) echoChar, NULL);
    }
    AWT_UNLOCK();
}

/*
 * Class:     sun_awt_motif_MTextFieldPeer
 * Method:    setFont
 * Signature: (Ljava/awt/Font;)V
 */
JNIEXPORT void JNICALL Java_sun_awt_motif_MTextFieldPeer_setFont
  (JNIEnv *env, jobject this, jobject f)
{
    struct TextFieldData *tdata;
    struct FontData *fdata;
    XmFontListEntry fontentry;
    XmFontList fontlist;
    char *err;

    AWT_LOCK();
    if (JNU_IsNull(env, f)) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        AWT_UNLOCK();
        return;
    }
    fdata = awtJNI_GetFontData(env, f, &err);
    if (fdata == NULL) {
        JNU_ThrowInternalError(env, err);
        AWT_UNLOCK();
        return;
    }
    tdata = (struct TextFieldData *)
        JNU_GetLongFieldAsPtr(env,this,mComponentPeerIDs.pData);
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
        XtVaSetValues(tdata->comp.widget, XmNfontList, fontlist, NULL);
        XmFontListFree(fontlist);
    } else {
        JNU_ThrowNullPointerException(env, "NullPointerException");
    }
    AWT_UNLOCK();
}

/*
 * Class:     sun_awt_motif_MTextFieldPeer
 * Method:    setCaretPosition
 * Signature: (I)V
 */
JNIEXPORT void JNICALL Java_sun_awt_motif_MTextFieldPeer_setCaretPosition
  (JNIEnv *env, jobject this, jint pos)
{
    struct TextFieldData *tdata;

    AWT_LOCK();

    tdata = (struct TextFieldData *)
        JNU_GetLongFieldAsPtr(env,this,mComponentPeerIDs.pData);

    if (tdata == NULL || tdata->comp.widget == NULL) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        AWT_UNLOCK();
        return;
    }
    XmTextSetInsertionPosition(tdata->comp.widget, (XmTextPosition) pos);

    AWT_FLUSH_UNLOCK();
}

/*
 * Class:     sun_awt_motif_MTextFieldPeer
 * Method:    getCaretPosition
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_sun_awt_motif_MTextFieldPeer_getCaretPosition
  (JNIEnv *env, jobject this)
{
    struct TextFieldData *tdata;
    XmTextPosition pos;

    AWT_LOCK();

    tdata = (struct TextFieldData *)
        JNU_GetLongFieldAsPtr(env,this,mComponentPeerIDs.pData);

    if (tdata == NULL || tdata->comp.widget == NULL) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        AWT_UNLOCK();
        return 0;
    }
    pos = XmTextGetInsertionPosition(tdata->comp.widget);
    AWT_UNLOCK();

    return (jint) pos;
}


/*  To be fully implemented in a future release
 *
 * Class:     sun_awt_windows_MTextFieldPeer
 * Method:    getIndexAtPoint
 * Signature: (Ljava/awt/Point;)I
 *
JNIEXPORT jint JNICALL
Java_sun_awt_motif_MTextFieldPeer_getIndexAtPoint(JNIEnv *env, jobject self,
 jint x, jint y)
{
    struct ComponentData *tdata;
    XmTextPosition pos;

    AWT_LOCK();

    tdata = (struct ComponentData *)
        JNU_GetLongFieldAsPtr(env,self,mComponentPeerIDs.pData);

    if (tdata == NULL || tdata->comp.widget == NULL) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        AWT_UNLOCK();
        return -1;
    }
    pos = XmTextFieldXYToPos(tdata->widget, x, y);
    AWT_UNLOCK();

    return (jint) pos;
}
*/

/*  To be fully implemented in a future release
 *
 * Class:     sun_awt_windows_MTextFieldPeer
 * Method:    getCharacterBounds
 * Signature: (I)Ljava/awt/Rectangle;
 *
JNIEXPORT jobject JNICALL
Java_sun_awt_motif_MTextFieldPeer_getCharacterBounds(JNIEnv *env, jobject self, jint i)
{
#define TextF_FontAscent(tfg)                   (((XmTextFieldWidget)(tfg)) -> \
                                           text.font_ascent)
#define TextF_FontDescent(tfg)                  (((XmTextFieldWidget)(tfg)) -> \
                                           text.font_descent)

    struct ComponentData *tdata;
    jobject rect=NULL;
    Position x=0, y=0;
    Position next_x=0, next_y=0;
    int32_t w=0, h=0;

    AWT_LOCK();

    tdata = (struct ComponentData *)
        JNU_GetLongFieldAsPtr(env,self,mComponentPeerIDs.pData);

    if (tdata == NULL || tdata->widget == NULL) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        AWT_UNLOCK();
        return (jobject) NULL;
    }

    XmTextFieldPosToXY(tdata->widget, i, &x, &y);
    y -= TextF_FontAscent(tdata->widget);
    XmTextFieldPosToXY(tdata->widget, i+1, &next_x, &next_y);
    w = next_x - x;
    h = TextF_FontAscent(tdata->widget) + TextF_FontDescent(tdata->widget);

    AWT_UNLOCK();

    if (w>0) {
        jclass clazz;
        jmethodID mid;

        clazz = (*env)->FindClass(env, "java/awt/Rectangle");
        mid = (*env)->GetMethodID(env, clazz, "<init>", "(IIII)V");
        if (mid != NULL) {
            rect = (*env)->NewObject(env, clazz, mid, x, y, w, h);
            if ((*env)->ExceptionOccurred(env)) {
                return NULL;
            }
        }
    }
    return rect;
}
*/
