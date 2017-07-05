/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
 */

package com.oracle.java.testlibrary;

public class Platform {
  private static final String osName = System.getProperty("os.name");
  private static final String dataModel = System.getProperty("sun.arch.data.model");
  private static final String vmVersion = System.getProperty("java.vm.version");

  public static boolean is64bit() {
    return dataModel.equals("64");
  }

  public static boolean isSolaris() {
    return osName.toLowerCase().startsWith("sunos");
  }

  public static boolean isWindows() {
    return osName.toLowerCase().startsWith("win");
  }

  public static boolean isOSX() {
    return osName.toLowerCase().startsWith("mac");
  }

  public static boolean isLinux() {
    return osName.toLowerCase().startsWith("linux");
  }

  public static String getOsName() {
    return osName;
  }

  public static boolean isDebugBuild() {
    return vmVersion.toLowerCase().contains("debug");
  }

  public static String getVMVersion() {
    return vmVersion;
  }
}
