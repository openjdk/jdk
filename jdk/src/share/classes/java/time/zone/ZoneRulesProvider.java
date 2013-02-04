/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
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

/*
 * This file is available under and governed by the GNU General Public
 * License version 2 only, as published by the Free Software Foundation.
 * However, the following notice accompanied the original version of this
 * file:
 *
 * Copyright (c) 2009-2012, Stephen Colebourne & Michael Nascimento Santos
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 *  * Neither the name of JSR-310 nor the names of its contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package java.time.zone;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Provider of time-zone rules to the system.
 * <p>
 * This class manages the configuration of time-zone rules.
 * The static methods provide the public API that can be used to manage the providers.
 * The abstract methods provide the SPI that allows rules to be provided.
 * <p>
 * Rules are looked up primarily by zone ID, as used by {@link ZoneId}.
 * Only zone region IDs may be used, zone offset IDs are not used here.
 * <p>
 * Time-zone rules are political, thus the data can change at any time.
 * Each provider will provide the latest rules for each zone ID, but they
 * may also provide the history of how the rules changed.
 *
 * <h3>Specification for implementors</h3>
 * This interface is a service provider that can be called by multiple threads.
 * Implementations must be immutable and thread-safe.
 * <p>
 * Providers must ensure that once a rule has been seen by the application, the
 * rule must continue to be available.
 * <p>
 * Many systems would like to update time-zone rules dynamically without stopping the JVM.
 * When examined in detail, this is a complex problem.
 * Providers may choose to handle dynamic updates, however the default provider does not.
 *
 * @since 1.8
 */
public abstract class ZoneRulesProvider {

    /**
     * The set of loaded providers.
     */
    private static final CopyOnWriteArrayList<ZoneRulesProvider> PROVIDERS = new CopyOnWriteArrayList<>();
    /**
     * The lookup from zone region ID to provider.
     */
    private static final ConcurrentMap<String, ZoneRulesProvider> ZONES = new ConcurrentHashMap<>(512, 0.75f, 2);
    static {
        registerProvider(new TzdbZoneRulesProvider());
        ServiceLoader<ZoneRulesProvider> sl = ServiceLoader.load(ZoneRulesProvider.class, ClassLoader.getSystemClassLoader());
        List<ZoneRulesProvider> loaded = new ArrayList<>();
        Iterator<ZoneRulesProvider> it = sl.iterator();
        while (it.hasNext()) {
            ZoneRulesProvider provider;
            try {
                provider = it.next();
            } catch (ServiceConfigurationError ex) {
                if (ex.getCause() instanceof SecurityException) {
                    continue;  // ignore the security exception, try the next provider
                }
                throw ex;
            }
            registerProvider0(provider);
        }
        // CopyOnWriteList could be slow if lots of providers and each added individually
        PROVIDERS.addAll(loaded);
    }

    //-------------------------------------------------------------------------
    /**
     * Gets the set of available zone IDs.
     * <p>
     * These zone IDs are loaded and available for use by {@code ZoneId}.
     *
     * @return a modifiable copy of the set of zone IDs, not null
     */
    public static Set<String> getAvailableZoneIds() {
        return new HashSet<>(ZONES.keySet());
    }

    /**
     * Gets the rules for the zone ID.
     * <p>
     * This returns the latest available rules for the zone ID.
     * <p>
     * This method relies on time-zone data provider files that are configured.
     * These are loaded using a {@code ServiceLoader}.
     *
     * @param zoneId  the zone region ID as used by {@code ZoneId}, not null
     * @return the rules for the ID, not null
     * @throws ZoneRulesException if the zone ID is unknown
     */
    public static ZoneRules getRules(String zoneId) {
        Objects.requireNonNull(zoneId, "zoneId");
        return getProvider(zoneId).provideRules(zoneId);
    }

