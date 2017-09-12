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

#import "PrinterView.h"

#import "java_awt_print_Pageable.h"
#import "java_awt_print_PageFormat.h"

#import <JavaNativeFoundation/JavaNativeFoundation.h>

#import "ThreadUtilities.h"
#import "GeomUtilities.h"


static JNF_CLASS_CACHE(sjc_CPrinterJob, "sun/lwawt/macosx/CPrinterJob");
static JNF_CLASS_CACHE(sjc_PageFormat, "java/awt/print/PageFormat");

@implementation PrinterView

- (id)initWithFrame:(NSRect)aRect withEnv:(JNIEnv*)env withPrinterJob:(jobject)printerJob
{
    self = [super initWithFrame:aRect];
    if (self)
    {
        fPrinterJob = JNFNewGlobalRef(env, printerJob);
        fCurPageFormat = NULL;
        fCurPainter = NULL;
        fCurPeekGraphics = NULL;
    }
    return self;
}

- (void)releaseReferences:(JNIEnv*)env
{
    if (fCurPageFormat != NULL)
    {
        JNFDeleteGlobalRef(env, fCurPageFormat);
        fCurPageFormat = NULL;
    }
    if (fCurPainter != NULL)
    {
        JNFDeleteGlobalRef(env, fCurPainter);
        fCurPainter = NULL;
    }
    if (fCurPeekGraphics != NULL)
    {
        JNFDeleteGlobalRef(env, fCurPeekGraphics);
        fCurPeekGraphics = NULL;
    }
}

- (void)setFirstPage:(jint)firstPage lastPage:(jint)lastPage {
    fFirstPage = firstPage;
    fLastPage = lastPage;
}

- (void)drawRect:(NSRect)aRect
{
    AWT_ASSERT_NOT_APPKIT_THREAD;

    static JNF_MEMBER_CACHE(jm_printToPathGraphics, sjc_CPrinterJob, "printToPathGraphics", "(Lsun/print/PeekGraphics;Ljava/awt/print/PrinterJob;Ljava/awt/print/Printable;Ljava/awt/print/PageFormat;IJ)V");

    // Create and draw into a new CPrinterGraphics with the current Context.
    assert(fCurPageFormat != NULL);
    assert(fCurPainter != NULL);
    assert(fCurPeekGraphics != NULL);

    JNIEnv* env = [ThreadUtilities getJNIEnvUncached];

    if ([self cancelCheck:env])
    {
        [self releaseReferences:env];
        return;
    }

    NSPrintOperation* printLoop = [NSPrintOperation currentOperation];
    jint jPageIndex = [printLoop currentPage] - 1;

    jlong context = ptr_to_jlong([printLoop context]);
    CGContextRef cgRef = (CGContextRef)[[printLoop context] graphicsPort];
    CGContextSaveGState(cgRef); //04/28/2004: state needs to be saved here due to addition of lazy state management

    JNFCallVoidMethod(env, fPrinterJob, jm_printToPathGraphics, fCurPeekGraphics, fPrinterJob, fCurPainter, fCurPageFormat, jPageIndex, context); // AWT_THREADING Safe (AWTRunLoop)

    CGContextRestoreGState(cgRef);

    [self releaseReferences:env];
}

- (NSString*)printJobTitle
{
    AWT_ASSERT_NOT_APPKIT_THREAD;

    static JNF_MEMBER_CACHE(jm_getJobName, sjc_CPrinterJob, "getJobName", "()Ljava/lang/String;");

    JNIEnv* env = [ThreadUtilities getJNIEnvUncached];

    jobject o = JNFCallObjectMethod(env, fPrinterJob, jm_getJobName); // AWT_THREADING Safe (known object)
    id result = JNFJavaToNSString(env, o);
    (*env)->DeleteLocalRef(env, o);
    return result;
}

- (BOOL)knowsPageRange:(NSRangePointer)aRange
{
    AWT_ASSERT_NOT_APPKIT_THREAD;

    JNIEnv* env = [ThreadUtilities getJNIEnvUncached];
    if ([self cancelCheck:env])
    {
        return NO;
    }

    aRange->location = fFirstPage + 1;

    if (fLastPage == java_awt_print_Pageable_UNKNOWN_NUMBER_OF_PAGES)
    {
        aRange->length = NSIntegerMax;
    }
    else
    {
        aRange->length = (fLastPage + 1) - fFirstPage;
    }

    return YES;
}

