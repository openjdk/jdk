/*
 * Copyright 1997-2009 Sun Microsystems, Inc.  All Rights Reserved.
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

# include "incls/_precompiled.incl"
# include "incls/_os.cpp.incl"

# include <signal.h>

OSThread*         os::_starting_thread    = NULL;
address           os::_polling_page       = NULL;
volatile int32_t* os::_mem_serialize_page = NULL;
uintptr_t         os::_serialize_page_mask = 0;
long              os::_rand_seed          = 1;
int               os::_processor_count    = 0;
size_t            os::_page_sizes[os::page_sizes_max];

#ifndef PRODUCT
int os::num_mallocs = 0;            // # of calls to malloc/realloc
size_t os::alloc_bytes = 0;         // # of bytes allocated
int os::num_frees = 0;              // # of calls to free
#endif

// Fill in buffer with current local time as an ISO-8601 string.
// E.g., yyyy-mm-ddThh:mm:ss-zzzz.
// Returns buffer, or NULL if it failed.
// This would mostly be a call to
//     strftime(...., "%Y-%m-%d" "T" "%H:%M:%S" "%z", ....)
// except that on Windows the %z behaves badly, so we do it ourselves.
// Also, people wanted milliseconds on there,
// and strftime doesn't do milliseconds.
char* os::iso8601_time(char* buffer, size_t buffer_length) {
  // Output will be of the form "YYYY-MM-DDThh:mm:ss.mmm+zzzz\0"
  //                                      1         2
  //                             12345678901234567890123456789
  static const char* iso8601_format =
    "%04d-%02d-%02dT%02d:%02d:%02d.%03d%c%02d%02d";
  static const size_t needed_buffer = 29;

  // Sanity check the arguments
  if (buffer == NULL) {
    assert(false, "NULL buffer");
    return NULL;
  }
  if (buffer_length < needed_buffer) {
    assert(false, "buffer_length too small");
    return NULL;
  }
  // Get the current time
  jlong milliseconds_since_19700101 = javaTimeMillis();
  const int milliseconds_per_microsecond = 1000;
  const time_t seconds_since_19700101 =
    milliseconds_since_19700101 / milliseconds_per_microsecond;
  const int milliseconds_after_second =
    milliseconds_since_19700101 % milliseconds_per_microsecond;
  // Convert the time value to a tm and timezone variable
  struct tm time_struct;
  if (localtime_pd(&seconds_since_19700101, &time_struct) == NULL) {
    assert(false, "Failed localtime_pd");
    return NULL;
  }
  const time_t zone = timezone;

  // If daylight savings time is in effect,
  // we are 1 hour East of our time zone
  const time_t seconds_per_minute = 60;
  const time_t minutes_per_hour = 60;
  const time_t seconds_per_hour = seconds_per_minute * minutes_per_hour;
  time_t UTC_to_local = zone;
  if (time_struct.tm_isdst > 0) {
    UTC_to_local = UTC_to_local - seconds_per_hour;
  }
  // Compute the time zone offset.
  //    localtime_pd() sets timezone to the difference (in seconds)
  //    between UTC and and local time.
  //    ISO 8601 says we need the difference between local time and UTC,
  //    we change the sign of the localtime_pd() result.
  const time_t local_to_UTC = -(UTC_to_local);
  // Then we have to figure out if if we are ahead (+) or behind (-) UTC.
  char sign_local_to_UTC = '+';
  time_t abs_local_to_UTC = local_to_UTC;
  if (local_to_UTC < 0) {
    sign_local_to_UTC = '-';
    abs_local_to_UTC = -(abs_local_to_UTC);
  }
  // Convert time zone offset seconds to hours and minutes.
  const time_t zone_hours = (abs_local_to_UTC / seconds_per_hour);
  const time_t zone_min =
    ((abs_local_to_UTC % seconds_per_hour) / seconds_per_minute);

  // Print an ISO 8601 date and time stamp into the buffer
  const int year = 1900 + time_struct.tm_year;
  const int month = 1 + time_struct.tm_mon;
  const int printed = jio_snprintf(buffer, buffer_length, iso8601_format,
                                   year,
                                   month,
                                   time_struct.tm_mday,
                                   time_struct.tm_hour,
                                   time_struct.tm_min,
                                   time_struct.tm_sec,
                                   milliseconds_after_second,
                                   sign_local_to_UTC,
                                   zone_hours,
                                   zone_min);
  if (printed == 0) {
    assert(false, "Failed jio_printf");
    return NULL;
  }
  return buffer;
}

OSReturn os::set_priority(Thread* thread, ThreadPriority p) {
#ifdef ASSERT
  if (!(!thread->is_Java_thread() ||
         Thread::current() == thread  ||
         Threads_lock->owned_by_self()
         || thread->is_Compiler_thread()
        )) {
    assert(false, "possibility of dangling Thread pointer");
  }
#endif

  if (p >= MinPriority && p <= MaxPriority) {
    int priority = java_to_os_priority[p];
    return set_native_priority(thread, priority);
  } else {
    assert(false, "Should not happen");
    return OS_ERR;
  }
}


OSReturn os::get_priority(const Thread* const thread, ThreadPriority& priority) {
  int p;
  int os_prio;
  OSReturn ret = get_native_priority(thread, &os_prio);
  if (ret != OS_OK) return ret;

  for (p = MaxPriority; p > MinPriority && java_to_os_priority[p] > os_prio; p--) ;
  priority = (ThreadPriority)p;
  return OS_OK;
}


// --------------------- sun.misc.Signal (optional) ---------------------


// SIGBREAK is sent by the keyboard to query the VM state
#ifndef SIGBREAK
#define SIGBREAK SIGQUIT
#endif

// sigexitnum_pd is a platform-specific special signal used for terminating the Signal thread.


static void signal_thread_entry(JavaThread* thread, TRAPS) {
  os::set_priority(thread, NearMaxPriority);
  while (true) {
    int sig;
    {
      // FIXME : Currently we have not decieded what should be the status
      //         for this java thread blocked here. Once we decide about
      //         that we should fix this.
      sig = os::signal_wait();
    }
    if (sig == os::sigexitnum_pd()) {
       // Terminate the signal thread
       return;
    }

    switch (sig) {
      case SIGBREAK: {
        // Check if the signal is a trigger to start the Attach Listener - in that
        // case don't print stack traces.
        if (!DisableAttachMechanism && AttachListener::is_init_trigger()) {
          continue;
        }
        // Print stack traces
        // Any SIGBREAK operations added here should make sure to flush
        // the output stream (e.g. tty->flush()) after output.  See 4803766.
        // Each module also prints an extra carriage return after its output.
        VM_PrintThreads op;
        VMThread::execute(&op);
        VM_PrintJNI jni_op;
        VMThread::execute(&jni_op);
        VM_FindDeadlocks op1(tty);
        VMThread::execute(&op1);
        Universe::print_heap_at_SIGBREAK();
        if (PrintClassHistogram) {
          VM_GC_HeapInspection op1(gclog_or_tty, true /* force full GC before heap inspection */,
                                   true /* need_prologue */);
          VMThread::execute(&op1);
        }
        if (JvmtiExport::should_post_data_dump()) {
          JvmtiExport::post_data_dump();
        }
        break;
      }
      default: {
        // Dispatch the signal to java
        HandleMark hm(THREAD);
        klassOop k = SystemDictionary::resolve_or_null(vmSymbolHandles::sun_misc_Signal(), THREAD);
        KlassHandle klass (THREAD, k);
        if (klass.not_null()) {
          JavaValue result(T_VOID);
          JavaCallArguments args;
          args.push_int(sig);
          JavaCalls::call_static(
            &result,
            klass,
            vmSymbolHandles::dispatch_name(),
            vmSymbolHandles::int_void_signature(),
            &args,
            THREAD
          );
        }
        if (HAS_PENDING_EXCEPTION) {
          // tty is initialized early so we don't expect it to be null, but
          // if it is we can't risk doing an initialization that might
          // trigger additional out-of-memory conditions
          if (tty != NULL) {
            char klass_name[256];
            char tmp_sig_name[16];
            const char* sig_name = "UNKNOWN";
            instanceKlass::cast(PENDING_EXCEPTION->klass())->
              name()->as_klass_external_name(klass_name, 256);
            if (os::exception_name(sig, tmp_sig_name, 16) != NULL)
              sig_name = tmp_sig_name;
            warning("Exception %s occurred dispatching signal %s to handler"
                    "- the VM may need to be forcibly terminated",
                    klass_name, sig_name );
          }
          CLEAR_PENDING_EXCEPTION;
        }
      }
    }
  }
}


