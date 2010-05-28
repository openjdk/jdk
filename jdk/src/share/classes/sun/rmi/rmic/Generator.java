/*
 * Copyright (c) 1998, 2007, Oracle and/or its affiliates. All rights reserved.
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

/*
 * Licensed Materials - Property of IBM
 * RMI-IIOP v1.0
 * Copyright IBM Corp. 1998 1999  All Rights Reserved
 *
 */

package sun.rmi.rmic;

import java.io.File;
import sun.tools.java.ClassDefinition;

/**
 * Generator defines the protocol for back-end implementations to be added
 * to rmic.  See the rmic.properties file for a description of the format for
 * adding new Generators to rmic.
 * <p>
 * Classes implementing this interface must have a public default constructor
 * which should set any required arguments to their defaults.  When Main
 * encounters a command line argument which maps to a specific Generator
 * subclass, it will instantiate one and call parseArgs(...).  At some later
 * point, Main will invoke the generate(...) method once for _each_ class passed
 * on the command line.
 *
 * WARNING: The contents of this source file are not part of any
 * supported API.  Code that depends on them does so at its own risk:
 * they are subject to change or removal without notice.
 *
 * @author      Bryan Atsatt
 */
public interface Generator {

    /**
     * Examine and consume command line arguments.
     * @param argv The command line arguments. Ignore null
     * and unknown arguments. Set each consumed argument to null.
     * @param main Report any errors using the main.error() methods.
     * @return true if no errors, false otherwise.
     */
    public boolean parseArgs(String argv[], Main main);

    /**
     * Generate output. Any source files created which need compilation should
     * be added to the compiler environment using the addGeneratedFile(File)
     * method.
     *
     * @param env       The compiler environment
     * @param cdef      The definition for the implementation class or interface from
     *              which to generate output
     * @param destDir   The directory for the root of the package hierarchy
     *                          for generated files. May be null.
     */
    public void generate(BatchEnvironment env, ClassDefinition cdef, File destDir);
}
