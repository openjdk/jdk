/*
 * Copyright 1999-2007 Sun Microsystems, Inc.  All Rights Reserved.
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

#include <X11/Shell.h>
#include <Xm/VendorS.h>
#include <Xm/Form.h>
#include <Xm/DialogS.h>
#include <Xm/AtomMgr.h>
#include <Xm/Protocols.h>
#include <Xm/MenuShell.h>
#include <Xm/MwmUtil.h>
#include "VDrawingArea.h"

#ifdef DEBUG
#  include <X11/Xmu/Editres.h>
#endif

#include <jni.h>
#include <jni_util.h>

/* JNI headers */
#include "java_awt_Color.h"
#include "java_awt_Component.h"
#include "java_awt_Dialog.h"
#include "java_awt_Font.h"
#include "java_awt_Frame.h"
#include "java_awt_Image.h"
#include "java_awt_Insets.h"
#include "java_awt_Insets.h"
#include "java_awt_MenuBar.h"
#include "java_awt_Window.h"
#include "java_awt_event_FocusEvent.h"
#include "java_awt_TrayIcon.h"
#include "sun_awt_EmbeddedFrame.h"
#include "sun_awt_motif_MComponentPeer.h"
#include "sun_awt_motif_MDialogPeer.h"
#include "sun_awt_motif_MEmbeddedFramePeer.h"
#include "sun_awt_motif_MFramePeer.h"
#include "sun_awt_motif_MMenuBarPeer.h"
#include "sun_awt_motif_MWindowPeer.h"

/* JNI field and method ids */
#include "awt_Component.h"
#include "awt_GraphicsEnv.h"
#include "awt_Insets.h"
#include "awt_MenuBar.h"
#include "awt_Window.h"
#include "awt_KeyboardFocusManager.h"
#include "awt_MToolkit.h"
#include "awt_Plugin.h"

#include "color.h"
#include "canvas.h"
#include "awt_util.h"
#include "img_util.h"
#include "awt_wm.h"
#include "awt_util.h"
#include "awt_xembed.h"


#ifdef __linux__
void adjustStatusWindow(Widget shell);
#endif
/* For the moment only InputMethodWindow is taking advantage of
** the posibility for different decor styles
** values could be passed are the MWM_DECOR defines
** for the moment we are full on or full off.
*/
#define AWT_NO_DECOR    0x0
#define AWT_FULL_DECOR  MWM_DECOR_ALL

static void reshape(JNIEnv *env, jobject this, struct FrameData *wdata,
                    jint x, jint y, jint w, jint h, Boolean setXY);
Widget findTopLevelByShell(Widget widget);

extern EmbeddedFrame *theEmbeddedFrameList;
extern struct ComponentIDs componentIDs;
extern struct MMenuBarPeerIDs mMenuBarPeerIDs;
extern struct MComponentPeerIDs mComponentPeerIDs;
struct WindowIDs windowIDs;
struct MWindowPeerIDs mWindowPeerIDs;
extern struct InsetsIDs insetsIDs;
extern struct X11GraphicsConfigIDs x11GraphicsConfigIDs;
extern struct KeyboardFocusManagerIDs keyboardFocusManagerIDs;
extern struct X11GraphicsDeviceIDs x11GraphicsDeviceIDs;

#ifndef NOMODALFIX
extern Boolean awt_isModal();
extern Boolean awt_isWidgetModal(Widget w);
extern void awt_shellPoppedUp(Widget shell, XtPointer c, XtPointer d);
extern void awt_shellPoppedDown(Widget shell, XtPointer c, XtPointer d);
#endif //NOMODALFIX

static jclass inputMethodWindowClass = NULL;

static int32_t globalTopGuess    = 0;
static int32_t globalLeftGuess   = 0;
static int32_t globalBottomGuess = 0;
static int32_t globalRightGuess  = 0;


// Atom used for otlogenniy top-level disposal
static Atom _XA_JAVA_DISPOSE_PROPERTY_ATOM = 0;

/*
 * Fix for bug 4141361
 *
 * We keep a linked list of the FrameData information for
 * every top level window.
 */
struct FrameDataList {
    struct FrameData* wdata;
    struct FrameDataList* next;
};

static struct FrameDataList* allTopLevel = NULL;

extern void checkNewXineramaScreen(JNIEnv* env, jobject peer,
                                   struct FrameData* wdata,
                                   int32_t newX, int32_t newY,
                                   int32_t newWidth, int32_t newHeight);

// Returns false if this Window is non-focusable
// or its nearest decorated parent is non-focusable.
Boolean isFocusableWindowByPeer(JNIEnv * env, jobject peer) {
    jobject target, decoratedParent;
    struct FrameData *wdata;
    Boolean focusable;

    wdata = (struct FrameData *)JNU_GetLongFieldAsPtr(env, peer, mComponentPeerIDs.pData);
    DASSERT(wdata != NULL);

    target = (*env)->GetObjectField(env, peer, mComponentPeerIDs.target);
    DASSERT(target != NULL);

    decoratedParent = getOwningFrameOrDialog(target, env);
    (*env)->DeleteLocalRef(env, target);

    if (decoratedParent == NULL) {
        return wdata->isFocusableWindow;
    } else {
        jobject parentPeer = (*env)->GetObjectField(env, decoratedParent, componentIDs.peer);
        DASSERT(parentPeer != NULL);
        focusable = wdata->isFocusableWindow && isFocusableWindowByPeer(env, parentPeer);

        (*env)->DeleteLocalRef(env, decoratedParent);
        (*env)->DeleteLocalRef(env, parentPeer);
    }
    return focusable;
}

// Returns false if this shell's Java Window is non-focusable
// or its nearest decorated parent is non-focusable.
// Returns true otherwise or if any of parameters is NULL
Boolean isFocusableWindowByShell(JNIEnv* env, Widget shell) {
    Widget toplevel;
    jobject peer;
    Boolean focusable;

    DASSERT(shell != NULL && XtIsShell(shell));
    if (shell == NULL) return True;
    if (!XtIsShell(shell)) return True;

    toplevel = findTopLevelByShell(shell);
    if (toplevel == NULL) {
        return True;
    }
    peer = findPeer(&toplevel);
    DASSERT(peer != NULL);

    if (env == NULL) {
        env = (JNIEnv *)JNU_GetEnv(jvm, JNI_VERSION_1_2);
    }
    return isFocusableWindowByPeer(env, peer);
}


// Returns Shell widget - the parent of this child
Widget getShellWidget(Widget child) {

    while (child != NULL && !XtIsShell(child)) {
        child = XtParent(child);
    }
    return child;
}

// Returns false if the parent shell of this widget is non-focusable Java Window.
// Returns false otherwise.
// Doesn't accept NULL parameters.
Boolean isFocusableComponentTopLevelByWidget(JNIEnv * env, Widget child) {
    Widget shell = NULL;
    shell = getShellWidget(child);
    DASSERT(shell);
    return isFocusableWindowByShell(env, shell);
}


/*
 * Add a new element into the top level window list
 */
void addTopLevel(struct FrameData* wdata) {
    struct FrameDataList* newNode;
    newNode = (struct FrameDataList*)
        malloc(sizeof(struct FrameDataList));
    newNode->wdata = wdata;
    newNode->next = allTopLevel;
    allTopLevel = newNode;
}

/*
 * Remove an element from the top level window list
 * (recursive)
 */
Boolean removeTopLevelR(struct FrameDataList** ptr,
    struct FrameData* wdata) {
    struct FrameDataList* node = *ptr;
    if (node == NULL) {
        return False;
    }
    if (node->wdata == wdata) {
        *ptr = node->next;
        free(node);
        return True;
    }
    return removeTopLevelR(&(node->next), wdata);
}

Boolean removeTopLevel(struct FrameData* wdata) {
    return removeTopLevelR(&allTopLevel, wdata);
}

/*
 * Return the Widget ID of the top level window underneath the
 * mouse pointer.
 */
Widget awt_GetWidgetAtPointer() {
    struct FrameDataList* ptr = allTopLevel;
    Window rootWindow, childWindow, mainWindow;
    int32_t xw, yw, xr, yr;
    uint32_t keys;
    while (ptr != NULL) {
        mainWindow = XtWindow(ptr->wdata->mainWindow);
        XQueryPointer(awt_display, mainWindow,
            &rootWindow, &childWindow, &xr, &yr, &xw, &yw, &keys);
        if (childWindow != None) {
            return ptr->wdata->winData.comp.widget;
        }
        ptr = ptr->next;
    }
    return NULL;
}

Widget findFocusProxy(Widget widget) {
  struct FrameDataList* ptr = allTopLevel;
  for (ptr = allTopLevel; ptr != NULL; ptr = ptr->next) {
    if (ptr->wdata->winData.comp.widget == widget) {
      return ptr->wdata->focusProxy;
    }
  }
  return NULL;
}

Widget findTopLevelByShell(Widget widget) {
  struct FrameDataList* ptr;
  for (ptr = allTopLevel; ptr != NULL; ptr = ptr->next) {
      if (ptr->wdata->winData.shell == widget) {
          return ptr->wdata->winData.comp.widget;
      }
  }
  return NULL;
}

void
awt_Frame_guessInsets(struct FrameData *wdata)
{
    if (wdata->decor == AWT_NO_DECOR ) {
        wdata->top    = wdata->topGuess    = 0;
        wdata->left   = wdata->leftGuess   = 0;
        wdata->bottom = wdata->bottomGuess = 0;
        wdata->right  = wdata->rightGuess  = 0;
        return;
    }

    if (globalTopGuess == 0) {
        char *insets_env;

        if (wdata->top >= 0) {
            /* insets were set on wdata by System Properties */
            globalTopGuess    = wdata->top;
            globalLeftGuess   = wdata->left;
            globalBottomGuess = wdata->bottom;
            globalRightGuess  = wdata->right;
        }
        else switch (awt_wm_getRunningWM()) {
        case ENLIGHTEN_WM:
            globalTopGuess    = 19;
            globalLeftGuess   =  4;
            globalBottomGuess =  4;
            globalRightGuess  =  4;
            break;

        case CDE_WM:
            globalTopGuess    = 28;
            globalLeftGuess   =  6;
            globalBottomGuess =  6;
            globalRightGuess  =  6;
            break;

        case MOTIF_WM:
        case OPENLOOK_WM:
        default:
            globalTopGuess    = 25;
            globalLeftGuess   =  5;
            globalBottomGuess =  5;
            globalRightGuess  =  5;
            break;
        }

        if ((insets_env = getenv("AWT_INSETS")) != NULL) {
            int guess = atoi(insets_env);
            globalTopGuess    = (guess & 0xff00) >> 8;
            globalLeftGuess   = guess & 0x00ff;
            globalBottomGuess = wdata->leftGuess;
            globalRightGuess  = wdata->leftGuess;
        }

        /* don't allow bizarly large insets */
        if ((globalTopGuess > 64) || (globalTopGuess < 0))
            globalTopGuess = 28;
        if ((globalLeftGuess > 32) || (globalLeftGuess < 0))
            globalLeftGuess = 6;
        if ((globalBottomGuess > 32) || (globalBottomGuess < 0))
            globalBottomGuess = 6;
        if ((globalRightGuess > 32) || (globalRightGuess < 0))
            globalRightGuess = 6;
    }

    wdata->top    = wdata->topGuess    = globalTopGuess;
    wdata->left   = wdata->leftGuess   = globalLeftGuess;
    wdata->bottom = wdata->bottomGuess = globalBottomGuess;
    wdata->right  = wdata->rightGuess  = globalRightGuess;
}

/*
 * To keep input method windows floating, maintain a list of all
 * input method windows here.  When some top level window gets
 * activated, moved, or resized, these input method windows need
 * to be brought on top.
 */
static struct FrameDataList* allInputMethodWindow = NULL;

/*
 * Add a new element into the input method window list
 */
void addInputMethodWindow(struct FrameData* wdata) {
    struct FrameDataList* newNode;
    newNode = (struct FrameDataList*)
        malloc(sizeof(struct FrameDataList));
    newNode->wdata = wdata;
    newNode->next = allInputMethodWindow;
    allInputMethodWindow = newNode;
}

/*
 * Remove an element from the top level window list
 * (recursive)
 */
Boolean removeInputMethodWindowR(struct FrameDataList** ptr,
    struct FrameData* wdata) {
    struct FrameDataList* node = *ptr;
    if (node == NULL) {
        return False;
    }
    if (node->wdata == wdata) {
        *ptr = node->next;
        free(node);
        return True;
    }
    return removeInputMethodWindowR(&(node->next), wdata);
}

Boolean removeInputMethodWindow(struct FrameData* wdata) {
    return removeInputMethodWindowR(&allInputMethodWindow, wdata);
}

/*
 * Raise input method windows
 */
void raiseInputMethodWindow(struct FrameData* wdata) {
    struct FrameDataList* node = allInputMethodWindow;

    if (wdata->isInputMethodWindow) {
        return;
    }

    while (node != NULL) {
        XRaiseWindow(awt_display, XtWindow(node->wdata->winData.shell));
        node = node->next;
    }
}

/* fieldIDs for Frame fields that may be accessed from C */
static struct FrameIDs {
    jfieldID resizable;
    jfieldID state;
} frameIDs;

/*
 * Class:     java_awt_Frame
 * Method:    initIDs
 * Signature: ()V
 */

/* This function gets called from the static initializer for Frame.java
   to initialize the fieldIDs for fields that may be accessed from C */
JNIEXPORT void JNICALL
Java_java_awt_Frame_initIDs
  (JNIEnv *env, jclass cls)
{
    frameIDs.resizable = (*env)->GetFieldID(env, cls, "resizable", "Z");
    frameIDs.state = (*env)->GetFieldID(env, cls, "state", "I");
}

/* ******* */
/* Dialogs */
/* ******* */
/* No longer have a need for unique fields for query */
static struct DialogIDs {
    jfieldID modal;
    jfieldID resizable;
} dialogIDs;

JNIEXPORT void JNICALL
Java_java_awt_Dialog_initIDs
  (JNIEnv *env, jclass cls)
{
#if 0
    dialogIDs.modal = (*env)->GetFieldID(env, cls, "modal", "Z");
    dialogIDs.resizable = (*env)->GetFieldID(env, cls, "resizable", "Z");
#endif
}

/* ******* */
/* Windows */
/* ******* */

JNIEXPORT void JNICALL
Java_java_awt_Window_initIDs
  (JNIEnv *env, jclass cls)
{
    windowIDs.warningString = (*env)->GetFieldID(env, cls, "warningString",
                                                 "Ljava/lang/String;");
    windowIDs.resetGCMID = (*env)->GetMethodID(env, cls, "resetGC",
                                                 "()V");

    windowIDs.locationByPlatform = (*env)->GetFieldID(env, cls, "locationByPlatform",
                                                        "Z");
    windowIDs.isAutoRequestFocus = (*env)->GetFieldID(env, cls, "autoRequestFocus", "Z");

    DASSERT(windowIDs.resetGCMID);
}

/*
 * Class:     sun_motif_awt_WindowAttributes
 * Method:    initIDs
 * Signature: ()V
 */

static struct MWindowAttributeIDs {
    jfieldID nativeDecor;
    jfieldID initialFocus;
    jfieldID isResizable;
    jfieldID initialState;
    jfieldID visibilityState;
    jfieldID decorations;
} mWindowAttributeIDs;

JNIEXPORT void JNICALL
Java_sun_awt_motif_MWindowAttributes_initIDs
  (JNIEnv *env, jclass cls)
{
    mWindowAttributeIDs.nativeDecor =
        (*env)->GetFieldID(env, cls, "nativeDecor", "Z");
    mWindowAttributeIDs.initialFocus =
        (*env)->GetFieldID(env, cls, "initialFocus", "Z");
    mWindowAttributeIDs.isResizable =
        (*env)->GetFieldID(env, cls, "isResizable", "Z");
    mWindowAttributeIDs.initialState =
        (*env)->GetFieldID(env, cls, "initialState", "I");
    mWindowAttributeIDs.visibilityState =
        (*env)->GetFieldID(env, cls, "visibilityState", "I");
    mWindowAttributeIDs.decorations =
        (*env)->GetFieldID(env, cls, "decorations", "I");
}

/*
 * Class:     sun_awt_motif_MWindowPeer
 * Method:    initIDs
 * Signature: ()V
 */

/* This function gets called from the static initializer for MWindowPeer.java
   to initialize the fieldIDs for fields that may be accessed from C */

JNIEXPORT void JNICALL
Java_sun_awt_motif_MWindowPeer_initIDs
  (JNIEnv *env, jclass cls)
{
    mWindowPeerIDs.insets =
        (*env)->GetFieldID(env, cls, "insets", "Ljava/awt/Insets;");
    mWindowPeerIDs.winAttr =
        (*env)->GetFieldID( env,
                            cls,
                            "winAttr",
                            "Lsun/awt/motif/MWindowAttributes;"
                          );
    mWindowPeerIDs.iconWidth =
        (*env)->GetFieldID(env, cls, "iconWidth", "I");
    mWindowPeerIDs.iconHeight =
        (*env)->GetFieldID(env, cls, "iconHeight", "I");
    mWindowPeerIDs.handleWindowFocusOut =
        (*env)->GetMethodID(env,
                            cls,
                            "handleWindowFocusOut",
                            "(Ljava/awt/Window;)V");
    mWindowPeerIDs.handleWindowFocusIn =
        (*env)->GetMethodID(env,
                            cls,
                            "handleWindowFocusIn",
                            "()V");
    mWindowPeerIDs.handleIconify =
        (*env)->GetMethodID(env,
                            cls,
                            "handleIconify",
                            "()V");
    mWindowPeerIDs.handleDeiconify =
        (*env)->GetMethodID(env,
                            cls,
                            "handleDeiconify",
                            "()V");
    mWindowPeerIDs.handleStateChange =
        (*env)->GetMethodID(env,
                            cls,
                            "handleStateChange",
                            "(II)V");

    mWindowPeerIDs.draggedToScreenMID = (*env)->GetMethodID(env, cls,
                                                           "draggedToNewScreen",
                                                           "(I)V");
    DASSERT(mWindowPeerIDs.draggedToScreenMID);
}

/*
 * Class:     sun_awt_motif_MWindowPeer
 * Method:    wrapInSequenced
 * Signature: (Ljava/awt/AWTEvent;)Ljava/awt/SequencedEvent;
 */

/* This method gets called from MWindowPeer to wrap a FocusEvent in
   a SequencedEvent. We have to do this in native code, because we
   don't want to make SequencedEvent a public class. */

JNIEXPORT jobject JNICALL
Java_sun_awt_motif_MWindowPeer_wrapInSequenced
  (JNIEnv *env, jobject this, jobject awtevent)
{
  jobject global = awt_canvas_wrapInSequenced(awtevent);
  jobject local = (*env)->NewLocalRef(env, global);
  (*env)->DeleteGlobalRef(env, global);
  return local;
}

extern jobject findTopLevelOpposite();

/*
 * Class:     sun_awt_motif_MWindowPeer
 * Method:    findOpposite
 * Signature: (Ljava/awt/AWTEvent;)Ljava/awt/Window;
 */

JNIEXPORT jobject JNICALL
Java_sun_awt_motif_MWindowPeer_findOpposite
    (JNIEnv *env, jobject this, jint eventType)
{
#ifdef HEADLESS
    return NULL;
#else
    if ((*env)->EnsureLocalCapacity(env, 1) < 0) {
        return NULL;
    }

    return findTopLevelOpposite(env, eventType);
#endif
}

/* changeInsets() sets target's insets equal to X/Motif values. */

static void
awtJNI_ChangeInsets(JNIEnv * env, jobject this, struct FrameData *wdata)
{
    jobject insets;

    if ((*env)->EnsureLocalCapacity(env, 1) < 0)
        return;

    insets = (*env)->GetObjectField(env, this, mWindowPeerIDs.insets);

    if (JNU_IsNull(env, insets)) {
        return;
    }

    (*env)->SetIntField(env, insets, insetsIDs.top, wdata->top);
    (*env)->SetIntField(env, insets, insetsIDs.left, wdata->left);
    (*env)->SetIntField(env, insets, insetsIDs.bottom, wdata->bottom);
    (*env)->SetIntField(env, insets, insetsIDs.right, wdata->right);

    /* Fix for 4106068: don't do it, rely on the window */
    /*   manager maximizing policy instead              */
#if 0
    /* when the insets get set, make sure we set the proper */
    /* max window size (since it's dependent on inset size) */
    if (wdata->isResizable) {
        int32_t screenWidth = XWidthOfScreen( XDefaultScreenOfDisplay(awt_display));
        int32_t screenHeight= XHeightOfScreen(XDefaultScreenOfDisplay(awt_display));
        XtVaSetValues(wdata->winData.shell,
                XmNmaxWidth, screenWidth - (wdata->left + wdata->right),
                XmNmaxHeight, screenHeight - (wdata->top + wdata->bottom),
                NULL);
    }
#endif
    (*env)->DeleteLocalRef(env, insets);
}


/* setMbAndWwHeightAndOffsets() attempts to establish the heights
   of frame's menu bar and warning window (if present in frame).
   setMbAndWwHeightAndOffsets() also adjusts appropriately the
   X/Motif offsets and calls changeInsets() to set target insets.
   A warning window, if present, is established during ...create().
   wdata->warningWindow is set there, wdata->wwHeight is set here.
   Routine pSetMenuBar() sets value of the wdata->menuBar field.
   This routine reads that value. If it is not null, a menubar
   has been added.  In this case, calculate the current height
   of the menu bar.  This may be a partial (incomplete) menubar
   because ths routine may be called before the X/Motif menubar
   is completely realized. In this case, the menubar height may
   be adjusted incrementally.  This routine may be called from
   ...pSetMenuBar(), innerCanvasEH(), and ...pReshape(). It is
   designed to (eventually) obtain the correct menubar height.
   On the other hand, if wdata->menuBar is NULL and the stored
   menubar height is not zero, then we subtract off the height. */

static void
awtJNI_setMbAndWwHeightAndOffsets(JNIEnv * env,
                                  jobject this,
                                  struct FrameData *wdata )
{
    Dimension   warningHeight,  /* Motif warning window height  */
                labelHeight;    /* Motif warning label's height */

