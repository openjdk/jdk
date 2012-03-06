/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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

#import <JavaNativeFoundation/JavaNativeFoundation.h>
#import <JavaRuntimeSupport/JavaRuntimeSupport.h>
#import <sys/time.h>

#import "LWCToolkit.h"
#import "ThreadUtilities.h"

#import "java_awt_event_InputEvent.h"
#import "java_awt_event_KeyEvent.h"
#import "java_awt_event_MouseEvent.h"

/*
 * Table to map typed characters to their Java virtual key equivalent and back.
 * We use the incoming unichar (ignoring all modifiers) and try to figure out
 * which virtual key code is appropriate. A lot of them just have direct
 * mappings (the function keys, arrow keys, etc.) so they aren't a problem.
 * We had to do something a little funky to catch the keys on the numeric
 * key pad (i.e. using event mask to distinguish between period on regular
 * keyboard and decimal on keypad). We also have to do something incredibly
 * hokey with regards to the shifted punctuation characters. For examples,
 * consider '&' which is usually Shift-7.  For the Java key typed events,
 * that's no problem, we just say pass the unichar. But for the
 * KeyPressed/Released events, we need to identify the virtual key code
 * (which roughly correspond to hardware keys) which means we are supposed
 * to say the virtual 7 key was pressed.  But how are we supposed to know
 * when we get a punctuation char what was the real hardware key was that
 * was pressed?  Although '&' often comes from Shift-7 the keyboard can be
 * remapped!  I don't think there really is a good answer, and hopefully
 * all good applets are only interested in logical key typed events not
 * press/release.  Meanwhile, we are hard-coding the shifted punctuation
 * to trigger the virtual keys that are the expected ones under a standard
 * keymapping. Looking at Windows & Mac, they don't actually do this, the
 * Mac seems to just put the ascii code in for the shifted punctuation
 * (which means they actually end up with bogus key codes on the Java side),
 * Windows I can't even figure out what it's doing.
 */
