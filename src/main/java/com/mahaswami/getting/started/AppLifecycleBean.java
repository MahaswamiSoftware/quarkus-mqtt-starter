package com.mahaswami.getting.started;

import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import com.mahaswami.getting.started.service.MqttManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.inject.Inject;

@ApplicationScoped
public class AppLifecycleBean {

    private static final Logger LOGGER = LoggerFactory.getLogger("ListenerBean");

    @Inject
    MqttManager mqttManager;

    void onStart(@Observes StartupEvent ev) {
        System.out.println("The application is starting...{}");
        mqttManager.init();
    }

    void onStop(@Observes ShutdownEvent ev) {
        System.out.println("The application is stopping... {}");
    }

}
