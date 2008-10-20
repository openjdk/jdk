/*
 * Copyright 2005-2008 Sun Microsystems, Inc.  All Rights Reserved.
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

#include "awt_Component.h"

#include <commctrl.h>

#ifndef _COMCTL32UTIL_H
#define _COMCTL32UTIL_H


/*
 * comctl32.dll version 6 subclassing - taken from PlatformSDK/Include/commctrl.h
 */
typedef LRESULT (CALLBACK *SUBCLASSPROC)(HWND hWnd, UINT uMsg, WPARAM wParam, \
    LPARAM lParam, UINT_PTR uIdSubclass, DWORD_PTR dwRefData);

typedef BOOL (WINAPI *PFNSETWINDOWSUBCLASS)(HWND hWnd, SUBCLASSPROC pfnSubclass, UINT_PTR uIdSubclass, \
    DWORD_PTR dwRefData);
typedef BOOL (WINAPI *PFNREMOVEWINDOWSUBCLASS)(HWND hWnd, SUBCLASSPROC pfnSubclass, \
    UINT_PTR uIdSubclass);

typedef LRESULT (WINAPI *PFNDEFSUBCLASSPROC)(HWND hWnd, UINT uMsg, WPARAM wParam, LPARAM lParam);

class ComCtl32Util
{
    public:
        static ComCtl32Util &GetInstance() {
            static ComCtl32Util theInstance;
            return theInstance;
        }

        // loads comctl32.dll and checks if required routines are available
        // called from AwtToolkit::AwtToolkit()
        void InitLibraries();
        // unloads comctl32.dll
        // called from AwtToolkit::Dispose()
        void FreeLibraries();

        //-- comctl32.dll version 6 subclassing API --//

        INLINE BOOL IsNewSubclassing() {
            return m_bNewSubclassing;
        }

        // if comctl32.dll version 6 is used returns NULL, otherwise
        // returns default window proc
        WNDPROC SubclassHWND(HWND hwnd, WNDPROC _WindowProc);
        // DefWindowProc is the same as returned from SubclassHWND
        void UnsubclassHWND(HWND hwnd, WNDPROC _WindowProc, WNDPROC _DefWindowProc);
        // DefWindowProc is the same as returned from SubclassHWND or NULL
        LRESULT DefWindowProc(WNDPROC _DefWindowProc, HWND hwnd, UINT msg, WPARAM wParam, LPARAM lParam);

    private:
        ComCtl32Util();
        ~ComCtl32Util();

        HMODULE hModComCtl32;

        PFNSETWINDOWSUBCLASS m_lpfnSetWindowSubclass;
        PFNREMOVEWINDOWSUBCLASS m_lpfnRemoveWindowSubclass;
        PFNDEFSUBCLASSPROC m_lpfnDefSubclassProc;

        typedef BOOL (WINAPI * InitCommonControlsExType)(const LPINITCOMMONCONTROLSEX lpInitCtrls);
        InitCommonControlsExType fn_InitCommonControlsEx;

        void InitCommonControls();

        BOOL m_bNewSubclassing;

        // comctl32.dll version 6 window proc
        static LRESULT CALLBACK SharedWindowProc(HWND hwnd, UINT message,
                                                 WPARAM wParam, LPARAM lParam,
                                                 UINT_PTR uIdSubclass, DWORD_PTR dwRefData);
};

#endif // _COMCTL32UTIL_H
