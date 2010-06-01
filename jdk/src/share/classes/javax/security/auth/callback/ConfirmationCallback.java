/*
 * Copyright (c) 1999, 2003, Oracle and/or its affiliates. All rights reserved.
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

package javax.security.auth.callback;

/**
 * <p> Underlying security services instantiate and pass a
 * <code>ConfirmationCallback</code> to the <code>handle</code>
 * method of a <code>CallbackHandler</code> to ask for YES/NO,
 * OK/CANCEL, YES/NO/CANCEL or other similar confirmations.
 *
 * @see javax.security.auth.callback.CallbackHandler
 */
public class ConfirmationCallback implements Callback, java.io.Serializable {

    private static final long serialVersionUID = -9095656433782481624L;

    /**
     * Unspecified option type.
     *
     * <p> The <code>getOptionType</code> method returns this
     * value if this <code>ConfirmationCallback</code> was instantiated
     * with <code>options</code> instead of an <code>optionType</code>.
     */
    public static final int UNSPECIFIED_OPTION          = -1;

    /**
     * YES/NO confirmation option.
     *
     * <p> An underlying security service specifies this as the
     * <code>optionType</code> to a <code>ConfirmationCallback</code>
     * constructor if it requires a confirmation which can be answered
     * with either <code>YES</code> or <code>NO</code>.
     */
    public static final int YES_NO_OPTION               = 0;

    /**
     * YES/NO/CANCEL confirmation confirmation option.
     *
     * <p> An underlying security service specifies this as the
     * <code>optionType</code> to a <code>ConfirmationCallback</code>
     * constructor if it requires a confirmation which can be answered
     * with either <code>YES</code>, <code>NO</code> or <code>CANCEL</code>.
     */
    public static final int YES_NO_CANCEL_OPTION        = 1;

    /**
     * OK/CANCEL confirmation confirmation option.
     *
     * <p> An underlying security service specifies this as the
     * <code>optionType</code> to a <code>ConfirmationCallback</code>
     * constructor if it requires a confirmation which can be answered
     * with either <code>OK</code> or <code>CANCEL</code>.
     */
    public static final int OK_CANCEL_OPTION            = 2;

    /**
     * YES option.
     *
     * <p> If an <code>optionType</code> was specified to this
     * <code>ConfirmationCallback</code>, this option may be specified as a
     * <code>defaultOption</code> or returned as the selected index.
     */
    public static final int YES                         = 0;

    /**
     * NO option.
     *
     * <p> If an <code>optionType</code> was specified to this
     * <code>ConfirmationCallback</code>, this option may be specified as a
     * <code>defaultOption</code> or returned as the selected index.
     */
    public static final int NO                          = 1;

    /**
     * CANCEL option.
     *
     * <p> If an <code>optionType</code> was specified to this
     * <code>ConfirmationCallback</code>, this option may be specified as a
     * <code>defaultOption</code> or returned as the selected index.
     */
    public static final int CANCEL                      = 2;

    /**
     * OK option.
     *
     * <p> If an <code>optionType</code> was specified to this
     * <code>ConfirmationCallback</code>, this option may be specified as a
     * <code>defaultOption</code> or returned as the selected index.
     */
    public static final int OK                          = 3;

    /** INFORMATION message type.  */
    public static final int INFORMATION                 = 0;

    /** WARNING message type. */
    public static final int WARNING                     = 1;

    /** ERROR message type. */
    public static final int ERROR                       = 2;
    /**
     * @serial
     * @since 1.4
     */
    private String prompt;
    /**
     * @serial
     * @since 1.4
     */
    private int messageType;
    /**
     * @serial
     * @since 1.4
     */
    private int optionType = UNSPECIFIED_OPTION;
    /**
     * @serial
     * @since 1.4
     */
    private int defaultOption;
    /**
     * @serial
     * @since 1.4
     */
    private String[] options;
    /**
     * @serial
     * @since 1.4
     */
    private int selection;

