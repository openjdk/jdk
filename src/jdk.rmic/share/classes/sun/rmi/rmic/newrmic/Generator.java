/*
 * Copyright (c) 2003, Oracle and/or its affiliates. All rights reserved.
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

package sun.rmi.rmic.newrmic;

import com.sun.javadoc.ClassDoc;
import java.io.File;
import java.util.Set;

/**
 * The interface to rmic back end implementations.  Classes that
 * implement this interface correspond to the various generation modes
 * of rmic (JRMP, IIOP, IDL, etc.).
 *
 * A Generator instance corresponds to a particular rmic compilation
 * batch, and its instance state represents the generator-specific
 * command line options for that batch.  Main will instantiate a
 * generator class when the command line arguments indicate selection
 * of the corresponding generation mode.  Main will then invoke the
 * "parseArgs" method to allow the generator to process any
 * generator-specific command line options and set its instance state
 * accordingly.
 *
 * WARNING: The contents of this source file are not part of any
 * supported API.  Code that depends on them does so at its own risk:
 * they are subject to change or removal without notice.
 *
 * @author Peter Jones
 **/
public interface Generator {

    /**
     * Processes the command line options specific to this generator.
     * Processed options are set to null in the specified array.
     * Returns true if successful or false if an error occurs.  Errors
     * are output to the specific Main instance.
     **/
    public boolean parseArgs(String[] args, Main main);

    /**
     * Returns the most specific environment class required by this
     * generator.
     **/
    public Class<? extends BatchEnvironment> envClass();

    /**
     * Returns the names of the classes that must be available through
     * the doclet API in order for this generator to function.
     **/
    public Set<String> bootstrapClassNames();

    /**
     * Generates the protocol-specific rmic output files for the
     * specified remote class.  This method is invoked once for each
     * class or interface specified on the command line for the rmic
     * compilation batch associated with this instance.
     *
     * Any generated source files (to be compiled with javac) are
     * passed to the addGeneratedFile method of the specified
     * BatchEnvironment.
     **/
    public void generate(BatchEnvironment env,
                         ClassDoc inputClass,
                         File destDir);
}
