/*
 * Copyright 1998-2007 Sun Microsystems, Inc.  All Rights Reserved.
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

class BasicLock VALUE_OBJ_CLASS_SPEC {
  friend class VMStructs;
 private:
  volatile markOop _displaced_header;
 public:
  markOop      displaced_header() const               { return _displaced_header; }
  void         set_displaced_header(markOop header)   { _displaced_header = header; }

  void print_on(outputStream* st) const;

  // move a basic lock (used during deoptimization
  void move_to(oop obj, BasicLock* dest);

  static int displaced_header_offset_in_bytes()       { return offset_of(BasicLock, _displaced_header); }
};

// A BasicObjectLock associates a specific Java object with a BasicLock.
// It is currently embedded in an interpreter frame.

// Because some machines have alignment restrictions on the control stack,
// the actual space allocated by the interpreter may include padding words
// after the end of the BasicObjectLock.  Also, in order to guarantee
// alignment of the embedded BasicLock objects on such machines, we
// put the embedded BasicLock at the beginning of the struct.

class BasicObjectLock VALUE_OBJ_CLASS_SPEC {
  friend class VMStructs;
 private:
  BasicLock _lock;                                    // the lock, must be double word aligned
  oop       _obj;                                     // object holds the lock;

 public:
  // Manipulation
  oop      obj() const                                { return _obj;  }
  void set_obj(oop obj)                               { _obj = obj; }
  BasicLock* lock()                                   { return &_lock; }

  // Note: Use frame::interpreter_frame_monitor_size() for the size of BasicObjectLocks
  //       in interpreter activation frames since it includes machine-specific padding.
  static int size()                                   { return sizeof(BasicObjectLock)/wordSize; }

  // GC support
  void oops_do(OopClosure* f) { f->do_oop(&_obj); }

  static int obj_offset_in_bytes()                    { return offset_of(BasicObjectLock, _obj);  }
  static int lock_offset_in_bytes()                   { return offset_of(BasicObjectLock, _lock); }
};

class ObjectMonitor;

class ObjectSynchronizer : AllStatic {
  friend class VMStructs;
 public:
  typedef enum {
    owner_self,
    owner_none,
    owner_other
  } LockOwnership;
  // exit must be implemented non-blocking, since the compiler cannot easily handle
  // deoptimization at monitor exit. Hence, it does not take a Handle argument.

  // This is full version of monitor enter and exit. I choose not
  // to use enter() and exit() in order to make sure user be ware
  // of the performance and semantics difference. They are normally
  // used by ObjectLocker etc. The interpreter and compiler use
  // assembly copies of these routines. Please keep them synchornized.
  //
  // attempt_rebias flag is used by UseBiasedLocking implementation
  static void fast_enter  (Handle obj, BasicLock* lock, bool attempt_rebias, TRAPS);
  static void fast_exit   (oop obj,    BasicLock* lock, Thread* THREAD);

  // WARNING: They are ONLY used to handle the slow cases. They should
  // only be used when the fast cases failed. Use of these functions
  // without previous fast case check may cause fatal error.
  static void slow_enter  (Handle obj, BasicLock* lock, TRAPS);
  static void slow_exit   (oop obj,    BasicLock* lock, Thread* THREAD);

  // Used only to handle jni locks or other unmatched monitor enter/exit
  // Internally they will use heavy weight monitor.
  static void jni_enter   (Handle obj, TRAPS);
  static bool jni_try_enter(Handle obj, Thread* THREAD); // Implements Unsafe.tryMonitorEnter
  static void jni_exit    (oop obj,    Thread* THREAD);

  // Handle all interpreter, compiler and jni cases
  static void wait               (Handle obj, jlong millis, TRAPS);
  static void notify             (Handle obj,               TRAPS);
  static void notifyall          (Handle obj,               TRAPS);

  // Special internal-use-only method for use by JVM infrastructure
  // that needs to wait() on a java-level object but that can't risk
  // throwing unexpected InterruptedExecutionExceptions.
  static void waitUninterruptibly (Handle obj, jlong Millis, Thread * THREAD) ;

  // used by classloading to free classloader object lock,
  // wait on an internal lock, and reclaim original lock
  // with original recursion count
  static intptr_t complete_exit  (Handle obj,                TRAPS);
  static void reenter            (Handle obj, intptr_t recursion, TRAPS);

  // thread-specific and global objectMonitor free list accessors
  static ObjectMonitor * omAlloc (Thread * Self) ;
  static void omRelease (Thread * Self, ObjectMonitor * m) ;
  static void omFlush   (Thread * Self) ;

  // Inflate light weight monitor to heavy weight monitor
  static ObjectMonitor* inflate(Thread * Self, oop obj);
  // This version is only for internal use
  static ObjectMonitor* inflate_helper(oop obj);

  // Returns the identity hash value for an oop
  // NOTE: It may cause monitor inflation
  static intptr_t identity_hash_value_for(Handle obj);
  static intptr_t FastHashCode (Thread * Self, oop obj) ;

  // java.lang.Thread support
  static bool current_thread_holds_lock(JavaThread* thread, Handle h_obj);
  static LockOwnership query_lock_ownership(JavaThread * self, Handle h_obj);

  static JavaThread* get_lock_owner(Handle h_obj, bool doLock);

  // JNI detach support
  static void release_monitors_owned_by_thread(TRAPS);
  static void monitors_iterate(MonitorClosure* m);

  // GC: we current use aggressive monitor deflation policy
  // Basically we deflate all monitors that are not busy.
  // An adaptive profile-based deflation policy could be used if needed
  static void deflate_idle_monitors();
  static bool deflate_monitor(ObjectMonitor* mid, oop obj, ObjectMonitor** FreeHeadp,
                              ObjectMonitor** FreeTailp);
  static void oops_do(OopClosure* f);

  // debugging
  static void trace_locking(Handle obj, bool is_compiled, bool is_method, bool is_locking) PRODUCT_RETURN;
  static void verify() PRODUCT_RETURN;
  static int  verify_objmon_isinpool(ObjectMonitor *addr) PRODUCT_RETURN0;

 private:
  enum { _BLOCKSIZE = 128 };
  static ObjectMonitor* gBlockList;
  static ObjectMonitor * volatile gFreeList;

 public:
  static void Initialize () ;
  static PerfCounter * _sync_ContendedLockAttempts ;
  static PerfCounter * _sync_FutileWakeups ;
  static PerfCounter * _sync_Parks ;
  static PerfCounter * _sync_EmptyNotifications ;
  static PerfCounter * _sync_Notifications ;
  static PerfCounter * _sync_SlowEnter ;
  static PerfCounter * _sync_SlowExit ;
  static PerfCounter * _sync_SlowNotify ;
  static PerfCounter * _sync_SlowNotifyAll ;
  static PerfCounter * _sync_FailedSpins ;
  static PerfCounter * _sync_SuccessfulSpins ;
  static PerfCounter * _sync_PrivateA ;
  static PerfCounter * _sync_PrivateB ;
  static PerfCounter * _sync_MonInCirculation ;
  static PerfCounter * _sync_MonScavenged ;
  static PerfCounter * _sync_Inflations ;
  static PerfCounter * _sync_Deflations ;
  static PerfLongVariable * _sync_MonExtant ;

 public:
  static void RegisterSpinCallback (int (*)(intptr_t, int), intptr_t) ;

};

// ObjectLocker enforced balanced locking and can never thrown an
// IllegalMonitorStateException. However, a pending exception may
// have to pass through, and we must also be able to deal with
// asynchronous exceptions. The caller is responsible for checking
// the threads pending exception if needed.
// doLock was added to support classloading with UnsyncloadClass which
// requires flag based choice of locking the classloader lock.
class ObjectLocker : public StackObj {
 private:
  Thread*   _thread;
  Handle    _obj;
  BasicLock _lock;
  bool      _dolock;   // default true
 public:
  ObjectLocker(Handle obj, Thread* thread, bool doLock = true);
  ~ObjectLocker();

  // Monitor behavior
  void wait      (TRAPS)      { ObjectSynchronizer::wait     (_obj, 0, CHECK); } // wait forever
  void notify_all(TRAPS)      { ObjectSynchronizer::notifyall(_obj,    CHECK); }
  void waitUninterruptibly (TRAPS) { ObjectSynchronizer::waitUninterruptibly (_obj, 0, CHECK);}
  // complete_exit gives up lock completely, returning recursion count
  // reenter reclaims lock with original recursion count
  intptr_t complete_exit(TRAPS) { return  ObjectSynchronizer::complete_exit(_obj, CHECK_0); }
  void reenter(intptr_t recursion, TRAPS) { ObjectSynchronizer::reenter(_obj, recursion, CHECK); }
};
