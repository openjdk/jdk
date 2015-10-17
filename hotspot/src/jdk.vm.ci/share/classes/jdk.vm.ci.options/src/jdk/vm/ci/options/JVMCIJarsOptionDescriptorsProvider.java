/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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
package jdk.vm.ci.options;

import java.io.*;
import java.util.*;
import java.util.jar.*;
import java.util.zip.*;

import jdk.vm.ci.options.OptionsParser.*;

/**
 * Access to the {@link OptionDescriptors} declared by
 * {@code META-INF/services/jdk.vm.ci.options.OptionDescriptors} files in {@code
 * <jre>/lib/jvmci/*.jar}.
 */
class JVMCIJarsOptionDescriptorsProvider implements OptionDescriptorsProvider {

    static final String OptionDescriptorsServiceFile = "META-INF/services/" + OptionDescriptors.class.getName();

    private final Iterator<File> jars;
    private final List<OptionDescriptors> optionsDescriptorsList;

    JVMCIJarsOptionDescriptorsProvider() {
        List<File> jarsList = findJVMCIJars();
        this.jars = jarsList.iterator();
        this.optionsDescriptorsList = new ArrayList<>(jarsList.size() * 3);
    }

    /**
     * Finds the list of JVMCI jars.
     */
    private static List<File> findJVMCIJars() {
        File javaHome = new File(System.getProperty("java.home"));
        File lib = new File(javaHome, "lib");
        File jvmci = new File(lib, "jvmci");

        List<File> jarFiles = new ArrayList<>();
        if (jvmci.exists()) {
            for (String fileName : jvmci.list()) {
                if (fileName.endsWith(".jar")) {
                    File file = new File(jvmci, fileName);
                    if (file.isDirectory()) {
                        continue;
                    }
                    jarFiles.add(file);
                }
            }
        }
        return jarFiles;
    }

    public OptionDescriptor get(String name) {
        // Look up loaded option descriptors first
        for (OptionDescriptors optionDescriptors : optionsDescriptorsList) {
            OptionDescriptor desc = optionDescriptors.get(name);
            if (desc != null) {
                return desc;
            }
        }
        while (jars.hasNext()) {
            File path = jars.next();
            try (JarFile jar = new JarFile(path)) {
                ZipEntry entry = jar.getEntry(OptionDescriptorsServiceFile);
                if (entry != null) {
                    BufferedReader br = new BufferedReader(new InputStreamReader(jar.getInputStream(entry)));
                    String line = null;
                    OptionDescriptor desc = null;
                    while ((line = br.readLine()) != null) {
                        OptionDescriptors options;
                        try {
                            options = (OptionDescriptors) Class.forName(line).newInstance();
                            optionsDescriptorsList.add(options);
                            if (desc == null) {
                                desc = options.get(name);
                            }
                        } catch (Exception e) {
                            throw new InternalError("Error instantiating class " + line + " read from " + path, e);
                        }
                    }
                    if (desc != null) {
                        return desc;
                    }
                }
            } catch (IOException e) {
                throw new InternalError("Error reading " + path, e);
            }
        }
        return null;
    }
}
