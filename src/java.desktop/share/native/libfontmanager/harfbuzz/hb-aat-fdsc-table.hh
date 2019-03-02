/*
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
 */

#ifndef HB_AAT_FDSC_TABLE_HH
#define HB_AAT_FDSC_TABLE_HH

#include "hb-aat-layout-common.hh"
#include "hb-open-type.hh"

/*
 * fdsc -- Font descriptors
 * https://developer.apple.com/fonts/TrueType-Reference-Manual/RM06/Chap6fdsc.html
 */
#define HB_AAT_TAG_fdsc HB_TAG('f','d','s','c')


namespace AAT {


struct FontDescriptor
{
  bool has_data () const { return tag; }

  int cmp (hb_tag_t a) const { return tag.cmp (a); }

  float get_value () const { return u.value.to_float (); }

  enum non_alphabetic_value_t {
    Alphabetic          = 0,
    Dingbats            = 1,
    PiCharacters        = 2,
    Fleurons            = 3,
    DecorativeBorders   = 4,
    InternationalSymbols= 5,
    MathSymbols         = 6
  };

  bool sanitize (hb_sanitize_context_t *c) const
  {
    TRACE_SANITIZE (this);
    return_trace (c->check_struct (this));
  }

  protected:
  Tag           tag;            /* The 4-byte table tag name. */
  union {
  Fixed         value;          /* The value for the descriptor tag. */
  HBUINT32      nalfType;       /* If the tag is `nalf`, see non_alphabetic_value_t */
  } u;
  public:
  DEFINE_SIZE_STATIC (8);
};

struct fdsc
{
  static constexpr hb_tag_t tableTag = HB_AAT_TAG_fdsc;

  enum {
    Weight       = HB_TAG ('w','g','h','t'),
                                /* Percent weight relative to regular weight.
                                 * (defaul value: 1.0) */
    Width        = HB_TAG ('w','d','t','h'),
                                /* Percent width relative to regular width.
                                 * (default value: 1.0) */
    Slant        = HB_TAG ('s','l','n','t'),
                                /* Angle of slant in degrees, where positive
                                 * is clockwise from straight up.
                                 * (default value: 0.0) */
    OpticalSize  = HB_TAG ('o','p','s','z'),
                                /* Point size the font was designed for.
                                 * (default value: 12.0) */
    NonAlphabetic= HB_TAG ('n','a','l','f')
                                /* These values are treated as integers,
                                 * not fixed32s. 0 means alphabetic, and greater
                                 * integers mean the font is non-alphabetic (e.g. symbols).
                                 * (default value: 0) */
  };

  const FontDescriptor &get_descriptor (hb_tag_t style) const
  { return descriptors.lsearch (style); }

  bool sanitize (hb_sanitize_context_t *c) const
  {
    TRACE_SANITIZE (this);
    return_trace (c->check_struct (this) &&
                  descriptors.sanitize (c));
  }

  protected:
  Fixed         version;        /* Version number of the font descriptors
                                 * table (0x00010000 for the current version). */
  LArrayOf<FontDescriptor>
                descriptors;    /* List of tagged-coordinate pairs style descriptors
                                 * that will be included to characterize this font.
                                 * Each descriptor consists of a <tag, value> pair.
                                 * These pairs are located in the gxFontDescriptor
                                 * array that follows. */
  public:
  DEFINE_SIZE_ARRAY (8, descriptors);
};

} /* namespace AAT */


#endif /* HB_AAT_FDSC_TABLE_HH */
