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

/*
 * @test
 * @bug 8276422
 * @summary add command-line option to disable finalization
 * @run main/othervm                         FinalizationOption yes
 * @run main/othervm --finalization=enabled  FinalizationOption yes
 * @run main/othervm --finalization=disabled FinalizationOption no
 */
public class FinalizationOption {
    static volatile boolean finalizerWasCalled = false;

    @SuppressWarnings("deprecation")
    protected void finalize() {
        finalizerWasCalled = true;
    }

    static void create() {
        new FinalizationOption();
    }

    public static void main(String[] args) {
        boolean expectFinalizerToBeCalled = switch (args[0]) {
            case "yes" -> true;
            case "no"  -> false;
            default -> {
                throw new AssertionError("usage: FinalizationOption yes|no");
            }
        };

        create();
        for (int i = 0; i < 100; i++) {
            System.gc();
            try {
                Thread.sleep(10L);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
            if (finalizerWasCalled) {
                break;
            }
        }
        boolean passed = (expectFinalizerToBeCalled == finalizerWasCalled);

        System.out.printf("expectFinalizerToBeCalled: %s   finalizerWasCalled: %s   %s%n",
            expectFinalizerToBeCalled, finalizerWasCalled,
            passed ? "Passed." : "FAILED!");

        if (! passed)
            throw new AssertionError();
    }
}
