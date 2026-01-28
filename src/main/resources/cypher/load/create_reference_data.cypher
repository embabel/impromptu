// This is in addition to Open Opus import, which imports Genre


CREATE CONSTRAINT instrument_id IF NOT EXISTS
FOR (i:Instrument) REQUIRE i.id IS UNIQUE;

CREATE CONSTRAINT technique_id IF NOT EXISTS
FOR (t:Technique) REQUIRE t.id IS UNIQUE;

// Techniques / compositional methods
UNWIND [
{
id:'serialism',
name:'Serialism',
aka:['dodecaphony','total serialism'],
description:'A compositional method based on ordered series of musical elements, most commonly pitch, to avoid tonal hierarchy.'
},
{
id:'minimalism',
name:'Minimalism',
aka:[],
description:'A style based on repetition, gradual process, steady pulse, and limited musical materials.'
},
{
id:'phase-shifting',
name:'Phase shifting',
aka:[],
description:'A process in which identical musical patterns gradually move out of sync, creating evolving rhythmic and harmonic relationships.'
},
{
id:'additive-process',
name:'Additive process',
aka:['additive rhythm'],
description:'A technique where musical material is built up incrementally by adding notes, rhythms, or layers over time.'
},
{
id:'aleatoric',
name:'Aleatoric technique',
aka:['chance music'],
description:'A technique that introduces elements of chance or performer choice into the composition or performance.'
},
{
id:'indeterminacy',
name:'Indeterminacy',
aka:[],
description:'A compositional approach in which aspects of the music are left unspecified, producing variable outcomes in performance.'
},
{
id:'extended-techniques',
name:'Extended techniques',
aka:[],
description:'Non-traditional ways of playing instruments to produce unusual sounds or timbres.'
},
{
id:'prepared-piano',
name:'Prepared piano',
aka:[],
description:'A technique in which objects are placed on or between piano strings to alter the instrument’s sound.'
},
{
id:'musique-concrete',
name:'Musique concrète',
aka:[],
description:'A form of composition using recorded natural or industrial sounds as primary musical material.'
},
{
id:'electronic',
name:'Electronic technique',
aka:[],
description:'The use of electronic sound generation, processing, or manipulation as a core compositional element.'
},
{
id:'spectralism',
name:'Spectral technique',
aka:['spectral music'],
description:'A technique based on the acoustic properties of sound, especially the overtone spectrum, as a basis for harmony and form.'
},
{
id:'microtonality',
name:'Microtonality',
aka:[],
description:'The use of pitch intervals smaller than the conventional semitone of Western equal temperament.'
},
{
id:'polyrhythm',
name:'Polyrhythm',
aka:[],
description:'The simultaneous use of two or more conflicting rhythmic patterns.'
},
{
id:'polymeter',
name:'Polymeter',
aka:[],
description:'The simultaneous use of multiple metrical structures, typically sharing a common pulse.'
},
{
id:'isorhythm',
name:'Isorhythm',
aka:[],
description:'A medieval technique in which a repeating rhythmic pattern is applied to a sequence of changing pitches.'
},
{
id:'cantus-firmus',
name:'Cantus firmus',
aka:[],
description:'A pre-existing melody used as the structural foundation of a polyphonic composition.'
},
{
id:'counterpoint',
name:'Counterpoint',
aka:[],
description:'The art of combining independent melodic lines according to established harmonic and voice-leading principles.'
},
{
id:'ostinato',
name:'Ostinato',
aka:[],
description:'A persistently repeated musical pattern, typically in the bass or accompaniment.'
},
{
id:'ground-bass',
name:'Ground bass',
aka:['basso ostinato'],
description:'A form built over a continuously repeating bass line with varying material above it.'
},
{
id:'theme-and-variations',
name:'Theme and variations technique',
aka:['variations'],
description:'A form in which a theme is followed by a series of modified restatements.'
},
{
id:'sonata-principle',
name:'Sonata principle',
aka:['sonata form'],
description:'A formal principle based on exposition, development, and recapitulation of contrasting thematic material.'
},
{
id:'through-composed',
name:'Through-composed',
aka:[],
description:'A structure in which the music is continuously developed without repeated sections.'
},
{
id:'leitmotif',
name:'Leitmotif',
aka:[],
description:'A recurring musical idea associated with a character, object, or concept, especially in dramatic works.'
},
{
id:'modalism',
name:'Modal technique',
aka:['modalism'],
description:'The use of musical modes as an organizing principle instead of major–minor tonality.'
},
{
id:'polytonality',
name:'Polytonality',
aka:[],
description:'The simultaneous use of two or more tonal centers.'
},
{
id:'atonality',
name:'Atonality',
aka:[],
description:'Music that avoids establishing a tonal center or key.'
}
] AS t
MERGE (tech:Technique:Reference:__Entity__ {id: t.id})
SET tech.name        = t.name,
tech.aka         = t.aka,
tech.description = t.description;

