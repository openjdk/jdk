/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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
#include "logging/logOutput.hpp"
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

#define assert_str_eq(s1, s2) \
  assert(strcmp(s1, s2) == 0, "Expected '%s' to equal '%s'", s1, s2)

#define assert_char_in(c, s) \
  assert(strchr(s, c) != NULL, "Expected '%s' to contain character '%c'", s, c)

#define assert_char_not_in(c, s) \
  assert(strchr(s, c) == NULL, "Expected '%s' to *not* contain character '%c'", s, c)

void Test_configure_stdout() {
  ResourceMark rm;
  LogHandle(logging) log;
  LogOutput* stdoutput = LogOutput::Stdout;

  // Save current stdout config and clear it
  char* saved_config = os::strdup_check_oom(stdoutput->config_string());
  LogConfiguration::parse_log_arguments("stdout", "all=off", NULL, NULL, log.error_stream());

  // Enable 'logging=info', verifying it has been set
  LogConfiguration::configure_stdout(LogLevel::Info, true, LOG_TAGS(logging));
  assert_str_eq("logging=info,", stdoutput->config_string());
  assert(log_is_enabled(Info, logging), "logging was not properly enabled");

  // Enable 'gc=debug' (no wildcard), verifying no other tags are enabled
  LogConfiguration::configure_stdout(LogLevel::Debug, true, LOG_TAGS(gc));
  // No '+' character means only single tags are enabled, and no combinations
  assert_char_not_in('+', stdoutput->config_string());
  assert(log_is_enabled(Debug, gc), "logging was not properly enabled");

  // Enable 'gc*=trace' (with wildcard), verifying at least one tag combination is enabled (gc+...)
  LogConfiguration::configure_stdout(LogLevel::Trace, false, LOG_TAGS(gc));
  assert_char_in('+', stdoutput->config_string());
  assert(log_is_enabled(Trace, gc), "logging was not properly enabled");

  // Disable 'gc*' and 'logging', verifying all logging is properly disabled
  LogConfiguration::configure_stdout(LogLevel::Off, false, LOG_TAGS(gc));
  LogConfiguration::configure_stdout(LogLevel::Off, true, LOG_TAGS(logging));
  assert_str_eq("all=off", stdoutput->config_string());

  // Restore saved configuration
  LogConfiguration::parse_log_arguments("stdout", saved_config, NULL, NULL, log.error_stream());
  os::free(saved_config);
}

static int Test_logconfiguration_subscribe_triggered = 0;

static void Test_logconfiguration_subscribe_helper() {
  Test_logconfiguration_subscribe_triggered++;
}

void Test_logconfiguration_subscribe() {
  ResourceMark rm;
  LogHandle(logging) log;

  LogConfiguration::register_update_listener(&Test_logconfiguration_subscribe_helper);

  LogConfiguration::parse_log_arguments("stdout", "logging=trace", NULL, NULL, log.error_stream());
  assert(Test_logconfiguration_subscribe_triggered == 1, "subscription not triggered (1)");

  LogConfiguration::configure_stdout(LogLevel::Debug, true, LOG_TAGS(gc));
  assert(Test_logconfiguration_subscribe_triggered == 2, "subscription not triggered (2)");

  LogConfiguration::disable_logging();
  assert(Test_logconfiguration_subscribe_triggered == 3, "subscription not triggered (3)");
}

#endif // PRODUCT
