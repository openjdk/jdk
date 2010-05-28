/*
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

// This file is available under and governed by the GNU General Public
// License version 2 only, as published by the Free Software Foundation.
// However, the following notice accompanied the original version of this
// file:
//
//
//  Little cms
//  Copyright (C) 1998-2007 Marti Maria
//
// Permission is hereby granted, free of charge, to any person obtaining
// a copy of this software and associated documentation files (the "Software"),
// to deal in the Software without restriction, including without limitation
// the rights to use, copy, modify, merge, publish, distribute, sublicense,
// and/or sell copies of the Software, and to permit persons to whom the Software
// is furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in
// all copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
// EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO
// THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
// NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
// LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
// OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
// WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
//

// Generic I/O, tag dictionary management, profile struct



#include "lcms.h"


// Memory-based stream ---------------------------------------------------

typedef struct {
                LPBYTE Block;           // Points to allocated memory
                size_t Size;            // Size of allocated memory
                size_t Pointer;         // Points to current location
                int FreeBlockOnClose;   // As title

                } FILEMEM;

static
LPVOID MemoryOpen(LPBYTE Block, size_t Size, char Mode)
{
    FILEMEM* fm = (FILEMEM*) _cmsMalloc(sizeof(FILEMEM));
    if (fm == NULL) return NULL;

    ZeroMemory(fm, sizeof(FILEMEM));

    if (Mode == 'r') {

        fm ->Block   = (LPBYTE) _cmsMalloc(Size);
        if (fm ->Block == NULL) {
             _cmsFree(fm);
            return NULL;
        }

        CopyMemory(fm->Block, Block, Size);
        fm ->FreeBlockOnClose = TRUE;
    }
    else {
        fm ->Block = Block;
        fm ->FreeBlockOnClose = FALSE;
    }

    fm ->Size    = Size;
    fm ->Pointer = 0;

    return (LPVOID) fm;
}


static
size_t MemoryRead(LPVOID buffer, size_t size, size_t count, struct _lcms_iccprofile_struct* Icc)
{
     FILEMEM* ResData = (FILEMEM*) Icc ->stream;
     LPBYTE Ptr;
     size_t len = size * count;
     size_t extent = ResData -> Pointer + len;

        if (len == 0) {
                return 0;
        }

        if (len / size != count) {
          cmsSignalError(LCMS_ERRC_ABORTED, "Read from memory error. Integer overflow with count / size.");
          return 0;
      }

      if (extent < len || extent < ResData -> Pointer) {
          cmsSignalError(LCMS_ERRC_ABORTED, "Read from memory error. Integer overflow with len.");
          return 0;
      }

      if (ResData -> Pointer + len > ResData -> Size) {

         len = (ResData -> Size - ResData -> Pointer);
         cmsSignalError(LCMS_ERRC_ABORTED, "Read from memory error. Got %d bytes, block should be of %d bytes", len * size, count * size);
         return 0;
     }

    Ptr  = ResData -> Block;
    Ptr += ResData -> Pointer;
    CopyMemory(buffer, Ptr, len);
    ResData -> Pointer += (int) len;

    return count;
}

// SEEK_CUR is assumed

static
LCMSBOOL MemorySeek(struct _lcms_iccprofile_struct* Icc, size_t offset)
{
    FILEMEM* ResData = (FILEMEM*) Icc ->stream;

    if (offset > ResData ->Size) {
         cmsSignalError(LCMS_ERRC_ABORTED,  "Pointer error; probably corrupted file");
         return TRUE;
    }

    ResData ->Pointer = (DWORD) offset;
    return FALSE;
}

// FTell

static
size_t MemoryTell(struct _lcms_iccprofile_struct* Icc)
{
    FILEMEM* ResData = (FILEMEM*) Icc ->stream;

    return ResData -> Pointer;
}


// Writes data to memory, also keeps used space for further reference. NO CHECK IS PERFORMED

static
LCMSBOOL MemoryWrite(struct _lcms_iccprofile_struct* Icc, size_t size, void *Ptr)
{
        FILEMEM* ResData = (FILEMEM*) Icc ->stream;

       if (size == 0) return TRUE;

       if (ResData != NULL)
           CopyMemory(ResData ->Block + ResData ->Pointer, Ptr, size);

       ResData->Pointer += size;
       Icc->UsedSpace += size;

       return TRUE;
}


static
LCMSBOOL MemoryGrow(struct _lcms_iccprofile_struct* Icc, size_t size)
{
    FILEMEM* ResData = (FILEMEM*) Icc->stream;

    void* newBlock = NULL;

    /* Follow same policies as functions in lcms.h  */
    if (ResData->Size + size < 0) return NULL;
    if (ResData->Size + size > ((size_t)1024*1024*500)) return NULL;

    newBlock = realloc(ResData->Block, ResData->Size + size);

    if (!newBlock) {
        return FALSE;
    }
    ResData->Block = newBlock;
    ResData->Size += size;
    return TRUE;
}


