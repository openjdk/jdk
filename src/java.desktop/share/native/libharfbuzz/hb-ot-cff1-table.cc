/*
 * Copyright © 2018 Adobe Inc.
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
 * Adobe Author(s): Michiharu Ariza
 */

#include "hb-ot-cff1-table.hh"
#include "hb-cff1-interp-cs.hh"

using namespace CFF;

/* SID to code */
static const uint8_t standard_encoding_to_code [] =
{
    0,   32,   33,   34,   35,   36,   37,   38,  39,   40,   41,   42,   43,   44,   45,   46,
   47,   48,   49,   50,   51,   52,   53,   54,  55,   56,   57,   58,   59,   60,   61,   62,
   63,   64,   65,   66,   67,   68,   69,   70,  71,   72,   73,   74,   75,   76,   77,   78,
   79,   80,   81,   82,   83,   84,   85,   86,  87,   88,   89,   90,   91,   92,   93,   94,
   95,   96,   97,   98,   99,  100,  101,  102, 103,  104,  105,  106,  107,  108,  109,  110,
  111,  112,  113,  114,  115,  116,  117,  118, 119,  120,  121,  122,  123,  124,  125,  126,
  161,  162,  163,  164,  165,  166,  167,  168, 169,  170,  171,  172,  173,  174,  175,  177,
  178,  179,  180,  182,  183,  184,  185,  186, 187,  188,  189,  191,  193,  194,  195,  196,
  197,  198,  199,  200,  202,  203,  205,  206, 207,  208,  225,  227,  232,  233,  234,  235,
  241,  245,  248,  249,  250,  251
};

/* SID to code */
static const uint8_t expert_encoding_to_code [] =
{
    0,   32,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,   44,   45,   46,
    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,   58,   59,    0,    0,    0,
    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,
    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,
    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,
    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,
    0,    0,    0,   47,    0,    0,    0,    0,    0,    0,    0,    0,    0,   87,   88,    0,
    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,
    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,
    0,    0,    0,    0,    0,    0,  201,    0,    0,    0,    0,  189,    0,    0,  188,    0,
    0,    0,    0,  190,  202,    0,    0,    0,    0,  203,    0,    0,    0,    0,    0,    0,
    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,
    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,
    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,
    0,    0,    0,    0,    0,   33,   34,   36,   37,   38,   39,   40,   41,   42,   43,   48,
   49,   50,   51,   52,   53,   54,   55,   56,   57,   60,   61,   62,   63,   65,   66,   67,
   68,   69,   73,   76,   77,   78,   79,   82,   83,   84,   86,   89,   90,   91,   93,   94,
   95,   96,   97,   98,   99,  100,  101,  102,  103,  104,  105,  106,  107,  108,  109,  110,
  111,  112,  113,  114,  115,  116,  117,  118,  119,  120,  121,  122,  123,  124,  125,  126,
  161,  162,  163,  166,  167,  168,  169,  170,  172,  175,  178,  179,  182,  183,  184,  191,
  192,  193,  194,  195,  196,  197,  200,  204,  205,  206,  207,  208,  209,  210,  211,  212,
  213,  214,  215,  216,  217,  218,  219,  220,  221,  222,  223,  224,  225,  226,  227,  228,
  229,  230,  231,  232,  233,  234,  235,  236,  237,  238,  239,  240,  241,  242,  243,  244,
  245,  246,  247,  248,  249,  250,  251,  252,  253,  254,  255
};

