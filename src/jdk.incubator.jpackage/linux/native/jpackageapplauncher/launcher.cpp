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

#include <dlfcn.h>
#include <locale.h>
#include <string>
#include <libgen.h>
#include <stdio.h>
#include <unistd.h>


typedef bool (*start_launcher)(int argc, char* argv[]);
typedef void (*stop_launcher)();

#define MAX_PATH 1024

std::string GetProgramPath() {
    ssize_t len = 0;
    std::string result;
    char buffer[MAX_PATH] = {0};

    if ((len = readlink("/proc/self/exe", buffer, MAX_PATH - 1)) != -1) {
        buffer[len] = '\0';
        result = buffer;
    }

    return result;
}

int main(int argc, char *argv[]) {
    int result = 1;
    setlocale(LC_ALL, "en_US.utf8");
    void* library = NULL;

    {
        std::string programPath = GetProgramPath();
        std::string libraryName = dirname((char*)programPath.c_str());
        libraryName += "/../lib/libapplauncher.so";
        library = dlopen(libraryName.c_str(), RTLD_LAZY);

        if (library == NULL) {
            fprintf(stderr, "dlopen failed: %s\n", dlerror());
            fprintf(stderr, "%s not found.\n", libraryName.c_str());
        }
    }

    if (library != NULL) {
        start_launcher start = (start_launcher)dlsym(library, "start_launcher");
        stop_launcher stop = (stop_launcher)dlsym(library, "stop_launcher");

        if (start != NULL && stop != NULL) {
            if (start(argc, argv) == true) {
                result = 0;
                stop();
            }
        } else {
            fprintf(stderr, "cannot find start_launcher and stop_launcher in libapplauncher.so");
        }

        dlclose(library);
    }


    return result;
}
