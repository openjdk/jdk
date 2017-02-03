/*
 * Copyright © 2010,2012  Google, Inc.
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

#include "hb-ot-shape-complex-arabic-private.hh"
#include "hb-ot-shape-private.hh"


#ifndef HB_DEBUG_ARABIC
#define HB_DEBUG_ARABIC (HB_DEBUG+0)
#endif


/* buffer var allocations */
#define arabic_shaping_action() complex_var_u8_0() /* arabic shaping action */

#define HB_BUFFER_SCRATCH_FLAG_ARABIC_HAS_STCH HB_BUFFER_SCRATCH_FLAG_COMPLEX0

/* See:
 * https://github.com/behdad/harfbuzz/commit/6e6f82b6f3dde0fc6c3c7d991d9ec6cfff57823d#commitcomment-14248516 */
#define HB_ARABIC_GENERAL_CATEGORY_IS_WORD(gen_cat) \
        (FLAG_SAFE (gen_cat) & \
         (FLAG (HB_UNICODE_GENERAL_CATEGORY_UNASSIGNED) | \
          FLAG (HB_UNICODE_GENERAL_CATEGORY_PRIVATE_USE) | \
          /*FLAG (HB_UNICODE_GENERAL_CATEGORY_LOWERCASE_LETTER) |*/ \
          FLAG (HB_UNICODE_GENERAL_CATEGORY_MODIFIER_LETTER) | \
          FLAG (HB_UNICODE_GENERAL_CATEGORY_OTHER_LETTER) | \
          /*FLAG (HB_UNICODE_GENERAL_CATEGORY_TITLECASE_LETTER) |*/ \
          /*FLAG (HB_UNICODE_GENERAL_CATEGORY_UPPERCASE_LETTER) |*/ \
          FLAG (HB_UNICODE_GENERAL_CATEGORY_SPACING_MARK) | \
          FLAG (HB_UNICODE_GENERAL_CATEGORY_ENCLOSING_MARK) | \
          FLAG (HB_UNICODE_GENERAL_CATEGORY_NON_SPACING_MARK) | \
          FLAG (HB_UNICODE_GENERAL_CATEGORY_DECIMAL_NUMBER) | \
          FLAG (HB_UNICODE_GENERAL_CATEGORY_LETTER_NUMBER) | \
          FLAG (HB_UNICODE_GENERAL_CATEGORY_OTHER_NUMBER) | \
          FLAG (HB_UNICODE_GENERAL_CATEGORY_CURRENCY_SYMBOL) | \
          FLAG (HB_UNICODE_GENERAL_CATEGORY_MODIFIER_SYMBOL) | \
          FLAG (HB_UNICODE_GENERAL_CATEGORY_MATH_SYMBOL) | \
          FLAG (HB_UNICODE_GENERAL_CATEGORY_OTHER_SYMBOL)))


/*
 * Joining types:
 */

/*
 * Bits used in the joining tables
 */
enum hb_arabic_joining_type_t {
  JOINING_TYPE_U                = 0,
  JOINING_TYPE_L                = 1,
  JOINING_TYPE_R                = 2,
  JOINING_TYPE_D                = 3,
  JOINING_TYPE_C                = JOINING_TYPE_D,
  JOINING_GROUP_ALAPH           = 4,
  JOINING_GROUP_DALATH_RISH     = 5,
  NUM_STATE_MACHINE_COLS        = 6,

  JOINING_TYPE_T = 7,
  JOINING_TYPE_X = 8  /* means: use general-category to choose between U or T. */
};

#include "hb-ot-shape-complex-arabic-table.hh"

static unsigned int get_joining_type (hb_codepoint_t u, hb_unicode_general_category_t gen_cat)
{
  unsigned int j_type = joining_type(u);
  if (likely (j_type != JOINING_TYPE_X))
    return j_type;

  return (FLAG_SAFE(gen_cat) &
          (FLAG(HB_UNICODE_GENERAL_CATEGORY_NON_SPACING_MARK) |
           FLAG(HB_UNICODE_GENERAL_CATEGORY_ENCLOSING_MARK) |
           FLAG(HB_UNICODE_GENERAL_CATEGORY_FORMAT))
         ) ?  JOINING_TYPE_T : JOINING_TYPE_U;
}

