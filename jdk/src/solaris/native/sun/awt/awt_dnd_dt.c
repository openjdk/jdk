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

#include "jlong.h"

#include "awt_DataTransferer.h"
#include "awt_MToolkit.h"

#include "java_awt_dnd_DnDConstants.h"
#include "java_awt_event_MouseEvent.h"

#include "sun_awt_motif_MComponentPeer.h"
#include "awt_xembed.h"

#define DT_INITIAL_STATE 0
#define DT_ENTERED_STATE 1
#define DT_OVER_STATE    2

extern struct ComponentIDs componentIDs;
extern struct MComponentPeerIDs mComponentPeerIDs;

/**************************** XEmbed server DnD support ***********************/
extern void
set_xembed_drop_target(JNIEnv* env, jobject server);
extern void
remove_xembed_drop_target(JNIEnv* env, jobject server);
extern Boolean
is_xembed_client(Window window);

DECLARE_JAVA_CLASS(MEmbedCanvasPeerClass, "sun/awt/motif/MEmbedCanvasPeer");
/******************************************************************************/

typedef enum {
    EventSuccess,    /* Event is successfully processed. */
    EventFailure     /* Failed to process the event. */
} EventStatus;

typedef enum {
    EnterEvent,    /* XdndEnter, TOP_LEVEL_ENTER */
    MotionEvent,   /* XdndPosition, DRAG_MOTION, OPERATION_CHANGED */
    LeaveEvent,    /* XdndLeave, TOP_LEVEL_LEAVE */
    DropEvent,     /* XdndDrop, DROP_START */
    UnknownEvent
} EventType;

static Protocol source_protocol = NO_PROTOCOL;
static unsigned int source_protocol_version = 0;
static Window source_window = None;
static Atom source_atom = None;
static long source_window_mask = None;
static jint source_actions = java_awt_dnd_DnDConstants_ACTION_NONE;
/*
 * According to XDnD protocol, XdndActionList is optional.
 * In case if XdndActionList is not set on the source, the list of drop actions
 * supported by the source is constructed as follows:
 *  - "copy" is always included;
 *  - "move" is included if at least one XdndPosition message received
 *    after the latest XdndEnter passed XdndActionMove in data.l[4];
 *  - "link" is included if at least one XdndPosition message received
 *    after the latest XdndEnter passed XdndActionLink in data.l[4].
 * We use a boolean flag to signal that we are building the list of drop actions
 * supported by the source.
 */
static Boolean track_source_actions = False;
static jint user_action = java_awt_dnd_DnDConstants_ACTION_NONE;
static jlongArray source_data_types = NULL;
static Atom* source_data_types_native = NULL;
static unsigned int source_data_types_count = 0;
static int source_x = 0;
static int source_y = 0;
static jobject target_component = NULL;
/*
 * The Motif DnD protocol prescribes that DROP_START message should always be
 * preceeded with TOP_LEVEL_LEAVE message. We need to cleanup on TOP_LEVEL_LEAVE
 * message, but DROP_START wouldn't be processed properly.
 * To resolve this issue we postpone cleanup using a boolean flag this flag is
 * set when we receive the TOP_LEVEL_LEAVE message and cleared when the next
 * client message arrives if that message is not DROP_START. If that message is
 * a DROP_START message, the flag is cleared after the DROP_START is processed.
 */
static Boolean motif_top_level_leave_postponed = False;
/*
 * We store a postponed TOP_LEVEL_LEAVE message here.
 */
static XClientMessageEvent motif_top_level_leave_postponed_event;

/* Forward declarations */
static Window get_root_for_window(Window window);
static Window get_outer_canvas_for_window(Window window);
static Boolean register_drop_site(Widget outer_canvas, XtPointer componentRef);
static Boolean is_xdnd_drag_message_type(unsigned long message_type);
static Boolean register_xdnd_drop_site(Display* dpy, Window toplevel,
                                       Window window);

/**************************** JNI stuff ***************************************/

DECLARE_JAVA_CLASS(dtcp_clazz, "sun/awt/motif/X11DropTargetContextPeer")

static void
dt_postDropTargetEvent(JNIEnv* env, jobject component, int x, int y,
                       jint dropAction, jint event_id,
                       XClientMessageEvent* event) {
    DECLARE_STATIC_VOID_JAVA_METHOD(dtcp_postDropTargetEventToPeer, dtcp_clazz,
                                    "postDropTargetEventToPeer",
                                    "(Ljava/awt/Component;IIII[JJI)V");

    {
        void* copy = NULL;

        if (event != NULL) {
            /*
             * For XDnD messages we append the information from the latest
             * XdndEnter to the context. It is done to be able to reconstruct
             * XdndEnter for an XEmbed client.
             */
            Boolean isXDnDMessage =
                is_xdnd_drag_message_type(event->message_type);

            if (isXDnDMessage) {
                copy = malloc(sizeof(XClientMessageEvent) +
                                                 4 * sizeof(long));
            } else {
                copy = malloc(sizeof(XClientMessageEvent));
            }

            if (copy == NULL) {
                DTRACE_PRINTLN2("%s:%d malloc failed.", __FILE__, __LINE__);
                return;
            }

            memcpy(copy, event, sizeof(XClientMessageEvent));

            if (isXDnDMessage) {
                size_t msgSize = sizeof(XClientMessageEvent);
                long data1 = source_protocol_version << XDND_PROTOCOL_SHIFT;
                long * appended_data;
                if (source_data_types_native != NULL &&
                    source_data_types_count > 3) {

                    data1 |= XDND_DATA_TYPES_BIT;
                }

                appended_data = (long*)((char*)copy + msgSize);
                appended_data[0] = data1;
                appended_data[1] = source_data_types_count > 0 ?
                    source_data_types_native[0] : 0;
                appended_data[2] = source_data_types_count > 1 ?
                    source_data_types_native[1] : 0;
                appended_data[3] = source_data_types_count > 2 ?
                    source_data_types_native[2] : 0;
            }
        }

        DASSERT(!JNU_IsNull(env, component));

        (*env)->CallStaticVoidMethod(env, clazz, dtcp_postDropTargetEventToPeer,
                                     component, x, y, dropAction,
                                     source_actions, source_data_types,
                                     ptr_to_jlong(copy), event_id);
    }
}

/******************************************************************************/

/********************* Embedded drop site list support ************************/

struct EmbeddedDropSiteListEntryRec;

typedef struct EmbeddedDropSiteListEntryRec EmbeddedDropSiteListEntry;

struct EmbeddedDropSiteListEntryRec {
    Window toplevel;
    Window root;
    /*
     * We select for PropertyNotify events on the toplevel, so we need to
     * restore the event mask when we are done with this toplevel.
     */
    long event_mask;
    unsigned int embedded_sites_count;
    Window* embedded_sites;
    EmbeddedDropSiteListEntry* next;
};

static EmbeddedDropSiteListEntry* embedded_drop_site_list = NULL;

struct EmbeddedDropSiteProtocolListEntryRec;

typedef struct EmbeddedDropSiteProtocolListEntryRec EmbeddedDropSiteProtocolListEntry;

struct EmbeddedDropSiteProtocolListEntryRec {
    Window window;
    Window proxy;
    /*
     * We override the XdndAware property on the toplevel, so we should keep its
     * original contents - the XDnD protocol version supported by the browser.
     * This is needed to adjust XDnD messages forwarded to the browser.
     */
    unsigned int protocol_version;
    /* True if the toplevel was already registered as a drag receiver and
       we just changed the proxy. False, otherwise */
    Boolean overriden;
    EmbeddedDropSiteProtocolListEntry* next;
};

static EmbeddedDropSiteProtocolListEntry* embedded_motif_protocol_list = NULL;
static EmbeddedDropSiteProtocolListEntry* embedded_xdnd_protocol_list = NULL;

typedef enum {
    RegFailure, /* Proxy registration failed */
    RegSuccess, /* The new drop site is registered with the new proxy */
    RegOverride, /* The new proxy is set for the existing drop site */
    RegAlreadyRegistered /* This proxy is already set for this drop site */
} ProxyRegistrationStatus;

/* Forward declarations. */
static EmbeddedDropSiteProtocolListEntry*
get_xdnd_protocol_entry_for_toplevel(Window toplevel);
static EmbeddedDropSiteProtocolListEntry*
get_motif_protocol_entry_for_toplevel(Window toplevel);
static void remove_xdnd_protocol_entry_for_toplevel(Window toplevel);
static void remove_motif_protocol_entry_for_toplevel(Window toplevel);

/*
 * Registers the toplevel as a Motif drag receiver if it is not registered yet,
 * sets the specified new_proxy for it and returns the previous proxy in old_proxy.
 * Does nothing if the new_proxy is already set as a proxy for this toplevel.
 * Returns the completion status.
 */
static ProxyRegistrationStatus
set_motif_proxy(Display* dpy, Window toplevel, Window new_proxy, Window *old_proxy) {
    Boolean        override = False;

    Atom           type;
    int            format;
    unsigned long  nitems;
    unsigned long  after;
    unsigned char* data;
    unsigned char  ret;

    DASSERT(old_proxy != NULL);

    *old_proxy = None;

    data = NULL;
    ret = checked_XGetWindowProperty(dpy, toplevel,
                                     _XA_MOTIF_DRAG_RECEIVER_INFO, 0, 0xFFFF,
                                     False, AnyPropertyType, &type, &format,
                                     &nitems, &after, &data);

    /* Check if toplevel is a valid window. */
    if (ret != Success) {
        return RegFailure;
    }

    if (ret == Success && data != NULL && type != None && format == 8
        && nitems >= MOTIF_RECEIVER_INFO_SIZE) {
        unsigned char byte_order = read_card8((char*)data, 0);
        void* p = (char*)data + 4;

        /* Browser and plugin have different byte orders - report failure for now. */
        if (MOTIF_BYTE_ORDER != byte_order) {
            XFree(data);
            return RegFailure;
        }

        *old_proxy = read_card32((char*)data, 4, byte_order);

        /* If the proxy is already set to the specified window - return. */
        if (*old_proxy == new_proxy) {
            XFree(data);
            return RegAlreadyRegistered;
        }

        /* replace the proxy window */
        write_card32(&p, new_proxy);

        override = True;
    } else {
        void* p;

        if (ret == Success) {
            XFree(data);
            data = NULL;
        }

        data = malloc(MOTIF_RECEIVER_INFO_SIZE);

        if (data == NULL) {
            DTRACE_PRINTLN2("%s:%d malloc failed.", __FILE__, __LINE__);
            return RegFailure;
        }

        p = data;

        write_card8(&p, MOTIF_BYTE_ORDER);
        write_card8(&p, MOTIF_DND_PROTOCOL_VERSION); /* protocol version */
        write_card8(&p, MOTIF_DYNAMIC_STYLE); /* protocol style */
        write_card8(&p, 0); /* pad */
        write_card32(&p, new_proxy); /* proxy window */
        write_card16(&p, 0); /* num_drop_sites */
        write_card16(&p, 0); /* pad */
        write_card32(&p, MOTIF_RECEIVER_INFO_SIZE);
    }

    ret = checked_XChangeProperty(dpy, toplevel,
                                  _XA_MOTIF_DRAG_RECEIVER_INFO,
                                  _XA_MOTIF_DRAG_RECEIVER_INFO, 8,
                                  PropModeReplace, (unsigned char*)data,
                                  MOTIF_RECEIVER_INFO_SIZE);

    if (data != NULL) {
        XFree(data);
        data = NULL;
    }

    if (ret == Success) {
        if (override) {
            return RegOverride;
        } else {
            return RegSuccess;
        }
    } else {
        return RegFailure;
    }
}

/*
 * Registers the toplevel as a XDnD drag receiver if it is not registered yet,
 * sets the specified new_proxy for it and returns the previous proxy in
 * old_proxy and the original XDnD protocol version in old_version.
 * Does nothing if the new_proxy is already set as a proxy for this toplevel.
 * Returns the completion status.
 */
static ProxyRegistrationStatus
set_xdnd_proxy(Display* dpy, Window toplevel, Window new_proxy,
               Window* old_proxy, unsigned int* old_version) {
    Atom version_atom = XDND_PROTOCOL_VERSION;
    Window xdnd_proxy = None;
    Boolean override = False;

    Atom           type;
    int            format;
    unsigned long  nitems;
    unsigned long  after;
    unsigned char* data;
    unsigned char  ret;

    DASSERT(old_proxy != NULL);

    *old_proxy = None;

    data = NULL;
    ret = checked_XGetWindowProperty(dpy, toplevel, XA_XdndAware, 0, 1,
                                     False, AnyPropertyType, &type, &format,
                                     &nitems, &after, &data);

    if (ret != Success) {
        return RegFailure;
    }

    if (ret == Success && data != NULL && type == XA_ATOM) {
        unsigned int protocol_version = *((unsigned int*)data);

        override = True;
        *old_version = protocol_version;

        /* XdndProxy is not supported for prior to XDnD version 4 */
        if (protocol_version >= 4) {
            int status;

            XFree(data);

            data = NULL;
            status = XGetWindowProperty(dpy, toplevel, XA_XdndProxy, 0, 1,
                                        False, XA_WINDOW, &type, &format,
                                        &nitems, &after, &data);

            if (status == Success && data != NULL && type == XA_WINDOW) {
                xdnd_proxy = *((Window*)data);

                if (xdnd_proxy != None) {
                    XFree(data);

                    data = NULL;
                    status = XGetWindowProperty(dpy, xdnd_proxy, XA_XdndProxy,
                                                0, 1, False, XA_WINDOW, &type,
                                                &format, &nitems, &after, &data);

                    if (status != Success || data == NULL || type != XA_WINDOW ||
                        *((Window*)data) != xdnd_proxy) {
                        /* Ignore invalid proxy. */
                        xdnd_proxy = None;
                    }
                }

                if (xdnd_proxy != None) {
                    XFree(data);

                    data = NULL;
                    status = XGetWindowProperty(dpy, xdnd_proxy, XA_XdndAware,
                                                0, 1, False, AnyPropertyType,
                                                &type, &format, &nitems, &after,
                                                &data);

                    if (status == Success && data != NULL && type == XA_ATOM) {
                        unsigned int proxy_version = *((unsigned int*)data);

                        if (proxy_version != protocol_version) {
                            /* Ignore invalid proxy. */
                            xdnd_proxy = None;
                        }
                    } else {
                        /* Ignore invalid proxy. */
                        xdnd_proxy = None;
                    }
                }
            }

            *old_proxy = xdnd_proxy;
        }
    }

    XFree(data);

    /* If the proxy is already set to the specified window - return. */
    if (xdnd_proxy == new_proxy) {
        return RegAlreadyRegistered;
    }

    /* The proxy window must have the XdndAware set, as XDnD protocol prescribes
       to check the proxy window for XdndAware. */
    ret = checked_XChangeProperty(dpy, new_proxy, XA_XdndAware, XA_ATOM, 32,
                                  PropModeReplace,
                                  (unsigned char*)&version_atom, 1);

    if (ret != Success) {
        return RegFailure;
    }

    /* The proxy window must have the XdndProxy set to point to itself. */
    ret = checked_XChangeProperty(dpy, new_proxy, XA_XdndProxy, XA_WINDOW, 32,
                                  PropModeReplace,
                                  (unsigned char*)&new_proxy, 1);

    if (ret != Success) {
        return RegFailure;
    }

    ret = checked_XChangeProperty(dpy, toplevel, XA_XdndAware, XA_ATOM, 32,
                                  PropModeReplace,
                                  (unsigned char*)&version_atom, 1);

    if (ret != Success) {
        return RegFailure;
    }

    ret = checked_XChangeProperty(dpy, toplevel, XA_XdndProxy, XA_WINDOW, 32,
                                  PropModeReplace,
                                  (unsigned char*)&new_proxy, 1);

    if (ret == Success) {
        if (override) {
            return RegOverride;
        } else {
            return RegSuccess;
        }
    } else {
        return RegFailure;
    }
}

