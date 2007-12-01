/*
 * Copyright 2003-2006 Sun Microsystems, Inc.  All Rights Reserved.
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

#include "awt_dnd.h"

/* Declares getCursor(JNIEnv, jobject) */
#include "awt_Cursor.h"

/* Define java constants */
#include "java_awt_dnd_DnDConstants.h"
#include "sun_awt_dnd_SunDragSourceContextPeer.h"

/* Define DECLARE_* macros */
#include "awt_DataTransferer.h"

#define GRAB_EVENT_MASK                                          \
   (ButtonPressMask | ButtonMotionMask | ButtonReleaseMask)

/* Events selected on the root window during drag. */
#define ROOT_EVENT_MASK                                          \
   (ButtonMotionMask | KeyPressMask | KeyReleaseMask)

/* Events selected on registered receiver windows during drag. */
#define RECEIVER_EVENT_MASK                                      \
   (StructureNotifyMask)


/* in canvas.c */
extern jint getModifiers(uint32_t state, jint button, jint keyCode);

typedef struct {
    CARD8    byte_order;
    CARD8    protocol_version;
    CARD16   index;
    CARD32   selection_atom;
} InitiatorInfo;

typedef enum {
    /*
     * Communicate with receivers of both protocols.
     * If the receiver supports both protocols,
     * choose Motif DnD for communication.
     */
    DS_POLICY_PREFER_MOTIF,
    /*
     * Communicate with receivers of both protocols.
     * If the receiver supports both protocols,
     * choose XDnD for communication. [default]
     */
    DS_POLICY_PREFER_XDND,
    /* Communicate only with Motif DnD receivers. */
    DS_POLICY_ONLY_MOTIF,
    /* Communicate only with XDnD receivers. */
    DS_POLICY_ONLY_XDND
} DragSourcePolicy;


/* The drag source policy. */
static DragSourcePolicy drag_source_policy = DS_POLICY_PREFER_XDND;

static Boolean dnd_in_progress = False;
static Boolean drag_in_progress = False;
static jobject source_peer = NULL;
static Atom* data_types = NULL;
static unsigned int data_types_count = 0;
static Window drag_root_window = None;
static EventMask your_root_event_mask = NoEventMask;
static Time latest_time_stamp = CurrentTime;

/* The child of the root which is currently under the mouse. */
static Window target_root_subwindow = None;

static Window target_window = None;
static long target_window_mask = 0;
static Window target_proxy_window = None;
static Protocol target_protocol = NO_PROTOCOL;
static unsigned int target_protocol_version = 0;
/*
 * The server time when the pointer entered the current target -
 * needed on Motif DnD to filter out messages from the previous
 * target.
 * It is updated whenever the target_window is updated.
 * If the target_window is set to non-None, it is set to the time stamp
 * of the X event that trigger the update. Otherwise, it is set to CurrentTime.
 */
static Time target_enter_server_time = CurrentTime;

static int x_root = 0;
static int y_root = 0;
static unsigned int event_state = 0;

static jint source_action = java_awt_dnd_DnDConstants_ACTION_NONE;
static jint source_actions = java_awt_dnd_DnDConstants_ACTION_NONE;
static jint target_action = java_awt_dnd_DnDConstants_ACTION_NONE;

/* Forward declarations */
static void cleanup_drag(Display* dpy, Time time);
static Boolean process_proxy_mode_event(XEvent* xev);

/**************************** XEmbed server DnD support ***********************/
static Window proxy_mode_source_window = None;
/******************************************************************************/

/**************************** JNI stuff ***************************************/

DECLARE_JAVA_CLASS(dscp_clazz, "sun/awt/dnd/SunDragSourceContextPeer")

static void
ds_postDragSourceDragEvent(JNIEnv* env, jint targetAction, unsigned int state,
                           int x, int y, jint dispatch_type) {
    DECLARE_VOID_JAVA_METHOD(dscp_postDragSourceDragEvent, dscp_clazz,
                             "postDragSourceDragEvent", "(IIIII)V");

    DASSERT(!JNU_IsNull(env, source_peer));
    if (JNU_IsNull(env, source_peer)) {
        return;
    }

    (*env)->CallVoidMethod(env, source_peer, dscp_postDragSourceDragEvent,
                           targetAction, getModifiers(state, 0, 0), x, y,
                           dispatch_type);
}

static jint
ds_convertModifiersToDropAction(JNIEnv* env, unsigned int state) {
    jint action;
    DECLARE_STATIC_JINT_JAVA_METHOD(dscp_convertModifiersToDropAction, dscp_clazz,
                                    "convertModifiersToDropAction", "(II)I");
    action = (*env)->CallStaticIntMethod(env, clazz, dscp_convertModifiersToDropAction,
                                              getModifiers(state, 0, 0), source_actions);
    if ((*env)->ExceptionCheck(env) == JNI_TRUE) {
        (*env)->ExceptionDescribe(env);
        (*env)->ExceptionClear(env);
        return java_awt_dnd_DnDConstants_ACTION_NONE;
    }
    return action;
}

static void
ds_postDragSourceEvent(JNIEnv* env, int x, int y) {
    DECLARE_VOID_JAVA_METHOD(dscp_dragExit, dscp_clazz,
                             "dragExit", "(II)V");

    DASSERT(!JNU_IsNull(env, source_peer));
    if (JNU_IsNull(env, source_peer)) {
        return;
    }

    (*env)->CallVoidMethod(env, source_peer, dscp_dragExit, x, y);
}

static void
ds_postDragSourceDropEvent(JNIEnv* env, jboolean success, jint targetAction,
                           int x, int y) {
    DECLARE_VOID_JAVA_METHOD(dscp_dragDropFinished, dscp_clazz,
                             "dragDropFinished", "(ZIII)V");

    DASSERT(!JNU_IsNull(env, source_peer));
    if (JNU_IsNull(env, source_peer)) {
        return;
    }

    (*env)->CallVoidMethod(env, source_peer, dscp_dragDropFinished,
                           success, targetAction, x, y);
}

/******************************************************************************/

static void
cancel_drag(XtPointer client_data, XtIntervalId* id) {
    Time time_stamp = awt_util_getCurrentServerTime();

    cleanup_drag(awt_display, time_stamp);
}

#define DONT_CARE -1

static void
awt_popupCallback(Widget shell, XtPointer closure, XtPointer call_data) {
    XtGrabKind grab_kind = XtGrabNone;

    if (call_data != NULL) {
        grab_kind = *((XtGrabKind*)call_data);
    }

    if (XmIsVendorShell(shell)) {
        int input_mode;
        XtVaGetValues(shell, XmNmwmInputMode, &input_mode, NULL);
        switch (input_mode) {
        case DONT_CARE:
        case MWM_INPUT_MODELESS:
            grab_kind = XtGrabNonexclusive; break;
        case MWM_INPUT_PRIMARY_APPLICATION_MODAL:
        case MWM_INPUT_SYSTEM_MODAL:
        case MWM_INPUT_FULL_APPLICATION_MODAL:
            grab_kind = XtGrabExclusive; break;
        }
    }

    if (grab_kind == XtGrabExclusive) {
        /*
         * We should cancel the drag on the toolkit thread. Otherwise, it can be
         * called while the toolkit thread is waiting inside some drag callback.
         * In this case Motif will crash when the drag callback returns.
         */
        XtAppAddTimeOut(awt_appContext, 0L, cancel_drag, NULL);
    }
}

