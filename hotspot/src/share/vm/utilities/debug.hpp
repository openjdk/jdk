/*
 * Copyright 1997-2007 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 *
 */

// assertions
#ifdef ASSERT
// Turn this off by default:
//#define USE_REPEATED_ASSERTS
#ifdef USE_REPEATED_ASSERTS
  #define assert(p,msg)                                              \
    { for (int __i = 0; __i < AssertRepeat; __i++) {                 \
        if (!(p)) {                                                  \
          report_assertion_failure(__FILE__, __LINE__,               \
                                  "assert(" XSTR(p) ",\"" msg "\")");\
          BREAKPOINT;                                                \
        }                                                            \
      }                                                              \
    }
#else
  #define assert(p,msg)                                          \
    if (!(p)) {                                                  \
      report_assertion_failure(__FILE__, __LINE__,               \
                              "assert(" XSTR(p) ",\"" msg "\")");\
      BREAKPOINT;                                                \
    }
#endif

// This version of assert is for use with checking return status from
// library calls that return actual error values eg. EINVAL,
// ENOMEM etc, rather than returning -1 and setting errno.
// When the status is not what is expected it is very useful to know
// what status was actually returned, so we pass the status variable as
// an extra arg and use strerror to convert it to a meaningful string
// like "Invalid argument", "out of memory" etc
#define assert_status(p, status, msg)                                     \
   do {                                                                   \
    if (!(p)) {                                                           \
      char buf[128];                                                      \
      snprintf(buf, 127,                                                  \
               "assert_status(" XSTR(p) ", error: %s(%d), \"" msg "\")" , \
               strerror((status)), (status));                             \
      report_assertion_failure(__FILE__, __LINE__, buf);                  \
      BREAKPOINT;                                                         \
    }                                                                     \
  } while (0)

// Another version of assert where the message is not a string literal
// The boolean condition is not printed out because cpp doesn't like it.
#define assert_msg(p, msg)                                       \
    if (!(p)) {                                                  \
      report_assertion_failure(__FILE__, __LINE__, msg);         \
      BREAKPOINT;                                                \
    }

// Do not assert this condition if there's already another error reported.
#define assert_if_no_error(cond,msg) assert((cond) || is_error_reported(), msg)
#else
  #define assert(p,msg)
  #define assert_status(p,status,msg)
  #define assert_if_no_error(cond,msg)
  #define assert_msg(cond,msg)
#endif


// fatals
#define fatal(m)                             { report_fatal(__FILE__, __LINE__, m                          ); BREAKPOINT; }
#define fatal1(m,x1)                         { report_fatal_vararg(__FILE__, __LINE__, m, x1               ); BREAKPOINT; }
#define fatal2(m,x1,x2)                      { report_fatal_vararg(__FILE__, __LINE__, m, x1, x2           ); BREAKPOINT; }
#define fatal3(m,x1,x2,x3)                   { report_fatal_vararg(__FILE__, __LINE__, m, x1, x2, x3       ); BREAKPOINT; }
#define fatal4(m,x1,x2,x3,x4)                { report_fatal_vararg(__FILE__, __LINE__, m, x1, x2, x3, x4   ); BREAKPOINT; }

// out of memory
#define vm_exit_out_of_memory(s,m)              { report_vm_out_of_memory(__FILE__, __LINE__, s, m                       ); BREAKPOINT; }
#define vm_exit_out_of_memory1(s,m,x1)          { report_vm_out_of_memory_vararg(__FILE__, __LINE__, s, m, x1            ); BREAKPOINT; }
#define vm_exit_out_of_memory2(s,m,x1,x2)       { report_vm_out_of_memory_vararg(__FILE__, __LINE__, s, m, x1, x2        ); BREAKPOINT; }
#define vm_exit_out_of_memory3(s,m,x1,x2,x3)    { report_vm_out_of_memory_vararg(__FILE__, __LINE__, s, m, x1, x2, x3    ); BREAKPOINT; }
#define vm_exit_out_of_memory4(s,m,x1,x2,x3,x4) { report_vm_out_of_memory_vararg(__FILE__, __LINE__, s, m, x1, x2, x3, x4); BREAKPOINT; }

// guarantee is like assert except it's always executed -- use it for
// cheap tests that catch errors that would otherwise be hard to find
// guarantee is also used for Verify options.
#define guarantee(b,msg)         { if (!(b)) fatal("guarantee(" XSTR(b) ",\"" msg "\")"); }

#define ShouldNotCallThis()      { report_should_not_call        (__FILE__, __LINE__); BREAKPOINT; }
#define ShouldNotReachHere()     { report_should_not_reach_here  (__FILE__, __LINE__); BREAKPOINT; }
#define Unimplemented()          { report_unimplemented          (__FILE__, __LINE__); BREAKPOINT; }
#define Untested(msg)            { report_untested               (__FILE__, __LINE__, msg); BREAKPOINT; }

// error reporting helper functions
void report_assertion_failure(const char* file_name, int line_no, const char* message);
void report_fatal_vararg(const char* file_name, int line_no, const char* format, ...);
void report_fatal(const char* file_name, int line_no, const char* message);
void report_vm_out_of_memory_vararg(const char* file_name, int line_no, size_t size, const char* format, ...);
void report_vm_out_of_memory(const char* file_name, int line_no, size_t size, const char* message);
void report_should_not_call(const char* file_name, int line_no);
void report_should_not_reach_here(const char* file_name, int line_no);
void report_unimplemented(const char* file_name, int line_no);
void report_untested(const char* file_name, int line_no, const char* msg);
void warning(const char* format, ...);

// out of memory reporting
void report_java_out_of_memory(const char* message);

// Support for self-destruct
bool is_error_reported();
void set_error_reported();

void pd_ps(frame f);
void pd_obfuscate_location(char *buf, size_t buflen);
