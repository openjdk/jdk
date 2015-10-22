/*
 * Copyright (c) 2005, 2015, Oracle and/or its affiliates. All rights reserved.
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
#include "classfile/symbolTable.hpp"
#include "classfile/systemDictionary.hpp"
#include "classfile/vmSymbols.hpp"
#include "gc/shared/gcLocker.inline.hpp"
#include "gc/shared/genCollectedHeap.hpp"
#include "gc/shared/vmGCOperations.hpp"
#include "memory/universe.hpp"
#include "oops/objArrayKlass.hpp"
#include "oops/objArrayOop.inline.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/javaCalls.hpp"
#include "runtime/jniHandles.hpp"
#include "runtime/os.hpp"
#include "runtime/reflectionUtils.hpp"
#include "runtime/vframe.hpp"
#include "runtime/vmThread.hpp"
#include "runtime/vm_operations.hpp"
#include "services/heapDumper.hpp"
#include "services/threadService.hpp"
#include "utilities/macros.hpp"
#include "utilities/ostream.hpp"
#if INCLUDE_ALL_GCS
#include "gc/parallel/parallelScavengeHeap.hpp"
#endif // INCLUDE_ALL_GCS

/*
 * HPROF binary format - description copied from:
 *   src/share/demo/jvmti/hprof/hprof_io.c
 *
 *
 *  header    "JAVA PROFILE 1.0.1" or "JAVA PROFILE 1.0.2"
 *            (0-terminated)
 *
 *  u4        size of identifiers. Identifiers are used to represent
 *            UTF8 strings, objects, stack traces, etc. They usually
 *            have the same size as host pointers. For example, on
 *            Solaris and Win32, the size is 4.
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

typedef enum {
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
} hprofTag;

// Default stack trace ID (used for dummy HPROF_TRACE record)
enum {
  STACK_TRACE_ID = 1,
  INITIAL_CLASS_COUNT = 200
};

// Supports I/O operations on a dump file

class DumpWriter : public StackObj {
 private:
  enum {
    io_buffer_size  = 8*M
  };

  int _fd;              // file descriptor (-1 if dump file not open)
  jlong _bytes_written; // number of byte written to dump file

  char* _buffer;    // internal buffer
  int _size;
  int _pos;

  char* _error;   // error message when I/O fails

  void set_file_descriptor(int fd)              { _fd = fd; }
  int file_descriptor() const                   { return _fd; }

  char* buffer() const                          { return _buffer; }
  int buffer_size() const                       { return _size; }
  int position() const                          { return _pos; }
  void set_position(int pos)                    { _pos = pos; }

  void set_error(const char* error)             { _error = (char*)os::strdup(error); }

  // all I/O go through this function
  void write_internal(void* s, int len);

 public:
  DumpWriter(const char* path);
  ~DumpWriter();

  void close();
  bool is_open() const                  { return file_descriptor() >= 0; }
  void flush();

  // total number of bytes written to the disk
  jlong bytes_written() const           { return _bytes_written; }

  // adjust the number of bytes written to disk (used to keep the count
  // of the number of bytes written in case of rewrites)
  void adjust_bytes_written(jlong n)     { _bytes_written += n; }

  // number of (buffered) bytes as yet unwritten to the dump file
  jlong bytes_unwritten() const          { return (jlong)position(); }

  char* error() const                   { return _error; }

  jlong current_offset();
  void seek_to_offset(jlong pos);

  // writer functions
  void write_raw(void* s, int len);
  void write_u1(u1 x)                   { write_raw((void*)&x, 1); }
  void write_u2(u2 x);
  void write_u4(u4 x);
  void write_u8(u8 x);
  void write_objectID(oop o);
  void write_symbolID(Symbol* o);
  void write_classID(Klass* k);
  void write_id(u4 x);
};

DumpWriter::DumpWriter(const char* path) {
  // try to allocate an I/O buffer of io_buffer_size. If there isn't
  // sufficient memory then reduce size until we can allocate something.
  _size = io_buffer_size;
  do {
    _buffer = (char*)os::malloc(_size, mtInternal);
    if (_buffer == NULL) {
      _size = _size >> 1;
    }
  } while (_buffer == NULL && _size > 0);
  assert((_size > 0 && _buffer != NULL) || (_size == 0 && _buffer == NULL), "sanity check");
  _pos = 0;
  _error = NULL;
  _bytes_written = 0L;
  _fd = os::create_binary_file(path, false);    // don't replace existing file

  // if the open failed we record the error
  if (_fd < 0) {
    _error = (char*)os::strdup(strerror(errno));
  }
}

DumpWriter::~DumpWriter() {
  // flush and close dump file
  if (is_open()) {
    close();
  }
  if (_buffer != NULL) os::free(_buffer);
  if (_error != NULL) os::free(_error);
}

// closes dump file (if open)
void DumpWriter::close() {
  // flush and close dump file
  if (is_open()) {
    flush();
    ::close(file_descriptor());
    set_file_descriptor(-1);
  }
}

// write directly to the file
void DumpWriter::write_internal(void* s, int len) {
  if (is_open()) {
    int n = ::write(file_descriptor(), s, len);
    if (n > 0) {
      _bytes_written += n;
    }
    if (n != len) {
      if (n < 0) {
        set_error(strerror(errno));
      } else {
        set_error("file size limit");
      }
      ::close(file_descriptor());
      set_file_descriptor(-1);
    }
  }
}

// write raw bytes
void DumpWriter::write_raw(void* s, int len) {
  if (is_open()) {
    // flush buffer to make toom
    if ((position()+ len) >= buffer_size()) {
      flush();
    }

    // buffer not available or too big to buffer it
    if ((buffer() == NULL) || (len >= buffer_size())) {
      write_internal(s, len);
    } else {
      // Should optimize this for u1/u2/u4/u8 sizes.
      memcpy(buffer() + position(), s, len);
      set_position(position() + len);
    }
  }
}

// flush any buffered bytes to the file
void DumpWriter::flush() {
  if (is_open() && position() > 0) {
    write_internal(buffer(), position());
    set_position(0);
  }
}


jlong DumpWriter::current_offset() {
  if (is_open()) {
    // the offset is the file offset plus whatever we have buffered
    jlong offset = os::current_file_offset(file_descriptor());
    assert(offset >= 0, "lseek failed");
    return offset + (jlong)position();
  } else {
    return (jlong)-1;
  }
}

void DumpWriter::seek_to_offset(jlong off) {
  assert(off >= 0, "bad offset");

  // need to flush before seeking
  flush();

  // may be closed due to I/O error
  if (is_open()) {
    jlong n = os::seek_to_file_offset(file_descriptor(), off);
    assert(n >= 0, "lseek failed");
  }
}

void DumpWriter::write_u2(u2 x) {
  u2 v;
  Bytes::put_Java_u2((address)&v, x);
  write_raw((void*)&v, 2);
}

void DumpWriter::write_u4(u4 x) {
  u4 v;
  Bytes::put_Java_u4((address)&v, x);
  write_raw((void*)&v, 4);
}

void DumpWriter::write_u8(u8 x) {
  u8 v;
  Bytes::put_Java_u8((address)&v, x);
  write_raw((void*)&v, 8);
}

void DumpWriter::write_objectID(oop o) {
  address a = (address)o;
#ifdef _LP64
  write_u8((u8)a);
#else
  write_u4((u4)a);
#endif
}

void DumpWriter::write_symbolID(Symbol* s) {
  address a = (address)((uintptr_t)s);
#ifdef _LP64
  write_u8((u8)a);
#else
  write_u4((u4)a);
#endif
}

void DumpWriter::write_id(u4 x) {
#ifdef _LP64
  write_u8((u8) x);
#else
  write_u4(x);
#endif
}

// We use java mirror as the class ID
void DumpWriter::write_classID(Klass* k) {
  write_objectID(k->java_mirror());
}



// Support class with a collection of functions used when dumping the heap

class DumperSupport : AllStatic {
 public:

  // write a header of the given type
  static void write_header(DumpWriter* writer, hprofTag tag, u4 len);

  // returns hprof tag for the given type signature
  static hprofTag sig2tag(Symbol* sig);
  // returns hprof tag for the given basic type
  static hprofTag type2tag(BasicType type);

  // returns the size of the instance of the given class
  static u4 instance_size(Klass* k);

  // dump a jfloat
  static void dump_float(DumpWriter* writer, jfloat f);
  // dump a jdouble
  static void dump_double(DumpWriter* writer, jdouble d);
  // dumps the raw value of the given field
  static void dump_field_value(DumpWriter* writer, char type, address addr);
  // dumps static fields of the given class
  static void dump_static_fields(DumpWriter* writer, Klass* k);
  // dump the raw values of the instance fields of the given object
  static void dump_instance_fields(DumpWriter* writer, oop o);
  // dumps the definition of the instance fields for a given class
  static void dump_instance_field_descriptors(DumpWriter* writer, Klass* k);
  // creates HPROF_GC_INSTANCE_DUMP record for the given object
  static void dump_instance(DumpWriter* writer, oop o);
  // creates HPROF_GC_CLASS_DUMP record for the given class and each of its
  // array classes
  static void dump_class_and_array_classes(DumpWriter* writer, Klass* k);
  // creates HPROF_GC_CLASS_DUMP record for a given primitive array
  // class (and each multi-dimensional array class too)
  static void dump_basic_type_array_class(DumpWriter* writer, Klass* k);

  // creates HPROF_GC_OBJ_ARRAY_DUMP record for the given object array
  static void dump_object_array(DumpWriter* writer, objArrayOop array);
  // creates HPROF_GC_PRIM_ARRAY_DUMP record for the given type array
  static void dump_prim_array(DumpWriter* writer, typeArrayOop array);
  // create HPROF_FRAME record for the given method and bci
  static void dump_stack_frame(DumpWriter* writer, int frame_serial_num, int class_serial_num, Method* m, int bci);
};

// write a header of the given type
void DumperSupport:: write_header(DumpWriter* writer, hprofTag tag, u4 len) {
  writer->write_u1((u1)tag);
  writer->write_u4(0);                  // current ticks
  writer->write_u4(len);
}

// returns hprof tag for the given type signature
hprofTag DumperSupport::sig2tag(Symbol* sig) {
  switch (sig->byte_at(0)) {
    case JVM_SIGNATURE_CLASS    : return HPROF_NORMAL_OBJECT;
    case JVM_SIGNATURE_ARRAY    : return HPROF_NORMAL_OBJECT;
    case JVM_SIGNATURE_BYTE     : return HPROF_BYTE;
    case JVM_SIGNATURE_CHAR     : return HPROF_CHAR;
    case JVM_SIGNATURE_FLOAT    : return HPROF_FLOAT;
    case JVM_SIGNATURE_DOUBLE   : return HPROF_DOUBLE;
    case JVM_SIGNATURE_INT      : return HPROF_INT;
    case JVM_SIGNATURE_LONG     : return HPROF_LONG;
    case JVM_SIGNATURE_SHORT    : return HPROF_SHORT;
    case JVM_SIGNATURE_BOOLEAN  : return HPROF_BOOLEAN;
    default : ShouldNotReachHere(); /* to shut up compiler */ return HPROF_BYTE;
  }
}