#define KL_STANDARD java_awt_event_KeyEvent_KEY_LOCATION_STANDARD
#define KL_NUMPAD   java_awt_event_KeyEvent_KEY_LOCATION_NUMPAD
#define KL_UNKNOWN  java_awt_event_KeyEvent_KEY_LOCATION_UNKNOWN
static struct _key
{
    unsigned short keyCode;
    BOOL postsTyped;
    jint javaKeyLocation;
    jint javaKeyCode;
}
const keyTable[] =
{
    {0x00, YES, KL_STANDARD, java_awt_event_KeyEvent_VK_A},
    {0x01, YES, KL_STANDARD, java_awt_event_KeyEvent_VK_S},
    {0x02, YES, KL_STANDARD, java_awt_event_KeyEvent_VK_D},
    {0x03, YES, KL_STANDARD, java_awt_event_KeyEvent_VK_F},
    {0x04, YES, KL_STANDARD, java_awt_event_KeyEvent_VK_H},
    {0x05, YES, KL_STANDARD, java_awt_event_KeyEvent_VK_G},
    {0x06, YES, KL_STANDARD, java_awt_event_KeyEvent_VK_Z},
    {0x07, YES, KL_STANDARD, java_awt_event_KeyEvent_VK_X},
    {0x08, YES, KL_STANDARD, java_awt_event_KeyEvent_VK_C},
    {0x09, YES, KL_STANDARD, java_awt_event_KeyEvent_VK_V},
    {0x0A, YES, KL_STANDARD, java_awt_event_KeyEvent_VK_BACK_QUOTE},
    {0x0B, YES, KL_STANDARD, java_awt_event_KeyEvent_VK_B},
    {0x0C, YES, KL_STANDARD, java_awt_event_KeyEvent_VK_Q},
    {0x0D, YES, KL_STANDARD, java_awt_event_KeyEvent_VK_W},
    {0x0E, YES, KL_STANDARD, java_awt_event_KeyEvent_VK_E},
    {0x0F, YES, KL_STANDARD, java_awt_event_KeyEvent_VK_R},
    {0x10, YES, KL_STANDARD, java_awt_event_KeyEvent_VK_Y},
    {0x11, YES, KL_STANDARD, java_awt_event_KeyEvent_VK_T},
    {0x12, YES, KL_STANDARD, java_awt_event_KeyEvent_VK_1},
    {0x13, YES, KL_STANDARD, java_awt_event_KeyEvent_VK_2},
    {0x14, YES, KL_STANDARD, java_awt_event_KeyEvent_VK_3},
    {0x15, YES, KL_STANDARD, java_awt_event_KeyEvent_VK_4},
    {0x16, YES, KL_STANDARD, java_awt_event_KeyEvent_VK_6},
    {0x17, YES, KL_STANDARD, java_awt_event_KeyEvent_VK_5},
    {0x18, YES, KL_STANDARD, java_awt_event_KeyEvent_VK_EQUALS},
    {0x19, YES, KL_STANDARD, java_awt_event_KeyEvent_VK_9},
    {0x1A, YES, KL_STANDARD, java_awt_event_KeyEvent_VK_7},
    {0x1B, YES, KL_STANDARD, java_awt_event_KeyEvent_VK_MINUS},
    {0x1C, YES, KL_STANDARD, java_awt_event_KeyEvent_VK_8},
    {0x1D, YES, KL_STANDARD, java_awt_event_KeyEvent_VK_0},
    {0x1E, YES, KL_STANDARD, java_awt_event_KeyEvent_VK_CLOSE_BRACKET},
    {0x1F, YES, KL_STANDARD, java_awt_event_KeyEvent_VK_O},
    {0x20, YES, KL_STANDARD, java_awt_event_KeyEvent_VK_U},
    {0x21, YES, KL_STANDARD, java_awt_event_KeyEvent_VK_OPEN_BRACKET},
    {0x22, YES, KL_STANDARD, java_awt_event_KeyEvent_VK_I},
    {0x23, YES, KL_STANDARD, java_awt_event_KeyEvent_VK_P},
    {0x24, YES, KL_STANDARD, java_awt_event_KeyEvent_VK_ENTER},
    {0x25, YES, KL_STANDARD, java_awt_event_KeyEvent_VK_L},
    {0x26, YES, KL_STANDARD, java_awt_event_KeyEvent_VK_J},
    {0x27, YES, KL_STANDARD, java_awt_event_KeyEvent_VK_QUOTE},
    {0x28, YES, KL_STANDARD, java_awt_event_KeyEvent_VK_K},
    {0x29, YES, KL_STANDARD, java_awt_event_KeyEvent_VK_SEMICOLON},
    {0x2A, YES, KL_STANDARD, java_awt_event_KeyEvent_VK_BACK_SLASH},
    {0x2B, YES, KL_STANDARD, java_awt_event_KeyEvent_VK_COMMA},
    {0x2C, YES, KL_STANDARD, java_awt_event_KeyEvent_VK_SLASH},
    {0x2D, YES, KL_STANDARD, java_awt_event_KeyEvent_VK_N},
    {0x2E, YES, KL_STANDARD, java_awt_event_KeyEvent_VK_M},
    {0x2F, YES, KL_STANDARD, java_awt_event_KeyEvent_VK_PERIOD},
    {0x30, YES, KL_STANDARD, java_awt_event_KeyEvent_VK_TAB},
    {0x31, YES, KL_STANDARD, java_awt_event_KeyEvent_VK_SPACE},
    {0x32, YES, KL_STANDARD, java_awt_event_KeyEvent_VK_BACK_QUOTE},
    {0x33, YES, KL_STANDARD, java_awt_event_KeyEvent_VK_BACK_SPACE},
    {0x34, YES, KL_NUMPAD,   java_awt_event_KeyEvent_VK_ENTER},
    {0x35, NO,  KL_STANDARD, java_awt_event_KeyEvent_VK_ESCAPE},
    {0x36, NO,  KL_UNKNOWN,  java_awt_event_KeyEvent_VK_UNDEFINED},
    {0x37, NO,  KL_UNKNOWN,  java_awt_event_KeyEvent_VK_META},      // ****
    {0x38, NO,  KL_UNKNOWN,  java_awt_event_KeyEvent_VK_SHIFT},     // ****
    {0x39, NO,  KL_STANDARD, java_awt_event_KeyEvent_VK_CAPS_LOCK},
    {0x3A, NO,  KL_UNKNOWN,  java_awt_event_KeyEvent_VK_ALT},       // ****
    {0x3B, NO,  KL_UNKNOWN,  java_awt_event_KeyEvent_VK_CONTROL},   // ****
    {0x3C, NO,  KL_UNKNOWN,  java_awt_event_KeyEvent_VK_UNDEFINED},
    {0x3D, NO,  KL_UNKNOWN,  java_awt_event_KeyEvent_VK_UNDEFINED},
    {0x3E, NO,  KL_UNKNOWN,  java_awt_event_KeyEvent_VK_UNDEFINED},
    {0x3F, NO,  KL_UNKNOWN,  java_awt_event_KeyEvent_VK_UNDEFINED}, // the 'fn' key on PowerBooks
    {0x40, NO,  KL_UNKNOWN,  java_awt_event_KeyEvent_VK_UNDEFINED},
    {0x41, YES, KL_NUMPAD,   java_awt_event_KeyEvent_VK_DECIMAL},
    {0x42, NO,  KL_UNKNOWN,  java_awt_event_KeyEvent_VK_UNDEFINED},
    {0x43, YES, KL_NUMPAD,   java_awt_event_KeyEvent_VK_MULTIPLY},
    {0x44, NO,  KL_UNKNOWN,  java_awt_event_KeyEvent_VK_UNDEFINED},
    {0x45, YES, KL_NUMPAD,   java_awt_event_KeyEvent_VK_ADD},
    {0x46, NO,  KL_UNKNOWN,  java_awt_event_KeyEvent_VK_UNDEFINED},
    {0x47, NO,  KL_NUMPAD,   java_awt_event_KeyEvent_VK_CLEAR},
    {0x48, NO,  KL_UNKNOWN,  java_awt_event_KeyEvent_VK_UNDEFINED},
    {0x49, NO,  KL_UNKNOWN,  java_awt_event_KeyEvent_VK_UNDEFINED},
    {0x4A, NO,  KL_UNKNOWN,  java_awt_event_KeyEvent_VK_UNDEFINED},
    {0x4B, YES, KL_NUMPAD,   java_awt_event_KeyEvent_VK_DIVIDE},
    {0x4C, YES, KL_NUMPAD,   java_awt_event_KeyEvent_VK_ENTER},
    {0x4D, NO,  KL_UNKNOWN,  java_awt_event_KeyEvent_VK_UNDEFINED},
    {0x4E, YES, KL_NUMPAD,   java_awt_event_KeyEvent_VK_SUBTRACT},
    {0x4F, NO,  KL_UNKNOWN,  java_awt_event_KeyEvent_VK_UNDEFINED},
    {0x50, NO,  KL_UNKNOWN,  java_awt_event_KeyEvent_VK_UNDEFINED},
    {0x51, YES, KL_NUMPAD,   java_awt_event_KeyEvent_VK_EQUALS},
    {0x52, YES, KL_NUMPAD,   java_awt_event_KeyEvent_VK_NUMPAD0},
    {0x53, YES, KL_NUMPAD,   java_awt_event_KeyEvent_VK_NUMPAD1},
    {0x54, YES, KL_NUMPAD,   java_awt_event_KeyEvent_VK_NUMPAD2},
    {0x55, YES, KL_NUMPAD,   java_awt_event_KeyEvent_VK_NUMPAD3},
    {0x56, YES, KL_NUMPAD,   java_awt_event_KeyEvent_VK_NUMPAD4},
    {0x57, YES, KL_NUMPAD,   java_awt_event_KeyEvent_VK_NUMPAD5},
    {0x58, YES, KL_NUMPAD,   java_awt_event_KeyEvent_VK_NUMPAD6},
    {0x59, YES, KL_NUMPAD,   java_awt_event_KeyEvent_VK_NUMPAD7},
    {0x5A, NO,  KL_UNKNOWN,  java_awt_event_KeyEvent_VK_UNDEFINED},
    {0x5B, YES, KL_NUMPAD,   java_awt_event_KeyEvent_VK_NUMPAD8},
    {0x5C, YES, KL_NUMPAD,   java_awt_event_KeyEvent_VK_NUMPAD9},
    {0x5D, YES, KL_STANDARD, java_awt_event_KeyEvent_VK_BACK_SLASH}, // This is a combo yen/backslash on JIS keyboards.
    {0x5E, YES, KL_NUMPAD,   java_awt_event_KeyEvent_VK_UNDERSCORE},
    {0x5F, YES, KL_NUMPAD,   java_awt_event_KeyEvent_VK_COMMA},
    {0x60, NO,  KL_STANDARD, java_awt_event_KeyEvent_VK_F5},
    {0x61, NO,  KL_STANDARD, java_awt_event_KeyEvent_VK_F6},
    {0x62, NO,  KL_STANDARD, java_awt_event_KeyEvent_VK_F7},
    {0x63, NO,  KL_STANDARD, java_awt_event_KeyEvent_VK_F3},
    {0x64, NO,  KL_STANDARD, java_awt_event_KeyEvent_VK_F8},
    {0x65, NO,  KL_STANDARD, java_awt_event_KeyEvent_VK_F9},
    {0x66, NO,  KL_STANDARD, java_awt_event_KeyEvent_VK_ALPHANUMERIC},
    {0x67, NO,  KL_STANDARD, java_awt_event_KeyEvent_VK_F11},
    {0x68, NO,  KL_STANDARD, java_awt_event_KeyEvent_VK_KATAKANA},
    {0x69, NO,  KL_STANDARD, java_awt_event_KeyEvent_VK_F13},
    {0x6A, NO,  KL_STANDARD, java_awt_event_KeyEvent_VK_F16},
    {0x6B, NO,  KL_STANDARD, java_awt_event_KeyEvent_VK_F14},
    {0x6C, NO,  KL_UNKNOWN,  java_awt_event_KeyEvent_VK_UNDEFINED},
    {0x6D, NO,  KL_STANDARD, java_awt_event_KeyEvent_VK_F10},
    {0x6E, NO,  KL_UNKNOWN,  java_awt_event_KeyEvent_VK_UNDEFINED},
    {0x6F, NO,  KL_STANDARD, java_awt_event_KeyEvent_VK_F12},
    {0x70, NO,  KL_UNKNOWN,  java_awt_event_KeyEvent_VK_UNDEFINED},
    {0x71, NO,  KL_STANDARD, java_awt_event_KeyEvent_VK_F15},
    {0x72, NO,  KL_STANDARD, java_awt_event_KeyEvent_VK_HELP},
    {0x73, NO,  KL_STANDARD, java_awt_event_KeyEvent_VK_HOME},
    {0x74, NO,  KL_STANDARD, java_awt_event_KeyEvent_VK_PAGE_UP},
    {0x75, YES, KL_STANDARD, java_awt_event_KeyEvent_VK_DELETE},
    {0x76, NO,  KL_STANDARD, java_awt_event_KeyEvent_VK_F4},
    {0x77, NO,  KL_STANDARD, java_awt_event_KeyEvent_VK_END},
    {0x78, NO,  KL_STANDARD, java_awt_event_KeyEvent_VK_F2},
    {0x79, NO,  KL_STANDARD, java_awt_event_KeyEvent_VK_PAGE_DOWN},
    {0x7A, NO,  KL_STANDARD, java_awt_event_KeyEvent_VK_F1},
    {0x7B, NO,  KL_STANDARD, java_awt_event_KeyEvent_VK_LEFT},
    {0x7C, NO,  KL_STANDARD, java_awt_event_KeyEvent_VK_RIGHT},
    {0x7D, NO,  KL_STANDARD, java_awt_event_KeyEvent_VK_DOWN},
    {0x7E, NO,  KL_STANDARD, java_awt_event_KeyEvent_VK_UP},
    {0x7F, NO,  KL_UNKNOWN,  java_awt_event_KeyEvent_VK_UNDEFINED},
};