#define FEATURE_IS_SYRIAC(tag) hb_in_range<unsigned char> ((unsigned char) (tag), '2', '3')

static const hb_tag_t arabic_features[] =
{
  HB_TAG('i','s','o','l'),
  HB_TAG('f','i','n','a'),
  HB_TAG('f','i','n','2'),
  HB_TAG('f','i','n','3'),
  HB_TAG('m','e','d','i'),
  HB_TAG('m','e','d','2'),
  HB_TAG('i','n','i','t'),
  HB_TAG_NONE
};


/* Same order as the feature array */
enum arabic_action_t {
  ISOL,
  FINA,
  FIN2,
  FIN3,
  MEDI,
  MED2,
  INIT,

  NONE,

  ARABIC_NUM_FEATURES = NONE,

  /* We abuse the same byte for other things... */
  STCH_FIXED,
  STCH_REPEATING,
};

static const struct arabic_state_table_entry {
        uint8_t prev_action;
        uint8_t curr_action;
        uint16_t next_state;
} arabic_state_table[][NUM_STATE_MACHINE_COLS] =
{
  /*   jt_U,          jt_L,          jt_R,          jt_D,          jg_ALAPH,      jg_DALATH_RISH */

  /* State 0: prev was U, not willing to join. */
  { {NONE,NONE,0}, {NONE,ISOL,2}, {NONE,ISOL,1}, {NONE,ISOL,2}, {NONE,ISOL,1}, {NONE,ISOL,6}, },

  /* State 1: prev was R or ISOL/ALAPH, not willing to join. */
  { {NONE,NONE,0}, {NONE,ISOL,2}, {NONE,ISOL,1}, {NONE,ISOL,2}, {NONE,FIN2,5}, {NONE,ISOL,6}, },

  /* State 2: prev was D/L in ISOL form, willing to join. */
  { {NONE,NONE,0}, {NONE,ISOL,2}, {INIT,FINA,1}, {INIT,FINA,3}, {INIT,FINA,4}, {INIT,FINA,6}, },

  /* State 3: prev was D in FINA form, willing to join. */
  { {NONE,NONE,0}, {NONE,ISOL,2}, {MEDI,FINA,1}, {MEDI,FINA,3}, {MEDI,FINA,4}, {MEDI,FINA,6}, },

  /* State 4: prev was FINA ALAPH, not willing to join. */
  { {NONE,NONE,0}, {NONE,ISOL,2}, {MED2,ISOL,1}, {MED2,ISOL,2}, {MED2,FIN2,5}, {MED2,ISOL,6}, },

  /* State 5: prev was FIN2/FIN3 ALAPH, not willing to join. */
  { {NONE,NONE,0}, {NONE,ISOL,2}, {ISOL,ISOL,1}, {ISOL,ISOL,2}, {ISOL,FIN2,5}, {ISOL,ISOL,6}, },

  /* State 6: prev was DALATH/RISH, not willing to join. */
  { {NONE,NONE,0}, {NONE,ISOL,2}, {NONE,ISOL,1}, {NONE,ISOL,2}, {NONE,FIN3,5}, {NONE,ISOL,6}, }
};


static void
nuke_joiners (const hb_ot_shape_plan_t *plan,
              hb_font_t *font,
              hb_buffer_t *buffer);

static void
arabic_fallback_shape (const hb_ot_shape_plan_t *plan,
                       hb_font_t *font,
                       hb_buffer_t *buffer);

static void
record_stch (const hb_ot_shape_plan_t *plan,
             hb_font_t *font,
             hb_buffer_t *buffer);

