/*
 * Copyright 2003-2005 Sun Microsystems, Inc.  All Rights Reserved.
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

#include <X11/Xproto.h>
#include <X11/Xlib.h>
#include <X11/Xatom.h>
#include <Xm/MwmUtil.h>

/* JNI headers */
#include "java_awt_Frame.h"     /* for frame state constants */

#include "awt_wm.h"
#include "awt_util.h"           /* for X11 error handling macros */
#include "awt_xembed.h"
#include "awt_MToolkit.h"
#include "awt_DataTransferer.h"   /* for DECLARE_XXX macros */

#ifdef DOTRACE
#define MTRACE(param) fprintf(myerr, param)
#define MTRACEP1(format, p1) fprintf(myerr, format, p1)
#define MTRACEP2(format, p1, p2) fprintf(myerr, format, p1, p2)
#define MTRACEP3(format, p1, p2, p3) fprintf(myerr, format, p1, p2, p3)
#define MTRACEP4(format, p1, p2, p3, p4) fprintf(myerr, format, p1, p2, p3, p4)
#define MTRACEP5(format, p1, p2, p3, p4, p5) fprintf(myerr, format, p1, p2, p3, p4, p5)
#define MTRACEP6(format, p1, p2, p3, p4, p5, p6) fprintf(myerr, format, p1, p2, p3, p4, p5, p6)
#define MTRACEP7(format, p1, p2, p3, p4, p5, p6, p7) fprintf(myerr, format, p1, p2, p3, p4, p5, p6, p7)
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

#ifdef DOTRACE
static FILE* myerr;
#endif

static Window getParent(Window window);
static Window getEmbedder(Window client);
static jmethodID handleFocusInMID;

const char * error_msg = "UNKNOWN XEMBED MESSAGE";

const char * xembed_strs[] = {
    "EMBEDDED_NOTIFY",
    "WINDOW_ACTIVATE",
    "WINDOW_DEACTIVATE",
    "REQUEST_FOCUS",
    "FOCUS_IN",
    "FOCUS_OUT",
    "FOCUS_NEXT",
    "FOCUS_PREV" ,
    "GRAB_KEY",
    "UNGRAB_KEY",
    "MODALITY_ON" ,
    "MODALITY_OFF",
    "REGISTER_ACCELERATOR",
    "UNREGISTER_ACCELERATOR",
    "ACTIVATE_ACCELERATOR"
};

const char *
msg_to_str(int msg) {
    if (msg >= 0 && msg <= XEMBED_LAST_MSG) {
        return xembed_strs[msg];
    } else {
        return error_msg;
    }
}

DECLARE_JAVA_CLASS(MEmbeddedFramePeerClass, "sun/awt/motif/MEmbeddedFramePeer");

typedef struct _xembed_info {
    CARD32 version;
    CARD32 flags;
} xembed_info;

typedef struct _xembed_data {
    struct FrameData * wdata; // pointer to EmbeddedFrame wdata
    Window client; // pointer to plugin intermediate widget, XEmbed client
    Boolean active; // whether xembed is active for this client
    Boolean applicationActive; // whether the embedding application is active
    Window embedder; // Window ID of the embedder
    struct _xembed_data * next;
} xembed_data, * pxembed_data;

static pxembed_data xembed_list = NULL;

static pxembed_data
getData(Window client) {
    pxembed_data temp = xembed_list;
    while (temp != NULL) {
        if (temp->client == client) {
            return temp;
        }
        temp = temp->next;
    }
    return NULL;
}

static pxembed_data
getDataByFrame(struct FrameData* wdata) {
    pxembed_data temp = xembed_list;
    while (temp != NULL) {
        if (temp->wdata == wdata) {
            return temp;
        }
        temp = temp->next;
    }
    return NULL;
}

static pxembed_data
addData(Window client) {
    xembed_data * data = malloc(sizeof(xembed_data));
    memset(data, 0, sizeof(xembed_data));
    data->client = client;
    data->next = xembed_list;
    xembed_list = data;
    return data;
}

static void
removeData(Window client) {
    pxembed_data * temp = &xembed_list;
    while (*temp != NULL) {
        if ((*temp)->client == client) {
            xembed_data * data = *temp;
            *temp = (*temp)->next;
            free(data);
            return;
        }
        temp = &(*temp)->next;
    }
}

static Atom XA_XEmbedInfo;
static Atom XA_XEmbed;

