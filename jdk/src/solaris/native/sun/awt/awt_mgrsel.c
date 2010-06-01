/*
 * Copyright (c) 2003, 2004, Oracle and/or its affiliates. All rights reserved.
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

#include "awt_mgrsel.h"

static Atom XA_MANAGER = None;

/*
 * Structures that describes the manager selection AWT listens to with
 * callabacks to the subsytems interested in the selection.  (We only
 * listen to a couple of selections, so linear search is enough).
 */
struct AwtMgrsel {
    char *selname;              /* base name of selection atoms */
    Atom *per_scr_atoms;        /* per-screen selection atoms (ICCCM 1.2.6) */
    Atom *per_scr_owners;       /* windows currently owning the selection */
    long extra_mask;            /* extra events to listen to on owners */
    void *cookie;
    void (*callback_event)(int, XEvent *, void *); /* extra_mask events */
    void (*callback_owner)(int, Window, long *, void *); /* owner changes */
    struct AwtMgrsel *next;
};

static struct AwtMgrsel *mgrsel_list = NULL;


static int awt_mgrsel_screen(Window w);
static Window awt_mgrsel_select_per_screen(Atom, long);
static int awt_mgrsel_managed(XClientMessageEvent *mgrown);
static int awt_mgrsel_unmanaged(XDestroyWindowEvent *ev);

#ifdef DEBUG
static void awt_mgrsel_dtraceManaged(XClientMessageEvent *mgrown);
#endif



/*
 * Find which screen the window W is the root of.
 * Returns the screen number, or -1 if W is not a root.
 */
static int
awt_mgrsel_screen(Window w)
{
    Display *dpy = awt_display;
    int scr;

    for (scr = 0; scr < ScreenCount(dpy); ++scr) {
        if (w == RootWindow(dpy, scr)) {
            return (scr);
        }
    }

    return (-1);
}


/************************************************************************
 * For every one that asketh receiveth; and he that seeketh findeth;
 * and to him that knocketh it shall be opened.  (Luke 11:10).
 */


/*
 * A method for a subsytem to express its interest in a certain
 * manager selection.
 *
 * If owner changes, the callback_owner will be called with the screen
 * number and the new owning window when onwership is established, or
 * None if the owner is gone.
 *
 * Events in extra_mask are selected for on owning windows (exsiting
 * ones and on new owners when established) and callback_event will be
 * called with the screen number and an event.
 *
 * The function returns an array of current owners.  The size of the
 * array is ScreenCount(awt_display).  The array is "owned" by this
 * module and should be considered by the caller as read-only.
 */
const Window *
awt_mgrsel_select(const char *selname, long extra_mask,
                  void *cookie,
                  void (*callback_event)(int, XEvent *, void *),
                  void (*callback_owner)(int, Window, long *, void *))
{
    Display *dpy = awt_display;
    struct AwtMgrsel *mgrsel;
    Atom *per_scr_atoms;
    Window *per_scr_owners;
    char *namesbuf;
    char **names;
    int per_scr_sz;
    int nscreens = ScreenCount(dpy);
    int scr;
    Status status;

    DASSERT(selname != NULL);
    DTRACE_PRINTLN1("MG: select: %s", selname);

    /* buffer size for one per-screen atom name */
    per_scr_sz = strlen(selname) + /* "_S" */ 2 + /* %2d */ + 2 /* '\0' */+ 1;

    namesbuf = malloc(per_scr_sz * nscreens);  /* actual storage for names */
    names = malloc(sizeof(char *) * nscreens); /* pointers to names */
    per_scr_atoms = malloc(sizeof(Atom) * nscreens);
    per_scr_owners = malloc(sizeof(Window) * nscreens);
    mgrsel = malloc(sizeof(struct AwtMgrsel));

    if (namesbuf == NULL || names == NULL || per_scr_atoms == NULL
        || per_scr_owners == NULL || mgrsel == NULL)
    {
        DTRACE_PRINTLN("MG: select: unable to allocate memory");
        if (namesbuf != NULL) free(per_scr_atoms);
        if (names != NULL) free(names);
        if (per_scr_atoms != NULL) free(per_scr_atoms);
        if (per_scr_owners != NULL) free(per_scr_owners);
        if (mgrsel != NULL) free(mgrsel);
        return (NULL);
    }


    for (scr = 0; scr < nscreens; ++scr) {
        size_t sz;

        names[scr] = &namesbuf[per_scr_sz * scr];
        sz = snprintf(names[scr], per_scr_sz, "%s_S%-d", selname, scr);
        DASSERT(sz < per_scr_sz);
    }

    status = XInternAtoms(dpy, names, nscreens, False, per_scr_atoms);

    free(names);
    free(namesbuf);

    if (status == 0) {
        DTRACE_PRINTLN("MG: select: XInternAtoms failed");
        free(per_scr_atoms);
        free(per_scr_owners);
        return (NULL);
    }

    mgrsel->selname = strdup(selname);
    mgrsel->per_scr_atoms = per_scr_atoms;
    mgrsel->per_scr_owners = per_scr_owners;
    mgrsel->extra_mask = extra_mask;
    mgrsel->cookie = cookie;
    mgrsel->callback_event = callback_event;
    mgrsel->callback_owner = callback_owner;

    for (scr = 0; scr < nscreens; ++scr) {
        Window owner;

        owner = awt_mgrsel_select_per_screen(per_scr_atoms[scr], extra_mask);
        mgrsel->per_scr_owners[scr] = owner;
#ifdef DEBUG
        if (owner == None) {
            DTRACE_PRINTLN1("MG:   screen %d - None", scr);
        } else {
            DTRACE_PRINTLN2("MG:   screen %d - 0x%08lx", scr, owner);
        }
#endif
    }

    mgrsel->next = mgrsel_list;
    mgrsel_list = mgrsel;

    return (per_scr_owners);
}


