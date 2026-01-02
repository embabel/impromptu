// Useful admin commands

// Delete all propositions
MATCH (p:Proposition)
DETACH DELETE p;


// Clear the database. You were WARNED!!
MATCH (n)
DETACH DELETE n;