/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
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
// IT8.7 / CGATS.17-200x handling

#include "lcms.h"


LCMSAPI LCMSHANDLE      LCMSEXPORT cmsIT8Alloc(void);
LCMSAPI void            LCMSEXPORT cmsIT8Free(LCMSHANDLE IT8);

// Tables

LCMSAPI int             LCMSEXPORT cmsIT8TableCount(LCMSHANDLE IT8);
LCMSAPI int             LCMSEXPORT cmsIT8SetTable(LCMSHANDLE IT8, int nTable);

// Persistence
LCMSAPI LCMSHANDLE      LCMSEXPORT cmsIT8LoadFromFile(const char* cFileName);
LCMSAPI LCMSHANDLE      LCMSEXPORT cmsIT8LoadFromMem(void *Ptr, size_t len);
LCMSAPI LCMSBOOL        LCMSEXPORT cmsIT8SaveToFile(LCMSHANDLE IT8, const char* cFileName);

// Properties
LCMSAPI const char*     LCMSEXPORT cmsIT8GetSheetType(LCMSHANDLE hIT8);
LCMSAPI LCMSBOOL        LCMSEXPORT cmsIT8SetSheetType(LCMSHANDLE hIT8, const char* Type);

LCMSAPI LCMSBOOL        LCMSEXPORT cmsIT8SetComment(LCMSHANDLE hIT8, const char* cComment);

LCMSAPI LCMSBOOL        LCMSEXPORT cmsIT8SetPropertyStr(LCMSHANDLE hIT8, const char* cProp, const char *Str);
LCMSAPI LCMSBOOL        LCMSEXPORT cmsIT8SetPropertyDbl(LCMSHANDLE hIT8, const char* cProp, double Val);
LCMSAPI LCMSBOOL        LCMSEXPORT cmsIT8SetPropertyHex(LCMSHANDLE hIT8, const char* cProp, int Val);
LCMSAPI LCMSBOOL        LCMSEXPORT cmsIT8SetPropertyMulti(LCMSHANDLE hIT8, const char* cProp, const char* cSubProp, const char *Val);
LCMSAPI LCMSBOOL        LCMSEXPORT cmsIT8SetPropertyUncooked(LCMSHANDLE hIT8, const char* Key, const char* Buffer);

LCMSAPI const char*     LCMSEXPORT cmsIT8GetProperty(LCMSHANDLE hIT8, const char* cProp);
LCMSAPI double          LCMSEXPORT cmsIT8GetPropertyDbl(LCMSHANDLE hIT8, const char* cProp);
LCMSAPI const char*     LCMSEXPORT cmsIT8GetPropertyMulti(LCMSHANDLE hIT8, const char* cProp, const char *cSubProp);
LCMSAPI int             LCMSEXPORT cmsIT8EnumProperties(LCMSHANDLE IT8, const char ***PropertyNames);
LCMSAPI int             LCMSEXPORT cmsIT8EnumPropertyMulti(LCMSHANDLE hIT8, const char* cProp, const char*** SubpropertyNames);

// Datasets

LCMSAPI const char*     LCMSEXPORT cmsIT8GetPatchName(LCMSHANDLE hIT8, int nPatch, char* buffer);

LCMSAPI const char*     LCMSEXPORT cmsIT8GetDataRowCol(LCMSHANDLE IT8, int row, int col);
LCMSAPI double          LCMSEXPORT cmsIT8GetDataRowColDbl(LCMSHANDLE IT8, int col, int row);

LCMSAPI LCMSBOOL        LCMSEXPORT cmsIT8SetDataRowCol(LCMSHANDLE hIT8, int row, int col,
                                                const char* Val);

LCMSAPI LCMSBOOL        LCMSEXPORT cmsIT8SetDataRowColDbl(LCMSHANDLE hIT8, int row, int col,
                                                double Val);

LCMSAPI const char*     LCMSEXPORT cmsIT8GetData(LCMSHANDLE IT8, const char* cPatch, const char* cSample);


LCMSAPI double          LCMSEXPORT cmsIT8GetDataDbl(LCMSHANDLE IT8, const char* cPatch, const char* cSample);

LCMSAPI LCMSBOOL        LCMSEXPORT cmsIT8SetData(LCMSHANDLE IT8, const char* cPatch,
                                                const char* cSample,
                                                const char *Val);

LCMSAPI LCMSBOOL        LCMSEXPORT cmsIT8SetDataDbl(LCMSHANDLE hIT8, const char* cPatch,
                                                const char* cSample,
                                                double Val);

LCMSAPI LCMSBOOL        LCMSEXPORT cmsIT8SetDataFormat(LCMSHANDLE IT8, int n, const char *Sample);
LCMSAPI int             LCMSEXPORT cmsIT8EnumDataFormat(LCMSHANDLE IT8, char ***SampleNames);

LCMSAPI void            LCMSEXPORT cmsIT8DefineDblFormat(LCMSHANDLE IT8, const char* Formatter);

LCMSAPI int             LCMSEXPORT cmsIT8SetTableByLabel(LCMSHANDLE hIT8, const char* cSet,
                                                                          const char* cField,
                                                                          const char* ExpectedType);

// ------------------------------------------------------------- Implementation


#define SIZEOFLONGMINUS1    (sizeof(long)-1)
#define ALIGNLONG(x) (((x)+SIZEOFLONGMINUS1) & ~(SIZEOFLONGMINUS1))

// #define STRICT_CGATS  1

#define MAXID       128     // Max lenght of identifier
#define MAXSTR      1024     // Max lenght of string
#define MAXTABLES   255     // Max Number of tables in a single stream
#define MAXINCLUDE   20     // Max number of nested includes

#define DEFAULT_DBL_FORMAT  "%.10g" // Double formatting

#include <ctype.h>
#include <limits.h>

#ifndef NON_WINDOWS
#include <io.h>
#define DIR_CHAR    '\\'
#else
#define DIR_CHAR    '/'
#endif

// Symbols

typedef enum {

        SNONE,
        SINUM,      // Integer
        SDNUM,      // Real
        SIDENT,     // Identifier
        SSTRING,    // string
        SCOMMENT,   // comment
        SEOLN,      // End of line
        SEOF,       // End of stream
        SSYNERROR,  // Syntax error found on stream

        // Keywords

        SBEGIN_DATA,
        SBEGIN_DATA_FORMAT,
        SEND_DATA,
        SEND_DATA_FORMAT,
        SKEYWORD,
        SDATA_FORMAT_ID,
        SINCLUDE

    } SYMBOL;


// How to write the value

typedef enum {
        WRITE_UNCOOKED,
        WRITE_STRINGIFY,
        WRITE_HEXADECIMAL,
        WRITE_BINARY,
        WRITE_PAIR

    } WRITEMODE;

// Linked list of variable names

typedef struct _KeyVal {

        struct _KeyVal*  Next;
        char*            Keyword;       // Name of variable
        struct _KeyVal*  NextSubkey;    // If key is a dictionary, points to the next item
        char*            Subkey;        // If key is a dictionary, points to the subkey name
        char*            Value;         // Points to value
        WRITEMODE        WriteAs;       // How to write the value

   } KEYVALUE, *LPKEYVALUE;


// Linked list of memory chunks (Memory sink)

typedef struct _OwnedMem {

        struct _OwnedMem* Next;
        void *            Ptr;          // Point to value

   } OWNEDMEM, *LPOWNEDMEM;

// Suballocator

typedef struct _SubAllocator {

         LPBYTE Block;
         size_t BlockSize;
         size_t Used;

    } SUBALLOCATOR, *LPSUBALLOCATOR;

// Table. Each individual table can hold properties and rows & cols

typedef struct _Table {

        int            nSamples, nPatches;    // Cols, Rows
        int            SampleID;              // Pos of ID

        LPKEYVALUE     HeaderList;            // The properties

        char**         DataFormat;            // The binary stream descriptor
        char**         Data;                  // The binary stream

    } TABLE, *LPTABLE;

// File stream being parsed

typedef struct _FileContext {
        char           FileName[MAX_PATH];    // File name if being readed from file
        FILE*          Stream;                // File stream or NULL if holded in memory
    } FILECTX, *LPFILECTX;

// This struct hold all information about an openened
// IT8 handler. Only one dataset is allowed.

typedef struct {

        char SheetType[MAXSTR];

        int  TablesCount;                     // How many tables in this stream
        int  nTable;                          // The actual table

        TABLE Tab[MAXTABLES];

        // Memory management

        LPOWNEDMEM     MemorySink;            // The storage backend
        SUBALLOCATOR   Allocator;             // String suballocator -- just to keep it fast

        // Parser state machine

        SYMBOL         sy;                    // Current symbol
        int            ch;                    // Current character

        int            inum;                  // integer value
        double         dnum;                  // real value
        char           id[MAXID];             // identifier
        char           str[MAXSTR];           // string

        // Allowed keywords & datasets. They have visibility on whole stream

        LPKEYVALUE     ValidKeywords;
        LPKEYVALUE     ValidSampleID;

        char*          Source;                // Points to loc. being parsed
        int            lineno;                // line counter for error reporting

        LPFILECTX      FileStack[MAXINCLUDE]; // Stack of files being parsed
        int            IncludeSP;             // Include Stack Pointer

        char*          MemoryBlock;           // The stream if holded in memory

        char           DoubleFormatter[MAXID];   // Printf-like 'double' formatter

   } IT8, *LPIT8;



typedef struct {

        FILE* stream;   // For save-to-file behaviour

        LPBYTE Base;
        LPBYTE Ptr;     // For save-to-mem behaviour
        size_t Used;
        size_t Max;

    } SAVESTREAM, FAR* LPSAVESTREAM;


