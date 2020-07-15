
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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;


/**
 * Created by hansolo on 26.11.15.
 */
public class MqttCommon {
    public static String getCurrentFolder() { return new File(".").getAbsolutePath(); }

    /**
     * Returns the properties defined by the given name if available, otherwise it will
     * create properties with default values
     * @param CONFIG_FILE_NAME Name of the properties file
     * @return Properties file with either default or configured values
     */
    public static Properties getProperties(final String CONFIG_FILE_NAME) {
        final String CURRENT_FOLDER = getCurrentFolder();
        final String CONFIG_FILE;
        if (!CURRENT_FOLDER.isEmpty()) {
            String TMP_CONFIG_FILE = String.join(File.separator, getCurrentFolder(), CONFIG_FILE_NAME);
            if (new File(TMP_CONFIG_FILE).exists()) {
                CONFIG_FILE = TMP_CONFIG_FILE;
            } else {
                CONFIG_FILE = String.join(File.separator, System.getProperty("user.dir"), CONFIG_FILE_NAME);
            }
        } else {
            CONFIG_FILE = String.join(File.separator, System.getProperty("user.dir"), CONFIG_FILE_NAME);
        }

        Properties properties = new Properties();
        try {
            properties.load(new FileInputStream(CONFIG_FILE));
        } catch (IOException exception) {
            properties = createProperties(CONFIG_FILE);
        }
        return properties;
    }

    /**
     * Creates a properties file with a given name and default parameters
     * @param CONFIG_FILE_NAME Name of the properties file
     * @return Properties file with default values
     */
    public static Properties createProperties(final String CONFIG_FILE_NAME) {
        final Properties PROPERTIES = new Properties();
        try {
            PROPERTIES.load(new FileInputStream(CONFIG_FILE_NAME));
        } catch (IOException exception) {
            PROPERTIES.put("config.mqttId","");
            PROPERTIES.put("config.mqttBroker", "");
            PROPERTIES.put("config.mqttPort", "");
            PROPERTIES.put("config.mqttUsername", "");
            PROPERTIES.put("config.mqttPassword", "");
            try {
                PROPERTIES.store(new FileOutputStream(CONFIG_FILE_NAME), "Properties");
            } catch (IOException e) {
                System.out.println("Error saving properties file: " + e.toString());
            }
        }
        return PROPERTIES;
    }

    /**
     * Checks given VALUE against MIN and MAX value
     * @param MIN Given min value
     * @param MAX Given max value
     * @param VALUE Given value to test agains MIN and MAX
     * @return The value within the range of MIN and MAX
     */
    public static int clamp(final int MIN, final int MAX, final int VALUE) {
        if (VALUE < MIN) return MIN;
        if (VALUE > MAX) return MAX;
        return VALUE;
    }
}