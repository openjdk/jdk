/*
 * Copyright (c) 2011, 2026, Oracle and/or its affiliates. All rights reserved.
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

#import "PrinterView.h"
#import "PrintModel.h"
#import "ThreadUtilities.h"
#import "GeomUtilities.h"
#import "JNIUtilities.h"

#define ONE_SIDED 0
#define TWO_SIDED_LONG_EDGE 1
#define TWO_SIDED_SHORT_EDGE 2

static jclass sjc_Paper = NULL;
static jclass sjc_PageFormat = NULL;
static jclass sjc_CPrinterJob = NULL;
static jclass sjc_CPrinterDialog = NULL;
static jmethodID sjm_getNSPrintInfo = NULL;
static jmethodID sjm_printerJob = NULL;

#define GET_PAPER_CLASS() GET_CLASS(sjc_Paper, "java/awt/print/Paper");
#define GET_PAGEFORMAT_CLASS() GET_CLASS(sjc_PageFormat, "java/awt/print/PageFormat");
#define GET_CPRINTERDIALOG_CLASS() GET_CLASS(sjc_CPrinterDialog, "sun/lwawt/macosx/CPrinterDialog");
#define GET_CPRINTERDIALOG_CLASS_RETURN(ret) GET_CLASS_RETURN(sjc_CPrinterDialog, "sun/lwawt/macosx/CPrinterDialog", ret);
#define GET_CPRINTERJOB_CLASS() GET_CLASS(sjc_CPrinterJob, "sun/lwawt/macosx/CPrinterJob");
#define GET_CPRINTERJOB_CLASS_RETURN(ret) GET_CLASS_RETURN(sjc_CPrinterJob, "sun/lwawt/macosx/CPrinterJob", ret);

#define GET_NSPRINTINFO_METHOD_RETURN(ret) \
    GET_CPRINTERJOB_CLASS_RETURN(ret); \
    GET_METHOD_RETURN(sjm_getNSPrintInfo, sjc_CPrinterJob, "getNSPrintInfo", "()J", ret);

#define GET_CPRINTERDIALOG_FIELD_RETURN(ret) \
   GET_CPRINTERDIALOG_CLASS_RETURN(ret); \
   GET_FIELD_RETURN(sjm_printerJob, sjc_CPrinterDialog, "fPrinterJob", "Lsun/lwawt/macosx/CPrinterJob;", ret);

static NSPrintInfo* createDefaultNSPrintInfo(JNIEnv* env, jstring printer);

static void makeBestFit(NSPrintInfo* src);
static NSPrinter* getPrinter(JNIEnv* env, jobject srcPrintJob);
static PMPageFormat getPageFormat(JNIEnv* env, NSPrintInfo* printInfo, jobject paper);
static PMPaper findExistedPaper(JNIEnv* env, NSPrintInfo* printInfo, jobject paper);

static void nsPrintInfoToJavaPaper(JNIEnv* env, NSPrintInfo* src, jobject dst);
static void javaPaperToNSPrintInfo(JNIEnv* env, jobject src, NSPrintInfo* dst, PMOrientation orientation);

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
        NSPrinter* nsPrinter = [NSPrinter printerWithName:JavaStringToNSString(env, printer)];
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


static NSPrinter* getPrinter(JNIEnv* env, jobject srcPrintJob)
{
    GET_CPRINTERJOB_CLASS_RETURN(NULL);
    DECLARE_METHOD_RETURN(jm_getPrinterName, sjc_CPrinterJob, "getPrinterName", "()Ljava/lang/String;", NULL);

    // <rdar://problem/4022422> NSPrinterInfo is not correctly set to the selected printer
    // from the Java side of CPrinterJob. Has always assumed the default printer was the one we wanted.
    if (srcPrintJob == NULL)
        return NULL;
    jobject printerNameObj = (*env)->CallObjectMethod(env, srcPrintJob, jm_getPrinterName);
    CHECK_EXCEPTION();
    if (printerNameObj == NULL)
        return NULL;
    NSString *printerName = JavaStringToNSString(env, printerNameObj);
    if (printerName == nil)
        return NULL;
    return [NSPrinter printerWithName:printerName];
}

static PMPaper findExistedPaper(JNIEnv* env, NSPrintInfo* printInfo, jobject paper)
{
    GET_CLASS_RETURN(sjc_Paper, "java/awt/print/Paper", NULL);
    DECLARE_METHOD_RETURN(jm_getWidth, sjc_Paper, "getWidth", "()D", NULL);
    DECLARE_METHOD_RETURN(jm_getHeight, sjc_Paper, "getHeight", "()D", NULL);
    DECLARE_METHOD_RETURN(jm_getImageableX, sjc_Paper, "getImageableX", "()D", NULL);
    DECLARE_METHOD_RETURN(jm_getImageableY, sjc_Paper, "getImageableY", "()D", NULL);
    DECLARE_METHOD_RETURN(jm_getImageableW, sjc_Paper, "getImageableWidth", "()D", NULL);
    DECLARE_METHOD_RETURN(jm_getImageableH, sjc_Paper, "getImageableHeight", "()D", NULL);

    jdouble jPaperW = (*env)->CallDoubleMethod(env, paper, jm_getWidth);
    CHECK_EXCEPTION();
    jdouble jPaperH = (*env)->CallDoubleMethod(env, paper, jm_getHeight);
    CHECK_EXCEPTION();
    jdouble jMarginLeft = (*env)->CallDoubleMethod(env, paper, jm_getImageableX);
    CHECK_EXCEPTION();
    jdouble jMarginTop = (*env)->CallDoubleMethod(env, paper, jm_getImageableY);
    CHECK_EXCEPTION();
    jdouble jImageW = (*env)->CallDoubleMethod(env, paper, jm_getImageableW);
    CHECK_EXCEPTION();
    jdouble jImageH = (*env)->CallDoubleMethod(env, paper, jm_getImageableH);
    CHECK_EXCEPTION();

    jdouble jMarginRight = jPaperW - (jImageW + jMarginLeft);
    jdouble jMarginBottom = jPaperH - (jImageH + jMarginTop);

    PMPrintSession printSession = (PMPrintSession) ([printInfo PMPrintSession]);
    PMPrinter printer = NULL;
    if (PMSessionGetCurrentPrinter(printSession, &printer) != noErr)
    {
        return NULL;
    }

    CFArrayRef availablePapers = NULL;
    if (PMPrinterGetPaperList(printer, &availablePapers) != noErr)
    {
        return NULL;
    }

    PMPaper result = NULL;
    CFIndex paperCount = CFArrayGetCount(availablePapers);

    double actualDifference = 99999999.99;
    double acceptableDifference = 1.5;
    for (CFIndex i = 0; i < paperCount; i++)
    {
        PMPaper paper = (PMPaper)CFArrayGetValueAtIndex(availablePapers, i);
        double paperWidth = 0.0;
        double paperHeight = 0.0;
        PMPaperGetWidth(paper, &paperWidth);
        PMPaperGetHeight(paper, &paperHeight);

        double widthDiff = jPaperW - paperWidth;
        double heightDiff = jPaperH - paperHeight;
        double difference = fmax(fabs(widthDiff), fabs(heightDiff));

        if (difference > acceptableDifference || difference > actualDifference)
        {
            continue;
        }

        PMPaperMargins margins;
        if (difference < actualDifference && PMPaperGetMargins(paper, &margins) == noErr)
        {
            if(jMarginLeft >= margins.left && jMarginRight >= margins.right &&
                jMarginTop >= margins.top && jMarginBottom >= margins.bottom)
            {
                result = paper;
                actualDifference = difference;
                // Borderless printing is slower than a printing with margins. Try to find a paper with margins.
                if (difference < 0.85 && margins.left > 0 && margins.right > 0 && margins.top > 0 && margins.bottom > 0)
                {
                    break;
                }
            }
        }
    }
    return result;
}

static PMPageFormat getPageFormat(JNIEnv* env, NSPrintInfo* printInfo, jobject jPaper)
{
    GET_CLASS_RETURN(sjc_Paper, "java/awt/print/Paper", NULL);
    DECLARE_METHOD_RETURN(jm_getWidth, sjc_Paper, "getWidth", "()D", NULL);
    DECLARE_METHOD_RETURN(jm_getHeight, sjc_Paper, "getHeight", "()D", NULL);
    DECLARE_METHOD_RETURN(jm_getImageableX, sjc_Paper, "getImageableX", "()D", NULL);
    DECLARE_METHOD_RETURN(jm_getImageableY, sjc_Paper, "getImageableY", "()D", NULL);
    DECLARE_METHOD_RETURN(jm_getImageableW, sjc_Paper, "getImageableWidth", "()D", NULL);
    DECLARE_METHOD_RETURN(jm_getImageableH, sjc_Paper, "getImageableHeight", "()D", NULL);

    OSStatus status;
    PMPageFormat pageFormat;
    PMPaper pmPaper = findExistedPaper(env, printInfo, jPaper);
    if (pmPaper != NULL)
    {
        status = PMCreatePageFormatWithPMPaper(&pageFormat, pmPaper);
    } else
    {
        jdouble jPaperW = (*env)->CallDoubleMethod(env, jPaper, jm_getWidth);
        CHECK_EXCEPTION();
        jdouble jPaperH = (*env)->CallDoubleMethod(env, jPaper, jm_getHeight);
        CHECK_EXCEPTION();
        jdouble jImageX = (*env)->CallDoubleMethod(env, jPaper, jm_getImageableX);
        CHECK_EXCEPTION();
        jdouble jImageY = (*env)->CallDoubleMethod(env, jPaper, jm_getImageableY);
        CHECK_EXCEPTION();
        jdouble jImageW = (*env)->CallDoubleMethod(env, jPaper, jm_getImageableW);
        CHECK_EXCEPTION();
        jdouble jImageH = (*env)->CallDoubleMethod(env, jPaper, jm_getImageableH);
        CHECK_EXCEPTION();

        PMPaperMargins paperMargins;
        paperMargins.left = jImageX;
        paperMargins.top = jImageY;
        paperMargins.right = jPaperW - (jImageW + jImageX);
        paperMargins.bottom = jPaperH - (jImageH + jImageY);

        PMPrintSession printSession = (PMPrintSession) [printInfo PMPrintSession];
        PMPrinter pmPrinter;

        if (PMSessionGetCurrentPrinter(printSession, &pmPrinter) != noErr
            || PMPaperCreateCustom(pmPrinter, CFSTR("Java paper"), CFSTR("Java paper"),
                                    jPaperW, jPaperH, &paperMargins, &pmPaper) != noErr)
        {
            return NULL;
        }
        status = PMCreatePageFormatWithPMPaper(&pageFormat, pmPaper);
        PMRelease(pmPaper);
    }

    return status == noErr ? pageFormat : NULL;
}

// In AppKit Printing, the rectangle is always oriented. In AppKit Printing, setting
//  the rectangle will always set the orientation.
// In java printing, the rectangle is oriented if accessed from PageFormat. It is
//  not oriented when accessed from Paper.

static void nsPrintInfoToJavaPaper(JNIEnv* env, NSPrintInfo* src, jobject dst)
{
    GET_PAGEFORMAT_CLASS();
    GET_PAPER_CLASS();
    DECLARE_METHOD(jm_setSize, sjc_Paper, "setSize", "(DD)V");
    DECLARE_METHOD(jm_setImageableArea, sjc_Paper, "setImageableArea", "(DDDD)V");

    PMPaper pmPaper;
    PMOrientation pmOrientation;
    PMPaperMargins pmMargins;
    double paperWidth;
    double paperHeight;
    if (PMGetPageFormatPaper([src PMPageFormat], &pmPaper) != noErr ||
            PMPaperGetWidth(pmPaper, &paperWidth) != noErr ||
            PMPaperGetHeight(pmPaper, &paperHeight) != noErr ||
            PMGetOrientation([src PMPageFormat], &pmOrientation) != noErr) {
        return;
    }

    jdouble jPaperW = paperWidth;
    jdouble jPaperH = paperHeight;

    (*env)->CallVoidMethod(env, dst, jm_setSize, jPaperW, jPaperH); // AWT_THREADING Safe (known object - always actual Paper)
    CHECK_EXCEPTION();

    // Should set user's margins, not paper's.
    jdouble jImageX ,jImageY ,jImageW, jImageH;
    switch (pmOrientation) {
        case kPMLandscape:
            jImageX = [src topMargin];
            jImageY = [src rightMargin];
            jImageW = jPaperW - (jImageX + [src bottomMargin]);
            jImageH = jPaperH - (jImageY + [src leftMargin]);
            break;
        case kPMReverseLandscape:
            jImageX = [src bottomMargin];
            jImageY = [src leftMargin];
            jImageW = jPaperW - (jImageX + [src topMargin]);
            jImageH = jPaperH - (jImageY + [src rightMargin]);
            break;
        case kPMReversePortrait:
            jImageX = [src rightMargin];
            jImageY = [src bottomMargin];
            jImageW = jPaperW - (jImageX + [src leftMargin]);
            jImageH = jPaperH - (jImageY + [src topMargin]);
        case kPMPortrait:
        default:
            jImageX = [src leftMargin];
            jImageY = [src topMargin];
            jImageW = jPaperW - (jImageX + [src rightMargin]);
            jImageH = jPaperH - (jImageY + [src bottomMargin]);
            break;
    }

    (*env)->CallVoidMethod(env, dst, jm_setImageableArea, jImageX, jImageY, jImageW, jImageH); // AWT_THREADING Safe (known object - always actual Paper)
    CHECK_EXCEPTION();
}

static void javaPaperToNSPrintInfo(JNIEnv* env, jobject src, NSPrintInfo* dst, PMOrientation orientation)
{
    AWT_ASSERT_NOT_APPKIT_THREAD;

    GET_PAGEFORMAT_CLASS();
    GET_PAPER_CLASS();
    DECLARE_METHOD(jm_getWidth, sjc_Paper, "getWidth", "()D");
    DECLARE_METHOD(jm_getHeight, sjc_Paper, "getHeight", "()D");
    DECLARE_METHOD(jm_getImageableX, sjc_Paper, "getImageableX", "()D");
    DECLARE_METHOD(jm_getImageableY, sjc_Paper, "getImageableY", "()D");
    DECLARE_METHOD(jm_getImageableW, sjc_Paper, "getImageableWidth", "()D");
    DECLARE_METHOD(jm_getImageableH, sjc_Paper, "getImageableHeight", "()D");

    PMPageFormat pageFormat = getPageFormat(env, dst, src);
    if (pageFormat == NULL)
    {
        return;
    }
    PMPrintSession printSession = (PMPrintSession) [dst PMPrintSession];
    Boolean updated;

    if (PMSetOrientation(pageFormat, orientation, kPMUnlocked) != noErr
        || PMSessionValidatePageFormat(printSession, pageFormat, &updated) != noErr
        || PMCopyPageFormat(pageFormat, [dst PMPageFormat]) != noErr
        || updated == YES)
    {
        PMRelease(pageFormat);
        return;
    }
    [dst updateFromPMPrintSettings];

    jdouble jPaperW = (*env)->CallDoubleMethod(env, src, jm_getWidth);
    CHECK_EXCEPTION();
    jdouble jPaperH = (*env)->CallDoubleMethod(env, src, jm_getHeight);
    CHECK_EXCEPTION();
    jdouble jMarginLeft = (*env)->CallDoubleMethod(env, src, jm_getImageableX);
    CHECK_EXCEPTION();
    jdouble jMarginTop = (*env)->CallDoubleMethod(env, src, jm_getImageableY);
    CHECK_EXCEPTION();
    jdouble jImageW = (*env)->CallDoubleMethod(env, src, jm_getImageableW);
    CHECK_EXCEPTION();
    jdouble jImageH = (*env)->CallDoubleMethod(env, src, jm_getImageableH);
    CHECK_EXCEPTION();

    jdouble jMarginRight = jPaperW - (jImageW + jMarginLeft);
    jdouble jMarginBottom = jPaperH - (jImageH + jMarginTop);

    switch (orientation) {
        case kPMLandscape:
            [dst setTopMargin: jMarginLeft];
            [dst setRightMargin: jMarginTop];
            [dst setBottomMargin: jMarginRight];
            [dst setLeftMargin: jMarginBottom];
            break;
        case kPMReverseLandscape:
            [dst setTopMargin: jMarginRight];
            [dst setRightMargin: jMarginBottom];
            [dst setBottomMargin: jMarginLeft];
            [dst setLeftMargin: jMarginTop];
            break;
        case kPMReversePortrait:
            [dst setRightMargin: jMarginLeft];
            [dst setBottomMargin: jMarginTop];
            [dst setLeftMargin: jMarginRight];
            [dst setTopMargin: jMarginBottom];
        case kPMPortrait:
        default:
            [dst setLeftMargin: jMarginLeft];
            [dst setTopMargin: jMarginTop];
            [dst setRightMargin: jMarginRight];
            [dst setBottomMargin: jMarginBottom];
            break;
    }

    PMRelease(pageFormat);
}

static void nsPrintInfoToJavaPageFormat(JNIEnv* env, NSPrintInfo* src, jobject dst)
{
    AWT_ASSERT_NOT_APPKIT_THREAD;

    GET_CPRINTERJOB_CLASS();
    GET_PAGEFORMAT_CLASS();
    GET_PAPER_CLASS();
    DECLARE_METHOD(jm_setOrientation, sjc_PageFormat, "setOrientation", "(I)V");
    DECLARE_METHOD(jm_setPaper, sjc_PageFormat, "setPaper", "(Ljava/awt/print/Paper;)V");
    DECLARE_METHOD(jm_Paper_ctor, sjc_Paper, "<init>", "()V");

    PMOrientation pmOrientation;
    if (PMGetOrientation([src PMPageFormat], &pmOrientation) != noErr) {
        return;
    }

    jint jOrientation = java_awt_print_PageFormat_PORTRAIT;
    switch (pmOrientation) {
        case kPMPortrait:
        case kPMReversePortrait:
            jOrientation = java_awt_print_PageFormat_PORTRAIT;
            break;
        case kPMLandscape:
            jOrientation = java_awt_print_PageFormat_LANDSCAPE;
            break;
        case kPMReverseLandscape:
            jOrientation = java_awt_print_PageFormat_REVERSE_LANDSCAPE;
            break;
        default:
            jOrientation = java_awt_print_PageFormat_PORTRAIT;
            break;
    }

    (*env)->CallVoidMethod(env, dst, jm_setOrientation, jOrientation); // AWT_THREADING Safe (!appKit)
    CHECK_EXCEPTION();

    // Create a new Paper
    jobject paper = (*env)->NewObject(env, sjc_Paper, jm_Paper_ctor); // AWT_THREADING Safe (known object)
    CHECK_EXCEPTION();
    if (paper == NULL) {
        return;
    }

    nsPrintInfoToJavaPaper(env, src, paper);

    // Set the Paper in the PageFormat
    (*env)->CallVoidMethod(env, dst, jm_setPaper, paper); // AWT_THREADING Safe (!appKit)
    CHECK_EXCEPTION();

    (*env)->DeleteLocalRef(env, paper);
}

static void javaPageFormatToNSPrintInfo(JNIEnv* env, jobject srcPrintJob, jobject srcPageFormat, NSPrintInfo* dstPrintInfo)
{
    AWT_ASSERT_NOT_APPKIT_THREAD;

    GET_CPRINTERJOB_CLASS();
    GET_PAGEFORMAT_CLASS();
    GET_PAPER_CLASS();
    DECLARE_METHOD(jm_getOrientation, sjc_PageFormat, "getOrientation", "()I");
    DECLARE_METHOD(jm_getPaper, sjc_PageFormat, "getPaper", "()Ljava/awt/print/Paper;");
    DECLARE_METHOD(jm_getPrinterName, sjc_CPrinterJob, "getPrinterName", "()Ljava/lang/String;");

    NSPrinter *printer = getPrinter(env, srcPrintJob);
    if (printer != nil)
    {
        [dstPrintInfo setPrinter:printer];
    }

    jobject paper = (*env)->CallObjectMethod(env, srcPageFormat, jm_getPaper); // AWT_THREADING Safe (!appKit)
    CHECK_EXCEPTION();

    jint jOrientation = (*env)->CallIntMethod(env, srcPageFormat, jm_getOrientation);
    CHECK_EXCEPTION();

    PMOrientation orientation;
    switch (jOrientation)
    {
        case java_awt_print_PageFormat_PORTRAIT:
            orientation = kPMPortrait;
            break;

        case java_awt_print_PageFormat_LANDSCAPE:
            orientation = kPMLandscape;
            break;

        case java_awt_print_PageFormat_REVERSE_LANDSCAPE:
            orientation = kPMReverseLandscape;
            break;

        default:
            orientation = kPMPortrait;
    }

    javaPaperToNSPrintInfo(env, paper, dstPrintInfo, orientation);

    (*env)->DeleteLocalRef(env, paper);
}

static jint duplexModeToSides(PMDuplexMode duplexMode) {
    switch(duplexMode) {
        case kPMDuplexNone: return ONE_SIDED;
        case kPMDuplexTumble: return TWO_SIDED_SHORT_EDGE;
        case kPMDuplexNoTumble: return TWO_SIDED_LONG_EDGE;
        default: return -1;
    }
}

static PMDuplexMode sidesToDuplexMode(jint sides) {
    switch(sides) {
        case ONE_SIDED: return kPMDuplexNone;
        case TWO_SIDED_SHORT_EDGE: return kPMDuplexTumble;
        case TWO_SIDED_LONG_EDGE: return kPMDuplexNoTumble;
        default: return kPMDuplexNone;
    }
}

static void nsPrintInfoToJavaPrinterJob(JNIEnv* env, NSPrintInfo* src, jobject dstPrinterJob, jobject dstPageable)
{
    GET_CPRINTERJOB_CLASS();
    DECLARE_METHOD(jm_setService, sjc_CPrinterJob, "setPrinterServiceFromNative", "(Ljava/lang/String;)V");
    DECLARE_METHOD(jm_setCopiesAttribute, sjc_CPrinterJob, "setCopiesAttribute", "(I)V");
    DECLARE_METHOD(jm_setCollated, sjc_CPrinterJob, "setCollated", "(Z)V");
    DECLARE_METHOD(jm_setPageRangeAttribute, sjc_CPrinterJob, "setPageRangeAttribute", "(IIZ)V");
    DECLARE_METHOD(jm_setPrintToFile, sjc_CPrinterJob, "setPrintToFile", "(Z)V");
    DECLARE_METHOD(jm_setDestinationFile, sjc_CPrinterJob, "setDestinationFile", "(Ljava/lang/String;)V");
    DECLARE_METHOD(jm_setSides, sjc_CPrinterJob, "setSides", "(I)V");
    DECLARE_METHOD(jm_setOutputBin, sjc_CPrinterJob, "setOutputBin", "(Ljava/lang/String;)V");

    // get the selected printer's name, and set the appropriate PrintService on the Java side
    NSString *name = [[src printer] name];
    jstring printerName = NSStringToJavaString(env, name);
    (*env)->CallVoidMethod(env, dstPrinterJob, jm_setService, printerName);
    CHECK_EXCEPTION();

    NSMutableDictionary* printingDictionary = [src dictionary];

    if (src.jobDisposition == NSPrintSaveJob) {
        (*env)->CallVoidMethod(env, dstPrinterJob, jm_setPrintToFile, true);
        CHECK_EXCEPTION();
        NSURL *url = [printingDictionary objectForKey:NSPrintJobSavingURL];
        NSString *nsStr = [url absoluteString];
        jstring str = NSStringToJavaString(env, nsStr);
        (*env)->CallVoidMethod(env, dstPrinterJob, jm_setDestinationFile, str);
        CHECK_EXCEPTION();
    } else {
        (*env)->CallVoidMethod(env, dstPrinterJob, jm_setPrintToFile, false);
        CHECK_EXCEPTION();
    }

    NSNumber* nsCopies = [printingDictionary objectForKey:NSPrintCopies];
    if ([nsCopies respondsToSelector:@selector(integerValue)])
    {
        (*env)->CallVoidMethod(env, dstPrinterJob, jm_setCopiesAttribute, [nsCopies integerValue]); // AWT_THREADING Safe (known object)
        CHECK_EXCEPTION();
    }

    NSNumber* nsCollated = [printingDictionary objectForKey:NSPrintMustCollate];
    if ([nsCollated respondsToSelector:@selector(boolValue)])
    {
        (*env)->CallVoidMethod(env, dstPrinterJob, jm_setCollated, [nsCollated boolValue] ? JNI_TRUE : JNI_FALSE); // AWT_THREADING Safe (known object)
        CHECK_EXCEPTION();
    }

    NSNumber* nsPrintAllPages = [printingDictionary objectForKey:NSPrintAllPages];
    if ([nsPrintAllPages respondsToSelector:@selector(boolValue)])
    {
        jint jFirstPage = 0, jLastPage = java_awt_print_Pageable_UNKNOWN_NUMBER_OF_PAGES;
        jboolean isRangeSet = false;
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
            isRangeSet = true;
        }
        (*env)->CallVoidMethod(env, dstPrinterJob, jm_setPageRangeAttribute,
                          jFirstPage, jLastPage, isRangeSet); // AWT_THREADING Safe (known object)
        CHECK_EXCEPTION();

        PMDuplexMode duplexSetting;
        if (PMGetDuplex(src.PMPrintSettings, &duplexSetting) == noErr) {
            jint sides = duplexModeToSides(duplexSetting);
            (*env)->CallVoidMethod(env, dstPrinterJob, jm_setSides, sides); // AWT_THREADING Safe (known object)
            CHECK_EXCEPTION();
        }

        NSString* outputBin = [[src printSettings] objectForKey:@"OutputBin"];
        if (outputBin != nil) {
            jstring outputBinName = NSStringToJavaString(env, outputBin);
            (*env)->CallVoidMethod(env, dstPrinterJob, jm_setOutputBin, outputBinName);
            CHECK_EXCEPTION();
        }
    }
}

static void javaPrinterJobToNSPrintInfo(JNIEnv* env, jobject srcPrinterJob, jobject srcPageable, NSPrintInfo* dst)
{
    AWT_ASSERT_NOT_APPKIT_THREAD;

    DECLARE_CLASS(jc_Pageable, "java/awt/print/Pageable");
    DECLARE_METHOD(jm_getCopies, sjc_CPrinterJob, "getCopiesInt", "()I");
    DECLARE_METHOD(jm_isCollated, sjc_CPrinterJob, "isCollated", "()Z");
    DECLARE_METHOD(jm_getFromPage, sjc_CPrinterJob, "getFromPageAttrib", "()I");
    DECLARE_METHOD(jm_getToPage, sjc_CPrinterJob, "getToPageAttrib", "()I");
    DECLARE_METHOD(jm_getMinPage, sjc_CPrinterJob, "getMinPageAttrib", "()I");
    DECLARE_METHOD(jm_getMaxPage, sjc_CPrinterJob, "getMaxPageAttrib", "()I");
    DECLARE_METHOD(jm_getSelectAttrib, sjc_CPrinterJob, "getSelectAttrib", "()I");
    DECLARE_METHOD(jm_getNumberOfPages, jc_Pageable, "getNumberOfPages", "()I");
    DECLARE_METHOD(jm_getPageFormat, sjc_CPrinterJob, "getPageFormatFromAttributes", "()Ljava/awt/print/PageFormat;");
    DECLARE_METHOD(jm_getDestinationFile, sjc_CPrinterJob, "getDestinationFile", "()Ljava/lang/String;");
    DECLARE_METHOD(jm_getSides, sjc_CPrinterJob, "getSides", "()I");
    DECLARE_METHOD(jm_getOutputBin, sjc_CPrinterJob, "getOutputBin", "()Ljava/lang/String;");


    NSMutableDictionary* printingDictionary = [dst dictionary];

    jint copies = (*env)->CallIntMethod(env, srcPrinterJob, jm_getCopies); // AWT_THREADING Safe (known object)
    CHECK_EXCEPTION();
    [printingDictionary setObject:[NSNumber numberWithInteger:copies] forKey:NSPrintCopies];

    jboolean collated = (*env)->CallBooleanMethod(env, srcPrinterJob, jm_isCollated); // AWT_THREADING Safe (known object)
    CHECK_EXCEPTION();
    [printingDictionary setObject:[NSNumber numberWithBool:collated ? YES : NO] forKey:NSPrintMustCollate];
    jint selectID = (*env)->CallIntMethod(env, srcPrinterJob, jm_getSelectAttrib);
    CHECK_EXCEPTION();
    jint fromPage = (*env)->CallIntMethod(env, srcPrinterJob, jm_getFromPage);
    CHECK_EXCEPTION();
    jint toPage = (*env)->CallIntMethod(env, srcPrinterJob, jm_getToPage);
    CHECK_EXCEPTION();
    if (selectID ==0) {
        [printingDictionary setObject:[NSNumber numberWithBool:YES] forKey:NSPrintAllPages];
    } else if (selectID == 2) {
        // In Mac 10.7,  Print ALL is deselected if PrintSelection is YES whether
        // NSPrintAllPages is YES or NO
        [printingDictionary setObject:[NSNumber numberWithBool:NO] forKey:NSPrintAllPages];
        [printingDictionary setObject:[NSNumber numberWithBool:YES] forKey:NSPrintSelectionOnly];
    } else {
        jint minPage = (*env)->CallIntMethod(env, srcPrinterJob, jm_getMinPage);
        CHECK_EXCEPTION();
        jint maxPage = (*env)->CallIntMethod(env, srcPrinterJob, jm_getMaxPage);
        CHECK_EXCEPTION();

        // for PD_SELECTION or PD_NOSELECTION, check from/to page
        // to determine which radio button to select
        if (fromPage > minPage || toPage < maxPage) {
            [printingDictionary setObject:[NSNumber numberWithBool:NO] forKey:NSPrintAllPages];
        } else {
            [printingDictionary setObject:[NSNumber numberWithBool:YES] forKey:NSPrintAllPages];
        }
    }

    // setting fromPage and toPage will not be shown in the dialog if printing All pages
    [printingDictionary setObject:[NSNumber numberWithInteger:fromPage] forKey:NSPrintFirstPage];
    [printingDictionary setObject:[NSNumber numberWithInteger:toPage] forKey:NSPrintLastPage];

    jobject page = (*env)->CallObjectMethod(env, srcPrinterJob, jm_getPageFormat);
    CHECK_EXCEPTION();
    if (page != NULL) {
        javaPageFormatToNSPrintInfo(env, NULL, page, dst);
    }

    jstring dest = (*env)->CallObjectMethod(env, srcPrinterJob, jm_getDestinationFile);
    CHECK_EXCEPTION();
    if (dest != NULL) {
       [dst setJobDisposition:NSPrintSaveJob];
       NSString *nsDestStr = JavaStringToNSString(env, dest);
       NSURL *nsURL = [NSURL fileURLWithPath:nsDestStr isDirectory:NO];
       [printingDictionary setObject:nsURL forKey:NSPrintJobSavingURL];
    } else {
       [dst setJobDisposition:NSPrintSpoolJob];
    }

    jint sides = (*env)->CallIntMethod(env, srcPrinterJob, jm_getSides);
    CHECK_EXCEPTION();

    if (sides >= 0) {
        PMDuplexMode duplexMode = sidesToDuplexMode(sides);
        PMPrintSettings printSettings = dst.PMPrintSettings;
        if (PMSetDuplex(printSettings, duplexMode) == noErr) {
            [dst updateFromPMPrintSettings];
        }
    }

    jobject outputBin = (*env)->CallObjectMethod(env, srcPrinterJob, jm_getOutputBin);
    CHECK_EXCEPTION();
    if (outputBin != NULL) {
        NSString *nsOutputBinStr = JavaStringToNSString(env, outputBin);
        if (nsOutputBinStr != nil) {
            [[dst printSettings] setObject:nsOutputBinStr forKey:@"OutputBin"];
        }
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
JNI_COCOA_ENTER(env);
    // This is only called during the printLoop from the printLoop thread
    NSPrintOperation* printLoop = [NSPrintOperation currentOperation];
    NSPrintInfo* printInfo = [printLoop printInfo];
    [printInfo setJobDisposition:NSPrintCancelJob];
JNI_COCOA_EXIT(env);
}

/*
 * Class:     sun_lwawt_macosx_CPrinterJob
 * Method:    getDefaultPage
 * Signature: (Ljava/awt/print/PageFormat;)V
 */
