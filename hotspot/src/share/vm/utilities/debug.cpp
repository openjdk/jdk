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

#include "precompiled.hpp"
#include "classfile/systemDictionary.hpp"
#include "code/codeCache.hpp"
#include "code/icBuffer.hpp"
#include "code/nmethod.hpp"
#include "code/vtableStubs.hpp"
#include "compiler/compileBroker.hpp"
#include "compiler/disassembler.hpp"
#include "gc_implementation/shared/markSweep.hpp"
#include "gc_interface/collectedHeap.hpp"
#include "interpreter/bytecodeHistogram.hpp"
#include "interpreter/interpreter.hpp"
#include "memory/resourceArea.hpp"
#include "memory/universe.hpp"
#include "oops/oop.inline.hpp"
#include "prims/privilegedStack.hpp"
#include "runtime/arguments.hpp"
#include "runtime/frame.hpp"
#include "runtime/java.hpp"
#include "runtime/sharedRuntime.hpp"
#include "runtime/stubCodeGenerator.hpp"
#include "runtime/stubRoutines.hpp"
#include "runtime/thread.inline.hpp"
#include "runtime/vframe.hpp"
#include "services/heapDumper.hpp"
#include "utilities/defaultStream.hpp"
#include "utilities/events.hpp"
#include "utilities/top.hpp"
#include "utilities/vmError.hpp"
#ifdef TARGET_OS_FAMILY_linux
# include "os_linux.inline.hpp"
#endif
#ifdef TARGET_OS_FAMILY_solaris
# include "os_solaris.inline.hpp"
#endif
#ifdef TARGET_OS_FAMILY_windows
# include "os_windows.inline.hpp"
#endif
#ifdef TARGET_OS_FAMILY_bsd
# include "os_bsd.inline.hpp"
#endif

#ifndef ASSERT
#  ifdef _DEBUG
   // NOTE: don't turn the lines below into a comment -- if you're getting
   // a compile error here, change the settings to define ASSERT
   ASSERT should be defined when _DEBUG is defined.  It is not intended to be used for debugging
   functions that do not slow down the system too much and thus can be left in optimized code.
   On the other hand, the code should not be included in a production version.
#  endif // _DEBUG
#endif // ASSERT


#ifdef _DEBUG
#  ifndef ASSERT
     configuration error: ASSERT must be defined in debug version
#  endif // ASSERT
#endif // _DEBUG


#ifdef PRODUCT
#  if -defined _DEBUG || -defined ASSERT
     configuration error: ASSERT et al. must not be defined in PRODUCT version
#  endif
#endif // PRODUCT

FormatBufferResource::FormatBufferResource(const char * format, ...)
  : FormatBufferBase((char*)resource_allocate_bytes(RES_BUFSZ)) {
  va_list argp;
  va_start(argp, format);
  jio_vsnprintf(_buf, RES_BUFSZ, format, argp);
  va_end(argp);
}

void warning(const char* format, ...) {
  if (PrintWarnings) {
    FILE* const err = defaultStream::error_stream();
    jio_fprintf(err, "%s warning: ", VM_Version::vm_name());
    va_list ap;
    va_start(ap, format);
    vfprintf(err, format, ap);
    va_end(ap);
    fputc('\n', err);
  }
  if (BreakAtWarning) BREAKPOINT;
}

#ifndef PRODUCT

#define is_token_break(ch) (isspace(ch) || (ch) == ',')

static const char* last_file_name = NULL;
static int         last_line_no   = -1;

