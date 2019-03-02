/*
 * Copyright © 1998-2004  David Turner and Werner Lemberg
 * Copyright © 2006  Behdad Esfahbod
 * Copyright © 2007,2008,2009  Red Hat, Inc.
 * Copyright © 2012,2013  Google, Inc.
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
 * Red Hat Author(s): Behdad Esfahbod
 * Google Author(s): Behdad Esfahbod
 */

#include "hb-open-type.hh"
#include "hb-ot-layout.hh"
#include "hb-ot-face.hh"
#include "hb-ot-map.hh"
#include "hb-map.hh"

#include "hb-ot-kern-table.hh"
#include "hb-ot-gasp-table.hh" // Just so we compile it; unused otherwise.
#include "hb-ot-layout-gdef-table.hh"
#include "hb-ot-layout-gsub-table.hh"
#include "hb-ot-layout-gpos-table.hh"
#include "hb-ot-layout-base-table.hh" // Just so we compile it; unused otherwise.
#include "hb-ot-layout-jstf-table.hh" // Just so we compile it; unused otherwise.
#include "hb-ot-name-table.hh"
#include "hb-ot-os2-table.hh"

#include "hb-aat-layout-lcar-table.hh"
#include "hb-aat-layout-morx-table.hh"


/**
 * SECTION:hb-ot-layout
 * @title: hb-ot-layout
 * @short_description: OpenType Layout
 * @include: hb-ot.h
 *
 * Functions for querying OpenType Layout features in the font face.
 **/


/*
 * kern
 */

bool
hb_ot_layout_has_kerning (hb_face_t *face)
{
  return face->table.kern->has_data ();
}

bool
hb_ot_layout_has_machine_kerning (hb_face_t *face)
{
  return face->table.kern->has_state_machine ();
}

bool
hb_ot_layout_has_cross_kerning (hb_face_t *face)
{
  return face->table.kern->has_cross_stream ();
}

void
hb_ot_layout_kern (const hb_ot_shape_plan_t *plan,
                   hb_font_t *font,
                   hb_buffer_t  *buffer)
{
  hb_blob_t *blob = font->face->table.kern.get_blob ();
  const AAT::kern& kern = *blob->as<AAT::kern> ();

  AAT::hb_aat_apply_context_t c (plan, font, buffer, blob);

  kern.apply (&c);
}


/*
 * GDEF
 */

bool
OT::GDEF::is_blacklisted (hb_blob_t *blob,
                          hb_face_t *face) const
{
  /* The ugly business of blacklisting individual fonts' tables happen here!
   * See this thread for why we finally had to bend in and do this:
   * https://lists.freedesktop.org/archives/harfbuzz/2016-February/005489.html
   *
   * In certain versions of Times New Roman Italic and Bold Italic,
   * ASCII double quotation mark U+0022 has wrong glyph class 3 (mark)
   * in GDEF.  Many versions of Tahoma have bad GDEF tables that
   * incorrectly classify some spacing marks such as certain IPA
   * symbols as glyph class 3. So do older versions of Microsoft
   * Himalaya, and the version of Cantarell shipped by Ubuntu 16.04.
   *
   * Nuke the GDEF tables of to avoid unwanted width-zeroing.
   *
   * See https://bugzilla.mozilla.org/show_bug.cgi?id=1279925
   *     https://bugzilla.mozilla.org/show_bug.cgi?id=1279693
   *     https://bugzilla.mozilla.org/show_bug.cgi?id=1279875
   */
#define ENCODE(x,y,z) (((uint64_t) (x) << 48) | ((uint64_t) (y) << 24) | (uint64_t) (z))
  switch ENCODE(blob->length,
                face->table.GSUB->table.get_length (),
                face->table.GPOS->table.get_length ())
  {
    /* sha1sum:c5ee92f0bca4bfb7d06c4d03e8cf9f9cf75d2e8a Windows 7? timesi.ttf */
    case ENCODE (442, 2874, 42038):
    /* sha1sum:37fc8c16a0894ab7b749e35579856c73c840867b Windows 7? timesbi.ttf */
    case ENCODE (430, 2874, 40662):
    /* sha1sum:19fc45110ea6cd3cdd0a5faca256a3797a069a80 Windows 7 timesi.ttf */
    case ENCODE (442, 2874, 39116):
    /* sha1sum:6d2d3c9ed5b7de87bc84eae0df95ee5232ecde26 Windows 7 timesbi.ttf */
    case ENCODE (430, 2874, 39374):
    /* sha1sum:8583225a8b49667c077b3525333f84af08c6bcd8 OS X 10.11.3 Times New Roman Italic.ttf */
    case ENCODE (490, 3046, 41638):
    /* sha1sum:ec0f5a8751845355b7c3271d11f9918a966cb8c9 OS X 10.11.3 Times New Roman Bold Italic.ttf */
    case ENCODE (478, 3046, 41902):
    /* sha1sum:96eda93f7d33e79962451c6c39a6b51ee893ce8c  tahoma.ttf from Windows 8 */
    case ENCODE (898, 12554, 46470):
    /* sha1sum:20928dc06014e0cd120b6fc942d0c3b1a46ac2bc  tahomabd.ttf from Windows 8 */
    case ENCODE (910, 12566, 47732):
    /* sha1sum:4f95b7e4878f60fa3a39ca269618dfde9721a79e  tahoma.ttf from Windows 8.1 */
    case ENCODE (928, 23298, 59332):
    /* sha1sum:6d400781948517c3c0441ba42acb309584b73033  tahomabd.ttf from Windows 8.1 */
    case ENCODE (940, 23310, 60732):
    /* tahoma.ttf v6.04 from Windows 8.1 x64, see https://bugzilla.mozilla.org/show_bug.cgi?id=1279925 */
    case ENCODE (964, 23836, 60072):
    /* tahomabd.ttf v6.04 from Windows 8.1 x64, see https://bugzilla.mozilla.org/show_bug.cgi?id=1279925 */
    case ENCODE (976, 23832, 61456):
    /* sha1sum:e55fa2dfe957a9f7ec26be516a0e30b0c925f846  tahoma.ttf from Windows 10 */
    case ENCODE (994, 24474, 60336):
    /* sha1sum:7199385abb4c2cc81c83a151a7599b6368e92343  tahomabd.ttf from Windows 10 */
    case ENCODE (1006, 24470, 61740):
    /* tahoma.ttf v6.91 from Windows 10 x64, see https://bugzilla.mozilla.org/show_bug.cgi?id=1279925 */
    case ENCODE (1006, 24576, 61346):
    /* tahomabd.ttf v6.91 from Windows 10 x64, see https://bugzilla.mozilla.org/show_bug.cgi?id=1279925 */
    case ENCODE (1018, 24572, 62828):
    /* sha1sum:b9c84d820c49850d3d27ec498be93955b82772b5  tahoma.ttf from Windows 10 AU */
    case ENCODE (1006, 24576, 61352):
    /* sha1sum:2bdfaab28174bdadd2f3d4200a30a7ae31db79d2  tahomabd.ttf from Windows 10 AU */
    case ENCODE (1018, 24572, 62834):
    /* sha1sum:b0d36cf5a2fbe746a3dd277bffc6756a820807a7  Tahoma.ttf from Mac OS X 10.9 */
    case ENCODE (832, 7324, 47162):
    /* sha1sum:12fc4538e84d461771b30c18b5eb6bd434e30fba  Tahoma Bold.ttf from Mac OS X 10.9 */
    case ENCODE (844, 7302, 45474):
    /* sha1sum:eb8afadd28e9cf963e886b23a30b44ab4fd83acc  himalaya.ttf from Windows 7 */
    case ENCODE (180, 13054, 7254):
    /* sha1sum:73da7f025b238a3f737aa1fde22577a6370f77b0  himalaya.ttf from Windows 8 */
    case ENCODE (192, 12638, 7254):
    /* sha1sum:6e80fd1c0b059bbee49272401583160dc1e6a427  himalaya.ttf from Windows 8.1 */
    case ENCODE (192, 12690, 7254):
    /* 8d9267aea9cd2c852ecfb9f12a6e834bfaeafe44  cantarell-fonts-0.0.21/otf/Cantarell-Regular.otf */
    /* 983988ff7b47439ab79aeaf9a45bd4a2c5b9d371  cantarell-fonts-0.0.21/otf/Cantarell-Oblique.otf */
    case ENCODE (188, 248, 3852):
    /* 2c0c90c6f6087ffbfea76589c93113a9cbb0e75f  cantarell-fonts-0.0.21/otf/Cantarell-Bold.otf */
    /* 55461f5b853c6da88069ffcdf7f4dd3f8d7e3e6b  cantarell-fonts-0.0.21/otf/Cantarell-Bold-Oblique.otf */
    case ENCODE (188, 264, 3426):
    /* d125afa82a77a6475ac0e74e7c207914af84b37a padauk-2.80/Padauk.ttf RHEL 7.2 */
    case ENCODE (1058, 47032, 11818):
    /* 0f7b80437227b90a577cc078c0216160ae61b031 padauk-2.80/Padauk-Bold.ttf RHEL 7.2*/
    case ENCODE (1046, 47030, 12600):
    /* d3dde9aa0a6b7f8f6a89ef1002e9aaa11b882290 padauk-2.80/Padauk.ttf Ubuntu 16.04 */
    case ENCODE (1058, 71796, 16770):
    /* 5f3c98ccccae8a953be2d122c1b3a77fd805093f padauk-2.80/Padauk-Bold.ttf Ubuntu 16.04 */
    case ENCODE (1046, 71790, 17862):
    /* 6c93b63b64e8b2c93f5e824e78caca555dc887c7 padauk-2.80/Padauk-book.ttf */
    case ENCODE (1046, 71788, 17112):
    /* d89b1664058359b8ec82e35d3531931125991fb9 padauk-2.80/Padauk-bookbold.ttf */
    case ENCODE (1058, 71794, 17514):
    /* 824cfd193aaf6234b2b4dc0cf3c6ef576c0d00ef padauk-3.0/Padauk-book.ttf */
    case ENCODE (1330, 109904, 57938):
    /* 91fcc10cf15e012d27571e075b3b4dfe31754a8a padauk-3.0/Padauk-bookbold.ttf */
    case ENCODE (1330, 109904, 58972):
    /* sha1sum: c26e41d567ed821bed997e937bc0c41435689e85  Padauk.ttf
     *  "Padauk Regular" "Version 2.5", see https://crbug.com/681813 */
    case ENCODE (1004, 59092, 14836):
      return true;
#undef ENCODE
  }
  return false;
}

