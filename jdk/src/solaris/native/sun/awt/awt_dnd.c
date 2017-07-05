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

#include "awt_p.h"

#include "java_awt_dnd_DnDConstants.h"

/* Shared atoms */

Atom XA_WM_STATE;
Atom XA_DELETE;

/* XDnD atoms */

Atom XA_XdndAware;
Atom XA_XdndProxy;

Atom XA_XdndEnter;
Atom XA_XdndPosition;
Atom XA_XdndLeave;
Atom XA_XdndDrop;
Atom XA_XdndStatus;
Atom XA_XdndFinished;

Atom XA_XdndTypeList;
Atom XA_XdndSelection;

Atom XA_XdndActionCopy;
Atom XA_XdndActionMove;
Atom XA_XdndActionLink;
Atom XA_XdndActionAsk;
Atom XA_XdndActionPrivate;
Atom XA_XdndActionList;

/* Motif DnD atoms */

Atom _XA_MOTIF_DRAG_WINDOW;
Atom _XA_MOTIF_DRAG_TARGETS;
Atom _XA_MOTIF_DRAG_INITIATOR_INFO;
Atom _XA_MOTIF_DRAG_RECEIVER_INFO;
Atom _XA_MOTIF_DRAG_AND_DROP_MESSAGE;
Atom _XA_MOTIF_ATOM_0;
Atom XA_XmTRANSFER_SUCCESS;
Atom XA_XmTRANSFER_FAILURE;

unsigned char MOTIF_BYTE_ORDER = 0;

static Window awt_root_window = None;

static Boolean
init_atoms(Display* display) {
    struct atominit {
        Atom *atomptr;
        const char *name;
    };

    /* Add new atoms to this list */
    static struct atominit atom_list[] = {
        /* Shared atoms */
        { &XA_WM_STATE,                     "WM_STATE"                     },
        { &XA_DELETE,                       "DELETE"                       },

        /* XDnD atoms */
        { &XA_XdndAware,                    "XdndAware"                    },
        { &XA_XdndProxy,                    "XdndProxy"                    },
        { &XA_XdndEnter,                    "XdndEnter"                    },
        { &XA_XdndPosition,                 "XdndPosition"                 },
        { &XA_XdndLeave,                    "XdndLeave"                    },
        { &XA_XdndDrop,                     "XdndDrop"                     },
        { &XA_XdndStatus,                   "XdndStatus"                   },
        { &XA_XdndFinished,                 "XdndFinished"                 },
        { &XA_XdndTypeList,                 "XdndTypeList"                 },
        { &XA_XdndSelection,                "XdndSelection"                },
        { &XA_XdndActionCopy,               "XdndActionCopy"               },
        { &XA_XdndActionMove,               "XdndActionMove"               },
        { &XA_XdndActionLink,               "XdndActionLink"               },
        { &XA_XdndActionAsk,                "XdndActionAsk"                },
        { &XA_XdndActionPrivate,            "XdndActionPrivate"            },
        { &XA_XdndActionList,               "XdndActionList"               },

        /* Motif DnD atoms */
        { &_XA_MOTIF_DRAG_WINDOW,           "_MOTIF_DRAG_WINDOW"           },
        { &_XA_MOTIF_DRAG_TARGETS,          "_MOTIF_DRAG_TARGETS"          },
        { &_XA_MOTIF_DRAG_INITIATOR_INFO,   "_MOTIF_DRAG_INITIATOR_INFO"   },
        { &_XA_MOTIF_DRAG_RECEIVER_INFO,    "_MOTIF_DRAG_RECEIVER_INFO"    },
        { &_XA_MOTIF_DRAG_AND_DROP_MESSAGE, "_MOTIF_DRAG_AND_DROP_MESSAGE" },
        { &_XA_MOTIF_ATOM_0,                "_MOTIF_ATOM_0"                },
        { &XA_XmTRANSFER_SUCCESS,           "XmTRANSFER_SUCCESS"           },
        { &XA_XmTRANSFER_FAILURE,           "XmTRANSFER_FAILURE"           }
    };

#define ATOM_LIST_LENGTH (sizeof(atom_list)/sizeof(atom_list[0]))

    const char *names[ATOM_LIST_LENGTH];
    Atom atoms[ATOM_LIST_LENGTH];
    Status status;
    size_t i;

    /* Fill the array of atom names */
    for (i = 0; i < ATOM_LIST_LENGTH; ++i) {
        names[i] = atom_list[i].name;
    }

    DTRACE_PRINT2("%s:%d initializing atoms ... ", __FILE__, __LINE__);

    status = XInternAtoms(awt_display, (char**)names, ATOM_LIST_LENGTH,
                          False, atoms);
    if (status == 0) {
        DTRACE_PRINTLN("failed");
        return False;
    }

    /* Store returned atoms into corresponding global variables */
    DTRACE_PRINTLN("ok");
    for (i = 0; i < ATOM_LIST_LENGTH; ++i) {
        *atom_list[i].atomptr = atoms[i];
    }

    return True;
#undef ATOM_LIST_LENGTH
}