/* glyph ID to SID */
static const uint16_t expert_charset_to_sid [] =
{
    0,    1,  229,  230,  231,  232,  233,  234,  235,  236,  237,  238,   13,   14,   15,   99,
  239,  240,  241,  242,  243,  244,  245,  246,  247,  248,   27,   28,  249,  250,  251,  252,
  253,  254,  255,  256,  257,  258,  259,  260,  261,  262,  263,  264,  265,  266,  109,  110,
  267,  268,  269,  270,  271,  272,  273,  274,  275,  276,  277,  278,  279,  280,  281,  282,
  283,  284,  285,  286,  287,  288,  289,  290,  291,  292,  293,  294,  295,  296,  297,  298,
  299,  300,  301,  302,  303,  304,  305,  306,  307,  308,  309,  310,  311,  312,  313,  314,
  315,  316,  317,  318,  158,  155,  163,  319,  320,  321,  322,  323,  324,  325,  326,  150,
  164,  169,  327,  328,  329,  330,  331,  332,  333,  334,  335,  336,  337,  338,  339,  340,
  341,  342,  343,  344,  345,  346,  347,  348,  349,  350,  351,  352,  353,  354,  355,  356,
  357,  358,  359,  360,  361,  362,  363,  364,  365,  366,  367,  368,  369,  370,  371,  372,
  373,  374,  375,  376,  377,  378
};

/* glyph ID to SID */
static const uint16_t expert_subset_charset_to_sid [] =
{
    0,    1,  231,  232,  235,  236,  237,  238,   13,   14,   15,   99,  239,  240,  241,  242,
  243,  244,  245,  246,  247,  248,   27,   28,  249,  250,  251,  253,  254,  255,  256,  257,
  258,  259,  260,  261,  262,  263,  264,  265,  266,  109,  110,  267,  268,  269,  270,  272,
  300,  301,  302,  305,  314,  315,  158,  155,  163,  320,  321,  322,  323,  324,  325,  326,
  150,  164,  169,  327,  328,  329,  330,  331,  332,  333,  334,  335,  336,  337,  338,  339,
  340,  341,  342,  343,  344,  345,  346
};

/* code to SID */
static const uint8_t standard_encoding_to_sid [] =
{
    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,
    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,
    1,    2,    3,    4,    5,    6,    7,    8,    9,   10,   11,   12,   13,   14,   15,   16,
    17,  18,   19,   20,   21,   22,   23,   24,   25,   26,   27,   28,   29,   30,   31,   32,
    33,  34,   35,   36,   37,   38,   39,   40,   41,   42,   43,   44,   45,   46,   47,   48,
    49,  50,   51,   52,   53,   54,   55,   56,   57,   58,   59,   60,   61,   62,   63,   64,
    65,  66,   67,   68,   69,   70,   71,   72,   73,   74,   75,   76,   77,   78,   79,   80,
    81,  82,   83,   84,   85,   86,   87,   88,   89,   90,   91,   92,   93,   94,   95,    0,
    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,
    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,
    0,   96,   97,   98,   99,  100,  101,  102,  103,  104,  105,  106,  107,  108,  109,  110,
    0,  111,  112,  113,  114,    0,  115,  116,  117,  118,  119,  120,  121,  122,    0,  123,
    0,  124,  125,  126,  127,  128,  129,  130,  131,    0,  132,  133,    0,  134,  135,  136,
  137,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,
    0,   138,   0,  139,    0,    0,    0,    0,  140,  141,  142,  143,    0,    0,    0,    0,
    0,   144,   0,    0,    0,  145,    0,    0,  146,  147,  148,  149,    0,    0,    0,    0
};

hb_codepoint_t OT::cff1::lookup_standard_encoding_for_code (hb_codepoint_t sid)
{
  if (sid < ARRAY_LENGTH (standard_encoding_to_code))
    return (hb_codepoint_t)standard_encoding_to_code[sid];
  else
    return 0;
}

hb_codepoint_t OT::cff1::lookup_expert_encoding_for_code (hb_codepoint_t sid)
{
  if (sid < ARRAY_LENGTH (expert_encoding_to_code))
    return (hb_codepoint_t)expert_encoding_to_code[sid];
  else
    return 0;
}

hb_codepoint_t OT::cff1::lookup_expert_charset_for_sid (hb_codepoint_t glyph)
{
  if (glyph < ARRAY_LENGTH (expert_charset_to_sid))
    return (hb_codepoint_t)expert_charset_to_sid[glyph];
  else
    return 0;
}

