# Verifying consistency guarantees in Cassandra:

*By [Casper Henkes](https://github.com/),
[Marcus Schutte](https://github.com/),
[Emilia Rieschel](https://github.com/rieschel).*

Cassandra is a popular open-source database used by large enterprises such as [Netflix, Apple, and eBay](https://cassandra.apache.org/_/case-studies.html). While it may be assumed that the documentation regarding Cassandra's consistency guarantees is comprehensive and thoroughly tested, our research shows that this is not always the case. Therefore, we conducted an assessment of some of Cassandra's consistency [guarantees](https://cassandra.apache.org/doc/latest/cassandra/architecture/guarantees.html) to validate them.

Cassandra is an AP system that prioritizes availability and partition tolerance over consistency, which means it only guarantees eventual consistency. Nevertheless, Cassandra offers [tunable consistency](https://cassandra.apache.org/doc/latest/cassandra/architecture/dynamo.html#tunable-consistency) which allows developers to adjust consistency levels according to their application needs. Using tunable consistency, the documentation states that "strong consistency" can be achieved when read and write consistency levels are chosen such that the replica sets overlap. Specifically, this is achieved when $R + W \gt RF$, where $R$ is the consistency level for read operations, $W$ is the consistency level for write operations, and $RF$ is the replication factor. "Strong consistency" defined by Cassandra ensures that every read operation returns the most recent write value, and is in other words not the same as linearizability. We expect that the guarantee also implies that no writes can be lost during the test, and therefore, our first test verifies whether any writes can be lost when $R + W \gt RF$.

If linearizable consistency is required, Cassandra offers Lightweight Transactions (LWTs) that guarantee linearizability on single [partitions](https://cassandra.apache.org/doc/latest/cassandra/architecture/dynamo.html#dataset-partitioning-consistent-hashing). For LWTs we want to see if this guarantee still holds when some program variables like the read consistency change, and if they hold under different types of network failures, as those two things are not explained in the documentation. 

Finally, one of the most challenging transactional tests to achieve is a bank transaction where no money can be lost during the transaction. Cassandra does not guarantee serializability, also not with the LWTs since they only guarantee linearizability over a single partition. However, Cassandra provides atomicity and isolation with the batch operation. Therefore, our third test will aim to explore if the batch operation would ensure the atomicity and isolation needed to perform a bank transaction.

## Test setup

Our GitHub repository is forked from [Scalar-labs's repository](https://github.com/scalar-labs/scalar-jepsen). A test cluster is set up with 5 Cassandra nodes and 1 Jepsen control node and a replication factor of three. The machines run Ubuntu 20.04. Cassandra version 3.11.14 is used. See the README.md in our GitHub repository for a detailed explanation of the setup.

### Jepsen

In this project we have used the testing framework [Jepsen](https://github.com/jepsen-io/jepsen). Jepsen is a testing tool created by Kyle Kingsbury to analyze the consistency and reliability of distributed databases under various failure scenarios. Jepsen works by subjecting the database system to a series of tests that simulate real-world network and hardware failures, such as node crashes, network partitions, and message loss. There are other tools we could have used to test Cassandra's consistency, for example [Cassandra's stress testing tool](https://cassandra.apache.org/doc/latest/cassandra/tools/cassandra_stress.html). However, that tool is more specialized in testing benchmarking performance or identifying system bottlenecks, while Jepsen is specialized in testing consistency guarantees. 

Jepsen tests generate histories, and checkers are then used to verify the correctness of the histories against a formal model of the expected behavior. In our project, we used the [Knossos checker](https://github.com/jepsen-io/knossos), which implements the [Wing and Gong](https://doi.org/10.1006/jpdc.1993.1015)  linearizability checker, to test for linearizable consistency. Although [Porcupine](https://github.com/anishathalye/porcupine) is another checker that could be used for testing linearizability, we chose Knossos because previous Cassandra testing had been performed using it and because it is easily integrated with Jepsen. We also adapted some of the checkers found in the Jepsen GitHub repository for our first and third tests.

### Nemesis

The nemesis we chose to use for our tests are: Crash, which ‘crashes’ a node, stopping it and restarting it later also wiping its data. Bridge, where the network is partitioned into two halves of two nodes where both halves can communicate with the fifth bridge node, and Halves: which partitions the nodes into two subnetworks of size three and two.

### Nondeterminism:

Since Cassandra is a distributed system that prioritizes availability and partition tolerance over consistency, there are several factors that can cause nondeterminism for our tests. First of all, we are inserting faults during the tests and have used both network partitioning and node failures.

- Network partitioning: When dividing a network into two subclusters, inconsistencies may occur between the clusters. In our tests, we utilized two different partitioning techniques: bridge and halves. The bridge partition separates our 5-node cluster into two subclusters, each consisting of 3 nodes, with one node overlapping in both subclusters. The halves partition splits our 5-node cluster into two subclusters with 3 and 2 nodes, respectively. Within each subcluster, all nodes are fully connected with no network issues. However, between the two subclusters, existing connections are dropped, and no new connections can be established during the network partition. As Cassandra prioritizes availability, read and write operations are permitted during network partitions, which can result in nondeterministic outcomes. For instance, in a halves network partition, if a write operation takes place in one of the subclusters, the other subcluster will not have the latest write. Therefore, if we execute a read operation during the network partition, we cannot determine whether it will obtain the most recent data, since it may read from either of the two subclusters. In a bridge network partition, this can also happen if the data is not replicated to the bridge node. Despite the possibility of inconsistencies during network partitions, Cassandra implements a technique called hinted handoff to ensure that all nodes are synchronized once again after a network partition. This is accomplished by storing the missed write on the coordinator node when attempting to write to an unavailable node. Once the network partition is resolved, the stored missed updates are sent to the previously unavailable nodes.

- Node failures: The effects of crashing the nodes will depend, but can cause nondeterminism if it disrupts the replication phase. For example, if a write request is made to a suspended node the data will not be propagated to the other nodes until the node is brought back online. In the meantime, a read request may be made to the other nodes, and it may then return an outdated version of the data that does not reflect the changes made to the suspended node. But if the node was suspended after that the data was replicated to all the nodes, the read would return the most recent write data. 

Secondly, not only inserting faults in the tests can cause nondeterminism, but also Cassandra by design. For example, Cassandra's conflict handling can cause nondeterminism during concurrent writes:

- Concurrent writes without using lightweight transactions: To handle concurrent writes, Cassandra uses timestamps and the last-write-wins policy. For each write, the client assigns a timestamp for the column value that is updated. If two writes try to update the same data at the same time, the write with the latest timestamp will be chosen according to the last-write-win policy. If both of the writes would have the exact same timestamp, which is unlikely, the lexically bigger value will be chosen. So far, Cassandra handles concurrent writes deterministically. However, nondeterminism can occur if the client clocks become out of sync, because then it is not sure which write will have the latest timestamp. 

Finally, Cassandra offers the tunable consistency to balance consistency and availability. If availability is prioritized by using low consistency levels this can result in nondeterminism as reads might not read from the latest writes:

- Low consistency levels: For example if setting read consistency level to ONE, two read queries can give different data if one node has the latest write and the other one has not received it yet. 

## Test 1 - Testing if $R + W \gt RF$ guarantees no writes are lost

This test verifies if every read reads the most recent write when $R + W \gt RF$. We do this by verifying if any updates are lost when apppending items to a list. We expect that all reads will read the most up to date value and that none are lost. And by only appending to a list we can easily verify if any updates are lost.

### Test setup
This test detects lost writes. It uses a list datatype. All transactions are appended to list or read operations. Different read/write consistency levels are configured such that the number of reads $R$ plus the number of writes $W$ is larger than the replication factor $RF$:
For the nemesis we used the Crash nemesis that simulates node failure.

### Results

In the table below X means that no nemesis was active during the test.

| Write consistency  | Nemesis |lost count |Acknowledged count | Attempt count|
| ------------- | ------------- | ------------- | ------------- |------------- |
| Quorum  | X | 0  | 17231| 17231 |
|  Once |   X | 0 | 16038| 16038 |
| Quorum | Crash | 0 | 4353| 4380|
|  Once |   Crash | 0 | 5990 | 5995 |

As we can see even with a nemesis active we still find no writes being lost. Thus we can assume that the guarantee holds.

## Test 2 - Testing LWTs for linearizability

LWTs are guaranteed to be linearizable if performed on a single partition without any non-LTWs happening. To test this claim we will test two different scenarios. First off we will change the read-consistency to be not serial. The documentation states that the serial read-consistency should be used with LWTs so by changing this we expect the resulting history to not always be linearizable. Secondly, we introduce different nemesis into the system to see if the linearizability guarantee still holds with network failures.

### Test setup
This test uses the 5 nodes from our general test setup. A generator then generates a random operation, which is either a Read, a Write, or a Compare And Swap (CAS) operation, on a random node. Here reads are roughly three times as likely as write and CAS operations. There is also a nemesis generator in the test. If a nemesis is active then it will activate the nemesis on a random node with a five second active five second passive timeline. The nemesis we chose to use for this test are: Crash, which ‘crashes’ a node, stopping it and restarting it later also wiping its data. Bridge, where the network is partitioned into two halves of two nodes where both halves can communicate with the fifth bridge node, and Halves: which partitions the nodes into two subnetworks of size three and two.

### Results

#### **Read consistency**

| Consistency | Result |
| --- | --- |
| SERIAL | pass |
| ONE | fail |
| TWO | pass |
| QUORUM | pass |
| ALL | pass |

For the read consistency, we only observed failures for read-consistency level ONE. We hypothesize that if $R + W \le RF$ then it is possible to read an outdated value. This is not allowed in linearizability and so we can find errors here. We first expected that errors might also arise with read-consistency TWO, but if W for LWTs is two then $R + W \gt RF$ as $RF = 3$ in our test environment.

#### **Nemesis**

| Nemesis | Result |
| --- | --- |
| None | pass |
| Crash | pass |
| Halves | pass |
| Bridge | pass |

For the nemesis as is seen, all tests pass. This means that Cassandra's LWTs seem to be linearizable even with the tested network failures for the scenarios that we have tested. This does not prove linearizability but it is a good sign.

## Test 3 - Testing atomicity and isolation of batch operations in the bank transaction scenario

In this test we will perform bank transactions using the batch operation, and see if the batch operation achieves the atomicity and isolation it guarantees.

### Requirements:

We use the Cassandra counter data type to represent the bank account. This allows us to increment and decrement the bank account value. We use Cassandra counter batch since it guarantees atomicity and isolation within a single partition. Ideally, we would use lightweight transactions to make sure bank account values remain positive, but counter batches are idempotent, and lightweight transactions do not support idempotent data types. Therefore, we drop the constraint that bank transactions have to be non-negative.

### Test setup

The workload consists of read operations, which read all bank accounts with consistency level ALL, and counter batches, which transfer money from one account to another. The reads read the total state of the database. There are 8 accounts in total and they start with 10 credits in each account. The reads read the total state of the database and are expected to return a total amount of 80 credits summing the amount in all accounts. After the transactions are done the test times out for 60 seconds and reads one more time to observe the final state. 

The bank transaction test are set up in two different ways:
- Configuration 1: the bank accounts live on multiple partitions. 
- Configuration 2: the bank accounts live on one partition. 
Cassandra does not provide isolation in batches when data is located at multiple partitions. 

### Results

The results of the two configurations are as follows:

#### **Configuration 1: Bank accounts on multiple partitions**

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

This is to be expected since with this configuration Cassandra does not provide isolation. This means that a read can happen when a counter batch is only halfway in progress, resulting in reading an inconsistent state. However, the last read of the database reads as shown below:

```
2	:ok	:read	[-246 -236 -87 153 393 -74 -73 250]
```

Here all the accounts sum to 80 total again. This gives a good example of Cassandra's eventual consistency at play.

#### **Configuration 2: All Bank accounts on one partition**

As expected all the reads observe a consistent state of the database. Jepsen outputs:

```
Everything looks good! ヽ(‘ー`)ノ
```

We verify that the counter batch operates in atomicity and isolation as promised by Cassandra.

#### **Introducing nemesis to Configuration 2**

Let's see if the batch counter keeps its atomicity when we introduce failure into the system.
In the picture below a latency plot of the bank transactions are shown for when crash nemesis was added to the test. Although it results in more transfers failing, there are no exceptions thrown. Similar results are observed for the bridge nemesis.

![bank set crash](https://github.com/MWschutte/scalar-jepsen/blob/blog_post/doc/latency-raw-bank-set-crash-bootstrap.png?raw=true)

## Conclusion

Our results show that Cassandra is able to uphold its guarantees, including both lightweight transactions and batch operations which performed as expected and that $R + W \gt RF$ seems to quarantee that no writes are lost. While our results alone do not prove the guarantees, they do suggest that users of Cassandra can have confidence in the guarantees provided in the documentation. However, it is important to note that Cassandra is an AP system, and as such, both lightweight transactions and stronger transactional guarantees only apply to single partitions and not multiple partitions. Therefore, while Cassandra is an excellent choice for applications prioritizing availability and scalability, other options may be more suitable for those prioritizing consistency.

To extend the work we could perform the tests on newer versions of Cassandra, e.g. 4.X. Additionally, we could further investigate the transactional guarantees of Cassandra, for example by testing the lightweight transactions with the [Gretchen](https://github.com/aphyr/gretchen) checker, which checks for serializability.