# Cassandra tests with Jepsen

This is based on [riptano's jepsen](https://github.com/riptano/jepsen/tree/cassandra/cassandra).

## Current status
- Supports Apache Cassandra 3.11.14
- Supports `collections.map-test`, `collections.set-test`, `batch-test`, `counter-test`(only adds)
- Newly Added `bank-test`, `listset-test`
- Updated `lwt-test`

## How to test

Refer to the base readme for setting up the containers / environment.

### Docker settings

You will probably need to increase the amount of memory that you make available to Docker in order to run these tests with Docker. We have had success using 8GB of memory and 2GB of swap. More, if you can spare it, is probably better.

### Run a test
1. Start the Docker nodes or multiple machines and log into jepsen-control (as explained [here](https://github.com/scalar-labs/scalar-jepsen/tree/README.md)).

2. Run a test
For running the tests you need to go to the cassandra directory within the jepsen-control node.
```
(in jepsen-control)
$ cd ${SCALAR_JEPSEN}/cassandra
```

The base command for a test is as follows
```
(in jepsen-control)
$ lein run test --test <testname> --nemesis <nemesis name> --ssh-private-key ~/.ssh/id_rsa
```
For example the run the lwt test with the bridge nemesis you can use.
```
(in jepsen-control)
$ lein run test --test lwt --nemesis bridge --ssh-private-key ~/.ssh/id_rsa
```

- See `lein run test --help` for full options
