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

#include "Platform.h"
#include "PlatformString.h"
#include "FilePath.h"
#include "PropertyFile.h"
#include "JavaVirtualMachine.h"
#include "Package.h"
#include "Macros.h"
#include "Messages.h"

#include <stdio.h>
#include <signal.h>
#include <stdlib.h>

/*
This is the app launcher program for application packaging on Windows, Mac,
    and Linux.

Basic approach:
  - Launcher (jpackageapplauncher) is executable that loads
    applauncher.dll/libapplauncher.dylib/libapplauncher.so
    and calls start_launcher below.
  - Reads app/package.cfg or Info.plist or app/<appname>.cfg for application
    launch configuration (package.cfg is property file).
  - Load Java with requested Java settings (bundled client Java if availble,
    server or installed Java otherwise).
  - Wait for Java to exit and then exit from Main
  - To debug application by passing command line argument.
  - Application folder is added to the library path (so LoadLibrary()) works.

Limitations and future work:
  - Running Java code in primordial thread may cause problems
    (example: can not use custom stack size).
    Solution used by java launcher is to create a new thread to invoke Java.
    See CR 6316197 for more information.
*/

extern "C" {

    JNIEXPORT bool start_launcher(int argc, TCHAR* argv[]) {
        bool result = false;
        bool parentProcess = true;

        // Platform must be initialize first.
        Platform& platform = Platform::GetInstance();

        try {
            for (int index = 0; index < argc; index++) {
                TString argument = argv[index];

                if (argument == _T("-Xappcds:generatecache")) {
                    platform.SetAppCDSState(cdsGenCache);
                }
                else if (argument == _T("-Xappcds:off")) {
                    platform.SetAppCDSState(cdsDisabled);
                }
                else if (argument == _T("-Xapp:child")) {
                    parentProcess = false;
                }
            }

            // Package must be initialized after Platform is fully initialized.
            Package& package = Package::GetInstance();
            Macros::Initialize();
            package.SetCommandLineArguments(argc, argv);

            switch (platform.GetAppCDSState()) {
                case cdsDisabled:
                case cdsUninitialized:
                case cdsEnabled: {
                    break;
                }

                case cdsGenCache: {
                    TString cacheDirectory = package.GetAppCDSCacheDirectory();

                    if (FilePath::DirectoryExists(cacheDirectory) == false) {
                        FilePath::CreateDirectory(cacheDirectory, true);
                    } else {
                        TString cacheFileName =
                                package.GetAppCDSCacheFileName();
                        if (FilePath::FileExists(cacheFileName) == true) {
                            FilePath::DeleteFile(cacheFileName);
                        }
                    }

                    break;
                }

                case cdsAuto: {
                    TString cacheFileName = package.GetAppCDSCacheFileName();

                    if (parentProcess == true &&
                            FilePath::FileExists(cacheFileName) == false) {
                        AutoFreePtr<Process> process = platform.CreateProcess();
                        std::vector<TString> args;
                        args.push_back(_T("-Xappcds:generatecache"));
                        args.push_back(_T("-Xapp:child"));
                        process->Execute(
                                platform.GetModuleFileName(), args, true);

                        if (FilePath::FileExists(cacheFileName) == false) {
                            // Cache does not exist after trying to generate it,
                            // so run without cache.
                            platform.SetAppCDSState(cdsDisabled);
                            package.Clear();
                            package.Initialize();
                        }
                    }

                    break;
                }
            }

            // Validation
            switch (platform.GetAppCDSState()) {
                case cdsDisabled:
                case cdsGenCache: {
                    // Do nothing.
                    break;
                }

                case cdsEnabled:
                case cdsAuto: {
                    TString cacheFileName =
                            package.GetAppCDSCacheFileName();

                    if (FilePath::FileExists(cacheFileName) == false) {
                        Messages& messages = Messages::GetInstance();
                        TString message = PlatformString::Format(
                                messages.GetMessage(
                                APPCDS_CACHE_FILE_NOT_FOUND),
                                cacheFileName.data());
                        throw Exception(message);
                    }
                    break;
                }

                case cdsUninitialized: {
                    platform.ShowMessage(_T("Internal Error"));
                    break;
                }
            }

            // Run App
            result = RunVM();
        } catch (Exception &e) {
            platform.ShowMessage(e.GetMessage());
        }

        return result;
    }

    JNIEXPORT void stop_launcher() {
    }
}
