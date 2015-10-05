/*
 * Copyright (c) 2011, 2015, Oracle and/or its affiliates. All rights reserved.
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


#import "java_awt_print_PageFormat.h"
#import "java_awt_print_Pageable.h"
#import "sun_lwawt_macosx_CPrinterJob.h"
#import "sun_lwawt_macosx_CPrinterPageDialog.h"

#import <Cocoa/Cocoa.h>
#import <JavaNativeFoundation/JavaNativeFoundation.h>

#import "PrinterView.h"
#import "PrintModel.h"
#import "ThreadUtilities.h"
#import "GeomUtilities.h"

static JNF_CLASS_CACHE(sjc_Paper, "java/awt/print/Paper");
static JNF_CLASS_CACHE(sjc_PageFormat, "java/awt/print/PageFormat");
static JNF_CLASS_CACHE(sjc_CPrinterJob, "sun/lwawt/macosx/CPrinterJob");
static JNF_CLASS_CACHE(sjc_CPrinterDialog, "sun/lwawt/macosx/CPrinterDialog");
static JNF_MEMBER_CACHE(sjm_getNSPrintInfo, sjc_CPrinterJob, "getNSPrintInfo", "()J");
static JNF_MEMBER_CACHE(sjm_printerJob, sjc_CPrinterDialog, "fPrinterJob", "Lsun/lwawt/macosx/CPrinterJob;");

static NSPrintInfo* createDefaultNSPrintInfo();

static void makeBestFit(NSPrintInfo* src);

static void nsPrintInfoToJavaPaper(JNIEnv* env, NSPrintInfo* src, jobject dst);
static void javaPaperToNSPrintInfo(JNIEnv* env, jobject src, NSPrintInfo* dst);

static void nsPrintInfoToJavaPageFormat(JNIEnv* env, NSPrintInfo* src, jobject dst);
static void javaPageFormatToNSPrintInfo(JNIEnv* env, jobject srcPrinterJob, jobject srcPageFormat, NSPrintInfo* dst);

static void nsPrintInfoToJavaPrinterJob(JNIEnv* env, NSPrintInfo* src, jobject dstPrinterJob, jobject dstPageable);
static void javaPrinterJobToNSPrintInfo(JNIEnv* env, jobject srcPrinterJob, jobject srcPageable, NSPrintInfo* dst);


#ifdef __MAC_10_9 // code for SDK 10.9 or newer
#define NS_PORTRAIT NSPaperOrientationPortrait
#define NS_LANDSCAPE NSPaperOrientationLandscape
#else // code for SDK 10.8 or older
#define NS_PORTRAIT NSPortraitOrientation
#define NS_LANDSCAPE NSLandscapeOrientation
#endif

static NSPrintInfo* createDefaultNSPrintInfo(JNIEnv* env, jstring printer)
{
    NSPrintInfo* defaultPrintInfo = [[NSPrintInfo sharedPrintInfo] copy];
    if (printer != NULL)
    {
        NSPrinter* nsPrinter = [NSPrinter printerWithName:JNFJavaToNSString(env, printer)];
        if (nsPrinter != nil)
        {
            [defaultPrintInfo setPrinter:nsPrinter];
        }
    }
    [defaultPrintInfo setUpPrintOperationDefaultValues];

    // cmc 05/18/04 radr://3160443 : setUpPrintOperationDefaultValues sets the
    // page margins to 72, 72, 90, 90 - need to use [NSPrintInfo imageablePageBounds]
    // to get values from the printer.
    // NOTE: currently [NSPrintInfo imageablePageBounds] does not update itself when
    // the user selects a different printer - see radr://3657453. However, rather than
    // directly querying the PPD here, we'll let AppKit printing do the work. The AppKit
    // printing bug above is set to be fixed for Tiger.
    NSRect imageableRect = [defaultPrintInfo imageablePageBounds];
    [defaultPrintInfo setLeftMargin: imageableRect.origin.x];
    [defaultPrintInfo setBottomMargin: imageableRect.origin.y]; //top and bottom are flipped because [NSPrintInfo imageablePageBounds] returns a flipped NSRect (bottom-left to top-right).
    [defaultPrintInfo setRightMargin: [defaultPrintInfo paperSize].width-imageableRect.origin.x-imageableRect.size.width];
    [defaultPrintInfo setTopMargin: [defaultPrintInfo paperSize].height-imageableRect.origin.y-imageableRect.size.height];

    return defaultPrintInfo;
}

static void makeBestFit(NSPrintInfo* src)
{
    // This will look at the NSPrintInfo's margins. If they are out of bounds to the
    // imageable area of the page, it will set them to the largest possible size.

    NSRect imageable = [src imageablePageBounds];

    NSSize paperSize = [src paperSize];

    CGFloat fullLeftM = imageable.origin.x;
    CGFloat fullRightM = paperSize.width - (imageable.origin.x + imageable.size.width);

    // These are flipped because [NSPrintInfo imageablePageBounds] returns a flipped
    //  NSRect (bottom-left to top-right).
    CGFloat fullTopM = paperSize.height - (imageable.origin.y + imageable.size.height);
    CGFloat fullBottomM = imageable.origin.y;

    if (fullLeftM > [src leftMargin])
    {
        [src setLeftMargin:fullLeftM];
    }

    if (fullRightM > [src rightMargin])
    {
        [src setRightMargin:fullRightM];
    }

    if (fullTopM > [src topMargin])
    {
        [src setTopMargin:fullTopM];
    }

    if (fullBottomM > [src bottomMargin])
    {
        [src setBottomMargin:fullBottomM];
    }
}

// In AppKit Printing, the rectangle is always oriented. In AppKit Printing, setting
//  the rectangle will always set the orientation.
// In java printing, the rectangle is oriented if accessed from PageFormat. It is
//  not oriented when accessed from Paper.

static void nsPrintInfoToJavaPaper(JNIEnv* env, NSPrintInfo* src, jobject dst)
{
    static JNF_MEMBER_CACHE(jm_setSize, sjc_Paper, "setSize", "(DD)V");
    static JNF_MEMBER_CACHE(jm_setImageableArea, sjc_Paper, "setImageableArea", "(DDDD)V");

    jdouble jPaperW, jPaperH;

    // NSPrintInfo paperSize is oriented. java Paper is not oriented. Take
    //  the -[NSPrintInfo orientation] into account when setting the Paper
    //  rectangle.

    NSSize paperSize = [src paperSize];
    switch ([src orientation]) {
        case NS_PORTRAIT:
            jPaperW = paperSize.width;
            jPaperH = paperSize.height;
            break;

        case NS_LANDSCAPE:
            jPaperW = paperSize.height;
            jPaperH = paperSize.width;
            break;

        default:
            jPaperW = paperSize.width;
            jPaperH = paperSize.height;
            break;
    }

    JNFCallVoidMethod(env, dst, jm_setSize, jPaperW, jPaperH); // AWT_THREADING Safe (known object - always actual Paper)

    // Set the imageable area from the margins
    CGFloat leftM = [src leftMargin];
    CGFloat rightM = [src rightMargin];
    CGFloat topM = [src topMargin];
    CGFloat bottomM = [src bottomMargin];

    jdouble jImageX = leftM;
    jdouble jImageY = topM;
    jdouble jImageW = jPaperW - (leftM + rightM);
    jdouble jImageH = jPaperH - (topM + bottomM);

    JNFCallVoidMethod(env, dst, jm_setImageableArea, jImageX, jImageY, jImageW, jImageH); // AWT_THREADING Safe (known object - always actual Paper)
}

static void javaPaperToNSPrintInfo(JNIEnv* env, jobject src, NSPrintInfo* dst)
{
    AWT_ASSERT_NOT_APPKIT_THREAD;

    static JNF_MEMBER_CACHE(jm_getWidth, sjc_Paper, "getWidth", "()D");
    static JNF_MEMBER_CACHE(jm_getHeight, sjc_Paper, "getHeight", "()D");
    static JNF_MEMBER_CACHE(jm_getImageableX, sjc_Paper, "getImageableX", "()D");
    static JNF_MEMBER_CACHE(jm_getImageableY, sjc_Paper, "getImageableY", "()D");
    static JNF_MEMBER_CACHE(jm_getImageableW, sjc_Paper, "getImageableWidth", "()D");
    static JNF_MEMBER_CACHE(jm_getImageableH, sjc_Paper, "getImageableHeight", "()D");

    // java Paper is always Portrait oriented. Set NSPrintInfo with this
    //  rectangle, and it's orientation may change. If necessary, be sure to call
    //  -[NSPrintInfo setOrientation] after this call, which will then
    //  adjust the -[NSPrintInfo paperSize] as well.

    jdouble jPhysicalWidth = JNFCallDoubleMethod(env, src, jm_getWidth); // AWT_THREADING Safe (!appKit)
    jdouble jPhysicalHeight = JNFCallDoubleMethod(env, src, jm_getHeight); // AWT_THREADING Safe (!appKit)

    [dst setPaperSize:NSMakeSize(jPhysicalWidth, jPhysicalHeight)];

    // Set the margins from the imageable area
    jdouble jImageX = JNFCallDoubleMethod(env, src, jm_getImageableX); // AWT_THREADING Safe (!appKit)
    jdouble jImageY = JNFCallDoubleMethod(env, src, jm_getImageableY); // AWT_THREADING Safe (!appKit)
    jdouble jImageW = JNFCallDoubleMethod(env, src, jm_getImageableW); // AWT_THREADING Safe (!appKit)
    jdouble jImageH = JNFCallDoubleMethod(env, src, jm_getImageableH); // AWT_THREADING Safe (!appKit)

    [dst setLeftMargin:(CGFloat)jImageX];
    [dst setTopMargin:(CGFloat)jImageY];
    [dst setRightMargin:(CGFloat)(jPhysicalWidth - jImageW - jImageX)];
    [dst setBottomMargin:(CGFloat)(jPhysicalHeight - jImageH - jImageY)];
}

static void nsPrintInfoToJavaPageFormat(JNIEnv* env, NSPrintInfo* src, jobject dst)
{
    AWT_ASSERT_NOT_APPKIT_THREAD;

    static JNF_MEMBER_CACHE(jm_setOrientation, sjc_PageFormat, "setOrientation", "(I)V");
    static JNF_MEMBER_CACHE(jm_setPaper, sjc_PageFormat, "setPaper", "(Ljava/awt/print/Paper;)V");
    static JNF_CTOR_CACHE(jm_Paper_ctor, sjc_Paper, "()V");

    jint jOrientation;
    switch ([src orientation]) {
        case NS_PORTRAIT:
            jOrientation = java_awt_print_PageFormat_PORTRAIT;
            break;

        case NS_LANDSCAPE:
            jOrientation = java_awt_print_PageFormat_LANDSCAPE; //+++gdb Are LANDSCAPE and REVERSE_LANDSCAPE still inverted?
            break;

/*
        // AppKit printing doesn't support REVERSE_LANDSCAPE. Radar 2960295.
        case NSReverseLandscapeOrientation:
            jOrientation = java_awt_print_PageFormat.REVERSE_LANDSCAPE; //+++gdb Are LANDSCAPE and REVERSE_LANDSCAPE still inverted?
            break;
*/

        default:
            jOrientation = java_awt_print_PageFormat_PORTRAIT;
            break;
    }

    JNFCallVoidMethod(env, dst, jm_setOrientation, jOrientation); // AWT_THREADING Safe (!appKit)

    // Create a new Paper
    jobject paper = JNFNewObject(env, jm_Paper_ctor); // AWT_THREADING Safe (known object)

    nsPrintInfoToJavaPaper(env, src, paper);

    // Set the Paper in the PageFormat
    JNFCallVoidMethod(env, dst, jm_setPaper, paper); // AWT_THREADING Safe (!appKit)

    (*env)->DeleteLocalRef(env, paper);
}

