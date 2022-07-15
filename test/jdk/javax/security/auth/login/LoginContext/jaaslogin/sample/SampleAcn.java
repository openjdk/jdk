/*
 * @(#)SampleAcn.java	
 *
 * Copyright 2001-2002 Sun Microsystems, Inc. All Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or 
 * without modification, are permitted provided that the following 
 * conditions are met:
 * 
 * -Redistributions of source code must retain the above copyright  
 * notice, this  list of conditions and the following disclaimer.
 * 
 * -Redistribution in binary form must reproduct the above copyright 
 * notice, this list of conditions and the following disclaimer in 
 * the documentation and/or other materials provided with the 
 * distribution.
 * 
 * Neither the name of Sun Microsystems, Inc. or the names of 
 * contributors may be used to endorse or promote products derived 
 * from this software without specific prior written permission.
 * 
 * This software is provided "AS IS," without a warranty of any 
 * kind. ALL EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND 
 * WARRANTIES, INCLUDING ANY IMPLIED WARRANTY OF MERCHANTABILITY, 
 * FITNESS FOR A PARTICULAR PURPOSE OR NON-INFRINGEMENT, ARE HEREBY 
 * EXCLUDED. SUN AND ITS LICENSORS SHALL NOT BE LIABLE FOR ANY 
 * DAMAGES OR LIABILITIES  SUFFERED BY LICENSEE AS A RESULT OF  OR 
 * RELATING TO USE, MODIFICATION OR DISTRIBUTION OF THE SOFTWARE OR 
 * ITS DERIVATIVES. IN NO EVENT WILL SUN OR ITS LICENSORS BE LIABLE 
 * FOR ANY LOST REVENUE, PROFIT OR DATA, OR FOR DIRECT, INDIRECT, 
 * SPECIAL, CONSEQUENTIAL, INCIDENTAL OR PUNITIVE DAMAGES, HOWEVER 
 * CAUSED AND REGARDLESS OF THE THEORY OF LIABILITY, ARISING OUT OF 
 * THE USE OF OR INABILITY TO USE SOFTWARE, EVEN IF SUN HAS BEEN 
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 * 
 * You acknowledge that Software is not designed, licensed or 
 * intended for use in the design, construction, operation or 
 * maintenance of any nuclear facility. 
 */

package sample;

import java.io.*;
import java.util.*;
import javax.security.auth.login.*;
import javax.security.auth.*;
import javax.security.auth.callback.*;

/*
 * @test
 * @bug 8215916
 * @summary This Sample application attempts to authenticate a user
 * and reports whether or not the authentication was successful.
 * @compile SampleAcn.java module/SampleLoginModule.java principal/SamplePrincipal.java
 * @run main/othervm -Djava.security.auth.login.config==samplejaas.config sample.SampleAcn
 */

/**
 * <p> This Sample application attempts to authenticate a user
 * and reports whether or not the authentication was successful.
 */
public class SampleAcn {

   /**
    * Attempt to authenticate the user.
    *
    * <p>
    * 
    * @param args input arguments for this application.  These are ignored.
    */
    public static void main(String[] args) {

	// Obtain a LoginContext, needed for authentication. Tell it 
	// to use the LoginModule implementation specified by the 
	// entry named "Sample" in the JAAS login configuration 
	// file and to also use the specified CallbackHandler.
	LoginContext lc = null;
	try {
	    lc = new LoginContext("Sample", new MyCallbackHandler());
	} catch (LoginException le) {
	    System.err.println("Cannot create LoginContext. "
	        + le.getMessage());
	    System.exit(-1);
	} catch (SecurityException se) {
	    System.err.println("Cannot create LoginContext. "
	        + se.getMessage());
	    System.exit(-1);
	} 

	// the user has 3 attempts to authenticate successfully
	int i;
	for (i = 0; i < 3; i++) {
	    try {

		// attempt authentication
		lc.login();

		// if we return with no exception, authentication succeeded
		break;

	    } catch (LoginException le) {

		  System.err.println("Authentication failed:");
		  System.err.println("  " + le.getMessage());
		  try {
		      Thread.currentThread().sleep(3000);
		  } catch (Exception e) {
		      // ignore
		  } 
	
	    }
	}

	// did they fail three times?
	if (i == 3) {
	    System.out.println("Sorry");
	    System.exit(-1);
	}

	System.out.println("Authentication succeeded!");

    }
}


