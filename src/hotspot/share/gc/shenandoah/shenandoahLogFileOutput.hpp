/*
 * Copyright (c) 2021, Amazon.com, Inc. All rights reserved.
 * Copyright (c) 2015, 2021, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_SHENANDOAH_SHENANDOAHLOGFILEOUTPUT_HPP
#define SHARE_GC_SHENANDOAH_SHENANDOAHLOGFILEOUTPUT_HPP

#include "logging/logFileStreamOutput.hpp"
#include "logging/logFileOutput.hpp"
#include "utilities/globalDefinitions.hpp"
#include "runtime/perfData.inline.hpp"

// Log file output to capture Shenandoah GC data.

class ShenandoahLogFileOutput : public CHeapObj<mtClass> {
private:
    static const char* const FileOpenMode;
    static const char* const PidFilenamePlaceholder;
    static const char* const TimestampFilenamePlaceholder;
    static const char* const TimestampFormat;
    static const size_t StartTimeBufferSize = 20;
    static const size_t PidBufferSize = 21;
    static char         _pid_str[PidBufferSize];
    static char         _vm_start_time_str[StartTimeBufferSize];

    const char* _name;
    char* _file_name;
    FILE* _stream;

    bool _write_error_is_shown;

    bool parse_options(const char* options, outputStream* errstream);
    char *make_file_name(const char* file_name, const char* pid_string, const char* timestamp_string);

    bool flush();

public:
    ShenandoahLogFileOutput(const char *name, jlong vm_start_time);
    ~ShenandoahLogFileOutput();

    void initialize(outputStream* errstream);

    int write_snapshot(PerfLongVariable** regions,
                       PerfLongVariable* ts,
                       PerfLongVariable* status,
                       size_t num_regions,
                       size_t region_size, size_t protocolVersion);

    const char* name() const {
      return _name;
    }

    static const char* const Prefix;
    static void set_file_name_parameters(jlong start_time);
};
#endif //SHARE_GC_SHENANDOAH_SHENANDOAHLOGFILEOUTPUT_HPP