static XtInitProc xt_shell_initialize = NULL;

static void
awt_ShellInitialize(Widget req, Widget new, ArgList args, Cardinal *num_args) {
    XtAddCallback(new, XtNpopupCallback, awt_popupCallback, NULL);
    (*xt_shell_initialize)(req, new, args, num_args);
}

/*
 * Fix for 4484572 (copied from awt_XmDnD.c).
 * Modify the 'initialize' routine for all ShellWidget instances, so that it
 * will install an XtNpopupCallback that cancels the current drag operation.
 * It is needed, since AWT doesn't have full control over all ShellWidget
 * instances (e.g. XmPopupMenu internally creates and popups an XmMenuShell).
 */
static void
awt_set_ShellInitialize() {
    static Boolean inited = False;

    DASSERT(!inited);
    if (inited) {
        return;
    }

    xt_shell_initialize = shellWidgetClass->core_class.initialize;
    shellWidgetClass->core_class.initialize = (XtInitProc)awt_ShellInitialize;
    inited = True;
}

/*
 * Returns True if initialization completes successfully.
 */
Boolean
awt_dnd_ds_init(Display* display) {
    if (XSaveContext(display, XA_XdndSelection, awt_convertDataContext,
                     (XPointer)NULL) == XCNOMEM) {
        return False;
    }

    if (XSaveContext(display, _XA_MOTIF_ATOM_0, awt_convertDataContext,
                     (XPointer)NULL) == XCNOMEM) {
        return False;
    }

    {
        char *ev = getenv("_JAVA_DRAG_SOURCE_POLICY");

        /* By default XDnD protocol is preferred. */
        drag_source_policy = DS_POLICY_PREFER_XDND;

        if (ev != NULL) {
            if (strcmp(ev, "PREFER_XDND") == 0) {
                drag_source_policy = DS_POLICY_PREFER_XDND;
            } else if (strcmp(ev, "PREFER_MOTIF") == 0) {
                drag_source_policy = DS_POLICY_PREFER_MOTIF;
            } else if (strcmp(ev, "ONLY_MOTIF") == 0) {
                drag_source_policy = DS_POLICY_ONLY_MOTIF;
            } else if (strcmp(ev, "ONLY_XDND") == 0) {
                drag_source_policy = DS_POLICY_ONLY_XDND;
            }
        }
    }

    awt_set_ShellInitialize();

    return True;
}

/*
 * Returns a handle of the window used as a drag source.
 */
Window
awt_dnd_ds_get_source_window() {
    return get_awt_root_window();
}

/*
 * Returns True if a drag operation initiated by this client
 * is still in progress.
 */
Boolean
awt_dnd_ds_in_progress() {
    return dnd_in_progress;
}

static void
ds_send_event_to_target(XClientMessageEvent* xclient) {
    /* Shortcut if the source is in the same JVM. */
    if (XtWindowToWidget(xclient->display, target_proxy_window) != NULL) {
        awt_dnd_dt_process_event((XEvent*)xclient);
    } else {
        XSendEvent(xclient->display, target_proxy_window, False, NoEventMask,
                   (XEvent*)xclient);
    }
}

static void
xdnd_send_enter(Display* dpy, Time time) {
    XClientMessageEvent enter;

    enter.display = dpy;
    enter.type = ClientMessage;
    enter.window = target_window;
    enter.format = 32;
    enter.message_type = XA_XdndEnter;
    enter.data.l[0] = awt_dnd_ds_get_source_window();
    enter.data.l[1] = target_protocol_version << XDND_PROTOCOL_SHIFT;
    enter.data.l[1] |= data_types_count > 3 ? XDND_DATA_TYPES_BIT : 0;
    enter.data.l[2] = data_types_count > 0 ? data_types[0] : None;
    enter.data.l[3] = data_types_count > 1 ? data_types[1] : None;
    enter.data.l[4] = data_types_count > 2 ? data_types[2] : None;

    ds_send_event_to_target(&enter);
}

static void
motif_send_enter(Display* dpy, Time time) {
    XClientMessageEvent enter;

    enter.display = dpy;
    enter.type = ClientMessage;
    enter.window = target_window;
    enter.format = 8;
    enter.message_type = _XA_MOTIF_DRAG_AND_DROP_MESSAGE;

    {
        void* p = &enter.data.b[0];
        int flags = 0;

        flags |= java_to_motif_actions(source_action) << MOTIF_DND_ACTION_SHIFT;
        flags |= java_to_motif_actions(source_actions) << MOTIF_DND_ACTIONS_SHIFT;

        write_card8(&p, TOP_LEVEL_ENTER | MOTIF_MESSAGE_FROM_INITIATOR);
        write_card8(&p, MOTIF_BYTE_ORDER);
        write_card16(&p, flags);
        write_card32(&p, time);
        write_card32(&p, awt_dnd_ds_get_source_window());
        write_card32(&p, _XA_MOTIF_ATOM_0);
    }

    ds_send_event_to_target(&enter);
}

static void
send_enter(Display* dpy, Time time) {
    switch (target_protocol) {
    case XDND_PROTOCOL:
        xdnd_send_enter(dpy, time);
        break;
    case MOTIF_DND_PROTOCOL:
        motif_send_enter(dpy, time);
        break;
    case NO_PROTOCOL:
    default:
        DTRACE_PRINTLN2("%s:%d send_enter: unknown DnD protocol.", __FILE__, __LINE__);
        break;
    }
}

static void
xdnd_send_move(XMotionEvent* event) {
    XClientMessageEvent move;

    move.display = event->display;
    move.type = ClientMessage;
    move.window = target_window;
    move.format = 32;
    move.message_type = XA_XdndPosition;
    move.data.l[0] = awt_dnd_ds_get_source_window();
    move.data.l[1] = 0; /* flags */
    move.data.l[2] = event->x_root << 16 | event->y_root;
    move.data.l[3] = event->time;
    move.data.l[4] = java_to_xdnd_action(source_action);

    ds_send_event_to_target(&move);
}

static void
motif_send_move(XMotionEvent* event) {
    XClientMessageEvent move;

    move.display = event->display;
    move.type = ClientMessage;
    move.window = target_window;
    move.format = 8;
    move.message_type = _XA_MOTIF_DRAG_AND_DROP_MESSAGE;

    {
        void* p = move.data.b;
        int flags = 0;

        flags |= java_to_motif_actions(source_action) << MOTIF_DND_ACTION_SHIFT;
        flags |= java_to_motif_actions(source_actions) << MOTIF_DND_ACTIONS_SHIFT;

        write_card8(&p, DRAG_MOTION | MOTIF_MESSAGE_FROM_INITIATOR);
        write_card8(&p, MOTIF_BYTE_ORDER);
        write_card16(&p, flags);
        write_card32(&p, event->time);
        write_card16(&p, event->x_root);
        write_card16(&p, event->y_root);
    }

    ds_send_event_to_target(&move);
}

static void
send_move(XMotionEvent* event) {
    switch (target_protocol) {
    case XDND_PROTOCOL:
        xdnd_send_move(event);
        break;
    case MOTIF_DND_PROTOCOL:
        motif_send_move(event);
        break;
    case NO_PROTOCOL:
    default:
        DTRACE_PRINTLN2("%s:%d send_move: unknown DnD protocol.", __FILE__, __LINE__);
        break;
    }
}

