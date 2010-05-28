/*
 * Copyright (c) 2003, 2005, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

// TODO: Propogate applicationActive from Java

#ifdef HEADLESS
    #error This file should not be included in headless library
#endif

#include "awt_p.h"

#include <X11/Xproto.h>
#include <X11/Xlib.h>
#include <X11/Xatom.h>
#include <Xm/MwmUtil.h>
#ifdef __linux__
#include <execinfo.h>
#endif
#include <stdio.h>
#include <stdlib.h>

/* JNI headers */
#include "java_awt_Frame.h"     /* for frame state constants */
#include "java_awt_event_KeyEvent.h"
#include "awt_wm.h"
#include "awt_util.h"           /* for X11 error handling macros */
#include "awt_xembed.h"
#include "awt_Component.h"
#include "awt_AWTEvent.h"
#include "canvas.h"
#include "sun_awt_motif_MEmbedCanvasPeer.h"

#ifdef DOTRACE
#define MTRACE(param) fprintf(stderr, param)
#define MTRACEP1(format, p1) fprintf(stderr, format, p1)
#define MTRACEP2(format, p1, p2) fprintf(stderr, format, p1, p2)
#define MTRACEP3(format, p1, p2, p3) fprintf(stderr, format, p1, p2, p3)
#define MTRACEP4(format, p1, p2, p3, p4) fprintf(stderr, format, p1, p2, p3, p4)
#define MTRACEP5(format, p1, p2, p3, p4, p5) fprintf(stderr, format, p1, p2, p3, p4, p5)
#define MTRACEP6(format, p1, p2, p3, p4, p5, p6) fprintf(stderr, format, p1, p2, p3, p4, p5, p6)
#define MTRACEP7(format, p1, p2, p3, p4, p5, p6, p7) fprintf(stderr, format, p1, p2, p3, p4, p5, p6, p7)
#else
#define MTRACE(param)
#define MTRACEP1(format, p1)
#define MTRACEP2(format, p1, p2)
#define MTRACEP3(format, p1, p2, p3)
#define MTRACEP4(format, p1, p2, p3, p4)
#define MTRACEP5(format, p1, p2, p3, p4, p5)
#define MTRACEP6(format, p1, p2, p3, p4, p5, p6)
#define MTRACEP7(format, p1, p2, p3, p4, p5, p6, p7)
#endif

/**************************** XEmbed server DnD support ***********************/
extern Atom XA_XdndAware;
extern Boolean
register_xembed_drop_site(JNIEnv* env, Display* dpy, jobject server,
                          Window serverHandle, Window clientHandle);
extern Boolean
unregister_xembed_drop_site(JNIEnv* env, Display* dpy, jobject server,
                            Window serverHandle, Window clientHandle);
extern void
forward_event_to_embedded(Window embedded, jlong ctxt, jint eventID);

extern const char * msg_to_str(int msg);

void
set_xembed_drop_target(JNIEnv* env, jobject server);
void
remove_xembed_drop_target(JNIEnv* env, jobject server);
Boolean
is_xembed_client(Window window);
/******************************************************************************/
extern struct MComponentPeerIDs mComponentPeerIDs;
static jobject createRectangle(JNIEnv* env, int x, int y, int width, int height);
static jobject createDimension(JNIEnv* env, int width, int height);
static void processXEmbedInfo(JNIEnv* env, jobject this);
static Atom XA_XEmbedInfo;
static Atom XA_XEmbed;
static jmethodID requestXEmbedFocusMID, focusNextMID, focusPrevMID,
    registerAcceleratorMID, unregisterAcceleratorMID,
    grabKeyMID, ungrabKeyMID, childResizedMID,
    setXEmbedDropTargetMID, removeXEmbedDropTargetMID;
static jfieldID keysymFID, modifiersFID, applicationActiveFID;

typedef struct _xembed_server_data {
    Window handle; // pointer to plugin intermediate widget, XEmbed client
    Window serverHandle;
    Widget serverWidget;
    Boolean dispatching; // whether we dispatch messages for handle
    int version;
    jobject server;
    struct _xembed_server_data * next;
} xembed_server_data, * pxembed_server_data;

static pxembed_server_data xembed_list = NULL;

static pxembed_server_data
getData(Window handle) {
    pxembed_server_data temp = xembed_list;
    while (temp != NULL) {
        if (temp->handle == handle) {
            return temp;
        }
        temp = temp->next;
    }
    return NULL;
}

static pxembed_server_data
getDataByEmbedder(jobject server) {
    JNIEnv      *env = (JNIEnv *)JNU_GetEnv(jvm, JNI_VERSION_1_2);
    pxembed_server_data temp = xembed_list;
    DASSERT(server != NULL);
    while (temp != NULL) {
        if ((*env)->IsSameObject(env, temp->server, server)) {
            return temp;
        }
        temp = temp->next;
    }
    return NULL;
}

