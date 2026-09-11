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
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import jdk.tools.jlink.internal.ResourcePoolEntryFactory;
import jdk.tools.jlink.plugin.PluginException;
import jdk.tools.jlink.plugin.ResourcePool;
import jdk.tools.jlink.plugin.ResourcePoolBuilder;
import jdk.tools.jlink.plugin.ResourcePoolEntry;

/**
 * Security properties plugin.
 *
 * Creates the java.security configuration file in the output image and
 * adds or overrides properties from the specified file.
 */
public class SecurityPropertiesPlugin extends AbstractPlugin {

    private static final String RES = "/java.base/conf/security/java.security";

    // holds additional properties to be added or overridden
    private Properties extraProps;

    public SecurityPropertiesPlugin() {
        super("security-properties");
    }

    @Override
    public boolean hasArguments() {
        return true;
    }

    @Override
    public boolean hasRawArgument() {
        return true;
    }

    @Override
    public void configure(Map<String, String> config) {
        String propsFile = config.get(getName());
        if (propsFile == null) {
            throw new AssertionError();
        }

        extraProps = new Properties();
        try (FileInputStream fis = new FileInputStream(propsFile)) {
            extraProps.load(fis);
        } catch (IOException ioe) {
            throw new IllegalArgumentException(ioe);
        }
        if (extraProps.isEmpty()) {
            throw new IllegalArgumentException("No properties in " + propsFile);
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
        Set<String> keys = new HashSet<>();

        // Read in contents of java.security file into separate list,
        // replacing values of overridden properties as we go.
        // Use ISO_8859_1 to be consistent with java.security file loading.
        try (InputStreamReader isr = new InputStreamReader(content,
                    StandardCharsets.ISO_8859_1);
                BufferedReader br = new BufferedReader(isr)) {
            String line = br.readLine();
            while (line != null) {
                // add comments and blank lines as-is
                if (isBlankOrComment(line)) {
                    lines.add(line);
                    line = br.readLine();
                    continue;
                }

                StringBuilder sb = new StringBuilder(line);
                while (lineContinues(line)) {
                    // multi-lined value, add lines until end of value
                    line = br.readLine();
                    if (line == null) {
                        // end of stream
                        break;
                    }
                    sb.append("\n").append(line);
                }

                // use Properties.load() to ensure property is parsed correctly
                ByteArrayInputStream bais = new ByteArrayInputStream(
                    sb.toString().getBytes(StandardCharsets.ISO_8859_1));
                Properties props = new Properties();
                props.load(bais);
                Set<String> propNames = props.stringPropertyNames();
                // should only be one property
                if (propNames.size() != 1) {
                    throw new PluginException("Parsing error: " + propNames);
                }

                // check for duplicate properties and include statements
                String propName = propNames.iterator().next();
                if (keys.contains(propName)) {
                    throw new PluginException("Parsing error, " +
                        "duplicate property: " + propName);
                }
                if (propName.equals("include")) {
                    throw new PluginException("Parsing error, " +
                        "include statement disallowed");
                }
                keys.add(propName);

                // check if property should be overridden
                String propValue = (String) extraProps.remove(propName);
                if (propValue != null) {
                    // override value
                    lines.add(line(propName, propValue));
                } else {
                    lines.add(line(propName, props.getProperty(propName)));
                }
                line = br.readLine();
            }
        } catch (Exception e) {
            throw new PluginException(e);
        }

        // extract and remove include property if it exists
        String includeValue = (String) extraProps.remove("include");

        // add user-defined properties at end
        for (String propName : extraProps.stringPropertyNames()) {
            lines.add(line(propName, extraProps.getProperty(propName)));
        };

        // add include property at end if it has been specified and use
        // space character as delimiter
        if (includeValue != null) {
            lines.add("include " + convert(includeValue, false));
        }

        // Write contents of list to byte array. Use ISO_8859_1 to be
        // consistent with java.security file loading.
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (OutputStreamWriter osw = new OutputStreamWriter(baos,
                 StandardCharsets.ISO_8859_1);
             BufferedWriter bw = new BufferedWriter(osw)) {
            for (CharSequence line: lines) {
                bw.append(line);
                bw.newLine();
            }
            bw.flush();
        } catch (Exception e) {
            throw new PluginException(e);
        }
        return baos.toByteArray();
    }

    /**
     * Returns true if line is empty, only contains whitespace, or has an
     * ASCII '#' or '!' as its first non-whitespace character. Whitespace
     * characters are as specified by java.util.Properties.
     */
    private static boolean isBlankOrComment(String line) {
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '#' || c == '!') {
                return true;
            } else if (c == ' ' || c == '\t' || c == '\f') {
                continue;
            } else if (c == '\n' || c == '\r') {
                return true;
            } else {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns true if line ends in an odd number of contiguous backslashes.
     */
    private static boolean lineContinues(String line) {
        int backslashes = 0;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '\\') {
                backslashes++;
            } else {
                backslashes = 0;
            }
        }
        return (backslashes % 2 == 0) ? false : true;
    }

    /**
     * Create line to add to properties file.
     */
    private static String line(String name, String value) {
        return convert(name, true) + "=" + convert(value, false);
    }

    /**
     * Converts unicodes to encoded &#92;uxxxx and escapes
     * special characters with a preceding slash.
     *
     * This method was copied from java.util.Properties.saveConvert().
     */          
    private static String convert(String theString,
                                  boolean escapeSpace) {
        int len = theString.length();
        int bufLen = len * 2;
        if (bufLen < 0) {
            bufLen = Integer.MAX_VALUE;
        }
        StringBuilder outBuffer = new StringBuilder(bufLen);
        HexFormat hex = HexFormat.of().withUpperCase();
        for(int x=0; x<len; x++) {
            char aChar = theString.charAt(x);
            // Handle common case first, selecting largest block that
            // avoids the specials below
            if ((aChar > 61) && (aChar < 127)) {
                if (aChar == '\\') {
                    outBuffer.append('\\'); outBuffer.append('\\');
                    continue;
                }
                outBuffer.append(aChar);
                continue;
            }
            switch(aChar) {
                case ' ':
                    if (x == 0 || escapeSpace)
                        outBuffer.append('\\');
                    outBuffer.append(' ');
                    break;
                case '\t':outBuffer.append('\\'); outBuffer.append('t');
                          break;
                case '\n':outBuffer.append('\\'); outBuffer.append('n');
                          break;
                case '\r':outBuffer.append('\\'); outBuffer.append('r');
                          break;
                case '\f':outBuffer.append('\\'); outBuffer.append('f');
                          break;
                case '=': // Fall through
                case ':': // Fall through
                case '#': // Fall through
                case '!':
                    outBuffer.append('\\'); outBuffer.append(aChar);
                    break;
                default:
                    if (((aChar < 0x0020) || (aChar > 0x007e))) {
                        outBuffer.append("\\u");
                        outBuffer.append(hex.toHexDigits(aChar));
                    } else {
                        outBuffer.append(aChar);
                    }
            }
        }
        return outBuffer.toString();
    }
}
