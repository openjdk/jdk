/*
 * Copyright 1995-2002 Sun Microsystems, Inc.  All Rights Reserved.
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
#include "sun_awt_motif_MComponentPeer.h"
#include "sun_awt_motif_MCheckboxPeer.h"
#include "java_awt_Checkbox.h"
#include "java_awt_CheckboxGroup.h"

#include "awt_Component.h"

#include "multi_font.h"
#include <jni.h>
#include <jni_util.h>

extern struct MComponentPeerIDs mComponentPeerIDs;
extern AwtGraphicsConfigDataPtr
    copyGraphicsConfigToPeer(JNIEnv *env, jobject this);

/* fieldIDs for Checkbox fields that may be accessed from C */
static struct CheckboxIDs {
    jfieldID label;
} checkboxIDs;

static char emptyString[] = "";


/*
 * Class:     java_awt_Checkbox
 * Method:    initIDs
 * Signature: ()V
 */

/* This function gets called from the static initializer for Checkbox.java
   to initialize the fieldIDs for fields that may be accessed from C */

JNIEXPORT void JNICALL
Java_java_awt_Checkbox_initIDs
  (JNIEnv *env, jclass cls)
{
    checkboxIDs.label =
      (*env)->GetFieldID(env, cls, "label", "Ljava/lang/String;");
}

/*
 * client_data is MCheckboxPeer instance pointer
 */
static void
Toggle_callback(Widget w,
                XtPointer client_data,
                XmAnyCallbackStruct * call_data)
{
    Boolean state;
    JNIEnv *env = (JNIEnv *)JNU_GetEnv(jvm, JNI_VERSION_1_2);

    XtVaGetValues(w, XmNset, &state, NULL);

    JNU_CallMethodByName(env, NULL, (jobject) client_data, "action", "(Z)V", state);
    if ((*env)->ExceptionOccurred(env)) {
        (*env)->ExceptionDescribe(env);
        (*env)->ExceptionClear(env);
    }
}

/*
 * Class:     sun_awt_motif_MCheckboxPeer
 * Method:    create
 * Signature: (Lsun/awt/motif/MComponentPeer;)V
 */