- (NSRect)rectForPage:(NSInteger)pageNumber
{
    AWT_ASSERT_NOT_APPKIT_THREAD;

    static JNF_MEMBER_CACHE(jm_getPageformatPrintablePeekgraphics, sjc_CPrinterJob, "getPageformatPrintablePeekgraphics", "(I)[Ljava/lang/Object;");
    static JNF_MEMBER_CACHE(jm_printAndGetPageFormatArea, sjc_CPrinterJob, "printAndGetPageFormatArea", "(Ljava/awt/print/Printable;Ljava/awt/Graphics;Ljava/awt/print/PageFormat;I)Ljava/awt/geom/Rectangle2D;");
    static JNF_MEMBER_CACHE(jm_getOrientation, sjc_PageFormat, "getOrientation", "()I");

    // Assertions removed, and corresponding JNFDeleteGlobalRefs added, for radr://3962543
    // Actual fix that will keep these assertions from being true is radr://3205462 ,
    // which will hopefully be fixed by the blocking AppKit bug radr://3056694
    //assert(fCurPageFormat == NULL);
    //assert(fCurPainter == NULL);
    //assert(fCurPeekGraphics == NULL);

    JNIEnv* env = [ThreadUtilities getJNIEnvUncached];
    if(fCurPageFormat != NULL) {
        JNFDeleteGlobalRef(env, fCurPageFormat);
    }
    if(fCurPainter != NULL) {
        JNFDeleteGlobalRef(env, fCurPainter);
    }
    if(fCurPeekGraphics != NULL) {
        JNFDeleteGlobalRef(env, fCurPeekGraphics);
    }

    //+++gdb Check the pageNumber for validity (PageAttrs)

    jint jPageNumber = pageNumber - 1;

    NSRect result;

    if ([self cancelCheck:env])
    {
        return NSZeroRect;
    }

    jobjectArray objectArray = JNFCallObjectMethod(env, fPrinterJob, jm_getPageformatPrintablePeekgraphics, jPageNumber); // AWT_THREADING Safe (AWTRunLoopMode)
    if (objectArray != NULL) {
        // Get references to the return objects -> PageFormat, Printable, PeekGraphics
        // Cheat - we know we either got NULL or a 3 element array
        jobject pageFormat = (*env)->GetObjectArrayElement(env, objectArray, 0);
        fCurPageFormat = JNFNewGlobalRef(env, pageFormat);
        (*env)->DeleteLocalRef(env, pageFormat);

        jobject painter = (*env)->GetObjectArrayElement(env, objectArray, 1);
        fCurPainter = JNFNewGlobalRef(env, painter);
        (*env)->DeleteLocalRef(env, painter);

        jobject peekGraphics = (*env)->GetObjectArrayElement(env, objectArray, 2);
        fCurPeekGraphics = JNFNewGlobalRef(env, peekGraphics);
        (*env)->DeleteLocalRef(env, peekGraphics);

        // Actually print and get the PageFormatArea
        jobject pageFormatArea = JNFCallObjectMethod(env, fPrinterJob, jm_printAndGetPageFormatArea, fCurPainter, fCurPeekGraphics, fCurPageFormat, jPageNumber); // AWT_THREADING Safe (AWTRunLoopMode)
        if (pageFormatArea != NULL) {
            NSPrintingOrientation currentOrientation = 
                    [[[NSPrintOperation currentOperation] printInfo] orientation];
            // set page orientation
            switch (JNFCallIntMethod(env, fCurPageFormat, jm_getOrientation)) { 
                case java_awt_print_PageFormat_PORTRAIT:
                default:
                    if (currentOrientation != NSPortraitOrientation) {
                        [[[NSPrintOperation currentOperation] printInfo] 
                                            setOrientation:NSPortraitOrientation];
                    }
                    break;

                case java_awt_print_PageFormat_LANDSCAPE:
                case java_awt_print_PageFormat_REVERSE_LANDSCAPE:
                    if (currentOrientation != NSLandscapeOrientation) {
                        [[[NSPrintOperation currentOperation] printInfo] 
                                            setOrientation:NSLandscapeOrientation];
                    }
                    break;
                }
            result = JavaToNSRect(env, pageFormatArea);
            (*env)->DeleteLocalRef(env, pageFormatArea);
        } else {
            [self releaseReferences:env];
            result = NSZeroRect;
        }

        (*env)->DeleteLocalRef(env, objectArray);
    } else {
        [self releaseReferences:env];
        result = NSZeroRect;
    }

    return result;
}

- (BOOL)cancelCheck:(JNIEnv*)env
{
    AWT_ASSERT_NOT_APPKIT_THREAD;

    static JNF_MEMBER_CACHE(jm_cancelCheck, sjc_CPrinterJob, "cancelCheck", "()Z");

    return JNFCallBooleanMethod(env, fPrinterJob, jm_cancelCheck); // AWT_THREADING Safe (known object)
}

// This is called by -[PrintModel safePrintLoop]
- (void)complete:(JNIEnv*)env
{
    AWT_ASSERT_NOT_APPKIT_THREAD;

    static JNF_MEMBER_CACHE(jf_completePrintLoop, sjc_CPrinterJob, "completePrintLoop", "()V");
    JNFCallVoidMethod(env, fPrinterJob, jf_completePrintLoop);

    // Clean up after ourselves
    // Can't put these into -dealloc since that happens (potentially) after the JNIEnv is stale
    [self releaseReferences:env];
    if (fPrinterJob != NULL)
    {
        JNFDeleteGlobalRef(env, fPrinterJob);
        fPrinterJob = NULL;
    }
}

- (BOOL)isFlipped
{
    return TRUE;
}

@end
