package com.nepxion.discovery.plugin.strategy.adapter;

/**
 * <p>Title: Nepxion Discovery</p>
 * <p>Description: Nepxion Discovery</p>
 * <p>Copyright: Copyright (c) 2017-2050</p>
 * <p>Company: Nepxion</p>
 * @author Haojun Ren
 * @version 1.0
 */

import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.CollectionUtils;

import com.nepxion.discovery.common.constant.DiscoveryConstant;
import com.nepxion.discovery.common.util.JsonUtil;
import com.nepxion.discovery.common.util.StringUtil;
import com.nepxion.discovery.plugin.framework.adapter.PluginAdapter;
import com.nepxion.discovery.plugin.framework.context.PluginContextHolder;
import com.nepxion.discovery.plugin.strategy.filter.StrategyRegionFilter;
import com.nepxion.discovery.plugin.strategy.filter.StrategyVersionFilter;
import com.nepxion.discovery.plugin.strategy.matcher.DiscoveryMatcherStrategy;
import com.netflix.loadbalancer.Server;

public class DefaultDiscoveryEnabledAdapter implements DiscoveryEnabledAdapter {
    @Autowired(required = false)
    private List<DiscoveryEnabledStrategy> discoveryEnabledStrategyList;

    @Autowired
    private DiscoveryMatcherStrategy discoveryMatcherStrategy;

    @Autowired(required = false)
    private StrategyVersionFilter strategyVersionFilter;

    @Autowired(required = false)
    private StrategyRegionFilter strategyRegionFilter;

    @Autowired
    protected PluginAdapter pluginAdapter;

    @Autowired
    protected PluginContextHolder pluginContextHolder;

    @Override
    public boolean apply(Server server) {
        boolean enabled = applyVersion(server);
        if (!enabled) {
            return false;
        }

        enabled = applyRegion(server);
        if (!enabled) {
            return false;
        }

        enabled = applyAddress(server);
        if (!enabled) {
            return false;
        }

        return applyStrategy(server);
    }

    private boolean applyVersion(Server server) {
        String versions = getVersions(server);
        if (StringUtils.isEmpty(versions)) {
            if (strategyVersionFilter != null) {
                return strategyVersionFilter.apply(server);
            } else {
                return true;
            }
        }

        String version = pluginAdapter.getServerVersion(server);

        // 如果精确匹配不满足，尝试用通配符匹配
        List<String> versionList = StringUtil.splitToList(versions, DiscoveryConstant.SEPARATE);
        if (versionList.contains(version)) {
            return true;
        }

        // 通配符匹配。前者是通配表达式，后者是具体值
        for (String versionPattern : versionList) {
            if (discoveryMatcherStrategy.match(versionPattern, version)) {
                return true;
            }
        }

        return false;
    }

    @SuppressWarnings("unchecked")
    private String getVersions(Server server) {
        String versionValue = pluginContextHolder.getContextRouteVersion();
        if (StringUtils.isEmpty(versionValue)) {
            return null;
        }

        String serviceId = pluginAdapter.getServerServiceId(server);

        String versions = null;
        try {
            Map<String, String> versionMap = JsonUtil.fromJson(versionValue, Map.class);
            versions = versionMap.get(serviceId);
        } catch (Exception e) {
            versions = versionValue;
        }

        return versions;
    }

    private boolean applyRegion(Server server) {
        String regions = getRegions(server);
        if (StringUtils.isEmpty(regions)) {
            if (strategyRegionFilter != null) {
                return strategyRegionFilter.apply(server);
            } else {
                return true;
            }
        }

        String region = pluginAdapter.getServerRegion(server);

        // 如果精确匹配不满足，尝试用通配符匹配
        List<String> regionList = StringUtil.splitToList(regions, DiscoveryConstant.SEPARATE);
        if (regionList.contains(region)) {
            return true;
        }

        // 通配符匹配。前者是通配表达式，后者是具体值
        for (String regionPattern : regionList) {
            if (discoveryMatcherStrategy.match(regionPattern, region)) {
                return true;
            }
        }

        return false;
    }

    @SuppressWarnings("unchecked")
    private String getRegions(Server server) {
        String regionValue = pluginContextHolder.getContextRouteRegion();
        if (StringUtils.isEmpty(regionValue)) {
            return null;
        }

        String serviceId = pluginAdapter.getServerServiceId(server);

        String regions = null;
        try {
            Map<String, String> regionMap = JsonUtil.fromJson(regionValue, Map.class);
            regions = regionMap.get(serviceId);
        } catch (Exception e) {
            regions = regionValue;
        }

        return regions;
    }

    private boolean applyAddress(Server server) {
        String addresses = getAddresses(server);
        if (StringUtils.isEmpty(addresses)) {
            return true;
        }

        // 如果精确匹配不满足，尝试用通配符匹配
        List<String> addressList = StringUtil.splitToList(addresses, DiscoveryConstant.SEPARATE);
        if (addressList.contains(server.getHostPort()) || addressList.contains(server.getHost()) || addressList.contains(String.valueOf(server.getPort()))) {
            return true;
        }

        // 通配符匹配。前者是通配表达式，后者是具体值
        for (String addressPattern : addressList) {
            if (discoveryMatcherStrategy.match(addressPattern, server.getHostPort()) || discoveryMatcherStrategy.match(addressPattern, server.getHost()) || discoveryMatcherStrategy.match(addressPattern, String.valueOf(server.getPort()))) {
                return true;
            }
        }

        return false;
    }

    @SuppressWarnings("unchecked")
    private String getAddresses(Server server) {
        String addressValue = pluginContextHolder.getContextRouteAddress();
        if (StringUtils.isEmpty(addressValue)) {
            return null;
        }

        String serviceId = pluginAdapter.getServerServiceId(server);

        Map<String, String> addressMap = JsonUtil.fromJson(addressValue, Map.class);
        String addresses = addressMap.get(serviceId);

        return addresses;
    }

    private boolean applyStrategy(Server server) {
        if (CollectionUtils.isEmpty(discoveryEnabledStrategyList)) {
            return true;
        }

        for (DiscoveryEnabledStrategy discoveryEnabledStrategy : discoveryEnabledStrategyList) {
            boolean enabled = discoveryEnabledStrategy.apply(server);
            if (!enabled) {
                return false;
            }
        }

        return true;
    }

    public PluginAdapter getPluginAdapter() {
        return pluginAdapter;
    }

    public PluginContextHolder getPluginContextHolder() {
        return pluginContextHolder;
    }
}