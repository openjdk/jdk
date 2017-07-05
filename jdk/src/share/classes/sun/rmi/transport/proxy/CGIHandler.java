/*
 * Copyright (c) 1996, 2008, Oracle and/or its affiliates. All rights reserved.
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
package sun.rmi.transport.proxy;

import java.io.*;
import java.net.*;
import java.util.Hashtable;

/**
 * CGIClientException is thrown when an error is detected
 * in a client's request.
 */
class CGIClientException extends Exception {

    public CGIClientException(String s) {
        super(s);
    }
}

/**
 * CGIServerException is thrown when an error occurs here on the server.
 */
class CGIServerException extends Exception {

    public CGIServerException(String s) {
        super(s);
    }
}

/**
 * CGICommandHandler is the interface to an object that handles a
 * particular supported command.
 */
interface CGICommandHandler {

    /**
     * Return the string form of the command
     * to be recognized in the query string.
     */
    public String getName();

    /**
     * Execute the command with the given string as parameter.
     */
    public void execute(String param) throws CGIClientException, CGIServerException;
}

/**
 * The CGIHandler class contains methods for executing as a CGI program.
 * The main function interprets the query string as a command of the form
 * "<command>=<parameters>".
 *
 * This class depends on the CGI 1.0 environment variables being set as
 * properties of the same name in this Java VM.
 *
 * All data and methods of this class are static because they are specific
 * to this particular CGI process.
 */
public final class CGIHandler {

    /* get CGI parameters that we need */
    static int ContentLength;
    static String QueryString;
    static String RequestMethod;
    static String ServerName;
    static int ServerPort;

    static {
        java.security.AccessController.doPrivileged(
            new java.security.PrivilegedAction<Void>() {
            public Void run() {
                ContentLength =
                    Integer.getInteger("CONTENT_LENGTH", 0).intValue();
                QueryString = System.getProperty("QUERY_STRING", "");
                RequestMethod = System.getProperty("REQUEST_METHOD", "");
                ServerName = System.getProperty("SERVER_NAME", "");
                ServerPort = Integer.getInteger("SERVER_PORT", 0).intValue();
                return null;
            }
        });
    }

    /* list of handlers for supported commands */
    private static CGICommandHandler commands[] = {
        new CGIForwardCommand(),
        new CGIGethostnameCommand(),
        new CGIPingCommand(),
        new CGITryHostnameCommand()
    };

    /* construct table mapping command strings to handlers */
    private static Hashtable commandLookup;
    static {
        commandLookup = new Hashtable();
        for (int i = 0; i < commands.length; ++ i)
            commandLookup.put(commands[i].getName(), commands[i]);
    }

    /* prevent instantiation of this class */
    private CGIHandler() {}

    /**
     * Execute command given in query string on URL.  The string before
     * the first '=' is interpreted as the command name, and the string
     * after the first '=' is the parameters to the command.
     */
    public static void main(String args[])
    {
        try {
            String command, param;
            int delim = QueryString.indexOf("=");
            if (delim == -1) {
                command = QueryString;
                param = "";
            }
            else {
                command = QueryString.substring(0, delim);
                param = QueryString.substring(delim + 1);
            }
            CGICommandHandler handler =
                (CGICommandHandler) commandLookup.get(command);
            if (handler != null)
                try {
                    handler.execute(param);
                } catch (CGIClientException e) {
                    returnClientError(e.getMessage());
                } catch (CGIServerException e) {
                    returnServerError(e.getMessage());
                }
            else
                returnClientError("invalid command: " + command);
        } catch (Exception e) {
            returnServerError("internal error: " + e.getMessage());
        }
        System.exit(0);
    }

    /**
     * Return an HTML error message indicating there was error in
     * the client's request.
     */
    private static void returnClientError(String message)
    {
        System.out.println("Status: 400 Bad Request: " + message);
        System.out.println("Content-type: text/html");
        System.out.println("");
        System.out.println("<HTML>" +
                           "<HEAD><TITLE>Java RMI Client Error" +
                           "</TITLE></HEAD>" +
                           "<BODY>");
        System.out.println("<H1>Java RMI Client Error</H1>");
        System.out.println("");
        System.out.println(message);
        System.out.println("</BODY></HTML>");
        System.exit(1);
    }

    /**
     * Return an HTML error message indicating an error occurred
     * here on the server.
     */
    private static void returnServerError(String message)
    {
        System.out.println("Status: 500 Server Error: " + message);
        System.out.println("Content-type: text/html");
        System.out.println("");
        System.out.println("<HTML>" +
                           "<HEAD><TITLE>Java RMI Server Error" +
                           "</TITLE></HEAD>" +
                           "<BODY>");
        System.out.println("<H1>Java RMI Server Error</H1>");
        System.out.println("");
        System.out.println(message);
        System.out.println("</BODY></HTML>");
        System.exit(1);
    }
}

/**
 * "forward" command: Forward request body to local port on the server,
 * and send reponse back to client.
 */
final class CGIForwardCommand implements CGICommandHandler {

    public String getName() {
        return "forward";
    }