    /**
     * Construct a <code>ConfirmationCallback</code> with a
     * message type, an option type and a default option.
     *
     * <p> Underlying security services use this constructor if
     * they require either a YES/NO, YES/NO/CANCEL or OK/CANCEL
     * confirmation.
     *
     * <p>
     *
     * @param messageType the message type (<code>INFORMATION</code>,
     *                  <code>WARNING</code> or <code>ERROR</code>). <p>
     *
     * @param optionType the option type (<code>YES_NO_OPTION</code>,
     *                  <code>YES_NO_CANCEL_OPTION</code> or
     *                  <code>OK_CANCEL_OPTION</code>). <p>
     *
     * @param defaultOption the default option
     *                  from the provided optionType (<code>YES</code>,
     *                  <code>NO</code>, <code>CANCEL</code> or
     *                  <code>OK</code>).
     *
     * @exception IllegalArgumentException if messageType is not either
     *                  <code>INFORMATION</code>, <code>WARNING</code>,
     *                  or <code>ERROR</code>, if optionType is not either
     *                  <code>YES_NO_OPTION</code>,
     *                  <code>YES_NO_CANCEL_OPTION</code>, or
     *                  <code>OK_CANCEL_OPTION</code>,
     *                  or if <code>defaultOption</code>
     *                  does not correspond to one of the options in
     *                  <code>optionType</code>.
     */
    public ConfirmationCallback(int messageType,
                int optionType, int defaultOption) {

        if (messageType < INFORMATION || messageType > ERROR ||
            optionType < YES_NO_OPTION || optionType > OK_CANCEL_OPTION)
            throw new IllegalArgumentException();

        switch (optionType) {
        case YES_NO_OPTION:
            if (defaultOption != YES && defaultOption != NO)
                throw new IllegalArgumentException();
            break;
        case YES_NO_CANCEL_OPTION:
            if (defaultOption != YES && defaultOption != NO &&
                defaultOption != CANCEL)
                throw new IllegalArgumentException();
            break;
        case OK_CANCEL_OPTION:
            if (defaultOption != OK && defaultOption != CANCEL)
                throw new IllegalArgumentException();
            break;
        }

        this.messageType = messageType;
        this.optionType = optionType;
        this.defaultOption = defaultOption;
    }

    /**
     * Construct a <code>ConfirmationCallback</code> with a
     * message type, a list of options and a default option.
     *
     * <p> Underlying security services use this constructor if
     * they require a confirmation different from the available preset
     * confirmations provided (for example, CONTINUE/ABORT or STOP/GO).
     * The confirmation options are listed in the <code>options</code> array,
     * and are displayed by the <code>CallbackHandler</code> implementation
     * in a manner consistent with the way preset options are displayed.
     *
     * <p>
     *
     * @param messageType the message type (<code>INFORMATION</code>,
     *                  <code>WARNING</code> or <code>ERROR</code>). <p>
     *
     * @param options the list of confirmation options. <p>
     *
     * @param defaultOption the default option, represented as an index
     *                  into the <code>options</code> array.
     *
     * @exception IllegalArgumentException if messageType is not either
     *                  <code>INFORMATION</code>, <code>WARNING</code>,
     *                  or <code>ERROR</code>, if <code>options</code> is null,
     *                  if <code>options</code> has a length of 0,
     *                  if any element from <code>options</code> is null,
     *                  if any element from <code>options</code>
     *                  has a length of 0, or if <code>defaultOption</code>
     *                  does not lie within the array boundaries of
     *                  <code>options</code>.
     */
    public ConfirmationCallback(int messageType,
                String[] options, int defaultOption) {

        if (messageType < INFORMATION || messageType > ERROR ||
            options == null || options.length == 0 ||
            defaultOption < 0 || defaultOption >= options.length)
            throw new IllegalArgumentException();

        for (int i = 0; i < options.length; i++) {
            if (options[i] == null || options[i].length() == 0)
                throw new IllegalArgumentException();
        }

        this.messageType = messageType;
        this.options = options;
        this.defaultOption = defaultOption;
    }

