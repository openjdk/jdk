/*
 *
 * Copyright (c) 2001, 2002, Oracle and/or its affiliates. All rights reserved.
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
 * Neither the name of Oracle nor the names of
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

import javax.security.auth.*;
import javax.security.auth.callback.*;
import javax.security.auth.login.*;
import com.sun.security.auth.callback.TextCallbackHandler;

/*
 * @test
 * @bug 8215916
 * @summary This Sample application attempts to authenticate a user
 * and reports whether or not the authentication was successful.
 * @compile JaasAcn.java
 * @run main/othervm -Djava.security.auth.login.config=file:${test.src}/jaas.conf -Djava.security.debug=logincontext JaasAcn
 */

/**
 * This JaasAcn application attempts to authenticate a user
 * and reports whether or not the authentication was successful.
 */
public class JaasAcn {

    public static void main(String[] args) {

        // Obtain a LoginContext, needed for authentication. Tell it
        // to use the LoginModule implementation specified by the
        // entry named "JaasSample" in the JAAS login configuration
        // file and to also use the specified CallbackHandler.
        LoginContext lc = null;
        try {
            lc = new LoginContext("JaasSample", new TextCallbackHandler());
        } catch (LoginException le) {
            System.err.println("Cannot create LoginContext. "
                + le.getMessage());
            System.exit(-1);
        } catch (SecurityException se) {
            System.err.println("Cannot create LoginContext. "
                + se.getMessage());
            System.exit(-1);
        }

        try {

            // attempt authentication
            lc.login();

        } catch (LoginException le) {

            System.err.println("Authentication failed:");
            System.err.println("  " + le.getMessage());
            System.exit(-1);

        }

        System.out.println("Authentication succeeded!");

    }
}
