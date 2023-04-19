# WIP Report part for Lightweigt Transactions

## What does cassandra quarantee?
Data must be read and written in a sequential order. Paxos consensus protocol is used to implement lightweight transactions. Paxos protocol implements lightweight transactions that are able to handle concurrent operations using linearizable consistency. Linearizable consistency is sequential consistency with real-time constraints and it ensures transaction isolation with compare and set (CAS) transaction. With CAS replica data is compared and data that is found to be out of date is set to the most consistent value. Reads with linearizable consistency allow reading the current state of the data, which may possibly be uncommitted, without making a new addition or update. 

## What we tested.
### The provided test.
Start running the provided lwt test. This test creates one table and then randomly executes compare and set (cas), write, read instructions (with serializable serial consistency level). It also runs a nemesis that makes a sequence with start, stop, mix bootstrapping and decommissioning failures. The execution history of this test is then checked by Knossos to test for linearizability.

The expectation is that this test correctly implements linearizability and as expected this test passes.

(include image of passing test?)

### Changing the consistency level of reads.
After that it seemed interesting to test what would happen if we remove the constraint that the read consistency should be serial. We expect the analysis to now fail as the reads do not have serial consistency and indeed we observe that the history is not linearizable

(include image of failing test?)

After that ran a bunch of different consistency lvls  to see results

Consistency
- SERIAL : Succes
- LOCAL_ONE : Succes
- ONE : Fail
- TWO : Succes
- THREE : Succes
- LOCAL_QUORUM :Succes
- QUOREM : Succes
- ALL : Succes

At first found that the tests tend to run quite a few commands on the same node in a sequence and that it ran quite a lot of read commands when compared to write commands. Turns out that you need enough reads to actually know if something goes wrong in the histories thus the changes i made that made the amound of reads, writes, and casses about equal ensured that knossos could not longer find errors in the histories. I tried a lot more changes and came to the conclusion that a setup similar to the setup already in the Scalar repository worked best. i did increase the time the tests ran for to be extra sure that the results I was finding were consistent.

Tests are not consistent (probably because of the nemesis or the seed used for the sequence generators)


Changed test to be more consistent with how it generates operations. Which gives basically the same result as the original test but is easier to change and operate.

Adding different nemesis does not seem to matter for the working of the system that much.

seems there might be something weird / wrong with the test nemesis..