void
init_xembed() {
    XA_XEmbedInfo = XInternAtom(awt_display, "_XEMBED_INFO", False);
    XA_XEmbed = XInternAtom(awt_display, "_XEMBED", False);
#ifdef DOTRACE
    myerr = fopen("xembedclient.log","w");
#endif
}

static Time
getCurrentServerTime() {
    return awt_util_getCurrentServerTime();
}


void
sendMessageHelper(Window window, int message, long detail,
                              long data1, long data2)
{
    JNIEnv      *env = (JNIEnv *)JNU_GetEnv(jvm, JNI_VERSION_1_2);
    XEvent ev;
    XClientMessageEvent * req = (XClientMessageEvent*)&ev;
    memset(&ev, 0, sizeof(ev));

    req->type = ClientMessage;
    req->window = window;
    req->message_type = XA_XEmbed;
    req->format = 32;
    req->data.l[0] = getCurrentServerTime();
    req->data.l[1] = message;
    req->data.l[2] = detail;
    req->data.l[3] = data1;
    req->data.l[4] = data2;
    AWT_LOCK();
    XSendEvent(awt_display, window, False, NoEventMask, &ev);
    AWT_UNLOCK();
}

void
sendMessage(Window window, int message) {
    sendMessageHelper(window, message, 0, 0, 0);
}


static Window
getParent(Window window) {
    Window root, parent = None, *children = NULL;
    unsigned int count;
    XQueryTree(awt_display, window, &root, &parent, &children, &count);
    if (children != NULL) {
        XFree(children);
    }
    return parent;
}

static Window
getEmbedder(Window client) {
    return getParent(client);
}


static void
handleFocusIn(struct FrameData* wdata, int detail) {
    JNIEnv      *env = (JNIEnv *)JNU_GetEnv(jvm, JNI_VERSION_1_2);
    struct WidgetInfo* winfo;
    MTRACE("HandleFocusIn\n");
    winfo = findWidgetInfo(wdata->winData.comp.widget);
    if (winfo != NULL) {
        jobject peer = winfo->peer;
        if (handleFocusInMID == NULL) {
            jclass clazz = (*env)->FindClass(env, "sun/awt/motif/MEmbeddedFramePeer");
            DASSERT(clazz != NULL);
            handleFocusInMID = (*env)->GetMethodID(env, clazz, "handleFocusIn", "(I)V");
            DASSERT(handleFocusInMID != NULL);
            if (clazz != NULL) {
                (*env)->DeleteLocalRef(env, clazz);
            }
        }
        if (handleFocusInMID != NULL) {
            (*env)->CallVoidMethod(env, peer, handleFocusInMID, (jint)detail);
        }
    }
}

static void
genWindowFocus(struct FrameData *wdata, Boolean gain) {
    XEvent ev;
    JNIEnv      *env = (JNIEnv *)JNU_GetEnv(jvm, JNI_VERSION_1_2);
    memset(&ev, 0, sizeof(ev));

    ev.type = (gain?FocusIn:FocusOut);
    ev.xany.send_event = True;
    ev.xany.display = awt_display;
    ev.xfocus.mode = NotifyNormal;
    ev.xfocus.detail = NotifyNonlinear;
    ev.xfocus.window = XtWindow(wdata->winData.shell);
    awt_put_back_event(env, &ev);
}

extern Boolean skipNextFocusIn;

static void
callNotifyStarted(JNIEnv* env, jobject peer) {
    DECLARE_VOID_JAVA_METHOD(notifyStartedMID, MEmbeddedFramePeerClass,
                             "notifyStarted", "()V");

    (*env)->CallVoidMethod(env, peer, notifyStartedMID);
}

