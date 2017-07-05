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

inline const char* os::file_separator() { return "/"; }
inline const char* os::line_separator() { return "\n"; }
inline const char* os::path_separator() { return ":"; }

inline const char* os::jlong_format_specifier()   { return "%lld"; }
inline const char* os::julong_format_specifier()  { return "%llu"; }

// File names are case-sensitive on windows only
inline int os::file_name_strcmp(const char* s1, const char* s2) {
  return strcmp(s1, s2);
}

inline bool os::uses_stack_guard_pages() {
  return true;
}

inline bool os::allocate_stack_guard_pages() {
  assert(uses_stack_guard_pages(), "sanity check");
  int r = thr_main() ;
  guarantee (r == 0 || r == 1, "CR6501650 or CR6493689") ;
  return r;
}


// On Solaris, reservations are made on a page by page basis, nothing to do.
inline void os::split_reserved_memory(char *base, size_t size,
                                      size_t split, bool realloc) {
}


// Bang the shadow pages if they need to be touched to be mapped.
inline void os::bang_stack_shadow_pages() {
}

inline DIR* os::opendir(const char* dirname)
{
  assert(dirname != NULL, "just checking");
  return ::opendir(dirname);
}

inline int os::readdir_buf_size(const char *path)
{
  int size = pathconf(path, _PC_NAME_MAX);
  return (size < 0 ? MAXPATHLEN : size) + sizeof(dirent) + 1;
}

inline struct dirent* os::readdir(DIR* dirp, dirent* dbuf)
{
  assert(dirp != NULL, "just checking");
#if defined(_LP64) || defined(_GNU_SOURCE)
  dirent* p;
  int status;

  if((status = ::readdir_r(dirp, dbuf, &p)) != 0) {
    errno = status;
    return NULL;
  } else
    return p;
#else  // defined(_LP64) || defined(_GNU_SOURCE)
  return ::readdir_r(dirp, dbuf);
#endif // defined(_LP64) || defined(_GNU_SOURCE)
}

inline int os::closedir(DIR *dirp)
{
  assert(dirp != NULL, "just checking");
  return ::closedir(dirp);
}

//////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////

// macros for interruptible io and system calls and system call restarting

#define _INTERRUPTIBLE(_setup, _cmd, _result, _thread, _clear, _before, _after, _int_enable) \
do { \
  _setup; \
  _before; \
  OSThread* _osthread = _thread->osthread(); \
  if (_int_enable && _thread->has_last_Java_frame()) { \
    /* this is java interruptible io stuff */ \
    if (os::is_interrupted(_thread, _clear))  { \
      os::Solaris::bump_interrupted_before_count(); \
      _result = OS_INTRPT; \
    } else { \
      /* _cmd always expands to an assignment to _result */ \
      if ((_cmd) < 0 && errno == EINTR  \
       && os::is_interrupted(_thread, _clear)) { \
        os::Solaris::bump_interrupted_during_count(); \
        _result = OS_INTRPT; \
      } \
    } \
  } else { \
    /* this is normal blocking io stuff */ \
    _cmd; \
  } \
  _after; \
} while(false)

// Interruptible io support + restarting of interrupted system calls

#ifndef ASSERT

#define INTERRUPTIBLE(_cmd, _result, _clear) do { \
  _INTERRUPTIBLE( JavaThread* _thread = (JavaThread*)ThreadLocalStorage::thread(),_result = _cmd, _result, _thread, _clear, , , UseVMInterruptibleIO); \
} while((_result == OS_ERR) && (errno == EINTR))

#else

// This adds an assertion that it is only called from thread_in_native
// The call overhead is skipped for performance in product mode
#define INTERRUPTIBLE(_cmd, _result, _clear) do { \
  _INTERRUPTIBLE(JavaThread* _thread = os::Solaris::setup_interruptible_native(), _result = _cmd, _result, _thread, _clear, , os::Solaris::cleanup_interruptible_native(_thread), UseVMInterruptibleIO ); \
} while((_result == OS_ERR) && (errno == EINTR))

#endif

// Used for calls from _thread_in_vm, not from _thread_in_native
#define INTERRUPTIBLE_VM(_cmd, _result, _clear) do { \
  _INTERRUPTIBLE(JavaThread* _thread = os::Solaris::setup_interruptible(), _result = _cmd, _result, _thread, _clear, , os::Solaris::cleanup_interruptible(_thread), UseVMInterruptibleIO ); \
} while((_result == OS_ERR) && (errno == EINTR))

/* Use NORESTART when the system call cannot return EINTR, when something other
   than a system call is being invoked, or when the caller must do EINTR
   handling. */

#ifndef ASSERT

#define INTERRUPTIBLE_NORESTART(_cmd, _result, _clear) \
  _INTERRUPTIBLE( JavaThread* _thread = (JavaThread*)ThreadLocalStorage::thread(),_result = _cmd, _result, _thread, _clear, , , UseVMInterruptibleIO)

#else

// This adds an assertion that it is only called from thread_in_native
// The call overhead is skipped for performance in product mode
#define INTERRUPTIBLE_NORESTART(_cmd, _result, _clear) \
  _INTERRUPTIBLE(JavaThread* _thread = os::Solaris::setup_interruptible_native(), _result = _cmd, _result, _thread, _clear, , os::Solaris::cleanup_interruptible_native(_thread), UseVMInterruptibleIO )

#endif

// Don't attend to UseVMInterruptibleIO. Always allow interruption.
// Also assumes that it is called from the _thread_blocked state.
// Used by os_sleep().

#define INTERRUPTIBLE_NORESTART_VM_ALWAYS(_cmd, _result, _thread, _clear) \
  _INTERRUPTIBLE(os::Solaris::setup_interruptible_already_blocked(_thread), _result = _cmd, _result, _thread, _clear, , , true )

#define INTERRUPTIBLE_RETURN_INT(_cmd, _clear) do { \
  int _result; \
  do { \
    INTERRUPTIBLE(_cmd, _result, _clear); \
  } while((_result == OS_ERR) && (errno == EINTR)); \
  return _result; \
} while(false)

#define INTERRUPTIBLE_RETURN_INT_VM(_cmd, _clear) do { \
  int _result; \
  do { \
    INTERRUPTIBLE_VM(_cmd, _result, _clear); \
  } while((_result == OS_ERR) && (errno == EINTR)); \
  return _result; \
} while(false)

#define INTERRUPTIBLE_RETURN_INT_NORESTART(_cmd, _clear) do { \
  int _result; \
  INTERRUPTIBLE_NORESTART(_cmd, _result, _clear); \
  return _result; \
} while(false)

/* Use the RESTARTABLE macros when interruptible io is not needed */

#define RESTARTABLE(_cmd, _result) do { \
  do { \
    _result = _cmd; \
  } while((_result == OS_ERR) && (errno == EINTR)); \
} while(false)

#define RESTARTABLE_RETURN_INT(_cmd) do { \
  int _result; \
  RESTARTABLE(_cmd, _result); \
  return _result; \
} while(false)

inline bool os::numa_has_static_binding()   { return false; }
inline bool os::numa_has_group_homing()     { return true;  }