JNIEXPORT void JNICALL Java_sun_lwawt_macosx_CPrinterJob_getDefaultPage
  (JNIEnv *env, jobject jthis, jobject page)
{
JNI_COCOA_ENTER(env);
    NSPrintInfo* printInfo = createDefaultNSPrintInfo(env, NULL);

    nsPrintInfoToJavaPageFormat(env, printInfo, page);

    [printInfo release];
JNI_COCOA_EXIT(env);
}

/*
 * Class:     sun_lwawt_macosx_CPrinterJob
 * Method:    validatePaper
 * Signature: (Ljava/awt/print/Paper;Ljava/awt/print/Paper;)V
 */
JNIEXPORT void JNICALL Java_sun_lwawt_macosx_CPrinterJob_validatePaper
  (JNIEnv *env, jobject jthis, jobject origpaper, jobject newpaper, jint jorientation)
{
JNI_COCOA_ENTER(env);

    PMOrientation orientation;
    switch (jorientation)
    {
        case java_awt_print_PageFormat_PORTRAIT:
            orientation = kPMPortrait;
            break;

        case java_awt_print_PageFormat_LANDSCAPE:
            orientation = kPMLandscape;
            break;

        case java_awt_print_PageFormat_REVERSE_LANDSCAPE:
            orientation = kPMReverseLandscape;
            break;

        default:
            orientation = kPMPortrait;
    }

    NSPrintInfo* printInfo = createDefaultNSPrintInfo(env, NULL);
    javaPaperToNSPrintInfo(env, origpaper, printInfo, orientation);
    makeBestFit(printInfo);
    nsPrintInfoToJavaPaper(env, printInfo, newpaper);
    [printInfo release];

JNI_COCOA_EXIT(env);
}