static pxembed_server_data
getDataByServerHandle(Window serverHandle) {
    JNIEnv      *env = (JNIEnv *)JNU_GetEnv(jvm, JNI_VERSION_1_2);
    pxembed_server_data temp = xembed_list;
    Widget serverWidget = NULL;
    if (serverHandle == None) {
        return NULL;
    }
    serverWidget = XtWindowToWidget(awt_display, serverHandle);
    while (temp != NULL) {
        if (temp->serverHandle == serverHandle || temp->serverWidget == serverWidget) {
            temp->serverHandle = serverWidget;
            return temp;
        }
        temp = temp->next;
    }
    return NULL;
}

static pxembed_server_data
addData(jobject server) {
    JNIEnv      *env = (JNIEnv *)JNU_GetEnv(jvm, JNI_VERSION_1_2);
    struct ComponentData *cdata;
    xembed_server_data * data = malloc(sizeof(xembed_server_data));
    DASSERT(server != NULL);
    memset(data, 0, sizeof(xembed_server_data));
    data->server = server;
    cdata = (struct ComponentData *)
        JNU_GetLongFieldAsPtr(env, server, mComponentPeerIDs.pData);
    DASSERT(cdata != NULL);
    data->serverHandle = XtWindow(cdata->widget);
    data->serverWidget = cdata->widget;
    data->next = xembed_list;
    xembed_list = data;
    return data;
}

static void
removeData(jobject server) {
    JNIEnv      *env = (JNIEnv *)JNU_GetEnv(jvm, JNI_VERSION_1_2);
    pxembed_server_data * temp = &xembed_list;
    DASSERT(server != NULL);
    while (*temp != NULL) {
        if ((*env)->IsSameObject(env, (*temp)->server, server)) {
            xembed_server_data * data = *temp;
            *temp = (*temp)->next;
            DASSERT(data->server != NULL);
            (*env)->DeleteGlobalRef(env, data->server);
            free(data);
            return;
        }
        temp = &(*temp)->next;
    }
}

void
initXEmbedServerData() {
    JNIEnv      *env = (JNIEnv *)JNU_GetEnv(jvm, JNI_VERSION_1_2);
    jclass clazz;
    MTRACE("initXEmbedServerData\n");
    XA_XEmbedInfo = XInternAtom(awt_display, "_XEMBED_INFO", False);
    XA_XEmbed = XInternAtom(awt_display, "_XEMBED", False);

    clazz = (*env)->FindClass(env, "sun/awt/motif/MEmbedCanvasPeer");
    DASSERT(clazz != NULL);
    requestXEmbedFocusMID = (*env)->GetMethodID(env, clazz, "requestXEmbedFocus", "()V");
    DASSERT(requestXEmbedFocusMID != NULL);
    focusNextMID = (*env)->GetMethodID(env, clazz, "focusNext", "()V");
    DASSERT(focusNextMID != NULL);
    focusPrevMID = (*env)->GetMethodID(env, clazz, "focusPrev", "()V");
    DASSERT(focusPrevMID != NULL);
    registerAcceleratorMID = (*env)->GetMethodID(env, clazz, "registerAccelerator", "(JJJ)V");
    DASSERT(registerAcceleratorMID != NULL);
    unregisterAcceleratorMID = (*env)->GetMethodID(env, clazz, "unregisterAccelerator", "(J)V");
    DASSERT(unregisterAcceleratorMID != NULL);
    grabKeyMID = (*env)->GetMethodID(env, clazz, "grabKey", "(JJ)V");
    DASSERT(grabKeyMID != NULL);
    ungrabKeyMID = (*env)->GetMethodID(env, clazz, "ungrabKey", "(JJ)V");
    DASSERT(ungrabKeyMID != NULL);
    childResizedMID = (*env)->GetMethodID(env, clazz, "childResized", "()V");
    DASSERT(childResizedMID != NULL);
    setXEmbedDropTargetMID =
        (*env)->GetMethodID(env, clazz, "setXEmbedDropTarget", "()V");
    DASSERT(setXEmbedDropTargetMID != NULL);
    removeXEmbedDropTargetMID =
        (*env)->GetMethodID(env, clazz, "removeXEmbedDropTarget", "()V");
    DASSERT(removeXEmbedDropTargetMID != NULL);

    applicationActiveFID = (*env)->GetFieldID(env, clazz, "applicationActive", "Z");
    DASSERT(applicationActiveFID != NULL);
    (*env)->DeleteLocalRef(env, clazz);

    clazz = (*env)->FindClass(env, "sun/awt/motif/GrabbedKey");
    DASSERT(clazz != NULL);
    keysymFID = (*env)->GetFieldID(env, clazz, "keysym", "J");
    DASSERT(keysymFID != NULL);
    modifiersFID = (*env)->GetFieldID(env, clazz, "modifiers", "J");
    DASSERT(modifiersFID != NULL);
    (*env)->DeleteLocalRef(env, clazz);
}