static Window
awt_mgrsel_select_per_screen(Atom selection, long extra_mask)
{
    Display *dpy = awt_display;
    Window owner;

    XGrabServer(dpy);

    owner = XGetSelectionOwner(dpy, selection);
    if (owner == None) {
        /* we'll get notified later if one arrives */
        XUngrabServer(dpy);
        /* Workaround for bug 5039226 */
        XSync(dpy, False);
        return (None);
    }

    /*
     * Select for StructureNotifyMask to get DestroyNotify when owner
     * is gone.  Also select for any additional events caller is
     * interested in (e.g. PropertyChangeMask).  Caller will be
     * notifed of these events via ... XXX ...
     */
    XSelectInput(dpy, owner, StructureNotifyMask | extra_mask);

    XUngrabServer(dpy);
    /* Workaround for bug 5039226 */
    XSync(dpy, False);
    return (owner);
}


/************************************************************************
 * And so I saw the wicked buried, who had come and gone from the
 * place of the holy, and they were forgotten in the city where they
 * had so done: this is also vanity.  (Eccl 8:10)
 */

#ifdef DEBUG
/*
 * Print the message from the new manager that announces it acquired
 * ownership.
 */
static void
awt_mgrsel_dtraceManaged(XClientMessageEvent *mgrown)
{
    Display *dpy = awt_display;
    Atom selection;
    char *selname, *print_selname;
    int scr;

    scr = awt_mgrsel_screen(mgrown->window);

    selection = mgrown->data.l[1];
    print_selname = selname = XGetAtomName(dpy, selection);
    if (selname == NULL) {
        if (selection == None) {
            print_selname = "<None>";
        } else {
            print_selname = "<Unknown>";
        }
    }

    DTRACE_PRINTLN4("MG: new MANAGER for %s: screen %d, owner 0x%08lx (@%lu)",
                   print_selname, scr,
                   mgrown->data.l[2],  /* the window owning the selection */
                   mgrown->data.l[0]); /* timestamp */
    DTRACE_PRINTLN4("MG:   %ld %ld / 0x%lx 0x%lx", /* extra data */
                    mgrown->data.l[3], mgrown->data.l[4],
                    mgrown->data.l[3], mgrown->data.l[4]);

    if (selname != NULL) {
        XFree(selname);
    }
}
#endif /* DEBUG */


