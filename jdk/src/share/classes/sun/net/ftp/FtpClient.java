/*
 * Copyright 1994-2004 Sun Microsystems, Inc.  All Rights Reserved.
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

package sun.net.ftp;

import java.util.StringTokenizer;
import java.util.regex.*;
import java.io.*;
import java.net.*;
import sun.net.TransferProtocolClient;
import sun.net.TelnetInputStream;
import sun.net.TelnetOutputStream;
import sun.misc.RegexpPool;
import java.security.AccessController;
import java.security.PrivilegedAction;

/**
 * This class implements the FTP client.
 *
 * @author      Jonathan Payne
 */

public class FtpClient extends TransferProtocolClient {
    public static final int FTP_PORT = 21;

    static int  FTP_SUCCESS = 1;
    static int  FTP_TRY_AGAIN = 2;
    static int  FTP_ERROR = 3;

    /** remember the ftp server name because we may need it */
    private String      serverName = null;

    /** socket for data transfer */
    private boolean     replyPending = false;
    private boolean     binaryMode = false;
    private boolean     loggedIn = false;

    /** regexp pool of hosts for which we should connect directly, not Proxy
     *  these are intialized from a property.
     */
    private static RegexpPool nonProxyHostsPool = null;

    /** The string soucre of nonProxyHostsPool
     */
    private static String nonProxyHostsSource = null;

    /** last command issued */
    String              command;

    /** The last reply code from the ftp daemon. */
    int                 lastReplyCode;

    /** Welcome message from the server, if any. */
    public String       welcomeMsg;


    /* these methods are used to determine whether ftp urls are sent to */
    /* an http server instead of using a direct connection to the */
    /* host. They aren't used directly here. */
    /**
     * @return if the networking layer should send ftp connections through
     *          a proxy
     */
    public static boolean getUseFtpProxy() {
        // if the ftp.proxyHost is set, use it!
        return (getFtpProxyHost() != null);
    }

    /**
     * @return the host to use, or null if none has been specified
     */
    public static String getFtpProxyHost() {
        return java.security.AccessController.doPrivileged(
            new java.security.PrivilegedAction<String>() {
            public String run() {
                String result = System.getProperty("ftp.proxyHost");
                if (result == null) {
                    result = System.getProperty("ftpProxyHost");
                }
                if (result == null) {
                    // as a last resort we use the general one if ftp.useProxy
                    // is true
                    if (Boolean.getBoolean("ftp.useProxy")) {
                    result = System.getProperty("proxyHost");
                    }
                }
                return result;
            }
        });
    }

    /**
     * @return the proxy port to use.  Will default reasonably if not set.
     */
    public static int getFtpProxyPort() {
        final int result[] = {80};
        java.security.AccessController.doPrivileged(
          new java.security.PrivilegedAction() {
            public Object run() {

                String tmp = System.getProperty("ftp.proxyPort");
                if (tmp == null) {
                    // for compatibility with 1.0.2
                    tmp = System.getProperty("ftpProxyPort");
                }
                if (tmp == null) {
                    // as a last resort we use the general one if ftp.useProxy
                    // is true
                    if (Boolean.getBoolean("ftp.useProxy")) {
                        tmp = System.getProperty("proxyPort");
                    }
                }
                if (tmp != null) {
                    result[0] = Integer.parseInt(tmp);
                }
                return null;
            }
        });
        return result[0];
    }