    /**
     * Construct a <code>ConfirmationCallback</code> with a prompt,
     * message type, an option type and a default option.
     *
     * <p> Underlying security services use this constructor if
     * they require either a YES/NO, YES/NO/CANCEL or OK/CANCEL
     * confirmation.
     *
     * <p>
     *
     * @param prompt the prompt used to describe the list of options. <p>
     *
     * @param messageType the message type (<code>INFORMATION</code>,
     *                  <code>WARNING</code> or <code>ERROR</code>). <p>
     *
     * @param optionType the option type (<code>YES_NO_OPTION</code>,
     *                  <code>YES_NO_CANCEL_OPTION</code> or
     *                  <code>OK_CANCEL_OPTION</code>). <p>
     *
     * @param defaultOption the default option
     *                  from the provided optionType (<code>YES</code>,
     *                  <code>NO</code>, <code>CANCEL</code> or
     *                  <code>OK</code>).
     *
     * @exception IllegalArgumentException if <code>prompt</code> is null,
     *                  if <code>prompt</code> has a length of 0,
     *                  if messageType is not either
     *                  <code>INFORMATION</code>, <code>WARNING</code>,
     *                  or <code>ERROR</code>, if optionType is not either
     *                  <code>YES_NO_OPTION</code>,
     *                  <code>YES_NO_CANCEL_OPTION</code>, or
     *                  <code>OK_CANCEL_OPTION</code>,
     *                  or if <code>defaultOption</code>
     *                  does not correspond to one of the options in
     *                  <code>optionType</code>.
     */
    public ConfirmationCallback(String prompt, int messageType,
                int optionType, int defaultOption) {

        if (prompt == null || prompt.length() == 0 ||
            messageType < INFORMATION || messageType > ERROR ||
            optionType < YES_NO_OPTION || optionType > OK_CANCEL_OPTION)
            throw new IllegalArgumentException();

        switch (optionType) {
        case YES_NO_OPTION:
            if (defaultOption != YES && defaultOption != NO)
                throw new IllegalArgumentException();
            break;
        case YES_NO_CANCEL_OPTION:
            if (defaultOption != YES && defaultOption != NO &&
                defaultOption != CANCEL)
                throw new IllegalArgumentException();
            break;
        case OK_CANCEL_OPTION:
            if (defaultOption != OK && defaultOption != CANCEL)
                throw new IllegalArgumentException();
            break;
        }

        this.prompt = prompt;
        this.messageType = messageType;
        this.optionType = optionType;
        this.defaultOption = defaultOption;
    }

    /**
     * Construct a <code>ConfirmationCallback</code> with a prompt,
     * message type, a list of options and a default option.
     *
     * <p> Underlying security services use this constructor if
     * they require a confirmation different from the available preset
     * confirmations provided (for example, CONTINUE/ABORT or STOP/GO).
     * The confirmation options are listed in the <code>options</code> array,
     * and are displayed by the <code>CallbackHandler</code> implementation
     * in a manner consistent with the way preset options are displayed.
     *
     * <p>
     *
     * @param prompt the prompt used to describe the list of options. <p>
     *
     * @param messageType the message type (<code>INFORMATION</code>,
     *                  <code>WARNING</code> or <code>ERROR</code>). <p>
     *
     * @param options the list of confirmation options. <p>
     *
     * @param defaultOption the default option, represented as an index
     *                  into the <code>options</code> array.
     *
     * @exception IllegalArgumentException if <code>prompt</code> is null,
     *                  if <code>prompt</code> has a length of 0,
     *                  if messageType is not either
     *                  <code>INFORMATION</code>, <code>WARNING</code>,
     *                  or <code>ERROR</code>, if <code>options</code> is null,
     *                  if <code>options</code> has a length of 0,
     *                  if any element from <code>options</code> is null,
     *                  if any element from <code>options</code>
     *                  has a length of 0, or if <code>defaultOption</code>
     *                  does not lie within the array boundaries of
     *                  <code>options</code>.
     */
    public ConfirmationCallback(String prompt, int messageType,
                String[] options, int defaultOption) {

        if (prompt == null || prompt.length() == 0 ||
            messageType < INFORMATION || messageType > ERROR ||
            options == null || options.length == 0 ||
            defaultOption < 0 || defaultOption >= options.length)
            throw new IllegalArgumentException();

        for (int i = 0; i < options.length; i++) {
            if (options[i] == null || options[i].length() == 0)
                throw new IllegalArgumentException();
        }

        this.prompt = prompt;
        this.messageType = messageType;
        this.options = options;
        this.defaultOption = defaultOption;
    }