/*
 * Class:     sun_awt_motif_MEmbedCanvasPeer
 * Method:    initXEmbedServer
 * Signature: ()V
 */
JNIEXPORT void JNICALL
Java_sun_awt_motif_MEmbedCanvasPeer_initXEmbedServer(JNIEnv *env, jobject this) {
    struct ComponentData *cdata;
    AWT_LOCK();
    MTRACE("initXEmbedServer\n");
    addData((*env)->NewGlobalRef(env, this));
    if (XA_XEmbedInfo == None) {
        initXEmbedServerData();
    }
    cdata = (struct ComponentData *)
        JNU_GetLongFieldAsPtr(env, this, mComponentPeerIDs.pData);
/*     XSelectInput(awt_display, XtWindow(cdata->widget), SubstructureNotifyMask); */
    XtAddEventHandler(cdata->widget,
                      SubstructureNotifyMask,
                      False, null_event_handler, NULL);
    AWT_UNLOCK();
}

/*
 * Class:     sun_awt_motif_MEmbedCanvasPeer
 * Method:    destroyXEmbedServer
 * Signature: ()V
 */
JNIEXPORT void JNICALL
Java_sun_awt_motif_MEmbedCanvasPeer_destroyXEmbedServer(JNIEnv *env, jobject this) {
    AWT_LOCK();
    MTRACE("destroyXEmbedServer\n");
    removeData(this);
    AWT_UNLOCK();
}

/*
 * Class:     sun_awt_motif_MEmbedCanvasPeer
 * Method:    isXEmbedActive
 * Signature: ()Z
 */
JNIEXPORT jboolean JNICALL
Java_sun_awt_motif_MEmbedCanvasPeer_isXEmbedActive(JNIEnv *env, jobject this) {
    pxembed_server_data sdata;
    jboolean res = JNI_FALSE;
    AWT_LOCK();
    sdata = getDataByEmbedder(this);
    if (sdata != NULL) {
        res = (sdata->handle != None)?JNI_TRUE:JNI_FALSE;
    }
    AWT_UNLOCK();
    return res;
}

/*
 * Class:     sun_awt_motif_MEmbedCanvasPeer
 * Method:    initDispatching
 * Signature: ()V
 */
JNIEXPORT void JNICALL
Java_sun_awt_motif_MEmbedCanvasPeer_initDispatching (JNIEnv *env, jobject this) {
    pxembed_server_data sdata;
    AWT_LOCK();
    MTRACE("initDispatching\n");
    sdata = getDataByEmbedder(this);
    if (sdata != NULL) {
        XSelectInput(awt_display, sdata->handle, StructureNotifyMask | PropertyChangeMask);
        sdata->dispatching = True;
        register_xembed_drop_site(env, awt_display, sdata->server,
                                  sdata->serverHandle, sdata->handle);
    }
    processXEmbedInfo(env, this);
    Java_sun_awt_motif_MEmbedCanvasPeer_notifyChildEmbedded(env, this);
    AWT_UNLOCK();
}

/*
 * Class:     sun_awt_motif_MEmbedCanvasPeer
 * Method:    endDispatching
 * Signature: ()V
 */
JNIEXPORT void JNICALL
Java_sun_awt_motif_MEmbedCanvasPeer_endDispatching (JNIEnv *env, jobject this) {
    pxembed_server_data sdata;
    AWT_LOCK();
    MTRACE("endDispatching\n");
    sdata = getDataByEmbedder(this);
    if (sdata != NULL) {
        unregister_xembed_drop_site(env, awt_display, sdata->server,
                                    sdata->serverHandle, sdata->handle);
        sdata->dispatching = False;
    }
    AWT_UNLOCK();
}

/*
 * Class:     sun_awt_motif_MEmbedCanvasPeer
 * Method:    embedChild
 * Signature: (J)V
 */
JNIEXPORT void JNICALL
Java_sun_awt_motif_MEmbedCanvasPeer_embedChild (JNIEnv * env, jobject this, jlong handle) {
    pxembed_server_data sdata;
    AWT_LOCK();
    MTRACE("embedChild\n");
    sdata = getDataByEmbedder(this);
    if (sdata != NULL) {
        if (sdata->handle != None) {
            Java_sun_awt_motif_MEmbedCanvasPeer_detachChild(env, this);
        }
        sdata->handle = (Window)handle;
        Java_sun_awt_motif_MEmbedCanvasPeer_initDispatching(env, this);
    }
    AWT_UNLOCK();
}

