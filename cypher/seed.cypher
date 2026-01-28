UNWIND ['Baroque','Classical','Romantic','20th-century','Contemporary'] AS name
MERGE (:Period {name:name});

MATCH (c:Composer)
  WHERE c.birthYear IS NOT NULL AND c.deathYear IS NOT NULL
WITH c, toInteger((c.birthYear + c.deathYear) / 2.0) AS mid
WITH c,
     CASE
       WHEN mid < 1400 THEN 'Medieval'
       WHEN mid < 1600 THEN 'Renaissance'
       WHEN mid < 1750 THEN 'Baroque'
       WHEN mid < 1820 THEN 'Classical'
       WHEN mid < 1890 THEN 'Romantic'
       WHEN mid < 1945 THEN 'Modern'
       WHEN mid < 1975 THEN 'Post-war'
       ELSE 'Contemporary'
       END
MATCH (p:Period {name: period})
MERGE (c)-[r:IN_PERIOD]->(p)
  ON CREATE SET r.method='heuristic', r.confidence=0.35
  ON MATCH  SET r.method=coalesce(r.method,'heuristic');


MATCH (w:Work)
WHERE w.subtitle IS NOT NULL AND toLower(w.subtitle) STARTS WITH 'for '
WITH w, trim(substring(w.subtitle, 4)) AS raw0
// unify separators
WITH w,
toLower(
replace(
replace(
replace(raw0, ';', ','),
' and ', ', '),
' & ', ', ')
) AS raw
UNWIND [x IN split(raw, ',') | trim(x)] AS part0
WITH w, part0
WHERE part0 <> ''
WITH w,
CASE
// ensembles
WHEN part0 = 'orchestra' THEN {id:'orchestra', name:'Orchestra'}
WHEN part0 = 'chamber orchestra' THEN {id:'chamber-orchestra', name:'Chamber orchestra'}
WHEN part0 = 'string quartet' THEN {id:'string-quartet', name:'String quartet'}
WHEN part0 IN ['strings','string orchestra'] THEN {id:'strings', name:'Strings'}

// families
WHEN part0 IN ['winds','woodwinds'] THEN {id:'woodwinds', name:'Woodwinds'}
WHEN part0 = 'brass' THEN {id:'brass', name:'Brass'}
WHEN part0 = 'percussion' THEN {id:'percussion', name:'Percussion'}

// keyboards etc
WHEN part0 IN ['piano','pianos'] THEN {id:'piano', name:'Piano'}
WHEN part0 = 'organ' THEN {id:'organ', name:'Organ'}
WHEN part0 = 'harpsichord' THEN {id:'harpsichord', name:'Harpsichord'}

// strings
WHEN part0 = 'violin' THEN {id:'violin', name:'Violin'}
WHEN part0 = 'viola' THEN {id:'viola', name:'Viola'}
WHEN part0 IN ['cello','violoncello'] THEN {id:'cello', name:'Cello'}
WHEN part0 IN ['double bass','contrabass'] THEN {id:'double-bass', name:'Double bass'}
WHEN part0 = 'guitar' THEN {id:'guitar', name:'Guitar'}
WHEN part0 = 'harp' THEN {id:'harp', name:'Harp'}

// winds
WHEN part0 = 'flute' THEN {id:'flute', name:'Flute'}
WHEN part0 = 'oboe' THEN {id:'oboe', name:'Oboe'}
WHEN part0 = 'clarinet' THEN {id:'clarinet', name:'Clarinet'}
WHEN part0 = 'bassoon' THEN {id:'bassoon', name:'Bassoon'}

// brass instruments
WHEN part0 = 'horn' THEN {id:'horn', name:'Horn'}
WHEN part0 = 'trumpet' THEN {id:'trumpet', name:'Trumpet'}
WHEN part0 = 'trombone' THEN {id:'trombone', name:'Trombone'}
WHEN part0 = 'tuba' THEN {id:'tuba', name:'Tuba'}

// voices (collapse to “Voice” plus optional typed voices later)
WHEN part0 IN ['voice','voices','soprano','mezzo-soprano','alto','tenor','baritone','bass','chorus','choir'] THEN
{id:'voice', name:'Voice'}

ELSE null
END AS inst
WHERE inst IS NOT NULL
MERGE (i:Instrument {id: inst.id})
ON CREATE SET i.name = inst.name
MERGE (w)-[r:SCORED_FOR]->(i)
ON CREATE SET r.source='subtitle', r.method='rule-map', r.confidence=0.8;

MATCH (w:Work)-[:OF_GENRE]->(g:Genre)
WHERE NOT (w)-[:SCORED_FOR]->(:Instrument)
WITH w, g.name AS gname
WITH w,
CASE gname
WHEN 'Keyboard'   THEN [{id:'piano',             name:'Piano',            conf:0.4}]
WHEN 'Orchestral' THEN [{id:'orchestra',         name:'Orchestra',        conf:0.4}]
WHEN 'Chamber'    THEN [{id:'chamber-ensemble',  name:'Chamber ensemble', conf:0.4}]
WHEN 'Vocal'      THEN [{id:'voice',             name:'Voice',            conf:0.4}]
WHEN 'Stage'      THEN [
{id:'voice',     name:'Voice',     conf:0.4},
{id:'orchestra', name:'Orchestra', conf:0.4}
]
ELSE []
END AS insts
UNWIND insts AS inst
MERGE (i:Instrument {id:inst.id})
ON CREATE SET i.name = inst.name
MERGE (w)-[r:SCORED_FOR]->(i)
ON CREATE SET r.source='genre', r.method='inferred', r.confidence=inst.conf;


MATCH (w:Work)
WHERE NOT (w)-[:SCORED_FOR]->(:Instrument)
WITH w, toLower(coalesce(w.title,'') + ' ' + coalesce(w.description,'')) AS t
UNWIND [
{pat:' for piano', id:'piano', name:'Piano'},
{pat:' for organ', id:'organ', name:'Organ'},
{pat:' string quartet', id:'string-quartet', name:'String quartet'},
{pat:' violin concerto', id:'violin', name:'Violin'},
{pat:' cello concerto', id:'cello', name:'Cello'}
] AS rule
WITH w, t, rule
WHERE t CONTAINS rule.pat
MERGE (i:Instrument {id: rule.id})
ON CREATE SET i.name = rule.name
MERGE (w)-[r:SCORED_FOR]->(i)
ON CREATE SET r.source='title+description', r.method='contains', r.confidence=0.55;


