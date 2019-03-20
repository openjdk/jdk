/*
 * Copyright (c) 1999, 2019, Oracle and/or its affiliates. All rights reserved.
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

#include "jvm.h"
#include "logging/log.hpp"
#include "memory/allocation.inline.hpp"
#include "os_posix.inline.hpp"
#include "utilities/globalDefinitions.hpp"
#include "runtime/frame.inline.hpp"
#include "runtime/interfaceSupport.inline.hpp"
#include "services/memTracker.hpp"
#include "utilities/align.hpp"
#include "utilities/events.hpp"
#include "utilities/formatBuffer.hpp"
#include "utilities/macros.hpp"
#include "utilities/vmError.hpp"

#include <dirent.h>
#include <dlfcn.h>
#include <grp.h>
#include <pwd.h>
#include <pthread.h>
#include <signal.h>
#include <sys/mman.h>
#include <sys/resource.h>
#include <sys/utsname.h>
#include <time.h>
#include <unistd.h>

// Todo: provide a os::get_max_process_id() or similar. Number of processes
// may have been configured, can be read more accurately from proc fs etc.
#ifndef MAX_PID
#define MAX_PID INT_MAX
#endif
#define IS_VALID_PID(p) (p > 0 && p < MAX_PID)

#define ROOT_UID 0

#ifndef MAP_ANONYMOUS
  #define MAP_ANONYMOUS MAP_ANON
#endif

#define check_with_errno(check_type, cond, msg)                             \
  do {                                                                      \
    int err = errno;                                                        \
    check_type(cond, "%s; error='%s' (errno=%s)", msg, os::strerror(err),   \
               os::errno_name(err));                                        \
} while (false)

#define assert_with_errno(cond, msg)    check_with_errno(assert, cond, msg)
#define guarantee_with_errno(cond, msg) check_with_errno(guarantee, cond, msg)

// Check core dump limit and report possible place where core can be found
void os::check_dump_limit(char* buffer, size_t bufferSize) {
  if (!FLAG_IS_DEFAULT(CreateCoredumpOnCrash) && !CreateCoredumpOnCrash) {
    jio_snprintf(buffer, bufferSize, "CreateCoredumpOnCrash is disabled from command line");
    VMError::record_coredump_status(buffer, false);
    return;
  }

  int n;
  struct rlimit rlim;
  bool success;

  char core_path[PATH_MAX];
  n = get_core_path(core_path, PATH_MAX);

  if (n <= 0) {
    jio_snprintf(buffer, bufferSize, "core.%d (may not exist)", current_process_id());
    success = true;
#ifdef LINUX
  } else if (core_path[0] == '"') { // redirect to user process
    jio_snprintf(buffer, bufferSize, "Core dumps may be processed with %s", core_path);
    success = true;
#endif
  } else if (getrlimit(RLIMIT_CORE, &rlim) != 0) {
    jio_snprintf(buffer, bufferSize, "%s (may not exist)", core_path);
    success = true;
  } else {
    switch(rlim.rlim_cur) {
      case RLIM_INFINITY:
        jio_snprintf(buffer, bufferSize, "%s", core_path);
        success = true;
        break;
      case 0:
        jio_snprintf(buffer, bufferSize, "Core dumps have been disabled. To enable core dumping, try \"ulimit -c unlimited\" before starting Java again");
        success = false;
        break;
      default:
        jio_snprintf(buffer, bufferSize, "%s (max size " UINT64_FORMAT " kB). To ensure a full core dump, try \"ulimit -c unlimited\" before starting Java again", core_path, uint64_t(rlim.rlim_cur) / 1024);
        success = true;
        break;
    }
  }

  VMError::record_coredump_status(buffer, success);
}

int os::get_native_stack(address* stack, int frames, int toSkip) {
  int frame_idx = 0;
  int num_of_frames;  // number of frames captured
  frame fr = os::current_frame();
  while (fr.pc() && frame_idx < frames) {
    if (toSkip > 0) {
      toSkip --;
    } else {
      stack[frame_idx ++] = fr.pc();
    }
    if (fr.fp() == NULL || fr.cb() != NULL ||
        fr.sender_pc() == NULL || os::is_first_C_frame(&fr)) break;

    if (fr.sender_pc() && !os::is_first_C_frame(&fr)) {
      fr = os::get_sender_for_C_frame(&fr);
    } else {
      break;
    }
  }
  num_of_frames = frame_idx;
  for (; frame_idx < frames; frame_idx ++) {
    stack[frame_idx] = NULL;
  }

  return num_of_frames;
}


bool os::unsetenv(const char* name) {
  assert(name != NULL, "Null pointer");
  return (::unsetenv(name) == 0);
}

int os::get_last_error() {
  return errno;
}

size_t os::lasterror(char *buf, size_t len) {
  if (errno == 0)  return 0;

  const char *s = os::strerror(errno);
  size_t n = ::strlen(s);
  if (n >= len) {
    n = len - 1;
  }
  ::strncpy(buf, s, n);
  buf[n] = '\0';
  return n;
}

bool os::is_debugger_attached() {
  // not implemented
  return false;
}

void os::wait_for_keypress_at_exit(void) {
  // don't do anything on posix platforms
  return;
}

int os::create_file_for_heap(const char* dir) {

  const char name_template[] = "/jvmheap.XXXXXX";

  size_t fullname_len = strlen(dir) + strlen(name_template);
  char *fullname = (char*)os::malloc(fullname_len + 1, mtInternal);
  if (fullname == NULL) {
    vm_exit_during_initialization(err_msg("Malloc failed during creation of backing file for heap (%s)", os::strerror(errno)));
    return -1;
  }
  int n = snprintf(fullname, fullname_len + 1, "%s%s", dir, name_template);
  assert((size_t)n == fullname_len, "Unexpected number of characters in string");

  os::native_path(fullname);

  // set the file creation mask.
  mode_t file_mode = S_IRUSR | S_IWUSR;

  // create a new file.
  int fd = mkstemp(fullname);

  if (fd < 0) {
    warning("Could not create file for heap with template %s", fullname);
    os::free(fullname);
    return -1;
  }

  // delete the name from the filesystem. When 'fd' is closed, the file (and space) will be deleted.
  int ret = unlink(fullname);
  assert_with_errno(ret == 0, "unlink returned error");

  os::free(fullname);
  return fd;
}

static char* reserve_mmapped_memory(size_t bytes, char* requested_addr) {
  char * addr;
  int flags = MAP_PRIVATE NOT_AIX( | MAP_NORESERVE ) | MAP_ANONYMOUS;
  if (requested_addr != NULL) {
    assert((uintptr_t)requested_addr % os::vm_page_size() == 0, "Requested address should be aligned to OS page size");
    flags |= MAP_FIXED;
  }

  // Map reserved/uncommitted pages PROT_NONE so we fail early if we
  // touch an uncommitted page. Otherwise, the read/write might
  // succeed if we have enough swap space to back the physical page.
  addr = (char*)::mmap(requested_addr, bytes, PROT_NONE,
                       flags, -1, 0);

  if (addr != MAP_FAILED) {
    MemTracker::record_virtual_memory_reserve((address)addr, bytes, CALLER_PC);
    return addr;
  }
  return NULL;
}

static int util_posix_fallocate(int fd, off_t offset, off_t len) {
#ifdef __APPLE__
  fstore_t store = { F_ALLOCATECONTIG, F_PEOFPOSMODE, 0, len };
  // First we try to get a continuous chunk of disk space
  int ret = fcntl(fd, F_PREALLOCATE, &store);
  if (ret == -1) {
    // Maybe we are too fragmented, try to allocate non-continuous range
    store.fst_flags = F_ALLOCATEALL;
    ret = fcntl(fd, F_PREALLOCATE, &store);
  }
  if(ret != -1) {
    return ftruncate(fd, len);
  }
  return -1;
#else
  return posix_fallocate(fd, offset, len);
#endif
}

// Map the given address range to the provided file descriptor.
char* os::map_memory_to_file(char* base, size_t size, int fd) {
  assert(fd != -1, "File descriptor is not valid");

  // allocate space for the file
  int ret = util_posix_fallocate(fd, 0, (off_t)size);
  if (ret != 0) {
    vm_exit_during_initialization(err_msg("Error in mapping Java heap at the given filesystem directory. error(%d)", ret));
    return NULL;
  }

  int prot = PROT_READ | PROT_WRITE;
  int flags = MAP_SHARED;
  if (base != NULL) {
    flags |= MAP_FIXED;
  }
  char* addr = (char*)mmap(base, size, prot, flags, fd, 0);

  if (addr == MAP_FAILED) {
    warning("Failed mmap to file. (%s)", os::strerror(errno));
    return NULL;
  }
  if (base != NULL && addr != base) {
    if (!os::release_memory(addr, size)) {
      warning("Could not release memory on unsuccessful file mapping");
    }
    return NULL;
  }
  return addr;
}

char* os::replace_existing_mapping_with_file_mapping(char* base, size_t size, int fd) {
  assert(fd != -1, "File descriptor is not valid");
  assert(base != NULL, "Base cannot be NULL");

  return map_memory_to_file(base, size, fd);
}

// Multiple threads can race in this code, and can remap over each other with MAP_FIXED,
// so on posix, unmap the section at the start and at the end of the chunk that we mapped
// rather than unmapping and remapping the whole chunk to get requested alignment.
char* os::reserve_memory_aligned(size_t size, size_t alignment, int file_desc) {
  assert((alignment & (os::vm_allocation_granularity() - 1)) == 0,
      "Alignment must be a multiple of allocation granularity (page size)");
  assert((size & (alignment -1)) == 0, "size must be 'alignment' aligned");

  size_t extra_size = size + alignment;
  assert(extra_size >= size, "overflow, size is too large to allow alignment");

  char* extra_base;
  if (file_desc != -1) {
    // For file mapping, we do not call os:reserve_memory(extra_size, NULL, alignment, file_desc) because
    // we need to deal with shrinking of the file space later when we release extra memory after alignment.
    // We also cannot called os:reserve_memory() with file_desc set to -1 because on aix we might get SHM memory.
    // So here to call a helper function while reserve memory for us. After we have a aligned base,
    // we will replace anonymous mapping with file mapping.
    extra_base = reserve_mmapped_memory(extra_size, NULL);
    if (extra_base != NULL) {
      MemTracker::record_virtual_memory_reserve((address)extra_base, extra_size, CALLER_PC);
    }
  } else {
    extra_base = os::reserve_memory(extra_size, NULL, alignment);
  }

  if (extra_base == NULL) {
    return NULL;
  }

  // Do manual alignment
  char* aligned_base = align_up(extra_base, alignment);

  // [  |                                       |  ]
  // ^ extra_base
  //    ^ extra_base + begin_offset == aligned_base
  //     extra_base + begin_offset + size       ^
  //                       extra_base + extra_size ^
  // |<>| == begin_offset
  //                              end_offset == |<>|
  size_t begin_offset = aligned_base - extra_base;
  size_t end_offset = (extra_base + extra_size) - (aligned_base + size);

  if (begin_offset > 0) {
      os::release_memory(extra_base, begin_offset);
  }

  if (end_offset > 0) {
      os::release_memory(extra_base + begin_offset + size, end_offset);
  }

  if (file_desc != -1) {
    // After we have an aligned address, we can replace anonymous mapping with file mapping
    if (replace_existing_mapping_with_file_mapping(aligned_base, size, file_desc) == NULL) {
      vm_exit_during_initialization(err_msg("Error in mapping Java heap at the given filesystem directory"));
    }
    MemTracker::record_virtual_memory_commit((address)aligned_base, size, CALLER_PC);
  }
  return aligned_base;
}

int os::vsnprintf(char* buf, size_t len, const char* fmt, va_list args) {
  // All supported POSIX platforms provide C99 semantics.
  int result = ::vsnprintf(buf, len, fmt, args);
  // If an encoding error occurred (result < 0) then it's not clear
  // whether the buffer is NUL terminated, so ensure it is.
  if ((result < 0) && (len > 0)) {
    buf[len - 1] = '\0';
  }
  return result;
}

int os::get_fileno(FILE* fp) {
  return NOT_AIX(::)fileno(fp);
}

struct tm* os::gmtime_pd(const time_t* clock, struct tm*  res) {
  return gmtime_r(clock, res);
}

void os::Posix::print_load_average(outputStream* st) {
  st->print("load average:");
  double loadavg[3];
  os::loadavg(loadavg, 3);
  st->print("%0.02f %0.02f %0.02f", loadavg[0], loadavg[1], loadavg[2]);
  st->cr();
}

void os::Posix::print_rlimit_info(outputStream* st) {
  st->print("rlimit:");
  struct rlimit rlim;

  st->print(" STACK ");
  getrlimit(RLIMIT_STACK, &rlim);
  if (rlim.rlim_cur == RLIM_INFINITY) st->print("infinity");
  else st->print(UINT64_FORMAT "k", uint64_t(rlim.rlim_cur) / 1024);

  st->print(", CORE ");
  getrlimit(RLIMIT_CORE, &rlim);
  if (rlim.rlim_cur == RLIM_INFINITY) st->print("infinity");
  else st->print(UINT64_FORMAT "k", uint64_t(rlim.rlim_cur) / 1024);

  // Isn't there on solaris
#if defined(AIX)
  st->print(", NPROC ");
  st->print("%d", sysconf(_SC_CHILD_MAX));
#elif !defined(SOLARIS)
  st->print(", NPROC ");
  getrlimit(RLIMIT_NPROC, &rlim);
  if (rlim.rlim_cur == RLIM_INFINITY) st->print("infinity");
  else st->print(UINT64_FORMAT, uint64_t(rlim.rlim_cur));
#endif

  st->print(", NOFILE ");
  getrlimit(RLIMIT_NOFILE, &rlim);
  if (rlim.rlim_cur == RLIM_INFINITY) st->print("infinity");
  else st->print(UINT64_FORMAT, uint64_t(rlim.rlim_cur));

  st->print(", AS ");
  getrlimit(RLIMIT_AS, &rlim);
  if (rlim.rlim_cur == RLIM_INFINITY) st->print("infinity");
  else st->print(UINT64_FORMAT "k", uint64_t(rlim.rlim_cur) / 1024);

  st->print(", DATA ");
  getrlimit(RLIMIT_DATA, &rlim);
  if (rlim.rlim_cur == RLIM_INFINITY) st->print("infinity");
  else st->print(UINT64_FORMAT "k", uint64_t(rlim.rlim_cur) / 1024);

  st->print(", FSIZE ");
  getrlimit(RLIMIT_FSIZE, &rlim);
  if (rlim.rlim_cur == RLIM_INFINITY) st->print("infinity");
  else st->print(UINT64_FORMAT "k", uint64_t(rlim.rlim_cur) / 1024);

  st->cr();
}

void os::Posix::print_uname_info(outputStream* st) {
  // kernel
  st->print("uname:");
  struct utsname name;
  uname(&name);
  st->print("%s ", name.sysname);
#ifdef ASSERT
  st->print("%s ", name.nodename);
#endif
  st->print("%s ", name.release);
  st->print("%s ", name.version);
  st->print("%s", name.machine);
  st->cr();
}

void os::Posix::print_umask(outputStream* st, mode_t umsk) {
  st->print((umsk & S_IRUSR) ? "r" : "-");
  st->print((umsk & S_IWUSR) ? "w" : "-");
  st->print((umsk & S_IXUSR) ? "x" : "-");
  st->print((umsk & S_IRGRP) ? "r" : "-");
  st->print((umsk & S_IWGRP) ? "w" : "-");
  st->print((umsk & S_IXGRP) ? "x" : "-");
  st->print((umsk & S_IROTH) ? "r" : "-");
  st->print((umsk & S_IWOTH) ? "w" : "-");
  st->print((umsk & S_IXOTH) ? "x" : "-");
}

void os::Posix::print_user_info(outputStream* st) {
  unsigned id = (unsigned) ::getuid();
  st->print("uid  : %u ", id);
  id = (unsigned) ::geteuid();
  st->print("euid : %u ", id);
  id = (unsigned) ::getgid();
  st->print("gid  : %u ", id);
  id = (unsigned) ::getegid();
  st->print_cr("egid : %u", id);
  st->cr();

  mode_t umsk = ::umask(0);
  ::umask(umsk);
  st->print("umask: %04o (", (unsigned) umsk);
  print_umask(st, umsk);
  st->print_cr(")");
  st->cr();
}


bool os::get_host_name(char* buf, size_t buflen) {
  struct utsname name;
  uname(&name);
  jio_snprintf(buf, buflen, "%s", name.nodename);
  return true;
}

bool os::has_allocatable_memory_limit(julong* limit) {
  struct rlimit rlim;
  int getrlimit_res = getrlimit(RLIMIT_AS, &rlim);
  // if there was an error when calling getrlimit, assume that there is no limitation
  // on virtual memory.
  bool result;
  if ((getrlimit_res != 0) || (rlim.rlim_cur == RLIM_INFINITY)) {
    result = false;
  } else {
    *limit = (julong)rlim.rlim_cur;
    result = true;
  }
#ifdef _LP64
  return result;
#else
  // arbitrary virtual space limit for 32 bit Unices found by testing. If
  // getrlimit above returned a limit, bound it with this limit. Otherwise
  // directly use it.
  const julong max_virtual_limit = (julong)3800*M;
  if (result) {
    *limit = MIN2(*limit, max_virtual_limit);
  } else {
    *limit = max_virtual_limit;
  }

  // bound by actually allocatable memory. The algorithm uses two bounds, an
  // upper and a lower limit. The upper limit is the current highest amount of
  // memory that could not be allocated, the lower limit is the current highest
  // amount of memory that could be allocated.
  // The algorithm iteratively refines the result by halving the difference
  // between these limits, updating either the upper limit (if that value could
  // not be allocated) or the lower limit (if the that value could be allocated)
  // until the difference between these limits is "small".

  // the minimum amount of memory we care about allocating.
  const julong min_allocation_size = M;

  julong upper_limit = *limit;

  // first check a few trivial cases
  if (is_allocatable(upper_limit) || (upper_limit <= min_allocation_size)) {
    *limit = upper_limit;
  } else if (!is_allocatable(min_allocation_size)) {
    // we found that not even min_allocation_size is allocatable. Return it
    // anyway. There is no point to search for a better value any more.
    *limit = min_allocation_size;
  } else {
    // perform the binary search.
    julong lower_limit = min_allocation_size;
    while ((upper_limit - lower_limit) > min_allocation_size) {
      julong temp_limit = ((upper_limit - lower_limit) / 2) + lower_limit;
      temp_limit = align_down(temp_limit, min_allocation_size);
      if (is_allocatable(temp_limit)) {
        lower_limit = temp_limit;
      } else {
        upper_limit = temp_limit;
      }
    }
    *limit = lower_limit;
  }
  return true;
#endif
}

const char* os::get_current_directory(char *buf, size_t buflen) {
  return getcwd(buf, buflen);
}

FILE* os::open(int fd, const char* mode) {
  return ::fdopen(fd, mode);
}

ssize_t os::read_at(int fd, void *buf, unsigned int nBytes, jlong offset) {
  return ::pread(fd, buf, nBytes, offset);
}

void os::flockfile(FILE* fp) {
  ::flockfile(fp);
}

void os::funlockfile(FILE* fp) {
  ::funlockfile(fp);
}

DIR* os::opendir(const char* dirname) {
  assert(dirname != NULL, "just checking");
  return ::opendir(dirname);
}

struct dirent* os::readdir(DIR* dirp) {
  assert(dirp != NULL, "just checking");
  return ::readdir(dirp);
}

int os::closedir(DIR *dirp) {
  assert(dirp != NULL, "just checking");
  return ::closedir(dirp);
}

// Builds a platform dependent Agent_OnLoad_<lib_name> function name
// which is used to find statically linked in agents.
// Parameters:
//            sym_name: Symbol in library we are looking for
//            lib_name: Name of library to look in, NULL for shared libs.
//            is_absolute_path == true if lib_name is absolute path to agent
//                                     such as "/a/b/libL.so"
//            == false if only the base name of the library is passed in
//               such as "L"
char* os::build_agent_function_name(const char *sym_name, const char *lib_name,
                                    bool is_absolute_path) {
  char *agent_entry_name;
  size_t len;
  size_t name_len;
  size_t prefix_len = strlen(JNI_LIB_PREFIX);
  size_t suffix_len = strlen(JNI_LIB_SUFFIX);
  const char *start;

  if (lib_name != NULL) {
    name_len = strlen(lib_name);
    if (is_absolute_path) {
      // Need to strip path, prefix and suffix
      if ((start = strrchr(lib_name, *os::file_separator())) != NULL) {
        lib_name = ++start;
      }
      if (strlen(lib_name) <= (prefix_len + suffix_len)) {
        return NULL;
      }
      lib_name += prefix_len;
      name_len = strlen(lib_name) - suffix_len;
    }
  }
  len = (lib_name != NULL ? name_len : 0) + strlen(sym_name) + 2;
  agent_entry_name = NEW_C_HEAP_ARRAY_RETURN_NULL(char, len, mtThread);
  if (agent_entry_name == NULL) {
    return NULL;
  }
  strcpy(agent_entry_name, sym_name);
  if (lib_name != NULL) {
    strcat(agent_entry_name, "_");
    strncat(agent_entry_name, lib_name, name_len);
  }
  return agent_entry_name;
}

int os::sleep(Thread* thread, jlong millis, bool interruptible) {
  assert(thread == Thread::current(),  "thread consistency check");

  ParkEvent * const slp = thread->_SleepEvent ;
  slp->reset() ;
  OrderAccess::fence() ;

  if (interruptible) {
    jlong prevtime = javaTimeNanos();

    for (;;) {
      if (os::is_interrupted(thread, true)) {
        return OS_INTRPT;
      }

      jlong newtime = javaTimeNanos();

      if (newtime - prevtime < 0) {
        // time moving backwards, should only happen if no monotonic clock
        // not a guarantee() because JVM should not abort on kernel/glibc bugs
        assert(!os::supports_monotonic_clock(), "unexpected time moving backwards detected in os::sleep(interruptible)");
      } else {
        millis -= (newtime - prevtime) / NANOSECS_PER_MILLISEC;
      }

      if (millis <= 0) {
        return OS_OK;
      }

      prevtime = newtime;

      {
        assert(thread->is_Java_thread(), "sanity check");
        JavaThread *jt = (JavaThread *) thread;
        ThreadBlockInVM tbivm(jt);
        OSThreadWaitState osts(jt->osthread(), false /* not Object.wait() */);

        jt->set_suspend_equivalent();
        // cleared by handle_special_suspend_equivalent_condition() or
        // java_suspend_self() via check_and_wait_while_suspended()

        slp->park(millis);

        // were we externally suspended while we were waiting?
        jt->check_and_wait_while_suspended();
      }
    }
  } else {
    OSThreadWaitState osts(thread->osthread(), false /* not Object.wait() */);
    jlong prevtime = javaTimeNanos();

    for (;;) {
      // It'd be nice to avoid the back-to-back javaTimeNanos() calls on
      // the 1st iteration ...
      jlong newtime = javaTimeNanos();

      if (newtime - prevtime < 0) {
        // time moving backwards, should only happen if no monotonic clock
        // not a guarantee() because JVM should not abort on kernel/glibc bugs
        assert(!os::supports_monotonic_clock(), "unexpected time moving backwards detected on os::sleep(!interruptible)");
      } else {
        millis -= (newtime - prevtime) / NANOSECS_PER_MILLISEC;
      }

      if (millis <= 0) break ;

      prevtime = newtime;
      slp->park(millis);
    }
    return OS_OK ;
  }
}

