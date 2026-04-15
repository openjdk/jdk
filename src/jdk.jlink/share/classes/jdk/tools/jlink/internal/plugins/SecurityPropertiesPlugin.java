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
package jdk.tools.jlink.internal.plugins;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import jdk.tools.jlink.internal.ResourcePoolEntryFactory;
import jdk.tools.jlink.plugin.PluginException;
import jdk.tools.jlink.plugin.ResourcePool;
import jdk.tools.jlink.plugin.ResourcePoolBuilder;
import jdk.tools.jlink.plugin.ResourcePoolEntry;

/**
 * Creates the java.security configuration file in the output image and
 * overrides the property values with corresponding properties in the
 * specified file.
 */
public class SecurityPropertiesPlugin extends AbstractPlugin {

    private static final String RES = "/java.base/conf/security/java.security";

    // holds properties and values that will be overridden
    private Map<String, String> props = new HashMap<>();

    public SecurityPropertiesPlugin() {
        super("security-properties");
    }

    @Override
    public boolean hasArguments() {
        return true;
    }

    @Override
    public void configure(Map<String, String> config) {
        String file = config.get(getName());
        if (file == null) {
            throw new AssertionError();
        }

        Properties overrideProps = new Properties();
        try (FileInputStream fis = new FileInputStream(file)) {
            overrideProps.load(fis);
        } catch (IOException ioe) {
            throw new IllegalArgumentException(ioe);
        }
        if (overrideProps.containsKey("include")) {
            throw new IllegalArgumentException(
                "the include property is not supported");
        }
        for (String name : overrideProps.stringPropertyNames()) {
            props.put(name, overrideProps.getProperty(name));
        }
        if (props.isEmpty()) {
            throw new IllegalArgumentException("No properties in " + file);
        }
    }

    @Override
    public ResourcePool transform(ResourcePool in, ResourcePoolBuilder out) {
        in.transformAndCopy(res -> {
            if (res.type().equals(ResourcePoolEntry.Type.CONFIG) &&
                res.path().equals(RES)) {
                    byte[] props = processProperties(res.content());
                    return ResourcePoolEntryFactory.create(res, props);
            }
            return res;
        }, out);
        return out.build();
    }

    private byte[] processProperties(InputStream content) {
        List<String> lines = new ArrayList<>();

        // read in contents of java.security file into separate list,
        // replacing values of overridden properties as we go
        try (InputStreamReader isr = new InputStreamReader(content);
                BufferedReader br = new BufferedReader(isr)) {
            String line = br.readLine();
            while (line != null) {
                if (!line.isEmpty() && line.charAt(0) != '#') {
                    // assume "=" used as delimiter
                    int index = line.indexOf('=');
                    if (index != -1) {
                        String propName = line.substring(0, index);
                        String propValue = props.remove(propName.trim());
                        if (propValue != null) {
                            // skip multi-line values
                            while (line.endsWith("\\")) {
                                line = br.readLine();
                            }
                            line = propName + "=" + propValue;
                        }
                    }
                }
                lines.add(line);
                line = br.readLine();
            }
        } catch (Exception e) {
            throw new PluginException(e);
        }
        // add user-defined properties at end
        props.forEach((k, v) -> lines.add(k + "=" + v));

        // write contents of list to byte array
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (OutputStreamWriter osw = new OutputStreamWriter(baos);
             BufferedWriter bw = new BufferedWriter(osw)) {
            for (CharSequence line: lines) {
                bw.append(line);
                bw.newLine();
            }
        } catch (Exception e) {
            throw new PluginException(e);
        }
        return baos.toByteArray();
    }
}