/*
 * NOTE: must be called after awt_root_shell is created and realized.
 */
Boolean
awt_dnd_init(Display* display) {
    static Boolean inited = False;

    if (!inited) {
        Boolean atoms_inited = False;
        Boolean ds_inited = False;
        unsigned int value = 1;
        MOTIF_BYTE_ORDER = (*((char*)&value) != 0) ? 'l' : 'B';

        /* NOTE: init_atoms() should be called before the rest of initialization
           so that atoms can be used. */
        inited = init_atoms(display);

        if (inited) {
            if (XtIsRealized(awt_root_shell)) {
                awt_root_window = XtWindow(awt_root_shell);
            } else {
                inited = False;
            }
        }

        inited = inited && awt_dnd_ds_init(display);
    }

    return inited;
}

/*
 * Returns a window of awt_root_shell.
 */
Window
get_awt_root_window() {
    return awt_root_window;
}

static unsigned char local_xerror_code = Success;

static int
xerror_handler(Display *dpy, XErrorEvent *err) {
    local_xerror_code = err->error_code;
    return 0;
}

/**************** checked_X* wrappers *****************************************/
#undef NO_SYNC
#undef SYNC_TRACE

unsigned char
checked_XChangeProperty(Display* display, Window w, Atom property, Atom type,
                        int format, int mode, unsigned char* data,
                        int nelements) {
    XErrorHandler xerror_saved_handler;

#ifndef NO_SYNC
    XSync(display, False);
#ifdef SYNC_TRACE
    fprintf(stderr,"XSync 1\n");
#endif
#endif
    local_xerror_code = Success;
    xerror_saved_handler = XSetErrorHandler(xerror_handler);

    XChangeProperty(display, w, property, type, format, mode, data, nelements);

#ifndef NO_SYNC
    XSync(display, False);
#ifdef SYNC_TRACE
    fprintf(stderr,"XSync 2\n");
#endif
#endif
    XSetErrorHandler(xerror_saved_handler);

    return local_xerror_code;
}

unsigned char
checked_XGetWindowProperty(Display* display, Window w, Atom property, long long_offset,
                           long long_length, Bool delete, Atom req_type,
                           Atom* actual_type_return, int* actual_format_return,
                           unsigned long* nitems_return, unsigned long* bytes_after_return,
                           unsigned char** prop_return) {

    XErrorHandler xerror_saved_handler;
    int ret_val = Success;

#ifndef NO_SYNC
    XSync(display, False);
#ifdef SYNC_TRACE
    fprintf(stderr,"XSync 3\n");
#endif
#endif
    local_xerror_code = Success;
    xerror_saved_handler = XSetErrorHandler(xerror_handler);

    ret_val = XGetWindowProperty(display, w, property, long_offset, long_length,
                                 delete, req_type, actual_type_return,
                                 actual_format_return, nitems_return,
                                 bytes_after_return, prop_return);

#ifndef NO_SYNC
    XSync(display, False);
#ifdef SYNC_TRACE
    fprintf(stderr,"XSync 4\n");
#endif
#endif
    XSetErrorHandler(xerror_saved_handler);

    return ret_val != Success ? local_xerror_code : Success;
}