void os::naked_short_nanosleep(jlong ns) {
  struct timespec req;
  assert(ns > -1 && ns < NANOUNITS, "Un-interruptable sleep, short time use only");
  req.tv_sec = 0;
  req.tv_nsec = ns;
  ::nanosleep(&req, NULL);
  return;
}

void os::naked_short_sleep(jlong ms) {
  assert(ms < MILLIUNITS, "Un-interruptable sleep, short time use only");
  os::naked_short_nanosleep(ms * (NANOUNITS / MILLIUNITS));
  return;
}

////////////////////////////////////////////////////////////////////////////////
// interrupt support

void os::interrupt(Thread* thread) {
  debug_only(Thread::check_for_dangling_thread_pointer(thread);)

  OSThread* osthread = thread->osthread();

  if (!osthread->interrupted()) {
    osthread->set_interrupted(true);
    // More than one thread can get here with the same value of osthread,
    // resulting in multiple notifications.  We do, however, want the store
    // to interrupted() to be visible to other threads before we execute unpark().
    OrderAccess::fence();
    ParkEvent * const slp = thread->_SleepEvent ;
    if (slp != NULL) slp->unpark() ;
  }

  // For JSR166. Unpark even if interrupt status already was set
  if (thread->is_Java_thread())
    ((JavaThread*)thread)->parker()->unpark();

  ParkEvent * ev = thread->_ParkEvent ;
  if (ev != NULL) ev->unpark() ;
}