// ------------------------------------------------------ IT8 parsing routines


// A keyword
typedef struct {

        const char *id;
        SYMBOL sy;

   } KEYWORD;

// The keyword->symbol translation table. Sorting is required.
static const KEYWORD TabKeys[] = {

        {"$INCLUDE",            SINCLUDE},
        {".INCLUDE",            SINCLUDE},
        {"BEGIN_DATA",          SBEGIN_DATA },
        {"BEGIN_DATA_FORMAT",   SBEGIN_DATA_FORMAT },
        {"DATA_FORMAT_IDENTIFIER", SDATA_FORMAT_ID},
        {"END_DATA",            SEND_DATA},
        {"END_DATA_FORMAT",     SEND_DATA_FORMAT},
        {"KEYWORD",             SKEYWORD}
        };

#define NUMKEYS (sizeof(TabKeys)/sizeof(KEYWORD))

// Predefined properties

// A property
typedef struct {
        const char *id;
        WRITEMODE as;
    } PROPERTY;

static PROPERTY PredefinedProperties[] = {

        {"NUMBER_OF_FIELDS", WRITE_UNCOOKED},    // Required - NUMBER OF FIELDS
        {"NUMBER_OF_SETS",   WRITE_UNCOOKED},    // Required - NUMBER OF SETS
        {"ORIGINATOR",       WRITE_STRINGIFY},   // Required - Identifies the specific system, organization or individual that created the data file.
        {"FILE_DESCRIPTOR",  WRITE_STRINGIFY},   // Required - Describes the purpose or contents of the data file.
        {"CREATED",          WRITE_STRINGIFY},   // Required - Indicates date of creation of the data file.
        {"DESCRIPTOR",       WRITE_STRINGIFY},   // Required  - Describes the purpose or contents of the data file.
        {"DIFFUSE_GEOMETRY", WRITE_STRINGIFY},   // The diffuse geometry used. Allowed values are "sphere" or "opal".
        {"MANUFACTURER",     WRITE_STRINGIFY},
        {"MANUFACTURE",      WRITE_STRINGIFY},   // Some broken Fuji targets does store this value
        {"PROD_DATE",        WRITE_STRINGIFY},   // Identifies year and month of production of the target in the form yyyy:mm.
        {"SERIAL",           WRITE_STRINGIFY},   // Uniquely identifies individual physical target.

        {"MATERIAL",         WRITE_STRINGIFY},   // Identifies the material on which the target was produced using a code
                               // uniquely identifying th e material. This is intend ed to be used for IT8.7
                               // physical targets only (i.e . IT8.7/1 a nd IT8.7/2).

        {"INSTRUMENTATION",  WRITE_STRINGIFY},   // Used to report the specific instrumentation used (manufacturer and
                               // model number) to generate the data reported. This data will often
                               // provide more information about the particular data collected than an
                               // extensive list of specific details. This is particularly important for
                               // spectral data or data derived from spectrophotometry.

        {"MEASUREMENT_SOURCE", WRITE_STRINGIFY}, // Illumination used for spectral measurements. This data helps provide
                               // a guide to the potential for issues of paper fluorescence, etc.

        {"PRINT_CONDITIONS", WRITE_STRINGIFY},   // Used to define the characteristics of the printed sheet being reported.
                               // Where standard conditions have been defined (e.g., SWOP at nominal)
                               // named conditions may suffice. Otherwise, detailed information is
                               // needed.

        {"SAMPLE_BACKING",   WRITE_STRINGIFY},   // Identifies the backing material used behind the sample during
                               // measurement. Allowed values are “black”, “white”, or {"na".

        {"CHISQ_DOF",        WRITE_STRINGIFY},   // Degrees of freedom associated with the Chi squared statistic

//    new in recent specs:
        {"MEASUREMENT_GEOMETRY", WRITE_STRINGIFY}, // The type of measurement, either reflection or transmission, should be indicated
                               // along with details of the geometry and the aperture size and shape. For example,
                               // for transmission measurements it is important to identify 0/diffuse, diffuse/0,
                               // opal or integrating sphere, etc. For reflection it is important to identify 0/45,
                               // 45/0, sphere (specular included or excluded), etc.

       {"FILTER",            WRITE_STRINGIFY},   // Identifies the use of physical filter(s) during measurement. Typically used to
                               // denote the use of filters such as none, D65, Red, Green or Blue.

       {"POLARIZATION",      WRITE_STRINGIFY},   // Identifies the use of a physical polarization filter during measurement. Allowed
                               // values are {"yes”, “white”, “none” or “na”.

       {"WEIGHTING_FUNCTION", WRITE_PAIR},   // Indicates such functions as: the CIE standard observer functions used in the
                               // calculation of various data parameters (2 degree and 10 degree), CIE standard
                               // illuminant functions used in the calculation of various data parameters (e.g., D50,
                               // D65, etc.), density status response, etc. If used there shall be at least one
                               // name-value pair following the WEIGHTING_FUNCTION tag/keyword. The first attribute
                               // in the set shall be {"name" and shall identify the particular parameter used.
                               // The second shall be {"value" and shall provide the value associated with that name.
                               // For ASCII data, a string containing the Name and Value attribute pairs shall follow
                               // the weighting function keyword. A semi-colon separates attribute pairs from each
                               // other and within the attribute the name and value are separated by a comma.

       {"COMPUTATIONAL_PARAMETER", WRITE_PAIR}, // Parameter that is used in computing a value from measured data. Name is the name
                               // of the calculation, parameter is the name of the parameter used in the calculation
                               // and value is the value of the parameter.

       {"TARGET_TYPE",        WRITE_STRINGIFY},  // The type of target being measured, e.g. IT8.7/1, IT8.7/3, user defined, etc.

       {"COLORANT",           WRITE_STRINGIFY},  // Identifies the colorant(s) used in creating the target.

       {"TABLE_DESCRIPTOR",   WRITE_STRINGIFY},  // Describes the purpose or contents of a data table.

       {"TABLE_NAME",         WRITE_STRINGIFY}   // Provides a short name for a data table.
};

#define NUMPREDEFINEDPROPS (sizeof(PredefinedProperties)/sizeof(PROPERTY))


// Predefined sample types on dataset
static const char* PredefinedSampleID[] = {
        "SAMPLE_ID",      // Identifies sample that data represents
        "STRING",         // Identifies label, or other non-machine readable value.
                          // Value must begin and end with a " symbol

        "CMYK_C",         // Cyan component of CMYK data expressed as a percentage
        "CMYK_M",         // Magenta component of CMYK data expressed as a percentage
        "CMYK_Y",         // Yellow component of CMYK data expressed as a percentage
        "CMYK_K",         // Black component of CMYK data expressed as a percentage
        "D_RED",          // Red filter density
        "D_GREEN",        // Green filter density
        "D_BLUE",         // Blue filter density
        "D_VIS",          // Visual filter density
        "D_MAJOR_FILTER", // Major filter d ensity
        "RGB_R",          // Red component of RGB data
        "RGB_G",          // Green component of RGB data
        "RGB_B",          // Blue com ponent of RGB data
        "SPECTRAL_NM",    // Wavelength of measurement expressed in nanometers
        "SPECTRAL_PCT",   // Percentage reflectance/transmittance
        "SPECTRAL_DEC",   // Reflectance/transmittance
        "XYZ_X",          // X component of tristimulus data
        "XYZ_Y",          // Y component of tristimulus data
        "XYZ_Z",          // Z component of tristimulus data
        "XYY_X"           // x component of chromaticity data
        "XYY_Y",          // y component of chromaticity data
        "XYY_CAPY",       // Y component of tristimulus data
        "LAB_L",          // L* component of Lab data
        "LAB_A",          // a* component of Lab data
        "LAB_B",          // b* component of Lab data
        "LAB_C",          // C*ab component of Lab data
        "LAB_H",          // hab component of Lab data
        "LAB_DE",         //  CIE dE
        "LAB_DE_94",      //  CIE dE using CIE 94
        "LAB_DE_CMC",     //  dE using CMC
        "LAB_DE_2000",    // CIE dE using CIE DE 2000
        "MEAN_DE",        // Mean Delta E (LAB_DE) of samples compared to batch average
                          // (Used for data files for ANSI IT8.7/1 and IT8.7/2 targets)
        "STDEV_X",        // Standard deviation of X (tristimulus data)
        "STDEV_Y",        // Standard deviation of Y (tristimulus data)
        "STDEV_Z",        // Standard deviation of Z (tristimulus data)
        "STDEV_L",        // Standard deviation of L*
        "STDEV_A",        // Standard deviation of a*
        "STDEV_B",        // Standard deviation of b*
        "STDEV_DE",       // Standard deviation of CIE dE
        "CHI_SQD_PAR"};   // The average of the standard deviations of L*, a* and b*. It is
                          // used to derive an estimate of the chi-squared parameter which is
                          // recommended as the predictor of the variability of dE

#define NUMPREDEFINEDSAMPLEID (sizeof(PredefinedSampleID)/sizeof(char *))

//Forward declaration of some internal functions
static
void* AllocChunk(LPIT8 it8, size_t size);

// Checks if c is a separator
static
LCMSBOOL isseparator(int c)
{
        return (c == ' ') || (c == '\t') || (c == '\r');
}

// Checks whatever if c is a valid identifier char
static
LCMSBOOL ismiddle(int c)
{
   return (!isseparator(c) && (c != '#') && (c !='\"') && (c != '\'') && (c > 32) && (c < 127));
}

