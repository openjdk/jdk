/*
 * Copyright (c) 1998, 2004, Oracle and/or its affiliates. All rights reserved.
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

package javax.security.auth.spi;

import javax.security.auth.Subject;
import javax.security.auth.AuthPermission;
import javax.security.auth.callback.*;
import javax.security.auth.login.*;
import java.util.Map;

/**
 * <p> <code>LoginModule</code> describes the interface
 * implemented by authentication technology providers.  LoginModules
 * are plugged in under applications to provide a particular type of
 * authentication.
 *
 * <p> While applications write to the <code>LoginContext</code> API,
 * authentication technology providers implement the
 * <code>LoginModule</code> interface.
 * A <code>Configuration</code> specifies the LoginModule(s)
 * to be used with a particular login application.  Therefore different
 * LoginModules can be plugged in under the application without
 * requiring any modifications to the application itself.
 *
 * <p> The <code>LoginContext</code> is responsible for reading the
 * <code>Configuration</code> and instantiating the appropriate
 * LoginModules.  Each <code>LoginModule</code> is initialized with
 * a <code>Subject</code>, a <code>CallbackHandler</code>, shared
 * <code>LoginModule</code> state, and LoginModule-specific options.
 *
 * The <code>Subject</code> represents the
 * <code>Subject</code> currently being authenticated and is updated
 * with relevant Credentials if authentication succeeds.
 * LoginModules use the <code>CallbackHandler</code> to
 * communicate with users.  The <code>CallbackHandler</code> may be
 * used to prompt for usernames and passwords, for example.
 * Note that the <code>CallbackHandler</code> may be null.  LoginModules
 * which absolutely require a <code>CallbackHandler</code> to authenticate
 * the <code>Subject</code> may throw a <code>LoginException</code>.
 * LoginModules optionally use the shared state to share information
 * or data among themselves.
 *
 * <p> The LoginModule-specific options represent the options
 * configured for this <code>LoginModule</code> by an administrator or user
 * in the login <code>Configuration</code>.
 * The options are defined by the <code>LoginModule</code> itself
 * and control the behavior within it.  For example, a
 * <code>LoginModule</code> may define options to support debugging/testing
 * capabilities.  Options are defined using a key-value syntax,
 * such as <i>debug=true</i>.  The <code>LoginModule</code>
 * stores the options as a <code>Map</code> so that the values may
 * be retrieved using the key.  Note that there is no limit to the number
 * of options a <code>LoginModule</code> chooses to define.
 *
 * <p> The calling application sees the authentication process as a single
 * operation.  However, the authentication process within the
 * <code>LoginModule</code> proceeds in two distinct phases.
 * In the first phase, the LoginModule's
 * <code>login</code> method gets invoked by the LoginContext's
 * <code>login</code> method.  The <code>login</code>
 * method for the <code>LoginModule</code> then performs
 * the actual authentication (prompt for and verify a password for example)
 * and saves its authentication status as private state
 * information.  Once finished, the LoginModule's <code>login</code>
 * method either returns <code>true</code> (if it succeeded) or
 * <code>false</code> (if it should be ignored), or throws a
 * <code>LoginException</code> to specify a failure.
 * In the failure case, the <code>LoginModule</code> must not retry the
 * authentication or introduce delays.  The responsibility of such tasks
 * belongs to the application.  If the application attempts to retry
 * the authentication, the LoginModule's <code>login</code> method will be
 * called again.
 *
 * <p> In the second phase, if the LoginContext's overall authentication
 * succeeded (the relevant REQUIRED, REQUISITE, SUFFICIENT and OPTIONAL
 * LoginModules succeeded), then the <code>commit</code>
 * method for the <code>LoginModule</code> gets invoked.
 * The <code>commit</code> method for a <code>LoginModule</code> checks its
 * privately saved state to see if its own authentication succeeded.
 * If the overall <code>LoginContext</code> authentication succeeded
 * and the LoginModule's own authentication succeeded, then the
 * <code>commit</code> method associates the relevant
 * Principals (authenticated identities) and Credentials (authentication data
 * such as cryptographic keys) with the <code>Subject</code>
 * located within the <code>LoginModule</code>.
 *
 * <p> If the LoginContext's overall authentication failed (the relevant
 * REQUIRED, REQUISITE, SUFFICIENT and OPTIONAL LoginModules did not succeed),
 * then the <code>abort</code> method for each <code>LoginModule</code>
 * gets invoked.  In this case, the <code>LoginModule</code> removes/destroys
 * any authentication state originally saved.
 *
 * <p> Logging out a <code>Subject</code> involves only one phase.
 * The <code>LoginContext</code> invokes the LoginModule's <code>logout</code>
 * method.  The <code>logout</code> method for the <code>LoginModule</code>
 * then performs the logout procedures, such as removing Principals or
 * Credentials from the <code>Subject</code> or logging session information.
 *
 * <p> A <code>LoginModule</code> implementation must have a constructor with
 * no arguments.  This allows classes which load the <code>LoginModule</code>
 * to instantiate it.
 *
 * @see javax.security.auth.login.LoginContext
 * @see javax.security.auth.login.Configuration
 */
