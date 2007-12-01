/*
 * Copyright 2002-2003 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */


package com.sun.tools.javah;


import java.io.*;

/**
 * Javah generates support files for native methods.
 * Parse commandline options & Invokes javadoc to execute those commands.
 *
 * @author Sucheta Dambalkar
 */
public class Main{
    /*
     * Parse arguments given for javah to give proper error messages.
     */
    public static void main(String[] args){

        if (args.length == 0) {
            Util.usage(1);
        }
        for ( int i = 0; i < args.length; i++) {
            if (args[i].equals("-o")) {
                i++;
                if(i >= args.length){
                    Util.usage(1);
                }else if(args[i].charAt(0) == '-'){
                    Util.error("no.outputfile.specified");
                }else if((i+1) >= args.length){
                    Util.error("no.classes.specified");
                }
            } else if (args[i].equals("-d")) {
                i++;
                if(i >= args.length){
                    Util.usage(1);
                }else if(args[i].charAt(0) == '-')  {
                    Util.error("no.outputdir.specified");
                }else if((i+1) >= args.length){
                    Util.error("no.classes.specified");
                }
            } else if (args[i].equals("-td")) {
                /* Ignored.  Generate tmp files to memory. */
                i++;
                if (i == args.length)
                    Util.usage(1);
            } else if (args[i].equals("-stubs")) {
                if((i+1) >= args.length){
                    Util.error("no.classes.specified");
                }
            } else if (args[i].equals("-v") || args[i].equals("-verbose")) {
                if((i+1) >= args.length){
                    Util.error("no.classes.specified");
                }
                args[i] = "-verbose";
            } else if ((args[i].equals("-help")) || (args[i].equals("--help"))
                       || (args[i].equals("-?")) || (args[i].equals("-h"))) {
                Util.usage(0);
            } else if (args[i].equals("-trace")) {
                System.err.println(Util.getText("tracing.not.supported"));
            } else if (args[i].equals("-version")) {
                if((i+1) >= args.length){
                    Util.version();
                }
            } else if (args[i].equals("-jni")) {
                if((i+1) >= args.length){
                    Util.error("no.classes.specified");
                }
            } else if (args[i].equals("-force")) {
                if((i+1) >= args.length){
                    Util.error("no.classes.specified");
                }
            } else if (args[i].equals("-Xnew")) {
                // we're already using the new javah
            } else if (args[i].equals("-old")) {
                System.err.println(Util.getText("old.not.supported"));
                Util.usage(1);
            } else if (args[i].equals("-Xllni")) {
                if((i+1) >= args.length){
                    Util.error("no.classes.specified");
                }
            } else if (args[i].equals("-llni")) {
                if((i+1) >= args.length){
                    Util.error("no.classes.specified");
                }
            } else if (args[i].equals("-llniDouble")) {
                if((i+1) >= args.length){
                    Util.error("no.classes.specified");
                }
            } else if (args[i].equals("-classpath")) {
                i++;
                if(i >= args.length){
                    Util.usage(1);
                }else if(args[i].charAt(0) == '-') {
                    Util.error("no.classpath.specified");
                }else if((i+1) >= args.length){
                    Util.error("no.classes.specified");
                }
            } else if (args[i].equals("-bootclasspath")) {
                i++;
                if(i >= args.length){
                    Util.usage(1);
                }else if(args[i].charAt(0) == '-'){
                    Util.error("no.bootclasspath.specified");
                }else if((i+1) >= args.length){
                    Util.error("no.classes.specified");
                }
            } else if (args[i].charAt(0) == '-') {
                Util.error("unknown.option", args[i], null, true);

            } else {
                //break; /* The rest must be classes. */
            }
        }

        /* Invoke javadoc */

        String[] javadocargs = new String[args.length + 2];
        int i = 0;

        for(; i < args.length; i++) {
            javadocargs[i] = args[i];
        }

        javadocargs[i] = "-private";
        i++;
        javadocargs[i] = "-Xclasses";

        int rc = com.sun.tools.javadoc.Main.execute("javadoc", "com.sun.tools.javah.MainDoclet", javadocargs);
        System.exit(rc);
    }
}
