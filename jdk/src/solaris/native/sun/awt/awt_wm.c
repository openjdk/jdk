/*
 * Copyright (c) 2001, 2006, Oracle and/or its affiliates. All rights reserved.
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

#ifdef HEADLESS
    #error This file should not be included in headless library
#endif

/*
 * Some SCIENCE stuff happens, and it is CONFUSING
 */

#include "awt_p.h"

#include <X11/Xproto.h>
#include <X11/Xlib.h>
#include <X11/Xatom.h>
#include <Xm/MwmUtil.h>

/* JNI headers */
#include "java_awt_Frame.h"     /* for frame state constants */

#include "awt_wm.h"
#include "awt_util.h"           /* for X11 error handling macros */

/*
 * NB: 64 bit awareness.
 *
 * Since this code reads/writes window properties heavily, one thing
 * should be noted well.  Xlib uses C type 'long' for properties of
 * format 32.  Fortunately, typedef for Atom is 'long' as well, so
 * passing property data as or casting returned property data to
 * arrays of atoms is safe.
 */


/*
 * Atoms used to communicate with window manager(s).
 * Naming convention:
 *   o  for atom  "FOO" the variable is  "XA_FOO"
 *   o  for atom "_BAR" the variable is "_XA_BAR"
 * Don't forget to add initialization to awt_wm_initAtoms below.
 */

/*
 * Before WM rototill JDK used to check for running WM by just testing
 * if certain atom is interned or not.  We'd better not confuse older
 * JDK by interning these atoms.  Use awt_wm_atomInterned() to intern
 * them lazily.
 *
 * ENLIGHTENMENT_COMMS
 * _ICEWM_WINOPTHINT
 * _SAWMILL_TIMESTAMP
 * _DT_SM_WINDOW_INFO
 * _MOTIF_WM_INFO
 * _SUN_WM_PROTOCOLS
 */

/* Good old ICCCM */
static Atom XA_WM_STATE;

/* New "netwm" spec from www.freedesktop.org */
static Atom XA_UTF8_STRING;     /* like STRING but encoding is UTF-8 */
static Atom _XA_NET_SUPPORTING_WM_CHECK;
static Atom _XA_NET_SUPPORTED;  /* list of protocols (property of root) */
static Atom _XA_NET_WM_NAME;    /* window property */
static Atom _XA_NET_WM_STATE;   /* both window property and request */

/*
 * _NET_WM_STATE is a list of atoms.
 * NB: Standard spelling is "HORZ" (yes, without an 'I'), but KDE2
 * uses misspelled "HORIZ" (see KDE bug #20229).  This was fixed in
 * KDE 2.2.  Under earlier versions of KDE2 horizontal and full
 * maximization doesn't work .
 */
static Atom _XA_NET_WM_STATE_MAXIMIZED_HORZ;
static Atom _XA_NET_WM_STATE_MAXIMIZED_VERT;
static Atom _XA_NET_WM_STATE_SHADED;
static Atom _XA_NET_WM_STATE_ABOVE;
static Atom _XA_NET_WM_STATE_BELOW;
static Atom _XA_NET_WM_STATE_HIDDEN;

/* Currently we only care about max_v and max_h in _NET_WM_STATE */
#define AWT_NET_N_KNOWN_STATES 2

/* Gnome WM spec (superseded by "netwm" above, but still in use) */
static Atom _XA_WIN_SUPPORTING_WM_CHECK;
static Atom _XA_WIN_PROTOCOLS;
static Atom _XA_WIN_STATE;
static Atom _XA_WIN_LAYER;

/* Enlightenment */
static Atom _XA_E_FRAME_SIZE;

/* KWin (KDE2) */
static Atom _XA_KDE_NET_WM_FRAME_STRUT;

/* KWM (KDE 1.x) OBSOLETE??? */
static Atom XA_KWM_WIN_ICONIFIED;
static Atom XA_KWM_WIN_MAXIMIZED;

/* OpenLook */
static Atom _XA_OL_DECOR_DEL;
static Atom _XA_OL_DECOR_HEADER;
static Atom _XA_OL_DECOR_RESIZE;
static Atom _XA_OL_DECOR_PIN;
static Atom _XA_OL_DECOR_CLOSE;

/* For _NET_WM_STATE ClientMessage requests */
#define _NET_WM_STATE_REMOVE    0 /* remove/unset property */
#define _NET_WM_STATE_ADD       1 /* add/set property      */
#define _NET_WM_STATE_TOGGLE    2 /* toggle property       */

/* _WIN_STATE bits */
#define WIN_STATE_STICKY          (1<<0) /* everyone knows sticky            */
#define WIN_STATE_MINIMIZED       (1<<1) /* Reserved - definition is unclear */
#define WIN_STATE_MAXIMIZED_VERT  (1<<2) /* window in maximized V state      */
#define WIN_STATE_MAXIMIZED_HORIZ (1<<3) /* window in maximized H state      */
#define WIN_STATE_HIDDEN          (1<<4) /* not on taskbar but window visible*/
#define WIN_STATE_SHADED          (1<<5) /* shaded (MacOS / Afterstep style) */
#define WIN_LAYER_ONTOP           6
#define WIN_LAYER_NORMAL          4

#define  URGENCY_HINT             (1<<8)
#define  LAYER_ALWAYS_ON_TOP      1
#define  LAYER_NORMAL             0


/*
 * Intern a bunch of atoms we are going use.
 */