    /**
     * Get the prompt.
     *
     * <p>
     *
     * @return the prompt, or null if this <code>ConfirmationCallback</code>
     *          was instantiated without a <code>prompt</code>.
     */
    public String getPrompt() {
        return prompt;
    }

    /**
     * Get the message type.
     *
     * <p>
     *
     * @return the message type (<code>INFORMATION</code>,
     *          <code>WARNING</code> or <code>ERROR</code>).
     */
    public int getMessageType() {
        return messageType;
    }

    /**
     * Get the option type.
     *
     * <p> If this method returns <code>UNSPECIFIED_OPTION</code>, then this
     * <code>ConfirmationCallback</code> was instantiated with
     * <code>options</code> instead of an <code>optionType</code>.
     * In this case, invoke the <code>getOptions</code> method
     * to determine which confirmation options to display.
     *
     * <p>
     *
     * @return the option type (<code>YES_NO_OPTION</code>,
     *          <code>YES_NO_CANCEL_OPTION</code> or
     *          <code>OK_CANCEL_OPTION</code>), or
     *          <code>UNSPECIFIED_OPTION</code> if this
     *          <code>ConfirmationCallback</code> was instantiated with
     *          <code>options</code> instead of an <code>optionType</code>.
     */
    public int getOptionType() {
        return optionType;
    }

    /**
     * Get the confirmation options.
     *
     * <p>
     *
     * @return the list of confirmation options, or null if this
     *          <code>ConfirmationCallback</code> was instantiated with
     *          an <code>optionType</code> instead of <code>options</code>.
     */
    public String[] getOptions() {
        return options;
    }

    /**
     * Get the default option.
     *
     * <p>
     *
     * @return the default option, represented as
     *          <code>YES</code>, <code>NO</code>, <code>OK</code> or
     *          <code>CANCEL</code> if an <code>optionType</code>
     *          was specified to the constructor of this
     *          <code>ConfirmationCallback</code>.
     *          Otherwise, this method returns the default option as
     *          an index into the
     *          <code>options</code> array specified to the constructor
     *          of this <code>ConfirmationCallback</code>.
     */
    public int getDefaultOption() {
        return defaultOption;
    }

    /**
     * Set the selected confirmation option.
     *
     * <p>
     *
     * @param selection the selection represented as <code>YES</code>,
     *          <code>NO</code>, <code>OK</code> or <code>CANCEL</code>
     *          if an <code>optionType</code> was specified to the constructor
     *          of this <code>ConfirmationCallback</code>.
     *          Otherwise, the selection represents the index into the
     *          <code>options</code> array specified to the constructor
     *          of this <code>ConfirmationCallback</code>.
     *
     * @see #getSelectedIndex
     */
    public void setSelectedIndex(int selection) {
        this.selection = selection;
    }

    /**
     * Get the selected confirmation option.
     *
     * <p>
     *
     * @return the selected confirmation option represented as
     *          <code>YES</code>, <code>NO</code>, <code>OK</code> or
     *          <code>CANCEL</code> if an <code>optionType</code>
     *          was specified to the constructor of this
     *          <code>ConfirmationCallback</code>.
     *          Otherwise, this method returns the selected confirmation
     *          option as an index into the
     *          <code>options</code> array specified to the constructor
     *          of this <code>ConfirmationCallback</code>.
     *
     * @see #setSelectedIndex
     */
    public int getSelectedIndex() {
        return selection;
    }
}
