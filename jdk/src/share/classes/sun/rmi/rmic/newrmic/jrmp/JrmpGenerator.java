/*
 * Copyright (c) 2003, 2005, Oracle and/or its affiliates. All rights reserved.
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

package sun.rmi.rmic.newrmic.jrmp;

import com.sun.javadoc.ClassDoc;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import sun.rmi.rmic.newrmic.BatchEnvironment;
import sun.rmi.rmic.newrmic.Generator;
import sun.rmi.rmic.newrmic.IndentingWriter;
import sun.rmi.rmic.newrmic.Main;
import sun.rmi.rmic.newrmic.Resources;

import static sun.rmi.rmic.newrmic.jrmp.Constants.*;

/**
 * JRMP rmic back end; generates source code for JRMP stub and
 * skeleton classes.
 *
 * WARNING: The contents of this source file are not part of any
 * supported API.  Code that depends on them does so at its own risk:
 * they are subject to change or removal without notice.
 *
 * @author Peter Jones
 **/
public class JrmpGenerator implements Generator {

    private static final Map<String,StubVersion> versionOptions =
        new HashMap<String,StubVersion>();
    static {
        versionOptions.put("-v1.1", StubVersion.V1_1);
        versionOptions.put("-vcompat", StubVersion.VCOMPAT);
        versionOptions.put("-v1.2", StubVersion.V1_2);
    }

    private static final Set<String> bootstrapClassNames =
        new HashSet<String>();
    static {
        bootstrapClassNames.add("java.lang.Exception");
        bootstrapClassNames.add("java.rmi.Remote");
        bootstrapClassNames.add("java.rmi.RemoteException");
        bootstrapClassNames.add("java.lang.RuntimeException");
    };

    /** version of the JRMP stub protocol to generate code for */
    private StubVersion version = StubVersion.V1_2;     // default is -v1.2

    /**
     * Creates a new JrmpGenerator.
     **/
    public JrmpGenerator() { }

    /**
     * The JRMP generator recognizes command line options for
     * selecting the JRMP stub protocol version to generate classes
     * for.  Only one such option is allowed.
     **/
    public boolean parseArgs(String[] args, Main main) {
        String explicitVersion = null;
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (versionOptions.containsKey(arg)) {
                if (explicitVersion != null && !explicitVersion.equals(arg)) {
                    main.error("rmic.cannot.use.both", explicitVersion, arg);
                    return false;
                }
                explicitVersion = arg;
                version = versionOptions.get(arg);
                args[i] = null;
            }
        }
        return true;
    }

    /**
     * The JRMP generator does not require an environment class more
     * specific than BatchEnvironment.
     **/
    public Class<? extends BatchEnvironment> envClass() {
        return BatchEnvironment.class;
    }

    public Set<String> bootstrapClassNames() {
        return Collections.unmodifiableSet(bootstrapClassNames);
    }

    /**
     * Generates the source file(s) for the JRMP stub class and
     * (optionally) skeleton class for the specified remote
     * implementation class.
     **/
    public void generate(BatchEnvironment env,
                         ClassDoc inputClass,
                         File destDir)
    {
        RemoteClass remoteClass = RemoteClass.forClass(env, inputClass);
        if (remoteClass == null) {
            return;     // an error must have occurred
        }

        StubSkeletonWriter writer =
            new StubSkeletonWriter(env, remoteClass, version);

        File stubFile = sourceFileForClass(writer.stubClassName(), destDir);
        try {
            IndentingWriter out = new IndentingWriter(
                new OutputStreamWriter(new FileOutputStream(stubFile)));
            writer.writeStub(out);
            out.close();
            if (env.verbose()) {
                env.output(Resources.getText("rmic.wrote",
                                             stubFile.getPath()));
            }
            env.addGeneratedFile(stubFile);
        } catch (IOException e) {
            env.error("rmic.cant.write", stubFile.toString());
            return;
        }

        File skeletonFile =
            sourceFileForClass(writer.skeletonClassName(), destDir);
        if (version == StubVersion.V1_1 ||
            version == StubVersion.VCOMPAT)
        {
            try {
                IndentingWriter out = new IndentingWriter(
                    new OutputStreamWriter(
                        new FileOutputStream(skeletonFile)));
                writer.writeSkeleton(out);
                out.close();
                if (env.verbose()) {
                    env.output(Resources.getText("rmic.wrote",
                                                 skeletonFile.getPath()));
                }
                env.addGeneratedFile(skeletonFile);
            } catch (IOException e) {
                env.error("rmic.cant.write", skeletonFile.toString());
                return;
            }
        } else {
            /*
             * If skeleton files are not being generated for this run,
             * delete old skeleton source or class files for this
             * remote implementation class that were (presumably) left
             * over from previous runs, to avoid user confusion from
             * extraneous or inconsistent generated files.
             */
            File skeletonClassFile =
                classFileForClass(writer.skeletonClassName(), destDir);

            skeletonFile.delete();      // ignore failures (no big deal)
            skeletonClassFile.delete();
        }
    }


    /**
     * Returns the File object to be used as the source file for a
     * class with the specified binary name, with the specified
     * destination directory as the top of the package hierarchy.
     **/
    private File sourceFileForClass(String binaryName, File destDir) {
        return fileForClass(binaryName, destDir, ".java");
    }

    /**
     * Returns the File object to be used as the class file for a
     * class with the specified binary name, with the supplied
     * destination directory as the top of the package hierarchy.
     **/
    private File classFileForClass(String binaryName, File destDir) {
        return fileForClass(binaryName, destDir, ".class");
    }

    private File fileForClass(String binaryName, File destDir, String ext) {
        int i = binaryName.lastIndexOf('.');
        String classFileName = binaryName.substring(i + 1) + ext;
        if (i != -1) {
            String packageName = binaryName.substring(0, i);
            String packagePath = packageName.replace('.', File.separatorChar);
            File packageDir = new File(destDir, packagePath);
            /*
             * Make sure that the directory for this package exists.
             * We assume that the caller has verified that the top-
             * level destination directory exists, so we need not
             * worry about creating it unintentionally.
             */
            if (!packageDir.exists()) {
                packageDir.mkdirs();
            }
            return new File(packageDir, classFileName);
        } else {
            return new File(destDir, classFileName);
        }
    }
}
