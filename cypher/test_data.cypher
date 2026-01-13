
CREATE (u:User:__Entity__ {
id:'alice_id',
name:'Alice',
email:'alice@nowhere.com',
displayName:'Alice',
username:'alice',
currentContextName:'alice_default'
})
RETURN u;


MATCH (u:User {id:'alice_id'}) DETACH DELETE u;