// Checks whatsever if c is a valid identifier middle char.
static
LCMSBOOL isidchar(int c)
{
   return isalnum(c) || ismiddle(c);
}

// Checks whatsever if c is a valid identifier first char.
static
LCMSBOOL isfirstidchar(int c)
{
     return !isdigit(c) && ismiddle(c);
}

// checks whether the supplied path looks like an absolute path
// NOTE: this function doesn't checks if the path exists or even if it's legal
static
LCMSBOOL isabsolutepath(const char *path)
{
    if(path == NULL)
        return FALSE;

    if(path[0] == DIR_CHAR)
        return TRUE;

#ifndef NON_WINDOWS
    if(isalpha(path[0]) && path[1] == ':')
        return TRUE;
#endif
    return FALSE;
}

// Makes a file path based on a given reference path
// NOTE: buffer is assumed to point to at least MAX_PATH bytes
// NOTE: both relPath and basePath are assumed to be no more than MAX_PATH characters long (including the null terminator!)
// NOTE: this function doesn't check if the path exists or even if it's legal
static
LCMSBOOL _cmsMakePath(const char *relPath, const char *basePath, char *buffer)
{
    if (!isabsolutepath(relPath)) {

        char *tail;

        strncpy(buffer, basePath, MAX_PATH-1);
        tail = strrchr(buffer, DIR_CHAR);
        if (tail != NULL) {

            size_t len = tail - buffer;
            strncpy(tail + 1, relPath, MAX_PATH - len -1);
            //  TODO: if combined path is longer than MAX_PATH, this should return FALSE!
            return TRUE;
        }
    }
    strncpy(buffer, relPath, MAX_PATH - 1);
        buffer[MAX_PATH-1] = 0;
    return TRUE;
}


// Make sure no exploit is being even tried

static
const char* NoMeta(const char* str)
{
    if (strchr(str, '%') != NULL)
        return "**** CORRUPTED FORMAT STRING ***";

    return str;
}


// Syntax error
static
LCMSBOOL SynError(LPIT8 it8, const char *Txt, ...)
{
        char Buffer[256], ErrMsg[1024];
        va_list args;

        va_start(args, Txt);
        vsnprintf(Buffer, 255, Txt, args);
        Buffer[255] = 0;
        va_end(args);

        snprintf(ErrMsg, 1023, "%s: Line %d, %s", it8->FileStack[it8 ->IncludeSP]->FileName, it8->lineno, Buffer);
        ErrMsg[1023] = 0;
        it8->sy = SSYNERROR;
        cmsSignalError(LCMS_ERRC_ABORTED, "%s", ErrMsg);
        return FALSE;
}

// Check if current symbol is same as specified. issue an error else.
static
LCMSBOOL Check(LPIT8 it8, SYMBOL sy, const char* Err)
{
        if (it8 -> sy != sy)
                return SynError(it8, NoMeta(Err));
        return TRUE;
}



// Read Next character from stream
static
void NextCh(LPIT8 it8)
{
    if (it8 -> FileStack[it8 ->IncludeSP]->Stream) {

        it8 ->ch = fgetc(it8 ->FileStack[it8 ->IncludeSP]->Stream);

        if (feof(it8 -> FileStack[it8 ->IncludeSP]->Stream))  {

            if (it8 ->IncludeSP > 0) {

                fclose(it8 ->FileStack[it8->IncludeSP--]->Stream);
                it8 -> ch = ' ';                            // Whitespace to be ignored

            } else
                it8 ->ch = 0;   // EOF
        }



    }
    else {
        it8->ch = *it8->Source;
        if (it8->ch) it8->Source++;
    }
}


// Try to see if current identifier is a keyword, if so return the referred symbol
static
SYMBOL BinSrchKey(const char *id)
{
        int l = 1;
        int r = NUMKEYS;
        int x, res;

        while (r >= l)
        {
                x = (l+r)/2;
                res = stricmp(id, TabKeys[x-1].id);
                if (res == 0) return TabKeys[x-1].sy;
                if (res < 0) r = x - 1;
                else l = x + 1;
        }

        return SNONE;
}


// 10 ^n
static
double xpow10(int n)
{
    return pow(10, (double) n);
}


//  Reads a Real number, tries to follow from integer number
static
void ReadReal(LPIT8 it8, int inum)
{
        it8->dnum = (double) inum;

        while (isdigit(it8->ch)) {

        it8->dnum = it8->dnum * 10.0 + (it8->ch - '0');
        NextCh(it8);
        }

        if (it8->ch == '.') {        // Decimal point

                double frac = 0.0;      // fraction
                int prec = 0;           // precission

                NextCh(it8);               // Eats dec. point

                while (isdigit(it8->ch)) {

                        frac = frac * 10.0 + (it8->ch - '0');
                        prec++;
                        NextCh(it8);
                }

                it8->dnum = it8->dnum + (frac / xpow10(prec));
        }

        // Exponent, example 34.00E+20
        if (toupper(it8->ch) == 'E') {

                int e;
                int sgn;

                NextCh(it8); sgn = 1;

                if (it8->ch == '-') {

                        sgn = -1; NextCh(it8);
                }
                else
                if (it8->ch == '+') {

                        sgn = +1;
                        NextCh(it8);
                }


                e = 0;
                while (isdigit(it8->ch)) {

                        if ((double) e * 10L < INT_MAX)
                            e = e * 10 + (it8->ch - '0');

                        NextCh(it8);
                }

                e = sgn*e;

                it8 -> dnum = it8 -> dnum * xpow10(e);
        }
}



// Reads next symbol
static
void InSymbol(LPIT8 it8)
{
    register char *idptr;
    register int k;
    SYMBOL key;
    int sng;

    do {

        while (isseparator(it8->ch))
            NextCh(it8);

        if (isfirstidchar(it8->ch)) {          // Identifier


            k = 0;
            idptr = it8->id;

            do {

                if (++k < MAXID) *idptr++ = (char) it8->ch;

                NextCh(it8);

            } while (isidchar(it8->ch));

            *idptr = '\0';


            key = BinSrchKey(it8->id);
            if (key == SNONE) it8->sy = SIDENT;
            else it8->sy = key;

        }
        else                         // Is a number?
            if (isdigit(it8->ch) || it8->ch == '.' || it8->ch == '-' || it8->ch == '+')
            {
                int sign = 1;

                if (it8->ch == '-') {
                    sign = -1;
                    NextCh(it8);
                }

                it8->inum = 0;
                it8->sy   = SINUM;

                if (it8->ch == '0') {          // 0xnnnn (Hexa) or 0bnnnn (Binary)

                    NextCh(it8);
                    if (toupper(it8->ch) == 'X') {

                        int j;

                        NextCh(it8);
                        while (isxdigit(it8->ch))
                        {
                            it8->ch = toupper(it8->ch);
                            if (it8->ch >= 'A' && it8->ch <= 'F')  j = it8->ch -'A'+10;
                            else j = it8->ch - '0';

                            if ((long) it8->inum * 16L > (long) INT_MAX)
                            {
                                SynError(it8, "Invalid hexadecimal number");
                                return;
                            }

                            it8->inum = it8->inum * 16 + j;
                            NextCh(it8);
                        }
                        return;
                    }

                    if (toupper(it8->ch) == 'B') {  // Binary

                        int j;

                        NextCh(it8);
                        while (it8->ch == '0' || it8->ch == '1')
                        {
                            j = it8->ch - '0';

                            if ((long) it8->inum * 2L > (long) INT_MAX)
                            {
                                SynError(it8, "Invalid binary number");
                                return;
                            }

                            it8->inum = it8->inum * 2 + j;
                            NextCh(it8);
                        }
                        return;
                    }
                }


                while (isdigit(it8->ch)) {

                    if ((long) it8->inum * 10L > (long) INT_MAX) {
                        ReadReal(it8, it8->inum);
                        it8->sy = SDNUM;
                        it8->dnum *= sign;
                        return;
                    }

                    it8->inum = it8->inum * 10 + (it8->ch - '0');
                    NextCh(it8);
                }

                if (it8->ch == '.') {

                    ReadReal(it8, it8->inum);
                    it8->sy = SDNUM;
                    it8->dnum *= sign;
                    return;
                }

                it8 -> inum *= sign;

                // Special case. Numbers followed by letters are taken as identifiers

                if (isidchar(it8 ->ch)) {

                    if (it8 ->sy == SINUM) {

                        sprintf(it8->id, "%d", it8->inum);
                    }
                    else {

                        sprintf(it8->id, it8 ->DoubleFormatter, it8->dnum);
                    }

                    k = (int) strlen(it8 ->id);
                    idptr = it8 ->id + k;
                    do {

                        if (++k < MAXID) *idptr++ = (char) it8->ch;

                        NextCh(it8);

                    } while (isidchar(it8->ch));

                    *idptr = '\0';

                    it8->sy = SIDENT;
                }
                return;

            }
            else
                switch ((int) it8->ch) {

        // EOF marker -- ignore it
        case '\x1a':
            NextCh(it8);
            break;

        // Eof stream markers

        case 0:
        case -1:
            it8->sy = SEOF;
            break;


        // Next line

        case '\n':
            NextCh(it8);
            it8->sy = SEOLN;
            it8->lineno++;
            break;

        // Comment

        case '#':
            NextCh(it8);
            while (it8->ch && it8->ch != '\n')
                NextCh(it8);

            it8->sy = SCOMMENT;
            break;

            // String.

        case '\'':
        case '\"':
            idptr = it8->str;
            sng = it8->ch;
            k = 0;
            NextCh(it8);

            while (k < MAXSTR && it8->ch != sng) {

                if (it8->ch == '\n'|| it8->ch == '\r') k = MAXSTR+1;
                else {
                    *idptr++ = (char) it8->ch;
                    NextCh(it8);
                    k++;
                }
            }

            it8->sy = SSTRING;
            *idptr = '\0';
            NextCh(it8);
            break;


        default:
            SynError(it8, "Unrecognized character: 0x%x", it8 ->ch);
            return;
            }

    } while (it8->sy == SCOMMENT);

    // Handle the include special token

    if (it8 -> sy == SINCLUDE) {

                LPFILECTX FileNest;

                if(it8 -> IncludeSP >= (MAXINCLUDE-1))
                {
                    SynError(it8, "Too many recursion levels");
                    return;
                }

                InSymbol(it8);
                if (!Check(it8, SSTRING, "Filename expected")) return;

                FileNest = it8 -> FileStack[it8 -> IncludeSP + 1];
                if(FileNest == NULL)
                {
                    FileNest = it8 ->FileStack[it8 -> IncludeSP + 1] = (LPFILECTX)AllocChunk(it8, sizeof(FILECTX));
                    //if(FileNest == NULL)
                        //  TODO: how to manage out-of-memory conditions?
                }

                if(_cmsMakePath(it8->str, it8->FileStack[it8->IncludeSP]->FileName, FileNest->FileName) == FALSE)
                {
                    SynError(it8, "File path too long");
                    return;
                }

                FileNest->Stream = fopen(FileNest->FileName, "rt");
                if (FileNest->Stream == NULL) {

                        SynError(it8, "File %s not found", FileNest->FileName);
                        return;
                }
                it8->IncludeSP++;

                it8 ->ch = ' ';
                InSymbol(it8);
    }

}

