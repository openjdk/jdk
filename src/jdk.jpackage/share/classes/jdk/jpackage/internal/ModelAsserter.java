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

import static jdk.jpackage.internal.util.function.ExceptionBox.rethrowUnchecked;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import jdk.jpackage.internal.model.AppImageLayout;
import jdk.jpackage.internal.model.Application;
import jdk.jpackage.internal.model.DottedVersion;
import jdk.jpackage.internal.model.Launcher;
import jdk.jpackage.internal.model.Package;
import jdk.jpackage.internal.util.ObjectMapper;
import jdk.jpackage.internal.util.XmlUtils;

final class ModelAsserter {

    static ModelAsserter instance() {
        return INSTANCE.get();
    }

    void notifyFromParams(Map<String,? super Object> params) {

        final var pkg = FromParams.getCurrentPackage(params);

        pkg.ifPresent(this::notifyFromParams);

        if (pkg.isEmpty()) {
            notifyFromParams(FromParams.APPLICATION.findIn(params).orElseThrow());
        }
    }

    void notifyFromParams(Application app) {
        params.app(app);
    }

    void notifyFromParams(Package pkg) {
        params.pkg(pkg);
    }

    void notifyFromOptions(Application app) {
        options.app(app);
    }

    void notifyFromOptions(Package pkg) {
        options.pkg(pkg);
    }

    void assertEquals(Path outputDir) {
        Objects.requireNonNull(outputDir);

        var paramsMap = OM.toMap(params);
        var optionsMap = OM.toMap(options);

        if (!paramsMap.equals(optionsMap)) {
            var paramsXml = outputDir.resolve("params.xml");
            var optionsXml = outputDir.resolve("options.xml");

            try {
                Files.deleteIfExists(paramsXml);
                Files.deleteIfExists(optionsXml);

                XmlUtils.createXml(paramsXml, xml -> {
                    ObjectMapper.store(paramsMap, xml);
                });

                XmlUtils.createXml(optionsXml, xml -> {
                    ObjectMapper.store(optionsMap, xml);
                });
            } catch (Exception ex) {
                rethrowUnchecked(ex);
            }

            throw new AssertionError();
        }
    }

    private ModelAsserter() {
    }


    final static class ModelObjectsSink {

        void app(Application v) {
            app = Objects.requireNonNull(v);
            pkg = null;
        }

        void pkg(Package v) {
            pkg = Objects.requireNonNull(v);
            app = null;
        }

        Application app() {
            return app;
        }

        Package pkg() {
            return pkg;
        }

        private Application app;
        private Package pkg;
    }

    private final ModelObjectsSink params = new ModelObjectsSink();
    private final ModelObjectsSink options = new ModelObjectsSink();

    private final static ObjectMapper OM = ObjectMapper.standard()
            .accessPackageMethods(ModelAsserter.class.getPackage())
            .methods(AppImageLayout.class).invert().exclude("unresolve", "resetRootDirectory").apply()
            .methods(Launcher.class).invert().exclude("executableResource").apply()
            .methods(Application.class).invert().exclude("fileAssociations").apply()
            .methods(Application.class).invert().exclude("launchers").apply()
            .subst(DottedVersion.class, "getComponents", ver -> {
                return List.of(ver.getComponents());
            })
            .subst(Application.class, "runtimeBuilder", app -> {
                return app.runtimeBuilder().map(_ -> "A runtime builder");
            })
            .create();

    private static final ThreadLocal<ModelAsserter> INSTANCE = ThreadLocal.withInitial(ModelAsserter::new);
}
