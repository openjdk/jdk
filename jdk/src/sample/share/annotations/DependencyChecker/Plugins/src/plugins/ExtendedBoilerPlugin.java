/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
package plugins;

import checker.Module;
import checker.Require;
import java.util.Calendar;

/**
 * Introduces new features for BoilerPlugin. Features are boiling water by an
 * SMS and boiling water by date with notification by a phone call.
 */
@Require(value = Module.SPEAKER)
@Require(value = Module.GSM, minVersion = 3)
@Require(value = Module.DISPLAY)
public class ExtendedBoilerPlugin extends BoilerPlugin {

    /**
     * Boils water at the appointed time and wakes you up by a ring and phone
     * call. Shows "Good morning" and a quote of the day from the Internet on the
     * display.
     *
     * @param calendar - date and time when water should be boiled
     * @param phoneNumber - phone number to call
     */
    public void boilAndWakeUp(Calendar calendar, int phoneNumber) {
        //implementation
    }

    /**
     * Boils water at the appointed time by getting an SMS of fixed format.
     * Sends an SMS on finish.
     *
     * @param sms - text of SMS
     */
    public void boilBySMS(String sms) {
        //implementation
    }
}