unsigned char
checked_XSendEvent(Display* display, Window w, Bool propagate, long event_mask,
                   XEvent* event_send) {

    XErrorHandler xerror_saved_handler;
    Status ret_val = 0;

#ifndef NO_SYNC
    XSync(display, False);
#ifdef SYNC_TRACE
    fprintf(stderr,"XSync 5\n");
#endif
#endif
    local_xerror_code = Success;
    xerror_saved_handler = XSetErrorHandler(xerror_handler);

    ret_val = XSendEvent(display, w, propagate, event_mask, event_send);

#ifndef NO_SYNC
    XSync(display, False);
#ifdef SYNC_TRACE
    fprintf(stderr,"XSync 6\n");
#endif
#endif
    XSetErrorHandler(xerror_saved_handler);

    return ret_val == 0 ? local_xerror_code : Success;
}

/*
 * NOTE: returns Success even if the two windows aren't on the same screen.
 */
unsigned char
checked_XTranslateCoordinates(Display* display, Window src_w, Window dest_w,
                              int src_x, int src_y, int* dest_x_return,
                              int* dest_y_return, Window* child_return) {

    XErrorHandler xerror_saved_handler;
    Bool ret_val = True;

#ifndef NO_SYNC
    XSync(display, False);
#ifdef SYNC_TRACE
    fprintf(stderr,"XSync 7\n");
#endif
#endif
    local_xerror_code = Success;
    xerror_saved_handler = XSetErrorHandler(xerror_handler);

    ret_val = XTranslateCoordinates(display, src_w, dest_w, src_x, src_y,
                                    dest_x_return, dest_y_return, child_return);

#ifndef NO_SYNC
    XSync(display, False);
#ifdef SYNC_TRACE
    fprintf(stderr,"XSync 8\n");
#endif
#endif
    XSetErrorHandler(xerror_saved_handler);

    return local_xerror_code;
}

unsigned char
checked_XSelectInput(Display* display, Window w, long event_mask) {
    XErrorHandler xerror_saved_handler;
    Bool ret_val = True;

#ifndef NO_SYNC
    XSync(display, False);
#ifdef SYNC_TRACE
    fprintf(stderr,"XSync 7\n");
#endif
#endif
    local_xerror_code = Success;
    xerror_saved_handler = XSetErrorHandler(xerror_handler);

    XSelectInput(display, w, event_mask);

#ifndef NO_SYNC
    XSync(display, False);
#ifdef SYNC_TRACE
    fprintf(stderr,"XSync 8\n");
#endif
#endif
    XSetErrorHandler(xerror_saved_handler);

    return local_xerror_code;
}
/******************************************************************************/

jint
xdnd_to_java_action(Atom action) {
    if (action == XA_XdndActionCopy) {
        return java_awt_dnd_DnDConstants_ACTION_COPY;
    } else if (action == XA_XdndActionMove) {
        return java_awt_dnd_DnDConstants_ACTION_MOVE;
    } else if (action == XA_XdndActionLink) {
        return java_awt_dnd_DnDConstants_ACTION_LINK;
    } else if (action == None) {
        return java_awt_dnd_DnDConstants_ACTION_NONE;
    } else {
        /* XdndActionCopy is the default. */
        return java_awt_dnd_DnDConstants_ACTION_COPY;
    }
}

Atom
java_to_xdnd_action(jint action) {
    switch (action) {
    case java_awt_dnd_DnDConstants_ACTION_COPY: return XA_XdndActionCopy;
    case java_awt_dnd_DnDConstants_ACTION_MOVE: return XA_XdndActionMove;
    case java_awt_dnd_DnDConstants_ACTION_LINK: return XA_XdndActionLink;
    default:                                    return None;
    }
}

