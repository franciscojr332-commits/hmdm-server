package com.hmdm.plugins.terminal;

import com.google.inject.Module;
import com.hmdm.plugin.PluginConfiguration;
import com.hmdm.plugin.PluginTaskModule;
import com.hmdm.plugins.terminal.guice.module.TerminalLiquibaseModule;
import com.hmdm.plugins.terminal.guice.module.TerminalPersistenceModule;
import com.hmdm.plugins.terminal.guice.module.TerminalRestModule;

import javax.servlet.ServletContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Terminal plugin — CMD-style remote shell for HMDM-managed devices.
 *
 * Adds a "Terminal" entry under the Functions menu, allowing administrators
 * to send shell commands via runCommand push to one or more devices, and
 * stream the output back from plugin_devicelog_log.
 */
public class TerminalPluginConfigurationImpl implements PluginConfiguration {

    public static final String PLUGIN_ID = "terminal";

    public TerminalPluginConfigurationImpl() {
    }

    @Override
    public String getPluginId() {
        return PLUGIN_ID;
    }

    @Override
    public String getRootPackage() {
        return "com.hmdm.plugins.terminal";
    }

    @Override
    public List<Module> getPluginModules(ServletContext context) {
        List<Module> modules = new ArrayList<>();
        modules.add(new TerminalLiquibaseModule(context));
        modules.add(new TerminalPersistenceModule(context));
        modules.add(new TerminalRestModule());
        return modules;
    }

    @Override
    public Optional<List<Class<? extends PluginTaskModule>>> getTaskModules(ServletContext context) {
        return Optional.empty();
    }
}