    public static boolean matchNonProxyHosts(String host) {
        synchronized (FtpClient.class) {
            String rawList = java.security.AccessController.doPrivileged(
                    new sun.security.action.GetPropertyAction("ftp.nonProxyHosts"));
            if (rawList == null) {
                nonProxyHostsPool = null;
            } else {
                if (!rawList.equals(nonProxyHostsSource)) {
                    RegexpPool pool = new RegexpPool();
                    StringTokenizer st = new StringTokenizer(rawList, "|", false);
                    try {
                        while (st.hasMoreTokens()) {
                            pool.add(st.nextToken().toLowerCase(), Boolean.TRUE);
                        }
                    } catch (sun.misc.REException ex) {
                        System.err.println("Error in http.nonProxyHosts system property: " + ex);
                    }
                    nonProxyHostsPool = pool;
                }
            }
            nonProxyHostsSource = rawList;
        }

        if (nonProxyHostsPool == null) {
            return false;
        }

        if (nonProxyHostsPool.match(host) != null) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * issue the QUIT command to the FTP server and close the connection.
     *
     * @exception       FtpProtocolException if an error occured
     */
    public void closeServer() throws IOException {
        if (serverIsOpen()) {
            issueCommand("QUIT");
            super.closeServer();
        }
    }

    /**
     * Send a command to the FTP server.
     *
     * @param   cmd     String containing the command
     * @return          reply code
     *
     * @exception       FtpProtocolException if an error occured
     */
    protected int issueCommand(String cmd) throws IOException {
        command = cmd;

        int reply;

        while (replyPending) {
            replyPending = false;
            if (readReply() == FTP_ERROR)
                throw new FtpProtocolException("Error reading FTP pending reply\n");
        }
        do {
            sendServer(cmd + "\r\n");
            reply = readReply();
        } while (reply == FTP_TRY_AGAIN);
        return reply;
    }

    /**
     * Send a command to the FTP server and check for success.
     *
     * @param   cmd     String containing the command
     *
     * @exception       FtpProtocolException if an error occured
     */
    protected void issueCommandCheck(String cmd) throws IOException {
        if (issueCommand(cmd) != FTP_SUCCESS)
            throw new FtpProtocolException(cmd + ":" + getResponseString());
    }

    /**
     * Read the reply from the FTP server.
     *
     * @return          FTP_SUCCESS or FTP_ERROR depending on success
     * @exception       FtpProtocolException if an error occured
     */
    protected int readReply() throws IOException {
        lastReplyCode = readServerResponse();

        switch (lastReplyCode / 100) {
        case 1:
            replyPending = true;
            /* falls into ... */

        case 2:
        case 3:
            return FTP_SUCCESS;

        case 5:
            if (lastReplyCode == 530) {
                if (!loggedIn) {
                    throw new FtpLoginException("Not logged in");
                }
                return FTP_ERROR;
            }
            if (lastReplyCode == 550) {
                throw new FileNotFoundException(command + ": " + getResponseString());
            }
        }

        /* this statement is not reached */
        return FTP_ERROR;
    }

    /**
     * Tries to open a Data Connection in "PASSIVE" mode by issuing a EPSV or
     * PASV command then opening a Socket to the specified address & port
     *
     * @return          the opened socket
     * @exception       FtpProtocolException if an error occurs when issuing the
     *                  PASV command to the ftp server.
     */
    protected Socket openPassiveDataConnection() throws IOException {
        String serverAnswer;
        int port;
        InetSocketAddress dest = null;

        /**
         * Here is the idea:
         *
         * - First we want to try the new (and IPv6 compatible) EPSV command
         *   But since we want to be nice with NAT software, we'll issue the
         *   EPSV ALL cmd first.
         *   EPSV is documented in RFC2428
         * - If EPSV fails, then we fall back to the older, yet OK PASV command
         * - If PASV fails as well, then we throw an exception and the calling method
         *   will have to try the EPRT or PORT command
         */
        if (issueCommand("EPSV ALL") == FTP_SUCCESS) {
            // We can safely use EPSV commands
            if (issueCommand("EPSV") == FTP_ERROR)
                throw new FtpProtocolException("EPSV Failed: " + getResponseString());
            serverAnswer = getResponseString();

            // The response string from a EPSV command will contain the port number
            // the format will be :
            //  229 Entering Extended Passive Mode (|||58210|)
            //
            // So we'll use the regular expresions package to parse the output.

            Pattern p = Pattern.compile("^229 .* \\(\\|\\|\\|(\\d+)\\|\\)");
            Matcher m = p.matcher(serverAnswer);
            if (! m.find())
                throw new FtpProtocolException("EPSV failed : " + serverAnswer);
            // Yay! Let's extract the port number
            String s = m.group(1);
            port = Integer.parseInt(s);
            InetAddress add = serverSocket.getInetAddress();
            if (add != null) {
                dest = new InetSocketAddress(add, port);
            } else {
                // This means we used an Unresolved address to connect in
                // the first place. Most likely because the proxy is doing
                // the name resolution for us, so let's keep using unresolved
                // address.
                dest = InetSocketAddress.createUnresolved(serverName, port);
            }
        } else {
            // EPSV ALL failed, so Let's try the regular PASV cmd
            if (issueCommand("PASV") == FTP_ERROR)
                throw new FtpProtocolException("PASV failed: " + getResponseString());
            serverAnswer = getResponseString();

            // Let's parse the response String to get the IP & port to connect to
            // the String should be in the following format :
            //
            // 227 Entering Passive Mode (A1,A2,A3,A4,p1,p2)
            //
            // Note that the two parenthesis are optional
            //
            // The IP address is A1.A2.A3.A4 and the port is p1 * 256 + p2
            //
            // The regular expression is a bit more complex this time, because the
            // parenthesis are optionals and we have to use 3 groups.

            Pattern p = Pattern.compile("227 .* \\(?(\\d{1,3},\\d{1,3},\\d{1,3},\\d{1,3}),(\\d{1,3}),(\\d{1,3})\\)?");
            Matcher m = p.matcher(serverAnswer);
            if (! m.find())
                throw new FtpProtocolException("PASV failed : " + serverAnswer);
            // Get port number out of group 2 & 3
            port = Integer.parseInt(m.group(3)) + (Integer.parseInt(m.group(2)) << 8);
            // IP address is simple
            String s = m.group(1).replace(',','.');
            dest = new InetSocketAddress(s, port);
        }
        // Got everything, let's open the socket!
        Socket s;
        if (proxy != null) {
            if (proxy.type() == Proxy.Type.SOCKS) {
                s = (Socket) AccessController.doPrivileged(
                                            new PrivilegedAction() {
                              public Object run() {
                                  return new Socket(proxy);
                              }});
            } else
                s = new Socket(Proxy.NO_PROXY);
        } else
            s = new Socket();
        if (connectTimeout >= 0) {
            s.connect(dest, connectTimeout);
        } else {
            if (defaultConnectTimeout > 0) {
                s.connect(dest, defaultConnectTimeout);
            } else {
                s.connect(dest);
            }
        }
        if (readTimeout >= 0)
            s.setSoTimeout(readTimeout);
        else
            if (defaultSoTimeout > 0) {
                s.setSoTimeout(defaultSoTimeout);
        }
        return s;
    }

    /**
     * Tries to open a Data Connection with the server. It will first try a passive
     * mode connection, then, if it fails, a more traditional PORT command
     *
     * @param   cmd     the command to execute (RETR, STOR, etc...)
     * @return          the opened socket
     *
     * @exception       FtpProtocolException if an error occurs when issuing the
     *                  PORT command to the ftp server.
     */
    protected Socket openDataConnection(String cmd) throws IOException {
        ServerSocket portSocket;
        Socket  clientSocket = null;
        String      portCmd;
        InetAddress myAddress;
        IOException e;

        // Let's try passive mode first
        try {
            clientSocket = openPassiveDataConnection();
        } catch (IOException ex) {
            clientSocket = null;
        }
        if (clientSocket != null) {
            // We did get a clientSocket, so the passive mode worked
            // Let's issue the command (GET, DIR, ...)
            try {
                if (issueCommand(cmd) == FTP_ERROR) {
                    clientSocket.close();
                    throw new FtpProtocolException(getResponseString());
                } else
                    return clientSocket;
            } catch (IOException ioe) {
                clientSocket.close();
                throw ioe;
            }
        }

        assert(clientSocket == null);

        // Passive mode failed, let's fall back to the good old "PORT"

        if (proxy != null && proxy.type() == Proxy.Type.SOCKS) {
            // We're behind a firewall and the passive mode fail,
            // since we can't accept a connection through SOCKS (yet)
            // throw an exception
            throw new FtpProtocolException("Passive mode failed");
        } else
            portSocket = new ServerSocket(0, 1);
        try {
            myAddress = portSocket.getInetAddress();
            if (myAddress.isAnyLocalAddress())
                myAddress = getLocalAddress();
            // Let's try the new, IPv6 compatible EPRT command
            // See RFC2428 for specifics
            // Some FTP servers (like the one on Solaris) are bugged, they
            // will accept the EPRT command but then, the subsequent command
            // (e.g. RETR) will fail, so we have to check BOTH results (the
            // EPRT cmd then the actual command) to decide wether we should
            // fall back on the older PORT command.
            portCmd = "EPRT |" +
                ((myAddress instanceof Inet6Address) ? "2" : "1") + "|" +
                myAddress.getHostAddress() +"|" +
                portSocket.getLocalPort()+"|";
            if (issueCommand(portCmd) == FTP_ERROR ||
                issueCommand(cmd) == FTP_ERROR) {
                // The EPRT command failed, let's fall back to good old PORT
                portCmd = "PORT ";
                byte[] addr = myAddress.getAddress();

                /* append host addr */
                for (int i = 0; i < addr.length; i++) {
                    portCmd = portCmd + (addr[i] & 0xFF) + ",";
                }

                /* append port number */
                portCmd = portCmd + ((portSocket.getLocalPort() >>> 8) & 0xff) + ","
                    + (portSocket.getLocalPort() & 0xff);
                if (issueCommand(portCmd) == FTP_ERROR) {
                    e = new FtpProtocolException("PORT :" + getResponseString());
                    throw e;
                }
                if (issueCommand(cmd) == FTP_ERROR) {
                    e = new FtpProtocolException(cmd + ":" + getResponseString());
                    throw e;
                }
            }
            // Either the EPRT or the PORT command was successful
            // Let's create the client socket
            if (connectTimeout >= 0) {
                portSocket.setSoTimeout(connectTimeout);
            } else {
                if (defaultConnectTimeout > 0)
                    portSocket.setSoTimeout(defaultConnectTimeout);
            }
            clientSocket = portSocket.accept();
            if (readTimeout >= 0)
                clientSocket.setSoTimeout(readTimeout);
            else {
                if (defaultSoTimeout > 0)
                    clientSocket.setSoTimeout(defaultSoTimeout);
            }
        } finally {
            portSocket.close();
        }

        return clientSocket;
    }

    /* public methods */

    /**
     * Open a FTP connection to host <i>host</i>.
     *
     * @param   host    The hostname of the ftp server
     *
     * @exception       FtpProtocolException if connection fails
     */
    public void openServer(String host) throws IOException {
        openServer(host, FTP_PORT);
    }

    /**
     * Open a FTP connection to host <i>host</i> on port <i>port</i>.
     *
     * @param   host    the hostname of the ftp server
     * @param   port    the port to connect to (usually 21)
     *
     * @exception       FtpProtocolException if connection fails
     */
    public void openServer(String host, int port) throws IOException {
        this.serverName = host;
        super.openServer(host, port);
        if (readReply() == FTP_ERROR)
            throw new FtpProtocolException("Welcome message: " +
                                           getResponseString());
    }


    /**
     * login user to a host with username <i>user</i> and password
     * <i>password</i>
     *
     * @param   user            Username to use at login
     * @param   password        Password to use at login or null of none is needed
     *
     * @exception       FtpLoginException if login is unsuccesful
     */
    public void login(String user, String password) throws IOException {
        if (!serverIsOpen())
            throw new FtpLoginException("not connected to host");
        if (user == null || user.length() == 0)
            return;
        if (issueCommand("USER " + user) == FTP_ERROR)
            throw new FtpLoginException("user " + user + " : " + getResponseString());
        /*
         * Checks for "331 User name okay, need password." answer
         */

        if (lastReplyCode == 331)
            if ((password == null) || (password.length() == 0) ||
                (issueCommand("PASS " + password) == FTP_ERROR))
                throw new FtpLoginException("password: " + getResponseString());

        // keep the welcome message around so we can
        // put it in the resulting HTML page.
        String l;
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < serverResponse.size(); i++) {
            l = (String)serverResponse.elementAt(i);
            if (l != null) {
                if (l.length() >= 4 && l.startsWith("230")) {
                    // get rid of the "230-" prefix
                    l = l.substring(4);
                }
                sb.append(l);
            }
        }
        welcomeMsg = sb.toString();
        loggedIn = true;
    }

