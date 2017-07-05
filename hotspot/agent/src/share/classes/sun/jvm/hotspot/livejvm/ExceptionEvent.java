/*
 * Copyright (c) 2002, Oracle and/or its affiliates. All rights reserved.
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

package sun.jvm.hotspot.livejvm;

import sun.jvm.hotspot.oops.*;
import sun.jvm.hotspot.runtime.*;

public class ExceptionEvent extends Event {
  private Oop thread;
  private Oop clazz;
  private JNIid method;
  private int location;
  private Oop exception;
  private Oop catchClass;
  private JNIid catchMethod;
  private int catchLocation;

  public ExceptionEvent(Oop thread,
                        Oop clazz,
                        JNIid method,
                        int location,
                        Oop exception,
                        Oop catchClass,
                        JNIid catchMethod,
                        int catchLocation) {
    super(Event.Type.EXCEPTION);
    this.thread        = thread;
    this.clazz         = clazz;
    this.method        = method;
    this.location      = location;
    this.exception     = exception;
    this.catchClass    = catchClass;
    this.catchMethod   = catchMethod;
    this.catchLocation = catchLocation;
  }

  public Oop   thread()        { return thread;        }
  public Oop   clazz()         { return clazz;         }
  public JNIid methodID()      { return method;        }
  public int   location()      { return location;      }
  public Oop   exception()     { return exception;     }
  public Oop   catchClass()    { return catchClass;    }
  public JNIid catchMethodID() { return catchMethod;   }
  public int   catchLocation() { return catchLocation; }
}
