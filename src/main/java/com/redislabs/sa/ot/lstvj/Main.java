package com.redislabs.sa.ot.lstvj;

import io.lettuce.core.Range;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisConnection;
import redis.clients.jedis.*;
import redis.clients.jedis.providers.PooledConnectionProvider;
import redis.clients.jedis.util.SafeEncoder;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

/**
 * This is a test to see if it is faster to use Jedis or Lettuce to interact with Redis
 */
public class Main {
    static String PERFORMANCE_TEST_THREAD_COUNTER = "PERFORMANCE_TEST_THREAD_COUNTER";
    static String ALL_RESULTS_SORTED_SET="allresults";
    static String KEY_PREFIX = "OT:";
    static JedisConnectionHelper jedisConnectionHelper = null;
    static SpringThingBean springThingBean = new SpringThingBean();
    static LettuceConnectionHelper lettuceConnectionHelper = null;

    public static void main(String [] args){
        boolean bogusBoolean = true;
        boolean useJedis = true;
        String host = "192.168.1.21";
        int port = 12000;
        String username = "default";
        String password = "";
        int limitSize = 1000;
        int numberOfThreads = 2;
        int queryCountPerThread = 100;
        int pauseBetweenThreads = 100;//milliseconds
        ArrayList<String> argList =null;
        ArrayList<CommandsTester> testers = new ArrayList<>();
        ArrayList<String> testCommands = loadComands();

        if(args.length>0){
            argList = new ArrayList<>(Arrays.asList(args));
            if(argList.contains("--usejedis")){
                int index = argList.indexOf("--usejedis");
                useJedis = Boolean.parseBoolean(argList.get(index+1));
            }
            if(argList.contains("--keyprefix")){
                int index = argList.indexOf("--keyprefix");
                KEY_PREFIX = argList.get(index+1);
            }
            if(argList.contains("--username")){
                int index = argList.indexOf("--username");
                username = argList.get(index+1);
            }
            if(argList.contains("--password")){
                int index = argList.indexOf("--password");
                password = argList.get(index + 1);
            }
            if(argList.contains("--host")){
                int index = argList.indexOf("--host1");
                host = argList.get(index+1);
            }
            if(argList.contains("--port")){
                int index = argList.indexOf("--port");
                port = Integer.parseInt(argList.get(index+1));
            }
            if(argList.contains("--querycountperthread")){
                int index = argList.indexOf("--querycountperthread");
                queryCountPerThread = Integer.parseInt(argList.get(index+1));
            }
            if(argList.contains("--pausebetweenthreads")){
                int index = argList.indexOf("--pausebetweenthreads");
                pauseBetweenThreads = Integer.parseInt(argList.get(index+1));
            }
            if(argList.contains("--numberofthreads")){
                int index = argList.indexOf("--numberofthreads");
                numberOfThreads = Integer.parseInt(argList.get(index+1));
            }
            if(argList.contains("--limitsize")){
                int index = argList.indexOf("--limitsize");
                limitSize = Integer.parseInt(argList.get(index+1));
            }
        }
        URI redisURI = JedisConnectionHelper.buildURI(host,port,username,password);
        System.out.println(redisURI);
        boolean shortCircuit = false;
        if(shortCircuit){
            return;
        }
        lettuceConnectionHelper = new LettuceConnectionHelper(redisURI);
        jedisConnectionHelper = new JedisConnectionHelper(redisURI);
        //springThingBean.configureSpringThingBean(host,port,username,password);


        //Have to do this before the test kicks off!
        JedisPooled jedis = jedisConnectionHelper.getPooledJedis();
        jedis.set(PERFORMANCE_TEST_THREAD_COUNTER,"0");
        jedis.del(ALL_RESULTS_SORTED_SET);

        for(int x= 0;x<numberOfThreads;x++){
            System.out.println("Connecting to "+host+":"+port);
            CommandsTester test = new CommandsTester();
            test.setKeyPrefix(KEY_PREFIX);
            test.setTestInstanceID("#"+(x+1));
            test.setNumberOfResultsLimit(limitSize);
            test.setTimesToQuery(queryCountPerThread);
            test.setMillisecondPauseBetweenQueryExecutions(0);
            test.setCommands(testCommands);
            test.setUseJedis(useJedis);
            test.setJedisConnectionHelper(jedisConnectionHelper);
            test.setSpringThingBean(springThingBean);
            test.setLettuceConnectionHelper(lettuceConnectionHelper);
            test.init();
            testers.add(test);
        }
        for(CommandsTester test:testers){
            try {
                Thread.sleep(pauseBetweenThreads);
            }catch(Throwable t){}
            Thread t = new Thread(test);
            t.start();
        }
        System.out.println("\nEach thread will execute commands using some or all of the following: (in a set sequence each time a thread fires a query)");
        for(String q: testCommands){
            System.out.println(q);
        }
        //wait to determine test has ended before getting results:
        waitForCompletion(false,testers.size());

        for(CommandsTester test:testers){
            ArrayList<Long> numericResults = test.getPerfTestNumericResults();
            ArrayList<String> stringResults = test.getPerfTestResults();
            String threadId = "Thread "+stringResults.get(0).split(":")[0];
            long totalMilliseconds =0l;
            long avgDuration = 0l;
            int resultsCounter =0;
            for(Long time:numericResults){
                totalMilliseconds+=time;
                jedis.zadd(ALL_RESULTS_SORTED_SET,time,"Thread "+stringResults.get(resultsCounter));
                resultsCounter++;
            }
            avgDuration = totalMilliseconds/numericResults.size();
            System.out.println(threadId+" executed "+numericResults.size()+" queries");
            System.out.println(threadId+" avg execution time (milliseconds) was: "+avgDuration);
            if(totalMilliseconds>1000) {
                System.out.println(threadId + " total execution time (seconds) was: " + totalMilliseconds / 1000);
            }else{
                System.out.println(threadId + " total execution time (milliseconds) was: " + totalMilliseconds);
            }
        }
        long totalResultsCaptured = jedis.zcard(ALL_RESULTS_SORTED_SET);
        System.out.println("\nAcross "+totalResultsCaptured+" unique results captured, latencies look like this:");

        System.out.println("Lowest Recorded roundtrip: "+jedis.zrange(ALL_RESULTS_SORTED_SET,0,0));
        long millis05Index = (long) (totalResultsCaptured*.05);
        System.out.println("5th percentile: "+jedis.zrange(ALL_RESULTS_SORTED_SET,millis05Index,millis05Index));
        long millis10Index = (long) (totalResultsCaptured*.1);
        System.out.println("10th percentile: "+jedis.zrange(ALL_RESULTS_SORTED_SET,millis10Index,millis10Index));
        long millis25Index = (long) (totalResultsCaptured*.25);
        System.out.println("25th percentile: "+jedis.zrange(ALL_RESULTS_SORTED_SET,millis25Index,millis25Index));
        long millis50Index = (long) (totalResultsCaptured*.5);
        System.out.println("50th percentile: "+jedis.zrange(ALL_RESULTS_SORTED_SET,millis50Index,millis50Index));
        long millis75Index = (long) (totalResultsCaptured*.75);
        System.out.println("75th percentile: "+jedis.zrange(ALL_RESULTS_SORTED_SET,millis75Index,millis75Index));
        long millis90Index = (long) (totalResultsCaptured*.9);
        System.out.println("90th percentile: "+jedis.zrange(ALL_RESULTS_SORTED_SET,millis90Index,millis90Index));
        long millis95Index = (long) (totalResultsCaptured*.95);
        System.out.println("95th percentile: "+jedis.zrange(ALL_RESULTS_SORTED_SET,millis95Index,millis95Index));
        System.out.println("Highest Recorded roundtrip: "+jedis.zrange(ALL_RESULTS_SORTED_SET,(totalResultsCaptured-1),(totalResultsCaptured-1)));
        System.out.println("\nPlease check the --> slowlog <-- on your Redis database to determine if any slowness is serverside or driven by client or network limits\n\n");

    }

