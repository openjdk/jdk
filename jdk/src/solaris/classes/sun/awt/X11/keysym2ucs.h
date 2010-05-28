/*
 * Copyright (c) 2005, 2009, Oracle and/or its affiliates. All rights reserved.
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

/*
 * yan:
 * This table looks like C header because
 * (1) I use actual headers to make it;
 * (2) syntax is nicely highlighted in my editor.
 * Processed will be all lines started with 0x; 0x0000-started lines will
 * be skipped though.
 * Also java code will be copied to a resulting file.
 *
 * 0x0000 unicode means here either there's no equivalent to a keysym
 * or we just skip it from the table for now because i.e. we'll never use
 * the conversion in our workflow.
 *
 */

tojava /*
tojava  * Copyright 2005-2009 Sun Microsystems, Inc.  All Rights Reserved.
tojava  * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
tojava  *
tojava  * This code is free software; you can redistribute it and/or modify it
tojava  * under the terms of the GNU General Public License version 2 only, as
tojava  * published by the Free Software Foundation.  Oracle designates this
tojava  * particular file as subject to the "Classpath" exception as provided
tojava  * by Oracle in the LICENSE file that accompanied this code.
tojava  *
tojava  * This code is distributed in the hope that it will be useful, but WITHOUT
tojava  * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
tojava  * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
tojava  * version 2 for more details (a copy is included in the LICENSE file that
tojava  * accompanied this code).
tojava  *
tojava  * You should have received a copy of the GNU General Public License version
tojava  * 2 along with this work; if not, write to the Free Software Foundation,
tojava  * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
tojava  *
tojava  * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
tojava  * or visit www.oracle.com if you need additional information or have any
tojava  * questions.
tojava  */
tojava
tojava package sun.awt.X11;
tojava import java.util.Hashtable;
tojava import sun.misc.Unsafe;
tojava
tojava import sun.util.logging.PlatformLogger;
tojava
tojava public class XKeysym {
tojava
tojava     public static void main( String args[] ) {
tojava        System.out.println( "Cyrillc zhe:"+convertKeysym(0x06d6, 0));
tojava        System.out.println( "Arabic sheen:"+convertKeysym(0x05d4, 0));
tojava        System.out.println( "Latin a breve:"+convertKeysym(0x01e3, 0));
tojava        System.out.println( "Latin f:"+convertKeysym(0x066, 0));
tojava        System.out.println( "Backspace:"+Integer.toHexString(convertKeysym(0xff08, 0)));
tojava        System.out.println( "Ctrl+f:"+Integer.toHexString(convertKeysym(0x066, XConstants.ControlMask)));
tojava     }
tojava
tojava     private XKeysym() {}
tojava
tojava     static class Keysym2JavaKeycode  {
tojava         int jkeycode;
tojava         int keyLocation;
tojava         int getJavaKeycode() {
tojava             return jkeycode;
tojava         }
tojava         int getKeyLocation() {
tojava             return keyLocation;
tojava         }
tojava         Keysym2JavaKeycode(int jk, int loc) {
tojava             jkeycode = jk;
tojava             keyLocation = loc;
tojava         }
tojava     };
tojava     private static Unsafe unsafe = XlibWrapper.unsafe;
tojava     static Hashtable<Long, Keysym2JavaKeycode>  keysym2JavaKeycodeHash = new Hashtable<Long, Keysym2JavaKeycode>();
tojava     static Hashtable<Long, Character> keysym2UCSHash = new Hashtable<Long, Character>();
tojava     static Hashtable<Long, Long> uppercaseHash = new Hashtable<Long, Long>();
tojava     // TODO: or not to do: add reverse lookup javakeycode2keysym,
tojava     // for robot only it seems to me. After that, we can remove lookup table
tojava     // from XWindow.c altogether.
tojava     // Another use for reverse lookup: query keyboard state, for some keys.
tojava     static Hashtable<Integer, Long> javaKeycode2KeysymHash = new Hashtable<Integer, Long>();
tojava     static long keysym_lowercase = unsafe.allocateMemory(Native.getLongSize());
tojava     static long keysym_uppercase = unsafe.allocateMemory(Native.getLongSize());
tojava     static Keysym2JavaKeycode kanaLock = new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_KANA_LOCK,
tojava                                                                 java.awt.event.KeyEvent.KEY_LOCATION_STANDARD);
tojava     private static PlatformLogger keyEventLog = PlatformLogger.getLogger("sun.awt.X11.kye.XKeysym");
tojava     public static char convertKeysym( long ks, int state ) {
tojava
tojava         /* First check for Latin-1 characters (1:1 mapping) */
tojava         if ((ks >= 0x0020 && ks <= 0x007e) ||
tojava             (ks >= 0x00a0 && ks <= 0x00ff)) {
tojava             if( (state & XConstants.ControlMask) != 0 ) {
tojava                 if ((ks >= 'A' && ks <= ']') || (ks == '_') ||
tojava                     (ks >= 'a' && ks <='z')) {
tojava                     ks &= 0x1F;
tojava                 }
tojava             }
tojava             return (char)ks;
tojava         }
tojava
tojava         /* XXX: Also check for directly encoded 24-bit UCS characters:
tojava          */
tojava         if ((ks & 0xff000000) == 0x01000000)
tojava           return (char)(ks & 0x00ffffff);
tojava
tojava         Character ch = keysym2UCSHash.get(ks);
tojava         return ch == null ? (char)0 : ch.charValue();
tojava     }
tojava     static long xkeycode2keysym_noxkb(XKeyEvent ev, int ndx) {
tojava         XToolkit.awtLock();
tojava         try {
tojava             return XlibWrapper.XKeycodeToKeysym(ev.get_display(), ev.get_keycode(), ndx);
tojava         } finally {
tojava             XToolkit.awtUnlock();
tojava         }
tojava     }
tojava     static long xkeycode2keysym_xkb(XKeyEvent ev, int ndx) {
tojava         XToolkit.awtLock();
tojava         try {
tojava             int mods = ev.get_state();
tojava             if ((ndx == 0) && ((mods & XConstants.ShiftMask) != 0)) {
tojava                 // I don't know all possible meanings of 'ndx' in case of XKB
tojava                 // and don't want to speculate. But this particular case
tojava                 // clearly means that caller needs a so called primary keysym.
tojava                 mods ^= XConstants.ShiftMask;
tojava             }
tojava             long kbdDesc = XToolkit.getXKBKbdDesc();
tojava             if( kbdDesc != 0 ) {
tojava                 XlibWrapper.XkbTranslateKeyCode(kbdDesc, ev.get_keycode(),
tojava                        mods, XlibWrapper.iarg1, XlibWrapper.larg3);
tojava             }else{
tojava                 // xkb resources already gone
tojava                 keyEventLog.fine("Thread race: Toolkit shutdown before the end of a key event processing.");
tojava                 return 0;
tojava             }
tojava             //XXX unconsumed modifiers?
tojava             return Native.getLong(XlibWrapper.larg3);
tojava         } finally {
tojava             XToolkit.awtUnlock();
tojava         }
tojava     }
tojava     static long xkeycode2keysym(XKeyEvent ev, int ndx) {
tojava         XToolkit.awtLock();
tojava         try {
tojava             if (XToolkit.canUseXKBCalls()) {
tojava                 return xkeycode2keysym_xkb(ev, ndx);
tojava             }else{
tojava                 return xkeycode2keysym_noxkb(ev, ndx);
tojava             }
tojava         } finally {
tojava             XToolkit.awtUnlock();
tojava         }
tojava     }
tojava     static long xkeycode2primary_keysym(XKeyEvent ev) {
tojava         return xkeycode2keysym(ev, 0);
tojava     }
tojava     public static boolean isKPEvent( XKeyEvent ev )
tojava     {
tojava         // Xsun without XKB uses keysymarray[2] keysym to determine if it is KP event.
tojava         // Otherwise, it is [1].
tojava         int ndx = XToolkit.isXsunKPBehavior() &&
tojava                   ! XToolkit.isXKBenabled() ? 2 : 1;
tojava         // Even if XKB is enabled, we have another problem: some symbol tables (e.g. cz) force
tojava         // a regular comma instead of KP_comma for a decimal separator. Result is,
tojava         // bugs like 6454041. So, we will try for keypadness  a keysym with ndx==0 as well.
tojava         XToolkit.awtLock();
tojava         try {
tojava             return (XlibWrapper.IsKeypadKey(
tojava                 XlibWrapper.XKeycodeToKeysym(ev.get_display(), ev.get_keycode(), ndx ) ) ||
tojava                    XlibWrapper.IsKeypadKey(
tojava                 XlibWrapper.XKeycodeToKeysym(ev.get_display(), ev.get_keycode(), 0 ) ));
tojava         } finally {
tojava             XToolkit.awtUnlock();
tojava         }
tojava     }
tojava     /**
tojava         Return uppercase keysym correspondent to a given keysym.
tojava         If input keysym does not belong to any lower/uppercase pair, return -1.
tojava     */
tojava     public static long getUppercaseAlphabetic( long keysym ) {
tojava         long lc = -1;
tojava         long uc = -1;
tojava         Long stored =  uppercaseHash.get(keysym);
tojava         if (stored != null ) {
tojava             return stored.longValue();
tojava         }
tojava         XToolkit.awtLock();
tojava         try {
tojava             XlibWrapper.XConvertCase(keysym, keysym_lowercase, keysym_uppercase);
tojava             lc = Native.getLong(keysym_lowercase);
tojava             uc = Native.getLong(keysym_uppercase);
tojava             if (lc == uc) {
tojava                 //not applicable
tojava                 uc = -1;
tojava             }
tojava             uppercaseHash.put(keysym, uc);
tojava         } finally {
tojava             XToolkit.awtUnlock();
tojava         }
tojava         return uc;
tojava     }
tojava     /**
tojava         Get a keypad keysym derived from a keycode.
tojava         I do not check if this is a keypad event, I just presume it.
tojava     */
tojava     private static long getKeypadKeysym( XKeyEvent ev ) {
tojava         int ndx = 0;
tojava         long keysym = XConstants.NoSymbol;
tojava         if( XToolkit.isXsunKPBehavior() &&
tojava             ! XToolkit.isXKBenabled() ) {
tojava             if( (ev.get_state() & XConstants.ShiftMask) != 0 ) { // shift modifier is on
tojava                 ndx = 3;
tojava                 keysym = xkeycode2keysym(ev, ndx);
tojava             } else {
tojava                 ndx = 2;
tojava                 keysym = xkeycode2keysym(ev, ndx);
tojava             }
tojava         } else {
tojava             if( (ev.get_state() & XConstants.ShiftMask) != 0 || // shift modifier is on
tojava                 ((ev.get_state() & XConstants.LockMask) != 0 && // lock modifier is on
tojava                  (XToolkit.modLockIsShiftLock != 0)) ) {     // it is interpreted as ShiftLock
tojava                 ndx = 0;
tojava                 keysym = xkeycode2keysym(ev, ndx);
tojava             } else {
tojava                 ndx = 1;
tojava                 keysym = xkeycode2keysym(ev, ndx);
tojava             }
tojava         }
tojava         return keysym;
tojava     }
tojava
tojava     /**
tojava         Return java.awt.KeyEvent constant meaning (Java) keycode, derived from X keysym.
tojava         Some keysyms maps to more than one keycode, these would require extra processing.
tojava     */
tojava     static Keysym2JavaKeycode getJavaKeycode( long keysym ) {
tojava         if(keysym == XKeySymConstants.XK_Mode_switch){
tojava            /* XK_Mode_switch on solaris maps either to VK_ALT_GRAPH (default) or VK_KANA_LOCK */
tojava            if( XToolkit.isKanaKeyboard() ) {
tojava                return kanaLock;
tojava            }
tojava         }else if(keysym == XKeySymConstants.XK_L1){
tojava            /* if it is Sun keyboard, trick hash to return VK_STOP else VK_F11 (default) */
tojava            if( XToolkit.isSunKeyboard() ) {
tojava                keysym = XKeySymConstants.SunXK_Stop;
tojava            }
tojava         }else if(keysym == XKeySymConstants.XK_L2) {
tojava            /* if it is Sun keyboard, trick hash to return VK_AGAIN else VK_F12 (default) */
tojava            if( XToolkit.isSunKeyboard() ) {
tojava                keysym = XKeySymConstants.SunXK_Again;
tojava            }
tojava         }
tojava
tojava         return  keysym2JavaKeycodeHash.get( keysym );
tojava     }
tojava     /**
tojava         Return java.awt.KeyEvent constant meaning (Java) keycode, derived from X Window KeyEvent.
tojava         Algorithm is, extract via XKeycodeToKeysym  a proper keysym according to Xlib spec rules and
tojava         err exceptions, then search a java keycode in a table.
tojava     */
tojava     static Keysym2JavaKeycode getJavaKeycode( XKeyEvent ev ) {
tojava         // get from keysym2JavaKeycodeHash.
tojava         long keysym = XConstants.NoSymbol;
tojava         int ndx = 0;
tojava         if( (ev.get_state() & XToolkit.numLockMask) != 0 &&
tojava              isKPEvent(ev)) {
tojava             keysym = getKeypadKeysym( ev );
tojava         } else {
tojava             // we only need primary-layer keysym to derive a java keycode.
tojava             ndx = 0;
tojava             keysym = xkeycode2keysym(ev, ndx);
tojava         }
tojava
tojava         Keysym2JavaKeycode jkc = getJavaKeycode( keysym );
tojava         return jkc;
tojava     }
tojava     static int getJavaKeycodeOnly( XKeyEvent ev ) {
tojava         Keysym2JavaKeycode jkc = getJavaKeycode( ev );
tojava         return jkc == null ? java.awt.event.KeyEvent.VK_UNDEFINED : jkc.getJavaKeycode();
tojava     }
tojava     /**
tojava      * Return an integer java keycode apprx as it was before extending keycodes range.
tojava      * This call would ignore for instance XKB and process whatever is on the bottom
tojava      * of keysym stack. Result will not depend on actual locale, will differ between
tojava      * dual/multiple keyboard setup systems (e.g. English+Russian vs French+Russian)
tojava      * but will be someway compatible with old releases.
tojava      */
tojava     static int getLegacyJavaKeycodeOnly( XKeyEvent ev ) {
tojava         long keysym = XConstants.NoSymbol;
tojava         int ndx = 0;
tojava         if( (ev.get_state() & XToolkit.numLockMask) != 0 &&
tojava              isKPEvent(ev)) {
tojava             keysym = getKeypadKeysym( ev );
tojava         } else {
tojava             // we only need primary-layer keysym to derive a java keycode.
tojava             ndx = 0;
tojava             keysym = xkeycode2keysym_noxkb(ev, ndx);
tojava         }
tojava         Keysym2JavaKeycode jkc = getJavaKeycode( keysym );
tojava         return jkc == null ? java.awt.event.KeyEvent.VK_UNDEFINED : jkc.getJavaKeycode();
tojava     }
tojava     static long javaKeycode2Keysym( int jkey ) {
tojava         Long ks = javaKeycode2KeysymHash.get( jkey );
tojava         return  (ks == null ? 0 : ks.longValue());
tojava     }
tojava     /**
tojava         Return keysym derived from a keycode and modifiers.
tojava         Usually an input method does this. However non-system input methods (e.g. Java IMs) do not.
tojava         For rules, see "Xlib - C Language X Interface",
tojava                         MIT X Consortium Standard
tojava                         X Version 11, Release 6
tojava                         Ch. 12.7
tojava         XXX TODO: or maybe not to do: process Mode Lock and therefore
tojava         not only 0-th and 1-st but 2-nd and 3-rd keysyms for a keystroke.
tojava     */
tojava     static long getKeysym( XKeyEvent ev ) {
tojava         long keysym = XConstants.NoSymbol;
tojava         long uppercaseKeysym = XConstants.NoSymbol;
tojava         int  ndx = 0;
tojava         boolean getUppercase = false;
tojava         if ((ev.get_state() & XToolkit.numLockMask) != 0 &&
tojava              isKPEvent(ev)) {
tojava             keysym = getKeypadKeysym( ev );
tojava         } else {
tojava             // XXX: at this point, anything in keysym[23] is ignored.
tojava             //
tojava             // Shift & Lock are off ===> ndx = 0;
tojava             // Shift off & Lock on & Lock is CapsLock ===> ndx = 0;
tojava             //       if keysym[ndx] is lowecase alphabetic, then corresp. uppercase used.
tojava             // Shift on & Lock on & Lock is CapsLock ===> ndx == 1;
tojava             //       if keysym[ndx] is lowecase alphabetic, then corresp. uppercase used.
tojava             // Shift on || (Lock on & Lock is ShiftLock) ===> ndx = 1.
tojava             if ((ev.get_state() & XConstants.ShiftMask) == 0) {     // shift is off
tojava                 if ((ev.get_state() & XConstants.LockMask) == 0 ) {  // lock is off
tojava                     ndx = 0;
tojava                     getUppercase = false;
tojava                 } else if ((ev.get_state() & XConstants.LockMask) != 0 && // lock is on
tojava                      (XToolkit.modLockIsShiftLock == 0)) { // lock is capslock
tojava                     ndx = 0;
tojava                     getUppercase = true;
tojava                 } else if ((ev.get_state() & XConstants.LockMask) != 0 && // lock is on
tojava                      (XToolkit.modLockIsShiftLock != 0)) { // lock is shift lock
tojava                     ndx = 1;
tojava                     getUppercase = false;
tojava                 }
tojava             } else { // shift on
tojava                 if ((ev.get_state() & XConstants.LockMask) != 0 && // lock is on
tojava                      (XToolkit.modLockIsShiftLock == 0)) { // lock is capslock
tojava                     ndx = 1;
tojava                     getUppercase = true;
tojava                 } else {
tojava                     ndx = 1;
tojava                     getUppercase = false;
tojava                 }
tojava             }
tojava             keysym = xkeycode2keysym(ev, ndx);
tojava             if (getUppercase && (uppercaseKeysym =  getUppercaseAlphabetic( keysym )) != -1) {
tojava                 keysym = uppercaseKeysym;
tojava             }
tojava         }
tojava         return keysym;
tojava     }
tojava
tojava     static {

/***********************************************************
Copyright 1987, 1994, 1998  The Open Group

Permission to use, copy, modify, distribute, and sell this software and its
documentation for any purpose is hereby granted without fee, provided that
the above copyright notice appear in all copies and that both that
copyright notice and this permission notice appear in supporting
documentation.

The above copyright notice and this permission notice shall be included
in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS
OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
IN NO EVENT SHALL THE OPEN GROUP BE LIABLE FOR ANY CLAIM, DAMAGES OR
OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
OTHER DEALINGS IN THE SOFTWARE.

Except as contained in this notice, the name of The Open Group shall
not be used in advertising or otherwise to promote the sale, use or
other dealings in this Software without prior written authorization
from The Open Group.


Copyright 1987 by Digital Equipment Corporation, Maynard, Massachusetts

                        All Rights Reserved

Permission to use, copy, modify, and distribute this software and its
documentation for any purpose and without fee is hereby granted,
provided that the above copyright notice appear in all copies and that
both that copyright notice and this permission notice appear in
supporting documentation, and that the name of Digital not be
used in advertising or publicity pertaining to distribution of the
software without specific, written prior permission.

DIGITAL DISCLAIMS ALL WARRANTIES WITH REGARD TO THIS SOFTWARE, INCLUDING
ALL IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS, IN NO EVENT SHALL
DIGITAL BE LIABLE FOR ANY SPECIAL, INDIRECT OR CONSEQUENTIAL DAMAGES OR
ANY DAMAGES WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS,
WHETHER IN AN ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION,
ARISING OUT OF OR IN CONNECTION WITH THE USE OR PERFORMANCE OF THIS
SOFTWARE.

******************************************************************/

/*
 * TTY Functions, cleverly chosen to map to ascii, for convenience of
 * programming, but could have been arbitrary (at the cost of lookup
 * tables in client code.
 */

0x0008 #define XK_BackSpace        0xFF08    /* back space, back char */
0x0009 #define XK_Tab            0xFF09
0x000a #define XK_Linefeed        0xFF0A    /* Linefeed, LF */
0x000b #define XK_Clear        0xFF0B
/*XXX map to 0a instead of 0d - why? for some good reason I hope */
0x000a #define XK_Return        0xFF0D    /* Return, enter */
0x0000 #define XK_Pause        0xFF13    /* Pause, hold */
0x0000 #define XK_Scroll_Lock        0xFF14
0x0000 #define XK_Sys_Req        0xFF15
0x001B #define XK_Escape        0xFF1B
0x007F #define XK_Delete        0xFFFF    /* Delete, rubout */



/* International & multi-key character composition */

0x0000 #define XK_Multi_key        0xFF20  /* Multi-key character compose */
0x0000 #define XK_Codeinput        0xFF37
0x0000 #define XK_SingleCandidate    0xFF3C
0x0000 #define XK_MultipleCandidate    0xFF3D
0x0000 #define XK_PreviousCandidate    0xFF3E

/* Japanese keyboard support */

0x0000 #define XK_Kanji        0xFF21    /* Kanji, Kanji convert */
0x0000 #define XK_Muhenkan        0xFF22  /* Cancel Conversion */
0x0000 #define XK_Henkan_Mode        0xFF23  /* Start/Stop Conversion */
0x0000 #define XK_Henkan        0xFF23  /* Alias for Henkan_Mode */
0x0000 #define XK_Romaji        0xFF24  /* to Romaji */
0x0000 #define XK_Hiragana        0xFF25  /* to Hiragana */
0x0000 #define XK_Katakana        0xFF26  /* to Katakana */
0x0000 #define XK_Hiragana_Katakana    0xFF27  /* Hiragana/Katakana toggle */
0x0000 #define XK_Zenkaku        0xFF28  /* to Zenkaku */
0x0000 #define XK_Hankaku        0xFF29  /* to Hankaku */
0x0000 #define XK_Zenkaku_Hankaku    0xFF2A  /* Zenkaku/Hankaku toggle */
0x0000 #define XK_Touroku        0xFF2B  /* Add to Dictionary */
0x0000 #define XK_Massyo        0xFF2C  /* Delete from Dictionary */
0x0000 #define XK_Kana_Lock        0xFF2D  /* Kana Lock */
0x0000 #define XK_Kana_Shift        0xFF2E  /* Kana Shift */
0x0000 #define XK_Eisu_Shift        0xFF2F  /* Alphanumeric Shift */
0x0000 #define XK_Eisu_toggle        0xFF30  /* Alphanumeric toggle */
0x0000 #define XK_Kanji_Bangou        0xFF37  /* Codeinput */
0x0000 #define XK_Zen_Koho        0xFF3D    /* Multiple/All Candidate(s) */
0x0000 #define XK_Mae_Koho        0xFF3E    /* Previous Candidate */

/* Cursor control & motion */

0x0000 #define XK_Home            0xFF50
0x0000 #define XK_Left            0xFF51    /* Move left, left arrow */
0x0000 #define XK_Up            0xFF52    /* Move up, up arrow */
0x0000 #define XK_Right        0xFF53    /* Move right, right arrow */
0x0000 #define XK_Down            0xFF54    /* Move down, down arrow */
0x0000 #define XK_Prior        0xFF55    /* Prior, previous */
0x0000 #define XK_Page_Up        0xFF55
0x0000 #define XK_Next            0xFF56    /* Next */
0x0000 #define XK_Page_Down        0xFF56
0x0000 #define XK_End            0xFF57    /* EOL */
0x0000 #define XK_Begin        0xFF58    /* BOL */


/* Misc Functions */

0x0000 #define XK_Select        0xFF60    /* Select, mark */
0x0000 #define XK_Print        0xFF61
0x0000 #define XK_Execute        0xFF62    /* Execute, run, do */
0x0000 #define XK_Insert        0xFF63    /* Insert, insert here */
0x0000 #define XK_Undo            0xFF65    /* Undo, oops */
0x0000 #define XK_Redo            0xFF66    /* redo, again */
0x0000 #define XK_Menu            0xFF67
0x0000 #define XK_Find            0xFF68    /* Find, search */
0x0000 #define XK_Cancel        0xFF69    /* Cancel, stop, abort, exit */
0x0000 #define XK_Help            0xFF6A    /* Help */
0x0000 #define XK_Break        0xFF6B
0x0000 #define XK_Mode_switch        0xFF7E    /* Character set switch */
0x0000 #define XK_script_switch        0xFF7E  /* Alias for mode_switch */
0x0000 #define XK_Num_Lock        0xFF7F

/* Keypad Functions, keypad numbers cleverly chosen to map to ascii */

0x0020 #define XK_KP_Space        0xFF80    /* space */
0x0009 #define XK_KP_Tab        0xFF89
0x000A #define XK_KP_Enter        0xFF8D    /* enter: note that it is again 0A */
0x0000 #define XK_KP_F1        0xFF91    /* PF1, KP_A, ... */
0x0000 #define XK_KP_F2        0xFF92
0x0000 #define XK_KP_F3        0xFF93
0x0000 #define XK_KP_F4        0xFF94
0x0000 #define XK_KP_Home        0xFF95
0x0000 #define XK_KP_Left        0xFF96
0x0000 #define XK_KP_Up        0xFF97
0x0000 #define XK_KP_Right        0xFF98
0x0000 #define XK_KP_Down        0xFF99
0x0000 #define XK_KP_Prior        0xFF9A
0x0000 #define XK_KP_Page_Up        0xFF9A
0x0000 #define XK_KP_Next        0xFF9B
0x0000 #define XK_KP_Page_Down        0xFF9B
0x0000 #define XK_KP_End        0xFF9C
0x0000 #define XK_KP_Begin        0xFF9D
0x0000 #define XK_KP_Insert        0xFF9E
0x007F #define XK_KP_Delete        0xFF9F
0x003d #define XK_KP_Equal        0xFFBD    /* equals */
0x002a #define XK_KP_Multiply        0xFFAA
0x002b #define XK_KP_Add        0xFFAB
0x002c #define XK_KP_Separator        0xFFAC    /* separator, often comma */
0x002d #define XK_KP_Subtract        0xFFAD
0x002e #define XK_KP_Decimal        0xFFAE
0x002f #define XK_KP_Divide        0xFFAF

0x0030 #define XK_KP_0            0xFFB0
0x0031 #define XK_KP_1            0xFFB1
0x0032 #define XK_KP_2            0xFFB2
0x0033 #define XK_KP_3            0xFFB3
0x0034 #define XK_KP_4            0xFFB4
0x0035 #define XK_KP_5            0xFFB5
0x0036 #define XK_KP_6            0xFFB6
0x0037 #define XK_KP_7            0xFFB7
0x0038 #define XK_KP_8            0xFFB8
0x0039 #define XK_KP_9            0xFFB9



/*
 * Auxilliary Functions; note the duplicate definitions for left and right
 * function keys;  Sun keyboards and a few other manufactures have such
 * function key groups on the left and/or right sides of the keyboard.
 * We've not found a keyboard with more than 35 function keys total.
 */

0x0000 #define XK_F1            0xFFBE
0x0000 #define XK_F2            0xFFBF
0x0000 #define XK_F3            0xFFC0
0x0000 #define XK_F4            0xFFC1
0x0000 #define XK_F5            0xFFC2
0x0000 #define XK_F6            0xFFC3
0x0000 #define XK_F7            0xFFC4
0x0000 #define XK_F8            0xFFC5
0x0000 #define XK_F9            0xFFC6
0x0000 #define XK_F10            0xFFC7
0x0000 #define XK_F11            0xFFC8
0x0000 #define XK_L1            0xFFC8
0x0000 #define XK_F12            0xFFC9
0x0000 #define XK_L2            0xFFC9
0x0000 #define XK_F13            0xFFCA
0x0000 #define XK_L3            0xFFCA
0x0000 #define XK_F14            0xFFCB
0x0000 #define XK_L4            0xFFCB
0x0000 #define XK_F15            0xFFCC
0x0000 #define XK_L5            0xFFCC
0x0000 #define XK_F16            0xFFCD
0x0000 #define XK_L6            0xFFCD
0x0000 #define XK_F17            0xFFCE
0x0000 #define XK_L7            0xFFCE
0x0000 #define XK_F18            0xFFCF
0x0000 #define XK_L8            0xFFCF
0x0000 #define XK_F19            0xFFD0
0x0000 #define XK_L9            0xFFD0
0x0000 #define XK_F20            0xFFD1
0x0000 #define XK_L10            0xFFD1
0x0000 #define XK_F21            0xFFD2
0x0000 #define XK_R1            0xFFD2
0x0000 #define XK_F22            0xFFD3
0x0000 #define XK_R2            0xFFD3
0x0000 #define XK_F23            0xFFD4
0x0000 #define XK_R3            0xFFD4
0x0000 #define XK_F24            0xFFD5
0x0000 #define XK_R4            0xFFD5
0x0000 #define XK_F25            0xFFD6
0x0000 #define XK_R5            0xFFD6
0x0000 #define XK_F26            0xFFD7
0x0000 #define XK_R6            0xFFD7
0x0000 #define XK_F27            0xFFD8
0x0000 #define XK_R7            0xFFD8
0x0000 #define XK_F28            0xFFD9
0x0000 #define XK_R8            0xFFD9
0x0000 #define XK_F29            0xFFDA
0x0000 #define XK_R9            0xFFDA
0x0000 #define XK_F30            0xFFDB
0x0000 #define XK_R10            0xFFDB
0x0000 #define XK_F31            0xFFDC
0x0000 #define XK_R11            0xFFDC
0x0000 #define XK_F32            0xFFDD
0x0000 #define XK_R12            0xFFDD
0x0000 #define XK_F33            0xFFDE
0x0000 #define XK_R13            0xFFDE
0x0000 #define XK_F34            0xFFDF
0x0000 #define XK_R14            0xFFDF
0x0000 #define XK_F35            0xFFE0
0x0000 #define XK_R15            0xFFE0

/* Modifiers */

0x0000 #define XK_Shift_L        0xFFE1    /* Left shift */
0x0000 #define XK_Shift_R        0xFFE2    /* Right shift */
0x0000 #define XK_Control_L        0xFFE3    /* Left control */
0x0000 #define XK_Control_R        0xFFE4    /* Right control */
0x0000 #define XK_Caps_Lock        0xFFE5    /* Caps lock */
0x0000 #define XK_Shift_Lock        0xFFE6    /* Shift lock */

0x0000 #define XK_Meta_L        0xFFE7    /* Left meta */
0x0000 #define XK_Meta_R        0xFFE8    /* Right meta */
0x0000 #define XK_Alt_L        0xFFE9    /* Left alt */
0x0000 #define XK_Alt_R        0xFFEA    /* Right alt */
0x0000 #define XK_Super_L        0xFFEB    /* Left super */
0x0000 #define XK_Super_R        0xFFEC    /* Right super */
0x0000 #define XK_Hyper_L        0xFFED    /* Left hyper */
0x0000 #define XK_Hyper_R        0xFFEE    /* Right hyper */
#endif /* XK_MISCELLANY */

/*
 * ISO 9995 Function and Modifier Keys
 * Byte 3 = 0xFE
 */

#ifdef XK_XKB_KEYS
0x0000 #define    XK_ISO_Lock                    0xFE01
0x0000 #define    XK_ISO_Level2_Latch                0xFE02
0x0000 #define    XK_ISO_Level3_Shift                0xFE03
0x0000 #define    XK_ISO_Level3_Latch                0xFE04
0x0000 #define    XK_ISO_Level3_Lock                0xFE05
0x0000 #define    XK_ISO_Group_Shift        0xFF7E    /* Alias for mode_switch */
0x0000 #define    XK_ISO_Group_Latch                0xFE06
0x0000 #define    XK_ISO_Group_Lock                0xFE07
0x0000 #define    XK_ISO_Next_Group                0xFE08
0x0000 #define    XK_ISO_Next_Group_Lock                0xFE09
0x0000 #define    XK_ISO_Prev_Group                0xFE0A
0x0000 #define    XK_ISO_Prev_Group_Lock                0xFE0B
0x0000 #define    XK_ISO_First_Group                0xFE0C
0x0000 #define    XK_ISO_First_Group_Lock                0xFE0D
0x0000 #define    XK_ISO_Last_Group                0xFE0E
0x0000 #define    XK_ISO_Last_Group_Lock                0xFE0F

0x0009 #define    XK_ISO_Left_Tab                    0xFE20
0x0000 #define    XK_ISO_Move_Line_Up                0xFE21
0x0000 #define    XK_ISO_Move_Line_Down                0xFE22
0x0000 #define    XK_ISO_Partial_Line_Up                0xFE23
0x0000 #define    XK_ISO_Partial_Line_Down            0xFE24
0x0000 #define    XK_ISO_Partial_Space_Left            0xFE25
0x0000 #define    XK_ISO_Partial_Space_Right            0xFE26
0x0000 #define    XK_ISO_Set_Margin_Left                0xFE27
0x0000 #define    XK_ISO_Set_Margin_Right                0xFE28
0x0000 #define    XK_ISO_Release_Margin_Left            0xFE29
0x0000 #define    XK_ISO_Release_Margin_Right            0xFE2A
0x0000 #define    XK_ISO_Release_Both_Margins            0xFE2B
0x0000 #define    XK_ISO_Fast_Cursor_Left                0xFE2C
0x0000 #define    XK_ISO_Fast_Cursor_Right            0xFE2D
0x0000 #define    XK_ISO_Fast_Cursor_Up                0xFE2E
0x0000 #define    XK_ISO_Fast_Cursor_Down                0xFE2F
0x0000 #define    XK_ISO_Continuous_Underline            0xFE30
0x0000 #define    XK_ISO_Discontinuous_Underline            0xFE31
0x0000 #define    XK_ISO_Emphasize                0xFE32
0x0000 #define    XK_ISO_Center_Object                0xFE33
0x0000 #define    XK_ISO_Enter                    0xFE34

0x0000 #define    XK_dead_grave                    0xFE50
0x0000 #define    XK_dead_acute                    0xFE51
0x0000 #define    XK_dead_circumflex                0xFE52
0x0000 #define    XK_dead_tilde                    0xFE53
0x0000 #define    XK_dead_macron                    0xFE54
0x0000 #define    XK_dead_breve                    0xFE55
0x0000 #define    XK_dead_abovedot                0xFE56
0x0000 #define    XK_dead_diaeresis                0xFE57
0x0000 #define    XK_dead_abovering                0xFE58
0x0000 #define    XK_dead_doubleacute                0xFE59
0x0000 #define    XK_dead_caron                    0xFE5A
0x0000 #define    XK_dead_cedilla                    0xFE5B
0x0000 #define    XK_dead_ogonek                    0xFE5C
0x0000 #define    XK_dead_iota                    0xFE5D
0x0000 #define    XK_dead_voiced_sound                0xFE5E
0x0000 #define    XK_dead_semivoiced_sound            0xFE5F
0x0000 #define    XK_dead_belowdot                0xFE60
0x0000 #define XK_dead_hook                    0xFE61
0x0000 #define XK_dead_horn                    0xFE62

0x0000 #define    XK_First_Virtual_Screen                0xFED0
0x0000 #define    XK_Prev_Virtual_Screen                0xFED1
0x0000 #define    XK_Next_Virtual_Screen                0xFED2
0x0000 #define    XK_Last_Virtual_Screen                0xFED4
0x0000 #define    XK_Terminate_Server                0xFED5

0x0000 #define    XK_AccessX_Enable                0xFE70
0x0000 #define    XK_AccessX_Feedback_Enable            0xFE71
0x0000 #define    XK_RepeatKeys_Enable                0xFE72
0x0000 #define    XK_SlowKeys_Enable                0xFE73
0x0000 #define    XK_BounceKeys_Enable                0xFE74
0x0000 #define    XK_StickyKeys_Enable                0xFE75
0x0000 #define    XK_MouseKeys_Enable                0xFE76
0x0000 #define    XK_MouseKeys_Accel_Enable            0xFE77
0x0000 #define    XK_Overlay1_Enable                0xFE78
0x0000 #define    XK_Overlay2_Enable                0xFE79
0x0000 #define    XK_AudibleBell_Enable                0xFE7A

0x0000 #define    XK_Pointer_Left                    0xFEE0
0x0000 #define    XK_Pointer_Right                0xFEE1
0x0000 #define    XK_Pointer_Up                    0xFEE2
0x0000 #define    XK_Pointer_Down                    0xFEE3
0x0000 #define    XK_Pointer_UpLeft                0xFEE4
0x0000 #define    XK_Pointer_UpRight                0xFEE5
0x0000 #define    XK_Pointer_DownLeft                0xFEE6
0x0000 #define    XK_Pointer_DownRight                0xFEE7
0x0000 #define    XK_Pointer_Button_Dflt                0xFEE8
0x0000 #define    XK_Pointer_Button1                0xFEE9
0x0000 #define    XK_Pointer_Button2                0xFEEA
0x0000 #define    XK_Pointer_Button3                0xFEEB
0x0000 #define    XK_Pointer_Button4                0xFEEC
0x0000 #define    XK_Pointer_Button5                0xFEED
0x0000 #define    XK_Pointer_DblClick_Dflt            0xFEEE
0x0000 #define    XK_Pointer_DblClick1                0xFEEF
0x0000 #define    XK_Pointer_DblClick2                0xFEF0
0x0000 #define    XK_Pointer_DblClick3                0xFEF1
0x0000 #define    XK_Pointer_DblClick4                0xFEF2
0x0000 #define    XK_Pointer_DblClick5                0xFEF3
0x0000 #define    XK_Pointer_Drag_Dflt                0xFEF4
0x0000 #define    XK_Pointer_Drag1                0xFEF5
0x0000 #define    XK_Pointer_Drag2                0xFEF6
0x0000 #define    XK_Pointer_Drag3                0xFEF7
0x0000 #define    XK_Pointer_Drag4                0xFEF8
0x0000 #define    XK_Pointer_Drag5                0xFEFD

0x0000 #define    XK_Pointer_EnableKeys                0xFEF9
0x0000 #define    XK_Pointer_Accelerate                0xFEFA
0x0000 #define    XK_Pointer_DfltBtnNext                0xFEFB
0x0000 #define    XK_Pointer_DfltBtnPrev                0xFEFC

#endif

/*
 * 3270 Terminal Keys
 * Byte 3 = 0xFD
 */

#ifdef XK_3270
0x0000 #define XK_3270_Duplicate      0xFD01
0x0000 #define XK_3270_FieldMark      0xFD02
0x0000 #define XK_3270_Right2         0xFD03
0x0000 #define XK_3270_Left2          0xFD04
0x0000 #define XK_3270_BackTab        0xFD05
0x0000 #define XK_3270_EraseEOF       0xFD06
0x0000 #define XK_3270_EraseInput     0xFD07
0x0000 #define XK_3270_Reset          0xFD08
0x0000 #define XK_3270_Quit           0xFD09
0x0000 #define XK_3270_PA1            0xFD0A
0x0000 #define XK_3270_PA2            0xFD0B
0x0000 #define XK_3270_PA3            0xFD0C
0x0000 #define XK_3270_Test           0xFD0D
0x0000 #define XK_3270_Attn           0xFD0E
0x0000 #define XK_3270_CursorBlink    0xFD0F
0x0000 #define XK_3270_AltCursor      0xFD10
0x0000 #define XK_3270_KeyClick       0xFD11
0x0000 #define XK_3270_Jump           0xFD12
0x0000 #define XK_3270_Ident          0xFD13
0x0000 #define XK_3270_Rule           0xFD14
0x0000 #define XK_3270_Copy           0xFD15
0x0000 #define XK_3270_Play           0xFD16
0x0000 #define XK_3270_Setup          0xFD17
0x0000 #define XK_3270_Record         0xFD18
0x0000 #define XK_3270_ChangeScreen   0xFD19
0x0000 #define XK_3270_DeleteWord     0xFD1A
0x0000 #define XK_3270_ExSelect       0xFD1B
0x0000 #define XK_3270_CursorSelect   0xFD1C
0x0000 #define XK_3270_PrintScreen    0xFD1D
0x0000 #define XK_3270_Enter          0xFD1E
#endif

/*
 *  Latin 1
 *  Byte 3 = 0
 */
// yan: skip Latin1 as it is mapped to Unicode 1:1
#ifdef XK_LATIN1
0x0000 #define XK_space               0x020
0x0000 #define XK_exclam              0x021
0x0000 #define XK_quotedbl            0x022
0x0000 #define XK_numbersign          0x023
0x0000 #define XK_dollar              0x024
0x0000 #define XK_percent             0x025
0x0000 #define XK_ampersand           0x026
0x0000 #define XK_apostrophe          0x027
0x0000 #define XK_quoteright          0x027    /* deprecated */
0x0000 #define XK_parenleft           0x028
0x0000 #define XK_parenright          0x029
0x0000 #define XK_asterisk            0x02a
0x0000 #define XK_plus                0x02b
0x0000 #define XK_comma               0x02c
0x0000 #define XK_minus               0x02d
0x0000 #define XK_period              0x02e
0x0000 #define XK_slash               0x02f
0x0000 #define XK_0                   0x030
0x0000 #define XK_1                   0x031
0x0000 #define XK_2                   0x032
0x0000 #define XK_3                   0x033
0x0000 #define XK_4                   0x034
0x0000 #define XK_5                   0x035
0x0000 #define XK_6                   0x036
0x0000 #define XK_7                   0x037
0x0000 #define XK_8                   0x038
0x0000 #define XK_9                   0x039
0x0000 #define XK_colon               0x03a
0x0000 #define XK_semicolon           0x03b
0x0000 #define XK_less                0x03c
0x0000 #define XK_equal               0x03d
0x0000 #define XK_greater             0x03e
0x0000 #define XK_question            0x03f
0x0000 #define XK_at                  0x040
0x0000 #define XK_A                   0x041
0x0000 #define XK_B                   0x042
0x0000 #define XK_C                   0x043
0x0000 #define XK_D                   0x044
0x0000 #define XK_E                   0x045
0x0000 #define XK_F                   0x046
0x0000 #define XK_G                   0x047
0x0000 #define XK_H                   0x048
0x0000 #define XK_I                   0x049
0x0000 #define XK_J                   0x04a
0x0000 #define XK_K                   0x04b
0x0000 #define XK_L                   0x04c
0x0000 #define XK_M                   0x04d
0x0000 #define XK_N                   0x04e
0x0000 #define XK_O                   0x04f
0x0000 #define XK_P                   0x050
0x0000 #define XK_Q                   0x051
0x0000 #define XK_R                   0x052
0x0000 #define XK_S                   0x053
0x0000 #define XK_T                   0x054
0x0000 #define XK_U                   0x055
0x0000 #define XK_V                   0x056
0x0000 #define XK_W                   0x057
0x0000 #define XK_X                   0x058
0x0000 #define XK_Y                   0x059
0x0000 #define XK_Z                   0x05a
0x0000 #define XK_bracketleft         0x05b
0x0000 #define XK_backslash           0x05c
0x0000 #define XK_bracketright        0x05d
0x0000 #define XK_asciicircum         0x05e
0x0000 #define XK_underscore          0x05f
0x0000 #define XK_grave               0x060
0x0000 #define XK_quoteleft           0x060    /* deprecated */
0x0000 #define XK_a                   0x061
0x0000 #define XK_b                   0x062
0x0000 #define XK_c                   0x063
0x0000 #define XK_d                   0x064
0x0000 #define XK_e                   0x065
0x0000 #define XK_f                   0x066
0x0000 #define XK_g                   0x067
0x0000 #define XK_h                   0x068
0x0000 #define XK_i                   0x069
0x0000 #define XK_j                   0x06a
0x0000 #define XK_k                   0x06b
0x0000 #define XK_l                   0x06c
0x0000 #define XK_m                   0x06d
0x0000 #define XK_n                   0x06e
0x0000 #define XK_o                   0x06f
0x0000 #define XK_p                   0x070
0x0000 #define XK_q                   0x071
0x0000 #define XK_r                   0x072
0x0000 #define XK_s                   0x073
0x0000 #define XK_t                   0x074
0x0000 #define XK_u                   0x075
0x0000 #define XK_v                   0x076
0x0000 #define XK_w                   0x077
0x0000 #define XK_x                   0x078
0x0000 #define XK_y                   0x079
0x0000 #define XK_z                   0x07a
0x0000 #define XK_braceleft           0x07b
0x0000 #define XK_bar                 0x07c
0x0000 #define XK_braceright          0x07d
0x0000 #define XK_asciitilde          0x07e

0x0000 #define XK_nobreakspace        0x0a0
0x0000 #define XK_exclamdown          0x0a1
0x0000 #define XK_cent                   0x0a2
0x0000 #define XK_sterling            0x0a3
0x0000 #define XK_currency            0x0a4
0x0000 #define XK_yen                 0x0a5
0x0000 #define XK_brokenbar           0x0a6
0x0000 #define XK_section             0x0a7
0x0000 #define XK_diaeresis           0x0a8
0x0000 #define XK_copyright           0x0a9
0x0000 #define XK_ordfeminine         0x0aa
0x0000 #define XK_guillemotleft       0x0ab    /* left angle quotation mark */
0x0000 #define XK_notsign             0x0ac
0x0000 #define XK_hyphen              0x0ad
0x0000 #define XK_registered          0x0ae
0x0000 #define XK_macron              0x0af
0x0000 #define XK_degree              0x0b0
0x0000 #define XK_plusminus           0x0b1
0x0000 #define XK_twosuperior         0x0b2
0x0000 #define XK_threesuperior       0x0b3
0x0000 #define XK_acute               0x0b4
0x0000 #define XK_mu                  0x0b5
0x0000 #define XK_paragraph           0x0b6
0x0000 #define XK_periodcentered      0x0b7
0x0000 #define XK_cedilla             0x0b8
0x0000 #define XK_onesuperior         0x0b9
0x0000 #define XK_masculine           0x0ba
0x0000 #define XK_guillemotright      0x0bb    /* right angle quotation mark */
0x0000 #define XK_onequarter          0x0bc
0x0000 #define XK_onehalf             0x0bd
0x0000 #define XK_threequarters       0x0be
0x0000 #define XK_questiondown        0x0bf
0x0000 #define XK_Agrave              0x0c0
0x0000 #define XK_Aacute              0x0c1
0x0000 #define XK_Acircumflex         0x0c2
0x0000 #define XK_Atilde              0x0c3
0x0000 #define XK_Adiaeresis          0x0c4
0x0000 #define XK_Aring               0x0c5
0x0000 #define XK_AE                  0x0c6
0x0000 #define XK_Ccedilla            0x0c7
0x0000 #define XK_Egrave              0x0c8
0x0000 #define XK_Eacute              0x0c9
0x0000 #define XK_Ecircumflex         0x0ca
0x0000 #define XK_Ediaeresis          0x0cb
0x0000 #define XK_Igrave              0x0cc
0x0000 #define XK_Iacute              0x0cd
0x0000 #define XK_Icircumflex         0x0ce
0x0000 #define XK_Idiaeresis          0x0cf
0x0000 #define XK_ETH                 0x0d0
0x0000 #define XK_Eth                 0x0d0    /* deprecated */
0x0000 #define XK_Ntilde              0x0d1
0x0000 #define XK_Ograve              0x0d2
0x0000 #define XK_Oacute              0x0d3
0x0000 #define XK_Ocircumflex         0x0d4
0x0000 #define XK_Otilde              0x0d5
0x0000 #define XK_Odiaeresis          0x0d6
0x0000 #define XK_multiply            0x0d7
0x0000 #define XK_Ooblique            0x0d8
0x0000 #define XK_Ugrave              0x0d9
0x0000 #define XK_Uacute              0x0da
0x0000 #define XK_Ucircumflex         0x0db
0x0000 #define XK_Udiaeresis          0x0dc
0x0000 #define XK_Yacute              0x0dd
0x0000 #define XK_THORN               0x0de
0x0000 #define XK_Thorn               0x0de    /* deprecated */
0x0000 #define XK_ssharp              0x0df
0x0000 #define XK_agrave              0x0e0
0x0000 #define XK_aacute              0x0e1
0x0000 #define XK_acircumflex         0x0e2
0x0000 #define XK_atilde              0x0e3
0x0000 #define XK_adiaeresis          0x0e4
0x0000 #define XK_aring               0x0e5
0x0000 #define XK_ae                  0x0e6
0x0000 #define XK_ccedilla            0x0e7
0x0000 #define XK_egrave              0x0e8
0x0000 #define XK_eacute              0x0e9
0x0000 #define XK_ecircumflex         0x0ea
0x0000 #define XK_ediaeresis          0x0eb
0x0000 #define XK_igrave              0x0ec
0x0000 #define XK_iacute              0x0ed
0x0000 #define XK_icircumflex         0x0ee
0x0000 #define XK_idiaeresis          0x0ef
0x0000 #define XK_eth                 0x0f0
0x0000 #define XK_ntilde              0x0f1
0x0000 #define XK_ograve              0x0f2
0x0000 #define XK_oacute              0x0f3
0x0000 #define XK_ocircumflex         0x0f4
0x0000 #define XK_otilde              0x0f5
0x0000 #define XK_odiaeresis          0x0f6
0x0000 #define XK_division            0x0f7
0x0000 #define XK_oslash              0x0f8
0x0000 #define XK_ugrave              0x0f9
0x0000 #define XK_uacute              0x0fa
0x0000 #define XK_ucircumflex         0x0fb
0x0000 #define XK_udiaeresis          0x0fc
0x0000 #define XK_yacute              0x0fd
0x0000 #define XK_thorn               0x0fe
0x0000 #define XK_ydiaeresis          0x0ff
#endif /* XK_LATIN1 */

/*
 *   Latin 2
 *   Byte 3 = 1
 */

#ifdef XK_LATIN2
0x0104 #define XK_Aogonek             0x1a1
0x02d8 #define XK_breve               0x1a2
0x0141 #define XK_Lstroke             0x1a3
0x013d #define XK_Lcaron              0x1a5
0x015a #define XK_Sacute              0x1a6
0x0160 #define XK_Scaron              0x1a9
0x015e #define XK_Scedilla            0x1aa
0x0164 #define XK_Tcaron              0x1ab
0x0179 #define XK_Zacute              0x1ac
0x017d #define XK_Zcaron              0x1ae
0x017b #define XK_Zabovedot           0x1af
0x0105 #define XK_aogonek             0x1b1
0x02db #define XK_ogonek              0x1b2
0x0142 #define XK_lstroke             0x1b3
0x013e #define XK_lcaron              0x1b5
0x015b #define XK_sacute              0x1b6
0x02c7 #define XK_caron               0x1b7
0x0161 #define XK_scaron              0x1b9
0x015f #define XK_scedilla            0x1ba
0x0165 #define XK_tcaron              0x1bb
0x017a #define XK_zacute              0x1bc
0x02dd #define XK_doubleacute         0x1bd
0x017e #define XK_zcaron              0x1be
0x017c #define XK_zabovedot           0x1bf
0x0154 #define XK_Racute              0x1c0
0x0102 #define XK_Abreve              0x1c3
0x0139 #define XK_Lacute              0x1c5
0x0106 #define XK_Cacute              0x1c6
0x010c #define XK_Ccaron              0x1c8
0x0118 #define XK_Eogonek             0x1ca
0x011a #define XK_Ecaron              0x1cc
0x010e #define XK_Dcaron              0x1cf
0x0110 #define XK_Dstroke             0x1d0
0x0143 #define XK_Nacute              0x1d1
0x0147 #define XK_Ncaron              0x1d2
0x0150 #define XK_Odoubleacute        0x1d5
0x0158 #define XK_Rcaron              0x1d8
0x016e #define XK_Uring               0x1d9
0x0170 #define XK_Udoubleacute        0x1db
0x0162 #define XK_Tcedilla            0x1de
0x0155 #define XK_racute              0x1e0
0x0103 #define XK_abreve              0x1e3
0x013a #define XK_lacute              0x1e5
0x0107 #define XK_cacute              0x1e6
0x010d #define XK_ccaron              0x1e8
0x0119 #define XK_eogonek             0x1ea
0x011b #define XK_ecaron              0x1ec
0x010f #define XK_dcaron              0x1ef
0x0111 #define XK_dstroke             0x1f0
0x0144 #define XK_nacute              0x1f1
0x0148 #define XK_ncaron              0x1f2
0x0151 #define XK_odoubleacute        0x1f5
0x0171 #define XK_udoubleacute        0x1fb
0x0159 #define XK_rcaron              0x1f8
0x016f #define XK_uring               0x1f9
0x0163 #define XK_tcedilla            0x1fe
0x02d9 #define XK_abovedot            0x1ff
#endif /* XK_LATIN2 */

/*
 *   Latin 3
 *   Byte 3 = 2
 */

#ifdef XK_LATIN3
0x0126 #define XK_Hstroke             0x2a1
0x0124 #define XK_Hcircumflex         0x2a6
0x0130 #define XK_Iabovedot           0x2a9
0x011e #define XK_Gbreve              0x2ab
0x0134 #define XK_Jcircumflex         0x2ac
0x0127 #define XK_hstroke             0x2b1
0x0125 #define XK_hcircumflex         0x2b6
0x0131 #define XK_idotless            0x2b9
0x011f #define XK_gbreve              0x2bb
0x0135 #define XK_jcircumflex         0x2bc
0x010a #define XK_Cabovedot           0x2c5
0x0108 #define XK_Ccircumflex         0x2c6
0x0120 #define XK_Gabovedot           0x2d5
0x011c #define XK_Gcircumflex         0x2d8
0x016c #define XK_Ubreve              0x2dd
0x015c #define XK_Scircumflex         0x2de
0x010b #define XK_cabovedot           0x2e5
0x0109 #define XK_ccircumflex         0x2e6
0x0121 #define XK_gabovedot           0x2f5
0x011d #define XK_gcircumflex         0x2f8
0x016d #define XK_ubreve              0x2fd
0x015d #define XK_scircumflex         0x2fe
#endif /* XK_LATIN3 */


/*
 *   Latin 4
 *   Byte 3 = 3
 */

#ifdef XK_LATIN4
0x0138 #define XK_kra                 0x3a2
0x0000 #define XK_kappa               0x3a2    /* deprecated */
0x0156 #define XK_Rcedilla            0x3a3
0x0128 #define XK_Itilde              0x3a5
0x013b #define XK_Lcedilla            0x3a6
0x0112 #define XK_Emacron             0x3aa
0x0122 #define XK_Gcedilla            0x3ab
0x0166 #define XK_Tslash              0x3ac
0x0157 #define XK_rcedilla            0x3b3
0x0129 #define XK_itilde              0x3b5
0x013c #define XK_lcedilla            0x3b6
0x0113 #define XK_emacron             0x3ba
0x0123 #define XK_gcedilla            0x3bb
0x0167 #define XK_tslash              0x3bc
0x014a #define XK_ENG                 0x3bd
0x014b #define XK_eng                 0x3bf
0x0100 #define XK_Amacron             0x3c0
0x012e #define XK_Iogonek             0x3c7
0x0116 #define XK_Eabovedot           0x3cc
0x012a #define XK_Imacron             0x3cf
0x0145 #define XK_Ncedilla            0x3d1
0x014c #define XK_Omacron             0x3d2
0x0136 #define XK_Kcedilla            0x3d3
0x0172 #define XK_Uogonek             0x3d9
0x0168 #define XK_Utilde              0x3dd
0x016a #define XK_Umacron             0x3de
0x0101 #define XK_amacron             0x3e0
0x012f #define XK_iogonek             0x3e7
0x0117 #define XK_eabovedot           0x3ec
0x012b #define XK_imacron             0x3ef
0x0146 #define XK_ncedilla            0x3f1
0x014d #define XK_omacron             0x3f2
0x0137 #define XK_kcedilla            0x3f3
0x0173 #define XK_uogonek             0x3f9
0x0169 #define XK_utilde              0x3fd
0x016b #define XK_umacron             0x3fe
#endif /* XK_LATIN4 */

/*
 * Latin-8
 * Byte 3 = 18
 */
#ifdef XK_LATIN8
0x1e02 #define XK_Babovedot           0x12a1
0x1e03 #define XK_babovedot           0x12a2
0x1e0a #define XK_Dabovedot           0x12a6
0x1e80 #define XK_Wgrave              0x12a8
0x1e82 #define XK_Wacute              0x12aa
0x1e0b #define XK_dabovedot           0x12ab
0x1ef2 #define XK_Ygrave              0x12ac
0x1e1e #define XK_Fabovedot           0x12b0
0x1e1f #define XK_fabovedot           0x12b1
0x1e40 #define XK_Mabovedot           0x12b4
0x1e41 #define XK_mabovedot           0x12b5
0x1e56 #define XK_Pabovedot           0x12b7
0x1e81 #define XK_wgrave              0x12b8
0x1e57 #define XK_pabovedot           0x12b9
0x1e83 #define XK_wacute              0x12ba
0x1e60 #define XK_Sabovedot           0x12bb
0x1ef3 #define XK_ygrave              0x12bc
0x1e84 #define XK_Wdiaeresis          0x12bd
0x1e85 #define XK_wdiaeresis          0x12be
0x1e61 #define XK_sabovedot           0x12bf
0x017 4#define XK_Wcircumflex         0x12d0
0x1e6a #define XK_Tabovedot           0x12d7
0x0176 #define XK_Ycircumflex         0x12de
0x0175 #define XK_wcircumflex         0x12f0
0x1e6b #define XK_tabovedot           0x12f7
0x0177 #define XK_ycircumflex         0x12fe
#endif /* XK_LATIN8 */

/*
 * Latin-9 (a.k.a. Latin-0)
 * Byte 3 = 19
 */

#ifdef XK_LATIN9
0x0152 #define XK_OE                  0x13bc
0x0153 #define XK_oe                  0x13bd
0x0178 #define XK_Ydiaeresis          0x13be
#endif /* XK_LATIN9 */

/*
 * Katakana
 * Byte 3 = 4
 */

#ifdef XK_KATAKANA
0x203e #define XK_overline                       0x47e
0x3002 #define XK_kana_fullstop                               0x4a1
0x300c #define XK_kana_openingbracket                         0x4a2
0x300d #define XK_kana_closingbracket                         0x4a3
0x3001 #define XK_kana_comma                                  0x4a4
0x30fb #define XK_kana_conjunctive                            0x4a5
0x0000 #define XK_kana_middledot                              0x4a5  /* deprecated */
0x30f2 #define XK_kana_WO                                     0x4a6
0x30a1 #define XK_kana_a                                      0x4a7
0x30a3 #define XK_kana_i                                      0x4a8
0x30a5 #define XK_kana_u                                      0x4a9
0x30a7 #define XK_kana_e                                      0x4aa
0x30a9 #define XK_kana_o                                      0x4ab
0x30e3 #define XK_kana_ya                                     0x4ac
0x30e5 #define XK_kana_yu                                     0x4ad
0x30e7 #define XK_kana_yo                                     0x4ae
0x30c3 #define XK_kana_tsu                                    0x4af
0x0000 #define XK_kana_tu                                     0x4af  /* deprecated */
0x30fc #define XK_prolongedsound                              0x4b0
0x30a2 #define XK_kana_A                                      0x4b1
0x30a4 #define XK_kana_I                                      0x4b2
0x30a6 #define XK_kana_U                                      0x4b3
0x30a8 #define XK_kana_E                                      0x4b4
0x30aa #define XK_kana_O                                      0x4b5
0x30ab #define XK_kana_KA                                     0x4b6
0x30ad #define XK_kana_KI                                     0x4b7
0x30af #define XK_kana_KU                                     0x4b8
0x30b1 #define XK_kana_KE                                     0x4b9
0x30b3 #define XK_kana_KO                                     0x4ba
0x30b5 #define XK_kana_SA                                     0x4bb
0x30b7 #define XK_kana_SHI                                    0x4bc
0x30b9 #define XK_kana_SU                                     0x4bd
0x30bb #define XK_kana_SE                                     0x4be
0x30bd #define XK_kana_SO                                     0x4bf
0x30bf #define XK_kana_TA                                     0x4c0
0x30c1 #define XK_kana_CHI                                    0x4c1
0x0000 #define XK_kana_TI                                     0x4c1  /* deprecated */
0x30c4 #define XK_kana_TSU                                    0x4c2
0x0000 #define XK_kana_TU                                     0x4c2  /* deprecated */
0x30c6 #define XK_kana_TE                                     0x4c3
0x30c8 #define XK_kana_TO                                     0x4c4
0x30ca #define XK_kana_NA                                     0x4c5
0x30cb #define XK_kana_NI                                     0x4c6
0x30cc #define XK_kana_NU                                     0x4c7
0x30cd #define XK_kana_NE                                     0x4c8
0x30ce #define XK_kana_NO                                     0x4c9
0x30cf #define XK_kana_HA                                     0x4ca
0x30d2 #define XK_kana_HI                                     0x4cb
0x30d5 #define XK_kana_FU                                     0x4cc
0x0000 #define XK_kana_HU                                     0x4cc  /* deprecated */
0x30d8 #define XK_kana_HE                                     0x4cd
0x30db #define XK_kana_HO                                     0x4ce
0x30de #define XK_kana_MA                                     0x4cf
0x30df #define XK_kana_MI                                     0x4d0
0x30e0 #define XK_kana_MU                                     0x4d1
0x30e1 #define XK_kana_ME                                     0x4d2
0x30e2 #define XK_kana_MO                                     0x4d3
0x30e4 #define XK_kana_YA                                     0x4d4
0x30e6 #define XK_kana_YU                                     0x4d5
0x30e8 #define XK_kana_YO                                     0x4d6
0x30e9 #define XK_kana_RA                                     0x4d7
0x30ea #define XK_kana_RI                                     0x4d8
0x30eb #define XK_kana_RU                                     0x4d9
0x30ec #define XK_kana_RE                                     0x4da
0x30ed #define XK_kana_RO                                     0x4db
0x30ef #define XK_kana_WA                                     0x4dc
0x30f3 #define XK_kana_N                                      0x4dd
0x309b #define XK_voicedsound                                 0x4de
0x309c #define XK_semivoicedsound                             0x4df
0x0000 #define XK_kana_switch          0xFF7E  /* Alias for mode_switch */
#endif /* XK_KATAKANA */

/*
 *  Arabic
 *  Byte 3 = 5
 */

#ifdef XK_ARABIC
0x0670 #define XK_Farsi_0                                     0x590
0x06f1 #define XK_Farsi_1                                     0x591
0x06f2 #define XK_Farsi_2                                     0x592
0x06f3 #define XK_Farsi_3                                     0x593
0x06f4 #define XK_Farsi_4                                     0x594
0x06f5 #define XK_Farsi_5                                     0x595
0x06f6 #define XK_Farsi_6                                     0x596
0x06f7 #define XK_Farsi_7                                     0x597
0x06f8 #define XK_Farsi_8                                     0x598
0x06f9 #define XK_Farsi_9                                     0x599
0x066a #define XK_Arabic_percent                              0x5a5
0x0670 #define XK_Arabic_superscript_alef                     0x5a6
0x0679 #define XK_Arabic_tteh                                 0x5a7
0x067e #define XK_Arabic_peh                                  0x5a8
0x0686 #define XK_Arabic_tcheh                                0x5a9
0x0688 #define XK_Arabic_ddal                                 0x5aa
0x0691 #define XK_Arabic_rreh                                 0x5ab
0x060c #define XK_Arabic_comma                                0x5ac
0x06d4 #define XK_Arabic_fullstop                             0x5ae
0x0660 #define XK_Arabic_0                                    0x5b0
0x0661 #define XK_Arabic_1                                    0x5b1
0x0662 #define XK_Arabic_2                                    0x5b2
0x0663 #define XK_Arabic_3                                    0x5b3
0x0664 #define XK_Arabic_4                                    0x5b4
0x0665 #define XK_Arabic_5                                    0x5b5
0x0666 #define XK_Arabic_6                                    0x5b6
0x0667 #define XK_Arabic_7                                    0x5b7
0x0668 #define XK_Arabic_8                                    0x5b8
0x0669 #define XK_Arabic_9                                    0x5b9
0x061b #define XK_Arabic_semicolon                            0x5bb
0x061f #define XK_Arabic_question_mark                        0x5bf
0x0621 #define XK_Arabic_hamza                                0x5c1
0x0622 #define XK_Arabic_maddaonalef                          0x5c2
0x0623 #define XK_Arabic_hamzaonalef                          0x5c3
0x0624 #define XK_Arabic_hamzaonwaw                           0x5c4
0x0625 #define XK_Arabic_hamzaunderalef                       0x5c5
0x0626 #define XK_Arabic_hamzaonyeh                           0x5c6
0x0627 #define XK_Arabic_alef                                 0x5c7
0x0628 #define XK_Arabic_beh                                  0x5c8
0x0629 #define XK_Arabic_tehmarbuta                           0x5c9
0x062a #define XK_Arabic_teh                                  0x5ca
0x062b #define XK_Arabic_theh                                 0x5cb
0x062c #define XK_Arabic_jeem                                 0x5cc
0x062d #define XK_Arabic_hah                                  0x5cd
0x062e #define XK_Arabic_khah                                 0x5ce
0x062f #define XK_Arabic_dal                                  0x5cf
0x0630 #define XK_Arabic_thal                                 0x5d0
0x0631 #define XK_Arabic_ra                                   0x5d1
0x0632 #define XK_Arabic_zain                                 0x5d2
0x0633 #define XK_Arabic_seen                                 0x5d3
0x0634 #define XK_Arabic_sheen                                0x5d4
0x0635 #define XK_Arabic_sad                                  0x5d5
0x0636 #define XK_Arabic_dad                                  0x5d6
0x0637 #define XK_Arabic_tah                                  0x5d7
0x0638 #define XK_Arabic_zah                                  0x5d8
0x0639 #define XK_Arabic_ain                                  0x5d9
0x063a #define XK_Arabic_ghain                                0x5da
0x0640 #define XK_Arabic_tatweel                              0x5e0
0x0641 #define XK_Arabic_feh                                  0x5e1
0x0642 #define XK_Arabic_qaf                                  0x5e2
0x0643 #define XK_Arabic_kaf                                  0x5e3
0x0644 #define XK_Arabic_lam                                  0x5e4
0x0645 #define XK_Arabic_meem                                 0x5e5
0x0646 #define XK_Arabic_noon                                 0x5e6
0x0647 #define XK_Arabic_ha                                   0x5e7
0x0000 #define XK_Arabic_heh                                  0x5e7  /* deprecated */
0x0648 #define XK_Arabic_waw                                  0x5e8
0x0649 #define XK_Arabic_alefmaksura                          0x5e9
0x064a #define XK_Arabic_yeh                                  0x5ea
0x064b #define XK_Arabic_fathatan                             0x5eb
0x064c #define XK_Arabic_dammatan                             0x5ec
0x064d #define XK_Arabic_kasratan                             0x5ed
0x064e #define XK_Arabic_fatha                                0x5ee
0x064f #define XK_Arabic_damma                                0x5ef
0x0650 #define XK_Arabic_kasra                                0x5f0
0x0651 #define XK_Arabic_shadda                               0x5f1
0x0652 #define XK_Arabic_sukun                                0x5f2
0x0653 #define XK_Arabic_madda_above                          0x5f3
0x0654 #define XK_Arabic_hamza_above                          0x5f4
0x0655 #define XK_Arabic_hamza_below                          0x5f5
0x0698 #define XK_Arabic_jeh                                  0x5f6
0x06a4 #define XK_Arabic_veh                                  0x5f7
0x06a9 #define XK_Arabic_keheh                                0x5f8
0x06af #define XK_Arabic_gaf                                  0x5f9
0x06ba #define XK_Arabic_noon_ghunna                          0x5fa
0x06be #define XK_Arabic_heh_doachashmee                      0x5fb
0x06cc #define XK_Farsi_yeh                                   0x5fc
0x06d2 #define XK_Arabic_yeh_baree                            0x5fd
0x06c1 #define XK_Arabic_heh_goal                             0x5fe
0x0000 #define XK_Arabic_switch        0xFF7E  /* Alias for mode_switch */
#endif /* XK_ARABIC */

/*
 * Cyrillic
 * Byte 3 = 6
 */
#ifdef XK_CYRILLIC
0x0492 #define XK_Cyrillic_GHE_bar                               0x680
0x0493 #define XK_Cyrillic_ghe_bar                               0x690
0x0496 #define XK_Cyrillic_ZHE_descender                       0x681
0x0497 #define XK_Cyrillic_zhe_descender                       0x691
0x049a #define XK_Cyrillic_KA_descender                   0x682
0x049b #define XK_Cyrillic_ka_descender                       0x692
0x049c #define XK_Cyrillic_KA_vertstroke                   0x683
0x049d #define XK_Cyrillic_ka_vertstroke                   0x693
0x04a2 #define XK_Cyrillic_EN_descender                   0x684
0x04a3 #define XK_Cyrillic_en_descender                   0x694
0x04ae #define XK_Cyrillic_U_straight                       0x685
0x04af #define XK_Cyrillic_u_straight                       0x695
0x04b0 #define XK_Cyrillic_U_straight_bar                   0x686
0x04b1 #define XK_Cyrillic_u_straight_bar                   0x696
0x04b2 #define XK_Cyrillic_HA_descender                       0x687
0x04b3 #define XK_Cyrillic_ha_descender                       0x697
0x04b6 #define XK_Cyrillic_CHE_descender                       0x688
0x04b7 #define XK_Cyrillic_che_descender                       0x698
0x04b8 #define XK_Cyrillic_CHE_vertstroke                       0x689
0x04b9 #define XK_Cyrillic_che_vertstroke                       0x699
0x04ba #define XK_Cyrillic_SHHA                               0x68a
0x04bb #define XK_Cyrillic_shha                               0x69a

0x04d8 #define XK_Cyrillic_SCHWA                               0x68c
0x04d9 #define XK_Cyrillic_schwa                               0x69c
0x04e2 #define XK_Cyrillic_I_macron                           0x68d
0x04e3 #define XK_Cyrillic_i_macron                           0x69d
0x04e8 #define XK_Cyrillic_O_bar                               0x68e
0x04e9 #define XK_Cyrillic_o_bar                               0x69e
0x04ee #define XK_Cyrillic_U_macron                           0x68f
0x04ef #define XK_Cyrillic_u_macron                           0x69f

0x0452 #define XK_Serbian_dje                                 0x6a1
0x0453 #define XK_Macedonia_gje                               0x6a2
0x0451 #define XK_Cyrillic_io                                 0x6a3
0x0454 #define XK_Ukrainian_ie                                0x6a4
0x0000 #define XK_Ukranian_je                                 0x6a4  /* deprecated */
0x0455 #define XK_Macedonia_dse                               0x6a5
0x0456 #define XK_Ukrainian_i                                 0x6a6
0x0000 #define XK_Ukranian_i                                  0x6a6  /* deprecated */
0x0457 #define XK_Ukrainian_yi                                0x6a7
0x0000 #define XK_Ukranian_yi                                 0x6a7  /* deprecated */
0x0458 #define XK_Cyrillic_je                                 0x6a8
0x0000 #define XK_Serbian_je                                  0x6a8  /* deprecated */
0x0459 #define XK_Cyrillic_lje                                0x6a9
0x0000 #define XK_Serbian_lje                                 0x6a9  /* deprecated */
0x045a #define XK_Cyrillic_nje                                0x6aa
0x0000 #define XK_Serbian_nje                                 0x6aa  /* deprecated */
0x045b #define XK_Serbian_tshe                                0x6ab
0x045c #define XK_Macedonia_kje                               0x6ac
0x0491 #define XK_Ukrainian_ghe_with_upturn                   0x6ad
0x045e #define XK_Byelorussian_shortu                         0x6ae
0x045f #define XK_Cyrillic_dzhe                               0x6af
0x0000 #define XK_Serbian_dze                                 0x6af  /* deprecated */
0x2116 #define XK_numerosign                                  0x6b0
0x0402 #define XK_Serbian_DJE                                 0x6b1
0x0403 #define XK_Macedonia_GJE                               0x6b2
0x0401 #define XK_Cyrillic_IO                                 0x6b3
0x0404 #define XK_Ukrainian_IE                                0x6b4
0x0000 #define XK_Ukranian_JE                                 0x6b4  /* deprecated */
0x0405 #define XK_Macedonia_DSE                               0x6b5
0x0406 #define XK_Ukrainian_I                                 0x6b6
0x0000 #define XK_Ukranian_I                                  0x6b6  /* deprecated */
0x0407 #define XK_Ukrainian_YI                                0x6b7
0x0000 #define XK_Ukranian_YI                                 0x6b7  /* deprecated */
0x0408 #define XK_Cyrillic_JE                                 0x6b8
0x0000 #define XK_Serbian_JE                                  0x6b8  /* deprecated */
0x0409 #define XK_Cyrillic_LJE                                0x6b9
0x0000 #define XK_Serbian_LJE                                 0x6b9  /* deprecated */
0x040a #define XK_Cyrillic_NJE                                0x6ba
0x0000 #define XK_Serbian_NJE                                 0x6ba  /* deprecated */
0x040b #define XK_Serbian_TSHE                                0x6bb
0x040c #define XK_Macedonia_KJE                               0x6bc
0x0490 #define XK_Ukrainian_GHE_WITH_UPTURN                   0x6bd
0x040e #define XK_Byelorussian_SHORTU                         0x6be
0x040f #define XK_Cyrillic_DZHE                               0x6bf
0x0000 #define XK_Serbian_DZE                                 0x6bf  /* deprecated */
0x044e #define XK_Cyrillic_yu                                 0x6c0
0x0430 #define XK_Cyrillic_a                                  0x6c1
0x0431 #define XK_Cyrillic_be                                 0x6c2
0x0446 #define XK_Cyrillic_tse                                0x6c3
0x0434 #define XK_Cyrillic_de                                 0x6c4
0x0435 #define XK_Cyrillic_ie                                 0x6c5
0x0444 #define XK_Cyrillic_ef                                 0x6c6
0x0433 #define XK_Cyrillic_ghe                                0x6c7
0x0445 #define XK_Cyrillic_ha                                 0x6c8
0x0438 #define XK_Cyrillic_i                                  0x6c9
0x0439 #define XK_Cyrillic_shorti                             0x6ca
0x043a #define XK_Cyrillic_ka                                 0x6cb
0x043b #define XK_Cyrillic_el                                 0x6cc
0x043c #define XK_Cyrillic_em                                 0x6cd
0x043d #define XK_Cyrillic_en                                 0x6ce
0x043e #define XK_Cyrillic_o                                  0x6cf
0x043f #define XK_Cyrillic_pe                                 0x6d0
0x044f #define XK_Cyrillic_ya                                 0x6d1
0x0440 #define XK_Cyrillic_er                                 0x6d2
0x0441 #define XK_Cyrillic_es                                 0x6d3
0x0442 #define XK_Cyrillic_te                                 0x6d4
0x0443 #define XK_Cyrillic_u                                  0x6d5
0x0436 #define XK_Cyrillic_zhe                                0x6d6
0x0432 #define XK_Cyrillic_ve                                 0x6d7
0x044c #define XK_Cyrillic_softsign                           0x6d8
0x044b #define XK_Cyrillic_yeru                               0x6d9
0x0437 #define XK_Cyrillic_ze                                 0x6da
0x0448 #define XK_Cyrillic_sha                                0x6db
0x044d #define XK_Cyrillic_e                                  0x6dc
0x0449 #define XK_Cyrillic_shcha                              0x6dd
0x0447 #define XK_Cyrillic_che                                0x6de
0x044a #define XK_Cyrillic_hardsign                           0x6df
0x042e #define XK_Cyrillic_YU                                 0x6e0
0x0410 #define XK_Cyrillic_A                                  0x6e1
0x0411 #define XK_Cyrillic_BE                                 0x6e2
0x0426 #define XK_Cyrillic_TSE                                0x6e3
0x0414 #define XK_Cyrillic_DE                                 0x6e4
0x0415 #define XK_Cyrillic_IE                                 0x6e5
0x0424 #define XK_Cyrillic_EF                                 0x6e6
0x0413 #define XK_Cyrillic_GHE                                0x6e7
0x0425 #define XK_Cyrillic_HA                                 0x6e8
0x0418 #define XK_Cyrillic_I                                  0x6e9
0x0419 #define XK_Cyrillic_SHORTI                             0x6ea
0x041a #define XK_Cyrillic_KA                                 0x6eb
0x041b #define XK_Cyrillic_EL                                 0x6ec
0x041c #define XK_Cyrillic_EM                                 0x6ed
0x041d #define XK_Cyrillic_EN                                 0x6ee
0x041e #define XK_Cyrillic_O                                  0x6ef
0x041f #define XK_Cyrillic_PE                                 0x6f0
0x042f #define XK_Cyrillic_YA                                 0x6f1
0x0420 #define XK_Cyrillic_ER                                 0x6f2
0x0421 #define XK_Cyrillic_ES                                 0x6f3
0x0422 #define XK_Cyrillic_TE                                 0x6f4
0x0423 #define XK_Cyrillic_U                                  0x6f5
0x0416 #define XK_Cyrillic_ZHE                                0x6f6
0x0412 #define XK_Cyrillic_VE                                 0x6f7
0x042c #define XK_Cyrillic_SOFTSIGN                           0x6f8
0x042b #define XK_Cyrillic_YERU                               0x6f9
0x0417 #define XK_Cyrillic_ZE                                 0x6fa
0x0428 #define XK_Cyrillic_SHA                                0x6fb
0x042d #define XK_Cyrillic_E                                  0x6fc
0x0429 #define XK_Cyrillic_SHCHA                              0x6fd
0x0427 #define XK_Cyrillic_CHE                                0x6fe
0x042a #define XK_Cyrillic_HARDSIGN                           0x6ff
#endif /* XK_CYRILLIC */

/*
 * Greek
 * Byte 3 = 7
 */

#ifdef XK_GREEK
0x0386 #define XK_Greek_ALPHAaccent                           0x7a1
0x0388 #define XK_Greek_EPSILONaccent                         0x7a2
0x0389 #define XK_Greek_ETAaccent                             0x7a3
0x038a #define XK_Greek_IOTAaccent                            0x7a4
0x03aa #define XK_Greek_IOTAdieresis                          0x7a5
0x0000 #define XK_Greek_IOTAdiaeresis         XK_Greek_IOTAdieresis /* old typo */
0x038c #define XK_Greek_OMICRONaccent                         0x7a7
0x038e #define XK_Greek_UPSILONaccent                         0x7a8
0x03ab  #define XK_Greek_UPSILONdieresis                       0x7a9
0x038f #define XK_Greek_OMEGAaccent                           0x7ab
0x0385 #define XK_Greek_accentdieresis                        0x7ae
0x2015 #define XK_Greek_horizbar                              0x7af
0x03ac #define XK_Greek_alphaaccent                           0x7b1
0x03ad #define XK_Greek_epsilonaccent                         0x7b2
0x03ae #define XK_Greek_etaaccent                             0x7b3
0x03af #define XK_Greek_iotaaccent                            0x7b4
0x03ca #define XK_Greek_iotadieresis                          0x7b5
0x0390 #define XK_Greek_iotaaccentdieresis                    0x7b6
0x03cc #define XK_Greek_omicronaccent                         0x7b7
0x03cd #define XK_Greek_upsilonaccent                         0x7b8
0x03cb #define XK_Greek_upsilondieresis                       0x7b9
0x03b0 #define XK_Greek_upsilonaccentdieresis                 0x7ba
0x03ce #define XK_Greek_omegaaccent                           0x7bb
0x0391 #define XK_Greek_ALPHA                                 0x7c1
0x0392 #define XK_Greek_BETA                                  0x7c2
0x0393 #define XK_Greek_GAMMA                                 0x7c3
0x0394 #define XK_Greek_DELTA                                 0x7c4
0x0395 #define XK_Greek_EPSILON                               0x7c5
0x0396 #define XK_Greek_ZETA                                  0x7c6
0x0397 #define XK_Greek_ETA                                   0x7c7
0x0398 #define XK_Greek_THETA                                 0x7c8
0x0399 #define XK_Greek_IOTA                                  0x7c9
0x039a #define XK_Greek_KAPPA                                 0x7ca
0x0000 #define XK_Greek_LAMDA                                 0x7cb
0x039b #define XK_Greek_LAMBDA                                0x7cb
0x039c #define XK_Greek_MU                                    0x7cc
0x039d #define XK_Greek_NU                                    0x7cd
0x039e #define XK_Greek_XI                                    0x7ce
0x039f #define XK_Greek_OMICRON                               0x7cf
0x03a0 #define XK_Greek_PI                                    0x7d0
0x03a1 #define XK_Greek_RHO                                   0x7d1
0x03a3 #define XK_Greek_SIGMA                                 0x7d2
0x03a4 #define XK_Greek_TAU                                   0x7d4
0x03a5 #define XK_Greek_UPSILON                               0x7d5
0x03a6 #define XK_Greek_PHI                                   0x7d6
0x03a7 #define XK_Greek_CHI                                   0x7d7
0x03a8 #define XK_Greek_PSI                                   0x7d8
0x03a9 #define XK_Greek_OMEGA                                 0x7d9
0x03b1 #define XK_Greek_alpha                                 0x7e1
0x03b2 #define XK_Greek_beta                                  0x7e2
0x03b3 #define XK_Greek_gamma                                 0x7e3
0x03b4 #define XK_Greek_delta                                 0x7e4
0x03b5 #define XK_Greek_epsilon                               0x7e5
0x03b6 #define XK_Greek_zeta                                  0x7e6
0x03b7 #define XK_Greek_eta                                   0x7e7
0x03b8 #define XK_Greek_theta                                 0x7e8
0x03b9 #define XK_Greek_iota                                  0x7e9
0x03ba #define XK_Greek_kappa                                 0x7ea
0x0000 #define XK_Greek_lamda                                 0x7eb
0x03bb #define XK_Greek_lambda                                0x7eb
0x03bc #define XK_Greek_mu                                    0x7ec
0x03bd #define XK_Greek_nu                                    0x7ed
0x03be #define XK_Greek_xi                                    0x7ee
0x03bf #define XK_Greek_omicron                               0x7ef
0x03c0 #define XK_Greek_pi                                    0x7f0
0x03c1 #define XK_Greek_rho                                   0x7f1
0x03c3 #define XK_Greek_sigma                                 0x7f2
0x03c2 #define XK_Greek_finalsmallsigma                       0x7f3
0x03c4 #define XK_Greek_tau                                   0x7f4
0x03c5 #define XK_Greek_upsilon                               0x7f5
0x03c6 #define XK_Greek_phi                                   0x7f6
0x03c7 #define XK_Greek_chi                                   0x7f7
0x03c8 #define XK_Greek_psi                                   0x7f8
0x03c9 #define XK_Greek_omega                                 0x7f9
0x0000 #define XK_Greek_switch         0xFF7E  /* Alias for mode_switch */
#endif /* XK_GREEK */

/*
 * Technical
 * Byte 3 = 8
 */

#ifdef XK_TECHNICAL
0x23b7 #define XK_leftradical                                 0x8a1
0x250c #define XK_topleftradical                              0x8a2
0x2500 #define XK_horizconnector                              0x8a3
0x2320 #define XK_topintegral                                 0x8a4
0x2321 #define XK_botintegral                                 0x8a5
0x2502 #define XK_vertconnector                               0x8a6
0x23a1 #define XK_topleftsqbracket                            0x8a7
0x23a3 #define XK_botleftsqbracket                            0x8a8
0x23a4 #define XK_toprightsqbracket                           0x8a9
0x23a6 #define XK_botrightsqbracket                           0x8aa
0x239b #define XK_topleftparens                               0x8ab
0x239d #define XK_botleftparens                               0x8ac
0x239e #define XK_toprightparens                              0x8ad
0x23a0 #define XK_botrightparens                              0x8ae
0x23a8 #define XK_leftmiddlecurlybrace                        0x8af
0x23ac #define XK_rightmiddlecurlybrace                       0x8b0
0x0000 #define XK_topleftsummation                            0x8b1
0x0000 #define XK_botleftsummation                            0x8b2
0x0000 #define XK_topvertsummationconnector                   0x8b3
0x0000 #define XK_botvertsummationconnector                   0x8b4
0x0000 #define XK_toprightsummation                           0x8b5
0x0000 #define XK_botrightsummation                           0x8b6
0x0000 #define XK_rightmiddlesummation                        0x8b7
0x2264 #define XK_lessthanequal                               0x8bc
0x2260 #define XK_notequal                                    0x8bd
0x2265 #define XK_greaterthanequal                            0x8be
0x222b #define XK_integral                                    0x8bf
0x2234 #define XK_therefore                                   0x8c0
0x221d #define XK_variation                                   0x8c1
0x221e #define XK_infinity                                    0x8c2
0x2207 #define XK_nabla                                       0x8c5
0x223c #define XK_approximate                                 0x8c8
0x2243 #define XK_similarequal                                0x8c9
0x2104 #define XK_ifonlyif                                    0x8cd
0x21d2 #define XK_implies                                     0x8ce
0x2261 #define XK_identical                                   0x8cf
0x221a #define XK_radical                                     0x8d6
0x2282 #define XK_includedin                                  0x8da
0x2283 #define XK_includes                                    0x8db
0x2229 #define XK_intersection                                0x8dc
0x222a #define XK_union                                       0x8dd
0x2227 #define XK_logicaland                                  0x8de
0x2228 #define XK_logicalor                                   0x8df
0x2202 #define XK_partialderivative                           0x8ef
0x0192 #define XK_function                                    0x8f6
0x2190 #define XK_leftarrow                                   0x8fb
0x2191 #define XK_uparrow                                     0x8fc
0x2192 #define XK_rightarrow                                  0x8fd
0x2193 #define XK_downarrow                                   0x8fe
#endif /* XK_TECHNICAL */

/*
 *  Special
 *  Byte 3 = 9
 */

#ifdef XK_SPECIAL
0x0000 #define XK_blank                                       0x9df
0x25c6 #define XK_soliddiamond                                0x9e0
0x2592 #define XK_checkerboard                                0x9e1
0x2409 #define XK_ht                                          0x9e2
0x240c #define XK_ff                                          0x9e3
0x240d #define XK_cr                                          0x9e4
0x240a #define XK_lf                                          0x9e5
0x2424 #define XK_nl                                          0x9e8
0x240b #define XK_vt                                          0x9e9
0x2518 #define XK_lowrightcorner                              0x9ea
0x2510 #define XK_uprightcorner                               0x9eb
0x250c #define XK_upleftcorner                                0x9ec
0x2514 #define XK_lowleftcorner                               0x9ed
0x253c #define XK_crossinglines                               0x9ee
0x23ba #define XK_horizlinescan1                              0x9ef
0x23bb #define XK_horizlinescan3                              0x9f0
0x2500 #define XK_horizlinescan5                              0x9f1
0x23bc #define XK_horizlinescan7                              0x9f2
0x23bd #define XK_horizlinescan9                              0x9f3
0x251c #define XK_leftt                                       0x9f4
0x2524 #define XK_rightt                                      0x9f5
0x2534 #define XK_bott                                        0x9f6
0x242c #define XK_topt                                        0x9f7
0x2502 #define XK_vertbar                                     0x9f8
#endif /* XK_SPECIAL */

/*
 *  Publishing
 *  Byte 3 = a
 */

#ifdef XK_PUBLISHING
0x2003 #define XK_emspace                                     0xaa1
0x2002 #define XK_enspace                                     0xaa2
0x2004 #define XK_em3space                                    0xaa3
0x2005 #define XK_em4space                                    0xaa4
0x2007 #define XK_digitspace                                  0xaa5
0x2008 #define XK_punctspace                                  0xaa6
0x2009 #define XK_thinspace                                   0xaa7
0x200a #define XK_hairspace                                   0xaa8
0x2014 #define XK_emdash                                      0xaa9
0x2013 #define XK_endash                                      0xaaa
0x2423 #define XK_signifblank                                 0xaac
0x2026 #define XK_ellipsis                                    0xaae
0x2025 #define XK_doubbaselinedot                             0xaaf
0x2153 #define XK_onethird                                    0xab0
0x2154 #define XK_twothirds                                   0xab1
0x2155 #define XK_onefifth                                    0xab2
0x2156 #define XK_twofifths                                   0xab3
0x2157 #define XK_threefifths                                 0xab4
0x2158 #define XK_fourfifths                                  0xab5
0x2159 #define XK_onesixth                                    0xab6
0x215a #define XK_fivesixths                                  0xab7
0x2105 #define XK_careof                                      0xab8
0x2012 #define XK_figdash                                     0xabb
0x27e8 #define XK_leftanglebracket                            0xabc
0x002e #define XK_decimalpoint                                0xabd
0x27e9 #define XK_rightanglebracket                           0xabe
0x0000 #define XK_marker                                      0xabf
0x215b #define XK_oneeighth                                   0xac3
0x215c #define XK_threeeighths                                0xac4
0x215d #define XK_fiveeighths                                 0xac5
0x215e #define XK_seveneighths                                0xac6
0x2122 #define XK_trademark                                   0xac9
0x2613 #define XK_signaturemark                               0xaca
0x0000 #define XK_trademarkincircle                           0xacb
0x25c1 #define XK_leftopentriangle                            0xacc
0x25b7 #define XK_rightopentriangle                           0xacd
0x25cb #define XK_emopencircle                                0xace
0x25af #define XK_emopenrectangle                             0xacf
0x2018 #define XK_leftsinglequotemark                         0xad0
0x2019 #define XK_rightsinglequotemark                        0xad1
0x201c #define XK_leftdoublequotemark                         0xad2
0x201d #define XK_rightdoublequotemark                        0xad3
0x211e #define XK_prescription                                0xad4
0x2032 #define XK_minutes                                     0xad6
0x2033 #define XK_seconds                                     0xad7
0x271d #define XK_latincross                                  0xad9
0x0000 #define XK_hexagram                                    0xada
0x25ac #define XK_filledrectbullet                            0xadb
0x25c0 #define XK_filledlefttribullet                         0xadc
0x25b6 #define XK_filledrighttribullet                        0xadd
0x25cf #define XK_emfilledcircle                              0xade
0x25ae #define XK_emfilledrect                                0xadf
0x25e6 #define XK_enopencircbullet                            0xae0
0x25ab #define XK_enopensquarebullet                          0xae1
0x25ad #define XK_openrectbullet                              0xae2
0x25b3 #define XK_opentribulletup                             0xae3
0x25bd #define XK_opentribulletdown                           0xae4
0x2606 #define XK_openstar                                    0xae5
0x2022 #define XK_enfilledcircbullet                          0xae6
0x25aa #define XK_enfilledsqbullet                            0xae7
0x25b2 #define XK_filledtribulletup                           0xae8
0x25bc #define XK_filledtribulletdown                         0xae9
0x261c #define XK_leftpointer                                 0xaea
0x261e #define XK_rightpointer                                0xaeb
0x2663 #define XK_club                                        0xaec
0x2666 #define XK_diamond                                     0xaed
0x2665 #define XK_heart                                       0xaee
0x2720 #define XK_maltesecross                                0xaf0
0x2020 #define XK_dagger                                      0xaf1
0x2021 #define XK_doubledagger                                0xaf2
0x2713 #define XK_checkmark                                   0xaf3
0x2717 #define XK_ballotcross                                 0xaf4
0x266f #define XK_musicalsharp                                0xaf5
0x266d #define XK_musicalflat                                 0xaf6
0x2642 #define XK_malesymbol                                  0xaf7
0x2640 #define XK_femalesymbol                                0xaf8
0x260e #define XK_telephone                                   0xaf9
0x2315 #define XK_telephonerecorder                           0xafa
0x2117 #define XK_phonographcopyright                         0xafb
0x2038 #define XK_caret                                       0xafc
0x201a #define XK_singlelowquotemark                          0xafd
0x201e #define XK_doublelowquotemark                          0xafe
0x0000 #define XK_cursor                                      0xaff
#endif /* XK_PUBLISHING */

/*
 *  APL
 *  Byte 3 = b
 */

#ifdef XK_APL
0x003c #define XK_leftcaret                                   0xba3
0x003e #define XK_rightcaret                                  0xba6
0x2228 #define XK_downcaret                                   0xba8
0x2227 #define XK_upcaret                                     0xba9
0x00af #define XK_overbar                                     0xbc0
0x22a5 #define XK_downtack                                    0xbc2
0x2229 #define XK_upshoe                                      0xbc3
0x230a #define XK_downstile                                   0xbc4
0x005f #define XK_underbar                                    0xbc6
0x2218 #define XK_jot                                         0xbca
0x2395 #define XK_quad                                        0xbcc
0x22a4 #define XK_uptack                                      0xbce
0x25cb #define XK_circle                                      0xbcf
0x2308 #define XK_upstile                                     0xbd3
0x222a #define XK_downshoe                                    0xbd6
0x2283 #define XK_rightshoe                                   0xbd8
0x2282 #define XK_leftshoe                                    0xbda
0x22a2 #define XK_lefttack                                    0xbdc
0x22a3 #define XK_righttack                                   0xbfc
#endif /* XK_APL */

/*
 * Hebrew
 * Byte 3 = c
 */

#ifdef XK_HEBREW
0x2017 #define XK_hebrew_doublelowline                        0xcdf
0x05d0 #define XK_hebrew_aleph                                0xce0
0x05d1 #define XK_hebrew_bet                                  0xce1
0x0000 #define XK_hebrew_beth                                 0xce1  /* deprecated */
0x05d2 #define XK_hebrew_gimel                                0xce2
0x0000 #define XK_hebrew_gimmel                               0xce2  /* deprecated */
0x05d3 #define XK_hebrew_dalet                                0xce3
0x0000 #define XK_hebrew_daleth                               0xce3  /* deprecated */
0x05d4 #define XK_hebrew_he                                   0xce4
0x05d5 #define XK_hebrew_waw                                  0xce5
0x05d6 #define XK_hebrew_zain                                 0xce6
0x0000 #define XK_hebrew_zayin                                0xce6  /* deprecated */
0x05d7 #define XK_hebrew_chet                                 0xce7
0x0000 #define XK_hebrew_het                                  0xce7  /* deprecated */
0x05d8 #define XK_hebrew_tet                                  0xce8
0x0000 #define XK_hebrew_teth                                 0xce8  /* deprecated */
0x05d9 #define XK_hebrew_yod                                  0xce9
0x05da #define XK_hebrew_finalkaph                            0xcea
0x05db #define XK_hebrew_kaph                                 0xceb
0x05dc #define XK_hebrew_lamed                                0xcec
0x05dd #define XK_hebrew_finalmem                             0xced
0x05de #define XK_hebrew_mem                                  0xcee
0x05df #define XK_hebrew_finalnun                             0xcef
0x05e0 #define XK_hebrew_nun                                  0xcf0
0x05e1 #define XK_hebrew_samech                               0xcf1
0x0000 #define XK_hebrew_samekh                               0xcf1  /* deprecated */
0x05e2 #define XK_hebrew_ayin                                 0xcf2
0x05e3 #define XK_hebrew_finalpe                              0xcf3
0x05e4 #define XK_hebrew_pe                                   0xcf4
0x05e5 #define XK_hebrew_finalzade                            0xcf5
0x0000 #define XK_hebrew_finalzadi                            0xcf5  /* deprecated */
0x05e6 #define XK_hebrew_zade                                 0xcf6
0x0000 #define XK_hebrew_zadi                                 0xcf6  /* deprecated */
0x05e7 #define XK_hebrew_qoph                                 0xcf7
0x0000 #define XK_hebrew_kuf                                  0xcf7  /* deprecated */
0x05e8 #define XK_hebrew_resh                                 0xcf8
0x05e9 #define XK_hebrew_shin                                 0xcf9
0x05ea #define XK_hebrew_taw                                  0xcfa
0x0000 #define XK_hebrew_taf                                  0xcfa  /* deprecated */
0x0000 #define XK_Hebrew_switch        0xFF7E  /* Alias for mode_switch */
#endif /* XK_HEBREW */

/*
 * Thai
 * Byte 3 = d
 */

#ifdef XK_THAI
0x0e01 #define XK_Thai_kokai                    0xda1
0x0e02 #define XK_Thai_khokhai                    0xda2
0x0e03 #define XK_Thai_khokhuat                0xda3
0x0e04 #define XK_Thai_khokhwai                0xda4
0x0e05 #define XK_Thai_khokhon                    0xda5
0x0e06 #define XK_Thai_khorakhang                    0xda6
0x0e07 #define XK_Thai_ngongu                    0xda7
0x0e08 #define XK_Thai_chochan                    0xda8
0x0e09 #define XK_Thai_choching                0xda9
0x0e0a #define XK_Thai_chochang                0xdaa
0x0e0b #define XK_Thai_soso                    0xdab
0x0e0c #define XK_Thai_chochoe                    0xdac
0x0e0d #define XK_Thai_yoying                    0xdad
0x0e0e #define XK_Thai_dochada                    0xdae
0x0e0f #define XK_Thai_topatak                    0xdaf
0x0e10 #define XK_Thai_thothan                    0xdb0
0x0e11 #define XK_Thai_thonangmontho                    0xdb1
0x0e12 #define XK_Thai_thophuthao                    0xdb2
0x0e13 #define XK_Thai_nonen                    0xdb3
0x0e14 #define XK_Thai_dodek                    0xdb4
0x0e15 #define XK_Thai_totao                    0xdb5
0x0e16 #define XK_Thai_thothung                0xdb6
0x0e17 #define XK_Thai_thothahan                0xdb7
0x0e18 #define XK_Thai_thothong                 0xdb8
0x0e19 #define XK_Thai_nonu                    0xdb9
0x0e1a #define XK_Thai_bobaimai                0xdba
0x0e1b #define XK_Thai_popla                    0xdbb
0x0e1c #define XK_Thai_phophung                0xdbc
0x0e1d #define XK_Thai_fofa                    0xdbd
0x0e1e #define XK_Thai_phophan                    0xdbe
0x0e1f #define XK_Thai_fofan                    0xdbf
0x0e20 #define XK_Thai_phosamphao                    0xdc0
0x0e21 #define XK_Thai_moma                    0xdc1
0x0e22 #define XK_Thai_yoyak                    0xdc2
0x0e23 #define XK_Thai_rorua                    0xdc3
0x0e24 #define XK_Thai_ru                    0xdc4
0x0e25 #define XK_Thai_loling                    0xdc5
0x0e26 #define XK_Thai_lu                    0xdc6
0x0e27 #define XK_Thai_wowaen                    0xdc7
0x0e28 #define XK_Thai_sosala                    0xdc8
0x0e29 #define XK_Thai_sorusi                    0xdc9
0x0e2a #define XK_Thai_sosua                    0xdca
0x0e2b #define XK_Thai_hohip                    0xdcb
0x0e2c #define XK_Thai_lochula                    0xdcc
0x0e2d #define XK_Thai_oang                    0xdcd
0x0e2e #define XK_Thai_honokhuk                0xdce
0x0e2f #define XK_Thai_paiyannoi                0xdcf
0x0e30 #define XK_Thai_saraa                    0xdd0
0x0e31 #define XK_Thai_maihanakat                0xdd1
0x0e32 #define XK_Thai_saraaa                    0xdd2
0x0e33 #define XK_Thai_saraam                    0xdd3
0x0e34 #define XK_Thai_sarai                    0xdd4
0x0e35 #define XK_Thai_saraii                    0xdd5
0x0e36 #define XK_Thai_saraue                    0xdd6
0x0e37 #define XK_Thai_sarauee                    0xdd7
0x0e38 #define XK_Thai_sarau                    0xdd8
0x0e39 #define XK_Thai_sarauu                    0xdd9
0x0e3a #define XK_Thai_phinthu                    0xdda
0x0000 #define XK_Thai_maihanakat_maitho               0xdde
0x0e3f #define XK_Thai_baht                    0xddf
0x0e40 #define XK_Thai_sarae                    0xde0
0x0e41 #define XK_Thai_saraae                    0xde1
0x0e42 #define XK_Thai_sarao                    0xde2
0x0e43 #define XK_Thai_saraaimaimuan                0xde3
0x0e44 #define XK_Thai_saraaimaimalai                0xde4
0x0e45 #define XK_Thai_lakkhangyao                0xde5
0x0e46 #define XK_Thai_maiyamok                0xde6
0x0e47 #define XK_Thai_maitaikhu                0xde7
0x0e48 #define XK_Thai_maiek                    0xde8
0x0e49 #define XK_Thai_maitho                    0xde9
0x0e4a #define XK_Thai_maitri                    0xdea
0x0e4b #define XK_Thai_maichattawa                0xdeb
0x0e4c #define XK_Thai_thanthakhat                0xdec
0x0e4d #define XK_Thai_nikhahit                0xded
0x0e50 #define XK_Thai_leksun                    0xdf0
0x0e51 #define XK_Thai_leknung                    0xdf1
0x0e52 #define XK_Thai_leksong                    0xdf2
0x0e53 #define XK_Thai_leksam                    0xdf3
0x0e54 #define XK_Thai_leksi                    0xdf4
0x0e55 #define XK_Thai_lekha                    0xdf5
0x0e56 #define XK_Thai_lekhok                    0xdf6
0x0e57 #define XK_Thai_lekchet                    0xdf7
0x0e58 #define XK_Thai_lekpaet                    0xdf8
0x0e59 #define XK_Thai_lekkao                    0xdf9
#endif /* XK_THAI */

/*
 *   Korean
 *   Byte 3 = e
 */

#ifdef XK_KOREAN

0x0000 #define XK_Hangul        0xff31    /* Hangul start/stop(toggle) */
0x0000 #define XK_Hangul_Start        0xff32    /* Hangul start */
0x0000 #define XK_Hangul_End        0xff33    /* Hangul end, English start */
0x0000 #define XK_Hangul_Hanja        0xff34    /* Start Hangul->Hanja Conversion */
0x0000 #define XK_Hangul_Jamo        0xff35    /* Hangul Jamo mode */
0x0000 #define XK_Hangul_Romaja    0xff36    /* Hangul Romaja mode */
0x0000 #define XK_Hangul_Codeinput    0xff37    /* Hangul code input mode */
0x0000 #define XK_Hangul_Jeonja    0xff38    /* Jeonja mode */
0x0000 #define XK_Hangul_Banja        0xff39    /* Banja mode */
0x0000 #define XK_Hangul_PreHanja    0xff3a    /* Pre Hanja conversion */
0x0000 #define XK_Hangul_PostHanja    0xff3b    /* Post Hanja conversion */
0x0000 #define XK_Hangul_SingleCandidate    0xff3c    /* Single candidate */
0x0000 #define XK_Hangul_MultipleCandidate    0xff3d    /* Multiple candidate */
0x0000 #define XK_Hangul_PreviousCandidate    0xff3e    /* Previous candidate */
0x0000 #define XK_Hangul_Special    0xff3f    /* Special symbols */
0x0000 #define XK_Hangul_switch    0xFF7E    /* Alias for mode_switch */

/* Hangul Consonant Characters */
0x3131 #define XK_Hangul_Kiyeog                0xea1
0x3132 #define XK_Hangul_SsangKiyeog                0xea2
0x3133 #define XK_Hangul_KiyeogSios                0xea3
0x3134 #define XK_Hangul_Nieun                    0xea4
0x3135 #define XK_Hangul_NieunJieuj                0xea5
0x3136 #define XK_Hangul_NieunHieuh                0xea6
0x3137 #define XK_Hangul_Dikeud                0xea7
0x3138 #define XK_Hangul_SsangDikeud                0xea8
0x3139 #define XK_Hangul_Rieul                    0xea9
0x313a #define XK_Hangul_RieulKiyeog                0xeaa
0x313b #define XK_Hangul_RieulMieum                0xeab
0x313c #define XK_Hangul_RieulPieub                0xeac
0x313d #define XK_Hangul_RieulSios                0xead
0x313e #define XK_Hangul_RieulTieut                0xeae
0x313f #define XK_Hangul_RieulPhieuf                0xeaf
0x3140 #define XK_Hangul_RieulHieuh                0xeb0
0x3141 #define XK_Hangul_Mieum                    0xeb1
0x3142 #define XK_Hangul_Pieub                    0xeb2
0x3143 #define XK_Hangul_SsangPieub                0xeb3
0x3144 #define XK_Hangul_PieubSios                0xeb4
0x3145 #define XK_Hangul_Sios                    0xeb5
0x3146 #define XK_Hangul_SsangSios                0xeb6
0x3147 #define XK_Hangul_Ieung                    0xeb7
0x3148 #define XK_Hangul_Jieuj                    0xeb8
0x3149 #define XK_Hangul_SsangJieuj                0xeb9
0x314a #define XK_Hangul_Cieuc                    0xeba
0x314b #define XK_Hangul_Khieuq                0xebb
0x314c #define XK_Hangul_Tieut                    0xebc
0x314d #define XK_Hangul_Phieuf                0xebd
0x314e #define XK_Hangul_Hieuh                    0xebe

 /* Hangul Vowel Characters */
0x314f #define XK_Hangul_A                    0xebf
0x3150 #define XK_Hangul_AE                    0xec0
0x3151 #define XK_Hangul_YA                    0xec1
0x3152 #define XK_Hangul_YAE                    0xec2
0x3153 #define XK_Hangul_EO                    0xec3
0x3154 #define XK_Hangul_E                    0xec4
0x3155 #define XK_Hangul_YEO                    0xec5
0x3156 #define XK_Hangul_YE                    0xec6
0x3157 #define XK_Hangul_O                    0xec7
0x3158 #define XK_Hangul_WA                    0xec8
0x3159 #define XK_Hangul_WAE                    0xec9
0x315a #define XK_Hangul_OE                    0xeca
0x315b #define XK_Hangul_YO                    0xecb
0x315c #define XK_Hangul_U                    0xecc
0x315d #define XK_Hangul_WEO                    0xecd
0x315e #define XK_Hangul_WE                    0xece
0x315f #define XK_Hangul_WI                    0xecf
0x3160 #define XK_Hangul_YU                    0xed0
0x3161 #define XK_Hangul_EU                    0xed1
0x3162 #define XK_Hangul_YI                    0xed2
0x3163 #define XK_Hangul_I                    0xed3

/* Hangul syllable-final (JongSeong) Characters */
0x11a8 #define XK_Hangul_J_Kiyeog                0xed4
0x11a9 #define XK_Hangul_J_SsangKiyeog                0xed5
0x11aa #define XK_Hangul_J_KiyeogSios                0xed6
0x11ab #define XK_Hangul_J_Nieun                0xed7
0x11ac #define XK_Hangul_J_NieunJieuj                0xed8
0x11ad #define XK_Hangul_J_NieunHieuh                0xed9
0x11ae #define XK_Hangul_J_Dikeud                0xeda
0x11af #define XK_Hangul_J_Rieul                0xedb
0x11b0 #define XK_Hangul_J_RieulKiyeog                0xedc
0x11b1 #define XK_Hangul_J_RieulMieum                0xedd
0x11b2 #define XK_Hangul_J_RieulPieub                0xede
0x11b3 #define XK_Hangul_J_RieulSios                0xedf
0x11b4 #define XK_Hangul_J_RieulTieut                0xee0
0x11b5 #define XK_Hangul_J_RieulPhieuf                0xee1
0x11b6 #define XK_Hangul_J_RieulHieuh                0xee2
0x11b7 #define XK_Hangul_J_Mieum                0xee3
0x11b8 #define XK_Hangul_J_Pieub                0xee4
0x11b9 #define XK_Hangul_J_PieubSios                0xee5
0x11ba #define XK_Hangul_J_Sios                0xee6
0x11bb #define XK_Hangul_J_SsangSios                0xee7
0x11bc #define XK_Hangul_J_Ieung                0xee8
0x11bd #define XK_Hangul_J_Jieuj                0xee9
0x11be #define XK_Hangul_J_Cieuc                0xeea
0x11bf #define XK_Hangul_J_Khieuq                0xeeb
0x11c0 #define XK_Hangul_J_Tieut                0xeec
0x11c1 #define XK_Hangul_J_Phieuf                0xeed
0x11c2 #define XK_Hangul_J_Hieuh                0xeee

/* Ancient Hangul Consonant Characters */
0x316d #define XK_Hangul_RieulYeorinHieuh            0xeef
0x3171 #define XK_Hangul_SunkyeongeumMieum            0xef0
0x3178 #define XK_Hangul_SunkyeongeumPieub            0xef1
0x317f #define XK_Hangul_PanSios                0xef2
0x3181 #define XK_Hangul_KkogjiDalrinIeung            0xef3
0x3184 #define XK_Hangul_SunkyeongeumPhieuf            0xef4
0x3186 #define XK_Hangul_YeorinHieuh                0xef5

/* Ancient Hangul Vowel Characters */
0x318d #define XK_Hangul_AraeA                    0xef6
0x318e #define XK_Hangul_AraeAE                0xef7

/* Ancient Hangul syllable-final (JongSeong) Characters */
0x11eb #define XK_Hangul_J_PanSios                0xef8
0x11f0 #define XK_Hangul_J_KkogjiDalrinIeung            0xef9
0x11f9 #define XK_Hangul_J_YeorinHieuh                0xefa

/* Korean currency symbol */
0x20a9 #define XK_Korean_Won                    0xeff

#endif /* XK_KOREAN */

/*
 *   Armenian
 *   Byte 3 = 0x14
 */
// yan: skip Armenian for the time being
#ifdef XK_ARMENIAN
0x0000 #define XK_Armenian_eternity                0x14a1
0x0000 #define XK_Armenian_ligature_ew                0x14a2
0x0000 #define XK_Armenian_full_stop                0x14a3
0x0000 #define XK_Armenian_verjaket                0x14a3
0x0000 #define XK_Armenian_parenright                0x14a4
0x0000 #define XK_Armenian_parenleft                0x14a5
0x0000 #define XK_Armenian_guillemotright            0x14a6
0x0000 #define XK_Armenian_guillemotleft            0x14a7
0x0000 #define XK_Armenian_em_dash                0x14a8
0x0000 #define XK_Armenian_dot                    0x14a9
0x0000 #define XK_Armenian_mijaket                0x14a9
0x0000 #define XK_Armenian_separation_mark            0x14aa
0x0000 #define XK_Armenian_but                    0x14aa
0x0000 #define XK_Armenian_comma                0x14ab
0x0000 #define XK_Armenian_en_dash                0x14ac
0x0000 #define XK_Armenian_hyphen                0x14ad
0x0000 #define XK_Armenian_yentamna                0x14ad
0x0000 #define XK_Armenian_ellipsis                0x14ae
0x0000 #define XK_Armenian_exclam                0x14af
0x0000 #define XK_Armenian_amanak                0x14af
0x0000 #define XK_Armenian_accent                0x14b0
0x0000 #define XK_Armenian_shesht                0x14b0
0x0000 #define XK_Armenian_question                0x14b1
0x0000 #define XK_Armenian_paruyk                0x14b1
0x0000 #define XK_Armenian_AYB                    0x14b2
0x0000 #define XK_Armenian_ayb                    0x14b3
0x0000 #define XK_Armenian_BEN                    0x14b4
0x0000 #define XK_Armenian_ben                    0x14b5
0x0000 #define XK_Armenian_GIM                    0x14b6
0x0000 #define XK_Armenian_gim                    0x14b7
0x0000 #define XK_Armenian_DA                    0x14b8
0x0000 #define XK_Armenian_da                    0x14b9
0x0000 #define XK_Armenian_YECH                0x14ba
0x0000 #define XK_Armenian_yech                0x14bb
0x0000 #define XK_Armenian_ZA                    0x14bc
0x0000 #define XK_Armenian_za                    0x14bd
0x0000 #define XK_Armenian_E                    0x14be
0x0000 #define XK_Armenian_e                    0x14bf
0x0000 #define XK_Armenian_AT                    0x14c0
0x0000 #define XK_Armenian_at                    0x14c1
0x0000 #define XK_Armenian_TO                    0x14c2
0x0000 #define XK_Armenian_to                    0x14c3
0x0000 #define XK_Armenian_ZHE                    0x14c4
0x0000 #define XK_Armenian_zhe                    0x14c5
0x0000 #define XK_Armenian_INI                    0x14c6
0x0000 #define XK_Armenian_ini                    0x14c7
0x0000 #define XK_Armenian_LYUN                0x14c8
0x0000 #define XK_Armenian_lyun                0x14c9
0x0000 #define XK_Armenian_KHE                    0x14ca
0x0000 #define XK_Armenian_khe                    0x14cb
0x0000 #define XK_Armenian_TSA                    0x14cc
0x0000 #define XK_Armenian_tsa                    0x14cd
0x0000 #define XK_Armenian_KEN                    0x14ce
0x0000 #define XK_Armenian_ken                    0x14cf
0x0000 #define XK_Armenian_HO                    0x14d0
0x0000 #define XK_Armenian_ho                    0x14d1
0x0000 #define XK_Armenian_DZA                    0x14d2
0x0000 #define XK_Armenian_dza                    0x14d3
0x0000 #define XK_Armenian_GHAT                0x14d4
0x0000 #define XK_Armenian_ghat                0x14d5
0x0000 #define XK_Armenian_TCHE                0x14d6
0x0000 #define XK_Armenian_tche                0x14d7
0x0000 #define XK_Armenian_MEN                    0x14d8
0x0000 #define XK_Armenian_men                    0x14d9
0x0000 #define XK_Armenian_HI                    0x14da
0x0000 #define XK_Armenian_hi                    0x14db
0x0000 #define XK_Armenian_NU                    0x14dc
0x0000 #define XK_Armenian_nu                    0x14dd
0x0000 #define XK_Armenian_SHA                    0x14de
0x0000 #define XK_Armenian_sha                    0x14df
0x0000 #define XK_Armenian_VO                    0x14e0
0x0000 #define XK_Armenian_vo                    0x14e1
0x0000 #define XK_Armenian_CHA                    0x14e2
0x0000 #define XK_Armenian_cha                    0x14e3
0x0000 #define XK_Armenian_PE                    0x14e4
0x0000 #define XK_Armenian_pe                    0x14e5
0x0000 #define XK_Armenian_JE                    0x14e6
0x0000 #define XK_Armenian_je                    0x14e7
0x0000 #define XK_Armenian_RA                    0x14e8
0x0000 #define XK_Armenian_ra                    0x14e9
0x0000 #define XK_Armenian_SE                    0x14ea
0x0000 #define XK_Armenian_se                    0x14eb
0x0000 #define XK_Armenian_VEV                    0x14ec
0x0000 #define XK_Armenian_vev                    0x14ed
0x0000 #define XK_Armenian_TYUN                0x14ee
0x0000 #define XK_Armenian_tyun                0x14ef
0x0000 #define XK_Armenian_RE                    0x14f0
0x0000 #define XK_Armenian_re                    0x14f1
0x0000 #define XK_Armenian_TSO                    0x14f2
0x0000 #define XK_Armenian_tso                    0x14f3
0x0000 #define XK_Armenian_VYUN                0x14f4
0x0000 #define XK_Armenian_vyun                0x14f5
0x0000 #define XK_Armenian_PYUR                0x14f6
0x0000 #define XK_Armenian_pyur                0x14f7
0x0000 #define XK_Armenian_KE                    0x14f8
0x0000 #define XK_Armenian_ke                    0x14f9
0x0000 #define XK_Armenian_O                    0x14fa
0x0000 #define XK_Armenian_o                    0x14fb
0x0000 #define XK_Armenian_FE                    0x14fc
0x0000 #define XK_Armenian_fe                    0x14fd
0x0000 #define XK_Armenian_apostrophe                0x14fe
0x0000 #define XK_Armenian_section_sign            0x14ff
#endif /* XK_ARMENIAN */

/*
 *   Georgian
 *   Byte 3 = 0x15
 */
//yan: skip Georgian for now;
#ifdef XK_GEORGIAN
0x0000 #define XK_Georgian_an                    0x15d0
0x0000 #define XK_Georgian_ban                    0x15d1
0x0000 #define XK_Georgian_gan                    0x15d2
0x0000 #define XK_Georgian_don                    0x15d3
0x0000 #define XK_Georgian_en                    0x15d4
0x0000 #define XK_Georgian_vin                    0x15d5
0x0000 #define XK_Georgian_zen                    0x15d6
0x0000 #define XK_Georgian_tan                    0x15d7
0x0000 #define XK_Georgian_in                    0x15d8
0x0000 #define XK_Georgian_kan                    0x15d9
0x0000 #define XK_Georgian_las                    0x15da
0x0000 #define XK_Georgian_man                    0x15db
0x0000 #define XK_Georgian_nar                    0x15dc
0x0000 #define XK_Georgian_on                    0x15dd
0x0000 #define XK_Georgian_par                    0x15de
0x0000 #define XK_Georgian_zhar                0x15df
0x0000 #define XK_Georgian_rae                    0x15e0
0x0000 #define XK_Georgian_san                    0x15e1
0x0000 #define XK_Georgian_tar                    0x15e2
0x0000 #define XK_Georgian_un                    0x15e3
0x0000 #define XK_Georgian_phar                0x15e4
0x0000 #define XK_Georgian_khar                0x15e5
0x0000 #define XK_Georgian_ghan                0x15e6
0x0000 #define XK_Georgian_qar                    0x15e7
0x0000 #define XK_Georgian_shin                0x15e8
0x0000 #define XK_Georgian_chin                0x15e9
0x0000 #define XK_Georgian_can                    0x15ea
0x0000 #define XK_Georgian_jil                    0x15eb
0x0000 #define XK_Georgian_cil                    0x15ec
0x0000 #define XK_Georgian_char                0x15ed
0x0000 #define XK_Georgian_xan                    0x15ee
0x0000 #define XK_Georgian_jhan                0x15ef
0x0000 #define XK_Georgian_hae                    0x15f0
0x0000 #define XK_Georgian_he                    0x15f1
0x0000 #define XK_Georgian_hie                    0x15f2
0x0000 #define XK_Georgian_we                    0x15f3
0x0000 #define XK_Georgian_har                    0x15f4
0x0000 #define XK_Georgian_hoe                    0x15f5
0x0000 #define XK_Georgian_fi                    0x15f6
#endif /* XK_GEORGIAN */

/*
 * Azeri (and other Turkic or Caucasian languages of ex-USSR)
 * Byte 3 = 0x16
 */

#ifdef XK_CAUCASUS
/* latin */
0x0000 #define XK_Ccedillaabovedot    0x16a2
0x1e8a #define XK_Xabovedot        0x16a3
0x0000 #define XK_Qabovedot        0x16a5
0x012c #define    XK_Ibreve        0x16a6
0x0000 #define XK_IE            0x16a7
0x0000 #define XK_UO            0x16a8
0x01b5 #define XK_Zstroke        0x16a9
0x01e6 #define    XK_Gcaron        0x16aa
0x019f #define    XK_Obarred        0x16af
0x0000 #define XK_ccedillaabovedot    0x16b2
0x1e8b #define XK_xabovedot        0x16b3
0x0000 #define    XK_Ocaron        0x16b4
0x0000 #define XK_qabovedot        0x16b5
0x012d #define    XK_ibreve        0x16b6
0x0000 #define XK_ie            0x16b7
0x0000 #define XK_uo            0x16b8
0x01b6 #define XK_zstroke        0x16b9
0x01e7 #define    XK_gcaron        0x16ba
0x01d2 #define    XK_ocaron        0x16bd
0x0275 #define    XK_obarred        0x16bf
0x018f #define XK_SCHWA        0x16c6
0x0259 #define XK_schwa        0x16f6
/* those are not really Caucasus, but I put them here for now */
/* For Inupiak */
// yan: is there unicode for Inupiak or Guarani at all?
0x0000 #define XK_Lbelowdot        0x16d1
0x0000 #define XK_Lstrokebelowdot    0x16d2
0x0000 #define XK_lbelowdot        0x16e1
0x0000 #define XK_lstrokebelowdot    0x16e2
/* For Guarani */
0x0000 #define XK_Gtilde        0x16d3
0x0000 #define XK_gtilde        0x16e3
#endif /* XK_CAUCASUS */

/*
 *   Vietnamese
 *   Byte 3 = 0x1e
 */

#ifdef XK_VIETNAMESE
0x1ea0 #define XK_Abelowdot                    0x1ea0
0x1ea1 #define XK_abelowdot                    0x1ea1
0x1ea2 #define XK_Ahook                    0x1ea2
0x1ea3 #define XK_ahook                    0x1ea3
0x1ea4 #define XK_Acircumflexacute                0x1ea4
0x1ea5 #define XK_acircumflexacute                0x1ea5
0x1ea6 #define XK_Acircumflexgrave                0x1ea6
0x1ea7 #define XK_acircumflexgrave                0x1ea7
0x1ea8 #define XK_Acircumflexhook                0x1ea8
0x1ea9 #define XK_acircumflexhook                0x1ea9
0x1eaa #define XK_Acircumflextilde                0x1eaa
0x1eab #define XK_acircumflextilde                0x1eab
0x1eac #define XK_Acircumflexbelowdot                0x1eac
0x1ead #define XK_acircumflexbelowdot                0x1ead
0x1eae #define XK_Abreveacute                    0x1eae
0x1eaf #define XK_abreveacute                    0x1eaf
0x1eb0 #define XK_Abrevegrave                    0x1eb0
0x1eb1 #define XK_abrevegrave                    0x1eb1
0x1eb2 #define XK_Abrevehook                    0x1eb2
0x1eb3 #define XK_abrevehook                    0x1eb3
0x1eb4 #define XK_Abrevetilde                    0x1eb4
0x1eb5 #define XK_abrevetilde                    0x1eb5
0x1eb6 #define XK_Abrevebelowdot                0x1eb6
0x1eb7 #define XK_abrevebelowdot                0x1eb7
0x1eb8 #define XK_Ebelowdot                    0x1eb8
0x1eb9 #define XK_ebelowdot                    0x1eb9
0x1eba #define XK_Ehook                    0x1eba
0x1ebb #define XK_ehook                    0x1ebb
0x1ebc #define XK_Etilde                    0x1ebc
0x1ebd #define XK_etilde                    0x1ebd
0x1ebe #define XK_Ecircumflexacute                0x1ebe
0x1ebf #define XK_ecircumflexacute                0x1ebf
0x1ec0 #define XK_Ecircumflexgrave                0x1ec0
0x1ec1 #define XK_ecircumflexgrave                0x1ec1
0x1ec2 #define XK_Ecircumflexhook                0x1ec2
0x1ec3 #define XK_ecircumflexhook                0x1ec3
0x1ec4 #define XK_Ecircumflextilde                0x1ec4
0x1ec5 #define XK_ecircumflextilde                0x1ec5
0x1ec6 #define XK_Ecircumflexbelowdot                0x1ec6
0x1ec7 #define XK_ecircumflexbelowdot                0x1ec7
0x1ec8 #define XK_Ihook                    0x1ec8
0x1ec9 #define XK_ihook                    0x1ec9
0x1eca #define XK_Ibelowdot                    0x1eca
0x1ecb #define XK_ibelowdot                    0x1ecb
0x1ecc #define XK_Obelowdot                    0x1ecc
0x1ecd #define XK_obelowdot                    0x1ecd
0x1ece #define XK_Ohook                    0x1ece
0x1ecf #define XK_ohook                    0x1ecf
0x1ed0 #define XK_Ocircumflexacute                0x1ed0
0x1ed1 #define XK_ocircumflexacute                0x1ed1
0x1ed2 #define XK_Ocircumflexgrave                0x1ed2
0x1ed3 #define XK_ocircumflexgrave                0x1ed3
0x1ed4 #define XK_Ocircumflexhook                0x1ed4
0x1ed5 #define XK_ocircumflexhook                0x1ed5
0x1ed6 #define XK_Ocircumflextilde                0x1ed6
0x1ed7 #define XK_ocircumflextilde                0x1ed7
0x1ed8 #define XK_Ocircumflexbelowdot                0x1ed8
0x1ed9 #define XK_ocircumflexbelowdot                0x1ed9
0x1eda #define XK_Ohornacute                    0x1eda
0x1edb #define XK_ohornacute                    0x1edb
0x1edc #define XK_Ohorngrave                    0x1edc
0x1edd #define XK_ohorngrave                    0x1edd
0x1ede #define XK_Ohornhook                    0x1ede
0x1edf #define XK_ohornhook                    0x1edf
0x1ee0 #define XK_Ohorntilde                    0x1ee0
0x1ee1 #define XK_ohorntilde                    0x1ee1
0x1ee2 #define XK_Ohornbelowdot                0x1ee2
0x1ee3 #define XK_ohornbelowdot                0x1ee3
0x1ee4 #define XK_Ubelowdot                    0x1ee4
0x1ee5 #define XK_ubelowdot                    0x1ee5
0x1ee6 #define XK_Uhook                    0x1ee6
0x1ee7 #define XK_uhook                    0x1ee7
0x1ee8 #define XK_Uhornacute                    0x1ee8
0x1ee9 #define XK_uhornacute                    0x1ee9
0x1eea #define XK_Uhorngrave                    0x1eea
0x1eeb #define XK_uhorngrave                    0x1eeb
0x1eec #define XK_Uhornhook                    0x1eec
0x1eed #define XK_uhornhook                    0x1eed
0x1eee #define XK_Uhorntilde                    0x1eee
0x1eef #define XK_uhorntilde                    0x1eef
0x1ef0 #define XK_Uhornbelowdot                0x1ef0
0x1ef1 #define XK_uhornbelowdot                0x1ef1
0x1ef4 #define XK_Ybelowdot                    0x1ef4
0x1ef5 #define XK_ybelowdot                    0x1ef5
0x1ef6 #define XK_Yhook                    0x1ef6
0x1ef7 #define XK_yhook                    0x1ef7
0x1ef8 #define XK_Ytilde                    0x1ef8
0x1ef9 #define XK_ytilde                    0x1ef9
0x01a0 #define XK_Ohorn                    0x1efa /* U+01a0 */
0x01a1 #define XK_ohorn                    0x1efb /* U+01a1 */
0x01af #define XK_Uhorn                    0x1efc /* U+01af */
0x01b0 #define XK_uhorn                    0x1efd /* U+01b0 */

0x0000 #define XK_combining_tilde                0x1e9f /* U+0303 */
0x0000 #define XK_combining_grave                0x1ef2 /* U+0300 */
0x0000 #define XK_combining_acute                0x1ef3 /* U+0301 */
0x0000 #define XK_combining_hook                0x1efe /* U+0309 */
0x0000 #define XK_combining_belowdot                0x1eff /* U+0323 */
#endif /* XK_VIETNAMESE */

#ifdef XK_CURRENCY
0x20a0 #define XK_EcuSign                    0x20a0
0x20a1 #define XK_ColonSign                    0x20a1
0x20a2 #define XK_CruzeiroSign                    0x20a2
0x20a3 #define XK_FFrancSign                    0x20a3
0x20a4 #define XK_LiraSign                    0x20a4
0x20a5 #define XK_MillSign                    0x20a5
0x20a6 #define XK_NairaSign                    0x20a6
0x20a7 #define XK_PesetaSign                    0x20a7
0x20a8 #define XK_RupeeSign                    0x20a8
0x20a9 #define XK_WonSign                    0x20a9
0x20aa #define XK_NewSheqelSign                0x20aa
0x20ab #define XK_DongSign                    0x20ab
0x20ac #define XK_EuroSign                    0x20ac
#endif

//yan: keysyms from vendor headers go here. I don't know  many though.

0x0008  #define  osfXK_BackSpace 0x1004FF08
0x001b  #define  osfXK_Escape   0x1004FF1B
//XXX ? Esc on Solaris?, to check
0x0000  #define  osfXK_Cancel   0x1004FF69
0x007f  #define  osfXK_Delete   0x1004FFFF

tojava
tojava         //XXX fill keysym2JavaKeycodeHash.
tojava
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_a),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_A, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_b),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_B, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_c),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_C, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_d),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_D, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_e),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_E, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_f),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_F, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_g),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_G, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_h),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_H, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_i),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_I, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_j),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_J, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_k),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_K, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_l),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_L, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_m),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_M, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_n),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_N, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_o),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_O, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_p),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_P, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_q),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_Q, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_r),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_R, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_s),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_S, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_t),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_T, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_u),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_U, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_v),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_V, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_w),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_W, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_x),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_X, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_y),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_Y, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_z),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_Z, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava
tojava             /* TTY Function keys */
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_BackSpace),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_BACK_SPACE, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_Tab),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_TAB, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_ISO_Left_Tab),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_TAB, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_Clear),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_CLEAR, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_Return),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_ENTER, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_Linefeed),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_ENTER, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_Pause),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_PAUSE, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_F21),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_PAUSE, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_R1),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_PAUSE, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_Scroll_Lock),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_SCROLL_LOCK, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_F23),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_SCROLL_LOCK, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_R3),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_SCROLL_LOCK, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_Escape),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_ESCAPE, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava
tojava             /* Other vendor-specific versions of TTY Function keys */
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.osfXK_BackSpace),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_BACK_SPACE, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.osfXK_Clear),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_CLEAR, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.osfXK_Escape),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_ESCAPE, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava
tojava             /* Modifier keys */
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_Shift_L),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_SHIFT, java.awt.event.KeyEvent.KEY_LOCATION_LEFT));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_Shift_R),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_SHIFT, java.awt.event.KeyEvent.KEY_LOCATION_RIGHT));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_Control_L),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_CONTROL, java.awt.event.KeyEvent.KEY_LOCATION_LEFT));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_Control_R),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_CONTROL, java.awt.event.KeyEvent.KEY_LOCATION_RIGHT));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_Alt_L),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_ALT, java.awt.event.KeyEvent.KEY_LOCATION_LEFT));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_Alt_R),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_ALT, java.awt.event.KeyEvent.KEY_LOCATION_RIGHT));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_Meta_L),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_META, java.awt.event.KeyEvent.KEY_LOCATION_LEFT));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_Meta_R),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_META, java.awt.event.KeyEvent.KEY_LOCATION_RIGHT));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_Caps_Lock),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_CAPS_LOCK, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava
tojava             /* Misc Functions */
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_Print),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_PRINTSCREEN, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_F22),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_PRINTSCREEN, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_R2),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_PRINTSCREEN, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_Cancel),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_CANCEL, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_Help),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_HELP, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_Num_Lock),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_NUM_LOCK, java.awt.event.KeyEvent.KEY_LOCATION_NUMPAD));
tojava
tojava             /* Other vendor-specific versions of Misc Functions */
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.osfXK_Cancel),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_CANCEL, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.osfXK_Help),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_HELP, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava
tojava             /* Rectangular Navigation Block */
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_Home),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_HOME, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_R7),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_HOME, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_Page_Up),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_PAGE_UP, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_Prior),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_PAGE_UP, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_R9),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_PAGE_UP, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_Page_Down),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_PAGE_DOWN, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_Next),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_PAGE_DOWN, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_R15),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_PAGE_DOWN, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_End),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_END, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_R13),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_END, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_Insert),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_INSERT, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_Delete),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_DELETE, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava
tojava             /* Keypad equivalents of Rectangular Navigation Block */
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_KP_Home),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_HOME, java.awt.event.KeyEvent.KEY_LOCATION_NUMPAD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_KP_Page_Up),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_PAGE_UP, java.awt.event.KeyEvent.KEY_LOCATION_NUMPAD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_KP_Prior),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_PAGE_UP, java.awt.event.KeyEvent.KEY_LOCATION_NUMPAD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_KP_Page_Down),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_PAGE_DOWN, java.awt.event.KeyEvent.KEY_LOCATION_NUMPAD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_KP_Next),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_PAGE_DOWN, java.awt.event.KeyEvent.KEY_LOCATION_NUMPAD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_KP_End),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_END, java.awt.event.KeyEvent.KEY_LOCATION_NUMPAD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_KP_Insert),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_INSERT, java.awt.event.KeyEvent.KEY_LOCATION_NUMPAD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_KP_Delete),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_DELETE, java.awt.event.KeyEvent.KEY_LOCATION_NUMPAD));
tojava
tojava             /* Other vendor-specific Rectangular Navigation Block */
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.osfXK_PageUp),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_PAGE_UP, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.osfXK_Prior),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_PAGE_UP, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.osfXK_PageDown),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_PAGE_DOWN, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.osfXK_Next),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_PAGE_DOWN, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.osfXK_EndLine),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_END, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.osfXK_Insert),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_INSERT, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.osfXK_Delete),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_DELETE, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava
tojava             /* Triangular Navigation Block */
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_Left),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_LEFT, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_Up),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_UP, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_Right),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_RIGHT, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_Down),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_DOWN, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava
tojava             /* Keypad equivalents of Triangular Navigation Block */
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_KP_Left),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_KP_LEFT, java.awt.event.KeyEvent.KEY_LOCATION_NUMPAD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_KP_Up),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_KP_UP, java.awt.event.KeyEvent.KEY_LOCATION_NUMPAD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_KP_Right),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_KP_RIGHT, java.awt.event.KeyEvent.KEY_LOCATION_NUMPAD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_KP_Down),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_KP_DOWN, java.awt.event.KeyEvent.KEY_LOCATION_NUMPAD));
tojava
tojava             /* Other vendor-specific Triangular Navigation Block */
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.osfXK_Left),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_LEFT, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.osfXK_Up),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_UP, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.osfXK_Right),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_RIGHT, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.osfXK_Down),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_DOWN, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava
tojava             /* Remaining Cursor control & motion */
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_Begin),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_BEGIN, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_KP_Begin),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_BEGIN, java.awt.event.KeyEvent.KEY_LOCATION_NUMPAD));
tojava
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_0),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_0, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_1),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_1, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_2),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_2, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_3),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_3, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_4),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_4, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_5),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_5, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_6),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_6, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_7),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_7, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_8),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_8, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_9),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_9, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_space),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_SPACE, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_exclam),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_EXCLAMATION_MARK, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_quotedbl),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_QUOTEDBL, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_numbersign),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_NUMBER_SIGN, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_dollar),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_DOLLAR, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_ampersand),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_AMPERSAND, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_apostrophe),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_QUOTE, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_parenleft),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_LEFT_PARENTHESIS, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_parenright),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_RIGHT_PARENTHESIS, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_asterisk),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_ASTERISK, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_plus),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_PLUS, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_comma),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_COMMA, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_minus),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_MINUS, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_period),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_PERIOD, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_slash),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_SLASH, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_colon),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_COLON, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_semicolon),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_SEMICOLON, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_less),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_LESS, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_equal),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_EQUALS, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_greater),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_GREATER, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_at),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_AT, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_bracketleft),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_OPEN_BRACKET, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_backslash),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_BACK_SLASH, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_bracketright),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_CLOSE_BRACKET, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_asciicircum),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_CIRCUMFLEX, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_underscore),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_UNDERSCORE, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_Super_L),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_WINDOWS, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_Super_R),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_WINDOWS, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_Menu),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_CONTEXT_MENU, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_grave),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_BACK_QUOTE, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_braceleft),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_BRACELEFT, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_braceright),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_BRACERIGHT, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_exclamdown),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_INVERTED_EXCLAMATION_MARK, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava
tojava             /* Remaining Numeric Keypad Keys */
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_KP_0),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_NUMPAD0, java.awt.event.KeyEvent.KEY_LOCATION_NUMPAD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_KP_1),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_NUMPAD1, java.awt.event.KeyEvent.KEY_LOCATION_NUMPAD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_KP_2),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_NUMPAD2, java.awt.event.KeyEvent.KEY_LOCATION_NUMPAD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_KP_3),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_NUMPAD3, java.awt.event.KeyEvent.KEY_LOCATION_NUMPAD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_KP_4),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_NUMPAD4, java.awt.event.KeyEvent.KEY_LOCATION_NUMPAD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_KP_5),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_NUMPAD5, java.awt.event.KeyEvent.KEY_LOCATION_NUMPAD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_KP_6),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_NUMPAD6, java.awt.event.KeyEvent.KEY_LOCATION_NUMPAD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_KP_7),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_NUMPAD7, java.awt.event.KeyEvent.KEY_LOCATION_NUMPAD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_KP_8),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_NUMPAD8, java.awt.event.KeyEvent.KEY_LOCATION_NUMPAD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_KP_9),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_NUMPAD9, java.awt.event.KeyEvent.KEY_LOCATION_NUMPAD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_KP_Space),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_SPACE, java.awt.event.KeyEvent.KEY_LOCATION_NUMPAD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_KP_Tab),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_TAB, java.awt.event.KeyEvent.KEY_LOCATION_NUMPAD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_KP_Enter),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_ENTER, java.awt.event.KeyEvent.KEY_LOCATION_NUMPAD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_KP_Equal),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_EQUALS, java.awt.event.KeyEvent.KEY_LOCATION_NUMPAD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_R4),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_EQUALS, java.awt.event.KeyEvent.KEY_LOCATION_NUMPAD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_KP_Multiply),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_MULTIPLY, java.awt.event.KeyEvent.KEY_LOCATION_NUMPAD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_F26),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_MULTIPLY, java.awt.event.KeyEvent.KEY_LOCATION_NUMPAD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_R6),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_MULTIPLY, java.awt.event.KeyEvent.KEY_LOCATION_NUMPAD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_KP_Add),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_ADD, java.awt.event.KeyEvent.KEY_LOCATION_NUMPAD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_KP_Separator),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_SEPARATOR, java.awt.event.KeyEvent.KEY_LOCATION_NUMPAD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_KP_Subtract),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_SUBTRACT, java.awt.event.KeyEvent.KEY_LOCATION_NUMPAD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_F24),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_SUBTRACT, java.awt.event.KeyEvent.KEY_LOCATION_NUMPAD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_KP_Decimal),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_DECIMAL, java.awt.event.KeyEvent.KEY_LOCATION_NUMPAD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_KP_Divide),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_DIVIDE, java.awt.event.KeyEvent.KEY_LOCATION_NUMPAD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_F25),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_DIVIDE, java.awt.event.KeyEvent.KEY_LOCATION_NUMPAD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_R5),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_DIVIDE, java.awt.event.KeyEvent.KEY_LOCATION_NUMPAD));
tojava
tojava             /* Function Keys */
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_F1),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_F1, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_F2),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_F2, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_F3),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_F3, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_F4),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_F4, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_F5),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_F5, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_F6),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_F6, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_F7),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_F7, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_F8),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_F8, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_F9),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_F9, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_F10),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_F10, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_F11),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_F11, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_F12),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_F12, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava
tojava             /* Sun vendor-specific version of F11 and F12 */
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.SunXK_F36),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_F11, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.SunXK_F37),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_F12, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava
tojava             /* X11 keysym names for input method related keys don't always
tojava              * match keytop engravings or Java virtual key names, so here we
tojava              * only map constants that we've found on real keyboards.
tojava              */
tojava             /* Type 5c Japanese keyboard: kakutei */
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_Execute),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_ACCEPT, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava             /* Type 5c Japanese keyboard: henkan */
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_Kanji),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_CONVERT, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava             /* Type 5c Japanese keyboard: nihongo */
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_Henkan_Mode),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_INPUT_METHOD_ON_OFF, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava             /* VK_KANA_LOCK is handled separately because it generates the
tojava              * same keysym as ALT_GRAPH in spite of its different behavior.
tojava              */
tojava
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_Multi_key),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_COMPOSE, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_Mode_switch),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_ALT_GRAPH, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_ISO_Level3_Shift),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_ALT_GRAPH, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava
tojava             /* Editing block */
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_Redo),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_AGAIN, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         // XXX XK_L2 == F12; TODO: add code to use only one of them depending on the keyboard type. For now, restore
tojava         // good PC behavior and bad but old Sparc behavior.
tojava         // keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_L2),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_AGAIN, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_Undo),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_UNDO, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_L4),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_UNDO, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_L6),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_COPY, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_L8),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_PASTE, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_L10),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_CUT, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_Find),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_FIND, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_L9),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_FIND, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_L3),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_PROPS, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         // XXX XK_L1 == F11; TODO: add code to use only one of them depending on the keyboard type. For now, restore
tojava         // good PC behavior and bad but old Sparc behavior.
tojava         // keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_L1),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_STOP, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava
tojava             /* Sun vendor-specific versions for editing block */
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.SunXK_Again),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_AGAIN, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.SunXK_Undo),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_UNDO, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.SunXK_Copy),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_COPY, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.SunXK_Paste),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_PASTE, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.SunXK_Cut),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_CUT, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.SunXK_Find),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_FIND, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.SunXK_Props),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_PROPS, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.SunXK_Stop),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_STOP, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava
tojava             /* Apollo (HP) vendor-specific versions for editing block */
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.apXK_Copy),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_COPY, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.apXK_Cut),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_CUT, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.apXK_Paste),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_PASTE, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava
tojava             /* Other vendor-specific versions for editing block */
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.osfXK_Copy),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_COPY, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.osfXK_Cut),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_CUT, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.osfXK_Paste),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_PASTE, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.osfXK_Undo),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_UNDO, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava
tojava             /* Dead key mappings (for European keyboards) */
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_dead_grave),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_DEAD_GRAVE, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_dead_acute),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_DEAD_ACUTE, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_dead_circumflex),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_DEAD_CIRCUMFLEX, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_dead_tilde),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_DEAD_TILDE, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_dead_macron),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_DEAD_MACRON, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_dead_breve),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_DEAD_BREVE, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_dead_abovedot),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_DEAD_ABOVEDOT, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_dead_diaeresis),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_DEAD_DIAERESIS, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_dead_abovering),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_DEAD_ABOVERING, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_dead_doubleacute),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_DEAD_DOUBLEACUTE, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_dead_caron),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_DEAD_CARON, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_dead_cedilla),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_DEAD_CEDILLA, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_dead_ogonek),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_DEAD_OGONEK, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_dead_iota),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_DEAD_IOTA, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_dead_voiced_sound),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_DEAD_VOICED_SOUND, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.XK_dead_semivoiced_sound),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_DEAD_SEMIVOICED_SOUND, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava
tojava             /* Sun vendor-specific dead key mappings (for European keyboards) */
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.SunXK_FA_Grave),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_DEAD_GRAVE, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.SunXK_FA_Circum),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_DEAD_CIRCUMFLEX, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.SunXK_FA_Tilde),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_DEAD_TILDE, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.SunXK_FA_Acute),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_DEAD_ACUTE, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.SunXK_FA_Diaeresis),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_DEAD_DIAERESIS, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.SunXK_FA_Cedilla),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_DEAD_CEDILLA, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava
tojava             /* DEC vendor-specific dead key mappings (for European keyboards) */
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.DXK_ring_accent),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_DEAD_ABOVERING, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.DXK_circumflex_accent),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_DEAD_CIRCUMFLEX, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.DXK_cedilla_accent),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_DEAD_CEDILLA, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.DXK_acute_accent),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_DEAD_ACUTE, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.DXK_grave_accent),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_DEAD_GRAVE, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.DXK_tilde),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_DEAD_TILDE, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.DXK_diaeresis),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_DEAD_DIAERESIS, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava
tojava             /* Other vendor-specific dead key mappings (for European keyboards) */
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.hpXK_mute_acute),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_DEAD_ACUTE, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.hpXK_mute_grave),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_DEAD_GRAVE, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.hpXK_mute_asciicircum),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_DEAD_CIRCUMFLEX, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.hpXK_mute_diaeresis),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_DEAD_DIAERESIS, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XKeySymConstants.hpXK_mute_asciitilde),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_DEAD_TILDE, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
tojava
tojava         keysym2JavaKeycodeHash.put( Long.valueOf(XConstants.NoSymbol),     new Keysym2JavaKeycode(java.awt.event.KeyEvent.VK_UNDEFINED, java.awt.event.KeyEvent.KEY_LOCATION_UNKNOWN));
tojava
tojava         /* Reverse search of keysym by keycode. */
tojava
tojava         /* Add keyboard locking codes. */
tojava         javaKeycode2KeysymHash.put( java.awt.event.KeyEvent.VK_CAPS_LOCK, XKeySymConstants.XK_Caps_Lock);
tojava         javaKeycode2KeysymHash.put( java.awt.event.KeyEvent.VK_NUM_LOCK, XKeySymConstants.XK_Num_Lock);
tojava         javaKeycode2KeysymHash.put( java.awt.event.KeyEvent.VK_SCROLL_LOCK, XKeySymConstants.XK_Scroll_Lock);
tojava         javaKeycode2KeysymHash.put( java.awt.event.KeyEvent.VK_KANA_LOCK, XKeySymConstants.XK_Kana_Lock);
tojava     };
tojava
tojava }
