/*
 * Copyright (c) 2005, 2023, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_SERVICES_HEAPDUMPER_HPP
#define SHARE_SERVICES_HEAPDUMPER_HPP

#include "memory/allocation.hpp"
#include "oops/oop.hpp"
#include "runtime/os.hpp"

/*
 * HPROF binary format - description copied from:
 *   src/share/demo/jvmti/hprof/hprof_io.c
 *
 *
 *  header    "JAVA PROFILE 1.0.2" (0-terminated)
 *
 *  u4        size of identifiers. Identifiers are used to represent
 *            UTF8 strings, objects, stack traces, etc. They usually
 *            have the same size as host pointers.
 * u4         high word
 * u4         low word    number of milliseconds since 0:00 GMT, 1/1/70
 * [record]*  a sequence of records.
 *
 *
 * Record format:
 *
 * u1         a TAG denoting the type of the record
 * u4         number of *microseconds* since the time stamp in the
 *            header. (wraps around in a little more than an hour)
 * u4         number of bytes *remaining* in the record. Note that
 *            this number excludes the tag and the length field itself.
 * [u1]*      BODY of the record (a sequence of bytes)
 *
 *
 * The following TAGs are supported:
 *
 * TAG           BODY       notes
 *----------------------------------------------------------
 * HPROF_UTF8               a UTF8-encoded name
 *
 *               id         name ID
 *               [u1]*      UTF8 characters (no trailing zero)
 *
 * HPROF_LOAD_CLASS         a newly loaded class
 *
 *                u4        class serial number (> 0)
 *                id        class object ID
 *                u4        stack trace serial number
 *                id        class name ID
 *
 * HPROF_UNLOAD_CLASS       an unloading class
 *
 *                u4        class serial_number
 *
 * HPROF_FRAME              a Java stack frame
 *
 *                id        stack frame ID
 *                id        method name ID
 *                id        method signature ID
 *                id        source file name ID
 *                u4        class serial number
 *                i4        line number. >0: normal
 *                                       -1: unknown
 *                                       -2: compiled method
 *                                       -3: native method
 *
 * HPROF_TRACE              a Java stack trace
 *
 *               u4         stack trace serial number
 *               u4         thread serial number
 *               u4         number of frames
 *               [id]*      stack frame IDs
 *
 *
 * HPROF_ALLOC_SITES        a set of heap allocation sites, obtained after GC
 *
 *               u2         flags 0x0001: incremental vs. complete
 *                                0x0002: sorted by allocation vs. live
 *                                0x0004: whether to force a GC
 *               u4         cutoff ratio
 *               u4         total live bytes
 *               u4         total live instances
 *               u8         total bytes allocated
 *               u8         total instances allocated
 *               u4         number of sites that follow
 *               [u1        is_array: 0:  normal object
 *                                    2:  object array
 *                                    4:  boolean array
 *                                    5:  char array
 *                                    6:  float array
 *                                    7:  double array
 *                                    8:  byte array
 *                                    9:  short array
 *                                    10: int array
 *                                    11: long array
 *                u4        class serial number (may be zero during startup)
 *                u4        stack trace serial number
 *                u4        number of bytes alive
 *                u4        number of instances alive
 *                u4        number of bytes allocated
 *                u4]*      number of instance allocated
 *
 * HPROF_START_THREAD       a newly started thread.
 *
 *               u4         thread serial number (> 0)
 *               id         thread object ID
 *               u4         stack trace serial number
 *               id         thread name ID
 *               id         thread group name ID
 *               id         thread group parent name ID
 *
 * HPROF_END_THREAD         a terminating thread.
 *
 *               u4         thread serial number
 *
 * HPROF_HEAP_SUMMARY       heap summary
 *
 *               u4         total live bytes
 *               u4         total live instances
 *               u8         total bytes allocated
 *               u8         total instances allocated
 *
 * HPROF_HEAP_DUMP          denote a heap dump
 *
 *               [heap dump sub-records]*
 *
 *                          There are four kinds of heap dump sub-records:
 *
 *               u1         sub-record type
 *
 *               HPROF_GC_ROOT_UNKNOWN         unknown root
 *
 *                          id         object ID
 *
 *               HPROF_GC_ROOT_THREAD_OBJ      thread object
 *
 *                          id         thread object ID  (may be 0 for a
 *                                     thread newly attached through JNI)
 *                          u4         thread sequence number
 *                          u4         stack trace sequence number
 *
 *               HPROF_GC_ROOT_JNI_GLOBAL      JNI global ref root
 *
 *                          id         object ID
 *                          id         JNI global ref ID
 *
 *               HPROF_GC_ROOT_JNI_LOCAL       JNI local ref
 *
 *                          id         object ID
 *                          u4         thread serial number
 *                          u4         frame # in stack trace (-1 for empty)
 *
 *               HPROF_GC_ROOT_JAVA_FRAME      Java stack frame
 *
 *                          id         object ID
 *                          u4         thread serial number
 *                          u4         frame # in stack trace (-1 for empty)
 *
 *               HPROF_GC_ROOT_NATIVE_STACK    Native stack
 *
 *                          id         object ID
 *                          u4         thread serial number
 *
 *               HPROF_GC_ROOT_STICKY_CLASS    System class
 *
 *                          id         object ID
 *
 *               HPROF_GC_ROOT_THREAD_BLOCK    Reference from thread block
 *
 *                          id         object ID
 *                          u4         thread serial number
 *
 *               HPROF_GC_ROOT_MONITOR_USED    Busy monitor
 *
 *                          id         object ID
 *
 *               HPROF_GC_CLASS_DUMP           dump of a class object
 *
 *                          id         class object ID
 *                          u4         stack trace serial number
 *                          id         super class object ID
 *                          id         class loader object ID
 *                          id         signers object ID
 *                          id         protection domain object ID
 *                          id         reserved
 *                          id         reserved
 *
 *                          u4         instance size (in bytes)
 *
 *                          u2         size of constant pool
 *                          [u2,       constant pool index,
 *                           ty,       type
 *                                     2:  object
 *                                     4:  boolean
 *                                     5:  char
 *                                     6:  float
 *                                     7:  double
 *                                     8:  byte
 *                                     9:  short
 *                                     10: int
 *                                     11: long
 *                           vl]*      and value
 *
 *                          u2         number of static fields
 *                          [id,       static field name,
 *                           ty,       type,
 *                           vl]*      and value
 *
 *                          u2         number of inst. fields (not inc. super)
 *                          [id,       instance field name,
 *                           ty]*      type
 *
 *               HPROF_GC_INSTANCE_DUMP        dump of a normal object
 *
 *                          id         object ID
 *                          u4         stack trace serial number
 *                          id         class object ID
 *                          u4         number of bytes that follow
 *                          [vl]*      instance field values (class, followed
 *                                     by super, super's super ...)
 *
 *               HPROF_GC_OBJ_ARRAY_DUMP       dump of an object array
 *
 *                          id         array object ID
 *                          u4         stack trace serial number
 *                          u4         number of elements
 *                          id         array class ID
 *                          [id]*      elements
 *
 *               HPROF_GC_PRIM_ARRAY_DUMP      dump of a primitive array
 *
 *                          id         array object ID
 *                          u4         stack trace serial number
 *                          u4         number of elements
 *                          u1         element type
 *                                     4:  boolean array
 *                                     5:  char array
 *                                     6:  float array
 *                                     7:  double array
 *                                     8:  byte array
 *                                     9:  short array
 *                                     10: int array
 *                                     11: long array
 *                          [u1]*      elements
 *
 * HPROF_CPU_SAMPLES        a set of sample traces of running threads
 *
 *                u4        total number of samples
 *                u4        # of traces
 *               [u4        # of samples
 *                u4]*      stack trace serial number
 *
 * HPROF_CONTROL_SETTINGS   the settings of on/off switches
 *
 *                u4        0x00000001: alloc traces on/off
 *                          0x00000002: cpu sampling on/off
 *                u2        stack trace depth
 *
 *
 * When the header is "JAVA PROFILE 1.0.2" a heap dump can optionally
 * be generated as a sequence of heap dump segments. This sequence is
 * terminated by an end record. The additional tags allowed by format
 * "JAVA PROFILE 1.0.2" are:
 *
 * HPROF_HEAP_DUMP_SEGMENT  denote a heap dump segment
 *
 *               [heap dump sub-records]*
 *               The same sub-record types allowed by HPROF_HEAP_DUMP
 *
 * HPROF_HEAP_DUMP_END      denotes the end of a heap dump
 *
 */


