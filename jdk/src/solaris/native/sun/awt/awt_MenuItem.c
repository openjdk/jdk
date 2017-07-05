/*
 * Copyright 1995-2004 Sun Microsystems, Inc.  All Rights Reserved.
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
#include <Xm/Separator.h>
#include "java_awt_MenuItem.h"
#include "sun_awt_motif_MMenuItemPeer.h"
#include "sun_awt_motif_MCheckboxMenuItemPeer.h"
#include "java_awt_Menu.h"
#include "sun_awt_motif_MMenuPeer.h"

#include "awt_MenuComponent.h"
#include "awt_MenuItem.h"

#include "multi_font.h"
#include <jni.h>
#include <jni_util.h>
#include <jlong.h>

extern struct MenuComponentIDs menuComponentIDs;

/* fieldIDs for MenuItem fields that may be accessed from C */
struct MenuItemIDs menuItemIDs;

/*
 * Class:     java_awt_MenuItem
 * Method:    initIDs
 * Signature: ()V
 */

/* This function gets called from the static initializer for
   MenuItem.java to initialize the fieldIDs for fields that may
   be accessed from C */

JNIEXPORT void JNICALL Java_java_awt_MenuItem_initIDs
  (JNIEnv *env, jclass cls)
{
    menuItemIDs.label =
      (*env)->GetFieldID(env, cls, "label", "Ljava/lang/String;");
    menuItemIDs.enabled =
      (*env)->GetFieldID(env, cls, "enabled", "Z");
    menuItemIDs.shortcut =
      (*env)->GetFieldID(env, cls, "shortcut", "Ljava/awt/MenuShortcut;");
}

/* fieldIDs for MMenuItemPeer fields that may be accessed from C */
struct MMenuItemPeerIDs mMenuItemPeerIDs;

/*
 * Class:     sun_awt_motif_MMenuItemPeer
 * Method:    initIDs
 * Signature: ()V
 */

/* This function gets called from the static initializer for
   MMenuItemPeer.java to initialize the fieldIDs for fields that may
   be accessed from C */

JNIEXPORT void JNICALL Java_sun_awt_motif_MMenuItemPeer_initIDs
  (JNIEnv *env, jclass cls)
{
    mMenuItemPeerIDs.target =
      (*env)->GetFieldID(env, cls, "target", "Ljava/awt/MenuItem;");
    mMenuItemPeerIDs.pData = (*env)->GetFieldID(env, cls, "pData", "J");
    mMenuItemPeerIDs.isCheckbox =
      (*env)->GetFieldID(env, cls, "isCheckbox", "Z");
    mMenuItemPeerIDs.jniGlobalRef =
      (*env)->GetFieldID(env, cls, "jniGlobalRef", "J");
}

/*
 * Class:     sun_awt_motif_MMenuItemPeer
 * Method:    getParent_NoClientCode
 * Signature: (Ljava/awt/MenuComponent;)Ljava/awt/MenuContainer;
 *
 * Gets the MenuContainer parent of this object, without executing client
 * code (e.g., no code in subclasses will be executed).
 */
JNIEXPORT jobject JNICALL Java_sun_awt_motif_MMenuItemPeer_getParent_1NoClientCode
  (JNIEnv *env, jclass thisClass, jobject menuComponent)
{
    jobject parent = NULL;

    /* getParent is actually getParent_NoClientCode() */
    parent = (*env)->CallObjectMethod(
                        env,menuComponent,menuComponentIDs.getParent);
    DASSERT(!((*env)->ExceptionOccurred(env)));
    return parent;
}

/*
 *  client_data is MMenuItemPeer instance pointer
 */
static void
MenuItem_selected(Widget w, XtPointer client_data, XmAnyCallbackStruct * s)
{
    JNIEnv *env = (JNIEnv *)JNU_GetEnv(jvm, JNI_VERSION_1_2);
    jobject this = (jobject) client_data;
    ConvertEventTimeAndModifiers converted;

    awt_util_convertEventTimeAndModifiers(s->event, &converted);

    if ((*env)->GetBooleanField(env, this, mMenuItemPeerIDs.isCheckbox)) {
        jboolean state;
        struct MenuItemData *mdata;

        mdata = (struct MenuItemData *)
          JNU_GetLongFieldAsPtr(env, this, mMenuItemPeerIDs.pData);

        if (mdata != NULL) {
            XtVaGetValues(mdata->comp.widget, XmNset, &state, NULL);

            JNU_CallMethodByName(env, NULL, this
                                 ,"action"
                                 ,"(JIZ)V"
                                 ,converted.when, converted.modifiers,
                                 state);
            if ((*env)->ExceptionOccurred(env)) {
                (*env)->ExceptionDescribe(env);
                (*env)->ExceptionClear(env);
            }
        }
    } else {
        JNU_CallMethodByName(env, NULL, this, "action", "(JI)V",
                             converted.when, converted.modifiers);
        if ((*env)->ExceptionOccurred(env)) {
            (*env)->ExceptionDescribe(env);
            (*env)->ExceptionClear(env);
        }
    }
}