static void
_hb_ot_layout_set_glyph_props (hb_font_t *font,
                               hb_buffer_t *buffer)
{
  _hb_buffer_assert_gsubgpos_vars (buffer);

  const OT::GDEF &gdef = *font->face->table.GDEF->table;
  unsigned int count = buffer->len;
  for (unsigned int i = 0; i < count; i++)
  {
    _hb_glyph_info_set_glyph_props (&buffer->info[i], gdef.get_glyph_props (buffer->info[i].codepoint));
    _hb_glyph_info_clear_lig_props (&buffer->info[i]);
    buffer->info[i].syllable() = 0;
  }
}

/* Public API */

hb_bool_t
hb_ot_layout_has_glyph_classes (hb_face_t *face)
{
  return face->table.GDEF->table->has_glyph_classes ();
}

/**
 * hb_ot_layout_get_glyph_class:
 *
 * Since: 0.9.7
 **/
hb_ot_layout_glyph_class_t
hb_ot_layout_get_glyph_class (hb_face_t      *face,
                              hb_codepoint_t  glyph)
{
  return (hb_ot_layout_glyph_class_t) face->table.GDEF->table->get_glyph_class (glyph);
}

/**
 * hb_ot_layout_get_glyphs_in_class:
 *
 * Since: 0.9.7
 **/
void
hb_ot_layout_get_glyphs_in_class (hb_face_t                  *face,
                                  hb_ot_layout_glyph_class_t  klass,
                                  hb_set_t                   *glyphs /* OUT */)
{
  return face->table.GDEF->table->get_glyphs_in_class (klass, glyphs);
}

unsigned int
hb_ot_layout_get_attach_points (hb_face_t      *face,
                                hb_codepoint_t  glyph,
                                unsigned int    start_offset,
                                unsigned int   *point_count /* IN/OUT */,
                                unsigned int   *point_array /* OUT */)
{
  return face->table.GDEF->table->get_attach_points (glyph,
                                                     start_offset,
                                                     point_count,
                                                     point_array);
}

unsigned int
hb_ot_layout_get_ligature_carets (hb_font_t      *font,
                                  hb_direction_t  direction,
                                  hb_codepoint_t  glyph,
                                  unsigned int    start_offset,
                                  unsigned int   *caret_count /* IN/OUT */,
                                  hb_position_t  *caret_array /* OUT */)
{
  unsigned int result_caret_count = 0;
  unsigned int result = font->face->table.GDEF->table->get_lig_carets (font, direction, glyph, start_offset, &result_caret_count, caret_array);
  if (result)
  {
    if (caret_count) *caret_count = result_caret_count;
  }
  else
    result = font->face->table.lcar->get_lig_carets (font, direction, glyph, start_offset, caret_count, caret_array);
  return result;
}


/*
 * GSUB/GPOS
 */

bool
OT::GSUB::is_blacklisted (hb_blob_t *blob HB_UNUSED,
                          hb_face_t *face) const
{
  /* Mac OS X prefers morx over GSUB.  It also ships with various Indic fonts,
   * all by 'MUTF' foundry (Tamil MN, Tamil Sangam MN, etc.), that have broken
   * GSUB/GPOS tables.  Some have GSUB with zero scripts, those are ignored by
   * our morx/GSUB preference code.  But if GSUB has non-zero scripts, we tend
   * to prefer it over morx because we want to be consistent with other OpenType
   * shapers.
   *
   * To work around broken Indic Mac system fonts, we ignore GSUB table if
   * OS/2 VendorId is 'MUTF' and font has morx table as well.
   *
   * https://github.com/harfbuzz/harfbuzz/issues/1410
   * https://github.com/harfbuzz/harfbuzz/issues/1348
   * https://github.com/harfbuzz/harfbuzz/issues/1391
   */
  if (unlikely (face->table.OS2->achVendID == HB_TAG ('M','U','T','F') &&
                face->table.morx->has_data ()))
    return true;

  return false;
}