    /**
     * GET a file from the FTP server
     *
     * @param   filename        name of the file to retrieve
     * @return  the <code>InputStream</code> to read the file from
     *
     * @exception       FileNotFoundException if the file can't be opened
     */
    public TelnetInputStream get(String filename) throws IOException {
        Socket  s;

        try {
            s = openDataConnection("RETR " + filename);
        } catch (FileNotFoundException fileException) {
            /* Well, "/" might not be the file delimitor for this
               particular ftp server, so let's try a series of
               "cd" commands to get to the right place. */
            /* But don't try this if there are no '/' in the path */
            if (filename.indexOf('/') == -1)
                throw fileException;

            StringTokenizer t = new StringTokenizer(filename, "/");
            String          pathElement = null;

            while (t.hasMoreElements()) {
                pathElement = t.nextToken();

                if (!t.hasMoreElements()) {
                    /* This is the file component.  Look it up now. */
                    break;
                }
                try {
                    cd(pathElement);
                } catch (FtpProtocolException e) {
                    /* Giving up. */
                    throw fileException;
                }
            }
            if (pathElement != null) {
                s = openDataConnection("RETR " + pathElement);
            } else {
                throw fileException;
            }
        }

        return new TelnetInputStream(s.getInputStream(), binaryMode);
    }

