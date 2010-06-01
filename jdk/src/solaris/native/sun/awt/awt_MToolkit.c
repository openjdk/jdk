/*
 * Copyright (c) 1995, 2007, Oracle and/or its affiliates. All rights reserved.
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

#include "awt_p.h"

#include <sys/time.h>
#include <limits.h>
#include <locale.h>

#ifndef HEADLESS
#include <X11/cursorfont.h>
#include <Xm/MenuShell.h>
#include <Xm/RowColumn.h>
#endif /* !HEADLESS */

#include <jvm.h>
#include <jni.h>
#include <jlong.h>
#include <jni_util.h>

/* JNI headers */
#include "java_awt_AWTEvent.h"
#include "java_awt_Frame.h"
#include "java_awt_SystemColor.h"
#include "sun_awt_motif_MToolkit.h"

/* JNI field and method ids */
#include "awt_Component.h"
//#include "awt_Cursor.h"
#include "awt_MenuComponent.h"
#include "awt_TopLevel.h"
#include "canvas.h"
#include "color.h"
#include "awt_mgrsel.h"
#include "awt_wm.h"
#include "awt_DrawingSurface.h"
#include "awt_Window.h"
#include "awt_xembed.h"
#include "awt_xembed_server.h"

extern JavaVM *jvm;

#ifndef HEADLESS
#ifdef __linux__
extern void statusWindowEventHandler(XEvent event);
#endif
#endif /* !HEADLESS */

JNIEXPORT jint JNICALL
JNI_OnLoad(JavaVM *vm, void *reserved)
{
#ifndef HEADLESS
    awt_util_debug_init();
#endif /* !HEADLESS */
    jvm = vm;
    return JNI_VERSION_1_2;
}

JNIEXPORT jboolean JNICALL AWTIsHeadless() {
#ifdef HEADLESS
    return JNI_TRUE;
#else
    return JNI_FALSE;
#endif
}

#ifndef HEADLESS
static jlong awtJNI_TimeMillis(void);
extern void awt_initialize_Xm_DnD(Display*);
extern void awt_initialize_DataTransferer();

extern Display *awt_init_Display(JNIEnv *env);

extern void X11SD_LibDispose(JNIEnv *env);

extern Widget drag_source = NULL;

extern struct ComponentIDs componentIDs;
extern struct MenuComponentIDs menuComponentIDs;
extern struct MComponentPeerIDs mComponentPeerIDs;
extern struct WindowIDs windowIDs;

static Atom _XA_XSETTINGS_SETTINGS = None;
struct xsettings_callback_cookie {
    jobject mtoolkit;
    jmethodID upcallMID;
};

static struct xsettings_callback_cookie xsettings_callback_cookie;


static XEvent focusOutEvent;

static void awt_pipe_init(void);
static void processOneEvent(XtInputMask iMask);
extern void waitForEvents(JNIEnv *env, int32_t fdXPipe, int32_t fdAWTPipe);
#ifdef USE_SELECT
static void performSelect(JNIEnv *env, int32_t fdXPipe, int32_t fdAWTPipe);
#else
static void performPoll(JNIEnv *env,int32_t fdXPipe, int32_t fdAWTPipe);
#endif


#include <X11/Intrinsic.h>
#include <dlfcn.h>
#include <fcntl.h>

#ifdef USE_SELECT
#if defined(AIX)
#include <sys/select.h>
#endif
#else
#include <poll.h>
#ifndef POLLRDNORM
#define POLLRDNORM POLLIN
#endif
#endif

#ifdef NDEBUG
#undef DEBUG            /* NDEBUG overrides DEBUG */
#endif

static struct WidgetInfo *awt_winfo = (struct WidgetInfo *) NULL;
static struct MenuList* menu_list = (struct MenuList*) NULL;

#ifndef bzero
#define bzero(a,b) memset(a, 0, b)
#endif

static jboolean syncUpdated = JNI_FALSE;
static jboolean syncFailed = JNI_FALSE;
static jint eventNumber = 0;
static void syncWait_eventHandler(XEvent *);
static Atom oops_atom = None;
static Atom wm_selection = None;
static Atom version_atom = None;

static Boolean inSyncWait = False;

Widget grabbed_widget = NULL;

XtAppContext awt_appContext;
Widget awt_root_shell;
Pixel awt_defaultBg;
Pixel awt_defaultFg;
int32_t awt_multiclick_time;        /* milliseconds */
uint32_t awt_MetaMask = 0;
uint32_t awt_AltMask = 0;
uint32_t awt_NumLockMask = 0;
uint32_t awt_ModeSwitchMask = 0;
Cursor awt_scrollCursor;
Boolean  awt_ModLockIsShiftLock = False;
extern Boolean awt_UseType4Patch;
extern Boolean awt_UseXKB;

#define SPECIAL_KEY_EVENT 2

/* implement a "putback queue" -- see comments on awt_put_back_event() */
#define PUTBACK_QUEUE_MIN_INCREMENT 5   /* min size increase */
static XEvent *putbackQueue = NULL;     /* the queue -- next event is 0 */
static int32_t putbackQueueCount = 0;   /* # of events available on queue */
static int32_t putbackQueueCapacity = 0;        /* total capacity of queue */
static XtInputMask awt_events_pending(XtAppContext appContext);
static int32_t awt_get_next_put_back_event(XEvent *xev_out);

#define AWT_FLUSH_TIMEOUT    ((uint32_t)100) /* milliseconds */
#define AWT_MIN_POLL_TIMEOUT ((uint32_t)0) /* milliseconds */
#define AWT_MAX_POLL_TIMEOUT ((uint32_t)250) /* milliseconds */

#define AWT_POLL_BUFSIZE        100
#define AWT_READPIPE            (awt_pipe_fds[0])
#define AWT_WRITEPIPE           (awt_pipe_fds[1])
#define AWT_FLUSHOUTPUT_NOW()  \
{                              \
    XFlush(awt_display);       \
    awt_next_flush_time = 0LL; \
}

typedef XtIntervalId (*XTFUNC)();

static jobject  awt_MainThread = NULL;
static char     read_buf[AWT_POLL_BUFSIZE + 1];    /* dummy buf to empty pipe */
static int32_t      awt_pipe_fds[2];                   /* fds for wkaeup pipe */
static Boolean  awt_pipe_inited = False;           /* make sure pipe is initialized before write */
static int32_t      def_poll_timeout = AWT_MAX_POLL_TIMEOUT;   /* default value for timeout */
static jlong awt_next_flush_time = 0LL; /* 0 == no scheduled flush */
static void     *xt_lib = NULL;
static XTFUNC   xt_timeout = NULL;

#ifdef DEBUG_AWT_LOCK

int32_t awt_locked = 0;
char *lastF = "";
int32_t lastL = -1;

#endif

#ifndef NOMODALFIX
extern Boolean awt_isModal();
extern Boolean awt_isWidgetModal(Widget w);
#endif

Boolean keyboardGrabbed = False;

static uint32_t curPollTimeout = AWT_MAX_POLL_TIMEOUT;

/* Font information to feed Motif widgets. */
static const char      *motifFontList;
static XFontSet        defaultMotifFontSet;
static XFontStruct     *defaultMotifFontStruct;
static const char *defaultMotifFont =  /* a.k.a "fixed", known everywhere */
        "-misc-fixed-medium-r-semicondensed--13-120-75-75-c-60-iso8859-1";

XFontSet getMotifFontSet() {
    char    **missingList;
    int32_t     missingCount;
    char    *defChar;

    return XCreateFontSet(awt_display, motifFontList,
                          &missingList, &missingCount, &defChar);
}

XFontStruct *getMotifFontStruct() {
    return XLoadQueryFont(awt_display, defaultMotifFont);
}

XmFontList getMotifFontList() {
    XmFontListEntry motifFontListEntry;
    XmFontList fontlist;

    if (strchr(motifFontList, ',') == NULL) {
        /* If the default font is a single font. */
        if (defaultMotifFontStruct == NULL)
            defaultMotifFontStruct = getMotifFontStruct();
        motifFontListEntry = XmFontListEntryCreate(XmFONTLIST_DEFAULT_TAG,
                                                   XmFONT_IS_FONT,
                                           (XtPointer)defaultMotifFontStruct);
    }
    else {
        /* If the default font is multiple fonts. */
        if (defaultMotifFontSet == NULL)
            defaultMotifFontSet = getMotifFontSet();
            motifFontListEntry = XmFontListEntryCreate(XmFONTLIST_DEFAULT_TAG,
                                               XmFONT_IS_FONTSET,
                                               (XtPointer)defaultMotifFontSet);
    }
    fontlist = XmFontListAppendEntry(NULL, motifFontListEntry);
    XmFontListEntryFree(&motifFontListEntry);
    return fontlist;
}

static void
awt_set_poll_timeout (uint32_t newTimeout)
{
    DTRACE_PRINTLN1("awt_set_poll_timeout(%lu)", newTimeout);

    newTimeout = max(AWT_MIN_POLL_TIMEOUT, newTimeout);
    newTimeout = min(AWT_MAX_POLL_TIMEOUT, newTimeout);
    newTimeout = min(newTimeout, curPollTimeout);
    curPollTimeout = newTimeout;

} /* awt_set_poll_timeout */

/*
 * Gets the best timeout for the next call to poll() or select().
 * If timedOut is True, we assume that our previous timeout elapsed
 * with no events/timers arriving. Therefore, we can increase the
 * next timeout slightly.
 */
static uint32_t
awt_get_poll_timeout( Boolean timedOut )
{
    uint32_t timeout = AWT_MAX_POLL_TIMEOUT;

    DTRACE_PRINTLN2("awt_get_poll_timeout(%s), awt_next_flush_time:%ld",
        (remove?"true":"false"),
        awt_next_flush_time);

    if (timedOut) {
        /* add 1/16 (plus 1, in case the division truncates to 0) */
        curPollTimeout += ((curPollTimeout>>4) + 1);
        curPollTimeout = min(AWT_MAX_POLL_TIMEOUT, curPollTimeout);
    }
    if (awt_next_flush_time > 0) {
        int32_t flushDiff = (int32_t)(awt_next_flush_time - awtJNI_TimeMillis());
        timeout = min(curPollTimeout, flushDiff);
    } else {
        timeout = curPollTimeout;
    }

    return timeout;
} /* awt_get_poll_timeout() */

static jlong
awtJNI_TimeMillis(void)
{
    struct timeval t;

    gettimeofday(&t, 0);

    return jlong_add(jlong_mul(jint_to_jlong(t.tv_sec), jint_to_jlong(1000)),
                     jint_to_jlong(t.tv_usec / 1000));
}

static int32_t
xtError()
{
#ifdef DEBUG
    JNIEnv *env = (JNIEnv *)JNU_GetEnv(jvm, JNI_VERSION_1_2);

    jio_fprintf(stderr, "Xt error\n");
    JNU_ThrowNullPointerException(env, "NullPointerException");
#endif
    return 0;
}

static int32_t
xIOError(Display *dpy)
{
    JNIEnv *env = (JNIEnv *)JNU_GetEnv(jvm, JNI_VERSION_1_2);
    jclass cl = (*env)->FindClass(env, "java/lang/Thread");

    if (errno == EPIPE) {
        jio_fprintf(stderr, "X connection to %s host broken (explicit kill or server shutdown)\n", XDisplayName(NULL));
    }
    AWT_NOFLUSH_UNLOCK();
    JVM_RaiseSignal(JVM_SIGTERM); /* Shut down cleanly */
    if (cl != NULL) {
        JVM_Sleep(env, cl, 20000);
    }

    return 0; /* to keep compiler happy */
}

/* Like XKeysymToKeycode, but ensures that keysym is the primary
 * symbol on the keycode returned.  Returns zero otherwise.
 */
static int32_t
keysym_to_keycode_if_primary(Display *dpy, KeySym sym)
{
    KeyCode code;
    KeySym primary;

    code = XKeysymToKeycode(dpy, sym);
    if (code == 0) {
        return 0;
    }

    primary = XKeycodeToKeysym(dpy, code, 0);
    if (sym == primary) {
        return code;
    } else {
        return 0;
    }
}
/*
 * +kb or -kb ?
 */
static Boolean
isXKBenabled(Display *display) {
    int mop, beve, berr;
    /*
     * NB: TODO: hope it will return False if XkbIgnoreExtension was called!
     */
    return XQueryExtension(display, "XKEYBOARD", &mop, &beve, &berr);
}


/* Assign meaning - alt, meta, etc. - to X modifiers mod1 ... mod5.
 * Only consider primary symbols on keycodes attached to modifiers.
 */
