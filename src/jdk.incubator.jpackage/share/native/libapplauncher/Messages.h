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

#ifndef MESSAGES_H
#define MESSAGES_H

#include "PropertyFile.h"

#define LIBRARY_NOT_FOUND _T("library.not.found")
#define FAILED_CREATING_JVM _T("failed.creating.jvm")
#define FAILED_LOCATING_JVM_ENTRY_POINT _T("failed.locating.jvm.entry.point")
#define NO_MAIN_CLASS_SPECIFIED _T("no.main.class.specified")

#define METHOD_NOT_FOUND _T("method.not.found")
#define CLASS_NOT_FOUND _T("class.not.found")
#define ERROR_INVOKING_METHOD _T("error.invoking.method")

#define CONFIG_FILE_NOT_FOUND _T("config.file.not.found")

#define BUNDLED_JVM_NOT_FOUND _T("bundled.jvm.not.found")

#define APPCDS_CACHE_FILE_NOT_FOUND _T("appcds.cache.file.not.found")

class Messages {
private:
    PropertyFile FMessages;

    Messages(void);
public:
    static Messages& GetInstance();
    ~Messages(void);

    TString GetMessage(const TString Key);
};

#endif // MESSAGES_H
