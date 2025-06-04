#
# Register $@ unit files with systemd service.
#
register_services ()
{
  for unit in "$@"; do
    local unit_name=`basename "$unit"`
    systemctl enable --now "$unit_name"
  done
}


#
# Unregister $@ unit files with systemd service.
#
unregister_services ()
{
  for unit in "$@"; do
    if file_belongs_to_single_package "$unit"; then
      local unit_name=`basename "$unit"`
      if systemctl list-units --full -all | grep -q "$unit_name"; then
        systemctl disable --now "$unit_name"
      fi
    fi
  done
}
