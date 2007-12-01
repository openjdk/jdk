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
#include "color.h"
#include "java_awt_Menu.h"
#include "sun_awt_motif_MMenuPeer.h"
#include "java_awt_MenuBar.h"
#include "sun_awt_motif_MMenuBarPeer.h"

#include "awt_MenuBar.h"
#include "awt_MenuComponent.h"
#include "awt_MenuItem.h"
#include "awt_Menu.h"

#include "multi_font.h"
#include <jni.h>
#include <jni_util.h>
#include <Xm/CascadeBP.h>

extern struct MenuComponentIDs menuComponentIDs;
extern struct MenuItemIDs menuItemIDs;
extern struct MMenuItemPeerIDs mMenuItemPeerIDs;
extern struct MMenuBarPeerIDs mMenuBarPeerIDs;

struct MenuIDs menuIDs;

/*
 * Class:     java_awt_Menu
 * Method:    initIDs
 * Signature: ()V
 */

/* This function gets called from the static initializer for
   Menu.java to initialize the fieldIDs for fields that may
   be accessed from C */

JNIEXPORT void JNICALL Java_java_awt_Menu_initIDs
  (JNIEnv *env, jclass cls)
{
    menuIDs.tearOff = (*env)->GetFieldID(env, cls, "tearOff", "Z");
    menuIDs.isHelpMenu = (*env)->GetFieldID(env, cls, "isHelpMenu", "Z");
}

/*
 * Fix for Bug Traq 4251941 - segfault after double tear-off and close
 * Removes the lost callback from menu item on tear-off control re-creation.
 * Only for internal use, to be used from awtTearOffActivatedCallback
 */
static void awtTearOffShellDestroy(Widget widget, XtPointer closure, XtPointer data) {
    if (widget != NULL ) {
        XtSetKeyboardFocus(widget, NULL);
    }
}

/*
 * Fix for Bug Traq 4251941 - segfault after double tear-off and close
 * This callback is added to menu after the creation.
 * It adds the destroy callback awtTearOffShellDestroy to remove the lost focus callback on destroy
 */
static void awtTearOffActivatedCallback(Widget widget, XtPointer closure, XtPointer data) {
    Widget shell;
    shell = XtParent(widget);
    if (shell != NULL && XtClass(shell) == transientShellWidgetClass) {
        XtAddCallback(shell, XtNdestroyCallback, awtTearOffShellDestroy, widget);
    }
}

extern Boolean skipNextNotifyWhileGrabbed;

static void
Menu_popDownCB(Widget w, XtPointer client_data, XtPointer calldata)
{
    skipNextNotifyWhileGrabbed = True;
}



/*
 * this is a MMenuPeer instance
 */
