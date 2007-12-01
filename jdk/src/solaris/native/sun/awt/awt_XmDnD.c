/*
 * Copyright 1997-2005 Sun Microsystems, Inc.  All Rights Reserved.
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

#include <stdio.h>
#include <string.h>

#include "jvm.h"
#include "jni.h"
#include "jni_util.h"
#include "jlong.h"

#include "awt_DataTransferer.h"
#include "awt_XmDnD.h"

#include "awt_p.h"

#include "java_awt_Cursor.h"
#include "java_awt_dnd_DnDConstants.h"
#include "java_awt_event_MouseEvent.h"
#include "sun_awt_dnd_SunDragSourceContextPeer.h"
#include "sun_awt_motif_MComponentPeer.h"
#include "sun_awt_motif_MDragSourceContextPeer.h"
#include "sun_awt_motif_MDropTargetContextPeer.h"

#include <X11/cursorfont.h>
#include <X11/Xutil.h>
/*
 * Fix for 4285634.
 * Include the private Motif header to enable access to lastEventState.
 */
#include <Xm/DragCP.h>

#include "awt_Component.h"
#include "awt_Cursor.h"
#include "awt_AWTEvent.h"

extern struct MComponentPeerIDs mComponentPeerIDs;
extern struct CursorIDs cursorIDs;
extern struct ContainerIDs containerIDs;

/* globals */


/* forwards */

static void awt_XmDropProc(Widget, XtPointer, XmDropProcCallbackStruct*);
static void awt_XmDragProc(Widget, XtPointer, XmDragProcCallbackStruct*);

static void awt_XmTransferProc(Widget, XtPointer, Atom*, Atom*, XtPointer,
                               unsigned long*, int32_t*);

/* for XmDragContext callbacks etc ... */
static void awt_XmDragEnterProc(Widget, XtPointer,
                                XmDropSiteEnterCallbackStruct*);
static void awt_XmDragMotionProc(Widget, XtPointer,
                                 XmDragMotionCallbackStruct*);
static void awt_XmDragLeaveProc(Widget, XtPointer,
                                XmDropSiteLeaveCallbackStruct*);
static void awt_XmDropOperationChangedProc(Widget, XtPointer,
                                           XmDropStartCallbackStruct*);
static void awt_XmDropFinishProc(Widget, XtPointer,
                                 XmDropFinishCallbackStruct*);

static unsigned char DnDConstantsToXm(jint operations);
static jint XmToDnDConstants(unsigned char operations);
static unsigned char selectOperation(unsigned char operations);

static void flush_cache(JNIEnv* env);
static void    cacheDropDone(Boolean dropDone);
static Boolean isDropDone();

static void setCursor(JNIEnv* env, Display* d, jobject c, jint type, Time t);

static Atom MOTIF_DROP_ATOM = None;

/* in canvas.c */
extern jint getModifiers(uint32_t state, jint button, jint keyCode);

/**
 * static cache of DropTarget related info.
 */

static struct {
    Widget        w;                /* if NULL, cache invalid */

    jobject       peer;
    jobject       component;

    jobject       dtcpeer;

    Widget        dt;

    jlongArray    targets;
    Cardinal      nTargets;

    Boolean       dropDone;
    int32_t       transfersPending;
    Widget        transfer;

    jint          dropAction;       /* used only on JVM transfers */

    Boolean       flushPending;

    Window win;
    uint32_t state;
} _cache;

uint32_t
buttonToMask(uint32_t button) {
    switch (button) {
        case Button1:
            return Button1Mask;
        case Button2:
            return Button2Mask;
        case Button3:
            return Button3Mask;
        case Button4:
            return Button4Mask;
        case Button5:
            return Button5Mask;
        default:
            return 0;
    }
}

/* Fix for 4215643: extract the values cached on drag start and send
   ButtonRelease event to the window which originated the drag */

void
dragsource_track_release(Widget w, XtPointer client_data,
                         XEvent * event, Boolean * cont)
{
    DASSERT (event != NULL);

    if (_cache.win != None &&
        (buttonToMask(event->xbutton.button) & _cache.state)) {

        JNIEnv *env = (JNIEnv *) JNU_GetEnv(jvm, JNI_VERSION_1_2);
        Window win = event->xbutton.window;
        event->xbutton.window = _cache.win;
        awt_put_back_event(env, event);
        event->xbutton.window = win;
        _cache.win = None;
        _cache.state = 0;
        XtRemoveEventHandler(w, ButtonReleaseMask, False,
                             dragsource_track_release, NULL);
    }
}

