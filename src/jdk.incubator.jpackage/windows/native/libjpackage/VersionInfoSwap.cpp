/*
 * Copyright (c) 2015, 2019, Oracle and/or its affiliates. All rights reserved.
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

#include "VersionInfoSwap.h"

#include <stdio.h>
#include <tchar.h>

#include <windows.h>
#include <stdio.h>
#include <Strsafe.h>
#include <fstream>
#include <locale>
#include <codecvt>

using namespace std;

/*
 * [Property file] contains key/value pairs
 * The swap tool uses these pairs to create new version resource
 *
 * See MSDN docs for VS_VERSIONINFO structure that
 * depicts organization of data in this version resource
 *    https://msdn.microsoft.com/en-us/library/ms647001(v=vs.85).aspx
 *
 * The swap tool makes changes in [Executable file]
 * The tool assumes that the executable file has no version resource
 * and it adds new resource in the executable file.
 * If the executable file has an existing version resource, then
 * the existing version resource will be replaced with new one.
 */

VersionInfoSwap::VersionInfoSwap(wstring executableProperties,
        wstring launcher) {
    m_executableProperties = executableProperties;
    m_launcher = launcher;
}

bool VersionInfoSwap::PatchExecutable() {
    bool b = LoadFromPropertyFile();
    if (!b) {
        return false;
    }

    ByteBuffer buf;
    b = CreateNewResource(&buf);
    if (!b) {
        return false;
    }

    b = this->UpdateResource(buf.getPtr(), static_cast<DWORD> (buf.getPos()));
    if (!b) {
        return false;
    }

    return true;
}

bool VersionInfoSwap::LoadFromPropertyFile() {
    wifstream stream(m_executableProperties.c_str());

    const locale empty_locale = locale::empty();
    const locale utf8_locale =
            locale(empty_locale, new codecvt_utf8<wchar_t>());
    stream.imbue(utf8_locale);

    if (stream.is_open() == true) {
        int lineNumber = 1;
        while (stream.eof() == false) {
            wstring line;
            getline(stream, line);

            // # at the first character will comment out the line.
            if (line.empty() == false && line[0] != '#') {
                wstring::size_type pos = line.find('=');
                if (pos != wstring::npos) {
                    wstring name = line.substr(0, pos);
                    wstring value = line.substr(pos + 1);
                    m_props[name] = value;
                }
            }
            lineNumber++;
        }
        return true;
    }

    return false;
}

/*
 * Creates new version resource
 *
 * MSND docs for VS_VERSION_INFO structure
 *     https://msdn.microsoft.com/en-us/library/ms647001(v=vs.85).aspx
 */
bool VersionInfoSwap::CreateNewResource(ByteBuffer *buf) {
    size_t versionInfoStart = buf->getPos();
    buf->AppendWORD(0);
    buf->AppendWORD(sizeof VS_FIXEDFILEINFO);
    buf->AppendWORD(0);
    buf->AppendString(TEXT("VS_VERSION_INFO"));
    buf->Align(4);

    VS_FIXEDFILEINFO fxi;
    if (!FillFixedFileInfo(&fxi)) {
        return false;
    }
    buf->AppendBytes((BYTE*) & fxi, sizeof (VS_FIXEDFILEINFO));
    buf->Align(4);

    // String File Info
    size_t stringFileInfoStart = buf->getPos();
    buf->AppendWORD(0);
    buf->AppendWORD(0);
    buf->AppendWORD(1);
    buf->AppendString(TEXT("StringFileInfo"));
    buf->Align(4);

    // String Table
    size_t stringTableStart = buf->getPos();
    buf->AppendWORD(0);
    buf->AppendWORD(0);
    buf->AppendWORD(1);

    // "040904B0" = LANG_ENGLISH/SUBLANG_ENGLISH_US, Unicode CP
    buf->AppendString(TEXT("040904B0"));
    buf->Align(4);

    // Strings
    vector<wstring> keys;
    for (map<wstring, wstring>::const_iterator it =
            m_props.begin(); it != m_props.end(); ++it) {
        keys.push_back(it->first);
    }

    for (size_t index = 0; index < keys.size(); index++) {
        wstring name = keys[index];
        wstring value = m_props[name];

        size_t stringStart = buf->getPos();
        buf->AppendWORD(0);
        buf->AppendWORD(static_cast<WORD> (value.length()));
        buf->AppendWORD(1);
        buf->AppendString(name);
        buf->Align(4);
        buf->AppendString(value);
        buf->ReplaceWORD(stringStart,
                static_cast<WORD> (buf->getPos() - stringStart));
        buf->Align(4);
    }

    buf->ReplaceWORD(stringTableStart,
            static_cast<WORD> (buf->getPos() - stringTableStart));
    buf->ReplaceWORD(stringFileInfoStart,
            static_cast<WORD> (buf->getPos() - stringFileInfoStart));

    // VarFileInfo
    size_t varFileInfoStart = buf->getPos();
    buf->AppendWORD(1);
    buf->AppendWORD(0);
    buf->AppendWORD(1);
    buf->AppendString(TEXT("VarFileInfo"));
    buf->Align(4);

    buf->AppendWORD(0x24);
    buf->AppendWORD(0x04);
    buf->AppendWORD(0x00);
    buf->AppendString(TEXT("Translation"));
    buf->Align(4);
    // "000004B0" = LANG_NEUTRAL/SUBLANG_ENGLISH_US, Unicode CP
    buf->AppendWORD(0x0000);
    buf->AppendWORD(0x04B0);

    buf->ReplaceWORD(varFileInfoStart,
            static_cast<WORD> (buf->getPos() - varFileInfoStart));
    buf->ReplaceWORD(versionInfoStart,
            static_cast<WORD> (buf->getPos() - versionInfoStart));

    return true;
}

