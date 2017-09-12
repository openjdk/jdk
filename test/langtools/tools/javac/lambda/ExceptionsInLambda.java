/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8015809
 * @summary Producing individual errors for uncaught undeclared exceptions inside lambda expressions, instead of one error for whole lambda
 * @compile/fail/ref=ExceptionsInLambda.out -XDrawDiagnostics ExceptionsInLambda.java
 */

import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.InputStream;
import java.io.Reader;

public class ExceptionsInLambda {

    public static void main(Runnable p, File f) {
        main(() -> {
            StringBuilder sb = new StringBuilder();

            Reader in = new FileReader(f);
            int r;

            while ((r = in.read()) != (-1)) {
                sb.append((char) r);
            }
        }, f);

        doOpen(() -> new FileInputStream(f));
    }

    public static InputStream doOpen(Open open) {
        return open.open();
    }

    public interface Open {
        public InputStream open();
    }
}
