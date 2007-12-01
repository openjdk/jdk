/*
 * Copyright 1995-2006 Sun Microsystems, Inc.  All Rights Reserved.
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
#include "awt_AWTEvent.h"
#include "VDrawingArea.h"
#include "awt_KeyboardFocusManager.h"
#include "awt_MToolkit.h"
#include "awt_TopLevel.h"
#include "java_awt_Color.h"
#include "java_awt_Cursor.h"
#include "java_awt_Font.h"
#include "java_awt_Point.h"
#include "java_awt_Component.h"
#include "java_awt_AWTEvent.h"
#include "java_awt_KeyboardFocusManager.h"
#include "java_awt_event_KeyEvent.h"
#include "java_awt_event_MouseEvent.h"
#include "sun_awt_motif_MComponentPeer.h"

#include "multi_font.h"
#include "jni.h"
#include "jni_util.h"
#include <jawt.h>
#include <Xm/PrimitiveP.h>
#include <Xm/ManagerP.h>
#include <Xm/ComboBox.h>

/* CanvasType widgets: Frame, Dialog, Window, Panel, Canvas,
 *                     &  all lightweights (Component, Container)
 */
#define IsCanvasTypeWidget(w) \
        XtIsSubclass(w, xmDrawingAreaWidgetClass) ||\
        XtIsSubclass(w, vDrawingAreaClass)


#include "awt_Component.h"
#include "awt_GraphicsEnv.h"

#include "awt_AWTEvent.h"
#include "awt_Cursor.h"

extern struct CursorIDs cursorIDs;
extern struct X11GraphicsConfigIDs x11GraphicsConfigIDs;
extern struct KeyboardFocusManagerIDs keyboardFocusManagerIDs;

/* fieldIDs for Component fields that may be accessed from C */
struct ComponentIDs componentIDs;

/*
 * Class:     java_awt_Component
 * Method:    initIDs
 * Signature: ()V
 */

/* This function gets called from the static initializer for Component.java
   to initialize the fieldIDs for fields that may be accessed from C */

JNIEXPORT void JNICALL
Java_java_awt_Component_initIDs
(JNIEnv *env, jclass cls)
{
    jclass keyclass = NULL;

    componentIDs.x = (*env)->GetFieldID(env, cls, "x", "I");
    componentIDs.y = (*env)->GetFieldID(env, cls, "y", "I");
    componentIDs.width = (*env)->GetFieldID(env, cls, "width", "I");
    componentIDs.height = (*env)->GetFieldID(env, cls, "height", "I");
    componentIDs.isPacked = (*env)->GetFieldID(env, cls, "isPacked", "Z");
    componentIDs.peer =
        (*env)->GetFieldID(env, cls, "peer", "Ljava/awt/peer/ComponentPeer;");
    componentIDs.background =
        (*env)->GetFieldID(env, cls, "background", "Ljava/awt/Color;");
    componentIDs.foreground =
        (*env)->GetFieldID(env, cls, "foreground", "Ljava/awt/Color;");
    componentIDs.graphicsConfig =
        (*env)->GetFieldID(env, cls, "graphicsConfig",
                           "Ljava/awt/GraphicsConfiguration;");
    componentIDs.name =
        (*env)->GetFieldID(env, cls, "name", "Ljava/lang/String;");

    /* Use _NoClientCode() methods for trusted methods, so that we
     *  know that we are not invoking client code on trusted threads
     */
    componentIDs.getParent =
        (*env)->GetMethodID(env, cls, "getParent_NoClientCode",
                            "()Ljava/awt/Container;");

    componentIDs.getLocationOnScreen =
        (*env)->GetMethodID(env, cls, "getLocationOnScreen_NoTreeLock",
                            "()Ljava/awt/Point;");

    componentIDs.resetGCMID =
        (*env)->GetMethodID(env, cls, "resetGC", "()V");

    keyclass = (*env)->FindClass(env, "java/awt/event/KeyEvent");
    DASSERT (keyclass != NULL);

    componentIDs.isProxyActive =
        (*env)->GetFieldID(env, keyclass, "isProxyActive",
                           "Z");

    componentIDs.appContext =
        (*env)->GetFieldID(env, cls, "appContext",
                           "Lsun/awt/AppContext;");

    (*env)->DeleteLocalRef(env, keyclass);

    DASSERT(componentIDs.resetGCMID);
}

/* fieldIDs for MComponentPeer fields that may be accessed from C */
struct MComponentPeerIDs mComponentPeerIDs;

/*
 * Class:     sun_awt_motif_MComponentPeer
 * Method:    initIDs
 * Signature: ()V
 */

/* This function gets called from the static initializer for
   MComponentPeer.java to initialize the fieldIDs for fields that may
   be accessed from C */

JNIEXPORT void JNICALL Java_sun_awt_motif_MComponentPeer_initIDs
(JNIEnv *env, jclass cls)
{
    mComponentPeerIDs.pData = (*env)->GetFieldID(env, cls, "pData", "J");
    mComponentPeerIDs.target =
        (*env)->GetFieldID(env, cls, "target", "Ljava/awt/Component;");
    mComponentPeerIDs.jniGlobalRef =
        (*env)->GetFieldID(env, cls, "jniGlobalRef", "J");
    mComponentPeerIDs.graphicsConfig =
        (*env)->GetFieldID(env, cls, "graphicsConfig",
                           "Lsun/awt/X11GraphicsConfig;");
    mComponentPeerIDs.drawState =
        (*env)->GetFieldID(env, cls, "drawState", "I");
    mComponentPeerIDs.isFocusableMID =
        (*env)->GetMethodID(env, cls, "isFocusable", "()Z");
}

/* field and method IDs for java.awt.Container. */
struct ContainerIDs containerIDs;

/*
 * Class:     java_awt_Container
 * Method:    initIDs
 * Signature: ()V
 */
/* This function gets called from the static initializer for java.awt.Container
   to initialize the fieldIDs for fields that may be accessed from C */
JNIEXPORT void JNICALL
Java_java_awt_Container_initIDs
(JNIEnv *env, jclass cls)
{
    containerIDs.layoutMgr =
        (*env)->GetFieldID(env, cls, "layoutMgr", "Ljava/awt/LayoutManager;");

    containerIDs.getComponents =
        (*env)->GetMethodID(env, cls, "getComponents_NoClientCode",
                            "()[Ljava/awt/Component;");
    containerIDs.findComponentAt =
        (*env)->GetMethodID(env, cls, "findComponentAt",
                            "(IIZ)Ljava/awt/Component;");
}