static void
xdnd_send_leave(Display* dpy, Time time) {
    XClientMessageEvent leave;

    leave.display = dpy;
    leave.type = ClientMessage;
    leave.window = target_window;
    leave.format = 32;
    leave.message_type = XA_XdndLeave;
    leave.data.l[0] = awt_dnd_ds_get_source_window();
    leave.data.l[1] = 0;
    leave.data.l[2] = 0;
    leave.data.l[3] = 0;
    leave.data.l[4] = 0;

    ds_send_event_to_target(&leave);
}

static void
motif_send_leave(Display* dpy, Time time) {
    XClientMessageEvent leave;

    leave.display = dpy;
    leave.type = ClientMessage;
    leave.window = target_window;
    leave.format = 8;
    leave.message_type = _XA_MOTIF_DRAG_AND_DROP_MESSAGE;

    {
        void* p = &leave.data.b[0];

        write_card8(&p, TOP_LEVEL_LEAVE | MOTIF_MESSAGE_FROM_INITIATOR);
        write_card8(&p, MOTIF_BYTE_ORDER);
        write_card16(&p, 0);
        write_card32(&p, time);
        write_card32(&p, awt_dnd_ds_get_source_window());
    }

    ds_send_event_to_target(&leave);
}

static void
send_leave(Display* dpy, Time time) {
    switch (target_protocol) {
    case XDND_PROTOCOL:
        xdnd_send_leave(dpy, time);
        break;
    case MOTIF_DND_PROTOCOL:
        motif_send_leave(dpy, time);
        break;
    case NO_PROTOCOL:
    default:
        DTRACE_PRINTLN2("%s:%d send_leave: unknown DnD protocol.", __FILE__, __LINE__);
        break;
    }
}


static void
xdnd_send_drop(XButtonEvent* event) {
    XClientMessageEvent drop;

    drop.display = event->display;
    drop.type = ClientMessage;
    drop.window = target_window;
    drop.format = 32;
    drop.message_type = XA_XdndDrop;
    drop.data.l[0] = awt_dnd_ds_get_source_window();
    drop.data.l[1] = 0; /* flags */
    drop.data.l[2] = event->time; /* ### */
    drop.data.l[3] = 0;
    drop.data.l[4] = 0;

    ds_send_event_to_target(&drop);
}

static void
motif_send_drop(XButtonEvent* event) {
    XClientMessageEvent drop;

    /*
     * Motif drop sites expect TOP_LEVEL_LEAVE before DROP_START.
     */
    motif_send_leave(event->display, event->time);

    drop.display = event->display;
    drop.type = ClientMessage;
    drop.window = target_window;
    drop.format = 8;
    drop.message_type = _XA_MOTIF_DRAG_AND_DROP_MESSAGE;

    {
        void* p = &drop.data.b[0];
        int flags = 0;

        flags |= java_to_motif_actions(source_action) << MOTIF_DND_ACTION_SHIFT;
        flags |= java_to_motif_actions(source_actions) << MOTIF_DND_ACTIONS_SHIFT;

        write_card8(&p, DROP_START | MOTIF_MESSAGE_FROM_INITIATOR);
        write_card8(&p, MOTIF_BYTE_ORDER);
        write_card16(&p, flags);
        write_card32(&p, event->time);
        write_card16(&p, event->x_root);
        write_card16(&p, event->y_root);
        write_card32(&p, _XA_MOTIF_ATOM_0);
        write_card32(&p, awt_dnd_ds_get_source_window());
    }

    ds_send_event_to_target(&drop);
}

static void
send_drop(XButtonEvent* event) {
    switch (target_protocol) {
    case XDND_PROTOCOL:
        xdnd_send_drop(event);
        break;
    case MOTIF_DND_PROTOCOL:
        motif_send_drop(event);
        break;
    case NO_PROTOCOL:
    default:
        DTRACE_PRINTLN2("%s:%d send_drop: unknown DnD protocol.", __FILE__, __LINE__);
        break;
    }
}

static void
remove_dnd_grab(Display* dpy, Time time) {
    XUngrabPointer(dpy, time);
    XUngrabKeyboard(dpy, time);

    /* Restore the root event mask if it was changed. */
    if ((your_root_event_mask | ROOT_EVENT_MASK) != your_root_event_mask &&
        drag_root_window != None) {

        XSelectInput(dpy, drag_root_window, your_root_event_mask);

        drag_root_window = None;
        your_root_event_mask = NoEventMask;
    }
}

static void
cleanup_target_info(Display* dpy) {
    target_root_subwindow = None;

    target_window = None;
    target_proxy_window = None;
    target_protocol = NO_PROTOCOL;
    target_protocol_version = 0;
    target_enter_server_time = CurrentTime;
    target_action = java_awt_dnd_DnDConstants_ACTION_NONE;
}

static void
cleanup_drag(Display* dpy, Time time) {
    JNIEnv *env = (JNIEnv*)JNU_GetEnv(jvm, JNI_VERSION_1_4);

    if (dnd_in_progress) {
        if (target_window != None) {
            send_leave(dpy, time);
        }

        if (target_action != java_awt_dnd_DnDConstants_ACTION_NONE) {
            JNIEnv *env = (JNIEnv*)JNU_GetEnv(jvm, JNI_VERSION_1_4);
            ds_postDragSourceEvent(env, x_root, y_root);
        }

        ds_postDragSourceDropEvent(env, JNI_FALSE,
                                   java_awt_dnd_DnDConstants_ACTION_NONE,
                                   x_root, y_root);
    }

    /* Cleanup the global state */
    dnd_in_progress = False;
    drag_in_progress = False;
    data_types_count = 0;
    if (data_types != NULL) {
        free(data_types);
        data_types = NULL;
    }
    if (!JNU_IsNull(env, source_peer)) {
        (*env)->DeleteGlobalRef(env, source_peer);
        source_peer = NULL;
    }

    cleanup_target_info(dpy);

    remove_dnd_grab(dpy, time);

    XDeleteProperty(awt_display, awt_dnd_ds_get_source_window(), _XA_MOTIF_ATOM_0);
    XDeleteProperty(awt_display, awt_dnd_ds_get_source_window(), XA_XdndTypeList);
    XDeleteProperty(awt_display, awt_dnd_ds_get_source_window(), XA_XdndActionList);
    XtDisownSelection(awt_root_shell, _XA_MOTIF_ATOM_0, time);
    XtDisownSelection(awt_root_shell, XA_XdndSelection, time);

    awt_cleanupConvertDataContext(env, _XA_MOTIF_ATOM_0);
    awt_cleanupConvertDataContext(env, XA_XdndSelection);
}

static void
process_drop(XButtonEvent* event) {
    unsigned char ret;
    XWindowAttributes xwa;

    DASSERT(target_window != None);

    XGetWindowAttributes(event->display, target_window, &xwa);

    target_window_mask = xwa.your_event_mask;

    /* Select for DestoyNotify to cleanup if the target crashes. */
    ret = checked_XSelectInput(event->display, target_window,
                               (target_window_mask | StructureNotifyMask));

    if (ret == Success) {
        send_drop(event);
    } else {
        DTRACE_PRINTLN2("%s:%d drop rejected - invalid window.",
                        __FILE__, __LINE__);
        cleanup_drag(event->display, event->time);
    }
}

