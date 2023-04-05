# Verifying consistency settings and models in Cassandra

*By [Emilia Rieschel](https://github.com/rieschel),
[Casper](https://github.com/)
and [Marcus](https://github.com/)*.

Cassandra is an AP system, meaning that they deprioritize consistency in favour for availability and partition tolerance. 

# What is Cassandra?
*This part should fulfill the criteria: "Explanation of the targeted concurrency model"*

## Short overview
Regarding the CAP theorem, Cassandra is an AP system. This means it prioritizes availability and partition tolerance over consistency.

![Cassandra CAP](cap.png)

## Architecture (focus on Dynamo)
*What we mention in this section depends on what exactly we test*

TODO:
- dataset partitioning using consistent hashing (do we need to write about this?)
    - token ring
    - last-write-wins element-set conflict-free replicated data type for each CQL row
- replication: versioned data and tunable consistency
    - replicates every partition of data to many nodes across the cluster to maintain high availability
    - replication factor (RF)
    - replication strategy (probably not important)
    - data versioning: mutations timestamp versioning. Therefore they can guarantee eventual consistency of data. Updates resolve according to last write wins.
    - replica synchronization, read repair
    - tunable consistency: consistency levels, R + W > N

## Consistency guarantees

Cassandra guarantees according to Apache Cassandra's documentation the following regarding consistency:
- Eventual Consistency of writes to a single table.
- Strong consistency when R + W > N. (not really a guarantee though)
- Batched writes across multiple tables are guaranteed to succeed completely or not at all
- Linearizable consistency in lightweight transactions. 

Since Cassandra is an AP system, Cassandra does not guarantee linearizability or sequential consistency. Instead it guarantees eventual consistency, a form of weak consistency. Eventual consistency says that all updates will reach all replicas eventually. However, Cassandra offers tunable consistency as a option to achieve stronger consistency, which enables the user to balance the consistency and availability. Strong consistency can then be achieved by fullfilling R + W > RF, where R is the read consistency level, W is the write consistency level and RF is the replication factor. Strong consistency means that any read operation will return the most recent write value. 

Batched writes are guaranteed to succeed completely or not at all. 
**TODO**: add information here depending on what we are testing in the batch test. Among others, write about that batch writes ensure atomicity and isolation (ACID) at partition level withing a single replica (https://www.baeldung.com/java-cql-cassandra-batch).

Finally, Cassandra guarantee linearizability in lighweight transactions which will be handled in the following section.

## Linearizability with LWT

Linearizability is a consistency model that guarantees that each operation in a distributed system appears to have occurred instantaneously, in a globally consistent order. In other words, the system behaves as if there is a single copy of the data that all operations are performed on, even though the data is actually distributed across multiple nodes.

Cassandra provides support for linearizable operations through its Lightweight Transactions (LWTs) feature, which uses a Compare-and-Set (CAS) mechanism to ensure that a write operation can only succeed if the current state of the data matches a specific condition.

LWTs in Cassandra provide stronger consistency guarantees than the eventual consistency model that Cassandra uses by default. However, LWTs come with some performance trade-offs, as they require additional communication between nodes to ensure that operations are linearizable. Therefore, it is important to carefully consider the use of LWTs in an application and use them only when necessary.

But how does the LWTs work in more detail? The LWTs are implemented using the Paxos consensus protocol, and a light weight transaction consists of four phases: prepare and promise, read existing values, propose and accept, and commit. 

According to Cassandra's documentation, the LWT is a serial transaction. 


Explain how LWT are implemented using Paxos:
- provides linearizable concistency
- according to Cassandra's documentation, this means serializability (serial transaction in ACID context)
- CAS (compare and set operation)
- Four phases:
1. Prepare and promise
2. Read existing value
3. Propose and accept
4. Commit
 
Address nondeternism regarding lwt:
-https://faun.pub/cassandra-light-weight-transactions-busted-229e0633c8b here it says that 
"LWT may result in a non-deterministic state"
- under contention, it's possible mutation operations succeeds, but the client might receive an exception which results in the interpretation and could cause serious ramifactions

# Non-deternism
Either include this in other sections, or have its own section here

Some ideas of what we can mention here (however, we should probably only mention the things that are relevant to what we test):
- Last write wins principle? Because we don't know which write will succeed. According to the original Jepsen article: "... (last write wins principle) destroys information nondeterministically"
- Maybe something regarding isolation of transactions. According to original Jepsen article Cassandra allows even dirty write, meaning it has the lowest isolation level. 
- Repairs?
- LWT, see above

# Related work

- Rubric: Analysis of the technique in compared to related work (Sufficient discussion of pros and cons of the studied approach compared to 5+ related work)

TODO:
1. Original Jepsen test of Cassandra
2. Datastax's/scalar's tests
3. Look at the reports that were cited in the lectures

Option is also to combine this inside other sections. 

# Tests

TODO: Change structure depending on tests performed and which results we get.

## Test setup

A test cluster is set up with 5 docker containers and 1 control docker container. The machines run Ubuntu 20.04. Cassandra version 3.x is used. See the README.md in the Github repository for a detailed explanation of the setup. 

### Jepsen

- short description
- histories are generated
- checkers such as Knossos (linearizability) and Gretchen (serializability)

## Bank transaction using lwt and batches

- Goal: Check if it is possible to implement bank transactions with Cassandra's consitency

### Implementation

### Limitations (if relevant)

## Testing isolation levels in Cassandra

### Implementation

### Limitations (if relevant)

## Testing strong consistency in Cassandra

### Implementation

### Limitations (if relevant)

# Nemesis

## Network partitioning

## Daemon pause/terminate a node

## ...

# Results

TODO:
- some cool graphs and tables

# Conclusion

TODO:
- Summary
- Future

Our implementation is available ... 




