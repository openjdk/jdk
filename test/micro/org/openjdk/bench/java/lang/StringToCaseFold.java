/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

/*
  * @test
  * @benchmark
  * @summary Compare String.toUpperCase().toLowerCase() vs String.toCaseFold()
  * @library /test/lib
  * @run main org.openjdk.jmh.Main StringCaseFoldBenchmark
  */

 package org.openjdk.bench.java.lang;

 import org.openjdk.jmh.annotations.*;
 import java.util.concurrent.TimeUnit;

 @BenchmarkMode(Mode.Throughput)
 @OutputTimeUnit(TimeUnit.MILLISECONDS)
 @State(Scope.Thread)
 public class StringToCaseFold {

     @Param({"LATIN", "BMP", "SUPPLEMENTARY", "MIXED"})
     private String dataset;

     private String input;

     @Setup(Level.Trial)
     public void setup() {
         switch (dataset) {
             case "LATIN":
                 // "The Quick Brown Fox Jumps Over The Lazy Dog ß ü ö µ"
                 input = "The Quick Brown Fox Jumps Over The Lazy Dog \u00DF \u00FC \u00F6 \u00B5";
                 break;
             case "BMP":
                 // "Αλφάβητο кириллица עברית العربية"
                 input = "\u0391\u03BB\u03C6\u03AC\u03B2\u03B7\u03C4\u03BF " + // Αλφάβητο
                         "\u043A\u0438\u0440\u0438\u043B\u043B\u0438\u0446\u0430 " + // кириллица
                         "\u05E2\u05D1\u05E8\u05D9\u05EA " + // עברית
                         "\u0627\u0644\u0639\u0631\u0628\u064A\u0629"; // العربية
                 break;
             case "SUPPLEMENTARY":
                 // "𐐷𐐲𐑌 Deseret 𐍈 Gothic 𝒜𝒷𝒸𝒟𝒺𝒻 MathBold"
                 input = "\uD801\uDC37\uD801\uDC32\uD801\uDC4C Deseret " + // 𐐷𐐲𐑌
                         "\uD800\uDF48 Gothic " + // 𐍈
                         "\uD835\uDC9C\uD835\uDCB7\uD835\uDCB8\uD835\uDC9F\uD835\uDCA0\uD835\uDCA1 MathBold"; // 𝒜𝒷𝒸𝒟𝒺𝒻
                 break;
             case "MIXED":
                 // "Hello 𐐷 World ß ΐ ΰ 𝒜𝒷 𐍈 😊"
                 input = "Hello \uD801\uDC37 World \u00DF \u0390 \u03B0 " + // 𐐷, ß, ΐ, ΰ
                         "\uD835\uDC9C\uD835\uDCB7 " + // 𝒜𝒷
                         "\uD800\uDF48 " + // 𐍈
                         "\uD83D\uDE0A"; // 😊
                 break;
             default:
                 throw new IllegalArgumentException("Unknown dataset: " + dataset);
         }
     }

     @Benchmark
     public String upperLower() {
         // Current common workaround for caseless comparison
         return input.toUpperCase().toLowerCase();
     }

     @Benchmark
     public String caseFold() {
         // Proposed API
         return input.toCaseFold();
     }
 }