hprofTag DumperSupport::type2tag(BasicType type) {
  switch (type) {
    case T_BYTE     : return HPROF_BYTE;
    case T_CHAR     : return HPROF_CHAR;
    case T_FLOAT    : return HPROF_FLOAT;
    case T_DOUBLE   : return HPROF_DOUBLE;
    case T_INT      : return HPROF_INT;
    case T_LONG     : return HPROF_LONG;
    case T_SHORT    : return HPROF_SHORT;
    case T_BOOLEAN  : return HPROF_BOOLEAN;
    default : ShouldNotReachHere(); /* to shut up compiler */ return HPROF_BYTE;
  }
}

// dump a jfloat
void DumperSupport::dump_float(DumpWriter* writer, jfloat f) {
  if (g_isnan(f)) {
    writer->write_u4(0x7fc00000);    // collapsing NaNs
  } else {
    union {
      int i;
      float f;
    } u;
    u.f = (float)f;
    writer->write_u4((u4)u.i);
  }
}

// dump a jdouble
void DumperSupport::dump_double(DumpWriter* writer, jdouble d) {
  union {
    jlong l;
    double d;
  } u;
  if (g_isnan(d)) {                 // collapsing NaNs
    u.l = (jlong)(0x7ff80000);
    u.l = (u.l << 32);
  } else {
    u.d = (double)d;
  }
  writer->write_u8((u8)u.l);
}

// dumps the raw value of the given field
void DumperSupport::dump_field_value(DumpWriter* writer, char type, address addr) {
  switch (type) {
    case JVM_SIGNATURE_CLASS :
    case JVM_SIGNATURE_ARRAY : {
      oop o;
      if (UseCompressedOops) {
        o = oopDesc::load_decode_heap_oop((narrowOop*)addr);
      } else {
        o = oopDesc::load_decode_heap_oop((oop*)addr);
      }

      // reflection and sun.misc.Unsafe classes may have a reference to a
      // Klass* so filter it out.
      assert(o->is_oop_or_null(), "Expected an oop or NULL at " PTR_FORMAT, p2i(o));
      writer->write_objectID(o);
      break;
    }
    case JVM_SIGNATURE_BYTE     : {
      jbyte* b = (jbyte*)addr;
      writer->write_u1((u1)*b);
      break;
    }
    case JVM_SIGNATURE_CHAR     : {
      jchar* c = (jchar*)addr;
      writer->write_u2((u2)*c);
      break;
    }
    case JVM_SIGNATURE_SHORT : {
      jshort* s = (jshort*)addr;
      writer->write_u2((u2)*s);
      break;
    }
    case JVM_SIGNATURE_FLOAT : {
      jfloat* f = (jfloat*)addr;
      dump_float(writer, *f);
      break;
    }
    case JVM_SIGNATURE_DOUBLE : {
      jdouble* f = (jdouble*)addr;
      dump_double(writer, *f);
      break;
    }
    case JVM_SIGNATURE_INT : {
      jint* i = (jint*)addr;
      writer->write_u4((u4)*i);
      break;
    }
    case JVM_SIGNATURE_LONG     : {
      jlong* l = (jlong*)addr;
      writer->write_u8((u8)*l);
      break;
    }
    case JVM_SIGNATURE_BOOLEAN : {
      jboolean* b = (jboolean*)addr;
      writer->write_u1((u1)*b);
      break;
    }
    default : ShouldNotReachHere();
  }
}

