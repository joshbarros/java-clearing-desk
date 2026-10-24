package com.clearing.screening;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "screen_decisions")
public class DecisionEntity {
    @Id
    private String transferId;
    private String status;

    public String getTransferId() { return transferId; }
    public void setTransferId(String transferId) { this.transferId = transferId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