/*
 * 'toplevel' is the browser toplevel window. To register a drop site on the
 * plugin window we set the proxy for the browser toplevel window to point to
 * the awt_root_shell window.
 *
 * We assume that only one JVM per browser instance is possible. This
 * assumption is true with the current plugin implementation - it creates a
 * single JVM for all plugin instances created by the given plugin factory.
 *
 * When a client message event for the browser toplevel window is received, we
 * will iterate over drop sites registered with this toplevel and determine if
 * the mouse pointer is currently over one of them (there could be several
 * plugin windows in one browser window - for example if an HTML page contains
 * frames and several frames contain a plugin object).
 *
 * If the pointer is not over any of the plugin drop sites the client message
 * will be resent to the browser, otherwise it will be processed normally.
 */
static EmbeddedDropSiteListEntry*
awt_dnd_dt_init_proxy(Display* dpy, Window root, Window toplevel, Window window) {
    Window         awt_root_window = get_awt_root_window();
    Window         motif_proxy = None;
    Boolean        motif_override = False;
    unsigned long  event_mask = 0;

    if (awt_root_window == None) {
        return NULL;
    }

    /* Grab server, since we are working with the window that belongs to
       another client. REMIND: ungrab when done!!! */
    XGrabServer(dpy);

    {
        ProxyRegistrationStatus motif_status = RegFailure;

        motif_status = set_motif_proxy(dpy, toplevel, awt_root_window, &motif_proxy);

        switch (motif_status) {
        case RegFailure:
        case RegAlreadyRegistered:
            XUngrabServer(dpy);
            /* Workaround for bug 5039226 */
            XSync(dpy, False);
            return NULL;
        case RegOverride:
            motif_override = True;
            break;
        case RegSuccess:
            motif_override = False;
            break;
        default:
            DASSERT(False);
        }


    }

    {
        XWindowAttributes xwa;
        XGetWindowAttributes(dpy, toplevel, &xwa);
        event_mask = xwa.your_event_mask;
        if ((event_mask & PropertyChangeMask) == 0) {
            XSelectInput(dpy, toplevel, event_mask | PropertyChangeMask);
        }
    }

    XUngrabServer(dpy);
    /* Workaround for bug 5039226 */
    XSync(dpy, False);

    /* Add protocol specific entries for the toplevel. */
    {
        EmbeddedDropSiteProtocolListEntry* motif_entry = NULL;

        motif_entry = malloc(sizeof(EmbeddedDropSiteProtocolListEntry));

        if (motif_entry == NULL) {
            return NULL;
        }

        motif_entry->window = toplevel;
        motif_entry->proxy = motif_proxy;
        motif_entry->protocol_version = 0;
        motif_entry->overriden = motif_override;
        motif_entry->next = embedded_motif_protocol_list;
        embedded_motif_protocol_list = motif_entry;
    }

    {
        EmbeddedDropSiteListEntry* entry = NULL;
        Window* sites = NULL;

        entry = malloc(sizeof(EmbeddedDropSiteListEntry));

        if (entry == NULL) {
            return NULL;
        }

        sites = malloc(sizeof(Window));

        if (sites == NULL) {
            free(entry);
            return NULL;
        }

        sites[0] = window;

        entry->toplevel = toplevel;
        entry->root = root;
        entry->event_mask = event_mask;
        entry->embedded_sites_count = 1;
        entry->embedded_sites = sites;
        entry->next = NULL;

        return entry;
    }
}

static void
register_xdnd_embedder(Display* dpy, EmbeddedDropSiteListEntry* entry, long window) {
    Window         awt_root_window = get_awt_root_window();
    Window         toplevel = entry->toplevel;
    Window         xdnd_proxy = None;
    unsigned int   xdnd_protocol_version = 0;
    Boolean        xdnd_override = False;
    Boolean        register_xdnd = True;
    Boolean        motif_overriden = False;

    EmbeddedDropSiteProtocolListEntry* motif_entry = embedded_motif_protocol_list;
    while (motif_entry != NULL) {
        if (motif_entry->window == toplevel) {
            motif_overriden = motif_entry->overriden;
            break;
        }
        motif_entry = motif_entry->next;
    }

    /*
     * First check if the window is an XEmbed client.
     * In this case we don't have to setup a proxy on the toplevel,
     * instead we register the XDnD drop site on the embedded window.
     */
    if (isXEmbedActiveByWindow(window)) {
        register_xdnd_drop_site(dpy, toplevel, window);
        return;
    }

    /*
     * By default, we register a drop site that supports both dnd
     * protocols. This approach is not appropriate in plugin
     * scenario if the browser doesn't support XDnD. If we forcibly set
     * XdndAware on the browser toplevel, any drag source that supports both
     * protocols and prefers XDnD will be unable to drop anything on the
     * browser.
     * The solution for this problem is not to register XDnD drop site
     * if the browser supports only Motif DnD.
     */
    if (motif_overriden) {
        int            status;
        Atom           type;
        int            format;
        unsigned long  nitems;
        unsigned long  after;
        unsigned char* data;

        data = NULL;
        status = XGetWindowProperty(dpy, toplevel, XA_XdndAware, 0, 1,
                                    False, AnyPropertyType, &type, &format,
                                    &nitems, &after, &data);

        XFree(data);
        data = NULL;

        if (type != XA_ATOM) {
            register_xdnd = False;
        }
    }

    if (register_xdnd) {
        ProxyRegistrationStatus xdnd_status;
        /* Grab server, since we are working with the window that belongs to
           another client. REMIND: ungrab when done!!! */
        XGrabServer(dpy);

        xdnd_status =
            set_xdnd_proxy(dpy, toplevel, awt_root_window, &xdnd_proxy,
                           &xdnd_protocol_version);

        XUngrabServer(dpy);

        switch (xdnd_status) {
        case RegFailure:
        case RegAlreadyRegistered:
            return;
        case RegOverride:
            xdnd_override = True;
            break;
        case RegSuccess:
            xdnd_override = False;
            break;
        default:
            DASSERT(False);
        }

        {
            EmbeddedDropSiteProtocolListEntry* xdnd_entry = NULL;

            xdnd_entry = malloc(sizeof(EmbeddedDropSiteProtocolListEntry));

            if (xdnd_entry == NULL) {
                return;
            }

            xdnd_entry->window = toplevel;
            xdnd_entry->proxy = xdnd_proxy;
            xdnd_entry->protocol_version = xdnd_protocol_version;
            xdnd_entry->overriden = xdnd_override;
            xdnd_entry->next = embedded_xdnd_protocol_list;
            embedded_xdnd_protocol_list = xdnd_entry;
        }
    }
}

/*
 * If embedded_drop_site_list already contains an entry with the specified
 * 'toplevel', the method registers the specified 'window' as an embedded drop
 * site for this 'toplevel' and returns True.
 * Otherwise, it checks if the 'toplevel' is a registered drop site for adds
 * (window, component) pair to the list and returns True
 * if completes successfully.
 */
static Boolean
add_to_embedded_drop_site_list(Display* dpy, Window root, Window toplevel,
                               Window window) {
    EmbeddedDropSiteListEntry* entry = embedded_drop_site_list;

    while (entry != NULL) {
        if (entry->toplevel == toplevel) {
            void* p = realloc(entry->embedded_sites,
                              sizeof(Window) *
                              (entry->embedded_sites_count + 1));
            if (p == NULL) {
                return False;
            }
            entry->embedded_sites = p;
            entry->embedded_sites[entry->embedded_sites_count++] = window;

            register_xdnd_embedder(dpy, entry, window);

            return True;
        }
        entry = entry->next;
    }

    entry = awt_dnd_dt_init_proxy(dpy, root, toplevel, window);

    if (entry == NULL) {
        return False;
    }

    register_xdnd_embedder(dpy, entry, window);

    entry->next = embedded_drop_site_list;
    embedded_drop_site_list = entry;

    return True;
}

/*
 * Removes the window from the list of embedded drop sites for the toplevel.
 * Returns True if the window was successfully removed, False otherwise.
 */
static Boolean
remove_from_embedded_drop_site_list(Display* dpy, Window toplevel, Window window) {
    EmbeddedDropSiteListEntry* entry = embedded_drop_site_list;
    EmbeddedDropSiteListEntry* prev = NULL;

    while (entry != NULL) {
        if (entry->toplevel == toplevel) {
            unsigned int idx;

            for (idx = 0; idx < entry->embedded_sites_count; idx++) {
                if (entry->embedded_sites[idx] == window) {
                    int tail = entry->embedded_sites_count - idx - 1;
                    if (tail > 0) {
                        memmove(entry->embedded_sites + idx,
                                entry->embedded_sites + idx + 1,
                                tail * sizeof(Window));
                    }
                    entry->embedded_sites_count--;

                    /* If the list of embedded drop sites for this toplevel
                       becomes empty - restore the original proxies and remove
                       the entry. */
                    if (entry->embedded_sites_count == 0) {
                        Widget w = XtWindowToWidget(dpy, toplevel);

                        if (w != NULL) {
                            JNIEnv *env = (JNIEnv*)JNU_GetEnv(jvm, JNI_VERSION_1_4);
                            Widget copy = w;
                            jobject peer = findPeer(&w);

                            if (!JNU_IsNull(env, peer) &&
                                (*env)->IsInstanceOf(env, peer,
                                                     get_MEmbedCanvasPeerClass(env)) == JNI_TRUE) {
                                remove_xembed_drop_target(env, peer);
                            }
                        } else {
                            EmbeddedDropSiteProtocolListEntry* xdnd_entry =
                                get_xdnd_protocol_entry_for_toplevel(toplevel);
                            EmbeddedDropSiteProtocolListEntry* motif_entry =
                                get_motif_protocol_entry_for_toplevel(toplevel);

                            if (xdnd_entry != NULL) {
                                if (xdnd_entry->overriden == True) {
                                    XChangeProperty(dpy, toplevel, XA_XdndAware,
                                                    XA_ATOM, 32,
                                                    PropModeReplace,
                                                    (unsigned char*)&xdnd_entry->protocol_version,
                                                    1);

                                    XChangeProperty(dpy, toplevel, XA_XdndProxy,
                                                    XA_WINDOW, 32,
                                                    PropModeReplace,
                                                    (unsigned char*)&xdnd_entry->proxy, 1);
                                } else {
                                    XDeleteProperty(dpy, toplevel, XA_XdndAware);
                                    XDeleteProperty(dpy, toplevel, XA_XdndProxy);
                                }
                                remove_xdnd_protocol_entry_for_toplevel(toplevel);
                            }

                            if (motif_entry != NULL) {
                                if (motif_entry->overriden == True) {
                                    /* Request status */
                                    int status;

                                    Atom           type;
                                    int            format;
                                    unsigned long  nitems;
                                    unsigned long  after;
                                    unsigned char* data;

                                    data = NULL;
                                    status = XGetWindowProperty(dpy, toplevel,
                                                                _XA_MOTIF_DRAG_RECEIVER_INFO, 0, 0xFFFF,
                                                                False, AnyPropertyType, &type, &format,
                                                                &nitems, &after, &data);

                                    if (status == Success && data != NULL && type != None &&
                                        format == 8 && nitems >= MOTIF_RECEIVER_INFO_SIZE) {
                                        unsigned char byte_order = read_card8((char*)data, 0);
                                        void* p = (char*)data + 4;

                                        DASSERT(MOTIF_BYTE_ORDER == byte_order);

                                        if (MOTIF_BYTE_ORDER == byte_order) {
                                            /* restore the original proxy window */
                                            write_card32(&p, motif_entry->proxy);

                                            XChangeProperty(dpy, toplevel,
                                                            _XA_MOTIF_DRAG_RECEIVER_INFO,
                                                            _XA_MOTIF_DRAG_RECEIVER_INFO, 8,
                                                            PropModeReplace,
                                                            (unsigned char*)data,
                                                            MOTIF_RECEIVER_INFO_SIZE);
                                        }
                                    }

                                    if (status == Success) {
                                        XFree(data);
                                    }
                                } else {
                                    XDeleteProperty(dpy, toplevel, _XA_MOTIF_DRAG_RECEIVER_INFO);
                                }

                                remove_motif_protocol_entry_for_toplevel(toplevel);
                            }

                            if ((entry->event_mask & PropertyChangeMask) == 0) {
                                XSelectInput(dpy, toplevel, entry->event_mask);
                            }
                        }

                        if (prev == NULL) {
                            embedded_drop_site_list = entry->next;
                        } else {
                            prev->next = entry->next;
                        }

                        free(entry);
                    }
                    return True;
                }
            }
            return False;
        }
        prev = entry;
        entry = entry->next;
    }
    return False;
}

static EmbeddedDropSiteListEntry*
get_entry_for_toplevel(Window toplevel) {
    EmbeddedDropSiteListEntry* entry = embedded_drop_site_list;

    while (entry != NULL) {
        if (entry->toplevel == toplevel) {
            return entry;
        }
        entry = entry->next;
    }
    return NULL;
}

static EmbeddedDropSiteProtocolListEntry*
get_motif_protocol_entry_for_toplevel(Window toplevel) {
    EmbeddedDropSiteProtocolListEntry* entry = embedded_motif_protocol_list;

    while (entry != NULL) {
        if (entry->window == toplevel) {
            return entry;
        }
        entry = entry->next;
    }
    return NULL;
}

static EmbeddedDropSiteProtocolListEntry*
get_xdnd_protocol_entry_for_toplevel(Window toplevel) {
    EmbeddedDropSiteProtocolListEntry* entry = embedded_xdnd_protocol_list;

    while (entry != NULL) {
        if (entry->window == toplevel) {
            return entry;
        }
        entry = entry->next;
    }
    return NULL;
}