JNIEXPORT void JNICALL Java_sun_awt_motif_MCheckboxPeer_create
  (JNIEnv * env, jobject this, jobject parent)
{
    jobject target;
    struct ComponentData *bdata;
    struct ComponentData *wdata;
    char *clabel;
#define MAX_ARGC 10
    Arg args[MAX_ARGC];
    Cardinal argc;
    jobject label;
    XmString mfstr = NULL;
    jobject font = awtJNI_GetFont(env, this);
    jboolean isMultiFont = awtJNI_IsMultiFont(env, font);
    jobject globalRef = awtJNI_CreateAndSetGlobalRef(env, this);
    AwtGraphicsConfigDataPtr adata;
    XmFontList fontlist = NULL;
    Dimension height;
    Boolean labelIsEmpty = FALSE;

    AWT_LOCK();

    if (JNU_IsNull(env, parent)) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        AWT_UNLOCK();

        return;
    }
    target = (*env)->GetObjectField(env, this, mComponentPeerIDs.target);
    wdata = (struct ComponentData *)
      JNU_GetLongFieldAsPtr(env, parent, mComponentPeerIDs.pData);

    if (JNU_IsNull(env, target) || wdata == NULL) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        AWT_UNLOCK();

        return;
    }
    bdata = ZALLOC(ComponentData);
    if (bdata == NULL) {
        JNU_ThrowOutOfMemoryError(env, "OutOfMemoryError");
        AWT_UNLOCK();

        return;
    }
    JNU_SetLongFieldFromPtr(env, this, mComponentPeerIDs.pData, bdata);

    adata = copyGraphicsConfigToPeer(env, this);

    argc = 0;
    XtSetArg(args[argc], XmNrecomputeSize, False);
    argc++;
    XtSetArg(args[argc], XmNvisibleWhenOff, True);
    argc++;
    XtSetArg(args[argc], XmNtraversalOn, True);
    argc++;
    XtSetArg(args[argc], XmNspacing, 0);
    argc++;
    XtSetArg(args[argc], XmNuserData, (XtPointer) globalRef);
    argc++;
    XtSetArg (args[argc], XmNscreen,
              ScreenOfDisplay(awt_display,
                              adata->awt_visInfo.screen));
    argc++;

    label = (*env)->GetObjectField(env, target, checkboxIDs.label);

    // fix for 4383735.
    // If the label is empty we need to set the indicator size
    // proportional to the size of the font.
    // kdm@sparc.spb.su
    if (JNU_IsNull(env, label) || ((*env)->GetStringLength(env, label) == 0)) {
        labelIsEmpty = TRUE;
        if (!JNU_IsNull(env, font)) {
            mfstr = XmStringCreateLocalized(" ");
            if (mfstr != NULL) {
                fontlist = awtJNI_GetFontList(env, font);
                if (fontlist != NULL) {
                    height = XmStringHeight(fontlist, mfstr);
                    XtSetArg(args[argc], XmNindicatorSize, height);
                    argc++;
                    XmFontListFree(fontlist);
                    fontlist = NULL;
                }
                XmStringFree(mfstr);
                mfstr = NULL;
            }
        }
    }

    if (isMultiFont) {
        /*
         * We don't use makeCString() function here.
         * We create Motif multi-font compound string to display
         * unicode on the platform which is not spporting unicode.
         */
        if (labelIsEmpty) {
            mfstr = XmStringCreateLocalized("");
        } else {
            mfstr = awtJNI_MakeMultiFontString(env, label, font);
        }

        XtSetArg(args[argc], XmNlabelString, mfstr);
        argc++;

        DASSERT(!(argc > MAX_ARGC));
        bdata->widget = XmCreateToggleButton(wdata->widget, "", args, argc);

        if (mfstr != NULL) {
            XmStringFree(mfstr);
            mfstr = NULL;
        }
    } else {
        if (labelIsEmpty) {
            clabel = emptyString;
        } else {
            clabel = (char *) JNU_GetStringPlatformChars(env, label, NULL);

            if (clabel == NULL) {        /* Exception? */
                AWT_UNLOCK();
                return;
            }
        }

        DASSERT(!(argc > MAX_ARGC));
        bdata->widget = XmCreateToggleButton(wdata->widget, clabel, args, argc);

        if (clabel != emptyString) {
            JNU_ReleaseStringPlatformChars(env, label, (const char *) clabel);;
        }
    }

    XtAddCallback(bdata->widget,
                  XmNvalueChangedCallback,
                  (XtCallbackProc) Toggle_callback,
                  (XtPointer) globalRef);

    XtSetMappedWhenManaged(bdata->widget, False);
    XtManageChild(bdata->widget);

    AWT_UNLOCK();
}

/*
 * Class:     sun_awt_motif_MCheckboxPeer
 * Method:    setLabel
 * Signature: (Ljava/lang/String;)V
 */
JNIEXPORT void JNICALL Java_sun_awt_motif_MCheckboxPeer_setLabel
  (JNIEnv * env, jobject this, jstring label)
{
    struct ComponentData *wdata;
    char *clabel;
    XmString xim;
    jobject font;

    AWT_LOCK();

    wdata = (struct ComponentData *)
      JNU_GetLongFieldAsPtr(env, this, mComponentPeerIDs.pData);
    if (wdata == NULL) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        AWT_UNLOCK();
        return;
    }
    if (JNU_IsNull(env, label) || ((*env)->GetStringLength(env, label) == 0)) {
        xim = XmStringCreateLocalized("");
    } else {
        font = awtJNI_GetFont(env, this);

        if (awtJNI_IsMultiFont(env, font)) {
            xim = awtJNI_MakeMultiFontString(env, label, font);
        } else {
            clabel = (char *) JNU_GetStringPlatformChars(env, label, NULL);

            if (clabel == NULL) {
                AWT_UNLOCK();
                return;
            }
            xim = XmStringCreate(clabel, "labelFont");

            JNU_ReleaseStringPlatformChars(env, label, (const char *) clabel);;
        }
    }

    XtVaSetValues(wdata->widget, XmNlabelString, xim, NULL);
    XmStringFree(xim);
    AWT_FLUSH_UNLOCK();
}

/*
 * Class:     sun_awt_motif_MCheckboxPeer
 * Method:    pSetState
 * Signature: (Z)V
 */
