/*
 * Copyright 2008 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

import java.lang.instrument.*;
import java.net.*;
import java.io.*;
import java.security.*;

/**
 *      Test Java Agent
 *
 *      @author Daryl Puryear
 *      @copyright 1999-2004 Wily Technology, Inc.  All rights reserved.
 */
public class ParallelTransformerLoaderAgent
{
        private static URL sURL;
        private static ClassLoader sClassLoader;

        public static synchronized ClassLoader
        getClassLoader()
        {
                return sClassLoader;
        }

        public static synchronized void
        generateNewClassLoader()
        {
                sClassLoader = new URLClassLoader(new URL[] {sURL});
        }

        public static void
        premain(        String agentArgs,
                        Instrumentation instrumentation)
                throws Exception
        {
                if (agentArgs == null || agentArgs == "")
                {
                        System.err.println("Error: No jar file name provided, test will not run.");
                        return;
                }

                sURL = (new File(agentArgs)).toURL();
                System.out.println("Using jar file: " + sURL);
                generateNewClassLoader();

                instrumentation.addTransformer(new TestTransformer());
        }

        private static class TestTransformer
                implements ClassFileTransformer
        {
                public byte[]
                transform(      ClassLoader loader,
                                String className,
                                Class classBeingRedefined,
                                ProtectionDomain protectionDomain,
                                byte[] classfileBuffer)
                        throws IllegalClassFormatException
                {
                        String tName = Thread.currentThread().getName();
                        // In 160_03 and older, transform() is called
                        // with the "system_loader_lock" held and that
                        // prevents the bootstrap class loaded from
                        // running in parallel. If we add a slight sleep
                        // delay here when the transform() call is not
                        // main or TestThread, then the deadlock in
                        // 160_03 and older is much more reproducible.
                        if (!tName.equals("main") && !tName.equals("TestThread")) {
                            System.out.println("Thread '" + tName +
                                "' has called transform()");
                            try {
                                Thread.sleep(500);
                            } catch (InterruptedException ie) {
                            }
                        }

                        // load additional classes when called from other threads
                        if (!tName.equals("main"))
                        {
                                loadClasses(3);
                        }
                        return null;
                }

                public static void
                loadClasses( int index)
                {
                        ClassLoader loader = ParallelTransformerLoaderAgent.getClassLoader();
                        try
                        {
                                Class.forName("TestClass" + index, true, loader);
                        }
                        catch (Exception e)
                        {
                                e.printStackTrace();
                        }
                }
        }
}