    public void execute(String param) throws CGIClientException, CGIServerException
    {
        if (!CGIHandler.RequestMethod.equals("POST"))
            throw new CGIClientException("can only forward POST requests");

        int port;
        try {
            port = Integer.parseInt(param);
        } catch (NumberFormatException e) {
            throw new CGIClientException("invalid port number: " + param);
        }
        if (port <= 0 || port > 0xFFFF)
            throw new CGIClientException("invalid port: " + port);
        if (port < 1024)
            throw new CGIClientException("permission denied for port: " +
                                         port);

        byte buffer[];
        Socket socket;
        try {
            socket = new Socket(InetAddress.getLocalHost(), port);
        } catch (IOException e) {
            throw new CGIServerException("could not connect to local port");
        }

        /*
         * read client's request body
         */
        DataInputStream clientIn = new DataInputStream(System.in);
        buffer = new byte[CGIHandler.ContentLength];
        try {
            clientIn.readFully(buffer);
        } catch (EOFException e) {
            throw new CGIClientException("unexpected EOF reading request body");
        } catch (IOException e) {
            throw new CGIClientException("error reading request body");
        }

        /*
         * send to local server in HTTP
         */
        try {
            DataOutputStream socketOut =
                new DataOutputStream(socket.getOutputStream());
            socketOut.writeBytes("POST / HTTP/1.0\r\n");
            socketOut.writeBytes("Content-length: " +
                                 CGIHandler.ContentLength + "\r\n\r\n");
            socketOut.write(buffer);
            socketOut.flush();
        } catch (IOException e) {
            throw new CGIServerException("error writing to server");
        }

        /*
         * read response
         */
        DataInputStream socketIn;
        try {
            socketIn = new DataInputStream(socket.getInputStream());
        } catch (IOException e) {
            throw new CGIServerException("error reading from server");
        }
        String key = "Content-length:".toLowerCase();
        boolean contentLengthFound = false;
        String line;
        int responseContentLength = -1;
        do {
            try {
                line = socketIn.readLine();
            } catch (IOException e) {
                throw new CGIServerException("error reading from server");
            }
            if (line == null)
                throw new CGIServerException(
                    "unexpected EOF reading server response");

            if (line.toLowerCase().startsWith(key)) {
                if (contentLengthFound)
                    ; // what would we want to do in this case??
                responseContentLength =
                    Integer.parseInt(line.substring(key.length()).trim());
                contentLengthFound = true;
            }
        } while ((line.length() != 0) &&
                 (line.charAt(0) != '\r') && (line.charAt(0) != '\n'));

        if (!contentLengthFound || responseContentLength < 0)
            throw new CGIServerException(
                "missing or invalid content length in server response");
        buffer = new byte[responseContentLength];
        try {
            socketIn.readFully(buffer);
        } catch (EOFException e) {
            throw new CGIServerException(
                "unexpected EOF reading server response");
        } catch (IOException e) {
            throw new CGIServerException("error reading from server");
        }

        /*
         * send response back to client
         */
        System.out.println("Status: 200 OK");
        System.out.println("Content-type: application/octet-stream");
        System.out.println("");
        try {
            System.out.write(buffer);
        } catch (IOException e) {
            throw new CGIServerException("error writing response");
        }
        System.out.flush();
    }
}

/**
 * "gethostname" command: Return the host name of the server as the
 * response body
 */
final class CGIGethostnameCommand implements CGICommandHandler {

    public String getName() {
        return "gethostname";
    }

    public void execute(String param)
    {
        System.out.println("Status: 200 OK");
        System.out.println("Content-type: application/octet-stream");
        System.out.println("Content-length: " +
                           CGIHandler.ServerName.length());
        System.out.println("");
        System.out.print(CGIHandler.ServerName);
        System.out.flush();
    }
}

/**
 * "ping" command: Return an OK status to indicate that connection
 * was successful.
 */
final class CGIPingCommand implements CGICommandHandler {

    public String getName() {
        return "ping";
    }

    public void execute(String param)
    {
        System.out.println("Status: 200 OK");
        System.out.println("Content-type: application/octet-stream");
        System.out.println("Content-length: 0");
        System.out.println("");
    }
}

/**
 * "tryhostname" command: Return a human readable message describing
 * what host name is available to local Java VMs.
 */
final class CGITryHostnameCommand implements CGICommandHandler {

    public String getName() {
        return "tryhostname";
    }

    public void execute(String param)
    {
        System.out.println("Status: 200 OK");
        System.out.println("Content-type: text/html");
        System.out.println("");
        System.out.println("<HTML>" +
                           "<HEAD><TITLE>Java RMI Server Hostname Info" +
                           "</TITLE></HEAD>" +
                           "<BODY>");
        System.out.println("<H1>Java RMI Server Hostname Info</H1>");
        System.out.println("<H2>Local host name available to Java VM:</H2>");
        System.out.print("<P>InetAddress.getLocalHost().getHostName()");
        try {
            String localHostName = InetAddress.getLocalHost().getHostName();

            System.out.println(" = " + localHostName);
        } catch (UnknownHostException e) {
            System.out.println(" threw java.net.UnknownHostException");
        }

        System.out.println("<H2>Server host information obtained through CGI interface from HTTP server:</H2>");
        System.out.println("<P>SERVER_NAME = " + CGIHandler.ServerName);
        System.out.println("<P>SERVER_PORT = " + CGIHandler.ServerPort);
        System.out.println("</BODY></HTML>");
    }
}