    WidgetList  warningChildrenWL; /* warning children widgets  */

    Dimension   menuBarWidth,   /* Motif menubar width          */
                menuBarHeight,  /* Motif menubar height         */
                menuBarBorderSize, /* Motif menubar border size */
                marginHeight,   /* Motif menubar margin height  */
                menuHeight,     /* Motif menubar's menu height  */
                menuBorderSize, /* Motif menu border size       */
                actualHeight;   /* height: menu+margins+borders */

    WidgetList  menuBarChildrenWL; /* menubar children widgets  */
    Cardinal    numberChildren; /* number of menubar children   */

#ifdef _pauly_debug
    fprintf(stdout," ++ setMenuBar\n");
    fflush(stdout);
#endif /* _pauly_debug */

    /* If warning window height not yet known, try to get it now.
       It will be added to top or bottom (iff NETSCAPE) offset. */
    if  (wdata->warningWindow != NULL) {
        XtVaGetValues(wdata->warningWindow,
                      XmNheight, &warningHeight,
                      XmNchildren, &warningChildrenWL,
                      XmNnumChildren, &numberChildren,
                      NULL);

        /* We may be doing this before warning window is realized ! So,
           check for a child label in the warning. If its height is not
           yet accounted for in the warning height, then use it here.   */
        if  (numberChildren != 0) {
            XtVaGetValues(warningChildrenWL[0],
                          XmNheight, &labelHeight,
                          NULL);
#ifdef _pauly_debug
            fprintf(stdout,"    setMenuBar.... warning label found with height: %d\n", labelHeight);
            fflush(stdout);
#endif /* _pauly_debug */
            if  (warningHeight < labelHeight) {
#ifdef _pauly_debug
    fprintf(stdout,"    setMenuBar.... !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!\n");
    fflush(stdout);
#endif /* _pauly_debug */
                warningHeight = labelHeight;
            }
        }

        if  (wdata->wwHeight < warningHeight) {
#ifdef _pauly_debug
            fprintf(stdout, "    setMenuBar.... adding warning height: %d\n", warningHeight);
            fflush(stdout);
#endif /* _pauly_debug */
#ifdef NETSCAPE
            wdata->bottom += (warningHeight - wdata->wwHeight);
#else
            wdata->top += (warningHeight - wdata->wwHeight);
#endif /* NETSCAPE */
            awtJNI_ChangeInsets(env, this, wdata);
            wdata->wwHeight = warningHeight;
        }
    }

    /* Now we adjust offsets for an added or removed menu bar   */
    if  (wdata->menuBar != NULL) {
#ifdef _pauly_debug
        fprintf(stdout,"    setMenuBar.  menu bar: %x\n", wdata->menuBar);
        fflush(stdout);
#endif /* _pauly_debug */
        XtVaGetValues(wdata->menuBar,
                      XmNwidth, &menuBarWidth,
                      XmNheight, &menuBarHeight,
                      XmNchildren, &menuBarChildrenWL,
                      XmNnumChildren, &numberChildren,
                      XmNborderWidth, &menuBarBorderSize,
                      XmNmarginHeight, &marginHeight,
                      NULL);

        /* We may be doing this before menu bar is realized ! Hence,
           check for a menu in the menu bar. If its height is not yet
           accounted for in the menu bar height, then add it in here.   */
        if  (numberChildren != 0) {
            XtVaGetValues(menuBarChildrenWL[0],
                          XmNheight, &menuHeight,
                          XmNborderWidth, &menuBorderSize,
                          NULL);
#ifdef _pauly_debug
            fprintf(stdout,"    setMenuBar.... menu found with height: %d, border: %d, margin: %d, bar border: %d\n", menuHeight, menuBorderSize, marginHeight, menuBarBorderSize);
            fflush(stdout);
#endif /* _pauly_debug */
            /* Calculate real height of menu bar by adding height of its
               child menu and borders, margins, and the menu bar borders*/
            actualHeight = menuHeight + (2 * menuBorderSize) +
                           (2 * marginHeight) + (2 * menuBarBorderSize);
#ifdef __linux__
#ifdef _pauly_debug
            fprintf(stdout,"  actual height: %d mb height %d\n", actualHeight, menuBarHeight);
            fflush(stdout);
#endif /* _pauly_debug */
#endif
            if  (menuBarHeight < actualHeight) {
#ifdef _pauly_debug
fprintf(stdout,"    setMenuBar.... ****************************************\n");
fflush(stdout);
#endif /* _pauly_debug */
                menuBarHeight = actualHeight;
            }
        }

        if  (wdata->mbHeight < menuBarHeight) {
            /* Adjust the (partially) added menu bar height, top offset.*/
#ifdef _pauly_debug
            fprintf(stdout, "    setMenuBar.... added menuBar height: %d\n", menuBarHeight);
            fflush(stdout);
#endif /* _pauly_debug */
            wdata->top += (menuBarHeight - wdata->mbHeight);
            awtJNI_ChangeInsets(env, this, wdata);
            wdata->mbHeight = menuBarHeight;
        }
    } else if  ((wdata->menuBar == NULL) && (wdata->mbHeight > 0)) {
        /* A menu bar has been removed; subtract height from top offset.*/
        wdata->top -= wdata->mbHeight;
#ifdef _pauly_debug
        fprintf(stdout, "    setMenuBar.... removed menuBar height: %d\n", wdata->mbHeight);
        fflush(stdout);
#endif /* _pauly_debug */
        awtJNI_ChangeInsets(env, this, wdata);
        wdata->mbHeight = 0;
    }
}


/* outerCanvasResizeCB() is Motif resize callback for outer/child canvas.
   It reads width, height of Motif widget, sets java target accordingly,
   and then calls handleResize() to affect any changes.
   This call is only done for a shell resize or inner/parent resize;
   i.e., it may not be done for a ...pReshape() to avoid doing a loop.

   client_data is MWindowPeer instance
*/
static void
outerCanvasResizeCB(Widget wd, XtPointer client_data, XtPointer call_data)
{
    JNIEnv *env = (JNIEnv *)JNU_GetEnv(jvm, JNI_VERSION_1_2);
    jobject target;
    struct FrameData *wdata;
    Position    screenX;        /* x position of the canvas, screen */
    Position    screenY;        /* y position of the canvas, screen */
    Dimension   width;          /* width of the canvas, target  */
    Dimension   height;         /* height of the canvas, target */
    jint        oldWidth;
    jint        oldHeight;

#ifdef _pauly_debug
    fprintf(stdout," ++ WindowResize.\n");
    fflush(stdout);
#endif /* _pauly_debug */

    wdata = (struct FrameData *)
        JNU_GetLongFieldAsPtr(env, (jobject) client_data,
                              mComponentPeerIDs.pData);
    if (wdata == NULL) {
        return;
    }

    if ((*env)->EnsureLocalCapacity(env, 1) < 0)
        return;

    target = (*env)->GetObjectField(env, (jobject) client_data,
                                    mComponentPeerIDs.target);
    XtVaGetValues(wd,
                  XmNwidth, &width,
                  XmNheight, &height,
                  NULL);
#ifdef _pauly_debug
    fprintf(stdout,"    outerCanvasResizeCB.  width: %d, height: %d\n", width, height);
    fflush(stdout);
#endif /* _pauly_debug */


    XtTranslateCoords(wd, 0, 0, &screenX, &screenY);

    if  ((wdata->shellResized) || (wdata->canvasResized)) {
#ifdef _pauly_debug
        fprintf(stdout,"    outerCanvasResizeCB\n");
        fflush(stdout);
#endif /* _pauly_debug */
        wdata->shellResized = False;
        wdata->canvasResized = False;
        /*
        ** if you are not yet reparented, don't compute the size based on the
        ** widgets, as the window manager shell containg the insets is not yet
        ** there.  Use the size the application has set.
        ** If not reparented, we got here because the application set the size,
        ** so just send them Component.RESIZED event with the size they set.
        **
        ** If the reparenting causes a resize ( only when inset guess is wrong )        ** the new size will be sent in a Component.RESIZED event at that time.
        */
        if (wdata->reparented)
        {
            (*env)->SetIntField(env, target, componentIDs.x, (jint) screenX);
            (*env)->SetIntField(env, target, componentIDs.y, (jint) screenY);
        }

    oldWidth = (*env)->GetIntField(env, target, componentIDs.width);
    oldHeight = (*env)->GetIntField(env, target, componentIDs.height);

    if (oldWidth != width || oldHeight != height || wdata->need_reshape)
    {
        wdata->need_reshape = False;
        (*env)->SetIntField(env, target, componentIDs.width, (jint)width);
        (*env)->SetIntField(env, target, componentIDs.height,
                (jint)height);

        /* only do this for Windows, not Canvases, btw */
        checkNewXineramaScreen(env, client_data, wdata, screenX, screenY, width, height);

        JNU_CallMethodByName(env, NULL, (jobject) client_data,
                 "handleResize", "(II)V", width, height);
        if  ((*env)->ExceptionOccurred(env)) {
            (*env)->ExceptionDescribe(env);
        (*env)->ExceptionClear(env);
        }
    }
    }

    (*env)->DeleteLocalRef(env, target);

#ifdef _pauly_debug
    fprintf(stdout,"    WindowResize. Done.\n");
    fflush(stdout);
#endif /* _pauly_debug */

} /* outerCanvasResizeCB() */

static void reconfigureOuterCanvas ( JNIEnv *env, jobject target,
                                     jobject this, struct FrameData *wdata )
{
    Dimension   innerDAWidth,   /* width of inner Motif canvas  */
                innerDAHeight,  /* height of inner Motif canvas */
                outerDAWidth,   /* width of outer Motif canvas  */
                outerDAHeight;  /* height of outer Motif canvas */
    int32_t     targetWidth,    /* java target object's width   */
                targetHeight;   /* java target's object height  */
    Dimension   width;          /* width of the canvas, target  */
    Dimension   height;         /* height of the canvas, target */


    Position    innerX,         /* x loc. of inner Motif canvas */
                innerY,         /* y loc. of inner Motif canvas */
                x, y;

    /* canvasW is (visible) inner/parent drawing area (canvas) widget   */
    XtVaGetValues(XtParent(wdata->winData.comp.widget),
                  XmNwidth, &innerDAWidth,
                  XmNheight, &innerDAHeight,
                  XmNx, &innerX,
                  XmNy, &innerY,
                  NULL);

    /* This resize may be due to the insertion or removal of a menu bar.
       If so, we appropriately adjust the top offset in wdata, insets.  */
    awtJNI_setMbAndWwHeightAndOffsets(env, this, wdata);

    outerDAWidth = innerDAWidth + wdata->left + wdata->right;
    outerDAHeight = innerDAHeight + wdata->top + wdata->bottom;

    /* If it's a menu bar reset, do not do resize of outer/child canvas.
       (Another thread problem; we arrest this now before damage done.) */
    if  (wdata->menuBarReset)
    {
        targetWidth = (*env)->GetIntField(env, target, componentIDs.width);
        targetHeight = (*env)->GetIntField(env, target, componentIDs.height);
        if  ((outerDAWidth != targetWidth) || (outerDAHeight != targetHeight))
        {
            return;
        }
    }

    wdata->canvasResized = True;

    /* The outer/child drawing area (canvas) needs to be configured too.
       If its size changes, its resize callback will thereby be invoked.*/
    x = -wdata->left;
    y = -wdata->top;
    width = innerDAWidth + wdata->left + wdata->right;
    height = innerDAHeight + wdata->top + wdata->bottom;

    XtConfigureWidget(wdata->winData.comp.widget, x, y, width, height, 0 );
}



/* innerCanvasEH() is event handler for inner/parent canvas. It handles
   map and configure notify events. It reads width and height, adjusts
   for menubar insertion / removal and configures outer/child canvas.   */

static void
innerCanvasEH(Widget canvasW, XtPointer client_data, XEvent *event,
              Boolean* continueToDispatch)
{
    JNIEnv      *env = (JNIEnv *)JNU_GetEnv(jvm, JNI_VERSION_1_2);
    jobject     this = (jobject) client_data;
    jobject     target;
    struct FrameData *wdata;


    wdata = (struct FrameData *)
        JNU_GetLongFieldAsPtr(env, this, mComponentPeerIDs.pData);
    if  (wdata == NULL) {
        return;
    }

    if  ((*env)->EnsureLocalCapacity(env, 1) < 0)
        return;

    target = (*env)->GetObjectField(env, (jobject) client_data,
                                    mComponentPeerIDs.target);

    /* While inside ...pSetMenuBar(), don't react to incomplete resizing
       events supplied by Xt toolkit. Wait for completion of the routine. */


    /* For a map or resize, we need to check for the addition or deletion
       of a menu bar to the form which is the of this drawing area (canvas).
       We also must then configure the outer/child canvas appropriately.  */

    if  ( (event->xany.type == MapNotify) ||
          (event->xany.type == ConfigureNotify) )
    {
        reconfigureOuterCanvas( env, target, this, wdata );
    }

    (*env)->DeleteLocalRef(env, target);

}

/* syncTopLevelPos() is necessary to insure that the window manager has in
 * fact moved us to our final position relative to the reParented WM window.
 * We have noted a timing window which our shell has not been moved so we
 * screw up the insets thinking they are 0,0.  Wait (for a limited period of
 * time to let the WM hava a chance to move us
 */
void syncTopLevelPos( Display *d, Window w, XWindowAttributes *winAttr )
{
    int32_t i = 0;
    memset(winAttr, 0, sizeof(*winAttr));

    do {
        if (!XGetWindowAttributes(d, w, winAttr)) {
            memset(winAttr, 0, sizeof(*winAttr));
            break;
        }
        /* Sometimes we get here before the WM has updated the
        ** window data struct with the correct position.  Loop
        ** until we get a non-zero position.
        */
        if ((winAttr->x != 0) || (winAttr->y != 0)) {
            break;
        }
        else {
            /* What we really want here is to sync with the WM,
            ** but there's no explicit way to do this, so we
            ** call XSync for a delay.
            */
            XSync(d, False);
        }
    } while (i++ < 50);
}

typedef struct FocusOutInfo_str {
    XEvent * eventOut;
    Window inWin;
    Window inChild;
    Widget defChild;
    jobject childComp;
} FocusOutInfo_t;

#define IsCanvasTypeWidget(w) \
        (XtIsSubclass(w, xmDrawingAreaWidgetClass) ||\
        XtIsSubclass(w, vDrawingAreaClass))

int isTopLevelPartWidget(Widget w) {
    if (XtIsShell(w)) {
        return TRUE;
    }
    if (XtIsSubclass(w, xmFormWidgetClass)) {
        return TRUE;
    }
    if (IsCanvasTypeWidget(w)) {
        Widget w1 = XtParent(w);
        if (w1 != NULL) {
            if (XtIsSubclass(w1, xmFormWidgetClass)) {
                return TRUE;
            }
            if (IsCanvasTypeWidget(w1)) {
                Widget w2 = XtParent(w1);
                if (w2 != NULL) {
                    if (XtIsSubclass(w2, xmFormWidgetClass)) {
                        return TRUE;
                    }
                }
            }

        }
    }
    return FALSE;
}

void
shellFocusEH(Widget w, XtPointer data, XEvent *event, Boolean *continueToDispatch)
{
    JNIEnv      *env = (JNIEnv *)JNU_GetEnv(jvm, JNI_VERSION_1_2);
    jobject     this = (jobject) data;
    jobject     target;
    struct FrameData *wdata;

    /* Any event handlers which take peer instance pointers as
     * client_data should check to ensure the widget has not been
     * marked as destroyed as a result of a dispose() call on the peer
     * (which can result in the peer instance pointer already haven
     * been gc'd by the time this event is processed)
     */
    if (w->core.being_destroyed) {
        return;
    }

    wdata = (struct FrameData *)
        JNU_GetLongFieldAsPtr(env, this, mComponentPeerIDs.pData);
    if (wdata == NULL) {
        return;
    }

    switch (event->xany.type) {
      case FocusOut:
          // Will be handled by proxy automaticall since he is focus owner
        break;
      case FocusIn:
        // Forward focus event to the proxy
        XSetInputFocus(awt_display, XtWindow(wdata->focusProxy), RevertToParent, CurrentTime);
        break;
    }
}

/**
 * Fix for Alt-Tab problem.
 * See coments on use semantics below.
 */
Boolean skipNextNotifyWhileGrabbed = False;
Boolean skipNextFocusIn = False;

Boolean focusOnMapNotify = False;