CREATE CONSTRAINT nationality_id IF NOT
EXISTS
FOR (n:Nationality) REQUIRE n.id IS UNIQUE;

// Nationalities (composer / person level) with ISO-3166-1 alpha-2 country codes
UNWIND [
{id:'austrian',        name:'Austrian',        country:'Austria',        code:'AT'},
{id:'german',          name:'German',          country:'Germany',        code:'DE'},
{id:'french',          name:'French',          country:'France',         code:'FR'},
{id:'italian',         name:'Italian',         country:'Italy',          code:'IT'},
{id:'british',         name:'British',         country:'United Kingdom', code:'GB'},
{id:'english',         name:'English',         country:'England',        code:'GB'},
{id:'scottish',        name:'Scottish',        country:'Scotland',       code:'GB'},
{id:'irish',           name:'Irish',           country:'Ireland',        code:'IE'},
{id:'spanish',         name:'Spanish',         country:'Spain',          code:'ES'},
{id:'portuguese',      name:'Portuguese',      country:'Portugal',       code:'PT'},
{id:'dutch',           name:'Dutch',           country:'Netherlands',    code:'NL'},
{id:'belgian',         name:'Belgian',         country:'Belgium',        code:'BE'},
{id:'swiss',           name:'Swiss',           country:'Switzerland',    code:'CH'},
{id:'czech',           name:'Czech',           country:'Czech Republic', code:'CZ'},
{id:'slovak',          name:'Slovak',          country:'Slovakia',       code:'SK'},
{id:'hungarian',       name:'Hungarian',       country:'Hungary',        code:'HU'},
{id:'polish',          name:'Polish',          country:'Poland',         code:'PL'},
{id:'russian',         name:'Russian',         country:'Russia',         code:'RU'},
{id:'ukrainian',       name:'Ukrainian',       country:'Ukraine',        code:'UA'},
{id:'estonian',        name:'Estonian',        country:'Estonia',        code:'EE'},
{id:'latvian',         name:'Latvian',         country:'Latvia',         code:'LV'},
{id:'lithuanian',      name:'Lithuanian',      country:'Lithuania',      code:'LT'},
{id:'norwegian',       name:'Norwegian',       country:'Norway',         code:'NO'},
{id:'swedish',         name:'Swedish',         country:'Sweden',         code:'SE'},
{id:'danish',          name:'Danish',          country:'Denmark',        code:'DK'},
{id:'finnish',         name:'Finnish',         country:'Finland',        code:'FI'},
{id:'icelandic',       name:'Icelandic',       country:'Iceland',        code:'IS'},
{id:'american',        name:'American',        country:'United States',  code:'US'},
{id:'canadian',        name:'Canadian',        country:'Canada',         code:'CA'},
{id:'australian',      name:'Australian',      country:'Australia',     code:'AU'},
{id:'new-zealander',   name:'New Zealander',   country:'New Zealand',    code:'NZ'},
{id:'argentine',       name:'Argentine',       country:'Argentina',     code:'AR'},
{id:'brazilian',       name:'Brazilian',       country:'Brazil',         code:'BR'},
{id:'mexican',         name:'Mexican',         country:'Mexico',         code:'MX'},
{id:'japanese',        name:'Japanese',        country:'Japan',          code:'JP'},
{id:'chinese',         name:'Chinese',         country:'China',          code:'CN'},
{id:'korean',          name:'Korean',          country:'South Korea',    code:'KR'}
] AS n
MERGE (nat:Nationality:Reference:__Entity__ {id: n.id})
SET nat.name        = n.name,
nat.country     = n.country,
nat.countryCode = n.code;

// Composers and works don't have this
MATCH (n:Reference) SET n:__Entity__;
