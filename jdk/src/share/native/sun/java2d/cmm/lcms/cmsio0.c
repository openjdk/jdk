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
//---------------------------------------------------------------------------------
//
//  Little Color Management System
//  Copyright (c) 1998-2010 Marti Maria Saguer
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
//---------------------------------------------------------------------------------
//

#include "lcms2_internal.h"

// Generic I/O, tag dictionary management, profile struct

// IOhandlers are abstractions used by littleCMS to read from whatever file, stream,
// memory block or any storage. Each IOhandler provides implementations for read,
// write, seek and tell functions. LittleCMS code deals with IO across those objects.
// In this way, is easier to add support for new storage media.

// NULL stream, for taking care of used space -------------------------------------

// NULL IOhandler basically does nothing but keep track on how many bytes have been
// written. This is handy when creating profiles, where the file size is needed in the
// header. Then, whole profile is serialized across NULL IOhandler and a second pass
// writes the bytes to the pertinent IOhandler.

typedef struct {
    cmsUInt32Number Pointer;         // Points to current location
} FILENULL;

static
cmsUInt32Number NULLRead(cmsIOHANDLER* iohandler, void *Buffer, cmsUInt32Number size, cmsUInt32Number count)
{
    FILENULL* ResData = (FILENULL*) iohandler ->stream;

    cmsUInt32Number len = size * count;
    ResData -> Pointer += len;
    return count;

    cmsUNUSED_PARAMETER(Buffer);
}

static
cmsBool  NULLSeek(cmsIOHANDLER* iohandler, cmsUInt32Number offset)
{
    FILENULL* ResData = (FILENULL*) iohandler ->stream;

    ResData ->Pointer = offset;
    return TRUE;
}

static
cmsUInt32Number NULLTell(cmsIOHANDLER* iohandler)
{
    FILENULL* ResData = (FILENULL*) iohandler ->stream;
    return ResData -> Pointer;
}

static
cmsBool  NULLWrite(cmsIOHANDLER* iohandler, cmsUInt32Number size, const void *Ptr)
{
    FILENULL* ResData = (FILENULL*) iohandler ->stream;

    ResData ->Pointer += size;
    if (ResData ->Pointer > iohandler->UsedSpace)
        iohandler->UsedSpace = ResData ->Pointer;

    return TRUE;

    cmsUNUSED_PARAMETER(Ptr);
}

static
cmsBool  NULLClose(cmsIOHANDLER* iohandler)
{
    FILENULL* ResData = (FILENULL*) iohandler ->stream;

    _cmsFree(iohandler ->ContextID, ResData);
    _cmsFree(iohandler ->ContextID, iohandler);
    return TRUE;
}

// The NULL IOhandler creator
cmsIOHANDLER*  CMSEXPORT cmsOpenIOhandlerFromNULL(cmsContext ContextID)
{
    struct _cms_io_handler* iohandler = NULL;
    FILENULL* fm = NULL;

    iohandler = (struct _cms_io_handler*) _cmsMallocZero(ContextID, sizeof(struct _cms_io_handler));
    if (iohandler == NULL) return NULL;

    fm = (FILENULL*) _cmsMallocZero(ContextID, sizeof(FILENULL));
    if (fm == NULL) goto Error;

    fm ->Pointer = 0;

    iohandler ->ContextID = ContextID;
    iohandler ->stream  = (void*) fm;
    iohandler ->UsedSpace = 0;
    iohandler ->PhysicalFile[0] = 0;

    iohandler ->Read    = NULLRead;
    iohandler ->Seek    = NULLSeek;
    iohandler ->Close   = NULLClose;
    iohandler ->Tell    = NULLTell;
    iohandler ->Write   = NULLWrite;

    return iohandler;

Error:
    if (fm) _cmsFree(ContextID, fm);
    if (iohandler) _cmsFree(ContextID, iohandler);
    return NULL;

}


// Memory-based stream --------------------------------------------------------------

// Those functions implements an iohandler which takes a block of memory as storage medium.

typedef struct {
    cmsUInt8Number* Block;    // Points to allocated memory
    cmsUInt32Number Size;     // Size of allocated memory
    cmsUInt32Number Pointer;  // Points to current location
    int FreeBlockOnClose;     // As title

} FILEMEM;

static
cmsUInt32Number MemoryRead(struct _cms_io_handler* iohandler, void *Buffer, cmsUInt32Number size, cmsUInt32Number count)
{
    FILEMEM* ResData = (FILEMEM*) iohandler ->stream;
    cmsUInt8Number* Ptr;
    cmsUInt32Number len = size * count;

    if (ResData -> Pointer + len > ResData -> Size){

        len = (ResData -> Size - ResData -> Pointer);
        cmsSignalError(iohandler ->ContextID, cmsERROR_READ, "Read from memory error. Got %d bytes, block should be of %d bytes", len, count * size);
        return 0;
    }

    Ptr  = ResData -> Block;
    Ptr += ResData -> Pointer;
    memmove(Buffer, Ptr, len);
    ResData -> Pointer += len;

    return count;
}

// SEEK_CUR is assumed
static
cmsBool  MemorySeek(struct _cms_io_handler* iohandler, cmsUInt32Number offset)
{
    FILEMEM* ResData = (FILEMEM*) iohandler ->stream;

    if (offset > ResData ->Size) {
        cmsSignalError(iohandler ->ContextID, cmsERROR_SEEK,  "Too few data; probably corrupted profile");
        return FALSE;
    }

    ResData ->Pointer = offset;
    return TRUE;
}

// Tell for memory
static
cmsUInt32Number MemoryTell(struct _cms_io_handler* iohandler)
{
    FILEMEM* ResData = (FILEMEM*) iohandler ->stream;

    if (ResData == NULL) return 0;
    return ResData -> Pointer;
}


// Writes data to memory, also keeps used space for further reference.
static
cmsBool  MemoryWrite(struct _cms_io_handler* iohandler, cmsUInt32Number size, const void *Ptr)
{
    FILEMEM* ResData = (FILEMEM*) iohandler ->stream;

    if (ResData == NULL) return FALSE; // Housekeeping

    if (size == 0) return TRUE;     // Write zero bytes is ok, but does nothing

    memmove(ResData ->Block + ResData ->Pointer, Ptr, size);
    ResData ->Pointer += size;

    if (ResData ->Pointer > iohandler->UsedSpace)
        iohandler->UsedSpace = ResData ->Pointer;


    iohandler->UsedSpace += size;

    return TRUE;
}


static
cmsBool  MemoryClose(struct _cms_io_handler* iohandler)
{
    FILEMEM* ResData = (FILEMEM*) iohandler ->stream;

    if (ResData ->FreeBlockOnClose) {

        if (ResData ->Block) _cmsFree(iohandler ->ContextID, ResData ->Block);
    }

    _cmsFree(iohandler ->ContextID, ResData);
    _cmsFree(iohandler ->ContextID, iohandler);

    return TRUE;
}