bool
OT::GPOS::is_blacklisted (hb_blob_t *blob HB_UNUSED,
                          hb_face_t *face HB_UNUSED) const
{
  return false;
}

static const OT::GSUBGPOS&
get_gsubgpos_table (hb_face_t *face,
                    hb_tag_t   table_tag)
{
  switch (table_tag) {
    case HB_OT_TAG_GSUB: return *face->table.GSUB->table;
    case HB_OT_TAG_GPOS: return *face->table.GPOS->table;
    default:             return Null(OT::GSUBGPOS);
  }
}


unsigned int
hb_ot_layout_table_get_script_tags (hb_face_t    *face,
                                    hb_tag_t      table_tag,
                                    unsigned int  start_offset,
                                    unsigned int *script_count /* IN/OUT */,
                                    hb_tag_t     *script_tags  /* OUT */)
{
  const OT::GSUBGPOS &g = get_gsubgpos_table (face, table_tag);

  return g.get_script_tags (start_offset, script_count, script_tags);
}

#define HB_OT_TAG_LATIN_SCRIPT          HB_TAG ('l', 'a', 't', 'n')

hb_bool_t
hb_ot_layout_table_find_script (hb_face_t    *face,
                                hb_tag_t      table_tag,
                                hb_tag_t      script_tag,
                                unsigned int *script_index)
{
  static_assert ((OT::Index::NOT_FOUND_INDEX == HB_OT_LAYOUT_NO_SCRIPT_INDEX), "");
  const OT::GSUBGPOS &g = get_gsubgpos_table (face, table_tag);

  if (g.find_script_index (script_tag, script_index))
    return true;

  /* try finding 'DFLT' */
  if (g.find_script_index (HB_OT_TAG_DEFAULT_SCRIPT, script_index))
    return false;

  /* try with 'dflt'; MS site has had typos and many fonts use it now :(.
   * including many versions of DejaVu Sans Mono! */
  if (g.find_script_index (HB_OT_TAG_DEFAULT_LANGUAGE, script_index))
    return false;

  /* try with 'latn'; some old fonts put their features there even though
     they're really trying to support Thai, for example :( */
  if (g.find_script_index (HB_OT_TAG_LATIN_SCRIPT, script_index))
    return false;

  if (script_index) *script_index = HB_OT_LAYOUT_NO_SCRIPT_INDEX;
  return false;
}

hb_bool_t
hb_ot_layout_table_choose_script (hb_face_t      *face,
                                  hb_tag_t        table_tag,
                                  const hb_tag_t *script_tags,
                                  unsigned int   *script_index,
                                  hb_tag_t       *chosen_script)
{
  const hb_tag_t *t;
  for (t = script_tags; *t; t++);
  return hb_ot_layout_table_select_script (face, table_tag, t - script_tags, script_tags, script_index, chosen_script);
}

/**
 * hb_ot_layout_table_select_script:
 *
 * Since: 2.0.0
 **/
hb_bool_t
hb_ot_layout_table_select_script (hb_face_t      *face,
                                  hb_tag_t        table_tag,
                                  unsigned int    script_count,
                                  const hb_tag_t *script_tags,
                                  unsigned int   *script_index  /* OUT */,
                                  hb_tag_t       *chosen_script /* OUT */)
{
  static_assert ((OT::Index::NOT_FOUND_INDEX == HB_OT_LAYOUT_NO_SCRIPT_INDEX), "");
  const OT::GSUBGPOS &g = get_gsubgpos_table (face, table_tag);
  unsigned int i;

  for (i = 0; i < script_count; i++)
  {
    if (g.find_script_index (script_tags[i], script_index))
    {
      if (chosen_script)
        *chosen_script = script_tags[i];
      return true;
    }
  }

  /* try finding 'DFLT' */
  if (g.find_script_index (HB_OT_TAG_DEFAULT_SCRIPT, script_index)) {
    if (chosen_script)
      *chosen_script = HB_OT_TAG_DEFAULT_SCRIPT;
    return false;
  }

  /* try with 'dflt'; MS site has had typos and many fonts use it now :( */
  if (g.find_script_index (HB_OT_TAG_DEFAULT_LANGUAGE, script_index)) {
    if (chosen_script)
      *chosen_script = HB_OT_TAG_DEFAULT_LANGUAGE;
    return false;
  }

  /* try with 'latn'; some old fonts put their features there even though
     they're really trying to support Thai, for example :( */
  if (g.find_script_index (HB_OT_TAG_LATIN_SCRIPT, script_index)) {
    if (chosen_script)
      *chosen_script = HB_OT_TAG_LATIN_SCRIPT;
    return false;
  }

  if (script_index) *script_index = HB_OT_LAYOUT_NO_SCRIPT_INDEX;
  if (chosen_script)
    *chosen_script = HB_OT_LAYOUT_NO_SCRIPT_INDEX;
  return false;
}

unsigned int
hb_ot_layout_table_get_feature_tags (hb_face_t    *face,
                                     hb_tag_t      table_tag,
                                     unsigned int  start_offset,
                                     unsigned int *feature_count /* IN/OUT */,
                                     hb_tag_t     *feature_tags  /* OUT */)
{
  const OT::GSUBGPOS &g = get_gsubgpos_table (face, table_tag);

  return g.get_feature_tags (start_offset, feature_count, feature_tags);
}

bool
hb_ot_layout_table_find_feature (hb_face_t    *face,
                                 hb_tag_t      table_tag,
                                 hb_tag_t      feature_tag,
                                 unsigned int *feature_index)
{
  static_assert ((OT::Index::NOT_FOUND_INDEX == HB_OT_LAYOUT_NO_FEATURE_INDEX), "");
  const OT::GSUBGPOS &g = get_gsubgpos_table (face, table_tag);

  unsigned int num_features = g.get_feature_count ();
  for (unsigned int i = 0; i < num_features; i++)
  {
    if (feature_tag == g.get_feature_tag (i)) {
      if (feature_index) *feature_index = i;
      return true;
    }
  }

  if (feature_index) *feature_index = HB_OT_LAYOUT_NO_FEATURE_INDEX;
  return false;
}


unsigned int
hb_ot_layout_script_get_language_tags (hb_face_t    *face,
                                       hb_tag_t      table_tag,
                                       unsigned int  script_index,
                                       unsigned int  start_offset,
                                       unsigned int *language_count /* IN/OUT */,
                                       hb_tag_t     *language_tags  /* OUT */)
{
  const OT::Script &s = get_gsubgpos_table (face, table_tag).get_script (script_index);

  return s.get_lang_sys_tags (start_offset, language_count, language_tags);
}

