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
#include "java_awt_Color.h"
#include "java_awt_Font.h"
#include "java_awt_Label.h"
#include "sun_awt_motif_MLabelPeer.h"
#include "sun_awt_motif_MComponentPeer.h"

#include "awt_Component.h"

#include "multi_font.h"
#include <jni.h>
#include <jni_util.h>

extern struct MComponentPeerIDs mComponentPeerIDs;
extern AwtGraphicsConfigDataPtr
    copyGraphicsConfigToPeer(JNIEnv *env, jobject this);

static char emptyString[] = "";


/*
 * Class:     sun_awt_motif_MLabelPeer
 * Method:    create
 * Signature: (Lsun/awt/motif/MComponentPeer;)V
 */
JNIEXPORT void JNICALL Java_sun_awt_motif_MLabelPeer_create
  (JNIEnv *env, jobject this, jobject parent)
{
    struct ComponentData *cdata;
    struct ComponentData *wdata;
    jobject target;
    jobject globalRef = awtJNI_CreateAndSetGlobalRef(env, this);
    AwtGraphicsConfigDataPtr adata;
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
    cdata = ZALLOC(ComponentData);
    if (cdata == NULL) {
        JNU_ThrowOutOfMemoryError(env, "OutOfMemoryError");
        AWT_UNLOCK();
        return;
    }
    JNU_SetLongFieldFromPtr(env, this, mComponentPeerIDs.pData,cdata);

    adata = copyGraphicsConfigToPeer(env, this);

    cdata->widget = XtVaCreateManagedWidget("",
                                            xmLabelWidgetClass, wdata->widget,
                                            XmNhighlightThickness, 0,
                                            XmNalignment, XmALIGNMENT_BEGINNING,
                                            XmNrecomputeSize, False,
                                            XmNuserData, (XtPointer) globalRef,
                                            XmNtraversalOn, True,
                                            XmNscreen,
                                            ScreenOfDisplay(awt_display,
                                               adata->awt_visInfo.screen),
                                            XmNfontList, getMotifFontList(),
                                            NULL);
    XtSetMappedWhenManaged(cdata->widget, False);
    AWT_UNLOCK();
}

/*
 * Class:     sun_awt_motif_MLabelPeer
 * Method:    setText
 * Signature: (Ljava/lang/String;)V
 */
JNIEXPORT void JNICALL Java_sun_awt_motif_MLabelPeer_setText
  (JNIEnv *env, jobject this, jstring label)
{
    char *clabel = NULL;
    char *clabelEnd;
    struct ComponentData *cdata;
    XmString xim = NULL;
    jobject font;
    Boolean isMultiFont;

    AWT_LOCK();

    font = awtJNI_GetFont(env, this);
    isMultiFont = awtJNI_IsMultiFont(env, font);

    cdata = (struct ComponentData *)
        JNU_GetLongFieldAsPtr(env,this,mComponentPeerIDs.pData);
    if (cdata == NULL || cdata->widget == NULL) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        AWT_UNLOCK();
        return;
    }
    if (JNU_IsNull(env, label)) {
        clabel = emptyString;
    } else {
        if (isMultiFont) {
            if ((*env)->GetStringLength(env, label) <= 0) {
                xim = XmStringCreateLocalized("");
            } else {
                xim = awtJNI_MakeMultiFontString(env, label, font);
            }
        } else {
            clabel = (char *) JNU_GetStringPlatformChars(env, label, NULL);

            /* scan for any \n's and terminate the string at that point */
            clabelEnd = strchr(clabel, '\n');
            if (clabelEnd != NULL) {
                *clabelEnd = '\0';
            }
        }
    }

    if (!isMultiFont) {
        xim = XmStringCreate(clabel, "labelFont");
    }
    XtVaSetValues(cdata->widget, XmNlabelString, xim, NULL);

    if (!isMultiFont) {
        /* Must test for "" too! */
        if (clabel != NULL && (*clabel != '\0')) {
            JNU_ReleaseStringPlatformChars(env, label, (const char *) clabel);
        }
    }
    XmStringFree(xim);
    AWT_FLUSH_UNLOCK();
}

/*
 * Class:     sun_awt_motif_MLabelPeer
 * Method:    setAlignment
 * Signature: (I)V
 */
JNIEXPORT void JNICALL Java_sun_awt_motif_MLabelPeer_setAlignment
  (JNIEnv *env, jobject this, jint alignment)
{
    struct ComponentData *cdata;

    AWT_LOCK();

    cdata = (struct ComponentData *)
        JNU_GetLongFieldAsPtr(env,this,mComponentPeerIDs.pData);
    if (cdata == NULL || cdata->widget == NULL) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        AWT_UNLOCK();
        return;
    }
    switch (alignment) {
        case java_awt_Label_LEFT:
            XtVaSetValues(cdata->widget,
                          XmNalignment, XmALIGNMENT_BEGINNING,
                          NULL);
            break;

        case java_awt_Label_CENTER:
            XtVaSetValues(cdata->widget,
                          XmNalignment, XmALIGNMENT_CENTER,
                          NULL);
            break;

        case java_awt_Label_RIGHT:
            XtVaSetValues(cdata->widget,
                          XmNalignment, XmALIGNMENT_END,
                          NULL);
            break;

        default:
            break;
    }

    AWT_FLUSH_UNLOCK();
}
