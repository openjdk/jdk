/*
 * Copyright 2005-2006 Sun Microsystems, Inc.  All Rights Reserved.
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
package com.sun.tools.internal.jxc;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.JAXBContext;

import com.sun.mirror.apt.AnnotationProcessorFactory;
import com.sun.tools.internal.jxc.apt.Options;
import com.sun.tools.internal.xjc.BadCommandLineException;
import com.sun.tools.internal.xjc.api.util.APTClassLoader;
import com.sun.tools.internal.xjc.api.util.ToolsJarNotFoundException;
import com.sun.xml.internal.bind.util.Which;

/**
 * CLI entry-point to the schema generator.
 *
 * @author Bhakti Mehta
 */
public class SchemaGenerator {
    /**
     * Runs the schema generator.
     */
    public static void main(String[] args) throws Exception {
        System.exit(run(args));
    }

    public static int run(String[] args) throws Exception {
        try {
            ClassLoader cl = SchemaGenerator.class.getClassLoader();
            if(cl==null)    cl = ClassLoader.getSystemClassLoader();
            ClassLoader classLoader = new APTClassLoader(cl,packagePrefixes);
            return run(args, classLoader);
        } catch( ToolsJarNotFoundException e) {
            System.err.println(e.getMessage());
            return -1;
        }
    }

    /**
     * List of package prefixes we want to load in the same package
     */
    private static final String[] packagePrefixes = {
        "com.sun.tools.internal.jxc.",
        "com.sun.tools.internal.xjc.",
        "com.sun.istack.internal.tools.",
        "com.sun.tools.apt.",
        "com.sun.tools.javac.",
        "com.sun.tools.javadoc.",
        "com.sun.mirror."
    };

    /**
     * Runs the schema generator.
     *
     * @param classLoader
     *      the schema generator will run in this classLoader.
     *      It needs to be able to load APT and JAXB RI classes. Note that
     *      JAXB RI classes refer to APT classes. Must not be null.
     *
     * @return
     *      exit code. 0 if success.
     *
     */
    public static int run(String[] args, ClassLoader classLoader) throws ClassNotFoundException, NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        final Options options = new Options();
        if (args.length ==0) {
            usage();
            return -1;
        }
        for (String arg : args) {
            if (arg.equals("-help")) {
                usage();
                return -1;
            }

            if (arg.equals("-version")) {
                System.out.println(Messages.VERSION.format());
                return -1;
            }
        }

        try {
            options.parseArguments(args);
        } catch (BadCommandLineException e) {
            // there was an error in the command line.
            // print usage and abort.
            System.out.println(e.getMessage());
            System.out.println();
            usage();
            return -1;
        }

        Class schemagenRunner = classLoader.loadClass(Runner.class.getName());
        Method mainMethod = schemagenRunner.getDeclaredMethod("main",String[].class,File.class);

        List<String> aptargs = new ArrayList<String>();

        if(hasClass(options.arguments))
            aptargs.add("-XclassesAsDecls");

        // make jaxb-api.jar visible to classpath
        File jaxbApi = findJaxbApiJar();
        if(jaxbApi!=null) {
            if(options.classpath!=null) {
                options.classpath = options.classpath+File.pathSeparatorChar+jaxbApi;
            } else {
                options.classpath = jaxbApi.getPath();
            }
        }

        aptargs.add("-cp");
        aptargs.add(options.classpath);

        if(options.targetDir!=null) {
            aptargs.add("-d");
            aptargs.add(options.targetDir.getPath());
        }

        aptargs.addAll(options.arguments);

        String[] argsarray = aptargs.toArray(new String[aptargs.size()]);
        return (Integer)mainMethod.invoke(null,new Object[]{argsarray,options.episodeFile});
    }

    /**
     * Computes the file system path of <tt>jaxb-api.jar</tt> so that
     * APT will see them in the <tt>-cp</tt> option.
     *
     * <p>
     * In Java, you can't do this reliably (for that matter there's no guarantee
     * that such a jar file exists, such as in Glassfish), so we do the best we can.
     *
     * @return
     *      null if failed to locate it.
     */
    private static File findJaxbApiJar() {
        String url = Which.which(JAXBContext.class);
        if(url==null)       return null;    // impossible, but hey, let's be defensive

        if(!url.startsWith("jar:") || url.lastIndexOf('!')==-1)
            // no jar file
            return null;

        String jarFileUrl = url.substring(4,url.lastIndexOf('!'));
        if(!jarFileUrl.startsWith("file:"))
            return null;    // not from file system

        try {
            File f = new File(new URL(jarFileUrl).getFile());
            if(f.exists() && f.getName().endsWith(".jar"))
                return f;
            else
                return null;
        } catch (MalformedURLException e) {
            return null;    // impossible
        }
    }

    /**
     * Returns true if the list of arguments have an argument
     * that looks like a class name.
     */
    private static boolean hasClass(List<String> args) {
        for (String arg : args) {
            if(!arg.endsWith(".java"))
                return true;
        }
        return false;
    }

    private static void usage( ) {
        System.out.println(Messages.USAGE.format());
    }

    public static final class Runner {
        public static int main(String[] args, File episode) throws Exception {
            ClassLoader cl = Runner.class.getClassLoader();
            Class apt = cl.loadClass("com.sun.tools.apt.Main");
            Method processMethod = apt.getMethod("process",AnnotationProcessorFactory.class, String[].class);

            com.sun.tools.internal.jxc.apt.SchemaGenerator r = new com.sun.tools.internal.jxc.apt.SchemaGenerator();
            if(episode!=null)
                r.setEpisodeFile(episode);
            return (Integer) processMethod.invoke(null, r, args);
        }
    }
}
