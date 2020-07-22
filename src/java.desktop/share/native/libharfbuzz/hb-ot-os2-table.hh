/*
 * Copyright © 2011,2012  Google, Inc.
 * Copyright © 2018  Ebrahim Byagowi
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

#include "hb-open-type.hh"
#include "hb-ot-os2-unicode-ranges.hh"

#include "hb-set.hh"

/*
 * OS/2 and Windows Metrics
 * https://docs.microsoft.com/en-us/typography/opentype/spec/os2
 */
#define HB_OT_TAG_OS2 HB_TAG('O','S','/','2')


namespace OT {

struct OS2V1Tail
{
  bool sanitize (hb_sanitize_context_t *c) const
  {
    TRACE_SANITIZE (this);
    return_trace (c->check_struct (this));
  }

  public:
  HBUINT32      ulCodePageRange1;
  HBUINT32      ulCodePageRange2;
  public:
  DEFINE_SIZE_STATIC (8);
};

struct OS2V2Tail
{
  bool sanitize (hb_sanitize_context_t *c) const
  {
    TRACE_SANITIZE (this);
    return_trace (c->check_struct (this));
  }

  public:
  HBINT16       sxHeight;
  HBINT16       sCapHeight;
  HBUINT16      usDefaultChar;
  HBUINT16      usBreakChar;
  HBUINT16      usMaxContext;
  public:
  DEFINE_SIZE_STATIC (10);
};

struct OS2V5Tail
{
  bool sanitize (hb_sanitize_context_t *c) const
  {
    TRACE_SANITIZE (this);
    return_trace (c->check_struct (this));
  }

  public:
  HBUINT16      usLowerOpticalPointSize;
  HBUINT16      usUpperOpticalPointSize;
  public:
  DEFINE_SIZE_STATIC (4);
};

struct OS2
{
  static constexpr hb_tag_t tableTag = HB_OT_TAG_OS2;

  bool has_data () const { return this != &Null (OS2); }

  const OS2V1Tail &v1 () const { return version >= 1 ? v1X : Null (OS2V1Tail); }
  const OS2V2Tail &v2 () const { return version >= 2 ? v2X : Null (OS2V2Tail); }
  const OS2V5Tail &v5 () const { return version >= 5 ? v5X : Null (OS2V5Tail); }

  enum selection_flag_t {
    ITALIC              = 1u<<0,
    UNDERSCORE          = 1u<<1,
    NEGATIVE            = 1u<<2,
    OUTLINED            = 1u<<3,
    STRIKEOUT           = 1u<<4,
    BOLD                = 1u<<5,
    REGULAR             = 1u<<6,
    USE_TYPO_METRICS    = 1u<<7,
    WWS                 = 1u<<8,
    OBLIQUE             = 1u<<9
  };

  bool is_italic () const       { return fsSelection & ITALIC; }
  bool is_oblique () const      { return fsSelection & OBLIQUE; }
  bool is_typo_metrics () const { return fsSelection & USE_TYPO_METRICS; }

  enum width_class_t {
    FWIDTH_ULTRA_CONDENSED      = 1, /* 50% */
    FWIDTH_EXTRA_CONDENSED      = 2, /* 62.5% */
    FWIDTH_CONDENSED            = 3, /* 75% */
    FWIDTH_SEMI_CONDENSED       = 4, /* 87.5% */
    FWIDTH_NORMAL               = 5, /* 100% */
    FWIDTH_SEMI_EXPANDED        = 6, /* 112.5% */
    FWIDTH_EXPANDED             = 7, /* 125% */
    FWIDTH_EXTRA_EXPANDED       = 8, /* 150% */
    FWIDTH_ULTRA_EXPANDED       = 9  /* 200% */
  };

  float get_width () const
  {
    switch (usWidthClass) {
    case FWIDTH_ULTRA_CONDENSED:return 50.f;
    case FWIDTH_EXTRA_CONDENSED:return 62.5f;
    case FWIDTH_CONDENSED:      return 75.f;
    case FWIDTH_SEMI_CONDENSED: return 87.5f;
    default:
    case FWIDTH_NORMAL:         return 100.f;
    case FWIDTH_SEMI_EXPANDED:  return 112.5f;
    case FWIDTH_EXPANDED:       return 125.f;
    case FWIDTH_EXTRA_EXPANDED: return 150.f;
    case FWIDTH_ULTRA_EXPANDED: return 200.f;
    }
  }

