/*
 * Copyright (c) 2012, 2019, Oracle and/or its affiliates. All rights reserved.
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

#include <Windows.h>
#include <Shellapi.h>
#include <locale.h>
#include <tchar.h>
#include <string>

#define JPACKAGE_LIBRARY TEXT("applauncher.dll")

typedef bool (*start_launcher)(int argc, TCHAR* argv[]);
typedef void (*stop_launcher)();

std::wstring GetTitle() {
    std::wstring result;
    wchar_t buffer[MAX_PATH];
    GetModuleFileName(NULL, buffer, MAX_PATH - 1);
    buffer[MAX_PATH - 1] = '\0';
    result = buffer;
    size_t slash = result.find_last_of('\\');

    if (slash != std::wstring::npos)
        result = result.substr(slash + 1, result.size() - slash - 1);

    return result;
}

#ifdef LAUNCHERC
int main(int argc0, char *argv0[]) {
#else // LAUNCHERC
int APIENTRY _tWinMain(HINSTANCE hInstance, HINSTANCE hPrevInstance,
                       LPTSTR lpCmdLine, int nCmdShow) {
#endif // LAUNCHERC
    int result = 1;
    TCHAR **argv;
    int argc;

    // [RT-31061] otherwise UI can be left in back of other windows.
    ::AllowSetForegroundWindow(ASFW_ANY);

    ::setlocale(LC_ALL, "en_US.utf8");
    argv = CommandLineToArgvW(GetCommandLine(), &argc);

    HMODULE library = ::LoadLibrary(JPACKAGE_LIBRARY);

    if (library == NULL) {
        std::wstring title = GetTitle();
        std::wstring description = std::wstring(JPACKAGE_LIBRARY)
                + std::wstring(TEXT(" not found."));
        MessageBox(NULL, description.data(),
                title.data(), MB_ICONERROR | MB_OK);
    }
    else {
        start_launcher start =
                (start_launcher)GetProcAddress(library, "start_launcher");
        stop_launcher stop =
                (stop_launcher)GetProcAddress(library, "stop_launcher");

        if (start != NULL && stop != NULL) {
            if (start(argc, argv) == true) {
                result = 0;
                stop();
            }
        }

        ::FreeLibrary(library);
    }

    if (argv != NULL) {
        LocalFree(argv);
    }

    return result;
}

