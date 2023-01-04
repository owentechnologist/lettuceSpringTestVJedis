## Run this test program like follows:

### To performance test only the Lettuce Client code:
``` 
mvn compile exec:java -Dexec.cleanupDaemonThreads=false -Dexec.args="--host1 192.168.1.21 --port 18386 --querycountperthread 2000 --numberofthreads 20 --usejedis false"
```

### To performance test only the Jedis Client code:
``` 
mvn compile exec:java -Dexec.cleanupDaemonThreads=false -Dexec.args="--host1 192.168.1.21 --port 18386 --querycountperthread 2000 --numberofthreads 3 --usejedis true"
```
#### <em>Toggle the last argument [usejedis] from true to false to compare performance </em>
### Here is a sample run:

``` 
Connecting to 192.168.1.21:18386
COMMANDS TO BE EXECUTED: 

ZADD~keyname,numeric,string
ZRANDMEMBER~keyname
ZREVRANGEBYSCORE~keyname,numeric,zero
ZRANGEBYSCORE~keyname,zero,numeric


Connecting to 192.168.1.21:18386
Connecting to 192.168.1.21:18386

Each thread will execute commands using some or all of the following: (in a set sequence each time a thread fires a query)
ZADD~keyname,numeric,string
ZRANDMEMBER~keyname
ZREVRANGEBYSCORE~keyname,numeric,zero
ZRANGEBYSCORE~keyname,zero,numeric
Waiting for results to come in from our threads...   
..
RESULTS COMING IN!-->>  3 threads have completed their processing...
Thread #1 executed 2000 queries
Thread #1 avg execution time (milliseconds) was: 2
Thread #1 total execution time (seconds) was: 5
Thread #2 executed 2000 queries
Thread #2 avg execution time (milliseconds) was: 3
Thread #2 total execution time (seconds) was: 6
Thread #3 executed 2000 queries
Thread #3 avg execution time (milliseconds) was: 2
Thread #3 total execution time (seconds) was: 5

Across 3163 unique results captured, latencies look like this:
Lowest Recorded roundtrip: [Thread #1: executed command: ZADD (with 1 results...  Execution took: 1 milliseconds]
5th percentile: [Thread #1: executed command: ZRANDMEMBER (with [B@31df573b results...  Execution took: 2 milliseconds]
10th percentile: [Thread #1: executed command: ZRANGEBYSCORE (with [[B@23a6d2c1, [B@5c1bc655, [B@596f0421, [B@3c2aeea0, [B@290f133e] results...  Execution took: 2 milliseconds]
25th percentile: [Thread #2: executed command: ZRANGEBYSCORE (with [[B@1f45f808] results...  Execution took: 2 milliseconds]
50th percentile: [Thread #1: executed command: ZRANDMEMBER (with [B@399c85ca results...  Execution took: 3 milliseconds]
75th percentile: [Thread #3: executed command: ZRANGEBYSCORE (with [[B@47445611] results...  Execution took: 3 milliseconds]
90th percentile: [Thread #1: executed command: ZRANDMEMBER (with [B@785a8bb7 results...  Execution took: 5 milliseconds]
95th percentile: [Thread #1: executed command: ZRANDMEMBER (with [B@1d3ce98f results...  Execution took: 6 milliseconds]
Highest Recorded roundtrip: [Thread #3: executed command: ZRANGEBYSCORE (with [[B@54f5ea5a] results...  Execution took: 143 milliseconds]

Please check the --> slowlog <-- on your Redis database to determine if any slowness is serverside or driven by client or network limits
```
