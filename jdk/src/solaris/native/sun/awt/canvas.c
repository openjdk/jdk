/*
 * Copyright 1995-2005 Sun Microsystems, Inc.  All Rights Reserved.
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
#include <sys/time.h> /* timeval */

#define XK_KATAKANA
#include <X11/keysym.h>     /* standard X keysyms */
#include <X11/DECkeysym.h>  /* DEC vendor-specific */
#include <X11/Sunkeysym.h>  /* Sun vendor-specific */
#include <X11/ap_keysym.h>  /* Apollo (HP) vendor-specific */
/*
 * #include <X11/HPkeysym.h>    HP vendor-specific
 * I checked HPkeysym.h into the workspace because it ships
 * with X11R6.4.2 (and later) but not with X11R6.4.1.
 * So, it ought to ship with Solaris 9, but not Solaris 8.
 * Same deal for Linux - newer versions of XFree have it.
 *
 * Note: this is mainly done for the hp keysyms; it does NOT
 * give us the osf keysyms that are also defined in HPkeysym.h.
 * This is because we are already getting /Xm/VirtKeys.h
 * from awt_p.h <- /Xm/Xm.h <- /Xm/VirtKeys.h, and VirtKeys.h
 * #defines _OSF_Keysyms before we get here.  We are
 * missing a couple of osf keysyms because of this,
 * so I have #defined them below.
 */
#include "HPkeysym.h"   /* HP vendor-specific */

#include <Xm/Display.h>
#include <ctype.h>
#include "java_awt_Frame.h"
#include "java_awt_Component.h"
#include "java_awt_AWTEvent.h"
#include "java_awt_event_KeyEvent.h"
#include "java_awt_event_FocusEvent.h"
#include "java_awt_event_MouseEvent.h"
#include "java_awt_event_MouseWheelEvent.h"
#include "java_awt_event_InputEvent.h"
#include "java_awt_event_WindowEvent.h"
#include "sun_awt_motif_MComponentPeer.h"
#include "color.h"
#include "canvas.h"
#include "awt_Cursor.h"
#include "VDrawingArea.h"
#include "XDrawingArea.h"
#include "awt_Component.h"
#include "awt_AWTEvent.h"
#include "awt_Event.h"
#include "awt_KeyboardFocusManager.h"
#include "awt_MToolkit.h"
#include "awt_TopLevel.h"
#include "awt_util.h"

#include <jni.h>
#include <jni_util.h>
#include <jvm.h>
#include <jawt.h>

#ifdef NDEBUG   /* NDEBUG overrides DEBUG */
#undef DEBUG
#endif

/*
 * Two osf keys are not defined in standard keysym.h,
 * /Xm/VirtKeys.h, or HPkeysym.h, so I added them below.
 * I found them in /usr/openwin/lib/X11/XKeysymDB
 */
#ifndef osfXK_Prior
#define osfXK_Prior 0x1004FF55
#endif
#ifndef osfXK_Next
#define osfXK_Next 0x1004FF56
#endif
/*
 * osfXK_Escape is defined in HPkeysym.h, but not in
 * /Xm/VirtKeys.h, so I added it below.  It is also in
 * /usr/openwin/lib/X11/XKeysymDB
 * Note: it is in /Xm/VirtKeys.h in the AWT motif workspace,
 * but not in /usr/local/Motif/include/Xm/VirtKeys.h
 * on the Solaris 7, 8, or 9 machines I tried.
 */
#ifndef osfXK_Escape
#define osfXK_Escape 0x1004FF1B
#endif

extern struct MComponentPeerIDs mComponentPeerIDs;
extern struct AWTEventIDs awtEventIDs;
extern struct KeyEventIDs keyEventIDs;
extern struct InputEventIDs inputEventIDs;
extern struct ComponentIDs componentIDs;
extern struct KeyboardFocusManagerIDs keyboardFocusManagerIDs;

#ifdef DEBUG
static Boolean debugKeys = False;
#endif

jint awt_multiclick_smudge = 4;

extern Widget drag_source;

Widget prevWidget = NULL; /* for bug fix 4017222 */

FocusListElt *focusList = NULL, *focusListEnd = NULL;

jweak forGained = NULL;

extern Boolean scrollBugWorkAround;
extern jobject currentX11InputMethodInstance;
extern Window  currentFocusWindow;
extern Boolean awt_x11inputmethod_lookupString(XKeyPressedEvent *, KeySym *);
Boolean awt_UseType4Patch = True;
Boolean awt_ServerDetected = False;
Boolean awt_IsXsun = False;
Boolean awt_UseXKB = False;

void awt_post_java_key_event(XtPointer client_data, jint id,
                             XEvent *xevent, Time when, jint keycode,
                             jchar keychar, jint modifiers,
                             jint keyLocation, XEvent *anEvent);
void awt_post_java_focus_event(XtPointer client_data, jint id, jobject cause,
                               XEvent *event);
void awt_post_java_mouse_event(XtPointer client_data, jint id,
                               XEvent *event, Time when, jint modifiers,
                               jint x, jint y,
                               jint xAbs, jint yAbs,
                               jint clickcount, Boolean popuptrigger,
                               jint wheelAmt, jint button);

typedef struct KEYMAP_ENTRY {
    jint awtKey;
    KeySym x11Key;
    Boolean mapsToUnicodeChar;
    jint keyLocation;
} KeymapEntry;

/* NB: XK_R? keysyms are for Type 4 keyboards.
 * The corresponding XK_F? keysyms are for Type 5
 *
 * Note: this table must be kept in sorted order, since it is traversed
 * according to both Java keycode and X keysym.  There are a number of
 * keycodes that map to more than one corresponding keysym, and we need
 * to choose the right one.  Unfortunately, there are some keysyms that
 * can map to more than one keycode, depending on what kind of keyboard
 * is in use (e.g. F11 and F12).
 */

