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
package com.sun.xml.internal.ws.developer;

import javax.xml.ws.spi.WebServiceFeatureAnnotation;
import java.lang.annotation.*;
import java.io.File;

/**
 * This feature represents the use of StreamingAttachment attachments with a
 * web service.
 *
 * <p>
 * for e.g.: To keep all MIME attachments in memory, do the following
 *
 * <pre>
 * &#64;WebService
 * &#64;MIME(memoryThreshold=-1L)
 * public class HelloService {
 * }
 * </pre>
 *
 * @see StreamingAttachmentFeature
 *
 * @author Jitendra Kotamraju
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Documented
@WebServiceFeatureAnnotation(id = StreamingAttachmentFeature.ID, bean = StreamingAttachmentFeature.class)
public @interface StreamingAttachment {

    /**
     * Directory in which large attachments are stored. {@link File#createTempFile}
     * methods are used to create temp files for storing attachments. This
     * value is used in {@link File#createTempFile}, if specified. If a file
     * cannot be created in this dir, then all the content is kept in memory.
     */
    String dir() default "";

    /**
     * MIME message is parsed eagerly.
     */
    boolean parseEagerly() default false;

    /**
     * After this threshold(no of bytes per attachment), large attachment is
     * written to file system.
     *
     * If the value is -1, then all the attachment content is kept in memory.
     */
    long memoryThreshold() default 1048576L;

}
