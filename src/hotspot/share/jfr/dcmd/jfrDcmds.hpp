/*
 * Copyright (c) 2012, 2018, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_JFR_JFRDCMDS_HPP
#define SHARE_VM_JFR_JFRDCMDS_HPP

#include "services/diagnosticCommand.hpp"

class JfrDumpFlightRecordingDCmd : public DCmdWithParser {
 protected:
  DCmdArgument<char*> _name;
  DCmdArgument<char*> _filename;
  DCmdArgument<NanoTimeArgument> _maxage;
  DCmdArgument<MemorySizeArgument> _maxsize;
  DCmdArgument<char*> _begin;
  DCmdArgument<char*> _end;
  DCmdArgument<bool>  _path_to_gc_roots;

 public:
  JfrDumpFlightRecordingDCmd(outputStream* output, bool heap);
  static const char* name() {
    return "JFR.dump";
  }
  static const char* description() {
    return "Copies contents of a JFR recording to file. Either the name or the recording id must be specified.";
  }
  static const char* impact() {
    return "Low";
  }
  static const JavaPermission permission() {
    JavaPermission p = {"java.lang.management.ManagementPermission", "monitor", NULL};
    return p;
  }
  static int num_arguments();
  virtual void execute(DCmdSource source, TRAPS);
};

class JfrCheckFlightRecordingDCmd : public DCmdWithParser {
 protected:
  DCmdArgument<char*> _name;
  DCmdArgument<bool>  _verbose;

 public:
  JfrCheckFlightRecordingDCmd(outputStream* output, bool heap);
  static const char* name() {
    return "JFR.check";
  }
  static const char* description() {
    return "Checks running JFR recording(s)";
  }
  static const char* impact() {
    return "Low";
  }
  static const JavaPermission permission() {
    JavaPermission p = {"java.lang.management.ManagementPermission", "monitor", NULL};
    return p;
  }
  static int num_arguments();
  virtual void execute(DCmdSource source, TRAPS);
};

class JfrStartFlightRecordingDCmd : public DCmdWithParser {
 protected:
  DCmdArgument<char*> _name;
  DCmdArgument<StringArrayArgument*> _settings;
  DCmdArgument<NanoTimeArgument> _delay;
  DCmdArgument<NanoTimeArgument> _duration;
  DCmdArgument<bool> _disk;
  DCmdArgument<char*> _filename;
  DCmdArgument<NanoTimeArgument> _maxage;
  DCmdArgument<MemorySizeArgument> _maxsize;
  DCmdArgument<bool> _dump_on_exit;
  DCmdArgument<bool> _path_to_gc_roots;

 public:
  JfrStartFlightRecordingDCmd(outputStream* output, bool heap);
  static const char* name() {
    return "JFR.start";
  }
  static const char* description() {
    return "Starts a new JFR recording";
  }
  static const char* impact() {
    return "Medium: Depending on the settings for a recording, the impact can range from low to high.";
  }
  static const JavaPermission permission() {
    JavaPermission p = {"java.lang.management.ManagementPermission", "monitor", NULL};
    return p;
  }
  static int num_arguments();
  virtual void execute(DCmdSource source, TRAPS);
};

class JfrStopFlightRecordingDCmd : public DCmdWithParser {
 protected:
  DCmdArgument<char*> _name;
  DCmdArgument<char*> _filename;

 public:
  JfrStopFlightRecordingDCmd(outputStream* output, bool heap);
  static const char* name() {
    return "JFR.stop";
  }
  static const char* description() {
    return "Stops a JFR recording";
  }
  static const char* impact() {
    return "Low";
  }
  static const JavaPermission permission() {
    JavaPermission p = {"java.lang.management.ManagementPermission", "monitor", NULL};
    return p;
  }
  static int num_arguments();
  virtual void execute(DCmdSource source, TRAPS);
};

class JfrRuntimeOptions;

class JfrConfigureFlightRecorderDCmd : public DCmdWithParser {
  friend class JfrOptionSet;
 protected:
  DCmdArgument<char*> _repository_path;
  DCmdArgument<char*> _dump_path;
  DCmdArgument<jlong> _stack_depth;
  DCmdArgument<jlong> _global_buffer_count;
  DCmdArgument<jlong> _global_buffer_size;
  DCmdArgument<jlong> _thread_buffer_size;
  DCmdArgument<jlong> _memory_size;
  DCmdArgument<jlong> _max_chunk_size;
  DCmdArgument<bool>  _sample_threads;

 public:
  JfrConfigureFlightRecorderDCmd(outputStream* output, bool heap);
  static const char* name() {
    return "JFR.configure";
  }
  static const char* description() {
    return "Configure JFR";
  }
  static const char* impact() {
    return "Low";
  }
  static const JavaPermission permission() {
    JavaPermission p = {"java.lang.management.ManagementPermission", "monitor", NULL};
    return p;
  }
  static int num_arguments();
  virtual void execute(DCmdSource source, TRAPS);
};

bool register_jfr_dcmds();

#endif // SHARE_VM_JFR_JFRDCMDS_HPP