/* shellEH() is event handler for the Motif shell widget. It handles
   focus change, map notify, configure notify events for the shell.
   Please see internal comments pertaining to these specific events.

   data is MWindowPeer instance pointer
*/
void
shellEH(Widget w, XtPointer data, XEvent *event, Boolean *continueToDispatch)
{
    JNIEnv      *env = (JNIEnv *)JNU_GetEnv(jvm, JNI_VERSION_1_2);
    jobject     this = (jobject) data;
    jobject     target;
    struct FrameData *wdata;
    int32_t     setTargetX,
                setTargetY,
                getTargetX,
                getTargetY;
    /* Changed long to int for 64-bit */
    int32_t     wwHeight;       /* height of any warning window present */
    int32_t     topAdjust;      /* adjust top offset for menu, warning  */
    jclass      clazz;
    int32_t     x, y;
    int32_t     width, height;
    enum wmgr_t runningWM;
    jobject   winAttrObj;
    static jobject windowClass = NULL;
    /* Any event handlers which take peer instance pointers as
     * client_data should check to ensure the widget has not been
     * marked as destroyed as a result of a dispose() call on the peer
     * (which can result in the peer instance pointer already haven
     * been gc'd by the time this event is processed)
     */
    if (w->core.being_destroyed) {
        return;
    }

    wdata = (struct FrameData *)
        JNU_GetLongFieldAsPtr(env, this, mComponentPeerIDs.pData);
    if (wdata == NULL) {
        return;
    }

    switch (event->xany.type) {

    case FocusOut: {
        int32_t res = 0;
        int revert_to = 0;
        Widget defChild = NULL;
        Window focusOwner = None;
        jobject oppositeWindow = NULL;
        Widget oppositeShell = NULL;
        XEvent inEvent;
        Widget shell = NULL;
#ifdef DEBUG_FOCUS
        fprintf(stderr, "Focusout on proxy; window = %x, mode %d, detail %d\n",
                event->xfocus.window, event->xfocus.mode, event->xfocus.detail);
#endif
        shell = wdata->winData.shell;

        if ((*env)->EnsureLocalCapacity(env, 3) < 0) {
            break;
        }

        /**
         * Fix for Alt-Tab problem. We should process NotifyWhileGrabbed events
         * only if they are due to the switch between top-levels.
         * skipNextNotifyWhileGrabbed is set from Menu and PopupMenu code
         * to prevent generation of focus events when user interact with these
         * widget.
         */
        if (event->xfocus.mode == NotifyWhileGrabbed) {
            if (skipNextNotifyWhileGrabbed) {
                skipNextNotifyWhileGrabbed = False;
                break;
            }
        } else if (event->xfocus.mode != NotifyNormal) break;

        /**
         * Fix for Alt-Tab problem.
         * skipNextFocusIn is set in Choice code to avoid processing of
         * next focus-in or focus-out generated by Choice as it is a fake
         * event.
         */
        if (skipNextFocusIn && event->xfocus.detail == NotifyPointer) {
            break;
        }

        XGetInputFocus( awt_display, &focusOwner, &revert_to);

        if (focusOwner != None) {
            Widget inWidget = NULL;
            jobject wpeer = NULL;
            inWidget = XtWindowToWidget(awt_display, focusOwner);
            if (inWidget != NULL && inWidget != shell) {
                oppositeShell = getShellWidget(inWidget);
                wpeer = findPeer(&inWidget);
                if (wpeer == NULL) {
                    inWidget = findTopLevelByShell(inWidget);
                    if (inWidget != NULL) {
                        wpeer = findPeer(&inWidget);
                    }
                }
                if (wpeer != NULL) {
                    jobject peerComp =
                        (*env)->GetObjectField(env,
                                               wpeer,
                                               mComponentPeerIDs.target);
                    if (peerComp != NULL) {
                        // Check that peerComp is top-level

                        // load class
                        if (windowClass == NULL) {
                            jobject localWindowClass = (*env)->FindClass(env, "java/awt/Window");
                            windowClass = (*env)->NewGlobalRef(env, localWindowClass);
                            (*env)->DeleteLocalRef(env, localWindowClass);
                        }
                        if ((*env)->IsInstanceOf(env, peerComp, windowClass)) {
                            oppositeWindow = peerComp;
                        } else { // Opposite object is not Window - there is no opposite window.
                            (*env)->DeleteLocalRef(env, peerComp);
                            peerComp = NULL;
                            oppositeShell = NULL;
                        }
                    }
                }
            }
        } else {
            // If there is no opposite shell but we have active popup - this popup is actually
            // the oppposite. This should mean that this focus out is due to popup - and thus
            // should be skipped. Fix for 4478780.
            if (skipNextNotifyWhileGrabbed) {
                break;
            }
        }

        // If current window is not focusable and opposite window is not focusable - do nothing
        // If current window is focusable and opposite is not - do not clear focus variables like
        // focus didn't leave this window(but it will in terms of X). When we later switch to either
        // - back to this window: variables are already here
        // - another focusable window: variables point to focusable window and "focus lost" events
        //   will be generated for it
        // - non-java window: variables point to focusable window and "focus lost" events
        //   will be generated for it, not for non-focusable.
        // If current window is non-focusable and opposite is focusable then do not generate anything
        // as if we didn't leave previous focusable window so Java events will generated for it.
        //
        // Fix for 6547951.
        // Also do cleaning when switching to non-java window (opposite is null).
        if (isFocusableWindowByShell(env, shell) && shell != oppositeShell &&
            ((oppositeShell != NULL && isFocusableWindowByShell(env, oppositeShell)) ||
             oppositeShell == NULL))
        {
            // The necessary FOCUS_LOST event will be generated by DKFM.
            // So we need to process focus list like we received FocusOut
            // for the desired component - shell's current focus widget
            defChild = XmGetFocusWidget(shell);
            if (defChild != NULL) {
                jobject peer = findPeer(&defChild);
                if (peer == NULL) {
                    defChild = findTopLevelByShell(defChild);
                    if (defChild != NULL) {
                        peer = findPeer(&defChild);
                    }
                }
                if (peer != NULL) {
                    jobject comp = (*env)->GetObjectField(env, peer, mComponentPeerIDs.target);
                    if (focusList != NULL) {
                        jobject last = (*env)->NewLocalRef(env, focusList->requestor);
                        if ((*env)->IsSameObject(env, comp, last)) {
                            FocusListElt * temp = focusList;
                            forGained = focusList->requestor;
                            focusList = focusList->next;
                            free(temp);
                            if (focusList == NULL) {
                                focusListEnd = NULL;
                            }
                        }
                        if (!JNU_IsNull(env, last)) {
                            (*env)->DeleteLocalRef(env, last);
                        }
                    }
                    (*env)->DeleteLocalRef(env, comp);
                }
            }
            target = (*env)->GetObjectField(env, this, mComponentPeerIDs.target);
            processTree(defChild, findWindowsProxy(target, env), False);
            XtSetKeyboardFocus(shell, NULL);
            (*env)->DeleteLocalRef(env, target);
        }
#ifndef NOMODALFIX
        if (!awt_isModal() || awt_isWidgetModal(shell)) {
#endif //NOMODALFIX
            if ( oppositeShell != NULL
                 && isFocusableWindowByShell(env, oppositeShell)
                 && isFocusableWindowByShell(env, shell)
                 || (oppositeShell == NULL))
            {
                /*
                 * Fix for 5095117.
                 * Check if current native focused window is the same as source.
                 * Sometimes it is not - we must not however clean reference to
                 * actual native focused window.
                 */
                jobject currentFocusedWindow = awt_canvas_getFocusedWindowPeer();
                if ((*env)->IsSameObject(env, this, currentFocusedWindow)) {
                    awt_canvas_setFocusedWindowPeer(NULL);
                }
                (*env)->DeleteLocalRef(env, currentFocusedWindow);

                JNU_CallMethodByName(env, NULL, this, "handleWindowFocusOut", "(Ljava/awt/Window;)V",
                                     oppositeWindow);
                if ((*env)->ExceptionCheck(env) == JNI_TRUE) {
                    (*env)->ExceptionDescribe(env);
                    (*env)->ExceptionClear(env);
                }
            }
#ifndef NOMODALFIX
        }
#endif //NOMODALFIX
        if (oppositeWindow != NULL) {
            (*env)->DeleteLocalRef(env, oppositeWindow);
        }

        break;
    } /* FocusOut */

    case FocusIn: {
        Widget shell = wdata->winData.shell;
#ifdef DEBUG_FOCUS
        fprintf(stderr, "FocusIn on proxy; window = %x, mode %d, detail %d\n", event->xfocus.window,
                event->xfocus.mode, event->xfocus.detail);
#endif
        if (/*  event->xfocus.mode == NotifyNormal */ 1) {

            /**
             * Fix for Alt-Tab problem. We should process NotifyWhileGrabbed events to detect
             * switch between top-levels using alt-tab, but avoid processing these type of event
             * when they are originated from other sources.
             */
            if (event->xfocus.mode == NotifyWhileGrabbed) {
                /**
                 * skipNextNotifyWhileGrabbed is set from Menu and PopupMenu code to
                 * skip next focus-in event with NotifyWhileGrabbed as it is generated
                 * in result of closing of the Menu's shell.
                 * Event will also have NotifyInferior if uses clicked on menu bar in the
                 * space where there is not menu items.
                 */
                if (skipNextNotifyWhileGrabbed || event->xfocus.detail == NotifyInferior) {
                    skipNextNotifyWhileGrabbed = False;
                    break;
                }
            } else if (event->xfocus.mode != NotifyNormal)  {
                break;
            }

            /**
             * Fix for Alt-Tab problem.
             * skipNextFocusIn is set from Choice code to avoid processing next focus-in
             * as it is a fake event.
             */
            if (skipNextFocusIn == True) {
                /**
                 * There could be the set of fake events, the last one
                 * will have detail == NotifyPointer
                 */
                if (event->xfocus.detail != NotifyPointer) {
                    skipNextFocusIn = False;
                }
                break;
            }
#ifndef NOMODALFIX
            if (!awt_isModal() || awt_isWidgetModal(shell)) {
#endif //NOMODALFIX
                if (isFocusableWindowByShell(env, shell)) {
                    jobject currentFocusedWindow = awt_canvas_getFocusedWindowPeer();
                    // Check if focus variables already point to this window. If so,
                    // it means there were transfer to non-focusable window and now we
                    // are back to origianl focusable window. No need to generate Java events
                    // in this case.
                    if (!(*env)->IsSameObject(env, this, currentFocusedWindow)) {
                        awt_canvas_setFocusedWindowPeer(this);
                        awt_canvas_setFocusOwnerPeer(this);

                        /*
                         * Fix for 6465038.
                         * Restore focus on the toplevel widget if it's broken.
                         */
                        Widget widgetToFocus = getFocusWidget(findTopLevelByShell(shell));
                        Widget currentOwner = XmGetFocusWidget(shell);

                        if (widgetToFocus != currentOwner) {
#ifdef DEBUG_FOCUS
                            fprintf(stderr, "Wrong Xm focus; resetting Xm focus from %x to toplevel %x...\n",
                                    currentOwner != NULL ? XtWindow(currentOwner) : 0,
                                    widgetToFocus != NULL ? XtWindow(widgetToFocus) : 0);
#endif
                            if ( !XmProcessTraversal(widgetToFocus, XmTRAVERSE_CURRENT) ) {
                                XtSetKeyboardFocus(shell, widgetToFocus);
                            }
#ifdef DEBUG_FOCUS
                            Widget _w = XmGetFocusWidget(shell);
                            fprintf(stderr, "                ...focus resulted on window %x\n", _w != NULL ? XtWindow(_w) : 0);
#endif
                        }

                        JNU_CallMethodByName(env, NULL, this, "handleWindowFocusIn", "()V");
                        if ((*env)->ExceptionCheck(env) == JNI_TRUE) {
                            (*env)->ExceptionDescribe(env);
                            (*env)->ExceptionClear(env);
                        }
                    }
                    (*env)->DeleteLocalRef(env, currentFocusedWindow);
                }
#ifndef NOMODALFIX
            }
#endif //NOMODALFIX
        }
        raiseInputMethodWindow(wdata);
        break;
    } /* FocusIn */

    case VisibilityNotify: {
       winAttrObj = (*env)->GetObjectField(env, this, mWindowPeerIDs.winAttr);
       (*env)->SetIntField(env, winAttrObj,
                           mWindowAttributeIDs.visibilityState,
                           event->xvisibility.state);
        if (event->xvisibility.state == VisibilityUnobscured) {
            raiseInputMethodWindow(wdata);
        }
        break;
    } /* VisibilityNotify */

    case MapNotify: {
        /* Your body seems to unfade */
        if (wdata->initialFocus == False) {
            XtVaSetValues(wdata->winData.shell, XmNinput, True, NULL);

            // We have to to evidently move the window to the front here.
            Window shellWindow;
            if ((shellWindow = XtWindow(wdata->winData.shell)) != None) {
                XRaiseWindow(awt_display, shellWindow);
            }
        }
        if (awt_wm_isStateNetHidden(XtWindow(wdata->winData.shell))) {
            focusOnMapNotify = True;
        }
        /*
         * TODO: perhaps we need this putback only for simple Window.
         * For Frame/Dialog XmNinput==True would be enough. The native
         * system will focus it itself.
         */
        if (wdata->isFocusableWindow && focusOnMapNotify) {
            XEvent ev;
            memset(&ev, 0, sizeof(ev));

            ev.type = FocusIn;
            ev.xany.send_event = True;
            ev.xany.display = awt_display;
            ev.xfocus.mode = NotifyNormal;
            ev.xfocus.detail = NotifyNonlinear;
            ev.xfocus.window = XtWindow(wdata->winData.shell);
            awt_put_back_event(env, &ev);
        }
        focusOnMapNotify = False;

        break;
    }

    case UnmapNotify: {
        /* Gee!  All of a sudden, you can't see yourself */
        if (wdata->initialFocus == False) {
            XtVaSetValues(wdata->winData.shell, XmNinput, False, NULL);
        }
        if (awt_wm_isStateNetHidden(XtWindow(wdata->winData.shell))) {
            focusOnMapNotify = True;
        }
        break;
    }

    case DestroyNotify: {       /* Foul play!  ICCCM forbids WM to do this! */
        /* Your window is killed by the WM */
        JNU_CallMethodByName(env, NULL, this, "handleDestroy", "()V");
        if ((*env)->ExceptionOccurred(env)) {
            (*env)->ExceptionDescribe(env);
            (*env)->ExceptionClear(env);
        }
        break;
    }

    case PropertyNotify: {
        jint state, old_state, changed;

        /*
         * Let's see if this is a window state protocol message, and
         * if it is - decode a new state in terms of java constants.
         */
        if (!awt_wm_isStateChange(wdata, (XPropertyEvent *)event, &state)) {
            /* Pakka Pakka seems not interested */
            break;
        }

        changed = wdata->state ^ state;
        if (changed == 0) {
            /* You feel dizzy for a moment, but nothing happens... */
            DTRACE_PRINTLN("TL: >>> state unchanged");
            break;
        }

        old_state = wdata->state;
        wdata->state = state;

#ifdef DEBUG
        DTRACE_PRINT("TL: >>> State Changed:");
        if (changed & java_awt_Frame_ICONIFIED) {
            if (state & java_awt_Frame_ICONIFIED) {
                DTRACE_PRINT(" ICON");
            } else {
                DTRACE_PRINT(" !icon");
            }
        }
        if (changed & java_awt_Frame_MAXIMIZED_VERT) {
            if (state & java_awt_Frame_MAXIMIZED_VERT) {
                DTRACE_PRINT(" MAX_VERT");
            } else {
                DTRACE_PRINT(" !max_vert");
            }
        }
        if (changed & java_awt_Frame_MAXIMIZED_HORIZ) {
            if (state & java_awt_Frame_MAXIMIZED_HORIZ) {
                DTRACE_PRINT(" MAX_HORIZ");
            } else {
                DTRACE_PRINT(" !max_horiz");
            }
        }
        DTRACE_PRINTLN("");
#endif

        if (changed & java_awt_Frame_ICONIFIED) {
            /* Generate window de/iconified event for old clients */
            if (state & java_awt_Frame_ICONIFIED) {
                DTRACE_PRINTLN("TL: ... handleIconify");
                JNU_CallMethodByName(env, NULL,
                                     this, "handleIconify", "()V");
            }
            else {
                DTRACE_PRINTLN("TL: ... handleDeiconify");
                JNU_CallMethodByName(env, NULL,
                                     this, "handleDeiconify", "()V");
            }
            if ((*env)->ExceptionOccurred(env)) {
                (*env)->ExceptionDescribe(env);
                (*env)->ExceptionClear(env);
            }
        }

        DTRACE_PRINTLN("TL: ... handleStateChange");
        JNU_CallMethodByName(env, NULL,
                             this, "handleStateChange", "(II)V",
                             old_state, state);
        if ((*env)->ExceptionOccurred(env)) {
            (*env)->ExceptionDescribe(env);
            (*env)->ExceptionClear(env);
        }
        break;
    } /* PropertyNotify */

    case ReparentNotify: {
        Window root = RootWindowOfScreen(XtScreen(wdata->winData.shell));

#ifdef DEBUG
        DTRACE_PRINT2("TL: ReparentNotify(0x%x/0x%x) to ",
                      wdata->winData.shell, XtWindow(wdata->winData.shell));
        if (event->xreparent.parent == root) {
            DTRACE_PRINTLN("root");
        } else {
            DTRACE_PRINTLN1("window 0x%x", event->xreparent.parent);
        }
#endif

        if (wdata->winData.flags & W_IS_EMBEDDED) {
            DTRACE_PRINTLN("TL:   embedded frame - nothing to do");
            break;
        }

#ifdef __linux__
        if (!wdata->fixInsets) {
            DTRACE_PRINTLN("TL:   insets already fixed");
            break;
        }
        else {
            wdata->fixInsets = False;
        }
#endif

        if ((*env)->EnsureLocalCapacity(env, 1) < 0)
            break;

        target = (*env)->GetObjectField(env, this, mComponentPeerIDs.target);

        x      = (*env)->GetIntField(env, target, componentIDs.x);
        y      = (*env)->GetIntField(env, target, componentIDs.y);
        width  = (*env)->GetIntField(env, target, componentIDs.width);
        height = (*env)->GetIntField(env, target, componentIDs.height);

        /* The insets were literally hardcoded in the MWindowPeer.
           But they are dependent upon both the window manager (WM)
           and the hardware display.  So, these are usually wrong.
           This leads to problems with shell positioning and size.
           Furthermore, there is not a published interface or way
           to obtain from any given window manager the dimensions
           of its decoration windows (i.e., borders and title bar).
           So, given this problem in design, we must workaround.
           N.B. (0) This works.  But there is one functional caveat:
           the frame.insets() function will usually return
           the wrong values until AFTER the frame is shown.
           It always did this before; it's just that now,
           the values will become correct after rendering,
           whereas before the values were never corrected.
           (I believe this unavoidable given this design.)
           (1) Note that we must/have to do this exactly once.
           (2) The hardcoded values of ...create() (25,5)
           are also utilized here and must be consistent.
           This of course could be reworked as desired.
           (3) Assume top border (title bar) is one width,
           and other three borders are another width.
           This, however, could be easily reworked below.       */

        /*
         * The above comment is no longer completely true.
         * The insets are no longer hardcoded but are retrieved from
         * guessInsets(), either from a per-window manager default,
         * set in the awt.properties file, or overwritten by the
         * actual values determined from a previous frames
         * reparenting.
         */

        if (wdata->decor == AWT_NO_DECOR) {
            if (!wdata->isResizable && !wdata->isFixedSizeSet) {
                reshape(env, this, wdata, x, y, width, height, False);
                if (wdata->warningWindow != NULL)
                    awtJNI_ChangeInsets(env, this, wdata);
            }
        }
        else if (event->xreparent.parent == root) {
            wdata->reparented = False;
            wdata->configure_seen = False;

            /*
             * We can be repareted to root for two reasons:
             *   . setVisible(false)
             *   . WM exited
             */
            if (wdata->isShowing) { /* WM exited */
                /* Work around 4775545 */
                awt_wm_unshadeKludge(wdata);
            }
        }
        else  { /* reparented to WM frame, figure out our insets */
            XWindowAttributes   winAttr, actualAttr;
            int32_t             correctWMTop = -1;
            int32_t             correctWMLeft = -1;
            int32_t             correctWMBottom;
            int32_t             correctWMRight;
            int32_t             topCorrection;
            int32_t             leftCorrection;
            int32_t             bottomCorrection = 0;
            int32_t             rightCorrection = 0;
            int32_t             screenX, screenY;
            int32_t             i;
            int32_t             actualWidth, actualHeight;
            int32_t             t, l, b, r;
            Window              containerWindow;

            /* Dummies for XQueryTree */
            Window              ignore_Window, *ignore_WindowPtr;
            uint32_t            ignore_uint;

            Boolean             setXY = True;
            XSizeHints*         hints = XAllocSizeHints();

            wdata->reparented = True;

            if (hints != NULL) {
                long ignore = 0;
                XGetWMNormalHints(awt_display, XtWindow(wdata->winData.shell),
                    hints, &ignore);
                setXY = (hints->flags & (USPosition|PPosition)) != 0;
                XFree(hints);
            }

            /*
             * Unfortunately the concept of "insets" borrowed to AWT
             * from Win32 is *absolutely*, *unbelievably* foreign to
             * X11.  Few WMs provide the size of frame decor
             * (i.e. insets) in a property they set on the client
             * window, so we check if we can get away with just
             * peeking at it.  [Future versions of wm-spec might add a
             * standardized hint for this].
             *
             * Otherwise we do some special casing.  Actually the
             * fallback code ("default" case) seems to cover most of
             * the existing WMs (modulo Reparent/Configure order
             * perhaps?).
             *
             * Fallback code tries to account for the two most common cases:
             *
             * . single reparenting
             *       parent window is the WM frame
             *       [twm, olwm, sawfish]
             *
             * . double reparenting
             *       parent is a lining exactly the size of the client
             *       grandpa is the WM frame
             *       [mwm, e!, kwin, fvwm2 ... ]
             */

            if (awt_wm_getInsetsFromProp(event->xreparent.window,
                                         &t, &l, &b, &r))
            {
                correctWMTop    = t;
                correctWMLeft   = l;
                correctWMBottom = b;
                correctWMRight  = r;
                setXY = False;
            }
            else
            switch (awt_wm_getRunningWM()) {

            /* should've been done in awt_wm_getInsetsFromProp */
            case ENLIGHTEN_WM: {
                DTRACE_PRINTLN("TL:   hmm, E! insets should have been read"
                               " from _E_FRAME_SIZE");
                /* enlightenment does double reparenting */
                syncTopLevelPos(XtDisplay(wdata->winData.shell),
                                event->xreparent.parent, &winAttr);

                XQueryTree(XtDisplay(wdata->winData.shell),
                           event->xreparent.parent,
                           &ignore_Window,
                           &containerWindow, /* actual WM frame */
                           &ignore_WindowPtr,
                           &ignore_uint);
                if (ignore_WindowPtr)
                    XFree(ignore_WindowPtr);

                correctWMLeft = winAttr.x;
                correctWMTop  = winAttr.y;

                /*
                 * Now get the actual dimensions of the parent window
                 * resolve the difference.  We can't rely on the left
                 * to be equal to right or bottom...  Enlightment
                 * breaks that assumption.
                 */
                XGetWindowAttributes(XtDisplay(wdata->winData.shell),
                                     containerWindow, &actualAttr);
                correctWMRight  = actualAttr.width
                    - (winAttr.width + correctWMLeft);
                correctWMBottom = actualAttr.height
                    - (winAttr.height + correctWMTop) ;
                break;
            }

            case ICE_WM:
            case KDE2_WM: /* should've been done in awt_wm_getInsetsFromProp */
            case CDE_WM:
            case MOTIF_WM: {
                /* these are double reparenting too */
                syncTopLevelPos(XtDisplay(wdata->winData.shell),
                                event->xreparent.parent, &winAttr);

                correctWMTop    = winAttr.y;
                correctWMLeft   = winAttr.x;
                correctWMRight  = correctWMLeft;
                correctWMBottom = correctWMLeft;

                XTranslateCoordinates(awt_display, event->xreparent.window,
                                      root, 0,0, &screenX, &screenY,
                                      &containerWindow);

                if ((screenX != x + wdata->leftGuess)
                    || (screenY != y + wdata->topGuess))
                {
                    /*
                     * looks like the window manager has placed us somewhere
                     * other than where we asked for, lets respect the window
                     * and go where he put us, not where we tried to put us
                     */
                    x = screenX - correctWMLeft;
                    y = screenY - correctWMTop;
                }
                break;
            }

            case SAWFISH_WM:
            case OPENLOOK_WM: {
                /* single reparenting */
                syncTopLevelPos(XtDisplay(wdata->winData.shell),
                                event->xreparent.window, &winAttr);

                correctWMTop    = winAttr.y;
                correctWMLeft   = winAttr.x;
                correctWMRight  = correctWMLeft;
                correctWMBottom = correctWMLeft;
                break;
            }

            case OTHER_WM:
            default: {          /* this is very similar to the E! case above */
                Display *dpy = event->xreparent.display;
                Window w = event->xreparent.window;
                Window parent = event->xreparent.parent;
                XWindowAttributes wattr, pattr;

                XGetWindowAttributes(dpy, w, &wattr);
                XGetWindowAttributes(dpy, parent, &pattr);

                DTRACE_PRINTLN5("TL:   window attr +%d+%d+%dx%d (%d)",
                                wattr.x, wattr.y, wattr.width, wattr.height,
                                wattr.border_width);
                DTRACE_PRINTLN5("TL:   parent attr +%d+%d+%dx%d (%d)",
                                pattr.x, pattr.y, pattr.width, pattr.height,
                                pattr.border_width);

                /*
                 * Check for double-reparenting WM.
                 *
                 * If the parent is exactly the same size as the
                 * top-level assume taht it's the "lining" window and
                 * that the grandparent is the actual frame (NB: we
                 * have already handled undecorated windows).
                 *
                 * XXX: what about timing issues that syncTopLevelPos
                 * is supposed to work around?
                 */
                if (wattr.x == 0 && wattr.y == 0
                    && wattr.width  + 2*wattr.border_width == pattr.width
                    && wattr.height + 2*wattr.border_width == pattr.height)
                {
                    Window ignore_root, grandparent, *children;
                    unsigned int ignore_nchildren;

                    DTRACE_PRINTLN("TL:   double reparenting WM detected");
                    XQueryTree(dpy, parent,
                               &ignore_root,
                               &grandparent,
                               &children,
                               &ignore_nchildren);
                    if (children)
                        XFree(children);

                    /* take lining window into account */
                    wattr.x = pattr.x;
                    wattr.y = pattr.y;
                    wattr.border_width += pattr.border_width;

                    parent = grandparent;
                    XGetWindowAttributes(dpy, parent, &pattr);
                    DTRACE_PRINTLN5("TL:   window attr +%d+%d+%dx%d (%d)",
                                    wattr.x, wattr.y,
                                    wattr.width, wattr.height,
                                    wattr.border_width);
                    DTRACE_PRINTLN5("TL:   parent attr +%d+%d+%dx%d (%d)",
                                    pattr.x, pattr.y,
                                    pattr.width, pattr.height,
                                    pattr.border_width);
                }

                /*
                 * XXX: To be absolutely correct, we'd need to take
                 * parent's border-width into account too, but the
                 * rest of the code is happily unaware about border
                 * widths and inner/outer distinction, so for the time
                 * being, just ignore it.
                 */
                correctWMTop = wattr.y + wattr.border_width;
                correctWMLeft = wattr.x + wattr.border_width;
                correctWMBottom = pattr.height
                    - (wattr.y + wattr.height + 2*wattr.border_width);
                correctWMRight = pattr.width
                    - (wattr.x + wattr.width + 2*wattr.border_width);
                DTRACE_PRINTLN4("TL: insets = top %d, left %d, bottom %d, right %d",
                                correctWMTop, correctWMLeft,
                                correctWMBottom, correctWMRight);
                break;
            } /* default */

            } /* switch (runningWM) */


            /*
             * Ok, now see if we need adjust window size because
             * initial insets were wrong (most likely they were).
             */
            topCorrection    = correctWMTop    - wdata->topGuess;
            leftCorrection   = correctWMLeft   - wdata->leftGuess;
            bottomCorrection = correctWMBottom - wdata->bottomGuess;
            rightCorrection  = correctWMRight  - wdata->rightGuess;

            DTRACE_PRINTLN3("TL: top:    computed=%d, guess=%d, correction=%d",
                correctWMTop, wdata->topGuess, topCorrection);
            DTRACE_PRINTLN3("TL: left:   computed=%d, guess=%d, correction=%d",
                correctWMLeft, wdata->leftGuess, leftCorrection);
            DTRACE_PRINTLN3("TL: bottom: computed=%d, guess=%d, correction=%d",
                correctWMBottom, wdata->bottomGuess, bottomCorrection);
            DTRACE_PRINTLN3("TL: right:  computed=%d, guess=%d, correction=%d",
                correctWMRight, wdata->rightGuess, rightCorrection);

            if (topCorrection != 0 || leftCorrection != 0
                || bottomCorrection != 0 || rightCorrection != 0)
            {
                jboolean isPacked;

                DTRACE_PRINTLN("TL: insets need correction");
                wdata->need_reshape = True;

                globalTopGuess    = correctWMTop;
                globalLeftGuess   = correctWMLeft;
                globalBottomGuess = correctWMBottom;
                globalRightGuess  = correctWMRight;

                /* guesses are for WM decor *only* */
                wdata->topGuess    = correctWMTop;
                wdata->leftGuess   = correctWMLeft;
                wdata->bottomGuess = correctWMBottom;
                wdata->rightGuess  = correctWMRight;

                /*
                 * Actual insets account for menubar/warning label,
                 * so we can't assign directly but must adjust them.
                 */
                wdata->top    += topCorrection;
                wdata->left   += leftCorrection;
                wdata->bottom += bottomCorrection;
                wdata->right  += rightCorrection;

                awtJNI_ChangeInsets(env, this, wdata);

                /*
                 * If this window has been sized by a pack() we need
                 * to keep the interior geometry intact.  Since pack()
                 * computed width and height with wrong insets, we
                 * must adjust the target dimensions appropriately.
                 */
                isPacked = (*env)->GetBooleanField(env, target,
                                                   componentIDs.isPacked);
                if (isPacked) {
                    int32_t correctTargetW;
                    int32_t correctTargetH;

                    DTRACE_PRINTLN("TL: window is packed, "
                                   "adjusting size to preserve layout");

                    correctTargetW = width + (leftCorrection + rightCorrection);
                    correctTargetH = height +(topCorrection + bottomCorrection);

                    (*env)->SetIntField(env, target, componentIDs.width,
                                        (jint) correctTargetW);
                    (*env)->SetIntField(env, target, componentIDs.height,
                                        (jint) correctTargetH);
                    /*
                    **  Normally you only reconfigure the outerCanvas due to
                    **  handling the ReconfigureNotify on the innerCanvas.
                    **  However, in this case the innerCanvas may not have
                    **  changed, but outterCanvas may still need to, since the
                    **  insets have changed.
                    */
                    reshape(env, this, wdata, x, y,
                            correctTargetW, correctTargetH, setXY);
                    reconfigureOuterCanvas(env, target, this, wdata);
                } else {
                    reshape(env, this, wdata, x, y, width, height, setXY);
                    JNU_CallMethodByName(env, NULL, this,
                        "handleResize", "(II)V", width, height);
                }
            }
/* NEW for dialog */ /* XXX: what this comment is supposed to mean? */
            else {
                wdata->need_reshape = False;
                /* fix for 4976337 - son@sparc.spb.su */
                /* we should find better fix later if needed */
                if (wdata->isResizable || !wdata->isFixedSizeSet) {
                    reshape(env, this, wdata, x, y, width, height, setXY);
                }
            }
        }
        (*env)->DeleteLocalRef(env, target);
        break;
    } /* ReparentNotify */

    case ConfigureNotify: {
        DTRACE_PRINTLN2("TL: ConfigureNotify(0x%x/0x%x)",
                        wdata->winData.shell, XtWindow(wdata->winData.shell));

        /*
         * Some window managers configure before we are reparented and
         * the send event flag is set! ugh... (Enlighetenment for one,
         * possibly MWM as well).  If we haven't been reparented yet
         * this is just the WM shuffling us into position.  Ignore
         * it!!!! or we wind up in a bogus location.
         */
        runningWM = awt_wm_getRunningWM();
        if (!wdata->reparented && wdata->isShowing &&
            runningWM != NO_WM && wdata->decor != AWT_NO_DECOR) {
            break;
        }

        /*
         * Notice that we have seen a ConfigureNotify after being
         * reparented.  We should really check for it being a
         * synthetic event, but metacity doesn't send one.
         */
        if (wdata->reparented)
            wdata->configure_seen = 1;

        if ((*env)->EnsureLocalCapacity(env, 1) < 0) {
            break;
        }
        target = (*env)->GetObjectField(env, this, mComponentPeerIDs.target);

        /*
         * We can detect the difference between a move and a resize by
         * checking the send_event flag on the event; if it's true,
         * then it's indeed a move, if it's false, then this is a
         * resize and we do not want to process it as a "move" (for
         * resizes the x,y values are misleadingly set to 0,0 and so
         * just checking for an x,y delta won't work).
         */

        getTargetX = (*env)->GetIntField(env, target, componentIDs.x);
        getTargetY = (*env)->GetIntField(env, target, componentIDs.y);

        DTRACE_PRINTLN2("TL:   target thinks (%d, %d)",
                        getTargetX, getTargetY);
        DTRACE_PRINTLN3("TL:   event is (%d, %d)%s",
                        event->xconfigure.x, event->xconfigure.y,
                        (event->xconfigure.send_event ? " synthetic" : ""));

        /*
         * N.B. The wdata top offset is the offset from the outside of
         * the entire (bordered window) to the inner/parent drawing
         * area (canvas), NOT to the shell.  Thus, if a menubar is
         * present and/or a warning window at the top (not NETSCAPE),
         * the top offset will also include space for these.  In order
         * to position the abstract java window relative to the shell,
         * we must add back in the appropriate space for these when we
         * subtract off the wdata top field.
         */
#ifdef NETSCAPE
        wwHeight = 0;
#else /* NETSCAPE */
        if (wdata->warningWindow != NULL)
            wwHeight = wdata->wwHeight;
        else
            wwHeight = 0;
#endif /* NETSCAPE */
        topAdjust = wdata->mbHeight + wwHeight;

        /*
         * Coordinates in Component.setLocation() are treated as the
         * upper-left corner of the outer shell.  The x and y in the
         * ConfigureNotify event, however, are the upper-left corner
         * of the inset CLIENT window.  Therefore, the coordinates
         * from the event are massaged using the inset values in order
         * to determine if the top-level shell has moved.  In the
         * event of a user- generated move event (i.e. dragging the
         * window itself), these coordinates are written back into the
         * Window object.
         *
         * Neat X/CDE/Native bug:
         * If an attempt is made to move the shell in the y direction
         * by an amount equal to the top inset, the Window isn't
         * moved.  This can be seen here by examining event->xconfigure.y
         * before and after such a request is made: the value remains
         * unchanged.  This wrecks a little havoc here, as the x and y
         * in the Component have already been set to the new location
         * (in Component.reshape()), but the Window doesn't end up in
         * the new location.  What's more, if a second request is
         * made, the window will be relocated by TWICE the requested
         * amount, sort of "catching up" it would seem.
         *
         * For a test case of this, see bug 4234645.
         */
        setTargetX = event->xconfigure.x - wdata->left;
        setTargetY = event->xconfigure.y - wdata->top + topAdjust;

        width = (*env)->GetIntField(env, target, componentIDs.width);
        height = (*env)->GetIntField(env, target, componentIDs.height);
        checkNewXineramaScreen(env, this, wdata, setTargetX, setTargetY,
                               width, height);

        if ((getTargetX != setTargetX || getTargetY != setTargetY)
            && (event->xconfigure.send_event || runningWM == NO_WM))
        {
            (*env)->SetIntField(env, target, componentIDs.x, (jint)setTargetX);
            (*env)->SetIntField(env, target, componentIDs.y, (jint)setTargetY);
#ifdef _pauly_debug
            fprintf(stdout, " ++ shell move. Xevent x,y: %d, %d.\n",
                    event->xconfigure.x, event->xconfigure.y);
            fprintf(stdout, "    shell move. left: %d, top: %d, but offset: %d\n", wdata->left, wdata->top, topAdjust);
            fprintf(stdout,"    shell move. target x: %d, target y: %d\n", setTargetX, setTargetY);
            fprintf(stdout,"    shell move. ww height: %d\n", wwHeight);
            fflush(stdout);
#endif /* _pauly_debug */

            DTRACE_PRINTLN2("TL:   handleMoved(%d, %d)",
                            setTargetX, setTargetY);
            JNU_CallMethodByName(env, NULL,
                                 this, "handleMoved", "(II)V",
                                 setTargetX, setTargetY);
            if ((*env)->ExceptionOccurred(env)) {
                (*env)->ExceptionDescribe(env);
                (*env)->ExceptionClear(env);
            }
        }
        else if (event->xconfigure.send_event == False) {
#ifdef _pauly_debug
            fprintf(stdout,
                    " ++ shell resize. Xevent x,y,w,h: %d, %d, %d, %d.\n",
                    event->xconfigure.x, event->xconfigure.y,
                    event->xconfigure.width, event->xconfigure.height);
            fflush(stdout);
#endif /* _pauly_debug */

            wdata->shellResized = True;
        }


        (*env)->DeleteLocalRef(env, target);
        raiseInputMethodWindow(wdata);
#ifdef __linux__
        adjustStatusWindow(wdata->winData.shell);
#endif
        break;
    } /* ConfigureNotify */

    default:
        break;
    }
}


