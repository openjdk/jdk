/*
 * Copyright (c) 2014, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.mxtool.junit;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.runner.Description;
import org.junit.runner.Request;
import org.junit.runner.notification.Failure;

public final class MxJUnitRequest {

    private final Request request;

    final Set<Class<?>> classes;
    final String methodName;

    final List<Failure> missingClasses;

    private MxJUnitRequest(Request request, Set<Class<?>> classes, String methodName, List<Failure> missingClasses) {
        this.request = request;
        this.classes = classes;
        this.methodName = methodName;
        this.missingClasses = missingClasses;
    }

    public Request getRequest() {
        return request;
    }

    public static final class BuilderException extends Exception {

        private static final long serialVersionUID = 1L;

        private BuilderException(String msg) {
            super(msg);
        }
    }

    public static class Builder {

        private final Set<Class<?>> classes = new LinkedHashSet<>();
        private String methodName = null;
        private final List<Failure> missingClasses = new ArrayList<>();

        protected Class<?> resolveClass(String name) throws ClassNotFoundException {
            return Class.forName(name, false, Builder.class.getClassLoader());
        }

        public void addTestSpec(String arg) throws BuilderException {
            String className;
            /*
             * Entries of the form class#method are handled specially. Only one can be specified on
             * the command line as there's no obvious way to build a runner for multiple ones.
             */
            if (methodName != null) {
                throw new BuilderException("Only a single class and method can be specified: " + arg);
            } else if (arg.contains("#")) {
                String[] pair = arg.split("#");
                if (pair.length != 2) {
                    throw new BuilderException("Malformed class and method request: " + arg);
                } else if (!classes.isEmpty()) {
                    throw new BuilderException("Only a single class and method can be specified: " + arg);
                } else {
                    methodName = pair[1];
                    className = pair[0];
                }
            } else {
                className = arg;
            }
            try {
                Class<?> cls = resolveClass(className);
                if ((cls.getModifiers() & Modifier.ABSTRACT) == 0) {
                    classes.add(cls);
                }
            } catch (ClassNotFoundException e) {
                Description description = Description.createSuiteDescription(className);
                Failure failure = new Failure(description, e);
                missingClasses.add(failure);
            }
        }

        public MxJUnitRequest build() {
            Request request;
            if (methodName == null) {
                request = Request.classes(classes.toArray(new Class<?>[0]));
            } else {
                request = Request.method(classes.iterator().next(), methodName);
            }
            return new MxJUnitRequest(request, classes, methodName, missingClasses);
        }
    }
}
