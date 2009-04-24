/*
 * Copyright 2005-2006 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
package com.sun.codemodel.internal.fmt;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;

import com.sun.codemodel.internal.JResourceFile;

/**
 * Allows an application to copy a resource file to the output.
 *
 * @author
 *     Kohsuke Kawaguchi (kohsuke.kawaguchi@sun.com)
 */
public final class JStaticFile extends JResourceFile {

    private final ClassLoader classLoader;
    private final String resourceName;
    private final boolean isResource;

    public JStaticFile(String _resourceName) {
        this(_resourceName,!_resourceName.endsWith(".java"));
    }

    public JStaticFile(String _resourceName,boolean isResource) {
        this( JStaticFile.class.getClassLoader(), _resourceName, isResource );
    }

    /**
     * @param isResource
     *      false if this is a Java source file. True if this is other resource files.
     */
    public JStaticFile(ClassLoader _classLoader, String _resourceName, boolean isResource) {
        super(_resourceName.substring(_resourceName.lastIndexOf('/')+1));
        this.classLoader = _classLoader;
        this.resourceName = _resourceName;
        this.isResource = isResource;
    }

    protected boolean isResource() {
        return isResource;
    }

    protected void build(OutputStream os) throws IOException {
        DataInputStream dis = new DataInputStream(classLoader.getResourceAsStream(resourceName));

        byte[] buf = new byte[256];
        int sz;
        while( (sz=dis.read(buf))>0 )
            os.write(buf,0,sz);

        dis.close();
    }

}
