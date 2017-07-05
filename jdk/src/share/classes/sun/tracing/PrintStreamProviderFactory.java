/*
 * Copyright (c) 2008, Oracle and/or its affiliates. All rights reserved.
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

package sun.tracing;

import java.lang.reflect.Method;
import java.io.PrintStream;
import java.util.HashMap;

import com.sun.tracing.ProviderFactory;
import com.sun.tracing.Provider;
import com.sun.tracing.ProviderName;
import com.sun.tracing.Probe;
import com.sun.tracing.ProbeName;

/**
 * Factory class to create tracing Providers.
 *
 * This factory will create tracing instances that print to a PrintStream
 * when activated.
 *
 * @since 1.7
 */
public class PrintStreamProviderFactory extends ProviderFactory {

    private PrintStream stream;

    public PrintStreamProviderFactory(PrintStream stream) {
        this.stream = stream;
    }

    public <T extends Provider> T createProvider(Class<T> cls) {
        PrintStreamProvider provider = new PrintStreamProvider(cls, stream);
        provider.init();
        return provider.newProxyInstance();
    }
}

class PrintStreamProvider extends ProviderSkeleton {

    private PrintStream stream;
    private String providerName;

    protected ProbeSkeleton createProbe(Method m) {
        String probeName = getAnnotationString(m, ProbeName.class, m.getName());
        return new PrintStreamProbe(this, probeName, m.getParameterTypes());
    }

    PrintStreamProvider(Class<? extends Provider> type, PrintStream stream) {
        super(type);
        this.stream = stream;
        this.providerName = getProviderName();
    }

    PrintStream getStream() {
        return stream;
    }

    String getName() {
        return providerName;
    }
}

class PrintStreamProbe extends ProbeSkeleton {

    private PrintStreamProvider provider;
    private String name;

    PrintStreamProbe(PrintStreamProvider p, String name, Class<?>[] params) {
        super(params);
        this.provider = p;
        this.name = name;
    }

    public boolean isEnabled() {
        return true;
    }

    public void uncheckedTrigger(Object[] args) {
        StringBuffer sb = new StringBuffer();
        sb.append(provider.getName());
        sb.append(".");
        sb.append(name);
        sb.append("(");
        boolean first = true;
        for (Object o : args) {
            if (first == false) {
                sb.append(",");
            } else {
                first = false;
            }
            sb.append(o.toString());
        }
        sb.append(")");
        provider.getStream().println(sb.toString());
    }
}

