/*
 * Copyright (c) 2011, 2014, Oracle and/or its affiliates. All rights reserved.
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

#import "CClipboard.h"
#import "CDataTransferer.h"
#import "ThreadUtilities.h"
#import "jni_util.h" 
#import <Cocoa/Cocoa.h>
#import <JavaNativeFoundation/JavaNativeFoundation.h>

static CClipboard *sClipboard = nil;

//
// CClipboardUpdate is used for mulitple calls to setData that happen before
// the model and AppKit can get back in sync.
//

@interface CClipboardUpdate : NSObject {
    NSData *fData;
    NSString *fFormat;
}

- (id)initWithData:(NSData *)inData withFormat:(NSString *)inFormat;
- (NSData *)data;
- (NSString *)format;

@end

@implementation CClipboardUpdate

- (id)initWithData:(NSData *)inData withFormat:(NSString *)inFormat
{
    self = [super init];

    if (self != nil) {
        fData = [inData retain];
        fFormat = [inFormat retain];
    }

    return self;
}

- (void)dealloc
{
    [fData release];
    fData = nil;

    [fFormat release];
    fFormat = nil;

    [super dealloc];
}

- (NSData *)data {
    return fData;
}

- (NSString *)format {
    return fFormat;
}
@end

@implementation CClipboard

// Clipboard creation is synchronized at the Java level.
+ (CClipboard *) sharedClipboard
{
    if (sClipboard == nil) {
        sClipboard = [[CClipboard alloc] init];
        [[NSNotificationCenter defaultCenter] addObserver:sClipboard selector: @selector(checkPasteboard:)
                                                     name: NSApplicationDidBecomeActiveNotification
                                                   object: nil];
    }

    return sClipboard;
}

- (id) init
{
    self = [super init];

    if (self != nil) {
        fChangeCount = [[NSPasteboard generalPasteboard] changeCount];
    }

    return self;
}

- (void) javaDeclareTypes:(NSArray *)inTypes withOwner:(jobject)inClipboard jniEnv:(JNIEnv *)inEnv {

    @synchronized(self) {
        if (inClipboard != NULL) {
            if (fClipboardOwner != NULL) {
                JNFDeleteGlobalRef(inEnv, fClipboardOwner);
            }
            fClipboardOwner = JNFNewGlobalRef(inEnv, inClipboard);
        }
    }
    [ThreadUtilities performOnMainThread:@selector(_nativeDeclareTypes:) on:self withObject:inTypes waitUntilDone:YES];
}

- (void) _nativeDeclareTypes:(NSArray *)inTypes {
    AWT_ASSERT_APPKIT_THREAD;

    fChangeCount = [[NSPasteboard generalPasteboard] declareTypes:inTypes owner:self];
}


- (NSArray *) javaGetTypes {

    NSMutableArray *args = [NSMutableArray arrayWithCapacity:1];
    [ThreadUtilities performOnMainThread:@selector(_nativeGetTypes:) on:self withObject:args waitUntilDone:YES];
    return [args lastObject];
}

- (void) _nativeGetTypes:(NSMutableArray *)args {
    AWT_ASSERT_APPKIT_THREAD;

    [args addObject:[[NSPasteboard generalPasteboard] types]];
}

- (void) javaSetData:(NSData *)inData forType:(NSString *) inFormat {

    CClipboardUpdate *newUpdate = [[CClipboardUpdate alloc] initWithData:inData withFormat:inFormat];
    [ThreadUtilities performOnMainThread:@selector(_nativeSetData:) on:self withObject:newUpdate waitUntilDone:YES];
    [newUpdate release];
}

- (void) _nativeSetData:(CClipboardUpdate *)newUpdate {
    AWT_ASSERT_APPKIT_THREAD;

    [[NSPasteboard generalPasteboard] setData:[newUpdate data] forType:[newUpdate format]];
}

- (NSData *) javaGetDataForType:(NSString *) inFormat {

    NSMutableArray *args = [NSMutableArray arrayWithObject:inFormat];
    [ThreadUtilities performOnMainThread:@selector(_nativeGetDataForType:) on:self withObject:args waitUntilDone:YES];
    return [args lastObject];
}

- (void) _nativeGetDataForType:(NSMutableArray *) args {
    AWT_ASSERT_APPKIT_THREAD;

    NSData *returnValue = [[NSPasteboard generalPasteboard] dataForType:[args objectAtIndex:0]];

    if (returnValue) [args replaceObjectAtIndex:0 withObject:returnValue];
    else [args removeLastObject];
}

- (void) checkPasteboard:(id)application {
    AWT_ASSERT_APPKIT_THREAD;
    
    // This is called via NSApplicationDidBecomeActiveNotification.
    
    // If the change count on the general pasteboard is different than when we set it
    // someone else put data on the clipboard.  That means the current owner lost ownership.
    NSInteger newChangeCount = [[NSPasteboard generalPasteboard] changeCount];
    
    if (fChangeCount != newChangeCount) {
        fChangeCount = newChangeCount;    

        // Notify that the content might be changed
        static JNF_CLASS_CACHE(jc_CClipboard, "sun/lwawt/macosx/CClipboard");
        static JNF_STATIC_MEMBER_CACHE(jm_contentChanged, jc_CClipboard, "notifyChanged", "()V");
        JNIEnv *env = [ThreadUtilities getJNIEnv];
        JNFCallStaticVoidMethod(env, jm_contentChanged);

        // If we have a Java pasteboard owner, tell it that it doesn't own the pasteboard anymore.
        static JNF_MEMBER_CACHE(jm_lostOwnership, jc_CClipboard, "notifyLostOwnership", "()V");
        @synchronized(self) {
            if (fClipboardOwner) {
                JNIEnv *env = [ThreadUtilities getJNIEnv];
                JNFCallVoidMethod(env, fClipboardOwner, jm_lostOwnership); // AWT_THREADING Safe (event)
                JNFDeleteGlobalRef(env, fClipboardOwner);
                fClipboardOwner = NULL;
            }
        }
    }
}

@end

/*
 * Class:     sun_lwawt_macosx_CClipboard
 * Method:    declareTypes
 * Signature: ([JLsun/awt/datatransfer/SunClipboard;)V
*/
JNIEXPORT void JNICALL Java_sun_lwawt_macosx_CClipboard_declareTypes
(JNIEnv *env, jobject inObject, jlongArray inTypes, jobject inJavaClip)
{
JNF_COCOA_ENTER(env);

    jint i;
    jint nElements = (*env)->GetArrayLength(env, inTypes);
    NSMutableArray *formatArray = [NSMutableArray arrayWithCapacity:nElements];
    jlong *elements = (*env)->GetPrimitiveArrayCritical(env, inTypes, NULL);

    for (i = 0; i < nElements; i++) {
        NSString *pbFormat = formatForIndex(elements[i]);
        if (pbFormat)
            [formatArray addObject:pbFormat];
    }

    (*env)->ReleasePrimitiveArrayCritical(env, inTypes, elements, JNI_ABORT);
    [[CClipboard sharedClipboard] javaDeclareTypes:formatArray withOwner:inJavaClip jniEnv:env];
JNF_COCOA_EXIT(env);
}

