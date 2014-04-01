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

#import "CDataTransferer.h"
#include "sun_lwawt_macosx_CDataTransferer.h"

#import <AppKit/AppKit.h>
#import <JavaNativeFoundation/JavaNativeFoundation.h>
#import "jni_util.h"

#include "ThreadUtilities.h"


// ***** NOTE ***** This dictionary corresponds to the static array predefinedClipboardNames
// in CDataTransferer.java.
NSMutableDictionary *sStandardMappings = nil;

NSMutableDictionary *getMappingTable() {
    if (sStandardMappings == nil) {
        sStandardMappings = [[NSMutableDictionary alloc] init];
        [sStandardMappings setObject:NSStringPboardType
                              forKey:[NSNumber numberWithLong:sun_lwawt_macosx_CDataTransferer_CF_STRING]];
        [sStandardMappings setObject:NSFilenamesPboardType
                              forKey:[NSNumber numberWithLong:sun_lwawt_macosx_CDataTransferer_CF_FILE]];
        [sStandardMappings setObject:NSTIFFPboardType
                              forKey:[NSNumber numberWithLong:sun_lwawt_macosx_CDataTransferer_CF_TIFF]];
        [sStandardMappings setObject:NSRTFPboardType
                              forKey:[NSNumber numberWithLong:sun_lwawt_macosx_CDataTransferer_CF_RICH_TEXT]];
        [sStandardMappings setObject:NSHTMLPboardType
                              forKey:[NSNumber numberWithLong:sun_lwawt_macosx_CDataTransferer_CF_HTML]];
        [sStandardMappings setObject:NSPDFPboardType
                              forKey:[NSNumber numberWithLong:sun_lwawt_macosx_CDataTransferer_CF_PDF]];
        [sStandardMappings setObject:NSURLPboardType
                              forKey:[NSNumber numberWithLong:sun_lwawt_macosx_CDataTransferer_CF_URL]];
    }
    return sStandardMappings;
}

/*
 * Convert from a standard NSPasteboard data type to an index in our mapping table.
 */
jlong indexForFormat(NSString *format) {
    jlong returnValue = -1;

    NSMutableDictionary *mappingTable = getMappingTable();
    NSArray *matchingKeys = [mappingTable allKeysForObject:format];

    // There should only be one matching key here...
    if ([matchingKeys count] > 0) {
        NSNumber *formatID = (NSNumber *)[matchingKeys objectAtIndex:0];
        returnValue = [formatID longValue];
    }

    // If we don't recognize the format, but it's a Java "custom" format register it
    if (returnValue == -1 && ([format hasPrefix:@"JAVA_DATAFLAVOR:"]) ) {
        returnValue = registerFormatWithPasteboard(format);
    }

    return returnValue;
}

/*
 * Inverse of above -- given a long int index, get the matching data format NSString.
 */
NSString *formatForIndex(jlong inFormatCode) {
    return [getMappingTable() objectForKey:[NSNumber numberWithLong:inFormatCode]];
}

jlong registerFormatWithPasteboard(NSString *format) {
    NSMutableDictionary *mappingTable = getMappingTable();
    NSUInteger nextID = [mappingTable count] + 1;
    [mappingTable setObject:format forKey:[NSNumber numberWithLong:nextID]];
    return nextID;
}


/*
 * Class:     sun_lwawt_macosx_CDataTransferer
 * Method:    registerFormatWithPasteboard
 * Signature: (Ljava/lang/String;)J
 */
JNIEXPORT jlong JNICALL Java_sun_lwawt_macosx_CDataTransferer_registerFormatWithPasteboard
(JNIEnv *env, jobject jthis, jstring newformat)
{
    jlong returnValue = -1;
JNF_COCOA_ENTER(env);
    returnValue = registerFormatWithPasteboard(JNFJavaToNSString(env, newformat));
JNF_COCOA_EXIT(env);
    return returnValue;
}

/*
 * Class:     sun_lwawt_macosx_CDataTransferer
 * Method:    formatForIndex
 * Signature: (J)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_sun_lwawt_macosx_CDataTransferer_formatForIndex
  (JNIEnv *env, jobject jthis, jlong index)
{
    jstring returnValue = NULL;
JNF_COCOA_ENTER(env);
    returnValue = JNFNSToJavaString(env, formatForIndex(index));
JNF_COCOA_EXIT(env);
    return returnValue;
}

/*
 * Class:     sun_lwawt_macosx_CDataTransferer
 * Method:    imageDataToPlatformImageBytes
 * Signature: ([III)[B
     */
