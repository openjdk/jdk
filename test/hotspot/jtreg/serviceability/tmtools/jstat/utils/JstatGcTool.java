/*
 * Copyright (c) 2015, 2025, Oracle and/or its affiliates. All rights reserved.
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
package utils;

import common.TmTool;

/**
 * This tool executes "jstat -gc <pid>" and returns the results as
 * JstatGcToolResults
 */
public class JstatGcTool extends TmTool<JstatGcResults> {

    private static final int TRIES = 3;

    public JstatGcTool(long pid) {
        super(JstatGcResults.class, "jstat", "-gc " + pid);
    }

    /**
      * Measure, and call assertConsistency() on the results,
      * tolerating a set number of failures to account for inconsistency in PerfData.
      */
    public JstatGcResults measureAndAssertConsistency() throws Exception {
        JstatGcResults results = null;
        for (int i = 1; i <= TRIES; i++) {
            try {
                results = measure();
                results.assertConsistency();
                break;
            } catch (RuntimeException e) {
                System.out.println("Attempt " + i + ": " + e);
                if (i == TRIES) {
                    System.out.println("Too many failures.");
                    throw(e);
                }
                // Inconsistent, will retry.
            }
        }
        return results;
    }
}
