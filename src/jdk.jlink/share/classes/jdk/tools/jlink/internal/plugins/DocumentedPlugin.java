package jdk.tools.jlink.internal.plugins;

import jdk.tools.jlink.plugin.Plugin;

public abstract class DocumentedPlugin implements Plugin {

    private final String NAME;
    protected DocumentedPlugin(String name) {
        this.NAME = name;
    }

    @Override
    public String getName() {
        return this.NAME;
    }

    @Override
    public String getDescription() {
        return PluginsResourceBundle.getDescription(NAME);
    }

    @Override
    public String getUsage() {
        return PluginsResourceBundle.getUsage(NAME);
    };
}
