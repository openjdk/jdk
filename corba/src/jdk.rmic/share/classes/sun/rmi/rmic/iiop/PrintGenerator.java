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

package sun.rmi.rmic.iiop;

import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import sun.tools.java.CompilerError;
import sun.tools.java.ClassDefinition;
import sun.rmi.rmic.IndentingWriter;
import sun.rmi.rmic.Main;

/**
 * An IDL generator for rmic.
 *
 * @author      Bryan Atsatt
 */
public class PrintGenerator implements sun.rmi.rmic.Generator,
                                       sun.rmi.rmic.iiop.Constants {

    private static final int JAVA = 0;
    private static final int IDL = 1;
    private static final int BOTH = 2;

    private int whatToPrint; // Initialized in parseArgs.
    private boolean global = false;
    private boolean qualified = false;
    private boolean trace = false;
    private boolean valueMethods = false;

    private IndentingWriter out;

    /**
     * Default constructor for Main to use.
     */
    public PrintGenerator() {
        OutputStreamWriter writer = new OutputStreamWriter(System.out);
        out = new IndentingWriter (writer);
    }

    /**
     * Examine and consume command line arguments.
     * @param argv The command line arguments. Ignore null
     * @param error Report any errors using the main.error() methods.
     * @return true if no errors, false otherwise.
     */
    public boolean parseArgs(String argv[], Main main) {
        for (int i = 0; i < argv.length; i++) {
            if (argv[i] != null) {
                String arg = argv[i].toLowerCase();
                if (arg.equals("-xprint")) {
                    whatToPrint = JAVA;
                    argv[i] = null;
                    if (i+1 < argv.length) {
                        if (argv[i+1].equalsIgnoreCase("idl")) {
                            argv[++i] = null;
                            whatToPrint = IDL;
                        } else if (argv[i+1].equalsIgnoreCase("both")) {
                            argv[++i] = null;
                            whatToPrint = BOTH;
                        }
                    }
                } else if (arg.equals("-xglobal")) {
                    global = true;
                    argv[i] = null;
                } else if (arg.equals("-xqualified")) {
                    qualified = true;
                    argv[i] = null;
                } else if (arg.equals("-xtrace")) {
                    trace = true;
                    argv[i] = null;
                } else if (arg.equals("-xvaluemethods")) {
                    valueMethods = true;
                    argv[i] = null;
                }
            }
        }
        return true;
    }

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
    public void generate(sun.rmi.rmic.BatchEnvironment env, ClassDefinition cdef, File destDir) {

        BatchEnvironment ourEnv = (BatchEnvironment) env;
        ContextStack stack = new ContextStack(ourEnv);
        stack.setTrace(trace);

        if (valueMethods) {
            ourEnv.setParseNonConforming(true);
        }

        // Get our top level type...

        CompoundType topType = CompoundType.forCompound(cdef,stack);

        if (topType != null) {

            try {

                                // Collect up all the compound types...

                Type[] theTypes = topType.collectMatching(TM_COMPOUND);

                for (int i = 0; i < theTypes.length; i++) {

                    out.pln("\n-----------------------------------------------------------\n");

                    Type theType = theTypes[i];

                    switch (whatToPrint) {
                    case JAVA:  theType.println(out,qualified,false,false);
                        break;

                    case IDL:   theType.println(out,qualified,true,global);
                        break;

                    case BOTH:  theType.println(out,qualified,false,false);
                        theType.println(out,qualified,true,global);
                        break;

                    default:    throw new CompilerError("Unknown type!");
                    }
                }

                out.flush();

            } catch (IOException e) {
                throw new CompilerError("PrintGenerator caught " + e);
            }
        }
    }
}