// Checks end of line separator
static
LCMSBOOL CheckEOLN(LPIT8 it8)
{
        if (!Check(it8, SEOLN, "Expected separator")) return FALSE;
        while (it8 -> sy == SEOLN)
                        InSymbol(it8);
        return TRUE;

}

// Skip a symbol

static
void Skip(LPIT8 it8, SYMBOL sy)
{
        if (it8->sy == sy && it8->sy != SEOF)
                        InSymbol(it8);
}


// Skip multiple EOLN
static
void SkipEOLN(LPIT8 it8)
{
    while (it8->sy == SEOLN) {
             InSymbol(it8);
    }
}


// Returns a string holding current value
static
LCMSBOOL GetVal(LPIT8 it8, char* Buffer, size_t max, const char* ErrorTitle)
{
    switch (it8->sy) {

    case SIDENT:  strncpy(Buffer, it8->id, max);
                  Buffer[max-1]=0;
                  break;
    case SINUM:   snprintf(Buffer, max, "%d", it8 -> inum); break;
    case SDNUM:   snprintf(Buffer, max, it8->DoubleFormatter, it8 -> dnum); break;
    case SSTRING: strncpy(Buffer, it8->str, max);
                  Buffer[max-1] = 0;
                  break;


    default:
         return SynError(it8, "%s", ErrorTitle);
    }

    Buffer[max] = 0;
    return TRUE;
}

// ---------------------------------------------------------- Table

static
LPTABLE GetTable(LPIT8 it8)
{
   if ((it8 -> nTable >= it8 ->TablesCount) || (it8 -> nTable < 0)) {

           SynError(it8, "Table %d out of sequence", it8 -> nTable);
           return it8 -> Tab;
   }

   return it8 ->Tab + it8 ->nTable;
}

// ---------------------------------------------------------- Memory management



// Frees an allocator and owned memory
void LCMSEXPORT cmsIT8Free(LCMSHANDLE hIT8)
{
   LPIT8 it8 = (LPIT8) hIT8;

    if (it8 == NULL)
        return;


    if (it8->MemorySink) {

        LPOWNEDMEM p;
        LPOWNEDMEM n;

        for (p = it8->MemorySink; p != NULL; p = n) {

            n = p->Next;
            if (p->Ptr) _cmsFree(p->Ptr);
            _cmsFree(p);
        }
    }

    if (it8->MemoryBlock)
        _cmsFree(it8->MemoryBlock);

     _cmsFree(it8);
}


// Allocates a chunk of data, keep linked list
static
void* AllocBigBlock(LPIT8 it8, size_t size)
{
   LPOWNEDMEM ptr1;
   void* ptr = _cmsMalloc(size);

        if (ptr) {

                ZeroMemory(ptr, size);
                ptr1 = (LPOWNEDMEM) _cmsMalloc(sizeof(OWNEDMEM));

                if (ptr1 == NULL) {

                     _cmsFree(ptr);
                    return NULL;
                }

                ZeroMemory(ptr1, sizeof(OWNEDMEM));

                ptr1-> Ptr        = ptr;
                ptr1-> Next       = it8 -> MemorySink;
                it8 -> MemorySink = ptr1;
        }

        return ptr;
}


// Suballocator.
static
void* AllocChunk(LPIT8 it8, size_t size)
{
    size_t free = it8 ->Allocator.BlockSize - it8 ->Allocator.Used;
    LPBYTE ptr;

    size = ALIGNLONG(size);

    if (size > free) {

        if (it8 -> Allocator.BlockSize == 0)

                it8 -> Allocator.BlockSize = 20*1024;
        else
                it8 ->Allocator.BlockSize *= 2;

        if (it8 ->Allocator.BlockSize < size)
                it8 ->Allocator.BlockSize = size;

        it8 ->Allocator.Used = 0;
        it8 ->Allocator.Block = (LPBYTE) AllocBigBlock(it8, it8 ->Allocator.BlockSize);
    }

    ptr = it8 ->Allocator.Block + it8 ->Allocator.Used;
    it8 ->Allocator.Used += size;

    return (void*) ptr;

}


// Allocates a string
static
char *AllocString(LPIT8 it8, const char* str)
{
    size_t Size = strlen(str)+1;
    char *ptr;


    ptr = (char *) AllocChunk(it8, Size);
    if (ptr) strncpy (ptr, str, Size-1);

    return ptr;
}

// Searches through linked list

static
LCMSBOOL IsAvailableOnList(LPKEYVALUE p, const char* Key, const char* Subkey, LPKEYVALUE* LastPtr)
{
    if (LastPtr) *LastPtr = p;

    for (;  p != NULL; p = p->Next) {

        if (LastPtr) *LastPtr = p;

        if (*Key != '#') { // Comments are ignored

            if (stricmp(Key, p->Keyword) == 0)
                    break;
        }
        }

    if (p == NULL)
        return FALSE;

    if (Subkey == 0)
        return TRUE;

    for (; p != NULL; p = p->NextSubkey) {

        if (LastPtr) *LastPtr = p;

        if (stricmp(Subkey, p->Subkey) == 0)
            return TRUE;
    }

    return FALSE;
}



// Add a property into a linked list
static
LPKEYVALUE AddToList(LPIT8 it8, LPKEYVALUE* Head, const char *Key, const char *Subkey, const char* xValue, WRITEMODE WriteAs)
{
    LPKEYVALUE p;

    // Check if property is already in list (this is an error)

    if (IsAvailableOnList(*Head, Key, Subkey, &p)) {

            // This may work for editing properties

        //     return SynError(it8, "duplicate key <%s>", Key);
    }
    else {
        LPKEYVALUE last = p;

    // Allocate the container
    p = (LPKEYVALUE) AllocChunk(it8, sizeof(KEYVALUE));
    if (p == NULL)
    {
            SynError(it8, "AddToList: out of memory");
            return NULL;
    }

    // Store name and value
    p->Keyword = AllocString(it8, Key);
        p->Subkey = (Subkey == NULL) ? NULL : AllocString(it8, Subkey);

        // Keep the container in our list
        if (*Head == NULL)
            *Head = p;
        else
        {
            if(Subkey != 0 && last != 0) {
                last->NextSubkey = p;

                // If Subkey is not null, then last is the last property with the same key,
                // but not necessarily is the last property in the list, so we need to move
                // to the actual list end
                while(last->Next != 0)
                    last = last->Next;
    }
            last->Next = p;
    }

    p->Next    = NULL;
        p->NextSubkey = NULL;
    }

    p->WriteAs = WriteAs;
    if (xValue != NULL) {

        p->Value   = AllocString(it8, xValue);
    }
    else {
        p->Value   = NULL;
    }

    return p;
}

static
LPKEYVALUE AddAvailableProperty(LPIT8 it8, const char* Key, WRITEMODE as)
{
        return AddToList(it8, &it8->ValidKeywords, Key, NULL, NULL, as);
}


static
LPKEYVALUE AddAvailableSampleID(LPIT8 it8, const char* Key)
{
        return AddToList(it8, &it8->ValidSampleID, Key, NULL, NULL, WRITE_UNCOOKED);
}


static
void AllocTable(LPIT8 it8)
{
    LPTABLE t;

    t = it8 ->Tab + it8 ->TablesCount;

    t->HeaderList = NULL;
    t->DataFormat = NULL;
    t->Data       = NULL;

    it8 ->TablesCount++;
}


int LCMSEXPORT cmsIT8SetTable(LCMSHANDLE IT8, int nTable)
{
     LPIT8 it8 = (LPIT8) IT8;

     if (nTable >= it8 ->TablesCount) {

         if (nTable == it8 ->TablesCount) {

             AllocTable(it8);
         }
         else {
             SynError(it8, "Table %d is out of sequence", nTable);
             return -1;
         }
     }

     it8 ->nTable = nTable;

     return nTable;
}