/*
 * Class:     sun_awt_motif_MEmbedCanvasPeer
 * Method:    childDestroyed
 * Signature: ()V
 */
JNIEXPORT void JNICALL
Java_sun_awt_motif_MEmbedCanvasPeer_childDestroyed (JNIEnv *env, jobject this) {
    pxembed_server_data sdata;
    AWT_LOCK();
    MTRACE("childDestroyed\n");
    Java_sun_awt_motif_MEmbedCanvasPeer_endDispatching(env, this);
    sdata = getDataByEmbedder(this);
    if (sdata != NULL) {
        sdata->handle = None;
    }
    AWT_UNLOCK();
}

/*
 * Class:     sun_awt_motif_MEmbedCanvasPeer
 * Method:    getEmbedPreferredSize
 * Signature: ()Ljava/awt/Dimension;
 */
JNIEXPORT jobject JNICALL
Java_sun_awt_motif_MEmbedCanvasPeer_getEmbedPreferredSize (JNIEnv *env, jobject this) {
    pxembed_server_data sdata;
    jobject res = NULL;
    XSizeHints * hints;
    long dummy;
    AWT_LOCK();
    MTRACE("getPreferredSize\n");
    sdata = getDataByEmbedder(this);
    if (sdata != NULL) {
        hints = XAllocSizeHints();
        DASSERT(hints != NULL);
        DASSERT(sdata->handle != None);
        if (XGetWMNormalHints(awt_display, sdata->handle, hints, &dummy) == Success) {
            res = createDimension(env, hints->width, hints->height);
        }
        XFree(hints);
    }
    AWT_UNLOCK();
    return res;
}

/*
 * Class:     sun_awt_motif_MEmbedCanvasPeer
 * Method:    getEmbedMinimumSize
 * Signature: ()Ljava/awt/Dimension;
 */
JNIEXPORT jobject JNICALL
Java_sun_awt_motif_MEmbedCanvasPeer_getEmbedMinimumSize (JNIEnv *env, jobject this) {
    pxembed_server_data sdata;
    jobject res = NULL;
    XSizeHints * hints;
    long dummy;
    AWT_LOCK();
    MTRACE("getMinimumSize\n");
    sdata = getDataByEmbedder(this);
    if (sdata != NULL) {
        hints = XAllocSizeHints();
        DASSERT(hints != NULL);
        DASSERT(sdata->handle != None);
        if (XGetWMNormalHints(awt_display, sdata->handle, hints, &dummy) == Success) {
            res = createDimension(env, hints->min_width, hints->min_height);
        }
        XFree(hints);
    }
    AWT_UNLOCK();
    return res;
}

/*
 * Class:     sun_awt_motif_MEmbedCanvasPeer
 * Method:    getClientBounds
 * Signature: ()Ljava/awt/Rectangle;
 */
JNIEXPORT jobject JNICALL
Java_sun_awt_motif_MEmbedCanvasPeer_getClientBounds (JNIEnv *env, jobject this) {
    pxembed_server_data sdata;
    jobject res = NULL;
    AWT_LOCK();
    MTRACE("getClientBounds\n");
    sdata = getDataByEmbedder(this);
    if (sdata != NULL) {
        XWindowAttributes attrs;
        DASSERT(sdata->handle != None);
        if (XGetWindowAttributes(awt_display, sdata->handle, &attrs) == Success) {
            res = createRectangle(env, attrs.x, attrs.y, attrs.width, attrs.height);
        }
    }
    AWT_UNLOCK();
    return res;
}

Boolean
isApplicationActive(JNIEnv * env, jobject this) {
    return (*env)->GetBooleanField(env, this, applicationActiveFID);
}

/*
 * Class:     sun_awt_motif_MEmbedCanvasPeer
 * Method:    notifyChildEmbedded
 * Signature: ()V
 */
JNIEXPORT void JNICALL
Java_sun_awt_motif_MEmbedCanvasPeer_notifyChildEmbedded (JNIEnv *env, jobject this) {
    struct ComponentData *cdata;
    pxembed_server_data sdata;
    AWT_LOCK();
    MTRACE("notifyChildEmbedded\n");
    cdata = (struct ComponentData *)
        JNU_GetLongFieldAsPtr(env, this, mComponentPeerIDs.pData);
    sdata = getDataByEmbedder(this);
    if (sdata != NULL) {
        DASSERT(sdata->handle != None);
        DASSERT(cdata != NULL);
        DASSERT(XtWindow(cdata->widget) != None);
        sendMessageHelper(sdata->handle, XEMBED_EMBEDDED_NOTIFY, XtWindow(cdata->widget), min(sdata->version, XEMBED_VERSION), 0);
        if (isApplicationActive(env, this)) {
            sendMessage(sdata->handle, XEMBED_WINDOW_ACTIVATE);
        }
    }
    AWT_UNLOCK();
}