/*
 * Fix for 4090493.  When Motif computes indicator size, it uses
 * (effectively) XmTextExtents, so the size of the indicator depends
 * on the text of the label.  The side effect is that if the label
 * text is rendered using different platform fonts (for a single Java
 * logical font) the display is inconsistent.  E.g. for 12pt font
 * English label will have a check mark, while Japanese label will
 * not, because underlying X11 fonts have different metrics.
 *
 * The fix is to override Motif calculations for the indicatorSize and
 * compute it ourselves based on the font metrics for all the platform
 * fonts given Java font maps onto.  Every time we set XmNfontList we
 * should set XmNindicatorSize as well.
 *
 * The logic is in awt_computeIndicatorSize that just compute the
 * arithmetic mean of platform fonts by now.  HIE should take a look
 * at this.
 */

struct changeFontInfo {
    XmFontList fontList;        /* value to set */
    Boolean isMultiFont;        /* only need to compute for multifont */
    struct FontData *fontData;  /* need this to compute indicator size */
    Dimension indSize;          /* computed once by changeFont */

    Boolean    initialized;
    Boolean    error;
    JNIEnv     *env;
    jobject    fObj;
};

static void
changeFont(Widget w, void *info)
{
    struct changeFontInfo *f = (struct changeFontInfo *)info;
    WidgetClass widgetClass;

    if (f->error)
        return;

    /* Some widgets use no fonts - skip them! */
    /* Also skip the Text widgets, since they each have their own setFont. */
    widgetClass = XtClass(w);
    if (widgetClass == xmDrawingAreaWidgetClass    ||
        widgetClass == xmScrollBarWidgetClass      ||
        widgetClass == xmScrolledWindowWidgetClass ||
        widgetClass == xmComboBoxWidgetClass       ||
        widgetClass == xmTextWidgetClass           ||
        widgetClass == xmTextFieldWidgetClass)
        return;

    if (!f->initialized) {
        struct FontData *fdata;
        char *err;

        f->initialized = TRUE;

        fdata = awtJNI_GetFontData(f->env, f->fObj, &err);
        if (fdata == NULL) {
            JNU_ThrowInternalError(f->env, err);
            f->error = TRUE;
            return;
        }

        if (awtJNI_IsMultiFont(f->env, f->fObj)) {
            f->fontList = awtJNI_GetFontList(f->env, f->fObj);
            f->isMultiFont = TRUE;
        } else {
            f->fontList = XmFontListCreate(fdata->xfont, "labelFont");
            f->isMultiFont = FALSE;
        }

        if (f->fontList == NULL) {
            JNU_ThrowNullPointerException(f->env, "NullPointerException");
            f->error = TRUE;
            return;
        }
    }

    /* Fix for 4090493. */
    if (f->isMultiFont && XmIsToggleButton(w)) {
        Dimension indSize;

        /* Compute indicator size if first time through.  Note that
           ToggleButtons that are children of menus live in different
           hierarchy (MenuComponent), so we don't check for this case
           here.  In fact, the only time the XmNfontList is set on
           MCheckboxMenuItemPeer widget is when it is created. */
        if (f->indSize == 0)
            f->indSize = awt_computeIndicatorSize(f->fontData);

        XtVaSetValues(w, XmNfontList, f->fontList, NULL);
        if (f->indSize != MOTIF_XmINVALID_DIMENSION)
            XtVaSetValues(w, XmNindicatorSize, f->indSize, NULL);
    }
    else {                      /* usual case */
        XtVaSetValues(w, XmNfontList, f->fontList, NULL);
    }
}

static void
changeForeground(Widget w, void *fg)
{
    XtVaSetValues(w, XmNforeground, fg, NULL);
}

static void
changeBackground(Widget w, void *bg)
{
    Pixel fg;

    XtVaGetValues(w, XmNforeground, &fg, NULL);
    XmChangeColor(w, (Pixel) bg);
    XtVaSetValues(w, XmNforeground, fg, NULL);
}

// Sets widget's traversalOn property into value 'value'
void setTraversal(Widget w, Boolean value) {
    if (w == NULL) {
        return;
    }
    if (XmIsPrimitive(w)) {
        XmPrimitiveWidget prim = (XmPrimitiveWidget)w;
        prim->primitive.traversal_on = value;
    } else
        if (XmIsManager(w)) {
            XmManagerWidget man = (XmManagerWidget)w;
            man->manager.traversal_on = value;
        }
}


AwtGraphicsConfigDataPtr
getGraphicsConfigFromComponentPeer(JNIEnv *env, jobject this) {
    AwtGraphicsConfigDataPtr adata;
    jobject gc_object;

    /* GraphicsConfiguration object of MComponentPeer */
    gc_object = (*env)->GetObjectField(env, this,
                                       mComponentPeerIDs.graphicsConfig);

    if (gc_object != NULL) {
        adata = (AwtGraphicsConfigDataPtr)
            JNU_GetLongFieldAsPtr(env, gc_object,
                                  x11GraphicsConfigIDs.aData);
    } else {
        adata = getDefaultConfig(DefaultScreen(awt_display));
    }

    return adata;
}

AwtGraphicsConfigDataPtr
copyGraphicsConfigToPeer(JNIEnv *env, jobject this) {

    jobject component_object, gc_object;
    AwtGraphicsConfigDataPtr adata;

    /**
     * Copy the GraphicsConfiguration object from Component object to
     * MComponentPeer object.
     */
    component_object = (*env)->GetObjectField(env, this,
                                              mComponentPeerIDs.target);
    /* GraphicsConfiguration object of Component */
    gc_object = (JNU_CallMethodByName(env, NULL, component_object,
                                      "getGraphicsConfiguration",
                                      "()Ljava/awt/GraphicsConfiguration;")).l;

    if (gc_object != NULL) {
        /* Set graphicsConfig field of MComponentPeer */
        (*env)->SetObjectField (env, this,
                                mComponentPeerIDs.graphicsConfig,
                                gc_object);
        adata = (AwtGraphicsConfigDataPtr)
            JNU_GetLongFieldAsPtr(env, gc_object,
                                  x11GraphicsConfigIDs.aData);
    } else {
        /* Component was not constructed with a GraphicsConfiguration
           object */
        adata = getDefaultConfig(DefaultScreen(awt_display));
    }

    return adata;
}

/*
 * Class:     sun_awt_motif_MComponentPeer
 * Method:    getNativeColor
 * Signature  (Ljava/awt/Color;Ljava/awt/GraphicsConfiguration;)I
 */
