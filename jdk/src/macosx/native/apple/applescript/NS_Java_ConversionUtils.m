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

#import "NS_Java_ConversionUtils.h"

#import <Cocoa/Cocoa.h>


@interface JavaAppleScriptBaseConverter : NSObject <JNFTypeCoercion>
@end

@interface JavaAppleScriptImageConverter : NSObject <JNFTypeCoercion>
@end

@interface JavaAppleScriptVersionConverter : NSObject <JNFTypeCoercion>
@end

@interface JavaAppleScriptNullConverter : NSObject <JNFTypeCoercion>
@end


@implementation JavaAppleScriptEngineCoercion

static JNFTypeCoercer *appleScriptCoercer = nil;

+ (JNFTypeCoercer *) coercer {
    if (appleScriptCoercer) return appleScriptCoercer;

    id asSpecificCoercions = [[JNFDefaultCoercions defaultCoercer] deriveCoercer];
    [asSpecificCoercions addCoercion:[[[JavaAppleScriptImageConverter alloc] init] autorelease] forNSClass:[NSImage class] javaClass:@"java/awt/Image"];
    [asSpecificCoercions addCoercion:[[[JavaAppleScriptVersionConverter alloc] init] autorelease] forNSClass:[NSAppleEventDescriptor class] javaClass:nil];
    [asSpecificCoercions addCoercion:[[[JavaAppleScriptNullConverter alloc] init] autorelease] forNSClass:[NSNull class] javaClass:nil];

    return appleScriptCoercer = [asSpecificCoercions retain];
}

@end


// [NSObject description] <-> java.lang.Object.toString()
@implementation JavaAppleScriptBaseConverter

// by default, bizzare NSObjects will have -description called on them, and passed back to Java like that
- (jobject) coerceNSObject:(id)obj withEnv:(JNIEnv *)env usingCoercer:(JNFTypeCoercion *)coercer {
    return JNFNSToJavaString(env, [obj description]);
}

// by default, bizzare Java objects will be toString()'d and passed to AppleScript like that
- (id) coerceJavaObject:(jobject)obj withEnv:(JNIEnv *)env usingCoercer:(JNFTypeCoercion *)coercer {
    return JNFObjectToString(env, obj);
}

@end


// NSImage <-> apple.awt.CImage
@implementation JavaAppleScriptImageConverter

static JNF_CLASS_CACHE(jc_CImage, "apple/awt/CImage");
static JNF_STATIC_MEMBER_CACHE(jm_CImage_getCreator, jc_CImage, "getCreator", "()Lapple/awt/CImage$Creator;");
static JNF_MEMBER_CACHE(jm_CImage_getNSImage, jc_CImage, "getNSImage", "()J");

static JNF_CLASS_CACHE(jc_CImage_Generator, "apple/awt/CImage$Creator");
static JNF_MEMBER_CACHE(jm_CImage_Generator_createImageFromPtr, jc_CImage_Generator, "createImage", "(J)Ljava/awt/image/BufferedImage;");
static JNF_MEMBER_CACHE(jm_CImage_Generator_createImageFromImg, jc_CImage_Generator, "createImage", "(Ljava/awt/Image;)Lapple/awt/CImage;");

- (jobject) coerceNSObject:(id)obj withEnv:(JNIEnv *)env usingCoercer:(JNFTypeCoercion *)coercer {
    NSImage *img = (NSImage *)obj;
    CFRetain(img);
    jobject creator = JNFCallStaticObjectMethod(env, jm_CImage_getCreator);
    jobject jobj = JNFCallObjectMethod(env, creator, jm_CImage_Generator_createImageFromPtr, ptr_to_jlong(img));
    return jobj;
}

- (id) coerceJavaObject:(jobject)obj withEnv:(JNIEnv *)env usingCoercer:(JNFTypeCoercion *)coercer {
    jobject cimage = obj;
    if (!JNFIsInstanceOf(env, obj, &jc_CImage)) {
        jobject creator = JNFCallStaticObjectMethod(env, jm_CImage_getCreator);
        cimage = JNFCallObjectMethod(env, creator, jm_CImage_Generator_createImageFromImg, obj);
    }

    jlong nsImagePtr = JNFCallLongMethod(env, cimage, jm_CImage_getNSImage);

    NSImage *img = (NSImage *)jlong_to_ptr(nsImagePtr);
    return [[img retain] autorelease];
}

@end


// NSAppleEventDescriptor('vers') -> java.lang.String
@implementation JavaAppleScriptVersionConverter

- (jobject) coerceNSObject:(id)obj withEnv:(JNIEnv *)env usingCoercer:(JNFTypeCoercion *)coercer {
    NSAppleEventDescriptor *desc = (NSAppleEventDescriptor *)obj;

    const AEDesc *aeDesc = [desc aeDesc];
    if (aeDesc->descriptorType == typeNull) {
        return NULL;
    }

    return JNFNSToJavaString(env, [obj description]);
}

- (id) coerceJavaObject:(jobject)obj withEnv:(JNIEnv *)env usingCoercer:(JNFTypeCoercion *)coercer {
    return nil; // there is no Java object that represents a "version"
}

@end


// NSNull <-> null
@implementation JavaAppleScriptNullConverter

- (jobject) coerceNSObject:(id)obj withEnv:(JNIEnv *)env usingCoercer:(JNFTypeCoercion *)coercer {
    return NULL;
}

- (id) coerceJavaObject:(jobject)obj withEnv:(JNIEnv *)env usingCoercer:(JNFTypeCoercion *)coercer {
    return nil;
}

@end
