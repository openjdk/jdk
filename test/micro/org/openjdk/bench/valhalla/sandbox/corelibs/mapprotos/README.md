# Prototype implementations of HashMaps using inline classes for the table entries.

   **NOTE: The implementations have NOT been optimized or tuned.**

## HashMap uses Open Addressing to store all entries in a table of inline classes
The hash of the key is used as the first index into the table. 
If there is a collision, double hashing (with a static offset) is used to probe subsequent locations for available storage.
The Robin Hood hashing variation on insertion is used to reduce worst case lookup times.
On key removal, the subsequent double-hashed entries are compressed into the entry being removed.

### HashMap Storage requirements
Typical storage usage for a table near its load factor is 22 bytes per entry.

Inserting entries into the HashMap may resize the table but otherwise does
not use any additional memory on each get or put.

## XHashMap stores the initial entry in a table of inline classes
The hash of the key is used as the first index into the table. 
If there is a collision, subsequent entries add the familiar link list of Nodes.
On key removal, direct entries in the table are replaced by the first linked node;
for Nodes in the link list, the Node is unlinked.

### XHashMap Storage requirements:
Typical storage usage for a table near its load factor is 32 bytes per entry.

## java.util.HashMap (the original)
HashMap uses a table of references to the initial Node entries, collisions are handled by 
linked Nodes.

### HashMap Storage requirements:
Typical storage usage for a table near its load factor is 37 bytes per entry.