JNIEXPORT jint JNICALL Java_sun_awt_motif_MComponentPeer_getNativeColor
(JNIEnv *env, jobject this, jobject color, jobject gc_object) {
    AwtGraphicsConfigDataPtr adata;
    adata = (AwtGraphicsConfigDataPtr) JNU_GetLongFieldAsPtr(env, gc_object,
                                                             x11GraphicsConfigIDs.aData);
    return awtJNI_GetColorForVis(env, color, adata);
}

/*
 * Class:     sun_awt_motif_MComponentPeer
 * Method:    pInitialize
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_sun_awt_motif_MComponentPeer_pInitialize
(JNIEnv *env, jobject this)
{
    struct ComponentData *cdata;
    Widget parent;
    jobject target;
    jobject globalRef;
    EventMask xtMask;
    jlong awtMask = (jlong) 0;
    AwtGraphicsConfigDataPtr adata;
    Boolean initialTraversal = False;

    globalRef = (jobject)
        JNU_GetLongFieldAsPtr(env, this, mComponentPeerIDs.jniGlobalRef);

    adata = copyGraphicsConfigToPeer(env, this);

    AWT_LOCK();

    target = (*env)->GetObjectField(env, this, mComponentPeerIDs.target);

    cdata = (struct ComponentData *)
        JNU_GetLongFieldAsPtr(env,this,mComponentPeerIDs.pData);
    if (JNU_IsNull(env, cdata) || (cdata == NULL)) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        AWT_UNLOCK();
        return;
    }
    /* Allow FileDialog to have its own traversal policy because
     * it doesn't interfer with our.
     */
    if (XtIsSubclass(cdata->widget, xmFileSelectionBoxWidgetClass)) {
        initialTraversal = True;
    }
    XtVaSetValues(cdata->widget,
                  XmNx, (*env)->GetIntField(env, target, componentIDs.x),
                  XmNy, (*env)->GetIntField(env, target, componentIDs.y),
                  XmNvisual, adata->awt_visInfo.visual,
                  XmNscreen, ScreenOfDisplay(awt_display,
                                             adata->awt_visInfo.screen),
                  /**
                   * From now we keep all but the focus owner widget unable
                   * to receive focus. This will prevent Motif from unexpected
                   * focus transfers.
                   */
                  XmNtraversalOn, initialTraversal,
                  NULL);


    /* For all but canvas-style components, pre-process
     * mouse and keyboard events (which means posting them
     * to the Java EventQueue before dispatching them to Xt).
     * For canvas-style components ONLY pre-process mouse events
     * because the input-method currently relies on key events
     * being processed by Xt first.
     */
    awtMask = java_awt_AWTEvent_MOUSE_EVENT_MASK |
        java_awt_AWTEvent_MOUSE_MOTION_EVENT_MASK;
    xtMask = ExposureMask | FocusChangeMask;

    if (!IsCanvasTypeWidget(cdata->widget)) {
        awtMask |= java_awt_AWTEvent_KEY_EVENT_MASK;
    } else {
        xtMask |= (KeyPressMask | KeyReleaseMask);
    }
    XtAddEventHandler(cdata->widget, xtMask,
                      True, awt_canvas_event_handler, globalRef);

    awt_addWidget(cdata->widget, cdata->widget, globalRef, awtMask);

    cdata->repaintPending = RepaintPending_NONE;

    AWT_UNLOCK();
}

/**
 * Updates stacking order of X windows according to the order of children widgets in
 * parent widget
 */
void restack(Widget parent) {
    WidgetList children;
    int32_t num_children;
    Window *windows;
    int32_t num_windows = 0;
    int32_t i;
    XtVaGetValues(parent,
                  XmNnumChildren, &num_children,
                  XmNchildren, &children,
                  NULL);

    windows = (Window *) XtMalloc(num_children * sizeof(Window));
    for (i = 0; i < num_children; i++) {
        if (XtIsRealized(children[i])) {
            windows[num_windows++] = XtWindow(children[i]);
        }
    }
    XRestackWindows(awt_display, windows, num_windows);
    XtFree((char *) windows);
}

/*
 * Class:     sun_awt_motif_MComponentPeer
 * Method:    pShow
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_sun_awt_motif_MComponentPeer_pShow
(JNIEnv *env, jobject this)
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

    awt_util_show(cdata->widget);

    AWT_FLUSH_UNLOCK();
}

/*
 * Class:     sun_awt_motif_MComponentPeer
 * Method:    pHide
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_sun_awt_motif_MComponentPeer_pHide
(JNIEnv *env, jobject this)
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

    awt_util_hide(cdata->widget);
    AWT_FLUSH_UNLOCK();
}

/*
 * Class:     sun_awt_motif_MComponentPeer
 * Method:    pEnable
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_sun_awt_motif_MComponentPeer_pEnable
(JNIEnv *env, jobject this)
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
    awt_util_enable(cdata->widget);
    AWT_UNLOCK();
}

/*
 * Class:     sun_awt_motif_MComponentPeer
 * Method:    pDisable
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_sun_awt_motif_MComponentPeer_pDisable
(JNIEnv *env, jobject this)
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
    awt_util_disable(cdata->widget);
    AWT_FLUSH_UNLOCK();
}

/*
 * Class:     sun_awt_motif_MComponentPeer
 * Method:    pReshape
 * Signature: (IIII)V
 */
JNIEXPORT void JNICALL Java_sun_awt_motif_MComponentPeer_pReshape
(JNIEnv *env, jobject this, jint x, jint y, jint w, jint h)
{
    struct ComponentData *cdata;
    jint drawState;

    AWT_LOCK();
    cdata = (struct ComponentData *)
        JNU_GetLongFieldAsPtr(env,this,mComponentPeerIDs.pData);
    if (cdata == NULL || cdata->widget == NULL) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        AWT_UNLOCK();
        return;
    }
    /* Set the draw state */
    drawState = (*env)->GetIntField(env, this,
                                    mComponentPeerIDs.drawState);
    (*env)->SetIntField(env, this,
                        mComponentPeerIDs.drawState,
                        drawState | JAWT_LOCK_BOUNDS_CHANGED | JAWT_LOCK_CLIP_CHANGED);
    awt_util_reshape(cdata->widget, x, y, w, h);

    AWT_FLUSH_UNLOCK();
}

/*
 * Class:     sun_awt_motif_MComponentPeer
 * Method:    pDispose
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_sun_awt_motif_MComponentPeer_pDispose
(JNIEnv *env, jobject this)
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

    XtUnmanageChild(cdata->widget);

    awt_delWidget(cdata->widget);
    awt_util_consumeAllXEvents(cdata->widget);
    awt_util_cleanupBeforeDestroyWidget(cdata->widget);
    XtDestroyWidget(cdata->widget);

    free((void *) cdata);
    (*env)->SetLongField(env,this,mComponentPeerIDs.pData, (int64_t) 0);

    awtJNI_DeleteGlobalRef(env, this);

    AWT_UNLOCK();
}

/*
 * Class:     sun_awt_motif_MComponentPeer
 * Method:    pMakeCursorVisible
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_sun_awt_motif_MComponentPeer_pMakeCursorVisible
(JNIEnv *env, jobject this)
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
    // need to change, may not be needed
    // awt_util_setCursor(cdata->widget, cdata->cursor);

    AWT_FLUSH_UNLOCK();
}


/*
 * Call with AWT_LOCK held.
 */