static void
setup_modifier_map(Display *disp)
{
    KeyCode metaL      = keysym_to_keycode_if_primary(disp, XK_Meta_L);
    KeyCode metaR      = keysym_to_keycode_if_primary(disp, XK_Meta_R);
    KeyCode altL       = keysym_to_keycode_if_primary(disp, XK_Alt_L);
    KeyCode altR       = keysym_to_keycode_if_primary(disp, XK_Alt_R);
    KeyCode numLock    = keysym_to_keycode_if_primary(disp, XK_Num_Lock);
    KeyCode modeSwitch = keysym_to_keycode_if_primary(disp, XK_Mode_switch);
    KeyCode shiftLock  = keysym_to_keycode_if_primary(disp, XK_Shift_Lock);
    KeyCode capsLock   = keysym_to_keycode_if_primary(disp, XK_Caps_Lock);

    XModifierKeymap *modmap = NULL;
    int32_t nkeys, modn, i;
    char *ptr = NULL;

    DTRACE_PRINTLN("In setup_modifier_map");

    modmap = XGetModifierMapping(disp);
    nkeys = modmap->max_keypermod;

    for (modn = Mod1MapIndex;
         (modn <= Mod5MapIndex) &&
             (awt_MetaMask == 0 || awt_AltMask == 0 ||
              awt_NumLockMask == 0 || awt_ModeSwitchMask == 0);
         ++modn)
    {
        static const uint32_t modmask[8] = {
            ShiftMask, LockMask, ControlMask,
            Mod1Mask, Mod2Mask, Mod3Mask, Mod4Mask, Mod5Mask
        };


        for (i = 0; i < nkeys; ++i) {
            /* for each keycode attached to this modifier */
            KeyCode keycode = modmap->modifiermap[modn * nkeys + i];
            if (keycode == 0) {
                continue;
            }

            if (awt_MetaMask == 0 && (keycode == metaL || keycode == metaR)) {
                awt_MetaMask = modmask[modn];
                DTRACE_PRINTLN2("    awt_MetaMask       = %d, modn = %d", awt_MetaMask, modn);
                break;
            } else if (awt_AltMask == 0 && (keycode == altL || keycode == altR)) {
                awt_AltMask = modmask[modn];
                DTRACE_PRINTLN2("    awt_AltMask        = %d, modn = %d", awt_AltMask, modn);
                break;
            } else if (awt_NumLockMask == 0 && keycode == numLock) {
                awt_NumLockMask = modmask[modn];
                DTRACE_PRINTLN2("    awt_NumLockMask    = %d, modn = %d", awt_NumLockMask, modn);
                break;
            } else if (awt_ModeSwitchMask == 0 && keycode == modeSwitch) {
                awt_ModeSwitchMask = modmask[modn];
                DTRACE_PRINTLN2("    awt_ModeSwitchMask = %d, modn = %d", awt_ModeSwitchMask, modn);
                break;
            }
        }
    }
    for(i = 0; i < nkeys; i++) {
        KeyCode keycode = modmap->modifiermap[LockMapIndex * nkeys + i];
        if (keycode == 0) {
            break;
        }
        if (keycode == shiftLock) {
            awt_ModLockIsShiftLock = True;
            break;
        }
        if (keycode == capsLock) {
            break;
        }
    }

    DTRACE_PRINTLN1("    ShiftMask          = %d", ShiftMask);
    DTRACE_PRINTLN1("    ControlMask        = %d", ControlMask);

    XFreeModifiermap(modmap);
    ptr = getenv("_AWT_USE_TYPE4_PATCH");
    if( ptr != NULL && ptr[0] != 0 ) {
        if( strncmp("true", ptr, 4) == 0 ) {
           awt_UseType4Patch = True;
        }else if( strncmp("false", ptr, 5) == 0 ) {
           awt_UseType4Patch = False;
        }
    }
    awt_UseXKB = isXKBenabled(disp);

}


Boolean scrollBugWorkAround;


void
awt_output_flush()
{
    char c = 'p';

    if (awt_next_flush_time == 0)
    {
        Boolean needsWakeup = False;
        JNIEnv *env = (JNIEnv *)JNU_GetEnv(jvm, JNI_VERSION_1_2);
        if (awt_pipe_inited && (awt_get_poll_timeout(False) > (2*AWT_FLUSH_TIMEOUT))){
            needsWakeup = True;
        }
        /* awt_next_flush_time affects awt_get_poll_timeout(), so set
         * the variable *after* calling the function.
         */
        awt_next_flush_time = awtJNI_TimeMillis() + AWT_FLUSH_TIMEOUT;
        if (needsWakeup)
        {
            /* write to the utility pipe to wake up the event
             * loop, if it's sleeping
             */
            write ( AWT_WRITEPIPE, &c, 1 );
        }
    }
#ifdef FLUSHDEBUG
else
jio_fprintf(stderr, "!");
#endif
} /* awt_output_flush() */

void
null_event_handler(Widget w, XtPointer client_data,
                   XEvent * event, Boolean * cont)
{
    /* do nothing */
}

struct WidgetInfo *
findWidgetInfo(Widget widget)
{
    struct WidgetInfo *cw;

    for (cw = awt_winfo; cw != NULL; cw = cw->next) {
        if (cw->widget == widget || cw->origin == widget) {
            return cw;
        }
    }
    return NULL;
}

void
awt_addWidget(Widget w, Widget origin, void *peer, jlong event_flags)
{
    if (findWidgetInfo(w) != NULL) return;

    if (!XtIsSubclass(w, xmFileSelectionBoxWidgetClass)) {
        struct WidgetInfo *nw = (struct WidgetInfo *) malloc(sizeof(struct WidgetInfo));

        if (nw) {
            nw->widget     = w;
            nw->origin     = origin;
            nw->peer       = peer;
            nw->event_mask = event_flags;
            nw->next       = awt_winfo;
            awt_winfo      = nw;

            if (event_flags & java_awt_AWTEvent_MOUSE_EVENT_MASK) {
                XtAddEventHandler(w,
                                  ButtonPressMask | ButtonReleaseMask |
                                  EnterWindowMask | LeaveWindowMask,
                                  False, null_event_handler, NULL);
                if (w != origin) {
                    XtAddEventHandler(origin,
                                      ButtonPressMask | ButtonReleaseMask |
                                      EnterWindowMask | LeaveWindowMask,
                                      False, null_event_handler, NULL);
                }
            }
            if (event_flags & java_awt_AWTEvent_MOUSE_MOTION_EVENT_MASK) {
                XtAddEventHandler(w,
                                  PointerMotionMask,
                                  False, null_event_handler, NULL);
                if (w != origin) {
                    XtAddEventHandler(origin,
                                      PointerMotionMask,
                                      False, null_event_handler, NULL);
                }
            }
            if (event_flags & java_awt_AWTEvent_KEY_EVENT_MASK) {
                XtAddEventHandler(w,
                                  KeyPressMask | KeyReleaseMask,
                                  False, null_event_handler, NULL);
                if (w != origin) {
                    XtAddEventHandler(origin,
                                      KeyPressMask | KeyReleaseMask,
                                      False, null_event_handler, NULL);
                }
            }
        } else {
            JNIEnv *env = (JNIEnv *)JNU_GetEnv(jvm, JNI_VERSION_1_2);
            JNU_ThrowOutOfMemoryError(env, "OutOfMemoryError");
        }

    }
}

void
awt_delWidget(Widget w)
{
    struct WidgetInfo *cw;

    if (awt_winfo != NULL) {
        if ((awt_winfo->widget == w) ||
            (awt_winfo->origin == w)) {
            cw = awt_winfo;
            awt_winfo = awt_winfo->next;
            free((void *) cw);
        } else {
            struct WidgetInfo *pw;

            for (pw = awt_winfo, cw = awt_winfo->next;
                 cw != NULL;
                 pw = cw, cw = cw->next) {
                if ((cw->widget == w) ||
                    (cw->origin == w)) {
                    pw->next = cw->next;
                    free((void *) cw);
                    break;
                }
            }
        }
    }
}


void *
findPeer(Widget * pwidget)
{
    struct WidgetInfo   *cw;
    Widget widgetParent;
    void * peer;

    if ((cw = findWidgetInfo(*pwidget)) != NULL) {
        return cw->peer;
    }
    /* fix for 4053856, robi.khan@eng
       couldn't find peer corresponding to widget
       but the widget may be child of one with
       a peer, so recurse up the hierarchy */
    widgetParent = XtParent(*pwidget);
    if (widgetParent != NULL ) {
        peer = findPeer(&widgetParent);
        if( peer != NULL ) {
        /* found peer attached to ancestor of given
           widget, so set widget return value as well */
            *pwidget = widgetParent;
            return peer;
        }
    }

    return NULL;
}

Boolean
awt_isAwtWidget(Widget widget)
{
    return (findWidgetInfo(widget) != NULL);
}


static Boolean
awt_isAwtMenuWidget(Widget wdgt) {
    struct MenuList* cur;

    if (!XtIsSubclass(wdgt, xmRowColumnWidgetClass)) {
        return False;
    }
    for (cur = menu_list; cur != NULL; cur = cur->next) {
        if (cur->menu == wdgt) {
            return True;
        }
    }
    return False;
}

void
awt_addMenuWidget(Widget wdgt) {
    DASSERT(XtIsSubclass(wdgt, xmRowColumnWidgetClass));

    if (!awt_isAwtMenuWidget(wdgt)) {
        struct MenuList* ml = (struct MenuList*) malloc(sizeof(struct MenuList));
        if (ml != NULL) {
            ml->menu = wdgt;
            ml->next = menu_list;
            menu_list = ml;
        } else {
            JNIEnv* env = (JNIEnv*)JNU_GetEnv(jvm, JNI_VERSION_1_2);
            JNU_ThrowOutOfMemoryError(env, "OutOfMemoryError");
        }
    }
}

void
awt_delMenuWidget(Widget wdgt) {
    struct MenuList** pp;
    struct MenuList* p;

    DASSERT(XtIsSubclass(wdgt, xmRowColumnWidgetClass));

    for (pp = &menu_list; *pp != NULL; pp = &((*pp)->next)) {
        if ((*pp)->menu == wdgt) {
            p = *pp;
            *pp = (*pp)->next;
            free((void*)p);
            break;
        }
    }
}


static Widget
getShellWidgetByPart(Widget part) {
    int i;
    for (i = 0; i < 3; i++) {
        if (part == NULL) return NULL;
        if (XtIsShell(part)) return part;
        part = XtParent(part);
    }
    return NULL;
}

static Boolean
isTheSameShellWidget(Widget shell, Widget w) {
    Widget s1, s2;
    if (shell == NULL || w == NULL) return False;
    s1 = getShellWidgetByPart(shell);
    s2 = getShellWidgetByPart(w);
    if (s1 == s2 && s1 != NULL) {
        return True;
    } else {
        return False;
    }
}

static Boolean
shouldDispatchToWidget(XEvent * xev)
{
  /* If this function returns False, that means that it has not pre-posted
     this event to Java. The caller will then dispatch the event to Motif,
     and our handlers will be called to post it to Java.
     If this function returns true, then this function has posted this event
     to java before returning. The caller will not dispatch it to Motif;
     it will be dispatched to Motif via the putbackQueue after it has been
     processed by Java */

    Window win;
    Widget widget = NULL;
    struct WidgetInfo *winfo;
    void *peer = NULL;
    Boolean cont = FALSE;

    switch (xev->type) {
        case KeyPress:
        case KeyRelease:
            win = xev->xkey.window;
            break;
        case FocusIn:
        case FocusOut:
            win = xev->xfocus.window;
            break;
        case ButtonPress:
        case ButtonRelease:
            win = xev->xbutton.window;
            break;
        case MotionNotify:
            win = xev->xmotion.window;
            break;
        case EnterNotify:
        case LeaveNotify:
            win = xev->xcrossing.window;
            break;
        default:
            return False;
    }

    if ((widget = XtWindowToWidget(awt_display, win)) == NULL) {
        return False;
    }

    if (xev->type == KeyPress || xev->type == KeyRelease) {
        Widget focusWidget = XmGetFocusWidget(widget);

        /* Fix for 4328561 by ibd@sparc.spb.su
           If the widget is a Choice, the widget with focus is probably lying
           outside the current widget's sub-hierarchy, so we have to go up the
           hierarchy to reach it */

        if ((focusWidget == NULL) && XmIsMenuShell(widget)) {
            if ((widget = XtParent(widget)) != NULL) {
                focusWidget = XmGetFocusWidget(widget);
            } else {
                return False;
            }

            /* In this case, focus widget should be CascadeButtonGadget type,
               but we should send the events to its parent */
            if (focusWidget != NULL && XmIsCascadeButtonGadget(focusWidget)) {
                widget = XtParent(focusWidget);
            } else {
                /* If something went wrong, restore the original status */
                widget = XtWindowToWidget(awt_display, win);
            }
        }

        /* if focus owner is null, redirect key events to focused window */
        if (focusWidget == NULL && findWidgetInfo(widget) == NULL) {
            focusWidget = findTopLevelByShell(widget);
        }

        /* If we are on a non-choice widget, process events in a normal way */
        if ((focusWidget != NULL) && (focusWidget != widget)) {
            if (isTheSameShellWidget(focusWidget, widget)) {
                focusWidget = findTopLevelByShell(widget);
            }
            if (focusWidget != NULL) {
                peer = findPeer(&focusWidget);
            }
            if (peer != NULL) {
                widget = focusWidget;
                win = xev->xkey.window = XtWindow(focusWidget);
            }
        }
    }

    if ((winfo = findWidgetInfo(widget)) == NULL) {
        return False;
    }

    /*
     * Fix for bug 4145193
     *
     * If a menu is up (not just a popup menu), prevent awt components from
     * getting any events until the menu is popped down.
     * Before this fix, the fact that mouse/button events were
     * preposted to the Java event queue was causing the ButtonRelease
     * (needed to pop menu down) to be seen by the menu's parent and
     * not the menu.
     */
    if (awtMenuIsActive()) {
        Widget focusWidget = XmGetFocusWidget(widget);

        if (focusWidget == NULL) {
            return False;
        }

        /* If we are on a choice, dispatch the events to widget, but do not
         * dispatch the events if we are on popped up menu.
         */
        if (!XmIsRowColumn(widget) || !XmIsCascadeButtonGadget(focusWidget)) {
            /* Fix for 4328557 by ibd@sparc.spb.su
             * If we are dragging mouse from choice and are currently outside
             * of it, dispatch events to the choice - the source of dragging.
             */

            if ((drag_source != NULL) && (widget != drag_source) &&
                (peer = findPeer(&drag_source))) {
                awt_canvas_handleEvent(drag_source, peer, xev, winfo, &cont, TRUE);
            }
            return False;
        }
    }

    /* If the keyboard is grabbed by a popup (such as a choice) during
       a time when a focus proxy is in effect, the abovefocusIsOnMenu
       test will not detect the sitation because the focus will be on
       the proxy. But we need events to go to Motif first, so that the
       grab can be undone when appropriate. */
    if (keyboardGrabbed) {
        return False;
    }

    /* If it's a keyboard event, we need to find the peer associated */
    /* with the widget that has the focus rather than the widget */
    /* associated with the window in the X event. */

    switch (xev->type) {
      case KeyPress:
      case KeyRelease:
          if (!(winfo->event_mask & java_awt_AWTEvent_KEY_EVENT_MASK))
              return False;
          break;
        case FocusIn:
        case FocusOut:
            if (!(winfo->event_mask & java_awt_AWTEvent_FOCUS_EVENT_MASK))
                return False;
            break;
        case ButtonPress:
        case ButtonRelease:
            if (!(winfo->event_mask & java_awt_AWTEvent_MOUSE_EVENT_MASK)) {
                return False;
            }
            break;
        case EnterNotify:
        case LeaveNotify:
            /*
             * Do not post the enter/leave event if it's on a subwidget
             * within the component.
             */
            if (!(winfo->event_mask & java_awt_AWTEvent_MOUSE_EVENT_MASK) ||
                widget != winfo->origin)
                return False;
            break;
        case MotionNotify:
            if (!(winfo->event_mask & java_awt_AWTEvent_MOUSE_MOTION_EVENT_MASK))
                return False;
            break;
        default:
            return False;
    }

    peer = winfo->peer;

    /* If we found a widget and a suitable peer (either the focus
       peer above or the one associated with the widget then we
       dispatch to it. */
    if (peer == NULL) {
        return False;
    }

    /*
     * Fix for bug 4173714 - java.awt.button behaves differently under
     * Win32/Solaris.
     * Component should not get any events when it's disabled.
     */
    if (!XtIsSensitive(widget)) {
        if (xev->type == EnterNotify) {
            updateCursor(peer, CACHE_UPDATE);
        }
        return False;
    }

    awt_canvas_handleEvent(widget, peer, xev, winfo, &cont, TRUE);
    return (!cont);
} /* shouldDispatchToWidget() */


