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

#include "Package.h"
#include "Executor.h"
#include "AppLauncher.h"
#include "ErrorHandling.h"


Package::Package(): type(Unknown) {
}


Package::Package(const PackageDesc& desc):
        type(static_cast<Type>(desc.type)),
        theName(desc.name ? desc.name : "") {
}


namespace {
class AppLauncherInitializer : public CommandOutputConsumer {
public:
    AppLauncherInitializer() {
    }

    virtual bool accept(const std::string& line) {
        if (appDir.empty()) {
            if (tstrings::endsWith(line, "/app")) {
                appDir = line;
            }
        }

        if (runtimeDir.empty()) {
            if (tstrings::endsWith(line, "/runtime")) {
                runtimeDir = line;
            }
        }

        return !appDir.empty() && !runtimeDir.empty();
    };

    void apply(AppLauncher& launcher) {
        launcher.setDefaultRuntimePath(runtimeDir);
        launcher.setAppDir(appDir);
    }

private:
    std::string appDir;
    std::string runtimeDir;
};

} // namespace

void Package::initAppLauncher(AppLauncher& appLauncher) const {
    AppLauncherInitializer consumer;
    int exitCode = -1;
    if (type == RPM) {
        exitCode = executeCommandLineAndReadStdout(
                { "rpm", "-ql", theName }, consumer);
    } else if (type == DEB) {
        exitCode = executeCommandLineAndReadStdout(
                { "dpkg", "-L", theName }, consumer);
    }

    if (exitCode == 0) {
        consumer.apply(appLauncher);
    }
}
