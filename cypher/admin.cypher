// Useful admin commands

// Delete all propositions
MATCH (p:Proposition)
DETACH DELETE p;

// Delete Entity nodes that don't have the Reference label
MATCH (e:__Entity__)
  WHERE NOT e:Reference
DETACH DELETE e;



// DANGER ZONE

// Clear the database. You were WARNED!!
MATCH (n)
DETACH DELETE n;