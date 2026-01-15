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

package jdk.jpackage.internal;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;

import jdk.jpackage.internal.resources.ResourceLocator;

final class MacDmgLicense {

    public static void prepareLicensePListFile(Path licenseFile, Path licensePListFile)
            throws IOException {
        byte[] licenseContentOriginal =
                Files.readAllBytes(licenseFile);
        String licenseInBase64 =
                Base64.getEncoder().encodeToString(licenseContentOriginal);

        Map<String, String> data = new HashMap<>();
        data.put("APPLICATION_LICENSE_TEXT", licenseInBase64);
        data.put("STR_DATA_ENGLISH",
                getSTRData("English", Locale.ENGLISH, "MacRoman"));
        data.put("STR_DATA_GERMAN",
                getSTRData("German", Locale.GERMAN, "MacRoman"));
        data.put("STR_DATA_JAPANESE",
                getSTRData("Japanese", Locale.JAPANESE, "Shift_JIS"));
        data.put("STR_DATA_SIMPLIFIED_CHINESE",
                getSTRData("Simplified Chinese", Locale.SIMPLIFIED_CHINESE, "GB2312"));

        new OverridableResource(DEFAULT_LICENSE_PLIST, ResourceLocator.class)
                .setCategory(I18N.getString("resource.license-setup"))
                .setSubstitutionData(data)
                .saveToFile(licensePListFile);
    }

    private static void writeSTRDataString(ByteArrayOutputStream bos,
                String str, String charset) {
        byte [] bytes = str.getBytes(Charset.forName(charset));
        byte [] bytesLength = {(byte)bytes.length};
        bos.writeBytes(bytesLength);
        bos.writeBytes(bytes);
    }

    // Returns base64 decoded STR section data.
    // Strings should be in following order:
    // Language, message.dmg.license.button.agree,
    // message.dmg.license.button.disagree, message.dmg.license.button.print
    // message.dmg.license.button.save, message.dmg.license.message
    // STR section data encoded:
    // Number of strings in the list (unsigned 16-bit integer, big endian): 6
    // A sequence of strings prefixed with string length (unsigned 8-bit integer)
    // Note: Language should not be translated.
    private static String getSTRData(String language, Locale locale, String charset) {
        ResourceBundle bundle = ResourceBundle.getBundle(
                "jdk.jpackage.internal.resources.MacResources", locale);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();

        byte [] numberOfStrings = {0x00, 0x06}; // Always 6
        bos.writeBytes(numberOfStrings);

        writeSTRDataString(bos, language, charset);
        writeSTRDataString(bos, bundle.getString("message.dmg.license.button.agree"), charset);
        writeSTRDataString(bos, bundle.getString("message.dmg.license.button.disagree"), charset);
        writeSTRDataString(bos, bundle.getString("message.dmg.license.button.print"), charset);
        writeSTRDataString(bos, bundle.getString("message.dmg.license.button.save"), charset);
        writeSTRDataString(bos, bundle.getString("message.dmg.license.message"), charset);

        return Base64.getEncoder().encodeToString(bos.toByteArray());
    }

    private static final String DEFAULT_LICENSE_PLIST = "lic_template.plist";
}
