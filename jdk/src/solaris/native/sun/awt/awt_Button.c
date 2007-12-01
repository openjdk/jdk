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

#include <jni.h>
#include <jni_util.h>
#include "multi_font.h"

#include "awt_Component.h"

extern struct MComponentPeerIDs mComponentPeerIDs;
extern AwtGraphicsConfigDataPtr
    copyGraphicsConfigToPeer(JNIEnv *env, jobject this);

/*
 * When the -jni switch is thrown, these headers can be deleted.
 */
#include "java_awt_Button.h"
#include "sun_awt_motif_MButtonPeer.h"
#include "sun_awt_motif_MComponentPeer.h"

/* fieldIDs for Button fields that may be accessed from C */
static struct ButtonIDs {
    jfieldID label;
} buttonIDs;

static char emptyString[] = "";


/*
 * Class:     java_awt_Button
 * Method:    initIDs
 * Signature: ()V
 */

/* This function gets called from the static initializer for Button.java
   to initialize the fieldIDs for fields that may be accessed from C */

JNIEXPORT void JNICALL
Java_java_awt_Button_initIDs
  (JNIEnv *env, jclass cls)
{
    buttonIDs.label =
      (*env)->GetFieldID(env, cls, "label", "Ljava/lang/String;");
}

/*
 * client_data is MButtonPeer instance
 */
static void
Button_callback (Widget w,
                 XtPointer client_data,
                 XmPushButtonCallbackStruct * call_data)
{
    JNIEnv *env = (JNIEnv *)JNU_GetEnv(jvm, JNI_VERSION_1_2);
    ConvertEventTimeAndModifiers converted;

    awt_util_convertEventTimeAndModifiers(call_data->event, &converted);

    JNU_CallMethodByName(env, NULL, (jobject)client_data, "action", "(JI)V",
                         converted.when, converted.modifiers);
    if ((*env)->ExceptionOccurred (env)) {
        (*env)->ExceptionDescribe (env);
        (*env)->ExceptionClear (env);
    }
}

/*
 * Class:     sun_awt_motif_MButtonPeer
 * Method:    create
 * Signature: (Lsun/awt/motif/MComponentPeer;)V
 */