/*
 * This table was stolen from the Windows implementation for mapping
 * Unicode values to VK codes for dead keys.  On Windows, some layouts
 * return ASCII punctuation for dead accents, while some return spacing
 * accent chars, so both should be listed.  However, in all of the
 * keyboard layouts I tried only the Unicode values are used.
 */
struct CharToVKEntry {
    UniChar c;
    jint javaKey;
};
static const struct CharToVKEntry charToDeadVKTable[] = {
    {0x0060, java_awt_event_KeyEvent_VK_DEAD_GRAVE},
    {0x00B4, java_awt_event_KeyEvent_VK_DEAD_ACUTE},
    {0x0384, java_awt_event_KeyEvent_VK_DEAD_ACUTE}, // Unicode "GREEK TONOS" -- Greek keyboard, semicolon key
    {0x005E, java_awt_event_KeyEvent_VK_DEAD_CIRCUMFLEX},
    {0x007E, java_awt_event_KeyEvent_VK_DEAD_TILDE},
    {0x02DC, java_awt_event_KeyEvent_VK_DEAD_TILDE}, // Unicode "SMALL TILDE"
    {0x00AF, java_awt_event_KeyEvent_VK_DEAD_MACRON},
    {0x02D8, java_awt_event_KeyEvent_VK_DEAD_BREVE},
    {0x02D9, java_awt_event_KeyEvent_VK_DEAD_ABOVEDOT},
    {0x00A8, java_awt_event_KeyEvent_VK_DEAD_DIAERESIS},
    {0x02DA, java_awt_event_KeyEvent_VK_DEAD_ABOVERING},
    {0x02DD, java_awt_event_KeyEvent_VK_DEAD_DOUBLEACUTE},
    {0x02C7, java_awt_event_KeyEvent_VK_DEAD_CARON},
    {0x00B8, java_awt_event_KeyEvent_VK_DEAD_CEDILLA},
    {0x02DB, java_awt_event_KeyEvent_VK_DEAD_OGONEK},
    {0x037A, java_awt_event_KeyEvent_VK_DEAD_IOTA},
    {0x309B, java_awt_event_KeyEvent_VK_DEAD_VOICED_SOUND},
    {0x309C, java_awt_event_KeyEvent_VK_DEAD_SEMIVOICED_SOUND},
    {0,0}
};