hb_codepoint_t OT::cff1::lookup_expert_subset_charset_for_sid (hb_codepoint_t glyph)
{
  if (glyph < ARRAY_LENGTH (expert_subset_charset_to_sid))
    return (hb_codepoint_t)expert_subset_charset_to_sid[glyph];
  else
    return 0;
}

hb_codepoint_t OT::cff1::lookup_standard_encoding_for_sid (hb_codepoint_t code)
{
  if (code < ARRAY_LENGTH (standard_encoding_to_sid))
    return (hb_codepoint_t)standard_encoding_to_sid[code];
  else
    return CFF_UNDEF_SID;
}

struct bounds_t
{
  void init ()
  {
    min.set_int (0x7FFFFFFF, 0x7FFFFFFF);
    max.set_int (-0x80000000, -0x80000000);
  }

  void update (const point_t &pt)
  {
    if (pt.x < min.x) min.x = pt.x;
    if (pt.x > max.x) max.x = pt.x;
    if (pt.y < min.y) min.y = pt.y;
    if (pt.y > max.y) max.y = pt.y;
  }

  void merge (const bounds_t &b)
  {
    if (empty ())
      *this = b;
    else if (!b.empty ())
    {
      if (b.min.x < min.x) min.x = b.min.x;
      if (b.max.x > max.x) max.x = b.max.x;
      if (b.min.y < min.y) min.y = b.min.y;
      if (b.max.y > max.y) max.y = b.max.y;
    }
  }

  void offset (const point_t &delta)
  {
    if (!empty ())
    {
      min.move (delta);
      max.move (delta);
    }
  }

  bool empty () const
  { return (min.x >= max.x) || (min.y >= max.y); }

  point_t min;
  point_t max;
};

struct extents_param_t
{
  void init (const OT::cff1::accelerator_t *_cff)
  {
    path_open = false;
    cff = _cff;
    bounds.init ();
  }

  void start_path ()         { path_open = true; }
  void end_path ()           { path_open = false; }
  bool is_path_open () const { return path_open; }

  bool    path_open;
  bounds_t  bounds;

  const OT::cff1::accelerator_t *cff;
};

struct cff1_path_procs_extents_t : path_procs_t<cff1_path_procs_extents_t, cff1_cs_interp_env_t, extents_param_t>
{
  static void moveto (cff1_cs_interp_env_t &env, extents_param_t& param, const point_t &pt)
  {
    param.end_path ();
    env.moveto (pt);
  }

  static void line (cff1_cs_interp_env_t &env, extents_param_t& param, const point_t &pt1)
  {
    if (!param.is_path_open ())
    {
      param.start_path ();
      param.bounds.update (env.get_pt ());
    }
    env.moveto (pt1);
    param.bounds.update (env.get_pt ());
  }

  static void curve (cff1_cs_interp_env_t &env, extents_param_t& param, const point_t &pt1, const point_t &pt2, const point_t &pt3)
  {
    if (!param.is_path_open ())
    {
      param.start_path ();
      param.bounds.update (env.get_pt ());
    }
    /* include control points */
    param.bounds.update (pt1);
    param.bounds.update (pt2);
    env.moveto (pt3);
    param.bounds.update (env.get_pt ());
  }
};

static bool _get_bounds (const OT::cff1::accelerator_t *cff, hb_codepoint_t glyph, bounds_t &bounds, bool in_seac=false);

