/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8380391
 * @summary basic test of CardImpl Cleaner
 * @modules java.base/java.lang.ref:open
 * @modules java.smartcardio/javax.smartcardio
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/manual/othervm
 *      -Xbootclasspath/a:.
 *      -XX:+UnlockDiagnosticVMOptions
 *      -XX:+WhiteBoxAPI
 *      TestCleaner
 */

// This test requires special hardware; a card must be present

import java.util.WeakHashMap;
import javax.smartcardio.Card;
import javax.smartcardio.CardTerminal;
import java.security.NoSuchAlgorithmException;

import jdk.test.whitebox.WhiteBox;
import jtreg.SkippedException;

/**
 * Rudimentary test to confirm that the cleaning action does not prevent the
 * CardImpl from becoming unreachable and being collected/cleaned.
 */
public class TestCleaner extends Utils {
    static WhiteBox wb;

    public static void main(String[] args) throws Exception {
        CardTerminal terminal = null;
        try {
            terminal = getTerminal(args);
        } catch (NoSuchAlgorithmException e) {
            if ("Error constructing TerminalFactory for PC/SC using SunPCSC".equals(e.getMessage())) {
                // Cause is expected to be a PCSCException
                if ("SCARD_E_NO_SERVICE".equals(e.getCause().getMessage())) {
                    throw new SkippedException("Skipping the test: " +
                            "Unable to construct TerminalFactory");
                } else {
                    throw e;
                }
            }
        }
        if (terminal == null) {
            throw new SkippedException("Skipping the test: " +
                    "no card terminals available");
        }

        while (!terminal.isCardPresent()) {
            System.out.println("*** Insert card!");
            Thread.sleep(1000);
        }

        // Connect using any available protocol
        Card card = terminal.connect("*");
        if (card == null) {
            throw new SkippedException("Skipping the test: " +
                    "no card available");
        }
        System.out.println("card is " + card);

        // Ensure card object can become unreachable
        WeakHashMap<Card,Object> whm = new WeakHashMap<>();
        whm.put(card, new Object());

        System.out.println("Allow card object to be collected");
        card = null;
        terminal = null;

        wb = WhiteBox.getWhiteBox();
        wb.fullGC();
        wb.waitForReferenceProcessing();

        if (whm.size() > 0) {
            throw new RuntimeException("*** TEST FAILED - Card could not be collected");
        }
        System.out.println("Card object collected.");
    }
}
