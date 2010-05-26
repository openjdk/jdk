/*
 * Copyright (c) 2005, 2006, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6293769 6294527 6309280
 * @summary test connect() works
 * @author Andreas Sterbenz
 * @ignore requires special hardware
 * @run main/manual TestConnect
 */

import java.io.*;
import java.util.*;

import javax.smartcardio.*;

public class TestConnect extends Utils {

    public static void main(String[] args) throws Exception {
        TerminalFactory factory = TerminalFactory.getInstance("PC/SC", null, "SunPCSC");
        System.out.println(factory);

        List<CardTerminal> terminals = factory.terminals().list();
        System.out.println("Terminals: " + terminals);
        if (terminals.isEmpty()) {
            throw new Exception("No card terminals available");
        }
        CardTerminal terminal = terminals.get(0);

        if (terminal.isCardPresent() == false) {
            System.out.println("*** Insert card");
            if (terminal.waitForCardPresent(20 * 1000) == false) {
                throw new Exception("no card available");
            }
        }
        System.out.println("card present: " + terminal.isCardPresent());

        Card card = terminal.connect("*");
        System.out.println("card: " + card);
        if (card.getProtocol().equals("T=0") == false) {
            throw new Exception("Not T=0 protocol");
        }
        transmit(card);
        card.disconnect(false);

        try {
            transmit(card);
            throw new Exception("transmitted to disconnected card");
        } catch (IllegalStateException e) {
            System.out.println("OK: " + e);
        }

/*      ignore: Solaris bug
        try {
            card = terminal.connect("T=1");
            System.out.println(card);
            throw new Exception("connected via T=1");
        } catch (CardException e) {
            System.out.println("OK: " + e);
        }
*/

        try {
            card = terminal.connect("T=Foo");
            System.out.println(card);
            throw new Exception("connected via T=Foo");
        } catch (IllegalArgumentException e) {
            System.out.println("OK: " + e);
        }

        card = terminal.connect("T=0");
        System.out.println(card);
        if (card.getProtocol().equals("T=0") == false) {
            throw new Exception("Not T=0 protocol");
        }
        transmit(card);
        card.disconnect(true);

        card = terminal.connect("*");
        System.out.println("card: " + card);
        if (card.getProtocol().equals("T=0") == false) {
            throw new Exception("Not T=0 protocol");
        }
        transmit(card);
        card.disconnect(true);
        card.disconnect(true);

        System.out.println("OK.");
    }

    private static void transmit(Card card) throws Exception {
        CardChannel channel = card.getBasicChannel();
        System.out.println("Transmitting...");
        transmitTestCommand(channel);
    }

}
