// Create Alice user
CREATE (u:User:__Entity__ {
id:'alice_id',
name:'Alice',
email:'alice@nowhere.com',
displayName:'Alice',
username:'alice',
currentContextName:'alice_default'
})
RETURN u;


// Find Alice's propositions
match (p:Proposition { contextId: 'alice_default'}) return p;

// Delete Alice user
MATCH (u:User {id:'alice_id'}) DETACH DELETE u;

// Delete all propositions associated with Alice's context
MATCH (p:Proposition {contextId:'alice_default'}) DETACH DELETE p;