// Init an empty container
LCMSHANDLE LCMSEXPORT cmsIT8Alloc(void)
{
    LPIT8 it8;
    int i;

    it8 = (LPIT8) malloc(sizeof(IT8));
    if (it8 == NULL) return NULL;

    ZeroMemory(it8, sizeof(IT8));

    AllocTable(it8);

    it8->MemoryBlock = NULL;
    it8->MemorySink  = NULL;

    it8 ->nTable = 0;

    it8->Allocator.Used = 0;
    it8->Allocator.Block = NULL;
    it8->Allocator.BlockSize = 0;

    it8->ValidKeywords = NULL;
    it8->ValidSampleID = NULL;

    it8 -> sy = SNONE;
    it8 -> ch = ' ';
    it8 -> Source = NULL;
    it8 -> inum = 0;
    it8 -> dnum = 0.0;

    it8->FileStack[0] = (LPFILECTX)AllocChunk(it8, sizeof(FILECTX));
    it8->IncludeSP   = 0;
    it8 -> lineno = 1;

    strcpy(it8->DoubleFormatter, DEFAULT_DBL_FORMAT);
    strcpy(it8->SheetType, "CGATS.17");

    // Initialize predefined properties & data

    for (i=0; i < NUMPREDEFINEDPROPS; i++)
            AddAvailableProperty(it8, PredefinedProperties[i].id, PredefinedProperties[i].as);

    for (i=0; i < NUMPREDEFINEDSAMPLEID; i++)
            AddAvailableSampleID(it8, PredefinedSampleID[i]);


   return (LCMSHANDLE) it8;
}


const char* LCMSEXPORT cmsIT8GetSheetType(LCMSHANDLE hIT8)
{
        LPIT8 it8 = (LPIT8) hIT8;

        return it8 ->SheetType;

}

LCMSBOOL  LCMSEXPORT cmsIT8SetSheetType(LCMSHANDLE hIT8, const char* Type)
{
        LPIT8 it8 = (LPIT8) hIT8;

        strncpy(it8 ->SheetType, Type, MAXSTR-1);
        it8 ->SheetType[MAXSTR-1] = 0;
        return TRUE;
}

LCMSBOOL LCMSEXPORT cmsIT8SetComment(LCMSHANDLE hIT8, const char* Val)
{
    LPIT8 it8 = (LPIT8) hIT8;

    if (!Val) return FALSE;
    if (!*Val) return FALSE;

    return AddToList(it8, &GetTable(it8)->HeaderList, "# ", NULL, Val, WRITE_UNCOOKED) != NULL;
}



// Sets a property
LCMSBOOL LCMSEXPORT cmsIT8SetPropertyStr(LCMSHANDLE hIT8, const char* Key, const char *Val)
{
    LPIT8 it8 = (LPIT8) hIT8;

    if (!Val) return FALSE;
    if (!*Val) return FALSE;

    return AddToList(it8, &GetTable(it8)->HeaderList, Key, NULL, Val, WRITE_STRINGIFY) != NULL;
}


LCMSBOOL LCMSEXPORT cmsIT8SetPropertyDbl(LCMSHANDLE hIT8, const char* cProp, double Val)
{
    LPIT8 it8 = (LPIT8) hIT8;
    char Buffer[1024];

    sprintf(Buffer, it8->DoubleFormatter, Val);

    return AddToList(it8, &GetTable(it8)->HeaderList, cProp, NULL, Buffer, WRITE_UNCOOKED) != NULL;
}

LCMSBOOL LCMSEXPORT cmsIT8SetPropertyHex(LCMSHANDLE hIT8, const char* cProp, int Val)
{
    LPIT8 it8 = (LPIT8) hIT8;
    char Buffer[1024];

    sprintf(Buffer, "%d", Val);

    return AddToList(it8, &GetTable(it8)->HeaderList, cProp, NULL, Buffer, WRITE_HEXADECIMAL) != NULL;
}

LCMSBOOL LCMSEXPORT cmsIT8SetPropertyUncooked(LCMSHANDLE hIT8, const char* Key, const char* Buffer)
{
    LPIT8 it8 = (LPIT8) hIT8;

    return AddToList(it8, &GetTable(it8)->HeaderList, Key, NULL, Buffer, WRITE_UNCOOKED) != NULL;
}

LCMSBOOL LCMSEXPORT cmsIT8SetPropertyMulti(LCMSHANDLE hIT8, const char* Key, const char* SubKey, const char *Buffer)
{
    LPIT8 it8 = (LPIT8) hIT8;

    return AddToList(it8, &GetTable(it8)->HeaderList, Key, SubKey, Buffer, WRITE_PAIR) != NULL;
}

// Gets a property
const char* LCMSEXPORT cmsIT8GetProperty(LCMSHANDLE hIT8, const char* Key)
{
    LPIT8 it8 = (LPIT8) hIT8;
    LPKEYVALUE p;

    if (IsAvailableOnList(GetTable(it8) -> HeaderList, Key, NULL, &p))
    {
        return p -> Value;
    }
    return NULL;
}


double LCMSEXPORT cmsIT8GetPropertyDbl(LCMSHANDLE hIT8, const char* cProp)
{
    const char *v = cmsIT8GetProperty(hIT8, cProp);

    if (v) return atof(v);
    else return 0.0;
}

const char* LCMSEXPORT cmsIT8GetPropertyMulti(LCMSHANDLE hIT8, const char* Key, const char *SubKey)
{
    LPIT8 it8 = (LPIT8) hIT8;
    LPKEYVALUE p;

    if (IsAvailableOnList(GetTable(it8) -> HeaderList, Key, SubKey, &p))
    {
        return p -> Value;
    }
    return NULL;
}

// ----------------------------------------------------------------- Datasets


static
void AllocateDataFormat(LPIT8 it8)
{
    LPTABLE t = GetTable(it8);

    if (t -> DataFormat) return;    // Already allocated

    t -> nSamples  = (int) cmsIT8GetPropertyDbl(it8, "NUMBER_OF_FIELDS");

    if (t -> nSamples <= 0) {

        SynError(it8, "AllocateDataFormat: Unknown NUMBER_OF_FIELDS");
        t -> nSamples = 10;
        }

    t -> DataFormat = (char**) AllocChunk (it8, (t->nSamples + 1) * sizeof(char *));
    if (t->DataFormat == NULL)
    {
        SynError(it8, "AllocateDataFormat: Unable to allocate dataFormat array");
    }

}

static
const char *GetDataFormat(LPIT8 it8, int n)
{
    LPTABLE t = GetTable(it8);

    if (t->DataFormat)
        return t->DataFormat[n];

    return NULL;
}

static
LCMSBOOL SetDataFormat(LPIT8 it8, int n, const char *label)
{
    LPTABLE t = GetTable(it8);

#ifdef  STRICT_CGATS
    if (!IsAvailableOnList(it8-> ValidSampleID, label, NULL, NULL)) {
        SynError(it8, "Invalid data format '%s'.", label);
        return FALSE;
    }
#endif

    if (!t->DataFormat)
        AllocateDataFormat(it8);

    if (n > t -> nSamples) {
        SynError(it8, "More than NUMBER_OF_FIELDS fields.");
        return FALSE;
    }


    if (t->DataFormat) {
        t->DataFormat[n] = AllocString(it8, label);
    }

    return TRUE;
}


LCMSBOOL LCMSEXPORT cmsIT8SetDataFormat(LCMSHANDLE h, int n, const char *Sample)
{
        LPIT8 it8 = (LPIT8) h;
        return SetDataFormat(it8, n, Sample);
}

static
void AllocateDataSet(LPIT8 it8)
{
    LPTABLE t = GetTable(it8);

    if (t -> Data) return;    // Already allocated

    t-> nSamples   = atoi(cmsIT8GetProperty(it8, "NUMBER_OF_FIELDS"));
    t-> nPatches   = atoi(cmsIT8GetProperty(it8, "NUMBER_OF_SETS"));

    t-> Data = (char**)AllocChunk (it8, (t->nSamples + 1) * (t->nPatches + 1) *sizeof (char*));
    if (t->Data == NULL)
    {
        SynError(it8, "AllocateDataSet: Unable to allocate data array");
    }

}

static
char* GetData(LPIT8 it8, int nSet, int nField)
{
    LPTABLE t = GetTable(it8);
    int  nSamples   = t -> nSamples;
    int  nPatches   = t -> nPatches;


    if (nSet >= nPatches || nField >= nSamples)
        return NULL;

    if (!t->Data) return NULL;
    return t->Data [nSet * nSamples + nField];
}

static
LCMSBOOL SetData(LPIT8 it8, int nSet, int nField, const char *Val)
{
    LPTABLE t = GetTable(it8);

    if (!t->Data)
        AllocateDataSet(it8);

    if (!t->Data) return FALSE;



    if (nSet > t -> nPatches || nSet < 0) {

            return SynError(it8, "Patch %d out of range, there are %d patches", nSet, t -> nPatches);
    }

    if (nField > t ->nSamples || nField < 0) {
            return SynError(it8, "Sample %d out of range, there are %d samples", nField, t ->nSamples);

    }


    t->Data [nSet * t -> nSamples + nField] = AllocString(it8, Val);
    return TRUE;
}


// --------------------------------------------------------------- File I/O