static int
awt_mgrsel_managed(XClientMessageEvent *mgrown)
{
    Display *dpy = awt_display;
    struct AwtMgrsel *mgrsel;
    int scr;

    long timestamp;
    Atom selection;
    Window owner;
    long *data;

    if (mgrown->message_type != XA_MANAGER) {
        DTRACE_PRINTLN("MG: ClientMessage type != MANAGER, ignoring");
        return (0);
    }

    scr = awt_mgrsel_screen(mgrown->window);

#ifdef DEBUG
    awt_mgrsel_dtraceManaged(mgrown);
#endif

    if (scr < 0) {
        DTRACE_PRINTLN("MG: MANAGER ClientMessage with a non-root window!");
        return (0);
    }

    timestamp = mgrown->data.l[0];
    selection = mgrown->data.l[1];
    owner     = mgrown->data.l[2];
    data      = &mgrown->data.l[3]; /* long[2], selection specific */

    /* is this a selection we are intrested in? */
    for (mgrsel = mgrsel_list; mgrsel != NULL; mgrsel = mgrsel->next) {
        if (selection == mgrsel->per_scr_atoms[scr])
            break;
    }

    if (mgrsel == NULL) {
        DTRACE_PRINTLN("MG: not interested in this selection, ignoring");
        return (0);
    }


    mgrsel->per_scr_owners[scr] = owner;

    XSelectInput(dpy, owner, StructureNotifyMask | mgrsel->extra_mask);

    /* notify the listener */
    if (mgrsel->callback_owner != NULL) {
        (*mgrsel->callback_owner)(scr, owner, data, mgrsel->cookie);
    }

    return (1);
}


static int
awt_mgrsel_unmanaged(XDestroyWindowEvent *ev)
{
    Display *dpy = awt_display;
    struct AwtMgrsel *mgrsel;
    Window exowner;
    int scr;

    exowner = ev->window;       /* selection owner that's gone */

    /* is this a selection we are intrested in? */
    for (mgrsel = mgrsel_list; mgrsel != NULL; mgrsel = mgrsel->next) {
        for (scr = 0; scr < ScreenCount(dpy); ++scr) {
            if (exowner == mgrsel->per_scr_owners[scr]) {
                /* can one window own selections for more than one screen? */
                goto out;       /* XXX??? */
            }
        }
    }
  out:
    if (mgrsel == NULL) {
        DTRACE_PRINTLN1("MG: DestroyNotify for 0x%08lx ignored", exowner);
        return (0);
    }

    DTRACE_PRINTLN3("MG: DestroyNotify for 0x%08lx, owner of %s at screen %d",
                    exowner, mgrsel->selname, scr);

    /* notify the listener (pass exowner as data???) */
    if (mgrsel->callback_owner != NULL) {
        (*mgrsel->callback_owner)(scr, None, NULL, mgrsel->cookie);
    }

    return (1);
}


/*
 * Hook to be called from toolkit event loop.
 */
int
awt_mgrsel_processEvent(XEvent *ev)
{
    Display *dpy = awt_display;
    struct AwtMgrsel *mgrsel;
    int scr;

    if (ev->type == ClientMessage) { /* new manager announces ownership? */
        if (awt_mgrsel_managed(&ev->xclient))
            return (1);
    }

    if (ev->type == DestroyNotify) { /* manager gives up selection? */
        if (awt_mgrsel_unmanaged(&ev->xdestroywindow))
            return (1);
    }

    /* is this an event selected on one of selection owners? */
    for (mgrsel = mgrsel_list; mgrsel != NULL; mgrsel = mgrsel->next) {
        for (scr = 0; scr < ScreenCount(dpy); ++scr) {
            if (ev->xany.window == mgrsel->per_scr_owners[scr]) {
                /* can one window own selections for more than one screen? */
                goto out;       /* XXX??? */
            }
        }
    }
  out:
    DTRACE_PRINT2("MG: screen %d, event %d ...  ",
                  scr, ev->xany.type);
    if (mgrsel == NULL) {
        DTRACE_PRINTLN("ignored");
        return (0);             /* not interested */
    }

    DTRACE_PRINT1("%s ...  ", mgrsel->selname);
    if (mgrsel->callback_event != NULL) {
        DTRACE_PRINTLN("dispatching");
        (*mgrsel->callback_event)(scr, ev, mgrsel->cookie);
    }
#ifdef DEBUG
    else {
        DTRACE_PRINTLN("no callback");
    }
#endif

    return (1);
}


void
awt_mgrsel_init(void)
{
    static Boolean inited = False;

    Display *dpy = awt_display;
    int scr;

    if (inited) {
        return;
    }

    XA_MANAGER = XInternAtom(dpy, "MANAGER", False);
    DASSERT(XA_MANAGER != None);


    /*
     * Listen for ClientMessage's on each screen's root.  We hook into
     * the message loop in the toolkit (with awt_mgrsel_processEvent)
     * to get the events processed.  We need this for notifications of
     * new manager acquiring ownership of the manager selection.
     */
    for (scr = 0; scr < ScreenCount(dpy); ++scr) {
        XSelectInput(dpy, RootWindow(dpy, scr), StructureNotifyMask);
    }

    inited = True;
}