// assert/guarantee/... may happen very early during VM initialization.
// Don't rely on anything that is initialized by Threads::create_vm(). For
// example, don't use tty.
bool error_is_suppressed(const char* file_name, int line_no) {
  // The following 1-element cache requires that passed-in
  // file names are always only constant literals.
  if (file_name == last_file_name && line_no == last_line_no)  return true;

  int file_name_len = (int)strlen(file_name);
  char separator = os::file_separator()[0];
  const char* base_name = strrchr(file_name, separator);
  if (base_name == NULL)
    base_name = file_name;

  // scan the SuppressErrorAt option
  const char* cp = SuppressErrorAt;
  for (;;) {
    const char* sfile;
    int sfile_len;
    int sline;
    bool noisy;
    while ((*cp) != '\0' && is_token_break(*cp))  cp++;
    if ((*cp) == '\0')  break;
    sfile = cp;
    while ((*cp) != '\0' && !is_token_break(*cp) && (*cp) != ':')  cp++;
    sfile_len = cp - sfile;
    if ((*cp) == ':')  cp++;
    sline = 0;
    while ((*cp) != '\0' && isdigit(*cp)) {
      sline *= 10;
      sline += (*cp) - '0';
      cp++;
    }
    // "file:line!" means the assert suppression is not silent
    noisy = ((*cp) == '!');
    while ((*cp) != '\0' && !is_token_break(*cp))  cp++;
    // match the line
    if (sline != 0) {
      if (sline != line_no)  continue;
    }
    // match the file
    if (sfile_len > 0) {
      const char* look = file_name;
      const char* look_max = file_name + file_name_len - sfile_len;
      const char* foundp;
      bool match = false;
      while (!match
             && (foundp = strchr(look, sfile[0])) != NULL
             && foundp <= look_max) {
        match = true;
        for (int i = 1; i < sfile_len; i++) {
          if (sfile[i] != foundp[i]) {
            match = false;
            break;
          }
        }
        look = foundp + 1;
      }
      if (!match)  continue;
    }
    // got a match!
    if (noisy) {
      fdStream out(defaultStream::output_fd());
      out.print_raw("[error suppressed at ");
      out.print_raw(base_name);
      char buf[16];
      jio_snprintf(buf, sizeof(buf), ":%d]", line_no);
      out.print_raw_cr(buf);
    } else {
      // update 1-element cache for fast silent matches
      last_file_name = file_name;
      last_line_no   = line_no;
    }
    return true;
  }

  if (!is_error_reported()) {
    // print a friendly hint:
    fdStream out(defaultStream::output_fd());
    out.print_raw_cr("# To suppress the following error report, specify this argument");
    out.print_raw   ("# after -XX: or in .hotspotrc:  SuppressErrorAt=");
    out.print_raw   (base_name);
    char buf[16];
    jio_snprintf(buf, sizeof(buf), ":%d", line_no);
    out.print_raw_cr(buf);
  }
  return false;
}

#undef is_token_break

#else

// Place-holder for non-existent suppression check:
#define error_is_suppressed(file_name, line_no) (false)

#endif // !PRODUCT

void report_vm_error(const char* file, int line, const char* error_msg,
                     const char* detail_msg)
{
  if (Debugging || error_is_suppressed(file, line)) return;
  Thread* const thread = ThreadLocalStorage::get_thread_slow();
  VMError err(thread, file, line, error_msg, detail_msg);
  err.report_and_die();
}

void report_fatal(const char* file, int line, const char* message)
{
  report_vm_error(file, line, "fatal error", message);
}

void report_vm_out_of_memory(const char* file, int line, size_t size,
                             VMErrorType vm_err_type, const char* message) {
  if (Debugging) return;

  Thread* thread = ThreadLocalStorage::get_thread_slow();
  VMError(thread, file, line, size, vm_err_type, message).report_and_die();

  // The UseOSErrorReporting option in report_and_die() may allow a return
  // to here. If so then we'll have to figure out how to handle it.
  guarantee(false, "report_and_die() should not return here");
}

void report_should_not_call(const char* file, int line) {
  report_vm_error(file, line, "ShouldNotCall()");
}

void report_should_not_reach_here(const char* file, int line) {
  report_vm_error(file, line, "ShouldNotReachHere()");
}

void report_unimplemented(const char* file, int line) {
  report_vm_error(file, line, "Unimplemented()");
}

void report_untested(const char* file, int line, const char* message) {
#ifndef PRODUCT
  warning("Untested: %s in %s: %d\n", message, file, line);
#endif // !PRODUCT
}

