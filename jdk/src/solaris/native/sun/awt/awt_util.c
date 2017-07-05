/*
 * Copyright (c) 1995, 2003, Oracle and/or its affiliates. All rights reserved.
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

#include "awt_p.h"
#include "color.h"
#include "awt_TopLevel.h"
#include <X11/IntrinsicP.h>
#include <X11/Xatom.h>
#include <X11/Xmd.h>
#include <X11/Xutil.h>
#include <X11/Xproto.h>
#ifndef XAWT
#include <Xm/MenuShell.h>
#include <Xm/List.h>
#include <Xm/Form.h>
#include <Xm/RowColumn.h>
#include <Xm/MwmUtil.h>
#endif /* XAWT */
#include <jni.h>
#include <jni_util.h>
#include <sys/time.h>

#include "awt_xembed.h"


#ifndef XAWT
#if MOTIF_VERSION!=1
    #include <Xm/GrabShell.h>
#endif
#endif

#include "java_awt_event_MouseWheelEvent.h"

/*
 * Since X reports protocol errors asynchronously, we often need to
 * install an error handler that acts like a callback.  While that
 * specialized handler is installed we save original handler here.
 */
XErrorHandler xerror_saved_handler;

/*
 * A place for error handler to report the error code.
 */
unsigned char xerror_code;

extern jint getModifiers(uint32_t state, jint button, jint keyCode);
extern jint getButton(uint32_t button);

static int32_t winmgr_running = 0;
static Atom OLDecorDelAtom = 0;
static Atom MWMHints = 0;
static Atom DTWMHints = 0;
static Atom decor_list[9];

#ifndef MAX
#define MAX(a,b) ((a) > (b) ? (a) : (b))
#endif

#ifndef MIN
#define MIN(a,b) ((a) < (b) ? (a) : (b))
#endif

#ifndef XAWT
/*
 * The following three funtions are to work around menu problems
 */

/*
 * test if there is a menu that has the current focus
 * called from awt_Dialog.c and awt_Component.c
 */
Boolean
awt_util_focusIsOnMenu(Display *display)
{
  Window window;
  Widget widget;
  int32_t rtr;

  XGetInputFocus(display, &window, &rtr);
  if (window == None) {
    return False;
  }

  widget = XtWindowToWidget(display, window);
  if (widget == NULL) {
    return False;
  }

  if (XtIsSubclass(widget, xmMenuShellWidgetClass)) {
    return True;
  }

  #if MOTIF_VERSION!=1
  /* Motif 2.1 uses XmGrabShell on XmComboBox instead
     of XmMenuShell
  */
  if (XtIsSubclass(widget, xmGrabShellWidgetClass)) {
      return True;
  }
  /* Fix 4800638 check the ancestor of focus widget is
     GrabSell
   */
  if (XtIsSubclass(widget, xmListWidgetClass))
  {
      Widget shell = getShellWidget(widget);
      if (shell && XtIsSubclass(shell,
          xmGrabShellWidgetClass))
      {
          return True;
      }
  }
  #endif

  if (XtIsSubclass(widget, xmRowColumnWidgetClass)) {
      unsigned char type;
      XtVaGetValues(widget, XmNrowColumnType, &type, NULL);
      if (type == XmMENU_BAR) {
          return True;
      }
  }
  return False;
}

static
void fillButtonEvent(XButtonEvent *ev, int32_t type, Display *display, Window window) {
    ev->type = type;
    ev->display = display;
    ev->window = window;
    ev->send_event = True;

    /* REMIND: multi-screen */
    ev->root = RootWindow(display, DefaultScreen(display));
    ev->subwindow = (Window)None;
    ev->time = CurrentTime;
    ev->x = 0;
    ev->y = 0;
    ev->x_root = 0;
    ev->y_root = 0;
    ev->same_screen = True;
    ev->button = Button1;
    ev->state = Button1Mask;
}

/*
 * generates a mouse press event and a release event
 * called from awt_Dialog.c
 */
int32_t
awt_util_sendButtonClick(Display *display, Window window)
{
  XButtonEvent ev;
  int32_t status;

  fillButtonEvent(&ev, ButtonPress, display, window);
  status = XSendEvent(display, window, True, ButtonPressMask, (XEvent *)&ev);

  if (status != 0) {
      fillButtonEvent(&ev, ButtonRelease, display, window);
      status = XSendEvent(display, window, False, ButtonReleaseMask,
                          (XEvent *)&ev);
  }
  return status;
}