void os::signal_init() {
  if (!ReduceSignalUsage) {
    // Setup JavaThread for processing signals
    EXCEPTION_MARK;
    klassOop k = SystemDictionary::resolve_or_fail(vmSymbolHandles::java_lang_Thread(), true, CHECK);
    instanceKlassHandle klass (THREAD, k);
    instanceHandle thread_oop = klass->allocate_instance_handle(CHECK);

    const char thread_name[] = "Signal Dispatcher";
    Handle string = java_lang_String::create_from_str(thread_name, CHECK);

    // Initialize thread_oop to put it into the system threadGroup
    Handle thread_group (THREAD, Universe::system_thread_group());
    JavaValue result(T_VOID);
    JavaCalls::call_special(&result, thread_oop,
                           klass,
                           vmSymbolHandles::object_initializer_name(),
                           vmSymbolHandles::threadgroup_string_void_signature(),
                           thread_group,
                           string,
                           CHECK);

    KlassHandle group(THREAD, SystemDictionary::ThreadGroup_klass());
    JavaCalls::call_special(&result,
                            thread_group,
                            group,
                            vmSymbolHandles::add_method_name(),
                            vmSymbolHandles::thread_void_signature(),
                            thread_oop,         // ARG 1
                            CHECK);

    os::signal_init_pd();

    { MutexLocker mu(Threads_lock);
      JavaThread* signal_thread = new JavaThread(&signal_thread_entry);

      // At this point it may be possible that no osthread was created for the
      // JavaThread due to lack of memory. We would have to throw an exception
      // in that case. However, since this must work and we do not allow
      // exceptions anyway, check and abort if this fails.
      if (signal_thread == NULL || signal_thread->osthread() == NULL) {
        vm_exit_during_initialization("java.lang.OutOfMemoryError",
                                      "unable to create new native thread");
      }

      java_lang_Thread::set_thread(thread_oop(), signal_thread);
      java_lang_Thread::set_priority(thread_oop(), NearMaxPriority);
      java_lang_Thread::set_daemon(thread_oop());

      signal_thread->set_threadObj(thread_oop());
      Threads::add(signal_thread);
      Thread::start(signal_thread);
    }
    // Handle ^BREAK
    os::signal(SIGBREAK, os::user_handler());
  }
}


