package com.hmdm.plugins.terminal.guice.module;

import com.hmdm.guice.module.AbstractLiquibaseModule;
import com.hmdm.plugin.guice.module.PluginLiquibaseResourceAccessor;
import liquibase.resource.ResourceAccessor;

import javax.servlet.ServletContext;

public class TerminalLiquibaseModule extends AbstractLiquibaseModule {

    public TerminalLiquibaseModule(ServletContext context) {
        super(context);
    }

    @Override
    protected String getChangeLogResourcePath() {
        String path = this.getClass().getResource("/liquibase/terminal.changelog.xml").getPath();
        if (!path.startsWith("jar:")) {
            path = "jar:" + path;
        }
        return path;
    }

    @Override
    protected ResourceAccessor getResourceAccessor() {
        return new PluginLiquibaseResourceAccessor();
    }
}