// HPROF tags

enum hprofTag : u1 {
  // top-level records
  HPROF_UTF8                    = 0x01,
  HPROF_LOAD_CLASS              = 0x02,
  HPROF_UNLOAD_CLASS            = 0x03,
  HPROF_FRAME                   = 0x04,
  HPROF_TRACE                   = 0x05,
  HPROF_ALLOC_SITES             = 0x06,
  HPROF_HEAP_SUMMARY            = 0x07,
  HPROF_START_THREAD            = 0x0A,
  HPROF_END_THREAD              = 0x0B,
  HPROF_HEAP_DUMP               = 0x0C,
  HPROF_CPU_SAMPLES             = 0x0D,
  HPROF_CONTROL_SETTINGS        = 0x0E,

  // 1.0.2 record types
  HPROF_HEAP_DUMP_SEGMENT       = 0x1C,
  HPROF_HEAP_DUMP_END           = 0x2C,

  // field types
  HPROF_ARRAY_OBJECT            = 0x01,
  HPROF_NORMAL_OBJECT           = 0x02,
  HPROF_BOOLEAN                 = 0x04,
  HPROF_CHAR                    = 0x05,
  HPROF_FLOAT                   = 0x06,
  HPROF_DOUBLE                  = 0x07,
  HPROF_BYTE                    = 0x08,
  HPROF_SHORT                   = 0x09,
  HPROF_INT                     = 0x0A,
  HPROF_LONG                    = 0x0B,