static jobject
MComponentPeer_doGetLocationOnScreen(JNIEnv *env, jobject this)
{
    jobject point = NULL;
    struct ComponentData *cdata;
    int32_t x = 0, y = 0;
    Screen *widget_screen = NULL;
    Window child_ignored;

    cdata = (struct ComponentData *)
        JNU_GetLongFieldAsPtr(env, this, mComponentPeerIDs.pData);
    if (cdata == NULL || cdata->widget == NULL) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        return NULL;
    }
    if (!XtIsRealized(cdata->widget)) {
        JNU_ThrowInternalError(env, "widget not visible on screen");
        return NULL;
    }

    /* Translate the component to the screen coordinate system */
    XtVaGetValues(cdata->widget, XmNscreen, &widget_screen, NULL);
    XTranslateCoordinates(awt_display, XtWindow(cdata->widget),
                          XRootWindowOfScreen(widget_screen),
                          0, 0, &x, &y,
                          &child_ignored);

    point = JNU_NewObjectByName(env, "java/awt/Point", "(II)V",
                                (jint)x, (jint)y);
    if (((*env)->ExceptionOccurred(env)) || JNU_IsNull(env, point)) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        return NULL;
    }

    return point;
}


/*
 * Class:     sun_awt_motif_MComponentPeer
 * Method:    pGetLocationOnScreen
 * Signature: ()Ljava/awt/Point;
 */
JNIEXPORT jobject JNICALL
Java_sun_awt_motif_MComponentPeer_pGetLocationOnScreen(JNIEnv *env,
                                                       jobject this)
{
    jobject point;

    AWT_LOCK();
    point = MComponentPeer_doGetLocationOnScreen(env, this);
    AWT_UNLOCK();
    return point;
}


/*
 * Class:     sun_awt_motif_MComponentPeer
 * Method:    pGetLocationOnScreen
 * Signature: (Ljava/awt/Window;Lsun/awt/motif/MWindowPeer;)Ljava/awt/Point;
 */
JNIEXPORT jobject JNICALL
Java_sun_awt_motif_MComponentPeer_pGetLocationOnScreen2(
    JNIEnv *env, jobject this, jobject wtarget, jobject wpeer)
{
    jobject point;
    struct ComponentData *cdata;
    struct FrameData *wdata;
    Screen *widget_screen = NULL;
    Window child_ignored;
    int32_t x = 0, y = 0;

    AWT_LOCK();

    wdata = (struct ComponentData *)
        JNU_GetLongFieldAsPtr(env, wpeer, mComponentPeerIDs.pData);

    if (wdata == NULL || wdata->winData.comp.widget == NULL) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        AWT_UNLOCK();
        return NULL;
    }
    if (!XtIsRealized(wdata->winData.comp.widget)) {
        JNU_ThrowInternalError(env, "widget not visible on screen");
        AWT_UNLOCK();
        return NULL;
    }

    /*
     * Translate directly if the parent window is already adopted by WM.
     */
    if (wdata->configure_seen) {
        point = MComponentPeer_doGetLocationOnScreen(env, this);
        AWT_UNLOCK();
        return point;
    }

    /*
     * We are called while the parent window is still not adopted by
     * WM (but may already be in the process of being reparented).
     * Translate to the parent and add parent target's (x,y) to avoid
     * racing with WM shuffling us into the final position.
     */
    cdata = (struct ComponentData *)
        JNU_GetLongFieldAsPtr(env, this, mComponentPeerIDs.pData);

    if (cdata == &wdata->winData.comp) { /* called for the window itself */
        x = y = 0;
    }
    else {
        if (cdata == NULL || cdata->widget == NULL) {
            JNU_ThrowNullPointerException(env, "NullPointerException");
            AWT_UNLOCK();
            return NULL;
        }
        if (!XtIsRealized(cdata->widget)) {
            JNU_ThrowInternalError(env, "widget not visible on screen");
            AWT_UNLOCK();
            return NULL;
        }

        /* Translate to the outer canvas coordinate system first */
        XtVaGetValues(cdata->widget, XmNscreen, &widget_screen, NULL);
        XTranslateCoordinates(awt_display, XtWindow(cdata->widget),
                              XtWindow(wdata->winData.comp.widget),
                              0, 0, &x, &y,
                              &child_ignored);
    }

    x += (*env)->GetIntField(env, wtarget, componentIDs.x);
    y += (*env)->GetIntField(env, wtarget, componentIDs.y);

    point = JNU_NewObjectByName(env, "java/awt/Point", "(II)V",
                                (jint)x, (jint)y);
    if (((*env)->ExceptionOccurred(env)) || JNU_IsNull(env, point)) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        AWT_UNLOCK();
        return NULL;
    }

    AWT_UNLOCK();
    return point;
}


/*
 * Class:     sun_awt_motif_MComponentPeer
 * Method:    getParent_NoClientCode
 * Signature: (Ljava/awt/Component)Ljava/awt/Container;
 *
 * NOTE: This method may be called by privileged threads.
 *       DO NOT INVOKE CLIENT CODE ON THIS THREAD!
 */
JNIEXPORT jobject JNICALL Java_sun_awt_motif_MComponentPeer_getParent_1NoClientCode
(JNIEnv *env, jclass thisClass, jobject component)
{
    jobject parent = NULL;

    /* getParent is actually getParent_NoClientCode() */
    parent = (*env)->CallObjectMethod(env,component,componentIDs.getParent);
    DASSERT(!((*env)->ExceptionOccurred(env)));
    return parent;
}

/*
 * Class:     sun_awt_motif_MComponentPeer
 * Method:    getComponents_NoClientCode
 * Signature: (Ljava/awt/Container)[Ljava/awt/Component;
 *               REMIND: Signature is incorrect for returned array value
 *
 * NOTE: This method may be called by privileged threads.
 *       DO NOT INVOKE CLIENT CODE ON THIS THREAD!
 */
