/*
 * Copyright (c) 2016, 2022, Oracle and/or its affiliates. All rights reserved.
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

package jdk.javadoc.internal.tool;

import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.JavacMessages;
import java.io.PrintWriter;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.spi.ToolProvider;

/**
 * An implementation of the {@link java.util.spi.ToolProvider ToolProvider} SPI,
 * providing access to JDK documentation tool, javadoc.
 *
 * @since 9
 */
// This is currently a stand-alone top-level class so that it can easily be excluded
// from interims builds of javadoc, used while building JDK.
public class JavadocToolProvider implements ToolProvider {

    @Override
    public String name() {
        return "javadoc";
    }

    // @Override - commented out due to interim builds of javadoc with JDKs < 19.
    public Optional<String> description() {
        JavacMessages messages = JavacMessages.instance(new Context());
        messages.add(locale -> ResourceBundle.getBundle("jdk.javadoc.internal.tool.resources.javadoc", locale));
        return Optional.of(messages.getLocalizedString("javadoc.description"));
    }

    @Override
    public int run(PrintWriter out, PrintWriter err, String... args) {
        return Main.execute(args, out, err);
    }
}
