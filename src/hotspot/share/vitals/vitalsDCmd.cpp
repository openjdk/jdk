/*
 * Copyright (c) 2019, 2021 SAP SE. All rights reserved.
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
 *
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


#include "precompiled.hpp"
#include "memory/resourceArea.hpp"
#include "utilities/ostream.hpp"
#include "utilities/globalDefinitions.hpp"
#include "vitals/vitals.hpp"
#include "vitals/vitalsDCmd.hpp"

namespace sapmachine_vitals {

VitalsDCmd::VitalsDCmd(outputStream* output, bool heap)
  : DCmdWithParser(output, heap),
    _scale("scale", "Memory usage in which to scale. Valid values are: k, m, g (fixed scale) "
           "or \"dynamic\" for a dynamically chosen scale.",
           "STRING", false, "dynamic"),
    _csv("csv", "csv format.", "BOOLEAN", false, "false"),
    _no_legend("no-legend", "Omit legend.", "BOOLEAN", false, "false"),
    _reverse("reverse", "Reverse printing order.", "BOOLEAN", false, "false"),
    _raw("raw", "Print raw values.", "BOOLEAN", false, "false"),
    _sample_now("now", "Sample now values", "BOOLEAN", false, "false")
{
  _dcmdparser.add_dcmd_option(&_scale);
  _dcmdparser.add_dcmd_option(&_csv);
  _dcmdparser.add_dcmd_option(&_no_legend);
  _dcmdparser.add_dcmd_option(&_reverse);
  _dcmdparser.add_dcmd_option(&_raw);
  _dcmdparser.add_dcmd_option(&_sample_now);
}

int VitalsDCmd::num_arguments() {
  ResourceMark rm;
  VitalsDCmd* dcmd = new VitalsDCmd(NULL, false);
  if (dcmd != NULL) {
    DCmdMark mark(dcmd);
    return dcmd->_dcmdparser.num_arguments();
  } else {
    return 0;
  }
}

static bool scale_from_name(const char* scale, size_t* out) {
  if (strcasecmp(scale, "dynamic") == 0) {
    *out = 0;
  } else if (strcasecmp(scale, "1") == 0 || strcasecmp(scale, "b") == 0) {
    *out = 1;
  } else if (strcasecmp(scale, "kb") == 0 || strcasecmp(scale, "k") == 0) {
    *out = K;
  } else if (strcasecmp(scale, "mb") == 0 || strcasecmp(scale, "m") == 0) {
    *out = M;
  } else if (strcasecmp(scale, "gb") == 0 || strcasecmp(scale, "g") == 0) {
    *out = G;
  } else {
    return false; // Invalid value
  }
  return true;
}

void VitalsDCmd::execute(DCmdSource source, TRAPS) {
  sapmachine_vitals::print_info_t info;
  sapmachine_vitals::default_settings(&info);
  if (!scale_from_name(_scale.value(), &(info.scale))) {
    output()->print_cr("Invalid scale: \"%s\".", _scale.value());
    return;
  }
  info.csv = _csv.value();
  info.no_legend = _no_legend.value();
  info.reverse_ordering = _reverse.value();
  info.raw = _raw.value();
  info.sample_now = _sample_now.value();

  sapmachine_vitals::print_report(output(), &info);
}

}; // namespace sapmachine_vitals