static void javaPageFormatToNSPrintInfo(JNIEnv* env, jobject srcPrintJob, jobject srcPageFormat, NSPrintInfo* dstPrintInfo)
{
    AWT_ASSERT_NOT_APPKIT_THREAD;

    static JNF_MEMBER_CACHE(jm_getOrientation, sjc_PageFormat, "getOrientation", "()I");
    static JNF_MEMBER_CACHE(jm_getPaper, sjc_PageFormat, "getPaper", "()Ljava/awt/print/Paper;");
    static JNF_MEMBER_CACHE(jm_getPrinterName, sjc_CPrinterJob, "getPrinterName", "()Ljava/lang/String;");

    // When setting page information (orientation, size) in NSPrintInfo, set the
    //  rectangle first. This is because setting the orientation will change the
    //  rectangle to match.

    // Set up the paper. This will force Portrait since java Paper is
    //  not oriented. Then setting the NSPrintInfo orientation below
    //  will flip NSPrintInfo's info as necessary.
    jobject paper = JNFCallObjectMethod(env, srcPageFormat, jm_getPaper); // AWT_THREADING Safe (!appKit)
    javaPaperToNSPrintInfo(env, paper, dstPrintInfo);
    (*env)->DeleteLocalRef(env, paper);

    switch (JNFCallIntMethod(env, srcPageFormat, jm_getOrientation)) { // AWT_THREADING Safe (!appKit)
        case java_awt_print_PageFormat_PORTRAIT:
            [dstPrintInfo setOrientation:NS_PORTRAIT];
            break;

        case java_awt_print_PageFormat_LANDSCAPE:
            [dstPrintInfo setOrientation:NS_LANDSCAPE]; //+++gdb Are LANDSCAPE and REVERSE_LANDSCAPE still inverted?
            break;

        // AppKit printing doesn't support REVERSE_LANDSCAPE. Radar 2960295.
        case java_awt_print_PageFormat_REVERSE_LANDSCAPE:
            [dstPrintInfo setOrientation:NS_LANDSCAPE]; //+++gdb Are LANDSCAPE and REVERSE_LANDSCAPE still inverted?
            break;

        default:
            [dstPrintInfo setOrientation:NS_PORTRAIT];
            break;
    }

    // <rdar://problem/4022422> NSPrinterInfo is not correctly set to the selected printer
    // from the Java side of CPrinterJob. Has always assumed the default printer was the one we wanted.
    if (srcPrintJob == NULL) return;
    jobject printerNameObj = JNFCallObjectMethod(env, srcPrintJob, jm_getPrinterName);
    if (printerNameObj == NULL) return;
    NSString *printerName = JNFJavaToNSString(env, printerNameObj);
    if (printerName == nil) return;
    NSPrinter *printer = [NSPrinter printerWithName:printerName];
    if (printer == nil) return;
    [dstPrintInfo setPrinter:printer];
}

