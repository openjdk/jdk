/*
 * Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import jdk.jpackage.test.TKit;
import jdk.jpackage.test.Executor;

import jdk.jpackage.internal.MacCertificate;

public class SigningCheck {

    public static void checkCertificates(int certIndex) {
        if (!SigningBase.isDevNameDefault()) {
            // Do not validate user supplied certificates.
            // User supplied certs whose trust is set to "Use System Defaults"
            // will not be listed as trusted by dump-trust-settings, so we
            // cannot verify them completely.
            return;
        }

        // Index can be -1 for unsigned tests, but we still skipping test
        // if machine is not configured for signing testing, so default it to
        // SigningBase.DEFAULT_INDEX
        if (certIndex <= -1) {
            certIndex = SigningBase.DEFAULT_INDEX;
        }

        String key = MacCertificate.findCertificateKey(null,
                        SigningBase.getAppCert(certIndex),
                        SigningBase.getKeyChain());
        validateCertificate(key);
        validateCertificateTrust(SigningBase.getAppCert(certIndex));

        key = MacCertificate.findCertificateKey(null,
                SigningBase.getInstallerCert(certIndex),
                SigningBase.getKeyChain());
        validateCertificate(key);
        validateCertificateTrust(SigningBase.getInstallerCert(certIndex));
    }

    private static void validateCertificate(String key) {
        if (key != null) {
            MacCertificate certificate = new MacCertificate(key);
            if (!certificate.isValid()) {
                TKit.throwSkippedException("Certifcate expired: " + key);
            } else {
                return;
            }
        }

        TKit.throwSkippedException("Cannot find required certifciates: " + key);
    }

    private static void validateCertificateTrust(String name) {
        // Certificates using the default user name must be trusted by user.
        List<String> result = new Executor()
                .setExecutable("/usr/bin/security")
                .addArguments("dump-trust-settings")
                .executeWithoutExitCodeCheckAndGetOutput();
        result.stream().forEachOrdered(TKit::trace);
        TKit.assertTextStream(name)
                .predicate((line, what) -> line.trim().endsWith(what))
                .orElseThrow(() -> TKit.throwSkippedException(
                        "Certifcate not trusted by current user: " + name))
                .apply(result.stream());
    }

}
