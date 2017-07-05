/*
 * Copyright (c) 2004, 2005, Oracle and/or its affiliates. All rights reserved.
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

/* Misc functions for conversion of Unicode and UTF-8 and platform encoding */

#include <stdio.h>
#include <stddef.h>
#include <stdlib.h>
#include <stdarg.h>
#include <string.h>
#include <ctype.h>

#include "jni.h"

#include "utf.h"

/*
 * Error handler
 */
void
utfError(char *file, int line, char *message)
{
    (void)fprintf(stderr, "UTF ERROR [\"%s\":%d]: %s\n", file, line, message);
    abort();
}

/*
 * Convert UTF-8 to UTF-16
 *    Returns length or -1 if output overflows.
 */
int JNICALL
utf8ToUtf16(struct UtfInst *ui, jbyte *utf8, int len, unsigned short *output, int outputMaxLen)
{
    int outputLen;
    int i;

    UTF_ASSERT(utf8);
    UTF_ASSERT(len>=0);
    UTF_ASSERT(output);
    UTF_ASSERT(outputMaxLen>0);

    i = 0;
    outputLen = 0;
    while ( i<len ) {
        unsigned code, x, y, z;

        if ( outputLen >= outputMaxLen ) {
            return -1;
        }
        x = (unsigned char)utf8[i++];
        code = x;
        if ( (x & 0xE0)==0xE0 ) {
            y = (unsigned char)utf8[i++];
            z = (unsigned char)utf8[i++];
            code = ((x & 0xF)<<12) + ((y & 0x3F)<<6) + (z & 0x3F);
        } else if ( (x & 0xC0)==0xC0 ) {
            y = (unsigned char)utf8[i++];
            code = ((x & 0x1F)<<6) + (y & 0x3F);
        }
        output[outputLen++] = code;
    }
    return outputLen;
}

/*
 * Convert UTF-16 to UTF-8 Modified
 *    Returns length or -1 if output overflows.
 */
int JNICALL
utf16ToUtf8m(struct UtfInst *ui, unsigned short *utf16, int len, jbyte *output, int outputMaxLen)
{
    int i;
    int outputLen;

    UTF_ASSERT(utf16);
    UTF_ASSERT(len>=0);
    UTF_ASSERT(output);
    UTF_ASSERT(outputMaxLen>0);

    outputLen = 0;
    for (i = 0; i < len; i++) {
        unsigned code;

        code = utf16[i];
        if ( code >= 0x0001 && code <= 0x007F ) {
            output[outputLen++] = code;
        } else if ( code == 0 || ( code >= 0x0080 && code <= 0x07FF ) ) {
            output[outputLen++] = ((code>>6) & 0x1F) | 0xC0;
            output[outputLen++] = (code & 0x3F) | 0x80;
        } else if ( code >= 0x0800 && code <= 0xFFFF ) {
            output[outputLen++] = ((code>>12) & 0x0F) | 0xE0;
            output[outputLen++] = ((code>>6) & 0x3F) | 0x80;
            output[outputLen++] = (code & 0x3F) | 0x80;
        }
        if ( outputLen > outputMaxLen ) {
            return -1;
        }
    }
    output[outputLen] = 0;
    return outputLen;
}

int JNICALL
utf16ToUtf8s(struct UtfInst *ui, unsigned short *utf16, int len, jbyte *output, int outputMaxLen)
{
    return -1; /* FIXUP */
}

/* Determine length of this Standard UTF-8 in Modified UTF-8.
 *    Validation is done of the basic UTF encoding rules, returns
 *    length (no change) when errors are detected in the UTF encoding.
 *
 *    Note: Accepts Modified UTF-8 also, no verification on the
 *          correctness of Standard UTF-8 is done. e,g, 0xC080 input is ok.
 */