static void
remove_motif_protocol_entry_for_toplevel(Window toplevel) {
    EmbeddedDropSiteProtocolListEntry* entry = embedded_motif_protocol_list;
    EmbeddedDropSiteProtocolListEntry* prev_entry = NULL;

    while (entry != NULL) {
        if (entry->window == toplevel) {
            if (prev_entry != NULL) {
                prev_entry->next = entry->next;
            } else {
                embedded_motif_protocol_list = entry->next;
            }
            free(entry);
        }
        entry = entry->next;
        prev_entry = entry;
    }
}

static void
remove_xdnd_protocol_entry_for_toplevel(Window toplevel) {
    EmbeddedDropSiteProtocolListEntry* entry = embedded_xdnd_protocol_list;
    EmbeddedDropSiteProtocolListEntry* prev_entry = NULL;

    while (entry != NULL) {
        if (entry->window == toplevel) {
            if (prev_entry != NULL) {
                prev_entry->next = entry->next;
            } else {
                embedded_xdnd_protocol_list = entry->next;
            }
            free(entry);
        }
        entry = entry->next;
    }
}

static Boolean
is_embedding_toplevel(Window toplevel) {
    return get_entry_for_toplevel(toplevel) != NULL;
}

static Window
get_embedded_window(Display* dpy, Window toplevel, int x, int y) {
    EmbeddedDropSiteListEntry* entry = get_entry_for_toplevel(toplevel);

    if (entry != NULL) {
        unsigned int idx;

        for (idx = 0; idx < entry->embedded_sites_count; idx++) {
            Window site = entry->embedded_sites[idx];
            Window child = None;
            int x_return, y_return;

            if (XTranslateCoordinates(dpy, entry->root, site, x, y,
                                      &x_return, &y_return, &child)) {
                if (x_return >= 0 && y_return >= 0) {
                    XWindowAttributes xwa;
                    XGetWindowAttributes(dpy, site, &xwa);
                    if (xwa.map_state != IsUnmapped &&
                        x_return < xwa.width && y_return < xwa.height) {
                        return site;
                    }
                }
            }
        }
    }

    return None;
}

/*
 * If the toplevel is not an embedding toplevel does nothing and returns False.
 * Otherwise, sets xdnd_proxy for the specified toplevel to the 'proxy_window',
 * xdnd_protocol_version to 'version', xdnd_override to 'override', returns True.
 */
static Boolean
set_xdnd_proxy_for_toplevel(Window toplevel, Window proxy_window,
                            unsigned int version, Boolean override) {
    EmbeddedDropSiteProtocolListEntry* entry =
        get_xdnd_protocol_entry_for_toplevel(toplevel);

    if (entry == NULL) {
        return False;
    }

    entry->proxy = proxy_window;
    entry->protocol_version = version;
    entry->overriden = override;

    return True;
}

/*
 * If the toplevel is not an embedding toplevel does nothing and returns False.
 * Otherwise, sets motif_proxy for the specified toplevel to the proxy_window,
 * motif_override to 'override' and returns True.
 */
static Boolean
set_motif_proxy_for_toplevel(Window toplevel, Window proxy_window, Boolean override) {
    EmbeddedDropSiteProtocolListEntry* entry =
        get_motif_protocol_entry_for_toplevel(toplevel);

    if (entry == NULL) {
        return False;
    }

    entry->proxy = proxy_window;
    entry->overriden = override;

    return True;
}

/*
 * Forwards a drag notification to the embedding toplevel modifying the event
 * to match the protocol version supported by the toplevel.
 * Returns True if the event is sent, False otherwise.
 */
static Boolean
forward_client_message_to_toplevel(Window toplevel, XClientMessageEvent* event) {
    EmbeddedDropSiteProtocolListEntry* protocol_entry = NULL;
    Window proxy = None;

    if (event->message_type == _XA_MOTIF_DRAG_AND_DROP_MESSAGE) {
        protocol_entry = get_motif_protocol_entry_for_toplevel(toplevel);
    } else {
        /* Assume XDnD */
        protocol_entry = get_xdnd_protocol_entry_for_toplevel(toplevel);
        if (protocol_entry != NULL) {
            /* Adjust the event to match the XDnD protocol version. */
            unsigned int version = protocol_entry->protocol_version;
            if (event->message_type == XA_XdndEnter) {
                unsigned int min_version = source_protocol_version < version ?
                    source_protocol_version : version;
                event->data.l[1] = min_version << XDND_PROTOCOL_SHIFT;
                event->data.l[1] |= source_data_types_count > 3 ? XDND_DATA_TYPES_BIT : 0;
            }
        }
    }

    if (protocol_entry == NULL) {
        return False;
    }

    if (!protocol_entry->overriden) {
        return False;
    }
    proxy = protocol_entry->proxy;

    if (proxy == None) {
        proxy = toplevel;
    }

    event->window = toplevel;

    XSendEvent(event->display, proxy, False, NoEventMask, (XEvent*)event);

    return True;
}

/******************************************************************************/

/********************* Drop site list support *********************************/

struct DropSiteListEntryRec;

typedef struct DropSiteListEntryRec DropSiteListEntry;

struct DropSiteListEntryRec {
    Window             window;
    Window             root;
    /*
     * The closest to the root ancestor with WM_STATE property set.
     * Normally toplevel == window.
     * In plugin scenario toplevel is the browser toplevel window.
     */
    Window             toplevel;
    /*
     * Java top-level position is the outer canvas position, not the shell
     * window position. We need to keep the outer canvas ID (and the root ID) to
     * translate from mouse position root coordinates to the Java component
     * coordinates.
     */
    Window             outer_canvas;
    jobject            component;
    DropSiteListEntry* next;
};

static DropSiteListEntry* drop_site_list = NULL;

/*
 * If drop_site_list already contains an entry with the same window,
 * does nothing and returns False.
 * Otherwise, adds a new entry the list and returns True
 * if completes successfully.
 */
static Boolean
add_to_drop_site_list(Window window, Window root, Window toplevel,
                      Window outer_canvas, jobject component) {
    DropSiteListEntry* entry = drop_site_list;

    while (entry != NULL) {
        if (entry->window == window) {
            return False;
        }
        entry = entry->next;
    }

    entry = malloc(sizeof(DropSiteListEntry));

    if (entry == NULL) {
        return False;
    }

    entry->window = window;
    entry->root = root;
    entry->toplevel = toplevel;
    entry->outer_canvas = outer_canvas;
    entry->component = component;
    entry->next = drop_site_list;
    drop_site_list = entry;

    return True;
}

/*
 * Returns True if the list entry for the specified window has been successfully
 * removed from the list. Otherwise, returns False.
 */
static Boolean
remove_from_drop_site_list(Window window) {
    DropSiteListEntry* entry = drop_site_list;
    DropSiteListEntry* prev = NULL;

    while (entry != NULL) {
        if (entry->window == window) {
            if (prev != NULL) {
                prev->next = entry->next;
            } else {
                drop_site_list = entry->next;
            }
            free(entry);
            return True;
        }
        prev = entry;
        entry = entry->next;
    }

    return False;
}

static jobject
get_component_for_window(Window window) {
    DropSiteListEntry* entry = drop_site_list;

    while (entry != NULL) {
        if (entry->window == window) {
            return entry->component;
        }
        entry = entry->next;
    }

    return NULL;
}

static Window
get_root_for_window(Window window) {
    DropSiteListEntry* entry = drop_site_list;

    while (entry != NULL) {
        if (entry->window == window) {
            return entry->root;
        }
        entry = entry->next;
    }

    return None;
}

static Window
get_toplevel_for_window(Window window) {
    DropSiteListEntry* entry = drop_site_list;

    while (entry != NULL) {
        if (entry->window == window) {
            return entry->toplevel;
        }
        entry = entry->next;
    }

    return None;
}

static Window
get_outer_canvas_for_window(Window window) {
    DropSiteListEntry* entry = drop_site_list;

    while (entry != NULL) {
        if (entry->window == window) {
            return entry->outer_canvas;
        }
        entry = entry->next;
    }

    return None;
}
/******************************************************************************/

/******************* Delayed drop site registration stuff *********************/
struct DelayedRegistrationEntryRec;

typedef struct DelayedRegistrationEntryRec DelayedRegistrationEntry;

struct DelayedRegistrationEntryRec {
    Widget outer_canvas;
    jobject component;
    XtIntervalId timer;
    DelayedRegistrationEntry* next;
};

static DelayedRegistrationEntry* delayed_registration_list = NULL;

static const int DELAYED_REGISTRATION_PERIOD = 500;

/* Timer callback. */
static void
register_drop_site_later(XtPointer client_data, XtIntervalId* id);

/*
 * Enqueues the specified widget and component for delayed drop site
 * registration. If this widget has already been registered, does nothing and
 * returns False. Otherwise, schedules a timer callback that will repeatedly
 * attempt to register the drop site until the registration succeeds.
 * To remove this widget from the queue of delayed registration call
 * remove_delayed_registration_entry().
 *
 * The caller must own AWT_LOCK.
 */
static Boolean
add_delayed_registration_entry(Widget outer_canvas, XtPointer componentRef) {
    DelayedRegistrationEntry* entry = delayed_registration_list;

    if (outer_canvas == NULL || componentRef == NULL) {
        return False;
    }

    while (entry != NULL && entry->outer_canvas != outer_canvas) {
        entry = entry->next;
    }

    if (entry != NULL) {
        return False;
    }

    entry = malloc(sizeof(DelayedRegistrationEntry));

    if (entry == NULL) {
        return False;
    }

    entry->outer_canvas = outer_canvas;
    entry->component = componentRef;
    entry->timer = XtAppAddTimeOut(awt_appContext, DELAYED_REGISTRATION_PERIOD,
                                   register_drop_site_later, entry);
    entry->next = delayed_registration_list;
    delayed_registration_list = entry;

    return True;
}

/*
 * Unregisters the timer callback and removes this widget from the queue of
 * delayed drop site registration.
 *
 * The caller must own AWT_LOCK.
 */
static Boolean
remove_delayed_registration_entry(Widget outer_canvas) {
    DelayedRegistrationEntry* entry = delayed_registration_list;
    DelayedRegistrationEntry* prev = NULL;

    if (outer_canvas == NULL) {
        return False;
    }

    while (entry != NULL && entry->outer_canvas != outer_canvas) {
        prev = entry;
        entry = entry->next;
    }

    if (entry == NULL) {
        return False;
    }

    if (prev != NULL) {
        prev->next = entry->next;
    } else {
        delayed_registration_list = entry->next;
    }

    if (entry->timer) {
        XtRemoveTimeOut(entry->timer);
        entry->timer = (XtIntervalId)0;
    }

    free(entry);

    return True;
}

static void
register_drop_site_later(XtPointer client_data, XtIntervalId* id) {
    DelayedRegistrationEntry* entry = (DelayedRegistrationEntry*)client_data;

    if (XtIsRealized(entry->outer_canvas) &&
        register_drop_site(entry->outer_canvas, entry->component)) {
        remove_delayed_registration_entry(entry->outer_canvas);
    } else {
        entry->timer = XtAppAddTimeOut(awt_appContext, DELAYED_REGISTRATION_PERIOD,
                                       register_drop_site_later, entry);
    }
}
/******************************************************************************/

static void
awt_dnd_cleanup() {
    JNIEnv *env = (JNIEnv*)JNU_GetEnv(jvm, JNI_VERSION_1_4);

    if (!JNU_IsNull(env, target_component)) {
        /* Trigger dragExit */
        /*
         * Note: we pass NULL native context. This indicates that response
         * shouldn't be sent to the source.
         */
        dt_postDropTargetEvent(env, target_component, 0, 0,
                               java_awt_dnd_DnDConstants_ACTION_NONE,
                               java_awt_event_MouseEvent_MOUSE_EXITED,
                               NULL);
    }

    if (motif_top_level_leave_postponed) {
        XClientMessageEvent* leave = &motif_top_level_leave_postponed_event;
        if (leave->type == ClientMessage) {
            Window win = leave->window;
            if (is_embedding_toplevel(win)) {
                forward_client_message_to_toplevel(win, leave);
            }
        }
    }

    if (source_window != None) {
        XSelectInput(awt_display, source_window, source_window_mask);
    }

    source_protocol = NO_PROTOCOL;
    source_protocol_version = 0;
    source_window = None;
    source_atom = None;
    source_window_mask = 0;
    source_actions = java_awt_dnd_DnDConstants_ACTION_NONE;
    track_source_actions = False;
    (*env)->DeleteGlobalRef(env, source_data_types);
    source_data_types = NULL;
    if (source_data_types_native != NULL) {
        free(source_data_types_native);
        source_data_types_native = NULL;
    }
    source_data_types_count = 0;
    source_x = 0;
    source_y = 0;
    target_component = NULL;
    motif_top_level_leave_postponed = False;
    memset(&motif_top_level_leave_postponed_event, 0,
           sizeof(XClientMessageEvent));
}

static jlongArray
get_data_types_array(JNIEnv* env, Atom* types, unsigned int types_count) {
    jlongArray array = NULL;
    jboolean isCopy;
    jlong*   jTargets;
#ifndef _LP64 /* Atom and jlong are different sizes in the 32-bit build */
    unsigned int i;
#endif

    if ((*env)->PushLocalFrame(env, 1) < 0) {
        return NULL;
    }

    array = (*env)->NewLongArray(env, types_count);

    if (JNU_IsNull(env, array)) {
        return NULL;
    }

    if (types_count == 0) {
        return array;
    }

    jTargets = (*env)->GetLongArrayElements(env, array, &isCopy);
    if (jTargets == NULL) {
        (*env)->PopLocalFrame(env, NULL);
        return NULL;
    }

#ifdef _LP64
    memcpy(jTargets, types, types_count * sizeof(Atom));
#else
    for (i = 0; i < types_count; i++) {
        jTargets[i] = (types[i] & 0xFFFFFFFFLU);
    }
#endif

    (*env)->ReleaseLongArrayElements(env, array, jTargets, 0);

    array = (*env)->NewGlobalRef(env, array);

    (*env)->PopLocalFrame(env, NULL);

    return array;
}

static Boolean
is_xdnd_drag_message_type(unsigned long message_type) {
    return message_type == XA_XdndEnter ||
        message_type == XA_XdndPosition ||
        message_type == XA_XdndLeave ||
        message_type == XA_XdndDrop ? True : False;
}

/*
 * Returns EventConsume if the event should be consumed,
 * EventPassAlong otherwise.
 */