static Window
find_client_window(Display* dpy, Window window) {
    Window root, parent, *children;
    unsigned int nchildren, idx;

    Atom           type;
    int            format;
    unsigned long  nitems;
    unsigned long  after;
    unsigned char  *data;
    Status ret;

    if (XGetWindowProperty(dpy, window, XA_WM_STATE, 0, 0, False,
                           AnyPropertyType, &type, &format, &nitems,
                           &after, &data) == Success) {
        XFree(data);
    }

    if (type != None) {
        return window;
    }

    if (!XQueryTree(dpy, window, &root, &parent, &children, &nchildren)) {
        return None;
    }

    if (children == NULL) {
        return None;
    }

    for (idx = 0; idx < nchildren; idx++) {
        Window win = find_client_window(dpy, children[idx]);
        if (win != None) {
            XFree(children);
            return win;
        }
    }

    XFree(children);
    return None;
}

static void
do_update_target_window(Display* dpy, Window subwindow, Time time) {
    Window client_window = None;
    Window proxy_window = None;
    Protocol protocol = NO_PROTOCOL;
    unsigned int protocol_version = 0;
    Boolean is_receiver = False;

    client_window = find_client_window(dpy, subwindow);

    if (client_window != None) {
        /* Request status */
        int            status;

        /* Returns of XGetWindowProperty */
        Atom           type;
        int            format;
        unsigned long  nitems;
        unsigned long  after;
        unsigned char  *data;

        /*
         * No need for checked_XGetWindowProperty, since we check the returned
         * property type anyway.
         */
        if (drag_source_policy != DS_POLICY_ONLY_XDND) {

            data = NULL;
            status = XGetWindowProperty(dpy, client_window,
                                        _XA_MOTIF_DRAG_RECEIVER_INFO,
                                        0, 0xFFFF, False, AnyPropertyType,
                                        &type, &format, &nitems, &after, &data);

            if (status == Success && data != NULL && type != None && format == 8
                && nitems >= MOTIF_RECEIVER_INFO_SIZE) {
                unsigned char byte_order = read_card8((char*)data, 0);
                unsigned char drag_protocol_style = read_card8((char*)data, 2);

                switch (drag_protocol_style) {
                case MOTIF_PREFER_PREREGISTER_STYLE :
                case MOTIF_PREFER_DYNAMIC_STYLE :
                case MOTIF_DYNAMIC_STYLE :
                case MOTIF_PREFER_RECEIVER_STYLE :
                    proxy_window = read_card32((char*)data, 4, byte_order);
                    protocol = MOTIF_DND_PROTOCOL;
                    protocol_version = read_card8((char*)data, 1);
                    is_receiver = True;
                    break;
                default:
                    DTRACE_PRINTLN3("%s:%d unsupported protocol style (%d).",
                                    __FILE__, __LINE__, (int)drag_protocol_style);
                }
            }

            if (status == Success) {
                XFree(data);
                data = NULL;
            }
        }

        if (drag_source_policy != DS_POLICY_ONLY_MOTIF &&
            (drag_source_policy != DS_POLICY_PREFER_MOTIF || !is_receiver)) {

            data = NULL;
            status = XGetWindowProperty(dpy, client_window, XA_XdndAware, 0, 1,
                                        False, AnyPropertyType, &type, &format,
                                        &nitems, &after, &data);

            if (status == Success && data != NULL && type == XA_ATOM) {
                unsigned int target_version = *((unsigned int*)data);

                if (target_version >= XDND_MIN_PROTOCOL_VERSION) {
                    proxy_window = None;
                    protocol = XDND_PROTOCOL;
                    protocol_version = target_version < XDND_PROTOCOL_VERSION ?
                        target_version : XDND_PROTOCOL_VERSION;
                    is_receiver = True;
                }
            }

            /* Retrieve the proxy window handle and check if it is valid. */
            if (protocol == XDND_PROTOCOL) {
                if (status == Success) {
                    XFree(data);
                }

                data = NULL;
                status = XGetWindowProperty(dpy, client_window, XA_XdndProxy, 0,
                                            1, False, XA_WINDOW, &type, &format,
                                            &nitems, &after, &data);

                if (status == Success && data != NULL && type == XA_WINDOW) {
                    proxy_window = *((Window*)data);
                }

                if (proxy_window != None) {
                    if (status == Success) {
                        XFree(data);
                    }

                    data = NULL;
                    status = XGetWindowProperty(dpy, proxy_window, XA_XdndProxy,
                                                0, 1, False, XA_WINDOW, &type,
                                                &format, &nitems, &after, &data);

                    if (status != Success || data == NULL || type != XA_WINDOW ||
                        *((Window*)data) != proxy_window) {
                        proxy_window = None;
                    } else {
                        if (status == Success) {
                            XFree(data);
                        }

                        data = NULL;
                        status = XGetWindowProperty(dpy, proxy_window,
                                                    XA_XdndAware,  0, 1, False,
                                                    AnyPropertyType, &type,
                                                    &format, &nitems, &after,
                                                    &data);

                        if (status != Success || data == NULL || type != XA_ATOM) {
                            proxy_window = None;
                        }
                    }
                }
            }

            XFree(data);
        }

        if (proxy_window == None) {
            proxy_window = client_window;
        }
    }

    if (is_receiver) {
        target_window = client_window;
        target_proxy_window = proxy_window;
        target_protocol = protocol;
        target_protocol_version = protocol_version;
    } else {
        target_window = None;
        target_proxy_window = None;
        target_protocol = NO_PROTOCOL;
        target_protocol_version = 0;
    }

    target_action = java_awt_dnd_DnDConstants_ACTION_NONE;

    if (target_window != None) {
        target_enter_server_time = time;
    } else {
        target_enter_server_time = CurrentTime;
    }

    target_root_subwindow = subwindow;
}

static void
update_target_window(XMotionEvent* event) {
    Display* dpy = event->display;
    int x = event->x_root;
    int y = event->x_root;
    Time time = event->time;
    Window subwindow = event->subwindow;

    /*
     * If this event had occurred before the pointer was grabbed,
     * query the server for the current root subwindow.
     */
    if (event->window != event->root) {
        int xw, yw, xr, yr;
        unsigned int modifiers;
        XQueryPointer(dpy, event->root, &event->root, &subwindow,
                      &xr, &yr, &xw, &yw, &modifiers);
    }

    if (target_root_subwindow != subwindow) {
        if (target_window != None) {
            send_leave(dpy, time);

            /*
             * Neither Motif DnD nor XDnD provide a mean for the target
             * to notify the source that the pointer exits the drop site
             * that occupies the whole top level.
             * We detect this situation and post dragExit.
             */
            if (target_action != java_awt_dnd_DnDConstants_ACTION_NONE) {
                JNIEnv *env = (JNIEnv*)JNU_GetEnv(jvm, JNI_VERSION_1_4);
                ds_postDragSourceEvent(env, x, y);
            }
        }

        /* Update the global state. */
        do_update_target_window(dpy, subwindow, time);

        if (target_window != None) {
            send_enter(dpy, time);
        }
    }
}

/*
 * Updates the source action based on the specified event state.
 * Returns True if source action changed, False otherwise.
 */