Widget
awt_util_createWarningWindow(Widget parent, char *warning)
{
    Widget warningWindow;
#ifdef NETSCAPE
    extern Widget FE_MakeAppletSecurityChrome(Widget parent, char* message);
    warningWindow = FE_MakeAppletSecurityChrome(parent, warning);
#else
    Widget label;
    int32_t argc;
#define MAX_ARGC 10
    Arg args[MAX_ARGC];
    int32_t screen = 0;
    int32_t i;
    AwtGraphicsConfigDataPtr adata;
    extern int32_t awt_numScreens;

    Pixel gray;
    Pixel black;

    for (i = 0; i < awt_numScreens; i++) {
        if (ScreenOfDisplay(awt_display, i) == XtScreen(parent)) {
            screen = i;
            break;
        }
    }
    adata = getDefaultConfig(screen);

    gray = adata->AwtColorMatch(192, 192, 192, adata);
    black = adata->AwtColorMatch(0, 0, 0, adata);

    argc = 0;
    XtSetArg(args[argc], XmNbackground, gray); argc++;
    XtSetArg(args[argc], XmNmarginHeight, 0); argc++;
    XtSetArg(args[argc], XmNmarginWidth, 0); argc++;
    XtSetArg (args[argc], XmNscreen, XtScreen(parent)); argc++;

    DASSERT(!(argc > MAX_ARGC));
    warningWindow =  XmCreateForm(parent, "main", args, argc);

    XtManageChild(warningWindow);
    label = XtVaCreateManagedWidget(warning,
                                    xmLabelWidgetClass, warningWindow,
                                    XmNhighlightThickness, 0,
                                    XmNbackground, gray,
                                    XmNforeground, black,
                                    XmNalignment, XmALIGNMENT_CENTER,
                                    XmNrecomputeSize, False,
                                    NULL);
    XtVaSetValues(label,
                  XmNbottomAttachment, XmATTACH_FORM,
                  XmNtopAttachment, XmATTACH_FORM,
                  XmNleftAttachment, XmATTACH_FORM,
                  XmNrightAttachment, XmATTACH_FORM,
                  NULL);
#endif
    return warningWindow;
}

void
awt_setWidgetGravity(Widget w, int32_t gravity)
{
    XSetWindowAttributes    xattr;
    Window  win = XtWindow(w);

    if (win != None) {
        xattr.bit_gravity = StaticGravity;
        xattr.win_gravity = StaticGravity;
        XChangeWindowAttributes(XtDisplay(w), win,
                                CWBitGravity|CWWinGravity,
                                &xattr);
    }
}

Widget get_shell_focused_widget(Widget w) {
    while (w != NULL && !XtIsShell(w)) {
        w = XtParent(w);
    }
    if (w != NULL) {
        return XmGetFocusWidget(w);
    } else {
        return NULL;
    }
}

void
awt_util_reshape(Widget w, jint x, jint y, jint wd, jint ht)
{
    Widget parent;
    Dimension ww, wh;
    Position wx, wy;
    Boolean move = False;
    Boolean resize = False;
    Boolean mapped_when_managed = False;
    Boolean need_to_unmanage = True;
    Widget saved_focus_widget = NULL;

    if (w == NULL) {
        JNIEnv *env = (JNIEnv *)JNU_GetEnv(jvm, JNI_VERSION_1_2);
        JNU_ThrowNullPointerException(env,"NullPointerException");
        return;
    }
    parent = XtParent(w);

    /* Aim: hack to prevent direct children of scrollpane from
     * being unmanaged during a reshape operation (which results
     * in too many expose events).
     */
    if (parent != NULL && XtParent(parent) != NULL &&
        XtIsSubclass(XtParent(parent), xmScrolledWindowWidgetClass)) {
        need_to_unmanage = False;
    }

    XtVaGetValues(w,
                  XmNwidth, &ww,
                  XmNheight, &wh,
                  XmNx, &wx,
                  XmNy, &wy,
                  NULL);

    if (x != wx || y != wy) {
        move = True;
    }
    if (wd != ww || ht != wh) {
        resize = True;
    }
    if (!move && !resize) {
        return;
    }

    if (need_to_unmanage) {
        if (!resize) {
            mapped_when_managed = w->core.mapped_when_managed;
            w->core.mapped_when_managed = False;
        }
        saved_focus_widget = get_shell_focused_widget(w);
        XtUnmanageChild(w);
    }

    /* GES: AVH's hack:
     * Motif ignores attempts to move a toplevel window to 0,0.
     * Instead we set the position to 1,1. The expected value is
     * returned by Frame.getBounds() since it uses the internally
     * held rectangle rather than querying the peer.
     * N.B. [pauly, 9/97]  This is only required for wm shells
     * under the Motif Window Manager (MWM), not for any others.
     * Note. Utilizes C short-circuiting if w is not a wm shell.
     */
    if ((x == 0) && (y == 0) &&
        (XtIsSubclass(w, wmShellWidgetClass)) &&
        (XmIsMotifWMRunning(w))) {
        XtVaSetValues(w, XmNx, 1, XmNy, 1, NULL);
    }

    if (move && !resize) {
        XtVaSetValues(w, XmNx, x, XmNy, y, NULL);

    } else if (resize && !move) {
        XtVaSetValues(w,
                      XmNwidth, (wd > 0) ? wd : 1,
                      XmNheight, (ht > 0) ? ht : 1,
                      NULL);

    } else  {
        XtVaSetValues(w,
                  XmNx, x,
                  XmNy, y,
                  XmNwidth, (wd > 0) ? wd : 1,
                  XmNheight, (ht > 0) ? ht : 1,
                  NULL);
    }

    if (need_to_unmanage) {
        XtManageChild(w);
        if (!resize) {
            w->core.mapped_when_managed = mapped_when_managed;
        }
        if (saved_focus_widget != NULL) {
            Boolean result = XmProcessTraversal(saved_focus_widget, XmTRAVERSE_CURRENT);
            if (!result)
            {
                Widget shell = saved_focus_widget;
                while(shell != NULL && !XtIsShell(shell)) {
                    shell = XtParent(shell);
                }
                XtSetKeyboardFocus(shell, saved_focus_widget);
            }
        }
    }
}