static EventStatus
handle_xdnd_enter(XClientMessageEvent* event) {
    JNIEnv *env = (JNIEnv*)JNU_GetEnv(jvm, JNI_VERSION_1_4);
    Display* dpy = event->display;
    long* event_data = event->data.l;
    Window source_win = None;
    long source_win_mask = 0;
    unsigned int protocol_version = 0;
    unsigned int data_types_count = 0;
    Atom* data_types = NULL;
    jlongArray java_data_types = NULL;
    jint actions = java_awt_dnd_DnDConstants_ACTION_NONE;
    Boolean track = False;

    DTRACE_PRINTLN5("%s:%d XdndEnter comp=%X src_win=%ld protocol=%d.",
                    __FILE__, __LINE__,
                    target_component, source_window, source_protocol);

    if (!JNU_IsNull(env, target_component) || source_window != None ||
        source_protocol != NO_PROTOCOL) {
        DTRACE_PRINTLN2("%s:%d XdndEnter rejected - invalid state.",
                        __FILE__, __LINE__);
        return EventFailure;
    }

    /*
     * NOTE: the component can be NULL if the event was sent to the embedding
     * toplevel.
     */
    if (JNU_IsNull(env, get_component_for_window(event->window)) &&
        !is_embedding_toplevel(event->window)) {
        DTRACE_PRINTLN2("%s:%d XdndEnter rejected - window is not a registered drop site.",
                        __FILE__, __LINE__);
        return EventFailure;
    }

    protocol_version =
        (event_data[1] & XDND_PROTOCOL_MASK) >> XDND_PROTOCOL_SHIFT;

    /* XDnD compliance only requires supporting version 3 and up. */
    if (protocol_version < XDND_MIN_PROTOCOL_VERSION) {
        DTRACE_PRINTLN2("%s:%d XdndEnter rejected - invalid protocol version.",
                        __FILE__, __LINE__);
        return EventFailure;
    }

    /* Ignore the source if the protocol version is higher than we support. */
    if (protocol_version > XDND_PROTOCOL_VERSION) {
        DTRACE_PRINTLN2("%s:%d XdndEnter rejected - invalid protocol version.",
                        __FILE__, __LINE__);
        return EventFailure;
    }

    source_win = event_data[0];

    /* Extract the list of supported actions. */
    if (protocol_version < 2) {
        /* Prior to XDnD version 2 only COPY action was supported. */
        actions = java_awt_dnd_DnDConstants_ACTION_COPY;
    } else {
        unsigned char  ret;
        Atom           type;
        int            format;
        unsigned long  nitems;
        unsigned long  after;
        unsigned char  *data;

        data = NULL;
        ret = checked_XGetWindowProperty(dpy, source_win, XA_XdndActionList,
                                         0, 0xFFFF, False, XA_ATOM, &type,
                                         &format, &nitems, &after, &data);

        /* Ignore the source if the window is destroyed. */
        if (ret == BadWindow) {
            DTRACE_PRINTLN2("%s:%d XdndEnter rejected - invalid window.",
                            __FILE__, __LINE__);
            return EventFailure;
        }

        if (ret == Success) {
            if (type == XA_ATOM && format == 32) {
                unsigned int i;
                Atom* action_atoms = (Atom*)data;

                for (i = 0; i < nitems; i++) {
                    actions |= xdnd_to_java_action(action_atoms[i]);
                }
            }

            /*
             * According to XDnD protocol, XdndActionList is optional.
             * If XdndActionList is not set we try to guess which actions are
             * supported.
             */
            if (type == None) {
                actions = java_awt_dnd_DnDConstants_ACTION_COPY;
                track = True;
            }

            XFree(data);
        }
    }

    /* Extract the available data types. */
    if (event_data[1] & XDND_DATA_TYPES_BIT) {
        unsigned char  ret;
        Atom           type;
        int            format;
        unsigned long  nitems;
        unsigned long  after;
        unsigned char  *data;

        data = NULL;
        ret = checked_XGetWindowProperty(dpy, source_win, XA_XdndTypeList,
                                         0, 0xFFFF, False, XA_ATOM, &type,
                                         &format, &nitems, &after, &data);

        /* Ignore the source if the window is destroyed. */
        if (ret == BadWindow) {
            DTRACE_PRINTLN2("%s:%d XdndEnter rejected - invalid window.",
                            __FILE__, __LINE__);
            return EventFailure;
        }

        if (ret == Success) {
            if (type == XA_ATOM && format == 32 && nitems > 0) {
                data_types_count = nitems;
                data_types = (Atom*)malloc(data_types_count * sizeof(Atom));

                if (data_types == NULL) {
                    XFree(data);
                    DTRACE_PRINTLN2("%s:%d XdndEnter rejected - malloc fails.",
                                    __FILE__, __LINE__);
                    return EventFailure;
                }

                memcpy((void *)data_types, (void *)data,
                       data_types_count * sizeof(Atom));
            }

            XFree(data);
        }
    } else {
        int i;
        data_types = (Atom*)malloc(3 * sizeof (Atom));
        if (data_types == NULL) {
            DTRACE_PRINTLN2("%s:%d XdndEnter rejected - malloc fails.",
                            __FILE__, __LINE__);
            return EventFailure;
        }
        for (i = 0; i < 3; i++) {
            Atom j;
            if ((j = event_data[2 + i]) != None) {
                data_types[data_types_count++] = j;
            }
        }
    }

    java_data_types = get_data_types_array(env, data_types, data_types_count);

    if (JNU_IsNull(env, java_data_types)) {
        DTRACE_PRINTLN2("%s:%d XdndEnter rejected - cannot create types array.",
                        __FILE__, __LINE__);
        free((char*)data_types);
        return EventFailure;
    }

    /*
     * Select for StructureNotifyMask to receive DestroyNotify in case of source
     * crash.
     */
    {
        unsigned char ret;
        XWindowAttributes xwa;

        XGetWindowAttributes(dpy, source_win, &xwa);

        source_win_mask = xwa.your_event_mask;

        ret = checked_XSelectInput(dpy, source_win,
                                   (source_win_mask | StructureNotifyMask));

        if (ret == BadWindow) {
            DTRACE_PRINTLN2("%s:%d XdndEnter rejected - invalid window.",
                            __FILE__, __LINE__);
            free((char*)data_types);
            (*env)->DeleteGlobalRef(env, java_data_types);
            return EventFailure;
        }
    }

    /* Update the global state. */
    source_protocol = XDND_PROTOCOL;
    source_protocol_version = protocol_version;
    source_window = source_win;
    source_window_mask = source_win_mask;
    source_actions = actions;
    track_source_actions = track;
    source_data_types = java_data_types;
    source_data_types_native = data_types;
    source_data_types_count = data_types_count;

    DTRACE_PRINTLN5("%s:%d XdndEnter handled src_win=%ld protocol=%d fmt=%d.",
                    __FILE__, __LINE__,
                    source_window, source_protocol, data_types_count);

    return EventSuccess;
}

/*
 * Returns EventConsume if the event should be consumed,
 * EventPassAlong otherwise.
 */
static EventStatus
handle_xdnd_position(XClientMessageEvent* event) {
    JNIEnv *env = (JNIEnv*)JNU_GetEnv(jvm, JNI_VERSION_1_4);
    long* event_data = event->data.l;
    Window source_win = None;
    Time time_stamp = CurrentTime;
    Atom action_atom = None;
    jint action = java_awt_dnd_DnDConstants_ACTION_NONE;
    int x = 0;
    int y = 0;
    jint java_event_id = 0;
    jobject component = NULL;
    Window receiver = None;

    DTRACE_PRINTLN5("%s:%d XdndPosition comp=%X src_win=%ld protocol=%d.",
                    __FILE__, __LINE__,
                    target_component, source_window, source_protocol);

    if (source_protocol != XDND_PROTOCOL) {
        DTRACE_PRINTLN2("%s:%d XdndPosition rejected - invalid state.",
                        __FILE__, __LINE__);
        return EventFailure;
    }

    source_win = event_data[0];

    /* Ignore XDnD messages from all other windows. */
    if (source_window != source_win) {
        DTRACE_PRINTLN4("%s:%d XdndPosition rejected - invalid source window cur=%ld this=%ld.",
                        __FILE__, __LINE__, source_window, source_win);
        return EventFailure;
    }

    x = event_data[2] >> 16;
    y = event_data[2] & 0xFFFF;

    component = get_component_for_window(event->window);

    if (JNU_IsNull(env, component)) {
        /*
         * The window must be the embedding toplevel, since otherwise we would reject the
         * XdndEnter and never get to this point.
         */
        DASSERT(is_embedding_toplevel(event->window));

        receiver = get_embedded_window(event->display, event->window, x, y);

        if (receiver != None) {
            component = get_component_for_window(receiver);
        }
    } else {
        receiver = event->window;
    }

    /* Translate mouse position from root coordinates
       to the target window coordinates. */
    if (receiver != None) {
        Window child = None;
        XTranslateCoordinates(event->display,
                              get_root_for_window(receiver),
                              get_outer_canvas_for_window(receiver),
                              x, y, &x, &y, &child);
    }

    /* Time stamp - new in XDnD version 1. */
    if (source_protocol_version > 0) {
        time_stamp = event_data[3];
    }

    /* User action - new in XDnD version 1. */
    if (source_protocol_version > 1) {
        action_atom = event_data[4];
    } else {
        /* The default action is XdndActionCopy */
        action_atom = XA_XdndActionCopy;
    }

    action = xdnd_to_java_action(action_atom);

    if (track_source_actions) {
        source_actions |= action;
    }

    if (JNU_IsNull(env, component)) {
        if (!JNU_IsNull(env, target_component)) {
            dt_postDropTargetEvent(env, target_component, x, y,
                                   java_awt_dnd_DnDConstants_ACTION_NONE,
                                   java_awt_event_MouseEvent_MOUSE_EXITED,
                                   NULL);
        }
    } else {
        if (JNU_IsNull(env, target_component)) {
            java_event_id = java_awt_event_MouseEvent_MOUSE_ENTERED;
        } else {
            java_event_id = java_awt_event_MouseEvent_MOUSE_DRAGGED;
        }

        dt_postDropTargetEvent(env, component, x, y, action,
                               java_event_id, event);
    }

    user_action = action;
    source_x = x;
    source_y = y;
    target_component = component;

    return EventSuccess;
}

/*
 * Returns EventConsume if the event should be consumed,
 * EventPassAlong otherwise.
 */
static EventStatus
handle_xdnd_leave(XClientMessageEvent* event) {
    JNIEnv *env = (JNIEnv*)JNU_GetEnv(jvm, JNI_VERSION_1_4);
    long* event_data = event->data.l;
    Window source_win = None;

    if (source_protocol != XDND_PROTOCOL) {
        DTRACE_PRINTLN2("%s:%d XdndLeave rejected - invalid state.",
                        __FILE__, __LINE__);
        return EventFailure;
    }

    source_win = event_data[0];

    /* Ignore XDnD messages from all other windows. */
    if (source_window != source_win) {
        DTRACE_PRINTLN4("%s:%d XdndLeave rejected - invalid source window cur=%ld this=%ld.",
                        __FILE__, __LINE__, source_window, source_win);
        return EventFailure;
    }

    awt_dnd_cleanup();

    return EventSuccess;
}

/*
 * Returns EventConsume if the event should be consumed,
 * EventPassAlong otherwise.
 */
static EventStatus
handle_xdnd_drop(XClientMessageEvent* event) {
    JNIEnv *env = (JNIEnv*)JNU_GetEnv(jvm, JNI_VERSION_1_4);
    long* event_data = event->data.l;
    Window source_win = None;

    DTRACE_PRINTLN5("%s:%d XdndDrop comp=%X src_win=%ld protocol=%d.",
                    __FILE__, __LINE__,
                    target_component, source_window, source_protocol);

    if (source_protocol != XDND_PROTOCOL) {
        DTRACE_PRINTLN2("%s:%d XdndDrop rejected - invalid state.",
                        __FILE__, __LINE__);
        return EventFailure;
    }

    source_win = event_data[0];

    /* Ignore XDnD messages from all other windows. */
    if (source_window != source_win) {
        DTRACE_PRINTLN4("%s:%d XdndDrop rejected - invalid source window cur=%ld this=%ld.",
                        __FILE__, __LINE__, source_window, source_win);
        return EventFailure;
    }

    if (!JNU_IsNull(env, target_component)) {
        dt_postDropTargetEvent(env, target_component, source_x, source_y, user_action,
                               java_awt_event_MouseEvent_MOUSE_RELEASED, event);
    }

    return EventSuccess;
}

/*
 * Returns EventPassAlong if the event should be passed to the original proxy.
 * TOP_LEVEL_ENTER should be passed to the original proxy only if the event is
 * invalid.
 */