/*
 * Class:     sun_lwawt_macosx_CClipboard
 * Method:    setData
 * Signature: ([BJ)V
*/
JNIEXPORT void JNICALL Java_sun_lwawt_macosx_CClipboard_setData
(JNIEnv *env, jobject inObject, jbyteArray inBytes, jlong inFormat)
{
    if (inBytes == NULL) {
        return;
    }

JNF_COCOA_ENTER(env);
    jint nBytes = (*env)->GetArrayLength(env, inBytes);
    jbyte *rawBytes = (*env)->GetPrimitiveArrayCritical(env, inBytes, NULL);
    CHECK_NULL(rawBytes);
    NSData *bytesAsData = [NSData dataWithBytes:rawBytes length:nBytes];
    (*env)->ReleasePrimitiveArrayCritical(env, inBytes, rawBytes, JNI_ABORT);
    NSString *format = formatForIndex(inFormat);
    [[CClipboard sharedClipboard] javaSetData:bytesAsData forType:format];
JNF_COCOA_EXIT(env);
}

/*
 * Class:     sun_lwawt_macosx_CClipboard
 * Method:    getClipboardFormats
 * Signature: (J)[J
     */
JNIEXPORT jlongArray JNICALL Java_sun_lwawt_macosx_CClipboard_getClipboardFormats
(JNIEnv *env, jobject inObject)
{
    jlongArray returnValue = NULL;
JNF_COCOA_ENTER(env);

    NSArray *dataTypes = [[CClipboard sharedClipboard] javaGetTypes];
    NSUInteger nFormats = [dataTypes count];
    NSUInteger knownFormats = 0;
    NSUInteger i;

    // There can be any number of formats on the general pasteboard.  Find out which ones
    // we know about (i.e., live in the flavormap.properties).
    for (i = 0; i < nFormats; i++) {
        NSString *format = (NSString *)[dataTypes objectAtIndex:i];
        if (indexForFormat(format) != -1)
            knownFormats++;
    }

    returnValue = (*env)->NewLongArray(env, knownFormats);
    if (returnValue == NULL) {
        return NULL;
    }

    if (knownFormats == 0) {
        return returnValue;
    }

    // Now go back and map the formats we found back to Java indexes.
    jboolean isCopy;
    jlong *lFormats = (*env)->GetLongArrayElements(env, returnValue, &isCopy);
    jlong *saveFormats = lFormats;

    for (i = 0; i < nFormats; i++) {
        NSString *format = (NSString *)[dataTypes objectAtIndex:i];
        jlong index = indexForFormat(format);

        if (index != -1) {
            *lFormats = index;
            lFormats++;
        }
    }

    (*env)->ReleaseLongArrayElements(env, returnValue, saveFormats, JNI_COMMIT);
JNF_COCOA_EXIT(env);
    return returnValue;
}

