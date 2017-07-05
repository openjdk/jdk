/*
 * Copyright 2009 Sun Microsystems, Inc.  All Rights Reserved.
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

#ifndef DLLUTIL_H
#define DLLUTIL_H

#include <tchar.h>
#include <windows.h>

/**
 * Utility class to handle dynamically loadable libraries.
 *
 * NOTE: THIS CLASS IS NOT THREAD-SAFE!
 */
class DllUtil {
    public:
        class Exception {};
        class LibraryUnavailableException : public Exception {};
        class FunctionUnavailableException : public Exception {};

        FARPROC GetProcAddress(LPCSTR name);

    protected:
        DllUtil(const TCHAR * name) : name(name), module(NULL) {}
        virtual ~DllUtil();

        HMODULE GetModule();

        template <class FunctionType> class Function {
            public:
                Function(DllUtil * dll, LPCSTR name) :
                    dll(dll), name(name), function(NULL) {}

                inline FunctionType operator () () {
                    if (!function) {
                        function = (FunctionType)dll->GetProcAddress(name);
                    }
                    return function;
                }

            private:
                DllUtil * const dll;
                LPCSTR name;

                FunctionType function;
        };

    private:
        const TCHAR * const name;
        HMODULE module;
};

class DwmAPI : public DllUtil {
    public:
        // See DWMWINDOWATTRIBUTE enum in dwmapi.h
        static const DWORD DWMWA_EXTENDED_FRAME_BOUNDS = 9;

        static HRESULT DwmIsCompositionEnabled(BOOL * pfEnabled);
        static HRESULT DwmGetWindowAttribute(HWND hwnd, DWORD dwAttribute,
                PVOID pvAttribute, DWORD cbAttribute);

    private:
        static DwmAPI & GetInstance();
        DwmAPI();

        typedef HRESULT (WINAPI *DwmIsCompositionEnabledType)(BOOL*);
        Function<DwmIsCompositionEnabledType> DwmIsCompositionEnabledFunction;

        typedef HRESULT (WINAPI *DwmGetWindowAttributeType)(HWND hwnd, DWORD dwAttribute,
                PVOID pvAttribute, DWORD cbAttribute);
        Function<DwmGetWindowAttributeType> DwmGetWindowAttributeFunction;
};

#endif // DLLUTIL_H