void
awt_util_hide(Widget w)
{
    if (w == NULL) {
        JNIEnv *env = (JNIEnv *)JNU_GetEnv(jvm, JNI_VERSION_1_2);
        JNU_ThrowNullPointerException(env,"NullPointerException");
        return;
    }
    XtSetMappedWhenManaged(w, False);
}

void
awt_util_show(Widget w)
{
/*
    extern Boolean  scrollBugWorkAround;
*/
    if (w == NULL) {
        JNIEnv *env = (JNIEnv *)JNU_GetEnv(jvm, JNI_VERSION_1_2);
        JNU_ThrowNullPointerException(env,"NullPointerException");
        return;
    }
    XtSetMappedWhenManaged(w, True);
/*
  XXX: causes problems on 2.5
    if (!scrollBugWorkAround) {
        awt_setWidgetGravity(w, StaticGravity);
    }
*/
}

void
awt_util_enable(Widget w)
{
    XtSetSensitive(w, True);
}

void
awt_util_disable(Widget w)
{
    XtSetSensitive(w, False);
}

void
awt_util_mapChildren(Widget w, void (*func)(Widget,void *),
                     int32_t applyToCurrent, void *data) {
    WidgetList                  wlist;
    Cardinal                    wlen = 0;
    Cardinal                    i;

    /* The widget may have been destroyed by another thread. */
    if ((w == NULL) || (!XtIsObject(w)) || (w->core.being_destroyed))
        return;

    if (applyToCurrent != 0) {
        (*func)(w, data);
    }
    if (!XtIsComposite(w)) {
        return;
    }

    XtVaGetValues(w,
                  XmNchildren, &wlist,
                  XmNnumChildren, &wlen,
                  NULL);
    if (wlen > 0) {
        for (i=0; i < wlen; i++) {
            awt_util_mapChildren(wlist[i], func, 1, data);
        }
    }
}

void
awt_changeAttributes(Display *dpy, Widget w, unsigned long mask,
                     XSetWindowAttributes *xattr)
{
    WidgetList          wlist;
    Cardinal            wlen = 0;
    Cardinal            i;

    if (XtWindow(w) && XtIsRealized(w)) {
        XChangeWindowAttributes(dpy,
                                XtWindow(w),
                                mask,
                                xattr);
    } else {
        return;
    }
    XtVaGetValues(w,
                  XmNchildren, &wlist,
                  XmNnumChildren, &wlen,
                  NULL);
    for (i = 0; i < wlen; i++) {
        if (XtWindow(wlist[i]) && XtIsRealized(wlist[i])) {
            XChangeWindowAttributes(dpy,
                                    XtWindow(wlist[i]),
                                    mask,
                                    xattr);
        }
    }
}

static Widget prevWgt = NULL;

static void
DestroyCB(Widget w, XtPointer client_data, XtPointer call_data) {
    if (prevWgt == w) {
        prevWgt = NULL;
    }
}

int32_t
awt_util_setCursor(Widget w, Cursor c) {
    static Cursor prevCur = None;

    if (XtIsRealized(w)) {
        unsigned long valuemask = 0;
        XSetWindowAttributes    attributes;

        valuemask = CWCursor;
        if (prevWgt != NULL) {
            attributes.cursor = None;
            XChangeWindowAttributes(awt_display,
                        XtWindow(prevWgt),
                        valuemask,
                        &attributes);
        }

        if (c == None) {
            c = prevCur;
            if (w != NULL) {
                XtAddCallback(w, XmNdestroyCallback, DestroyCB, NULL);
            }
            prevWgt = w;
        } else {
            prevCur = c;
            prevWgt = NULL;
        }
        attributes.cursor = c;
        XChangeWindowAttributes(awt_display,
                                XtWindow(w),
                                valuemask,
                                &attributes);
        XFlush(awt_display);
        return 1;
    } else
        return 0;
}

void
awt_util_convertEventTimeAndModifiers(XEvent *event,
                                      ConvertEventTimeAndModifiers *output) {
    switch (event->type) {
    case KeyPress:
    case KeyRelease:
        output->when = awt_util_nowMillisUTC_offset(event->xkey.time);
        output->modifiers = getModifiers(event->xkey.state, 0, 0);
        break;
    case ButtonPress:
    case ButtonRelease:
        output->when = awt_util_nowMillisUTC_offset(event->xbutton.time);
        output->modifiers = getModifiers(event->xbutton.state,
            getButton(event->xbutton.button), 0);
        break;
    default:
        output->when = awt_util_nowMillisUTC();
        output->modifiers =0;
        break;
    }
}


