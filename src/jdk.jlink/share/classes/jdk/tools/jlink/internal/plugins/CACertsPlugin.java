/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.tools.jlink.internal.plugins;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.util.HashMap;
import java.util.Map;

import jdk.tools.jlink.internal.ResourcePoolEntryFactory;
import jdk.tools.jlink.plugin.PluginException;
import jdk.tools.jlink.plugin.ResourcePool;
import jdk.tools.jlink.plugin.ResourcePoolBuilder;
import jdk.tools.jlink.plugin.ResourcePoolEntry;

/**
 * Creates the cacerts keystore in the output image with the certificates of
 * the specified aliases only.
 */
public class CACertsPlugin extends AbstractPlugin {

    private static final String RES = "/java.base/lib/security/cacerts";

    // cacerts keystore aliases
    private String[] aliases;

    public CACertsPlugin() {
        super("cacerts");
    }

    @Override
    public Category getType() {
        return Category.TRANSFORMER;
    }

    @Override
    public boolean hasArguments() {
        return true;
    }

    @Override
    public void configure(Map<String, String> config) {
        String option = config.get(getName());
        if (option == null) {
            throw new AssertionError();
        }
        // If alias has a comma in it, this won't work, but no cacerts
        // aliases have commas.
        aliases = option.split(",");
    }

    @Override
    public ResourcePool transform(ResourcePool in, ResourcePoolBuilder out) {
        in.transformAndCopy(res -> {
            if (res.type() == ResourcePoolEntry.Type.NATIVE_LIB &&
                    res.path().equals(RES)) {
                byte[] cacerts = transformCACerts(res.content());
                return ResourcePoolEntryFactory.create(res, cacerts);
            }
            return res;
        }, out);
        return out.build();
    }

    /**
     * Creates a keystore containing only the certificates of the specified
     * aliases.
     */
    private byte[] transformCACerts(InputStream content) {
        try {
            var ks = KeyStore.getInstance("PKCS12");
            ks.load(content, null);
            Map<String, Certificate> certs = new HashMap<>(aliases.length);
            for (var alias : aliases) {
                var cert = ks.getCertificate(alias);
                if (cert == null) {
                    throw new PluginException(
                        "alias " + alias + " does not exist");
                }
                certs.put(alias, cert);
            }
            ks.load(null, null);
            for (var entry : certs.entrySet()) {
                ks.setCertificateEntry(entry.getKey(), entry.getValue());
            }
            var baos = new ByteArrayOutputStream();
            ks.store(baos, null);
            return baos.toByteArray();
        } catch (Exception ex) {
            throw new PluginException(ex);
        }
    }
}