static void
awtJNI_CreateMenu(JNIEnv * env, jobject this, Widget menuParent)
{
    int32_t argc;
#define MAX_ARGC 10
    Arg args[MAX_ARGC];
    char *ctitle = NULL;
    struct MenuData *mdata;
    struct FontData *fdata;
    Pixel bg;
    Pixel fg;
    XmFontList fontlist = NULL;
    Widget tearOff;
    XmString mfstr = NULL;
    XmString str = NULL;
    jobject target;
    jobject targetFont;
    jobject label;
    jobject font;
    jboolean IsMultiFont;
    jboolean isTearOff;

    /* perhaps this is unncessary, if awtJNI_CreateMenu is only called
     * from a native method.
     */
    if ((*env)->PushLocalFrame(env, (jint)16) < (jint)0) {
        return;
    }

    fdata = NULL;

    target = (*env)->GetObjectField(env, this, mMenuItemPeerIDs.target);
    if (JNU_IsNull(env, target)) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        (*env)->PopLocalFrame(env, NULL);
        return;
    }
    font = JNU_CallMethodByName(env, NULL, target, "getFont_NoClientCode",
                                "()Ljava/awt/Font;").l;

    mdata = ZALLOC(MenuData);
    JNU_SetLongFieldFromPtr(env, this, mMenuItemPeerIDs.pData, mdata);
    if (mdata == NULL) {
        JNU_ThrowOutOfMemoryError(env, "OutOfMemoryError");
        (*env)->PopLocalFrame(env, NULL);
        return;
    }
    targetFont = (*env)->GetObjectField(env, target, menuComponentIDs.font);
    if (!JNU_IsNull(env, targetFont) &&
        (fdata = awtJNI_GetFontData(env, targetFont, NULL)) != NULL) {
        IsMultiFont = awtJNI_IsMultiFont(env, targetFont);
    } else {
        IsMultiFont = awtJNI_IsMultiFont(env, font);
    }

    label = (*env)->GetObjectField(env, target, menuItemIDs.label);
    if (JNU_IsNull(env, label)) {
        mfstr = XmStringCreateLocalized("");
        ctitle = "";
    } else {
        if (IsMultiFont) {
            mfstr = awtJNI_MakeMultiFontString(env, label, font);
        } else {
            ctitle = (char *) JNU_GetStringPlatformChars(env, label, NULL);
        }
    }

    XtVaGetValues(menuParent, XmNbackground, &bg, NULL);
    XtVaGetValues(menuParent, XmNforeground, &fg, NULL);

    argc = 0;
    XtSetArg(args[argc], XmNbackground, bg);
    argc++;
    XtSetArg(args[argc], XmNforeground, fg);
    argc++;

    XtSetArg(args[argc], XmNlabelFontList,   getMotifFontList());
    argc++;
    XtSetArg(args[argc], XmNbuttonFontList,  getMotifFontList());
    argc++;

    isTearOff = (*env)->GetBooleanField(env, target, menuIDs.tearOff);

    if (isTearOff) {
        XtSetArg(args[argc], XmNtearOffModel, XmTEAR_OFF_ENABLED);
        argc++;
    }

    if (IsMultiFont) {
        DASSERT(!(argc > MAX_ARGC));
        mdata->itemData.comp.widget = XmCreatePulldownMenu(menuParent,
                                                           "",
                                                           args,
                                                           argc);
    } else {
        DASSERT(!(argc > MAX_ARGC));
        mdata->itemData.comp.widget = XmCreatePulldownMenu(menuParent,
                                                           ctitle,
                                                           args,
                                                           argc);
    }
    awt_addMenuWidget(mdata->itemData.comp.widget);

    if (isTearOff) {
        tearOff = XmGetTearOffControl(mdata->itemData.comp.widget);
        XtVaSetValues(tearOff,
                      XmNbackground, bg,
                      XmNforeground, fg,
                      XmNhighlightColor, fg,
                      NULL);
        XtAddCallback(mdata->itemData.comp.widget, XmNtearOffMenuActivateCallback,
                      awtTearOffActivatedCallback, NULL);
    }
    argc = 0;
    XtSetArg(args[argc], XmNsubMenuId, mdata->itemData.comp.widget);
    argc++;

    if (IsMultiFont) {
        XtSetArg(args[argc], XmNlabelString, mfstr);
    } else {
        str = XmStringCreate(ctitle, XmSTRING_DEFAULT_CHARSET);
        XtSetArg(args[argc], XmNlabelString, str);
    }
    argc++;
    XtSetArg(args[argc], XmNbackground, bg);
    argc++;
    XtSetArg(args[argc], XmNforeground, fg);
    argc++;

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

    if (IsMultiFont) {
        DASSERT(!(argc > MAX_ARGC));
        mdata->comp.widget = XmCreateCascadeButton(menuParent, "", args, argc);
    } else {
        DASSERT(!(argc > MAX_ARGC));
        mdata->comp.widget = XmCreateCascadeButton(menuParent, ctitle, args, argc);
    }

    if ((*env)->GetBooleanField(env, target, menuIDs.isHelpMenu)) {
        XtVaSetValues(menuParent,
                      XmNmenuHelpWidget, mdata->comp.widget,
                      NULL);
    }

    /**
     * Add callback to MenuShell of the menu so we know when
     * menu pops down. mdata->itemData.comp.widget is RowColumn,
     * its parent - MenuShell.
     */
    XtAddCallback(XtParent(mdata->itemData.comp.widget), XtNpopdownCallback,
                  Menu_popDownCB,
                  (XtPointer)
                  JNU_GetLongFieldAsPtr(env, this,
                                        mMenuItemPeerIDs.jniGlobalRef));

    /*
     * Free resources
     */
    if (!JNU_IsNull(env, targetFont)) {
        XmFontListFree(fontlist);
    }

    if (mfstr != NULL) {
      XmStringFree(mfstr);
      mfstr = NULL;
    }

    if (str) {
      XmStringFree(str);
      str = NULL;
    }

    XtManageChild(mdata->comp.widget);
    XtSetSensitive(mdata->comp.widget,
                   (*env)->GetBooleanField(env, target, menuItemIDs.enabled) ?
                   True : False);

    if (ctitle != NULL && ctitle != "") {
        JNU_ReleaseStringPlatformChars(env, label, (const char *) ctitle);
    }
    (*env)->PopLocalFrame(env, NULL);
}