int JNICALL
utf8sToUtf8mLength(struct UtfInst *ui, jbyte *string, int length)
{
    int newLength;
    int i;

    newLength = 0;
    for ( i = 0 ; i < length ; i++ ) {
        unsigned byte;

        byte = (unsigned char)string[i];
        if ( (byte & 0x80) == 0 ) { /* 1byte encoding */
            newLength++;
            if ( byte == 0 ) {
                newLength++; /* We gain one byte in length on NULL bytes */
            }
        } else if ( (byte & 0xE0) == 0xC0 ) { /* 2byte encoding */
            /* Check encoding of following bytes */
            if ( (i+1) >= length || (string[i+1] & 0xC0) != 0x80 ) {
                break; /* Error condition */
            }
            i++; /* Skip next byte */
            newLength += 2;
        } else if ( (byte & 0xF0) == 0xE0 ) { /* 3byte encoding */
            /* Check encoding of following bytes */
            if ( (i+2) >= length || (string[i+1] & 0xC0) != 0x80
                                 || (string[i+2] & 0xC0) != 0x80 ) {
                break; /* Error condition */
            }
            i += 2; /* Skip next two bytes */
            newLength += 3;
        } else if ( (byte & 0xF8) == 0xF0 ) { /* 4byte encoding */
            /* Check encoding of following bytes */
            if ( (i+3) >= length || (string[i+1] & 0xC0) != 0x80
                                 || (string[i+2] & 0xC0) != 0x80
                                 || (string[i+3] & 0xC0) != 0x80 ) {
                break; /* Error condition */
            }
            i += 3; /* Skip next 3 bytes */
            newLength += 6; /* 4byte encoding turns into 2 3byte ones */
        } else {
            break; /* Error condition */
        }
    }
    if ( i != length ) {
        /* Error in finding new length, return old length so no conversion */
        /* FIXUP: ERROR_MESSAGE? */
        return length;
    }
    return newLength;
}

/* Convert Standard UTF-8 to Modified UTF-8.
 *    Assumes the UTF-8 encoding was validated by utf8mLength() above.
 *
 *    Note: Accepts Modified UTF-8 also, no verification on the
 *          correctness of Standard UTF-8 is done. e,g, 0xC080 input is ok.
 */
void JNICALL
utf8sToUtf8m(struct UtfInst *ui, jbyte *string, int length, jbyte *newString, int newLength)
{
    int i;
    int j;

    j = 0;
    for ( i = 0 ; i < length ; i++ ) {
        unsigned byte1;

        byte1 = (unsigned char)string[i];

        /* NULL bytes and bytes starting with 11110xxx are special */
        if ( (byte1 & 0x80) == 0 ) { /* 1byte encoding */
            if ( byte1 == 0 ) {
                /* Bits out: 11000000 10000000 */
                newString[j++] = (jbyte)0xC0;
                newString[j++] = (jbyte)0x80;
            } else {
                /* Single byte */
                newString[j++] = byte1;
            }
        } else if ( (byte1 & 0xE0) == 0xC0 ) { /* 2byte encoding */
            newString[j++] = byte1;
            newString[j++] = string[++i];
        } else if ( (byte1 & 0xF0) == 0xE0 ) { /* 3byte encoding */
            newString[j++] = byte1;
            newString[j++] = string[++i];
            newString[j++] = string[++i];
        } else if ( (byte1 & 0xF8) == 0xF0 ) { /* 4byte encoding */
            /* Beginning of 4byte encoding, turn into 2 3byte encodings */
            unsigned byte2, byte3, byte4, u21;

            /* Bits in: 11110xxx 10xxxxxx 10xxxxxx 10xxxxxx */
            byte2 = (unsigned char)string[++i];
            byte3 = (unsigned char)string[++i];
            byte4 = (unsigned char)string[++i];
            /* Reconstruct full 21bit value */
            u21  = (byte1 & 0x07) << 18;
            u21 += (byte2 & 0x3F) << 12;
            u21 += (byte3 & 0x3F) << 6;
            u21 += (byte4 & 0x3F);
            /* Bits out: 11101101 1010xxxx 10xxxxxx */
            newString[j++] = (jbyte)0xED;
            newString[j++] = (jbyte)(0xA0 + (((u21 >> 16) - 1) & 0x0F));
            newString[j++] = (jbyte)(0x80 + ((u21 >> 10) & 0x3F));
            /* Bits out: 11101101 1011xxxx 10xxxxxx */
            newString[j++] = (jbyte)0xED;
            newString[j++] = (jbyte)(0xB0 + ((u21 >>  6) & 0x0F));
            newString[j++] = byte4;
        }
    }
    UTF_ASSERT(i==length);
    UTF_ASSERT(j==newLength);
    newString[j] = (jbyte)0;
}

