/*
 * Copyright 1996-2004 Sun Microsystems, Inc.  All Rights Reserved.
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
#include <Xm/MenuShell.h>
#include <Xm/RowColumn.h>
#include "color.h"
#include "java_awt_PopupMenu.h"
#include "java_awt_Component.h"
#include "java_awt_Event.h"
#include "sun_awt_motif_MPopupMenuPeer.h"
#include "sun_awt_motif_MComponentPeer.h"

#include "awt_PopupMenu.h"
#include "awt_MenuItem.h"
#include "awt_Component.h"
#include "awt_MenuComponent.h"
#include "awt_Menu.h"
#include "awt_Event.h"

#include "multi_font.h"
#include <jni.h>
#include <jni_util.h>

extern struct MMenuItemPeerIDs mMenuItemPeerIDs;
extern struct MComponentPeerIDs mComponentPeerIDs;
extern struct MenuComponentIDs menuComponentIDs;
extern struct MenuItemIDs menuItemIDs;
extern struct MenuIDs menuIDs;
extern AwtGraphicsConfigDataPtr
getGraphicsConfigFromComponentPeer(JNIEnv *env, jobject parentPeer);
extern Boolean keyboardGrabbed;
Boolean poppingDown = False;

struct MPopupMenuPeerIDs mPopupMenuPeerIDs;

static Widget activePopup;

void removePopupMenus() {
    if (activePopup != NULL &&
        XtIsManaged(activePopup))
    {
            XtUnmanageChild(activePopup);
            activePopup = NULL;
    }
}

Boolean awtMenuIsActive() {
    return ((activePopup != NULL) || (awt_util_focusIsOnMenu(awt_display)));
}

struct ClientDataStruct {
    struct ComponentData *wdata;
    jobject mMenuItemPeerIDs;
};

/*
 * Class:     sun_awt_motif_MPopupMenuPeer
 * Method:    initIDs
 * Signature: ()V
 */

/* This function gets called from the static initializer for
   MPopupMenuPeer.java to initialize the methodIDs for methods that may
   be accessed from C */

JNIEXPORT void JNICALL Java_sun_awt_motif_MPopupMenuPeer_initIDs
  (JNIEnv *env, jclass cls)
{
    mPopupMenuPeerIDs.destroyNativeWidgetAfterGettingTreeLock =
        (*env)->GetMethodID(env, cls,
                            "destroyNativeWidgetAfterGettingTreeLock", "()V");
}

extern Boolean skipNextNotifyWhileGrabbed;

static void
Popup_popUpCB(Widget w, XtPointer client_data, XtPointer calldata)
{
    skipNextNotifyWhileGrabbed = True;
}
/*
 * client_data is MPopupMenuPeer instance
 */
static void
Popup_popdownCB(Widget w, XtPointer client_data, XtPointer calldata)
{
    JNIEnv *env = (JNIEnv *)JNU_GetEnv(jvm, JNI_VERSION_1_2);
    jobject target = NULL;

    /*
     * Fix for 4394847. Due to the race keyboard remains grabbed after menu
     * was disposed. Clear the grab status here instead of processOneEvent.
     */
    poppingDown = True;
    keyboardGrabbed = False;
    skipNextNotifyWhileGrabbed = True;

    XtRemoveCallback(w, XtNpopdownCallback,
                     Popup_popdownCB, (XtPointer) client_data);

    (*env)->CallVoidMethod(env, (jobject) client_data,
        mPopupMenuPeerIDs.destroyNativeWidgetAfterGettingTreeLock);

    if ((*env)->ExceptionOccurred(env)) {
        (*env)->ExceptionDescribe(env);
        (*env)->ExceptionClear(env);
    }
}

/*
 * Class:     sun_awt_motif_MPopupMenuPeer
 * Method:    createMenu
 * Signature: (Lsun/awt/motif/MComponentPeer;)V
 */