static EventStatus
handle_motif_top_level_enter(XClientMessageEvent* event) {
    JNIEnv *env = (JNIEnv*)JNU_GetEnv(jvm, JNI_VERSION_1_4);
    Display* dpy = event->display;
    char* event_data = event->data.b;
    unsigned char event_byte_order = 0;
    Window source_win = None;
    long source_win_mask = 0;
    unsigned int protocol_version = MOTIF_DND_PROTOCOL_VERSION;
    Atom property_atom = None;
    unsigned int data_types_count = 0;
    Atom* data_types = NULL;
    jlongArray java_data_types = NULL;

    DTRACE_PRINTLN5("%s:%d TOP_LEVEL_ENTER comp=%X src_win=%ld protocol=%d.",
                    __FILE__, __LINE__,
                    target_component, source_window, source_protocol);

    if (!JNU_IsNull(env, target_component) || source_window != None ||
        source_protocol != NO_PROTOCOL) {
        DTRACE_PRINTLN2("%s:%d TOP_LEVEL_ENTER rejected - invalid state.",
                        __FILE__, __LINE__);
        return EventFailure;
    }

    if (JNU_IsNull(env, get_component_for_window(event->window)) &&
        !is_embedding_toplevel(event->window)) {
        DTRACE_PRINTLN2("%s:%d TOP_LEVEL_ENTER rejected - window is not a registered drop site.",
                        __FILE__, __LINE__);
        return EventFailure;
    }

    event_byte_order = read_card8(event_data, 1);
    source_win = read_card32(event_data, 8, event_byte_order);
    property_atom = read_card32(event_data, 12, event_byte_order);

    /* Extract the available data types. */
    {
        unsigned char  ret;
        Atom           type;
        int            format;
        unsigned long  nitems;
        unsigned long  after;
        unsigned char  *data;

        data = NULL;
        ret = checked_XGetWindowProperty(dpy, source_win, property_atom, 0,
                                         0xFFFF, False,
                                         _XA_MOTIF_DRAG_INITIATOR_INFO, &type,
                                         &format, &nitems, &after, &data);

        /* Ignore the source if the window is destroyed. */
        if (ret == BadWindow) {
            DTRACE_PRINTLN2("%s:%d TOP_LEVEL_ENTER rejected - invalid window.",
                            __FILE__, __LINE__);
            return EventFailure;
        }

        if (ret == BadAtom) {
            DTRACE_PRINTLN2("%s:%d TOP_LEVEL_ENTER rejected - invalid property atom.",
                            __FILE__, __LINE__);
            return EventFailure;
        }

        if (ret == Success) {
            if (type == _XA_MOTIF_DRAG_INITIATOR_INFO && format == 8 &&
                nitems == MOTIF_INITIATOR_INFO_SIZE) {
                unsigned char property_byte_order = read_card8((char*)data, 0);
                int index = read_card16((char*)data, 2, property_byte_order);

                protocol_version = read_card8((char*)data, 1);

                if (protocol_version > MOTIF_DND_PROTOCOL_VERSION) {
                    DTRACE_PRINTLN3("%s:%d TOP_LEVEL_ENTER rejected - invalid protocol version: %d.",
                                    __FILE__, __LINE__, protocol_version);
                    XFree(data);
                    return EventFailure;
                }

                get_target_list_for_index(dpy, index, &data_types, &data_types_count);
            }

            XFree(data);
        }
    }

    java_data_types = get_data_types_array(env, data_types, data_types_count);

    if (JNU_IsNull(env, java_data_types)) {
        DTRACE_PRINTLN2("%s:%d TOP_LEVEL_ENTER rejected - cannot create types array.",
                        __FILE__, __LINE__);
        free((char*)data_types);
        return EventFailure;
    }

    /*
     * Select for StructureNotifyMask to receive DestroyNotify in case of source
     * crash.
     */
    {
        unsigned char ret;
        XWindowAttributes xwa;

        XGetWindowAttributes(dpy, source_win, &xwa);

        source_win_mask = xwa.your_event_mask;

        ret = checked_XSelectInput(dpy, source_win,
                                   (source_win_mask | StructureNotifyMask));

        if (ret == BadWindow) {
            DTRACE_PRINTLN2("%s:%d XdndEnter rejected - invalid window.",
                            __FILE__, __LINE__);
            free((char*)data_types);
            (*env)->DeleteGlobalRef(env, java_data_types);
            return EventFailure;
        }
    }

    source_protocol = MOTIF_DND_PROTOCOL;
    source_protocol_version = protocol_version;
    source_window = source_win;
    source_atom = property_atom;
    source_window_mask = source_win_mask;
    /*
     * TOP_LEVEL_ENTER doesn't communicate the list of supported actions
     * They are provided in DRAG_MOTION.
     */
    source_actions = java_awt_dnd_DnDConstants_ACTION_NONE;
    track_source_actions = False;
    source_data_types = java_data_types;
    source_data_types_native = data_types;
    source_data_types_count = data_types_count;
    DTRACE_PRINTLN6("%s:%d TOP_LEVEL_ENTER comp=%d src_win=%ld protocol=%d fmt=%d.",
                    __FILE__, __LINE__,
                    target_component, source_window, source_protocol, data_types_count);

    return EventSuccess;
}

/*
 * Returns EventPassAlong if the event should be passed to the original proxy.
 * DRAG_MOTION event shouldn't be passed to the original proxy only if it is
 * a valid event and the mouse coordinates passed in it specify the point over
 * a Java component in this JVM.
 */
static EventStatus
handle_motif_drag_motion(XClientMessageEvent* event) {
    JNIEnv *env = (JNIEnv*)JNU_GetEnv(jvm, JNI_VERSION_1_4);
    char* event_data = event->data.b;
    unsigned char event_reason = 0;
    unsigned char event_byte_order = 0;
    Window source_win = None;
    CARD16 flags = 0;
    unsigned char motif_action = 0;
    unsigned char motif_actions = 0;
    jint java_action = java_awt_dnd_DnDConstants_ACTION_NONE;
    jint java_actions = java_awt_dnd_DnDConstants_ACTION_NONE;
    int x = 0;
    int y = 0;
    jint java_event_id = 0;
    jobject component = NULL;

    DTRACE_PRINTLN5("%s:%d DRAG_MOTION comp=%X src_win=%ld protocol=%d.",
                    __FILE__, __LINE__,
                    target_component, source_window, source_protocol);

    if (source_protocol != MOTIF_DND_PROTOCOL) {
        DTRACE_PRINTLN2("%s:%d DRAG_MOTION rejected - invalid state.",
                        __FILE__, __LINE__);
        return EventFailure;
    }

    event_reason = read_card8(event_data, 0) & MOTIF_MESSAGE_REASON_MASK;
    event_byte_order = read_card8(event_data, 1);

    flags = read_card16(event_data, 2, event_byte_order);

    motif_action = (flags & MOTIF_DND_ACTION_MASK) >> MOTIF_DND_ACTION_SHIFT;
    motif_actions = (flags & MOTIF_DND_ACTIONS_MASK) >> MOTIF_DND_ACTIONS_SHIFT;

    java_action = motif_to_java_actions(motif_action);
    java_actions = motif_to_java_actions(motif_actions);

    /* Append source window id to the event data, so that we can send the
       response properly. */
    {
        Window win = source_window;
        void* p = &event->data.b[12];
        if (event_byte_order != MOTIF_BYTE_ORDER) {
            SWAP4BYTES(win);
        }
        write_card32(&p, (CARD32)win);
    }

    component = get_component_for_window(event->window);

    if (event_reason == OPERATION_CHANGED) {
        /* OPERATION_CHANGED event doesn't provide coordinates, so we use
           previously stored position and component ref. */
        x = source_x;
        y = source_y;

        if (JNU_IsNull(env, component)) {
            component = target_component;
        }
    } else {
        Window receiver = None;

        x = read_card16(event_data, 8, event_byte_order);
        y = read_card16(event_data, 10, event_byte_order);

        if (JNU_IsNull(env, component)) {
            /*
             * The window must be the embedding toplevel, since otherwise we
             * would reject the TOP_LEVEL_ENTER and never get to this point.
             */
            DASSERT(is_embedding_toplevel(event->window));

            receiver = get_embedded_window(event->display, event->window, x, y);

            if (receiver != None) {
                component = get_component_for_window(receiver);
            }
        } else {
            receiver = event->window;
        }

        /* Translate mouse position from root coordinates
           to the target window coordinates. */
        if (receiver != None) {
            Window child = None;
            XTranslateCoordinates(event->display,
                                  get_root_for_window(receiver),
                                  get_outer_canvas_for_window(receiver),
                                  x, y, &x, &y, &child);
        }
    }

    if (JNU_IsNull(env, component)) {
        if (!JNU_IsNull(env, target_component)) {
            /* Triggers dragExit */
            dt_postDropTargetEvent(env, target_component, x, y,
                                   java_awt_dnd_DnDConstants_ACTION_NONE,
                                   java_awt_event_MouseEvent_MOUSE_EXITED,
                                   NULL);
        }
    } else {
        if (JNU_IsNull(env, target_component)) {
            /* Triggers dragEnter */
            java_event_id = java_awt_event_MouseEvent_MOUSE_ENTERED;
        } else {
            /* Triggers dragOver */
            java_event_id = java_awt_event_MouseEvent_MOUSE_DRAGGED;
        }

        dt_postDropTargetEvent(env, component, x, y, java_action, java_event_id,
                               event);
    }

    source_actions = java_actions;
    track_source_actions = False;
    user_action = java_action;
    source_x = x;
    source_y = y;
    target_component = component;

    return EventSuccess;
}

/*
 * Returns EventPassAlong if the event should be passed to the original proxy.
 * TOP_LEVEL_LEAVE should be passed to the original proxy only if the event
 * is invalid.
 */
static EventStatus
handle_motif_top_level_leave(XClientMessageEvent* event) {
    JNIEnv *env = (JNIEnv*)JNU_GetEnv(jvm, JNI_VERSION_1_4);
    char* event_data = event->data.b;
    unsigned char event_byte_order = 0;
    Window source_win = None;

    DTRACE_PRINTLN5("%s:%d TOP_LEVEL_LEAVE comp=%X src_win=%ld protocol=%d.",
                    __FILE__, __LINE__,
                    target_component, source_window, source_protocol);

    if (source_protocol != MOTIF_DND_PROTOCOL) {
        DTRACE_PRINTLN2("%s:%d TOP_LEVEL_LEAVE rejected - invalid state.",
                        __FILE__, __LINE__);
        return EventFailure;
    }

    event_byte_order = read_card8(event_data, 1);
    source_win = read_card32(event_data, 8, event_byte_order);

    /* Ignore Motif DnD messages from all other windows. */
    if (source_window != source_win) {
        DTRACE_PRINTLN4("%s:%d TOP_LEVEL_LEAVE rejected - invalid source window cur=%ld this=%ld.",
                        __FILE__, __LINE__, source_window, source_win);
        return EventFailure;
    }

    /*
     * Postpone upcall to java, so that we can abort it in case
     * if drop immediatelly follows (see BugTraq ID 4395290).
     * Send a dummy ClientMessage event to guarantee that a postponed java
     * upcall will be processed.
     */
    motif_top_level_leave_postponed = True;
    {
        XClientMessageEvent dummy;
        Window proxy;

        dummy.display      = event->display;
        dummy.type         = ClientMessage;
        dummy.window       = event->window;
        dummy.format       = 32;
        dummy.message_type = None;

        /*
         * If this is an embedded drop site, the event should go to the
         * awt_root_window as this is a proxy for all embedded drop sites.
         * Otherwise the event should go to the event->window, as we don't use
         * proxies for normal drop sites.
         */
        if (is_embedding_toplevel(event->window)) {
            proxy = get_awt_root_window();
        } else {
            proxy = event->window;
        }

        XSendEvent(event->display, proxy, False, NoEventMask,
                   (XEvent*)&dummy);
    }

    return EventSuccess;
}

/*
 * Returns EventPassAlong if the event should be passed to the original proxy.
 * DROP_START event shouldn't be passed to the original proxy only if it is
 * a valid event and the mouse coordinates passed in it specify the point over
 * a Java component in this JVM.
 */
static EventStatus
handle_motif_drop_start(XClientMessageEvent* event) {
    JNIEnv *env = (JNIEnv*)JNU_GetEnv(jvm, JNI_VERSION_1_4);
    char* event_data = event->data.b;
    unsigned char event_byte_order = 0;
    Window source_win = None;
    Atom property_atom = None;
    CARD16 flags = 0;
    unsigned char motif_action = 0;
    unsigned char motif_actions = 0;
    jint java_action = java_awt_dnd_DnDConstants_ACTION_NONE;
    jint java_actions = java_awt_dnd_DnDConstants_ACTION_NONE;
    int x = 0;
    int y = 0;
    jobject component = NULL;
    Window receiver = None;

    DTRACE_PRINTLN5("%s:%d DROP_START comp=%X src_win=%ld protocol=%d.",
                    __FILE__, __LINE__,
                    target_component, source_window, source_protocol);

    if (source_protocol != MOTIF_DND_PROTOCOL) {
        DTRACE_PRINTLN2("%s:%d DROP_START rejected - invalid state.",
                        __FILE__, __LINE__);
        return EventFailure;
    }

    event_byte_order = read_card8(event_data, 1);
    source_win = read_card32(event_data, 16, event_byte_order);

    /* Ignore Motif DnD messages from all other windows. */
    if (source_window != source_win) {
        DTRACE_PRINTLN4("%s:%d DROP_START rejected - invalid source window cur=%ld this=%ld.",
                        __FILE__, __LINE__, source_window, source_win);
        return EventFailure;
    }

    property_atom = read_card32(event_data, 12, event_byte_order);

    flags = read_card16(event_data, 2, event_byte_order);

    motif_action = (flags & MOTIF_DND_ACTION_MASK) >> MOTIF_DND_ACTION_SHIFT;
    motif_actions = (flags & MOTIF_DND_ACTIONS_MASK) >> MOTIF_DND_ACTIONS_SHIFT;

    java_action = motif_to_java_actions(motif_action);
    java_actions = motif_to_java_actions(motif_actions);

    x = read_card16(event_data, 8, event_byte_order);
    y = read_card16(event_data, 10, event_byte_order);

    source_actions = java_actions;

    component = get_component_for_window(event->window);

    if (JNU_IsNull(env, component)) {
        /*
         * The window must be the embedding toplevel, since otherwise we would reject the
         * TOP_LEVEL_ENTER and never get to this point.
         */
        DASSERT(is_embedding_toplevel(event->window));

        receiver = get_embedded_window(event->display, event->window, x, y);

        if (receiver != None) {
            component = get_component_for_window(receiver);
        }
    } else {
        receiver = event->window;
    }

    /* Translate mouse position from root coordinates
       to the target window coordinates. */
    if (receiver != None) {
        Window child = None;
        XTranslateCoordinates(event->display,
                              get_root_for_window(receiver),
                              get_outer_canvas_for_window(receiver),
                              x, y, &x, &y, &child);
    }

    if (JNU_IsNull(env, component)) {
        if (!JNU_IsNull(env, target_component)) {
            /* Triggers dragExit */
            dt_postDropTargetEvent(env, target_component, x, y,
                                   java_awt_dnd_DnDConstants_ACTION_NONE,
                                   java_awt_event_MouseEvent_MOUSE_EXITED,
                                   NULL);
        }
    } else {
        dt_postDropTargetEvent(env, component, x, y, java_action,
                               java_awt_event_MouseEvent_MOUSE_RELEASED,
                               event);
    }

    return EventSuccess;
}

