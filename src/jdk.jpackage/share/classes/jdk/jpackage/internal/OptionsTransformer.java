/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.jpackage.internal;

import static jdk.jpackage.internal.cli.StandardOption.ADDITIONAL_LAUNCHERS;
import static jdk.jpackage.internal.cli.StandardOption.ICON;
import static jdk.jpackage.internal.cli.StandardOption.APPCLASS;
import static jdk.jpackage.internal.cli.StandardOption.APP_VERSION;
import static jdk.jpackage.internal.cli.StandardOption.DESCRIPTION;
import static jdk.jpackage.internal.cli.StandardOption.LAUNCHER_AS_SERVICE;
import static jdk.jpackage.internal.cli.StandardOption.NAME;
import static jdk.jpackage.internal.cli.StandardOption.PREDEFINED_APP_IMAGE;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import jdk.jpackage.internal.cli.OptionIdentifier;
import jdk.jpackage.internal.cli.Options;
import jdk.jpackage.internal.model.ApplicationLayout;
import jdk.jpackage.internal.model.ExternalApplication;

record OptionsTransformer(Options mainOptions, Optional<ExternalApplication> externalApp) {

    OptionsTransformer {
        Objects.requireNonNull(mainOptions);
        Objects.requireNonNull(externalApp);
    }

    OptionsTransformer(Options mainOptions, ApplicationLayout appLayout) {
        this(mainOptions, PREDEFINED_APP_IMAGE.findIn(mainOptions).map(appLayout::resolveAt).map(AppImageFile::load));
    }

    Options appOptions() {
        return externalApp.map(ea -> {
            var overrideOptions = Map.<OptionIdentifier, Object>of(
                    NAME.id(), ea.getAppName(),
                    APPCLASS.id(), ea.getMainClass(),
                    APP_VERSION.id(), ea.getAppVersion(),
                    ADDITIONAL_LAUNCHERS.id(), ea.getAddLaunchers().stream().map(li -> {
                        return Options.concat(li.extra(), Options.of(Map.of(
                                NAME.id(), li.name(),
                                LAUNCHER_AS_SERVICE.id(), li.service(),
                                // This should prevent the code building the Launcher instance
                                // from the Options object from trying to create a startup info object.
                                PREDEFINED_APP_IMAGE.id(), PREDEFINED_APP_IMAGE.getFrom(mainOptions),
                                //
                                // For backward compatibility, descriptions of the additional
                                // launchers in the predefined app image will be set to
                                // the application description, if available, or to the name
                                // of the main launcher in the predefined app image.
                                //
                                // All launchers in the predefined app image will have the same description.
                                // This is wrong and should be revised.
                                //
                                DESCRIPTION.id(), DESCRIPTION.findIn(mainOptions).orElseGet(ea::getLauncherName)
                            )));
                    }).toList()
            );
            return Options.concat(
                    Options.of(overrideOptions),
                    ea.getExtra(),
                    // Remove icon if any from the application/launcher options.
                    // If the icon is specified in the main options, it for the installer.
                    mainOptions.copyWithout(ICON)
            );
        }).orElse(mainOptions);
    }
}