hb_bool_t
hb_ot_layout_script_find_language (hb_face_t    *face,
                                   hb_tag_t      table_tag,
                                   unsigned int  script_index,
                                   hb_tag_t      language_tag,
                                   unsigned int *language_index)
{
  return hb_ot_layout_script_select_language (face,
                                              table_tag,
                                              script_index,
                                              1,
                                              &language_tag,
                                              language_index);
}

/**
 * hb_ot_layout_script_select_language:
 *
 * Since: 2.0.0
 **/
hb_bool_t
hb_ot_layout_script_select_language (hb_face_t      *face,
                                     hb_tag_t        table_tag,
                                     unsigned int    script_index,
                                     unsigned int    language_count,
                                     const hb_tag_t *language_tags,
                                     unsigned int   *language_index /* OUT */)
{
  static_assert ((OT::Index::NOT_FOUND_INDEX == HB_OT_LAYOUT_DEFAULT_LANGUAGE_INDEX), "");
  const OT::Script &s = get_gsubgpos_table (face, table_tag).get_script (script_index);
  unsigned int i;

  for (i = 0; i < language_count; i++)
  {
    if (s.find_lang_sys_index (language_tags[i], language_index))
      return true;
  }

  /* try finding 'dflt' */
  if (s.find_lang_sys_index (HB_OT_TAG_DEFAULT_LANGUAGE, language_index))
    return false;

  if (language_index) *language_index = HB_OT_LAYOUT_DEFAULT_LANGUAGE_INDEX;
  return false;
}

hb_bool_t
hb_ot_layout_language_get_required_feature_index (hb_face_t    *face,
                                                  hb_tag_t      table_tag,
                                                  unsigned int  script_index,
                                                  unsigned int  language_index,
                                                  unsigned int *feature_index)
{
  return hb_ot_layout_language_get_required_feature (face,
                                                     table_tag,
                                                     script_index,
                                                     language_index,
                                                     feature_index,
                                                     nullptr);
}

/**
 * hb_ot_layout_language_get_required_feature:
 *
 * Since: 0.9.30
 **/
hb_bool_t
hb_ot_layout_language_get_required_feature (hb_face_t    *face,
                                            hb_tag_t      table_tag,
                                            unsigned int  script_index,
                                            unsigned int  language_index,
                                            unsigned int *feature_index,
                                            hb_tag_t     *feature_tag)
{
  const OT::GSUBGPOS &g = get_gsubgpos_table (face, table_tag);
  const OT::LangSys &l = g.get_script (script_index).get_lang_sys (language_index);

  unsigned int index = l.get_required_feature_index ();
  if (feature_index) *feature_index = index;
  if (feature_tag) *feature_tag = g.get_feature_tag (index);

  return l.has_required_feature ();
}

unsigned int
hb_ot_layout_language_get_feature_indexes (hb_face_t    *face,
                                           hb_tag_t      table_tag,
                                           unsigned int  script_index,
                                           unsigned int  language_index,
                                           unsigned int  start_offset,
                                           unsigned int *feature_count   /* IN/OUT */,
                                           unsigned int *feature_indexes /* OUT */)
{
  const OT::GSUBGPOS &g = get_gsubgpos_table (face, table_tag);
  const OT::LangSys &l = g.get_script (script_index).get_lang_sys (language_index);

  return l.get_feature_indexes (start_offset, feature_count, feature_indexes);
}

unsigned int
hb_ot_layout_language_get_feature_tags (hb_face_t    *face,
                                        hb_tag_t      table_tag,
                                        unsigned int  script_index,
                                        unsigned int  language_index,
                                        unsigned int  start_offset,
                                        unsigned int *feature_count /* IN/OUT */,
                                        hb_tag_t     *feature_tags  /* OUT */)
{
  const OT::GSUBGPOS &g = get_gsubgpos_table (face, table_tag);
  const OT::LangSys &l = g.get_script (script_index).get_lang_sys (language_index);

  static_assert ((sizeof (unsigned int) == sizeof (hb_tag_t)), "");
  unsigned int ret = l.get_feature_indexes (start_offset, feature_count, (unsigned int *) feature_tags);

  if (feature_tags) {
    unsigned int count = *feature_count;
    for (unsigned int i = 0; i < count; i++)
      feature_tags[i] = g.get_feature_tag ((unsigned int) feature_tags[i]);
  }

  return ret;
}


hb_bool_t
hb_ot_layout_language_find_feature (hb_face_t    *face,
                                    hb_tag_t      table_tag,
                                    unsigned int  script_index,
                                    unsigned int  language_index,
                                    hb_tag_t      feature_tag,
                                    unsigned int *feature_index)
{
  static_assert ((OT::Index::NOT_FOUND_INDEX == HB_OT_LAYOUT_NO_FEATURE_INDEX), "");
  const OT::GSUBGPOS &g = get_gsubgpos_table (face, table_tag);
  const OT::LangSys &l = g.get_script (script_index).get_lang_sys (language_index);

  unsigned int num_features = l.get_feature_count ();
  for (unsigned int i = 0; i < num_features; i++) {
    unsigned int f_index = l.get_feature_index (i);

    if (feature_tag == g.get_feature_tag (f_index)) {
      if (feature_index) *feature_index = f_index;
      return true;
    }
  }

  if (feature_index) *feature_index = HB_OT_LAYOUT_NO_FEATURE_INDEX;
  return false;
}

/**
 * hb_ot_layout_feature_get_lookups:
 *
 * Since: 0.9.7
 **/
unsigned int
hb_ot_layout_feature_get_lookups (hb_face_t    *face,
                                  hb_tag_t      table_tag,
                                  unsigned int  feature_index,
                                  unsigned int  start_offset,
                                  unsigned int *lookup_count   /* IN/OUT */,
                                  unsigned int *lookup_indexes /* OUT */)
{
  return hb_ot_layout_feature_with_variations_get_lookups (face,
                                                           table_tag,
                                                           feature_index,
                                                           HB_OT_LAYOUT_NO_VARIATIONS_INDEX,
                                                           start_offset,
                                                           lookup_count,
                                                           lookup_indexes);
}

/**
 * hb_ot_layout_table_get_lookup_count:
 *
 * Since: 0.9.22
 **/
unsigned int
hb_ot_layout_table_get_lookup_count (hb_face_t    *face,
                                     hb_tag_t      table_tag)
{
  return get_gsubgpos_table (face, table_tag).get_lookup_count ();
}


struct hb_collect_features_context_t
{
  hb_collect_features_context_t (hb_face_t       *face,
                                 hb_tag_t         table_tag,
                                 hb_set_t        *feature_indexes_)
    : g (get_gsubgpos_table (face, table_tag)),
      feature_indexes (feature_indexes_),
      script_count(0),langsys_count(0) {}