// Create a iohandler for memory block. AccessMode=='r' assumes the iohandler is going to read, and makes
// a copy of the memory block for letting user to free the memory after invoking open profile. In write
// mode ("w"), Buffere points to the begin of memory block to be written.
cmsIOHANDLER* CMSEXPORT cmsOpenIOhandlerFromMem(cmsContext ContextID, void *Buffer, cmsUInt32Number size, const char* AccessMode)
{
    cmsIOHANDLER* iohandler = NULL;
    FILEMEM* fm = NULL;

    _cmsAssert(AccessMode != NULL);

    iohandler = (cmsIOHANDLER*) _cmsMallocZero(ContextID, sizeof(cmsIOHANDLER));
    if (iohandler == NULL) return NULL;

    switch (*AccessMode) {

    case 'r':
        fm = (FILEMEM*) _cmsMallocZero(ContextID, sizeof(FILEMEM));
        if (fm == NULL) goto Error;

        if (Buffer == NULL) {
            cmsSignalError(ContextID, cmsERROR_READ, "Couldn't read profile from NULL pointer");
            goto Error;
        }

        fm ->Block = (cmsUInt8Number*) _cmsMalloc(ContextID, size);
        if (fm ->Block == NULL) {

            _cmsFree(ContextID, fm);
            _cmsFree(ContextID, iohandler);
            cmsSignalError(ContextID, cmsERROR_READ, "Couldn't allocate %ld bytes for profile", size);
            return NULL;
        }


        memmove(fm->Block, Buffer, size);
        fm ->FreeBlockOnClose = TRUE;
        fm ->Size    = size;
        fm ->Pointer = 0;
        break;

    case 'w':
        fm = (FILEMEM*) _cmsMallocZero(ContextID, sizeof(FILEMEM));
        if (fm == NULL) goto Error;

        fm ->Block = (cmsUInt8Number*) Buffer;
        fm ->FreeBlockOnClose = FALSE;
        fm ->Size    = size;
        fm ->Pointer = 0;
        break;

    default:
        cmsSignalError(ContextID, cmsERROR_UNKNOWN_EXTENSION, "Unknow access mode '%c'", *AccessMode);
        return NULL;
    }

    iohandler ->ContextID = ContextID;
    iohandler ->stream  = (void*) fm;
    iohandler ->UsedSpace = 0;
    iohandler ->PhysicalFile[0] = 0;

    iohandler ->Read    = MemoryRead;
    iohandler ->Seek    = MemorySeek;
    iohandler ->Close   = MemoryClose;
    iohandler ->Tell    = MemoryTell;
    iohandler ->Write   = MemoryWrite;

    return iohandler;

Error:
    if (fm) _cmsFree(ContextID, fm);
    if (iohandler) _cmsFree(ContextID, iohandler);
    return NULL;
}

// File-based stream -------------------------------------------------------

// Read count elements of size bytes each. Return number of elements read
static
cmsUInt32Number FileRead(cmsIOHANDLER* iohandler, void *Buffer, cmsUInt32Number size, cmsUInt32Number count)
{
    cmsUInt32Number nReaded = (cmsUInt32Number) fread(Buffer, size, count, (FILE*) iohandler->stream);

    if (nReaded != count) {
            cmsSignalError(iohandler ->ContextID, cmsERROR_FILE, "Read error. Got %d bytes, block should be of %d bytes", nReaded * size, count * size);
            return 0;
    }

    return nReaded;
}

// Postion file pointer in the file
static
cmsBool  FileSeek(cmsIOHANDLER* iohandler, cmsUInt32Number offset)
{
    if (fseek((FILE*) iohandler ->stream, (long) offset, SEEK_SET) != 0) {

       cmsSignalError(iohandler ->ContextID, cmsERROR_FILE, "Seek error; probably corrupted file");
       return FALSE;
    }

    return TRUE;
}

// Returns file pointer position
static
cmsUInt32Number FileTell(cmsIOHANDLER* iohandler)
{
    return ftell((FILE*)iohandler ->stream);
}

// Writes data to stream, also keeps used space for further reference. Returns TRUE on success, FALSE on error
static
cmsBool  FileWrite(cmsIOHANDLER* iohandler, cmsUInt32Number size, const void* Buffer)
{
       if (size == 0) return TRUE;  // We allow to write 0 bytes, but nothing is written

       iohandler->UsedSpace += size;
       return (fwrite(Buffer, size, 1, (FILE*) iohandler->stream) == 1);
}

// Closes the file
static
cmsBool  FileClose(cmsIOHANDLER* iohandler)
{
    if (fclose((FILE*) iohandler ->stream) != 0) return FALSE;
    _cmsFree(iohandler ->ContextID, iohandler);
    return TRUE;
}

// Create a iohandler for disk based files. if FileName is NULL, then 'stream' member is also set
// to NULL and no real writting is performed. This only happens in writting access mode
cmsIOHANDLER* CMSEXPORT cmsOpenIOhandlerFromFile(cmsContext ContextID, const char* FileName, const char* AccessMode)
{
    cmsIOHANDLER* iohandler = NULL;
    FILE* fm = NULL;

    iohandler = (cmsIOHANDLER*) _cmsMallocZero(ContextID, sizeof(cmsIOHANDLER));
    if (iohandler == NULL) return NULL;

    switch (*AccessMode) {

    case 'r':
        fm = fopen(FileName, "rb");
        if (fm == NULL) {
            _cmsFree(ContextID, iohandler);
             cmsSignalError(ContextID, cmsERROR_FILE, "File '%s' not found", FileName);
            return NULL;
        }
        break;

    case 'w':
        fm = fopen(FileName, "wb");
        if (fm == NULL) {
            _cmsFree(ContextID, iohandler);
             cmsSignalError(ContextID, cmsERROR_FILE, "Couldn't create '%s'", FileName);
            return NULL;
        }
        break;

    default:
        _cmsFree(ContextID, iohandler);
         cmsSignalError(ContextID, cmsERROR_FILE, "Unknow access mode '%c'", *AccessMode);
        return NULL;
    }

    iohandler ->ContextID = ContextID;
    iohandler ->stream = (void*) fm;
    iohandler ->UsedSpace = 0;

    // Keep track of the original file
    if (FileName != NULL)  {

        strncpy(iohandler -> PhysicalFile, FileName, sizeof(iohandler -> PhysicalFile)-1);
        iohandler -> PhysicalFile[sizeof(iohandler -> PhysicalFile)-1] = 0;
    }

    iohandler ->Read    = FileRead;
    iohandler ->Seek    = FileSeek;
    iohandler ->Close   = FileClose;
    iohandler ->Tell    = FileTell;
    iohandler ->Write   = FileWrite;

    return iohandler;
}

// Create a iohandler for stream based files
cmsIOHANDLER* CMSEXPORT cmsOpenIOhandlerFromStream(cmsContext ContextID, FILE* Stream)
{
    cmsIOHANDLER* iohandler = NULL;

    iohandler = (cmsIOHANDLER*) _cmsMallocZero(ContextID, sizeof(cmsIOHANDLER));
    if (iohandler == NULL) return NULL;

    iohandler -> ContextID = ContextID;
    iohandler -> stream = (void*) Stream;
    iohandler -> UsedSpace = 0;
    iohandler -> PhysicalFile[0] = 0;

    iohandler ->Read    = FileRead;
    iohandler ->Seek    = FileSeek;
    iohandler ->Close   = FileClose;
    iohandler ->Tell    = FileTell;
    iohandler ->Write   = FileWrite;

    return iohandler;
}



// Close an open IO handler
cmsBool CMSEXPORT cmsCloseIOhandler(cmsIOHANDLER* io)
{
    return io -> Close(io);
}

// -------------------------------------------------------------------------------------------------------

// Creates an empty structure holding all required parameters
cmsHPROFILE CMSEXPORT cmsCreateProfilePlaceholder(cmsContext ContextID)
{
    time_t now = time(NULL);
    _cmsICCPROFILE* Icc = (_cmsICCPROFILE*) _cmsMallocZero(ContextID, sizeof(_cmsICCPROFILE));
    if (Icc == NULL) return NULL;

    Icc ->ContextID = ContextID;

    // Set it to empty
    Icc -> TagCount   = 0;

    // Set default version
    Icc ->Version =  0x02100000;

    // Set creation date/time
    memmove(&Icc ->Created, gmtime(&now), sizeof(Icc ->Created));

    // Return the handle
    return (cmsHPROFILE) Icc;
}