void set_toolkit_busy(Boolean busy) {

    JNIEnv *env = (JNIEnv *)JNU_GetEnv(jvm, JNI_VERSION_1_2);

    static jclass awtAutoShutdownClass = NULL;
    static jmethodID notifyBusyMethodID = NULL;
    static jmethodID notifyFreeMethodID = NULL;

    if (awtAutoShutdownClass == NULL) {
        jclass awtAutoShutdownClassLocal = (*env)->FindClass(env, "sun/awt/AWTAutoShutdown");
        if ((*env)->ExceptionOccurred(env)) {
            (*env)->ExceptionDescribe(env);
            (*env)->ExceptionClear(env);
        }
        DASSERT(awtAutoShutdownClassLocal != NULL);
        if (awtAutoShutdownClassLocal == NULL) {
            return;
        }

        awtAutoShutdownClass = (jclass)(*env)->NewGlobalRef(env, awtAutoShutdownClassLocal);
        (*env)->DeleteLocalRef(env, awtAutoShutdownClassLocal);

        notifyBusyMethodID = (*env)->GetStaticMethodID(env, awtAutoShutdownClass,
                                                    "notifyToolkitThreadBusy", "()V");
        if ((*env)->ExceptionOccurred(env)) {
            (*env)->ExceptionDescribe(env);
            (*env)->ExceptionClear(env);
        }
        notifyFreeMethodID = (*env)->GetStaticMethodID(env, awtAutoShutdownClass,
                                                    "notifyToolkitThreadFree", "()V");
        if ((*env)->ExceptionOccurred(env)) {
            (*env)->ExceptionDescribe(env);
            (*env)->ExceptionClear(env);
        }
        DASSERT(notifyBusyMethodID != NULL);
        DASSERT(notifyFreeMethodID != NULL);
        if (notifyBusyMethodID == NULL || notifyFreeMethodID == NULL) {
            return;
        }
    } /* awtAutoShutdownClass == NULL*/

    if (busy) {
        (*env)->CallStaticVoidMethod(env, awtAutoShutdownClass,
                                     notifyBusyMethodID);
    } else {
        (*env)->CallStaticVoidMethod(env, awtAutoShutdownClass,
                                     notifyFreeMethodID);
    }

    if ((*env)->ExceptionOccurred(env)) {
        (*env)->ExceptionDescribe(env);
        (*env)->ExceptionClear(env);
    }
}

#ifdef DEBUG
static int32_t debugPrintLineCount = 0;   /* limit debug output per line */
#endif

/*
 * This is the main Xt event loop for the AWT.
 *
 * Because java applications are multithreaded, but X and Xt
 * are thread-dumb, we must make special considerations to
 * make ensure that the X/Xt libraries are not entered by
 * multiple threads simultaneously.
 *
 * The biggest difference between the standard Xt loop
 * and this loop is that we go to great lengths never to block
 * in the X libraries. We poll() on the X event pipe, waiting
 * for events, rather than simply calling XtAppNextEvent() and
 * blocking. If this thread were to block in XtAppNextEvent(),
 * no other thread could enter (e.g., to perform a paint or
 * retrieve data).
 */
/* #ifdef DEBUG */
    int32_t  numEventsHandled = 0;
/* #endif */
static void
awt_MToolkit_loop(JNIEnv *env)
{
    XtInputMask iMask;
    int32_t  fdXPipe = -1;              /* pipe where X events arrive */

    /* only privileged thread should be running here */
    DASSERT(awt_currentThreadIsPrivileged(env));

    /* The pipe where X events arrive */
    fdXPipe = ConnectionNumber(awt_display) ;

    /* We execute events while locked, unlocking only when waiting
     * for an event
     */
    AWT_LOCK();

    /* Create the AWT utility pipe. See the comments on awt_pipe_init() */
    awt_pipe_init();

    /*
     * Need to flush here in case data on the connection was read
     * before we acquired the monitor.
     *
     * I don't get this, but I'm too chicken to remove it. -jethro 2Sep98
     */
    AWT_FLUSHOUTPUT_NOW();

    /*
     * ACTUALLY PROCESS EVENTS
     */
    while(True) {

        /* process all events in the queue */
/*      #ifdef DEBUG */
/*          numEventsHandled = 0; */
/*      #endif */
        while (((iMask = awt_events_pending(awt_appContext)) & XtIMAll) > 0) {

/*          #ifdef DEBUG */
                ++numEventsHandled;
/*          #endif */
            processOneEvent(iMask);

        }  /* end while awt_events_pending() */
        /* At this point, we have exhausted the event queue */

        /* print the number of events handled in parens */
        DTRACE_PRINT1("(%d events)",(int32_t)numEventsHandled);
#ifdef DEBUG
        if (++debugPrintLineCount > 8) {
            DTRACE_PRINTLN("");
            debugPrintLineCount = 0;
        }
#endif

        AWT_NOTIFY_ALL();               /* wake up modalWait() */

        set_toolkit_busy(False);

        /* Here, we wait for X events, outside of the X libs. When
         * it's likely that an event is waiting, we process the queue
         */
        waitForEvents(env, fdXPipe, AWT_READPIPE);

        set_toolkit_busy(True);

    } /* while(True) */

    /* If we ever exit the loop, must unlock the toolkit */

} /* awt_MToolkit_loop() */

/*
 * Creates the AWT utility pipe. This pipe exists solely so that
 * we can cause the main event thread to wake up from a poll() or
 * select() by writing to this pipe.
 */
static void
awt_pipe_init(void) {

    if (awt_pipe_inited) {
        return;
    }

    if ( pipe ( awt_pipe_fds ) == 0 )
    {
        /*
        ** the write wakes us up from the infinite sleep, which
        ** then we cause a delay of AWT_FLUSHTIME and then we
        ** flush.
        */
        int32_t flags = 0;
        awt_set_poll_timeout (def_poll_timeout);
        /* set the pipe to be non-blocking */
        flags = fcntl ( AWT_READPIPE, F_GETFL, 0 );
        fcntl( AWT_READPIPE, F_SETFL, flags | O_NDELAY | O_NONBLOCK );
        flags = fcntl ( AWT_WRITEPIPE, F_GETFL, 0 );
        fcntl( AWT_WRITEPIPE, F_SETFL, flags | O_NDELAY | O_NONBLOCK );
        awt_pipe_inited = True;
    }
    else
    {
        AWT_READPIPE = -1;
        AWT_WRITEPIPE = -1;
        awt_pipe_inited = False;
    }
} /* awt_pipe_init() */

static Window
proxyTopLevel(Window proxyWindow) {
    Window parent = None, root = None, *children = NULL, retvalue = None;
    uint32_t nchildren = 0;
    Status res = XQueryTree(awt_display, proxyWindow, &root, &parent,
             &children, &nchildren);
    if (res != 0) {
        if (nchildren > 0) {
            retvalue = children[0];
        }
        else retvalue = None;
        if (children != NULL) {
            XFree(children);
        }
        return retvalue;
    } else {
        return None;
    }
}

static jclass clazzF, clazzD = NULL;

static Boolean
initClazzD(JNIEnv *env) {
    jclass t_clazzD = (*env)->FindClass(env, "java/awt/Dialog");
    if ((*env)->ExceptionOccurred(env)) {
        (*env)->ExceptionDescribe(env);
        (*env)->ExceptionClear(env);
    }
    DASSERT(t_clazzD != NULL);
    if (t_clazzD == NULL) {
        return False;
    }
    clazzD = (*env)->NewGlobalRef(env, t_clazzD);
    DASSERT(clazzD != NULL);
    (*env)->DeleteLocalRef(env, t_clazzD);
    return True;
}

Boolean
isFrameOrDialog(jobject target, JNIEnv *env) {
    if ((*env)->EnsureLocalCapacity(env, 1) < 0) {
        return False;
    }

    if (clazzF == NULL) {
        jclass t_clazzF = (*env)->FindClass(env, "java/awt/Frame");
        if ((*env)->ExceptionOccurred(env)) {
            (*env)->ExceptionDescribe(env);
            (*env)->ExceptionClear(env);
        }
        DASSERT(t_clazzF != NULL);
        if (t_clazzF == NULL) {
            return False;
        }
        clazzF = (*env)->NewGlobalRef(env, t_clazzF);
        DASSERT(clazzF != NULL);
        (*env)->DeleteLocalRef(env, t_clazzF);
    }

    if (clazzD == NULL && !initClazzD(env)) {
        return False;
    }

    return (*env)->IsInstanceOf(env, target, clazzF) ||
        (*env)->IsInstanceOf(env, target, clazzD);
}

Boolean
isDialog(jobject target, JNIEnv *env) {
    if (clazzD == NULL && !initClazzD(env)) {
        return False;
    }
    return (*env)->IsInstanceOf(env, target, clazzD);
}

// Returns a local ref to a decorated owner of the target,
// or NULL if the target is Frame or Dialog itself.
// The local ref returned should be deleted by the caller.
jobject
getOwningFrameOrDialog(jobject target, JNIEnv *env) {
    jobject _target = (*env)->NewLocalRef(env, target);
    jobject parent = _target;
    Boolean isSelfFrameOrDialog = True;

    while (!isFrameOrDialog(parent, env)) {
        isSelfFrameOrDialog = False;
        parent = (*env)->CallObjectMethod(env, _target, componentIDs.getParent);
        (*env)->DeleteLocalRef(env, _target);
        _target = parent;
    }

    if (isSelfFrameOrDialog) {
        (*env)->DeleteLocalRef(env, parent);
        return NULL;
    }
    return parent;
}

Widget
findWindowsProxy(jobject window, JNIEnv *env) {
    struct ComponentData *cdata;
    jobject tlPeer;
    jobject owner_prev = NULL, owner_new = NULL;
    /* the owner of a Window is in its parent field */
    /* we may have a chain of Windows; go up the chain till we find the
       owning Frame or Dialog */
    if ((*env)->EnsureLocalCapacity(env, 4) < 0) {
        return NULL;
    }

    if (window == NULL) return NULL;

    owner_prev = (*env)->NewLocalRef(env, window);
    while (!JNU_IsNull(env, owner_prev) && !(isFrameOrDialog(owner_prev, env))) {
        owner_new = (*env)->CallObjectMethod(env, owner_prev, componentIDs.getParent);
        (*env)->DeleteLocalRef(env, owner_prev);
        owner_prev = owner_new;
    }

    if (owner_prev == NULL) return NULL;

    tlPeer = (*env)->GetObjectField(env, owner_prev, componentIDs.peer);
    (*env)->DeleteLocalRef(env, owner_prev);
    if (tlPeer == NULL) return NULL;

    cdata = (struct ComponentData *)
        JNU_GetLongFieldAsPtr(env, tlPeer, mComponentPeerIDs.pData);
    (*env)->DeleteLocalRef(env, tlPeer);

    if (cdata == NULL) return NULL;
    return(findFocusProxy(cdata->widget));
}

jobject
findTopLevel(jobject peer, JNIEnv *env) {
    jobject target_prev = NULL;
    static jclass clazzW = NULL;

    if ((*env)->EnsureLocalCapacity(env, 3) < 0) {
        return NULL;
    }

    if (clazzW == NULL) {
        jclass t_clazzW = (*env)->FindClass(env, "java/awt/Window");
        if ((*env)->ExceptionOccurred(env)) {
            (*env)->ExceptionDescribe(env);
            (*env)->ExceptionClear(env);
        }
        DASSERT(t_clazzW != NULL);
        if (t_clazzW == NULL) {
            return NULL;
        }
        clazzW = (*env)->NewGlobalRef(env, t_clazzW);
        DASSERT(clazzW != NULL);
        (*env)->DeleteLocalRef(env, t_clazzW);
    }
    target_prev = (*env)->GetObjectField(env, peer, mComponentPeerIDs.target);
    if (target_prev == NULL) {
        return NULL;
    }

    while ((target_prev != NULL)
           && !(*env)->IsInstanceOf(env, target_prev, clazzW) )
    {
        /* go up the hierarchy until we find a window */
        jobject target_new = (*env)->CallObjectMethod(env, target_prev, componentIDs.getParent);
        (*env)->DeleteLocalRef(env, target_prev);
        target_prev = target_new;
    }
    return target_prev;
}

static Window
rootWindow(Window w) {
    Window root = None;
    Window parent = None;
    Window *children = NULL;
    uint32_t nchildren = 0;

    if (w != None) {
        Status res = XQueryTree(awt_display, w, &root, &parent, &children, &nchildren);
        if (res == 0) {
            return None;
        }
        if (children != NULL) {
            XFree(children);
        }
  return root;
    } else {
        return None;
    }
}