  // data-dump sub-records
  HPROF_GC_ROOT_UNKNOWN         = 0xFF,
  HPROF_GC_ROOT_JNI_GLOBAL      = 0x01,
  HPROF_GC_ROOT_JNI_LOCAL       = 0x02,
  HPROF_GC_ROOT_JAVA_FRAME      = 0x03,
  HPROF_GC_ROOT_NATIVE_STACK    = 0x04,
  HPROF_GC_ROOT_STICKY_CLASS    = 0x05,
  HPROF_GC_ROOT_THREAD_BLOCK    = 0x06,
  HPROF_GC_ROOT_MONITOR_USED    = 0x07,
  HPROF_GC_ROOT_THREAD_OBJ      = 0x08,
  HPROF_GC_CLASS_DUMP           = 0x20,
  HPROF_GC_INSTANCE_DUMP        = 0x21,
  HPROF_GC_OBJ_ARRAY_DUMP       = 0x22,
  HPROF_GC_PRIM_ARRAY_DUMP      = 0x23
};

class outputStream;

// HeapDumper is used to dump the java heap to file in HPROF binary format
class HeapDumper : public StackObj {
 private:
  char* _error;
  bool _gc_before_heap_dump;
  bool _oome;
  elapsedTimer _t;

  HeapDumper(bool gc_before_heap_dump, bool oome) :
    _error(nullptr), _gc_before_heap_dump(gc_before_heap_dump), _oome(oome) { }

  // string representation of error
  char* error() const                   { return _error; }
  void set_error(char const* error);

  // internal timer.
  elapsedTimer* timer()                 { return &_t; }

  static void dump_heap(bool oome);

 public:
  HeapDumper(bool gc_before_heap_dump) :
    _error(nullptr), _gc_before_heap_dump(gc_before_heap_dump), _oome(false) { }

  ~HeapDumper();

  // dumps the heap to the specified file, returns 0 if success.
  // additional info is written to out if not null.
  // compression >= 0 creates a gzipped file with the given compression level.
  // parallel_thread_num >= 0 indicates thread numbers of parallel object dump
  int dump(const char* path, outputStream* out = nullptr, int compression = -1, bool overwrite = false, uint parallel_thread_num = 1);

  // returns error message (resource allocated), or null if no error
  char* error_as_C_string() const;

  static void dump_heap()    NOT_SERVICES_RETURN;

  static void dump_heap_from_oome()    NOT_SERVICES_RETURN;

  // Parallel thread number for heap dump, initialize based on active processor count.
  static uint default_num_of_dump_threads() {
    return MAX2<uint>(1, (uint)os::initial_active_processor_count() * 3 / 8);
  }
};

#endif // SHARE_SERVICES_HEAPDUMPER_HPP
