package com.cybersammy.bugreport.core.registry;

import com.cybersammy.bugreport.api.BugReportProvider;
import com.cybersammy.bugreport.api.identifier.NamespaceId;
import com.cybersammy.bugreport.api.identifier.ProviderId;
import com.cybersammy.bugreport.api.specification.ProviderSpecification;

record ValidatedProvider(
        NamespaceId ownerNamespace,
        String implementationClass,
        ProviderId id,
        BugReportProvider provider,
        ProviderSpecification specification) {}