void os::terminate_signal_thread() {
  if (!ReduceSignalUsage)
    signal_notify(sigexitnum_pd());
}


// --------------------- loading libraries ---------------------

typedef jint (JNICALL *JNI_OnLoad_t)(JavaVM *, void *);
extern struct JavaVM_ main_vm;

static void* _native_java_library = NULL;

void* os::native_java_library() {
  if (_native_java_library == NULL) {
    char buffer[JVM_MAXPATHLEN];
    char ebuf[1024];

    // Try to load verify dll first. In 1.3 java dll depends on it and is not
    // always able to find it when the loading executable is outside the JDK.
    // In order to keep working with 1.2 we ignore any loading errors.
    dll_build_name(buffer, sizeof(buffer), Arguments::get_dll_dir(), "verify");
    dll_load(buffer, ebuf, sizeof(ebuf));

    // Load java dll
    dll_build_name(buffer, sizeof(buffer), Arguments::get_dll_dir(), "java");
    _native_java_library = dll_load(buffer, ebuf, sizeof(ebuf));
    if (_native_java_library == NULL) {
      vm_exit_during_initialization("Unable to load native library", ebuf);
    }
  }
  static jboolean onLoaded = JNI_FALSE;
  if (onLoaded) {
    // We may have to wait to fire OnLoad until TLS is initialized.
    if (ThreadLocalStorage::is_initialized()) {
      // The JNI_OnLoad handling is normally done by method load in
      // java.lang.ClassLoader$NativeLibrary, but the VM loads the base library
      // explicitly so we have to check for JNI_OnLoad as well
      const char *onLoadSymbols[] = JNI_ONLOAD_SYMBOLS;
      JNI_OnLoad_t JNI_OnLoad = CAST_TO_FN_PTR(
          JNI_OnLoad_t, dll_lookup(_native_java_library, onLoadSymbols[0]));
      if (JNI_OnLoad != NULL) {
        JavaThread* thread = JavaThread::current();
        ThreadToNativeFromVM ttn(thread);
        HandleMark hm(thread);
        jint ver = (*JNI_OnLoad)(&main_vm, NULL);
        onLoaded = JNI_TRUE;
        if (!Threads::is_supported_jni_version_including_1_1(ver)) {
          vm_exit_during_initialization("Unsupported JNI version");
        }
      }
    }
  }
  return _native_java_library;
}

// --------------------- heap allocation utilities ---------------------

char *os::strdup(const char *str) {
  size_t size = strlen(str);
  char *dup_str = (char *)malloc(size + 1);
  if (dup_str == NULL) return NULL;
  strcpy(dup_str, str);
  return dup_str;
}



#ifdef ASSERT
#define space_before             (MallocCushion + sizeof(double))
#define space_after              MallocCushion
#define size_addr_from_base(p)   (size_t*)(p + space_before - sizeof(size_t))
#define size_addr_from_obj(p)    ((size_t*)p - 1)
// MallocCushion: size of extra cushion allocated around objects with +UseMallocOnly
// NB: cannot be debug variable, because these aren't set from the command line until
// *after* the first few allocs already happened
#define MallocCushion            16
#else
#define space_before             0
#define space_after              0
#define size_addr_from_base(p)   should not use w/o ASSERT
#define size_addr_from_obj(p)    should not use w/o ASSERT
#define MallocCushion            0
#endif
#define paranoid                 0  /* only set to 1 if you suspect checking code has bug */

#ifdef ASSERT
inline size_t get_size(void* obj) {
  size_t size = *size_addr_from_obj(obj);
  if (size < 0) {
    fatal(err_msg("free: size field of object #" PTR_FORMAT " was overwritten ("
                  SIZE_FORMAT ")", obj, size));
  }
  return size;
}

u_char* find_cushion_backwards(u_char* start) {
  u_char* p = start;
  while (p[ 0] != badResourceValue || p[-1] != badResourceValue ||
         p[-2] != badResourceValue || p[-3] != badResourceValue) p--;
  // ok, we have four consecutive marker bytes; find start
  u_char* q = p - 4;
  while (*q == badResourceValue) q--;
  return q + 1;
}