  bool subset (hb_subset_plan_t *plan) const
  {
    hb_blob_t *os2_blob = hb_sanitize_context_t ().reference_table<OS2> (plan->source);
    hb_blob_t *os2_prime_blob = hb_blob_create_sub_blob (os2_blob, 0, -1);
    // TODO(grieger): move to hb_blob_copy_writable_or_fail
    hb_blob_destroy (os2_blob);

    OS2 *os2_prime = (OS2 *) hb_blob_get_data_writable (os2_prime_blob, nullptr);
    if (unlikely (!os2_prime)) {
      hb_blob_destroy (os2_prime_blob);
      return false;
    }

    uint16_t min_cp, max_cp;
    find_min_and_max_codepoint (plan->unicodes, &min_cp, &max_cp);
    os2_prime->usFirstCharIndex.set (min_cp);
    os2_prime->usLastCharIndex.set (max_cp);

    _update_unicode_ranges (plan->unicodes, os2_prime->ulUnicodeRange);
    bool result = plan->add_table (HB_OT_TAG_OS2, os2_prime_blob);

    hb_blob_destroy (os2_prime_blob);
    return result;
  }

  void _update_unicode_ranges (const hb_set_t *codepoints,
                               HBUINT32 ulUnicodeRange[4]) const
  {
    for (unsigned int i = 0; i < 4; i++)
      ulUnicodeRange[i].set (0);

    hb_codepoint_t cp = HB_SET_VALUE_INVALID;
    while (codepoints->next (&cp)) {
      unsigned int bit = _hb_ot_os2_get_unicode_range_bit (cp);
      if (bit < 128)
      {
        unsigned int block = bit / 32;
        unsigned int bit_in_block = bit % 32;
        unsigned int mask = 1 << bit_in_block;
        ulUnicodeRange[block].set (ulUnicodeRange[block] | mask);
      }
      if (cp >= 0x10000 && cp <= 0x110000)
      {
        /* the spec says that bit 57 ("Non Plane 0") implies that there's
           at least one codepoint beyond the BMP; so I also include all
           the non-BMP codepoints here */
        ulUnicodeRange[1].set (ulUnicodeRange[1] | (1 << 25));
      }
    }
  }

  static void find_min_and_max_codepoint (const hb_set_t *codepoints,
                                                 uint16_t *min_cp, /* OUT */
                                                 uint16_t *max_cp  /* OUT */)
  {
    *min_cp = codepoints->get_min ();
    *max_cp = codepoints->get_max ();
  }

  enum font_page_t {
    HEBREW_FONT_PAGE            = 0xB100, // Hebrew Windows 3.1 font page
    SIMP_ARABIC_FONT_PAGE       = 0xB200, // Simplified Arabic Windows 3.1 font page
    TRAD_ARABIC_FONT_PAGE       = 0xB300, // Traditional Arabic Windows 3.1 font page
    OEM_ARABIC_FONT_PAGE        = 0xB400, // OEM Arabic Windows 3.1 font page
    SIMP_FARSI_FONT_PAGE        = 0xBA00, // Simplified Farsi Windows 3.1 font page
    TRAD_FARSI_FONT_PAGE        = 0xBB00, // Traditional Farsi Windows 3.1 font page
    THAI_FONT_PAGE              = 0xDE00  // Thai Windows 3.1 font page
  };

  // https://github.com/Microsoft/Font-Validator/blob/520aaae/OTFontFileVal/val_OS2.cs#L644-L681
  font_page_t get_font_page () const
  { return (font_page_t) (version == 0 ? fsSelection & 0xFF00 : 0); }

  bool sanitize (hb_sanitize_context_t *c) const
  {
    TRACE_SANITIZE (this);
    if (unlikely (!c->check_struct (this))) return_trace (false);
    if (unlikely (version >= 1 && !v1X.sanitize (c))) return_trace (false);
    if (unlikely (version >= 2 && !v2X.sanitize (c))) return_trace (false);
    if (unlikely (version >= 5 && !v5X.sanitize (c))) return_trace (false);
    return_trace (true);
  }

  public:
  HBUINT16      version;
  HBINT16       xAvgCharWidth;
  HBUINT16      usWeightClass;
  HBUINT16      usWidthClass;
  HBUINT16      fsType;
  HBINT16       ySubscriptXSize;
  HBINT16       ySubscriptYSize;
  HBINT16       ySubscriptXOffset;
  HBINT16       ySubscriptYOffset;
  HBINT16       ySuperscriptXSize;
  HBINT16       ySuperscriptYSize;
  HBINT16       ySuperscriptXOffset;
  HBINT16       ySuperscriptYOffset;
  HBINT16       yStrikeoutSize;
  HBINT16       yStrikeoutPosition;
  HBINT16       sFamilyClass;
  HBUINT8       panose[10];
  HBUINT32      ulUnicodeRange[4];
  Tag           achVendID;
  HBUINT16      fsSelection;
  HBUINT16      usFirstCharIndex;
  HBUINT16      usLastCharIndex;
  HBINT16       sTypoAscender;
  HBINT16       sTypoDescender;
  HBINT16       sTypoLineGap;
  HBUINT16      usWinAscent;
  HBUINT16      usWinDescent;
  OS2V1Tail     v1X;
  OS2V2Tail     v2X;
  OS2V5Tail     v5X;
  public:
  DEFINE_SIZE_MIN (78);
};

} /* namespace OT */


#endif /* HB_OT_OS2_TABLE_HH */
