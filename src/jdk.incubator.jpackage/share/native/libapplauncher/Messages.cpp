/*
 * Copyright (c) 2014, 2019, Oracle and/or its affiliates. All rights reserved.
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

#include "Messages.h"
#include "Platform.h"
#include "FilePath.h"
#include "Helpers.h"
#include "Macros.h"
#include "JavaVirtualMachine.h"

Messages::Messages(void) {
    FMessages.SetReadOnly(false);
    FMessages.SetValue(LIBRARY_NOT_FOUND, _T("Failed to find library."));
    FMessages.SetValue(FAILED_CREATING_JVM, _T("Failed to create JVM"));
    FMessages.SetValue(FAILED_LOCATING_JVM_ENTRY_POINT,
            _T("Failed to locate JLI_Launch"));
    FMessages.SetValue(NO_MAIN_CLASS_SPECIFIED, _T("No main class specified"));
    FMessages.SetValue(METHOD_NOT_FOUND, _T("No method %s in class %s."));
    FMessages.SetValue(CLASS_NOT_FOUND, _T("Class %s not found."));
    FMessages.SetValue(ERROR_INVOKING_METHOD, _T("Error invoking method."));
    FMessages.SetValue(APPCDS_CACHE_FILE_NOT_FOUND,
            _T("Error: AppCDS cache does not exists:\n%s\n"));
}

Messages& Messages::GetInstance() {
    static Messages instance;
    // Guaranteed to be destroyed. Instantiated on first use.
    return instance;
}

Messages::~Messages(void) {
}

TString Messages::GetMessage(const TString Key) {
    TString result;
    FMessages.GetValue(Key, result);
    Macros& macros = Macros::GetInstance();
    result = macros.ExpandMacros(result);
    return result;
}
