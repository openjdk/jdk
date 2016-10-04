/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
#include "logging/logFileOutput.hpp"
#include "memory/resourceArea.hpp"
#include "runtime/os.hpp"
#include "unittest.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/ostream.hpp"

static const char* name = "file=testlog.pid%p.%t.log";

// Test parsing a bunch of valid file output options
TEST_VM(LogFileOutput, parse_valid) {
  const char* valid_options[] = {
    "", "filecount=10", "filesize=512",
    "filecount=11,filesize=256",
    "filesize=256,filecount=11",
    "filesize=0", "filecount=1",
    "filesize=1m", "filesize=1M",
    "filesize=1k", "filesize=1G"
  };

  // Override LogOutput's vm_start time to get predictable file name
  LogFileOutput::set_file_name_parameters(0);
  char expected_filename[1 * K];
  int ret = jio_snprintf(expected_filename, sizeof(expected_filename),
                         "testlog.pid%d.1970-01-01_01-00-00.log",
                         os::current_process_id());
  ASSERT_GT(ret, 0) << "Buffer too small";

  for (size_t i = 0; i < ARRAY_SIZE(valid_options); i++) {
    ResourceMark rm;
    stringStream ss;
    {
      LogFileOutput fo(name);
      EXPECT_STREQ(name, fo.name());
      EXPECT_TRUE(fo.initialize(valid_options[i], &ss))
        << "Did not accept valid option(s) '" << valid_options[i] << "': " << ss.as_string();
    }
    remove(expected_filename);
  }
}

// Test parsing a bunch of invalid file output options
TEST_VM(LogFileOutput, parse_invalid) {
  const char* invalid_options[] = {
    "invalidopt", "filecount=",
    "filesize=,filecount=10",
    "fileco=10", "ilesize=512",
    "filecount=11,,filesize=256",
    ",filesize=256,filecount=11",
    "filesize=256,filecount=11,",
    "filesize=-1", "filecount=0.1",
    "filecount=-2", "filecount=2.0",
    "filecount= 2", "filesize=2 ",
    "filecount=ab", "filesize=0xz",
    "filecount=1MB", "filesize=99bytes",
    "filesize=9999999999999999999999999"
    "filecount=9999999999999999999999999"
  };

  for (size_t i = 0; i < ARRAY_SIZE(invalid_options); i++) {
    ResourceMark rm;
    stringStream ss;
    LogFileOutput fo(name);
    EXPECT_FALSE(fo.initialize(invalid_options[i], &ss))
      << "Accepted invalid option(s) '" << invalid_options[i] << "': " << ss.as_string();
  }
}

// Test for overflows with filesize
TEST_VM(LogFileOutput, filesize_overflow) {
  char buf[256];
  int ret = jio_snprintf(buf, sizeof(buf), "filesize=" SIZE_FORMAT "K", SIZE_MAX);
  ASSERT_GT(ret, 0) << "Buffer too small";

  ResourceMark rm;
  stringStream ss;
  LogFileOutput fo(name);
  EXPECT_FALSE(fo.initialize(buf, &ss)) << "Accepted filesize that overflows";
}