/* Given a Modified UTF-8 string, calculate the Standard UTF-8 length.
 *   Basic validation of the UTF encoding rules is done, and length is
 *   returned (no change) when errors are detected.
 *
 *   Note: No validation is made that this is indeed Modified UTF-8 coming in.
 *
 */
int JNICALL
utf8mToUtf8sLength(struct UtfInst *ui, jbyte *string, int length)
{
    int newLength;
    int i;

    newLength = 0;
    for ( i = 0 ; i < length ; i++ ) {
        unsigned byte1, byte2, byte3, byte4, byte5, byte6;

        byte1 = (unsigned char)string[i];
        if ( (byte1 & 0x80) == 0 ) { /* 1byte encoding */
            newLength++;
        } else if ( (byte1 & 0xE0) == 0xC0 ) { /* 2byte encoding */
            /* Check encoding of following bytes */
            if ( (i+1) >= length || (string[i+1] & 0xC0) != 0x80 ) {
                break; /* Error condition */
            }
            byte2 = (unsigned char)string[++i];
            if ( byte1 != 0xC0 || byte2 != 0x80 ) {
                newLength += 2; /* Normal 2byte encoding, not 0xC080 */
            } else {
                newLength++;    /* We will turn 0xC080 into 0 */
            }
        } else if ( (byte1 & 0xF0) == 0xE0 ) { /* 3byte encoding */
            /* Check encoding of following bytes */
            if ( (i+2) >= length || (string[i+1] & 0xC0) != 0x80
                                 || (string[i+2] & 0xC0) != 0x80 ) {
                break; /* Error condition */
            }
            byte2 = (unsigned char)string[++i];
            byte3 = (unsigned char)string[++i];
            newLength += 3;
            /* Possible process a second 3byte encoding */
            if ( (i+3) < length && byte1 == 0xED && (byte2 & 0xF0) == 0xA0 ) {
                /* See if this is a pair of 3byte encodings */
                byte4 = (unsigned char)string[i+1];
                byte5 = (unsigned char)string[i+2];
                byte6 = (unsigned char)string[i+3];
                if ( byte4 == 0xED && (byte5 & 0xF0) == 0xB0 ) {
                    /* Check encoding of 3rd byte */
                    if ( (byte6 & 0xC0) != 0x80 ) {
                        break; /* Error condition */
                    }
                    newLength++; /* New string will have 4byte encoding */
                    i += 3;       /* Skip next 3 bytes */
                }
            }
        } else {
            break; /* Error condition */
        }
    }
    if ( i != length ) {
        /* Error in UTF encoding */
        /*  FIXUP: ERROR_MESSAGE()? */
        return length;
    }
    return newLength;
}

/* Convert a Modified UTF-8 string into a Standard UTF-8 string
 *   It is assumed that this string has been validated in terms of the
 *   basic UTF encoding rules by utf8Length() above.
 *
 *   Note: No validation is made that this is indeed Modified UTF-8 coming in.
 *
 */