static void
Frame_quit(Widget w,
           XtPointer client_data,
           XtPointer call_data)
{
    JNIEnv *env = (JNIEnv *)JNU_GetEnv(jvm, JNI_VERSION_1_2);

    JNU_CallMethodByName(env, NULL, (jobject) client_data, "handleQuit", "()V");
    if ((*env)->ExceptionOccurred(env)) {
        (*env)->ExceptionDescribe(env);
        (*env)->ExceptionClear(env);
    }
}


static void
setDeleteCallback(jobject this, struct FrameData *wdata)
{
    Atom xa_WM_DELETE_WINDOW;
    Atom xa_WM_TAKE_FOCUS;
    Atom xa_WM_PROTOCOLS;

    XtVaSetValues(wdata->winData.shell,
                  XmNdeleteResponse, XmDO_NOTHING,
                  NULL);
    xa_WM_DELETE_WINDOW = XmInternAtom(XtDisplay(wdata->winData.shell),
                                       "WM_DELETE_WINDOW", False);
    xa_WM_TAKE_FOCUS = XmInternAtom(XtDisplay(wdata->winData.shell),
                                    "WM_TAKE_FOCUS", False);
    xa_WM_PROTOCOLS = XmInternAtom(XtDisplay(wdata->winData.shell),
                                   "WM_PROTOCOLS", False);

    XmAddProtocolCallback(wdata->winData.shell,
                          xa_WM_PROTOCOLS,
                          xa_WM_DELETE_WINDOW,
                          Frame_quit, (XtPointer) this);
}


extern AwtGraphicsConfigDataPtr
copyGraphicsConfigToPeer(JNIEnv *env, jobject this);

extern AwtGraphicsConfigDataPtr
getGraphicsConfigFromComponentPeer(JNIEnv *env, jobject this);

// Returns true if this shell has some transient shell chidlren
// which are either Dialogs or Windows.
// Returns false otherwise.
Boolean hasTransientChildren(Widget shell) {
    int childIndex;

    // Enumerate through the popups
    for (childIndex = 0; childIndex < shell->core.num_popups; childIndex++) {
        Widget childShell = shell->core.popup_list[childIndex];
        // Find all transient shell which are either Dialog or Window
        if (XtIsTransientShell(childShell)) {
            Widget toplevel = findTopLevelByShell(childShell);
            if (toplevel != NULL) {
                // It is Dialog or Window - return true.
                return True;
            }
        }
    }
    return False;
}

extern Widget grabbed_widget;
/**
 * Disposes top-level component and its widgets
 */
static
void disposeTopLevel(JNIEnv * env, jobject this) {

    struct FrameData *wdata;
    Widget parentShell;

    wdata = (struct FrameData *)
        JNU_GetLongFieldAsPtr(env, this, mComponentPeerIDs.pData);

    if (wdata == NULL || wdata->mainWindow == NULL
        || wdata->winData.shell == NULL)
    {
        /* do nothing */
        return;
    }

    // Save parent shell for later disposal.
    parentShell = XtParent(wdata->winData.shell);

    removeTopLevel(wdata);
    if (wdata->isInputMethodWindow) {
        removeInputMethodWindow(wdata);
    }

    XtRemoveEventHandler(wdata->focusProxy, FocusChangeMask,
                         False, shellEH, this);
    XtUnmanageChild(wdata->focusProxy);
    awt_util_consumeAllXEvents(wdata->focusProxy);
    awt_util_cleanupBeforeDestroyWidget(wdata->focusProxy);
    XtDestroyWidget(wdata->focusProxy);

    XtUnmanageChild(wdata->winData.comp.widget);
    awt_delWidget(wdata->winData.comp.widget);
    awt_util_consumeAllXEvents(wdata->winData.comp.widget);
    awt_util_cleanupBeforeDestroyWidget(wdata->winData.comp.widget);
    XtDestroyWidget(wdata->winData.comp.widget);

    XtUnmanageChild(wdata->mainWindow);
    awt_util_consumeAllXEvents(wdata->mainWindow);
    awt_util_consumeAllXEvents(wdata->winData.shell);
    XtDestroyWidget(wdata->mainWindow);
    XtDestroyWidget(wdata->winData.shell);
    if (wdata->iconPixmap) {
        XFreePixmap(awt_display, wdata->iconPixmap);
    }

    if (grabbed_widget == wdata->winData.shell) {
        XUngrabPointer(awt_display, CurrentTime);
        XUngrabKeyboard(awt_display, CurrentTime);
        grabbed_widget = NULL;
    }

    free((void *) wdata);

    (*env)->SetLongField(env, this, mComponentPeerIDs.pData, 0);
    awtJNI_DeleteGlobalRef(env, this);

    // Check if parent shell was scheduled for disposal.
    // If it doesn't have window then we have to dispose it
    // by ourselves right now.
    // We can dispose shell only if it doesn't have "transient" children.
    {
        struct FrameData *pdata;
        struct WidgetInfo* winfo;
        Widget toplevel = findTopLevelByShell(parentShell);
        if (toplevel == NULL) {
            // Has already been deleted or it is top shell
            return;
        }
        winfo = findWidgetInfo(toplevel);
        DASSERT(winfo != NULL);
        if (winfo == NULL) {
            // Huh - has already been deleted?
            return;
        }
        pdata = (struct FrameData *)
            JNU_GetLongFieldAsPtr(env, winfo->peer, mComponentPeerIDs.pData);
        DASSERT(pdata != NULL);
        if (pdata == NULL) {
            // Huh - has already been deleted?
            return;
        }
        // 1) scheduled 2) no children 3) no window
        if (pdata->isDisposeScheduled
            && !hasTransientChildren(parentShell)
            && XtWindow(parentShell) == None)
        {
            disposeTopLevel(env, winfo->peer);
        }
    }
}


/**
 * Property change listener. Listens to _XA_JAVA_DISPOSE_PROPERTY_ATOM,
 * disposes the top-level when this property has been changed.
 */
static void
shellDisposeNotifyHandler(Widget w, XtPointer client_data,
                           XEvent* event, Boolean* continue_to_dispatch) {
    struct FrameData *wdata;

    *continue_to_dispatch = True;

    if (event->type == PropertyNotify &&
        event->xproperty.atom == _XA_JAVA_DISPOSE_PROPERTY_ATOM)
    {
        JNIEnv *env = (JNIEnv *)JNU_GetEnv(jvm, JNI_VERSION_1_2);

        wdata = (struct FrameData *)
            JNU_GetLongFieldAsPtr(env, (jobject)client_data,
                                  mComponentPeerIDs.pData);
        if (wdata != NULL && wdata->isDisposeScheduled) {
            disposeTopLevel(env, (jobject)client_data);

            // We've disposed top-level, no more actions on it
            *continue_to_dispatch = False;
        }
    }
}

/**
 * Schedules top-level for later dispose - when all events
 * on it will be processed.
 */
static
void scheduleDispose(JNIEnv * env, jobject peer) {

    struct FrameData *wdata;

    wdata = (struct FrameData *)
        JNU_GetLongFieldAsPtr(env, peer, mComponentPeerIDs.pData);

    if (wdata->isDisposeScheduled) {
        return;
    }

    wdata->isDisposeScheduled = True;
    if (XtWindow(wdata->winData.shell) != None) {
        XChangeProperty(awt_display, XtWindow(wdata->winData.shell),
                        _XA_JAVA_DISPOSE_PROPERTY_ATOM, XA_ATOM, 32, PropModeAppend,
                        (unsigned char *)"", 0);
        XFlush(awt_display);
        XSync(awt_display, False);
    } else {
        // If this top-level has children which are still visible then
        // their disposal could have been scheduled. We shouldn't allow this widget
// to destroy its children top-levels. For this purpose we postpone the disposal
        // of this toplevel until after all its children are disposed.
        if (!hasTransientChildren(wdata->winData.shell)) {
            disposeTopLevel(env, peer);
        }
    }
}


/* sun_awt_motif_MWindowPeer_pCreate() is native (X/Motif) create routine */
static char* focusProxyName = "FocusProxy";

Widget createFocusProxy(jobject globalRef, Widget parent) {
    Widget proxy;
#define MAX_ARGC 20
    Arg args[MAX_ARGC];
    int32_t argc;

    JNIEnv *env = (JNIEnv *)JNU_GetEnv(jvm, JNI_VERSION_1_2);

    if (parent == NULL) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        return NULL;
    }
    argc = 0;
    XtSetArg(args[argc], XmNwidth, 1);
    argc++;
    XtSetArg(args[argc], XmNheight, 1);
    argc++;
    XtSetArg(args[argc], XmNx, -1);
    argc++;
    XtSetArg(args[argc], XmNy, -1);
    argc++;
    XtSetArg(args[argc], XmNmarginWidth, 0);
    argc++;
    XtSetArg(args[argc], XmNmarginHeight, 0);
    argc++;
    XtSetArg(args[argc], XmNspacing, 0);
    argc++;
    XtSetArg(args[argc], XmNresizePolicy, XmRESIZE_NONE);
    argc++;

    DASSERT(!(argc > MAX_ARGC));
    proxy = XmCreateDrawingArea(parent, focusProxyName, args, argc);
    XtAddEventHandler(proxy,
                      FocusChangeMask,
                      False, shellEH, globalRef);
    XtManageChild(proxy);
#undef MAX_ARGC
    return proxy;
}

/*
 * Class:     sun_awt_motif_MWindowPeer
 * Method:    pCreate
 * Signature: (Lsun/awt/motif/MComponentPeer;Ljava/lang/String;)V
 */
