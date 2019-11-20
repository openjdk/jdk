/*
 * Copyright (c) 2006, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
 *
 */

#include "logging/log.hpp"
#include "precompiled.hpp"
#include "runtime/os.hpp"
#include "runtime/vm_version.hpp"


#define CPUINFO_LINE_SIZE 1024


class CPUinfo {
public:
  CPUinfo(const char* field) : _string(NULL) {

    char line[CPUINFO_LINE_SIZE];
    FILE* fp = fopen("/proc/cpuinfo", "r");

    if (fp != NULL) {
      while (fgets(line, sizeof(line), fp) != NULL) {
        assert(strlen(line) < sizeof(line) - 1,
               "buffer too small (%d)", CPUINFO_LINE_SIZE);

        const char* vstr = match_field(line, field);

        if (vstr != NULL) {
          // We have a matching line and a valid starting point to the value of
          // the field, copy the string for keeps.
          _string = strdup(vstr);
          break;
        }
      }
      fclose(fp);
    }
  }

  ~CPUinfo() { free((void*)_string); }

  const char* value() const { return _string; }

  bool valid() const { return _string != NULL; }

  bool match(const char* s) const {
    return valid() ? strcmp(_string, s) == 0 : false;
  }

private:
  const char* _string;

  const char* match_field(char line[CPUINFO_LINE_SIZE], const char* field);
  const char* match_alo(const char* text, const char* exp);
  const char* match_seq(const char* text, const char* seq);
};

/* Given a line of text read from /proc/cpuinfo, determine if the property header
 * matches the field specified, according to the following regexp: "<field>"\W+:\W+
 *
 * If we have a matching expression, return a pointer to the first character after
 * the matching pattern, i.e. the "value", otherwise return NULL.
 */
const char* CPUinfo::match_field(char line[CPUINFO_LINE_SIZE], const char* field) {
  return match_alo(match_seq(match_alo(match_seq(line, field), "\t "), ":"), "\t ");
}

/* Match a sequence of at-least-one character in the string expression (exp) to
 * the text input.
 */
const char* CPUinfo::match_alo(const char* text, const char* exp) {
  if (text == NULL) return NULL;

  const char* chp;

  for (chp = &text[0]; *chp != '\0'; chp++) {
    if (strchr(exp, *chp) == NULL) break;
  }

  return text < chp ? chp : NULL;
}

/* Match an exact sequence of characters as specified by the string expression
 * (seq) to the text input.
 */
const char* CPUinfo::match_seq(const char* text, const char* seq) {
  if (text == NULL) return NULL;

  while (*seq != '\0') {
    if (*seq != *text++) break; else seq++;
  }

  return *seq == '\0' ? text : NULL;
}


typedef struct {
  const uint32_t    hash;
  bool              seen;
  const char* const name;
  const uint64_t    mask;
} FeatureEntry;


static uint64_t parse_features(FeatureEntry feature_tbl[], const char input[]);