static Boolean
update_source_action(unsigned int state) {
    JNIEnv* env = (JNIEnv*)JNU_GetEnv(jvm, JNI_VERSION_1_4);
    jint action = ds_convertModifiersToDropAction(env, state);
    if (source_action == action) {
        return False;
    }
    source_action = action;
    return True;
}

static void
handle_mouse_move(XMotionEvent* event) {
    if (!drag_in_progress) {
        return;
    }

    if (x_root != event->x_root || y_root != event->y_root) {
        JNIEnv *env = (JNIEnv*)JNU_GetEnv(jvm, JNI_VERSION_1_4);
        ds_postDragSourceDragEvent(env, target_action, event->state,
                                   event->x_root, event->y_root,
                                   sun_awt_dnd_SunDragSourceContextPeer_DISPATCH_MOUSE_MOVED);

        x_root = event->x_root;
        y_root = event->y_root;
    }

    if (event_state != event->state) {
        if (update_source_action(event->state) && target_window != None) {
            JNIEnv *env = (JNIEnv*)JNU_GetEnv(jvm, JNI_VERSION_1_4);
            ds_postDragSourceDragEvent(env, target_action, event->state,
                                       event->x_root, event->y_root,
                                       sun_awt_dnd_SunDragSourceContextPeer_DISPATCH_CHANGED);
        }
        event_state = event->state;
    }

    update_target_window(event);

    if (target_window != None) {
        send_move(event);
    }
}

static Boolean
handle_xdnd_status(XClientMessageEvent* event) {
    JNIEnv *env = (JNIEnv*)JNU_GetEnv(jvm, JNI_VERSION_1_4);
    long* event_data = event->data.l;
    Window target_win = None;
    jint action = java_awt_dnd_DnDConstants_ACTION_NONE;

    DTRACE_PRINTLN4("%s:%d XdndStatus target_window=%ld target_protocol=%d.",
                    __FILE__, __LINE__, target_window, target_protocol);

    if (target_protocol != XDND_PROTOCOL) {
        DTRACE_PRINTLN2("%s:%d XdndStatus rejected - invalid state.",
                        __FILE__, __LINE__);
        return True;
    }

    target_win = event_data[0];

    /* Ignore XDnD messages from all other windows. */
    if (target_window != target_win) {
        DTRACE_PRINTLN4("%s:%d XdndStatus rejected - invalid target window cur=%ld this=%ld.",
                        __FILE__, __LINE__, target_window, target_win);
        return True;
    }

    if (event_data[1] & XDND_ACCEPT_DROP_FLAG) {
        /* This feature is new in XDnD version 2, but we can use it as XDnD
           compliance only requires supporting version 3 and up. */
        action = xdnd_to_java_action(event_data[4]);
    }

    if (action == java_awt_dnd_DnDConstants_ACTION_NONE &&
        target_action != java_awt_dnd_DnDConstants_ACTION_NONE) {
        ds_postDragSourceEvent(env, x_root, y_root);
    } else if (action != java_awt_dnd_DnDConstants_ACTION_NONE) {
        jint type = 0;

        if (target_action == java_awt_dnd_DnDConstants_ACTION_NONE) {
            type = sun_awt_dnd_SunDragSourceContextPeer_DISPATCH_ENTER;
        } else {
            type = sun_awt_dnd_SunDragSourceContextPeer_DISPATCH_MOTION;
        }

        ds_postDragSourceDragEvent(env, action, event_state,
                                   x_root, y_root, type);
    }

    target_action = action;

    return True;
}

static Boolean
handle_xdnd_finished(XClientMessageEvent* event) {
    JNIEnv *env = (JNIEnv*)JNU_GetEnv(jvm, JNI_VERSION_1_4);
    long* event_data = event->data.l;
    Window target_win = None;
    jboolean success = JNI_TRUE;
    jint action = java_awt_dnd_DnDConstants_ACTION_NONE;

    if (target_protocol != XDND_PROTOCOL) {
        DTRACE_PRINTLN2("%s:%d XdndStatus rejected - invalid state.",
                        __FILE__, __LINE__);
        return True;
    }

    target_win = event_data[0];

    /* Ignore XDnD messages from all other windows. */
    if (target_window != target_win) {
        DTRACE_PRINTLN4("%s:%d XdndStatus rejected - invalid target window cur=%ld this=%ld.",
                        __FILE__, __LINE__, target_window, target_win);
        return True;
    }

    if (target_protocol_version >= 5) {
        success = (event_data[1] & XDND_ACCEPT_DROP_FLAG) != 0 ?
            JNI_TRUE : JNI_FALSE;
        action = xdnd_to_java_action(event_data[2]);
    } else {
        /* Assume that the drop was successful and the performed drop action is
           the drop action accepted with the latest XdndStatus message. */
        success = JNI_TRUE;
        action = target_action;
    }

    ds_postDragSourceDropEvent(env, success, action, x_root, y_root);

    dnd_in_progress = False;

    XSelectInput(event->display, target_win, target_window_mask);

    cleanup_drag(event->display, CurrentTime);

    return True;
}

static Boolean
handle_motif_client_message(XClientMessageEvent* event) {
    JNIEnv *env = (JNIEnv*)JNU_GetEnv(jvm, JNI_VERSION_1_4);
    int reason = (int)(event->data.b[0] & MOTIF_MESSAGE_REASON_MASK);
    int origin = (int)(event->data.b[0] & MOTIF_MESSAGE_SENDER_MASK);
    unsigned char byte_order = event->data.b[1];
    jint action = java_awt_dnd_DnDConstants_ACTION_NONE;
    Time time = CurrentTime;
    int x = 0, y = 0;

    /* Only receiver messages should be handled. */
    if (origin != MOTIF_MESSAGE_FROM_RECEIVER) {
        return False;
    }

    if (target_protocol != MOTIF_DND_PROTOCOL) {
        DTRACE_PRINTLN2("%s:%d _MOTIF_DRAG_AND_DROP_MESSAGE rejected - invalid state.",
                        __FILE__, __LINE__);
        return True;
    }

    switch (reason) {
    case DROP_SITE_ENTER:
    case DROP_SITE_LEAVE:
    case DRAG_MOTION:
    case OPERATION_CHANGED:
        break;
    default:
        return False;
    }

    time = read_card32(event->data.b, 4, byte_order);

    /* Discard events from the previous receiver. */
    if (target_enter_server_time == CurrentTime ||
        time < target_enter_server_time) {
        DTRACE_PRINTLN2("%s:%d _MOTIF_DRAG_AND_DROP_MESSAGE rejected - invalid time.",
                        __FILE__, __LINE__);
        return True;
    }

    if (reason != DROP_SITE_LEAVE) {
        CARD16 flags = read_card16(event->data.b, 2, byte_order);
        unsigned char status = (flags & MOTIF_DND_STATUS_MASK) >>
            MOTIF_DND_STATUS_SHIFT;
        unsigned char motif_action = (flags & MOTIF_DND_ACTION_MASK) >>
            MOTIF_DND_ACTION_SHIFT;

        if (status == MOTIF_VALID_DROP_SITE) {
            action = motif_to_java_actions(motif_action);
        } else {
            action = java_awt_dnd_DnDConstants_ACTION_NONE;
        }

        x = read_card16(event->data.b, 8, byte_order);
        y = read_card16(event->data.b, 10, byte_order);
    }

    /*
     * We should derive the type of java event to post not from the message
     * reason, but from the combination of the current and previous target
     * actions:
     * Even if the reason is DROP_SITE_LEAVE we shouldn't post dragExit
     * if the drag was rejected earlier.
     * Even if the reason is DROP_SITE_ENTER we shouldn't post dragEnter
     * if the drag is not accepted.
     */
    if (target_action != java_awt_dnd_DnDConstants_ACTION_NONE &&
        action == java_awt_dnd_DnDConstants_ACTION_NONE) {

        ds_postDragSourceEvent(env, x, y);
    } else if (action != java_awt_dnd_DnDConstants_ACTION_NONE) {
        jint type = 0;

        if (target_action == java_awt_dnd_DnDConstants_ACTION_NONE) {
            type = sun_awt_dnd_SunDragSourceContextPeer_DISPATCH_ENTER;
        } else {
            type = sun_awt_dnd_SunDragSourceContextPeer_DISPATCH_MOTION;
        }

        ds_postDragSourceDragEvent(env, action, event_state, x, y, type);
    }

    target_action = action;

    return True;
}

