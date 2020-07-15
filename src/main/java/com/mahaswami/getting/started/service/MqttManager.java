/*
 * Copyright (c) 2015 by Gerrit Grunwald
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mahaswami.getting.started.service;

import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.MqttTopic;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import javax.enterprise.context.RequestScoped;
import java.util.EventListener;
import java.util.EventObject;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;


/**
 * Created by hansolo on 24.11.15.
 */


@RequestScoped
public class MqttManager implements MqttCallback {

    public static final int QOS_0 = 0;

    public static final int QOS_1 = 1;

    public static final int QOS_2 = 2;

    public static final boolean RETAINED = true;

    public static final boolean NOT_RETAINED = false;

    private MqttClient client;

    private MqttConnectOptions clientConnectOptions;

    private String clientId = ConfigProvider.getConfig().getValue("mqtt.config.clientId", String.class);

    private final String brokerUrl = ConfigProvider.getConfig().getValue("mqtt.config.brokerUrl", String.class);

    private Integer port = ConfigProvider.getConfig().getValue("mqtt.config.port", Integer.class);

    private String userName = ConfigProvider.getConfig().getValue("mqtt.config.username", String.class);

    private String password = ConfigProvider.getConfig().getValue("mqtt.config.password", String.class);

    private Thread reconnectThread;

    private int randomBase = new Random().nextInt(11) + 5; // between 5 and 15 seconds

    private boolean connected;

    private CopyOnWriteArrayList<MqttEventListener> listenerList = new CopyOnWriteArrayList<>();

    private ConcurrentHashMap<String, Integer> topics = new ConcurrentHashMap<>();

    public MqttManager() {
        System.out.println(brokerUrl + ", " + port + ", " + clientId);
        //init();
    }

    // ******************* Initialization *************************************
    public void init() {
        clientConnectOptions = new MqttConnectOptions();
        clientConnectOptions.setCleanSession(true);
        clientConnectOptions.setKeepAliveInterval(1200);
        clientConnectOptions.setUserName(userName);
        clientConnectOptions.setPassword(password.toCharArray());

        connected = false;
        connectToBroker();
    }
    // ******************** Methods *******************************************
    private void connectToBroker() {
        try {
            client = new MqttClient(brokerUrl + ":" + port, clientId, new MemoryPersistence());
            client.setCallback(this);
            client.connect(clientConnectOptions);
            connected = true;
        } catch (MqttException exception) {
            connected = false;
            reconnect();
        }
    }

    public void subscribe(final String TOPIC, final int QOS) {
        if (null == client) connectToBroker();
        if (null != client && client.isConnected() && !topics.containsKey(TOPIC)) {
            try {
                client.subscribe(TOPIC, MqttCommon.clamp(QOS_0, QOS_2, QOS));
                topics.put(TOPIC, MqttCommon.clamp(QOS_0, QOS_2, QOS));
            } catch (MqttException e) {
                System.out.println("Error subscribing to " + TOPIC + " : " + e.toString());
            }
        }
    }

    public void unSubscribe(final String TOPIC) {
        if (null != client && client.isConnected() && topics.containsKey(TOPIC)) {
            try {
                client.unsubscribe(TOPIC);
                topics.remove(TOPIC);
            } catch (MqttException e) {
                System.out.println("Error unsubscribing to " + TOPIC + " : " + e.toString());
            }
        }
    }

    private void reSubscribeToTopics() {
        if (null == client) connectToBroker();
        if (null != client && client.isConnected() && !topics.isEmpty()) {
            topics.forEach(this::subscribe);
        }
    }

    public void publish(final String TOPIC, final int QOS, final String MESSAGE, final boolean RETAINED) {
        if (null == client) connectToBroker();
        if (null != client && client.isConnected()) {
            try {
                MqttTopic topic = client.getTopic(TOPIC);
                MqttMessage message = new MqttMessage(MESSAGE.getBytes());
                message.setQos(MqttCommon.clamp(QOS_0, QOS_2, QOS));
                message.setRetained(RETAINED);
                MqttDeliveryToken token = topic.publish(message);
                token.waitForCompletion(100);
                Thread.sleep(10);
            } catch (MqttException | InterruptedException exception) {
                System.out.println("Error publishing message to " + TOPIC + ": " + exception.toString());
            }
        } else {
            connected = false;
        }
    }