// Writes a string to file
static
void WriteStr(LPSAVESTREAM f, const char *str)
{

    size_t len;

    if (str == NULL)
        str = " ";

    // Lenghth to write
    len = strlen(str);
    f ->Used += len;


    if (f ->stream) {   // Should I write it to a file?

        fwrite(str, 1, len, f->stream);

    }
    else {  // Or to a memory block?


        if (f ->Base) {   // Am I just counting the bytes?

            if (f ->Used > f ->Max) {

                cmsSignalError(LCMS_ERRC_ABORTED, "Write to memory overflows in CGATS parser");
                return;
            }

            CopyMemory(f ->Ptr, str, len);
            f->Ptr += len;

        }

    }
}


// Write formatted

static
void Writef(LPSAVESTREAM f, const char* frm, ...)
{
    char Buffer[4096];
    va_list args;

    va_start(args, frm);
    vsnprintf(Buffer, 4095, frm, args);
    Buffer[4095] = 0;
    WriteStr(f, Buffer);
    va_end(args);

}

// Writes full header
static
void WriteHeader(LPIT8 it8, LPSAVESTREAM fp)
{
    LPKEYVALUE p;
    LPTABLE t = GetTable(it8);


    for (p = t->HeaderList; (p != NULL); p = p->Next)
    {
        if (*p ->Keyword == '#') {

            char* Pt;

            WriteStr(fp, "#\n# ");
            for (Pt = p ->Value; *Pt; Pt++) {


                Writef(fp, "%c", *Pt);

                if (*Pt == '\n') {
                    WriteStr(fp, "# ");
                }
            }

            WriteStr(fp, "\n#\n");
            continue;
        }


        if (!IsAvailableOnList(it8-> ValidKeywords, p->Keyword, NULL, NULL)) {

#ifdef STRICT_CGATS
            WriteStr(fp, "KEYWORD\t\"");
            WriteStr(fp, p->Keyword);
            WriteStr(fp, "\"\n");
#endif

            AddAvailableProperty(it8, p->Keyword, WRITE_UNCOOKED);

        }

        WriteStr(fp, p->Keyword);
        if (p->Value) {

            switch (p ->WriteAs) {

            case WRITE_UNCOOKED:
                    Writef(fp, "\t%s", p ->Value);
                    break;

            case WRITE_STRINGIFY:
                    Writef(fp, "\t\"%s\"", p->Value );
                    break;

            case WRITE_HEXADECIMAL:
                    Writef(fp, "\t0x%X", atoi(p ->Value));
                    break;

            case WRITE_BINARY:
                    Writef(fp, "\t0x%B", atoi(p ->Value));
                    break;

            case WRITE_PAIR:
                    Writef(fp, "\t\"%s,%s\"", p->Subkey, p->Value);
                    break;

            default: SynError(it8, "Unknown write mode %d", p ->WriteAs);
                     return;
            }
        }

        WriteStr (fp, "\n");
    }

}


// Writes the data format
static
void WriteDataFormat(LPSAVESTREAM fp, LPIT8 it8)
{
    int i, nSamples;
    LPTABLE t = GetTable(it8);

    if (!t -> DataFormat) return;

       WriteStr(fp, "BEGIN_DATA_FORMAT\n");
       WriteStr(fp, " ");
       nSamples = atoi(cmsIT8GetProperty(it8, "NUMBER_OF_FIELDS"));

       for (i = 0; i < nSamples; i++) {

              WriteStr(fp, t->DataFormat[i]);
              WriteStr(fp, ((i == (nSamples-1)) ? "\n" : "\t"));
          }

       WriteStr (fp, "END_DATA_FORMAT\n");
}


// Writes data array
static
void WriteData(LPSAVESTREAM fp, LPIT8 it8)
{
       int  i, j;
       LPTABLE t = GetTable(it8);

       if (!t->Data) return;

       WriteStr (fp, "BEGIN_DATA\n");

       t->nPatches = atoi(cmsIT8GetProperty(it8, "NUMBER_OF_SETS"));

       for (i = 0; i < t-> nPatches; i++) {

              WriteStr(fp, " ");

              for (j = 0; j < t->nSamples; j++) {

                     char *ptr = t->Data[i*t->nSamples+j];

                     if (ptr == NULL) WriteStr(fp, "\"\"");
                     else {
                         // If value contains whitespace, enclose within quote

                         if (strchr(ptr, ' ') != NULL) {

                             WriteStr(fp, "\"");
                             WriteStr(fp, ptr);
                             WriteStr(fp, "\"");
                         }
                         else
                            WriteStr(fp, ptr);
                     }

                     WriteStr(fp, ((j == (t->nSamples-1)) ? "\n" : "\t"));
              }
       }
       WriteStr (fp, "END_DATA\n");
}



// Saves whole file
LCMSBOOL LCMSEXPORT cmsIT8SaveToFile(LCMSHANDLE hIT8, const char* cFileName)
{
    SAVESTREAM sd;
    int i;
    LPIT8 it8 = (LPIT8) hIT8;

    ZeroMemory(&sd, sizeof(SAVESTREAM));

    sd.stream = fopen(cFileName, "wt");
    if (!sd.stream) return FALSE;

    WriteStr(&sd, it8->SheetType);
    WriteStr(&sd, "\n");
    for (i=0; i < it8 ->TablesCount; i++) {

            cmsIT8SetTable(hIT8, i);
            WriteHeader(it8, &sd);
            WriteDataFormat(&sd, it8);
            WriteData(&sd, it8);
    }

    fclose(sd.stream);

    return TRUE;
}


// Saves to memory
LCMSBOOL LCMSEXPORT cmsIT8SaveToMem(LCMSHANDLE hIT8, void *MemPtr, size_t* BytesNeeded)
{
    SAVESTREAM sd;
    int i;
    LPIT8 it8 = (LPIT8) hIT8;

    ZeroMemory(&sd, sizeof(SAVESTREAM));

    sd.stream = NULL;
    sd.Base   = (LPBYTE) MemPtr;
    sd.Ptr    = sd.Base;

    sd.Used = 0;

    if (sd.Base)
        sd.Max  = *BytesNeeded;     // Write to memory?
    else
        sd.Max  = 0;                // Just counting the needed bytes

    WriteStr(&sd, it8->SheetType);
    WriteStr(&sd, "\n");
    for (i=0; i < it8 ->TablesCount; i++) {

            cmsIT8SetTable(hIT8, i);
            WriteHeader(it8, &sd);
            WriteDataFormat(&sd, it8);
            WriteData(&sd, it8);
    }

    sd.Used++;  // The \0 at the very end

    if (sd.Base)
        sd.Ptr = 0;

    *BytesNeeded = sd.Used;

    return TRUE;
}


// -------------------------------------------------------------- Higer level parsing

static
LCMSBOOL DataFormatSection(LPIT8 it8)
{
    int iField = 0;
    LPTABLE t = GetTable(it8);

    InSymbol(it8);   // Eats "BEGIN_DATA_FORMAT"
    CheckEOLN(it8);

    while (it8->sy != SEND_DATA_FORMAT &&
        it8->sy != SEOLN &&
        it8->sy != SEOF &&
        it8->sy != SSYNERROR)  {

            if (it8->sy != SIDENT) {

                return SynError(it8, "Sample type expected");
            }

            if (!SetDataFormat(it8, iField, it8->id)) return FALSE;
            iField++;

            InSymbol(it8);
            SkipEOLN(it8);
       }

       SkipEOLN(it8);
       Skip(it8, SEND_DATA_FORMAT);
       SkipEOLN(it8);

       if (iField != t ->nSamples) {
           SynError(it8, "Count mismatch. NUMBER_OF_FIELDS was %d, found %d\n", t ->nSamples, iField);


       }

       return TRUE;
}



static
LCMSBOOL DataSection (LPIT8 it8)
{
    int  iField = 0;
    int  iSet   = 0;
    char Buffer[MAXSTR];
    LPTABLE t = GetTable(it8);

    InSymbol(it8);   // Eats "BEGIN_DATA"
    CheckEOLN(it8);

    if (!t->Data)
        AllocateDataSet(it8);

    while (it8->sy != SEND_DATA && it8->sy != SEOF)
    {
        if (iField >= t -> nSamples) {
            iField = 0;
            iSet++;

        }

        if (it8->sy != SEND_DATA && it8->sy != SEOF) {

            if (!GetVal(it8, Buffer, 255, "Sample data expected"))
                return FALSE;

            if (!SetData(it8, iSet, iField, Buffer))
                return FALSE;

            iField++;

            InSymbol(it8);
            SkipEOLN(it8);
        }
    }

    SkipEOLN(it8);
    Skip(it8, SEND_DATA);
    SkipEOLN(it8);

    // Check for data completion.

    if ((iSet+1) != t -> nPatches)
        return SynError(it8, "Count mismatch. NUMBER_OF_SETS was %d, found %d\n", t ->nPatches, iSet+1);

    return TRUE;
}