/*
 * Class:     sun_lwawt_macosx_CPrinterJob
 * Method:    createNSPrintInfo
 * Signature: ()J
 */
JNIEXPORT jlong JNICALL Java_sun_lwawt_macosx_CPrinterJob_createNSPrintInfo
  (JNIEnv *env, jclass clazz)
{
    jlong result = -1;
JNI_COCOA_ENTER(env);
    // This is used to create the NSPrintInfo for a PrinterJob. Thread
    //  safety is assured by the java side of this call.

    NSPrintInfo* printInfo = createDefaultNSPrintInfo(env, NULL);

    result = ptr_to_jlong(printInfo);

JNI_COCOA_EXIT(env);
    return result;
}

/*
 * Class:     sun_lwawt_macosx_CPrinterJob
 * Method:    disposeNSPrintInfo
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_sun_lwawt_macosx_CPrinterJob_disposeNSPrintInfo
  (JNIEnv *env, jclass clazz, jlong nsPrintInfo)
{
JNI_COCOA_ENTER(env);
    if (nsPrintInfo != -1)
    {
        NSPrintInfo* printInfo = (NSPrintInfo*)jlong_to_ptr(nsPrintInfo);
        [printInfo release];
    }
JNI_COCOA_EXIT(env);
}


/*
 * Class:     sun_lwawt_macosx_CPrinterJob
 * Method:    printLoop
 * Signature: ()V
 */