    static void waitForCompletion(boolean userDriven, int threadsExpected){
        boolean noResultsYet = true;
        if(userDriven) {
            System.out.println("Pausing... \n\n\tPlease hit enter when all test threads have completed...");
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(System.in));
            try {
                reader.readLine();
            } catch (Throwable t) {
            }
        }else{
            JedisPooled jedis = jedisConnectionHelper.getPooledJedis();
            System.out.println("Waiting for results to come in from our threads...   ");
            while (noResultsYet) {
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException ie) {
                    ie.printStackTrace();
                }
                if (jedis.exists(PERFORMANCE_TEST_THREAD_COUNTER)) {
                    int threadsCompleted = Integer.parseInt(jedis.get(PERFORMANCE_TEST_THREAD_COUNTER));
                    if (threadsCompleted > 0) {
                        System.out.println("\nRESULTS COMING IN!-->>  " + threadsCompleted + " threads have completed their processing...");
                    } else {
                        System.out.print(".");
                    }
                    if (threadsExpected <= threadsCompleted) {
                        noResultsYet = false;
                    }
                }
            }
        }
    }

    static ArrayList<String> loadComands(){
        InputStream input = Main.class.getClassLoader().getResourceAsStream("commands.properties");
        Properties p = new Properties();
        try {
            p.load(input);
        } catch (IOException e) {
            e.printStackTrace();
        }
        ArrayList<String> commands = new ArrayList<>();
        for(String s : p.stringPropertyNames()) {
            commands.add(p.getProperty(s));
        }
        return commands;
    }

}

