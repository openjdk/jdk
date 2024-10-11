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
 * @run driver common.config.ConfigFileTest 0 // verifies jaxp.properties
 * @run driver common.config.ConfigFileTest 1 // verifies jaxp-strict.properties.template
 */
public class ConfigFileTest {
    // system property for custom configuration file
    static final String SP_CONFIG = "java.xml.config.file";
    // target directory
    static String TEST_DIR = System.getProperty("test.classes");

    // properties in the configuration file
    String[] keys = {
        "jdk.xml.enableExtensionFunctions",
        "jdk.xml.overrideDefaultParser",
        "jdk.xml.jdkcatalog.resolve",
        "jdk.xml.dtd.support",
        "jdk.xml.entityExpansionLimit",
        "jdk.xml.totalEntitySizeLimit",
        "jdk.xml.maxGeneralEntitySizeLimit",
        "jdk.xml.maxParameterEntitySizeLimit",
        "jdk.xml.entityReplacementLimit",
        "jdk.xml.elementAttributeLimit",
        "jdk.xml.maxOccurLimit",
        "jdk.xml.maxElementDepth",
        "jdk.xml.maxXMLNameLimit",
        "jdk.xml.xpathExprGrpLimit",
        "jdk.xml.xpathExprOpLimit",
        "jdk.xml.xpathTotalOpLimit"};

    // type of properties
    boolean[] propertyIsFeature ={true, true, false, false, false, false,
        false, false, false, false, false, false, false, false, false, false};

    // values from jaxp-strict.properties.template
    String[] strictValues ={"false", "false", "strict", "allow", "2500", "100000",
        "100000", "15000", "100000", "10000", "5000", "0", "1000", "10", "100", "10000"};

    // values from jaxp.properties, as of JDK 23
    String[] defaultValues ={"true", "false", "continue", "allow", "64000", "50000000",
        "0", "1000000", "3000000", "10000", "5000", "0", "1000", "10", "100", "10000"};

    public static void main(String args[]) throws Exception {
        new ConfigFileTest().run(args[0]);
    }

    public void run(String index) throws Exception {
        String conf = System.getProperty("java.home") + "/conf/";
        if (index.equals("0")) {
            verifyConfig(conf + CONFIG_DEFAULT, defaultValues);
        } else {
            Path config = Paths.get(TEST_DIR, CONFIG_STRICT);
            Files.copy(Paths.get(conf, CONFIG_TEMPLATE_STRICT), config);
            verifyConfig(config.toString(), strictValues);
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
        IntStream.range(0, keys.length).forEach(i -> {
            if (propertyIsFeature[i]) {
                TestBase.Assert.assertEquals(tf.getFeature(keys[i]), Boolean.parseBoolean(values[i]));
            } else {
                TestBase.Assert.assertEquals(tf.getAttribute(keys[i]), values[i]);
            }
        });
        System.clearProperty(SP_CONFIG);
    }
}
