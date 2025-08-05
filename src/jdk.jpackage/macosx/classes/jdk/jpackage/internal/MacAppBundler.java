/*
 * Copyright (c) 2012, 2025, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.jpackage.internal.StandardBundlerParam.OUTPUT_DIR;
import static jdk.jpackage.internal.StandardBundlerParam.SIGN_BUNDLE;

import java.util.Map;
import java.util.Optional;
import jdk.jpackage.internal.model.ConfigException;
import jdk.jpackage.internal.util.function.ExceptionBox;

public class MacAppBundler extends AppImageBundler {
     public MacAppBundler() {
         setAppImageSupplier((params, output) -> {

             // Order is important!
             final var app = MacFromParams.APPLICATION.fetchFrom(params);
             final BuildEnv env;

             if (StandardBundlerParam.hasPredefinedAppImage(params)) {
                 env = BuildEnvFromParams.BUILD_ENV.fetchFrom(params);
                 final var pkg = MacPackagingPipeline.createSignAppImagePackage(app, env);
                 MacPackagingPipeline.build(Optional.of(pkg)).create().execute(env, pkg, output);
             } else {
                 env = BuildEnv.withAppImageDir(BuildEnvFromParams.BUILD_ENV.fetchFrom(params), output);
                 MacPackagingPipeline.build(Optional.empty())
                         .excludeDirFromCopying(output.getParent())
                         .excludeDirFromCopying(OUTPUT_DIR.fetchFrom(params)).create().execute(env, app);
             }

         });
         setParamsValidator(MacAppBundler::doValidate);
    }

    private static void doValidate(Map<String, ? super Object> params)
            throws ConfigException {

        try {
            MacFromParams.APPLICATION.fetchFrom(params);
        } catch (ExceptionBox ex) {
            if (ex.getCause() instanceof ConfigException cfgEx) {
                throw cfgEx;
            } else {
                throw ex;
            }
        }

        if (StandardBundlerParam.hasPredefinedAppImage(params)) {
            if (!Optional.ofNullable(
                    SIGN_BUNDLE.fetchFrom(params)).orElse(Boolean.FALSE)) {
                throw new ConfigException(
                        I18N.getString("error.app-image.mac-sign.required"),
                        null);
            }
        }
    }
}