static void
cancel_drag(XtPointer client_data, XtIntervalId* id) {
    Time time = awt_util_getCurrentServerTime();
    Widget dc = XmGetDragContext(awt_root_shell, time);

    if (dc != NULL) {
        Boolean sourceIsExternal = True;
        XtVaGetValues(dc, XmNsourceIsExternal, &sourceIsExternal, NULL);
        if (!sourceIsExternal) {
            XEvent xevent;
            XmDragCancel(dc);

            /*
             * When running the internal drag-and-drop event loop
             * (see DragC.c:InitiatorMainLoop) Motif DnD uses XtAppNextEvent,
             * that processes all timer callbacks and then returns the next X
             * event from the queue. Motif DnD doesn't check if the drag
             * operation is cancelled after XtAppNextEvent returns and processes
             * the returned event. When the drag operation is cancelled the
             * XmDragContext widget is destroyed and Motif will crash if the new
             * event is dispatched to the destroyed XmDragContext.
             * We cancel the drag operation in the timer callback, so we putback
             * a dummy X event. This event will be returned from XtAppNextEvent
             * and Motif DnD will safely exit from the internal event loop.
             */
            xevent.type = LASTEvent;
            xevent.xany.send_event = True;
            xevent.xany.display = awt_display;
            xevent.xany.window = XtWindow(awt_root_shell);
            XPutBackEvent(awt_display, &xevent);
        }
    }
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
 * Fix for 4484572.
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

/**
 * global function to initialize this client as a Dynamic-only app.
 *
 * gets called once during toolkit initialization.
 */

void awt_initialize_Xm_DnD(Display* dpy) {
    JNIEnv* env = (JNIEnv *)JNU_GetEnv(jvm, JNI_VERSION_1_2);
    jclass  clazz;

    XtVaSetValues(XmGetXmDisplay(dpy),
                  XmNdragInitiatorProtocolStyle, XmDRAG_DYNAMIC,
                  XmNdragReceiverProtocolStyle,  XmDRAG_DYNAMIC,
                  NULL
                  );

    MOTIF_DROP_ATOM = XInternAtom(dpy, _XA_MOTIF_DROP, False);
    if (XSaveContext(dpy, MOTIF_DROP_ATOM, awt_convertDataContext,
                     (XPointer)NULL) == XCNOMEM) {
        JNU_ThrowInternalError(env, "");
        return;
    }

    /* No drop in progress. */
    cacheDropDone(True);

    /*
     * Fix for BugTraq ID 4407057.
     * Have to disable Motif default drag support, since it doesn't work
     * reliably with our event dispatch mechanism. To do this we allow a drag
     * operation only if it is registered on the awt_root_shell.
     */
    awt_motif_enableSingleDragInitiator(awt_root_shell);

    awt_set_ShellInitialize();

    /*
     * load the Cursor stuff
     */

    clazz = (*env)->FindClass(env, "sun/awt/motif/MCustomCursor");

    if (!JNU_IsNull(env, ((*env)->ExceptionOccurred(env)))) {
        (*env)->ExceptionDescribe(env);
        (*env)->ExceptionClear(env);
    }
}

typedef struct DSInfoRec {
    Widget         widget;

    Pixmap         animation_mask;
    Pixmap         animation_pixmap;
    int32_t        animation_pixmap_depth;
    unsigned char  animation_style;
    XtPointer      client_data;
    XtCallbackProc drag_proc;
    XtCallbackProc drop_proc;
    XRectangle     *drop_rectangles;
    unsigned char  drop_site_activity;
    unsigned char  drop_site_operations;
    unsigned char  drop_site_type;
    Atom           *import_targets;
    Cardinal       num_drop_rectangles;
    Cardinal       num_import_targets;

    struct DSInfoRec* next;
} DSInfoRec, * DSInfoPtr;

#define ARG_COUNT 14

/*
 * Allocates DSInfoRect structure, retrieves all attributes of a Motif drop site
 * registered on the specified widget and puts them into the allocated storage.
 * The caller should free the storage after use.
 */
DSInfoPtr get_drop_site_info(Widget w) {
    Arg       arglist[ARG_COUNT];
    Cardinal  argcount = 0;
    DSInfoPtr info = ZALLOC(DSInfoRec);

    if (info == NULL) {
        JNIEnv* env = (JNIEnv *)JNU_GetEnv(jvm, JNI_VERSION_1_2);
        JNU_ThrowOutOfMemoryError(env, "OutOfMemoryError");
        return NULL;
    }

    XtSetArg(arglist[argcount], XmNanimationMask,
             (XtArgVal)&info->animation_mask); argcount++;
    XtSetArg(arglist[argcount], XmNanimationPixmap,
             (XtArgVal)&info->animation_pixmap); argcount++;
    XtSetArg(arglist[argcount], XmNanimationPixmapDepth,
             (XtArgVal)&info->animation_pixmap_depth); argcount++;
    XtSetArg(arglist[argcount], XmNanimationStyle,
             (XtArgVal)&info->animation_style); argcount++;
    XtSetArg(arglist[argcount], XmNclientData,
             (XtArgVal)&info->client_data); argcount++;
    XtSetArg(arglist[argcount], XmNdragProc,
             (XtArgVal)&info->drag_proc); argcount++;
    XtSetArg(arglist[argcount], XmNdropProc,
             (XtArgVal)&info->drop_proc); argcount++;
    XtSetArg(arglist[argcount], XmNdropSiteActivity,
             (XtArgVal)&info->drop_site_activity); argcount++;
    XtSetArg(arglist[argcount], XmNdropSiteOperations,
             (XtArgVal)&info->drop_site_operations); argcount++;
    XtSetArg(arglist[argcount], XmNdropSiteType,
             (XtArgVal)&info->drop_site_type); argcount++;
    XtSetArg(arglist[argcount], XmNnumDropRectangles,
             (XtArgVal)&info->num_drop_rectangles); argcount++;
    XtSetArg(arglist[argcount], XmNnumImportTargets,
             (XtArgVal)&info->num_import_targets); argcount++;
    DASSERT(argcount == ARG_COUNT - 2);

    XmDropSiteRetrieve(w, arglist, argcount);

    if (info->num_import_targets > 0) {
        Atom *targets = NULL;

        info->import_targets = malloc(info->num_import_targets * sizeof(Atom));

        if (info->import_targets == NULL) {
            JNIEnv* env = (JNIEnv *)JNU_GetEnv(jvm, JNI_VERSION_1_2);

            free(info);
            JNU_ThrowOutOfMemoryError(env, "OutOfMemoryError");
            return NULL;
        }

        XtSetArg(arglist[0], XmNimportTargets, (XtArgVal)&targets);
        XmDropSiteRetrieve(w, arglist, 1);

        memcpy(info->import_targets, targets,
               info->num_import_targets * sizeof(Atom));
    }

    if (info->drop_site_type == XmDROP_SITE_SIMPLE && info->num_drop_rectangles > 0) {
            XRectangle *rectangles = NULL;
            info->drop_rectangles =
                malloc(info->num_drop_rectangles * sizeof(XRectangle));

            if (info->drop_rectangles == NULL) {
                JNIEnv* env = (JNIEnv *)JNU_GetEnv(jvm, JNI_VERSION_1_2);

                if (info->import_targets != NULL) {
                    free(info->import_targets);
                }
                free(info);
                JNU_ThrowOutOfMemoryError(env, "OutOfMemoryError");
                return NULL;
            }

            XtSetArg(arglist[0], XmNdropRectangles, (XtArgVal)&rectangles);
            XmDropSiteRetrieve(w, arglist, 1);

            memcpy(info->drop_rectangles, rectangles,
                   info->num_drop_rectangles * sizeof(XRectangle));
    } else /* if (info->drop_site_type == XmDROP_SITE_COMPOSITE) */ {
        info->num_drop_rectangles = 1;
        info->drop_rectangles = NULL;
    }

    info->widget = w;
    return info;
}

/*
 * Registers a Motif drop site on a widget given the information
 * in the passed DSInfoRec structure.
 */
void restore_drop_site(DSInfoPtr info) {
    Arg      arglist[ARG_COUNT];
    Cardinal argcount = 0;

    if (info->drop_site_type == XmDROP_SITE_COMPOSITE) {
        info->num_drop_rectangles = 1;
        info->drop_rectangles = NULL;
    }

    XtSetArg(arglist[argcount], XmNanimationMask,
             (XtArgVal)info->animation_mask); argcount++;
    XtSetArg(arglist[argcount], XmNanimationPixmap,
             (XtArgVal)info->animation_pixmap); argcount++;
    XtSetArg(arglist[argcount], XmNanimationPixmapDepth,
             (XtArgVal)info->animation_pixmap_depth); argcount++;
    XtSetArg(arglist[argcount], XmNanimationStyle,
             (XtArgVal)info->animation_style); argcount++;
    XtSetArg(arglist[argcount], XmNclientData,
             (XtArgVal)info->client_data); argcount++;
    XtSetArg(arglist[argcount], XmNdragProc,
             (XtArgVal)info->drag_proc); argcount++;
    XtSetArg(arglist[argcount], XmNdropProc,
             (XtArgVal)info->drop_proc); argcount++;
    XtSetArg(arglist[argcount], XmNdropRectangles,
             (XtArgVal)info->drop_rectangles); argcount++;
    XtSetArg(arglist[argcount], XmNdropSiteActivity,
             (XtArgVal)info->drop_site_activity); argcount++;
    XtSetArg(arglist[argcount], XmNdropSiteOperations,
             (XtArgVal)info->drop_site_operations); argcount++;
    XtSetArg(arglist[argcount], XmNdropSiteType,
             (XtArgVal)info->drop_site_type); argcount++;
    XtSetArg(arglist[argcount], XmNimportTargets,
             (XtArgVal)info->import_targets); argcount++;
    XtSetArg(arglist[argcount], XmNnumDropRectangles,
             (XtArgVal)info->num_drop_rectangles); argcount++;
    XtSetArg(arglist[argcount], XmNnumImportTargets,
             (XtArgVal)info->num_import_targets); argcount++;
    DASSERT(argcount == ARG_COUNT);

    XmDropSiteUnregister(info->widget);
    XmDropSiteRegister(info->widget, arglist, argcount);
    XmDropSiteConfigureStackingOrder(info->widget, (Widget)NULL, XmABOVE);
}

#undef ARG_COUNT

/*
 * This routine ensures that hierarchy of Motif drop sites is not broken
 * when a new drop site is registered or an existing drop site is
 * unregistered. It unregisters all drop sites registered on the descendants of
 * the specified widget, then registers or unregisters a Motif drop site on the
 * root widget depending on the value of registerNewSite. After that the routine
 * restores all the drop sites on the descendants.
 * The routine recursively traverses through the hierarchy of descendant Motif
 * drop sites and stores the info for all drop sites in a list. Then this list
 * is used to restore all descendant drop sites.
 * @param w    current widget in the hierarchy traversal
 * @param top  root widget of the traversed hierarchy - the one to be inserted or
 *             removed
 * @param list a list of DSInfoRec structures which keep drop site info for
 *             child drop sites
 * @param registerNewSite if True a new Motif drop site should be registered on
 *             the root widget. If False an existing drop site of the root widget
 *             should be unregistered.
 * @param isDropSite if True the widget being currently traversed has an
 *             associated Motif drop site.
 */
static DSInfoPtr
update_drop_site_hierarchy(Widget w, Widget top, DSInfoPtr list,
                           Boolean registerNewSite, Boolean isDropSite) {

    Widget     parent = NULL;
    Widget     *children = NULL;
    Cardinal   num_children = 0;

    if (w == NULL || !XtIsObject(w) || w->core.being_destroyed) {
        return NULL;
    }

    /* Get the child drop sites of the widget.*/
    if (XmDropSiteQueryStackingOrder(w, &parent, &children,
                                     &num_children) == 0) {
        /*
         * The widget is declared to be a drop site, but the query fails.
         * The drop site must be corrupted. Truncate traversal.
         */
        if (isDropSite) {
            return NULL;
        }
    } else {
        /* The query succeded, so the widget is definitely a drop site. */
        isDropSite = True;
    }

    /* Traverse descendants of the widget, if it is composite. */
    if (XtIsComposite(w)) {
        Cardinal   i = 0;

        /* If it is not a drop site, check all its children. */
        if (!isDropSite) {
            XtVaGetValues(w, XmNchildren, &children,
                          XmNnumChildren, &num_children, NULL);
        }

        for (i = 0; i < num_children; i++) {
            list = update_drop_site_hierarchy(children[i], top, list,
                                              registerNewSite, isDropSite);
        }
    }

    /* The storage allocated by XmDropSiteQueryStackingOrder must be freed.*/
    if (isDropSite && children != NULL) {
        XtFree((void*)children);
    }

    if (w != top) {
        if (isDropSite) {
            /* Prepend drop site info to the list and unregister a drop site.*/
            DSInfoPtr info = get_drop_site_info(w);

            if (info != NULL) {
                info->next = list;
                list = info;
            }
            XmDropSiteUnregister(w);
        }
    } else {
        /* Traversal is complete.*/
        DSInfoPtr info = list;

        if (isDropSite) {
            XmDropSiteUnregister(w);
        }

        if (registerNewSite) {
            Arg              args[10];
            unsigned int nargs = 0;

#define SetArg(n, v) args[nargs].name = n; args[nargs++].value = (XtArgVal)(v);

            SetArg(XmNanimationStyle,   XmDRAG_UNDER_NONE);
            SetArg(XmNdragProc,                awt_XmDragProc);
            SetArg(XmNdropProc,                awt_XmDropProc);
            SetArg(XmNdropSiteActivity, XmDROP_SITE_ACTIVE);

            SetArg(XmNdropSiteOperations,
                   XmDROP_LINK | XmDROP_MOVE | XmDROP_COPY);

            SetArg(XmNimportTargets,    NULL);
            SetArg(XmNnumImportTargets, 0);

            SetArg(XmNdropSiteType,     XmDROP_SITE_COMPOSITE);
            SetArg(XmNdropRectangles,   (XRectangle*)NULL);
#undef  SetArg

            XmDropSiteRegister(w, args, nargs);
            XmDropSiteConfigureStackingOrder(w, (Widget)NULL, XmABOVE);
        }

        /* Go through the list and restore all child drop sites.*/
        while (info != NULL) {
            restore_drop_site(info);

            info = info->next;
            list->next = NULL;
            if (list->import_targets != NULL) {
                free(list->import_targets);
            }
            if (list->drop_rectangles != NULL) {
                free(list->drop_rectangles);
            }
            free(list);
            list = info;
        }
    }
    return list;
}

void
register_drop_site(Widget w) {
    update_drop_site_hierarchy(w, w, NULL, True, False);
}

void
unregister_drop_site(Widget w) {
    update_drop_site_hierarchy(w, w, NULL, False, True);
}

DECLARE_JAVA_CLASS(dSCClazz, "sun/awt/motif/MDragSourceContextPeer")
DECLARE_JAVA_CLASS(dTCClazz, "sun/awt/motif/MDropTargetContextPeer")

static void
call_dSCenter(JNIEnv* env, jobject this, jint targetActions,
              jint modifiers, jint x, jint y) {
    DECLARE_VOID_JAVA_METHOD(dSCenter, dSCClazz, "dragEnter", "(IIII)V");
    DASSERT(!JNU_IsNull(env, this));
    (*env)->CallVoidMethod(env, this, dSCenter, targetActions, modifiers, x, y);
}

static void
call_dSCmotion(JNIEnv* env, jobject this, jint targetActions,
               jint modifiers, jint x, jint y) {
    DECLARE_VOID_JAVA_METHOD(dSCmotion, dSCClazz, "dragMotion", "(IIII)V");
    DASSERT(!JNU_IsNull(env, this));
    (*env)->CallVoidMethod(env, this, dSCmotion, targetActions,
                           modifiers, x, y);
}

static void
call_dSCchanged(JNIEnv* env, jobject this, jint targetActions,
                jint modifiers, jint x, jint y) {
    DECLARE_VOID_JAVA_METHOD(dSCchanged, dSCClazz, "operationChanged",
                             "(IIII)V");
    DASSERT(!JNU_IsNull(env, this));
    (*env)->CallVoidMethod(env, this, dSCchanged, targetActions,
                           modifiers, x, y);
}

static void
call_dSCmouseMoved(JNIEnv* env, jobject this, jint targetActions,
                   jint modifiers, jint x, jint y) {
    DECLARE_VOID_JAVA_METHOD(dSCmouseMoved, dSCClazz, "dragMouseMoved",
                             "(IIII)V");
    DASSERT(!JNU_IsNull(env, this));
    (*env)->CallVoidMethod(env, this, dSCmouseMoved, targetActions,
                           modifiers, x, y);
}

static void
call_dSCexit(JNIEnv* env, jobject this, jint x, jint y) {
    DECLARE_VOID_JAVA_METHOD(dSCexit, dSCClazz, "dragExit", "(II)V");
    DASSERT(!JNU_IsNull(env, this));
    (*env)->CallVoidMethod(env, this, dSCexit, x, y);
}

static void
call_dSCddfinished(JNIEnv* env, jobject this, jboolean success,
                   jint operations, jint x, jint y) {
    DECLARE_VOID_JAVA_METHOD(dSCddfinished, dSCClazz, "dragDropFinished",
                             "(ZIII)V");
    DASSERT(!JNU_IsNull(env, this));
    (*env)->CallVoidMethod(env, this, dSCddfinished, success, operations, x, y);
}

static jobject
call_dTCcreate(JNIEnv* env) {
    DECLARE_STATIC_OBJECT_JAVA_METHOD(dTCcreate, dTCClazz,
                                     "createMDropTargetContextPeer",
                                     "()Lsun/awt/motif/MDropTargetContextPeer;");
    return (*env)->CallStaticObjectMethod(env, clazz, dTCcreate);
}

static jint
call_dTCenter(JNIEnv* env, jobject this, jobject component, jint x, jint y,
              jint dropAction, jint actions, jlongArray formats,
              jlong nativeCtxt) {
    DECLARE_JINT_JAVA_METHOD(dTCenter, dTCClazz, "handleEnterMessage",
                            "(Ljava/awt/Component;IIII[JJ)I");
    DASSERT(!JNU_IsNull(env, this));
    return (*env)->CallIntMethod(env, this, dTCenter, component, x, y, dropAction,
                                 actions, formats, nativeCtxt);
}

static void
call_dTCexit(JNIEnv* env, jobject this, jobject component, jlong nativeCtxt) {
    DECLARE_VOID_JAVA_METHOD(dTCexit, dTCClazz, "handleExitMessage",
                            "(Ljava/awt/Component;J)V");
    DASSERT(!JNU_IsNull(env, this));
    (*env)->CallVoidMethod(env, this, dTCexit, component, nativeCtxt);
}

static jint
call_dTCmotion(JNIEnv* env, jobject this, jobject component, jint x, jint y,
               jint dropAction, jint actions, jlongArray formats,
               jlong nativeCtxt) {
    DECLARE_JINT_JAVA_METHOD(dTCmotion, dTCClazz, "handleMotionMessage",
                            "(Ljava/awt/Component;IIII[JJ)I");
    DASSERT(!JNU_IsNull(env, this));
    return (*env)->CallIntMethod(env, this, dTCmotion, component, x, y,
                                 dropAction, actions, formats, nativeCtxt);
}

static void
call_dTCdrop(JNIEnv* env, jobject this, jobject component, jint x, jint y,
             jint dropAction, jint actions, jlongArray formats,
             jlong nativeCtxt) {
    DECLARE_VOID_JAVA_METHOD(dTCdrop, dTCClazz, "handleDropMessage",
                            "(Ljava/awt/Component;IIII[JJ)V");
    DASSERT(!JNU_IsNull(env, this));
    (*env)->CallVoidMethod(env, this, dTCdrop, component, x, y,
                           dropAction, actions, formats, nativeCtxt);
}

static void
call_dTCnewData(JNIEnv* env, jobject this, jlong format, jobject type,
                jbyteArray data) {
    DECLARE_VOID_JAVA_METHOD(dTCnewData, dTCClazz, "newData",
                            "(JLjava/lang/String;[B)V");
    DASSERT(!JNU_IsNull(env, this));
    (*env)->CallVoidMethod(env, this, dTCnewData, format, type, data);
}

static void
call_dTCtxFailed(JNIEnv* env, jobject this, jlong format) {
    DECLARE_VOID_JAVA_METHOD(dTCtxFailed, dTCClazz, "transferFailed", "(J)V");
    DASSERT(!JNU_IsNull(env, this));
    (*env)->CallVoidMethod(env, this, dTCtxFailed, format);
}

/*
 * Class:     sun_awt_motif_MComponentPeer
 * Method:    addNativeDropTarget
 * Signature: (Ljava/awt/dnd/DropTarget;)V
 */

JNIEXPORT void JNICALL Java_sun_awt_motif_MComponentPeer_addNativeDropTarget
    (JNIEnv *env, jobject this, jobject droptarget)
{
    struct ComponentData* cdata     = (struct ComponentData *)NULL;
    DropSitePtr          dropsite  = (DropSitePtr)NULL;

    if (JNU_IsNull(env, droptarget)) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        return;
    }

    AWT_LOCK();

    cdata = (struct ComponentData *)
        JNU_GetLongFieldAsPtr(env, this, mComponentPeerIDs.pData);

    if (cdata == NULL || cdata->widget == NULL) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        AWT_UNLOCK();
        return;
    }

    /* introduce a new Component as a root of a set of DropTargets */

    if ((dropsite = cdata->dsi) == (DropSitePtr)NULL) {
        dropsite = cdata->dsi = (DropSitePtr)ZALLOC(DropSiteInfo);

        if (dropsite == (DropSitePtr)NULL) {
            JNU_ThrowOutOfMemoryError (env, "OutOfMemoryError");
            AWT_UNLOCK ();
            return;
        }

        dropsite->component = (*env)->NewGlobalRef
            (env, (*env)->GetObjectField(env, this,
                                         mComponentPeerIDs.target));
        dropsite->isComposite = True;

        /*
         * Fix for Bug Id 4389284.
         * Revalidate drop site hierarchy so that this drop site doesn't obscure
         * drop sites that are already registered on its children.
         */
        register_drop_site(cdata->widget);
    }

    dropsite->dsCnt++;

    AWT_UNLOCK();
}

