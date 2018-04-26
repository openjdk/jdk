#
# matching the following output specified as a pattern that verifies
# that the numerical values conform to a specific pattern, rather than
# specific values.
#
# NGCMN    NGCMX     NGC     S0C   S1C       EC      OGCMN      OGCMX       OGC         OC       MCMN     MCMX      MC     CCSMN    CCSMX     CCSC    YGC    FGC   CGC
#  4096.0 657408.0   8192.0  512.0  512.0   3072.0     6144.0  1312768.0     6144.0     6144.0    512.0 132096.0   5120.0    512.0 131072.0    512.0      1     0    0


BEGIN	{
	    headerlines=0; datalines=0; totallines=0
	}

/^ NGCMN    NGCMX     NGC     S0C   S1C       EC      OGCMN      OGCMX       OGC         OC       MCMN     MCMX      MC     CCSMN    CCSMX     CCSC    YGC    FGC   CGC $/	{
	    headerlines++;
	}

/^[ ]*[0-9]+\.[0-9]+[ ]*[0-9]+\.[0-9]+[ ]*[0-9]+\.[0-9]+[ ]*[0-9]+\.[0-9]+[ ]*[0-9]+\.[0-9]+[ ]*[0-9]+\.[0-9]+[ ]*[0-9]+\.[0-9]+[ ]*[0-9]+\.[0-9]+[ ]*[0-9]+\.[0-9]+[ ]*[0-9]+\.[0-9]+[ ]*[0-9]+\.[0-9]+[ ]*[0-9]+\.[0-9]+[ ]*[0-9]+\.[0-9]+[ ]*[0-9]+\.[0-9]+[ ]*[0-9]+\.[0-9]+[ ]*[0-9]+\.[0-9]+[ ]*[0-9]+[ ]*[0-9]+[ ]*(-|[0-9]+)$/	{
	    datalines++;
	}

	{ totallines++; print $0 }

END	{
	    if ((headerlines == 1) && (datalines == 1)) {
	        exit 0
	    }
	    else {
	        exit 1
	    }
	}