void VM_Version::platform_features() {

  // Some of the features reported via "cpucaps", such as; 'flush', 'stbar',
  // 'swap', 'muldiv', 'ultra3', 'blkinit', 'n2', 'mul32', 'div32', 'fsmuld'
  // and 'v8plus', are either SPARC V8, supported by all HW or simply nonsense
  // (the 'ultra3' "property").
  //
  // Entries marked as 'NYI' are not yet supported via "cpucaps" but are
  // expected to have the names used in the table below (these are SPARC M7
  // features or more recent).
  //
  // NOTE: Table sorted on lookup/hash ID.

  static FeatureEntry s_feature_tbl[] = {
    { 0x006f, false, "v9",         ISA_v9_msk },            // Mandatory
    { 0x00a6, false, "md5",        ISA_md5_msk },
    { 0x00ce, false, "adi",        ISA_adi_msk },           // NYI
    { 0x00d7, false, "ima",        ISA_ima_msk },
    { 0x00d9, false, "aes",        ISA_aes_msk },
    { 0x00db, false, "hpc",        ISA_hpc_msk },
    { 0x00dc, false, "des",        ISA_des_msk },
    { 0x00ed, false, "sha1",       ISA_sha1_msk },
    { 0x00f2, false, "vis",        ISA_vis1_msk },
    { 0x0104, false, "vis2",       ISA_vis2_msk },
    { 0x0105, false, "vis3",       ISA_vis3_msk },
    { 0x0114, false, "sha512",     ISA_sha512_msk },
    { 0x0119, false, "sha256",     ISA_sha256_msk },
    { 0x011a, false, "fmaf",       ISA_fmaf_msk },
    { 0x0132, false, "popc",       ISA_popc_msk },
    { 0x0140, false, "crc32c",     ISA_crc32c_msk },
    { 0x0147, false, "vis3b",      ISA_vis3b_msk },         // NYI
    { 0x017e, false, "pause",      ISA_pause_msk },
    { 0x0182, false, "mwait",      ISA_mwait_msk },         // NYI
    { 0x018b, false, "mpmul",      ISA_mpmul_msk },
    { 0x018e, false, "sparc5",     ISA_sparc5_msk },        // NYI
    { 0x01a9, false, "cbcond",     ISA_cbcond_msk },
    { 0x01c3, false, "vamask",     ISA_vamask_msk },        // NYI
    { 0x01ca, false, "kasumi",     ISA_kasumi_msk },
    { 0x01e3, false, "xmpmul",     ISA_xmpmul_msk },        // NYI
    { 0x022c, false, "montmul",    ISA_mont_msk },
    { 0x0234, false, "montsqr",    ISA_mont_msk },
    { 0x0238, false, "camellia",   ISA_camellia_msk },
    { 0x024a, false, "ASIBlkInit", ISA_blk_init_msk },
    { 0x0284, false, "xmontmul",   ISA_xmont_msk },         // NYI
    { 0x02e6, false, "pause_nsec", ISA_pause_nsec_msk },    // NYI

    { 0x0000, false, NULL, 0 }
  };

  CPUinfo caps("cpucaps");      // Read "cpucaps" from /proc/cpuinfo.

  assert(caps.valid(), "must be");

  _features = parse_features(s_feature_tbl, caps.value());

  assert(has_v9(), "must be");  // Basic SPARC-V9 required (V8 not supported).

  CPUinfo type("type");

  bool is_sun4v = type.match("sun4v");   // All Oracle SPARC + Fujitsu Athena+
  bool is_sun4u = type.match("sun4u");   // All other Fujitsu

  uint64_t synthetic = 0;

  if (is_sun4v) {
    // Indirect and direct branches are equally fast.
    synthetic = CPU_fast_ind_br_msk;
    // Fast IDIV, BIS and LD available on Niagara Plus.
    if (has_vis2()) {
      synthetic |= (CPU_fast_idiv_msk | CPU_fast_ld_msk);
      // ...on Core C4 however, we prefer not to use BIS.
      if (!has_sparc5()) {
        synthetic |= CPU_fast_bis_msk;
      }
    }
    // Niagara Core C3 supports fast RDPC and block zeroing.
    if (has_ima()) {
      synthetic |= (CPU_fast_rdpc_msk | CPU_blk_zeroing_msk);
    }
    // Niagara Core C3 and C4 have slow CMOVE.
    if (!has_ima()) {
      synthetic |= CPU_fast_cmove_msk;
    }
  } else if (is_sun4u) {
    // SPARC64 only have fast IDIV and RDPC.
    synthetic |= (CPU_fast_idiv_msk | CPU_fast_rdpc_msk);
  } else {
    log_info(os, cpu)("Unable to derive CPU features: %s", type.value());
  }

  _features += synthetic;   // Including CPU derived/synthetic features.
}


////////////////////////////////////////////////////////////////////////////////

static uint32_t uhash32(const char name[]);

static void update_table(FeatureEntry feature_tbl[], uint32_t hv,
                         const char* ch1p,
                         const char* endp);