void
xembed_eventHandler(XEvent *event)
{
    JNIEnv      *env = (JNIEnv *)JNU_GetEnv(jvm, JNI_VERSION_1_2);
    struct FrameData *wdata;
    xembed_data * data;

    data = getData(event->xany.window);
    if (data == NULL) {
        MTRACEP1("No XEMBED client registered for this window %x\n", event->xany.window);
        if (event->xany.type == ClientMessage) {
            MTRACEP7("Unprocessed handleClientMessage: type=%d 0=%ld 1=%ld(%s) 2=%ld 3=%ld 4=%ld\n",
                     event->xclient.message_type, event->xclient.data.l[0],
                     event->xclient.data.l[1], msg_to_str(event->xclient.data.l[1]),
                     event->xclient.data.l[2],
                     event->xclient.data.l[3], event->xclient.data.l[4]);
        }
        return;
    }

    wdata = data->wdata;

    if (event->xany.type == ClientMessage) {
        MTRACEP6("handleClientMessage: type=%d 0=%ld 1=%ld 2=%ld 3=%ld 4=%ld\n",
                 event->xclient.message_type, event->xclient.data.l[0],
                 event->xclient.data.l[1], event->xclient.data.l[2],
                 event->xclient.data.l[3], event->xclient.data.l[4]);
        // Probably a message from embedder
        if (event->xclient.message_type == XA_XEmbed) {
            // XEmbed message, data[1] contains message
            switch ((int)event->xclient.data.l[1]) {
              case XEMBED_EMBEDDED_NOTIFY:
                  MTRACE("EMBEDDED_NOTIFY\n");
                  data->active = True;
                  data->embedder = getEmbedder(data->client);
                  // If Frame has not been reparented already we should "reparent"
                  // it manually
                  if (!(wdata->reparented)) {
                      wdata->reparented = True;
                      // in XAWT we also update WM_NORMAL_HINTS here.
                  }
                  {
                      struct WidgetInfo* winfo =
                          findWidgetInfo(wdata->winData.comp.widget);
                      JNIEnv *env = (JNIEnv *)JNU_GetEnv(jvm, JNI_VERSION_1_4);
                      if (winfo != NULL) {
                          callNotifyStarted(env, winfo->peer);
                      }
                  }
                  MTRACE("Embedded notify in client\n");
                  break;
              case XEMBED_WINDOW_DEACTIVATE:
                  MTRACE("DEACTIVATE\n");
                  data->applicationActive = False;
                  break;
              case XEMBED_WINDOW_ACTIVATE:
                  MTRACE("ACTIVATE\n");
                  data->applicationActive = True;
                  break;
              case XEMBED_FOCUS_IN:
                  MTRACE("FOCUS IN\n");
                  skipNextFocusIn = False;
                  handleFocusIn(wdata, (int)(event->xclient.data.l[2]));
                  genWindowFocus(wdata, True);
                  break;
              case XEMBED_FOCUS_OUT:
                  MTRACE("FOCUS OUT\n");
                  genWindowFocus(wdata, False);
                  break;
            }
        }
    } else if (event->xany.type == ReparentNotify) {
        data->embedder = event->xreparent.parent;
    }
}

void
notify_ready(Window client) {
    sendMessage(getEmbedder(client), _SUN_XEMBED_START);
}

void
install_xembed(Widget client_widget, struct FrameData* wdata) {
    JNIEnv      *env = (JNIEnv *)JNU_GetEnv(jvm, JNI_VERSION_1_2);
    xembed_info info = {XEMBED_VERSION, XEMBED_MAPPED};
    Window client = XtWindow(client_widget);
    xembed_data * data;

    AWT_LOCK();
    data = addData(client);
    data->wdata = wdata;

    // Install event handler for messages from embedder
    XSelectInput(awt_display, client, StructureNotifyMask);

    // Install XEMBED_INFO information
    XChangeProperty(awt_display, client, XA_XEmbedInfo,
                    XA_XEmbedInfo, 32, PropModeReplace,
                    (unsigned char*)&info, 2);
    MTRACE("Installing xembed\n");

    notify_ready(client);

    AWT_UNLOCK();
}

void
deinstall_xembed(struct FrameData* wdata) {
    xembed_data * data = getDataByFrame(wdata);

    if (data != NULL) {
        removeData(data->client);
    }
}

void
requestXEmbedFocus(struct FrameData * wdata) {
    xembed_data * data = getDataByFrame(wdata);

    if (data != NULL) {
        if (data->active && data->applicationActive) {
            sendMessage(data->embedder, XEMBED_REQUEST_FOCUS);
        }
    }
}

Boolean
isXEmbedActive(struct FrameData * wdata) {
    xembed_data * data = getDataByFrame(wdata);
    return (data != NULL && data->active);
}

Boolean
isXEmbedActiveByWindow(Window client) {
    xembed_data * data = getData(client);
    return (data != NULL && data->active);
}


Boolean
isXEmbedApplicationActive(struct FrameData * wdata) {
    xembed_data * data = getDataByFrame(wdata);
    return (data != NULL && data->applicationActive);
}

void
xembed_traverse_out(struct FrameData * wdata, jboolean direction) {
    xembed_data * data = getDataByFrame(wdata);
    sendMessage(data->embedder, (direction == JNI_TRUE)?XEMBED_FOCUS_NEXT:XEMBED_FOCUS_PREV);
}