/*
 * Class:     sun_awt_motif_MMenuItemPeer
 * Method:    createMenuItem
 * Signature: (Lsun/awt/motif/MMenuPeer;)V
 *
 * ASSUMES: This function is never called by a privileged thread
 */
JNIEXPORT void JNICALL Java_sun_awt_motif_MMenuItemPeer_createMenuItem(
JNIEnv *env, jobject this, jobject parent)
{
    int32_t argc;
#define MAX_ARGC 20
    Arg args[MAX_ARGC];
    char *clabel;
    struct MenuData *menuData;
    struct MenuItemData *mdata;
    struct FontData *fdata;
    Pixel bg;
    Pixel fg;
    XmFontList fontlist = NULL;
    jobject target;
    jobject targetFont;
    XmString mfstr = NULL;
    XmString shortcut_str = NULL;
    XmString str = NULL;
    jobject font;
    jobject shortcut;
    jboolean IsMultiFont;
    jboolean isCheckbox;
    jstring label;
    jobject globalRef = (*env)->NewGlobalRef(env, this);
    const jchar *unicodeLabel = NULL;
    jsize unicodeLabelLen = 0;
    jboolean isCopy = JNI_FALSE;

    // We call client code on this thread, so it must *NOT* be privileged
    DASSERT(!awt_currentThreadIsPrivileged(env));

    JNU_SetLongFieldFromPtr(env, this, mMenuItemPeerIDs.jniGlobalRef,
                            globalRef);

    fdata = NULL;

    fflush(stderr);
    target =
      (*env)->GetObjectField(env, this, mMenuItemPeerIDs.target);
    if (JNU_IsNull(env, target)) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        return;
    }
    font = JNU_CallMethodByName(env, NULL, target, "getFont_NoClientCode",
                                "()Ljava/awt/Font;").l;

    if (JNU_IsNull(env, parent)) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        return;
    }
    AWT_LOCK();
    menuData = (struct MenuData *)
      JNU_GetLongFieldAsPtr(env, parent, mMenuItemPeerIDs.pData);

    targetFont =
      (*env)->GetObjectField(env, target, menuComponentIDs.font);
    if (!JNU_IsNull(env, targetFont) &&
        (fdata = awtJNI_GetFontData(env, targetFont, NULL)) != NULL) {
        IsMultiFont = awtJNI_IsMultiFont(env, targetFont);
    } else {
        IsMultiFont = awtJNI_IsMultiFont(env, font);
    }

    label = (*env)->GetObjectField(env, target, menuItemIDs.label);
    if (JNU_IsNull(env, label) || ((*env)->GetStringLength (env, label) == 0)) {
        mfstr = XmStringCreateLocalized("");
        clabel = "";
    } else {
        if (IsMultiFont) {
            mfstr = awtJNI_MakeMultiFontString(env, label, font);
        } else {
            mfstr = XmStringCreateLocalized("");
        }
        clabel = (char *) JNU_GetStringPlatformChars(env, label, NULL);
    }

    mdata = ZALLOC(MenuItemData);
    JNU_SetLongFieldFromPtr(env, this, mMenuItemPeerIDs.pData, mdata);

    argc = 0;
    XtSetArg(args[argc], XmNbackground, &bg);
    argc++;
    XtSetArg(args[argc], XmNforeground, &fg);
    argc++;
    XtGetValues(menuData->itemData.comp.widget, args, argc);

    argc = 0;
    XtSetArg(args[argc], XmNbackground, bg);
    argc++;
    XtSetArg(args[argc], XmNforeground, fg);
    argc++;

    /* check if the label is "-" but don't use strcmp(clabel, "-") because
     * the high-order bytes in the unicode characters are not present in
     * the C string (bugid 4099695)
     */
    if (!JNU_IsNull(env, label)) {
        unicodeLabel = (*env)->GetStringChars(env, label, &isCopy);
        unicodeLabelLen = (*env)->GetStringLength(env, label);
    }
    if ((unicodeLabel != NULL) && (unicodeLabel[0] == '-') &&
        (unicodeLabelLen == 1)) {
        DASSERT(!(argc > MAX_ARGC));
        mdata->comp.widget = XmCreateSeparator(menuData->itemData.comp.widget,
                                               "", args, argc);
    } else {
        if (IsMultiFont) {
            XtSetArg(args[argc], XmNlabelString, mfstr);
        } else {
            str = XmStringCreate(clabel, XmSTRING_DEFAULT_CHARSET);
            XtSetArg(args[argc], XmNlabelString, str);
        }
        argc++;

        shortcut =
          (*env)->GetObjectField(env, target, menuItemIDs.shortcut);
        if (!JNU_IsNull(env, shortcut)) {
            jstring shortcutText;
            char *text = "";

            shortcutText = JNU_CallMethodByName(env, NULL, shortcut,
                                                "toString",
                                                "()Ljava/lang/String;").l;

            if (!JNU_IsNull(env, shortcutText)) {
                text = (char *) JNU_GetStringPlatformChars(env, shortcutText, NULL);
            }
            shortcut_str = XmStringCreate(text, XmSTRING_DEFAULT_CHARSET);
            XtSetArg(args[argc], XmNacceleratorText, shortcut_str);

            argc++;

            if (!JNU_IsNull(env, shortcutText)) {
                JNU_ReleaseStringPlatformChars(env, shortcutText, (const char *) text);
            }
        }
        if (!JNU_IsNull(env, targetFont) && (fdata != NULL)) {
            if (IsMultiFont) {
                fontlist = awtJNI_GetFontList(env, targetFont);
            } else {
                fontlist = XmFontListCreate(fdata->xfont, "labelFont");
            }
            XtSetArg(args[argc], XmNfontList, fontlist);
            argc++;
        } else {
            if (IsMultiFont) {
                fontlist = awtJNI_GetFontList(env, font);
                XtSetArg(args[argc], XmNfontList, fontlist);
                argc++;
            }
        }

        isCheckbox =
          (*env)->GetBooleanField(env, this, mMenuItemPeerIDs.isCheckbox);
        if (isCheckbox) {
            /* Fix for 4090493 */
            if (IsMultiFont) {
                /* FontData that correspond to XmNfontList we just set */
                struct FontData *fdataForIndSize;
                Dimension indSize;
                if (!JNU_IsNull(env, targetFont) && (fdata != NULL)) {
                    fdataForIndSize = fdata;
                }
                else {
                fdataForIndSize = awtJNI_GetFontData(env, font, NULL);
                }
                indSize = awt_adjustIndicatorSizeForMenu(awt_computeIndicatorSize(fdataForIndSize));
                if (indSize != MOTIF_XmINVALID_DIMENSION) {
                    XtSetArg(args[argc], XmNindicatorSize, indSize); argc++;
                }
            }
            /* End of fix for 4090493 */
            XtSetArg(args[argc], XmNset, False);
            argc++;
            XtSetArg(args[argc], XmNvisibleWhenOff, True);
            argc++;

            DASSERT(!(argc > MAX_ARGC));
            mdata->comp.widget = XmCreateToggleButton(menuData->itemData.comp.widget,
                                                      clabel,
                                                      args,
                                                      argc);
        } else {
            DASSERT(!(argc > MAX_ARGC));
            mdata->comp.widget = XmCreatePushButton(menuData->itemData.comp.widget,
                                                    clabel,
                                                    args,
                                                    argc);
        }
        XtAddCallback(mdata->comp.widget,
                      ((isCheckbox) ? XmNvalueChangedCallback : XmNactivateCallback),
                      (XtCallbackProc) MenuItem_selected,
                      (XtPointer) globalRef);

        XtSetSensitive(mdata->comp.widget,
          (*env)->GetBooleanField(env, target, menuItemIDs.enabled) ?
                       True : False);


        if (!JNU_IsNull(env, targetFont)) {
            XmFontListFree(fontlist);
        }
    }

    if (clabel && (clabel != "")) {
        JNU_ReleaseStringPlatformChars(env, label, clabel);
    }

    /*
     * Free up resources after we have created the widget
     */
    if (mfstr != NULL) {
      XmStringFree(mfstr);
      mfstr = NULL;
    }
    if (str) {
      XmStringFree(str);
      str = NULL;
    }
    if (shortcut_str) {
      XmStringFree(shortcut_str);
      shortcut_str = NULL;
    }
    if (isCopy == JNI_TRUE) {
        (*env)->ReleaseStringChars(env, label, unicodeLabel);
    }

    XtManageChild(mdata->comp.widget);
    AWT_UNLOCK();
}