bool VersionInfoSwap::FillFixedFileInfo(VS_FIXEDFILEINFO *fxi) {
    wstring fileVersion;
    wstring productVersion;
    int ret;

    fileVersion = m_props[TEXT("FileVersion")];
    productVersion = m_props[TEXT("ProductVersion")];

    unsigned fv_1 = 0, fv_2 = 0, fv_3 = 0, fv_4 = 0;
    unsigned pv_1 = 0, pv_2 = 0, pv_3 = 0, pv_4 = 0;

    ret = _stscanf_s(fileVersion.c_str(),
            TEXT("%d.%d.%d.%d"), &fv_1, &fv_2, &fv_3, &fv_4);
    if (ret <= 0 || ret > 4) {
        return false;
    }

    ret = _stscanf_s(productVersion.c_str(),
            TEXT("%d.%d.%d.%d"), &pv_1, &pv_2, &pv_3, &pv_4);
    if (ret <= 0 || ret > 4) {
        return false;
    }

    fxi->dwSignature = 0xFEEF04BD;
    fxi->dwStrucVersion = 0x00010000;

    fxi->dwFileVersionMS = MAKELONG(fv_2, fv_1);
    fxi->dwFileVersionLS = MAKELONG(fv_4, fv_3);
    fxi->dwProductVersionMS = MAKELONG(pv_2, pv_1);
    fxi->dwProductVersionLS = MAKELONG(pv_4, pv_3);

    fxi->dwFileFlagsMask = 0;
    fxi->dwFileFlags = 0;
    fxi->dwFileOS = VOS_NT_WINDOWS32;

    wstring exeExt =
            m_launcher.substr(m_launcher.find_last_of(TEXT(".")));
    if (exeExt == TEXT(".exe")) {
        fxi->dwFileType = VFT_APP;
    } else if (exeExt == TEXT(".dll")) {
        fxi->dwFileType = VFT_DLL;
    } else {
        fxi->dwFileType = VFT_UNKNOWN;
    }
    fxi->dwFileSubtype = 0;

    fxi->dwFileDateLS = 0;
    fxi->dwFileDateMS = 0;

    return true;
}

/*
 * Adds new resource in the executable
 */
bool VersionInfoSwap::UpdateResource(LPVOID lpResLock, DWORD size) {

    HANDLE hUpdateRes;
    BOOL r;

    hUpdateRes = ::BeginUpdateResource(m_launcher.c_str(), FALSE);
    if (hUpdateRes == NULL) {
        return false;
    }

    r = ::UpdateResource(hUpdateRes,
            RT_VERSION,
            MAKEINTRESOURCE(VS_VERSION_INFO),
            MAKELANGID(LANG_NEUTRAL, SUBLANG_NEUTRAL),
            lpResLock,
            size);

    if (!r) {
        return false;
    }

    if (!::EndUpdateResource(hUpdateRes, FALSE)) {
        return false;
    }

    return true;
}
