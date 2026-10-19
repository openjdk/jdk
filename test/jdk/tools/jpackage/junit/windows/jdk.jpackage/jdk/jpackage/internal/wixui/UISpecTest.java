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

package jdk.jpackage.internal.wixui;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class UISpecTest {

    @ParameterizedTest
    @MethodSource
    void test(UIConfig cfg) {
        var uiSpec = UISpec.create(cfg);

        validateCustomDialogSequence(uiSpec.customDialogSequence());
    }

    private static Collection<UIConfig> test() {

        var testCases = new ArrayList<UIConfig>();

        for (boolean withInstallDirChooserDlg : List.of(true, false)) {
            for (boolean withShortcutPromptDlg : List.of(true, false)) {
                for (boolean withLicenseDlg : List.of(true, false)) {
                    testCases.add(UIConfig.build()
                            .withInstallDirChooserDlg(withInstallDirChooserDlg)
                            .withShortcutPromptDlg(withShortcutPromptDlg)
                            .withLicenseDlg(withLicenseDlg)
                            .create());
                }
            }
        }

        return testCases;
    }

    static void validateCustomDialogSequence(Map<DialogPair, Publish> seq) {
        seq.entrySet().stream().map(DialogControl::new).collect(Collectors.toMap(x -> x, x -> x, (a, b) -> {
            throw new AssertionError(String.format(
                    "Dialog [%s] has multiple Publish elements associated with [%s] control", a.host(), a.hostedControl()));
        }));
    }

    record DialogControl(Dialog host, Control hostedControl) {
        DialogControl {
            Objects.requireNonNull(host);
            Objects.requireNonNull(hostedControl);
        }

        DialogControl(DialogPair pair, Publish publish) {
            this(pair.first(), publish.control());
        }

        DialogControl(Map.Entry<DialogPair, Publish> e) {
            this(e.getKey(), e.getValue());
        }
    }
}
