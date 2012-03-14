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

#import "com_apple_eio_FileManager.h"

#import <Cocoa/Cocoa.h>
#import <JavaNativeFoundation/JavaNativeFoundation.h>

#import "ThreadUtilities.h"


/*
 * Class:     com_apple_eio_FileManager
 * Method:    _setFileTypeAndCreator
 * Signature: (Ljava/lang/String;II)V
 */
JNIEXPORT void JNICALL Java_com_apple_eio_FileManager__1setFileTypeAndCreator
(JNIEnv *env, jclass clz, jstring javaFilename, jint type, jint creator)
{
JNF_COCOA_ENTER(env);
        NSString *filename = JNFNormalizedNSStringForPath(env, javaFilename);
        NSDictionary *attr = [NSDictionary dictionaryWithObjectsAndKeys:
                                                        [NSNumber numberWithInt:type], NSFileHFSTypeCode,
                                                        [NSNumber numberWithInt:creator], NSFileHFSCreatorCode, nil];
    [[NSFileManager defaultManager] changeFileAttributes:attr atPath:filename];
JNF_COCOA_EXIT(env);
}

/*
 * Class:     com_apple_eio_FileManager
 * Method:    _setFileType
 * Signature: (Ljava/lang/String;I)V
 */
JNIEXPORT void JNICALL Java_com_apple_eio_FileManager__1setFileType
(JNIEnv *env, jclass ckz, jstring javaFilename, jint type)
{
JNF_COCOA_ENTER(env);
        NSString *filename = JNFNormalizedNSStringForPath(env, javaFilename);
        NSDictionary *attr = [NSDictionary dictionaryWithObject:[NSNumber numberWithInt:type] forKey:NSFileHFSTypeCode];
    [[NSFileManager defaultManager] changeFileAttributes:attr atPath:filename];
JNF_COCOA_EXIT(env);
}

/*
 * Class:     com_apple_eio_FileManager
 * Method:    _setFileCreator
 * Signature: (Ljava/lang/String;I)V
 */
JNIEXPORT void JNICALL Java_com_apple_eio_FileManager__1setFileCreator
(JNIEnv *env, jclass clz, jstring javaFilename, jint creator)
{
JNF_COCOA_ENTER(env);
        NSString *filename = JNFNormalizedNSStringForPath(env, javaFilename);
        NSDictionary *attr = [NSDictionary dictionaryWithObject:[NSNumber numberWithInt:creator] forKey:NSFileHFSCreatorCode];
    [[NSFileManager defaultManager] changeFileAttributes:attr atPath:filename];
JNF_COCOA_EXIT(env);
}

/*
 * Class:     com_apple_eio_FileManager
 * Method:    _getFileType
 * Signature: (Ljava/lang/String;)I
 */
JNIEXPORT jint JNICALL Java_com_apple_eio_FileManager__1getFileType
(JNIEnv *env, jclass clz, jstring javaFilename)
{
    jint type = 0;
JNF_COCOA_ENTER(env);
        NSString *filename = JNFNormalizedNSStringForPath(env, javaFilename);
    NSDictionary *attributes = [[NSFileManager defaultManager] fileAttributesAtPath:filename traverseLink:YES];
    NSNumber *val = [attributes objectForKey:NSFileHFSTypeCode];
    type = [val intValue];
JNF_COCOA_EXIT(env);
    return type;
}

/*
 * Class:     com_apple_eio_FileManager
 * Method:    _getFileCreator
 * Signature: (Ljava/lang/String;)I
 */
JNIEXPORT jint JNICALL Java_com_apple_eio_FileManager__1getFileCreator
  (JNIEnv *env, jclass clz, jstring javaFilename)
{
    jint creator = 0;
JNF_COCOA_ENTER(env);
        NSString *filename = JNFNormalizedNSStringForPath(env, javaFilename);
    NSDictionary *attributes = [[NSFileManager defaultManager] fileAttributesAtPath:filename traverseLink:YES];
    NSNumber *val = [attributes objectForKey:NSFileHFSCreatorCode];
    creator = [val intValue];
JNF_COCOA_EXIT(env);
    return creator;
}