bool os::is_interrupted(Thread* thread, bool clear_interrupted) {
  debug_only(Thread::check_for_dangling_thread_pointer(thread);)

  OSThread* osthread = thread->osthread();

  bool interrupted = osthread->interrupted();

  // NOTE that since there is no "lock" around the interrupt and
  // is_interrupted operations, there is the possibility that the
  // interrupted flag (in osThread) will be "false" but that the
  // low-level events will be in the signaled state. This is
  // intentional. The effect of this is that Object.wait() and
  // LockSupport.park() will appear to have a spurious wakeup, which
  // is allowed and not harmful, and the possibility is so rare that
  // it is not worth the added complexity to add yet another lock.
  // For the sleep event an explicit reset is performed on entry
  // to os::sleep, so there is no early return. It has also been
  // recommended not to put the interrupted flag into the "event"
  // structure because it hides the issue.
  if (interrupted && clear_interrupted) {
    osthread->set_interrupted(false);
    // consider thread->_SleepEvent->reset() ... optional optimization
  }

  return interrupted;
}



static const struct {
  int sig; const char* name;
}
 g_signal_info[] =
  {
  {  SIGABRT,     "SIGABRT" },
#ifdef SIGAIO
  {  SIGAIO,      "SIGAIO" },
#endif
  {  SIGALRM,     "SIGALRM" },
#ifdef SIGALRM1
  {  SIGALRM1,    "SIGALRM1" },
#endif
  {  SIGBUS,      "SIGBUS" },
#ifdef SIGCANCEL
  {  SIGCANCEL,   "SIGCANCEL" },
#endif
  {  SIGCHLD,     "SIGCHLD" },
#ifdef SIGCLD
  {  SIGCLD,      "SIGCLD" },
#endif
  {  SIGCONT,     "SIGCONT" },
#ifdef SIGCPUFAIL
  {  SIGCPUFAIL,  "SIGCPUFAIL" },
#endif
#ifdef SIGDANGER
  {  SIGDANGER,   "SIGDANGER" },
#endif
#ifdef SIGDIL
  {  SIGDIL,      "SIGDIL" },
#endif
#ifdef SIGEMT
  {  SIGEMT,      "SIGEMT" },
#endif
  {  SIGFPE,      "SIGFPE" },
#ifdef SIGFREEZE
  {  SIGFREEZE,   "SIGFREEZE" },
#endif
#ifdef SIGGFAULT
  {  SIGGFAULT,   "SIGGFAULT" },
#endif
#ifdef SIGGRANT
  {  SIGGRANT,    "SIGGRANT" },
#endif
  {  SIGHUP,      "SIGHUP" },
  {  SIGILL,      "SIGILL" },
  {  SIGINT,      "SIGINT" },
#ifdef SIGIO
  {  SIGIO,       "SIGIO" },
#endif
#ifdef SIGIOINT
  {  SIGIOINT,    "SIGIOINT" },
#endif
#ifdef SIGIOT
// SIGIOT is there for BSD compatibility, but on most Unices just a
// synonym for SIGABRT. The result should be "SIGABRT", not
// "SIGIOT".
#if (SIGIOT != SIGABRT )
  {  SIGIOT,      "SIGIOT" },
#endif
#endif
#ifdef SIGKAP
  {  SIGKAP,      "SIGKAP" },
#endif
  {  SIGKILL,     "SIGKILL" },
#ifdef SIGLOST
  {  SIGLOST,     "SIGLOST" },
#endif
#ifdef SIGLWP
  {  SIGLWP,      "SIGLWP" },
#endif
#ifdef SIGLWPTIMER
  {  SIGLWPTIMER, "SIGLWPTIMER" },
#endif
#ifdef SIGMIGRATE
  {  SIGMIGRATE,  "SIGMIGRATE" },
#endif
#ifdef SIGMSG
  {  SIGMSG,      "SIGMSG" },
#endif
  {  SIGPIPE,     "SIGPIPE" },
#ifdef SIGPOLL
  {  SIGPOLL,     "SIGPOLL" },
#endif
#ifdef SIGPRE
  {  SIGPRE,      "SIGPRE" },
#endif
  {  SIGPROF,     "SIGPROF" },
#ifdef SIGPTY
  {  SIGPTY,      "SIGPTY" },
#endif
#ifdef SIGPWR
  {  SIGPWR,      "SIGPWR" },
#endif
  {  SIGQUIT,     "SIGQUIT" },
#ifdef SIGRECONFIG
  {  SIGRECONFIG, "SIGRECONFIG" },
#endif
#ifdef SIGRECOVERY
  {  SIGRECOVERY, "SIGRECOVERY" },
#endif
#ifdef SIGRESERVE
  {  SIGRESERVE,  "SIGRESERVE" },
#endif
#ifdef SIGRETRACT
  {  SIGRETRACT,  "SIGRETRACT" },
#endif
#ifdef SIGSAK
  {  SIGSAK,      "SIGSAK" },
#endif
  {  SIGSEGV,     "SIGSEGV" },
#ifdef SIGSOUND
  {  SIGSOUND,    "SIGSOUND" },
#endif
#ifdef SIGSTKFLT
  {  SIGSTKFLT,    "SIGSTKFLT" },
#endif
  {  SIGSTOP,     "SIGSTOP" },
  {  SIGSYS,      "SIGSYS" },
#ifdef SIGSYSERROR
  {  SIGSYSERROR, "SIGSYSERROR" },
#endif
#ifdef SIGTALRM
  {  SIGTALRM,    "SIGTALRM" },
#endif
  {  SIGTERM,     "SIGTERM" },
#ifdef SIGTHAW
  {  SIGTHAW,     "SIGTHAW" },
#endif
  {  SIGTRAP,     "SIGTRAP" },
#ifdef SIGTSTP
  {  SIGTSTP,     "SIGTSTP" },
#endif
  {  SIGTTIN,     "SIGTTIN" },
  {  SIGTTOU,     "SIGTTOU" },
#ifdef SIGURG
  {  SIGURG,      "SIGURG" },
#endif
  {  SIGUSR1,     "SIGUSR1" },
  {  SIGUSR2,     "SIGUSR2" },
#ifdef SIGVIRT
  {  SIGVIRT,     "SIGVIRT" },
#endif
  {  SIGVTALRM,   "SIGVTALRM" },
#ifdef SIGWAITING
  {  SIGWAITING,  "SIGWAITING" },
#endif
#ifdef SIGWINCH
  {  SIGWINCH,    "SIGWINCH" },
#endif
#ifdef SIGWINDOW
  {  SIGWINDOW,   "SIGWINDOW" },
#endif
  {  SIGXCPU,     "SIGXCPU" },
  {  SIGXFSZ,     "SIGXFSZ" },
#ifdef SIGXRES
  {  SIGXRES,     "SIGXRES" },
#endif
  { -1, NULL }
};

