/*
 * Copyright (c) 2025, Google LLC. All rights reserved.
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
package org.openjdk.bench.java.lang.foreign;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.annotations.State;

@Warmup(time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@State(Scope.Benchmark)
public class StringLoopJmhBenchmark {
  @Param({"10", "100", "1000", "100000"})
  int stringLength;

  @Param({"ASCII", "LATIN1", "UTF16"})
  String encoding;

  String stringData;

  @Setup
  public void setUp() {
    stringData = "";

    // Character at the _end_ to affect if we hit
    // - ASCII = compact strings and compatible with UTF-8
    // - LATIN1 = compact strings but not compatible with UTF-8
    // - UTF16 = 2-byte char storage and not compatible with UTF-8
    String c;
    if (encoding.equals("ASCII")) {
      c = "a";
    } else if (encoding.equals("LATIN1")) {
      c = "\u00C4";
    } else if (encoding.equals("UTF16")) {
      c = "\u2603";
    } else {
      throw new IllegalArgumentException("Unknown encoding: " + encoding);
    }

    var stringDataBuilder = new StringBuilder(stringLength + 1);
    while (stringDataBuilder.length() < stringLength) {
      stringDataBuilder.append((char) (Math.random() * 26) + 'a');
    }
    stringData = stringDataBuilder.append(c).toString();
  }

  @Benchmark
  public int utf8LenByLoop() {
    final String s = stringData;
    final int len = s.length();

    // ASCII prefix strings.
    int idx = 0;
    for (char c; idx < len && (c = s.charAt(idx)) < 0x80; ++idx) {}

    // Entire string was ASCII.
    if (idx == len) {
      return len;
    }

    int utf8Len = len;
    for (char c; idx < len; ++idx) {
      c = s.charAt(idx);
      if (c < 0x80) {
        utf8Len++;
      } else if (c < 0x800) {
        utf8Len += 2;
      } else {
        utf8Len += 3;
        if (Character.isSurrogate(c)) {
          int cp = Character.codePointAt(s, idx);
          if (cp < Character.MIN_SUPPLEMENTARY_CODE_POINT) {
            throw new RuntimeException("Unpaired surrogate");
          }
          idx++;
        }
      }
    }
    return utf8Len;
  }

  @Benchmark
  public int getBytes() throws Exception {
    return stringData.getBytes(StandardCharsets.UTF_8).length;
  }

  @Benchmark
  public int getByteLength() throws Exception {
    return stringData.getByteLength(StandardCharsets.UTF_8);
  }
}