Boolean IsRootOf(Window root, Window child) {
    Window w_root = None, w_parent = None, * children = NULL;
    uint32_t c_count = 0;
    if (root == None || child == None) {
        return False;
    }
    do {
        w_root = None;
        w_parent = None;
        children = NULL;
        c_count = 0;
        if (XQueryTree(awt_display, child, &w_root, &w_parent,
                       &children, &c_count)) {
            if (children != NULL) {
                XFree(children);
            }
            if (w_parent == None) {
                return False;
            }
            if (w_parent == root) {
                return True;
            }
        } else {
            return False;
        }
        child = w_parent;
    } while (True);
}

Window findShellByProxy(Window proxy) {
    Widget proxy_wid = XtWindowToWidget(awt_display, proxy);
    while (proxy_wid != NULL && !XtIsShell(proxy_wid)) {
        proxy_wid = XtParent(proxy_wid);
    }
    if (proxy_wid == NULL) {
        return None;
    }
    return XtWindow(proxy_wid);
}

// Window which contains focus owner when focus proxy is enabled
Window trueFocusWindow = None;
// Window which works as proxy for input events for real focus owner.
Window focusProxyWindow = None;

void clearFocusPathOnWindow(Window win) {
    if (focusProxyWindow != None && IsRootOf(win, trueFocusWindow)) {
        XEvent ev;
        memset(&ev, 0, sizeof(ev));
        ev.type = FocusOut;
        ev.xany.send_event = True;
        ev.xany.display = awt_display;
        ev.xfocus.mode = NotifyNormal;
        ev.xfocus.detail = NotifyNonlinear;
        {
            Window root = rootWindow(trueFocusWindow);
            JNIEnv *env = (JNIEnv *) JNU_GetEnv(jvm, JNI_VERSION_1_2);
            ev.xfocus.window = trueFocusWindow;
            while (ev.xfocus.window != root &&
                   ev.xfocus.window != None) {
                Widget w = XtWindowToWidget(awt_display,
                                            ev.xfocus.window);
                awt_put_back_event(env, &ev);
                if (w == NULL) {
                    break;
                }
                if (XtParent(w) != NULL) {
                    ev.xfocus.window = XtWindow(XtParent(w));
                } else {
                    ev.xfocus.window = None;
                }
            }
        }
        XSetInputFocus(awt_display, findShellByProxy(focusProxyWindow), RevertToPointerRoot, CurrentTime);
        trueFocusWindow = None;
        focusProxyWindow = None;
    }
}
void clearFocusPath(Widget shell) {
    Window w = None;
    if (shell == NULL) {
        return;
    }
    w = XtWindow(shell);
    clearFocusPathOnWindow(w);
}

void globalClearFocusPath(Widget focusOwnerShell ) {
    if (focusProxyWindow != None) {
        Window shellWindow = findShellByProxy(trueFocusWindow);
        if (shellWindow != None) {
            Widget shell = XtWindowToWidget(awt_display, shellWindow);
            if (shell != NULL && shell != focusOwnerShell) {
                clearFocusPath(shell);
            }
        }
    }
}

static void
focusEventForProxy(XEvent xev,
                   JNIEnv *env,
                   Window *trueFocusWindow,
                   Window *focusProxyWindow) {

    DASSERT (trueFocusWindow != NULL && focusProxyWindow != NULL);
  if (xev.type == FocusOut) {
    if (xev.xfocus.window == *focusProxyWindow) {
            if (*trueFocusWindow != None) {
                Window root = rootWindow(*trueFocusWindow);
      focusOutEvent.xfocus.window = *trueFocusWindow;
#ifdef DEBUG_FOCUS
      printf(" nulling out proxy; putting back event"
             "\n");
#endif

      while (focusOutEvent.xfocus.window != root &&
             focusOutEvent.xfocus.window != None) {
        Widget w = XtWindowToWidget(awt_display,
                                    focusOutEvent.xfocus.window);
        awt_put_back_event(env, &focusOutEvent);
        if (w != NULL && XtParent(w) != NULL) {
          focusOutEvent.xfocus.window = XtWindow(XtParent(w));
        } else {
          focusOutEvent.xfocus.window = None;
        }
      }
      *trueFocusWindow = None;
      *focusProxyWindow = None;
      return;
    } else {
#ifdef DEBUG_FOCUS
      printf("\n");
#endif
      return;
    }
  } else {
#ifdef DEBUG_FOCUS
    printf("\n");
#endif
    return;
  }
    }
}

static void
focusEventForFrame(XEvent xev, Window focusProxyWindow) {
  if (xev.type == FocusIn) {
    if (focusProxyWindow != None) {
      /* eat it */
      return;
    } else /* FocusIn on Frame or Dialog */ {
      XtDispatchEvent(&xev);
    }
  } else /* FocusOut on Frame or Dialog */{
    XtDispatchEvent(&xev);
  }
}

static void
focusEventForWindow(XEvent xev, JNIEnv *env, Window *trueFocusWindow,
                    Window *focusProxyWindow, jobject target) {
  XEvent pev;
  if (xev.type == FocusIn && xev.xfocus.mode == NotifyNormal) {
    /* If it's a FocusIn, allow it to process, then set
       focus to focus proxy */
    Widget focusProxy;
    focusProxy = findWindowsProxy(target, env);
    if (focusProxy != NULL) {
      XtDispatchEvent(&xev);
      *focusProxyWindow = XtWindow(focusProxy);

      XSetInputFocus(awt_display, *focusProxyWindow,
                     RevertToParent,
                     CurrentTime);

      XPeekEvent(awt_display, &pev);
      while (pev.type == FocusIn) {
        XNextEvent(awt_display, &xev);
        XPeekEvent(awt_display, &pev);
      }
      *trueFocusWindow = xev.xany.window;

    } /* otherwise error */
  } else /* FocusOut */ {
    /* If it's a FocusOut on a Window, discard it unless
       it's an event generated by us. */
    if (xev.xany.send_event) {
      XtDispatchEvent(&xev);
    }
  }
}

Boolean
isAncestor(Window ancestor, Window child) {
  Window *children;
  uint32_t nchildren;
  Boolean retvalue = False;

  while (child != ancestor) {
    Window parent, root;
    Status status;

    status = XQueryTree(awt_display, child, &root, &parent,
                        &children, &nchildren);
    if (status == 0) return False; /* should be an error of some sort? */

    if (parent == root) {
      if (child != ancestor) {
        retvalue = False;
        break;
      } else {
        retvalue = True;
        break;
      }
    }
    if (parent == ancestor) { retvalue = True; break; }
    if (nchildren > 0) XFree(children);
    child = parent;
  }
  if (nchildren > 0) XFree(children);
  return retvalue;
}

/**
 * Returns focusability of the corresponding Java Window object
 */
Boolean
isFocusableWindow(Window w) {
    Widget wid = NULL;
    JNIEnv *env = (JNIEnv *) JNU_GetEnv(jvm, JNI_VERSION_1_2);

    wid = XtWindowToWidget(awt_display, w);
    while (wid != NULL && !XtIsShell(wid)) {
        wid = XtParent(wid);
    }

    // If the window doesn't have shell consider it focusable as all windows
    // are focusable by default
    if (wid == NULL) return True;

    return isFocusableWindowByShell(env, wid);
}

void postUngrabEvent(Widget shell) {
    JNIEnv *env = (JNIEnv *) JNU_GetEnv(jvm, JNI_VERSION_1_2);
    Widget canvas = findTopLevelByShell(shell);
    if (canvas != NULL) {
        jobject peer = findPeer(&canvas);
        if (peer != NULL) {
            JNU_CallMethodByName(env, NULL, peer, "postUngrabEvent", "()V", NULL);
        }
    }
}

Boolean eventInsideGrabbed(XEvent * ev) {
    if (grabbed_widget == NULL) {
        return False;
    }

    switch (ev->xany.type) {
      case LeaveNotify:
      case ButtonPress:
      case ButtonRelease:
      case MotionNotify:
      case EnterNotify:
      {
          JNIEnv *env = (JNIEnv *)JNU_GetEnv(jvm, JNI_VERSION_1_2);
          Widget grab = findTopLevelByShell(grabbed_widget);
          if (grab != NULL) {
              jobject peer = findPeer(&grab);
              Widget target = XtWindowToWidget(awt_display, ev->xbutton.window);
              jobject targetPeer = findPeer(&target);
              if (peer != NULL) {
                  return JNU_CallMethodByName(env, NULL, peer, "processUngrabMouseEvent", "(Lsun/awt/motif/MComponentPeer;III)Z",
                                              targetPeer, ev->xbutton.x_root, ev->xbutton.y_root,
                                              ev->xany.type, NULL).z;
              }
          }
          return False;
      }
      case FocusOut:
          if (ev->xfocus.window == XtWindow(grabbed_widget) ||
              isAncestor(XtWindow(grabbed_widget), ev->xfocus.window))
          {
              postUngrabEvent(grabbed_widget);
              return True;
          }
      default:
          return True;
    }
}

/**
 * Processes and removes one X/Xt event from the Xt event queue.
 * Handles events pushed back via awt_put_back_event() FIRST,
 * then new events on the X queue
 */
