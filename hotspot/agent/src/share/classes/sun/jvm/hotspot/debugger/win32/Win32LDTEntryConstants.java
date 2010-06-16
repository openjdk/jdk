/*
 * Copyright (c) 2001, Oracle and/or its affiliates. All rights reserved.
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

package sun.jvm.hotspot.debugger.win32;

/** Enumerates flags in Win32LDTEntry */

interface Win32LDTEntryConstants {
  // Types of segments
  public static final int TYPE_READ_ONLY_DATA                      = 0;
  public static final int TYPE_READ_WRITE_DATA                     = 1;
  public static final int TYPE_UNUSED                              = 2;
  public static final int TYPE_READ_WRITE_EXPAND_DOWN_DATA         = 3;
  public static final int TYPE_EXECUTE_ONLY_CODE                   = 4;
  public static final int TYPE_EXECUTABLE_READABLE_CODE            = 5;
  public static final int TYPE_EXECUTE_ONLY_CONFORMING_CODE        = 6;
  public static final int TYPE_EXECUTABLE_READABLE_CONFORMING_CODE = 7;
}
