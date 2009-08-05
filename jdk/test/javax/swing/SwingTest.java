/*
 * Copyright 2009 Sun Microsystems, Inc.  All Rights Reserved.
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

import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;
import javax.swing.JFrame;

import static javax.swing.SwingUtilities.invokeLater;

/**
 * SwingTestHelper is a utility class for writing regression tests
 * that require interacting with the UI.
 *
 * @author Sergey A. Malenkov
 */
final class SwingTest implements Runnable {

    private static final int WIDTH = 640;
    private static final int HEIGHT = 480;

    public static void start(Class<?> type) {
        new SwingTest(type).start();
    }

    private final PrintWriter writer = new PrintWriter(System.out, true);

    private Class<?> type;
    private JFrame frame;
    private Iterator<Method> methods;
    private Object object;
    private Method method;
    private Throwable error;

    private SwingTest(Class<?> type) {
        this.type = type;
    }

    public void run() {
        synchronized (this.writer) {
            if (this.error != null) {
                this.frame.dispose();
                this.frame = null;
            }
            else if (this.object == null) {
                invoke();
                Set<Method> methods = new TreeSet<Method>(new Comparator<Method>() {
                    public int compare(Method first, Method second) {
                        return first.getName().compareTo(second.getName());
                    }
                });
                for (Method method : this.type.getMethods()) {
                    if (method.getDeclaringClass().equals(this.type)) {
                        if (method.getReturnType().equals(void.class)) {
                            if (0 == method.getParameterTypes().length) {
                                methods.add(method);
                            }
                        }
                    }
                }
                this.methods = methods.iterator();
            }
            else if (this.method != null) {
                invoke();
            }
            else if (this.methods.hasNext()) {
                this.method = this.methods.next();
            }
            else {
                this.frame.dispose();
                this.frame = null;
                this.type = null;
            }
            this.writer.notifyAll();
        }
    }

    private void start() {
        synchronized (this.writer) {
            while (this.type != null) {
                if ((this.method != null) && Modifier.isStatic(this.method.getModifiers())) {
                    invoke();
                }
                else {
                    invokeLater(this);
                    try {
                        this.writer.wait();
                    }
                    catch (InterruptedException exception) {
                        exception.printStackTrace(this.writer);
                    }
                }
                if ((this.frame == null) && (this.error != null)) {
                    throw new Error("unexpected error", this.error);
                }
            }
        }
    }

    private void invoke() {
        try {
            if (this.method != null) {
                this.writer.println(this.method);
                this.method.invoke(this.object);
                this.method = null;
            }
            else {
                this.writer.println(this.type);
                this.frame = new JFrame(this.type.getSimpleName());
                this.frame.setSize(WIDTH, HEIGHT);
                this.frame.setLocationRelativeTo(null);
                this.object = this.type.getConstructor(JFrame.class).newInstance(this.frame);
                this.frame.setVisible(true);
            }
        }
        catch (NoSuchMethodException exception) {
            this.error = exception;
        }
        catch (SecurityException exception) {
            this.error = exception;
        }
        catch (IllegalAccessException exception) {
            this.error = exception;
        }
        catch (IllegalArgumentException exception) {
            this.error = exception;
        }
        catch (InstantiationException exception) {
            this.error = exception;
        }
        catch (InvocationTargetException exception) {
            this.error = exception.getTargetException();
        }
    }
}
