/*
 * Copyright (c) 2011, 2013, Oracle and/or its affiliates. All rights reserved.
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

#import "JVMArgs.h"


#define kArgsFailure "JVMArgsFailure"

NSString *kArgumentsKey = @"Arguments";

NSString *kClassPathKey = @"ClassPath";
#ifdef __i386__
NSString *kArchClassPathKey = @"ClassPath.i386";
#elif __x86_64__
NSString *kArchClassPathKey = @"ClassPath.x86_64";
#endif

NSString *kVMOptionsKey = @"VMOptions";
#ifdef __i386__
NSString *kArchVMOptionsKey = @"VMOptions.i386";
#elif __x86_64__
NSString *kArchVMOptionsKey = @"VMOptions.x86_64";
#endif


@implementation JVMArgs

@synthesize jreBundle;
@synthesize preferredJVMLib;
@synthesize vm_args;
@synthesize startOnFirstThread;
@synthesize debug;

@synthesize appInfo;
@synthesize jvmInfo;

@synthesize userHome;
@synthesize appPackage;
@synthesize javaRoot;

- (void) dealloc {
    self.jreBundle = nil;
    if (self.preferredJVMLib) free(self.preferredJVMLib);

    self.appInfo = nil;
    self.jvmInfo = nil;

    self.userHome = nil;
    self.appPackage = nil;
    self.javaRoot = nil;

    [super dealloc];
}


NSString *GetJavaRoot(NSDictionary *jvmInfoDict) {
    NSObject *javaRoot = [jvmInfoDict objectForKey:@"$JAVAROOT"];
    if (![javaRoot isKindOfClass:[NSString class]]) return @"$APP_PACKAGE/Contents/Java";
    return (NSString *)javaRoot;
}

// Replaces occurances of $JAVAROOT, $APP_PACKAGE, and $USER_HOME
- (NSString *) expandMacros:(NSString *)str {
    if ([str rangeOfString:@"$JAVAROOT"].length == 0 && [str rangeOfString:@"$APP_PACKAGE"].length == 0 && [str rangeOfString:@"$USER_HOME"].length == 0) return str;

    // expand $JAVAROOT first, because it can contain $APP_PACKAGE
    NSMutableString *mutable = [str mutableCopy];
    [mutable replaceOccurrencesOfString:@"$JAVAROOT" withString:javaRoot options:0 range:NSMakeRange(0, [str length])];
    [mutable replaceOccurrencesOfString:@"$APP_PACKAGE" withString:appPackage options:0 range:NSMakeRange(0, [str length])];
    [mutable replaceOccurrencesOfString:@"$USER_HOME" withString:userHome options:0 range:NSMakeRange(0, [str length])];
    return mutable;
}

- (NSArray *) arrayFrom:(id) obj delimitedBy:(NSString *)delimiter withErrKey:(NSString *)key {
    if (obj == nil) return nil;
    if ([obj isKindOfClass:[NSArray class]]) return obj;
    if (![obj isKindOfClass:[NSString class]]) {
        [NSException raise:@kArgsFailure format:@"%@", [NSString stringWithFormat:@"Failed to find '%@' array in JVMInfo Info.plist"]];
    }

    // split
    return [(NSString *)obj componentsSeparatedByString:delimiter];
}

- (void) buildArgsForBundle:(NSBundle *)appBundle argc:(int)argc argv:(char *[])argv {
    // for verbose logging
    self.debug = NULL != getenv("JAVA_LAUNCHER_VERBOSE");

    self.appInfo = [appBundle infoDictionary];

    // all apps must have a JVMInfo dictionary inside their Info.plist
    self.jvmInfo = [[self.appInfo objectForKey:@"JVMInfo"] mutableCopy];
    if (![jvmInfo isKindOfClass:[NSDictionary class]]) {
        [NSException raise:@kArgsFailure format:@"Failed to find 'JVMInfo' dictionary in Info.plist"];
    }

    // initialize macro expansion values
    self.userHome = NSHomeDirectory();
    self.appPackage = [appBundle bundlePath];
    self.javaRoot = GetJavaRoot(jvmInfo);
    self.javaRoot = [self expandMacros:self.javaRoot]; // dereference $APP_PACKAGE

    // if the 'Arguments' key is defined, those override the ones that came into main()
    NSArray *jvmInfoArgs = [jvmInfo valueForKey:kArgumentsKey];
    if (jvmInfoArgs != nil) {
        // substitute all the variables in the 'Arguments' array/string
        jvmInfoArgs = [self arrayFrom:jvmInfoArgs delimitedBy:@" " withErrKey:kArgumentsKey];
        NSMutableArray *arguments = [NSMutableArray arrayWithCapacity:[jvmInfoArgs count]];
        [jvmInfoArgs enumerateObjectsUsingBlock:^(id obj, NSUInteger idx, BOOL *stop) {
            [arguments replaceObjectAtIndex:idx withObject:[self expandMacros:[obj description]]];
        }];
        [jvmInfo setObject:arguments forKey:kArgumentsKey];
    } else if (argc != 0) {
        // put the (macro expanded) args to main() in an NSArray
        NSMutableArray *arguments = [NSMutableArray arrayWithCapacity:argc];
        for (int i = 0; i < argc; i++) {
            [arguments addObject:[self expandMacros:[NSString stringWithUTF8String:(argv[i])]]];
        }
        [jvmInfo setObject:arguments forKey:kArgumentsKey];
    }

    // all JVMInfo's must have a JRE or JDK key
    NSString *jreBundleName = [jvmInfo objectForKey:@"JRE"];
    if (!jreBundleName) jreBundleName = [jvmInfo objectForKey:@"JDK"];
    if (![jreBundleName isKindOfClass:[NSString class]]) {
        [NSException raise:@kArgsFailure format:@"Failed to find 'JRE' or 'JDK' string in Info.plist JVMInfo"];
    }

    // the JRE/JDK must be loadable from the ($APP_PACKAGE)/Contents/PlugIns/ directory
    NSURL *jreBundleURL = [[appBundle builtInPlugInsURL] URLByAppendingPathComponent:jreBundleName];
    self.jreBundle = [NSBundle bundleWithURL:jreBundleURL];
    if (!self.jreBundle) {
        [NSException raise:@kArgsFailure format:@"Failed to find JRE/JDK at: %@", jreBundleURL];
    }

    // if the app prefers 'client' or 'server', use the JVM key
    NSString *JVMLib = [jvmInfo objectForKey:@"JVM"];
    if (JVMLib != nil) self.preferredJVMLib = strdup([JVMLib UTF8String]);

    // sniff for StartOnFirstThread
    if ([[jvmInfo objectForKey:@"StartOnFirstThread"] boolValue]) {
        self.startOnFirstThread = YES;
    } else if ([[jvmInfo objectForKey:@"StartOnMainThread"] boolValue]) {
        // for key compatibility with the Apple JavaApplicationStub's 'Java' dictionary
        self.startOnFirstThread = YES;
    }

    // add $JAVAROOT directory to the JNI library search path
    setenv("JAVA_LIBRARY_PATH", [javaRoot UTF8String], 1);

    // 'WorkingDirectory' key changes current working directory
    NSString *javaWorkingDir = [jvmInfo objectForKey:@"WorkingDirectory"];
    if (javaWorkingDir == nil) javaWorkingDir = @"$APP_PACKAGE/..";
    javaWorkingDir = [self expandMacros:javaWorkingDir];
    if (chdir([javaWorkingDir UTF8String]) == -1) {
        NSLog(@kArgsFailure " chdir() failed, could not change the current working directory to %s\n", [javaWorkingDir UTF8String]);
    }

    NSMutableArray *classpath = [NSMutableArray array];

    // 'Jar' key sets exactly one classpath entry
    NSString *jarFile = [jvmInfo objectForKey:@"Jar"];
    if (jarFile != nil) {
        [jvmInfo setObject:[self expandMacros:jarFile] forKey:@"Jar"];
        [classpath addObject:jarFile];
    }

    // 'ClassPath' key allows arbitrary classpath
    [classpath addObjectsFromArray:[self arrayFrom:[jvmInfo objectForKey:kClassPathKey] delimitedBy:@":" withErrKey:kClassPathKey]];
    [classpath addObjectsFromArray:[self arrayFrom:[jvmInfo objectForKey:kArchClassPathKey] delimitedBy:@":" withErrKey:kArchClassPathKey]];

    // Sum up all the classpath entries into one big JVM arg
    NSMutableString *classpathOption = [NSMutableString stringWithString:@"-Djava.class.path="];
    [classpath enumerateObjectsUsingBlock:^(id obj, NSUInteger idx, BOOL *stop) {
        if (idx > 1) [classpathOption appendString:@":"];
        [classpathOption appendString:obj];
    }];

    NSMutableArray *jvmOptions = [NSMutableArray arrayWithObject:classpathOption];

    // 'VMOptions' key allows arbitary VM start up options
    [jvmOptions addObjectsFromArray:[self arrayFrom:[jvmInfo objectForKey:kVMOptionsKey] delimitedBy:@" " withErrKey:kVMOptionsKey]];
    [jvmOptions addObjectsFromArray:[self arrayFrom:[jvmInfo objectForKey:kArchVMOptionsKey] delimitedBy:@" " withErrKey:kArchVMOptionsKey]];

    // 'Properties' key is a sub-dictionary transfered to initial System.properties
    NSDictionary *properties = [jvmInfo objectForKey:@"Properties"];
    if (properties != nil) {
        if (![properties isKindOfClass:[NSDictionary class]]) {
            [NSException raise:@kArgsFailure format:@"Failed to find 'Properties' dictionary in Info.plist JVMInfo"];
        }

        [properties enumerateKeysAndObjectsUsingBlock:^(id key, id obj, BOOL *stop) {
            [jvmOptions addObject:[NSString stringWithFormat:@"-D%@=%@", key, obj]];
        }];
    }

    // build the real JVM init args struct
    vm_args.version = JNI_VERSION_1_6;
    vm_args.ignoreUnrecognized = JNI_TRUE;
    vm_args.nOptions = [jvmOptions count];
    vm_args.options = calloc(vm_args.nOptions, sizeof(JavaVMOption));
    [jvmOptions enumerateObjectsUsingBlock:^(id obj, NSUInteger idx, BOOL *stop) {
        NSString *expanded = [self expandMacros:[obj description]]; // turn everything into a string, and expand macros
        vm_args.options[idx].optionString = strdup([expanded UTF8String]);
    }];
}

+ (JVMArgs *)jvmArgsForBundle:(NSBundle *)appBundle argc:(int)argc argv:(char *[])argv {
    JVMArgs *args = [JVMArgs new];
    [args buildArgsForBundle:appBundle argc:argc argv:argv];
    return [args autorelease];
}

@end