/*
  Part fix for bug id 4017222. Return the widget at the given screen coords
  by searching the widget tree beginning at root. This function will return
  null if the pointer is not over the root widget or child of the root widget.

  Additionally, this function will only return a Widget with non-nil XmNuserData.
  In 1.2.1, when the mouse was dragged over a Choice component, this function
  returned the GadgetButton associated with the Choice.  This GadgetButton had
  nil as its XmNuserData.  This lead to a crash when the nil XmNuserData was
  extracted and used as a reference to a peer.  Ooops.
  Now the GadgetButton is not returned and the function goes on to find a widget
  which contains the correct peer reference in XmNuserData.
*/
Widget
awt_WidgetAtXY(Widget root, Position pointerx, Position pointery) {
  Widget answer = NULL;

  if(!root) return NULL;

  if(XtIsComposite(root)) {
    int32_t i=0;
    WidgetList wl=NULL;
    Cardinal wlen=0;

    XtVaGetValues(root, XmNchildren, &wl, XmNnumChildren, &wlen, NULL);

    if(wlen>0) {
      for(i=0; i<wlen && !answer; i++) {
        answer = awt_WidgetAtXY(wl[i], pointerx, pointery);
      }
    }
  }

  if(!answer) {
    Position wx=0, wy=0;
    Dimension width=0, height=0;
    int32_t lastx=0, lasty=0;
    XtPointer widgetUserData=NULL;

    XtVaGetValues(root, XmNwidth, &width, XmNheight, &height,
                  XmNuserData, &widgetUserData,
                  NULL);

    XtTranslateCoords(root, 0, 0, &wx, &wy);
    lastx = wx + width;
    lasty = wy + height;

    if(pointerx>=wx && pointerx<=lastx && pointery>=wy && pointery<=lasty &&
           widgetUserData)
        answer = root;
  }

  return answer;
}
#ifdef __linux__


#define MAXARGS 10
static Arg xic_vlist[MAXARGS];
static Arg status_vlist[MAXARGS];
static Arg preedit_vlist[MAXARGS];

#define NO_ARG_VAL -1
#define SEPARATOR_HEIGHT 2

static XFontSet extract_fontset(XmFontList);

/* get_im_height: returns height of the input method status area in pixels.
 *
 * This function assumes that if any XIM related information cannot be
 * queried then the app must not have an input method status area in the
 * current locale and returns zero as the status area height
 */

static XtPointer*
get_im_info_ptr(Widget  w,
                Boolean create)
{
  Widget p;
  XmVendorShellExtObject ve;
  XmWidgetExtData extData;
  XmImShellInfo im_info;
  XmImDisplayInfo xim_info;

  if (w == NULL)
    return NULL;

  p = w;
  while (!XtIsShell(p))
    p = XtParent(p);

  /* Check extension data since app could be attempting to create
   * a text widget as child of menu shell.  This is illegal, and will
   * be detected later, but check here so we don't core dump.
   */
  if ((extData = _XmGetWidgetExtData((Widget)p, XmSHELL_EXTENSION)) == NULL)
    return NULL;

  ve = (XmVendorShellExtObject) extData->widget;

  return &ve->vendor.im_info;
}

static XmImShellInfo
get_im_info(Widget w,
            Boolean create)
{
  XmImShellInfo* ptr = (XmImShellInfo *) get_im_info_ptr(w, create);
  if (ptr != NULL)
    return *ptr;
  else
    return NULL;
}

#endif /* !linux */

Widget
awt_util_getXICStatusAreaWindow(Widget w)
{
    while (!XtIsShell(w)){
        w = XtParent(w);
    }
    return w;
}

#ifdef __linux__
static XRectangle geometryRect;
XVaNestedList awt_util_getXICStatusAreaList(Widget w)
{
    XIC xic;
    XmImXICInfo icp;
    XmVendorShellExtObject ve;
    XmWidgetExtData extData;
    XmImShellInfo im_info;
    XmFontList fl=NULL;

    XRectangle  *ssgeometry = &geometryRect;
    XRectangle geomRect ;
    XRectangle *im_rect;
    XFontSet   *im_font;

    Pixel bg ;
    Pixel fg ;
    Dimension height, width ;
    Position x,y ;
    Pixmap bpm, *bpmout ;

    XVaNestedList list = NULL;

    char *ret;
    Widget p=w;

    while (!XtIsShell(p)) {
        p = XtParent(p);
    }

    XtVaGetValues(p,
        XmNx, &x,
        XmNy, &y,
        XmNwidth, &width,
        XmNheight, &height,
        XmNbackgroundPixmap, &bpm,
        NULL);

    extData = _XmGetWidgetExtData((Widget) p, XmSHELL_EXTENSION);
    if (extData == NULL) {
        return NULL;
    }
    ve = (XmVendorShellExtObject) extData->widget;
    im_info = get_im_info(w, False);

    if (im_info == NULL) {
        return NULL;
    } else {
        icp = im_info->iclist;
    }

    if (icp) {
        /*
         * We have at least a textfield/textarea in the frame, use the
         * first one.
         */
        ssgeometry->x = 0;
        ssgeometry->y = height - icp->sp_height;
        ssgeometry->width = icp->status_width;
        ssgeometry->height = icp->sp_height;
        XtVaGetValues(w, XmNbackground, &bg, NULL);
        XtVaGetValues(w, XmNforeground, &fg, NULL);
        XtVaGetValues(w, XmNfontList, &fl, NULL);
        /*
         * use motif TextComponent's resource
         */

        list = XVaCreateNestedList(0,
                        XNFontSet, extract_fontset(fl),
                        XNArea, ssgeometry,
                        XNBackground, bg,
                        XNForeground, fg,
                        NULL);
   }
   return list ;
}

