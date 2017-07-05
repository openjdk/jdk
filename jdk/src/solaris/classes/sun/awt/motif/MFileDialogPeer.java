/*
 * Copyright 1995-2003 Sun Microsystems, Inc.  All Rights Reserved.
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
package sun.awt.motif;

import java.awt.*;
import java.awt.peer.*;
import java.io.*;
import java.awt.datatransfer.*;
import java.util.ArrayList;
import sun.awt.datatransfer.ToolkitThreadBlockedHandler;

public class MFileDialogPeer extends MDialogPeer implements FileDialogPeer {
    private FilenameFilter filter;
    private String[] NativeFilteredFiles;
    native void create(MComponentPeer parent);
    void create(MComponentPeer parent, Object arg) {
        create(parent);
    }
    public MFileDialogPeer(FileDialog target) {
        super(target);
        FileDialog      fdialog = (FileDialog)target;
        String          dir = fdialog.getDirectory();
        String          file = fdialog.getFile();
        FilenameFilter  filter = fdialog.getFilenameFilter();

        insets = new Insets(0, 0, 0, 0);
        setDirectory(dir);
        if (file != null) {
            setFile(file);
        }
            setFilenameFilter(filter);
    }
    native void         pReshape(int x, int y, int width, int height);
    native void         pDispose();
    native void         pShow();
    native void         pHide();
    native void         setFileEntry(String dir, String file, String[] ffiles);
    native void insertReplaceFileDialogText(String l);
    public native void  setFont(Font f);

    String getFilteredFile(String file) {
        if (file == null) {
            file = ((FileDialog)target).getFile();
        }
        String dir = ((FileDialog)target).getDirectory();
        if (dir == null) {
            dir = "./";
        }
        if (file == null) {
            file = "";
        }
        if (filter != null && !filter.accept(new File(dir), file)) {
            file = "";
        }
        return file;
    }
    // NOTE: This method is called by privileged threads.
    //       DO NOT INVOKE CLIENT CODE ON THIS THREAD!
    public void         handleSelected(final String file) {
        final FileDialog fileDialog = (FileDialog)target;
        MToolkit.executeOnEventHandlerThread(fileDialog, new Runnable() {
            public void run() {
                int index = file.lastIndexOf(java.io.File.separatorChar);/*2509*//*ibm*/
                String dir;

                if (index == -1) {
                    dir = "."+java.io.File.separator;
                    fileDialog.setFile(file);
                } else {
                    dir = file.substring(0, index + 1);
                    fileDialog.setFile(file.substring(index + 1));
                }
                fileDialog.setDirectory(dir);
                fileDialog.hide();
            }
        });
    } // handleSelected()

    // NOTE: This method is called by privileged threads.
    //       DO NOT INVOKE CLIENT CODE ON THIS THREAD!
    public void         handleCancel() {
        final FileDialog fileDialog = (FileDialog)target;
        MToolkit.executeOnEventHandlerThread(fileDialog, new Runnable() {
            public void run() {
                fileDialog.setFile(null);
                fileDialog.hide();
            }
        });
    } // handleCancel()

    // NOTE: This method is called by privileged threads.
    //       DO NOT INVOKE CLIENT CODE ON THIS THREAD!
    public void         handleQuit() {
        final FileDialog fileDialog = (FileDialog)target;
        MToolkit.executeOnEventHandlerThread(fileDialog, new Runnable() {
            public void run() {
                fileDialog.hide();
            }
        });
    } // handleQuit()

    public  void setDirectory(String dir) {
        String file = ((FileDialog)target).getFile();
        setFileEntry((dir != null) ? dir : "./", (file != null) ? file
                     : "", null);
    }


    public  void setFile(String file) {
        String dir = ((FileDialog)target).getDirectory();
        if (dir == null) {
            dir = "./";
        }
        setFileEntry((dir != null) ? dir : "./", getFilteredFile(null), null);
    }
    class DirectoryFilter implements FilenameFilter {
        FilenameFilter userFilter;
        DirectoryFilter(FilenameFilter userFilter) {
            this.userFilter = userFilter;
        }
        public boolean accept(File parent, String name) {
            File toTest = new File(parent, name);
            if (toTest.isDirectory()) {
                return false;
            } else if (userFilter != null) {
                return userFilter.accept(parent, name);
            } else {
                return true;
            }
        }
    }
    public void doFilter(FilenameFilter filter, String dir) {
        String d = (dir == null) ? (((FileDialog)target).getDirectory()):(dir);
        String f = getFilteredFile(null);
        File df = new File((d != null) ? d : ".");
        String[] files = df.list(new DirectoryFilter(filter));
        String[] nffiles = NativeFilteredFiles;

        // At this point we have two file lists.
        // The first one is a filtered list of files that we retrieve
        // by using Java code and Java filter.
        // The second one is a filtered list of files that we retrieve
        // by using the native code and native pattern.
        // We should find an intersection of these two lists. The result
        // will be exactly what we expect to see in setFileEntry.
        // For more details please see 4784704.
        if ( files != null ) {
            ArrayList filearr = new ArrayList();
            if (nffiles != null) {
                for (int j = 0; j < files.length; j++) {
                    for (int n = 0; n < nffiles.length; n++) {
                        if (files[j].equals(nffiles[n])) {
                            filearr.add(files[j]);
                            break;
                        }
                    }
                }
            }
            files = new String[filearr.size()];
            for (int i = 0; i < files.length; i++) {
                files[i] = (String)filearr.get(i);
            }
        }
        if (files == null || files.length == 0) {
            files = new String[1];
            files[0] = "";
        }
        setFileEntry((d != null) ? d : ".", (f != null) ? f : "", files);
    }
    private boolean proceedFiltering(final String dir, String[] nffiles,
                                     boolean isPrivileged)
    {
        // Transfer the native filtered file list to the doFilter method.
        NativeFilteredFiles = nffiles;
        // If we are not on the Toolkit thread we can call doFilter() directly.
        // If the filter is null no user code will be invoked
        if (!isPrivileged || filter == null) {
            try {
                doFilter(filter, dir);
                return true;
            } catch(Exception e) {
                e.printStackTrace();
                return false;
            }
        }
        // Otherwise we have to call user code on EvenDispatchThread
        final ToolkitThreadBlockedHandler priveleged_lock =
            MToolkitThreadBlockedHandler.getToolkitThreadBlockedHandler();
        final boolean[] finished = new boolean[1];
        final boolean[] result = new boolean[1];
        finished[0] = false;
        result[0] = false;


        // Use the same Toolkit blocking mechanism as in DnD.
        priveleged_lock.lock();

        MToolkit.executeOnEventHandlerThread((FileDialog)target, new Runnable() {
            public void run() {
                priveleged_lock.lock();
                try {
                    doFilter(filter, dir);
                    result[0] = true;
                } catch (Exception e) {
                    e.printStackTrace();
                    result[0] = false;
                } finally {
                    finished[0] = true;
                    priveleged_lock.exit();
                    priveleged_lock.unlock();
                }
            }
        });

        while (!finished[0]) {
            priveleged_lock.enter();
        }

        priveleged_lock.unlock();

        return result[0];
    }

    public void setFilenameFilter(FilenameFilter filter) {
        this.filter = filter;
        FileDialog      fdialog = (FileDialog)target;
        String          dir = fdialog.getDirectory();
        String          file = fdialog.getFile();
        setFile(file);
        doFilter(filter, null);
    }

    // Called from native widget when paste key is pressed and we
    // already own the selection (prevents Motif from hanging while
    // waiting for the selection)
    //
    public void pasteFromClipboard() {
        Clipboard clipboard = target.getToolkit().getSystemClipboard();

        Transferable content = clipboard.getContents(this);
        if (content != null) {
            try {
                String data = (String)(content.getTransferData(DataFlavor.stringFlavor));
                insertReplaceFileDialogText(data);
            } catch (Exception e) {
            }
        }
    }

// CAVEAT:
// Peer coalescing code turned over the fact that the following functions
// were being inherited from Dialog and were not implemented in awt_FileDialog.c
// Five methods decribed by the peer interface are at fault (setResizable, setTitle,
// toFront, toBack and handleFocusTraversalEvent).  Additionally show has to be overridden
// as it was necessary to add a show function in MDialogPeer for modality flag passing.
// As a result we were winding up in  awt_Dialog.c (now coalesced into awt_TopLevel).
// As Filedialogs are modal and its unclear to me that any of these functions
// can be called while the FD is on-screen let it go.  RJM.
    public void show() {
        // must have our own show or we wind up in pShow for Window. Bad. Very bad.
        setVisible(true);
        setFilenameFilter(filter);
    }

    /**
     * MFileDialogPeer doesn't have native pData so we don't do restack on it
     * @see java.awt.peer.ContainerPeer#restack
     */
    public void restack() {
    }

    /**
     * @see java.awt.peer.ContainerPeer#isRestackSupported
     */
    public boolean isRestackSupported() {
        return false;
    }
}