/*
 * Class:     sun_awt_motif_MComponentPeer
 * Method:    removeNativeDropTarget
 * Signature: (Ljava/awt/dnd/DropTarget;)V
 */

JNIEXPORT void JNICALL Java_sun_awt_motif_MComponentPeer_removeNativeDropTarget
    (JNIEnv *env, jobject this, jobject droptarget)
{
    struct ComponentData* cdata;
    DropSitePtr           dropsite;

    if (JNU_IsNull(env, droptarget)) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        return;
    }

    AWT_LOCK();

    cdata = (struct ComponentData *)
        JNU_GetLongFieldAsPtr(env, this, mComponentPeerIDs.pData);

    if (cdata == NULL || cdata->widget == NULL) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        AWT_UNLOCK();
        return;
    }

    if ((dropsite = cdata->dsi) == (DropSitePtr)NULL) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        AWT_UNLOCK();
        return;
    }

    dropsite->dsCnt--;
    if (dropsite->dsCnt == 0) {
        /*
         * Fix for Bug Id 4411368.
         * Revalidate drop site hierarchy to prevent crash when a composite drop
         * site is unregistered before its child drop sites.
         */
        unregister_drop_site(cdata->widget);

        (*env)->DeleteGlobalRef(env, dropsite->component);

        free((void *)(cdata->dsi));
        cdata->dsi = (DropSitePtr)NULL;
    }

    AWT_UNLOCK();
}

/**
 *
 */

JNIEXPORT void JNICALL
Java_sun_awt_motif_MDragSourceContextPeer_setNativeCursor(JNIEnv *env,
                                                          jobject this,
                                                          jlong nativeCtxt,
                                                          jobject cursor,
                                                          jint type) {
    /*
     * NOTE: no need to synchronize on awt_lock here, since we should have
     * already acquired it in MDragSourceContextPeer.setCursor().
     */
    setCursor(env, awt_display, cursor, type, CurrentTime);
}

