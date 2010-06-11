/*
 * Copyright (c) 2000, 2003, Oracle and/or its affiliates. All rights reserved.
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

package sun.jvm.hotspot.utilities;

/** Provides canonicalized OS and CPU information for the rest of the
    system. */

public class PlatformInfo {
  /* Returns "solaris" if on Solaris; "win32" if Windows; "linux" if
     Linux. Used to determine location of dbx and import module, or
     possible debugger agent on win32. */
  public static String getOS() throws UnsupportedPlatformException {
    String os = System.getProperty("os.name");
    if (os.equals("SunOS")) {
      return "solaris";
    } else if (os.equals("Linux")) {
      return "linux";
    } else if (os.startsWith("Windows")) {
      return "win32";
    } else {
      throw new UnsupportedPlatformException("Operating system " + os + " not yet supported");
    }
  }

  /* Returns "sparc" if on SPARC, "x86" if on x86. */
  public static String getCPU() throws UnsupportedPlatformException {
    String cpu = System.getProperty("os.arch");
    if (cpu.equals("i386")) {
      return "x86";
    } else if (cpu.equals("sparc") || cpu.equals("x86") || cpu.equals("ia64")) {
      return cpu;
    } else if (cpu.equals("sparcv9")) {
      return "sparc";
    } else if (cpu.equals("x86_64") || cpu.equals("amd64")) {
      return "amd64";
    } else {
      throw new UnsupportedPlatformException("CPU type " + cpu + " not yet supported");
    }
  }

  // this main is invoked from Makefile to make platform specific agent Makefile(s).
  public static void main(String[] args) {
    System.out.println(getOS());
  }
}
