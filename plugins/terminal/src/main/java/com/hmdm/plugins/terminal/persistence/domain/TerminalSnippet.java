package com.hmdm.plugins.terminal.persistence.domain;

public class TerminalSnippet {
    private Integer id;
    private Integer customerId;
    private String category;
    private String label;
    private String commands;
    private String messageType;
    private String payloadTemplate;
    private boolean destructive;
    private Integer sortOrder;
    private Long createdAt;
    private Integer createdBy;

    public TerminalSnippet() {}

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public Integer getCustomerId() { return customerId; }
    public void setCustomerId(Integer customerId) { this.customerId = customerId; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public String getCommands() { return commands; }
    public void setCommands(String commands) { this.commands = commands; }
    public String getMessageType() { return messageType; }
    public void setMessageType(String messageType) { this.messageType = messageType; }
    public String getPayloadTemplate() { return payloadTemplate; }
    public void setPayloadTemplate(String payloadTemplate) { this.payloadTemplate = payloadTemplate; }
    public boolean isDestructive() { return destructive; }
    public void setDestructive(boolean destructive) { this.destructive = destructive; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public Long getCreatedAt() { return createdAt; }
    public void setCreatedAt(Long createdAt) { this.createdAt = createdAt; }
    public Integer getCreatedBy() { return createdBy; }
    public void setCreatedBy(Integer createdBy) { this.createdBy = createdBy; }
}