JNIEXPORT jboolean JNICALL Java_sun_lwawt_macosx_CPrinterJob_printLoop
  (JNIEnv *env, jobject jthis, jboolean blocks, jint firstPage, jint totalPages)
{
    AWT_ASSERT_NOT_APPKIT_THREAD;

    GET_CPRINTERJOB_CLASS_RETURN(NO);
    DECLARE_METHOD_RETURN(jm_getPageFormat, sjc_CPrinterJob, "getPageFormat", "(I)Ljava/awt/print/PageFormat;", NO);
    DECLARE_METHOD_RETURN(jm_getPageFormatArea, sjc_CPrinterJob, "getPageFormatArea", "(Ljava/awt/print/PageFormat;)Ljava/awt/geom/Rectangle2D;", NO);
    DECLARE_METHOD_RETURN(jm_getPrinterName, sjc_CPrinterJob, "getPrinterName", "()Ljava/lang/String;", NO);
    DECLARE_METHOD_RETURN(jm_getPageable, sjc_CPrinterJob, "getPageable", "()Ljava/awt/print/Pageable;", NO);
    DECLARE_METHOD_RETURN(jm_getPrinterTray, sjc_CPrinterJob, "getPrinterTray", "()Ljava/lang/String;", NO);

    jboolean retVal = JNI_FALSE;

JNI_COCOA_ENTER(env);
    // Get the first page's PageFormat for setting things up (This introduces
    //  and is a facet of the same problem in Radar 2818593/2708932).
    jobject page = (*env)->CallObjectMethod(env, jthis, jm_getPageFormat, firstPage); // AWT_THREADING Safe (!appKit)
    CHECK_EXCEPTION();
    if (page != NULL) {
        jobject pageFormatArea = (*env)->CallObjectMethod(env, jthis, jm_getPageFormatArea, page); // AWT_THREADING Safe (!appKit)
        CHECK_EXCEPTION();

        PrinterView* printerView = [[PrinterView alloc] initWithFrame:JavaToNSRect(env, pageFormatArea) withEnv:env withPrinterJob:jthis];
        [printerView setTotalPages:totalPages];

        GET_NSPRINTINFO_METHOD_RETURN(NO)
        NSPrintInfo* printInfo = (NSPrintInfo*)jlong_to_ptr((*env)->CallLongMethod(env, jthis, sjm_getNSPrintInfo)); // AWT_THREADING Safe (known object)
        CHECK_EXCEPTION();

        NSPrinter *printer = getPrinter(env, jthis);
        if (printer != nil)
        {
            [printInfo setPrinter:printer];
        }

        jobject printerTrayObj = (*env)->CallObjectMethod(env, jthis, jm_getPrinterTray);
        CHECK_EXCEPTION();
        if (printerTrayObj != NULL) {
            NSString *printerTray = JavaStringToNSString(env, printerTrayObj);
            if (printerTray != nil) {
                [[printInfo printSettings] setObject:printerTray forKey:@"InputSlot"];
            }
        }

        // <rdar://problem/4156975> passing jthis CPrinterJob as well, so we can extract the printer name from the current job
        javaPageFormatToNSPrintInfo(env, jthis, page, printInfo);

        // <rdar://problem/4367998> JTable.print attributes are ignored
        jobject pageable = (*env)->CallObjectMethod(env, jthis, jm_getPageable); // AWT_THREADING Safe (!appKit)
        CHECK_EXCEPTION();
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
JNI_COCOA_EXIT(env);
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

    DECLARE_CLASS_RETURN(jc_CPrinterPageDialog, "sun/lwawt/macosx/CPrinterPageDialog", NO);
    DECLARE_FIELD_RETURN(jm_page, jc_CPrinterPageDialog, "fPage", "Ljava/awt/print/PageFormat;", NO);

    jboolean result = JNI_FALSE;
JNI_COCOA_ENTER(env);
    GET_CPRINTERDIALOG_FIELD_RETURN(NO);
    GET_NSPRINTINFO_METHOD_RETURN(NO)
    jobject printerJob = (*env)->GetObjectField(env, jthis, sjm_printerJob);
    if (printerJob == NULL) return NO;
    NSPrintInfo* printInfo = (NSPrintInfo*)jlong_to_ptr((*env)->CallLongMethod(env, printerJob, sjm_getNSPrintInfo)); // AWT_THREADING Safe (known object)
    CHECK_EXCEPTION();
    if (printInfo == NULL) return result;

    jobject page = (*env)->GetObjectField(env, jthis, jm_page);
    if (page == NULL) return NO;

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

JNI_COCOA_EXIT(env);
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
    DECLARE_CLASS_RETURN(jc_CPrinterJobDialog, "sun/lwawt/macosx/CPrinterJobDialog", NO);
    DECLARE_FIELD_RETURN(jm_pageable, jc_CPrinterJobDialog, "fPageable", "Ljava/awt/print/Pageable;", NO);

    jboolean result = JNI_FALSE;
JNI_COCOA_ENTER(env);
    GET_CPRINTERDIALOG_FIELD_RETURN(NO);
    jobject printerJob = (*env)->GetObjectField(env, jthis, sjm_printerJob);
    if (printerJob == NULL) return NO;
    GET_NSPRINTINFO_METHOD_RETURN(NO)
    NSPrintInfo* printInfo = (NSPrintInfo*)jlong_to_ptr((*env)->CallLongMethod(env, printerJob, sjm_getNSPrintInfo)); // AWT_THREADING Safe (known object)

    jobject pageable = (*env)->GetObjectField(env, jthis, jm_pageable);
    if (pageable == NULL) return NO;

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

JNI_COCOA_EXIT(env);
    return result;
}