static XFontSet
extract_fontset(XmFontList fl)
{
    XmFontContext context;
    XmFontListEntry next_entry;
    XmFontType type_return;
    XtPointer tmp_font;
    XFontSet first_fs = NULL;
    char *font_tag;

    if (!XmFontListInitFontContext(&context, fl))
        return NULL;

    do {
        next_entry = XmFontListNextEntry(context);
        if (next_entry) {
            tmp_font = XmFontListEntryGetFont(next_entry, &type_return);
            if (type_return == XmFONT_IS_FONTSET) {
                font_tag = XmFontListEntryGetTag(next_entry);
                if (!strcmp(font_tag, XmFONTLIST_DEFAULT_TAG)) {
                    XmFontListFreeFontContext(context);
                    XtFree(font_tag);
                    return (XFontSet) tmp_font;
                }
                XtFree(font_tag);
                if (first_fs == NULL)
                    first_fs = (XFontSet) tmp_font;
            }
        }
    } while (next_entry);

    XmFontListFreeFontContext(context);
    return first_fs;
}
#endif

/*the caller does have the responsibility to free the memory return
  from this function...*/
char* awt_util_makeWMMenuItem(char *target, Atom protocol){
    char        *buf = NULL;
    int32_t         buflen = 0;

    /*a label in a menuitem is not supposed to be a FullOfSpaceString... */
    buflen = strlen(target) * 3;
    buf = (char*)malloc(buflen + 20);
    if (buf == NULL){
        JNU_ThrowOutOfMemoryError((JNIEnv *)JNU_GetEnv(jvm, JNI_VERSION_1_2), NULL);
    }
    else{
      int32_t   off = 0;
      char  *ptr = target;
      while ((off < (buflen - 20)) && (*ptr != '\0')){
        if (*ptr == ' '){
          *(buf + off++) = 0x5c;
        }
        *(buf + off++) = *ptr++;
      }
      sprintf(buf + off, " f.send_msg %ld", protocol);
    }
    return buf;
}

/*
 * This callback proc is installed via setting the XmNinsertPosition
 * resource on a widget. It ensures that components added
 * to a widget are inserted in the correct z-order position
 * to match up with their peer/target ordering in Container.java
 */
Cardinal
awt_util_insertCallback(Widget w)
{
    jobject peer;
    WidgetList children;
    Cardinal num_children;
    Widget parent;
    XtPointer userdata;
    Cardinal index;
    int32_t pos;
    JNIEnv *env = (JNIEnv *)JNU_GetEnv(jvm, JNI_VERSION_1_2);

    parent = XtParent(w);
    XtVaGetValues(parent,
                  XmNnumChildren, &num_children,
                  XmNchildren, &children,
                  NULL);
    XtVaGetValues(w, XmNuserData, &userdata, NULL);

    index = num_children;         /* default is to add to end */

    if (userdata != NULL) {
        peer = (jobject) userdata;

        // SECURITY: We are running on the privileged toolkit thread.
        //           The peer must *NOT* call into user code
        pos = (int32_t) JNU_CallMethodByName(env
                                          ,NULL
                                          ,(jobject) peer
                                          ,"getZOrderPosition_NoClientCode"
                                          ,"()I").i;
        if ((*env)->ExceptionOccurred(env)) {
            (*env)->ExceptionDescribe(env);
            (*env)->ExceptionClear(env);
        }
        index = (Cardinal) (pos != -1 ? pos : num_children);
    }
    return index;
}

void
awt_util_consumeAllXEvents(Widget widget)
{
    /* Remove all queued X Events for the window of the widget. */

#define ALL_EVENTS_MASK 0xFFFF

    XEvent xev;

    XFlush(awt_display);
    while (XCheckWindowEvent(awt_display, XtWindow(widget),
               ALL_EVENTS_MASK, &xev)) ;
}

#endif /* XAWT */
/**
 * Gets the thread we are currently executing on
 */
jobject
awtJNI_GetCurrentThread(JNIEnv *env) {
    static jclass threadClass = NULL;
    static jmethodID currentThreadMethodID = NULL;

    jobject currentThread = NULL;

    /* Initialize our java identifiers once. Checking before locking
     * is a huge performance win.
     */
    if (threadClass == NULL) {
        // should enter a monitor here...
        Boolean err = FALSE;
        if (threadClass == NULL) {
            jclass tc = (*env)->FindClass(env, "java/lang/Thread");
            threadClass = (*env)->NewGlobalRef(env, tc);
            if (threadClass != NULL) {
                currentThreadMethodID = (*env)->GetStaticMethodID(env,
                                              threadClass,
                                              "currentThread",
                                              "()Ljava/lang/Thread;"
                                                );
            }
        }
        if (currentThreadMethodID == NULL) {
            threadClass = NULL;
            err = TRUE;
        }
        if (err) {
            return NULL;
        }
    } /* threadClass == NULL*/

    currentThread = (*env)->CallStaticObjectMethod(
                        env, threadClass, currentThreadMethodID);
    DASSERT(!((*env)->ExceptionOccurred(env)));
    /*JNU_PrintString(env, "getCurrentThread() -> ", JNU_ToString(env,currentThread));*/
    return currentThread;
} /* awtJNI_GetCurrentThread() */

