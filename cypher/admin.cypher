// Useful admin commands

// Delete all propositions
MATCH (p:Proposition)
DETACH DELETE p;

// Delete all user data (Users and their Propositions)
MATCH (u:User)
DETACH DELETE u;
MATCH (p:Proposition)
DETACH DELETE p;

// Delete Entity nodes that don't have the Reference label
MATCH (e:__Entity__)
  WHERE NOT e:Reference
DETACH DELETE e;

// Delete open opus data
MATCH (n)
WHERE n.primarySource = 'openopus'
DETACH DELETE n;

// DANGER ZONE

// Clear the database. You were WARNED!!
MATCH (n)
DETACH DELETE n;



MATCH (w:Work)
WITH count(w) AS total
MATCH (w2:Work)
  WHERE (w2)-[:SCORED_FOR]->(:Instrument)
WITH total, count(DISTINCT w2) AS tagged
RETURN total,
       tagged,
       round(100.0 * tagged / total, 1) AS pct;

