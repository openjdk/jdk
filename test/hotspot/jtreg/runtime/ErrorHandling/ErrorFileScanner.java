/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.IOException;

import java.util.regex.Pattern;

import jdk.test.lib.process.OutputAnalyzer;

public class ErrorFileScanner {

  public static File findHsErrorFileInOutput(OutputAnalyzer output) {

    String hs_err_file = output.firstMatch("# *(\\S*hs_err_pid.*\\.log)", 1);
    if(hs_err_file ==null) {
      throw new RuntimeException("Did not find hs-err file in output.\n");
    }

    File f = new File(hs_err_file);
    if (!f.exists()) {
      throw new RuntimeException("hs-err file missing at "
              + f.getAbsolutePath() + ".\n");
    }

    return f;

  }

  public static void scanHsErrorFileForContent(File f, Pattern[] pattern) throws IOException {
    FileInputStream fis = new FileInputStream(f);
    BufferedReader br = new BufferedReader(new InputStreamReader(fis));
    String line = null;

    int currentPattern = 0;

    String lastLine = null;
    while ((line = br.readLine()) != null && currentPattern < pattern.length) {
      if (pattern[currentPattern].matcher(line).matches()) {
        System.out.println("Found: " + line + ".");
        currentPattern++;
      }
      lastLine = line;
    }
    br.close();

    if (currentPattern < pattern.length) {
      throw new RuntimeException("hs-err file incomplete (first missing pattern: " +  pattern[currentPattern] + ")");
    }

  }
}
