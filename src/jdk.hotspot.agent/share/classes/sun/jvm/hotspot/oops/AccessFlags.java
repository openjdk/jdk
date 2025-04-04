/*
 * Copyright (c) 2000, 2025, Oracle and/or its affiliates. All rights reserved.
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

package sun.jvm.hotspot.oops;

import sun.jvm.hotspot.runtime.ClassConstants;
import java.io.*;

public class AccessFlags implements /* imports */ ClassConstants {
  public AccessFlags(long flags) {
    this.flags = flags;
  }

  private long flags;

  // Java access flags
  public boolean isPublic      () { return (flags & JVM_ACC_PUBLIC      ) != 0; }
  public boolean isPrivate     () { return (flags & JVM_ACC_PRIVATE     ) != 0; }
  public boolean isProtected   () { return (flags & JVM_ACC_PROTECTED   ) != 0; }
  public boolean isStatic      () { return (flags & JVM_ACC_STATIC      ) != 0; }
  public boolean isFinal       () { return (flags & JVM_ACC_FINAL       ) != 0; }
  public boolean isSynchronized() { return (flags & JVM_ACC_SYNCHRONIZED) != 0; }
  public boolean isSuper       () { return (flags & JVM_ACC_SUPER       ) != 0; }
  public boolean isVolatile    () { return (flags & JVM_ACC_VOLATILE    ) != 0; }
  public boolean isBridge      () { return (flags & JVM_ACC_BRIDGE      ) != 0; }
  public boolean isTransient   () { return (flags & JVM_ACC_TRANSIENT   ) != 0; }
  public boolean isVarArgs     () { return (flags & JVM_ACC_VARARGS     ) != 0; }
  public boolean isNative      () { return (flags & JVM_ACC_NATIVE      ) != 0; }
  public boolean isEnum        () { return (flags & JVM_ACC_ENUM        ) != 0; }
  public boolean isAnnotation  () { return (flags & JVM_ACC_ANNOTATION  ) != 0; }
  public boolean isInterface   () { return (flags & JVM_ACC_INTERFACE   ) != 0; }
  public boolean isAbstract    () { return (flags & JVM_ACC_ABSTRACT    ) != 0; }
  public boolean isStrict      () { return (flags & JVM_ACC_STRICT      ) != 0; }
  public boolean isSynthetic   () { return (flags & JVM_ACC_SYNTHETIC   ) != 0; }

  public long getValue         () { return flags; }

  public void printOn(PrintStream tty) {
    // prints only .class flags and not the hotspot internal flags
    if (isPublic      ()) tty.print("public "      );
    if (isPrivate     ()) tty.print("private "     );
    if (isProtected   ()) tty.print("protected "   );
    if (isStatic      ()) tty.print("static "      );
    if (isFinal       ()) tty.print("final "       );
    if (isSynchronized()) tty.print("synchronized ");
    if (isVolatile    ()) tty.print("volatile "    );
    if (isBridge      ()) tty.print("bridge "      );
    if (isTransient   ()) tty.print("transient "   );
    if (isVarArgs     ()) tty.print("varargs "     );
    if (isNative      ()) tty.print("native "      );
    if (isEnum        ()) tty.print("enum "        );
    if (isInterface   ()) tty.print("interface "   );
    if (isAbstract    ()) tty.print("abstract "    );
    if (isStrict      ()) tty.print("strict "      );
    if (isSynthetic   ()) tty.print("synthetic "   );
  }
}
