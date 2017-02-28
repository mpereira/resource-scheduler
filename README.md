# resource-scheduler

FIXME: description

## Dependencies

- Java
- Leiningen

## Installation

```
$ git clone https://mpereira@bitbucket.org/mpereira/resource-scheduler.git
$ lein uberjar
```

## Usage

FIXME: explanation

    $ java -jar target/uberjar/resource-scheduler-0.1.0-SNAPSHOT-standalone.jar $scheduler-type $resource-stream $job-stream

## Examples

    $ java -jar target/uberjar/resource-scheduler-0.1.0-SNAPSHOT-standalone.jar first-come-first-served "(2 3) (7 1) (1 10) (8 5) (2 8)" "(3 4) (1 4) (4 7) (1 3) (5 4) (9 3)"

    $ java -jar target/uberjar/resource-scheduler-0.1.0-SNAPSHOT-standalone.jar smart "(2 3) (7 1) (1 10) (8 5) (2 8)" "(3 4) (1 4) (4 7) (1 3) (5 4) (9 3)"
...

## License

Copyright © 2017 FIXME

Distributed under the Eclipse Public License either version 1.0 or (at your
option) any later version.