/*
 * Class:     sun_awt_motif_MEmbedCanvasPeer
 * Method:    detachChild
 * Signature: ()V
 */
JNIEXPORT void JNICALL
Java_sun_awt_motif_MEmbedCanvasPeer_detachChild (JNIEnv *env, jobject this) {
    pxembed_server_data sdata;
    AWT_LOCK();
    MTRACE("detachChild\n");
    sdata = getDataByEmbedder(this);
    if (sdata != NULL) {
        /**
         *  XEmbed specification:
         *  "The embedder can unmap the client and reparent the client window to the root window. If the
         *  client receives an ReparentNotify event, it should check the parent field of the XReparentEvent
         *  structure. If this is the root window of the window's screen, then the protocol is finished and
         *  there is no further interaction. If it is a window other than the root window, then the protocol
         *  continues with the new parent acting as the embedder window."
         */
        DASSERT(sdata->handle != None);
        XUnmapWindow(awt_display, sdata->handle);
        XReparentWindow(awt_display, sdata->handle, DefaultRootWindow(awt_display), 0, 0);
        Java_sun_awt_motif_MEmbedCanvasPeer_endDispatching(env, this);
        sdata->handle = None;
    }
    AWT_UNLOCK();
}

/*
 * Class:     sun_awt_motif_MEmbedCanvasPeer
 * Method:    forwardKeyEvent
 * Signature: (Ljava/awt/event/KeyEvent;)V
 */
JNIEXPORT void JNICALL
Java_sun_awt_motif_MEmbedCanvasPeer_forwardKeyEvent (JNIEnv *env, jobject this, jobject event) {
    pxembed_server_data sdata;
    jbyteArray array;
    XEvent *xevent;
    AWT_LOCK();
    MTRACE("forwardKeyEvent\n");
    sdata = getDataByEmbedder(this);
    if (sdata != NULL) {
        DASSERT(sdata->handle != None);
        array = (jbyteArray)(*env)->GetObjectField(env, event, awtEventIDs.bdata);
        if (array == NULL) {
            MTRACE("array is null\n");
            AWT_UNLOCK();
            return;
        }

        xevent = (XEvent *)(*env)->GetByteArrayElements(env, array, NULL);
        if (xevent == NULL) {
            (*env)->DeleteLocalRef(env, array);
            MTRACE("xevent is null\n");
            AWT_UNLOCK();
            return;
        }
        xevent->xany.window = sdata->handle;
        XSendEvent(awt_display, sdata->handle, False, NoEventMask, xevent);
        (*env)->DeleteLocalRef(env, array);
    }
    AWT_UNLOCK();
}

/*
 * Class:     sun_awt_motif_MEmbedCanvasPeer
 * Method:    getAWTKeyCodeForKeySym
 * Signature: (I)I
 */
JNIEXPORT jint JNICALL
Java_sun_awt_motif_MEmbedCanvasPeer_getAWTKeyCodeForKeySym (JNIEnv *env, jobject this, jint keysym) {
    jint keycode = java_awt_event_KeyEvent_VK_UNDEFINED;
    Boolean mapsToUnicodeChar;
    jint keyLocation;
    keysymToAWTKeyCode(keysym, &keycode, &mapsToUnicodeChar, &keyLocation);
    return keycode;
}

/*
 * Class:     sun_awt_motif_MEmbedCanvasPeer
 * Method:    sendMessage
 * Signature: (I)V
 */
JNIEXPORT void JNICALL
Java_sun_awt_motif_MEmbedCanvasPeer_sendMessage__I (JNIEnv *env, jobject this, jint msg) {
    pxembed_server_data sdata;
    AWT_LOCK();
    MTRACEP2("sendMessage %d(%s)\n", msg, msg_to_str(msg));
    sdata = getDataByEmbedder(this);
    if (sdata != NULL) {
        DASSERT(sdata->handle != None);
        sendMessage(sdata->handle, msg);
    }
    AWT_UNLOCK();
}

/*
 * Class:     sun_awt_motif_MEmbedCanvasPeer
 * Method:    sendMessage
 * Signature: (IJJJ)V
 */
