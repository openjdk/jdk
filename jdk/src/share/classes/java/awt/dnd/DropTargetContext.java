/*
 * Copyright (c) 1997, 2004, Oracle and/or its affiliates. All rights reserved.
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

package java.awt.dnd;

import java.awt.Component;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;

import java.awt.dnd.peer.DropTargetContextPeer;

import java.io.IOException;
import java.io.Serializable;

import java.util.Arrays;
import java.util.List;


/**
 * A <code>DropTargetContext</code> is created
 * whenever the logical cursor associated
 * with a Drag and Drop operation coincides with the visible geometry of
 * a <code>Component</code> associated with a <code>DropTarget</code>.
 * The <code>DropTargetContext</code> provides
 * the mechanism for a potential receiver
 * of a drop operation to both provide the end user with the appropriate
 * drag under feedback, but also to effect the subsequent data transfer
 * if appropriate.
 *
 * @since 1.2
 */

public class DropTargetContext implements Serializable {

    private static final long serialVersionUID = -634158968993743371L;

    /**
     * Construct a <code>DropTargetContext</code>
     * given a specified <code>DropTarget</code>.
     *
     * @param dt the DropTarget to associate with
     */

    DropTargetContext(DropTarget dt) {
        super();

        dropTarget = dt;
    }

    /**
     * This method returns the <code>DropTarget</code> associated with this
     * <code>DropTargetContext</code>.
     *
     * @return the <code>DropTarget</code> associated with this <code>DropTargetContext</code>
     */

    public DropTarget getDropTarget() { return dropTarget; }

    /**
     * This method returns the <code>Component</code> associated with
     * this <code>DropTargetContext</code>.
     *
     * @return the Component associated with this Context
     */

    public Component getComponent() { return dropTarget.getComponent(); }

    /**
     * Called when associated with the <code>DropTargetContextPeer</code>.
     *
     * @param dtcp the <code>DropTargetContextPeer</code>
     */

    public void addNotify(DropTargetContextPeer dtcp) {
        dropTargetContextPeer = dtcp;
    }

    /**
     * Called when disassociated with the <code>DropTargetContextPeer</code>.
     */

    public void removeNotify() {
        dropTargetContextPeer = null;
        transferable          = null;
    }

    /**
     * This method sets the current actions acceptable to
     * this <code>DropTarget</code>.
     *
     * @param actions an <code>int</code> representing the supported action(s)
     */

    protected void setTargetActions(int actions) {
        DropTargetContextPeer peer = getDropTargetContextPeer();
        if (peer != null) {
            synchronized (peer) {
                peer.setTargetActions(actions);
                getDropTarget().doSetDefaultActions(actions);
            }
        } else {
            getDropTarget().doSetDefaultActions(actions);
        }
    }

    /**
     * This method returns an <code>int</code> representing the
     * current actions this <code>DropTarget</code> will accept.
     *
     * @return the current actions acceptable to this <code>DropTarget</code>
     */

    protected int getTargetActions() {
        DropTargetContextPeer peer = getDropTargetContextPeer();
        return ((peer != null)
                        ? peer.getTargetActions()
                        : dropTarget.getDefaultActions()
        );
    }

    /**
     * This method signals that the drop is completed and
     * if it was successful or not.
     *
     * @param success true for success, false if not
     *
     * @throws InvalidDnDOperationException if a drop is not outstanding/extant
     */

    public void dropComplete(boolean success) throws InvalidDnDOperationException{
        DropTargetContextPeer peer = getDropTargetContextPeer();
        if (peer != null) {
            peer.dropComplete(success);
        }
    }

    /**
     * accept the Drag.
     *
     * @param dragOperation the supported action(s)
     */

    protected void acceptDrag(int dragOperation) {
        DropTargetContextPeer peer = getDropTargetContextPeer();
        if (peer != null) {
            peer.acceptDrag(dragOperation);
        }
    }

    /**
     * reject the Drag.
     */

    protected void rejectDrag() {
        DropTargetContextPeer peer = getDropTargetContextPeer();
        if (peer != null) {
            peer.rejectDrag();
        }
    }

    /**
     * called to signal that the drop is acceptable
     * using the specified operation.
     * must be called during DropTargetListener.drop method invocation.
     *
     * @param dropOperation the supported action(s)
     */

    protected void acceptDrop(int dropOperation) {
        DropTargetContextPeer peer = getDropTargetContextPeer();
        if (peer != null) {
            peer.acceptDrop(dropOperation);
        }
    }

    /**
     * called to signal that the drop is unacceptable.
     * must be called during DropTargetListener.drop method invocation.
     */

    protected void rejectDrop() {
        DropTargetContextPeer peer = getDropTargetContextPeer();
        if (peer != null) {
            peer.rejectDrop();
        }
    }

    /**
     * get the available DataFlavors of the
     * <code>Transferable</code> operand of this operation.
     *
     * @return a <code>DataFlavor[]</code> containing the
     * supported <code>DataFlavor</code>s of the
     * <code>Transferable</code> operand.
     */

    protected DataFlavor[] getCurrentDataFlavors() {
        DropTargetContextPeer peer = getDropTargetContextPeer();
        return peer != null ? peer.getTransferDataFlavors() : new DataFlavor[0];
    }

    /**
     * This method returns a the currently available DataFlavors
     * of the <code>Transferable</code> operand
     * as a <code>java.util.List</code>.
     *
     * @return the currently available
     * DataFlavors as a <code>java.util.List</code>
     */