static
LCMSBOOL MemoryClose(struct _lcms_iccprofile_struct* Icc)
{
    FILEMEM* ResData = (FILEMEM*) Icc ->stream;

    if (ResData ->FreeBlockOnClose) {

        if (ResData ->Block)  _cmsFree(ResData ->Block);
    }
     _cmsFree(ResData);
    return 0;
}


// File-based stream -------------------------------------------------------

static
LPVOID FileOpen(const char* filename)
{
    return (void*) fopen(filename, "rb");
}

static
size_t FileRead(void *buffer, size_t size, size_t count, struct _lcms_iccprofile_struct* Icc)
{
    size_t nReaded = fread(buffer, size, count, (FILE*) Icc->stream);
    if (nReaded != count) {
            cmsSignalError(LCMS_ERRC_ABORTED, "Read error. Got %d bytes, block should be of %d bytes", nReaded * size, count * size);
            return 0;
    }

    return nReaded;
}


static
LCMSBOOL FileSeek(struct _lcms_iccprofile_struct* Icc, size_t offset)
{
    if (fseek((FILE*) Icc ->stream, (long) offset, SEEK_SET) != 0) {

       cmsSignalError(LCMS_ERRC_ABORTED, "Seek error; probably corrupted file");
       return TRUE;
    }

    return FALSE;
}


static
size_t FileTell(struct _lcms_iccprofile_struct* Icc)
{
    return ftell((FILE*) Icc ->stream);
}

// Writes data to stream, also keeps used space for further reference


static
LCMSBOOL FileWrite(struct _lcms_iccprofile_struct* Icc, size_t size, LPVOID Ptr)
{
       if (size == 0) return TRUE;

       Icc->UsedSpace += size;

       if (Icc->stream == NULL) {

              return TRUE;
       }

       return (fwrite(Ptr, size, 1, (FILE*) Icc->stream) == 1);
}


static
LCMSBOOL FileGrow(struct _lcms_iccprofile_struct* Icc, size_t size)
{
  return TRUE;
}


static
LCMSBOOL FileClose(struct _lcms_iccprofile_struct* Icc)
{
    return fclose((FILE*) Icc ->stream);
}

// ----------------------------------------------------------------------------------------------------


// Creates an empty structure holding all required parameters

cmsHPROFILE _cmsCreateProfilePlaceholder(void)
{

    LPLCMSICCPROFILE Icc = (LPLCMSICCPROFILE) _cmsMalloc(sizeof(LCMSICCPROFILE));
    if (Icc == NULL) return NULL;

    // Empty values
    ZeroMemory(Icc, sizeof(LCMSICCPROFILE));

    // Make sure illuminant is correct
    Icc ->Illuminant = *cmsD50_XYZ();

    // Set it to empty
    Icc -> TagCount   = 0;

    // Return the handle
    return (cmsHPROFILE) Icc;
}


// Return the number of tags
icInt32Number LCMSEXPORT cmsGetTagCount(cmsHPROFILE hProfile)
{
    LPLCMSICCPROFILE Icc = (LPLCMSICCPROFILE) hProfile;
    return  Icc->TagCount;
}

// Return the tag signature of a given tag number
icTagSignature LCMSEXPORT cmsGetTagSignature(cmsHPROFILE hProfile, icInt32Number n)
{
    LPLCMSICCPROFILE Icc = (LPLCMSICCPROFILE) hProfile;

    if (n < 0 || n > Icc->TagCount) return (icTagSignature) 0;  // Mark as not available

    return Icc ->TagNames[n];
}


// Search for a specific tag in tag dictionary
// Returns position or -1 if tag not found