class CommandsTester implements Runnable{

    static volatile boolean showCommandsInfo = true;

    SpringThingBean springThingBean = null;
    JedisConnectionHelper jedisConnectionHelper = null;
    LettuceConnectionHelper lettuceConnectionHelper = null;
    ArrayList<String> commands = null;
    ArrayList<String> perfTestResults = new ArrayList<>();
    ArrayList<Long> perfTestNumericResults = new ArrayList<>();
    String keyPrefix = "";
    int timesToQuery =1;
    int numberOfResultsLimit = 100;
    long millisecondPauseBetweenQueryExecutions = 500;
    String testInstanceID = "";
    boolean useJedis = false;


    public void setMillisecondPauseBetweenQueryExecutions(long millisecondPauseBetweenQueryExecutions){
        this.millisecondPauseBetweenQueryExecutions = millisecondPauseBetweenQueryExecutions;
    }

    public void setUseJedis(boolean useJedis){this.useJedis = useJedis;}

    public void setTestInstanceID(String testInstanceID) {
        this.testInstanceID = testInstanceID;
    }

    public void setCommands(ArrayList<String> commands) {
        this.commands = commands;
    }

    public void setKeyPrefix(String keyPrefix) {
        this.keyPrefix = keyPrefix;
    }

    public void setLettuceConnectionHelper(LettuceConnectionHelper lettuceConnectionHelper){this.lettuceConnectionHelper = lettuceConnectionHelper;}

    public void setSpringThingBean(SpringThingBean springThingBean) { this.springThingBean = springThingBean; }

    public void setJedisConnectionHelper(JedisConnectionHelper jedisConnectionHelper) { this.jedisConnectionHelper = jedisConnectionHelper; }

    public void setNumberOfResultsLimit(int numberOfResultsLimit) {
        this.numberOfResultsLimit = numberOfResultsLimit;
    }

    public void setTimesToQuery(int timesToQuery) {
        this.timesToQuery = timesToQuery;
    }

    //call init after all properties are set

    public void init(){
        if(showCommandsInfo){
            System.out.println("COMMANDS TO BE EXECUTED: \n");
            for(String xCommand:commands){
                System.out.println(xCommand);
            }
            System.out.println("\n");
            showCommandsInfo=false; // only show it once across all threads
        }

    }

    //default constructor initializes the object
    public CommandsTester(){
    }