// Returned string is a constant. For unknown signals "UNKNOWN" is returned.
const char* os::Posix::get_signal_name(int sig, char* out, size_t outlen) {

  const char* ret = NULL;

#ifdef SIGRTMIN
  if (sig >= SIGRTMIN && sig <= SIGRTMAX) {
    if (sig == SIGRTMIN) {
      ret = "SIGRTMIN";
    } else if (sig == SIGRTMAX) {
      ret = "SIGRTMAX";
    } else {
      jio_snprintf(out, outlen, "SIGRTMIN+%d", sig - SIGRTMIN);
      return out;
    }
  }
#endif

  if (sig > 0) {
    for (int idx = 0; g_signal_info[idx].sig != -1; idx ++) {
      if (g_signal_info[idx].sig == sig) {
        ret = g_signal_info[idx].name;
        break;
      }
    }
  }

  if (!ret) {
    if (!is_valid_signal(sig)) {
      ret = "INVALID";
    } else {
      ret = "UNKNOWN";
    }
  }

  if (out && outlen > 0) {
    strncpy(out, ret, outlen);
    out[outlen - 1] = '\0';
  }
  return out;
}

int os::Posix::get_signal_number(const char* signal_name) {
  char tmp[30];
  const char* s = signal_name;
  if (s[0] != 'S' || s[1] != 'I' || s[2] != 'G') {
    jio_snprintf(tmp, sizeof(tmp), "SIG%s", signal_name);
    s = tmp;
  }
  for (int idx = 0; g_signal_info[idx].sig != -1; idx ++) {
    if (strcmp(g_signal_info[idx].name, s) == 0) {
      return g_signal_info[idx].sig;
    }
  }
  return -1;
}

int os::get_signal_number(const char* signal_name) {
  return os::Posix::get_signal_number(signal_name);
}

// Returns true if signal number is valid.
bool os::Posix::is_valid_signal(int sig) {
  // MacOS not really POSIX compliant: sigaddset does not return
  // an error for invalid signal numbers. However, MacOS does not
  // support real time signals and simply seems to have just 33
  // signals with no holes in the signal range.
#ifdef __APPLE__
  return sig >= 1 && sig < NSIG;
#else
  // Use sigaddset to check for signal validity.
  sigset_t set;
  sigemptyset(&set);
  if (sigaddset(&set, sig) == -1 && errno == EINVAL) {
    return false;
  }
  return true;
#endif
}

bool os::Posix::is_sig_ignored(int sig) {
  struct sigaction oact;
  sigaction(sig, (struct sigaction*)NULL, &oact);
  void* ohlr = oact.sa_sigaction ? CAST_FROM_FN_PTR(void*,  oact.sa_sigaction)
                                 : CAST_FROM_FN_PTR(void*,  oact.sa_handler);
  if (ohlr == CAST_FROM_FN_PTR(void*, SIG_IGN)) {
    return true;
  } else {
    return false;
  }
}

// Returns:
// NULL for an invalid signal number
// "SIG<num>" for a valid but unknown signal number
// signal name otherwise.
const char* os::exception_name(int sig, char* buf, size_t size) {
  if (!os::Posix::is_valid_signal(sig)) {
    return NULL;
  }
  const char* const name = os::Posix::get_signal_name(sig, buf, size);
  if (strcmp(name, "UNKNOWN") == 0) {
    jio_snprintf(buf, size, "SIG%d", sig);
  }
  return buf;
}

#define NUM_IMPORTANT_SIGS 32
// Returns one-line short description of a signal set in a user provided buffer.
const char* os::Posix::describe_signal_set_short(const sigset_t* set, char* buffer, size_t buf_size) {
  assert(buf_size == (NUM_IMPORTANT_SIGS + 1), "wrong buffer size");
  // Note: for shortness, just print out the first 32. That should
  // cover most of the useful ones, apart from realtime signals.
  for (int sig = 1; sig <= NUM_IMPORTANT_SIGS; sig++) {
    const int rc = sigismember(set, sig);
    if (rc == -1 && errno == EINVAL) {
      buffer[sig-1] = '?';
    } else {
      buffer[sig-1] = rc == 0 ? '0' : '1';
    }
  }
  buffer[NUM_IMPORTANT_SIGS] = 0;
  return buffer;
}

// Prints one-line description of a signal set.
void os::Posix::print_signal_set_short(outputStream* st, const sigset_t* set) {
  char buf[NUM_IMPORTANT_SIGS + 1];
  os::Posix::describe_signal_set_short(set, buf, sizeof(buf));
  st->print("%s", buf);
}

// Writes one-line description of a combination of sigaction.sa_flags into a user
// provided buffer. Returns that buffer.
const char* os::Posix::describe_sa_flags(int flags, char* buffer, size_t size) {
  char* p = buffer;
  size_t remaining = size;
  bool first = true;
  int idx = 0;

  assert(buffer, "invalid argument");

  if (size == 0) {
    return buffer;
  }

  strncpy(buffer, "none", size);

  const struct {
    // NB: i is an unsigned int here because SA_RESETHAND is on some
    // systems 0x80000000, which is implicitly unsigned.  Assignining
    // it to an int field would be an overflow in unsigned-to-signed
    // conversion.
    unsigned int i;
    const char* s;
  } flaginfo [] = {
    { SA_NOCLDSTOP, "SA_NOCLDSTOP" },
    { SA_ONSTACK,   "SA_ONSTACK"   },
    { SA_RESETHAND, "SA_RESETHAND" },
    { SA_RESTART,   "SA_RESTART"   },
    { SA_SIGINFO,   "SA_SIGINFO"   },
    { SA_NOCLDWAIT, "SA_NOCLDWAIT" },
    { SA_NODEFER,   "SA_NODEFER"   },
#ifdef AIX
    { SA_ONSTACK,   "SA_ONSTACK"   },
    { SA_OLDSTYLE,  "SA_OLDSTYLE"  },
#endif
    { 0, NULL }
  };

  for (idx = 0; flaginfo[idx].s && remaining > 1; idx++) {
    if (flags & flaginfo[idx].i) {
      if (first) {
        jio_snprintf(p, remaining, "%s", flaginfo[idx].s);
        first = false;
      } else {
        jio_snprintf(p, remaining, "|%s", flaginfo[idx].s);
      }
      const size_t len = strlen(p);
      p += len;
      remaining -= len;
    }
  }

  buffer[size - 1] = '\0';

  return buffer;
}

// Prints one-line description of a combination of sigaction.sa_flags.
void os::Posix::print_sa_flags(outputStream* st, int flags) {
  char buffer[0x100];
  os::Posix::describe_sa_flags(flags, buffer, sizeof(buffer));
  st->print("%s", buffer);
}

// Helper function for os::Posix::print_siginfo_...():
// return a textual description for signal code.
struct enum_sigcode_desc_t {
  const char* s_name;
  const char* s_desc;
};

static bool get_signal_code_description(const siginfo_t* si, enum_sigcode_desc_t* out) {

  const struct {
    int sig; int code; const char* s_code; const char* s_desc;
  } t1 [] = {
    { SIGILL,  ILL_ILLOPC,   "ILL_ILLOPC",   "Illegal opcode." },
    { SIGILL,  ILL_ILLOPN,   "ILL_ILLOPN",   "Illegal operand." },
    { SIGILL,  ILL_ILLADR,   "ILL_ILLADR",   "Illegal addressing mode." },
    { SIGILL,  ILL_ILLTRP,   "ILL_ILLTRP",   "Illegal trap." },
    { SIGILL,  ILL_PRVOPC,   "ILL_PRVOPC",   "Privileged opcode." },
    { SIGILL,  ILL_PRVREG,   "ILL_PRVREG",   "Privileged register." },
    { SIGILL,  ILL_COPROC,   "ILL_COPROC",   "Coprocessor error." },
    { SIGILL,  ILL_BADSTK,   "ILL_BADSTK",   "Internal stack error." },
#if defined(IA64) && defined(LINUX)
    { SIGILL,  ILL_BADIADDR, "ILL_BADIADDR", "Unimplemented instruction address" },
    { SIGILL,  ILL_BREAK,    "ILL_BREAK",    "Application Break instruction" },
#endif
    { SIGFPE,  FPE_INTDIV,   "FPE_INTDIV",   "Integer divide by zero." },
    { SIGFPE,  FPE_INTOVF,   "FPE_INTOVF",   "Integer overflow." },
    { SIGFPE,  FPE_FLTDIV,   "FPE_FLTDIV",   "Floating-point divide by zero." },
    { SIGFPE,  FPE_FLTOVF,   "FPE_FLTOVF",   "Floating-point overflow." },
    { SIGFPE,  FPE_FLTUND,   "FPE_FLTUND",   "Floating-point underflow." },
    { SIGFPE,  FPE_FLTRES,   "FPE_FLTRES",   "Floating-point inexact result." },
    { SIGFPE,  FPE_FLTINV,   "FPE_FLTINV",   "Invalid floating-point operation." },
    { SIGFPE,  FPE_FLTSUB,   "FPE_FLTSUB",   "Subscript out of range." },
    { SIGSEGV, SEGV_MAPERR,  "SEGV_MAPERR",  "Address not mapped to object." },
    { SIGSEGV, SEGV_ACCERR,  "SEGV_ACCERR",  "Invalid permissions for mapped object." },
#ifdef AIX
    // no explanation found what keyerr would be
    { SIGSEGV, SEGV_KEYERR,  "SEGV_KEYERR",  "key error" },
#endif
#if defined(IA64) && !defined(AIX)
    { SIGSEGV, SEGV_PSTKOVF, "SEGV_PSTKOVF", "Paragraph stack overflow" },
#endif
#if defined(__sparc) && defined(SOLARIS)
// define Solaris Sparc M7 ADI SEGV signals
#if !defined(SEGV_ACCADI)
#define SEGV_ACCADI 3
#endif
    { SIGSEGV, SEGV_ACCADI,  "SEGV_ACCADI",  "ADI not enabled for mapped object." },
#if !defined(SEGV_ACCDERR)
#define SEGV_ACCDERR 4
#endif
    { SIGSEGV, SEGV_ACCDERR, "SEGV_ACCDERR", "ADI disrupting exception." },
#if !defined(SEGV_ACCPERR)
#define SEGV_ACCPERR 5
#endif
    { SIGSEGV, SEGV_ACCPERR, "SEGV_ACCPERR", "ADI precise exception." },
#endif // defined(__sparc) && defined(SOLARIS)
    { SIGBUS,  BUS_ADRALN,   "BUS_ADRALN",   "Invalid address alignment." },
    { SIGBUS,  BUS_ADRERR,   "BUS_ADRERR",   "Nonexistent physical address." },
    { SIGBUS,  BUS_OBJERR,   "BUS_OBJERR",   "Object-specific hardware error." },
    { SIGTRAP, TRAP_BRKPT,   "TRAP_BRKPT",   "Process breakpoint." },
    { SIGTRAP, TRAP_TRACE,   "TRAP_TRACE",   "Process trace trap." },
    { SIGCHLD, CLD_EXITED,   "CLD_EXITED",   "Child has exited." },
    { SIGCHLD, CLD_KILLED,   "CLD_KILLED",   "Child has terminated abnormally and did not create a core file." },
    { SIGCHLD, CLD_DUMPED,   "CLD_DUMPED",   "Child has terminated abnormally and created a core file." },
    { SIGCHLD, CLD_TRAPPED,  "CLD_TRAPPED",  "Traced child has trapped." },
    { SIGCHLD, CLD_STOPPED,  "CLD_STOPPED",  "Child has stopped." },
    { SIGCHLD, CLD_CONTINUED,"CLD_CONTINUED","Stopped child has continued." },
#ifdef SIGPOLL
    { SIGPOLL, POLL_OUT,     "POLL_OUT",     "Output buffers available." },
    { SIGPOLL, POLL_MSG,     "POLL_MSG",     "Input message available." },
    { SIGPOLL, POLL_ERR,     "POLL_ERR",     "I/O error." },
    { SIGPOLL, POLL_PRI,     "POLL_PRI",     "High priority input available." },
    { SIGPOLL, POLL_HUP,     "POLL_HUP",     "Device disconnected. [Option End]" },
#endif
    { -1, -1, NULL, NULL }
  };

  // Codes valid in any signal context.
  const struct {
    int code; const char* s_code; const char* s_desc;
  } t2 [] = {
    { SI_USER,      "SI_USER",     "Signal sent by kill()." },
    { SI_QUEUE,     "SI_QUEUE",    "Signal sent by the sigqueue()." },
    { SI_TIMER,     "SI_TIMER",    "Signal generated by expiration of a timer set by timer_settime()." },
    { SI_ASYNCIO,   "SI_ASYNCIO",  "Signal generated by completion of an asynchronous I/O request." },
    { SI_MESGQ,     "SI_MESGQ",    "Signal generated by arrival of a message on an empty message queue." },
    // Linux specific
#ifdef SI_TKILL
    { SI_TKILL,     "SI_TKILL",    "Signal sent by tkill (pthread_kill)" },
#endif
#ifdef SI_DETHREAD
    { SI_DETHREAD,  "SI_DETHREAD", "Signal sent by execve() killing subsidiary threads" },
#endif
#ifdef SI_KERNEL
    { SI_KERNEL,    "SI_KERNEL",   "Signal sent by kernel." },
#endif
#ifdef SI_SIGIO
    { SI_SIGIO,     "SI_SIGIO",    "Signal sent by queued SIGIO" },
#endif

#ifdef AIX
    { SI_UNDEFINED, "SI_UNDEFINED","siginfo contains partial information" },
    { SI_EMPTY,     "SI_EMPTY",    "siginfo contains no useful information" },
#endif

#ifdef __sun
    { SI_NOINFO,    "SI_NOINFO",   "No signal information" },
    { SI_RCTL,      "SI_RCTL",     "kernel generated signal via rctl action" },
    { SI_LWP,       "SI_LWP",      "Signal sent via lwp_kill" },
#endif

    { -1, NULL, NULL }
  };

  const char* s_code = NULL;
  const char* s_desc = NULL;

  for (int i = 0; t1[i].sig != -1; i ++) {
    if (t1[i].sig == si->si_signo && t1[i].code == si->si_code) {
      s_code = t1[i].s_code;
      s_desc = t1[i].s_desc;
      break;
    }
  }

  if (s_code == NULL) {
    for (int i = 0; t2[i].s_code != NULL; i ++) {
      if (t2[i].code == si->si_code) {
        s_code = t2[i].s_code;
        s_desc = t2[i].s_desc;
      }
    }
  }

  if (s_code == NULL) {
    out->s_name = "unknown";
    out->s_desc = "unknown";
    return false;
  }

  out->s_name = s_code;
  out->s_desc = s_desc;

  return true;
}