JNIEXPORT jobjectArray JNICALL Java_sun_awt_motif_MComponentPeer_getComponents_1NoClientCode
(JNIEnv *env, jclass thisClass, jobject container)
{
    jobjectArray contents = NULL;
    contents = (*env)->CallObjectMethod(
        env, container, containerIDs.getComponents);
    DASSERT(!((*env)->ExceptionOccurred(env)));
    return contents;
}

/*
 * Class:     sun_awt_motif_MComponentPeer
 * Method:    pSetForeground
 * Signature: (Ljava/awt/Color;)V
 */
JNIEXPORT void JNICALL Java_sun_awt_motif_MComponentPeer_pSetForeground
(JNIEnv *env, jobject this, jobject c)
{
    struct ComponentData *bdata;
    Pixel color;
    AwtGraphicsConfigDataPtr adata;

    if (JNU_IsNull(env, c)) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        return;
    }
    AWT_LOCK();
    bdata = (struct ComponentData *)
        JNU_GetLongFieldAsPtr(env,this,mComponentPeerIDs.pData);
    if (bdata == NULL || bdata->widget == NULL) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        AWT_UNLOCK();
        return;
    }

    adata = getGraphicsConfigFromComponentPeer(env, this);

    color = (Pixel) awtJNI_GetColorForVis (env, c, adata);
    XtVaSetValues(bdata->widget, XmNforeground, color, NULL);
    AWT_FLUSH_UNLOCK();
}

/*
 * Class:     sun_awt_motif_MComponentPeer
 * Method:    pSetBackground
 * Signature: (Ljava/awt/Color;)V
 */
JNIEXPORT void JNICALL Java_sun_awt_motif_MComponentPeer_pSetBackground
(JNIEnv *env, jobject this, jobject c)
{
    struct ComponentData *bdata;
    Pixel color;
    Pixel fg;
    AwtGraphicsConfigDataPtr adata;

    if (JNU_IsNull(env, c)) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        return;
    }
    AWT_LOCK();
    bdata = (struct ComponentData *)
        JNU_GetLongFieldAsPtr(env,this,mComponentPeerIDs.pData);
    if (bdata == NULL || bdata->widget == NULL) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        AWT_UNLOCK();
        return;
    }

    adata = getGraphicsConfigFromComponentPeer(env, this);

    color = (Pixel) awtJNI_GetColorForVis (env, c, adata);
    XtVaGetValues(bdata->widget, XmNforeground, &fg, NULL);
    XmChangeColor(bdata->widget, color);
    XtVaSetValues(bdata->widget, XmNforeground, fg, NULL);
    AWT_FLUSH_UNLOCK();
}

/*
 * Class:     sun_awt_motif_MComponentPeer
 * Method:    pSetScrollbarBackground
 * Signature: (Ljava/awt/Color;)V
 */
JNIEXPORT void JNICALL Java_sun_awt_motif_MComponentPeer_pSetScrollbarBackground
(JNIEnv *env, jobject this, jobject c)
{
    struct ComponentData *bdata;
    Pixel color;
    Pixel fg;
    int32_t                 i;
    WidgetList          wlist;
    Cardinal            wlen = 0;

    /* This method propagates the specified background color to the scrollbars in the composite widget.
     * Used to set background scrollbar color in List, TextArea, ScrollPane to its parent.
     */
    if (JNU_IsNull(env, c)) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        return;
    }
    AWT_LOCK();
    bdata = (struct ComponentData *)
        JNU_GetLongFieldAsPtr(env,this,mComponentPeerIDs.pData);
    if (bdata == NULL || bdata->widget == NULL) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        AWT_UNLOCK();
        return;
    }
    if (!XtIsComposite(bdata->widget)) {
        AWT_UNLOCK();
        return;
    }
    color = (Pixel) awtJNI_GetColor(env, c);

    XtVaGetValues(bdata->widget,
                  XmNchildren, &wlist,
                  XmNnumChildren, &wlen,
                  NULL);
    if (wlen > 0) { /* this test doesn't make much sense, since wlen
                       is a Cardinal and cardinal is unsigned int... */
        for (i=0; i < wlen; i++) {
            if (XtIsSubclass(wlist[i], xmScrollBarWidgetClass)) {
                XtVaGetValues(wlist[i], XmNforeground, &fg, NULL);
                XmChangeColor(wlist[i], color);
                XtVaSetValues(wlist[i], XmNforeground, fg, NULL);
            }
        }
        XtVaGetValues(bdata->widget, XmNforeground, &fg, NULL);
        XmChangeColor(bdata->widget, color);
        XtVaSetValues(bdata->widget, XmNforeground, fg, NULL);
    }

    AWT_FLUSH_UNLOCK();
}

/*
 * Class:     sun_awt_motif_MComponentPeer
 * Method:    pSetInnerForeground
 * Signature: (Ljava/awt/Color;)V
 */
JNIEXPORT void JNICALL Java_sun_awt_motif_MComponentPeer_pSetInnerForeground
(JNIEnv *env, jobject this, jobject c)
{
    struct ComponentData *bdata;
    Pixel color;

    /* This method propagates the specified foreground color to all its children.
     * It is called to set foreground color in List, TextArea, ScrollPane.
     */
    if (JNU_IsNull(env, c)) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        return;
    }
    AWT_LOCK();
    bdata = (struct ComponentData *)
        JNU_GetLongFieldAsPtr(env,this,mComponentPeerIDs.pData);
    if (bdata == NULL || bdata->widget == NULL) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        AWT_UNLOCK();
        return;
    }
    color = awtJNI_GetColor(env, c);
    awt_util_mapChildren(bdata->widget, changeForeground, 1, (void *) color);
    AWT_FLUSH_UNLOCK();
}

/*
 * Class:     sun_awt_motif_MComponentPeer
 * Method:    pSetFont
 * Signature: (Ljava/awt/Font;)V
 */
JNIEXPORT void JNICALL Java_sun_awt_motif_MComponentPeer_pSetFont
(JNIEnv *env, jobject this, jobject f)
{
    struct ComponentData *cdata;

    struct changeFontInfo finfo = { NULL, FALSE, NULL, 0,
                                    FALSE, FALSE, NULL, NULL };

    if (JNU_IsNull(env, f)) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        return;
    }
    AWT_LOCK();

    cdata = (struct ComponentData *)
        JNU_GetLongFieldAsPtr(env,this,mComponentPeerIDs.pData);
    if (cdata == NULL || cdata->widget == NULL) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        AWT_UNLOCK();
        return;
    }

    finfo.env = env;
    finfo.fObj = f;
    awt_util_mapChildren(cdata->widget, changeFont, 1, (void *)&finfo);
    if (!finfo.error && finfo.fontList != NULL) {
        XmFontListFree(finfo.fontList);
    }

    AWT_FLUSH_UNLOCK();
} /* MComponentPeer.pSetFont() */