static void nsPrintInfoToJavaPrinterJob(JNIEnv* env, NSPrintInfo* src, jobject dstPrinterJob, jobject dstPageable)
{
    static JNF_MEMBER_CACHE(jm_setService, sjc_CPrinterJob, "setPrinterServiceFromNative", "(Ljava/lang/String;)V");
    static JNF_MEMBER_CACHE(jm_setCopies, sjc_CPrinterJob, "setCopies", "(I)V");
    static JNF_MEMBER_CACHE(jm_setCollated, sjc_CPrinterJob, "setCollated", "(Z)V");
    static JNF_MEMBER_CACHE(jm_setPageRange, sjc_CPrinterJob, "setPageRange", "(II)V");

    // get the selected printer's name, and set the appropriate PrintService on the Java side
    NSString *name = [[src printer] name];
    jstring printerName = JNFNSToJavaString(env, name);
    JNFCallVoidMethod(env, dstPrinterJob, jm_setService, printerName);


    NSMutableDictionary* printingDictionary = [src dictionary];

    NSNumber* nsCopies = [printingDictionary objectForKey:NSPrintCopies];
    if ([nsCopies respondsToSelector:@selector(integerValue)])
    {
        JNFCallVoidMethod(env, dstPrinterJob, jm_setCopies, [nsCopies integerValue]); // AWT_THREADING Safe (known object)
    }

    NSNumber* nsCollated = [printingDictionary objectForKey:NSPrintMustCollate];
    if ([nsCollated respondsToSelector:@selector(boolValue)])
    {
        JNFCallVoidMethod(env, dstPrinterJob, jm_setCollated, [nsCollated boolValue] ? JNI_TRUE : JNI_FALSE); // AWT_THREADING Safe (known object)
    }

    NSNumber* nsPrintAllPages = [printingDictionary objectForKey:NSPrintAllPages];
    if ([nsPrintAllPages respondsToSelector:@selector(boolValue)])
    {
        jint jFirstPage = 0, jLastPage = java_awt_print_Pageable_UNKNOWN_NUMBER_OF_PAGES;
        if (![nsPrintAllPages boolValue])
        {
            NSNumber* nsFirstPage = [printingDictionary objectForKey:NSPrintFirstPage];
            if ([nsFirstPage respondsToSelector:@selector(integerValue)])
            {
                jFirstPage = [nsFirstPage integerValue] - 1;
            }

            NSNumber* nsLastPage = [printingDictionary objectForKey:NSPrintLastPage];
            if ([nsLastPage respondsToSelector:@selector(integerValue)])
            {
                jLastPage = [nsLastPage integerValue] - 1;
            }
        }

        JNFCallVoidMethod(env, dstPrinterJob, jm_setPageRange, jFirstPage, jLastPage); // AWT_THREADING Safe (known object)
    }
}