JNIEXPORT void JNICALL
Java_sun_awt_motif_MWindowPeer_pCreate(JNIEnv *env, jobject this,
    jobject parent, jstring target_class_name, jboolean isFocusableWindow)
{
#define MAX_ARGC 50
    Arg                 args[MAX_ARGC];
    int32_t             argc;
    struct FrameData    *wdata;
    struct FrameData    *pdata = NULL;
    char                *shell_name = NULL;
    WidgetClass         shell_class;
    Widget              parent_widget;
    jobject             target;
    jobject             insets;
    jobject             winAttr;
    jstring             warningString;
    jboolean            resizable;
    jboolean            isModal;
    jboolean            initialFocus;
    jint                state;
    jclass              clazz;
    jobject             globalRef = awtJNI_CreateAndSetGlobalRef(env, this);

    uint32_t            runningWM;      /* the running Window Manager   */
    Widget              innerCanvasW;   /* form's child, parent of the
                                           outer canvas (drawing area)  */
    Position            x,y;
    Dimension           w,h;
    AwtGraphicsConfigDataPtr adata;
    AwtGraphicsConfigDataPtr defConfig;
    jobject gd = NULL;
    jobject gc = NULL;
    char *cname = NULL;
    jstring jname;

    AWT_LOCK();

    target = (*env)->GetObjectField(env, this, mComponentPeerIDs.target);

    if (JNU_IsNull(env, target)) {
        JNU_ThrowNullPointerException(env, "null target");
        AWT_UNLOCK();
        return;
    }

    wdata = ZALLOC(FrameData);
    JNU_SetLongFieldFromPtr(env, this, mComponentPeerIDs.pData, wdata);
    if (wdata == NULL) {
        JNU_ThrowOutOfMemoryError(env, "OutOfMemoryError");
        AWT_UNLOCK();
        return;
    }

    adata = copyGraphicsConfigToPeer(env, this);
    defConfig = getDefaultConfig(adata->awt_visInfo.screen);


    /* Retrieve the specified characteristics for this window */
    winAttr = (*env)->GetObjectField(env, this, mWindowPeerIDs.winAttr);
    resizable = (*env)->GetBooleanField( env,
                                         winAttr,
                                         mWindowAttributeIDs.isResizable);
    state = (*env)->GetIntField( env,
                                 winAttr,
                                 mWindowAttributeIDs.initialState);
    initialFocus = (*env)->GetBooleanField( env,
                                            winAttr,
                                            mWindowAttributeIDs.initialFocus);

    /* As of today decor is either on or off... except the InputMethodWindow */
    if ((*env)->GetBooleanField(env, winAttr, mWindowAttributeIDs.nativeDecor)) {
        wdata->decor = (*env)->GetIntField(env, winAttr, mWindowAttributeIDs.decorations);
    } else {
        wdata->decor = AWT_NO_DECOR;
    }

    insets = (*env)->GetObjectField(env, this, mWindowPeerIDs.insets);

    /* The insets will be corrected upon the reparent
           event in shellEH().  For now, use bogus values.      */
    wdata->top = (*env)->GetIntField(env, insets, insetsIDs.top);
    wdata->left = (*env)->GetIntField(env, insets, insetsIDs.left);
    wdata->bottom = (*env)->GetIntField(env, insets, insetsIDs.bottom);
    wdata->right = (*env)->GetIntField(env, insets, insetsIDs.right);
    awt_Frame_guessInsets(wdata);
    awtJNI_ChangeInsets(env, this, wdata);
    wdata->reparented = False;
    wdata->configure_seen = False;
    x = (*env)->GetIntField(env, target, componentIDs.x) + wdata->left;
    y = (*env)->GetIntField(env, target, componentIDs.y) + wdata->top;

    w = (*env)->GetIntField(env, target, componentIDs.width)
        - (wdata->left + wdata->right);
    h = (*env)->GetIntField(env, target, componentIDs.height)
        - (wdata->top + wdata->bottom);
    if (w < 0) w = 0;
    if (h < 0) h = 0;

    DTRACE_PRINTLN1("TL: pCreate: state = 0x%X", state);

    wdata->isModal = 0;
    wdata->initialFocus = (Boolean)initialFocus;
    wdata->isShowing = False;
    wdata->shellResized = False;
    wdata->canvasResized = False;
    wdata->menuBarReset = False;
    wdata->need_reshape = False;
    wdata->focusProxy = NULL;
#ifdef __linux__
    wdata->fixInsets = True;
#endif
    wdata->state = state;

    /* initialize screen to screen number in GraphicsConfig's device */
    /* can the Window's GC ever be null? */
    gc =  (*env)->GetObjectField(env, target, componentIDs.graphicsConfig);
    DASSERT(gc);

    gd =  (*env)->GetObjectField(env, gc, x11GraphicsConfigIDs.screen);
    DASSERT(gd);

    wdata->screenNum = (*env)->GetIntField(env, gd, x11GraphicsDeviceIDs.screen);

    wdata->isFocusableWindow = (Boolean)isFocusableWindow;

    /*
     * Create a top-level shell widget.
     */
    argc = 0;
    XtSetArg(args[argc], XmNsaveUnder, False); argc++;
    if (resizable) {
        XtSetArg(args[argc], XmNallowShellResize, True); argc++;
    } else {
        XtSetArg(args[argc], XmNallowShellResize, False); argc++;
    }
    XtSetArg(args[argc], XmNvisual, defConfig->awt_visInfo.visual); argc++;
    XtSetArg(args[argc], XmNcolormap, defConfig->awt_cmap); argc++;
    XtSetArg(args[argc], XmNdepth, defConfig->awt_depth); argc++;
    XtSetArg(args[argc], XmNmappedWhenManaged, False); argc++;
    XtSetArg(args[argc], XmNx, x); argc++;
    XtSetArg(args[argc], XmNy, y); argc++;
    XtSetArg(args[argc], XmNwidth, w); argc++;
    XtSetArg(args[argc], XmNheight, h); argc++;

    XtSetArg(args[argc], XmNbuttonFontList, getMotifFontList()); argc++;
    XtSetArg(args[argc], XmNlabelFontList, getMotifFontList()); argc++;
    XtSetArg(args[argc], XmNtextFontList, getMotifFontList()); argc++;

    XtSetArg(args[argc], XmNmwmDecorations, wdata->decor); argc++;
    XtSetArg(args[argc], XmNscreen,
             ScreenOfDisplay(awt_display, defConfig->awt_visInfo.screen)); argc++;

    if (wdata->initialFocus == False || !isFocusableWindowByPeer(env, this)) {
        XtSetArg(args[argc], XmNinput, False); argc++;
    }

    if (wdata->decor == AWT_NO_DECOR) {
        /* this is heinous but it can not be avoided for now.
         ** this is the only known way to eliminate all decorations
         ** for openlook, which btw, is a bug as ol theoretically
         ** supports MWM_HINTS
         */
#ifndef DO_FULL_DECOR
        if (awt_wm_getRunningWM() == OPENLOOK_WM) {
            XtSetArg(args[argc], XmNoverrideRedirect, True);
            argc++;
        }
#endif
    }

    /* 4334958: Widget name is set to the Java class name */
    shell_name =
        (char *)JNU_GetStringPlatformChars(env, target_class_name, NULL);

    if (parent) {
        pdata = (struct FrameData *)
            (*env)->GetLongField(env, parent, mComponentPeerIDs.pData);
    }

    /* Parenting tells us whether we wish to be transient or not */
    if (pdata == NULL) {
        if (!shell_name)
            shell_name = "AWTapp";
        shell_class =  topLevelShellWidgetClass;
        parent_widget = awt_root_shell;
    }
    else {
        if (!shell_name)
            shell_name = "AWTdialog";
        shell_class = transientShellWidgetClass;
        parent_widget = pdata->winData.shell;
        XtSetArg(args[argc], XmNtransient, True); argc++;
        XtSetArg(args[argc], XmNtransientFor, parent_widget); argc++;

        /* Fix Forte Menu Bug. If Window name is "###overrideRedirect###",
         * then set XmNoverrideRedirect to prevent Menus from getting focus.
         * In JDK 1.2.2 we created Windows as xmMenuShellWidgetClass,
         * so we did not need to do this. Swing DefaultPopupFactory's
         * createHeavyWeightPopup sets Window name to "###overrideRedirect###".
        */
        /**
         * Fix for 4476629. Allow Swing to create heavyweight popups which will
         * not steal focus from Frame.
         */
        jname = (*env)->GetObjectField(env, target, componentIDs.name);
        if (!JNU_IsNull(env, jname)) {
          cname = (char *)JNU_GetStringPlatformChars(env, jname, NULL);
        }
        if ( (cname != NULL && strcmp(cname, "###overrideRedirect###") == 0)
            || (!isFrameOrDialog(target, env)
                && !isFocusableWindowByPeer(env, this)
                )
            )
        {    /* mbron */
            XtSetArg(args[argc], XmNoverrideRedirect, True);
            argc++;
        }
        if (cname) {
            JNU_ReleaseStringPlatformChars(env, jname, (const char *) cname);
        }
        (*env)->DeleteLocalRef(env, jname);
    }
    DASSERT(!(argc > MAX_ARGC));
    wdata->winData.shell = XtCreatePopupShell(shell_name, shell_class,
                                              parent_widget, args, argc);
    if (shell_name) {
        JNU_ReleaseStringPlatformChars(env, target_class_name, shell_name);
    }

#ifdef DEBUG
    /* Participate in EditRes protocol to facilitate debugging */
    XtAddEventHandler(wdata->winData.shell, (EventMask)0, True,
                      _XEditResCheckMessages, NULL);
#endif

    setDeleteCallback(globalRef, wdata);

    /* Establish resizability.  For the case of not resizable, do not
       yet set a fixed size here; we must wait until in the routine
       sun_awt_motif_MWindowPeer_pReshape() after insets have been fixed.
       This is because correction of the insets may affect shell size.
       (See comments in shellEH() concerning correction of the insets.  */
    /*
     * Fix for BugTraq ID 4313607.
     * Initial resizability will be set later in MWindowPeer_setResizable()
     * called from init().
     */
    wdata->isResizable = True;
    wdata->isFixedSizeSet = False;

    XtAddEventHandler(wdata->winData.shell,
                      (StructureNotifyMask | PropertyChangeMask
                       | VisibilityChangeMask),
                      False, shellEH, globalRef);

    XtAddEventHandler(wdata->winData.shell,
                      FocusChangeMask,
                      False, shellFocusEH, globalRef);


    /**
     * Installing property change handler for DISPOSE property.
     * This property will be changed when we need to dispose the whole
     * top-level. The nature of PropertyNotify will guarantee that it is
     * the latest event on the top-level so we can freely dispose it.
     */
    wdata->isDisposeScheduled = False;
    if (_XA_JAVA_DISPOSE_PROPERTY_ATOM == 0) {
        _XA_JAVA_DISPOSE_PROPERTY_ATOM = XInternAtom(awt_display, "_SUNW_JAVA_AWT_DISPOSE", False);
    }
    XtAddEventHandler(wdata->winData.shell, PropertyChangeMask, False,
                      shellDisposeNotifyHandler, globalRef);

    /*
     * Create "main" form.
     */
    argc = 0;
    XtSetArg(args[argc], XmNmarginWidth, 0); argc++;
    XtSetArg(args[argc], XmNmarginHeight, 0); argc++;
    XtSetArg(args[argc], XmNhorizontalSpacing, 0); argc++;
    XtSetArg(args[argc], XmNverticalSpacing, 0); argc++;
    XtSetArg(args[argc], XmNresizePolicy, XmRESIZE_NONE); argc++;

    XtSetArg(args[argc], XmNbuttonFontList, getMotifFontList()); argc++;
    XtSetArg(args[argc], XmNlabelFontList, getMotifFontList()); argc++;
    XtSetArg(args[argc], XmNtextFontList, getMotifFontList()); argc++;

    DASSERT(!(argc > MAX_ARGC));
    wdata->mainWindow = XmCreateForm(wdata->winData.shell, "main", args, argc);

    /* The widget returned by awt_canvas_create is a drawing area
       (i.e., canvas) which is the child of another drawing area
       parent widget.  The parent is the drawing area within the
       form just created.  The child is an drawing area layer over
       the entire frame window, including the form, any menu bar
       and warning windows present, and also window manager stuff.
       The top, bottom, left, and right fields in wdata maintain
       the respective offsets between these two drawing areas.  */

    wdata->winData.comp.widget = awt_canvas_create((XtPointer)globalRef,
                                                   wdata->mainWindow,
                                                   "frame_",
                                                   -1,
                                                   -1,
                                                   True,
                                                   wdata,
                                                   adata);
    XtAddCallback(wdata->winData.comp.widget,
                  XmNresizeCallback, outerCanvasResizeCB,
                  globalRef);

    innerCanvasW = XtParent(wdata->winData.comp.widget);
    XtVaSetValues(innerCanvasW,
                  XmNleftAttachment, XmATTACH_FORM,
                  XmNrightAttachment, XmATTACH_FORM,
                  NULL);

    XtAddEventHandler(innerCanvasW, StructureNotifyMask, FALSE,
                      innerCanvasEH, globalRef);

    wdata->focusProxy = createFocusProxy((XtPointer)globalRef,
                                         wdata->mainWindow);

    /* No menu bar initially */
    wdata->menuBar = NULL;
    wdata->mbHeight = 0;

    /* If a warning window (string) is needed, establish it now.*/
    warningString =
        (*env)->GetObjectField(env, target, windowIDs.warningString);
    if (!JNU_IsNull(env, warningString) ) {
        char *wString;
        /* Insert a warning window. It's height can't be set yet;
           it will later be set in setMbAndWwHeightAndOffsets().*/
        wString = (char *) JNU_GetStringPlatformChars(env, warningString, NULL);
        wdata->warningWindow = awt_util_createWarningWindow(wdata->mainWindow, wString);
        JNU_ReleaseStringPlatformChars(env, warningString, (const char *) wString);

        wdata->wwHeight = 0;
        XtVaSetValues(wdata->warningWindow,
                      XmNleftAttachment, XmATTACH_FORM,
                      XmNrightAttachment, XmATTACH_FORM,
                      NULL);

#ifdef NETSCAPE
        /* For NETSCAPE, warning window is at bottom of the form*/
        XtVaSetValues(innerCanvasW,
                      XmNtopAttachment, XmATTACH_FORM,
                      NULL);
        XtVaSetValues(wdata->warningWindow,
                      XmNtopAttachment, XmATTACH_WIDGET,
                      XmNtopWidget, innerCanvasW,
                      XmNbottomAttachment, XmATTACH_FORM,
                      NULL);
#else  /* NETSCAPE */
        /* Otherwise (not NETSCAPE), warning is at top of form  */
        XtVaSetValues(wdata->warningWindow,
                      XmNtopAttachment, XmATTACH_FORM,
                      NULL);
        XtVaSetValues(innerCanvasW,
                      XmNtopAttachment, XmATTACH_WIDGET,
                      XmNtopWidget, wdata->warningWindow,
                      XmNbottomAttachment, XmATTACH_FORM,
                      NULL);
#endif /* NETSCAPE */

    } else {
        /* No warning window present */
        XtVaSetValues(innerCanvasW,
                      XmNtopAttachment, XmATTACH_FORM,
                      XmNbottomAttachment, XmATTACH_FORM,
                      NULL);
        wdata->warningWindow = NULL;
        wdata->wwHeight = 0;
    }

    awt_util_show(wdata->winData.comp.widget);

    AWT_FLUSH_UNLOCK();

    addTopLevel(wdata);

    /* Check whether this is an instance of InputMethodWindow or not */
    if (inputMethodWindowClass == NULL) {
        jclass localClass = (*env)->FindClass(env, "sun/awt/im/InputMethodWindow");
        inputMethodWindowClass = (jclass)(*env)->NewGlobalRef(env, localClass);
        (*env)->DeleteLocalRef(env, localClass);
    }
    if ((*env)->IsInstanceOf(env, target, inputMethodWindowClass)) {
        wdata->isInputMethodWindow = True;
        addInputMethodWindow(wdata);
    }
} /* MWindowPeer_pCreate() */


/*
 * Class:     sun_awt_motif_MWindowPeer
 * Method:    pSetTitle
 * Signature: (Ljava/lang/String;)V
 */
JNIEXPORT void JNICALL
Java_sun_awt_motif_MWindowPeer_pSetTitle(JNIEnv *env, jobject this,
    jstring title)
{
    char *ctitle;
    char *empty_string = " ";
    struct FrameData *wdata;
    XTextProperty text_prop;
    char *c[1];
    int32_t conv_result;

    AWT_LOCK();

    wdata = (struct FrameData *)
        JNU_GetLongFieldAsPtr(env, this, mComponentPeerIDs.pData);

    if (wdata == NULL || wdata->winData.shell == NULL) {
        JNU_ThrowNullPointerException(env, "null wdata or shell");
        AWT_UNLOCK();
        return;
    }

    /* TODO: uwe: set _NET_WM_NAME property to utf-8 name */

    ctitle = (JNU_IsNull(env, title)) ? empty_string
        : (char *) JNU_GetStringPlatformChars(env, title, NULL);

    if (strcmp(ctitle, "") == 0)
        ctitle = empty_string;

    c[0] = ctitle;

    /* need to convert ctitle to CompoundText */
    conv_result = XmbTextListToTextProperty(awt_display, c, 1,
                                            XStdICCTextStyle,
                                            &text_prop);

    /*
     * XmbTextListToTextProperty returns value that is greater
     * than Success if the supplied text is not fully convertible
     * to specified encoding. In this case, the return value is
     * the number of inconvertible characters. But convertibility
     * is guaranteed for XCompoundTextStyle, so it will actually
     * never be greater than Success. Errors handled below are
     * represented by values that are lower than Success.
     */
    if (conv_result >= Success) {
        XtVaSetValues(wdata->winData.shell,
                  XmNtitle, text_prop.value,
                  XmNtitleEncoding, text_prop.encoding,
                  XmNiconName, text_prop.value,
                  XmNiconNameEncoding, text_prop.encoding,
                  XmNname, ctitle,
                  NULL);
    }

    if (ctitle != empty_string)
        JNU_ReleaseStringPlatformChars(env, title, (const char *) ctitle);

    if (conv_result == XNoMemory) {
        JNU_ThrowOutOfMemoryError(env, "OutOfMemoryError");
        AWT_UNLOCK();
        return;
    }
    if (conv_result == XLocaleNotSupported) {
        JNU_ThrowInternalError(env, "Current locale is not supported");
        AWT_UNLOCK();
        return;
    }

    XFree(text_prop.value);
    AWT_FLUSH_UNLOCK();
}

/*
 * Class:     sun_awt_motif_MWindowPeer
 * Method:    pToFront
 * Signature: ()V
 */
JNIEXPORT void JNICALL
Java_sun_awt_motif_MWindowPeer_pToFront(JNIEnv *env, jobject this)
{
    struct FrameData *wdata;
    jobject target;
    Window shellWindow;
    Boolean autoRequestFocus;
    Boolean isModal = FALSE;

    AWT_LOCK();

    target = (*env)->GetObjectField(env, this, mComponentPeerIDs.target);

    wdata = (struct FrameData *)
        JNU_GetLongFieldAsPtr(env, this, mComponentPeerIDs.pData);
    if (wdata == NULL
        || wdata->winData.comp.widget == NULL
        || wdata->winData.shell == NULL
        || wdata->mainWindow == NULL
        || JNU_IsNull(env, target))
    {
        JNU_ThrowNullPointerException(env, "null widget/target data");
        AWT_UNLOCK();
        return;
    }

    if ((shellWindow = XtWindow(wdata->winData.shell)) != None) {
        XRaiseWindow(awt_display, shellWindow);

        autoRequestFocus = (*env)->GetBooleanField(env, target, windowIDs.isAutoRequestFocus);

        if (isDialog(target, env)) {
            isModal = (*env)->GetBooleanField(env, target, dialogIDs.modal);
        }

        // In contrast to XToolkit/WToolkit modal dialog can be unfocused.
        // So we should also ask for modality in addition to 'autoRequestFocus'.
        if (wdata->isFocusableWindow && (autoRequestFocus || isModal)) {
            XSetInputFocus(awt_display, XtWindow(wdata->focusProxy), RevertToPointerRoot, CurrentTime);
        }
    }

   (*env)->DeleteLocalRef(env, target);

    AWT_UNLOCK();
}

/*
 * Class:     sun_awt_motif_MWindowPeer
 * Method:    pShow
 * Signature: ()V
 */
JNIEXPORT void JNICALL
Java_sun_awt_motif_MWindowPeer_pShow(JNIEnv *env, jobject this)
{
    Java_sun_awt_motif_MWindowPeer_pShowModal(env, this, JNI_FALSE);
}

/*
 * Class:     sun_awt_motif_MWindowPeer
 * Method:    pShowModal
 * Signature: (Z)V
 */
JNIEXPORT void JNICALL
Java_sun_awt_motif_MWindowPeer_pShowModal(JNIEnv *env, jobject this,
    jboolean isModal)
{
    struct FrameData *wdata;
    Boolean iconic;
    jobject target;
    Boolean locationByPlatform;

    AWT_LOCK();

    target = (*env)->GetObjectField(env, this, mComponentPeerIDs.target);

    wdata = (struct FrameData *)
        JNU_GetLongFieldAsPtr(env, this, mComponentPeerIDs.pData);
    if (wdata == NULL
        || wdata->winData.comp.widget == NULL
        || wdata->winData.shell == NULL
        || wdata->mainWindow == NULL
        || (wdata->winData.flags & W_IS_EMBEDDED)
        || JNU_IsNull(env, target))
    {
        JNU_ThrowNullPointerException(env, "null widget/target data");
        AWT_UNLOCK();
        return;
    }

    DTRACE_PRINTLN2("TL: pShowModal(modal = %s) state = 0x%X",
                    isModal ? "true" : "false",
                    wdata->state);

    wdata->isModal = isModal;

    /*
     * A workaround for bug 4062589 that is really a motif problem
     * (see bug 4064803).  Before popping up a modal dialog, if a
     * pulldown menu has the input focus (i.e. user has pulled the
     * menu down), we send a fake click event and make sure the click
     * event is processed.  With this simulation of user clicking, X
     * server will not get confused about the modality and a
     * subsequent click on the popup modal dialog will not cause
     * system lockup.
     */
    if (wdata->isModal && awt_util_focusIsOnMenu(awt_display)
        && awt_util_sendButtonClick(awt_display, InputFocus))
    {
        for (;;) {
            XEvent ev;
            XtAppPeekEvent(awt_appContext, &ev);
            if ((ev.type == ButtonRelease)
                && (*(XButtonEvent *)&ev).send_event)
            {
                XtAppProcessEvent(awt_appContext, XtIMAll);
                break;
            } else {
                XtAppProcessEvent(awt_appContext, XtIMAll);
            }
        }
    }
    target = (*env)->GetObjectField(env, this, mComponentPeerIDs.target);

    // 4488209: kdm@sparc.spb.su
    // wdata->isShowing is True when toFront calls pShow.
    // We do not need to do some things if wdata->isShowing is True.
    if (!wdata->isShowing) {
        XtVaSetValues(wdata->winData.comp.widget,
                      XmNx, -(wdata->left),
                      XmNy, -(wdata->top),
                      NULL);

        /* But see below! */
        iconic = (wdata->state & java_awt_Frame_ICONIFIED) ? True : False;
        XtVaSetValues(wdata->winData.shell,
                      XmNinitialState, iconic ? IconicState : NormalState,
                      NULL);

        if (wdata->menuBar != NULL) {
            awt_util_show(wdata->menuBar);
        }
        XtManageChild(wdata->mainWindow);
        XtRealizeWidget(wdata->winData.shell); /* but not map it yet */

/*         fprintf(stderr, "*** proxy window %x\n", XtWindow(wdata->focusProxy)); */
        XStoreName(awt_display, XtWindow(wdata->focusProxy), "FocusProxy");
        /*
         * Maximization and other stuff that requires a live Window to set
         * properties on to communicate with WM.
         */
        awt_wm_setExtendedState(wdata, wdata->state);
        awt_wm_setShellDecor(wdata, wdata->isResizable);

        if (wdata->isModal) {
            removePopupMenus();
#ifndef NOMODALFIX
            /*
             * Fix for 4078176 Modal dialogs don't act modal
             * if addNotify() is called before setModal(true).
             * Moved from Java_sun_awt_motif_MDialogPeer_create.
             */
            if (!wdata->callbacksAdded) {
                XtAddCallback(wdata->winData.shell,
                              XtNpopupCallback, awt_shellPoppedUp,
                              NULL);
                XtAddCallback(wdata->winData.shell,
                              XtNpopdownCallback, awt_shellPoppedDown,
                              NULL);
                wdata->callbacksAdded = True;
            }
#endif /* !NOMODALFIX */
            /*
             * Set modality on the Shell, not the BB.  The BB expects that
             * its parent is an xmDialogShell, which as the result of
             * coalescing is now a transientShell...  This has resulted in
             * a warning message generated under fvwm.  The shells are
             * virtually identical and a review of Motif src suggests that
             * setting dialog style on BB is a convenience not functional
             * for BB so set Modality on shell, not the BB(form) widget.
             */
            XtVaSetValues(wdata->winData.shell,
                          XmNmwmInputMode, MWM_INPUT_FULL_APPLICATION_MODAL,
                          NULL);
            XtManageChild(wdata->winData.comp.widget);
        }
        else {                  /* not modal */
            XtVaSetValues(wdata->winData.shell,
                          XmNmwmInputMode, MWM_INPUT_MODELESS, NULL);
            XtManageChild(wdata->winData.comp.widget);
            XtSetMappedWhenManaged(wdata->winData.shell, True);
        }
        if (wdata->isResizable) {
            /* REMINDER: uwe: will need to revisit for setExtendedStateBounds */
            awt_wm_removeSizeHints(wdata->winData.shell, PMinSize|PMaxSize);
        }
        locationByPlatform =
            (*env)->GetBooleanField(env, target, windowIDs.locationByPlatform);
        if (locationByPlatform) {
            awt_wm_removeSizeHints(wdata->winData.shell, USPosition|PPosition);
        }
    }

    /*
     * 4261047: always pop up with XtGrabNone.  Motif notices the
     * modal input mode and perform the grab for us, doing its
     * internal book-keeping as well.
     */
    XtPopup(wdata->winData.shell, XtGrabNone);
    wdata->isShowing = True;

    wdata->initialFocus = (*env)->GetBooleanField(env, target, windowIDs.isAutoRequestFocus);

    if (wdata->isFocusableWindow) {
        if (wdata->initialFocus || wdata->isModal) {
            focusOnMapNotify = True;
        } else {
            XtVaSetValues(wdata->winData.shell, XmNinput, False, NULL);
        }
    }

    (*env)->DeleteLocalRef(env, target);

    AWT_FLUSH_UNLOCK();
}

