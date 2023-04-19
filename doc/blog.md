# Verifying consistency guarantees in Cassandra:

*By [Casper Henkes](https://github.com/),
[Marcus Schutte](https://github.com/),
[Emilia Rieschel](https://github.com/rieschel).*

Cassandra is a popular open-source database used by large enterprises such as Netflix, Apple, and eBay. While it may be assumed that the documentation regarding Cassandra's consistency guarantees is comprehensive and thoroughly tested, our research shows that this is not always the case. Therefore, we conducted an in-depth assessment of Cassandra's consistency guarantees to validate them.

Regarding the CAP theorem, Cassandra is an AP system, which means it prioritizes availability and partition tolerance over consistency. Cassandra is therefore only guaranteeing eventual consistency. However, Cassandra allows developers to tune consistency levels to match the needs of their applications. Additionally, the documentation claims that "strong consistency" can be achieved when R + W > RF, where R = Consistency level for read operations, W = Consistency level for write operations, and RF = Replication factor. "Strong consistency" defined by Cassandra ensures that every read operation returns the most recent write value, and is in other words not the same as linearizability. However, it is unclear from Cassandra's documentation whether "strong consistency" (every read operation returns the most recent write) is guaranteed during failures, such as node failures or network partitions. Therefore, our first test will focus on testing "strong consistency" guarantees during failure scenarios.

If linearizable consistency is required, Cassandra offers Lightweight Transactions (LWTs) that guarantee linearizability on single partitions. However, when using LWTs, the documentation specifies that you must use the read consistency level serial. Unfortunately, the documentation does not provide a clear explanation as to why the serial read consistency level is necessary. Hence, our second test aims to determine what happens when other read consistency levels are used while performing lightweight transactions.

Finally, one of the most challenging consistency and isolation tests to achieve is a bank transaction where no money can be lost during the transaction. Therefore, our third test focuses on whether Cassandra's transactional guarantees are sufficient to perform bank transactions.

# Test setup

Our Github repository is forked from [Scalar-labs's repository](https://github.com/scalar-labs/scalar-jepsen). A test cluster is set up with 5 docker containers and 1 control docker container. The machines run Ubuntu 20.04. Cassandra version 3.11 is used. See the README.md in our Github repository for a detailed explanation of the setup. 

## Jepsen

In this project we have used the testing framework [Jepsen](https://github.com/jepsen-io/jepsen). Jepsen is a testing tool created by Kyle Kingsbury to analyze the consistency and reliability of distributed databases under various failure scenarios. Jepsen works by subjecting the database system to a series of tests that simulate real-world network and hardware failures, such as node crashes, network partitions, and message loss. There are other tools we could have used to test Cassandra's consistency, for example [Cassandra's stress testing tool](https://cassandra.apache.org/doc/latest/cassandra/tools/cassandra_stress.html). However, that tool is more specialized in testing benchmarking performance or identifying system bottlenecks, while Jepsen is specialized on testing consistency guarantees. 

Jepsen tests generate histories, and checkers are then used to verify the correctness of the histories against a formal model of the expected behaviour. In our project, we used the [Knossos checker](https://github.com/jepsen-io/knossos), which implements the [Wing and Gong](https://doi.org/10.1006/jpdc.1993.1015)  linearizability checker, to test for linearizable consistency. Although [Porcupine](https://github.com/anishathalye/porcupine) is another checker that could be used for testing linearizability, we chose Knossos because previous Cassandra testing had been performed using it and because it is easily integrated with Jepsen. We also adapted some of the checkers found in the Jepsen Github repository for our strong consistency test and bank transaction test.

# Nondeternism
*Change the following (and add more details + double check if everything is correct) depending on exactly what we are testing*

Since Cassandra is a distributed system that prioritizes availability and partition tolerance over consistency, there are several factors that can cause nondeternism to our tests. First of all, we are inserting faults during the tests and have used both network partitioning and temporarily suspending nodes.

- Network partitioning: When partioning the network into two subclusters, inconsistencies between the clusters can happen. For example, if a write happens in one of the subclusters, the other subcluster will not have the latest write. This means, if we perform a read, we won't know if it will get the most recent data or not. (TODO: look into hinted handoff, because apparently Cassandra is using the to handle writes that occur during network partitions. However, Cassandra should not have anything that handle reads during network partitions as I have understood it)

- Temporarily suspending nodes: The effects of suspending the nodes will depend, but can cause nondeternism if it disrupts the replication phase. For example, if a write request is made to a suspended node the data will not be propagated to the other nodes until the node is brought back online. In the meantime, a read request may be made to the other nodes, and it may then return an outdated version of the data that does not reflect the changes made to the suspended node. But if the node was suspended after that the data was replicated to all the nodes, the read would return the most recent write data. 

Secondly, not only inserting faults in the tests can cause nondeternism, but also Cassandra by design. For example, Cassandra's conflict handling can cause nondeternism during concurrent writes:

- Concurrent writes without using lwt: Concurrent writes in Cassandra can cause nondeterminism because multiple clients can simultaneously issue write requests to update the same data row with different values, and it's not guaranteed which write request will be applied first. Additionally, the writes could have the same timestamp, and then the lexically bigger value will be chosen.

Finally, Cassandra offers the tunable consistency to balance between consistency and availability. If availability is prioritized by using low conistency levels or low replication factor, this can resuls in nondeternism:

- Low consistency levels: For example if setting read consistency level to ONE, two read queries can give different data if one node has the latest write and the other one has not received it yet. 

- Low replication factor: For example if RF is set to 1, and the node with the data becomes unavailable somehow in one read query, two read queries for the same data could give back different results.

# Testing the "strong consistency" guarantee during node failure
*Discuss together exactly what we want to test here*

> **TODO - Tests:**
> - Verfiying strong consistency during node failure - probably we should be able to find inconsistency (in this article they show examples of when strong consistency do not hold https://blog.scottlogic.com/2017/10/06/cassandra-eventual-consistency.html)
> - Maybe test with other nemesesis as well? 
> - Maybe also test with Knossos and see what it is that fails. Probably however it will only show that some updates are lost, at least this was found by Kyle's tests.

> **TODO - Discuss results:**
> - Create a diagram showing what happens during the tests, and why we can get inconsistencies during node failure (if we got some)
> - Maybe show the histories if possible
> - NON-DETERNISM: probably enough with just discussing causes for the inconsistencies we see? Also, explain why last write wins policy can cause lost updates (no isolation), and reference to Kyle's testing.

# Testing LWT's for linearizability using different read consistency levels
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

# Testing atomicity and isolation in the bank transaction scenario
**TODO:** In order to compare our studied approach we could motivate shortly why this test tests the consistency of batch operations more thoroughly than the batch operation in the Scalar-lab. 

Cassandra gives no linearizability/serializability guarantees except for in the light weight transactions. However lightweight transactions are only supported within a row. But cassandra does guarentee strong consistency in terms of CAP theorem and it provides atomicity and isolation with batched transactions. 
With these building blocks we try to implement bank transactions.

## Requirements.
We use the cassandra [counter](https://docs.datastax.com/en/cql-oss/3.x/cql/cql_reference/counter_type.html#:~:text=Counter%20type-,A%20counter%20column%20value%20is%20a%2064%2Dbit%20signed%20integer,two%20operations%3A%20increment%20and%20decrement.) data type to represent the bank account. This allows us to increment and decrement the bank account value. We use cassandra [counter batch] (https://docs.datastax.com/en/cql-oss/3.x/cql/cql_reference/cqlBatch.html) since it guarantees atomicity and isolation within a single partition. Idealy we would use light weight transactions to make sure bank account values remain positive. But counter batches are [idempotent](https://cassandra.apache.org/doc/latest/cassandra/cql/dml.html#counter-batches). And [lightweight transactions do not suppport idempotent datat types](https://docs.datastax.com/en/developer/java-driver/3.1/manual/idempotence/). So we drop the constraints that bank transactions have to be nonegative.

## test setup
The workload consist of reads of all bank accounts with consistency level all and counter batches mony is transfered from one account to another. The reads read the total state of the database. There are 8 accounts in total and they start of with 10 credit in each account. The reads read the total state of the databsae and are expected to return a total amount of 80 credit summing the ammount in all accounts. After the transactions are done the test times out for 60 seconds and reads one more time to observe the final state. 

The bank transaction test are setup in twoo different ways. Cpnfiguration 1) the bank accounts live on multiple partitions. Configuration two) the accounts live on one partition. Cassandra does not provide isolation in batches when data is located at muliple partitions. 

## Results
The results of the two configurations are as follows:

### Configuration 1: Bank accounts on mulitple partitions
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
This is to be expected since with this configurations Cassandra does not provide isolation. A read can happen when a counter batch is only half way in progress resulting to read of an inconsistent state. However the last read of the database reads as shown bellow:

```
2	:ok	:read	[-246 -236 -87 153 393 -74 -73 250]
```
Here all the accounts sum to 80 total again. This gives a good example of Cassandras eventual consistency at play.

### Configuration 2: All Bank accounts on one partition.
As expected all the reads observe conssitent state of the database. Jepsn outputs:
```
Everything looks good! ヽ(‘ー`)ノ
```
We verify that the counter batch operates in atomicity and isolation as promised by cassandra.

### Configuration 2) Introducing Nemesis
Lets see if the batch counter keeps its atomicity when we introduce failure into the system.

# Conclusion
**TODO:**
- Summarize results
- Implications

To extend the work we could perform the tests on newer versions of Cassandra, for example version 4.0. Additionally, we could further investigate the transactional guarantees of Cassandra, for example by testing the lightweight transaction for serializability. For such tests using the [Gretchen](https://github.com/aphyr/gretchen) checker would be relevant, which checks for serializability.