static void javaPrinterJobToNSPrintInfo(JNIEnv* env, jobject srcPrinterJob, jobject srcPageable, NSPrintInfo* dst)
{
    AWT_ASSERT_NOT_APPKIT_THREAD;

    static JNF_CLASS_CACHE(jc_Pageable, "java/awt/print/Pageable");
    static JNF_MEMBER_CACHE(jm_getCopies, sjc_CPrinterJob, "getCopiesInt", "()I");
    static JNF_MEMBER_CACHE(jm_isCollated, sjc_CPrinterJob, "isCollated", "()Z");
    static JNF_MEMBER_CACHE(jm_getFromPage, sjc_CPrinterJob, "getFromPageAttrib", "()I");
    static JNF_MEMBER_CACHE(jm_getToPage, sjc_CPrinterJob, "getToPageAttrib", "()I");
    static JNF_MEMBER_CACHE(jm_getSelectAttrib, sjc_CPrinterJob, "getSelectAttrib", "()I");
    static JNF_MEMBER_CACHE(jm_getNumberOfPages, jc_Pageable, "getNumberOfPages", "()I");
    static JNF_MEMBER_CACHE(jm_getPageFormat, sjc_CPrinterJob, "getPageFormatFromAttributes", "()Ljava/awt/print/PageFormat;");

    NSMutableDictionary* printingDictionary = [dst dictionary];

    jint copies = JNFCallIntMethod(env, srcPrinterJob, jm_getCopies); // AWT_THREADING Safe (known object)
    [printingDictionary setObject:[NSNumber numberWithInteger:copies] forKey:NSPrintCopies];

    jboolean collated = JNFCallBooleanMethod(env, srcPrinterJob, jm_isCollated); // AWT_THREADING Safe (known object)
    [printingDictionary setObject:[NSNumber numberWithBool:collated ? YES : NO] forKey:NSPrintMustCollate];
    jint jNumPages = JNFCallIntMethod(env, srcPageable, jm_getNumberOfPages); // AWT_THREADING Safe (!appKit)
    if (jNumPages != java_awt_print_Pageable_UNKNOWN_NUMBER_OF_PAGES)
    {
        jint selectID = JNFCallIntMethod(env, srcPrinterJob, jm_getSelectAttrib);
        if (selectID ==0) {
            [printingDictionary setObject:[NSNumber numberWithBool:YES] forKey:NSPrintAllPages];
        } else if (selectID == 2) {
            // In Mac 10.7,  Print ALL is deselected if PrintSelection is YES whether
            // NSPrintAllPages is YES or NO
            [printingDictionary setObject:[NSNumber numberWithBool:NO] forKey:NSPrintAllPages];
            [printingDictionary setObject:[NSNumber numberWithBool:YES] forKey:NSPrintSelectionOnly];
        } else {
            [printingDictionary setObject:[NSNumber numberWithBool:NO] forKey:NSPrintAllPages];
        }

        jint fromPage = JNFCallIntMethod(env, srcPrinterJob, jm_getFromPage);
        jint toPage = JNFCallIntMethod(env, srcPrinterJob, jm_getToPage);
        // setting fromPage and toPage will not be shown in the dialog if printing All pages
        [printingDictionary setObject:[NSNumber numberWithInteger:fromPage] forKey:NSPrintFirstPage];
        [printingDictionary setObject:[NSNumber numberWithInteger:toPage] forKey:NSPrintLastPage];
    }
    else
    {
        [printingDictionary setObject:[NSNumber numberWithBool:YES] forKey:NSPrintAllPages];
    }
    jobject page = JNFCallObjectMethod(env, srcPrinterJob, jm_getPageFormat); 
    if (page != NULL) {
        javaPageFormatToNSPrintInfo(env, NULL, page, dst);
    }
}

