/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug 8350481
 * @summary Test ServiceLoader when finding the provider's public constructor fails with LinkageError
 * @compile Provider1.java Provider2.java
 * @run main/othervm LinkageError1
 */

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;

public class LinkageError1 {
    public static void main(String[] args) throws Exception {

        // create the services configuration file that lists two providers
        URI u = LinkageError1.class.getProtectionDomain().getCodeSource().getLocation().toURI();
        Path dir = Path.of(u);
        Path configFile = dir.resolve("META-INF", "services", "Service");
        Files.createDirectories(configFile.getParent());
        Files.write(configFile, List.of("Provider1", "Provider2"));

        // delete class file for a parameter in one of Provider2's constructors
        Files.delete(dir.resolve("Arg.class"));

        // iterate over all providers in the "keep on tricking" way
        Iterator<Service> iterator = ServiceLoader.load(Service.class).iterator();
        int count = 0;
        boolean done = false;
        while (!done) {
            try {
                if (iterator.hasNext()) {
                    Service provider = iterator.next();
                    System.out.println(provider);
                    count++;
                } else {
                    done = true;
                }
            } catch (ServiceConfigurationError e) {
                e.printStackTrace(System.out);
                Throwable cause = e.getCause();
                if (!(cause instanceof LinkageError)) {
                    fail("ServiceConfigurationError cause " +  cause + ", expected LinkageError");
                }

            }
        }
        if (count != 1) {
            fail("Found " + count + " provider(s), expected 1");
        }
    }

    static void fail(String message) {
        throw new RuntimeException(message);
    }
}
