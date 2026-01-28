// This is in addition to Open Opus import, which imports Genre


CREATE CONSTRAINT instrument_id IF NOT EXISTS
FOR (i:Instrument) REQUIRE i.id IS UNIQUE;

