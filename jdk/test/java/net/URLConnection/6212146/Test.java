/*
 * Copyright (c) 2006, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

import java.net.*;
import java.io.*;

public class Test {

    public static void main(String[] args)
         throws Exception {
      String BASE_DIR = args[0];
      String ARCHIVE_NAME = args[1];
      String lProperty = System.getProperty( "do.iterations", "5000" );
      int lRepetitions = new Integer( lProperty ).intValue();
      System.out.println ( "Start creating copys of the archive, " + lRepetitions + " times" );
      for( int i = 0; i < lRepetitions; i++ ) {
         // Copy the given jar file and add a prefix
         copyFile( BASE_DIR, ARCHIVE_NAME, i);
      }
      System.out.println ( "Start opening the archives archive, " + lRepetitions + " times" );
      System.out.println ( "First URL is jar:file://" + BASE_DIR + "1" + ARCHIVE_NAME + "!/foo/Test.class");
      for( int i = 0; i < lRepetitions; i++ ) {
         // Create ULR
         String lURLPath = "jar:file://" + BASE_DIR + i + ARCHIVE_NAME + "!/foo/Test.class";
         URL lURL = new URL( lURLPath );
         // Open URL Connection
         try {
            URLConnection lConnection = lURL.openConnection();
            lConnection.getInputStream();
         } catch( java.io.FileNotFoundException fnfe ) {
            // Ignore this one because we expect this one
         } catch( java.util.zip.ZipException ze ) {
            throw new RuntimeException ("Test failed: " + ze.getMessage());
         }
      }
      //System.out.println ( "Done testing, waiting 20 seconds for checking" );
      //System.out.println ( "Cleaning up");
      //for( int i = 0; i < lRepetitions; i++ ) {
         // Copy the given jar file and add a prefix
         //deleteFile( BASE_DIR, i, ARCHIVE_NAME);
      ////}
   }

   private static void deleteFile (String BASE_DIR, int pIndex, String pArchiveName) {
         java.io.File file = new java.io.File (BASE_DIR, pIndex + pArchiveName );
         file.delete ();
   }

   private static void copyFile( String pBaseDir, String pArchiveName, int pIndex) {
      try {
         java.io.File lSource = new java.io.File( pBaseDir, pArchiveName );
         java.io.File lDestination = new java.io.File( pBaseDir, pIndex + pArchiveName );
         if( !lDestination.exists() ) {
            lDestination.createNewFile();
            java.io.InputStream lInput = new java.io.FileInputStream( lSource );
            java.io.OutputStream lOutput = new java.io.FileOutputStream( lDestination );
            byte[] lBuffer = new byte[ 1024 ];
            int lLength = -1;
            while( ( lLength = lInput.read( lBuffer ) ) > 0 ) {
               lOutput.write( lBuffer, 0, lLength );
            }
            lInput.close();
            lOutput.close();
         }
      } catch( Exception e ) {
         e.printStackTrace();
      }
   }
}