static
LCMSBOOL HeaderSection(LPIT8 it8)
{
    char VarName[MAXID];
    char Buffer[MAXSTR];
    LPKEYVALUE Key;

        while (it8->sy != SEOF &&
               it8->sy != SSYNERROR &&
               it8->sy != SBEGIN_DATA_FORMAT &&
               it8->sy != SBEGIN_DATA) {


        switch (it8 -> sy) {

        case SKEYWORD:
                InSymbol(it8);
                if (!GetVal(it8, Buffer, MAXSTR-1, "Keyword expected")) return FALSE;
                if (!AddAvailableProperty(it8, Buffer, WRITE_UNCOOKED)) return FALSE;
                InSymbol(it8);
                break;


        case SDATA_FORMAT_ID:
                InSymbol(it8);
                if (!GetVal(it8, Buffer, MAXSTR-1, "Keyword expected")) return FALSE;
                if (!AddAvailableSampleID(it8, Buffer)) return FALSE;
                InSymbol(it8);
                break;


        case SIDENT:
                strncpy(VarName, it8->id, MAXID-1);
                VarName[MAXID-1] = 0;

                if (!IsAvailableOnList(it8-> ValidKeywords, VarName, NULL, &Key)) {

#ifdef STRICT_CGATS
                 return SynError(it8, "Undefined keyword '%s'", VarName);
#else
                    Key = AddAvailableProperty(it8, VarName, WRITE_UNCOOKED);
                    if (Key == NULL) return FALSE;
#endif
                }

                InSymbol(it8);
                if (!GetVal(it8, Buffer, MAXSTR-1, "Property data expected")) return FALSE;

                if(Key->WriteAs != WRITE_PAIR) {
                    AddToList(it8, &GetTable(it8)->HeaderList, VarName, NULL, Buffer,
                                (it8->sy == SSTRING) ? WRITE_STRINGIFY : WRITE_UNCOOKED);
                }
                else {
                    const char *Subkey;
                    char *Nextkey;
                    if (it8->sy != SSTRING)
                        return SynError(it8, "Invalid value '%s' for property '%s'.", Buffer, VarName);

                    // chop the string as a list of "subkey, value" pairs, using ';' as a separator
                    for(Subkey = Buffer; Subkey != NULL; Subkey = Nextkey)
                    {
                        char *Value, *temp;

                        //  identify token pair boundary
                        Nextkey = (char*) strchr(Subkey, ';');
                        if(Nextkey)
                            *Nextkey++ = '\0';

                        // for each pair, split the subkey and the value
                        Value = (char*) strrchr(Subkey, ',');
                        if(Value == NULL)
                            return SynError(it8, "Invalid value for property '%s'.", VarName);

                        // gobble the spaces before the coma, and the coma itself
                        temp = Value++;
                        do *temp-- = '\0'; while(temp >= Subkey && *temp == ' ');

                        // gobble any space at the right
                        temp = Value + strlen(Value) - 1;
                        while(*temp == ' ') *temp-- = '\0';

                        // trim the strings from the left
                        Subkey += strspn(Subkey, " ");
                        Value += strspn(Value, " ");

                        if(Subkey[0] == 0 || Value[0] == 0)
                            return SynError(it8, "Invalid value for property '%s'.", VarName);
                        AddToList(it8, &GetTable(it8)->HeaderList, VarName, Subkey, Value, WRITE_PAIR);
                    }
                }

                InSymbol(it8);
                break;


        case SEOLN: break;

        default:
                return SynError(it8, "expected keyword or identifier");
        }

    SkipEOLN(it8);
    }

    return TRUE;

}


static
LCMSBOOL ParseIT8(LPIT8 it8, LCMSBOOL nosheet)
{
    char* SheetTypePtr = it8 ->SheetType;

    if (nosheet == 0) {

    // First line is a very special case.

    while (isseparator(it8->ch))
            NextCh(it8);

    while (it8->ch != '\r' && it8 ->ch != '\n' && it8->ch != '\t' && it8 -> ch != -1) {

        *SheetTypePtr++= (char) it8 ->ch;
        NextCh(it8);
    }
    }

    *SheetTypePtr = 0;
    InSymbol(it8);

    SkipEOLN(it8);

    while (it8-> sy != SEOF &&
           it8-> sy != SSYNERROR) {

            switch (it8 -> sy) {

            case SBEGIN_DATA_FORMAT:
                    if (!DataFormatSection(it8)) return FALSE;
                    break;

            case SBEGIN_DATA:

                    if (!DataSection(it8)) return FALSE;

                    if (it8 -> sy != SEOF) {

                            AllocTable(it8);
                            it8 ->nTable = it8 ->TablesCount - 1;
                    }
                    break;

            case SEOLN:
                    SkipEOLN(it8);
                    break;

            default:
                    if (!HeaderSection(it8)) return FALSE;
           }

    }

    return (it8 -> sy != SSYNERROR);
}



// Init usefull pointers

static
void CookPointers(LPIT8 it8)
{
    int idField, i;
    char* Fld;
    int j;
    int nOldTable = it8 ->nTable;

    for (j=0; j < it8 ->TablesCount; j++) {

    LPTABLE t = it8 ->Tab + j;

    t -> SampleID = 0;
    it8 ->nTable = j;

    for (idField = 0; idField < t -> nSamples; idField++)
    {
        if (t ->DataFormat == NULL) {
             SynError(it8, "Undefined DATA_FORMAT");
             return;

        }

        Fld = t->DataFormat[idField];
        if (!Fld) continue;


        if (stricmp(Fld, "SAMPLE_ID") == 0) {

                    t -> SampleID = idField;

        for (i=0; i < t -> nPatches; i++) {

                char *Data = GetData(it8, i, idField);
                if (Data) {
                    char Buffer[256];

                    strncpy(Buffer, Data, 255);
                    Buffer[255] = 0;

                    if (strlen(Buffer) <= strlen(Data))
                        strcpy(Data, Buffer);
                    else
                        SetData(it8, i, idField, Buffer);

                }
                }

        }

        // "LABEL" is an extension. It keeps references to forward tables

        if ((stricmp(Fld, "LABEL") == 0) || Fld[0] == '$' ) {

                    // Search for table references...
                    for (i=0; i < t -> nPatches; i++) {

                            char *Label = GetData(it8, i, idField);

                            if (Label) {

                                int k;

                                // This is the label, search for a table containing
                                // this property

                                for (k=0; k < it8 ->TablesCount; k++) {

                                    LPTABLE Table = it8 ->Tab + k;
                                    LPKEYVALUE p;

                                    if (IsAvailableOnList(Table->HeaderList, Label, NULL, &p)) {

                                        // Available, keep type and table
                                        char Buffer[256];

                                        char *Type  = p ->Value;
                                        int  nTable = k;

                                        snprintf(Buffer, 255, "%s %d %s", Label, nTable, Type );

                                        SetData(it8, i, idField, Buffer);
                                    }
                                }


                            }

                    }


        }

    }
    }

    it8 ->nTable = nOldTable;
}

// Try to infere if the file is a CGATS/IT8 file at all. Read first line
// that should be something like some printable characters plus a \n

static
int IsMyBlock(LPBYTE Buffer, size_t n)
{
    int cols = 1, space = 0, quot = 0;
    size_t i;

    if (n < 10) return FALSE;   // Too small

    if (n > 132)
        n = 132;

    for (i = 1; i < n; i++) {

        switch(Buffer[i])
        {
        case '\n':
        case '\r':
            return quot == 1 || cols > 2 ? 0 : cols;
        case '\t':
        case ' ':
            if(!quot && !space)
                space = 1;
            break;
        case '\"':
            quot = !quot;
            break;
        default:
            if (Buffer[i] < 32) return 0;
            if (Buffer[i] > 127) return 0;
            cols += space;
            space = 0;
            break;
        }
    }

    return FALSE;

}


static
int IsMyFile(const char* FileName)
{
   FILE *fp;
   size_t Size;
   BYTE Ptr[133];

   fp = fopen(FileName, "rt");
   if (!fp) {
       cmsSignalError(LCMS_ERRC_ABORTED, "File '%s' not found", FileName);
       return FALSE;
   }

   Size = fread(Ptr, 1, 132, fp);
   fclose(fp);

   Ptr[Size] = '\0';

   return IsMyBlock(Ptr, Size);
}

// ---------------------------------------------------------- Exported routines


LCMSHANDLE LCMSEXPORT cmsIT8LoadFromMem(void *Ptr, size_t len)
{
    LCMSHANDLE hIT8;
    LPIT8  it8;

    int type = IsMyBlock((LPBYTE) Ptr, len);
    if (type == 0) return NULL;

    hIT8 = cmsIT8Alloc();
    if (!hIT8) return NULL;

    it8 = (LPIT8) hIT8;
    it8 ->MemoryBlock = (char*) _cmsMalloc(len + 1);

    strncpy(it8 ->MemoryBlock, (const char*) Ptr, len);
    it8 ->MemoryBlock[len] = 0;

    strncpy(it8->FileStack[0]->FileName, "", MAX_PATH-1);
    it8-> Source = it8 -> MemoryBlock;

    if (!ParseIT8(it8, type-1)) {

        cmsIT8Free(hIT8);
        return FALSE;
    }

    CookPointers(it8);
    it8 ->nTable = 0;

     _cmsFree(it8->MemoryBlock);
    it8 -> MemoryBlock = NULL;

    return hIT8;


}


LCMSHANDLE LCMSEXPORT cmsIT8LoadFromFile(const char* cFileName)
{

     LCMSHANDLE hIT8;
     LPIT8  it8;

     int type = IsMyFile(cFileName);
     if (type == 0) return NULL;

     hIT8 = cmsIT8Alloc();
     it8 = (LPIT8) hIT8;
     if (!hIT8) return NULL;


     it8 ->FileStack[0]->Stream = fopen(cFileName, "rt");

     if (!it8 ->FileStack[0]->Stream) {
         cmsIT8Free(hIT8);
         return NULL;
     }


    strncpy(it8->FileStack[0]->FileName, cFileName, MAX_PATH-1);
    it8->FileStack[0]->FileName[MAX_PATH-1] = 0;

    if (!ParseIT8(it8, type-1)) {

            fclose(it8 ->FileStack[0]->Stream);
            cmsIT8Free(hIT8);
            return NULL;
    }

    CookPointers(it8);
    it8 ->nTable = 0;

    fclose(it8 ->FileStack[0]->Stream);
    return hIT8;

}

