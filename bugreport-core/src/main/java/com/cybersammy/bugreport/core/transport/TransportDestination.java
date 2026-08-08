package com.cybersammy.bugreport.core.transport;

/** Restricted first-party delivery destination; not a provider extension point. */
public sealed interface TransportDestination permits LocalArchiveDestination {}
