/*
 * Copyright (c) 2020, 2026, Oracle and/or its affiliates. All rights reserved.
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

#include "Executor.h"
#include "ErrorHandling.h"
#include "ExecCommand.h"


namespace {

int callbackAdapter(void* callback, char* str) {
    JP_TRY;
    if (reinterpret_cast<CommandOutputConsumer*>(callback)->accept(str)) {
        return EXEC_CALLBACK_IGNORE;
    } else {
        return EXEC_CALLBACK_USE;
    }
    JP_CATCH_ALL;
    return EXEC_CALLBACK_ERROR;
}

} // namespace

int executeCommandLineAndReadStdout(const tstring_array& cmd,
        CommandOutputConsumer& consumer) {

    std::vector<const char*> argv;
    tstring_array::const_iterator it = cmd.begin();
    tstring_array::const_iterator end = cmd.end();
    for (; it != end; ++it) {
        argv.push_back(it->c_str());
    }
    argv.push_back(NULL);

    return execCommand(argv.data(), callbackAdapter, &consumer);
}