static void
send_enter_message_to_toplevel(Window toplevel, XClientMessageEvent* xclient) {
    XClientMessageEvent enter;

    if (source_protocol == XDND_PROTOCOL) {
        enter.display = xclient->display;
        enter.type = ClientMessage;
        enter.window = toplevel;
        enter.format = 32;
        enter.message_type = XA_XdndEnter;
        enter.data.l[0] = xclient->data.l[0]; /* XID of the source window */
        enter.data.l[1] = source_protocol_version << XDND_PROTOCOL_SHIFT;
        enter.data.l[1] |= source_data_types_count > 3 ? XDND_DATA_TYPES_BIT : 0;
        enter.data.l[2] =
            source_data_types_count > 0 ? source_data_types_native[0] : None;
        enter.data.l[3] =
            source_data_types_count > 1 ? source_data_types_native[1] : None;
        enter.data.l[4] =
            source_data_types_count > 2 ? source_data_types_native[2] : None;
    } else if (source_protocol == MOTIF_DND_PROTOCOL) {
        int reason = (int)(xclient->data.b[0] & MOTIF_MESSAGE_REASON_MASK);
        unsigned char byte_order = xclient->data.b[1];

        enter.display = xclient->display;
        enter.type = ClientMessage;
        enter.window = toplevel;
        enter.format = 8;
        enter.message_type = _XA_MOTIF_DRAG_AND_DROP_MESSAGE;

        {
            void* p = &enter.data.b[0];
            int flags = 0;

            flags |= java_to_motif_actions(user_action) << MOTIF_DND_ACTION_SHIFT;
            flags |= java_to_motif_actions(source_actions) << MOTIF_DND_ACTIONS_SHIFT;

            write_card8(&p, TOP_LEVEL_ENTER | MOTIF_MESSAGE_FROM_INITIATOR);
            write_card8(&p, byte_order);
            write_card16(&p, flags);
            {
                Time time_stamp = read_card32(xclient->data.b, 4, byte_order);
                Window src_window = source_window;
                Atom motif_atom = _XA_MOTIF_ATOM_0;

                if (byte_order != MOTIF_BYTE_ORDER) {
                    SWAP4BYTES(time_stamp);
                    SWAP4BYTES(src_window);
                    SWAP4BYTES(motif_atom);
                }
                write_card32(&p, time_stamp);
                write_card32(&p, src_window);
                write_card32(&p, motif_atom);
            }
        }
    } else {
        return;
    }

    forward_client_message_to_toplevel(toplevel, &enter);
}

static void
send_leave_message_to_toplevel(Window toplevel, XClientMessageEvent* xclient) {
    XClientMessageEvent leave;

    if (source_protocol == XDND_PROTOCOL) {
        leave.display = xclient->display;
        leave.type = ClientMessage;
        leave.window = toplevel;
        leave.format = 32;
        leave.message_type = XA_XdndLeave;
        leave.data.l[0] = xclient->data.l[0]; /* XID of the source window */
        leave.data.l[1] = 0; /* flags */
    } else if (source_protocol == MOTIF_DND_PROTOCOL) {
        int reason = (int)(xclient->data.b[0] & MOTIF_MESSAGE_REASON_MASK);
        unsigned char byte_order = xclient->data.b[1];

        leave.display = xclient->display;
        leave.type = ClientMessage;
        leave.window = toplevel;
        leave.format = 8;
        leave.message_type = _XA_MOTIF_DRAG_AND_DROP_MESSAGE;

        {
            void* p = &leave.data.b[0];
            int flags = 0;

            write_card8(&p, TOP_LEVEL_LEAVE | MOTIF_MESSAGE_FROM_INITIATOR);
            write_card8(&p, byte_order);

            {
                Time time_stamp = read_card32(xclient->data.b, 4, byte_order);
                Window src_window = source_window;

                if (byte_order != MOTIF_BYTE_ORDER) {
                    SWAP4BYTES(time_stamp);
                    SWAP4BYTES(src_window);
                }
                write_card32(&p, time_stamp);
                write_card32(&p, src_window);
            }
        }
    } else {
        return;
    }

    forward_client_message_to_toplevel(toplevel, &leave);
}

static void
post_process_client_message(XClientMessageEvent* xclient, EventStatus status,
                            EventType type) {
    Window win = xclient->window;
    Boolean postponed_leave = motif_top_level_leave_postponed;

    motif_top_level_leave_postponed = False;

    if (is_embedding_toplevel(win)) {
        Boolean server_grabbed = False;

        if (postponed_leave) {
            XClientMessageEvent* leave = &motif_top_level_leave_postponed_event;
            DASSERT(leave->type == ClientMessage && type == DropEvent);
            /* Grab the server to ensure that no event is sent between
               the TOP_LEVEL_LEAVE and the next message. */
            XGrabServer(awt_display);
            forward_client_message_to_toplevel(leave->window, leave);
            memset(&motif_top_level_leave_postponed_event, 0,
                   sizeof(XClientMessageEvent));
        }

        /*
         * This code forwards drag notifications to the browser according to the
         * following rules:
         *  - the messages that we failed to process are always forwarded to the
         *    browser;
         *  - MotionEvents and DropEvents are forwarded if and only if the drag
         *    is not over a plugin window;
         *  - XDnD: EnterEvents and LeaveEvents are never forwarded, instead, we
         *    send synthesized EnterEvents or LeaveEvents when the drag
         *    respectively exits or enters plugin windows;
         *  - Motif DnD: EnterEvents and LeaveEvents are always forwarded.
         * Synthetic EnterEvents and LeaveEvents are needed, because the XDnD drop
         * site implemented Netscape 6.2 has a nice feature: when it receives
         * the first XdndPosition it continuously sends XdndStatus messages to
         * the source (every 100ms) until the drag terminates or leaves the drop
         * site. When the mouse is dragged over plugin window embedded in the
         * browser frame, these XdndStatus messages are mixed with the XdndStatus
         * messages sent from the plugin.
         * For Motif DnD, synthetic events cause Motif warnings being displayed,
         * so these events are always forwarded. However, Motif DnD drop site in
         * Netscape 6.2 is implemented in the same way, so there could be similar
         * problems if the drag source choose Motif DnD for communication.
         */
        switch (status) {
        case EventFailure:
            forward_client_message_to_toplevel(win, xclient);
            break;
        case EventSuccess:
        {
            /* True iff the previous notification was MotionEvent and it was
               forwarded to the browser. */
            static Boolean motion_passed_along = False;

            Boolean motif_protocol =
                xclient->message_type == _XA_MOTIF_DRAG_AND_DROP_MESSAGE;

            switch (type) {
            case MotionEvent:
                if (JNU_IsNull(env, target_component)) {
                    if (!motion_passed_along && !motif_protocol) {
                        send_enter_message_to_toplevel(win, xclient);
                    }
                    forward_client_message_to_toplevel(win, xclient);
                    motion_passed_along = True;
                } else {
                    if (motion_passed_along && !motif_protocol) {
                        send_leave_message_to_toplevel(win, xclient);
                    }
                    motion_passed_along = False;
                }
                break;
            case DropEvent:
                if (JNU_IsNull(env, target_component)) {
                    forward_client_message_to_toplevel(win, xclient);
                    /* The last chance to cleanup. */
                    awt_dnd_cleanup();
                }
                motion_passed_along = False;
                break;
            case EnterEvent:
            case LeaveEvent:
                if (motif_protocol) {
                    forward_client_message_to_toplevel(win, xclient);
                }
                motion_passed_along = False;
                break;
            }
        }
        }

        if (postponed_leave) {
            XUngrabServer(awt_display);
        }
    }
}

/*
 * Returns True if the event is processed and shouldn't be passed along to Java.
 * Otherwise, return False.
 */
Boolean
awt_dnd_dt_process_event(XEvent* event) {
    Display* dpy = event->xany.display;
    EventStatus status = EventFailure;
    EventType type = UnknownEvent;

    if (event->type == DestroyNotify) {
        if (event->xany.window == source_window) {
            awt_dnd_cleanup();
        }
        /* pass along */
        return False;
    }

    if (event->type == PropertyNotify) {
        if (is_embedding_toplevel(event->xany.window)) {
            Atom atom = event->xproperty.atom;
            /*
             * If some other client replaced the XDnD or Motif DnD proxy with
             * another window we set the proxy back to the awt_root_window
             * and update the entry in the embedded_drop_site_list.
             * This code is needed, as for example Netscape 4.7 resets the proxy
             * when the browser shell is resized.
             */
            if (atom == _XA_MOTIF_DRAG_RECEIVER_INFO) {
                Window prev_motif_proxy;
                ProxyRegistrationStatus status;
                status = set_motif_proxy(event->xany.display, event->xany.window,
                                         get_awt_root_window(), &prev_motif_proxy);
                if (status != RegFailure && status != RegAlreadyRegistered) {
                    set_motif_proxy_for_toplevel(event->xany.window,
                                                 prev_motif_proxy,
                                                 status == RegOverride);
                }
            }

            if (atom == XA_XdndAware || atom == XA_XdndProxy) {
                Window prev_xdnd_proxy;
                unsigned int prev_protocol_version;
                ProxyRegistrationStatus status;
                status = set_xdnd_proxy(event->xany.display, event->xany.window,
                                        get_awt_root_window(), &prev_xdnd_proxy,
                                        &prev_protocol_version);
                if (status != RegFailure && status != RegAlreadyRegistered) {
                    set_xdnd_proxy_for_toplevel(event->xany.window,
                                                prev_xdnd_proxy,
                                                prev_protocol_version,
                                                status == RegOverride);
                }
            }
        }
        /* pass along */
        return False;
    }

    if (event->type != ClientMessage) {
        return False;
    }

    if (get_component_for_window(event->xany.window) == NULL &&
        !is_embedding_toplevel(event->xany.window)) {
        return False;
    }

    if (motif_top_level_leave_postponed) {
        /* Sanity check. */
        if (source_protocol != MOTIF_DND_PROTOCOL) {
            DTRACE_PRINTLN2("%s:%d TOP_LEVEL_LEAVE rejected - invalid state.",
                            __FILE__, __LINE__);
            awt_dnd_cleanup();
        } else if (event->xclient.message_type ==
                   _XA_MOTIF_DRAG_AND_DROP_MESSAGE) {
            unsigned char first_byte = event->xclient.data.b[0];
            unsigned char reason = first_byte & MOTIF_MESSAGE_REASON_MASK;
            unsigned char origin = first_byte & MOTIF_MESSAGE_SENDER_MASK;

            if (origin == MOTIF_MESSAGE_FROM_INITIATOR &&
                reason != DROP_START) {
                awt_dnd_cleanup();
            }
        } else {
            awt_dnd_cleanup();
        }
    }

    if (event->xclient.message_type == XA_XdndEnter) {
        status = handle_xdnd_enter(&event->xclient);
        type = EnterEvent;
    } else if (event->xclient.message_type == XA_XdndPosition) {
        status = handle_xdnd_position(&event->xclient);
        type = MotionEvent;
    } else if (event->xclient.message_type == XA_XdndLeave) {
        status = handle_xdnd_leave(&event->xclient);
        type = LeaveEvent;
    } else if (event->xclient.message_type == XA_XdndDrop) {
        status = handle_xdnd_drop(&event->xclient);
        type = DropEvent;
    } else if (event->xclient.message_type == _XA_MOTIF_DRAG_AND_DROP_MESSAGE) {
        unsigned char reason = event->xclient.data.b[0] & MOTIF_MESSAGE_REASON_MASK;
        unsigned char origin = event->xclient.data.b[0] & MOTIF_MESSAGE_SENDER_MASK;

        /* Only initiator messages should be handled. */
        if (origin == MOTIF_MESSAGE_FROM_INITIATOR) {
            switch (reason) {
            case DRAG_MOTION:
            case OPERATION_CHANGED:
                status = handle_motif_drag_motion(&event->xclient);
                type = MotionEvent;
                break;
            case TOP_LEVEL_ENTER:
                status = handle_motif_top_level_enter(&event->xclient);
                type = EnterEvent;
                break;
            case TOP_LEVEL_LEAVE:
                status = handle_motif_top_level_leave(&event->xclient);
                type = LeaveEvent;
                break;
            case DROP_START:
                status = handle_motif_drop_start(&event->xclient);
                type = DropEvent;
                break;
            }
        }
    } else {
        /* Unknown message type. */
        return False;
    }

    /*
     * We need to handle a special case here: Motif DnD protocol prescribed that
     * DROP_START message should always be preceeded with TOP_LEVEL_LEAVE
     * message. We need to cleanup on TOP_LEVEL_LEAVE message, but DROP_START
     * wouldn't be processed properly. Instead we postpone the cleanup and
     * send a dummy client message to ourselves. If dummy arrives first we do a
     * normal cleanup. If DROP_START arrives before the dummy we discard delayed
     * cleanup.
     * In case of forwarding events from an embedded Java app to an embedding
     * Java app it could happen that the embedding app receives the dummy before
     * the DROP_START message arrives from the embedding app. In this case the
     * drop operation on the embedding app fails to complete.
     * To resolve this problem we postpone forwarding of TOP_LEVEL_LEAVE message
     * until the next client message is about to be forwarded.
     */
    if (motif_top_level_leave_postponed && type == LeaveEvent) {
        /* motif_top_level_leave_postponed can be set only if the latest client
           message has been processed successfully. */
        DASSERT(status == EventSuccess);
        memcpy(&motif_top_level_leave_postponed_event, &event->xclient,
               sizeof(XClientMessageEvent));
    } else {
        post_process_client_message(&event->xclient, status, type);
    }

    return True;
}

static Boolean
register_xdnd_drop_site(Display* dpy, Window toplevel, Window window) {
    unsigned char ret;
    Atom version_atom = XDND_PROTOCOL_VERSION;

    ret = checked_XChangeProperty(dpy, window, XA_XdndAware, XA_ATOM, 32,
                                  PropModeReplace,
                                  (unsigned char*)&version_atom, 1);

    return (ret == Success);
}

static Boolean
register_motif_drop_site(Display* dpy, Window toplevel, Window window) {
    unsigned char status;
    size_t data_size = MOTIF_RECEIVER_INFO_SIZE;
    char* data = malloc(data_size);
    void* p = data;

    if (data == NULL) {
        DTRACE_PRINTLN2("%s:%d malloc failed.", __FILE__, __LINE__);
        return False;
    }

    write_card8(&p, MOTIF_BYTE_ORDER);
    write_card8(&p, MOTIF_DND_PROTOCOL_VERSION); /* protocol version */
    write_card8(&p, MOTIF_DYNAMIC_STYLE); /* protocol style */
    write_card8(&p, 0); /* pad */
    write_card32(&p, window); /* proxy window */
    write_card16(&p, 0); /* num_drop_sites */
    write_card16(&p, 0); /* pad */
    write_card32(&p, data_size);

    status = checked_XChangeProperty(dpy, window, _XA_MOTIF_DRAG_RECEIVER_INFO,
                                     _XA_MOTIF_DRAG_RECEIVER_INFO, 8, PropModeReplace,
                                     (unsigned char*)data, data_size);

    free(data);

    return (status == Success);
}

static Window
find_toplevel_window(Display* dpy, Window window) {
    Window         ret = None;
    Window         root = None;
    Window         parent = None;
    Window         *children;
    unsigned int   nchildren;

    int            status;

    Atom           type;
    int            format;
    unsigned long  nitems;
    unsigned long  after;
    unsigned char  *data;

    /* Traverse the ancestor tree from window up to the root and find
       the top-level client window nearest to the root. */
    do {
        type = None;

        data = NULL;
        status = XGetWindowProperty(dpy, window, XA_WM_STATE, 0, 0, False,
                                    AnyPropertyType, &type, &format, &nitems,
                                    &after, &data);

        if (status == Success) {
            XFree(data);
        }

        if (type != None) {
            ret = window;
        }

        if (!XQueryTree(dpy, window, &root, &parent, &children, &nchildren)) {
            return None;
        }

        XFree(children);

        window = parent;
    } while (window != root);

    return ret;
}