icInt32Number _cmsSearchTag(LPLCMSICCPROFILE Profile, icTagSignature sig, LCMSBOOL lSignalError)
{
       icInt32Number i;

       if (sig == 0) return -1;     // 0 identifies a special tag holding raw memory.

       for (i=0; i < Profile -> TagCount; i++) {

              if (sig == Profile -> TagNames[i])
                            return i;
       }

       if (lSignalError)
            cmsSignalError(LCMS_ERRC_ABORTED, "Tag '%lx' not found", sig);

       return -1;
}


// Check existance

LCMSBOOL LCMSEXPORT cmsIsTag(cmsHPROFILE hProfile, icTagSignature sig)
{
       LPLCMSICCPROFILE  Icc = (LPLCMSICCPROFILE) (LPSTR) hProfile;
       return _cmsSearchTag(Icc, sig, FALSE) >= 0;
}



// Search for a particular tag, replace if found or add new one else

LPVOID _cmsInitTag(LPLCMSICCPROFILE Icc, icTagSignature sig, size_t size, const void* Init)
{
    LPVOID Ptr;
    icInt32Number i;

    i = _cmsSearchTag(Icc, sig, FALSE);

    if (i >=0) {

        if (Icc -> TagPtrs[i]) _cmsFree(Icc -> TagPtrs[i]);
    }
    else  {

        i = Icc -> TagCount;
        Icc -> TagCount++;

        if (Icc ->TagCount >= MAX_TABLE_TAG) {

            cmsSignalError(LCMS_ERRC_ABORTED, "Too many tags (%d)", MAX_TABLE_TAG);
            Icc ->TagCount = MAX_TABLE_TAG-1;
            return NULL;
        }
    }


    Ptr = _cmsMalloc(size);
    if (Ptr == NULL) return NULL;

    CopyMemory(Ptr, Init, size);

    Icc ->TagNames[i] = sig;
    Icc ->TagSizes[i] = size;
    Icc ->TagPtrs[i]  = Ptr;

    return Ptr;
}





// Creates a profile from file read placeholder

LPLCMSICCPROFILE _cmsCreateProfileFromFilePlaceholder(const char* FileName)
{
    LPLCMSICCPROFILE NewIcc;
    LPVOID ICCfile = FileOpen(FileName);

    if (ICCfile == NULL) {

              cmsSignalError(LCMS_ERRC_ABORTED, "File '%s' not found", FileName);
              return NULL;
    }

    NewIcc = (LPLCMSICCPROFILE) _cmsCreateProfilePlaceholder();
    if (NewIcc == NULL) return NULL;

    strncpy(NewIcc -> PhysicalFile, FileName, MAX_PATH-1);
    NewIcc -> PhysicalFile[MAX_PATH-1] = 0;

    NewIcc ->stream = ICCfile;

    NewIcc ->Read  = FileRead;
    NewIcc ->Seek  = FileSeek;
    NewIcc ->Tell  = FileTell;
    NewIcc ->Close = FileClose;
    NewIcc ->Grow  = FileGrow;
    NewIcc ->Write = NULL;

    NewIcc ->IsWrite = FALSE;




    return NewIcc;
}


// Creates a profile from memory read placeholder

LPLCMSICCPROFILE _cmsCreateProfileFromMemPlaceholder(LPVOID MemPtr, DWORD dwSize)
{

    LPLCMSICCPROFILE NewIcc;
    LPVOID ICCfile = MemoryOpen((LPBYTE) MemPtr, (size_t) dwSize, 'r');


    if (ICCfile == NULL) {

        cmsSignalError(LCMS_ERRC_ABORTED, "Couldn't allocate %ld bytes for profile", dwSize);
        return NULL;
    }


    NewIcc = (LPLCMSICCPROFILE) _cmsCreateProfilePlaceholder();
    if (NewIcc == NULL) return NULL;

    NewIcc -> PhysicalFile[0] = 0;
    NewIcc ->stream = ICCfile;

    NewIcc ->Read  = MemoryRead;
    NewIcc ->Seek  = MemorySeek;
    NewIcc ->Tell  = MemoryTell;
    NewIcc ->Close = MemoryClose;
    NewIcc ->Grow  = MemoryGrow;
    NewIcc ->Write = MemoryWrite;

    NewIcc ->IsWrite = FALSE;


    return NewIcc;
}


