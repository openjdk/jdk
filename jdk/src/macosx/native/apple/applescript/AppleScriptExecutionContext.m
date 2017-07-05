/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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

#import "AppleScriptExecutionContext.h"

#import <Carbon/Carbon.h>

#import "AS_NS_ConversionUtils.h"


@implementation AppleScriptExecutionContext

@synthesize source;
@synthesize context;
@synthesize error;
@synthesize returnValue;

- (id) init:(NSString *)sourceIn context:(id)contextIn {
    self = [super init];
    if (!self) return self;

    self.source = sourceIn;
    self.context = contextIn;
    self.returnValue = nil;
    self.error = nil;

    return self;
}

- (id) initWithSource:(NSString *)sourceIn context:(NSDictionary *)contextIn {
    self = [self init:sourceIn context:contextIn];
    isFile = NO;
    return self;
}

- (id) initWithFile:(NSString *)filenameIn context:(NSDictionary *)contextIn {
    self = [self init:filenameIn context:contextIn];
    isFile = YES;
    return self;
}

- (void) dealloc {
    self.source = nil;
    self.context = nil;
    self.returnValue = nil;
    self.error = nil;

    [super dealloc];
}

- (NSAppleScript *) scriptFromURL {
    NSURL *url = [NSURL URLWithString:source];
    NSDictionary *err = nil;
    NSAppleScript *script = [[[NSAppleScript alloc] initWithContentsOfURL:url error:(&err)] autorelease];
    if (err != nil) self.error = err;
    return script;
}

- (NSAppleScript *) scriptFromSource {
    return [[[NSAppleScript alloc] initWithSource:source] autorelease];
}

- (NSAppleEventDescriptor *) functionInvocationEvent {
    NSString *function = [[context objectForKey:@"javax_script_function"] description];
    if (function == nil) return nil;

    // wrap the arg in an array if it is not already a list
    id args = [context objectForKey:@"javax_script_argv"];
    if (![args isKindOfClass:[NSArray class]]) {
        args = [NSArray arrayWithObjects:args, nil];
    }

    // triangulate our target
    int pid = [[NSProcessInfo processInfo] processIdentifier];
    NSAppleEventDescriptor* targetAddress = [NSAppleEventDescriptor descriptorWithDescriptorType:typeKernelProcessID
                                                                                           bytes:&pid
                                                                                          length:sizeof(pid)];

    // create the event to call a subroutine in the script
    NSAppleEventDescriptor* event = [[NSAppleEventDescriptor alloc] initWithEventClass:kASAppleScriptSuite
                                                                               eventID:kASSubroutineEvent
                                                                      targetDescriptor:targetAddress
                                                                              returnID:kAutoGenerateReturnID
                                                                         transactionID:kAnyTransactionID];

    // set up the handler
    NSAppleEventDescriptor* subroutineDescriptor = [NSAppleEventDescriptor descriptorWithString:[function lowercaseString]];
    [event setParamDescriptor:subroutineDescriptor forKeyword:keyASSubroutineName];

    // set up the arguments
    [event setParamDescriptor:[args aeDescriptorValue] forKeyword:keyDirectObject];

    return [event autorelease];
}

- (void) invoke {
    // create our script
    NSAppleScript *script = isFile ? [self scriptFromURL] : [self scriptFromSource];
    if (self.error != nil) return;

    // find out if we have a subroutine to call
    NSAppleEventDescriptor *fxnInvkEvt = [self functionInvocationEvent];

    // exec!
    NSAppleEventDescriptor *desc = nil;
    NSDictionary *err = nil;
    if (fxnInvkEvt == nil) {
        desc = [script executeAndReturnError:(&err)];
    } else {
        desc = [script executeAppleEvent:fxnInvkEvt error:(&err)];
    }

    // if we encountered an exception, stash and bail
    if (err != nil) {
        self.error = err;
        return;
    }

    // convert to NSObjects, and return in ivar
    self.returnValue = [desc objCObjectValue];
}

- (id) invokeWithEnv:(JNIEnv *)env {
    BOOL useAnyThread = [@"any-thread" isEqual:[context valueForKey:@"javax_script_threading"]];

    // check if we are already on the AppKit thread, if desired
    if(pthread_main_np() || useAnyThread) {
        [self invoke];
    } else {
        [JNFRunLoop performOnMainThread:@selector(invoke) on:self withObject:nil waitUntilDone:YES];
    }

    // if we have an exception parked in our ivar, snarf the message (if there is one), and toss a ScriptException
    if (self.error != nil) {
        NSString *asErrString = [self.error objectForKey:NSAppleScriptErrorMessage];
        if (!asErrString) asErrString = @"AppleScriptEngine failed to execute script."; // usually when we fail to load a file
        [JNFException raise:env as:"javax/script/ScriptException" reason:[asErrString UTF8String]];
    }

    return self.returnValue;
}

@end
