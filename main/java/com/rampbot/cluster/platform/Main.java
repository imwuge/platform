package com.rampbot.cluster.platform;

import akka.actor.*;
import akka.cluster.singleton.ClusterSingletonManager;
import akka.cluster.singleton.ClusterSingletonManagerSettings;

import com.rampbot.cluster.platform.network.tcp.manager.TcpManager;
import com.rampbot.cluster.platform.server.manager.Server;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.net.UnknownHostException;

@Slf4j
public class Main {


    public static void main(String[] args) throws InterruptedException, UnknownHostException {

        log.info("启动系统");

        int port = 2551;

        ActorSystem actorSystem = launchClusterSingleton(port, "server");

    }

    /**
     * Launch cluster singleton and the node manager.
     * @param port
     * @param role
     * @throws InterruptedException
     * @throws UnknownHostException
     */
    private static ActorSystem launchClusterSingleton(final int port, @NonNull final String role) {



        Config conf = ConfigFactory.parseString("akka.cluster.roles=[" + role + "]")
                .withFallback(ConfigFactory.parseString("akka.remote.netty.tcp.port=" + port))
                .withFallback(ConfigFactory.load());
        ActorSystem system = ActorSystem.create("ClusterSystem", conf);

        ActorRef actorRef = system.actorOf(Props.create(Server.class), "server" );


        return system;
    }



}