/*
 * Class:     sun_awt_motif_MWindowPeer
 * Method:    getState
 * Signature: ()I
 */
JNIEXPORT jint JNICALL
Java_sun_awt_motif_MWindowPeer_getState(JNIEnv *env, jobject this)
{
    struct FrameData *wdata;
    jint state;

    AWT_LOCK();

    wdata = (struct FrameData *)
        JNU_GetLongFieldAsPtr(env, this, mComponentPeerIDs.pData);
    if (wdata == NULL || wdata->winData.shell == NULL) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        AWT_UNLOCK();
        return java_awt_Frame_NORMAL;
    }

    state = wdata->state;

    AWT_FLUSH_UNLOCK();
    return state;
}

/*
 * Class:     sun_awt_motif_MWindowPeer
 * Method:    setState
 * Signature: (I)V
 */
JNIEXPORT void JNICALL
Java_sun_awt_motif_MWindowPeer_setState(JNIEnv *env, jobject this,
    jint state)
{
    struct FrameData *wdata;
    Widget shell;
    Window shell_win;
    jint changed;
    Boolean changeIconic, iconic;

    AWT_LOCK();
    wdata = (struct FrameData *)
        JNU_GetLongFieldAsPtr(env, this, mComponentPeerIDs.pData);
    if (wdata == NULL || wdata->winData.shell == NULL) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        AWT_UNLOCK();
        return;
    }

    shell = wdata->winData.shell;
    shell_win = XtWindow(shell);

    DTRACE_PRINTLN4("TL: setState(0x%x/0x%x, 0x%X -> 0x%X)",
                    shell, shell_win,
                    wdata->state, state);

    if (!wdata->isShowing) {
        /*
         * Not showing, so just record requested state; pShow will set
         * initial state hints/properties appropriately before poping
         * us up again.
         */
        DTRACE_PRINTLN("TL:     NOT showing (just record the new state)");
        wdata->state = state;
        AWT_UNLOCK();
        return;
    }

    /*
     * Request the state transition from WM here and do java upcalls
     * in shell event handler when WM actually changes our state.
     */
    changed = wdata->state ^ state;

    changeIconic = changed & java_awt_Frame_ICONIFIED;
    iconic = (state & java_awt_Frame_ICONIFIED) ? True : False;

    if (changeIconic && iconic) {
        DTRACE_PRINTLN("TL:     set iconic = True");
        XIconifyWindow(XtDisplay(shell), shell_win,
                       XScreenNumberOfScreen(XtScreen(shell)));
    }

    /*
     * If a change in both iconic and extended states requested, do
     * changes to extended state when we are in iconic state.
     */
    if ((changed & ~java_awt_Frame_ICONIFIED) != 0) {
        awt_wm_setExtendedState(wdata, state);
    }

    if (changeIconic && !iconic) {
        DTRACE_PRINTLN("TL:     set iconic = False");
        XMapWindow(XtDisplay(shell), shell_win);
    }

    AWT_FLUSH_UNLOCK();
}


/*
 * Class:     sun_awt_motif_MWindowPeer
 * Method:    pHide
 * Signature: ()V
 */
JNIEXPORT void JNICALL
Java_sun_awt_motif_MWindowPeer_pHide(JNIEnv *env, jobject this)
{
    struct FrameData *wdata;

    AWT_LOCK();
    wdata = (struct FrameData *)
        JNU_GetLongFieldAsPtr(env, this, mComponentPeerIDs.pData);

    if (wdata == NULL
        || wdata->winData.comp.widget == NULL
        || wdata->winData.shell == NULL)
    {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        AWT_UNLOCK();
        return;
    }

    /**
     * Disable proxy mechanism when Window's shell is being hidden
     */
    clearFocusPath(wdata->winData.shell);

    wdata->isShowing = False;   /* ignore window state events */

    if (XtIsRealized(wdata->winData.shell)) {
        /* XXX: uwe: this is bogus */
        /*
         * Make sure we withdraw a window in an unmaximized state, or
         * we'll lose out normal bounds (pShow will take care of
         * hinting maximization, so when the window is shown again it
         * will be correctly shown maximized).
         */
        if (wdata->state & java_awt_Frame_MAXIMIZED_BOTH) {
            awt_wm_setExtendedState(wdata,
                wdata->state & ~java_awt_Frame_MAXIMIZED_BOTH);
        }
        XtUnmanageChild(wdata->winData.comp.widget);
        XtPopdown(wdata->winData.shell);
    }

    AWT_FLUSH_UNLOCK();
}


/* sun_awt_motif_MWindowPeer_pReshape() is native (X/Motif) routine that
   is called to effect a reposition and / or resize of the target frame.
   The parameters x,y,w,h specify target's x, y position, width, height.*/

/*
 * This functionality is invoked from both java and native code, and
 * we only want to lock when invoking it from java, so wrap the native
 * method version with the locking.
 */

/*
 * Class:     sun_awt_motif_MWindowPeer
 * Method:    pReshape
 * Signature: (IIII)V
 */
JNIEXPORT void JNICALL
Java_sun_awt_motif_MWindowPeer_pReshape(JNIEnv *env, jobject this,
    jint x, jint y, jint w, jint h)
{
    struct FrameData    *wdata;

    AWT_LOCK();

    wdata = (struct FrameData *)
        JNU_GetLongFieldAsPtr(env, this, mComponentPeerIDs.pData);

    if (wdata == NULL ||
        wdata->winData.comp.widget == NULL ||
        wdata->winData.shell == NULL) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        AWT_UNLOCK();
        return;
    }

    // See if our new location is on a new screen
    if (wdata->reparented) {
        checkNewXineramaScreen(env, this, wdata, x, y, w, h);
    }

    /**
     * Fix for 4652685.
     * Avoid setting position for embedded frames, since this conflicts with the
     * fix for 4419207. We assume that the embedded frame never changes its
     * position relative to the parent.
     */
    if (wdata->winData.flags & W_IS_EMBEDDED) {
        x = 0;
        y = 0;
    }

    reshape(env, this, wdata, x, y, w, h, True);

    AWT_FLUSH_UNLOCK();
}

/*
 * Class:     sun_awt_motif_MEmbeddedFramePeer
 * Method:    pReshapePrivate
 * Signature: (IIII)V
 */
JNIEXPORT void JNICALL
Java_sun_awt_motif_MEmbeddedFramePeer_pReshapePrivate(JNIEnv *env, jobject this,
    jint x, jint y, jint w, jint h)
{
    struct FrameData    *wdata;

    AWT_LOCK();

    wdata = (struct FrameData *)
        JNU_GetLongFieldAsPtr(env, this, mComponentPeerIDs.pData);

    if (wdata == NULL ||
        wdata->winData.comp.widget == NULL ||
        wdata->winData.shell == NULL) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        AWT_UNLOCK();
        return;
    }

    reshape(env, this, wdata, x, y, w, h, True);

    AWT_FLUSH_UNLOCK();
}

static void
reshape(JNIEnv *env, jobject this, struct FrameData *wdata,
        jint x, jint y, jint w, jint h, Boolean setXY)
{
    int32_t     topAdjust,      /* top adjustment of offset     */
                bottomAdjust;   /* bottom adjustment of offset  */
    int32_t     width,          /* of X/Motif shell and form    */
                height;         /* of X/Motif shell and form    */
    int32_t     w1, h1;
    enum wmgr_t wm;             /* window manager */
    XWindowAttributes winAttr;

    DTRACE_PRINTLN7("TL: reshape(0x%x/0x%x,\n"/**/
                    "TL:         x = %d, y = %d, w = %d, h = %d, %s)",
                    wdata->winData.shell, XtWindow(wdata->winData.shell),
                    x, y, w, h,
                    setXY ? "setXY" : "false");

    wm = awt_wm_getRunningWM();

    /* Make adjustments in case of a dynamically added/removed menu bar */
    awtJNI_setMbAndWwHeightAndOffsets(env, this, wdata);

#ifdef _pauly_debug
    fprintf(stdout,"    reshape. offsets - top: %d, bottom: %d, left: %d, right: %d\n",
            wdata->top, wdata->bottom, wdata->left, wdata->right);
    fflush(stdout);
#endif /* _pauly_debug */

    /* The abstract java (target) position coordinates (x,y)
       are for the bordered window.  Eventually(!), the Motif
       (shell) coordinates (XmNx, XmNy) will exclude borders.
       (This is true only AFTER shell is massaged by the WM.)   */

    /* The abstract java (target) width and height includes any WM
       borders. But the Motif width and height excludes WM borders.
       The wdata top and bottom fields may include space for menu bar,
       warning window, etc. We must adjust by these values for shell.   */
    topAdjust = 0;
    bottomAdjust = 0;
    /* Surprise - do not(!) check for nonNull MenuBar because that can
       occur separately (in ...pSetMenubar()) from calculation of the
       menu bar height and offsets (in setMbAndWwHeightAndOffsets()).
       In any event, the offsets and wdata mbHeight field should jive.  */
    topAdjust += wdata->mbHeight;
    if  (wdata->warningWindow != NULL) {
#ifdef NETSCAPE
        bottomAdjust += wdata->wwHeight;
#else /* NETSCAPE */
        topAdjust += wdata->wwHeight;
#endif /* NETSCAPE */
    }
    if (wdata->hasTextComponentNative) {
        bottomAdjust +=  wdata->imHeight;
    }
#ifdef _pauly_debug
    fprintf(stdout,"    reshape. adjustments - top: %d, bottom: %d\n", topAdjust, bottomAdjust);
    fflush(stdout);
#endif /* _pauly_debug */

    width  = w - (wdata->left + wdata->right);
    height = h - (wdata->top + wdata->bottom) + (topAdjust + bottomAdjust);

    /*
     * Shell size.
     * 4033151.  If nonpositive size specified (e.g., if no size
     * given), establish minimum allowable size.  Note: Motif shell
     * can not be sized 0.
     */
    w1 = (width  > 0) ? width  : 1;
    h1 = (height > 0) ? height : 1;

    if (awt_wm_configureGravityBuggy() /* WM ignores window gravity */
        && wdata->reparented && wdata->isShowing)
    {
        /*
         * Buggy WM places client window at (x,y) ignoring the window
         * gravity.  All our windows are NorthWestGravity, so adjust
         * (x,y) by insets appropriately.
         */
        x += wdata->left;
        y += wdata->top;
        DTRACE_PRINTLN2("TL: work around WM gravity bug: x += %d, y += %d",
                        wdata->left, wdata->top);
    }

    if (wdata->imRemove) {
        XtVaSetValues(XtParent(wdata->winData.comp.widget),
                      XmNheight, (((h - (wdata->top + wdata->bottom)) > 0) ?
                                  (h - (wdata->top + wdata->bottom)) : 1),
                      NULL);
        wdata->imRemove = False;
    }

#if 0 /* XXX: this screws insets calculation under KDE2 in the case of
         negative x, y */
    /*
     * Without these checks, kwm places windows slightly off the screen,
     * when there is a window underneath at (0,0) and empty space below,
     * but not to the right.
     */
    if (x < 0) x = 0;
    if (y < 0) y = 0;
#endif
    if ((wdata->winData.flags & W_IS_EMBEDDED) == 0) {
        if ((wm == MOTIF_WM) || (wm == CDE_WM)) {
            /*
             * By default MWM has "usePPosition: nonzero" and so ignores
             * windows with PPosition (0,0).  Work around (should we???).
             */
            if ((x == 0) && (y == 0)) {
                x = y = 1;
            }
        }
    }

    if ( wdata->decor == AWT_NO_DECOR ) {
        if (setXY)
            XtConfigureWidget(wdata->winData.shell, x, y, w1, h1, 0 );
        else
            XtResizeWidget(wdata->winData.shell, w1, h1, 0);
    }
    else {
        /*
         * 5006248, workaround for OpenLook WM.
         * Thread gets stuck at XtVaSetValues call awaiting for first
         * ConfigureNotify to come. For OpenLook it looks like a showstopper.
         * We put dummy ConfigureNotify to satisfy the requirements.
         */
        if (awt_wm_getRunningWM() == OPENLOOK_WM) {
            XEvent xev;
            xev.xconfigure.type = ConfigureNotify;
            xev.xconfigure.display = awt_display;
            xev.xconfigure.window = XtWindow(wdata->winData.shell);
            xev.xconfigure.event = xev.xconfigure.window;
            xev.xconfigure.x = x;
            xev.xconfigure.y = y;
            xev.xconfigure.height = h1;
            xev.xconfigure.width = w1;
            xev.xconfigure.serial = NextRequest(awt_display) + 1; // see isMine() Xt inner function code.

            XPutBackEvent(awt_display, &xev);
        }

        if (wdata->isResizable) {
            XtVaSetValues(wdata->winData.shell,
                          XmNwidth, w1,
                          XmNheight, h1,
                          NULL);
        }
        else {
            /*
             * Fix for BugTraq ID 4313607 - call awt_wm_setShellNotResizable
             * regardless of wdata->isFixedSizeSet and wdata->reparented values.
             */
            DTRACE_PRINTLN("TL: set fixed size from reshape");
            awt_wm_setShellNotResizable(wdata, w1, h1, True);
            if (wdata->reparented && (w1 > 0) && (h1 > 0)) {
                wdata->isFixedSizeSet = True;
            }
        }
        if (setXY)
            XtVaSetValues(wdata->winData.shell,
                          XmNx, x,
                          XmNy, y,
                          NULL);
    }
    /* inner/parent drawing area (parent is form) */
    h1 = h - (wdata->top + wdata->bottom);
    h1 = ( h1 > 0 ) ? h1 : 1;
#if 0
    XtConfigureWidget(XtParent(wdata->winData.comp.widget),
                      0, topAdjust, w1, h1, 0 );
#else
    XtVaSetValues(XtParent(wdata->winData.comp.widget),
                  XmNx, 0,
                  XmNy, topAdjust,
                  XmNwidth, w1,
                  XmNheight, h1,
                  NULL);
#endif

#ifdef _pauly_debug
        fprintf(stdout,"    reshape. setting inner canvas to: %d,%d,%d,%d\n",
        0, topAdjust, w1, h1 );
        fflush(stdout);
#endif /* _pauly_debug */

    wdata->menuBarReset = False;

    /* DTRACE_PRINTLN("TL: reshape -> returning"); */
    return;
}

/*
 * Class:     sun_awt_motif_MEmbeddedFramePeer
 * Method:    getBoundsPrivate
 * Signature: ()Ljava/awt/Rectangle
 */
JNIEXPORT jobject JNICALL Java_sun_awt_motif_MEmbeddedFramePeer_getBoundsPrivate
  (JNIEnv * env, jobject this)
{
    jobject bounds = NULL;
    struct FrameData *cdata;
    XWindowAttributes attr;

    AWT_LOCK();

    cdata = (struct FrameData *)
        JNU_GetLongFieldAsPtr(env, this, mComponentPeerIDs.pData);
    if (cdata == NULL || cdata->mainWindow == NULL) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        AWT_UNLOCK();
        return NULL;
    }
    if (!XtIsRealized(cdata->mainWindow) || !XtIsRealized(cdata->winData.shell)) {
        JNU_ThrowInternalError(env, "widget not visible on screen");
        AWT_UNLOCK();
        return NULL;
    }

    memset(&attr, 0, sizeof(XWindowAttributes));
    XGetWindowAttributes(awt_display, XtWindow(cdata->winData.shell), &attr);

    bounds = JNU_NewObjectByName(env, "java/awt/Rectangle", "(IIII)V",
                                (jint)attr.x, (jint)attr.y, (jint)attr.width, (jint)attr.height);
    if (((*env)->ExceptionOccurred(env)) || JNU_IsNull(env, bounds)) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        AWT_UNLOCK();
        return NULL;
    }

    AWT_UNLOCK();

    return bounds;
}

/*
 * Class:     sun_awt_motif_MWindowPeer
 * Method:    pDispose
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_sun_awt_motif_MWindowPeer_pDispose
(JNIEnv *env, jobject this)
{
    struct FrameData *wdata;

    AWT_LOCK();
    wdata = (struct FrameData *)
        JNU_GetLongFieldAsPtr(env, this, mComponentPeerIDs.pData);
    if (wdata == NULL || wdata->mainWindow == NULL || wdata->winData.shell == NULL) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        AWT_UNLOCK();
        return;
    }
    if (wdata->winData.flags & W_IS_EMBEDDED) {
        awt_util_delEmbeddedFrame(wdata->winData.shell);
        deinstall_xembed(wdata);
    }
    scheduleDispose(env, this);

    AWT_FLUSH_UNLOCK();
}

/*
 * Class:     sun_awt_motif_MFramePeer
 * Method:    pGetIconSize
 * Signature: (II)Z
 */
JNIEXPORT jboolean JNICALL Java_sun_awt_motif_MFramePeer_pGetIconSize
(JNIEnv *env, jobject this, jint widthHint, jint heightHint)
{
    struct FrameData *wdata;
    uint32_t width, height, border_width, depth;
    Window win;
    int32_t x, y;
    uint32_t mask;
    XSetWindowAttributes attrs;
    uint32_t saveWidth = 0;
    uint32_t saveHeight = 0;
    uint32_t dist = 0xffffffff;
    int32_t diff = 0;
    int32_t closestWidth;
    int32_t closestHeight;
    int32_t newDist;
    int32_t found = 0;
    AwtGraphicsConfigDataPtr adata;

    AWT_LOCK();
    wdata = (struct FrameData *)
        JNU_GetLongFieldAsPtr(env, this, mComponentPeerIDs.pData);
    if (wdata == NULL) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        AWT_UNLOCK();
        return FALSE;
    }
    XtVaGetValues(wdata->winData.shell,
                  XmNiconWindow, &win,
                  NULL);
    if (!win) {
        int32_t count;
        int32_t i;
        XIconSize *sizeList;

        adata = getGraphicsConfigFromComponentPeer(env, this);

        if (!XGetIconSizes(awt_display,
                           RootWindow(awt_display, adata->awt_visInfo.screen),
                           &sizeList, &count)) {
            /* No icon sizes so can't set it -- Should we throw an exception?*/
            /* [jk] I don't think so: simply fall back to 16x16 */
            saveWidth = saveHeight = 16;
            goto top;
        }
        for (i=0; i < count; i++) {
            if (widthHint >= sizeList[i].min_width &&
                widthHint <= sizeList[i].max_width &&
                heightHint >= sizeList[i].min_height &&
                heightHint <= sizeList[i].max_height) {
                found = 1;
                if ((((widthHint-sizeList[i].min_width)
                      % sizeList[i].width_inc) == 0) &&
                    (((heightHint-sizeList[i].min_height)
                      % sizeList[i].height_inc) ==0)) {
                    /* Found an exact match */
                    saveWidth = widthHint;
                    saveHeight = heightHint;
                    dist = 0;
                    break;
                }
                diff = widthHint - sizeList[i].min_width;
                if (diff == 0) {
                    closestWidth = widthHint;
                } else {
                    diff = diff%sizeList[i].width_inc;
                    closestWidth = widthHint - diff;
                }
                diff = heightHint - sizeList[i].min_height;
                if (diff == 0) {
                    closestHeight = heightHint;
                } else {
                    diff = diff%sizeList[i].height_inc;
                    closestHeight = heightHint - diff;
                }
                newDist = closestWidth*closestWidth +
                    closestHeight*closestHeight;
                if (dist > newDist) {
                    saveWidth = closestWidth;
                    saveHeight = closestHeight;
                    dist = newDist;
                }
            }
        }

        if (!found) {
#if 1
            /* [sbb] this code should work better than the original Solaris
               code */
            if (widthHint  >= sizeList[0].max_width ||
                heightHint >= sizeList[0].max_height) {
              /* determine which way to scale */
              int32_t wdiff = widthHint - sizeList[0].max_width;
              int32_t hdiff = heightHint - sizeList[0].max_height;
              if (wdiff >= hdiff) { /* need to scale width more  */
                saveWidth = sizeList[0].max_width;
                saveHeight = (int32_t)(((double)sizeList[0].max_width/widthHint) *
                                   heightHint);
              } else {
                saveWidth = (int32_t)(((double)sizeList[0].max_height/heightHint) *
                                  widthHint);
                saveHeight = sizeList[0].max_height;
              }
            } else if (widthHint  < sizeList[0].min_width ||
                       heightHint < sizeList[0].min_height) {
                saveWidth = (sizeList[0].min_width+sizeList[0].max_width)/2;
                saveHeight = (sizeList[0].min_height+sizeList[0].max_height)/2;
            } else {            /* it fits within the right size */
              saveWidth = widthHint;
              saveHeight = heightHint;
            }

#else /* XXX: old Solaris code */
            /* REMIND: Aspect ratio */
            if (widthHint  >= sizeList[0].max_width &&
                heightHint >= sizeList[0].max_height) {
                saveWidth = sizeList[0].max_width;
                saveHeight = sizeList[0].max_height;
            } else if (widthHint  >= sizeList[0].min_width &&
                       heightHint >= sizeList[0].min_height) {
                saveWidth = sizeList[0].min_width;
                saveHeight = sizeList[0].min_height;
            } else {
                saveWidth = (sizeList[0].min_width+sizeList[0].max_width)/2;
                saveHeight = (sizeList[0].min_height+sizeList[0].max_height)/2;
            }
#endif
        }
        free((void *) sizeList);
    } else {
        Window root;
        if (XGetGeometry(awt_display,
                         win,
                         &root,
                         &x,
                         &y,
                         (uint32_t *)&saveWidth,
                         (uint32_t *)&saveHeight,
                         (uint32_t *)&border_width,
                         (uint32_t *)&depth)) {
        }
    }

 top:
    (*env)->SetIntField(env, this, mWindowPeerIDs.iconWidth, (jint)saveWidth);
    (*env)->SetIntField(env, this, mWindowPeerIDs.iconHeight, (jint)saveHeight);

    AWT_UNLOCK();
    return TRUE;
}

