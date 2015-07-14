/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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

package jdk.internal.jimage;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ImageModuleDataWriter {
    final byte[] bytes;

    public ImageModuleDataWriter(BasicImageWriter writer,
            Map<String, List<String>> modulePackages) {
        PerfectHashBuilder<String> packageToModule = new PerfectHashBuilder<>(
                new PerfectHashBuilder.Entry<String>().getClass(),
                new PerfectHashBuilder.Bucket<String>().getClass());
        PerfectHashBuilder<List<String>> moduleToPackages = new PerfectHashBuilder<>(
                new PerfectHashBuilder.Entry<List<String>>().getClass(),
                new PerfectHashBuilder.Bucket<List<String>>().getClass());

        modulePackages.entrySet().stream().forEach((entry) -> {
            String moduleName = entry.getKey();
            List<String> packages = entry.getValue();
            packages.stream().forEach((packageName) -> {
                packageToModule.put(packageName, moduleName);
            });

            moduleToPackages.put(moduleName, packages);
        });

        packageToModule.generate();
        moduleToPackages.generate();

        bytes = getBytes(writer, packageToModule, moduleToPackages);
    }

    public static ImageModuleDataWriter buildModuleData(BasicImageWriter writer,
            Map<String, Set<String>> modulePackagesMap) {
        Set<String> modules = modulePackagesMap.keySet();

        Map<String, List<String>> modulePackages = new LinkedHashMap<>();
        modules.stream().sorted().forEach((moduleName) -> {
            List<String> localPackages = modulePackagesMap.get(moduleName).stream()
                    .map(pn -> pn.replace('.', '/'))
                    .sorted()
                    .collect(Collectors.toList());
            modulePackages.put(moduleName, localPackages);
        });

        return new ImageModuleDataWriter(writer, modulePackages);
    }

    public static Map<String, List<String>> toModulePackages(List<String> lines) {
        Map<String, List<String>> modulePackages = new LinkedHashMap<>();

        for (String line : lines) {
            String[] parts = line.split(ImageModuleData.SEPARATOR);
            String moduleName = parts[0];
            List<String> packages = Arrays.asList(Arrays.copyOfRange(parts, 1, parts.length));
            modulePackages.put(moduleName, packages);
        }

        return modulePackages;
    }

    public void addLocation(String name, BasicImageWriter writer) {
        writer.addLocation(ImageModuleData.getModuleDataName(name), 0, 0, bytes.length);
    }

    private byte[] getBytes(BasicImageWriter writer,
            PerfectHashBuilder<String> packageToModule,
            PerfectHashBuilder<List<String>> moduleToPackages) {
        ImageStream stream = new ImageStream(writer.getByteOrder());

        int[] ptmRedirect = packageToModule.getRedirect();
        int[] mtpRedirect = moduleToPackages.getRedirect();
        PerfectHashBuilder.Entry<String>[] ptmOrder = packageToModule.getOrder();
        PerfectHashBuilder.Entry<List<String>>[] mtpOrder = moduleToPackages.getOrder();

        stream.putInt(ptmRedirect.length);
        stream.putInt(mtpRedirect.length);

        for (int value : ptmRedirect) {
            stream.putInt(value);
        }

        for (PerfectHashBuilder.Entry<String> entry : ptmOrder) {
            if (entry != null) {
                stream.putInt(writer.addString(entry.getKey()));
                stream.putInt(writer.addString(entry.getValue()));
            } else {
                stream.putInt(0);
                stream.putInt(0);
            }
        }

        for (int value : mtpRedirect) {
            stream.putInt(value);
        }

        int index = 0;

        for (PerfectHashBuilder.Entry<List<String>> entry : mtpOrder) {
            if (entry != null) {
                int count = entry.getValue().size();
                stream.putInt(writer.addString(entry.getKey()));
                stream.putInt(count);
                stream.putInt(index);
                index += count;
            } else {
                stream.putInt(0);
                stream.putInt(0);
                stream.putInt(0);
            }
        }

        for (PerfectHashBuilder.Entry<List<String>> entry : mtpOrder) {
            if (entry != null) {
                List<String> value = entry.getValue();
                value.stream().forEach((packageName) -> {
                    stream.putInt(writer.addString(packageName));
                });
            }
        }

        return stream.toArray();
    }

    public void writeTo(DataOutputStream out) throws IOException {
         out.write(bytes, 0, bytes.length);
    }

    public int size() {
        return bytes.length;
    }
}