JNIEXPORT void JNICALL
Java_sun_awt_motif_MEmbedCanvasPeer_sendMessage__IJJJ (JNIEnv *env, jobject this, jint msg, jlong detail, jlong data1, jlong data2) {
    pxembed_server_data sdata;
    AWT_LOCK();
    MTRACEP5("sendMessage2 msg %d(%s) detail %d data: %d %d\n", msg, msg_to_str(msg), detail, data1, data2);
    sdata = getDataByEmbedder(this);
    if (sdata != NULL) {
        DASSERT(sdata->handle != None);
        sendMessageHelper(sdata->handle, msg, detail, data1, data2);
    }
    AWT_UNLOCK();
}

static jobject
createRectangle(JNIEnv* env, int x, int y, int width, int height) {
    static jclass clazz;
    static jmethodID mid;
    jobject rect = NULL;
    if (mid == 0) {
        jclass l_clazz = (*env)->FindClass(env, "java/awt/Rectangle");
        DASSERT(l_clazz != NULL);
        mid = (*env)->GetMethodID(env, clazz, "<init>", "(IIII)V");
        DASSERT(mid != NULL);
        clazz = (*env)->NewGlobalRef(env, l_clazz);
        (*env)->DeleteLocalRef(env, l_clazz);
    }
    if (mid != NULL) {
        rect = (*env)->NewObject(env, clazz, mid, x, y, width, height);
        if ((*env)->ExceptionOccurred(env)) {
            return NULL;
        }
    }
    return rect;
}

static jobject
createDimension(JNIEnv* env, int width, int height) {
    static jclass clazz;
    static jmethodID mid;
    jobject dim = NULL;
    if (mid == 0) {
        jclass l_clazz = (*env)->FindClass(env, "java/awt/Dimension");
        DASSERT(l_clazz != NULL);
        mid = (*env)->GetMethodID(env, clazz, "<init>", "(II)V");
        DASSERT(mid != NULL);
        clazz = (*env)->NewGlobalRef(env, l_clazz);
        (*env)->DeleteLocalRef(env, l_clazz);
    }
    if (mid != NULL) {
        dim = (*env)->NewObject(env, clazz, mid, width, height);
        if ((*env)->ExceptionOccurred(env)) {
            return NULL;
        }
    }
    return dim;
}

Boolean isMapped(Window w) {
    XWindowAttributes attr;
    Status status = 0;
    WITH_XERROR_HANDLER(xerror_ignore_bad_window);
    status = XGetWindowAttributes(awt_display, w, &attr);
    RESTORE_XERROR_HANDLER;
    if (status == 0 || xerror_code != Success) {
        return False;
    }
    return !(attr.map_state == IsUnmapped);
}

static void
processXEmbedInfo(JNIEnv * env, jobject this) {
    pxembed_server_data sdata;
    AWT_LOCK();
    MTRACE("processXEmbedInfo\n");
    sdata = getDataByEmbedder(this);
    if (Java_sun_awt_motif_MEmbedCanvasPeer_isXEmbedActive(env, this)) {
        Atom actual_type;
        int actual_format;
        unsigned long nitems;
        unsigned long bytes_after;
        CARD32 * data = NULL;
        DASSERT(sdata->handle != None);
        if (XGetWindowProperty(awt_display, sdata->handle, XA_XEmbedInfo,
                           0, 2, False, XA_XEmbedInfo, &actual_type,
                           &actual_format, &nitems, &bytes_after,
                               (unsigned char**)&data) != Success)
        {
            AWT_UNLOCK();
            return;
        }
        if (actual_type == XA_XEmbedInfo && actual_format == 32
            && nitems == 2)
        {
            CARD32 flags;
            Boolean new_mapped, currently_mapped;
            sdata->version = *data;
            flags = *(data+1);
            new_mapped = (flags & XEMBED_MAPPED) != 0;
            currently_mapped = isMapped(sdata->handle);
            if (new_mapped != currently_mapped) {
                if (new_mapped) {
                    XMapWindow(awt_display, sdata->handle);
                } else {
                    XUnmapWindow(awt_display, sdata->handle);
                }
            }
        }
        if (data != NULL) {
            XFree(data);
        }
    }
    AWT_UNLOCK();
}

/**
 * Handles client message on embedder
 */