static void
collect_features_arabic (hb_ot_shape_planner_t *plan)
{
  hb_ot_map_builder_t *map = &plan->map;

  /* We apply features according to the Arabic spec, with pauses
   * in between most.
   *
   * The pause between init/medi/... and rlig is required.  See eg:
   * https://bugzilla.mozilla.org/show_bug.cgi?id=644184
   *
   * The pauses between init/medi/... themselves are not necessarily
   * needed as only one of those features is applied to any character.
   * The only difference it makes is when fonts have contextual
   * substitutions.  We now follow the order of the spec, which makes
   * for better experience if that's what Uniscribe is doing.
   *
   * At least for Arabic, looks like Uniscribe has a pause between
   * rlig and calt.  Otherwise the IranNastaliq's ALLAH ligature won't
   * work.  However, testing shows that rlig and calt are applied
   * together for Mongolian in Uniscribe.  As such, we only add a
   * pause for Arabic, not other scripts.
   */

  map->add_gsub_pause (nuke_joiners);

  map->add_global_bool_feature (HB_TAG('s','t','c','h'));
  map->add_gsub_pause (record_stch);

  map->add_global_bool_feature (HB_TAG('c','c','m','p'));
  map->add_global_bool_feature (HB_TAG('l','o','c','l'));

  map->add_gsub_pause (NULL);

  for (unsigned int i = 0; i < ARABIC_NUM_FEATURES; i++)
  {
    bool has_fallback = plan->props.script == HB_SCRIPT_ARABIC && !FEATURE_IS_SYRIAC (arabic_features[i]);
    map->add_feature (arabic_features[i], 1, has_fallback ? F_HAS_FALLBACK : F_NONE);
    map->add_gsub_pause (NULL);
  }

  map->add_feature (HB_TAG('r','l','i','g'), 1, F_GLOBAL|F_HAS_FALLBACK);
  if (plan->props.script == HB_SCRIPT_ARABIC)
    map->add_gsub_pause (arabic_fallback_shape);

  map->add_global_bool_feature (HB_TAG('c','a','l','t'));

  /* The spec includes 'cswh'.  Earlier versions of Windows
   * used to enable this by default, but testing suggests
   * that Windows 8 and later do not enable it by default,
   * and spec now says 'Off by default'.
   * We disabled this in ae23c24c32.
   * Note that IranNastaliq uses this feature extensively
   * to fixup broken glyph sequences.  Oh well...
   * Test case: U+0643,U+0640,U+0631. */
  //map->add_gsub_pause (NULL);
  //map->add_global_bool_feature (HB_TAG('c','s','w','h'));
  map->add_global_bool_feature (HB_TAG('m','s','e','t'));
}

#include "hb-ot-shape-complex-arabic-fallback.hh"

struct arabic_shape_plan_t
{
  ASSERT_POD ();

  /* The "+ 1" in the next array is to accommodate for the "NONE" command,
   * which is not an OpenType feature, but this simplifies the code by not
   * having to do a "if (... < NONE) ..." and just rely on the fact that
   * mask_array[NONE] == 0. */
  hb_mask_t mask_array[ARABIC_NUM_FEATURES + 1];

  arabic_fallback_plan_t *fallback_plan;

  unsigned int do_fallback : 1;
  unsigned int has_stch : 1;
};

void *
data_create_arabic (const hb_ot_shape_plan_t *plan)
{
  arabic_shape_plan_t *arabic_plan = (arabic_shape_plan_t *) calloc (1, sizeof (arabic_shape_plan_t));
  if (unlikely (!arabic_plan))
    return NULL;

  arabic_plan->do_fallback = plan->props.script == HB_SCRIPT_ARABIC;
  arabic_plan->has_stch = !!plan->map.get_1_mask (HB_TAG ('s','t','c','h'));
  for (unsigned int i = 0; i < ARABIC_NUM_FEATURES; i++) {
    arabic_plan->mask_array[i] = plan->map.get_1_mask (arabic_features[i]);
    arabic_plan->do_fallback = arabic_plan->do_fallback &&
                               (FEATURE_IS_SYRIAC (arabic_features[i]) ||
                                plan->map.needs_fallback (arabic_features[i]));
  }

  return arabic_plan;
}

void
data_destroy_arabic (void *data)
{
  arabic_shape_plan_t *arabic_plan = (arabic_shape_plan_t *) data;

  arabic_fallback_plan_destroy (arabic_plan->fallback_plan);

  free (data);
}