/*
 * Class:     sun_awt_motif_MComponentPeer
 * Method:    setTargetBackground
 * Signature: (Ljava/awt/Color;)V
 */
JNIEXPORT void JNICALL Java_sun_awt_motif_MComponentPeer_setTargetBackground
(JNIEnv *env, jobject this, jobject c)
{
    jobject target = NULL;

    if ((*env)->EnsureLocalCapacity(env, 1) < 0) {
        return;
    }

    target = (*env)->GetObjectField(env, this, mComponentPeerIDs.target);
    (*env)->SetObjectField(env, target, componentIDs.background, c);
    (*env)->DeleteLocalRef(env, target);
}

/*
 * Class:     sun_awt_motif_MComponentPeer
 * Method:    pSetCursor
 * Signature: (Ljava/awt/Cursor;)V
 */
JNIEXPORT void JNICALL Java_sun_awt_motif_MComponentPeer_pSetCursor
(JNIEnv *env, jobject this, jobject cursor)
{
    Cursor xcursor;
    struct ComponentData *cdata;

    AWT_LOCK();

    cdata = (struct ComponentData *)
        JNU_GetLongFieldAsPtr(env, this, mComponentPeerIDs.pData);
    if (cdata == NULL || cdata->widget == NULL || JNU_IsNull(env, cursor)) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        AWT_UNLOCK();
        return;
    }

    awt_util_setCursor(cdata->widget, getCursor(env, cursor));

    AWT_FLUSH_UNLOCK();
}

/*
 * Class:     sun_awt_motif_MComponentPeer
 * Method:    nativeHandleEvent
 * Signature: (Ljava/awt/AWTEvent;)V
 */
JNIEXPORT void JNICALL Java_sun_awt_motif_MComponentPeer_nativeHandleEvent
(JNIEnv *env, jobject this, jobject event)
{
    extern void awt_modify_KeyEvent(JNIEnv *env, XEvent * xevent, jobject jevent);
    jbyteArray array;
    XEvent *xevent;
    Widget widget = NULL;
    Boolean consumed;

    if (JNU_IsNull(env, event)) {
        return;
    }
    AWT_LOCK();

    consumed = (*env)->GetBooleanField(env, event, awtEventIDs.consumed);

    /*
     * Fix for bug 4280561
     *
     * If a menu is up, we must dispatch all XEvents, to allow
     * mouse grabs to be released and prevent server hangs.
     */
    consumed = consumed && !awt_util_focusIsOnMenu(awt_display);

    if (consumed) {
        AWT_UNLOCK();
        return;
    }

    array = (jbyteArray)(*env)->GetObjectField(env, event, awtEventIDs.bdata);
    if (array == NULL) {
        AWT_UNLOCK();
        return;
    }

    xevent = (XEvent *)(*env)->GetByteArrayElements(env, array, NULL);
    if (xevent == NULL) {
        AWT_UNLOCK();
        return;
    }

    switch ((*env)->GetIntField(env, event, awtEventIDs.id)) {
      case java_awt_event_KeyEvent_KEY_RELEASED:
      case java_awt_event_KeyEvent_KEY_PRESSED:
          awt_modify_KeyEvent(env, xevent, event);
          if ((*env)->GetBooleanField(env, event, componentIDs.isProxyActive) == JNI_TRUE) {
              xevent->xany.send_event = SPECIAL_KEY_EVENT;
          }
          break;
      default:
          break;
    }
    widget = XtWindowToWidget(awt_display, xevent->xany.window);

    if (!((widget == NULL) || (!XtIsObject(widget)) ||
          (widget->core.being_destroyed))) {
        /* Queue the event to be handled by the AWT-Motif thread */
        if (!IsCanvasTypeWidget(widget)) {
            awt_put_back_event(env, xevent);
        }
    }

    (*env)->ReleaseByteArrayElements(env, array, (jbyte *)xevent, JNI_ABORT);
    (*env)->DeleteLocalRef(env, array);

    AWT_UNLOCK();
    return;
}

// Returns the widget from parent's hierarchy which should be
// used for focus operations. This widget is stored in WidgetInfo
// structure and should be prepared by the appropriate component
// type constructor
Widget getFocusWidget(Widget parent) {
    struct WidgetInfo * winfo = NULL;
    DASSERT(parent != NULL);
    if (parent == NULL) {
        return NULL;
    }
    winfo = findWidgetInfo(parent);
    if (winfo == NULL) {
        return NULL;
    }
    return winfo->widget;
}


// Returns value of widget's traversalOn property
Boolean getTraversal(Widget w) {
    if (w == NULL) {
        return False;
    }
    if (XmIsPrimitive(w)) {
        XmPrimitiveWidget prim = (XmPrimitiveWidget)w;
        return prim->primitive.traversal_on;
    }
    if (XmIsManager(w)) {
        XmManagerWidget man = (XmManagerWidget)w;
        return man->manager.traversal_on;
    }
    return False;
}


void processTree(Widget from, Widget to, Boolean action) {
// Workhorse function that makes sure that the only widgets
// which have traversalOn == true are the ones on the path from
// shell to current focus widget. Function uses two widgets -
// the one which is supposed to have focus currently(from) and
// the one which will receive focus(to). Function disables and
// enables the appropriate widgets so 'to' can become focus owner.
    JNIEnv      *env = (JNIEnv *)JNU_GetEnv(jvm, JNI_VERSION_1_2);
    int32_t count_from = 0, count_to = 0;
    Widget parent_from = NULL, parent_to = NULL;
    Widget * parents_from = NULL, * parents_to = NULL;
    int32_t index = 0;

    // Count amount of parents up the tree from widget
    parent_from = from;
    while (parent_from != NULL) {
        parent_from = XtParent(parent_from);
        count_from++;
    }
    parent_to = to;
    while (parent_to != NULL) {
        parent_to = XtParent(parent_to);
        count_to++;
    }

    // Store all the parents in the list. Both list wittingly
    // have common parts starting from the beginning. We need
    // to find the end of this common part.
    parents_from = (Widget*)malloc(count_from*sizeof(Widget*));
    parents_to = (Widget*)malloc(count_to*sizeof(Widget*));
    parent_from = from;
    index = count_from;
    while (parent_from != NULL) {
        parents_from[index-1] = parent_from;
        parent_from = XtParent(parent_from);
        index--;
    }
    parent_to = to;
    index = count_to;
    while (parent_to != NULL) {
        parents_to[index-1] = parent_to;
        parent_to = XtParent(parent_to);
        index--;
    }

    // Process parents list. Find common part which is usually doesn't
    // require changes. At the exit of the cycle index will point
    // to the first widget which requeires the change.

    if (from != NULL && to != NULL) {
        do {
            if (index >= count_from-1 || index >= count_to-1) {
                break;
            }
            if (parents_from[index] == parents_to[index])
            {
                if (XtIsShell(parents_from[index])) {
                    index++;
                    continue;
                }
                if (action) {
                    if (getTraversal(parents_from[index])) {
                        index++;
                    } else {
                        break;
                    }
                } else {
                    index++;
                }
            } else {
                break;
            }
        } while (True);
    }


    if (action) { // enable the tree starting from uncommon part till 'to'
        if (to != NULL) {
            while (index < count_to - 1) {
                if (!getTraversal(parents_to[index])) {
                    XtVaSetValues(parents_to[index], XmNtraversalOn, True, NULL);
                }
                index++;
            }
            XtVaSetValues(to, XmNtraversalOn, True, NULL);
        }
    } else if (from != NULL) {
        // disable the tree starting from uncommon part to 'from'
        if (parents_from[index] == parents_to[index]) {
            if (index == count_from - 1) {
                // 'from' is one of the parents of 'to' - no need
                // to disable 'from'
                goto skip_disable;
            }
            index++;
        }
        while (index < count_from - 1) {
            if (!XmIsGadget(parents_from[index]) && !XtIsShell(parents_from[index])) {
                setTraversal(parents_from[index], False);
            }
            index++;
        }
        if (!XmIsGadget(from)) {
            setTraversal(parents_from[index], False);
        }
    }
  skip_disable:
    free(parents_from);
    free(parents_to);
}