void
awtJNI_ThreadYield(JNIEnv *env) {

    static jclass threadClass = NULL;
    static jmethodID yieldMethodID = NULL;

    /* Initialize our java identifiers once. Checking before locking
     * is a huge performance win.
     */
    if (threadClass == NULL) {
        // should enter a monitor here...
        Boolean err = FALSE;
        if (threadClass == NULL) {
            jclass tc = (*env)->FindClass(env, "java/lang/Thread");
            threadClass = (*env)->NewGlobalRef(env, tc);
            (*env)->DeleteLocalRef(env, tc);
            if (threadClass != NULL) {
                yieldMethodID = (*env)->GetStaticMethodID(env,
                                              threadClass,
                                              "yield",
                                              "()V"
                                                );
            }
        }
        if (yieldMethodID == NULL) {
            threadClass = NULL;
            err = TRUE;
        }
        if (err) {
            return;
        }
    } /* threadClass == NULL*/

    (*env)->CallStaticVoidMethod(env, threadClass, yieldMethodID);
    DASSERT(!((*env)->ExceptionOccurred(env)));
} /* awtJNI_ThreadYield() */

#ifndef XAWT

void
awt_util_cleanupBeforeDestroyWidget(Widget widget)
{
    /* Bug 4017222: Drag processing uses global prevWidget. */
    if (widget == prevWidget) {
        prevWidget = NULL;
    }
}

static Boolean timeStampUpdated = False;

static int32_t
isTimeStampUpdated(void* p) {
    return timeStampUpdated;
}

static void
propertyChangeEventHandler(Widget w, XtPointer client_data,
                           XEvent* event, Boolean* continue_to_dispatch) {
    timeStampUpdated = True;
}

/*
 * If the application doesn't receive events with timestamp for a long time
 * XtLastTimestampProcessed() will return out-of-date value. This may cause
 * selection handling routines to fail (see BugTraq ID 4085183).
 * This routine is to resolve this problem. It queries the current X server
 * time by appending a zero-length data to a property as prescribed by
 * X11 Reference Manual.
 * Note that this is a round-trip request, so it can be slow. If you know
 * that the Xt timestamp is up-to-date use XtLastTimestampProcessed().
 */
Time
awt_util_getCurrentServerTime() {

    JNIEnv *env = (JNIEnv *)JNU_GetEnv(jvm, JNI_VERSION_1_2);
    static Atom _XA_JAVA_TIME_PROPERTY_ATOM = 0;
    Time server_time = 0;

    AWT_LOCK();

    if (_XA_JAVA_TIME_PROPERTY_ATOM == 0) {
        XtAddEventHandler(awt_root_shell, PropertyChangeMask, False,
                          propertyChangeEventHandler, NULL);
        _XA_JAVA_TIME_PROPERTY_ATOM = XInternAtom(awt_display, "_SUNW_JAVA_AWT_TIME", False);
    }

    timeStampUpdated = False;
    XChangeProperty(awt_display, XtWindow(awt_root_shell),
                    _XA_JAVA_TIME_PROPERTY_ATOM, XA_ATOM, 32, PropModeAppend,
                    (unsigned char *)"", 0);
    XFlush(awt_display);

    if (awt_currentThreadIsPrivileged(env)) {
        XEvent event;
        XMaskEvent(awt_display, PropertyChangeMask, &event);
        XtDispatchEvent(&event);
    } else {
        awt_MToolkit_modalWait(isTimeStampUpdated, NULL);
    }
    server_time = XtLastTimestampProcessed(awt_display);

    AWT_UNLOCK();

    return server_time;
}

/*
 * This function is stolen from /src/solaris/hpi/src/system_md.c
 * It is used in setting the time in Java-level InputEvents
 */
jlong
awt_util_nowMillisUTC()
{
    struct timeval t;
    gettimeofday(&t, NULL);
    return ((jlong)t.tv_sec) * 1000 + (jlong)(t.tv_usec/1000);
}

/*
 * This function converts between the X server time (number of milliseconds
 * since the last server reset) and the UTC time for the 'when' field of an
 * InputEvent (or another event type with a timestamp).
 */
jlong
awt_util_nowMillisUTC_offset(Time server_offset)
{
    /*
     * Because Time is of type 'unsigned long', it is possible that Time will
     * never wrap when using 64-bit Xlib. However, if a 64-bit client
     * connects to a 32-bit server, I suspect the values will still wrap. So
     * we should not attempt to remove the wrap checking even if _LP64 is
     * true.
     */
    static const jlong WRAP_TIME_MILLIS = (jlong)((uint32_t)-1);
    static jlong reset_time_utc;

    jlong current_time_utc = awt_util_nowMillisUTC();

    if ((current_time_utc - reset_time_utc) > WRAP_TIME_MILLIS) {
        reset_time_utc = awt_util_nowMillisUTC() -
            awt_util_getCurrentServerTime();
    }

    return reset_time_utc + server_offset;
}