JNIEXPORT void JNICALL Java_sun_awt_motif_MPopupMenuPeer_createMenu
  (JNIEnv *env, jobject this, jobject parent)
{
    struct ComponentData *wdata;
    struct MenuData *mdata;
    struct FontData *fdata;
    char *ctitle = NULL;
    int32_t argc;
#define MAX_ARGC 10
    Arg args[MAX_ARGC];
    Pixel bg;
    Pixel fg;
    XmFontList fontlist = NULL;
    XmString mfstr = NULL;
    jobject font;
    jobject target;
    jobject targetFont;
    jobject label;
    jboolean IsMultiFont;
    jboolean tearOff;
    jobject globalRef = (*env)->NewGlobalRef(env, this);
    AwtGraphicsConfigDataPtr adata;

    JNU_SetLongFieldFromPtr(env, this,
                            mMenuItemPeerIDs.jniGlobalRef, globalRef);


    AWT_LOCK();

    if (JNU_IsNull(env, parent)) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        AWT_UNLOCK();
        return;
    }
    target =
      (*env)->GetObjectField(env, this, mMenuItemPeerIDs.target);
    wdata = (struct ComponentData *)
      JNU_GetLongFieldAsPtr(env, parent, mComponentPeerIDs.pData);

    if (wdata == NULL || JNU_IsNull(env, target)) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        AWT_UNLOCK();
        return;
    }
    mdata = ZALLOC(MenuData);
    if (mdata == NULL) {
        JNU_ThrowOutOfMemoryError(env, "OutOfMemoryError");
        AWT_UNLOCK();
        return;
    }
    JNU_SetLongFieldFromPtr(env, this, mMenuItemPeerIDs.pData, mdata);

    adata = getGraphicsConfigFromComponentPeer(env, parent);

    /*
     * Why are these different?
     */
    font = JNU_CallMethodByName(env, NULL, target, "getFont_NoClientCode",
                                "()Ljava/awt/Font;").l;
    targetFont =
      (*env)->GetObjectField(env, target, menuComponentIDs.font);
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

    XtVaGetValues(wdata->widget, XmNbackground, &bg, NULL);
    XtVaGetValues(wdata->widget, XmNforeground, &fg, NULL);

    argc = 0;
    XtSetArg(args[argc], XmNbackground, bg);
    argc++;
    XtSetArg(args[argc], XmNforeground, fg);
    argc++;
    tearOff = (*env)->GetBooleanField(env, target, menuIDs.tearOff);
    if (tearOff) {
        XtSetArg(args[argc], XmNtearOffModel, XmTEAR_OFF_ENABLED);
        argc++;
    }
    if (!JNU_IsNull(env, targetFont)
        && (fdata = awtJNI_GetFontData(env, targetFont, NULL)) != NULL) {
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

    XtSetArg(args[argc], XmNvisual, adata->awt_visInfo.visual);
    argc++;
    XtSetArg (args[argc], XmNscreen,
              ScreenOfDisplay(awt_display,
                              adata->awt_visInfo.screen));
    argc++;

    if (IsMultiFont) {
        DASSERT(!(argc > MAX_ARGC));
        mdata->itemData.comp.widget = XmCreatePopupMenu(wdata->widget,
                                                        "",
                                                        args,
                                                        argc);
    } else {
        DASSERT(!(argc > MAX_ARGC));
        mdata->itemData.comp.widget = XmCreatePopupMenu(wdata->widget,
                                                        ctitle,
                                                        args,
                                                        argc);
    }
    awt_addMenuWidget(mdata->itemData.comp.widget);

    /*
     * Fix for bug 4180147 -
     * screen can be frozen when interacting with MB3 using AWT on Motif
     */
    XtUngrabButton(wdata->widget, AnyButton, AnyModifier);
    XtUngrabPointer(wdata->widget, CurrentTime);

    /* fix for bug #4169155: Popup menus get a leading separator on Motif
       system.
       Additional check that title string is not empty*/
    if (!JNU_IsNull(env, label) &&
        (*env)->GetStringUTFLength( env, label) != (jsize)0 ) {
        if (IsMultiFont) {
            XtVaCreateManagedWidget("",
                                    xmLabelWidgetClass,
                                    mdata->itemData.comp.widget,
                                    XmNfontList, fontlist,
                                    XmNlabelString, mfstr,
                                    XmNbackground, bg,
                                    XmNforeground, fg,
                                    XmNhighlightColor, fg,
                                    NULL);
            XmStringFree(mfstr);
        } else {
            XmString xmstr = XmStringCreateLocalized(ctitle);

            XtVaCreateManagedWidget(ctitle,
                                    xmLabelWidgetClass,
                                    mdata->itemData.comp.widget,
                                    XmNlabelString, xmstr,
                                    XmNbackground, bg,
                                    XmNforeground, fg,
                                    XmNhighlightColor, fg,
                                    NULL);
            XmStringFree(xmstr);
            JNU_ReleaseStringPlatformChars(env, label, (const char *) ctitle);
        }
        /* Create separator */
        XtVaCreateManagedWidget("",
                                xmSeparatorWidgetClass,
                                mdata->itemData.comp.widget,
                                XmNbackground, bg,
                                XmNforeground, fg,
                                NULL);
    }
    if (tearOff) {
        Widget tearOffWidget = XmGetTearOffControl(mdata->itemData.comp.widget);

        XtVaSetValues(tearOffWidget,
                      XmNbackground, bg,
                      XmNforeground, fg,
                      XmNhighlightColor, fg,
                      NULL);
    }
    mdata->comp.widget = mdata->itemData.comp.widget;

    if (!JNU_IsNull(env, targetFont)) {
        XmFontListFree(fontlist);
    }
    XtSetSensitive(mdata->comp.widget,
      ((*env)->GetBooleanField(env, target, menuItemIDs.enabled) ?
       True : False));

    AWT_UNLOCK();
}

/*
 * Class:     sun_awt_motif_MPopupMenuPeer
 * Method:    pShow
 * Signature: (Ljava/awt/Event;IILsun/awt/motif/MComponentPeer;)V
 */