    @Override
    public void run() {
        for(int x=0;x<timesToQuery;x++){
            try {
                Thread.sleep(millisecondPauseBetweenQueryExecutions);
                if(useJedis) {
                    executeJedisCommand();
                }else{
                    executeLettuceCommand();
                }
            }catch(InterruptedException ie){ie.getMessage();}
        }
        JedisPooled jedis = jedisConnectionHelper.getPooledJedis();
        jedis.incr(Main.PERFORMANCE_TEST_THREAD_COUNTER);
        //System.out.println("ThreadID: "+testInstanceID+" COMPLETED TEST RUN OF: "+timesToQuery+" QUERIES.");
        //System.out.println("PERFORMANCE_TEST_THREAD_COUNTER now equals: "+pool.get(Main.PERFORMANCE_TEST_THREAD_COUNTER));
    }

    ArrayList<String> getPerfTestResults(){
        return perfTestResults;
    }
    ArrayList<Long> getPerfTestNumericResults(){
        return perfTestNumericResults;
    }


    void executeLettuceCommand() {
        //try(RedisConnection redisConnection = springThingBean.redisConnectionFactory().getConnection()){
        StatefulRedisConnection redisConnection = lettuceConnectionHelper.getConnection();
        long startTime = System.currentTimeMillis();
        int commandIndex = (int) (System.currentTimeMillis() % commands.size());
        String command = commands.get(commandIndex).split("~")[0].toUpperCase();
        //System.out.println(command);
        String argsString = commands.get(commandIndex).split("~")[1];
        String[] args = argsString.split(",");
        //System.out.println("Length of args in command is: " + args.length);
        String[] strarr = new String[args.length];
        for (int x = 0; x < args.length; x++) {
            if (args[x].equalsIgnoreCase("numeric")) {
                strarr[x] = ("" + System.nanoTime() % System.currentTimeMillis());
            } else if (args[x].equalsIgnoreCase("string")) {
                strarr[x] = ("runner_" + (System.nanoTime() % 2000000));
            } else if (args[x].equalsIgnoreCase("keyname")) {
                strarr[x] = (keyPrefix + "akey:" + (System.nanoTime() % 20000));
            } else if (args[x].equalsIgnoreCase("zero")) {
                strarr[x] = ("" +0);
            }
        }
        Object result = null;
        switch (command){
            case "ZADD":
                System.out.println("\nZADD LETTUCE");
                result = redisConnection.sync().zadd(strarr[0],Double.parseDouble(strarr[1]),strarr[2]);
                break;
            case "ZRANDMEMBER":
                System.out.println("\nZRANDMEMBER LETTUCE");
                result = redisConnection.sync().zrandmember(strarr[0]);
                break;
            case "ZREVRANGEBYSCORE":
                System.out.println("\nZREVRANGEBYSCORE LETTUCE");
                result = redisConnection.sync().zrevrangebyscore(strarr[0],Range.create(new Double(0),new Double(System.nanoTime() % System.currentTimeMillis())));
                break;
            case "ZRANGEBYSCORE":
                System.out.println("\nZRANGEBYSCORE LETTUCE");
                result = redisConnection.sync().zrangebyscore(strarr[0],Range.create(new Double(System.nanoTime() % System.currentTimeMillis()),new Double(0)));
                break;
            default:
                System.out.println("no match for that command has been assigned in Owen's code");
        }
        long duration = (System.currentTimeMillis() - startTime);
        perfTestResults.add(testInstanceID + ": executed command: " + command + " (with " + result + " results...  Execution took: " + duration + " milliseconds");
        perfTestNumericResults.add(duration);
    }

    void executeJedisCommand(){
        //System.out.println("executeJedisCommand() called...");
        JedisPooled jedis = jedisConnectionHelper.getPooledJedis();
        Object result = null;
        long startTime = System.currentTimeMillis();
        int commandIndex = (int) (System.currentTimeMillis() % commands.size());
        String command = commands.get(commandIndex).split("~")[0].toUpperCase();
        //System.out.println(command);
        String argsString = commands.get(commandIndex).split("~")[1];
        String[] args = argsString.split(",");
        //System.out.println("Length of args in command is: " + args.length);
        String[] strarr = new String[args.length];
        for (int x = 0; x < args.length; x++) {
            if (args[x].equalsIgnoreCase("numeric")) {
                strarr[x] = ("" + System.nanoTime() % System.currentTimeMillis());
            } else if (args[x].equalsIgnoreCase("string")) {
                strarr[x] = ("runner_" + (System.nanoTime() % 2000000));
            } else if (args[x].equalsIgnoreCase("keyname")) {
                strarr[x] = (keyPrefix + "akey:" + (System.nanoTime() % 20000));
            } else if (args[x].equalsIgnoreCase("zero")) {
                strarr[x] = ("" +0);
            }
        }
        result = jedis.sendCommand(() -> SafeEncoder.encode(command.trim().toUpperCase()), strarr);
        long duration = (System.currentTimeMillis() - startTime);
        perfTestResults.add(testInstanceID + ": executed command: " + command + " (with " + result + " results...  Execution took: " + duration + " milliseconds");
        perfTestNumericResults.add(duration);
    }
}