  bool visited (const OT::Script &s)
  {
    /* We might have Null() object here.  Don't want to involve
     * that in the memoize.  So, detect empty objects and return. */
    if (unlikely (!s.has_default_lang_sys () &&
                  !s.get_lang_sys_count ()))
      return true;

    if (script_count++ > HB_MAX_SCRIPTS)
      return true;

    return visited (s, visited_script);
  }
  bool visited (const OT::LangSys &l)
  {
    /* We might have Null() object here.  Don't want to involve
     * that in the memoize.  So, detect empty objects and return. */
    if (unlikely (!l.has_required_feature () &&
                  !l.get_feature_count ()))
      return true;

    if (langsys_count++ > HB_MAX_LANGSYS)
      return true;

    return visited (l, visited_langsys);
  }

  private:
  template <typename T>
  bool visited (const T &p, hb_set_t &visited_set)
  {
    hb_codepoint_t delta = (hb_codepoint_t) ((uintptr_t) &p - (uintptr_t) &g);
     if (visited_set.has (delta))
      return true;

    visited_set.add (delta);
    return false;
  }

  public:
  const OT::GSUBGPOS &g;
  hb_set_t           *feature_indexes;

  private:
  hb_set_t visited_script;
  hb_set_t visited_langsys;
  unsigned int script_count;
  unsigned int langsys_count;
};

static void
langsys_collect_features (hb_collect_features_context_t *c,
                          const OT::LangSys  &l,
                          const hb_tag_t     *features)
{
  if (c->visited (l)) return;

  if (!features)
  {
    /* All features. */
    if (l.has_required_feature ())
      c->feature_indexes->add (l.get_required_feature_index ());

    l.add_feature_indexes_to (c->feature_indexes);
  }
  else
  {
    /* Ugh. Any faster way? */
    for (; *features; features++)
    {
      hb_tag_t feature_tag = *features;
      unsigned int num_features = l.get_feature_count ();
      for (unsigned int i = 0; i < num_features; i++)
      {
        unsigned int feature_index = l.get_feature_index (i);

        if (feature_tag == c->g.get_feature_tag (feature_index))
        {
          c->feature_indexes->add (feature_index);
          break;
        }
      }
    }
  }
}

static void
script_collect_features (hb_collect_features_context_t *c,
                         const OT::Script   &s,
                         const hb_tag_t *languages,
                         const hb_tag_t *features)
{
  if (c->visited (s)) return;

  if (!languages)
  {
    /* All languages. */
    if (s.has_default_lang_sys ())
      langsys_collect_features (c,
                                s.get_default_lang_sys (),
                                features);

    unsigned int count = s.get_lang_sys_count ();
    for (unsigned int language_index = 0; language_index < count; language_index++)
      langsys_collect_features (c,
                                s.get_lang_sys (language_index),
                                features);
  }
  else
  {
    for (; *languages; languages++)
    {
      unsigned int language_index;
      if (s.find_lang_sys_index (*languages, &language_index))
        langsys_collect_features (c,
                                  s.get_lang_sys (language_index),
                                  features);
    }
  }
}

/**
 * hb_ot_layout_collect_features:
 *
 * Since: 1.8.5
 **/
void
hb_ot_layout_collect_features (hb_face_t      *face,
                               hb_tag_t        table_tag,
                               const hb_tag_t *scripts,
                               const hb_tag_t *languages,
                               const hb_tag_t *features,
                               hb_set_t       *feature_indexes /* OUT */)
{
  hb_collect_features_context_t c (face, table_tag, feature_indexes);
  if (!scripts)
  {
    /* All scripts. */
    unsigned int count = c.g.get_script_count ();
    for (unsigned int script_index = 0; script_index < count; script_index++)
      script_collect_features (&c,
                               c.g.get_script (script_index),
                               languages,
                               features);
  }
  else
  {
    for (; *scripts; scripts++)
    {
      unsigned int script_index;
      if (c.g.find_script_index (*scripts, &script_index))
        script_collect_features (&c,
                                 c.g.get_script (script_index),
                                 languages,
                                 features);
    }
  }
}

/**
 * hb_ot_layout_collect_lookups:
 *
 * Since: 0.9.8
 **/
void
hb_ot_layout_collect_lookups (hb_face_t      *face,
                              hb_tag_t        table_tag,
                              const hb_tag_t *scripts,
                              const hb_tag_t *languages,
                              const hb_tag_t *features,
                              hb_set_t       *lookup_indexes /* OUT */)
{
  const OT::GSUBGPOS &g = get_gsubgpos_table (face, table_tag);

  hb_set_t feature_indexes;
  hb_ot_layout_collect_features (face, table_tag, scripts, languages, features, &feature_indexes);

  for (hb_codepoint_t feature_index = HB_SET_VALUE_INVALID;
       hb_set_next (&feature_indexes, &feature_index);)
    g.get_feature (feature_index).add_lookup_indexes_to (lookup_indexes);
}

/**
 * hb_ot_layout_lookup_collect_glyphs:
 *
 * Since: 0.9.7
 **/
void
hb_ot_layout_lookup_collect_glyphs (hb_face_t    *face,
                                    hb_tag_t      table_tag,
                                    unsigned int  lookup_index,
                                    hb_set_t     *glyphs_before, /* OUT.  May be NULL */
                                    hb_set_t     *glyphs_input,  /* OUT.  May be NULL */
                                    hb_set_t     *glyphs_after,  /* OUT.  May be NULL */
                                    hb_set_t     *glyphs_output  /* OUT.  May be NULL */)
{
  OT::hb_collect_glyphs_context_t c (face,
                                     glyphs_before,
                                     glyphs_input,
                                     glyphs_after,
                                     glyphs_output);

  switch (table_tag)
  {
    case HB_OT_TAG_GSUB:
    {
      const OT::SubstLookup& l = face->table.GSUB->table->get_lookup (lookup_index);
      l.collect_glyphs (&c);
      return;
    }
    case HB_OT_TAG_GPOS:
    {
      const OT::PosLookup& l = face->table.GPOS->table->get_lookup (lookup_index);
      l.collect_glyphs (&c);
      return;
    }
  }
}


/* Variations support */

hb_bool_t
hb_ot_layout_table_find_feature_variations (hb_face_t    *face,
                                            hb_tag_t      table_tag,
                                            const int    *coords,
                                            unsigned int  num_coords,
                                            unsigned int *variations_index /* out */)
{
  const OT::GSUBGPOS &g = get_gsubgpos_table (face, table_tag);

  return g.find_variations_index (coords, num_coords, variations_index);
}

unsigned int
hb_ot_layout_feature_with_variations_get_lookups (hb_face_t    *face,
                                                  hb_tag_t      table_tag,
                                                  unsigned int  feature_index,
                                                  unsigned int  variations_index,
                                                  unsigned int  start_offset,
                                                  unsigned int *lookup_count /* IN/OUT */,
                                                  unsigned int *lookup_indexes /* OUT */)
{
  static_assert ((OT::FeatureVariations::NOT_FOUND_INDEX == HB_OT_LAYOUT_NO_VARIATIONS_INDEX), "");
  const OT::GSUBGPOS &g = get_gsubgpos_table (face, table_tag);

  const OT::Feature &f = g.get_feature_variation (feature_index, variations_index);

  return f.get_lookup_indexes (start_offset, lookup_count, lookup_indexes);
}


