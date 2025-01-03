/*
 * Copyright (c) 2001, 2024, Oracle and/or its affiliates. All rights reserved.
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

package sun.jvm.hotspot.runtime;

import java.util.*;

import sun.jvm.hotspot.oops.*;
import sun.jvm.hotspot.utilities.*;
import sun.jvm.hotspot.debugger.*;
import sun.jvm.hotspot.types.*;
import sun.jvm.hotspot.utilities.Observable;
import sun.jvm.hotspot.utilities.Observer;

public class ObjectSynchronizer {
  static {
    VM.registerVMInitializedObserver(new Observer() {
        public void update(Observable o, Object data) {
          initialize(VM.getVM().getTypeDataBase());
        }
      });
  }

  private static synchronized void initialize(TypeDataBase db) throws WrongTypeException {
    Type objectSynchronizerType = db.lookupType("ObjectSynchronizer");
    Type monitorListType = db.lookupType("MonitorList");
    Address monitorListAddr = objectSynchronizerType.getField("_in_use_list").getStaticFieldAddress();
    inUseListHead = monitorListType.getAddressField("_head").getAddress(monitorListAddr);
  }

  public long identityHashValueFor(Oop obj) {
    Mark mark = obj.getMark();
    if (mark.isUnlocked()) {
      // FIXME: can not generate marks in debugging system
      return mark.hash();
    } else if (mark.hasMonitor()) {
      if (VM.getVM().getCommandLineFlag("UseObjectMonitorTable").getBool()) {
        return mark.hash();
      }
      ObjectMonitor monitor = mark.monitor();
      Mark temp = monitor.header();
      return temp.hash();
    } else {
      if (Assert.ASSERTS_ENABLED) {
        Assert.that(VM.getVM().isDebugging(), "Can not access displaced header otherwise");
      }
      if (mark.hasDisplacedMarkHelper()) {
        Mark temp = mark.displacedMarkHelper();
        return temp.hash();
      }
      // FIXME: can not do anything else here in debugging system
      return 0;
    }
  }

  public static Iterator objectMonitorIterator() {
    return new ObjectMonitorIterator();
  }

  private static class ObjectMonitorIterator implements Iterator {

    // JVMTI raw monitors are not included in _in_use_list and
    // are not returned by this Iterator.

    ObjectMonitorIterator() {
      mon = inUseListHead == null ? null : new ObjectMonitor(inUseListHead);
    }

    public boolean hasNext() {
      return (mon != null);
    }

    public Object next() {
      ObjectMonitor ret = mon;
      if (ret == null) {
        throw new NoSuchElementException();
      }
      // advance to next entry
      Address nextMon = mon.nextOM();
      mon = nextMon == null ? null : new ObjectMonitor(nextMon);

      return ret;
    }

    public void remove() {
      throw new UnsupportedOperationException();
    }

    private ObjectMonitor mon;
  }

  private static Address inUseListHead;

}