/*
 * Class:     sun_lwawt_macosx_CClipboard
 * Method:    getClipboardData
 * Signature: (JJ)[B
     */
JNIEXPORT jbyteArray JNICALL Java_sun_lwawt_macosx_CClipboard_getClipboardData
(JNIEnv *env, jobject inObject, jlong format)
{
    jbyteArray returnValue = NULL;

    // Note that this routine makes no attempt to interpret the data, since we're returning
    // a byte array back to Java.  CDataTransferer will do that if necessary.
JNF_COCOA_ENTER(env);

    NSString *formatAsString = formatForIndex(format);
    NSData *clipData = [[CClipboard sharedClipboard] javaGetDataForType:formatAsString];

    if (clipData == NULL) {
        [JNFException raise:env as:"java/io/IOException" reason:"Font transform has NaN position"];
        return NULL;
    }

    NSUInteger dataSize = [clipData length];
    returnValue = (*env)->NewByteArray(env, dataSize);
    if (returnValue == NULL) {
        return NULL;
    }

    if (dataSize != 0) {
        const void *dataBuffer = [clipData bytes];
        (*env)->SetByteArrayRegion(env, returnValue, 0, dataSize, (jbyte *)dataBuffer);
    }

JNF_COCOA_EXIT(env);
    return returnValue;
}

/*
 * Class:     sun_lwawt_macosx_CClipboard
 * Method:    checkPasteboard
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_sun_lwawt_macosx_CClipboard_checkPasteboard
(JNIEnv *env, jobject inObject )
{
    JNF_COCOA_ENTER(env);

    [ThreadUtilities performOnMainThreadWaiting:YES block:^(){
        [[CClipboard sharedClipboard] checkPasteboard:nil];
    }];
        
    JNF_COCOA_EXIT(env);
}


