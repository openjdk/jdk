/*
 * Copyright (c) 1997, 2012, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.ws.policy;

import com.sun.xml.internal.ws.policy.privateutil.PolicyLogger;
import com.sun.xml.internal.ws.policy.privateutil.PolicyUtils;
import com.sun.xml.internal.ws.policy.spi.PolicyAssertionValidator;
import static com.sun.xml.internal.ws.policy.privateutil.LocalizationMessages.WSP_0076_NO_SERVICE_PROVIDERS_FOUND;

import java.util.Collection;
import java.util.LinkedList;

/**
 * Provides methods for assertion validation.
 *
 * @author Marek Potociar (marek.potociar at sun.com)
 * @author Fabian Ritzmann
 */
public class AssertionValidationProcessor {
    private static final PolicyLogger LOGGER = PolicyLogger.getLogger(AssertionValidationProcessor.class);

    private final Collection<PolicyAssertionValidator> validators = new LinkedList<PolicyAssertionValidator>();

    /**
     * This constructor instantiates the object with a set of dynamically
     * discovered PolicyAssertionValidators.
     *
     * @throws PolicyException Thrown if the set of dynamically discovered
     *   PolicyAssertionValidators is empty.
     */
    private AssertionValidationProcessor() throws PolicyException {
        this(null);
    }

    /**
     * This constructor adds the given set of policy validators to the dynamically
     * discovered PolicyAssertionValidators.
     *
     * This constructor is intended to be used by the JAX-WS com.sun.xml.internal.ws.policy.api.ValidationProcessor.
     *
     * @param policyValidators A set of PolicyAssertionValidators. May be null
     * @throws PolicyException Thrown if the set of given PolicyAssertionValidators
     *   and dynamically discovered PolicyAssertionValidators is empty.
     */
    protected AssertionValidationProcessor(final Collection<PolicyAssertionValidator> policyValidators)
            throws PolicyException {
        for(PolicyAssertionValidator validator : PolicyUtils.ServiceProvider.load(PolicyAssertionValidator.class)) {
            validators.add(validator);
        }
        if (policyValidators != null) {
            for (PolicyAssertionValidator validator : policyValidators) {
                validators.add(validator);
            }
        }
        if (validators.size() == 0) {
            throw LOGGER.logSevereException(new PolicyException(WSP_0076_NO_SERVICE_PROVIDERS_FOUND(PolicyAssertionValidator.class.getName())));
        }
    }

    /**
     * Factory method that returns singleton instance of the class.
     *
     * This method is only intended to be used by code that has no dependencies on
     * JAX-WS. Otherwise use com.sun.xml.internal.ws.api.policy.ValidationProcessor.
     *
     * @return singleton An instance of the class.
     * @throws PolicyException If instantiation failed.
     */
    public static AssertionValidationProcessor getInstance() throws PolicyException {
        return new AssertionValidationProcessor();
    }

    /**
     * Validates fitness of the {@code assertion} on the client side.
     *
     * return client side {@code assertion} fitness
     * @param assertion The assertion to be validated.
     * @return The fitness of the assertion on the client side.
     * @throws PolicyException If validation failed.
     */
    public PolicyAssertionValidator.Fitness validateClientSide(final PolicyAssertion assertion) throws PolicyException {
        PolicyAssertionValidator.Fitness assertionFitness = PolicyAssertionValidator.Fitness.UNKNOWN;
        for ( PolicyAssertionValidator validator : validators ) {
            assertionFitness = assertionFitness.combine(validator.validateClientSide(assertion));
            if (assertionFitness == PolicyAssertionValidator.Fitness.SUPPORTED) {
                break;
            }
        }

        return assertionFitness;
    }

    /**
     * Validates fitness of the {@code assertion} on the server side.
     *
     * return server side {@code assertion} fitness
     * @param assertion The assertion to be validated.
     * @return The fitness of the assertion on the server side.
     * @throws PolicyException If validation failed.
     */
    public PolicyAssertionValidator.Fitness validateServerSide(final PolicyAssertion assertion) throws PolicyException {
        PolicyAssertionValidator.Fitness assertionFitness = PolicyAssertionValidator.Fitness.UNKNOWN;
        for (PolicyAssertionValidator validator : validators) {
            assertionFitness = assertionFitness.combine(validator.validateServerSide(assertion));
            if (assertionFitness == PolicyAssertionValidator.Fitness.SUPPORTED) {
                break;
            }
        }

        return assertionFitness;
    }
}