static void
handleClientMessage(JNIEnv* env, jobject this, XClientMessageEvent * ev) {
    pxembed_server_data sdata;
    AWT_LOCK();
    MTRACEP5("handleClientMessage: 0=%ld 1=%ld 2=%ld 3=%ld 4=%ld\n",
            ev->data.l[0], ev->data.l[1], ev->data.l[2], ev->data.l[3], ev->data.l[4]);
    sdata = getDataByEmbedder(this);
    if (sdata != NULL && sdata->handle != None) {
        switch ((int)ev->data.l[1]) {
          case XEMBED_REQUEST_FOCUS:
              MTRACE("REQUEST_FOCUS\n");
              (*env)->CallVoidMethod(env, this, requestXEmbedFocusMID);
              break;
          case XEMBED_FOCUS_NEXT:
              MTRACE("FOCUS_NEXT\n");
              (*env)->CallVoidMethod(env, this, focusNextMID);
              break;
          case XEMBED_FOCUS_PREV:
              MTRACE("FOCUS_PREV\n");
              (*env)->CallVoidMethod(env, this, focusPrevMID);
              break;
          case XEMBED_REGISTER_ACCELERATOR:
              MTRACE("REGISTER_ACCEL\n");
              (*env)->CallVoidMethod(env, this, registerAcceleratorMID,
                                     (jlong)ev->data.l[2],
                                     (jlong)ev->data.l[3],
                                     (jlong)ev->data.l[4]);
              break;
          case XEMBED_UNREGISTER_ACCELERATOR:
              MTRACE("UNREGISTER_ACCEL\n");
              (*env)->CallVoidMethod(env, this, unregisterAcceleratorMID,
                                     (jlong)ev->data.l[2]);
              break;
          case NON_STANDARD_XEMBED_GTK_GRAB_KEY:
              MTRACE("GRAB_KEY\n");
              (*env)->CallVoidMethod(env, this, grabKeyMID,
                                     (jlong)ev->data.l[3],
                                     (jlong)ev->data.l[4]);
              break;
          case NON_STANDARD_XEMBED_GTK_UNGRAB_KEY:
              MTRACE("UNGRAB_KEY\n");
              (*env)->CallVoidMethod(env, this, ungrabKeyMID,
                                     (jlong)ev->data.l[3],
                                     (jlong)ev->data.l[4]);
          case _SUN_XEMBED_START:
              MTRACE("XEMBED_START\n");
              processXEmbedInfo(env, this);
              Java_sun_awt_motif_MEmbedCanvasPeer_notifyChildEmbedded(env, this);
              break;
        }
    }
    AWT_UNLOCK();
}

/**
 * Handles property changes on xembed client
 */
static void
handlePropertyNotify(XPropertyEvent * ev) {
    JNIEnv      *env = (JNIEnv *)JNU_GetEnv(jvm, JNI_VERSION_1_2);
    pxembed_server_data sdata;
    AWT_LOCK();
    MTRACE("handlePropertyNotify\n");
    sdata = getData(ev->window);
    if (sdata != NULL) {
        if (ev->atom == XA_WM_NORMAL_HINTS) {
            DASSERT(sdata->server != NULL);
            (*env)->CallVoidMethod(env, sdata->server, childResizedMID);
            MTRACE("NORMAL_HINTS have changed\n");
        } else if (ev->atom == XA_XdndAware) {
            unregister_xembed_drop_site(env, awt_display, sdata->server,
                                        sdata->serverHandle, sdata->handle);
            if (ev->state == PropertyNewValue) {
                register_xembed_drop_site(env, awt_display, sdata->server,
                                          sdata->serverHandle, sdata->handle);
            }
        } else if (ev->atom == XA_XEmbedInfo) {
            DASSERT(sdata->server != NULL);
            MTRACE("XEMBED_INFO has changed\n");
            processXEmbedInfo(env, sdata->server);
        }
    }
    AWT_UNLOCK();
}

static void
handleConfigureNotify(XConfigureEvent * ev) {
    JNIEnv      *env = (JNIEnv *)JNU_GetEnv(jvm, JNI_VERSION_1_2);
    pxembed_server_data sdata;
    AWT_LOCK();
    MTRACE("handleConfigureNotify\n");
    sdata = getData(ev->window);
    if (sdata != NULL) {
        DASSERT(sdata->server != NULL);
        (*env)->CallVoidMethod(env, sdata->server, childResizedMID);
    }
    AWT_UNLOCK();
}

/*
 * Class:     sun_awt_motif_MEmbedCanvasPeer
 * Method:    sendMessage
 * Signature: (IJJJ)V
 */
JNIEXPORT void JNICALL
Java_sun_awt_motif_GrabbedKey_initKeySymAndModifiers (JNIEnv *env, jobject this, jobject keyevent) {
    jbyteArray array;
    XEvent *xevent;
    int keysym, modifiers;
    int keycode;
    AWT_LOCK();
    array = (jbyteArray)(*env)->GetObjectField(env, keyevent, awtEventIDs.bdata);
    if (array == NULL) {
        AWT_UNLOCK();
        return;
    }
    xevent = (XEvent *)(*env)->GetByteArrayElements(env, array, NULL);
    if (xevent == NULL) {
        (*env)->DeleteLocalRef(env, array);
        AWT_UNLOCK();
        return;
    }
    keycode = (*env)->GetIntField(env, keyevent, keyEventIDs.keyCode);
    keysym = awt_getX11KeySym(keycode);
    modifiers = xevent->xkey.state;
    (*env)->SetLongField(env, this, keysymFID, (jlong)keysym);
    (*env)->SetLongField(env, this, modifiersFID, (jlong)modifiers);
    (*env)->DeleteLocalRef(env, array);
    AWT_UNLOCK();
}

