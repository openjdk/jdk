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

/**
 * BoilerPlugin provides support for boiling water and keeping water warm.
 */
@Require(value = Module.CLOCK, maxVersion = 3)
@Require(value = Module.THERMOMETER)
@Require(value = Module.HEATER)
@Require(value = Module.LED, optional = true) //will use if present
public class BoilerPlugin {

    /**
     * Heats water up to 100 degrees Celsius.
     */
    public void boil() {
        boil(100);
    }

    /**
     * Heats water up to temperature.
     *
     * @param temperature - desired temperature of the water in the boiler
     */
    public void boil(int temperature) {
        /*
         * Turn on heater and wait while temperature reaches desired temperature
         * in Celsius. Finally, turn off heater.
         * If present, the LED light changes color according to the temperature.
         */
    }

    /**
     * Keeps desired temperature.
     *
     * @param temperature - desired temperature of the water in the boiler
     * @param seconds - period of time for checking temperature in seconds
     */
    public void keepWarm(int temperature, int seconds) {
        //Every n seconds check temperature and warm up, if necessary.
    }

}
