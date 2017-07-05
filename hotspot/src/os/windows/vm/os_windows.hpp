/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
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

#ifndef OS_WINDOWS_VM_OS_WINDOWS_HPP
#define OS_WINDOWS_VM_OS_WINDOWS_HPP
// Win32_OS defines the interface to windows operating systems

class win32 {
  friend class os;

 protected:
  static int    _vm_page_size;
  static int    _vm_allocation_granularity;
  static int    _processor_type;
  static int    _processor_level;
  static julong _physical_memory;
  static size_t _default_stack_size;
  static bool   _is_nt;
  static bool   _is_windows_2003;
  static bool   _is_windows_server;

  static void print_windows_version(outputStream* st);

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

  // load dll from Windows system directory or Windows directory
  static HINSTANCE load_Windows_dll(const char* name, char *ebuf, int ebuflen);

 public:
  // Generic interface:

  // Trace number of created threads
  static          intx  _os_thread_limit;
  static volatile intx  _os_thread_count;

  // Tells whether the platform is NT or Windown95
  static bool is_nt() { return _is_nt; }

  // Tells whether this is a server version of Windows
  static bool is_windows_server() { return _is_windows_server; }

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

#ifndef PRODUCT
  static void call_test_func_with_wrapper(void (*funcPtr)(void));
#endif

  // filter function to ignore faults on serializations page
  static LONG WINAPI serialize_fault_filter(struct _EXCEPTION_POINTERS* e);
};

/*
 * Crash protection for the watcher thread. Wrap the callback
 * with a __try { call() }
 * To be able to use this - don't take locks, don't rely on destructors,
 * don't make OS library calls, don't allocate memory, don't print,
 * don't call code that could leave the heap / memory in an inconsistent state,
 * or anything else where we are not in control if we suddenly jump out.
 */
class WatcherThreadCrashProtection : public StackObj {
public:
  WatcherThreadCrashProtection();
  bool call(os::CrashProtectionCallback& cb);
};

class PlatformEvent : public CHeapObj<mtInternal> {
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



class PlatformParker : public CHeapObj<mtInternal> {
  protected:
    HANDLE _ParkEvent ;

  public:
    ~PlatformParker () { guarantee (0, "invariant") ; }
    PlatformParker  () {
      _ParkEvent = CreateEvent (NULL, true, false, NULL) ;
      guarantee (_ParkEvent != NULL, "invariant") ;
    }

} ;

// JDK7 requires VS2010
#if _MSC_VER < 1600
#define JDK6_OR_EARLIER 1
#endif



class WinSock2Dll: AllStatic {
public:
  static BOOL WSAStartup(WORD, LPWSADATA);
  static struct hostent* gethostbyname(const char *name);
  static BOOL WinSock2Available();
#ifdef JDK6_OR_EARLIER
private:
  static int (PASCAL FAR* _WSAStartup)(WORD, LPWSADATA);
  static struct hostent *(PASCAL FAR *_gethostbyname)(...);
  static BOOL initialized;

  static void initialize();
#endif
};

class Kernel32Dll: AllStatic {
public:
  static BOOL SwitchToThread();
  static SIZE_T GetLargePageMinimum();

  static BOOL SwitchToThreadAvailable();
  static BOOL GetLargePageMinimumAvailable();

  // Help tools
  static BOOL HelpToolsAvailable();
  static HANDLE CreateToolhelp32Snapshot(DWORD,DWORD);
  static BOOL Module32First(HANDLE,LPMODULEENTRY32);
  static BOOL Module32Next(HANDLE,LPMODULEENTRY32);

  static BOOL GetNativeSystemInfoAvailable();
  static void GetNativeSystemInfo(LPSYSTEM_INFO);

  // NUMA calls
  static BOOL NumaCallsAvailable();
  static LPVOID VirtualAllocExNuma(HANDLE, LPVOID, SIZE_T, DWORD, DWORD, DWORD);
  static BOOL GetNumaHighestNodeNumber(PULONG);
  static BOOL GetNumaNodeProcessorMask(UCHAR, PULONGLONG);

  // Stack walking
  static USHORT RtlCaptureStackBackTrace(ULONG, ULONG, PVOID*, PULONG);

private:
  // GetLargePageMinimum available on Windows Vista/Windows Server 2003
  // and later
  // NUMA calls available Windows Vista/WS2008 and later

  static SIZE_T (WINAPI *_GetLargePageMinimum)(void);
  static LPVOID (WINAPI *_VirtualAllocExNuma) (HANDLE, LPVOID, SIZE_T, DWORD, DWORD, DWORD);
  static BOOL (WINAPI *_GetNumaHighestNodeNumber) (PULONG);
  static BOOL (WINAPI *_GetNumaNodeProcessorMask) (UCHAR, PULONGLONG);
  static USHORT (WINAPI *_RtlCaptureStackBackTrace)(ULONG, ULONG, PVOID*, PULONG);
  static BOOL initialized;

  static void initialize();
  static void initializeCommon();

#ifdef JDK6_OR_EARLIER
private:
  static BOOL (WINAPI *_SwitchToThread)(void);
  static HANDLE (WINAPI* _CreateToolhelp32Snapshot)(DWORD,DWORD);
  static BOOL (WINAPI* _Module32First)(HANDLE,LPMODULEENTRY32);
  static BOOL (WINAPI* _Module32Next)(HANDLE,LPMODULEENTRY32);
  static void (WINAPI *_GetNativeSystemInfo)(LPSYSTEM_INFO);
#endif

};

class Advapi32Dll: AllStatic {
public:
  static BOOL AdjustTokenPrivileges(HANDLE, BOOL, PTOKEN_PRIVILEGES, DWORD, PTOKEN_PRIVILEGES, PDWORD);
  static BOOL OpenProcessToken(HANDLE, DWORD, PHANDLE);
  static BOOL LookupPrivilegeValue(LPCTSTR, LPCTSTR, PLUID);

  static BOOL AdvapiAvailable();

#ifdef JDK6_OR_EARLIER
private:
  static BOOL (WINAPI *_AdjustTokenPrivileges)(HANDLE, BOOL, PTOKEN_PRIVILEGES, DWORD, PTOKEN_PRIVILEGES, PDWORD);
  static BOOL (WINAPI *_OpenProcessToken)(HANDLE, DWORD, PHANDLE);
  static BOOL (WINAPI *_LookupPrivilegeValue)(LPCTSTR, LPCTSTR, PLUID);
  static BOOL initialized;

  static void initialize();
#endif
};

class PSApiDll: AllStatic {
public:
  static BOOL EnumProcessModules(HANDLE, HMODULE *, DWORD, LPDWORD);
  static DWORD GetModuleFileNameEx(HANDLE, HMODULE, LPTSTR, DWORD);
  static BOOL GetModuleInformation(HANDLE, HMODULE, LPMODULEINFO, DWORD);

  static BOOL PSApiAvailable();

#ifdef JDK6_OR_EARLIER
private:
  static BOOL (WINAPI *_EnumProcessModules)(HANDLE, HMODULE *, DWORD, LPDWORD);
  static BOOL (WINAPI *_GetModuleFileNameEx)(HANDLE, HMODULE, LPTSTR, DWORD);;
  static BOOL (WINAPI *_GetModuleInformation)(HANDLE, HMODULE, LPMODULEINFO, DWORD);
  static BOOL initialized;

  static void initialize();
#endif
};

#endif // OS_WINDOWS_VM_OS_WINDOWS_HPP