u_char* find_cushion_forwards(u_char* start) {
  u_char* p = start;
  while (p[0] != badResourceValue || p[1] != badResourceValue ||
         p[2] != badResourceValue || p[3] != badResourceValue) p++;
  // ok, we have four consecutive marker bytes; find end of cushion
  u_char* q = p + 4;
  while (*q == badResourceValue) q++;
  return q - MallocCushion;
}

void print_neighbor_blocks(void* ptr) {
  // find block allocated before ptr (not entirely crash-proof)
  if (MallocCushion < 4) {
    tty->print_cr("### cannot find previous block (MallocCushion < 4)");
    return;
  }
  u_char* start_of_this_block = (u_char*)ptr - space_before;
  u_char* end_of_prev_block_data = start_of_this_block - space_after -1;
  // look for cushion in front of prev. block
  u_char* start_of_prev_block = find_cushion_backwards(end_of_prev_block_data);
  ptrdiff_t size = *size_addr_from_base(start_of_prev_block);
  u_char* obj = start_of_prev_block + space_before;
  if (size <= 0 ) {
    // start is bad; mayhave been confused by OS data inbetween objects
    // search one more backwards
    start_of_prev_block = find_cushion_backwards(start_of_prev_block);
    size = *size_addr_from_base(start_of_prev_block);
    obj = start_of_prev_block + space_before;
  }

  if (start_of_prev_block + space_before + size + space_after == start_of_this_block) {
    tty->print_cr("### previous object: %p (%ld bytes)", obj, size);
  } else {
    tty->print_cr("### previous object (not sure if correct): %p (%ld bytes)", obj, size);
  }

  // now find successor block
  u_char* start_of_next_block = (u_char*)ptr + *size_addr_from_obj(ptr) + space_after;
  start_of_next_block = find_cushion_forwards(start_of_next_block);
  u_char* next_obj = start_of_next_block + space_before;
  ptrdiff_t next_size = *size_addr_from_base(start_of_next_block);
  if (start_of_next_block[0] == badResourceValue &&
      start_of_next_block[1] == badResourceValue &&
      start_of_next_block[2] == badResourceValue &&
      start_of_next_block[3] == badResourceValue) {
    tty->print_cr("### next object: %p (%ld bytes)", next_obj, next_size);
  } else {
    tty->print_cr("### next object (not sure if correct): %p (%ld bytes)", next_obj, next_size);
  }
}


void report_heap_error(void* memblock, void* bad, const char* where) {
  tty->print_cr("## nof_mallocs = %d, nof_frees = %d", os::num_mallocs, os::num_frees);
  tty->print_cr("## memory stomp: byte at %p %s object %p", bad, where, memblock);
  print_neighbor_blocks(memblock);
  fatal("memory stomping error");
}

void verify_block(void* memblock) {
  size_t size = get_size(memblock);
  if (MallocCushion) {
    u_char* ptr = (u_char*)memblock - space_before;
    for (int i = 0; i < MallocCushion; i++) {
      if (ptr[i] != badResourceValue) {
        report_heap_error(memblock, ptr+i, "in front of");
      }
    }
    u_char* end = (u_char*)memblock + size + space_after;
    for (int j = -MallocCushion; j < 0; j++) {
      if (end[j] != badResourceValue) {
        report_heap_error(memblock, end+j, "after");
      }
    }
  }
}
#endif

void* os::malloc(size_t size) {
  NOT_PRODUCT(num_mallocs++);
  NOT_PRODUCT(alloc_bytes += size);

  if (size == 0) {
    // return a valid pointer if size is zero
    // if NULL is returned the calling functions assume out of memory.
    size = 1;
  }

  NOT_PRODUCT(if (MallocVerifyInterval > 0) check_heap());
  u_char* ptr = (u_char*)::malloc(size + space_before + space_after);
#ifdef ASSERT
  if (ptr == NULL) return NULL;
  if (MallocCushion) {
    for (u_char* p = ptr; p < ptr + MallocCushion; p++) *p = (u_char)badResourceValue;
    u_char* end = ptr + space_before + size;
    for (u_char* pq = ptr+MallocCushion; pq < end; pq++) *pq = (u_char)uninitBlockPad;
    for (u_char* q = end; q < end + MallocCushion; q++) *q = (u_char)badResourceValue;
  }
  // put size just before data
  *size_addr_from_base(ptr) = size;
#endif
  u_char* memblock = ptr + space_before;
  if ((intptr_t)memblock == (intptr_t)MallocCatchPtr) {
    tty->print_cr("os::malloc caught, %lu bytes --> %p", size, memblock);
    breakpoint();
  }
  debug_only(if (paranoid) verify_block(memblock));
  if (PrintMalloc && tty != NULL) tty->print_cr("os::malloc %lu bytes --> %p", size, memblock);
  return memblock;
}


