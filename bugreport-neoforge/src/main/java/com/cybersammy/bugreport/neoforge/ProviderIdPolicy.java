package com.cybersammy.bugreport.neoforge;

import com.cybersammy.bugreport.api.identifier.InvalidIdentifierException;
import com.cybersammy.bugreport.api.identifier.NamespaceId;
import com.cybersammy.bugreport.api.identifier.ProviderId;

final class ProviderIdPolicy {
    private ProviderIdPolicy() {}

    static boolean isValidForOwner(String providerId, String ownerModId) {
        try {
            return ProviderId.parse(providerId).isOwnedBy(NamespaceId.of(ownerModId));
        } catch (InvalidIdentifierException exception) {
            return false;
        }
    }
}