/**
 *
 */

JNIEXPORT jlong JNICALL
Java_sun_awt_motif_MDropTargetContextPeer_startTransfer(JNIEnv *env,
                                                        jobject this,
                                                        jlong dragContextVal,
                                                        jlong atom) {
    XmDropTransferEntryRec trec;
    Widget                 dropTransfer;
    Arg                    args[3];
    Cardinal               nargs = 0;
    jboolean               isCopy;
    Widget                 dragContext = (Widget)jlong_to_ptr(dragContextVal);

    AWT_LOCK();

    trec.target      = (Atom) atom;
    trec.client_data = (XtPointer)trec.target;


#define SetArg(n, v) args[nargs].name = n; args[nargs++].value = (XtArgVal)(v);

    SetArg(XmNdropTransfers,    &trec);
    SetArg(XmNnumDropTransfers, 1    );
    SetArg(XmNtransferProc,     awt_XmTransferProc);

#undef SetArg

    _cache.transfer = dropTransfer =
        XmDropTransferStart(dragContext, args, nargs);

    _cache.transfersPending++;

    AWT_NOTIFY_ALL();
    AWT_UNLOCK();

    return ptr_to_jlong(dropTransfer);
}

/**
 *
 */

JNIEXPORT void JNICALL
Java_sun_awt_motif_MDropTargetContextPeer_addTransfer(JNIEnv *env,
                                                      jobject this,
                                                      jlong dropTransferVal,
                                                      jlong atom) {
    XmDropTransferEntryRec trec;
    jboolean               isCopy;
    Widget                 dropTransfer=(Widget)jlong_to_ptr(dropTransferVal);
    trec.target      = (Atom)atom;
    trec.client_data = (XtPointer)trec.target;

    AWT_LOCK();

    XmDropTransferAdd(dropTransfer, &trec, 1);

    _cache.transfersPending++;

    AWT_NOTIFY_ALL();
    AWT_UNLOCK();
}

/**
 *
 */

JNIEXPORT void JNICALL Java_sun_awt_motif_MDropTargetContextPeer_dropDone
    (JNIEnv *env, jobject this, jlong dragContextVal, jlong dropTransferVal,
     jboolean isLocal, jboolean success, jint dropAction)
{
    Widget dropTransfer = (Widget)jlong_to_ptr(dropTransferVal);
    Widget dragContext = (Widget)jlong_to_ptr(dragContextVal);

    AWT_LOCK();

    if (_cache.w == (Widget)NULL) {
        AWT_UNLOCK();
        return;
    }

    if (!isDropDone()) {
        if (dropTransfer != (jlong)NULL) {
            XtVaSetValues(dropTransfer,
                          XmNtransferStatus,
                          success == JNI_TRUE
                          ? XmTRANSFER_SUCCESS : XmTRANSFER_FAILURE,
                          NULL
                          );
        } else {
            /*
             * start a transfer that notifies failure
             * this causes src side callbacks to be processed.
             * However, you cannot pass an a success, so the workaround is
             * to set _cache.transferSuccess to the proper value and read it
             * on the other side.
             */


            Arg arg;

            /*
             * this is the workaround code
             */
            _cache.transfer = NULL;
            _cache.dropAction = dropAction;

            /*
             * End workaround code
             */

            arg.name  = XmNtransferStatus;
            arg.value = (XtArgVal)(success == JNI_TRUE ? XmTRANSFER_SUCCESS
                                   : XmTRANSFER_FAILURE
                                   );

            XmDropTransferStart(dragContext, &arg, 1);
        }

        /*
         * bugid# 4146717
         *
         * If this is a local tx, then we never exec the awt_XmTransferProc,
         * thus we need to flush the cache here as it is our only chance,
         * otherwise we leave a mess for the next operation to fail on ....
         *
         */

        if (isLocal == JNI_TRUE)
            flush_cache(env); /* flush now, last chance */
        else
            _cache.flushPending = True; /* flush pending in transfer proc */
    }

    cacheDropDone(True);

    AWT_NOTIFY_ALL();
    AWT_UNLOCK();
}


static Boolean exitIdleProc = False;
static int32_t x_root = -1, y_root = -1;

extern void waitForEvents(JNIEnv *env, int32_t fdXPipe, int32_t fdAWTPipe);

static jint convertModifiers(uint32_t modifiers) {
    return getModifiers(modifiers, 0, 0);
}

static void
checkMouseMoved(XtPointer client_data) {
    Window rootWindow, childWindow;
    int32_t xw, yw, xr, yr;
    uint32_t modifiers;

    /*
     * When dragging over the root window XmNdragMotionCallback is not called
     * (Motif feature).
     * Since there is no legal way to receive MotionNotify events during drag
     * we have to query for mouse position periodically.
     */
    if (XQueryPointer(awt_display, XDefaultRootWindow(awt_display),
                      &rootWindow, &childWindow,
                      &xr, &yr, &xw, &yw, &modifiers) &&
        childWindow == None && (xr != x_root || yr != y_root)) {

        JNIEnv *env = (JNIEnv *)JNU_GetEnv(jvm, JNI_VERSION_1_2);
        jobject this = (jobject)client_data;

        call_dSCmouseMoved(env, this, XmDROP_NOOP, convertModifiers(modifiers),
                           xr, yr);

        if ((*env)->ExceptionCheck(env) == JNI_TRUE) {
            (*env)->ExceptionDescribe(env);
            (*env)->ExceptionClear(env);
        }

        x_root = xr;
        y_root = yr;
    }
}

static void IdleProc(XtPointer client_data, XtIntervalId* id) {
    if (!exitIdleProc) {
        JNIEnv *env = (JNIEnv *)JNU_GetEnv(jvm, JNI_VERSION_1_2);
        /* The pipe where X events arrive */
        int32_t fdXPipe = ConnectionNumber(awt_display) ;

        /*
         * Motif DnD internal event loop doesn't process the events
         * from the AWT putback event queue. So we pass -1 instead
         * of the AWT read pipe descriptor to disable checking of
         * the putback event queue.
         */
        waitForEvents(env, fdXPipe, -1);

        checkMouseMoved(client_data);
        /* Reschedule the timer callback */
        XtAppAddTimeOut(awt_appContext, AWT_DND_POLL_INTERVAL / 10,
                        IdleProc, client_data);
    }
}

static void RemoveIdleProc(Widget w,
                           XtPointer client_data,
                           XmDropFinishCallbackStruct* cbstruct) {
    exitIdleProc = True;
}

/**
 *
 */

