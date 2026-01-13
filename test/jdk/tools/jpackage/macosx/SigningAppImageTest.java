/*
 * Copyright (c) 2019, 2026, Oracle and/or its affiliates. All rights reserved.
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import jdk.jpackage.test.AdditionalLauncher;
import jdk.jpackage.test.Annotations.ParameterSupplier;
import jdk.jpackage.test.Annotations.Test;
import jdk.jpackage.test.JPackageCommand;
import jdk.jpackage.test.MacHelper.SignKeyOption;
import jdk.jpackage.test.MacHelper.SignKeyOptionWithKeychain;
import jdk.jpackage.test.MacSign;
import jdk.jpackage.test.MacSignVerify;

/**
 * Tests signing of an app image.
 *
 * <p>
 * Prerequisites: Keychains with self-signed certificates as specified in
 * {@link SigningBase.StandardKeychain#MAIN} and
 * {@link SigningBase.StandardKeychain#SINGLE}.
 */

/*
 * @test
 * @summary jpackage with --type app-image --mac-sign
 * @library /test/jdk/tools/jpackage/helpers
 * @build jdk.jpackage.test.*
 * @compile -Xlint:all -Werror SigningBase.java
 * @compile -Xlint:all -Werror SigningAppImageTest.java
 * @requires (jpackage.test.MacSignTests == "run")
 * @run main/othervm/timeout=1440 -Xmx512m jdk.jpackage.test.Main
 *  --jpt-run=SigningAppImageTest
 *  --jpt-before-run=SigningBase.verifySignTestEnvReady
 */
public class SigningAppImageTest {

    @Test
    @ParameterSupplier
    public static void test(SignKeyOptionWithKeychain sign) {

        var cmd = JPackageCommand.helloAppImage();

        var testAL = new AdditionalLauncher("testAL");
        testAL.applyTo(cmd);
        cmd.executeAndAssertHelloAppImageCreated();

        MacSign.withKeychain(keychain -> {
            sign.addTo(cmd);
            cmd.executeAndAssertHelloAppImageCreated();
            MacSignVerify.verifyAppImageSigned(cmd, sign.certRequest());
        }, sign.keychain());
    }

    public static Collection<Object[]> test() {

        List<SignKeyOptionWithKeychain> data = new ArrayList<>();

        for (var certRequest : List.of(
                SigningBase.StandardCertificateRequest.CODESIGN,
                SigningBase.StandardCertificateRequest.CODESIGN_UNICODE
        )) {
            for (var signIdentityType : SignKeyOption.Type.defaultValues()) {
                data.add(new SignKeyOptionWithKeychain(
                        signIdentityType,
                        certRequest,
                        SigningBase.StandardKeychain.MAIN.keychain()));
            }
        }

        return data.stream().map(v -> {
            return new Object[] {v};
        }).toList();
    }
}