/*
 * Class:     sun_awt_motif_MComponentPeer
 * Method:    requestFocus
 * Signature: (Ljava/awt/Component;ZZJ)Z
 */
JNIEXPORT jboolean JNICALL Java_sun_awt_motif_MComponentPeer__1requestFocus
(JNIEnv *env, jobject this, jobject lightweightChild, jboolean temporary,
     jboolean focusedWindowChangeAllowed, jlong time, jobject cause)
{
    struct ComponentData *bdata;
    Boolean result;
    jobject target;
    jint retval;
    Widget currentOwner = NULL;
    jobject curPeer = NULL;
    Widget shell;
    Widget widgetToFocus = NULL;

    AWT_LOCK();

    bdata = (struct ComponentData *)
        JNU_GetLongFieldAsPtr(env,this,mComponentPeerIDs.pData);
    if (bdata == NULL || bdata->widget == NULL) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        AWT_UNLOCK();
        return JNI_FALSE;
    }
    if ((*env)->EnsureLocalCapacity(env, 1) < 0) {
        AWT_UNLOCK();
        return JNI_FALSE;
    }

    target = (*env)->GetObjectField(env, this, mComponentPeerIDs.target);
    /* Don't need to free target explicitly. That will happen automatically
       when this function returns. */

    if (target == NULL) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        AWT_UNLOCK();
        return JNI_FALSE;
    }

    /* The X11 implementation does not permit cross-Window focus transfers,
       so always pass JNI_FALSE for that parameter. */
    retval = (*env)->CallStaticIntMethod
        (env, keyboardFocusManagerIDs.keyboardFocusManagerCls,
         keyboardFocusManagerIDs.shouldNativelyFocusHeavyweightMID,
         target, lightweightChild, temporary, JNI_FALSE, time, cause);

    if (retval == java_awt_KeyboardFocusManager_SNFH_SUCCESS_HANDLED) {
        AWT_UNLOCK();
        (*env)->DeleteLocalRef(env, target);
        return JNI_TRUE;
    }
    if (retval == java_awt_KeyboardFocusManager_SNFH_FAILURE) {
        AWT_UNLOCK();
        (*env)->DeleteLocalRef(env, target);
        return JNI_FALSE;
    }

    DASSERT(retval == java_awt_KeyboardFocusManager_SNFH_SUCCESS_PROCEED);

    shell = getShellWidget(bdata->widget);
    currentOwner = XmGetFocusWidget(shell);

    widgetToFocus = getFocusWidget(bdata->widget);

    globalClearFocusPath(shell);

    // Prepare widgets tree
    processTree(currentOwner, widgetToFocus, False);
    processTree(currentOwner, widgetToFocus, True);

    /*
      Fix for bug 4157017: replace XmProcessTraversal with
      XtSetKeyboardFocus because XmProcessTraversal does not allow
      focus to go to non-visible widgets.

      (There is a corresponding change to awt_MToolkit.c:dispatchToWidget)

      I found a last minute problem with this fix i.e. it broke the test
      case for bug 4053856. XmProcessTraversal does something else (that
      XtSetKeyboardFocus does not do) that stops this test case from
      failing. So, as I do not have time to investigate, and having
      both XmProcessTraversal and XtSetKeyboardFocus fixes 4157017 and
      4053856 and should be harmless (reviewer agreed), we have both
      below - XmProcessTraversal AND XtSetKeyboardFocus.
    */
    result = XmProcessTraversal(widgetToFocus, XmTRAVERSE_CURRENT);
    if (!result)
    {
        Widget w = widgetToFocus;

        shell = getShellWidget(w);
        XtSetKeyboardFocus(shell, w);
    }
    /* end 4157017 */

    // Because Motif focus callbacks are disabled we need to generate
    // the required events by ourselves.
    // First, check if the current focused widget has the entry in focus
    // list. If not, add it because it is required for further processing
    if (currentOwner != NULL) {
        jobject last = NULL;
        curPeer = findPeer(&currentOwner);
        if (curPeer == NULL) {
            currentOwner = findTopLevelByShell(currentOwner);
            if (currentOwner != NULL) {
                curPeer = findPeer(&currentOwner);
            }
        }
        if (curPeer != NULL) {
            curPeer = (*env)->GetObjectField(env, curPeer, mComponentPeerIDs.target);
            if (focusList == NULL) {
                awt_canvas_addToFocusListWithDuplicates(curPeer, JNI_TRUE);
            } else {
                last = (*env)->NewLocalRef(env, focusList->requestor);
                if (!(*env)->IsSameObject(env, last, curPeer)) {
                    awt_canvas_addToFocusList(curPeer);
                }
                if (!JNU_IsNull(env, last)) {
                    (*env)->DeleteLocalRef(env, last);
                }
            }
            (*env)->DeleteLocalRef(env, curPeer);
        }
    }
    awt_canvas_addToFocusList(target);

    // If new and current focus owners are the same do not generate FOCUS_LOST
    // event because we don't expect it, but generate FOCUS_GAIN because we
    // wait for it.
    if ( currentOwner != NULL && !JNU_IsNull(env, curPeer) &&
         !(*env)->IsSameObject(env, curPeer, target)) {
        callFocusHandler(currentOwner, FocusOut, cause);
    }
    callFocusHandler(widgetToFocus, FocusIn, cause);
    (*env)->DeleteLocalRef(env, target);

    AWT_FLUSH_UNLOCK();
    return JNI_TRUE;
}