cmsContext CMSEXPORT cmsGetProfileContextID(cmsHPROFILE hProfile)
{
     _cmsICCPROFILE* Icc = (_cmsICCPROFILE*) hProfile;

    if (Icc == NULL) return NULL;
    return Icc -> ContextID;
}


// Return the number of tags
cmsInt32Number CMSEXPORT cmsGetTagCount(cmsHPROFILE hProfile)
{
    _cmsICCPROFILE* Icc = (_cmsICCPROFILE*) hProfile;
    if (Icc == NULL) return -1;

    return  Icc->TagCount;
}

// Return the tag signature of a given tag number
cmsTagSignature CMSEXPORT cmsGetTagSignature(cmsHPROFILE hProfile, cmsUInt32Number n)
{
    _cmsICCPROFILE* Icc = (_cmsICCPROFILE*) hProfile;

    if (n > Icc->TagCount) return (cmsTagSignature) 0;  // Mark as not available
    if (n >= MAX_TABLE_TAG) return (cmsTagSignature) 0; // As double check

    return Icc ->TagNames[n];
}


static
int SearchOneTag(_cmsICCPROFILE* Profile, cmsTagSignature sig)
{
    cmsUInt32Number i;

    for (i=0; i < Profile -> TagCount; i++) {

        if (sig == Profile -> TagNames[i])
            return i;
    }

    return -1;
}

// Search for a specific tag in tag dictionary. Returns position or -1 if tag not found.
// If followlinks is turned on, then the position of the linked tag is returned
int _cmsSearchTag(_cmsICCPROFILE* Icc, cmsTagSignature sig, cmsBool lFollowLinks)
{
    int n;
    cmsTagSignature LinkedSig;

    do {

        // Search for given tag in ICC profile directory
        n = SearchOneTag(Icc, sig);
        if (n < 0)
            return -1;        // Not found

        if (!lFollowLinks)
            return n;         // Found, don't follow links

        // Is this a linked tag?
        LinkedSig = Icc ->TagLinked[n];

        // Yes, follow link
        if (LinkedSig != (cmsTagSignature) 0) {
            sig = LinkedSig;
        }

    } while (LinkedSig != (cmsTagSignature) 0);

    return n;
}


// Create a new tag entry

static
cmsBool _cmsNewTag(_cmsICCPROFILE* Icc, cmsTagSignature sig, int* NewPos)
{
    int i;

    // Search for the tag
    i = _cmsSearchTag(Icc, sig, FALSE);

    // Now let's do it easy. If the tag has been already written, that's an error
    if (i >= 0) {
        cmsSignalError(Icc ->ContextID, cmsERROR_ALREADY_DEFINED, "Tag '%x' already exists", sig);
        return FALSE;
    }
    else  {

        // New one

        if (Icc -> TagCount >= MAX_TABLE_TAG) {
            cmsSignalError(Icc ->ContextID, cmsERROR_RANGE, "Too many tags (%d)", MAX_TABLE_TAG);
            return FALSE;
        }

        *NewPos = Icc ->TagCount;
        Icc -> TagCount++;
    }

    return TRUE;
}


// Check existance
cmsBool CMSEXPORT cmsIsTag(cmsHPROFILE hProfile, cmsTagSignature sig)
{
       _cmsICCPROFILE*  Icc = (_cmsICCPROFILE*) (void*) hProfile;
       return _cmsSearchTag(Icc, sig, FALSE) >= 0;
}


// Read profile header and validate it
cmsBool _cmsReadHeader(_cmsICCPROFILE* Icc)
{
    cmsTagEntry Tag;
    cmsICCHeader Header;
    cmsUInt32Number i, j;
    cmsUInt32Number HeaderSize;
    cmsIOHANDLER* io = Icc ->IOhandler;
    cmsUInt32Number TagCount;


    // Read the header
    if (io -> Read(io, &Header, sizeof(cmsICCHeader), 1) != 1) {
        return FALSE;
    }

    // Validate file as an ICC profile
    if (_cmsAdjustEndianess32(Header.magic) != cmsMagicNumber) {
        cmsSignalError(Icc ->ContextID, cmsERROR_BAD_SIGNATURE, "not an ICC profile, invalid signature");
        return FALSE;
    }

    // Adjust endianess of the used parameters
    Icc -> DeviceClass     = (cmsProfileClassSignature) _cmsAdjustEndianess32(Header.deviceClass);
    Icc -> ColorSpace      = (cmsColorSpaceSignature)   _cmsAdjustEndianess32(Header.colorSpace);
    Icc -> PCS             = (cmsColorSpaceSignature)   _cmsAdjustEndianess32(Header.pcs);
    Icc -> RenderingIntent = _cmsAdjustEndianess32(Header.renderingIntent);
    Icc -> flags           = _cmsAdjustEndianess32(Header.flags);
    Icc -> manufacturer    = _cmsAdjustEndianess32(Header.manufacturer);
    Icc -> model           = _cmsAdjustEndianess32(Header.model);
    _cmsAdjustEndianess64(&Icc -> attributes, Header.attributes);
    Icc -> Version         = _cmsAdjustEndianess32(Header.version);

    // Get size as reported in header
    HeaderSize = _cmsAdjustEndianess32(Header.size);

    // Get creation date/time
    _cmsDecodeDateTimeNumber(&Header.date, &Icc ->Created);

    // The profile ID are 32 raw bytes
    memmove(Icc ->ProfileID.ID32, Header.profileID.ID32, 16);


    // Read tag directory
    if (!_cmsReadUInt32Number(io, &TagCount)) return FALSE;
    if (TagCount > MAX_TABLE_TAG) {

        cmsSignalError(Icc ->ContextID, cmsERROR_RANGE, "Too many tags (%d)", TagCount);
        return FALSE;
    }

    // Read tag directory
    Icc -> TagCount = 0;
    for (i=0; i < TagCount; i++) {

        if (!_cmsReadUInt32Number(io, (cmsUInt32Number *) &Tag.sig)) return FALSE;
        if (!_cmsReadUInt32Number(io, &Tag.offset)) return FALSE;
        if (!_cmsReadUInt32Number(io, &Tag.size)) return FALSE;

        // Perform some sanity check. Offset + size should fall inside file.
        if (Tag.offset + Tag.size > HeaderSize)
                  continue;

        Icc -> TagNames[Icc ->TagCount]   = Tag.sig;
        Icc -> TagOffsets[Icc ->TagCount] = Tag.offset;
        Icc -> TagSizes[Icc ->TagCount]   = Tag.size;

       // Search for links
        for (j=0; j < Icc ->TagCount; j++) {

            if ((Icc ->TagOffsets[j] == Tag.offset) &&
                (Icc ->TagSizes[j]   == Tag.size)) {

                Icc ->TagLinked[Icc ->TagCount] = Icc ->TagNames[j];
            }

        }

        Icc ->TagCount++;
    }

    return TRUE;
}

