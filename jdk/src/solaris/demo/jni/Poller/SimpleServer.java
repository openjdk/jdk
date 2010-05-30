/*
 * Copyright (c) 1999, Oracle and/or its affiliates. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *   - Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *
 *   - Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer in the
 *     documentation and/or other materials provided with the distribution.
 *
 *   - Neither the name of Oracle nor the names of its
 *     contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

import java.io.*;
import java.net.*;
import java.lang.Byte;

/**
 * Simple Java "server" using a single thread to handle each connection.
 */

public class SimpleServer
{
  private final static int BYTESPEROP= PollingServer.BYTESPEROP;
  private final static int PORTNUM   = PollingServer.PORTNUM;
  private final static int MAXCONN   = PollingServer.MAXCONN;

  /*
   * This synchronization object protects access to certain
   * data (bytesRead,eventsToProcess) by concurrent Consumer threads.
   */
  private final static Object eventSync = new Object();

  private static InputStream[] instr = new InputStream[MAXCONN];
  private static int bytesRead;
  private static int bytesToRead;

  public SimpleServer() {
    Socket[] sockArr = new Socket[MAXCONN];
    long timestart, timestop;
    int bytes;
    int totalConn=0;


    System.out.println ("Serv: Initializing port " + PORTNUM);
    try {

      ServerSocket skMain = new ServerSocket (PORTNUM);

      bytesRead = 0;
      Socket ctrlSock = skMain.accept();

      BufferedReader ctrlReader =
        new BufferedReader(new InputStreamReader(ctrlSock.getInputStream()));
      String ctrlString = ctrlReader.readLine();
      bytesToRead = Integer.valueOf(ctrlString).intValue();
      ctrlString = ctrlReader.readLine();
      totalConn = Integer.valueOf(ctrlString).intValue();

      System.out.println("Receiving " + bytesToRead + " bytes from " +
                         totalConn + " client connections");

      timestart = System.currentTimeMillis();

      /*
       * Take connections, spawn off connection handling threads
       */
      ConnHandler[] connHA = new ConnHandler[MAXCONN];
      int conn = 0;
      while ( conn < totalConn ) {
          Socket sock = skMain.accept();
          connHA[conn] = new ConnHandler(sock.getInputStream());
          connHA[conn].start();
          conn++;
      }

      while ( bytesRead < bytesToRead ) {
          java.lang.Thread.sleep(500);
      }
      timestop = System.currentTimeMillis();
      System.out.println("Time for all reads (" + totalConn +
                         " sockets) : " + (timestop-timestart));
      // Tell the client it can now go away
      byte[] buff = new byte[BYTESPEROP];
      ctrlSock.getOutputStream().write(buff,0,BYTESPEROP);
    } catch (Exception exc) { exc.printStackTrace(); }
  }

  /*
   * main ... just create invoke the SimpleServer constructor.
   */
  public static void main (String args[])
  {
    SimpleServer server = new SimpleServer();
  }

  /*
   * Connection Handler inner class...one of these per client connection.
   */
  class ConnHandler extends Thread {
    private InputStream instr;
    public ConnHandler(InputStream inputStr) { instr = inputStr; }

    public void run() {
      try {
        int bytes;
        byte[] buff = new byte[BYTESPEROP];

        while ( bytesRead < bytesToRead ) {
          bytes = instr.read (buff, 0, BYTESPEROP);
          if (bytes > 0 ) {
            synchronized(eventSync) {
              bytesRead += bytes;
            }
            /*
             * Any real server would do some synchronized and some
             * unsynchronized work on behalf of the client, and
             * most likely send some data back...but this is a
             * gross oversimplification.
             */
          }
          else {
            if (bytesRead < bytesToRead)
              System.out.println("instr.read returned : " + bytes);
          }
        }
      }
      catch (Exception e) {e.printStackTrace();}
    }
  }
}
