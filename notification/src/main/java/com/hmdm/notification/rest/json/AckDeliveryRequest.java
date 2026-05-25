/*
 * HMDM-EVOLUTION F2: Delivery ACK request payload from agent.
 */

package com.hmdm.notification.rest.json;

import java.io.Serializable;

public class AckDeliveryRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    private String deviceNumber;
    private Integer messageId;
    private Long receivedAt;

    public AckDeliveryRequest() {}

    public String getDeviceNumber() { return deviceNumber; }
    public void setDeviceNumber(String deviceNumber) { this.deviceNumber = deviceNumber; }

    public Integer getMessageId() { return messageId; }
    public void setMessageId(Integer messageId) { this.messageId = messageId; }

    public Long getReceivedAt() { return receivedAt; }
    public void setReceivedAt(Long receivedAt) { this.receivedAt = receivedAt; }
}
