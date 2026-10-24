package com.clearing.book;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "transfers")
public class TransferEntity {
    @Id
    private String id;
    @Column(name = "from_account_id")
    private String fromAccountId;
    @Column(name = "to_account_id")
    private String toAccountId;
    @Column(name = "to_name")
    private String toName;
    @Column(name = "amount_cents")
    private int amountCents;
    private String reference;
    private String status;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getFromAccountId() { return fromAccountId; }
    public void setFromAccountId(String fromAccountId) { this.fromAccountId = fromAccountId; }
    public String getToAccountId() { return toAccountId; }
    public void setToAccountId(String toAccountId) { this.toAccountId = toAccountId; }
    public String getToName() { return toName; }
    public void setToName(String toName) { this.toName = toName; }
    public int getAmountCents() { return amountCents; }
    public void setAmountCents(int amountCents) { this.amountCents = amountCents; }
    public String getReference() { return reference; }
    public void setReference(String reference) { this.reference = reference; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
