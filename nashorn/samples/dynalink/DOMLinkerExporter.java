/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.List;
import jdk.dynalink.CallSiteDescriptor;
import jdk.dynalink.CompositeOperation;
import jdk.dynalink.NamedOperation;
import jdk.dynalink.Operation;
import jdk.dynalink.StandardOperation;
import jdk.dynalink.linker.GuardingDynamicLinker;
import jdk.dynalink.linker.GuardingDynamicLinkerExporter;
import jdk.dynalink.linker.GuardedInvocation;
import jdk.dynalink.linker.TypeBasedGuardingDynamicLinker;
import jdk.dynalink.linker.LinkRequest;
import jdk.dynalink.linker.LinkerServices;
import jdk.dynalink.linker.support.Guards;
import jdk.dynalink.linker.support.Lookup;
import org.w3c.dom.Attr;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Element;

/**
 * This is a dynalink pluggable linker (see http://openjdk.java.net/jeps/276).
 * This linker handles XML DOM Element objects specially. This linker links
 * special properties starting with "_" and treats those as child element names
 * to access. This kind of child element access makes it easy to write XML DOM
 * accessing scripts. See for example ./dom_linker_gutenberg.js.
 */
public final class DOMLinkerExporter extends GuardingDynamicLinkerExporter {
    static {
        System.out.println("pluggable dynalink DOM linker loaded");
    }

    // return List of child Elements of the given Element matching the given name.
    private static List<Element> getChildElements(Element elem, String name) {
        NodeList nodeList = elem.getChildNodes();
        List<Element> childElems = new ArrayList<>();
        int len = nodeList.getLength();
        for (int i = 0; i < len; i++) {
            Node node = nodeList.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE &&
                ((Element)node).getTagName().equals(name)) {
                childElems.add((Element)node);
            }
        }
        return childElems;
    }

    // method that returns either unique child element matching given name
    // or a list of child elements of that name (if there are more than one matches).
    public static Object getElementsByName(Object elem, final String name) {
        List<Element> elems = getChildElements((Element)elem, name);
        return elems.size() == 1? elems.get(0) : elems;
    }

    // method to extract text context under a given DOM Element
    public static Object getElementText(Object elem) {
        NodeList nodeList = ((Element)elem).getChildNodes();
        int len = nodeList.getLength();
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < len; i++) {
            Node node = nodeList.item(i);
            if (node.getNodeType() == Node.TEXT_NODE) {
                text.append(node.getNodeValue());
            }
        }
        return text.toString();
    }

    private static final MethodHandle ELEMENTS_BY_NAME;
    private static final MethodHandle ELEMENT_TEXT;
    private static final MethodHandle IS_ELEMENT;
    static {
        ELEMENTS_BY_NAME = Lookup.PUBLIC.findStatic(DOMLinkerExporter.class,
            "getElementsByName",
            MethodType.methodType(Object.class, Object.class, String.class));
        ELEMENT_TEXT = Lookup.PUBLIC.findStatic(DOMLinkerExporter.class,
            "getElementText",
            MethodType.methodType(Object.class, Object.class));
        IS_ELEMENT = Guards.isInstance(Element.class, MethodType.methodType(Boolean.TYPE, Object.class));
    }

    @Override
    public List<GuardingDynamicLinker> get() {
        final ArrayList<GuardingDynamicLinker> linkers = new ArrayList<>();
        linkers.add(new TypeBasedGuardingDynamicLinker() {
            @Override
            public boolean canLinkType(final Class<?> type) {
                return Element.class.isAssignableFrom(type);
            }

            @Override
            public GuardedInvocation getGuardedInvocation(LinkRequest request,
                LinkerServices linkerServices) throws Exception {
                final Object self = request.getReceiver();
                if (! (self instanceof Element)) {
                    return null;
                }

                CallSiteDescriptor desc = request.getCallSiteDescriptor();
                Operation op = desc.getOperation();
                Object name = NamedOperation.getName(op);
                boolean getProp = CompositeOperation.contains(
                        NamedOperation.getBaseOperation(op),
                        StandardOperation.GET_PROPERTY);
                if (getProp && name instanceof String) {
                    String nameStr = (String)name;

                    // Treat names starting with "_" as special names.
                    // Everything else is linked other dynalink bean linker!
                    // This avoids collision with Java methods of org.w3c.dom.Element class
                    // Assumption is that Java APIs won't start with "_" character!!
                    if (nameStr.equals("_")) {
                        // short-hand to get text content under a given DOM Element
                        return new GuardedInvocation(ELEMENT_TEXT, IS_ELEMENT);
                    } else if (nameStr.startsWith("_")) {
                        return new GuardedInvocation(
                            MethodHandles.insertArguments(ELEMENTS_BY_NAME, 1, nameStr.substring(1)),
                            IS_ELEMENT);
                    }

                }

                return null;
            }
        });
        return linkers;
    }
}