bool os::signal_sent_by_kill(const void* siginfo) {
  const siginfo_t* const si = (const siginfo_t*)siginfo;
  return si->si_code == SI_USER || si->si_code == SI_QUEUE
#ifdef SI_TKILL
         || si->si_code == SI_TKILL
#endif
  ;
}

void os::print_siginfo(outputStream* os, const void* si0) {

  const siginfo_t* const si = (const siginfo_t*) si0;

  char buf[20];
  os->print("siginfo:");

  if (!si) {
    os->print(" <null>");
    return;
  }

  const int sig = si->si_signo;

  os->print(" si_signo: %d (%s)", sig, os::Posix::get_signal_name(sig, buf, sizeof(buf)));

  enum_sigcode_desc_t ed;
  get_signal_code_description(si, &ed);
  os->print(", si_code: %d (%s)", si->si_code, ed.s_name);

  if (si->si_errno) {
    os->print(", si_errno: %d", si->si_errno);
  }

  // Output additional information depending on the signal code.

  // Note: Many implementations lump si_addr, si_pid, si_uid etc. together as unions,
  // so it depends on the context which member to use. For synchronous error signals,
  // we print si_addr, unless the signal was sent by another process or thread, in
  // which case we print out pid or tid of the sender.
  if (signal_sent_by_kill(si)) {
    const pid_t pid = si->si_pid;
    os->print(", si_pid: %ld", (long) pid);
    if (IS_VALID_PID(pid)) {
      const pid_t me = getpid();
      if (me == pid) {
        os->print(" (current process)");
      }
    } else {
      os->print(" (invalid)");
    }
    os->print(", si_uid: %ld", (long) si->si_uid);
    if (sig == SIGCHLD) {
      os->print(", si_status: %d", si->si_status);
    }
  } else if (sig == SIGSEGV || sig == SIGBUS || sig == SIGILL ||
             sig == SIGTRAP || sig == SIGFPE) {
    os->print(", si_addr: " PTR_FORMAT, p2i(si->si_addr));
#ifdef SIGPOLL
  } else if (sig == SIGPOLL) {
    os->print(", si_band: %ld", si->si_band);
#endif
  }

}

bool os::signal_thread(Thread* thread, int sig, const char* reason) {
  OSThread* osthread = thread->osthread();
  if (osthread) {
#if defined (SOLARIS)
    // Note: we cannot use pthread_kill on Solaris - not because
    // its missing, but because we do not have the pthread_t id.
    int status = thr_kill(osthread->thread_id(), sig);
#else
    int status = pthread_kill(osthread->pthread_id(), sig);
#endif
    if (status == 0) {
      Events::log(Thread::current(), "sent signal %d to Thread " INTPTR_FORMAT " because %s.",
                  sig, p2i(thread), reason);
      return true;
    }
  }
  return false;
}

int os::Posix::unblock_thread_signal_mask(const sigset_t *set) {
  return pthread_sigmask(SIG_UNBLOCK, set, NULL);
}

address os::Posix::ucontext_get_pc(const ucontext_t* ctx) {
#if defined(AIX)
   return Aix::ucontext_get_pc(ctx);
#elif defined(BSD)
   return Bsd::ucontext_get_pc(ctx);
#elif defined(LINUX)
   return Linux::ucontext_get_pc(ctx);
#elif defined(SOLARIS)
   return Solaris::ucontext_get_pc(ctx);
#else
   VMError::report_and_die("unimplemented ucontext_get_pc");
#endif
}

void os::Posix::ucontext_set_pc(ucontext_t* ctx, address pc) {
#if defined(AIX)
   Aix::ucontext_set_pc(ctx, pc);
#elif defined(BSD)
   Bsd::ucontext_set_pc(ctx, pc);
#elif defined(LINUX)
   Linux::ucontext_set_pc(ctx, pc);
#elif defined(SOLARIS)
   Solaris::ucontext_set_pc(ctx, pc);
#else
   VMError::report_and_die("unimplemented ucontext_get_pc");
#endif
}

char* os::Posix::describe_pthread_attr(char* buf, size_t buflen, const pthread_attr_t* attr) {
  size_t stack_size = 0;
  size_t guard_size = 0;
  int detachstate = 0;
  pthread_attr_getstacksize(attr, &stack_size);
  pthread_attr_getguardsize(attr, &guard_size);
  // Work around linux NPTL implementation error, see also os::create_thread() in os_linux.cpp.
  LINUX_ONLY(stack_size -= guard_size);
  pthread_attr_getdetachstate(attr, &detachstate);
  jio_snprintf(buf, buflen, "stacksize: " SIZE_FORMAT "k, guardsize: " SIZE_FORMAT "k, %s",
    stack_size / 1024, guard_size / 1024,
    (detachstate == PTHREAD_CREATE_DETACHED ? "detached" : "joinable"));
  return buf;
}

char* os::Posix::realpath(const char* filename, char* outbuf, size_t outbuflen) {

  if (filename == NULL || outbuf == NULL || outbuflen < 1) {
    assert(false, "os::Posix::realpath: invalid arguments.");
    errno = EINVAL;
    return NULL;
  }

  char* result = NULL;

  // This assumes platform realpath() is implemented according to POSIX.1-2008.
  // POSIX.1-2008 allows to specify NULL for the output buffer, in which case
  // output buffer is dynamically allocated and must be ::free()'d by the caller.
  char* p = ::realpath(filename, NULL);
  if (p != NULL) {
    if (strlen(p) < outbuflen) {
      strcpy(outbuf, p);
      result = outbuf;
    } else {
      errno = ENAMETOOLONG;
    }
    ::free(p); // *not* os::free
  } else {
    // Fallback for platforms struggling with modern Posix standards (AIX 5.3, 6.1). If realpath
    // returns EINVAL, this may indicate that realpath is not POSIX.1-2008 compatible and
    // that it complains about the NULL we handed down as user buffer.
    // In this case, use the user provided buffer but at least check whether realpath caused
    // a memory overwrite.
    if (errno == EINVAL) {
      outbuf[outbuflen - 1] = '\0';
      p = ::realpath(filename, outbuf);
      if (p != NULL) {
        guarantee(outbuf[outbuflen - 1] == '\0', "realpath buffer overwrite detected.");
        result = p;
      }
    }
  }
  return result;

}

int os::stat(const char *path, struct stat *sbuf) {
  return ::stat(path, sbuf);
}

char * os::native_path(char *path) {
  return path;
}