static void
processOneEvent(XtInputMask iMask) {
            XEvent xev;
            Boolean haveEvent = False;
            if (putbackQueueCount > 0) {
                // There is a pushed-back event - handle it first
                if (awt_get_next_put_back_event(&xev) == 0) {
                    if (xev.xany.send_event != SPECIAL_KEY_EVENT) {
#ifdef DEBUG_FOCUS
                        if (xev.type == FocusOut) {
                            printf("putback FocusOut on window %d, mode %d, "
                                   "detail %d, send_event  %d\n",
                                   xev.xfocus.window, xev.xfocus.mode,
                                   xev.xfocus.detail, xev.xfocus.send_event);
                        }
#endif
                        eventNumber++;
                        XtDispatchEvent(&xev);
                        return;
                    } else {
                        haveEvent = True;
                    }
                }
            }

            if (haveEvent || XtAppPeekEvent(awt_appContext, &xev)) {
             /*
              * Fix for BugTraq ID 4041235, 4100167:
              * First check that the event still has a widget, because
              * the widget may have been destroyed by another thread.
              */
              Widget widget=XtWindowToWidget(awt_display, xev.xany.window);
              eventNumber++;
#ifdef __linux__
              statusWindowEventHandler(xev);
#endif
              xembed_eventHandler(&xev);
              xembed_serverEventHandler(&xev);
              syncWait_eventHandler(&xev);

              if (!haveEvent && awt_dnd_process_event(&xev)) {
                  return;
              }

              if ((widget == NULL) || (!XtIsObject(widget)) ||
                  (widget->core.being_destroyed)) {
                /*
                 * if we get here, the event could be one of
                 * the following:
                 * - notification that a "container" of
                 *    any of our embedded frame has been moved
                 * - event understandable by XFilterEvent
                 * - for one of our old widget which has gone away
                 */
                XNextEvent(awt_display, &xev);

                if (widget == NULL) {
                    /* an embedded frame container has been moved? */
                    if (awt_util_processEventForEmbeddedFrame(&xev)) {
                        return;
                    }

                    /* manager selections related event? */
                    if (awt_mgrsel_processEvent(&xev)) {
                        return;
                    }
                }

                /*
                 * Fix for BugTraq ID 4196573:
                 * Call XFilterEvent() to give a chance to X Input
                 * Method to process this event before being
                 * discarded.
                 */
                (void) XFilterEvent(&xev, NULL);
                return;
              }

              /* There is an X event on the queue. */
              switch (xev.type) {
              case KeyPress:
              case KeyRelease:
              case ButtonPress:
              case ButtonRelease:
              case MotionNotify:
              case EnterNotify:
              case LeaveNotify:
                /* Fix for BugTraq ID 4048060. Dispatch scrolling events
                   immediately to the ScrollBar widget to prevent spurious
                   continuous scrolling. Otherwise, if the application is busy,
                   the ButtonRelease event is not dispatched in time to prevent
                   a ScrollBar timeout from expiring, and restarting the
                   continuous scrolling timer.
                   */
                  if ((xev.type == ButtonPress                          ||
                       xev.type == ButtonRelease                                ||
                       (xev.type == MotionNotify                                &&
                        (xev.xmotion.state == Button1Mask                       ||
                         xev.xmotion.state == Button2Mask                       ||
                         xev.xmotion.state == Button3Mask)))            &&
                      (XtIsSubclass(widget, xmScrollBarWidgetClass))) {
                      /* Use XNextEvent instead of XtAppNextEvent, because
                         XtAppNextEvent processes timers before getting the next X
                         event, causing a race condition, since the TimerEvent
                         callback in the ScrollBar widget restarts the continuous
                         scrolling timer.
                      */
                      XNextEvent(awt_display, &xev);

                      XtDispatchEvent(&xev);
                      XSync(awt_display, False);

                      // This is the event on scrollbar.  Key, Motion,
                      // Enter/Leave dispatch as usual, Button should
                      // generate Ungrab after Java mouse event
                      if (xev.type == ButtonPress && grabbed_widget != NULL) {
                          eventInsideGrabbed(&xev);
                      }
                  }
                  else {
                      if (!haveEvent) XtAppNextEvent(awt_appContext, &xev);

                      // This is an event on one of our widgets.  Key,
                      // Motion, Enter/Leave dispatch as usual, Button
                      // should generate Ungrab after Java mouse event
/*                       if (grabbed_widget != NULL && !eventInsideGrabbed(&xev)) { */
/*                           return; */
/*                       } */

                      if (xev.type == ButtonPress) {
                          Window window = findShellByProxy(xev.xbutton.window);
                          if (window != None) {
                              XWindowAttributes winAttr;
                              memset(&winAttr, 0, sizeof(XWindowAttributes));
                              XGetWindowAttributes(awt_display, window, &winAttr);
                              if (winAttr.override_redirect == TRUE && isFocusableWindow(window)) {
                                  XSetInputFocus(awt_display, window, RevertToPointerRoot, CurrentTime);
                              }
                          }
                      }
                      if(xev.type == KeyPress) {
#ifdef DEBUG_FOCUS
                          printf("KeyPress on window %d\n", xev.xany.window);
#endif
                      }

                    /* this could be moved to shouldDispatchToWidget */
                    /* if there is a proxy in effect, dispatch key events
                       through the proxy */
                    if ((xev.type == KeyPress || xev.type == KeyRelease) &&
                        !keyboardGrabbed && !haveEvent) {
                        if (focusProxyWindow != None) {
                            Widget widget;
                            struct WidgetInfo *winfo;
                            Boolean cont;
                            /* Key event should be posted to the top-level
                               widget of the proxy */
                            xev.xany.window = proxyTopLevel(focusProxyWindow);
                            widget = XtWindowToWidget(awt_display,
                                                      xev.xany.window);
                            if (widget == NULL) return;
                            if ((winfo = findWidgetInfo(widget)) == NULL) {
                                return;
                            }
                            awt_canvas_handleEvent(widget, winfo->peer, &xev,
                                                   winfo, &cont, TRUE);
                            return;
                        }
                    }
                    if (!shouldDispatchToWidget(&xev)) {
                        XtDispatchEvent(&xev);
                    }

                    // See comment above - "after time" is here.
                    if (grabbed_widget != NULL && xev.type == ButtonPress) {
                        eventInsideGrabbed(&xev);
                    }
                }


              break;

              case FocusIn:
              case FocusOut: {
                  void *peer;
                  jobject target;

                  JNIEnv *env = (JNIEnv *) JNU_GetEnv(jvm, JNI_VERSION_1_2);

#ifdef DEBUG_FOCUS
                  if (xev.type == FocusIn) {

                      fprintf(stderr, "FocusIn on window %x, mode %d, detail %d, "
                             "send_event %d\n", xev.xfocus.window,
                             xev.xfocus.mode, xev.xfocus.detail,
                             xev.xfocus.send_event);
                  } else {
                      fprintf(stderr, "FocusOut on window %x, mode %d, detail %d, "
                             "send_event %d\n", xev.xfocus.window,
                             xev.xfocus.mode, xev.xfocus.detail,
                             xev.xfocus.send_event);
                  }
#endif
                  XtAppNextEvent(awt_appContext, &xev);

                  if (xev.xfocus.detail == NotifyVirtual ||
                      xev.xfocus.detail == NotifyNonlinearVirtual) {
#ifdef DEBUG_FOCUS
                      printf("discarding\n");
#endif
                      return;
                  }

                  // Check for xembed on this window. If it is active and this is not XEmbed focus
                  // event(send_event = 0) then we should skip it
                  if (isXEmbedActiveByWindow(xev.xfocus.window) && !xev.xfocus.send_event) {
                      return;
                  }

                  /* In general, we need to to block out focus events
                     that are caused by keybaord grabs initiated by
                     dragging the title bar or the scrollbar. But we
                     need to let through the ones that are aimed at
                     choice boxes or menus. So we keep track of when
                     the keyboard is grabbed by a popup. */

                  if (awt_isAwtMenuWidget(widget)) {
                    if (xev.type == FocusIn &&
                        xev.xfocus.mode == NotifyGrab) {
                          extern Boolean poppingDown;
                          if (!poppingDown) {
                      keyboardGrabbed = True;
                           }
                      } else /* FocusOut */ {
                          if (xev.type == FocusOut &&
                              xev.xfocus.mode == NotifyUngrab) {
                        keyboardGrabbed = False;
                      }
                    }
                  }

                  if (focusProxyWindow != None) {
#ifdef DEBUG_FOCUS
                      printf("non-null proxy; proxy = %d ", focusProxyWindow);
#endif
                      if (trueFocusWindow != None) {
                        /* trueFocusWindow should never be None here, but if
                           things ever get skewed, we want to be able to
                           recover rather than crash */
                        focusEventForProxy(xev, env, &trueFocusWindow,
                                           &focusProxyWindow);
                      return;
                      } else {
                        /* beartrap -- remove before shipping */
                        /* printf("trueFocusWindow None in processOneEvent;\n"); */
                        /* printf("Please file a bug\n"); */
                      }
                  }

                  peer = findPeer(&widget);
                  if (peer == NULL) {
#ifdef DEBUG_FOCUS
                      printf("null peer -- shouldn't see in java handler\n");
#endif
                      XtDispatchEvent(&xev);
                      return;
                  }

                  /* Find the top-level component */

                  if ((*env)->EnsureLocalCapacity(env, 1) < 0) {
                    return;
                  }
                  target = findTopLevel(peer, env);
                  if (target == NULL) {
                      JNU_ThrowNullPointerException(env, "component without a "
                                                    "window");
                      return;
                  }

                  if (isFrameOrDialog(target, env)) {
#ifdef DEBUG_FOCUS
                      printf("Focus event directed at a frame; frame = %d\n",
                             xev.xany.window);
#endif
                      focusEventForFrame(xev, focusProxyWindow);
                      (*env)->DeleteLocalRef(env, target);
                      return;
                  } else {
#ifdef DEBUG_FOCUS
                      printf("Focus event directed at a window; window = %d\n",
                             xev.xany.window);
#endif
                      focusEventForWindow(xev, env, &trueFocusWindow,
                                          &focusProxyWindow, target);
                      (*env)->DeleteLocalRef(env, target);
                      return;
                  }
              }

              case UnmapNotify:
#ifdef DEBUG_FOCUS
                printf("Unmap on window %d\n", xev.xany.window);
                printf("True focus window is %d\n", trueFocusWindow);
#endif
                clearFocusPathOnWindow(xev.xunmap.window);

              default:
                XtAppProcessEvent(awt_appContext, iMask);
                break;
              }
            }
            else {
              /* There must be a timer, alternate input, or signal event. */
              XtAppProcessEvent(awt_appContext, iMask & ~XtIMXEvent);
            }

} /* processOneEvent() */

/*
 * Waits for X/Xt events to appear on the pipe. Returns only when
 * it is likely (but not definite) that there are events waiting to
 * be processed.
 *
 * This routine also flushes the outgoing X queue, when the
 * awt_next_flush_time has been reached.
 *
 * If fdAWTPipe is greater or equal than zero the routine also
 * checks if there are events pending on the putback queue.
 */
void
waitForEvents(JNIEnv *env, int32_t fdXPipe, int32_t fdAWTPipe) {

        while ((fdAWTPipe >= 0 && awt_events_pending(awt_appContext) == 0) ||
               (fdAWTPipe <  0 && XtAppPending(awt_appContext) == 0)) {
#ifdef USE_SELECT
            performSelect(env,fdXPipe,fdAWTPipe);
#else
            performPoll(env,fdXPipe,fdAWTPipe);
#endif
            if ((awt_next_flush_time > 0) &&
                (awtJNI_TimeMillis() > awt_next_flush_time)) {
                AWT_FLUSHOUTPUT_NOW();
            }
        }  /* end while awt_events_pending() == 0 */
} /* waitForEvents() */

/*************************************************************************
 **                                                                     **
 ** WE USE EITHER select() OR poll(), DEPENDING ON THE USE_SELECT       **
 ** COMPILE-TIME CONSTANT.                                              **
 **                                                                     **
 *************************************************************************/

#ifdef USE_SELECT

static struct fd_set rdset;
struct timeval sel_time;

/*
 * Performs select() on both the X pipe and our AWT utility pipe.
 * Returns when data arrives or the operation times out.
 *
 * Not all Xt events come across the X pipe (e.g., timers
 * and alternate inputs), so we must time out every now and
 * then to check the Xt event queue.
 *
 * The fdAWTPipe will be empty when this returns.
 */
static void
performSelect(JNIEnv *env, int32_t fdXPipe, int32_t fdAWTPipe) {

            int32_t result;
            int32_t count;
            int32_t nfds = 1;
            uint32_t timeout = awt_get_poll_timeout(False);

            /* Fixed 4250354 7/28/99 ssi@sparc.spb.su
             * Cleaning up Global Refs in case of No Events
             */
            awtJNI_CleanupGlobalRefs();

            FD_ZERO( &rdset );
            FD_SET(fdXPipe, &rdset);
            if (fdAWTPipe >= 0) {
                nfds++;
                FD_SET(fdAWTPipe, &rdset);
            }
            if (timeout == 0) {
                // be sure other threads get a chance
                awtJNI_ThreadYield(env);
            }
            // set the appropriate time values. The DASSERT() in
            // MToolkit_run() makes sure that this will not overflow
            sel_time.tv_sec = (timeout * 1000) / (1000 * 1000);
            sel_time.tv_usec = (timeout * 1000) % (1000 * 1000);
            AWT_NOFLUSH_UNLOCK();
            result = select(nfds, &rdset, 0, 0, &sel_time);
            AWT_LOCK();

            /* reset tick if this was not a time out */
            if (result == 0) {
                /* select() timed out -- update timeout value */
                awt_get_poll_timeout(True);
            }
            if (fdAWTPipe >= 0 && FD_ISSET ( fdAWTPipe, &rdset ) )
            {
                /* There is data on the AWT pipe - empty it */
                do {
                    count = read(fdAWTPipe, read_buf, AWT_POLL_BUFSIZE );
                } while (count == AWT_POLL_BUFSIZE );
            }
} /* performSelect() */

#else /* !USE_SELECT */

/*
 * Polls both the X pipe and our AWT utility pipe. Returns
 * when there is data on one of the pipes, or the operation times
 * out.
 *
 * Not all Xt events come across the X pipe (e.g., timers
 * and alternate inputs), so we must time out every now and
 * then to check the Xt event queue.
 *
 * The fdAWTPipe will be empty when this returns.
 */
static void
performPoll(JNIEnv *env, int32_t fdXPipe, int32_t fdAWTPipe) {

            static struct pollfd pollFds[2];
            uint32_t timeout = awt_get_poll_timeout(False);
            int32_t result;
            int32_t count;

            /* Fixed 4250354 7/28/99 ssi@sparc.spb.su
             * Cleaning up Global Refs in case of No Events
             */
            awtJNI_CleanupGlobalRefs();

            pollFds[0].fd = fdXPipe;
            pollFds[0].events = POLLRDNORM;
            pollFds[0].revents = 0;

            pollFds[1].fd = fdAWTPipe;
            pollFds[1].events = POLLRDNORM;
            pollFds[1].revents = 0;

            AWT_NOFLUSH_UNLOCK();

            /* print the poll timeout time in brackets */
            DTRACE_PRINT1("[%dms]",(int32_t)timeout);
#ifdef DEBUG
            if (++debugPrintLineCount > 8) {
                DTRACE_PRINTLN("");
                debugPrintLineCount = 0;
            }
#endif
            /* ACTUALLY DO THE POLL() */
            if (timeout == 0) {
                // be sure other threads get a chance
                awtJNI_ThreadYield(env);
            }
            result = poll( pollFds, 2, (int32_t) timeout );

#ifdef DEBUG
            DTRACE_PRINT1("[poll()->%d]", result);
            if (++debugPrintLineCount > 8) {
                DTRACE_PRINTLN("");
                debugPrintLineCount = 0;
            }
#endif
            AWT_LOCK();
            if (result == 0) {
                /* poll() timed out -- update timeout value */
                awt_get_poll_timeout(True);
            }
            if ( pollFds[1].revents )
            {
                /* There is data on the AWT pipe - empty it */
                do {
                    count = read(AWT_READPIPE, read_buf, AWT_POLL_BUFSIZE );
                } while (count == AWT_POLL_BUFSIZE );
                DTRACE_PRINTLN1("wokeup on AWTPIPE, timeout:%d", timeout);
            }
            return;

} /* performPoll() */

#endif /* !USE_SELECT */

/*
 * Pushes an X event back on the queue to be handled
 * later.
 *
 * Ignores the request if event is NULL
 */
void
awt_put_back_event(JNIEnv *env, XEvent *event) {

    Boolean addIt = True;
    if (putbackQueueCount >= putbackQueueCapacity) {
        /* not enough room - alloc 50% more space */
        int32_t newCapacity;
        XEvent *newQueue;
        newCapacity = putbackQueueCapacity * 3 / 2;
        if ((newCapacity - putbackQueueCapacity)
                                        < PUTBACK_QUEUE_MIN_INCREMENT) {
            /* always increase by at least min increment */
            newCapacity = putbackQueueCapacity + PUTBACK_QUEUE_MIN_INCREMENT;
        }
        newQueue = (XEvent*)realloc(
                        putbackQueue, newCapacity*(sizeof(XEvent)));
        if (newQueue == NULL) {
            JNU_ThrowOutOfMemoryError(env, "OutOfMemoryError");
            addIt = False;
        } else {
            putbackQueue = newQueue;
            putbackQueueCapacity = newCapacity;
        }
    }
    if (addIt) {
        char oneChar = 'p';
        memcpy(&(putbackQueue[putbackQueueCount]), event, sizeof(XEvent));
        putbackQueueCount++;

        // wake up the event loop, if it's sleeping
        write (AWT_WRITEPIPE, &oneChar, 1);
    }

    return;
} /* awt_put_back_event() */

/*
 * Gets the next event that has been pushed back onto the queue.
 * Returns 0 and fills in xev_out if successful
 */
static int32_t
awt_get_next_put_back_event(XEvent *xev_out) {

    Boolean err = False;
    if (putbackQueueCount < 1) {
        err = True;
    } else {
        memcpy(xev_out, &(putbackQueue[0]), sizeof(XEvent));
    }
    if (!err) {
        /* remove it from the queue */
        if (putbackQueueCount == 1) {

            // queue is now empty
            if (putbackQueueCapacity > PUTBACK_QUEUE_MIN_INCREMENT) {

                /* Too much space -- delete it and rebuild later */
                free(putbackQueue);
                putbackQueue = NULL;
                putbackQueueCapacity = 0;
            }
        } else {
            /* more than 1 event in queue - shift all events to the left */
            /* We don't free the allocated memory until the queue
               becomes empty, just 'cause it's easier that way. */
            /* NOTE: use memmove(), because the memory blocks overlap */
            memmove(&(putbackQueue[0]), &(putbackQueue[1]),
                (putbackQueueCount-1)*sizeof(XEvent));
        }
        --putbackQueueCount;
    }
    DASSERT(putbackQueueCount >= 0);

    return (err? -1:0);

} /* awt_get_next_put_back_event() */

