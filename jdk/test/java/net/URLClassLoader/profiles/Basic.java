/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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

import java.net.*;
import java.io.File;
import java.util.jar.*;

/**
 * Attempts to load classes or resources from a JAR file. The load should succeed
 * if the runtime supports the profile indicated by the Profile attribute, fail
 * with UnsupportedProfileException otherwise.
 */

public class Basic {

    static int indexOf(String profile) {
        if (profile == null || "compact1".equals(profile)) return 1;
        if ("compact2".equals(profile)) return 2;
        if ("compact3".equals(profile)) return 3;
        if ("".equals(profile)) return 4;
        return Integer.MAX_VALUE;  // unknown profile name
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2)
            throw new RuntimeException("Usage: java <jarfile> <classname>");
        String jar = args[0];
        String cn = args[1];

        File lib = new File(jar);
        URL url = lib.toURI().toURL();
        URL urls[] = { url };

        // ## replace this if there is a standard way to determine the profile
        String thisProfile = sun.misc.Version.profileName();

        String jarProfile = null;
        try (JarFile jf = new JarFile(lib)) {
            Manifest manifest = jf.getManifest();
            if (manifest != null) {
                Attributes mainAttrs = manifest.getMainAttributes();
                if (mainAttrs != null) {
                    jarProfile = mainAttrs.getValue(Attributes.Name.PROFILE);
                }
            }
        }

        boolean shouldFail = indexOf(thisProfile) < indexOf(jarProfile);

        try (URLClassLoader cl = new URLClassLoader(urls)) {
            System.out.format("Loading %s from %s ...%n", cn, jar);
            Class<?> c = Class.forName(cn, true, cl);
            System.out.println(c);
            if (shouldFail)
                throw new RuntimeException("UnsupportedProfileException expected");
        } catch (UnsupportedProfileException x) {
            if (!shouldFail)
                throw x;
            System.out.println("UnsupportedProfileException thrown as expected");
        }

        try (URLClassLoader cl = new URLClassLoader(urls)) {
            System.out.format("Loading resource from %s ...%n", jar);
            URL r = cl.findResource("META-INF/MANIFEST.MF");
            System.out.println(r);
            if (shouldFail)
                throw new RuntimeException("UnsupportedProfileException expected");
        } catch (UnsupportedProfileException x) {
            if (!shouldFail)
                throw x;
            System.out.println("UnsupportedProfileException thrown as expected");
        }
    }
}

