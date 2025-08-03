/*
 * Copyright (c) 2010, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6813340
 * @summary X509Factory should not depend on is.available()==0
 */

import java.io.File;
import java.io.FileInputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.security.cert.CertificateFactory;
import java.util.concurrent.atomic.AtomicBoolean;

public class SlowStream {
    public static void main(String[] args) throws Exception {
        final var outputStream = new PipedOutputStream();
        final var inputStream = new PipedInputStream(outputStream);

        final var failed = new AtomicBoolean(false);

        final var writer = new Thread(() -> {
            try {
                for (int i = 0; i < 5; i++) {
                    try (final var fin = new FileInputStream(new File(
                            new File(System.getProperty("test.src", "."),
                                    "openssl"),
                            "pem"))) {

                        fin.transferTo(outputStream);

                        Thread.sleep(2000);
                    }
                }
                outputStream.close();
            } catch (final Exception e) {
                System.out.println("Writer Thread Error: ");
                e.printStackTrace(System.out);
                failed.set(true);
            }
        });

        final var reader = new Thread(() -> {
            try {
                final var factory = CertificateFactory.getInstance("X.509");
                final var numOfCerts = factory.generateCertificates(inputStream).size();
                if (numOfCerts != 5) {
                    throw new Exception(
                            String.format("Not all certs read. %d found 5 expected", numOfCerts)
                    );
                }
                inputStream.close();
            } catch (final Exception e) {
                System.out.println("Reader Thread Error: ");
                e.printStackTrace(System.out);
                failed.set(true);
            }
        });

        writer.start();
        reader.start();

        writer.join();
        reader.join();

        if (failed.get()) {
            throw new RuntimeException(
                    "The test failed, please check the reader and writer threads output"
            );
        }
    }
}
