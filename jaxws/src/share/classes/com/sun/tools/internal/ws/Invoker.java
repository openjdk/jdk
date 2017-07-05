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

package com.sun.tools.internal.ws;

import com.sun.istack.internal.tools.MaskingClassLoader;
import com.sun.istack.internal.tools.ParallelWorldClassLoader;
import com.sun.tools.internal.ws.resources.WscompileMessages;
import com.sun.tools.internal.xjc.api.util.ToolsJarNotFoundException;
import com.sun.xml.internal.bind.util.Which;

import javax.xml.ws.Service;
import javax.xml.ws.WebServiceFeature;
import java.io.File;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Invokes JAX-WS tools in a special class loader that can pick up APT classes,
 * even if it's not available in the tool launcher classpath.
 *
 * @author Kohsuke Kawaguchi
 */
public final class Invoker {
    static int invoke(String mainClass, String[] args) throws Throwable {
        // use the platform default proxy if available.
        // see sun.net.spi.DefaultProxySelector for details.
        if(!noSystemProxies) {
            try {
                System.setProperty("java.net.useSystemProxies","true");
            } catch (SecurityException e) {
                // failing to set this property isn't fatal
            }
        }

        ClassLoader oldcc = Thread.currentThread().getContextClassLoader();
        try {
            ClassLoader cl = Invoker.class.getClassLoader();
            if(Arrays.asList(args).contains("-Xendorsed"))
                cl = createClassLoader(cl); // perform JDK6 workaround hack
            else {
                if(!checkIfLoading21API()) {
                    if(Service.class.getClassLoader()==null)
                        System.err.println(WscompileMessages.INVOKER_NEED_ENDORSED());
                    else
                        System.err.println(WscompileMessages.WRAPPER_TASK_LOADING_20_API(Which.which(Service.class)));
                    return -1;
                }
                //find and load tools.jar
                List<URL> urls = new ArrayList<URL>();
                findToolsJar(cl, urls);

                if(urls.size() > 0){
                    List<String> mask = new ArrayList<String>(Arrays.asList(maskedPackages));

                    // first create a protected area so that we load JAXB/WS 2.1 API
                    // and everything that depends on them inside
                    cl = new MaskingClassLoader(cl,mask);

                    // then this classloader loads the API and tools.jar
                    cl = new URLClassLoader(urls.toArray(new URL[urls.size()]), cl);

                    // finally load the rest of the RI. The actual class files are loaded from ancestors
                    cl = new ParallelWorldClassLoader(cl,"");
                }

            }

            Thread.currentThread().setContextClassLoader(cl);

            Class compileTool = cl.loadClass(mainClass);
            Constructor ctor = compileTool.getConstructor(OutputStream.class);
            Object tool = ctor.newInstance(System.out);
            Method runMethod = compileTool.getMethod("run",String[].class);
            boolean r = (Boolean)runMethod.invoke(tool,new Object[]{args});
            return r ? 0 : 1;
        } catch (ToolsJarNotFoundException e) {
            System.err.println(e.getMessage());
        } catch (InvocationTargetException e) {
            throw e.getCause();
        } catch(ClassNotFoundException e){
            throw e;
        }finally {
            Thread.currentThread().setContextClassLoader(oldcc);
        }

        return -1;
    }

    /**
     * Returns true if the RI appears to be loading the JAX-WS 2.1 API.
     */
    public static boolean checkIfLoading21API() {
        try {
            Service.class.getMethod("getPort",Class.class, WebServiceFeature[].class);
            // yup. things look good.
            return true;
        } catch (NoSuchMethodException e) {
        } catch (LinkageError e) {
        }
        // nope
        return false;
    }

    /**
     * Creates a classloader that can load JAXB/WS 2.1 API and tools.jar,
     * and then return a classloader that can RI classes, which can see all those APIs and tools.jar.
     */
    public static ClassLoader createClassLoader(ClassLoader cl) throws ClassNotFoundException, MalformedURLException, ToolsJarNotFoundException {

        URL[] urls = findIstackAPIs(cl);
        if(urls.length==0)
            return cl;  // we seem to be able to load everything already. no need for the hack

        List<String> mask = new ArrayList<String>(Arrays.asList(maskedPackages));
        if(urls.length>1) {
            // we need to load 2.1 API from side. so add them to the mask
            mask.add("javax.xml.bind.");
            mask.add("javax.xml.ws.");
        }

        // first create a protected area so that we load JAXB/WS 2.1 API
        // and everything that depends on them inside
        cl = new MaskingClassLoader(cl,mask);

        // then this classloader loads the API and tools.jar
        cl = new URLClassLoader(urls, cl);

        // finally load the rest of the RI. The actual class files are loaded from ancestors
        cl = new ParallelWorldClassLoader(cl,"");

        return cl;
    }

    /**
     * Creates a classloader for loading JAXB/WS 2.1 jar and tools.jar
     */
    private static URL[] findIstackAPIs(ClassLoader cl) throws ClassNotFoundException, MalformedURLException, ToolsJarNotFoundException {
        List<URL> urls = new ArrayList<URL>();

        if(Service.class.getClassLoader()==null) {
            // JAX-WS API is loaded from bootstrap classloader
            URL res = cl.getResource("javax/xml/ws/EndpointReference.class");
            if(res==null)
                throw new ClassNotFoundException("There's no JAX-WS 2.1 API in the classpath");
            urls.add(ParallelWorldClassLoader.toJarUrl(res));

            res = cl.getResource("javax/xml/bind/annotation/XmlSeeAlso.class");
            if(res==null)
                throw new ClassNotFoundException("There's no JAXB 2.1 API in the classpath");
            urls.add(ParallelWorldClassLoader.toJarUrl(res));
        }

        findToolsJar(cl, urls);

        return urls.toArray(new URL[urls.size()]);
    }

    private static void findToolsJar(ClassLoader cl, List<URL> urls) throws ToolsJarNotFoundException, MalformedURLException {
        try {
            Class.forName("com.sun.tools.javac.Main",false,cl);
            Class.forName("com.sun.tools.apt.Main",false,cl);
            // we can already load them in the parent class loader.
            // so no need to look for tools.jar.
            // this happens when we are run inside IDE/Ant, or
            // in Mac OS.
        } catch (ClassNotFoundException e) {
            // otherwise try to find tools.jar
            File jreHome = new File(System.getProperty("java.home"));
            File toolsJar = new File( jreHome.getParent(), "lib/tools.jar" );

            if (!toolsJar.exists()) {
                throw new ToolsJarNotFoundException(toolsJar);
            }
            urls.add(toolsJar.toURL());
        }
    }

    /**
     * The list of package prefixes we want the
     * {@link MaskingClassLoader} to prevent the parent
     * classLoader from loading
     */
    public static String[] maskedPackages = new String[]{
        "com.sun.istack.internal.tools.",
        "com.sun.tools.internal.jxc.",
        "com.sun.tools.internal.xjc.",
        "com.sun.tools.internal.ws.",
        "com.sun.codemodel.internal.",
        "com.sun.relaxng.",
        "com.sun.xml.internal.xsom.",
        "com.sun.xml.internal.bind.",
        "com.sun.xml.internal.ws."
    };

    /**
     * Escape hatch to work around IBM JDK problem.
     * See http://www-128.ibm.com/developerworks/forums/dw_thread.jsp?nav=false&forum=367&thread=164718&cat=10
     */
    public static boolean noSystemProxies = false;

    static {
        try {
            noSystemProxies = Boolean.getBoolean(Invoker.class.getName()+".noSystemProxies");
        } catch(SecurityException e) {
            // ignore
        }
    }
}