// Turn a placeholder into file writter

void _cmsSetSaveToDisk(LPLCMSICCPROFILE Icc, const char* FileName)
{

    if (FileName == NULL) {

          Icc ->stream = NULL;
    }
    else {

          Icc ->stream = fopen(FileName, "wb");
          if (Icc ->stream == NULL)
                cmsSignalError(LCMS_ERRC_ABORTED, "Couldn't write to file '%s'", FileName);
    }

    Icc ->Write = FileWrite;   // Save to disk
    Icc ->Close = FileClose;
}



// Turn a  placeholder into memory writter

void _cmsSetSaveToMemory(LPLCMSICCPROFILE Icc, LPVOID MemPtr, size_t dwSize)
{

    if (MemPtr == NULL) {

        Icc ->stream = NULL;
    }
    else {

        Icc ->stream = (FILEMEM*) MemoryOpen((LPBYTE) MemPtr, dwSize, 'w');
        if (Icc ->stream == NULL)
                cmsSignalError(LCMS_ERRC_ABORTED, "Couldn't write to memory");
    }

    Icc ->Write = MemoryWrite;
    Icc ->Close = MemoryClose;
}


// ----------------------------------------------------------------------- Set/Get several struct members




LCMSBOOL LCMSEXPORT cmsTakeMediaWhitePoint(LPcmsCIEXYZ Dest, cmsHPROFILE hProfile)
{
     LPLCMSICCPROFILE    Icc = (LPLCMSICCPROFILE) hProfile;
     *Dest = Icc -> MediaWhitePoint;
     return TRUE;
}


LCMSBOOL LCMSEXPORT cmsTakeMediaBlackPoint(LPcmsCIEXYZ Dest, cmsHPROFILE hProfile)
{
      LPLCMSICCPROFILE    Icc = (LPLCMSICCPROFILE) hProfile;
      *Dest = Icc -> MediaBlackPoint;
      return TRUE;
}

LCMSBOOL  LCMSEXPORT cmsTakeIluminant(LPcmsCIEXYZ Dest, cmsHPROFILE hProfile)
{
       LPLCMSICCPROFILE  Icc = (LPLCMSICCPROFILE) hProfile;
       *Dest = Icc -> Illuminant;
       return TRUE;
}

int LCMSEXPORT cmsTakeRenderingIntent(cmsHPROFILE hProfile)
{
       LPLCMSICCPROFILE  Icc = (LPLCMSICCPROFILE) hProfile;
       return (int) Icc -> RenderingIntent;
}

void LCMSEXPORT cmsSetRenderingIntent(cmsHPROFILE hProfile, int RenderingIntent)
{
    LPLCMSICCPROFILE  Icc = (LPLCMSICCPROFILE) hProfile;
    Icc -> RenderingIntent = (icRenderingIntent) RenderingIntent;
}


DWORD LCMSEXPORT cmsTakeHeaderFlags(cmsHPROFILE hProfile)
{
       LPLCMSICCPROFILE  Icc = (LPLCMSICCPROFILE) hProfile;
       return (DWORD) Icc -> flags;
}

void LCMSEXPORT cmsSetHeaderFlags(cmsHPROFILE hProfile, DWORD Flags)
{
    LPLCMSICCPROFILE  Icc = (LPLCMSICCPROFILE) hProfile;
    Icc -> flags = (icUInt32Number) Flags;
}

DWORD LCMSEXPORT cmsTakeHeaderAttributes(cmsHPROFILE hProfile)
{
       LPLCMSICCPROFILE  Icc = (LPLCMSICCPROFILE) hProfile;
       return (DWORD) Icc -> attributes;
}

void LCMSEXPORT cmsSetHeaderAttributes(cmsHPROFILE hProfile, DWORD Flags)
{
    LPLCMSICCPROFILE  Icc = (LPLCMSICCPROFILE) hProfile;
    Icc -> attributes = (icUInt32Number) Flags;
}


const BYTE* LCMSEXPORT cmsTakeProfileID(cmsHPROFILE hProfile)
{
    LPLCMSICCPROFILE  Icc = (LPLCMSICCPROFILE) hProfile;
    return Icc ->ProfileID;
}