JNIEXPORT jlong JNICALL
Java_sun_awt_motif_MDragSourceContextPeer_startDrag(JNIEnv *env,
                                                    jobject this,
                                                    jobject component,
                                                    jobject transferable,
                                                    jobject trigger,
                                                    jobject cursor,
                                                    jint ctype,
                                                    jint actions,
                                                    jlongArray formats,
                                                    jobject formatMap) {
    Arg                    args[32];
    Cardinal               nargs = 0;
    jobject                dscp  = (*env)->NewGlobalRef(env, this);
    jbyteArray             bdata =
        (jbyteArray)(*env)->GetObjectField(env, trigger, awtEventIDs.bdata);
    Atom*                  targets = NULL;
    jlong*                 jTargets;
    jsize                  nTargets;
    Widget                 dc;
    XtCallbackRec          dsecbr[2];
    XtCallbackRec          dmcbr[2];
    XtCallbackRec          occbr[2];
    XtCallbackRec          dslcbr[2];
    XtCallbackRec          dscbr[2];
    XtCallbackRec          ddfcbr[2];
    XEvent*                xevent;
    unsigned char          xmActions = DnDConstantsToXm(actions);
    jboolean               isCopy=JNI_TRUE;
    awt_convertDataCallbackStruct* structPtr;

#ifndef _LP64 /* Atom and jlong are different sizes in the 32-bit build */
    jsize                  i;
    jlong*                 saveJTargets;
    Atom*                  saveTargets;
#endif

    if (xmActions == XmDROP_NOOP) {
        JNU_ThrowByName(env, "java/awt/dnd/InvalidDnDOperationException",
                        "Invalid source actions.");
        return ptr_to_jlong(NULL);
    }

    if (JNU_IsNull(env, formats)) {
        JNU_ThrowNullPointerException(env, "formats");
        return ptr_to_jlong(NULL);
    }

    if (JNU_IsNull(env, bdata)) {
        JNU_ThrowNullPointerException(env,
                                      "null native data for trigger event");
        return ptr_to_jlong(NULL);
    }

    nTargets = (*env)->GetArrayLength(env, formats);

    /*
     * In debug build GetLongArrayElements aborts with assertion on an empty
     * array.
     */
    if (nTargets > 0) {
        jTargets = (*env)->GetLongArrayElements(env, formats, &isCopy);
        if (!JNU_IsNull(env, ((*env)->ExceptionOccurred(env)))) {
            (*env)->ExceptionDescribe(env);
            (*env)->ExceptionClear(env);
        }
        if (jTargets != NULL) {
            targets = (Atom *)malloc(nTargets * sizeof(Atom));
            if (targets != NULL) {
#ifdef _LP64
                memcpy(targets, jTargets, nTargets * sizeof(Atom));
#else
                saveJTargets = jTargets;
                saveTargets = targets;
                for (i = 0; i < nTargets; i++, targets++, jTargets++) {
                    *targets = (Atom)*jTargets;
                }
                jTargets = saveJTargets;
                targets = saveTargets;
#endif
            }
            (*env)->ReleaseLongArrayElements(env, formats, jTargets, JNI_ABORT);
        }
    }
    if (targets == NULL) {
        nTargets = 0;
    }

#define SetCB(cbr, cb, cl) cbr[0].callback = (XtCallbackProc)cb; cbr[0].closure = (XtPointer)cl; cbr[1].callback = (XtCallbackProc)NULL; cbr[1].closure = (XtPointer)NULL

#define SetArg(n, v) args[nargs].name = n; args[nargs++].value = (XtArgVal)(v);

    SetCB(dsecbr, awt_XmDragEnterProc,            dscp);
    SetCB(dmcbr,  awt_XmDragMotionProc,           dscp);
    SetCB(occbr,  awt_XmDropOperationChangedProc, dscp);
    SetCB(dslcbr, awt_XmDragLeaveProc,            dscp);
    SetCB(ddfcbr, awt_XmDropFinishProc,           dscp);

    SetArg(XmNblendModel,               XmBLEND_NONE      );
    SetArg(XmNdragOperations,           xmActions         );
    /* No incremental transfer */
    SetArg(XmNconvertProc,              awt_convertData    );
    SetArg(XmNdropSiteEnterCallback,    dsecbr             );
    SetArg(XmNdragMotionCallback,       dmcbr              );
    SetArg(XmNoperationChangedCallback, occbr              );
    SetArg(XmNdropSiteLeaveCallback,    dslcbr             );
    SetArg(XmNdropFinishCallback,       ddfcbr             );
    SetArg(XmNexportTargets,            targets            );
    SetArg(XmNnumExportTargets,         (Cardinal)nTargets );

    {
        jsize len = (*env)->GetArrayLength(env, bdata);
        if (len <= 0) {
            free(targets);
            return ptr_to_jlong(NULL);
        }

        xevent = calloc(1, len);

        if (xevent == NULL) {
            free(targets);
            JNU_ThrowOutOfMemoryError(env, "");
            return ptr_to_jlong(NULL);
        }

        (*env)->GetByteArrayRegion(env, bdata, 0, len, (jbyte *)xevent);

        DASSERT(JNU_IsNull(env, (*env)->ExceptionOccurred(env)));
    }

    if (xevent->type != ButtonPress &&
        xevent->type != ButtonRelease &&
        xevent->type != KeyRelease &&
        xevent->type != KeyPress &&
        xevent->type != MotionNotify) {

        JNU_ThrowByName(env, "java/awt/dnd/InvalidDnDOperationException",
                        "A drag can only be initiated in response to an InputEvent.");
        free(xevent);
        free(targets);
        return ptr_to_jlong(NULL);
    }

    /* This call causes an UnsatisfiedLinkError on Linux.
     * This function is a no-op for Motif 2.1.
     * Since Linux only links against Motif 2.1, we can safely remove
     * this function altogether from the Linux build.
     * bchristi 1/22/2001
     */

#ifdef __solaris__
    awt_motif_adjustDragTriggerEvent(xevent);
#endif

    AWT_LOCK();

    /*
     * Fix for BugTraq ID 4357905.
     * Drop is processed asynchronously on the event dispatch thread.
     * Reject all drag attempts until the current drop is done.
     */
    if (!isDropDone()) {
        JNU_ThrowByName(env, "java/awt/dnd/InvalidDnDOperationException",
                        "Drop transfer in progress.");
        free(xevent);
        free(targets);
        AWT_UNLOCK();
        return ptr_to_jlong(NULL);
    }

    if (XFindContext(awt_display, MOTIF_DROP_ATOM, awt_convertDataContext,
                     (XPointer*)&structPtr) == XCNOMEM || structPtr != NULL) {
        free(xevent);
        free(targets);
        AWT_UNLOCK();
        return ptr_to_jlong(NULL);
    }

    structPtr = calloc(1, sizeof(awt_convertDataCallbackStruct));
    if (structPtr == NULL) {
        free(xevent);
        free(targets);
        JNU_ThrowOutOfMemoryError(env, "");
        AWT_UNLOCK();
        return ptr_to_jlong(NULL);
    }

    structPtr->source              = (*env)->NewGlobalRef(env, component);
    structPtr->transferable        = (*env)->NewGlobalRef(env, transferable);
    structPtr->formatMap           = (*env)->NewGlobalRef(env, formatMap);
    structPtr->formats             = (*env)->NewGlobalRef(env, formats);

    if (XSaveContext(awt_display, MOTIF_DROP_ATOM, awt_convertDataContext,
                     (XPointer)structPtr) == XCNOMEM) {
        free(structPtr);
        free(xevent);
        free(targets);
        AWT_UNLOCK();
        return ptr_to_jlong(NULL);
    }

    dc = XmDragStart(awt_root_shell, xevent, args, nargs);

    /* Fix for 4215643: remember the window corresponding to the drag source
       and the button mask after the event which triggered drag start */

    if (xevent->type == ButtonPress || xevent->type == MotionNotify) {
        _cache.win = xevent->xbutton.window;
        if (xevent->type == ButtonPress) {
            _cache.state = buttonToMask(xevent->xbutton.button);
        } else {
            _cache.state = xevent->xmotion.state & (Button1Mask | Button2Mask);
        }
        XtAddEventHandler(dc, ButtonReleaseMask, False,
                          dragsource_track_release, NULL);
    }

    free(targets);

    if (dc != (Widget)NULL) {
        setCursor(env, awt_display, cursor, ctype, xevent->xbutton.time);
    }

    free(xevent);

    /*
     * With the new synchronization model we don't release awt_lock
     * in the DragContext callbacks. During drag-n-drop operation
     * the events processing is performed not by our awt_MToolkit_loop,
     * but by internal Motif InitiatorMainLoop, which returns only
     * when the operation is completed. So our polling mechanism doesn't
     * have a chance to execute and even if there are no events in
     * the queue AWT_LOCK will still be held by the Toolkit thread
     * and so other threads will likely be blocked on it.
     *
     * The solution is to schedule a timer callback which checks
     * for events and if the queue is empty releases AWT_LOCK and polls
     * the X pipe for some time, then acquires AWT_LOCK back again
     * and reschedules itself.
     */
    if (dc != NULL) {
        exitIdleProc = False;
        XtAddCallback(dc, XmNdragDropFinishCallback,
                      (XtCallbackProc)RemoveIdleProc, NULL);
        XtAppAddTimeOut(awt_appContext, AWT_DND_POLL_INTERVAL / 10,
                        IdleProc, (XtPointer)dscp);
    }

    AWT_UNLOCK();

    return ptr_to_jlong(dc);

#undef SetArg
#undef SetCB
}

/*****************************************************************************/

/**
 *
 */

static void setCursor(JNIEnv* env, Display* dpy, jobject cursor, jint type,
                      Time time)
{
    Cursor xcursor = None;

    if (JNU_IsNull(env, cursor)) return;

    XChangeActivePointerGrab(dpy,
                             ButtonPressMask   |
                             ButtonMotionMask  |
                             ButtonReleaseMask |
                             EnterWindowMask   |
                             LeaveWindowMask,
                             getCursor(env, cursor),
                             time
                             );

    XSync(dpy, False);
}

/**
 * Update the cached targets for this widget
 */

static Boolean updateCachedTargets(JNIEnv* env, Widget dt) {
    Atom*              targets  = (Atom*)NULL;
    Cardinal           nTargets = (Cardinal)0;
    Arg                args[2];

    /*
     * Get the targets for this component
     */
    args[0].name = XmNexportTargets;    args[0].value = (XtArgVal)&targets;
    args[1].name = XmNnumExportTargets; args[1].value = (XtArgVal)&nTargets;
    XtGetValues(_cache.dt = dt, args, 2);

    /*
     * Free the previous targets if there were any
     */
    if (!JNU_IsNull(env, _cache.targets)) {
        (*env)->DeleteGlobalRef(env, _cache.targets);
        _cache.targets = (jlongArray)NULL;
    }

    _cache.nTargets = nTargets;

    /*
     * If the widget has targets (atoms) then copy them to the cache
     */
    if (nTargets > 0) {
        jboolean isCopy;
        jlong*   jTargets;

#ifndef _LP64 /* Atom and jlong are different sizes in the 32-bit build */
        jlong*   saveJTargets;
        Cardinal i;
#endif

        _cache.targets = (*env)->NewLongArray(env, nTargets);
        if (_cache.targets == NULL) {
            _cache.nTargets = 0;
            return False;
        }

        _cache.targets = (*env)->NewGlobalRef(env, _cache.targets);
        if (_cache.targets == NULL) {
            _cache.nTargets = 0;
            return False;
        }

        jTargets = (*env)->GetLongArrayElements(env, _cache.targets, &isCopy);
        if (jTargets == NULL) {
            (*env)->DeleteGlobalRef(env, _cache.targets);
            _cache.targets = NULL;
            _cache.nTargets = 0;
            return False;
        }

#ifdef _LP64
        memcpy(jTargets, targets, nTargets * sizeof(Atom));
#else
        saveJTargets = jTargets;
        for (i = 0; i < nTargets; i++, jTargets++, targets++) {
            *jTargets = (*targets & 0xFFFFFFFFLU);
        }
        jTargets = saveJTargets;
#endif

        (*env)->ReleaseLongArrayElements(env, _cache.targets, jTargets, 0);
        return True;
    }

    return False;
}


/**
 *
 */

