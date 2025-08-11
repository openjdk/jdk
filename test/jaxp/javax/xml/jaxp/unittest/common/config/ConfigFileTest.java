/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package common.config;

import common.util.TestBase;
import static common.util.TestBase.CONFIG_DEFAULT;
import static common.util.TestBase.CONFIG_STRICT;
import static common.util.TestBase.CONFIG_TEMPLATE_STRICT;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.IntStream;
import javax.xml.transform.TransformerFactory;

/**
 * @test @bug 8330542
 * @summary verifies the default JAXP configuration file jaxp.properties and
 * strict template jaxp-strict.properties.template.
 * @library /javax/xml/jaxp/libs /javax/xml/jaxp/unittest
 * @modules java.xml/jdk.xml.internal
 *
 * @run driver common.config.ConfigFileTest 1 // verifies jaxp.properties in JDK 24 and later
 * @run driver common.config.ConfigFileTest 2 // verifies jaxp-strict.properties.template
 */
public class ConfigFileTest extends ImplProperties {
    // system property for custom configuration file
    static final String SP_CONFIG = "java.xml.config.file";
    // target directory
    static String TEST_DIR = System.getProperty("test.classes");

    public static void main(String args[]) throws Exception {
        new ConfigFileTest().run(args[0]);
    }

    public void run(String index) throws Exception {
        String conf = System.getProperty("java.home") + "/conf/";
        int i = Integer.parseInt(index);
        switch (i) {
            case 0: // JDK 23 and older
                // add compat template after the JEP
                break;
            case 1: // JDK 24
                verifyConfig(conf + CONFIG_DEFAULT, PROPERTY_VALUE[PROPERTY_VALUE_JDK24]);
                break;
            case 2: // strict template, since JDK 23
                Path configStrict = Paths.get(TEST_DIR, CONFIG_STRICT);
                Files.copy(Paths.get(conf, CONFIG_TEMPLATE_STRICT), configStrict);
                verifyConfig(configStrict.toString(), PROPERTY_VALUE[PROPERTY_VALUE_JDK23STRICT]);
                break;
        }
    }

    /**
     * Verifies a configuration file by iterating through its property settings.
     * @param filename the configuration file
     * @param values expected values in the configuration file
     */
    private void verifyConfig(String filename, String[] values) {
        System.setProperty(SP_CONFIG, filename);

        TransformerFactory tf = TransformerFactory.newInstance();
        IntStream.range(0, PROPERTY_KEYS.length).forEach(i -> {
            if (PROPERTY_TYPE[i] == PropertyType.BOOLEAN) {
                TestBase.Assert.assertEquals(tf.getFeature(PROPERTY_KEYS[i]), Boolean.parseBoolean(values[i]));
            } else {
                TestBase.Assert.assertEquals(tf.getAttribute(PROPERTY_KEYS[i]), values[i]);
            }
        });
        System.clearProperty(SP_CONFIG);
    }
}