/*
 * Handles client messages.
 * Returns True if the event is processed, False otherwise.
 */
static Boolean
handle_client_message(XClientMessageEvent* event) {
    if (event->message_type == XA_XdndStatus) {
        return handle_xdnd_status(event);
    } else if (event->message_type == XA_XdndFinished) {
        return handle_xdnd_finished(event);
    } else if (event->message_type == _XA_MOTIF_DRAG_AND_DROP_MESSAGE) {
        return handle_motif_client_message(event);
    }
    return False;
}

/*
 * Similar to XtLastTimestampProcessed(). We cannot use Xt time stamp, as it is
 * updated in XtDispatchEvent that may not be called if a java event is
 * consumed. This can make Xt time stamp out-of-date and cause XGrab* failures
 * with GrabInvalidTime reason.
 */
static Time
get_latest_time_stamp() {
    return latest_time_stamp;
}

static void
update_latest_time_stamp(XEvent* event) {
    Time time = latest_time_stamp;

    switch (event->type) {
    case KeyPress:
    case KeyRelease:     time = event->xkey.time;            break;
    case ButtonPress:
    case ButtonRelease:  time = event->xbutton.time;         break;
    case MotionNotify:   time = event->xmotion.time;         break;
    case EnterNotify:
    case LeaveNotify:    time = event->xcrossing.time;       break;
    case PropertyNotify: time = event->xproperty.time;       break;
    case SelectionClear: time = event->xselectionclear.time; break;
    }

    latest_time_stamp = time;
}

Boolean
awt_dnd_ds_process_event(XEvent* event) {
    Display* dpy = event->xany.display;

    update_latest_time_stamp(event);

    if (process_proxy_mode_event(event)) {
        return True;
    }

    if (!dnd_in_progress) {
        return False;
    }

    /* Process drag and drop messages. */
    switch (event->type) {
    case ClientMessage:
        return handle_client_message(&event->xclient);
    case DestroyNotify:
        /* Target crashed during drop processing - cleanup. */
        if (!drag_in_progress &&
            event->xdestroywindow.window == target_window) {
            cleanup_drag(dpy, CurrentTime);
            return True;
        }
        /* Pass along */
        return False;
    }

    if (!drag_in_progress) {
        return False;
    }

    /* Process drag-only messages. */
    switch (event->type) {
    case KeyRelease:
    case KeyPress: {
        KeySym keysym = XKeycodeToKeysym(dpy, event->xkey.keycode, 0);
        switch (keysym) {
        case XK_Escape: {
            if (keysym == XK_Escape) {
                remove_dnd_grab(dpy, event->xkey.time);
                cleanup_drag(dpy, event->xkey.time);
            }
            break;
        }
        case XK_Control_R:
        case XK_Control_L:
        case XK_Shift_R:
        case XK_Shift_L: {
            Window subwindow;
            int xw, yw, xr, yr;
            unsigned int modifiers;
            XQueryPointer(event->xkey.display, event->xkey.root, &event->xkey.root, &subwindow,
                          &xr, &yr, &xw, &yw, &modifiers);
            event->xkey.state = modifiers;
            //It's safe to use key event as motion event since we use only their common fields.
            handle_mouse_move(&event->xmotion);
            break;
        }
        }
        return True;
    }
    case ButtonPress:
        return True;
    case MotionNotify:
        handle_mouse_move(&event->xmotion);
        return True;
    case ButtonRelease:
        /*
         * On some X servers it could happen that ButtonRelease coordinates
         * differ from the latest MotionNotify coordinates, so we need to
         * process it as a mouse motion.
         * MotionNotify differs from ButtonRelease only in is_hint member, but
         * we never use it, so it is safe to cast to MotionNotify.
         */
        handle_mouse_move(&event->xmotion);
        if (event->xbutton.button == Button1 || event->xbutton.button == Button2) {
            // drag is initiated with Button1 or Button2 pressed and
            // ended on release of either of these buttons (as the same
            // behavior was with our old Motif DnD-based implementation)
            remove_dnd_grab(dpy, event->xbutton.time);
            drag_in_progress = False;
            if (target_window != None && target_action != java_awt_dnd_DnDConstants_ACTION_NONE) {
                /*
                 * ACTION_NONE indicates that either the drop target rejects the
                 * drop or it haven't responded yet. The latter could happen in
                 * case of fast drag, slow target-server connection or slow
                 * drag notifications processing on the target side.
                 */
                process_drop(&event->xbutton);
            } else {
                cleanup_drag(dpy, event->xbutton.time);
            }
        }
        return True;
    default:
        return False;
    }
}

static Boolean
motif_convert_proc(Widget w, Atom* selection, Atom* target, Atom* type,
                   XtPointer* value, unsigned long* length, int32_t* format) {

    if (*target == XA_XmTRANSFER_SUCCESS ||
        *target == XA_XmTRANSFER_FAILURE) {

        JNIEnv *env = (JNIEnv*)JNU_GetEnv(jvm, JNI_VERSION_1_4);
        jboolean success =
            (*target == XA_XmTRANSFER_SUCCESS) ? JNI_TRUE : JNI_FALSE;

        ds_postDragSourceDropEvent(env, success, target_action,
                                   x_root, y_root);

        dnd_in_progress = False;

        XSelectInput(XtDisplay(w), target_window, target_window_mask);

        cleanup_drag(XtDisplay(w), CurrentTime);

        *type = *target;
        *length = 0;
        *format = 32;
        *value = NULL;

        return True;
    } else {
        return awt_convertData(w, selection, target, type, value, length,
                               format);
    }
}

