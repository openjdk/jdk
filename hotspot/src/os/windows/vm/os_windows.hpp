/*
 * Copyright (c) 1997, 2008, Oracle and/or its affiliates. All rights reserved.
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

// Win32_OS defines the interface to windows operating systems

class win32 {

 protected:
  static int    _vm_page_size;
  static int    _vm_allocation_granularity;
  static int    _processor_type;
  static int    _processor_level;
  static julong _physical_memory;
  static size_t _default_stack_size;
  static bool   _is_nt;
  static bool   _is_windows_2003;

 public:
  // Windows-specific interface:
  static void   initialize_system_info();
  static void   setmode_streams();

  // Processor info as provided by NT
  static int processor_type()  { return _processor_type;  }
  // Processor level may not be accurate on non-NT systems
  static int processor_level() {
    assert(is_nt(), "use vm_version instead");
    return _processor_level;
  }
  static julong available_memory();
  static julong physical_memory() { return _physical_memory; }

 public:
  // Generic interface:

  // Trace number of created threads
  static          intx  _os_thread_limit;
  static volatile intx  _os_thread_count;

  // Tells whether the platform is NT or Windown95
  static bool is_nt() { return _is_nt; }

  // Tells whether the platform is Windows 2003
  static bool is_windows_2003() { return _is_windows_2003; }

  // Returns the byte size of a virtual memory page
  static int vm_page_size() { return _vm_page_size; }

  // Returns the size in bytes of memory blocks which can be allocated.
  static int vm_allocation_granularity() { return _vm_allocation_granularity; }

  // Read the headers for the executable that started the current process into
  // the structure passed in (see winnt.h).
  static void read_executable_headers(PIMAGE_NT_HEADERS);

  // Default stack size for the current process.
  static size_t default_stack_size() { return _default_stack_size; }

#ifndef _WIN64
  // A wrapper to install a structured exception handler for fast JNI accesors.
  static address fast_jni_accessor_wrapper(BasicType);
#endif

  // filter function to ignore faults on serializations page
  static LONG WINAPI serialize_fault_filter(struct _EXCEPTION_POINTERS* e);
};

class PlatformEvent : public CHeapObj {
  private:
    double CachePad [4] ;   // increase odds that _Event is sole occupant of cache line
    volatile int _Event ;
    HANDLE _ParkHandle ;

  public:       // TODO-FIXME: make dtor private
    ~PlatformEvent() { guarantee (0, "invariant") ; }

  public:
    PlatformEvent() {
      _Event   = 0 ;
      _ParkHandle = CreateEvent (NULL, false, false, NULL) ;
      guarantee (_ParkHandle != NULL, "invariant") ;
    }

    // Exercise caution using reset() and fired() - they may require MEMBARs
    void reset() { _Event = 0 ; }
    int  fired() { return _Event; }
    void park () ;
    void unpark () ;
    int  park (jlong millis) ;
} ;



class PlatformParker : public CHeapObj {
  protected:
    HANDLE _ParkEvent ;

  public:
    ~PlatformParker () { guarantee (0, "invariant") ; }
    PlatformParker  () {
      _ParkEvent = CreateEvent (NULL, true, false, NULL) ;
      guarantee (_ParkEvent != NULL, "invariant") ;
    }

} ;
