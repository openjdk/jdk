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
    public String jmh_method, mode, units;
    public int    N, threads, cnt;
    public double score, error;
  }

  public static enum BenchmarkFields {
    NMT_METHOD    ,
    N             ,
    THREADS       ,
    MODE          ,
    CNT           ,
    SCORE         ,
    QUESTION_MARK ,
    ERROR         ,
    UNITS
  }

  public static String fileName;
  public static List<BenchmarkRecord> records = new ArrayList<>();

  private static void skipUntilReport(BufferedReader reader) throws IOException {
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
  }

  private static void readRecords(BufferedReader reader) throws IOException {
    String line;
    while ((line = reader.readLine()) != null) {

      if (!line.startsWith("NMTBenchmark"))
        continue;
      BenchmarkRecord rec = new BenchmarkRecord();
      String[] fields = Arrays.asList(line.split(" "))
                      .stream()
                      .filter(f -> f.length() > 0)
                      .collect(Collectors.toList())
                      .toArray(new String[0]);

      rec.jmh_method  =                    fields[BenchmarkFields.NMT_METHOD.ordinal()];
      rec.N           =   Integer.parseInt(fields[BenchmarkFields.N         .ordinal()]);
      rec.threads     =   Integer.parseInt(fields[BenchmarkFields.THREADS   .ordinal()]);
      rec.cnt         =   Integer.parseInt(fields[BenchmarkFields.CNT       .ordinal()]);
      rec.mode        =                    fields[BenchmarkFields.MODE      .ordinal()];
      rec.units       =                    fields[BenchmarkFields.UNITS     .ordinal()];
      rec.score       = Double.parseDouble(fields[BenchmarkFields.SCORE     .ordinal()]);
      rec.error       = Double.parseDouble(fields[BenchmarkFields.ERROR     .ordinal()]);

      records.add(rec);
    };
  }

  private static void analyzeRecords(BufferedReader reader) {
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
		try (BufferedReader reader = new BufferedReader(new FileReader(fileName))) {
      System.out.println("file name " + fileName);

      skipUntilReport(reader);
      readRecords(reader);
      analyzeRecords(reader);

			reader.close();
		} catch (IOException ioe) {
      ioe.printStackTrace();
      System.out.println(ioe.getMessage());
		} finally {
    }

  }
}