/*
 * Class:     sun_lwawt_macosx_CPrinterJob
 * Method:    abortDoc
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_sun_lwawt_macosx_CPrinterJob_abortDoc
  (JNIEnv *env, jobject jthis)
{
JNF_COCOA_ENTER(env);
    // This is only called during the printLoop from the printLoop thread
    NSPrintOperation* printLoop = [NSPrintOperation currentOperation];
    NSPrintInfo* printInfo = [printLoop printInfo];
    [printInfo setJobDisposition:NSPrintCancelJob];
JNF_COCOA_EXIT(env);
}

/*
 * Class:     sun_lwawt_macosx_CPrinterJob
 * Method:    getDefaultPage
 * Signature: (Ljava/awt/print/PageFormat;)V
 */
JNIEXPORT void JNICALL Java_sun_lwawt_macosx_CPrinterJob_getDefaultPage
  (JNIEnv *env, jobject jthis, jobject page)
{
JNF_COCOA_ENTER(env);
    NSPrintInfo* printInfo = createDefaultNSPrintInfo(env, NULL);

    nsPrintInfoToJavaPageFormat(env, printInfo, page);

    [printInfo release];
JNF_COCOA_EXIT(env);
}

/*
 * Class:     sun_lwawt_macosx_CPrinterJob
 * Method:    validatePaper
 * Signature: (Ljava/awt/print/Paper;Ljava/awt/print/Paper;)V
 */
