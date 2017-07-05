/*
 * Portions Copyright 2006 Sun Microsystems, Inc.  All Rights Reserved.
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

package com.sun.tools.internal.ws.processor.util;

import java.net.URLClassLoader;
import java.util.Iterator;

import com.sun.tools.internal.ws.processor.generator.Names;
import com.sun.xml.internal.ws.util.localization.Localizable;

import com.sun.mirror.apt.Filer;

/**
 *
 * @author WS Development Team
 */
public interface ProcessorEnvironment {

    /*
     * Flags
     */
    int F_VERBOSE       = 1 << 0;
    int F_WARNINGS      = 1 << 1;

    /**
     * Set the environment flags
     */
    public void setFlags(int flags);

    /**
     * Get the environment flags
     */
    public int getFlags();

    /**
     * Get the ClassPath.
     */
    public String getClassPath();

    /**
     * Is verbose turned on
     */
    public boolean verbose();

    /**
     * Remember a generated file and its type so that it
     * can be removed later, if appropriate.
     */
    public void addGeneratedFile(GeneratedFileInfo file);

    public Filer getFiler();
    public void setFiler(Filer filer);

    /**
     * Delete all the generated files made during the execution of this
     * environment (those that have been registered with the "addGeneratedFile"
     * method)
     */
    public void deleteGeneratedFiles();

    /**
     * Get a URLClassLoader from using the classpath
     */
    public URLClassLoader getClassLoader();

    public Iterator getGeneratedFiles();

    /**
     * Release resources, if any.
     */
    public void shutdown();

    public void error(Localizable msg);

    public void warn(Localizable msg);

    public void info(Localizable msg);

    public void printStackTrace(Throwable t);

    public Names getNames();

    public int getErrorCount();
    public int getWarningCount();
}
