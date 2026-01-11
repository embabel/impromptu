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