JNIEXPORT jbyteArray JNICALL Java_sun_lwawt_macosx_CDataTransferer_imageDataToPlatformImageBytes
(JNIEnv *env, jobject obj, jintArray inPixelData, jint inWidth, jint inHeight)
{
    jbyteArray returnValue = nil;
JNF_COCOA_ENTER(env);
    UInt32 *rawImageData = (UInt32 *)(*env)->GetPrimitiveArrayCritical(env, inPixelData, 0);

    // The pixel data is in premultiplied ARGB format. That's exactly what
    // we need for the bitmap image rep.
    if (rawImageData != NULL) {
        NSBitmapImageRep *imageRep = [[NSBitmapImageRep alloc] initWithBitmapDataPlanes:NULL
                                                                             pixelsWide:inWidth
                                                                             pixelsHigh:inHeight
                                                                          bitsPerSample:8
                                                                        samplesPerPixel:4
                                                                               hasAlpha:YES
                                                                               isPlanar:NO
                                                                         colorSpaceName:NSCalibratedRGBColorSpace
                                                                            bytesPerRow:(inWidth*4)
                                                                           bitsPerPixel:32];

        // Conver the ARGB data into RGBA data that the bitmap can draw.
        unsigned char *destData = [imageRep bitmapData];
        unsigned char *currentRowBase;
        jint x, y;

        for (y = 0; y < inHeight; y++) {
            currentRowBase = destData + y * (inWidth * 4);
            unsigned char *currElement = currentRowBase;
            for (x = 0; x < inWidth; x++) {
                UInt32 currPixel = rawImageData[y * inWidth + x];
                *currElement++ = ((currPixel & 0xFF0000) >> 16);
                *currElement++ = ((currPixel & 0xFF00) >> 8);
                *currElement++ = (currPixel & 0xFF);
                *currElement++ = ((currPixel & 0xFF000000) >> 24);
            }
        }

        (*env)->ReleasePrimitiveArrayCritical(env, inPixelData, rawImageData, JNI_ABORT);
        NSData *tiffImage = [imageRep TIFFRepresentation];
        jsize tiffSize = (jsize)[tiffImage length]; // #warning 64-bit: -length returns NSUInteger, but NewByteArray takes jsize
        returnValue = (*env)->NewByteArray(env, tiffSize);
        CHECK_NULL_RETURN(returnValue, nil);
        jbyte *tiffData = (jbyte *)(*env)->GetPrimitiveArrayCritical(env, returnValue, 0);
        CHECK_NULL_RETURN(tiffData, nil);
        [tiffImage getBytes:tiffData];
        (*env)->ReleasePrimitiveArrayCritical(env, returnValue, tiffData, 0); // Do not use JNI_COMMIT, as that will not free the buffer copy when +ProtectJavaHeap is on.
        [imageRep release];
    }
JNF_COCOA_EXIT(env);
    return returnValue;

}

static jobject getImageForByteStream(JNIEnv *env, jbyteArray sourceData)
{
    CHECK_NULL_RETURN(sourceData, NULL);

    jsize sourceSize = (*env)->GetArrayLength(env, sourceData);
    if (sourceSize == 0) return NULL;

    jbyte *sourceBytes = (*env)->GetPrimitiveArrayCritical(env, sourceData, NULL);
    CHECK_NULL_RETURN(sourceBytes, NULL);
    NSData *rawData = [NSData dataWithBytes:sourceBytes length:sourceSize];

    NSImage *newImage = [[NSImage alloc] initWithData:rawData];
    if (newImage) CFRetain(newImage); // GC
    [newImage release];

    (*env)->ReleasePrimitiveArrayCritical(env, sourceData, sourceBytes, JNI_ABORT);
    CHECK_NULL_RETURN(newImage, NULL);

    // The ownership of the NSImage is passed to the new CImage jobject. No need to release it.
    static JNF_CLASS_CACHE(jc_CImage, "sun/lwawt/macosx/CImage");
    static JNF_STATIC_MEMBER_CACHE(jm_CImage_getCreator, jc_CImage, "getCreator", "()Lsun/lwawt/macosx/CImage$Creator;");
    jobject creator = JNFCallStaticObjectMethod(env, jm_CImage_getCreator);

    static JNF_CLASS_CACHE(jc_CImage_Generator, "sun/lwawt/macosx/CImage$Creator");
    static JNF_MEMBER_CACHE(jm_CImage_Generator_createImageUsingNativeSize, jc_CImage_Generator, "createImageUsingNativeSize", "(J)Ljava/awt/image/BufferedImage;");
    return JNFCallObjectMethod(env, creator, jm_CImage_Generator_createImageUsingNativeSize, ptr_to_jlong(newImage)); // AWT_THREADING Safe (known object)
}

/*
 * Class:     sun_lwawt_macosx_CDataTransferer
 * Method:    getImageForByteStream
 * Signature: ([B)Ljava/awt/Image;
 */
JNIEXPORT jobject JNICALL Java_sun_lwawt_macosx_CDataTransferer_getImageForByteStream
  (JNIEnv *env, jobject obj, jbyteArray sourceData)
{
    jobject img = NULL;
JNF_COCOA_ENTER(env);
    img = getImageForByteStream(env, sourceData);
JNF_COCOA_EXIT(env);
    return img;
}

