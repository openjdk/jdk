/*
 * Copyright 1996-2003 Sun Microsystems, Inc.  All Rights Reserved.
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

#ifndef AWT_CLIPBOARD_H
#define AWT_CLIPBOARD_H

#include "awt.h"


/************************************************************************
 * AwtClipboard class
 */

class AwtClipboard {
private:
    static BOOL isGettingOwnership;
    // handle to the next window in the clipboard viewer chain
    static volatile HWND hwndNextViewer;
    static volatile BOOL isClipboardViewerRegistered;
    static volatile BOOL skipInitialWmDrawClipboardMsg;
    static volatile jmethodID handleContentsChangedMID;

public:
    static jmethodID lostSelectionOwnershipMID;
    static jobject theCurrentClipboard;

    INLINE static void GetOwnership() {
        AwtClipboard::isGettingOwnership = TRUE;
        VERIFY(EmptyClipboard());
        AwtClipboard::isGettingOwnership = FALSE;
    }

    INLINE static BOOL IsGettingOwnership() {
        return isGettingOwnership;
    }

    static void LostOwnership(JNIEnv *env);
    static void WmChangeCbChain(WPARAM wparam, LPARAM lparam);
    static void WmDrawClipboard(JNIEnv *env, WPARAM wparam, LPARAM lparam);
    static void RegisterClipboardViewer(JNIEnv *env, jobject jclipboard);
    static void UnregisterClipboardViewer(JNIEnv *env);
};

#endif /* AWT_CLIPBOARD_H */
