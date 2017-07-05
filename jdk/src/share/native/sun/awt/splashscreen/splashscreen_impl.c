/*
 * Copyright (c) 2005, 2006, Oracle and/or its affiliates. All rights reserved.
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

#include "splashscreen_impl.h"
#include "splashscreen_gfx_impl.h"

int splashIsVisible = 0;

Splash *
SplashGetInstance()
{
    static Splash splash;
    static int preInitialized = 0;
    if (!preInitialized) {
        memset(&splash, 0, sizeof(Splash));
        splash.currentFrame = -1;
        preInitialized = 1;
    }
    return &splash;
}

SPLASHEXPORT void
SplashSetFileJarName(const char* fileName, const char* jarName) {
    Splash *splash = SplashGetInstance();

    free(splash->fileName);
    splash->fileName = SplashConvertStringAlloc(fileName, &splash->fileNameLen);

    free(splash->jarName);
    splash->jarName = SplashConvertStringAlloc(jarName, &splash->jarNameLen);
}

SPLASHEXPORT void
SplashInit()
{
    Splash *splash = SplashGetInstance();

    memset(splash, 0, sizeof(Splash));
    splash->currentFrame = -1;
    initFormat(&splash->imageFormat, QUAD_RED_MASK, QUAD_GREEN_MASK,
        QUAD_BLUE_MASK, QUAD_ALPHA_MASK);
    SplashInitPlatform(splash);
}

SPLASHEXPORT void
SplashClose()
{
    Splash *splash = SplashGetInstance();

    if (splash->isVisible > 0) {
        SplashLock(splash);
        splash->isVisible = -1;
        SplashClosePlatform(splash);
        SplashUnlock(splash);
    }
}

void
SplashCleanup(Splash * splash)
{
    int i;

    splash->currentFrame = -1;
    SplashCleanupPlatform(splash);
    if (splash->frames) {
        for (i = 0; i < splash->frameCount; i++) {
            if (splash->frames[i].bitmapBits) {
                free(splash->frames[i].bitmapBits);
                splash->frames[i].bitmapBits = NULL;
            }
        }
        free(splash->frames);
        splash->frames = NULL;
    }
    if (splash->overlayData) {
        free(splash->overlayData);
        splash->overlayData = NULL;
    }
    SplashSetFileJarName(NULL, NULL);
}

void
SplashDone(Splash * splash)
{
    SplashCleanup(splash);
    SplashDonePlatform(splash);
}

int
SplashIsStillLooping(Splash * splash)
{
    if (splash->currentFrame < 0) {
        return 0;
    }
    return splash->loopCount != 1 ||
        splash->currentFrame + 1 < splash->frameCount;
}

void
SplashUpdateScreenData(Splash * splash)
{
    ImageRect srcRect, dstRect;
    if (splash->currentFrame < 0) {
        return;
    }

    initRect(&srcRect, 0, 0, splash->width, splash->height, 1,
        splash->width * sizeof(rgbquad_t),
        splash->frames[splash->currentFrame].bitmapBits, &splash->imageFormat);
    if (splash->screenData) {
        free(splash->screenData);
    }
    splash->screenStride = splash->width * splash->screenFormat.depthBytes;
    if (splash->byteAlignment > 1) {
        splash->screenStride =
            (splash->screenStride + splash->byteAlignment - 1) &
            ~(splash->byteAlignment - 1);
    }
    splash->screenData = malloc(splash->height * splash->screenStride);
    initRect(&dstRect, 0, 0, splash->width, splash->height, 1,
        splash->screenStride, splash->screenData, &splash->screenFormat);
    if (splash->overlayData) {
        convertRect2(&srcRect, &dstRect, CVT_BLEND, &splash->overlayRect);
    }
    else {
        convertRect(&srcRect, &dstRect, CVT_COPY);
    }
}

void
SplashNextFrame(Splash * splash)
{
    if (splash->currentFrame < 0) {
        return;
    }
    do {
        if (!SplashIsStillLooping(splash)) {
            return;
        }
        splash->time += splash->frames[splash->currentFrame].delay;
        if (++splash->currentFrame >= splash->frameCount) {
            splash->currentFrame = 0;
            if (splash->loopCount > 0) {
                splash->loopCount--;
            }
        }
    } while (splash->time + splash->frames[splash->currentFrame].delay -
        SplashTime() <= 0);
}

int
BitmapToYXBandedRectangles(ImageRect * pSrcRect, RECT_T * out)
{
    RECT_T *pPrevLine = NULL, *pFirst = out, *pThis = pFirst;
    int i, j, i0;
    int length;

    for (j = 0; j < pSrcRect->numLines; j++) {

        /* generate data for a scanline */

        byte_t *pSrc = (byte_t *) pSrcRect->pBits + j * pSrcRect->stride;
        RECT_T *pLine = pThis;

        i = 0;

        do {
            while (i < pSrcRect->numSamples &&
                   getRGBA(pSrc, pSrcRect->format) < ALPHA_THRESHOLD) {
                pSrc += pSrcRect->depthBytes;
                ++i;
            }
            if (i >= pSrcRect->numSamples) {
                break;
            }
            i0 = i;
            while (i < pSrcRect->numSamples &&
                   getRGBA(pSrc, pSrcRect->format) >= ALPHA_THRESHOLD) {
                pSrc += pSrcRect->depthBytes;
                ++i;
            }
            RECT_SET(*pThis, i0, j, i - i0, 1);
            ++pThis;
        } while (i < pSrcRect->numSamples);

        /*  check if the previous scanline is exactly the same, merge if so
           (this is the only optimization we can use for YXBanded rectangles, and win32 supports
           YXBanded only */

        length = pThis - pLine;
        if (pPrevLine && pLine - pPrevLine == length) {
            for (i = 0; i < length && RECT_EQ_X(pPrevLine[i], pLine[i]); ++i) {
            }
            if (i == pLine - pPrevLine) {
                // do merge
                for (i = 0; i < length; i++) {
                    RECT_INC_HEIGHT(pPrevLine[i]);
                }
                pThis = pLine;
                continue;
            }
        }
        /* or else use the generated scanline */

        pPrevLine = pLine;
    }
    return pThis - pFirst;
}

