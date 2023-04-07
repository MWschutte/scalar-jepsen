# Report part for bank workload

## Bank tests
A transactional workload that is often used to test consistency of a database is the bank test. In a bank test mony transfers between bank accounts are simulated. For these transactions it is of course important that there is no mony lost during transfer. Moreover bank accounts have the simple constraint that they are not allowed to have a negative amount in the bank.

### Requirements
A bank account could be represented as an integer. A transaction must be atomic. For a mony transfer, mony needs to be subtracted from one account and then added to another one. If there is a failure half way the transaction should roll back. Otherwise mony will be lost. In order for the account to be nonnegative a constraint must be added in the transaction that if the transfer amount is less than the bank account the transaction should fail.

### Cassandra guarantees
The question is: does cassandra give sufficient transactional guaranties to implement the bank test. Using ```batch```, ```counter``` and light weight transactions (```ltw```) as building blocks is should be possible to build a bank test with mony transfer. 

The ```batch``` statement [guarantees atomicity and isolation within a single partition](https://docs.datastax.com/en/cql-oss/3.x/cql/cql_reference/cqlBatch.html). the ```counter``` datatype supports [addition and subtracting](https://docs.datastax.com/en/cql-oss/3.x/cql/cql_reference/counter_type.html). Finally, using ```ltw``` a [compare and set operation](https://docs.datastax.com/en/drivers/python/3.2/lwt.html) allows us to check if there is enough mony in the account for a transfer. 

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
However running this query gives an error unfortunatly:

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