// Check minimum allowable stack sizes for thread creation and to initialize
// the java system classes, including StackOverflowError - depends on page
// size.
// The space needed for frames during startup is platform dependent. It
// depends on word size, platform calling conventions, C frame layout and
// interpreter/C1/C2 design decisions. Therefore this is given in a
// platform (os/cpu) dependent constant.
// To this, space for guard mechanisms is added, which depends on the
// page size which again depends on the concrete system the VM is running
// on. Space for libc guard pages is not included in this size.
jint os::Posix::set_minimum_stack_sizes() {
  size_t os_min_stack_allowed = SOLARIS_ONLY(thr_min_stack()) NOT_SOLARIS(PTHREAD_STACK_MIN);

  _java_thread_min_stack_allowed = _java_thread_min_stack_allowed +
                                   JavaThread::stack_guard_zone_size() +
                                   JavaThread::stack_shadow_zone_size();

  _java_thread_min_stack_allowed = align_up(_java_thread_min_stack_allowed, vm_page_size());
  _java_thread_min_stack_allowed = MAX2(_java_thread_min_stack_allowed, os_min_stack_allowed);

  size_t stack_size_in_bytes = ThreadStackSize * K;
  if (stack_size_in_bytes != 0 &&
      stack_size_in_bytes < _java_thread_min_stack_allowed) {
    // The '-Xss' and '-XX:ThreadStackSize=N' options both set
    // ThreadStackSize so we go with "Java thread stack size" instead
    // of "ThreadStackSize" to be more friendly.
    tty->print_cr("\nThe Java thread stack size specified is too small. "
                  "Specify at least " SIZE_FORMAT "k",
                  _java_thread_min_stack_allowed / K);
    return JNI_ERR;
  }

  // Make the stack size a multiple of the page size so that
  // the yellow/red zones can be guarded.
  JavaThread::set_stack_size_at_create(align_up(stack_size_in_bytes, vm_page_size()));

  // Reminder: a compiler thread is a Java thread.
  _compiler_thread_min_stack_allowed = _compiler_thread_min_stack_allowed +
                                       JavaThread::stack_guard_zone_size() +
                                       JavaThread::stack_shadow_zone_size();

  _compiler_thread_min_stack_allowed = align_up(_compiler_thread_min_stack_allowed, vm_page_size());
  _compiler_thread_min_stack_allowed = MAX2(_compiler_thread_min_stack_allowed, os_min_stack_allowed);

  stack_size_in_bytes = CompilerThreadStackSize * K;
  if (stack_size_in_bytes != 0 &&
      stack_size_in_bytes < _compiler_thread_min_stack_allowed) {
    tty->print_cr("\nThe CompilerThreadStackSize specified is too small. "
                  "Specify at least " SIZE_FORMAT "k",
                  _compiler_thread_min_stack_allowed / K);
    return JNI_ERR;
  }

  _vm_internal_thread_min_stack_allowed = align_up(_vm_internal_thread_min_stack_allowed, vm_page_size());
  _vm_internal_thread_min_stack_allowed = MAX2(_vm_internal_thread_min_stack_allowed, os_min_stack_allowed);

  stack_size_in_bytes = VMThreadStackSize * K;
  if (stack_size_in_bytes != 0 &&
      stack_size_in_bytes < _vm_internal_thread_min_stack_allowed) {
    tty->print_cr("\nThe VMThreadStackSize specified is too small. "
                  "Specify at least " SIZE_FORMAT "k",
                  _vm_internal_thread_min_stack_allowed / K);
    return JNI_ERR;
  }
  return JNI_OK;
}

// Called when creating the thread.  The minimum stack sizes have already been calculated
size_t os::Posix::get_initial_stack_size(ThreadType thr_type, size_t req_stack_size) {
  size_t stack_size;
  if (req_stack_size == 0) {
    stack_size = default_stack_size(thr_type);
  } else {
    stack_size = req_stack_size;
  }

  switch (thr_type) {
  case os::java_thread:
    // Java threads use ThreadStackSize which default value can be
    // changed with the flag -Xss
    if (req_stack_size == 0 && JavaThread::stack_size_at_create() > 0) {
      // no requested size and we have a more specific default value
      stack_size = JavaThread::stack_size_at_create();
    }
    stack_size = MAX2(stack_size,
                      _java_thread_min_stack_allowed);
    break;
  case os::compiler_thread:
    if (req_stack_size == 0 && CompilerThreadStackSize > 0) {
      // no requested size and we have a more specific default value
      stack_size = (size_t)(CompilerThreadStackSize * K);
    }
    stack_size = MAX2(stack_size,
                      _compiler_thread_min_stack_allowed);
    break;
  case os::vm_thread:
  case os::pgc_thread:
  case os::cgc_thread:
  case os::watcher_thread:
  default:  // presume the unknown thr_type is a VM internal
    if (req_stack_size == 0 && VMThreadStackSize > 0) {
      // no requested size and we have a more specific default value
      stack_size = (size_t)(VMThreadStackSize * K);
    }

    stack_size = MAX2(stack_size,
                      _vm_internal_thread_min_stack_allowed);
    break;
  }

  // pthread_attr_setstacksize() may require that the size be rounded up to the OS page size.
  // Be careful not to round up to 0. Align down in that case.
  if (stack_size <= SIZE_MAX - vm_page_size()) {
    stack_size = align_up(stack_size, vm_page_size());
  } else {
    stack_size = align_down(stack_size, vm_page_size());
  }

  return stack_size;
}

bool os::Posix::is_root(uid_t uid){
    return ROOT_UID == uid;
}

bool os::Posix::matches_effective_uid_or_root(uid_t uid) {
    return is_root(uid) || geteuid() == uid;
}

bool os::Posix::matches_effective_uid_and_gid_or_root(uid_t uid, gid_t gid) {
    return is_root(uid) || (geteuid() == uid && getegid() == gid);
}

Thread* os::ThreadCrashProtection::_protected_thread = NULL;
os::ThreadCrashProtection* os::ThreadCrashProtection::_crash_protection = NULL;
volatile intptr_t os::ThreadCrashProtection::_crash_mux = 0;

os::ThreadCrashProtection::ThreadCrashProtection() {
}

/*
 * See the caveats for this class in os_posix.hpp
 * Protects the callback call so that SIGSEGV / SIGBUS jumps back into this
 * method and returns false. If none of the signals are raised, returns true.
 * The callback is supposed to provide the method that should be protected.
 */
bool os::ThreadCrashProtection::call(os::CrashProtectionCallback& cb) {
  sigset_t saved_sig_mask;

  Thread::muxAcquire(&_crash_mux, "CrashProtection");

  _protected_thread = Thread::current_or_null();
  assert(_protected_thread != NULL, "Cannot crash protect a NULL thread");

  // we cannot rely on sigsetjmp/siglongjmp to save/restore the signal mask
  // since on at least some systems (OS X) siglongjmp will restore the mask
  // for the process, not the thread
  pthread_sigmask(0, NULL, &saved_sig_mask);
  if (sigsetjmp(_jmpbuf, 0) == 0) {
    // make sure we can see in the signal handler that we have crash protection
    // installed
    _crash_protection = this;
    cb.call();
    // and clear the crash protection
    _crash_protection = NULL;
    _protected_thread = NULL;
    Thread::muxRelease(&_crash_mux);
    return true;
  }
  // this happens when we siglongjmp() back
  pthread_sigmask(SIG_SETMASK, &saved_sig_mask, NULL);
  _crash_protection = NULL;
  _protected_thread = NULL;
  Thread::muxRelease(&_crash_mux);
  return false;
}

void os::ThreadCrashProtection::restore() {
  assert(_crash_protection != NULL, "must have crash protection");
  siglongjmp(_jmpbuf, 1);
}

void os::ThreadCrashProtection::check_crash_protection(int sig,
    Thread* thread) {

  if (thread != NULL &&
      thread == _protected_thread &&
      _crash_protection != NULL) {

    if (sig == SIGSEGV || sig == SIGBUS) {
      _crash_protection->restore();
    }
  }
}

// Shared clock/time and other supporting routines for pthread_mutex/cond
// initialization. This is enabled on Solaris but only some of the clock/time
// functionality is actually used there.

// Shared condattr object for use with relative timed-waits. Will be associated
// with CLOCK_MONOTONIC if available to avoid issues with time-of-day changes,
// but otherwise whatever default is used by the platform - generally the
// time-of-day clock.
static pthread_condattr_t _condAttr[1];

// Shared mutexattr to explicitly set the type to PTHREAD_MUTEX_NORMAL as not
// all systems (e.g. FreeBSD) map the default to "normal".
static pthread_mutexattr_t _mutexAttr[1];

// common basic initialization that is always supported
static void pthread_init_common(void) {
  int status;
  if ((status = pthread_condattr_init(_condAttr)) != 0) {
    fatal("pthread_condattr_init: %s", os::strerror(status));
  }
  if ((status = pthread_mutexattr_init(_mutexAttr)) != 0) {
    fatal("pthread_mutexattr_init: %s", os::strerror(status));
  }
  if ((status = pthread_mutexattr_settype(_mutexAttr, PTHREAD_MUTEX_NORMAL)) != 0) {
    fatal("pthread_mutexattr_settype: %s", os::strerror(status));
  }
  // Solaris has it's own PlatformMonitor, distinct from the one for POSIX.
  NOT_SOLARIS(os::PlatformMonitor::init();)
}

#ifndef SOLARIS
sigset_t sigs;
struct sigaction sigact[NSIG];

struct sigaction* os::Posix::get_preinstalled_handler(int sig) {
  if (sigismember(&sigs, sig)) {
    return &sigact[sig];
  }
  return NULL;
}

void os::Posix::save_preinstalled_handler(int sig, struct sigaction& oldAct) {
  assert(sig > 0 && sig < NSIG, "vm signal out of expected range");
  sigact[sig] = oldAct;
  sigaddset(&sigs, sig);
}
#endif

// Not all POSIX types and API's are available on all notionally "posix"
// platforms. If we have build-time support then we will check for actual
// runtime support via dlopen/dlsym lookup. This allows for running on an
// older OS version compared to the build platform. But if there is no
// build time support then there cannot be any runtime support as we do not
// know what the runtime types would be (for example clockid_t might be an
// int or int64_t).
//
#ifdef SUPPORTS_CLOCK_MONOTONIC

// This means we have clockid_t, clock_gettime et al and CLOCK_MONOTONIC

int (*os::Posix::_clock_gettime)(clockid_t, struct timespec *) = NULL;
int (*os::Posix::_clock_getres)(clockid_t, struct timespec *) = NULL;

static int (*_pthread_condattr_setclock)(pthread_condattr_t *, clockid_t) = NULL;

static bool _use_clock_monotonic_condattr = false;

