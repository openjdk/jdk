/*
 * Copyright (c) 1999, 2007, Oracle and/or its affiliates. All rights reserved.
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

//
// class JvmtiRawMonitor
//
// Used by JVMTI methods: All RawMonitor methods (CreateRawMonitor, EnterRawMonitor, etc.)
//
// Wrapper for ObjectMonitor class that saves the Monitor's name
//

class JvmtiRawMonitor : public ObjectMonitor  {
private:
  int           _magic;
  char *        _name;
  // JVMTI_RM_MAGIC is set in contructor and unset in destructor.
  enum { JVMTI_RM_MAGIC = (int)(('T' << 24) | ('I' << 16) | ('R' << 8) | 'M') };

  int       SimpleEnter (Thread * Self) ;
  int       SimpleExit  (Thread * Self) ;
  int       SimpleWait  (Thread * Self, jlong millis) ;
  int       SimpleNotify (Thread * Self, bool All) ;

public:
  JvmtiRawMonitor(const char *name);
  ~JvmtiRawMonitor();
  int       raw_enter(TRAPS);
  int       raw_exit(TRAPS);
  int       raw_wait(jlong millis, bool interruptable, TRAPS);
  int       raw_notify(TRAPS);
  int       raw_notifyAll(TRAPS);
  int            magic()   { return _magic;  }
  const char *get_name()   { return _name; }
  bool        is_valid();
};

// Onload pending raw monitors
// Class is used to cache onload or onstart monitor enter
// which will transition into real monitor when
// VM is fully initialized.
class JvmtiPendingMonitors : public AllStatic {

private:
  static GrowableArray<JvmtiRawMonitor*> *_monitors; // Cache raw monitor enter

  inline static GrowableArray<JvmtiRawMonitor*>* monitors() { return _monitors; }

  static void dispose() {
    delete monitors();
  }

public:
  static void enter(JvmtiRawMonitor *monitor) {
    monitors()->append(monitor);
  }

  static int count() {
    return monitors()->length();
  }

  static void destroy(JvmtiRawMonitor *monitor) {
    while (monitors()->contains(monitor)) {
      monitors()->remove(monitor);
    }
  }

  // Return false if monitor is not found in the list.
  static bool exit(JvmtiRawMonitor *monitor) {
    if (monitors()->contains(monitor)) {
      monitors()->remove(monitor);
      return true;
    } else {
      return false;
    }
  }

  static void transition_raw_monitors();
};