void report_out_of_shared_space(SharedSpaceType shared_space) {
  static const char* name[] = {
    "native memory for metadata",
    "shared read only space",
    "shared read write space",
    "shared miscellaneous data space"
  };
  static const char* flag[] = {
    "Metaspace",
    "SharedReadOnlySize",
    "SharedReadWriteSize",
    "SharedMiscDataSize"
  };

   warning("\nThe %s is not large enough\n"
           "to preload requested classes. Use -XX:%s=\n"
           "to increase the initial size of %s.\n",
           name[shared_space], flag[shared_space], name[shared_space]);
   exit(2);
}

void report_java_out_of_memory(const char* message) {
  static jint out_of_memory_reported = 0;

  // A number of threads may attempt to report OutOfMemoryError at around the
  // same time. To avoid dumping the heap or executing the data collection
  // commands multiple times we just do it once when the first threads reports
  // the error.
  if (Atomic::cmpxchg(1, &out_of_memory_reported, 0) == 0) {
    // create heap dump before OnOutOfMemoryError commands are executed
    if (HeapDumpOnOutOfMemoryError) {
      tty->print_cr("java.lang.OutOfMemoryError: %s", message);
      HeapDumper::dump_heap_from_oome();
    }

    if (OnOutOfMemoryError && OnOutOfMemoryError[0]) {
      VMError err(message);
      err.report_java_out_of_memory();
    }
  }
}

static bool error_reported = false;

// call this when the VM is dying--it might loosen some asserts
void set_error_reported() {
  error_reported = true;
}

bool is_error_reported() {
    return error_reported;
}

#ifndef PRODUCT
#include <signal.h>

void test_error_handler() {
  uintx test_num = ErrorHandlerTest;
  if (test_num == 0) return;

  // If asserts are disabled, use the corresponding guarantee instead.
  size_t n = test_num;
  NOT_DEBUG(if (n <= 2) n += 2);

  const char* const str = "hello";
  const size_t      num = (size_t)os::vm_page_size();

  const char* const eol = os::line_separator();
  const char* const msg = "this message should be truncated during formatting";
  char * const dataPtr = NULL;  // bad data pointer
  const void (*funcPtr)(void) = (const void(*)()) 0xF;  // bad function pointer

  // Keep this in sync with test/runtime/6888954/vmerrors.sh.
  switch (n) {
    case  1: assert(str == NULL, "expected null");
    case  2: assert(num == 1023 && *str == 'X',
                    err_msg("num=" SIZE_FORMAT " str=\"%s\"", num, str));
    case  3: guarantee(str == NULL, "expected null");
    case  4: guarantee(num == 1023 && *str == 'X',
                       err_msg("num=" SIZE_FORMAT " str=\"%s\"", num, str));
    case  5: fatal("expected null");
    case  6: fatal(err_msg("num=" SIZE_FORMAT " str=\"%s\"", num, str));
    case  7: fatal(err_msg("%s%s#    %s%s#    %s%s#    %s%s#    %s%s#    "
                           "%s%s#    %s%s#    %s%s#    %s%s#    %s%s#    "
                           "%s%s#    %s%s#    %s%s#    %s%s#    %s",
                           msg, eol, msg, eol, msg, eol, msg, eol, msg, eol,
                           msg, eol, msg, eol, msg, eol, msg, eol, msg, eol,
                           msg, eol, msg, eol, msg, eol, msg, eol, msg));
    case  8: vm_exit_out_of_memory(num, OOM_MALLOC_ERROR, "ChunkPool::allocate");
    case  9: ShouldNotCallThis();
    case 10: ShouldNotReachHere();
    case 11: Unimplemented();
    // There's no guarantee the bad data pointer will crash us
    // so "break" out to the ShouldNotReachHere().
    case 12: *dataPtr = '\0'; break;
    // There's no guarantee the bad function pointer will crash us
    // so "break" out to the ShouldNotReachHere().
    case 13: (*funcPtr)(); break;

    default: tty->print_cr("ERROR: %d: unexpected test_num value.", n);
  }
  ShouldNotReachHere();
}
#endif // !PRODUCT

// ------ helper functions for debugging go here ------------

// All debug entries should be wrapped with a stack allocated
// Command object. It makes sure a resource mark is set and
// flushes the logfile to prevent file sharing problems.