    protected List<DataFlavor> getCurrentDataFlavorsAsList() {
        return Arrays.asList(getCurrentDataFlavors());
    }

    /**
     * This method returns a <code>boolean</code>
     * indicating if the given <code>DataFlavor</code> is
     * supported by this <code>DropTargetContext</code>.
     *
     * @param df the <code>DataFlavor</code>
     *
     * @return if the <code>DataFlavor</code> specified is supported
     */

    protected boolean isDataFlavorSupported(DataFlavor df) {
        return getCurrentDataFlavorsAsList().contains(df);
    }

    /**
     * get the Transferable (proxy) operand of this operation
     *
     * @throws InvalidDnDOperationException if a drag is not outstanding/extant
     *
     * @return the <code>Transferable</code>
     */

    protected Transferable getTransferable() throws InvalidDnDOperationException {
        DropTargetContextPeer peer = getDropTargetContextPeer();
        if (peer == null) {
            throw new InvalidDnDOperationException();
        } else {
            if (transferable == null) {
                Transferable t = peer.getTransferable();
                boolean isLocal = peer.isTransferableJVMLocal();
                synchronized (this) {
                    if (transferable == null) {
                        transferable = createTransferableProxy(t, isLocal);
                    }
                }
            }

            return transferable;
        }
    }

    /**
     * Get the <code>DropTargetContextPeer</code>
     *
     * @return the platform peer
     */

    DropTargetContextPeer getDropTargetContextPeer() {
        return dropTargetContextPeer;
    }

    /**
     * Creates a TransferableProxy to proxy for the specified
     * Transferable.
     *
     * @param t the <tt>Transferable</tt> to be proxied
     * @param local <tt>true</tt> if <tt>t</tt> represents
     *        the result of a local drag-n-drop operation.
     * @return the new <tt>TransferableProxy</tt> instance.
     */
    protected Transferable createTransferableProxy(Transferable t, boolean local) {
        return new TransferableProxy(t, local);
    }

/****************************************************************************/


    /**
     * <code>TransferableProxy</code> is a helper inner class that implements
     * <code>Transferable</code> interface and serves as a proxy for another
     * <code>Transferable</code> object which represents data transfer for
     * a particular drag-n-drop operation.
     * <p>
     * The proxy forwards all requests to the encapsulated transferable
     * and automatically performs additional conversion on the data
     * returned by the encapsulated transferable in case of local transfer.
     */

    protected class TransferableProxy implements Transferable {

        /**
         * Constructs a <code>TransferableProxy</code> given
         * a specified <code>Transferable</code> object representing
         * data transfer for a particular drag-n-drop operation and
         * a <code>boolean</code> which indicates whether the
         * drag-n-drop operation is local (within the same JVM).
         *
         * @param t the <code>Transferable</code> object
         * @param local <code>true</code>, if <code>t</code> represents
         *        the result of local drag-n-drop operation
         */
        TransferableProxy(Transferable t, boolean local) {
            proxy = new sun.awt.datatransfer.TransferableProxy(t, local);
            transferable = t;
            isLocal      = local;
        }

        /**
         * Returns an array of DataFlavor objects indicating the flavors
         * the data can be provided in by the encapsulated transferable.
         *
         * @return an array of data flavors in which the data can be
         *         provided by the encapsulated transferable
         */
        public DataFlavor[] getTransferDataFlavors() {
            return proxy.getTransferDataFlavors();
        }

        /**
         * Returns whether or not the specified data flavor is supported by
         * the encapsulated transferable.
         * @param flavor the requested flavor for the data
         * @return <code>true</code> if the data flavor is supported,
         *         <code>false</code> otherwise
         */
        public boolean isDataFlavorSupported(DataFlavor flavor) {
            return proxy.isDataFlavorSupported(flavor);
        }

        /**
         * Returns an object which represents the data provided by
         * the encapsulated transferable for the requested data flavor.
         * <p>
         * In case of local transfer a serialized copy of the object
         * returned by the encapsulated transferable is provided when
         * the data is requested in application/x-java-serialized-object
         * data flavor.
         *
         * @param df the requested flavor for the data
         * @throws IOException if the data is no longer available
         *              in the requested flavor.
         * @throws UnsupportedFlavorException if the requested data flavor is
         *              not supported.
         */
        public Object getTransferData(DataFlavor df)
            throws UnsupportedFlavorException, IOException
        {
            return proxy.getTransferData(df);
        }

        /*
         * fields
         */

        // We don't need to worry about client code changing the values of
        // these variables. Since TransferableProxy is a protected class, only
        // subclasses of DropTargetContext can access it. And DropTargetContext
        // cannot be subclassed by client code because it does not have a
        // public constructor.

        /**
         * The encapsulated <code>Transferable</code> object.
         */
        protected Transferable  transferable;

        /**
         * A <code>boolean</code> indicating if the encapsulated
         * <code>Transferable</code> object represents the result
         * of local drag-n-drop operation (within the same JVM).
         */
        protected boolean       isLocal;

        private sun.awt.datatransfer.TransferableProxy proxy;
    }

/****************************************************************************/

    /*
     * fields
     */

    /**
     * The DropTarget associated with this DropTargetContext.
     *
     * @serial
     */
    private DropTarget dropTarget;

    private transient DropTargetContextPeer dropTargetContextPeer;

    private transient Transferable transferable;
}
