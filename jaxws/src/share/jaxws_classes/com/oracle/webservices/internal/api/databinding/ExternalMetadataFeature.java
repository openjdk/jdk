/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 1997-2013 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * http://glassfish.java.net/public/CDDL+GPL_1_1.html
 * or packager/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at packager/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */

package com.oracle.webservices.internal.api.databinding;

import com.sun.xml.internal.ws.api.databinding.MetadataReader;
import com.sun.xml.internal.ws.model.ExternalMetadataReader;

import javax.xml.ws.WebServiceFeature;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * WebServiceFeature allowing to define either on server or client side external xml descriptors replacing/supplementing
 * WS metadata provided by class annotations. This can be useful if those annotations are missing (existing non-WS
 * components) or if it is necessary to override those.
 *
 * @author Miroslav Kos (miroslav.kos at oracle.com)
 */
public class ExternalMetadataFeature extends WebServiceFeature {

    private static final String ID = "com.oracle.webservices.internal.api.databinding.ExternalMetadataFeature";

    /**
     * Enable this feature.  Defaults to true.
     */
    private boolean enabled = true;

    private List<String> resourceNames;
    private List<File> files;

    private ExternalMetadataFeature() {
    }

    public void addResources(String... resourceNames) {
        if (this.resourceNames == null) {
            this.resourceNames = new ArrayList<String>();
        }
        Collections.addAll(this.resourceNames, resourceNames);
    }

    public List<String> getResourceNames() { return resourceNames; }

    public void addFiles(File... files) {
        if (this.files == null) {
            this.files = new ArrayList<File>();
        }
        Collections.addAll(this.files, files);
    }

    public List<File> getFiles() { return files; }

    public boolean isEnabled() {
        return enabled;
    }

    private void setEnabled(final boolean x) {
        enabled = x;
    }

    @Override
    public String getID() {
        return ID;
    }

    public MetadataReader getMetadataReader(ClassLoader classLoader, boolean disableSecureXmlProcessing) {
        return enabled ? new ExternalMetadataReader(files, resourceNames, classLoader, true, disableSecureXmlProcessing) : null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ExternalMetadataFeature that = (ExternalMetadataFeature) o;

        if (enabled != that.enabled) return false;
        if (files != null ? !files.equals(that.files) : that.files != null) return false;
        if (resourceNames != null ? !resourceNames.equals(that.resourceNames) : that.resourceNames != null)
            return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = (enabled ? 1 : 0);
        result = 31 * result + (resourceNames != null ? resourceNames.hashCode() : 0);
        result = 31 * result + (files != null ? files.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "[" + getID() +
                ", enabled=" + enabled +
                ", resourceNames=" + resourceNames +
                ", files=" + files +
                ']';
    }

    public static Builder builder() {
        return new Builder(new ExternalMetadataFeature());
    }

    public final static class Builder {
        final private ExternalMetadataFeature o;

        Builder(final ExternalMetadataFeature x) {
            o = x;
        }

        public ExternalMetadataFeature build() {
            return o;
        }

        public Builder addResources(String... res) {
            o.addResources(res);
            return this;
        }

        public Builder addFiles(File... files) {
            o.addFiles(files);
            return this;
        }

        public Builder setEnabled(boolean enabled) {
            o.setEnabled(enabled);
            return this;
        }

    }
}