    /**
     * PUT a file to the FTP server
     *
     * @param   filename        name of the file to store
     * @return  the <code>OutputStream</code> to write the file to
     *
     */
    public TelnetOutputStream put(String filename) throws IOException {
        Socket s = openDataConnection("STOR " + filename);
        TelnetOutputStream out = new TelnetOutputStream(s.getOutputStream(), binaryMode);
        if (!binaryMode)
            out.setStickyCRLF(true);
        return out;
    }

    /**
     * Append to a file on the FTP server
     *
     * @param   filename        name of the file to append to
     * @return  the <code>OutputStream</code> to write the file to
     *
     */
    public TelnetOutputStream append(String filename) throws IOException {
        Socket s = openDataConnection("APPE " + filename);
        TelnetOutputStream out = new TelnetOutputStream(s.getOutputStream(), binaryMode);
        if (!binaryMode)
            out.setStickyCRLF(true);

        return out;
    }

    /**
     * LIST files in the current directory on a remote FTP server
     *
     * @return  the <code>InputStream</code> to read the list from
     *
     */
    public TelnetInputStream list() throws IOException {
        Socket s = openDataConnection("LIST");

        return new TelnetInputStream(s.getInputStream(), binaryMode);
    }

    /**
     * List (NLST) file names on a remote FTP server
     *
     * @param   path    pathname to the directory to list, null for current
     *                  directory
     * @return  the <code>InputStream</code> to read the list from
     * @exception       <code>FtpProtocolException</code>
     */
    public TelnetInputStream nameList(String path) throws IOException {
        Socket s;

        if (path != null)
            s = openDataConnection("NLST " + path);
        else
            s = openDataConnection("NLST");
        return new TelnetInputStream(s.getInputStream(), binaryMode);
    }