JNIEXPORT void JNICALL Java_sun_awt_motif_MCheckboxPeer_pSetState
  (JNIEnv * env, jobject this, jboolean state)
{
    struct ComponentData *bdata;

    AWT_LOCK();

    bdata = (struct ComponentData *)
      JNU_GetLongFieldAsPtr(env, this, mComponentPeerIDs.pData);

    if (bdata == NULL || bdata->widget == NULL) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        AWT_UNLOCK();
        return;
    }
    XtVaSetValues(bdata->widget, XmNset, (Boolean) state, NULL);
    AWT_FLUSH_UNLOCK();
}

/*
 * Class:     sun_awt_motif_MCheckboxPeer
 * Method:    pGetState
 * Signature: ()Z
 */
JNIEXPORT jboolean JNICALL Java_sun_awt_motif_MCheckboxPeer_pGetState
  (JNIEnv * env, jobject this)
{
    struct ComponentData *bdata;
    Boolean               state;

    AWT_LOCK();

    bdata = (struct ComponentData *)
        JNU_GetLongFieldAsPtr(env,this,mComponentPeerIDs.pData);

    if (bdata == NULL || bdata->widget == NULL) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        AWT_UNLOCK();
        return JNI_FALSE;
    }
    XtVaGetValues(bdata->widget, XmNset, &state, NULL);
    AWT_FLUSH_UNLOCK();
    return ((state) ? JNI_TRUE : JNI_FALSE);
}


/*
 * Class:     sun_awt_motif_MCheckboxPeer
 * Method:    setCheckboxGroup
 * Signature: (Ljava/awt/CheckboxGroup;)V
 */
JNIEXPORT void JNICALL Java_sun_awt_motif_MCheckboxPeer_setCheckboxGroup
  (JNIEnv * env, jobject this, jobject group)
{
    struct ComponentData *bdata;

    AWT_LOCK();

    bdata = (struct ComponentData *)
        JNU_GetLongFieldAsPtr(env, this, mComponentPeerIDs.pData);

    if (bdata == NULL || bdata->widget == NULL) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        AWT_UNLOCK();
        return;
    }
    if (JNU_IsNull(env, group)) {
        XtVaSetValues(bdata->widget,
                      XmNindicatorType, XmN_OF_MANY,
                      NULL);
    } else {
        XtVaSetValues(bdata->widget,
                      XmNindicatorType, XmONE_OF_MANY,
                      NULL);
    }

    AWT_FLUSH_UNLOCK();
}

/*
 * Class:     sun_awt_motif_MCheckboxPeer
 * Method:    getIndicatorSize
 * Signature: (V)I
 */
JNIEXPORT jint JNICALL Java_sun_awt_motif_MCheckboxPeer_getIndicatorSize
  (JNIEnv * env, jobject this)
{
    struct ComponentData *wdata;
    Dimension size;

    AWT_LOCK();

    wdata = (struct ComponentData *)
        JNU_GetLongFieldAsPtr(env, this, mComponentPeerIDs.pData);

    if (wdata == NULL || wdata->widget == NULL) {
        JNU_ThrowNullPointerException(env, "Null pData");
        AWT_UNLOCK();
        return 0;
    }
    XtVaGetValues(wdata->widget,
                  XmNindicatorSize, &size,
                  NULL);

    AWT_FLUSH_UNLOCK();

    return size;
}

/*
 * Class:     sun_awt_motif_MCheckboxPeer
 * Method:    getSpacing
 * Signature: (V)I
 */
JNIEXPORT jint JNICALL Java_sun_awt_motif_MCheckboxPeer_getSpacing
  (JNIEnv * env, jobject this)
{
    struct ComponentData *wdata;
    Dimension dim;

    AWT_LOCK();

    wdata = (struct ComponentData *)
        JNU_GetLongFieldAsPtr(env, this, mComponentPeerIDs.pData);

    if (wdata == NULL || wdata->widget == NULL) {
        JNU_ThrowNullPointerException(env, "Null pData");
        AWT_UNLOCK();
        return 0;
    }
    XtVaGetValues(wdata->widget,
                  XmNspacing, &dim,
                  NULL);

    AWT_FLUSH_UNLOCK();

    return dim;
}
