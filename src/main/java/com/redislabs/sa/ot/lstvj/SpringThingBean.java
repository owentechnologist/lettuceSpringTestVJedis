package com.redislabs.sa.ot.lstvj;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import io.lettuce.core.SslOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.Duration;

public class SpringThingBean {
    boolean sslEnabled = false;
    String redisHost = "localhost";
    int redisPort = 6379;
    String keyStorePath = "/FIXME";
    String commandTimeout = "20000";
    String socketTimeout = "20000";
    String password = "";
    String userName = "default";


    String getRedisPassword(){
        if(null == password) {
            try {
                System.out.println("please type your redis password, then hit enter");
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(System.in));
                // Reading data using readLine
                password = reader.readLine();
            } catch (IOException ioe) {
                ioe.printStackTrace();
            }
        }
        return password;
    }

    void configureSpringThingBean(String host,int port, String userName,String password){
        this.redisHost=host;
        this.redisPort=port;
        this.userName=userName;
        this.password=password;
    }

    public RedisConnectionFactory redisConnectionFactory() {
        LettuceConnectionFactory connectionFactory = null;
        RedisStandaloneConfiguration redisStandaloneConfiguration = new RedisStandaloneConfiguration(redisHost, redisPort);
        String redisPassword = getRedisPassword();
        redisStandaloneConfiguration.setPassword(redisPassword);
        if (!sslEnabled) {
            connectionFactory = new LettuceConnectionFactory(redisStandaloneConfiguration);
            setValidateConnection(connectionFactory);
            return connectionFactory;
        }
        File keyStoreFile = new File(keyStorePath);
        char[] keyStorePassword = redisPassword.toCharArray();
        SslOptions sslOptions = SslOptions.builder()
                .keystore(keyStoreFile, keyStorePassword)
                .build();
        SocketOptions socketOptions = SocketOptions.builder()
                .connectTimeout(Duration.parse(socketTimeout))
                .build() ;
        ClientOptions clientOptions = ClientOptions.builder()
                .socketOptions(socketOptions)
                .sslOptions(sslOptions)
                .build();
        LettuceClientConfiguration lettuceClientConfiguration = LettuceClientConfiguration.builder()
                .clientOptions(clientOptions)
                .commandTimeout(Duration.parse(commandTimeout))
                .useSsl()
                .build();
        connectionFactory =  new LettuceConnectionFactory(redisStandaloneConfiguration, lettuceClientConfiguration);
        setValidateConnection(connectionFactory);
        return connectionFactory;

    }

    void setValidateConnection(LettuceConnectionFactory connectionFactory){
        System.out.println("validating LettuceConnectionFactory connection... [true]");
        connectionFactory.setValidateConnection(true);
    }
}