typedef struct FILEFORMAT
{
    int sign;
    int (*decodeStream) (Splash * splash, SplashStream * stream);
} FILEFORMAT;

static const FILEFORMAT formats[] = {
    {0x47, SplashDecodeGifStream},
    {0x89, SplashDecodePngStream},
    {0xFF, SplashDecodeJpegStream}
};

static int
SplashLoadStream(SplashStream * stream)
{
    int success = 0;
    int c;
    size_t i;

    Splash *splash = SplashGetInstance();
    if (splash->isVisible < 0) {
        return 0;
    }

    SplashLock(splash);

    /* the formats we support can be easily distinguished by the first byte */
    c = stream->peek(stream);
    if (c != -1) {
        for (i = 0; i < sizeof(formats) / sizeof(FILEFORMAT); i++) {
            if (c == formats[i].sign) {
                success = formats[i].decodeStream(splash, stream);
                break;
            }
        }
    }
    stream->close(stream);

    if (!success) {             // failed to decode
        if (splash->isVisible == 0) {
            SplashCleanup(splash);
        }
        SplashUnlock(splash);   // SplashClose locks
        if (splash->isVisible == 0) {
            SplashClose();
        }
    }
    else {
        splash->currentFrame = 0;
        if (splash->isVisible == 0) {
            SplashStart(splash);
        } else {
            SplashReconfigure(splash);
            splash->time = SplashTime();
        }
        SplashUnlock(splash);
    }
    return success;
}

SPLASHEXPORT int
SplashLoadFile(const char *filename)
{
    SplashStream stream;
    return SplashStreamInitFile(&stream, filename) &&
                SplashLoadStream(&stream);
}

SPLASHEXPORT int
SplashLoadMemory(void *data, int size)
{
    SplashStream stream;
    return SplashStreamInitMemory(&stream, data, size) &&
                SplashLoadStream(&stream);
}

/* SplashStart MUST be called from under the lock */

void
SplashStart(Splash * splash)
{
    if (splash->isVisible == 0) {
        SplashCreateThread(splash);
        splash->isVisible = 1;
    }
}

/* SplashStream functions */

static int readFile(void* pStream, void* pData, int nBytes) {
    FILE* f = ((SplashStream*)pStream)->arg.stdio.f;
    return fread(pData, 1, nBytes, f);
}
static int peekFile(void* pStream) {
    FILE* f = ((SplashStream*)pStream)->arg.stdio.f;
    int c = fgetc(f);
    if (c != EOF) {
        ungetc(c, f);
        return c;
    } else {
        return -1;
    }
}

static void closeFile(void* pStream) {
    FILE* f = ((SplashStream*)pStream)->arg.stdio.f;
    fclose(f);
}

static int readMem(void* pStream, void* pData, int nBytes) {
    unsigned char* pSrc = (unsigned char*)(((SplashStream*)pStream)->arg.mem.pData);
    unsigned char* pSrcEnd = (unsigned char*)(((SplashStream*)pStream)->arg.mem.pDataEnd);
    if (nBytes > pSrcEnd - pSrc) {
        nBytes = pSrcEnd - pSrc;
    }
    if (nBytes>0) {
        memcpy(pData, pSrc, nBytes);
        pSrc += nBytes;
        ((SplashStream*)pStream)->arg.mem.pData = (void*)pSrc;
    }
    return nBytes;
}

static int peekMem(void* pStream) {
    unsigned char* pSrc = (unsigned char*)(((SplashStream*)pStream)->arg.mem.pData);
    unsigned char* pSrcEnd = (unsigned char*)(((SplashStream*)pStream)->arg.mem.pDataEnd);
    if (pSrc >= pSrcEnd) {
        return -1;
    } else {
        return (int)*pSrc;
    }
}

static void closeMem(void* pStream) {
}

int SplashStreamInitFile(SplashStream * pStream, const char* filename) {
    pStream->arg.stdio.f = fopen(filename, "rb");
    pStream->read = readFile;
    pStream->peek = peekFile;
    pStream->close = closeFile;
    return pStream->arg.stdio.f != 0;
}

int SplashStreamInitMemory(SplashStream * pStream, void* pData, int size) {
    pStream->arg.mem.pData = (unsigned char*)pData;
    pStream->arg.mem.pDataEnd = (unsigned char*)pData + size;
    pStream->read = readMem;
    pStream->peek = peekMem;
    pStream->close = closeMem;
    return 1;
}