void JNICALL
utf8mToUtf8s(struct UtfInst *ui, jbyte *string, int length, jbyte *newString, int newLength)
{
    int i;
    int j;

    j = 0;
    for ( i = 0 ; i < length ; i++ ) {
        unsigned byte1, byte2, byte3, byte4, byte5, byte6;

        byte1 = (unsigned char)string[i];
        if ( (byte1 & 0x80) == 0 ) { /* 1byte encoding */
            /* Single byte */
            newString[j++] = byte1;
        } else if ( (byte1 & 0xE0) == 0xC0 ) { /* 2byte encoding */
            byte2 = (unsigned char)string[++i];
            if ( byte1 != 0xC0 || byte2 != 0x80 ) {
                newString[j++] = byte1;
                newString[j++] = byte2;
            } else {
                newString[j++] = 0;
            }
        } else if ( (byte1 & 0xF0) == 0xE0 ) { /* 3byte encoding */
            byte2 = (unsigned char)string[++i];
            byte3 = (unsigned char)string[++i];
            if ( i+3 < length && byte1 == 0xED && (byte2 & 0xF0) == 0xA0 ) {
                /* See if this is a pair of 3byte encodings */
                byte4 = (unsigned char)string[i+1];
                byte5 = (unsigned char)string[i+2];
                byte6 = (unsigned char)string[i+3];
                if ( byte4 == 0xED && (byte5 & 0xF0) == 0xB0 ) {
                    unsigned u21;

                    /* Bits in: 11101101 1010xxxx 10xxxxxx */
                    /* Bits in: 11101101 1011xxxx 10xxxxxx */
                    i += 3;

                    /* Reconstruct 21 bit code */
                    u21  = ((byte2 & 0x0F) + 1) << 16;
                    u21 += (byte3 & 0x3F) << 10;
                    u21 += (byte5 & 0x0F) << 6;
                    u21 += (byte6 & 0x3F);

                    /* Bits out: 11110xxx 10xxxxxx 10xxxxxx 10xxxxxx */

                    /* Convert to 4byte encoding */
                    newString[j++] = 0xF0 + ((u21 >> 18) & 0x07);
                    newString[j++] = 0x80 + ((u21 >> 12) & 0x3F);
                    newString[j++] = 0x80 + ((u21 >>  6) & 0x3F);
                    newString[j++] = 0x80 + (u21 & 0x3F);
                    continue;
                }
            }
            /* Normal 3byte encoding */
            newString[j++] = byte1;
            newString[j++] = byte2;
            newString[j++] = byte3;
        }
    }
    UTF_ASSERT(i==length);
    UTF_ASSERT(j==newLength);
    newString[j] = 0;
}

/* ================================================================= */

#if 1  /* Test program */

/*
 * Convert any byte array into a printable string.
 *    Returns length or -1 if output overflows.
 */
static int
bytesToPrintable(struct UtfInst *ui, char *bytes, int len, char *output, int outputMaxLen)
{
    int outputLen;
    int i;

    UTF_ASSERT(bytes);
    UTF_ASSERT(len>=0);
    UTF_ASSERT(output);
    UTF_ASSERT(outputMaxLen>=0);

    outputLen = 0;
    for ( i=0; i<len ; i++ ) {
        unsigned byte;

        byte = bytes[i];
        if ( outputLen >= outputMaxLen ) {
            return -1;
        }
        if ( byte <= 0x7f && isprint(byte) && !iscntrl(byte) ) {
            output[outputLen++] = (char)byte;
        } else {
            (void)sprintf(output+outputLen,"\\x%02x",byte);
            outputLen += 4;
        }
    }
    output[outputLen] = 0;
    return outputLen;
}

static void
test(void)
{
    static char *strings[] = {
                "characters",
                "abcdefghijklmnopqrstuvwxyz",
                "0123456789",
                "!@#$%^&*()_+=-{}[]:;",
                NULL };
    int i;
    struct UtfInst *ui;

    ui = utfInitialize(NULL);

    i = 0;
    while ( strings[i] != NULL ) {
        char *str;
        #define MAX 1024
        char buf0[MAX];
        char buf1[MAX];
        char buf2[MAX];
        unsigned short buf3[MAX];
        int len1;
        int len2;
        int len3;

        str = strings[i];

        (void)bytesToPrintable(ui, str, (int)strlen(str), buf0, 1024);

        len1 = utf8FromPlatform(ui, str, (int)strlen(str), (jbyte*)buf1, 1024);

        UTF_ASSERT(len1==(int)strlen(str));

        len3 = utf8ToUtf16(ui, (jbyte*)buf1, len1, (jchar*)buf3, 1024);

        UTF_ASSERT(len3==len1);

        len1 = utf16ToUtf8m(ui, (jchar*)buf3, len3, (jbyte*)buf1, 1024);

        UTF_ASSERT(len1==len3);
        UTF_ASSERT(strcmp(str, buf1) == 0);

        len2 = utf8ToPlatform(ui, (jbyte*)buf1, len1, buf2, 1024);

        UTF_ASSERT(len2==len1);
        UTF_ASSERT(strcmp(str, buf2) == 0);

        i++;
    }

    utfTerminate(ui, NULL);

}

int
main(int argc, char **argv)
{
    test();
    return 0;
}

#endif
