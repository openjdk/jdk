/*
 * Copyright (c) 1998, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @run junit RegionMatches
 */

import java.io.UnsupportedEncodingException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RegionMatches {

  private final String s1_LATIN1 = "abc";
  private final String s2_LATIN1 = "def";

  private final String s1_UTF16 = "\u041e\u0434\u043d\u0430\u0436\u0434\u044b";
  private final String s2_UTF16 = "\u0432\u0441\u0442\u0443\u0434\u0435\u043d";

  @Test
  public void TestLATIN1() {
      // Test for 4016509
      boolean result = s1_LATIN1.regionMatches(0, s2_LATIN1, 0, Integer.MIN_VALUE);
      assertTrue(result, "Integer overflow in RegionMatches when comparing LATIN1 strings");
  }

  @Test
  public void TestUTF16() throws UnsupportedEncodingException{
      // Test for 8316879
      boolean result = s1_UTF16.regionMatches(0, s2_UTF16, 0, Integer.MIN_VALUE + 1);
      assertTrue(result, "Integer overflow in RegionMatches when comparing UTF16 strings");
  }
}
