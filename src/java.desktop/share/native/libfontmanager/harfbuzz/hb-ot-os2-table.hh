/*
 * Copyright © 2011,2012  Google, Inc.
 *
 *  This is part of HarfBuzz, a text shaping library.
 *
 * Permission is hereby granted, without written agreement and without
 * license or royalty fees, to use, copy, modify, and distribute this
 * software and its documentation for any purpose, provided that the
 * above copyright notice and the following two paragraphs appear in
 * all copies of this software.
 *
 * IN NO EVENT SHALL THE COPYRIGHT HOLDER BE LIABLE TO ANY PARTY FOR
 * DIRECT, INDIRECT, SPECIAL, INCIDENTAL, OR CONSEQUENTIAL DAMAGES
 * ARISING OUT OF THE USE OF THIS SOFTWARE AND ITS DOCUMENTATION, EVEN
 * IF THE COPYRIGHT HOLDER HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH
 * DAMAGE.
 *
 * THE COPYRIGHT HOLDER SPECIFICALLY DISCLAIMS ANY WARRANTIES, INCLUDING,
 * BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 * FITNESS FOR A PARTICULAR PURPOSE.  THE SOFTWARE PROVIDED HEREUNDER IS
 * ON AN "AS IS" BASIS, AND THE COPYRIGHT HOLDER HAS NO OBLIGATION TO
 * PROVIDE MAINTENANCE, SUPPORT, UPDATES, ENHANCEMENTS, OR MODIFICATIONS.
 *
 * Google Author(s): Behdad Esfahbod
 */

#ifndef HB_OT_OS2_TABLE_HH
#define HB_OT_OS2_TABLE_HH

#include "hb-open-type-private.hh"


namespace OT {

/*
 * OS/2 and Windows Metrics
 * http://www.microsoft.com/typography/otspec/os2.htm
 */

#define HB_OT_TAG_os2 HB_TAG('O','S','/','2')

struct os2
{
  static const hb_tag_t tableTag = HB_OT_TAG_os2;

  inline bool sanitize (hb_sanitize_context_t *c) const
  {
    TRACE_SANITIZE (this);
    return_trace (c->check_struct (this));
  }

  public:
  USHORT        version;

  /* Version 0 */
  SHORT         xAvgCharWidth;
  USHORT        usWeightClass;
  USHORT        usWidthClass;
  USHORT        fsType;
  SHORT         ySubscriptXSize;
  SHORT         ySubscriptYSize;
  SHORT         ySubscriptXOffset;
  SHORT         ySubscriptYOffset;
  SHORT         ySuperscriptXSize;
  SHORT         ySuperscriptYSize;
  SHORT         ySuperscriptXOffset;
  SHORT         ySuperscriptYOffset;
  SHORT         yStrikeoutSize;
  SHORT         yStrikeoutPosition;
  SHORT         sFamilyClass;
  BYTE          panose[10];
  ULONG         ulUnicodeRange[4];
  Tag           achVendID;
  USHORT        fsSelection;
  USHORT        usFirstCharIndex;
  USHORT        usLastCharIndex;
  SHORT         sTypoAscender;
  SHORT         sTypoDescender;
  SHORT         sTypoLineGap;
  USHORT        usWinAscent;
  USHORT        usWinDescent;

  /* Version 1 */
  //ULONG ulCodePageRange1;
  //ULONG ulCodePageRange2;

  /* Version 2 */
  //SHORT sxHeight;
  //SHORT sCapHeight;
  //USHORT  usDefaultChar;
  //USHORT  usBreakChar;
  //USHORT  usMaxContext;

  /* Version 5 */
  //USHORT  usLowerOpticalPointSize;
  //USHORT  usUpperOpticalPointSize;

  public:
  DEFINE_SIZE_STATIC (78);
};

} /* namespace OT */


#endif /* HB_OT_OS2_TABLE_HH */