public interface LoginModule {

    /**
     * Initialize this LoginModule.
     *
     * <p> This method is called by the <code>LoginContext</code>
     * after this <code>LoginModule</code> has been instantiated.
     * The purpose of this method is to initialize this
     * <code>LoginModule</code> with the relevant information.
     * If this <code>LoginModule</code> does not understand
     * any of the data stored in <code>sharedState</code> or
     * <code>options</code> parameters, they can be ignored.
     *
     * <p>
     *
     * @param subject the <code>Subject</code> to be authenticated. <p>
     *
     * @param callbackHandler a <code>CallbackHandler</code> for communicating
     *                  with the end user (prompting for usernames and
     *                  passwords, for example). <p>
     *
     * @param sharedState state shared with other configured LoginModules. <p>
     *
     * @param options options specified in the login
     *                  <code>Configuration</code> for this particular
     *                  <code>LoginModule</code>.
     */
    void initialize(Subject subject, CallbackHandler callbackHandler,
                    Map<String,?> sharedState,
                    Map<String,?> options);

    /**
     * Method to authenticate a <code>Subject</code> (phase 1).
     *
     * <p> The implementation of this method authenticates
     * a <code>Subject</code>.  For example, it may prompt for
     * <code>Subject</code> information such
     * as a username and password and then attempt to verify the password.
     * This method saves the result of the authentication attempt
     * as private state within the LoginModule.
     *
     * <p>
     *
     * @exception LoginException if the authentication fails
     *
     * @return true if the authentication succeeded, or false if this
     *                  <code>LoginModule</code> should be ignored.
     */
    boolean login() throws LoginException;

    /**
     * Method to commit the authentication process (phase 2).
     *
     * <p> This method is called if the LoginContext's
     * overall authentication succeeded
     * (the relevant REQUIRED, REQUISITE, SUFFICIENT and OPTIONAL LoginModules
     * succeeded).
     *
     * <p> If this LoginModule's own authentication attempt
     * succeeded (checked by retrieving the private state saved by the
     * <code>login</code> method), then this method associates relevant
     * Principals and Credentials with the <code>Subject</code> located in the
     * <code>LoginModule</code>.  If this LoginModule's own
     * authentication attempted failed, then this method removes/destroys
     * any state that was originally saved.
     *
     * <p>
     *
     * @exception LoginException if the commit fails
     *
     * @return true if this method succeeded, or false if this
     *                  <code>LoginModule</code> should be ignored.
     */
    boolean commit() throws LoginException;

    /**
     * Method to abort the authentication process (phase 2).
     *
     * <p> This method is called if the LoginContext's
     * overall authentication failed.
     * (the relevant REQUIRED, REQUISITE, SUFFICIENT and OPTIONAL LoginModules
     * did not succeed).
     *
     * <p> If this LoginModule's own authentication attempt
     * succeeded (checked by retrieving the private state saved by the
     * <code>login</code> method), then this method cleans up any state
     * that was originally saved.
     *
     * <p>
     *
     * @exception LoginException if the abort fails
     *
     * @return true if this method succeeded, or false if this
     *                  <code>LoginModule</code> should be ignored.
     */
    boolean abort() throws LoginException;

    /**
     * Method which logs out a <code>Subject</code>.
     *
     * <p>An implementation of this method might remove/destroy a Subject's
     * Principals and Credentials.
     *
     * <p>
     *
     * @exception LoginException if the logout fails
     *
     * @return true if this method succeeded, or false if this
     *                  <code>LoginModule</code> should be ignored.
     */
    boolean logout() throws LoginException;
}
