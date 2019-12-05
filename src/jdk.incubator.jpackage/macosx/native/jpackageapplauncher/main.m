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

#import <Cocoa/Cocoa.h>
#include <dlfcn.h>
#include <unistd.h>

typedef bool (*start_launcher)(int argc, char* argv[]);
typedef void (*stop_launcher)();

int main(int argc, char *argv[]) {
#if !__has_feature(objc_arc)
    NSAutoreleasePool *pool = [[NSAutoreleasePool alloc] init];
#endif

    int result = 1;

    @try {
        setlocale(LC_ALL, "en_US.utf8");

        NSBundle *mainBundle = [NSBundle mainBundle];
        NSString *mainBundlePath = [mainBundle bundlePath];
        NSString *libraryName = [mainBundlePath stringByAppendingPathComponent:@"Contents/MacOS/libapplauncher.dylib"];

        void* library = dlopen([libraryName UTF8String], RTLD_LAZY);

        if (library == NULL) {
            NSLog(@"%@ not found.\n", libraryName);
        }

        if (library != NULL) {
            start_launcher start =
                    (start_launcher)dlsym(library, "start_launcher");
            stop_launcher stop =
                    (stop_launcher)dlsym(library, "stop_launcher");

            if (start != NULL && stop != NULL) {
                if (start(argc, argv) == true) {
                    result = 0;
                    stop();
                }
            } else if (start == NULL) {
                NSLog(@"start_launcher not found in %@.\n", libraryName);
            } else {
                NSLog(@"stop_launcher not found in %@.\n", libraryName);
            }
            dlclose(library);
        }
    } @catch (NSException *exception) {
        NSLog(@"%@: %@", exception, [exception callStackSymbols]);
        result = 1;
    }

#if !__has_feature(objc_arc)
    [pool drain];
#endif

    return result;
}