int LCMSEXPORT cmsIT8EnumDataFormat(LCMSHANDLE hIT8, char ***SampleNames)
{
        LPIT8 it8 = (LPIT8) hIT8;
        LPTABLE t = GetTable(it8);

        *SampleNames = t -> DataFormat;
        return t -> nSamples;
}


int LCMSEXPORT cmsIT8EnumProperties(LCMSHANDLE hIT8, const char ***PropertyNames)
{
    LPIT8 it8 = (LPIT8) hIT8;
    LPKEYVALUE p;
    int n;
    const char **Props;
    LPTABLE t = GetTable(it8);

    // Pass#1 - count properties

    n = 0;
    for (p = t -> HeaderList;  p != NULL; p = p->Next) {
        n++;
    }


    Props = (const char **) AllocChunk(it8, sizeof(char *) * n);

    // Pass#2 - Fill pointers
    n = 0;
    for (p = t -> HeaderList;  p != NULL; p = p->Next) {
        Props[n++] = p -> Keyword;
    }

    *PropertyNames = Props;
    return n;
}

int LCMSEXPORT cmsIT8EnumPropertyMulti(LCMSHANDLE hIT8, const char* cProp, const char ***SubpropertyNames)
{
    LPIT8 it8 = (LPIT8) hIT8;
    LPKEYVALUE p, tmp;
    int n;
    const char **Props;
    LPTABLE t = GetTable(it8);

    if(!IsAvailableOnList(t->HeaderList, cProp, NULL, &p)) {
        *SubpropertyNames = 0;
        return 0;
    }

    // Pass#1 - count properties

    n = 0;
    for (tmp = p;  tmp != NULL; tmp = tmp->NextSubkey) {
        if(tmp->Subkey != NULL)
            n++;
    }


    Props = (const char **) AllocChunk(it8, sizeof(char *) * n);

    // Pass#2 - Fill pointers
    n = 0;
    for (tmp = p;  tmp != NULL; tmp = tmp->NextSubkey) {
        if(tmp->Subkey != NULL)
            Props[n++] = p ->Subkey;
    }

    *SubpropertyNames = Props;
    return n;
}

static
int LocatePatch(LPIT8 it8, const char* cPatch)
{
    int i;
    const char *data;
    LPTABLE t = GetTable(it8);

    for (i=0; i < t-> nPatches; i++) {

        data = GetData(it8, i, t->SampleID);

        if (data != NULL) {

                if (stricmp(data, cPatch) == 0)
                        return i;
                }
        }

        // SynError(it8, "Couldn't find patch '%s'\n", cPatch);
        return -1;
}


static
int LocateEmptyPatch(LPIT8 it8)
{
    int i;
    const char *data;
    LPTABLE t = GetTable(it8);

    for (i=0; i < t-> nPatches; i++) {

        data = GetData(it8, i, t->SampleID);

        if (data == NULL)
                    return i;

        }

        return -1;
}

static
int LocateSample(LPIT8 it8, const char* cSample)
{
    int i;
    const char *fld;
    LPTABLE t = GetTable(it8);

    for (i=0; i < t->nSamples; i++) {

        fld = GetDataFormat(it8, i);
        if (stricmp(fld, cSample) == 0)
            return i;
    }


    // SynError(it8, "Couldn't find data field %s\n", cSample);
    return -1;

}


int LCMSEXPORT cmsIT8GetDataFormat(LCMSHANDLE hIT8, const char* cSample)
{
    LPIT8 it8 = (LPIT8) hIT8;
    return LocateSample(it8, cSample);
}



const char* LCMSEXPORT cmsIT8GetDataRowCol(LCMSHANDLE hIT8, int row, int col)
{
    LPIT8 it8 = (LPIT8) hIT8;

    return GetData(it8, row, col);
}


double LCMSEXPORT cmsIT8GetDataRowColDbl(LCMSHANDLE hIT8, int row, int col)
{
    const char* Buffer;

    Buffer = cmsIT8GetDataRowCol(hIT8, row, col);

    if (Buffer) {

        return atof(Buffer);

    } else
        return 0;

}


LCMSBOOL LCMSEXPORT cmsIT8SetDataRowCol(LCMSHANDLE hIT8, int row, int col, const char* Val)
{
    LPIT8 it8 = (LPIT8) hIT8;

    return SetData(it8, row, col, Val);
}


LCMSBOOL LCMSEXPORT cmsIT8SetDataRowColDbl(LCMSHANDLE hIT8, int row, int col, double Val)
{
    LPIT8 it8 = (LPIT8) hIT8;
    char Buff[256];

    sprintf(Buff, it8->DoubleFormatter, Val);

    return SetData(it8, row, col, Buff);
}



const char* LCMSEXPORT cmsIT8GetData(LCMSHANDLE hIT8, const char* cPatch, const char* cSample)
{
    LPIT8 it8 = (LPIT8) hIT8;
    int iField, iSet;


    iField = LocateSample(it8, cSample);
    if (iField < 0) {
        return NULL;
    }


    iSet = LocatePatch(it8, cPatch);
    if (iSet < 0) {
            return NULL;
    }

    return GetData(it8, iSet, iField);
}


double LCMSEXPORT cmsIT8GetDataDbl(LCMSHANDLE it8, const char* cPatch, const char* cSample)
{
    const char* Buffer;

    Buffer = cmsIT8GetData(it8, cPatch, cSample);

    if (Buffer) {

        return atof(Buffer);

    } else {

        return 0;
    }
}



LCMSBOOL LCMSEXPORT cmsIT8SetData(LCMSHANDLE hIT8, const char* cPatch,
                        const char* cSample,
                        const char *Val)
{
    LPIT8 it8 = (LPIT8) hIT8;
    int iField, iSet;
    LPTABLE t = GetTable(it8);


    iField = LocateSample(it8, cSample);

    if (iField < 0)
        return FALSE;



        if (t-> nPatches == 0) {

                AllocateDataFormat(it8);
                AllocateDataSet(it8);
                CookPointers(it8);
        }


        if (stricmp(cSample, "SAMPLE_ID") == 0)
        {

                iSet   = LocateEmptyPatch(it8);
                if (iSet < 0) {
                        return SynError(it8, "Couldn't add more patches '%s'\n", cPatch);
                }

                iField = t -> SampleID;
        }
        else {
                iSet = LocatePatch(it8, cPatch);
                if (iSet < 0) {
                    return FALSE;
            }
        }

        return SetData(it8, iSet, iField, Val);
}


LCMSBOOL LCMSEXPORT cmsIT8SetDataDbl(LCMSHANDLE hIT8, const char* cPatch,
                        const char* cSample,
                        double Val)
{
    LPIT8 it8 = (LPIT8) hIT8;
    char Buff[256];

        snprintf(Buff, 255, it8->DoubleFormatter, Val);
        return cmsIT8SetData(hIT8, cPatch, cSample, Buff);

}

// Buffer should get MAXSTR at least

const char* LCMSEXPORT cmsIT8GetPatchName(LCMSHANDLE hIT8, int nPatch, char* buffer)
{
        LPIT8 it8 = (LPIT8) hIT8;
        LPTABLE t = GetTable(it8);
        char* Data = GetData(it8, nPatch, t->SampleID);

        if (!Data) return NULL;
        if (!buffer) return Data;

        strncpy(buffer, Data, MAXSTR-1);
        buffer[MAXSTR-1] = 0;
        return buffer;
}

int LCMSEXPORT cmsIT8GetPatchByName(LCMSHANDLE hIT8, const char *cPatch)
{
    return LocatePatch((LPIT8)hIT8, cPatch);
}

int LCMSEXPORT cmsIT8TableCount(LCMSHANDLE hIT8)
{
        LPIT8 it8 = (LPIT8) hIT8;

        return it8 ->TablesCount;
}

// This handles the "LABEL" extension.
// Label, nTable, Type

int LCMSEXPORT cmsIT8SetTableByLabel(LCMSHANDLE hIT8, const char* cSet, const char* cField, const char* ExpectedType)
{
    const char* cLabelFld;
    char Type[256], Label[256];
    int nTable;

    if (cField != NULL && *cField == 0)
            cField = "LABEL";

    if (cField == NULL)
            cField = "LABEL";

    cLabelFld = cmsIT8GetData(hIT8, cSet, cField);
    if (!cLabelFld) return -1;

    if (sscanf(cLabelFld, "%255s %d %255s", Label, &nTable, Type) != 3)
            return -1;

    if (ExpectedType != NULL && *ExpectedType == 0)
        ExpectedType = NULL;

    if (ExpectedType) {

        if (stricmp(Type, ExpectedType) != 0) return -1;
    }

    return cmsIT8SetTable(hIT8, nTable);
}


LCMSBOOL LCMSEXPORT cmsIT8SetIndexColumn(LCMSHANDLE hIT8, const char* cSample)
{
    LPIT8 it8 = (LPIT8) hIT8;

    int pos = LocateSample(it8, cSample);
    if(pos == -1)
        return FALSE;

    it8->Tab[it8->nTable].SampleID = pos;
    return TRUE;
}


void LCMSEXPORT cmsIT8DefineDblFormat(LCMSHANDLE hIT8, const char* Formatter)
{
    LPIT8 it8 = (LPIT8) hIT8;

    if (Formatter == NULL)
        strcpy(it8->DoubleFormatter, DEFAULT_DBL_FORMAT);
    else
        strcpy(it8->DoubleFormatter, Formatter);
}

