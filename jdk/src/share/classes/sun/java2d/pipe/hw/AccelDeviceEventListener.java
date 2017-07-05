/*
 * Copyright 2007-2008 Sun Microsystems, Inc.  All Rights Reserved.
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

package sun.java2d.pipe.hw;

/**
 * An interface for receiving notifications about imminent accelerated device's
 * events. Upon receiving such event appropriate actions can be taken (for
 * example, resources associated with the device can be freed).
 */
public interface AccelDeviceEventListener {
    /**
     * Called when the device is about to be reset.
     *
     * One must release all native resources associated with the device which
     * prevent the device from being reset (such as Default Pool resources for
     * the D3D pipeline).
     *
     * It is safe to remove the listener while in the call back.
     *
     * Note: this method is called on the rendering thread,
     * do not call into user code, do not take RQ lock!
     */
    public void onDeviceReset();

    /**
     * Called when the device is about to be disposed of.
     *
     * One must release all native resources associated with the device.
     *
     * It is safe to remove the listener while in the call back.
     *
     * Note: this method is called on the rendering thread,
     * do not call into user code, do not take RQ lock!
     */
    public void onDeviceDispose();
}