// Determine what POSIX API's are present and do appropriate
// configuration.
void os::Posix::init(void) {

  // NOTE: no logging available when this is called. Put logging
  // statements in init_2().

  // 1. Check for CLOCK_MONOTONIC support.

  void* handle = NULL;

  // For linux we need librt, for other OS we can find
  // this function in regular libc.
#ifdef NEEDS_LIBRT
  // We do dlopen's in this particular order due to bug in linux
  // dynamic loader (see 6348968) leading to crash on exit.
  handle = dlopen("librt.so.1", RTLD_LAZY);
  if (handle == NULL) {
    handle = dlopen("librt.so", RTLD_LAZY);
  }
#endif

  if (handle == NULL) {
    handle = RTLD_DEFAULT;
  }

  int (*clock_getres_func)(clockid_t, struct timespec*) =
    (int(*)(clockid_t, struct timespec*))dlsym(handle, "clock_getres");
  int (*clock_gettime_func)(clockid_t, struct timespec*) =
    (int(*)(clockid_t, struct timespec*))dlsym(handle, "clock_gettime");
  if (clock_getres_func != NULL && clock_gettime_func != NULL) {
    // We assume that if both clock_gettime and clock_getres support
    // CLOCK_MONOTONIC then the OS provides true high-res monotonic clock.
    struct timespec res;
    struct timespec tp;
    if (clock_getres_func(CLOCK_MONOTONIC, &res) == 0 &&
        clock_gettime_func(CLOCK_MONOTONIC, &tp) == 0) {
      // Yes, monotonic clock is supported.
      _clock_gettime = clock_gettime_func;
      _clock_getres = clock_getres_func;
    } else {
#ifdef NEEDS_LIBRT
      // Close librt if there is no monotonic clock.
      if (handle != RTLD_DEFAULT) {
        dlclose(handle);
      }
#endif
    }
  }

  // 2. Check for pthread_condattr_setclock support.

  // libpthread is already loaded.
  int (*condattr_setclock_func)(pthread_condattr_t*, clockid_t) =
    (int (*)(pthread_condattr_t*, clockid_t))dlsym(RTLD_DEFAULT,
                                                   "pthread_condattr_setclock");
  if (condattr_setclock_func != NULL) {
    _pthread_condattr_setclock = condattr_setclock_func;
  }

  // Now do general initialization.

  pthread_init_common();

#ifndef SOLARIS
  int status;
  if (_pthread_condattr_setclock != NULL && _clock_gettime != NULL) {
    if ((status = _pthread_condattr_setclock(_condAttr, CLOCK_MONOTONIC)) != 0) {
      if (status == EINVAL) {
        _use_clock_monotonic_condattr = false;
        warning("Unable to use monotonic clock with relative timed-waits" \
                " - changes to the time-of-day clock may have adverse affects");
      } else {
        fatal("pthread_condattr_setclock: %s", os::strerror(status));
      }
    } else {
      _use_clock_monotonic_condattr = true;
    }
  }
#endif // !SOLARIS

}

void os::Posix::init_2(void) {
#ifndef SOLARIS
  log_info(os)("Use of CLOCK_MONOTONIC is%s supported",
               (_clock_gettime != NULL ? "" : " not"));
  log_info(os)("Use of pthread_condattr_setclock is%s supported",
               (_pthread_condattr_setclock != NULL ? "" : " not"));
  log_info(os)("Relative timed-wait using pthread_cond_timedwait is associated with %s",
               _use_clock_monotonic_condattr ? "CLOCK_MONOTONIC" : "the default clock");
  sigemptyset(&sigs);
#endif // !SOLARIS
}

#else // !SUPPORTS_CLOCK_MONOTONIC

void os::Posix::init(void) {
  pthread_init_common();
}

void os::Posix::init_2(void) {
#ifndef SOLARIS
  log_info(os)("Use of CLOCK_MONOTONIC is not supported");
  log_info(os)("Use of pthread_condattr_setclock is not supported");
  log_info(os)("Relative timed-wait using pthread_cond_timedwait is associated with the default clock");
  sigemptyset(&sigs);
#endif // !SOLARIS
}

#endif // SUPPORTS_CLOCK_MONOTONIC

// Utility to convert the given timeout to an absolute timespec
// (based on the appropriate clock) to use with pthread_cond_timewait,
// and sem_timedwait().
// The clock queried here must be the clock used to manage the
// timeout of the condition variable or semaphore.
//
// The passed in timeout value is either a relative time in nanoseconds
// or an absolute time in milliseconds. A relative timeout will be
// associated with CLOCK_MONOTONIC if available, unless the real-time clock
// is explicitly requested; otherwise, or if absolute,
// the default time-of-day clock will be used.

// Given time is a 64-bit value and the time_t used in the timespec is
// sometimes a signed-32-bit value we have to watch for overflow if times
// way in the future are given. Further on Solaris versions
// prior to 10 there is a restriction (see cond_timedwait) that the specified
// number of seconds, in abstime, is less than current_time + 100000000.
// As it will be over 20 years before "now + 100000000" will overflow we can
// ignore overflow and just impose a hard-limit on seconds using the value
// of "now + 100000000". This places a limit on the timeout of about 3.17
// years from "now".
//
#define MAX_SECS 100000000

// Calculate a new absolute time that is "timeout" nanoseconds from "now".
// "unit" indicates the unit of "now_part_sec" (may be nanos or micros depending
// on which clock API is being used).
static void calc_rel_time(timespec* abstime, jlong timeout, jlong now_sec,
                          jlong now_part_sec, jlong unit) {
  time_t max_secs = now_sec + MAX_SECS;

  jlong seconds = timeout / NANOUNITS;
  timeout %= NANOUNITS; // remaining nanos

  if (seconds >= MAX_SECS) {
    // More seconds than we can add, so pin to max_secs.
    abstime->tv_sec = max_secs;
    abstime->tv_nsec = 0;
  } else {
    abstime->tv_sec = now_sec  + seconds;
    long nanos = (now_part_sec * (NANOUNITS / unit)) + timeout;
    if (nanos >= NANOUNITS) { // overflow
      abstime->tv_sec += 1;
      nanos -= NANOUNITS;
    }
    abstime->tv_nsec = nanos;
  }
}

// Unpack the given deadline in milliseconds since the epoch, into the given timespec.
// The current time in seconds is also passed in to enforce an upper bound as discussed above.
// This is only used with gettimeofday, when clock_gettime is not available.
static void unpack_abs_time(timespec* abstime, jlong deadline, jlong now_sec) {
  time_t max_secs = now_sec + MAX_SECS;

  jlong seconds = deadline / MILLIUNITS;
  jlong millis = deadline % MILLIUNITS;

  if (seconds >= max_secs) {
    // Absolute seconds exceeds allowed max, so pin to max_secs.
    abstime->tv_sec = max_secs;
    abstime->tv_nsec = 0;
  } else {
    abstime->tv_sec = seconds;
    abstime->tv_nsec = millis * (NANOUNITS / MILLIUNITS);
  }
}

static jlong millis_to_nanos(jlong millis) {
  // We have to watch for overflow when converting millis to nanos,
  // but if millis is that large then we will end up limiting to
  // MAX_SECS anyway, so just do that here.
  if (millis / MILLIUNITS > MAX_SECS) {
    millis = jlong(MAX_SECS) * MILLIUNITS;
  }
  return millis * (NANOUNITS / MILLIUNITS);
}

