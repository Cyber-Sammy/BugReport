package com.cybersammy.bugreport.core.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.cybersammy.bugreport.api.identifier.CapabilityId;
import com.cybersammy.bugreport.api.identifier.ProviderId;
import com.cybersammy.bugreport.api.version.CapabilityVersion;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class CapabilityOfferCollisionTest {
    private static final CapabilityId CAPABILITY_ID = CapabilityId.of("shared:service");
    private static final CapabilityVersion VERSION = new CapabilityVersion(1, 0);

    @Test
    void canonicalizesProviderOffersAndDefensivelyCopiesInput() {
        CapabilityOfferProvenance providerA = offer("shared:a");
        CapabilityOfferProvenance providerB = offer("shared:b");
        List<CapabilityOfferProvenance> input = new ArrayList<>(List.of(providerB, providerA));

        CapabilityOfferCollision collision =
                new CapabilityOfferCollision(CAPABILITY_ID, input, Optional.empty());
        input.clear();

        assertEquals(List.of(providerA, providerB), collision.providerOffers());
        assertEquals(
                List.of(ProviderId.parse("shared:a"), ProviderId.parse("shared:b")),
                collision.providerIds());
    }

    @Test
    void rejectsRepeatedProviderId() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new CapabilityOfferCollision(
                                CAPABILITY_ID,
                                List.of(offer("shared:a"), offer("shared:a")),
                                Optional.of(VERSION)));
    }

    @Test
    void rejectsNullProviderOffer() {
        List<CapabilityOfferProvenance> offers = new ArrayList<>();
        offers.add(offer("shared:a"));
        offers.add(null);

        assertThrows(
                NullPointerException.class,
                () -> new CapabilityOfferCollision(CAPABILITY_ID, offers, Optional.empty()));
    }

    private static CapabilityOfferProvenance offer(String providerId) {
        return new CapabilityOfferProvenance(ProviderId.parse(providerId), VERSION);
    }
}
