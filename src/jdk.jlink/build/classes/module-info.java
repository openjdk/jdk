module jdk.unsupported_jlink_runtime {
    requires jdk.jlink;

    uses jdk.tools.jlink.plugin.Plugin;

    provides jdk.tools.jlink.plugin.Plugin with build.tools.runtimelink.CreateLinkableRuntimePlugin;
}