    public boolean isConnected() {
        return connected;
    }

    public void disconnect(final long TIMEOUT) {
        if (null == client || !client.isConnected()) return;
        try {
            if (null != reconnectThread && reconnectThread.isAlive()) {
                reconnectThread.interrupt();
            }
            client.disconnect(TIMEOUT);
        } catch (MqttException exception) {

        }
    }


    // ******************** Reconnection **************************************
    private boolean isReconnectionAllowed() {
        return !client.isConnected();
    }

    synchronized protected void reconnect() {
        if (client.isConnected() && reconnectThread != null && reconnectThread.isAlive()) return;

        reconnectThread = new Thread() {
            private int attempts = 0;

            /**
             * Returns the number of seconds until the next reconnection attempt.
             * @return the number of seconds until the next reconnection attempt.
             */
            private int timeDelay() {
                attempts++;
                if (attempts > 13) {
                    return randomBase * 6 * 5; // between 2.5 and 7.5 minutes (~5 minutes)
                }
                if (attempts > 7) {
                    return randomBase * 6;     // between 30 and 90 seconds (~1 minutes)
                }
                return randomBase;             // 10 seconds
            }

            /**
             * The process will try the reconnection until the connection
             * succeed or the user cancel it
             */
            public void run() {
                while (MqttManager.this.isReconnectionAllowed()) {
                    int remainingSeconds = timeDelay();

                    while (MqttManager.this.isReconnectionAllowed() && remainingSeconds > 0) {
                        try {
                            Thread.sleep(1000);
                            remainingSeconds--;
                        } catch (InterruptedException exception) {
                            connected = false;
                        }
                    }

                    // Makes a reconnection attempt
                    try {
                        if (MqttManager.this.isReconnectionAllowed()) {
                            client.connect(clientConnectOptions);
                            if (client.isConnected()) {
                                connected = true;
                                reSubscribeToTopics();
                            }
                        }
                    } catch (MqttException exception) {
                        // Fires the failed reconnection notification
                        connected = false;
                    }
                }
            }
        };
        reconnectThread.setName("MQTT Reconnection Manager");
        reconnectThread.setDaemon(false);
        reconnectThread.start();
    }


    // ******************** Event handling ************************************
    @Override
    public void connectionLost(final Throwable CAUSE) {
        reconnect();
    }

    @Override
    public void messageArrived(final String TOPIC, final MqttMessage MQTT_MESSAGE) {
        fireMqttEvent(new MqttEvent(this, TOPIC, MQTT_MESSAGE));
    }

    @Override
    public void deliveryComplete(final IMqttDeliveryToken TOKEN) {
    }


    public void setOnMessageReceived(final MqttEventListener LISTENER) {
        addMqttEventListener(LISTENER);
    }

    public final void addMqttEventListener(final MqttEventListener LISTENER) {
        listenerList.add(LISTENER);
    }

    public final void removeMqttEventListener(final MqttEventListener LISTENER) {
        listenerList.remove(LISTENER);
    }

    public void fireMqttEvent(final MqttEvent EVENT) {
        listenerList.forEach(listener -> listener.onMqttEvent(EVENT));
    }


    // ******************** Inner Classes *************************************
    public class MqttEvent extends EventObject {
        public final String TOPIC;
        public final MqttMessage MESSAGE;

        // ******************** Constructor ***********************************
        public MqttEvent(Object source, final String MQTT_TOPIC, final MqttMessage MQTT_MESSAGE) {
            super(source);
            TOPIC = MQTT_TOPIC;
            MESSAGE = MQTT_MESSAGE;
        }
    }

    public interface MqttEventListener extends EventListener {
        void onMqttEvent(MqttEvent event);
    }
}