/*
 * OT::GSUB
 */

hb_bool_t
hb_ot_layout_has_substitution (hb_face_t *face)
{
  return face->table.GSUB->table->has_data ();
}

/**
 * hb_ot_layout_lookup_would_substitute:
 *
 * Since: 0.9.7
 **/
hb_bool_t
hb_ot_layout_lookup_would_substitute (hb_face_t            *face,
                                      unsigned int          lookup_index,
                                      const hb_codepoint_t *glyphs,
                                      unsigned int          glyphs_length,
                                      hb_bool_t             zero_context)
{
  return hb_ot_layout_lookup_would_substitute_fast (face,
                                                    lookup_index,
                                                    glyphs, glyphs_length,
                                                    zero_context);
}

bool
hb_ot_layout_lookup_would_substitute_fast (hb_face_t            *face,
                                           unsigned int          lookup_index,
                                           const hb_codepoint_t *glyphs,
                                           unsigned int          glyphs_length,
                                           bool                  zero_context)
{
  if (unlikely (lookup_index >= face->table.GSUB->lookup_count)) return false;
  OT::hb_would_apply_context_t c (face, glyphs, glyphs_length, (bool) zero_context);

  const OT::SubstLookup& l = face->table.GSUB->table->get_lookup (lookup_index);

  return l.would_apply (&c, &face->table.GSUB->accels[lookup_index]);
}

void
hb_ot_layout_substitute_start (hb_font_t    *font,
                               hb_buffer_t  *buffer)
{
_hb_ot_layout_set_glyph_props (font, buffer);
}

void
hb_ot_layout_delete_glyphs_inplace (hb_buffer_t *buffer,
                                    bool (*filter) (const hb_glyph_info_t *info))
{
  /* Merge clusters and delete filtered glyphs.
   * NOTE! We can't use out-buffer as we have positioning data. */
  unsigned int j = 0;
  unsigned int count = buffer->len;
  hb_glyph_info_t *info = buffer->info;
  hb_glyph_position_t *pos = buffer->pos;
  for (unsigned int i = 0; i < count; i++)
  {
    if (filter (&info[i]))
    {
      /* Merge clusters.
       * Same logic as buffer->delete_glyph(), but for in-place removal. */

      unsigned int cluster = info[i].cluster;
      if (i + 1 < count && cluster == info[i + 1].cluster)
        continue; /* Cluster survives; do nothing. */

      if (j)
      {
        /* Merge cluster backward. */
        if (cluster < info[j - 1].cluster)
        {
          unsigned int mask = info[i].mask;
          unsigned int old_cluster = info[j - 1].cluster;
          for (unsigned k = j; k && info[k - 1].cluster == old_cluster; k--)
            buffer->set_cluster (info[k - 1], cluster, mask);
        }
        continue;
      }

      if (i + 1 < count)
        buffer->merge_clusters (i, i + 2); /* Merge cluster forward. */

      continue;
    }

    if (j != i)
    {
      info[j] = info[i];
      pos[j] = pos[i];
    }
    j++;
  }
  buffer->len = j;
}

/**
 * hb_ot_layout_lookup_substitute_closure:
 *
 * Since: 0.9.7
 **/
void
hb_ot_layout_lookup_substitute_closure (hb_face_t    *face,
                                        unsigned int  lookup_index,
                                        hb_set_t     *glyphs)
{
  hb_map_t done_lookups;
  OT::hb_closure_context_t c (face, glyphs, &done_lookups);

  const OT::SubstLookup& l = face->table.GSUB->table->get_lookup (lookup_index);

  l.closure (&c, lookup_index);
}

/**
 * hb_ot_layout_lookups_substitute_closure:
 *
 * Compute the transitive closure of glyphs needed for all of the
 * provided lookups.
 *
 * Since: 1.8.1
 **/
void
hb_ot_layout_lookups_substitute_closure (hb_face_t      *face,
                                         const hb_set_t *lookups,
                                         hb_set_t       *glyphs)
{
  hb_map_t done_lookups;
  OT::hb_closure_context_t c (face, glyphs, &done_lookups);
  const OT::GSUB& gsub = *face->table.GSUB->table;

  unsigned int iteration_count = 0;
  unsigned int glyphs_length;
  do
  {
    glyphs_length = glyphs->get_population ();
    if (lookups != nullptr)
    {
      for (hb_codepoint_t lookup_index = HB_SET_VALUE_INVALID; hb_set_next (lookups, &lookup_index);)
        gsub.get_lookup (lookup_index).closure (&c, lookup_index);
    }
    else
    {
      for (unsigned int i = 0; i < gsub.get_lookup_count (); i++)
        gsub.get_lookup (i).closure (&c, i);
    }
  } while (iteration_count++ <= HB_CLOSURE_MAX_STAGES &&
           glyphs_length != glyphs->get_population ());
}

/*
 * OT::GPOS
 */

hb_bool_t
hb_ot_layout_has_positioning (hb_face_t *face)
{
  return face->table.GPOS->table->has_data ();
}

void
hb_ot_layout_position_start (hb_font_t *font, hb_buffer_t *buffer)
{
  OT::GPOS::position_start (font, buffer);
}

void
hb_ot_layout_position_finish_advances (hb_font_t *font, hb_buffer_t *buffer)
{
  OT::GPOS::position_finish_advances (font, buffer);
}

void
hb_ot_layout_position_finish_offsets (hb_font_t *font, hb_buffer_t *buffer)
{
  OT::GPOS::position_finish_offsets (font, buffer);
}

/**
 * hb_ot_layout_get_size_params:
 *
 * Since: 0.9.10
 **/
hb_bool_t
hb_ot_layout_get_size_params (hb_face_t       *face,
                              unsigned int    *design_size,       /* OUT.  May be NULL */
                              unsigned int    *subfamily_id,      /* OUT.  May be NULL */
                              hb_ot_name_id_t *subfamily_name_id, /* OUT.  May be NULL */
                              unsigned int    *range_start,       /* OUT.  May be NULL */
                              unsigned int    *range_end          /* OUT.  May be NULL */)
{
  const OT::GPOS &gpos = *face->table.GPOS->table;
  const hb_tag_t tag = HB_TAG ('s','i','z','e');

  unsigned int num_features = gpos.get_feature_count ();
  for (unsigned int i = 0; i < num_features; i++)
  {
    if (tag == gpos.get_feature_tag (i))
    {
      const OT::Feature &f = gpos.get_feature (i);
      const OT::FeatureParamsSize &params = f.get_feature_params ().get_size_params (tag);

      if (params.designSize)
      {
        if (design_size) *design_size = params.designSize;
        if (subfamily_id) *subfamily_id = params.subfamilyID;
        if (subfamily_name_id) *subfamily_name_id = params.subfamilyNameID;
        if (range_start) *range_start = params.rangeStart;
        if (range_end) *range_end = params.rangeEnd;

        return true;
      }
    }
  }

  if (design_size) *design_size = 0;
  if (subfamily_id) *subfamily_id = 0;
  if (subfamily_name_id) *subfamily_name_id = HB_OT_NAME_ID_INVALID;
  if (range_start) *range_start = 0;
  if (range_end) *range_end = 0;

  return false;
}