// TODO: some constants below are part of CGS (private interfaces)...
// for now we will look at the raw key code to determine left/right status
// but not sure this is foolproof...
static struct _nsKeyToJavaModifier
{
    NSUInteger nsMask;
    //NSUInteger cgsLeftMask;
    //NSUInteger cgsRightMask;
    unsigned short leftKeyCode;
    unsigned short rightKeyCode;
    jint javaMask;
    jint javaKey;
}
const nsKeyToJavaModifierTable[] =
{
    {
        NSAlphaShiftKeyMask,
        0,
        0,
        0, // no Java equivalent
        java_awt_event_KeyEvent_VK_CAPS_LOCK
    },
    {
        NSShiftKeyMask,
        //kCGSFlagsMaskAppleShiftKey,
        //kCGSFlagsMaskAppleRightShiftKey,
        56,
        60,
        java_awt_event_InputEvent_SHIFT_DOWN_MASK,
        java_awt_event_KeyEvent_VK_SHIFT
    },
    {
        NSControlKeyMask,
        //kCGSFlagsMaskAppleControlKey,
        //kCGSFlagsMaskAppleRightControlKey,
        59,
        62,
        java_awt_event_InputEvent_CTRL_DOWN_MASK,
        java_awt_event_KeyEvent_VK_CONTROL
    },
    {
        NSAlternateKeyMask,
        //kCGSFlagsMaskAppleLeftAlternateKey,
        //kCGSFlagsMaskAppleRightAlternateKey,
        58,
        61,
        java_awt_event_InputEvent_ALT_DOWN_MASK,
        java_awt_event_KeyEvent_VK_ALT
    },
    {
        NSCommandKeyMask,
        //kCGSFlagsMaskAppleLeftCommandKey,
        //kCGSFlagsMaskAppleRightCommandKey,
        55,
        54,
        java_awt_event_InputEvent_META_DOWN_MASK,
        java_awt_event_KeyEvent_VK_META
    },
    // NSNumericPadKeyMask
    {
        NSHelpKeyMask,
        0,
        0,
        0, // no Java equivalent
        java_awt_event_KeyEvent_VK_HELP
    },
    // NSFunctionKeyMask
    {0, 0, 0, 0, 0}
};

/*
 * Almost all unicode characters just go from NS to Java with no translation.
 *  For the few exceptions, we handle it here with this small table.
 */
static struct _char {
    NSUInteger modifier;
    unichar nsChar;
    unichar javaChar;
}
const charTable[] = {
    // map enter on keypad to same as return key
    {0,              NSEnterCharacter,          NSNewlineCharacter},

    // [3134616] return newline instead of carriage return
    {0,              NSCarriageReturnCharacter, NSNewlineCharacter},

    // "delete" means backspace in Java
    {0,              NSDeleteCharacter,         NSBackspaceCharacter},
    {0,              NSDeleteFunctionKey,     NSDeleteCharacter},

    // back-tab is only differentiated from tab by Shift flag
    {NSShiftKeyMask, NSBackTabCharacter,     NSTabCharacter},

    {0, 0, 0}
};

static unichar
NsCharToJavaChar(unichar nsChar, NSUInteger modifiers)
{
    const struct _char *cur;
    NSUInteger keyModifierFlags =
        NSShiftKeyMask | NSControlKeyMask |
        NSAlternateKeyMask | NSCommandKeyMask;

    // Mask off just the keyboard modifiers from the event modifier mask.
    NSUInteger testableFlags = (modifiers & keyModifierFlags);

    // walk through table & find the match
    for (cur = charTable; cur->nsChar != 0 ; cur++) {
        // <rdar://Problem/3476426> Need to determine if we are looking at
        // a plain keypress or a modified keypress.  Don't adjust the
        // character of a keypress with a modifier.
        if (cur->nsChar == nsChar) {
            if (cur->modifier == 0 && testableFlags == 0) {
                // If the modifier field is 0, that means to transform
                // this character if no additional keyboard modifiers are set.
                // This lets ctrl-C be reported as ctrl-C and not transformed
                // into Newline.
                return cur->javaChar;
            } else if (cur->modifier != 0 &&
                       (testableFlags & cur->modifier) == testableFlags)
            {
                // Likewise, if the modifier field is nonzero, that means
                // transform this character if only these modifiers are
                // set in the testable flags.
                return cur->javaChar;
            }
        }
    }

    if (nsChar >= NSUpArrowFunctionKey && nsChar <= NSModeSwitchFunctionKey) {
        return java_awt_event_KeyEvent_CHAR_UNDEFINED;
    }

    // otherwise return character unchanged
    return nsChar;
}

/*
 * This is the function that uses the table above to take incoming
 * NSEvent keyCodes and translate to the Java virtual key code.
 */
static void
NsCharToJavaVirtualKeyCode(unichar ch, unichar deadChar,
                           NSUInteger flags, unsigned short key,
                           jint *keyCode, jint *keyLocation, BOOL *postsTyped)
{
    static size_t size = sizeof(keyTable) / sizeof(struct _key);
    NSInteger offset;

    if (deadChar) {
        const struct CharToVKEntry *map;
        for (map = charToDeadVKTable; map->c != 0; ++map) {
            if (deadChar == map->c) {
                *keyCode = map->javaKey;
                *postsTyped = NO;
                // TODO: use UNKNOWN here?
                *keyLocation = java_awt_event_KeyEvent_KEY_LOCATION_UNKNOWN;
                return;
            }
        }
        // If we got here, we keep looking for a normal key.
    }

    if ([[NSCharacterSet letterCharacterSet] characterIsMember:ch]) {
        // key is an alphabetic character
        unichar lower;
        lower = tolower(ch);
        offset = lower - 'a';
        if (offset >= 0 && offset <= 25) {
            // some chars in letter set are NOT actually A-Z characters?!
            // skip them...
            *postsTyped = YES;
            // do quick conversion
            *keyCode = java_awt_event_KeyEvent_VK_A + offset;
            *keyLocation = java_awt_event_KeyEvent_KEY_LOCATION_STANDARD;
            return;
        }
    }

    if ([[NSCharacterSet decimalDigitCharacterSet] characterIsMember:ch]) {
        // key is a digit
        offset = ch - '0';
        // make sure in range for decimal digits
        if (offset >= 0 && offset <= 9)    {
            jboolean numpad = (flags & NSNumericPadKeyMask) != 0;
            *postsTyped = YES;
            if (numpad) {
                *keyCode = offset + java_awt_event_KeyEvent_VK_NUMPAD0;
                *keyLocation = java_awt_event_KeyEvent_KEY_LOCATION_NUMPAD;
            } else {
                *keyCode = offset + java_awt_event_KeyEvent_VK_0;
                *keyLocation = java_awt_event_KeyEvent_KEY_LOCATION_STANDARD;
            }
            return;
        }
    }

    if (key < size) {
        *postsTyped = keyTable[key].postsTyped;
        *keyCode = keyTable[key].javaKeyCode;
        *keyLocation = keyTable[key].javaKeyLocation;
    } else {
        // Should we report this? This means we've got a keyboard
        // we don't know about...
        *postsTyped = NO;
        *keyCode = java_awt_event_KeyEvent_VK_UNDEFINED;
        *keyLocation = java_awt_event_KeyEvent_KEY_LOCATION_UNKNOWN;
    }
}