/*
 * Class:     sun_awt_motif_MFramePeer
 * Method:    pSetIconImage
 * Signature: ([B[I[SII)V
 */
JNIEXPORT void JNICALL Java_sun_awt_motif_MFramePeer_pSetIconImage___3B_3I_3SII
(JNIEnv *env, jobject this,
 jbyteArray jbyteData, jintArray jintData, jshortArray jushortData,
 jint iconWidth, jint iconHeight)
{
    struct FrameData *wdata;
    Window win;
    GC gc;
    int32_t x, y;
    XImage *dst;
    uint32_t mask;
    XSetWindowAttributes attrs;
    jobject jbuf = NULL;
    void *buf = NULL;
    int32_t len = 0;
    int32_t bpp, slp, bpsl;
    AwtGraphicsConfigDataPtr adata;

    if (JNU_IsNull(env, jbyteData)) {
        if (JNU_IsNull(env, jintData)) {
            if (JNU_IsNull(env, jushortData)) {
                /* [jk] Don't throw an exception here, it breaks
                 * programs that run correctly on Windows
                 * JNU_ThrowNullPointerException(env, "NullPointerException");
                 */
                return;
            } else {
                jbuf = jushortData;
            }
        } else {
            jbuf = jintData;
        }
    } else {
        jbuf = jbyteData;
        len = (*env)->GetArrayLength(env, jbyteData);
    }
    AWT_LOCK();
    wdata = (struct FrameData *)
        JNU_GetLongFieldAsPtr(env, this, mComponentPeerIDs.pData);
    /* REMIND: Need to figure out how to display image on a pixmap */

    if (wdata == NULL || wdata->winData.shell == NULL) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        AWT_UNLOCK();
        return;
    }

    adata = getGraphicsConfigFromComponentPeer(env, this);

    /* [jk] we need a new pixmap everytime:
     * Test case: src/share/test/awt/FrameTest.html Look at the icon,
     * select Operations/Change IconImage, you should see a different
     * icon now.
     */
    if (wdata->iconPixmap) {
        XFreePixmap(awt_display, wdata->iconPixmap);
        wdata->iconPixmap = None;
    }

    if (wdata->iconPixmap == None) {
        if ((wdata->iconPixmap =
             XCreatePixmap(awt_display,
                           RootWindow(awt_display, adata->awt_visInfo.screen),
                           iconWidth, iconHeight,
                           adata->awtImage->Depth)) == None) {
            /* REMIND: How to warn that there was a problem? */
            AWT_UNLOCK();
            return;
        }
        wdata->iconWidth = iconWidth;
        wdata->iconHeight = iconHeight;
    }

    buf = (void *) (*env)->GetPrimitiveArrayCritical(env, jbuf, NULL);
    if (jbyteData != NULL) {
        int32_t i;
        unsigned char *ubuf = (unsigned char *) buf;
        /* Need to map from ICM lut to cmap */
        for (i=0; i < len; i++) {
            ubuf[i] = (ubuf[i] >= adata->color_data->awt_numICMcolors)
                        ? 0
                        : adata->color_data->awt_icmLUT2Colors[ubuf[i]];
        }
    }

    bpp = adata->awtImage->wsImageFormat.bits_per_pixel;
    slp = adata->awtImage->wsImageFormat.scanline_pad;
    bpsl = paddedwidth(iconWidth * bpp, slp) >> 3;
    if (((bpsl << 3) / bpp) < iconWidth) {
        (*env)->ReleasePrimitiveArrayCritical(env, jbuf, buf, JNI_ABORT);
        AWT_UNLOCK();
        return;
    }
    dst = XCreateImage(awt_display, adata->awt_visInfo.visual,
                       adata->awtImage->Depth, ZPixmap, 0,
                       buf, iconWidth, iconHeight, 32, bpsl);
    if (dst == NULL) {
        /* REMIND: How to warn that there was a problem? */
        (*env)->ReleasePrimitiveArrayCritical(env, jbuf, buf, JNI_ABORT);
        AWT_UNLOCK();
        return;
    }

    if ((gc = XCreateGC(awt_display, wdata->iconPixmap, 0, 0)) == NULL) {
        XDestroyImage (dst);
        (*env)->ReleasePrimitiveArrayCritical(env, jbuf, buf, JNI_ABORT);
        AWT_UNLOCK();
        return;
    }

    XPutImage(awt_display, wdata->iconPixmap, gc, dst,
              0, 0, 0, 0, iconWidth, iconHeight);
    (*env)->ReleasePrimitiveArrayCritical(env, jbuf, buf, JNI_ABORT);
    dst->data=NULL;
    XDestroyImage(dst);
    XFreeGC(awt_display, gc);

    XtVaGetValues(wdata->winData.shell,
                  XmNiconWindow, &win,
                  NULL);
    if (!win) {
        mask = CWBorderPixel | CWColormap | CWBackPixmap;
        attrs.border_pixel = awt_defaultFg;
        attrs.colormap = adata->awt_cmap;
        attrs.background_pixmap = wdata->iconPixmap;
        if (!(win = XCreateWindow(awt_display,
                                  RootWindow(awt_display,
                                             adata->awt_visInfo.screen),
                                  0, 0, iconWidth, iconHeight,
                                  (uint32_t) 0,
                                  adata->awtImage->Depth,
                                  InputOutput,
                                  adata->awt_visInfo.visual,
                                  mask, &attrs))) {
            /* Still can't create the window so try setting iconPixmap */
            XtVaSetValues(wdata->winData.shell,
                          XmNiconPixmap, wdata->iconPixmap,
                          NULL);
            AWT_FLUSH_UNLOCK();
            return;
        }
    }

    XtVaSetValues(wdata->winData.shell,
                  XmNiconPixmap, wdata->iconPixmap,
                  XmNiconWindow, win,
                  NULL);

    XSetWindowBackgroundPixmap(awt_display, win, wdata->iconPixmap);
    XClearWindow(awt_display, win);
    AWT_FLUSH_UNLOCK();
}


/*
 * Class:     sun_awt_motif_MWindowPeer
 * Method:    setResizable
 * Signature: (Z)V
 */
JNIEXPORT void JNICALL
Java_sun_awt_motif_MWindowPeer_setResizable(JNIEnv *env, jobject this,
    jboolean resizable)
{
    struct FrameData    *wdata;
    jobject             target;
    int32_t             targetWidth,
                        targetHeight;
    int32_t             width,          /* fixed width if not resizable */
                        height;         /* fixed height if not resizable*/
    int32_t             verticalAdjust; /* menubar, warning window, etc.*/

    if ((*env)->EnsureLocalCapacity(env, 1) < 0) {
        return;
    }

    AWT_LOCK();

    target = (*env)->GetObjectField(env, this, mComponentPeerIDs.target);

    wdata = (struct FrameData *)
        JNU_GetLongFieldAsPtr(env, this, mComponentPeerIDs.pData);

    if (wdata == NULL
        || wdata->winData.comp.widget == NULL
        || wdata->winData.shell == NULL
        || JNU_IsNull(env, target))
    {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        if (!JNU_IsNull(env, target))
            (*env)->DeleteLocalRef(env, target);
        AWT_UNLOCK();
        return;
    }

    DTRACE_PRINTLN3("TL: setResizable(0x%x/0x%x, %s)",
                    wdata->winData.shell, XtWindow(wdata->winData.shell),
                    resizable ? "true" : "false");

    if ((!wdata->isResizable) && (resizable)) {
        awt_wm_setShellResizable(wdata);
        wdata->isFixedSizeSet = False;
    }
    else if ((wdata->isResizable) && (!resizable)) {
        /*
         * To calculate fixed window width, height, we must subtract
         * off the window manager borders as stored in the wdata
         * structure.  But note that the wdata top and bottom fields
         * may include space for warning window, menubar, IM status;
         * this IS part of shell.
         */
        verticalAdjust = wdata->mbHeight;
        if (wdata->warningWindow != NULL) {
            verticalAdjust += wdata->wwHeight;
        }
        if (wdata->hasTextComponentNative) {
            verticalAdjust += wdata->imHeight;
        }

        targetWidth  = (*env)->GetIntField(env, target, componentIDs.width);
        targetHeight = (*env)->GetIntField(env, target, componentIDs.height);
        width  = targetWidth  - (wdata->left + wdata->right);
        height = targetHeight - (wdata->top + wdata->bottom) + verticalAdjust;
#ifdef __linux__
        width  = (width  > 0) ? width  : 1;
        height = (height > 0) ? height : 1;
#endif
        DTRACE_PRINTLN2("TL:     setting fixed size %ld x %ld", width, height);
        awt_wm_setShellNotResizable(wdata, width, height, False);
        if ((width > 0) && (height > 0)) {
            wdata->isFixedSizeSet = True;
        }
    }

    wdata->isResizable = (Boolean)resizable;

    (*env)->DeleteLocalRef(env, target);
    AWT_FLUSH_UNLOCK();
}


/* sun_awt_motif_MWindowPeer_pSetMenuBar() is native (X/Motif) routine
   which handles insertion or deletion of a menubar from this frame.    */

/*
 * Class:     sun_awt_motif_MWindowPeer
 * Method:    pSetMenuBar
 * Signature: (Lsun/awt/motif/MMenuBarPeer;)V
 */
JNIEXPORT void JNICALL Java_sun_awt_motif_MWindowPeer_pSetMenuBar
(JNIEnv *env, jobject this, jobject mb)
{
    struct FrameData            *wdata;
    struct ComponentData        *mdata;
    jobject                     target;
    Widget                      innerCanvasW;   /* Motif inner canvas   */
#ifdef _pauly_debug
    Dimension                   mbHeight;       /* Motif menubar height */
#endif /* _pauly_debug */

#ifdef _pauly_debug
    fprintf(stdout," ++ ...pSetMenuBar.\n");
    fflush(stdout);
#endif /* _pauly_debug */


    if ((*env)->EnsureLocalCapacity(env, 1) < 0) {
        return;
    }
    AWT_LOCK();

    target = (*env)->GetObjectField(env, this, mComponentPeerIDs.target);

    wdata = (struct FrameData *)
        JNU_GetLongFieldAsPtr(env, this, mComponentPeerIDs.pData);

    if (JNU_IsNull(env, target) || wdata == NULL) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        if  (!JNU_IsNull(env, target)) {
            (*env)->DeleteLocalRef(env, target);
        }
        AWT_UNLOCK();
        return;
    }

    if (mb == NULL) {
#ifdef _pauly_debug
        fprintf(stdout,"    ...pSetMenuBar. mb is null.\n");
        fflush(stdout);
#endif /* _pauly_debug */
        if  (wdata->menuBar != NULL) {
            /* Redo attachments of other form widgets appropriately now */
            innerCanvasW = XtParent(wdata->winData.comp.widget);

            if  (wdata->warningWindow == NULL) {
                /* no warning window: canvas is now attached to form    */
                XtVaSetValues(innerCanvasW,
                              XmNtopAttachment, XmATTACH_FORM,
                              NULL);
            } else {
                /* warning window present - conditional on #define NETSCAPE:
                   if NETSCAPE, warning window is at bottom, so canvas is
                   attached to the form (as above); otherwise (not NETSCAPE),
                   warning window itself is instead attached to form.   */
#ifdef NETSCAPE
                XtVaSetValues(innerCanvasW,
                              XmNtopAttachment, XmATTACH_FORM,
                              NULL);
#else  /* NETSCAPE */
                XtVaSetValues(wdata->warningWindow,
                              XmNtopAttachment, XmATTACH_FORM,
                              NULL);
#endif /* NETSCAPE */
            }

            wdata->menuBarReset = True;
        }
        wdata->menuBar = NULL;
        awtJNI_setMbAndWwHeightAndOffsets(env, this, wdata);
        (*env)->DeleteLocalRef(env, target);
        AWT_FLUSH_UNLOCK();
#ifdef _pauly_debug
        fprintf(stdout,"    ...pSetMenuBar. Done.\n");
        fflush(stdout);
#endif /* _pauly_debug */
        return;
    }

    mdata = (struct ComponentData *)
        JNU_GetLongFieldAsPtr(env, mb, mMenuBarPeerIDs.pData);
    if (mdata == NULL) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        (*env)->DeleteLocalRef(env, target);
        AWT_UNLOCK();
        return;
    }

    /* OK - insert the new menu bar into the form (at the top).
       Redo the attachments of other form widgets appropriately.*/

    if  (wdata->menuBar == NULL)
        wdata->menuBarReset = True;
    wdata->menuBar = mdata->widget;

#ifdef _pauly_debug
    XtVaGetValues(mdata->widget, XmNheight, &mbHeight, NULL);
    fprintf(stdout,"    ...pSetMenuBar. new menu bar (widget %x, parent: %x) - menu bar height: %d\n", wdata->menuBar, XtParent(wdata->menuBar), mbHeight);
    fflush(stdout);
#endif /* _pauly_debug */

    XtVaSetValues(mdata->widget,
                  XmNtopAttachment, XmATTACH_FORM,
                  XmNleftAttachment, XmATTACH_FORM,
                  XmNrightAttachment, XmATTACH_FORM,
                  NULL);

    innerCanvasW = XtParent(wdata->winData.comp.widget);

    if  (wdata->warningWindow == NULL) {
        /* no warning window: menu bar at top, canvas attached to it    */
        XtVaSetValues(innerCanvasW,
                      XmNtopAttachment, XmATTACH_WIDGET,
                      XmNtopWidget, mdata->widget,
                      NULL);
    } else {
        /* warning window present - conditional on #define NETSCAPE:
           if NETSCAPE, warning window is at bottom, so canvas is
           attached to menu bar (as above); otherwise (not NETSCAPE),
           the warning window is attached just below the menu bar.  */
#ifdef NETSCAPE
        XtVaSetValues(innerCanvasW,
                      XmNtopAttachment, XmATTACH_WIDGET,
                      XmNtopWidget, mdata->widget,
                      NULL);
#else  /* NETSCAPE */
        XtVaSetValues(wdata->warningWindow,
                      XmNtopAttachment, XmATTACH_WIDGET,
                      XmNtopWidget, mdata->widget,
                      NULL);
#endif /* NETSCAPE */
    }

    XtManageChild(mdata->widget);
    XtMapWidget(mdata->widget);
    XSync(awt_display, False);
    awtJNI_setMbAndWwHeightAndOffsets(env, this, wdata);

#ifdef _pauly_debug
    XtVaGetValues(mdata->widget, XmNheight, &mbHeight, NULL);
    fprintf(stdout,"    ...pSetMenuBar. with menu bar: menu bar height: %d, top offset: %d, bottom offset: %d\n", mbHeight, wdata->top, wdata->bottom);
    fflush(stdout);
#endif /* _pauly_debug */

    (*env)->DeleteLocalRef(env, target);

    AWT_FLUSH_UNLOCK();

#ifdef _pauly_debug
    fprintf(stdout,"    ...pSetMenuBar. Done\n");
    fflush(stdout);
#endif /* _pauly_debug */
}

/*
 * Class:     sun_awt_motif_MWindowPeer
 * Method:    toBack
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_sun_awt_motif_MWindowPeer_toBack
(JNIEnv *env, jobject this)
{
    struct FrameData *wdata;

    AWT_LOCK();

    wdata = (struct FrameData *)
        JNU_GetLongFieldAsPtr(env, this, mComponentPeerIDs.pData);

    if (wdata == NULL || wdata->winData.shell == NULL) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        AWT_UNLOCK();
        return;
    }
    if (XtWindow(wdata->winData.shell) != 0) {
        XLowerWindow(awt_display, XtWindow(wdata->winData.shell));
    }
    AWT_FLUSH_UNLOCK();
}

/*
 * Class:     sun_awt_motif_MWindowPeer
 * Method:    updateAlwaysOnTop
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_sun_awt_motif_MWindowPeer_updateAlwaysOnTop
(JNIEnv *env, jobject this, jboolean isOnTop)
{
    struct FrameData *wdata;
    AWT_LOCK();
    wdata = (struct FrameData *)
        JNU_GetLongFieldAsPtr(env, this, mComponentPeerIDs.pData);
    awt_wm_updateAlwaysOnTop(wdata, isOnTop);
    AWT_FLUSH_UNLOCK();
}

JNIEXPORT void JNICALL Java_sun_awt_motif_MWindowPeer_addTextComponentNative
(JNIEnv *env, jobject this, jobject tc)
{
    struct FrameData            *wdata;
    jobject                     target;

    if (JNU_IsNull(env, this)) {
        return;
    }

    AWT_LOCK();

    target = (*env)->GetObjectField(env, this, mComponentPeerIDs.target);
    wdata = (struct FrameData *)
        JNU_GetLongFieldAsPtr(env, this, mComponentPeerIDs.pData);

    if (wdata == NULL ||
        wdata->winData.comp.widget==NULL ||
        wdata->winData.shell==NULL ||
        JNU_IsNull(env, target)) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        AWT_UNLOCK();
        return;
    }
    if ( !wdata->hasTextComponentNative) {
        wdata->hasTextComponentNative = True;
        wdata->imHeight = awt_motif_getIMStatusHeight(wdata->winData.shell, tc);
        wdata->bottom += wdata->imHeight;
        awtJNI_ChangeInsets(env, this, wdata);
        reshape(env, this, wdata,
                (*env)->GetIntField(env, target, componentIDs.x),
                (*env)->GetIntField(env, target, componentIDs.y),
                (*env)->GetIntField(env, target, componentIDs.width),
                (*env)->GetIntField(env, target, componentIDs.height),
                True);
    }
    AWT_UNLOCK();
}

JNIEXPORT void JNICALL Java_sun_awt_motif_MWindowPeer_removeTextComponentNative
(JNIEnv *env, jobject this)
{
    struct FrameData            *wdata;
    jobject                     target;

    if (JNU_IsNull(env, this)) {
        return;
    }

    AWT_LOCK();

    target = (*env)->GetObjectField(env, this, mComponentPeerIDs.target);
    wdata = (struct FrameData *)
        JNU_GetLongFieldAsPtr(env, this, mComponentPeerIDs.pData);

    if (wdata == NULL ||
        wdata->winData.comp.widget== NULL ||
        wdata->winData.shell== NULL ||
        JNU_IsNull(env, target)) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        AWT_UNLOCK();
        return;
    }
    if (!wdata->hasTextComponentNative) {
        AWT_UNLOCK();
        return;
    }

    wdata->bottom -= wdata->imHeight;
    awtJNI_ChangeInsets(env, this, wdata);
    wdata->imRemove = True;
    reshape(env, this, wdata,
            (*env)->GetIntField(env, target, componentIDs.x),
            (*env)->GetIntField(env, target, componentIDs.y),
            (*env)->GetIntField(env, target, componentIDs.width),
            (*env)->GetIntField(env, target, componentIDs.height),
            True);

    wdata->hasTextComponentNative = False;
    wdata->imHeight = 0;

    AWT_UNLOCK();
} /* ...removeTextComponentPeer() */

static Atom java_protocol = None;
static Atom motif_wm_msgs = None;

static void im_callback(Widget shell, XtPointer client_data, XtPointer call_data)
{
    JNIEnv *env = (JNIEnv *)JNU_GetEnv(jvm, JNI_VERSION_1_2);
    JNU_CallMethodByName(env, NULL,
                         (jobject)client_data,
                         "notifyIMMOptionChange",
                         "()V");
}

JNIEXPORT void JNICALL Java_sun_awt_motif_MWindowPeer_pSetIMMOption
(JNIEnv *env, jobject this, jstring option)
{
    char        *coption;
    char        *empty = "InputMethod";
    char        *menuItem;
    jobject     globalRef;
    struct FrameData *wdata;

    AWT_LOCK();

    wdata = (struct FrameData *)
        JNU_GetLongFieldAsPtr(env, this, mComponentPeerIDs.pData);
    if (wdata == NULL || wdata->winData.shell == NULL) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        AWT_UNLOCK();
        return;
    }

    globalRef = (jobject)JNU_GetLongFieldAsPtr(env, this, mComponentPeerIDs.jniGlobalRef);
    coption = (JNU_IsNull(env, option)) ? empty : (char *) JNU_GetStringPlatformChars(env, option, NULL);
    if (java_protocol == None || motif_wm_msgs == None) {
        java_protocol = XmInternAtom(awt_display, "_JAVA_IM_MSG", False);
        motif_wm_msgs = XmInternAtom(awt_display, "_MOTIF_WM_MESSAGES", False);
    }
    XmAddProtocols (wdata->winData.shell, motif_wm_msgs, &java_protocol, 1);
    XmAddProtocolCallback(wdata->winData.shell, motif_wm_msgs, java_protocol, im_callback, (XtPointer)globalRef);

    if ((menuItem = awt_util_makeWMMenuItem(coption, java_protocol))) {
        XtVaSetValues(wdata->winData.shell,
                      XmNmwmMenu,
                      menuItem,
                      NULL);
        free(menuItem);
    }
    if (coption != empty)
        JNU_ReleaseStringPlatformChars(env, option, (const char *) coption);
    AWT_FLUSH_UNLOCK();
}