void
write_card8(void** p, CARD8 value) {
    CARD8** card8_pp = (CARD8**)p;
    **card8_pp = value;
    (*card8_pp)++;
}

void
write_card16(void** p, CARD16 value) {
    CARD16** card16_pp = (CARD16**)p;
    **card16_pp = value;
    (*card16_pp)++;
}

void
write_card32(void** p, CARD32 value) {
    CARD32** card32_pp = (CARD32**)p;
    **card32_pp = value;
    (*card32_pp)++;
}

CARD8
read_card8(char* data, size_t offset) {
    return *((CARD8*)(data + offset));
}

CARD16
read_card16(char* data, size_t offset, char byte_order) {
    CARD16 card16 = *((CARD16*)(data + offset));

    if (byte_order != MOTIF_BYTE_ORDER) {
        SWAP2BYTES(card16);
    }

    return card16;
}

CARD32
read_card32(char* data, size_t offset, char byte_order) {
    CARD32 card32 = *((CARD32*)(data + offset));

    if (byte_order != MOTIF_BYTE_ORDER) {
        SWAP4BYTES(card32);
    }

    return card32;
}

static Window
read_motif_window(Display* dpy) {
    Window         root_window = DefaultRootWindow(dpy);
    Window         motif_window = None;

    unsigned char  ret;
    Atom           type;
    int            format;
    unsigned long  nitems;
    unsigned long  after;
    unsigned char  *data;

    ret = checked_XGetWindowProperty(dpy, root_window, _XA_MOTIF_DRAG_WINDOW,
                                     0, 0xFFFF, False, AnyPropertyType, &type,
                                     &format, &nitems, &after, &data);

    if (ret != Success) {
        DTRACE_PRINTLN2("%s:%d Failed to read _MOTIF_DRAG_WINDOW.",
                        __FILE__, __LINE__);
        return None;
    }


    if (type == XA_WINDOW && format == 32 && nitems == 1) {
        motif_window = *((Window*)data);
    }

    XFree ((char *)data);

    return motif_window;
}

static Window
create_motif_window(Display* dpy) {
    Window   root_window = DefaultRootWindow(dpy);
    Window   motif_window = None;
    Display* display = NULL;
    XSetWindowAttributes swa;

    display = XOpenDisplay(XDisplayString(dpy));
    if (display == NULL) {
        return None;
    }

    XGrabServer(display);

    XSetCloseDownMode(display, RetainPermanent);

    swa.override_redirect = True;
    swa.event_mask = PropertyChangeMask;
    motif_window = XCreateWindow(display, root_window,
                                 -10, -10, 1, 1, 0, 0,
                                 InputOnly, CopyFromParent,
                                 (CWOverrideRedirect|CWEventMask),
                                 &swa);
    XMapWindow(display, motif_window);

    XChangeProperty(display, root_window, _XA_MOTIF_DRAG_WINDOW, XA_WINDOW, 32,
                    PropModeReplace, (unsigned char *)&motif_window, 1);

    XUngrabServer(display);

    XCloseDisplay(display);

    return motif_window;
}

Window
get_motif_window(Display* dpy) {
    /*
     * Note: it is unsafe to cache the motif drag window handle, as another
     * client can change the _MOTIF_DRAG_WINDOW property on the root, the handle
     * becomes out-of-sync and all subsequent drag operations will fail.
     */
    Window motif_window = read_motif_window(dpy);
    if (motif_window == None) {
        motif_window = create_motif_window(dpy);
    }

    return motif_window;
}

typedef struct {
    CARD16 num_targets;
    Atom* targets;
} TargetsTableEntry;

typedef struct {
    CARD16 num_entries;
    TargetsTableEntry* entries;
} TargetsTable;

typedef struct {
    CARD8       byte_order;
    CARD8       protocol_version;
    CARD16      num_entries B16;
    CARD32      heap_offset B32;
} TargetsPropertyRec;