class Command : public StackObj {
 private:
  ResourceMark rm;
  ResetNoHandleMark rnhm;
  HandleMark   hm;
  bool debug_save;
 public:
  static int level;
  Command(const char* str) {
    debug_save = Debugging;
    Debugging = true;
    if (level++ > 0)  return;
    tty->cr();
    tty->print_cr("\"Executing %s\"", str);
  }

  ~Command() {
        tty->flush();
        Debugging = debug_save;
        level--;
  }
};

int Command::level = 0;

#ifndef PRODUCT

extern "C" void blob(CodeBlob* cb) {
  Command c("blob");
  cb->print();
}


extern "C" void dump_vtable(address p) {
  Command c("dump_vtable");
  Klass* k = (Klass*)p;
  InstanceKlass::cast(k)->vtable()->print();
}


extern "C" void nm(intptr_t p) {
  // Actually we look through all CodeBlobs (the nm name has been kept for backwards compatability)
  Command c("nm");
  CodeBlob* cb = CodeCache::find_blob((address)p);
  if (cb == NULL) {
    tty->print_cr("NULL");
  } else {
    cb->print();
  }
}


extern "C" void disnm(intptr_t p) {
  Command c("disnm");
  CodeBlob* cb = CodeCache::find_blob((address) p);
  nmethod* nm = cb->as_nmethod_or_null();
  if (nm) {
    nm->print();
    Disassembler::decode(nm);
  } else {
    cb->print();
    Disassembler::decode(cb);
  }
}


extern "C" void printnm(intptr_t p) {
  char buffer[256];
  sprintf(buffer, "printnm: " INTPTR_FORMAT, p);
  Command c(buffer);
  CodeBlob* cb = CodeCache::find_blob((address) p);
  if (cb->is_nmethod()) {
    nmethod* nm = (nmethod*)cb;
    nm->print_nmethod(true);
  }
}


extern "C" void universe() {
  Command c("universe");
  Universe::print();
}


extern "C" void verify() {
  // try to run a verify on the entire system
  // note: this may not be safe if we're not at a safepoint; for debugging,
  // this manipulates the safepoint settings to avoid assertion failures
  Command c("universe verify");
  bool safe = SafepointSynchronize::is_at_safepoint();
  if (!safe) {
    tty->print_cr("warning: not at safepoint -- verify may fail");
    SafepointSynchronize::set_is_at_safepoint();
  }
  // Ensure Eden top is correct before verification
  Universe::heap()->prepare_for_verify();
  Universe::verify();
  if (!safe) SafepointSynchronize::set_is_not_at_safepoint();
}


extern "C" void pp(void* p) {
  Command c("pp");
  FlagSetting fl(PrintVMMessages, true);
  FlagSetting f2(DisplayVMOutput, true);
  if (Universe::heap()->is_in(p)) {
    oop obj = oop(p);
    obj->print();
  } else {
    tty->print(PTR_FORMAT, p);
  }
}


// pv: print vm-printable object
extern "C" void pa(intptr_t p)   { ((AllocatedObj*) p)->print(); }
extern "C" void findpc(intptr_t x);

#endif // !PRODUCT

extern "C" void ps() { // print stack
  if (Thread::current() == NULL) return;
  Command c("ps");


  // Prints the stack of the current Java thread
  JavaThread* p = JavaThread::active();
  tty->print(" for thread: ");
  p->print();
  tty->cr();

  if (p->has_last_Java_frame()) {
    // If the last_Java_fp is set we are in C land and
    // can call the standard stack_trace function.
#ifdef PRODUCT
    p->print_stack();
  } else {
    tty->print_cr("Cannot find the last Java frame, printing stack disabled.");
#else // !PRODUCT
    p->trace_stack();
  } else {
    frame f = os::current_frame();
    RegisterMap reg_map(p);
    f = f.sender(&reg_map);
    tty->print("(guessing starting frame id=%#p based on current fp)\n", f.id());
    p->trace_stack_from(vframe::new_vframe(&f, &reg_map, p));
  pd_ps(f);
#endif // PRODUCT
  }

}

