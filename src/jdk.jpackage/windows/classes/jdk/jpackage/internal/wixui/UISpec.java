/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

import static jdk.jpackage.internal.wixui.CustomDialog.ShortcutPromptDlg;
import static jdk.jpackage.internal.wixui.ShowActionSuppresser.suppressShowAction;
import static jdk.jpackage.internal.wixui.WixDialog.InstallDirDlg;
import static jdk.jpackage.internal.wixui.WixDialog.LicenseAgreementDlg;
import static jdk.jpackage.internal.wixui.WixDialog.ProgressDlg;
import static jdk.jpackage.internal.wixui.WixDialog.VerifyReadyDlg;
import static jdk.jpackage.internal.wixui.WixDialog.WelcomeDlg;
import static jdk.jpackage.internal.wixui.WixDialog.WelcomeEulaDlg;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * UI spec.
 * <p>
 * UI is based on one of the standard WiX UIs with optional alterations.
 */
public record UISpec(
        WixUI wixUI,
        Map<String, String> wixVariables,
        Map<DialogPair, Publish> customDialogSequence,
        Collection<ShowActionSuppresser> hideDialogs) {

    public UISpec {
        Objects.requireNonNull(wixUI);
        Objects.requireNonNull(wixVariables);
        Objects.requireNonNull(customDialogSequence);
        Objects.requireNonNull(hideDialogs);
    }

    static Builder build(WixUI wixUI) {
        return new Builder().wixUI(wixUI);
    }

    static final class Builder {

        private Builder() {
        }

        UISpec create() {
            return new UISpec(
                    wixUI,
                    Optional.ofNullable(wixVariables).map(Collections::unmodifiableMap).orElseGet(Map::of),
                    Optional.ofNullable(customDialogSequence).map(Collections::unmodifiableMap).orElseGet(Map::of),
                    Optional.ofNullable(hideDialogs).map(List::copyOf).orElseGet(List::of));
        }

        Builder wixUI(WixUI v) {
            wixUI = v;
            return this;
        }

        Builder setWixVariable(String name, String value) {
            wixVariables.put(Objects.requireNonNull(name), Objects.requireNonNull(value));
            return this;
        }

        Builder customDialogSequence(Map<DialogPair, Publish> v) {
            customDialogSequence = v;
            return this;
        }

        Builder hideDialogs(Collection<ShowActionSuppresser> v) {
            hideDialogs = v;
            return this;
        }

        Builder hideDialogs(ShowActionSuppresser... v) {
            return hideDialogs(List.of(v));
        }

        private WixUI wixUI;
        private final Map<String, String> wixVariables = new HashMap<>();
        private Map<DialogPair, Publish> customDialogSequence;
        private Collection<ShowActionSuppresser> hideDialogs;
    }

    public static UISpec create(UIConfig cfg) {
        Objects.requireNonNull(cfg);
        return Optional.ofNullable(DEFAULT_SPECS.get(cfg)).map(Supplier::get).orElseGet(() -> {
            return createCustom(cfg);
        });
    }

    private static UISpec createCustom(UIConfig cfg) {
        Objects.requireNonNull(cfg);

        var dialogs = installDirUiDialogs(cfg);
        var dialogPairs = toDialogPairs(dialogs);

        var customDialogSequence = overrideInstallDirDialogSequence().stream().filter(e -> {
            return dialogPairs.contains(e.getKey());
        }).collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));

        var uiSpec = build(WixUI.INSTALL_DIR).customDialogSequence(customDialogSequence);

        var it = dialogs.iterator();
        do {
            if (it.next().equals(InstallDirDlg)) {
                uiSpec.setWixVariable("JpAfterInstallDirDlg", it.next().id());
            }
        } while (it.hasNext());

        return uiSpec.create();
    }

    private static void createPairs(
            BiConsumer<DialogPair, Publish> sink,
            Dialog first,
            Dialog second,
            Publish publishNext,
            Publish publishPrev) {

        createPairNext(sink, first, second, publishNext);
        createPairBack(sink, second, first, publishPrev);
    }

    private static void createPairs(
            BiConsumer<DialogPair, Publish> sink,
            Dialog first,
            Dialog second,
            Publish publish) {
        createPairs(sink, first, second, publish, publish);
    }

    private static void createPairNext(
            BiConsumer<DialogPair, Publish> sink,
            Dialog first,
            Dialog second,
            Publish publish) {

        var pair = new DialogPair(first, second);

        sink.accept(pair, publish.toBuilder().next().create());
    }

    private static void createPairBack(
            BiConsumer<DialogPair, Publish> sink,
            Dialog first,
            Dialog second,
            Publish publish) {

        var pair = new DialogPair(first, second);

        sink.accept(pair, publish.toBuilder().back().create());
    }

    private static Collection<DialogPair> toDialogPairs(List<Dialog> dialogs) {
        if (dialogs.size() < 2) {
            throw new IllegalArgumentException();
        }

        var pairs = new ArrayList<DialogPair>();
        var it = dialogs.listIterator();
        var prev = it.next();
        do {
            var next = it.next();
            var pair = new DialogPair(prev, next);
            pairs.add(pair);
            pairs.add(pair.flip());
            prev = next;
        } while (it.hasNext());

        return pairs;
    }

    private static List<Dialog> installDirUiDialogs(UIConfig cfg) {
        var dialogs = new ArrayList<Dialog>();

        dialogs.add(WelcomeDlg);

        if (cfg.isWithLicenseDlg()) {
            dialogs.add(LicenseAgreementDlg);
        }

        if (cfg.isWithInstallDirChooserDlg()) {
            dialogs.add(InstallDirDlg);
        }

        if (cfg.isWithShortcutPromptDlg()) {
            dialogs.add(ShortcutPromptDlg);
        }

        dialogs.add(VerifyReadyDlg);

        return dialogs;
    }

    private static Collection<Map.Entry<DialogPair, Publish>> overrideInstallDirDialogSequence() {

        List<Map.Entry<DialogPair, Publish>> entries = new ArrayList<>();

        BiConsumer<DialogPair, Publish> acc = (pair, publish) -> {
            entries.add(Map.entry(pair, publish));
        };

        // Order is a "weight" of action. If there are multiple
        // "NewDialog" action for the same dialog Id, MSI would pick the one
        // with higher order value. In WixUI_InstallDir dialog sequence the
        // highest order value is 4. InstallDirNotEmptyDlg adds NewDialog
        // action with order 5. Setting order to 6 for all
        // actions configured in this function would make them executed
        // instead of corresponding default actions defined in
        // WixUI_InstallDir dialog sequence.
        var order = 6;

        // Based on WixUI_InstallDir.wxs
        var backFromVerifyReadyDlg = Publish.build().condition(CONDITION_NOT_INSTALLED).order(order).create();
        var uncondinal = Publish.build().condition(CONDITION_ALWAYS).create();
        var ifNotIstalled = Publish.build().condition(CONDITION_NOT_INSTALLED).order(order).create();
        var ifLicenseAccepted = Publish.build().condition("LicenseAccepted = \"1\"").order(order).create();

        // Define all alternative transitions:
        //  - Skip standard license dialog
        //  - Insert shortcut prompt dialog after the standard install dir dialog
        //  - Replace the standard install dir dialog with the shortcut prompt dialog

        createPairs(acc, WelcomeDlg, InstallDirDlg, ifNotIstalled);
        createPairs(acc, WelcomeDlg, VerifyReadyDlg, ifNotIstalled);
        createPairs(acc, WelcomeDlg, ShortcutPromptDlg, ifNotIstalled);

        createPairs(acc, LicenseAgreementDlg, ShortcutPromptDlg, ifLicenseAccepted, uncondinal);
        createPairs(acc, LicenseAgreementDlg, VerifyReadyDlg, ifLicenseAccepted, backFromVerifyReadyDlg);

        createPairs(acc, InstallDirDlg, ShortcutPromptDlg, uncondinal);

        createPairs(acc, ShortcutPromptDlg, VerifyReadyDlg, uncondinal, backFromVerifyReadyDlg);

        return entries;
    }

    private static final Map<UIConfig, Supplier<UISpec>> DEFAULT_SPECS;

    private static final String CONDITION_ALWAYS = "1";
    private static final String CONDITION_NOT_INSTALLED = "NOT Installed";

    static {

        var specs = new HashMap<UIConfig, Supplier<UISpec>>();

        // Verbatim WiX "Minimal" dialog set.
        specs.put(UIConfig.build()
                .withLicenseDlg()
                .create(), () -> {
                    return build(WixUI.MINIMAL).create();
                });

        // Standard WiX "Minimal" dialog set without the license dialog.
        // The license dialog is removed by overriding the default "Show"
        // action with the condition that always evaluates to "FALSE".
        specs.put(UIConfig.build()
                .create(), () -> {
                    return build(WixUI.MINIMAL).hideDialogs(suppressShowAction(WelcomeEulaDlg).after(ProgressDlg)).create();
                });

        DEFAULT_SPECS = Collections.unmodifiableMap(specs);
    }
}
