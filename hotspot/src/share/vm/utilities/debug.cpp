/*
 * Copyright (c) 1997, 2010, Oracle and/or its affiliates. All rights reserved.
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
#include "runtime/vframe.hpp"
#include "services/heapDumper.hpp"
#include "utilities/defaultStream.hpp"
#include "utilities/events.hpp"
#include "utilities/top.hpp"
#include "utilities/vmError.hpp"
#ifdef TARGET_OS_FAMILY_linux
# include "os_linux.inline.hpp"
# include "thread_linux.inline.hpp"
#endif
#ifdef TARGET_OS_FAMILY_solaris
# include "os_solaris.inline.hpp"
# include "thread_solaris.inline.hpp"
#endif
#ifdef TARGET_OS_FAMILY_windows
# include "os_windows.inline.hpp"
# include "thread_windows.inline.hpp"
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


void warning(const char* format, ...) {
  if (PrintWarnings) {
    // In case error happens before init or during shutdown
    if (tty == NULL) ostream_init();

    tty->print("%s warning: ", VM_Version::vm_name());
    va_list ap;
    va_start(ap, format);
    tty->vprint_cr(format, ap);
    va_end(ap);
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

#endif //PRODUCT

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

// Used by report_vm_out_of_memory to detect recursion.
static jint _exiting_out_of_mem = 0;

void report_vm_out_of_memory(const char* file, int line, size_t size,
                             const char* message) {
  if (Debugging || error_is_suppressed(file, line)) return;

  // We try to gather additional information for the first out of memory
  // error only; gathering additional data might cause an allocation and a
  // recursive out_of_memory condition.

  const jint exiting = 1;
  // If we succeed in changing the value, we're the first one in.
  bool first_time_here = Atomic::xchg(exiting, &_exiting_out_of_mem) != exiting;

  if (first_time_here) {
    Thread* thread = ThreadLocalStorage::get_thread_slow();
    VMError(thread, file, line, size, message).report_and_die();
  }

  // Dump core and abort
  vm_abort(true);
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
#endif // PRODUCT
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


extern "C" void ps();

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

void test_error_handler(size_t test_num)
{
  if (test_num == 0) return;

  // If asserts are disabled, use the corresponding guarantee instead.
  size_t n = test_num;
  NOT_DEBUG(if (n <= 2) n += 2);

  const char* const str = "hello";
  const size_t      num = (size_t)os::vm_page_size();

  const char* const eol = os::line_separator();
  const char* const msg = "this message should be truncated during formatting";

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
    case  8: vm_exit_out_of_memory(num, "ChunkPool::allocate");
    case  9: ShouldNotCallThis();
    case 10: ShouldNotReachHere();
    case 11: Unimplemented();
    // This is last because it does not generate an hs_err* file on Windows.
    case 12: os::signal_raise(SIGSEGV);

    default: ShouldNotReachHere();
  }
}
#endif // #ifndef PRODUCT

// ------ helper functions for debugging go here ------------

#ifndef PRODUCT
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

  ~Command() { tty->flush(); Debugging = debug_save; level--; }
};

int Command::level = 0;

extern "C" void blob(CodeBlob* cb) {
  Command c("blob");
  cb->print();
}


extern "C" void dump_vtable(address p) {
  Command c("dump_vtable");
  klassOop k = (klassOop)p;
  instanceKlass::cast(k)->vtable()->print();
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
  cb->print();
  Disassembler::decode(cb);
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
  Universe::verify(true);
  if (!safe) SafepointSynchronize::set_is_not_at_safepoint();
}


extern "C" void pp(void* p) {
  Command c("pp");
  FlagSetting fl(PrintVMMessages, true);
  if (Universe::heap()->is_in(p)) {
    oop obj = oop(p);
    obj->print();
  } else {
    tty->print("%#p", p);
  }
}


// pv: print vm-printable object
extern "C" void pa(intptr_t p)   { ((AllocatedObj*) p)->print(); }
extern "C" void findpc(intptr_t x);

extern "C" void ps() { // print stack
  Command c("ps");


  // Prints the stack of the current Java thread
  JavaThread* p = JavaThread::active();
  tty->print(" for thread: ");
  p->print();
  tty->cr();

  if (p->has_last_Java_frame()) {
    // If the last_Java_fp is set we are in C land and
    // can call the standard stack_trace function.
    p->trace_stack();
  } else {
    frame f = os::current_frame();
    RegisterMap reg_map(p);
    f = f.sender(&reg_map);
    tty->print("(guessing starting frame id=%#p based on current fp)\n", f.id());
    p->trace_stack_from(vframe::new_vframe(&f, &reg_map, p));
  pd_ps(f);
  }

}


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


extern "C" void pss() { // print all stacks
  Command c("pss");
  Threads::print(true, true);
}


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
  Events::print_last(tty, 50);
}


extern "C" void nevents(int n) {
  Command c("events");
  Events::print_last(tty, n);
}


// Given a heap address that was valid before the most recent GC, if
// the oop that used to contain it is still live, prints the new
// location of the oop and the address. Useful for tracking down
// certain kinds of naked oop and oop map bugs.
extern "C" void pnl(intptr_t old_heap_addr) {
  // Print New Location of old heap address
  Command c("pnl");
#ifndef VALIDATE_MARK_SWEEP
  tty->print_cr("Requires build with VALIDATE_MARK_SWEEP defined (debug build) and RecordMarkSweepCompaction enabled");
#else
  MarkSweep::print_new_location_of_heap_address((HeapWord*) old_heap_addr);
#endif
}


extern "C" methodOop findm(intptr_t pc) {
  Command c("findm");
  nmethod* nm = CodeCache::find_nmethod((address)pc);
  return (nm == NULL) ? (methodOop)NULL : nm->method();
}


extern "C" nmethod* findnm(intptr_t addr) {
  Command c("findnm");
  return  CodeCache::find_nmethod((address)addr);
}

static address same_page(address x, address y) {
  intptr_t page_bits = -os::vm_page_size();
  if ((intptr_t(x) & page_bits) == (intptr_t(y) & page_bits)) {
    return x;
  } else if (x > y) {
    return (address)(intptr_t(y) | ~page_bits) + 1;
  } else {
    return (address)(intptr_t(y) & page_bits);
  }
}

class LookForRefInGenClosure : public OopsInGenClosure {
public:
  oop target;
  void do_oop(oop* o) {
    if (o != NULL && *o == target) {
      tty->print_cr(INTPTR_FORMAT, o);
    }
  }
  void do_oop(narrowOop* o) { ShouldNotReachHere(); }
};


class LookForRefInObjectClosure : public ObjectClosure {
private:
  LookForRefInGenClosure look_in_object;
public:
  LookForRefInObjectClosure(oop target) { look_in_object.target = target; }
  void do_object(oop obj) {
    obj->oop_iterate(&look_in_object);
  }
};


static void findref(intptr_t x) {
  CollectedHeap *ch = Universe::heap();
  LookForRefInGenClosure lookFor;
  lookFor.target = (oop) x;
  LookForRefInObjectClosure look_in_object((oop) x);

  tty->print_cr("Searching heap:");
  ch->object_iterate(&look_in_object);

  tty->print_cr("Searching strong roots:");
  Universe::oops_do(&lookFor, false);
  JNIHandles::oops_do(&lookFor);   // Global (strong) JNI handles
  Threads::oops_do(&lookFor, NULL);
  ObjectSynchronizer::oops_do(&lookFor);
  //FlatProfiler::oops_do(&lookFor);
  SystemDictionary::oops_do(&lookFor);

  tty->print_cr("Searching code cache:");
  CodeCache::oops_do(&lookFor);

  tty->print_cr("Done.");
}

class FindClassObjectClosure: public ObjectClosure {
  private:
    const char* _target;
  public:
    FindClassObjectClosure(const char name[])  { _target = name; }

    virtual void do_object(oop obj) {
      if (obj->is_klass()) {
        Klass* k = klassOop(obj)->klass_part();
        if (k->name() != NULL) {
          ResourceMark rm;
          const char* ext = k->external_name();
          if ( strcmp(_target, ext) == 0 ) {
            tty->print_cr("Found " INTPTR_FORMAT, obj);
            obj->print();
          }
        }
      }
    }
};

//
extern "C" void findclass(const char name[]) {
  Command c("findclass");
  if (name != NULL) {
    tty->print_cr("Finding class %s -> ", name);
    FindClassObjectClosure srch(name);
    Universe::heap()->permanent_object_iterate(&srch);
  }
}

// Another interface that isn't ambiguous in dbx.
// Can we someday rename the other find to hsfind?
extern "C" void hsfind(intptr_t x) {
  Command c("hsfind");
  os::print_location(tty, x, false);
}


extern "C" void hsfindref(intptr_t x) {
  Command c("hsfindref");
  findref(x);
}

extern "C" void find(intptr_t x) {
  Command c("find");
  os::print_location(tty, x, false);
}


extern "C" void findpc(intptr_t x) {
  Command c("findpc");
  os::print_location(tty, x, true);
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
  tty->print_cr("  pm(int pc)    - print methodOop given compiled PC");
  tty->print_cr("  findm(intptr_t pc) - finds methodOop");
  tty->print_cr("  find(intptr_t x)   - finds & prints nmethod/stub/bytecode/oop based on pointer into it");

  tty->print_cr("misc.");
  tty->print_cr("  flush()       - flushes the log file");
  tty->print_cr("  events()      - dump last 50 events");


  tty->print_cr("compiler debugging");
  tty->print_cr("  debug()       - to set things up for compiler debugging");
  tty->print_cr("  ndebug()      - undo debug");
}

#if 0

// BobV's command parser for debugging on windows when nothing else works.

enum CommandID {
  CMDID_HELP,
  CMDID_QUIT,
  CMDID_HSFIND,
  CMDID_PSS,
  CMDID_PS,
  CMDID_PSF,
  CMDID_FINDM,
  CMDID_FINDNM,
  CMDID_PP,
  CMDID_BPT,
  CMDID_EXIT,
  CMDID_VERIFY,
  CMDID_THREADS,
  CMDID_ILLEGAL = 99
};

struct CommandParser {
   char *name;
   CommandID code;
   char *description;
};

struct CommandParser CommandList[] = {
  (char *)"help", CMDID_HELP, "  Dump this list",
  (char *)"quit", CMDID_QUIT, "  Return from this routine",
  (char *)"hsfind", CMDID_HSFIND, "Perform an hsfind on an address",
  (char *)"ps", CMDID_PS, "    Print Current Thread Stack Trace",
  (char *)"pss", CMDID_PSS, "   Print All Thread Stack Trace",
  (char *)"psf", CMDID_PSF, "   Print All Stack Frames",
  (char *)"findm", CMDID_FINDM, " Find a methodOop from a PC",
  (char *)"findnm", CMDID_FINDNM, "Find an nmethod from a PC",
  (char *)"pp", CMDID_PP, "    Find out something about a pointer",
  (char *)"break", CMDID_BPT, " Execute a breakpoint",
  (char *)"exitvm", CMDID_EXIT, "Exit the VM",
  (char *)"verify", CMDID_VERIFY, "Perform a Heap Verify",
  (char *)"thread", CMDID_THREADS, "Dump Info on all Threads",
  (char *)0, CMDID_ILLEGAL
};


// get_debug_command()
//
// Read a command from standard input.
// This is useful when you have a debugger
// which doesn't support calling into functions.
//
void get_debug_command()
{
  ssize_t count;
  int i,j;
  bool gotcommand;
  intptr_t addr;
  char buffer[256];
  nmethod *nm;
  methodOop m;

  tty->print_cr("You have entered the diagnostic command interpreter");
  tty->print("The supported commands are:\n");
  for ( i=0; ; i++ ) {
    if ( CommandList[i].code == CMDID_ILLEGAL )
      break;
    tty->print_cr("  %s \n", CommandList[i].name );
  }

  while ( 1 ) {
    gotcommand = false;
    tty->print("Please enter a command: ");
    count = scanf("%s", buffer) ;
    if ( count >=0 ) {
      for ( i=0; ; i++ ) {
        if ( CommandList[i].code == CMDID_ILLEGAL ) {
          if (!gotcommand) tty->print("Invalid command, please try again\n");
          break;
        }
        if ( strcmp(buffer, CommandList[i].name) == 0 ) {
          gotcommand = true;
          switch ( CommandList[i].code ) {
              case CMDID_PS:
                ps();
                break;
              case CMDID_PSS:
                pss();
                break;
              case CMDID_PSF:
                psf();
                break;
              case CMDID_FINDM:
                tty->print("Please enter the hex addr to pass to findm: ");
                scanf("%I64X", &addr);
                m = (methodOop)findm(addr);
                tty->print("findm(0x%I64X) returned 0x%I64X\n", addr, m);
                break;
              case CMDID_FINDNM:
                tty->print("Please enter the hex addr to pass to findnm: ");
                scanf("%I64X", &addr);
                nm = (nmethod*)findnm(addr);
                tty->print("findnm(0x%I64X) returned 0x%I64X\n", addr, nm);
                break;
              case CMDID_PP:
                tty->print("Please enter the hex addr to pass to pp: ");
                scanf("%I64X", &addr);
                pp((void*)addr);
                break;
              case CMDID_EXIT:
                exit(0);
              case CMDID_HELP:
                tty->print("Here are the supported commands: ");
                for ( j=0; ; j++ ) {
                  if ( CommandList[j].code == CMDID_ILLEGAL )
                    break;
                  tty->print_cr("  %s --  %s\n", CommandList[j].name,
                                                 CommandList[j].description );
                }
                break;
              case CMDID_QUIT:
                return;
                break;
              case CMDID_BPT:
                BREAKPOINT;
                break;
              case CMDID_VERIFY:
                verify();;
                break;
              case CMDID_THREADS:
                threads();;
                break;
              case CMDID_HSFIND:
                tty->print("Please enter the hex addr to pass to hsfind: ");
                scanf("%I64X", &addr);
                tty->print("Calling hsfind(0x%I64X)\n", addr);
                hsfind(addr);
                break;
              default:
              case CMDID_ILLEGAL:
                break;
          }
        }
      }
    }
  }
}
#endif

#endif // PRODUCT