// Saves profile header
cmsBool _cmsWriteHeader(_cmsICCPROFILE* Icc, cmsUInt32Number UsedSpace)
{
    cmsICCHeader Header;
    cmsUInt32Number i;
    cmsTagEntry Tag;
    cmsInt32Number Count = 0;

    Header.size        = _cmsAdjustEndianess32(UsedSpace);
    Header.cmmId       = _cmsAdjustEndianess32(lcmsSignature);
    Header.version     = _cmsAdjustEndianess32(Icc ->Version);

    Header.deviceClass = (cmsProfileClassSignature) _cmsAdjustEndianess32(Icc -> DeviceClass);
    Header.colorSpace  = (cmsColorSpaceSignature) _cmsAdjustEndianess32(Icc -> ColorSpace);
    Header.pcs         = (cmsColorSpaceSignature) _cmsAdjustEndianess32(Icc -> PCS);

    //   NOTE: in v4 Timestamp must be in UTC rather than in local time
    _cmsEncodeDateTimeNumber(&Header.date, &Icc ->Created);

    Header.magic       = _cmsAdjustEndianess32(cmsMagicNumber);

#ifdef CMS_IS_WINDOWS_
    Header.platform    = (cmsPlatformSignature) _cmsAdjustEndianess32(cmsSigMicrosoft);
#else
    Header.platform    = (cmsPlatformSignature) _cmsAdjustEndianess32(cmsSigMacintosh);
#endif

    Header.flags        = _cmsAdjustEndianess32(Icc -> flags);
    Header.manufacturer = _cmsAdjustEndianess32(Icc -> manufacturer);
    Header.model        = _cmsAdjustEndianess32(Icc -> model);

    _cmsAdjustEndianess64(&Header.attributes, Icc -> attributes);

    // Rendering intent in the header (for embedded profiles)
    Header.renderingIntent = _cmsAdjustEndianess32(Icc -> RenderingIntent);

    // Illuminant is always D50
    Header.illuminant.X = _cmsAdjustEndianess32(_cmsDoubleTo15Fixed16(cmsD50_XYZ()->X));
    Header.illuminant.Y = _cmsAdjustEndianess32(_cmsDoubleTo15Fixed16(cmsD50_XYZ()->Y));
    Header.illuminant.Z = _cmsAdjustEndianess32(_cmsDoubleTo15Fixed16(cmsD50_XYZ()->Z));

    // Created by LittleCMS (that's me!)
    Header.creator      = _cmsAdjustEndianess32(lcmsSignature);

    memset(&Header.reserved, 0, sizeof(Header.reserved));

    // Set profile ID. Endianess is always big endian
    memmove(&Header.profileID, &Icc ->ProfileID, 16);

    // Dump the header
    if (!Icc -> IOhandler->Write(Icc->IOhandler, sizeof(cmsICCHeader), &Header)) return FALSE;

    // Saves Tag directory

    // Get true count
    for (i=0;  i < Icc -> TagCount; i++) {
        if (Icc ->TagNames[i] != 0)
            Count++;
    }

    // Store number of tags
    if (!_cmsWriteUInt32Number(Icc ->IOhandler, Count)) return FALSE;

    for (i=0; i < Icc -> TagCount; i++) {

        if (Icc ->TagNames[i] == 0) continue;   // It is just a placeholder

        Tag.sig    = (cmsTagSignature) _cmsAdjustEndianess32((cmsInt32Number) Icc -> TagNames[i]);
        Tag.offset = _cmsAdjustEndianess32((cmsInt32Number) Icc -> TagOffsets[i]);
        Tag.size   = _cmsAdjustEndianess32((cmsInt32Number) Icc -> TagSizes[i]);

        if (!Icc ->IOhandler -> Write(Icc-> IOhandler, sizeof(cmsTagEntry), &Tag)) return FALSE;
    }

    return TRUE;
}

// ----------------------------------------------------------------------- Set/Get several struct members


cmsUInt32Number CMSEXPORT cmsGetHeaderRenderingIntent(cmsHPROFILE hProfile)
{
    _cmsICCPROFILE*  Icc = (_cmsICCPROFILE*) hProfile;
    return Icc -> RenderingIntent;
}

void CMSEXPORT cmsSetHeaderRenderingIntent(cmsHPROFILE hProfile, cmsUInt32Number RenderingIntent)
{
    _cmsICCPROFILE*  Icc = (_cmsICCPROFILE*) hProfile;
    Icc -> RenderingIntent = RenderingIntent;
}

cmsUInt32Number CMSEXPORT cmsGetHeaderFlags(cmsHPROFILE hProfile)
{
    _cmsICCPROFILE*  Icc = (_cmsICCPROFILE*) hProfile;
    return (cmsUInt32Number) Icc -> flags;
}

void CMSEXPORT cmsSetHeaderFlags(cmsHPROFILE hProfile, cmsUInt32Number Flags)
{
    _cmsICCPROFILE*  Icc = (_cmsICCPROFILE*) hProfile;
    Icc -> flags = (cmsUInt32Number) Flags;
}

cmsUInt32Number CMSEXPORT cmsGetHeaderManufacturer(cmsHPROFILE hProfile)
{
    _cmsICCPROFILE*  Icc = (_cmsICCPROFILE*) hProfile;
    return (cmsUInt32Number) Icc ->manufacturer;
}

void CMSEXPORT cmsSetHeaderManufacturer(cmsHPROFILE hProfile, cmsUInt32Number manufacturer)
{
    _cmsICCPROFILE*  Icc = (_cmsICCPROFILE*) hProfile;
    Icc -> manufacturer = (cmsUInt32Number) manufacturer;
}

cmsUInt32Number CMSEXPORT cmsGetHeaderModel(cmsHPROFILE hProfile)
{
    _cmsICCPROFILE*  Icc = (_cmsICCPROFILE*) hProfile;
    return (cmsUInt32Number) Icc ->model;
}

void CMSEXPORT cmsSetHeaderModel(cmsHPROFILE hProfile, cmsUInt32Number model)
{
    _cmsICCPROFILE*  Icc = (_cmsICCPROFILE*) hProfile;
    Icc -> manufacturer = (cmsUInt32Number) model;
}


void CMSEXPORT cmsGetHeaderAttributes(cmsHPROFILE hProfile, cmsUInt64Number* Flags)
{
    _cmsICCPROFILE*  Icc = (_cmsICCPROFILE*) hProfile;
    memmove(Flags, &Icc -> attributes, sizeof(cmsUInt64Number));
}

void CMSEXPORT cmsSetHeaderAttributes(cmsHPROFILE hProfile, cmsUInt64Number Flags)
{
    _cmsICCPROFILE*  Icc = (_cmsICCPROFILE*) hProfile;
    memmove(&Icc -> attributes, &Flags, sizeof(cmsUInt64Number));
}

void CMSEXPORT cmsGetHeaderProfileID(cmsHPROFILE hProfile, cmsUInt8Number* ProfileID)
{
    _cmsICCPROFILE*  Icc = (_cmsICCPROFILE*) hProfile;
    memmove(ProfileID, Icc ->ProfileID.ID8, 16);
}

void CMSEXPORT cmsSetHeaderProfileID(cmsHPROFILE hProfile, cmsUInt8Number* ProfileID)
{
    _cmsICCPROFILE*  Icc = (_cmsICCPROFILE*) hProfile;
    memmove(&Icc -> ProfileID, ProfileID, 16);
}

cmsBool  CMSEXPORT cmsGetHeaderCreationDateTime(cmsHPROFILE hProfile, struct tm *Dest)
{
    _cmsICCPROFILE*  Icc = (_cmsICCPROFILE*) hProfile;
    memmove(Dest, &Icc ->Created, sizeof(struct tm));
    return TRUE;
}