/*
 * Class:     sun_awt_motif_MMenuPeer
 * Method:    createMenu
 * Signature: (Lsun/awt/motif/MMenuBarPeer;)V
 */
JNIEXPORT void JNICALL Java_sun_awt_motif_MMenuPeer_createMenu
  (JNIEnv *env, jobject this, jobject parent)
{
    struct ComponentData *mbdata;
    AwtGraphicsConfigDataPtr adata;

    AWT_LOCK();
    if (JNU_IsNull(env, parent)) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        AWT_UNLOCK();
        return;
    }
    mbdata = (struct ComponentData *)
        JNU_GetLongFieldAsPtr(env, parent, mMenuBarPeerIDs.pData);
    if (mbdata == NULL) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        AWT_UNLOCK();
        return;
    }

    awtJNI_CreateMenu(env, this, mbdata->widget);

    AWT_UNLOCK();
}

/*
 * Class:     sun_awt_motif_MMenuPeer
 * Method:    createSubMenu
 * Signature: (Lsun/awt/motif/MMenuPeer;)V
 */
JNIEXPORT void JNICALL Java_sun_awt_motif_MMenuPeer_createSubMenu
(JNIEnv *env, jobject this, jobject parent)
{
    struct MenuData *mpdata;
    AwtGraphicsConfigDataPtr adata;

    AWT_LOCK();
    if (JNU_IsNull(env, parent)) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        AWT_UNLOCK();
        return;
    }
    mpdata = (struct MenuData *)
        JNU_GetLongFieldAsPtr(env, parent, mMenuItemPeerIDs.pData);
    if (mpdata == NULL) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        AWT_UNLOCK();
        return;
    }

    awtJNI_CreateMenu(env, this, mpdata->itemData.comp.widget);

    AWT_UNLOCK();
}

/*
 * Class:     sun_awt_motif_MMenuPeer
 * Method:    pDispose
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_sun_awt_motif_MMenuPeer_pDispose
  (JNIEnv *env, jobject this)
{
    struct MenuData *mdata;
    Widget parent;
    Boolean isParentManaged = False;

    AWT_LOCK();

    mdata = (struct MenuData *)
        JNU_GetLongFieldAsPtr(env, this, mMenuItemPeerIDs.pData);
    if (mdata == NULL) {
        AWT_UNLOCK();
        return;
    }
    awt_delMenuWidget(mdata->itemData.comp.widget);
    XtUnmanageChild(mdata->comp.widget);
    awt_util_consumeAllXEvents(mdata->itemData.comp.widget);
    awt_util_consumeAllXEvents(mdata->comp.widget);

    parent = XtParent(mdata->itemData.comp.widget);
    if (parent != NULL && XtIsManaged(parent)) {
        isParentManaged = True;
        XtUnmanageChild(parent);
    }

    XtDestroyWidget(mdata->itemData.comp.widget);

    if (isParentManaged) {
        XtManageChild(parent);
    }

    XtDestroyWidget(mdata->comp.widget);
    free((void *) mdata);
    AWT_UNLOCK();
}