static Boolean
set_convert_data_context(JNIEnv* env, Display* dpy, XID xid, jobject component,
                         jobject transferable, jobject formatMap,
                         jlongArray formats) {
    awt_convertDataCallbackStruct* structPtr = NULL;

    if (XFindContext(awt_display, xid, awt_convertDataContext,
                     (XPointer*)&structPtr) == XCNOMEM || structPtr != NULL) {
        return False;
    }

    structPtr = calloc(1, sizeof(awt_convertDataCallbackStruct));
    if (structPtr == NULL) {
        return False;
    }

    structPtr->source              = (*env)->NewGlobalRef(env, component);
    structPtr->transferable        = (*env)->NewGlobalRef(env, transferable);
    structPtr->formatMap           = (*env)->NewGlobalRef(env, formatMap);
    structPtr->formats             = (*env)->NewGlobalRef(env, formats);

    if (JNU_IsNull(env, structPtr->source) ||
        JNU_IsNull(env, structPtr->transferable) ||
        JNU_IsNull(env, structPtr->formatMap) ||
        JNU_IsNull(env, structPtr->formats)) {

        if (!JNU_IsNull(env, structPtr->source)) {
            (*env)->DeleteGlobalRef(env, structPtr->source);
        }
        if (!JNU_IsNull(env, structPtr->transferable)) {
            (*env)->DeleteGlobalRef(env, structPtr->transferable);
        }
        if (!JNU_IsNull(env, structPtr->formatMap)) {
            (*env)->DeleteGlobalRef(env, structPtr->formatMap);
        }
        if (!JNU_IsNull(env, structPtr->formats)) {
            (*env)->DeleteGlobalRef(env, structPtr->formats);
        }
        free(structPtr);
        return False;
    }

    if (XSaveContext(dpy, xid, awt_convertDataContext,
                     (XPointer)structPtr) == XCNOMEM) {
        free(structPtr);
        return False;
    }

    return True;
}

/*
 * Convenience routine. Constructs an appropriate exception message based on the
 * specified prefix and the return code of XGrab* function and throws an
 * InvalidDnDOperationException with the constructed message.
 */
static void
throw_grab_failure_exception(JNIEnv* env, int ret_code, char* msg_prefix) {
    char msg[200];
    char* msg_cause = "";

    switch (ret_code) {
    case GrabNotViewable:  msg_cause = "not viewable";    break;
    case AlreadyGrabbed:   msg_cause = "already grabbed"; break;
    case GrabInvalidTime:  msg_cause = "invalid time";    break;
    case GrabFrozen:       msg_cause = "grab frozen";     break;
    default:               msg_cause = "unknown failure"; break;
    }

    sprintf(msg, "%s: %s.", msg_prefix, msg_cause);
    JNU_ThrowByName(env, "java/awt/dnd/InvalidDnDOperationException",
                    msg);
}

/*
 * Sets the proxy mode source window - the source window which the drag
 * notifications from an XEmbed client should be forwarded to.
 * If the window is not None and there is a drag operation in progress,
 * throws InvalidDnDOperationException and doesn't change
 * proxy_mode_source_window.
 * The caller mush hold AWT_LOCK.
 */
void
set_proxy_mode_source_window(Window window) {
    if (window != None && dnd_in_progress) {
        JNIEnv *env = (JNIEnv*)JNU_GetEnv(jvm, JNI_VERSION_1_4);
        JNU_ThrowByName(env, "java/awt/dnd/InvalidDnDOperationException",
                        "Drag and drop is already in progress.");
        return;
    }

    proxy_mode_source_window = window;
}

/*
 * Checks if the event is a drag notification from an XEmbed client.
 * If it is, forwards this event back to the current source and returns True.
 * Otherwise, returns False.
 * Currently only XDnD protocol notifications are recognized.
 * The caller must hold AWT_LOCK.
 */
static Boolean
process_proxy_mode_event(XEvent* event) {
    if (proxy_mode_source_window == None) {
        return False;
    }

    if (event->type == ClientMessage) {
        XClientMessageEvent* xclient = &event->xclient;
        if (xclient->message_type == XA_XdndStatus ||
            xclient->message_type == XA_XdndFinished) {
            Window source = proxy_mode_source_window;

            xclient->data.l[0] = xclient->window;
            xclient->window = source;

            XSendEvent(xclient->display, source, False, NoEventMask,
                       (XEvent*)xclient);

            if (xclient->message_type == XA_XdndFinished) {
                proxy_mode_source_window = None;
            }

            return True;
        }
    }

    return False;
}

/*
 * Class:     sun_awt_motif_X11DragSourceContextPeer
 * Method:    startDrag
 * Signature: ()V
 */