// returns the size of the instance of the given class
u4 DumperSupport::instance_size(Klass* k) {
  HandleMark hm;
  instanceKlassHandle ikh = instanceKlassHandle(Thread::current(), k);

  int size = 0;

  for (FieldStream fld(ikh, false, false); !fld.eos(); fld.next()) {
    if (!fld.access_flags().is_static()) {
      Symbol* sig = fld.signature();
      switch (sig->byte_at(0)) {
        case JVM_SIGNATURE_CLASS   :
        case JVM_SIGNATURE_ARRAY   : size += oopSize; break;

        case JVM_SIGNATURE_BYTE    :
        case JVM_SIGNATURE_BOOLEAN : size += 1; break;

        case JVM_SIGNATURE_CHAR    :
        case JVM_SIGNATURE_SHORT   : size += 2; break;

        case JVM_SIGNATURE_INT     :
        case JVM_SIGNATURE_FLOAT   : size += 4; break;

        case JVM_SIGNATURE_LONG    :
        case JVM_SIGNATURE_DOUBLE  : size += 8; break;

        default : ShouldNotReachHere();
      }
    }
  }
  return (u4)size;
}

// dumps static fields of the given class
void DumperSupport::dump_static_fields(DumpWriter* writer, Klass* k) {
  HandleMark hm;
  instanceKlassHandle ikh = instanceKlassHandle(Thread::current(), k);

  // pass 1 - count the static fields
  u2 field_count = 0;
  for (FieldStream fldc(ikh, true, true); !fldc.eos(); fldc.next()) {
    if (fldc.access_flags().is_static()) field_count++;
  }

  writer->write_u2(field_count);

  // pass 2 - dump the field descriptors and raw values
  for (FieldStream fld(ikh, true, true); !fld.eos(); fld.next()) {
    if (fld.access_flags().is_static()) {
      Symbol* sig = fld.signature();

      writer->write_symbolID(fld.name());   // name
      writer->write_u1(sig2tag(sig));       // type

      // value
      int offset = fld.offset();
      address addr = (address)ikh->java_mirror() + offset;

      dump_field_value(writer, sig->byte_at(0), addr);
    }
  }
}

// dump the raw values of the instance fields of the given object
void DumperSupport::dump_instance_fields(DumpWriter* writer, oop o) {
  HandleMark hm;
  instanceKlassHandle ikh = instanceKlassHandle(Thread::current(), o->klass());

  for (FieldStream fld(ikh, false, false); !fld.eos(); fld.next()) {
    if (!fld.access_flags().is_static()) {
      Symbol* sig = fld.signature();
      address addr = (address)o + fld.offset();

      dump_field_value(writer, sig->byte_at(0), addr);
    }
  }
}

// dumps the definition of the instance fields for a given class
void DumperSupport::dump_instance_field_descriptors(DumpWriter* writer, Klass* k) {
  HandleMark hm;
  instanceKlassHandle ikh = instanceKlassHandle(Thread::current(), k);

  // pass 1 - count the instance fields
  u2 field_count = 0;
  for (FieldStream fldc(ikh, true, true); !fldc.eos(); fldc.next()) {
    if (!fldc.access_flags().is_static()) field_count++;
  }

  writer->write_u2(field_count);

  // pass 2 - dump the field descriptors
  for (FieldStream fld(ikh, true, true); !fld.eos(); fld.next()) {
    if (!fld.access_flags().is_static()) {
      Symbol* sig = fld.signature();

      writer->write_symbolID(fld.name());                   // name
      writer->write_u1(sig2tag(sig));       // type
    }
  }
}

// creates HPROF_GC_INSTANCE_DUMP record for the given object
void DumperSupport::dump_instance(DumpWriter* writer, oop o) {
  Klass* k = o->klass();

  writer->write_u1(HPROF_GC_INSTANCE_DUMP);
  writer->write_objectID(o);
  writer->write_u4(STACK_TRACE_ID);

  // class ID
  writer->write_classID(k);

  // number of bytes that follow
  writer->write_u4(instance_size(k) );

  // field values
  dump_instance_fields(writer, o);
}

// creates HPROF_GC_CLASS_DUMP record for the given class and each of
// its array classes
void DumperSupport::dump_class_and_array_classes(DumpWriter* writer, Klass* k) {
  Klass* klass = k;
  assert(klass->oop_is_instance(), "not an InstanceKlass");
  InstanceKlass* ik = (InstanceKlass*)klass;

  // Ignore the class if it hasn't been initialized yet
  if (!ik->is_linked()) {
    return;
  }

  writer->write_u1(HPROF_GC_CLASS_DUMP);

  // class ID
  writer->write_classID(ik);
  writer->write_u4(STACK_TRACE_ID);

  // super class ID
  Klass* java_super = ik->java_super();
  if (java_super == NULL) {
    writer->write_objectID(oop(NULL));
  } else {
    writer->write_classID(java_super);
  }

  writer->write_objectID(ik->class_loader());
  writer->write_objectID(ik->signers());
  writer->write_objectID(ik->protection_domain());

  // reserved
  writer->write_objectID(oop(NULL));
  writer->write_objectID(oop(NULL));

  // instance size
  writer->write_u4(DumperSupport::instance_size(k));

  // size of constant pool - ignored by HAT 1.1
  writer->write_u2(0);

  // number of static fields
  dump_static_fields(writer, k);

  // description of instance fields
  dump_instance_field_descriptors(writer, k);

  // array classes
  k = klass->array_klass_or_null();
  while (k != NULL) {
    Klass* klass = k;
    assert(klass->oop_is_objArray(), "not an ObjArrayKlass");

    writer->write_u1(HPROF_GC_CLASS_DUMP);
    writer->write_classID(klass);
    writer->write_u4(STACK_TRACE_ID);

    // super class of array classes is java.lang.Object
    java_super = klass->java_super();
    assert(java_super != NULL, "checking");
    writer->write_classID(java_super);

    writer->write_objectID(ik->class_loader());
    writer->write_objectID(ik->signers());
    writer->write_objectID(ik->protection_domain());

    writer->write_objectID(oop(NULL));    // reserved
    writer->write_objectID(oop(NULL));
    writer->write_u4(0);             // instance size
    writer->write_u2(0);             // constant pool
    writer->write_u2(0);             // static fields
    writer->write_u2(0);             // instance fields

    // get the array class for the next rank
    k = klass->array_klass_or_null();
  }
}

// creates HPROF_GC_CLASS_DUMP record for a given primitive array
// class (and each multi-dimensional array class too)
void DumperSupport::dump_basic_type_array_class(DumpWriter* writer, Klass* k) {
 // array classes
 while (k != NULL) {
    Klass* klass = k;

    writer->write_u1(HPROF_GC_CLASS_DUMP);
    writer->write_classID(klass);
    writer->write_u4(STACK_TRACE_ID);

    // super class of array classes is java.lang.Object
    Klass* java_super = klass->java_super();
    assert(java_super != NULL, "checking");
    writer->write_classID(java_super);

    writer->write_objectID(oop(NULL));    // loader
    writer->write_objectID(oop(NULL));    // signers
    writer->write_objectID(oop(NULL));    // protection domain

    writer->write_objectID(oop(NULL));    // reserved
    writer->write_objectID(oop(NULL));
    writer->write_u4(0);             // instance size
    writer->write_u2(0);             // constant pool
    writer->write_u2(0);             // static fields
    writer->write_u2(0);             // instance fields

    // get the array class for the next rank
    k = klass->array_klass_or_null();
  }
}

