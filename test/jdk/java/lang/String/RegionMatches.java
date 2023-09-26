/*
 * Copyright (c) 1998, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @bug 4016509 8316879
 * @summary test regionMatches corner cases
 * @run testng RegionMatches
 */

import java.io.UnsupportedEncodingException;
import org.testng.annotations.Test;

@Test
public class RegionMatches {

  private final String s1_LATIN1 = "abc";
  private final String s2_LATIN1 = "def";

  private final byte[] b1_UTF16 = new byte[]{0x04, 0x3d, 0x04, 0x30, 0x04, 0x36, 0x04, 0x34};
  private final byte[] b2_UTF16 = new byte[]{0x04, 0x32, 0x00, 0x20, 0x04, 0x41, 0x04, 0x42};

  @Test
  public void TestLATIN1() {
      // Test for 4016509
      if (!s1_LATIN1.regionMatches(0, s2_LATIN1, 0, Integer.MIN_VALUE))
          throw new RuntimeException("Integer overflow in RegionMatches when comparing LATIN1 strings");
  }

  @Test
  public void TestUTF16() throws UnsupportedEncodingException{
      // Test for 8316879
      String s1_UTF16 = new String(b1_UTF16, "UTF-16");
      String s2_UTF16 = new String(b2_UTF16, "UTF-16");
      if (!s1_UTF16.regionMatches(0, s2_UTF16, 0, Integer.MIN_VALUE + 1))
          throw new RuntimeException("Integer overflow in RegionMatches when comparing UTF16 strings");
  }
}