void* os::realloc(void *memblock, size_t size) {
  NOT_PRODUCT(num_mallocs++);
  NOT_PRODUCT(alloc_bytes += size);
#ifndef ASSERT
  return ::realloc(memblock, size);
#else
  if (memblock == NULL) {
    return os::malloc(size);
  }
  if ((intptr_t)memblock == (intptr_t)MallocCatchPtr) {
    tty->print_cr("os::realloc caught %p", memblock);
    breakpoint();
  }
  verify_block(memblock);
  NOT_PRODUCT(if (MallocVerifyInterval > 0) check_heap());
  if (size == 0) return NULL;
  // always move the block
  void* ptr = malloc(size);
  if (PrintMalloc) tty->print_cr("os::remalloc %lu bytes, %p --> %p", size, memblock, ptr);
  // Copy to new memory if malloc didn't fail
  if ( ptr != NULL ) {
    memcpy(ptr, memblock, MIN2(size, get_size(memblock)));
    if (paranoid) verify_block(ptr);
    if ((intptr_t)ptr == (intptr_t)MallocCatchPtr) {
      tty->print_cr("os::realloc caught, %lu bytes --> %p", size, ptr);
      breakpoint();
    }
    free(memblock);
  }
  return ptr;
#endif
}


void  os::free(void *memblock) {
  NOT_PRODUCT(num_frees++);
#ifdef ASSERT
  if (memblock == NULL) return;
  if ((intptr_t)memblock == (intptr_t)MallocCatchPtr) {
    if (tty != NULL) tty->print_cr("os::free caught %p", memblock);
    breakpoint();
  }
  verify_block(memblock);
  if (PrintMalloc && tty != NULL)
    // tty->print_cr("os::free %p", memblock);
    fprintf(stderr, "os::free %p\n", memblock);
  NOT_PRODUCT(if (MallocVerifyInterval > 0) check_heap());
  // Added by detlefs.
  if (MallocCushion) {
    u_char* ptr = (u_char*)memblock - space_before;
    for (u_char* p = ptr; p < ptr + MallocCushion; p++) {
      guarantee(*p == badResourceValue,
                "Thing freed should be malloc result.");
      *p = (u_char)freeBlockPad;
    }
    size_t size = get_size(memblock);
    u_char* end = ptr + space_before + size;
    for (u_char* q = end; q < end + MallocCushion; q++) {
      guarantee(*q == badResourceValue,
                "Thing freed should be malloc result.");
      *q = (u_char)freeBlockPad;
    }
  }
#endif
  ::free((char*)memblock - space_before);
}

void os::init_random(long initval) {
  _rand_seed = initval;
}


long os::random() {
  /* standard, well-known linear congruential random generator with
   * next_rand = (16807*seed) mod (2**31-1)
   * see
   * (1) "Random Number Generators: Good Ones Are Hard to Find",
   *      S.K. Park and K.W. Miller, Communications of the ACM 31:10 (Oct 1988),
   * (2) "Two Fast Implementations of the 'Minimal Standard' Random
   *     Number Generator", David G. Carta, Comm. ACM 33, 1 (Jan 1990), pp. 87-88.
  */
  const long a = 16807;
  const unsigned long m = 2147483647;
  const long q = m / a;        assert(q == 127773, "weird math");
  const long r = m % a;        assert(r == 2836, "weird math");

  // compute az=2^31p+q
  unsigned long lo = a * (long)(_rand_seed & 0xFFFF);
  unsigned long hi = a * (long)((unsigned long)_rand_seed >> 16);
  lo += (hi & 0x7FFF) << 16;

  // if q overflowed, ignore the overflow and increment q
  if (lo > m) {
    lo &= m;
    ++lo;
  }
  lo += hi >> 15;

  // if (p+q) overflowed, ignore the overflow and increment (p+q)
  if (lo > m) {
    lo &= m;
    ++lo;
  }
  return (_rand_seed = lo);
}

// The INITIALIZED state is distinguished from the SUSPENDED state because the
// conditions in which a thread is first started are different from those in which
// a suspension is resumed.  These differences make it hard for us to apply the
// tougher checks when starting threads that we want to do when resuming them.
// However, when start_thread is called as a result of Thread.start, on a Java
// thread, the operation is synchronized on the Java Thread object.  So there
// cannot be a race to start the thread and hence for the thread to exit while
// we are working on it.  Non-Java threads that start Java threads either have
// to do so in a context in which races are impossible, or should do appropriate
// locking.

void os::start_thread(Thread* thread) {
  // guard suspend/resume
  MutexLockerEx ml(thread->SR_lock(), Mutex::_no_safepoint_check_flag);
  OSThread* osthread = thread->osthread();
  osthread->set_state(RUNNABLE);
  pd_start_thread(thread);
}

//---------------------------------------------------------------------------
// Helper functions for fatal error handler

void os::print_hex_dump(outputStream* st, address start, address end, int unitsize) {
  assert(unitsize == 1 || unitsize == 2 || unitsize == 4 || unitsize == 8, "just checking");

  int cols = 0;
  int cols_per_line = 0;
  switch (unitsize) {
    case 1: cols_per_line = 16; break;
    case 2: cols_per_line = 8;  break;
    case 4: cols_per_line = 4;  break;
    case 8: cols_per_line = 2;  break;
    default: return;
  }

  address p = start;
  st->print(PTR_FORMAT ":   ", start);
  while (p < end) {
    switch (unitsize) {
      case 1: st->print("%02x", *(u1*)p); break;
      case 2: st->print("%04x", *(u2*)p); break;
      case 4: st->print("%08x", *(u4*)p); break;
      case 8: st->print("%016" FORMAT64_MODIFIER "x", *(u8*)p); break;
    }
    p += unitsize;
    cols++;
    if (cols >= cols_per_line && p < end) {
       cols = 0;
       st->cr();
       st->print(PTR_FORMAT ":   ", p);
    } else {
       st->print(" ");
    }
  }
  st->cr();
}