/**
 * hb_ot_layout_feature_get_name_ids:
 * @face: #hb_face_t to work upon
 * @table_tag: table tag to query, "GSUB" or "GPOS".
 * @feature_index: index of feature to query.
 * @label_id: (out) (allow-none): The ‘name’ table name ID that specifies a string
 *            for a user-interface label for this feature. (May be NULL.)
 * @tooltip_id: (out) (allow-none): The ‘name’ table name ID that specifies a string
 *              that an application can use for tooltip text for this
 *              feature. (May be NULL.)
 * @sample_id: (out) (allow-none): The ‘name’ table name ID that specifies sample text
 *             that illustrates the effect of this feature. (May be NULL.)
 * @num_named_parameters: (out) (allow-none):  Number of named parameters. (May be zero.)
 * @first_param_id: (out) (allow-none): The first ‘name’ table name ID used to specify
 *                  strings for user-interface labels for the feature
 *                  parameters. (Must be zero if numParameters is zero.)
 *
 * Fetches name indices from feature parameters for "Stylistic Set" ('ssXX') or
 * "Character Variant" ('cvXX') features.
 *
 * Return value: true if data found, false otherwise
 *
 * Since: 2.0.0
 **/
hb_bool_t
hb_ot_layout_feature_get_name_ids (hb_face_t       *face,
                                   hb_tag_t         table_tag,
                                   unsigned int     feature_index,
                                   hb_ot_name_id_t *label_id,             /* OUT.  May be NULL */
                                   hb_ot_name_id_t *tooltip_id,           /* OUT.  May be NULL */
                                   hb_ot_name_id_t *sample_id,            /* OUT.  May be NULL */
                                   unsigned int    *num_named_parameters, /* OUT.  May be NULL */
                                   hb_ot_name_id_t *first_param_id        /* OUT.  May be NULL */)
{
  const OT::GSUBGPOS &g = get_gsubgpos_table (face, table_tag);

  hb_tag_t feature_tag = g.get_feature_tag (feature_index);
  const OT::Feature &f = g.get_feature (feature_index);

  const OT::FeatureParams &feature_params = f.get_feature_params ();
  if (&feature_params != &Null (OT::FeatureParams))
  {
    const OT::FeatureParamsStylisticSet& ss_params =
      feature_params.get_stylistic_set_params (feature_tag);
    if (&ss_params != &Null (OT::FeatureParamsStylisticSet)) /* ssXX */
    {
      if (label_id) *label_id = ss_params.uiNameID;
      // ssXX features don't have the rest
      if (tooltip_id) *tooltip_id = HB_OT_NAME_ID_INVALID;
      if (sample_id) *sample_id = HB_OT_NAME_ID_INVALID;
      if (num_named_parameters) *num_named_parameters = 0;
      if (first_param_id) *first_param_id = HB_OT_NAME_ID_INVALID;
      return true;
    }
    const OT::FeatureParamsCharacterVariants& cv_params =
      feature_params.get_character_variants_params (feature_tag);
    if (&cv_params != &Null (OT::FeatureParamsCharacterVariants)) /* cvXX */
    {
      if (label_id) *label_id = cv_params.featUILableNameID;
      if (tooltip_id) *tooltip_id = cv_params.featUITooltipTextNameID;
      if (sample_id) *sample_id = cv_params.sampleTextNameID;
      if (num_named_parameters) *num_named_parameters = cv_params.numNamedParameters;
      if (first_param_id) *first_param_id = cv_params.firstParamUILabelNameID;
      return true;
    }
  }

  if (label_id) *label_id = HB_OT_NAME_ID_INVALID;
  if (tooltip_id) *tooltip_id = HB_OT_NAME_ID_INVALID;
  if (sample_id) *sample_id = HB_OT_NAME_ID_INVALID;
  if (num_named_parameters) *num_named_parameters = 0;
  if (first_param_id) *first_param_id = HB_OT_NAME_ID_INVALID;
  return false;
}

/**
 * hb_ot_layout_feature_get_characters:
 * @face: #hb_face_t to work upon
 * @table_tag: table tag to query, "GSUB" or "GPOS".
 * @feature_index: index of feature to query.
 * @start_offset: In case the resulting char_count was equal to its input value, there
 *                is a chance there were more characters on the tag so this API can be
 *                called with an offset till resulting char_count gets to a number
 *                lower than input buffer (or consider using just a bigger buffer for
 *                one shot copying).
 * @char_count: (inout) (allow-none): The count of characters for which this feature
 *              provides glyph variants. (May be zero.)
 * @characters: (out caller-allocates) (array length=char_count): A buffer pointer. The Unicode codepoints
 *              of the characters for which this feature provides glyph variants.
 *
 * Fetches characters listed by designer under feature parameters for "Character
 * Variant" ("cvXX") features.
 *
 * Return value: Number of total sample characters in the cvXX feature.
 *
 * Since: 2.0.0
 **/
unsigned int
hb_ot_layout_feature_get_characters (hb_face_t      *face,
                                     hb_tag_t        table_tag,
                                     unsigned int    feature_index,
                                     unsigned int    start_offset,
                                     unsigned int   *char_count, /* IN/OUT.  May be NULL */
                                     hb_codepoint_t *characters  /* OUT.     May be NULL */)
{
  const OT::GSUBGPOS &g = get_gsubgpos_table (face, table_tag);

  hb_tag_t feature_tag = g.get_feature_tag (feature_index);
  const OT::Feature &f = g.get_feature (feature_index);

  const OT::FeatureParams &feature_params = f.get_feature_params ();

  const OT::FeatureParamsCharacterVariants& cv_params =
    feature_params.get_character_variants_params(feature_tag);

  unsigned int len = 0;
  if (char_count && characters && start_offset < cv_params.characters.len)
  {
    len = MIN (cv_params.characters.len - start_offset, *char_count);
    for (unsigned int i = 0; i < len; ++i)
      characters[i] = cv_params.characters[start_offset + i];
  }
  if (char_count) *char_count = len;
  return cv_params.characters.len;
}


/*
 * Parts of different types are implemented here such that they have direct
 * access to GSUB/GPOS lookups.
 */


struct GSUBProxy
{
  static constexpr unsigned table_index = 0u;
  static constexpr bool inplace = false;
  typedef OT::SubstLookup Lookup;

  GSUBProxy (hb_face_t *face) :
    table (*face->table.GSUB->table),
    accels (face->table.GSUB->accels) {}

  const OT::GSUB &table;
  const OT::hb_ot_layout_lookup_accelerator_t *accels;
};

struct GPOSProxy
{
  static constexpr unsigned table_index = 1u;
  static constexpr bool inplace = true;
  typedef OT::PosLookup Lookup;

  GPOSProxy (hb_face_t *face) :
    table (*face->table.GPOS->table),
    accels (face->table.GPOS->accels) {}