/*
 * This returns the java key data for the key NSEvent modifiers
 * (after NSFlagChanged).
 */
static void
NsKeyModifiersToJavaKeyInfo(NSUInteger nsFlags, unsigned short eventKeyCode,
                            jint *javaKeyCode,
                            jint *javaKeyLocation,
                            jint *javaKeyType)
{
    static NSUInteger sPreviousNSFlags = 0;

    const struct _nsKeyToJavaModifier* cur;
    NSUInteger oldNSFlags = sPreviousNSFlags;
    NSUInteger changedNSFlags = oldNSFlags ^ nsFlags;
    sPreviousNSFlags = nsFlags;

    *javaKeyCode = java_awt_event_KeyEvent_VK_UNDEFINED;
    *javaKeyLocation = java_awt_event_KeyEvent_KEY_LOCATION_UNKNOWN;
    *javaKeyType = java_awt_event_KeyEvent_KEY_PRESSED;

    for (cur = nsKeyToJavaModifierTable; cur->nsMask != 0; ++cur) {
        if (changedNSFlags & cur->nsMask) {
            *javaKeyCode = cur->javaKey;
            *javaKeyLocation = java_awt_event_KeyEvent_KEY_LOCATION_STANDARD;
            // TODO: uses SPI...
            //if (changedNSFlags & cur->cgsLeftMask) {
            //    *javaKeyLocation = java_awt_event_KeyEvent_KEY_LOCATION_LEFT;
            //} else if (changedNSFlags & cur->cgsRightMask) {
            //    *javaKeyLocation = java_awt_event_KeyEvent_KEY_LOCATION_RIGHT;
            //}
            if (eventKeyCode == cur->leftKeyCode) {
                *javaKeyLocation = java_awt_event_KeyEvent_KEY_LOCATION_LEFT;
            } else if (eventKeyCode == cur->rightKeyCode) {
                *javaKeyLocation = java_awt_event_KeyEvent_KEY_LOCATION_RIGHT;
            }
            *javaKeyType = (cur->nsMask & nsFlags) ?
                java_awt_event_KeyEvent_KEY_PRESSED :
                java_awt_event_KeyEvent_KEY_RELEASED;
            break;
        }
    }
}

/*
 * This returns the java modifiers for a key NSEvent.
 */
static jint
NsKeyModifiersToJavaModifiers(NSUInteger nsFlags)
{
    jint javaModifiers = 0;
    const struct _nsKeyToJavaModifier* cur;

    for (cur = nsKeyToJavaModifierTable; cur->nsMask != 0; ++cur) {
        if ((cur->nsMask & nsFlags) != 0) {
            javaModifiers |= cur->javaMask;
        }
    }

    return javaModifiers;
}

/*
 * Returns the correct java character for a key event.  Most unicode
 * characters don't require any fussing, but a few seem to need adjusting,
 * see nsCharToJavaChar.
 */
static unichar
GetJavaCharacter(NSEvent *event, unsigned int index)
{
    unichar returnValue = java_awt_event_KeyEvent_CHAR_UNDEFINED;
    NSString *chars = nil;
    unichar testChar = 0, testDeadChar = 0;
    jint javaModifiers = NsKeyModifiersToJavaModifiers([event modifierFlags]);

    switch ([event type]) {
    case NSFlagsChanged:
        // no character for modifier keys
        returnValue = java_awt_event_KeyEvent_CHAR_UNDEFINED;
        break;

    case NSKeyDown:
    case NSKeyUp:
        chars = [event characters];
        if ([chars length] > 0) {
            testChar = [chars characterAtIndex:index];
        }

        if (javaModifiers == 0) {
            // TODO: uses SPI...
            //if (TSMGetDeadKeyState() != 0) {
            //    testDeadChar = [self deadKeyCharacter];
            //}
        }

        if (testChar != 0) {
            returnValue = NsCharToJavaChar(testChar, [event modifierFlags]);
        } else if (testDeadChar != 0) {
            returnValue = NsCharToJavaChar(testDeadChar, [event modifierFlags]);
        } else {
            returnValue = java_awt_event_KeyEvent_CHAR_UNDEFINED;
        }
        break;

    default:
        //[NSException raise:@"AWT error" format:@"Attempt to get character code from non-key event!"];
        break;
    }

    return returnValue;
}

