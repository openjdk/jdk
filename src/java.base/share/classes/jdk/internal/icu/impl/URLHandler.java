// Copyright 2016 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html
/*
 ******************************************************************************
 * Copyright (C) 2005-2015, International Business Machines Corporation and
 * others. All Rights Reserved.
 ******************************************************************************
 */

package jdk.internal.icu.impl;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

@SuppressWarnings("deprecation")
public abstract class URLHandler {
    public static final String PROPNAME = "urlhandler.props";

    private static final Map<String, Method> handlers;

    private static final boolean DEBUG = ICUDebug.enabled("URLHandler");

    static {
        Map<String, Method> h = null;

        BufferedReader br = null;
        try {
            @SuppressWarnings("resource")  // Closed by BufferedReader.
            ClassLoader loader = ClassLoaderUtil.getClassLoader(URLHandler.class);
            InputStream is = loader.getResourceAsStream(PROPNAME);

            if (is != null) {
                Class<?>[] params = { URL.class };
                br = new BufferedReader(new InputStreamReader(is));

                for (String line = br.readLine(); line != null; line = br.readLine()) {
                    line = line.trim();

                    if (line.length() == 0 || line.charAt(0) == '#') {
                        continue;
                    }

                    int ix = line.indexOf('=');

                    if (ix == -1) {
                        if (DEBUG) System.err.println("bad urlhandler line: '" + line + "'");
                        break;
                    }

                    String key = line.substring(0, ix).trim();
                    String value = line.substring(ix+1).trim();

                    try {
                        Class<?> cl = Class.forName(value);
                        Method m = cl.getDeclaredMethod("get", params);

                        if (h == null) {
                            h = new HashMap<String, Method>();
                        }

                        h.put(key, m);
                    }
                    catch (ClassNotFoundException e) {
                        if (DEBUG) System.err.println(e);
                    }
                    catch(NoSuchMethodException e) {
                        if (DEBUG) System.err.println(e);
                    }
                    catch(SecurityException e) {
                        if (DEBUG) System.err.println(e);
                    }
                }
                br.close();
            }
        } catch (Throwable t) {
            if (DEBUG) System.err.println(t);
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (IOException ignored) {
                }
            }
        }

        handlers = h;
    }

    public static URLHandler get(URL url) {
        if (url == null) {
            return null;
        }

        String protocol = url.getProtocol();

        if (handlers != null) {
            Method m = handlers.get(protocol);

            if (m != null) {
                try {
                    URLHandler handler = (URLHandler)m.invoke(null, new Object[] { url });

                    if (handler != null) {
                        return handler;
                    }
                }
                catch(IllegalAccessException e) {
                    if (DEBUG) System.err.println(e);
                }
                catch(IllegalArgumentException e) {
                    if (DEBUG) System.err.println(e);
                }
                catch(InvocationTargetException e) {
                    if (DEBUG) System.err.println(e);
                }
            }
        }

        return getDefault(url);
    }

    protected static URLHandler getDefault(URL url) {
        URLHandler handler = null;

        String protocol = url.getProtocol();
        try {
            if (protocol.equals("file")) {
                handler = new FileURLHandler(url);
            } else if (protocol.equals("jar") || protocol.equals("wsjar")) {
                handler = new JarURLHandler(url);
            }
        } catch (Exception e) {
            // ignore - just return null
        }
        return handler;
    }

    private static class FileURLHandler extends URLHandler {
        File file;

        FileURLHandler(URL url) {
            try {
                file = new File(url.toURI());
            } catch (URISyntaxException use) {
                // fall through
            }
            if (file == null || !file.exists()) {
                if (DEBUG) System.err.println("file does not exist - " + url.toString());
                throw new IllegalArgumentException();
            }
        }

        @Override
        public void guide(URLVisitor v, boolean recurse, boolean strip) {
            if (file.isDirectory()) {
                process(v, recurse, strip, "/", file.listFiles());
            } else {
                v.visit(file.getName());
            }
        }

        private void process(URLVisitor v, boolean recurse, boolean strip, String path, File[] files) {
            if (files != null) {
                for (int i = 0; i < files.length; i++) {
                    File f = files[i];

                    if (f.isDirectory()) {
                        if (recurse) {
                            process(v, recurse, strip, path + f.getName()+ '/', f.listFiles());
                        }
                    } else {
                        v.visit(strip? f.getName() : path + f.getName());
                    }
                }
            }
        }
    }

    private static class JarURLHandler extends URLHandler {
        JarFile jarFile;
        String prefix;

        JarURLHandler(URL url) {
            try {
                prefix = url.getPath();

                int ix = prefix.lastIndexOf("!/");

                if (ix >= 0) {
                    prefix = prefix.substring(ix + 2); // truncate after "!/"
                }

                String protocol = url.getProtocol();
                if (!protocol.equals("jar")) {
                    // change the protocol to "jar"
                    // Note: is this really OK?
                    String urlStr = url.toString();
                    int idx = urlStr.indexOf(":");
                    if (idx != -1) {
                        url = new URL("jar" + urlStr.substring(idx));
                    }
                }

                JarURLConnection conn = (JarURLConnection)url.openConnection();
                jarFile = conn.getJarFile();
            }
            catch (Exception e) {
                if (DEBUG) System.err.println("icurb jar error: " + e);
                throw new IllegalArgumentException("jar error: " + e.getMessage());
            }
        }

        @Override
        public void guide(URLVisitor v, boolean recurse, boolean strip) {
            try {
                Enumeration<JarEntry> entries = jarFile.entries();

                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();

                    if (!entry.isDirectory()) { // skip just directory paths
                        String name = entry.getName();

                        if (name.startsWith(prefix)) {
                            name = name.substring(prefix.length());
                            int ix = name.lastIndexOf('/');
                            if (ix > 0 && !recurse) {
                                continue;
                            }
                            if (strip && ix != -1) {
                                name = name.substring(ix+1);
                            }
                            v.visit(name);
                        }
                    }
                }
            }
            catch (Exception e) {
                if (DEBUG) System.err.println("icurb jar error: " + e);
            }
        }
    }

    public void guide(URLVisitor visitor, boolean recurse)
    {
        guide(visitor, recurse, true);
    }

    public abstract void guide(URLVisitor visitor, boolean recurse, boolean strip);

    public interface URLVisitor {
        void visit(String str);
    }
}