extern "C" void pfl() {
  // print frame layout
  Command c("pfl");
  JavaThread* p = JavaThread::active();
  tty->print(" for thread: ");
  p->print();
  tty->cr();
  if (p->has_last_Java_frame()) {
    p->print_frame_layout();
  }
}

#ifndef PRODUCT

extern "C" void psf() { // print stack frames
  {
    Command c("psf");
    JavaThread* p = JavaThread::active();
    tty->print(" for thread: ");
    p->print();
    tty->cr();
    if (p->has_last_Java_frame()) {
      p->trace_frames();
    }
  }
}


extern "C" void threads() {
  Command c("threads");
  Threads::print(false, true);
}


extern "C" void psd() {
  Command c("psd");
  SystemDictionary::print();
}


extern "C" void safepoints() {
  Command c("safepoints");
  SafepointSynchronize::print_state();
}

#endif // !PRODUCT

extern "C" void pss() { // print all stacks
  if (Thread::current() == NULL) return;
  Command c("pss");
  Threads::print(true, PRODUCT_ONLY(false) NOT_PRODUCT(true));
}

#ifndef PRODUCT

extern "C" void debug() {               // to set things up for compiler debugging
  Command c("debug");
  WizardMode = true;
  PrintVMMessages = PrintCompilation = true;
  PrintInlining = PrintAssembly = true;
  tty->flush();
}


extern "C" void ndebug() {              // undo debug()
  Command c("ndebug");
  PrintCompilation = false;
  PrintInlining = PrintAssembly = false;
  tty->flush();
}


extern "C" void flush()  {
  Command c("flush");
  tty->flush();
}

extern "C" void events() {
  Command c("events");
  Events::print();
}

extern "C" Method* findm(intptr_t pc) {
  Command c("findm");
  nmethod* nm = CodeCache::find_nmethod((address)pc);
  return (nm == NULL) ? (Method*)NULL : nm->method();
}


extern "C" nmethod* findnm(intptr_t addr) {
  Command c("findnm");
  return  CodeCache::find_nmethod((address)addr);
}

// Another interface that isn't ambiguous in dbx.
// Can we someday rename the other find to hsfind?
extern "C" void hsfind(intptr_t x) {
  Command c("hsfind");
  os::print_location(tty, x, false);
}


extern "C" void find(intptr_t x) {
  Command c("find");
  os::print_location(tty, x, false);
}


extern "C" void findpc(intptr_t x) {
  Command c("findpc");
  os::print_location(tty, x, true);
}


// Need method pointer to find bcp, when not in permgen.
extern "C" void findbcp(intptr_t method, intptr_t bcp) {
  Command c("findbcp");
  Method* mh = (Method*)method;
  if (!mh->is_native()) {
    tty->print_cr("bci_from(%p) = %d; print_codes():",
                        mh, mh->bci_from(address(bcp)));
    mh->print_codes_on(tty);
  }
}

// int versions of all methods to avoid having to type type casts in the debugger

void pp(intptr_t p)          { pp((void*)p); }
void pp(oop p)               { pp((void*)p); }

void help() {
  Command c("help");
  tty->print_cr("basic");
  tty->print_cr("  pp(void* p)   - try to make sense of p");
  tty->print_cr("  pv(intptr_t p)- ((PrintableResourceObj*) p)->print()");
  tty->print_cr("  ps()          - print current thread stack");
  tty->print_cr("  pss()         - print all thread stacks");
  tty->print_cr("  pm(int pc)    - print Method* given compiled PC");
  tty->print_cr("  findm(intptr_t pc) - finds Method*");
  tty->print_cr("  find(intptr_t x)   - finds & prints nmethod/stub/bytecode/oop based on pointer into it");

  tty->print_cr("misc.");
  tty->print_cr("  flush()       - flushes the log file");
  tty->print_cr("  events()      - dump events from ring buffers");


  tty->print_cr("compiler debugging");
  tty->print_cr("  debug()       - to set things up for compiler debugging");
  tty->print_cr("  ndebug()      - undo debug");
}

#endif // !PRODUCT
