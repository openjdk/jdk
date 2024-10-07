/*
* Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_JFR_SUPPORT_JFRNATIVELIBRARYLOADEVENT_HPP
#define SHARE_JFR_SUPPORT_JFRNATIVELIBRARYLOADEVENT_HPP

#include "memory/allocation.hpp"

class JfrTicksWrapper;

/*
 * Helper types for populating NativeLibrary events.
 * Event commit is run as part of destructors.
 */

class JfrNativeLibraryEventBase : public StackObj {
 protected:
  const char* _name;
  const char* _error_msg;
  JfrTicksWrapper* _start_time;
  JfrNativeLibraryEventBase(const char* name);
  ~JfrNativeLibraryEventBase();
 public:
  const char* name() const;
  const char* error_msg() const;
  void set_error_msg(const char* error_msg);
  JfrTicksWrapper* start_time() const;
  bool has_start_time() const;
};

class NativeLibraryLoadEvent : public JfrNativeLibraryEventBase {
 private:
  void** _result;
  bool _fp_env_correction_attempt;
  bool _fp_env_correction_success;
 public:
  NativeLibraryLoadEvent(const char* name, void** result);
  ~NativeLibraryLoadEvent();
  bool success() const;
  bool get_fp_env_correction_attempt() const { return _fp_env_correction_attempt; }
  bool get_fp_env_correction_success() const { return _fp_env_correction_success; }
  void set_fp_env_correction_attempt(bool v) { _fp_env_correction_attempt = v; }
  void set_fp_env_correction_success(bool v) { _fp_env_correction_success = v; }
};

class NativeLibraryUnloadEvent : public JfrNativeLibraryEventBase {
 private:
  bool _result;
 public:
  NativeLibraryUnloadEvent(const char* name);
  ~NativeLibraryUnloadEvent();
  bool success() const;
  void set_result(bool result);
};

#endif // SHARE_JFR_SUPPORT_JFRNATIVELIBRARYLOADEVENT_HPP