JNIEXPORT void JNICALL Java_sun_lwawt_macosx_CPrinterJob_validatePaper
  (JNIEnv *env, jobject jthis, jobject origpaper, jobject newpaper)
{
JNF_COCOA_ENTER(env);

    NSPrintInfo* printInfo = createDefaultNSPrintInfo(env, NULL);
    javaPaperToNSPrintInfo(env, origpaper, printInfo);
    makeBestFit(printInfo);
    nsPrintInfoToJavaPaper(env, printInfo, newpaper);
    [printInfo release];

JNF_COCOA_EXIT(env);
}

/*
 * Class:     sun_lwawt_macosx_CPrinterJob
 * Method:    createNSPrintInfo
 * Signature: ()J
 */
JNIEXPORT jlong JNICALL Java_sun_lwawt_macosx_CPrinterJob_createNSPrintInfo
  (JNIEnv *env, jobject jthis)
{
    jlong result = -1;
JNF_COCOA_ENTER(env);
    // This is used to create the NSPrintInfo for this PrinterJob. Thread
    //  safety is assured by the java side of this call.

    NSPrintInfo* printInfo = createDefaultNSPrintInfo(env, NULL);

    result = ptr_to_jlong(printInfo);

JNF_COCOA_EXIT(env);
    return result;
}

