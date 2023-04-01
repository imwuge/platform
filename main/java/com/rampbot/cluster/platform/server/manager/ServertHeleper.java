package com.rampbot.cluster.platform.server.manager;

import akka.actor.ActorRef;
import akka.actor.UntypedActor;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;

public class ServertHeleper extends UntypedActor {

    private final Server server;


    private Map<String, ActorRef> server2serverRefs;



    public ServertHeleper(@NonNull final Server server) {
        this.server = server;
    }


    public void preStart(){
        this.server2serverRefs = new HashMap<>();
    }


    @Override
    public void onReceive(Object o) throws Throwable {

    }
}