#ifdef __linux__
void
print_stack (void)
{
  void *array[10];
  size_t size;
  char **strings;
  size_t i;

  size = backtrace (array, 10);
  strings = backtrace_symbols (array, size);

  fprintf (stderr, "Obtained %zd stack frames.\n", size);

  for (i = 0; i < size; i++)
     fprintf (stderr, "%s\n", strings[i]);

  free (strings);
}
#endif

extern int32_t  numEventsHandled;

XCreateWindowEvent cr;

void
dispatchEmbedderEvent(jobject server, XEvent * ev) {
    JNIEnv      *env = (JNIEnv *)JNU_GetEnv(jvm, JNI_VERSION_1_2);
    DASSERT(server != NULL);
    DASSERT(ev != NULL);
    AWT_LOCK();
/*     MTRACE("dispatchEmebddedEvent\n"); */
    switch (ev->type) {
      case CreateNotify:

          MTRACEP3("CreateNotify for %x, serial %d, num events %d\n", (ev->xcreatewindow.window), (ev->xany.serial), (numEventsHandled));
          Java_sun_awt_motif_MEmbedCanvasPeer_embedChild(env, server, ev->xcreatewindow.window);
          break;
      case DestroyNotify:
          MTRACE("DestroyNotify\n");
          Java_sun_awt_motif_MEmbedCanvasPeer_childDestroyed(env, server);
          break;
      case ReparentNotify:
          MTRACEP2("ReparentNotify for %x, parent %x\n", (ev->xreparent.window), (ev->xreparent.parent));
          Java_sun_awt_motif_MEmbedCanvasPeer_embedChild(env, server, ev->xreparent.window);
          break;
      case ClientMessage:
          MTRACE("ClientMessage\n");
          handleClientMessage(env, server, &ev->xclient);
          break;
    }
    AWT_UNLOCK();
}

void
dispatchEmbeddingClientEvent(XEvent * ev) {
    DASSERT(ev != NULL);
    MTRACE("dispatchEmbeddingClientEvent\n");
    switch (ev->type) {
      case PropertyNotify:
          handlePropertyNotify(&ev->xproperty);
          break;
      case ConfigureNotify:
          handleConfigureNotify(&ev->xconfigure);
          break;
    }
}

void
xembed_serverEventHandler(XEvent * ev) {
    pxembed_server_data sdata;
    sdata = getData(ev->xany.window);
    if (sdata != NULL) { // Event on client
        dispatchEmbeddingClientEvent(ev);
    } else {
        sdata = getDataByServerHandle(ev->xany.window);
        if (sdata != NULL) {
            DASSERT(sdata->server != NULL);
            dispatchEmbedderEvent(sdata->server, ev);
        }
    }
}

/**************************** XEmbed server DnD support ***********************/
void
set_xembed_drop_target(JNIEnv* env, jobject server) {

    (*env)->CallVoidMethod(env, server, setXEmbedDropTargetMID);
}

void
remove_xembed_drop_target(JNIEnv* env, jobject server) {
    (*env)->CallVoidMethod(env, server, removeXEmbedDropTargetMID);
}

Boolean
is_xembed_client(Window window) {
    return getData(window) != NULL;
}
/******************************************************************************/

/*
 * Class:     sun_awt_motif_MEmbedCanvasPeer
 * Method:    getWindow
 * Signature: ()V
 */
JNIEXPORT jlong JNICALL
Java_sun_awt_motif_MEmbedCanvasPeer_getWindow(JNIEnv *env, jobject this) {
    struct ComponentData *cdata;
    Window res = None;
    AWT_LOCK();
    cdata = (struct ComponentData *)
        JNU_GetLongFieldAsPtr(env, this, mComponentPeerIDs.pData);
    DASSERT(cdata != NULL);
    res = XtWindow(cdata->widget);
    AWT_UNLOCK();
    return (jlong)res;
}

JNIEXPORT void JNICALL
Java_sun_awt_motif_MEmbedCanvasPeer_forwardEventToEmbedded(JNIEnv *env,
                                                           jobject this,
                                                           jlong ctxt,
                                                           jint eventID){
    pxembed_server_data sdata;
    AWT_LOCK();
    sdata = getDataByEmbedder(this);
    if (sdata != NULL) {
        forward_event_to_embedded(sdata->handle, ctxt, eventID);
    }
    AWT_UNLOCK();
}