/* Given a feature table, parse the input text holding the string value of
 * 'cpucaps' as reported by '/proc/cpuinfo', in order to complete the table
 * with information on each admissible feature (whether present or not).
 *
 * Return the composite bit-mask representing the features found.
 */
static uint64_t parse_features(FeatureEntry feature_tbl[], const char input[]) {
  log_info(os, cpu)("Parse CPU features: %s\n", input);

#ifdef ASSERT
  // Verify that hash value entries in the table are unique and ordered.

  uint32_t prev = 0;

  for (uint k = 0; feature_tbl[k].name != NULL; k++) {
    feature_tbl[k].seen = false;

    assert(feature_tbl[k].hash == uhash32(feature_tbl[k].name),
           "feature '%s' has mismatching hash 0x%08x (expected 0x%08x).\n",
           feature_tbl[k].name,
           feature_tbl[k].hash,
           uhash32(feature_tbl[k].name));

    assert(prev < feature_tbl[k].hash,
           "feature '%s' has invalid hash 0x%08x (previous is 0x%08x).\n",
           feature_tbl[k].name,
           feature_tbl[k].hash,
           prev);

    prev = feature_tbl[k].hash;
  }
#endif
  // Identify features from the input, consisting of a string with features
  // separated by commas (or whitespace), e.g. "flush,muldiv,v9,mul32,div32,
  // v8plus,popc,vis".

  uint32_t hv = 0;
  const char* ch1p = &input[0];
  uint i = 0;

  do {
    char ch = input[i];

    if (isalnum(ch) || ch == '_') {
      hv += (ch - 32u);
    }
    else if (isspace(ch) || ch == ',' || ch == '\0') { // end-of-token
      if (ch1p < &input[i]) {
        update_table(feature_tbl, hv, ch1p, &input[i]);
      }
      ch1p = &input[i + 1]; hv = 0;
    } else {
      // Handle non-accepted input robustly.
      log_info(os, cpu)("Bad token in feature string: '%c' (0x%02x).\n", ch, ch);
      ch1p = &input[i + 1]; hv = 0;
    }
  }
  while (input[i++] != '\0');

  // Compute actual bit-mask representation.

  uint64_t mask = 0;

  for (uint k = 0; feature_tbl[k].name != NULL; k++) {
    mask |= feature_tbl[k].seen ? feature_tbl[k].mask : 0;
  }

  return mask;
}

static uint32_t uhash32(const char name[]) {
  uint32_t hv = 0;

  for (uint i = 0; name[i] != '\0'; i++) {
    hv += (name[i] - 32u);
  }

  return hv;
}

static bool verify_match(const char name[], const char* ch1p, const char* endp);

static void update_table(FeatureEntry feature_tbl[], uint32_t hv, const char* ch1p, const char* endp) {
  assert(ch1p < endp, "at least one character");

  // Look for a hash value in the table. Since this table is a small one (and
  // is expected to stay small), we use a simple linear search (iff the table
  // grows large, we may consider to adopt a binary ditto, or a perfect hash).

  for (uint k = 0; feature_tbl[k].name != NULL; k++) {
    uint32_t hash = feature_tbl[k].hash;

    if (hash < hv) continue;

    if (hash == hv) {
      const char* name = feature_tbl[k].name;

      if (verify_match(name, ch1p, endp)) {
        feature_tbl[k].seen = true;
        break;
      }
    }

    // Either a non-matching feature (when hash == hv) or hash > hv. In either
    // case we break out of the loop and terminate the search (note that the
    // table is assumed to be uniquely sorted on the hash).

    break;
  }
}

static bool verify_match(const char name[], const char* ch1p, const char* endp) {
  size_t len = strlen(name);

  if (len != static_cast<size_t>(endp - ch1p)) {
    return false;
  }

  for (uint i = 0; ch1p + i < endp; i++) {
    if (name[i] != ch1p[i]) return false;
  }

  return true;
}
