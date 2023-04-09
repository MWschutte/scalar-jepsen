# Verifying consistency settings and models in Cassandra

*By [Emilia Rieschel](https://github.com/rieschel),
[Casper](https://github.com/)
and [Marcus](https://github.com/)*.

Cassandra is known for being an AP system, meaning it prioritizes availability and partition tolerance over strong consistency. However, Cassandra still offers options to achieve stronger consistency, such as tunable consistency and lightweight transactions. This raises questions about what guarantees these consistency settings provide and whether Cassandra's claims about consistency hold up under scrutiny.

To evaluate Cassandra's consistency guarantees, we conducted three tests using Jepsen, a distributed systems testing tool. First, we tested the isolation levels provided by Cassandra's lightweight transactions. Second, we tested the consistency models achievable with Cassandra's tunable consistency. Finally, we tested whether Cassandra's consistency is sufficient to pass a Jepsen bank account transfer test. By performing these tests, we aimed to better understand the strengths and limitations of Cassandra's consistency model and its implications for applications that rely on strong consistency.

# What is Cassandra?
*This part should fulfill the criteria: "Explanation of the targeted concurrency model - all details of the system with clear justification"*

## Short overview
Regarding the CAP theorem, Cassandra is an AP system. This means it prioritizes availability and partition tolerance over consistency.

![Cassandra CAP](cap.png)

## Architecture (focus on Dynamo)
*What we mention in this section depends on what exactly we test*

**TODO**:
- dataset partitioning using consistent hashing
    - especially important to explain enough in order to understand why isolation is guaranteed on single replicas but not on multiple replicas
    - token ring
    - last-write-wins element-set conflict-free replicated data type for each CQL row (we probably don't need to write about this)
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
**TODO**: add information here depending on what we are testing in the batch test. Among others, write about that batch writes ensure atomicity and isolation (ACID) at partition level within a single replica (https://www.baeldung.com/java-cql-cassandra-batch).

Finally, Cassandra guarantee linearizability in lighweight transactions which will be handled in the following section.

## Linearizability with LWT

Linearizability is a consistency model that guarantees that each operation in a distributed system appears to have occurred instantaneously, in a globally consistent order. In other words, the system behaves as if there is a single copy of the data that all operations are performed on, even though the data is actually distributed across multiple nodes.

Cassandra provides support for linearizable operations through its Lightweight Transactions (LWTs) feature, which uses a Compare-and-Set (CAS) mechanism to ensure that a write operation can only succeed if the current state of the data matches a specific condition.

LWTs in Cassandra provide stronger consistency guarantees than the eventual consistency model that Cassandra uses by default. However, LWTs come with some performance trade-offs, as they require additional communication between nodes to ensure that operations are linearizable. Therefore, it is important to carefully consider the use of LWTs in an application and use them only when necessary.

But how does the LWTs work in more detail? The LWTs are implemented using the Paxos consensus protocol, and a light weight transaction consists of four phases: prepare and promise, read existing values, propose and accept, and commit. 

**TODO**: Write about Paxos in more detail. And include the non-determinism.  

**TODO**: Write something about that according to Cassandra's documentation, the LWT is a serial transaction, but it is not? Or at least mentioning this depending on what our tests shows.

**TODO**: Discuss LWTs regarding ACID. When does it guarantee isolation and when not. 
 
Address nondeternism regarding lwt:
- https://faun.pub/cassandra-light-weight-transactions-busted-229e0633c8b here it says that 
"LWT may result in a non-deterministic state"
- https://issues.apache.org/jira/browse/CASSANDRA-9328 here is a issue thread talking about LWTs non-deterministic behaviour
- under contention, it's possible mutation operations succeeds, but the client might receive an exception which results in the interpretation and could cause serious ramifactions

# Non-deternism
*Rubric: Explanation of sources of nondeterminism addressed (All sources of nondeterminism with clear examples)*

Probably only include he parts that are relevant to our tests? 

Some ideas of what we can mention here (however, we should probably only mention the things that are relevant to what we test):
- Last write wins principle? Because we don't know which write will succeed. According to the original Jepsen article: "... (last write wins principle) destroys information nondeterministically". Maybe we can have a test (when we don't have strong consistency) that shows this? Last write write policy result in that P0 is allowed?
- Maybe something regarding isolation of transactions. According to original Jepsen article Cassandra allows even dirty write, meaning it has the lowest isolation level. 
- Repairs?
- LWT, see section above
- What happens during failures? This article: https://www.yugabyte.com/blog/apache-cassandra-lightweight-transactions-secondary-indexes-tunable-consistency/ says: "However, writes that fail because only a partial set of replicas are updated could lead to two different readers seeing two different values of data." when talking about quorum consistency. 


# Related work

*Rubric: Analysis of the technique in compared to related work (Sufficient discussion of pros and cons of the studied approach compared to 5+ related work)*

**TODO**:
1. Original Jepsen test of Cassandra
2. Datastax's/scalar's tests
3. Look at the reports that were cited in the lectures

Cassandra's consistency has, to some extent, been tested before. Kyle Kingsbury tested Cassandra latest 2013 and found several issues (version 2.0.0). 
Tests showed:
- Strong consistency (mutating the same cell repeatedly with quorum consistency) resulted in 28% write loss
- CRDTs work as expected 
- Counters can drift up to 50% during network partitions
- Isolation, Cassandra allows P0. Also, Kyle reasons about the problems with Cassandra if writes have the same timestamp. Recommendation is to use a strong external coordinator which guarantees unique timestamps. 
- LWTs did not work as expected, 1-5% acknowledged writes were lost. 

Datastax tested Cassandra 2016:
- CQL sets and maps
- Counters
- LWTs
- Materialized views
- Nemesis: network partitions, process crashes, clock drift
- Tests resulted mainly in stabilizing materialized views
- Only minor issues were found

Option is also to combine this inside other sections. 

# Tests
*Rubric: Explanation of the bug detection technique  (Explanation of the bug detection technique (10 pts)	Satisfying explanation of the algorithm, its implementation architecture, applicability to benchmarks, implementation limitations and possible extensions	Sufficient explanation of the algorithm, its implementation architecture, applicability to benchmarks, implementation limitations	Sufficient explanation of the algorithm and its implementation architecture	Lacks detail, just enough explanation of the algorithm and its implementation architecture)*

*Rubric: Empirical evaluation results (Sufficient level of experimental results with a few different configurations of parameters, in comparison to more than one baselines)*

**TODO**: Change structure depending on tests performed and which results we get.

## Test setup

A test cluster is set up with 5 docker containers and 1 control docker container. The machines run Ubuntu 20.04. Cassandra version 3.x is used. See the README.md in the Github repository for a detailed explanation of the setup. 

### Jepsen

- short description
- histories are generated
- checkers such as Knossos (linearizability) and Gretchen (serializability)

## Bank test with batched counters

- Goal: Check if it is possible to implement bank transactions with Cassandra's consistency

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

- Bank test shows inconsistency.  

**TODO**:
- some cool graphs and tables

# Conclusion

**TODO**:
- Summary
- Future

Our implementation is available on Github ... 