cmsColorSpaceSignature CMSEXPORT cmsGetPCS(cmsHPROFILE hProfile)
{
    _cmsICCPROFILE*  Icc = (_cmsICCPROFILE*) hProfile;
    return Icc -> PCS;
}

void CMSEXPORT cmsSetPCS(cmsHPROFILE hProfile, cmsColorSpaceSignature pcs)
{
    _cmsICCPROFILE*  Icc = (_cmsICCPROFILE*) hProfile;
    Icc -> PCS = pcs;
}

cmsColorSpaceSignature CMSEXPORT cmsGetColorSpace(cmsHPROFILE hProfile)
{
    _cmsICCPROFILE*  Icc = (_cmsICCPROFILE*) hProfile;
    return Icc -> ColorSpace;
}

void CMSEXPORT cmsSetColorSpace(cmsHPROFILE hProfile, cmsColorSpaceSignature sig)
{
    _cmsICCPROFILE*  Icc = (_cmsICCPROFILE*) hProfile;
    Icc -> ColorSpace = sig;
}

cmsProfileClassSignature CMSEXPORT cmsGetDeviceClass(cmsHPROFILE hProfile)
{
    _cmsICCPROFILE*  Icc = (_cmsICCPROFILE*) hProfile;
    return Icc -> DeviceClass;
}

void CMSEXPORT cmsSetDeviceClass(cmsHPROFILE hProfile, cmsProfileClassSignature sig)
{
    _cmsICCPROFILE*  Icc = (_cmsICCPROFILE*) hProfile;
    Icc -> DeviceClass = sig;
}

cmsUInt32Number CMSEXPORT cmsGetEncodedICCversion(cmsHPROFILE hProfile)
{
    _cmsICCPROFILE*  Icc = (_cmsICCPROFILE*) hProfile;
    return Icc -> Version;
}

void CMSEXPORT cmsSetEncodedICCversion(cmsHPROFILE hProfile, cmsUInt32Number Version)
{
    _cmsICCPROFILE*  Icc = (_cmsICCPROFILE*) hProfile;
    Icc -> Version = Version;
}

// Get an hexadecimal number with same digits as v
static
cmsUInt32Number BaseToBase(cmsUInt32Number in, int BaseIn, int BaseOut)
{
    char Buff[100];
    int i, len;
    cmsUInt32Number out;

    for (len=0; in > 0 && len < 100; len++) {

        Buff[len] = (char) (in % BaseIn);
        in /= BaseIn;
    }

    for (i=len-1, out=0; i >= 0; --i) {
        out = out * BaseOut + Buff[i];
    }

    return out;
}

void  CMSEXPORT cmsSetProfileVersion(cmsHPROFILE hProfile, cmsFloat64Number Version)
{
    _cmsICCPROFILE*  Icc = (_cmsICCPROFILE*) hProfile;

    // 4.2 -> 0x4200000

    Icc -> Version = BaseToBase((cmsUInt32Number) floor(Version * 100.0), 10, 16) << 16;
}

cmsFloat64Number CMSEXPORT cmsGetProfileVersion(cmsHPROFILE hProfile)
{
    _cmsICCPROFILE*  Icc = (_cmsICCPROFILE*) hProfile;
    cmsUInt32Number n = Icc -> Version >> 16;

    return BaseToBase(n, 16, 10) / 100.0;
}
// --------------------------------------------------------------------------------------------------------------


// Create profile from IOhandler
cmsHPROFILE CMSEXPORT cmsOpenProfileFromIOhandlerTHR(cmsContext ContextID, cmsIOHANDLER* io)
{
    _cmsICCPROFILE* NewIcc;
    cmsHPROFILE hEmpty = cmsCreateProfilePlaceholder(ContextID);

    if (hEmpty == NULL) return NULL;

    NewIcc = (_cmsICCPROFILE*) hEmpty;

    NewIcc ->IOhandler = io;
    if (!_cmsReadHeader(NewIcc)) goto Error;
    return hEmpty;

Error:
    cmsCloseProfile(hEmpty);
    return NULL;
}

// Create profile from disk file
cmsHPROFILE CMSEXPORT cmsOpenProfileFromFileTHR(cmsContext ContextID, const char *lpFileName, const char *sAccess)
{
    _cmsICCPROFILE* NewIcc;
    cmsHPROFILE hEmpty = cmsCreateProfilePlaceholder(ContextID);

    if (hEmpty == NULL) return NULL;

    NewIcc = (_cmsICCPROFILE*) hEmpty;

    NewIcc ->IOhandler = cmsOpenIOhandlerFromFile(ContextID, lpFileName, sAccess);
    if (NewIcc ->IOhandler == NULL) goto Error;

    if (*sAccess == 'W' || *sAccess == 'w') {

        NewIcc -> IsWrite = TRUE;

        return hEmpty;
    }

    if (!_cmsReadHeader(NewIcc)) goto Error;
    return hEmpty;

Error:
    cmsCloseProfile(hEmpty);
    return NULL;
}


cmsHPROFILE CMSEXPORT cmsOpenProfileFromFile(const char *ICCProfile, const char *sAccess)
{
    return cmsOpenProfileFromFileTHR(NULL, ICCProfile, sAccess);
}


cmsHPROFILE  CMSEXPORT cmsOpenProfileFromStreamTHR(cmsContext ContextID, FILE* ICCProfile, const char *sAccess)
{
    _cmsICCPROFILE* NewIcc;
    cmsHPROFILE hEmpty = cmsCreateProfilePlaceholder(ContextID);

    if (hEmpty == NULL) return NULL;

    NewIcc = (_cmsICCPROFILE*) hEmpty;

    NewIcc ->IOhandler = cmsOpenIOhandlerFromStream(ContextID, ICCProfile);
    if (NewIcc ->IOhandler == NULL) goto Error;

    if (*sAccess == 'w') {

        NewIcc -> IsWrite = TRUE;
        return hEmpty;
    }

    if (!_cmsReadHeader(NewIcc)) goto Error;
    return hEmpty;

Error:
    cmsCloseProfile(hEmpty);
    return NULL;

}

cmsHPROFILE  CMSEXPORT cmsOpenProfileFromStream(FILE* ICCProfile, const char *sAccess)
{
    return cmsOpenProfileFromStreamTHR(NULL, ICCProfile, sAccess);
}


// Open from memory block
cmsHPROFILE CMSEXPORT cmsOpenProfileFromMemTHR(cmsContext ContextID, const void* MemPtr, cmsUInt32Number dwSize)
{
    _cmsICCPROFILE* NewIcc;
    cmsHPROFILE hEmpty;

    hEmpty = cmsCreateProfilePlaceholder(ContextID);
    if (hEmpty == NULL) return NULL;

    NewIcc = (_cmsICCPROFILE*) hEmpty;

    // Ok, in this case const void* is casted to void* just because open IO handler
    // shares read and writting modes. Don't abuse this feature!
    NewIcc ->IOhandler = cmsOpenIOhandlerFromMem(ContextID, (void*) MemPtr, dwSize, "r");
    if (NewIcc ->IOhandler == NULL) goto Error;

    if (!_cmsReadHeader(NewIcc)) goto Error;

    return hEmpty;

Error:
    cmsCloseProfile(hEmpty);
    return NULL;
}

cmsHPROFILE CMSEXPORT cmsOpenProfileFromMem(const void* MemPtr, cmsUInt32Number dwSize)
{
    return cmsOpenProfileFromMemTHR(NULL, MemPtr, dwSize);
}



