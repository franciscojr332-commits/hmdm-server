/*
 * HMDM-EVOLUTION F2: Batch ACK request payload — agent sends multiple ACKs in one call.
 */

package com.hmdm.notification.rest.json;

import java.io.Serializable;
import java.util.List;

public class AckBatchRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    private String deviceNumber;
    private List<AckDeliveryRequest> deliveries;
    private List<AckExecutionRequest> executions;

    public AckBatchRequest() {}

    public String getDeviceNumber() { return deviceNumber; }
    public void setDeviceNumber(String deviceNumber) { this.deviceNumber = deviceNumber; }

    public List<AckDeliveryRequest> getDeliveries() { return deliveries; }
    public void setDeliveries(List<AckDeliveryRequest> deliveries) { this.deliveries = deliveries; }

    public List<AckExecutionRequest> getExecutions() { return executions; }
    public void setExecutions(List<AckExecutionRequest> executions) { this.executions = executions; }
}
