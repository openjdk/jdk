/*
 * Copyright (c) 2014, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8059510
 * @summary Test SharedSymbolTableBucketSize option
 * @requires vm.cds
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 */

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import jdk.test.lib.cds.CDSTestUtils;
import jdk.test.lib.process.OutputAnalyzer;

public class SharedSymbolTableBucketSize {
    public static void main(String[] args) throws Exception {
        int bucket_size = 8;

        OutputAnalyzer output =
            CDSTestUtils.createArchiveAndCheck("-XX:SharedSymbolTableBucketSize="
                                               + Integer.valueOf(bucket_size));
        CDSTestUtils.checkMappingFailure(output);

        /* [1] There may be other table stats that precede the symbol tabble.
               Skip all thse until we find this:

           [0.677s][info][aot,hashtables] Shared symbol table stats -------- base: 0x0000000800000000
           [0.677s][info][aot,hashtables] Number of entries       :     46244
           [0.677s][info][aot,hashtables] Total bytes used        :    393792
           [0.677s][info][aot,hashtables] Average bytes per entry :     8.516
           [0.677s][info][aot,hashtables] Average bucket size     :     7.734
           [0.677s][info][aot,hashtables] Variance of bucket size :     7.513
           [0.677s][info][aot,hashtables] Std. dev. of bucket size:     2.741
           [0.677s][info][aot,hashtables] Maximum bucket size     :        20
           [0.677s][info][aot,hashtables] Empty buckets           :         2
           [0.677s][info][aot,hashtables] Value_Only buckets      :        24
           [0.677s][info][aot,hashtables] Other buckets           :      5953
           ....
        */
        Pattern pattern0 = Pattern.compile("Shared symbol table stats.*", Pattern.DOTALL);
        Matcher matcher0 = pattern0.matcher(output.getStdout());
        String stat = null;
        if (matcher0.find()) {
            stat = matcher0.group(0);
        }
        if (stat == null) {
            throw new Exception("FAILED: pattern \"" + pattern0 + "\" not found");
        }

        /* (2) The first "Average bucket size" line in the remaining output is for the
               shared symbol table */
        Pattern pattern = Pattern.compile("Average bucket size *: *([0-9]+\\.[0-9]+).*", Pattern.MULTILINE);
        Matcher matcher = pattern.matcher(stat);
        String s = null;
        if (matcher.find()) {
            s = matcher.group(1);
        }
        if (s == null) {
            throw new Exception("FAILED: pattern \"" + pattern + "\" not found");
        }

        Float f = Float.parseFloat(s);
        int size = Math.round(f);
        if (size != bucket_size) {
            throw new Exception("FAILED: incorrect bucket size " + size +
                                ", expect " + bucket_size);
        }

        // Invalid SharedSymbolTableBucketSize input
        String input[] = {"-XX:SharedSymbolTableBucketSize=-1",
                          "-XX:SharedSymbolTableBucketSize=2.5"};
        for (int i = 0; i < input.length; i++) {
            CDSTestUtils.createArchive(input[i])
                .shouldContain("Improperly specified VM option")
                .shouldHaveExitValue(1);
        }
    }
}
