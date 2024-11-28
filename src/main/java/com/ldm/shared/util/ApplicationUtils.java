package com.ldm.shared.util;

import org.eclipse.microprofile.config.ConfigProvider;

public class ApplicationUtils {

    public static boolean isProfileActive(String profile) {
        return ConfigProvider.getConfig().getOptionalValue("quarkus.profile", String.class)
                .map(profile::equals)
                .orElse(false);
    }

//    public static boolean isClusterMonitoringEnabled(){
//        return ConfigProvider.getConfig()
//                .getOptionalValue("ldm.enable-cluster-monitoring", Boolean.class)
//                .orElse(false);
//
//    }
}