/**
 * The application implements the CallbackHandler.
 *
 * <p> This application is text-based.  Therefore it displays information
 * to the user using the OutputStreams System.out and System.err,
 * and gathers input from the user using the InputStream System.in.
 */
class MyCallbackHandler implements CallbackHandler {

    /**
     * Invoke an array of Callbacks.
     *
     * <p>
     *
     * @param callbacks an array of <code>Callback</code> objects which contain
     *			the information requested by an underlying security
     *			service to be retrieved or displayed.
     *
     * @exception java.io.IOException if an input or output error occurs. <p>
     *
     * @exception UnsupportedCallbackException if the implementation of this
     *			method does not support one or more of the Callbacks
     *			specified in the <code>callbacks</code> parameter.
     */
    public void handle(Callback[] callbacks)
    throws IOException, UnsupportedCallbackException {
      
	for (int i = 0; i < callbacks.length; i++) {
	    if (callbacks[i] instanceof TextOutputCallback) {
      
		// display the message according to the specified type
		TextOutputCallback toc = (TextOutputCallback)callbacks[i];
		switch (toc.getMessageType()) {
		case TextOutputCallback.INFORMATION:
 		    System.out.println(toc.getMessage());
 		    break;
 		case TextOutputCallback.ERROR:
 		    System.out.println("ERROR: " + toc.getMessage());
 		    break;
 		case TextOutputCallback.WARNING:
 		    System.out.println("WARNING: " + toc.getMessage());
 		    break;
 		default:
 		    throw new IOException("Unsupported message type: " +
 					toc.getMessageType());
 		}
 
 	    } else if (callbacks[i] instanceof NameCallback) {
  
 		// prompt the user for a username
 		NameCallback nc = (NameCallback)callbacks[i];
  
 		System.err.print(nc.getPrompt());
 		System.err.flush();
 		nc.setName((new BufferedReader
			(new InputStreamReader(System.in))).readLine());
 
 	    } else if (callbacks[i] instanceof PasswordCallback) {
  
 		// prompt the user for sensitive information
 		PasswordCallback pc = (PasswordCallback)callbacks[i];
 		System.err.print(pc.getPrompt());
 		System.err.flush();
 		pc.setPassword(readPassword(System.in));
  
 	    } else {
 		throw new UnsupportedCallbackException
 			(callbacks[i], "Unrecognized Callback");
 	    }
	}
    }
   
    // Reads user password from given input stream.
    private char[] readPassword(InputStream in) throws IOException {
	
	char[] lineBuffer;
	char[] buf;
	int i;

	buf = lineBuffer = new char[128];

	int room = buf.length;
	int offset = 0;
	int c;

loop:	while (true) {
 	    switch (c = in.read()) {
 	    case -1:
 	    case '\n':
		break loop;

 	    case '\r':
 		int c2 = in.read();
 		if ((c2 != '\n') && (c2 != -1)) {
 		    if (!(in instanceof PushbackInputStream)) {
 			in = new PushbackInputStream(in);
 		    }
 		    ((PushbackInputStream)in).unread(c2);
 		} else
 		    break loop;

 	    default:
 		if (--room < 0) {
 		    buf = new char[offset + 128];
 		    room = buf.length - offset - 1;
 		    System.arraycopy(lineBuffer, 0, buf, 0, offset);
 		    Arrays.fill(lineBuffer, ' ');
 		    lineBuffer = buf;
 		}
 		buf[offset++] = (char) c;
 		break;
 	    }
 	}

 	if (offset == 0) {
 	    return null;
 	}

 	char[] ret = new char[offset];
 	System.arraycopy(buf, 0, ret, 0, offset);
 	Arrays.fill(buf, ' ');

 	return ret;
    }
}