void LCMSEXPORT cmsSetProfileID(cmsHPROFILE hProfile, LPBYTE ProfileID)
{
    LPLCMSICCPROFILE  Icc = (LPLCMSICCPROFILE) hProfile;
    CopyMemory(Icc -> ProfileID, ProfileID, 16);
}


LCMSBOOL LCMSEXPORT cmsTakeCreationDateTime(struct tm *Dest, cmsHPROFILE hProfile)
{
    LPLCMSICCPROFILE  Icc = (LPLCMSICCPROFILE) (LPSTR) hProfile;
    CopyMemory(Dest, &Icc ->Created, sizeof(struct tm));
    return TRUE;
}


icColorSpaceSignature LCMSEXPORT cmsGetPCS(cmsHPROFILE hProfile)
{
       LPLCMSICCPROFILE  Icc = (LPLCMSICCPROFILE) hProfile;
       return Icc -> PCS;
}


void LCMSEXPORT cmsSetPCS(cmsHPROFILE hProfile, icColorSpaceSignature pcs)
{
       LPLCMSICCPROFILE  Icc = (LPLCMSICCPROFILE) hProfile;
       Icc -> PCS = pcs;
}

icColorSpaceSignature LCMSEXPORT cmsGetColorSpace(cmsHPROFILE hProfile)
{
       LPLCMSICCPROFILE  Icc = (LPLCMSICCPROFILE) hProfile;
       return Icc -> ColorSpace;
}

void LCMSEXPORT cmsSetColorSpace(cmsHPROFILE hProfile, icColorSpaceSignature sig)
{
       LPLCMSICCPROFILE  Icc = (LPLCMSICCPROFILE) hProfile;
       Icc -> ColorSpace = sig;
}

icProfileClassSignature LCMSEXPORT cmsGetDeviceClass(cmsHPROFILE hProfile)
{
       LPLCMSICCPROFILE  Icc = (LPLCMSICCPROFILE) hProfile;
       return Icc -> DeviceClass;
}

DWORD LCMSEXPORT cmsGetProfileICCversion(cmsHPROFILE hProfile)
{
       LPLCMSICCPROFILE  Icc = (LPLCMSICCPROFILE) hProfile;
       return (DWORD) Icc -> Version;
}

void LCMSEXPORT cmsSetProfileICCversion(cmsHPROFILE hProfile, DWORD Version)
{
   LPLCMSICCPROFILE  Icc = (LPLCMSICCPROFILE) hProfile;
   Icc -> Version = Version;
}


void LCMSEXPORT cmsSetDeviceClass(cmsHPROFILE hProfile, icProfileClassSignature sig)
{
       LPLCMSICCPROFILE  Icc = (LPLCMSICCPROFILE) hProfile;
       Icc -> DeviceClass = sig;
}


// --------------------------------------------------------------------------------------------------------------


static
int SizeOfGammaTab(LPGAMMATABLE In)
{
       return sizeof(GAMMATABLE) + (In -> nEntries - 1)*sizeof(WORD);
}


// Creates a phantom tag holding a memory block

static
LPVOID DupBlock(LPLCMSICCPROFILE Icc, LPVOID Block, size_t size)
{
    if (Block != NULL && size > 0)
        return _cmsInitTag(Icc, (icTagSignature) 0, size, Block);
    else
        return NULL;

}

// This is tricky, since LUT structs does have pointers

LCMSBOOL LCMSEXPORT _cmsAddLUTTag(cmsHPROFILE hProfile, icTagSignature sig, const void* lut)
{
       LPLCMSICCPROFILE Icc = (LPLCMSICCPROFILE) (LPSTR) hProfile;
       LPLUT Orig, Stored;
       unsigned int i;

       // The struct itself

       Orig   = (LPLUT) lut;
       Stored = (LPLUT) _cmsInitTag(Icc, (icTagSignature) sig, sizeof(LUT), lut);

       // dup' the memory blocks
       for (i=0; i < Orig ->InputChan; i++)
            Stored -> L1[i] = (LPWORD) DupBlock(Icc, (LPWORD) Orig ->L1[i],
                                            sizeof(WORD) * Orig ->In16params.nSamples);

       for (i=0; i < Orig ->OutputChan; i++)
            Stored -> L2[i] = (LPWORD) DupBlock(Icc, (LPWORD) Orig ->L2[i],
                                            sizeof(WORD) * Orig ->Out16params.nSamples);

       Stored -> T     = (LPWORD) DupBlock(Icc, (LPWORD) Orig ->T, Orig -> Tsize);

       // Zero any additional pointer
       Stored ->CLut16params.p8 = NULL;
       return TRUE;
}