// Dump tag contents. If the profile is being modified, untouched tags are copied from FileOrig
static
cmsBool SaveTags(_cmsICCPROFILE* Icc, _cmsICCPROFILE* FileOrig)
{
    cmsUInt8Number* Data;
    cmsUInt32Number i;
    cmsUInt32Number Begin;
    cmsIOHANDLER* io = Icc ->IOhandler;
    cmsTagDescriptor* TagDescriptor;
    cmsTagTypeSignature TypeBase;
    cmsTagTypeHandler* TypeHandler;


    for (i=0; i < Icc -> TagCount; i++) {


        if (Icc ->TagNames[i] == 0) continue;

        // Linked tags are not written
        if (Icc ->TagLinked[i] != (cmsTagSignature) 0) continue;

        Icc -> TagOffsets[i] = Begin = io ->UsedSpace;

        Data = (cmsUInt8Number*)  Icc -> TagPtrs[i];

        if (!Data) {

            // Reach here if we are copying a tag from a disk-based ICC profile which has not been modified by user.
            // In this case a blind copy of the block data is performed
            if (FileOrig != NULL && Icc -> TagOffsets[i]) {

                cmsUInt32Number TagSize   = FileOrig -> TagSizes[i];
                cmsUInt32Number TagOffset = FileOrig -> TagOffsets[i];
                void* Mem;

                if (!FileOrig ->IOhandler->Seek(FileOrig ->IOhandler, TagOffset)) return FALSE;

                Mem = _cmsMalloc(Icc ->ContextID, TagSize);
                if (Mem == NULL) return FALSE;

                if (FileOrig ->IOhandler->Read(FileOrig->IOhandler, Mem, TagSize, 1) != 1) return FALSE;
                if (!io ->Write(io, TagSize, Mem)) return FALSE;
                _cmsFree(Icc ->ContextID, Mem);

                Icc -> TagSizes[i] = (io ->UsedSpace - Begin);


                // Align to 32 bit boundary.
                if (! _cmsWriteAlignment(io))
                    return FALSE;
            }

            continue;
        }


        // Should this tag be saved as RAW? If so, tagsizes should be specified in advance (no further cooking is done)
        if (Icc ->TagSaveAsRaw[i]) {

            if (io -> Write(io, Icc ->TagSizes[i], Data) != 1) return FALSE;
        }
        else {

            // Search for support on this tag
            TagDescriptor = _cmsGetTagDescriptor(Icc -> TagNames[i]);
            if (TagDescriptor == NULL) continue;                        // Unsupported, ignore it

            TypeHandler = Icc ->TagTypeHandlers[i];

            if (TypeHandler == NULL) {
                cmsSignalError(Icc ->ContextID, cmsERROR_INTERNAL, "(Internal) no handler for tag %x", Icc -> TagNames[i]);
                continue;
            }

            TypeBase    = TypeHandler ->Signature;
            if (!_cmsWriteTypeBase(io, TypeBase))
                return FALSE;

            if (!TypeHandler ->WritePtr(TypeHandler, io, Data, TagDescriptor ->ElemCount)) {

                char String[5];

                _cmsTagSignature2String(String, (cmsTagSignature) TypeBase);
                cmsSignalError(Icc ->ContextID, cmsERROR_WRITE, "Couldn't write type '%s'", String);
                return FALSE;
            }
        }


        Icc -> TagSizes[i] = (io ->UsedSpace - Begin);

        // Align to 32 bit boundary.
        if (! _cmsWriteAlignment(io))
            return FALSE;
    }


    return TRUE;
}


// Fill the offset and size fields for all linked tags
static
cmsBool SetLinks( _cmsICCPROFILE* Icc)
{
    cmsUInt32Number i;

    for (i=0; i < Icc -> TagCount; i++) {

        cmsTagSignature lnk = Icc ->TagLinked[i];
        if (lnk != (cmsTagSignature) 0) {

            int j = _cmsSearchTag(Icc, lnk, FALSE);
            if (j >= 0) {

                Icc ->TagOffsets[i] = Icc ->TagOffsets[j];
                Icc ->TagSizes[i]   = Icc ->TagSizes[j];
            }

        }
    }

    return TRUE;
}

// Low-level save to IOHANDLER. It returns the number of bytes used to
// store the profile, or zero on error. io may be NULL and in this case
// no data is written--only sizes are calculated
cmsUInt32Number CMSEXPORT cmsSaveProfileToIOhandler(cmsHPROFILE hProfile, cmsIOHANDLER* io)
{
    _cmsICCPROFILE* Icc = (_cmsICCPROFILE*) hProfile;
    _cmsICCPROFILE Keep;
    cmsIOHANDLER* PrevIO;
    cmsUInt32Number UsedSpace;
    cmsContext ContextID;

    memmove(&Keep, Icc, sizeof(_cmsICCPROFILE));

    ContextID = cmsGetProfileContextID(hProfile);
    PrevIO = Icc ->IOhandler = cmsOpenIOhandlerFromNULL(ContextID);
    if (PrevIO == NULL) return 0;

    // Pass #1 does compute offsets

    if (!_cmsWriteHeader(Icc, 0)) return 0;
    if (!SaveTags(Icc, &Keep)) return 0;

    UsedSpace = PrevIO ->UsedSpace;

    // Pass #2 does save to iohandler

    if (io != NULL) {
        Icc ->IOhandler = io;
        if (!SetLinks(Icc)) goto CleanUp;
        if (!_cmsWriteHeader(Icc, UsedSpace)) goto CleanUp;
        if (!SaveTags(Icc, &Keep)) goto CleanUp;
    }

    memmove(Icc, &Keep, sizeof(_cmsICCPROFILE));
    if (!cmsCloseIOhandler(PrevIO)) return 0;

    return UsedSpace;


CleanUp:
    cmsCloseIOhandler(PrevIO);
    memmove(Icc, &Keep, sizeof(_cmsICCPROFILE));
    return 0;
}


// Low-level save to disk.
cmsBool  CMSEXPORT cmsSaveProfileToFile(cmsHPROFILE hProfile, const char* FileName)
{
    cmsContext ContextID = cmsGetProfileContextID(hProfile);
    cmsIOHANDLER* io = cmsOpenIOhandlerFromFile(ContextID, FileName, "w");
    cmsBool rc;

    if (io == NULL) return FALSE;

    rc = (cmsSaveProfileToIOhandler(hProfile, io) != 0);
    rc &= cmsCloseIOhandler(io);

    if (rc == FALSE) {          // remove() is C99 per 7.19.4.1
            remove(FileName);   // We have to IGNORE return value in this case
    }
    return rc;
}

// Same as anterior, but for streams
cmsBool CMSEXPORT cmsSaveProfileToStream(cmsHPROFILE hProfile, FILE* Stream)
{
    cmsBool rc;
    cmsContext ContextID = cmsGetProfileContextID(hProfile);
    cmsIOHANDLER* io = cmsOpenIOhandlerFromStream(ContextID, Stream);

    if (io == NULL) return FALSE;

    rc = (cmsSaveProfileToIOhandler(hProfile, io) != 0);
    rc &= cmsCloseIOhandler(io);

    return rc;
}


// Same as anterior, but for memory blocks. In this case, a NULL as MemPtr means calculate needed space only
cmsBool CMSEXPORT cmsSaveProfileToMem(cmsHPROFILE hProfile, void *MemPtr, cmsUInt32Number* BytesNeeded)
{
    cmsBool rc;
    cmsIOHANDLER* io;
    cmsContext ContextID = cmsGetProfileContextID(hProfile);

    // Should we just calculate the needed space?
    if (MemPtr == NULL) {

           *BytesNeeded =  cmsSaveProfileToIOhandler(hProfile, NULL);
            return TRUE;
    }

    // That is a real write operation
    io =  cmsOpenIOhandlerFromMem(ContextID, MemPtr, *BytesNeeded, "w");
    if (io == NULL) return FALSE;

    rc = (cmsSaveProfileToIOhandler(hProfile, io) != 0);
    rc &= cmsCloseIOhandler(io);

    return rc;
}



