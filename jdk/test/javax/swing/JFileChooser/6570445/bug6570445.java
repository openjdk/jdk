/*
 * @test
 * @bug 6570445
 * @summary Checks if Win32ShellFolder2's COM-using methods work under a security manager
 * @author Leonid Popov
 */

import javax.swing.filechooser.FileSystemView;

public class bug6570445 {
    public static void main(String[] args) {
        System.setSecurityManager(new SecurityManager());

        // The next line of code forces FileSystemView to request data from Win32ShellFolder2,
        // what causes an exception if a security manager installed (see the bug 6570445 description)
        FileSystemView.getFileSystemView().getRoots();

        System.out.println("Passed.");
    }
}
