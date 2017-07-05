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

/*
 * This source code is provided to illustrate the usage of a given feature
 * or technique and has been deliberately simplified. Additional steps
 * required for a production-quality application, such as security checks,
 * input validation and proper error handling, might not be present in
 * this sample code.
 */


import  java.util.*;
import  java.net.*;
import  java.io.*;

public class Client
{
  private final static int BYTESPEROP= PollingServer.BYTESPEROP;
  private final static int PORTNUM   = PollingServer.PORTNUM;
  private final static int MAXCONN   = PollingServer.MAXCONN;

  private static Socket[] sockArr = new Socket[MAXCONN];
  private static int totalConn =10;
  private static int bytesToSend =1024000;
  private static int connections = 0;
  private static int sends = 0;

  public static void main (String args[]) {

    String host = "localhost";

    if (args.length < 1 || args.length > 3) {
      System.out.println("Usage : java Client <num_connects>");
      System.out.println("      | java Client <num_connects> <server_name>");
      System.out.println("      | java Client <num_connects> <server_name>" +
                         " <max_Kbytes>");
      System.exit(-1);
    }

    if (args.length >= 1)
      totalConn = java.lang.Integer.valueOf(args[0]).intValue();
    if (args.length >= 2)
      host = args[1];
    if (args.length == 3)
      bytesToSend = java.lang.Integer.valueOf(args[2]).intValue() * 1024;


    if (totalConn <= 0 || totalConn > MAXCONN) {
      System.out.println("Connections out of range.  Terminating.");
      System.exit(-1);
    }

    System.out.println("Using " + totalConn + " connections for sending " +
                       bytesToSend + " bytes to " + host);


    try {
      Socket ctrlSock = new Socket (host, PORTNUM);
      PrintStream ctrlStream =
        new PrintStream(ctrlSock.getOutputStream());
      ctrlStream.println(bytesToSend);
      ctrlStream.println(totalConn);

      while (connections < totalConn ) {
        sockArr[connections] = new Socket (host, PORTNUM);
        connections ++;
      }
      System.out.println("Connections made : " + connections);

      byte[] buff = new byte[BYTESPEROP];
      for (int i = 0; i < BYTESPEROP; i++) // just put some junk in!
        buff[i] = (byte) i;

      Random rand = new Random(5321L);
      while (sends < bytesToSend/BYTESPEROP) {
        int idx = java.lang.Math.abs(rand.nextInt()) % totalConn;
        sockArr[idx].getOutputStream().write(buff,0,BYTESPEROP);
        sends++;
      }
      // Wait for server to say done.
      int bytes = ctrlSock.getInputStream().read(buff, 0, BYTESPEROP);
      System.out.println (" Total connections : " + connections +
                          " Bytes sent : " + sends * BYTESPEROP +
                          "...Done!");
    } catch (Exception e) { e.printStackTrace(); }
  }
}