/*
static jchar
GetDeadKeyCharacter(NSEvent *event)
{
    // If the current event is not a dead key, return 0.
    // TODO: this uses SPI; it's an optimization but not strictly necessary
    //if (TSMGetDeadKeyState() == 0) {
    //    return 0;
    //}

    // AppKit does not track dead-key states directly, but TSM does. Even then,
    // it's not necessarily all that accurate, because the dead key can change
    // given some combination of modifier keys on certain layouts.
    // As a result, finding the unicode value for the front end of the dead
    // key is a bit of a heuristic.

    // This algorithm was suggested by Aki Inoue.
    // When you get a dead key, you need to simiulate what would happen in
    // the current dead-key and modifier state if the user hit the spacebar.
    // That will tell you the front end of the dead-key combination.

    unichar returnValue = 0;
    const UInt16 VIRTUAL_KEY_SPACE = 49;
    UInt32 deadKeyState = 0;
    UInt32 appkitFlags = [event modifierFlags];
    UniCharCount actualStringLength;
    UniChar unicodeInputString[16];
    TISInputSourceRef keyLayout;
    const void *chrData;

    keyLayout = TISCopyCurrentKeyboardLayoutInputSource();
    CFDataRef cfUchrData =
        TISGetInputSourceProperty(keyLayout, kTISPropertyUnicodeKeyLayoutData);

    if (cfUchrData == NULL) {
        return returnValue;
    }

    // The actual 'uchr' table is inside the CFDataRef.
    chrData = CFDataGetBytePtr(cfUchrData);

    UInt8 keyboardType = LMGetKbdType();
    UInt32 keyEventModifiers = 0;
    if (appkitFlags & NSShiftKeyMask)      keyEventModifiers |= shiftKey;
    if (appkitFlags & NSCommandKeyMask)    keyEventModifiers |= cmdKey;
    if (appkitFlags & NSAlphaShiftKeyMask) keyEventModifiers |= alphaLock;
    if (appkitFlags & NSControlKeyMask)    keyEventModifiers |= controlKey;
    if (appkitFlags & NSAlternateKeyMask)  keyEventModifiers |= optionKey;

    if (noErr == UCKeyTranslate(chrData,
        VIRTUAL_KEY_SPACE,
        ([event type] == NSKeyDown ? kUCKeyActionDown : kUCKeyActionUp),
        keyEventModifiers,
        keyboardType,
        kUCKeyTranslateNoDeadKeysMask,
        &deadKeyState,
        16,
        &actualStringLength,
        unicodeInputString))
    {
        if (actualStringLength > 0) {
            returnValue = unicodeInputString[0];
        }
    }

    return returnValue;
}
*/


// REMIND: The fix for MACOSX_PORT-539 introduces Java-level implementation
// of the function below (see CPlatformResponder). Consider removing this code.

void
DeliverJavaKeyEvent(JNIEnv *env, NSEvent *event, jobject peer)
{
    jint javaKeyType = java_awt_event_KeyEvent_KEY_PRESSED;
    jint javaKeyCode = java_awt_event_KeyEvent_VK_UNDEFINED;
    jint javaKeyLocation = java_awt_event_KeyEvent_KEY_LOCATION_UNKNOWN;
    NSString *chars = nil;
    BOOL postsTyped;
    unichar testChar = java_awt_event_KeyEvent_CHAR_UNDEFINED;
    unichar testDeadChar = 0;
    jint javaModifiers = 0;

    switch ([event type]) {
    case NSFlagsChanged:
        NsKeyModifiersToJavaKeyInfo([event modifierFlags],
                                    [event keyCode],
                                    &javaKeyCode,
                                    &javaKeyLocation,
                                    &javaKeyType);
        break;

    case NSKeyDown:
    case NSKeyUp:
        chars = [event charactersIgnoringModifiers];
        if ([chars length] > 0) {
            testChar = [chars characterAtIndex:0];
        }

        javaModifiers = NsKeyModifiersToJavaModifiers([event modifierFlags]);
        if (javaModifiers == 0) {
      // TODO: dead key chars
//            testDeadChar = GetDeadKeyCharacter(event);
        }

        NsCharToJavaVirtualKeyCode(testChar, testDeadChar,
                                   [event modifierFlags], [event keyCode],
                                   &javaKeyCode, &javaKeyLocation, &postsTyped);
        if( !postsTyped ) {
            testChar = java_awt_event_KeyEvent_CHAR_UNDEFINED;
        }

        javaKeyType = ([event type] == NSKeyDown) ?
            java_awt_event_KeyEvent_KEY_PRESSED :
            java_awt_event_KeyEvent_KEY_RELEASED;
        break;

    default:
        //[NSException raise:@"AWT error" format:@"Attempt to get virtual key code from non-key event!"];
        break;
    }

    if (env != NULL) {
        static JNF_CLASS_CACHE(jc_CPlatformView, "sun/lwawt/macosx/CPlatformView");
        static JNF_MEMBER_CACHE(jm_deliverKeyEvent, jc_CPlatformView, "deliverKeyEvent", "(IICII)V");
        JNFCallVoidMethod(env, peer, jm_deliverKeyEvent,
                          javaKeyType, javaModifiers,
                          testChar, javaKeyCode, javaKeyLocation);
    }
}

jint GetJavaMouseModifiers(NSInteger button, NSUInteger modifierFlags)
{
    // Mousing needs the key modifiers
    jint modifiers = NsKeyModifiersToJavaModifiers(modifierFlags);


    /*
     * Ask Quartz about mouse buttons state
     */

    if (CGEventSourceButtonState(kCGEventSourceStateCombinedSessionState,
                                 kCGMouseButtonLeft)) {
        modifiers |= java_awt_event_InputEvent_BUTTON1_DOWN_MASK;
    }

    if (CGEventSourceButtonState(kCGEventSourceStateCombinedSessionState,
                                 kCGMouseButtonRight)) {
        modifiers |= java_awt_event_InputEvent_BUTTON3_DOWN_MASK;
    }

    if (CGEventSourceButtonState(kCGEventSourceStateCombinedSessionState,
                                 kCGMouseButtonCenter)) {
        modifiers |= java_awt_event_InputEvent_BUTTON2_DOWN_MASK;
    }

    NSInteger extraButton = 3;
    for (; extraButton < gNumberOfButtons; extraButton++) {
        if (CGEventSourceButtonState(kCGEventSourceStateCombinedSessionState,
                                 extraButton)) {
            modifiers |= gButtonDownMasks[extraButton];
        }
    }

    return modifiers;
}

/*
 * Converts an NSEvent button number to a MouseEvent constant.
 */
static jint
NSButtonToJavaButton(NSInteger nsButtonNumber)
{
    jint jbutton = java_awt_event_MouseEvent_NOBUTTON;

    if (nsButtonNumber == 0) { // left
        jbutton = java_awt_event_MouseEvent_BUTTON1;
    } else if (nsButtonNumber == 1) { // right
        jbutton = java_awt_event_MouseEvent_BUTTON3;
    } else if (nsButtonNumber == 2) { // middle
        jbutton = java_awt_event_MouseEvent_BUTTON2;
    }

    return jbutton;
}


static BOOL isDragging = NO;

