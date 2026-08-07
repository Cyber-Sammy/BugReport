package com.cybersammy.bugreport.core.transport;

import static org.junit.jupiter.api.Assertions.assertFalse;

import com.cybersammy.bugreport.core.packaging.ReportPackagePlan;
import java.lang.reflect.Modifier;
import org.junit.jupiter.api.Test;

class TransportBoundaryVisibilityTest {
    @Test
    void rawConsentAndItsIssuerAreNotPublicMintingApis() throws NoSuchMethodException {
        assertFalse(Modifier.isPublic(LocalExportConsent.class.getModifiers()));
        assertFalse(Modifier.isPublic(TransportConsentIssuer.class.getModifiers()));
        assertFalse(Modifier.isPublic(TransportConsentIssuer.class
                .getDeclaredMethod(
                        "issueConfirmedLocalExport",
                        ReportPackagePlan.class,
                        LocalArchiveDestination.class)
                .getModifiers()));
    }
}
