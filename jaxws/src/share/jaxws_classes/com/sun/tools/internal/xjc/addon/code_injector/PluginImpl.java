/*
 * Copyright (c) 1997, 2011, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.internal.xjc.addon.code_injector;

import java.util.Collections;
import java.util.List;

import com.sun.tools.internal.xjc.Options;
import com.sun.tools.internal.xjc.Plugin;
import com.sun.tools.internal.xjc.model.CPluginCustomization;
import com.sun.tools.internal.xjc.outline.ClassOutline;
import com.sun.tools.internal.xjc.outline.Outline;
import com.sun.tools.internal.xjc.util.DOMUtils;

import org.xml.sax.ErrorHandler;

/**
 * Entry point of a plugin.
 *
 * See the javadoc of {@link Plugin} for what those methods mean.
 *
 * @author Kohsuke Kawaguchi
 */
public class PluginImpl extends Plugin {
    public String getOptionName() {
        return "Xinject-code";
    }

    public List<String> getCustomizationURIs() {
        return Collections.singletonList(Const.NS);
    }

    public boolean isCustomizationTagName(String nsUri, String localName) {
        return nsUri.equals(Const.NS) && localName.equals("code");
    }

    public String getUsage() {
        return "  -Xinject-code      :  inject specified Java code fragments into the generated code";
    }

    // meat of the processing
    public boolean run(Outline model, Options opt, ErrorHandler errorHandler) {
        for( ClassOutline co : model.getClasses() ) {
            CPluginCustomization c = co.target.getCustomizations().find(Const.NS,"code");
            if(c==null)
                continue;   // no customization --- nothing to inject here

            c.markAsAcknowledged();
            // TODO: ideally you should validate this DOM element to make sure
            // that there's no typo/etc. JAXP 1.3 can do this very easily.
            String codeFragment = DOMUtils.getElementText(c.element);

            // inject the specified code fragment into the implementation class.
            co.implClass.direct(codeFragment);
        }

        return true;
    }
}
