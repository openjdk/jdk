/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
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

package nsk.share;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicReference;


public final class MainWrapper {
    public static final String OLD_MAIN_THREAD_NAME = "old-m-a-i-n";

    static AtomicReference<Throwable> ue = new AtomicReference<>();
    public MainWrapper() {
    }

    public static void main(String[] args) throws Throwable {
        String wrapperName = args[0];
        String className = args[1];
        String[] classArgs = new String[args.length - 2];
        System.arraycopy(args, 2, classArgs, 0, args.length - 2);

        // It is needed to register finalizer thread in default thread group
        // So FinalizerThread thread can't be in virtual threads group
        Finalizer finalizer = new Finalizer(new FinalizableObject());
        finalizer.activate();



        Runnable task = () -> {
            try {
                Class<?> c = Class.forName(className);
                Method mainMethod = c.getMethod("main", new Class[] { String[].class });
                mainMethod.setAccessible(true);
                mainMethod.invoke(null, new Object[] { classArgs });
            } catch (InvocationTargetException e) {
                e.printStackTrace();
                ue.set(e.getCause());
            } catch (Exception e) {
                e.printStackTrace();
            }
        };

        Thread t;
        if (wrapperName.equals("Virtual")) {
            t = unstartedVirtualThread(task);
        } else {
            t = new Thread(task);
        }
        t.setName("main");
        Thread.currentThread().setName(OLD_MAIN_THREAD_NAME);
        t.start();
        t.join();
        if (ue.get() != null) {
            throw ue.get();
        }
    }

    static Thread unstartedVirtualThread(Runnable task) {
        try {
            Object builder = Thread.class.getMethod("ofVirtual").invoke(null);
            Class<?> clazz = Class.forName("java.lang.Thread$Builder");
            Method unstarted = clazz.getMethod("unstarted", Runnable.class);
            return (Thread) unstarted.invoke(builder, task);
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