/*
 * Class:     sun_lwawt_macosx_CPrinterJob
 * Method:    dispose
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_sun_lwawt_macosx_CPrinterJob_dispose
  (JNIEnv *env, jobject jthis, jlong nsPrintInfo)
{
JNF_COCOA_ENTER(env);
    if (nsPrintInfo != -1)
    {
        NSPrintInfo* printInfo = (NSPrintInfo*)jlong_to_ptr(nsPrintInfo);
        [printInfo release];
    }
JNF_COCOA_EXIT(env);
}


/*
 * Class:     sun_lwawt_macosx_CPrinterJob
 * Method:    printLoop
 * Signature: ()V
 */
JNIEXPORT jboolean JNICALL Java_sun_lwawt_macosx_CPrinterJob_printLoop
  (JNIEnv *env, jobject jthis, jboolean blocks, jint firstPage, jint lastPage)
{
    AWT_ASSERT_NOT_APPKIT_THREAD;

    static JNF_MEMBER_CACHE(jm_getPageFormat, sjc_CPrinterJob, "getPageFormat", "(I)Ljava/awt/print/PageFormat;");
    static JNF_MEMBER_CACHE(jm_getPageFormatArea, sjc_CPrinterJob, "getPageFormatArea", "(Ljava/awt/print/PageFormat;)Ljava/awt/geom/Rectangle2D;");
    static JNF_MEMBER_CACHE(jm_getPrinterName, sjc_CPrinterJob, "getPrinterName", "()Ljava/lang/String;");
    static JNF_MEMBER_CACHE(jm_getPageable, sjc_CPrinterJob, "getPageable", "()Ljava/awt/print/Pageable;");

    jboolean retVal = JNI_FALSE;

JNF_COCOA_ENTER(env);
    // Get the first page's PageFormat for setting things up (This introduces
    //  and is a facet of the same problem in Radar 2818593/2708932).
    jobject page = JNFCallObjectMethod(env, jthis, jm_getPageFormat, 0); // AWT_THREADING Safe (!appKit)
    if (page != NULL) {
        jobject pageFormatArea = JNFCallObjectMethod(env, jthis, jm_getPageFormatArea, page); // AWT_THREADING Safe (!appKit)

        PrinterView* printerView = [[PrinterView alloc] initWithFrame:JavaToNSRect(env, pageFormatArea) withEnv:env withPrinterJob:jthis];
        [printerView setFirstPage:firstPage lastPage:lastPage];

        NSPrintInfo* printInfo = (NSPrintInfo*)jlong_to_ptr(JNFCallLongMethod(env, jthis, sjm_getNSPrintInfo)); // AWT_THREADING Safe (known object)

        // <rdar://problem/4156975> passing jthis CPrinterJob as well, so we can extract the printer name from the current job
        javaPageFormatToNSPrintInfo(env, jthis, page, printInfo);

        // <rdar://problem/4093799> NSPrinterInfo is not correctly set to the selected printer
        // from the Java side of CPrinterJob. Had always assumed the default printer was the one we wanted.
        jobject printerNameObj = JNFCallObjectMethod(env, jthis, jm_getPrinterName);
        if (printerNameObj != NULL) {
            NSString *printerName = JNFJavaToNSString(env, printerNameObj);
            if (printerName != nil) {
                NSPrinter *printer = [NSPrinter printerWithName:printerName];
                if (printer != nil) [printInfo setPrinter:printer];
            }
        }

        // <rdar://problem/4367998> JTable.print attributes are ignored
        jobject pageable = JNFCallObjectMethod(env, jthis, jm_getPageable); // AWT_THREADING Safe (!appKit)
        javaPrinterJobToNSPrintInfo(env, jthis, pageable, printInfo);

        PrintModel* printModel = [[PrintModel alloc] initWithPrintInfo:printInfo];

        (void)[printModel runPrintLoopWithView:printerView waitUntilDone:blocks withEnv:env];

        // Only set this if we got far enough to call runPrintLoopWithView, or we will spin CPrinterJob.print() forever!
        retVal = JNI_TRUE;

        [printModel release];
        [printerView release];

        if (page != NULL)
        {
            (*env)->DeleteLocalRef(env, page);
        }

        if (pageFormatArea != NULL)
        {
            (*env)->DeleteLocalRef(env, pageFormatArea);
        }
    }
JNF_COCOA_EXIT(env);
    return retVal;
}