static void
awt_wm_initAtoms(void)
{
    /* Minimize X traffic by creating atoms en mass...  This requires
       slightly more code but reduces number of server requests. */
    struct atominit {
        Atom *atomptr;
        const char *name;
    };

    /* Just add new atoms to this list */
    static struct atominit atom_list[] = {
        { &XA_WM_STATE,                      "WM_STATE"                      },

        { &XA_UTF8_STRING,                   "UTF8_STRING"                   },

        { &_XA_NET_SUPPORTING_WM_CHECK,      "_NET_SUPPORTING_WM_CHECK"      },
        { &_XA_NET_SUPPORTED,                "_NET_SUPPORTED"                },
        { &_XA_NET_WM_STATE,                 "_NET_WM_STATE"                 },
        { &_XA_NET_WM_STATE_MAXIMIZED_VERT,  "_NET_WM_STATE_MAXIMIZED_VERT"  },
        { &_XA_NET_WM_STATE_MAXIMIZED_HORZ,  "_NET_WM_STATE_MAXIMIZED_HORZ"  },
        { &_XA_NET_WM_STATE_SHADED,          "_NET_WM_STATE_SHADED"          },
        { &_XA_NET_WM_STATE_ABOVE,           "_NET_WM_STATE_ABOVE"           },
        { &_XA_NET_WM_STATE_BELOW,           "_NET_WM_STATE_BELOW"           },
        { &_XA_NET_WM_STATE_HIDDEN,          "_NET_WM_STATE_HIDDEN"          },
        { &_XA_NET_WM_NAME,                  "_NET_WM_NAME"                  },

        { &_XA_WIN_SUPPORTING_WM_CHECK,      "_WIN_SUPPORTING_WM_CHECK"      },
        { &_XA_WIN_PROTOCOLS,                "_WIN_PROTOCOLS"                },
        { &_XA_WIN_STATE,                    "_WIN_STATE"                    },
        { &_XA_WIN_LAYER,                    "_WIN_LAYER"                    },

        { &_XA_KDE_NET_WM_FRAME_STRUT,       "_KDE_NET_WM_FRAME_STRUT"       },

        { &_XA_E_FRAME_SIZE,                 "_E_FRAME_SIZE"                 },

        { &XA_KWM_WIN_ICONIFIED,             "KWM_WIN_ICONIFIED"             },
        { &XA_KWM_WIN_MAXIMIZED,             "KWM_WIN_MAXIMIZED"             },

        { &_XA_OL_DECOR_DEL,                 "_OL_DECOR_DEL"                 },
        { &_XA_OL_DECOR_HEADER,              "_OL_DECOR_HEADER"              },
        { &_XA_OL_DECOR_RESIZE,              "_OL_DECOR_RESIZE"              },
        { &_XA_OL_DECOR_PIN,                 "_OL_DECOR_PIN"                 },
        { &_XA_OL_DECOR_CLOSE,               "_OL_DECOR_CLOSE"               }
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

    DTRACE_PRINT("WM: initializing atoms ...  ");
    status = XInternAtoms(awt_display, (char**)names, ATOM_LIST_LENGTH,
                          False, atoms);
    if (status == 0) {
        DTRACE_PRINTLN("failed");
        return;
    }

    /* Store returned atoms into corresponding global variables */
    DTRACE_PRINTLN("ok");
    for (i = 0; i < ATOM_LIST_LENGTH; ++i) {
        *atom_list[i].atomptr = atoms[i];
    }
#undef ATOM_LIST_LENGTH
}


/*
 * When checking for various WMs don't intern certain atoms we use to
 * distinguish those WMs.  Rather check if the atom is interned first.
 * If it's not, further tests are not necessary anyway.
 * This also saves older JDK a great deal of confusion (4487993).
 */
static Boolean
awt_wm_atomInterned(Atom *pa, const char *name)
{
    DASSERT(pa != NULL);
    if (*pa == None) {
        DASSERT(name != NULL);
        *pa = XInternAtom(awt_display, name, True);
        if (*pa == None) {
            DTRACE_PRINTLN1("\"%s\" is not interned", name);
            return False;
        } else {
            return True;
        }
    } else {
        return True;
    }
}



/*****************************************************************************\
 *
 * DTRACE utils for various states ...
 *
\*****************************************************************************/


static void
awt_wm_dtraceWMState(uint32_t wm_state)
{
#ifdef DEBUG
    DTRACE_PRINT("WM_STATE = ");
    switch (wm_state) {
      case WithdrawnState:
          DTRACE_PRINTLN("Withdrawn");
          break;
      case NormalState:
          DTRACE_PRINTLN("Normal");
          break;
      case IconicState:
          DTRACE_PRINTLN("Iconic");
          break;
      default:
          DTRACE_PRINTLN1("unknown state %d", wm_state);
          break;
    }
#endif /* DEBUG */
}

static void
awt_wm_dtraceStateNet(Atom *net_wm_state, unsigned long nitems)
{
#ifdef DEBUG
    unsigned long i;

    DTRACE_PRINT("_NET_WM_STATE = {");
    for (i = 0; i < nitems; ++i) {
        char *name, *print_name;
        name = XGetAtomName(awt_display, net_wm_state[i]);
        if (name == NULL) {
            print_name = "???";
        } else if (strncmp(name, "_NET_WM_STATE", 13) == 0) {
            print_name = name + 13; /* skip common prefix to reduce noice */
        } else {
            print_name = name;
        }
        DTRACE_PRINT1(" %s", print_name);
        if (name) {
            XFree(name);
        }
    }
    DTRACE_PRINTLN(" }");
#endif
}


static void
awt_wm_dtraceStateWin(uint32_t win_state)
{
#ifdef DEBUG
    DTRACE_PRINT("_WIN_STATE = {");
    if (win_state & WIN_STATE_STICKY) {
        DTRACE_PRINT(" STICKY");
    }
    if (win_state & WIN_STATE_MINIMIZED) {
        DTRACE_PRINT(" MINIMIZED");
    }
    if (win_state & WIN_STATE_MAXIMIZED_VERT) {
        DTRACE_PRINT(" MAXIMIZED_VERT");
    }
    if (win_state & WIN_STATE_MAXIMIZED_HORIZ) {
        DTRACE_PRINT(" MAXIMIZED_HORIZ");
    }
    if (win_state & WIN_STATE_HIDDEN) {
        DTRACE_PRINT(" HIDDEN");
    }
    if (win_state & WIN_STATE_SHADED) {
        DTRACE_PRINT(" SHADED");
    }
    DTRACE_PRINTLN(" }");
#endif
}


static void
awt_wm_dtraceStateJava(jint java_state)
{
#ifdef DEBUG
    DTRACE_PRINT("java state = ");
    if (java_state == java_awt_Frame_NORMAL) {
        DTRACE_PRINTLN("NORMAL");
    }
    else {
        DTRACE_PRINT("{");
        if (java_state & java_awt_Frame_ICONIFIED) {
            DTRACE_PRINT(" ICONIFIED");
        }
        if ((java_state & java_awt_Frame_MAXIMIZED_BOTH)
                       == java_awt_Frame_MAXIMIZED_BOTH)
        {
            DTRACE_PRINT(" MAXIMIZED_BOTH");
        }
        else if (java_state & java_awt_Frame_MAXIMIZED_HORIZ) {
            DTRACE_PRINT(" MAXIMIZED_HORIZ");
        }
        else if (java_state & java_awt_Frame_MAXIMIZED_VERT) {
            DTRACE_PRINT(" MAXIMIZED_VERT");
        }
        DTRACE_PRINTLN(" }");
    }
#endif /* DEBUG */
}



/*****************************************************************************\
 *
 * Utility functions ...
 *
\*****************************************************************************/

/*
 * Instead of validating window id, we simply call XGetWindowProperty,
 * but temporary install this function as the error handler to ignore
 * BadWindow error.
 */
int /* but ingored */
xerror_ignore_bad_window(Display *dpy, XErrorEvent *err)
{
    XERROR_SAVE(err);
    if (err->error_code == BadWindow) {
        DTRACE_PRINTLN("IGNORING BadWindow");
        return 0; /* ok to fail */
    }
    else {
        return (*xerror_saved_handler)(dpy, err);
    }
}


/*
 * Convenience wrapper for XGetWindowProperty for XA_ATOM properties.
 * E.g. WM_PROTOCOLS, _NET_WM_STATE, _OL_DECOR_DEL.
 * It's up to caller to XFree returned value.
 * Number of items returned is stored to nitems_ptr (if non-null).
 */
static Atom *
awt_getAtomListProperty(Window w, Atom property, unsigned long *nitems_ptr)
{
    /* Request status */
    int status;

    /* Returns of XGetWindowProperty */
    Atom actual_type;
    int actual_format;
    unsigned long nitems_stub;
    unsigned long bytes_after;
    Atom *list;

    if (nitems_ptr == NULL) {
        /* Caller is not interested in the number of items,
           provide a stub for XGetWindowProperty */
        nitems_ptr = &nitems_stub;
    }

    status = XGetWindowProperty(awt_display, w,
                 property, 0, 0xFFFF, False, XA_ATOM,
                 &actual_type, &actual_format, nitems_ptr, &bytes_after,
                 (unsigned char **)&list);

    if (status != Success || list == NULL) {
        *nitems_ptr = 0;
        return NULL;
    }

    if (actual_type != XA_ATOM || actual_format != 32) {
        XFree(list);
        *nitems_ptr = 0;
        return NULL;
    }

    if (*nitems_ptr == 0) {
        XFree(list);
        return NULL;
    }

    return list;
}


/*
 * Auxiliary function that returns the value of 'property' of type
 * 'property_type' on window 'w'.  Format of the property must be 8.
 * Terminating zero added by XGetWindowProperty is preserved.
 * It's up to caller to XFree the result.
 */
static unsigned char *
awt_getProperty8(Window w, Atom property, Atom property_type)
{
    /* Request status */
    int status;

    /* Returns of XGetWindowProperty */
    Atom actual_type;
    int actual_format;
    unsigned long nitems;
    unsigned long bytes_after;
    unsigned char *string;

    /* BadWindow is ok and will be blocked by our special handler */
    WITH_XERROR_HANDLER(xerror_ignore_bad_window);
    {
        status = XGetWindowProperty(awt_display, w,
                     property, 0, 0xFFFF, False, property_type,
                     &actual_type, &actual_format, &nitems, &bytes_after,
                     &string);
    }
    RESTORE_XERROR_HANDLER;

    if (status != Success || string == NULL) {
        return NULL;
    }

    if (actual_type != property_type || actual_format != 8) {
        XFree(string);
        return NULL;
    }

    /* XGetWindowProperty kindly supplies terminating zero */
    return string;
}


/*
 * Auxiliary function that returns the value of 'property' of type
 * 'property_type' on window 'w'.  Format of the property must be 32.
 */
static int32_t
awt_getProperty32(Window w, Atom property, Atom property_type)
{
    /* Property value*/
    int32_t value;

    /* Request status */
    int status;

    /* Returns of XGetWindowProperty */
    Atom actual_type;
    int actual_format;
    unsigned long nitems;
    unsigned long bytes_after;
    long *data;                 /* NB: 64 bit: Format 32 props are 'long' */

    /* BadWindow is ok and will be blocked by our special handler */
    WITH_XERROR_HANDLER(xerror_ignore_bad_window);
    {
        status = XGetWindowProperty(awt_display, w,
                     property, 0, 1, False, property_type,
                     &actual_type, &actual_format, &nitems, &bytes_after,
                     (unsigned char **)&data);
    }
    RESTORE_XERROR_HANDLER;

    if (status != Success || data == NULL) {
        return 0;
    }

    if (actual_type != property_type || actual_format != 32) {
        XFree(data);            /* NULL data already catched above */
        return 0;
    }

    value = (int32_t)*data;
    XFree(data);

    return value;
}



/*****************************************************************************\
 *
 * Detecting WM ...
 *
\*****************************************************************************/



/*
 * Check for anchor_prop(anchor_type) on root, take the value as the
 * window id and check if that window exists and has anchor_prop(anchor_type)
 * with the same value (i.e. pointing back to self).
 *
 * Returns the anchor window, as some WM may put interesting stuff in
 * its properties (e.g. sawfish).
 */
static Window
awt_wm_checkAnchor(Atom anchor_prop, Atom anchor_type)
{
    Window root_xref;
    Window self_xref;

    root_xref = (Window)awt_getProperty32(DefaultRootWindow(awt_display),
                                          anchor_prop, anchor_type);
    if (root_xref == None) {
        DTRACE_PRINTLN("no");
        return None;
    }

    DTRACE_PRINT1("0x%x ...  ", (unsigned int)root_xref);
    self_xref = (Window)awt_getProperty32(root_xref,
                                          anchor_prop, anchor_type);
    if (self_xref != root_xref) {
        DTRACE_PRINTLN("stale");
        return None;
    }

    DTRACE_PRINTLN("ok");
    return self_xref;
}


/*
 * New WM spec: KDE 2.0.1, sawfish 0.3x, ...
 * <http://www.freedesktop.org/standards/wm-spec.html>
 */
static Window
awt_wm_isNetSupporting(void)
{
    static Boolean checked = False;
    static Window isNetSupporting = None;

    if (checked) {
        return isNetSupporting;
    }

    DTRACE_PRINT("WM: checking for _NET_SUPPORTING ...  ");
    isNetSupporting = awt_wm_checkAnchor(_XA_NET_SUPPORTING_WM_CHECK,
                                         XA_WINDOW);
    checked = True;
    return isNetSupporting;
}


/*
 * Old Gnome WM spec: WindowMaker, Enlightenment, IceWM ...
 * <http://developer.gnome.org/doc/standards/wm/book1.html>
 */
static Window
awt_wm_isWinSupporting(void)
{
    static Boolean checked = False;
    static Window isWinSupporting = None;

    if (checked) {
        return isWinSupporting;
    }

    DTRACE_PRINT("WM: checking for _WIN_SUPPORTING ...  ");
    isWinSupporting = awt_wm_checkAnchor(_XA_WIN_SUPPORTING_WM_CHECK,
                                         XA_CARDINAL);
    checked = True;
    return isWinSupporting;
}


/*
 * Check that that the list of protocols specified by WM in property
 * named LIST_NAME on the root window contains protocol PROTO.
 */
static Boolean
awt_wm_checkProtocol(Atom list_name, Atom proto)
{
    Atom *protocols;
    unsigned long nproto;
    Boolean found;
    unsigned long i;

    protocols = awt_getAtomListProperty(DefaultRootWindow(awt_display),
                                        list_name, &nproto);
    if (protocols == NULL) {
        return False;
    }

    found = False;
    for (i = 0; i < nproto; ++i) {
        if (protocols[i] == proto) {
            found = True;
            break;
        }
    }

    if (protocols != NULL) {
        XFree(protocols);
    }
    return found;
}

static Boolean
awt_wm_doStateProtocolNet(void)
{
    static Boolean checked = False;
    static Boolean supported = False;

    if (checked) {
        return supported;
    }

    if (awt_wm_isNetSupporting()) {
        DTRACE_PRINT("WM: checking for _NET_WM_STATE in _NET_SUPPORTED ...  ");
        supported = awt_wm_checkProtocol(_XA_NET_SUPPORTED, _XA_NET_WM_STATE);
        DTRACE_PRINTLN1("%s", supported ? "yes" : "no");
    }

    checked = True;
    return supported;
}

static Boolean
awt_wm_doStateProtocolWin(void)
{
    static Boolean checked = False;
    static Boolean supported = False;

    if (checked) {
        return supported;
    }

    if (awt_wm_isWinSupporting()) {
        DTRACE_PRINT("WM: checking for _WIN_STATE in _WIN_PROTOCOLS ...  ");
        supported = awt_wm_checkProtocol(_XA_WIN_PROTOCOLS, _XA_WIN_STATE);
        DTRACE_PRINTLN1("%s", supported ? "yes" : "no");
    }
    checked = True;
    return supported;
}



/*
 * Helper function for awt_wm_isEnlightenment.
 * Enlightenment uses STRING property for its comms window id.  Gaaa!
 * The property is ENLIGHTENMENT_COMMS, STRING/8 and the string format
 * is "WINID %8x".  Gee, I haven't been using scanf for *ages*... :-)
 */
static Window
awt_getECommsWindowIDProperty(Window w)
{
    static Atom XA_ENLIGHTENMENT_COMMS = None;

    /* Property value*/
    Window value;

    /* Request status */
    int status;

    /* Returns of XGetWindowProperty */
    Atom actual_type;
    int actual_format;
    unsigned long nitems;
    unsigned long bytes_after;
    unsigned char *data;

    if (!awt_wm_atomInterned(&XA_ENLIGHTENMENT_COMMS, "ENLIGHTENMENT_COMMS")) {
        return False;
    }

    /* BadWindow is ok and will be blocked by our special handler */
    WITH_XERROR_HANDLER(xerror_ignore_bad_window);
    {
        status = XGetWindowProperty(awt_display, w,
                     XA_ENLIGHTENMENT_COMMS, 0, 14, False, XA_STRING,
                     &actual_type, &actual_format, &nitems, &bytes_after,
                     &data);
    }
    RESTORE_XERROR_HANDLER;

    if (status != Success || data == NULL) {
        DTRACE_PRINTLN("no ENLIGHTENMENT_COMMS");
        return None;
    }

    if (actual_type != XA_STRING || actual_format != 8
        || nitems != 14 || bytes_after != 0)
    {
        DTRACE_PRINTLN("malformed ENLIGHTENMENT_COMMS");
        XFree(data);            /* NULL data already catched above */
        return None;
    }

    value = None;
    sscanf((char *)data, "WINID %8lx", &value); /* NB: 64 bit: XID is long */
    XFree(data);

    return value;
}


/*
 * Is Enlightenment WM running?  Congruent to awt_wm_checkAnchor, but
 * uses STRING property peculiar to Enlightenment.
 */
static Boolean
awt_wm_isEnlightenment(void)
{
    Window root_xref;
    Window self_xref;

    DTRACE_PRINT("WM: checking for Enlightenment ...  ");
    root_xref = awt_getECommsWindowIDProperty(DefaultRootWindow(awt_display));
    if (root_xref == None) {
        return False;
    }

    DTRACE_PRINT1("0x%x ...  ", root_xref);
    self_xref = awt_getECommsWindowIDProperty(root_xref);
    if (self_xref != root_xref) {
        return False;
    }

    DTRACE_PRINTLN("ok");
    return True;
}


/*
 * Is CDE running?
 *
 * XXX: This is hairy...  CDE is MWM as well.  It seems we simply test
 * for default setup and will be bitten if user changes things...
 *
 * Check for _DT_SM_WINDOW_INFO(_DT_SM_WINDOW_INFO) on root.  Take the
 * second element of the property and check for presence of
 * _DT_SM_STATE_INFO(_DT_SM_STATE_INFO) on that window.
 *
 * XXX: Any header that defines this structures???
 */
static Boolean
awt_wm_isCDE(void)
{
    static Atom _XA_DT_SM_WINDOW_INFO = None;
    static Atom _XA_DT_SM_STATE_INFO = None;

    /* Property value*/
    Window wmwin;

    /* Request status */
    int status;

    /* Returns of XGetWindowProperty */
    Atom actual_type;
    int actual_format;
    unsigned long nitems;
    unsigned long bytes_after;
    long *data;                 /* NB: 64 bit: Format 32 props are 'long' */

    DTRACE_PRINT("WM: checking for CDE ...  ");

    if (!awt_wm_atomInterned(&_XA_DT_SM_WINDOW_INFO, "_DT_SM_WINDOW_INFO")) {
        return False;
    }

    status = XGetWindowProperty(awt_display, DefaultRootWindow(awt_display),
                 _XA_DT_SM_WINDOW_INFO, 0, 2, False, _XA_DT_SM_WINDOW_INFO,
                 &actual_type, &actual_format, &nitems, &bytes_after,
                 (unsigned char **)&data);

    if (status != Success || data == NULL) {
        DTRACE_PRINTLN("no _DT_SM_WINDOW_INFO on root");
        return False;
    }

    if (actual_type != _XA_DT_SM_WINDOW_INFO || actual_format != 32
        || nitems != 2 || bytes_after != 0)
    {
        DTRACE_PRINTLN("malformed _DT_SM_WINDOW_INFO on root");
        XFree(data);            /* NULL data already catched above */
        return False;
    }

    wmwin = (Window)data[1];
    XFree(data);

    /* Now check that this window has _DT_SM_STATE_INFO (ignore contents) */

    if (!awt_wm_atomInterned(&_XA_DT_SM_STATE_INFO, "_DT_SM_STATE_INFO")) {
        return False;
    }

    /* BadWindow is ok and will be blocked by our special handler */
    WITH_XERROR_HANDLER(xerror_ignore_bad_window);
    {
        status = XGetWindowProperty(awt_display, wmwin,
                     _XA_DT_SM_STATE_INFO, 0, 1, False, _XA_DT_SM_STATE_INFO,
                     &actual_type, &actual_format, &nitems, &bytes_after,
                     (unsigned char **)&data);
    }
    RESTORE_XERROR_HANDLER;

    if (status != Success || data == NULL) {
        DTRACE_PRINTLN("no _DT_SM_STATE_INFO");
        return False;
    }

    if (actual_type != _XA_DT_SM_STATE_INFO || actual_format != 32) {
        DTRACE_PRINTLN("malformed _DT_SM_STATE_INFO");
        XFree(data);            /* NULL data already catched above */
        return False;
    }

    DTRACE_PRINTLN("yes");
    XFree(data);
    return True;
}

/*
 * Is MWM running?  (Note that CDE will test positive as well).
 *
 * Check for _MOTIF_WM_INFO(_MOTIF_WM_INFO) on root.  Take the
 * second element of the property and check for presence of
 * _DT_SM_STATE_INFO(_DT_SM_STATE_INFO) on that window.
 */
static Boolean
awt_wm_isMotif(void)
{
    /*
     * Grr.  Motif just had to be different, ain't it!?  Everyone use
     * "XA" for things of type Atom, but motif folks chose to define
     * _XA_MOTIF_* to be atom *names*.  How pathetic...
     */
#undef _XA_MOTIF_WM_INFO
    static Atom _XA_MOTIF_WM_INFO = None;
    static Atom _XA_DT_WORKSPACE_CURRENT = None;

    /* Property value */
    Window wmwin;
    Atom *curws;

    /* Request status */
    int status;

    /* Returns of XGetWindowProperty */
    Atom actual_type;
    int actual_format;
    unsigned long nitems;
    unsigned long bytes_after;
    long *data;                 /* NB: 64 bit: Format 32 props are 'long' */

    DTRACE_PRINT("WM: checking for MWM ...  ");

    if (!awt_wm_atomInterned(&_XA_MOTIF_WM_INFO, "_MOTIF_WM_INFO")
        || !awt_wm_atomInterned(&_XA_DT_WORKSPACE_CURRENT, "_DT_WORKSPACE_CURRENT"))
    {
        return False;
    }


    status = XGetWindowProperty(awt_display, DefaultRootWindow(awt_display),
                 _XA_MOTIF_WM_INFO, 0, PROP_MOTIF_WM_INFO_ELEMENTS, False,
                 _XA_MOTIF_WM_INFO, &actual_type,
                 &actual_format, &nitems, &bytes_after,
                 (unsigned char **)&data);

    if (status != Success || data == NULL) {
        DTRACE_PRINTLN("no _MOTIF_WM_INFO on root");
        return False;
    }

    if (actual_type != _XA_MOTIF_WM_INFO || actual_format != 32
        || nitems != PROP_MOTIF_WM_INFO_ELEMENTS || bytes_after != 0)
    {
        DTRACE_PRINTLN("malformed _MOTIF_WM_INFO on root");
        XFree(data);            /* NULL data already catched above */
        return False;
    }

    /* NB: 64 bit: Cannot cast data to MotifWmInfo */
    wmwin = (Window)data[1];
    XFree(data);

    /* Now check that this window has _DT_WORKSPACE_CURRENT */
    curws = awt_getAtomListProperty(wmwin, _XA_DT_WORKSPACE_CURRENT, NULL);
    if (curws == NULL) {
        DTRACE_PRINTLN("no _DT_WORKSPACE_CURRENT");
        return False;
    }

    DTRACE_PRINTLN("yes");
    XFree(curws);
    return True;
}


static Boolean
awt_wm_isNetWMName(char *name)
{
    Window anchor;
    unsigned char *net_wm_name;
    Boolean matched;

    anchor = awt_wm_isNetSupporting();
    if (anchor == None) {
        return False;
    }

    DTRACE_PRINT1("WM: checking for %s by _NET_WM_NAME ...  ", name);

    /*
     * Check both UTF8_STRING and STRING.  We only call this function
     * with ASCII names and UTF8 preserves ASCII bit-wise.  wm-spec
     * mandates UTF8_STRING for _NET_WM_NAME but at least sawfish-1.0
     * still uses STRING.  (mmm, moving targets...).
     */
    net_wm_name = awt_getProperty8(anchor, _XA_NET_WM_NAME, XA_UTF8_STRING);
    if (net_wm_name == NULL) {
        net_wm_name = awt_getProperty8(anchor, _XA_NET_WM_NAME, XA_STRING);
    }

    if (net_wm_name == NULL) {
        DTRACE_PRINTLN("no (missing _NET_WM_NAME)");
        return False;
    }

    matched = (strcmp((char *)net_wm_name, name) == 0);
    if (matched) {
        DTRACE_PRINTLN("yes");
    } else {
        DTRACE_PRINTLN1("no (_NET_WM_NAME = \"%s\")", net_wm_name);
    }
    XFree(net_wm_name);
    return matched;
}

/*
 * Is Sawfish running?
 */
static Boolean
awt_wm_isSawfish(void)
{
    return awt_wm_isNetWMName("Sawfish");
}

/*
 * Is KDE2 (KWin) running?
 */
static Boolean
awt_wm_isKDE2(void)
{
    return awt_wm_isNetWMName("KWin");
}


/*
 * Is Metacity running?
 */
static Boolean
awt_wm_isMetacity(void)
{
    return awt_wm_isNetWMName("Metacity");
}


/*
 * Temporary error handler that ensures that we know if
 * XChangeProperty succeeded or not.
 */
static int /* but ignored */
xerror_verify_change_property(Display *dpy, XErrorEvent *err)
{
    XERROR_SAVE(err);
    if (err->request_code == X_ChangeProperty) {
        return 0;
    }
    else {
        return (*xerror_saved_handler)(dpy, err);
    }
}


/*
 * Prepare IceWM check.
 *
 * The only way to detect IceWM, seems to be by setting
 * _ICEWM_WINOPTHINT(_ICEWM_WINOPTHINT/8) on root and checking if it
 * was immediately deleted by IceWM.
 *
 * But messing with PropertyNotify here is way too much trouble, so
 * approximate the check by setting the property in this function and
 * checking if it still exists later on.
 *
 * Gaa, dirty dances...
 */
static Boolean
awt_wm_prepareIsIceWM(void)
{
    static Atom _XA_ICEWM_WINOPTHINT = None;

    /*
     * Choose something innocuous: "AWT_ICEWM_TEST allWorkspaces 0".
     * IceWM expects "class\0option\0arg\0" with zero bytes as delimiters.
     */
    static unsigned char opt[] = {
        'A','W','T','_','I','C','E','W','M','_','T','E','S','T','\0',
        'a','l','l','W','o','r','k','s','p','a','c','e','s','\0',
        '0','\0'
    };

    DTRACE_PRINT("WM: scheduling check for IceWM ...  ");

    if (!awt_wm_atomInterned(&_XA_ICEWM_WINOPTHINT, "_ICEWM_WINOPTHINT")) {
        return False;
    }

    WITH_XERROR_HANDLER(xerror_verify_change_property);
    {
        XChangeProperty(awt_display, DefaultRootWindow(awt_display),
                        _XA_ICEWM_WINOPTHINT, _XA_ICEWM_WINOPTHINT, 8,
                        PropModeReplace, opt, sizeof(opt));
    }
    RESTORE_XERROR_HANDLER;

    if (xerror_code != Success) {
        DTRACE_PRINTLN1("can't set _ICEWM_WINOPTHINT, error = %d",
                        xerror_code);
        return False;
    }
    else {
        DTRACE_PRINTLN("scheduled");
        return True;
    }
}

/*
 * Is IceWM running?
 *
 * Note well: Only call this if awt_wm_prepareIsIceWM succeeded, or a
 * false positive will be reported.
 */
static Boolean
awt_wm_isIceWM(void)
{
    static Atom _XA_ICEWM_WINOPTHINT = None;

    /* Request status */
    int status;

    /* Returns of XGetWindowProperty */
    Atom actual_type;
    int actual_format;
    unsigned long nitems;
    unsigned long bytes_after;
    unsigned char *data;

    DTRACE_PRINT("WM: checking for IceWM ...  ");

    if (!awt_wm_atomInterned(&_XA_ICEWM_WINOPTHINT, "_ICEWM_WINOPTHINT")) {
        return False;
    }

    XGetWindowProperty(awt_display, DefaultRootWindow(awt_display),
                 _XA_ICEWM_WINOPTHINT, 0, 0xFFFF, True, /* NB: deleting! */
                 _XA_ICEWM_WINOPTHINT, &actual_type,
                 &actual_format, &nitems, &bytes_after,
                 &data);

    if (data != NULL) {
        XFree(data);
    }

    if (actual_type == None) {
        DTRACE_PRINTLN("yes");
        return True;
    }
    else {
        DTRACE_PRINTLN("no");
        return False;
    }
}

/*
 * Is OpenLook WM running?
 *
 * This one is pretty lame, but the only property peculiar to OLWM is
 * _SUN_WM_PROTOCOLS(ATOM[]).  Fortunately, olwm deletes it on exit.
 */
static Boolean
awt_wm_isOpenLook(void)
{
    static Atom _XA_SUN_WM_PROTOCOLS = None;
    Atom *list;

    DTRACE_PRINT("WM: checking for OpenLook WM ...  ");

    if (!awt_wm_atomInterned(&_XA_SUN_WM_PROTOCOLS, "_SUN_WM_PROTOCOLS")) {
        return False;
    }

    list = awt_getAtomListProperty(DefaultRootWindow(awt_display),
                                   _XA_SUN_WM_PROTOCOLS, NULL);
    if (list == NULL) {
        DTRACE_PRINTLN("no _SUN_WM_PROTOCOLS on root");
        return False;
    }

    DTRACE_PRINTLN("yes");
    XFree(list);
    return True;
}



static Boolean winmgr_running = False;

/*
 * Temporary error handler that checks if selecting for
 * SubstructureRedirect failed.
 */
static int /* but ignored */
xerror_detect_wm(Display *dpy, XErrorEvent *err)
{
    XERROR_SAVE(err);
    if (err->request_code == X_ChangeWindowAttributes
        && err->error_code == BadAccess)
    {
        DTRACE_PRINTLN("some WM is running (hmm, we'll see)");
        winmgr_running = True;
        return 0;
    }
    else {
        return (*xerror_saved_handler)(dpy, err);
    }
}


/*
 * Make an educated guess about running window manager.
 * XXX: ideally, we should detect wm restart.
 */
enum wmgr_t
awt_wm_getRunningWM(void)
{
    /*
     * Ideally, we should support cases when a different WM is started
     * during a Java app lifetime.
     */
    static enum wmgr_t awt_wmgr = UNDETERMINED_WM;

    XSetWindowAttributes substruct;
    const char *vendor_string;
    Boolean doIsIceWM;

    if (awt_wmgr != UNDETERMINED_WM) {
        return awt_wmgr;
    }

    /*
     * Quick checks for specific servers.
     */
    vendor_string = ServerVendor(awt_display);
    if (strstr(vendor_string, "eXcursion") != NULL) {
        /*
         * Use NO_WM since in all other aspects eXcursion is like not
         * having a window manager running. I.e. it does not reparent
         * top level shells.
         */
        DTRACE_PRINTLN("WM: eXcursion detected - treating as NO_WM");
        awt_wmgr = NO_WM;
        return awt_wmgr;
    }

    /*
     * If *any* window manager is running?
     *
     * Try selecting for SubstructureRedirect, that only one client
     * can select for, and if the request fails, than some other WM is
     * already running.
     */
    winmgr_running = 0;
    substruct.event_mask = SubstructureRedirectMask;

    DTRACE_PRINT("WM: trying SubstructureRedirect ...  ");
    WITH_XERROR_HANDLER(xerror_detect_wm);
    {
        XChangeWindowAttributes(awt_display, DefaultRootWindow(awt_display),
                                CWEventMask, &substruct);
    }
    RESTORE_XERROR_HANDLER;

    /*
     * If no WM is running than our selection for SubstructureRedirect
     * succeeded and needs to be undone (hey we are *not* a WM ;-).
     */
    if (!winmgr_running) {
        DTRACE_PRINTLN("no WM is running");
        awt_wmgr = NO_WM;
        substruct.event_mask = 0;
        XChangeWindowAttributes(awt_display, DefaultRootWindow(awt_display),
                                CWEventMask, &substruct);
        return NO_WM;
    }

    /* actual check for IceWM to follow below */
    doIsIceWM = awt_wm_prepareIsIceWM(); /* and let IceWM to act */

    if (awt_wm_isNetSupporting()) {
        awt_wm_doStateProtocolNet();
    }
    if (awt_wm_isWinSupporting()) {
        awt_wm_doStateProtocolWin();
    }

    /*
     * Ok, some WM is out there.  Check which one by testing for
     * "distinguishing" atoms.
     */
    if (doIsIceWM && awt_wm_isIceWM()) {
        awt_wmgr = ICE_WM;
    }
    else if (awt_wm_isEnlightenment()) {
        awt_wmgr = ENLIGHTEN_WM;
    }
    else if (awt_wm_isMetacity()) {
        awt_wmgr = METACITY_WM;
    }
    else if (awt_wm_isSawfish()) {
        awt_wmgr = SAWFISH_WM;
    }
    else if (awt_wm_isKDE2()) {
        awt_wmgr = KDE2_WM;
    }
    /*
     * We don't check for legacy WM when we already know that WM
     * supports WIN or _NET wm spec.
     */
    else if (awt_wm_isNetSupporting()) {
        DTRACE_PRINTLN("WM: other WM (supports _NET)");
        awt_wmgr = OTHER_WM;
    }
    else if (awt_wm_isWinSupporting()) {
        DTRACE_PRINTLN("WM: other WM (supports _WIN)");
        awt_wmgr = OTHER_WM;
    }
    /*
     * Check for legacy WMs.
     */
    else if (awt_wm_isCDE()) {  /* XXX: must come before isMotif */
        awt_wmgr = CDE_WM;
    }
    else if (awt_wm_isMotif()) {
        awt_wmgr = MOTIF_WM;
    }
    else if (awt_wm_isOpenLook()) {
        awt_wmgr = OPENLOOK_WM;
    }
    else {
        DTRACE_PRINTLN("WM: some other legacy WM");
        awt_wmgr = OTHER_WM;
    }

    return awt_wmgr;
}


/*
 * Some buggy WMs ignore window gravity when processing
 * ConfigureRequest and position window as if the gravity is Static.
 * We work around this in MWindowPeer.pReshape().
 */
Boolean
awt_wm_configureGravityBuggy(void)
{
    static int env_not_checked = 1;
    static int env_buggy = 0;

    if (env_not_checked) {
        DTRACE_PRINT("WM: checking for _JAVA_AWT_WM_STATIC_GRAVITY in environment ...  ");
        if (getenv("_JAVA_AWT_WM_STATIC_GRAVITY") != NULL) {
            DTRACE_PRINTLN("set");
            env_buggy = 1;
        } else {
            DTRACE_PRINTLN("no");
        }
        env_not_checked = 0;
    }

    if (env_buggy) {
        return True;
    }

    switch (awt_wm_getRunningWM()) {
      case ICE_WM:
          /*
           * See bug #228981 at IceWM's SourceForge pages.
           * Latest stable version 1.0.8-6 still has this problem.
           */
          return True;

      case ENLIGHTEN_WM:
          /* At least E16 is buggy. */
          return True;

      default:
          return False;
    }
}

/**
 * Check if state is supported.
 * Note that a compound state is always reported as not supported.
 * Note also that MAXIMIZED_BOTH is considered not a compound state.
 * Therefore, a compound state is just ICONIFIED | anything else.
 *
 */
Boolean
awt_wm_supportsExtendedState(jint state)
{
    switch (state) {
      case java_awt_Frame_MAXIMIZED_VERT:
      case java_awt_Frame_MAXIMIZED_HORIZ:
          /*
           * WMs that talk NET/WIN protocol, but do not support
           * unidirectional maximization.
           */
          if (awt_wm_getRunningWM() == METACITY_WM) {
              /* "This is a deliberate policy decision." -hp */
              return JNI_FALSE;
          }
          /* FALLTROUGH */
      case java_awt_Frame_MAXIMIZED_BOTH:
          return (awt_wm_doStateProtocolNet() || awt_wm_doStateProtocolWin());
      default:
          return JNI_FALSE;
    }
}




/*****************************************************************************\
 *
 * Size and decoration hints ...
 *
\*****************************************************************************/


/*
 * Remove size hints specified by the mask.
 * XXX: Why do we need this in the first place???
 */
void
awt_wm_removeSizeHints(Widget shell, long mask)
{
    Display *dpy = XtDisplay(shell);
    Window shell_win = XtWindow(shell);
    XSizeHints *hints = XAllocSizeHints();
    long ignore = 0;

    if (hints == NULL) {
        DTRACE_PRINTLN("WM: removeSizeHints FAILED to allocate XSizeHints");
        return;
    }

    /* sanitize the mask, only do these hints */
    mask &= (PMaxSize|PMinSize|USPosition|PPosition);

    XGetWMNormalHints(dpy, shell_win, hints, &ignore);
    if ((hints->flags & mask) == 0) {
        XFree(hints);
        return;
    }

#ifdef DEBUG
    DTRACE_PRINT("WM: removing hints");

    if (mask & PMaxSize) {
        DTRACE_PRINT(" Max = ");
        if (hints->flags & PMaxSize) {
            DTRACE_PRINT2("%d x %d;", hints->max_width, hints->max_height);
        } else {
            DTRACE_PRINT("none;");
        }
    }

    if (mask & PMinSize) {
        DTRACE_PRINT(" Min = ");
        if (hints->flags & PMinSize) {
            DTRACE_PRINT2("%d x %d;", hints->min_width, hints->min_height);
        } else {
            DTRACE_PRINT("none;");
        }
    }

    DTRACE_PRINTLN("");
#endif

    hints->flags &= ~mask;
    XSetWMNormalHints(dpy, shell_win, hints);
    XFree(hints);
}

/*
 *
 *
 */
static void
awt_wm_proclaimUrgency(struct FrameData *wdata)
{
    Display *dpy = XtDisplay(wdata->winData.shell);
    Window shell_win = XtWindow(wdata->winData.shell);

    XWMHints *hints = XGetWMHints(dpy, shell_win);
    if( hints == NULL ) {
       /* For now just */ return;
    }
    if ((hints->flags & URGENCY_HINT) != 0) {
        /* it's here already */
        XFree(hints);
        return;
    }
    hints->flags |= URGENCY_HINT;
    XSetWMHints(dpy, shell_win, hints);
    XFree(hints);
}

/*
 * If MWM_DECOR_ALL bit is set, then the rest of the bit-mask is taken
 * to be subtracted from the decorations.  Normalize decoration spec
 * so that we can map motif decor to something else bit-by-bit in the
 * rest of the code.
 */
static int
awt_wm_normalizeMotifDecor(int decorations)
{
    int d;

    if (!(decorations & MWM_DECOR_ALL))
        return decorations;     /* already normalized */

    d = MWM_DECOR_BORDER |MWM_DECOR_RESIZEH | MWM_DECOR_TITLE
        | MWM_DECOR_MENU | MWM_DECOR_MINIMIZE | MWM_DECOR_MAXIMIZE;
    d &= ~decorations;
    return d;
}


/*
 * Infer OL properties from MWM decorations.
 * Use _OL_DECOR_DEL(ATOM[]) to remove unwanted ones.
 */
static void
awt_wm_setOLDecor(struct FrameData *wdata, Boolean resizable, int decorations)
{
    Window shell_win = XtWindow(wdata->winData.shell);
    Atom decorDel[3];
    int nitems;

    if (shell_win == None) {
        DTRACE_PRINTLN("WM: setOLDecor - no window, returning");
        return;
    }

    decorations = awt_wm_normalizeMotifDecor(decorations);
    DTRACE_PRINT("WM: _OL_DECOR_DEL = {");

    nitems = 0;
    if (!(decorations & MWM_DECOR_TITLE)) {
        DTRACE_PRINT(" _OL_DECOR_HEADER");
        decorDel[nitems++] = _XA_OL_DECOR_HEADER;
    }
    if (!(decorations & (MWM_DECOR_RESIZEH | MWM_DECOR_MAXIMIZE))) {
        DTRACE_PRINT(" _OL_DECOR_RESIZE");
        decorDel[nitems++] = _XA_OL_DECOR_RESIZE;
    }
    if (!(decorations & (MWM_DECOR_MENU | MWM_DECOR_MAXIMIZE
                         | MWM_DECOR_MINIMIZE)))
    {
        DTRACE_PRINT(" _OL_DECOR_CLOSE");
        decorDel[nitems++] = _XA_OL_DECOR_CLOSE;
    }
    DTRACE_PRINT(" }");

    if (nitems == 0) {
        DTRACE_PRINTLN(" ...  removing");
        XDeleteProperty(awt_display, shell_win, _XA_OL_DECOR_DEL);
    }
    else {
        DTRACE_PRINTLN(" ...  setting");
        XChangeProperty(awt_display, shell_win,
                        _XA_OL_DECOR_DEL, XA_ATOM, 32,
                        PropModeReplace, (unsigned char *)decorDel, nitems);
    }
}

/*
 * Set MWM decorations.  Infer MWM functions from decorations.
 */
static void
awt_wm_setMotifDecor(struct FrameData *wdata, Boolean resizable, int decorations)
{
    int functions;

    /* Apparently some WMs don't implement MWM_*_ALL semantic correctly */
    if ((decorations & MWM_DECOR_ALL) && (decorations != MWM_DECOR_ALL)) {
        decorations = awt_wm_normalizeMotifDecor(decorations);
        DTRACE_PRINTLN1("WM: setMotifDecor normalize exclusions, decor = 0x%X",
                        decorations);
    }

    DTRACE_PRINT("WM: setMotifDecor functions = {");
    functions = 0;

    if (decorations & MWM_DECOR_ALL) {
        DTRACE_PRINT(" ALL");
        functions |= MWM_FUNC_ALL;
    }
    else {
        /*
         * Functions we always want to be enabled as mwm(1) and
         * descendants not only hide disabled functions away from
         * user, but also ignore corresponding requests from the
         * program itself (e.g. 4442047).
         */
        DTRACE_PRINT(" CLOSE MOVE MINIMIZE");
        functions |= (MWM_FUNC_CLOSE | MWM_FUNC_MOVE | MWM_FUNC_MINIMIZE);

        if (resizable) {
            DTRACE_PRINT(" RESIZE MAXIMIZE");
            functions |= MWM_FUNC_RESIZE | MWM_FUNC_MAXIMIZE;
        }
    }

    DTRACE_PRINTLN(" }");

    XtVaSetValues(wdata->winData.shell,
                  XmNmwmDecorations, decorations,
                  XmNmwmFunctions, functions,
                  NULL);
}


/*
 * Under some window managers if shell is already mapped, we MUST
 * unmap and later remap in order to effect the changes we make in the
 * window manager decorations.
 *
 * N.B.  This unmapping / remapping of the shell exposes a bug in
 * X/Motif or the Motif Window Manager.  When you attempt to map a
 * widget which is positioned (partially) off-screen, the window is
 * relocated to be entirely on screen. Good idea.  But if both the x
 * and the y coordinates are less than the origin (0,0), the first
 * (re)map will move the window to the origin, and any subsequent
 * (re)map will relocate the window at some other point on the screen.
 * I have written a short Motif test program to discover this bug.
 * This should occur infrequently and it does not cause any real
 * problem.  So for now we'll let it be.
 */
static Boolean
awt_wm_needRemap()
{
    switch (awt_wm_getRunningWM()) {
#if 0 /* XXX */
      case OPENLOOK_WM:
      case MOTIF_WM:
      case CDE_WM:
      case ICE_WM:
      case ENLIGHTEN_WM:
          return True;
#endif
      default:
          return True;
    }
}

/*
 * Set decoration hints on the shell to wdata->decor adjusted
 * appropriately if not resizable.
 */
void
awt_wm_setShellDecor(struct FrameData *wdata, Boolean resizable)
{
    int decorations = wdata->decor;

    DTRACE_PRINTLN3("WM: setShellDecor(0x%x/0x%x, %s)",
                    wdata->winData.shell, XtWindow(wdata->winData.shell),
                    resizable ? "resizable" : "not resizable");

    if (!resizable) {
        if (decorations & MWM_DECOR_ALL) {
            decorations |= (MWM_DECOR_RESIZEH | MWM_DECOR_MAXIMIZE);
        }
        else {
            decorations &= ~(MWM_DECOR_RESIZEH | MWM_DECOR_MAXIMIZE);
        }
    }

    DTRACE_PRINTLN1("WM:     decorations = 0x%X", decorations);
    awt_wm_setMotifDecor(wdata, resizable, decorations);
    awt_wm_setOLDecor(wdata, resizable, decorations);

    /* Some WMs need remap to redecorate the window */
    if (wdata->isShowing && awt_wm_needRemap()) {
        /*
         * Do the re/mapping at the Xlib level.  Since we essentially
         * work around a WM bug we don't want this hack to be exposed
         * to Intrinsics (i.e. don't mess with grabs, callbacks etc).
         */
        Display *dpy = XtDisplay(wdata->winData.shell);
        Window shell_win = XtWindow(wdata->winData.shell);

        DTRACE_PRINT("WM: setShellDecor REMAPPING ...  ");
        XUnmapWindow(dpy, shell_win);
        XSync(dpy, False);      /* give WM a chance to catch up */
        XMapWindow(dpy, shell_win);
        DTRACE_PRINTLN("done");
    }
}


/*
 * Make specified shell resizable.
 */
void
awt_wm_setShellResizable(struct FrameData *wdata)
{
    DTRACE_PRINTLN2("WM: setShellResizable(0x%x/0x%x)",
                    wdata->winData.shell, XtWindow(wdata->winData.shell));

    XtVaSetValues(wdata->winData.shell,
                  XmNallowShellResize, True,
                  XmNminWidth,  XtUnspecifiedShellInt,
                  XmNminHeight, XtUnspecifiedShellInt,
                  XmNmaxWidth,  XtUnspecifiedShellInt,
                  XmNmaxHeight, XtUnspecifiedShellInt,
                  NULL);

    /* REMINDER: will need to revisit when setExtendedStateBounds is added */
    awt_wm_removeSizeHints(wdata->winData.shell, PMinSize|PMaxSize);

    /* Restore decorations */
    awt_wm_setShellDecor(wdata, True);
}


/*
 * Make specified shell non-resizable.
 * If justChangeSize is false, update decorations as well.
 */
void
awt_wm_setShellNotResizable(struct FrameData *wdata,
                            int32_t width, int32_t height,
                            Boolean justChangeSize)
{
    DTRACE_PRINTLN5("WM: setShellNotResizable(0x%x/0x%x, %d, %d, %s)",
                    wdata->winData.shell, XtWindow(wdata->winData.shell),
                    width, height,
                    justChangeSize ? "size only" : "redecorate");

    /* Fix min/max size hints at the specified values */
    if ((width > 0) && (height > 0)) {
        XtVaSetValues(wdata->winData.shell,
                      XmNwidth,     (XtArgVal)width,
                      XmNheight,    (XtArgVal)height,
                      XmNminWidth,  (XtArgVal)width,
                      XmNminHeight, (XtArgVal)height,
                      XmNmaxWidth,  (XtArgVal)width,
                      XmNmaxHeight, (XtArgVal)height,
                      NULL);
    }

    if (!justChangeSize) {      /* update decorations */
        awt_wm_setShellDecor(wdata, False);
    }
}


/*
 * Helper function for awt_wm_getInsetsFromProp.
 * Read property of type CARDINAL[4] = { left, right, top, bottom }
 */
static Boolean
awt_wm_readInsetsArray(Window shell_win, Atom insets_property,
    int32_t *top, int32_t *left, int32_t *bottom, int32_t *right)
{
    /* Request status */
    int status;

    /* Returns of XGetWindowProperty */
    Atom actual_type;
    int actual_format;
    unsigned long nitems;
    unsigned long bytes_after;
    long *insets = NULL;        /* NB: 64 bit: Format 32 props are 'long' */

    status = XGetWindowProperty (awt_display, shell_win,
                 insets_property, 0, 4, False, XA_CARDINAL,
                 &actual_type, &actual_format, &nitems, &bytes_after,
                 (unsigned char **)&insets);

    if (status != Success || insets == NULL) {
        DTRACE_PRINTLN("failed");
        return False;
    }

    if (actual_type != XA_CARDINAL || actual_format != 32) {
        DTRACE_PRINTLN("type/format mismatch");
        XFree(insets);
        return False;
    }

    *left   = (int32_t)insets[0];
    *right  = (int32_t)insets[1];
    *top    = (int32_t)insets[2];
    *bottom = (int32_t)insets[3];
    XFree(insets);

    /* Order is that of java.awt.Insets.toString */
    DTRACE_PRINTLN4("[top=%d,left=%d,bottom=%d,right=%d]",
                    *top, *left, *bottom, *right);
    return True;
}

/*
 * If WM implements the insets property - fill insets with values
 * specified in that property.
 */
Boolean
awt_wm_getInsetsFromProp(Window shell_win,
    int32_t *top, int32_t *left, int32_t *bottom, int32_t *right)
{
    switch (awt_wm_getRunningWM()) {

      case ENLIGHTEN_WM:
          DTRACE_PRINT("WM: reading _E_FRAME_SIZE ...  ");
          return awt_wm_readInsetsArray(shell_win, _XA_E_FRAME_SIZE,
                                        top, left, bottom, right);

#if 0
     /*
      * uwe: disabled for now, as KDE seems to supply bogus values
      * when we maximize iconified frame.  Need to verify with KDE2.1.
      * NB: Also note, that "external" handles (e.g. in laptop decor)
      * are also included in the frame strut, which is probably not
      * what we want.
      */
      case KDE2_WM:
          DTRACE_PRINT("WM: reading _KDE_NET_WM_FRAME_STRUT ...  ");
          return awt_wm_readInsetsArray(shell_win, _XA_KDE_NET_WM_FRAME_STRUT,
                                        top, left, bottom, right);
#endif

      default:
          return False;
    }
}

/*
 * XmNiconic and Map/UnmapNotify (that XmNiconic relies on) are
 * unreliable, since mapping changes can happen for a virtual desktop
 * switch or MacOS style shading that became quite popular under X as
 * well.  Yes, it probably should not be this way, as it violates
 * ICCCM, but reality is that quite a lot of window managers abuse
 * mapping state.
 */
int
awt_wm_getWMState(Window shell_win)
{
    /* Request status */
    int status;

    /* Returns of XGetWindowProperty */
    Atom actual_type;
    int actual_format;
    unsigned long nitems;
    unsigned long bytes_after;
    long *data;                 /* NB: 64 bit: Format 32 props are 'long' */

    int wm_state;

    status = XGetWindowProperty(awt_display, shell_win,
                 XA_WM_STATE, 0, 1, False, XA_WM_STATE,
                 &actual_type, &actual_format, &nitems, &bytes_after,
                 (unsigned char **)&data);

    if (status != Success || data == NULL) {
        return WithdrawnState;
    }

    if (actual_type != XA_WM_STATE) {
        DTRACE_PRINTLN1("WM:     WM_STATE(0x%x) - wrong type", shell_win);
        XFree(data);
        return WithdrawnState;
    }

    wm_state = (int)*data;
    XFree(data);
    return wm_state;
}



/*****************************************************************************\
 *
 * Reading state from properties WM puts on our window ...
 *
\*****************************************************************************/

/*
 * New "NET" WM spec: _NET_WM_STATE/Atom[]
 */
static jint
awt_wm_getStateNet(Window shell_win)
{
    Atom *net_wm_state;
    jint java_state;
    unsigned long nitems;
    unsigned long i;

    net_wm_state = awt_getAtomListProperty(shell_win, _XA_NET_WM_STATE, &nitems);
    if (nitems == 0) {
        DTRACE_PRINTLN("WM:     _NET_WM_STATE = { }");
        if (net_wm_state) {
            XFree(net_wm_state);
        }
        return java_awt_Frame_NORMAL;
    }
#ifdef DEBUG
    DTRACE_PRINT("WM:     ");
    awt_wm_dtraceStateNet(net_wm_state, nitems);
#endif

    java_state = java_awt_Frame_NORMAL;
    for (i = 0; i < nitems; ++i) {
        if (net_wm_state[i] == _XA_NET_WM_STATE_MAXIMIZED_VERT) {
            java_state |= java_awt_Frame_MAXIMIZED_VERT;
        }
        else if (net_wm_state[i] == _XA_NET_WM_STATE_MAXIMIZED_HORZ) {
            java_state |= java_awt_Frame_MAXIMIZED_HORIZ;
        }
    }
    XFree(net_wm_state);
    return java_state;
}

Boolean
awt_wm_isStateNetHidden(Window shell_win)
{
    Atom *net_wm_state;
    Boolean result = False;
    unsigned long nitems;
    unsigned long i;

    net_wm_state = awt_getAtomListProperty(shell_win, _XA_NET_WM_STATE, &nitems);
    if (nitems == 0) {
        DTRACE_PRINTLN("WM:     _NET_WM_STATE = { }");
        if (net_wm_state) {
            XFree(net_wm_state);
        }
        return False;
    }
#ifdef DEBUG
    DTRACE_PRINT("WM:     ");
    awt_wm_dtraceStateNet(net_wm_state, nitems);
#endif

    for (i = 0; i < nitems; ++i) {
        if (net_wm_state[i] == _XA_NET_WM_STATE_HIDDEN) {
            result = True;
        }
    }
    XFree(net_wm_state);
    return result;
}

/*
 * Similar code to getStateNet, to get layer state.
 */
static int
awt_wm_getLayerNet(Window shell_win)
{
    Atom *net_wm_state;
    int java_state;
    unsigned long nitems;
    unsigned long i;

    net_wm_state = awt_getAtomListProperty(shell_win, _XA_NET_WM_STATE, &nitems);
    if (nitems == 0) {
        DTRACE_PRINTLN("WM:     _NET_WM_STATE = { }");
        if (net_wm_state) {
            XFree(net_wm_state);
        }
        return LAYER_NORMAL;
    }
#ifdef DEBUG
    DTRACE_PRINT("WM:     ");
    awt_wm_dtraceStateNet(net_wm_state, nitems);
#endif

    java_state = LAYER_NORMAL;
    for (i = 0; i < nitems; ++i) {
        if (net_wm_state[i] == _XA_NET_WM_STATE_ABOVE) {
            java_state = LAYER_ALWAYS_ON_TOP;
        }
    }
    XFree(net_wm_state);
    return java_state;
}

/*
 * Old Gnome spec: _WIN_STATE/CARDINAL
 */
static jint
awt_wm_getStateWin(Window shell_win)
{
    long win_state;
    jint java_state;

    win_state = awt_getProperty32(shell_win, _XA_WIN_STATE, XA_CARDINAL);
#ifdef DEBUG
    DTRACE_PRINT("WM:     ");
    awt_wm_dtraceStateWin(win_state);
#endif

    java_state = java_awt_Frame_NORMAL;
    if (win_state & WIN_STATE_MAXIMIZED_VERT) {
        java_state |= java_awt_Frame_MAXIMIZED_VERT;
    }
    if (win_state & WIN_STATE_MAXIMIZED_HORIZ) {
        java_state |= java_awt_Frame_MAXIMIZED_HORIZ;
    }
    return java_state;
}

/*
 * Code similar to getStateWin, to get layer state.
 */
static int
awt_wm_getLayerWin(Window shell_win)
{
    long win_state;
    jint java_state;

    win_state = awt_getProperty32(shell_win, _XA_WIN_LAYER, XA_CARDINAL);
#ifdef DEBUG
    DTRACE_PRINT("WM:     ");
    awt_wm_dtraceStateWin(win_state);
#endif

    java_state = LAYER_NORMAL;
    if (win_state == WIN_LAYER_ONTOP) {
        java_state = LAYER_ALWAYS_ON_TOP;
    }
    return java_state;
}


static jint
awt_wm_getExtendedState(Window shell_win)
{
    if (awt_wm_doStateProtocolNet()) {
        return awt_wm_getStateNet(shell_win);
    }
    else if (awt_wm_doStateProtocolWin()) {
        return awt_wm_getStateWin(shell_win);
    }
    else {
        return java_awt_Frame_NORMAL;
    }
}

jint
awt_wm_getState(struct FrameData *wdata)
{
    Window shell_win = XtWindow(wdata->winData.shell);
    jint java_state;

    DTRACE_PRINTLN2("WM: getState(0x%x/0x%x)",
                    wdata->winData.shell, shell_win);

    if (shell_win == None) {
        DTRACE_PRINTLN("WM:     no window, use wdata");
        java_state = wdata->state;
    }
    else {
        int wm_state = awt_wm_getWMState(shell_win);
        if (wm_state == WithdrawnState) {
            DTRACE_PRINTLN("WM:     window withdrawn, use wdata");
            java_state = wdata->state;
        }
        else {
#ifdef DEBUG
            DTRACE_PRINT("WM:     ");
            awt_wm_dtraceWMState(wm_state);
#endif
            if (wm_state == IconicState) {
                java_state = java_awt_Frame_ICONIFIED;
            } else {
                java_state = java_awt_Frame_NORMAL;
            }
            java_state |= awt_wm_getExtendedState(shell_win);
        }
    }

#ifdef DEBUG
    DTRACE_PRINT("WM: ");
    awt_wm_dtraceStateJava(java_state);
#endif

    return java_state;
}



/*****************************************************************************\
 *
 * Notice window state change when WM changes a property on the window ...
 *
\*****************************************************************************/


/*
 * Check if property change is a window state protocol message.
 * If it is - return True and return the new state in *pstate.
 */
Boolean
awt_wm_isStateChange(struct FrameData *wdata, XPropertyEvent *e, jint *pstate)
{
    Window shell_win = XtWindow(wdata->winData.shell);
    Boolean is_state_change = False;
    int wm_state;

    if (!wdata->isShowing) {
        return False;
    }

    wm_state = awt_wm_getWMState(shell_win);
    if (wm_state == WithdrawnState) {
        return False;
    }

    if (e->atom == XA_WM_STATE) {
        is_state_change = True;
    }
    else if (e->atom == _XA_NET_WM_STATE) {
        is_state_change = awt_wm_doStateProtocolNet();
    }
    else if (e->atom == _XA_WIN_STATE) {
        is_state_change = awt_wm_doStateProtocolWin();
    }

    if (is_state_change) {
#ifdef DEBUG
        Widget shell = wdata->winData.shell;
        char *name = XGetAtomName(XtDisplay(shell), e->atom);
        DTRACE_PRINTLN4("WM: PropertyNotify(0x%x/0x%x) %s %s",
                        shell, XtWindow(shell),
                        name != NULL ? name : "???",
                        e->state == PropertyNewValue ? "changed" : "deleted");
        if (name != NULL) {
            XFree(name);
        }
        DTRACE_PRINT("WM:     ");
        awt_wm_dtraceWMState(wm_state);
#endif
        if (wm_state == IconicState) {
            *pstate = java_awt_Frame_ICONIFIED;
        } else {
            *pstate = java_awt_Frame_NORMAL;
        }
        *pstate |= awt_wm_getExtendedState(shell_win);

#ifdef DEBUG
        DTRACE_PRINT("WM: ");
        awt_wm_dtraceStateJava(*pstate);
#endif
    }

    return is_state_change;
}




/*****************************************************************************\
 *
 * Setting/changing window state ...
 *
\*****************************************************************************/

/*
 * Request a state transition from a _NET supporting WM by sending
 * _NET_WM_STATE ClientMessage to root window.
 */
static void
awt_wm_requestStateNet(struct FrameData *wdata, jint state)
{
    Widget shell = wdata->winData.shell;
    Window shell_win = XtWindow(shell);
    XClientMessageEvent req;
    jint old_net_state;
    jint max_changed;

    /* must use awt_wm_setInitialStateNet for withdrawn windows */
    DASSERT(wdata->isShowing);

    /*
     * We have to use toggle for maximization because of transitions
     * from maximization in one direction only to maximization in the
     * other direction only.
     */
    old_net_state = awt_wm_getStateNet(shell_win);
    max_changed = (state ^ old_net_state) & java_awt_Frame_MAXIMIZED_BOTH;

    switch (max_changed) {
      case 0:
          DTRACE_PRINTLN("WM: requestStateNet - maximization unchanged");
          return;

      case java_awt_Frame_MAXIMIZED_HORIZ:
          DTRACE_PRINTLN("WM: requestStateNet - toggling MAX_HORZ");
          req.data.l[1] = _XA_NET_WM_STATE_MAXIMIZED_HORZ;
          req.data.l[2] = 0;
          break;

      case java_awt_Frame_MAXIMIZED_VERT:
          DTRACE_PRINTLN("WM: requestStateNet - toggling MAX_VERT");
          req.data.l[1] = _XA_NET_WM_STATE_MAXIMIZED_VERT;
          req.data.l[2] = 0;
          break;

      default: /* both */
          DTRACE_PRINTLN("WM: requestStateNet - toggling HORZ + VERT");
          req.data.l[1] = _XA_NET_WM_STATE_MAXIMIZED_HORZ;
          req.data.l[2] = _XA_NET_WM_STATE_MAXIMIZED_VERT;
          break;
    }

    req.type         = ClientMessage;
    req.window       = XtWindow(shell);
    req.message_type = _XA_NET_WM_STATE;
    req.format       = 32;
    req.data.l[0]    = _NET_WM_STATE_TOGGLE;

    XSendEvent(XtDisplay(shell), RootWindowOfScreen(XtScreen(shell)), False,
               (SubstructureRedirectMask | SubstructureNotifyMask),
               (XEvent *)&req);
}


/*
 * Request state transition from a Gnome WM (_WIN protocol) by sending
 * _WIN_STATE ClientMessage to root window.
 */
static void
awt_wm_requestStateWin(struct FrameData *wdata, jint state)
{
    Widget shell = wdata->winData.shell;
    XClientMessageEvent req;
    long win_state;             /* typeof(XClientMessageEvent.data.l) */

    /* must use awt_wm_setInitialStateWin for withdrawn windows */
    DASSERT(wdata->isShowing);

    win_state = 0;
    if (state & java_awt_Frame_MAXIMIZED_VERT) {
        win_state |= WIN_STATE_MAXIMIZED_VERT;
    }
    if (state & java_awt_Frame_MAXIMIZED_HORIZ) {
        win_state |= WIN_STATE_MAXIMIZED_HORIZ;
    }

    req.type         = ClientMessage;
    req.window       = XtWindow(shell);
    req.message_type = _XA_WIN_STATE;
    req.format       = 32;
    req.data.l[0]    = (WIN_STATE_MAXIMIZED_HORIZ | WIN_STATE_MAXIMIZED_VERT);
    req.data.l[1]    = win_state;

    XSendEvent(XtDisplay(shell), RootWindowOfScreen(XtScreen(shell)), False,
               (SubstructureRedirectMask | SubstructureNotifyMask),
               (XEvent *)&req);
}


/*
 * Specify initial state for _NET supporting WM by setting
 * _NET_WM_STATE property on the window to the desired state before
 * mapping it.
 */
static void
awt_wm_setInitialStateNet(struct FrameData *wdata, jint state)
{
    Widget shell = wdata->winData.shell;
    Window shell_win = XtWindow(shell);
    Display *dpy = XtDisplay(shell);

    Atom *old_state;
    unsigned long nitems;

    /* must use awt_wm_requestStateNet for managed windows */
    DASSERT(!wdata->isShowing);

    /* Be careful to not wipe out state bits we don't understand */
    old_state = awt_getAtomListProperty(shell_win, _XA_NET_WM_STATE, &nitems);

    if (nitems == 0) {
        /*
         * Empty or absent _NET_WM_STATE - set a new one if necessary.
         */
        Atom net_wm_state[AWT_NET_N_KNOWN_STATES];

        if (old_state != NULL) {
            XFree(old_state);
        }

        if (state & java_awt_Frame_MAXIMIZED_VERT) {
            net_wm_state[nitems++] = _XA_NET_WM_STATE_MAXIMIZED_VERT;
        }
        if (state & java_awt_Frame_MAXIMIZED_HORIZ) {
            net_wm_state[nitems++] = _XA_NET_WM_STATE_MAXIMIZED_HORZ;
        }
        DASSERT(nitems <= AWT_NET_N_KNOWN_STATES);

        if (nitems == 0) {
            DTRACE_PRINTLN("WM:     initial _NET_WM_STATE not necessary");
            return;
        }

#ifdef DEBUG
        DTRACE_PRINT("WM:     setting initial ");
        awt_wm_dtraceStateNet(net_wm_state, nitems);
#endif
        XChangeProperty(dpy, shell_win,
                        _XA_NET_WM_STATE, XA_ATOM, 32, PropModeReplace,
                        (unsigned char *)net_wm_state, nitems);
    }
    else {
        /*
         * Tweak existing _NET_WM_STATE, preserving bits we don't use.
         */
        jint want= state        /* which flags we want */
            & (java_awt_Frame_MAXIMIZED_HORIZ | java_awt_Frame_MAXIMIZED_VERT);

        jint has = 0;           /* which flags the window already has */
        int mode;               /* property mode: replace/append */

        Atom *new_state;        /* new _net_wm_state value */
        int new_nitems;
        unsigned long i;

#ifdef DEBUG
        DTRACE_PRINT("WM:     already has ");
        awt_wm_dtraceStateNet(old_state, nitems);
#endif

        for (i = 0; i < nitems; ++i) {
            if (old_state[i] == _XA_NET_WM_STATE_MAXIMIZED_HORZ) {
                has |= java_awt_Frame_MAXIMIZED_HORIZ;
            }
            else if (old_state[i] == _XA_NET_WM_STATE_MAXIMIZED_VERT) {
                has |= java_awt_Frame_MAXIMIZED_VERT;
            }
        }

        if ((has ^ want) == 0) {
            DTRACE_PRINTLN("WM:     no changes to _NET_WM_STATE necessary");
            XFree(old_state);
            return;
        }

        new_nitems = 0;
        if (has == 0) {         /* only adding flags */
            new_state = calloc(AWT_NET_N_KNOWN_STATES, sizeof(Atom));
            mode = PropModeAppend;
        }
        else {
            new_state = calloc(nitems + AWT_NET_N_KNOWN_STATES, sizeof(Atom));
            mode = PropModeReplace;
        }

        if (has != 0) {         /* copy existing flags */
            DTRACE_PRINT("WM:    ");
            for (i = 0; i < nitems; ++i) {
                if (old_state[i] == _XA_NET_WM_STATE_MAXIMIZED_HORZ) {
                    if (want & java_awt_Frame_MAXIMIZED_HORIZ) {
                        DTRACE_PRINT(" keep _HORZ");
                    } else {
                        DTRACE_PRINT(" drop _HORZ");
                        continue;
                    }
                }
                else if (old_state[i] == _XA_NET_WM_STATE_MAXIMIZED_VERT) {
                    if (want & java_awt_Frame_MAXIMIZED_VERT) {
                        DTRACE_PRINT(" keep _VERT");
                    } else {
                        DTRACE_PRINT(" drop _VERT");
                        continue;
                    }
                }
                new_state[new_nitems++] = old_state[i];
            }
        }

        /* Add missing flags */
        if ((want & java_awt_Frame_MAXIMIZED_HORIZ)
             && !(has & java_awt_Frame_MAXIMIZED_HORIZ))
        {
            DTRACE_PRINT(" add _HORZ");
            new_state[new_nitems] = _XA_NET_WM_STATE_MAXIMIZED_HORZ;
            ++new_nitems;
        }
        if ((want & java_awt_Frame_MAXIMIZED_VERT)
             && !(has & java_awt_Frame_MAXIMIZED_VERT))
        {
            DTRACE_PRINT(" add _VERT");
            new_state[new_nitems] = _XA_NET_WM_STATE_MAXIMIZED_VERT;
            ++new_nitems;
        }

        DTRACE_PRINTLN(mode == PropModeReplace ?
                       " ...  replacing" : " ...  appending");
        XChangeProperty(dpy, shell_win,
                        _XA_NET_WM_STATE, XA_ATOM, 32, mode,
                        (unsigned char *)new_state, new_nitems);
        XFree(old_state);
        XFree(new_state);
    }
}


/*
 * Specify initial state for a Gnome WM (_WIN protocol) by setting
 * WIN_STATE property on the window to the desired state before
 * mapping it.
 */
static void
awt_wm_setInitialStateWin(struct FrameData *wdata, jint state)
{
    Display *dpy = XtDisplay(wdata->winData.shell);
    Window shell_win = XtWindow(wdata->winData.shell);
    long win_state, old_win_state;

    /* must use awt_wm_requestStateWin for managed windows */
    DASSERT(!wdata->isShowing);

    /* Be careful to not wipe out state bits we don't understand */
    win_state = awt_getProperty32(shell_win, _XA_WIN_STATE, XA_CARDINAL);
    old_win_state = win_state;
#ifdef DEBUG
    if (win_state != 0) {
        DTRACE_PRINT("WM:     already has ");
        awt_wm_dtraceStateWin(win_state);
    }
#endif

    /*
     * In their stupid quest of reinventing every wheel, Gnome WM spec
     * have its own "minimized" hint (instead of using initial state
     * and WM_STATE hints).  This is bogus, but, apparently, some WMs
     * pay attention.
     */
    if (state & java_awt_Frame_ICONIFIED) {
        win_state |= WIN_STATE_MINIMIZED;
    } else {
        win_state &= ~WIN_STATE_MINIMIZED;
    }

    if (state & java_awt_Frame_MAXIMIZED_VERT) {
        win_state |= WIN_STATE_MAXIMIZED_VERT;
    } else {
        win_state &= ~WIN_STATE_MAXIMIZED_VERT;
    }

    if (state & java_awt_Frame_MAXIMIZED_HORIZ) {
        win_state |= WIN_STATE_MAXIMIZED_HORIZ;
    } else {
        win_state &= ~WIN_STATE_MAXIMIZED_HORIZ;
    }

    if (old_win_state ^ win_state) {
#ifdef DEBUG
        DTRACE_PRINT("WM:     setting initial ");
        awt_wm_dtraceStateWin(win_state);
#endif
        XChangeProperty(dpy, shell_win,
          _XA_WIN_STATE, XA_CARDINAL, 32, PropModeReplace,
          (unsigned char *)&win_state, 1);
    }
#ifdef DEBUG
    else {
        DTRACE_PRINTLN("WM:     no changes to _WIN_STATE necessary");
    }
#endif
}

/*
 * Request a layer change from a _NET supporting WM by sending
 * _NET_WM_STATE ClientMessage to root window.
 */
static void
awt_wm_requestLayerNet(struct FrameData *wdata, int state)
{
    Widget shell = wdata->winData.shell;
    Window shell_win = XtWindow(shell);
    XClientMessageEvent req;
    int currentLayer;
    long cmd;

    /* must use awt_wm_setInitialLayerNet for withdrawn windows */
    DASSERT(wdata->isShowing);

    currentLayer = awt_wm_getLayerNet(shell_win);
    if(state == currentLayer) {
       return;
    }
    cmd = currentLayer == LAYER_ALWAYS_ON_TOP && state == LAYER_NORMAL ?
                                                  _NET_WM_STATE_REMOVE :
          currentLayer == LAYER_NORMAL && state == LAYER_ALWAYS_ON_TOP  ?
                                                  _NET_WM_STATE_ADD :
                                                  _NET_WM_STATE_ADD;
    req.type          = ClientMessage;
    req.window          = XtWindow(shell);
    req.message_type = _XA_NET_WM_STATE;
    req.format          = 32;
    req.data.l[0]    = cmd;
    req.data.l[1]    = _XA_NET_WM_STATE_ABOVE;
    req.data.l[2]    = 0L;

    XSendEvent(XtDisplay(shell), RootWindowOfScreen(XtScreen(shell)), False,
           (SubstructureRedirectMask | SubstructureNotifyMask),
           (XEvent *)&req);
}

/*
 * Request a layer change from a Gnome WM (_WIN protocol) by sending
 * _WIN_LAYER ClientMessage to root window.
 */
static void
awt_wm_requestLayerWin(struct FrameData *wdata, int state)
{
    Widget shell = wdata->winData.shell;
    XClientMessageEvent req;
    Display *dpy = XtDisplay(shell);

    /* must use awt_wm_setInitialLayerWin for withdrawn windows */
    DASSERT(wdata->isShowing);

    req.type          = ClientMessage;
    req.window          = XtWindow(shell);
    req.message_type = _XA_WIN_LAYER;
    req.format          = 32;
    req.data.l[0]    = state == LAYER_NORMAL ? WIN_LAYER_NORMAL : WIN_LAYER_ONTOP;
    req.data.l[1]    = 0L;
    req.data.l[2]    = 0L;

    XSendEvent(XtDisplay(shell), RootWindowOfScreen(XtScreen(shell)), False,
           /*(SubstructureRedirectMask |*/
               SubstructureNotifyMask,
           (XEvent *)&req);
}
/*
 * Specify initial layer for _NET supporting WM by setting
 * _NET_WM_STATE property on the window to the desired state before
 * mapping it.
 * NB: looks like it doesn't have any effect.
 */
static void
awt_wm_setInitialLayerNet(struct FrameData *wdata, int state)
{
    Widget shell = wdata->winData.shell;
    Window shell_win = XtWindow(shell);
    Display *dpy = XtDisplay(shell);

    Atom *old_state;
    unsigned long nitems;
    Atom new_state = _XA_NET_WM_STATE_ABOVE;

    /* must use awt_wm_requestLayerNet for managed windows */
    DASSERT(!wdata->isShowing);

    /* Be careful to not wipe out state bits we don't understand */
    old_state = awt_getAtomListProperty(shell_win, _XA_NET_WM_STATE, &nitems);

    if (nitems == 0 && state != LAYER_ALWAYS_ON_TOP) {
        if (old_state != NULL) {
            XFree(old_state);
        }
        return;
    }else if( nitems == 0 && state == LAYER_ALWAYS_ON_TOP) {
        unsigned long data[2];
        /* create new state */
        if (old_state != NULL) {
            XFree(old_state);
        }
        nitems = 1;
        data[0] = new_state;
        data[1] = 0;
        XChangeProperty(dpy, shell_win,
                _XA_NET_WM_STATE, XA_ATOM, 32, PropModeReplace,
                (unsigned char *)data, nitems);
            XSync(dpy, False);
    }else { /* nitems > 0 */
        unsigned long i;
        Boolean bShift = False;
        int mode;
        for(i = 0; i < nitems; i++) {
            if( bShift ) {
                old_state[i-1] = old_state[i];
            }else if( old_state[i] == _XA_NET_WM_STATE_ABOVE ) {
                if(state == LAYER_ALWAYS_ON_TOP) {
                    /* no change necessary */
                    XFree(old_state);
                    return;
                }else{
                    /* wipe off this atom */
                    bShift = True;
                }
            }
        }

        if( bShift ) {
            /* atom was found and removed: change property */
            mode = PropModeReplace;
            nitems--;
        }else if( state != LAYER_ALWAYS_ON_TOP ) {
            /* atom was not found and not needed */
            XFree( old_state);
            return;
        }else {
            /* must add new atom */
            mode = PropModeAppend;
            nitems = 1;
        }

        XChangeProperty(dpy, shell_win,
                _XA_NET_WM_STATE, XA_ATOM, 32, mode,
                mode == PropModeAppend ?
                            (unsigned char *)(&new_state) :
                            (unsigned char *)old_state, nitems);
        XFree(old_state);
            XSync(dpy, False);
    }
}

/*
 * Specify initial layer for a Gnome WM (_WIN protocol) by setting
 * WIN_LAYER property on the window to the desired state before
 * mapping it.
 */
static void
awt_wm_setInitialLayerWin(struct FrameData *wdata, int state)
{
    Display *dpy = XtDisplay(wdata->winData.shell);
    Window shell_win = XtWindow(wdata->winData.shell);
    long win_state, old_win_state;
    int currentLayer;

    /* must use awt_wm_requestLayerWin for managed windows */
    DASSERT(!wdata->isShowing);

    currentLayer = awt_wm_getLayerWin(shell_win);
    if( currentLayer == state ) {
        /* no change necessary */
        return;
    }
    if( state == LAYER_ALWAYS_ON_TOP ) {
        win_state = WIN_LAYER_ONTOP;
    }else {
        win_state = WIN_LAYER_NORMAL;
    }

    XChangeProperty(dpy, shell_win,
            _XA_WIN_LAYER, XA_CARDINAL, 32, PropModeReplace,
            (unsigned char *)&win_state, 1);
}

void
awt_wm_setExtendedState(struct FrameData *wdata, jint state)
{
    Display *dpy = XtDisplay(wdata->winData.shell);
    Window shell_win = XtWindow(wdata->winData.shell);

#ifdef DEBUG
    DTRACE_PRINT2("WM: setExtendedState(0x%x/0x%x) ",
                  wdata->winData.shell, shell_win);
    awt_wm_dtraceStateJava(state);
#endif

    if (wdata->isShowing) {
        /*
         * If the window is managed by WM, we should send
         * ClientMessage requests.
         */
        if (awt_wm_doStateProtocolNet()) {
            awt_wm_requestStateNet(wdata, state);
        }
        else if (awt_wm_doStateProtocolWin()) {
            awt_wm_requestStateWin(wdata, state);
        }
        XSync(dpy, False);
    }
    else {
        /*
         * If the window is withdrawn we should set necessary
         * properties directly to the window before mapping it.
         */
        if (awt_wm_doStateProtocolNet()) {
            awt_wm_setInitialStateNet(wdata, state);
        }
        else if (awt_wm_doStateProtocolWin()) {
            awt_wm_setInitialStateWin(wdata, state);
        }
#if 1
        /*
         * Purge KWM bits.
         * Not really tested with KWM, only with WindowMaker.
         */
        XDeleteProperty(dpy, shell_win, XA_KWM_WIN_ICONIFIED);
        XDeleteProperty(dpy, shell_win, XA_KWM_WIN_MAXIMIZED);
#endif /* 1 */
    }
}

static Boolean
awt_wm_supportsLayersNet() {
    Boolean supported = awt_wm_doStateProtocolNet();

    /*
       In fact, WM may report this not supported but do support.
     */
    supported &= awt_wm_checkProtocol(_XA_NET_SUPPORTED, _XA_NET_WM_STATE_ABOVE);
    return supported;
}

static Boolean
awt_wm_supportsLayersWin() {
    Boolean supported = awt_wm_doStateProtocolWin();
    /*
     * In fact, WM may report this supported but do not support.
     */
    supported &= awt_wm_checkProtocol(_XA_WIN_PROTOCOLS, _XA_WIN_LAYER);
    return supported;
}

void
awt_wm_updateAlwaysOnTop(struct FrameData *wdata, jboolean bLayerState) {
    Display *dpy = XtDisplay(wdata->winData.shell);
    Window shell_win = XtWindow(wdata->winData.shell);
    int layerState = bLayerState ? LAYER_ALWAYS_ON_TOP : LAYER_NORMAL;

    if (wdata->isShowing) {
        /**
           We don't believe anyone, and now send both ClientMessage requests.
           And eg Metacity under RH 6.1 required both to work.
         **/
        awt_wm_requestLayerNet(wdata, layerState);
        awt_wm_requestLayerWin(wdata, layerState);
    } else {
        /**
           We don't believe anyone, and now set both atoms.
           And eg Metacity under RH 6.1 required both to work.
         **/
        awt_wm_setInitialLayerNet(wdata, layerState);
        awt_wm_setInitialLayerWin(wdata, layerState);
    }
    XSync(dpy, False);
}

/*
 * Work around for 4775545.  _NET version.
 */
static void
awt_wm_unshadeKludgeNet(struct FrameData *wdata)
{
    Display *dpy = XtDisplay(wdata->winData.shell);
    Window shell_win = XtWindow(wdata->winData.shell);
    Atom *net_wm_state;
    Boolean shaded;
    unsigned long nitems;
    unsigned long i;

    net_wm_state = awt_getAtomListProperty(shell_win,
                                           _XA_NET_WM_STATE, &nitems);
    if (nitems == 0) {
        DTRACE_PRINTLN("WM:     _NET_WM_STATE = { }");
        if (net_wm_state) {
            XFree(net_wm_state);
        }
        return;
    }
#ifdef DEBUG
    DTRACE_PRINT("WM:     ");
    awt_wm_dtraceStateNet(net_wm_state, nitems);
#endif

    shaded = False;
    for (i = 0; i < nitems; ++i) {
        if (net_wm_state[i] == _XA_NET_WM_STATE_SHADED) {
            shaded = True;
            break;
        }
    }

    if (!shaded) {
        DTRACE_PRINTLN("WM:     not _SHADED, no workaround necessary");
        return;
    }

    DTRACE_PRINTLN("WM:     removing _SHADED");
    ++i;                        /* skip _SHADED  */
    while (i < nitems) {        /* copy the rest */
        net_wm_state[i-1] = net_wm_state[i];
        ++i;
    }
    --nitems;                   /* _SHADED has been removed */

#ifdef DEBUG
    DTRACE_PRINT("WM:     ");
    awt_wm_dtraceStateNet(net_wm_state, nitems);
#endif

    WITH_XERROR_HANDLER(xerror_verify_change_property);
    {
        XChangeProperty(dpy, shell_win,
                        _XA_NET_WM_STATE, XA_ATOM, 32, PropModeReplace,
                        (unsigned char *)net_wm_state, nitems);
    }
    RESTORE_XERROR_HANDLER;

    if (xerror_code != Success) {
        DTRACE_PRINTLN1("WM:     XChangeProperty failed, error = %d",
                        xerror_code);
    }

    XFree(net_wm_state);
}


/*
 * Work around for 4775545.  _WIN version.
 */
static void
awt_wm_unshadeKludgeWin(struct FrameData *wdata)
{
    Display *dpy = XtDisplay(wdata->winData.shell);
    Window shell_win = XtWindow(wdata->winData.shell);
    long win_state;

    win_state = awt_getProperty32(shell_win, _XA_WIN_STATE, XA_CARDINAL);
#ifdef DEBUG
    DTRACE_PRINT("WM:     ");
    awt_wm_dtraceStateWin(win_state);
#endif

    if ((win_state & WIN_STATE_SHADED) == 0) {
        DTRACE_PRINTLN("WM:     not _SHADED, no workaround necessary");
        return;
    }

    win_state &= ~WIN_STATE_SHADED;
    XChangeProperty(dpy, shell_win,
                    _XA_WIN_STATE, XA_CARDINAL, 32, PropModeReplace,
                    (unsigned char *)&win_state, 1);
}


/*
 * Work around for 4775545.
 *
 * If WM exits while the top-level is shaded, the shaded hint remains
 * on the top-level properties.  When WM restarts and sees the shaded
 * window it can reparent it into a "pre-shaded" decoration frame
 * (Metacity does), and our insets logic will go crazy, b/c it will
 * see a huge nagative bottom inset.  There's no clean solution for
 * this, so let's just be weasels and drop the shaded hint if we
 * detect that WM exited.  NB: we are in for a race condition with WM
 * restart here.  NB2: e.g. WindowMaker saves the state in a private
 * property that this code knows nothing about, so this workaround is
 * not effective; other WMs might play similar tricks.
 */
void
awt_wm_unshadeKludge(struct FrameData *wdata)
{
    DTRACE_PRINTLN("WM: unshade kludge");
    DASSERT(wdata->isShowing);

    if (awt_wm_doStateProtocolNet()) {
        awt_wm_unshadeKludgeNet(wdata);
    }
    else if (awt_wm_doStateProtocolWin()) {
        awt_wm_unshadeKludgeWin(wdata);
    }
#ifdef DEBUG
    else {
        DTRACE_PRINTLN("WM:     not a _NET or _WIN supporting WM");
    }
#endif

    XSync(XtDisplay(wdata->winData.shell), False);
}


void
awt_wm_init(void)
{
    static Boolean inited = False;
    if (inited) {
        return;
    }

    awt_wm_initAtoms();
    awt_wm_getRunningWM();
    inited = True;
}

Boolean awt_wm_supportsAlwaysOnTop() {
    return awt_wm_supportsLayersNet() || awt_wm_supportsLayersWin();
}
