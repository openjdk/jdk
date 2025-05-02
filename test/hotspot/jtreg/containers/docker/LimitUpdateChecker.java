/*
 * Copyright (c) 2023, Red Hat, Inc.
 *
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

import java.io.File;
import java.io.FileOutputStream;
import jdk.test.whitebox.WhiteBox;

// Check dynamic limits updating. HotSpot side.
public class LimitUpdateChecker {

    private static final File UPDATE_FILE = new File("/tmp", "limitsUpdated");
    private static final File STARTED_FILE = new File("/tmp", "started");

    public static void main(String[] args) throws Exception {
        System.out.println("LimitUpdateChecker: Entering");
        WhiteBox wb = WhiteBox.getWhiteBox();
        printMetrics(wb); // print initial limits
        createStartedFile();
        while (!UPDATE_FILE.exists()) {
            Thread.sleep(200);
        }
        System.out.println("'limitsUpdated' file appeared. Stopped loop.");
        printMetrics(wb); // print limits after update
        System.out.println("LimitUpdateChecker DONE.");

    }

    private static void printMetrics(WhiteBox wb) {
        wb.printOsInfo();
    }

    private static void createStartedFile() throws Exception {
        FileOutputStream fout = new FileOutputStream(STARTED_FILE);
        fout.close();
    }
}