static void
arabic_joining (hb_buffer_t *buffer)
{
  unsigned int count = buffer->len;
  hb_glyph_info_t *info = buffer->info;
  unsigned int prev = (unsigned int) -1, state = 0;

  /* Check pre-context */
  for (unsigned int i = 0; i < buffer->context_len[0]; i++)
  {
    unsigned int this_type = get_joining_type (buffer->context[0][i], buffer->unicode->general_category (buffer->context[0][i]));

    if (unlikely (this_type == JOINING_TYPE_T))
      continue;

    const arabic_state_table_entry *entry = &arabic_state_table[state][this_type];
    state = entry->next_state;
    break;
  }

  for (unsigned int i = 0; i < count; i++)
  {
    unsigned int this_type = get_joining_type (info[i].codepoint, _hb_glyph_info_get_general_category (&info[i]));

    if (unlikely (this_type == JOINING_TYPE_T)) {
      info[i].arabic_shaping_action() = NONE;
      continue;
    }

    const arabic_state_table_entry *entry = &arabic_state_table[state][this_type];

    if (entry->prev_action != NONE && prev != (unsigned int) -1)
      info[prev].arabic_shaping_action() = entry->prev_action;

    info[i].arabic_shaping_action() = entry->curr_action;

    prev = i;
    state = entry->next_state;
  }

  for (unsigned int i = 0; i < buffer->context_len[1]; i++)
  {
    unsigned int this_type = get_joining_type (buffer->context[1][i], buffer->unicode->general_category (buffer->context[1][i]));

    if (unlikely (this_type == JOINING_TYPE_T))
      continue;

    const arabic_state_table_entry *entry = &arabic_state_table[state][this_type];
    if (entry->prev_action != NONE && prev != (unsigned int) -1)
      info[prev].arabic_shaping_action() = entry->prev_action;
    break;
  }
}

static void
mongolian_variation_selectors (hb_buffer_t *buffer)
{
  /* Copy arabic_shaping_action() from base to Mongolian variation selectors. */
  unsigned int count = buffer->len;
  hb_glyph_info_t *info = buffer->info;
  for (unsigned int i = 1; i < count; i++)
    if (unlikely (hb_in_range (info[i].codepoint, 0x180Bu, 0x180Du)))
      info[i].arabic_shaping_action() = info[i - 1].arabic_shaping_action();
}

void
setup_masks_arabic_plan (const arabic_shape_plan_t *arabic_plan,
                         hb_buffer_t               *buffer,
                         hb_script_t                script)
{
  HB_BUFFER_ALLOCATE_VAR (buffer, arabic_shaping_action);

  arabic_joining (buffer);
  if (script == HB_SCRIPT_MONGOLIAN)
    mongolian_variation_selectors (buffer);

  unsigned int count = buffer->len;
  hb_glyph_info_t *info = buffer->info;
  for (unsigned int i = 0; i < count; i++)
    info[i].mask |= arabic_plan->mask_array[info[i].arabic_shaping_action()];
}

static void
setup_masks_arabic (const hb_ot_shape_plan_t *plan,
                    hb_buffer_t              *buffer,
                    hb_font_t                *font HB_UNUSED)
{
  const arabic_shape_plan_t *arabic_plan = (const arabic_shape_plan_t *) plan->data;
  setup_masks_arabic_plan (arabic_plan, buffer, plan->props.script);
}


static void
nuke_joiners (const hb_ot_shape_plan_t *plan HB_UNUSED,
              hb_font_t *font HB_UNUSED,
              hb_buffer_t *buffer)
{
  unsigned int count = buffer->len;
  hb_glyph_info_t *info = buffer->info;
  for (unsigned int i = 0; i < count; i++)
    if (_hb_glyph_info_is_zwj (&info[i]))
      _hb_glyph_info_flip_joiners (&info[i]);
}

static void
arabic_fallback_shape (const hb_ot_shape_plan_t *plan,
                       hb_font_t *font,
                       hb_buffer_t *buffer)
{
  const arabic_shape_plan_t *arabic_plan = (const arabic_shape_plan_t *) plan->data;

  if (!arabic_plan->do_fallback)
    return;

retry:
  arabic_fallback_plan_t *fallback_plan = (arabic_fallback_plan_t *) hb_atomic_ptr_get (&arabic_plan->fallback_plan);
  if (unlikely (!fallback_plan))
  {
    /* This sucks.  We need a font to build the fallback plan... */
    fallback_plan = arabic_fallback_plan_create (plan, font);
    if (unlikely (!hb_atomic_ptr_cmpexch (&(const_cast<arabic_shape_plan_t *> (arabic_plan))->fallback_plan, NULL, fallback_plan))) {
      arabic_fallback_plan_destroy (fallback_plan);
      goto retry;
    }
  }

  arabic_fallback_plan_shape (fallback_plan, font, buffer);
}