JNIEXPORT void JNICALL Java_sun_awt_motif_MPopupMenuPeer_pShow
  (JNIEnv *env, jobject this, jobject event, jint x, jint y, jobject origin)
{
    struct MenuData *mdata;
    struct ComponentData *wdata;
    XButtonEvent *bevent;
    XButtonEvent *newEvent = NULL;
    void *data;

    AWT_LOCK();

    mdata = (struct MenuData *)
      JNU_GetLongFieldAsPtr(env, this, mMenuItemPeerIDs.pData);
    if (mdata == NULL || JNU_IsNull(env, event)) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        AWT_UNLOCK();
        return;
    }

    wdata = (struct ComponentData *)
        JNU_GetLongFieldAsPtr(env, origin, mComponentPeerIDs.pData);

    if ( wdata == NULL || wdata->widget == NULL ) { /* 425598 */
        JNU_ThrowNullPointerException(env, "NullPointerException"); /* 425598 */
        AWT_UNLOCK(); /* 425598 */
        return; /* 425598 */
    } /* 425598 */

    if (!XtIsRealized(wdata->widget)) {
        JNU_ThrowInternalError(env, "widget not visible on screen");
        AWT_UNLOCK();
        return;
    }

    /*
     * Fix for BugTraq ID 4186663 - Pural PopupMenus appear at the same time.
     * If another popup is currently visible hide it.
     */
    if (activePopup != NULL &&
        activePopup != mdata->comp.widget &&
        XtIsObject(activePopup) &&
        XtIsManaged(activePopup)) {
            removePopupMenus();
    }

    /* If the raw x event is not available, then we must use an unfortunate
     * round-trip call to XTranslateCoordiates to get the root coordinates.
     */
    data = JNU_GetLongFieldAsPtr(env, event, eventIDs.data);
    if (data == NULL || ((XEvent *) data)->type != ButtonPress) {
        int32_t rx, ry;
        Window root, win;

        root = RootWindowOfScreen(XtScreen(wdata->widget));
        XTranslateCoordinates(awt_display,
                              XtWindow(wdata->widget),
                              root,
                              (int32_t) x, (int32_t) y,
                              &rx, &ry,
                              &win);
        /*
                printf("translated coords %d,%d to root %d,%d\n", x, y, rx, ry);
        */

        newEvent = (XButtonEvent *) malloc(sizeof(XButtonEvent));
        newEvent->type = ButtonPress;
        newEvent->display = awt_display;
        newEvent->window = XtWindow(wdata->widget);
        newEvent->time = awt_util_getCurrentServerTime();
        newEvent->x = (int32_t) x;
        newEvent->y = (int32_t) y;
        newEvent->x_root = rx;
        newEvent->y_root = ry;
        bevent = newEvent;

    } else {
        bevent = (XButtonEvent *) data;
    }

    XtAddCallback(XtParent(mdata->comp.widget), XtNpopdownCallback,
                  Popup_popdownCB,
                  (XtPointer)
                  JNU_GetLongFieldAsPtr(env, this,
                                        mMenuItemPeerIDs.jniGlobalRef));

    XtAddCallback(XtParent(mdata->comp.widget), XtNpopupCallback,
                  Popup_popUpCB,
                  (XtPointer)
                  JNU_GetLongFieldAsPtr(env, this,
                                        mMenuItemPeerIDs.jniGlobalRef));


    XmMenuPosition(mdata->comp.widget, bevent);
    XtManageChild(mdata->comp.widget);

    /*
     * Fix for BugTraq ID 4186663 - Pural PopupMenus appear at the same time.
     * Store the pointer to the currently showing popup.
     */
    activePopup = mdata->comp.widget;

    if (newEvent) {
        free((void *) newEvent);
    }
    AWT_UNLOCK();
}

/*
 * Class:     sun_awt_motif_MPopupMenuPeer
 * Method:    pDispose
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_sun_awt_motif_MPopupMenuPeer_pDispose
  (JNIEnv *env, jobject this)
{
    struct MenuData *mdata;

    AWT_LOCK();

    mdata = (struct MenuData *)
      JNU_GetLongFieldAsPtr(env, this, mMenuItemPeerIDs.pData);

    if (mdata == NULL) {
        AWT_UNLOCK();
        return;
    }
    /*
     * Fix for BugTraq ID 4186663 - Pural PopupMenus appear at the same time.
     * Clear the pointer to the currently showing popup.
     */
    if (activePopup == mdata->comp.widget) {
        activePopup = NULL;
    }
    awt_delMenuWidget(mdata->itemData.comp.widget);
    XtUnmanageChild(mdata->comp.widget);
    awt_util_consumeAllXEvents(mdata->comp.widget);
    XtDestroyWidget(mdata->comp.widget);
    free((void *) mdata);
    (*env)->SetLongField(env, this, mMenuItemPeerIDs.pData, (jlong)0);

    awtJNI_DeleteGlobalMenuRef(env, this);

    poppingDown = False;
    AWT_UNLOCK();
}