/*
 * Class:     sun_lwawt_macosx_CPrinterPageDialog
 * Method:    showDialog
 * Signature: ()Z
 */
JNIEXPORT jboolean JNICALL Java_sun_lwawt_macosx_CPrinterPageDialog_showDialog
  (JNIEnv *env, jobject jthis)
{

    static JNF_CLASS_CACHE(jc_CPrinterPageDialog, "sun/lwawt/macosx/CPrinterPageDialog");
    static JNF_MEMBER_CACHE(jm_page, jc_CPrinterPageDialog, "fPage", "Ljava/awt/print/PageFormat;");

    jboolean result = JNI_FALSE;
JNF_COCOA_ENTER(env);
    jobject printerJob = JNFGetObjectField(env, jthis, sjm_printerJob);
    NSPrintInfo* printInfo = (NSPrintInfo*)jlong_to_ptr(JNFCallLongMethod(env, printerJob, sjm_getNSPrintInfo)); // AWT_THREADING Safe (known object)

    jobject page = JNFGetObjectField(env, jthis, jm_page);

    // <rdar://problem/4156975> passing NULL, because only a CPrinterJob has a real printer associated with it
    javaPageFormatToNSPrintInfo(env, NULL, page, printInfo);

    PrintModel* printModel = [[PrintModel alloc] initWithPrintInfo:printInfo];
    result = [printModel runPageSetup];
    [printModel release];

    if (result)
    {
        nsPrintInfoToJavaPageFormat(env, printInfo, page);
    }

    if (printerJob != NULL)
    {
        (*env)->DeleteLocalRef(env, printerJob);
    }

    if (page != NULL)
    {
        (*env)->DeleteLocalRef(env, page);
    }

JNF_COCOA_EXIT(env);
    return result;
}

/*
 * Class:     sun_lwawt_macosx_CPrinterJobDialog
 * Method:    showDialog
 * Signature: ()Z
 */
JNIEXPORT jboolean JNICALL Java_sun_lwawt_macosx_CPrinterJobDialog_showDialog
  (JNIEnv *env, jobject jthis)
{
    static JNF_CLASS_CACHE(jc_CPrinterJobDialog, "sun/lwawt/macosx/CPrinterJobDialog");
    static JNF_MEMBER_CACHE(jm_pageable, jc_CPrinterJobDialog, "fPageable", "Ljava/awt/print/Pageable;");

    jboolean result = JNI_FALSE;
JNF_COCOA_ENTER(env);
    jobject printerJob = JNFGetObjectField(env, jthis, sjm_printerJob);
    NSPrintInfo* printInfo = (NSPrintInfo*)jlong_to_ptr(JNFCallLongMethod(env, printerJob, sjm_getNSPrintInfo)); // AWT_THREADING Safe (known object)

    jobject pageable = JNFGetObjectField(env, jthis, jm_pageable);

    javaPrinterJobToNSPrintInfo(env, printerJob, pageable, printInfo);

    PrintModel* printModel = [[PrintModel alloc] initWithPrintInfo:printInfo];
    result = [printModel runJobSetup];
    [printModel release];

    if (result)
    {
        nsPrintInfoToJavaPrinterJob(env, printInfo, printerJob, pageable);
    }

    if (printerJob != NULL)
    {
        (*env)->DeleteLocalRef(env, printerJob);
    }

    if (pageable != NULL)
    {
        (*env)->DeleteLocalRef(env, pageable);
    }

JNF_COCOA_EXIT(env);
    return result;
}
