// This is in addition to Open Opus import, which imports Genre

CREATE CONSTRAINT family_id IF NOT EXISTS
FOR (f:Family) REQUIRE f.id IS UNIQUE;

CREATE CONSTRAINT instrument_id IF NOT EXISTS
FOR (i:Instrument) REQUIRE i.id IS UNIQUE;

// Instrument Families
UNWIND [
{id:'strings',     name:'Strings',     description:'Instruments that produce sound through vibrating strings'},
{id:'woodwinds',   name:'Woodwinds',   description:'Wind instruments typically made of wood or producing sound via a reed'},
{id:'brass',       name:'Brass',       description:'Wind instruments made of brass with sound produced by lip vibration'},
{id:'percussion',  name:'Percussion',  description:'Instruments that produce sound when struck, shaken, or scraped'},
{id:'keyboard',    name:'Keyboard',    description:'Instruments played using a keyboard'},
{id:'voice',       name:'Voice',       description:'The human voice as a musical instrument'}
] AS f
MERGE (fam:Family:Reference:__Entity__ {id: f.id})
SET fam.name = f.name,
    fam.description = f.description;

// Instruments
UNWIND [
// Strings
{id:'violin',        name:'Violin',        family:'strings', description:'The highest-pitched bowed string instrument'},
{id:'viola',         name:'Viola',         family:'strings', description:'A bowed string instrument slightly larger than the violin'},
{id:'cello',         name:'Cello',         family:'strings', description:'A large bowed string instrument with a rich, deep tone'},
{id:'double-bass',   name:'Double Bass',   family:'strings', description:'The largest and lowest-pitched bowed string instrument'},
{id:'harp',          name:'Harp',          family:'strings', description:'A plucked string instrument with a triangular frame'},
{id:'guitar',        name:'Guitar',        family:'strings', description:'A plucked string instrument with a fretted fingerboard'},
{id:'lute',          name:'Lute',          family:'strings', description:'A plucked string instrument with a rounded back, popular in Renaissance music'},
{id:'mandolin',      name:'Mandolin',      family:'strings', description:'A small plucked string instrument with paired strings'},
// Woodwinds
{id:'flute',         name:'Flute',         family:'woodwinds', description:'A high-pitched wind instrument played by blowing across an embouchure hole'},
{id:'piccolo',       name:'Piccolo',       family:'woodwinds', description:'A small flute sounding an octave higher than the standard flute'},
{id:'oboe',          name:'Oboe',          family:'woodwinds', description:'A double-reed woodwind with a distinctive penetrating tone'},
{id:'english-horn',  name:'English Horn',  family:'woodwinds', description:'A double-reed woodwind, lower in pitch than the oboe'},
{id:'clarinet',      name:'Clarinet',      family:'woodwinds', description:'A single-reed woodwind with a wide range and warm tone'},
{id:'bass-clarinet', name:'Bass Clarinet', family:'woodwinds', description:'A larger clarinet sounding an octave lower than the standard clarinet'},
{id:'bassoon',       name:'Bassoon',       family:'woodwinds', description:'A double-reed woodwind with a distinctive deep tone'},
{id:'contrabassoon', name:'Contrabassoon', family:'woodwinds', description:'The largest and lowest-pitched woodwind, an octave below the bassoon'},
{id:'recorder',      name:'Recorder',      family:'woodwinds', description:'A fipple flute popular in early music'},
{id:'saxophone',     name:'Saxophone',     family:'woodwinds', description:'A single-reed instrument with a brass body'},
// Brass
{id:'trumpet',       name:'Trumpet',       family:'brass', description:'A high-pitched brass instrument with a brilliant tone'},
{id:'french-horn',   name:'French Horn',   family:'brass', description:'A brass instrument with a mellow, rich tone and coiled tubing'},
{id:'trombone',      name:'Trombone',      family:'brass', description:'A brass instrument with a slide mechanism for changing pitch'},
{id:'tuba',          name:'Tuba',          family:'brass', description:'The largest and lowest-pitched brass instrument'},
{id:'cornet',        name:'Cornet',        family:'brass', description:'A brass instrument similar to the trumpet but with a more mellow tone'},
// Percussion
{id:'timpani',       name:'Timpani',       family:'percussion', description:'Large kettle drums that can be tuned to specific pitches'},
{id:'snare-drum',    name:'Snare Drum',    family:'percussion', description:'A drum with metal wires stretched across the bottom head'},
{id:'bass-drum',     name:'Bass Drum',     family:'percussion', description:'A large drum that produces a deep, low sound'},
{id:'cymbals',       name:'Cymbals',       family:'percussion', description:'Circular metal plates that produce a shimmering sound when struck'},
{id:'xylophone',     name:'Xylophone',     family:'percussion', description:'A tuned percussion instrument with wooden bars'},
{id:'marimba',       name:'Marimba',       family:'percussion', description:'A large xylophone with resonators beneath the wooden bars'},
{id:'vibraphone',    name:'Vibraphone',    family:'percussion', description:'A tuned percussion instrument with metal bars and motor-driven resonators'},
{id:'glockenspiel',  name:'Glockenspiel',  family:'percussion', description:'A tuned percussion instrument with metal bars producing a bright, bell-like tone'},
{id:'triangle',      name:'Triangle',      family:'percussion', description:'A metal bar bent into a triangle shape, struck with a metal beater'},
{id:'tambourine',    name:'Tambourine',    family:'percussion', description:'A small drum with metal jingles in the frame'},
{id:'celesta',       name:'Celesta',       family:'percussion', description:'A keyboard instrument that strikes metal plates to produce a bell-like tone'},
// Keyboard
{id:'piano',         name:'Piano',         family:'keyboard', description:'A keyboard instrument with hammers striking strings'},
{id:'organ',         name:'Organ',         family:'keyboard', description:'A keyboard instrument producing sound through pipes or electronic means'},
{id:'harpsichord',   name:'Harpsichord',   family:'keyboard', description:'A keyboard instrument with strings plucked by quills'},
{id:'clavichord',    name:'Clavichord',    family:'keyboard', description:'A soft-toned keyboard instrument with strings struck by tangents'},
// Voice
{id:'soprano',       name:'Soprano',       family:'voice', description:'The highest female or boy singing voice'},
{id:'mezzo-soprano', name:'Mezzo-soprano', family:'voice', description:'A female voice between soprano and contralto'},
{id:'contralto',     name:'Contralto',     family:'voice', description:'The lowest female singing voice'},
{id:'countertenor',  name:'Countertenor',  family:'voice', description:'A male voice using falsetto to sing in the alto range'},
{id:'tenor',         name:'Tenor',         family:'voice', description:'The highest natural adult male singing voice'},
{id:'baritone',      name:'Baritone',      family:'voice', description:'A male voice between tenor and bass'},
{id:'bass',          name:'Bass',          family:'voice', description:'The lowest male singing voice'}
] AS i
MERGE (inst:Instrument:Reference:__Entity__ {id: i.id})
SET inst.name = i.name,
    inst.description = i.description