/*
 * Class:     sun_awt_motif_MMenuItemPeer
 * Method:    pSetLabel
 * Signature: (Ljava/lang/String;)V
 */
JNIEXPORT void JNICALL Java_sun_awt_motif_MMenuItemPeer_pSetLabel
(JNIEnv *env, jobject this, jstring label)
{
    struct ComponentData *wdata;
    char *clabel;
    XmString xim;

    AWT_LOCK();
    wdata = (struct ComponentData *)
        JNU_GetLongFieldAsPtr(env, this, mMenuItemPeerIDs.pData);
    if (wdata == NULL) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        AWT_UNLOCK();
        return;
    }
    if (JNU_IsNull(env, label) || ((*env)->GetStringLength (env, label) == 0)) {
        xim = XmStringCreateLocalized("");
    } else {
        jobject font;
        jobject target;

        target = (*env)->GetObjectField(env, this, mMenuItemPeerIDs.target);
        if (JNU_IsNull(env, target)) {
            JNU_ThrowNullPointerException(env, "NullPointerException");
            AWT_UNLOCK();
            return;
        }
        font = JNU_CallMethodByName(env, NULL, target, "getFont_NoClientCode",
                                    "()Ljava/awt/Font;").l;

        if (awtJNI_IsMultiFont(env, font)) {
            xim = awtJNI_MakeMultiFontString(env, label, font);
        } else {
            clabel = (char *) JNU_GetStringPlatformChars(env, label, NULL);
            xim = XmStringCreate(clabel, "labelFont");
            JNU_ReleaseStringPlatformChars(env, label, clabel);
        }
    }
    XtUnmanageChild(wdata->widget);
    XtVaSetValues(wdata->widget, XmNlabelString, xim, NULL);
    XtManageChild(wdata->widget);
    XmStringFree(xim);
    AWT_UNLOCK();
}


