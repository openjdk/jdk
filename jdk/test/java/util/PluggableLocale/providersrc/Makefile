#
#
#

DESTDIR=..
FOODIR=foo-contents
BARDIR=bar-contents

all: $(DESTDIR)/fooprovider.jar $(DESTDIR)/barprovider.jar

FOOSERVICES = \
        java.text.spi.BreakIteratorProvider \
        java.text.spi.CollatorProvider \
        java.text.spi.DateFormatProvider \
	java.text.spi.DateFormatSymbolsProvider \
	java.text.spi.DecimalFormatSymbolsProvider \
	java.text.spi.NumberFormatProvider 

BARSERVICES = \
	java.util.spi.CurrencyNameProvider \
	java.util.spi.TimeZoneNameProvider \
	java.util.spi.LocaleNameProvider 

FOOFILES_JAVA = \
    BreakIteratorProviderImpl.java \
    CollatorProviderImpl.java \
    DateFormatProviderImpl.java \
    DateFormatSymbolsProviderImpl.java \
    DecimalFormatSymbolsProviderImpl.java \
    NumberFormatProviderImpl.java \
    FooDateFormat.java \
    FooNumberFormat.java \
    Utils.java

BARFILES_JAVA = \
    CurrencyNameProviderImpl.java \
    TimeZoneNameProviderImpl.java \
    LocaleNameProviderImpl.java \
    Utils.java

BARFILES_PROPERTIES = \
    LocaleNames.properties \
    LocaleNames_ja.properties \
    LocaleNames_ja_JP_osaka.properties \
    LocaleNames_ja_JP_kyoto.properties \
    LocaleNames_xx.properties

$(DESTDIR)/fooprovider.jar: $(FOOSERVICES) $(FOOFILES_JAVA)
	rm -rf $(FOODIR)
	mkdir -p $(FOODIR)
	mkdir -p $(FOODIR)/META-INF
	mkdir -p $(FOODIR)/META-INF/services
	$(BINDIR)/javac -d $(FOODIR) $(FOOFILES_JAVA)
	cp $(FOOSERVICES) $(FOODIR)/META-INF/services
	rm -f $(DESTDIR)/fooprovider.jar
	$(BINDIR)/jar  cvf $(DESTDIR)/fooprovider.jar -C $(FOODIR) .

$(DESTDIR)/barprovider.jar: $(BARSERVICES) $(BARFILES_JAVA) $(BARFILES_PROPERTIES)
	rm -rf $(BARDIR)
	mkdir -p $(BARDIR)
	mkdir -p $(BARDIR)/META-INF
	mkdir -p $(BARDIR)/META-INF/services
	$(BINDIR)/javac -d $(BARDIR) $(BARFILES_JAVA)
	cp $(BARSERVICES) $(BARDIR)/META-INF/services
	cp $(BARFILES_PROPERTIES) $(BARDIR)/com/bar
	rm -f $(DESTDIR)/barprovider.jar
	$(BINDIR)/jar  cvf $(DESTDIR)/barprovider.jar -C $(BARDIR) .
