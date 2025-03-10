/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package org.openjdk.bench.java.lang;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 3)
public class StringIndexOfHuge {

  private String dataString;
  private String dataString16;
  private String dataStringHuge;
  private String dataStringHuge16;
  private String earlyMatchString;
  private String earlyMatchString16;
  private String midMatchString;
  private String midMatchString16;
  private String lateMatchString;
  private String lateMatchString16;

  private String searchString;
  private String searchString16;
  private String searchStringSmall;
  private String searchStringSmall16;

  private String searchStringHuge;
  private String searchStringHuge16;

  private String searchNoMatch;
  private String searchNoMatch16;

  private String Amdahl_1;
  private String Amdahl_2;
  private String Amdahl_3;
  private String Amdahl_4;
  private String Amdahl_5;
  private String Amdahl_6;

  @Setup
  public void setup() {
    dataString = "ngdflsoscargfdgf";
    dataString16 = "ngdfilso\u01facargfd\u01eef";
    dataStringHuge = (("A".repeat(32) + "B".repeat(32)).repeat(16) + "X").repeat(2) + "bB";
    dataStringHuge16 = "\u01de" + (("A".repeat(32) + "B".repeat(32)).repeat(16) + "\u01fe").repeat(2) + "\u01eeB";
    earlyMatchString = dataStringHuge.substring(0, 34);
    earlyMatchString16 = dataStringHuge16.substring(0, 34);
    midMatchString = dataStringHuge.substring(dataStringHuge.length() / 2 - 16, dataStringHuge.length() / 2 + 17);
    midMatchString16 = dataStringHuge16.substring(dataStringHuge16.length() / 2 - 16, dataStringHuge16.length() / 2 + 17);
    lateMatchString = dataStringHuge.substring(dataStringHuge.length() - 31);
    lateMatchString16 = dataStringHuge16.substring(dataStringHuge16.length() - 31);

    searchString = "oscar";
    searchString16 = "o\u01facar";
    searchStringSmall = "dgf";
    searchStringSmall16 = "d\u01eef";

    searchStringHuge = "capaapapapasdkajdlkajskldjaslkajdlkajskldjaslkjdlkasjdsalk";
    searchStringHuge16 = "capaapapapasdkajdlka\u01feskldjaslkajdlkajskldjaslkjdlkasjdsalk";

    searchNoMatch = "XYXyxYxy".repeat(22);
    searchNoMatch16 = "\u01ab\u01ba\u01cb\u01bc\u01de\u01ed\u01fa\u01af".repeat(22);

    Amdahl_1 = "B".repeat(30) + "X" + "A".repeat(30);
    Amdahl_2 = "A".repeat(32) + "F" + "B".repeat(32);
    Amdahl_3 = "A".repeat(32) + "B".repeat(32) + "XbB";
    Amdahl_4 = "B".repeat(30) + "\u01ef" + "A".repeat(30);
    Amdahl_5 = "A".repeat(32) + "\u01ef" + "B".repeat(32);
    Amdahl_6 = "A".repeat(32) + "B".repeat(32) + "\u01fe\u01eeB";
  }


  /** IndexOf Micros */
  @Benchmark
  public int searchHugeEarlyMatch() {
      return dataStringHuge.indexOf(earlyMatchString);
  }

  @Benchmark
  public int searchHugeMiddleMatch() {
      return dataStringHuge.indexOf(midMatchString);
  }

  @Benchmark
  public int searchHugeLateMatch() {
      return dataStringHuge.indexOf(lateMatchString);
  }

  @Benchmark
  public int searchHugeNoMatch() {
      return dataStringHuge.indexOf(searchNoMatch);
  }

  @Benchmark
  public int searchSmallEarlyMatch() {
      return searchString.indexOf(searchString);
  }

  @Benchmark
  public int searchSmallMidMatch() {
      return dataString.indexOf(searchString);
  }

  @Benchmark
  public int searchSmallLateMatch() {
      return dataString.indexOf(searchStringSmall);
  }

  @Benchmark
  public int searchHugeLargeSubstring() {
      return dataStringHuge.indexOf(Amdahl_1, 74);
  }

  @Benchmark
  public int searchHugeLargeSubstringNoMatch() {
      return dataStringHuge.indexOf(Amdahl_2, 64);
  }

  @Benchmark
  public int searchSubstringLongerThanString() {
      return midMatchString.indexOf(dataStringHuge, 3);
  }

  @Benchmark
  public int searchHugeWorstCase() {
      return dataStringHuge.indexOf(Amdahl_3);
  }

  @Benchmark
  public int search16HugeEarlyMatch() {
    return dataStringHuge16.indexOf(earlyMatchString);
  }

  @Benchmark
  public int search16HugeMiddleMatch() {
    return dataStringHuge16.indexOf(midMatchString);
  }

  @Benchmark
  public int search16HugeLateMatch() {
    return dataStringHuge16.indexOf(lateMatchString);
  }

  @Benchmark
  public int search16HugeNoMatch() {
    return dataStringHuge16.indexOf(searchNoMatch);
  }

  @Benchmark
  public int search16SmallEarlyMatch() {
    return searchString16.indexOf(searchString);
  }

  @Benchmark
  public int search16SmallMidMatch() {
    return dataString16.indexOf(searchString);
  }

  @Benchmark
  public int search16SmallLateMatch() {
    return dataString16.indexOf(searchStringSmall);
  }

  @Benchmark
  public int search16HugeLargeSubstring() {
    return dataStringHuge16.indexOf(Amdahl_1, 74);
  }

  @Benchmark
  public int search16HugeLargeSubstringNoMatch() {
    return dataStringHuge16.indexOf(Amdahl_2, 64);
  }

  @Benchmark
  public int search16SubstringLongerThanString() {
    return midMatchString16.indexOf(dataStringHuge, 3);
  }

  @Benchmark
  public int search16HugeWorstCase() {
    return dataStringHuge16.indexOf(Amdahl_3);
  }

  @Benchmark
  public int search16HugeEarlyMatch16() {
    return dataStringHuge16.indexOf(earlyMatchString16);
  }

  @Benchmark
  public int search16HugeMiddleMatch16() {
    return dataStringHuge16.indexOf(midMatchString16);
  }

  @Benchmark
  public int search16HugeLateMatch16() {
    return dataStringHuge16.indexOf(lateMatchString16);
  }

  @Benchmark
  public int search16HugeNoMatch16() {
    return dataStringHuge16.indexOf(searchNoMatch16);
  }

  @Benchmark
  public int search16SmallEarlyMatch16() {
    return searchString16.indexOf(searchString16);
  }

  @Benchmark
  public int search16SmallMidMatch16() {
    return dataString16.indexOf(searchString16);
  }

  @Benchmark
  public int search16SmallLateMatch16() {
    return dataString16.indexOf(searchStringSmall16);
  }

  @Benchmark
  public int search16HugeLargeSubstring16() {
    return dataStringHuge16.indexOf(Amdahl_4, 74);
  }

  @Benchmark
  public int search16HugeLargeSubstringNoMatch16() {
    return dataStringHuge16.indexOf(Amdahl_5, 64);
  }

  @Benchmark
  public int search16SubstringLongerThanString16() {
    return midMatchString16.indexOf(dataStringHuge16, 3);
  }

  @Benchmark
  public int search16HugeWorstCase16() {
    return dataStringHuge16.indexOf(Amdahl_6);
  }
}