void os::print_environment_variables(outputStream* st, const char** env_list,
                                     char* buffer, int len) {
  if (env_list) {
    st->print_cr("Environment Variables:");

    for (int i = 0; env_list[i] != NULL; i++) {
      if (getenv(env_list[i], buffer, len)) {
        st->print(env_list[i]);
        st->print("=");
        st->print_cr(buffer);
      }
    }
  }
}

void os::print_cpu_info(outputStream* st) {
  // cpu
  st->print("CPU:");
  st->print("total %d", os::processor_count());
  // It's not safe to query number of active processors after crash
  // st->print("(active %d)", os::active_processor_count());
  st->print(" %s", VM_Version::cpu_features());
  st->cr();
}

void os::print_date_and_time(outputStream *st) {
  time_t tloc;
  (void)time(&tloc);
  st->print("time: %s", ctime(&tloc));  // ctime adds newline.

  double t = os::elapsedTime();
  // NOTE: It tends to crash after a SEGV if we want to printf("%f",...) in
  //       Linux. Must be a bug in glibc ? Workaround is to round "t" to int
  //       before printf. We lost some precision, but who cares?
  st->print_cr("elapsed time: %d seconds", (int)t);
}


// Looks like all platforms except IA64 can use the same function to check
// if C stack is walkable beyond current frame. The check for fp() is not
// necessary on Sparc, but it's harmless.
bool os::is_first_C_frame(frame* fr) {
#ifdef IA64
  // In order to walk native frames on Itanium, we need to access the unwind
  // table, which is inside ELF. We don't want to parse ELF after fatal error,
  // so return true for IA64. If we need to support C stack walking on IA64,
  // this function needs to be moved to CPU specific files, as fp() on IA64
  // is register stack, which grows towards higher memory address.
  return true;
#endif

  // Load up sp, fp, sender sp and sender fp, check for reasonable values.
  // Check usp first, because if that's bad the other accessors may fault
  // on some architectures.  Ditto ufp second, etc.
  uintptr_t fp_align_mask = (uintptr_t)(sizeof(address)-1);
  // sp on amd can be 32 bit aligned.
  uintptr_t sp_align_mask = (uintptr_t)(sizeof(int)-1);

  uintptr_t usp    = (uintptr_t)fr->sp();
  if ((usp & sp_align_mask) != 0) return true;

  uintptr_t ufp    = (uintptr_t)fr->fp();
  if ((ufp & fp_align_mask) != 0) return true;

  uintptr_t old_sp = (uintptr_t)fr->sender_sp();
  if ((old_sp & sp_align_mask) != 0) return true;
  if (old_sp == 0 || old_sp == (uintptr_t)-1) return true;

  uintptr_t old_fp = (uintptr_t)fr->link();
  if ((old_fp & fp_align_mask) != 0) return true;
  if (old_fp == 0 || old_fp == (uintptr_t)-1 || old_fp == ufp) return true;

  // stack grows downwards; if old_fp is below current fp or if the stack
  // frame is too large, either the stack is corrupted or fp is not saved
  // on stack (i.e. on x86, ebp may be used as general register). The stack
  // is not walkable beyond current frame.
  if (old_fp < ufp) return true;
  if (old_fp - ufp > 64 * K) return true;

  return false;
}

#ifdef ASSERT
extern "C" void test_random() {
  const double m = 2147483647;
  double mean = 0.0, variance = 0.0, t;
  long reps = 10000;
  unsigned long seed = 1;

  tty->print_cr("seed %ld for %ld repeats...", seed, reps);
  os::init_random(seed);
  long num;
  for (int k = 0; k < reps; k++) {
    num = os::random();
    double u = (double)num / m;
    assert(u >= 0.0 && u <= 1.0, "bad random number!");

    // calculate mean and variance of the random sequence
    mean += u;
    variance += (u*u);
  }
  mean /= reps;
  variance /= (reps - 1);

  assert(num == 1043618065, "bad seed");
  tty->print_cr("mean of the 1st 10000 numbers: %f", mean);
  tty->print_cr("variance of the 1st 10000 numbers: %f", variance);
  const double eps = 0.0001;
  t = fabsd(mean - 0.5018);
  assert(t < eps, "bad mean");
  t = (variance - 0.3355) < 0.0 ? -(variance - 0.3355) : variance - 0.3355;
  assert(t < eps, "bad variance");
}
#endif


// Set up the boot classpath.