static TargetsTable*
get_target_list_table(Display* dpy) {
    Window motif_window = get_motif_window(dpy);
    TargetsTable* targets_table = NULL;
    TargetsPropertyRec* targets_property_rec_ptr;
    char* bufptr;

    unsigned char  ret;
    Atom           type;
    int            format;
    unsigned long  nitems;
    unsigned long  after;
    unsigned char  *data;
    unsigned int   i, j;

    ret = checked_XGetWindowProperty(dpy, motif_window, _XA_MOTIF_DRAG_TARGETS,
                                     0L, 100000L, False, _XA_MOTIF_DRAG_TARGETS,
                                     &type, &format, &nitems, &after,
                                     (unsigned char**)&targets_property_rec_ptr);

    if (ret != Success || type != _XA_MOTIF_DRAG_TARGETS ||
        targets_property_rec_ptr == NULL) {

        DTRACE_PRINT2("%s:%d Cannot read _XA_MOTIF_DRAG_TARGETS", __FILE__, __LINE__);
        return NULL;
    }

    if (targets_property_rec_ptr->protocol_version !=
        MOTIF_DND_PROTOCOL_VERSION) {
        DTRACE_PRINT2("%s:%d incorrect protocol version", __FILE__, __LINE__);
        return NULL;
    }

    if (targets_property_rec_ptr->byte_order != MOTIF_BYTE_ORDER) {
        SWAP2BYTES(targets_property_rec_ptr->num_entries);
        SWAP4BYTES(targets_property_rec_ptr->heap_offset);
    }

    targets_table = (TargetsTable*)malloc(sizeof(TargetsTable));
    if (targets_table == NULL) {
        DTRACE_PRINT2("%s:%d malloc failed", __FILE__, __LINE__);
        return NULL;
    }
    targets_table->num_entries = targets_property_rec_ptr->num_entries;
    targets_table->entries =
        (TargetsTableEntry*)malloc(sizeof(TargetsTableEntry) *
                                   targets_property_rec_ptr->num_entries);
    if (targets_table->entries == NULL) {
        DTRACE_PRINT2("%s:%d malloc failed", __FILE__, __LINE__);
        free(targets_table);
        return NULL;
    }

    bufptr = (char *)targets_property_rec_ptr + sizeof(TargetsPropertyRec);
    for (i = 0; i < targets_table->num_entries; i++) {
        CARD16 num_targets;
        Atom* targets;
        memcpy(&num_targets, bufptr, 2 );
        bufptr += 2;
        if (targets_property_rec_ptr->byte_order != MOTIF_BYTE_ORDER) {
            SWAP2BYTES(num_targets);
        }

        targets = (Atom*)malloc(sizeof(Atom) * num_targets);
        if (targets == NULL) {
            DTRACE_PRINT2("%s:%d malloc failed", __FILE__, __LINE__);
            free(targets_table->entries);
            free(targets_table);
            return NULL;
        }
        for (j = 0; j < num_targets; j++) {
            CARD32 target;
            memcpy(&target, bufptr, 4 );
            bufptr += 4;
            if (targets_property_rec_ptr->byte_order != MOTIF_BYTE_ORDER) {
                SWAP4BYTES(target);
            }
            targets[j] = (Atom)target;
        }

        targets_table->entries[i].num_targets = num_targets;
        targets_table->entries[i].targets = targets;
    }

    free(targets_property_rec_ptr);

    return targets_table;
}