/*
This class helps get connections to Redis
It establishes a pool of connections that is set to max out at 1000
 */
class JedisConnectionHelper{

    final PooledConnectionProvider connectionProvider;
    final JedisPooled jedisPooled;

    /**
     * Used when you want to send a batch of commands to the Redis Server
     * @return Pipeline
     */
    public Pipeline getPipeline(){
        return  new Pipeline(jedisPooled.getPool().getResource());
    }

    /**
     * Assuming use of Jedis 4.3.1:
     * https://github.com/redis/jedis/blob/82f286b4d1441cf15e32cc629c66b5c9caa0f286/src/main/java/redis/clients/jedis/Transaction.java#L22-L23
     * @return Transaction
     */
    public Transaction getTransaction(){
        return new Transaction(jedisPooled.getPool().getResource());
    }

    /**
     * Obtain the default object used to perform Redis commands
     * @return JedisPooled
     */
    public JedisPooled getPooledJedis(){
        return jedisPooled;
    }

    /**
     * Use this to build the URI expected in this classes' Constructor
     * @param host
     * @param port
     * @param username
     * @param password
     * @return
     */
    public static URI buildURI(String host, int port, String username, String password){
        URI uri = null;
        try {
            if (!("".equalsIgnoreCase(password))) {
                uri = new URI("redis://" + username + ":" + password + "@" + host + ":" + port);
            } else {
                uri = new URI("redis://" + host + ":" + port);
            }
        } catch (URISyntaxException use) {
            use.printStackTrace();
            System.exit(1);
        }
        return uri;
    }


    public JedisConnectionHelper(URI uri){
        HostAndPort address = new HostAndPort(uri.getHost(), uri.getPort());
        JedisClientConfig clientConfig = null;
        System.out.println("$$$ "+uri.getAuthority().split(":").length);
        if(uri.getAuthority().split(":").length==3){
            String user = uri.getAuthority().split(":")[0];
            String password = uri.getAuthority().split(":")[1];
            password = password.split("@")[0];
            System.out.println("\n\nUsing user: "+user+" / password @@@@@@@@@@"+password);
            clientConfig = DefaultJedisClientConfig.builder().user(user).password(password)
                    .connectionTimeoutMillis(30000).timeoutMillis(120000).build(); // timeout and client settings

        }else {
            clientConfig = DefaultJedisClientConfig.builder()
                    .connectionTimeoutMillis(30000).timeoutMillis(120000).build(); // timeout and client settings
        }
        GenericObjectPoolConfig<Connection> poolConfig = new ConnectionPoolConfig();
        poolConfig.setMaxIdle(10);
        poolConfig.setMaxTotal(1000);
        poolConfig.setMinIdle(1);
        poolConfig.setMaxWait(Duration.ofMinutes(1));
        poolConfig.setTestOnCreate(true);

        this.connectionProvider = new PooledConnectionProvider(new ConnectionFactory(address, clientConfig), poolConfig);
        this.jedisPooled = new JedisPooled(connectionProvider);
    }
}

/*
* This class provides connections to Redis using the Lettuce Library
*/
class LettuceConnectionHelper {

    RedisClient redisClient = null;
    StatefulRedisConnection<String, String> connection = null;

    StatefulRedisConnection<String, String> getConnection(){
        return this.connection;
    }

    public LettuceConnectionHelper(URI redisURI){
        redisClient = RedisClient.create(redisURI.toString());
        connection = redisClient.connect();
    }

}