/**
 * Determines whether or not there are X or Xt events pending.
 * Looks at the putbackQueue.
 */
static XtInputMask
awt_events_pending(XtAppContext appContext) {
    XtInputMask imask = 0L;
    imask = XtAppPending(appContext);
    if (putbackQueueCount > 0) {
        imask |= XtIMXEvent;
    }
    return imask;
}


#ifndef NOMODALFIX
#define WIDGET_ARRAY_SIZE 5;
static int32_t arraySize = 0;
static int32_t arrayIndx = 0;
static Widget *dShells = NULL;

void
awt_shellPoppedUp(Widget shell,
                   XtPointer modal,
                   XtPointer call_data)
{
    if (arrayIndx == arraySize ) {
        /* if we have not allocate an array, do it first */
        if (arraySize == 0) {
            arraySize += WIDGET_ARRAY_SIZE;
            dShells = (Widget *) malloc(sizeof(Widget) * arraySize);
        } else {
            arraySize += WIDGET_ARRAY_SIZE;
            dShells = (Widget *) realloc((void *)dShells, sizeof(Widget) * arraySize);
        }
    }

    dShells[arrayIndx] = shell;
    arrayIndx++;
}

void
awt_shellPoppedDown(Widget shell,
                   XtPointer modal,
                   XtPointer call_data)
{
    arrayIndx--;

    if (dShells[arrayIndx] == shell) {
        dShells[arrayIndx] = NULL;
        return;
    } else {
        int32_t i;

        /* find the position of the shell in the array */
        for (i = arrayIndx; i >= 0; i--) {
            if (dShells[i] == shell) {
                break;
            }
        }

        /* remove the found element */
        while (i <= arrayIndx-1) {
            dShells[i] = dShells[i+1];
            i++;
        }
    }
}

Boolean
awt_isWidgetModal(Widget widget)
{
    Widget w;

    for (w = widget; !XtIsShell(w); w = XtParent(w)) { }

    while (w != NULL) {
        if (w == dShells[arrayIndx-1]) {
            return True;
        }
        w = XtParent(w);
    }
    return False;
}

Boolean
awt_isModal()
{
    return (arrayIndx > 0);
}
#endif // NOMODALFIX


/*
 * Simply waits for terminateFn() to return True. Waits on the
 * awt lock and is notified to check its state by the main event
 * loop whenever the Xt event queue is empty.
 *
 * NOTE: when you use this routine check if it can be called on the event
 * dispatch thread during drag-n-drop operation and update
 * secondary_loop_event() predicate to prevent deadlock.
 */
void
awt_MToolkit_modalWait(int32_t (*terminateFn) (void *data), void *data )
{
    JNIEnv *env = (JNIEnv *)JNU_GetEnv(jvm, JNI_VERSION_1_2);

    AWT_LOCK();
    AWT_FLUSHOUTPUT_NOW();
    while ((*terminateFn) (data) == 0) {
        AWT_WAIT(AWT_MAX_POLL_TIMEOUT);
        if ((*env)->ExceptionCheck(env)) {
            (*env)->ExceptionClear(env);
            break;
        }
    }
    AWT_NOTIFY_ALL();
    AWT_UNLOCK();
}

static uint32_t
colorToRGB(XColor * color)
{
    int32_t rgb = 0;

    rgb |= ((color->red >> 8) << 16);
    rgb |= ((color->green >> 8) << 8);
    rgb |= ((color->blue >> 8) << 0);

    return rgb;
}

/*
 * fix for bug #4088106 - ugly text boxes and grayed out looking text
 */

XmColorProc oldColorProc;

void
ColorProc(XColor* bg_color,
          XColor* fg_color,
          XColor* sel_color,
          XColor* ts_color,
          XColor* bs_color)
{
    unsigned long plane_masks[1];
    unsigned long colors[5];

    AwtGraphicsConfigDataPtr defaultConfig =
        getDefaultConfig(DefaultScreen(awt_display));

    /* use the default procedure to calculate colors */
    oldColorProc(bg_color, fg_color, sel_color, ts_color, bs_color);

    /* check if there is enought free color cells */
    if (XAllocColorCells(awt_display, defaultConfig->awt_cmap, False,
        plane_masks, 0, colors, 5)) {
        XFreeColors(awt_display, defaultConfig->awt_cmap, colors, 5, 0);
        return;
    }

    /* find the closest matches currently available */
    fg_color->pixel = defaultConfig->AwtColorMatch(fg_color->red   >> 8,
                                                   fg_color->green >> 8,
                                                   fg_color->blue  >> 8,
                                                   defaultConfig);
    fg_color->flags = DoRed | DoGreen | DoBlue;
    XQueryColor(awt_display, defaultConfig->awt_cmap, fg_color);
    sel_color->pixel = defaultConfig->AwtColorMatch(sel_color->red   >> 8,
                                                    sel_color->green >> 8,
                                                    sel_color->blue  >> 8,
                                                    defaultConfig);
    sel_color->flags = DoRed | DoGreen | DoBlue;
    XQueryColor(awt_display, defaultConfig->awt_cmap, sel_color);
    ts_color->pixel = defaultConfig->AwtColorMatch(ts_color->red   >> 8,
                                                   ts_color->green >> 8,
                                                   ts_color->blue  >> 8,
                                                   defaultConfig);
    ts_color->flags = DoRed | DoGreen | DoBlue;
    XQueryColor(awt_display, defaultConfig->awt_cmap, ts_color);
    bs_color->pixel = defaultConfig->AwtColorMatch(bs_color->red   >> 8,
                                                   bs_color->green >> 8,
                                                   bs_color->blue  >> 8,
                                                   defaultConfig);
    bs_color->flags = DoRed | DoGreen | DoBlue;
    XQueryColor(awt_display, defaultConfig->awt_cmap, bs_color);
}


/*
 * Read _XSETTINGS_SETTINGS property from _XSETTINGS selection owner
 * and pass its value to the java world for processing.
 */
/*static*/ void
awt_xsettings_update(int scr, Window owner, void *cookie)
{
    Display *dpy = awt_display;
    int status;

    JNIEnv *env;
    jobject mtoolkit;
    jmethodID upcall;
    jbyteArray array;

    struct xsettings_callback_cookie *upcall_cookie = cookie;

    /* Returns of XGetWindowProperty */
    Atom actual_type;
    int actual_format;
    unsigned long nitems;
    unsigned long bytes_after;
    unsigned char *xsettings;

    DTRACE_PRINTLN2("XS: update screen %d, owner 0x%08lx",
                    scr, owner);

#if 1 /* XXX: kludge */
    /*
     * As toolkit cannot yet cope with per-screen desktop properties,
     * only report XSETTINGS changes on the default screen.  This
     * should be "good enough" for most cases.
     */
    if (scr != DefaultScreen(dpy)) {
        DTRACE_PRINTLN2("XS: XXX: default screen is %d, update is for %d, ignoring", DefaultScreen(dpy), scr);
        return;
    }
#endif /* kludge */

    env = (JNIEnv *)JNU_GetEnv(jvm, JNI_VERSION_1_2);
    DASSERT(env != NULL);

    DASSERT(upcall_cookie != NULL);
    mtoolkit = upcall_cookie->mtoolkit;
    upcall = upcall_cookie->upcallMID;

    DASSERT(!JNU_IsNull(env, mtoolkit));
    DASSERT(upcall != NULL);

    /*
     * XXX: move awt_getPropertyFOO from awt_wm.c to awt_util.c and
     * use the appropriate one.
     */
    status = XGetWindowProperty(dpy, owner,
                 _XA_XSETTINGS_SETTINGS, 0, 0xFFFF, False,
                 _XA_XSETTINGS_SETTINGS,
                 &actual_type, &actual_format, &nitems, &bytes_after,
                 &xsettings);

    if (status != Success) {
        DTRACE_PRINTLN("XS:   unable to read _XSETTINGS");
        return;
    }

    if (xsettings == NULL) {
        DTRACE_PRINTLN("XS:   reading _XSETTINGS, got NULL");
        return;
    }

    if (actual_type != _XA_XSETTINGS_SETTINGS) {
        XFree(xsettings);       /* NULL data already catched above */
        DTRACE_PRINTLN("XS:   _XSETTINGS_SETTINGS is not of type _XSETTINGS_SETTINGS");
        return;
    }

    DTRACE_PRINTLN1("XS:   read %lu bytes of _XSETTINGS_SETTINGS",
                    nitems);

    /* ok, propagate xsettings to the toolkit for processing */
    if ((*env)->EnsureLocalCapacity(env, 1) < 0) {
        DTRACE_PRINTLN("XS:   EnsureLocalCapacity failed");
        XFree(xsettings);
        return;
    }

    array = (*env)->NewByteArray(env, (jint)nitems);
    if (JNU_IsNull(env, array)) {
        DTRACE_PRINTLN("awt_xsettings_update: NewByteArray failed");
        XFree(xsettings);
        return;
    }

    (*env)->SetByteArrayRegion(env, array, 0, (jint)nitems,
                               (jbyte *)xsettings);
    XFree(xsettings);

    (*env)->CallVoidMethod(env, mtoolkit, upcall, (jint)scr, array);
    (*env)->DeleteLocalRef(env, array);
}


/*
 * Event handler for events on XSETTINGS selection owner.
 * We are interested in PropertyNotify only.
 */
static void
awt_xsettings_callback(int scr, XEvent *xev, void *cookie)
{
    Display *dpy = awt_display; /* xev->xany.display */
    XPropertyEvent *ev;

    if (xev->type != PropertyNotify) {
        DTRACE_PRINTLN2("XS: awt_xsettings_callback(%d) event %d ignored",
                        scr, xev->type);
        return;
    }

    ev = &xev->xproperty;

    if (ev->atom == None) {
        DTRACE_PRINTLN("XS: awt_xsettings_callback(%d) atom == None");
        return;
    }

#ifdef DEBUG
    {
        char *name;

        DTRACE_PRINT2("XS: awt_xsettings_callback(%d) 0x%08lx ",
                      scr, ev->window);
        name = XGetAtomName(dpy, ev->atom);
        if (name == NULL) {
            DTRACE_PRINT1("atom #%d", ev->atom);
        } else {
            DTRACE_PRINT1("%s", name);
            XFree(name);
        }
        DTRACE_PRINTLN1(" %s", ev->state == PropertyNewValue ?
                                        "changed" : "deleted");
    }
#endif

    if (ev->atom != _XA_XSETTINGS_SETTINGS) {
        DTRACE_PRINTLN("XS:   property != _XSETTINGS_SETTINGS ...  ignoring");
        return;
    }


    if (ev->state == PropertyDelete) {
        /* XXX: notify toolkit to reset to "defaults"? */
        return;
    }

    awt_xsettings_update(scr, ev->window, cookie);
}


/*
 * Owner of XSETTINGS selection changed on the given screen.
 */
static void
awt_xsettings_owner_callback(int scr, Window owner, long *data_unused,
                             void *cookie)
{
    if (owner == None) {
        DTRACE_PRINTLN("XS: awt_xsettings_owner_callback: owner = None");
        /* XXX: reset to defaults??? */
        return;
    }

    DTRACE_PRINTLN1("XS: awt_xsettings_owner_callback: owner = 0x%08lx",
                    owner);

    awt_xsettings_update(scr, owner, cookie);
}

/*
 * Returns a reference to the class java.awt.Component.
 */
jclass
getComponentClass(JNIEnv *env)
{
    static jclass componentCls = NULL;

    // get global reference of java/awt/Component class (run only once)
    if (componentCls == NULL) {
        jclass componentClsLocal = (*env)->FindClass(env, "java/awt/Component");
        DASSERT(componentClsLocal != NULL);
        if (componentClsLocal == NULL) {
            /* exception already thrown */
            return NULL;
        }
        componentCls = (jclass)(*env)->NewGlobalRef(env, componentClsLocal);
        (*env)->DeleteLocalRef(env, componentClsLocal);
    }
    return componentCls;
}


/*
 * Returns a reference to the class java.awt.MenuComponent.
 */
jclass
getMenuComponentClass(JNIEnv *env)
{
    static jclass menuComponentCls = NULL;

    // get global reference of java/awt/MenuComponent class (run only once)
    if (menuComponentCls == NULL) {
        jclass menuComponentClsLocal = (*env)->FindClass(env, "java/awt/MenuComponent");
        DASSERT(menuComponentClsLocal != NULL);
        if (menuComponentClsLocal == NULL) {
            /* exception already thrown */
            return NULL;
        }
        menuComponentCls = (jclass)(*env)->NewGlobalRef(env, menuComponentClsLocal);
        (*env)->DeleteLocalRef(env, menuComponentClsLocal);
    }
    return menuComponentCls;
}

/*
 * Class:     sun_awt_motif_MToolkit
 * Method:    init
 * Signature: (Ljava/lang/String;)V
 */