    /**
     * CD to a specific directory on a remote FTP server
     *
     * @param   remoteDirectory path of the directory to CD to
     *
     * @exception       <code>FtpProtocolException</code>
     */
    public void cd(String remoteDirectory) throws IOException {
        if (remoteDirectory == null ||
            "".equals(remoteDirectory))
            return;
        issueCommandCheck("CWD " + remoteDirectory);
    }

    /**
     * CD to the parent directory on a remote FTP server
     *
     */
    public void cdUp() throws IOException {
        issueCommandCheck("CDUP");
    }

    /**
     * Print working directory of remote FTP server
     *
     * @exception FtpProtocolException if the command fails
     */
    public String pwd() throws IOException {
        String answ;

        issueCommandCheck("PWD");
        /*
         * answer will be of the following format :
         *
         * 257 "/" is current directory.
         */
        answ = getResponseString();
        if (!answ.startsWith("257"))
            throw new FtpProtocolException("PWD failed. " + answ);
        return answ.substring(5, answ.lastIndexOf('"'));
    }

    /**
     * Set transfer type to 'I'
     *
     * @exception FtpProtocolException if the command fails
     */
    public void binary() throws IOException {
        issueCommandCheck("TYPE I");
        binaryMode = true;
    }

    /**
     * Set transfer type to 'A'
     *
     * @exception FtpProtocolException if the command fails
     */
    public void ascii() throws IOException {
        issueCommandCheck("TYPE A");
        binaryMode = false;
    }

