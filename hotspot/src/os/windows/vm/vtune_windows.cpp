/*
 * Copyright (c) 1998, 2007, Oracle and/or its affiliates. All rights reserved.
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

#include "incls/_precompiled.incl"
#include "incls/_vtune_windows.cpp.incl"

static int current_method_ID = 0;

// ------------- iJITProf.h -------------------
// defined by Intel -- do not change

#include "windows.h"

extern "C" {
  enum iJITP_Event {
    ExceptionOccurred_S,                  // Java exception
    ExceptionOccurred_IDS,

    Shutdown,                             // VM exit

    ThreadCreate,                         // threads
    ThreadDestroy,
    ThreadSwitch,

    ClassLoadStart,                       // class loading
    ClassLoadEnd,

    GCStart,                              // GC
    GCEnd,

    NMethodCreate = 13,                   // nmethod creation
    NMethodDelete

    // rest of event types omitted (call profiling not supported yet)
  };

  // version number -- 0 if VTune not installed
  int WINAPI iJitP_VersionNumber();

  enum iJITP_ModeFlags {
    NoNotification = 0x0,                // don't call vtune
    NotifyNMethodCreate   = 0x1,         // notify NMethod_Create
    NotifyNMethodDelete   = 0x2,         // notify NMethod_Create
    NotifyMethodEnter     = 0x4,         // method entry
    NotifyMethodExit      = 0x8,         // method exit
    NotifyShutdown        = 0x10,        // VM exit
    NotifyGC              = 0x20,        // GC
  };

  // call back function type
  typedef void (WINAPI *ModeChangedFn)(iJITP_ModeFlags flags);

  // -------------  VTune method interfaces ----------------------
  typedef void  (WINAPI *RegisterCallbackFn)(ModeChangedFn fn);   // register callback
  typedef int   (WINAPI *NotifyEventFn)(iJITP_Event, void* event_data);

  // specific event data structures

  // data for NMethodCreate

  struct VTuneObj {                       // base class for allocation
                                          // (can't use CHeapObj -- has vtable ptr)
    void* operator new(size_t size) { return os::malloc(size); }
    void  operator delete(void* p)  { fatal("never delete VTune data"); }
  };

  struct LineNumberInfo : VTuneObj {      // PC-to-line number mapping
    unsigned long offset;                 // byte offset from start of method
    unsigned long line_num;               // corresponding line number
  };

  struct MethodLoadInfo : VTuneObj {
    unsigned long methodID;               // unique method ID
    const char* name;                     // method name
    unsigned long instr_start;            // start address
    unsigned long instr_size;             // length in bytes
    unsigned long line_number_size;       // size of line number table
    LineNumberInfo* line_number_table;    // line number mapping
    unsigned long classID;                // unique class ID
    char* class_file_name;                // fully qualified class file name
    char* source_file_name;               // fully qualified source file name

    MethodLoadInfo(nmethod* nm);          // for real nmethods
    MethodLoadInfo(const char* vm_name, address start, address end);
                                          // for "nmethods" like stubs, interpreter, etc

  };

  // data for NMethodDelete
  struct MethodInfo : VTuneObj {
    unsigned long methodID;               // unique method ID
    unsigned long classID;                // (added for convenience -- not part of Intel interface)

    MethodInfo(methodOop m);
  };
};

MethodInfo::MethodInfo(methodOop m) {
  // just give it a new ID -- we're not compiling methods twice (usually)
  // (and even if we did, one might want to see the two versions separately)
  methodID = ++current_method_ID;
}

MethodLoadInfo::MethodLoadInfo(const char* vm_name, address start, address end) {
  classID  = 0;
  methodID = ++current_method_ID;
  name = vm_name;
  instr_start = (unsigned long)start;
  instr_size = end - start;
  line_number_size = 0;
  line_number_table = NULL;
  class_file_name = source_file_name = "HotSpot JVM";
}

MethodLoadInfo::MethodLoadInfo(nmethod* nm) {
  methodOop m = nm->method();
  MethodInfo info(m);
  classID  = info.classID;
  methodID = info.methodID;
  name = strdup(m->name()->as_C_string());
  instr_start = (unsigned long)nm->instructions_begin();
  instr_size = nm->code_size();
  line_number_size = 0;
  line_number_table = NULL;
  klassOop kl = m->method_holder();
  char* class_name = Klass::cast(kl)->name()->as_C_string();
  char* file_name = NEW_C_HEAP_ARRAY(char, strlen(class_name) + 1);
  strcpy(file_name, class_name);
  class_file_name = file_name;
  char* src_name = NEW_C_HEAP_ARRAY(char, strlen(class_name) + strlen(".java") + 1);
  strcpy(src_name, class_name);
  strcat(src_name, ".java");
  source_file_name = src_name;
}

// --------------------- DLL loading functions ------------------------

#define DLLNAME "iJitProf.dll"

static HINSTANCE load_lib(char* name) {
  HINSTANCE lib = NULL;
  HKEY hk;

  // try to get VTune directory from the registry
  if (RegOpenKey(HKEY_CURRENT_USER, "Software\\VB and VBA Program Settings\\VTune\\StartUp", &hk) == ERROR_SUCCESS) {
    for (int i = 0; true; i++) {
      char szName[MAX_PATH + 1];
      char szVal [MAX_PATH + 1];
      DWORD cbName, cbVal;

      cbName = cbVal = MAX_PATH + 1;
      if (RegEnumValue(hk, i, szName, &cbName, NULL, NULL, (LPBYTE)szVal, &cbVal) == ERROR_SUCCESS) {
        // get VTune directory
        if (!strcmp(szName, name)) {
          char*p = szVal;
          while (*p == ' ') p++;    // trim
          char* q = p + strlen(p) - 1;
          while (*q == ' ') *(q--) = '\0';

          // chdir to the VTune dir
          GetCurrentDirectory(MAX_PATH + 1, szName);
          SetCurrentDirectory(p);
          // load lib
          lib = LoadLibrary(strcat(strcat(p, "\\"), DLLNAME));
          if (lib != NULL && WizardMode) tty->print_cr("*loaded VTune DLL %s", p);
          // restore current dir
          SetCurrentDirectory(szName);
          break;
        }
      } else {
        break;
      }
    }
  }
  return lib;
}

static RegisterCallbackFn iJIT_RegisterCallback = NULL;
static NotifyEventFn      iJIT_NotifyEvent      = NULL;

static bool load_iJIT_funcs() {
  // first try to load from PATH
  HINSTANCE lib = LoadLibrary(DLLNAME);
  if (lib != NULL && WizardMode) tty->print_cr("*loaded VTune DLL %s via PATH", DLLNAME);

  // if not successful, try to look in the VTUNE directory
  if (lib == NULL) lib = load_lib("VTUNEDIR30");
  if (lib == NULL) lib = load_lib("VTUNEDIR25");
  if (lib == NULL) lib = load_lib("VTUNEDIR");

  if (lib == NULL) return false;    // unsuccessful

  // try to load the functions
  iJIT_RegisterCallback = (RegisterCallbackFn)GetProcAddress(lib, "iJIT_RegisterCallback");
  iJIT_NotifyEvent      = (NotifyEventFn)     GetProcAddress(lib, "iJIT_NotifyEvent");

  if (!iJIT_RegisterCallback) tty->print_cr("*couldn't find VTune entry point iJIT_RegisterCallback");
  if (!iJIT_NotifyEvent)      tty->print_cr("*couldn't find VTune entry point iJIT_NotifyEvent");
  return iJIT_RegisterCallback != NULL && iJIT_NotifyEvent != NULL;
}

// --------------------- VTune class ------------------------

static bool active = false;
static int  flags  = 0;

void VTune::start_GC() {
  if (active && (flags & NotifyGC)) iJIT_NotifyEvent(GCStart, NULL);
}

void VTune::end_GC() {
  if (active && (flags & NotifyGC)) iJIT_NotifyEvent(GCEnd, NULL);
}

void VTune::start_class_load() {
  // not yet implemented in VTune
}

void VTune::end_class_load() {
  // not yet implemented in VTune
}

void VTune::exit() {
  if (active && (flags & NotifyShutdown)) iJIT_NotifyEvent(Shutdown, NULL);
}

void VTune::register_stub(const char* name, address start, address end) {
  if (flags & NotifyNMethodCreate) {
    MethodLoadInfo* info = new MethodLoadInfo(name, start, end);
    if (PrintMiscellaneous && WizardMode && Verbose) {
      tty->print_cr("NMethodCreate %s (%d): %#x..%#x", info->name, info->methodID,
                    info->instr_start, info->instr_start + info->instr_size);
    }
    iJIT_NotifyEvent(NMethodCreate, info);
  }
}

void VTune::create_nmethod(nmethod* nm) {
  if (flags & NotifyNMethodCreate) {
    MethodLoadInfo* info = new MethodLoadInfo(nm);
    if (PrintMiscellaneous && WizardMode && Verbose) {
      tty->print_cr("NMethodCreate %s (%d): %#x..%#x", info->name, info->methodID,
                    info->instr_start, info->instr_start + info->instr_size);
    }
    iJIT_NotifyEvent(NMethodCreate, info);
  }
}

void VTune::delete_nmethod(nmethod* nm) {
  if (flags & NotifyNMethodDelete) {
    MethodInfo* info = new MethodInfo(nm->method());
    iJIT_NotifyEvent(NMethodDelete, info);
  }
}

static void set_flags(int new_flags) {
  flags = new_flags;
  // if (WizardMode) tty->print_cr("*new VTune flags: %#x", flags);
}

void vtune_init() {
  if (!UseVTune) return;
  active = load_iJIT_funcs();
  if (active) {
    iJIT_RegisterCallback((ModeChangedFn)set_flags);
  } else {
    assert(flags == 0, "flags shouldn't be set");
  }
}