JNIEXPORT void JNICALL
Java_sun_awt_motif_MToolkit_init(JNIEnv *env, jobject this,
    jstring mainClassName)
{
    char *appName = NULL;
    char *mainChars = NULL;

    int32_t   argc     = 0;
    char *argv[10] = { NULL };

    /*
     * Note: The MToolkit object depends on the static initializer
     * of X11GraphicsEnvironment to initialize the connection to
     * the X11 server.
     */
    XFontStruct *xfont;
    XmFontListEntry tmpFontListEntry;
    char *multiclick_time_query;
    AwtGraphicsConfigDataPtr defaultConfig =
        getDefaultConfig(DefaultScreen(awt_display));
    AwtScreenDataPtr defaultScreen =
        getScreenData(DefaultScreen(awt_display));

    static String fallback_resources[] =
    {
        "*enableThinThickness:                   True",
        "*XmFileSelectionBox.fileFilterStyle:    XmFILTER_HIDDEN_FILES",
        "*XmFileSelectionBox.pathMode:           XmPATH_MODE_RELATIVE",
        "*XmFileSelectionBox.resizePolicy:       XmRESIZE_GROW",
        "*XmFileSelectionBox*dirTextLabelString:         Enter path or folder name:",
        "*XmFileSelectionBox*applyLabelString:           Update",
        "*XmFileSelectionBox*selectionLabelString:       Enter file name:",
        "*XmFileSelectionBox*dirListLabelString:         Folders",
        NULL                        /* Must be NULL terminated */
    };

    focusOutEvent.type = FocusOut;
    focusOutEvent.xfocus.send_event = True;
    focusOutEvent.xfocus.display = awt_display;
    focusOutEvent.xfocus.mode = NotifyNormal;
    focusOutEvent.xfocus.detail = NotifyNonlinear;

    /* Need to make sure this is deleted someplace! */
    AWT_LOCK();

    XSetIOErrorHandler(xIOError);

    if (!XSupportsLocale()) {
        jio_fprintf(stderr,
                    "current locale is not supported in X11, locale is set to C");
        setlocale(LC_ALL, "C");
    }
    if (!XSetLocaleModifiers("")) {
        jio_fprintf(stderr, "X locale modifiers are not supported, using default");
    }
#ifdef NETSCAPE
    if (awt_init_xt) {
        XtToolkitInitialize();
    }
#else
    XtToolkitInitialize();
#endif

    {
        jclass  fontConfigClass;
        jmethodID methID;
        jstring jFontList;
        char       *cFontRsrc;
        char       *cFontRsrc2;

        fontConfigClass = (*env)->FindClass(env, "sun/awt/motif/MFontConfiguration");
        methID = (*env)->GetStaticMethodID(env, fontConfigClass,
                                           "getDefaultMotifFontSet",
                                           "()Ljava/lang/String;");
        jFontList = (*env)->CallStaticObjectMethod(env, fontConfigClass, methID);
        if (jFontList == NULL) {
            motifFontList =
                "-monotype-arial-regular-r-normal--*-140-*-*-p-*-iso8859-1";
        } else {
            motifFontList = JNU_GetStringPlatformChars(env, jFontList, NULL);
        }

        /* fprintf(stderr, "motifFontList: %s\n", motifFontList); */

        cFontRsrc = malloc(strlen(motifFontList) + 20);
        strcpy(cFontRsrc, "*fontList: ");
        strcat(cFontRsrc, motifFontList);
        cFontRsrc2 = malloc(strlen(motifFontList) + 20);
        strcpy(cFontRsrc2, "*labelFontList: ");
        strcat(cFontRsrc2, motifFontList);

        argc = 1;
        argv[argc++] = "-xrm";
        argv[argc++] = cFontRsrc;
        argv[argc++] = "-xrm";
        argv[argc++] = cFontRsrc2;
        argv[argc++] = "-font";
        argv[argc++] = (char *)defaultMotifFont;
    }

    awt_appContext = XtCreateApplicationContext();
    XtAppSetErrorHandler(awt_appContext, (XtErrorHandler) xtError);
    XtAppSetFallbackResources(awt_appContext, fallback_resources);

    appName = NULL;
    mainChars = NULL;
    if (!JNU_IsNull(env, mainClassName)) {
        mainChars = (char *)JNU_GetStringPlatformChars(env, mainClassName, NULL);
        appName = mainChars;
    }
    if (appName == NULL || appName[0] == '\0') {
        appName = "AWT";
    }

    XtDisplayInitialize(awt_appContext, awt_display,
                        appName, /* application name  */
                        appName, /* application class */
                        NULL, 0, &argc, argv);

    /* Root shell widget that serves as a parent for all AWT top-levels.    */
    awt_root_shell = XtVaAppCreateShell(appName, /* application name  */
                                        appName, /* application class */
                                        applicationShellWidgetClass,
                                        awt_display,
                                        /* va_list */
                                        XmNmappedWhenManaged, False,
                                        NULL);
    XtRealizeWidget(awt_root_shell);

    if (mainChars != NULL) {
        JNU_ReleaseStringPlatformChars(env, mainClassName, mainChars);
    }

    awt_mgrsel_init();
    awt_wm_init();
    init_xembed();

    /*
     * Find the correct awt_multiclick_time to use. We normally
     * would call XtMultiClickTime() and wouldn't have to do
     * anything special, but because OpenWindows defines its own
     * version (OpenWindows.MultiClickTimeout), we need to
     * determine out which resource to use.
     *
     * We do this by searching in order for:
     *
     *   1) an explicit definition of multiClickTime
     *      (this is the resource that XtGetMultiClickTime uses)
     *
     * if that fails, search for:
     *
     *   2) an explicit definition of Openwindows.MultiClickTimeout
     *
     * if both searches fail:
     *
     *   3) use the fallback provided by XtGetMultiClickTime()
     *      (which is 200 milliseconds... I looked at the source :-)
     *
     */
    multiclick_time_query = XGetDefault(awt_display, "*", "multiClickTime");
    if (multiclick_time_query) {
        awt_multiclick_time = XtGetMultiClickTime(awt_display);
    } else {
        multiclick_time_query = XGetDefault(awt_display,
                                            "OpenWindows", "MultiClickTimeout");
        if (multiclick_time_query) {
            /* Note: OpenWindows.MultiClickTimeout is in tenths of
               a second, so we need to multiply by 100 to convert to
               milliseconds */
            awt_multiclick_time = atoi(multiclick_time_query) * 100;
        } else {
            awt_multiclick_time = XtGetMultiClickTime(awt_display);
        }
    }

    /*
    scrollBugWorkAround =
        (strcmp(XServerVendor(awt_display), "Sun Microsystems, Inc.") == 0
         && XVendorRelease(awt_display) == 3400);
    */
    scrollBugWorkAround = TRUE;

    /*
     * Create the cursor for TextArea scrollbars...
     */
    awt_scrollCursor = XCreateFontCursor(awt_display, XC_left_ptr);

    awt_defaultBg = defaultConfig->AwtColorMatch(200, 200, 200, defaultConfig);
    awt_defaultFg = defaultScreen->blackpixel;
    setup_modifier_map(awt_display);

    awt_initialize_DataTransferer();
    awt_initialize_Xm_DnD(awt_display);

    /*
     * fix for bug #4088106 - ugly text boxes and grayed out looking text
     */
    oldColorProc = XmGetColorCalculation();
    XmSetColorCalculation(ColorProc);

    AWT_UNLOCK();
}

/*
 * Class:     sun_awt_motif_MToolkit
 * Method:    run
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_sun_awt_motif_MToolkit_run
  (JNIEnv *env, jobject this)
{
    /*
     * in performSelect(), we multiply the timeout by 1000. Make sure
     * that the maximum value will not cause an overflow.
     */
    DASSERT(AWT_MAX_POLL_TIMEOUT <= (ULONG_MAX/1000));

    awt_MainThread = (*env)->NewGlobalRef(env, awtJNI_GetCurrentThread(env));
    awt_MToolkit_loop(env); /* never returns */
}

/*
 * Class:     sun_awt_motif_MToolkit
 * Method:    makeColorModel
 * Signature: ()Ljava/awt/image/ColorModel;
 */
JNIEXPORT jobject JNICALL Java_sun_awt_motif_MToolkit_makeColorModel
  (JNIEnv *env, jclass this)
{
    AwtGraphicsConfigDataPtr defaultConfig =
        getDefaultConfig(DefaultScreen(awt_display));

    return awtJNI_GetColorModel(env, defaultConfig);
}

/*
 * Class:     sun_awt_motif_MToolkit
 * Method:    getScreenResolution
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_sun_awt_motif_MToolkit_getScreenResolution
  (JNIEnv *env, jobject this)
{
    return (jint) ((DisplayWidth(awt_display, DefaultScreen(awt_display))
                    * 25.4) /
                   DisplayWidthMM(awt_display, DefaultScreen(awt_display)));
}

/*
 * Class:     sun_awt_motif_MToolkit
 * Method:    getScreenWidth
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_sun_awt_motif_MToolkit_getScreenWidth
  (JNIEnv *env, jobject this)
{
    return DisplayWidth(awt_display, DefaultScreen(awt_display));
}
/*
 * Class:     sun_awt_motif_MToolkit
 * Method:    getScreenHeight
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_sun_awt_motif_MToolkit_getScreenHeight
  (JNIEnv *env, jobject this)
{
    return DisplayHeight(awt_display, DefaultScreen(awt_display));
}

/*
 * Class:     sun_awt_motif_MToolkit
 * Method:    beep
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_sun_awt_motif_MToolkit_beep
  (JNIEnv *env, jobject this)
{
    AWT_LOCK();
    XBell(awt_display, 0);
    AWT_FLUSH_UNLOCK();
}

/*
 * Class:     sun_awt_motif_MToolkit
 * Method:    shutdown
 * Signature: ()V
 */

JNIEXPORT void JNICALL Java_sun_awt_motif_MToolkit_shutdown
  (JNIEnv *env, jobject this)
{
    X11SD_LibDispose(env);
}

/*
 * Class:     sun_awt_motif_MToolkit
 * Method:    getLockingKeyStateNative
 * Signature: (I)B
 */
JNIEXPORT jboolean JNICALL Java_sun_awt_motif_MToolkit_getLockingKeyStateNative
  (JNIEnv *env, jobject this, jint awtKey)
{
    KeySym sym;
    KeyCode keyCode;
    uint32_t byteIndex;
    uint32_t bitIndex;
    char keyVector[32];

    AWT_LOCK();

    sym = awt_getX11KeySym(awtKey);
    keyCode = XKeysymToKeycode(awt_display, sym);
    if (sym == NoSymbol || keyCode == 0) {
        JNU_ThrowByName(env, "java/lang/UnsupportedOperationException", "Keyboard doesn't have requested key");
        AWT_UNLOCK();
        return False;
    }

    byteIndex = (keyCode/8);
    bitIndex = keyCode & 7;
    XQueryKeymap(awt_display, keyVector);

    AWT_UNLOCK();

    return (1 & (keyVector[byteIndex] >> bitIndex));
}

/*
 * Class:     sun_awt_motif_MToolkit
 * Method:    loadSystemColors
 * Signature: ([I)V
 */
JNIEXPORT void JNICALL Java_sun_awt_motif_MToolkit_loadSystemColors
  (JNIEnv *env, jobject this, jintArray systemColors)
{
    Widget frame, panel, control, menu, text, scrollbar;
    Colormap cmap;
    Pixel bg, fg, highlight, shadow;
    Pixel pixels[java_awt_SystemColor_NUM_COLORS];
    XColor *colorsPtr;
    jint rgbColors[java_awt_SystemColor_NUM_COLORS];
    int32_t count = 0;
    int32_t i, j;
    Arg args[10];
    int32_t argc;
    AwtGraphicsConfigDataPtr defaultConfig =
        getDefaultConfig(DefaultScreen(awt_display));

    AWT_LOCK();

    /*
     * initialize array of pixels
     */
    for (i = 0; i < java_awt_SystemColor_NUM_COLORS; i++) {
        pixels[i] = -1;
    }

    /*
     * Create phantom widgets in order to determine the default
     * colors;  this is somewhat inelegant, however it is the simplest
     * and most reliable way to determine the system's default colors
     * for objects.
     */
    argc = 0;
    XtSetArg(args[argc], XmNbuttonFontList,  getMotifFontList()); argc++;
    XtSetArg(args[argc], XmNlabelFontList,   getMotifFontList()); argc++;
    XtSetArg(args[argc], XmNtextFontList,    getMotifFontList()); argc++;
    frame = XtAppCreateShell("AWTColors", "XApplication",
                             vendorShellWidgetClass,
                             awt_display,
                             args, argc);
    /*
      XtSetMappedWhenManaged(frame, False);
      XtRealizeWidget(frame);
    */
    panel = XmCreateDrawingArea(frame, "awtPanelColor", NULL, 0);
    argc = 0;
    XtSetArg(args[argc], XmNfontList,        getMotifFontList()); argc++;
    control = XmCreatePushButton(panel, "awtControlColor", args, argc);
    argc = 0;
    XtSetArg(args[argc], XmNlabelFontList,   getMotifFontList()); argc++;
    XtSetArg(args[argc], XmNbuttonFontList,  getMotifFontList()); argc++;
    menu = XmCreatePulldownMenu(control, "awtColorMenu", args, argc);
    argc = 0;
    XtSetArg(args[argc], XmNfontList,        getMotifFontList()); argc++;
    text = XmCreateText(panel, "awtTextColor", args, argc);
    scrollbar = XmCreateScrollBar(panel, "awtScrollbarColor", NULL, 0);

    XtVaGetValues(panel,
                  XmNbackground, &bg,
                  XmNforeground, &fg,
                  XmNcolormap, &cmap,
                  NULL);

    pixels[java_awt_SystemColor_WINDOW] = bg;
    count++;
    pixels[java_awt_SystemColor_INFO] = bg;
    count++;
    pixels[java_awt_SystemColor_WINDOW_TEXT] = fg;
    count++;
    pixels[java_awt_SystemColor_INFO_TEXT] = fg;
    count++;

    XtVaGetValues(menu,
                  XmNbackground, &bg,
                  XmNforeground, &fg,
                  NULL);

    pixels[java_awt_SystemColor_MENU] = bg;
    count++;
    pixels[java_awt_SystemColor_MENU_TEXT] = fg;
    count++;

    XtVaGetValues(text,
                  XmNbackground, &bg,
                  XmNforeground, &fg,
                  NULL);

    pixels[java_awt_SystemColor_TEXT] = bg;
    count++;
    pixels[java_awt_SystemColor_TEXT_TEXT] = fg;
    count++;
    pixels[java_awt_SystemColor_TEXT_HIGHLIGHT] = fg;
    count++;
    pixels[java_awt_SystemColor_TEXT_HIGHLIGHT_TEXT] = bg;
    count++;

    XtVaGetValues(control,
                  XmNbackground, &bg,
                  XmNforeground, &fg,
                  XmNtopShadowColor, &highlight,
                  XmNbottomShadowColor, &shadow,
                  NULL);

    pixels[java_awt_SystemColor_CONTROL] = bg;
    count++;
    pixels[java_awt_SystemColor_CONTROL_TEXT] = fg;
    count++;
    pixels[java_awt_SystemColor_CONTROL_HIGHLIGHT] = highlight;
    count++;
    pixels[java_awt_SystemColor_CONTROL_LT_HIGHLIGHT] = highlight;
    count++;
    pixels[java_awt_SystemColor_CONTROL_SHADOW] = shadow;
    count++;
    pixels[java_awt_SystemColor_CONTROL_DK_SHADOW] = shadow;
    count++;

    XtVaGetValues(scrollbar,
                  XmNbackground, &bg,
                  NULL);
    pixels[java_awt_SystemColor_SCROLLBAR] = bg;
    count++;

    /*
     * Convert pixel values to RGB
     */
    colorsPtr = (XColor *) malloc(count * sizeof(XColor));
    j = 0;
    for (i = 0; i < java_awt_SystemColor_NUM_COLORS; i++) {
        if (pixels[i] != -1) {
            colorsPtr[j++].pixel = pixels[i];
        }
    }
    XQueryColors(awt_display, cmap, colorsPtr, count);

    /* Get current System Colors */

    (*env)->GetIntArrayRegion (env, systemColors, 0,
                              java_awt_SystemColor_NUM_COLORS,
                              rgbColors);

    /*
     * Fill systemColor array with new rgb values
     */

    j = 0;
    for (i = 0; i < java_awt_SystemColor_NUM_COLORS; i++) {
        if (pixels[i] != -1) {
            uint32_t rgb = colorToRGB(&colorsPtr[j++]);

            /*
              printf("SystemColor[%d] = %x\n", i, rgb);
            */
            rgbColors[i] = (rgb | 0xFF000000);
        }
    }

    (*env)->SetIntArrayRegion(env,
                              systemColors,
                              0,
                              java_awt_SystemColor_NUM_COLORS,
                              rgbColors);

    /* Duplicate system colors. If color allocation is unsuccessful,
       system colors will be approximated with matched colors */
    if (defaultConfig->awt_depth == 8)
        awt_allocate_systemcolors(colorsPtr, count, defaultConfig);

    /*
     * Cleanup
     */
    XtDestroyWidget(frame);
    free(colorsPtr);

    AWT_UNLOCK();
}

