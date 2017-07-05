/*
 * Copyright (c) 2011, 2012, Oracle and/or its affiliates. All rights reserved.
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

#import "JVMArgs.h"
#import "JavaAppLauncher.h"


static void dummyTimer(CFRunLoopTimerRef timer, void *info) {}
static void ParkEventLoop() {
    // RunLoop needs at least one source, and 1e20 is pretty far into the future
    CFRunLoopTimerRef t = CFRunLoopTimerCreate(kCFAllocatorDefault, 1.0e20, 0.0, 0, 0, dummyTimer, NULL);
    CFRunLoopAddTimer(CFRunLoopGetCurrent(), t, kCFRunLoopDefaultMode);
    CFRelease(t);

    // Park this thread in the main run loop.
    int32_t result;
    do {
        result = CFRunLoopRunInMode(kCFRunLoopDefaultMode, 1.0e20, false);
    } while (result != kCFRunLoopRunFinished);
}

int main(int argc, char *argv[]) {
    NSAutoreleasePool *pool = [NSAutoreleasePool new];

    @try {
        NSBundle *mainBundle = [NSBundle mainBundle];

        // pick apart the Info.plist, and release all the temporary objects
        NSAutoreleasePool *argParsingPool = [NSAutoreleasePool new];
        JVMArgs *args = [JVMArgs jvmArgsForBundle:mainBundle argc:argc argv:argv];
        JavaAppLauncher *launcher = [JavaAppLauncher new];
        launcher.args = args;
        [argParsingPool drain];

        // kick off a new thread to instantiate the JVM on
        NSThread *thread = [[NSThread alloc] initWithTarget:launcher selector:@selector(findAndLoadJVM) object:nil];
        struct rlimit limit;
        int err = getrlimit(RLIMIT_STACK, &limit);
        if (err == 0 && limit.rlim_cur != 0LL) {
            [thread setStackSize:limit.rlim_cur];
        }
        [thread start];
        [thread release];

        [launcher release];

        ParkEventLoop();

    } @catch (NSException *e) {
        NSLog(@"%@: %@", e, [e callStackSymbols]);
    }

    [pool drain];

    return 0;
}
