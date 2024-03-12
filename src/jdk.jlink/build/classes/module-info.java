module jdk.jlink_build_runlink {
    requires jdk.jlink;

    provides jdk.tools.jlink.plugin.Plugin with build.tools.runtimelink.CreateLinkableRuntimePlugin;
}