/*
 * Stretch feature: "stch".
 * See example here:
 * https://www.microsoft.com/typography/OpenTypeDev/syriac/intro.htm
 * We implement this in a generic way, such that the Arabic subtending
 * marks can use it as well.
 */

static void
record_stch (const hb_ot_shape_plan_t *plan,
             hb_font_t *font,
             hb_buffer_t *buffer)
{
  const arabic_shape_plan_t *arabic_plan = (const arabic_shape_plan_t *) plan->data;
  if (!arabic_plan->has_stch)
    return;

  /* 'stch' feature was just applied.  Look for anything that multiplied,
   * and record it for stch treatment later.  Note that rtlm, frac, etc
   * are applied before stch, but we assume that they didn't result in
   * anything multiplying into 5 pieces, so it's safe-ish... */

  unsigned int count = buffer->len;
  hb_glyph_info_t *info = buffer->info;
  for (unsigned int i = 0; i < count; i++)
    if (unlikely (_hb_glyph_info_multiplied (&info[i])))
    {
      unsigned int comp = _hb_glyph_info_get_lig_comp (&info[i]);
      info[i].arabic_shaping_action() = comp % 2 ? STCH_REPEATING : STCH_FIXED;
      buffer->scratch_flags |= HB_BUFFER_SCRATCH_FLAG_ARABIC_HAS_STCH;
    }
}