static jobjectArray CreateJavaFilenameArray(JNIEnv *env, NSArray *filenameArray)
{
    NSUInteger filenameCount = [filenameArray count];
    if (filenameCount == 0) return nil;

    // Get the java.lang.String class object:
    jclass stringClazz = (*env)->FindClass(env, "java/lang/String");
    CHECK_NULL_RETURN(stringClazz, nil);
    jobject jfilenameArray = (*env)->NewObjectArray(env, filenameCount, stringClazz, NULL); // AWT_THREADING Safe (known object)
    if ((*env)->ExceptionOccurred(env)) {
        (*env)->ExceptionDescribe(env);
        (*env)->ExceptionClear(env);
        return nil;
    }
    if (!jfilenameArray) {
        NSLog(@"CDataTransferer_CreateJavaFilenameArray: couldn't create jfilenameArray.");
        return nil;
    }
    (*env)->DeleteLocalRef(env, stringClazz);

    // Iterate through all the filenames:
    NSUInteger i;
    for (i = 0; i < filenameCount; i++) {
        NSMutableString *stringVal = [[NSMutableString alloc] initWithString:[filenameArray objectAtIndex:i]];
        CFStringNormalize((CFMutableStringRef)stringVal, kCFStringNormalizationFormC);
        const char* stringBytes = [stringVal UTF8String];

        // Create a Java String:
        jstring string = (*env)->NewStringUTF(env, stringBytes);
        if ((*env)->ExceptionOccurred(env)) {
            (*env)->ExceptionDescribe(env);
            (*env)->ExceptionClear(env);
            continue;
        }
        if (!string) {
            NSLog(@"CDataTransferer_CreateJavaFilenameArray: couldn't create jstring[%lu] for [%@].", (unsigned long) i, stringVal);
            continue;
        }

        // Set the Java array element with our String:
        (*env)->SetObjectArrayElement(env, jfilenameArray, i, string);
        if ((*env)->ExceptionOccurred(env)) {
            (*env)->ExceptionDescribe(env);
            (*env)->ExceptionClear(env);
            continue;
        }

        // Release local String reference:
        (*env)->DeleteLocalRef(env, string);
    }

    return jfilenameArray;
}

/*
 * Class:     sun_lwawt_macosx_CDataTransferer
 * Method:    draqQueryFile
 * Signature: ([B)[Ljava/lang/String;
 */
JNIEXPORT jobjectArray JNICALL
Java_sun_lwawt_macosx_CDataTransferer_nativeDragQueryFile
(JNIEnv *env, jclass clazz, jbyteArray jbytearray)
{
    // Decodes a byte array into a set of String filenames.
    // bytes here is an XML property list containing all of the filenames in the drag.
    // Parse the XML list into strings and return an array of Java strings matching all of the
    // files in the list.

    jobjectArray jreturnArray = NULL;

JNF_COCOA_ENTER(env);
    // Get byte array elements:
    jboolean isCopy;
    jbyte* jbytes = (*env)->GetByteArrayElements(env, jbytearray, &isCopy);
    if (jbytes == NULL) {
        return NULL;
    }

    // Wrap jbytes in an NSData object:
    jsize jbytesLength = (*env)->GetArrayLength(env, jbytearray);
    NSData *xmlData = [NSData dataWithBytesNoCopy:jbytes length:jbytesLength freeWhenDone:NO];

    // Create a property list from the Java data:
    NSString *errString = nil;
    NSPropertyListFormat plistFormat = 0;
    id plist = [NSPropertyListSerialization propertyListFromData:xmlData mutabilityOption:NSPropertyListImmutable
        format:&plistFormat errorDescription:&errString];

    // The property list must be an array of strings:
    if (plist == nil || [plist isKindOfClass:[NSArray class]] == FALSE) {
        NSLog(@"CDataTransferer_dragQueryFile: plist not a valid NSArray (error %@):\n%@", errString, plist);
        (*env)->ReleaseByteArrayElements(env, jbytearray, jbytes, JNI_ABORT);
        return NULL;
    }

    // Transfer all string items from the plistArray to filenameArray. This wouldn't be necessary
    // if we could trust the array to contain all valid elements but this way we'll be sure.
    NSArray *plistArray = (NSArray *)plist;
    NSUInteger plistItemCount = [plistArray count];
    NSMutableArray *filenameArray = [[NSMutableArray alloc] initWithCapacity:plistItemCount];

    NSUInteger i;
    for (i = 0; i < plistItemCount; i++) {
        // Filenames must be strings:
        id idVal = [plistArray objectAtIndex:i];
        if ([idVal isKindOfClass:[NSString class]] == FALSE) {
            NSLog(@"CDataTransferer_dragQueryFile: plist[%lu] not an NSString:\n%@", (unsigned long) i, idVal);
            continue;
        }

        [filenameArray addObject:idVal];
    }

    // Convert our array of filenames into a Java array of String filenames:
    jreturnArray = CreateJavaFilenameArray(env, filenameArray);

    [filenameArray release];

    // We're done with the jbytes (backing the plist/plistArray):
    (*env)->ReleaseByteArrayElements(env, jbytearray, jbytes, JNI_ABORT);
JNF_COCOA_EXIT(env);
    return jreturnArray;
}