// Closes a profile freeing any involved resources
cmsBool  CMSEXPORT cmsCloseProfile(cmsHPROFILE hProfile)
{
    _cmsICCPROFILE* Icc = (_cmsICCPROFILE*) hProfile;
    cmsBool  rc = TRUE;
    cmsUInt32Number i;

    if (!Icc) return FALSE;

    // Was open in write mode?
    if (Icc ->IsWrite) {

        Icc ->IsWrite = FALSE;      // Assure no further writting
        rc &= cmsSaveProfileToFile(hProfile, Icc ->IOhandler->PhysicalFile);
    }

    for (i=0; i < Icc -> TagCount; i++) {

        if (Icc -> TagPtrs[i]) {

            cmsTagTypeHandler* TypeHandler = Icc ->TagTypeHandlers[i];

            if (TypeHandler != NULL)
                TypeHandler ->FreePtr(TypeHandler, Icc -> TagPtrs[i]);
            else
                _cmsFree(Icc ->ContextID, Icc ->TagPtrs[i]);
        }
    }

    if (Icc ->IOhandler != NULL) {
        rc &= cmsCloseIOhandler(Icc->IOhandler);
    }

    _cmsFree(Icc ->ContextID, Icc);   // Free placeholder memory

    return rc;
}


// -------------------------------------------------------------------------------------------------------------------


// Returns TRUE if a given tag is supported by a plug-in
static
cmsBool IsTypeSupported(cmsTagDescriptor* TagDescriptor, cmsTagTypeSignature Type)
{
    cmsUInt32Number i, nMaxTypes;

    nMaxTypes = TagDescriptor->nSupportedTypes;
    if (nMaxTypes >= MAX_TYPES_IN_LCMS_PLUGIN)
        nMaxTypes = MAX_TYPES_IN_LCMS_PLUGIN;

    for (i=0; i < nMaxTypes; i++) {
        if (Type == TagDescriptor ->SupportedTypes[i]) return TRUE;
    }

    return FALSE;
}


// That's the main read function
void* CMSEXPORT cmsReadTag(cmsHPROFILE hProfile, cmsTagSignature sig)
{
    _cmsICCPROFILE* Icc = (_cmsICCPROFILE*) hProfile;
    cmsIOHANDLER* io = Icc ->IOhandler;
    cmsTagTypeHandler* TypeHandler;
    cmsTagDescriptor*  TagDescriptor;
    cmsTagTypeSignature BaseType;
    cmsUInt32Number Offset, TagSize;
    cmsUInt32Number ElemCount;
    int n;

    n = _cmsSearchTag(Icc, sig, TRUE);
    if (n < 0) return NULL;                 // Not found, return NULL



    // If the element is already in memory, return the pointer
    if (Icc -> TagPtrs[n]) {

        if (Icc ->TagSaveAsRaw[n]) return NULL;  // We don't support read raw tags as cooked
        return Icc -> TagPtrs[n];
    }

    // We need to read it. Get the offset and size to the file
    Offset    = Icc -> TagOffsets[n];
    TagSize   = Icc -> TagSizes[n];

    // Seek to its location
    if (!io -> Seek(io, Offset))
            return NULL;

    // Search for support on this tag
    TagDescriptor = _cmsGetTagDescriptor(sig);
    if (TagDescriptor == NULL) return NULL;     // Unsupported.

    // if supported, get type and check if in list
    BaseType = _cmsReadTypeBase(io);
    if (BaseType == 0) return NULL;

    if (!IsTypeSupported(TagDescriptor, BaseType)) return NULL;

    TagSize  -= 8;                      // Alredy read by the type base logic

    // Get type handler
    TypeHandler = _cmsGetTagTypeHandler(BaseType);
    if (TypeHandler == NULL) return NULL;


    // Read the tag
    Icc -> TagTypeHandlers[n] = TypeHandler;
    Icc -> TagPtrs[n] = TypeHandler ->ReadPtr(TypeHandler, io, &ElemCount, TagSize);

    // The tag type is supported, but something wrong happend and we cannot read the tag.
    // let know the user about this (although it is just a warning)
    if (Icc -> TagPtrs[n] == NULL) {

        char String[5];

        _cmsTagSignature2String(String, sig);
        cmsSignalError(Icc ->ContextID, cmsERROR_CORRUPTION_DETECTED, "Corrupted tag '%s'", String);
        return NULL;
    }

    // This is a weird error that may be a symptom of something more serious, the number of
    // stored item is actually less than the number of required elements.
    if (ElemCount < TagDescriptor ->ElemCount) {

        char String[5];

        _cmsTagSignature2String(String, sig);
        cmsSignalError(Icc ->ContextID, cmsERROR_CORRUPTION_DETECTED, "'%s' Inconsistent number of items: expected %d, got %d",
                                                             String, TagDescriptor ->ElemCount, ElemCount);
    }


    // Return the data
    return Icc -> TagPtrs[n];
}


// Get true type of data
cmsTagTypeSignature _cmsGetTagTrueType(cmsHPROFILE hProfile, cmsTagSignature sig)
{
    _cmsICCPROFILE* Icc = (_cmsICCPROFILE*) hProfile;
    cmsTagTypeHandler* TypeHandler;
    int n;

    // Search for given tag in ICC profile directory
    n = _cmsSearchTag(Icc, sig, TRUE);
    if (n < 0) return (cmsTagTypeSignature) 0;                // Not found, return NULL

    // Get the handler. The true type is there
    TypeHandler =  Icc -> TagTypeHandlers[n];
    return TypeHandler ->Signature;
}


