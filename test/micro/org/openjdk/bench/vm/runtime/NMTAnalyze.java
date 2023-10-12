/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class NMTAnalyze {
  public static class BenchmarkRecord {
    public String jmh_method;
    public int N, threads, cnt;
    public double score, error;
    public String mode;
    public String units;
  }
  public static class BenchmarkFields {
    static int NMT_METHOD    = 0;
    static int N             = 1;
    static int THREADS       = 2;
    static int MODE          = 3;
    static int CNT           = 4;
    static int SCORE         = 5;
    static int QUESTION_MARK = 6;
    static int ERROR         = 7;
    static int UNITS         = 8;
  }
  public static BufferedReader reader;
  public static String fileName;
  public static List<BenchmarkRecord> records = new ArrayList<>();
  private static void skipUntilReport() {
    try {
      String line = reader.readLine();
      while (line != null) {
        String ReportStartKeyword = "Benchmark";
        if (!line.startsWith(ReportStartKeyword) || line.contains("#")) {
          line = reader.readLine();
          continue;
        } else {
          break;
        }
      }
    } catch (IOException ioe) {
      System.out.println(ioe.getMessage());
    }
  }
  private static void readRecords() {
    try {
      do {
        String line = reader.readLine();
        if (line == null) break;
        if (!line.startsWith("NMTBenchmark"))
          continue;
        String[] _fields= line.split(" ");
        BenchmarkRecord rec = new BenchmarkRecord();
        List<String> __fields = Arrays.asList(_fields);
        String[] fields = __fields.stream().filter(f -> f.length() > 0).collect(Collectors.toList()).toArray(new String[0]);

        rec.jmh_method  =                    fields[BenchmarkFields.NMT_METHOD];
        rec.N           =   Integer.parseInt(fields[BenchmarkFields.N         ]);
        rec.threads     =   Integer.parseInt(fields[BenchmarkFields.THREADS   ]);
        rec.cnt         =   Integer.parseInt(fields[BenchmarkFields.CNT       ]);
        rec.mode        =                    fields[BenchmarkFields.MODE      ];
        rec.units       =                    fields[BenchmarkFields.UNITS     ];
        rec.score       = Double.parseDouble(fields[BenchmarkFields.SCORE     ]);
        rec.error       = Double.parseDouble(fields[BenchmarkFields.ERROR     ]);
        records.add(rec);
      } while (true);
    } catch (IOException ioe) {
      System.out.println(ioe.getMessage());
    }
  }
  private static void analyzeRecords() {
    String NMT_OFF     = "NMTOff";
    String NMT_DETAIL  = "NMTDetail";
    String NMT_SUMMARY = "NMTSummary";
    int[] threads = {0, 4};
    String [] methods = {"mixAallocateFreeMemory", "mixAllocateReallocateMemory", "onlyAllocateMemory"};

    System.out.printf("\n%40s %6s %9s %6s %15s %6s %24s %6s\n","Method", "Threads", " ", "Off ", " ", "Summary", " ", "Detail");
    for(String m: methods) {
      for (int t: threads) {
        List<BenchmarkRecord> mtd_thrd = records.stream()
                                        .filter(r -> r.jmh_method.contains(m) && r.threads == t)
                                        .collect(Collectors.toList());
        BenchmarkRecord off_rec     = mtd_thrd.stream().filter(r->r.jmh_method.contains(NMT_OFF))    .collect(Collectors.toList()).get(0);
        BenchmarkRecord summary_rec = mtd_thrd.stream().filter(r->r.jmh_method.contains(NMT_SUMMARY)).collect(Collectors.toList()).get(0);
        BenchmarkRecord detail_rec  = mtd_thrd.stream().filter(r->r.jmh_method.contains(NMT_DETAIL)) .collect(Collectors.toList()).get(0);

        Double nmt_off         = off_rec    .score;
        Double nmt_summary     = summary_rec.score;
        Double nmt_detail      = detail_rec .score;

        Double nmt_off_err     = off_rec    .error;
        Double nmt_summary_err = summary_rec.error;
        Double nmt_detail_err  = detail_rec .error;

        System.out.printf("%40s %5d    %7.3f (± %6.3f)    %7.3f (± %6.3f) %7.3f%%   %7.3f (± %6.3f) %7.3f%%\n",
                          m, t,
                          nmt_off, nmt_off_err,
                          nmt_summary, nmt_summary_err, (nmt_summary - nmt_off) / nmt_off * 100.0,
                          nmt_detail, nmt_detail_err, (nmt_detail - nmt_off) / nmt_off * 100.0);
      }
    }

  }
  public static void main(String[] args) {
    if (args.length < 1) {
      System.out.println("Usage: java NMTAnalyze.java <benchmark-results-filename>");
      System.exit(1);
    }
    fileName = args[0];
    System.out.println(args[0]);
		try {
      System.out.println("file name " + fileName);
			reader = new BufferedReader(new FileReader(fileName));
      skipUntilReport();
      readRecords();
      analyzeRecords();
			reader.close();
		} catch (IOException ioe) {
      ioe.printStackTrace();
      System.out.println(ioe.getMessage());
		} finally {
    }

  }
}