char* os::format_boot_path(const char* format_string,
                           const char* home,
                           int home_len,
                           char fileSep,
                           char pathSep) {
    assert((fileSep == '/' && pathSep == ':') ||
           (fileSep == '\\' && pathSep == ';'), "unexpected seperator chars");

    // Scan the format string to determine the length of the actual
    // boot classpath, and handle platform dependencies as well.
    int formatted_path_len = 0;
    const char* p;
    for (p = format_string; *p != 0; ++p) {
        if (*p == '%') formatted_path_len += home_len - 1;
        ++formatted_path_len;
    }

    char* formatted_path = NEW_C_HEAP_ARRAY(char, formatted_path_len + 1);
    if (formatted_path == NULL) {
        return NULL;
    }

    // Create boot classpath from format, substituting separator chars and
    // java home directory.
    char* q = formatted_path;
    for (p = format_string; *p != 0; ++p) {
        switch (*p) {
        case '%':
            strcpy(q, home);
            q += home_len;
            break;
        case '/':
            *q++ = fileSep;
            break;
        case ':':
            *q++ = pathSep;
            break;
        default:
            *q++ = *p;
        }
    }
    *q = '\0';

    assert((q - formatted_path) == formatted_path_len, "formatted_path size botched");
    return formatted_path;
}


bool os::set_boot_path(char fileSep, char pathSep) {
    const char* home = Arguments::get_java_home();
    int home_len = (int)strlen(home);

    static const char* meta_index_dir_format = "%/lib/";
    static const char* meta_index_format = "%/lib/meta-index";
    char* meta_index = format_boot_path(meta_index_format, home, home_len, fileSep, pathSep);
    if (meta_index == NULL) return false;
    char* meta_index_dir = format_boot_path(meta_index_dir_format, home, home_len, fileSep, pathSep);
    if (meta_index_dir == NULL) return false;
    Arguments::set_meta_index_path(meta_index, meta_index_dir);

    // Any modification to the JAR-file list, for the boot classpath must be
    // aligned with install/install/make/common/Pack.gmk. Note: boot class
    // path class JARs, are stripped for StackMapTable to reduce download size.
    static const char classpath_format[] =
        "%/lib/resources.jar:"
        "%/lib/rt.jar:"
        "%/lib/sunrsasign.jar:"
        "%/lib/jsse.jar:"
        "%/lib/jce.jar:"
        "%/lib/charsets.jar:"
        "%/classes";
    char* sysclasspath = format_boot_path(classpath_format, home, home_len, fileSep, pathSep);
    if (sysclasspath == NULL) return false;
    Arguments::set_sysclasspath(sysclasspath);

    return true;
}

/*
 * Splits a path, based on its separator, the number of
 * elements is returned back in n.
 * It is the callers responsibility to:
 *   a> check the value of n, and n may be 0.
 *   b> ignore any empty path elements
 *   c> free up the data.
 */
char** os::split_path(const char* path, int* n) {
  *n = 0;
  if (path == NULL || strlen(path) == 0) {
    return NULL;
  }
  const char psepchar = *os::path_separator();
  char* inpath = (char*)NEW_C_HEAP_ARRAY(char, strlen(path) + 1);
  if (inpath == NULL) {
    return NULL;
  }
  strncpy(inpath, path, strlen(path));
  int count = 1;
  char* p = strchr(inpath, psepchar);
  // Get a count of elements to allocate memory
  while (p != NULL) {
    count++;
    p++;
    p = strchr(p, psepchar);
  }
  char** opath = (char**) NEW_C_HEAP_ARRAY(char*, count);
  if (opath == NULL) {
    return NULL;
  }

  // do the actual splitting
  p = inpath;
  for (int i = 0 ; i < count ; i++) {
    size_t len = strcspn(p, os::path_separator());
    if (len > JVM_MAXPATHLEN) {
      return NULL;
    }
    // allocate the string and add terminator storage
    char* s  = (char*)NEW_C_HEAP_ARRAY(char, len + 1);
    if (s == NULL) {
      return NULL;
    }
    strncpy(s, p, len);
    s[len] = '\0';
    opath[i] = s;
    p += len + 1;
  }
  FREE_C_HEAP_ARRAY(char, inpath);
  *n = count;
  return opath;
}

void os::set_memory_serialize_page(address page) {
  int count = log2_intptr(sizeof(class JavaThread)) - log2_intptr(64);
  _mem_serialize_page = (volatile int32_t *)page;
  // We initialize the serialization page shift count here
  // We assume a cache line size of 64 bytes
  assert(SerializePageShiftCount == count,
         "thread size changed, fix SerializePageShiftCount constant");
  set_serialize_page_mask((uintptr_t)(vm_page_size() - sizeof(int32_t)));
}

static volatile intptr_t SerializePageLock = 0;

// This method is called from signal handler when SIGSEGV occurs while the current
// thread tries to store to the "read-only" memory serialize page during state
// transition.
void os::block_on_serialize_page_trap() {
  if (TraceSafepoint) {
    tty->print_cr("Block until the serialize page permission restored");
  }
  // When VMThread is holding the SerializePageLock during modifying the
  // access permission of the memory serialize page, the following call
  // will block until the permission of that page is restored to rw.
  // Generally, it is unsafe to manipulate locks in signal handlers, but in
  // this case, it's OK as the signal is synchronous and we know precisely when
  // it can occur.
  Thread::muxAcquire(&SerializePageLock, "set_memory_serialize_page");
  Thread::muxRelease(&SerializePageLock);
}