/*
 * Class:     sun_awt_motif_MMenuItemPeer
 * Method:    pSetShortCut
 * Signature: (Ljava/lang/String;)V
 */
JNIEXPORT void JNICALL Java_sun_awt_motif_MMenuItemPeer_pSetShortcut
(JNIEnv *env, jobject this, jstring shortcut)
{
    struct ComponentData *wdata;
    char *cshortcut;
    XmString xim;


    AWT_LOCK();
    wdata = (struct ComponentData *)
        JNU_GetLongFieldAsPtr(env, this, mMenuItemPeerIDs.pData);
    if (wdata == NULL) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        AWT_UNLOCK();
        return;
    }
    if (JNU_IsNull(env, shortcut)) {
        xim = XmStringCreateLocalized("");
    } else {
        jobject font;
        jobject target;

        target = (*env)->GetObjectField(env, this, mMenuItemPeerIDs.target);
        if (JNU_IsNull(env, target)) {
            JNU_ThrowNullPointerException(env, "NullPointerException");
            AWT_UNLOCK();
            return;
        }
        font = JNU_CallMethodByName(env, NULL, target, "getFont_NoClientCode",
                                    "()Ljava/awt/Font;").l;

        if (awtJNI_IsMultiFont(env, font)) {
            xim = awtJNI_MakeMultiFontString(env, shortcut, font);
        } else {
            cshortcut = (char *) JNU_GetStringPlatformChars(env, shortcut, NULL);
            xim = XmStringCreate(cshortcut, "labelFont");
            JNU_ReleaseStringPlatformChars(env, shortcut, cshortcut);
        }
    }

    XtUnmanageChild(wdata->widget);
    XtVaSetValues(wdata->widget, XmNacceleratorText, xim, NULL);
    XtManageChild(wdata->widget);
    XmStringFree(xim);
    AWT_UNLOCK();
}


