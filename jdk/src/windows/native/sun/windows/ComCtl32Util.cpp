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

#include "awt.h"
#include "ComCtl32Util.h"

ComCtl32Util::ComCtl32Util() {
}

ComCtl32Util::~ComCtl32Util() {
}

void ComCtl32Util::InitLibraries() {
    INITCOMMONCONTROLSEX iccex;
    memset(&iccex, 0, sizeof(INITCOMMONCONTROLSEX));
    iccex.dwSize = sizeof(INITCOMMONCONTROLSEX);
    ::InitCommonControlsEx(&iccex);
}

WNDPROC ComCtl32Util::SubclassHWND(HWND hwnd, WNDPROC _WindowProc) {
    if (IS_WINXP) {
        const SUBCLASSPROC p = SharedWindowProc; // let compiler check type of SharedWindowProc
        ::SetWindowSubclass(hwnd, p, (UINT_PTR)_WindowProc, NULL); // _WindowProc is used as subclass ID
        return NULL;
    } else {
        return (WNDPROC)::SetWindowLongPtr(hwnd, GWLP_WNDPROC, (LONG_PTR)_WindowProc);
    }
}

void ComCtl32Util::UnsubclassHWND(HWND hwnd, WNDPROC _WindowProc, WNDPROC _DefWindowProc) {
    if (IS_WINXP) {
        const SUBCLASSPROC p = SharedWindowProc; // let compiler check type of SharedWindowProc
        ::RemoveWindowSubclass(hwnd, p, (UINT_PTR)_WindowProc); // _WindowProc is used as subclass ID
    } else {
        ::SetWindowLongPtr(hwnd, GWLP_WNDPROC, (LONG_PTR)_DefWindowProc);
    }
}

LRESULT ComCtl32Util::DefWindowProc(WNDPROC _DefWindowProc, HWND hwnd, UINT msg, WPARAM wParam, LPARAM lParam) {
    if (IS_WINXP) {
        return ::DefSubclassProc(hwnd, msg, wParam, lParam);
    } else if (_DefWindowProc != NULL) {
        return ::CallWindowProc(_DefWindowProc, hwnd, msg, wParam, lParam);
    } else {
        return ::DefWindowProc(hwnd, msg, wParam, lParam);
    }
}

LRESULT ComCtl32Util::SharedWindowProc(HWND hwnd, UINT msg,
                                       WPARAM wParam, LPARAM lParam,
                                       UINT_PTR uIdSubclass, DWORD_PTR dwRefData)
{
    TRY;

    WNDPROC _WindowProc = (WNDPROC)uIdSubclass;
    return ::CallWindowProc(_WindowProc, hwnd, msg, wParam, lParam);

    CATCH_BAD_ALLOC_RET(0);
}