// Write a single tag. This just keeps track of the tak into a list of "to be written". If the tag is already
// in that list, the previous version is deleted.
cmsBool CMSEXPORT cmsWriteTag(cmsHPROFILE hProfile, cmsTagSignature sig, const void* data)
{
    _cmsICCPROFILE* Icc = (_cmsICCPROFILE*) hProfile;
    cmsTagTypeHandler* TypeHandler = NULL;
    cmsTagDescriptor* TagDescriptor = NULL;
    cmsTagTypeSignature Type;
    int i;
    cmsFloat64Number Version;


    if (data == NULL) {

         cmsSignalError(cmsGetProfileContextID(hProfile), cmsERROR_NULL, "couldn't wite NULL to tag");
         return FALSE;
    }

    i = _cmsSearchTag(Icc, sig, FALSE);
    if (i >=0) {

        if (Icc -> TagPtrs[i] != NULL) {

            // Already exists. Free previous version
            if (Icc ->TagSaveAsRaw[i]) {
                _cmsFree(Icc ->ContextID, Icc ->TagPtrs[i]);
            }
            else {
                TypeHandler = Icc ->TagTypeHandlers[i];
                TypeHandler->FreePtr(TypeHandler, Icc -> TagPtrs[i]);
            }
        }
    }
    else  {
        // New one
        i = Icc -> TagCount;

        if (i >= MAX_TABLE_TAG) {
            cmsSignalError(Icc ->ContextID, cmsERROR_RANGE, "Too many tags (%d)", MAX_TABLE_TAG);
            return FALSE;
        }

        Icc -> TagCount++;
    }

    // This is not raw
    Icc ->TagSaveAsRaw[i] = FALSE;

    // This is not a link
    Icc ->TagLinked[i] = (cmsTagSignature) 0;

    // Get information about the TAG.
    TagDescriptor = _cmsGetTagDescriptor(sig);
    if (TagDescriptor == NULL){
         cmsSignalError(Icc ->ContextID, cmsERROR_UNKNOWN_EXTENSION, "Unsupported tag '%x'", sig);
        return FALSE;
    }


    // Now we need to know which type to use. It depends on the version.
    Version = cmsGetProfileVersion(hProfile);
    if (TagDescriptor ->DecideType != NULL) {

        // Let the tag descriptor to decide the type base on depending on
        // the data. This is useful for example on parametric curves, where
        // curves specified by a table cannot be saved as parametric and needs
        // to be revented to single v2-curves, even on v4 profiles.

        Type = TagDescriptor ->DecideType(Version, data);
    }
    else {

        Type = TagDescriptor ->SupportedTypes[0];
    }

    // Does the tag support this type?
    if (!IsTypeSupported(TagDescriptor, Type)) {
        cmsSignalError(Icc ->ContextID, cmsERROR_UNKNOWN_EXTENSION, "Unsupported type '%x' for tag '%x'", Type, sig);
        return FALSE;
    }

    // Does we have a handler for this type?
    TypeHandler =  _cmsGetTagTypeHandler(Type);
    if (TypeHandler == NULL) {
        cmsSignalError(Icc ->ContextID, cmsERROR_UNKNOWN_EXTENSION, "Unsupported type '%x' for tag '%x'", Type, sig);
        return FALSE;           // Should never happen
    }

    // Fill fields on icc structure
    Icc ->TagTypeHandlers[i]  = TypeHandler;
    Icc ->TagNames[i]         = sig;
    Icc ->TagSizes[i]         = 0;
    Icc ->TagOffsets[i]       = 0;
    Icc ->TagPtrs[i]          = TypeHandler ->DupPtr(TypeHandler, data, TagDescriptor ->ElemCount);

    if (Icc ->TagPtrs[i] == NULL)  {

        TypeHandler ->DupPtr(TypeHandler, data, TagDescriptor ->ElemCount);
        cmsSignalError(Icc ->ContextID, cmsERROR_CORRUPTION_DETECTED, "Malformed struct in type '%x' for tag '%x'", Type, sig);

        return FALSE;
    }

    return TRUE;
}

// Read and write raw data. The only way those function would work and keep consistence with normal read and write
// is to do an additional step of serialization. That means, readRaw would issue a normal read and then convert the obtained
// data to raw bytes by using the "write" serialization logic. And vice-versa. I know this may end in situations where
// raw data written does not exactly correspond with the raw data proposed to cmsWriteRaw data, but this approach allows
// to write a tag as raw data and the read it as handled.

cmsInt32Number CMSEXPORT cmsReadRawTag(cmsHPROFILE hProfile, cmsTagSignature sig, void* data, cmsUInt32Number BufferSize)
{
    _cmsICCPROFILE* Icc = (_cmsICCPROFILE*) hProfile;
    void *Object;
    int i;
    cmsIOHANDLER* MemIO;
    cmsTagTypeHandler* TypeHandler = NULL;
    cmsTagDescriptor* TagDescriptor = NULL;
    cmsUInt32Number rc;
    cmsUInt32Number Offset, TagSize;

    // Search for given tag in ICC profile directory
    i = _cmsSearchTag(Icc, sig, TRUE);
    if (i < 0) return 0;                 // Not found, return 0

    // It is already read?
    if (Icc -> TagPtrs[i] == NULL) {

        // No yet, get original position
        Offset   = Icc ->TagOffsets[i];
        TagSize  = Icc ->TagSizes[i];


        // read the data directly, don't keep copy
        if (data != NULL) {

            if (BufferSize < TagSize)
                 TagSize = BufferSize;

            if (!Icc ->IOhandler ->Seek(Icc ->IOhandler, Offset)) return 0;
            if (!Icc ->IOhandler ->Read(Icc ->IOhandler, data, 1, TagSize)) return 0;
        }

        return Icc ->TagSizes[i];
    }

    // The data has been already read, or written. But wait!, maybe the user choosed to save as
    // raw data. In this case, return the raw data directly
    if (Icc ->TagSaveAsRaw[i]) {

        if (data != NULL)  {

            TagSize  = Icc ->TagSizes[i];
            if (BufferSize < TagSize)
                       TagSize = BufferSize;

            memmove(data, Icc ->TagPtrs[i], TagSize);
        }

        return Icc ->TagSizes[i];
    }

    // Already readed, or previously set by cmsWriteTag(). We need to serialize that
    // data to raw in order to maintain consistency.
    Object = cmsReadTag(hProfile, sig);
    if (Object == NULL) return 0;

    // Now we need to serialize to a memory block: just use a memory iohandler

    if (data == NULL) {
        MemIO = cmsOpenIOhandlerFromNULL(cmsGetProfileContextID(hProfile));
    } else{
          MemIO = cmsOpenIOhandlerFromMem(cmsGetProfileContextID(hProfile), data, BufferSize, "w");
    }
    if (MemIO == NULL) return 0;

    // Obtain type handling for the tag
    TypeHandler = Icc ->TagTypeHandlers[i];
    TagDescriptor = _cmsGetTagDescriptor(sig);

    // Serialize
    if (!TypeHandler ->WritePtr(TypeHandler, MemIO, Object, TagDescriptor ->ElemCount)) return 0;

    // Get Size and close
    rc = MemIO ->Tell(MemIO);
    cmsCloseIOhandler(MemIO);      // Ignore return code this time

    return rc;
}

// Similar to the anterior. This function allows to write directly to the ICC profile any data, without
// checking anything. As a rule, mixing Raw with cooked doesn't work, so writting a tag as raw and then reading
// it as cooked without serializing does result into an error. If that is wha you want, you will need to dump
// the profile to memry or disk and then reopen it.
cmsBool CMSEXPORT cmsWriteRawTag(cmsHPROFILE hProfile, cmsTagSignature sig, const void* data, cmsUInt32Number Size)
{
    _cmsICCPROFILE* Icc = (_cmsICCPROFILE*) hProfile;
    int i;

    if (!_cmsNewTag(Icc, sig, &i)) return FALSE;

    // Mark the tag as being written as RAW
    Icc ->TagSaveAsRaw[i] = TRUE;
    Icc ->TagNames[i]     = sig;
    Icc ->TagLinked[i]    = (cmsTagSignature) 0;

    // Keep a copy of the block
    Icc ->TagPtrs[i]  = _cmsDupMem(Icc ->ContextID, data, Size);
    Icc ->TagSizes[i] = Size;

    return TRUE;
}

// Using this function you can collapse several tag entries to the same block in the profile
cmsBool CMSEXPORT cmsLinkTag(cmsHPROFILE hProfile, cmsTagSignature sig, cmsTagSignature dest)
{
     _cmsICCPROFILE* Icc = (_cmsICCPROFILE*) hProfile;
    int i;

    if (!_cmsNewTag(Icc, sig, &i)) return FALSE;

    // Keep necessary information
    Icc ->TagSaveAsRaw[i] = FALSE;
    Icc ->TagNames[i]     = sig;
    Icc ->TagLinked[i]    = dest;

    Icc ->TagPtrs[i]    = NULL;
    Icc ->TagSizes[i]   = 0;
    Icc ->TagOffsets[i] = 0;

    return TRUE;
}
