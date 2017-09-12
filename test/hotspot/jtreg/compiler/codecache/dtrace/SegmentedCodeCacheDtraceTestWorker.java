/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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

package compiler.codecache.dtrace;

import jdk.test.lib.Utils;
import sun.hotspot.WhiteBox;

import java.lang.reflect.Executable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class SegmentedCodeCacheDtraceTestWorker {

    private static final String METHOD1_NAME = "foo";
    private static final String METHOD2_NAME = "bar";
    private static final String METHOD3_NAME = "baz";
    public static final List<Executable> TESTED_METHODS_LIST;
    private final WhiteBox wb;
    private final int compLevels[];

    static {
        List<Executable> methods = new ArrayList<>();
        try {
            // method order is important. Need to place methods in call order,
            // to be able to verify results later
            methods.add(SegmentedCodeCacheDtraceTestWorker.class.getMethod(METHOD1_NAME));
            methods.add(SegmentedCodeCacheDtraceTestWorker.class.getMethod(METHOD2_NAME));
            methods.add(SegmentedCodeCacheDtraceTestWorker.class.getMethod(METHOD3_NAME));
        } catch (NoSuchMethodException e) {
            throw new Error("TESTBUG: no expected method found", e);
        }
        TESTED_METHODS_LIST = Collections.unmodifiableList(methods);
    }

    protected static final boolean BACKGROUND_COMPILATION
            = WhiteBox.getWhiteBox().getBooleanVMFlag("BackgroundCompilation");

    public static void main(String[] args) {
        if (args.length != 2 * TESTED_METHODS_LIST.size()) {
            throw new Error("Usage: java <thisClass> <fooCompLevel> <fooInlined>"
                    + "<barCompLevel> <barInlined> "
                    + "<bazCompLevel> <bazInlined>");
        } else {
            int compLevels[] = new int[TESTED_METHODS_LIST.size()];
            boolean inlines[] = new boolean[TESTED_METHODS_LIST.size()];
            for (int i = 0; i < TESTED_METHODS_LIST.size(); i++) {
                compLevels[i] = Integer.parseInt(args[2 * i]);
                inlines[i] = Boolean.parseBoolean(args[2 * i + 1]);
            }
            new SegmentedCodeCacheDtraceTestWorker(compLevels, inlines).test();
        }
    }

    public SegmentedCodeCacheDtraceTestWorker(int compLevels[], boolean inlines[]) {
        wb = WhiteBox.getWhiteBox();
        this.compLevels = Arrays.copyOf(compLevels, compLevels.length);
        for (int i = 0; i < compLevels.length; i++) {
            if (inlines[i]) {
                wb.testSetForceInlineMethod(TESTED_METHODS_LIST.get(i), true);
            } else {
                wb.testSetDontInlineMethod(TESTED_METHODS_LIST.get(i), true);
            }
        }
    }

    private void waitForCompilation(Executable executable, int compLevel) {
        if (compLevel > 0) {
            Utils.waitForCondition(() -> wb.isMethodCompiled(executable));
        }
    }

    protected void test() {
        for (int i = 0; i < TESTED_METHODS_LIST.size(); i++) {
            Executable method = TESTED_METHODS_LIST.get(i);
            int compLevel = compLevels[i];
            wb.enqueueMethodForCompilation(method, compLevel);
            waitForCompilation(method, compLevel);
        }
        foo();
    }

    public static void foo() {
        bar();
    }

    public static void bar() {
        baz();
    }

    public static void baz() {
        System.out.println("Reached baz method");
    }
}
