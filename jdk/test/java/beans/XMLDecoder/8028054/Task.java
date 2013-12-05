/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

abstract class Task<T> implements Runnable {
    private transient boolean working = true;
    private final List<T> methods;
    private final Thread thread;

    Task(List<T> methods) {
        this.methods = methods;
        this.thread = new Thread(this);
        this.thread.start();
    }

    boolean isAlive() {
        return this.thread.isAlive();
    }

    boolean isWorking() {
        boolean working = this.working && this.thread.isAlive();
        this.working = false;
        return working;
    }

    @Override
    public void run() {
        long time = -System.currentTimeMillis();
        for (T method : this.methods) {
            this.working = true;
            try {
                for (int i = 0; i < 100; i++) {
                    process(method);
                }
            } catch (NoSuchMethodException ignore) {
            }
        }
        time += System.currentTimeMillis();
        print("thread done in " + time / 1000 + " seconds");
    }

    protected abstract void process(T method) throws NoSuchMethodException;

    static synchronized void print(Object message) {
        System.out.println(message);
        System.out.flush();
    }

    static List<Class<?>> getClasses(int count) throws Exception {
        String resource = ClassLoader.getSystemClassLoader().getResource("java/lang/Object.class").toString();

        Pattern pattern = Pattern.compile("jar:file:(.*)!.*");
        Matcher matcher = pattern.matcher(resource);
        matcher.matches();
        resource = matcher.group(1);

        List<Class<?>> classes = new ArrayList<>();
        try (JarFile jarFile = new JarFile(resource)) {
            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                String name = entries.nextElement().getName();
                if (name.startsWith("java") && name.endsWith(".class")) {
                    classes.add(Class.forName(name.substring(0, name.indexOf(".")).replace('/', '.')));
                    if (count == classes.size()) {
                        break;
                    }
                }
            }
        }
        return classes;
    }
}