JNIEXPORT void JNICALL
Java_sun_awt_motif_MEmbeddedFramePeer_synthesizeFocusInOut(JNIEnv *env, jobject this,
                                                           jboolean b)
{
    EmbeddedFrame *ef;
    Boolean dummy;

    AWT_LOCK();
    ef = theEmbeddedFrameList;
    while (ef != NULL) {
        if ((*env)->IsSameObject(env, ef->javaRef, this)) {
            XFocusChangeEvent xev;
            xev.display = awt_display;
            xev.serial = 0;
            xev.type = b ? FocusIn : FocusOut;
            xev.send_event = False;
            xev.window = XtWindow(ef->embeddedFrame);
            xev.mode = NotifyNormal;
            xev.detail = NotifyNonlinear;
            shellEH(ef->embeddedFrame, this, (XEvent*)&xev, &dummy);
            break;
        }
        ef = ef->next;
    }
    AWT_UNLOCK();
}

JNIEXPORT void JNICALL
Java_sun_awt_motif_MEmbeddedFramePeer_traverseOut(JNIEnv *env, jobject this, jboolean direction)
{
    struct FrameData            *wdata;

    if (JNU_IsNull(env, this)) {
        return;
    }

    AWT_LOCK();

    wdata = (struct FrameData *)
        JNU_GetLongFieldAsPtr(env, this, mComponentPeerIDs.pData);

    if (wdata == NULL ||
        wdata->winData.comp.widget== NULL ||
        wdata->winData.shell== NULL)
    {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        AWT_UNLOCK();
        return;
    }
    xembed_traverse_out(wdata, direction);
    AWT_UNLOCK();
}


JNIEXPORT void JNICALL
Java_sun_awt_motif_MEmbeddedFramePeer_NEFcreate(JNIEnv *env, jobject this,
                                                jobject parent, jlong handle)
{
#undef MAX_ARGC
#define MAX_ARGC 40
    Arg      args[MAX_ARGC];
    int32_t  argc;
    struct   FrameData *wdata;
    jobject  target;
    jstring  warningString;
    jboolean resizable;
    jobject  globalRef = awtJNI_CreateAndSetGlobalRef(env, this);
    Widget   innerCanvasW;  /* form's child, parent of the outer canvas
                               drawing area */
    AwtGraphicsConfigDataPtr adata;
    AwtGraphicsConfigDataPtr defConfig;

    AWT_LOCK();

    target = (*env)->GetObjectField(env, this, mComponentPeerIDs.target);

    if (JNU_IsNull(env, target)) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        AWT_UNLOCK();
        return;
    }

    wdata = ZALLOC(FrameData);
    JNU_SetLongFieldFromPtr(env, this, mComponentPeerIDs.pData, wdata);
    if (wdata == NULL) {
        JNU_ThrowOutOfMemoryError(env, "OutOfMemoryError");
        AWT_UNLOCK();
        return;
    }

    adata = getGraphicsConfigFromComponentPeer(env, this);
    defConfig = getDefaultConfig(adata->awt_visInfo.screen);

    /* A variation on Netscape's hack for embedded frames: the client area
     * of the browser is a Java Frame for parenting purposes, but really a
     * Motif child window
     */
    wdata->winData.flags |= W_IS_EMBEDDED;

    wdata->top = 0;
    wdata->left = 0;
    wdata->bottom = 0;
    wdata->right = 0;
    awtJNI_ChangeInsets(env, this, wdata);


    wdata->isModal = 0;
    wdata->isShowing = False;
    wdata->shellResized = False;
    wdata->canvasResized = False;
    wdata->menuBarReset = False;

    resizable = (*env)->GetBooleanField(env, target, frameIDs.resizable);

    wdata->winData.shell = (Widget)handle;
    awt_util_addEmbeddedFrame(wdata->winData.shell, globalRef);

    install_xembed((Widget)handle, wdata);

    setDeleteCallback(globalRef, wdata);
    /* Establish resizability.  For the case of not resizable, do not
       yet set a fixed size here; we must wait until in the routine
       sun_awt_motif_MWindowPeer_pReshape() after insets have been fixed.
       This is because correction of the insets may affect shell size.
       (See comments in shellEH() concerning correction of the insets.  */
    /*
     * Fix for BugTraq ID 4313607.
     * Initial resizability will be set later in MWindowPeer_setResizable()
     * called from init(). But the real changes will be made only if the new
     * and old resizability values are different at that point, so we
     * initialize isResizable with inverse value here to get the job done.
     */
    wdata->isResizable = !resizable;
    wdata->isFixedSizeSet = False;
#if 0
    if (resizable) {
        awt_wm_setShellResizable(wdata);
    }
#endif

    XtAddEventHandler(wdata->winData.shell, StructureNotifyMask | FocusChangeMask,
                      FALSE, (XtEventHandler)shellEH, globalRef);


    argc = 0;
    XtSetArg(args[argc], XmNvisual, defConfig->awt_visInfo.visual); argc++;
    XtSetArg(args[argc], XmNcolormap, defConfig->awt_cmap); argc++;
    XtSetArg(args[argc], XmNdepth, defConfig->awt_depth); argc++;
    XtSetArg(args[argc], XmNmarginWidth, 0); argc++;
    XtSetArg(args[argc], XmNmarginHeight, 0); argc++;
    XtSetArg(args[argc], XmNhorizontalSpacing, 0); argc++;
    XtSetArg(args[argc], XmNverticalSpacing, 0); argc++;
    XtSetArg(args[argc], XmNscreen,
             ScreenOfDisplay(awt_display, defConfig->awt_visInfo.screen)); argc++;


    XtSetArg(args[argc], XmNresizePolicy, XmRESIZE_NONE); argc++;

    DASSERT(!(argc > MAX_ARGC));
    wdata->mainWindow = XmCreateForm(wdata->winData.shell, "main", args, argc);

    /* The widget returned by awt_canvas_create is a drawing area
       (i.e., canvas) which is the child of another drawing area
       parent widget.  The parent is the drawing area within the
       form just created.  The child is an drawing area layer over
       the entire frame window, including the form, any menu bar
       and warning windows present, and also window manager stuff.
       The top, bottom, left, and right fields in wdata maintain
       the respective offsets between these two drawing areas.  */

    wdata->winData.comp.widget = awt_canvas_create((XtPointer)globalRef,
                                                   wdata->mainWindow,
                                                   "frame_",
                                                   -1,
                                                   -1,
                                                   True,
                                                   wdata,
                                                   defConfig);

    XtAddCallback(wdata->winData.comp.widget,
                  XmNresizeCallback,
                  outerCanvasResizeCB,
                  globalRef);


    innerCanvasW = XtParent(wdata->winData.comp.widget);
    XtVaSetValues(innerCanvasW,
                  XmNleftAttachment, XmATTACH_FORM,
                  XmNrightAttachment, XmATTACH_FORM,
                  NULL);


    XtAddEventHandler(innerCanvasW, StructureNotifyMask, FALSE,
                      (XtEventHandler)innerCanvasEH, globalRef);

    /* No menu bar initially */
    wdata->menuBar = NULL;
    wdata->mbHeight = 0;

    /* If a warning window (string) is needed, establish it now.*/
    warningString =
        (*env)->GetObjectField(env, target, windowIDs.warningString);

    /* No warning window present */
    XtVaSetValues(innerCanvasW,
                  XmNtopAttachment, XmATTACH_FORM,
                  XmNbottomAttachment, XmATTACH_FORM,
                  NULL);
    wdata->warningWindow = NULL;
    wdata->wwHeight = 0;


    awt_util_show(wdata->winData.comp.widget);

    AWT_FLUSH_UNLOCK();
}  /* MEmbeddedFramePeer_NEFcreate() */


JNIEXPORT void JNICALL
Java_sun_awt_motif_MEmbeddedFramePeer_pShowImpl(JNIEnv *env, jobject this)
{
    struct FrameData *wdata;

    AWT_LOCK();

    wdata = (struct FrameData *)
        JNU_GetLongFieldAsPtr(env, this, mComponentPeerIDs.pData);
    if (wdata == NULL ||
        wdata->winData.comp.widget == NULL ||
        wdata->winData.shell == NULL ||
        wdata->mainWindow == NULL) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        AWT_UNLOCK();
        return;
    }
    XtVaSetValues(wdata->winData.comp.widget,
                  XmNx, -(wdata->left),
                  XmNy, -(wdata->top), NULL);

    if (wdata->menuBar != 0) {
        awt_util_show(wdata->menuBar);
    }

    XtManageChild(wdata->mainWindow);
    if (XtWindow(wdata->winData.shell) == None) {
        XtRealizeWidget(wdata->winData.shell);
    }
    XtManageChild(wdata->winData.comp.widget);
    XtSetMappedWhenManaged(wdata->winData.shell, True);
    XtPopup(wdata->winData.shell, XtGrabNone);
    wdata->isShowing = True;

    AWT_FLUSH_UNLOCK();
}

/*
 * Create a local managed widget inside a given X window.
 * We allocate a top-level shell and then reparent it into the
 * given window id.
 *
 * This is used to take the X11 window ID that has been passed
 * to us by our parent Navigator plugin and return a widget
 * that can be used as the base for our Java EmbeddeFrame.
 *
 * Note that the ordering of the various calls is tricky here as
 * we have to cope with the variations between 1.1.3, 1.1.6,
 * and 1.2.
 */
JNIEXPORT jlong JNICALL
Java_sun_awt_motif_MEmbeddedFrame_getWidget(
                JNIEnv *env, jclass clz, jlong winid)
{
    Arg args[40];
    int argc;
    Widget w;
    Window child, parent;
    Visual *visual;
    Colormap cmap;
    int depth;
    int ncolors;

    /*
     * Create a top-level shell.  Note that we need to use the
     * AWT's own awt_display to initialize the widget.  If we
     * try to create a second X11 display connection the Java
     * runtimes get very confused.
     */
    AWT_LOCK();

    argc = 0;
    XtSetArg(args[argc], XtNsaveUnder, False); argc++;
    XtSetArg(args[argc], XtNallowShellResize, False); argc++;

    /* the awt initialization should be done by now (awt_GraphicsEnv.c) */

    getAwtData(&depth,&cmap,&visual,&ncolors,NULL);

    XtSetArg(args[argc], XtNvisual, visual); argc++;
    XtSetArg(args[argc], XtNdepth, depth); argc++;
    XtSetArg(args[argc], XtNcolormap, cmap); argc++;

    XtSetArg(args[argc], XtNwidth, 1); argc++;
    XtSetArg(args[argc], XtNheight, 1); argc++;
    /* The shell has to have relative coords of O,0? */
    XtSetArg(args[argc], XtNx, 0); argc++;
    XtSetArg(args[argc], XtNy, 0); argc++;

    /* The shell widget starts out as a top level widget.
     * Without intervention, it will be managed by the window
     * manager and will be its own widow. So, until it is reparented,
     *  we don't map it.
     */
    XtSetArg(args[argc], XtNmappedWhenManaged, False); argc++;

    w = XtAppCreateShell("AWTapp","XApplication",
                                    vendorShellWidgetClass,
                                    awt_display,
                                    args,
                                    argc);
    XtRealizeWidget(w);

    /*
     * Now reparent our new Widget into our Navigator window
     */
    parent = (Window) winid;
    child = XtWindow(w);
    XReparentWindow(awt_display, child, parent, 0, 0);
    XFlush(awt_display);
    XSync(awt_display, False);
    XtVaSetValues(w, XtNx, 0, XtNy, 0, NULL);
    XFlush(awt_display);
    XSync(awt_display, False);

    AWT_UNLOCK();

    return (jlong)w;
}

/*
 * Make sure the given widget is mapped.
 *
 * This isn't necessary on JDK 1.1.5 but is needed on JDK 1.1.4
 */
JNIEXPORT jint JNICALL
Java_sun_awt_motif_MEmbeddedFrame_mapWidget(JNIEnv *env, jclass clz, jlong widget)
{
    Widget w = (Widget)widget;
    /*
     * this is what JDK 1.1.5 does in MFramePeer.pShow.
     */
    AWT_LOCK();
    XtSetMappedWhenManaged(w, True);
    XtPopup(w, XtGrabNone);
    AWT_UNLOCK();
    return (jint) 1;
}


JNIEXPORT jboolean JNICALL
Java_sun_awt_motif_MEmbeddedFramePeer_isXEmbedActive(JNIEnv *env, jobject this)
{
    struct FrameData *wdata;
    Boolean res;

    AWT_LOCK();

    wdata = (struct FrameData *)
        JNU_GetLongFieldAsPtr(env, this, mComponentPeerIDs.pData);
    if (wdata == NULL ||
        wdata->winData.comp.widget == NULL ||
        wdata->winData.shell == NULL ||
        wdata->mainWindow == NULL) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        AWT_UNLOCK();
        return False;
    }

    res = isXEmbedActive(wdata);
    AWT_UNLOCK();
    return res;

}

JNIEXPORT jboolean JNICALL
Java_sun_awt_motif_MEmbeddedFramePeer_isXEmbedApplicationActive(JNIEnv *env, jobject this)
{
    struct FrameData *wdata;
    Boolean res;

    AWT_LOCK();

    wdata = (struct FrameData *)
        JNU_GetLongFieldAsPtr(env, this, mComponentPeerIDs.pData);
    if (wdata == NULL ||
        wdata->winData.comp.widget == NULL ||
        wdata->winData.shell == NULL ||
        wdata->mainWindow == NULL) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        AWT_UNLOCK();
        return False;
    }

    res = isXEmbedApplicationActive(wdata);
    AWT_UNLOCK();
    return res;

}

JNIEXPORT void JNICALL
Java_sun_awt_motif_MEmbeddedFramePeer_requestXEmbedFocus(JNIEnv *env, jobject this)
{
    struct FrameData *wdata;

    AWT_LOCK();

    wdata = (struct FrameData *)
        JNU_GetLongFieldAsPtr(env, this, mComponentPeerIDs.pData);
    if (wdata == NULL ||
        wdata->winData.comp.widget == NULL ||
        wdata->winData.shell == NULL ||
        wdata->mainWindow == NULL) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        AWT_UNLOCK();
        return;
    }

    requestXEmbedFocus(wdata);
    AWT_UNLOCK();
}

/*
 * Class:     sun_awt_motif_MWindowPeer
 * Method:    setSaveUnder
 * Signature: (Z)V
 */
JNIEXPORT void JNICALL Java_sun_awt_motif_MWindowPeer_setSaveUnder
(JNIEnv *env, jobject this, jboolean state)
{
    struct FrameData    *wdata;
    jobject             target;

    AWT_LOCK();

    target = (*env)->GetObjectField(env, this, mComponentPeerIDs.target);

    wdata = (struct FrameData *)
        JNU_GetLongFieldAsPtr(env, this, mComponentPeerIDs.pData);

    if (wdata == NULL ||
        wdata->winData.comp.widget == NULL ||
        wdata->winData.shell == NULL ||
        JNU_IsNull(env, target)) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        if  (!JNU_IsNull(env, target))
            (*env)->DeleteLocalRef(env, target);
        AWT_UNLOCK();
        return;
    }

    XtVaSetValues(wdata->winData.shell, XmNsaveUnder, state, NULL);

    AWT_FLUSH_UNLOCK();
}


/*
 * Class:     sun_awt_motif_MWindowPeer
 * Method:    setFocusableWindow
 * Signature: (Z)V
 */
JNIEXPORT void JNICALL Java_sun_awt_motif_MWindowPeer_setFocusableWindow
(JNIEnv *env, jobject this, jboolean isFocusableWindow)
{
    struct FrameData    *wdata;
    jobject             target;

    AWT_LOCK();

    target = (*env)->GetObjectField(env, this, mComponentPeerIDs.target);

    wdata = (struct FrameData *)
        JNU_GetLongFieldAsPtr(env, this, mComponentPeerIDs.pData);

    if (wdata == NULL ||
        wdata->winData.comp.widget == NULL ||
        wdata->winData.shell == NULL ||
        JNU_IsNull(env, target)) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        if  (!JNU_IsNull(env, target))
            (*env)->DeleteLocalRef(env, target);
        AWT_UNLOCK();
        return;
    }

    wdata->isFocusableWindow = isFocusableWindow;

    AWT_FLUSH_UNLOCK();
}

/*
 * Class:     sun_awt_motif_MWindowPeer
 * Method:    resetTargetGC
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_sun_awt_motif_MWindowPeer_resetTargetGC
  (JNIEnv * env, jobject this, jobject target)
{
    (*env)->CallVoidMethod(env, target, windowIDs.resetGCMID);
}


/*
 * Old, compatibility, backdoor for DT.  This is a different
 * implementation.  It keeps the signature, but acts on
 * awt_root_shell, not the frame passed as an argument.  Note, that
 * the code that uses the old backdoor doesn't work correctly with
 * gnome session proxy that checks for WM_COMMAND when the window is
 * firts mapped, because DT code calls this old backdoor *after* the
 * frame is shown or it would get NPE with old AWT (previous
 * implementation of this backdoor) otherwise.  Old style session
 * managers (e.g. CDE) that check WM_COMMAND only during session
 * checkpoint should work fine, though.
 *
 * NB: The function name looks deceptively like a JNI native method
 * name.  It's not!  It's just a plain function.
 */
JNIEXPORT void JNICALL
Java_sun_awt_motif_XsessionWMcommand(JNIEnv *env, jobject this,
    jobject frame, jstring jcommand)
{
    const char *command;
    XTextProperty text_prop;
    char *c[1];
    int32_t status;

    AWT_LOCK();

    if (awt_root_shell == NULL) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        AWT_UNLOCK();
        return;
    }

    if (XtWindow(awt_root_shell) == None) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        AWT_UNLOCK();
        return;
    }

    /* need to convert ctitle to CompoundText */
    command = (char *) JNU_GetStringPlatformChars(env, jcommand, NULL);
    c[0] = (char *)command;
    status = XmbTextListToTextProperty(awt_display, c, 1,
                                       XStdICCTextStyle, &text_prop);

    if (status == Success || status > 0) {
        XSetTextProperty(awt_display, XtWindow(awt_root_shell),
                         &text_prop, XA_WM_COMMAND);
        if (text_prop.value != NULL)
            XFree(text_prop.value);
    }

    JNU_ReleaseStringPlatformChars(env, jcommand, command);

    AWT_UNLOCK();
    return;
}


/*
 * New DT backdoor to set WM_COMMAND.  New code should use this
 * backdoor and call it *before* the first frame is shown so that
 * gnome session proxy can correctly handle it.
 *
 * NB: The function name looks deceptively like a JNI native method
 * name.  It's not!  It's just a plain function.
 */
JNIEXPORT void JNICALL
Java_sun_awt_motif_XsessionWMcommand_New(JNIEnv *env, jobjectArray jargv)
{
    static const char empty[] = "";

    int argc;
    const char **cargv;
    XTextProperty text_prop;
    int status;
    int i;

    AWT_LOCK();

    if (awt_root_shell == NULL) {
        JNU_ThrowNullPointerException(env, "AWT root shell");
        AWT_UNLOCK();
        return;
    }

    if (XtWindow(awt_root_shell) == None) {
        JNU_ThrowNullPointerException(env, "AWT root shell is unrealized");
        AWT_UNLOCK();
        return;
    }

    argc = (int)(*env)->GetArrayLength(env, jargv);
    if (argc == 0) {
        /* nothing to do */
        AWT_UNLOCK();
        return;
    }

    /* array of C strings */
    cargv = (const char **)calloc(argc, sizeof(char *));
    if (cargv == NULL) {
        JNU_ThrowOutOfMemoryError(env, "Unable to allocate cargv");
        AWT_UNLOCK();
        return;
    }

    /* fill C array with platform chars of java strings */
    for (i = 0; i < argc; ++i) {
        jstring js;
        const char *cs;

        cs = NULL;
        js = (*env)->GetObjectArrayElement(env, jargv, i);
        if (js != NULL) {
            cs = JNU_GetStringPlatformChars(env, js, NULL);
        }
        if (cs == NULL) {
            cs = empty;
        }

        cargv[i] = cs;
        (*env)->DeleteLocalRef(env, js);
    }

    /* grr, X prototype doesn't declare cargv as const, thought it really is */
    status = XmbTextListToTextProperty(awt_display, (char **)cargv, argc,
                                       XStdICCTextStyle, &text_prop);
    if (status < 0) {
        switch (status) {
        case XNoMemory:
            JNU_ThrowOutOfMemoryError(env,
                "XmbTextListToTextProperty: XNoMemory");
            break;
        case XLocaleNotSupported:
            JNU_ThrowInternalError(env,
                "XmbTextListToTextProperty: XLocaleNotSupported");
            break;
        case XConverterNotFound:
            JNU_ThrowNullPointerException(env,
                "XmbTextListToTextProperty: XConverterNotFound");
            break;
        default:
            JNU_ThrowInternalError(env,
                "XmbTextListToTextProperty: unknown error");
        }
    } else {
        /*
         * status == Success (i.e. 0) or
         * status > 0 - a number of unconvertible characters
         *              (cannot happen for XStdICCTextStyle).
         */
        XSetTextProperty(awt_display, XtWindow(awt_root_shell),
                         &text_prop, XA_WM_COMMAND);
    }

    /* release platform chars */
    for (i = 0; i < argc; ++i) {
        jstring js;

        if (cargv[i] == empty)
            continue;

        js = (*env)->GetObjectArrayElement(env, jargv, i);
        JNU_ReleaseStringPlatformChars(env, js, cargv[i]);
        (*env)->DeleteLocalRef(env, js);
    }
    if (text_prop.value != NULL)
      XFree(text_prop.value);

    AWT_UNLOCK();
    return;
}

/*
 * Class:     java_awt_TrayIcon
 * Method:    initIDs
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_java_awt_TrayIcon_initIDs(JNIEnv *env , jclass clazz)
{
}