static void
put_target_list_table(Display* dpy, TargetsTable* table) {
    Window motif_window = get_motif_window(dpy);
    TargetsPropertyRec* targets_property_rec_ptr;
    size_t table_size = sizeof(TargetsPropertyRec);
    unsigned char ret;
    int i, j;
    char* buf;

    for (i = 0; i < table->num_entries; i++) {
        table_size += table->entries[i].num_targets * sizeof(Atom) + 2;
    }

    targets_property_rec_ptr = (TargetsPropertyRec*)malloc(table_size);
    if (targets_property_rec_ptr == NULL) {
        DTRACE_PRINT2("%s:%d malloc failed", __FILE__, __LINE__);
        return;
    }
    targets_property_rec_ptr->byte_order = MOTIF_BYTE_ORDER;
    targets_property_rec_ptr->protocol_version = MOTIF_DND_PROTOCOL_VERSION;
    targets_property_rec_ptr->num_entries = table->num_entries;
    targets_property_rec_ptr->heap_offset = table_size;

    buf = (char*)targets_property_rec_ptr + sizeof(TargetsPropertyRec);

    for (i = 0; i < table->num_entries; i++) {
        CARD16 num_targets = table->entries[i].num_targets;
        memcpy(buf, &num_targets, 2);
        buf += 2;

        for (j = 0; j < num_targets; j++) {
            CARD32 target = table->entries[i].targets[j];
            memcpy(buf, &target, 4);
            buf += 4;
        }
    }

    ret = checked_XChangeProperty(dpy, motif_window, _XA_MOTIF_DRAG_TARGETS,
                                  _XA_MOTIF_DRAG_TARGETS, 8, PropModeReplace,
                                  (unsigned char*)targets_property_rec_ptr,
                                  (int)table_size);

    if (ret != Success) {
        DTRACE_PRINT2("%s:%d XChangeProperty failed", __FILE__, __LINE__);
    }

    XtFree((char*)targets_property_rec_ptr);
}

static int
_compare(const void* p1, const void* p2) {
    long diff = *(Atom*)p1 - *(Atom*)p2;

    if (diff > 0) {
        return 1;
    } else if (diff < 0) {
        return -1;
    } else {
        return 0;
    }
}

/*
 * Returns the index for the specified target list or -1 on failure.
 */
int
get_index_for_target_list(Display* dpy, Atom* targets, unsigned int num_targets) {
    TargetsTable* targets_table = NULL;
    Atom* sorted_targets = NULL;
    int i, j;
    int ret = -1;

    if (targets == NULL && num_targets > 0) {
        DTRACE_PRINT4("%s:%d targets=%X num_targets=%d",
                      __FILE__, __LINE__, targets, num_targets);
        return -1;
    }

    if (num_targets > 0) {
        sorted_targets = (Atom*)malloc(sizeof(Atom) * num_targets);
        if (sorted_targets == NULL) {
            DTRACE_PRINT2("%s:%d malloc failed.", __FILE__, __LINE__);
            return -1;
        }

        memcpy(sorted_targets, targets, sizeof(Atom) * num_targets);
        qsort ((void *)sorted_targets, (size_t)num_targets, (size_t)sizeof(Atom),
               _compare);
    }

    XGrabServer(dpy);
    targets_table = get_target_list_table(dpy);

    if (targets_table != NULL) {
        for (i = 0; i < targets_table->num_entries; i++) {
            TargetsTableEntry* entry_ptr = &targets_table->entries[i];
            Boolean equals = True;
            if (num_targets == entry_ptr->num_targets) {
                for (j = 0; j < entry_ptr->num_targets; j++) {
                    if (sorted_targets[j] != entry_ptr->targets[j]) {
                        equals = False;
                        break;
                    }
                }
            } else {
                equals = False;
            }

            if (equals) {
                XUngrabServer(dpy);
                /* Workaround for bug 5039226 */
                XSync(dpy, False);
                free((char*)sorted_targets);
                return i;
            }
        }
    } else {
        targets_table = (TargetsTable*)malloc(sizeof(TargetsTable));
        targets_table->num_entries = 0;
        targets_table->entries = NULL;
    }

    /* Index not found - expand the table. */
    targets_table->entries =
        (TargetsTableEntry*)realloc((char*)targets_table->entries,
                                    sizeof(TargetsTableEntry) *
                                    (targets_table->num_entries + 1));
    if (targets_table->entries == NULL) {
        DTRACE_PRINT2("%s:%d realloc failed.", __FILE__, __LINE__);
        XUngrabServer(dpy);
        /* Workaround for bug 5039226 */
        XSync(dpy, False);
        free((char*)sorted_targets);
        return -1;
    }

    /* Fill in the new entry */
    {
        TargetsTableEntry* new_entry =
            &targets_table->entries[targets_table->num_entries];

        new_entry->num_targets = num_targets;
        if (num_targets > 0) {
            new_entry->targets = (Atom*)malloc(sizeof(Atom) * num_targets);
            if (new_entry->targets == NULL) {
                DTRACE_PRINT2("%s:%d malloc failed.", __FILE__, __LINE__);
                XUngrabServer(dpy);
                /* Workaround for bug 5039226 */
                XSync(dpy, False);
                free((char*)sorted_targets);
                return -1;
            }
            memcpy(new_entry->targets, sorted_targets,
                   sizeof(Atom) * num_targets);
        } else {
            new_entry->targets = NULL;
        }
    }

    targets_table->num_entries++;

    put_target_list_table(dpy, targets_table);

    XUngrabServer(dpy);
    /* Workaround for bug 5039226 */
    XSync(dpy, False);

    ret = targets_table->num_entries - 1;

    free((char*)sorted_targets);

    for (i = 0; i < targets_table->num_entries; i++) {
        free((char*)targets_table->entries[i].targets);
    }

    free((char*)targets_table->entries);
    free((char*)targets_table);
    return ret;
}

