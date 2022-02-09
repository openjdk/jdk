#
# Register $@ .plist files with launchd service.
#
register_services ()
{
  for daemonPlistFilePath in "$@"; do
    daemonPlistFileName="${daemonPlistFilePath#/Library/LaunchDaemons/}";

    launchctl load "$daemonPlistFilePath"

    launchctl start "${daemonPlistFileName%.plist}"
  done
}


#
# Unregister $@ .plist files with launchd service.
#
unregister_services ()
{
  for daemonPlistFilePath in "$@"; do
    daemonPlistFileName="${daemonPlistFilePath#/Library/LaunchDaemons/}";

    launchctl stop "${daemonPlistFileName%.plist}"

    launchctl unload "$daemonPlistFilePath"
  done
}
