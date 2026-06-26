package com.labs.server.dto;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CollectionStatsDTO {
    private long pendingPatients;
    private long pendingOrders;
    private long pendingStat;
    private long pendingUrgent;
    private long collectedToday;
    private long rejectedToday;
    private long awaitingReceiveToday;   // collected today but not yet received in the lab
}