static void to_abstime(timespec* abstime, jlong timeout,
                       bool isAbsolute, bool isRealtime) {
  DEBUG_ONLY(int max_secs = MAX_SECS;)

  if (timeout < 0) {
    timeout = 0;
  }

#ifdef SUPPORTS_CLOCK_MONOTONIC

  clockid_t clock = CLOCK_MONOTONIC;
  // need to ensure we have a runtime check for clock_gettime support
  if (!isAbsolute && os::Posix::supports_monotonic_clock()) {
    if (!_use_clock_monotonic_condattr || isRealtime) {
      clock = CLOCK_REALTIME;
    }
    struct timespec now;
    int status = os::Posix::clock_gettime(clock, &now);
    assert_status(status == 0, status, "clock_gettime");
    calc_rel_time(abstime, timeout, now.tv_sec, now.tv_nsec, NANOUNITS);
    DEBUG_ONLY(max_secs += now.tv_sec;)
  } else {

#else

  { // Match the block scope.

#endif // SUPPORTS_CLOCK_MONOTONIC

    // Time-of-day clock is all we can reliably use.
    struct timeval now;
    int status = gettimeofday(&now, NULL);
    assert_status(status == 0, errno, "gettimeofday");
    if (isAbsolute) {
      unpack_abs_time(abstime, timeout, now.tv_sec);
    } else {
      calc_rel_time(abstime, timeout, now.tv_sec, now.tv_usec, MICROUNITS);
    }
    DEBUG_ONLY(max_secs += now.tv_sec;)
  }

  assert(abstime->tv_sec >= 0, "tv_sec < 0");
  assert(abstime->tv_sec <= max_secs, "tv_sec > max_secs");
  assert(abstime->tv_nsec >= 0, "tv_nsec < 0");
  assert(abstime->tv_nsec < NANOUNITS, "tv_nsec >= NANOUNITS");
}

// Create an absolute time 'millis' milliseconds in the future, using the
// real-time (time-of-day) clock. Used by PosixSemaphore.
void os::Posix::to_RTC_abstime(timespec* abstime, int64_t millis) {
  to_abstime(abstime, millis_to_nanos(millis),
             false /* not absolute */,
             true  /* use real-time clock */);
}

// Shared pthread_mutex/cond based PlatformEvent implementation.
// Not currently usable by Solaris.

#ifndef SOLARIS

// PlatformEvent
//
// Assumption:
//    Only one parker can exist on an event, which is why we allocate
//    them per-thread. Multiple unparkers can coexist.
//
// _event serves as a restricted-range semaphore.
//   -1 : thread is blocked, i.e. there is a waiter
//    0 : neutral: thread is running or ready,
//        could have been signaled after a wait started
//    1 : signaled - thread is running or ready
//
//    Having three states allows for some detection of bad usage - see
//    comments on unpark().

os::PlatformEvent::PlatformEvent() {
  int status = pthread_cond_init(_cond, _condAttr);
  assert_status(status == 0, status, "cond_init");
  status = pthread_mutex_init(_mutex, _mutexAttr);
  assert_status(status == 0, status, "mutex_init");
  _event   = 0;
  _nParked = 0;
}

void os::PlatformEvent::park() {       // AKA "down()"
  // Transitions for _event:
  //   -1 => -1 : illegal
  //    1 =>  0 : pass - return immediately
  //    0 => -1 : block; then set _event to 0 before returning

  // Invariant: Only the thread associated with the PlatformEvent
  // may call park().
  assert(_nParked == 0, "invariant");

  int v;

  // atomically decrement _event
  for (;;) {
    v = _event;
    if (Atomic::cmpxchg(v - 1, &_event, v) == v) break;
  }
  guarantee(v >= 0, "invariant");

  if (v == 0) { // Do this the hard way by blocking ...
    int status = pthread_mutex_lock(_mutex);
    assert_status(status == 0, status, "mutex_lock");
    guarantee(_nParked == 0, "invariant");
    ++_nParked;
    while (_event < 0) {
      // OS-level "spurious wakeups" are ignored
      status = pthread_cond_wait(_cond, _mutex);
      assert_status(status == 0, status, "cond_wait");
    }
    --_nParked;

    _event = 0;
    status = pthread_mutex_unlock(_mutex);
    assert_status(status == 0, status, "mutex_unlock");
    // Paranoia to ensure our locked and lock-free paths interact
    // correctly with each other.
    OrderAccess::fence();
  }
  guarantee(_event >= 0, "invariant");
}

int os::PlatformEvent::park(jlong millis) {
  // Transitions for _event:
  //   -1 => -1 : illegal
  //    1 =>  0 : pass - return immediately
  //    0 => -1 : block; then set _event to 0 before returning

  // Invariant: Only the thread associated with the Event/PlatformEvent
  // may call park().
  assert(_nParked == 0, "invariant");

  int v;
  // atomically decrement _event
  for (;;) {
    v = _event;
    if (Atomic::cmpxchg(v - 1, &_event, v) == v) break;
  }
  guarantee(v >= 0, "invariant");

  if (v == 0) { // Do this the hard way by blocking ...
    struct timespec abst;
    to_abstime(&abst, millis_to_nanos(millis), false, false);

    int ret = OS_TIMEOUT;
    int status = pthread_mutex_lock(_mutex);
    assert_status(status == 0, status, "mutex_lock");
    guarantee(_nParked == 0, "invariant");
    ++_nParked;

    while (_event < 0) {
      status = pthread_cond_timedwait(_cond, _mutex, &abst);
      assert_status(status == 0 || status == ETIMEDOUT,
                    status, "cond_timedwait");
      // OS-level "spurious wakeups" are ignored unless the archaic
      // FilterSpuriousWakeups is set false. That flag should be obsoleted.
      if (!FilterSpuriousWakeups) break;
      if (status == ETIMEDOUT) break;
    }
    --_nParked;

    if (_event >= 0) {
      ret = OS_OK;
    }

    _event = 0;
    status = pthread_mutex_unlock(_mutex);
    assert_status(status == 0, status, "mutex_unlock");
    // Paranoia to ensure our locked and lock-free paths interact
    // correctly with each other.
    OrderAccess::fence();
    return ret;
  }
  return OS_OK;
}

void os::PlatformEvent::unpark() {
  // Transitions for _event:
  //    0 => 1 : just return
  //    1 => 1 : just return
  //   -1 => either 0 or 1; must signal target thread
  //         That is, we can safely transition _event from -1 to either
  //         0 or 1.
  // See also: "Semaphores in Plan 9" by Mullender & Cox
  //
  // Note: Forcing a transition from "-1" to "1" on an unpark() means
  // that it will take two back-to-back park() calls for the owning
  // thread to block. This has the benefit of forcing a spurious return
  // from the first park() call after an unpark() call which will help
  // shake out uses of park() and unpark() without checking state conditions
  // properly. This spurious return doesn't manifest itself in any user code
  // but only in the correctly written condition checking loops of ObjectMonitor,
  // Mutex/Monitor, Thread::muxAcquire and os::sleep

  if (Atomic::xchg(1, &_event) >= 0) return;

  int status = pthread_mutex_lock(_mutex);
  assert_status(status == 0, status, "mutex_lock");
  int anyWaiters = _nParked;
  assert(anyWaiters == 0 || anyWaiters == 1, "invariant");
  status = pthread_mutex_unlock(_mutex);
  assert_status(status == 0, status, "mutex_unlock");

  // Note that we signal() *after* dropping the lock for "immortal" Events.
  // This is safe and avoids a common class of futile wakeups.  In rare
  // circumstances this can cause a thread to return prematurely from
  // cond_{timed}wait() but the spurious wakeup is benign and the victim
  // will simply re-test the condition and re-park itself.
  // This provides particular benefit if the underlying platform does not
  // provide wait morphing.

  if (anyWaiters != 0) {
    status = pthread_cond_signal(_cond);
    assert_status(status == 0, status, "cond_signal");
  }
}

// JSR166 support

 os::PlatformParker::PlatformParker() {
  int status;
  status = pthread_cond_init(&_cond[REL_INDEX], _condAttr);
  assert_status(status == 0, status, "cond_init rel");
  status = pthread_cond_init(&_cond[ABS_INDEX], NULL);
  assert_status(status == 0, status, "cond_init abs");
  status = pthread_mutex_init(_mutex, _mutexAttr);
  assert_status(status == 0, status, "mutex_init");
  _cur_index = -1; // mark as unused
}

// Parker::park decrements count if > 0, else does a condvar wait.  Unpark
// sets count to 1 and signals condvar.  Only one thread ever waits
// on the condvar. Contention seen when trying to park implies that someone
// is unparking you, so don't wait. And spurious returns are fine, so there
// is no need to track notifications.

void Parker::park(bool isAbsolute, jlong time) {

  // Optional fast-path check:
  // Return immediately if a permit is available.
  // We depend on Atomic::xchg() having full barrier semantics
  // since we are doing a lock-free update to _counter.
  if (Atomic::xchg(0, &_counter) > 0) return;

  Thread* thread = Thread::current();
  assert(thread->is_Java_thread(), "Must be JavaThread");
  JavaThread *jt = (JavaThread *)thread;

  // Optional optimization -- avoid state transitions if there's
  // an interrupt pending.
  if (Thread::is_interrupted(thread, false)) {
    return;
  }

  // Next, demultiplex/decode time arguments
  struct timespec absTime;
  if (time < 0 || (isAbsolute && time == 0)) { // don't wait at all
    return;
  }
  if (time > 0) {
    to_abstime(&absTime, time, isAbsolute, false);
  }

  // Enter safepoint region
  // Beware of deadlocks such as 6317397.
  // The per-thread Parker:: mutex is a classic leaf-lock.
  // In particular a thread must never block on the Threads_lock while
  // holding the Parker:: mutex.  If safepoints are pending both the
  // the ThreadBlockInVM() CTOR and DTOR may grab Threads_lock.
  ThreadBlockInVM tbivm(jt);

  // Don't wait if cannot get lock since interference arises from
  // unparking. Also re-check interrupt before trying wait.
  if (Thread::is_interrupted(thread, false) ||
      pthread_mutex_trylock(_mutex) != 0) {
    return;
  }

  int status;
  if (_counter > 0)  { // no wait needed
    _counter = 0;
    status = pthread_mutex_unlock(_mutex);
    assert_status(status == 0, status, "invariant");
    // Paranoia to ensure our locked and lock-free paths interact
    // correctly with each other and Java-level accesses.
    OrderAccess::fence();
    return;
  }

  OSThreadWaitState osts(thread->osthread(), false /* not Object.wait() */);
  jt->set_suspend_equivalent();
  // cleared by handle_special_suspend_equivalent_condition() or java_suspend_self()

  assert(_cur_index == -1, "invariant");
  if (time == 0) {
    _cur_index = REL_INDEX; // arbitrary choice when not timed
    status = pthread_cond_wait(&_cond[_cur_index], _mutex);
    assert_status(status == 0, status, "cond_timedwait");
  }
  else {
    _cur_index = isAbsolute ? ABS_INDEX : REL_INDEX;
    status = pthread_cond_timedwait(&_cond[_cur_index], _mutex, &absTime);
    assert_status(status == 0 || status == ETIMEDOUT,
                  status, "cond_timedwait");
  }
  _cur_index = -1;

  _counter = 0;
  status = pthread_mutex_unlock(_mutex);
  assert_status(status == 0, status, "invariant");
  // Paranoia to ensure our locked and lock-free paths interact
  // correctly with each other and Java-level accesses.
  OrderAccess::fence();

  // If externally suspended while waiting, re-suspend
  if (jt->handle_special_suspend_equivalent_condition()) {
    jt->java_suspend_self();
  }
}

void Parker::unpark() {
  int status = pthread_mutex_lock(_mutex);
  assert_status(status == 0, status, "invariant");
  const int s = _counter;
  _counter = 1;
  // must capture correct index before unlocking
  int index = _cur_index;
  status = pthread_mutex_unlock(_mutex);
  assert_status(status == 0, status, "invariant");

  // Note that we signal() *after* dropping the lock for "immortal" Events.
  // This is safe and avoids a common class of futile wakeups.  In rare
  // circumstances this can cause a thread to return prematurely from
  // cond_{timed}wait() but the spurious wakeup is benign and the victim
  // will simply re-test the condition and re-park itself.
  // This provides particular benefit if the underlying platform does not
  // provide wait morphing.

  if (s < 1 && index != -1) {
    // thread is definitely parked
    status = pthread_cond_signal(&_cond[index]);
    assert_status(status == 0, status, "invariant");
  }
}

// Platform Monitor implementation

os::PlatformMonitor::Impl::Impl() : _next(NULL) {
  int status = pthread_cond_init(&_cond, _condAttr);
  assert_status(status == 0, status, "cond_init");
  status = pthread_mutex_init(&_mutex, _mutexAttr);
  assert_status(status == 0, status, "mutex_init");
}

os::PlatformMonitor::Impl::~Impl() {
  int status = pthread_cond_destroy(&_cond);
  assert_status(status == 0, status, "cond_destroy");
  status = pthread_mutex_destroy(&_mutex);
  assert_status(status == 0, status, "mutex_destroy");
}

#if PLATFORM_MONITOR_IMPL_INDIRECT

pthread_mutex_t os::PlatformMonitor::_freelist_lock;
os::PlatformMonitor::Impl* os::PlatformMonitor::_freelist = NULL;

void os::PlatformMonitor::init() {
  int status = pthread_mutex_init(&_freelist_lock, _mutexAttr);
  assert_status(status == 0, status, "freelist lock init");
}

struct os::PlatformMonitor::WithFreeListLocked : public StackObj {
  WithFreeListLocked() {
    int status = pthread_mutex_lock(&_freelist_lock);
    assert_status(status == 0, status, "freelist lock");
  }

  ~WithFreeListLocked() {
    int status = pthread_mutex_unlock(&_freelist_lock);
    assert_status(status == 0, status, "freelist unlock");
  }
};

os::PlatformMonitor::PlatformMonitor() {
  {
    WithFreeListLocked wfl;
    _impl = _freelist;
    if (_impl != NULL) {
      _freelist = _impl->_next;
      _impl->_next = NULL;
      return;
    }
  }
  _impl = new Impl();
}

os::PlatformMonitor::~PlatformMonitor() {
  WithFreeListLocked wfl;
  assert(_impl->_next == NULL, "invariant");
  _impl->_next = _freelist;
  _freelist = _impl;
}

#endif // PLATFORM_MONITOR_IMPL_INDIRECT

// Must already be locked
int os::PlatformMonitor::wait(jlong millis) {
  assert(millis >= 0, "negative timeout");
  if (millis > 0) {
    struct timespec abst;
    // We have to watch for overflow when converting millis to nanos,
    // but if millis is that large then we will end up limiting to
    // MAX_SECS anyway, so just do that here.
    if (millis / MILLIUNITS > MAX_SECS) {
      millis = jlong(MAX_SECS) * MILLIUNITS;
    }
    to_abstime(&abst, millis * (NANOUNITS / MILLIUNITS), false, false);

    int ret = OS_TIMEOUT;
    int status = pthread_cond_timedwait(cond(), mutex(), &abst);
    assert_status(status == 0 || status == ETIMEDOUT,
                  status, "cond_timedwait");
    if (status == 0) {
      ret = OS_OK;
    }
    return ret;
  } else {
    int status = pthread_cond_wait(cond(), mutex());
    assert_status(status == 0, status, "cond_wait");
    return OS_OK;
  }
}

#endif // !SOLARIS