/*
 * Class:     sun_awt_motif_MMenuItemPeer
 * Method:    pEnable
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_sun_awt_motif_MMenuItemPeer_pEnable
(JNIEnv *env, jobject this)
{
    struct MenuItemData *mdata;

    AWT_LOCK();

    mdata = (struct MenuItemData *)
        JNU_GetLongFieldAsPtr(env, this, mMenuItemPeerIDs.pData);

    if (mdata == NULL) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        AWT_UNLOCK();
        return;
    }
    XtSetSensitive(mdata->comp.widget, True);
    AWT_UNLOCK();
}

/*
 * Class:     sun_awt_motif_MMenuItemPeer
 * Method:    pDisable
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_sun_awt_motif_MMenuItemPeer_pDisable
(JNIEnv *env, jobject this)
{
    struct MenuItemData *mdata;

    AWT_LOCK();

    mdata = (struct MenuItemData *)
        JNU_GetLongFieldAsPtr(env, this, mMenuItemPeerIDs.pData);

    if (mdata == NULL) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        AWT_UNLOCK();
        return;
    }
    XtSetSensitive(mdata->comp.widget, False);
    AWT_UNLOCK();
}

/*
 * Class:     sun_awt_motif_MMenuItemPeer
 * Method:    pDispose
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_sun_awt_motif_MMenuItemPeer_pDispose
(JNIEnv *env, jobject this)
{
    struct MenuItemData *mdata;
    Widget parent;
    Boolean isParentManaged = False;

    AWT_LOCK();

    mdata = (struct MenuItemData *)
        JNU_GetLongFieldAsPtr(env, this, mMenuItemPeerIDs.pData);

    if (mdata != NULL) {
        /* Fix for 4280561:Workspace freezes, does not respond to mouse clicks
        **
        ** this really helps a lot of Fujitsu problems, take down a popup
        ** menu when removing items, on windows you could never get here, since
        ** the show() of a popup menu puts it in a menu loop where further
        ** events are processed in that loop, its like a modal dialog show,
        ** in that it dosn't return till it comes down.
        ** in X - future xevents will be dispatched immeadiatly, but some
        ** may be still waiting on the java queue - which can cause them to be
        ** dispatched out of order (sometimes hanging system !)
        */
        /* note: should realy only take down if XtParent(mdata->comp.widget)
        ** is the activePopup (in awt_PopupMenu.c) but ...
        */
        {
            removePopupMenus();
        }
        XtUnmanageChild(mdata->comp.widget);
        awt_util_consumeAllXEvents(mdata->comp.widget);

        parent = XtParent(mdata->comp.widget);
        if (parent != NULL && XtIsManaged(parent)) {
            isParentManaged = True;
            XtUnmanageChild(parent);
        }

        XtDestroyWidget(mdata->comp.widget);

        if (isParentManaged) {
            XtManageChild(parent);
        }

        free((void *) mdata);
        (*env)->SetLongField(env, this, mMenuItemPeerIDs.pData, (jlong)0);
        awtJNI_DeleteGlobalMenuRef(env, this);
    }
    AWT_UNLOCK();
}

/*
 * Class:     sun_awt_motif_MCheckboxMenuItemPeer
 * Method:    pSetState
 * Signature: (Z)V
 */
JNIEXPORT void JNICALL Java_sun_awt_motif_MCheckboxMenuItemPeer_pSetState
  (JNIEnv *env, jobject this, jboolean state)
{
    struct MenuItemData *mdata;

    AWT_LOCK();

    mdata = (struct MenuItemData *)
        JNU_GetLongFieldAsPtr(env, this, mMenuItemPeerIDs.pData);

    if (mdata == NULL) {
        JNU_ThrowNullPointerException(env, "menuitem data is null");
        AWT_UNLOCK();
        return;
    }
    XtVaSetValues(mdata->comp.widget, XmNset, (Boolean)state, NULL);
    AWT_UNLOCK();
}

/*
 * Class:     sun_awt_motif_MCheckboxMenuItemPeer
 * Method:    getState
 * Signature: ()Z
 */
JNIEXPORT jboolean JNICALL Java_sun_awt_motif_MCheckboxMenuItemPeer_getState
  (JNIEnv *env, jobject this)
{
    struct MenuItemData *mdata;
    Boolean             state;

    AWT_LOCK();

    mdata = (struct MenuItemData *)
      (*env)->GetLongField(env, this, mMenuItemPeerIDs.pData);

    if (mdata == NULL) {
        JNU_ThrowNullPointerException(env, "menuitem data is null");
        AWT_UNLOCK();
        return JNI_FALSE;
    }
    XtVaGetValues(mdata->comp.widget, XmNset, &state, NULL);
    AWT_UNLOCK();
    return ((state) ? JNI_TRUE : JNI_FALSE);
}