/*
 * Class:     com_apple_eio_FileManager
 * Method:    _findFolder
 * Signature: (SIZ)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_com_apple_eio_FileManager__1findFolder__SIZ
(JNIEnv *env, jclass clz, jshort domain, jint folderType, jboolean createIfNeeded)
{
    jstring filename = nil;
JNF_COCOA_ENTER(env);

    FSRef foundRef;
    createIfNeeded = createIfNeeded || (folderType == kTemporaryFolderType) || (folderType == kChewableItemsFolderType);
    if (FSFindFolder((SInt16)domain, (OSType)folderType, (Boolean)createIfNeeded, &foundRef) == noErr) {
        char path[PATH_MAX];
        if (FSRefMakePath(&foundRef, (UInt8 *)path, sizeof(path)) == noErr) {
            NSString *filenameString = [[NSFileManager defaultManager] stringWithFileSystemRepresentation:path length:strlen(path)];
            filename = JNFNormalizedJavaStringForPath(env, filenameString);
        }
    }

JNF_COCOA_EXIT(env);
    return filename;
}


/*
 * Class:     com_apple_eio_FileManager
 * Method:    _openURL
 * Signature: (Ljava/lang/String;)V
 */
JNIEXPORT void JNICALL Java_com_apple_eio_FileManager__1openURL
(JNIEnv *env, jclass clz, jstring urlString)
{
JNF_COCOA_ENTER(env);

    NSURL *url = [NSURL URLWithString:JNFNormalizedNSStringForPath(env, urlString)];

        // Radar 3208005: Run this on the main thread; file:// style URLs will hang otherwise.
    [JNFRunLoop performOnMainThreadWaiting:NO withBlock:^(){
        [[NSWorkspace sharedWorkspace] openURL:url];
    }];

JNF_COCOA_EXIT(env);
}


/*
 * Class:     com_apple_eio_FileManager
 * Method:    getNativeResourceFromBundle
 * Signature: (Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_com_apple_eio_FileManager_getNativeResourceFromBundle
(JNIEnv *env, jclass clz, jstring javaResourceName, jstring javaSubDirName, jstring javaTypeName)
{
    jstring filename = NULL;
JNF_COCOA_ENTER(env);

    NSString *resourceName = JNFNormalizedNSStringForPath(env, javaResourceName);
        NSString *subDirectory = JNFNormalizedNSStringForPath(env, javaSubDirName);
        NSString *typeName = JNFNormalizedNSStringForPath(env, javaTypeName);

    NSString *path = [[NSBundle mainBundle] pathForResource:resourceName
                                                     ofType:typeName
                                                inDirectory:subDirectory];

    filename = JNFNormalizedJavaStringForPath(env, path);

JNF_COCOA_EXIT(env);
    return filename;
}


/*
 * Class:     com_apple_eio_FileManager
 * Method:    getNativePathToApplicationBundle
 * Signature: ()Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_com_apple_eio_FileManager_getNativePathToApplicationBundle
(JNIEnv *env, jclass clazz)
{
        jstring filename = nil;
JNF_COCOA_ENTER(env);

        NSBundle *mainBundle = [NSBundle mainBundle];
        filename = JNFNormalizedJavaStringForPath(env, [mainBundle bundlePath]);

JNF_COCOA_EXIT(env);
        return filename;
}


/*
 * Class:     com_apple_eio_FileManager
 * Method:    __moveToTrash
 * Signature: (Ljava/lang/String;)V
 */

JNIEXPORT jboolean JNICALL Java_com_apple_eio_FileManager__1moveToTrash
(JNIEnv *env, jclass clz, jstring url)
{
        __block jboolean returnValue = JNI_FALSE;
JNF_COCOA_ENTER(env);

    NSString *path = JNFNormalizedNSStringForPath(env, url);
    [JNFRunLoop performOnMainThreadWaiting:YES withBlock:^(){
        NSInteger res = 0;
        [[NSWorkspace sharedWorkspace] performFileOperation:NSWorkspaceRecycleOperation
                                                     source:[path stringByDeletingLastPathComponent]
                                                destination:nil
                                                      files:[NSArray arrayWithObject:[path lastPathComponent]]
                                                        tag:&res];
        returnValue = (res == 0);
    }];

JNF_COCOA_EXIT(env);

        return returnValue;
}

/*
 * Class:     com_apple_eio_FileManager
 * Method:    __revealInFinder
 * Signature: (Ljava/lang/String;)V
 */

JNIEXPORT jboolean JNICALL Java_com_apple_eio_FileManager__1revealInFinder
(JNIEnv *env, jclass clz, jstring url)
{
        __block jboolean returnValue = JNI_FALSE;
JNF_COCOA_ENTER(env);

    NSString *path = JNFNormalizedNSStringForPath(env, url);
    [JNFRunLoop performOnMainThreadWaiting:YES withBlock:^(){
        returnValue = [[NSWorkspace sharedWorkspace] selectFile:path inFileViewerRootedAtPath:@""];
    }];

JNF_COCOA_EXIT(env);

        return returnValue;
}