/*
 * Class:     sun_awt_motif_MToolkit
 * Method:    isDynamicLayoutSupportedNative
 * Signature: ()Z
 *
 * Note: there doesn't seem to be a protocol for querying the WM
 * about its opaque resize settings, so this function just returns
 * whether there is a solid resize option available for that WM.
 */
JNIEXPORT jboolean JNICALL
Java_sun_awt_motif_MToolkit_isDynamicLayoutSupportedNative(JNIEnv *env, jobject this)
{
    enum wmgr_t wm;

    AWT_LOCK();
    wm = awt_wm_getRunningWM();
    AWT_UNLOCK();

    switch (wm) {
      case ENLIGHTEN_WM:
      case KDE2_WM:
      case SAWFISH_WM:
      case ICE_WM:
      case METACITY_WM:
        return JNI_TRUE;
      case OPENLOOK_WM:
      case MOTIF_WM:
      case CDE_WM:
        return JNI_FALSE;
      default:
        return JNI_FALSE;
    }
}

/*
 * Class:     sun_awt_motif_MToolkit
 * Method:    isFrameStateSupported
 * Signature: (I)Z
 */
JNIEXPORT jboolean JNICALL
Java_sun_awt_motif_MToolkit_isFrameStateSupported(JNIEnv *env, jobject this,
    jint state)
{
    if (state == java_awt_Frame_NORMAL || state == java_awt_Frame_ICONIFIED) {
        return JNI_TRUE;
    } else {
        return awt_wm_supportsExtendedState(state) ? JNI_TRUE : JNI_FALSE;
    }
}

/*
 * Class:     sun_awt_motif_MToolkit
 * Method:    getMulticlickTime
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_sun_awt_motif_MToolkit_getMulticlickTime
  (JNIEnv *env, jobject this)
{
    return awt_multiclick_time;
}

/*
 * Class:     sun_awt_motif_MToolkit
 * Method:    loadXSettings
 * Signature: ()V
 */
JNIEXPORT void JNICALL
Java_sun_awt_motif_MToolkit_loadXSettings(JNIEnv *env, jobject this)
{
    static Boolean registered = False;

    jclass mtoolkitCLS;
    Display *dpy = awt_display;
    const Window *owners;
    int scr;

    AWT_LOCK();

    if (registered) {
        AWT_UNLOCK();
        return;
    }

    if (_XA_XSETTINGS_SETTINGS == None) {
        _XA_XSETTINGS_SETTINGS = XInternAtom(dpy, "_XSETTINGS_SETTINGS", False);
        if (_XA_XSETTINGS_SETTINGS == None) {
            JNU_ThrowNullPointerException(env,
                "unable to intern _XSETTINGS_SETTINGS");
            AWT_UNLOCK();
            return;
        }
    }

    mtoolkitCLS = (*env)->GetObjectClass(env, this);

    xsettings_callback_cookie.mtoolkit =
        (*env)->NewGlobalRef(env, this);
    xsettings_callback_cookie.upcallMID =
        (*env)->GetMethodID(env, mtoolkitCLS,
                            "parseXSettings", "(I[B)V");

    if (JNU_IsNull(env, xsettings_callback_cookie.upcallMID)) {
        JNU_ThrowNoSuchMethodException(env,
            "sun.awt.motif.MToolkit.parseXSettings");
        AWT_UNLOCK();
        return;
    }

    owners = awt_mgrsel_select("_XSETTINGS", PropertyChangeMask,
                               &xsettings_callback_cookie,
                               awt_xsettings_callback,
                               awt_xsettings_owner_callback);
    if (owners == NULL) {
        JNU_ThrowNullPointerException(env,
            "unable to regiser _XSETTINGS with mgrsel");
        AWT_UNLOCK();
        return;
    }

    registered = True;

    for (scr = 0; scr < ScreenCount(dpy); ++scr) {
        if (owners[scr] == None) {
            DTRACE_PRINTLN1("XS: MToolkit.loadXSettings: none on screen %d",
                            scr);
            continue;
        }

        awt_xsettings_update(scr, owners[scr], &xsettings_callback_cookie);
    }

    AWT_UNLOCK();
}

JNIEXPORT jboolean JNICALL
Java_sun_awt_motif_MToolkit_isAlwaysOnTopSupported(JNIEnv *env, jobject toolkit) {
    Boolean res;
    AWT_LOCK();
    res = awt_wm_supportsAlwaysOnTop();
    AWT_UNLOCK();
    return res;
}

/*
 * Returns true if the current thread is privileged. Currently,
 * only the main event loop thread is considered to be privileged.
 */
Boolean
awt_currentThreadIsPrivileged(JNIEnv *env) {
    return (*env)->IsSameObject(env,
                        awt_MainThread, awtJNI_GetCurrentThread(env));
}

JNIEXPORT jboolean JNICALL
Java_sun_awt_motif_MToolkit_isSyncUpdated(JNIEnv *env, jobject toolkit) {
    return syncUpdated;
}

JNIEXPORT jboolean JNICALL
Java_sun_awt_motif_MToolkit_isSyncFailed(JNIEnv *env, jobject toolkit) {
    return syncFailed;
}

JNIEXPORT void JNICALL
Java_sun_awt_motif_MToolkit_updateSyncSelection(JNIEnv *env, jobject toolkit) {

    // AWT_LOCK is held by calling function
    if (wm_selection == None) {
        wm_selection = XInternAtom(awt_display, "WM_S0", False);
    }
    if (version_atom == None) {
        version_atom = XInternAtom(awt_display, "VERSION", False);
    }
    if (oops_atom == None) {
        oops_atom = XInternAtom(awt_display, "OOPS", False);
    }
    syncUpdated = False;
    syncFailed = False;
    XConvertSelection(awt_display, wm_selection, version_atom, oops_atom, XtWindow(awt_root_shell), CurrentTime);
    XSync(awt_display, False);
    inSyncWait = True; // Protect from spurious events
    // Calling function will call AWT_LOCK_WAIT instead of AWT_UNLOCK
}

JNIEXPORT jint JNICALL
Java_sun_awt_motif_MToolkit_getEventNumber(JNIEnv *env, jobject toolkit) {
    // AWT_LOCK must be held by the calling method
    return eventNumber;
}

static void
syncWait_eventHandler(XEvent * event) {
    static jmethodID syncNotifyMID = NULL;
    if (event != NULL && event->xany.type == SelectionNotify &&
        event->xselection.requestor == XtWindow(awt_root_shell) &&
        event->xselection.property == oops_atom &&
        inSyncWait)
    {
        JNIEnv *env = (JNIEnv *)JNU_GetEnv(jvm, JNI_VERSION_1_2);
        syncUpdated = True;
        inSyncWait = False;
        AWT_NOTIFY_ALL();
    } else if (event != NULL && event->xany.type == SelectionNotify &&
               event->xselection.requestor == XtWindow(awt_root_shell) &&
               event->xselection.target == version_atom &&
               event->xselection.property == None &&
               XGetSelectionOwner(awt_display, wm_selection) == None &&
               event->xselection.selection == wm_selection)
    {
        JNIEnv *env = (JNIEnv *)JNU_GetEnv(jvm, JNI_VERSION_1_2);
        syncFailed = True;
        inSyncWait = False;
        AWT_NOTIFY_ALL();
    }
}

JNIEXPORT void JNICALL
Java_sun_awt_motif_MToolkit_nativeGrab(JNIEnv *env, jobject toolkit, jobject window) {
    struct FrameData    *wdata;
    static Cursor cursor = None;
    int grab_result;

    AWT_LOCK();

    wdata = (struct FrameData *)
        JNU_GetLongFieldAsPtr(env, window, mComponentPeerIDs.pData);

    if (wdata == NULL ||
        wdata->winData.comp.widget == NULL ||
        wdata->winData.shell == NULL)
    {
        AWT_UNLOCK();
        return;
    }
    if (None == cursor) {
        cursor = XCreateFontCursor(awt_display, XC_hand2);
    }
    grabbed_widget = wdata->winData.shell;
    grab_result = XGrabPointer(awt_display, XtWindow(wdata->winData.shell),
                               True, (ButtonPressMask | ButtonReleaseMask
                                      | EnterWindowMask | LeaveWindowMask | PointerMotionMask
                                      | ButtonMotionMask),
                               GrabModeAsync, GrabModeAsync, None,
                               cursor, CurrentTime);
    if (GrabSuccess != grab_result) {
        XUngrabPointer(awt_display, CurrentTime);
        AWT_UNLOCK();
        DTRACE_PRINTLN1("XGrabPointer() failed, result %d", grab_result);
        return;
    }
    grab_result = XGrabKeyboard(awt_display, XtWindow(wdata->winData.shell),
                                True,
                                GrabModeAsync, GrabModeAsync, CurrentTime);
    if (GrabSuccess != grab_result) {
        XUngrabKeyboard(awt_display, CurrentTime);
        XUngrabPointer(awt_display, CurrentTime);
        DTRACE_PRINTLN1("XGrabKeyboard() failed, result %d", grab_result);
    }
    AWT_UNLOCK();
}

JNIEXPORT void JNICALL
Java_sun_awt_motif_MToolkit_nativeUnGrab(JNIEnv *env, jobject toolkit, jobject window) {
    struct FrameData    *wdata;

    AWT_LOCK();

    wdata = (struct FrameData *)
        JNU_GetLongFieldAsPtr(env, window, mComponentPeerIDs.pData);

    if (wdata == NULL ||
        wdata->winData.comp.widget == NULL ||
        wdata->winData.shell == NULL)
    {
        AWT_UNLOCK();
        return;
    }

    XUngrabPointer(awt_display, CurrentTime);
    XUngrabKeyboard(awt_display, CurrentTime);
    grabbed_widget = NULL;
    AWT_FLUSHOUTPUT_NOW();

    AWT_UNLOCK();

}

/*
 * Class:     sun_awt_motif_MToolkit
 * Method:    getWMName
 * Signature: ()Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL
Java_sun_awt_motif_MToolkit_getWMName(JNIEnv *env, jclass this)
{
    enum wmgr_t wm;

    AWT_LOCK();
    wm = awt_wm_getRunningWM();
    AWT_UNLOCK();

    switch (wm) {
      case NO_WM:
          return (*env)->NewStringUTF(env, "NO_WM");
      case OTHER_WM:
          return (*env)->NewStringUTF(env, "OTHER_WM");
      case ENLIGHTEN_WM:
          return (*env)->NewStringUTF(env, "ENLIGHTEN_WM");
      case KDE2_WM:
          return (*env)->NewStringUTF(env, "KDE2_WM");
      case SAWFISH_WM:
          return (*env)->NewStringUTF(env, "SAWFISH_WM");
      case ICE_WM:
          return (*env)->NewStringUTF(env, "ICE_WM");
      case METACITY_WM:
          return (*env)->NewStringUTF(env, "METACITY_WM");
      case OPENLOOK_WM:
          return (*env)->NewStringUTF(env, "OPENLOOK_WM");
      case MOTIF_WM:
          return (*env)->NewStringUTF(env, "MOTIF_WM");
      case CDE_WM:
          return (*env)->NewStringUTF(env, "CDE_WM");
    }
    return (*env)->NewStringUTF(env, "UNDETERMINED_WM");
}


#endif /* !HEADLESS */