// Serialize all thread state variables
void os::serialize_thread_states() {
  // On some platforms such as Solaris & Linux, the time duration of the page
  // permission restoration is observed to be much longer than expected  due to
  // scheduler starvation problem etc. To avoid the long synchronization
  // time and expensive page trap spinning, 'SerializePageLock' is used to block
  // the mutator thread if such case is encountered. See bug 6546278 for details.
  Thread::muxAcquire(&SerializePageLock, "serialize_thread_states");
  os::protect_memory((char *)os::get_memory_serialize_page(),
                     os::vm_page_size(), MEM_PROT_READ);
  os::protect_memory((char *)os::get_memory_serialize_page(),
                     os::vm_page_size(), MEM_PROT_RW);
  Thread::muxRelease(&SerializePageLock);
}

// Returns true if the current stack pointer is above the stack shadow
// pages, false otherwise.

bool os::stack_shadow_pages_available(Thread *thread, methodHandle method) {
  assert(StackRedPages > 0 && StackYellowPages > 0,"Sanity check");
  address sp = current_stack_pointer();
  // Check if we have StackShadowPages above the yellow zone.  This parameter
  // is dependent on the depth of the maximum VM call stack possible from
  // the handler for stack overflow.  'instanceof' in the stack overflow
  // handler or a println uses at least 8k stack of VM and native code
  // respectively.
  const int framesize_in_bytes =
    Interpreter::size_top_interpreter_activation(method()) * wordSize;
  int reserved_area = ((StackShadowPages + StackRedPages + StackYellowPages)
                      * vm_page_size()) + framesize_in_bytes;
  // The very lower end of the stack
  address stack_limit = thread->stack_base() - thread->stack_size();
  return (sp > (stack_limit + reserved_area));
}

size_t os::page_size_for_region(size_t region_min_size, size_t region_max_size,
                                uint min_pages)
{
  assert(min_pages > 0, "sanity");
  if (UseLargePages) {
    const size_t max_page_size = region_max_size / min_pages;

    for (unsigned int i = 0; _page_sizes[i] != 0; ++i) {
      const size_t sz = _page_sizes[i];
      const size_t mask = sz - 1;
      if ((region_min_size & mask) == 0 && (region_max_size & mask) == 0) {
        // The largest page size with no fragmentation.
        return sz;
      }

      if (sz <= max_page_size) {
        // The largest page size that satisfies the min_pages requirement.
        return sz;
      }
    }
  }

  return vm_page_size();
}

#ifndef PRODUCT
void os::trace_page_sizes(const char* str, const size_t region_min_size,
                          const size_t region_max_size, const size_t page_size,
                          const char* base, const size_t size)
{
  if (TracePageSizes) {
    tty->print_cr("%s:  min=" SIZE_FORMAT " max=" SIZE_FORMAT
                  " pg_sz=" SIZE_FORMAT " base=" PTR_FORMAT
                  " size=" SIZE_FORMAT,
                  str, region_min_size, region_max_size,
                  page_size, base, size);
  }
}
#endif  // #ifndef PRODUCT

// This is the working definition of a server class machine:
// >= 2 physical CPU's and >=2GB of memory, with some fuzz
// because the graphics memory (?) sometimes masks physical memory.
// If you want to change the definition of a server class machine
// on some OS or platform, e.g., >=4GB on Windohs platforms,
// then you'll have to parameterize this method based on that state,
// as was done for logical processors here, or replicate and
// specialize this method for each platform.  (Or fix os to have
// some inheritance structure and use subclassing.  Sigh.)
// If you want some platform to always or never behave as a server
// class machine, change the setting of AlwaysActAsServerClassMachine
// and NeverActAsServerClassMachine in globals*.hpp.
bool os::is_server_class_machine() {
  // First check for the early returns
  if (NeverActAsServerClassMachine) {
    return false;
  }
  if (AlwaysActAsServerClassMachine) {
    return true;
  }
  // Then actually look at the machine
  bool         result            = false;
  const unsigned int    server_processors = 2;
  const julong server_memory     = 2UL * G;
  // We seem not to get our full complement of memory.
  //     We allow some part (1/8?) of the memory to be "missing",
  //     based on the sizes of DIMMs, and maybe graphics cards.
  const julong missing_memory   = 256UL * M;

  /* Is this a server class machine? */
  if ((os::active_processor_count() >= (int)server_processors) &&
      (os::physical_memory() >= (server_memory - missing_memory))) {
    const unsigned int logical_processors =
      VM_Version::logical_processors_per_package();
    if (logical_processors > 1) {
      const unsigned int physical_packages =
        os::active_processor_count() / logical_processors;
      if (physical_packages > server_processors) {
        result = true;
      }
    } else {
      result = true;
    }
  }
  return result;
}