struct cff1_cs_opset_extents_t : cff1_cs_opset_t<cff1_cs_opset_extents_t, extents_param_t, cff1_path_procs_extents_t>
{
  static void process_seac (cff1_cs_interp_env_t &env, extents_param_t& param)
  {
    unsigned int  n = env.argStack.get_count ();
    point_t delta;
    delta.x = env.argStack[n-4];
    delta.y = env.argStack[n-3];
    hb_codepoint_t base = param.cff->std_code_to_glyph (env.argStack[n-2].to_int ());
    hb_codepoint_t accent = param.cff->std_code_to_glyph (env.argStack[n-1].to_int ());

    bounds_t  base_bounds, accent_bounds;
    if (likely (!env.in_seac && base && accent
               && _get_bounds (param.cff, base, base_bounds, true)
               && _get_bounds (param.cff, accent, accent_bounds, true)))
    {
      param.bounds.merge (base_bounds);
      accent_bounds.offset (delta);
      param.bounds.merge (accent_bounds);
    }
    else
      env.set_error ();
  }
};

bool _get_bounds (const OT::cff1::accelerator_t *cff, hb_codepoint_t glyph, bounds_t &bounds, bool in_seac)
{
  bounds.init ();
  if (unlikely (!cff->is_valid () || (glyph >= cff->num_glyphs))) return false;

  unsigned int fd = cff->fdSelect->get_fd (glyph);
  cff1_cs_interpreter_t<cff1_cs_opset_extents_t, extents_param_t> interp;
  const byte_str_t str = (*cff->charStrings)[glyph];
  interp.env.init (str, *cff, fd);
  interp.env.set_in_seac (in_seac);
  extents_param_t  param;
  param.init (cff);
  if (unlikely (!interp.interpret (param))) return false;
  bounds = param.bounds;
  return true;
}

bool OT::cff1::accelerator_t::get_extents (hb_codepoint_t glyph, hb_glyph_extents_t *extents) const
{
  bounds_t  bounds;

  if (!_get_bounds (this, glyph, bounds))
    return false;

  if (bounds.min.x >= bounds.max.x)
  {
    extents->width = 0;
    extents->x_bearing = 0;
  }
  else
  {
    extents->x_bearing = (int32_t)bounds.min.x.floor ();
    extents->width = (int32_t)bounds.max.x.ceil () - extents->x_bearing;
  }
  if (bounds.min.y >= bounds.max.y)
  {
    extents->height = 0;
    extents->y_bearing = 0;
  }
  else
  {
    extents->y_bearing = (int32_t)bounds.max.y.ceil ();
    extents->height = (int32_t)bounds.min.y.floor () - extents->y_bearing;
  }

  return true;
}

struct get_seac_param_t
{
  void init (const OT::cff1::accelerator_t *_cff)
  {
    cff = _cff;
    base = 0;
    accent = 0;
  }

  bool has_seac () const { return base && accent; }

  const OT::cff1::accelerator_t *cff;
  hb_codepoint_t  base;
  hb_codepoint_t  accent;
};

struct cff1_cs_opset_seac_t : cff1_cs_opset_t<cff1_cs_opset_seac_t, get_seac_param_t>
{
  static void process_seac (cff1_cs_interp_env_t &env, get_seac_param_t& param)
  {
    unsigned int  n = env.argStack.get_count ();
    hb_codepoint_t  base_char = (hb_codepoint_t)env.argStack[n-2].to_int ();
    hb_codepoint_t  accent_char = (hb_codepoint_t)env.argStack[n-1].to_int ();

    param.base = param.cff->std_code_to_glyph (base_char);
    param.accent = param.cff->std_code_to_glyph (accent_char);
  }
};

bool OT::cff1::accelerator_t::get_seac_components (hb_codepoint_t glyph, hb_codepoint_t *base, hb_codepoint_t *accent) const
{
  if (unlikely (!is_valid () || (glyph >= num_glyphs))) return false;

  unsigned int fd = fdSelect->get_fd (glyph);
  cff1_cs_interpreter_t<cff1_cs_opset_seac_t, get_seac_param_t> interp;
  const byte_str_t str = (*charStrings)[glyph];
  interp.env.init (str, *this, fd);
  get_seac_param_t  param;
  param.init (this);
  if (unlikely (!interp.interpret (param))) return false;

  if (param.has_seac ())
  {
    *base = param.base;
    *accent = param.accent;
    return true;
  }
  return false;
}