static void
apply_stch (const hb_ot_shape_plan_t *plan,
            hb_buffer_t              *buffer,
            hb_font_t                *font)
{
  if (likely (!(buffer->scratch_flags & HB_BUFFER_SCRATCH_FLAG_ARABIC_HAS_STCH)))
    return;

  /* The Arabic shaper currently always processes in RTL mode, so we should
   * stretch / position the stretched pieces to the left / preceding glyphs. */

  /* We do a two pass implementation:
   * First pass calculates the exact number of extra glyphs we need,
   * We then enlarge buffer to have that much room,
   * Second pass applies the stretch, copying things to the end of buffer.
   */

  int sign = font->x_scale < 0 ? -1 : +1;
  unsigned int extra_glyphs_needed = 0; // Set during MEASURE, used during CUT
  typedef enum { MEASURE, CUT } step_t;

  for (step_t step = MEASURE; step <= CUT; step = (step_t) (step + 1))
  {
    unsigned int count = buffer->len;
    hb_glyph_info_t *info = buffer->info;
    hb_glyph_position_t *pos = buffer->pos;
    unsigned int new_len = count + extra_glyphs_needed; // write head during CUT
    unsigned int j = new_len;
    for (unsigned int i = count; i; i--)
    {
      if (!hb_in_range<unsigned> (info[i - 1].arabic_shaping_action(), STCH_FIXED, STCH_REPEATING))
      {
        if (step == CUT)
        {
          --j;
          info[j] = info[i - 1];
          pos[j] = pos[i - 1];
        }
        continue;
      }

      /* Yay, justification! */

      hb_position_t w_total = 0; // Total to be filled
      hb_position_t w_fixed = 0; // Sum of fixed tiles
      hb_position_t w_repeating = 0; // Sum of repeating tiles
      int n_fixed = 0;
      int n_repeating = 0;

      unsigned int end = i;
      while (i &&
             hb_in_range<unsigned> (info[i - 1].arabic_shaping_action(), STCH_FIXED, STCH_REPEATING))
      {
        i--;
        hb_position_t width = font->get_glyph_h_advance (info[i].codepoint);
        if (info[i].arabic_shaping_action() == STCH_FIXED)
        {
          w_fixed += width;
          n_fixed++;
        }
        else
        {
          w_repeating += width;
          n_repeating++;
        }
      }
      unsigned int start = i;
      unsigned int context = i;
      while (context &&
             !hb_in_range<unsigned> (info[context - 1].arabic_shaping_action(), STCH_FIXED, STCH_REPEATING) &&
             (_hb_glyph_info_is_default_ignorable (&info[context - 1]) ||
              HB_ARABIC_GENERAL_CATEGORY_IS_WORD (_hb_glyph_info_get_general_category (&info[context - 1]))))
      {
        context--;
        w_total += pos[context].x_advance;
      }
      i++; // Don't touch i again.

      DEBUG_MSG (ARABIC, NULL, "%s stretch at (%d,%d,%d)",
                 step == MEASURE ? "measuring" : "cutting", context, start, end);
      DEBUG_MSG (ARABIC, NULL, "rest of word:    count=%d width %d", start - context, w_total);
      DEBUG_MSG (ARABIC, NULL, "fixed tiles:     count=%d width=%d", n_fixed, w_fixed);
      DEBUG_MSG (ARABIC, NULL, "repeating tiles: count=%d width=%d", n_repeating, w_repeating);

      /* Number of additional times to repeat each repeating tile. */
      int n_copies = 0;

      hb_position_t w_remaining = w_total - w_fixed;
      if (sign * w_remaining > sign * w_repeating && sign * w_repeating > 0)
        n_copies = (sign * w_remaining) / (sign * w_repeating) - 1;

      /* See if we can improve the fit by adding an extra repeat and squeezing them together a bit. */
      hb_position_t extra_repeat_overlap = 0;
      hb_position_t shortfall = sign * w_remaining - sign * w_repeating * (n_copies + 1);
      if (shortfall > 0)
      {
        ++n_copies;
        hb_position_t excess = (n_copies + 1) * sign * w_repeating - sign * w_remaining;
        if (excess > 0)
          extra_repeat_overlap = excess / (n_copies * n_repeating);
      }

      if (step == MEASURE)
      {
        extra_glyphs_needed += n_copies * n_repeating;
        DEBUG_MSG (ARABIC, NULL, "will add extra %d copies of repeating tiles", n_copies);
      }
      else
      {
        hb_position_t x_offset = 0;
        for (unsigned int k = end; k > start; k--)
        {
          hb_position_t width = font->get_glyph_h_advance (info[k - 1].codepoint);

          unsigned int repeat = 1;
          if (info[k - 1].arabic_shaping_action() == STCH_REPEATING)
            repeat += n_copies;

          DEBUG_MSG (ARABIC, NULL, "appending %d copies of glyph %d; j=%d",
                     repeat, info[k - 1].codepoint, j);
          for (unsigned int n = 0; n < repeat; n++)
          {
            x_offset -= width;
            if (n > 0)
              x_offset += extra_repeat_overlap;
            pos[k - 1].x_offset = x_offset;
            /* Append copy. */
            --j;
            info[j] = info[k - 1];
            pos[j] = pos[k - 1];
          }
        }
      }
    }

    if (step == MEASURE)
    {
      if (unlikely (!buffer->ensure (count + extra_glyphs_needed)))
        break;
    }
    else
    {
      assert (j == 0);
      buffer->len = new_len;
    }
  }
}


static void
postprocess_glyphs_arabic (const hb_ot_shape_plan_t *plan,
                           hb_buffer_t              *buffer,
                           hb_font_t                *font)
{
  apply_stch (plan, buffer, font);

  HB_BUFFER_DEALLOCATE_VAR (buffer, arabic_shaping_action);
}

const hb_ot_complex_shaper_t _hb_ot_complex_shaper_arabic =
{
  "arabic",
  collect_features_arabic,
  NULL, /* override_features */
  data_create_arabic,
  data_destroy_arabic,
  NULL, /* preprocess_text */
  postprocess_glyphs_arabic,
  HB_OT_SHAPE_NORMALIZATION_MODE_DEFAULT,
  NULL, /* decompose */
  NULL, /* compose */
  setup_masks_arabic,
  NULL, /* disable_otl */
  HB_OT_SHAPE_ZERO_WIDTH_MARKS_BY_GDEF_LATE,
  true, /* fallback_position */
};