Dimension
awt_computeIndicatorSize(struct FontData *fdata)
{
    int32_t height;
    int32_t acc;
    int32_t i;

    if (fdata == (struct FontData *) NULL)
        return MOTIF_XmINVALID_DIMENSION;

    /*
     * If Java font maps into single platform font - there's no
     * problem.  Let Motif use its usual calculations in this case.
     */
    if (fdata->charset_num == 1)
        return MOTIF_XmINVALID_DIMENSION;

    acc = 0;
    for (i = 0; i < fdata->charset_num; ++i) {
        XFontStruct *xfont = fdata->flist[i].xfont;
        acc += xfont->ascent + xfont->descent;
    }

    height = acc / fdata->charset_num;
    if (height < MOTIF_XmDEFAULT_INDICATOR_DIM)
        height = MOTIF_XmDEFAULT_INDICATOR_DIM;

    return height;
}

Dimension
awt_adjustIndicatorSizeForMenu(Dimension indSize)
{
    if (indSize == 0 || indSize == MOTIF_XmINVALID_DIMENSION)
        return MOTIF_XmINVALID_DIMENSION; /* let motif do the job */

    /* Indicators in menus are smaller.
       2/3 is a magic number from Motif internals. */
    indSize = indSize * 2 / 3;
    if (indSize < MOTIF_XmDEFAULT_INDICATOR_DIM)
        indSize = MOTIF_XmDEFAULT_INDICATOR_DIM;

    return indSize;
}

/*
 * Class:     sun_awt_motif_MComponentPeer
 * Method:    getWindow
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL Java_sun_awt_motif_MComponentPeer_getWindow
(JNIEnv *env, jobject this, jlong pData)
{
    jlong ret = (jlong)0;
    struct ComponentData* cdata;
    cdata = (struct ComponentData*)pData;
    AWT_LOCK();
    ret = (jlong)XtWindow(cdata->widget);
    AWT_FLUSH_UNLOCK();
    return ret;
}

/*
 * Class:     sun_awt_motif_MComponentPeer
 * Method:    restore_Focus
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_sun_awt_motif_MComponentPeer_restoreFocus
(JNIEnv *env, jobject this)
{
    jobject focus_peer;
    AWT_LOCK();

    focus_peer = awt_canvas_getFocusOwnerPeer();
    if (!JNU_IsNull(env, focus_peer)) {
        struct ComponentData *bdata;
        Boolean result;

        bdata = (struct ComponentData *)
            JNU_GetLongFieldAsPtr(env, focus_peer, mComponentPeerIDs.pData);
        if (bdata != NULL) {
            Widget widgetToFocus = getFocusWidget(bdata->widget);
            result = XmProcessTraversal(widgetToFocus, XmTRAVERSE_CURRENT);
            if (!result)
            {
                XtSetKeyboardFocus(getShellWidget(widgetToFocus), widgetToFocus);
            }
        }
    }
    (*env)->DeleteLocalRef(env, focus_peer);

    AWT_UNLOCK();
}

JNIEXPORT jboolean JNICALL
Java_sun_awt_motif_MComponentPeer_processSynchronousLightweightTransfer(
    JNIEnv * env, jclass cls, jobject heavyweight, jobject descendant,
    jboolean temporary, jboolean focusedWindowChangeAllowed, jlong time)
{
    return (*env)->CallStaticBooleanMethod(env, keyboardFocusManagerIDs.keyboardFocusManagerCls,
                                           keyboardFocusManagerIDs.processSynchronousTransferMID,
                                           heavyweight, descendant, temporary,
                                           focusedWindowChangeAllowed, time);
}
/*
 * Class:     sun_awt_motif_MComponentPeer
 * Method:    getNativeFocusedWindow
 * Signature: ()Ljava/awt/Window;
 */
JNIEXPORT jobject JNICALL
Java_sun_awt_motif_MComponentPeer_getNativeFocusedWindow
(JNIEnv *env, jclass cls)
{
    jobject l_peer;

    AWT_LOCK();
    l_peer = awt_canvas_getFocusedWindowPeer();
    AWT_UNLOCK();

    return (l_peer != NULL)
        ? (*env)->GetObjectField(env, l_peer, mComponentPeerIDs.target)
        : NULL;
}

/**
 * Makes sure that child has correct index inside parent
 * Note: there was a short time when we were counting index in the
 * opposite order when it seemed that X and Java z-order notions
 * are different. Now we know they are not: last component is
 * painted first and appears below all other components with
 * smaller indices.
 */
void ensureIndex(Widget parent, Widget child, int index) {
    WidgetList children;
    int32_t num_children;
    int32_t i;

    if (parent == NULL) {
        return;
    }
    if (child == NULL) {
        return;
    }
    XtVaGetValues(parent,
                  XmNnumChildren, &num_children,
                  XmNchildren, &children,
                  NULL);
    if (index < 0 || index >= num_children) {
        return;
    }
    if (children[index] != child) {
        for (i = 0; i < num_children; i++) {
            if (children[i] == child) {
                break;
            }
        }
        if (i < num_children) {
            Widget temp = children[index];
            children[index] = child;
            children[i] = temp;
        }
    }
}

JNIEXPORT void JNICALL
Java_sun_awt_motif_MPanelPeer_pEnsureIndex(JNIEnv * env, jobject this, jobject child, jint index) {
    struct ComponentData *cdata;
    Widget w_parent, w_child;
    AWT_LOCK();

    cdata = (struct ComponentData *)
        JNU_GetLongFieldAsPtr(env, this, mComponentPeerIDs.pData);
    w_parent = cdata->widget;

    cdata = (struct ComponentData *)
        JNU_GetLongFieldAsPtr(env, child, mComponentPeerIDs.pData);
    w_child = cdata->widget;
    ensureIndex(w_parent, w_child, index);
    AWT_UNLOCK();
}

JNIEXPORT void JNICALL
Java_sun_awt_motif_MPanelPeer_pRestack(JNIEnv * env, jobject this) {
    struct ComponentData *cdata;
    Widget w_parent;
    AWT_LOCK();

    cdata = (struct ComponentData *)
        JNU_GetLongFieldAsPtr(env, this, mComponentPeerIDs.pData);
    w_parent = cdata->widget;
    restack(w_parent);
    AWT_UNLOCK();
}