LCMSBOOL LCMSEXPORT _cmsAddXYZTag(cmsHPROFILE hProfile, icTagSignature sig, const cmsCIEXYZ* XYZ)
{
       LPLCMSICCPROFILE Icc = (LPLCMSICCPROFILE) (LPSTR) hProfile;

       _cmsInitTag(Icc, sig, sizeof(cmsCIEXYZ), XYZ);
       return TRUE;
}


LCMSBOOL LCMSEXPORT _cmsAddTextTag(cmsHPROFILE hProfile, icTagSignature sig, const char* Text)
{
       LPLCMSICCPROFILE Icc = (LPLCMSICCPROFILE) (LPSTR) hProfile;

       _cmsInitTag(Icc, sig, strlen(Text)+1, (LPVOID) Text);
       return TRUE;
}

LCMSBOOL LCMSEXPORT _cmsAddGammaTag(cmsHPROFILE hProfile, icTagSignature sig, LPGAMMATABLE TransferFunction)
{
    LPLCMSICCPROFILE Icc = (LPLCMSICCPROFILE) (LPSTR) hProfile;

    _cmsInitTag(Icc, sig, SizeOfGammaTab(TransferFunction), TransferFunction);
    return TRUE;
}


LCMSBOOL LCMSEXPORT _cmsAddChromaticityTag(cmsHPROFILE hProfile, icTagSignature sig, LPcmsCIExyYTRIPLE Chrm)
{
    LPLCMSICCPROFILE Icc = (LPLCMSICCPROFILE) (LPSTR) hProfile;

    _cmsInitTag(Icc, sig, sizeof(cmsCIExyYTRIPLE), Chrm);
    return TRUE;
}


LCMSBOOL LCMSEXPORT _cmsAddSequenceDescriptionTag(cmsHPROFILE hProfile, icTagSignature sig, LPcmsSEQ pseq)
{
    LPLCMSICCPROFILE Icc = (LPLCMSICCPROFILE) (LPSTR) hProfile;

    _cmsInitTag(Icc, sig, sizeof(int) + pseq -> n * sizeof(cmsPSEQDESC), pseq);
    return TRUE;

}


LCMSBOOL LCMSEXPORT _cmsAddNamedColorTag(cmsHPROFILE hProfile, icTagSignature sig, LPcmsNAMEDCOLORLIST nc)
{
    LPLCMSICCPROFILE Icc = (LPLCMSICCPROFILE) (LPSTR) hProfile;

    _cmsInitTag(Icc, sig, sizeof(cmsNAMEDCOLORLIST) + (nc ->nColors - 1) * sizeof(cmsNAMEDCOLOR), nc);
    return TRUE;
}


LCMSBOOL LCMSEXPORT _cmsAddDateTimeTag(cmsHPROFILE hProfile, icTagSignature sig, struct tm *DateTime)
{
    LPLCMSICCPROFILE Icc = (LPLCMSICCPROFILE) (LPSTR) hProfile;

    _cmsInitTag(Icc, sig, sizeof(struct tm), DateTime);
    return TRUE;
}


LCMSBOOL LCMSEXPORT _cmsAddColorantTableTag(cmsHPROFILE hProfile, icTagSignature sig, LPcmsNAMEDCOLORLIST nc)
{
    LPLCMSICCPROFILE Icc = (LPLCMSICCPROFILE) (LPSTR) hProfile;

    _cmsInitTag(Icc, sig, sizeof(cmsNAMEDCOLORLIST) + (nc ->nColors - 1) * sizeof(cmsNAMEDCOLOR), nc);
    return TRUE;
}


LCMSBOOL LCMSEXPORT _cmsAddChromaticAdaptationTag(cmsHPROFILE hProfile, icTagSignature sig, const cmsCIEXYZ* mat)
{
    LPLCMSICCPROFILE Icc = (LPLCMSICCPROFILE) (LPSTR) hProfile;

    _cmsInitTag(Icc, sig, 3*sizeof(cmsCIEXYZ), mat);
    return TRUE;

}


