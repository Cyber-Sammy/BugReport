package com.cybersammy.bugreport.core.transport;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cybersammy.bugreport.core.packaging.ReportPackagePlan;
import com.cybersammy.bugreport.core.workspace.ReportWorkspace;
import java.lang.reflect.Modifier;
import org.junit.jupiter.api.Test;

/** Guards the visibility boundary that keeps consent minting out of public transport APIs. */
final class LocalExportTransportBridgeTest {
    @Test
    void rawConsentIssuanceAndExecutionAreNotPublicApis() throws Exception {
        assertFalse(Modifier.isPublic(LocalExportConsent.class.getModifiers()));
        assertFalse(Modifier.isPublic(LocalExportConsent.class
                .getDeclaredMethod("issueConfirmed", ReportPackagePlan.class, LocalArchiveDestination.class)
                .getModifiers()));
        assertTrue(Modifier.isProtected(LocalExportTransportBridge.class
                .getDeclaredMethod(
                        "executeConfirmedLocalExport",
                        ReportPackagePlan.class,
                        ReportWorkspace.class,
                        LocalArchiveDestination.class,
                        TransportRunControl.class)
                .getModifiers()));
    }
}
