/*
 * Copyright (c) 2017, 2025, Oracle and/or its affiliates. All rights reserved.
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

import jtreg.SkippedException;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class ArtifactResolver {
    private static ArtifactManager getManager() throws ArtifactResolverException {
        ArtifactManager manager;
        try {
            String managerName = System.getProperty("jdk.test.lib.artifacts.artifactmanager");
            if (managerName != null) {
                manager = (ArtifactManager) Class.forName(managerName).getDeclaredConstructor().newInstance();
            } else if (System.getenv().containsKey(JibArtifactManager.JIB_HOME_ENV_NAME)) {
                manager = JibArtifactManager.newInstance();
            } else {
                manager = new DefaultArtifactManager();
            }
        } catch (Exception e) {
            throw new ArtifactResolverException("Failed to load ArtifactManager", e);
        }
        return manager;
    }

    public static Map<String, Path> resolve(Class<?> klass) throws ArtifactResolverException {
        ArtifactManager manager = getManager();
        ArtifactContainer artifactContainer = klass.getAnnotation(ArtifactContainer.class);
        HashMap<String, Path> locations = new HashMap<>();
        Artifact[] artifacts;

        if (artifactContainer == null) {
            artifacts = new Artifact[]{klass.getAnnotation(Artifact.class)};
        } else {
            artifacts = artifactContainer.value();
        }
        for (Artifact artifact : artifacts) {
            locations.put(artifactName(artifact), manager.resolve(artifact));
        }

        return locations;
    }

    public static Path resolve(String name, Map<String, Object> artifactDescription, boolean unpack) throws ArtifactResolverException {
        ArtifactManager manager = getManager();
        return  manager.resolve(name, artifactDescription, unpack);
    }

    /**
     * Retrieve an artifact/library/file from a repository or local file system.
     * <p>
     * Artifacts are defined with the {@link jdk.test.lib.artifacts.Artifact}
     * annotation.
     * <p>
     * If you have a local version of a dependency that you want to use, you can
     * specify that by setting the system property:
     * <code>jdk.test.lib.artifacts.ARTIFACT_NAME</code>. Where ARTIFACT_NAME
     * is the name field of the Artifact annotation.
     * <p>
     * Generally, tests that use this method should be run with <code>make test</code>.
     * However, tests can also be run with <code>jtreg</code> but you must have a
     * local copy of the artifact and the system property must be set as specified
     * above.
     *
     * @param klass a class annotated with {@link jdk.test.lib.artifacts.Artifact}
     * @return the local path to the artifact. If the artifact is a compressed
     * file that gets unpacked, this path will point to the root
     * directory of the uncompressed file(s).
     * @throws SkippedException thrown if the artifact cannot be found
     */
    public static Path fetchOne(Class<?> klass) {
        try {
            return ArtifactResolver.resolve(klass).entrySet().stream()
                    .findAny().get().getValue();
        } catch (ArtifactResolverException e) {
            Artifact artifact = klass.getAnnotation(Artifact.class);
            throw new SkippedException("Cannot find the artifact " + artifact.name(), e);
        }
    }

    private static String artifactName(Artifact artifact) {
        // Format of the artifact name is <organization>.<name>-<revision>(-<classifier>)
        String name = String.format("%s.%s-%s", artifact.organization(), artifact.name(), artifact.revision());
        if (artifact.classifier().length() != 0) {
            name = name +"-" + artifact.classifier();
        }
        return name;
    }
}