static Boolean
register_drop_site(Widget outer_canvas, XtPointer componentRef) {
    Display* dpy = XtDisplay(outer_canvas);
    Widget shell = NULL;
    /* Shell window. */
    Window window = None;
    Window root = None;
    Window toplevel = None;

    for (shell = outer_canvas; shell != NULL && !XtIsShell(shell);
         shell = XtParent(shell));

    if (shell == NULL || !XtIsRealized(shell)) {
        DTRACE_PRINTLN2("%s:%d Cannot find a realized shell for the widget.",
                       __FILE__, __LINE__);
        return False;
    }

    window = XtWindow(shell);

    if (!awt_dnd_init(dpy)) {
        DTRACE_PRINTLN2("%s:%d Fail to initialize.", __FILE__, __LINE__);
        return False;
    }

    {
        XWindowAttributes xwa;

        if (!XGetWindowAttributes(dpy, window, &xwa)) {
            DTRACE_PRINTLN2("%s:%d XGetWindowAttributes failed.", __FILE__, __LINE__);
            return False;
        }

        root = xwa.root;

        if (root == None) {
            DTRACE_PRINTLN2("%s:%d Bad root.", __FILE__, __LINE__);
            return False;
        }
    }

    toplevel = find_toplevel_window(dpy, window);

    /*
     * No window with WM_STATE property is found.
     * Since the window can be a plugin window reparented to the browser
     * toplevel, we cannot determine which window will eventually have WM_STATE
     * property set. So we schedule a timer callback that will periodically
     * attempt to find an ancestor with WM_STATE and register the drop site
     * appropriately.
     */
    if (toplevel == None) {
        add_delayed_registration_entry(outer_canvas, componentRef);
        return False;
    }

    if (toplevel == window) {
        Boolean xdnd_registered = False;
        Boolean motif_registered = False;

        xdnd_registered = register_xdnd_drop_site(dpy, toplevel, window);

        motif_registered = register_motif_drop_site(dpy, toplevel, window);

        if (!xdnd_registered && !motif_registered) {
            DTRACE_PRINTLN2("%s:%d Failed to register.", __FILE__, __LINE__);
            return False;
        }
    } else {
        if (!add_to_embedded_drop_site_list(dpy, root, toplevel, window)) {
            DTRACE_PRINTLN2("%s:%d Failed to init proxy.", __FILE__, __LINE__);
            return False;
        }
    }

    /* There is no need to update the window for the component later, since the
       window is destroyed only when the component is disposed in which case the
       drop site will be unregistered as well. */
    if (add_to_drop_site_list(window, root, toplevel, XtWindow(outer_canvas),
                              (jobject)componentRef)) {
        DTRACE_PRINTLN2("%s:%d Drop site registered.", __FILE__, __LINE__);
        return True;
    } else {
        DTRACE_PRINTLN2("%s:%d Failed to register.", __FILE__, __LINE__);
        return False;
    }
}

static void
register_drop_site_when_realized(Widget outer_canvas, XtPointer client_data,
                                 XEvent *event, Boolean *dontSwallow) {
    if (XtIsRealized(outer_canvas)) {
        XtRemoveEventHandler(outer_canvas, StructureNotifyMask, False,
                             register_drop_site_when_realized, client_data);

        register_drop_site(outer_canvas, client_data);
    }
}

/*
 * Registers the top-level Window that contains the specified widget as a drop
 * site that supports XDnD and Motif DnD protocols.
 * If the registration fails for some reason, adds an event handler that will
 * attempt to register the drop site later.
 *
 * Returns True if the drop site is registered successfully.
 */
static Boolean
awt_dnd_register_drop_site(Widget outer_canvas, XtPointer componentRef) {
    if (XtIsRealized(outer_canvas)) {
        return register_drop_site(outer_canvas, componentRef);
    } else {
        XtAddEventHandler(outer_canvas, StructureNotifyMask, False,
                          register_drop_site_when_realized,
                          componentRef);

        DTRACE_PRINTLN2("%s:%d Unrealized shell. Register later.",
                        __FILE__, __LINE__);

        return True;
    }
}

/*
 * Unregisters the drop site associated with the top-level Window that contains
 * the specified widget .
 *
 * Returns True if completes successfully, False otherwise.
 */
static Boolean
awt_dnd_unregister_drop_site(Widget outer_canvas, XtPointer componentRef) {
    Widget shell = NULL;

    XtRemoveEventHandler(outer_canvas, StructureNotifyMask, False,
                         register_drop_site_when_realized, componentRef);

    remove_delayed_registration_entry(outer_canvas);

    for (shell = outer_canvas; shell != NULL && !XtIsShell(shell);
         shell = XtParent(shell));

    if (shell != NULL && XtIsShell(shell) && XtIsRealized(shell)) {
        Window win = XtWindow(shell);
        Window toplevel = get_toplevel_for_window(win);
        /*
         * Cleanup the global state if this drop site participate in the current
         * drag operation. Particularly, this allows to delete global ref to the
         * component safely.
         */
        if (get_component_for_window(win) == target_component) {
            awt_dnd_cleanup();
        }
        if (toplevel != win) {
            remove_from_embedded_drop_site_list(awt_display, toplevel, win);
        }
        return remove_from_drop_site_list(win);
    }

    return True;
}

/**************************** XEmbed server DnD support ***********************/

/*
 *
 *
 */
Boolean
register_xembed_drop_site(JNIEnv* env, Display* dpy, jobject server,
                          Window serverHandle, Window clientHandle) {
    Atom           type;
    int            format;
    unsigned long  nitems;
    unsigned long  after;
    unsigned char* data;
    unsigned char  ret;
    unsigned int   protocol_version;

    Window         xdnd_proxy = None;
    unsigned int   xdnd_protocol_version = 0;
    Boolean        xdnd_override = False;

    if (!awt_dnd_init(dpy)) {
        DTRACE_PRINTLN2("%s:%d Fail to initialize.", __FILE__, __LINE__);
        return False;
    }

    /* Get the XDnD protocol version and XDnD proxy of the XEmbed client. */
    data = NULL;
    ret = checked_XGetWindowProperty(dpy, clientHandle, XA_XdndAware, 0, 1,
                                     False, AnyPropertyType, &type, &format,
                                     &nitems, &after, &data);

    /* XEmbed client doesn't have an associated XDnD drop site -
       do nothing and return True to indicate success. */
    if (ret != Success || data == NULL || nitems == 0 || type != XA_ATOM) {
        XFree(data);
        return False;
    }

    protocol_version = *((unsigned int*)data);

    XFree(data);

    if (protocol_version < XDND_MIN_PROTOCOL_VERSION) {
        return False;
    }

    xdnd_protocol_version = protocol_version;

    /* XdndProxy is not supported prior to XDnD version 4 */
    if (protocol_version >= 4) {
        int status;

        data = NULL;
        status = XGetWindowProperty(dpy, clientHandle, XA_XdndProxy, 0, 1,
                                    False, XA_WINDOW, &type, &format,
                                    &nitems, &after, &data);

        if (status == Success && data != NULL && type == XA_WINDOW) {
            xdnd_proxy = *((Window*)data);

            if (xdnd_proxy != None) {
                XFree(data);

                data = NULL;
                status = XGetWindowProperty(dpy, xdnd_proxy, XA_XdndProxy,
                                            0, 1, False, XA_WINDOW, &type,
                                            &format, &nitems, &after,
                                            &data);

                if (status != Success || data == NULL || type != XA_WINDOW ||
                    *((Window*)data) != xdnd_proxy) {
                    /* Ignore invalid proxy. */
                    xdnd_proxy = None;
                }
            }

            if (xdnd_proxy != None) {
                XFree(data);

                data = NULL;
                status = XGetWindowProperty(dpy, xdnd_proxy, XA_XdndAware, 0, 1,
                                            False, AnyPropertyType, &type,
                                            &format, &nitems, &after, &data);

                if (status == Success && data != NULL && type == XA_ATOM) {
                    unsigned int proxy_version = *((unsigned int*)data);

                    if (proxy_version != protocol_version) {
                        /* Ignore invalid proxy. */
                        xdnd_proxy = None;
                    }
                } else {
                    /* Ignore invalid proxy. */
                    xdnd_proxy = None;
                }
            }
        }

        XFree(data);
    }

    set_xembed_drop_target(env, server);

    /* Add protocol specific entries for the embedded window. */
    /* Only XDnD protocol is supported for XEmbed clients. */
    {
        EmbeddedDropSiteProtocolListEntry* xdnd_entry = NULL;

        xdnd_entry = malloc(sizeof(EmbeddedDropSiteProtocolListEntry));

        if (xdnd_entry == NULL) {
            return False;
        }

        xdnd_entry->window = clientHandle;
        xdnd_entry->proxy = xdnd_proxy;
        xdnd_entry->protocol_version = xdnd_protocol_version;
        xdnd_entry->overriden = True;
        xdnd_entry->next = embedded_xdnd_protocol_list;
        embedded_xdnd_protocol_list = xdnd_entry;
    }

    {
        EmbeddedDropSiteListEntry* entry = NULL;
        Window* sites = NULL;

        entry = malloc(sizeof(EmbeddedDropSiteListEntry));

        if (entry == NULL) {
            return False;
        }

        sites = malloc(sizeof(Window));

        if (sites == NULL) {
            free(entry);
            return False;
        }

        sites[0] = clientHandle;

        entry->toplevel = serverHandle;
        entry->root = None;
        entry->event_mask = 0;
        entry->embedded_sites_count = 1;
        entry->embedded_sites = sites;
        entry->next = embedded_drop_site_list;
        embedded_drop_site_list = entry;
    }

    return True;
}

Boolean
unregister_xembed_drop_site(JNIEnv* env, Display* dpy, jobject server,
                            Window serverHandle, Window clientHandle) {
    remove_from_embedded_drop_site_list(dpy, serverHandle, clientHandle);
    return True;
}

void
forward_event_to_embedded(Window embedded, jlong ctxt, jint eventID) {
    static XClientMessageEvent* prevMessage = NULL;
    static Boolean overXEmbedClient = False;

    XClientMessageEvent* xclient =
        (XClientMessageEvent*)jlong_to_ptr(ctxt);

    if (xclient == NULL && prevMessage == NULL) {
        return;
    }

    if (xclient != NULL) {
        /*
         * NOTE: this check guarantees that prevMessage will always be an XDnD
         * drag message.
         */
        if (!is_xdnd_drag_message_type(xclient->message_type)) {
            return;
        }

        if (!overXEmbedClient) {
            long* appended_data = jlong_to_ptr(ctxt) +
                sizeof(XClientMessageEvent);

            /* Copy XdndTypeList from source to proxy. */
            if ((appended_data[0] & XDND_DATA_TYPES_BIT) != 0) {
                unsigned char  ret;
                Atom           type;
                int            format;
                unsigned long  nitems;
                unsigned long  after;
                unsigned char  *data;

                data = NULL;
                ret = checked_XGetWindowProperty(xclient->display,
                                                 xclient->data.l[0],
                                                 XA_XdndTypeList, 0, 0xFFFF,
                                                 False, XA_ATOM, &type, &format,
                                                 &nitems, &after, &data);

                /* Ignore the source if the window is destroyed. */
                if (ret == BadWindow) {
                    return;
                }

                if (ret == Success) {
                    if (type == XA_ATOM && format == 32) {
                        ret = checked_XChangeProperty(xclient->display,
                                                      xclient->window,
                                                      XA_XdndTypeList, XA_ATOM,
                                                      32, PropModeReplace, data,
                                                      nitems);
                    }

                    XFree(data);
                }
            }

            set_proxy_mode_source_window(xclient->data.l[0]);

            {
                XClientMessageEvent enter;
                enter.display = xclient->display;
                enter.type = ClientMessage;
                enter.window = embedded;
                enter.format = 32;
                enter.message_type = XA_XdndEnter;

                enter.data.l[0] = xclient->window; /* XID of the source window */
                enter.data.l[1] = appended_data[0];
                enter.data.l[2] = appended_data[1];
                enter.data.l[3] = appended_data[2];
                enter.data.l[4] = appended_data[3];

                forward_client_message_to_toplevel(embedded, &enter);
            }

            overXEmbedClient = True;
        }

        /* Make a copy of the original event, since we are going to modify the
           event while it still can be referenced from other Java events. */
        {
            XClientMessageEvent copy;
            memcpy(&copy, xclient, sizeof(XClientMessageEvent));
            copy.data.l[0] = xclient->window;

            forward_client_message_to_toplevel(embedded, &copy);
        }
    }

    if (eventID == java_awt_event_MouseEvent_MOUSE_EXITED) {
        if (overXEmbedClient) {
            if (xclient != NULL || prevMessage != NULL) {
                /* Last chance to send XdndLeave to the XEmbed client. */
                XClientMessageEvent leave;

                leave.display = xclient != NULL ?
                    xclient->display : prevMessage->display;
                leave.type = ClientMessage;
                leave.window = embedded;
                leave.format = 32;
                leave.message_type = XA_XdndLeave;
                leave.data.l[0] = xclient != NULL ?
                    xclient->window : prevMessage->window; /* XID of the source window */
                leave.data.l[1] = 0; /* flags */

                forward_client_message_to_toplevel(embedded, &leave);
            }
            overXEmbedClient = False;
        }
    }

    if (eventID == java_awt_event_MouseEvent_MOUSE_RELEASED) {
        overXEmbedClient = False;
        awt_dnd_cleanup();
    }

    if (prevMessage != 0) {
        free(prevMessage);
        prevMessage = 0;
    }

    if (xclient != 0 && overXEmbedClient) {
        prevMessage = malloc(sizeof(XClientMessageEvent));

        memcpy(prevMessage, xclient, sizeof(XClientMessageEvent));
    }
}

/******************************************************************************/

/*
 * Class:     sun_awt_motif_MWindowPeer
 * Method:    registerX11DropTarget
 * Signature: (Ljava/awt/Component;)V
 */

JNIEXPORT void JNICALL
Java_sun_awt_motif_MWindowPeer_registerX11DropTarget(JNIEnv *env, jobject this,
                                                     jobject target) {
    struct FrameData* wdata = NULL;
    DropSitePtr dsi = NULL;

    wdata = (struct FrameData *)
        JNU_GetLongFieldAsPtr(env, this, mComponentPeerIDs.pData);

    if (wdata == NULL || wdata->winData.comp.widget == NULL) {
        JNU_ThrowNullPointerException(env, "NULL component data");
        return;
    }

    if (wdata->winData.shell == NULL) {
        JNU_ThrowNullPointerException(env, "Null shell widget");
        return;
    }

    DASSERT(wdata->winData.comp.dsi == NULL);

    dsi = (DropSitePtr)calloc(1, sizeof(struct DropSiteInfo));

    if (dsi == NULL) {
        JNU_ThrowOutOfMemoryError(env, "");
        return;
    }

    dsi->component = (*env)->NewGlobalRef(env, target);
    dsi->isComposite = False;

    wdata->winData.comp.dsi = dsi;

    AWT_LOCK();

    awt_dnd_register_drop_site(wdata->winData.comp.widget,
                               dsi->component);

    AWT_UNLOCK();
}

