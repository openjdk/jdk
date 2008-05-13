/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved.
 * SUN PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

package sun.tracing;

import java.lang.reflect.Method;
import java.util.logging.Logger;

import com.sun.tracing.ProviderFactory;
import com.sun.tracing.Provider;

/**
 * Factory class to create tracing Providers.
 *
 * This factory will create tracing instances that do nothing.
 * It is used when no tracing is desired, but Provider instances still
 * must be generated so that tracing calls in the application continue to
 * run.
 *
 * @since 1.7
 */
public class NullProviderFactory extends ProviderFactory {

    /**
     * Creates and returns a Null provider.
     *
     * See comments at {@code ProviderSkeleton.createProvider()} for more
     * details.
     *
     * @return a provider whose probe trigger are no-ops.
     */
    public <T extends Provider> T createProvider(Class<T> cls) {
        NullProvider provider = new NullProvider(cls);
        try {
            provider.init();
        } catch (Exception e) {
            // Probably a permission problem (can't get declared members)
            Logger.getAnonymousLogger().warning(
                "Could not initialize tracing provider: " + e.getMessage());
        }
        return provider.newProxyInstance();
    }
}

class NullProvider extends ProviderSkeleton {

    NullProvider(Class<? extends Provider> type) {
        super(type);
    }

    protected ProbeSkeleton createProbe(Method m) {
        return new NullProbe(m.getParameterTypes());
    }
}

class NullProbe extends ProbeSkeleton {

    public NullProbe(Class<?>[] parameters) {
        super(parameters);
    }

    public boolean isEnabled() {
        return false;
    }

    public void uncheckedTrigger(Object[] args) {
    }
}

