/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *   - Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *
 *   - Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer in the
 *     documentation and/or other materials provided with the distribution.
 *
 *   - Neither the name of Oracle nor the names of its
 *     contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package jdk.dynalink.test;

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import jdk.dynalink.CallSiteDescriptor;
import jdk.dynalink.NamedOperation;
import jdk.dynalink.NamespaceOperation;
import jdk.dynalink.Operation;
import jdk.dynalink.StandardNamespace;
import jdk.dynalink.StandardOperation;
import jdk.dynalink.linker.GuardedInvocation;
import jdk.dynalink.linker.GuardingDynamicLinker;
import jdk.dynalink.linker.GuardingDynamicLinkerExporter;
import jdk.dynalink.linker.LinkRequest;
import jdk.dynalink.linker.LinkerServices;
import jdk.dynalink.linker.support.SimpleLinkRequest;

/**
 * This is a dynalink pluggable linker (see http://openjdk.java.net/jeps/276).
 * This linker translater underscore_separated method names to CamelCase names
 * used in Java APIs.
 */
public final class TrustedUnderscoreNameLinkerExporter extends GuardingDynamicLinkerExporter {
    private static final Pattern UNDERSCORE_NAME = Pattern.compile("_(.)");

    // translate underscore_separated name as a CamelCase name
    private static String translateToCamelCase(final String name) {
        final Matcher m = UNDERSCORE_NAME.matcher(name);
        final StringBuilder buf = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(buf, m.group(1).toUpperCase());
        }
        m.appendTail(buf);
        return buf.toString();
    }

    @Override
    public List<GuardingDynamicLinker> get() {
        final ArrayList<GuardingDynamicLinker> linkers = new ArrayList<>();
        linkers.add(new GuardingDynamicLinker() {
            @Override
            public GuardedInvocation getGuardedInvocation(final LinkRequest request,
                final LinkerServices linkerServices) throws Exception {
                final CallSiteDescriptor desc = request.getCallSiteDescriptor();
                final Operation op = desc.getOperation();
                final Object name = NamedOperation.getName(op);
                final Operation namespaceOp = NamedOperation.getBaseOperation(op);
                // is this a named GET_METHOD?
                final boolean isGetMethod =
                        NamespaceOperation.getBaseOperation(namespaceOp) == StandardOperation.GET
                        && StandardNamespace.findFirst(namespaceOp) == StandardNamespace.METHOD;
                if (isGetMethod && name instanceof String) {
                    final String str = (String)name;
                    if (str.indexOf('_') == -1) {
                        return null;
                    }

                    final String nameStr = translateToCamelCase(str);
                    // create a new call descriptor to use translated name
                    final CallSiteDescriptor newDesc = AccessController.doPrivileged(
                        new PrivilegedAction<CallSiteDescriptor>() {
                            @Override
                            public CallSiteDescriptor run() {
                                return desc.changeOperation(((NamedOperation)op).changeName(nameStr));
                            }
                        });
                    // create a new Link request to link the call site with translated name
                    final LinkRequest newRequest = request.replaceArguments(newDesc, request.getArguments());
                    // return guarded invocation linking the translated request
                    return linkerServices.getGuardedInvocation(newRequest);
                }

                return null;
            }
        });
        return linkers;
    }
}