KeymapEntry keymapTable[] =
{
    {java_awt_event_KeyEvent_VK_A, XK_a, TRUE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_B, XK_b, TRUE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_C, XK_c, TRUE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_D, XK_d, TRUE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_E, XK_e, TRUE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_F, XK_f, TRUE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_G, XK_g, TRUE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_H, XK_h, TRUE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_I, XK_i, TRUE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_J, XK_j, TRUE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_K, XK_k, TRUE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_L, XK_l, TRUE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_M, XK_m, TRUE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_N, XK_n, TRUE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_O, XK_o, TRUE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_P, XK_p, TRUE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_Q, XK_q, TRUE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_R, XK_r, TRUE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_S, XK_s, TRUE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_T, XK_t, TRUE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_U, XK_u, TRUE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_V, XK_v, TRUE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_W, XK_w, TRUE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_X, XK_x, TRUE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_Y, XK_y, TRUE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_Z, XK_z, TRUE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},

    /* TTY Function keys */
    {java_awt_event_KeyEvent_VK_BACK_SPACE, XK_BackSpace, TRUE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_TAB, XK_Tab, TRUE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_CLEAR, XK_Clear, FALSE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_ENTER, XK_Return, TRUE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_ENTER, XK_Linefeed, TRUE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_PAUSE, XK_Pause, FALSE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_PAUSE, XK_F21, FALSE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_PAUSE, XK_R1, FALSE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_SCROLL_LOCK, XK_Scroll_Lock, FALSE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_SCROLL_LOCK, XK_F23, FALSE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_SCROLL_LOCK, XK_R3, FALSE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_ESCAPE, XK_Escape, TRUE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},

    /* Other vendor-specific versions of TTY Function keys */
    {java_awt_event_KeyEvent_VK_BACK_SPACE, osfXK_BackSpace, TRUE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_CLEAR, osfXK_Clear, FALSE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_ESCAPE, osfXK_Escape, TRUE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},

    /* Modifier keys */
    {java_awt_event_KeyEvent_VK_SHIFT, XK_Shift_L, FALSE, java_awt_event_KeyEvent_KEY_LOCATION_LEFT},
    {java_awt_event_KeyEvent_VK_SHIFT, XK_Shift_R, FALSE, java_awt_event_KeyEvent_KEY_LOCATION_RIGHT},
    {java_awt_event_KeyEvent_VK_CONTROL, XK_Control_L, FALSE, java_awt_event_KeyEvent_KEY_LOCATION_LEFT},
    {java_awt_event_KeyEvent_VK_CONTROL, XK_Control_R, FALSE, java_awt_event_KeyEvent_KEY_LOCATION_RIGHT},
    {java_awt_event_KeyEvent_VK_ALT, XK_Alt_L, FALSE, java_awt_event_KeyEvent_KEY_LOCATION_LEFT},
    {java_awt_event_KeyEvent_VK_ALT, XK_Alt_R, FALSE, java_awt_event_KeyEvent_KEY_LOCATION_RIGHT},
    {java_awt_event_KeyEvent_VK_META, XK_Meta_L, FALSE, java_awt_event_KeyEvent_KEY_LOCATION_LEFT},
    {java_awt_event_KeyEvent_VK_META, XK_Meta_R, FALSE, java_awt_event_KeyEvent_KEY_LOCATION_RIGHT},
    {java_awt_event_KeyEvent_VK_CAPS_LOCK, XK_Caps_Lock, FALSE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},

    /* Misc Functions */
    {java_awt_event_KeyEvent_VK_PRINTSCREEN, XK_Print, FALSE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_PRINTSCREEN, XK_F22, FALSE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_PRINTSCREEN, XK_R2, FALSE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_CANCEL, XK_Cancel, TRUE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_HELP, XK_Help, FALSE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_NUM_LOCK, XK_Num_Lock, FALSE, java_awt_event_KeyEvent_KEY_LOCATION_NUMPAD},

    /* Other vendor-specific versions of Misc Functions */
    {java_awt_event_KeyEvent_VK_CANCEL, osfXK_Cancel, TRUE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_HELP, osfXK_Help, FALSE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},

    /* Rectangular Navigation Block */
    {java_awt_event_KeyEvent_VK_HOME, XK_Home, FALSE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_HOME, XK_R7, FALSE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_PAGE_UP, XK_Page_Up, FALSE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_PAGE_UP, XK_Prior, FALSE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_PAGE_UP, XK_R9, FALSE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_PAGE_DOWN, XK_Page_Down, FALSE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_PAGE_DOWN, XK_Next, FALSE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_PAGE_DOWN, XK_R15, FALSE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_END, XK_End, FALSE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_END, XK_R13, FALSE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_INSERT, XK_Insert, FALSE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_DELETE, XK_Delete, TRUE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},

    /* Keypad equivalents of Rectangular Navigation Block */
    {java_awt_event_KeyEvent_VK_HOME, XK_KP_Home, FALSE, java_awt_event_KeyEvent_KEY_LOCATION_NUMPAD},
    {java_awt_event_KeyEvent_VK_PAGE_UP, XK_KP_Page_Up, FALSE, java_awt_event_KeyEvent_KEY_LOCATION_NUMPAD},
    {java_awt_event_KeyEvent_VK_PAGE_UP, XK_KP_Prior, FALSE, java_awt_event_KeyEvent_KEY_LOCATION_NUMPAD},
    {java_awt_event_KeyEvent_VK_PAGE_DOWN, XK_KP_Page_Down, FALSE, java_awt_event_KeyEvent_KEY_LOCATION_NUMPAD},
    {java_awt_event_KeyEvent_VK_PAGE_DOWN, XK_KP_Next, FALSE, java_awt_event_KeyEvent_KEY_LOCATION_NUMPAD},
    {java_awt_event_KeyEvent_VK_END, XK_KP_End, FALSE, java_awt_event_KeyEvent_KEY_LOCATION_NUMPAD},
    {java_awt_event_KeyEvent_VK_INSERT, XK_KP_Insert, FALSE, java_awt_event_KeyEvent_KEY_LOCATION_NUMPAD},
    {java_awt_event_KeyEvent_VK_DELETE, XK_KP_Delete, TRUE, java_awt_event_KeyEvent_KEY_LOCATION_NUMPAD},

    /* Other vendor-specific Rectangular Navigation Block */
    {java_awt_event_KeyEvent_VK_PAGE_UP, osfXK_PageUp, FALSE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_PAGE_UP, osfXK_Prior, FALSE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_PAGE_DOWN, osfXK_PageDown, FALSE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_PAGE_DOWN, osfXK_Next, FALSE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_END, osfXK_EndLine, FALSE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_INSERT, osfXK_Insert, FALSE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_DELETE, osfXK_Delete, TRUE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},

    /* Triangular Navigation Block */
    {java_awt_event_KeyEvent_VK_LEFT, XK_Left, FALSE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_UP, XK_Up, FALSE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_RIGHT, XK_Right, FALSE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_DOWN, XK_Down, FALSE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},

    /* Keypad equivalents of Triangular Navigation Block */
    {java_awt_event_KeyEvent_VK_KP_LEFT, XK_KP_Left, FALSE, java_awt_event_KeyEvent_KEY_LOCATION_NUMPAD},
    {java_awt_event_KeyEvent_VK_KP_UP, XK_KP_Up, FALSE, java_awt_event_KeyEvent_KEY_LOCATION_NUMPAD},
    {java_awt_event_KeyEvent_VK_KP_RIGHT, XK_KP_Right, FALSE, java_awt_event_KeyEvent_KEY_LOCATION_NUMPAD},
    {java_awt_event_KeyEvent_VK_KP_DOWN, XK_KP_Down, FALSE, java_awt_event_KeyEvent_KEY_LOCATION_NUMPAD},

    /* Other vendor-specific Triangular Navigation Block */
    {java_awt_event_KeyEvent_VK_LEFT, osfXK_Left, FALSE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_UP, osfXK_Up, FALSE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_RIGHT, osfXK_Right, FALSE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_DOWN, osfXK_Down, FALSE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},

    /* Remaining Cursor control & motion */
    {java_awt_event_KeyEvent_VK_BEGIN, XK_Begin, FALSE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_BEGIN, XK_KP_Begin, FALSE, java_awt_event_KeyEvent_KEY_LOCATION_NUMPAD},

    {java_awt_event_KeyEvent_VK_0, XK_0, TRUE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_1, XK_1, TRUE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_2, XK_2, TRUE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_3, XK_3, TRUE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_4, XK_4, TRUE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_5, XK_5, TRUE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_6, XK_6, TRUE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_7, XK_7, TRUE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_8, XK_8, TRUE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_9, XK_9, TRUE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},

    {java_awt_event_KeyEvent_VK_SPACE, XK_space, TRUE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_EXCLAMATION_MARK, XK_exclam, TRUE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_QUOTEDBL, XK_quotedbl, TRUE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_NUMBER_SIGN, XK_numbersign, TRUE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_DOLLAR, XK_dollar, TRUE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_AMPERSAND, XK_ampersand, TRUE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_QUOTE, XK_apostrophe, TRUE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_LEFT_PARENTHESIS, XK_parenleft, TRUE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_RIGHT_PARENTHESIS, XK_parenright, TRUE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_ASTERISK, XK_asterisk, TRUE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_PLUS, XK_plus, TRUE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_COMMA, XK_comma, TRUE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_MINUS, XK_minus, TRUE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_PERIOD, XK_period, TRUE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_SLASH, XK_slash, TRUE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},

    {java_awt_event_KeyEvent_VK_COLON, XK_colon, TRUE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_SEMICOLON, XK_semicolon, TRUE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_LESS, XK_less, TRUE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_EQUALS, XK_equal, TRUE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_GREATER, XK_greater, TRUE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},

    {java_awt_event_KeyEvent_VK_AT, XK_at, TRUE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},

    {java_awt_event_KeyEvent_VK_OPEN_BRACKET, XK_bracketleft, TRUE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_BACK_SLASH, XK_backslash, TRUE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_CLOSE_BRACKET, XK_bracketright, TRUE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_CIRCUMFLEX, XK_asciicircum, TRUE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_UNDERSCORE, XK_underscore, TRUE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_BACK_QUOTE, XK_grave, TRUE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},

    {java_awt_event_KeyEvent_VK_BRACELEFT, XK_braceleft, TRUE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_BRACERIGHT, XK_braceright, TRUE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},

    {java_awt_event_KeyEvent_VK_INVERTED_EXCLAMATION_MARK, XK_exclamdown, TRUE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},

    /* Remaining Numeric Keypad Keys */
    {java_awt_event_KeyEvent_VK_NUMPAD0, XK_KP_0, TRUE, java_awt_event_KeyEvent_KEY_LOCATION_NUMPAD},
    {java_awt_event_KeyEvent_VK_NUMPAD1, XK_KP_1, TRUE, java_awt_event_KeyEvent_KEY_LOCATION_NUMPAD},
    {java_awt_event_KeyEvent_VK_NUMPAD2, XK_KP_2, TRUE, java_awt_event_KeyEvent_KEY_LOCATION_NUMPAD},
    {java_awt_event_KeyEvent_VK_NUMPAD3, XK_KP_3, TRUE, java_awt_event_KeyEvent_KEY_LOCATION_NUMPAD},
    {java_awt_event_KeyEvent_VK_NUMPAD4, XK_KP_4, TRUE, java_awt_event_KeyEvent_KEY_LOCATION_NUMPAD},
    {java_awt_event_KeyEvent_VK_NUMPAD5, XK_KP_5, TRUE, java_awt_event_KeyEvent_KEY_LOCATION_NUMPAD},
    {java_awt_event_KeyEvent_VK_NUMPAD6, XK_KP_6, TRUE, java_awt_event_KeyEvent_KEY_LOCATION_NUMPAD},
    {java_awt_event_KeyEvent_VK_NUMPAD7, XK_KP_7, TRUE, java_awt_event_KeyEvent_KEY_LOCATION_NUMPAD},
    {java_awt_event_KeyEvent_VK_NUMPAD8, XK_KP_8, TRUE, java_awt_event_KeyEvent_KEY_LOCATION_NUMPAD},
    {java_awt_event_KeyEvent_VK_NUMPAD9, XK_KP_9, TRUE, java_awt_event_KeyEvent_KEY_LOCATION_NUMPAD},
    {java_awt_event_KeyEvent_VK_SPACE, XK_KP_Space, TRUE, java_awt_event_KeyEvent_KEY_LOCATION_NUMPAD},
    {java_awt_event_KeyEvent_VK_TAB, XK_KP_Tab, TRUE, java_awt_event_KeyEvent_KEY_LOCATION_NUMPAD},
    {java_awt_event_KeyEvent_VK_ENTER, XK_KP_Enter, TRUE, java_awt_event_KeyEvent_KEY_LOCATION_NUMPAD},
    {java_awt_event_KeyEvent_VK_EQUALS, XK_KP_Equal, TRUE, java_awt_event_KeyEvent_KEY_LOCATION_NUMPAD},
    {java_awt_event_KeyEvent_VK_EQUALS, XK_R4, TRUE, java_awt_event_KeyEvent_KEY_LOCATION_NUMPAD},
    {java_awt_event_KeyEvent_VK_MULTIPLY, XK_KP_Multiply, TRUE, java_awt_event_KeyEvent_KEY_LOCATION_NUMPAD},
    {java_awt_event_KeyEvent_VK_MULTIPLY, XK_F26, TRUE, java_awt_event_KeyEvent_KEY_LOCATION_NUMPAD},
    {java_awt_event_KeyEvent_VK_MULTIPLY, XK_R6, TRUE, java_awt_event_KeyEvent_KEY_LOCATION_NUMPAD},
    {java_awt_event_KeyEvent_VK_ADD, XK_KP_Add, TRUE, java_awt_event_KeyEvent_KEY_LOCATION_NUMPAD},
    {java_awt_event_KeyEvent_VK_SEPARATOR, XK_KP_Separator, TRUE, java_awt_event_KeyEvent_KEY_LOCATION_NUMPAD},
    {java_awt_event_KeyEvent_VK_SUBTRACT, XK_KP_Subtract, TRUE, java_awt_event_KeyEvent_KEY_LOCATION_NUMPAD},
    {java_awt_event_KeyEvent_VK_SUBTRACT, XK_F24, TRUE, java_awt_event_KeyEvent_KEY_LOCATION_NUMPAD},
    {java_awt_event_KeyEvent_VK_DECIMAL, XK_KP_Decimal, TRUE, java_awt_event_KeyEvent_KEY_LOCATION_NUMPAD},
    {java_awt_event_KeyEvent_VK_DIVIDE, XK_KP_Divide, TRUE, java_awt_event_KeyEvent_KEY_LOCATION_NUMPAD},
    {java_awt_event_KeyEvent_VK_DIVIDE, XK_F25, TRUE, java_awt_event_KeyEvent_KEY_LOCATION_NUMPAD},
    {java_awt_event_KeyEvent_VK_DIVIDE, XK_R5, TRUE, java_awt_event_KeyEvent_KEY_LOCATION_NUMPAD},

    /* Function Keys */
    {java_awt_event_KeyEvent_VK_F1, XK_F1, FALSE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_F2, XK_F2, FALSE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_F3, XK_F3, FALSE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_F4, XK_F4, FALSE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_F5, XK_F5, FALSE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_F6, XK_F6, FALSE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_F7, XK_F7, FALSE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_F8, XK_F8, FALSE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_F9, XK_F9, FALSE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_F10, XK_F10, FALSE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_F11, XK_F11, FALSE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_F12, XK_F12, FALSE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},

    /* Sun vendor-specific version of F11 and F12 */
    {java_awt_event_KeyEvent_VK_F11, SunXK_F36, FALSE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_F12, SunXK_F37, FALSE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},

    /* X11 keysym names for input method related keys don't always
     * match keytop engravings or Java virtual key names, so here we
     * only map constants that we've found on real keyboards.
     */
    /* Type 5c Japanese keyboard: kakutei */
    {java_awt_event_KeyEvent_VK_ACCEPT, XK_Execute, FALSE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    /* Type 5c Japanese keyboard: henkan */
    {java_awt_event_KeyEvent_VK_CONVERT, XK_Kanji, FALSE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    /* Type 5c Japanese keyboard: nihongo */
    {java_awt_event_KeyEvent_VK_INPUT_METHOD_ON_OFF, XK_Henkan_Mode, FALSE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    /* VK_KANA_LOCK is handled separately because it generates the
     * same keysym as ALT_GRAPH in spite of its different behavior.
     */

    {java_awt_event_KeyEvent_VK_COMPOSE, XK_Multi_key, FALSE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_ALT_GRAPH, XK_Mode_switch, FALSE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},

    /* Editing block */
    {java_awt_event_KeyEvent_VK_AGAIN, XK_Redo, FALSE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_AGAIN, XK_L2, FALSE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_UNDO, XK_Undo, FALSE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_UNDO, XK_L4, FALSE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_COPY, XK_L6, FALSE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_PASTE, XK_L8, FALSE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_CUT, XK_L10, FALSE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_FIND, XK_Find, FALSE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_FIND, XK_L9, FALSE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_PROPS, XK_L3, FALSE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_STOP, XK_L1, FALSE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},

    /* Sun vendor-specific versions for editing block */
    {java_awt_event_KeyEvent_VK_AGAIN, SunXK_Again, FALSE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_UNDO, SunXK_Undo, FALSE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_COPY, SunXK_Copy, FALSE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_PASTE, SunXK_Paste, FALSE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_CUT, SunXK_Cut, FALSE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_FIND, SunXK_Find, FALSE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_PROPS, SunXK_Props, FALSE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_STOP, SunXK_Stop, FALSE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},

    /* Apollo (HP) vendor-specific versions for editing block */
    {java_awt_event_KeyEvent_VK_COPY, apXK_Copy, FALSE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_CUT, apXK_Cut, FALSE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_PASTE, apXK_Paste, FALSE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},

    /* Other vendor-specific versions for editing block */
    {java_awt_event_KeyEvent_VK_COPY, osfXK_Copy, FALSE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_CUT, osfXK_Cut, FALSE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_PASTE, osfXK_Paste, FALSE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_UNDO, osfXK_Undo, FALSE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},

    /* Dead key mappings (for European keyboards) */
    {java_awt_event_KeyEvent_VK_DEAD_GRAVE, XK_dead_grave, FALSE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_DEAD_ACUTE, XK_dead_acute, FALSE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_DEAD_CIRCUMFLEX, XK_dead_circumflex, FALSE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_DEAD_TILDE, XK_dead_tilde, FALSE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_DEAD_MACRON, XK_dead_macron, FALSE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_DEAD_BREVE, XK_dead_breve, FALSE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_DEAD_ABOVEDOT, XK_dead_abovedot, FALSE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_DEAD_DIAERESIS, XK_dead_diaeresis, FALSE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_DEAD_ABOVERING, XK_dead_abovering, FALSE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_DEAD_DOUBLEACUTE, XK_dead_doubleacute, FALSE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_DEAD_CARON, XK_dead_caron, FALSE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_DEAD_CEDILLA, XK_dead_cedilla, FALSE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_DEAD_OGONEK, XK_dead_ogonek, FALSE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_DEAD_IOTA, XK_dead_iota, FALSE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_DEAD_VOICED_SOUND, XK_dead_voiced_sound, FALSE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_DEAD_SEMIVOICED_SOUND, XK_dead_semivoiced_sound, FALSE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},

    /* Sun vendor-specific dead key mappings (for European keyboards) */
    {java_awt_event_KeyEvent_VK_DEAD_GRAVE, SunXK_FA_Grave, FALSE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_DEAD_CIRCUMFLEX, SunXK_FA_Circum, FALSE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_DEAD_TILDE, SunXK_FA_Tilde, FALSE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_DEAD_ACUTE, SunXK_FA_Acute, FALSE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_DEAD_DIAERESIS, SunXK_FA_Diaeresis, FALSE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_DEAD_CEDILLA, SunXK_FA_Cedilla, FALSE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},

    /* DEC vendor-specific dead key mappings (for European keyboards) */
    {java_awt_event_KeyEvent_VK_DEAD_ABOVERING, DXK_ring_accent, FALSE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_DEAD_CIRCUMFLEX, DXK_circumflex_accent, FALSE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_DEAD_CEDILLA, DXK_cedilla_accent, FALSE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_DEAD_ACUTE, DXK_acute_accent, FALSE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_DEAD_GRAVE, DXK_grave_accent, FALSE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_DEAD_TILDE, DXK_tilde, FALSE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_DEAD_DIAERESIS, DXK_diaeresis, FALSE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},

    /* Other vendor-specific dead key mappings (for European keyboards) */
    {java_awt_event_KeyEvent_VK_DEAD_ACUTE, hpXK_mute_acute, FALSE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_DEAD_GRAVE, hpXK_mute_grave, FALSE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_DEAD_CIRCUMFLEX, hpXK_mute_asciicircum, FALSE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_DEAD_DIAERESIS, hpXK_mute_diaeresis, FALSE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},
    {java_awt_event_KeyEvent_VK_DEAD_TILDE, hpXK_mute_asciitilde, FALSE, java_awt_event_KeyEvent_KEY_LOCATION_STANDARD},

    {java_awt_event_KeyEvent_VK_UNDEFINED, NoSymbol, FALSE, java_awt_event_KeyEvent_KEY_LOCATION_UNKNOWN}
};

static Boolean
keyboardHasKanaLockKey()
{
    static Boolean haveResult = FALSE;
    static Boolean result = FALSE;

    int32_t minKeyCode, maxKeyCode, keySymsPerKeyCode;
    KeySym *keySyms, *keySymsStart, keySym;
    int32_t i;
    int32_t kanaCount = 0;

    // Solaris doesn't let you swap keyboards without rebooting,
    // so there's no need to check for the kana lock key more than once.
    if (haveResult) {
       return result;
    }

    // There's no direct way to determine whether the keyboard has
    // a kana lock key. From available keyboard mapping tables, it looks
    // like only keyboards with the kana lock key can produce keysyms
    // for kana characters. So, as an indirect test, we check for those.

    XDisplayKeycodes(awt_display, &minKeyCode, &maxKeyCode);
    keySyms = XGetKeyboardMapping(awt_display, minKeyCode, maxKeyCode - minKeyCode + 1, &keySymsPerKeyCode);
    keySymsStart = keySyms;
    for (i = 0; i < (maxKeyCode - minKeyCode + 1) * keySymsPerKeyCode; i++) {
        keySym = *keySyms++;
        if ((keySym & 0xff00) == 0x0400) {
            kanaCount++;
        }
    }
    XFree(keySymsStart);

    // use a (somewhat arbitrary) minimum so we don't get confused by a stray function key
    result = kanaCount > 10;
    haveResult = TRUE;
    return result;
}

void
keysymToAWTKeyCode(KeySym x11Key, jint *keycode, Boolean *mapsToUnicodeChar,
  jint *keyLocation)
{
    int32_t i;

    // Solaris uses XK_Mode_switch for both the non-locking AltGraph
    // and the locking Kana key, but we want to keep them separate for
    // KeyEvent.
    if (x11Key == XK_Mode_switch && keyboardHasKanaLockKey()) {
        *keycode = java_awt_event_KeyEvent_VK_KANA_LOCK;
        *mapsToUnicodeChar = FALSE;
        *keyLocation = java_awt_event_KeyEvent_KEY_LOCATION_UNKNOWN;
        return;
    }

    for (i = 0;
         keymapTable[i].awtKey != java_awt_event_KeyEvent_VK_UNDEFINED;
         i++) {
        if (keymapTable[i].x11Key == x11Key) {
            *keycode = keymapTable[i].awtKey;
            *mapsToUnicodeChar = keymapTable[i].mapsToUnicodeChar;
            *keyLocation = keymapTable[i].keyLocation;
            return;
        }
    }

    *keycode = java_awt_event_KeyEvent_VK_UNDEFINED;
    *mapsToUnicodeChar = FALSE;
    *keyLocation = java_awt_event_KeyEvent_KEY_LOCATION_UNKNOWN;

    DTRACE_PRINTLN1("keysymToAWTKeyCode: no key mapping found: keysym = %x", x11Key);
}

KeySym
awt_getX11KeySym(jint awtKey)
{
    int32_t i;

    if (awtKey == java_awt_event_KeyEvent_VK_KANA_LOCK && keyboardHasKanaLockKey()) {
        return XK_Mode_switch;
    }

    for (i = 0; keymapTable[i].awtKey != 0; i++) {
        if (keymapTable[i].awtKey == awtKey) {
            return keymapTable[i].x11Key;
        }
    }

    DTRACE_PRINTLN1("awt_getX11KeySym: no key mapping found: awtKey = %x", awtKey);
    return NoSymbol;
}


typedef struct COLLAPSE_INFO {
    Window win;
    DamageRect *r;
} CollapseInfo;

static void
expandDamageRect(DamageRect * drect, XEvent * xev, Boolean debug, char *str)
{
    int32_t x1 = xev->xexpose.x;
    int32_t y1 = xev->xexpose.y;
    int32_t x2 = x1 + xev->xexpose.width;
    int32_t y2 = y1 + xev->xexpose.height;

    /*
      if (debug) {
      printf("   %s: collapsing (%d,%d %dx%d) into (%d,%d %dx%d) ->>",
      str, x1, y1, xev->xexpose.width, xev->xexpose.height,
      drect->x1, drect->y1, drect->x2 - drect->x1, drect->y2 - drect->y1);
      }
    */

    drect->x1 = MIN(x1, drect->x1);
    drect->y1 = MIN(y1, drect->y1);
    drect->x2 = MAX(x2, drect->x2);
    drect->y2 = MAX(y2, drect->y2);

    /*
      if (debug) {
      printf("(%d,%d %dx%d) %s\n",
      drect->x1, drect->y1, drect->x2 - drect->x1, drect->y2 - drect->y1);
      }
    */

}

static Bool
checkForExpose(Display * dpy, XEvent * evt, XPointer client_data)
{
    CollapseInfo *cinfo = (CollapseInfo *) client_data;

    if ((evt->type == Expose && evt->xexpose.window == cinfo->win &&
         INTERSECTS(cinfo->r->x1, cinfo->r->x2, cinfo->r->y1, cinfo->r->y2,
                    evt->xexpose.x,
                    evt->xexpose.x + evt->xexpose.width,
                    evt->xexpose.y,
                    evt->xexpose.y + evt->xexpose.height)) ||
        (evt->type == GraphicsExpose && evt->xgraphicsexpose.drawable == cinfo->win &&
         INTERSECTS(cinfo->r->x1, cinfo->r->x2, cinfo->r->y1, cinfo->r->y2,
                    evt->xgraphicsexpose.x,
                    evt->xgraphicsexpose.x + evt->xgraphicsexpose.width,
                    evt->xgraphicsexpose.y,
                    evt->xgraphicsexpose.y + evt->xgraphicsexpose.height))) {

        return True;
    }
    return False;
}

/*
 * javaObject is an MComponentPeer instance
 */
static void
HandleExposeEvent(Widget w, jobject javaObject, XEvent * event)
{
    jobject target;
    jint wdth, hght;

    JNIEnv *env = (JNIEnv *)JNU_GetEnv(jvm, JNI_VERSION_1_2);

    switch (event->type) {
        case Expose:
        case GraphicsExpose:
        {
            struct ComponentData *cdata;
            Boolean debug = FALSE;
            jint drawState;

            /* Set the draw state */
            drawState = (*env)->GetIntField(env, javaObject,
                mComponentPeerIDs.drawState);
            (*env)->SetIntField(env, javaObject, mComponentPeerIDs.drawState,
                drawState | JAWT_LOCK_CLIP_CHANGED);
            cdata = (struct ComponentData *)
              JNU_GetLongFieldAsPtr(env, javaObject, mComponentPeerIDs.pData);
            if (JNU_IsNull(env, javaObject) || (cdata == NULL)) {
                return;
            }
            if (event->xexpose.send_event) {
                if (cdata->repaintPending & RepaintPending_REPAINT) {
                    cdata->repaintPending &= ~RepaintPending_REPAINT;

                    JNU_CallMethodByName(env,
                                         NULL,
                                         javaObject,
                                         "handleRepaint",
                                         "(IIII)V",
                                         (jint) cdata->repaintRect.x1,
                                         (jint) cdata->repaintRect.y1,
                                         (jint) cdata->repaintRect.x2
                                         - cdata->repaintRect.x1,
                                         (jint) cdata->repaintRect.y2
                                         - cdata->repaintRect.y1);
                    if ((*env)->ExceptionOccurred(env)) {
                        (*env)->ExceptionDescribe(env);
                        (*env)->ExceptionClear(env);
                    }
                }
                return;
            }
            if ((cdata->repaintPending & RepaintPending_EXPOSE) == 0) {
                cdata->exposeRect.x1 = event->xexpose.x;
                cdata->exposeRect.y1 = event->xexpose.y;
                cdata->exposeRect.x2 = cdata->exposeRect.x1 + event->xexpose.width;
                cdata->exposeRect.y2 = cdata->exposeRect.y1 + event->xexpose.height;
                cdata->repaintPending |= RepaintPending_EXPOSE;
            } else {
                expandDamageRect(&(cdata->exposeRect), event, debug, "1");
            }

            /* Only post Expose/Repaint if we know others arn't following
             * directly in the queue.
             */
            if (event->xexpose.count == 0) {
                int32_t count = 0;
                CollapseInfo cinfo;

                cinfo.win = XtWindow(w);
                cinfo.r = &(cdata->exposeRect);

                /* Do a little more inspecting and collapse further if there
                 * are additional expose events pending on this window where
                 * the damage rects intersect with the current exposeRect.
                 */
                while (TRUE) {
                    XEvent xev;

                    if (XCheckIfEvent(XtDisplay(w), &xev
                                      ,checkForExpose, (XtPointer) & cinfo)) {
                        count = xev.xexpose.count;
                        expandDamageRect(&(cdata->exposeRect), &xev, debug, "2");

                    } else {
                        /* XCheckIfEvent Failed. */
                        break;
                    }
                }

                cdata->repaintPending &= ~RepaintPending_EXPOSE;

                /* Fix for bugtraq id 4262108. Paint events should not be
                 * delivered to components that have one of their
                 * dimensions equal to zero.
                 */

                if ((*env)->EnsureLocalCapacity(env, 1) < 0) {
                    return;
                }

                target = (*env)->GetObjectField(env, javaObject,
                                                    mComponentPeerIDs.target);
                wdth = (*env)->GetIntField(env, target, componentIDs.width);
                hght = (*env)->GetIntField(env, target, componentIDs.height);
                (*env)->DeleteLocalRef(env, target);

                if ( wdth != 0 && hght != 0) {
                    JNU_CallMethodByName(env,
                                        NULL,
                                        javaObject,
                                        "handleExpose",
                                        "(IIII)V",
                                        (jint) cdata->exposeRect.x1,
                                        (jint) cdata->exposeRect.y1,
                                        (jint) cdata->exposeRect.x2
                                        - cdata->exposeRect.x1,
                                        (jint) cdata->exposeRect.y2
                                        - cdata->exposeRect.y1);
                    if ((*env)->ExceptionOccurred(env)) {
                        (*env)->ExceptionDescribe(env);
                        (*env)->ExceptionClear(env);
                    }
                }
            }
        }
        break;

        default:
            jio_fprintf(stderr, "Got event %d in HandleExposeEvent!\n", event->type);
    }
}

/* We always store and return JNI GlobalRefs. */
static jweak focusOwnerPeer = NULL;
static jweak focusedWindowPeer = NULL;

/*
 * This function should only be called under the
 * protection of AWT_LOCK(). Otherwise, multithreaded access
 * can corrupt the value of focusOwnerPeer variable.
 * This function returns LocalRef, result should be deleted
 * explicitly if called on a thread that never returns to
 * Java.
 */
jobject
awt_canvas_getFocusOwnerPeer() {
    JNIEnv *env = (JNIEnv *)JNU_GetEnv(jvm, JNI_VERSION_1_2);
    jobject res;
    AWT_LOCK();
    res = (*env)->NewLocalRef(env, focusOwnerPeer);
    AWT_UNLOCK();
    return res;
}

/*
 * This function should only be called under the
 * protection of AWT_LOCK(). Otherwise, multithreaded access
 * can corrupt the value of focusedWindowPeer variable.
 * This function returns LocalRef, result should be deleted
 * explicitly if called on a thread that never returns to
 * Java.
 */
jobject
awt_canvas_getFocusedWindowPeer() {
    JNIEnv *env = (JNIEnv *)JNU_GetEnv(jvm, JNI_VERSION_1_2);
    jobject res;
    AWT_LOCK();
    res = (*env)->NewLocalRef(env, focusedWindowPeer);
    AWT_UNLOCK();
    return res;
}

/*
 * Only call this function under AWT_LOCK(). Otherwise, multithreaded
 * access can corrupt the value of focusOwnerPeer variable.
 */
void
awt_canvas_setFocusOwnerPeer(jobject peer) {
    JNIEnv *env = (JNIEnv *)JNU_GetEnv(jvm, JNI_VERSION_1_2);
    AWT_LOCK();
    if (focusOwnerPeer != NULL) {
        (*env)->DeleteWeakGlobalRef(env, focusOwnerPeer);
    }
    focusOwnerPeer = (peer != NULL)
        ? (*env)->NewWeakGlobalRef(env, peer) : NULL;
    AWT_UNLOCK();
}

/*
 * Only call this function under AWT_LOCK(). Otherwise, multithreaded
 * access can corrupt the value of focusedWindowPeer variable.
 */
void
awt_canvas_setFocusedWindowPeer(jobject peer) {
    JNIEnv *env = (JNIEnv *)JNU_GetEnv(jvm, JNI_VERSION_1_2);
    AWT_LOCK();
    if (focusedWindowPeer != NULL) {
        (*env)->DeleteWeakGlobalRef(env, focusedWindowPeer);
    }
    focusedWindowPeer = (peer != NULL)
        ? (*env)->NewWeakGlobalRef(env, peer) : NULL;
    AWT_UNLOCK();
}

void callFocusCallback(jobject focusPeer, int focus_type, jobject cause) {
    awt_post_java_focus_event(focusPeer,
                              focus_type,
                              cause,
                              NULL);
    awt_canvas_setFocusOwnerPeer(focusPeer);
}


void
handleFocusEvent(Widget w,
                 XFocusChangeEvent * fevent,
                 XtPointer client_data,
                 Boolean * cont,
                 Boolean passEvent,
                 jobject cause)
{
    if (fevent->type == FocusIn) {
        if (fevent->mode == NotifyNormal &&
            fevent->detail != NotifyPointer && fevent->detail != NotifyVirtual)
        {
#ifdef DEBUG_FOCUS
            printf("window = %d, mode = %d, detail = %d\n", fevent->window, fevent->mode, fevent->detail);
            printf("----posting java FOCUS GAINED on window %d, pass = %d\n", XtWindow(w), passEvent);
#endif
            awt_post_java_focus_event(client_data,
                                      java_awt_event_FocusEvent_FOCUS_GAINED,
                                      cause,
                                      NULL);
            awt_canvas_setFocusOwnerPeer(client_data);
        }
    } else {
        /* FocusOut */
        if (fevent->mode == NotifyNormal &&
            fevent->detail != NotifyPointer && fevent->detail != NotifyVirtual)
        {
#ifdef DEBUG_FOCUS
          printf("window = %d, mode = %d, detail = %d\n", fevent->window, fevent->mode, fevent->detail);
          printf("----posting java FOCUS LOST on window %d, pass = %d, temp = %d\n", XtWindow(w), passEvent, temp);
#endif
            awt_post_java_focus_event(client_data,
                                      java_awt_event_FocusEvent_FOCUS_LOST,
                                      cause,
                                      NULL);
            awt_canvas_setFocusOwnerPeer(NULL);
        }
    }
    *cont = TRUE;
}

void callFocusHandler(Widget w, int eventType, jobject cause) {
    jobject peer = NULL;
    XFocusChangeEvent event;
    Boolean cont;
    JNIEnv *env = (JNIEnv *) JNU_GetEnv(jvm, JNI_VERSION_1_2);

    if (w == NULL) {
        return;
    }

    peer = findPeer(&w);
    if (peer == NULL) {
        w = findTopLevelByShell(w);
        if (w != NULL) {
            peer = findPeer(&w);
        }
    }
    if (peer == NULL) {
        return;
    }
    memset(&event, 0, sizeof(event));
    event.type = eventType;
    event.mode = NotifyNormal;
    event.detail = NotifyAncestor;
    event.window = XtWindow(w);
    cont = FALSE;
    handleFocusEvent(w, &event, (XtPointer)peer, &cont, TRUE, cause);
}

/**
 * Copy XEvent to jbyteArray and save it in AWTEvent
 */
void
awt_copyXEventToAWTEvent(JNIEnv *env, XEvent * xev, jobject jevent)
{
    jbyteArray bdata;
    if (xev != NULL) {
        if ((*env)->EnsureLocalCapacity(env, 1) < 0) {
            return;
        }
        bdata = (*env)->NewByteArray(env, sizeof(XEvent));
        if (bdata != NULL) {
            (*env)->SetByteArrayRegion(env, bdata, 0, sizeof(XEvent),
                                       (jbyte *)xev);
            (*env)->SetObjectField(env, jevent, awtEventIDs.bdata, bdata);
            (*env)->DeleteLocalRef(env, bdata);
        }
    }
}

/* Returns new modifiers set like ???_DOWN_MASK for keyboard and mouse after the event.
 * The modifiers on a Java key event reflect the state of the modifier keys
 * immediately AFTER the key press or release.  This usually doesn't require
 * us to change the modifiers: the exception is when the key pressed or
 * released is a modifier key.  Since the state of an XEvent represents
 * the modifiers BEFORE the event, we change the modifiers according to
 * the button and keycode.
 */
jint
getModifiers(uint32_t state, jint button, jint keyCode)
{
    jint modifiers = 0;

    if (((state & ShiftMask) != 0) ^ (keyCode == java_awt_event_KeyEvent_VK_SHIFT))
    {
        modifiers |= java_awt_event_InputEvent_SHIFT_DOWN_MASK;
    }
    if (((state & ControlMask) != 0) ^ (keyCode == java_awt_event_KeyEvent_VK_CONTROL))
    {
        modifiers |= java_awt_event_InputEvent_CTRL_DOWN_MASK;
    }
    if (((state & awt_MetaMask) != 0) ^ (keyCode == java_awt_event_KeyEvent_VK_META))
    {
        modifiers |= java_awt_event_InputEvent_META_DOWN_MASK;
    }
    if (((state & awt_AltMask) != 0) ^ (keyCode == java_awt_event_KeyEvent_VK_ALT))
    {
        modifiers |= java_awt_event_InputEvent_ALT_DOWN_MASK;
    }
    if (((state & awt_ModeSwitchMask) != 0) ^ (keyCode == java_awt_event_KeyEvent_VK_ALT_GRAPH))
    {
        modifiers |= java_awt_event_InputEvent_ALT_GRAPH_DOWN_MASK;
    }
    if (((state & Button1Mask) != 0) ^ (button == java_awt_event_MouseEvent_BUTTON1)) {
        modifiers |= java_awt_event_InputEvent_BUTTON1_DOWN_MASK;
    }
    if (((state & Button2Mask) != 0) ^ (button == java_awt_event_MouseEvent_BUTTON2)) {
        modifiers |= java_awt_event_InputEvent_BUTTON2_DOWN_MASK;
    }
    if (((state & Button3Mask) != 0) ^ (button == java_awt_event_MouseEvent_BUTTON3)) {
        modifiers |= java_awt_event_InputEvent_BUTTON3_DOWN_MASK;
    }
    return modifiers;
}

/* Returns which mouse button has changed state
 */
jint
getButton(uint32_t button)
{
    switch (button) {
    case Button1:
        return java_awt_event_MouseEvent_BUTTON1;
    case Button2:
        return java_awt_event_MouseEvent_BUTTON2;
    case Button3:
        return java_awt_event_MouseEvent_BUTTON3;
    }
    return java_awt_event_MouseEvent_NOBUTTON;
}


/* This function changes the state of the native XEvent AFTER
 * the corresponding Java event has been processed.  The XEvent
 * needs to be modified before it is dispatched to the native widget.
 */
void
awt_modify_KeyEvent(JNIEnv *env, XEvent *xevent, jobject jevent)
{
    jint keyCode;
    jchar keyChar;
    jint modifiers;
    KeySym keysym = (KeySym) java_awt_event_KeyEvent_CHAR_UNDEFINED;

    if (xevent->type != KeyPress && xevent->type != KeyRelease) {
        return;
    }

    keyCode = (*env)->GetIntField(env, jevent, keyEventIDs.keyCode);
    keyChar = (*env)->GetCharField(env, jevent, keyEventIDs.keyChar);
    modifiers = (*env)->GetIntField(env, jevent, inputEventIDs.modifiers);

    switch (keyCode) {
        case java_awt_event_KeyEvent_VK_MULTIPLY:
        case java_awt_event_KeyEvent_VK_SUBTRACT:
        case java_awt_event_KeyEvent_VK_DIVIDE:
            /* Bugid 4103229:  Change the X event so these three Numpad
             * keys work with the NumLock off.  For some reason, Motif
             * widgets ignore the events produced by these three keys
             * unless the NumLock is on.  It also ignores them if some
             * other modifiers are set.  Turn off ALL modifiers, then
             * turn NumLock mask on in the X event.
             */
            xevent->xkey.state = awt_NumLockMask;
            return;
        case java_awt_event_KeyEvent_VK_ENTER:
        case java_awt_event_KeyEvent_VK_BACK_SPACE:
        case java_awt_event_KeyEvent_VK_TAB:
        case java_awt_event_KeyEvent_VK_ESCAPE:
        case java_awt_event_KeyEvent_VK_ADD:
        case java_awt_event_KeyEvent_VK_DECIMAL:
        case java_awt_event_KeyEvent_VK_NUMPAD0:
        case java_awt_event_KeyEvent_VK_NUMPAD1:
        case java_awt_event_KeyEvent_VK_NUMPAD2:
        case java_awt_event_KeyEvent_VK_NUMPAD3:
        case java_awt_event_KeyEvent_VK_NUMPAD4:
        case java_awt_event_KeyEvent_VK_NUMPAD5:
        case java_awt_event_KeyEvent_VK_NUMPAD6:
        case java_awt_event_KeyEvent_VK_NUMPAD7:
        case java_awt_event_KeyEvent_VK_NUMPAD8:
        case java_awt_event_KeyEvent_VK_NUMPAD9:
            keysym = awt_getX11KeySym(keyCode);
            break;
        case java_awt_event_KeyEvent_VK_DELETE:
            /* For some reason XKeysymToKeycode returns incorrect value for
             * Delete, so we don't want to modify the original event
             */
            break;
        default:
            if (keyChar < (KeySym) 256) {
                keysym = (KeySym) keyChar;
            } else {
                keysym = awt_getX11KeySym(keyCode);
            }
            break;
    }

    if (keysym < (KeySym) 256) {
        if (modifiers & java_awt_event_InputEvent_CTRL_MASK) {
            switch (keysym + 64) {
                case '[':
                case ']':
                case '\\':
                case '_':
                    keysym += 64;
                    break;
                default:
                    if (isalpha((int32_t)(keysym + 'a' - 1))) {
                        keysym += ('a' - 1);
                    }
                    break;
            }
        }
        /*
         * 0xff61 is Unicode value of first XK_kana_fullstop.
         * We need X Keysym to Unicode map in post1.1 release
         * to support more international keyboards.
         */
        if (keysym >= (KeySym) 0xff61 && keysym <= (KeySym) 0xff9f) {
            keysym = keysym - 0xff61 + XK_kana_fullstop;
        }
        xevent->xkey.keycode = XKeysymToKeycode(awt_display, keysym);
    }

    if (keysym >= 'A' && keysym <= 'Z') {
        xevent->xkey.state |= ShiftMask;
    }
    if (modifiers & java_awt_event_InputEvent_SHIFT_DOWN_MASK) {
        xevent->xkey.state |= ShiftMask;
    }
    if (modifiers & java_awt_event_InputEvent_CTRL_DOWN_MASK) {
        xevent->xkey.state |= ControlMask;
    }
    if (modifiers & java_awt_event_InputEvent_META_DOWN_MASK) {
        xevent->xkey.state |= awt_MetaMask;
    }
    if (modifiers & java_awt_event_InputEvent_ALT_DOWN_MASK) {
        xevent->xkey.state |= awt_AltMask;
    }
    if (modifiers & java_awt_event_InputEvent_ALT_GRAPH_DOWN_MASK) {
        xevent->xkey.state |= awt_ModeSwitchMask;
    }
    if (modifiers & java_awt_event_InputEvent_BUTTON1_DOWN_MASK) {
        xevent->xkey.state |= Button1Mask;
    }
    if (modifiers & java_awt_event_InputEvent_BUTTON2_DOWN_MASK) {
        xevent->xkey.state |= Button2Mask;
    }
    if (modifiers & java_awt_event_InputEvent_BUTTON3_DOWN_MASK) {
        xevent->xkey.state |= Button3Mask;
    }
}


/* Called from handleKeyEvent.  The purpose of this function is
 * to check for a list of vendor-specific keysyms, most of which
 * have values greater than 0xFFFF.  Most of these keys don't map
 * to unicode characters, but some do.
 *
 * For keys that don't map to unicode characters, the keysym
 * is irrelevant at this point.  We set the keysym to zero
 * to ensure that the switch statement immediately below
 * this function call (in adjustKeySym) won't incorrectly act
 * on them after the high bits are stripped off.
 *
 * For keys that do map to unicode characters, we change the keysym
 * to the equivalent that is < 0xFFFF
 */
static void
handleVendorKeySyms(XEvent *event, KeySym *keysym)
{
    KeySym originalKeysym = *keysym;

    switch (*keysym) {
        /* Apollo (HP) vendor-specific from <X11/ap_keysym.h> */
        case apXK_Copy:
        case apXK_Cut:
        case apXK_Paste:
        /* DEC vendor-specific from <X11/DECkeysym.h> */
        case DXK_ring_accent:         /* syn usldead_ring */
        case DXK_circumflex_accent:
        case DXK_cedilla_accent:      /* syn usldead_cedilla */
        case DXK_acute_accent:
        case DXK_grave_accent:
        case DXK_tilde:
        case DXK_diaeresis:
        /* Sun vendor-specific from <X11/Sunkeysym.h> */
        case SunXK_FA_Grave:
        case SunXK_FA_Circum:
        case SunXK_FA_Tilde:
        case SunXK_FA_Acute:
        case SunXK_FA_Diaeresis:
        case SunXK_FA_Cedilla:
        case SunXK_F36:                /* Labeled F11 */
        case SunXK_F37:                /* Labeled F12 */
        case SunXK_Props:
        case SunXK_Copy:
        case SunXK_Open:
        case SunXK_Paste:
        case SunXK_Cut:
        /* Other vendor-specific from HPkeysym.h */
        case hpXK_mute_acute:          /* syn usldead_acute */
        case hpXK_mute_grave:          /* syn usldead_grave */
        case hpXK_mute_asciicircum:    /* syn usldead_asciicircum */
        case hpXK_mute_diaeresis:      /* syn usldead_diaeresis */
        case hpXK_mute_asciitilde:     /* syn usldead_asciitilde */
        case osfXK_Copy:
        case osfXK_Cut:
        case osfXK_Paste:
        case osfXK_PageUp:
        case osfXK_PageDown:
        case osfXK_EndLine:
        case osfXK_Clear:
        case osfXK_Left:
        case osfXK_Up:
        case osfXK_Right:
        case osfXK_Down:
        case osfXK_Prior:
        case osfXK_Next:
        case osfXK_Insert:
        case osfXK_Undo:
        case osfXK_Help:
            *keysym = 0;
            break;
        /*
         * The rest DO map to unicode characters, so translate them
         */
        case osfXK_BackSpace:
            *keysym = XK_BackSpace;
            break;
        case osfXK_Escape:
            *keysym = XK_Escape;
            break;
        case osfXK_Cancel:
            *keysym = XK_Cancel;
            break;
        case osfXK_Delete:
            *keysym = XK_Delete;
            break;
        default:
            break;
    }

    if (originalKeysym != *keysym) {
        DTRACE_PRINTLN2("In handleVendorKeySyms: originalKeysym=%x, keysym=%x",
          originalKeysym, *keysym);
    }
}

/* Called from handleKeyEvent.
 * The purpose of this function is to adjust the keysym and XEvent
 * keycode for a key event.  This is basically a conglomeration of
 * bugfixes that require these adjustments.
 */
static void
adjustKeySym(XEvent *event, KeySym *keysym)
{
    KeySym originalKeysym = *keysym;

    /* We have seen bits set in the high two bytes on Linux,
     * which prevents this switch statement from executing
     * correctly.  Strip off the high order bits.
     */
    *keysym &= 0x0000FFFF;

    switch (*keysym) {
        case XK_Return:
            *keysym = XK_Linefeed;     /* fall thru */
        case XK_BackSpace:
        case XK_Tab:
        case XK_Linefeed:
        case XK_Escape:
        case XK_Delete:
            /* strip off highorder bits defined in keysymdef.h
             * I think doing this converts them to values that
             * we can cast to jchars and use as java keychars.
             * If so, it's really a hack.
             */
            *keysym &= 0x007F;
            break;
        case XK_Cancel:
            *keysym = 0x0018;  /* the unicode char for Cancel */
            break;
        case XK_KP_Decimal:
            *keysym = '.';
            break;
        case XK_KP_Add:
            *keysym = '+';
            break;
        case XK_F24:           /* NumLock off */
        case XK_KP_Subtract:   /* NumLock on */
            *keysym = '-';
            break;
        case XK_F25:           /* NumLock off */
        case XK_KP_Divide:     /* NumLock on */
            *keysym = '/';
            break;
        case XK_F26:           /* NumLock off */
        case XK_KP_Multiply:   /* NumLock on */
            *keysym = '*';
            break;
        case XK_KP_Equal:
            *keysym = '=';
            break;
        case XK_KP_0:
            *keysym = '0';
            break;
        case XK_KP_1:
            *keysym = '1';
            break;
        case XK_KP_2:
            *keysym = '2';
            break;
        case XK_KP_3:
            *keysym = '3';
            break;
        case XK_KP_4:
            *keysym = '4';
            break;
        case XK_KP_5:
            *keysym = '5';
            break;
        case XK_KP_6:
            *keysym = '6';
            break;
        case XK_KP_7:
            *keysym = '7';
            break;
        case XK_KP_8:
            *keysym = '8';
            break;
        case XK_KP_9:
            *keysym = '9';
            break;
        case XK_KP_Left:  /* Bug 4350175 */
            *keysym = XK_Left;
            event->xkey.keycode = XKeysymToKeycode(awt_display, *keysym);
            break;
        case XK_KP_Up:
            *keysym = XK_Up;
            event->xkey.keycode = XKeysymToKeycode(awt_display, *keysym);
            break;
        case XK_KP_Right:
            *keysym = XK_Right;
            event->xkey.keycode = XKeysymToKeycode(awt_display, *keysym);
            break;
        case XK_KP_Down:
            *keysym = XK_Down;
            event->xkey.keycode = XKeysymToKeycode(awt_display, *keysym);
            break;
        case XK_KP_Home:
            *keysym = XK_Home;
            event->xkey.keycode = XKeysymToKeycode(awt_display, *keysym);
            break;
        case XK_KP_End:
            *keysym = XK_End;
            event->xkey.keycode = XKeysymToKeycode(awt_display, *keysym);
            break;
        case XK_KP_Page_Up:
            *keysym = XK_Page_Up;
            event->xkey.keycode = XKeysymToKeycode(awt_display, *keysym);
            break;
        case XK_KP_Page_Down:
            *keysym = XK_Page_Down;
            event->xkey.keycode = XKeysymToKeycode(awt_display, *keysym);
            break;
        case XK_KP_Begin:
            *keysym = XK_Begin;
            event->xkey.keycode = XKeysymToKeycode(awt_display, *keysym);
            break;
        case XK_KP_Insert:
            *keysym = XK_Insert;
            event->xkey.keycode = XKeysymToKeycode(awt_display, *keysym);
            break;
        case XK_KP_Delete:
            *keysym = XK_Delete;
            event->xkey.keycode = XKeysymToKeycode(awt_display, *keysym);
            *keysym &= 0x007F;
            break;
        case XK_KP_Enter:
            *keysym = XK_Linefeed;
            event->xkey.keycode = XKeysymToKeycode(awt_display, XK_Return);
            *keysym &= 0x007F;
            break;
        default:
            break;
    }

    if (originalKeysym != *keysym) {
        DTRACE_PRINTLN2("In adjustKeySym: originalKeysym=%x, keysym=%x",
          originalKeysym, *keysym);
    }
}

/*
 * What a sniffer sez?
 * Xsun and Xorg if NumLock is on do two thing different:
 * keep Keypad key in different places of keysyms array and
 * ignore/obey "ModLock is ShiftLock", so we should choose.
 * People say, it's right to use behavior and not Vendor tags to decide.
 * Maybe. But why these tags were invented, then?
 * TODO: use behavior, not tags. Maybe.
 */
static Boolean
isXsunServer(XEvent *event) {
    if( awt_ServerDetected ) return awt_IsXsun;
    if( strncmp( ServerVendor( event->xkey.display ), "Sun Microsystems, Inc.", 32) ) {
        awt_ServerDetected = True;
        awt_IsXsun = False;
        return False;
    }
    // Now, it's Sun. It still may be Xorg though, eg on Solaris 10, x86.
    // Today (2005), VendorRelease of Xorg is a Big Number unlike Xsun.
    if( VendorRelease( event->xkey.display ) > 10000 ) {
        awt_ServerDetected = True;
        awt_IsXsun = False;
        return False;
    }
    awt_ServerDetected = True;
    awt_IsXsun = True;
    return True;
}
static Boolean
isKPevent(XEvent *event)
{
    /*
       Xlib manual, ch 12.7 says, as a first rule for choice of keysym:
       The numlock modifier is on and the second KeySym is a keypad KeySym. In this case,
       if the Shift modifier is on, or if the Lock modifier is on and is interpreted as ShiftLock,
       then the first KeySym is used, otherwise the second KeySym is used.

       However, Xsun server does ignore ShiftLock and always takes 3-rd element from an array.

       So, is it a keypad keysym?
     */
    jint mods = getModifiers(event->xkey.state, 0, event->xkey.keycode);
    Boolean bsun = isXsunServer( event );

    return IsKeypadKey( XKeycodeToKeysym(event->xkey.display, event->xkey.keycode,(bsun && !awt_UseXKB ? 2 : 1) ) );
}
/*
 * In a next redesign, get rid of this code altogether.
 *
 */
static void
handleKeyEventWithNumLockMask_New(XEvent *event, KeySym *keysym)
{
    KeySym originalKeysym = *keysym;
    if( !isKPevent( event ) ) {
        return;
    }
    if( isXsunServer( event ) && !awt_UseXKB ) {
        if( (event->xkey.state & ShiftMask) ) { // shift modifier is on
            *keysym = XKeycodeToKeysym(event->xkey.display,
                                   event->xkey.keycode, 3);
         }else {
            *keysym = XKeycodeToKeysym(event->xkey.display,
                                   event->xkey.keycode, 2);
         }
    } else {
        if( (event->xkey.state & ShiftMask) || // shift modifier is on
            ((event->xkey.state & LockMask) && // lock modifier is on
             (awt_ModLockIsShiftLock)) ) {     // it is interpreted as ShiftLock
            *keysym = XKeycodeToKeysym(event->xkey.display,
                                   event->xkey.keycode, 0);
        }else{
            *keysym = XKeycodeToKeysym(event->xkey.display,
                                   event->xkey.keycode, 1);
        }
    }
}

/* Called from handleKeyEvent.
 * The purpose of this function is to make some adjustments to keysyms
 * that have been found to be necessary when the NumLock mask is set.
 * They come from various bug fixes and rearchitectures.
 * This function is meant to be called when
 * (event->xkey.state & awt_NumLockMask) is TRUE.
 */
static void
handleKeyEventWithNumLockMask(XEvent *event, KeySym *keysym)
{
    KeySym originalKeysym = *keysym;

#ifndef __linux__
    /* The following code on Linux will cause the keypad keys
     * not to echo on JTextField when the NumLock is on. The
     * keysyms will be 0, because the last parameter 2 is not defined.
     * See Xlib Programming Manual, O'Reilly & Associates, Section
     * 9.1.5 "Other Keyboard-handling Routines", "The meaning of
     * the keysym list beyond the first two (unmodified, Shift or
     * Shift Lock) is not defined."
     */

    /* Translate again with NumLock as modifier. */
    /* ECH - I wonder why we think that NumLock corresponds to 2?
     * On Linux, we've seen xmodmap -pm yield mod2 as NumLock,
     * but I don't know that it will be for every configuration.
     * Perhaps using the index (modn in awt_MToolkit.c:setup_modifier_map)
     * would be more correct.
     */
    *keysym = XKeycodeToKeysym(event->xkey.display,
                               event->xkey.keycode, 2);
    if (originalKeysym != *keysym) {
        DTRACE_PRINTLN3("%s=%x, keysym=%x",
          "In handleKeyEventWithNumLockMask ifndef linux: originalKeysym",
          originalKeysym, *keysym);
    }
#endif

    /* Note: the XK_R? key assignments are for Type 4 kbds */
    switch (*keysym) {
        case XK_R13:
            *keysym = XK_KP_1;
            break;
        case XK_R14:
            *keysym = XK_KP_2;
            break;
        case XK_R15:
            *keysym = XK_KP_3;
            break;
        case XK_R10:
            *keysym = XK_KP_4;
            break;
        case XK_R11:
            *keysym = XK_KP_5;
            break;
        case XK_R12:
            *keysym = XK_KP_6;
            break;
        case XK_R7:
            *keysym = XK_KP_7;
            break;
        case XK_R8:
            *keysym = XK_KP_8;
            break;
        case XK_R9:
            *keysym = XK_KP_9;
            break;
        case XK_KP_Insert:
            *keysym = XK_KP_0;
            break;
        case XK_KP_Delete:
            *keysym = XK_KP_Decimal;
            break;
        case XK_R4:
            *keysym = XK_KP_Equal;  /* Type 4 kbd */
            break;
        case XK_R5:
            *keysym = XK_KP_Divide;
            break;
        case XK_R6:
            *keysym = XK_KP_Multiply;
            break;
        /*
         * Need the following keysym changes for Linux key releases.
         * Sometimes the modifier state gets messed up, so we get a
         * KP_Left when we should get a KP_4, for example.
         * XK_KP_Insert and XK_KP_Delete were already handled above.
         */
        case XK_KP_Left:
            *keysym = XK_KP_4;
            break;
        case XK_KP_Up:
            *keysym = XK_KP_8;
            break;
        case XK_KP_Right:
            *keysym = XK_KP_6;
            break;
        case XK_KP_Down:
            *keysym = XK_KP_2;
            break;
        case XK_KP_Home:
            *keysym = XK_KP_7;
            break;
        case XK_KP_End:
            *keysym = XK_KP_1;
            break;
        case XK_KP_Page_Up:
            *keysym = XK_KP_9;
            break;
        case XK_KP_Page_Down:
            *keysym = XK_KP_3;
            break;
        case XK_KP_Begin:
            *keysym = XK_KP_5;
            break;
        default:
            break;
    }

    if (originalKeysym != *keysym) {
        DTRACE_PRINTLN2("In handleKeyEventWithNumLockMask: originalKeysym=%x, keysym=%x",
          originalKeysym, *keysym);
    }
}

static void
handleKeyEvent(jint keyEventId,
               XEvent *event,
               XtPointer *client_data,
               Boolean *cont,
               Boolean passEvent)
{
    KeySym keysym = NoSymbol;
    jint keycode = java_awt_event_KeyEvent_VK_UNDEFINED;
    Modifiers mods = 0;
    Boolean mapsToUnicodeChar = FALSE;
    jint keyLocation = java_awt_event_KeyEvent_KEY_LOCATION_UNKNOWN;
    jint modifiers = 0;

    DTRACE_PRINTLN4("\nEntered handleKeyEvent: type=%d, xkeycode=%x, xstate=%x, keysym=%x",
      event->type, event->xkey.keycode, event->xkey.state, keysym);

    if (currentX11InputMethodInstance != NULL
        && keyEventId == java_awt_event_KeyEvent_KEY_PRESSED
        && event->xkey.window == currentFocusWindow)
    {
        /* invokes XmbLookupString to get a committed string or keysym if any.  */
        if (awt_x11inputmethod_lookupString((XKeyPressedEvent*)event, &keysym)) {
            *cont = FALSE;
            return;
        }
    }

    /* Ignore the keysym found immediately above in
     * awt_x11inputmethod_lookupString; the methodology in that function
     * sometimes returns incorrect results.
     *
     * Get keysym without taking modifiers into account first.
     * This keysym is not necessarily for the character that was typed:
     * it is for the primary layer.  So, if $ were typed by pressing
     * shift-4, this call should give us 4, not $
     *
     * We only want this keysym so we can use it to index into the
     * keymapTable to get the Java keycode associated with the
     * primary layer key that was pressed.
     */
    keysym = XKeycodeToKeysym(event->xkey.display, event->xkey.keycode, 0);

    /* Linux: Sometimes the keysym returned is uppercase when CapsLock is
     * on and LockMask is not set in event->xkey.state.
     */
    if (keysym >= (KeySym) 'A' && keysym <= (KeySym) 'Z') {
        event->xkey.state |= LockMask;
        keysym = (KeySym) tolower((int32_t) keysym);
    }

    DTRACE_PRINTLN4("In handleKeyEvent: type=%d, xkeycode=%x, xstate=%x, keysym=%x",
      event->type, event->xkey.keycode, event->xkey.state, keysym);

    if (keysym == NoSymbol) {
        *cont = TRUE;
        return;
    }

    if (keysym < (KeySym) 256) {
        keysymToAWTKeyCode(keysym, &keycode, &mapsToUnicodeChar, &keyLocation);

        /* Now get real keysym which looks at modifiers
         * XtGetActionKeySym() returns wrong value with Kana Lock,
         * so use XtTranslateKeycode().
         */
        XtTranslateKeycode(event->xkey.display, (KeyCode) event->xkey.keycode,
                           event->xkey.state, &mods, &keysym);
        DTRACE_PRINTLN6("%s: type=%d, xkeycode=%x, xstate=%x, keysym=%x, xmods=%d",
          "In handleKeyEvent keysym<256 ", event->type, event->xkey.keycode,
          event->xkey.state, keysym, mods);

        /* Linux: With caps lock on, chars echo lowercase. */
        if ((event->xkey.state & LockMask) &&
             (keysym >= (KeySym) 'a' && keysym <= (KeySym) 'z'))
        {
            keysym = (KeySym) toupper((int32_t) keysym);
        }

        if ((event->xkey.state & ControlMask)) {
            switch (keysym) {
                case '[':
                case ']':
                case '\\':
                case '_':
                    keysym -= 64;
                    break;
                default:
                    if (isalpha((int32_t) keysym)) {
                        keysym = (KeySym) tolower((int32_t) keysym) - 'a' + 1;
                    }
                    break;
            }
        }

        if (keysym >= (KeySym) XK_kana_fullstop &&
            keysym <= (KeySym) XK_semivoicedsound) {
            /*
             * 0xff61 is Unicode value of first XK_kana_fullstop.
             * We need X Keysym to Unicode map in post1.1 release
             * to support more intenational keyboard.
             */
            keysym = keysym - XK_kana_fullstop + 0xff61;
        }

        modifiers = getModifiers(event->xkey.state, 0, keycode);
        DTRACE_PRINTLN6("%s: type=%d, xkeycode=%x, xstate=%x, keysym=%x, AWTmodifiers=%d",
          "In handleKeyEvent keysym<256 ", event->type, event->xkey.keycode,
          event->xkey.state, keysym, modifiers);

        awt_post_java_key_event(client_data,
                                keyEventId,
                                (passEvent == TRUE) ?  event : NULL,
                                event->xkey.time,
                                keycode,
                                (jchar) keysym,
                                modifiers,
                                keyLocation,
                                event);

        if (keyEventId == java_awt_event_KeyEvent_KEY_PRESSED) {
            awt_post_java_key_event(client_data,
              java_awt_event_KeyEvent_KEY_TYPED,
              NULL,
              event->xkey.time,
              java_awt_event_KeyEvent_VK_UNDEFINED,
              (jchar) keysym,
              modifiers,
              java_awt_event_KeyEvent_KEY_LOCATION_UNKNOWN,
              event);

        }
    } else {
        if (event->xkey.state & awt_NumLockMask) {
            if( awt_UseType4Patch ) {
                handleKeyEventWithNumLockMask(event, &keysym);
            }else{
                handleKeyEventWithNumLockMask_New(event, &keysym);
            }
        }

        if (keysym == XK_ISO_Left_Tab) {
            keysym = XK_Tab;
        }

        /* The keysym here does not consider modifiers, so these results
         * are relevant to the KEY_PRESSED event only, not the KEY_TYPED
         */
        keysymToAWTKeyCode(keysym, &keycode, &mapsToUnicodeChar, &keyLocation);
        DTRACE_PRINTLN3("In handleKeyEvent: keysym=%x, AWTkeycode=%x, mapsToUnicodeChar=%d",
          keysym, keycode, mapsToUnicodeChar);

        if (keycode == java_awt_event_KeyEvent_VK_UNDEFINED) {
            *cont = TRUE;
            return;
        }

        /* Need to take care of keysyms > 0xFFFF here
         * Most of these keys don't map to unicode characters, but some do.
         *
         * For keys that don't map to unicode characters, the keysym
         * is irrelevant at this point.  We set the keysym to zero
         * to ensure that the switch statement immediately below
         * this function call (in adjustKeySym) won't incorrectly act
         * on them after the high bits are stripped off.
         *
         * For keys that do map to unicode characters, we change the keysym
         * to the equivalent that is < 0xFFFF
         */
        handleVendorKeySyms(event, &keysym);

        /* This function is a conglomeration of bug fixes that adjust
         * the keysym and XEvent keycode for this key event.
         */
        adjustKeySym(event, &keysym);

        modifiers = getModifiers(event->xkey.state, 0, keycode);

        DTRACE_PRINTLN6("%s: type=%d, xkeycode=%x, xstate=%x, keysym=%x, xmods=%d",
          "In handleKeyEvent keysym>=256 ", event->type, event->xkey.keycode,
          event->xkey.state, keysym, mods);
        DTRACE_PRINTLN2("                              AWTkeycode=%x, AWTmodifiers=%d",
          keycode, modifiers);

        awt_post_java_key_event(client_data,
          keyEventId,
          (passEvent == TRUE) ? event : NULL,
          event->xkey.time,
          keycode,
          (jchar) (mapsToUnicodeChar ? keysym :
            java_awt_event_KeyEvent_CHAR_UNDEFINED),
          modifiers,
          keyLocation,
          event);

        /* If this was a keyPressed event, we may need to post a
         * keyTyped event, too.  Otherwise, return.
         */
        if (keyEventId == java_awt_event_KeyEvent_KEY_RELEASED) {
            return;
        }
        DTRACE_PRINTLN("This is a keyPressed event");

        /* XtTranslateKeycode seems to return slightly bogus values for the
         * Escape key (keysym==1004ff69==osfXK_Cancel, xmods=2) on Solaris,
         * so we just create the KEY_TYPED as a special case for Escape here.
         * (Linux works fine, and this was also okay running under VNC.)
         */
        if (keycode == java_awt_event_KeyEvent_VK_ESCAPE) {
            awt_post_java_key_event(client_data,
              java_awt_event_KeyEvent_KEY_TYPED,
              NULL,
              event->xkey.time,
              java_awt_event_KeyEvent_VK_UNDEFINED,
              (jchar) keysym,
              modifiers,
              java_awt_event_KeyEvent_KEY_LOCATION_UNKNOWN,
              event);

            DTRACE_PRINTLN("Posted a keyTyped event for VK_ESCAPE");
            return;
        }

        /* Now get real keysym which looks at modifiers for keyTyped event.
         * XtGetActionKeySym() returns wrong value with Kana Lock,
         * so use XtTranslateKeycode().
         */
        XtTranslateKeycode(event->xkey.display, (KeyCode) event->xkey.keycode,
                           event->xkey.state, &mods, &keysym);
        DTRACE_PRINTLN6("%s: type=%d, xkeycode=%x, xstate=%x, keysym=%x, xmods=%d",
          "In handleKeyEvent keysym>=256 ", event->type, event->xkey.keycode,
          event->xkey.state, keysym, mods);

        if (keysym == NoSymbol) {
            return;
        }

        if (event->xkey.state & awt_NumLockMask) {
            if( awt_UseType4Patch ) {
                handleKeyEventWithNumLockMask(event, &keysym);
            }else{
                handleKeyEventWithNumLockMask_New(event, &keysym);
            }
        }

        if (keysym == XK_ISO_Left_Tab) {
            keysym = XK_Tab;
        }

        /* Map the real keysym to a Java keycode */
        keysymToAWTKeyCode(keysym, &keycode, &mapsToUnicodeChar, &keyLocation);
        DTRACE_PRINTLN3("In handleKeyEvent: keysym=%x, AWTkeycode=%x, mapsToUnicodeChar=%d",
          keysym, keycode, mapsToUnicodeChar);

        /* If it doesn't map to a Unicode character, don't post a keyTyped event */
        if (!mapsToUnicodeChar) {
            return;
        }

        handleVendorKeySyms(event, &keysym);
        adjustKeySym(event, &keysym);
        DTRACE_PRINT4("In handleKeyEvent: type=%d, xkeycode=%x, xstate=%x, keysym=%x",
          event->type, event->xkey.keycode, event->xkey.state, keysym);
        DTRACE_PRINTLN2(", AWTkeycode=%x, AWTmodifiers=%d", keycode, modifiers);

        awt_post_java_key_event(client_data,
          java_awt_event_KeyEvent_KEY_TYPED,
          NULL,
          event->xkey.time,
          java_awt_event_KeyEvent_VK_UNDEFINED,
          (jchar) keysym,
          modifiers,
          java_awt_event_KeyEvent_KEY_LOCATION_UNKNOWN,
          event);
    }
}


static void
translateXY(Widget w, jint *xp, jint *yp)
{
    Position wx, wy;

    XtVaGetValues(w, XmNx, &wx, XmNy, &wy, NULL);
    *xp += wx;
    *yp += wy;
}


/*
 * Part fix for bug id 4017222. Return the root widget of the Widget parameter.
 */
Widget
getRootWidget(Widget w) {
    if(!w) return NULL;

    if(XtParent(w))
        return getRootWidget(XtParent(w));
    else
        return w;
}

#define ABS(x) ((x) < 0 ? -(x) : (x))

/* This proc is the major AWT engine for processing X events
 * for Java components and is the proc responsible for taking
 * X events and posting their corresponding Java event to the
 * AWT EventQueue.  It is set up to be called both from an Xt
 * event handler and directly from MToolkit.c:shouldDispatchToWidget().
 * For the latter case, the "passEvent" parameter will be true,
 * which means that the event is being posted on the Java queue
 * BEFORE it is being passed to Xt and so a copy of the X event
 * must be stored within the Java event structure so it can be
 * dispatched to Xt later on.
 */
void
awt_canvas_handleEvent(Widget w, XtPointer client_data,
                       XEvent * event, struct WidgetInfo *winfo,
                       Boolean * cont, Boolean passEvent)
{
    static jint clickCount = 1;
    static XtPointer lastPeer = NULL;
    static Time lastTime = 0;
    static jint lastx = 0;
    static jint lasty = 0;
    static int32_t rbutton = 0;
    static int32_t lastButton = 0;
    Boolean popupTrigger;
    jint x, y;
    jint modifiers = 0;
    jint button = java_awt_event_MouseEvent_NOBUTTON;
    uint32_t fullRelease = 0;
    WidgetClass wclass = NULL;

    /* Any event handlers which take peer instance pointers as
     * client_data should check to ensure the widget has not been
     * marked as destroyed as a result of a dispose() call on the peer
     * (which can result in the peer instance pointer already haven
     * been gc'd by the time this event is processed)
     */
    if (w->core.being_destroyed) {
        return;
    }
    *cont = FALSE;

    switch (event->type) {
        case SelectionClear:
        case SelectionNotify:
        case SelectionRequest:
            *cont = TRUE;
            break;
        case GraphicsExpose:
        case Expose:
            HandleExposeEvent(w, (jobject) client_data, event);
            break;
        case FocusIn:
        case FocusOut:
            *cont = TRUE;
            updateCursor(client_data, CACHE_UPDATE); // 4840883
            // We no longer listen to the Motif focus notifications.
            // Instead we call focus callbacks in the times we think
            // appropriate trying to simulate correct Motif widget system
            // behavior.
            break;
        case ButtonPress:
            x = (jint) event->xbutton.x;
            y = (jint) event->xbutton.y;

            if (lastPeer == client_data &&
                lastButton == event->xbutton.button &&
                (event->xbutton.time - lastTime) <= (Time) awt_multiclick_time) {
                    clickCount++;
            } else {
                clickCount = 1;
                lastPeer = client_data;
                lastButton = event->xbutton.button;
                lastx = x;
                lasty = y;
            }
            lastTime = event->xbutton.time;

            /* On MouseEvent.MOUSE_PRESSED, RELEASED and CLICKED  only new modifiers and
             * modifier for changed mouse button are set.
             */
            button = getButton(event->xbutton.button);
            modifiers = getModifiers(event->xbutton.state, button, 0);


            /* If the widget is a subwidget on a component we need to
             * translate the x,y into the coordinate space of the component.
             */
            if (winfo != NULL && winfo->widget != winfo->origin) {
                translateXY(winfo->widget, &x, &y);
            }

            if (XtIsSubclass(w, xmScrollBarWidgetClass) && findWidgetInfo(w) != NULL) {
                passEvent = FALSE;
                *cont = TRUE;
            }

            /* Mouse wheel events come in as button 4 (wheel up) and
             * button 5 (wheel down).
             */
            if (lastButton == 4 || lastButton == 5) {
                *cont = FALSE;
                awt_post_java_mouse_event(client_data,
                                          java_awt_event_MouseEvent_MOUSE_WHEEL,
                                          (passEvent == TRUE) ? event : NULL,
                                          event->xbutton.time,
                                          modifiers,
                                          x, y,
                                          (jint) (event->xbutton.x_root),
                                          (jint) (event->xbutton.y_root),
                                          clickCount,
                                          False,
                                          lastButton == 4 ? -1 : 1,
                                          java_awt_event_MouseEvent_NOBUTTON);
                /* we're done with this event */
                break;
            }

            /* (4168006) Find out out how many buttons we have
             * If this is a two button system Right == 2
             * If this is a three button system Right == 3
             */
            if ( rbutton == 0 ) {
                unsigned char map[5];
                rbutton = XGetPointerMapping ( awt_display, map, 3 );
            }

            if (event->xbutton.button == rbutton || event->xbutton.button > 2) {
                popupTrigger = True;
            } else {
                popupTrigger = False;
            }

            awt_post_java_mouse_event(client_data,
                                      java_awt_event_MouseEvent_MOUSE_PRESSED,
                                      (passEvent == TRUE) ? event : NULL,
                                      event->xbutton.time,
                                      modifiers,
                                      x, y,
                                      (jint) (event->xbutton.x_root),
                                      (jint) (event->xbutton.y_root),
                                      clickCount,
                                      popupTrigger, 0,
                                      button);

            drag_source = w;

            break;
        case ButtonRelease:
            if (XtIsSubclass(w, xmScrollBarWidgetClass) && findWidgetInfo(w) != NULL) {
                passEvent = FALSE;
                *cont = TRUE;
            }

            /*
             * For button 4 & 5 (mouse wheel) we can simply ignore this event.
             * We dispatch the wheel on the ButtonPress.
             */
            if (event->xbutton.button == 4 ||
                event->xbutton.button == 5) {
                break;
            }

            prevWidget = NULL;
            x = (jint) event->xbutton.x;
            y = (jint) event->xbutton.y;
            /* On MouseEvent.MOUSE_PRESSED, RELEASED and CLICKED  only new modifiers and
             * modifier for changed mouse button are set.
             */
            button = getButton(event->xbutton.button);
            modifiers = getModifiers(event->xbutton.state, button, 0);

            fullRelease =
              ((event->xbutton.state & Button1Mask) &&
               !(event->xbutton.state & Button2Mask) &&
               !(event->xbutton.state & Button3Mask) &&
               (event->xbutton.button == Button1)) ||
              (!(event->xbutton.state & Button1Mask) &&
               (event->xbutton.state & Button2Mask) &&
               !(event->xbutton.state & Button3Mask) &&
               (event->xbutton.button == Button2)) ||
              (!(event->xbutton.state & Button1Mask) &&
               !(event->xbutton.state & Button2Mask) &&
               (event->xbutton.state & Button3Mask) &&
               (event->xbutton.button == Button3));

            /* If the widget is a subwidget on a component we need to
             * translate the x,y into the coordinate space of the component.
             */
            if (winfo != NULL && winfo->widget != winfo->origin) {
                translateXY(winfo->widget, &x, &y);
            }
            drag_source = NULL;
            awt_post_java_mouse_event(client_data,
                                      java_awt_event_MouseEvent_MOUSE_RELEASED,
                                      (passEvent == TRUE) ? event : NULL,
                                      event->xbutton.time,
                                      modifiers,
                                      x, y,
                                      (jint) (event->xbutton.x_root),
                                      (jint) (event->xbutton.y_root),
                                      clickCount,
                                      FALSE, 0,
                                      button);

            if (lastPeer == client_data) {
                awt_post_java_mouse_event(client_data,
                                          java_awt_event_MouseEvent_MOUSE_CLICKED,
                                          NULL,
                                          event->xbutton.time,
                                          modifiers,
                                          x, y,
                                          (jint) (event->xbutton.x_root),
                                          (jint) (event->xbutton.y_root),
                                          clickCount,
                                          FALSE, 0,
                                          button);
            }

            if (fullRelease) {
                updateCursor(client_data, UPDATE_ONLY);
            }

        break;
        case MotionNotify:
            if (XtIsSubclass(w, xmScrollBarWidgetClass) && findWidgetInfo(w) != NULL) {
                passEvent = FALSE;
                *cont = TRUE;
            }

            x = (jint) event->xmotion.x;
            y = (jint) event->xmotion.y;

            /* If a motion comes in while a multi-click is pending,
             * allow a smudge factor so that moving the mouse by a small
             * amount does not wipe out the multi-click state variables.
             */
            if (!(lastPeer == client_data &&
                  ((event->xmotion.time - lastTime) <= (Time) awt_multiclick_time) &&
                  (ABS(lastx - x) < awt_multiclick_smudge &&
                   ABS(lasty - y) < awt_multiclick_smudge))) {
                clickCount = (jint) 0;
                lastTime = (Time) 0;
                lastPeer = NULL;
                lastx = (jint) 0;
                lasty = (jint) 0;
            }
            /* On other MouseEvent only new modifiers and
             * old mouse modifiers are set.
             */
            modifiers = getModifiers(event->xmotion.state, 0, 0);

            /* If the widget is a subwidget on a component we need to
             * translate the x,y into the coordinate space of the component.
             */
            if (winfo != NULL && winfo->widget != winfo->origin) {
                translateXY(winfo->widget, &x, &y);
            }
            if (event->xmotion.state & (Button1Mask | Button2Mask | Button3Mask)) {
                if (!clickCount) {

            /*
                Fix for bug id 4017222. A button is down, so EnterNotify and
                LeaveNotify events are only being sent to this widget. If
                the pointer has moved over a new widget, manually generate
                MouseEnter and MouseExit and send them to the right widgets.
            */

                extern Widget awt_WidgetAtXY(Widget root, Position x, Position y);
                extern Widget awt_GetWidgetAtPointer();
                Widget currentWidget=NULL, topLevelW;
                Position wx=0, wy=0;

                XtTranslateCoords(w, (int32_t) x, (int32_t) y, &wx, &wy);
                /* Get the top level widget underneath the mouse pointer */
                currentWidget = awt_GetWidgetAtPointer();
                /* Get the exact widget at the current XY from the top level */
                currentWidget = awt_WidgetAtXY(currentWidget, wx, wy);
                if ((prevWidget != NULL) && (prevWidget != w) &&
                    (currentWidget != prevWidget) && awt_isAwtWidget(prevWidget) &&
                    !prevWidget->core.being_destroyed) {
                    XtPointer userData=NULL;
                    XtVaGetValues(prevWidget, XmNuserData, &userData, NULL);
                    if (userData) {
                        awt_post_java_mouse_event(userData,
                            java_awt_event_MouseEvent_MOUSE_EXITED,
                            (passEvent==TRUE) ? event : NULL,
                            event->xmotion.time,
                            modifiers,
                            x, y,
                            (jint) (event->xmotion.x_root),
                            (jint) (event->xmotion.y_root),
                            clickCount,
                            FALSE, 0,
                            java_awt_event_MouseEvent_NOBUTTON);
                    }
                }

                if ((currentWidget != NULL) && (currentWidget != w) &&
                    (currentWidget != prevWidget) && awt_isAwtWidget(currentWidget)) {
                    XtPointer userData=NULL;
                    XtVaGetValues(currentWidget, XmNuserData, &userData, NULL);
                    if (userData) {
                        awt_post_java_mouse_event(userData,
                            java_awt_event_MouseEvent_MOUSE_ENTERED,
                            (passEvent==TRUE) ? event : NULL,
                            event->xmotion.time,
                            modifiers,
                            x, y,
                            (jint) (event->xmotion.x_root),
                            (jint) (event->xmotion.y_root),
                            clickCount,
                            FALSE, 0,
                            java_awt_event_MouseEvent_NOBUTTON);
                    }

                    updateCursor(userData, CACHE_ONLY);
                    awt_util_setCursor(currentWidget, None);
                }

                prevWidget = currentWidget;
                /* end 4017222 */


                awt_post_java_mouse_event(client_data,
                                          java_awt_event_MouseEvent_MOUSE_DRAGGED,
                                          (passEvent == TRUE) ? event : NULL,
                                          event->xmotion.time,
                                          modifiers,
                                          x, y,
                                          (jint) (event->xmotion.x_root),
                                          (jint) (event->xmotion.y_root),
                                          clickCount,
                                          FALSE, 0,
                                          java_awt_event_MouseEvent_NOBUTTON);

            }
            } else {

                awt_post_java_mouse_event(client_data,
                                          java_awt_event_MouseEvent_MOUSE_MOVED,
                                          (passEvent == TRUE) ? event : NULL,
                                          event->xmotion.time,
                                          modifiers,
                                          x, y,
                                          (jint) (event->xmotion.x_root),
                                          (jint) (event->xmotion.y_root),
                                          clickCount,
                                          FALSE, 0,
                                          java_awt_event_MouseEvent_NOBUTTON);
            }
            break;
        case KeyPress:
            handleKeyEvent(java_awt_event_KeyEvent_KEY_PRESSED,
                           event, client_data, cont, TRUE);
            break;
        case KeyRelease:
            handleKeyEvent(java_awt_event_KeyEvent_KEY_RELEASED,
                           event, client_data, cont, TRUE);
            break;
        case EnterNotify:
        case LeaveNotify:
/*
  printf("----->%s on %s(%x):mode=%d detail = %d\n",
  event->type == EnterNotify?"EnterNotify":"LeaveNotify",
  XtName(w), w,
  ((XCrossingEvent*)event)->mode, ((XCrossingEvent*)event)->detail);
*/
        if (event->xcrossing.mode != NotifyNormal ||
                ((event->xcrossing.detail == NotifyVirtual ||
                  event->xcrossing.detail == NotifyNonlinearVirtual) &&
                 !XtIsSubclass(w, xmScrolledWindowWidgetClass))) {
                *cont = TRUE;
                return;
            }

            /* fix for 4454304.
             * We should not post MOUSE_ENTERED and MOUSE_EXITED events
             * if the mouse pointer is in the place between component
             * and its scrollbars.
             * kdm@sparc.spb.su
             */
            if (winfo != NULL && winfo->widget != NULL) {
                wclass = XtClass(winfo->widget);
                if (event->xcrossing.subwindow == NULL
                    && event->xcrossing.detail == NotifyInferior
                    && (wclass == xmTextWidgetClass
                        || wclass == xmListWidgetClass)) {
                    *cont = TRUE;
                    return;
                }
            }

            clickCount = (jint) 0;
            lastTime = (Time) 0;
            lastPeer = NULL;

            /* On other MouseEvent only new modifiers and
             * old mouse modifiers are set.
             */
            modifiers = getModifiers(event->xcrossing.state, 0, 0);

            switch (event->type) {
                case EnterNotify:
                    awt_post_java_mouse_event(client_data,
                                              java_awt_event_MouseEvent_MOUSE_ENTERED,
                                              (passEvent == TRUE) ? event : NULL,
                                              event->xcrossing.time,
                                              modifiers,
                                              (jint) (event->xcrossing.x),
                                              (jint) (event->xcrossing.y),
                                              (jint) (event->xcrossing.x_root),
                                              (jint) (event->xcrossing.y_root),
                                              clickCount,
                                              FALSE, 0,
                                              java_awt_event_MouseEvent_NOBUTTON);
                    if (!(event->xcrossing.state
                        & (Button1Mask | Button2Mask | Button3Mask))) {
                        updateCursor(client_data, CACHE_UPDATE);
                    }

                    break;
                case LeaveNotify:
                    awt_post_java_mouse_event(client_data,
                                              java_awt_event_MouseEvent_MOUSE_EXITED,
                                              (passEvent == TRUE) ? event : NULL,
                                              event->xcrossing.time,
                                              modifiers,
                                              (jint) (event->xcrossing.x),
                                              (jint) (event->xcrossing.y),
                                              (jint) (event->xcrossing.x_root),
                                              (jint) (event->xcrossing.y_root),
                                              clickCount,
                                              FALSE, 0,
                                              java_awt_event_MouseEvent_NOBUTTON);
                    break;
            }
            break;

        default:
            break;
    }
}

/*
 * client_data is MComponentPeer subclass
 */
void
awt_canvas_event_handler(Widget w, XtPointer client_data,
                         XEvent * event, Boolean * cont)
{
    awt_canvas_handleEvent(w, client_data, event, NULL, cont, FALSE);
}

void
awt_canvas_reconfigure(struct FrameData *wdata)
{
    Dimension w, h;

    if (wdata->winData.comp.widget == NULL ||
        XtParent(wdata->winData.comp.widget) == NULL) {
        return;
    }
    XtVaGetValues(XtParent(wdata->winData.comp.widget), XmNwidth, &w, XmNheight, &h, NULL);
    XtConfigureWidget(wdata->winData.comp.widget,
                      -(wdata->left),
                      -(wdata->top),
                      w + (wdata->left + wdata->right),
                      h + (wdata->top + wdata->bottom),
                      0);
}

static void
Wrap_event_handler(Widget widget,
                   XtPointer client_data,
                   XmDrawingAreaCallbackStruct * call_data)
{
    awt_canvas_reconfigure((struct FrameData *) client_data);
}


Widget
awt_canvas_create(XtPointer this,
                  Widget parent,
                  char *base,
                  int32_t width,
                  int32_t height,
                  Boolean parentIsFrame,
                  struct FrameData *wdata,
                  AwtGraphicsConfigDataPtr awtData)
{
    Widget newCanvas;
    Widget wrap;
#define MAX_ARGC 20
    Arg args[MAX_ARGC];
    int32_t argc;
    char name[128];
    static XtTranslations translationKeyDown = NULL;

    JNIEnv *env = (JNIEnv *)JNU_GetEnv(jvm, JNI_VERSION_1_2);


    if (parent == NULL) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        return NULL;
    }
    if (width == 0) {
        width = 1;
    }
    if (height == 0) {
        height = 1;
    }

    if (wdata != NULL) {
        argc = 0;
        if  (!parentIsFrame)
        {
            XtSetArg(args[argc], XmNwidth, width);
            argc++;
            XtSetArg(args[argc], XmNheight, height);
            argc++;
        }
        XtSetArg(args[argc], XmNmarginWidth, 0);
        argc++;
        XtSetArg(args[argc], XmNmarginHeight, 0);
        argc++;
        XtSetArg(args[argc], XmNspacing, 0);
        argc++;
        XtSetArg(args[argc], XmNresizePolicy, XmRESIZE_NONE);
        argc++;
        /* check for overflowing name? */
        strcpy(name, base);
        strcat(name, "wrap");

        DASSERT(!(argc > MAX_ARGC));
        wrap = XmCreateDrawingArea(parent, name, args, argc);
        if  (!parentIsFrame)
        {
            /* Fixing bugs in frame module (awt_Frame.c).  It will now
               provide the resize handling for this inner/parent canvas.*/
            XtAddCallback(wrap, XmNresizeCallback,
                          (XtCallbackProc) Wrap_event_handler, wdata);
        }
        XtManageChild(wrap);
    } else {
        wrap = parent;
    }

    /* check for overflowing name? */
    strcpy(name, base);
    strcat(name, "canvas");

    argc = 0;
    XtSetArg(args[argc], XmNspacing, 0);
    argc++;
    if  (!parentIsFrame)
    {
        XtSetArg(args[argc], XmNwidth, width);
        argc++;
        XtSetArg(args[argc], XmNheight, height);
        argc++;
    }
    XtSetArg(args[argc], XmNmarginHeight, 0);
    argc++;
    XtSetArg(args[argc], XmNmarginWidth, 0);
    argc++;
    XtSetArg(args[argc], XmNresizePolicy, XmRESIZE_NONE);
    argc++;
    XtSetArg(args[argc], XmNuserData, this);
    argc++;
    /* Fixed 4059430, 3/11/98, robi.khan@eng
     * install insert proc callback so components are ordered correctly
     * when added directly to frame/dialogs/windows
     */
    XtSetArg(args[argc], XmNinsertPosition, (XtPointer) awt_util_insertCallback);
    argc++;

    if (awtData != getDefaultConfig(awtData->awt_visInfo.screen)) {
        XtSetArg (args[argc], XtNvisual, awtData->awt_visInfo.visual); argc++;
        XtSetArg (args[argc], XmNdepth, awtData->awt_depth); argc++;
        XtSetArg (args[argc], XmNscreen,
                  ScreenOfDisplay(awt_display,
                                  awtData->awt_visInfo.screen)); argc++;

        if (awtData->awt_cmap == None) {
            awtJNI_CreateColorData (env, awtData, 1);
        }

        XtSetArg (args[argc], XmNcolormap, awtData->awt_cmap); argc++;

        DASSERT(!(argc > MAX_ARGC));
        newCanvas = XtCreateWidget(name, vDrawingAreaClass, wrap,
                                   args, argc);

    } else {
        newCanvas = XtCreateWidget(name, xDrawingAreaClass,
            wrap, args, argc);
    }

    XtSetMappedWhenManaged(newCanvas, False);
    XtManageChild(newCanvas);
/*
  XXX: causes problems on 2.5
  if (!scrollBugWorkAround) {
  awt_setWidgetGravity(newCanvas, StaticGravity);
  }
*/
    /* Fixed 4250354 7/28/99 ssi@sparc.spb.su
     * XtParseTranslationTable leaks in old ver of Xtoolkit
     * and result should be deletetd in any case
     *
     * XtOverrideTranslations(newCanvas,
     *                      XtParseTranslationTable("<KeyDown>:DrawingAreaInput()"));
     */
    if (NULL==translationKeyDown)
        translationKeyDown=XtParseTranslationTable("<KeyDown>:DrawingAreaInput()");
    XtOverrideTranslations(newCanvas,translationKeyDown);

    XtSetSensitive(newCanvas, True);

    return newCanvas;
}

static void
messWithGravity(Widget w, int32_t gravity)
{
    extern void awt_changeAttributes(Display * dpy, Widget w,
                                     unsigned long mask,
                                     XSetWindowAttributes * xattr);
    XSetWindowAttributes xattr;

    xattr.bit_gravity = gravity;
    xattr.win_gravity = gravity;

    awt_changeAttributes(XtDisplay(w), w, (CWBitGravity | CWWinGravity), &xattr);

}

struct MoveRecord {
    long dx;
    long dy;
};

void
moveWidget(Widget w, void *data)
{
    struct MoveRecord *rec = (struct MoveRecord *) data;

    if (XtIsRealized(w) && XmIsRowColumn(w)) {
        w->core.x -= rec->dx;
        w->core.y -= rec->dy;
    }
}

#if 0
/* Scroll entire contents of window by dx and dy.  Currently only
   dy is supported.  A negative dy means scroll backwards, i.e.,
   contents in window move down. */
void
awt_canvas_scroll(XtPointer this,
                  struct CanvasData *wdata,
                  long dx,
                  long dy)
{

    Window win;
    XWindowChanges xchgs;
    Window root;
    int x, y;
    unsigned int width, height, junk;
    Display *dpy;
    struct MoveRecord mrec;

    mrec.dx = dx;
    mrec.dy = dy;

    dpy = XtDisplay(wdata->comp.widget);
    win = XtWindow(wdata->comp.widget);

    /* REMIND: consider getting rid of this! */
    XGetGeometry(awt_display,
                 win,
                 &root,
                 &x,
                 &y,
                 &width,
                 &height,
                 &junk,
                 &junk);

    /* we need to actually update the coordinates for manager widgets, */
    /* otherwise the parent won't pass down events to them properly */
    /* after scrolling... */
    awt_util_mapChildren(wdata->comp.widget, moveWidget, 0, &mrec);

    if (dx < 0) {
        /* scrolling backward */

        if (scrollBugWorkAround) {
            messWithGravity(wdata->comp.widget, NorthWestGravity);
        }
        xchgs.x = x + dx;
        xchgs.y = y;
        xchgs.width = width - dx;
        xchgs.height = height;
        XConfigureWindow(awt_display,
                         win,
                         CWX | CWY | CWWidth | CWHeight,
                         &xchgs);

        if (scrollBugWorkAround) {
            messWithGravity(wdata->comp.widget, NorthWestGravity);
        }
        xchgs.x = x;
        xchgs.y = y;
        XConfigureWindow(awt_display,
                         win,
                         CWX | CWY,
                         &xchgs);

        xchgs.width = width;
        xchgs.height = height;
        XConfigureWindow(awt_display,
                         win,
                         CWWidth | CWHeight,
                         &xchgs);
    } else {
        /* forward scrolling */

        /* make window a little taller */
        xchgs.width = width + dx;
        xchgs.height = height;
        XConfigureWindow(awt_display,
                         win,
                         CWWidth | CWHeight,
                         &xchgs);

        if (scrollBugWorkAround) {
            messWithGravity(wdata->comp.widget, NorthEastGravity);
        }
        /* move window by amount we're scrolling */
        xchgs.x = x - dx;
        xchgs.y = y;
        XConfigureWindow(awt_display,
                         win,
                         CWX | CWY,
                         &xchgs);

        if (scrollBugWorkAround) {
            messWithGravity(wdata->comp.widget, NorthWestGravity);
        }
        /* resize to original size */
        xchgs.x = x;
        xchgs.y = y;
        xchgs.width = width;
        xchgs.height = height;
        XConfigureWindow(awt_display,
                         win,
                         CWX | CWY | CWWidth | CWHeight,
                         &xchgs);
    }
    /* Because of the weird way we're scrolling this window,
       we have to eat all the exposure events that result from
       scrolling forward, and translate them up by the amount we're
       scrolling by.

       Rather than just eating all the exposures and having the
       java code fill in what it knows is exposed, we do it this
       way.  The reason is that there might be some other exposure
       events caused by overlapping windows on top of us that we
       also need to deal with. */
    {
        XRectangle rect;

        rect.x = -1;
        eatAllExposures(dpy, win, &rect);
        if (rect.x != -1) {         /* we got at least one expose event */
            if (dx > 0) {
                rect.x -= dx;
                rect.width += dx;
            }
/*
  printf("EXPOSE (%d): %d, %d, %d, %d\n",
  dy, rect.x, rect.y, rect.width, rect.height);
*/
            callJavaExpose(this, &rect);
            XSync(awt_display, False);
        }
    }
    if (dy < 0) {
        /* scrolling backward */

        if (scrollBugWorkAround) {
            messWithGravity(wdata->comp.widget, SouthGravity);
        }
        xchgs.x = x;
        xchgs.y = y + dy;
        xchgs.width = width;
        xchgs.height = height - dy;
        XConfigureWindow(awt_display,
                         win,
                         CWX | CWY | CWWidth | CWHeight,
                         &xchgs);

        if (scrollBugWorkAround) {
            messWithGravity(wdata->comp.widget, NorthWestGravity);
        }
        xchgs.x = x;
        xchgs.y = y;
        XConfigureWindow(awt_display,
                         win,
                         CWX | CWY,
                         &xchgs);

        xchgs.width = width;
        xchgs.height = height;
        XConfigureWindow(awt_display,
                         win,
                         CWWidth | CWHeight,
                         &xchgs);
    } else {
        /* forward scrolling */

        /* make window a little taller */
        xchgs.width = width;
        xchgs.height = height + dy;
        XConfigureWindow(awt_display,
                         win,
                         CWWidth | CWHeight,
                         &xchgs);

        /* move window by amount we're scrolling */
        xchgs.x = x;
        xchgs.y = y - dy;
        XConfigureWindow(awt_display,
                         win,
                         CWX | CWY,
                         &xchgs);

        if (scrollBugWorkAround) {
            messWithGravity(wdata->comp.widget, SouthGravity);
        }
        /* resize to original size */
        xchgs.x = x;
        xchgs.y = y;
        xchgs.width = width;
        xchgs.height = height;
        XConfigureWindow(awt_display,
                         win,
                         CWX | CWY | CWWidth | CWHeight,
                         &xchgs);
        if (scrollBugWorkAround) {
            messWithGravity(wdata->comp.widget, NorthWestGravity);
        }
    }
    /* Because of the weird way we're scrolling this window,
       we have to eat all the exposure events that result from
       scrolling forward, and translate them up by the amount we're
       scrolling by.

       Rather than just eating all the exposures and having the
       java code fill in what it knows is exposed, we do it this
       way.  The reason is that there might be some other exposure
       events caused by overlapping windows on top of us that we
       also need to deal with. */
    {
        XRectangle rect;

        rect.x = -1;
        eatAllExposures(dpy, win, &rect);
        if (rect.x != -1) {         /* we got at least one expose event */
            if (dy > 0) {
                rect.y -= dy;
                rect.height += dy;
            }
            if (dx > 0) {
                rect.x -= dx;
                rect.width += dx;
            }
/*
  printf("EXPOSE (%d): %d, %d, %d, %d\n",
  dy, rect.x, rect.y, rect.width, rect.height);
*/
            callJavaExpose(this, &rect);
            XSync(awt_display, False);
        }
    }
}
#endif

extern Window focusProxyWindow;
/*
 * client_data is MComponentPeer instance
 */
void
awt_post_java_key_event(XtPointer client_data, jint id, XEvent *event,
  Time when, jint keycode, jchar keychar, jint modifiers, jint keyLocation, XEvent *anEvent)
{
    JNIEnv *env = (JNIEnv *)JNU_GetEnv(jvm, JNI_VERSION_1_2);
    jobject peer = (jobject) client_data;
    jobject target;
    static jclass classKeyEvent = NULL;
    static jmethodID mid = NULL;
    char *clsName = "java/awt/event/KeyEvent";
    jobject hEvent;
    jlong jWhen;
    Boolean isProxyActive = (focusProxyWindow != None);

    if (anEvent != NULL && anEvent->xany.send_event == 2){
        isProxyActive = False;
        if (event != NULL) {
            event->xany.send_event = 0;
        }
    }
    if ((*env)->PushLocalFrame(env, 16) < 0)
        return;

    target = (*env)->GetObjectField(env, peer, mComponentPeerIDs.target);

    if (classKeyEvent == NULL) {
        jobject sysClass;

        sysClass = (*env)->FindClass(env, clsName);
        if (sysClass != NULL) {
            /* Make this class 'sticky', we don't want it GC'd */
            classKeyEvent = (*env)->NewGlobalRef(env, sysClass);
            mid = (*env)->GetMethodID(env, classKeyEvent, "<init>",
              "(Ljava/awt/Component;IJIICIZ)V");
        }
        if (JNU_IsNull(env, classKeyEvent) || mid == NULL) {
            JNU_ThrowClassNotFoundException(env, clsName);
            (*env)->PopLocalFrame(env, 0);
            return;
        }
    }

    jWhen = awt_util_nowMillisUTC_offset(when); /* convert Time to UTC */

    hEvent = (*env)->NewObject(env, classKeyEvent, mid,
                               target, id, jWhen, modifiers,
                               keycode, keychar, keyLocation,
                               isProxyActive?JNI_TRUE:JNI_FALSE);

    if ((*env)->ExceptionOccurred(env)) {
        (*env)->ExceptionDescribe(env);
        (*env)->ExceptionClear(env);
    }
    if (JNU_IsNull(env, hEvent)) {
        JNU_ThrowNullPointerException(env, "NullPointerException: constructor failed.");
        (*env)->PopLocalFrame(env, 0);
        return;
    }
    awt_copyXEventToAWTEvent(env, event, hEvent);
    #ifdef DEBUG
    if (debugKeys) {
        jio_fprintf(stderr, "native posting event id:%d  keychar:%c\n", (int)id, (char)keychar);
    }
    #endif
    JNU_CallMethodByName(env, NULL, peer,
                         "postEvent", "(Ljava/awt/AWTEvent;)V", hEvent);
    if ((*env)->ExceptionOccurred(env)) {
        (*env)->ExceptionDescribe(env);
        (*env)->ExceptionClear(env);
    }
    (*env)->PopLocalFrame(env, 0);
} /* awt_post_java_key_event() */

/*
 * Note: this routine returns a global reference which should be deleted
 * after use.
 */
jobject
awt_canvas_wrapInSequenced(jobject awtevent) {
    static jclass classSequencedEvent = NULL;
    static jmethodID mid = NULL;
    jobject wrapperEventLocal = NULL;
    jobject wrapperEvent = NULL;

    JNIEnv *env = (JNIEnv *)JNU_GetEnv(jvm, JNI_VERSION_1_2);

    if ((*env)->PushLocalFrame(env, 5) < 0)
        return NULL;

    if (classSequencedEvent == NULL) {
        jobject sysClass = (*env)->FindClass(env, "java/awt/SequencedEvent");
        if (sysClass != NULL) {
            /* Make this class 'sticky', we don't want it GC'd */
            classSequencedEvent = (*env)->NewGlobalRef(env, sysClass);
            if (mid == NULL) {
              mid = (*env)->GetMethodID(env, classSequencedEvent
                                        ,"<init>"
                                        ,"(Ljava/awt/AWTEvent;)V");
            }
        }
        if (JNU_IsNull(env, classSequencedEvent) || mid == NULL) {
            JNU_ThrowClassNotFoundException(env, "java/awt/SequencedEvent");
            (*env)->PopLocalFrame(env, 0);
            return NULL;
        }
    }
    wrapperEventLocal = (*env)->NewObject(env, classSequencedEvent, mid, awtevent);

    if ((*env)->ExceptionOccurred(env)) {
        (*env)->ExceptionDescribe(env);
        (*env)->ExceptionClear(env);
    }
    if (JNU_IsNull(env, wrapperEventLocal)) {
        JNU_ThrowNullPointerException(env, "constructor failed.");
        (*env)->PopLocalFrame(env, 0);
        return NULL;
    }
    wrapperEvent = (*env)->NewGlobalRef(env, wrapperEventLocal);
    if (!JNU_IsNull(env, ((*env)->ExceptionOccurred(env)))) {
        (*env)->ExceptionDescribe(env);
        (*env)->ExceptionClear(env);
        (*env)->PopLocalFrame(env, 0);
        return NULL;
    }
    if (JNU_IsNull(env, wrapperEvent)) {
        JNU_ThrowNullPointerException(env, "NewGlobalRef failed.");
        (*env)->PopLocalFrame(env, 0);
        return NULL;
    }

    (*env)->PopLocalFrame(env, 0);
    return wrapperEvent;
}

jobject
findTopLevelOpposite(JNIEnv *env, jint eventType)
{
    jobject target, peer, opposite;

    if ((*env)->EnsureLocalCapacity(env, 2) < 0) {
        return NULL;
    }

    /* 4462056: Get a usable handle for a weakly referenced object */
    target = (*env)->NewLocalRef(env,
                 (eventType == java_awt_event_WindowEvent_WINDOW_GAINED_FOCUS)
                                 ? forGained
                                 : focusList->requestor);
    if (target == NULL) {
        return NULL;
    }

    peer = (*env)->GetObjectField(env, target, componentIDs.peer);
    (*env)->DeleteLocalRef(env, target);
    if (peer == NULL) {
        return NULL;
    }

    opposite = findTopLevel(peer, env);
    (*env)->DeleteLocalRef(env, peer);

    return opposite;
}

void
cleanFocusList(JNIEnv *env){

  while(focusList) {
    FocusListElt *tmp = focusList->next;
    (*env)->DeleteWeakGlobalRef(env, focusList->requestor);
    free(focusList);
    focusList = tmp;
  }
  focusListEnd = NULL;
}

static jweak
computeOpposite(jint id, jobject target)
{
    JNIEnv *env = (JNIEnv *)JNU_GetEnv(jvm, JNI_VERSION_1_2);
    jobject top;
    jboolean isSameObject;

    if (focusList == NULL) {
        return NULL;
    }

    /* 4462056: Get a usable handle for a weakly referenced object */
    top = (*env)->NewLocalRef(env, focusList->requestor);
    if (top == NULL) {
        /* weakly referenced component was deleted -- clean up focus list */
        cleanFocusList(env);
        return NULL;
    }

    isSameObject = (*env)->IsSameObject(env, target, top);
    (*env)->DeleteLocalRef(env, top);

    if (isSameObject) {
        if (id == java_awt_event_FocusEvent_FOCUS_GAINED) {
            return forGained;
        } else { /* focus lost */
            FocusListElt *tmp = focusList->next;
            (*env)->DeleteWeakGlobalRef(env, forGained);
            forGained = focusList->requestor;
            free(focusList);
            focusList = tmp;

            if (focusList == NULL) {
                focusListEnd = NULL;
                return NULL;
            }
            return focusList->requestor;
        }
    } else { /* target does not match top of list */
        /* be gentle with focus lost for now... */
        if (id == java_awt_event_FocusEvent_FOCUS_LOST) {
            (*env)->DeleteWeakGlobalRef(env, forGained);
            forGained = (*env)->NewWeakGlobalRef(env, target);
            return NULL;
        }

        cleanFocusList(env);
        return NULL;
    }
}


/*
 * client_data is MComponentPeer instance
 */
void
awt_post_java_focus_event(XtPointer client_data,
                          jint id, jobject cause,
                          XEvent* event)
{
    JNIEnv *env = (JNIEnv *)JNU_GetEnv(jvm, JNI_VERSION_1_2);
    jobject peer = (jobject) client_data;
    jobject target;
    jobject opposite;
    static jclass classFocusEvent = NULL;
    static jmethodID mid = NULL;
    char *clsName = "sun/awt/CausedFocusEvent";
    jobject hEvent;

    if ((*env)->PushLocalFrame(env, 16) < 0)
        return;

    target = (*env)->GetObjectField(env, peer, mComponentPeerIDs.target);

    opposite = (*env)->NewLocalRef(env, computeOpposite(id, target));

   if (classFocusEvent == NULL) {
        jobject sysClass;

        sysClass = (*env)->FindClass(env, clsName);
        if (sysClass != NULL) {
            /* Make this class 'sticky', we don't want it GC'd */
            classFocusEvent = (*env)->NewGlobalRef(env, sysClass);
            mid = (*env)->GetMethodID(env, classFocusEvent
                                      ,"<init>"
                                      ,"(Ljava/awt/Component;IZLjava/awt/Component;Lsun/awt/CausedFocusEvent$Cause;)V");
        }
        if (JNU_IsNull(env, classFocusEvent) || mid == 0) {
            JNU_ThrowClassNotFoundException(env, clsName);
            (*env)->PopLocalFrame(env, 0);
            return;
        }
    }
    hEvent = (*env)->NewObject(env, classFocusEvent, mid,
                               target, id, JNI_FALSE, opposite, cause);
    (*env)->DeleteLocalRef(env, opposite);

    if ((*env)->ExceptionOccurred(env)) {
        (*env)->ExceptionDescribe(env);
        (*env)->ExceptionClear(env);
    }
    if (JNU_IsNull(env, hEvent)) {
        JNU_ThrowNullPointerException(env, "NullPointerException: constructor failed.");
        (*env)->PopLocalFrame(env, 0);
        return;
    }
    awt_copyXEventToAWTEvent(env, event, hEvent);
    {
        jobject awtEvent = awt_canvas_wrapInSequenced(hEvent);
        JNU_CallMethodByName(env, NULL, peer,
                             "postEvent", "(Ljava/awt/AWTEvent;)V",
                             awtEvent);
        (*env)->DeleteGlobalRef(env, awtEvent);
    }
    if ((*env)->ExceptionOccurred(env)) {
        (*env)->ExceptionDescribe(env);
        (*env)->ExceptionClear(env);
    }
    (*env)->PopLocalFrame(env, 0);
}


void
awt_canvas_addToFocusListDefault(jobject target) {
    awt_canvas_addToFocusListWithDuplicates(target, JNI_FALSE);
}

void
awt_canvas_addToFocusListWithDuplicates(jobject target, jboolean acceptDuplicates)
{
    JNIEnv *env = (JNIEnv *)JNU_GetEnv(jvm, JNI_VERSION_1_2);
    jboolean isSameObject;

    if (focusListEnd) {
        jobject localRef = (*env)->NewLocalRef(env, focusListEnd->requestor);

        if (localRef == NULL) {
            isSameObject = JNI_FALSE;
        } else {
            isSameObject = (*env)->IsSameObject(env, target, localRef);
            (*env)->DeleteLocalRef(env, localRef);
        }

        if (isSameObject && !acceptDuplicates) {
            return;
        }

        focusListEnd->next = malloc(sizeof(FocusListElt));
        focusListEnd = focusListEnd->next;
    } else {
        jobject l_focusOwnerPeer = awt_canvas_getFocusOwnerPeer();
        if (l_focusOwnerPeer == NULL) {
            isSameObject = JNI_FALSE;
        } else {
            jobject l_focusOwner =
                (*env)->GetObjectField(env, l_focusOwnerPeer,
                                       mComponentPeerIDs.target);
            isSameObject =
                (*env)->IsSameObject(env, target, l_focusOwner);
            (*env)->DeleteLocalRef(env, l_focusOwner);
            (*env)->DeleteLocalRef(env, l_focusOwnerPeer);
        }

        if (isSameObject && !acceptDuplicates) {
            return;
        }

        focusList = focusListEnd = malloc(sizeof(FocusListElt));
    }

    focusListEnd->requestor = (*env)->NewWeakGlobalRef(env, target);
    focusListEnd->next = NULL;
}

/*
 * client_data is MComponentPeer instance
 */
void
awt_post_java_mouse_event(XtPointer client_data, jint id, XEvent* event,
                          Time when, jint modifiers, jint x, jint y,
                          jint xAbs, jint yAbs,
                          jint clickcount,
                          Boolean popuptrigger,
                          jint wheelAmt, jint button)
{
    JNIEnv *env = (JNIEnv *)JNU_GetEnv(jvm, JNI_VERSION_1_2);
    jobject peer = (jobject) client_data;
    jobject target;

    static jclass classMouseEvent = NULL;
    static jclass classMouseWheelEvent = NULL;

    static jmethodID mid = NULL;
    static jmethodID wheelmid = NULL;

    char *clsName = "java/awt/event/MouseEvent";
    char *wheelClsName = "java/awt/event/MouseWheelEvent";

    jobject hEvent;
    jobject sysClass;
    jlong jWhen;

    if ((*env)->PushLocalFrame(env, 16) < 0)
        return;

    target = (*env)->GetObjectField(env, peer, mComponentPeerIDs.target);

    if (classMouseEvent == NULL) {
        sysClass = (*env)->FindClass(env, clsName);
        if (sysClass != NULL) {
            /* Make this class 'sticky', we don't want it GC'd */
            classMouseEvent = (*env)->NewGlobalRef(env, sysClass);
            mid = (*env)->GetMethodID(env, classMouseEvent
                                      ,"<init>"
                                      ,"(Ljava/awt/Component;IJIIIIIIZI)V");
        }
        if (JNU_IsNull(env, classMouseEvent) || mid == 0) {
            JNU_ThrowClassNotFoundException(env, clsName);
            (*env)->PopLocalFrame(env, 0);
            return;
        }
    }

    if (id == java_awt_event_MouseEvent_MOUSE_WHEEL &&
        classMouseWheelEvent == NULL) {
        sysClass = (*env)->FindClass(env, wheelClsName);
        if (sysClass != NULL) {
            /* Make this class 'sticky', we don't want it GC'd */
            classMouseWheelEvent = (*env)->NewGlobalRef(env, sysClass);
            wheelmid = (*env)->GetMethodID(env, classMouseWheelEvent,
                                       "<init>",
                                       "(Ljava/awt/Component;IJIIIIIIZIII)V");
        }
        if (JNU_IsNull(env, classMouseWheelEvent) || wheelmid == 0) {
            JNU_ThrowClassNotFoundException(env, wheelClsName);
            (*env)->PopLocalFrame(env, 0);
            return;
        }
    }

    jWhen = awt_util_nowMillisUTC_offset(when); /* convert Time to UTC */

    if (id == java_awt_event_MouseEvent_MOUSE_WHEEL) {
        hEvent = (*env)->NewObject(env, classMouseWheelEvent, wheelmid,
                              target, id, jWhen, modifiers,
                              x, y,
                              xAbs, yAbs,
                              clickcount, popuptrigger,
                              /* Linux has no API for setting how a Component
                               * should scroll in response to the mouse wheel,
                               * so we have to make up our own.
                               * The default behavior on Windows is 3 lines of
                               * text, so we use that to match.
                               */
                              java_awt_event_MouseWheelEvent_WHEEL_UNIT_SCROLL,
                              3,
                              wheelAmt);
    }
    else {
        hEvent = (*env)->NewObject(env, classMouseEvent, mid,
                                   target, id, jWhen, modifiers,
                                   x, y,
                                   xAbs, yAbs,
                                   clickcount, popuptrigger, button);
    }


    if ((*env)->ExceptionOccurred(env)) {
        (*env)->ExceptionDescribe(env);
        (*env)->ExceptionClear(env);
    }
    if (JNU_IsNull(env, hEvent)) {
        JNU_ThrowNullPointerException(env, "NullPointerException: constructor failed.");
        (*env)->PopLocalFrame(env, 0);
        return;
    }
    awt_copyXEventToAWTEvent(env, event, hEvent);
    JNU_CallMethodByName(env, NULL, peer,
                         "postEvent", "(Ljava/awt/AWTEvent;)V", hEvent);
    if ((*env)->ExceptionOccurred(env)) {
        (*env)->ExceptionDescribe(env);
        (*env)->ExceptionClear(env);
    }
    (*env)->PopLocalFrame(env, 0);
}
