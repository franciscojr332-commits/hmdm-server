package com.hmdm.plugins.terminal.guice.module;

import com.hmdm.guice.module.AbstractPersistenceModule;

import javax.servlet.ServletContext;

public class TerminalPersistenceModule extends AbstractPersistenceModule {

    public TerminalPersistenceModule(ServletContext context) {
        super(context);
    }

    @Override
    protected String getMapperPackageName() {
        return "com.hmdm.plugins.terminal.persistence.mapper";
    }

    @Override
    protected String getDomainObjectsPackageName() {
        return "com.hmdm.plugins.terminal.persistence.domain";
    }
}
