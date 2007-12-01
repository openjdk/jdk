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

import com.sun.javadoc.*;
import java.io.*;

/**
 * A doclet to parse and execute commandline options.
 *
 * @author Sucheta Dambalkar(using code from old javap)
 */
public class MainDoclet{

    public static  String  odir        = null;
    public static  String  ofile       = null;
    public static  boolean stubs       = false;
    public static  boolean jni         = false;
    public static  boolean llni        = false;
    public static  boolean doubleAlign = false;
    public static  boolean force       = false;
    public static  String  genclass    = null;


    /**
     * Entry point.
     */
    public static boolean start(RootDoc root) {

        int j = 0;
        int k = 0;
        /**
         * Command line options.
         */
        String [][] cmdoptions = root.options();
        /**
         * Classes specified on command line.
         */
        ClassDoc[] classes = root.classes();
        /**
         * Generator used by javah.  Default is JNI.
         */
        Gen g = new JNI(root);

        validateOptions(cmdoptions);

        /*
         * Select native interface.
         */
        if (jni && llni)  Util.error("jni.llni.mixed");

        if (llni)
            g = new LLNI(doubleAlign, root);

        if (g instanceof JNI && stubs)  Util.error("jni.no.stubs");

        /*
         * Arrange for output destination.
         */
        if (odir != null && ofile != null)
            Util.error("dir.file.mixed");

        if (odir != null)
            g.setOutDir(odir);

        if (ofile != null)
            g.setOutFile(ofile);

        /*
         * Force set to false will turn off smarts about checking file
         * content before writing.
         */
        g.setForce(force);

        /*
         * Grab the rest of argv[] ... this must be the classes.
         */
        if (classes.length == 0){
            Util.error("no.classes.specified");
        }
        /*
         * Set classes.
         */
        g.setClasses(classes);

        try {
            g.run();
        } catch (ClassNotFoundException cnfe) {
            Util.error("class.not.found", cnfe.getMessage());
        } catch (IOException ioe) {
            Util.error("io.exception", ioe.getMessage());
        }

        return true;
    }

    /**
     * Required doclet method.
     */
    public static int optionLength(String option) {
        if (option.equals("-o")) {
            return 2;
        } else if(option.equals("-d")){
            return 2;
        } else if (option.equals("-td")) {
            return 1;
        } else if (option.equals("-stubs")) {
            return 1;
        } else if(option.equals("-help")){
            return 1;
        } else if(option.equals("--help")){
            return 1;
        } else if(option.equals("-?")){
            return 1;
        } else if(option.equals("-h")){
            return 1;
        } else if(option.equals("-trace")){
            return 1;
        } else if(option.equals("-version")) {
            return 1;
        } else if(option.equals("-jni")){
            return 1;
        } else if(option.equals("-force")){
            return 1;
        } else if(option.equals("-Xllni")){
            return 1;
        } else if(option.equals("-llni")){
            return 1;
        } else if(option.equals("-llniDouble")){
            return 1;
        } else return 0;
    }

    /**
     * Parse the command line options.
     */
    public static void validateOptions(String cmdoptions[][]) {
        /* Default values for options, overridden by user options. */
        String bootcp = System.getProperty("sun.boot.class.path");
        String  usercp = System.getProperty("env.class.path");

        for(int p = 0; p < cmdoptions.length; p++){

            if (cmdoptions[p][0].equals("-o")) {
                ofile = cmdoptions[p][1];
            } else if(cmdoptions[p][0].equals("-d")){
                odir = cmdoptions[p][1];
            } else if (cmdoptions[p][0].equals("-td")) {
                if (p ==cmdoptions.length)
                    Util.usage(1);
            } else if (cmdoptions[p][0].equals("-stubs")) {
                stubs = true;
            } else if (cmdoptions[p][0].equals("-verbose")) {
                Util.verbose = true;
            } else if((cmdoptions[p][0].equals("-help"))
                      || (cmdoptions[p][0].equals("--help"))
                      || (cmdoptions[p][0].equals("-?"))
                      || (cmdoptions[p][0].equals("-h"))) {
                Util.usage(0);
            } else if (cmdoptions[p][0].equals("-trace")) {
                System.err.println(Util.getText("tracing.not.supported"));
            } else if (cmdoptions[p][0].equals("-version")) {
                Util.version();
            } else if (cmdoptions[p][0].equals("-jni")) {
                jni = true;
            } else if (cmdoptions[p][0].equals("-force")) {
                force = true;
            } else if (cmdoptions[p][0].equals("-Xllni")) {
                llni = true;
            } else if (cmdoptions[p][0].equals("-llni")) {
                llni = true;
            } else if (cmdoptions[p][0].equals("-llniDouble")) {
                llni = true; doubleAlign = true;
            } else if (cmdoptions[p][0].equals("-classpath")) {
                usercp = cmdoptions[p][1];
            } else if (cmdoptions[p][0].equals("-bootclasspath")) {
                bootcp = cmdoptions[p][1];
            } else if((cmdoptions[p][0].charAt(0) == '-')
                      && (!cmdoptions[p][0].equals("-private"))){
                Util.error("unknown.option", cmdoptions[p][0], null, true);
            } else {
                break; /* The rest must be classes. */
            }
        }


        if (Util.verbose) {
                System.err.println("[ Search Path: "
                                    + bootcp
                                    + System.getProperty("file.separator")
                                    + usercp + " ]");
        }
    }
}