/*
 * Retrieves the target list for the specified index.
 * Stores the number of targets in the list to 'num_targets' and the targets
 * to 'targets'. On failure stores 0 and NULL respectively.
 * The caller should free the allocated array when done with it.
 */
void
get_target_list_for_index(Display* dpy, int index, Atom** targets, unsigned int* num_targets) {
    TargetsTable* table = get_target_list_table(dpy);
    TargetsTableEntry* entry = NULL;

    if (table == NULL) {
        DTRACE_PRINT2("%s:%d No target table.", __FILE__, __LINE__);
        *targets = NULL;
        *num_targets = 0;
        return;
    }

    if (table->num_entries <= index) {
        DTRACE_PRINT4("%s:%d index out of bounds idx=%d entries=%d",
                      __FILE__, __LINE__, index, table->num_entries);
        *targets = NULL;
        *num_targets = 0;
        return;
    }

    entry = &table->entries[index];

    *targets = (Atom*)malloc(entry->num_targets * sizeof(Atom));

    if (*targets == NULL) {
        DTRACE_PRINT2("%s:%d malloc failed.", __FILE__, __LINE__);
        *num_targets = 0;
        return;
    }

    memcpy(*targets, entry->targets, entry->num_targets * sizeof(Atom));
    *num_targets = entry->num_targets;
}

jint
motif_to_java_actions(unsigned char motif_action) {
    jint java_action = java_awt_dnd_DnDConstants_ACTION_NONE;

    if (motif_action & MOTIF_DND_COPY) {
        java_action |= java_awt_dnd_DnDConstants_ACTION_COPY;
    }

    if (motif_action & MOTIF_DND_MOVE) {
        java_action |= java_awt_dnd_DnDConstants_ACTION_MOVE;
    }

    if (motif_action & MOTIF_DND_LINK) {
        java_action |= java_awt_dnd_DnDConstants_ACTION_LINK;
    }

    return java_action;
}

unsigned char
java_to_motif_actions(jint java_action) {
    unsigned char motif_action = MOTIF_DND_NOOP;

    if (java_action & java_awt_dnd_DnDConstants_ACTION_COPY) {
        motif_action |= MOTIF_DND_COPY;
    }

    if (java_action & java_awt_dnd_DnDConstants_ACTION_MOVE) {
        motif_action |= MOTIF_DND_MOVE;
    }

    if (java_action & java_awt_dnd_DnDConstants_ACTION_LINK) {
        motif_action |= MOTIF_DND_LINK;
    }

    return motif_action;
}

Boolean
awt_dnd_process_event(XEvent* event) {
    Boolean ret = awt_dnd_ds_process_event(event) ||
        awt_dnd_dt_process_event(event);

    /* Extract the event from the queue if it is processed. */
    if (ret) {
        XNextEvent(event->xany.display, event);
    }

    return ret;
}