// creates HPROF_GC_OBJ_ARRAY_DUMP record for the given object array
void DumperSupport::dump_object_array(DumpWriter* writer, objArrayOop array) {

  writer->write_u1(HPROF_GC_OBJ_ARRAY_DUMP);
  writer->write_objectID(array);
  writer->write_u4(STACK_TRACE_ID);
  writer->write_u4((u4)array->length());

  // array class ID
  writer->write_classID(array->klass());

  // [id]* elements
  for (int index=0; index<array->length(); index++) {
    oop o = array->obj_at(index);
    writer->write_objectID(o);
  }
}

#define WRITE_ARRAY(Array, Type, Size) \
  for (int i=0; i<Array->length(); i++) { writer->write_##Size((Size)array->Type##_at(i)); }


// creates HPROF_GC_PRIM_ARRAY_DUMP record for the given type array
void DumperSupport::dump_prim_array(DumpWriter* writer, typeArrayOop array) {
  BasicType type = TypeArrayKlass::cast(array->klass())->element_type();

  writer->write_u1(HPROF_GC_PRIM_ARRAY_DUMP);
  writer->write_objectID(array);
  writer->write_u4(STACK_TRACE_ID);
  writer->write_u4((u4)array->length());
  writer->write_u1(type2tag(type));

  // nothing to copy
  if (array->length() == 0) {
    return;
  }

  // If the byte ordering is big endian then we can copy most types directly
  int length_in_bytes = array->length() * type2aelembytes(type);
  assert(length_in_bytes > 0, "nothing to copy");

  switch (type) {
    case T_INT : {
      if (Bytes::is_Java_byte_ordering_different()) {
        WRITE_ARRAY(array, int, u4);
      } else {
        writer->write_raw((void*)(array->int_at_addr(0)), length_in_bytes);
      }
      break;
    }
    case T_BYTE : {
      writer->write_raw((void*)(array->byte_at_addr(0)), length_in_bytes);
      break;
    }
    case T_CHAR : {
      if (Bytes::is_Java_byte_ordering_different()) {
        WRITE_ARRAY(array, char, u2);
      } else {
        writer->write_raw((void*)(array->char_at_addr(0)), length_in_bytes);
      }
      break;
    }
    case T_SHORT : {
      if (Bytes::is_Java_byte_ordering_different()) {
        WRITE_ARRAY(array, short, u2);
      } else {
        writer->write_raw((void*)(array->short_at_addr(0)), length_in_bytes);
      }
      break;
    }
    case T_BOOLEAN : {
      if (Bytes::is_Java_byte_ordering_different()) {
        WRITE_ARRAY(array, bool, u1);
      } else {
        writer->write_raw((void*)(array->bool_at_addr(0)), length_in_bytes);
      }
      break;
    }
    case T_LONG : {
      if (Bytes::is_Java_byte_ordering_different()) {
        WRITE_ARRAY(array, long, u8);
      } else {
        writer->write_raw((void*)(array->long_at_addr(0)), length_in_bytes);
      }
      break;
    }

    // handle float/doubles in a special value to ensure than NaNs are
    // written correctly. TO DO: Check if we can avoid this on processors that
    // use IEEE 754.

    case T_FLOAT : {
      for (int i=0; i<array->length(); i++) {
        dump_float( writer, array->float_at(i) );
      }
      break;
    }
    case T_DOUBLE : {
      for (int i=0; i<array->length(); i++) {
        dump_double( writer, array->double_at(i) );
      }
      break;
    }
    default : ShouldNotReachHere();
  }
}

// create a HPROF_FRAME record of the given Method* and bci
void DumperSupport::dump_stack_frame(DumpWriter* writer,
                                     int frame_serial_num,
                                     int class_serial_num,
                                     Method* m,
                                     int bci) {
  int line_number;
  if (m->is_native()) {
    line_number = -3;  // native frame
  } else {
    line_number = m->line_number_from_bci(bci);
  }

  write_header(writer, HPROF_FRAME, 4*oopSize + 2*sizeof(u4));
  writer->write_id(frame_serial_num);               // frame serial number
  writer->write_symbolID(m->name());                // method's name
  writer->write_symbolID(m->signature());           // method's signature

  assert(m->method_holder()->oop_is_instance(), "not InstanceKlass");
  writer->write_symbolID(m->method_holder()->source_file_name());  // source file name
  writer->write_u4(class_serial_num);               // class serial number
  writer->write_u4((u4) line_number);               // line number
}


// Support class used to generate HPROF_UTF8 records from the entries in the
// SymbolTable.

class SymbolTableDumper : public SymbolClosure {
 private:
  DumpWriter* _writer;
  DumpWriter* writer() const                { return _writer; }
 public:
  SymbolTableDumper(DumpWriter* writer)     { _writer = writer; }
  void do_symbol(Symbol** p);
};

void SymbolTableDumper::do_symbol(Symbol** p) {
  ResourceMark rm;
  Symbol* sym = load_symbol(p);
  int len = sym->utf8_length();
  if (len > 0) {
    char* s = sym->as_utf8();
    DumperSupport::write_header(writer(), HPROF_UTF8, oopSize + len);
    writer()->write_symbolID(sym);
    writer()->write_raw(s, len);
  }
}

// Support class used to generate HPROF_GC_ROOT_JNI_LOCAL records

class JNILocalsDumper : public OopClosure {
 private:
  DumpWriter* _writer;
  u4 _thread_serial_num;
  int _frame_num;
  DumpWriter* writer() const                { return _writer; }
 public:
  JNILocalsDumper(DumpWriter* writer, u4 thread_serial_num) {
    _writer = writer;
    _thread_serial_num = thread_serial_num;
    _frame_num = -1;  // default - empty stack
  }
  void set_frame_number(int n) { _frame_num = n; }
  void do_oop(oop* obj_p);
  void do_oop(narrowOop* obj_p) { ShouldNotReachHere(); }
};


void JNILocalsDumper::do_oop(oop* obj_p) {
  // ignore null or deleted handles
  oop o = *obj_p;
  if (o != NULL && o != JNIHandles::deleted_handle()) {
    writer()->write_u1(HPROF_GC_ROOT_JNI_LOCAL);
    writer()->write_objectID(o);
    writer()->write_u4(_thread_serial_num);
    writer()->write_u4((u4)_frame_num);
  }
}


// Support class used to generate HPROF_GC_ROOT_JNI_GLOBAL records

class JNIGlobalsDumper : public OopClosure {
 private:
  DumpWriter* _writer;
  DumpWriter* writer() const                { return _writer; }

 public:
  JNIGlobalsDumper(DumpWriter* writer) {
    _writer = writer;
  }
  void do_oop(oop* obj_p);
  void do_oop(narrowOop* obj_p) { ShouldNotReachHere(); }
};

void JNIGlobalsDumper::do_oop(oop* obj_p) {
  oop o = *obj_p;

  // ignore these
  if (o == NULL || o == JNIHandles::deleted_handle()) return;

  // we ignore global ref to symbols and other internal objects
  if (o->is_instance() || o->is_objArray() || o->is_typeArray()) {
    writer()->write_u1(HPROF_GC_ROOT_JNI_GLOBAL);
    writer()->write_objectID(o);
    writer()->write_objectID((oopDesc*)obj_p);      // global ref ID
  }
};


// Support class used to generate HPROF_GC_ROOT_MONITOR_USED records

class MonitorUsedDumper : public OopClosure {
 private:
  DumpWriter* _writer;
  DumpWriter* writer() const                { return _writer; }
 public:
  MonitorUsedDumper(DumpWriter* writer) {
    _writer = writer;
  }
  void do_oop(oop* obj_p) {
    writer()->write_u1(HPROF_GC_ROOT_MONITOR_USED);
    writer()->write_objectID(*obj_p);
  }
  void do_oop(narrowOop* obj_p) { ShouldNotReachHere(); }
};


// Support class used to generate HPROF_GC_ROOT_STICKY_CLASS records

class StickyClassDumper : public KlassClosure {
 private:
  DumpWriter* _writer;
  DumpWriter* writer() const                { return _writer; }
 public:
  StickyClassDumper(DumpWriter* writer) {
    _writer = writer;
  }
  void do_klass(Klass* k) {
    if (k->oop_is_instance()) {
      InstanceKlass* ik = InstanceKlass::cast(k);
        writer()->write_u1(HPROF_GC_ROOT_STICKY_CLASS);
        writer()->write_classID(ik);
      }
    }
};


class VM_HeapDumper;

// Support class using when iterating over the heap.

class HeapObjectDumper : public ObjectClosure {
 private:
  VM_HeapDumper* _dumper;
  DumpWriter* _writer;

  VM_HeapDumper* dumper()               { return _dumper; }
  DumpWriter* writer()                  { return _writer; }

  // used to indicate that a record has been writen
  void mark_end_of_record();

 public:
  HeapObjectDumper(VM_HeapDumper* dumper, DumpWriter* writer) {
    _dumper = dumper;
    _writer = writer;
  }

  // called for each object in the heap
  void do_object(oop o);
};

void HeapObjectDumper::do_object(oop o) {
  // hide the sentinel for deleted handles
  if (o == JNIHandles::deleted_handle()) return;

  // skip classes as these emitted as HPROF_GC_CLASS_DUMP records
  if (o->klass() == SystemDictionary::Class_klass()) {
    if (!java_lang_Class::is_primitive(o)) {
      return;
    }
  }

  // create a HPROF_GC_INSTANCE record for each object
  if (o->is_instance()) {
    DumperSupport::dump_instance(writer(), o);
    mark_end_of_record();
  } else {
    // create a HPROF_GC_OBJ_ARRAY_DUMP record for each object array
    if (o->is_objArray()) {
      DumperSupport::dump_object_array(writer(), objArrayOop(o));
      mark_end_of_record();
    } else {
      // create a HPROF_GC_PRIM_ARRAY_DUMP record for each type array
      if (o->is_typeArray()) {
        DumperSupport::dump_prim_array(writer(), typeArrayOop(o));
        mark_end_of_record();
      }
    }
  }
}

// The VM operation that performs the heap dump
class VM_HeapDumper : public VM_GC_Operation {
 private:
  static VM_HeapDumper* _global_dumper;
  static DumpWriter*    _global_writer;
  DumpWriter*           _local_writer;
  JavaThread*           _oome_thread;
  Method*               _oome_constructor;
  bool _gc_before_heap_dump;
  bool _is_segmented_dump;
  jlong _dump_start;
  GrowableArray<Klass*>* _klass_map;
  ThreadStackTrace** _stack_traces;
  int _num_threads;

  // accessors and setters
  static VM_HeapDumper* dumper()         {  assert(_global_dumper != NULL, "Error"); return _global_dumper; }
  static DumpWriter* writer()            {  assert(_global_writer != NULL, "Error"); return _global_writer; }
  void set_global_dumper() {
    assert(_global_dumper == NULL, "Error");
    _global_dumper = this;
  }
  void set_global_writer() {
    assert(_global_writer == NULL, "Error");
    _global_writer = _local_writer;
  }
  void clear_global_dumper() { _global_dumper = NULL; }
  void clear_global_writer() { _global_writer = NULL; }

  bool is_segmented_dump() const                { return _is_segmented_dump; }
  void set_segmented_dump()                     { _is_segmented_dump = true; }
  jlong dump_start() const                      { return _dump_start; }
  void set_dump_start(jlong pos);

  bool skip_operation() const;

  // writes a HPROF_LOAD_CLASS record
  static void do_load_class(Klass* k);

  // writes a HPROF_GC_CLASS_DUMP record for the given class
  // (and each array class too)
  static void do_class_dump(Klass* k);

  // writes a HPROF_GC_CLASS_DUMP records for a given basic type
  // array (and each multi-dimensional array too)
  static void do_basic_type_array_class_dump(Klass* k);

  // HPROF_GC_ROOT_THREAD_OBJ records
  int do_thread(JavaThread* thread, u4 thread_serial_num);
  void do_threads();

  void add_class_serial_number(Klass* k, int serial_num) {
    _klass_map->at_put_grow(serial_num, k);
  }

  // HPROF_TRACE and HPROF_FRAME records
  void dump_stack_traces();

  // writes a HPROF_HEAP_DUMP or HPROF_HEAP_DUMP_SEGMENT record
  void write_dump_header();

  // fixes up the length of the current dump record
  void write_current_dump_record_length();

  // fixes up the current dump record )and writes HPROF_HEAP_DUMP_END
  // record in the case of a segmented heap dump)
  void end_of_dump();

 public:
  VM_HeapDumper(DumpWriter* writer, bool gc_before_heap_dump, bool oome) :
    VM_GC_Operation(0 /* total collections,      dummy, ignored */,
                    GCCause::_heap_dump /* GC Cause */,
                    0 /* total full collections, dummy, ignored */,
                    gc_before_heap_dump) {
    _local_writer = writer;
    _gc_before_heap_dump = gc_before_heap_dump;
    _is_segmented_dump = false;
    _dump_start = (jlong)-1;
    _klass_map = new (ResourceObj::C_HEAP, mtInternal) GrowableArray<Klass*>(INITIAL_CLASS_COUNT, true);
    _stack_traces = NULL;
    _num_threads = 0;
    if (oome) {
      assert(!Thread::current()->is_VM_thread(), "Dump from OutOfMemoryError cannot be called by the VMThread");
      // get OutOfMemoryError zero-parameter constructor
      InstanceKlass* oome_ik = InstanceKlass::cast(SystemDictionary::OutOfMemoryError_klass());
      _oome_constructor = oome_ik->find_method(vmSymbols::object_initializer_name(),
                                                          vmSymbols::void_method_signature());
      // get thread throwing OOME when generating the heap dump at OOME
      _oome_thread = JavaThread::current();
    } else {
      _oome_thread = NULL;
      _oome_constructor = NULL;
    }
  }
  ~VM_HeapDumper() {
    if (_stack_traces != NULL) {
      for (int i=0; i < _num_threads; i++) {
        delete _stack_traces[i];
      }
      FREE_C_HEAP_ARRAY(ThreadStackTrace*, _stack_traces);
    }
    delete _klass_map;
  }

  VMOp_Type type() const { return VMOp_HeapDumper; }
  // used to mark sub-record boundary
  void check_segment_length();
  void doit();
};

VM_HeapDumper* VM_HeapDumper::_global_dumper = NULL;
DumpWriter*    VM_HeapDumper::_global_writer = NULL;

bool VM_HeapDumper::skip_operation() const {
  return false;
}

// sets the dump starting position
void VM_HeapDumper::set_dump_start(jlong pos) {
  _dump_start = pos;
}

 // writes a HPROF_HEAP_DUMP or HPROF_HEAP_DUMP_SEGMENT record
void VM_HeapDumper::write_dump_header() {
  if (writer()->is_open()) {
    if (is_segmented_dump()) {
      writer()->write_u1(HPROF_HEAP_DUMP_SEGMENT);
    } else {
      writer()->write_u1(HPROF_HEAP_DUMP);
    }
    writer()->write_u4(0); // current ticks

    // record the starting position for the dump (its length will be fixed up later)
    set_dump_start(writer()->current_offset());
    writer()->write_u4(0);
  }
}

// fixes up the length of the current dump record
void VM_HeapDumper::write_current_dump_record_length() {
  if (writer()->is_open()) {
    assert(dump_start() >= 0, "no dump start recorded");

    // calculate the size of the dump record
    jlong dump_end = writer()->current_offset();
    jlong dump_len = (dump_end - dump_start() - 4);

    // record length must fit in a u4
    if (dump_len > (jlong)(4L*(jlong)G)) {
      warning("record is too large");
    }

    // seek to the dump start and fix-up the length
    writer()->seek_to_offset(dump_start());
    writer()->write_u4((u4)dump_len);

    // adjust the total size written to keep the bytes written correct.
    writer()->adjust_bytes_written(-((long) sizeof(u4)));

    // seek to dump end so we can continue
    writer()->seek_to_offset(dump_end);

    // no current dump record
    set_dump_start((jlong)-1);
  }
}

// used on a sub-record boundary to check if we need to start a
// new segment.
void VM_HeapDumper::check_segment_length() {
  if (writer()->is_open()) {
    if (is_segmented_dump()) {
      // don't use current_offset that would be too expensive on a per record basis
      jlong dump_end = writer()->bytes_written() + writer()->bytes_unwritten();
      assert(dump_end == writer()->current_offset(), "checking");
      jlong dump_len = (dump_end - dump_start() - 4);
      assert(dump_len >= 0 && dump_len <= max_juint, "bad dump length");

      if (dump_len > (jlong)HeapDumpSegmentSize) {
        write_current_dump_record_length();
        write_dump_header();
      }
    }
  }
}

// fixes up the current dump record )and writes HPROF_HEAP_DUMP_END
// record in the case of a segmented heap dump)
void VM_HeapDumper::end_of_dump() {
  if (writer()->is_open()) {
    write_current_dump_record_length();

    // for segmented dump we write the end record
    if (is_segmented_dump()) {
      writer()->write_u1(HPROF_HEAP_DUMP_END);
      writer()->write_u4(0);
      writer()->write_u4(0);
    }
  }
}

// marks sub-record boundary
void HeapObjectDumper::mark_end_of_record() {
  dumper()->check_segment_length();
}

// writes a HPROF_LOAD_CLASS record for the class (and each of its
// array classes)
void VM_HeapDumper::do_load_class(Klass* k) {
  static u4 class_serial_num = 0;

  // len of HPROF_LOAD_CLASS record
  u4 remaining = 2*oopSize + 2*sizeof(u4);

  // write a HPROF_LOAD_CLASS for the class and each array class
  do {
    DumperSupport::write_header(writer(), HPROF_LOAD_CLASS, remaining);

    // class serial number is just a number
    writer()->write_u4(++class_serial_num);

    // class ID
    Klass* klass = k;
    writer()->write_classID(klass);

    // add the Klass* and class serial number pair
    dumper()->add_class_serial_number(klass, class_serial_num);

    writer()->write_u4(STACK_TRACE_ID);

    // class name ID
    Symbol* name = klass->name();
    writer()->write_symbolID(name);

    // write a LOAD_CLASS record for the array type (if it exists)
    k = klass->array_klass_or_null();
  } while (k != NULL);
}

// writes a HPROF_GC_CLASS_DUMP record for the given class
void VM_HeapDumper::do_class_dump(Klass* k) {
  if (k->oop_is_instance()) {
    DumperSupport::dump_class_and_array_classes(writer(), k);
  }
}

// writes a HPROF_GC_CLASS_DUMP records for a given basic type
// array (and each multi-dimensional array too)
void VM_HeapDumper::do_basic_type_array_class_dump(Klass* k) {
  DumperSupport::dump_basic_type_array_class(writer(), k);
}

// Walk the stack of the given thread.
// Dumps a HPROF_GC_ROOT_JAVA_FRAME record for each local
// Dumps a HPROF_GC_ROOT_JNI_LOCAL record for each JNI local
//
// It returns the number of Java frames in this thread stack
int VM_HeapDumper::do_thread(JavaThread* java_thread, u4 thread_serial_num) {
  JNILocalsDumper blk(writer(), thread_serial_num);

  oop threadObj = java_thread->threadObj();
  assert(threadObj != NULL, "sanity check");

  int stack_depth = 0;
  if (java_thread->has_last_Java_frame()) {

    // vframes are resource allocated
    Thread* current_thread = Thread::current();
    ResourceMark rm(current_thread);
    HandleMark hm(current_thread);

    RegisterMap reg_map(java_thread);
    frame f = java_thread->last_frame();
    vframe* vf = vframe::new_vframe(&f, &reg_map, java_thread);
    frame* last_entry_frame = NULL;
    int extra_frames = 0;

    if (java_thread == _oome_thread && _oome_constructor != NULL) {
      extra_frames++;
    }
    while (vf != NULL) {
      blk.set_frame_number(stack_depth);
      if (vf->is_java_frame()) {

        // java frame (interpreted, compiled, ...)
        javaVFrame *jvf = javaVFrame::cast(vf);
        if (!(jvf->method()->is_native())) {
          StackValueCollection* locals = jvf->locals();
          for (int slot=0; slot<locals->size(); slot++) {
            if (locals->at(slot)->type() == T_OBJECT) {
              oop o = locals->obj_at(slot)();

              if (o != NULL) {
                writer()->write_u1(HPROF_GC_ROOT_JAVA_FRAME);
                writer()->write_objectID(o);
                writer()->write_u4(thread_serial_num);
                writer()->write_u4((u4) (stack_depth + extra_frames));
              }
            }
          }
          StackValueCollection *exprs = jvf->expressions();
          for(int index = 0; index < exprs->size(); index++) {
            if (exprs->at(index)->type() == T_OBJECT) {
               oop o = exprs->obj_at(index)();
               if (o != NULL) {
                 writer()->write_u1(HPROF_GC_ROOT_JAVA_FRAME);
                 writer()->write_objectID(o);
                 writer()->write_u4(thread_serial_num);
                 writer()->write_u4((u4) (stack_depth + extra_frames));
               }
             }
          }
        } else {
          // native frame
          if (stack_depth == 0) {
            // JNI locals for the top frame.
            java_thread->active_handles()->oops_do(&blk);
          } else {
            if (last_entry_frame != NULL) {
              // JNI locals for the entry frame
              assert(last_entry_frame->is_entry_frame(), "checking");
              last_entry_frame->entry_frame_call_wrapper()->handles()->oops_do(&blk);
            }
          }
        }
        // increment only for Java frames
        stack_depth++;
        last_entry_frame = NULL;

      } else {
        // externalVFrame - if it's an entry frame then report any JNI locals
        // as roots when we find the corresponding native javaVFrame
        frame* fr = vf->frame_pointer();
        assert(fr != NULL, "sanity check");
        if (fr->is_entry_frame()) {
          last_entry_frame = fr;
        }
      }
      vf = vf->sender();
    }
  } else {
    // no last java frame but there may be JNI locals
    java_thread->active_handles()->oops_do(&blk);
  }
  return stack_depth;
}


// write a HPROF_GC_ROOT_THREAD_OBJ record for each java thread. Then walk
// the stack so that locals and JNI locals are dumped.
void VM_HeapDumper::do_threads() {
  for (int i=0; i < _num_threads; i++) {
    JavaThread* thread = _stack_traces[i]->thread();
    oop threadObj = thread->threadObj();
    u4 thread_serial_num = i+1;
    u4 stack_serial_num = thread_serial_num + STACK_TRACE_ID;
    writer()->write_u1(HPROF_GC_ROOT_THREAD_OBJ);
    writer()->write_objectID(threadObj);
    writer()->write_u4(thread_serial_num);  // thread number
    writer()->write_u4(stack_serial_num);   // stack trace serial number
    int num_frames = do_thread(thread, thread_serial_num);
    assert(num_frames == _stack_traces[i]->get_stack_depth(),
           "total number of Java frames not matched");
  }
}


// The VM operation that dumps the heap. The dump consists of the following
// records:
//
//  HPROF_HEADER
//  [HPROF_UTF8]*
//  [HPROF_LOAD_CLASS]*
//  [[HPROF_FRAME]*|HPROF_TRACE]*
//  [HPROF_GC_CLASS_DUMP]*
//  HPROF_HEAP_DUMP
//
// The HPROF_TRACE records represent the stack traces where the heap dump
// is generated and a "dummy trace" record which does not include
// any frames. The dummy trace record is used to be referenced as the
// unknown object alloc site.
//
// The HPROF_HEAP_DUMP record has a length following by sub-records. To allow
// the heap dump be generated in a single pass we remember the position of
// the dump length and fix it up after all sub-records have been written.
// To generate the sub-records we iterate over the heap, writing
// HPROF_GC_INSTANCE_DUMP, HPROF_GC_OBJ_ARRAY_DUMP, and HPROF_GC_PRIM_ARRAY_DUMP
// records as we go. Once that is done we write records for some of the GC
// roots.

void VM_HeapDumper::doit() {

  HandleMark hm;
  CollectedHeap* ch = Universe::heap();

  ch->ensure_parsability(false); // must happen, even if collection does
                                 // not happen (e.g. due to GC_locker)

  if (_gc_before_heap_dump) {
    if (GC_locker::is_active()) {
      warning("GC locker is held; pre-heapdump GC was skipped");
    } else {
      ch->collect_as_vm_thread(GCCause::_heap_dump);
    }
  }

  // At this point we should be the only dumper active, so
  // the following should be safe.
  set_global_dumper();
  set_global_writer();

  // Write the file header - use 1.0.2 for large heaps, otherwise 1.0.1
  size_t used = ch->used();
  const char* header;
  if (used > SegmentedHeapDumpThreshold) {
    set_segmented_dump();
    header = "JAVA PROFILE 1.0.2";
  } else {
    header = "JAVA PROFILE 1.0.1";
  }

  // header is few bytes long - no chance to overflow int
  writer()->write_raw((void*)header, (int)strlen(header));
  writer()->write_u1(0); // terminator
  writer()->write_u4(oopSize);
  writer()->write_u8(os::javaTimeMillis());

  // HPROF_UTF8 records
  SymbolTableDumper sym_dumper(writer());
  SymbolTable::symbols_do(&sym_dumper);

  // write HPROF_LOAD_CLASS records
  ClassLoaderDataGraph::classes_do(&do_load_class);
  Universe::basic_type_classes_do(&do_load_class);

  // write HPROF_FRAME and HPROF_TRACE records
  // this must be called after _klass_map is built when iterating the classes above.
  dump_stack_traces();

  // write HPROF_HEAP_DUMP or HPROF_HEAP_DUMP_SEGMENT
  write_dump_header();

  // Writes HPROF_GC_CLASS_DUMP records
  ClassLoaderDataGraph::classes_do(&do_class_dump);
  Universe::basic_type_classes_do(&do_basic_type_array_class_dump);
  check_segment_length();

  // writes HPROF_GC_INSTANCE_DUMP records.
  // After each sub-record is written check_segment_length will be invoked. When
  // generated a segmented heap dump this allows us to check if the current
  // segment exceeds a threshold and if so, then a new segment is started.
  // The HPROF_GC_CLASS_DUMP and HPROF_GC_INSTANCE_DUMP are the vast bulk
  // of the heap dump.
  HeapObjectDumper obj_dumper(this, writer());
  Universe::heap()->safe_object_iterate(&obj_dumper);

  // HPROF_GC_ROOT_THREAD_OBJ + frames + jni locals
  do_threads();
  check_segment_length();

  // HPROF_GC_ROOT_MONITOR_USED
  MonitorUsedDumper mon_dumper(writer());
  ObjectSynchronizer::oops_do(&mon_dumper);
  check_segment_length();

  // HPROF_GC_ROOT_JNI_GLOBAL
  JNIGlobalsDumper jni_dumper(writer());
  JNIHandles::oops_do(&jni_dumper);
  check_segment_length();

  // HPROF_GC_ROOT_STICKY_CLASS
  StickyClassDumper class_dumper(writer());
  SystemDictionary::always_strong_classes_do(&class_dumper);

  // fixes up the length of the dump record. In the case of a segmented
  // heap then the HPROF_HEAP_DUMP_END record is also written.
  end_of_dump();

  // Now we clear the global variables, so that a future dumper might run.
  clear_global_dumper();
  clear_global_writer();
}

void VM_HeapDumper::dump_stack_traces() {
  // write a HPROF_TRACE record without any frames to be referenced as object alloc sites
  DumperSupport::write_header(writer(), HPROF_TRACE, 3*sizeof(u4));
  writer()->write_u4((u4) STACK_TRACE_ID);
  writer()->write_u4(0);                    // thread number
  writer()->write_u4(0);                    // frame count

  _stack_traces = NEW_C_HEAP_ARRAY(ThreadStackTrace*, Threads::number_of_threads(), mtInternal);
  int frame_serial_num = 0;
  for (JavaThread* thread = Threads::first(); thread != NULL ; thread = thread->next()) {
    oop threadObj = thread->threadObj();
    if (threadObj != NULL && !thread->is_exiting() && !thread->is_hidden_from_external_view()) {
      // dump thread stack trace
      ThreadStackTrace* stack_trace = new ThreadStackTrace(thread, false);
      stack_trace->dump_stack_at_safepoint(-1);
      _stack_traces[_num_threads++] = stack_trace;

      // write HPROF_FRAME records for this thread's stack trace
      int depth = stack_trace->get_stack_depth();
      int thread_frame_start = frame_serial_num;
      int extra_frames = 0;
      // write fake frame that makes it look like the thread, which caused OOME,
      // is in the OutOfMemoryError zero-parameter constructor
      if (thread == _oome_thread && _oome_constructor != NULL) {
        int oome_serial_num = _klass_map->find(_oome_constructor->method_holder());
        // the class serial number starts from 1
        assert(oome_serial_num > 0, "OutOfMemoryError class not found");
        DumperSupport::dump_stack_frame(writer(), ++frame_serial_num, oome_serial_num,
                                        _oome_constructor, 0);
        extra_frames++;
      }
      for (int j=0; j < depth; j++) {
        StackFrameInfo* frame = stack_trace->stack_frame_at(j);
        Method* m = frame->method();
        int class_serial_num = _klass_map->find(m->method_holder());
        // the class serial number starts from 1
        assert(class_serial_num > 0, "class not found");
        DumperSupport::dump_stack_frame(writer(), ++frame_serial_num, class_serial_num, m, frame->bci());
      }
      depth += extra_frames;

      // write HPROF_TRACE record for one thread
      DumperSupport::write_header(writer(), HPROF_TRACE, 3*sizeof(u4) + depth*oopSize);
      int stack_serial_num = _num_threads + STACK_TRACE_ID;
      writer()->write_u4(stack_serial_num);      // stack trace serial number
      writer()->write_u4((u4) _num_threads);     // thread serial number
      writer()->write_u4(depth);                 // frame count
      for (int j=1; j <= depth; j++) {
        writer()->write_id(thread_frame_start + j);
      }
    }
  }
}

// dump the heap to given path.
PRAGMA_FORMAT_NONLITERAL_IGNORED_EXTERNAL
int HeapDumper::dump(const char* path) {
  assert(path != NULL && strlen(path) > 0, "path missing");

  // print message in interactive case
  if (print_to_tty()) {
    tty->print_cr("Dumping heap to %s ...", path);
    timer()->start();
  }

  // create the dump writer. If the file can be opened then bail
  DumpWriter writer(path);
  if (!writer.is_open()) {
    set_error(writer.error());
    if (print_to_tty()) {
      tty->print_cr("Unable to create %s: %s", path,
        (error() != NULL) ? error() : "reason unknown");
    }
    return -1;
  }

  // generate the dump
  VM_HeapDumper dumper(&writer, _gc_before_heap_dump, _oome);
  if (Thread::current()->is_VM_thread()) {
    assert(SafepointSynchronize::is_at_safepoint(), "Expected to be called at a safepoint");
    dumper.doit();
  } else {
    VMThread::execute(&dumper);
  }

  // close dump file and record any error that the writer may have encountered
  writer.close();
  set_error(writer.error());

  // print message in interactive case
  if (print_to_tty()) {
    timer()->stop();
    if (error() == NULL) {
      char msg[256];
      sprintf(msg, "Heap dump file created [%s bytes in %3.3f secs]",
        JLONG_FORMAT, timer()->seconds());
PRAGMA_DIAG_PUSH
PRAGMA_FORMAT_NONLITERAL_IGNORED_INTERNAL
      tty->print_cr(msg, writer.bytes_written());
PRAGMA_DIAG_POP
    } else {
      tty->print_cr("Dump file is incomplete: %s", writer.error());
    }
  }

  return (writer.error() == NULL) ? 0 : -1;
}

// stop timer (if still active), and free any error string we might be holding
HeapDumper::~HeapDumper() {
  if (timer()->is_active()) {
    timer()->stop();
  }
  set_error(NULL);
}


// returns the error string (resource allocated), or NULL
char* HeapDumper::error_as_C_string() const {
  if (error() != NULL) {
    char* str = NEW_RESOURCE_ARRAY(char, strlen(error())+1);
    strcpy(str, error());
    return str;
  } else {
    return NULL;
  }
}

// set the error string
void HeapDumper::set_error(char* error) {
  if (_error != NULL) {
    os::free(_error);
  }
  if (error == NULL) {
    _error = NULL;
  } else {
    _error = os::strdup(error);
    assert(_error != NULL, "allocation failure");
  }
}

// Called by out-of-memory error reporting by a single Java thread
// outside of a JVM safepoint
void HeapDumper::dump_heap_from_oome() {
  HeapDumper::dump_heap(true);
}

// Called by error reporting by a single Java thread outside of a JVM safepoint,
// or by heap dumping by the VM thread during a (GC) safepoint. Thus, these various
// callers are strictly serialized and guaranteed not to interfere below. For more
// general use, however, this method will need modification to prevent
// inteference when updating the static variables base_path and dump_file_seq below.
void HeapDumper::dump_heap() {
  HeapDumper::dump_heap(false);
}

void HeapDumper::dump_heap(bool oome) {
  static char base_path[JVM_MAXPATHLEN] = {'\0'};
  static uint dump_file_seq = 0;
  char* my_path;
  const int max_digit_chars = 20;

  const char* dump_file_name = "java_pid";
  const char* dump_file_ext  = ".hprof";

  // The dump file defaults to java_pid<pid>.hprof in the current working
  // directory. HeapDumpPath=<file> can be used to specify an alternative
  // dump file name or a directory where dump file is created.
  if (dump_file_seq == 0) { // first time in, we initialize base_path
    // Calculate potentially longest base path and check if we have enough
    // allocated statically.
    const size_t total_length =
                      (HeapDumpPath == NULL ? 0 : strlen(HeapDumpPath)) +
                      strlen(os::file_separator()) + max_digit_chars +
                      strlen(dump_file_name) + strlen(dump_file_ext) + 1;
    if (total_length > sizeof(base_path)) {
      warning("Cannot create heap dump file.  HeapDumpPath is too long.");
      return;
    }

    bool use_default_filename = true;
    if (HeapDumpPath == NULL || HeapDumpPath[0] == '\0') {
      // HeapDumpPath=<file> not specified
    } else {
      strncpy(base_path, HeapDumpPath, sizeof(base_path));
      // check if the path is a directory (must exist)
      DIR* dir = os::opendir(base_path);
      if (dir == NULL) {
        use_default_filename = false;
      } else {
        // HeapDumpPath specified a directory. We append a file separator
        // (if needed).
        os::closedir(dir);
        size_t fs_len = strlen(os::file_separator());
        if (strlen(base_path) >= fs_len) {
          char* end = base_path;
          end += (strlen(base_path) - fs_len);
          if (strcmp(end, os::file_separator()) != 0) {
            strcat(base_path, os::file_separator());
          }
        }
      }
    }
    // If HeapDumpPath wasn't a file name then we append the default name
    if (use_default_filename) {
      const size_t dlen = strlen(base_path);  // if heap dump dir specified
      jio_snprintf(&base_path[dlen], sizeof(base_path)-dlen, "%s%d%s",
                   dump_file_name, os::current_process_id(), dump_file_ext);
    }
    const size_t len = strlen(base_path) + 1;
    my_path = (char*)os::malloc(len, mtInternal);
    if (my_path == NULL) {
      warning("Cannot create heap dump file.  Out of system memory.");
      return;
    }
    strncpy(my_path, base_path, len);
  } else {
    // Append a sequence number id for dumps following the first
    const size_t len = strlen(base_path) + max_digit_chars + 2; // for '.' and \0
    my_path = (char*)os::malloc(len, mtInternal);
    if (my_path == NULL) {
      warning("Cannot create heap dump file.  Out of system memory.");
      return;
    }
    jio_snprintf(my_path, len, "%s.%d", base_path, dump_file_seq);
  }
  dump_file_seq++;   // increment seq number for next time we dump

  HeapDumper dumper(false /* no GC before heap dump */,
                    true  /* send to tty */,
                    oome  /* pass along out-of-memory-error flag */);
  dumper.dump(my_path);
  os::free(my_path);
}