JNIEXPORT void JNICALL
Java_sun_awt_motif_X11DragSourceContextPeer_startDrag(JNIEnv *env,
                                                      jobject this,
                                                      jobject component,
                                                      jobject wpeer,
                                                      jobject transferable,
                                                      jobject trigger,
                                                      jobject cursor,
                                                      jint ctype,
                                                      jint actions,
                                                      jlongArray formats,
                                                      jobject formatMap) {
    Time time_stamp = CurrentTime;
    Cursor xcursor = None;
    Window root_window = None;
    Atom* targets = NULL;
    jsize num_targets = 0;

    AWT_LOCK();

    if (dnd_in_progress) {
        JNU_ThrowByName(env, "java/awt/dnd/InvalidDnDOperationException",
                        "Drag and drop is already in progress.");
        AWT_UNLOCK();
        return;
    }

    if (proxy_mode_source_window != None) {
        JNU_ThrowByName(env, "java/awt/dnd/InvalidDnDOperationException",
                        "Proxy drag is in progress.");
        AWT_UNLOCK();
        return;
    }

    if (!awt_dnd_init(awt_display)) {
        JNU_ThrowByName(env, "java/awt/dnd/InvalidDnDOperationException",
                        "DnD subsystem initialization failed.");
        AWT_UNLOCK();
        return;
    }

    if (!JNU_IsNull(env, cursor)) {
        xcursor = getCursor(env, cursor);

        if (xcursor == None) {
            JNU_ThrowByName(env, "java/awt/dnd/InvalidDnDOperationException",
                            "Invalid drag cursor");
            AWT_UNLOCK();
        }
    }

    /* Determine the root window for the drag operation. */
    {
        struct FrameData* wdata = (struct FrameData*)
            JNU_GetLongFieldAsPtr(env, wpeer, mComponentPeerIDs.pData);

        if (wdata == NULL) {
            JNU_ThrowNullPointerException(env, "Null component data");
            AWT_UNLOCK();
            return;
        }

        if (wdata->winData.shell == NULL) {
            JNU_ThrowNullPointerException(env, "Null shell widget");
            AWT_UNLOCK();
            return;
        }

        root_window = RootWindowOfScreen(XtScreen(wdata->winData.shell));

        if (root_window == None) {
            JNU_ThrowByName(env, "java/awt/dnd/InvalidDnDOperationException",
                            "Cannot get the root window for the drag operation.");
            AWT_UNLOCK();
            return;
        }
    }

    time_stamp = get_latest_time_stamp();

    /* Extract the targets from java array. */
    {
        targets = NULL;
        num_targets = (*env)->GetArrayLength(env, formats);

        /*
         * In debug build GetLongArrayElements aborts with assertion on an empty
         * array.
         */
        if (num_targets > 0) {
            jboolean isCopy = JNI_TRUE;
            jlong* java_targets = (*env)->GetLongArrayElements(env, formats,
                                                               &isCopy);

            if ((*env)->ExceptionCheck(env) == JNI_TRUE) {
                AWT_UNLOCK();
                return;
            }

            if (java_targets != NULL) {
                targets = (Atom*)malloc(num_targets * sizeof(Atom));
                if (targets != NULL) {
#ifdef _LP64
                    memcpy(targets, java_targets, num_targets * sizeof(Atom));
#else
                    jsize i;

                    for (i = 0; i < num_targets; i++) {
                        targets[i] = (Atom)java_targets[i];
                    }
#endif
                }
                (*env)->ReleaseLongArrayElements(env, formats, java_targets,
                                                 JNI_ABORT);
            }
        }
        if (targets == NULL) {
            num_targets = 0;
        }
    }

    /* Write the XDnD initiator info on the awt_root_shell. */
    {
        unsigned char ret;
        Atom action_atoms[3];
        unsigned int action_count = 0;

        if (actions & java_awt_dnd_DnDConstants_ACTION_COPY) {
            action_atoms[action_count] = XA_XdndActionCopy;
            action_count++;
        }
        if (actions & java_awt_dnd_DnDConstants_ACTION_MOVE) {
            action_atoms[action_count] = XA_XdndActionMove;
            action_count++;
        }
        if (actions & java_awt_dnd_DnDConstants_ACTION_LINK) {
            action_atoms[action_count] = XA_XdndActionLink;
            action_count++;
        }

        ret = checked_XChangeProperty(awt_display, awt_dnd_ds_get_source_window(),
                                      XA_XdndActionList, XA_ATOM, 32,
                                      PropModeReplace, (unsigned char*)action_atoms,
                                      action_count * sizeof(Atom));

        if (ret != Success) {
            cleanup_drag(awt_display, time_stamp);
            JNU_ThrowByName(env, "java/awt/dnd/InvalidDnDOperationException",
                            "Cannot write XdndActionList property");
            AWT_UNLOCK();
            return;
        }

        ret = checked_XChangeProperty(awt_display, awt_dnd_ds_get_source_window(),
                                      XA_XdndTypeList, XA_ATOM, 32,
                                      PropModeReplace, (unsigned char*)targets,
                                      num_targets);

        if (ret != Success) {
            cleanup_drag(awt_display, time_stamp);
            JNU_ThrowByName(env, "java/awt/dnd/InvalidDnDOperationException",
                            "Cannot write XdndTypeList property");
            AWT_UNLOCK();
            return;
        }
    }

    /* Write the Motif DnD initiator info on the awt_root_shell. */
    {
        InitiatorInfo info;
        unsigned char ret;
        int target_list_index =
            get_index_for_target_list(awt_display, targets, num_targets);

        if (target_list_index == -1) {
            cleanup_drag(awt_display, time_stamp);
            JNU_ThrowByName(env, "java/awt/dnd/InvalidDnDOperationException",
                            "Cannot determine the target list index.");
            AWT_UNLOCK();
            return;
        }

        info.byte_order = MOTIF_BYTE_ORDER;
        info.protocol_version = MOTIF_DND_PROTOCOL_VERSION;
        info.index = target_list_index;
        info.selection_atom = _XA_MOTIF_ATOM_0;

        ret = checked_XChangeProperty(awt_display, awt_dnd_ds_get_source_window(),
                                      _XA_MOTIF_ATOM_0,
                                      _XA_MOTIF_DRAG_INITIATOR_INFO, 8,
                                      PropModeReplace, (unsigned char*)&info,
                                      sizeof(InitiatorInfo));

        if (ret != Success) {
            cleanup_drag(awt_display, time_stamp);
            JNU_ThrowByName(env, "java/awt/dnd/InvalidDnDOperationException",
                            "Cannot write the Motif DnD initiator info");
            AWT_UNLOCK();
            return;
        }
    }

    /* Acquire XDnD selection ownership. */
    if (XtOwnSelection(awt_root_shell, XA_XdndSelection, time_stamp,
                       awt_convertData, NULL, NULL) != True) {
        cleanup_drag(awt_display, time_stamp);
        JNU_ThrowByName(env, "java/awt/dnd/InvalidDnDOperationException",
                        "Cannot acquire XdndSelection ownership.");
        AWT_UNLOCK();
        return;
    }

    /* Acquire Motif DnD selection ownership. */
    if (XtOwnSelection(awt_root_shell, _XA_MOTIF_ATOM_0, time_stamp,
                       motif_convert_proc, NULL, NULL) != True) {
        cleanup_drag(awt_display, time_stamp);
        JNU_ThrowByName(env, "java/awt/dnd/InvalidDnDOperationException",
                        "Cannot acquire Motif DnD selection ownership.");
        AWT_UNLOCK();
        return;
    }

    /*
     * Store the information needed to convert data for both selections
     * in awt_convertDataContext.
     */
    {
        if (!set_convert_data_context(env, awt_display, XA_XdndSelection,
                                      component, transferable, formatMap,
                                      formats)) {
            cleanup_drag(awt_display, time_stamp);
            JNU_ThrowByName(env, "java/awt/dnd/InvalidDnDOperationException",
                            "Cannot save context for XDnD selection data conversion.");
            AWT_UNLOCK();
            return;
        }

        if (!set_convert_data_context(env, awt_display, _XA_MOTIF_ATOM_0,
                                      component, transferable, formatMap,
                                      formats)) {
            cleanup_drag(awt_display, time_stamp);
            JNU_ThrowByName(env, "java/awt/dnd/InvalidDnDOperationException",
                            "Cannot save context for Motif DnD selection data conversion.");
            AWT_UNLOCK();
            return;
        }
    }

    /* Install X grabs. */
    {
        XWindowAttributes xwa;
        int ret;

        XGetWindowAttributes(awt_display, root_window, &xwa);

        your_root_event_mask = xwa.your_event_mask;

        XSelectInput(awt_display, root_window,
                     your_root_event_mask | ROOT_EVENT_MASK);

        ret = XGrabPointer(awt_display,
                           root_window,
                           False,
                           GRAB_EVENT_MASK,
                           GrabModeAsync,
                           GrabModeAsync,
                           None,
                           xcursor,
                           time_stamp);

        if (ret != GrabSuccess) {
            cleanup_drag(awt_display, time_stamp);
            throw_grab_failure_exception(env, ret, "Cannot grab pointer");
            AWT_UNLOCK();
            return;
        }

        ret = XGrabKeyboard(awt_display,
                            root_window,
                            False,
                            GrabModeAsync,
                            GrabModeAsync,
                            time_stamp);

        if (ret != GrabSuccess) {
            cleanup_drag(awt_display, time_stamp);
            throw_grab_failure_exception(env, ret, "Cannot grab keyboard");
            AWT_UNLOCK();
            return;
        }
    }

    /* Update the global state. */
    source_peer = (*env)->NewGlobalRef(env, this);
    dnd_in_progress = True;
    drag_in_progress = True;
    data_types = targets;
    data_types_count = num_targets;
    source_actions = actions;
    drag_root_window = root_window;

    AWT_UNLOCK();
}

/*
 * Class:     sun_awt_motif_X11DragSourceContextPeer
 * Method:    setNativeCursor
 * Signature: ()V
 */
JNIEXPORT void JNICALL
Java_sun_awt_motif_X11DragSourceContextPeer_setNativeCursor(JNIEnv *env,
                                                            jobject this,
                                                            jlong nativeCtxt,
                                                            jobject cursor,
                                                            jint type) {
    if (JNU_IsNull(env, cursor)) {
        return;
    }

    XChangeActivePointerGrab(awt_display,
                             GRAB_EVENT_MASK,
                             getCursor(env, cursor),
                             CurrentTime);
}