WITH inst, i
MATCH (fam:Family {id: i.family})
MERGE (inst)-[:OF_FAMILY]->(fam);

CREATE CONSTRAINT technique_id IF NOT EXISTS
FOR (t:Technique) REQUIRE t.id IS UNIQUE;

CREATE CONSTRAINT ensemble_id IF NOT EXISTS
FOR (e:Ensemble) REQUIRE e.id IS UNIQUE;

// Ensembles
UNWIND [
// Orchestral
{id:'symphony-orchestra',   name:'Symphony Orchestra',   description:'A large ensemble typically with strings, woodwinds, brass, and percussion'},
{id:'chamber-orchestra',    name:'Chamber Orchestra',    description:'A smaller orchestra, typically 15-50 players'},
{id:'string-orchestra',     name:'String Orchestra',     description:'An orchestra consisting only of string instruments'},
{id:'baroque-orchestra',    name:'Baroque Orchestra',    description:'An orchestra using period instruments for Baroque music'},
// Chamber - Strings
{id:'string-quartet',       name:'String Quartet',       description:'Two violins, viola, and cello'},
{id:'string-trio',          name:'String Trio',          description:'Typically violin, viola, and cello'},
{id:'string-quintet',       name:'String Quintet',       description:'String quartet plus an additional viola or cello'},
{id:'string-sextet',        name:'String Sextet',        description:'Two violins, two violas, and two cellos'},
// Chamber - Piano
{id:'piano-trio',           name:'Piano Trio',           description:'Piano, violin, and cello'},
{id:'piano-quartet',        name:'Piano Quartet',        description:'Piano, violin, viola, and cello'},
{id:'piano-quintet',        name:'Piano Quintet',        description:'Piano with string quartet'},
// Chamber - Wind
{id:'wind-quintet',         name:'Wind Quintet',         description:'Flute, oboe, clarinet, bassoon, and French horn'},
{id:'wind-octet',           name:'Wind Octet',           description:'Pairs of oboes, clarinets, bassoons, and horns'},
{id:'brass-quintet',        name:'Brass Quintet',        description:'Two trumpets, French horn, trombone, and tuba'},
{id:'clarinet-quintet',     name:'Clarinet Quintet',     description:'Clarinet with string quartet'},
// Vocal
{id:'choir',                name:'Choir',                description:'A vocal ensemble, typically SATB'},
{id:'chamber-choir',        name:'Chamber Choir',        description:'A small vocal ensemble, typically 12-40 singers'},
{id:'opera-company',        name:'Opera Company',        description:'A theatrical company performing opera'},
{id:'a-cappella',           name:'A Cappella Ensemble',  description:'A vocal group performing without instrumental accompaniment'},
// Other
{id:'wind-ensemble',        name:'Wind Ensemble',        description:'A large ensemble of wind and percussion instruments'},
{id:'brass-band',           name:'Brass Band',           description:'An ensemble of brass instruments and percussion'},
{id:'solo',                 name:'Solo',                 description:'A single performer'},
{id:'duo',                  name:'Duo',                  description:'Two performers'},
{id:'consort',              name:'Consort',              description:'A Renaissance or early Baroque ensemble of instruments from the same family'}
] AS e
MERGE (ens:Ensemble:Reference:__Entity__ {id: e.id})
SET ens.name = e.name,
    ens.description = e.description;

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

MATCH (n:Reference) SET n:__Entity__;