/*
 * Class:     sun_awt_motif_MWindowPeer
 * Method:    unregisterX11DropTarget
 * Signature: (Ljava/awt/Component;)V
 */

JNIEXPORT void JNICALL
Java_sun_awt_motif_MWindowPeer_unregisterX11DropTarget(JNIEnv *env,
                                                       jobject this,
                                                       jobject target) {
    struct FrameData* wdata = NULL;
    DropSitePtr dsi = NULL;

    wdata = (struct FrameData *)
        JNU_GetLongFieldAsPtr(env, this, mComponentPeerIDs.pData);

    if (wdata == NULL) {
        JNU_ThrowNullPointerException(env, "Null component data");
        return;
    }

    if (wdata->winData.shell == NULL) {
        JNU_ThrowNullPointerException(env, "Null shell widget");
        return;
    }

    dsi = wdata->winData.comp.dsi;

    if (dsi == NULL) {
        JNU_ThrowNullPointerException(env, "Null DropSiteInfo");
        return;
    }

    AWT_LOCK();

    awt_dnd_unregister_drop_site(wdata->winData.comp.widget, dsi->component);

    AWT_UNLOCK();

    wdata->winData.comp.dsi = NULL;

    (*env)->DeleteGlobalRef(env, dsi->component);

    free(dsi);
}

static void
dt_send_event_to_source(XClientMessageEvent* xclient) {
    /* Shortcut if the source is in the same JVM. */
    if (xclient->window == awt_dnd_ds_get_source_window()) {
        awt_dnd_ds_process_event((XEvent*)xclient);
    } else {
        unsigned char ret;

        ret = checked_XSendEvent(xclient->display, xclient->window, False,
                                 NoEventMask, (XEvent*)xclient);

        if (ret == BadWindow) {
            DTRACE_PRINTLN2("%s:%d XSendEvent - invalid window.",
                            __FILE__, __LINE__);

            /* Cleanup if we are still communicating with this window. */
            if (source_window == xclient->window) {
                awt_dnd_cleanup();
            }
        }
    }
}

static void
dt_send_response(XClientMessageEvent* xclient, jint eventID, jint action) {
    Display* dpy = xclient->display;
    XClientMessageEvent response;

    if (xclient->message_type == XA_XdndPosition) {
        long* event_data = xclient->data.l;

        if (eventID == java_awt_event_MouseEvent_MOUSE_EXITED) {
            action = java_awt_dnd_DnDConstants_ACTION_NONE;
        }

        response.display = dpy;
        response.type = ClientMessage;
        response.window = event_data[0];
        response.format = 32;
        response.message_type = XA_XdndStatus;
        /* target window */
        response.data.l[0] = xclient->window;
        /* flags */
        response.data.l[1] = 0;
        if (action != java_awt_dnd_DnDConstants_ACTION_NONE) {
            response.data.l[1] |= XDND_ACCEPT_DROP_FLAG;
        }
        /* specify an empty rectangle */
        response.data.l[2] = 0; /* x, y */
        response.data.l[3] = 0; /* w, h */
        /* action accepted by the target */
        response.data.l[4] = java_to_xdnd_action(action);
    } else if (xclient->message_type == _XA_MOTIF_DRAG_AND_DROP_MESSAGE) {
        int reason = (int)(xclient->data.b[0] & MOTIF_MESSAGE_REASON_MASK);
        int origin = (int)(xclient->data.b[0] & MOTIF_MESSAGE_SENDER_MASK);
        unsigned char byte_order = xclient->data.b[1];
        CARD16 response_flags = 0;
        CARD8 response_reason = 0;
        void* p = &response.data.b;

        /* Only initiator messages should be handled. */
        if (origin != MOTIF_MESSAGE_FROM_INITIATOR) {
            DTRACE_PRINTLN2("%s:%d Receiver message.", __FILE__, __LINE__);
            return;
        }

        switch (reason) {
        case DRAG_MOTION:
            switch (eventID) {
            case java_awt_event_MouseEvent_MOUSE_ENTERED:
                response_reason = DROP_SITE_ENTER;
                break;
            case java_awt_event_MouseEvent_MOUSE_DRAGGED:
                response_reason = DRAG_MOTION;
                break;
            case java_awt_event_MouseEvent_MOUSE_EXITED:
                response_reason = DROP_SITE_LEAVE;
                break;
            }
        }

        response.display = dpy;
        response.type = ClientMessage;
        response.window = read_card32(xclient->data.b, 12, byte_order);
        response.format = 8;
        response.message_type = _XA_MOTIF_DRAG_AND_DROP_MESSAGE;

        write_card8(&p, response_reason | MOTIF_MESSAGE_FROM_RECEIVER);
        write_card8(&p, MOTIF_BYTE_ORDER);

        if (response_reason != DROP_SITE_LEAVE) {
            CARD16 flags = read_card16(xclient->data.b, 2, byte_order);
            unsigned char drop_site_status =
                (action == java_awt_dnd_DnDConstants_ACTION_NONE) ?
                MOTIF_INVALID_DROP_SITE : MOTIF_VALID_DROP_SITE;

            /* Clear action and drop site status bits. */
            response_flags =
                flags & ~MOTIF_DND_ACTION_MASK & ~MOTIF_DND_STATUS_MASK;

            /* Fill in new action and drop site status. */
            response_flags |=
                java_to_motif_actions(action) << MOTIF_DND_ACTION_SHIFT;
            response_flags |=
                drop_site_status << MOTIF_DND_STATUS_SHIFT;
        } else {
            response_flags = 0;
        }

        write_card16(&p, response_flags);

        /* Write time stamp. */
        write_card32(&p, read_card32(xclient->data.b, 4, byte_order));

        /* Write coordinates. */
        if (response_reason != DROP_SITE_LEAVE) {
            write_card16(&p, read_card16(xclient->data.b, 8, byte_order));
            write_card16(&p, read_card16(xclient->data.b, 10, byte_order));
        } else {
            write_card16(&p, 0);
            write_card16(&p, 0);
        }
    } else {
        return;
    }

    dt_send_event_to_source(&response);
}

static void
dummy_selection_callback(Widget w, XtPointer client_data, Atom* selection,
                         Atom* type, XtPointer value, unsigned long *length,
                         int32_t *format) {
    /* The selection callback is responsible for freeing the data. */
    if (value != NULL) {
        XtFree(value);
        value = NULL;
    }
}

static void
dt_notify_drop_done(JNIEnv* env, XClientMessageEvent* xclient, jboolean success,
                    jint action) {
    if (xclient->message_type == XA_XdndDrop) {
        Display* dpy = xclient->display;
        XClientMessageEvent finished;
        long* event_data = xclient->data.l;

        /*
         * The XDnD protocol recommends that the target requests the special
         * target DELETE in case if the drop action is XdndActionMove.
         */
        if (action == java_awt_dnd_DnDConstants_ACTION_MOVE &&
            success == JNI_TRUE) {

            Time time_stamp = event_data[2];

            XtGetSelectionValue(awt_root_shell, XA_XdndSelection, XA_DELETE,
                                dummy_selection_callback, NULL, time_stamp);
        }

        finished.display = dpy;
        finished.type = ClientMessage;
        finished.window = event_data[0];
        finished.format = 32;
        finished.message_type = XA_XdndFinished;
        finished.data.l[0] = xclient->window;
        finished.data.l[1] = 0; /* flags */
        finished.data.l[2] = None;
        if (source_protocol_version >= 5) {
            if (success == JNI_TRUE) {
                finished.data.l[1] |= XDND_ACCEPT_DROP_FLAG;
            }
            finished.data.l[2] = java_to_xdnd_action(action);
        }

        dt_send_event_to_source(&finished);
    } else if (xclient->message_type == _XA_MOTIF_DRAG_AND_DROP_MESSAGE) {
        char* event_data = xclient->data.b;
        unsigned char event_byte_order = read_card8(event_data, 1);
        unsigned char first_byte = read_card8(event_data, 0);
        unsigned char reason = first_byte & MOTIF_MESSAGE_REASON_MASK;
        unsigned char origin = first_byte & MOTIF_MESSAGE_SENDER_MASK;
        Atom selection = None;
        Time time_stamp = CurrentTime;
        Atom status_atom = None;

        if (origin != MOTIF_MESSAGE_FROM_INITIATOR) {
            DTRACE_PRINTLN2("%s:%d Invalid origin.", __FILE__, __LINE__);
            return;
        }

        if (reason != DROP_START) {
            DTRACE_PRINTLN2("%s:%d Invalid reason.", __FILE__, __LINE__);
            return;
        }

        selection = read_card32(event_data, 12, event_byte_order);
        time_stamp = read_card32(event_data, 4, event_byte_order);

        if (success == JNI_TRUE) {
            status_atom = XA_XmTRANSFER_SUCCESS;
        } else {
            status_atom = XA_XmTRANSFER_FAILURE;
        }

        /*
         * This is just the way to communicate the drop completion status back
         * to the initiator as prescribed by the Motif DnD protocol.
         */
        XtGetSelectionValue(awt_root_shell, selection, status_atom,
                            dummy_selection_callback, NULL, time_stamp);
    }

    /*
     * Flush the buffer to guarantee that the drop completion event is sent
     * to the source before the method returns.
     */
    XFlush(awt_display);

    /* Trick to prevent awt_dnd_cleanup() from posting dragExit */
    target_component = NULL;
    /* Cannot do cleanup before the drop finishes as we need source protocol
       version to send XdndFinished message. */
    awt_dnd_cleanup();
}

/*
 * Class:     sun_awt_motif_X11DropTargetContextPeer
 * Method:    sendResponse
 * Signature: (IIJZ)V
 */

JNIEXPORT void JNICALL
Java_sun_awt_motif_X11DropTargetContextPeer_sendResponse(JNIEnv *env,
                                                         jobject this,
                                                         jint eventID,
                                                         jint action,
                                                         jlong nativeCtxt,
                                                         jboolean dispatcherDone,
                                                         jboolean consumed) {
    XClientMessageEvent* xclient =
        (XClientMessageEvent*)jlong_to_ptr(nativeCtxt);

    AWT_LOCK();

    if (consumed == JNI_FALSE) {
        dt_send_response(xclient, eventID, action);
    }

    /*
     * Free the native context only if all copies of the original event are
     * processed.
     */
    if (dispatcherDone == JNI_TRUE) {
        XtFree((char*)xclient);
    }

    AWT_UNLOCK();
}

/*
 * Class:     sun_awt_motif_X11DropTargetContextPeer
 * Method:    dropDone
 * Signature: (JZI)V
 */

JNIEXPORT void JNICALL
Java_sun_awt_motif_X11DropTargetContextPeer_dropDone(JNIEnv *env,
                                                     jobject this,
                                                     jlong nativeCtxt,
                                                     jboolean success,
                                                     jint action) {
    XClientMessageEvent* xclient =
        (XClientMessageEvent*)jlong_to_ptr(nativeCtxt);

    AWT_LOCK();

    dt_notify_drop_done(env, xclient, success, action);

    XtFree((char*)xclient);

    AWT_UNLOCK();
}

/*
 * Class:     sun_awt_motif_X11DropTargetContextPeer
 * Method:    getData
 * Signature: (IJ)Ljava/lang/Object;
 */

JNIEXPORT jobject JNICALL
Java_sun_awt_motif_X11DropTargetContextPeer_getData(JNIEnv *env,
                                                    jobject this,
                                                    jlong nativeCtxt,
                                                    jlong formatAtom) {
    XClientMessageEvent* xclient =
        (XClientMessageEvent*)jlong_to_ptr(nativeCtxt);

    Atom selection    = None;
    Time time_stamp   = CurrentTime;
    Atom target       = (Atom)formatAtom;

    if (xclient->message_type == XA_XdndDrop ||
        xclient->message_type == XA_XdndPosition) {
        Display* dpy = xclient->display;
        Window source_win = xclient->data.l[0];
        Atom protocol_version = 0;

        int            status;

        Atom           type;
        int            format;
        unsigned long  nitems;
        unsigned long  after;
        unsigned char  *data;

        AWT_LOCK();

        data = NULL;
        status = XGetWindowProperty(dpy, source_win, XA_XdndAware, 0, 0xFFFF,
                                    False, XA_ATOM, &type, &format, &nitems,
                                    &after, &data);

        if (status == Success && data != NULL && type == XA_ATOM && format == 32
            && nitems > 0) {
            protocol_version = (protocol_version > XDND_PROTOCOL_VERSION) ?
                XDND_PROTOCOL_VERSION : protocol_version;

            if (protocol_version > 0) {
                if (xclient->message_type == XA_XdndDrop) {
                    time_stamp = xclient->data.l[2];
                } else if (xclient->message_type == XA_XdndPosition) {
                    time_stamp = xclient->data.l[3];
                }
            }
        }

        if (status == Success) {
            XFree(data);
            data = NULL;
        }

        AWT_FLUSH_UNLOCK();

        selection = XA_XdndSelection;
        if (time_stamp == CurrentTime) {
            time_stamp = awt_util_getCurrentServerTime();
        }

    } else if (xclient->message_type == _XA_MOTIF_DRAG_AND_DROP_MESSAGE) {
        char* event_data = xclient->data.b;
        unsigned char event_byte_order = read_card8(event_data, 1);
        unsigned char first_byte = read_card8(event_data, 0);
        unsigned char reason = first_byte & MOTIF_MESSAGE_REASON_MASK;
        unsigned char origin = first_byte & MOTIF_MESSAGE_SENDER_MASK;

        if (origin != MOTIF_MESSAGE_FROM_INITIATOR) {
            DTRACE_PRINTLN2("%s:%d Invalid origin.", __FILE__, __LINE__);
            return NULL;
        }

        switch (reason) {
        case DROP_START:
            selection = read_card32(event_data, 12, event_byte_order);
            break;
        case DRAG_MOTION:
        case OPERATION_CHANGED:
            selection = source_atom;
            break;
        default:
            DTRACE_PRINTLN2("%s:%d Invalid reason.", __FILE__, __LINE__);
            return NULL;
        }

        if (selection == None) {
            return NULL;
        }

        time_stamp = read_card32(event_data, 4, event_byte_order);
    } else {
        return NULL;
    }

    return get_selection_data(env, selection, target, time_stamp);
}
