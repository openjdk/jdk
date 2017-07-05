/*
 * Copyright 2003-2004 Sun Microsystems, Inc.  All Rights Reserved.
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

#include <X11/Intrinsic.h>

#include "awt_p.h"

/* For definition of MComponentPeerIDs */
#include "awt_Component.h"

extern struct MComponentPeerIDs mComponentPeerIDs;

/* DnD protocols */

typedef enum {
    NO_PROTOCOL,
    XDND_PROTOCOL,
    MOTIF_DND_PROTOCOL
} Protocol;

/* XDnD constants */

#define XDND_PROTOCOL_VERSION          5
/* XDnD compliance only requires supporting version 3 and up. */
#define XDND_MIN_PROTOCOL_VERSION      3

#define XDND_PROTOCOL_MASK    0xFF000000
#define XDND_PROTOCOL_SHIFT           24
#define XDND_DATA_TYPES_BIT          0x1
#define XDND_ACCEPT_DROP_FLAG        0x1

/* Motif DnD constants */

#define MOTIF_DND_PROTOCOL_VERSION 0

/* Suuported protocol styles */
#define MOTIF_PREFER_PREREGISTER_STYLE    2
#define MOTIF_PREFER_DYNAMIC_STYLE        4
#define MOTIF_DYNAMIC_STYLE               5
#define MOTIF_PREFER_RECEIVER_STYLE       6

#define MOTIF_MESSAGE_REASON_MASK      0x7F
#define MOTIF_MESSAGE_SENDER_MASK      0x80
#define MOTIF_MESSAGE_FROM_RECEIVER    0x80
#define MOTIF_MESSAGE_FROM_INITIATOR      0

/* Info structure sizes */
#define MOTIF_INITIATOR_INFO_SIZE         8
#define MOTIF_RECEIVER_INFO_SIZE         16

/* Message flags masks and shifts */
#define MOTIF_DND_ACTION_MASK        0x000F
#define MOTIF_DND_ACTION_SHIFT            0
#define MOTIF_DND_STATUS_MASK        0x00F0
#define MOTIF_DND_STATUS_SHIFT            4
#define MOTIF_DND_ACTIONS_MASK       0x0F00
#define MOTIF_DND_ACTIONS_SHIFT           8

/* message type constants */
#define TOP_LEVEL_ENTER    0
#define TOP_LEVEL_LEAVE    1
#define DRAG_MOTION        2
#define DROP_SITE_ENTER    3
#define DROP_SITE_LEAVE    4
#define DROP_START         5
#define DROP_FINISH        6
#define DRAG_DROP_FINISH   7
#define OPERATION_CHANGED  8

/* drop action constants */
#define MOTIF_DND_NOOP  0L
#define MOTIF_DND_MOVE  (1L << 0)
#define MOTIF_DND_COPY  (1L << 1)
#define MOTIF_DND_LINK  (1L << 2)

/* drop site status constants */
#define MOTIF_NO_DROP_SITE      1
#define MOTIF_INVALID_DROP_SITE 2
#define MOTIF_VALID_DROP_SITE   3

/* Shared atoms */

extern Atom XA_WM_STATE;
extern Atom XA_DELETE;

/* XDnD atoms */

extern Atom XA_XdndAware;
extern Atom XA_XdndProxy;

extern Atom XA_XdndEnter;
extern Atom XA_XdndPosition;
extern Atom XA_XdndLeave;
extern Atom XA_XdndDrop;
extern Atom XA_XdndStatus;
extern Atom XA_XdndFinished;

extern Atom XA_XdndTypeList;
extern Atom XA_XdndSelection;

extern Atom XA_XdndActionCopy;
extern Atom XA_XdndActionMove;
extern Atom XA_XdndActionLink;
extern Atom XA_XdndActionAsk;
extern Atom XA_XdndActionPrivate;
extern Atom XA_XdndActionList;

/* Motif DnD atoms */

extern Atom _XA_MOTIF_DRAG_WINDOW;
extern Atom _XA_MOTIF_DRAG_TARGETS;
extern Atom _XA_MOTIF_DRAG_INITIATOR_INFO;
extern Atom _XA_MOTIF_DRAG_RECEIVER_INFO;
extern Atom _XA_MOTIF_DRAG_AND_DROP_MESSAGE;
extern Atom XA_XmTRANSFER_SUCCESS;
extern Atom XA_XmTRANSFER_FAILURE;
extern Atom _XA_MOTIF_ATOM_0;

extern unsigned char MOTIF_BYTE_ORDER;

/* Motif DnD macros */

#define SWAP4BYTES(l) {\
        struct {\
          unsigned t :32;\
        } bit32;\
        char n, *tp = (char *) &bit32;\
        bit32.t = l;\
        n = tp[0]; tp[0] = tp[3]; tp[3] = n;\
        n = tp[1]; tp[1] = tp[2]; tp[2] = n;\
        l = bit32.t;\
}

#define SWAP2BYTES(s) {\
        struct {\
          unsigned t :16;\
        } bit16;\
        char n, *tp = (char *) &bit16;\
        bit16.t = s;\
        n = tp[0]; tp[0] = tp[1]; tp[1] = n;\
        s = bit16.t;\
}

typedef struct DropSiteInfo {
        Widget                  tlw;
        jobject                 component;
        Boolean                 isComposite;
        uint32_t                dsCnt;
} DropSiteInfo;

Boolean awt_dnd_init(Display* display);
Boolean awt_dnd_ds_init(Display* display);

Window get_awt_root_window();

/**************** checked_X* wrappers *****************************************/
unsigned char
checked_XChangeProperty(Display* display, Window w, Atom property, Atom type,
                        int format, int mode, unsigned char* data,
                        int nelements);

unsigned char
checked_XGetWindowProperty(Display* display, Window w, Atom property,
                           long long_offset, long long_length, Bool delete,
                           Atom req_type, Atom* actual_type_return,
                           int* actual_format_return,
                           unsigned long* nitems_return,
                           unsigned long* bytes_after_return,
                           unsigned char** prop_return);

unsigned char
checked_XSendEvent(Display* display, Window w, Bool propagate, long event_mask,
                   XEvent* event_send);

unsigned char
checked_XTranslateCoordinates(Display* display, Window src_w, Window dest_w,
                              int src_x, int src_y, int* dest_x_return,
                              int* dest_y_return, Window* child_return);

unsigned char
checked_XSelectInput(Display* display, Window w, long event_mask);
/******************************************************************************/

jint xdnd_to_java_action(Atom action);
Atom java_to_xdnd_action(jint action);

jint motif_to_java_actions(unsigned char action);
unsigned char java_to_motif_actions(jint action);

void write_card8(void** p, CARD8 value);
void write_card16(void** p, CARD16 value);
void write_card32(void** p, CARD32 value);

CARD8 read_card8(char* data, size_t offset);
CARD16 read_card16(char* data, size_t offset, char byte_order);
CARD32 read_card32(char* data, size_t offset, char byte_order);

Window get_motif_window(Display* dpy);

/*************************** TARGET LIST SUPPORT ***************************************/

int get_index_for_target_list(Display* dpy, Atom* targets, unsigned int num_targets);
void get_target_list_for_index(Display* dpy, int index, Atom** targets, unsigned
                               int* num_targets);

/***************************************************************************************/

Boolean awt_dnd_process_event(XEvent* event);
Boolean awt_dnd_ds_process_event(XEvent* event);
Boolean awt_dnd_dt_process_event(XEvent* event);

Window awt_dnd_ds_get_source_window();

/**************************** XEmbed server DnD support ***********************/
void set_proxy_mode_source_window(Window window);
/******************************************************************************/
