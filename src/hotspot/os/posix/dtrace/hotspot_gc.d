provider hotspot_gc {
  probe AllocObject__sample(char*, size_t, size_t);
}

#pragma D attributes Standard/Standard/Common provider hotspot_gc provider
#pragma D attributes Private/Private/Unknown provider hotspot_gc module
#pragma D attributes Private/Private/Unknown provider hotspot_gc function
#pragma D attributes Standard/Standard/Common provider hotspot_gc name
#pragma D attributes Evolving/Evolving/Common provider hotspot_gc args