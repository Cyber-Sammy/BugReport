package com.cybersammy.bugreport.core.transport;

/** Restricted first-party transport SPI; it is not a public runtime plugin contract. */
public interface ReportTransport {
    ReportTransportDescriptor descriptor();

    ReportTransportResult execute(
            ReportTransportRequest request, TransportRunControl control);
}
