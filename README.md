# resource-scheduler

Resource scheduling strategy simulation.

The current scheduler implementation has the following assumptions:

1. The scheduler can start 1 job per time step
2. The simulation is finished when all jobs finish

The currently available scheduling strategies are "first-come-first-served" and "smart".

## Strategies

### First Come First Served Strategy

This strategy consumes jobs as if they were a queue (FIFO) in each time
step. This is not optimal since large jobs that can't be run due to unavailable
resources may block the scheduler from running subsequent smaller jobs that
could be run somewhere.

### Smart Scheduler Strategy

This strategy scans all jobs and nodes for one match in each time step. A match
consists of a node-job pair where node has enough resources to run job. This is
better than "first-come-first-served" because large jobs won't block the
scheduler.

## Dependencies

- Java
- Leiningen

## Installation

```
$ git clone https://mpereira@bitbucket.org/mpereira/resource-scheduler.git
$ lein uberjar
```

## Usage

This will first output the scheduler topology: nodes and jobs, and then output
scheduler steps with per-step actions, until all jobs finish.

```
$ java -jar target/uberjar/resource-scheduler-0.1.0-SNAPSHOT-standalone.jar $scheduler-type $node-stream $job-stream
```

Small example:

- first-come-first-served strategy

- 2 nodes:

  - node 1 with 4 resource units
  - node 2 with 5 resource units

- 2 jobs:

  - first job needs 3 resource units for 2 time steps
  - second job needs 4 resource units for 3 time steps

```
$ java -jar target/uberjar/resource-scheduler-0.1.0-SNAPSHOT-standalone.jar first-come-first-served "(1 4) (2 5)" "(3 2) (4 3)"

Job queue: 2
|-------------------------+---------------------|
| Required Resource Units | Time Steps Required |
|-------------------------+---------------------|
| 3                       | 2                   |
| 4                       | 3                   |
|-------------------------+---------------------|

Nodes: 2
|----+----------------|
| Id | Resource Units |
|----+----------------|
| 1  | 4              |
| 2  | 5              |
|----+----------------|

Step 1
[scheduler] - Starting job {:required-resource-units 3, :time-steps-required 2} on node 1 (remaining resource units: 4)

Step 2
[node-1]    - Job {:required-resource-units 3, :time-steps-required 2} has been running for 1 steps (1 to go)
[scheduler] - Starting job {:required-resource-units 4, :time-steps-required 3} on node 2 (remaining resource units: 5)

Step 3
[scheduler] - Idle
[node-1]    - Job {:required-resource-units 3, :time-steps-required 2} finished
[node-2]    - Job {:required-resource-units 4, :time-steps-required 3} has been running for 1 steps (2 to go)

Step 4
[scheduler] - Idle
[node-2]    - Job {:required-resource-units 4, :time-steps-required 3} has been running for 2 steps (1 to go)

Step 5
[scheduler] - Idle
[node-2]    - Job {:required-resource-units 4, :time-steps-required 3} finished
```

So we see that the simulation took 5 time steps.

## Running a Simulation

Node stream: `(2 3) (7 1) (1 10) (8 5) (2 8)`

Job stream:  `(3 4) (1 4) (4 7) (1 3) (5 4) (9 3)`

Running a "first-come-first-served" simulation:

```
$ java -jar target/uberjar/resource-scheduler-0.1.0-SNAPSHOT-standalone.jar first-come-first-served "(2 3) (7 1) (1 10) (8 5) (2 8)" "(3 4) (1 4) (4 7) (1 3) (5 4) (9 3)"
```

Running a "smart" simulation:

```
$ java -jar target/uberjar/resource-scheduler-0.1.0-SNAPSHOT-standalone.jar smart "(2 3) (7 1) (1 10) (8 5) (2 8)" "(3 4) (1 4) (4 7) (1 3) (5 4) (9 3)"
```

For these inputs the "first-come-first-served" simulation takes 13 time steps to
finish. The "smart" simulation takes 9. This is because on the
"first-come-first-served" simulation the scheduler has to wait for resources on
node `(1 10)` to free up on step 7 so that it can run the `(9 3)` job, that
requires 9 resource units and can only be run on that node.

One possible improvement for either strategy would be reserving nodes with high
available resource units in the presence of large jobs for running those large
jobs.

## TODO

- [ ] Docker image
- [ ] Break core namespace into smaller namespaces
- [ ] Tests
- [ ] Show resource usage statistics at the end (total/average resource idleness, etc.)
- [ ] Make it possible for the scheduler to schedule multiple tasks per time step?

## License

Copyright © 2017 Murilo Pereira <murilo@murilpereira.com>

Distributed under the Eclipse Public License either version 1.0 or (at your
option) any later version.
