# Verifying consistency guarantees in Cassandra:

*By [Casper Henkes](https://github.com/),
[Marcus Schutte](https://github.com/),
[Emilia Rieschel](https://github.com/rieschel).*

Cassandra is a popular open-source database used by large enterprises such as Netflix, Apple, and eBay. While it may be assumed that the documentation regarding Cassandra's consistency guarantees is comprehensive and thoroughly tested, our research shows that this is not always the case. Therefore, we conducted an in-depth assessment of Cassandra's consistency guarantees to validate them.

Regarding the CAP theorem, Cassandra is an AP system, which means it prioritizes availability and partition tolerance over consistency. However, Cassandra allows developers to tune consistency levels to match the needs of their applications. Additionally, the documentation claims that strong consistency can be achieved when R + W > RF, where R = Consistency level for read operations, W = Consistency level for write operations, and RF = Replication factor. Strong consistency ensures that every read operation returns the most recent write. However, it is unclear from Cassandra's documentation whether strong consistency is guaranteed during failures, such as node failures or network partitions. Therefore, our first test will focus on testing strong consistency guarantees during failure scenarios.

If linearizable consistency is required, Cassandra offers Lightweight Transactions (LWTs) that guarantee linearizability on single partitions. However, when using LWTs, the documentation specifies that you must use the consistency level serial. Unfortunately, the documentation does not provide a clear explanation as to why serial consistency level is necessary. Hence, our second test aims to determine what happens when different consistency levels are used while performing lightweight transactions.

Finally, one of the most challenging consistency tests to achieve is a bank transaction where no money can be lost during the transaction. Therefore, our third test focuses on whether Cassandra's consistency guarantees are sufficient to perform bank transactions.

# Test setup

Our Github repository is forked from [Scalar-labs's repository](https://github.com/scalar-labs/scalar-jepsen). A test cluster is set up with 5 docker containers and 1 control docker container. The machines run Ubuntu 20.04. Cassandra version 3.11 is used. See the README.md in our Github repository for a detailed explanation of the setup. 

## Jepsen

In this project we have used the testing framework [Jepsen](https://github.com/jepsen-io/jepsen). Jepsen is a testing tool created by Kyle Kingsbury to analyze the consistency and reliability of distributed databases under various failure scenarios. Jepsen works by subjecting the database system to a series of tests that simulate real-world network and hardware failures, such as node crashes, network partitions, and message loss. There are other tools we could have used to test Cassandra's consistency, for example [Cassandra's stress testing tool](https://cassandra.apache.org/doc/latest/cassandra/tools/cassandra_stress.html). However, that tool is more specialized in testing benchmarking performance or identifying system bottlenecks, while Jepsen is specialized on testing consistency guarantees. 

Jepsen tests generate histories, and checkers are then used to verify the correctness of the histories against a formal model of the expected behaviour. In our project, we used the [Knossos checker](https://github.com/jepsen-io/knossos), which implements the [Wing and Gong](https://doi.org/10.1006/jpdc.1993.1015)  linearizability checker, to test for linearizable consistency. Although [Porcupine](https://github.com/anishathalye/porcupine) is another checker that could be used for testing linearizability, we chose Knossos because previous Cassandra testing had been performed using it and because it is easily integrated with Jepsen. We also adapted some of the checkers found in the Jepsen Github repository for our bank transaction test.

# Nondeternism
*Change the following (and add more details) depending on exactly what we are testing*

Since Cassandra is a distributed system that prioritizes availability and partition tolerance over consistency, there are several factors that can cause nondeternism to our tests. First of all, we are inserting faults during the tests and have used both network partitioning and temporarily suspending nodes.

- Network partitioning: When partioning the network into two subclusters, inconsistencies between the clusters can happen. For example, if a write happens in one of the subclusters, the other subcluster will not have the latest write. This means, if we perform a read, we won't know if it will get the most recent data or not. (TODO: look into hinted handoff, because apparently Cassandra is using the to handle writes that occur during network partitions. However, Cassandra should not have anything that handle reads during network partitions as I have understood it)

- Temporarily suspending nodes: The effects of suspending the nodes will depend, but can cause nondeternism if it disrupts the replication phase. For example, if a write request is made to a suspended node the data will not be propagated to the other nodes until the node is brought back online. In the meantime, a read request may be made to the other nodes, and it may then return an outdated version of the data that does not reflect the changes made to the suspended node. But if the node was suspended after that the data was replicated to all the nodes, the read would return the most recent write data. 

Secondly, not only inserting faults in the tests can cause nondeternism, but also Cassandra by design. For example, Cassandra's conflict handling can cause nondeternism during concurrent writes:

- Concurrent writes without using lwt: Concurrent writes in Cassandra can cause nondeterminism because multiple clients can simultaneously issue write requests to update the same data row with different values, and it's not guaranteed which write request will be applied first. Additionally, the writes could have the same timestamp, and then the lexically bigger value will be chosen.

Finally, Cassandra offers the tunable consistency to balance between consistency and availability. If availability is prioritized by using low conistency levels or low replication factor, this can resuls in nondeternism:

- Low consistency levels: For example if setting consistency level to ONE, two read queries can give different data if one node has the latest write and the other one has not received it yet. 

- Low replication factor: For example if RF is set to 1, and the node with the data becomes unavailable somehow in one read query, two read queries for the same data could give back different results.

# Testing strong consistency guarantees during node failure

> **TODO - Tests:**
> - Verfiying strong consistency during node failure - probably we should be able to find inconsistency (in this article they show examples of when strong consistency do not hold https://blog.scottlogic.com/2017/10/06/cassandra-eventual-consistency.html)
> - Maybe test with other nemesesis as well? 
> - Maybe also test with Knossos and see what it is that fails. Probably however it will only show that some updates are lost, at least this was found by Kyle's tests.

> **TODO - Discuss results:**
> - Create a diagram showing what happens during the tests, and why we can get inconsistencies during node failure (if we got some)
> - Maybe show the histories if possible
> - NON-DETERNISM: probably enough with just discussing causes for the inconsistencies we see? Also, explain why last write wins policy can cause lost updates (no isolation), and reference to Kyle's testing.

# Testing LWT's consistency with different consistency levels
Cassandra offers the following consistency levels: ANY, ONE, TWO, THREE, QUORUM, Serial (only for read consistency).

> **TODO - Tests:**
> - Testing LWTs for linearizability when having different read consistency levels (we could compare this with earlier work of Datastax and Kyle, where they only tested the linearizability of LWT but not the implementation of the LWT)
> - We can also just test the guarantee of LWTs fulfilling linearizability (very basic test but still a bit interesting for the report). If we do it, we can then also compare it to Kyle's testing in 2013. In thas case, we could write something like:
>
>As said, LWTs are guaranteed to achieve linearizability, meaning that sequential consistency is achieved with a time-constraint. This guarantee was tested by [Kyle Kingsbury in 2013](https://aphyr.com/posts/294-call-me-maybe-cassandra). At that time, LWT were a new feature of Cassandra 2.0.0, and several issues were found by Kyle. Among others, it was found that 1-5% of the acknowledged writes were dropped, and the cause seemed to be a broken implementation of Paxos. 
>
>Since we are running our tests on Cassandra 3.11 it would be interesting to see if the issues found 2013 are now resolved, and if we can find other issues with the LWT as well.
>
>We tested this by ... 

> **TODO - Discuss results:**
> - Maybe create a table showing for which consistency levels we found issues
> - Try to explain what this issues can be causes by, among others explain why LWT need serial consistency 
> - NON-DETERNISM: What happens during node failure in the LWT transactions? What is the cause to why we find issues in the tests sometimes for some consistency levels but not always?

# Bank transaction

**TODO:** In order to compare our studied approach we could motivate shortly why this test tests the consistency of batch operations more thoroughly than the batch operation in the Scalar-lab. 

A transactional workload that is often used to test consistency of a database is the bank test. In a bank test mony transfers between bank accounts are simulated. For these transactions it is of course important that there is no mony lost during transfer. Moreover bank accounts have the simple constraint that they are not allowed to have a negative amount in the bank.

### Requirements
A bank account could be represented as an integer. A transaction must be atomic. For a money transfer, money needs to be subtracted from one account and then added to another one. If there is a failure half way the transaction should roll back. Otherwise money will be lost. In order for the account to be non-negative a constraint must be added in the transaction that if the transfer amount is less than the bank account the transaction should fail.

### Cassandra guarantees
The question is: does cassandra give sufficient transactional guaranties to implement the bank test. Using ```batch```, ```counter``` and lightweight transactions (```ltw```) as building blocks is should be possible to build a bank test with money transfer. 

The ```batch``` statement [guarantees atomicity and isolation within a single partition](https://docs.datastax.com/en/cql-oss/3.x/cql/cql_reference/cqlBatch.html). the ```counter``` datatype supports [addition and subtracting](https://docs.datastax.com/en/cql-oss/3.x/cql/cql_reference/counter_type.html). Finally, using ```ltw``` a [compare and set operation](https://docs.datastax.com/en/drivers/python/3.2/lwt.html) allows us to check if there is enough money in the account for a transfer. 

Create the following table: 
```sql
CREATE TABLE test.t (
    id int PRIMARY KEY,
    value counter
)
```
Then combining the three building blocks gives our potential bank transfer:

```clojure
 (defn transfer [from, to, amount] 
    (str "BEGIN COUNTER BATCH "
    "UPDATE bat SET value = value + " amount " WHERE pid = " from "; "
    "UPDATE bat SET value = value - " amount " WHERE pid = " to 
        "IF value > "amount";"
    "APPLY BATCH;" ))
```
However running this query gives an error unfortunately:

>Conditions on counters are not supported

And if we look closer in Cassandras [cql documentation for counter batch](https://cassandra.apache.org/doc/latest/cassandra/cql/dml.html#counter-batches) we see that counters are not [idempotent](https://docs.datastax.com/en/glossary/docs/index.html#idempotent). Cassandra uses (a modified version) of Paxos for compare and set. If a Paxos round fails to commit the operation will be replayed next round but replaying a counter addition would result in a new value. Light weight transactions only support idempotent data types.

So we drop the constraint that bank accounts have to be non-negative an proceed with the test. We can still expect no mony to be lost in the transfer. The checker checks for every read operation if the total of te balances of every read operation is equal to the expected total.

After running:
``` bash
lein run test --test bank
```
Jepsen throws the following exception during during evaluation:
```clojure
{:type :wrong-total,
    :expected 80,
    :found 85,
    :op
    {:type :ok,
     :f :read,
     :time 69465289700,
     :process 0,
     :value [101 -26 145 80 134 -116 -44 -189],
     :index 21471}}
```
The reads seem to observe the database in an inconsistent state.
To understand what is going on here lets go back to the guarantees that cassandra gives us for batched operations.  
Remember that the batched updates only guarantee isolation for transactions within the same partition. 
In Cassandra the values are hashed to the first part of the primary key. 
Note that however the last read int the history:
```
2	:ok	:read	[-246 -236 -87 153 393 -74 -73 250]
```
Does sum to 80. This demonstrates the eventual consistency property of cassandra nicely. In other words, no mony was lost during the transactions. The transactions executed atomic.

We can  expand the schema to ensure that all the bank accounts are located in the same partition. We do this by adding a partition key to the primary key. The new schedule would be the following:

```sql
CREATE TABLE test.t (
    id int,
    pid int, 
    value counter
      PRIMARY KEY (pid, id)
)
```
Then if we ensure all the ```pid```'s to be equal (for instance 1) we ensure the all the bank accounts to be mapped to the same partition.
If we run this slightly adopted version of the tests again we get:
```
Everything looks good! ヽ(‘ー`)ノ
```
We verify that the counter batch operates in atomicity and isolation as promised by cassandra.

### Introducing Nemesis
Lets see if the batch counter keeps its atomicity when we introduce failure into the system.

# Conclusion
**TODO:**
- Summarize results
- Future 