static void flush_cache(JNIEnv* env) {
    _cache.w  = (Widget)NULL;
    _cache.dt = (Widget)NULL;

    (*env)->DeleteGlobalRef(env, _cache.peer);
    _cache.peer = (jobject)NULL;

    (*env)->DeleteGlobalRef(env, _cache.component);
    _cache.component = (jobject)NULL;

    if (_cache.dtcpeer != (jobject)NULL) {
        (*env)->DeleteGlobalRef(env, _cache.dtcpeer);

        _cache.dtcpeer = (jobject)NULL;
    }

    _cache.nTargets  = (Cardinal)0;
    if (_cache.targets != (jlongArray)NULL) {
        (*env)->DeleteGlobalRef(env, _cache.targets);
        _cache.targets = (jlongArray)NULL;
    }

    _cache.transfersPending = 0;
    _cache.flushPending     = False;
    _cache.transfer         = (Widget)NULL;
    cacheDropDone(True);
}

/**
 *
 */

static void update_cache(JNIEnv* env, Widget w, Widget dt) {
    if(w != _cache.w) {
        struct ComponentData* cdata   = (struct ComponentData *)NULL;
        Arg                   args[1] =
        {{ XmNuserData, (XtArgVal)&_cache.peer}};

        flush_cache(env);

        if (w == (Widget)NULL) return;

        XtGetValues(w, args, 1);

        if (JNU_IsNull(env, _cache.peer)) {
            _cache.w = NULL;

            return;
        }

        cdata = (struct ComponentData *)
            JNU_GetLongFieldAsPtr(env, _cache.peer, mComponentPeerIDs.pData);

        if (cdata         == NULL ||
            cdata->widget != w ||
            cdata->dsi    == (DropSitePtr)NULL) {
            _cache.w = NULL;

            return;
        }

        _cache.w         = w;
        _cache.component = (*env)->NewGlobalRef(env, cdata->dsi->component);
        _cache.peer      = (*env)->NewGlobalRef(env, _cache.peer);
        /* SECURITY: OK to call this on privileged thread - peer is secure */
        {
            jobject dtcpeer = call_dTCcreate(env);
            if (!JNU_IsNull(env, dtcpeer)) {
                _cache.dtcpeer = (*env)->NewGlobalRef(env, dtcpeer);
                (*env)->DeleteLocalRef(env, dtcpeer);
            } else {
                _cache.dtcpeer = NULL;
            }
        }

        _cache.transfersPending = 0;
        cacheDropDone(True);
    }

    if (_cache.w != (Widget)NULL) updateCachedTargets(env, dt);
}


/**
 *
 */

static void
cacheDropDone(Boolean dropDone) {
    _cache.dropDone = dropDone;
}

static Boolean
isDropDone() {
    return _cache.dropDone;
}

/**
 *
 */

static jint XmToDnDConstants(unsigned char operations) {
    jint src = java_awt_dnd_DnDConstants_ACTION_NONE;

    if (operations & XmDROP_MOVE) src |= java_awt_dnd_DnDConstants_ACTION_MOVE;
    if (operations & XmDROP_COPY) src |= java_awt_dnd_DnDConstants_ACTION_COPY;
    if (operations & XmDROP_LINK) src |= java_awt_dnd_DnDConstants_ACTION_LINK;

    return src;
}

static unsigned char selectOperation(unsigned char operations) {
    if (operations & XmDROP_MOVE) return XmDROP_MOVE;
    if (operations & XmDROP_COPY) return XmDROP_COPY;
    if (operations & XmDROP_LINK) return XmDROP_LINK;

    return XmDROP_NOOP;
}

/**
 *
 */

static unsigned char DnDConstantsToXm(jint actions) {
    unsigned char ret = XmDROP_NOOP;

    if (actions & java_awt_dnd_DnDConstants_ACTION_COPY) ret |= XmDROP_COPY;
    if (actions & java_awt_dnd_DnDConstants_ACTION_MOVE) ret |= XmDROP_MOVE;
    if (actions & java_awt_dnd_DnDConstants_ACTION_LINK) ret |= XmDROP_LINK;

    return ret;
}

/**
 *
 */

typedef struct DragExitProcStruct {
    XtIntervalId timerId;
    jobject      dtcpeer;     /* global reference */
    jobject      component;   /* global reference */
    jlong        dragContext; /* pointer          */
} DragExitProcStruct;

static DragExitProcStruct pending_drag_exit_data =
    { (XtIntervalId)0, NULL, NULL, (jlong)0 };

static void drag_exit_proc(XtPointer client_data, XtIntervalId* id) {
    JNIEnv* env = (JNIEnv *)JNU_GetEnv(jvm, JNI_VERSION_1_2);

    DASSERT(!JNU_IsNull(env, pending_drag_exit_data.dtcpeer));
    DASSERT(!JNU_IsNull(env, pending_drag_exit_data.component));
    DASSERT(pending_drag_exit_data.dragContext != NULL);

    if (pending_drag_exit_data.timerId != (XtIntervalId)0) {
        if (id == NULL) {
            XtRemoveTimeOut(pending_drag_exit_data.timerId);
        }
        if (id == NULL || pending_drag_exit_data.timerId == *id) {

            /* SECURITY: OK to call this on privileged thread -
               peer is secure */
            call_dTCexit(env, pending_drag_exit_data.dtcpeer,
                         pending_drag_exit_data.component,
                         pending_drag_exit_data.dragContext);

            if ((*env)->ExceptionCheck(env) == JNI_TRUE) {
                (*env)->ExceptionDescribe(env);
                (*env)->ExceptionClear(env);
            }
        }
    }

    /* cleanup */
    (*env)->DeleteGlobalRef(env, pending_drag_exit_data.dtcpeer);
    (*env)->DeleteGlobalRef(env, pending_drag_exit_data.component);

    memset(&pending_drag_exit_data, 0, sizeof(DragExitProcStruct));
}

static void awt_XmDragProc(Widget w, XtPointer closure,
                           XmDragProcCallbackStruct* cbstruct)
{
    JNIEnv* env       = (JNIEnv *)JNU_GetEnv(jvm, JNI_VERSION_1_2);
    jobject component = (jobject)NULL;
    jint    src       = java_awt_dnd_DnDConstants_ACTION_NONE;
    jint    usrAction = java_awt_dnd_DnDConstants_ACTION_NONE;
    jint    ret       = java_awt_dnd_DnDConstants_ACTION_NONE;
    unsigned char srcOps = XmDROP_NOOP;

    /*
     * Fix for BugTraq ID 4395290.
     * We should dispatch any pending java upcall right now
     * to keep the order of upcalls.
     */
    if (pending_drag_exit_data.timerId != (XtIntervalId)0) {
        drag_exit_proc(NULL, NULL);
    }

    /*
     * Fix for BugTraq ID 4357905.
     * Drop is processed asynchronously on the event dispatch thread.
     * We reject other drop attempts to protect the SunDTCP context
     * from being overwritten by an upcall before the drop is done.
     */
    if (!isDropDone()) {
        cbstruct->operation  = XmDROP_NOOP;
        cbstruct->dropSiteStatus = XmINVALID_DROP_SITE;
        return;
    }

    if (cbstruct->dragContext == NULL) {
        cbstruct->operation  = XmDROP_NOOP;
        cbstruct->dropSiteStatus = XmINVALID_DROP_SITE;
        return;
    }

    (*env)->PushLocalFrame(env, 0);

    /*
     * Fix for BugTraq ID 4285634.
     * If some modifier keys are pressed the Motif toolkit initializes
     * cbstruct->operations this field to the bitwise AND of the
     * XmDragOperations resource of the XmDragContext for this drag operation
     * and the drop action corresponding to the current modifiers state.
     * We need to determine the drag operations supported by the drag source, so
     * we have to get XmNdragOperations value of the XmDragSource.
     */
    XtVaGetValues(cbstruct->dragContext, XmNdragOperations, &srcOps, NULL);
    src = XmToDnDConstants(srcOps);
    usrAction = XmToDnDConstants(selectOperation(cbstruct->operations));

    update_cache(env, w, cbstruct->dragContext);

    if (!JNU_IsNull(env, (*env)->ExceptionOccurred(env))) {
        flush_cache(env);
        (*env)->ExceptionDescribe(env);
        (*env)->ExceptionClear(env);
        goto wayout;
    }

    switch (cbstruct->reason) {
    case XmCR_DROP_SITE_ENTER_MESSAGE: {

        /* SECURITY: OK to call this on privileged thread -
           peer is secure */
        ret = call_dTCenter(env, _cache.dtcpeer, _cache.component,
                            cbstruct->x, cbstruct->y,
                            usrAction, src,
                            _cache.targets,ptr_to_jlong(cbstruct->dragContext));

        if (!JNU_IsNull(env, (*env)->ExceptionOccurred(env))) {
            flush_cache(env);
            (*env)->ExceptionDescribe(env);
            (*env)->ExceptionClear(env);
        }
    }
    break;

    case XmCR_DROP_SITE_LEAVE_MESSAGE: {

        DASSERT(pending_drag_exit_data.timerId == (XtIntervalId)0);
        DASSERT(JNU_IsNull(env, pending_drag_exit_data.dtcpeer));
        DASSERT(JNU_IsNull(env, pending_drag_exit_data.component));
        DASSERT(pending_drag_exit_data.dragContext == (jlong)0);

        DASSERT(!JNU_IsNull(env, _cache.dtcpeer));
        DASSERT(!JNU_IsNull(env, _cache.component));
        DASSERT(cbstruct->dragContext != NULL);

        pending_drag_exit_data.dtcpeer =
            (*env)->NewGlobalRef(env, _cache.dtcpeer);
        pending_drag_exit_data.component =
            (*env)->NewGlobalRef(env, _cache.component);
        pending_drag_exit_data.dragContext =
            ptr_to_jlong(cbstruct->dragContext);

        /*
         * Fix for BugTraq ID 4395290.
         * Postpone upcall to java, so that we can abort it in case
         * if drop immediatelly follows.
         */
        if (!JNU_IsNull(env, pending_drag_exit_data.dtcpeer) &&
            !JNU_IsNull(env, pending_drag_exit_data.component)) {
            pending_drag_exit_data.timerId =
                XtAppAddTimeOut(awt_appContext, 0, drag_exit_proc, NULL);
            DASSERT(pending_drag_exit_data.timerId != (XtIntervalId)0);
        } else {
            JNU_ThrowOutOfMemoryError(env, "OutOfMemoryError");
            if ((*env)->ExceptionCheck(env) == JNI_TRUE) {
                (*env)->ExceptionDescribe(env);
                (*env)->ExceptionClear(env);
            }
            if (!JNU_IsNull(env, pending_drag_exit_data.dtcpeer)) {
                (*env)->DeleteGlobalRef(env, pending_drag_exit_data.dtcpeer);
            }
            if (!JNU_IsNull(env, pending_drag_exit_data.component)) {
                (*env)->DeleteGlobalRef(env, pending_drag_exit_data.component);
            }
            memset(&pending_drag_exit_data, 0, sizeof(DragExitProcStruct));
        }

        ret = java_awt_dnd_DnDConstants_ACTION_NONE;

        /* now cleanup */

        flush_cache(env);
    }
    break;

    case XmCR_DROP_SITE_MOTION_MESSAGE: {

        /* SECURITY: OK to call this on privileged thread -
           peer is secure */
        ret = call_dTCmotion(env, _cache.dtcpeer, _cache.component,
                             cbstruct->x, cbstruct->y,
                             usrAction, src,
                             _cache.targets,
                             ptr_to_jlong(cbstruct->dragContext));

        if (!JNU_IsNull(env, (*env)->ExceptionOccurred(env))) {
            flush_cache(env);
            (*env)->ExceptionDescribe(env);
            (*env)->ExceptionClear(env);
        }

    }
    break;

    case XmCR_OPERATION_CHANGED: {

        /* SECURITY: OK to call this on privileged thread -
           peer is secure */
        ret = call_dTCmotion(env, _cache.dtcpeer, _cache.component,
                             cbstruct->x, cbstruct->y,
                             usrAction, src,
                             _cache.targets,
                             ptr_to_jlong(cbstruct->dragContext));

        if (!JNU_IsNull(env, (*env)->ExceptionOccurred(env))) {
            flush_cache(env);
            (*env)->ExceptionDescribe(env);
            (*env)->ExceptionClear(env);
        }

    }
    break;

    default: break;
    }

 wayout:

    /*
     * Fix for BugTraq ID 4285634.
     * If some modifier keys are pressed the Motif toolkit initializes
     * cbstruct->operations this field to the bitwise AND of the
     * XmDragOperations resource of the XmDragContext for this drag operation
     * and the drop action corresponding to the current modifiers state.
     * We should allow the drop target to select a drop action independent of
     * the current modifiers state.
     */
    cbstruct->operation  = DnDConstantsToXm(ret);

    if (cbstruct->reason != XmCR_DROP_SITE_LEAVE_MESSAGE) {
        Arg arg;
        arg.name = XmNdropSiteOperations;
        arg.value = (XtArgVal)cbstruct->operation;

        XmDropSiteUpdate(w, &arg, 1);
    }

    if (ret != java_awt_dnd_DnDConstants_ACTION_NONE) {
        cbstruct->dropSiteStatus = XmVALID_DROP_SITE;
    }  else {
        cbstruct->dropSiteStatus = XmINVALID_DROP_SITE;
    }

    (*env)->PopLocalFrame(env, NULL);
}