  const OT::GPOS &table;
  const OT::hb_ot_layout_lookup_accelerator_t *accels;
};


static inline bool
apply_forward (OT::hb_ot_apply_context_t *c,
               const OT::hb_ot_layout_lookup_accelerator_t &accel)
{
  bool ret = false;
  hb_buffer_t *buffer = c->buffer;
  while (buffer->idx < buffer->len && buffer->successful)
  {
    bool applied = false;
    if (accel.may_have (buffer->cur().codepoint) &&
        (buffer->cur().mask & c->lookup_mask) &&
        c->check_glyph_property (&buffer->cur(), c->lookup_props))
     {
       applied = accel.apply (c);
     }

    if (applied)
      ret = true;
    else
      buffer->next_glyph ();
  }
  return ret;
}

static inline bool
apply_backward (OT::hb_ot_apply_context_t *c,
               const OT::hb_ot_layout_lookup_accelerator_t &accel)
{
  bool ret = false;
  hb_buffer_t *buffer = c->buffer;
  do
  {
    if (accel.may_have (buffer->cur().codepoint) &&
        (buffer->cur().mask & c->lookup_mask) &&
        c->check_glyph_property (&buffer->cur(), c->lookup_props))
     ret |= accel.apply (c);

    /* The reverse lookup doesn't "advance" cursor (for good reason). */
    buffer->idx--;

  }
  while ((int) buffer->idx >= 0);
  return ret;
}

template <typename Proxy>
static inline void
apply_string (OT::hb_ot_apply_context_t *c,
              const typename Proxy::Lookup &lookup,
              const OT::hb_ot_layout_lookup_accelerator_t &accel)
{
  hb_buffer_t *buffer = c->buffer;

  if (unlikely (!buffer->len || !c->lookup_mask))
    return;

  c->set_lookup_props (lookup.get_props ());

  if (likely (!lookup.is_reverse ()))
  {
    /* in/out forward substitution/positioning */
    if (Proxy::table_index == 0u)
      buffer->clear_output ();
    buffer->idx = 0;

    bool ret;
    ret = apply_forward (c, accel);
    if (ret)
    {
      if (!Proxy::inplace)
        buffer->swap_buffers ();
      else
        assert (!buffer->has_separate_output ());
    }
  }
  else
  {
    /* in-place backward substitution/positioning */
    if (Proxy::table_index == 0u)
      buffer->remove_output ();
    buffer->idx = buffer->len - 1;

    apply_backward (c, accel);
  }
}

template <typename Proxy>
inline void hb_ot_map_t::apply (const Proxy &proxy,
                                const hb_ot_shape_plan_t *plan,
                                hb_font_t *font,
                                hb_buffer_t *buffer) const
{
  const unsigned int table_index = proxy.table_index;
  unsigned int i = 0;
  OT::hb_ot_apply_context_t c (table_index, font, buffer);
  c.set_recurse_func (Proxy::Lookup::apply_recurse_func);

  for (unsigned int stage_index = 0; stage_index < stages[table_index].length; stage_index++) {
    const stage_map_t *stage = &stages[table_index][stage_index];
    for (; i < stage->last_lookup; i++)
    {
      unsigned int lookup_index = lookups[table_index][i].index;
      if (!buffer->message (font, "start lookup %d", lookup_index)) continue;
      c.set_lookup_index (lookup_index);
      c.set_lookup_mask (lookups[table_index][i].mask);
      c.set_auto_zwj (lookups[table_index][i].auto_zwj);
      c.set_auto_zwnj (lookups[table_index][i].auto_zwnj);
      if (lookups[table_index][i].random)
      {
        c.set_random (true);
        buffer->unsafe_to_break_all ();
      }
      apply_string<Proxy> (&c,
                           proxy.table.get_lookup (lookup_index),
                           proxy.accels[lookup_index]);
      (void) buffer->message (font, "end lookup %d", lookup_index);
    }

    if (stage->pause_func)
    {
      buffer->clear_output ();
      stage->pause_func (plan, font, buffer);
    }
  }
}

void hb_ot_map_t::substitute (const hb_ot_shape_plan_t *plan, hb_font_t *font, hb_buffer_t *buffer) const
{
  GSUBProxy proxy (font->face);
  apply (proxy, plan, font, buffer);
}

void hb_ot_map_t::position (const hb_ot_shape_plan_t *plan, hb_font_t *font, hb_buffer_t *buffer) const
{
  GPOSProxy proxy (font->face);
  apply (proxy, plan, font, buffer);
}

void
hb_ot_layout_substitute_lookup (OT::hb_ot_apply_context_t *c,
                                const OT::SubstLookup &lookup,
                                const OT::hb_ot_layout_lookup_accelerator_t &accel)
{
  apply_string<GSUBProxy> (c, lookup, accel);
}

#if 0
static const OT::BASE& _get_base (hb_face_t *face)
{
  return *face->table.BASE;
}

hb_bool_t
hb_ot_layout_get_baseline (hb_font_t               *font,
                           hb_ot_layout_baseline_t  baseline,
                           hb_direction_t           direction,
                           hb_tag_t                 script_tag,
                           hb_tag_t                 language_tag,
                           hb_position_t           *coord        /* OUT.  May be NULL. */)
{
  const OT::BASE &base = _get_base (font->face);
  bool result = base.get_baseline (font, baseline, direction, script_tag,
                                   language_tag, coord);

  /* TODO: Simulate https://docs.microsoft.com/en-us/typography/opentype/spec/baselinetags#ideographic-em-box */
  if (!result && coord) *coord = 0;

  if (coord) *coord = font->em_scale_dir (*coord, direction);

  return result;
}

/* To be moved to public header */
/*
 * BASE
 */

/**
 * hb_ot_layout_baseline_t:
 *
 * https://docs.microsoft.com/en-us/typography/opentype/spec/baselinetags
 *
 * Since: DONTREPLACEME
 */
typedef enum {
  HB_OT_LAYOUT_BASELINE_HANG = HB_TAG('h','a','n','g'),
  HB_OT_LAYOUT_BASELINE_ICFB = HB_TAG('i','c','f','b'),
  HB_OT_LAYOUT_BASELINE_ICFT = HB_TAG('i','c','f','t'),
  HB_OT_LAYOUT_BASELINE_IDEO = HB_TAG('i','d','e','o'),
  HB_OT_LAYOUT_BASELINE_IDTB = HB_TAG('i','d','t','b'),
  HB_OT_LAYOUT_BASELINE_MATH = HB_TAG('m','a','t','h'),
  HB_OT_LAYOUT_BASELINE_ROMN = HB_TAG('r','o','m','n')
} hb_ot_layout_baseline_t;

HB_EXTERN hb_bool_t
hb_ot_layout_get_baseline (hb_font_t               *font,
                           hb_ot_layout_baseline_t  baseline,
                           hb_direction_t           direction,
                           hb_tag_t                 script_tag,
                           hb_tag_t                 language_tag,
                           hb_position_t           *coord        /* OUT.  May be NULL. */);

#endif
