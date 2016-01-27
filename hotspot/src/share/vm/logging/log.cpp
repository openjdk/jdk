/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

/////////////// Unit tests ///////////////

#ifndef PRODUCT

#include "logging/log.hpp"
#include "logging/logConfiguration.hpp"
#include "memory/resourceArea.hpp"

void Test_log_length() {
  remove("loglengthoutput.txt");

  // Write long message to output file
  ResourceMark rm;
  LogHandle(logging) log;
  bool success = LogConfiguration::parse_log_arguments("loglengthoutput.txt", "logging=trace",
    NULL, NULL, log.error_stream());
  assert(success, "test unable to configure logging");
  log.trace("01:1234567890-"
            "02:1234567890-"
            "03:1234567890-"
            "04:1234567890-"
            "05:1234567890-"
            "06:1234567890-"
            "07:1234567890-"
            "08:1234567890-"
            "09:1234567890-"
            "10:1234567890-"
            "11:1234567890-"
            "12:1234567890-"
            "13:1234567890-"
            "14:1234567890-"
            "15:1234567890-"
            "16:1234567890-"
            "17:1234567890-"
            "18:1234567890-"
            "19:1234567890-"
            "20:1234567890-"
            "21:1234567890-"
            "22:1234567890-"
            "23:1234567890-"
            "24:1234567890-"
            "25:1234567890-"
            "26:1234567890-"
            "27:1234567890-"
            "28:1234567890-"
            "29:1234567890-"
            "30:1234567890-"
            "31:1234567890-"
            "32:1234567890-"
            "33:1234567890-"
            "34:1234567890-"
            "35:1234567890-"
            "36:1234567890-"
            "37:1234567890-");
  LogConfiguration::parse_log_arguments("loglengthoutput.txt", "all=off",
    NULL, NULL, log.error_stream());

  // Look for end of message in output file
  FILE* fp = fopen("loglengthoutput.txt", "r");
  assert(fp, "File read error");
  char output[600];
  if (fgets(output, 600, fp) != NULL) {
    assert(strstr(output, "37:1234567890-"), "logging print size error");
  }
  fclose(fp);
  remove("loglengthoutput.txt");
}

void Test_configure_stdout() {
  LogConfiguration::configure_stdout(LogLevel::Info, true, LOG_TAGS(logging));
  assert(log_is_enabled(Info, logging), "configure_stdout did not enable requested logging");
  assert(!log_is_enabled(Info, logging, gc), "configure_stdout enabled too much logging");
  LogConfiguration::configure_stdout(LogLevel::Off, false, LOG_TAGS(logging));
  assert(!log_is_enabled(Info, logging), "configure_stdout did not disable requested logging");
}
#endif // PRODUCT
