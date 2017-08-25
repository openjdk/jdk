/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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

package jdk.test.lib.artifacts;

import java.io.FileNotFoundException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class JibArtifactManager implements ArtifactManager {
    private static String jibVersion = "1.0";
    private Object installerObject;

    private JibArtifactManager(Object o) {
        installerObject = o;
    }

    public static JibArtifactManager newInstance() throws ClassNotFoundException {
        try {
            Class jibServiceFactory = Class.forName("com.oracle.jib.api.JibServiceFactory");
            Object jibArtifactInstaller = jibServiceFactory.getMethod("createJibArtifactInstaller").invoke(null);
            return new JibArtifactManager(jibArtifactInstaller);
        } catch (Exception e) {
            throw new ClassNotFoundException();
        }
    }

    private Path download(String jibVersion, HashMap<String, Object> artifactDescription) throws Exception {
        return invokeInstallerMethod("download", jibVersion, artifactDescription);
    }

    private Path install(String jibVersion, HashMap<String, Object> artifactDescription) throws Exception {
        return invokeInstallerMethod("install", jibVersion, artifactDescription);
    }

    private Path invokeInstallerMethod(String methodName, String jibVersion, HashMap<String, Object> artifactDescription) throws Exception {
        Method m = Class.forName("com.oracle.jib.api.JibArtifactInstaller").getMethod(methodName, String.class, Map.class);
        return (Path)m.invoke(installerObject, jibVersion, artifactDescription);
    }

    @Override
    public Path resolve(Artifact artifact) throws FileNotFoundException {
        Path path;
        // Use the DefaultArtifactManager to enable users to override locations
        try {
            ArtifactManager manager = new DefaultArtifactManager();
            path = manager.resolve(artifact);
        } catch (FileNotFoundException e) {
            // Location hasn't been overridden, continue to automatically try to resolve the dependency
            try {
                HashMap<String, Object> artifactDescription = new HashMap<>();
                artifactDescription.put("module", artifact.name());
                artifactDescription.put("organization", artifact.organization());
                artifactDescription.put("ext", artifact.extension());
                artifactDescription.put("revision", artifact.revision());
                if (artifact.classifier().length() > 0) {
                    artifactDescription.put("classifier", artifact.classifier());
                }

                path = download(jibVersion, artifactDescription);
                if (artifact.unpack()) {
                    path = install(jibVersion, artifactDescription);
                }
            } catch (Exception exception) {
                throw new FileNotFoundException("Failed to resolve the artifact " + artifact);
            }
        }
        return path;
   }
}