    /**
     * Rename a file on the ftp server
     *
     * @exception FtpProtocolException if the command fails
     */
    public void rename(String from, String to) throws IOException {
        issueCommandCheck("RNFR " + from);
        issueCommandCheck("RNTO " + to);
    }

    /**
     * Get the "System string" from the FTP server
     *
     * @exception       FtpProtocolException if it fails
     */
    public String system() throws IOException {
        String answ;
        issueCommandCheck("SYST");
        answ = getResponseString();
        if (!answ.startsWith("215"))
            throw new FtpProtocolException("SYST failed." + answ);
        return answ.substring(4); // Skip "215 "
    }

    /**
     * Send a No-operation command. It's usefull for testing the connection status
     *
     * @exception FtpProtocolException if the command fails
     */
    public void noop() throws IOException {
        issueCommandCheck("NOOP");
    }

    /**
     * Reinitialize the USER parameters on the FTp server
     *
     * @exception FtpProtocolException if the command fails
     */
    public void reInit() throws IOException {
        issueCommandCheck("REIN");
        loggedIn = false;
    }

    /**
     * New FTP client connected to host <i>host</i>.
     *
     * @param   host    Hostname of the FTP server
     *
     * @exception FtpProtocolException if the connection fails
     */
    public FtpClient(String host) throws IOException {
        super();
        openServer(host, FTP_PORT);
    }

    /**
     * New FTP client connected to host <i>host</i>, port <i>port</i>.
     *
     * @param   host    Hostname of the FTP server
     * @param   port    port number to connect to (usually 21)
     *
     * @exception FtpProtocolException if the connection fails
     */
    public FtpClient(String host, int port) throws IOException {
        super();
        openServer(host, port);
    }

    /** Create an uninitialized FTP client. */
    public FtpClient() {}

    public FtpClient(Proxy p) {
        proxy = p;
    }

    protected void finalize() throws IOException {
        /**
         * Do not call the "normal" closeServer() as we want finalization
         * to be as efficient as possible
         */
        if (serverIsOpen())
            super.closeServer();
    }

}