void awt_util_do_wheel_scroll(Widget scrolled_window, jint scrollType,
                              jint scrollAmt, jint wheelAmt) {
    Widget scrollbar = NULL;
    int value;
    int slider_size;
    int min;
    int max;
    int increment;
    int page_increment;
    int scrollAdjustment;
    int newValue;

    /* TODO:
     * If a TextArea's scrollbar policy is set to never, it should still
     * wheel scroll, but right now it doesn't.
     */

    scrollbar = awt_util_get_scrollbar_to_scroll(scrolled_window);
    if (scrollbar == NULL) { /* no suitable scrollbar for scrolling */
        return;
    }

    XtVaGetValues(scrollbar, XmNvalue, &value,
                             XmNsliderSize, &slider_size,
                             XmNminimum, &min,
                             XmNmaximum, &max,
                             XmNincrement, &increment,
                             XmNpageIncrement, &page_increment, NULL);

    if (scrollType == java_awt_event_MouseWheelEvent_WHEEL_BLOCK_SCROLL) {
        scrollAdjustment = page_increment;
    }
    else { // WHEEL_UNIT_SCROLL
        scrollAdjustment = increment * scrollAmt;
    }

    if (wheelAmt < 0) {
        // Don't need to check that newValue < max - slider_size because
        // newValue < current value.  If scrollAmt is ever user-configurable,
        // we'll have to check this.
        newValue = MAX(min, value+ (scrollAdjustment * wheelAmt));
    }
    else {
        newValue = MIN(max - slider_size,
                       value + (scrollAdjustment * wheelAmt));
    }

    XtVaSetValues(scrollbar, XmNvalue, newValue, NULL);
    XtCallCallbacks(scrollbar, XmNvalueChangedCallback, NULL);
}


/* Given a ScrollWindow widget, return the Scrollbar that the wheel should
 * scroll.  A null return value means that the ScrollWindow has a scrollbar
 * display policy of none, or that neither scrollbar can be scrolled.
 */
Widget awt_util_get_scrollbar_to_scroll(Widget scrolled_window) {
    Widget scrollbar = NULL;
    int value;
    int slider_size;
    int min;
    int max;

    /* first, try the vertical scrollbar */
    XtVaGetValues(scrolled_window, XmNverticalScrollBar, &scrollbar, NULL);
    if (scrollbar != NULL) {
        XtVaGetValues(scrollbar, XmNvalue, &value,
                                 XmNsliderSize, &slider_size,
                                 XmNminimum, &min,
                                 XmNmaximum, &max, NULL);
        if (slider_size < max - min) {
            return scrollbar;
        }
    }

    /* then, try the horiz */
    XtVaGetValues(scrolled_window, XmNhorizontalScrollBar, &scrollbar, NULL);
    if (scrollbar != NULL) {
        XtVaGetValues(scrollbar, XmNvalue, &value,
                                 XmNsliderSize, &slider_size,
                                 XmNminimum, &min,
                                 XmNmaximum, &max, NULL);
        if (slider_size < max - min) {
            return scrollbar;
        }
    }
    /* neither is suitable for scrolling */
    return NULL;
}

EmbeddedFrame *theEmbeddedFrameList = NULL;

static void awt_util_updateXtCoordinatesForEmbeddedFrame(Widget ef)
{
    Window ef_window;
    Window win;
    int32_t x, y;
    ef_window = XtWindow(ef);
    if (ef_window != None) {
        if (XTranslateCoordinates(awt_display, ef_window,
            RootWindowOfScreen(XtScreen(ef)),
            0, 0, &x, &y, &win)) {
            DTRACE_PRINTLN("correcting coordinates");
            ef->core.x = x;
            ef->core.y = y;
        }
    }
}

Boolean awt_util_processEventForEmbeddedFrame(XEvent *ev)
{
    EmbeddedFrame *ef;
    Boolean dummy;
    Boolean eventProcessed = False;
    switch (ev->type) {
    case FocusIn:
    case FocusOut:
        ef = theEmbeddedFrameList;
        while (ef != NULL) {
            if (ef->frameContainer == ev->xfocus.window) {
                eventProcessed = True;
                if (isXEmbedActiveByWindow(XtWindow(ef->embeddedFrame))) {
                    return True;
                }
                // pretend that the embedded frame gets a focus event
                // the event's window field is not the same as
                // the embeddedFrame's widget, but luckily the shellEH
                // doesnt seem to care about this.
                shellEH(ef->embeddedFrame, ef->javaRef, ev, &dummy);
            }
            ef = ef->next;
        }
        return eventProcessed;
    case ConfigureNotify:
        for (ef = theEmbeddedFrameList; ef != NULL; ef = ef->next) {
            awt_util_updateXtCoordinatesForEmbeddedFrame(ef->embeddedFrame);
        }
        return True;
    }
    return False;
}

