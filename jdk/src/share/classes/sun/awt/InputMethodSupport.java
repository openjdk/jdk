/*
 * Copyright 2000 Sun Microsystems, Inc.  All Rights Reserved.
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

package sun.awt;

import java.awt.AWTException;
import java.awt.Window;
import java.awt.im.spi.InputMethodDescriptor;
import java.util.Locale;
import sun.awt.im.InputContext;

/**
 * Input method support for toolkits
 */
public interface InputMethodSupport {
    /**
     * Returns a new input method adapter descriptor for native input methods.
     */
    InputMethodDescriptor getInputMethodAdapterDescriptor()
        throws AWTException;
    /**
     * Returns a new input method window for the platform
     */
    Window createInputMethodWindow(String title, InputContext context);

    /**
     * Returns whether input methods are enabled on the platform
     */
    boolean enableInputMethodsForTextComponent();

    /**
     * Returns the default keyboard locale of the underlying operating system.
     */
    Locale getDefaultKeyboardLocale();
}