void
DeliverMouseClickedEvent(JNIEnv *env, NSEvent *event, jobject peer)
{
    NSPoint pt = [event locationInWindow];
    NSPoint pOnScreen = [NSEvent mouseLocation];
    jint etype = java_awt_event_MouseEvent_MOUSE_CLICKED;
    jint modifiers = GetJavaMouseModifiers([event buttonNumber], [event modifierFlags]);
    jint clickCount = [event clickCount];
    jint button = NSButtonToJavaButton([event buttonNumber]);

    if (env != NULL) {
        static JNF_CLASS_CACHE(jc_CPlatformView, "sun/lwawt/macosx/CPlatformView");
        static JNF_MEMBER_CACHE(jm_deliverMouseEvent, jc_CPlatformView,
                                "deliverMouseEvent", "(IIIIFFFF)V");
        JNFCallVoidMethod(env, peer, jm_deliverMouseEvent,
                          etype, modifiers,
                          clickCount, button,
                          pt.x, pt.y,
                          pOnScreen.x, pOnScreen.y);
    }
}

/*
 * After every key down event, this is called to make the matching
 * KEY_TYPED (if this key posts those).  We use the same NSEvent for it,
 * but create a KEY_TYPED java event this time.
 * If this key doesn't post typed, we don't post the event.
 *
 * TODO: some duplicated effort here; could just fold it
 * into DeliverJavaKeyEvent...
 */
static void
DeliverKeyTypedEvents(JNIEnv *env, NSEvent *nsEvent, jobject peer)
{
    if (peer == NULL) {
        return;
    }

    jint javaKeyCode, javaKeyLocation;
    BOOL postsTyped = NO;
    unichar testChar, testDeadChar = 0;
    jint javaModifiers = NsKeyModifiersToJavaModifiers([nsEvent modifierFlags]);

    if (javaModifiers == 0) {
        testDeadChar = [nsEvent deadKeyCharacter];
    }

    NSString *theChars = [nsEvent characters];
    unsigned i, stringLength = [theChars length];

    for (i = 0; i < stringLength; i++) {
        testChar = [theChars characterAtIndex:i];
        NsCharToJavaVirtualKeyCode(testChar, testDeadChar,
                                   [nsEvent modifierFlags], [nsEvent keyCode],
                                   &javaKeyCode, &javaKeyLocation, &postsTyped);

        if (postsTyped) {
            // Some keys may generate a KEY_TYPED, but we can't determine
            // what that character is. That's likely a bug, but for now we
            // just check for CHAR_UNDEFINED.
            unichar theChar = GetJavaCharacter(nsEvent, i);
            if (theChar != java_awt_event_KeyEvent_CHAR_UNDEFINED) {
                if (env != NULL) {
                    static JNF_CLASS_CACHE(jc_CPlatformView,
                                           "sun/lwawt/macosx/CPlatformView");
                    static JNF_MEMBER_CACHE(jm_deliverKeyEvent, jc_CPlatformView,
                                            "deliverKeyEvent", "(IICII)V");
                    JNFCallVoidMethod(env, peer, jm_deliverKeyEvent,
                                      java_awt_event_KeyEvent_KEY_TYPED,
                                      javaModifiers,
                                      theChar,
                                      java_awt_event_KeyEvent_VK_UNDEFINED,
                                      java_awt_event_KeyEvent_KEY_LOCATION_UNKNOWN);
                }
            }
        }
    }
}

/*
 * There are a couple of extra events that Java expects to get that don't
 * actually correspond to a direct NSEvent, KEY_TYPED and MOUSE_CLICKED are
 * both extra events that are sort of redundant with ordinary
 * key downs and mouse ups.  In this extra message, we take the original
 * input event and if necessary, cons up a special follow-on event which
 * we dispatch over to Java.
 *
 * For Java, keyDown's generate a KeyPressed (for each hardware key as it
 * goes down) and then a "logical KeyTyped" event for the key event. (So
 * a shift-a generates two presses, one keytyped of "A", and then two
 * releases).  The standard event utility function converts a key down to
 * a key pressed. When appropriate, we need to cons up another event
 * (KEY_TYPED) to follow a keyDown.
 *
 * Java expects you to send a clicked event if you got a down & up, with no
 * intervening drag. So in addition to the MOUSE_RELEASED event that a
 * mouseUp is translated to, we also have to cons up a MOUSE_CLICKED event
 * for that case. Mike Paquette, god of Window Server event handling,
 * confirmed this fact about how to determine if a mouse up event had an
 * intervening drag:
 * An initial mouse-down gets a click count of 1. Subsequent left or right
 * mouse-downs within the space/time tolerance limits increment the click
 * count.  A mouse-up will have the clickCount of the last mouseDown if
 * mouse is not outside the tolerance limits, but 0 otherwise.  Thus, a
 * down-up sequence without any intervening drag will have a click count
 * of 0 in the mouse-up event.  NOTE: The problem with this is that
 * clickCount goes to zero after some point in time. So a long, click &
 * hold without moving and then release the mouse doesn't create a
 * MOUSE_CLICK event as it should. Java AWT now tracks the drag state itself.
 *
 * As another add-on, we also check for the status of mouse-motion events
 * after a mouse-down, so we know whether to generate mouse-dragged events
 * during this down sequence.
 */