    /**
     * Gets the history of rules for the zone ID.
     * <p>
     * Time-zones are defined by governments and change frequently.
     * This method allows applications to find the history of changes to the
     * rules for a single zone ID. The map is keyed by a string, which is the
     * version string associated with the rules.
     * <p>
     * The exact meaning and format of the version is provider specific.
     * The version must follow lexicographical order, thus the returned map will
     * be order from the oldest known rules to the newest available rules.
     * The default 'TZDB' group uses version numbering consisting of the year
     * followed by a letter, such as '2009e' or '2012f'.
     * <p>
     * Implementations must provide a result for each valid zone ID, however
     * they do not have to provide a history of rules.
     * Thus the map will always contain one element, and will only contain more
     * than one element if historical rule information is available.
     *
     * @param zoneId  the zone region ID as used by {@code ZoneId}, not null
     * @return a modifiable copy of the history of the rules for the ID, sorted
     *  from oldest to newest, not null
     * @throws ZoneRulesException if the zone ID is unknown
     */
    public static NavigableMap<String, ZoneRules> getVersions(String zoneId) {
        Objects.requireNonNull(zoneId, "zoneId");
        return getProvider(zoneId).provideVersions(zoneId);
    }

    /**
     * Gets the provider for the zone ID.
     *
     * @param zoneId  the zone region ID as used by {@code ZoneId}, not null
     * @return the provider, not null
     * @throws ZoneRulesException if the zone ID is unknown
     */
    private static ZoneRulesProvider getProvider(String zoneId) {
        ZoneRulesProvider provider = ZONES.get(zoneId);
        if (provider == null) {
            if (ZONES.isEmpty()) {
                throw new ZoneRulesException("No time-zone data files registered");
            }
            throw new ZoneRulesException("Unknown time-zone ID: " + zoneId);
        }
        return provider;
    }

    //-------------------------------------------------------------------------
    /**
     * Registers a zone rules provider.
     * <p>
     * This adds a new provider to those currently available.
     * A provider supplies rules for one or more zone IDs.
     * A provider cannot be registered if it supplies a zone ID that has already been
     * registered. See the notes on time-zone IDs in {@link ZoneId}, especially
     * the section on using the concept of a "group" to make IDs unique.
     * <p>
     * To ensure the integrity of time-zones already created, there is no way
     * to deregister providers.
     *
     * @param provider  the provider to register, not null
     * @throws ZoneRulesException if a region is already registered
     */
    public static void registerProvider(ZoneRulesProvider provider) {
        Objects.requireNonNull(provider, "provider");
        registerProvider0(provider);
        PROVIDERS.add(provider);
    }

    /**
     * Registers the provider.
     *
     * @param provider  the provider to register, not null
     * @throws ZoneRulesException if unable to complete the registration
     */
    private static void registerProvider0(ZoneRulesProvider provider) {
        for (String zoneId : provider.provideZoneIds()) {
            Objects.requireNonNull(zoneId, "zoneId");
            ZoneRulesProvider old = ZONES.putIfAbsent(zoneId, provider.provideBind(zoneId));
            if (old != null) {
                throw new ZoneRulesException(
                    "Unable to register zone as one already registered with that ID: " + zoneId +
                    ", currently loading from provider: " + provider);
            }
        }
    }

    //-------------------------------------------------------------------------
    /**
     * Refreshes the rules from the underlying data provider.
     * <p>
     * This method is an extension point that allows providers to refresh their
     * rules dynamically at a time of the applications choosing.
     * After calling this method, the offset stored in any {@link ZonedDateTime}
     * may be invalid for the zone ID.
     * <p>
     * Dynamic behavior is entirely optional and most providers, including the
     * default provider, do not support it.
     *
     * @return true if the rules were updated
     * @throws ZoneRulesException if an error occurs during the refresh
     */
    public static boolean refresh() {
        boolean changed = false;
        for (ZoneRulesProvider provider : PROVIDERS) {
            changed |= provider.provideRefresh();
        }
        return changed;
    }

    //-----------------------------------------------------------------------
    /**
     * Constructor.
     */
    protected ZoneRulesProvider() {
    }

