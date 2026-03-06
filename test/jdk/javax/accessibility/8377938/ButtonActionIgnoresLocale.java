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
 * @bug 8377938
 * @summary AbstractButton should not localize action description
 * @run main ButtonActionIgnoresLocale
 */

import javax.accessibility.AccessibleAction;
import javax.swing.JButton;
import javax.swing.SwingUtilities;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class ButtonActionIgnoresLocale {
    public static void main(String[] args) throws ExecutionException, InterruptedException {
        Locale.setDefault(Locale.forLanguageTag("de-DE"));
        CompletableFuture<String> axActionDesc = new CompletableFuture<>();

        SwingUtilities.invokeLater(() -> {
            JButton b = new JButton();
            String s = b.getAccessibleContext().getAccessibleAction().
                    getAccessibleActionDescription(0);
            axActionDesc.complete(s);
        });

        String s = axActionDesc.get();
        System.out.println("Action description: " + s);
        if (!AccessibleAction.CLICK.equals(s)) {
            throw new RuntimeException("Test failed.");
        }
    }
}