static void drop_failure_cleanup(JNIEnv* env, Widget dragContext) {
    Arg arg;

    DASSERT(dragContext != NULL);
    _cache.transfer = NULL;
    _cache.dropAction = XmDROP_NOOP;

    arg.name  = XmNtransferStatus;
    arg.value = (XtArgVal)XmTRANSFER_FAILURE;
    XmDropTransferStart(dragContext, &arg, 1);

    /* Flush here, since awt_XmTransferProc won't be called. */
    flush_cache(env);
}

/**
 *
 */

static void awt_XmDropProc(Widget w, XtPointer closure,
                           XmDropProcCallbackStruct* cbstruct)
{
    JNIEnv*       env       = (JNIEnv *)JNU_GetEnv(jvm, JNI_VERSION_1_2);
    jint          src       = java_awt_dnd_DnDConstants_ACTION_NONE;
    unsigned char operation = selectOperation(cbstruct->operations);
    unsigned char srcOps = XmDROP_NOOP;
    unsigned char dstOps = XmDROP_NOOP;
    Arg           arg;
    Boolean       sourceIsExternal = False;

    arg.name = XmNdropSiteOperations;
    arg.value = (XtArgVal)&dstOps;
    XmDropSiteRetrieve(w, &arg, 1);
    arg.value = (XtArgVal)(XmDROP_COPY | XmDROP_MOVE | XmDROP_LINK);
    XmDropSiteUpdate(w, &arg, 1);

    /*
     * Fix for BugTraq ID 4357905.
     * Drop is processed asynchronously on the event dispatch thread.
     * We reject other drop attempts to protect the SunDTCP context
     * from being overwritten by an upcall before the drop is done.
     */
    if (!isDropDone()) {
        return;
    }

    if (cbstruct->dragContext == NULL) {
        cbstruct->operation  = XmDROP_NOOP;
        cbstruct->dropSiteStatus = XmINVALID_DROP_SITE;
        return;
    }

    /*
     * Fix for BugTraq ID 4492640.
     * Because of the Motif bug #4528191 XmNdragOperations resource is always
     * equal to XmDROP_MOVE | XmDROP_COPY when the drag source is external.
     * The workaround for this bug is to assume that an external drag source
     * supports all drop actions.
     */
    XtVaGetValues(cbstruct->dragContext,
                  XmNsourceIsExternal, &sourceIsExternal, NULL);

    if (sourceIsExternal) {
        srcOps = XmDROP_LINK | XmDROP_MOVE | XmDROP_COPY;
    } else {
        /*
         * Fix for BugTraq ID 4285634.
         * If some modifier keys are pressed the Motif toolkit initializes
         * cbstruct->operations to the bitwise AND of the
         * XmDragOperations resource of the XmDragContext for this drag operation
         * and the drop action corresponding to the current modifiers state.
         * We need to determine the drag operations supported by the drag source, so
         * we have to get XmNdragOperations value of the XmDragSource.
         */
        XtVaGetValues(cbstruct->dragContext, XmNdragOperations, &srcOps, NULL);
    }

    src = XmToDnDConstants(srcOps);

    if ((srcOps & dstOps) == 0) {
        cbstruct->operation  = XmDROP_NOOP;
        cbstruct->dropSiteStatus = XmINVALID_DROP_SITE;
        drop_failure_cleanup(env, cbstruct->dragContext);
        return;
    }

    (*env)->PushLocalFrame(env, 0);

    update_cache(env, w, cbstruct->dragContext);

    cacheDropDone(False);

    if (!JNU_IsNull(env, (*env)->ExceptionOccurred(env))) {
        (*env)->ExceptionDescribe(env);
        (*env)->ExceptionClear(env);
        (*env)->PopLocalFrame(env, NULL);
        drop_failure_cleanup(env, cbstruct->dragContext);
        return;
    }

    /*
     * Fix for BugTraq ID 4395290.
     * Abort a pending upcall to dragExit.
     */
    pending_drag_exit_data.timerId = (XtIntervalId)0;

    /* SECURITY: OK to call this on privileged thread - peer is secure */
    call_dTCdrop(env, _cache.dtcpeer, _cache.component,
                 cbstruct->x, cbstruct->y,
                 XmToDnDConstants(operation), src, _cache.targets,
                 ptr_to_jlong(cbstruct->dragContext));

    if (!JNU_IsNull(env, (*env)->ExceptionOccurred(env))) {
        flush_cache(env);
        (*env)->ExceptionDescribe(env);
        (*env)->ExceptionClear(env);
    }
    (*env)->PopLocalFrame(env, NULL);
}

/**
 *
 */