void awt_util_addEmbeddedFrame(Widget embeddedFrame, jobject javaRef)
{
    EmbeddedFrame *ef, *eflist;
    Atom WM_STATE;
    Window win;
    Window parent, root;
    Window *children;
    uint32_t nchildren;
    Atom type = None;
    int32_t format;
    unsigned long nitems, after;
    unsigned char * data;
    XWindowAttributes win_attributes;

    WM_STATE = XInternAtom(awt_display, "WM_STATE", True);
    if (WM_STATE == None) {
        return;
    }
    win = XtWindow(embeddedFrame);
    if (win == None)
        return;
    /*
     * according to XICCM, we search our toplevel window
     * by looking for WM_STATE property
     */
    while (True) {
        if (!XQueryTree(awt_display, win, &root, &parent,
            &children, &nchildren)) {
            return;
        }
        if (children) {
            XFree(children);
        }
        if (parent == NULL || parent == root) {
            return;
        }
        win = parent;
        /*
         * Add StructureNotifyMask through hierarchy upto toplevel
         */
        XGetWindowAttributes(awt_display, win, &win_attributes);
        XSelectInput(awt_display, win, win_attributes.your_event_mask |
                StructureNotifyMask);

        if (XGetWindowProperty(awt_display, win, WM_STATE,
                0, 0, False, AnyPropertyType,
                &type, &format, &nitems, &after, &data) == Success) {
            XFree(data);
            if (type) {
                break;
            }
        }
    }
    ef = (EmbeddedFrame *) malloc(sizeof(EmbeddedFrame));
    if (ef == NULL) {
        JNU_ThrowOutOfMemoryError((JNIEnv *)JNU_GetEnv(jvm, JNI_VERSION_1_2),
            "OutOfMemory in awt_util_addEmbeddedFrame");
        return;
    }
    ef->embeddedFrame = embeddedFrame;
    ef->frameContainer = win;
    ef->javaRef = javaRef;
    ef->eventSelectedPreviously = False;
    /* initialize the xt coordinates */
    awt_util_updateXtCoordinatesForEmbeddedFrame(embeddedFrame);

    /*
     * go through the exisiting embedded frames see if we have
     * already selected the event on the same frameContainer
     */
    eflist = theEmbeddedFrameList;
    while (eflist != NULL) {
        if (eflist->frameContainer == win) {
            break;
        }
        eflist = eflist->next;
    }
    if (eflist != NULL) {
        /*
         * we already have a embedded frame selecting this container's
         * event, we remember its eventSelectedPreviously value
         * so that we know whether to deselect later when we are removed
         */
        ef->eventSelectedPreviously = eflist->eventSelectedPreviously;
    } else {
        XGetWindowAttributes(awt_display, ef->frameContainer,
            &win_attributes);
        XSelectInput(awt_display, ef->frameContainer,
                     win_attributes.your_event_mask | FocusChangeMask);
    }

    /* ef will become the head of the embedded frame list */
    ef->next = theEmbeddedFrameList;
    if (theEmbeddedFrameList != NULL) {
        theEmbeddedFrameList->prev = ef;
    }
    ef->prev = NULL;
    theEmbeddedFrameList = ef;
}

void awt_util_delEmbeddedFrame(Widget embeddedFrame)
{
    EmbeddedFrame *ef = theEmbeddedFrameList;
    Window frameContainer;
    XWindowAttributes win_attributes;
    Boolean needToDeselect;

    while (ef != NULL) {
        if (ef->embeddedFrame == embeddedFrame) {
            break;
        }
        ef = ef->next;
    }
    if (ef == NULL) { /* cannot find specified embedded frame */
        return;
    }
    /* remove ef from link list EmbeddedFrameList */
    if (ef->prev) {
        ef->prev->next = ef->next;
    }
    if (ef->next) {
        ef->next->prev = ef->prev;
    }
    if (theEmbeddedFrameList == ef) {
        theEmbeddedFrameList = ef->next;
    }

    frameContainer = ef->frameContainer;
    needToDeselect = ef->eventSelectedPreviously ? False : True;
    free(ef);
    if (!needToDeselect) {
        return;
    }
    /*
     * now decide whether we need to stop listenning event for
     * frameContainer
     */
    ef = theEmbeddedFrameList;
    while (ef != NULL) {
        if (ef->frameContainer == frameContainer) {
            break;
        }
        ef = ef->next;
    }
    if (ef == NULL)  {
        /*
         * if we get here, no one is interested in this frame
         * and StructureNotify was not selected by anyone else
         * so we deselect it
         */
        DTRACE_PRINTLN("remove event from frame");
        XGetWindowAttributes(awt_display, frameContainer, &win_attributes);
        XSelectInput(awt_display, frameContainer,
            win_attributes.your_event_mask &
                (~FocusChangeMask));
    }
}

#endif /* XAWT */

void awt_util_debug_init() {
#if defined(DEBUG)
    DTrace_Initialize();
#endif
}

static void awt_util_debug_fini() {
#if defined(DEBUG)
    DTrace_Shutdown();
#endif
}