JNIEXPORT void JNICALL Java_sun_awt_motif_MButtonPeer_create
  (JNIEnv * env, jobject this, jobject parent)
{
    jobject target;
    jobject label;
    struct ComponentData *cdata;
    struct ComponentData *wdata;
    char *clabel;
    Pixel bg;
    XmString mfstr = NULL;
    jobject globalRef = awtJNI_CreateAndSetGlobalRef (env, this);
    jobject font = awtJNI_GetFont (env, this);
    jboolean IsMultiFont = awtJNI_IsMultiFont (env, font);
    AwtGraphicsConfigDataPtr adata;

    AWT_LOCK ();

    if (JNU_IsNull (env, parent)) {
        JNU_ThrowNullPointerException (env, "NullPointerException");
        AWT_UNLOCK ();

        return;
    }
    target = (*env)->GetObjectField (env, this, mComponentPeerIDs.target);
    wdata = (struct ComponentData *)
      JNU_GetLongFieldAsPtr(env, parent, mComponentPeerIDs.pData);

    if (JNU_IsNull (env, target) || wdata == NULL) {
        JNU_ThrowNullPointerException (env, "NullPointerException");
        AWT_UNLOCK ();

        return;
    }
    cdata = ZALLOC (ComponentData);
    if (cdata == NULL) {
        JNU_ThrowOutOfMemoryError (env, "OutOfMemoryError");
        AWT_UNLOCK ();
        return;
    }
    JNU_SetLongFieldFromPtr(env, this, mComponentPeerIDs.pData, cdata);

    adata = copyGraphicsConfigToPeer(env, this);

    XtVaGetValues (wdata->widget, XmNbackground, &bg, NULL);

    label =
      (*env)->GetObjectField (env, target, buttonIDs.label);

    if (IsMultiFont) {
        /*
         * We don't use makeCString() function here.
         * We create Motif multi-font compound string to display
         * unicode on the platform which is not spporting unicode.
         */
        if (JNU_IsNull (env, label) || ((*env)->GetStringLength (env, label) == 0)) {
            mfstr = XmStringCreateLocalized ("");
        } else {
            mfstr = awtJNI_MakeMultiFontString (env, label, font);
        }

        cdata->widget = XtVaCreateManagedWidget
            ("", xmPushButtonWidgetClass,
             wdata->widget,
             XmNlabelString, mfstr,
             XmNrecomputeSize, False,
             XmNbackground, bg,
             XmNhighlightOnEnter, False,
             XmNshowAsDefault, 0,
             XmNdefaultButtonShadowThickness, 0,
             XmNmarginTop, 0,
             XmNmarginBottom, 0,
             XmNmarginLeft, 0,
             XmNmarginRight, 0,
             XmNuserData, (XtPointer) globalRef,
             XmNscreen, ScreenOfDisplay(awt_display,
                                        adata->awt_visInfo.screen),
             NULL);
        if (mfstr != NULL) {
            XmStringFree(mfstr);
            mfstr = NULL;
        }

    } else {
        if (JNU_IsNull (env, label)) {
            clabel = emptyString;
        } else {
            clabel = (char *) JNU_GetStringPlatformChars (env, label, NULL);
            if (clabel == NULL) {        /* Exception? */
                AWT_UNLOCK ();
                return;
            }
        }

        cdata->widget = XtVaCreateManagedWidget
            (clabel, xmPushButtonWidgetClass,
             wdata->widget,
             XmNrecomputeSize, False,
             XmNbackground, bg,
             XmNhighlightOnEnter, False,
             XmNshowAsDefault, 0,
             XmNdefaultButtonShadowThickness, 0,
             XmNmarginTop, 0,
             XmNmarginBottom, 0,
             XmNmarginLeft, 0,
             XmNmarginRight, 0,
             XmNuserData, (XtPointer) globalRef,
             XmNscreen, ScreenOfDisplay(awt_display,
                                        adata->awt_visInfo.screen),
             NULL);

        if (clabel != emptyString) {
            JNU_ReleaseStringPlatformChars (env, label, (const char *) clabel);;
        }
    }

    XtSetMappedWhenManaged (cdata->widget, False);
    XtAddCallback (cdata->widget,
                   XmNactivateCallback,
                   (XtCallbackProc) Button_callback,
                   (XtPointer) globalRef);

    AWT_UNLOCK ();
}

/*
 * Class:     sun_awt_motif_MButtonPeer
 * Method:    setLabel
 * Signature: (Ljava/lang/String;)V
 */
JNIEXPORT void JNICALL Java_sun_awt_motif_MButtonPeer_setLabel
  (JNIEnv * env, jobject this, jstring label)
{
    struct ComponentData *wdata;
    char *clabel;
    XmString xim;

    AWT_LOCK ();

    wdata = (struct ComponentData *)
      JNU_GetLongFieldAsPtr(env, this, mComponentPeerIDs.pData);

    if (wdata == NULL) {
        JNU_ThrowNullPointerException (env, "NullPointerException");
        AWT_UNLOCK ();
        return;
    }
    if (JNU_IsNull (env, label) || ((*env)->GetStringLength (env, label) == 0)) {
        xim = XmStringCreateLocalized ("");
    } else {
        jobject font = awtJNI_GetFont (env, this);

        if (awtJNI_IsMultiFont (env, font)) {
            xim = awtJNI_MakeMultiFontString (env, label, font);
        } else {
            if (JNU_IsNull (env, label)) {
                clabel = emptyString;
            } else {
                clabel = (char *) JNU_GetStringPlatformChars (env, label, NULL);

                if (clabel == NULL) {      /* Exception? */
                    AWT_UNLOCK ();
                    return;
                }
            }

            xim = XmStringCreate (clabel, "labelFont");

            if (clabel != emptyString) {
                JNU_ReleaseStringPlatformChars (env, label, (const char *) clabel);;
            }
        }
    }

    XtVaSetValues (wdata->widget, XmNlabelString, xim, NULL);
    XmStringFree (xim);
    AWT_FLUSH_UNLOCK ();
}