void
SendAdditionalJavaEvents(JNIEnv *env, NSEvent *nsEvent, jobject peer)
{
    AWT_ASSERT_APPKIT_THREAD;

    NSEventType type = [nsEvent type];
    switch (type) {
    case NSKeyDown:
        break;

    case NSLeftMouseUp:
    case NSRightMouseUp:
    case NSOtherMouseUp:
        // TODO: we may need to pull in changedDragToMove here...
        //if (!isDragging && ([NSViewAWT changedDragToMove]==NO)) {
        if (!isDragging) {
            // got down/up pair with no dragged in between; ignores drag events
            // that have been morphed to move events
            DeliverMouseClickedEvent(env, nsEvent, peer);
        }
        break;

// TODO: to be implemented...
#if 0
    case NSLeftMouseDragged:
    case NSRightMouseDragged:
    case NSOtherMouseDragged:
        //
        // During a drag, the AppKit does not send mouseEnter and mouseExit
        // events.  It turns out that doing a hitTest causes the window's
        // view hierarchy to be locked from drawing and that, of course,
        // slows everything way down.  Synthesize mouseEnter and mouseExit
        // then forward.
        //
        NSView *hitView = [[source model] hitTest:[nsEvent locationInWindow]];

        if ((hitView != nil) &&
            ([hitView conformsToProtocol:@protocol(AWTPeerControl)]))
        {
            if (sLastMouseDraggedView == nil) {
                sLastMouseDraggedView = hitView;
            }
            else if (hitView != sLastMouseDraggedView) {
                // We know sLastMouseDraggedView is a AWTPeerControl.
                jobject lastPeer =
                    [(id <AWTPeerControl>)sLastMouseDraggedView peer];

                // Send mouseExit to sLastMouseDraggedView
                jobject exitEvent =
                    makeMouseEvent(env, nsEvent, lastPeer,
                                   sLastMouseDraggedView,
                                   java_awt_event_MouseEvent_MOUSE_EXITED);
                pushEventForward(exitEvent, env);
                (*env)->DeleteLocalRef(env, exitEvent);

                // Send mouseEnter to hitView
                jobject enterEvent =
                    makeMouseEvent(env, nsEvent, peer, hitView,
                                   java_awt_event_MouseEvent_MOUSE_ENTERED);
                pushEventForward(enterEvent, env);

                (*env)->DeleteLocalRef(env, enterEvent);

                // Set sLastMouseDraggedView = hitView
                sLastMouseDraggedView = hitView;
            }
        }
        break;
#endif

    default:
        break;
    }
}

jlong UTC(NSEvent *event) {
    struct timeval tv;
    if (gettimeofday(&tv, NULL) == 0) {
        long long sec = (long long)tv.tv_sec;
        return (sec*1000) + (tv.tv_usec/1000);
    }
    return 0;
}

JNIEXPORT void JNICALL
Java_java_awt_AWTEvent_nativeSetSource
    (JNIEnv *env, jobject self, jobject newSource)
{
}

/*
 * Class:     sun_lwawt_macosx_event_NSEvent
 * Method:    nsToJavaMouseModifiers
 * Signature: (II)I
 */
JNIEXPORT jint JNICALL
Java_sun_lwawt_macosx_event_NSEvent_nsToJavaMouseModifiers
(JNIEnv *env, jclass cls, jint buttonNumber, jint modifierFlags)
{
    jint jmodifiers = 0;

JNF_COCOA_ENTER(env);

    jmodifiers = GetJavaMouseModifiers(buttonNumber, modifierFlags);

JNF_COCOA_EXIT(env);

    return jmodifiers;
}

/*
 * Class:     sun_lwawt_macosx_event_NSEvent
 * Method:    nsToJavaKeyModifiers
 * Signature: (I)I
 */
JNIEXPORT jint JNICALL
Java_sun_lwawt_macosx_event_NSEvent_nsToJavaKeyModifiers
(JNIEnv *env, jclass cls, jint modifierFlags)
{
    jint jmodifiers = 0;

JNF_COCOA_ENTER(env);

    jmodifiers = NsKeyModifiersToJavaModifiers(modifierFlags);

JNF_COCOA_EXIT(env);

    return jmodifiers;
}

/*
 * Class:     sun_lwawt_macosx_event_NSEvent
 * Method:    nsToJavaKeyInfo
 * Signature: ([I[I)Z
 */
JNIEXPORT jboolean JNICALL
Java_sun_lwawt_macosx_event_NSEvent_nsToJavaKeyInfo
(JNIEnv *env, jclass cls, jintArray inData, jintArray outData)
{
    BOOL postsTyped = NO;

JNF_COCOA_ENTER(env);

    jboolean copy = JNI_FALSE;
    jint *data = (*env)->GetIntArrayElements(env, inData, &copy);

    // in  = [testChar, testDeadChar, modifierFlags, keyCode]
    jchar testChar = (jchar)data[0];
    jchar testDeadChar = (jchar)data[1];
    jint modifierFlags = data[2];
    jshort keyCode = (jshort)data[3];

    jint jkeyCode = java_awt_event_KeyEvent_VK_UNDEFINED;
    jint jkeyLocation = java_awt_event_KeyEvent_KEY_LOCATION_UNKNOWN;

    NsCharToJavaVirtualKeyCode((unichar)testChar, (unichar)testDeadChar,
                               (NSUInteger)modifierFlags, (unsigned short)keyCode,
                               &jkeyCode, &jkeyLocation, &postsTyped);

    // out = [jkeyCode, jkeyLocation];
    (*env)->SetIntArrayRegion(env, outData, 0, 1, &jkeyCode);
    (*env)->SetIntArrayRegion(env, outData, 1, 1, &jkeyLocation);

    (*env)->ReleaseIntArrayElements(env, inData, data, 0);

JNF_COCOA_EXIT(env);

    return postsTyped;
}

/*
 * Class:     sun_lwawt_macosx_event_NSEvent
 * Method:    nsKeyModifiersToJavaKeyInfo
 * Signature: ([I[I)V
 */
JNIEXPORT void JNICALL
Java_sun_lwawt_macosx_event_NSEvent_nsKeyModifiersToJavaKeyInfo
(JNIEnv *env, jclass cls, jintArray inData, jintArray outData)
{
JNF_COCOA_ENTER(env);

    jboolean copy = JNI_FALSE;
    jint *data = (*env)->GetIntArrayElements(env, inData, &copy);

    // in  = [modifierFlags, keyCode]
    jint modifierFlags = data[0];
    jshort keyCode = (jshort)data[1];

    jint jkeyCode = java_awt_event_KeyEvent_VK_UNDEFINED;
    jint jkeyLocation = java_awt_event_KeyEvent_KEY_LOCATION_UNKNOWN;
    jint jkeyType = java_awt_event_KeyEvent_KEY_PRESSED;

    NsKeyModifiersToJavaKeyInfo(modifierFlags,
                                keyCode,
                                &jkeyCode,
                                &jkeyLocation,
                                &jkeyType);

    // out = [jkeyCode, jkeyLocation, jkeyType];
    (*env)->SetIntArrayRegion(env, outData, 0, 1, &jkeyCode);
    (*env)->SetIntArrayRegion(env, outData, 1, 1, &jkeyLocation);
    (*env)->SetIntArrayRegion(env, outData, 2, 1, &jkeyType);

    (*env)->ReleaseIntArrayElements(env, inData, data, 0);

JNF_COCOA_EXIT(env);
}
