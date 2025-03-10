/*
 * Copyright (c) 2015, 2024, Oracle and/or its affiliates. All rights reserved.
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
 */

#ifndef SHARE_GC_Z_ZDRIVER_HPP
#define SHARE_GC_Z_ZDRIVER_HPP

#include "gc/shared/gcTimer.hpp"
#include "gc/z/zDriverPort.hpp"
#include "gc/z/zThread.hpp"
#include "gc/z/zTracer.hpp"

// glibc <sys/sysmacros.h> which may get brought in via <sys/types.h>
// defines the macros minor and major. These keywords are central to
// the GC algorithm. These macros are undefined here so the code may
// use minor and major.
#ifdef minor
#undef minor
#endif
#ifdef major
#undef major
#endif

class VM_ZOperation;
class ZDriverMinor;
class ZDriverMajor;
class ZLock;

class ZDriver : public ZThread {
  friend class ZDriverLocker;
  friend class ZDriverUnlocker;

private:
  static ZLock*        _lock;
  static ZDriverMinor* _minor;
  static ZDriverMajor* _major;

  GCCause::Cause _gc_cause;

  static void lock();
  static void unlock();

public:
  static void initialize();

  static void set_minor(ZDriverMinor* minor);
  static void set_major(ZDriverMajor* major);

  static ZDriverMinor* minor();
  static ZDriverMajor* major();

  ZDriver();

  void set_gc_cause(GCCause::Cause cause);
  GCCause::Cause gc_cause();
};

class ZDriverMinor : public ZDriver {
private:
  ZDriverPort       _port;
  ConcurrentGCTimer _gc_timer;
  ZMinorTracer      _jfr_tracer;
  size_t            _used_at_start;

  void gc(const ZDriverRequest& request);
  void handle_alloc_stalls() const;

protected:
  virtual void run_thread();
  virtual void terminate();

public:
  ZDriverMinor();

  bool is_busy() const;

  void collect(const ZDriverRequest& request);

  GCTracer* jfr_tracer();

  void set_used_at_start(size_t used);
  size_t used_at_start() const;
};

class ZDriverMajor : public ZDriver {
private:
  ZDriverPort       _port;
  ConcurrentGCTimer _gc_timer;
  ZMajorTracer      _jfr_tracer;
  size_t            _used_at_start;

  void collect_young(const ZDriverRequest& request);

  void collect_old();
  void gc(const ZDriverRequest& request);
  void handle_alloc_stalls() const;

protected:
  virtual void run_thread();
  virtual void terminate();

public:
  ZDriverMajor();

  bool is_busy() const;

  void collect(const ZDriverRequest& request);

  GCTracer* jfr_tracer();

  void set_used_at_start(size_t used);
  size_t used_at_start() const;
};

class ZDriverLocker : public StackObj {
public:
  ZDriverLocker();
  ~ZDriverLocker();
};

class ZDriverUnlocker : public StackObj {
public:
  ZDriverUnlocker();
  ~ZDriverUnlocker();
};

#endif // SHARE_GC_Z_ZDRIVER_HPP