static void awt_XmTransferProc(Widget w, XtPointer closure, Atom* selection,
                               Atom* type, XtPointer value,
                               unsigned long* length, int32_t* format)
{
    JNIEnv*  env   = (JNIEnv *)JNU_GetEnv(jvm, JNI_VERSION_1_2);
    Atom     req   = (Atom)closure;
    Display* dpy   = XtDisplayOfObject(w);
    jobject  tName = NULL;

    /*
     * Note: this method is only called to transfer data between clients
     * in different JVM's or native apps. For Intra-JVM transfers the peer
     * code shares the sources Transferable with the destination.
     */

    if (_cache.w == (Widget)NULL || _cache.transfer != w) {
        if (value != NULL) {
            XtFree(value);
            value = NULL;
        }
        /* we have already cleaned up ... */
        return;
    }

    (*env)->PushLocalFrame(env, 0);

    if (*type == None || *type == XT_CONVERT_FAIL) {
        /* SECURITY: OK to call this on privileged thread - peer is secure
         */
        call_dTCtxFailed(env, _cache.dtcpeer, (jlong)req);
    } else {
        switch (*format) {
        case  8:
        case 16:
        case 32: {
            jsize size = (*length <= INT_MAX) ? (jsize)*length : INT_MAX;
            jbyteArray arry = (*env)->NewByteArray(env, size);

            if (!JNU_IsNull(env, (*env)->ExceptionOccurred(env))) {
                (*env)->ExceptionDescribe(env);
                (*env)->ExceptionClear(env);

                /* SECURITY: OK to call this on privileged thread -
                   peer is secure */
                call_dTCtxFailed(env, _cache.dtcpeer, (jlong)req);

                goto wayout;
            }

            (*env)->SetByteArrayRegion(env, arry, 0, size, (jbyte*)value);
            if (!JNU_IsNull(env, (*env)->ExceptionOccurred(env))) {
                (*env)->ExceptionDescribe(env);
                (*env)->ExceptionClear(env);

                /* SECURITY: OK to call this on privileged thread -
                   peer is secure */
                call_dTCtxFailed(env, _cache.dtcpeer, (jlong)req);
                goto wayout;
            }

            arry = (*env)->NewGlobalRef(env, arry);

            if (!JNU_IsNull(env, (*env)->ExceptionOccurred(env))) {
                (*env)->ExceptionDescribe(env);
                (*env)->ExceptionClear(env);
            }

            {
                char* tn = XGetAtomName(dpy, *type);

                tName = (*env)->NewStringUTF(env, (const char *)tn);

                if (!JNU_IsNull(env, (*env)->ExceptionOccurred(env))) {
                    (*env)->ExceptionDescribe(env);
                    (*env)->ExceptionClear(env);
                }

                XFree((void *)tn);
            }

            /* SECURITY: OK to call this on privileged thread - peer is
               secure */
            call_dTCnewData(env, _cache.dtcpeer, (jlong)req, tName, arry);

            if (!JNU_IsNull(env, (*env)->ExceptionOccurred(env))) {
                (*env)->ExceptionDescribe(env);
                (*env)->ExceptionClear(env);
            }
        }

        default:
            break;
        }
    }

 wayout:
    if (value != NULL) {
        XtFree(value);
        value = NULL;
    }

    _cache.transfersPending--;
    while (_cache.transfersPending == 0 && !isDropDone()) {
        AWT_WAIT(0);
    }

    if (isDropDone() && _cache.flushPending) {
        flush_cache(env);
    }

    (*env)->PopLocalFrame(env, NULL);
}

/**
 *
 */

static void awt_XmDragEnterProc(Widget w, XtPointer closure,
                                XmDropSiteEnterCallbackStruct* cbstruct)
{
    JNIEnv*  env   = (JNIEnv *)JNU_GetEnv(jvm, JNI_VERSION_1_2);
    jobject  this  = (jobject)closure;

    /*  This should only be valid, but Im leaving this part of the old code */
    jboolean valid = cbstruct->dropSiteStatus == XmVALID_DROP_SITE
        ? JNI_TRUE : JNI_FALSE;

    if (valid == JNI_TRUE) {
        /*
         * Workaround for Motif bug id #4457656.
         * Pointer coordinates passed in cbstruct are incorrect.
         * We have to make a round-trip query.
         */
        Window rootWindow, childWindow;
        int32_t xw, yw, xr, yr;
        uint32_t modifiers;

        XQueryPointer(awt_display, XtWindow(w),
                      &rootWindow, &childWindow, &xr, &yr, &xw, &yw, &modifiers);

        (*env)->PushLocalFrame(env, 0);

        /* SECURITY: OK to call this on privileged thread - peer is secure */
        call_dSCenter(env, this, XmToDnDConstants(cbstruct->operation),
                      convertModifiers(modifiers), xr, yr);

        if (!JNU_IsNull(env, (*env)->ExceptionOccurred(env))) {
            (*env)->ExceptionDescribe(env);
            (*env)->ExceptionClear(env);
        }

        (*env)->PopLocalFrame(env, NULL);
    }
}

/**
 *
 */

static void awt_XmDragMotionProc(Widget w, XtPointer closure,
                                 XmDragMotionCallbackStruct* cbstruct)
{
    JNIEnv*  env   = (JNIEnv *)JNU_GetEnv(jvm, JNI_VERSION_1_2);
    jobject  this  = (jobject)closure;

    /*  This should only be valid, but Im leaving this part of the old code */
    jboolean valid = cbstruct->dropSiteStatus == XmVALID_DROP_SITE
        ? JNI_TRUE : JNI_FALSE;
    Window rootWindow, childWindow;
    int32_t xw, yw, xr, yr;
    uint32_t modifiers;

    XQueryPointer(awt_display, XtWindow(w),
                  &rootWindow, &childWindow, &xr, &yr, &xw, &yw, &modifiers);
    /*
     * Fix for 4285634.
     * Use the cached modifiers state, since the directly queried state can
     * differ from the one associated with this dnd notification.
     */
    modifiers = ((XmDragContext)w)->drag.lastEventState;
    if (xr != x_root || yr != y_root) {
        call_dSCmouseMoved(env, this, XmToDnDConstants(cbstruct->operation),
                           convertModifiers(modifiers), xr, yr);

        if ((*env)->ExceptionCheck(env) == JNI_TRUE) {
            (*env)->ExceptionDescribe(env);
            (*env)->ExceptionClear(env);
        }

        x_root = xr;
        y_root = yr;
    }

    if (valid == JNI_TRUE) {

        (*env)->PushLocalFrame(env, 0);

        /* SECURITY: OK to call this on privileged thread - peer is secure */
        call_dSCmotion(env, this, XmToDnDConstants(cbstruct->operation),
                       convertModifiers(modifiers), xr, yr);

        if (!JNU_IsNull(env, (*env)->ExceptionOccurred(env))) {
            (*env)->ExceptionDescribe(env);
            (*env)->ExceptionClear(env);
        }

        (*env)->PopLocalFrame(env, NULL);
    } else {
        (*env)->PushLocalFrame(env, 0);

        /* SECURITY: OK to call this on privileged thread - peer is secure */
        call_dSCexit(env, this, xr, yr);

        if (!JNU_IsNull(env, (*env)->ExceptionOccurred(env))) {
            (*env)->ExceptionDescribe(env);
            (*env)->ExceptionClear(env);
        }

        (*env)->PopLocalFrame(env, NULL);
    }
}

/**
 *
 */

static void awt_XmDragLeaveProc(Widget w, XtPointer closure,
                                XmDropSiteLeaveCallbackStruct* cbstruct)
{
    JNIEnv* env  = (JNIEnv *)JNU_GetEnv(jvm, JNI_VERSION_1_2);
    jobject this = (jobject)closure;
    Window rootWindow, childWindow;
    int32_t xw, yw, xr, yr;
    uint32_t modifiers;

    XQueryPointer(XtDisplay(w), XtWindow(w),
                  &rootWindow, &childWindow, &xr, &yr, &xw, &yw, &modifiers);

    (*env)->PushLocalFrame(env, 0);

    /* SECURITY: OK to call this on privileged thread - peer is secure */
    call_dSCexit(env, this, xr, yr);

    if (!JNU_IsNull(env, (*env)->ExceptionOccurred(env))) {
        (*env)->ExceptionDescribe(env);
        (*env)->ExceptionClear(env);
    }

    (*env)->PopLocalFrame(env, NULL);
}

/**
 *
 */

static void awt_XmDropOperationChangedProc(Widget w, XtPointer closure,
                                           XmDropStartCallbackStruct* cbstruct)
{
    JNIEnv*  env   = (JNIEnv *)JNU_GetEnv(jvm, JNI_VERSION_1_2);
    jobject  this  = (jobject)closure;
    Window rootWindow, childWindow;
    int32_t xw, yw, xr, yr;
    uint32_t modifiers;

    XQueryPointer(XtDisplay(w), XtWindow(w),
                  &rootWindow, &childWindow, &xr, &yr, &xw, &yw, &modifiers);

    (*env)->PushLocalFrame(env, 0);


    /* SECURITY: OK to call this on privileged thread - peer is secure */
    call_dSCchanged(env, this, XmToDnDConstants(cbstruct->operation),
                    convertModifiers(modifiers), xr, yr);

    if (!JNU_IsNull(env, (*env)->ExceptionOccurred(env))) {
        (*env)->ExceptionDescribe(env);
        (*env)->ExceptionClear(env);
    }

    (*env)->PopLocalFrame(env, NULL);
}

/**
 *
 */

static void awt_XmDropFinishProc(Widget w, XtPointer closure,
                                 XmDropFinishCallbackStruct* cbstruct)
{
    JNIEnv* env  = (JNIEnv *)JNU_GetEnv(jvm, JNI_VERSION_1_2);
    jobject this = (jobject)closure;
    unsigned char completionStatus = cbstruct->completionStatus;
    jint dropAction = XmToDnDConstants(cbstruct->operation);
    Window rootWindow, childWindow;
    int32_t xw, yw, xr, yr;
    uint32_t modifiers;

    XQueryPointer(XtDisplay(w), XtWindow(w),
                  &rootWindow, &childWindow, &xr, &yr, &xw, &yw, &modifiers);

    /* cleanup */

    if (_cache.transfer == NULL) {
        dropAction = _cache.dropAction;
    }

    _cache.dropAction = java_awt_dnd_DnDConstants_ACTION_NONE;
    _cache.win = None;
    _cache.state = 0;
    XtRemoveEventHandler(w, ButtonReleaseMask, False,
                         dragsource_track_release, NULL);

    /* SECURITY: OK to call this on privileged thread - peer is secure */
    call_dSCddfinished(env, this, completionStatus, dropAction, xr, yr);

    if (!JNU_IsNull(env, (*env)->ExceptionOccurred(env))) {
        (*env)->ExceptionDescribe(env);
        (*env)->ExceptionClear(env);
    }

    awt_cleanupConvertDataContext(env, MOTIF_DROP_ATOM);
}