    //-----------------------------------------------------------------------
    /**
     * SPI method to get the available zone IDs.
     * <p>
     * This obtains the IDs that this {@code ZoneRulesProvider} provides.
     * A provider should provide data for at least one region.
     * <p>
     * The returned regions remain available and valid for the lifetime of the application.
     * A dynamic provider may increase the set of regions as more data becomes available.
     *
     * @return the unmodifiable set of region IDs being provided, not null
     */
    protected abstract Set<String> provideZoneIds();

    /**
     * SPI method to bind to the specified zone ID.
     * <p>
     * {@code ZoneRulesProvider} has a lookup from zone ID to provider.
     * This method is used when building that lookup, allowing providers
     * to insert a derived provider that is precisely tuned to the zone ID.
     * This replaces two hash map lookups by one, enhancing performance.
     * <p>
     * This optimization is optional. Returning {@code this} is acceptable.
     * <p>
     * This implementation creates a bound provider that caches the
     * rules from the underlying provider. The request to version history
     * is forward on to the underlying. This is suitable for providers that
     * cannot change their contents during the lifetime of the JVM.
     *
     * @param zoneId  the zone region ID as used by {@code ZoneId}, not null
     * @return the resolved provider for the ID, not null
     * @throws DateTimeException if there is no provider for the specified group
     */
    protected ZoneRulesProvider provideBind(String zoneId) {
        return new BoundProvider(this, zoneId);
    }

    /**
     * SPI method to get the rules for the zone ID.
     * <p>
     * This loads the rules for the region and version specified.
     * The version may be null to indicate the "latest" version.
     *
     * @param regionId  the time-zone region ID, not null
     * @return the rules, not null
     * @throws DateTimeException if rules cannot be obtained
     */
    protected abstract ZoneRules provideRules(String regionId);

    /**
     * SPI method to get the history of rules for the zone ID.
     * <p>
     * This returns a map of historical rules keyed by a version string.
     * The exact meaning and format of the version is provider specific.
     * The version must follow lexicographical order, thus the returned map will
     * be order from the oldest known rules to the newest available rules.
     * The default 'TZDB' group uses version numbering consisting of the year
     * followed by a letter, such as '2009e' or '2012f'.
     * <p>
     * Implementations must provide a result for each valid zone ID, however
     * they do not have to provide a history of rules.
     * Thus the map will always contain one element, and will only contain more
     * than one element if historical rule information is available.
     * <p>
     * The returned versions remain available and valid for the lifetime of the application.
     * A dynamic provider may increase the set of versions as more data becomes available.
     *
     * @param zoneId  the zone region ID as used by {@code ZoneId}, not null
     * @return a modifiable copy of the history of the rules for the ID, sorted
     *  from oldest to newest, not null
     * @throws ZoneRulesException if the zone ID is unknown
     */
    protected abstract NavigableMap<String, ZoneRules> provideVersions(String zoneId);

    /**
     * SPI method to refresh the rules from the underlying data provider.
     * <p>
     * This method provides the opportunity for a provider to dynamically
     * recheck the underlying data provider to find the latest rules.
     * This could be used to load new rules without stopping the JVM.
     * Dynamic behavior is entirely optional and most providers do not support it.
     * <p>
     * This implementation returns false.
     *
     * @return true if the rules were updated
     * @throws DateTimeException if an error occurs during the refresh
     */
    protected boolean provideRefresh() {
        return false;
    }

    //-------------------------------------------------------------------------
    /**
     * A provider bound to a single zone ID.
     */
    private static class BoundProvider extends ZoneRulesProvider {
        private final ZoneRulesProvider provider;
        private final String zoneId;
        private final ZoneRules rules;

        private BoundProvider(ZoneRulesProvider provider, String zoneId) {
            this.provider = provider;
            this.zoneId = zoneId;
            this.rules = provider.provideRules(zoneId);
        }

        @Override
        protected Set<String> provideZoneIds() {
            return new HashSet<>(Collections.singleton(zoneId));
        }

        @Override
        protected ZoneRules provideRules(String regionId) {
            return rules;
        }

        @Override
        protected NavigableMap<String, ZoneRules> provideVersions(String zoneId) {
            return provider.provideVersions(zoneId);
        }

        @Override
        public String toString() {
            return zoneId;
        }
    }

}
